(ns pm.crew-test
  (:require [cljs.test :refer [deftest is]]
            [pm.crew :as c] [pm.motion :as m] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def T0 1794097290232)
(def C {:as-of T0 :agents {:un-automate-monde {:status :working} :le-testeur {:status :blocked}
                           :un-architecte {:status :done}}})
(def P c/default-colors)
(def agents [:un-automate-monde :le-testeur])

(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))

;; Parsing

(deftest t01-parse-real-file
  (is (= {:as-of 1794097290232 :agents {:le-testeur {:status :done} :un-automate-monde {:status :working}}}
         (c/parse "{:as-of 1794097290232, :agents {:le-testeur {:status :done}, :un-automate-monde {:status :working}}}"))))

(deftest t02-parse-herdr-not-running
  (is (= {:as-of 5 :agents {}} (c/parse "{:as-of 5 :agents {}}"))))

(deftest t03-parse-not-edn-or-not-string
  (is (nil? (c/parse "{:as-of")))
  (is (nil? (c/parse "")))
  (is (nil? (c/parse nil)))
  (is (nil? (c/parse 42)))
  (is (nil? (c/parse "#foo/bar 1"))))

(deftest t04-parse-wrong-top-level
  (doseq [text ["[1 2]"
                "{:agents {}}"
                "{:as-of 1.5 :agents {}}"
                "{:as-of -1 :agents {}}"
                "{:as-of \"5\" :agents {}}"
                "{:as-of 5 :agents [1]}"
                "{:as-of 5}"]]
    (is (nil? (c/parse text)))))

(deftest t05-parse-unknown-statuses
  (is (= {:as-of 5 :agents {:a {:status :unknown} :b {:status :unknown} :c {:status :unknown} :d {:status :blocked}}}
         (c/parse "{:as-of 5 :agents {:a {:status :sleeping} :b {:status \"working\"} :c {} :d {:status :blocked}}}"))))

(deftest t06-parse-garbage-entries-dropped
  (is (= {:as-of 5 :agents {:e {:status :done}}}
         (c/parse "{:as-of 5 :host \"x\" :agents {\"s\" {:status :idle} :n 3 :e {:status :done :title \"secret\" :screen \"...\"}}}"))))

(deftest t07-parse-all-five-statuses
  (is (= {:as-of 0 :agents {:a {:status :idle} :b {:status :working} :c {:status :blocked}
                             :d {:status :done} :e {:status :unknown}}}
         (c/parse "{:as-of 0 :agents {:a {:status :idle} :b {:status :working} :c {:status :blocked} :d {:status :done} :e {:status :unknown}}}"))))

;; Freshness

(deftest t08-fresh-edges
  (is (true? (c/fresh? C (+ T0 15000))))
  (is (false? (c/fresh? C (+ T0 15001))))
  (is (true? (c/fresh? C T0)))
  (is (true? (c/fresh? C (- T0 60000))))
  (is (false? (c/fresh? nil T0))))

