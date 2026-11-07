#!/usr/bin/env bb
;; Prime Meridian content pipeline (Assignment 4: dynamic content from the GDD).
;;
;;   bb content <request>            e.g. bb content hal-lines
;;   bb content all                  every request in content/requests/
;;   bb content <request> --dry-run  build and save the prompts, call no model
;;
;; One run, logged step by step under content/runs/<request>-<stamp>/:
;;   1. context   pull the GDD sections the request names (+ any repo files)
;;   2. writer    a Claude model drafts the content as EDN, in the GDD's voice
;;   3. validate  plain code checks shape, coverage, lengths, placeholders,
;;                banned words and the GDD's hard rules (no model involved)
;;   4. critic    a second, stronger model reads the same GDD and the draft and
;;                returns findings (blocker / minor) with the GDD section each
;;                one rests on
;;   5. loop      any validator error or critic blocker goes back to the writer
;;                with the draft; up to :max-drafts drafts
;;   6. install   only a draft that passes both is written to the request's
;;                :out file (resources/content/*.edn), which the game loads
;;
;; Models: WRITER_MODEL (default claude-sonnet-5) and CRITIC_MODEL (default
;; claude-opus-5-5), through the `claude` CLI in print mode with no tools.
(ns pipeline
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.string :as str]))

(def root (str (fs/parent (fs/parent (fs/absolutize *file*)))))
(defn rpath [& xs] (str (apply fs/path root xs)))

(def writer-model (or (System/getenv "WRITER_MODEL") "claude-sonnet-5"))
(def critic-model (or (System/getenv "CRITIC_MODEL") "claude-opus-5-5"))

;; ---------------------------------------------------------------- context --

(defn gdd-sections
  "Split docs/GDD.md into {heading-text section-text}."
  []
  (let [lines (str/split-lines (slurp (rpath "docs" "GDD.md")))]
    (loop [ls lines, cur nil, level 0, acc [], out []]
      (if-let [l (first ls)]
        (if-let [[_ hashes text] (re-matches #"^(#{1,4}) (.*)$" l)]
          (recur (rest ls) text (count hashes) [l]
                 (if cur (conj out [cur level (str/join "\n" acc)]) out))
          (recur (rest ls) cur level (conj acc l) out))
        (cond-> out cur (conj [cur level (str/join "\n" acc)]))))))

(defn section
  "The GDD section whose heading starts with `id` (\"2.3\", \"1.\", \"3.2\"),
   including its subsections."
  [sections id]
  (let [idx (first (keep-indexed (fn [i [h]] (when (str/starts-with? h (str id " ")) i)) sections))]
    (when-not idx (throw (ex-info (str "no GDD section " id) {:id id})))
    (let [[_ level _] (nth sections idx)
          sub (take-while (fn [[_ l]] (> l level)) (drop (inc idx) sections))]
      (str/join "\n\n" (map #(nth % 2) (cons (nth sections idx) sub))))))

(defn build-context [{:keys [sources]}]
  (let [secs (gdd-sections)]
    (str "# Source: docs/GDD.md (Prime Meridian's game design document), the sections this content draws on\n\n"
         (str/join "\n\n---\n\n" (map #(section secs %) (:gdd sources)))
         (str/join (for [f (:files sources)]
                     (str "\n\n---\n\n# Source: " f "\n\n```\n" (slurp (rpath f)) "\n```"))))))

;; ------------------------------------------------------------------ model --

(defn call-model
  "Run `claude -p` with no tools from a scratch directory (no project
   CLAUDE.md), the given system prompt, and the prompt on stdin. Returns
   {:text :usage}."
  [model system prompt]
  (let [dir (str (fs/create-temp-dir {:prefix "pm-content-"}))
        res (p/shell {:in prompt :out :string :err :string :dir dir :continue true}
                     "claude" "-p" "--model" model "--output-format" "json"
                     "--no-session-persistence" "--tools" "" "--system-prompt" system)]
    (fs/delete-tree dir)
    (when-not (zero? (:exit res))
      (throw (ex-info (str "claude exited " (:exit res) ": " (str/trim (:err res))) {})))
    (let [j (json/parse-string (:out res) true)]
      (when (:is_error j) (throw (ex-info (str "model error: " (:result j)) {})))
      {:text (:result j) :usage (select-keys (:usage j) [:input_tokens :output_tokens])
       :cost (:total_cost_usd j)})))

(defn read-edn
  "Parse a model reply as EDN, tolerating a ```edn fence around it."
  [text]
  (let [t (str/trim (or text ""))
        t (if-let [[_ inner] (re-find #"(?s)```(?:edn|clojure)?\s*\n(.*?)```" t)] inner t)]
    (try {:value (edn/read-string t)}
         (catch Exception e {:error (str "not valid EDN: " (.getMessage e))}))))

;; --------------------------------------------------------------- validate --

(def banned-everywhere
  "Things the GDD rules out for any player-facing text: scores and streaks
   (§1 'no streaks, nothing to shame you', §2.6 'no XP'), shaming (§3.2),
   and the films' own lines (§1: 'original names and dialogue')."
  [[#"(?i)\bXP\b|\blevel(?:ed)? up\b|\bhigh score\b|\bpoints\b" "scores/XP (§2.6: no XP, no number on screen)"]
   [#"(?i)\bstreaks?\b" "streaks (§1: no streaks to break)"]
   [#"(?i)\blazy\b(?!-)|\bfailed\b|\bshould have\b|\bshame\b|\bdisappoint" "shaming (§3.2: never shames)"]
   [#"\{(?:stock|bales|count|hours)\}\s+[A-Za-z]+s\b" "a count followed by a plural noun reads wrong at 1 (\"1 worms\"): put the count after a label (\"Worms: {stock}\")"]
   [#"(?i)game over" "game over (§1: no game over)"]
   [#"(?i)i know kung fu|there is no spoon|follow the white rabbit|welcome to the real world|free your mind|red pill|blue pill|wake up, neo|you\'re the one\b"
    "a film line (§1: the shipped game uses original dialogue)"]])

(def placeholder-re #"\{([a-z][a-z0-9-]*)\}")
(defn placeholders [s] (set (map (comp keyword second) (re-seq placeholder-re s))))

(defn check-text
  "Errors for one player-facing string at `where`."
  [where s {:keys [max-chars allowed extra-banned]}]
  (concat
   (when-not (string? s) [(str where ": not a string")])
   (when (and (string? s) (str/blank? s)) [(str where ": empty")])
   (when (and (string? s) max-chars (> (count s) max-chars))
     [(str where ": " (count s) " chars, max " max-chars ": " (pr-str s))])
   (when (string? s)
     (for [[re why] (concat banned-everywhere extra-banned) :when (re-find re s)]
       (str where ": " why ": " (pr-str s))))
   (when (and (string? s) allowed)
     (for [ph (set/difference (placeholders s) allowed)]
       (str where ": placeholder {" (name ph) "} isn't available here (allowed: "
            (str/join ", " (map #(str "{" (name %) "}") (sort allowed))) ")")))))

(defn check-variants
  "A vector of `lo`..`hi` distinct strings, each passing check-text."
  [where xs [lo hi] opts]
  (cond
    (not (vector? xs)) [(str where ": expected a vector of lines")]
    :else
    (concat
     (when (< (count xs) lo) [(str where ": " (count xs) " lines, need at least " lo)])
     (when (> (count xs) hi) [(str where ": " (count xs) " lines, at most " hi)])
     (when (not= (count xs) (count (distinct xs))) [(str where ": duplicate lines")])
     (mapcat (fn [i s] (check-text (str where "[" i "]") s opts)) (range) xs))))

(defmulti validate (fn [req _] (get-in req [:checks :kind])))

(defmethod validate :line-table [{:keys [checks]} v]
  ;; {event [line ...]}: every required event present, nothing extra.
  (let [{:keys [events variants max-chars]} checks]
    (if-not (map? v)
      ["top level: expected a map of event -> lines"]
      (concat
       (for [e (keys events) :when (not (contains? v e))] (str "missing event " e))
       (for [e (keys v) :when (not (contains? events e))] (str "unknown event " e " (not in the request)"))
       (mapcat (fn [[e allowed]]
                 (when (contains? v e)
                   (check-variants (str e) (get v e) variants {:max-chars max-chars :allowed allowed})))
               events)))))

(def needs-words
  "§2.5: memorial and virtual creatures have no needs and never speak as if
   alive and wanting something."
  [[#"(?i)\bhungry\b|\bfeed\b|\bfed\b|\bworms?\b|\bhay\b|\bwalk(?:ed|s)?\b|\bleash\b|\bdue\b" "a need or chore (§2.5: memorial/virtual creatures have no needs)"]])

(def first-person [[#"(?i)\bI'm\b|\bI am\b|\bI need\b|\bI want\b|\bme\b|\bmy\b" "the creature speaking as if alive (§2.5: memorial pets never speak as if alive)"]])

(defmethod validate :mirror-lines [{:keys [checks]} v]
  ;; {:mirrors {id {key [lines]}} :modes {mode {key [lines]}}}
  (let [{:keys [mirrors modes variants]} checks]
    (if-not (and (map? v) (map? (:mirrors v)) (map? (:modes v)))
      ["top level: expected {:mirrors {...} :modes {...}}"]
      (concat
       (for [id (keys mirrors) :when (not (contains? (:mirrors v) id))] (str "missing mirror " id))
       (for [id (keys (:mirrors v)) :when (not (contains? mirrors id))] (str "unknown mirror " id))
       (for [[id ks] mirrors, k (keys ks) :when (not (contains? (get-in v [:mirrors id]) k))]
         (str "missing " id " " k))
       (for [[id ks] mirrors, k (keys (get-in v [:mirrors id])) :when (not (contains? ks k))]
         (str "unknown key " id " " k))
       (mapcat (fn [[id ks]]
                 (mapcat (fn [[k {:keys [allowed max-chars]}]]
                           (when-let [xs (get-in v [:mirrors id k])]
                             (check-variants (str id " " k) xs variants {:max-chars max-chars :allowed allowed})))
                         ks))
               mirrors)
       (for [m (keys modes) :when (not (contains? (:modes v) m))] (str "missing mode " m))
       (for [[m ks] modes, k (keys ks) :when (not (contains? (get-in v [:modes m]) k))]
         (str "missing " m " " k))
       (mapcat (fn [[m ks]]
                 (mapcat (fn [[k {:keys [allowed max-chars no-first-person]}]]
                           (when-let [xs (get-in v [:modes m k])]
                             (check-variants (str m " " k) xs variants
                                             {:max-chars max-chars :allowed allowed
                                              :extra-banned (cond-> needs-words no-first-person (concat first-person))})))
                         ks))
               modes)))))

(defmethod validate :inbox [{:keys [checks]} v]
  (let [{:keys [mailbox kinds count-range from-domains banned-names max-body max-subject]} checks
        msgs (:messages v)
        names-re [[(re-pattern (str "(?i)\\b(" (str/join "|" banned-names) ")\\b"))
                   "a real person or business (the sample inbox is everyone's: §4.6)"]]]
    (if-not (and (map? v) (vector? msgs))
      ["top level: expected {:mailbox ... :messages [...]}"]
      (concat
       (when (not= mailbox (:mailbox v)) [(str ":mailbox must be " mailbox)])
       (let [[lo hi] count-range n (count msgs)]
         (when-not (<= lo n hi) [(str n " messages, need " lo "-" hi)]))
       (for [k kinds :when (not-any? #(= k (:kind %)) msgs)] (str "no message of kind " k))
       (let [ids (map :id msgs)] (when (not= (count ids) (count (distinct ids))) ["duplicate :id"]))
       (mapcat
        (fn [i {:keys [id from from-name kind subject body minutes-ago sample clerk-note] :as m}]
          (let [w (str "message " i " " id)]
            (concat
             (when-not (keyword? id) [(str w ": :id must be a keyword")])
             (when-not (contains? (set kinds) kind) [(str w ": unknown :kind " kind)])
             (when-not (and (string? from) (some #(str/ends-with? from (str "@" %)) from-domains))
               [(str w ": :from must be @" (str/join " or @" from-domains) " (pretend addresses only): " from)])
             (when-not (string? from-name) [(str w ": :from-name missing")])
             (when-not (true? sample) [(str w ": must be marked :sample true (§3: never a fake passed off as real)")])
             (when-not (and (integer? minutes-ago) (<= 0 minutes-ago 1440)) [(str w ": :minutes-ago must be 0-1440")])
             (when (and (= kind :suspicious) (not (string? clerk-note)))
               [(str w ": a :suspicious message needs the Clerk's :clerk-note (§3.2: the reader with no keys)")])
             (when (and (string? body) (re-find #"https?://(?!example\.com)" body))
               [(str w ": links must point at example.com")])
             (check-text (str w " :subject") subject {:max-chars max-subject :extra-banned names-re})
             (check-text (str w " :body") body {:max-chars max-body :extra-banned names-re})
             (when (and (not (#{:suspicious :clerk-summary} kind)) (string? body)
                        (re-find #"(?i)\bpassword\b|\bcard number\b|\bbank (?:account|details|number)\b|\bSSN\b" body))
               [(str w ": asks for credentials or bank details outside a :suspicious message (§3.2)")]))))
        (range) msgs)))))

;; ----------------------------------------------------------------- prompts --

(def writer-system
  "You are the Writer on Prime Meridian's development crew (GDD §4.2): you write the
digital twin's voice, the status bubbles, and sample content, as EDN data the game
loads. You write ONLY from the game's own design document, which you are given.
Everything must sound like Prime Meridian, never like generic game text. Reply with
the EDN value only: no prose, no code fence.")

(def critic-system
  "You are the critic on Prime Meridian's development crew. You review content the
Writer drafted, against the game's design document, which you are given. You are
strict about facts and rules and fair about taste. Reply with one EDN map only: no
prose, no code fence.")

(defn writer-prompt [req context prev]
  (str context
       "\n\n===\n\n# Your job: " (:title req) "\n\n" (:brief req)
       "\n\n## Exact shape\n\n" (:shape req)
       "\n\n## Hard rules (a script checks these)\n\n" (:rules req)
       (when prev
         (str "\n\n===\n\n# Revise your previous draft\n\nYour previous draft:\n\n"
              (:draft-text prev)
              (when (seq (:errors prev))
                (str "\n\nThe validator found these errors (fix every one):\n"
                     (str/join "\n" (map #(str "- " %) (:errors prev)))))
              (when (seq (:blockers prev))
                (str "\n\nThe critic's blockers on this draft (fix every one; keep what it didn't flag):\n"
                     (str/join "\n" (map #(str "- " (:where %) ": " (:issue %) " [" (:gdd %) "] fix: " (:fix %)) (:blockers prev)))))
              (when (seq (:earlier prev))
                (str "\n\nBlockers the critic raised on EARLIER drafts (already fixed; don't bring any back):\n"
                     (str/join "\n" (map #(str "- " (:where %) ": " (:issue %)) (:earlier prev)))))
              "\n\nReturn the complete revised EDN."))))

(defn critic-prompt [req context draft-text]
  (str context
       "\n\n===\n\n# The Writer's draft: " (:title req) "\n\nWhat was asked:\n\n" (:brief req)
       "\n\nThe draft:\n\n" draft-text
       "\n\n===\n\n# Review it\n\nCheck, citing the GDD section each point rests on:\n"
       "1. Facts: does anything contradict the GDD (times, names, rules, places, who does what)?\n"
       "2. Voice: does it sound like Prime Meridian (warm, wry, a little Matrix/TRON shorthand, never the films' own lines; the twin is a companion, not a butler or a drill sergeant)? Quote any line that sounds generic.\n"
       "3. Guardrails: never shames, no scores or streaks, sends the player back to real life, purchases and messages to other people need approval, memorial and virtual creatures have no needs, the reader with no keys.\n"
       "4. Usefulness: would the game actually use each part; is there real variety, or the same line reworded?\n"
       "5. Public repo: no real person's private details.\n"
       (:critic req)
       "\n\nA **blocker** is anything that breaks a GDD fact or rule, or a line the game should not ship. "
       "A **minor** is taste. Return exactly:\n\n"
       "{:verdict :pass-or-:revise\n :sounds-like-game \"one sentence: does it sound like this game, and why\"\n"
       " :findings [{:severity :blocker-or-:minor :where \"which key/line\" :issue \"...\" :gdd \"§x.y\" :fix \"...\"}]}\n\n"
       ":verdict is :pass exactly when there are no blockers."))

;; ------------------------------------------------------------------- run --

(defn stamp [] (.format (java.text.SimpleDateFormat. "yyyyMMdd-HHmm") (java.util.Date.)))

(defn spit-run [dir name content] (spit (str (fs/path dir name)) content))

(defn pretty [v] (with-out-str (pp/pprint v)))

(defn run-request [req-id {:keys [dry-run]}]
  (let [req (edn/read-string (slurp (rpath "content" "requests" (str (name req-id) ".edn"))))
        dir (rpath "content" "runs" (str (name req-id) "-" (stamp)))
        _   (fs/create-dirs dir)
        context (build-context req)
        log (atom [(str "# Content run: " (:title req) "\n")
                   (str "- request: `content/requests/" (name req-id) ".edn`")
                   (str "- writer: `" writer-model "`, critic: `" critic-model "`")
                   (str "- GDD sections: " (str/join ", " (map #(str "§" %) (get-in req [:sources :gdd]))))
                   ""])
        say (fn [& xs] (let [s (apply str xs)] (println (str (name req-id) ": " s)) (swap! log conj s)))]
    (spit-run dir "context.md" context)
    (if dry-run
      (do (spit-run dir "writer-prompt-1.md" (writer-prompt req context nil))
          (spit-run dir "critic-prompt-example.md" (critic-prompt req context "<draft>"))
          (say "dry run: prompts saved in " dir) {:ok true :dir dir})
      (loop [n 1, prev nil]
        (say "\n## Draft " n "\n")
        (let [wp (writer-prompt req context prev)
              _  (spit-run dir (str "writer-prompt-" n ".md") wp)
              {:keys [text usage]} (call-model writer-model writer-system wp)
              _  (spit-run dir (str "draft-" n ".edn") text)
              {:keys [value error]} (read-edn text)
              errors (vec (if error [error] (validate req value)))
              _  (spit-run dir (str "validate-" n ".edn") (pretty errors))
              _  (say "- writer: " (:output_tokens usage) " tokens out; validator: "
                      (if (empty? errors) "PASS" (str (count errors) " errors")))
              _  (doseq [e (take 12 errors)] (say "  - " e))
              review (when (empty? errors)
                       (let [cp (critic-prompt req context (pretty value))
                             {ct :text} (call-model critic-model critic-system cp)
                             {cv :value cerr :error} (read-edn ct)]
                         (spit-run dir (str "critic-" n ".edn") ct)
                         (if cerr
                           {:verdict :revise :findings [{:severity :blocker :where "critic" :issue cerr :gdd "-" :fix "-"}]}
                           cv)))
              blockers (vec (filter #(= :blocker (:severity %)) (:findings review)))
              minors   (vec (filter #(= :minor (:severity %)) (:findings review)))]
          (when review
            (say "- critic: " (if (empty? blockers) "PASS" (str (count blockers) " blockers")) ", "
                 (count minors) " minor; \"" (:sounds-like-game review) "\"")
            (doseq [b blockers] (say "  - BLOCKER " (:where b) ": " (:issue b) " [" (:gdd b) "]"))
            (doseq [m minors] (say "  - minor " (:where m) ": " (:issue m) " [" (:gdd m) "]")))
          (cond
            (and (empty? errors) review (empty? blockers))
            (let [out (rpath (:out req))]
              (fs/create-dirs (fs/parent out))
              (spit out (str ";; GENERATED by the content pipeline (content/pipeline.clj) from docs/GDD.md.\n"
                             ";; Request: content/requests/" (name req-id) ".edn. Run: content/runs/" (fs/file-name dir) "/\n"
                             ";; Passed the validator and the critic on draft " n ". Edit the request, not this file.\n"
                             (pretty value)))
              (say "\n**Installed** `" (:out req) "` (draft " n ").")
              (spit-run dir "log.md" (str/join "\n" @log))
              {:ok true :dir dir :drafts n})

            (>= n (:max-drafts req 3))
            (do (say "\n**Not installed**: still failing after " n " drafts.")
                (spit-run dir "log.md" (str/join "\n" @log))
                {:ok false :dir dir :drafts n})

            :else
            (recur (inc n) {:draft-text (if value (pretty value) text) :errors errors :blockers blockers
                             :earlier (vec (concat (:earlier prev) (:blockers prev)))})))))))

(defn -main [& args]
  (let [dry (some #{"--dry-run"} args)
        ids (remove #(str/starts-with? % "--") args)
        ids (if (or (empty? ids) (= ["all"] ids))
              (sort (map #(str/replace (fs/file-name %) #"\.edn$" "") (fs/glob (rpath "content" "requests") "*.edn")))
              ids)
        results (doall (for [id ids] (run-request (keyword id) {:dry-run dry})))]
    (System/exit (if (every? :ok results) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
