(ns pm.lot-test
  (:require [cljs.test :refer [deftest is]]
            [pm.lot :as lot]
            [pm.blueprint-file :as bf]))

(defn approx=
  ([a b] (approx= a b 1e-9))
  ([a b eps]
   (cond
     (and (number? a) (number? b)) (< (js/Math.abs (- a b)) eps)
     (and (map? a) (map? b))
     (and (= (set (keys a)) (set (keys b)))
          (every? (fn [k] (approx= (get a k) (get b k) eps)) (keys a)))
     (and (sequential? a) (sequential? b))
     (and (= (count a) (count b)) (every? true? (map #(approx= %1 %2 eps) a b)))
     :else (= a b))))

;; ---------------------------------------------------------------------------
;; §5 fixtures

(def look-stool {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0] :color :brown}]})
(def look-bench {:parts [{:shape :box :size [1.2 0.1 0.4] :at [0 0.45 0]  :color :brown}   ; seat, bottom 0.4
                          {:shape :box :size [0.1 0.4 0.1] :at [0.5 0.2 0]  :color :brown}
                          {:shape :box :size [0.1 0.4 0.1] :at [-0.5 0.2 0] :color :brown}]})
(def look-base  {:parts [{:shape :box :size [1.0 0.2 0.6] :at [0.5 0.1 0]}]})          ; wide, off-centre
(def look-float {:parts [{:shape :box :size [0.5 0.5 0.5] :at [0 1 0]}]})
(def stool {:id :stool :summary "a stool" :look look-stool})
(def bench {:id :bench :summary "a bench" :look look-bench})
(def floater {:id :floater :summary "a floater" :look look-float})

(defn rules [m] (merge {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1} m))

(defn grid "n×n, origin [0 0], step 0.5, every row = row-str, every height h"
  [n row-str h] {:origin [0 0] :step 0.5 :cols n :rows n
                 :kinds (vec (repeat n row-str)) :y (vec (repeat n (vec (repeat n h))))})
(def G7  (grid 7 "ooooooo" 1.0))     ; x, z in 0..3
(def GF9 (grid 9 "ooooooooo" 1.0))   ; x, z in 0..4
(def GT9 (grid 9 "TTTTTTTTT" 1.0))
(def GSL9 (assoc GF9 :y (vec (repeat 9 [1.0 1.2 1.4 1.6 1.8 2.0 2.2 2.4 2.6]))))   ; slope in x

(def GK {:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1.0 1.1 1.2 1.3 0.2 0.1]]})
(def GO {:origin [-12 -26] :step 0.5 :cols 2 :rows 2 :kinds ["oT" "Bo"] :y [[1 2] [3 4]]})
(def GC2 {:origin [0 0] :step 0.5 :cols 3 :rows 2 :kinds ["o:o" "~oo"] :y [[1 0.2 1] [0.1 1 1]]})

;; the two-mirror estate
(def E2 {:hud {:mirrors [:pip]}
         :mailbox {:at [4.0 1.75 -8.0] :yaw 0 :look {:parts [{:shape :box :size [3 1 3] :at [0 0.5 0]}]}}
         :crew-key {:title "Crew" :offset [3.6 2.4 2.4]}
         :motion {:obstacles [{:at [-4.8 -8.2] :r 0.7} {:at [1 1] :r 0.5}]}
         :mirrors [{:id :pip :name "Pip"
                    :placed [{:id :jar :at [1 1 1] :scale 4
                              :look {:parts [{:shape :cylinder :size [0.08 0.11 0.08] :at [0 0.055 0]}]}}]}
                   {:id :wren :name "Wren" :placed [{:id :nest :at [-2 1 -2]}]}]
         :blueprints [{:id :bench-a :at [3 1 3] :summary "s" :look look-bench}]})
(def fb2 [{:id :shed :at [5 1 5] :summary "s" :look {:parts [{:shape :box :size [2 1 2] :at [0 0.5 0]}]}}
          {:id :no-at :summary "s"}])

;; ---------------------------------------------------------------------------
;; feet, radius, turn

(deftest t01-feet-narrow-legs
  (is (= [[0.5 0] [-0.5 0]] (lot/feet look-bench))))

(deftest t02-feet-wide-base
  (is (approx= [[0.5 0] [0 -0.3] [0 0.3] [1.0 -0.3] [1.0 0.3]] (lot/feet look-base))))

