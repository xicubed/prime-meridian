(ns pm.crew-fielder-v0-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs]
            [pm.jumeaux :as j]
            [pm.crew-log :as cl]
            [pm.bridge :as b]
            [pm.prompt :as pr]
            [pm.crew :as crew]
            [pm.fielder :as f]
            [pm.talk :as tk]
            [pm.overlay :as o]
            [pm.content :as c]
            [pm.world :as w]
            [pm.fixtures :as fx]))

(def A 1794013200000)            ; 5:00 PM at offset -480
(def OFF -480)
(def R "/repo")
(def ID1 "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6")  ; node -e 'console.log(6*7)'
(def ID2 "06b65e49c507048bccab6121f64aaad2518f3b83")  ; curl --version
(def ID3 "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787")  ; ls
(def IDE "6a049a2c7e97372a1caad99f23478183fe038e13")  ; unknown Edit file …
(def IDF1 "8d319583b6740bf75c9a1f4ba183adb09767f717") ; fixture F1
(def T1X "grep -n \"File:\" /tmp/full-compile.txt")
(def F1 (.readFileSync fs ".crew/quests/crew-talk-v1/screens/un-automate-monde-190664ad70.txt" "utf8"))
(defn lines [& ls] (str/join "\n" ls))
(def LS-SCREEN   (lines " Bash command" " ╌╌╌╌" " ls" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No"))
(def CURL-SCREEN (lines " Bash command" " ╌╌╌╌" " curl --version" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No"))
(def THINK "· Cogitating… (5m 16s …)")
(def JM {:jumeaux {:player {:name "Michael" :sex :m} :twin {:name "Hal"}}})
(def JA {:jumeaux {:player {:name "Claire" :sex :f} :twin {:name "Ada"}}})
(def NM {:player "Michael" :sex :m :twin "Hal" :twin-word "jumeau" :pair-word "jumeaux"})
(def NA {:player "Claire" :sex :f :twin "Ada" :twin-word "jumelle" :pair-word "jumelles"})
(def ND {:player "You" :sex nil :twin "Your twin" :twin-word "jumeau" :pair-word "jumeaux"})
(def QB  {:id ID3 :kind :bash :text "ls" :risky false :since A})
(def QB2 {:id ID1 :kind :bash :text "node -e 'console.log(6*7)'" :risky false :since (+ A 1000)})
(def QR  {:id ID2 :kind :bash :text "curl --version" :risky true :since A})
(def QU  {:id IDE :kind :unknown :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true :since A})
(def TI {:un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur" :un-architecte "Un Architecte"})
(defn crew-at [at agents] {:as-of at :agents agents :titles TI})
(def HOLD {:hold? true :surface-after-s 60})
(def T0 tk/empty-talk)
(defn e [t n] (tk/entry t n))
(def L1    {:at (+ A 5000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"})
(def L2    {:at (+ A 7000) :member :un-automate-monde :action :declined :by :twin :prompt-id ID2 :text "curl --version"})
(def L3    {:at (+ A 9000) :member :un-automate-monde :action :allowed :by :player :prompt-id ID1 :text "node -e 'console.log(6*7)'"})
(def L1old (assoc L1 :by :hal)) (def L2old (assoc L2 :by :hal)) (def L3old (assoc L3 :by :michael))
(def C (crew-at A {:le-testeur {:status :idle} :un-automate-monde {:status :blocked}}))
(def W (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(def MEM #{:le-testeur :un-automate-monde :un-architecte})
(def CS (crew-at (+ A 2000) {:le-testeur {:status :blocked :prompt QB}}))
(def CTX {:crew CS :hold nil :members MEM :log [] :tried {} :now (+ A 5000) :crew-talk {:answer-after-s 5}})
(defn v [ctx] (f/verdict ctx :le-testeur))
(defn with-prompt [p] (assoc CTX :crew (crew-at (+ A 2000) {:le-testeur {:status :blocked :prompt p}})))

;; 4.1 pm.jumeaux

(deftest t01-michaels-sample
  (is (= NM (j/names JM))))

(deftest t02-the-f-ada-example-same-code-path
  (is (= NA (j/names JA))))

(deftest t03-missing-block-defaults-never-an-error
  (doseq [x [nil {} "x" 42 [] {:jumeaux nil} {:jumeaux "Hal"} {:jumeaux {}}
             {:jumeaux {:player "Michael" :twin "Hal"}}]]
    (is (= ND (j/names x)) x))
  (is (= ND j/defaults)))

(deftest t04-bad-sex-falls-back-to-jumeau-jumeaux-names-kept
  (doseq [s [:x "f" :F nil 1]]
    (is (= {:player "Claire" :sex nil :twin "Ada" :twin-word "jumeau" :pair-word "jumeaux"}
           (j/names (assoc-in JA [:jumeaux :player :sex] s)))
        s))
  (is (= {:player "Claire" :sex nil :twin "Ada" :twin-word "jumeau" :pair-word "jumeaux"}
         (j/names (update-in JA [:jumeaux :player] dissoc :sex)))))

(deftest t05-name-cleaning
  (let [tw (fn [n] (:twin (j/names (assoc-in JA [:jumeaux :twin :name] n))))]
    (is (= "Ada" (tw "  Ada  ")))
    (doseq [n ["" "   " nil :Ada "A\tda" (apply str (repeat 33 "a"))]]
      (is (= "Your twin" (tw n)) n))
    (is (= (apply str (repeat 32 "a")) (tw (apply str (repeat 32 "a")))))
    (is (= (apply str (repeat 32 "a")) (tw (str " " (apply str (repeat 32 "a")) " ")))))
  (is (= "You" (:player (j/names (assoc-in JA [:jumeaux :player :name] "")))))
  (is (= "Ada" (:twin (j/names (assoc-in JA [:jumeaux :player] nil)))))
  (is (nil? (:sex (j/names (assoc-in JA [:jumeaux :player] nil))))))

(deftest t06-words-and-twin
  (is (= "jumeau" (j/twin-word :m)))
  (is (= "jumelle" (j/twin-word :f)))
  (is (= "jumeau" (j/twin-word nil)))
  (is (= "jumeau" (j/twin-word :x)))
  (is (= "jumeaux" (j/pair-word :m)))
  (is (= "jumelles" (j/pair-word :f)))
  (is (= "jumeaux" (j/pair-word nil)))
  (is (= "Ada" (j/twin NA)))
  (is (= "Hal" (j/twin NM)))
  (is (= "Your twin" (j/twin nil)))
  (is (= "Your twin" (j/twin {:twin ""})))
  (is (= "Your twin" (j/twin {:twin 5})))
  (is (= "Your twin" (j/twin "Ada"))))

(deftest t07-the-shipped-estate
  (is (= {:player {:name "Michael" :sex :m} :twin {:name "Hal"}} (:jumeaux (w/load-estate w/estate-edn))))
  (is (= NM (j/names (w/load-estate w/estate-edn))))
  (is (= NM (j/names W))))

;; 4.2 pm.crew-log

(deftest t08-role
  (is (= :twin (cl/role :twin)))
  (is (= :player (cl/role :player)))
  (is (= :twin (cl/role :hal)))
  (is (= :player (cl/role :michael)))
  (doseq [x [:manager :x "twin" nil]]
    (is (nil? (cl/role x)) x)))

(deftest t09-upgrade-entry
  (is (= L1 (cl/upgrade-entry L1old)))
  (is (= L3 (cl/upgrade-entry L3old)))
  (is (= L1 (cl/upgrade-entry L1)))
  (is (= (assoc L1 :by :x) (cl/upgrade-entry (assoc L1 :by :x))))
  (is (nil? (cl/upgrade-entry nil)))
  (is (= {:a 1} (cl/upgrade-entry {:a 1}))))

(deftest t10-valid-entry-is-strict-on-the-new-keys
  (doseq [entry [L1 L2 L3]]
    (is (true? (cl/valid-entry? entry)) entry))
  (doseq [entry [L1old L3old (assoc L1 :by :manager) (assoc L1 :by nil)]]
    (is (false? (cl/valid-entry? entry)) entry))
  (is (= #{:twin :player} cl/bys))
  (is (= {:hal :twin :michael :player} cl/legacy-bys)))

(deftest t11-parse-reads-old-entries-as-new-never-drops-them
  (is (= [L1 L2 L3] (cl/parse (pr-str [L1old {:bad 1} L2old L3old]))))
  (is (= [L1 L3] (cl/parse (pr-str [L1 L3old]))))
  (is (= [] (cl/parse (pr-str [(assoc L1 :by :x)]))))
  (let [parsed (cl/parse (pr-str (mapv #(assoc L1old :at (+ A %)) (range 205))))]
    (is (= 200 (count parsed)))
    (is (= :twin (:by (first parsed))))))

(deftest t12-append-upgrades
  (is (= [L1 L2] (cl/append [L1old] L2)))
  (is (= [L1 L3] (cl/append [L1] L3old)))
  (is (= [L1] (cl/append [L1] (assoc L2 :by :x))))
  (is (= [L1] (cl/append nil L1old))))

(deftest t13-entry-with-roles
  (is (= L1 (cl/entry (+ A 5000) :le-testeur :allowed :twin {:id ID3 :kind :bash :text "ls" :risky false})))
  (is (= L3 (cl/entry (+ A 9000) :un-automate-monde :allowed :player {:id ID1 :text "node -e 'console.log(6*7)'"}))))

;; 4.3 pm.bridge

(deftest t14-the-403-reason-names-no-one
  (is (= {:ok false :status 403 :reason "risky: your twin reviews this one"}
         (b/decide {:ok true :member :le-testeur :action :allow :prompt-id ID2 :text nil}
                    {:status :blocked :prompt (dissoc QR :since)})))
  (is (= {:ok true :steps [[:keys "1"]] :reason "allowed"}
         (b/decide {:action :allow :prompt-id ID3 :text nil} {:status :blocked :prompt (dissoc QB :since)}))))

;; 4.4 pm.talk: names from data

(deftest t15-observe-takes-the-twins-name-from-the-world
  (let [WA (assoc W :jumeaux (:jumeaux JA) :hal {:event :jack-in :text "Welcome back." :until (+ A 6000)})]
    (is (= {:n 1 :who "Ada" :text "Welcome back." :at A :kind :hal :event :jack-in :show-at A :show-until (+ A 2500)}
           (first (:entries (tk/observe T0 WA A)))))
    (is (= "Hal" (:who (first (:entries (tk/observe T0 (assoc WA :jumeaux (:jumeaux JM)) A))))))
    (is (= "Your twin" (:who (first (:entries (tk/observe T0 (dissoc WA :jumeaux) A))))))))

(deftest t16-observe-logs-quiet-line-uses-the-names
  (is (= [{:n 1 :who "Ada" :text "allowed `ls` for Le Testeur" :at (+ A 5000) :kind :crew-log
           :member :le-testeur :log L1}]
         (:entries (tk/observe-log T0 [L1] C (+ A 6000) NA))))
  (is (= (+ A 5000) (:log-at (tk/observe-log T0 [L1] C (+ A 6000) NA))))
  (is (= "Ada allowed `ls` for Le Testeur · 5:00 PM"
         (tk/crew-label (e (tk/observe-log T0 [L1] C (+ A 6000) NA) 1) OFF)))
  (is (= "Hal" (:who (e (tk/observe-log T0 [L1] C (+ A 6000) NM) 1))))
  (is (= "Your twin" (:who (e (tk/observe-log T0 [L1] C (+ A 6000)) 1)))))

(deftest t17-observe-log-reads-legacy-entries
  (is (= (tk/observe-log T0 [L1] C (+ A 6000) NA) (tk/observe-log T0 [L1old] C (+ A 6000) NA)))
  (is (= L1 (:log (e (tk/observe-log T0 [L1old] C (+ A 6000) NA) 1))))
  (let [r (tk/observe-log T0 [L3old] C (+ A 10000) NA)]
    (is (= [] (:entries r)))
    (is (= (+ A 9000) (:log-at r)))))

(deftest t18-pending-risky-unknown-lines-name-the-twin
  (let [Y (tk/observe-crew T0 (crew-at A {:un-automate-monde {:status :blocked :prompt QR}}) A HOLD)
        U (tk/observe-crew T0 (crew-at A {:le-testeur {:status :blocked :prompt QU}}) A HOLD)
        Z (tk/observe-crew T0 (crew-at A {:le-testeur {:status :blocked :prompt QB}}) A HOLD)]
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · Ada reviews this one" (tk/crew-label (e Y 1) OFF NA)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · Hal reviews this one" (tk/crew-label (e Y 1) OFF NM)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · Your twin reviews this one" (tk/crew-label (e Y 1) OFF)))
    (is (= "⚠ Le Testeur · is waiting at a prompt: Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs? · Ada reviews this one"
           (tk/crew-label (e U 1) OFF NA)))
    (is (= "Le Testeur · wants to run ls" (tk/crew-label (e Z 1) OFF NA)))))

(deftest t19-already-answered-by-from-new-and-legacy-log-entries
  (let [Y (tk/observe-crew T0 (crew-at A {:un-automate-monde {:status :blocked :prompt QR}}) A HOLD)
        r (tk/observe-log Y [L2] C (+ A 8000) NA)]
    (is (= :stale (:state (e r 1))))
    (is (= :twin (:by (e r 1))))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by Ada" (tk/crew-label (e r 1) OFF NA)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by Your twin" (tk/crew-label (e r 1) OFF)))
    (is (= {:n 2 :who "Ada" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
            :kind :crew-log :member :un-automate-monde :log L2}
           (e r 2)))
    (is (= r (tk/observe-log Y [L2old] C (+ A 8000) NA)))
    (let [rp (tk/observe-log Y [(assoc L2 :by :player)] C (+ A 8000) NA)]
      (is (= :player (:by (e rp 1))))
      (is (= 1 (count (:entries rp))))
      (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by you" (tk/crew-label (e rp 1) OFF NA)))
      (is (= rp (tk/observe-log Y [(assoc L2 :by :michael)] C (+ A 8000) NA))))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by Ada"
           (tk/crew-label (assoc (e r 1) :by :hal) OFF NA)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by you"
           (tk/crew-label (assoc (e r 1) :by :michael) OFF NA)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered"
           (tk/crew-label (assoc (e r 1) :by :x) OFF NA)))))

(deftest t20-log-label
  (is (= "Ada allowed `ls` · 5:00 PM" (tk/log-label L1 OFF NA)))
  (is (= "Ada allowed `ls` · 5:00 PM" (tk/log-label L1old OFF NA)))
  (is (= "You allowed `node -e 'console.log(6*7)'` · 5:00 PM" (tk/log-label L3 OFF NA)))
  (is (= "You allowed `node -e 'console.log(6*7)'` · 5:00 PM" (tk/log-label L3old OFF NA)))
  (is (= "Your twin allowed `ls` · 5:00 PM" (tk/log-label L1 OFF)))
  (is (= "Hal declined `curl --version` · 5:01 PM" (tk/log-label (assoc L2 :at (+ A 60000)) OFF NM))))

(deftest t21-the-switchs-hints
  (is (= {:checked? true :disabled? false :hint "you answer; Ada holds off"} (tk/hold-switch {:on true :at 5} true {} NA)))
  (is (= {:checked? false :disabled? false :hint "Ada answers the easy ones"} (tk/hold-switch nil true nil NA)))
  (is (= "you answer; Your twin holds off" (:hint (tk/hold-switch {:on true :at 5} true {}))))
  (is (= "Your twin answers the easy ones" (:hint (tk/hold-switch nil true nil))))
  (is (= {:checked? true :disabled? true :hint "bridge off"} (tk/hold-switch {:on true :at 5} false {} NA))))

(def SAVED-MAP {:v 1 :n 2 :seen 2 :log-at (+ A 7000)
  :entries [{:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
             :member :un-automate-monde :prompt QR :state :stale :by :hal}
            {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
             :kind :crew-log :member :un-automate-monde :log L2old}]})
(def R1 {:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
         :member :un-automate-monde :prompt QR :state :stale :by :twin})
(def R2 {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
         :kind :crew-log :member :un-automate-monde :log L2})

(deftest t22-restore-upgrades-a-saved-history
  (is (= [R1 R2] (:entries (tk/restore (pr-str SAVED-MAP)))))
  (is (= [(assoc R1 :by :player) R2] (:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 0 :by] :michael))))))
  (is (= [(dissoc R1 :by) R2] (:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 0 :by] :x))))))
  (is (= [R1] (:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 1 :log :by] :x)))))))

;; 4.5 pm.overlay / pm.content

(deftest t23-source-tag
  (is (= "Ada" (o/source-tag :hal false NA)))
  (is (= "Ada, cached" (o/source-tag :hal true NA)))
  (is (= "Hal" (o/source-tag :hal nil NM)))
  (is (= "sample" (o/source-tag :sample true NA)))
  (is (= "phone" (o/source-tag :phone false NA)))
  (is (nil? (o/source-tag nil true NA)))
  (is (nil? (o/source-tag "hal" false NA)))
  (is (= "Your twin" (o/source-tag :hal)))
  (is (= "Your twin, cached" (o/source-tag :hal true))))

(deftest t24-hud-line-reads-the-world-for-both-mirrors
  (let [W2 (o/apply-file W {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}})
        WJ (o/apply-file W {:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed A}}}})]
    (is (= "Flora (Ada) · stock 37" (o/hud-line (assoc W2 :jumeaux (:jumeaux JA)) :flora (+ A 1000))))
    (is (= "Flora (Hal) · stock 37" (o/hud-line W2 :flora (+ A 1000))))
    (is (= "Flora (Your twin) · stock 37" (o/hud-line (dissoc W2 :jumeaux) :flora (+ A 1000))))
    (is (= "Juniper (Ada) · bales 3 · Bales left: 3" (o/hud-line (assoc WJ :jumeaux (:jumeaux JA)) :juniper (+ A 1000))))
    (is (= "Flora (sample) · stock 15 · I'm hungry" (o/hud-line (assoc WJ :jumeaux (:jumeaux JA)) :flora A)))))

