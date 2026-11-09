(ns pm.creature-motion-test
  (:require [cljs.test :refer [deftest is]]
            [pm.creature :as c]
            [pm.creature-motion :as cm]
            [pm.species :as species]
            [pm.world :as w]))

(defn approx=
  ([a b] (approx= a b 1e-6))
  ([a b eps]
   (cond
     (and (number? a) (number? b)) (< (js/Math.abs (- a b)) eps)
     (and (sequential? a) (sequential? b))
     (and (= (count a) (count b)) (every? true? (map #(approx= %1 %2 eps) a b)))
     (and (map? a) (map? b))
     (and (= (set (keys a)) (set (keys b)))
          (every? (fn [k] (approx= (get a k) (get b k) eps)) (keys a)))
     :else (= a b))))

(def W (w/load-estate w/estate-edn))

(def cfg {:flag "pony" :kind :horse :seed 1 :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})

(def c-horse (c/parse (get species/texts :horse)))

;; M1

(deftest m01-spike-config
  (is (= cfg (cm/spike-config W)))
  (is (nil? (cm/spike-config {})))
  (is (nil? (cm/spike-config {:creature-spike {:flag "x" :kind :k :at [1 2]}})))
  (is (= {:flag "x" :kind :k :seed 1 :at [1 2 3] :yaw 0 :drop-m 0.4}
         (cm/spike-config {:creature-spike {:flag "x" :kind :k :at [1 2 3]
                                             :seed "a" :yaw nil :drop-m -1 :junk 1}}))))

;; M2

(deftest m02-spike-request
  (is (= cfg (cm/spike-request "?pony" cfg)))
  (is (= (assoc cfg :kind :dog) (cm/spike-request "?pony=dog" cfg)))
  (is (= (assoc cfg :seed 3) (cm/spike-request "?pony=horse&seed=3" cfg)))
  (is (nil? (cm/spike-request "?seed=3" cfg)))
  (is (nil? (cm/spike-request "" cfg)))
  (is (nil? (cm/spike-request nil cfg)))
  (is (= (assoc cfg :kind :dog) (cm/spike-request "?pony=dog&seed=abc" cfg)))
  (is (= (assoc cfg :seed -4) (cm/spike-request "?pony=&seed=-4" cfg)))
  (is (= (assoc cfg :kind :dog :seed 2) (cm/spike-request "?pony=dog&seed=2.9" cfg)))
  (is (nil? (cm/spike-request "?pony" nil))))

;; M3

(deftest m03-fixed-steps
  (is (= {:steps 8 :dropped 0 :acc 0} (cm/fixed-steps 0 0.016)))
  (let [r1 (cm/fixed-steps 0 0.0167)]
    (is (= 8 (:steps r1))) (is (= 0 (:dropped r1))) (is (approx= 0.0007 (:acc r1) 1e-9))
    (let [r2 (cm/fixed-steps (:acc r1) 0.0167)]
      (is (= 8 (:steps r2))) (is (= 0 (:dropped r2))) (is (approx= 0.0014 (:acc r2) 1e-9))
      (let [r3 (cm/fixed-steps (:acc r2) 0.0167)]
        (is (= 9 (:steps r3))) (is (= 0 (:dropped r3))) (is (approx= 0.0001 (:acc r3) 1e-9)))))
  (is (= {:steps 25 :dropped 25 :acc 0} (cm/fixed-steps 0 0.1)))
  (let [r (cm/fixed-steps 0.0015 0.1)]
    (is (= 25 (:steps r))) (is (= 25 (:dropped r))) (is (approx= 0.0015 (:acc r) 1e-9)))
  (is (= {:steps 25 :dropped 0 :acc 0} (cm/fixed-steps 0 0.05)))
  (is (= {:steps 25 :dropped 1 :acc 0} (cm/fixed-steps 0 0.052)))
  (is (approx= {:steps 0 :dropped 0 :acc 0.001} (cm/fixed-steps 0 0.001) 1e-9))
  (is (approx= {:steps 1 :dropped 0 :acc 0} (cm/fixed-steps 0.001 0.001) 1e-9))
  (is (approx= {:steps 0 :dropped 0 :acc 0.0005} (cm/fixed-steps 0.0005 0) 1e-9))
  (is (approx= {:steps 0 :dropped 0 :acc 0.0005} (cm/fixed-steps 0.0005 -0.01) 1e-9))
  (is (approx= {:steps 0 :dropped 0 :acc 0.0005} (cm/fixed-steps 0.0005 ##NaN) 1e-9))
  (is (= {:steps 2 :dropped 0 :acc 0} (cm/fixed-steps nil 0.004)))
  (is (= {:steps 1 :dropped 1 :acc 0} (cm/fixed-steps 0 0.01 0.005 1)))
  (is (= 0.002 cm/step-s))
  (is (= 25 cm/max-steps)))

;; M4

(deftest m04-rng
  (is (= 2 (cm/rng-seed 1)))
  (is (= 1 (cm/rng-seed 0)))
  (is (= 4 (cm/rng-seed -3)))
  (is (= 4 (cm/rng-seed 3.7)))
  (is (= 2 (cm/rng-seed 2147483647)))
  (let [[x1 u1] (cm/rng-next 2)]
    (is (= 33614 x1))
    (is (approx= 0.0000156528 u1 1e-9))
    (let [[x2 u2] (cm/rng-next x1)]
      (is (= 564950498 x2))
      (is (approx= 0.263075576 u2 1e-9))
      (let [[x3 _] (cm/rng-next x2)]
        (is (= 1097816499 x3))))))

;; M5

(deftest m05-twitch-init
  (let [t (cm/twitch-init 2 1)
        [ch0 ch1] (:chans t)]
    (is (= :twitch (:kind t)))
    (is (= 2 (:n t)))
    (is (approx= 0 (:v ch0) 1e-9)) (is (approx= 0 (:from ch0) 1e-9)) (is (approx= 0 (:t ch0) 1e-9))
    (is (approx= -0.3677249166 (:to ch0) 1e-9))
    (is (approx= 0.4618316621 (:dur ch0) 1e-9))
    (is (= 695061696 (:rng ch0)))
    (is (approx= -0.8565142722 (:to ch1) 1e-9))
    (is (approx= 0.6911567281 (:dur ch1) 1e-9))
    (is (= 1680005354 (:rng ch1))))
  (let [[ch0 ch1] (:chans (cm/twitch-init 2 3))]
    (is (approx= -0.1255960386 (:to ch0) 1e-9))
    (is (approx= -0.6143853942 (:to ch1) 1e-9))
    (is (approx= 0.3268448545 (:dur ch0) 1e-9))
    (is (approx= 0.5561699204 (:dur ch1) 1e-9)))
  (let [[ch0] (:chans (cm/twitch-init 1 0))]
    (is (approx= 0.5112106444 (:to ch0) 1e-9))
    (is (approx= 0.5293250660 (:dur ch0) 1e-9))
    (is (= 984943658 (:rng ch0)))))

;; M6

(deftest m06-twitch-step-easing
  (let [h {:kind :twitch :n 1 :chans [{:v 0 :from 0 :to 0.8 :t 0 :dur 0.5 :rng 2}]}
        ch (fn [dt] (first (:chans (cm/twitch-step h dt))))]
    (let [c (ch 0.125)]
      (is (approx= 0.125 (:v c) 1e-9)) (is (approx= 0 (:from c) 1e-9)) (is (approx= 0.8 (:to c) 1e-9))
      (is (approx= 0.125 (:t c) 1e-9)) (is (approx= 0.5 (:dur c) 1e-9)) (is (= 2 (:rng c))))
    (let [c (ch 0.25)]
      (is (approx= 0.4 (:v c) 1e-9)) (is (approx= 0.25 (:t c) 1e-9)))
    (let [c (ch 0.375)]
      (is (approx= 0.675 (:v c) 1e-9)) (is (approx= 0.375 (:t c) 1e-9)))
    (let [c (ch 0.5)]
      (is (approx= 0.8 (:v c) 1e-9)) (is (approx= 0.8 (:from c) 1e-9))
      (is (approx= -0.9999686945 (:to c) 1e-9)) (is (approx= 0 (:t c) 1e-9))
      (is (approx= 0.4315377881 (:dur c) 1e-9)) (is (= 564950498 (:rng c))))
    (let [c (ch 0.6)]
      (is (approx= 0.5548289402 (:v c) 1e-9)) (is (approx= 0.8 (:from c) 1e-9))
      (is (approx= -0.9999686945 (:to c) 1e-9)) (is (approx= 0.1 (:t c) 1e-9))
      (is (approx= 0.4315377881 (:dur c) 1e-9)) (is (= 564950498 (:rng c))))
    (let [c (ch 1.0)]
      (is (approx= -0.9764934631 (:v c) 1e-9)) (is (approx= -0.9999686945 (:from c) 1e-9))
      (is (approx= 0.0224212888 (:to c) 1e-9)) (is (approx= 0.0684622119 (:t c) 1e-9))
      (is (approx= 0.7586501319 (:dur c) 1e-9)) (is (= 1969887316 (:rng c))))))

;; M7

(deftest m07-twitch-step-noop
  (let [c (cm/twitch-init 3 1)]
    (is (= c (cm/twitch-step c 0)))
    (is (= c (cm/twitch-step c -0.1)))
    (is (= c (cm/twitch-step c ##NaN)))))

;; M8

(deftest m08-twitch-repeatable-and-seeded
  (let [run (fn [n seed] (reduce (fn [c _] (cm/twitch-step c 0.002))
                                  (cm/twitch-init n seed) (range 1000)))]
    (is (= (run 9 3) (run 9 3))))
  (is (not= (:to (first (:chans (cm/twitch-init 9 1))))
            (:to (first (:chans (cm/twitch-init 9 3)))))))

;; M9

(deftest m09-twitch-range
  (let [chans-ok? (fn [c] (every? (fn [ch] (and (<= -1 (:v ch) 1) (<= 0.3 (:dur ch) 0.8))) (:chans c)))
        init (cm/twitch-init 9 7)
        [final all-ok?] (loop [c init i 0 ok? true]
                          (if (>= i 5000)
                            [c ok?]
                            (let [c' (cm/twitch-step c 0.002)]
                              (recur c' (inc i) (and ok? (chans-ok? c'))))))]
    (is all-ok?)
    (is (every? true? (map not= (map :to (:chans init)) (map :to (:chans final)))))))

;; M10

(deftest m10-twitch-step-size-independence
  (let [stepped (reduce (fn [c _] (cm/twitch-step c 0.002)) (cm/twitch-init 3 5) (range 1000))
        jumped (cm/twitch-step (cm/twitch-init 3 5) 2.0)
        rngs [603745413 463499475 572165128]
        vs [-0.2333254991 -0.0405640359 0.4035366848]]
    (is (= (mapv :rng (:chans stepped)) (mapv :rng (:chans jumped))))
    (is (= rngs (mapv :rng (:chans jumped))))
    (is (approx= vs (mapv :v (:chans jumped)) 1e-9))
    (is (approx= (mapv :v (:chans stepped)) (mapv :v (:chans jumped)) 1e-9))))

;; M11

(deftest m11-act
  (let [[c' a] (cm/act (cm/twitch-init 2 1) nil 0.3)]
    (is (approx= [-0.2639112316 -0.3440240828] (cm/twitch-actions c') 1e-9))
    (is (= c' (cm/twitch-step (cm/twitch-init 2 1) 0.3))))
  (is (= (cm/twitch-init 2 1) (cm/controller :twitch 2 1)))
  (is (= [{:kind :still :n 3} [0 0 0]] (cm/act (cm/controller :still 3 1) nil 0.002))))

;; M12

(deftest m12-actions->ctrl
  (is (approx= [-0.5 2] (cm/actions->ctrl [-1 1] [[-0.5 1.5] [0 2]])))
  (is (approx= [0.5 1] (cm/actions->ctrl [0 0] [[-0.5 1.5] [0 2]])))
  (is (approx= [1.5 0] (cm/actions->ctrl [2 -3] [[-0.5 1.5] [0 2]])))
  (is (approx= [1 0.5] (cm/actions->ctrl [0.5 -0.5] [[-0.5 1.5] [0 2]])))
  (let [mids (cm/actions->ctrl (vec (repeat 18 0)) (c/ctrl-ranges c-horse))
        expected [0 0.043633 0 0 0 0.1308997 0.785398 0 0.1308997 0.785398
                  0 0 0 0.1308997 -0.785398 0 0.1308997 -0.785398]]
    (is (= 18 (count mids)))
    (is (approx= expected mids))))

;; M13

(deftest m13-stats-tick
  (let [s0 (cm/stats-tick cm/empty-stats 0 0.8 8 0)]
    (is (= {:since 0 :sum 0.8 :n 1 :ms nil :steps 8 :dropped 0} s0))
    (let [s1 (cm/stats-tick s0 500 1.0 9 0)]
      (is (= {:since 0 :sum 1.8 :n 2 :ms nil :steps 9 :dropped 0} s1))
      (let [s2 (cm/stats-tick s1 1000 0.9 8 2)]
        (is (= 1000 (:since s2))) (is (= 0 (:sum s2))) (is (= 0 (:n s2)))
        (is (approx= 0.9 (:ms s2))) (is (= 8 (:steps s2))) (is (= 2 (:dropped s2)))
        (let [s3 (cm/stats-tick s2 1200 2.0 25 25)]
          (is (= {:since 1000 :sum 2.0 :n 1 :steps 25 :dropped 27} (dissoc s3 :ms)))
          (is (approx= 0.9 (:ms s3))))))))

;; M14

(deftest m14-hud-text
  (is (= "pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149})))
  (is (= "pony: dog · 1.3 ms/frame · 9 steps · 3 dropped · torso 0.44 m"
         (cm/hud-text {:mode :running :flag "pony" :kind :dog
                        :stats {:ms 1.26 :steps 9 :dropped 3} :torso 0.4392})))
  (is (= "pony: horse · - ms/frame · 0 steps · 0 dropped · torso - m"
         (cm/hud-text {:mode :running :flag "pony" :kind :horse
                        :stats {:ms nil :steps 0 :dropped 0} :torso nil})))
  (is (= "pony: dog loading" (cm/hud-text {:mode :loading :flag "pony" :kind :dog})))
  (is (= "pony: lobster failed (no species :lobster)"
         (cm/hud-text {:mode :failed :flag "pony" :kind :lobster :reason "no species :lobster"})))
  (is (nil? (cm/hud-text nil)))
  (is (nil? (cm/hud-text {:mode :other :flag "pony" :kind :horse}))))

;; M15

(deftest m15-body-pose
  (let [xpos (js/Float64Array.from #js [0 0 0 1 2 3])
        xquat (js/Float64Array.from #js [1 0 0 0 0.7 0.1 0.2 0.3])]
    (is (approx= {:pos [1 3 -2] :quat [0.1 0.3 -0.2 0.7]} (cm/body-pose xpos xquat 1)))
    (is (approx= {:pos [0 0 0] :quat [0 0 0 1]} (cm/body-pose xpos xquat 0)))))

;; B5

(deftest b5-everything-downstream-adapts-by-count
  (let [n (count (c/ctrl-ranges c-horse))]
    (is (= 18 n))
    (is (= 18 (count (cm/twitch-actions (cm/twitch-step (cm/controller :twitch n 1) 0.002)))))
    (let [sig (cm/stiffness (cm/reflex-controller (cm/controller :twitch n 1) (c/reflexes c-horse) 1) 0.1 0.8)]
      (is (= 18 (count sig)))
      (is (= [1 1 1 1] (subvec (vec sig) 0 4)))
      (is (every? #(contains? #{1 0.1} %) sig)))
    (let [result (cm/limp (vec (repeat 18 0)) (js/Float64Array. 25) (vec (repeat 18 0.1)))]
      (is (= 18 (count result)))
      (is (every? zero? result)))))
