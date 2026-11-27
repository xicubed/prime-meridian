(ns pm.placement-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string]
            [cljs.reader :as reader]
            [pm.lot :as lot]
            [pm.ask-build :as ab]
            [pm.camera :as camera]
            [pm.blueprint :as bp]
            [pm.world :as w]))

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
;; fixtures (spec §7)

(def stool {:id :stool :summary "a stool"
            :look {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0] :color :brown}]}})   ; one foot [0 0], radius 0.1414
(defn rules [m] (merge {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1} m))
(defn grid [origin n row y] {:origin origin :step 0.5 :cols n :rows n
                              :kinds (vec (repeat n row)) :y (vec (repeat n (vec (repeat n y))))})
(def GT9 (grid [0 0] 9 "TTTTTTTTT" 1.0))                 ; canopy, x z in 0..4
(def GF9 (grid [0 0] 9 "ooooooooo" 1.0))                 ; open,   x z in 0..4
(def GW  (grid [-3 -3] 13 "BBBBBBBBBBBBo" 1.0))          ; bush everywhere but the column x = 3; x z in -3..3
(def GK  {:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1.0 1.1 1.2 1.3 0.2 0.1]]})
(def SHADE (rules {:open-kinds ["o" "T"]}))
(def MOVED "Not there: {why}, so I put it {moved} m from {place}.")
(defn ctx [g r] {:ground g :rules r :feet (lot/feet (:look stool)) :radius (lot/look-radius (:look stool) 1)
                 :things [] :segments []})
(def nest-here {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [9 1 9]}]}
                           {:id :wren :name "Wren" :placed [{:id :nest :at [0 1 0]}]}]})
(def jar-here  {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [0 1 0]}]}
                           {:id :wren :name "Wren" :placed [{:id :nest :at [9 1 9]}]}]})

;; ---------------------------------------------------------------------------
;; A. Rules helpers

