(ns pm.creature-effort-test
  (:require [cljs.test :refer [deftest is]]
            [pm.creature :as c]
            [pm.creature-motion :as cm]
            [pm.species :as species]))

(defn approx=
  ([a b] (approx= a b 1e-6))
  ([a b eps]
   (cond
     (and (number? a) (number? b)) (< (js/Math.abs (- a b)) eps)
     (and (sequential? a) (sequential? b))
     (and (= (count a) (count b)) (every? true? (map #(approx= %1 %2 eps) a b)))
     (and (map? a) (map? b))
     (and (= (set (keys a)) (set (keys b)))
          (every? (fn [k] (approx= (get a k) (get b k) eps)) (keys a)))
     :else (= a b))))

(def e {:energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :ramp-s 0.5})
(def act! {:energy 1 :mode :active :level 1})

(def horse (c/parse (get species/texts :horse)))

;; ---- The energy state machine ----

;; E1

(deftest e1-full-effort-drains
  (is (approx= {:energy 0.75 :mode :active :level 1} (cm/effort-step act! e 1 0.5))))

;; E2

(deftest e2-half-effort-drains-half
  (is (approx= 0.875 (:energy (cm/effort-step act! e 0.5 0.5))))
  (is (approx= 0.75 (:energy (cm/effort-step act! e 2 0.5))))
  (is (approx= 1.0 (:energy (cm/effort-step act! e -1 0.5)))))

;; E3

(deftest e3-full-effort-drains-and-rests
  (let [states (reductions (fn [st _] (cm/effort-step st e 1 0.1)) act! (range 20))
        s14 (nth states 14)
        s16 (nth states 16)]
    (is (every? #(>= (:energy %) 0) states))
    (is (approx= 0.3 (:energy s14)))
    (is (= :active (:mode s14)))
    (is (approx= 0.2 (:energy s16)))
    (is (= :resting (:mode s16)))
    (is (= :resting (:mode (last states))))))

;; E4

(deftest e4-lets-go-with-ramp-out
  (let [s1 (cm/effort-step {:energy 0.3 :mode :active :level 1} e 1 0.2)]
    (is (approx= {:energy 0.2 :mode :resting :level 0.6} s1))
    (let [s2 (cm/effort-step s1 e 1 0.3)]
      (is (approx= 0 (:level s2)))
      (is (approx= 0.275 (:energy s2))))))

;; E5

(deftest e5-hysteresis-no-flapping
  (is (approx= {:energy 0.5 :mode :resting :level 0}
               (cm/effort-step {:energy 0.4 :mode :resting :level 0} e 1 0.4)))
  (is (approx= {:energy 0.4 :mode :active :level 1}
               (cm/effort-step {:energy 0.4 :mode :active :level 1} e 0 0.4)))
  (let [amounts (take 10 (cycle [1 0]))
        states (reductions (fn [st a] (cm/effort-step st e a 0.05))
                            {:energy 0.4 :mode :active :level 1} amounts)]
    (is (every? #(= :active (:mode %)) states))
    (is (approx= 0.275 (:energy (last states))))))

;; E6

(deftest e6-recovery-flips-to-active-above-high
  (let [e' (assoc e :recover-s 5)
        states (reductions (fn [st _] (cm/effort-step st e' 0 0.1))
                            {:energy 0.25 :mode :resting :level 0} (range 18))
        s17 (nth states 17)
        s18 (nth states 18)]
    (is (approx= 0.59 (:energy s17)))
    (is (= :resting (:mode s17)))
    (is (approx= 0.61 (:energy s18)))
    (is (= :active (:mode s18)))
    (is (approx= 0.2 (:level s18)))))

;; E7

(deftest e7-ramp-in
  (let [s1 (cm/effort-step {:energy 0.7 :mode :resting :level 0} e 0 0.1)]
    (is (approx= {:energy 0.725 :mode :active :level 0.2} s1))
    (let [levels (rest (reductions (fn [st _] (cm/effort-step st e 0 0.1)) s1 (range 5)))]
      (is (approx= [0.4 0.6 0.8 1.0 1.0] (mapv :level levels))))))

;; E8

(deftest e8-dt-must-be-finite-positive
  (let [st {:energy 0.5 :mode :active :level 0.5}]
    (is (= st (cm/effort-step st e 1 0)))
    (is (= st (cm/effort-step st e 1 -1)))
    (is (= st (cm/effort-step st e 1 ##NaN)))))

;; E9

(deftest e9-effort-amount
  (is (approx= 0.4375 (cm/effort-amount #js [10 -20 5 0 99] [0 1 2 3] 20)))
  (is (approx= 1 (cm/effort-amount #js [40 -40 40 40] [0 1 2 3] 20)))
  (is (approx= 0 (cm/effort-amount #js [10 -20 5 0 99] [0 1 2 3] 0)))
  (is (approx= 0 (cm/effort-amount nil [0 1 2 3] 20)))
  (is (approx= 1.0 (cm/effort-amount #js [10 -20 5 0 99] [1] 20)))
  (is (approx= 0 (cm/effort-amount #js [10 -20 5 0 99] [] 20))))

;; ---- Tremor ----

;; T1

(deftest t1-tremor-amplitude
  (is (approx= 6 (cm/tremor-deg-at {:tremor-deg 6 :steady-s 90} 0)))
  (is (approx= 2.207277 (cm/tremor-deg-at {:tremor-deg 6 :steady-s 90} 90)))
  (is (approx= 0.812012 (cm/tremor-deg-at {:tremor-deg 6 :steady-s 90} 180)))
  (is (approx= 6 (cm/tremor-deg-at {:tremor-deg 6 :steady-s 90} -5)))
  (is (approx= 0.367879 (cm/tremor-deg-at {:tremor-deg 1 :steady-s 30} 30))))

;; Shared fixture for T2-T4, A1-A3 (the X11 reflex plus :effort)

(def t3-r {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j]
           :lift-m 0 :gain 1 :w-lift 0 :onset-s 0
           :effort {:strength 1 :torque-nm 10 :tremor-deg 10 :steady-s 1e9
                    :energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :seed-salt 7
                    :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}})

(def t3-qpos (doto #js [0 0 0 0 0 0 0 0 0] (aset 8 0.1)))
(def t3-zeros9 #js [0 0 0 0 0 0 0 0 0])
(def t3-obs {:nv 3 :qpos t3-qpos
             :senses [{:up [0 0 1] :clearance 1 :jacp t3-zeros9 :jacr t3-zeros9}]
             :forces #js [0 0 0]})

;; T2

(deftest t2-seeded-tremor
  (let [rh (first (c/reflexes horse))
        ctrl (cm/reflex-controller (cm/controller :still 12 1) [rh] 1)]
    (is (= (cm/twitch-init 4 8) (:tremor (first (:efforts ctrl))))))
  (let [mk (fn [] (cm/reflex-controller (cm/controller :still 3 1) [t3-r] 1))
        ok (loop [c1 (mk) c2 (mk) i 0 ok? true]
             (if (or (not ok?) (>= i 500))
               ok?
               (let [[c1' a1] (cm/act c1 t3-obs 0.002)
                     [c2' a2] (cm/act c2 t3-obs 0.002)]
                 (recur c1' c2' (inc i) (and (= c1' c2') (= a1 a2))))))]
    (is ok))
  (let [c1 (cm/reflex-controller (cm/controller :still 3 1) [t3-r] 1)
        c2 (cm/reflex-controller (cm/controller :still 3 1) [t3-r] 2)
        to (fn [c] (:to (first (:chans (:tremor (first (:efforts c))))))) ]
    (is (not= (to c1) (to c2)))))

;; T3

(deftest t3-tremor-added-to-targets
  (let [ctrl (cm/reflex-controller (cm/controller :still 3 1) [t3-r] 1)
        [_ a] (cm/act ctrl t3-obs 0.1)
        expected-v (:v (first (:chans (cm/twitch-step (cm/twitch-init 1 8) 0.8))))]
    (is (approx= 0.179391 expected-v))
    (is (approx= 0 (nth a 0)))
    (is (approx= 0.131310 (nth a 1)))
    (is (approx= 0 (nth a 2))))
  (let [r2 (assoc-in t3-r [:effort :steady-s] 0.1)
        ctrl2 (cm/reflex-controller (cm/controller :still 3 1) [r2] 1)
        [_ a2] (cm/act ctrl2 t3-obs 0.1)]
    (is (approx= 0.111518 (nth a2 1)))))

;; T4

(deftest t4-no-shaking-at-rest
  (let [ctrl (-> (cm/reflex-controller (cm/controller :still 3 1) [t3-r] 1)
                 (assoc :efforts [{:energy 0.1 :mode :resting :level 0 :tremor (cm/twitch-init 1 8)}]))
        [_ a] (cm/act ctrl t3-obs 0.1)]
    (is (approx= 0.09 (nth a 1)))))

;; ---- The layer ----

(def a1-r {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j]
           :lift-m 0 :gain 1 :w-lift 0 :onset-s 0
           :effort {:strength 0.5 :torque-nm 10 :tremor-deg 0 :steady-s 1
                    :energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :seed-salt 0
                    :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}})

;; A1

(deftest a1-gain-scaled-by-strength
  (let [qpos (doto #js [0 0 0 0 0 0 0 0 0] (aset 8 0.1))
        zeros9 #js [0 0 0 0 0 0 0 0 0]
        jacr #js [0 0 1 0 1 0 1 0 0]
        obs {:nv 3 :qpos qpos
             :senses [{:up [0.6 0 0.8] :clearance 1 :jacp zeros9 :jacr jacr}]
             :forces #js [0 5 0]}
        ctrl (cm/reflex-controller (cm/controller :still 3 1) [a1-r] 1)
        [ctrl' a] (cm/act ctrl obs 0.002)]
    (is (approx= [0 -0.2 0] a))
    (is (approx= {:energy 0.999 :mode :active :level 1}
                 (dissoc (first (:efforts ctrl')) :tremor)))))

;; A2

(deftest a2-without-effort-unchanged
  (let [inner (cm/controller :still 3 1)
        r-x11 {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j]
               :lift-m 0 :gain 1 :w-lift 0 :onset-s 0}
        expected {:kind :reflex :n 3 :inner inner :reflexes [r-x11] :t 0}]
    (is (= expected (cm/reflex-controller inner [r-x11] 5)))
    (is (= expected (cm/reflex-controller inner [r-x11])))
    (is (not (contains? (cm/reflex-controller inner [r-x11] 5) :efforts)))))

;; A3

(deftest a3-nil-obs-transparent
  (let [ctrl (cm/reflex-controller (cm/controller :still 3 1) [a1-r] 1)
        [ctrl' _] (cm/act ctrl nil 0.002)]
    (is (= (:efforts ctrl) (:efforts ctrl')))
    (is (approx= 0.002 (:t ctrl')))))

;; L1

(deftest l1-effort-label
  (let [r {:lift-m 0.25}]
    (is (= "resting" (cm/effort-label {:mode :resting} r {:clearance 0.3 :up [0 0 1]})))
    (is (= "holding" (cm/effort-label {:mode :active} r {:clearance 0.3 :up [0 0 1]})))
    (is (= "trying" (cm/effort-label {:mode :active} r {:clearance 0.1 :up [0 0 1]})))
    (is (= "trying" (cm/effort-label {:mode :active} r {:clearance 0.3 :up [0 -0.5 0.8660254]})))))

;; H1

(deftest h1-hud-effort
  (is (= "pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · head 0.24 m 1° · energy 0.42 · holding"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149
                        :head {:clearance 0.2437 :tilt 1.2}
                        :effort {:energy 0.4231 :label "holding"}})))
  (is (= "pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · energy 0.10 · resting"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149
                        :effort {:energy 0.1 :label "resting"}})))
  (is (= "pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149}))))

;; ---- Limp ----

;; K1

(deftest k1-limp
  (let [qpos (doto #js [0 0 0 0 0 0 0 0 0] (aset 7 0.1) (aset 8 0.3))]
    (is (approx= [0.14 -0.2] (cm/limp [0.5 -0.2] qpos [0.1 1])))
    (is (approx= [0.09 0.27] (cm/limp [0 0] qpos [0.1 0.1])))
    (is (approx= [0.1 0.3] (cm/limp [0 0] qpos [0 0])))))

;; K2

(deftest k2-stiffness
  (let [tw {:kind :twitch :n 3 :chans [{:to 0.9} {:to -0.8} {:to 0.5}]}]
    (is (= [1 1 0.1] (cm/stiffness tw 0.1 0.8)))
    (is (= [0.1 0.1 0.1] (cm/stiffness {:kind :still :n 3} 0.1 0.8)))
    (is (= [0.1 1 0.1] (cm/stiffness (cm/reflex-controller {:kind :still :n 3} [{:acts [1]}]) 0.1 0.8)))
    (is (= [1 1 1] (cm/stiffness tw 1 0.8))))
  (let [twi (cm/twitch-init 12 1)
        sig (cm/stiffness twi 0.1 0.8)
        tos (mapv :to (:chans twi))]
    (is (= 12 (count sig)))
    (is (every? #(contains? #{1 0.1} %) sig))
    (is (= sig (mapv #(if (>= (js/Math.abs %) 0.8) 1 0.1) tos)))))

;; K3

(deftest k3-partial-rest-blend
  (let [r (assoc-in t3-r [:effort :tremor-deg] 0)
        ctrl (-> (cm/reflex-controller (cm/controller :still 3 1) [r] 1)
                 (assoc :efforts [{:energy 0.1 :mode :resting :level 0.5 :tremor (cm/twitch-init 1 8)}]))
        [_ a] (cm/act ctrl t3-obs 0.1)]
    (is (approx= 0.093 (nth a 1)))))
