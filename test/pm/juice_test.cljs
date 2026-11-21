(ns pm.juice-test
  (:require [cljs.test :refer [deftest is]]
            [pm.juice :as juice]
            [pm.world :as w]))

(def T0 1000)

(deftest t01-juice-warning-below-threshold
  (is (nil? (juice/juice-warning {:name "Claude" :tokens-used 79000 :cap 100000 :refill-at "14:00"})))
  (is (nil? (juice/juice-warning {:name "Claude" :tokens-used 0 :cap 100000 :refill-at "14:00"}))))

(deftest t02-juice-warning-at-and-above-threshold
  (is (= "Claude's used 80% till 2:00 PM."
         (juice/juice-warning {:name "Claude" :tokens-used 80000 :cap 100000 :refill-at "14:00"})))
  (is (= "Claude's used 83% till 9:05 AM."
         (juice/juice-warning {:name "Claude" :tokens-used 834 :cap 1000 :refill-at "09:05"}))))

(deftest t03-juice-warning-no-refill-time
  (is (= "Claude's used 90%." (juice/juice-warning {:name "Claude" :tokens-used 90000 :cap 100000})))
  (is (= "Claude's used 90%." (juice/juice-warning {:name "Claude" :tokens-used 90000 :cap 100000 :refill-at "junk"})))
  (is (= "Claude's used 90%." (juice/juice-warning {:name "Claude" :tokens-used 90000 :cap 100000 :refill-at 1400}))))

(deftest t04-juice-warning-unusable-data
  (is (nil? (juice/juice-warning nil)))
  (is (nil? (juice/juice-warning {})))
  (is (nil? (juice/juice-warning {:name "" :tokens-used 90000 :cap 100000})))
  (is (nil? (juice/juice-warning {:name "Claude" :tokens-used "90000" :cap 100000})))
  (is (nil? (juice/juice-warning {:name "Claude" :tokens-used 90000 :cap 0})))
  (is (nil? (juice/juice-warning {:tokens-used 90000 :cap 100000}))))

(deftest t05-tick-no-logins-is-a-no-op
  (is (= {} (juice/tick {} T0)))
  (is (= {:logins 7} (juice/tick {:logins 7} T0))))

(deftest t06-tick-bad-now-is-a-no-op
  (let [w {:logins {:claude {:name "Claude" :tokens-used 90000 :cap 100000 :refill-at "14:00"}}}]
    (is (= w (juice/tick w nil)))
    (is (= w (juice/tick w "later")))))

