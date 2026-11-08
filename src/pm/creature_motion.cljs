(ns pm.creature-motion
  "Pure rules for the creature spike: config/URL, the twitch controller (a
   swappable driver, mirroring pm.motion's walker step/follow split), the
   fixed-step accumulator, stats, HUD text and pose from MuJoCo arrays. Only
   js/Math and js/URLSearchParams; no atoms, no three.js, no species names."
  (:require [clojure.string :as str]
            [pm.creature :as c]
            [pm.motion :as m]))

;; -- Spike config and URL (§5.1) --------------------------------------------

(defn spike-config [world]
  (let [cfg (:creature-spike world)]
    (when (and (map? cfg)
               (string? (:flag cfg)) (not (str/blank? (:flag cfg)))
               (keyword? (:kind cfg))
               (let [at (:at cfg)] (and (vector? at) (= 3 (count at)) (every? number? at))))
      (let [seed   (:seed cfg)
            seed   (if (integer? seed) seed 1)
            yaw    (:yaw cfg)
            yaw    (if (number? yaw) yaw 0)
            drop-m (:drop-m cfg)
            drop-m (if (and (number? drop-m) (>= drop-m 0)) drop-m 0.4)]
        {:flag (:flag cfg) :kind (:kind cfg) :seed seed :at (:at cfg) :yaw yaw :drop-m drop-m}))))

(defn spike-request [search cfg]
  (when cfg
    (let [params (js/URLSearchParams. (or search ""))]
      (when (.has params (:flag cfg))
        (let [v          (.get params (:flag cfg))
              k          (if (and (string? v) (not (str/blank? v))) (keyword v) (:kind cfg))
              seed-param (.get params "seed")
              parsed     (when (some? seed-param) (js/parseInt seed-param 10))
              s          (if (and (some? parsed) (js/isFinite parsed)) parsed (:seed cfg))]
          (assoc cfg :kind k :seed s))))))

;; -- The twitch noise (§5.3) -------------------------------------------------

(defn rng-seed [s]
  (inc (mod (js/Math.floor (js/Math.abs s)) 2147483646)))

(defn rng-next [x]
  (let [x' (mod (* 16807 x) 2147483647)]
    [x' (/ x' 2147483647)]))

(defn- chan-init [seed i]
  (let [x0        (rng-seed (+ (* seed 100) i))
        [x1 _]    (rng-next x0)
        [x2 _]    (rng-next x1)   ; warm-up: discard 2 draws
        [x3 u1]   (rng-next x2)
        [x4 u2]   (rng-next x3)]
    {:v 0 :from 0 :to (- (* 2 u1) 1) :t 0 :dur (+ 0.3 (* 0.5 u2)) :rng x4}))

(defn twitch-init [n seed]
  {:kind :twitch :n n :chans (vec (map #(chan-init seed %) (range n)))})

(defn- smoothstep [u] (* u u (- 3 (* 2 u))))

(defn- roll-channel [ch dt]
  (loop [from (:from ch) to (:to ch) t (+ (:t ch) dt) dur (:dur ch) rng (:rng ch)]
    (if (>= t dur)
      (let [from'    to
            t'       (- t dur)
            [r1 u1]  (rng-next rng)
            to'      (- (* 2 u1) 1)
            [r2 u2]  (rng-next r1)
            dur'     (+ 0.3 (* 0.5 u2))]
        (recur from' to' t' dur' r2))
      {:from from :to to :t t :dur dur :rng rng})))

(defn twitch-step [ctrl dt]
  (if-not (and (number? dt) (js/isFinite dt) (pos? dt))
    ctrl
    (update ctrl :chans
            (fn [chans]
              (mapv (fn [ch]
                      (let [{:keys [from to t dur rng]} (roll-channel ch dt)
                            v (+ from (* (- to from) (smoothstep (/ t dur))))]
                        {:v v :from from :to to :t t :dur dur :rng rng}))
                    chans)))))

(defn twitch-actions [ctrl] (mapv :v (:chans ctrl)))

;; -- The controller interface (§5.2) -----------------------------------------

(defn controller [kind n seed]
  (if (= kind :twitch) (twitch-init n seed) {:kind kind :n n}))

(defn act [ctrl obs dt]
  (case (:kind ctrl)
    :twitch (let [c' (twitch-step ctrl dt)] [c' (twitch-actions c')])
    [ctrl (vec (repeat (:n ctrl) 0))]))

(defn- clamp [x lo hi] (max lo (min hi x)))

(defn actions->ctrl [actions ranges]
  (mapv (fn [a [lo hi]] (+ (/ (+ lo hi) 2) (* (clamp a -1 1) (/ (- hi lo) 2)))) actions ranges))

;; -- Fixed physics step (§5.4) ------------------------------------------------

(def step-s c/timestep)
(def max-steps 25)

(defn fixed-steps
  ([acc dt] (fixed-steps acc dt step-s max-steps))
  ([acc dt step cap]
   (let [acc   (if (and (number? acc) (js/isFinite acc) (pos? acc)) acc 0)
         dt    (if (and (number? dt) (js/isFinite dt) (pos? dt)) dt 0)
         total (+ acc dt)
         n     (js/Math.floor (+ (/ total step) 1e-9))
         s     (min n cap)
         d     (- n s)
         a     (max 0 (- total (* n step)))]
     {:steps s :dropped d :acc a})))

;; -- Stats and HUD (§5.5) -----------------------------------------------------

(def empty-stats {:since nil :sum 0 :n 0 :ms nil :steps 0 :dropped 0})

(defn stats-tick [stats now-ms frame-ms steps dropped]
  (let [since    (or (:since stats) now-ms)
        sum      (+ (:sum stats) frame-ms)
        n        (inc (:n stats))
        dropped' (+ (:dropped stats) dropped)]
    (if (>= (- now-ms since) 1000)
      {:since now-ms :sum 0 :n 0 :ms (/ sum n) :steps steps :dropped dropped'}
      {:since since :sum sum :n n :ms (:ms stats) :steps steps :dropped dropped'})))

(defn hud-text [status]
  (when (map? status)
    (case (:mode status)
      :loading (str (:flag status) ": " (name (:kind status)) " loading")

      :running
      (let [stats (:stats status)
            ms    (:ms stats)
            torso (:torso status)]
        (str (:flag status) ": " (name (:kind status)) " · "
             (if (number? ms) (.toFixed ms 1) "-") " ms/frame · "
             (:steps stats) " steps · " (:dropped stats) " dropped · torso "
             (if (number? torso) (.toFixed torso 2) "-") " m"))

      :failed
      (str (:flag status) ": " (name (:kind status)) " failed (" (m/short-reason (:reason status)) ")")

      nil)))

;; -- Pose from MuJoCo arrays (§5.6) -------------------------------------------

(defn body-pose [xpos xquat id]
  {:pos  (c/mj->three [(aget xpos (* 3 id)) (aget xpos (+ (* 3 id) 1)) (aget xpos (+ (* 3 id) 2))])
   :quat (c/mj-quat->three [(aget xquat (* 4 id)) (aget xquat (+ (* 4 id) 1))
                             (aget xquat (+ (* 4 id) 2)) (aget xquat (+ (* 4 id) 3))])})