(deftest t03-feet-nothing-grounded
  (is (= [] (lot/feet look-float)))
  (is (= [] (lot/feet nil)))
  (is (= [] (lot/feet {:parts []}))))

(deftest t04-feet-2cm
  (is (= [[0 0] [2 0]]
         (lot/feet {:parts [{:size [0.1 0.4 0.1] :at [0 0.215 0]}
                             {:size [0.1 0.4 0.1] :at [1 0.225 0]}
                             {:size [0.1 0.4 0.1] :at [2 0.185 0]}]}))))

(deftest t05-feet-narrow-threshold
  (is (= [[0 0]] (lot/feet {:parts [{:size [0.3 0.2 0.3] :at [0 0.1 0]}]})))
  (is (approx= [[0 0] [-0.155 -0.05] [-0.155 0.05] [0.155 -0.05] [0.155 0.05]]
               (lot/feet {:parts [{:size [0.31 0.2 0.1] :at [0 0.1 0]}]}))))

(deftest t06-feet-rot-ignored-and-sizeless-skipped
  (is (= [[0 0]]
         (lot/feet {:parts [{:size [0.1 0.4 0.1] :at [0 0.2 0] :rot [0 0 90]}
                             {:shape :box :at [1 0 1]}]}))))

(deftest t07-look-radius
  (is (approx= 0.6324555320336759 (lot/look-radius look-bench 1)))
  (is (approx= 1.2649110640673518 (lot/look-radius look-bench 2)))
  (is (approx= 0.5830951894845301 (lot/look-radius look-base 1)))
  (is (approx= 0.1414213562373095 (lot/look-radius look-stool 1)))
  (is (= nil (lot/look-radius {:parts []} 1)))
  (is (= nil (lot/look-radius nil 1))))

(deftest t08-turn
  (is (approx= [1 0] (lot/turn [1 0] 0)))
  (is (approx= [0 -1] (lot/turn [1 0] 90)))
  (is (approx= [1 0] (lot/turn [0 1] 90)))
  (is (approx= [0.707106781187 -0.707106781187] (lot/turn [1 0] 45)))
  (is (approx= [-0.3535533905935 -0.3535533905935] (lot/turn [0.5 0] 135)))
  (is (approx= [-1 0] (lot/turn [1 0] 180))))

(deftest t09-feet-at-yaw-90
  (is (approx= [[2 1.5] [1.7 2] [2.3 2] [1.7 1] [2.3 1]]
               (lot/feet-at (lot/feet look-base) [2 2] 90)))
  (is (approx= [[2.5 2] [2 1.7] [2 2.3] [3 1.7] [3 2.3]]
               (lot/feet-at (lot/feet look-base) [2 2] 0))))

;; ---------------------------------------------------------------------------
;; cells, segments

(deftest t10-cell
  (is (= {:kind "o" :y 1.0} (lot/cell GK [0 0])))
  (is (= {:kind "T" :y 1.1} (lot/cell GK [0.74 0])))
  (is (= {:kind "R" :y 1.3} (lot/cell GK [1.26 0])))
  (is (= {:kind "o" :y 1.0} (lot/cell GK [0.2 0.1])))
  (is (= {:kind " " :y nil} (lot/cell GK [3.0 0])))
  (is (= {:kind " " :y nil} (lot/cell GK [0 0.5])))
  (is (= {:kind " " :y nil} (lot/cell GK [-0.3 0])))
  (is (= {:kind "o" :y 4} (lot/cell GO [-11.5 -25.5])))
  (is (= {:kind "B" :y 3} (lot/cell GO [-12 -25.5])))
  (is (= {:kind "T" :y 2} (lot/cell GO [-11.5 -26]))))

(deftest t11-segments
  (is (= [[[0 0] [1 0]] [[1 0] [1 1]]
          [[0 0] [1 0]] [[1 0] [1 1]] [[1 1] [0 0]]
          [[2 2] [2 2]]]
         (lot/segments {:walkers [{:id :a :route [[0 0] [1 0] [1 1]]}
                                   {:id :b :route [[0 0] [1 0] [1 1]] :loop true}
                                   {:id :c :route [[2 2]]}
                                   {:id :d}]})))
  (is (= [] (lot/segments {}))))

