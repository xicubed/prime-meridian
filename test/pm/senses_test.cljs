(ns pm.senses-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def cfg {:speed 1.2 :horizon-s 2})
(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))
(def PI js/Math.PI)

(def A (m/walker {:id :a :route [[0 0] [10 0]]}))       ; at [0 0], heading 0: faces +x, :pause 0
(defn o [id pos vel] (assoc (m/walker {:id id :route [[0 0] [1 0]]}) :pos pos :vel vel))
(def B5 (o :b [5 0] [0 0]))     ; ahead, 5 m
(def Bk (o :b [-1 0] [0 0]))    ; behind, 1 m (heard)
(def Bf (o :b [-5 0] [0 0]))    ; behind, 5 m (neither)
(defn run [w others dt n] (nth (iterate #(m/perceive % others dt) w) n))

(defn mv [e] (assoc (m/walker e) :pause 0 :to 1 :leg 1))
(def L (mv {:id :l :route [[0 0] [8 0]] :pause-s 1}))
(def S1 [{:at [4 0] :r 0.5}])
(def F (m/walker {:id :f :route [[0 0] [3 4] [6 0]] :pause-s 2}))
(def Fm (assoc F :pause 0 :to 1 :leg 1))
(def Ah (assoc (mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) :vel [1.2 0]))
(def Hh (assoc (mv {:id :h :route [[10 0] [0 0]] :pause-s 100}) :pos [4 0] :vel [-1.2 0]))
(def seen-1s {:on 1 :off 0 :noticed true :how :sight})

(defn frames [ws statics n]          ; vector of the walker vectors after each frame, dt 0.05
  (loop [ws ws acc 0 i 0 out []]
    (if (>= i n) out
      (let [[ws' acc'] (m/advance ws statics cfg 0.05 acc)]
        (recur ws' acc' (inc i) (conj out ws'))))))
(defn first-index [pred frs] (first (keep-indexed (fn [i x] (when (pred x) i)) frs)))

(defn first-crossing [ws0 frs]
  (some (fn [[prev cur]]
          (let [d0 (- (first (:pos (nth prev 0))) (first (:pos (nth prev 1))))
                d1 (- (first (:pos (nth cur 0))) (first (:pos (nth cur 1))))]
            (when (or (zero? d1) (neg? (* d0 d1)))
              [(second (:pos (nth cur 0))) (second (:pos (nth cur 1)))])))
        (map vector (cons ws0 frs) frs)))

(defn picks-of [ws0 frs idx]
  (loop [prev (nth ws0 idx) frs frs acc []]
    (if (empty? frs)
      acc
      (let [cur (nth (first frs) idx)
            acc' (if (not= (:leg prev) (:leg cur)) (conj acc (:to cur)) acc)]
        (recur cur (rest frs) acc')))))

;; Config and walker data

(deftest t01-defaults
  (is (= {:sight-m 8 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5} m/default-senses))
  (is (= m/default-senses (m/senses-config W)))
  (is (= m/default-senses (m/senses-config {}))))

(deftest t02-merging-and-validation
  (is (= {:sight-m 5 :sight-deg 360 :hear-m 1.2 :react-s 0.4 :forget-s 1.5}
         (m/senses m/default-senses {:sight-m 5 :hear-m -1 :react-s "x" :sight-deg 400 :extra 1})))
  (is (= m/default-senses (m/senses m/default-senses nil)))
  (is (= m/default-senses (m/senses m/default-senses "x")))
  (is (= 0 (:react-s (m/senses m/default-senses {:react-s 0}))))
  (is (= 1.2 (:hear-m (m/senses m/default-senses {:hear-m js/NaN})))))

(deftest t03-walker-fields
  (is (= m/default-senses (:senses A)))
  (is (= {} (:aware A)))
  (is (false? (:glance A)))
  (is (nil? (:facing A)))
  (is (nil? (:speed A)))
  (is (= 0.6 (:speed (m/walker {:id :s :route [[0 0]] :speed 0.6}))))
  (is (nil? (:speed (m/walker {:id :s :route [[0 0]] :speed 0}))))
  (is (nil? (:speed (m/walker {:id :s :route [[0 0]] :speed -1}))))
  (is (nil? (:speed (m/walker {:id :s :route [[0 0]] :speed "x"}))))
  (is (= {:sight-m 8 :sight-deg 110 :hear-m 2 :react-s 0.4 :forget-s 1.5}
         (:senses (m/walker {:id :s :route [[0 0]] :senses {:hear-m 2}})))))

(deftest t04-world-and-per-walker-overrides
  (is (= [{:sight-m 6 :sight-deg 110 :hear-m 2 :react-s 0.4 :forget-s 1.5}
          {:sight-m 6 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5}]
         (mapv :senses (m/walkers {:motion {:senses {:sight-m 6}}
                                    :walkers [{:id :a :route [[0 0]] :senses {:hear-m 2}}
                                              {:id :b :route [[0 0]]}]})))))

(deftest t05-estate-both-walkers
  (doseq [wk (m/walkers W)]
    (is (= m/default-senses (:senses wk)))
    (is (= {} (:aware wk)))
    (is (false? (:glance wk)))
    (is (nil? (:facing wk))))
  (is (= [nil nil nil 1.9 nil nil] (mapv :speed (m/walkers W))))
  (is (= [:un-automate-monde :le-testeur :un-architecte :le-coureur :le-critique :le-docteur]
         (mapv :id (m/walkers W)))))

;; Geometry

(deftest t06-forward
  (is (vnear? [1 0] (m/forward 0)))
  (is (vnear? [0 -1] (m/forward (/ PI 2))))
  (is (vnear? [0.6 0.8] (m/forward (m/yaw 3 4)))))

(deftest t07-facing
  (is (= 0 (m/facing A)))
  (is (= 2 (m/facing (assoc A :facing 2))))
  (is (= 1 (m/facing (assoc A :heading 1)))))

(deftest t08-sees-in-cone
  (doseq [p [[5 0] [5 3] [3 -4] [7.9 0]]]
    (is (true? (m/sees? A (o :b p [0 0]))))))

(deftest t09-not-seen
  (doseq [p [[-3 0] [3 5] [8.5 0] [-5 0]]]
    (is (false? (m/sees? A (o :b p [0 0]))))))

(deftest t10-sight-follows-facing
  (let [Az (assoc A :heading (/ PI 2))]
    (is (true? (m/sees? Az (o :b [0 -5] [0 0]))))
    (is (false? (m/sees? Az (o :b [0 5] [0 0]))))
    (is (false? (m/sees? Az (o :b [5 0] [0 0])))))
  (let [Ax (assoc A :facing PI)]
    (is (true? (m/sees? Ax (o :b [-5 0] [0 0]))))
    (is (false? (m/sees? Ax (o :b [5 0] [0 0]))))))

(deftest t11-sees-edge-cases
  (is (true? (m/sees? A (o :b [0 0] [0 0]))))
  (is (true? (m/sees? (assoc A :senses (assoc m/default-senses :sight-deg 360)) (o :b [-3 0] [0 0]))))
  (is (false? (m/sees? (assoc A :senses (assoc m/default-senses :sight-m 2)) (o :b [3 0] [0 0])))))

(deftest t12-hear-range
  (is (near? 1.2 (m/hear-range A (o :b [0 0] [0 0]))))
  (is (near? 1.2 (m/hear-range A (o :b [0 0] [0.6 0]))))
  (is (near? 2.4 (m/hear-range A (o :b [0 0] [2.4 0]))))
  (is (near? 3 (m/hear-range A (o :b [0 0] [1.8 2.4]))))
  (is (near? 1.2 (m/hear-range A (dissoc (o :b [0 0] [0 0]) :vel))))
  (is (near? 4 (m/hear-range (assoc A :senses (assoc m/default-senses :hear-m 2)) (o :b [0 0] [2.4 0])))))

(deftest t13-heard-behind
  (is (true? (m/hears? A Bk)))
  (is (false? (m/sees? A Bk)))
  (is (false? (m/hears? A (o :b [-1.5 0] [1.2 0]))))
  (is (true? (m/hears? A (o :b [-1.5 0] [2.4 0]))))
  (is (true? (m/hears? A (o :b [0.8 -0.8] [0 0]))))
  (is (false? (m/hears? A (o :b [0 1.3] [0 0])))))

;; Perception over time

(deftest t14-one-frame-seen
  (let [w' (m/perceive A [B5] 0.1)]
    (is (= {:b {:on 0.1 :off 0 :noticed false :how :sight}} (:aware w')))
    (is (false? (:glance w')))
    (is (vnear? (:pos A) (:pos w')))
    (is (near? (:heading A) (:heading w')))
    (is (= (:route A) (:route w')))))

(deftest t15-reaction-delay-dt-0-1
  (is (false? (get-in (run A [B5] 0.1 3) [:aware :b :noticed])))
  (is (true? (get-in (run A [B5] 0.1 4) [:aware :b :noticed])))
  (is (= [] (m/noticed (run A [B5] 0.1 3) [B5])))
  (is (= [B5] (m/noticed (run A [B5] 0.1 4) [B5]))))

(deftest t16-reaction-delay-other-steps
  (is (false? (get-in (run A [B5] 0.05 7) [:aware :b :noticed])))
  (is (true? (get-in (run A [B5] 0.05 8) [:aware :b :noticed])))
  (is (false? (get-in (run A [B5] 0.25 1) [:aware :b :noticed])))
  (is (true? (get-in (run A [B5] 0.25 2) [:aware :b :noticed]))))

(deftest t17-continuity
  (let [w3 (run A [B5] 0.1 3)
        w4 (m/perceive w3 [Bf] 0.1)]
    (is (= {} (:aware w4)))
    (is (= {:b {:on 0.1 :off 0 :noticed false :how :sight}} (:aware (m/perceive w4 [B5] 0.1))))))

(deftest t18-forgetting
  (let [wn (run A [B5] 0.1 4)
        g1 (m/perceive wn [Bf] 0.5)
        g2 (m/perceive g1 [Bf] 0.5)
        g3 (m/perceive g2 [Bf] 0.5)]
    (is (= {:b {:on 0 :off 0.5 :noticed true :how nil}} (:aware g1)))
    (is (near? 1.0 (get-in g2 [:aware :b :off])))
    (is (true? (get-in g2 [:aware :b :noticed])))
    (is (= [Bf] (m/noticed g2 [Bf])))
    (is (= {} (:aware g3)))
    (is (= [] (m/noticed g3 [Bf])))))

(deftest t19-seen-again-inside-forget-window
  (let [wn (run A [B5] 0.1 4)
        g1 (m/perceive wn [Bf] 0.5)
        g2 (m/perceive g1 [Bf] 0.5)]
    (is (= {:b {:on 0.1 :off 0 :noticed true :how :sight}} (:aware (m/perceive g2 [B5] 0.1))))))

(deftest t20-missing-other-not-perceived
  (let [wn (run A [B5] 0.1 4)]
    (is (= {:b {:on 0 :off 0.5 :noticed true :how nil}} (:aware (m/perceive wn [] 0.5))))))

(deftest t21-react-s-zero-and-dt-edge
  (is (true? (get-in (m/perceive (assoc A :senses (assoc m/default-senses :react-s 0)) [B5] 0.1)
                      [:aware :b :noticed])))
  (is (= {:b {:on 0 :off 0 :noticed false :how :sight}} (:aware (m/perceive A [B5] 0))))
  (is (= {:b {:on 0 :off 0 :noticed false :how :sight}} (:aware (m/perceive A [B5] -1)))))

(deftest t22-glance-hearing-only
  (let [w3 (run A [Bk] 0.1 3)
        w4 (run A [Bk] 0.1 4)]
    (is (= :hear (get-in w3 [:aware :b :how])))
    (is (false? (get-in w3 [:aware :b :noticed])))
    (is (false? (:glance w3)))
    (is (true? (get-in w4 [:aware :b :noticed])))
    (is (= :hear (get-in w4 [:aware :b :how])))
    (is (true? (:glance w4)))))

(deftest t23-louder-when-fast
  (let [wfast (run A [(o :b [-2 0] [2.4 0])] 0.1 4)
        wslow (run A [(o :b [-2 0] [0 0])] 0.1 4)]
    (is (true? (get-in wfast [:aware :b :noticed])))
    (is (= :hear (get-in wfast [:aware :b :how])))
    (is (true? (:glance wfast)))
    (is (= {} (:aware wslow)))
    (is (false? (:glance wslow)))))

(deftest t24-glance-clears
  (let [wg (run A [Bk] 0.1 4)]
    (let [r1 (m/perceive wg [(o :b [1 0] [0 0])] 0.1)]
      (is (= :sight (get-in r1 [:aware :b :how])))
      (is (true? (get-in r1 [:aware :b :noticed])))
      (is (false? (:glance r1))))
    (let [r2 (m/perceive wg [Bf] 0.1)]
      (is (nil? (get-in r2 [:aware :b :how])))
      (is (true? (get-in r2 [:aware :b :noticed])))
      (is (false? (:glance r2))))))

(deftest t25-two-others
  (let [Cc (o :c [-1 0] [0 0])
        w2 (run A [B5 Cc] 0.1 4)]
    (is (= #{:b :c} (set (keys (:aware w2)))))
    (is (= :sight (get-in w2 [:aware :b :how])))
    (is (= :hear (get-in w2 [:aware :c :how])))
    (is (true? (get-in w2 [:aware :b :noticed])))
    (is (true? (get-in w2 [:aware :c :noticed])))
    (is (true? (:glance w2)))
    (is (= [Cc B5] (m/noticed w2 [Cc B5])))))

(deftest t26-self-ignored
  (is (= {} (:aware (m/perceive A [A] 0.1)))))

(deftest t27-facing-from-motion
  (let [w' (m/perceive (assoc A :vel [0 1.2]) [] 0.1)]
    (is (near? -1.5707963 (:facing w')))
    (is (true? (m/sees? w' (o :b [0 5] [0 0]))))
    (is (false? (m/sees? w' (o :b [5 0] [0 0])))))
  (is (nil? (:facing (m/perceive (assoc A :vel [0.03 0]) [] 0.1))))
  (is (= :sight (get-in (m/perceive (assoc A :vel [0 1.2]) [(o :b [0 5] [0 0])] 0.1) [:aware :b :how]))))

(deftest t28-follow-and-perceive
  (let [f1 (m/follow Fm [1 1] false 0.1)]
    (is (near? -0.9272952 (:heading f1)))
    (is (nil? (:facing f1)))
    (is (vnear? [10 10] (:vel f1)))
    (is (near? -0.7853982 (:facing (m/perceive f1 [] 0.1))))))

(deftest t29-perceive-all-one-sided
  (let [Bb (m/walker {:id :b :route [[3 0] [10 0]]})
        [a' b'] (m/perceive-all [A Bb] 0.1)]
    (is (= {:b {:on 0.1 :off 0 :noticed false :how :sight}} (:aware a')))
    (is (= {} (:aware b')))
    (is (= [:a :b] (mapv :id [a' b'])))))

;; Planner

(deftest t30-plan-all-ignores-unnoticed
  (let [[pa ph] (m/plan-all [Ah Hh] [] cfg)]
    (is (nil? (:aim pa)))
    (is (nil? (:side pa)))
    (is (= 0 (:wait pa)))
    (is (nil? (:aim ph)))
    (is (nil? (:side ph)))
    (is (= 0 (:wait ph)))))

(deftest t31-plan-all-only-noticed-detours
  (let [[pa ph] (m/plan-all [(assoc Ah :aware {:h seen-1s}) Hh] [] cfg)]
    (is (vnear? [2.4081643 1.1306643] (:aim pa)))
    (is (= :right (:side pa)))
    (is (nil? (:aim ph)))))

(deftest t32-plan-all-both-noticed-head-on
  (let [[pa ph] (m/plan-all [(assoc Ah :aware {:h seen-1s}) (assoc Hh :aware {:a seen-1s})] [] cfg)]
    (is (vnear? [2.4081643 1.1306643] (:aim pa)))
    (is (= :right (:side pa)))
    (is (vnear? [1.5918357 -1.1306643] (:aim ph)))
    (is (= :right (:side ph)))))

(deftest t33-plan-all-statics-always-known
  (is (vnear? [4.3424073 1.4449073] (:aim (first (m/plan-all [(assoc L :pos [2 0])] S1 cfg)))))
  (is (vnear? [4.3424073 1.4449073]
              (:aim (first (m/plan-all [(assoc L :pos [2 0]) (o :z [2 3] [0 0])] S1 cfg))))))

(deftest t34-plan-own-speed
  (let [p (m/plan (assoc L :pos [2 0] :speed 0.5) [] S1 cfg)]
    (is (nil? (:aim p)))
    (is (nil? (:side p))))
  (is (vnear? [4.3424073 1.4449073] (:aim (m/plan (assoc L :pos [2 0]) [] S1 cfg)))))

(deftest t35-advance-own-speed
  (let [[ws acc] (m/advance [(assoc L :speed 0.6)] [] cfg 0.1 0)
        w1 (first ws)]
    (is (vnear? [0.06 0] (:pos w1)))
    (is (vnear? [0.6 0] (:vel w1)))
    (is (near? 0.1 acc)))
  (let [[ws _] (m/advance [L] [] cfg 0.1 0)]
    (is (vnear? [0.12 0] (:pos (first ws))))))

(deftest t36-replan-perceives-every-call
  (let [[ws acc] (m/replan [A B5] [A B5] [] cfg 0.1 0)]
    (is (near? 0.1 acc))
    (is (= {:b {:on 0.1 :off 0 :noticed false :how :sight}} (:aware (first ws))))
    (is (= {} (:aware (second ws))))
    (let [[ws2 acc2] (m/replan ws ws [] cfg 0.1 0.2)]
      (is (= 0 acc2))
      (is (near? 0.2 (get-in (first ws2) [:aware :b :on]))))))

;; Cone outline

(deftest t37-cone-points-example1
  (is (every? true? (map vnear? [[0 0] [5.6568542 5.6568542] [8 0] [5.6568542 -5.6568542] [0 0]]
                          (m/cone-points [0 0] 0 8 90 2)))))

(deftest t38-cone-points-example2-and-count
  (is (every? true? (map vnear? [[1 2] [3 2] [1 0] [-1 2] [1 2]]
                          (m/cone-points [1 2] (/ PI 2) 2 180 2))))
  (is (= 19 (count (m/cone-points [0 0] 0 8 110 16))))
  (is (every? true? (map vnear? [[0 0] [5.6568542 5.6568542] [5.6568542 -5.6568542] [0 0]]
                          (m/cone-points [0 0] 0 8 90 0)))))

;; Scenarios

(deftest t39-overtake
  (let [lead0 (assoc (mv {:id :lead :route [[0 0] [20 0]] :pause-s 100}) :pos [2 0] :speed 0.6)
        fol0  (mv {:id :fol :route [[0 0] [20 0]] :pause-s 100})
        frs (frames [lead0 fol0] [] 400)
        leads (mapv #(nth % 0) frs)
        fols  (mapv #(nth % 1) frs)
        final-lead (last leads)
        final-fol (last fols)
        pair-dist (fn [i] (dist (:pos (leads i)) (:pos (fols i))))
        min-dist (reduce min js/Infinity (map pair-dist (range (count frs))))
        cross-idx (first-index (fn [ws] (>= (first (:pos (nth ws 1))) (first (:pos (nth ws 0))))) frs)
        notice-idx (first-index (fn [ws] (true? (get-in (nth ws 0) [:aware :fol :noticed]))) frs)]
    (is (every? #(nil? (:aim %)) leads))
    (is (every? #(< (js/Math.abs (second (:pos %))) 1e-9) leads))
    (is (vnear? [14 0] (:pos final-lead)))
    (is (some #(some? (:aim %)) fols))
    (is (>= (reduce max (map #(second (:pos %)) fols)) 0.5))
    (is (some? cross-idx))
    (is (> (second (:pos (fols cross-idx))) 0.5))
    (is (> (:pause final-fol) 0))
    (is (= 1 (:to final-fol)))
    (is (<= (dist (:pos final-fol) [20 0]) 0.3))
    (is (>= min-dist 0.6))
    (is (= :sight (get-in (fols 7) [:aware :lead :how])))
    (is (some? notice-idx))
    (is (<= (pair-dist notice-idx) 1.2))
    (is (true? (:glance (leads notice-idx))))
    (is (every? (fn [i] (not-any? :noticed (vals (:aware (leads i))))) (range notice-idx)))))

(deftest t40-mirrored-overtake
  (let [fol0  (mv {:id :fol :route [[0 0] [-20 0]] :pause-s 100})
        lead0 (assoc (mv {:id :lead :route [[0 0] [-20 0]] :pause-s 100}) :pos [-2 0] :speed 0.6)
        frs (frames [fol0 lead0] [] 400)
        fols  (mapv #(nth % 0) frs)
        leads (mapv #(nth % 1) frs)
        final-lead (last leads)
        final-fol (last fols)
        pair-dist (fn [i] (dist (:pos (leads i)) (:pos (fols i))))
        min-dist (reduce min js/Infinity (map pair-dist (range (count frs))))
        cross-idx (first-index (fn [ws] (<= (first (:pos (nth ws 0))) (first (:pos (nth ws 1))))) frs)
        notice-idx (first-index (fn [ws] (true? (get-in (nth ws 1) [:aware :fol :noticed]))) frs)]
    (is (every? #(nil? (:aim %)) leads))
    (is (every? #(< (js/Math.abs (second (:pos %))) 1e-9) leads))
    (is (vnear? [-14 0] (:pos final-lead)))
    (is (<= (reduce min js/Infinity (map #(second (:pos %)) fols)) -0.5))
    (is (some? cross-idx))
    (is (< (second (:pos (fols cross-idx))) -0.5))
    (is (<= (dist (:pos final-fol) [-20 0]) 0.3))
    (is (> (:pause final-fol) 0))
    (is (>= min-dist 0.6))
    (is (= :sight (get-in (fols 7) [:aware :lead :how])))
    (is (some? notice-idx))
    (is (<= (pair-dist notice-idx) 1.2))
    (is (true? (:glance (leads notice-idx))))))

(deftest t41-head-on-both-notice
  (let [ws0 [(mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) (mv {:id :h :route [[10 0] [0 0]] :pause-s 100})]
        frs (frames ws0 [] 240)
        as (mapv #(nth % 0) frs)
        hs (mapv #(nth % 1) frs)
        pair-dist (fn [i] (dist (:pos (as i)) (:pos (hs i))))
        min-dist (reduce min js/Infinity (map pair-dist (range (count frs))))]
    (is (true? (get-in (as 23) [:aware :h :noticed])))
    (is (true? (get-in (hs 23) [:aware :a :noticed])))
    (is (= :sight (get-in (as 23) [:aware :h :how])))
    (is (= :sight (get-in (hs 23) [:aware :a :how])))
    (is (near? 7.12 (pair-dist 23)))
    (is (not (get-in (as 22) [:aware :h :noticed])))
    (is (not (get-in (hs 22) [:aware :a :noticed])))
    (let [[za zh] (first-crossing ws0 frs)]
      (is (> za 0))
      (is (> 0 zh)))
    (is (>= min-dist 0.6))
    (is (> (:pause (last as)) 0))
    (is (= 1 (:to (last as))))
    (is (<= (dist (:pos (last as)) [10 0]) 0.3))
    (is (> (:pause (last hs)) 0))
    (is (= 1 (:to (last hs))))
    (is (<= (dist (:pos (last hs)) [0 0]) 0.3))))

(deftest t42-estate-scenario
  (let [ws0 (m/walkers W)
        statics (a/statics W)
        frs (frames ws0 statics 2400)
        w1s (mapv #(nth % 0) frs)
        w2s (mapv #(nth % 1) frs)
        id1 (:id (first ws0))
        id2 (:id (second ws0))
        pair-dist (fn [i] (dist (:pos (w1s i)) (:pos (w2s i))))
        min-dist (reduce min js/Infinity (map pair-dist (range (count frs))))
        static-gap (fn [wk] (reduce min js/Infinity
                                     (map (fn [c] (- (dist (:pos wk) (:at c)) (:r c) (:radius wk))) statics)))
        min-static (reduce min js/Infinity (concat (map static-gap w1s) (map static-gap w2s)))
        picks1 (picks-of ws0 frs 0)
        picks2 (picks-of ws0 frs 1)]
    (is (= :sight (get-in (w1s 7) [:aware id2 :how])))
    (is (= :sight (get-in (w2s 7) [:aware id1 :how])))
    (is (= [1 2 1 0 1 2 1 0] (take 8 picks1)))
    (is (= [1 2 1 0 1 2 1 0] (take 8 picks2)))
    (is (>= (:leg (last w1s)) 12))
    (is (>= (:leg (last w2s)) 12))
    (is (>= min-dist 0.6))
    (is (>= min-static 0))
    (is (some (fn [ws] (some :glance ws)) frs))))

(deftest t43-unchanged-surfaces
  (is (= "motion: kinematic" (m/hud-text {:mode :kinematic})))
  (is (= "motion: kinematic · 1 detour" (m/hud-text {:mode :kinematic :detours 1})))
  (is (= 1.2 (:speed (m/config W))))
  (is (= ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"] (:ground (m/config W))))
  (is (= {:radius 0.3 :horizon-s 2} (m/avoid-config W))))
