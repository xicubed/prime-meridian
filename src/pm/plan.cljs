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

(defn- normalize-shape [shape]
  (if (#{:box :cylinder :capsule} shape) shape :box))

(defn- normalize-size [size]
  (if (and (vector? size) (= 3 (count size)) (every? #(and (number? %) (pos? %)) size))
    (vec size)
    [0.5 0.5 0.5]))

(defn- normalize-color [color]
  (if (keyword? color) color :stand-in))

(defn- normalize-look [look]
  {:shape (normalize-shape (:shape look))
   :size  (normalize-size (:size look))
   :color (normalize-color (:color look))})

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
                  (let [item-id  (or (:id item) idx)
                        [x y z]  at
                        look     (normalize-look (:look item))
                        [_ h _]  (:size look)
                        label    (:label item)
                        base     {:key    [mirror-id item-id]
                                  :mirror mirror-id
                                  :item   item-id
                                  :look   look
                                  :at     at
                                  :center [x (+ y (/ h 2)) z]}]
                    (if (#{:say :sign} label)
                      (assoc base
                             :label label
                             :label-at [x (+ y h label-lift) z]
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
        labelled (filter :label plan)
        l        (count labelled)
        s        (count (remove #(= "" (:text %)) labelled))]
    (str "scene: mirrors " mirrors " · items " items " · labels " s "/" l)))