(deftest t12-seg-dist
  (is (approx= 1.0 (lot/seg-dist [0 1] [-1 0] [1 0])))
  (is (approx= 2.0 (lot/seg-dist [3 0] [-1 0] [1 0])))
  (is (approx= 5.0 (lot/seg-dist [3 4] [0 0] [0 0])))
  (is (approx= 0.5 (lot/seg-dist [0.5 0.5] [0 0] [4 0]))))

;; ---------------------------------------------------------------------------
;; things, landmarks (both mirrors)

(deftest t13-things
  (is (approx= [{:id :bench-a    :xz [3 3]       :r 0.6324555320336759 :word "the bench a"}
                {:id :mailbox    :xz [4.0 -8.0]  :r 0.6                :word "the mailbox"}
                {:id :obstacle-1 :xz [-4.8 -8.2] :r 0.7                :word "an obstacle"}
                {:id :obstacle-2 :xz [1 1]       :r 0.5                :word "an obstacle"}
                {:id :pip-jar    :xz [1 1]       :r 0.2262741699796952 :word "Pip's jar"}
                {:id :shed       :xz [5 5]       :r 1.4142135623730951 :word "the shed"}
                {:id :wren-nest  :xz [-2 -2]     :r 0.6                :word "Wren's nest"}]
               (lot/things E2 fb2))))

;; ---------------------------------------------------------------------------
;; things, landmarks (both mirrors)

(deftest t14-landmarks
  (is (= [:bench-a :creek :mailbox :pip-jar :shed :wren-nest]
         (keys (lot/landmarks GC2 E2 fb2))))
  (is (approx= {:word "the creek" :cells [[0.5 0] [0 0.5]]}
               (:creek (lot/landmarks GC2 E2 fb2))))
  (is (= {:word "Wren's nest" :xz [-2 -2]} (:wren-nest (lot/landmarks GC2 E2 fb2))))
  (is (= {:word "Pip's jar" :xz [1 1]} (:pip-jar (lot/landmarks GC2 E2 fb2))))
  (is (= [:bench-a :mailbox :pip-jar :shed :wren-nest]
         (keys (lot/landmarks nil E2 fb2)))))

(deftest t15-landmark-lines
  (is (= ["bench-a: the bench a" "creek: the creek" "mailbox: the mailbox"
          "pip-jar: Pip's jar" "shed: the shed" "wren-nest: Wren's nest"]
         (lot/landmark-lines (lot/landmarks GC2 E2 fb2)))))

;; ---------------------------------------------------------------------------
;; spot (each check, and their order)

(defn ctx [g look things segs r]
  {:ground g :rules r :feet (lot/feet look) :radius (lot/look-radius look 1) :things things :segments segs})

(deftest t16-spot-kinds
  (let [c (ctx GK look-stool [] [] (rules {}))]
    (is (approx= {:ok true :y 1.0 :spread 0} (lot/spot c [0 0] 0)))
    (is (= {:ok false :why "most of it is under trees"} (lot/spot c [0.5 0] 0)))
    (is (= {:ok false :why "most of it is bushes or walls"} (lot/spot c [1.0 0] 0)))
    (is (= {:ok false :why "most of it is rocks"} (lot/spot c [1.5 0] 0)))
    (is (= {:ok false :why "most of it is in the creek"} (lot/spot c [2.0 0] 0)))
    (is (= {:ok false :why "most of it is in the creek"} (lot/spot c [2.5 0] 0)))
    (is (= {:ok false :why "most of it has no ground"} (lot/spot c [3.0 0] 0))))
  (let [g {:origin [0 0] :step 0.5 :cols 2 :rows 1 :kinds ["o "] :y [[nil 1.0]]}
        c (ctx g look-stool [] [] (rules {}))]
    (is (= {:ok false :why "most of it has no ground"} (lot/spot c [0 0] 0)))
    (is (= {:ok false :why "most of it has no ground"} (lot/spot c [0.5 0] 0)))))

(deftest t17-spot-steep-and-highest-foot
  (let [g {:origin [0 0] :step 0.5 :cols 5 :rows 1 :kinds ["ooooo"] :y [[1.0 1.0 1.1 1.0 1.25]]}
        c (ctx g look-bench [] [] (rules {}))]
    (is (approx= {:ok true :y 1.1 :spread 0.1} (lot/spot c [0.5 0] 0)))
    (is (approx= {:ok true :y 1.0 :spread 0} (lot/spot c [1.0 0] 0)))
    (is (= {:ok false :why "too steep"} (lot/spot c [1.5 0] 0)))))

