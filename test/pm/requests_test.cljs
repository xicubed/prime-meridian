(ns pm.requests-test
  (:require [cljs.test :refer [deftest is]]
            [pm.chores :as ch]
            [pm.talk :as tk]
            [pm.world :as w]
            [pm.overlay :as o]
            [pm.fixtures :as fx]))

(def A    1794013200000)          ; 5:00 PM PST, day 20764
(def R0   1794024180000)          ; A + 3h03m = 8:03 PM: when the creatures ask
(def T1   1794024960000)          ; 8:16 PM: Juniper fed
(def H12  43200000)
(def NEXT 1794096000000)          ; first ms of day 20765 (Nov 7 4:00 PM PST)
(def OFF  -480)
(def W  (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(def H  {:chore-done    ["{name}: {chore} done" "did {chore} for {name}"]
         :chore-not-due ["last {last-at}, next {next-at}"]
         :chore-undone  ["undid {name}'s {chore}" "took back {chore}"]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn st [world id] (get-in world [:mirrors id :state]))
(defn en [world n] (tk/entry (:talk world) n))
(defn texts [t] (mapv :text (:entries t)))
(def RJ {:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at T1 :prev-at nil :consumed [:bales 1]})
(def TR (tk/observe tk/empty-talk W R0))                  ; Flora and Juniper ask at 8:03 PM
(def WR (assoc W :talk TR))
(def K  (ch/click WR H :juniper T1 OFF))                  ; Juniper clicked at 8:16 PM
(def KO (assoc K :talk (tk/observe (:talk K) K T1)))     ; ...and the next frame observed
(def KU (ch/untick KO H 2 (+ T1 120000)))                 ; unticked at 8:18 PM
(def KT (ch/tick KU H 2 (+ T1 180000) OFF))               ; ticked again at 8:19 PM
(def WN (w/chore-done WR (ev :juniper :hay R0 :key)))     ; hay done outside the boxes
(def WJ (w/chore-done W (ev :juniper :hay A :key)))       ; Juniper hayed at 5 PM, before anyone asked

;; Requests in the world

(deftest t01-asks-both-mirrors
  (is (= (w/asks W :juniper R0) [:hay]))
  (is (= (w/asks W :flora R0) [:feed]))
  (is (= (w/asks WJ :juniper R0) []))
  (is (= (w/asks W :rex R0) []))
  (let [X (w/load-estate (pr-str {:mirrors [{:id :x :state {} :chores [{:id :c :every "1h"} {:id :d :every "1h"}]
                                              :rules [{:when [:due? :c] :sign "s"}
                                                       {:when [:due? :d] :say :missing}
                                                       {:when [:due? :c] :say "c1"}
                                                       {:when [:due? :c] :say "c2"}]}]}))]
    (is (= (w/asks X :x A) [:c])))
  (is (= (w/asks (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1} :rules [{:when [:< :water 5] :say "thirsty"}]}]}))
                 :fern A)
         [])))

(deftest t02-chore-past
  (is (= (w/chore-past W :juniper :hay) "fed"))
  (is (= (w/chore-past W :flora :feed) "fed"))
  (is (= (w/chore-past (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})) :fern :mist)
         "done"))
  (is (= (w/chore-past W :rex :feed) "done")))

;; Request lines carry a box

(deftest t03-a-request-line-gets-a-box
  (is (= (:entries TR)
         [{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora
           :asks :feed :done? false :show-at 1794024180000 :show-until 1794024182500}
          {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper
           :asks :hay :done? false :show-at 1794024182500 :show-until 1794024185000}]))
  (is (true? (identical? TR (tk/observe TR W (+ R0 16))))))

(deftest t04-box-and-open-box
  (is (= (tk/box? (tk/entry TR 2)) true))
  (is (= (tk/box-chore (tk/entry TR 2)) :hay))
  (is (= (tk/box? {:n 1 :kind :hal :text "x"}) false))
  (is (= (tk/box-chore {:kind :hal}) nil))
  (is (= (tk/box? {:kind :chore :chore RJ}) true))
  (is (= (tk/box-chore {:kind :chore :chore RJ}) :hay))
  (is (= (:n (tk/open-box TR :juniper :hay)) 2))
  (is (= (:n (tk/open-box TR :flora :feed)) 1))
  (is (= (tk/open-box TR :flora :hay) nil))
  (is (= (tk/open-box (:talk K) :juniper :hay) nil))
  (is (= (:n (tk/open-box (:talk KU) :juniper :hay)) 2)))

