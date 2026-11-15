(ns pm.juice-test
  (:require [cljs.test :refer [deftest is]]
            [pm.juice :as juice]
            [pm.world :as w]))

(def T0 1000)

(deftest t01-juice-warning-below-threshold
  (is (nil? (juice/juice-warning {:name "Claude" :pct 0.79 :refill-at "14:00"})))
  (is (nil? (juice/juice-warning {:name "Claude" :pct 0.0 :refill-at "14:00"}))))

(deftest t02-juice-warning-at-and-above-threshold
  (is (= "Claude's at 80%, refills at 2:00 PM."
         (juice/juice-warning {:name "Claude" :pct 0.8 :refill-at "14:00"})))
  (is (= "Claude's at 83%, refills at 9:05 AM."
         (juice/juice-warning {:name "Claude" :pct 0.834 :refill-at "09:05"}))))

(deftest t03-juice-warning-no-refill-time
  (is (= "Claude's at 90%." (juice/juice-warning {:name "Claude" :pct 0.9})))
  (is (= "Claude's at 90%." (juice/juice-warning {:name "Claude" :pct 0.9 :refill-at "junk"})))
  (is (= "Claude's at 90%." (juice/juice-warning {:name "Claude" :pct 0.9 :refill-at 1400}))))

(deftest t04-juice-warning-unusable-data
  (is (nil? (juice/juice-warning nil)))
  (is (nil? (juice/juice-warning {})))
  (is (nil? (juice/juice-warning {:name "" :pct 0.9})))
  (is (nil? (juice/juice-warning {:name "Claude" :pct "0.9"})))
  (is (nil? (juice/juice-warning {:pct 0.9}))))

(deftest t05-tick-no-logins-is-a-no-op
  (is (= {} (juice/tick {} T0)))
  (is (= {:logins 7} (juice/tick {:logins 7} T0))))

(deftest t06-tick-bad-now-is-a-no-op
  (let [w {:logins {:claude {:name "Claude" :pct 0.9 :refill-at "14:00"}}}]
    (is (= w (juice/tick w nil)))
    (is (= w (juice/tick w "later")))))

(deftest t07-tick-fires-once-on-crossing
  (let [w  {:logins {:claude {:name "Claude" :pct 0.83 :refill-at "14:00"}}}
        w1 (juice/tick w T0)]
    (is (= #{:claude} (:warned (:juice w1))))
    (is (= {:event :juice-warning
            :text  "Claude's at 83%, refills at 2:00 PM."
            :until (+ T0 6000)}
           (:hal w1)))
    ;; still over -> no second warning (the :hal banner from w1 is left as is)
    (let [w2 (juice/tick w1 (+ T0 1))]
      (is (= #{:claude} (:warned (:juice w2))))
      (is (= (:hal w1) (:hal w2))))))

(deftest t08-tick-below-threshold-warns-nothing
  (let [w (juice/tick {:logins {:claude {:name "Claude" :pct 0.5 :refill-at "14:00"}}} T0)]
    (is (= #{} (:warned (:juice w))))
    (is (nil? (:hal w)))))

(deftest t09-tick-refill-then-cross-again-warns-again
  (let [w0 {:logins {:claude {:name "Claude" :pct 0.9 :refill-at "14:00"}}}
        w1 (juice/tick w0 T0)
        refilled (assoc-in w1 [:logins :claude :pct] 0.1)
        w2 (juice/tick refilled (+ T0 1))
        over-again (assoc-in w2 [:logins :claude :pct] 0.95)
        w3 (juice/tick over-again (+ T0 2))]
    (is (some? (:hal w1)))
    (is (= #{} (:warned (:juice w2))))
    (is (= #{:claude} (:warned (:juice w3))))
    (is (not= (:hal w1) (:hal w3)))
    (is (= (+ T0 2 6000) (:until (:hal w3))))))

(deftest t10-tick-picks-the-lowest-id-when-two-cross-together
  (let [w {:logins {:zeta  {:name "Zeta"  :pct 0.9 :refill-at "14:00"}
                     :alpha {:name "Alpha" :pct 0.9 :refill-at "14:00"}}}
        w1 (juice/tick w T0)]
    (is (= #{:alpha :zeta} (:warned (:juice w1))))
    (is (re-find #"^Alpha" (:text (:hal w1))))))

(deftest t11-real-estate-has-a-sample-login-over-the-threshold
  (let [est (w/load-estate w/estate-edn)]
    (is (map? (:logins est)))
    (is (some? (some (fn [[_ l]] (juice/juice-warning l)) (:logins est))))))