(deftest t01-open-kinds
  (is (= #{"o"} (lot/open-kinds {})))
  (is (= #{"o" "T"} (lot/open-kinds {:open-kinds ["o" "T"]})))
  (is (= #{"o"} (lot/open-kinds {:open-kinds nil})))
  (is (= #{"o"} (lot/open-kinds {:open-kinds "oT"}))))

(deftest t02-max-search-m
  (is (= 6 (lot/max-search-m {:search-m 6})))
  (is (= 15 (lot/max-search-m {:search-m 6 :max-search-m 15})))
  (is (= 6 (lot/max-search-m {:search-m 6 :max-search-m 4})))
  (is (= 6 (lot/max-search-m {:search-m 6 :max-search-m "15"}))))

(deftest t03-near-rules
  (is (= {:search-m 6 :max-search-m 6 :gap-m 1}
         (lot/near-rules {:search-m 6 :max-search-m 15 :gap-m 1}))))

(deftest t04-dominant-why
  (is (= "too steep" (lot/dominant-why ["too steep" "most of it is under trees" "too steep"])))
  (is (= "most of it is under trees" (lot/dominant-why ["too steep" "most of it is under trees"])))
  (is (nil? (lot/dominant-why [])))
  (is (nil? (lot/dominant-why nil))))

(deftest t05-refusal-prefix-and-nothing-fits
  (is (= "no level open ground within " lot/refusal-prefix))
  (is (= "nothing fits closer" lot/nothing-fits))
  (is (= "no level open ground within 6 m of the well: too steep"
         (lot/refusal ["too steep"] 6 "the well"))))

;; ---------------------------------------------------------------------------
;; B. :open-kinds in spot

(deftest t06-spot-canopy-open-with-shade
  (is (approx= {:ok true :y 1.1 :spread 0} (lot/spot (ctx GK SHADE) [0.5 0] 0))))

(deftest t07-spot-other-kinds-with-shade
  (let [c (ctx GK SHADE)]
    (is (approx= {:ok true :y 1.0 :spread 0} (lot/spot c [0 0] 0)))
    (is (= {:ok false :why "most of it is bushes or walls"} (lot/spot c [1.0 0] 0)))
    (is (= {:ok false :why "most of it is rocks"} (lot/spot c [1.5 0] 0)))
    (is (= {:ok false :why "most of it is in the creek"} (lot/spot c [2.0 0] 0)))
    (is (= {:ok false :why "most of it is in the creek"} (lot/spot c [2.5 0] 0)))
    (is (= {:ok false :why "most of it has no ground"} (lot/spot c [3.0 0] 0)))))

(deftest t08-spot-canopy-refused-by-default
  (is (= {:ok false :why "most of it is under trees"} (lot/spot (ctx GK (rules {})) [0.5 0] 0)))
  (is (= {:ok false :why "most of it is under trees"}
         (lot/spot (ctx GK (rules {:open-kinds ["o"]})) [0.5 0] 0))))

(deftest t09-spot-canopy-no-height
  (let [g {:origin [0 0] :step 0.5 :cols 1 :rows 1 :kinds ["T"] :y [[nil]]}]
    (is (= {:ok false :why "most of it has no ground"} (lot/spot (ctx g SHADE) [0 0] 0)))
    (is (= {:ok false :why "most of it is under trees"} (lot/spot (ctx g (rules {})) [0 0] 0)))))

;; ---------------------------------------------------------------------------
;; C. :open-kinds in place (both mirrors)

(deftest t10-place-canopy-ok-with-shade
  (is (approx= {:ok true :at [2 1.0 2] :yaw 0 :landmark [2 2] :distance-m 0 :spread-m 0}
               (lot/place GT9 {} [] SHADE stool [2 2]))))

(deftest t11-place-canopy-refused-by-default
  (is (= {:ok false :reason "no level open ground within 1 m of [2 2]: most of it is under trees"}
         (lot/place GT9 {} [] (rules {}) stool [2 2]))))

(deftest t12-place-second-mirror-under-a-crown
  (let [E {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [9 1 9]}]}
                      {:id :wren :name "Wren" :placed [{:id :nest :at [2 1 2]}]}]}
        r (rules {:open-kinds ["o" "T"] :gap-m 0.5 :search-m 2})]
    (is (approx= {:ok true :at [1 1.0 1] :yaw 0 :landmark :wren-nest :distance-m 1.4142135623730951 :spread-m 0}
                 (lot/place GT9 E [] r stool :wren-nest)))))

(deftest t13-place-second-mirror-refused-open-only
  (let [E {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [9 1 9]}]}
                      {:id :wren :name "Wren" :placed [{:id :nest :at [2 1 2]}]}]}
        r (rules {:open-kinds ["o"] :gap-m 0.5 :search-m 2})]
    (is (= {:ok false :reason "no level open ground within 2 m of Wren's nest: most of it is under trees"}
           (lot/place GT9 E [] r stool :wren-nest)))))

(deftest t14-place-first-mirror-mirrored
  (let [E {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [2 1 2]}]}
                      {:id :wren :name "Wren" :placed [{:id :nest :at [9 1 9]}]}]}
        r (rules {:open-kinds ["o" "T"] :gap-m 0.5 :search-m 2})]
    (is (approx= {:ok true :at [1 1.0 1] :yaw 0 :landmark :pip-jar :distance-m 1.4142135623730951 :spread-m 0}
                 (lot/place GT9 E [] r stool :pip-jar)))))

;; ---------------------------------------------------------------------------
;; D. The widening search

(deftest t15-place-found-in-pass-2
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0
                :moved-m 3 :why "most of it is bushes or walls"}
               (lot/place GW {} [] (rules {:max-search-m 4}) stool [0 0]))))

(deftest t16-place-widened-inclusive-edge
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0
                :moved-m 3 :why "most of it is bushes or walls"}
               (lot/place GW {} [] (rules {:max-search-m 3}) stool [0 0]))))

(deftest t17-place-refusal-past-max
  (is (= {:ok false :reason "no level open ground within 2 m of [0 0]: most of it is bushes or walls"}
         (lot/place GW {} [] (rules {:max-search-m 2}) stool [0 0]))))

(deftest t18-place-no-second-pass
  (is (= {:ok false :reason "no level open ground within 1 m of [0 0]: most of it is bushes or walls"}
         (lot/place GW {} [] (rules {}) stool [0 0])))
  (is (= {:ok false :reason "no level open ground within 1 m of [0 0]: most of it is bushes or walls"}
         (lot/place GW {} [] (rules {:max-search-m 0.5}) stool [0 0]))))

(deftest t19-place-pass-1-wins-no-new-keys
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0}
               (lot/place GW {} [] (rules {:search-m 3 :max-search-m 4}) stool [0 0]))))

(deftest t20-place-pass-1-no-candidates-at-all
  (is (approx= {:ok true :at [0 1.0 1] :yaw 0 :landmark :well :distance-m 0.7615773105863908 :spread-m 0
                :moved-m 0.7615773105863908 :why "nothing fits closer"}
               (lot/place GF9 {:well {:at [0.3 1 0.3]}} [] (rules {:search-m 0.2 :max-search-m 1}) stool :well))))

