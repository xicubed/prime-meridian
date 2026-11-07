(ns pm.pick
  "Pure pointer-picking helpers: turn a click/drag and a raycast hit list
   into a mirror id. No DOM, no three.js. Never names a mirror."
  (:require [cljs.reader :as reader]))

(def click-max-px 6)
(def click-max-ms 400)

(defn- finite? [n]
  (and (number? n) (js/isFinite n)))

(defn- point? [m]
  (and (map? m) (finite? (:x m)) (finite? (:y m)) (finite? (:t m))))

(defn click? [down up]
  (boolean
    (and (point? down) (point? up)
         (let [dx (- (:x up) (:x down))
               dy (- (:y up) (:y down))
               dt (- (:t up) (:t down))]
           (and (< (+ (* dx dx) (* dy dy)) (* click-max-px click-max-px))
                (<= 0 dt) (< dt click-max-ms))))))

(defn ndc [client-x client-y rect]
  (let [{:keys [left top width height]} rect]
    (when (and (number? width) (number? height) (pos? width) (pos? height))
      [(- (* 2 (/ (- client-x left) width)) 1)
       (+ (* -2 (/ (- client-y top) height)) 1)])))

(defn key-of-name [s]
  (when (string? s)
    (when-let [[_ g1] (re-matches #"^(\[[^\]]*\])(?:/\d+)?$" s)]
      (let [v (try (reader/read-string g1) (catch :default _ nil))]
        (when (and (vector? v) (= 2 (count v)) (keyword? (first v)))
          v)))))

(defn pick-mirror [plan hits]
  (let [key->mirror (into {} (map (fn [item] [(:key item) (:mirror item)]) plan))
        first-key   (some (fn [chain] (some key-of-name chain)) hits)]
    (when first-key (get key->mirror first-key))))

(defn clickable? [world mirror-id]
  (let [m (get-in world [:mirrors mirror-id])]
    (boolean (and m (seq (:chores m))))))
