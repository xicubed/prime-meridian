#!/usr/bin/env bb
;; Prime Meridian content pipeline (Assignment 4: dynamic content from the GDD;
;; Assignment 5: a scored Generator -> Evaluator -> Refiner loop).
;;
;;   bb content <request>            e.g. bb content hal-lines
;;   bb content all                  every request in content/requests/
;;   bb content <request> --dry-run  build and save the prompts, call no model
;;
;; One run, logged step by step under content/runs/<request>-<stamp>/:
;;   1. context    pull the GDD sections the request names (+ any repo files)
;;   2. generator  a Claude model drafts the content as EDN, in the GDD's voice
;;                 (draft 1 only)
;;   3. validate   plain code checks shape, coverage, lengths, placeholders,
;;                 banned words and the GDD's hard rules (no model involved)
;;   4. evaluator  a second, stronger model scores the draft 0-10 WITH A REASON
;;                 on each rule family of content/STYLE.md (A who's talking,
;;                 B what the world is, C what's never said, D look and length)
;;                 plus S specificity (could a stranger tell it's this game),
;;                 listing the violations behind each score with a fix
;;   5. threshold  overall = weighted mean (C x2, A x1.5, B x1, D x1, S x1.5);
;;                 a draft passes at 8.0 or better with no criterion under 6
;;   6. refiner    its own model call with its own prompt: the draft, the scores
;;                 and reasons, the violations with fixes, STYLE.md, and any
;;                 validator errors; returns a revised draft that changes only
;;                 what scored down (drafts 2..:max-drafts)
;;   7. install    only a draft that passes the validator and the threshold is
;;                 written to the request's :out file, which the game loads
;;
;; log.md shows the three roles and one trajectory line per draft
;; ("draft 2: 6.8 (A 7 B 8 C 5 D 8 S 7)"); scores.edn keeps every draft's
;; scores and reasons. Nobody intervenes between draft 1 and the result.
;;
;; Models: WRITER_MODEL (default claude-sonnet-5) for the Generator and the
;; Refiner, CRITIC_MODEL (default claude-opus-5-5) for the Evaluator, through
;; the `claude` CLI in print mode with no tools.
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

;; ----------------------------------------------------------------- scoring --
;; Pure functions (tested in content/pipeline_test.clj).

(def criteria
  "The Evaluator's criteria: STYLE.md's rule families A-D plus S, specificity.
   Weights from STYLE.md 'How a draft is scored'."
  [[:A "who's talking, and to whom (rules 1-5)" 1.5]
   [:B "what the world is (rules 6-10)" 1.0]
   [:C "what's never said (rules 11-15)" 2.0]
   [:D "look and length (rules 16-19)" 1.0]
   [:S "specificity: could a stranger tell these lines belong to Prime Meridian?" 1.5]])

(def weights (into {} (map (fn [[k _ w]] [k w]) criteria)))
(def criterion-keys (mapv first criteria))

(def pass-overall 8.0)
(def pass-each 6)

(defn- clamp-score [x]
  (if (number? x) (-> x double (max 0.0) (min 10.0) Math/round long) 0))

(defn parse-scores
  "The Evaluator's reply as a map criterion -> {:score 0-10 :reason :violations}.
   A missing or malformed criterion counts as 0 with the reason \"not scored\";
   violations are kept only when they are maps."
  [reply]
  (let [m (if (map? reply) reply {})]
    (into {}
          (for [k criterion-keys]
            (let [c (get m k)]
              [k (if (and (map? c) (number? (:score c)))
                   {:score (clamp-score (:score c))
                    :reason (if (string? (:reason c)) (:reason c) "no reason given")
                    :violations (vec (filter map? (:violations c)))}
                   {:score 0 :reason "not scored" :violations []})])))))

(defn overall
  "Weighted mean of the criterion scores, one decimal."
  [scores]
  (let [num (reduce + (map (fn [[k w]] (* w (get-in scores [k :score] 0))) weights))
        den (reduce + (vals weights))]
    (/ (Math/round (* 10.0 (/ num den))) 10.0)))

(defn passes?
  "The threshold: overall at least 8.0 and no criterion under 6."
  [scores]
  (and (>= (overall scores) pass-overall)
       (every? #(>= (get-in scores [% :score] 0) pass-each) criterion-keys)))

(defn trajectory-line
  "One log line per draft: `draft 2: 6.8 (A 7 B 8 C 5 D 8 S 7)`."
  [n scores]
  (str "draft " n ": " (overall scores) " ("
       (str/join " " (map #(str (name %) " " (get-in scores [% :score] 0)) criterion-keys))
       ")"))

(defn low-criteria
  "The criteria under the per-criterion bar, lowest first."
  [scores]
  (->> criterion-keys
       (filter #(< (get-in scores [% :score] 0) pass-each))
       (sort-by #(get-in scores [% :score] 0))))

(defn all-violations [scores]
  (vec (for [k criterion-keys, v (get-in scores [k :violations])] (assoc v :criterion k))))

;; ----------------------------------------------------------------- prompts --

(defn style-guide [] (slurp (rpath "content" "STYLE.md")))

(def writer-system
  "You are the Generator on Prime Meridian's development crew (GDD §4.2): you write the
digital twin's voice, the status bubbles, and sample content, as EDN data the game
loads. You write ONLY from the game's own design document and style guide, which you
are given. Everything must sound like Prime Meridian, never like generic game text.
Reply with the EDN value only: no prose, no code fence.")

(def scorer-system
  "You are the Evaluator on Prime Meridian's development crew. You score content the
Generator drafted against the game's style guide (every rule cites the design
document, which you are also given). You never give a pass/fail: every criterion gets
a score 0-10 AND a reason, and every point you take off names the line and the rule.
Reply with one EDN map only: no prose, no code fence.")

(def refiner-system
  "You are the Refiner on Prime Meridian's development crew. You receive a draft, the
Evaluator's scores and reasons, its violations with suggested fixes, and the style
guide. You revise the draft so the violations are gone and the low criteria come up,
changing ONLY what was scored down: lines that drew no violation stay word for word.
Reply with the complete revised EDN value only: no prose, no code fence.")

(defn writer-prompt
  "The Generator's prompt (draft 1): the GDD context, the style guide, the brief."
  [req context style]
  (str context
       "\n\n===\n\n# Source: content/STYLE.md (the style guide every line is scored against)\n\n" style
       "\n\n===\n\n# Your job: " (:title req) "\n\n" (:brief req)
       "\n\n## Exact shape\n\n" (:shape req)
       "\n\n## Hard rules (a script checks these)\n\n" (:rules req)))

(defn scorer-prompt
  "The Evaluator's prompt: GDD context, STYLE.md verbatim, the draft, the reply shape."
  [req context style draft-text]
  (str context
       "\n\n===\n\n# Source: content/STYLE.md (the rules; score against these and nothing else)\n\n" style
       "\n\n===\n\n# The Generator's draft: " (:title req) "\n\nWhat was asked:\n\n" (:brief req)
       "\n\nThe draft:\n\n" draft-text
       "\n\n===\n\n# Score it\n\n"
       "Score the draft on each criterion, 0-10, with a reason of one to three sentences that "
       "quotes the lines behind the score. 10 means every line keeps every rule in that family; "
       "take points off per violation, more for a rule broken in several lines. Under each criterion "
       "list the violations: which key/line, which STYLE.md rule number, what's wrong, and the fix "
       "(the rewritten line, or how to rewrite it). A criterion with no violations has an empty "
       "vector and a reason saying what it does well.\n\n"
       (str/join "\n" (map (fn [[k what]] (str "- " (name k) ": " what)) criteria))
       (when (:critic req) (str "\n\nRequest-specific notes (they fall under A and B):\n" (:critic req)))
       "\n\nReturn exactly:\n\n"
       "{:A {:score 0-10 :reason \"...\" :violations [{:where \"which key/line\" :rule 3 :issue \"...\" :fix \"...\"}]}\n"
       " :B {:score 0-10 :reason \"...\" :violations [...]}\n"
       " :C {:score 0-10 :reason \"...\" :violations [...]}\n"
       " :D {:score 0-10 :reason \"...\" :violations [...]}\n"
       " :S {:score 0-10 :reason \"...\" :violations [...]}}\n\n"
       "No :verdict, no pass/fail: the pipeline applies the threshold."))

(defn refiner-prompt
  "The Refiner's prompt: the draft, the scores and reasons, every violation with its fix,
   the validator's errors if any, and the style guide. Names every violation."
  [req context style {:keys [draft-text scores errors]}]
  (str context
       "\n\n===\n\n# Source: content/STYLE.md (the rules the scores rest on)\n\n" style
       "\n\n===\n\n# Refine this draft: " (:title req) "\n\nWhat was asked:\n\n" (:brief req)
       "\n\n## Exact shape\n\n" (:shape req)
       "\n\n## Hard rules (a script checks these)\n\n" (:rules req)
       "\n\n## The draft\n\n" draft-text
       (when (seq errors)
         (str "\n\n## Validator errors (a script found these; fix every one)\n"
              (str/join "\n" (map #(str "- " %) errors))))
       (when scores
         (str "\n\n## The Evaluator's scores (overall " (overall scores) "; passes at "
              pass-overall " with none under " pass-each ")\n"
              (str/join "\n" (for [k criterion-keys]
                               (str "- " (name k) " " (get-in scores [k :score]) "/10: " (get-in scores [k :reason]))))
              (let [vs (all-violations scores)]
                (if (seq vs)
                  (str "\n\n## Violations to fix (every one)\n"
                       (str/join "\n" (map #(str "- [" (name (:criterion %)) " rule " (:rule %) "] " (:where %)
                                                 ": " (:issue %) " -> fix: " (:fix %)) vs)))
                  "\n\nNo violations were listed; raise the low criteria using their reasons."))))
       "\n\nChange only the lines named above (and whatever the validator flagged); keep every other "
       "line word for word. Return the complete revised EDN."))

;; ------------------------------------------------------------------- run --

(defn stamp [] (.format (java.text.SimpleDateFormat. "yyyyMMdd-HHmm") (java.util.Date.)))

(defn spit-run [dir name content] (spit (str (fs/path dir name)) content))

(defn pretty [v] (with-out-str (pp/pprint v)))

(defn run-request [req-id {:keys [dry-run]}]
  (let [req (edn/read-string (slurp (rpath "content" "requests" (str (name req-id) ".edn"))))
        dir (rpath "content" "runs" (str (name req-id) "-" (stamp)))
        _   (fs/create-dirs dir)
        context (build-context req)
        style (style-guide)
        log (atom [(str "# Content run: " (:title req) "\n")
                   (str "- request: `content/requests/" (name req-id) ".edn`")
                   (str "- generator + refiner: `" writer-model "`, evaluator: `" critic-model "`")
                   (str "- GDD sections: " (str/join ", " (map #(str "§" %) (get-in req [:sources :gdd])))
                        "; style guide: `content/STYLE.md`")
                   (str "- threshold: overall ≥ " pass-overall ", no criterion under " pass-each
                        " (weights " (str/join " " (map (fn [[k _ w]] (str (name k) "×" w)) criteria)) ")")
                   ""])
        say (fn [& xs] (let [s (apply str xs)] (println (str (name req-id) ": " s)) (swap! log conj s)))
        finish (fn [ok n history]
                 (say "\n## Trajectory\n")
                 (doseq [{:keys [draft scores errors]} history]
                   (say "- " (if scores (trajectory-line draft scores)
                                 (str "draft " draft ": not scored (" (count errors) " validator errors)"))))
                 (spit-run dir "scores.edn" (pretty (mapv #(select-keys % [:draft :overall :passes :errors :scores]) history)))
                 (spit-run dir "log.md" (str/join "\n" @log))
                 {:ok ok :dir dir :drafts n :history history})]
    (spit-run dir "context.md" context)
    (if dry-run
      (do (spit-run dir "generator-prompt-1.md" (writer-prompt req context style))
          (spit-run dir "evaluator-prompt-example.md" (scorer-prompt req context style "<draft>"))
          (spit-run dir "refiner-prompt-example.md" (refiner-prompt req context style {:draft-text "<draft>" :scores (parse-scores {}) :errors []}))
          (say "dry run: prompts saved in " dir) {:ok true :dir dir})
      (loop [n 1, prev nil, history []]
        (say "\n## Draft " n " — " (if prev "Refiner" "Generator") "\n")
        (let [role (if prev "refiner" "generator")
              wp (if prev (refiner-prompt req context style prev) (writer-prompt req context style))
              _  (spit-run dir (str role "-prompt-" n ".md") wp)
              {:keys [text usage]} (call-model writer-model (if prev refiner-system writer-system) wp)
              _  (spit-run dir (str "draft-" n ".edn") text)
              {:keys [value error]} (read-edn text)
              errors (vec (if error [error] (validate req value)))
              _  (spit-run dir (str "validate-" n ".edn") (pretty errors))
              _  (say "- " role ": " (:output_tokens usage) " tokens out; validator: "
                      (if (empty? errors) "PASS" (str (count errors) " errors")))
              _  (doseq [e (take 12 errors)] (say "  - " e))
              draft-text (if value (pretty value) text)
              scores (when value
                       (let [sp (scorer-prompt req context style draft-text)
                             _  (spit-run dir (str "evaluator-prompt-" n ".md") sp)
                             {st :text} (call-model critic-model scorer-system sp)
                             {sv :value serr :error} (read-edn st)]
                         (spit-run dir (str "evaluator-" n ".edn") st)
                         (when serr (say "- evaluator: reply was " serr))
                         (parse-scores sv)))
              entry {:draft n :errors errors :scores scores
                     :overall (when scores (overall scores))
                     :passes (boolean (and scores (empty? errors) (passes? scores)))}
              history (conj history entry)]
          (when scores
            (say "- evaluator: " (trajectory-line n scores)
                 (if (passes? scores)
                   " — over the bar"
                   (str " — under the bar"
                        (when-let [low (seq (low-criteria scores))]
                          (str " (under " pass-each ": " (str/join ", " (map name low)) ")")))))
            (doseq [k criterion-keys]
              (say "  - " (name k) " " (get-in scores [k :score]) ": " (get-in scores [k :reason])))
            (doseq [v (all-violations scores)]
              (say "  - violation [" (name (:criterion v)) " rule " (:rule v) "] " (:where v) ": " (:issue v))))
          (cond
            (:passes entry)
            (let [out (rpath (:out req))]
              (fs/create-dirs (fs/parent out))
              (spit out (str ";; GENERATED by the content pipeline (content/pipeline.clj) from docs/GDD.md and content/STYLE.md.\n"
                             ";; Request: content/requests/" (name req-id) ".edn. Run: content/runs/" (fs/file-name dir) "/\n"
                             ";; Passed the validator and the threshold (" (overall scores) ") on draft " n ". Edit the request, not this file.\n"
                             (pretty value)))
              (say "\n**Installed** `" (:out req) "` (draft " n ", " (overall scores) ").")
              (finish true n history))

            (>= n (:max-drafts req 3))
            (do (say "\n**Not installed**: still under the bar after " n " drafts.")
                (finish false n history))

            :else
            (recur (inc n) {:draft-text draft-text :errors errors :scores scores} history)))))))

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
