(ns pm.overlay-test
  (:require [cljs.test :refer [deftest is]]
            [pm.world :as w]
            [pm.overlay :as o]))

(def A 1794013200000)                 ; Fri 2026-11-06 17:00 PST
(def D2 172800000)                    ; "2d"
(def W  (w/load-estate w/estate-edn))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def HF {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}})
(def W2 (o/apply-file W HF))
(defn fl [world] (get-in world [:mirrors :flora :state]))
(defn ju [world] (get-in world [:mirrors :juniper :state]))
(defn on-flora [as-of st] {:as-of as-of :mirrors {:flora {:state st}}})
(def X (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 5 :sun 3}
                                           :chores [{:id :mist :every "90m"}]}
                                          {:id :panel :state {:text "hi" :on true :mode :a :n 1}}]})))

(deftest t01-hal-file-applied
  (is (= (fl W2) {:source :hal :stock 37 :last-fed 1794013200000}))
  (is (= (:state-as-of W2) 1794013200000))
  (is (= (ju W2) {:source :sample :bales 6 :last-hayed nil}))
  (is (= (get-in W2 [:mirrors :juniper]) (get-in W [:mirrors :juniper])))
  (is (= (dissoc (get-in W2 [:mirrors :flora]) :state) (dissoc (get-in W [:mirrors :flora]) :state)))
  (is (= (dissoc W2 :mirrors :state-as-of) (dissoc W :mirrors)))
  (is (= (fl W) {:source :sample :stock 15 :last-fed nil})))

(deftest t02-no-bubble-until-due
  (is (= (w/markers W2 :flora (+ A 1000)) []))
  (is (= (w/markers W2 :flora (+ A 172799999)) []))
  (is (= (w/markers W2 :flora (+ A D2)) [{:say "I'm hungry"}]))
  (is (= (w/due? W2 :flora :feed 1794186000000) true))
  (is (= (w/markers W :flora A) [{:say "I'm hungry"}])))

(deftest t03-hud-line
  (is (= (o/hud-line W :flora A) "Flora (sample) · stock 15 · I'm hungry"))
  (is (= (o/hud-line W2 :flora (+ A 1000)) "Flora (Hal) · stock 37"))
  (is (= (o/hud-line W2 :flora (+ A D2)) "Flora (Hal) · stock 37 · I'm hungry"))
  (is (= (o/hud-line W :juniper A) "Juniper (sample) · bales 6 · Hay, please?"))
  (is (= (o/hud-line (w/load-estate "{:mirrors [{:id :rock}]}") :rock A) "rock"))
  (is (= (o/hud-line W :rex A) nil))
  (is (= (w/status-line W2 :flora (+ A 1000)) "Flora · stock 37")))

(deftest t04-source-tag
  (is (= (o/source-tag :hal) "Hal"))
  (is (= (o/source-tag :sample) "sample"))
  (is (= (o/source-tag :phone) "phone"))
  (is (= (o/source-tag nil) nil))
  (is (= (o/source-tag "hal") nil)))

(deftest t05-same-as-of-no-change
  (is (= (o/apply-file W2 HF) W2))
  (let [K (w/chore-done W2 (ev :flora :feed (+ A D2) :key))]
    (is (= (:stock (fl K)) 36))
    (is (= (:last-fed (fl K)) 1794186000000))
    (is (= (o/apply-file K HF) K))))

(deftest t06-new-as-of-follows
  (let [H2 (on-flora (+ A 60000) {:stock 36})]
    (is (= (fl (o/apply-file W2 H2)) {:source :hal :stock 36 :last-fed 1794013200000}))
    (is (= (:state-as-of (o/apply-file W2 H2)) 1794013260000))
    (let [K (w/chore-done W2 (ev :flora :feed (+ A D2) :key))]
      (is (= (fl (o/apply-file K H2)) {:source :hal :stock 36 :last-fed 1794186000000})))
    (is (= (fl (o/apply-file W2 (on-flora (- A 1) {:stock 40})))
           {:source :hal :stock 40 :last-fed 1794013200000}))
    (is (= (:state-as-of (o/apply-file W2 (on-flora (- A 1) {:stock 40}))) 1794013199999))))