(deftest t05-link-chore
  (let [L (peek (:log (w/chore-done W (ev :juniper :hay T1 :click))))]
    (is (= (tk/link-chore TR L "Juniper" T1) (tk/set-done TR 2 true RJ)))
    (is (= (count (:entries (tk/link-chore TR L "Juniper" T1))) 2))
    (is (= (:done? (tk/entry (tk/link-chore TR L "Juniper" T1) 2)) true))
    (is (= (:chore (tk/entry (tk/link-chore TR L "Juniper" T1) 2)) RJ))
    (is (= (tk/link-chore tk/empty-talk L "Juniper" T1) (tk/add-chore tk/empty-talk L "Juniper" T1)))
    (is (= (tk/link-chore TR {:result :counted} "Juniper" T1) TR))))

;; Click, tick, untick

(deftest t06-click-ticks-the-request
  (is (= (count (:entries (:talk K))) 2))
  (is (= (en K 2) (assoc (tk/entry TR 2) :done? true :chore RJ)))
  (is (= (:done? (en K 1)) false))
  (is (= (:text (:hal K)) "did hay for Juniper"))
  (is (= (st K :juniper) {:source :sample :bales 5 :last-hayed 1794024960000}))
  (is (= (texts (:talk KO)) ["I'm hungry" "Hay, please?" "did hay for Juniper"]))
  (is (= (:done? (en KO 2)) true)))

(deftest t07-a-chore-nobody-asked-for-gets-its-own-line
  (let [TJ (tk/observe tk/empty-talk WJ R0)]
    (is (= (texts TJ) ["I'm hungry"]))
    (let [CJ (ch/click (assoc WJ :talk TJ) H :juniper (+ A H12) OFF)]
      (is (= (en CJ 2)
             {:n 2 :who "Juniper" :text "Juniper's hay" :at 1794056400000 :kind :chore :mirror :juniper
              :chore {:id "juniper/hay@1794056400000" :mirror :juniper :chore :hay :at 1794056400000
                      :prev-at 1794013200000 :consumed [:bales 1]}
              :done? true}))
      (is (= (:text (:hal CJ)) "Juniper: hay done"))
      (is (= (:bales (st CJ :juniper)) 4)))))

(deftest t08-ticking-an-open-request-does-the-chore
  (let [TJ2 (ch/tick WR H 2 T1 OFF)]
    (is (= (peek (:log TJ2))
           {:event :chore-done :mirror :juniper :chore :hay :at T1 :via :chat :who :me
            :result :counted :id "juniper/hay@1794024960000" :prev-at nil :consumed [:bales 1]}))
    (is (= (en TJ2 2) (assoc (tk/entry TR 2) :done? true :chore RJ)))
    (is (= (count (:entries (:talk TJ2))) 2))
    (is (= (:text (:hal TJ2)) "did hay for Juniper"))
    (is (= (:bales (st TJ2 :juniper)) 5)))
  (let [TF (ch/tick WR H 1 T1 OFF)]
    (is (= (:chore (en TF 1))
           {:id "flora/feed@1794024960000" :mirror :flora :chore :feed :at T1 :prev-at nil :consumed [:stock 1]}))
    (is (= (:stock (st TF :flora)) 14))
    (is (= (:text (:hal TF)) "did feed for Flora"))))

(deftest t09-ticking-a-request-that's-not-due
  (let [TN (ch/tick WN H 2 T1 OFF)]
    (is (= (:hal TN) {:event :chore-not-due :text "last 8:03 PM, next 8:03 AM" :until 1794024966000}))
    (is (= (:done? (en TN 2)) false))
    (is (= (count (:log TN)) 1))))

(deftest t10-untick-undoes-and-the-bubble-isn't-relogged
  (is (= (st KU :juniper) {:source :sample :bales 6 :last-hayed nil}))
  (is (= (:text (:hal KU)) "undid Juniper's hay"))
  (is (= (en KU 2) (assoc (en KO 2) :done? false)))
  (is (= (get-in (:talk KU) [:last-say :juniper]) "Hay, please?"))
  (is (= (w/markers KU :juniper (+ T1 120000)) [{:say "Hay, please?"}]))
  (is (= (texts (tk/observe (:talk KU) KU (+ T1 120000)))
         ["I'm hungry" "Hay, please?" "did hay for Juniper" "undid Juniper's hay"])))

