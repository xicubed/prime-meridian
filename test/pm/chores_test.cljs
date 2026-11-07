(ns pm.chores-test
  (:require [cljs.test :refer [deftest is]]
            [pm.chores :as ch]
            [pm.pick :as pk]
            [pm.talk :as tk]
            [pm.world :as w]
            [pm.content :as c]
            [pm.overlay :as o]
            [pm.plan :as p]
            [pm.fixtures :as fx]))

(def A   1794013200000)          ; Fri 2026-11-06 17:00 PST (day 20764)
(def T1  1794024960000)          ; A + 3h16m = 8:16 PM PST (day 20764)
(def D2  172800000)
(def H12 43200000)
(def OFF -480)                   ; PST
(def W (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(def H {:chore-done    ["{name}: {chore} done" "did {chore} for {name}"]
        :chore-not-due ["last {last-at}, next {next-at}"]
        :chore-undone  ["undid {name}'s {chore}" "took back {chore}"]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn st [world id] (get-in world [:mirrors id :state]))
(defn e1 [world] (tk/entry (:talk world) 1))
(def RJ {:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at T1 :prev-at nil :consumed [:bales 1]})
(def C1 (ch/click W H :juniper T1 OFF))                    ; Juniper hayed by a click at 8:16 PM
(def U  (ch/untick C1 H 1 (+ T1 120000)))                 ; ...and unticked at 8:18 PM
(def FX (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})))
(def WF (o/apply-file W {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}}))  ; Hal keeps Flora

;; World: records, next-due, undo

(deftest t01-event-id
  (is (= (w/event-id {:mirror :juniper :chore :hay :at T1}) "juniper/hay@1794024960000"))
  (is (= (w/event-id {:mirror nil :chore :hay :at T1}) nil))
  (is (= (w/event-id {:mirror :juniper :chore :hay :at nil}) nil)))

(deftest t02-counted-entry-records-undo
  (is (= (peek (:log C1))
         {:event :chore-done :mirror :juniper :chore :hay :at T1 :via :click :who :me
          :result :counted :id "juniper/hay@1794024960000" :prev-at nil :consumed [:bales 1]}))
  (is (= (peek (:log (w/chore-done C1 (ev :juniper :hay (+ T1 1000) :click))))
         (assoc (ev :juniper :hay (+ T1 1000) :click) :result :ignored :reason :not-due)))
  (is (= (:consumed (peek (:log (w/chore-done (assoc-in W [:mirrors :juniper :state :bales] 0) (ev :juniper :hay T1 :click)))))
         [:bales 0]))
  (is (= (contains? (peek (:log (w/chore-done FX (ev :fern :mist A :click)))) :consumed) false))
  (is (= (:prev-at (peek (:log (w/chore-done WF (ev :flora :feed (+ A D2) :click)))))
         1794013200000)))

(deftest t03-next-due-and-last-done
  (is (= (w/next-due W :juniper :hay) nil))
  (is (= (w/last-done-at W :juniper :hay) nil))
  (is (= (w/next-due C1 :juniper :hay) 1794068160000))
  (is (= (w/last-done-at C1 :juniper :hay) T1))
  (is (= (w/next-due WF :flora :feed) 1794186000000))
  (is (= (w/due? C1 :juniper :hay 1794068159999) false))
  (is (= (w/due? C1 :juniper :hay 1794068160000) true))
  (is (= (w/next-due W :rex :feed) nil))
  (is (= (w/next-due W :juniper :nope) nil))
  (is (= (w/next-due (assoc-in W [:mirrors :juniper :chores 0 :every] "bad") :juniper :hay) nil))
  (is (= (w/next-due (assoc-in (assoc-in W [:mirrors :juniper :chores 0 :every] "bad")
                                [:mirrors :juniper :state :last-hayed] T1)
                      :juniper :hay)
         nil)))

;; Click

