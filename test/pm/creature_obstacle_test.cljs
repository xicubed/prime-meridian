(ns pm.creature-obstacle-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]
            [pm.creature :as c] [pm.creature-motion :as cm] [pm.species :as species]))

(def W
  ;; the crew's original routes (wandering off: pm.wander-test covers it), and
  ;; Hal's new post left out, so these laps measure the scenes they were written for
  (update (w/load-estate w/estate-edn) :walkers
          (fn [ws] (into [] (comp (remove #(= :hal (:id %))) (map #(dissoc % :wander))) ws))))
(def cfg {:speed 1.2 :horizon-s 2})
(def horse (c/parse (get species/texts :horse)))
(def dog   (c/parse (get species/texts :dog)))
(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))

;; MuJoCo arrays from rows [[xpos3 xmat9] ...] for body ids 1..n (id 0, the world, is zeros)
(defn arrays [rows]
  [(clj->js (into [0 0 0] (mapcat first rows)))
   (clj->js (into (vec (repeat 9 0)) (mapcat second rows)))])
(def I    [1 0 0  0 1 0  0 0 1])
(def Rz90 [0 -1 0  1 0 0  0 0 1])     ; yaw +90° about MuJoCo z: body +x -> MuJoCo +y -> game -z
(def Rlie [1 0 0  0 0 -1  0 1 0])     ; roll +90° about body x: lying on the side, legs toward MuJoCo +y
(def Rfl  [0 0 1  1 0 0  0 1 0])      ; Rz(+90)·Rlie: a lying front leg folded back along the belly
(def Rhl  [0 0 -1  -1 0 0  0 1 0])    ; Rz(-90)·Rlie: a lying hind leg folded forward
(def qv (clj->js [0.6 0.8 0.1 0 0 0]))
(defn circle-of [sp rows] (let [[xp xm] (arrays rows)] (cm/ground-circle (cm/capsules sp) xp xm qv)))

(defn mv [e] (assoc (m/walker e) :pause 0 :to 1 :leg 1))
(def seen-1s {:on 1 :off 0 :noticed true :how :sight})
(defn frames ([ws statics n] (frames ws statics n (constantly nil)))
  ([ws statics n extras-at]           ; extras-at: frame index i (0-based) -> extras for that advance
   (loop [ws ws acc 0 i 0 out []]
     (if (>= i n) out
       (let [[ws' acc'] (m/advance ws statics cfg 0.05 acc (extras-at i))]
         (recur ws' acc' (inc i) (conj out ws')))))))
(defn first-index [pred xs] (first (keep-indexed (fn [i x] (when (pred x) i)) xs)))
(defn gap [w c] (- (dist (:pos w) (:at c)) (:r c) (:radius w)))   ; < 0 = inside r + walker r

;; -- §5.1 body rows: [[xpos3 xmat9] ...] for body ids 1..12, hand-checked in §7 ----

(def horse-standing-rows
  [[[4.5 8.0 0.9] I] [[4.8 8.0 0.98] I] [[4.98 8.0 1.22] I]
   [[4.78 8.1 0.85] I] [[4.78 8.1 0.47] I] [[4.78 7.9 0.85] I] [[4.78 7.9 0.47] I]
   [[4.5 8.0 0.9] I] [[4.22 8.1 0.85] I] [[4.22 8.1 0.47] I] [[4.22 7.9 0.85] I] [[4.22 7.9 0.47] I]])

(def horse-yaw90-rows
  [[[4.5 8.0 0.9] Rz90] [[4.5 8.3 0.98] Rz90] [[4.5 8.48 1.22] Rz90]
   [[4.4 8.28 0.85] Rz90] [[4.4 8.28 0.47] Rz90] [[4.6 8.28 0.85] Rz90] [[4.6 8.28 0.47] Rz90]
   [[4.5 8.0 0.9] Rz90] [[4.4 7.72 0.85] Rz90] [[4.4 7.72 0.47] Rz90] [[4.6 7.72 0.85] Rz90] [[4.6 7.72 0.47] Rz90]])

(def horse-lying-rows
  [[[4.5 8.0 0.15] Rlie] [[4.8 7.92 0.15] Rlie] [[4.98 7.68 0.15] Rlie]
   [[4.78 8.05 0.25] Rlie] [[4.78 8.43 0.25] Rlie] [[4.78 8.05 0.05] Rlie] [[4.78 8.43 0.05] Rlie]
   [[4.5 8.0 0.15] Rlie] [[4.22 8.05 0.25] Rlie] [[4.22 8.43 0.25] Rlie] [[4.22 8.05 0.05] Rlie] [[4.22 8.43 0.05] Rlie]])

(def horse-curled-rows
  [[[4.5 8.0 0.15] Rlie] [[4.8 7.92 0.15] Rlie] [[4.98 7.68 0.15] Rlie]
   [[4.78 8.05 0.25] Rfl] [[4.4 8.05 0.25] Rfl] [[4.78 8.05 0.05] Rfl] [[4.4 8.05 0.05] Rfl]
   [[4.5 8.0 0.15] Rlie] [[4.22 8.05 0.25] Rhl] [[4.6 8.05 0.25] Rhl] [[4.22 8.05 0.05] Rhl] [[4.6 8.05 0.05] Rhl]])

(def dog-standing-rows
  [[[0 0 0.5] I] [[0.2 0 0.54] I] [[0.29 0 0.66] I]
   [[0.2 0.07 0.47] I] [[0.2 0.07 0.27] I] [[0.2 -0.07 0.47] I] [[0.2 -0.07 0.27] I]
   [[0 0 0.5] I] [[-0.2 0.07 0.47] I] [[-0.2 0.07 0.27] I] [[-0.2 -0.07 0.47] I] [[-0.2 -0.07 0.27] I]])

(def dog-lying-rows
  [[[4.5 8.0 0.1] Rlie] [[4.7 7.96 0.1] Rlie] [[4.79 7.84 0.1] Rlie]
   [[4.7 8.03 0.17] Rlie] [[4.7 8.23 0.17] Rlie] [[4.7 8.03 0.03] Rlie] [[4.7 8.23 0.03] Rlie]
   [[4.5 8.0 0.1] Rlie] [[4.3 8.03 0.17] Rlie] [[4.3 8.23 0.17] Rlie] [[4.3 8.03 0.03] Rlie] [[4.3 8.23 0.03] Rlie]])

;; -- §5.2 Lap circles ----------------------------------------------------------

(def STAND {:id :creature :at [4.67 -8.0] :r 0.62})
(def LIE   {:id :creature :at [4.67 -8.225] :r 0.7774779})
(def SPOT  (let [[x _ z] (:at (:creature-spike W))] {:id :creature :at [x z] :r 1.0}))  ; [4.5 -8.0]
(defn kick [i]   ; LIE until frame 600, rolls 0.6 m toward -x over 10 frames at 1.2 m/s, then still
  (let [[x z] (:at LIE)]
    (cond (< i 600) [LIE]
          (< i 610) [(assoc LIE :at [(- x (* 0.06 (- i 599))) z] :vel [-1.2 0])]
          :else     [(assoc LIE :at [(- x 0.6) z])])))

;; -- Extras and senses fixtures --------------------------------------------------

(def A (m/walker {:id :a :route [[0 0] [10 0]]}))
(defn ob [at r v] (first (m/extra-others [{:id :foal :at at :r r :vel v}])))

;; -- The planner fixture ----------------------------------------------------------

(def L (mv {:id :l :route [[0 0] [8 0]] :pause-s 1}))

;; ================================================================================
;; The circle from body positions (pm.creature-motion)
;; ================================================================================

(deftest case01-capsules
  (is (= 12 (count (cm/capsules horse))))
  (is (= (vec (range 1 13)) (mapv :id (cm/capsules horse))))
  (is (= {:id 1 :from [0.1 0 0] :to [0.3 0 0] :r 0.15} (first (cm/capsules horse))))
  (is (= [0.15 0.06 0.07 0.05 0.04 0.05 0.04 0.15 0.05 0.04 0.05 0.04] (mapv :r (cm/capsules horse))))
  (is (= [0.1 0.045 0.05 0.035 0.03 0.035 0.03 0.1 0.035 0.03 0.035 0.03] (mapv :r (cm/capsules dog))))
  (is (= {:id 8 :from [-0.3 0 0] :to [-0.1 0 0] :r 0.15} (nth (cm/capsules horse) 7)))
  (is (= :creature cm/obstacle-id)))

(deftest case02-one-capsule-along-x-z-up-to-game-xz
  (let [res (cm/ground-circle [{:id 1 :from [0 0 0] :to [1 0 0] :r 0.5}]
                               (clj->js [0 0 0 2 3 0.4])
                               (clj->js (into (vec (repeat 9 0)) I))
                               qv)]
    (is (vnear? [2.5 -3] (:at res)))
    (is (near? 1.0 (:r res)))
    (is (vnear? [0.6 -0.8] (:vel res)))))

(deftest case03-height-is-ignored
  (let [res (cm/ground-circle [{:id 1 :from [0 0 0] :to [0 0 1] :r 0.2}]
                               (clj->js [0 0 0 1 2 5])
                               (clj->js (into (vec (repeat 9 0)) I))
                               qv)]
    (is (vnear? [1 -2] (:at res)))
    (is (near? 0.2 (:r res))))
  (let [res2 (cm/ground-circle [{:id 1 :from [0 0 0] :to [1 0 0] :r 0.5}]
                                (clj->js [0 0 0 2 3 7])
                                (clj->js (into (vec (repeat 9 0)) I))
                                qv)]
    (is (vnear? [2.5 -3] (:at res2)))
    (is (near? 1.0 (:r res2)))
    (is (vnear? [0.6 -0.8] (:vel res2)))))

(deftest case04-rotation-mujoco-y-is-game-neg-z
  (let [res (cm/ground-circle [{:id 1 :from [0 0 0] :to [1 0 0] :r 0.1}]
                               (clj->js [0 0 0 0 0 0])
                               (clj->js (into (vec (repeat 9 0)) Rz90))
                               qv)]
    (is (vnear? [0 -0.5] (:at res)))
    (is (near? 0.6 (:r res)))))

(deftest case05-horse-standing
  (let [res (circle-of horse horse-standing-rows)]
    (is (vnear? [4.67 -8.0] (:at res)))
    (is (near? 0.62 (:r res)))
    (is (vnear? [0.6 -0.8] (:vel res)))))

(deftest case06-horse-standing-yaw-90
  (let [res (circle-of horse horse-yaw90-rows)]
    (is (vnear? [4.5 -8.17] (:at res)))
    (is (near? 0.62 (:r res)))))

(deftest case07-horse-lying
  (let [res (circle-of horse horse-lying-rows)]
    (is (vnear? [4.67 -8.225] (:at res)))
    (is (near? 0.7774779 (:r res)))))

(deftest case08-horse-curled
  (let [res (circle-of horse horse-curled-rows)]
    (is (vnear? [4.64 -7.88] (:at res)))
    (is (near? 0.6732456 (:r res)))))

(deftest case09-dog
  (let [standing (circle-of dog dog-standing-rows)
        lying (circle-of dog dog-lying-rows)]
    (is (vnear? [0.07 0] (:at standing)))
    (is (near? 0.39 (:r standing)))
    (is (vnear? [4.57 -8.115] (:at lying)))
    (is (near? 0.4577070 (:r lying)))))

(defn- mat3 [xm id i j] (aget xm (+ (* 9 id) (* 3 i) j)))
(defn- ground-point
  "A capsule endpoint `p` (body-local) of body `id`, as the test independently
   computes it: world = xpos + M·p, ground point = [Wx (- Wy)]."
  [xp xm id [px py pz]]
  (let [ox (aget xp (+ (* 3 id) 0)) oy (aget xp (+ (* 3 id) 1))
        wx (+ ox (* (mat3 xm id 0 0) px) (* (mat3 xm id 0 1) py) (* (mat3 xm id 0 2) pz))
        wy (+ oy (* (mat3 xm id 1 0) px) (* (mat3 xm id 1 1) py) (* (mat3 xm id 1 2) pz))]
    [wx (- wy)]))

(deftest case10-covering-and-edge-cases
  (doseq [rows [horse-lying-rows horse-curled-rows]]
    (let [[xp xm] (arrays rows)
          circ (cm/ground-circle (cm/capsules horse) xp xm qv)]
      (doseq [cap (cm/capsules horse)
              p [(:from cap) (:to cap)]]
        (let [E (ground-point xp xm (:id cap) p)]
          (is (<= (+ (dist E (:at circ)) (:r cap)) (+ (:r circ) 1e-9)) [cap p])))))
  (is (nil? (cm/ground-circle [] (clj->js [0 0 0]) (clj->js (vec (repeat 9 0))) qv)))
  (let [res (cm/ground-circle [{:id 1 :from [0 0 0] :to [1 0 0] :r 0.5}]
                               (clj->js [0 0 0 2 3 0.4])
                               (clj->js (into (vec (repeat 9 0)) I))
                               nil)]
    (is (vnear? [0 0] (:vel res)))))

;; ================================================================================
;; Extras and senses (pm.motion)
;; ================================================================================

(deftest case11-extra-others
  (is (= [{:id :foal :pos [1 2] :radius 0.5 :vel [0 0] :obstacle true}
          {:id :foal :pos [1 2] :radius 0.5 :vel [1 -1] :obstacle true}
          {:id :e :pos [1 2] :radius 1 :vel [0 0] :obstacle true}]
         (m/extra-others [{:id :foal :at [1 2] :r 0.5}
                           {:id :foal :at [1 2] :r 0.5 :vel [1 -1] :y 1.79}
                           {:id "x" :at [1 2] :r 1}
                           {:id :b :at [1 2 3] :r 1}
                           {:id :c :at [1 2] :r 0}
                           {:id :d :at [1 2] :r -1}
                           {:id :e :at [1 2] :r 1 :vel [1]}
                           nil])))
  (is (= [] (m/extra-others nil))))

(deftest case12-sense-dist
  (is (= 5 (m/sense-dist A (assoc A :id :b :pos [3 4]))))
  (is (= 4 (m/sense-dist A (ob [3 4] 1 [0 0]))))
  (is (= 0 (m/sense-dist A (ob [0.3 0.4] 1 [0 0])))))

(deftest case13-sight-to-the-edge
  (is (true? (m/sees? A (ob [8.5 0] 1 [0 0]))))
  (is (false? (m/sees? A (ob [9.5 0] 1 [0 0]))))
  (is (true? (m/sees? A (ob [-0.5 0] 1 [0 0]))))
  (is (false? (m/sees? A (ob [-3 0] 1 [0 0]))))
  (is (false? (m/sees? A (ob [3 5] 1 [0 0])))))

(deftest case14-hearing-to-the-edge-scaled-by-speed
  (is (true? (m/hears? A (ob [-2 0] 0.9 [0 0]))))
  (is (false? (m/hears? A (ob [-2 0] 0.7 [0 0]))))
  (is (true? (m/hears? A (ob [-3 0] 0.7 [2.4 0])))))

(deftest case15-perceive-all-with-an-extra
  (is (= {:foal {:on 0.1 :off 0 :noticed false :how :sight}}
         (:aware (first (m/perceive-all [A] 0.1 [{:id :foal :at [5 0] :r 0.5}])))))
  (is (= {} (:aware (first (m/perceive-all [A] 0.1 []))))))

(deftest case16-silent-still-behind-vs-kicking
  (let [C (assoc (mv {:id :c :route [[-5 0] [5 0]] :pause-s 1}) :pos [2.5 0])
        still (m/extra-others [{:id :creature :at [0 0] :r 0.6}])
        kicking (m/extra-others [{:id :creature :at [0 0] :r 0.6 :vel [2.4 0]}])
        after-still (nth (iterate #(m/perceive % still 0.05) C) 8)
        before-kicking (nth (iterate #(m/perceive % kicking 0.05) C) 7)
        after-kicking (nth (iterate #(m/perceive % kicking 0.05) C) 8)]
    (is (= {} (:aware after-still)))
    (is (false? (:glance after-still)))
    (is (false? (get-in before-kicking [:aware :creature :noticed])))
    (is (= {:noticed true :how :hear} (select-keys (get-in after-kicking [:aware :creature]) [:noticed :how])))
    (is (true? (:glance after-kicking)))))

;; ================================================================================
;; The planner (pm.motion)
;; ================================================================================

(deftest case17-a-noticed-obstacle-is-a-walkers-circle
  (let [w (first (m/plan-all [(assoc L :pos [2 0] :aware {:foal seen-1s})] [] cfg [{:id :foal :at [4 0] :r 0.5}]))]
    (is (vnear? [4.3424073 1.4449073] (:aim w)))
    (is (= :right (:side w)))
    (is (nil? (:goal w)))))

(deftest case18-not-noticed-is-ignored
  (let [w (first (m/plan-all [(assoc L :pos [2 0])] [] cfg [{:id :foal :at [4 0] :r 0.5}]))]
    (is (nil? (:aim w)))
    (is (nil? (:side w)))))

(deftest case19-the-goal-moves-out-of-a-noticed-obstacle
  (let [U (assoc (mv {:id :u :route [[4 -6] [3.5 -7.5]] :pause-s 8}) :aware {:foal seen-1s})
        res (first (m/plan-all [U] [] cfg [{:id :foal :at [4.5 -8.0] :r 1.0}]))]
    (is (vnear? [3.7701562 -6.6895314] (:goal res)))
    (is (nil? (:aim res))))
  (let [U2 (mv {:id :u :route [[4 -6] [3.5 -7.5]] :pause-s 8})
        res2 (first (m/plan-all [U2] [] cfg [{:id :foal :at [4.5 -8.0] :r 1.0}]))]
    (is (nil? (:goal res2)))))

(deftest case20-arrives-at-the-moved-goal
  (let [U (assoc (mv {:id :u :route [[4 -6] [3.5 -7.5]] :pause-s 8}) :aware {:foal seen-1s})
        fs (frames [U] [] 60 (constantly [{:id :foal :at [4.5 -8.0] :r 1.0}]))
        idx (first-index #(> (:pause (first %)) 0) fs)
        w (first (nth fs idx))]
    (is (= 7 idx))
    (is (= 8 (:pause w)))
    (is (< (dist (:pos w) [3.7701562 -6.6895314]) 0.3))
    (is (>= (dist (:pos w) [4.5 -8.0]) 1.3))))

(deftest case21-empty-extras-are-exactly-today
  (let [ws (m/walkers W) S (a/statics W)]
    (is (= (m/plan-all ws S cfg) (m/plan-all ws S cfg nil) (m/plan-all ws S cfg [])))
    (is (= (m/perceive-all ws 0.05) (m/perceive-all ws 0.05 [])))
    (is (= (m/replan ws ws S cfg 0.05 0.2) (m/replan ws ws S cfg 0.05 0.2 [])))
    (loop [w5 ws acc5 0 wnil ws accnil 0 wempty ws accempty 0 i 0]
      (if (>= i 400)
        nil
        (let [[w5' acc5'] (m/advance w5 S cfg 0.05 acc5)
              [wnil' accnil'] (m/advance wnil S cfg 0.05 accnil nil)
              [wempty' accempty'] (m/advance wempty S cfg 0.05 accempty [])]
          (is (= w5' wnil' wempty') i)
          (recur w5' acc5' wnil' accnil' wempty' accempty' (inc i)))))))

;; ================================================================================
;; Scenarios (dt 0.05)
;; ================================================================================

(deftest case22-detour-a-route-through-a-perceived-circle
  (let [A1 (mv {:id :a :route [[0 0] [10 0]] :pause-s 100})
        F1 {:id :creature :at [5 0] :r 0.8}
        ws (mapv first (frames [A1] [] 240 (constantly [F1])))
        noticed-idx (first-index #(true? (get-in % [:aware :creature :noticed])) ws)
        aim-idx (first-index #(some? (:aim %)) ws)
        x5-idx (first-index #(>= (first (:pos %)) 5) ws)
        final (peek ws)]
    (is (= 7 noticed-idx))
    (is (= :sight (get-in (nth ws noticed-idx) [:aware :creature :how])))
    (is (= 24 aim-idx))
    (is (= :right (:side (nth ws aim-idx))))
    (doseq [wk ws] (is (>= (gap wk F1) 0) wk))
    (is (> (second (:pos (nth ws x5-idx))) 0.5))
    (is (> (:pause final) 0))
    (is (< (dist (:pos final) [10 0]) 0.3))))

(deftest case23-mirrored-dog-sized-heading-neg-x
  (let [B1 (mv {:id :b :route [[10 0] [0 0]] :pause-s 100})
        F2 {:id :creature :at [5 0] :r 0.39}
        ws (mapv first (frames [B1] [] 240 (constantly [F2])))
        noticed-idx (first-index #(true? (get-in % [:aware :creature :noticed])) ws)
        aim-idx (first-index #(some? (:aim %)) ws)
        x5-idx (first-index #(<= (first (:pos %)) 5) ws)
        final (peek ws)]
    (is (= 7 noticed-idx))
    (is (= :sight (get-in (nth ws noticed-idx) [:aware :creature :how])))
    (is (= 29 aim-idx))
    (is (= :right (:side (nth ws aim-idx))))
    (doseq [wk ws] (is (>= (gap wk F2) 0) wk))
    (is (< (second (:pos (nth ws x5-idx))) -0.5))
    (is (> (:pause final) 0))
    (is (< (dist (:pos final) [0 0]) 0.3))))

(deftest case24-coming-from-behind-sight-only
  (let [C (assoc (mv {:id :c :route [[-5 0] [5 0]] :pause-s 1}) :pos [2.5 0])
        F3 {:id :creature :at [0 0] :r 0.6}
        ws (mapv first (frames [C] [] 400 (constantly [F3])))
        leg2-idx (first-index #(= 2 (:leg %)) ws)
        creature-idx (first-index #(contains? (:aware %) :creature) ws)
        noticed-idx (first-index #(true? (get-in % [:aware :creature :noticed])) ws)
        leg2-negx-idx (first-index #(and (= 2 (:leg %)) (<= (first (:pos %)) 0)) ws)
        leg3-idx (first-index #(= 3 (:leg %)) ws)]
    (doseq [wk ws]
      (when (= 1 (:leg wk))
        (is (not (contains? (:aware wk) :creature)) wk)
        (is (nil? (:aim wk)) wk)))
    (is (= 56 leg2-idx))
    (is (= 57 creature-idx))
    (is (= :sight (get-in (nth ws creature-idx) [:aware :creature :how])))
    (is (= 64 noticed-idx))
    (is (< (js/Math.abs (- (first (:pos (nth ws noticed-idx))) 4.24)) 1e-3))
    (doseq [wk (take noticed-idx ws)] (is (nil? (:aim wk)) wk))
    (doseq [wk ws] (is (>= (gap wk F3) 0) wk))
    (is (< (second (:pos (nth ws leg2-negx-idx))) -0.5))
    (is (< (dist (:pos (nth ws leg3-idx)) [-5 0]) 0.3))))

;; ================================================================================
;; Laps on the real estate routes (six walkers, 2400 frames = 120 s)
;; ================================================================================

(defn lap-run [extras-at]
  (let [S (a/statics W)
        ws0 (m/walkers W)
        idx-of (fn [id] (first (keep-indexed (fn [i wk] (when (= id (:id wk)) i)) ws0)))
        ua-i (idx-of :un-architecte) lc-i (idx-of :le-coureur)]
    (loop [ws ws0 acc 0 i 0
           min-foal js/Infinity min-pair js/Infinity min-static js/Infinity
           picks {} ua-hist [] lc-hist []]
      (if (>= i 2400)
        {:ws ws :min-foal min-foal :min-pair min-pair :min-static min-static
         :picks picks :idx-of idx-of :ua-hist ua-hist :lc-hist lc-hist}
        (let [extras (extras-at i)
              [ws' acc'] (m/advance ws S cfg 0.05 acc extras)
              fc (first extras)
              foal (if fc (reduce min min-foal (map #(gap % fc) ws')) min-foal)
              pair (reduce min js/Infinity
                           (for [i1 (range (count ws')) i2 (range (inc i1) (count ws'))]
                             (dist (:pos (nth ws' i1)) (:pos (nth ws' i2)))))
              static (reduce min js/Infinity
                             (for [wk ws' s S] (- (dist (:pos wk) (:at s)) (:r s) (:radius wk))))
              picks' (reduce (fn [p idx]
                               (let [before (nth ws idx) after (nth ws' idx)]
                                 (if (not= (:leg before) (:leg after))
                                   (update p idx (fnil conj []) (:to after))
                                   p)))
                             picks (range (count ws)))
              uaw (nth ws' ua-i) lcw (nth ws' lc-i)]
          (recur ws' acc' (inc i)
                 foal (min min-pair pair) (min min-static static)
                 picks'
                 (conj ua-hist {:how (get-in uaw [:aware :creature :how])
                                :noticed (get-in uaw [:aware :creature :noticed])
                                :goal (:goal uaw)})
                 (conj lc-hist {:how (get-in lcw [:aware :creature :how])
                                :noticed (get-in lcw [:aware :creature :noticed])})))))))

(defn first-notice [hist] (first-index #(true? (:noticed %)) hist))

(defn check-lap [res]
  (let [{:keys [ws min-foal min-pair min-static picks idx-of]} res]
    (is (>= min-foal 0))
    (is (>= min-pair 0.6))
    (is (>= min-static -1e-9))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (get picks (idx-of :un-architecte)))))
    (is (= [1 2 3 0 1 2 3 0] (take 8 (get picks (idx-of :le-coureur)))))
    (is (>= (:leg (nth ws (idx-of :un-architecte))) 10))
    (is (>= (:leg (nth ws (idx-of :le-coureur))) 40))))

(deftest case25-lap-stand
  (let [res (lap-run (constantly [STAND]))]
    (check-lap res)
    (is (= 7 (first-notice (:ua-hist res))))
    (is (= :sight (:how (nth (:ua-hist res) 7))))
    (is (= 7 (first-notice (:lc-hist res))))
    (is (= :sight (:how (nth (:lc-hist res) 7))))))

(deftest case26-lap-lie
  (let [res (lap-run (constantly [LIE]))]
    (check-lap res)
    (is (= 7 (first-notice (:ua-hist res))))
    (is (= :sight (:how (nth (:ua-hist res) 7))))
    (is (= 7 (first-notice (:lc-hist res))))
    (is (= :sight (:how (nth (:lc-hist res) 7))))))

(deftest case27-lap-spot
  (let [res (lap-run (constantly [SPOT]))]
    (check-lap res)
    (is (= 7 (first-notice (:ua-hist res))))
    (is (= :sight (:how (nth (:ua-hist res) 7))))
    (is (= 7 (first-notice (:lc-hist res))))
    (is (= :hear (:how (nth (:lc-hist res) 7))))
    (let [goals (keep :goal (:ua-hist res))]
      (is (seq goals))
      (doseq [g goals] (is (>= (dist g [4.5 -8.0]) (- 1.45 1e-9)) g)))))

(deftest case28-lap-kick
  (let [res (lap-run kick)]
    (check-lap res)))

;; ================================================================================
;; Without ?pony nothing changes
;; ================================================================================

(deftest case29-without-pony-nothing-changes
  (is (= [] (m/extra-others [])))
  (is (= {:flag "pony" :kind :horse :seed 1 :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}
         (:creature-spike W))))