(deftest t21-place-second-mirror-widened
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark :wren-nest :distance-m 3 :spread-m 0
                :moved-m 3 :why "most of it is bushes or walls"}
               (lot/place GW nest-here [] (rules {:max-search-m 4}) stool :wren-nest))))

(deftest t22-place-first-mirror-widened
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark :pip-jar :distance-m 3 :spread-m 0
                :moved-m 3 :why "most of it is bushes or walls"}
               (lot/place GW jar-here [] (rules {:max-search-m 4}) stool :pip-jar))))

(deftest t23-place-widened-near-here
  (is (approx= {:ok true :at [3 1.0 0] :yaw 0 :landmark :here :distance-m 3 :spread-m 0
                :moved-m 3 :why "most of it is bushes or walls"}
               (lot/place GW {} [] (rules {:max-search-m 4}) stool :here
                          {:ok true :view {:center [0 1 0] :walkers {}}}))))

;; ---------------------------------------------------------------------------
;; E. The line

(deftest t24-moved-line-basic
  (is (= "Not there: most of it is bushes or walls, so I put it 3 m from where you're looking."
         (lot/moved-line MOVED "where you're looking" {:moved-m 3 :why "most of it is bushes or walls"}))))

(deftest t25-moved-line-half-metre-rounding
  (is (clojure.string/includes? (lot/moved-line MOVED "x" {:moved-m 6.5249 :why "w"}) "put it 6.5 m from"))
  (is (clojure.string/includes? (lot/moved-line MOVED "x" {:moved-m 6.76 :why "w"}) "put it 7 m from"))
  (is (clojure.string/includes? (lot/moved-line MOVED "x" {:moved-m 0.7615773105863908 :why "w"}) "put it 1 m from"))
  (is (clojure.string/includes? (lot/moved-line MOVED "x" {:moved-m 0.2 :why "w"}) "put it 0.5 m from")))

(deftest t26-moved-line-through-results-20-and-21
  (let [r20 (lot/place GF9 {:well {:at [0.3 1 0.3]}} [] (rules {:search-m 0.2 :max-search-m 1}) stool :well)
        r21 (lot/place GW nest-here [] (rules {:max-search-m 4}) stool :wren-nest)]
    (is (= "Not there: nothing fits closer, so I put it 1 m from the well."
           (lot/moved-line MOVED "the well" r20)))
    (is (= "Not there: most of it is bushes or walls, so I put it 3 m from Wren's nest."
           (lot/moved-line MOVED "Wren's nest" r21)))))

(deftest t27-moved-line-nil-why-falls-back
  (is (= "Not there: nothing fits closer, so I put it 3 m from the well."
         (lot/moved-line MOVED "the well" {:moved-m 3}))))

(deftest t28-moved-line-nil-cases
  (let [r19 (lot/place GW {} [] (rules {:search-m 3 :max-search-m 4}) stool [0 0])]
    (is (nil? (lot/moved-line MOVED "the well" r19))))
  (is (nil? (lot/moved-line nil "x" {:moved-m 3 :why "w"})))
  (is (= "w/{zz}" (lot/moved-line "{why}/{zz}" "x" {:moved-m 3 :why "w"}))))

(deftest t29-moved-line-the-estates-words
  (let [RAW (reader/read-string w/estate-edn)]
    (is (= "Not there: most of it is bushes or walls, so I put it 6.5 m from where you're looking."
           (lot/moved-line (get-in RAW [:blueprint-text :moved]) "where you're looking"
                            {:moved-m 6.4 :why "most of it is bushes or walls"})))))

;; ---------------------------------------------------------------------------
;; F. Estate data

(def RAW (reader/read-string w/estate-edn))

(deftest t30-build-rules-value
  (is (= {:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :max-search-m 15 :open-kinds ["o" "T"]
          :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12
          :show-me {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5}}
         (:build-rules RAW))))

