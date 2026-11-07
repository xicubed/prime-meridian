(ns pm.talk-test
  (:require [cljs.test :refer [deftest is]]
            [pm.talk :as tk]
            [pm.world :as w]
            [pm.content :as c]
            [pm.fixtures :as fx]))

(def A    1794013200000)        ; Fri 2026-11-06 17:00 PST = day 20764, 01:00 UTC
(def D0   1794009600000)        ; 00:00 UTC that day
(def D2   172800000)
(def H12  43200000)
(def NEXT (+ A 82800000))       ; 1794096000000, first ms of day 20765
(def W  (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))  ; "I'm hungry" / "Hay, please?"
(def WH (assoc W :hal {:event :jack-in :text "Welcome back." :until (+ A 6000)}))
(def T0 tk/empty-talk)
(def T3 (tk/observe T0 WH A))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn hal [text] {:who "Hal" :text text :kind :hal})
(defn texts [t] (mapv :text (:entries t)))
(defn appends [k] (reduce (fn [t i] (tk/append t (hal (str "l" i)) (+ A i))) T0 (range 1 (inc k))))
(def H {:jack-in ["hi" "hello" "hey"] :chore-done ["{name}: {chore} done" "did {chore} for {name}"]})
(def E1 {:n 1 :who "Hal"     :text "Welcome back." :at A :kind :hal :event :jack-in
         :show-at A :show-until (+ A 2500)})
(def E2 {:n 2 :who "Flora"   :text "I'm hungry"    :at A :kind :creature :mirror :flora
         :asks :feed :done? false
         :show-at (+ A 2500) :show-until (+ A 5000)})
(def E3 {:n 3 :who "Juniper" :text "Hay, please?"  :at A :kind :creature :mirror :juniper
         :asks :hay :done? false
         :show-at (+ A 5000) :show-until (+ A 7500)})
(defn bare [e] (dissoc e :show-at :show-until))

;; The talk log

(deftest t01-caption-ms
  (is (= (tk/caption-ms "Welcome back.") 2500))
  (is (= (tk/caption-ms "You're jacked in. Let's get going.") 3240))
  (is (= (tk/caption-ms (apply str (repeat 21 "a"))) 2500))
  (is (= (tk/caption-ms (apply str (repeat 22 "a"))) 2520))
  (is (= (tk/caption-ms (apply str (repeat 30 "a"))) 3000))
  (is (= (tk/caption-ms (apply str (repeat 130 "a"))) 9000))
  (is (= (tk/caption-ms (apply str (repeat 200 "a"))) 9000))
  (is (= (tk/caption-ms "") 2500))
  (is (= (tk/caption-ms nil) 2500)))

(deftest t02-say-text-both-mirrors
  (is (= (tk/say-text W :flora A) "I'm hungry"))
  (is (= (tk/say-text W :juniper A) "Hay, please?"))
  (is (= (tk/say-text (w/chore-done W (ev :flora :feed A :key)) :flora (+ A 1)) ""))
  (is (= (tk/say-text W :rock A) "")))

(deftest t03-append
  (let [t1 (tk/append T0 (hal "Welcome back.") A)]
    (is (= t1 (assoc T0 :entries [(dissoc E1 :mirror :event)] :n 1 :queue-end (+ A 2500))))
    (is (= (tk/append T0 (assoc (hal "Welcome back.") :event :jack-in) A)
           (assoc T0 :entries [E1] :n 1 :queue-end (+ A 2500))))
    (let [t2 (tk/append t1 (hal "You're jacked in. Let's get going.") (+ A 1000))]
      (is (= (peek (:entries t2))
             {:n 2 :who "Hal" :text "You're jacked in. Let's get going." :at (+ A 1000) :kind :hal
              :show-at (+ A 2500) :show-until (+ A 5740)}))
      (is (= (:queue-end t2) (+ A 5740)))
      (let [t3 (tk/append t2 (hal "Got it.") (+ A 10000))]
        (is (= (:show-at (peek (:entries t3))) (+ A 10000)))
        (is (= (:show-until (peek (:entries t3))) (+ A 12500)))))))

