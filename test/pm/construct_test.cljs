(ns pm.construct-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.construct :as construct]
            [pm.world :as w]))

;; The Construct's script is data (estate.edn :construct-script).
(def SC (select-keys (w/load-estate w/estate-edn) [:construct-script]))

(def T0 1000000)

(deftest t01-enter-records-once
  (let [w (construct/enter SC false T0)]
    (is (= T0 (get-in w [:construct :entered-at])))
    ;; a second call at a later time doesn't move it
    (is (= w (construct/enter w false (+ T0 999))))))

(deftest t02-enter-no-op-when-seen-or-bad-now
  (is (= {} (construct/enter {} true T0)))
  (is (= {} (construct/enter {} false nil)))
  (is (= {} (construct/enter {} false "nope"))))

(deftest t03-white-room-nil-for-a-returning-player
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/white-room w true T0)))))

(deftest t04-white-room-nil-before-entered
  (is (nil? (construct/white-room {} false T0))))

(deftest t05-white-room-nil-bad-now
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/white-room w false nil)))
    (is (nil? (construct/white-room w false "nope")))))

(deftest t06-white-room-silent-then-twin-then-done
  (let [w (construct/enter SC false T0)]
    (is (= {:phase :silent} (construct/white-room w false T0)))
    (is (= {:phase :silent} (construct/white-room w false (+ T0 (dec construct/silent-ms)))))
    (is (= {:phase :twin :line (construct/opening-line SC)}
           (construct/white-room w false (+ T0 construct/silent-ms))))
    (is (= {:phase :twin :line (construct/opening-line SC)}
           (construct/white-room w false (+ T0 construct/silent-ms (dec construct/line-ms)))))
    (is (nil? (construct/white-room w false (+ T0 construct/silent-ms construct/line-ms))))))

(deftest t07-white-room-never-negative-elapsed
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/white-room w false (dec T0))))))

(deftest t08-opening-line-is-the-original-not-a-film-quote
  (is (string? (construct/opening-line SC)))
  (is (not (str/blank? (construct/opening-line SC))))
  (is (not (str/includes? (construct/opening-line SC) "kung fu")))
  (is (str/includes? (construct/opening-line SC) "white room")))

;; Beat 2: the program rack (GDD §2.6, §2.7)

(def ^:private beat1-done-at (+ T0 construct/silent-ms construct/line-ms))

(deftest t09-program-rack-nil-before-beat1-is-done
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/program-rack w false T0)))
    (is (nil? (construct/program-rack w false (dec beat1-done-at))))))

(deftest t10-program-rack-nil-for-a-returning-player-or-before-entered
  (is (nil? (construct/program-rack {} true T0)))
  (is (nil? (construct/program-rack SC false T0))))

(deftest t11-program-rack-nil-bad-now
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/program-rack w false nil)))
    (is (nil? (construct/program-rack w false "nope")))))

(deftest t12-program-rack-scrolls-then-own-agent-loads-then-done
  (let [w (construct/enter SC false T0)]
    (is (= {:phase :rack :programs (construct/programs SC)}
           (construct/program-rack w false beat1-done-at)))
    (is (= {:phase :rack :programs (construct/programs SC)}
           (construct/program-rack w false (+ beat1-done-at (dec construct/rack-ms)))))
    (is (= {:phase :loaded :program (construct/own-agent SC) :line (construct/loaded-line SC)}
           (construct/program-rack w false (+ beat1-done-at construct/rack-ms))))
    (is (= {:phase :loaded :program (construct/own-agent SC) :line (construct/loaded-line SC)}
           (construct/program-rack w false (+ beat1-done-at construct/rack-ms (dec construct/line-ms)))))
    (is (nil? (construct/program-rack w false (+ beat1-done-at construct/rack-ms construct/line-ms))))))

(deftest t13-own-agent-leads-the-rack-never-mail
  (is (= :own-agent (:id (first (construct/programs SC)))))
  (is (= (:id (construct/own-agent SC)) (:id (first (construct/programs SC)))))
  (is (not= :mail (:id (first (construct/programs SC))))))

(deftest t14-load-program-records-sample-load
  (let [w (construct/load-program SC :mail T0)]
    (is (= #{:mail} (get-in w [:construct :loaded])))
    (is (nil? (get-in w [:construct :building-at])))))

(deftest t15-load-program-own-agent-also-drops-its-building
  (let [w (construct/load-program SC :own-agent T0)]
    (is (= #{:own-agent} (get-in w [:construct :loaded])))
    (is (= T0 (get-in w [:construct :building-at])))))

(deftest t16-load-program-is-a-no-op-once-loaded-or-with-a-bad-now
  (let [w (construct/load-program SC :own-agent T0)]
    (is (= w (construct/load-program w :own-agent (+ T0 999))))
    (is (= SC (construct/load-program SC :own-agent nil)))
    (is (= SC (construct/load-program SC :own-agent "nope")))))

(deftest t17-loaded-line-is-the-original-not-a-film-quote
  (is (string? (construct/loaded-line SC)))
  (is (not (str/blank? (construct/loaded-line SC))))
  (is (not (str/includes? (construct/loaded-line SC) "kung fu"))))
