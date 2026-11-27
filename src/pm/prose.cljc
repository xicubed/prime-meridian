(ns pm.prose
  "A Claude Code pane's own words: the assistant's prose blocks (⏺) out of a
   Herdr screen, never tool calls, tool output, diffs or file contents; the
   end-of-turn summary and question test; state/crew-said.edn's rules. Pure,
   portable (bb + cljs); may be required by pm.core. Never names a member."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

(def max-said 20)
(def summary-max 240)
(def said-grace-ms 5000)   ; see pm.talk/said-questions

(def ^:private rule-re #"^─{3,}$")
(def ^:private user-re #"^❯(?: |$)")
(def ^:private tool-head-re #"^⏺ [A-Za-z_][A-Za-z0-9_.:-]*\(")
;; Claude Code's own notices drawn with a ⏺ too (a background agent finished; the idle
;; compaction notice): not the member's words (manager fix, crew-mirror-v0 live check).
(def ^:private notice-re #"^⏺ (?:(?:Agent|Background command|Task) \"|Compacted\b)")
(def ^:private result-re #"^  ⎿")
(def ^:private summary-re #"^  [A-Z][a-z]+(?: for)? \d+ \S")
(def ^:private member-re #"^[a-z][a-z0-9_-]{0,31}$")

(defn- non-blank-str? [s]
  (and (string? s) (not (str/blank? s))))

(defn- read-edn [text]
  (try
    #?(:clj (edn/read-string text) :cljs (reader/read-string text))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- rule-line? [l] (boolean (re-matches rule-re l)))
(defn- user-line? [l] (boolean (re-find user-re l)))
(defn- bullet-line? [l] (str/starts-with? l "⏺"))
(defn- tool-head? [l] (boolean (re-find tool-head-re l)))
(defn- notice-line? [l] (boolean (re-find notice-re l)))
(defn- result-line? [l] (boolean (re-find result-re l)))
(defn- summary-line? [l] (boolean (re-find summary-re l)))

(defn- para-line? [l]
  (and (str/starts-with? l "  ") (not (str/blank? l)) (not (result-line? l))))

;; -- prose-blocks / last-said --

(defn- cut-composer
  "Drop the composer and everything under it: the lines up to (not
   including) the last rule line whose next line is the user's prompt.
   Everything, when none is found (for example, a dialog is up)."
  [ls]
  (let [n (count ls)
        i (last (keep (fn [i] (when (and (rule-line? (nth ls i))
                                          (< (inc i) n)
                                          (user-line? (nth ls (inc i))))
                                 i))
                      (range n)))]
    (if i (subvec ls 0 i) ls)))

(defn- para1-end
  "Index just past paragraph 1's para lines, starting right after the
   bullet line at i."
  [ls i]
  (let [n (count ls)]
    (loop [j (inc i)]
      (if (and (< j n) (para-line? (nth ls j)))
        (recur (inc j))
        j))))

(defn- blank-run-end [ls j]
  (let [n (count ls)]
    (loop [j j]
      (if (and (< j n) (str/blank? (nth ls j)))
        (recur (inc j))
        j))))

(defn- para-run-end [ls j]
  (let [n (count ls)]
    (loop [j j]
      (if (and (< j n) (para-line? (nth ls j)))
        (recur (inc j))
        j))))

(defn- later-paragraphs
  "[end paragraphs]: paragraphs is a vector of [start end) ranges for every
   later paragraph right after p1-end; end is where the block stops (right
   after the last one kept, before whatever follows)."
  [ls p1-end]
  (let [n (count ls)]
    (loop [j p1-end paras []]
      (let [j' (blank-run-end ls j)]
        (if (and (< j' n) (para-line? (nth ls j')) (not (summary-line? (nth ls j'))))
          (recur (para-run-end ls j') (conj paras [j' (para-run-end ls j')]))
          [j paras])))))

(defn- block-text [ls i p1-end paras]
  (let [first-line (nth ls i)
        other (concat (subvec ls (inc i) p1-end)
                       (mapcat (fn [[s e]] (subvec ls s e)) paras))
        raw (str/join " " (cons (subs first-line 1) other))]
    (-> raw (str/replace #"\s+" " ") str/trim)))

(defn- blocks-with-starts
  "Every prose block on ls (already composer-cut), as {:start :text}."
  [ls]
  (let [n (count ls)]
    (loop [i 0 out []]
      (if (>= i n)
        out
        (if-not (bullet-line? (nth ls i))
          (recur (inc i) out)
          (let [p1-end (para1-end ls i)
                skip?  (or (tool-head? (nth ls i))
                           (notice-line? (nth ls i))
                           (and (< p1-end n) (result-line? (nth ls p1-end))))]
            (if skip?
              (recur p1-end out)
              (let [[end paras] (later-paragraphs ls p1-end)]
                (recur end (conj out {:start i :text (block-text ls i p1-end paras)}))))))))))

(defn- screen-lines [screen]
  (when (and (string? screen) (not (str/blank? screen)))
    (cut-composer (mapv str/trimr (str/split-lines screen)))))

(defn prose-blocks [screen]
  (if-let [ls (screen-lines screen)]
    (mapv :text (blocks-with-starts ls))
    []))

(defn last-said [screen]
  (when-let [ls (screen-lines screen)]
    (let [u (or (last (keep-indexed (fn [i l] (when (user-line? l) i)) ls)) -1)]
      (:text (last (filter #(> (:start %) u) (blocks-with-starts ls)))))))

;; -- summary, question, turn end, roster --

(defn summary [text]
  (when (string? text)
    (if (<= (count text) summary-max)
      text
      (str (str/trimr (subs text 0 (dec summary-max))) "…"))))

(defn question? [text]
  (and (string? text) (str/ends-with? (str/trimr text) "?")))

(defn turn-ended? [prev now]
  (and (contains? #{:working :blocked} prev) (contains? #{:idle :done} now)))

(defn claude-members [herdr-list]
  (set (keep (fn [a]
               (when (and (map? a) (= "claude" (:agent a)) (string? (:name a))
                          (re-matches member-re (:name a)))
                 (keyword (:name a))))
             (get-in herdr-list [:result :agents]))))

;; -- state/crew-said.edn --

(defn add-said [said member text now]
  (if-not (and (keyword? member) (non-blank-str? text) (integer? now))
    said
    (let [said  (or said {:as-of now :said {}})
          s     (summary (str/trim text))
          q     (question? text)
          items (get-in said [:said member] [])]
      (if (= s (:text (peek items)))
        said
        (let [at (if (seq items) (max now (inc (:at (peek items)))) now)]
          (-> said
              (assoc :as-of now)
              (assoc-in [:said member]
                        (vec (take-last max-said (conj items {:at at :text s :question? q}))))))))))

(defn- valid-said-item? [it]
  (and (map? it)
       (integer? (:at it)) (not (neg? (:at it)))
       (non-blank-str? (:text it)) (<= (count (:text it)) summary-max)
       (boolean? (:question? it))))

(defn parse-said [text]
  (when (string? text)
    (let [v (read-edn text)]
      (when (and (map? v) (map? (:said v)))
        (let [as-of (:as-of v)
              as-of (if (and (integer? as-of) (not (neg? as-of))) as-of 0)
              said  (into {}
                          (keep (fn [[k items]]
                                  (when (and (keyword? k) (re-matches member-re (name k))
                                             (sequential? items))
                                    (let [valid (->> items
                                                      (filter valid-said-item?)
                                                      (mapv #(select-keys % [:at :text :question?]))
                                                      (take-last max-said)
                                                      vec)]
                                      (when (seq valid) [k valid])))))
                          (:said v))]
          {:as-of as-of :said said})))))
