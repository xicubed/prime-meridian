(ns pm.sky-test
  (:require [cljs.test :refer [deftest is testing]]
            [pm.sky :as sky]))

(def W {:sky [["19:00" :night] ["07:15" :golden] ["06:45" :blue] ["08:15" :day]
              ["16:00" :golden] ["16:45" :blue] ["25:00" :day] ["12:00" :noon]]})

(deftest t01-schedule-sorted-and-cleaned
  (is (= [[405 :blue] [435 :golden] [495 :day] [960 :golden] [1005 :blue] [1140 :night]]
         (sky/schedule W)))
  (is (= [] (sky/schedule {}))))

(deftest t02-hour-at
  (let [s (sky/schedule W)]
    (is (= :night (sky/hour-at s 0)) "past midnight wraps to the day's last entry")
    (is (= :blue (sky/hour-at s 405)))
    (is (= :day (sky/hour-at s 720)))
    (is (= :night (sky/hour-at s 1439)))
    (is (nil? (sky/hour-at [] 600)))))

(deftest t03-follow-leaves-a-manual-pick-alone
  (is (= {:set :night :immediate? true} (sky/follow nil :night)) "first set: at once")
  (is (nil? (sky/follow :night :night)) "same scheduled hour: don't touch the sky")
  (is (= {:set :blue :immediate? false} (sky/follow :night :blue)) "schedule moved on: fade")
  (is (nil? (sky/follow nil nil))))

(deftest t04-local-minute
  (is (= 0 (sky/local-minute 0 0)))
  (is (= (- 1440 480) (sky/local-minute 0 -480)) "UTC midnight is 4 PM the day before in PST"))