(deftest t04-chore-at-click
  (is (= (ch/chore-at-click W :juniper T1)
         {:due? true :mirror :juniper :chore :hay
          :event {:event :chore-done :mirror :juniper :chore :hay :at T1 :via :click :who :me}}))
  (is (= (ch/chore-at-click C1 :juniper (+ T1 60000))
         {:due? false :mirror :juniper :chore :hay :last-at 1794024960000 :next-at 1794068160000}))
  (is (= (:due? (ch/chore-at-click C1 :juniper 1794068159999)) false))
  (is (= (:due? (ch/chore-at-click C1 :juniper 1794068160000)) true))
  (is (= (ch/chore-at-click W :rex T1) nil))
  (is (= (ch/chore-at-click (w/load-estate "{:mirrors [{:id :stone :state {}}]}") :stone T1) nil)))

(deftest t05-first-due-chore-wins
  (let [M (w/chore-done (w/load-estate (pr-str {:mirrors [{:id :fern :state {}
                                                            :chores [{:id :mist :every "90m"} {:id :repot :every "1w"}]}]}))
                        (ev :fern :mist A :click))]
    (is (= (:chore (ch/chore-at-click M :fern (+ A 1000))) :repot))
    (is (= (ch/chore-at-click (w/chore-done M (ev :fern :repot (+ A 1000) :click)) :fern (+ A 2000))
           {:due? false :mirror :fern :chore :mist :last-at A :next-at 1794018600000}))))

(deftest t06-click-juniper-when-due
  (is (= (st C1 :juniper) {:source :sample :bales 5 :last-hayed 1794024960000}))
  (is (= (count (:log C1)) 1))
  (is (= (:hal C1) {:event :chore-done :text "did hay for Juniper" :until 1794024966000}))
  (is (= (:entries (:talk C1))
         [{:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper :chore RJ :done? true}]))
  (is (= (w/markers C1 :juniper (+ T1 1000)) []))
  (is (= (w/markers C1 :flora T1) [{:say "I'm hungry"}])))

(deftest t07-click-again-not-due-with-times
  (let [C2 (ch/click C1 H :juniper (+ T1 60000) OFF)]
    (is (= (:hal C2) {:event :chore-not-due :text "last 8:16 PM, next 8:16 AM" :until 1794025026000}))
    (is (= (:log C2) (:log C1)))
    (is (= (:mirrors C2) (:mirrors C1)))
    (is (= (:talk C2) (:talk C1))))
  (is (= (ch/click C1 H :juniper nil OFF) C1))
  (is (= (ch/click W H :rex T1 OFF) W)))

(deftest t08-click-flora-the-second-mirror
  (let [FC (ch/click W H :flora T1 OFF)]
    (is (= (st FC :flora) {:source :sample :stock 14 :last-fed 1794024960000}))
    (is (= (:text (:hal FC)) "did feed for Flora"))
    (is (= (:text (e1 FC)) "Flora's feed"))
    (is (= (:chore (e1 FC))
           {:id "flora/feed@1794024960000" :mirror :flora :chore :feed :at T1 :prev-at nil :consumed [:stock 1]}))
    (is (= (:text (:hal (ch/click FC H :flora (+ T1 60000) OFF))) "last 8:16 PM, next 8:16 PM"))
    (is (= (w/markers FC :juniper T1) [{:say "Hay, please?"}]))))

(deftest t09-hal-kept-flora-undo-restores-hal's-values
  (is (= (:text (:hal (ch/click WF H :flora T1 OFF))) "last 5:00 PM, next 5:00 PM"))
  (let [FD (ch/click WF H :flora (+ A D2) OFF)]
    (is (= (st FD :flora) {:source :hal :stock 36 :last-fed 1794186000000}))
    (is (= (:text (:hal FD)) "did feed for Flora"))
    (is (= (:chore (e1 FD))
           {:id "flora/feed@1794186000000" :mirror :flora :chore :feed :at 1794186000000 :prev-at 1794013200000 :consumed [:stock 1]}))
    (let [FU (ch/untick FD H 1 (+ A D2 1000))]
      (is (= (st FU :flora) {:source :hal :stock 37 :last-fed 1794013200000}))
      (is (= (:text (:hal FU)) "undid Flora's feed")))))

