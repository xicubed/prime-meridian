(ns pm.view-test
  (:require [cljs.test :refer [deftest is]]
            [pm.view :as v]
            [pm.bridge :as b]
            [pm.lot :as lot]))

;; §8 fixtures

(def S0 {:camera {:pos [0 10 0] :target [0 0 0]} :center [1 1.5 1] :walkers {:a [0 1 0] :b [5 1 5]}})
(def S1 (assoc-in S0 [:camera :pos] [0 11 0]))
(def on {:demo? false :bridge-on? true})
(def GW {:origin [0 0] :step 0.5 :cols 9 :rows 9
         :kinds (conj (vec (repeat 8 "ooooooooo")) ":::::::::")      ; z = 4 is the creek bed
         :y (vec (repeat 9 (vec (repeat 9 1.0))))})
(def EV {:mailbox {:at [3.5 1 2]} :well {:at [0 1 0]}})
(def VV {:as-of 1000000 :camera {:pos [2 10 -5] :target [2 1.7 2]} :center [2 1.7 2]
         :walkers {:hal [3.5 1.8 2.5] :b-walker [0.5 1.6 0.5]}})

(deftest v01-vec3?
  (is (true? (v/vec3? [1 2 3])))
  (is (true? (v/vec3? [2000 0 -2000])))
  (is (true? (v/vec3? '(1 2 3))))
  (is (false? (v/vec3? [1 2])))
  (is (false? (v/vec3? [1 2 "3"])))
  (is (false? (v/vec3? [2000.01 0 0])))
  (is (false? (v/vec3? [js/NaN 0 0])))
  (is (false? (v/vec3? [js/Infinity 0 0])))
  (is (false? (v/vec3? nil))))

(deftest v02-round-cm
  (is (= 1.23 (v/round-cm 1.234)))
  (is (= -6.53 (v/round-cm -6.527)))
  (is (= 2 (v/round-cm 2)))
  (is (= 0 (v/round-cm 0.004))))

(deftest v03-snapshot
  (is (= {:camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center [1.5 1.7 -8.2]
          :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}
         (v/snapshot {:pos [1.234 5.678 -9.999] :target [0.001 0 -2.346]} [1.5 1.7049 -8.2]
                     [{:id :hal :agent :hal :name "Hal" :pos [2.501 1.8 -7.0]}
                      {:id :b-walker :pos [-5.0 1.6 -14.0]}])))
  (is (nil? (v/snapshot nil [0 0 0] [])))
  (is (nil? (v/snapshot {:pos [1 2 3000] :target [0 0 0]} [0 0 0] [])))
  (let [cam {:pos [0 0 0] :target [0 0 0]}]
    (is (nil? (:center (v/snapshot cam [3000 0 0] []))))
    (is (nil? (:center (v/snapshot cam nil []))))
    (is (= {} (:walkers (v/snapshot cam [0 0 0] [{:id :a :pos [js/NaN 0 0]}]))))
    (let [spots (for [i (range 33)] {:id (keyword (str "w" (if (< i 10) "0" "") i)) :pos [0 0 0]})
          walkers (:walkers (v/snapshot cam [0 0 0] spots))]
      (is (= 32 (count walkers)))
      (is (contains? walkers :w00))
      (is (contains? walkers :w31))
      (is (not (contains? walkers :w32))))))

(deftest v04-changed?
  (is (true? (v/changed? nil S0)))
  (is (false? (v/changed? S0 S0)))
  (is (false? (v/changed? S0 (assoc-in S0 [:camera :pos] [0 10.05 0]))))
  (is (true? (v/changed? S0 (assoc-in S0 [:camera :pos] [0 10.06 0]))))
  (is (false? (v/changed? S0 (assoc-in S0 [:camera :target] [0.03 0 0.03]))))
  (is (true? (v/changed? S0 (assoc-in S0 [:camera :target] [0.04 0 0.04]))))
  (is (false? (v/changed? S0 (assoc S0 :center [1 1.5 1.1]))))
  (is (true? (v/changed? S0 (assoc S0 :center [1 1.5 1.11]))))
  (is (true? (v/changed? S0 (assoc S0 :center nil))))
  (is (false? (v/changed? (assoc S0 :center nil) (assoc S0 :center nil))))
  (is (false? (v/changed? S0 (assoc-in S0 [:walkers :a] [0.2 1 0]))))
  (is (true? (v/changed? S0 (assoc-in S0 [:walkers :a] [0.3 1 0]))))
  (is (true? (v/changed? S0 (assoc-in S0 [:walkers :c] [9 1 9]))))
  (is (true? (v/changed? S0 (update S0 :walkers dissoc :b)))))

(deftest v05-post?
  (is (true? (v/post? {} S0 1000 on)))
  (let [st {:last S0 :last-at 1000}]
    (is (false? (v/post? st S1 2500 on)))
    (is (true? (v/post? st S1 3000 on)))
    (is (false? (v/post? st S0 3000 on)))
    (is (false? (v/post? st S0 30999 on)))
    (is (true? (v/post? st S0 31000 on)))
    (is (false? (v/post? (assoc st :in-flight? true) S1 3000 on))))
  (is (false? (v/post? {} S0 1000 {:demo? true :bridge-on? true})))
  (is (false? (v/post? {} S0 1000 {:demo? false :bridge-on? false})))
  (is (false? (v/post? {} nil 1000 on))))

(deftest v06-sent-settled
  (let [sent (v/sent {:last nil :last-at nil :in-flight? false} S1 3000)]
    (is (= {:last S1 :last-at 3000 :in-flight? true} sent))
    (is (= {:last S1 :last-at 3000 :in-flight? false} (v/settled sent)))))

(deftest v07-view-url
  (is (= "http://127.0.0.1:8091/view" (v/view-url {:port 8091 :token "t" :as-of 1}))))

(deftest v08-body-and-round-trip
  (let [snap {:camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center nil
              :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}
        b-body (v/body {:port 8091 :token "tok"} snap)]
    (is (= {"token" "tok" "action" "view"
            "camera" {"pos" [1.23 5.68 -10] "target" [0 0 -2.35]}
            "center" nil
            "walkers" [{"id" "b-walker" "pos" [-5 1.6 -14]} {"id" "hal" "pos" [2.5 1.8 -7]}]}
           b-body))
    (is (= {:ok true :action :view :camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]}
            :center nil :walkers {:b-walker [-5 1.6 -14] :hal [2.5 1.8 -7]}}
           (b/validate {:method "POST" :uri b/view-uri :origin b/allowed-origin :body b-body}
                       {:token "tok"})))))

(deftest v09-view-edn
  (let [C {:pos [1 2 3] :target [0 0 0]}]
    (is (= {:as-of 1795550000000 :camera C :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7]}}
           (v/view-edn {:ok true :action :view :camera C :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7]}}
                       1795550000000)))))