(deftest t07-tick-fires-once-on-crossing
  (let [w  {:logins {:claude {:name "Claude" :tokens-used 83000 :cap 100000 :refill-at "14:00"}}}
        w1 (juice/tick w T0)]
    (is (= #{:claude} (:warned (:juice w1))))
    (is (= {:event :juice-warning
            :text  "Claude's used 83% till 2:00 PM."
            :until (+ T0 6000)}
           (:hal w1)))
    ;; still over -> no second warning (the :hal banner from w1 is left as is)
    (let [w2 (juice/tick w1 (+ T0 1))]
      (is (= #{:claude} (:warned (:juice w2))))
      (is (= (:hal w1) (:hal w2))))))

(deftest t08-tick-below-threshold-warns-nothing
  (let [w (juice/tick {:logins {:claude {:name "Claude" :tokens-used 50000 :cap 100000 :refill-at "14:00"}}} T0)]
    (is (= #{} (:warned (:juice w))))
    (is (nil? (:hal w)))))

(deftest t09-tick-refill-then-cross-again-warns-again
  (let [w0 {:logins {:claude {:name "Claude" :tokens-used 90000 :cap 100000 :refill-at "14:00"}}}
        w1 (juice/tick w0 T0)
        refilled (assoc-in w1 [:logins :claude :tokens-used] 10000)
        w2 (juice/tick refilled (+ T0 1))
        over-again (assoc-in w2 [:logins :claude :tokens-used] 95000)
        w3 (juice/tick over-again (+ T0 2))]
    (is (some? (:hal w1)))
    (is (= #{} (:warned (:juice w2))))
    (is (= #{:claude} (:warned (:juice w3))))
    (is (not= (:hal w1) (:hal w3)))
    (is (= (+ T0 2 6000) (:until (:hal w3))))))

(deftest t10-tick-picks-the-lowest-id-when-two-cross-together
  (let [w {:logins {:zeta  {:name "Zeta"  :tokens-used 90000 :cap 100000 :refill-at "14:00"}
                     :alpha {:name "Alpha" :tokens-used 90000 :cap 100000 :refill-at "14:00"}}}
        w1 (juice/tick w T0)]
    (is (= #{:alpha :zeta} (:warned (:juice w1))))
    (is (re-find #"^Alpha" (:text (:hal w1))))))

(deftest t11-real-estate-sample-login-follows-the-clock
  ;; the estate's login is a SAMPLE read off the clock: 5-hour windows from 14:00,
  ;; climbing to 83%; so it's over the threshold near the end of a window, under
  ;; it just after one, and its refill time is the next window boundary
  (let [est  (w/load-estate w/estate-edn)
        day  (* 86400000 20000)                       ; a UTC midnight; offset 0 keeps it simple
        at   (fn [hhmm] (let [[h m] (map js/parseInt (clojure.string/split hhmm #":"))] (+ day (* 60000 (+ (* 60 h) m)))))
        live (fn [hhmm] (:claude (juice/live-logins (:logins est) (at hhmm) 0)))]
    (is (map? (:logins est)))
    (is (= "19:00" (:refill-at (live "18:50"))))
    (is (some? (juice/juice-warning (live "18:50"))) "late in a window: over 80%")
    (is (= "14:00" (:refill-at (live "13:00"))))
    (is (nil? (juice/juice-warning (live "14:10"))) "just after a refill: nowhere near")
    (is (< (:tokens-used (live "14:10")) (:tokens-used (live "16:00")) (:tokens-used (live "18:50"))))
    (is (= "19:00" (:refill-at (live "14:10"))))
    (is (= {:x {:name "X" :tokens-used 5 :cap 10}} (juice/live-logins {:x {:name "X" :tokens-used 5 :cap 10}} (at "12:00") 0))
        "a plain login is untouched")))

(deftest t11b-the-real-reading-wins-while-fresh
  (let [file (juice/parse-file "{:as-of 1000 :via \"le-coureur\" :logins {:claude {:name \"Claude\" :tokens-used 23 :cap 100 :refill-at \"19:00\"}}}")
        est  (w/load-estate w/estate-edn)]
    (is (some? file))
    (is (true? (juice/real? file 2000)))
    (is (false? (juice/real? file (+ 1000 juice/real-fresh-ms))))
    (is (= {:name "Claude" :tokens-used 23 :cap 100 :refill-at "19:00" :real? true :via "le-coureur"}
           (:claude (juice/logins-now est file 2000 0))))
    (is (nil? (:real? (:claude (juice/logins-now est file (+ 1000 juice/real-fresh-ms) 0)))) "stale: back to the sample")
    (is (re-find #"\(real, via le-coureur\)$" (juice/hud-segment (juice/logins-now est file 2000 0))))
    (is (nil? (juice/parse-file "not edn {")))
    (is (nil? (juice/parse-file "{:logins {}}")) "no :as-of, no reading")))

(deftest t12-estimate-juice
  (is (= 0.5 (juice/estimate-juice 50 100)))
  (is (= 0.83 (juice/estimate-juice 83 100)))
  (is (= 0 (juice/estimate-juice 0 100)))
  (is (= 1.0 (juice/estimate-juice 150 100))) ;; capped at the window
  (is (= 0 (juice/estimate-juice -5 100)))    ;; never negative
  (is (= 0 (juice/estimate-juice 50 0)))      ;; no cap, no estimate
  (is (= 0 (juice/estimate-juice 50 -10)))
  (is (= 0 (juice/estimate-juice "50" 100)))
  (is (= 0 (juice/estimate-juice 50 "100")))
  (is (= 0 (juice/estimate-juice nil 100))))

(deftest t13-hud-segment
  (is (nil? (juice/hud-segment nil)))
  (is (nil? (juice/hud-segment {})))
  (is (nil? (juice/hud-segment {:claude {:name "" :tokens-used 1 :cap 10}})))
  (is (= "juice: Claude 17% left, refills 2:00 PM (sample)"
         (juice/hud-segment {:claude {:name "Claude" :tokens-used 83000 :cap 100000 :refill-at "14:00"}})))
  (is (= "juice: Claude 10% left (sample)"
         (juice/hud-segment {:claude {:name "Claude" :tokens-used 90 :cap 100}})))
  (let [s (juice/hud-segment {:zeta  {:name "Zeta"  :tokens-used 10 :cap 100 :refill-at "09:00"}
                               :alpha {:name "Alpha" :tokens-used 20 :cap 100 :refill-at "09:00"}})]
    (is (re-find #"Zeta" s))
    (is (re-find #"Alpha" s))))