(deftest t04-append-edges
  (is (= (tk/append T0 {:who "" :text "x" :kind :hal} A) T0))
  (is (= (tk/append T0 {:who nil :text "x"} A) T0))
  (is (= (tk/append T0 {:who "Hal" :text "  "} A) T0))
  (is (= (tk/append T0 (hal "x") nil) T0))
  (is (= (tk/append T0 (hal "x") ##NaN) T0))
  (is (= (:kind (peek (:entries (tk/append T0 {:who "Hal" :text "x" :kind "hal"} A)))) :other))
  (is (= (contains? (peek (:entries (tk/append T0 {:who "F" :text "x" :kind :creature :mirror "flora"} A))) :mirror) false))
  (is (= (:n (tk/append nil (hal "x") A)) 1)))

(deftest t05-cap
  (let [TC (appends 105)]
    (is (= (count (:entries TC)) 100))
    (is (true? (vector? (:entries TC))))
    (is (= (:n TC) 105))
    (is (= (:n (first (:entries TC))) 6))
    (is (= (:text (first (:entries TC))) "l6"))
    (is (= (:n (peek (:entries TC))) 105))
    (is (= (:text (peek (:entries TC))) "l105"))
    (is (= tk/cap 100))))

(deftest t06-observe-first-frame-hal-and-both-mirrors
  (is (= (:entries T3) [E1 E2 E3]))
  (is (= (:last-hal T3) ["Welcome back." 1794013206000]))
  (is (= (:last-say T3) {:flora "I'm hungry" :juniper "Hay, please?"}))
  (is (= (:n T3) 3))
  (is (= (:queue-end T3) (+ A 7500)))
  (is (= (:seen T3) 0))
  (is (= (texts (tk/observe T0 W A)) ["I'm hungry" "Hay, please?"])))

(deftest t07-logged-once-not-per-frame
  (is (true? (identical? T3 (tk/observe T3 WH (+ A 16)))))
  (is (true? (identical? T3 (reduce (fn [t i] (tk/observe t WH (+ A (* 16 i)))) T3 (range 60)))))
  (is (= (count (:entries (reduce (fn [t i] (tk/observe t WH (+ A (* 16 i)))) T0 (range 60)))) 3)))

(deftest t08-hal-new-lines
  (is (true? (identical? T3 (tk/observe T3 WH (+ A 6000)))))
  (is (= (texts (tk/observe T0 WH (+ A 6000))) ["I'm hungry" "Hay, please?"]))
  (let [WH2 (assoc W :hal {:event :chore-done :text "Got it." :until (+ A 20000)})
        T4 (tk/observe T3 WH2 (+ A 14000))]
    (is (= (peek (:entries T4))
           {:n 4 :who "Hal" :text "Got it." :at (+ A 14000) :kind :hal :event :chore-done :show-at (+ A 14000) :show-until (+ A 16500)}))
    (is (true? (identical? T4 (tk/observe T4 WH2 (+ A 15000)))))
    (let [T5 (tk/observe T4 (assoc W :hal {:event :chore-done :text "Got it." :until (+ A 30000)}) (+ A 25000))]
      (is (= (count (:entries T5)) 5))
      (is (= (:n (peek (:entries T5))) 5))
      (is (= (:text (peek (:entries T5))) "Got it."))
      (is (= (:show-at (peek (:entries T5))) (+ A 25000)))))
  (is (= (texts (tk/observe T0 (assoc W :hal {:text nil :until (+ A 9000)}) A)) ["I'm hungry" "Hay, please?"])))

(deftest t09-flora-bubble-changes
  (let [F1 (w/chore-done WH (ev :flora :feed (+ A 1000) :key))
        T5 (tk/observe T3 F1 (+ A 1000))]
    (is (= (count (:entries T5)) 3))
    (is (= (:last-say T5) {:flora "" :juniper "Hay, please?"}))
    (is (= (identical? T3 T5) false))
    (let [T6 (tk/observe T5 F1 (+ A D2 1000))]
      (is (= (count (:entries T6)) 4))
      (is (= (peek (:entries T6))
             {:n 4 :who "Flora" :text "I'm hungry" :at 1794186001000 :kind :creature :mirror :flora
              :asks :feed :done? false
              :show-at 1794186001000 :show-until 1794186003500}))))
  (is (true? (identical? T3 (tk/observe T3 (assoc-in WH [:mirrors :flora :state :stock] 2) (+ A 16))))))

(deftest t10-juniper-bubble-changes
  (let [J1 (w/chore-done WH (ev :juniper :hay (+ A 2000) :key))
        T7 (tk/observe T3 J1 (+ A 2000))]
    (is (= (count (:entries T7)) 3))
    (is (= (get-in T7 [:last-say :juniper]) ""))
    (is (= (peek (:entries (tk/observe T7 J1 (+ A 2000 H12))))
           {:n 4 :who "Juniper" :text "Hay, please?" :at 1794056402000 :kind :creature :mirror :juniper
            :asks :hay :done? false
            :show-at 1794056402000 :show-until 1794056404500}))))

(deftest t11-day-rollover-both-mirrors
  (let [WL (assoc W :lines {:mirrors {:flora {:hungry ["h0" "h1" "h2" "h3" "h4"]} :juniper {:hungry ["hay0" "hay1" "hay2"]}}})
        TL (tk/observe T0 WL A)]
    (is (= (texts TL) ["h1" "hay1"]))
    (is (true? (identical? TL (tk/observe TL WL (+ A 82799999)))))
    (let [TL2 (tk/observe TL WL NEXT)]
      (is (= (texts TL2) ["h1" "hay1" "h2" "hay2"]))
      (is (= (mapv :who (:entries TL2)) ["Flora" "Juniper" "Flora" "Juniper"]))
      (is (= (:show-at (nth (:entries TL2) 2)) 1794096000000))
      (is (= (:show-until (nth (:entries TL2) 2)) 1794096002500))
      (is (= (:show-at (nth (:entries TL2) 3)) 1794096002500))
      (is (= (:show-until (nth (:entries TL2) 3)) 1794096005000)))))

(deftest t12-generic-mirror
  (let [G (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1}
                                              :rules [{:when [:< :water 5] :say "thirsty"}
                                                       {:when [:< :water 5] :say "dry {water}" :sign "x"}
                                                       {:when [:< :water 5] :sign "only a sign"}]}]}))]
    (is (= (tk/say-text G :fern A) "thirsty · dry 1"))
    (is (= (:entries (tk/observe T0 G A))
           [{:n 1 :who "fern" :text "thirsty · dry 1" :at A :kind :creature :mirror :fern
             :show-at A :show-until (+ A 2500)}]))
    (let [G2 (w/load-estate (pr-str {:mirrors [{:id :fern :name "Fern" :kind :plant :state {:water 1}
                                                 :rules [{:when [:< :water 5] :say "thirsty"}]}]}))]
      (is (= (:who (peek (:entries (tk/observe T0 G2 A)))) "Fern"))
      (is (= (:kind (peek (:entries (tk/observe T0 G2 A)))) :plant)))
    (is (= (tk/say-text (assoc-in G [:mirrors :fern :state :water] 9) :fern A) ""))))