(deftest t18-spot-near-and-route
  (let [things [{:id :well :xz [2 2] :r 0.6 :word "the well"}]
        segs [[[0 0] [4 0]]]
        c (ctx GF9 look-stool things segs (rules {:gap-m 0.5 :route-m 0.5}))]
    (is (= {:ok false :why "too close to the well"} (lot/spot c [1 2] 0)))
    (is (approx= {:ok true :y 1.0 :spread 0} (lot/spot c [0.5 2] 0)))
    (is (= {:ok false :why "on a walker's path"} (lot/spot c [0.5 0.5] 0)))
    (is (approx= {:ok true :y 1.0 :spread 0} (lot/spot c [0.5 1.0] 0))))
  (let [things [{:id :well :xz [2 2] :r 0.6 :word "the well"}
                {:id :post :xz [1.5 2] :r 0.2 :word "the post"}]
        c (ctx GF9 look-stool things [] (rules {:gap-m 0.5 :route-m 0.5}))]
    (is (= {:ok false :why "too close to the post"} (lot/spot c [1 2] 0)))))

(deftest t19-spot-check-order
  (let [g {:origin [0 0] :step 0.5 :cols 4 :rows 1 :kinds ["oooT"] :y [[1.0 1.0 1.5 1.0]]}
        things [{:id :well :xz [0.5 0] :r 0.6 :word "the well"}]
        c (ctx g look-bench things [] (rules {:gap-m 0.5}))]
    (is (= {:ok false :why "most of it is under trees"} (lot/spot c [1.0 0] 0)))
    (is (= {:ok false :why "too steep"} (lot/spot c [0.5 0] 0))))
  (let [things [{:id :well :xz [2 2] :r 0.6 :word "the well"}]
        segs [[[0 2] [4 2]]]
        c (ctx GF9 look-stool things segs (rules {:gap-m 0.5 :route-m 0.5}))]
    (is (= {:ok false :why "too close to the well"} (lot/spot c [1.5 2] 0)))))

;; ---------------------------------------------------------------------------
;; refusal, fmt

(deftest t20-refusal
  (is (= "no level open ground within 6 m of the workshop: too steep"
         (lot/refusal ["too steep" "most of it is under trees" "too steep"] 6 "the workshop")))
  (is (= "no level open ground within 1.5 m of the well: most of it is under trees"
         (lot/refusal ["too steep" "most of it is under trees"] 1.5 "the well")))
  (is (= "no level open ground within 6 m of the creek"
         (lot/refusal [] 6 "the creek"))))

(deftest t21-fmt
  (is (= "6" (lot/fmt 6)))
  (is (= "6" (lot/fmt 6.0)))
  (is (= "2.5" (lot/fmt 2.5)))
  (is (= "1.23" (lot/fmt 1.2345)))
  (is (= "0.07" (lot/fmt 0.07)))
  (is (= "-0.5" (lot/fmt -0.5)))
  (is (= "0" (lot/fmt -0.004)))
  (is (= "12.3" (lot/fmt 12.3)))
  (is (= "1.41" (lot/fmt 1.4142135623730951))))

;; ---------------------------------------------------------------------------
;; place: found (every landmark kind, both mirrors)

(deftest t22-place-found-at-a-place
  (is (approx= {:ok true :at [0.5 1.0 0.5] :yaw 0 :landmark :mailbox :distance-m 1.4142135623730951 :spread-m 0}
               (lot/place G7 {:mailbox {:at [1.5 1.0 1.5]}} []
                          (rules {:yaws [0 90] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox))))

(deftest t23-place-tie-x-before-z
  (let [g (assoc G7 :kinds (assoc (:kinds G7) 1 "oBooooo"))
        file [{:id :shed :at [1.5 1.0 1.5] :summary "a shed"
               :look {:parts [{:shape :box :size [1.0 1.0 1.0] :at [0 0.5 0]}]}}]]
    (is (approx= {:ok true :at [0.5 1.0 2.5] :yaw 0 :landmark :shed :distance-m 1.4142135623730951 :spread-m 0}
                 (lot/place g {} file (rules {:yaws [0 90] :search-m 2 :gap-m 0.5}) stool :shed)))))

(deftest t24-place-second-mirror
  (let [g {:origin [-4 -4] :step 0.5 :cols 9 :rows 9 :kinds (vec (repeat 9 "ooooooooo"))
           :y (vec (repeat 9 (vec (repeat 9 2.0))))}
        estate {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [5 1 5]}]}
                           {:id :wren :name "Wren" :placed [{:id :nest :at [-2 1 -2]}]}]}]
    (is (approx= {:ok true :at [-3.0 2.0 -3.0] :yaw 0 :landmark :wren-nest :distance-m 1.4142135623730951 :spread-m 0}
                 (lot/place g estate [] (rules {:search-m 2 :gap-m 0.5}) stool :wren-nest)))))