;; Untick and tick

(deftest t10-untick-restores-done-time-and-bales
  (is (= (st U :juniper) {:source :sample :bales 6 :last-hayed nil}))
  (is (= (get-in U [:mirrors :juniper]) (get-in W [:mirrors :juniper])))
  (is (= (peek (:log U))
         {:event :chore-undone :mirror :juniper :chore :hay :at 1794025080000 :id "juniper/hay@1794024960000" :result :undone}))
  (is (= (:hal U) {:event :chore-undone :text "undid Juniper's hay" :until 1794025086000}))
  (is (= (e1 U) (assoc (e1 C1) :done? false)))
  (is (= (count (:entries (:talk U))) 1))
  (is (= (w/markers U :juniper (+ T1 120000)) [{:say "Hay, please?"}])))

(deftest t11-tick-again-redoes-when-due
  (let [R (ch/tick U H 1 (+ T1 180000) OFF)]
    (is (= (peek (:log R))
           {:event :chore-done :mirror :juniper :chore :hay :at 1794025140000 :via :tick :who :me
            :result :counted :id "juniper/hay@1794025140000" :prev-at nil :consumed [:bales 1]}))
    (is (= (st R :juniper) {:source :sample :bales 5 :last-hayed 1794025140000}))
    (is (= (:text (:hal R)) "did hay for Juniper"))
    (is (= (e1 R)
           {:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper
            :chore {:id "juniper/hay@1794025140000" :mirror :juniper :chore :hay :at 1794025140000 :prev-at nil :consumed [:bales 1]}
            :done? true}))
    (is (= (count (:entries (:talk R))) 1))
    (is (= (tk/chore-label (e1 R) OFF) "Juniper's hay · 8:19 PM")))
  (is (= (:via (peek (:log (w/redo-chore U RJ (+ T1 180000))))) :tick)))

(deftest t12-tick-when-not-due-stays-unticked
  (let [U2 (ch/click U H :juniper (+ T1 121000) OFF)]
    (is (= (count (:entries (:talk U2))) 1))
    (is (= (:done? (e1 U2)) true))
    (is (= (:chore (e1 U2))
           {:id "juniper/hay@1794025081000" :mirror :juniper :chore :hay :at 1794025081000 :prev-at nil :consumed [:bales 1]})))
  (let [X0 (w/chore-done U (ev :juniper :hay (+ T1 121000) :click))
        X (ch/tick X0 H 1 (+ T1 130000) OFF)]
    (is (= (:hal X) {:event :chore-not-due :text "last 8:18 PM, next 8:18 AM" :until 1794025096000}))
    (is (= (:done? (e1 X)) false))
    (is (= (count (:log X)) 3))
    (is (= (st X :juniper) {:source :sample :bales 5 :last-hayed 1794025081000}))))

(deftest t13-untick-guards
  (let [CG (assoc-in C1 [:mirrors :juniper :state :last-hayed] (+ T1 5000))
        UG (ch/untick CG H 1 (+ T1 120000))]
    (is (= (peek (:log UG))
           {:event :chore-undone :mirror :juniper :chore :hay :at 1794025080000 :id "juniper/hay@1794024960000"
            :result :ignored :reason :not-current}))
    (is (= (:bales (st UG :juniper)) 5))
    (is (= (:done? (e1 UG)) true))
    (is (= (:hal UG) (:hal CG))))
  (is (= (ch/untick U H 1 (+ T1 200000)) U))
  (is (= (ch/untick C1 H 7 T1) C1))
  (is (= (ch/tick C1 H 1 T1 OFF) C1))
  (let [CO (assoc C1 :talk (tk/observe (:talk C1) C1 T1))]
    (is (= (ch/untick CO H 2 T1) CO)))
  (is (= (peek (:log (w/undo-chore W {:id "x" :mirror :rex :chore :feed :at 1 :prev-at nil} A)))
         {:event :chore-undone :mirror :rex :chore :feed :at A :id "x" :result :ignored :reason :unknown-mirror}))
  (is (= (:reason (peek (:log (w/undo-chore W {:id "x" :mirror :juniper :chore :nope :at 1} A))))
         :unknown-chore)))

