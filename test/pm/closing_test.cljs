(ns pm.closing-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.closing :as cl]
            [pm.world]))

(def DAY (* 20000 86400000))                ; an arbitrary local-midnight-aligned day, at offset 0
(defn at [minute] (+ DAY (* minute 60000)))

(def W  {})                                 ; no :bedtime -> default
(def W9 {:bedtime "09:00"})
(def WB {:bedtime "9:00"})                  ; bad shape -> default
(def WN {:bedtime 2230})                    ; not a string -> default
(def WO {:bedtime "25:00"})                 ; out of range -> default

(deftest t01-bedtime-default-and-custom
  (is (= (cl/bedtime W) "22:30"))
  (is (= (cl/bedtime W9) "09:00"))
  (is (= (cl/bedtime WB) "22:30"))
  (is (= (cl/bedtime WN) "22:30"))
  (is (= (cl/bedtime WO) "22:30"))
  (is (= (cl/bedtime nil) "22:30")))

(deftest t02-bedtime-text
  (is (= (cl/bedtime-text W) "10:30 PM"))
  (is (= (cl/bedtime-text W9) "9:00 AM"))
  (is (= (cl/bedtime-text {:bedtime "00:05"}) "12:05 AM"))
  (is (= (cl/bedtime-text {:bedtime "12:00"}) "12:00 PM")))

(deftest t03-last-call-window
  (is (false? (cl/last-call? W (at 1334) 0)))   ; 22:14, 16 min out
  (is (true?  (cl/last-call? W (at 1335) 0)))   ; 22:15, last call opens
  (is (true?  (cl/last-call? W (at 1349) 0)))   ; 22:29
  (is (false? (cl/last-call? W (at 1350) 0)))   ; 22:30, that's closing-time's turn
  (is (false? (cl/last-call? W (at 1000) 0)))
  (is (false? (cl/last-call? W nil 0))))

(deftest t04-closing-time-window
  (is (false? (cl/closing-time? W (at 1349) 0)))
  (is (true?  (cl/closing-time? W (at 1350) 0)))
  (is (true?  (cl/closing-time? W (at 1439) 0)))  ; 23:59, still that day
  (is (false? (cl/closing-time? W nil 0))))

(deftest t05-offset-shifts-the-local-clock
  ;; UTC 22:30 reads as 23:30 local one hour east (offset-min +60) -- already
  ;; past a 22:30 bedtime -- and as 21:30 local one hour west (-60), not yet.
  (is (true?  (cl/closing-time? W (at 1350) 60)))
  (is (false? (cl/closing-time? W (at 1350) -60))))

(deftest t06-tick-fires-last-call-once
  (let [w1 (cl/tick W (at 1335) 0)]
    (is (= (:event (:hal w1)) :last-call))
    (is (str/includes? (:text (:hal w1)) "10:30 PM"))
    (is (true? (get-in w1 [:closing :last-call?])))
    (is (not (:closed? (:closing w1))))
    (let [w2 (cl/tick w1 (at 1340) 0)]
      (is (= w2 w1)))))                            ; same window, already fired: no-op

(deftest t07-tick-fires-closing-time-once-and-marks-last-call-too
  (let [w1 (cl/tick W (at 1350) 0)]
    (is (= (:event (:hal w1)) :closing-time))
    (is (str/includes? (:text (:hal w1)) "10:30 PM"))
    (is (true? (get-in w1 [:closing :closed?])))
    (is (true? (get-in w1 [:closing :last-call?])))
    (let [w2 (cl/tick w1 (at 1400) 0)]
      (is (= w2 w1)))))                            ; still closed, already fired: no-op

(deftest t08-tick-before-last-call-is-a-quiet-no-op
  (let [w1 (cl/tick W (at 1000) 0)]
    (is (= w1 (assoc W :closing {:day (quot (at 1000) 86400000)})))
    (is (nil? (:hal w1)))))

(deftest t09-a-new-local-day-resets-both
  (let [w1 (cl/tick W (at 1350) 0)
        w2 (cl/tick w1 (+ (at 1350) 86400000) 0)]
    (is (not= (:day (:closing w1)) (:day (:closing w2))))
    (is (true? (get-in w2 [:closing :closed?])))
    (is (= (:event (:hal w2)) :closing-time))))

(deftest t10-tick-not-a-number-now-is-a-no-op
  (is (= (cl/tick W nil 0) W)))

(deftest t11-asking-to-stay-only-once-closed-and-unanswered
  (is (false? (cl/asking-to-stay? W)))
  (let [w1 (cl/tick W (at 1350) 0)]
    (is (true? (cl/asking-to-stay? w1)))
    (let [w2 (cl/stay-late w1 (at 1351))]
      (is (false? (cl/asking-to-stay? w2))))))

(deftest t12-stay-late-says-its-line-once-and-no-further-ask
  (let [w1 (cl/tick W (at 1350) 0)
        w2 (cl/stay-late w1 (at 1360) )]
    (is (= (:event (:hal w2)) :stay-late))
    (is (true? (get-in w2 [:closing :stay-late?])))
    (is (false? (cl/asking-to-stay? w2)))
    ;; later ticks that same day never re-open the ask
    (let [w3 (cl/tick w2 (at 1400) 0)]
      (is (false? (cl/asking-to-stay? w3))))))

(deftest t13-stay-late-not-a-number-now-is-a-no-op
  (is (= (cl/stay-late W nil) W)))

(deftest t14-a-new-day-resets-the-ask
  (let [w1 (cl/tick W (at 1350) 0)
        w2 (cl/stay-late w1 (at 1360))
        w3 (cl/tick w2 (+ (at 1350) 86400000) 0)]
    (is (true? (cl/asking-to-stay? w3)))))

(deftest t15-close-up-jacks-out-for-the-night
  (let [sign "closed sign"
        w1 (cl/tick W (at 1350) 0)
        w2 (cl/close-up w1 sign (at 1360))]
    (is (= :close-up (:event (:hal w2))) "the twin says goodnight")
    (is (true? (pm.world/jacked-out? w2)) "the estate freezes, like the booth")
    (is (= sign (pm.world/jack-out-text w2)))
    (is (false? (cl/asking-to-stay? w2)) "no second ask")
    (is (false? (cl/asking-to-stay? (cl/tick w2 (at 1400) 0))) "not later tonight either")
    (let [w3 (pm.world/jack-in w2 (at 1410))]
      (is (false? (pm.world/jacked-out? w3)) "the booth brings you back in")
      (is (false? (cl/asking-to-stay? w3)) "and you aren't asked again tonight"))
    (is (true? (cl/asking-to-stay? (cl/tick w2 (+ (at 1350) 86400000) 0))) "a new night asks again")))

(deftest t16-close-up-bad-now-is-a-no-op
  (is (= W (cl/close-up W "x" nil))))
