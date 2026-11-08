(ns pm.creature-reflex-test
  (:require [cljs.test :refer [deftest is]]
            [pm.creature-motion :as cm]))

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

(def deg30 0.5235987755982988)

;; X1

(deftest x1-tilted-head-untilts
  (let [jacr #js [1 0 0]
        jacp #js [0 0 0]
        r {:dofs [0] :ranges [[-1.5 1.5]] :lift-m 0.25 :gain 0.5 :w-lift 0}
        up [0 -0.5 0.8660254]
        sense {:up up :clearance 1 :jacp jacp :jacr jacr}]
    (is (approx= [0.2735988] (cm/reflex-targets r sense [deg30] 1 1)))))

;; X2

(deftest x2-level-head-no-change
  (let [jacr #js [1 0 0]
        jacp #js [0 0 0]
        r {:dofs [0] :ranges [[-1.5 1.5]] :lift-m 0.25 :gain 0.5 :w-lift 0}
        sense {:up [0 0 1] :clearance 1 :jacp jacp :jacr jacr}]
    (is (approx= [0.2] (cm/reflex-targets r sense [0.2] 1 1)))))

;; X3

(deftest x3-clamping
  (let [jacr #js [1 0 0]
        jacp #js [0 0 0]
        r {:dofs [0] :ranges [[-0.3 1.0]] :lift-m 0.25 :gain 4 :w-lift 0}]
    (is (approx= [-0.3] (cm/reflex-targets r {:up [0 -0.5 0.8660254] :clearance 1 :jacp jacp :jacr jacr} [deg30] 1 1)))
    (is (approx= [1.0] (cm/reflex-targets r {:up [0 0.5 0.8660254] :clearance 1 :jacp jacp :jacr jacr} [deg30] 1 1)))))

;; X4

(deftest x4-strength
  (let [jacr #js [1 0 0]
        jacp #js [0 0 0]
        r {:dofs [0] :ranges [[-1.5 1.5]] :lift-m 0.25 :gain 0.5 :w-lift 0}
        sense {:up [0 -0.5 0.8660254] :clearance 1 :jacp jacp :jacr jacr}]
    (is (approx= [0.5235988] (cm/reflex-targets r sense [deg30] 0 1)))
    (is (approx= [0.3985988] (cm/reflex-targets r sense [deg30] 0.5 1)))))

;; X5

(deftest x5-lift-term-threshold
  (let [jacp #js [0 0 -1]
        jacr #js [0 1 0]
        r {:dofs [0] :ranges [[-1 1]] :lift-m 0.3 :gain 0.5 :w-lift 2}]
    (is (approx= [-0.2] (cm/reflex-targets r {:up [0 0 1] :clearance 0.1 :jacp jacp :jacr jacr} [0] 1 1)))
    (is (approx= [0] (cm/reflex-targets r {:up [0 0 1] :clearance 0.35 :jacp jacp :jacr jacr} [0] 1 1)))
    (is (approx= [0] (cm/reflex-targets r {:up [0 0 1] :clearance 0.3 :jacp jacp :jacr jacr} [0] 1 1)))))

;; X6

(deftest x6-only-own-dofs-row-major
  (let [jacr #js [0 0 1 0 1 0 1 0 0]
        jacp #js [0 0 0 0 0 0 0 0 0]
        r {:dofs [1 2] :ranges [[-1 1] [-1 1]] :lift-m 0 :gain 1 :w-lift 0}
        sense {:up [0.6 0 0.8] :clearance 1 :jacp jacp :jacr jacr}]
    (is (approx= [-0.2 0.2] (cm/reflex-targets r sense [0.1 0.2] 0.5 3)))))

;; X7

(deftest x7-head-up-and-tilt-deg
  (let [xmat #js [1 0 0 0 1 0 0 0 1
                  0.877583 0 0.479426 0 1 0 -0.479426 0 0.877583]]
    (is (approx= [0.479426 0 0.877583] (cm/head-up xmat 1 [0 0 1])))
    (is (approx= [0.877583 0 -0.479426] (cm/head-up xmat 1 [1 0 0])))
    (is (approx= 28.647890 (cm/tilt-deg [0.479426 0 0.877583]) 1e-4))
    (is (approx= 0 (cm/tilt-deg [0 0 1])))
    (is (approx= 180 (cm/tilt-deg [0 0 -1])))
    (is (approx= 90 (cm/tilt-deg [1 0 0])))
    (is (approx= [0 0 1] (cm/head-up xmat 0 [0 0 1])))))

;; X8

(deftest x8-capsule-clearance
  (let [xpos #js [0 0 0 0 0 1]
        cap {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07}
        xmat-id #js [1 0 0 0 1 0 0 0 1
                     1 0 0 0 1 0 0 0 1]
        xmat-rx #js [1 0 0 0 1 0 0 0 1
                     1 0 0 0 0 -1 0 1 0]
        xmat-ry #js [1 0 0 0 1 0 0 0 1
                     0 0 1 0 1 0 -1 0 0]]
    (is (approx= 0.33 (cm/capsule-clearance xpos xmat-id 1 cap 0.5)))
    (is (approx= 0.43 (cm/capsule-clearance xpos xmat-rx 1 cap 0.5)))
    (is (approx= 0.19 (cm/capsule-clearance xpos xmat-ry 1 cap 0.5)))))

