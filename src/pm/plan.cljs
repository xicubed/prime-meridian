(ns pm.plan
  "Pure scene plan: world + now -> what should be in the scene. No three.js,
   no js/Date, no atoms. Mirrors are data; this namespace never names one."
  (:require [clojure.string :as str]
            [pm.world :as w]))

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
    {:shape (:shape part) :size size :at at :rot rot
     :color (normalize-color (:color part))}))

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
                      (assoc base
                             :label label
                             :label-at [x (+ y top label-lift) z]
                             :text (label-text markers label (:idle item) state))
                      base)))))
            (:placed mirror))))
      (sort (keys (:mirrors world))))))

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

(defn plan-summary [plan]
  (let [mirrors  (count (distinct (map :mirror plan)))
        items    (count plan)
        parts-n  (reduce + 0 (map (comp count :parts) plan))
        labelled (filter :label plan)
        l        (count labelled)
        s        (count (remove #(= "" (:text %)) labelled))]
    (str "scene: mirrors " mirrors " · items " items " · parts " parts-n
         " · labels " s "/" l)))
