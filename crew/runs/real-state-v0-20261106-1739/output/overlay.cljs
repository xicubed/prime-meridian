(ns pm.overlay
  "Hal's twin state file, laid over the world. Pure: no js/fetch, no js/Date,
   no atoms. Never names a mirror; works from each mirror's own :state and
   :chores."
  (:require [cljs.reader :as reader]
            [pm.world :as w]))

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

(defn apply-file [world file]
  (if-not (map? file)
    world
    (let [a (:as-of file)]
      (if-not (and (integer? a) (not (neg? a)))
        world
        (if (= a (:state-as-of world))
          world
          (let [entries (let [m (:mirrors file)] (if (map? m) m {}))
                world'  (reduce
                          (fn [w [id entry]]
                            (let [mirror (get-in w [:mirrors id])]
                              (if (and mirror (map? entry) (map? (:state entry)))
                                (let [accepted (into {} (filter (fn [[k v]] (accept? mirror k v))
                                                                 (:state entry)))]
                                  (if (seq accepted)
                                    (-> w
                                        (update-in [:mirrors id :state] merge accepted)
                                        (assoc-in [:mirrors id :state :source] :hal))
                                    w))
                                w)))
                          world entries)]
            (assoc world' :state-as-of a)))))))

(defn source-tag [source]
  (cond
    (= source :hal) "Hal"
    (keyword? source) (name source)
    :else nil))

(defn hud-line [world mirror-id now]
  (when-let [line (w/status-line world mirror-id now)]
    (let [tag (source-tag (get-in world [:mirrors mirror-id :state :source]))]
      (if (nil? tag)
        line
        (let [mirror    (get-in world [:mirrors mirror-id])
              name-part (or (:name mirror) (name mirror-id))]
          (str name-part " (" tag ")" (subs line (count name-part))))))))