(deftest t25-place-yaw-90
  (let [g {:origin [0 0] :step 0.5 :cols 5 :rows 5 :kinds (vec (repeat 5 "TToTT"))
           :y [[1.0 1.0 1.0 1.0 1.0] [1.0 1.0 1.0 1.0 1.0] [1.06 1.06 1.06 1.06 1.06]
               [1.0 1.0 1.0 1.0 1.0] [1.0 1.0 1.0 1.0 1.0]]}
        estate {:corner {:at [1.0 1.0 -1.0]}}]
    (is (approx= {:ok true :at [1.0 1.06 0.5] :yaw 90 :landmark :corner :distance-m 1.5 :spread-m 0.06}
                 (lot/place g estate [] (rules {:yaws [0 45 90 135] :search-m 2}) bench :corner)))))

(deftest t26-place-creek-water
  (let [g {:origin [0 0] :step 0.5 :cols 5 :rows 1 :kinds ["~~ooo"] :y [[0.1 0.1 1.0 1.2 1.4]]}]
    (is (approx= {:ok true :at [1.0 1.0 0] :yaw 0 :landmark :creek :distance-m 0.5 :spread-m 0}
                 (lot/place g {} [] (rules {:search-m 2}) stool :creek)))))

(deftest t27-place-creek-spread-before-x
  (let [g {:origin [0 0] :step 0.5 :cols 7 :rows 4
           :kinds [":::::::" "ooooooo" "ooooooo" "ooooooo"]
           :y [[0.2 0.2 0.2 0.2 0.2 0.2 0.2]
               [1.04 1.0 1.03 1.02 1.0 1.05 1.005]
               [1.0 1.0 1.0 1.0 1.0 1.0 1.0]
               [1.0 1.0 1.0 1.0 1.0 1.0 1.0]]}]
    (is (approx= {:ok true :at [2.5 1.005 0.5] :yaw 0 :landmark :creek :distance-m 0.5 :spread-m 0.005}
                 (lot/place g {} [] (rules {:yaws [0 90] :search-m 0.5}) bench :creek)))))

