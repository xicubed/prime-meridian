(ns pm.overlay
  "The twin's state file, laid over the world. Pure: no js/fetch, no
   js/Date, no atoms. Never names a mirror; works from each mirror's own
   :state and :chores."
  (:require [cljs.reader :as reader]
            [pm.world :as w]
            [pm.jumeaux :as j]))

(defn parse-file [text]
  (when (string? text)
    (try (reader/read-string text) (catch :default _ nil))))

(defn- accept?
  "Can field `k` of mirror `m` take value `v` from the file?"
  [m k v]
  (let [state (:state m)
        times (set (keep :done-at (:chores m)))
        num?  (fn [x] (and (number? x) (js/isFinite x)))]
    (cond
      (= k :source) false
      (not (contains? state k)) false
      (contains? times k) (or (nil? v) (num? v))
      :else
      (let [cur (get state k)]
        (cond
          (and (number? cur) (js/isFinite cur)) (num? v)
          (string? cur)  (string? v)
          (keyword? cur) (keyword? v)
          (boolean? cur) (boolean? v)
          :else false)))))

(defn- merge-entries
  "Merge `entries` (a map id->{:state {...}}) into `world`'s mirrors, as far
   as `accept?` allows. Returns `[world' changed?]`, `changed?` true when at
   least one field of at least one mirror was accepted."
  [world entries]
  (reduce
    (fn [[w changed?] [id entry]]
      (let [mirror (get-in w [:mirrors id])]
        (if (and mirror (map? entry) (map? (:state entry)))
          (let [accepted (into {} (filter (fn [[k v]] (accept? mirror k v))
                                           (:state entry)))]
            (if (seq accepted)
              [(-> w
                   (update-in [:mirrors id :state] merge accepted)
                   (assoc-in [:mirrors id :state :source] :hal))
               true]
              [w changed?]))
          [w changed?])))
    [world false]
    entries))

(defn apply-file [world file]
  (if-not (map? file)
    world
    (let [a (:as-of file)]
      (if-not (and (integer? a) (not (neg? a)))
        world
        (if (= a (:state-as-of world))
          (dissoc world :state-cached?)
          (let [entries    (let [m (:mirrors file)] (if (map? m) m {}))
                [world' _] (merge-entries world entries)]
            (-> world'
                (assoc :state-as-of a)
                (dissoc :state-cached?))))))))

(defn cache-of [world]
  (let [a (:state-as-of world)]
    (when (and (integer? a) (not (neg? a)))
      (let [hal (into {} (filter (fn [[_ m]] (= :hal (get-in m [:state :source]))) (:mirrors world)))]
        (when (seq hal)
          {:as-of   a
           :mirrors (into {} (map (fn [[id m]] [id {:state (dissoc (:state m) :source)}]) hal))})))))

(defn apply-cache [world cache]
  (if-not (map? cache)
    world
    (let [a (:as-of cache)]
      (if-not (and (integer? a) (not (neg? a)))
        world
        (if (some? (:state-as-of world))
          world
          (let [entries        (let [m (:mirrors cache)] (if (map? m) m {}))
                [world' changed?] (merge-entries world entries)]
            (if changed?
              (assoc world' :state-as-of a :state-cached? true)
              world)))))))

(defn play-of
  "What the player did in the game to mirrors the twin doesn't keep
   (sample ones, e.g. a horse fed with a key): their state wherever it
   differs from the sample `estate`, so a reload doesn't undo it. nil when
   nothing differs."
  [world estate]
  (let [ms (for [[id m] (:mirrors world)
                 :when (not= :hal (get-in m [:state :source]))
                 :let [s  (dissoc (:state m) :source)
                       s0 (dissoc (get-in estate [:mirrors id :state]) :source)]
                 :when (not= s s0)]
             [id {:state s}])]
    (when (seq ms) {:mirrors (into {} ms)})))

(defn apply-play
  "Lay what `play-of` saved back over a freshly loaded world: same field
   rules as the twin's file, never over a mirror the twin keeps, never
   touching :source."
  [world play]
  (if-not (and (map? play) (map? (:mirrors play)))
    world
    (reduce (fn [w [id entry]]
              (let [m (get-in w [:mirrors id])]
                (if (and m (not= :hal (get-in m [:state :source]))
                         (map? entry) (map? (:state entry)))
                  (update-in w [:mirrors id :state] merge
                             (into {} (filter (fn [[k v]] (accept? m k v)) (:state entry))))
                  w)))
            world (:mirrors play))))

(defn source-tag
  ([source] (source-tag source nil nil))
  ([source cached?] (source-tag source cached? nil))
  ([source cached? jum]
   (cond
     (= source :hal) (if cached? (str (j/twin jum) ", cached") (j/twin jum))
     (keyword? source) (name source)
     :else nil)))

(defn hud-line [world mirror-id now]
  (when-let [line (w/status-line world mirror-id now)]
    (let [source (get-in world [:mirrors mirror-id :state :source])
          tag    (source-tag source (:state-cached? world) (j/names world))]
      (if (nil? tag)
        line
        (let [mirror    (get-in world [:mirrors mirror-id])
              name-part (or (:name mirror) (name mirror-id))]
          (str name-part " (" tag ")" (subs line (count name-part))))))))
