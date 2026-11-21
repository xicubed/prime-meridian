(ns pm.twin-test
  (:require [cljs.test :refer [deftest is]]
            [pm.talk :as talk]
            [pm.world :as w]))

(def twin (first (filter #(= :hal (:agent %)) (:walkers (w/load-estate w/estate-edn)))))

(deftest the-twin-gets-a-say-row
  (let [rows (talk/with-twin [{:member :le-testeur :who "Le Testeur" :say? true}] twin true)
        hal  (last rows)]
    (is (= 2 (count rows)))
    (is (= [(:agent twin) (:name twin) true] [(:member hal) (:who hal) (:say? hal)]))))

(deftest no-bridge-he-still-listens-and-answers-that-he-cannot-hear
  (is (true? (:say? (last (talk/with-twin [] twin false)))))
  (let [W  (w/load-estate w/estate-edn)
        ev (w/twin-says W (w/no-bridge-line W))]
    (is (string? (w/no-bridge-line W)))
    (is (= [(name (:agent twin)) :say] [(:agent ev) (:kind ev)]))
    (is (= (w/no-bridge-line W) (get (w/says-now (w/apply-event W ev 1800000000000) 1800000000000) (name (:agent twin)))))
    (is (nil? (w/twin-says W "   ")))
    (is (= (first (get-in W [:twin-talk :sample-says])) (w/sample-say W 0) (w/sample-say W (count (get-in W [:twin-talk :sample-says])))))))

(deftest only-once-and-only-with-a-twin
  (let [rows (talk/with-twin [] twin true)]
    (is (= rows (talk/with-twin rows twin true))))
  (is (= [] (talk/with-twin [] nil true))))

(def T0 1800000000000)

(deftest thinking-from-a-say-until-his-reply
  (let [a    (:agent twin)
        talk (talk/crew-say nil a (:name twin) "Hal, where's the mailbox?" T0)
        sent (update-in talk [:entries 0] assoc :state :sent)]
    (is (false? (talk/twin-thinking? nil {} a T0)) "nothing said")
    (is (true? (talk/twin-thinking? talk {} a (+ T0 1000))) "sending counts")
    (is (true? (talk/twin-thinking? sent {} a (+ T0 1000))))
    (is (false? (talk/twin-thinking? sent {(name a) {:text "By the booth." :since (+ T0 5000)}} a (+ T0 6000)))
        "his reply ends it")
    (is (true? (talk/twin-thinking? sent {(name a) {:text "earlier" :since (- T0 5000)}} a (+ T0 1000)))
        "an older say doesn't count as the reply")
    (is (false? (talk/twin-thinking? sent {} a (+ T0 talk/twin-think-ms 1))) "and it times out")
    (is (= :working (:status (last (talk/with-twin [] twin true true)))))
    (is (= :idle (:status (last (talk/with-twin [] twin true false)))))))