;; Caption, panel and keys

(deftest t13-caption-schedule
  (is (= (tk/caption T3 A) E1))
  (is (= (tk/caption T3 (+ A 2499)) E1))
  (is (= (tk/caption T3 (+ A 2500)) E2))
  (is (= (tk/caption T3 (+ A 4999)) E2))
  (is (= (tk/caption T3 (+ A 5000)) E3))
  (is (= (tk/caption T3 (+ A 7499)) E3))
  (is (= (tk/caption T3 (+ A 7500)) nil))
  (is (= (tk/caption T3 (- A 1)) nil))
  (is (= (tk/caption T0 A) nil))
  (is (= (tk/caption nil A) nil))
  (is (= (tk/caption T3 nil) nil)))

(deftest t14-caption-text
  (is (= (tk/caption-text E1) "Hal — Welcome back."))
  (is (= (tk/caption-text E2) "Flora — I'm hungry"))
  (is (= (tk/caption-text nil) nil)))

(deftest t15-unread-open-close
  (is (= (tk/unread T3) 3))
  (is (= (tk/badge-text T3) "3"))
  (let [O (tk/open-panel T3)]
    (is (= (:open? O) true))
    (is (= (:seen O) 3))
    (is (= (tk/unread O) 0))
    (is (= (tk/badge-text O) ""))
    (let [O2 (tk/append O (hal "x") (+ A 9000))]
      (is (= (:seen O2) 4))
      (is (= (tk/unread O2) 0))
      (let [C (tk/close-panel O2)]
        (is (= (:open? C) false))
        (is (= (:seen C) 4))
        (is (= (tk/unread C) 0))
        (let [C2 (tk/append C (hal "y") (+ A 9500))]
          (is (= (tk/unread C2) 1))
          (is (= (tk/badge-text C2) "1"))))))
  (is (= (tk/unread T0) 0))
  (is (= (tk/badge-text T0) ""))
  (is (= (tk/unread (assoc T3 :open? true)) 0)))