(deftest t25-hal-segment-reads-the-world
  (let [S (assoc W :hal {:event :jack-in :text "hello" :until (+ A 6000)})]
    (is (= "Ada: hello" (c/hal-segment (assoc S :jumeaux (:jumeaux JA)) (+ A 1000))))
    (is (= "Hal: hello" (c/hal-segment S (+ A 1000))))
    (is (= "Your twin: hello" (c/hal-segment (dissoc S :jumeaux) (+ A 1000))))
    (is (nil? (c/hal-segment (assoc S :jumeaux (:jumeaux JA)) (+ A 6000))))))

;; 4.6 No "Hal" in code

(defn code-files [] (vec (for [dir ["src/pm" "scripts"] f (.readdirSync fs dir) :when (re-find #"\.clj[cs]?$" f)] (str dir "/" f))))
(defn hal-hits [] (vec (for [p (code-files) [i l] (map-indexed vector (str/split-lines (.readFileSync fs p "utf8")))
                              :when (re-find #"\bHal\b" l)] (str p ":" (inc i)))))

(deftest t26-no-hal-in-code
  (is (= [] (hal-hits)))
  (let [files (set (code-files))]
    (doseq [p ["src/pm/talk.cljs" "src/pm/bridge.cljc" "src/pm/jumeaux.cljc" "src/pm/fielder.cljc"
               "scripts/crew_fielder.clj" "scripts/crew_bridge.clj"]]
      (is (contains? files p) p)))
  (is (nil? (re-find #"\bHal\b" "(def x :hal)")))
  (is (some? (re-find #"\bHal\b" "Hal's"))))

;; 4.7 pm.fielder: the pure decision

(deftest t27-answer-after-s
  (is (= 5 (f/answer-after-s nil)))
  (is (= 5 (f/answer-after-s {})))
  (is (= 5 (f/answer-after-s {:surface-after-s 60})))
  (is (= 10 (f/answer-after-s {:answer-after-s 10})))
  (is (= 0 (f/answer-after-s {:answer-after-s 0})))
  (is (= 3600 (f/answer-after-s {:answer-after-s 3600})))
  (is (= 2.5 (f/answer-after-s {:answer-after-s 2.5})))
  (is (= 5 (f/answer-after-s {:answer-after-s 3601})))
  (is (= 5 (f/answer-after-s {:answer-after-s -1})))
  (is (= 5 (f/answer-after-s {:answer-after-s "7"}))))

(deftest t28-constants-and-the-estate
  (is (= 5 f/default-answer-after-s))
  (is (= 2000 f/every-ms))
  (is (= 15000 f/stale-ms crew/stale-ms))
  (is (= {:surface-after-s 60 :answer-after-s 5} (:crew-talk (w/load-estate w/estate-edn))))
  (is (= 5 (f/answer-after-s (:crew-talk (w/load-estate w/estate-edn)))))
  (is (= 60 (:surface-after-s (tk/policy (:crew-talk (w/load-estate w/estate-edn)) nil)))))

(deftest t29-benign-answer-after-the-delay-from-data
  (is (= :answer (v CTX)))
  (is (= :too-soon (v (assoc CTX :now (+ A 4999)))))
  (is (= :answer (v (assoc CTX :crew-talk nil))))
  (is (= :too-soon (v (assoc CTX :crew-talk {:answer-after-s 10}))))
  (is (= :answer (v (assoc CTX :crew-talk {:answer-after-s 10} :now (+ A 10000)))))
  (is (= :answer (v (assoc CTX :crew-talk {:answer-after-s 0} :now (+ A 2000))))))

(deftest t30-hold-on-no
  (is (= :hold (v (assoc CTX :hold {:on true :at A}))))
  (is (= :answer (v (assoc CTX :hold {:on false :at A})))))

(deftest t31-stale-crew-edn-no
  (is (= :stale (v (assoc CTX :now (+ A 17001)))))
  (is (= :answer (v (assoc CTX :now (+ A 17000)))))
  (is (= :stale (v (assoc CTX :crew nil))))
  (is (= :stale (v (assoc CTX :crew (dissoc CS :as-of))))))

(deftest t32-not-in-the-roster-no
  (is (= :not-in-roster (v (assoc CTX :members #{:un-automate-monde}))))
  (is (= :not-in-roster (v (assoc CTX :members #{}))))
  (is (= :not-in-roster (v (assoc CTX :members nil)))))

(deftest t33-not-blocked-no
  (is (= :not-blocked (v (assoc CTX :crew (crew-at (+ A 2000) {:le-testeur {:status :working}})))))
  (is (= :not-blocked (v (assoc CTX :crew (crew-at (+ A 2000) {:le-testeur {:status :blocked}})))))
  (is (= :not-blocked (f/verdict CTX :un-automate-monde))))

(deftest t34-unknown-no
  (is (= :unknown (v (with-prompt QU))))
  (is (= :unknown (v (with-prompt (assoc QB :kind :unknown))))))

(deftest t35-risky-no
  (is (= :risky (v (with-prompt QR))))
  (is (= :risky (v (with-prompt (assoc QB :risky nil)))))
  (is (= :risky (v (with-prompt (assoc QB :risky "false"))))))

(deftest t36-no-since-no
  (is (= :no-since (v (with-prompt (dissoc QB :since)))))
  (is (= :no-since (v (with-prompt (assoc QB :since 1.5))))))

(deftest t37-already-answered-by-the-fielder-memory-vs-changed-id-or-new-sighting
  (is (= :answered (v (assoc CTX :tried {:le-testeur {:id ID3 :since A}}))))
  (is (= :answer (v (assoc CTX :tried {:le-testeur {:id ID3 :since (- A 60000)}}))))
  (is (= :answer (v (assoc CTX :tried {:le-testeur {:id ID1 :since A}}))))
  (is (= :answer (v (assoc CTX :tried {:un-automate-monde {:id ID3 :since A}})))))

(deftest t38-already-answered-per-the-log-anyone
  (let [LP {:at (+ A 3000) :member :le-testeur :action :allowed :by :player :prompt-id ID3 :text "ls"}]
    (is (= :answered (v (assoc CTX :log [LP]))))
    (is (= :answered (v (assoc CTX :log [(assoc LP :at A)]))))
    (is (= :answered (v (assoc CTX :log [(assoc LP :action :declined :by :twin)]))))
    (is (= :answer (v (assoc CTX :log [(assoc LP :at (dec A))]))))
    (is (= :answer (v (assoc CTX :log [(assoc LP :member :un-automate-monde)]))))
    (is (= :answer (v (assoc CTX :log [(assoc LP :prompt-id ID1)]))))))

(deftest t39-check-order
  (is (= :hold (v (assoc (with-prompt QR) :hold {:on true :at A}))))
  (is (= :hold (v (assoc CTX :hold {:on true :at A} :crew nil))))
  (is (= :stale (v (assoc CTX :crew nil :members #{}))))
  (is (= :not-in-roster (v (assoc (with-prompt QU) :members #{}))))
  (is (= :unknown (v (with-prompt (dissoc QU :since)))))
  (is (= :risky (v (with-prompt (dissoc QR :since)))))
  (is (= :answered (v (assoc CTX :now (+ A 1000) :tried {:le-testeur {:id ID3 :since A}})))))

(deftest t40-due
  (let [CS3 (crew-at (+ A 2000) {:le-testeur {:status :blocked :prompt QB}
                                  :un-automate-monde {:status :blocked :prompt QB2}
                                  :un-architecte {:status :blocked :prompt QR}
                                  :le-critique {:status :blocked :prompt QB}})]
    (is (= [{:member :le-testeur :prompt QB}] (f/due (assoc CTX :crew CS3))))
    (is (= [{:member :le-testeur :prompt QB} {:member :un-automate-monde :prompt QB2}]
           (f/due (assoc CTX :crew CS3 :now (+ A 6000)))))
    (is (= [] (f/due (assoc CTX :crew CS3 :hold {:on true :at A}))))
    (is (= [] (f/due (assoc CTX :crew nil))))))

(deftest t41-attempt-is-the-bridges-decide
  (is (= {:ok true :steps [[:keys "1"]] :reason "allowed"}
         (f/attempt QB {:status :blocked :prompt (dissoc QB :since)})))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (f/attempt QB {:status :blocked :prompt (dissoc QB2 :since)})))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (f/attempt QB {:status :blocked :prompt nil})))
  (is (= {:ok false :status 409 :reason "not blocked"}
         (f/attempt QB {:status :working :prompt nil})))
  (is (= {:ok false :status 403 :reason "risky: your twin reviews this one"}
         (f/attempt QR {:status :blocked :prompt (dissoc QR :since)})))
  (is (= {:ok false :status 403 :reason "unknown prompt"}
         (f/attempt QU {:status :blocked :prompt (dissoc QU :since)})))
  (doseq [r [(f/attempt QB {:status :blocked :prompt (dissoc QB :since)})
             (f/attempt QB {:status :blocked :prompt (dissoc QB2 :since)})
             (f/attempt QB {:status :blocked :prompt nil})
             (f/attempt QB {:status :working :prompt nil})
             (f/attempt QR {:status :blocked :prompt (dissoc QR :since)})
             (f/attempt QU {:status :blocked :prompt (dissoc QU :since)})]]
    (is (contains? #{nil [[:keys "1"]]} (:steps r)) r)))

(deftest t42-attempt-on-real-screens-live-classifier-wins
  (is (= {:id ID3 :kind :bash :text "ls" :risky false} (pr/read-prompt LS-SCREEN R)))
  (is (true? (:ok (f/attempt QB {:status :blocked :prompt (pr/read-prompt LS-SCREEN R)}))))
  (is (= {:id ID2 :kind :bash :text "curl --version" :risky true} (pr/read-prompt CURL-SCREEN R)))
  (is (= {:ok false :status 403 :reason "risky: your twin reviews this one"}
         (f/attempt (assoc QR :risky false) {:status :blocked :prompt (pr/read-prompt CURL-SCREEN R)})))
  (is (true? (:ok (f/attempt {:id IDF1 :kind :bash :text T1X :risky false :since A}
                              {:status :blocked :prompt (pr/read-prompt F1 R)}))))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (f/attempt QB {:status :blocked :prompt (pr/read-prompt THINK R)}))))

(deftest t43-log-entry-and-remember
  (let [le (f/log-entry (+ A 6000) :le-testeur {:status :blocked :prompt (dissoc QB :since)})]
    (is (= {:at (+ A 6000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"} le))
    (is (true? (cl/valid-entry? le))))
  (is (nil? (f/log-entry (+ A 6000) :le-testeur {:status :blocked :prompt nil})))
  (is (= {:le-testeur {:id ID3 :since A}} (f/remember {} :le-testeur QB)))
  (is (= {:le-testeur {:id ID3 :since A}} (f/remember nil :le-testeur QB)))
  (is (= {:le-testeur {:id ID3 :since A} :un-architecte {:id ID2 :since 2}}
         (f/remember {:le-testeur {:id ID1 :since 1} :un-architecte {:id ID2 :since 2}} :le-testeur QB))))

(deftest t44-answered-once-not-twice-same-command-later-answered-again
  (is (= [{:member :le-testeur :prompt QB}] (f/due CTX)))
  (let [live {:status :blocked :prompt (dissoc QB :since)}
        tried1 (f/remember {} :le-testeur QB)
        log1 (cl/append [] (f/log-entry (+ A 5000) :le-testeur live))]
    (is (= [{:at (+ A 5000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"}] log1))
    (is (= :answered (v (assoc CTX :now (+ A 7000) :tried tried1 :log log1))))
    (is (= [] (f/due (assoc CTX :now (+ A 7000) :tried tried1 :log log1))))
    (is (= :answered (v (assoc CTX :now (+ A 7000) :tried {} :log log1))))
    (let [CTX3 (assoc CTX :crew (crew-at (+ A 22000) {:le-testeur {:status :blocked :prompt (assoc QB :since (+ A 20000))}})
                      :tried tried1 :log log1 :now (+ A 25000))]
      (is (= :answer (v CTX3)))
      (is (= :too-soon (v (assoc CTX3 :now (+ A 24999))))))
    (is (true? (cl/valid-entry? (first log1))))
    (is (= :twin (:by (first (cl/parse (pr-str log1))))))))