(deftest t07-cant-invent
  (is (= (fl (o/apply-file W (on-flora A {:stock 37 :mood :happy :weight 80})))
         {:source :hal :stock 37 :last-fed nil}))
  (let [R (o/apply-file W {:as-of A :mirrors {:rex {:state {:stock 1}}}})]
    (is (= (:mirrors R) (:mirrors W)))
    (is (= (contains? (:mirrors R) :rex) false))
    (is (= R (assoc W :state-as-of A)))))

(deftest t08-source-not-settable
  (is (= (fl (o/apply-file W (on-flora A {:source :sample :stock 37})))
         {:source :hal :stock 37 :last-fed nil}))
  (is (= (o/apply-file W (on-flora A {:source :phone}))
         (assoc W :state-as-of A))))

(deftest t09-wrong-types
  (is (= (o/apply-file W (on-flora A {:stock "37" :last-fed "yesterday"})) (assoc W :state-as-of A)))
  (is (= (o/apply-file W (on-flora A {:stock nil})) (assoc W :state-as-of A)))
  (is (= (o/apply-file W (on-flora A {:stock js/Infinity})) (assoc W :state-as-of A)))
  (is (= (o/apply-file W (on-flora A {:stock js/NaN})) (assoc W :state-as-of A)))
  (is (= (o/apply-file W (on-flora A {:last-fed :today})) (assoc W :state-as-of A)))
  (is (= (o/apply-file W (on-flora A {:last-fed js/Infinity})) (assoc W :state-as-of A)))
  (is (= (:stock (fl (o/apply-file W (on-flora A {:stock 37.5})))) 37.5))
  (is (= (fl (o/apply-file W (on-flora A {:stock 37 :last-fed "x"})))
         {:source :hal :stock 37 :last-fed nil}))
  (is (= (fl (o/apply-file W2 (on-flora (+ A 1) {:last-fed nil})))
         {:source :hal :stock 37 :last-fed nil})))

(deftest t10-bad-as-of
  (doseq [f [{:mirrors {:flora {:state {:stock 37}}}}
             (on-flora "1794013200000" {:stock 37})
             (on-flora -1 {:stock 37})
             (on-flora 1.5 {:stock 37})
             (on-flora nil {:stock 37})
             (on-flora js/Infinity {:stock 37})]]
    (is (= (o/apply-file W f) W)))
  (is (= (:stock (fl (o/apply-file W (on-flora 0 {:stock 37})))) 37)))

(deftest t11-bad-shapes
  (doseq [x [nil [] "x" 5 '(1 2) :as-of]]
    (is (= (o/apply-file W x) W)))
  (is (= (o/apply-file W {:as-of A}) (assoc W :state-as-of A)))
  (is (= (o/apply-file W {:as-of A :mirrors [:flora]}) (assoc W :state-as-of A)))
  (is (= (o/apply-file W {:as-of A :mirrors {:flora 37}}) (assoc W :state-as-of A)))
  (is (= (o/apply-file W {:as-of A :mirrors {:flora {:state [1]}}}) (assoc W :state-as-of A)))
  (is (= (o/apply-file W {:as-of A :mirrors {:flora {:stock 37}}}) (assoc W :state-as-of A))))

(deftest t12-juniper-from-file
  (let [HJ {:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed A}}}}
        WJ (o/apply-file W HJ)]
    (is (= (ju WJ) {:source :hal :bales 3 :last-hayed 1794013200000}))
    (is (= (fl WJ) (fl W)))
    (is (= (w/markers WJ :juniper (+ A 1000)) [{:sign "Bales left: 3"}]))
    (is (= (w/markers WJ :juniper (+ A 43200000)) [{:say "Hay, please?"} {:sign "Bales left: 3"}]))
    (is (= (o/hud-line WJ :juniper (+ A 1000)) "Juniper (Hal) · bales 3 · Bales left: 3"))
    (is (= (o/hud-line WJ :flora A) "Flora (sample) · stock 15 · I'm hungry"))))

