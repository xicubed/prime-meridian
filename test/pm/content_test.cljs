(ns pm.content-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.set :as set]
            [pm.content :as c]
            [pm.world :as w]
            [pm.plan :as p]
            [pm.overlay :as o]))

(def A       1794013200000)        ; Fri 2026-11-06 17:00 PST = day 20764, 01:00 UTC
(def DAY-END (+ A 82799999))       ; 1794095999999, last ms of day 20764
(def NEXT    (+ A 82800000))       ; 1794096000000, first ms of day 20765
(def D2      172800000)
(def W  (w/load-estate w/estate-edn))          ; real estate, real table (no :lines)
(def LT {:mirrors {:flora   {:hungry ["h0" "h1" "h2" "h3" "h4"] :low ["low {stock}"]
                             :stock-sign ["jar {stock}" "worms {stock}"]}
                   :juniper {:hungry ["hay0" "hay1" "hay2"] :low ["bales {bales}" "few {bales}"]
                             :stock-sign ["barn {bales}"]}}})
(def WT (assoc W :lines LT))
(def L  {:mirrors {:flora {:hungry ["h0" "h1 {stock}" "h2"]}
                   :juniper {:low "bales {bales}"}
                   :odd {:empty [] :num [7] :mixed ["ok" 7]}}
         :jack-in ["hi" "hello {name}"]})
(def P  [:mirrors :flora :hungry])
(def H  {:jack-in ["hi" "hello" "hey"]
         :chore-done ["{name}: {chore} done" "did {chore} for {name}"]
         :empty []})
(def IB {:mailbox "demo"
         :messages [{:id :a :kind :chore}
                    {:id :b :kind :suspicious :clerk-note "Flagged: bait."}
                    {:id :c :kind :question}
                    "junk"
                    {:id :d :clerk-note ""}
                    {:id :e :clerk-note "  "}]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn texts [world now] (p/label-texts (p/scene-plan world now)))
(defn st [world id k v] (assoc-in world [:mirrors id :state k] v))
(defn variants [table path] (let [v (get-in table path)] (if (string? v) [v] (vec v))))
(defn filled [table path vars] (set (map #(c/fill % vars) (variants table path))))
(def MX (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1}
          :rules  [{:when [:< :water 5] :say "thirsty {water}"}
                   {:when [:< :water 5] :sign :dry}
                   {:when [:< :water 5] :say :missing}
                   {:when [:< :water 5] :say :dry :sign "sign {water}"}]
          :placed [{:id :pot  :at [0 0 0] :label :sign :idle :calm}
                   {:id :leaf :at [1 0 0] :label :say  :idle "leaf {water}"}]}]})))
(def MXL (assoc MX :lines {:mirrors {:fern {:dry ["dry {water}"] :calm ["calm {water}"]}}}))
(def FX (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})))
(def K  (w/chore-done W (ev :flora :feed A :key)))     ; Flora fed by the F key at A (counted)
(def K1 (c/hal-on-chore K H))
(def S  (c/hal-say W H :jack-in {} A))

;; Content basics

(deftest t01-parse
  (is (= (c/parse "{:a 1}") {:a 1}))
  (is (= (c/parse ";; GENERATED\n{:a [\"x\"]}") {:a ["x"]}))
  (is (= (c/parse "[1 2]") nil))
  (is (= (c/parse "{") nil))
  (is (= (c/parse "") nil))
  (is (= (c/parse nil) nil))
  (is (= (c/parse 7) nil))
  (is (true? (map? c/hal-lines)))
  (is (true? (map? c/mirror-lines)))
  (is (true? (map? (:mirrors c/mirror-lines))))
  (is (true? (map? c/sample-inbox)))
  (is (true? (vector? (:messages c/sample-inbox)))))

(deftest t02-fill
  (is (= (c/fill "a {x} {y-2} {Z} {q}" {:x 1 :y-2 "b"}) "a 1 b {Z} {q}"))
  (is (= (c/fill "{n}" {:n 0}) "0"))
  (is (= (c/fill "x {a}" nil) "x {a}"))
  (is (= (c/fill nil {}) nil))
  (is (= (c/fill 7 {}) nil))
  (is (= (w/fill "Worms: {stock}" {:stock 15}) "Worms: 15"))
  (is (= (w/fill nil {}) nil)))

