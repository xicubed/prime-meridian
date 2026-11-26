(ns pm.today-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string]
            [pm.today :as t]
            [pm.world :as w]
            [pm.blueprint :as bp]
            [pm.content :as c]))

(def E  (w/load-estate w/estate-edn))
(def TX (:today E))
(def TX2 {:rows {:tutorial "A" :build "B" :jack "C"} :hints {:tutorial "ha" :build "hb" :jack "hc"}
          :title "Jour" :pill "{title} · {done} sur {total}" :won "Gagné" :win-title "Bravo" :button "OK"})
(def A  1795597200000)  ; 2026-11-25T09:00Z
(def H  3600000)        ; one hour
(def B  1795683600000)  ; 2026-11-26T09:00Z
(def D1 "2026-11-25") (def D2 "2026-11-26")
(def S1 (t/tutorial-done nil A 0))   ; the tutorial done, nothing else
(def WON (-> S1 (t/build-real (+ A H) 0 {}) (t/jacked-out (+ A (* 2 H)) 0 {}) (t/jacked-in (+ A (* 3 H)) 0 {})))
(def BP1 :bench-by-mailbox) (def BP2 :lamp-post-by-canal)   ; the estate's two blueprints
(defn real [world id] (bp/make-real (bp/approve-blueprint world id) id))

(deftest t01-day-of
  (is (= (t/day-of A 0) "2026-11-25"))
  (is (= (t/day-of 1795662000000 -300) "2026-11-25"))
  (is (= (t/day-of 1795662000000 0) "2026-11-26"))
  (is (= (t/day-of 1798759800000 60) "2027-01-01"))
  (is (= (t/day-of 1835438400000 0) "2028-02-29"))
  (is (= (t/day-of 1795649400000 540) "2026-11-26"))
  (is (= (t/day-of 0 0) "1970-01-01"))
  (is (= (t/day-of -1 0) "1969-12-31"))
  (is (= (t/day-of A nil) "2026-11-25")))

