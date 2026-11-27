(ns pm.blueprint-seat-test
  "pm.blueprint/seat: a blueprint is seated on the ground under its feet
   before it is drawn (the mailbox bench straddled the creek bank)."
  (:require [cljs.test :refer [deftest is]]
            [pm.blueprint :as bp]))

(def legs-x 0.45)

(defn- bench [at yaw]
  {:id :bench :at at :yaw yaw :summary "proposed: a bench"
   :look {:parts [{:shape :box :size [1.1 0.1 0.4] :at [0 0.45 0] :color :brown}        ; seat, off the ground
                  {:shape :box :size [0.08 0.45 0.08] :at [legs-x 0.225 0] :color :brown}    ; +x foot
                  {:shape :box :size [0.08 0.45 0.08] :at [(- legs-x) 0.225 0] :color :brown}]}})  ; -x foot

(defn- near= [a b] (< (js/Math.abs (- a b)) 1e-9))

(deftest s01-flat-ground-lifts-to-the-ground-and-keeps-the-parts
  (let [b (bp/seat (bench [2 0 3] 0) (fn [_ _] 1.5))]
    (is (= [2 1.5 3] (:at b)))
    (is (= (get-in (bench [2 0 3] 0) [:look :parts]) (get-in b [:look :parts])))
    (is (true? (:seated? b)))
    (is (not (contains? b :too-steep?)))))

(deftest s02-slope-levels-at-the-high-foot-and-stretches-the-low-one
  ;; ground rises 0.1 m per metre of x: +x foot at 1.045, -x foot at 0.955
  (let [g  (fn [x _] (+ 1 (* 0.1 x)))
        b  (bp/seat (bench [0 0 0] 0) g)
        [seat hi lo] (get-in b [:look :parts])]
    (is (near= (+ 1 (* 0.1 legs-x)) (second (:at b))))
    (is (= [0.08 0.45 0.08] (:size hi)))
    (is (near= (+ 0.45 0.09) (get-in lo [:size 1])))
    ;; the stretched foot's bottom reaches its own ground
    (is (near= (- (+ (second (:at b)) (get-in lo [:at 1])) (/ (get-in lo [:size 1]) 2)) (g (- legs-x) 0)))
    (is (= [0 0.45 0] (:at seat)))
    (is (near= 0.09 (:seat-drop b)))
    (is (not (contains? b :too-steep?)))))

(deftest s03-the-bank-still-seats
  ;; the old mailbox spot: 1.3 m of drop across the bench
  (let [b (bp/seat (bench [0 0 0] 0) (fn [x _] (+ 0.86 (* 1.4 x))))]
    (is (near= 1.49 (second (:at b))))
    (is (near= 1.26 (:seat-drop b)))
    (is (true? (:seated? b)))
    (is (not (contains? b :too-steep?)))))

(deftest s04-yaw-turns-where-the-feet-stand
  ;; yaw 90 points +x at world -z: the +x foot reads the ground at z -0.45
  (let [seen (atom [])
        _    (bp/seat (bench [0 0 0] 90) (fn [x z] (swap! seen conj [(bit-or 0 (js/Math.round (* 100 x))) (bit-or 0 (js/Math.round (* 100 z)))]) 1))]
    (is (= #{[0 -45] [0 45]} (set @seen)))))

(deftest s05-missing-ground-leaves-it-as-authored
  (let [b0 (bench [0 0.7 0] 0)]
    (is (= b0 (bp/seat b0 (fn [x _] (when (pos? x) 1)))))))

(deftest s06-seated-once
  (let [b (bp/seat (bench [0 0 0] 0) (fn [x _] (+ 1 (* 0.1 x))))]
    (is (= b (bp/seat b (fn [_ _] 9))))))

(deftest s07-no-feet-sits-at-its-centre
  (let [b (bp/seat {:id :orb :at [1 0 2] :summary "proposed: an orb"
                    :look {:parts [{:shape :sphere :size [0.3 0.3 0.3] :at [0 2 0]}]}}
                   (fn [x z] (+ x z)))]
    (is (= [1 3 2] (:at b)))))

(deftest s08-seat-all-a-steep-ghost-signs-and-builds-like-any-other
  (let [words {:hint "click to build" :steep "too steep here"}
        w     (bp/seat-all {:blueprint-text words :blueprints [(bench [0 0 0] 0)]}
                           (fn [x _] (* 1.4 x)))
        b     (first (bp/pending w))]
    (is (= "proposed: a bench\nclick to build" (bp/sign-text w b)))
    (is (= :approved (bp/status (bp/approve-blueprint w :bench) :bench)))))
