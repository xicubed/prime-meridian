(ns pm.content
  "Generated game text (resources/content/*.edn), laid over the world. Pure:
   no atoms, no js/Date. Never names a mirror."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [shadow.resource :as rc]))

(defn parse [text]
  (when (string? text)
    (let [v (try (reader/read-string text) (catch :default _ nil))]
      (when (map? v) v))))

(def hal-lines (parse (rc/inline "content/hal-lines.edn")))
(def mirror-lines (parse (rc/inline "content/mirror-lines.edn")))
(def sample-inbox (parse (rc/inline "content/sample-inbox.edn")))

(def hal-events
  "The Hal events the game uses -> the placeholders each fills."
  {:jack-in       #{}
   :chore-done    #{:name :chore}
   :chore-not-due #{:name :chore :last-at :next-at}
   :chore-undone  #{:name :chore}})

(def hal-ms 6000)

(defn day-number [now]
  (if (and (number? now) (js/isFinite now))
    (js/Math.floor (/ now 86400000))
    0))

(defn name-code [k]
  (if (keyword? k)
    (reduce + 0 (map #(.charCodeAt % 0) (name k)))
    0))

(defn fill
  "Fill `{field}` placeholders in `s` from `vars`; nil/missing field keeps
   the placeholder. `s` not a string -> nil. Never throws."
  [s vars]
  (when (string? s)
    (str/replace s #"\{([a-z0-9-]+)\}"
                 (fn [[whole field-name]]
                   (let [v (get vars (keyword field-name))]
                     (if (nil? v) whole (str v)))))))

(defn placeholders [s]
  (if (string? s)
    (set (map (fn [[_ field]] (keyword field)) (re-seq #"\{([a-z0-9-]+)\}" s)))
    #{}))

(defn- usable?
  "A string, or a non-empty sequential whose every element is a string."
  [v]
  (or (string? v)
      (and (sequential? v) (seq v) (every? string? v))))

(defn line [table path vars seed]
  (if-not (and (map? table) (sequential? path))
    nil
    (let [v (get-in table path)]
      (cond
        (string? v) (fill v vars)
        (and (sequential? v) (seq v))
        (let [s      (if (integer? seed) seed 0)
              vv     (vec v)
              picked (nth vv (mod s (count vv)))]
          (when (string? picked) (fill picked vars)))
        :else nil))))

(defn used-lines
  "Set of [mirror-id key] for every keyword :say/:sign in a mirror's :rules
   and every keyword :idle in its :placed, over all of (:mirrors world)."
  [world]
  (set
    (mapcat
      (fn [[id m]]
        (concat
          (keep (fn [r] (let [v (:say r)] (when (keyword? v) [id v]))) (:rules m))
          (keep (fn [r] (let [v (:sign r)] (when (keyword? v) [id v]))) (:rules m))
          (keep (fn [p] (let [v (:idle p)] (when (keyword? v) [id v]))) (:placed m))))
      (:mirrors world))))

(defn missing-lines [world table]
  (set (filter (fn [[id k]] (not (usable? (get-in table [:mirrors id k]))))
               (used-lines world))))

(defn missing-hal [table]
  (set (filter (fn [k] (not (usable? (get table k)))) (keys hal-events))))

(defn hal-say [world table event vars now]
  (if (number? now)
    (let [t (line table [event] vars (+ (day-number now) (count (:log world))))]
      (if (string? t)
        (assoc world :hal {:event event :text t :until (+ now hal-ms)})
        world))
    world))

(defn hal-on-chore [world table]
  (let [e (peek (:log world))]
    (if (= :counted (:result e))
      (hal-say world table :chore-done
               {:name  (or (get-in world [:mirrors (:mirror e) :name]) (name (:mirror e)))
                :chore (name (:chore e))}
               (:at e))
      world)))

(defn hal-text [world now]
  (let [{:keys [text until]} (:hal world)]
    (when (and (string? text) (number? now) (number? until) (< now until))
      text)))

(defn hal-segment [world now]
  (when-let [t (hal-text world now)]
    (str "Hal: " t)))

(defn mail-counts [inbox]
  (if-not (and (map? inbox) (sequential? (:messages inbox)))
    nil
    (let [msgs    (filter map? (:messages inbox))
          flagged (count (filter (fn [m] (let [n (:clerk-note m)]
                                            (and (string? n) (not (str/blank? n)))))
                                  msgs))
          mail    (- (count msgs) flagged)]
      {:mail mail :flagged flagged})))

(defn mail-line [inbox]
  (when-let [{:keys [mail flagged]} (mail-counts inbox)]
    (if (zero? flagged)
      (str "mail " mail)
      (str "mail " mail " · " flagged " flagged"))))

(defn hud-segments [world inbox now]
  (vec (remove nil? [(mail-line inbox) (hal-segment world now)])))