(deftest t16-badge-cap
  (is (= (tk/badge-text (appends 99)) "99"))
  (is (= (tk/unread (appends 120)) 100))
  (is (= (tk/badge-text (appends 120)) "99+")))

(deftest t17-toggles
  (is (= (:voice? T0) false))
  (is (= (:voice? (tk/toggle-voice T0)) true))
  (is (= (:voice? (tk/toggle-voice (tk/toggle-voice T0))) false))
  (is (= (tk/toggle-panel T3) (tk/open-panel T3)))
  (is (= (tk/toggle-panel (tk/open-panel T3)) (tk/close-panel (tk/open-panel T3)))))

(deftest t18-close-key
  (is (= (tk/close-key? "Escape" true "DIV") true))
  (is (= (tk/close-key? "Escape" true nil) true))
  (is (= (tk/close-key? "Escape" false "DIV") false))
  (is (= (tk/close-key? "Escape" true "INPUT") false))
  (is (= (tk/close-key? "Escape" true "TEXTAREA") false))
  (is (= (tk/close-key? "Escape" true "SELECT") false))
  (is (= (tk/close-key? "f" true "DIV") false))
  (is (= (tk/close-key? "Esc" true nil) false)))

(deftest t19-clock-text
  (is (= (tk/clock-text A -480) "5:00 PM"))
  (is (= (tk/clock-text (+ A (* 42 60000)) -480) "5:42 PM"))
  (is (= (tk/clock-text A -420) "6:00 PM"))
  (is (= (tk/clock-text A 0) "1:00 AM"))
  (is (= (tk/clock-text D0 0) "12:00 AM"))
  (is (= (tk/clock-text (+ D0 300000) 0) "12:05 AM"))
  (is (= (tk/clock-text (+ D0 43200000) 0) "12:00 PM"))
  (is (= (tk/clock-text (+ D0 86340000) 0) "11:59 PM"))
  (is (= (tk/clock-text D0 -480) "4:00 PM"))
  (is (= (tk/clock-text A nil) "1:00 AM"))
  (is (= (tk/clock-text nil -480) ""))
  (is (= (tk/clock-text ##NaN 0) "")))

;; Voices

(deftest t20-voices-from-estate
  (is (= (tk/voice-for W {:kind :hal}) {:rate 1 :pitch 0.9 :lang "en-US"}))
  (is (= (tk/voice-for W E2) {:rate 1.1 :pitch 1.6 :lang "en-US"}))
  (is (= (tk/voice-for W E3) {:rate 0.9 :pitch 0.8 :lang "en-US"}))
  (is (= (tk/voice-for W {:kind :other}) {:rate 1 :pitch 1 :lang "en-US"}))
  (is (= (tk/voice-for {} nil) {:rate 1 :pitch 1 :lang "en-US"}))
  (is (= (tk/voice-for nil {:kind :hal}) {:rate 1 :pitch 1 :lang "en-US"}))
  (is (= (not= (tk/voice-for W E1) (tk/voice-for W E2)) true))
  (is (= (tk/voice-for (assoc-in W [:mirrors :pony] (assoc (get-in W [:mirrors :juniper]) :id :pony))
                       {:kind :creature :mirror :pony})
         (tk/voice-for W E3))))

(deftest t21-voice-validation
  (is (= (tk/voice-for {:voices {:hal {:rate 50 :pitch -1 :lang ""}}} {:kind :hal})
         {:rate 1 :pitch 1 :lang "en-US"}))
  (is (= (tk/voice-for {:voices {:hal {:rate 0.5 :pitch "hi" :lang "fr-FR"}}} {:kind :hal})
         {:rate 0.5 :pitch 1 :lang "fr-FR"}))
  (is (= (tk/voice-for {:voices {:hal {:rate 0.1 :pitch 2}}} {:kind :hal})
         {:rate 0.1 :pitch 2 :lang "en-US"}))
  (is (= (tk/voice-for {:voices {:hal {:rate 0.09 :pitch 2.01}}} {:kind :hal})
         {:rate 1 :pitch 1 :lang "en-US"}))
  (is (= (tk/voice-for {:voices {:creature {:pitch 1.5}} :mirrors {:x {:voice {:pitch 9 :rate 2}}}}
                       {:kind :creature :mirror :x})
         {:rate 2 :pitch 1.5 :lang "en-US"})))

;; History across reloads

(deftest t22-save-restore-roundtrip
  (is (true? (string? (tk/save-text T3))))
  (let [R (tk/restore (tk/save-text T3))]
    (is (= R (assoc T0 :entries [(bare E1) (bare E2) (bare E3)] :n 3 :seen 0
                    :last-say {:flora "I'm hungry" :juniper "Hay, please?"})))
    (is (= (tk/unread R) 3))
    (is (= (tk/caption R A) nil))
    ;; a reload is not a new visit: the greeting and the standing bubbles aren't logged again
    (let [RO (tk/observe R WH (+ A 100))]
      (is (= (count (:entries RO)) 3))
      (is (= (tk/caption RO (+ A 100)) nil))))
  (let [R2 (tk/restore (tk/save-text (tk/open-panel T3)))]
    (is (= (:seen R2) 3))
    (is (= (:open? R2) false))
    (is (= (tk/unread R2) 0)))
  (is (= (:voice? (tk/restore (tk/save-text (tk/toggle-voice T3)))) false))
  (is (= (tk/restore (tk/save-text T0)) T0)))

(deftest t23-restore-garbage
  (doseq [x [nil 7 "" "{" "[1 2]"
             "{:v 2 :n 3}" "{:v 1}" "{:v 1 :entries \"x\"}"
             (pr-str {:n 3 :entries []})]]
    (is (= (tk/restore x) tk/empty-talk))))

(deftest t24-restore-drops-bad-entries
  (let [P (pr-str {:v 1 :n 9 :seen 4
                   :entries [{:n 2 :who "Hal" :text "hi" :at 5 :kind :hal}
                             {:n 3 :who "" :text "x" :at 5 :kind :hal}
                             {:n 4 :who "Flora" :text "yo" :at "noon" :kind :creature}
                             "junk"
                             {:n 5 :who "Flora" :text "yo" :at 7 :kind :creature :mirror "flora"}
                             {:n 1 :who "Hal" :text "first" :at 1 :kind :hal}
                             {:n 0 :who "Hal" :text "zero" :at 1 :kind :hal}
                             {:n 6 :who "Hal" :text "k" :at 1 :kind "hal"}
                             {:n 7 :who "Hal" :text "extra" :at 1 :kind :hal :show-at 1 :show-until 99}]})]
    (is (= (:entries (tk/restore P))
           [{:n 1 :who "Hal" :text "first" :at 1 :kind :hal}
            {:n 2 :who "Hal" :text "hi" :at 5 :kind :hal}
            {:n 5 :who "Flora" :text "yo" :at 7 :kind :creature}
            {:n 7 :who "Hal" :text "extra" :at 1 :kind :hal}]))
    (is (= (:n (tk/restore P)) 9))
    (is (= (:seen (tk/restore P)) 4))
    (is (= (tk/unread (tk/restore P)) 2))
    (is (= (:n (tk/append (tk/restore P) (hal "next") A)) 10))))

(deftest t25-restore-counters-and-cap
  (let [one [{:n 3 :who "Hal" :text "a" :at 1 :kind :hal}]]
    (let [r (tk/restore (pr-str {:v 1 :n 1 :seen 0 :entries one}))]
      (is (= (:n r) 3))
      (is (= (:seen r) 0)))
    (is (= (:seen (tk/restore (pr-str {:v 1 :n 1 :seen 50 :entries one}))) 3))
    (is (= (:seen (tk/restore (pr-str {:v 1 :n 1 :seen -2 :entries one}))) 0))
    (is (= (:seen (tk/restore (pr-str {:v 1 :n 1 :seen "x" :entries one}))) 3))
    (is (= (:n (tk/restore (pr-str {:v 1 :n "x" :seen 0 :entries one}))) 3)))
  (let [many (mapv (fn [i] {:n i :who "Hal" :text (str "e" i) :at i :kind :hal}) (range 1 151))
        R (tk/restore (pr-str {:v 1 :n 1 :seen 0 :entries many}))]
    (is (= (count (:entries R)) 100))
    (is (= (:n (first (:entries R))) 51))
    (is (= (:n R) 150))
    (is (= (tk/unread R) 100))))

;; Wired to the content round

(deftest t26-end-to-end-with-hal-lines
  (let [entries (:entries (tk/observe T0 (c/hal-say W H :jack-in {} A) A))]
    (is (= (:who (first entries)) "Hal"))
    (is (= (:text (first entries)) "hello"))
    (is (= (mapv :text (rest entries)) ["I'm hungry" "Hay, please?"])))
  (let [T (tk/observe T0 W A)
        K1 (c/hal-on-chore (w/chore-done W (ev :flora :feed (+ A 1000) :key)) H)
        TK (tk/observe T K1 (+ A 1000))]
    (is (= (texts TK) ["I'm hungry" "Hay, please?" "did feed for Flora"]))
    (is (= (peek (:entries TK))
           {:n 3 :who "Hal" :text "did feed for Flora" :at (+ A 1000) :kind :hal :event :chore-done
            :show-at (+ A 5000) :show-until (+ A 7500)}))
    (is (= (:last-say TK) {:flora "" :juniper "Hay, please?"}))
    (is (true? (identical? TK (tk/observe TK K1 (+ A 1016)))))))

;; Reloads (Michael: "why'd I get duplicate messages?")

(deftest t27-reload-doesnt-repeat-the-scene
  (let [R  (tk/restore (tk/save-text T3))
        ;; reload 10 minutes later: Hal greets again (maybe another variant), bubbles unchanged
        WR (assoc W :hal {:event :jack-in :text "You're in." :until (+ A 600000 6000)})
        RO (tk/observe R WR (+ A 600000))]
    (is (= (texts RO) ["Welcome back." "I'm hungry" "Hay, please?"])))
  (let [R  (tk/restore (tk/save-text T3))
        ;; back the next day: a new visit, so Hal greets and it's logged
        WR (assoc W :hal {:event :jack-in :text "You're in." :until (+ NEXT 6000)})]
    (is (= (last (texts (tk/observe R WR NEXT))) "You're in.")))
  (let [R  (tk/restore (tk/save-text T3))
        ;; a creature's bubble that changed since is still logged
        WF (assoc-in W [:mirrors :juniper :state :last-hayed] A)]
    (is (= (texts (tk/observe R WF (+ A 1000))) ["Welcome back." "I'm hungry" "Hay, please?"]))))

(deftest t28-restore-cleans-old-repeats
  (let [dup (fn [n at text who mirror]
              (cond-> {:n n :who who :text text :at at :kind (if mirror :creature :hal)}
                mirror (assoc :mirror mirror)))
        old (pr-str {:v 1 :n 6 :seen 6
                     :entries [(dup 1 A "You're jacked in." "Hal" nil)
                               (dup 2 A "Feels like hay time" "Juniper" :juniper)
                               (dup 3 (+ A 60000) "You're jacked in." "Hal" nil)
                               (dup 4 (+ A 60000) "Feels like hay time" "Juniper" :juniper)
                               (dup 5 (+ A 7200000) "Feels like hay time" "Juniper" :juniper)
                               (dup 6 (+ A 7300000) "Hay, please?" "Juniper" :juniper)]})
        R (tk/restore old)]
    (is (= (mapv :n (:entries R)) [1 2 5 6]))
    (is (= (:last-say R) {:juniper "Hay, please?"}))))
