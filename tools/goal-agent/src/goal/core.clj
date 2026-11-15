(ns goal.core
  "The goal agent's reasoning layer, pure: scanning file text into a summary,
   deciding which GDD requirements are done / partial / missing, scoring the
   gaps, and picking the next one. No I/O here; goal_agent.clj does that."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; ------------------------------------------------------------------ scan --

(def ^:private def-re #"(?m)^\((def[a-z-]*)\s+(?:\^[^\s]+\s+)*([^\s\[\]\(\)]+)")
(def ^:private ns-re #"^\s*(?:;[^\n]*\n|\s)*\(ns\s+([^\s\)]+)")

(defn scan-file
  "{:path :ns :defs [names] :tests [names]} for one source file's text."
  [path text]
  (let [defs (re-seq def-re text)]
    {:path  path
     :ns    (second (re-find ns-re text))
     :defs  (vec (distinct (for [[_ kind n] defs :when (not= kind "deftest")] n)))
     :tests (vec (distinct (for [[_ kind n] defs :when (= kind "deftest")] n)))}))

(defn scan-summary
  "One line per file: path (ns): defs; tests. What the models see of the code."
  [scan]
  (str/join "\n"
            (for [{:keys [path ns defs tests]} (sort-by :path scan)]
              (str path (when ns (str " (" ns ")")) ": "
                   (str/join ", " (take 60 defs))
                   (when (seq tests) (str "; tests: " (str/join ", " (take 30 tests))))))))

;; ------------------------------------------------------------------ gaps --

(defn probe-hit?
  "Does one evidence probe {:path prefix :re regex} match some file in
   `files` ({path text})?"
  [files {:keys [path re]}]
  (let [pat (re-pattern re)]
    (boolean (some (fn [[p text]] (and (str/starts-with? p path) (re-find pat text))) files))))

(defn status
  "How much of a requirement exists: :done when every evidence probe hits,
   :partial when some do, :missing when none do (or it has no probes)."
  [files req]
  (let [ev   (:evidence req)
        hits (count (filter #(probe-hit? files %) ev))]
    (cond (and (seq ev) (= hits (count ev))) :done
          (pos? hits) :partial
          :else :missing)))

(defn gaps
  "{id {:status s :hits n :of m}} for every requirement."
  [files reqs]
  (into {} (for [r reqs]
             [(:id r) {:status (status files r)
                       :hits   (count (filter #(probe-hit? files %) (:evidence r)))
                       :of     (count (:evidence r))}])))

;; ----------------------------------------------------------------- score --

(defn days-between
  "Whole days from date string a to date string b (yyyy-MM-dd); negative when b is earlier."
  [a b]
  (.between java.time.temporal.ChronoUnit/DAYS (java.time.LocalDate/parse a) (java.time.LocalDate/parse b)))

(defn urgency
  "How pressing a milestone is from `today`: 2 on its due date, rising to 4
   for work two or more weeks overdue, falling toward 0 for far-off work."
  [today milestone]
  (let [days (days-between today milestone)]
    (if (<= days 0)
      (+ 2.0 (min 2.0 (/ (- days) 7.0)))
      (/ 2.0 (+ 1.0 (/ days 7.0))))))

(defn dependents
  "Ids of the requirements that depend on `id`, directly or through others."
  [reqs id]
  (loop [frontier #{id} seen #{}]
    (let [next (set (for [r reqs :when (some frontier (:depends r))] (:id r)))
          new  (set/difference next seen)]
      (if (empty? new) seen (recur new (into seen new))))))

(defn priority-score
  "The order the agent builds things in. Higher first; 0 = don't build.
     urgency (milestone vs today) x demo (2 if the demo shows it, else 1)
     x (1 + open requirements this one unblocks)
     x 0.25 when one of its own dependencies is still open
     / cost (size 1-5, x0.6 when partly built).
   Deferred and done requirements score 0."
  [req gap-map reqs today]
  (let [st (get-in gap-map [(:id req) :status])]
    (if (or (:deferred? req) (= st :done) (= st :parked))
      0.0
      (let [open?    (fn [id] (not= :done (get-in gap-map [id :status])))
            unlocks  (count (filter open? (dependents reqs (:id req))))
            blocked? (some open? (:depends req))
            cost     (* (max 1 (or (:size req) 3)) (if (= st :partial) 0.6 1.0))
            s        (/ (* (urgency today (:milestone req))
                           (if (:demo? req) 2.0 1.0)
                           (+ 1.0 unlocks)
                           (if blocked? 0.25 1.0))
                        cost)]
        (/ (Math/round (* s 100.0)) 100.0)))))

(defn ranked
  "[{:req r :score s :status st}] sorted best first; ties go to the smaller
   job, then the id, so a run is repeatable."
  [reqs gap-map today]
  (->> reqs
       (map (fn [r] {:req r :score (priority-score r gap-map reqs today)
                     :status (get-in gap-map [(:id r) :status])}))
       (sort-by (fn [{:keys [req score]}] [(- score) (or (:size req) 3) (name (:id req))]))
       vec))

(defn pick-next
  "The requirement to build next, or nil when nothing scores above 0."
  [reqs gap-map today]
  (let [top (first (ranked reqs gap-map today))]
    (when (and top (pos? (:score top))) top)))

(defn narrow
  "The Director's `--only` list: every requirement stays in view (so a
   dependency that's already done still counts as done, and what a pick
   unblocks is counted against the whole list), but the ones outside `ids`
   are set aside like deferred ones: they score 0 and don't hold up the
   stop rule. Filtering the list instead made a done dependency look open
   and cut its dependents' scores to a quarter. No `ids`: unchanged."
  [reqs ids]
  (if (seq ids)
    (mapv #(if (contains? (set ids) (:id %)) % (assoc % :deferred? true)) reqs)
    reqs))

(defn all-met?
  "True when every requirement that isn't deferred is done."
  [reqs gap-map]
  (every? #(or (:deferred? %) (= :done (get-in gap-map [(:id %) :status]))) reqs))

;; ------------------------------------------------------------- the guard --

(def allowed-prefixes ["src/pm/" "test/pm/" "resources/" "scripts/"])

(defn outside-allowed
  "Changed paths the writer wasn't allowed to touch."
  [paths]
  (vec (remove (fn [p] (some #(str/starts-with? p %) allowed-prefixes)) paths)))

(defn valid-requirement?
  "Shape check for one entry of requirements.edn."
  [r]
  (and (map? r) (keyword? (:id r)) (string? (:title r)) (string? (:milestone r))
       (re-matches #"\d{4}-\d{2}-\d{2}" (:milestone r))
       (vector? (:evidence r))
       (every? #(and (string? (:path %)) (string? (:re %))) (:evidence r))
       (every? keyword? (:depends r []))))
