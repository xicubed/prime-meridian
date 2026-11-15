(ns pm.overlay
  "The twin's state file, laid over the world. Pure: no js/fetch, no
   js/Date, no atoms. Never names a mirror; works from each mirror's own
   :state and :chores."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
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
   (sample ones, e.g. a pet fed with a key): their state wherever it
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

(defn sample-badge
  "A short, visible tag worn by a connection running on its pretend
   version (GDD §4.6, §2.8): nil once a real one is swapped in for it.
   `source` is a mirror's own :state :source, or any other connection's
   keyword name; everything but `:hal` (the twin's own file, still local,
   not an outside account) counts as pretend until a real one exists."
  [source]
  (when (not= source :hal) "SAMPLE"))

(defn demo-connections
  "Every connection's badge right now (the GDD §4.6 table): each mirror
   by its own :state :source, plus the estate's :connections with no real
   version yet (estate.edn data), plus agents -- real only
   while the bridge is live. Keyed by connection id -> badge text; a real
   connection (no badge) is dropped, so zero accounts means every key
   shows up here."
  [world bridge-on?]
  (let [mirrors (into {} (keep (fn [[id m]]
                                  (when-let [b (sample-badge (get-in m [:state :source]))]
                                    [id b]))
                                (:mirrors world)))
        agents  (when-not bridge-on? {:agents "SAMPLE"})]
    (merge (into {} (for [c (:connections world)] [c "SAMPLE"]))
           mirrors agents)))

(defn demo-line
  "The HUD's visible demo-mode line: every current connection with its
   sample badge, in a stable order. nil when every connection is real
   (nothing left to flag)."
  [world bridge-on?]
  (let [badges (demo-connections world bridge-on?)]
    (when (seq badges)
      (str "demo: "
           (str/join " · "
             (map (fn [[id b]] (str (name id) " " b))
                  (sort-by (comp name key) badges)))))))