(deftest t28-place-yaw-tie
  (is (approx= {:ok true :at [0.5 1.0 0.5] :yaw 0 :landmark :mailbox :distance-m 1.4142135623730951 :spread-m 0}
               (lot/place G7 {:mailbox {:at [1.5 1.0 1.5]}} []
                          (rules {:yaws [90 0] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox)))
  (is (approx= {:ok true :at [0.5 1.0 0.5] :yaw 90 :landmark :mailbox :distance-m 1.4142135623730951 :spread-m 0}
               (lot/place G7 {:mailbox {:at [1.5 1.0 1.5]}} []
                          (rules {:yaws [90] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox))))

;; ---------------------------------------------------------------------------
;; place: refusals

(deftest t29-refuse-canopy
  (is (= {:ok false :reason "no level open ground within 1 m of the well: most of it is under trees"}
         (lot/place GT9 {:well {:at [2 1 2]}} [] (rules {:yaws [0 90] :gap-m 0.5 :route-m 0.5}) stool :well))))

(deftest t30-refuse-steep
  (is (= {:ok false :reason "no level open ground within 1 m of the well: too steep"}
         (lot/place GSL9 {:well {:at [2 1 2]}} [] (rules {}) bench :well))))

(deftest t31-refuse-too-close
  (is (= {:ok false :reason "no level open ground within 1 m of the phone booth: too close to the phone booth"}
         (lot/place GF9 {:phone-booth {:at [2 1 2]}} [] (rules {:gap-m 0.5}) stool :phone-booth))))

(deftest t32-refuse-too-close-names-the-nearest
  (let [file [{:id :shed :at [2.5 1 2] :summary "s"
               :look {:parts [{:shape :box :size [2 1 2] :at [0 0.5 0]}]}}]]
    (is (= {:ok false :reason "no level open ground within 1 m of the phone booth: too close to the shed"}
           (lot/place GF9 {:phone-booth {:at [2 1 2]}} file (rules {:gap-m 0.5}) stool :phone-booth)))))

(deftest t33-refuse-route
  (is (= {:ok false :reason "no level open ground within 1.5 m of the gate: on a walker's path"}
         (lot/place GF9 {:gate {:at [2 1 2]} :walkers [{:id :w :route [[0 2] [4 2]]}]} []
                    (rules {:search-m 1.5 :route-m 2}) stool :gate))))

(deftest t34-refuse-unknown-landmark
  (let [g {:origin [0 0] :step 0.5 :cols 2 :rows 1 :kinds ["o:"] :y [[1.0 0.2]]}]
    (is (= {:ok false :reason "I don't know a place called pond; I know: creek, well"}
           (lot/place g {:well {:at [2 1 2]}} [] (rules {}) stool :pond)))
    (is (= {:ok false :reason "I don't know a place called pond; I know: creek, well"}
           (lot/place g {:well {:at [2 1 2]}} [] (rules {}) floater :pond)))))

(deftest t35-refuse-no-ground-map
  (is (= {:ok false :reason "no ground map (state/ground.edn): the twin samples it from the kit"}
         (lot/place nil {:well {:at [2 1 2]}} [] (rules {}) stool :well)))
  (is (= {:ok false :reason "no ground map (state/ground.edn): the twin samples it from the kit"}
         (lot/place nil {} [] (rules {}) floater :pond))))

(deftest t36-refuse-nothing-grounded
  (is (= {:ok false :reason "nothing touches the ground"}
         (lot/place GF9 {:well {:at [2 1 2]}} [] (rules {}) floater :well))))

(deftest t37-refuse-no-candidates
  (is (= {:ok false :reason "no level open ground within 0.2 m of the well"}
         (lot/place GF9 {:well {:at [0.3 1 0.3]}} [] (rules {:search-m 0.2}) stool :well))))

;; ---------------------------------------------------------------------------
;; the command's pure parts

(deftest t38-placed-line
  (is (= "placed bench-by-creek 1.41 m from the creek at [2.5 1.04 -0.5] yaw 90 (feet within 3 cm)"
         (lot/placed-line {:id :bench-by-creek} "the creek"
                           {:ok true :at [2.5 1.04 -0.5] :yaw 90 :landmark :creek
                            :distance-m 1.4142135623730951 :spread-m 0.034})))
  (let [t22-result (lot/place G7 {:mailbox {:at [1.5 1.0 1.5]}} []
                               (rules {:yaws [0 90] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox)]
    (is (= "placed stool 1.41 m from the mailbox at [0.5 1 0.5] yaw 0 (feet within 0 cm)"
           (lot/placed-line stool "the mailbox" t22-result)))))

(deftest t39-blueprint
  (let [t22-result (lot/place G7 {:mailbox {:at [1.5 1.0 1.5]}} []
                               (rules {:yaws [0 90] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox)
        bp (lot/blueprint (assoc stool :at [9 9 9] :extra 1) t22-result)]
    (is (= {:id :stool :at [0.5 1.0 0.5] :yaw 0 :summary "a stool" :look look-stool} bp))
    (is (true? (bf/valid? bp)))))

(deftest t40-parse-args
  (is (= {:mode :landmarks} (lot/parse-args ["--landmarks"])))
  (is (= {:mode :draft :member :member-a :path "d.edn" :near :creek :dry-run false}
         (lot/parse-args ["member-a" "d.edn" "--near" "creek"])))
  (is (= {:mode :draft :member :member-a :path "d.edn" :near :phone-booth :dry-run true}
         (lot/parse-args ["member-a" "d.edn" "--near" ":phone-booth" "--dry-run"])))
  (is (= {:mode :draft :member :member-a :path "d.edn" :near :creek :dry-run true}
         (lot/parse-args ["member-a" "d.edn" "--dry-run" "--near" "creek"])))
  (is (= {:usage lot/usage} (lot/parse-args nil)))
  (is (= {:usage lot/usage} (lot/parse-args [])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--near"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--near" "Creek!"])))
  (is (= {:usage lot/usage} (lot/parse-args ["Member" "d.edn" "--near" "creek"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--near" "creek" "--fast"])))
  (is (= {:usage lot/usage} (lot/parse-args ["--landmarks" "x"]))))
