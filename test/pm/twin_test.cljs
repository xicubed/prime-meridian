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

(deftest no-bridge-no-saying
  (is (false? (:say? (last (talk/with-twin [] twin false))))))

(deftest only-once-and-only-with-a-twin
  (let [rows (talk/with-twin [] twin true)]
    (is (= rows (talk/with-twin rows twin true))))
  (is (= [] (talk/with-twin [] nil true))))
