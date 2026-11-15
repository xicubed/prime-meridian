#!/usr/bin/env bb
;; Prime Meridian goal agent (Assignment 5: a goal-oriented coding agent).
;; It reads the GDD, scans the game's code, finds what the GDD asks for that
;; isn't built yet, decides what to build first, builds it, checks it, and
;; repeats until the requirements are met or it runs out of turns.
;;
;;   bb goal-agent extract [--force]   GDD -> tools/goal-agent/requirements.edn (a model drafts
;;                                     it once; a person reviews it before it is used)
;;   bb goal-agent plan [--today D]    scan + gaps + priority scores, no writes
;;   bb goal-agent run [--max N] [--only ID,ID] [--dry-run] [--today D]   (--only: the Director narrows the goals)
;;                                     the loop: pick -> write -> gate -> re-scan -> next
;;   bb goal-agent test                unit tests of the reasoning layer (goal.core)
;;
;; The loop, step by step (logged under tools/goal-agent/runs/<stamp>/):
;;   1. scan      every source, test and data file: namespace, definitions, tests
;;   2. gaps      each requirement's evidence probes (regexes over the code) say
;;                done / partial / missing
;;   3. score     goal.core/priority-score: urgency x demo x unblocks / size
;;   4. write     the top gap goes to the writer (Claude Code in print mode by
;;                default, any CLI agent via config.edn) with the requirement,
;;                its GDD text, the scan, and the repo's rules
;;   5. check     the writer may only touch src/pm, test/pm, resources and scripts;
;;                then the game's own gate (.crew/gate.sh: 0 warnings, all
;;                tests pass) and the requirement's probes must pass
;;   6. retry     gate failures and missed probes go back to the writer, up to
;;                :max-attempts; after that the attempt is stashed (git stash,
;;                recoverable) and the gap is parked
;;   7. repeat    re-scan and re-score until all requirements are met, nothing
;;                scores above 0, or --max iterations are used
;; It never commits: the Director approves every change (GDD §4.2).
(ns goal-agent
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [goal.core :as g]))

(def here (str (fs/parent (fs/absolutize *file*))))
(def root (str (fs/parent (fs/parent here))))
(defn rpath [& xs] (str (apply fs/path root xs)))
(defn hpath [& xs] (str (apply fs/path here xs)))

(def config (edn/read-string (slurp (hpath "config.edn"))))

(defn pretty [v] (with-out-str (pp/pprint v)))
(defn stamp [] (.format (java.text.SimpleDateFormat. "yyyyMMdd-HHmm") (java.util.Date.)))
(defn today-str [] (str (java.time.LocalDate/now)))

;; ------------------------------------------------------------------ files --

(def scan-roots ["src/pm" "test/pm" "resources" "scripts"])

(defn code-files
  "{repo-relative-path text} for every .clj/.cljs/.cljc/.edn file the agent scans."
  []
  (into (sorted-map)
        (for [d scan-roots
              :when (fs/exists? (rpath d))
              f (fs/glob (rpath d) "**.{clj,cljs,cljc,edn}")
              :let [rel (str (fs/relativize root f))]]
          [rel (slurp (str f))])))

(defn scan [files] (vec (for [[p t] files] (g/scan-file p t))))

;; -------------------------------------------------------------------- GDD --