(deftest t09-constants
  (is (= 15000 c/stale-ms))
  (is (= 2000 c/pulse-ms))
  (is (= #{:idle :working :blocked :done :unknown} c/statuses)))

;; Colours

(deftest t10-colors-from-estate
  (is (= {:working 15905076 :blocked 15026253 :done 4630360 :idle 10133670 :unknown 10133670}
         (c/colors W)))
  (is (= c/default-colors (c/colors {}))))

(deftest t11-colors-validation
  (is (= {:working 255 :blocked 15026253 :done 4630360 :idle 10133670 :unknown 10133670}
         (c/colors {:motion {:status-dots {:working 255 :done -1 :idle "red" :blocked 16777216 :extra 1}}}))))

;; Dots

(deftest t12-dot-one-per-status
  (is (= {:glyph "●" :color 15905076 :pulse? false} (c/dot C :un-automate-monde T0 P)))
  (is (= {:glyph "●" :color 15026253 :pulse? true} (c/dot C :le-testeur T0 P)))
  (is (= {:glyph "●" :color 4630360 :pulse? false} (c/dot C :un-architecte T0 P))))

(deftest t13-dot-idle-and-unknown
  (let [crew {:as-of T0 :agents {:a {:status :idle} :b {:status :unknown}}}]
    (is (= {:glyph "●" :color 10133670 :pulse? false} (c/dot crew :a T0 P)))
    (is (= {:glyph "●" :color 10133670 :pulse? false} (c/dot crew :b T0 P)))))

(deftest t14-dot-missing-agent-hollow
  (is (= {:glyph "○" :color 10133670 :pulse? false} (c/dot C :le-coureur T0 P))))

(deftest t15-dot-stale-all-hollow
  (is (= {:glyph "○" :color 10133670 :pulse? false} (c/dot C :un-automate-monde (+ T0 15001) P)))
  (is (= {:glyph "○" :color 10133670 :pulse? false} (c/dot C :le-testeur (+ T0 15001) P)))
  (is (= {:glyph "●" :color 15905076 :pulse? false} (c/dot C :un-automate-monde (+ T0 15000) P))))

(deftest t16-dot-no-file-hollow
  (is (= {:glyph "○" :color 10133670 :pulse? false} (c/dot nil :un-automate-monde T0 P))))

(deftest t17-dot-palette-from-data
  (let [P2 (assoc P :working 255 :idle 1)]
    (is (= 255 (:color (c/dot C :un-automate-monde T0 P2))))
    (is (= {:glyph "○" :color 1 :pulse? false} (c/dot C :nobody T0 P2)))
    (is (= 10133670 (:color (c/dot C :un-architecte T0 (dissoc P :done)))))))

;; Pulse and labels

(deftest t18-pulse-alpha
  (is (= 1 (c/pulse-alpha 0)))
  (is (= 0.9 (c/pulse-alpha 250)))
  (is (= 0.7 (c/pulse-alpha 500)))
  (is (= 0.4 (c/pulse-alpha 1000)))
  (is (= 0.7 (c/pulse-alpha 1500)))
  (is (= 1 (c/pulse-alpha 2000)))
  (is (= 0.9 (c/pulse-alpha 2250)))
  (is (= 0.4 (c/pulse-alpha 3000))))

(deftest t19-label-text
  (is (= "● Un Automate" (c/label-text (c/dot C :un-automate-monde T0 P) "Un Automate")))
  (is (= "○ Le Testeur" (c/label-text (c/dot C :le-coureur T0 P) "Le Testeur")))
  (is (= "○ Le Testeur" (c/label-text (c/dot nil :le-testeur T0 P) "Le Testeur"))))

(deftest t20-label-runs-working
  (is (= [{:text "● " :color 15905076 :alpha 1} {:text "Un Automate" :color nil :alpha 1}]
         (c/label-runs (c/dot C :un-automate-monde T0 P) "Un Automate" 1000))))

(deftest t21-label-runs-blocked-pulsing
  (is (= [{:text "● " :color 15026253 :alpha 0.4} {:text "Le Testeur" :color nil :alpha 1}]
         (c/label-runs (c/dot C :le-testeur T0 P) "Le Testeur" 1000)))
  (is (= 0.7 (:alpha (first (c/label-runs (c/dot C :le-testeur T0 P) "Le Testeur" 500)))))
  (is (= (c/label-runs (c/dot C :le-testeur T0 P) "Le Testeur" 0)
         (c/label-runs (c/dot C :le-testeur T0 P) "Le Testeur" 2000))))

(deftest t22-label-runs-hollow
  (is (= [{:text "○ " :color 10133670 :alpha 1} {:text "Le Testeur" :color nil :alpha 1}]
         (c/label-runs (c/dot C :x T0 P) "Le Testeur" 0))))

;; HUD and walkers

(deftest t23-hud-text
  (is (= "crew: 2/2 live" (c/hud-text C agents T0)))
  (is (= "crew: 1/2 live" (c/hud-text (update C :agents dissoc :le-testeur) agents T0)))
  (is (= "crew: 0/2 live" (c/hud-text {:as-of T0 :agents {}} agents T0))))

(deftest t24-hud-text-stale-missing-no-walkers
  (is (= "crew: stale" (c/hud-text C agents (+ T0 15001))))
  (is (= "crew: no status" (c/hud-text nil agents T0)))
  (is (nil? (c/hud-text C [] T0))))

(deftest t25-live-count
  (is (= 2 (c/live-count C agents T0)))
  (is (= 2 (c/live-count C [:un-automate-monde :un-automate-monde :x] T0)))
  (is (= 0 (c/live-count C agents (+ T0 20000)))))

(deftest t26-walker-agent
  (is (= [:un-automate-monde :le-testeur] (mapv :agent (m/walkers W))))
  (is (= :a (:agent (m/walker {:id :a :route [[0 0]]}))))
  (is (= :b (:agent (m/walker {:id :a :route [[0 0]] :agent :b}))))
  (is (= :a (:agent (m/walker {:id :a :route [[0 0]] :agent "b"})))))

(deftest t27-estate-walkers-end-to-end
  (let [S (c/parse "{:as-of 1000 :agents {:un-automate-monde {:status :working} :le-testeur {:status :idle}}}")
        colors (c/colors W)]
    (is (= ["● Un Automate" "● Le Testeur"]
           (mapv (fn [wk] (c/label-text (c/dot S (:agent wk) 3000 colors) (:name wk))) (m/walkers W))))
    (is (= ["○ Un Automate" "○ Le Testeur"]
           (mapv (fn [wk] (c/label-text (c/dot S (:agent wk) 16001 colors) (:name wk))) (m/walkers W))))
    (is (= [15905076 10133670]
           (mapv (fn [wk] (:color (c/dot S (:agent wk) 3000 colors))) (m/walkers W))))))

(deftest t28-keep-the-last-good-file
  (let [keep-good (fn [prev text] (or (c/parse text) prev))]
    (is (= C (keep-good C "garbage{")))
    (is (= C (keep-good C nil)))
    (is (= {:as-of 9 :agents {}} (keep-good C "{:as-of 9 :agents {}}")))
    (is (= {:glyph "○" :color 10133670 :pulse? false}
           (c/dot (keep-good C "x") :un-automate-monde (+ T0 15001) P)))))
