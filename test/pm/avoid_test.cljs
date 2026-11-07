(ns pm.avoid-test
  (:require [cljs.test :refer [deftest is]]
            [pm.avoid :as a]
            [pm.motion :as m]
            [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def cfg {:speed 1.2 :horizon-s 2})

(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn circ-near? [c1 c2] (and (vnear? (:at c1) (:at c2)) (near? (:r c1) (:r c2))))
(defn circs-near? [cs1 cs2] (and (= (count cs1) (count cs2)) (every? true? (map circ-near? cs1 cs2))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))

(defn mv [e] (assoc (m/walker e) :pause 0 :to 1 :leg 1))

(def F (m/walker {:id :f :route [[0 0] [3 4] [6 0]] :pause-s 2}))
(def Fm (assoc F :pause 0 :to 1 :leg 1))
(def L (mv {:id :l :route [[0 0] [8 0]] :pause-s 1}))
(def S1 [{:at [4 0] :r 0.5}])
(def S2 [{:at [4 0] :r 0.5} {:at [4.3 1.5] :r 0.4}])
(def B (m/walker {:id :b :route [[4.3 -1.5] [9 -9]] :pause-s 50}))

(def W1 (first (m/walkers W)))
(def W2 (second (m/walkers W)))

;; Scenario helpers

(defn frames [ws statics n dt]
  (loop [walkers ws acc 0 i 0 out []]
    (if (>= i n)
      out
      (let [[walkers' acc'] (m/advance walkers statics cfg dt acc)]
        (recur walkers' acc' (inc i) (conj out walkers'))))))

(defn min-pair-dist [frs]
  (reduce min js/Infinity
          (map (fn [ws] (dist (:pos (nth ws 0)) (:pos (nth ws 1)))) frs)))

(defn min-static-gap [frs statics]
  (reduce min js/Infinity
          (for [ws frs w ws c statics]
            (- (dist (:pos w) (:at c)) (:r c) (:radius w)))))

(defn sim [ws statics n dt]
  (let [frs (frames ws statics n dt)]
    [(last frs)
     (if (>= (count ws) 2) (min-pair-dist frs) js/Infinity)
     (if (seq statics) (min-static-gap frs statics) js/Infinity)]))

(defn picks-of [ws0 frs idx]
  (loop [prev (nth ws0 idx) frs frs acc []]
    (if (empty? frs)
      acc
      (let [cur (nth (first frs) idx)
            acc' (if (not= (:leg prev) (:leg cur)) (conj acc (:to cur)) acc)]
        (recur cur (rest frs) acc')))))

(defn first-crossing [ws0 frs]
  (some (fn [[prev cur]]
          (let [d0 (- (first (:pos (nth prev 0))) (first (:pos (nth prev 1))))
                d1 (- (first (:pos (nth cur 0))) (first (:pos (nth cur 1))))]
            (when (or (zero? d1) (neg? (* d0 d1)))
              [(second (:pos (nth cur 0))) (second (:pos (nth cur 1)))])))
        (map vector (cons ws0 frs) frs)))

;; Footprints and statics

(deftest t01-footprint-yaw-scale
  (let [I {:at [1 0 2] :yaw 90 :scale 2 :footprint [[0.5 0 0.25] [0 1 0.1]]}]
    (is (circs-near? [{:at [1 1] :r 0.5} {:at [3 2] :r 0.2}] (a/footprint I)))))

(deftest t02-footprint-no-yaw-scale
  (is (circs-near? [{:at [1.5 2] :r 0.25}] (a/footprint {:at [1 0 2] :footprint [[0.5 0 0.25]]})))
  (let [I {:at [1 0 2] :yaw 180 :scale 1 :footprint [[0.5 0 0.25] [0 1 0.1]]}]
    (is (circ-near? {:at [0.5 2] :r 0.25} (first (a/footprint I))))))

(deftest t03-rotate
  (is (vnear? [0 -0.5] (a/rotate [0.5 0] 90)))
  (is (vnear? [1 0] (a/rotate [0 1] 90)))
  (is (vnear? [0 1] (a/rotate [1 0] -90))))

(deftest t04-footprint-drops-invalid-entries
  (is (circs-near? [{:at [0 0] :r 1}]
                    (a/footprint {:at [0 0 0] :footprint [[1 2] [0 0 -1] ["a" 0 1] [0 0 1]]})))
  (is (= [] (a/footprint {:footprint [[0 0 1]]})))
  (is (= [] (a/footprint {:at [0 0 0]}))))

(deftest t05-statics-world
  (let [ss (a/statics W)]
    (is (= 6 (count ss)))
    (is (circs-near? [{:at [1.4 -15.7] :r 0.35}
                       {:at [-5 -10.75] :r 0.5}
                       {:at [-5 -10] :r 0.5}
                       {:at [-5 -9.4] :r 0.45}
                       {:at [-4.8 -8.2] :r 0.7}
                       {:at [-4.3 -12] :r 0.6}]
                      ss))))

(deftest t06-statics-obstacles-and-empty
  (is (= [{:at [1 2] :r 0.5}]
         (a/statics {:motion {:obstacles [{:at [1 2] :r 0.5} {:at [1] :r 1} {:at [0 0] :r 0}]}})))
  (is (= [] (a/statics {}))))

(deftest t07-avoid-config
  (is (= {:radius 0.3 :horizon-s 2} (m/avoid-config W)))
  (is (= {:radius 0.3 :horizon-s 2} (m/avoid-config {}))))

(deftest t08-walker-radius-and-defaults
  (is (= [0.5 0.4]
         (mapv :radius (m/walkers {:motion {:radius 0.5}
                                    :walkers [{:id :a :route [[0 0]]}
                                              {:id :b :route [[0 0]] :radius 0.4}]}))))
  (is (= 0.3 (:radius F)))
  (is (= [0 0] (:vel F)))
  (is (nil? (:aim F)))
  (is (nil? (:goal F)))
  (is (nil? (:side F)))
  (is (= 0 (:wait F)))
  (is (false? (:waited F))))

;; Geometry

(deftest t09-first-block-basic
  (let [b (a/first-block [2 0] [1.2 0] 2 0.3 S1)]
    (is (near? 1.6666667 (:t b)))
    (is (vnear? [4 0] (:at b)))
    (is (near? 0.95 (:clear b)))
    (is (near? 0 (:dist b)))))

(deftest t10-first-block-head-on
  (let [b (a/first-block [0 0] [1.2 0] 2 0.3 [{:at [4 0] :r 0.3 :vel [-1.2 0]}])]
    (is (near? 1.6666667 (:t b)))
    (is (vnear? [2 0] (:at b)))
    (is (near? 0.75 (:clear b)))))

(deftest t11-first-block-nil-cases
  (doseq [c [{:at [-1 0] :r 0.3}
             {:at [-0.5 0] :r 0.3}
             {:at [4 0] :r 0.5}
             {:at [1 0] :r 0.3 :vel [1.2 0]}
             {:at [2 0.96] :r 0.5}]]
    (is (nil? (a/first-block [0 0] [1.2 0] 2 0.3 [c]))))
  (is (some? (a/first-block [0 0] [1.2 0] 2 0.3 [{:at [2 0.94] :r 0.5}]))))

(deftest t12-first-block-earliest-of-two
  (let [b (a/first-block [2 0] [1.2 0] 2 0.3 [{:at [4 0] :r 0.5} {:at [3 0] :r 0.5}])]
    (is (vnear? [3 0] (:at b)))
    (is (near? 0.8333333 (:t b)))))

(deftest t13-path-block
  (let [b (a/path-block [2 0] [4.3424073 -1.4449073] 1.2 2 0.3 [{:at [4.3 -1.5] :r 0.3}])]
    (is (near? 2 (:t b)))
    (is (near? 0.3518969 (:dist b))))
  (is (nil? (a/path-block [1 1] [1 1] 1.2 2 0.3 S1))))

(deftest t14-tangent-aim-far
  (is (vnear? [5.6 4.2] (a/tangent-aim [0 0] [5 0] 3 :right)))
  (is (vnear? [5.6 -4.2] (a/tangent-aim [0 0] [5 0] 3 :left))))

(deftest t15-tangent-aim-inside-and-coincident
  (is (vnear? [0 1.05] (a/tangent-aim [0 0] [0.5 0] 1.05 :right)))
  (is (vnear? [0 -1.05] (a/tangent-aim [0 0] [0.5 0] 1.05 :left)))
  (is (vnear? [1 2] (a/tangent-aim [1 1] [1 1] 1 :right))))

(deftest t16-tangent-aim-common
  (is (vnear? [4.3424073 1.4449073] (a/tangent-aim [2 0] [4 0] 1.05 :right))))

(deftest t17-free-goal-one-static
  (is (vnear? [4.2 0] (a/free-goal [0 0] [5 0] 0.3 [{:at [5.2 0] :r 0.5}]))))

(deftest t18-free-goal-two-statics
  (is (vnear? [3.3675445 0]
              (a/free-goal [0 0] [5 0] 0.3 [{:at [5.2 0] :r 0.5} {:at [4.0 0.3] :r 0.2}]))))

(deftest t19-free-goal-behind-and-clear
  (is (vnear? [0 0] (a/free-goal [0 0] [1 0] 0.3 [{:at [1 0] :r 2}])))
  (is (vnear? [5 0] (a/free-goal [0 0] [5 0] 0.3 [{:at [5 3] :r 0.5}]))))

(deftest t20-inside
  (is (true? (a/inside? [4.3424073 1.4449073] 0.3 [{:at [4.3 1.5] :r 0.4}])))
  (is (false? (a/inside? [0 0] 0.3 S1))))

(deftest t21-circle-points
  (let [pts (a/circle-points [1 2] 0.5 4)]
    (is (= 5 (count pts)))
    (is (every? true? (map vnear? [[1.5 2] [1 2.5] [0.5 2] [1 1.5] [1.5 2]] pts)))
    (is (= (first pts) (last pts)))))

;; Planner: clear, static, sides

(deftest t22-plan-clear
  (let [p (m/plan (assoc L :pos [2 0]) [] [{:at [4 2] :r 0.5}] cfg)]
    (is (nil? (:aim p)))
    (is (nil? (:side p)))
    (is (= 0 (:wait p)))))

(deftest t23-plan-out-of-horizon
  (is (nil? (:aim (m/plan L [] S1 cfg)))))

(deftest t24-plan-static-right
  (let [p (m/plan (assoc L :pos [2 0]) [] S1 cfg)]
    (is (vnear? [4.3424073 1.4449073] (:aim p)))
    (is (= :right (:side p)))
    (is (nil? (:goal p)))))

(deftest t25-plan-right-blocked-goes-left
  (let [p (m/plan (assoc L :pos [2 0]) [] S2 cfg)]
    (is (vnear? [4.3424073 -1.4449073] (:aim p)))
    (is (= :left (:side p)))))

(deftest t26-plan-sticky-side
  (let [p (m/plan (assoc L :pos [2 0] :side :left) [] S1 cfg)]
    (is (vnear? [4.3424073 -1.4449073] (:aim p)))
    (is (= :left (:side p)))))

;; Planner: both blocked, wait, go, give up

(def P5 (m/plan (assoc L :pos [2 0]) [B] S2 cfg))

(deftest t27-plan-both-blocked-waits
  (is (nil? (:aim P5)))
  (is (= 2 (:wait P5)))
  (is (false? (:waited P5))))

(def Wt (m/step P5 1.2 0.5))

(deftest t28-step-and-plan-still-waiting
  (is (near? 1.5 (:wait Wt)))
  (is (vnear? [2 0] (:pos Wt)))
  (is (vnear? [0 0] (:vel Wt)))
  (let [p (m/plan Wt [B] S2 cfg)]
    (is (near? 1.5 (:wait p)))
    (is (nil? (:aim p)))))

(deftest t29-plan-goes-once-clear
  (let [p (m/plan Wt [(assoc B :pos [20 20])] S2 cfg)]
    (is (vnear? [4.3424073 -1.4449073] (:aim p)))
    (is (= :left (:side p)))
    (is (= 0 (:wait p)))))

(def Wx (m/step Wt 1.2 2))

(deftest t30-step-wait-runs-out-then-gives-up
  (is (= 0 (:wait Wx)))
  (is (true? (:waited Wx)))
  (let [p (m/plan Wx [B] S2 cfg)]
    (is (= 1 (:pause p)))
    (is (= 1 (:to p)))
    (is (= 1 (:leg p)))
    (is (nil? (:aim p)))
    (is (false? (:waited p)))))

;; Planner: walkers and targets

(deftest t31-plan-head-on
  (let [A (assoc (mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) :vel [1.2 0])
        H (assoc (mv {:id :h :route [[10 0] [0 0]] :pause-s 100}) :pos [4 0] :vel [-1.2 0])
        pa (m/plan A [H] [] cfg)
        ph (m/plan H [A] [] cfg)]
    (is (vnear? [2.4081643 1.1306643] (:aim pa)))
    (is (= :right (:side pa)))
    (is (vnear? [1.5918357 -1.1306643] (:aim ph)))
    (is (= :right (:side ph)))))

(deftest t32-plan-crossing
  (let [X (assoc (mv {:id :x :route [[0 0] [8 0]] :pause-s 100}) :vel [1.2 0])
        Y (assoc (mv {:id :y :route [[4 -4] [4 4]] :pause-s 100}) :pos [2 -2] :vel [0 1.2])
        py (m/plan Y [X] [] cfg)
        px (m/plan X [Y] [] cfg)]
    (is (vnear? [3.5734476 0.1958377] (:aim py)))
    (is (= :left (:side py)))
    (is (= 2 (:wait px)))
    (is (nil? (:aim px)))))

(deftest t33-plan-target-inside-footprint
  (let [T (mv {:id :t :route [[0 0] [5 0]] :pause-s 100})
        p (m/plan T [] [{:at [5.2 0] :r 0.5}] cfg)]
    (is (vnear? [4.2 0] (:goal p)))
    (is (nil? (:aim p)))))

(deftest t34-plan-real-data-first-walker
  (let [p (m/plan (assoc W1 :pos [-6 -10] :to 2 :dir 1 :pause 0 :leg 2) [] (a/statics W) cfg)]
    (is (vnear? [-6.1208569 -11.724002] (:aim p)))
    (is (= :left (:side p)))))

(deftest t35-plan-real-data-second-walker
  (let [p (m/plan (assoc W2 :pos [-6 -10] :to 0 :dir -1 :pause 0 :leg 2) [] (a/statics W) cfg)]
    (is (vnear? [-6.1208569 -11.724002] (:aim p)))
    (is (= :left (:side p)))))

(deftest t36-plan-real-data-clear-leg
  (is (nil? (:aim (m/plan (assoc W1 :pause 0 :to 1 :leg 1) [] (a/statics W) cfg)))))

(deftest t37-plan-paused-or-single-point
  (let [p (m/plan F [] S1 cfg)]
    (is (nil? (:aim p)))
    (is (= 2 (:pause p))))
  (is (nil? (:aim (m/plan (m/walker {:id :c :route [[0 0]]}) [] S1 cfg)))))

;; step / follow with aims, goals and waits

(deftest t38-step-toward-aim
  (let [w' (m/step (assoc Fm :aim [0 2]) 1 1)]
    (is (vnear? [0 1] (:pos w')))
    (is (near? -1.5707963 (:heading w')))
    (is (vnear? [0 1] (:vel w')))
    (is (vnear? [0 2] (:aim w')))
    (is (= 0 (:pause w')))))

(deftest t39-step-aim-cleared-near-arrival
  (let [w' (m/step (assoc Fm :aim [0 1.2]) 1 1)]
    (is (vnear? [0 1] (:pos w')))
    (is (nil? (:aim w')))))

(deftest t40-step-arrive-at-goal-not-target
  (let [w' (m/step (assoc Fm :goal [0.6 0.8]) 1 0.8)]
    (is (vnear? [0.48 0.64] (:pos w')))
    (is (near? 2 (:pause w')))
    (is (nil? (:goal w')))))

(deftest t41-step-goal-arrival-while-detouring
  (let [w' (m/step (assoc Fm :pos [2.5 3.5] :aim [5 5]) 1 0.5)]
    (is (vnear? [2.9287465 3.7572479] (:pos w')))
    (is (near? 2 (:pause w')))
    (is (nil? (:aim w')))))

(deftest t42-step-goal-close
  (let [w' (m/step (assoc Fm :goal [0.1 0]) 1 0.1)]
    (is (near? 2 (:pause w')))
    (is (vnear? [0 0] (:pos w')))))

(deftest t43-step-vel
  (is (vnear? [0.6 0.8] (:vel (m/step Fm 1 1))))
  (is (vnear? [0 0] (:vel (m/step F 1 0.5)))))

(deftest t44-step-wait
  (let [w' (m/step (assoc Fm :wait 2 :vel [1 0]) 1 0.5)]
    (is (near? 1.5 (:wait w')))
    (is (vnear? [0 0] (:pos w')))
    (is (vnear? [0 0] (:vel w')))
    (is (false? (:waited w')))
    (let [w'' (m/step w' 1 2)]
      (is (= 0 (:wait w'')))
      (is (true? (:waited w''))))))

(deftest t45-step-pick-clears-state
  (let [w' (m/step (assoc F :aim [9 9] :goal [1 1] :side :left :waited true) 1 2)]
    (is (= 1 (:to w')))
    (is (nil? (:aim w')))
    (is (nil? (:goal w')))
    (is (nil? (:side w')))
    (is (false? (:waited w')))))

(deftest t46-follow-aim-blocks-idle-arrival
  (let [w' (m/follow (assoc Fm :aim [0 2]) [0 0.5] true 0.5)]
    (is (= 0 (:pause w')))
    (is (vnear? [0 1] (:vel w')))
    (is (vnear? [0 0.5] (:pos w')))))

(deftest t47-follow-aim-cleared
  (let [w' (m/follow (assoc Fm :aim [0 2]) [0 1.8] false 0.1)]
    (is (nil? (:aim w')))
    (is (= 0 (:pause w')))))

(deftest t48-follow-wait
  (let [w' (m/follow (assoc Fm :wait 1) [0 0] true 0.5)]
    (is (near? 0.5 (:wait w')))
    (is (= 0 (:pause w'))))
  (let [w' (m/follow (assoc Fm :wait 0.4) [0 0] true 0.5)]
    (is (= 0 (:wait w')))
    (is (true? (:waited w')))
    (is (= 0 (:pause w')))))

(deftest t49-follow-goal-and-vel-dt0
  (is (near? 2 (:pause (m/follow (assoc Fm :goal [1 1]) [0.9 0.9] false 0.1))))
  (is (vnear? [5 5] (:vel (m/follow (assoc Fm :vel [5 5]) [1 1] false 0)))))

;; Cadence and commands

(deftest t50-advance-cadence
  (let [[ws acc] (m/advance [L] [] cfg 0.1 0)]
    (is (vnear? [0.12 0] (:pos (first ws))))
    (is (near? 0.1 acc)))
  (is (= 0 (second (m/advance [L] [] cfg 0.1 0.2)))))

(deftest t51-advance-leg-change-plans-now
  (let [[ws acc] (m/advance [(assoc (m/walker {:id :l :route [[0 0] [8 0]] :pause-s 1}) :pause 0.05)]
                             [] cfg 0.1 0)]
    (is (= 0 acc))
    (is (= 1 (:leg (first ws))))))

(deftest t52-advance-plans-and-detours
  (let [[ws acc] (m/advance [(assoc L :pos [1.9 0])] S1 cfg 0.25 0)
        w1 (first ws)]
    (is (vnear? [2.2 0] (:pos w1)))
    (is (vnear? [4.2403445 1.4653445] (:aim w1)))
    (is (= :right (:side w1)))
    (is (= 0 acc))))

(deftest t53-detours
  (is (= 2 (m/detours [(assoc F :aim [1 1]) F (assoc F :aim [2 2])])))
  (is (= 0 (m/detours []))))

(deftest t54-steer-command-resend-threshold
  (is (= [:move [3 4]] (m/steer-command nil Fm)))
  (is (nil? (m/steer-command [:move [3 4]] Fm)))
  (is (nil? (m/steer-command [:move [3.3 4.3]] Fm)))
  (is (= [:move [3 4]] (m/steer-command [:move [3.4 4.4]] Fm))))

(deftest t55-steer-command-aim-goal
  (is (= [:move [1 2]] (m/steer-command [:move [3 4]] (assoc Fm :aim [1 2]))))
  (is (= [:move [2.5 3.5]] (m/steer-command nil (assoc Fm :goal [2.5 3.5])))))

(deftest t56-steer-command-wait-and-paused
  (is (= [:stop] (m/steer-command [:move [3 4]] (assoc Fm :wait 1))))
  (is (nil? (m/steer-command [:stop] (assoc Fm :wait 1))))
  (is (nil? (m/steer-command nil F))))

;; HUD and flag

(deftest t57-hud-text-detours-kinematic
  (is (= "motion: kinematic · 1 detour" (m/hud-text {:mode :kinematic :detours 1})))
  (is (= "motion: kinematic · 2 detours" (m/hud-text {:mode :kinematic :detours 2})))
  (is (= "motion: kinematic" (m/hud-text {:mode :kinematic :detours 0}))))

(deftest t58-hud-text-detours-bricks-and-failed
  (is (= "motion: bricks webgpu · 2 walkers · gen 420 ms · 1 detour"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :detours 1})))
  (is (= "motion: kinematic (bricks failed: x) · 1 detour"
         (m/hud-text {:mode :failed :reason "x" :detours 1})))
  (is (nil? (m/hud-text {:mode :off :detours 1}))))

(deftest t59-paths-flag
  (is (true? (m/paths-flag? "?paths")))
  (is (true? (m/paths-flag? "?bricks&paths")))
  (is (false? (m/paths-flag? "?bricks")))
  (is (false? (m/paths-flag? nil))))

;; Scenarios

(deftest t60-scenario-head-on
  (let [ws0 [(mv {:id :a :route [[0 0] [10 0]] :pause-s 100})
             (mv {:id :h :route [[10 0] [0 0]] :pause-s 100})]
        frs (frames ws0 [] 240 0.05)
        final (last frs)]
    (is (>= (min-pair-dist frs) 0.6))
    (let [[za zh] (first-crossing ws0 frs)]
      (is (some? za))
      (is (> za zh)))
    (is (> (:pause (nth final 0)) 0))
    (is (= 1 (:to (nth final 0))))
    (is (<= (dist (:pos (nth final 0)) [10 0]) 0.3))
    (is (> (:pause (nth final 1)) 0))
    (is (= 1 (:to (nth final 1))))
    (is (<= (dist (:pos (nth final 1)) [0 0]) 0.3))))

(deftest t61-scenario-crossing
  (let [ws0 [(mv {:id :x :route [[0 0] [8 0]] :pause-s 100})
             (mv {:id :y :route [[4 -4] [4 4]] :pause-s 100})]
        [final min-d _] (sim ws0 [] 240 0.05)]
    (is (>= min-d 0.6))
    (is (> (:pause (nth final 0)) 0))
    (is (> (:pause (nth final 1)) 0))))

(deftest t62-scenario-static-on-line
  (let [ws0 [(mv {:id :l :route [[0 0] [8 0]] :pause-s 100})]
        [final _ min-s] (sim ws0 S1 200 0.05)
        w1 (first final)]
    (is (>= min-s 0))
    (is (<= (dist (:pos w1) [8 0]) 0.3))
    (is (> (:pause w1) 0))))

(deftest t63-scenario-right-blocked
  (let [ws0 [(assoc (mv {:id :l :route [[0 0] [8 0]] :pause-s 100}) :pos [2 0])]
        [final _ min-s] (sim ws0 S2 200 0.05)
        w1 (first final)]
    (is (>= min-s 0))
    (is (<= (dist (:pos w1) [8 0]) 0.3))
    (is (> (:pause w1) 0))))

(deftest t64-scenario-target-inside
  (let [T (mv {:id :t :route [[0 0] [5 0]] :pause-s 100})
        [final _ min-s] (sim [T] [{:at [5.2 0] :r 0.5}] 120 0.05)
        w1 (first final)
        [x z] (:pos w1)]
    (is (>= min-s 0))
    (is (> (:pause w1) 0))
    (is (= 1 (:to w1)))
    (is (<= 3.84 x 3.96))
    (is (near? 0 z))))

(deftest t65-scenario-real-estate
  (let [ws0 (m/walkers W)
        statics (a/statics W)
        frs (frames ws0 statics 2400 0.05)
        final (last frs)]
    (is (>= (min-pair-dist frs) 0.6))
    (is (>= (min-static-gap frs statics) 0))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (picks-of ws0 frs 0))))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (picks-of ws0 frs 1))))
    (is (>= (:leg (nth final 0)) 12))
    (is (>= (:leg (nth final 1)) 12))))
