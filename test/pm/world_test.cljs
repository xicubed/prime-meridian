(ns pm.world-test
  (:require [cljs.test :refer [deftest is]]
            [pm.world :as w]))

(def T0 1800000000000)
(def D2  172800000)   ; "2d"
(def H12  43200000)   ; "12h"
(def W   (w/load-estate w/estate-edn))
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
  (is (= {:mirrors [:flora] :keys {"f" [:flora :feed]}} (:hud W)))
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
  (is (= [(assoc fed :result :counted)] (:log W1)))
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

;; key-event

(deftest t30-key-f
  (is (= {:event :chore-done :mirror :flora :chore :feed :at 1800000000000 :via :key :who :me}
         (w/key-event W "f" T0)))
  (is (= {:event :chore-done :mirror :flora :chore :feed :at 1800000000000 :via :key :who :me}
         (w/key-event W "F" T0)))
  (is (nil? (w/key-event W "g" T0)))
  (is (nil? (w/key-event W nil T0)))
  (is (nil? (w/key-event F "f" T0))))

(deftest t31-key-second-mirror
  (let [WH (assoc-in W [:hud :keys "h"] [:juniper :hay])]
    (is (= hay (w/key-event WH "H" T0)))
    (is (= 5 (get-in (w/chore-done WH (w/key-event WH "H" T0)) [:mirrors :juniper :state :bales])))))

(deftest t32-key-end-to-end
  (let [K (w/chore-done W (w/key-event W "F" T0))]
    (is (= 14 (get-in K [:mirrors :flora :state :stock])))
    (let [r (w/chore-done K (w/key-event K "f" (+ T0 5000)))]
      (is (= 14 (get-in r [:mirrors :flora :state :stock])))
      (is (= :not-due (:reason (last (:log r)))))
      (is (= :key (:via (last (:log r))))))))
