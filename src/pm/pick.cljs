(ns pm.pick
  "Pure pointer-picking helpers: turn a click/drag and a raycast hit list
   into a mirror id or a crew agent. No DOM, no three.js. Never names a
   mirror."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]))

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

(defn pick-key
  "The scene-item key of the nearest hit, mirror or not (e.g. [:phone-booth :box])."
  [hits]
  (some (fn [chain] (some key-of-name chain)) hits))

(defn pick-mirror [plan hits]
  (let [key->mirror (into {} (map (fn [item] [(:key item) (:mirror item)]) plan))
        first-key   (pick-key hits)]
    (when first-key (get key->mirror first-key))))

(def booth-key
  "The phone booth's scene-item key (pm.plan/phone-booth-item): clicking it
   jacks out, clicking it again jacks back in."
  [:phone-booth :box])

(def mailbox-key
  "The mailbox's scene-item key (pm.plan/mailbox-item): clicking it opens
   the inbox."
  [:mailbox :box])

(defn clickable? [world mirror-id]
  (let [m (get-in world [:mirrors mirror-id])]
    (boolean (and m (seq (:chores m))))))

(def ^:private walker-name-prefix
  "The prefix src/pm/bricks.cljs gives each walker's anchor group, e.g.
   \"pm-walkers/le-testeur\"."
  "pm-walkers/")

(defn- walker-id-of-name [s]
  (when (string? s)
    (when (str/starts-with? s walker-name-prefix)
      (let [id (subs s (count walker-name-prefix))]
        (when (seq id) (keyword id))))))

(defn pick-agent
  "The crew agent id (GDD §2.1 Talk) for the nearest hit that belongs to a
   walker, looked up through `walkers` (pm.motion/walkers) for that
   walker's :agent. Nil when no hit belongs to a walker, or the walker
   names no agent."
  [walkers hits]
  (let [id->agent (into {} (map (fn [w] [(:id w) (:agent w)]) walkers))]
    (some (fn [chain]
            (some (fn [n] (when-let [id (walker-id-of-name n)] (get id->agent id)))
                  chain))
          hits)))

(defn screen-of
  "Where a projected point lands on screen (TASKS T-14, the trial card):
   `ndc` ([x y z], a point projected through the camera) and `size` ([w h]
   canvas px) turned into `{:x :y :visible?}` CSS px inside the canvas, the
   card's bottom-centre point. `{:x 0 :y 0 :visible? false}` when `ndc`
   isn't 3 finite numbers or `size`'s width/height isn't a finite positive
   number. No DOM, no js/Date."
  [ndc [w h]]
  (if (and (vector? ndc) (= 3 (count ndc)) (every? finite? ndc)
           (finite? w) (finite? h) (pos? w) (pos? h))
    (let [[x y z] ndc]
      {:x (* (/ (+ x 1) 2) w)
       :y (* (/ (- 1 y) 2) h)
       :visible? (and (<= -1 x 1) (<= -1 y 1) (<= z 1))})
    {:x 0 :y 0 :visible? false}))