(deftest t14-zero-stock-and-a-chore-without-done-at
  (let [J0 (ch/click (assoc-in W [:mirrors :juniper :state :bales] 0) H :juniper T1 OFF)]
    (is (= (:consumed (:chore (e1 J0))) [:bales 0]))
    (is (= (st (ch/untick J0 H 1 (+ T1 1000)) :juniper) {:source :sample :bales 0 :last-hayed nil})))
  (let [FC (ch/click FX H :fern A OFF)]
    (is (= (get-in FC [:mirrors :fern :done]) {:mist 1794013200000}))
    (is (= (:text (:hal FC)) "did mist for fern"))
    (is (= (:text (e1 FC)) "fern's mist"))
    (is (= (:chore (e1 FC)) {:id "fern/mist@1794013200000" :mirror :fern :chore :mist :at A :prev-at nil}))
    (is (= (:text (:hal (ch/click FC H :fern (+ A 60000) OFF))) "last 5:00 PM, next 6:30 PM"))
    (let [FU (ch/untick FC H 1 (+ A 1000))]
      (is (= (get-in FU [:mirrors :fern :done]) {}))
      (is (= (w/due? FU :fern :mist (+ A 1000)) true))
      (is (= (:text (:hal FU)) "undid fern's mist")))))

(deftest t15-once-chore-not-due-is-silent
  (let [RK (w/load-estate (pr-str {:mirrors [{:id :rock :state {} :chores [{:id :polish :once true}]}]}))
        R1 (ch/click RK H :rock A OFF)]
    (is (= (:result (peek (:log R1))) :counted))
    (is (= (ch/chore-at-click R1 :rock (+ A 1000))
           {:due? false :mirror :rock :chore :polish :last-at A :next-at nil}))
    (is (= (ch/click R1 H :rock (+ A 1000) OFF) R1))))

(deftest t16-missing-hal-events-fall-back-silently
  (let [r (ch/click W {} :juniper T1 OFF)]
    (is (= (:hal r) nil))
    (is (= (:bales (st r :juniper)) 5))
    (is (= (count (:entries (:talk r))) 1)))
  (is (= (ch/click C1 {} :juniper (+ T1 60000) OFF) C1))
  (let [r (ch/untick C1 {} 1 (+ T1 120000))]
    (is (= (:bales (st r :juniper)) 6))
    (is (= (:done? (e1 r)) false))
    (is (= (:hal r) (:hal C1)))))

