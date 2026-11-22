(ns pm.world-test
  (:require [cljs.test :refer [deftest is]]
            [pm.world :as w]
            [pm.content :as c]
            [pm.fixtures :as fx]))

(def T0 1800000000000)
(def D2  172800000)   ; "2d"
(def H12  43200000)   ; "12h"
(def W   (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def fed  (ev :flora :feed T0 :key))
(def hay  (ev :juniper :hay T0 :key))
(def W1  (w/chore-done W fed))          ; Flora fed once at T0
(def fern-edn
  (pr-str {:mirrors [{:id :fern :kind :plant :home :sill
                       :state {:water 5 :sun 3}
                       :rules [{:when [:< :water 5]   :say "thirsty"}
                               {:when [:>= :sun 3]    :sign "sun {sun}, mood {mood}"}
                               {:when [:= :water 5]   :say "ok" :sign "full"}
                               {:when [:> :missing 1] :say "never"}
                               {:when [:due? :nope]   :say "never2"}
                               {:when [:sparkle :water] :say "never3"}
                               {:when [:due? :mist]   :sign "mist me"}
                               {:when [:= :water 5]}]
                       :chores [{:id :mist :every "90m"}
                                {:id :repot :once true}]}]}))
(def F (w/load-estate fern-edn))

;; parse-every

(deftest t01-parse-every-units
  (is (= 172800000 (w/parse-every "2d")))
  (is (= 43200000 (w/parse-every "12h")))
  (is (= 5400000 (w/parse-every "90m")))
  (is (= 30000 (w/parse-every "30s")))
  (is (= 604800000 (w/parse-every "1w")))
  (is (= 0 (w/parse-every "0d"))))

(deftest t02-parse-every-rejects
  (is (nil? (w/parse-every nil)))
  (is (nil? (w/parse-every 5)))
  (is (nil? (w/parse-every "")))
  (is (nil? (w/parse-every "1.5h")))
  (is (nil? (w/parse-every "2D")))
  (is (nil? (w/parse-every " 2d")))
  (is (nil? (w/parse-every "d")))
  (is (nil? (w/parse-every "2")))
  (is (nil? (w/parse-every "2x"))))

;; load-estate

(deftest t03-load-real-estate
  (is (= #{:flora :juniper} (set (keys (:mirrors W)))))
  (is (= [] (:log W)))
  (is (= {:mirrors [:flora]} (:hud W)))
  (is (= {:source :sample :stock 15 :last-fed nil} (get-in W [:mirrors :flora :state])))
  (is (= {:source :sample :bales 6 :last-hayed nil} (get-in W [:mirrors :juniper :state])))
  (is (= :juniper (get-in W [:mirrors :juniper :id]))))

(deftest t04-real-chores-parse
  (doseq [[_ mirror] (:mirrors W)
          chore (:chores mirror)]
    (is (some? (w/parse-every (:every chore)))))
  (is (= 172800000 (w/parse-every (:every (first (get-in W [:mirrors :flora :chores]))))))
  (is (= 43200000 (w/parse-every (:every (first (get-in W [:mirrors :juniper :chores])))))))

(deftest t05-load-edges
  (is (= {:mirrors {} :log []} (w/load-estate "{}")))
  (is (= {:mirrors {} :log [] :hud {:mirrors []}} (w/load-estate "{:mirrors [] :hud {:mirrors []}}")))
  (is (= {:mirrors {:a {:id :a :n 2}} :log []}
         (w/load-estate "{:mirrors [{:id :a :n 1} {:id :a :n 2}]}")))
  (is (= {:mirrors {} :log []} (w/load-estate "{:mirrors [{:kind :rock}]}")))
  (is (thrown? :default (w/load-estate "{:mirrors ["))))

;; due?

(deftest t06-flora-never-fed-is-due
  (is (= true (w/due? W :flora :feed T0))))

(deftest t07-flora-window
  (is (= false (w/due? W1 :flora :feed (+ T0 172799999))))
  (is (= true (w/due? W1 :flora :feed (+ T0 172800000))))
  (is (= 1800172800000 (+ T0 172800000)))
  (is (= false (w/due? W1 :flora :feed (- T0 1000)))))

(deftest t08-juniper-window
  (let [W2 (w/chore-done W hay)]
    (is (= true (w/due? W :juniper :hay T0)))
    (is (= false (w/due? W2 :juniper :hay (+ T0 43199999))))
    (is (= true (w/due? W2 :juniper :hay (+ T0 43200000))))
    (is (= 1800043200000 (+ T0 43200000)))))

(deftest t09-due-unknowns
  (is (= false (w/due? W :flora :walk T0)))
  (is (= false (w/due? W :rex :feed T0))))

(deftest t10-due-no-done-at-and-once
  (is (= true (w/due? F :fern :mist 0)))
  (is (= true (w/due? F :fern :repot 0)))
  (let [F2 (w/chore-done F (ev :fern :mist 1000 :key))]
    (is (= 1000 (get-in F2 [:mirrors :fern :done :mist])))
    (is (= false (w/due? F2 :fern :mist 5400999)))
    (is (= true (w/due? F2 :fern :mist 5401000))))
  (let [F3 (w/chore-done F (ev :fern :repot 100 :key))]
    (is (= 100 (get-in F3 [:mirrors :fern :done :repot])))
    (is (= false (w/due? F3 :fern :repot 10000000000000)))))

;; chore-done

(deftest t11-feed-flora
  (is (= 14 (get-in W1 [:mirrors :flora :state :stock])))
  (is (= 1800000000000 (get-in W1 [:mirrors :flora :state :last-fed])))
  (is (= [(assoc fed :result :counted :id "flora/feed@1800000000000" :prev-at nil :consumed [:stock 1])] (:log W1)))
  (is (= (get-in W [:mirrors :juniper]) (get-in W1 [:mirrors :juniper]))))

(deftest t12-one-per-window
  (let [e2 (ev :flora :feed (+ T0 60000) :sms)
        W1b (w/chore-done W1 e2)]
    (is (= 14 (get-in W1b [:mirrors :flora :state :stock])))
    (is (= 1800000000000 (get-in W1b [:mirrors :flora :state :last-fed])))
    (is (= 2 (count (:log W1b))))
    (is (= (assoc e2 :result :ignored :reason :not-due) (last (:log W1b))))
    (is (= (dissoc W1 :log) (dissoc W1b :log)))))

(deftest t13-next-window-counts
  (let [r (w/chore-done W1 (ev :flora :feed (+ T0 172800000) :nfc))]
    (is (= 13 (get-in r [:mirrors :flora :state :stock])))
    (is (= 1800172800000 (get-in r [:mirrors :flora :state :last-fed])))
    (is (= :counted (:result (last (:log r)))))))

(deftest t14-ignored-unknowns
  (let [cases [[(ev :rex :feed T0 :key) :unknown-mirror]
               [(ev :flora :walk T0 :key) :unknown-chore]
               [(dissoc fed :at) :no-time]
               [(assoc fed :at "soon") :no-time]
               [{:event :chore-done :chore :feed :at T0} :unknown-mirror]]]
    (doseq [[e r] cases]
      (let [result (w/chore-done W e)]
        (is (= (dissoc W :log) (dissoc result :log)))
        (is (= (assoc e :result :ignored :reason r) (last (:log result))))))))

(deftest t15-never-below-zero
  (let [Z (assoc-in W [:mirrors :flora :state :stock] 0)
        r (w/chore-done Z fed)]
    (is (= 0 (get-in r [:mirrors :flora :state :stock])))
    (is (= 1800000000000 (get-in r [:mirrors :flora :state :last-fed])))
    (is (= :counted (:result (last (:log r))))))
  (let [Z5 (-> W
                (assoc-in [:mirrors :flora :state :stock] 3)
                (assoc-in [:mirrors :flora :chores 0 :consumes] [:stock 5]))
        r5 (w/chore-done Z5 fed)]
    (is (= 0 (get-in r5 [:mirrors :flora :state :stock])))))

(deftest t16-missing-counter-field
  (let [N (update-in W [:mirrors :flora :state] dissoc :stock)
        r (w/chore-done N fed)]
    (is (= false (contains? (get-in r [:mirrors :flora :state]) :stock)))
    (is (= 1800000000000 (get-in r [:mirrors :flora :state :last-fed])))
    (is (= :counted (:result (last (:log r)))))))

(deftest t17-hay-juniper
  (let [W2 (w/chore-done W hay)]
    (is (= 5 (get-in W2 [:mirrors :juniper :state :bales])))
    (is (= 1800000000000 (get-in W2 [:mirrors :juniper :state :last-hayed])))
    (is (= (get-in W [:mirrors :flora]) (get-in W2 [:mirrors :flora])))
    (let [r (w/chore-done W2 (ev :juniper :hay (+ T0 43199999) :sms))]
      (is (= 5 (get-in r [:mirrors :juniper :state :bales])))
      (is (= :not-due (:reason (last (:log r))))))))

(deftest t18-hay-runs-out
  (let [events (for [k (range 7)] (ev :juniper :hay (+ T0 (* k 43200000)) :key))
        results (reductions w/chore-done W events)
        afters (rest results)
        bales (map #(get-in % [:mirrors :juniper :state :bales]) afters)
        final (last afters)]
    (is (= [5 4 3 2 1 0 0] bales))
    (is (= 1800259200000 (get-in final [:mirrors :juniper :state :last-hayed])))
    (is (every? #(= :counted (:result (last (:log %)))) afters))))

(deftest t19-other-world-keys-kept
  (let [r (w/chore-done (assoc W :t 5 :spin 0.6) fed)]
    (is (= 5 (:t r)))
    (is (= 0.6 (:spin r)))))

;; markers

(deftest t20-flora-hungry
  (is (= [{:say "I'm hungry"}] (w/markers W :flora T0))))

(deftest t21-flora-fed-no-markers
  (is (= [] (w/markers W1 :flora (+ T0 1000)))))

(deftest t22-flora-low-jar
  (let [S2 (assoc-in W [:mirrors :flora :state :stock] 2)]
    (is (= [{:say "I'm hungry"} {:sign "Worms left: 2"}] (w/markers S2 :flora T0)))
    (is (= [{:say "I'm hungry"}] (w/markers (assoc-in W [:mirrors :flora :state :stock] 3) :flora T0)))
    (is (= [{:say "I'm hungry"} {:sign "Worms left: 0"}] (w/markers (assoc-in W [:mirrors :flora :state :stock] 0) :flora T0)))))

(deftest t23-snap
  (let [S3 (assoc-in W [:mirrors :flora :state :stock] 3)]
    (is (= [{:say "I'm hungry"}] (w/markers S3 :flora T0)))
    (is (= [{:sign "Worms left: 2"}] (w/markers (w/chore-done S3 fed) :flora (+ T0 1))))))

(deftest t24-juniper-markers
  (is (= [{:say "Hay, please?"}] (w/markers W :juniper T0)))
  (is (= [{:say "Hay, please?"} {:sign "Bales left: 3"}]
         (w/markers (assoc-in W [:mirrors :juniper :state :bales] 3) :juniper T0)))
  (let [events (for [k (range 4)] (ev :juniper :hay (+ T0 (* k 43200000)) :key))
        after4 (reduce w/chore-done W events)]
    (is (= 2 (get-in after4 [:mirrors :juniper :state :bales])))
    (is (= 1800129600000 (get-in after4 [:mirrors :juniper :state :last-hayed])))
    (is (= [{:sign "Bales left: 2"}] (w/markers after4 :juniper (+ T0 129600001))))
    (is (= [{:say "Hay, please?"} {:sign "Bales left: 2"}] (w/markers after4 :juniper (+ T0 172800000))))))

(deftest t25-conditions-and-placeholders
  (is (= [{:sign "sun 3, mood {mood}"} {:say "ok" :sign "full"} {:sign "mist me"}]
         (w/markers F :fern 0))))

(deftest t26-markers-unknowns
  (is (= [] (w/markers W :rex T0)))
  (is (= [] (w/markers (w/load-estate "{:mirrors [{:id :rock}]}") :rock T0))))

;; status-line

(deftest t27-status-flora
  (is (= "Flora · stock 15 · I'm hungry" (w/status-line W :flora T0)))
  (is (= "Flora · stock 14" (w/status-line W1 :flora (+ T0 1000))))
  (let [S2 (assoc-in W [:mirrors :flora :state :stock] 2)]
    (is (= "Flora · stock 2 · I'm hungry · Worms left: 2" (w/status-line S2 :flora T0)))))

(deftest t28-status-juniper
  (is (= "Juniper · bales 6 · Hay, please?" (w/status-line W :juniper T0))))

(deftest t29-status-edges
  (is (= "fern · sun 3, mood {mood} · ok · full · mist me" (w/status-line F :fern 0)))
  (is (= "rock" (w/status-line (w/load-estate "{:mirrors [{:id :rock}]}") :rock T0)))
  (is (nil? (w/status-line W :rex T0))))

;; jack out / jack in (GDD §2.3, §4.8)

(def src {:agent "twin" :session "s1" :machine "laptop"})
(def jo-household {:event :jack-out :reason :household :who :household :text "Dinner!" :source src})

(deftest t30-apply-event-unknown-is-a-no-op
  (is (= W (w/apply-event W {:event :mystery :mirror :flora} T0))))

(def SRC {:agent "hal-say" :session "t" :machine "local"})

(deftest t30b-a-say-shows-on-the-walker-and-the-twins-goes-to-the-caption
  (let [twin (first (filter #(= (get-in W [:jumeaux :twin :name]) (:name %)) (:walkers W)))
        tw   (name (:agent twin))
        crew (name (:agent (first (remove #(= twin %) (:walkers W)))))
        W2   (w/apply-event W {:type :agent-event :agent tw :kind :say :text "On it." :source SRC} T0)
        W3   (w/apply-event W {:type :agent-event :agent crew :kind :say :text "Sanding." :source SRC} T0)]
    (is (some? twin) "the estate has a walker named as the twin")
    (is (= {tw "On it."} (w/says-now W2 T0)))
    (is (= {} (w/says-now W2 (+ T0 w/say-ms))) "the bubble ends")
    (is (= "On it." (get-in W2 [:hal :text])) "the twin's line is his caption too")
    (is (= {crew "Sanding."} (w/says-now W3 T0)))
    (is (nil? (get-in W3 [:hal :text])) "a crew member's say is only its bubble")
    (is (empty? (:asks-player W2)) "a say raises no !")))

(deftest t31-jack-out-freezes-with-its-reason-shown
  (let [W2 (w/apply-event W jo-household T0)]
    (is (= true (w/jacked-out? W2)))
    ;; one of Hal's :jack-out-household lines, filled; the lines are generated content
    (is (some #(= (c/fill % {:text "Dinner!"}) (w/jack-out-text W2)) (:jack-out-household c/hal-lines)))
    (is (= {:out? true :reason :household :who :household
            :line (w/jack-out-text W2) :at T0 :log-n 1}
           (:jack W2)))
    (is (= (assoc jo-household :result :jacked-out) (last (:log W2))))))

(deftest t32-jack-out-ignores-an-invalid-event
  (let [bad (dissoc jo-household :source)
        W2  (w/apply-event W bad T0)]
    (is (= false (w/jacked-out? W2)))
    (is (nil? (w/jack-out-text W2)))
    (is (= (assoc bad :result :ignored :reason :invalid) (last (:log W2))))))

(deftest t33-jack-in-with-nothing-changed
  (let [W2 (w/apply-event W jo-household T0)
        W3 (w/jack-in W2 (+ T0 1000))]
    (is (= false (w/jacked-out? W3)))
    (is (= {:out? false :at (+ T0 1000)} (:jack W3)))
    (is (some #(= % (get-in W3 [:hal :text])) (:jack-in c/hal-lines)) "one of Hal's :jack-in lines")
    (is (= {:event :jack-in :text (get-in W3 [:hal :text])
            :until (+ T0 1000 c/hal-ms)}
           (:hal W3)))))

(deftest t34-jack-in-resumes-with-what-changed
  (let [W2 (w/apply-event W jo-household T0)
        W2b (w/chore-done W2 fed)
        W3  (w/jack-in W2b (+ T0 1000))]
    (is (= 2 (count (:log W2b))))
    (is (= false (w/jacked-out? W3)))
    (is (= {:event :jack-in :text "1 thing changed while you were out."
            :until (+ T0 1000 c/hal-ms)}
           (:hal W3)))))

(deftest t35-jack-in-is-a-no-op-when-not-jacked-out
  (is (= W (w/jack-in W T0))))

;; chore-done over the bridge (GDD §3.1, §4.8): a canal click calls
;; `chore-done` directly (it's local, already trusted); a bridge event goes
;; through `apply-event`, which checks :source and uses `now` -- the
;; moment it reached the game -- as :at, not anything the sender claims.

(def chore-done-bridge
  {:event :chore-done :mirror :flora :chore :feed :via :nfc :at 1 :source src})

(deftest t36-apply-event-chore-done-counts-once-per-window
  (let [W2 (w/apply-event W chore-done-bridge T0)]
    (is (= 14 (get-in W2 [:mirrors :flora :state :stock])))
    (is (= T0 (get-in W2 [:mirrors :flora :state :last-fed])))
    (is (= :counted (:result (last (:log W2)))))
    (is (= T0 (:at (last (:log W2)))))
    (let [W3 (w/apply-event W2 (assoc chore-done-bridge :at 2) (+ T0 60000))]
      (is (= 14 (get-in W3 [:mirrors :flora :state :stock])))
      (is (= T0 (get-in W3 [:mirrors :flora :state :last-fed])))
      (is (= :ignored (:result (last (:log W3)))))
      (is (= :not-due (:reason (last (:log W3))))))))

(deftest t37-apply-event-chore-done-without-a-source-is-ignored
  (let [bad (dissoc chore-done-bridge :source)
        W2  (w/apply-event W bad T0)]
    (is (= (dissoc W :log) (dissoc W2 :log)))
    (is (= (assoc bad :result :ignored :reason :invalid) (last (:log W2))))))

;; ask_player: agent-event / answer (GDD §3.1, §2.1, §4.8): an agent's
;; explicit `ask_player` tool call raises the "!" pm.world tracks in
;; :asks-player; the player's answer clears it.

(def ask-q {:type :agent-event :agent "le-critique" :kind :question
            :text "Rocks: round or jagged?" :source src})
(def ask-a {:type :answer :agent "le-critique" :text "jagged" :source src})

(deftest t38-agent-event-question-raises-the-flag
  (let [W2 (w/apply-event W ask-q T0)]
    (is (= {:text "Rocks: round or jagged?" :since T0} (get-in W2 [:asks-player "le-critique"])))
    (is (true? (w/needs-you? W2 "le-critique")))
    (is (false? (w/needs-you? W2 "someone-else")))
    (is (false? (w/needs-you? W "le-critique")))
    (is (= (assoc ask-q :result :asked) (last (:log W2))))))

(deftest t39-answer-clears-the-flag
  (let [W2 (w/apply-event W ask-q T0)
        W3 (w/apply-event W2 ask-a (+ T0 1000))]
    (is (true? (w/needs-you? W2 "le-critique")))
    (is (false? (w/needs-you? W3 "le-critique")))
    (is (= (assoc ask-a :result :answered) (last (:log W3))))))

(deftest t40-agent-event-without-a-source-is-ignored
  (let [bad (dissoc ask-q :source)
        W2  (w/apply-event W bad T0)]
    (is (= (dissoc W :log) (dissoc W2 :log)))
    (is (= (assoc bad :result :ignored :reason :invalid) (last (:log W2))))))

(deftest t41-answer-without-a-source-is-ignored
  (let [W2  (w/apply-event W ask-q T0)
        bad (dissoc ask-a :source)
        W3  (w/apply-event W2 bad (+ T0 1000))]
    (is (true? (w/needs-you? W3 "le-critique")))
    (is (= (assoc bad :result :ignored :reason :invalid) (last (:log W3))))))

(deftest t42-an-answer-with-nothing-pending-still-logs-answered
  (let [W2 (w/apply-event W ask-a T0)]
    (is (false? (w/needs-you? W2 "le-critique")))
    (is (= (assoc ask-a :result :answered) (last (:log W2))))))

;; The snap (GDD §2.2, §4.8): a counted chore marked :fx :snap leaves data
;; `pm.scene/play-snap!` and `pm.plan/snap-pose` can see; an ordinary chore
;; (Juniper's hay) never touches it.

(deftest t43-chore-done-flags-the-snap
  (is (= {:mirror :flora :at T0} (:snap W1))))

(deftest t44-an-ordinary-chore-leaves-no-snap
  (is (nil? (:snap (w/chore-done W hay)))))


(deftest t30c-errand-targets-stand-a-little-short
  (let [spots [{:agent :le-coureur :pos [10 1.8 0]}]]
    (is (= [8.8 0] (w/errand-target W "le-coureur" spots [0 0] 1.2)) "1.2 m short of a colleague")
    (is (= [9.5 0] (w/errand-target W "le-coureur" spots [9.5 0] 1.2)) "already close: stay put")
    (let [[x _ z] (get-in W [:mailbox :at])
          to (w/errand-target W "mailbox" [] [x (+ z 5)] 1.2)]
      (is (< (js/Math.abs (- 1.2 (js/Math.hypot (- x (first to)) (- z (second to))))) 1e-6) "1.2 m from the mailbox"))
    (is (nil? (w/errand-target W "nowhere" spots [0 0] 1.2)))
    (is (nil? (w/errand-target W "mailbox" spots nil 1.2)))
    (let [W2 (w/apply-event W {:type :agent-event :agent "hal" :kind :errand :where "mailbox" :text "Two letters." :source SRC} T0)]
      (is (= {:where "mailbox" :text "Two letters." :since T0 :state :pending} (get-in W2 [:errands "hal"]))))))

(deftest t39b-the-asker-brings-the-question-to-the-twin
  (let [tw (name (:agent (w/twin-walker W)))
        W2 (w/apply-event W ask-q T0)]
    (is (= {:where tw :text "Rocks: round or jagged?" :since T0 :state :pending}
           (get-in W2 [:errands "le-critique"]))
        "an errand to wherever the twin stands, the question said there")
    (is (nil? (get-in (w/apply-event W2 ask-a (+ T0 1000)) [:errands "le-critique"]))
        "answered before setting off: the walk is off")
    (is (= :going (get-in (w/apply-event (assoc-in W2 [:errands "le-critique" :state] :going) ask-a (+ T0 1000))
                          [:errands "le-critique" :state]))
        "already on the way: finishes the walk")
    (is (nil? (get-in (w/apply-event W (assoc ask-q :agent tw) T0) [:errands tw]))
        "the twin's own question doesn't walk to himself")))