(deftest t11-tick-again-redoes
  (is (= (:via (peek (:log KT))) :tick))
  (is (= (:result (peek (:log KT))) :counted))
  (is (= (:id (peek (:log KT))) "juniper/hay@1794025140000"))
  (is (= (:chore (en KT 2))
         {:id "juniper/hay@1794025140000" :mirror :juniper :chore :hay :at 1794025140000 :prev-at nil :consumed [:bales 1]}))
  (is (= (:done? (en KT 2)) true))
  (is (= (st KT :juniper) {:source :sample :bales 5 :last-hayed 1794025140000}))
  (is (= (:text (:hal KT)) "did hay for Juniper")))

(deftest t12-guards
  (is (= (ch/untick WR H 2 T1) WR))
  (is (= (ch/tick K H 2 (+ T1 1000) OFF) K))
  (is (= (ch/untick KO H 3 T1) KO))
  (is (= (ch/tick KO H 3 T1 OFF) KO))
  (is (= (ch/tick WR H 9 T1 OFF) WR)))

;; Which lines keep their box

(deftest t13-a-request-no-longer-owed-loses-its-box
  (let [TS (tk/observe TR WN (+ R0 1000))]
    (is (= (tk/entry TS 2)
           {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper
            :show-at 1794024182500 :show-until 1794024185000}))
    (is (= (tk/entry TS 1) (tk/entry TR 1)))
    (is (= (count (:entries TS)) 2)))
  (is (= (:done? (en KO 2)) true)))

(deftest t14-asking-again-gets-a-new-box-and-the-next-chore-ticks-it
  (let [KN (tk/observe (:talk KO) KO NEXT)]
    (is (= (count (:entries KN)) 4))
    (is (= (:text (tk/entry KN 4)) "Hay, please?"))
    (is (= (:at (tk/entry KN 4)) NEXT))
    (is (= (:asks (tk/entry KN 4)) :hay))
    (is (= (:done? (tk/entry KN 4)) false))
    (is (= (:done? (tk/entry KN 2)) true))
    (let [CN (ch/click (assoc KO :talk KN) H :juniper NEXT OFF)]
      (is (= (:chore (tk/entry (:talk CN) 4))
             {:id "juniper/hay@1794096000000" :mirror :juniper :chore :hay :at NEXT :prev-at T1 :consumed [:bales 1]}))
      (is (= (:done? (tk/entry (:talk CN) 4)) true))
      (is (= (count (:entries (:talk CN))) 4))
      (is (= (:text (:hal CN)) "did hay for Juniper")))))

(deftest t15-a-new-request-replaces-a-stale-one
  (let [WL (assoc W :lines {:mirrors {:flora {:hungry ["h0" "h1" "h2" "h3" "h4"]} :juniper {:hungry ["hay0" "hay1" "hay2"]}}})
        TL (tk/observe tk/empty-talk WL R0)
        TL2 (tk/observe TL WL NEXT)]
    (is (= (texts TL) ["h1" "hay1"]))
    (is (= (texts TL2) ["h1" "hay1" "h2" "hay2"]))
    (is (= (:asks (tk/entry TL2 1)) nil))
    (is (= (:asks (tk/entry TL2 2)) nil))
    (is (= (:asks (tk/entry TL2 3)) :feed))
    (is (= (:asks (tk/entry TL2 4)) :hay))
    (is (= (:n (tk/open-box TL2 :flora :feed)) 3))))

;; Labels and state helpers

(deftest t16-box-label
  (is (= (tk/box-label (tk/entry TR 2) OFF "fed") "Juniper · Hay, please?"))
  (is (= (tk/box-label (en K 2) OFF "fed") "Juniper · Hay, please? · fed 8:16 PM"))
  (is (= (tk/box-label (en KU 2) OFF "fed") "Juniper · Hay, please?"))
  (is (= (tk/box-label (en KT 2) OFF "fed") "Juniper · Hay, please? · fed 8:19 PM"))
  (is (= (tk/box-label {:n 1 :who "Juniper" :text "Juniper's hay" :kind :chore :chore RJ :done? true} OFF "fed")
         "Juniper's hay · 8:16 PM"))
  (is (= (tk/box-label (en KO 3) OFF "fed") nil)))

(deftest t17-set-done-on-requests
  (is (= (:done? (tk/entry (tk/set-done TR 2 true RJ) 2)) true))
  (is (= (:chore (tk/entry (tk/set-done TR 2 true RJ) 2)) RJ))
  (is (= (tk/entry (tk/set-done (:talk K) 2 false nil) 2) (assoc (en K 2) :done? false)))
  (is (true? (identical? (:talk KO) (tk/set-done (:talk KO) 3 true RJ)))))