(deftest t17-real-hal-lines
  (is (contains? (set (map #(c/fill % {:name "Juniper" :chore "hay" :last-at "8:16 PM" :next-at "8:16 AM"})
                           (:chore-not-due c/hal-lines)))
                 (:text (:hal (ch/click C1 c/hal-lines :juniper (+ T1 60000) OFF)))))
  (is (not (re-find #"\{" (:text (:hal (ch/click C1 c/hal-lines :juniper (+ T1 60000) OFF))))))
  (is (contains? (set (map #(c/fill % {:name "Juniper" :chore "hay"}) (:chore-undone c/hal-lines)))
                 (:text (:hal (ch/untick C1 c/hal-lines 1 (+ T1 120000))))))
  (is (not (re-find #"\{" (:text (:hal (ch/untick C1 c/hal-lines 1 (+ T1 120000))))))))

;; Talk

(deftest t18-add-chore
  (is (= (tk/caption (:talk C1) T1) nil))
  (is (= (:queue-end (:talk C1)) 0))
  (is (= (tk/unread (:talk C1)) 1))
  (is (= (tk/chore-record (peek (:log C1))) RJ))
  (is (= (tk/chore-record {:id "" :mirror :a :chore :b :at 1}) nil))
  (is (= (tk/add-chore tk/empty-talk nil "Juniper" T1) tk/empty-talk))
  (is (= (tk/add-chore tk/empty-talk (peek (:log C1)) "" T1) tk/empty-talk))
  (is (= (tk/add-chore tk/empty-talk (peek (:log C1)) "Juniper" nil) tk/empty-talk))
  (is (= (:seen (tk/add-chore (tk/open-panel tk/empty-talk) (peek (:log C1)) "Juniper" T1)) 1)))

(deftest t19-hal's-line-next-to-the-box
  (let [TO (tk/observe (:talk C1) C1 T1)]
    (is (= (mapv :text (:entries TO)) ["Juniper's hay" "did hay for Juniper" "I'm hungry"]))
    (is (= (mapv :kind (:entries TO)) [:chore :hal :creature]))
    (is (= (:n (tk/caption TO T1)) 2))
    (is (= (:show-until (tk/entry TO 2)) 1794024962500))
    (is (= (:show-at (tk/entry TO 3)) 1794024962500))))

(deftest t20-set-done-and-label
  (is (= (assoc (tk/set-done (:talk C1) 1 false nil) :last-say {:juniper "Hay, please?"}) (:talk U)))
  (is (true? (identical? (:talk C1) (tk/set-done (:talk C1) 9 false nil))))
  (let [TO (tk/observe (:talk C1) C1 T1)]
    (is (true? (identical? TO (tk/set-done TO 2 false nil)))))
  (is (= (tk/chore-label (e1 C1) OFF) "Juniper's hay · 8:16 PM"))
  (is (= (tk/chore-label nil OFF) nil)))

(deftest t21-undo-survives-a-reload
  (is (= (first (:entries (tk/restore (tk/save-text (:talk U)))))
         (e1 U)))
  (is (= (e1 U) {:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper :chore RJ :done? false}))
  (let [WR (-> W (o/apply-play (o/play-of C1 W)) (assoc :talk (tk/restore (tk/save-text (:talk C1)))))]
    (is (= (st WR :juniper) {:source :sample :bales 5 :last-hayed 1794024960000}))
    (let [UR (ch/untick WR H 1 (+ T1 120000))]
      (is (= (st UR :juniper) {:source :sample :bales 6 :last-hayed nil}))
      (is (= (:text (:hal UR)) "took back hay"))
      (is (= (:done? (e1 UR)) false))))
  (is (= (o/play-of U W) nil)))

(deftest t22-restore-validates-chore-entries
  (let [RT (pr-str {:v 1 :n 6 :seen 6 :entries
                    [{:n 1 :who "Flora" :text "I'm hungry" :at 10 :kind :creature :mirror :flora}
                     {:n 2 :who "Flora" :text "Flora's feed" :at 20 :kind :chore :mirror :flora
                      :chore {:id "flora/feed@20" :mirror :flora :chore :feed :at 20 :prev-at nil :consumed [:stock 1]} :done? true}
                     {:n 3 :who "Flora" :text "Flora's feed" :at 30 :kind :chore :mirror :flora
                      :chore {:id "flora/feed@30" :mirror :flora :chore :feed :at 30 :prev-at 20} :done? false}
                     {:n 4 :who "Flora" :text "Flora's feed" :at 40 :kind :chore :mirror :flora :chore "x" :done? true}
                     {:n 5 :who "Flora" :text "Flora's feed" :at 50 :kind :chore :mirror :flora
                      :chore {:id "flora/feed@50" :mirror :flora :chore :feed :at 50 :consumed [:stock "1"]} :done? true}
                     {:n 6 :who "Flora" :text "Flora's feed" :at 60 :kind :chore :mirror :flora
                      :chore {:id "flora/feed@60" :mirror :flora :chore :feed :at 60} :done? "yes"}]})]
    (is (= (mapv :n (:entries (tk/restore RT))) [1 2 3]))
    (is (= (:chore (tk/entry (tk/restore RT) 3))
           {:id "flora/feed@30" :mirror :flora :chore :feed :at 30 :prev-at 20}))
    (is (= (:last-say (tk/restore RT)) {:flora "I'm hungry"}))))

;; Pointer picking

(deftest t23-a-drag-isn't-a-click
  (let [d {:x 100 :y 100 :t 1000}]
    (is (= (pk/click? d {:x 103 :y 104 :t 1300}) true))
    (is (= (pk/click? d {:x 104 :y 105 :t 1300}) false))
    (is (= (pk/click? d {:x 100 :y 106 :t 1100}) false))
    (is (= (pk/click? d {:x 100 :y 100 :t 1400}) false))
    (is (= (pk/click? d {:x 100 :y 100 :t 1399}) true))
    (is (= (pk/click? d {:x 100 :y 100 :t 900}) false))
    (is (= (pk/click? nil {:x 1 :y 1 :t 1}) false))
    (is (= (pk/click? d {:x "1" :y 1 :t 1}) false)))
  (is (= pk/click-max-px 6))
  (is (= pk/click-max-ms 400)))

(deftest t24-ndc
  (let [r {:left 0 :top 0 :width 800 :height 600}]
    (is (= (pk/ndc 400 300 r) [0 0]))
    (is (= (pk/ndc 0 0 r) [-1 1]))
    (is (= (pk/ndc 800 600 r) [1 -1]))
    (is (= (pk/ndc 200 450 r) [-0.5 -0.5])))
  (is (= (pk/ndc 150 75 {:left 100 :top 50 :width 200 :height 100}) [-0.5 0.5]))
  (is (= (pk/ndc 1 1 {:left 0 :top 0 :width 0 :height 10}) nil)))

(deftest t25-key-of-name
  (is (= (pk/key-of-name "[:flora :body]/3") [:flora :body]))
  (is (= (pk/key-of-name "[:flora :jar]") [:flora :jar]))
  (is (= (pk/key-of-name "[:zed 0]/1") [:zed 0]))
  (doseq [s ["pm-mirrors" "" nil "[:flora]" "[\"a\" :b]" "[:a :b]/x" "[:a :b" "{:a 1}"]]
    (is (= (pk/key-of-name s) nil))))

(deftest t26-pick-mirror
  (let [P (p/scene-plan W A)]
    (is (= (pk/pick-mirror P [["[:juniper :body]/2" "[:juniper :body]" "pm-mirrors"]]) :juniper))
    (is (= (pk/pick-mirror P [["[:flora :jar]/0" "[:flora :jar]"]]) :flora))
    (is (= (pk/pick-mirror P [["Water" "Scene"] ["[:flora :body]/1" "[:flora :body]"]]) :flora))
    (is (= (pk/pick-mirror P [["[:juniper :body]/0"] ["[:flora :body]/0"]]) :juniper))
    (is (= (pk/pick-mirror P [[nil "[:flora :body]"]]) :flora))
    (doseq [hits [[["Water"]] [] nil [["[:rex :body]/0"]]]]
      (is (= (pk/pick-mirror P hits) nil)))))

(deftest t27-clickable
  (is (= (pk/clickable? W :flora) true))
  (is (= (pk/clickable? W :juniper) true))
  (is (= (pk/clickable? W :rex) false))
  (is (= (pk/clickable? (w/load-estate "{:mirrors [{:id :stone}]}") :stone) false)))

(deftest t28-no-chore-keys
  (is (= (:hud W) {:mirrors [:flora]}))
  (is (= (contains? (:hud W) :keys) false)))