(defn gdd-sections
  "docs/GDD.md as [[heading level text] ...]."
  []
  (let [lines (str/split-lines (slurp (rpath "docs" "GDD.md")))]
    (loop [ls lines, cur nil, level 0, acc [], out []]
      (if-let [l (first ls)]
        (if-let [[_ hashes text] (re-matches #"^(#{1,4}) (.*)$" l)]
          (recur (rest ls) text (count hashes) [l]
                 (if cur (conj out [cur level (str/join "\n" acc)]) out))
          (recur (rest ls) cur level (conj acc l) out))
        (cond-> out cur (conj [cur level (str/join "\n" acc)]))))))

(defn section [secs id]
  (when-let [idx (first (keep-indexed (fn [i [h]] (when (str/starts-with? h (str id " ")) i)) secs))]
    (let [[_ level _] (nth secs idx)
          sub (take-while (fn [[_ l]] (> l level)) (drop (inc idx) secs))]
      (str/join "\n\n" (map #(nth % 2) (cons (nth secs idx) sub))))))

;; ------------------------------------------------------------------ model --

(defn run-cli
  "Run one model call through the configured CLI. `kind` is :extract or
   :write (config.edn :models); returns {:text :cost}. :write calls run in
   the repo with file tools; :extract calls run tool-less in a temp dir."
  [kind system prompt]
  (let [{:keys [cmd model]} (get-in config [:writers (:writer config)])
        tools? (= kind :write)
        dir    (if tools? root (str (fs/create-temp-dir {:prefix "pm-goal-"})))
        model  (get-in config [:models kind] model)
        argv   (-> (vec cmd)
                   (into ["--model" model "--system-prompt" system])
                   (into (if tools?
                           ["--permission-mode" "acceptEdits" "--allowedTools" "Read,Edit,Write,Glob,Grep"]
                           ["--tools" ""])))
        res    (apply p/shell {:in prompt :out :string :err :string :dir dir :continue true} argv)]
    (when-not tools? (fs/delete-tree dir))
    (when-not (zero? (:exit res))
      (throw (ex-info (str "writer exited " (:exit res) ": " (str/trim (:err res))) {})))
    (let [j (json/parse-string (:out res) true)]
      (when (:is_error j) (throw (ex-info (str "model error: " (:result j)) {})))
      {:text (:result j) :cost (:total_cost_usd j)})))

(defn read-edn-reply [text]
  (let [t (str/trim text)
        t (if-let [[_ body] (re-find #"(?s)```(?:edn|clojure)?\s*\n(.*?)```" t)] body t)]
    (edn/read-string t)))

;; ---------------------------------------------------------------- extract --

(def extract-system
  "You turn a game design document into a checklist a coding agent can verify against the code.
Reply with one EDN vector and nothing else.")

(defn extract-prompt [secs summary]
  (str "Below are Prime Meridian's game design document (docs/GDD.md) and a one-line-per-file scan of its code.

Make the checklist of features the GDD's course milestones (§4.8 Scope and milestones: the rows for the weeks of Oct 26, Nov 2, Nov 9 and Nov 16) say must exist, one entry per separately checkable feature (split a row's \"done when\" into its parts). Read the sections those rows rely on for what each part means. Things §4.8 lists as deferred past Nov 24 get :deferred? true if they appear at all.

Each entry is a map:
  :id          keyword, kebab-case, e.g. :closing-time
  :title       one line
  :gdd         the GDD section numbers it rests on, e.g. [\"4.8\" \"2.1\"]
  :milestone   the row's week as \"yyyy-MM-dd\" (Oct 26 row = \"2026-10-26\", ... Nov 16 row = \"2026-11-16\")
  :demo?       true if the class demo would show it on screen
  :depends     ids of other entries that must exist first ([] if none)
  :size        1-5, how big a job it is in this codebase (1 = an evening's small change)
  :deferred?   true only for deferred items
  :done-when   one sentence a reviewer can check in the running game
  :evidence    1-3 probes {:path \"src/pm/\" :re \"<Java regex>\"} that are true only when the feature exists.
               When the scan shows the feature is already built, point the probes at the real names you see.
               When it isn't, name what a natural implementation in this codebase would define (namespace
               pm.<x>, functions, a deftest) so the probe becomes its acceptance check. Escape regex special
               characters; prefer \"defn-? closing-time\\\\?\" style probes over prose (defn-? because
               this codebase makes many functions private with defn-).

# Code scan (path (namespace): definitions; tests)

" summary "

# docs/GDD.md

" (slurp (rpath "docs" "GDD.md"))))

(defn extract! [{:keys [force]}]
  (let [out (hpath "requirements.edn")]
    (when (and (fs/exists? out) (not force))
      (println "requirements.edn exists (reviewed); pass --force to redraft it") (System/exit 1))
    (let [summary (g/scan-summary (scan (code-files)))
          {:keys [text cost]} (run-cli :extract extract-system (extract-prompt (gdd-sections) summary))
          reqs (read-edn-reply text)
          bad  (remove g/valid-requirement? reqs)]
      (when (seq bad) (println "invalid entries:" (pretty bad)))
      (spit out (str ";; Drafted from docs/GDD.md by `bb goal-agent extract` (" (today-str) "), then reviewed.\n"
                     ";; Each entry is one GDD feature; :evidence probes decide whether it exists.\n"
                     (pretty (vec reqs))))
      (println (count reqs) "requirements ->" out (format "($%.2f)" (or cost 0.0))))))

;; ------------------------------------------------------------------- plan --

(defn requirements []
  (let [reqs (edn/read-string (slurp (hpath "requirements.edn")))]
    (doseq [r reqs :when (not (g/valid-requirement? r))]
      (throw (ex-info (str "bad requirement " (:id r)) {:r r})))
    reqs))

(defn plan-table [rows]
  (str "| # | score | status | requirement | milestone | size | demo |\n|---|---|---|---|---|---|---|\n"
       (str/join "\n"
                 (map-indexed (fn [i {:keys [req score status]}]
                                (format "| %d | %.2f | %s | `%s` %s | %s | %s | %s |" (inc i) score (name status)
                                        (name (:id req)) (:title req) (:milestone req) (:size req)
                                        (if (:demo? req) "yes" "")))
                              rows))))

(defn plan! [{:keys [today]}]
  (let [reqs (requirements)
        gm   (g/gaps (code-files) reqs)
        rows (g/ranked reqs gm today)]
    (println (plan-table rows))
    (println (format "\n%d done, %d partial, %d missing, %d deferred"
                     (count (filter #(= :done (:status %)) rows))
                     (count (filter #(= :partial (:status %)) rows))
                     (count (filter #(and (= :missing (:status %)) (not (:deferred? (:req %)))) rows))
                     (count (filter #(:deferred? (:req %)) rows))))))

;; -------------------------------------------------------------------- git --

(defn sh [& args] (apply p/shell {:out :string :err :string :dir root :continue true} args))

(defn changed-paths
  "Paths git sees as changed or new, outside the agent's own run logs."
  []
  (->> (str/split-lines (:out (sh "git" "status" "--porcelain" "--untracked-files=all")))
       (remove str/blank?)
       (map #(str/trim (subs % 3)))
       (remove #(str/starts-with? % "tools/goal-agent/runs/"))
       vec))

(defn fingerprint
  "{path content-hash} for every changed path: lets the agent tell the writer's
   edits from changes that were there before it ran (e.g. the agent's own files)."
  []
  (into {} (for [path (changed-paths)]
             [path (let [f (rpath path)] (if (fs/regular-file? f) (hash (slurp f)) :gone))])))

(defn writer-changes [baseline]
  (vec (for [[path h] (fingerprint) :when (not= h (get baseline path))] path)))

(defn stash! [id paths]
  (when (seq paths)
    (apply sh "git" "stash" "push" "--include-untracked" "-m" (str "goal-agent parked " (name id)) "--" paths)))

;; ------------------------------------------------------------------ write --

(def write-system
  "You are the code-writing step of Prime Meridian's goal agent. You implement exactly one missing
feature of the game, in the repo you are running in, following its AGENTS.md. You edit files; you never
commit, install packages, or touch anything outside src/pm/, test/pm/, resources/ and scripts/ (Babashka
scripts the game runs, started with `bb scripts/<name>.clj`; bb.edn is off limits).")

(defn write-prompt [req secs summary feedback]
  (str "Implement this feature from the game design document:

" (pretty (select-keys req [:id :title :gdd :milestone :done-when :depends])) "

It counts as built when all of these evidence probes match (path prefix + Java regex over the files):
" (pretty (:evidence req)) "

Rules (from AGENTS.md and the GDD): ClojureScript for game code, all three.js through pm.three, world
state in pm.core/!world, pure rules in their own namespace with cljs.test tests in test/pm/*_test.cljs
(the gate compiles `app` and `test` with 0 warnings and runs every test), `now` passed in as an argument
in pure code, no species names (horse, dog, pony, juniper) outside src/pm/species.cljs, no player or twin
names (\"Hal\", \"Michael\") in code, everything a player sees is data or comes from the world atom, and it
must work in a public clone without the private kit. Keep the change small: the smallest version that
satisfies the probes and the done-when, wired into the running game so it shows on screen.

Read the code you need first (start from the files the scan points to). When you are done, reply with
three short lines: FILES: the files you changed; WHAT: what the feature does; HOW TO SEE IT: what to look
at in the running game.

# The GDD sections it rests on

" (str/join "\n\n---\n\n" (keep #(section secs %) (:gdd req))) "

# Code scan (path (namespace): definitions; tests)

" summary
       (when feedback (str "\n\n# Your previous attempt did not pass. Fix it:\n\n" feedback))))

(defn gate!
  "Run the game's gate; {:pass? bool :log text}."
  []
  (let [res (p/shell {:out :string :err :out :dir root :continue true
                      :extra-env {"PATH" (str "/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:" (System/getenv "PATH"))
                                  "JAVA_HOME" "/opt/homebrew/opt/openjdk@21"}}
                     "bash" ".crew/gate.sh")]
    {:pass? (zero? (:exit res)) :log (:out res)}))

(defn gate-feedback [log]
  (let [lines (str/split-lines log)
        fails (filter #(re-find #"FAIL in|ERROR in|WARNING|Exception|expected:|actual:|GATE:" %) lines)]
    (str/join "\n" (take 80 (concat fails ["" "--- last lines ---"] (take-last 30 lines))))))

;; ------------------------------------------------------------------- loop --

(defn log! [dir & xs]
  (let [line (apply str xs)]
    (println line)
    (spit (str (fs/path dir "log.md")) (str line "\n") :append true)))

(defn attempt!
  "Build one requirement: write, check, retry. Returns :done or :parked."
  [dir n {:keys [req score]} secs dry-run baseline]
  (let [max-attempts (:max-attempts config 3)
        id (name (:id req))]
    (loop [k 1 feedback nil]
      (let [summary (g/scan-summary (scan (code-files)))
            prompt  (write-prompt req secs summary feedback)
            base    (format "%02d-%s-attempt%d" n id k)]
        (spit (str (fs/path dir (str base "-prompt.md"))) prompt)
        (if dry-run
          (do (log! dir "- dry run: prompt saved as `" base "-prompt.md`, no model called") :parked)
          (let [{:keys [text cost]} (run-cli :write write-system prompt)
                _       (spit (str (fs/path dir (str base "-reply.md"))) text)
                paths   (writer-changes baseline)
                outside (g/outside-allowed paths)
                _       (log! dir (format "- attempt %d: writer changed %s (%s)" k (pr-str paths) (format "$%.2f" (or cost 0.0))))
                problem (cond
                          (empty? paths) "You changed no files."
                          (seq outside) (str "You changed files outside src/pm/, test/pm/, resources/ and scripts/: " (pr-str outside)
                                             ". Undo those changes.")
                          :else (let [{:keys [pass? log]} (gate!)]
                                  (spit (str (fs/path dir (str base "-gate.txt"))) log)
                                  (log! dir "- gate: " (if pass? "PASS" "FAIL") " (" (str/trim (or (last (filter #(str/starts-with? % "GATE:") (str/split-lines log))) "")) ")")
                                  (cond
                                    (not pass?) (str "The gate (.crew/gate.sh) failed:\n\n" (gate-feedback log))
                                    (not= :done (g/status (code-files) req))
                                    (str "The gate passes, but these evidence probes still don't match: "
                                         (pr-str (remove #(g/probe-hit? (code-files) %) (:evidence req))))
                                    :else nil)))]
            (cond
              (nil? problem) (do (log! dir "- **built** `" id "`: gate passes and every probe matches") :done)
              (< k max-attempts) (do (log! dir "- not yet: " (first (str/split-lines problem))) (recur (inc k) problem))
              :else (do (stash! (:id req) (writer-changes baseline))
                        (log! dir "- **parked** `" id "` after " k " attempts; the attempt is in `git stash list`")
                        :parked))))))))

(defn run! [{:keys [max only dry-run today]}]
  (when-let [other (seq (remove (fn [p] (some #(str/starts-with? p %) (:preexisting-ok config))) (changed-paths)))]
    (println "The working tree has changes; commit or stash them first so the agent's diff is its own:")
    (println (str/join "\n" other)) (System/exit 1))
  (let [dir  (hpath "runs" (stamp))
        secs (gdd-sections)
        reqs (cond->> (requirements) only (filter #(contains? (set (map keyword (str/split only #","))) (:id %))))]
    (fs/create-dirs dir)
    (log! dir "# Goal agent run " (stamp) "\n\ntoday " today ", writer " (name (:writer config)) ", max " max " iteration(s)" (when dry-run ", dry run") "\n")
    (loop [n 1 parked #{}]
      (let [gm   (reduce #(assoc-in %1 [%2 :status] :parked) (g/gaps (code-files) reqs) parked)
            rows (g/ranked reqs gm today)]
        (spit (str (fs/path dir (format "%02d-plan.md" n))) (plan-table rows))
        (cond
          (g/all-met? reqs gm) (log! dir "\n## Stop: every requirement is met")
          (> n max) (log! dir "\n## Stop: used " max " iteration(s); next would be `" (some-> (g/pick-next reqs gm today) :req :id name) "`")
          :else
          (if-let [top (g/pick-next reqs gm today)]
            (let [{:keys [req score]} top
                  runner-up (second rows)]
              (log! dir "\n## Iteration " n ": `" (name (:id req)) "` (score " score ", " (name (:status top)) ")\n")
              (log! dir "- why: " (:title req) "; milestone " (:milestone req) ", size " (:size req)
                    (when (:demo? req) ", shown in the demo")
                    (when-let [d (seq (filter #(= :missing (get-in gm [% :status])) (g/dependents reqs (:id req))))]
                      (str ", unblocks " (str/join ", " (map name d))))
                    (when runner-up (str "; next best `" (name (:id (:req runner-up))) "` at " (:score runner-up))))
              (let [result (attempt! dir n top secs dry-run (fingerprint))]
                (recur (inc n) (cond-> parked (= result :parked) (conj (:id req))))))
            (log! dir "\n## Stop: nothing left scores above 0")))))
    (println "log:" (str (fs/relativize root (fs/path dir "log.md"))))))

;; ------------------------------------------------------------------- main --

(defn opts [args]
  (loop [[a & more] args, o {:max 1 :today (today-str)}]
    (case a
      nil o
      "--force"   (recur more (assoc o :force true))
      "--dry-run" (recur more (assoc o :dry-run true))
      "--max"     (recur (rest more) (assoc o :max (parse-long (first more))))
      "--only"    (recur (rest more) (assoc o :only (first more)))
      "--today"   (recur (rest more) (assoc o :today (first more)))
      (recur more o))))

(defn -main [& [cmd & args]]
  (let [o (opts args)]
    (case cmd
      "extract" (extract! o)
      "plan"    (plan! o)
      "run"     (run! o)
      (do (println "usage: bb goal-agent extract [--force] | plan [--today yyyy-MM-dd] | run [--max N] [--only ID] [--dry-run] | test")
          (System/exit 2)))))

(apply -main *command-line-args*)
