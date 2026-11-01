(ns pm.plan-test
  (:require [cljs.test :refer [deftest is]]
            [pm.world :as w]
            [pm.plan :as p]))

(def T0 1800000000000)
(def W  (w/load-estate w/estate-edn))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def fed (ev :flora :feed T0 :key))
(def hay (ev :juniper :hay T0 :key))
(def W1 (w/chore-done W fed))
(defn stock [n] (assoc-in W [:mirrors :flora :state :stock] n))
(defn by-key [plan] (into {} (map (juxt :key identity)) plan))
(defn texts [world now] (p/label-texts (p/scene-plan world now)))
(defn near? [a b] (and (= (count a) (count b))
                       (every? #(< (js/Math.abs %) 1e-9) (map - a b))))
(def bales-item {:id :bales :at [9.0 1.45 11.0] :label :sign :idle "Bales: {bales}"
                 :look {:shape :box :size [1.0 0.5 0.5] :color :gold}})
(def WJ (update-in W [:mirrors :juniper :placed] conj bales-item))
(def odd-edn
  (pr-str {:mirrors [{:id :zed :state {:n 1}
                      :rules [{:when [:>= :n 1] :say "hi"}
                              {:when [:>= :n 1] :say "there"}
                              {:when [:>= :n 1] :sign "n {n}"}]
                      :placed [{:at [0 0 0]}
                               {:id :a :at [1 2] :label :say}
                               {:id :b :at [1 2 3] :label :shout
                                :look {:shape :cone :size [1 -1 1] :color "red"}}
                               {:id :c :at [0 1 0] :label :say
                                :look {:shape :cylinder :size [2 4 2] :color :moss :extra 1}}
                               {:id :d :at [0 0 0] :label :sign}]}
                     {:id :alpha :state {:k 2} :rules []
                      :placed [{:id :x :at [0 0 0] :label :say :idle "k={k}"}
                               {:id :y :at [0 0 0] :label :sign :idle 7}]}
                     {:id :empty}]}))
(def O (w/load-estate odd-edn))
(def P0 (p/scene-plan W T0))

(defn part-near? [p q]
  (and (= (dissoc p :size :at) (dissoc q :size :at))
       (near? (:size p) (:size q)) (near? (:at p) (:at q))))
(defn span [parts]   ; x extent in the item frame, ignoring :rot
  (- (apply max (map (fn [{[dx] :at [sx] :size}] (+ dx (/ sx 2))) parts))
     (apply min (map (fn [{[dx] :at [sx] :size}] (- dx (/ sx 2))) parts))))
(defn part-of [plan k i] (get-in (by-key plan) [k :parts i]))
(def toy-edn
  (pr-str {:mirrors [{:id :toy :state {}
                      :placed [{:id :a :at [1 0 2] :scale 2 :yaw 45 :label :say :idle "hello"
                                :look {:parts [{:shape :box :size [1 1 1] :at [0 0.5 0] :color :pink}
                                               {:shape :sphere :size [0.5 0.5 0.5] :at [0.5 1.25 0]
                                                :rot [0 90 0] :color :eye}]}}
                               {:id :b :at [0 0 0] :scale 0 :yaw "north"
                                :look {:parts [{:shape :star :size [1 1 1]}
                                               {:shape :box :size [1 0 1]}
                                               {:shape :box :size [1 1]}
                                               {:shape :box :size [1 1 1] :at [0 1]}
                                               {:shape :box :size [1 1 1] :rot "up"}
                                               "box"
                                               {:shape :cone :size [0.2 0.6 0.2]}]}}
                               {:id :c :at [0 0 0] :scale 3 :look {:parts []}}
                               {:id :d :at [0 0 0] :scale -1 :look {:parts :none :shape :sphere}}
                               {:id :e :at [0 0 0]
                                :look {:shape :sphere :size [1 2 3] :color :gill :rot [1 2 3] :at [9 9 9]}}]}]}))
(def Q  (w/load-estate toy-edn))
(def PQ (p/scene-plan Q 0))

;; Data

(deftest t01-estate-placed
  (is (= 2 (count (get-in W [:mirrors :flora :placed]))))
  (is (= 1 (count (get-in W [:mirrors :juniper :placed]))))
  (is (= :canal (get-in W [:mirrors :flora :home])))
  (is (= :barn (get-in W [:mirrors :juniper :home])))
  (is (= 0.3 p/label-lift))
  (is (= "Worms: 15" (w/fill "Worms: {stock}" {:stock 15})))
  (is (= "x {nope}" (w/fill "x {nope}" {}))))

;; label-text

(deftest t02-label-text
  (let [ms [{:say "a"} {:sign "b"} {:say "c" :sign "d"}]]
    (is (= "a · c" (p/label-text ms :say nil {})))
    (is (= "b · d" (p/label-text ms :sign nil {})))
    (is (= "x" (p/label-text [{:sign "x"}] :sign "idle" {})))
    (is (= "Worms: 15" (p/label-text [] :sign "Worms: {stock}" {:stock 15})))
    (is (= "Worms: 15" (p/label-text [{:say "hi"}] :sign "Worms: {stock}" {:stock 15})))
    (is (= "" (p/label-text [] :say nil {})))
    (is (= "" (p/label-text [] :sign 7 {})))
    (is (= "Worms: {stock}" (p/label-text [] :sign "Worms: {stock}" {})))))

;; scene-plan: the real estate

(deftest t03-plan-order-and-shape
  (is (= [[:flora :body] [:flora :jar] [:juniper :body]] (mapv :key P0)))
  (is (= {:key [:flora :body] :mirror :flora :item :body
          :at [-0.9 1.6 -15.7] :yaw 0 :label :say :text "I'm hungry"}
         (dissoc (nth P0 0) :center :label-at :parts)))
  (is (= {:key [:flora :jar] :mirror :flora :item :jar
          :at [1.4 1.81 -15.7] :yaw 0 :label :sign :text "Worms: 15"}
         (dissoc (nth P0 1) :center :label-at :parts)))
  (is (= {:key [:juniper :body] :mirror :juniper :item :body
          :at [-5.0 2.5 -10.0] :yaw 90 :label :say :text "Hay, please?"}
         (dissoc (nth P0 2) :center :label-at :parts)))
  (is (= false (contains? (nth P0 0) :look))))

(deftest t04-plan-positions
  (is (near? [-0.9 1.744 -15.7] (:center (nth P0 0))))
  (is (near? [-0.9 2.188 -15.7] (:label-at (nth P0 0))))
  (is (near? [1.4 2.058 -15.7] (:center (nth P0 1))))
  (is (near? [1.4 2.606 -15.7] (:label-at (nth P0 1))))
  (is (near? [-5.0 3.595 -10.0] (:center (nth P0 2))))
  (is (near? [-5.0 4.99 -10.0] (:label-at (nth P0 2)))))

(deftest t05-positions-dont-follow-state
  (is (= (map (juxt :key :at :center :label-at) P0)
         (map (juxt :key :at :center :label-at) (p/scene-plan W1 (+ T0 1000))))))

;; Flora: hungry/fed, full/low

(deftest t06-flora-hungry-full
  (is (= {[:flora :body] "I'm hungry" [:flora :jar] "Worms: 15" [:juniper :body] "Hay, please?"}
         (texts W T0))))

(deftest t07-flora-fed
  (is (= {[:flora :body] "" [:flora :jar] "Worms: 14" [:juniper :body] "Hay, please?"}
         (texts W1 (+ T0 1000)))))

(deftest t08-flora-hungry-again
  (is (= "I'm hungry" (get (texts W1 (+ T0 172800000)) [:flora :body])))
  (is (= "" (get (texts W1 (+ T0 172799999)) [:flora :body])))
  (is (= "Worms: 14" (get (texts W1 (+ T0 172800000)) [:flora :jar])))
  (is (= "Worms: 14" (get (texts W1 (+ T0 172799999)) [:flora :jar]))))

(deftest t09-flora-low
  (is (= "Worms left: 2" (get (texts (stock 2) T0) [:flora :jar])))
  (is (= "I'm hungry" (get (texts (stock 2) T0) [:flora :body])))
  (is (= "Worms: 3" (get (texts (stock 3) T0) [:flora :jar])))
  (is (= "Worms left: 0" (get (texts (stock 0) T0) [:flora :jar]))))

(deftest t10-flora-snap
  (let [S3 (stock 3)]
    (is (= "I'm hungry" (get (texts S3 T0) [:flora :body])))
    (is (= "Worms: 3" (get (texts S3 T0) [:flora :jar])))
    (is (= {[:flora :body] "" [:flora :jar] "Worms left: 2" [:juniper :body] "Hay, please?"}
           (texts (w/chore-done S3 fed) (+ T0 1))))
    (is (= "Worms left: 0" (get (texts (w/chore-done (stock 1) fed) (+ T0 1)) [:flora :jar])))))

(deftest t11-flora-no-stock-field
  (let [N (update-in W [:mirrors :flora :state] dissoc :stock)]
    (is (= "Worms: {stock}" (get (texts N T0) [:flora :jar])))))

(deftest t12-key-f-end-to-end
  (let [K (w/chore-done W (w/key-event W "F" T0))]
    (is (= {[:flora :body] "" [:flora :jar] "Worms: 14" [:juniper :body] "Hay, please?"}
           (texts K (+ T0 5000))))
    (let [K2 (w/chore-done K (w/key-event K "f" (+ T0 5000)))]
      (is (= (texts K (+ T0 5000)) (texts K2 (+ T0 6000)))))))

;; Juniper: the second mirror, same code

(deftest t13-juniper-hungry-fed
  (is (= "Hay, please?" (get (texts W T0) [:juniper :body])))
  (is (= {[:flora :body] "I'm hungry" [:flora :jar] "Worms: 15" [:juniper :body] ""}
         (texts (w/chore-done W hay) (+ T0 1000)))))

(deftest t14-juniper-sign-not-on-bubble
  (let [B3 (assoc-in W [:mirrors :juniper :state :bales] 3)]
    (is (= "Hay, please?" (get (texts B3 T0) [:juniper :body])))
    (is (= 3 (count (p/scene-plan B3 T0))))))

(deftest t15-juniper-store-item
  (is (= [[:flora :body] [:flora :jar] [:juniper :body] [:juniper :bales]]
         (mapv :key (p/scene-plan WJ T0))))
  (is (= "Bales: 6" (get (texts WJ T0) [:juniper :bales])))
  (let [WJ3 (assoc-in WJ [:mirrors :juniper :state :bales] 3)]
    (is (= "Bales left: 3" (get (texts WJ3 T0) [:juniper :bales]))))
  (let [item (get (by-key (p/scene-plan WJ T0)) [:juniper :bales])]
    (is (near? [9.0 1.7 11.0] (:center item)))
    (is (near? [9.0 2.25 11.0] (:label-at item)))
    (is (= [{:shape :box :size [1.0 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :gold}] (:parts item)))
    (is (= 0 (:yaw item)))))

(deftest t16-juniper-hay-cycle
  (let [WJ4 (reduce w/chore-done WJ (for [k (range 4)] (ev :juniper :hay (+ T0 (* k 43200000)) :key)))]
    (is (= 2 (get-in WJ4 [:mirrors :juniper :state :bales])))
    (is (= 1800129600000 (get-in WJ4 [:mirrors :juniper :state :last-hayed])))
    (let [item1 (get (by-key (p/scene-plan WJ4 (+ T0 129600001))) [:juniper :bales])
          body1 (get (by-key (p/scene-plan WJ4 (+ T0 129600001))) [:juniper :body])]
      (is (= "" (:text body1)))
      (is (= "Bales left: 2" (:text item1))))
    (let [item2 (get (by-key (p/scene-plan WJ4 (+ T0 172800000))) [:juniper :bales])
          body2 (get (by-key (p/scene-plan WJ4 (+ T0 172800000))) [:juniper :body])]
      (is (= "Hay, please?" (:text body2)))
      (is (= "Bales left: 2" (:text item2))))
    (let [after (texts (w/chore-done WJ hay) (+ T0 1000))]
      (is (= "Bales: 5" (get after [:juniper :bales])))
      (is (= "" (get after [:juniper :body]))))))

(deftest t17-renamed-copy
  (let [WP (assoc-in W [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet))]
    (is (= [[:flora :body] [:flora :jar] [:juniper :body] [:pet :body] [:pet :jar]]
           (mapv :key (p/scene-plan WP T0))))
    (is (= "I'm hungry" (get (texts WP T0) [:pet :body])))
    (is (= "Worms: 15" (get (texts WP T0) [:pet :jar])))
    (is (= "scene: mirrors 3 · items 5 · parts 47 · labels 5/5" (p/plan-summary (p/scene-plan WP T0))))))

;; scene-plan: edges

(deftest t18-odd-items
  (let [PO (p/scene-plan O 0)
        BO (by-key PO)]
    (is (= [[:alpha :x] [:alpha :y] [:zed 0] [:zed :b] [:zed :c] [:zed :d]] (mapv :key PO)))
    (is (= #{:key :mirror :item :at :yaw :parts :center} (set (keys (BO [:zed 0])))))
    (is (= 0 (:item (BO [:zed 0]))))
    (is (= [p/default-part] (:parts (BO [:zed 0]))))
    (is (near? [0 0.25 0] (:center (BO [:zed 0]))))
    (is (= [{:shape :cone :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in}]
           (:parts (BO [:zed :b]))))
    (is (= false (contains? (BO [:zed :b]) :label)))
    (is (near? [1 2.25 3] (:center (BO [:zed :b]))))
    (is (= [{:shape :cylinder :size [2 4 2] :at [0 2 0] :rot [0 0 0] :color :moss}]
           (:parts (BO [:zed :c]))))
    (is (near? [0 3 0] (:center (BO [:zed :c]))))
    (is (near? [0 5.3 0] (:label-at (BO [:zed :c]))))
    (is (= "hi · there" (:text (BO [:zed :c]))))
    (is (= :sign (:label (BO [:zed :d]))))
    (is (= "n 1" (:text (BO [:zed :d]))))
    (is (near? [0 0.8 0] (:label-at (BO [:zed :d]))))
    (is (= "k=2" (:text (BO [:alpha :x]))))
    (is (= "" (:text (BO [:alpha :y]))))
    (is (every? #(= 0 (:yaw %)) PO))))

(deftest t19-empty-worlds
  (is (= [] (p/scene-plan {} T0)))
  (is (= [] (p/scene-plan {:mirrors {}} T0)))
  (is (= [] (p/scene-plan (w/load-estate "{:mirrors [{:id :rock}]}") T0))))

;; label-texts / label-changes

(deftest t20-label-texts
  (is (= (texts W T0) (p/label-texts P0)))
  (is (= #{[:alpha :x] [:alpha :y] [:zed :c] [:zed :d]}
         (set (keys (p/label-texts (p/scene-plan O 0))))))
  (is (= {} (p/label-texts []))))

(deftest t21-label-changes
  (let [T (p/label-texts P0)]
    (is (= T (p/label-changes {} P0)))
    (is (= T (p/label-changes nil P0)))
    (is (= {} (p/label-changes T P0)))
    (is (= {} (p/label-changes (assoc T [:gone 0] "x") P0)))
    (let [K (w/chore-done W (w/key-event W "F" T0))
          PK (p/scene-plan K (+ T0 5000))]
      (is (= {[:flora :body] "" [:flora :jar] "Worms: 14"} (p/label-changes T PK))))
    (is (= {[:flora :body] "" [:flora :jar] "Worms left: 2"}
           (p/label-changes (texts (stock 3) T0) (p/scene-plan (w/chore-done (stock 3) fed) (+ T0 1)))))
    (is (= {[:juniper :body] ""}
           (p/label-changes T (p/scene-plan (w/chore-done W hay) (+ T0 1000)))))))

;; plan-summary

(deftest t22-summary
  (is (= "scene: mirrors 2 · items 3 · parts 30 · labels 3/3" (p/plan-summary P0)))
  (is (= "scene: mirrors 2 · items 3 · parts 30 · labels 2/3" (p/plan-summary (p/scene-plan W1 (+ T0 1000)))))
  (is (= "scene: mirrors 2 · items 3 · parts 30 · labels 2/3"
         (p/plan-summary (p/scene-plan (w/chore-done W hay) (+ T0 1000)))))
  (is (= "scene: mirrors 2 · items 4 · parts 31 · labels 4/4" (p/plan-summary (p/scene-plan WJ T0))))
  (is (= "scene: mirrors 2 · items 6 · parts 6 · labels 3/4" (p/plan-summary (p/scene-plan O 0))))
  (is (= "scene: mirrors 0 · items 0 · parts 0 · labels 0/0" (p/plan-summary []))))

;; v1: looks become parts

(deftest t23-constants
  (is (= #{:box :cylinder :capsule :sphere :cone} p/shapes))
  (is (= {:shape :box :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in} p/default-part))
  (is (= 0 (p/radians 0)))
  (is (near? [(p/radians 90) (p/radians 180) (p/radians -90)]
             [1.5707963267948966 js/Math.PI -1.5707963267948966])))

(deftest t24-single-shape-back-compat
  (is (= [p/default-part] (p/look-parts nil 1)))
  (is (= [p/default-part] (p/look-parts {} 1)))
  (is (= [{:shape :box :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :pink}]
         (p/look-parts {:shape :box :size [1 2 3] :color :pink} 1)))
  (is (= [p/default-part] (p/look-parts {:shape :star :size [1 2] :color "red"} 1)))
  (is (= [{:shape :sphere :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :stand-in}]
         (p/look-parts {:shape :sphere :size [1 2 3] :at [9 9 9] :rot [1 2 3]} 1)))
  (is (= 0.3 (p/parts-top (p/look-parts {:shape :cylinder :size [0.22 0.3 0.22] :color :glass} 1)))))

(deftest t25-parts-normalized
  (is (= [{:shape :cone :size [1 2 1] :at [0 1 0] :rot [0 0 0] :color :stand-in}
          {:shape :box :size [1 1 1] :at [1 2 3] :rot [10 20 30] :color :eye}]
         (p/look-parts {:parts [{:shape :cone :size [1 2 1]}
                                 {:shape :box :size [1 1 1] :at [1 2 3] :rot [10 20 30] :color :eye :name "x"}]} 1)))
  (is (= [{:shape :sphere :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]
         (p/look-parts {:parts (list {:shape :sphere :size [1 1 1]})} 1))))

(deftest t26-invalid-parts-dropped
  (is (= [{:shape :cone :size [0.2 0.6 0.2] :at [0 0.3 0] :rot [0 0 0] :color :stand-in}]
         (:parts (get (by-key PQ) [:toy :b]))))
  (is (= [{:shape :box :size [1.5 1.5 1.5] :at [0 0.75 0] :rot [0 0 0] :color :stand-in}]
         (:parts (get (by-key PQ) [:toy :c]))))
  (is (= [p/default-part] (:parts (get (by-key PQ) [:toy :d]))))
  (is (= [{:shape :box :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]
         (p/look-parts {:parts [{:shape :box}]} 2))))

(deftest t27-scale
  (is (= [{:shape :box :size [2 4 6] :at [0 2 0] :rot [0 0 0] :color :pink}]
         (p/look-parts {:shape :box :size [1 2 3] :color :pink} 2)))
  (is (= [{:shape :cone :size [0.5 0.5 0.5] :at [0.5 1 1.5] :rot [10 20 30] :color :stand-in}]
         (p/look-parts {:parts [{:shape :cone :size [1 1 1] :at [1 2 3] :rot [10 20 30]}]} 0.5)))
  (doseq [s [0 -1 "2" nil]]
    (is (= [{:shape :box :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]
           (p/look-parts {:shape :box :size [1 1 1]} s)))))

(deftest t28-parts-top-and-label
  (is (= 3.5 (p/parts-top [{:size [1 2 1] :at [0 1 0]} {:size [1 1 1] :at [0 3 0]}])))
  (is (= 0 (p/parts-top [])))
  (is (= -1.5 (p/parts-top [{:size [1 1 1] :at [0 -2 0]}])))
  (let [A (get (by-key PQ) [:toy :a])]
    (is (= [{:shape :box :size [2 2 2] :at [0 1 0] :rot [0 0 0] :color :pink}
            {:shape :sphere :size [1 1 1] :at [1 2.5 0] :rot [0 90 0] :color :eye}]
           (:parts A)))
    (is (= 3 (p/parts-top (:parts A))))
    (is (= [1 1.5 2] (:center A)))
    (is (near? [1 3.3 2] (:label-at A)))
    (is (= "hello" (:text A)))))

(deftest t29-yaw-passed-through
  (is (= 45 (:yaw (get (by-key PQ) [:toy :a]))))
  (is (= 0 (:yaw (get (by-key PQ) [:toy :b]))))
  (is (= 0 (:yaw (get (by-key PQ) [:toy :e]))))
  (is (= [0 0 90] (mapv :yaw P0)))
  (is (= [1 1.5 2]
         (:center (get (by-key (p/scene-plan (assoc-in Q [:mirrors :toy :placed 0 :yaw] -30) 0)) [:toy :a])))))

(deftest t30-toy-plan
  (is (= [[:toy :a] [:toy :b] [:toy :c] [:toy :d] [:toy :e]] (mapv :key PQ)))
  (is (= [{:shape :sphere :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :gill}]
         (:parts (get (by-key PQ) [:toy :e]))))
  (is (near? [0 0.3 0] (:center (get (by-key PQ) [:toy :b]))))
  (is (near? [0 0.75 0] (:center (get (by-key PQ) [:toy :c]))))
  (is (near? [0 0.25 0] (:center (get (by-key PQ) [:toy :d]))))
  (is (near? [0 1 0] (:center (get (by-key PQ) [:toy :e]))))
  (is (= #{:key :mirror :item :at :yaw :parts :center} (set (keys (get (by-key PQ) [:toy :b])))))
  (is (= "scene: mirrors 1 · items 5 · parts 6 · labels 1/1" (p/plan-summary PQ))))

(deftest t31-flora-look
  (let [FP (:parts (nth P0 0))]
    (is (= 15 (count FP)))
    (is (= {:pink 7 :gill 6 :eye 2} (frequencies (map :color FP))))
    (is (= {:capsule 1 :sphere 3 :box 1 :cone 6 :cylinder 4} (frequencies (map :shape FP))))
    (is (part-near? (nth FP 0) {:shape :capsule :size [0.72 0.192 0.24] :at [0.12 0.096 0] :rot [0 0 0] :color :pink}))
    (is (part-near? (nth FP 1) {:shape :sphere :size [0.3 0.18 0.336] :at [0.6 0.102 0] :rot [0 0 0] :color :pink}))
    (is (part-near? (nth FP 2) {:shape :box :size [0.54 0.144 0.036] :at [-0.51 0.096 0] :rot [0 0 0] :color :pink}))
    (is (part-near? (nth FP 3) {:shape :cone :size [0.036 0.144 0.036] :at [0.492 0.216 0.132] :rot [30 0 35] :color :gill}))
    (is (part-near? (nth FP 8) {:shape :cone :size [0.036 0.144 0.036] :at [0.54 0.156 -0.174] :rot [-80 0 15] :color :gill}))
    (is (near? [(span FP)] [1.53]))
    (is (near? [(p/parts-top FP)] [0.288]))
    (is (near? [(+ 1.6 (p/parts-top [(nth FP 0)]))] [1.792]))
    (is (every? (fn [{[_ dy] :at [_ sy] :size}] (> (+ 1.42 dy (/ sy 2)) 1.55))
                (filter #(= :gill (:color %)) FP)))))

(deftest t32-jar-look
  (let [JP (:parts (nth P0 1))]
    (is (= 2 (count JP)))
    (is (= [:glass :tin] (mapv :color JP)))
    (is (part-near? (nth JP 0) {:shape :cylinder :size [0.32 0.44 0.32] :at [0 0.22 0] :rot [0 0 0] :color :glass}))
    (is (part-near? (nth JP 1) {:shape :cylinder :size [0.352 0.056 0.352] :at [0 0.468 0] :rot [0 0 0] :color :tin}))
    (is (near? [(p/parts-top JP)] [0.496]))))

(deftest t33-juniper-look
  (let [HP (:parts (nth P0 2))]
    (is (= 13 (count HP)))
    (is (= {:brown 9 :eye 2 :mane 2} (frequencies (map :color HP))))
    (is (= {:capsule 1 :box 3 :cone 2 :sphere 2 :cylinder 5} (frequencies (map :shape HP))))
    (is (= {:shape :capsule :size [1.3 0.62 0.6] :at [0 1.2 0] :rot [0 0 0] :color :brown} (nth HP 0)))
    (is (= {:shape :box :size [0.55 0.26 0.24] :at [1.1 1.9 0] :rot [0 0 -35] :color :brown} (nth HP 2)))
    (is (= {:shape :cylinder :size [0.1 0.7 0.1] :at [-0.82 1.1 0] :rot [0 0 -20] :color :mane} (nth HP 8)))
    (is (near? [(p/parts-top HP)] [2.19]))
    (is (near? [(span HP)] [2.245]))
    (doseq [i (range 9 13)]
      (let [leg (nth HP i)]
        (is (= [0.14 0.95 0.14] (:size leg)))
        (is (= 0.475 (second (:at leg))))))))

(deftest t34-same-code-both-looks
  (let [WS (assoc-in W [:mirrors :flora :placed 0 :look] (get-in W [:mirrors :juniper :placed 0 :look]))
        FS (:parts (nth (p/scene-plan WS T0) 0))]
    (is (= 13 (count FS)))
    (is (part-near? (nth FS 0) {:shape :capsule :size [7.8 3.72 3.6] :at [0 7.2 0] :rot [0 0 0] :color :brown}))
    (is (near? [(p/parts-top FS)] [13.14]))
    (is (= (nth P0 2) (nth (p/scene-plan WS T0) 2)))))
