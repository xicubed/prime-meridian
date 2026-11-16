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

(deftest t04-white-room-waits-for-the-jack-in-click
  (is (= {:phase :waiting :prompt (:start (construct/script SC))} (construct/white-room SC false T0)))
  (is (nil? (construct/white-room SC true T0)) "a returning player never sees it"))

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

(deftest t15-own-agent-building-lands-in-beat-5
  (let [w (construct/load-program SC :own-agent T0)]
    (is (= #{:own-agent} (get-in w [:construct :loaded])))
    (is (nil? (get-in w [:construct :building-at])) "not while the screen is still white")
    (is (= (+ T0 5) (get-in (construct/land-building w (+ T0 5)) [:construct :building-at])))
    (is (= (+ T0 5) (get-in (-> w (construct/land-building (+ T0 5)) (construct/land-building (+ T0 9))) [:construct :building-at])) "once")
    (is (= SC (construct/land-building SC T0)) "nothing loaded, nothing lands")))

(deftest t16-load-program-is-a-no-op-once-loaded-or-with-a-bad-now
  (let [w (construct/load-program SC :own-agent T0)]
    (is (= w (construct/load-program w :own-agent (+ T0 999))))
    (is (= SC (construct/load-program SC :own-agent nil)))
    (is (= SC (construct/load-program SC :own-agent "nope")))))

(deftest t17-loaded-line-is-the-original-not-a-film-quote
  (is (string? (construct/loaded-line SC)))
  (is (not (str/blank? (construct/loaded-line SC))))
  (is (not (str/includes? (construct/loaded-line SC) "kung fu"))))

;; Beat 3: the sparring match (GDD §2.1, §2.7, §3.1, §4.5)

(def ^:private beat2-done-at
  (+ T0 construct/silent-ms construct/line-ms construct/rack-ms construct/line-ms))

(deftest t18-sparring-nil-before-beat2-is-done
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/sparring w false T0)))
    (is (nil? (construct/sparring w false (dec beat2-done-at))))))

(deftest t19-sparring-nil-for-a-returning-player-or-before-entered
  (is (nil? (construct/sparring {} true T0)))
  (is (nil? (construct/sparring SC false T0))))

(deftest t20-sparring-nil-bad-now
  (let [w (construct/enter SC false T0)]
    (is (nil? (construct/sparring w false nil)))
    (is (nil? (construct/sparring w false "nope")))))

(deftest t21-sparring-job-then-working-then-waits-on-an-answer
  (let [w (construct/enter SC false T0)]
    (is (= {:phase :job :line (construct/job-line SC)}
           (construct/sparring w false beat2-done-at)))
    (is (= {:phase :working :worker (construct/worker-name SC) :text (construct/working-line SC)}
           (construct/sparring w false (+ beat2-done-at construct/line-ms))))
    (is (= {:phase :ask :worker (construct/worker-name SC) :prompt (construct/ask-line SC)}
           (construct/sparring w false (+ beat2-done-at construct/line-ms construct/work-ms))))
    ;; it waits indefinitely for the player's answer, however long that takes
    (is (= {:phase :ask :worker (construct/worker-name SC) :prompt (construct/ask-line SC)}
           (construct/sparring w false (+ beat2-done-at construct/line-ms construct/work-ms 999999))))))

(deftest t22-answer-then-blueprint-waits-on-approval
  (let [ask-at (+ beat2-done-at construct/line-ms construct/work-ms)
        w      (-> SC (construct/enter false T0) (construct/answer false ask-at))]
    (is (= {:phase :blueprint :summary (construct/blueprint-summary SC)}
           (construct/sparring w false ask-at)))
    (is (= {:phase :blueprint :summary (construct/blueprint-summary SC)}
           (construct/sparring w false (+ ask-at 999999))))))

(deftest t23-approve-then-done-then-nil
  (let [ask-at     (+ beat2-done-at construct/line-ms construct/work-ms)
        approve-at (+ ask-at 500)
        w          (-> SC (construct/enter false T0)
                       (construct/answer false ask-at)
                       (construct/approve-rock false approve-at))]
    (is (= {:phase :done :line (construct/rock-done-line SC)}
           (construct/sparring w false approve-at)))
    (is (= {:phase :done :line (construct/rock-done-line SC)}
           (construct/sparring w false (+ approve-at (dec construct/line-ms)))))
    (is (nil? (construct/sparring w false (+ approve-at construct/line-ms))))))