(deftest v10-fresh
  (is (= {:ok true :view VV} (v/fresh VV 1000000)))
  (is (= {:ok true :view VV} (v/fresh VV 1120000)))
  (is (= {:ok false :reason "I can't see where you're looking: the game isn't open"}
         (v/fresh VV 1120001)))
  (is (= {:ok false :reason "I can't see where you're looking: the game isn't sending its view"}
         (v/fresh nil 5)))
  (is (= {:ok false :reason "I can't see where you're looking: the game isn't sending its view"}
         (v/fresh {:camera {}} 5)))
  (is (= {:ok false :reason "I can't see where you're looking: the game isn't sending its view"}
         (v/fresh "junk" 5))))

(deftest v11-ground-word
  (let [g {:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1 1 1 1 0.2 0.1]]}]
    (is (= "open ground" (v/ground-word g [0 0])))
    (is (= "under trees" (v/ground-word g [0.5 0])))
    (is (= "bushes or walls" (v/ground-word g [1 0])))
    (is (= "rocks" (v/ground-word g [1.5 0])))
    (is (= "creek bed" (v/ground-word g [2 0])))
    (is (= "water" (v/ground-word g [2.5 0])))
    (is (= "no ground" (v/ground-word g [3 0]))))
  (is (= "ground unknown (no ground map)" (v/ground-word nil [0 0]))))

(deftest v12-where-lines-fresh
  (is (= ["view 12 s old"
          "looking at [2 2]: open ground, 1.5 m from the mailbox"
          "b-walker at [0.5 0.5]: 0.71 m from the well"
          "hal at [3.5 2.5]: 0.5 m from the mailbox"]
         (v/where-lines VV 1012500 GW (lot/landmarks GW EV [])))))

(deftest v13-where-lines-sky
  (is (= ["view 12 s old"
          "looking at the sky"
          "b-walker at [0.5 0.5]: 0.71 m from the well"
          "hal at [3.5 2.5]: 0.5 m from the mailbox"]
         (v/where-lines (assoc VV :center nil) 1012500 GW (lot/landmarks GW EV [])))))

(deftest v14-where-lines-no-ground-map
  (is (= ["view 12 s old"
          "looking at [2 2]: ground unknown (no ground map), 1.5 m from the mailbox"
          "b-walker at [0.5 0.5]: 0.71 m from the well"
          "hal at [3.5 2.5]: 0.5 m from the mailbox"]
         (v/where-lines VV 1012500 nil (lot/landmarks nil EV [])))))

(deftest v15-where-lines-bare
  (is (= ["view 0 s old" "looking at [2 2]: open ground"]
         (v/where-lines (assoc VV :walkers {}) 999000 GW {}))))