(deftest t31-no-names
  (is (nil? (some #(clojure.string/includes? (pr-str (:build-rules RAW)) %) ["Michael" "Hal" "Flora" "Juniper"])))
  (is (nil? (some #(clojure.string/includes? (get-in RAW [:blueprint-text :moved]) %) ["Michael" "Hal" "Flora" "Juniper"]))))

;; ---------------------------------------------------------------------------
;; G. The demo's pretend twin (pm.ask-build)

(def DB (:demo-builds RAW))
(def E0 {:demo-builds DB :mirrors [] :walkers []})
(def E0m (assoc E0 :blueprint-text {:moved MOVED}))
(def R4 {:step-m 0.5 :yaws [0] :search-m 1 :max-search-m 4 :gap-m 0 :route-m 0 :max-spread-m 0.1})
(defn actx [estate rules] {:estate estate :world {} :file ab/empty-file :rules rules :here [0 1 0] :by :tw :now 5000})
(def GW1 (grid [-3 -3] 13 "BBBBBBBBBBBBo" 1))

(deftest t32-ask-has-wide-spec
  (is (= {:req {:look :lamp-post :near :here} :target [0 0]
          :spec {:origin [-2 -2] :step 0.5 :cols 9 :rows 9}
          :wide-spec {:origin [-5 -5] :step 0.5 :cols 21 :rows 21}}
         (ab/ask (actx E0 R4) "put a lamp here"))))

(deftest t33-ask-no-wide-spec-when-no-second-pass
  (is (= {:req {:look :lamp-post :near :here} :target [0 0]
          :spec {:origin [-2 -2] :step 0.5 :cols 9 :rows 9}}
         (ab/ask (actx E0 (lot/near-rules R4)) "put a lamp here"))))

(deftest t34-plan-pass-1-refuses-with-reason
  (is (= {:ok false
          :say "I can't put that there: no level open ground within 1 m of where you're looking: most of it is bushes or walls."
          :reason "no level open ground within 1 m of where you're looking: most of it is bushes or walls"}
         (ab/plan (actx E0m (lot/near-rules R4)) {:look :lamp-post :near :here} GW1))))

(deftest t35-plan-pass-2-places-and-says-so
  (let [p (ab/plan (actx E0m R4) {:look :lamp-post :near :here} GW1)]
    (is (true? (:ok p)))
    (is (= :lamp-post-1 (:id p)))
    (is (= [3 1 0] (:at (:blueprint p))))
    (is (= :tw (:by (:blueprint p))))
    (is (= "Not there: most of it is bushes or walls, so I put it 3 m from where you're looking. A lamp post, drawn near where you're looking. Try it, and make it real if you like it."
           (:say p)))))

(deftest t36-plan-no-template-no-moved-sentence
  (let [p (ab/plan (actx E0 R4) {:look :lamp-post :near :here} GW1)]
    (is (= "A lamp post, drawn near where you're looking. Try it, and make it real if you like it."
           (:say p)))))

(deftest t37-widen?
  (let [a  (ab/ask (actx E0 R4) "put a lamp here")
        p1 (ab/plan (actx E0m (lot/near-rules R4)) {:look :lamp-post :near :here} GW1)]
    (is (true? (ab/widen? a p1)))
    (is (false? (ab/widen? (dissoc a :wide-spec) p1)))
    (is (false? (ab/widen? a {:ok true})))
    (is (false? (ab/widen? a {:ok false :say "help"})))
    (is (false? (ab/widen? a {:ok false :say "x" :reason lot/no-ground-map})))))

;; ---------------------------------------------------------------------------
;; H. Show me (pm.camera)

(def S camera/show-defaults)
(def base {:ghost [10 1 0] :camera [0 5 0] :on-screen? false :card-open? false :last-move-ms nil :now-ms 100000})

(deftest t38-show-defaults
  (is (= {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5} S)))

(deftest t39-fly-decision-new-ghost-off-screen
  (is (= :fly (camera/fly-decision S base))))

(deftest t40-fly-decision-on-screen-and-near
  (is (= :in-view (camera/fly-decision S (assoc base :ghost [3 1 4] :camera [0 1 0] :on-screen? true)))))

(deftest t41-fly-decision-boundary
  (is (= :in-view (camera/fly-decision S (assoc base :ghost [6 1 8] :camera [0 1 0] :on-screen? true)))))

(deftest t42-fly-decision-on-screen-but-far
  (is (= :fly (camera/fly-decision S (assoc base :ghost [12 1 0] :camera [0 1 0] :on-screen? true)))))

(deftest t43-fly-decision-near-but-off-screen
  (is (= :fly (camera/fly-decision S (assoc base :ghost [3 1 4] :camera [0 1 0])))))

(deftest t44-fly-decision-trial-card-open
  (is (= :card-open (camera/fly-decision S (assoc base :card-open? true)))))

(deftest t45-fly-decision-recent-player-move
  (is (= :player-moving (camera/fly-decision S (assoc base :last-move-ms 99000))))
  (is (= :fly (camera/fly-decision S (assoc base :last-move-ms 98000)))))

(deftest t46-fly-decision-order
  (is (= :card-open (camera/fly-decision S (assoc base :card-open? true :last-move-ms 99500))))
  (is (= :no-ghost (camera/fly-decision S (assoc base :ghost nil :card-open? true))))
  (is (= :no-ghost (camera/fly-decision S (assoc base :ghost [1 2])))))

(deftest t47-fly-decision-no-camera
  (is (= :fly (camera/fly-decision S (assoc base :camera nil :on-screen? true)))))

(deftest t48-fly-decision-nil-show-uses-defaults
  (is (= :in-view (camera/fly-decision nil (assoc base :ghost [3 1 4] :camera [0 1 0] :on-screen? true))))
  (is (= :fly (camera/fly-decision {:near-m 4} (assoc base :ghost [3 1 4] :camera [0 1 0] :on-screen? true)))))

(deftest t49-on-screen?
  (is (true? (camera/on-screen? [0 0 0.5])))
  (is (true? (camera/on-screen? [1 -1 0.99])))
  (is (false? (camera/on-screen? [1.01 0 0.5])))
  (is (false? (camera/on-screen? [0 0 1.2])))
  (is (false? (camera/on-screen? [0 -1.01 0.5])))
  (is (false? (camera/on-screen? nil)))
  (is (false? (camera/on-screen? [0 0])))
  (is (false? (camera/on-screen? [js/NaN 0 0.5]))))

(deftest t50-flight-the-crew-maths
  (is (approx= {:target [2 2.0 3] :offset [-3.6 2.4 2.4]}
               (camera/flight [2 1 3] 1.0 1 [3.6 2.4 2.4])))
  (is (approx= {:target [-2 1.3 3] :offset [3.6 2.4 2.4]}
               (camera/flight [-2 1 3] 0.3 1 [3.6 2.4 2.4])))
  (is (approx= {:target [0 1.0 0] :offset [6.48 4.32 4.32]}
               (camera/flight [0 0 0] 1.0 1.8 [3.6 2.4 2.4])))
  (is (nil? (camera/flight [0 0] 1 1 [3.6 2.4 2.4])))
  (is (nil? (camera/flight [0 0 0] 1 1 nil))))

(deftest t51-ghost-flight
  (is (approx= {:target [2 1.6 3] :offset [-5.04 1.68 3.36]}
               (camera/ghost-flight [2 1 3] [3.6 2.4 2.4] nil)))
  (let [{:keys [offset]} (camera/ghost-flight [2 1 3] [3.6 2.4 2.4] nil)]
    (is (approx= 6.285984409780221
                 (js/Math.sqrt (reduce + (map #(* % %) offset))))))
  (is (nil? (camera/ghost-flight [2 1 3] nil nil)))
  (is (approx= {:target [-2 1.6 3] :offset [3.6 1.2 2.4]}
               (camera/ghost-flight [-2 1 3] [3.6 2.4 2.4] {:back 1}))))

;; ---------------------------------------------------------------------------
;; I. Which ghost (pm.blueprint)

(def W {:blueprints [{:id :a :at [1 2 3]} {:id :b :at [4 5 6]} {:id :c :at [1 2]}]})

(deftest t52-arrival-at
  (is (= [4 5 6] (bp/arrival-at W [:a :b])))
  (is (= [1 2 3] (bp/arrival-at W [:b :a])))
  (is (= [1 2 3] (bp/arrival-at W [:a :c])))
  (is (= [1 2 3] (bp/arrival-at W [:a :zz])))
  (is (nil? (bp/arrival-at W [])))
  (is (nil? (bp/arrival-at W nil)))
  (is (nil? (bp/arrival-at {} [:a]))))

(deftest t53-arrival-through-the-polls-merge
  (let [B  {:id :stool-1 :at [1 1 2] :yaw 0 :summary "a stool" :look (:look stool) :by :tw}
        r0 (bp/merge-file {} {:as-of 1 :blueprints [B]})]
    (is (nil? (bp/arrival-at (:world r0) (:announce r0))))
    (let [r1 (bp/merge-file (:world (bp/merge-file {} {:as-of 1 :blueprints []})) {:as-of 2 :blueprints [B]})]
      (is (= [:stool-1] (:announce r1)))
      (is (= [1 1 2] (bp/arrival-at (:world r1) (:announce r1)))))))

(deftest t54-arrival-through-the-demo-files-merge
  (let [B  {:id :stool-1 :at [1 1 2] :yaw 0 :summary "a stool" :look (:look stool) :by :tw}
        W1 (:world (bp/merge-file (w/load-estate w/estate-edn) ab/empty-file))
        r  (bp/merge-file W1 (ab/add-build ab/empty-file B 1000))]
    (is (= [1 1 2] (bp/arrival-at (:world r) (:announce r))))))
