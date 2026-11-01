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
          :look {:shape :capsule :size [0.5 0.16 0.16] :color :pink}
          :at [-0.27 1.56 -15.7] :label :say :text "I'm hungry"}
         (dissoc (nth P0 0) :center :label-at)))
  (is (= {:key [:flora :jar] :mirror :flora :item :jar
          :look {:shape :cylinder :size [0.22 0.3 0.22] :color :glass}
          :at [1.4 1.81 -15.7] :label :sign :text "Worms: 15"}
         (dissoc (nth P0 1) :center :label-at)))
  (is (= {:key [:juniper :body] :mirror :juniper :item :body
          :look {:shape :box :size [0.6 1.6 2.0] :color :brown}
          :at [-5.0 2.5 -10.0] :label :say :text "Hay, please?"}
         (dissoc (nth P0 2) :center :label-at))))

(deftest t04-plan-positions
  (is (near? [-0.27 1.64 -15.7] (:center (nth P0 0))))
  (is (near? [-0.27 2.02 -15.7] (:label-at (nth P0 0))))
  (is (near? [1.4 1.96 -15.7] (:center (nth P0 1))))
  (is (near? [1.4 2.41 -15.7] (:label-at (nth P0 1))))
  (is (near? [-5.0 3.3 -10.0] (:center (nth P0 2))))
  (is (near? [-5.0 4.4 -10.0] (:label-at (nth P0 2)))))

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
    (is (= {:shape :box :size [1.0 0.5 0.5] :color :gold} (:look item)))))

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
    (is (= "scene: mirrors 3 · items 5 · labels 5/5" (p/plan-summary (p/scene-plan WP T0))))))

;; scene-plan: edges

(deftest t18-odd-items
  (let [PO (p/scene-plan O 0)
        BO (by-key PO)]
    (is (= [[:alpha :x] [:alpha :y] [:zed 0] [:zed :b] [:zed :c] [:zed :d]] (mapv :key PO)))
    (is (= #{:key :mirror :item :look :at :center} (set (keys (BO [:zed 0])))))
    (is (= 0 (:item (BO [:zed 0]))))
    (is (= {:shape :box :size [0.5 0.5 0.5] :color :stand-in} (:look (BO [:zed 0]))))
    (is (near? [0 0.25 0] (:center (BO [:zed 0]))))
    (is (= {:shape :box :size [0.5 0.5 0.5] :color :stand-in} (:look (BO [:zed :b]))))
    (is (= false (contains? (BO [:zed :b]) :label)))
    (is (near? [1 2.25 3] (:center (BO [:zed :b]))))
    (is (= {:shape :cylinder :size [2 4 2] :color :moss} (:look (BO [:zed :c]))))
    (is (near? [0 3 0] (:center (BO [:zed :c]))))
    (is (near? [0 5.3 0] (:label-at (BO [:zed :c]))))
    (is (= "hi · there" (:text (BO [:zed :c]))))
    (is (= :sign (:label (BO [:zed :d]))))
    (is (= "n 1" (:text (BO [:zed :d]))))
    (is (near? [0 0.8 0] (:label-at (BO [:zed :d]))))
    (is (= "k=2" (:text (BO [:alpha :x]))))
    (is (= "" (:text (BO [:alpha :y]))))))

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
  (is (= "scene: mirrors 2 · items 3 · labels 3/3" (p/plan-summary P0)))
  (is (= "scene: mirrors 2 · items 3 · labels 2/3" (p/plan-summary (p/scene-plan W1 (+ T0 1000)))))
  (is (= "scene: mirrors 2 · items 3 · labels 2/3"
         (p/plan-summary (p/scene-plan (w/chore-done W hay) (+ T0 1000)))))
  (is (= "scene: mirrors 2 · items 4 · labels 4/4" (p/plan-summary (p/scene-plan WJ T0))))
  (is (= "scene: mirrors 2 · items 6 · labels 3/4" (p/plan-summary (p/scene-plan O 0))))
  (is (= "scene: mirrors 0 · items 0 · labels 0/0" (p/plan-summary []))))