;; History across reloads

(deftest t18-requests-survive-a-reload
  (let [RK (tk/restore (tk/save-text (:talk K)))]
    (is (= (:entries RK)
           [{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora :asks :feed :done? false}
            {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? true :chore RJ}])))
  (let [WRL (-> W (o/apply-play (o/play-of KO W)) (assoc :talk (tk/restore (tk/save-text (:talk KO)))))
        UR (ch/untick WRL H 2 (+ T1 120000))]
    (is (= (st UR :juniper) {:source :sample :bales 6 :last-hayed nil}))
    (is (= (:text (:hal UR)) "took back hay"))
    (is (= (:done? (en UR 2)) false))))

(deftest t19-restore-cleans-request-fields
  (let [rq (fn [m] (pr-str {:v 1 :n 1 :seen 1
                            :entries [(merge {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper} m)]}))
        one (fn [m] (first (:entries (tk/restore (rq m)))))]
    (is (= (one {:asks :hay :done? true})
           {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false}))
    (is (= (one {:asks :hay :done? "yes" :chore RJ})
           {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false :chore RJ}))
    (is (= (one {:asks :hay :done? true :chore {:id ""}})
           {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false}))
    (is (= (one {:asks "hay" :done? true :chore RJ})
           {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper}))
    (is (= (one {:asks :hay :done? true :chore RJ})
           {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? true :chore RJ}))))

(deftest t20-repeated-requests-both-stay
  (is (= (mapv :n (:entries (tk/restore (pr-str {:v 1 :n 2 :seen 2
                                                 :entries [{:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper
                                                            :asks :hay :done? true :chore RJ}
                                                           {:n 2 :who "Juniper" :text "Hay, please?" :at (+ R0 60000) :kind :creature :mirror :juniper
                                                            :asks :hay :done? false}]}))))
         [1 2])))

(deftest t21-an-old-history-gets-a-box-from-the-current-state
  (let [OLD (pr-str {:v 1 :n 2 :seen 2
                     :entries [{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora}
                               {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper}]})
        RT (tk/restore OLD)]
    (is (= (:asks (tk/entry RT 1)) nil))
    (let [AD (tk/observe RT W T1)]
      (is (= (count (:entries AD)) 2))
      (is (= (tk/entry AD 1)
             {:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora :asks :feed :done? false}))
      (is (= (tk/entry AD 2)
             {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false}))
      (is (true? (identical? AD (tk/observe AD W (+ T1 16)))))
      (let [CN (ch/click (assoc W :talk AD) H :juniper T1 OFF)]
        (is (= (:done? (tk/entry (:talk CN) 2)) true))
        (is (= (:chore (tk/entry (:talk CN) 2)) RJ))
        (is (= (count (:entries (:talk CN))) 2))))))

(deftest t22-an-old-history-gets-no-box-when-the-line-isn't-standing
  (let [OLD (pr-str {:v 1 :n 2 :seen 2
                     :entries [{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora}
                               {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper}]})
        RT (tk/restore OLD)
        AJ (tk/observe RT WJ T1)]
    (is (= (:asks (tk/entry AJ 2)) nil))
    (is (= (:asks (tk/entry AJ 1)) :feed))
    (is (= (count (:entries AJ)) 2)))
  (let [OLD2 (pr-str {:v 1 :n 1 :seen 1
                      :entries [{:n 1 :who "Juniper" :text "Feels like hay time" :at R0 :kind :creature :mirror :juniper}]})
        A2 (tk/observe (tk/restore OLD2) W T1)]
    (is (= (:asks (tk/entry A2 1)) nil))
    (is (= (texts A2) ["Feels like hay time" "I'm hungry" "Hay, please?"]))
    (is (= (:asks (tk/entry A2 3)) :hay))))

;; The live flow end to end

(deftest t23-feed-him-again-is-untick-then-tick
  (is (= (tk/box-label (en KO 2) OFF "fed") "Juniper · Hay, please? · fed 8:16 PM"))
  (is (= (:bales (st KO :juniper)) 5))
  (is (= (w/markers KU :juniper (+ T1 120000)) [{:say "Hay, please?"}]))
  (is (= (:bales (st KU :juniper)) 6))
  (is (= (tk/box-label (en KU 2) OFF "fed") "Juniper · Hay, please?"))
  (is (= (:bales (st KT :juniper)) 5))
  (is (= (w/markers KT :juniper (+ T1 180000)) []))
  (is (= (count (:entries (:talk KT))) 3)))
