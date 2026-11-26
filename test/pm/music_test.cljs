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

;; the rotation: the scene's own track first, then the rest of its group in turn
(deftest playlist-starts-with-the-scenes-track
  (let [M2 {:rotation [[:a :b :c] [:x :y]]}]
    (is (= [:a :b :c] (music/playlist M2 :a)))
    (is (= [:b :c :a] (music/playlist M2 :b)))
    (is (= [:y :x] (music/playlist M2 :y)))
    (is (= [:solo] (music/playlist M2 :solo)))     ; in no group: plays alone (loops)
    (is (= [] (music/playlist M2 nil)))
    (is (= [:a] (music/playlist {} :a)))))

(deftest every-estate-sky-track-rotates-through-the-others
  (doseq [sky (keys (:by-sky M))
          :let [t (music/track-for M {:sky sky}) pl (music/playlist M t)]]
    (is (= t (first pl)))
    (is (> (count pl) 1))
    (is (every? #(music/url-for M %) pl)))
  (is (= [:dojo] (music/playlist M :dojo)))
  (is (= :construct (first (music/playlist M :construct)))))
