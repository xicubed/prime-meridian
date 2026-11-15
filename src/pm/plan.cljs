(ns pm.plan
  "Pure scene plan: world + now -> what should be in the scene. No three.js,
   no js/Date, no atoms. Mirrors are data; this namespace never names one."
  (:require [clojure.string :as str]
            [pm.world :as w]
            [pm.content :as c]))

(def label-lift
  "Metres from the top of an item to its label's centre."
  0.3)

(defn label-text
  "Text for a `kind` (:say or :sign) label, from the mirror's current markers,
   falling back to `idle` (filled from `state`) when none hold."
  [markers kind idle state]
  (let [texts (keep kind markers)]
    (if (seq texts)
      (str/join " · " texts)
      (if (string? idle)
        (w/fill idle state)
        ""))))

(def shapes
  "Valid part shapes."
  #{:box :cylinder :capsule :sphere :cone})

(def default-part
  "The look's fallback when no part survives normalization."
  {:shape :box :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in})

(defn radians
  "Degrees to radians, for `pm.scene` (the plan itself stays in degrees)."
  [deg]
  (* deg (/ js/Math.PI 180)))

(defn- valid-shape? [shape]
  (contains? shapes shape))

(defn- valid-size? [size]
  (and (vector? size) (= 3 (count size)) (every? #(and (number? %) (pos? %)) size)))

(defn- valid-vec3? [v]
  (and (vector? v) (= 3 (count v)) (every? number? v)))

(defn- normalize-color [color]
  (if (keyword? color) color :stand-in))

(defn- single-shape-part
  "A single-shape look (no `:parts` key) is one part on the base; any `:at`/
   `:rot` on the look itself are ignored."
  [look]
  (let [shape (if (valid-shape? (:shape look)) (:shape look) :box)
        size  (if (valid-size? (:size look)) (vec (:size look)) [0.5 0.5 0.5])
        sy    (second size)]
    {:shape shape :size size :at [0 (/ sy 2) 0] :rot [0 0 0]
     :color (normalize-color (:color look))}))

(defn- valid-part? [part]
  (and (map? part)
       (valid-shape? (:shape part))
       (valid-size? (:size part))
       (or (nil? (:at part)) (valid-vec3? (:at part)))
       (or (nil? (:rot part)) (valid-vec3? (:rot part)))))

(defn- normalize-part [part]
  (let [size (vec (:size part))
        sy   (second size)
        at   (if (:at part) (vec (:at part)) [0 (/ sy 2) 0])
        rot  (if (:rot part) (vec (:rot part)) [0 0 0])]
    (cond-> {:shape (:shape part) :size size :at at :rot rot
             :color (normalize-color (:color part))}
      (true? (:glow part)) (assoc :glow true))))

(defn- raw-parts [look]
  (if (contains? look :parts)
    (let [candidates (:parts look)]
      (if (sequential? candidates)
        (let [valid (filterv valid-part? candidates)]
          (if (seq valid) (mapv normalize-part valid) [default-part]))
        [default-part]))
    [(single-shape-part look)]))

(defn look-parts
  "Normalized parts for `look`, scaled by `scale` (size and offset; `:rot` is
   not scaled). Always a non-empty vector."
  [look scale]
  (let [s (if (and (number? scale) (pos? scale)) scale 1)]
    (mapv (fn [part]
            (-> part
                (update :size (fn [sz] (mapv #(* s %) sz)))
                (update :at   (fn [a]  (mapv #(* s %) a)))))
          (raw-parts look))))

(defn parts-top
  "Highest point (in the item's own frame, ignoring :rot) any part reaches."
  [parts]
  (if (seq parts)
    (apply max (map (fn [{[_ dy _] :at [_ sy _] :size}] (+ dy (/ sy 2))) parts))
    0))

(defn- valid-at? [at]
  (and (vector? at) (= 3 (count at)) (every? number? at)))

(defn scene-plan
  "Vector of item maps for every placed item in `world` at `now`, in mirror-id
   then :placed order."
  [world now]
  (vec
    (mapcat
      (fn [mirror-id]
        (let [mirror  (get-in world [:mirrors mirror-id])
              markers (w/markers world mirror-id now)
              state   (:state mirror)]
          (keep-indexed
            (fn [idx item]
              (let [at (:at item)]
                (when (valid-at? at)
                  (let [item-id (or (:id item) idx)
                        [x y z] at
                        parts   (look-parts (:look item) (:scale item))
                        top     (parts-top parts)
                        yaw     (if (number? (:yaw item)) (:yaw item) 0)
                        label   (:label item)
                        base    {:key    [mirror-id item-id]
                                 :mirror mirror-id
                                 :item   item-id
                                 :at     at
                                 :yaw    yaw
                                 :parts  parts
                                 :center [x (+ y (/ top 2)) z]}]
                    (if (#{:say :sign} label)
                      (let [idle (:idle item)
                            idle (if (keyword? idle) (w/line-for world mirror-id idle now) idle)]
                        (assoc base
                               :label label
                               :label-at [x (+ y top label-lift) z]
                               :text (label-text markers label idle state)))
                      base)))))
            (:placed mirror))))
      (sort (keys (:mirrors world))))))

(defn lobby-item
  "The lobby's scene item (GDD §2.4, §4.8): home, with your kitchen, where
   agents bring their questions. Placement, look and sign text come from
   `world`'s `:lobby` only -- a plain, always-on label, not driven by any
   mirror's state. nil when `world` has no valid `:lobby`."
  [world]
  (let [{:keys [at yaw look text]} (:lobby world)]
    (when (valid-at? at)
      (let [[x y z] at
            parts (look-parts look nil)
            top   (parts-top parts)
            yaw   (if (number? yaw) yaw 0)]
        {:key      [:lobby :box]
         :at       at
         :yaw      yaw
         :parts    parts
         :center   [x (+ y (/ top 2)) z]
         :label    :sign
         :label-at [x (+ y top label-lift) z]
         :text     (if (string? text) text "")}))))

(defn workshop-item
  "The workshop's scene item (GDD §2.4, §4.8): one per project, standing for
   a code repo -- desks are open tasks, shelves fill as work lands.
   Placement, look and sign text come from `world`'s `:workshop` only -- a
   plain, always-on label, not driven by any mirror's state. nil when
   `world` has no valid `:workshop`."
  [world]
  (let [{:keys [at yaw look text]} (:workshop world)]
    (when (valid-at? at)
      (let [[x y z] at
            parts (look-parts look nil)
            top   (parts-top parts)
            yaw   (if (number? yaw) yaw 0)]
        {:key      [:workshop :box]
         :at       at
         :yaw      yaw
         :parts    parts
         :center   [x (+ y (/ top 2)) z]
         :label    :sign
         :label-at [x (+ y top label-lift) z]
         :text     (if (string? text) text "")}))))

(defn mailbox-item
  "The mailbox's scene item (GDD §2.4): `world`'s `:mailbox` gives its
   placement and look only; its text is always the unread count from
   `inbox` (GDD §4.6's sample inbox until a real one is connected), never
   from any world state. nil when `world` has no valid `:mailbox`."
  [world inbox]
  (let [{:keys [at yaw look]} (:mailbox world)]
    (when (valid-at? at)
      (let [[x y z] at
            parts (look-parts look nil)
            top   (parts-top parts)
            yaw   (if (number? yaw) yaw 0)]
        {:key      [:mailbox :box]
         :at       at
         :yaw      yaw
         :parts    parts
         :center   [x (+ y (/ top 2)) z]
         :label    :sign
         :label-at [x (+ y top label-lift) z]
         :text     (or (c/mail-line inbox) "")}))))

(defn phone-booth-item
  "The phone booth's scene item (GDD §2.3, §4.8): placement and look come
   from `world`'s `:phone-booth` only; its sign shows the jack-out reason
   while the estate is frozen (`pm.world/jack-out-text`), empty otherwise.
   nil when `world` has no valid `:phone-booth`."
  [world]
  (let [{:keys [at yaw look]} (:phone-booth world)]
    (when (valid-at? at)
      (let [[x y z] at
            parts (look-parts look nil)
            top   (parts-top parts)
            yaw   (if (number? yaw) yaw 0)]
        {:key      [:phone-booth :box]
         :at       at
         :yaw      yaw
         :parts    parts
         :center   [x (+ y (/ top 2)) z]
         :label    :sign
         :label-at [x (+ y top label-lift) z]
         :text     (or (w/jack-out-text world) "")}))))

(defn label-texts
  "{key text} for every labelled item in `plan`."
  [plan]
  (into {} (for [item plan :when (:label item)] [(:key item) (:text item)])))

(defn label-changes
  "{key text} for each labelled item in `plan` whose :text differs from
   `(get prev key)`. Keys in `prev` not in `plan` are ignored."
  [prev plan]
  (let [prev (or prev {})]
    (into {}
          (for [item plan
                :when (:label item)
                :let  [k (:key item) new-text (:text item)]
                :when (not= new-text (get prev k))]
            [k new-text]))))

(def snap-ms
  "How long the snap (GDD §2.2, §4.8) plays, start to finish."
  1100)

(defn snap-pose
  "0..1 progress through mirror-id's snap at now, or nil when none is live --
   world has no :snap, it names a different mirror, or more than snap-ms has
   passed. Pure timing only; `pm.scene/play-snap!` turns this into a mesh
   offset, since the scene plan's own positions never follow world state."
  [world mirror-id now]
  (let [{:keys [mirror at]} (:snap world)]
    (when (and (= mirror mirror-id) (number? at) (number? now))
      (let [p (/ (- now at) snap-ms)]
        (when (<= 0 p 1) p)))))

(defn plan-summary [plan]
  (let [mirrors  (count (distinct (map :mirror plan)))
        items    (count plan)
        parts-n  (reduce + 0 (map (comp count :parts) plan))
        labelled (filter :label plan)
        l        (count labelled)
        s        (count (remove #(= "" (:text %)) labelled))]
    (str "scene: mirrors " mirrors " · items " items " · parts " parts-n
         " · labels " s "/" l)))