(deftest t24-answer-is-a-no-op-once-answered-seen-or-with-a-bad-now
  (let [w (construct/answer SC false T0)]
    (is (= T0 (get-in w [:construct :answered-at])))
    (is (= w (construct/answer w false (+ T0 999))))
    (is (= SC (construct/answer SC true T0)))
    (is (= SC (construct/answer SC false nil)))))

(deftest t25-approve-rock-requires-an-answer-first
  (is (= SC (construct/approve-rock SC false T0)) "no answer yet: no-op"))

(deftest t26-approve-rock-is-a-no-op-once-approved-seen-or-with-a-bad-now
  (let [answered (construct/answer SC false T0)
        w        (construct/approve-rock answered false (+ T0 10))]
    (is (= (+ T0 10) (get-in w [:construct :approved-at])))
    (is (= w (construct/approve-rock w false (+ T0 999))))
    (is (= answered (construct/approve-rock answered true (+ T0 10))))
    (is (= answered (construct/approve-rock answered false nil)))))

(deftest t27-sparring-lines-are-original-not-film-quotes
  (doseq [line [(construct/job-line SC) (construct/working-line SC) (construct/ask-line SC)
                (construct/blueprint-summary SC) (construct/rock-done-line SC)]]
    (is (string? line))
    (is (not (str/blank? line)))
    (is (not (str/includes? line "kung fu"))))
  (is (string? (construct/worker-name SC)))
  (is (not (str/blank? (construct/worker-name SC)))))

;; Beat 4: the jump program (GDD §2.3, §2.7, §4.8) -- the first pretend jack-out

(def ^:private beat3-done-at
  (+ beat2-done-at construct/line-ms construct/work-ms 500 construct/line-ms))

(defn- sparring-done [world]
  (let [ask-at     (+ beat2-done-at construct/line-ms construct/work-ms)
        approve-at (+ ask-at 500)]
    (-> world
        (construct/answer false ask-at)
        (construct/approve-rock false approve-at))))

(deftest t28-jump-program-nil-before-sparring-is-done
  (let [w (-> SC (construct/enter false T0) sparring-done)]
    (is (nil? (construct/jump-program w false T0)))
    (is (nil? (construct/jump-program w false (dec beat3-done-at))))))

(deftest t29-jump-program-nil-for-a-returning-player-or-before-entered
  (is (nil? (construct/jump-program {} true T0)))
  (is (nil? (construct/jump-program SC false T0))))

(deftest t30-jump-program-nil-bad-now
  (let [w (-> SC (construct/enter false T0) sparring-done)]
    (is (nil? (construct/jump-program w false nil)))
    (is (nil? (construct/jump-program w false "nope")))))

(deftest t31-jump-program-show-then-rings-and-waits-on-jump-out
  (let [w (-> SC (construct/enter false T0) sparring-done)]
    (is (= {:phase :show :line (construct/show-line SC)}
           (construct/jump-program w false beat3-done-at)))
    (is (= {:phase :show :line (construct/show-line SC)}
           (construct/jump-program w false (+ beat3-done-at (dec construct/line-ms)))))
    (is (= {:phase :ring :prompt (construct/ring-line SC)}
           (construct/jump-program w false (+ beat3-done-at construct/line-ms))))
    ;; it waits indefinitely for the player to jack out, however long that takes
    (is (= {:phase :ring :prompt (construct/ring-line SC)}
           (construct/jump-program w false (+ beat3-done-at construct/line-ms 999999))))))

(deftest t32-jump-out-then-waits-on-jump-in
  (let [ring-at (+ beat3-done-at construct/line-ms)
        w       (-> SC (construct/enter false T0) sparring-done
                    (construct/jump-out false ring-at))]
    (is (= {:phase :out :prompt (construct/out-line SC)}
           (construct/jump-program w false ring-at)))
    (is (= {:phase :out :prompt (construct/out-line SC)}
           (construct/jump-program w false (+ ring-at 999999))))))