(deftest t13-both-mirrors
  (let [HB {:as-of A :mirrors {:flora {:state {:stock 2}} :juniper {:state {:bales 0}}}}
        WB (o/apply-file W HB)]
    (is (= (fl WB) {:source :hal :stock 2 :last-fed nil}))
    (is (= (ju WB) {:source :hal :bales 0 :last-hayed nil}))
    (is (= (o/hud-line WB :flora A) "Flora (Hal) · stock 2 · I'm hungry · Worms left: 2"))
    (is (= (o/hud-line WB :juniper A) "Juniper (Hal) · bales 0 · Hay, please? · Bales left: 0"))))

(deftest t14-rules-run-on-real-state
  (let [K1 (w/chore-done W2 (ev :flora :feed (+ A 1000) :key))]
    (is (= (:stock (fl K1)) 37))
    (is (= (last (:log K1)) (assoc (ev :flora :feed (+ A 1000) :key) :result :ignored :reason :not-due))))
  (let [K2 (w/chore-done W2 (ev :flora :feed (+ A D2) :key))]
    (is (= (:stock (fl K2)) 36))
    (is (= (:last-fed (fl K2)) 1794186000000))
    (is (= (:result (last (:log K2))) :counted))))

(deftest t15-parse-file
  (is (= (o/parse-file "{:as-of 1794013200000 :mirrors {:flora {:state {:stock 37 :last-fed 1794013200000}}}}") HF))
  (is (= (o/parse-file "; Hal's file\n{:as-of 5}") {:as-of 5}))
  (is (= (o/parse-file "[1 2]") [1 2]))
  (is (= (o/parse-file "{:as-of 1") nil))
  (is (= (o/parse-file "{:a 1 :a 2}") nil))
  (is (= (o/parse-file "#foo 1") nil))
  (is (= (o/parse-file nil) nil))
  (is (= (o/parse-file 42) nil))
  (is (= (o/parse-file "") nil))
  (is (= (o/apply-file W (o/parse-file "<html>404</html>")) W)))

(deftest t16-other-world-keys-kept
  (let [R (o/apply-file (assoc W :t 5 :spin 0.6) HF)]
    (is (= (:t R) 5))
    (is (= (:spin R) 0.6))
    (is (= (:log R) []))
    (is (= (:hud R) (:hud W)))
    (is (= (:sounds R) (:sounds W)))))

(deftest t17-renamed-copy
  (let [WP (assoc-in W [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet :name "Pet"))
        RP (o/apply-file WP {:as-of A :mirrors {:pet {:state {:stock 37 :last-fed A}}}})]
    (is (= (get-in RP [:mirrors :pet :state]) {:source :hal :stock 37 :last-fed 1794013200000}))
    (is (= (fl RP) {:source :sample :stock 15 :last-fed nil}))
    (is (= (o/hud-line RP :pet (+ A 1000)) "Pet (Hal) · stock 37"))
    (is (= (o/hud-line RP :flora A) "Flora (sample) · stock 15 · I'm hungry"))))

(deftest t18-other-field-kinds
  (let [X2 (o/apply-file X {:as-of 1 :mirrors {:fern {:state {:water nil :sun 4}}}})]
    (is (= (get-in X2 [:mirrors :fern :state]) {:water 5 :sun 4 :source :hal}))
    (is (= (o/hud-line X :fern 0) "fern"))
    (is (= (o/hud-line X2 :fern 0) "fern (Hal)")))
  (let [X3 (o/apply-file X {:as-of 1 :mirrors {:panel {:state {:text "yo" :on false :mode :b}}}})]
    (is (= (get-in X3 [:mirrors :panel :state]) {:text "yo" :on false :mode :b :n 1 :source :hal})))
  (is (= (o/apply-file X {:as-of 2 :mirrors {:panel {:state {:text 1 :on "yes" :mode "b" :n "2"}}}})
         (assoc X :state-as-of 2))))