(deftest t02-day-of-bad-now
  (is (nil? (t/day-of nil 0)))
  (is (nil? (t/day-of ##NaN 0)))
  (is (nil? (t/day-of ##Inf 0)))
  (is (nil? (t/day-of "x" 0)))
  (is (nil? (t/day-of nil 60)))
  (is (nil? (t/day-of ##NaN -300))))

(deftest t03-fresh
  (is (= (t/fresh D1 true) {:day D1 :tutorial? true :built? false :out? false :back? false :won-at nil}))
  (is (= (:tutorial? (t/fresh D1 nil)) false)))

(deftest t04-current-same-day-is-identical
  (is (identical? S1 (t/current S1 (+ A H) 0)))
  (is (= (t/current nil A 0) (t/fresh D1 false))))

(deftest t05-current-rollover-keeps-tutorial
  (is (= (t/current WON B 0) (t/fresh D2 true)))
  (is (= (t/current (t/fresh D1 false) B 0) (t/fresh D2 false))))

(deftest t06-current-offset-decides-day
  (is (identical? S1 (t/current S1 1795662000000 -300)))
  (is (= (t/current S1 1795662000000 0) (t/fresh D2 true))))

(deftest t07-current-and-events-with-bad-now
  (is (= (t/current S1 nil 0) S1))
  (is (nil? (t/current nil nil 0)))
  (is (nil? (t/tutorial-done nil nil 0)))
  (is (= (t/build-real S1 ##NaN 0 {}) S1))
  (is (= (t/jacked-out S1 nil 0 {}) S1))
  (is (= (t/jacked-in S1 nil 0 {}) S1)))

(deftest t08-tutorial-done-ticks-goal-1
  (is (= S1 {:day D1 :tutorial? true :built? false :out? false :back? false :won-at nil}))
  (is (= (t/tutorial-done S1 (+ A H) 0) S1)))

(deftest t09-build-real-ticks-goal-2-after-tutorial
  (is (= (:built? (t/build-real S1 (+ A H) 0 {})) true))
  (is (= (:built? (t/build-real S1 (+ A H) 0 nil)) true))
  (is (nil? (:won-at (t/build-real S1 (+ A H) 0 {})))))

(deftest t10-build-real-before-tutorial-doesnt-count
  (is (= (t/build-real nil A 0 {}) (t/fresh D1 false)))
  (is (= (:built? (t/build-real (t/fresh D1 false) A 0 {})) false)))

(deftest t11-tutorial-rock-and-at-load-builds-dont-count
  (is (= (t/build-real S1 A 0 {:practice? true}) S1))
  (is (= (t/build-real S1 A 0 {:at-load? true}) S1))
  (is (= (:built? (t/build-real S1 A 0 {:practice? false :at-load? false})) true)))

(deftest t12-jack-out-then-in-ticks-goal-3
  (let [O (t/jacked-out S1 (+ A H) 0 {})]
    (is (= (:out? O) true))
    (is (= (:back? O) false))
    (is (= (:back? (t/jacked-in O (+ A (* 2 H)) 0 {})) true))))

(deftest t13-order-matters-in-before-out-does-nothing
  (is (= (t/jacked-in S1 A 0 {}) S1))
  (let [O (t/jacked-out (t/jacked-in S1 A 0 {}) (+ A H) 0 {})]
    (is (= (:out? O) true))
    (is (= (:back? O) false))))

(deftest t14-practice-jump-doesnt-count
  (is (= (t/jacked-out S1 A 0 {:practice? true}) S1))
  (let [O (t/jacked-out S1 (+ A H) 0 {})]
    (is (= (t/jacked-in O A 0 {:practice? true}) O))))

(deftest t15-jack-before-tutorial-doesnt-count
  (let [P (t/jacked-out (t/fresh D1 false) A 0 {})]
    (is (= (:out? P) false))
    (is (= (:back? (-> P (t/tutorial-done (+ A H) 0) (t/jacked-in (+ A (* 2 H)) 0 {}))) false))))

(deftest t16-won-only-with-all-three
  (let [O (t/jacked-out S1 (+ A H) 0 {})]
    (is (false? (t/won? nil)))
    (is (false? (t/won? {})))
    (is (false? (t/won? S1)))
    (is (false? (t/won? (t/build-real S1 A 0 {}))))
    (is (false? (t/won? O)))
    (is (false? (t/won? (t/jacked-in O A 0 {}))))
    (is (false? (t/won? {:built? true :back? true})))
    (is (false? (t/won? {:tutorial? true :built? true :out? true})))
    (is (true? (t/won? WON)))
    (is (true? (t/won? {:tutorial? true :built? true :back? true})))))

(deftest t17-win-recorded-once-at-winning-event
  (is (= (:won-at WON) 1795608000000))
  (is (= (:won-at (-> WON (t/jacked-out (+ A (* 4 H)) 0 {}) (t/jacked-in (+ A (* 5 H)) 0 {}) (t/build-real (+ A (* 5 H)) 0 {})))
         1795608000000)))

(deftest t18-winning-by-the-build-last
  (let [before (-> S1 (t/jacked-out (+ A H) 0 {}) (t/jacked-in (+ A (* 2 H)) 0 {}))
        after  (t/build-real before (+ A (* 3 H)) 0 {})]
    (is (nil? (:won-at before)))
    (is (= (:won-at after) 1795608000000))))

(deftest t19-just-won
  (let [O' (-> S1 (t/build-real (+ A H) 0 {}) (t/jacked-out (+ A (* 2 H)) 0 {}))]
    (is (true? (t/just-won? O' WON))))
  (is (false? (t/just-won? WON WON)))
  (is (false? (t/just-won? nil nil)))
  (is (false? (t/just-won? WON (t/current WON B 0)))))

(deftest t20-day-rollover-with-an-event
  (is (= (t/build-real WON B 0 {}) {:day D2 :tutorial? true :built? true :out? false :back? false :won-at nil}))
  (is (false? (t/won? (t/current WON B 0)))))

(deftest t21-done-count
  (let [O (t/jacked-out S1 (+ A H) 0 {})]
    (is (= (t/done-count nil) 0))
    (is (= (t/done-count S1) 1))
    (is (= (t/done-count (t/build-real S1 A 0 {})) 2))
    (is (= (t/done-count O) 1))
    (is (= (t/done-count WON) 3))))

(deftest t22-rows-from-the-estates-texts
  (is (= (t/rows S1 TX)
         [{:id :tutorial :text "Finish the tutorial" :hint nil :ticked? true}
          {:id :build :text "Build something with your twin" :hint "Ask your twin in 💭 (type or 🎙 talk) for a thing, try it, make it real." :ticked? false}
          {:id :jack :text "Jack out and back in" :hint "Click the phone booth to jack out, then jack back in." :ticked? false}]))
  (is (= (mapv :ticked? (t/rows WON TX)) [true true true]))
  (is (= (mapv :hint (t/rows WON TX)) [nil nil nil])))

(deftest t23-rows-from-a-second-texts-map
  (is (= (t/rows (t/fresh D1 false) TX2)
         [{:id :tutorial :text "A" :hint "ha" :ticked? false}
          {:id :build :text "B" :hint "hb" :ticked? false}
          {:id :jack :text "C" :hint "hc" :ticked? false}]))
  (let [O (t/jacked-out S1 (+ A H) 0 {})]
    (is (= (nth (t/rows O TX2) 2) {:id :jack :text "C" :hint "hc" :ticked? false})))
  (is (= (mapv :text (t/rows S1 nil)) ["" "" ""]))
  (is (= (mapv :hint (t/rows S1 {})) [nil nil nil])))

(deftest t24-pill
  (is (= (t/pill S1 TX) "Today 1/3"))
  (is (= (t/pill (t/build-real S1 A 0 {}) TX) "Today 2/3"))
  (is (= (t/pill WON TX) "Day won ✓"))
  (is (= (t/pill S1 TX2) "Jour · 1 sur 3"))
  (is (= (t/pill WON TX2) "Gagné"))
  (is (= (t/pill S1 {}) " 1/3"))
  (is (= (t/pill WON {}) " 3/3"))
  (is (= (t/pill nil TX) "Today 0/3")))

(deftest t25-card-open
  (is (true? (t/card-open? S1 nil)))
  (is (false? (t/card-open? WON nil)))
  (is (false? (t/card-open? S1 false)))
  (is (true? (t/card-open? WON true)))
  (is (true? (t/card-open? nil nil))))

(deftest t26-layout-open-card
  (is (= (t/layout {:vw 1280 :vh 800 :panel-open? false :open? true})
         {:mode :card :left 14 :top 14 :width 300 :max-height 550}))
  (is (= (t/layout {:vw 720 :vh 300 :panel-open? false :open? true})
         {:mode :card :left 14 :top 14 :width 300 :max-height 120})))

(deftest t27-layout-pill
  (let [pill {:mode :pill :left 14 :top 14 :width nil :max-height nil}]
    (is (= (t/layout {:vw 1280 :vh 800 :panel-open? true :open? true}) pill))
    (is (= (t/layout {:vw 1280 :vh 800 :panel-open? false :open? false}) pill))
    (is (= (t/layout {:vw 719 :vh 800 :panel-open? false :open? true}) pill))
    (is (= (t/layout {:vw nil :vh 800 :open? true}) pill))
    (is (= (t/layout {}) pill)))
  (is (true? (< (+ t/margin t/pill-h) t/panel-top))))

(deftest t28-win-line
  (is (= (t/win-line {:day-won ["x" "y"]} 0) "x"))
  (is (= (t/win-line {:day-won ["x" "y"]} 1) "y"))
  (is (= (t/win-line {:day-won ["x" "y"]} 3) "y"))
  (is (= (t/win-line {} 0) t/fallback-win-line))
  (is (= t/fallback-win-line "Day won. What you built stays built."))
  (is (= (t/win-line nil 5) t/fallback-win-line))
  (is (= (t/win-line c/hal-lines 0) "That's the day won. What you built stays built."))
  (is (= (count (:day-won c/hal-lines)) 4))
  (is (not-any? #(clojure.string/includes? % "{") (:day-won c/hal-lines))))

(deftest t29-win-card-from-data
  (is (= (t/win-card TX "L") {:title "Day won" :line "L" :button "Keep playing"}))
  (is (= (t/win-card TX2 "L") {:title "Bravo" :line "L" :button "OK"})))

(deftest t30-save-text-restore
  (is (= (t/restore (t/save-text WON)) WON))
  (is (= (t/restore (t/save-text S1)) S1))
  (is (nil? (t/restore nil)))
  (is (nil? (t/restore "")))
  (is (nil? (t/restore "{:day")))
  (is (nil? (t/restore "[1 2]")))
  (is (nil? (t/restore "{:day 5}")))
  (is (nil? (t/restore "{:built? true}")))
  (is (= (t/restore "{:day \"2026-11-25\" :built? true}") {:day "2026-11-25" :built? true})))

(deftest t31-real-ids-on-both-estate-blueprints
  (is (= (t/real-ids E) #{}))
  (is (= (t/real-ids (real E BP1)) #{:bench-by-mailbox}))
  (is (= (t/real-ids (real E BP2)) #{:lamp-post-by-canal}))
  (is (= (t/real-ids (-> E (real BP1) (real BP2))) #{:bench-by-mailbox :lamp-post-by-canal}))
  (is (= (t/real-ids (bp/approve-blueprint E BP1)) #{}))
  (is (= (t/real-ids (bp/start-removal (real E BP1) BP1)) #{:bench-by-mailbox}))
  (is (= (t/real-ids nil) #{})))

(deftest t32-transitions-builds
  (is (= (t/transitions E (real E BP1)) [:build-real]))
  (is (= (t/transitions E (real E BP2)) [:build-real]))
  (is (= (t/transitions (real E BP1) (-> E (real BP1) (real BP2))) [:build-real]))
  (is (= (t/transitions (real E BP1) (real E BP1)) []))
  (is (= (t/transitions (bp/start-removal (real E BP1) BP1) (real E BP1)) [])))

(deftest t33-transitions-the-jack
  (is (= (t/transitions (assoc E :jack {:out? false}) (assoc E :jack {:out? true :at A})) [:jacked-out]))
  (is (= (t/transitions (assoc E :jack {:out? true :at A}) (assoc E :jack {:out? false})) [:jacked-in]))
  (is (= (t/transitions E (assoc E :jack {:out? true})) [:jacked-out]))
  (is (= (t/transitions nil {:jack {:out? true}}) [:jacked-out]))
  (is (= (t/transitions E (assoc-in E [:construct :jumped-out-at] A)) []))
  (is (= (t/transitions nil nil) [])))

(deftest t34-transitions-both-in-one-step-in-order
  (is (= (t/transitions E (assoc (real E BP2) :jack {:out? true})) [:build-real :jacked-out])))

(deftest t35-a-whole-day-the-way-the-core-watch-drives-it
  (let [step (fn [s w0 w1 now opts]
               (reduce (fn [s e]
                         (case e
                           :build-real (t/build-real s now 0 opts)
                           :jacked-out (t/jacked-out s now 0 opts)
                           :jacked-in  (t/jacked-in s now 0 opts)))
                       s (t/transitions w0 w1)))
        practice-out (assoc E :jack {:out? true})
        practice-in  (assoc E :jack {:out? false})
        s (step nil E practice-out A {:practice? true})
        s (step s practice-out practice-in A {:practice? true})
        _ (is (nil? s))
        s (t/tutorial-done s A 0)
        w-load (real E BP1)
        s (step s E w-load A {:at-load? true})
        _ (is (= (:built? s) false))
        w-build (real w-load BP2)
        s (step s w-load w-build (+ A H) {})
        _ (is (= (:built? s) true))
        w-out (assoc w-build :jack {:out? true})
        w-in  (assoc w-build :jack {:out? false})
        s (step s w-build w-out (+ A (* 2 H)) {})
        s (step s w-out w-in (+ A (* 3 H)) {})]
    (is (true? (t/won? s)))
    (is (= (:won-at s) 1795608000000))
    (is (= (t/done-count s) 3))
    (is (= (t/pill s TX) "Day won ✓"))))

(deftest t36-estate-block-complete-data-names-no-one
  (is (every? string? [(:title TX) (:pill TX) (:won TX) (:collapse TX) (:win-title TX) (:button TX)
                        (get-in TX [:rows :tutorial]) (get-in TX [:rows :build]) (get-in TX [:rows :jack])
                        (get-in TX [:hints :build]) (get-in TX [:hints :jack])]))
  (is (nil? (some #(clojure.string/includes? (pr-str TX) %) ["Michael" "Hal" "Flora" "Juniper"]))))