(deftest t33-jump-in-then-back-then-nil
  (let [ring-at (+ beat3-done-at construct/line-ms)
        in-at   (+ ring-at 500)
        w       (-> SC (construct/enter false T0) sparring-done
                    (construct/jump-out false ring-at)
                    (construct/jump-in false in-at))]
    (is (= {:phase :back :line (construct/back-line SC)}
           (construct/jump-program w false in-at)))
    (is (= {:phase :back :line (construct/back-line SC)}
           (construct/jump-program w false (+ in-at (dec construct/line-ms)))))
    (is (nil? (construct/jump-program w false (+ in-at construct/line-ms))))))

(deftest t34-jump-out-is-a-no-op-once-jumped-out-seen-or-with-a-bad-now
  (let [w (construct/jump-out SC false T0)]
    (is (= T0 (get-in w [:construct :jumped-out-at])))
    (is (= w (construct/jump-out w false (+ T0 999))))
    (is (= SC (construct/jump-out SC true T0)))
    (is (= SC (construct/jump-out SC false nil)))))

(deftest t35-jump-in-requires-jump-out-first
  (is (= SC (construct/jump-in SC false T0)) "no jack-out yet: no-op"))

(deftest t36-jump-in-is-a-no-op-once-jumped-in-seen-or-with-a-bad-now
  (let [out-w (construct/jump-out SC false T0)
        w     (construct/jump-in out-w false (+ T0 10))]
    (is (= (+ T0 10) (get-in w [:construct :jumped-in-at])))
    (is (= w (construct/jump-in w false (+ T0 999))))
    (is (= out-w (construct/jump-in out-w true (+ T0 10))))
    (is (= out-w (construct/jump-in out-w false nil)))))

(deftest t37-jump-program-lines-are-original-not-film-quotes
  (doseq [line [(construct/show-line SC) (construct/ring-line SC) (construct/out-line SC)
                (construct/back-line SC)]]
    (is (string? line))
    (is (not (str/blank? line)))
    (is (not (str/includes? line "kung fu")))))

;; Beat 5: free your mind (GDD §2.7, §4.8) -- the Construct's last beat

(defn- jump-program-done [world]
  (let [ring-at (+ beat3-done-at construct/line-ms)
        in-at   (+ ring-at 500)]
    (-> world
        (construct/jump-out false ring-at)
        (construct/jump-in false in-at))))

(def ^:private beat4-done-at
  (+ beat3-done-at construct/line-ms 500 construct/line-ms))

(deftest t38-free-your-mind-nil-before-jump-program-is-done
  (let [w (-> SC (construct/enter false T0) sparring-done jump-program-done)]
    (is (nil? (construct/free-your-mind w false T0)))
    (is (nil? (construct/free-your-mind w false (dec beat4-done-at))))))

(deftest t39-free-your-mind-nil-for-a-returning-player-or-before-entered
  (is (nil? (construct/free-your-mind {} true T0)))
  (is (nil? (construct/free-your-mind SC false T0))))

(deftest t40-free-your-mind-nil-bad-now
  (let [w (-> SC (construct/enter false T0) sparring-done jump-program-done)]
    (is (nil? (construct/free-your-mind w false nil)))
    (is (nil? (construct/free-your-mind w false "nope")))))

(deftest t41-free-your-mind-dissolves-then-the-twins-line-then-nil
  (let [w (-> SC (construct/enter false T0) sparring-done jump-program-done)]
    (is (= {:phase :dissolve} (construct/free-your-mind w false beat4-done-at)))
    (is (= {:phase :dissolve}
           (construct/free-your-mind w false (+ beat4-done-at (dec construct/dissolve-ms)))))
    (is (= {:phase :line :line (construct/step-back-line SC)}
           (construct/free-your-mind w false (+ beat4-done-at construct/dissolve-ms))))
    (is (= {:phase :line :line (construct/step-back-line SC)}
           (construct/free-your-mind w false (+ beat4-done-at construct/dissolve-ms (dec construct/line-ms)))))
    (is (nil? (construct/free-your-mind w false (+ beat4-done-at construct/dissolve-ms construct/line-ms))))))

(deftest t42-step-back-line-is-the-original-not-a-film-quote
  (is (string? (construct/step-back-line SC)))
  (is (not (str/blank? (construct/step-back-line SC))))
  (is (not (str/includes? (construct/step-back-line SC) "kung fu"))))
