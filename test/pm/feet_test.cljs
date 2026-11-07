(ns pm.feet-test
  (:require [cljs.test :refer [deftest is]]
            [pm.feet :as f]
            [pm.motion :as m]
            [pm.avoid :as a]
            [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def C (f/feet-config W))
(def C0 (assoc C :smooth-s 0 :tilt-blend-s 0))

(def H [0 1 0])
(def K [0.3 0.6 0])
(def A [0 0.2 0])

(def lvl {:obj-y 2.0 :base 2.0
          :feet [{:ankle [0 2.1 0]      :g 2.0 :n [0 1 0] :a0 0.1}
                 {:ankle [0.3 2.25 0.2] :g 2.3 :n [0 1 0] :a0 0.1}]})

(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn sub3 [[x1 y1 z1] [x2 y2 z2]] [(- x1 x2) (- y1 y2) (- z1 z2)])
(defn len3 [[x y z]] (js/Math.sqrt (+ (* x x) (* y y) (* z z))))
(defn dist3 [p q] (len3 (sub3 p q)))

;; Names and config

(deftest t01-sanitize
  (is (= "mixamorigLeft_Leg" (f/sanitize "mixamorig:Left Leg")))
  (is (= "abcd" (f/sanitize "a[b].c/d"))))

(deftest t02-find-bone-exact
  (is (= "LeftLeg" (f/find-bone "LeftLeg" ["Hips" "LeftLeg" "LeftShin"]))))

(deftest t03-find-bone-sanitized-with-prefix
  (is (= "mixamorigLeftUpLeg"
         (f/find-bone "mixamorig:LeftUpLeg" ["mixamorigHips" "mixamorigLeftUpLeg"]))))

(deftest t04-find-bone-without-prefix
  (is (= "LeftUpLeg" (f/find-bone "mixamorig:LeftUpLeg" ["Hips" "LeftUpLeg"]))))

(deftest t05-find-bone-pass-order
  (is (= "mixamorig:LeftUpLeg"
         (f/find-bone "mixamorig:LeftUpLeg" ["LeftUpLeg" "mixamorigLeftUpLeg" "mixamorig:LeftUpLeg"])))
  (is (= "mixamorigLeftUpLeg"
         (f/find-bone "mixamorig:LeftUpLeg" ["LeftUpLeg" "mixamorigLeftUpLeg"]))))

(deftest t06-find-bone-no-match
  (is (= "mixamorigLeftLeg"
         (f/find-bone "mixamorig:LeftLeg" ["mixamorigLeftUpLeg" "mixamorigLeftLeg"])))
  (is (nil? (f/find-bone "LeftLeg" ["LeftUpLeg"])))
  (is (nil? (f/find-bone "LeftLeg" [])))
  (is (nil? (f/find-bone "LeftLeg" nil))))

(deftest t07-leg-bones
  (is (= {:hip "mixamorigLeftUpLeg" :knee "mixamorigLeftLeg" :ankle "mixamorigLeftFoot" :toe nil}
         (f/leg-bones ["mixamorig:LeftUpLeg" "mixamorig:LeftLeg" "mixamorig:LeftFoot" "mixamorig:LeftToeBase"]
                       ["mixamorigLeftUpLeg" "mixamorigLeftLeg" "mixamorigLeftFoot"])))
  (is (nil? (f/leg-bones ["mixamorig:LeftUpLeg" "mixamorig:LeftLeg" "mixamorig:LeftFoot" "mixamorig:LeftToeBase"]
                          ["mixamorigLeftUpLeg" "mixamorigLeftFoot"]))))

(deftest t08-legs-estate
  (is (= #{:soma :mixamo} (set (keys (f/legs W)))))
  (is (= ["LeftLeg" "LeftShin" "LeftFoot" "LeftToeBase"] (get-in (f/legs W) [:soma :left])))
  (is (= "mixamorig:RightUpLeg" (get-in (f/legs W) [:mixamo :right 0]))))

(deftest t09-legs-filters-invalid
  (is (= {:a {:left ["A" "B" "C"] :right ["D" "E" "F"]}}
         (f/legs {:motion {:legs {:a {:left ["A" "B" "C"] :right ["D" "E" "F"]}
                                   :b {:left ["A" "B"] :right ["D" "E" "F"]}
                                   :c {:left ["A" "" "C"] :right ["D" "E" "F"]}
                                   :d "x"}}})))
  (is (= {} (f/legs {}))))

(deftest t10-feet-config
  (is (= {:drop-cap 0.35 :step-max 0.4 :plant-height 0.04 :plant-speed 0.3
          :tilt-max-deg 25 :tilt-blend-s 0.1 :smooth-s 0.08 :ray-move 0.03}
         (f/feet-config W)))
  (is (= (f/feet-config W) (f/feet-config {})))
  (let [c (f/feet-config {:motion {:feet {:drop-cap -1 :smooth-s 0 :step-max "x"}}})]
    (is (= 0.35 (:drop-cap c)))
    (is (= 0 (:smooth-s c)))
    (is (= 0.4 (:step-max c)))))

(deftest t11-ground-hit
  (is (= [2.4 [0.1 0.99 0]]
         (f/ground-hit [[2.0 "SM_Hiroshi_Terrain" [0 1 0]]
                        [2.4 "SM_Hiroshi_Gravel" [0.1 0.99 0]]
                        [3.0 "Rock" [0 1 0]]]
                       (:ground (m/config W)))))
  (is (= [2.0 [0 1 0]] (f/ground-hit [[2.0 "SM_Hiroshi_Terrain"]] (:ground (m/config W)))))
  (is (nil? (f/ground-hit [[3.0 "Rock" [0 1 0]]] (:ground (m/config W))))))

(deftest t12-needs-ray
  (is (true? (f/needs-ray? nil [0 0] 0.03)))
  (is (false? (f/needs-ray? [0 0] [0.02 0] 0.03)))
  (is (true? (f/needs-ray? [0 0] [0.03 0.01] 0.03))))

;; Two-bone IK

(deftest t13-two-bone-reachable
  (let [s (f/two-bone H K A [0 0.4 0] nil)]
    (is (vnear? [0.4 0.7 0] (:knee s)))
    (is (vnear? [0 0.4 0] (:ankle s)))
    (is (near? 0.6 (:reach s)))
    (is (false? (:clamped? s)))))

(deftest t14-two-bone-knee-side-kept
  (let [s (f/two-bone H [-0.3 0.6 0] A [0 0.4 0] nil)]
    (is (vnear? [-0.4 0.7 0] (:knee s)))))

(deftest t15-two-bone-unreachable-clamped
  (let [s (f/two-bone H K A [0 -0.5 0] nil)]
    (is (vnear? [0 0 0] (:ankle s)))
    (is (vnear? [0 0.5 0] (:knee s)))
    (is (near? 1 (:reach s)))
    (is (true? (:clamped? s)))))

(deftest t16-two-bone-straight-leg-pole
  (let [s1 (f/two-bone H [0 0.5 0] [0 0 0] [0 0.2 0] [0 0 1])
        s2 (f/two-bone H [0 0.5 0] [0 0 0] [0 0.2 0] nil)]
    (is (vnear? [0 0.6 0.3] (:knee s1)))
    (is (vnear? [0 0.2 0] (:ankle s1)))
    (is (vnear? [0 0.6 0.3] (:knee s2)))))

(deftest t17-two-bone-oblique
  (let [s (f/two-bone H K A [0.2 0.5 0.1] nil)]
    (is (vnear? [0.2 0.5 0.1] (:ankle s)))
    (is (near? 0.5 (dist3 (:knee s) H)))
    (is (near? 0.5 (dist3 (:ankle s) (:knee s))))
    (is (vnear? [0.4373853 0.8387856 -0.1808426] (:knee s)))))

(deftest t18-two-bone-target-at-hip
  (let [s (f/two-bone H K A [0 1 0] nil)]
    (is (near? 1e-4 (:reach s)))
    (is (true? (:clamped? s)))
    (is (vnear? [0 0.9999 0] (:ankle s)))
    (is (vnear? [0.5 0.99995 0] (:knee s)))))

;; Pelvis, base height, planted

(deftest t19-pelvis-drop
  (is (near? 0.2 (f/pelvis-drop 0.1 -0.2 0.35)))
  (is (= 0 (f/pelvis-drop 0.1 0.2 0.35)))
  (is (near? 0.35 (f/pelvis-drop -0.5 0 0.35)))
  (is (near? 0.1 (f/pelvis-drop nil -0.1 0.35)))
  (is (= 0 (f/pelvis-drop nil nil 0.35))))

(deftest t20-base-height
  (is (= 2.1 (f/base-height [2.1 2.3] nil)))
  (is (= 2.3 (f/base-height [nil 2.3] 2.0)))
  (is (= 2.0 (f/base-height [] 2.0)))
  (is (nil? (f/base-height [] nil))))

(deftest t21-planted
  (is (true? (f/planted? 0.03 0.1 C 1)))
  (is (false? (f/planted? 0.05 0.1 C 1)))
  (is (false? (f/planted? 0.03 0.5 C 1)))
  (is (true? (f/planted? 0.05 0.1 C 1.4)))
  (is (true? (f/planted? -0.02 0 C 1)))
  (is (false? (f/planted? nil 0 C 1))))

;; Tilt, blend, smoothing

(deftest t22-tilt-flat-or-nil
  (is (= {:axis nil :angle 0} (f/tilt [0 1 0] 25)))
  (is (= {:axis nil :angle 0} (f/tilt [0 2 0] 25)))
  (is (= {:axis nil :angle 0} (f/tilt [0 -1 0] 25)))
  (is (= {:axis nil :angle 0} (f/tilt nil 25))))

(deftest t23-tilt-10deg
  (let [t (f/tilt [0.1736482 0.9848078 0] 25)]
    (is (near? 0.1745329 (:angle t)))
    (is (vnear? [0 0 -1] (:axis t)))))

(deftest t24-tilt-capped
  (let [t (f/tilt [0.6427876 0.7660444 0] 25)]
    (is (near? 0.4363323 (:angle t)))
    (is (vnear? [0 0 -1] (:axis t)))))

(deftest t25-tilt-along-z
  (let [t (f/tilt [0 0.8 0.6] 25)]
    (is (vnear? [1 0 0] (:axis t)))
    (is (near? 0.4363323 (:angle t)))))

(deftest t26-blend-weight
  (is (near? 0.5 (f/blend-weight 0 true 0.05 0.1)))
  (is (= 1 (f/blend-weight 0.5 true 0.1 0.1)))
  (is (near? 0.7 (f/blend-weight 1 false 0.03 0.1)))
  (is (= 0 (f/blend-weight 0 false 0.1 0.1)))
  (is (= 1 (f/blend-weight 0.3 true 0.01 0))))

(deftest t27-smooth-by
  (is (near? 0.1896362 (f/smooth-by 0 0.3 0.08 0.08)))
  (is (near? 0.3934693 (f/smooth-by 0 1 0.04 0.08)))
  (is (= 0.3 (f/smooth-by nil 0.3 0.1 0.08)))
  (is (= 0.2 (f/smooth-by 0.2 nil 0.1 0.08)))
  (is (= 0.3 (f/smooth-by 0 0.3 0.1 0))))

;; foot-frame

(deftest t28-feet-init
  (is (= {:d [0 0] :drop 0 :w [0 0] :prev [nil nil]} (f/feet-init))))

(deftest t29-foot-frame-level
  (let [r (f/foot-frame (f/feet-init) lvl C0 1 0.1)]
    (is (= [true false] (:planted r)))
    (is (near? 2.0 (:ground r)))
    (is (vnear? [0 0.3] (:d (:state r))))
    (is (near? 0 (:drop (:state r))))
    (is (near? 2.0 (:obj-y r)))
    (is (vnear? [1 0] (:w (:state r))))
    (is (vnear? [0 2.1 0] (first (:targets r))))
    (is (vnear? [0.3 2.55 0.2] (second (:targets r))))
    (is (vnear? [0 0] (first (:prev (:state r)))))
    (is (vnear? [0.3 0.2] (second (:prev (:state r)))))))

(deftest t30-foot-frame-downhill-drop
  (let [lvl30 (assoc-in lvl [:feet 1 :g] 1.8)
        r (f/foot-frame (f/feet-init) lvl30 C0 1 0.1)]
    (is (vnear? [0 -0.2] (:d (:state r))))
    (is (near? 0.2 (:drop (:state r))))
    (is (near? 1.8 (:obj-y r)))
    (is (vnear? [0 2.1 0] (first (:targets r))))
    (is (vnear? [0.3 2.05 0.2] (second (:targets r))))))

(deftest t31-foot-frame-drop-cap
  (let [lvl31 (assoc-in lvl [:feet 1 :g] 1.5)
        r (f/foot-frame (f/feet-init) lvl31 C0 1 0.1)]
    (is (near? 0.35 (:drop (:state r))))
    (is (near? 1.65 (:obj-y r)))
    (is (near? 1.75 (second (second (:targets r)))))))

(deftest t32-foot-frame-moving-foot-not-planted
  (let [state0 (assoc (f/feet-init) :prev [[0 0] [0.3 0.2]])
        lvl32 (assoc-in lvl [:feet 0 :ankle] [0.05 2.1 0])
        r (f/foot-frame state0 lvl32 C0 1 0.1)]
    (is (= [false false] (:planted r)))
    (is (nil? (:ground r)))
    (is (near? 2.0 (:obj-y r)))))

(deftest t33-foot-frame-tilt-only-planted
  (let [n [0.1736482 0.9848078 0]
        lvl33 (-> lvl (assoc-in [:feet 0 :n] n) (assoc-in [:feet 1 :n] n))
        r (f/foot-frame (f/feet-init) lvl33 C0 1 0.1)
        [tl tr] (:tilts r)]
    (is (near? 0.1745329 (:angle tl)))
    (is (vnear? [0 0 -1] (:axis tl)))
    (is (near? 0 (:angle tr)))))

(deftest t34-foot-frame-no-ground-under-foot
  (let [lvl34 (assoc-in lvl [:feet 1 :g] nil)
        r (f/foot-frame (f/feet-init) lvl34 C0 1 0.1)]
    (is (near? 0 (second (:d (:state r)))))
    (is (near? 0 (:drop (:state r))))
    (is (near? 2.25 (second (second (:targets r)))))))

(deftest t35-foot-frame-display-scale-planting
  (let [lvl35 (assoc-in lvl [:feet 0 :ankle] [0 2.15 0])
        r14 (f/foot-frame (f/feet-init) lvl35 C0 1.4 0.1)
        r1  (f/foot-frame (f/feet-init) lvl35 C0 1 0.1)]
    (is (true? (first (:planted r14))))
    (is (near? 2.0 (:ground r14)))
    (is (false? (first (:planted r1))))
    (is (nil? (:ground r1)))))

(deftest t36-foot-frame-smoothed
  (let [r (f/foot-frame (f/feet-init) lvl C 1 0.08)]
    (is (near? 0.1896362 (second (:d (:state r)))))
    (is (near? 2.4396362 (second (second (:targets r)))))
    (is (near? 0.8 (first (:w (:state r)))))
    (is (near? 0 (:drop (:state r))))))

(deftest t37-foot-frame-base-from-lower-foot
  (let [input37 {:obj-y 2.0 :base 2.0
                 :feet [{:ankle [0 2.1 0]   :g 2.0 :n [0 1 0] :a0 0.1}
                        {:ankle [0.3 2.1 0.2] :g 2.3 :n [0 1 0] :a0 0.1}]}
        r (f/foot-frame (f/feet-init) input37 C0 1 0.1)]
    (is (near? 2.0 (:ground r)))
    (is (vnear? [0 2.1 0] (first (:targets r))))
    (is (vnear? [0.3 2.4 0.2] (second (:targets r))))))

;; Detours stay on walkable ground

(def L (assoc (m/walker {:id :l :route [[0 0] [8 0]] :pause-s 1}) :pause 0 :to 1 :leg 1))
(def S1 [{:at [4 0] :r 0.5}])
(def P (assoc L :pos [2 0] :y 2.0))
(def dcfg {:speed 1.2 :horizon-s 2})

(deftest t38-walkable
  (is (true? (m/walkable? 2.3 2.0 0.4)))
  (is (false? (m/walkable? 2.5 2.0 0.4)))
  (is (false? (m/walkable? 1.5 2.0 0.4)))
  (is (false? (m/walkable? nil 2.0 0.4))))

(deftest t39-plan-right-side-low-goes-left
  (let [p (m/plan P [] S1 (assoc dcfg :ground-at (fn [_ z] (if (pos? z) 0.5 2.0)) :step-max 0.4))]
    (is (vnear? [4.3424073 -1.4449073] (:aim p)))
    (is (= :left (:side p)))))

(deftest t40-plan-both-missing-waits
  (let [p (m/plan P [] S1 (assoc dcfg :ground-at (fn [_ _] nil)))]
    (is (nil? (:aim p)))
    (is (= 2 (:wait p)))))

(deftest t41-plan-ground-fine-right
  (let [p (m/plan P [] S1 (assoc dcfg :ground-at (fn [_ _] 2.3)))]
    (is (vnear? [4.3424073 1.4449073] (:aim p)))
    (is (= :right (:side p)))))

(deftest t42-plan-no-base-height-skips-rule
  (let [p (m/plan (assoc P :y nil) [] S1 (assoc dcfg :ground-at (fn [_ _] nil)))]
    (is (vnear? [4.3424073 1.4449073] (:aim p)))))

(deftest t43-plan-no-ground-at-unchanged
  (let [p (m/plan P [] S1 dcfg)]
    (is (vnear? [4.3424073 1.4449073] (:aim p)))
    (is (= :right (:side p)))))

(deftest t44-plan-real-data-ground-at
  (let [base (assoc (second (m/walkers W)) :pos [-6 -10] :to 0 :dir -1 :pause 0 :leg 2 :y 2.65)
        statics (a/statics W)
        p1 (m/plan base [] statics (assoc dcfg :ground-at (fn [_ _] 2.65)))
        p2 (m/plan base [] statics (assoc dcfg :ground-at (fn [x _] (if (< x -6) 0.5 2.65))))]
    (is (vnear? [-6.1208569 -11.724002] (:aim p1)))
    (is (= :left (:side p1)))
    (is (nil? (:aim p2)))
    (is (= 2 (:wait p2)))))
