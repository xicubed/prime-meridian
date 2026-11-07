(ns pm.motion
  "Pure rules for walkers pacing a route: kinematic stepping, MotionBricks
   follow-mode, ground sensing/smoothing, and HUD text. No three.js, no
   atoms, no js/Date and no walker names — the only JS used is js/Math and
   js/URLSearchParams, both available under Node for testing."
  (:require [clojure.string :as str]))

;; -- Constants ---------------------------------------------------------

(def default-speed 1.2)
(def arrive-m 0.3)
(def smooth-tau 0.15)

(def figure
  "The kinematic figure, in pm.plan's look-part format (+x forward, degrees)."
  {:parts [{:shape :capsule :size [1.1 0.42 0.42] :at [0 0.6 0]     :rot [0 0 90] :color :crew}
           {:shape :sphere  :size [0.3 0.32 0.3]  :at [0 1.38 0]                  :color :crew-head}
           {:shape :box     :size [0.06 0.08 0.18] :at [0.14 1.4 0]               :color :eye}]
   :label-y 1.95})

;; -- Heading -------------------------------------------------------------

(defn yaw [dx dz]
  (js/Math.atan2 (- dz) dx))

;; -- Config / walker state -------------------------------------------------

(defn config [world]
  (let [b (get-in world [:motion :bricks])
        b (when (map? b) b)
        g (get-in world [:motion :ground])
        g (if (sequential? g) (filterv string? g) [])
        s (get-in world [:motion :speed])
        s (if (and (number? s) (pos? s)) s default-speed)]
    {:bricks b :ground (vec g) :speed s}))

(defn- valid-point? [p]
  (and (vector? p) (= 2 (count p)) (every? number? p)))

(defn walker [entry]
  (let [id    (:id entry)
        route (filterv valid-point? (:route entry))]
    (when (and (keyword? id) (seq route))
      (let [nm      (:name entry)
            nm      (if (and (string? nm) (not (str/blank? nm))) nm (name id))
            pause-s (:pause-s entry)
            pause-s (if (and (number? pause-s) (>= pause-s 0)) pause-s 0)
            [x0 z0] (first route)
            heading (if (>= (count route) 2)
                      (let [[x1 z1] (second route)]
                        (yaw (- x1 x0) (- z1 z0)))
                      0)]
        {:id id :name nm :route route :pause-s pause-s
         :pos [x0 z0] :heading heading :to 0 :dir 1 :pause pause-s :leg 0
         :ground nil :y nil}))))

(defn walkers [world]
  (into [] (keep walker) (:walkers world)))

;; -- Ping-pong -------------------------------------------------------------

(defn next-index [n i dir]
  (if (< n 2)
    [0 1]
    (let [j (+ i dir)]
      (if (and (>= j 0) (< j n))
        [j dir]
        [(- i dir) (- dir)]))))

(defn target [w]
  (get (:route w) (:to w)))

(defn new-target? [before after]
  (not= (:leg before) (:leg after)))

;; -- step / follow ---------------------------------------------------------

(defn- pick [w]
  (let [[i' dir'] (next-index (count (:route w)) (:to w) (:dir w))]
    (assoc w :to i' :dir dir' :pause 0 :leg (inc (:leg w)))))

(defn- arrive [w]
  (if (> (:pause-s w) 0)
    (assoc w :pause (:pause-s w))
    (pick w)))

(defn step [w speed dt]
  (let [dt'   (max 0 dt)
        route (:route w)]
    (cond
      (< (count route) 2)
      w

      (> (:pause w) 0)
      (let [p (- (:pause w) dt')]
        (if (> p 0)
          (assoc w :pause p)
          (pick w)))

      :else
      (let [[tx tz] (target w)
            [x z]   (:pos w)
            dx (- tx x) dz (- tz z)
            d  (js/Math.hypot dx dz)]
        (if (<= d arrive-m)
          (arrive w)
          (let [s  (min (* speed dt') d)
                w' (assoc w
                          :pos [(+ x (* (/ dx d) s)) (+ z (* (/ dz d) s))]
                          :heading (yaw dx dz))]
            (if (<= (- d s) arrive-m)
              (arrive w')
              w')))))))

(defn follow [w pos idle? dt]
  (let [w     (if (some? pos) (assoc w :pos pos) w)
        dt'   (max 0 dt)
        route (:route w)]
    (cond
      (< (count route) 2)
      w

      (> (:pause w) 0)
      (let [p (- (:pause w) dt')]
        (if (> p 0)
          (assoc w :pause p)
          (pick w)))

      :else
      (let [[tx tz] (target w)
            [x z]   (:pos w)
            dx (- tx x) dz (- tz z)
            d  (js/Math.hypot dx dz)]
        (if (or (<= d arrive-m) idle?)
          (arrive w)
          w)))))

;; -- Ground sensing / smoothing ---------------------------------------------

(defn ground-name? [s prefixes]
  (boolean (and (string? s) (some #(str/starts-with? s %) prefixes))))

(defn ground-height [hits prefixes]
  (when (seq hits)
    (let [ys (keep (fn [[y n]] (when (ground-name? n prefixes) y)) hits)]
      (when (seq ys) (apply max ys)))))

(defn sense-ground [w hits prefixes]
  (if-let [h (ground-height hits prefixes)]
    (assoc w :ground h)
    w))

(defn smooth [prev target dt]
  (cond
    (nil? prev) target
    (nil? target) prev
    :else
    (let [dt' (max 0 dt)]
      (+ prev (* (- target prev) (- 1 (js/Math.exp (- (/ dt' smooth-tau)))))))))

(defn settle [w dt]
  (assoc w :y (smooth (:y w) (:ground w) dt)))

;; -- Flag / progress / reasons ----------------------------------------------

(defn bricks-flag? [search]
  (.has (js/URLSearchParams. (or search "")) "bricks"))

(defn load-fraction [stage loaded total]
  (case stage
    "runtime" 0
    "model"   (if (and (number? total) (pos? total)) (/ loaded total) 0)
    "warmup"  1
    nil))

(defn short-reason [msg]
  (let [first-line (first (str/split (str msg) #"\n" 2))
        trimmed    (str/trim (or first-line ""))]
    (cond
      (str/blank? trimmed)     "unknown"
      (> (count trimmed) 40)  (str (subs trimmed 0 39) "…")
      :else                    trimmed)))

;; -- HUD --------------------------------------------------------------------

(defn hud-text [status]
  (when (map? status)
    (case (:mode status)
      :kinematic "motion: kinematic"

      :loading
      (let [f (:progress status)
            f (if (number? f) f 0)
            f (max 0 (min 1 f))
            n (js/Math.round (* 100 f))]
        (str "motion: loading model " n "%"))

      :bricks
      (let [backend (:backend status)
            n       (:walkers status)
            gen-ms  (:gen-ms status)]
        (str "motion: bricks"
             (when (and (string? backend) (not (str/blank? backend))) (str " " backend))
             " · " n " walker" (when (not= n 1) "s")
             (when (number? gen-ms) (str " · gen " (js/Math.round gen-ms) " ms"))))

      :failed
      (str "motion: kinematic (bricks failed: " (:reason status) ")")

      nil)))