;; X9

(deftest x9-reflex-strength
  (is (approx= 0 (cm/reflex-strength 0 1.5)))
  (is (approx= 0.5 (cm/reflex-strength 0.75 1.5)))
  (is (approx= 1 (cm/reflex-strength 1.5 1.5)))
  (is (approx= 1 (cm/reflex-strength 3 1.5)))
  (is (approx= 0 (cm/reflex-strength -1 1.5)))
  (is (approx= 1 (cm/reflex-strength 0.3 0))))

;; X10

(deftest x10-target->action
  (is (approx= 0 (cm/target->action 0.5 [-0.5 1.5])))
  (is (approx= 1 (cm/target->action 1.5 [-0.5 1.5])))
  (is (approx= -1 (cm/target->action -0.5 [-0.5 1.5])))
  (is (approx= 0.5 (cm/target->action 1.0 [-0.5 1.5])))
  (is (approx= [0.3] (cm/actions->ctrl [(cm/target->action 0.3 [-0.5 1.5])] [[-0.5 1.5]]))))

;; X11

(deftest x11-reflex-layer-over-still
  (let [inner (cm/controller :still 3 1)
        r {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j]
           :lift-m 0 :gain 1 :w-lift 0 :onset-s 0}
        ctrl (cm/reflex-controller inner [r])
        qpos (doto #js [0 0 0 0 0 0 0 0 0] (aset 8 0.1))
        zeros9 #js [0 0 0 0 0 0 0 0 0]
        jacr #js [0 0 1 0 1 0 1 0 0]
        obs {:nv 3 :qpos qpos :senses [{:up [0.6 0 0.8] :clearance 1 :jacp zeros9 :jacr jacr}]}]
    (is (= {:kind :reflex :n 3 :inner inner :reflexes [r] :t 0} ctrl))
    (let [[ctrl' a] (cm/act ctrl obs 0.002)]
      (is (approx= [0 -0.5 0] a))
      (is (approx= 0.002 (:t ctrl')))
      (is (= inner (:inner ctrl'))))
    (let [onset-ctrl (assoc ctrl :reflexes [(assoc r :onset-s 1.5)])
          [_ a] (cm/act onset-ctrl obs 0.002)]
      (is (approx= [0 0.0992 0] a)))
    (let [[ctrl' a] (cm/act ctrl nil 0.002)]
      (is (approx= [0 0 0] a))
      (is (approx= 0.002 (:t ctrl'))))
    (let [onset-ctrl (assoc ctrl :reflexes [(assoc r :onset-s 1.5)])
          [ctrl' a] (cm/act onset-ctrl obs 0)]
      (is (approx= 0 (:t ctrl')))
      (is (approx= 0.1 (nth a 1))))))

;; X12

(deftest x12-reflex-joints-leave-twitch
  (let [tw (cm/twitch-init 3 1)
        r {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j]
           :lift-m 0 :gain 1 :w-lift 0 :onset-s 0}
        qpos (doto #js [0 0 0 0 0 0 0 0 0] (aset 8 0.1))
        zeros9 #js [0 0 0 0 0 0 0 0 0]
        jacr #js [0 0 1 0 1 0 1 0 0]
        obs {:nv 3 :qpos qpos :senses [{:up [0.6 0 0.8] :clearance 1 :jacp zeros9 :jacr jacr}]}
        [c' a] (cm/act (cm/reflex-controller tw [r]) obs 0.002)
        expected-actions (cm/twitch-actions (cm/twitch-step tw 0.002))]
    (is (= (nth expected-actions 0) (nth a 0)))
    (is (= (nth expected-actions 2) (nth a 2)))
    (is (approx= -0.5 (nth a 1)))
    (is (= (cm/twitch-step tw 0.002) (:inner c')))))

;; X13

(deftest x13-sense
  (let [cap {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07}
        xpos #js [0 0 0 0 0 1]
        xmat #js [1 0 0 0 1 0 0 0 1
                  1 0 0 0 1 0 0 0 1]
        jp #js [1 2 3]
        jr #js [4 5 6]
        r {:body-id 1 :up [0 0 1] :capsule cap}
        s (cm/sense r xpos xmat jp jr 0.5)]
    (is (approx= [0 0 1] (:up s)))
    (is (approx= 0.33 (:clearance s)))
    (is (identical? jp (:jacp s)))
    (is (identical? jr (:jacr s)))))

;; X14

(deftest x14-hud-head
  (is (= "pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · head 0.24 m 1°"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149
                        :head {:clearance 0.2437 :tilt 1.2}}))))