(deftest t03-day-number
  (is (= (c/day-number A) 20764))
  (is (= (c/day-number DAY-END) 20764))
  (is (= (c/day-number NEXT) 20765))
  (is (= (c/day-number 0) 0))
  (is (= (c/day-number 86399999) 0))
  (is (= (c/day-number 86400000) 1))
  (is (= (c/day-number -1) -1))
  (is (= (c/day-number nil) 0))
  (is (= (c/day-number ##NaN) 0))
  (is (= (c/day-number ##Inf) 0))
  (is (= (c/day-number "5") 0)))

(deftest t04-name-code
  (is (= (c/name-code :flora) 532))
  (is (= (c/name-code :juniper) 765))
  (is (= (c/name-code :pet) 329))
  (is (= (c/name-code :fern) 427))
  (is (= (c/name-code :a) 97))
  (is (= (c/name-code nil) 0))
  (is (= (c/name-code "flora") 0)))

(deftest t05-placeholders
  (is (= (c/placeholders "Worms left: {stock}") #{:stock}))
  (is (= (c/placeholders "{name}'s {chore}: done. That one counts.") #{:name :chore}))
  (is (= (c/placeholders "{a} {a}") #{:a}))
  (is (= (c/placeholders "Welcome back.") #{}))
  (is (= (c/placeholders "{Z}") #{}))
  (is (= (c/placeholders nil) #{})))

;; `line`

(deftest t06-line-picks-by-seed
  (is (= (c/line L P {} 0) "h0"))
  (is (= (c/line L P {} 1) "h1 {stock}"))
  (is (= (c/line L P {} 2) "h2"))
  (is (= (c/line L P {} 3) "h0"))
  (is (= (c/line L P {} 4) "h1 {stock}"))
  (is (= (c/line L P {} -1) "h2"))
  (is (= (c/line L P {} 21296) "h2")))

(deftest t07-line-fills
  (is (= (c/line L P {:stock 4} 1) "h1 4"))
  (is (= (c/line L P {:stock 0} 1) "h1 0"))
  (is (= (c/line L P {:stock nil} 1) "h1 {stock}"))
  (is (= (c/line L P nil 1) "h1 {stock}"))
  (is (= (c/line L [:jack-in] {:name "Flora"} 1) "hello Flora"))
  (is (= (c/line L [:jack-in] {:name "Flora" :extra 1} 0) "hi")))

(deftest t08-line-seed-stable
  (is (= (c/line L P {} 7) (c/line L P {} 7)))
  (is (= (c/line L P {} 7) "h1 {stock}"))
  (is (= (c/line L P {} nil) "h0"))
  (is (= (c/line L P {} 1.5) "h0"))
  (is (= (c/line L P {} "2") "h0")))

(deftest t09-line-missing-paths
  (is (= (c/line L [:mirrors :fern :hungry] {} 0) nil))
  (is (= (c/line L [:mirrors :flora :fed] {} 0) nil))
  (is (= (c/line L [:mirrors :flora] {} 0) nil))
  (is (= (c/line L [] {} 0) nil))
  (is (= (c/line L [:stay-late] {} 0) nil))
  (is (= (c/line L nil {} 0) nil))
  (is (= (c/line L :jack-in {} 0) nil))
  (is (= (c/line nil P {} 0) nil))
  (is (= (c/line "x" P {} 0) nil)))

(deftest t10-line-shapes
  (is (= (c/line L [:mirrors :juniper :low] {:bales 3} 9) "bales 3"))
  (is (= (c/line L [:mirrors :odd :empty] {} 0) nil))
  (is (= (c/line L [:mirrors :odd :num] {} 0) nil))
  (is (= (c/line L [:mirrors :odd :mixed] {} 0) "ok"))
  (is (= (c/line L [:mirrors :odd :mixed] {} 1) nil))
  (is (= (c/line L [:mirrors :odd :mixed] {} 2) "ok")))

;; Keyword rules in the world (both mirrors)

(deftest t11-line-for
  (is (= (w/line-for WT :flora :stock-sign A) "jar 15"))
  (is (= (w/line-for WT :flora :stock-sign NEXT) "worms 15"))
  (is (= (w/line-for WT :flora "Worms: {stock}" A) "Worms: 15"))
  (is (= (w/line-for WT :juniper :stock-sign A) "barn 6"))
  (is (= (w/line-for WT :flora :fed A) nil))
  (is (= (w/line-for WT :flora 7 A) nil))
  (is (= (w/line-for WT :flora nil A) nil))
  (is (= (w/line-for WT :fern :hungry A) nil))
  (is (= (w/line-for (assoc W :lines {}) :flora :hungry A) nil)))

(deftest t12-flora-keyword-rules
  (is (= (w/markers WT :flora A) [{:say "h1"}]))
  (is (= (w/markers (st WT :flora :stock 2) :flora A) [{:say "h1"} {:sign "low 2"}]))
  (is (= (w/markers WT :flora DAY-END) [{:say "h1"}]))
  (is (= (w/markers WT :flora NEXT) [{:say "h2"}]))
  (is (= (w/markers (w/chore-done WT (ev :flora :feed A :key)) :flora (+ A 1000)) [])))

(deftest t13-juniper-keyword-rules
  (is (= (w/markers WT :juniper A) [{:say "hay1"}]))
  (is (= (w/markers (st WT :juniper :bales 3) :juniper A) [{:say "hay1"} {:sign "few 3"}]))
  (is (= (w/markers (st WT :juniper :bales 3) :juniper NEXT) [{:say "hay2"} {:sign "bales 3"}]))
  (is (= (w/markers (st WT :juniper :bales 0) :juniper A) [{:say "hay1"} {:sign "few 0"}]))
  (let [JT (w/chore-done WT (ev :juniper :hay A :key))]
    (is (= (w/markers JT :juniper (+ A 1000)) []))
    (is (= (w/markers JT :juniper (+ A 43200000)) [{:say "hay1"}]))))

(deftest t14-status-and-hud-lines
  (is (= (w/status-line WT :flora A) "Flora · stock 15 · h1"))
  (is (= (w/status-line (st WT :flora :stock 2) :flora A) "Flora · stock 2 · h1 · low 2"))
  (is (= (o/hud-line WT :juniper A) "Juniper (sample) · bales 6 · hay1"))
  (is (= (o/hud-line (o/apply-file WT {:as-of A :mirrors {:flora {:state {:stock 2}}}}) :flora A)
         "Flora (Hal) · stock 2 · h1 · low 2")))

(deftest t15-scene-labels-keyword-idle
  (is (= (texts WT A) {[:flora :body] "h1" [:flora :jar] "jar 15" [:juniper :body] "hay1"}))
  (is (= (texts WT NEXT) {[:flora :body] "h2" [:flora :jar] "worms 15" [:juniper :body] "hay2"}))
  (is (= (texts (w/chore-done WT (ev :flora :feed A :key)) (+ A 1000))
         {[:flora :body] "" [:flora :jar] "jar 14" [:juniper :body] "hay1"}))
  (is (= (texts (st WT :flora :stock 2) A) {[:flora :body] "h1" [:flora :jar] "low 2" [:juniper :body] "hay1"})))

(deftest t16-no-per-frame-flicker
  (is (true? (every? #(= (texts WT A) (texts WT (+ A (* 16 %)))) (range 60))))
  (is (true? (every? #(= (texts W A) (texts W (+ A (* 16 %)))) (range 60))))
  (is (= (p/label-changes (texts WT A) (p/scene-plan WT (+ A 16))) {}))
  (is (= (p/label-changes (texts W A) (p/scene-plan W (+ A 16))) {}))
  (is (= (p/label-changes (texts WT DAY-END) (p/scene-plan WT NEXT))
         {[:flora :body] "h2" [:flora :jar] "worms 15" [:juniper :body] "hay2"})))

(deftest t17-string-and-keyword-rules-mixed
  (is (= (w/markers MXL :fern A) [{:say "thirsty 1"} {:sign "dry 1"} {:say "dry 1" :sign "sign 1"}]))
  (is (= (w/markers MX :fern A) [{:say "thirsty 1"} {:sign "sign 1"}]))
  (is (= (w/status-line MXL :fern A) "fern · thirsty 1 · dry 1 · dry 1 · sign 1"))
  (is (= (texts MXL A) {[:fern :pot] "dry 1 · sign 1" [:fern :leaf] "thirsty 1 · dry 1"}))
  (is (= (texts (st MXL :fern :water 9) A) {[:fern :pot] "calm 9" [:fern :leaf] "leaf 9"}))
  (is (= (texts (st MX :fern :water 9) A) {[:fern :pot] "" [:fern :leaf] "leaf 9"})))

;; The real generated files

(deftest t18-real-lines-both-mirrors
  (let [markers-flora (w/markers W :flora A)]
    (is (= (count markers-flora) 1))
    (is (contains? (filled c/mirror-lines [:mirrors :flora :hungry] {:stock 15}) (:say (first markers-flora)))))
  (let [second-marker (second (w/markers (st W :flora :stock 2) :flora A))]
    (is (contains? (filled c/mirror-lines [:mirrors :flora :low] {:stock 2}) (:sign second-marker))))
  (is (contains? (filled c/mirror-lines [:mirrors :flora :stock-sign] {:stock 15}) (get (texts W A) [:flora :jar])))
  (let [markers-juniper (w/markers W :juniper A)]
    (is (= (count markers-juniper) 1))
    (is (contains? (filled c/mirror-lines [:mirrors :juniper :hungry] {:bales 6}) (:say (first markers-juniper)))))
  (let [second-marker (second (w/markers (st W :juniper :bales 3) :juniper A))]
    (is (contains? (filled c/mirror-lines [:mirrors :juniper :low] {:bales 3}) (:sign second-marker))))
  (is (= (w/markers W :flora A) (w/markers W :flora DAY-END)))
  (is (= (w/markers W :juniper A) (w/markers W :juniper DAY-END)))
  (is (= (texts W A) (texts W DAY-END)))
  (is (not-any? #(and (string? %) (re-find #"\{" %)) (vals (texts (st W :flora :stock 2) A))))
  (is (= (w/markers (assoc W :lines "bad") :flora A) (w/markers W :flora A))))

(deftest t19-coverage-mirror-lines
  (is (= (c/used-lines W)
         #{[:flora :hungry] [:flora :low] [:flora :stock-sign] [:juniper :hungry] [:juniper :low]}))
  (is (= (c/missing-lines W c/mirror-lines) #{}))
  (doseq [[id k] (c/used-lines W)
          s (variants c/mirror-lines [:mirrors id k])]
    (is (set/subset? (c/placeholders s) (set (keys (get-in W [:mirrors id :state])))))))

(deftest t20-coverage-hal-lines
  (is (= (set (keys c/hal-events)) #{:jack-in :chore-done :chore-not-due :chore-undone}))
  (is (= (c/missing-hal c/hal-lines) #{}))
  (doseq [[e allowed] c/hal-events
          s (variants c/hal-lines [e])]
    (is (set/subset? (c/placeholders s) allowed))))

;; Hal in the HUD

(deftest t21-hal-jack-in
  (is (= (:hal S) {:event :jack-in :text "hello" :until 1794013206000}))
  (is (= (c/hal-text S A) "hello"))
  (is (= (c/hal-text S (+ A 5999)) "hello"))
  (is (= (c/hal-text S (+ A 6000)) nil))
  (is (= (c/hal-text W A) nil))
  (is (= (c/hal-text S nil) nil))
  (is (= (c/hal-segment S (+ A 1000)) "Hal: hello"))
  (is (= (c/hal-segment S (+ A 6000)) nil))
  (is (= (dissoc S :hal) W))
  (is (= c/hal-ms 6000)))

(deftest t22-hal-say-edges
  (is (= (:text (:hal (c/hal-say W H :jack-in {} NEXT))) "hey"))
  (is (= (c/hal-say W H :stay-late {} A) W))
  (is (= (c/hal-say W H :empty {} A) W))
  (is (= (c/hal-say W H :jack-in {} nil) W))
  (is (= (c/hal-say W nil :jack-in {} A) W))
  (is (= (:text (:hal (c/hal-say W H :chore-done {:name "Flora" :chore "feed"} A))) "Flora: feed done"))
  (is (= (:hal (c/hal-say S H :chore-done {:name "Juniper" :chore "hay"} (+ A 2000)))
         {:event :chore-done :text "Juniper: hay done" :until 1794013208000}))
  (is (contains? (set (variants c/hal-lines [:jack-in])) (c/hal-text (c/hal-say W c/hal-lines :jack-in {} A) A))))

(deftest t23-hal-after-f-flora
  (is (= (:result (peek (:log K))) :counted))
  (is (= (:hal K1) {:event :chore-done :text "did feed for Flora" :until 1794013206000}))
  (let [K2 (w/chore-done K1 (ev :flora :feed (+ A 1000) :key))]
    (is (= (:result (peek (:log K2))) :ignored))
    (is (= (c/hal-on-chore K2 H) K2))
    (is (= (c/hal-text K2 (+ A 1000)) "did feed for Flora"))
    (is (= (c/hal-text K2 (+ A 6000)) nil))
    (is (= (:hal (c/hal-on-chore (w/chore-done K2 (ev :flora :feed (+ A D2) :key)) H))
           {:event :chore-done :text "did feed for Flora" :until 1794186006000})))
  (is (contains? (filled c/hal-lines [:chore-done] {:name "Flora" :chore "feed"})
                 (c/hal-text (c/hal-on-chore K c/hal-lines) A)))
  (is (not (re-find #"\{" (c/hal-text (c/hal-on-chore K c/hal-lines) A)))))

(deftest t24-hal-after-chore-others
  (is (= (:hal (c/hal-on-chore (w/chore-done W (ev :juniper :hay A :key)) H))
         {:event :chore-done :text "did hay for Juniper" :until 1794013206000}))
  (is (= (c/hal-text (c/hal-on-chore (w/chore-done FX (ev :fern :mist A :key)) H) A) "did mist for fern"))
  (is (= (c/hal-on-chore W H) W))
  (let [R (w/chore-done W (ev :rock :feed A :key))]
    (is (= (c/hal-on-chore R H) R))))

;; Mailbox

(deftest t25-mail-toy
  (is (= (c/mail-counts IB) {:mail 4 :flagged 1}))
  (is (= (c/mail-line IB) "mail 4 · 1 flagged"))
  (is (= (c/mail-counts {:messages [{:id :a}]}) {:mail 1 :flagged 0}))
  (is (= (c/mail-line {:messages [{:id :a}]}) "mail 1"))
  (is (= (c/mail-counts {:messages []}) {:mail 0 :flagged 0}))
  (is (= (c/mail-line {:messages []}) "mail 0"))
  (is (= (c/mail-counts {:messages [{:clerk-note "x"} {:clerk-note "y"}]}) {:mail 0 :flagged 2}))
  (is (= (c/mail-line {:messages [{:clerk-note "x"} {:clerk-note "y"}]}) "mail 0 · 2 flagged"))
  (is (= (c/mail-counts {:messages (list {:id :a})}) {:mail 1 :flagged 0}))
  (is (= (c/mail-counts nil) nil))
  (is (= (c/mail-line nil) nil))
  (is (= (c/mail-counts "x") nil))
  (is (= (c/mail-line "x") nil))
  (is (= (c/mail-counts {}) nil))
  (is (= (c/mail-line {}) nil))
  (is (= (c/mail-counts {:messages "x"}) nil))
  (is (= (c/mail-line {:messages "x"}) nil)))

(deftest t26-mail-real-file
  (let [{:keys [mail flagged]} (c/mail-counts c/sample-inbox)]
    (is (integer? mail))
    (is (pos? mail))
    (is (>= flagged 1))
    (is (= (+ mail flagged) (count (:messages c/sample-inbox))))
    (is (= (c/mail-line c/sample-inbox) (str "mail " mail " · " flagged " flagged")))
    (is (= (c/mail-line c/sample-inbox) "mail 9 · 1 flagged"))
    (doseq [m (:messages c/sample-inbox)]
      (when (and (string? (:clerk-note m)) (re-find #"\S" (:clerk-note m)))
        (is (= (:kind m) :suspicious)))
      (is (= (:sample m) true)))))

(deftest t27-hud-segments
  (is (= (c/hud-segments K1 IB (+ A 1000)) ["mail 4 · 1 flagged" "Hal: did feed for Flora"]))
  (is (= (c/hud-segments K1 IB (+ A 6000)) ["mail 4 · 1 flagged"]))
  (is (= (c/hud-segments S nil A) ["Hal: hello"]))
  (is (= (c/hud-segments W nil A) [])))

(deftest t28-coverage-catches-a-broken-regeneration
  (is (= (c/missing-lines W (update-in c/mirror-lines [:mirrors :flora] dissoc :low)) #{[:flora :low]}))
  (is (= (c/missing-lines W (assoc-in c/mirror-lines [:mirrors :juniper :hungry] [])) #{[:juniper :hungry]}))
  (is (= (c/missing-lines W {}) (c/used-lines W)))
  (is (= (c/missing-lines W nil) (c/used-lines W)))
  (is (= (c/used-lines MX) #{[:fern :dry] [:fern :missing] [:fern :calm]}))
  (is (= (c/missing-lines MX (:lines MXL)) #{[:fern :missing]}))
  (is (= (c/missing-hal (dissoc c/hal-lines :jack-in)) #{:jack-in}))
  (is (= (c/missing-hal {}) #{:jack-in :chore-done :chore-not-due :chore-undone}))
  (is (= (c/missing-hal {:jack-in [] :chore-done "x"}) #{:jack-in :chore-not-due :chore-undone}))
  (is (= (c/missing-hal {:jack-in ["a" 7] :chore-done ["b"]}) #{:jack-in :chore-not-due :chore-undone})))
