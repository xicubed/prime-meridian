(ns pm.wander-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m]
            [pm.world]))

(def pts [[0 0] [1 0] [3 0] [6 0] [0 4] [20 20]])
(def world {:wander {:radius 7 :areas {:here pts :there [[1 1] [2 2]]}}
            :walkers [{:id :a :route [[0 0] [1 1]] :wander :here}
                      {:id :b :route [[0 0] [1 1]]}
                      {:id :c :route [[0 0] [1 1]] :wander :there}]})

(deftest only-spots-near-home-in-its-own-area
  (let [[a b c] (m/walkers world)]
    (is (= [[0 0] [1 0] [3 0] [6 0] [0 4]] (:spots a)) "within 7 m of home; never the far one")
    (is (= [] (:spots b)) "no :wander, no wandering")
    (is (= [[1 1] [2 2]] (:spots c)) "only its own area")))

(deftest next-spot-is-far-enough-and-repeatable
  (let [a (first (m/walkers world))]
    (doseq [leg (range 20)]
      (let [w (assoc a :leg leg :pos [0 0])
            s (m/next-spot w 2.5)]
        (is (>= (js/Math.hypot (first s) (second s)) 2.5))
        (is (= s (m/next-spot w 2.5)) "same leg, same spot")))
    (is (< 1 (count (set (map #(m/next-spot (assoc a :leg % :pos [0 0]) 2.5) (range 20))))) "it varies")))

(deftest a-wanderer-keeps-going-somewhere-new
  (let [a  (assoc (first (m/walkers world)) :pause-s 0)
        ;; walk it for a while; every leg it starts goes to one of its spots
        ws (take 400 (iterate #(m/step % 1.5 0.1) a))
        goals (set (map #(get (:route %) 1) (filter #(pos? (:leg %)) ws)))]
    (is (pos? (:leg (last ws))) "it has moved on at least once")
    (is (every? (set (:spots a)) goals))))

(deftest recolor-inverts-the-mannequin
  (let [c {:body 0x1b1b1f :joints 0xf4f2ee}]
    (is (= 0x1b1b1f (m/recolor-hex 0xf4f2ee c)) "light shell -> body")
    (is (= 0xf4f2ee (m/recolor-hex 0x16171a c)) "dark joints -> joints")
    (is (= 0x123456 (m/recolor-hex 0x123456 nil)) "no colours, unchanged")
    (is (= 0x123456 (m/recolor-hex 0x123456 {:body "black"})) "bad colours, unchanged")))

(deftest every-walker-on-the-estate-has-somewhere-to-wander
  (let [world (pm.world/load-estate pm.world/estate-edn)]
    (doseq [w (m/walkers world)]
      (is (<= 3 (count (:spots w))) (str (:id w) " has spots")))))

(deftest a-wedged-wanderer-picks-another-spot
  (let [a  (assoc (first (m/walkers world)) :pos [0 0] :route [[0 0] [6 0]] :to 1 :pause 0 :leg 3)
        ;; its body reports the same position frame after frame: wedged
        ws (iterate #(m/follow % [0 0] false 0.5) a)
        before (nth ws (dec (* 2 m/stuck-s)))
        after  (nth ws (inc (* 2 m/stuck-s)))]
    (is (= 3 (:leg before)) "still trying, under stuck-s")
    (is (= 4 (:leg after)) "gave up and picked a new leg")
    (is (= [0 0] (first (:route after))) "from where it stands")))

(deftest a-walker-that-moves-is-never-stuck
  (let [a  (assoc (first (m/walkers world)) :pos [0 0] :route [[0 0] [6 0]] :to 1 :pause 0 :leg 3)
        w  (reduce (fn [w i] (m/follow w [(* 0.05 i) 0] false 0.1)) a (range 1 100))]
    (is (= 0 (:stuck w)))))
