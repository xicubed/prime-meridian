(ns pm.music-test
  (:require [cljs.test :refer [deftest is]]
            [pm.music :as music]
            [pm.world :as w]))

;; the rule reads the estate's own :music, never restated numbers or names
(def M (:music (w/load-estate w/estate-edn)))

(deftest the-construct-and-the-dojo-win-over-the-sky
  (is (= :dojo (music/track-for M {:sparring? true :construct? true :sky :day})))
  (is (= :construct (music/track-for M {:construct? true :sky :night}))))

(deftest otherwise-the-sky-picks
  (doseq [[hour track] (:by-sky M)]
    (is (= track (music/track-for M {:sky hour})) (str hour))))

(deftest every-sky-hour-has-music
  (doseq [hour [:day :golden :blue :night]]
    (is (some? (music/url-for M (music/track-for M {:sky hour}))) (str hour))))

(deftest every-scene-track-has-a-file
  (is (some? (music/url-for M :construct)))
  (is (some? (music/url-for M :dojo))))

(deftest no-hour-no-music
  (is (nil? (music/track-for M {})))
  (is (nil? (music/url-for M :nope))))
