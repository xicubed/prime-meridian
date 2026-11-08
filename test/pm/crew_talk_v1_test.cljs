(ns pm.crew-talk-v1-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs]
            [pm.prompt :as pr] [pm.bridge :as b] [pm.crew :as c] [pm.crew-log :as cl] [pm.talk :as tk]
            [pm.jumeaux :as j]))

(defn fx [k] (.readFileSync fs (str ".crew/quests/crew-talk-v1/screens/un-automate-monde-" k ".txt") "utf8"))
(def F1 (fx "190664ad70")) (def F2 (fx "2929ebc6bf")) (def F3 (fx "49184610d7"))
(def F4 (fx "62aacd3ea6")) (def F5 (fx "b123e48d5a")) (def F6 (fx "bdf0107df6"))
(def R "/repo")
(def A 1794013200000)       ; 5:00 PM at offset -480
(def OFF -480)
(defn lines [& ls] (str/join "\n" ls))
(def IDF1 "8d319583b6740bf75c9a1f4ba183adb09767f717") ; bash\ngrep -n "File:" /tmp/full-compile.txt
(def IDF2 "c8b2eed4239dd484ce2a1e1984eb2dbf4e96f396") ; bash\ngrep -n "Use of undeclared Var" /tmp/full-compile.txt
(def IDF3 "06efeed48734418f09576328d1ea39249fa7811e") ; bash\n… | tail -30 && node out/scratch.js
(def IDF6 "1f6cf8d70a06918075a5d8c8a2358dcfef0da1c1") ; bash\n… | tail -20 && node out/scratch.js
(def ID1  "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6") ; bash\nnode -e 'console.log(6*7)'
(def ID2  "06b65e49c507048bccab6121f64aaad2518f3b83") ; bash\ncurl --version
(def ID3  "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787") ; bash\nls
(def IDE  "6a049a2c7e97372a1caad99f23478183fe038e13") ; unknown\nEdit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?
(def IDFE "34808102fd1d3c0c9fd527daed30c62cda81d974") ; unknown\nFetch · https://example.com/notes · Do you want to allow Claude to fetch this content?
(def IDQ  "5fed68ac039b68605d8c85529e8ed8fb59312ca7") ; unknown\nDo you want to proceed?
(def T1X "grep -n \"File:\" /tmp/full-compile.txt")
(def T2X "grep -n \"Use of undeclared Var\" /tmp/full-compile.txt")
(def T3X "npx shadow-cljs compile scratch 2>&1 | tail -30 && node out/scratch.js")
(def T6X "npx shadow-cljs compile scratch 2>&1 | tail -20 && node out/scratch.js")
(def EDIT (lines "⏺ Update(src/pm/core.cljs)" "" " Edit file" " src/pm/core.cljs"
                 " Do you want to make this edit to core.cljs?" " ❯ 1. Yes"
                 "   2. Yes, allow all edits during this session (shift+tab)" "   3. No" ""
                 " Esc to cancel · Tab to amend"))
(def FETCH (lines "⏺ Fetch(https://example.com/notes)" "" " Fetch" " https://example.com/notes"
                  " Do you want to allow Claude to fetch this content?" " ❯ 1. Yes"
                  "   2. Yes, and don't ask again for example.com"
                  "   3. No, and tell Claude what to do differently (esc)"))
(def TH1 (lines "" "⏺ Reading the v0 parser and the six fixture screens." ""
                "· Cogitating… (5m 16s · ↓ 12.4k tokens · esc to interrupt)" ""
                "─────────────────────────────────────────────" "❯ "
                "─────────────────────────────────────────────"
                "  ⏵⏵ accept edits on (shift+tab to cycle)"))
(def TH2 "· Cogitating… (5m 16s …)")
(def TH3 (str F1 "\n\n⏺ Bash(grep -n \"File:\" /tmp/full-compile.txt)\n  ⎿  12: File: src/pm/x.cljs\n\n✻ Cogitating… (3s)"))
(def TH4 (lines "⏺ The spec is ready." "" " Do you want to proceed?" " Say yes and I'll start." "" "> "))
(def TH5 (lines " Bash command" " ╌╌╌╌" " ls" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes"))
(def TH6 (lines " Bash command" " ╌╌╌╌" " ls" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   3. No"))
(def QB {:id ID3 :kind :bash :text "ls" :risky false :since A})
(def QR {:id ID2 :kind :bash :text "curl --version" :risky true :since A})
(def QU {:id IDE :kind :unknown :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true :since A})
(def QN {:id ID1 :kind :bash :text "node -e 'console.log(6*7)'" :risky false})   ; no :since
(def POL {:hold? false :surface-after-s 60})
(def HOLD {:hold? true :surface-after-s 60})
(def TI {:un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur" :un-architecte "Un Architecte"})
(defn crew [at agents] {:as-of at :agents agents :titles TI})
(def T0 tk/empty-talk)
(defn e [t n] (tk/entry t n))
(def L1 {:at (+ A 5000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"})
(def L2 {:at (+ A 7000) :member :un-automate-monde :action :declined :by :twin :prompt-id ID2 :text "curl --version"})
(def L3 {:at (+ A 9000) :member :un-automate-monde :action :allowed :by :player :prompt-id ID1 :text "node -e 'console.log(6*7)'"})
(def TOK (apply str (repeat 16 "0a1b")))
(def CTX {:token TOK :members #{:le-testeur :un-automate-monde}})
(def OK-ORIGIN "http://localhost:8080")
(defn hq [body] {:method "POST" :uri "/hold" :origin OK-ORIGIN :body body})
(def H {"token" TOK "action" "hold" "on" true})
(def JH (j/names {:jumeaux {:player {:name "Michael" :sex :m} :twin {:name "Hal"}}}))

;; pm.prompt: every fixture screen (command text, kind, risky)

(deftest t01-f1-header-after-bullet-reads-private-tmp
  (is (true? (pr/prompt-screen? F1)))
  (is (= {:id IDF1 :kind :bash :text T1X :risky false} (pr/read-prompt F1 R))))

(deftest t02-f2-command-wrapped-over-two-lines
  (is (= {:id IDF2 :kind :bash :text T2X :risky false} (pr/read-prompt F2 R)))
  (is (= ["grep -n \"Use of undeclared Var\"" "/tmp/full-compile.txt"] (:lines (pr/parse-screen F2)))))

(deftest t03-f3-requires-approval-wrapped-tail
  (is (= {:id IDF3 :kind :bash :text T3X :risky false} (pr/read-prompt F3 R))))

(deftest t04-f4-same-command-bullet-header-ids-match
  (is (= {:id IDF3 :kind :bash :text T3X :risky false} (pr/read-prompt F4 R)))
  (is (= (:id (pr/read-prompt F3 R)) (:id (pr/read-prompt F4 R)) IDF3)))

(deftest t05-f5-diff-lines-above-different-description
  (is (= {:id IDF3 :kind :bash :text T3X :risky false} (pr/read-prompt F5 R))))

(deftest t06-f6-tail-20
  (is (= {:id IDF6 :kind :bash :text T6X :risky false} (pr/read-prompt F6 R))))

(deftest t07-prompt-of-agrees-with-read-prompt-on-all-six
  (doseq [Fi [F1 F2 F3 F4 F5 F6]]
    (is (= (pr/prompt-of Fi R) (pr/read-prompt Fi R)) Fi))
  (is (= {:id IDF1 :kind :bash :text T1X :risky false}
         (pr/read-prompt F1 "/Users/x/src/prime-meridian"))))

(deftest t08-staged-risky-from-a-real-screen
  (is (= {:id ID2 :kind :bash :text "curl --version" :risky true}
         (pr/read-prompt (str/replace F1 T1X "curl --version") R)))
  (let [p (pr/read-prompt (str/replace F6 "node out/scratch.js" "rm -rf out") R)]
    (is (= :bash (:kind p)))
    (is (true? (:risky p)))
    (is (= "npx shadow-cljs compile scratch 2>&1 | tail -20 && rm -rf out" (:text p)))))

;; pm.prompt: the gate (thinking / phantom screens rejected)

(deftest t09-thinking-screens-rejected
  (doseq [s [TH1 TH2 TH3 TH4 TH5 TH6]]
    (is (false? (pr/prompt-screen? s)) s)
    (is (nil? (pr/read-prompt s R)) s))
  (is (= :unknown (:kind (pr/prompt-of TH1 R)))))

(deftest t10-non-strings
  (is (false? (pr/prompt-screen? nil)))
  (is (false? (pr/prompt-screen? 42)))
  (is (false? (pr/prompt-screen? "")))
  (is (nil? (pr/read-prompt nil R)))
  (is (nil? (pr/read-prompt "" R))))

(deftest t11-accepted-variants
  (is (= {:id IDF3 :kind :bash :text T3X :risky false}
         (pr/read-prompt (-> F3 (str/replace "❯ 1. Yes" "  1. Yes") (str/replace "   4. No" " ❯ 4. No")) R)))
  (is (= {:id IDF1 :kind :bash :text T1X :risky false}
         (pr/read-prompt (str/replace F1 " Esc to cancel · Tab to amend" "") R)))
  (is (= {:id IDF2 :kind :bash :text T2X :risky false}
         (pr/read-prompt (str F2 "\n\n\n") R)))
  (is (true? (pr/prompt-screen? (lines "╭────╮" "│ Bash command │" "│ ╌╌╌╌ │" "│ ls │" "│ ╌╌╌╌ │"
                                        "│ Do you want to proceed? │" "│ ❯ 1. Yes │" "│   2. No │" "╰────╯"))))
  (is (= {:id ID3 :kind :bash :text "ls" :risky false}
         (pr/read-prompt (lines "╭────╮" "│ Bash command │" "│ ╌╌╌╌ │" "│ ls │" "│ ╌╌╌╌ │"
                                 "│ Do you want to proceed? │" "│ ❯ 1. Yes │" "│   2. No │" "╰────╯") R))))

(deftest t12-readable-unknowns
  (is (= {:id IDE :kind :unknown
          :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true}
         (pr/read-prompt EDIT R)))
  (is (= {:id IDFE :kind :unknown
          :text "Fetch · https://example.com/notes · Do you want to allow Claude to fetch this content?" :risky true}
         (pr/read-prompt FETCH R)))
  (is (= {:id IDQ :kind :unknown :text "Do you want to proceed?" :risky true}
         (pr/read-prompt (lines " Bash command" " grep -n x" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No") R)))
  (is (= "b · c · d · Do you want to go?"
         (:text (pr/read-prompt (lines "" " a" " b" " c" " d" " Do you want to go?" " ❯ 1. Yes" "   2. No") R)))))

(deftest t13-confirm-ms-constant
  (is (= 2000 pr/confirm-ms)))

;; pm.prompt: the two-poll rule (observe-prompts)

(def AG {:le-testeur {:status :blocked} :un-automate-monde {:status :blocked} :le-critique {:status :idle}})
(def SC {:le-testeur F1 :un-automate-monde F3})

(deftest t14-first-sighting
  (is (= {:agents {:le-testeur {:status :working} :un-automate-monde {:status :working} :le-critique {:status :idle}}
          :seen {:le-testeur {:id IDF1 :at A} :un-automate-monde {:id IDF3 :at A}}}
         (pr/observe-prompts AG SC R nil A))))

(def S1 {:le-testeur {:id IDF1 :at A} :un-automate-monde {:id IDF3 :at A}})
(def PUB-AGENTS {:le-testeur {:status :blocked :prompt {:id IDF1 :kind :bash :text T1X :risky false :since A}}
                  :un-automate-monde {:status :blocked :prompt {:id IDF3 :kind :bash :text T3X :risky false :since A}}
                  :le-critique {:status :idle}})

(deftest t15-second-poll-2000ms-later-published-with-since
  (is (= {:agents PUB-AGENTS :seen S1} (pr/observe-prompts AG SC R S1 (+ A 2000))))
  (is (= PUB-AGENTS (:agents (pr/observe-prompts AG SC R S1 (+ A 4000))))))

(deftest t16-too-soon
  (is (= {:agents {:le-testeur {:status :working} :un-automate-monde {:status :working} :le-critique {:status :idle}}
          :seen S1}
         (pr/observe-prompts AG SC R S1 (+ A 1999)))))

(deftest t17-different-prompt-resets
  (let [r (pr/observe-prompts AG {:le-testeur F1 :un-automate-monde F6} R S1 (+ A 2000))]
    (is (= :blocked (get-in r [:agents :le-testeur :status])))
    (is (= {:id IDF1 :kind :bash :text T1X :risky false :since A} (get-in r [:agents :le-testeur :prompt])))
    (is (= {:status :working} (get-in r [:agents :un-automate-monde])))
    (is (= {:le-testeur {:id IDF1 :at A} :un-automate-monde {:id IDF6 :at (+ A 2000)}} (:seen r)))))

(deftest t18-phantom-is-working-and-breaks-chain
  (is (= {:agents {:le-testeur {:status :working} :un-automate-monde {:status :working} :le-critique {:status :idle}}
          :seen {}}
         (pr/observe-prompts AG {:le-testeur TH1 :un-automate-monde TH2} R nil A)))
  (let [r1 (pr/observe-prompts {:le-testeur {:status :blocked}} {:le-testeur F1} R nil A)
        r2 (pr/observe-prompts {:le-testeur {:status :blocked}} {:le-testeur TH1} R (:seen r1) (+ A 2000))
        r3 (pr/observe-prompts {:le-testeur {:status :blocked}} {:le-testeur F1} R (:seen r2) (+ A 4000))
        r4 (pr/observe-prompts {:le-testeur {:status :blocked}} {:le-testeur F1} R (:seen r3) (+ A 6000))]
    (is (= :working (get-in r1 [:agents :le-testeur :status])))
    (is (= :working (get-in r2 [:agents :le-testeur :status])))
    (is (= :working (get-in r3 [:agents :le-testeur :status])))
    (is (= :blocked (get-in r4 [:agents :le-testeur :status])))
    (is (= (+ A 4000) (get-in r4 [:agents :le-testeur :prompt :since])))))

(deftest t19-unread-not-blocked
  (is (= {:agents {:le-testeur {:status :blocked} :hal {:status :blocked} :un-automate-monde {:status :working}}
          :seen {}}
         (pr/observe-prompts {:le-testeur {:status :blocked} :hal {:status :blocked} :un-automate-monde {:status :working}}
                              {:le-testeur nil} R {:un-automate-monde {:id IDF3 :at A}} (+ A 2000)))))

;; pm.crew-log

(deftest t20-valid-entry
  (doseq [v [L1 L2 L3 (assoc L1 :text (apply str (repeat 400 "x")))]]
    (is (true? (cl/valid-entry? v)) v))
  (doseq [v [(dissoc L1 :prompt-id) (assoc L1 :action :allow) (assoc L1 :by :le-testeur)
             (assoc L1 :at -1) (assoc L1 :at 1.5) (assoc L1 :text "") (assoc L1 :text "  ")
             (assoc L1 :text (apply str (repeat 401 "x"))) (assoc L1 :member "le-testeur")
             (assoc L1 :member :Bad) (assoc L1 :prompt-id "ABC") nil []]]
    (is (false? (cl/valid-entry? v)) v)))

(deftest t21-parse
  (is (= [L1 L2] (cl/parse (pr-str [L1 {:bad 1} L2]))))
  (is (= [L1] (cl/parse (pr-str [(assoc L1 :note "x")]))))
  (is (nil? (cl/parse "{:a 1}")))
  (is (nil? (cl/parse "")))
  (is (nil? (cl/parse "[1 2")))
  (is (nil? (cl/parse nil)))
  (is (= [] (cl/parse "[]")))
  (let [entries (mapv #(assoc L1 :at (+ A %)) (range 205))
        parsed (cl/parse (pr-str entries))]
    (is (= 200 (count parsed)))
    (is (= (+ A 5) (:at (first parsed))))))

(deftest t22-entry-and-append
  (is (= L1 (cl/entry (+ A 5000) :le-testeur :allowed :twin {:id ID3 :kind :bash :text "ls" :risky false})))
  (is (= [L1 L2] (cl/append [L1] L2)))
  (is (= [L1] (cl/append nil L1)))
  (is (= [L1 (assoc L2 :at (+ A 5001))] (cl/append [L1] (assoc L2 :at (+ A 5000)))))
  (is (= [L1 (assoc L2 :at (+ A 5001))] (cl/append [L1] (assoc L2 :at (+ A 1000)))))
  (is (= [L1] (cl/append [L1] (assoc L2 :by :x))))
  (is (= [L1 L2] (cl/append [{:bad 1} L1] L2)))
  (let [r (cl/append (mapv #(assoc L1 :at (+ A %)) (range 200)) (assoc L2 :at (+ A 999)))]
    (is (= 200 (count r)))
    (is (= (+ A 1) (:at (first r))))
    (is (= (assoc L2 :at (+ A 999)) (last r))))
  (is (= 200 cl/max-entries)))

(deftest t23-for-member
  (is (= [L2 L3] (cl/for-member [L1 L2 L3] :un-automate-monde 3)))
  (is (= [L3] (cl/for-member [L1 L2 L3] :un-automate-monde 1)))
  (is (= [L1] (cl/for-member [L1 L2 L3] :le-testeur 3)))
  (is (= [] (cl/for-member [L1 L2 L3] :le-coureur 3)))
  (is (= [] (cl/for-member [L1 L2 L3] :un-automate-monde 0)))
  (is (= [] (cl/for-member nil :un-automate-monde 3))))

(deftest t24-hold-file
  (is (= {:on true :at 5} (cl/hold-edn true 5)))
  (is (= {:on false :at 5} (cl/hold-edn nil 5)))
  (is (= {:on true :at 5} (cl/parse-hold "{:on true :at 5}")))
  (is (= {:on false :at 5} (cl/parse-hold "{:on false :at 5 :x 1}")))
  (doseq [s ["{:on \"yes\" :at 5}" "{:on true}" "{:on true :at -1}" "[true]" "{:on" nil]]
    (is (nil? (cl/parse-hold s)) s))
  (is (false? (cl/hold-on? nil)))
  (is (false? (cl/hold-on? {:on false :at 5})))
  (is (true? (cl/hold-on? {:on true :at 5}))))

;; pm.bridge: the hold action

(deftest t25-valid-hold
  (is (= {:ok true :action :hold :on true} (b/validate (hq H) CTX)))
  (is (= {:ok true :action :hold :on false} (b/validate (hq (assoc H "on" false)) CTX)))
  (is (= {:ok true :action :hold :on true} (b/validate (hq H) {:token TOK :members #{}})))
  (is (= "/hold" b/hold-uri)))

(deftest t26-refusals-in-order
  (is (= {:ok false :status 400 :reason "bad on"} (b/validate (hq (assoc H "on" "true")) CTX)))
  (is (= {:ok false :status 400 :reason "bad on"} (b/validate (hq (dissoc H "on")) CTX)))
  (is (= {:ok false :status 400 :reason "bad action"} (b/validate (hq (assoc H "action" "allow")) CTX)))
  (is (= {:ok false :status 401 :reason "bad token"} (b/validate (hq (assoc H "token" (apply str (repeat 64 "f")))) CTX)))
  (is (= {:ok false :status 403 :reason "bad origin"} (b/validate (assoc (hq H) :origin "http://evil.example") CTX)))
  (is (= {:ok false :status 403 :reason "bad origin"} (b/validate (assoc (hq H) :origin nil) CTX)))
  (is (= {:ok false :status 405 :reason "method not allowed"} (b/validate (assoc (hq H) :method "GET") CTX)))
  (is (= {:ok false :status 400 :reason "bad json"} (b/validate (assoc (hq H) :body :bad-json) CTX)))
  (is (= {:ok false :status 404 :reason "not found"} (b/validate (assoc (hq H) :uri "/hold/x") CTX)))
  (is (= {:ok false :status 404 :reason "not found"} (b/validate (assoc (hq H) :uri "/HOLD") CTX)))
  ;; precedence: bad token + bad on -> 401; bad origin + bad action -> 403
  (is (= 401 (:status (b/validate (hq (-> H (assoc "token" (apply str (repeat 64 "f"))) (assoc "on" "bad"))) CTX))))
  (is (= 403 (:status (b/validate (assoc (hq (assoc H "action" "allow")) :origin "http://evil.example") CTX))))
  (is (= {:ok false :status 400 :reason "bad action"}
         (b/validate {:method "POST" :uri "/crew/le-testeur" :origin OK-ORIGIN :body {"token" TOK "action" "hold" "on" true}} CTX)))
  (is (= #{"allow" "deny" "say"} b/actions)))

(deftest t27-log-line
  (is (= "2026-11-08T17:00:00Z - hold 200 on" (b/log-line "2026-11-08T17:00:00Z" nil :hold 200 "on")))
  (is (= "2026-11-08T17:00:00Z - hold 200 on" (b/log-line "2026-11-08T17:00:00Z" nil "hold" 200 "on")))
  (is (= "t le-testeur allow 200 allowed" (b/log-line "t" :le-testeur :allow 200 "allowed"))))

;; pm.crew

(deftest t28-since
  (is (true? (c/valid-prompt? QB)))
  (is (true? (c/valid-prompt? (dissoc QB :since))))
  (is (false? (c/valid-prompt? (assoc QB :since -1))))
  (is (false? (c/valid-prompt? (assoc QB :since "x"))))
  (is (false? (c/valid-prompt? (assoc QB :since 1.5))))
  (is (= {:as-of 5 :agents {:a {:status :blocked :prompt (assoc QB :since 3)}}}
         (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc QB :since 3)}}}))))
  (is (= {:as-of 5 :agents {:a {:status :blocked}}}
         (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc QB :since -1)}}})))))

(def HB {:port 8091 :token TOK :as-of 1})

(deftest t29-hold-request
  (is (= "http://127.0.0.1:8091/hold" (c/hold-url HB)))
  (is (= {"token" TOK "action" "hold" "on" true} (c/hold-body HB true)))
  (is (= {"token" TOK "action" "hold" "on" false} (c/hold-body HB nil))))

;; pm.talk: policy and :surface-after-s

(deftest t30-policy
  (is (= {:hold? true :surface-after-s 30} (tk/policy {:surface-after-s 30} {:on true :at 5})))
  (is (= {:hold? false :surface-after-s 60} (tk/policy nil nil)))
  (doseq [bad [{:surface-after-s -1} {:surface-after-s 3601} {:surface-after-s "x"}]]
    (is (= 60 (:surface-after-s (tk/policy bad {:on false :at 5}))) bad))
  (is (= 3600 (:surface-after-s (tk/policy {:surface-after-s 3600} {:on false :at 5}))))
  (is (= 0 (:surface-after-s (tk/policy {:surface-after-s 0} {:on false :at 5}))))
  (is (= 60 tk/default-surface-after-s)))

(deftest t31-surfaced?
  (is (false? (tk/surfaced? QB (+ A 59999) POL)))
  (is (true? (tk/surfaced? QB (+ A 60000) POL)))
  (is (true? (tk/surfaced? QB A HOLD)))
  (is (true? (tk/surfaced? QR A POL)))
  (is (true? (tk/surfaced? QU A POL)))
  (is (true? (tk/surfaced? QN A POL)))
  (is (true? (tk/surfaced? QB A {:hold? false :surface-after-s 0})))
  (is (false? (tk/surfaced? QB A nil)))
  (is (true? (tk/surfaced? QB (+ A 60000) nil)))
  (is (true? (tk/surfaced? QB (+ A 30000) {:hold? false :surface-after-s 30}))))

;; pm.talk: quiet vs surfaced lines (observe-crew 4-arity)

(def AGENTS32 {:le-testeur {:status :blocked :prompt QB} :un-automate-monde {:status :blocked :prompt QR}})
(def O1 (tk/observe-crew T0 (crew A AGENTS32) A POL))
(def O3 (tk/observe-crew O1 (crew (+ A 60000) AGENTS32) (+ A 60000) POL))

(deftest t32-risky-surfaces-benign-stays-quiet
  (is (= [{:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
           :member :un-automate-monde :prompt QR :state :pending}]
         (:entries O1)))
  (is (= {:le-testeur {:status :blocked :prompt-id nil} :un-automate-monde {:status :blocked :prompt-id ID2}}
         (:crew-last O1)))
  (is (true? (tk/prompt-waiting? O1)))
  (is (= 1 (tk/unread O1))))

(deftest t33-still-quiet-at-30s-identical
  (is (identical? O1 (tk/observe-crew O1 (crew (+ A 30000) AGENTS32) (+ A 30000) POL))))

(deftest t34-overdue-at-60s
  (is (= {:n 2 :who "Le Testeur" :text "wants to run ls" :at (+ A 60000) :kind :crew-prompt
          :member :le-testeur :prompt QB :state :pending}
         (e O3 2)))
  (is (= {:status :blocked :prompt-id ID3} (get-in O3 [:crew-last :le-testeur])))
  (is (identical? O3 (tk/observe-crew O3 (crew (+ A 62000) AGENTS32) (+ A 62000) POL))))

(deftest t35-unknown-surfaces-at-once
  (let [r (tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt QU}}) A POL)]
    (is (= "is waiting at a prompt: Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?"
           (:text (e r 1))))
    (is (= :pending (:state (e r 1))))
    (is (false? (tk/allowable? (e r 1))))))

(deftest t36-hold-surfaces-everything-3-arity-is-hold
  (let [r (tk/observe-crew O1 (crew (+ A 2000) AGENTS32) (+ A 2000) HOLD)]
    (is (= (+ A 2000) (:at (e r 2))))
    (is (= :le-testeur (:member (e r 2)))))
  (let [C32 (crew A {:le-testeur {:status :blocked :prompt QB}})]
    (is (= (tk/observe-crew T0 C32 A) (tk/observe-crew T0 C32 A HOLD)))))

(deftest t37-sticky-after-hold-goes-off-no-duplicate-when-back
  (let [agents1 {:le-testeur {:status :blocked :prompt QB}}
        S1t (tk/observe-crew T0 (crew A agents1) A HOLD)
        S2t (tk/observe-crew S1t (crew (+ A 2000) agents1) (+ A 2000) POL)
        S3t (tk/observe-crew S2t (crew (+ A 4000) agents1) (+ A 4000) HOLD)]
    (is (= 1 (count (:entries S1t))))
    (is (identical? S1t S2t))
    (is (identical? S2t S3t))))

(deftest t38-hal-answered-a-quiet-one
  (let [r (tk/observe-crew O1 (crew (+ A 2000) {:le-testeur {:status :working}
                                                 :un-automate-monde {:status :blocked :prompt QR}})
                            (+ A 2000) POL)]
    (is (= 1 (count (:entries r))))
    (is (= {:status :working :prompt-id nil} (get-in r [:crew-last :le-testeur])))))

;; pm.talk: the log (observe-log) and "already answered by ..."

(def C (crew A {:le-testeur {:status :idle} :un-automate-monde {:status :blocked}}))
(def G1 (tk/observe-log T0 [L1] C (+ A 6000) JH))
(def G2 (tk/observe-log G1 [L1 L2 L3] C (+ A 10000) JH))
(def M (tk/observe-log O1 [L2] C (+ A 8000) JH))

(deftest t39-a-quiet-line-no-badge
  (is (= [{:n 1 :who "Hal" :text "allowed `ls` for Le Testeur" :at (+ A 5000) :kind :crew-log
           :member :le-testeur :log L1}]
         (:entries G1)))
  (is (= (+ A 5000) (:log-at G1)))
  (is (= 0 (tk/unread G1)))
  (is (= "" (tk/badge-text G1)))
  (is (false? (tk/prompt-waiting? G1)))
  (is (= "Hal allowed `ls` for Le Testeur · 5:00 PM" (tk/crew-label (e G1 1) OFF JH)))
  (is (nil? (tk/crew-controls (e G1 1) true))))

(deftest t40-idempotent-ordered-michaels-own-entries-no-line
  (is (identical? G1 (tk/observe-log G1 [L1] C (+ A 8000))))
  (is (= 2 (count (:entries G2))))
  (is (= {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
          :kind :crew-log :member :un-automate-monde :log L2}
         (e G2 2)))
  (is (= (+ A 9000) (:log-at G2)))
  (is (= "Hal declined `curl --version` for Un Automate (monde) · 5:00 PM" (tk/crew-label (e G2 2) OFF JH)))
  (is (identical? T0 (tk/observe-log T0 nil C A)))
  (is (identical? T0 (tk/observe-log T0 [] C A)))
  (is (= "allowed `ls` for le-testeur" (:text (e (tk/observe-log T0 [L1] nil (+ A 6000)) 1)))))

(deftest t41-old-entries-advance-log-at-but-add-no-line
  (is (= [] (:entries (tk/observe-log T0 [L1] C (+ A 5000 1800000)))))
  (is (= (+ A 5000) (:log-at (tk/observe-log T0 [L1] C (+ A 5000 1800000)))))
  (is (= 1 (count (:entries (tk/observe-log T0 [L1] C (+ A 5000 1799999)))))))

(deftest t42-hal-first-already-answered-by-hal
  (is (= :stale (:state (e M 1))))
  (is (= :twin (:by (e M 1))))
  (is (= :crew-log (:kind (e M 2))))
  (is (false? (tk/prompt-waiting? M)))
  (is (= 1 (tk/unread M)))
  (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by Hal" (tk/crew-label (e M 1) OFF JH)))
  (is (nil? (tk/crew-controls (e M 1) true))))

(deftest t43-michael-clicked-second-409-first-log-after
  (let [X (-> O1 (tk/crew-send 1 :deny nil) (tk/crew-result 1 {:result :stale :reason "prompt changed"} (+ A 7500)))]
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered" (tk/crew-label (e X 1) OFF)))
    (is (= "⚠ Un Automate (monde) · wants to run curl --version · already answered by Hal"
           (tk/crew-label (e (tk/observe-log X [L2] C (+ A 8000)) 1) OFF JH))))
  (let [sending (tk/crew-send O1 1 :deny nil)
        r (tk/observe-log sending [L2] C (+ A 8000))]
    (is (= :sending (:state (e r 1))))
    (is (= :twin (:by (e r 1))))))

(deftest t44-already-answered-by-you
  (let [Y (tk/observe-crew T0 (crew A {:un-automate-monde {:status :blocked :prompt QN}}) A HOLD)
        r (tk/observe-log Y [L3] C (+ A 10000))]
    (is (= :stale (:state (e r 1))))
    (is (= :player (:by (e r 1))))
    (is (= 1 (count (:entries r))))
    (is (= (+ A 9000) (:log-at r)))
    (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · already answered by you"
           (tk/crew-label (e r 1) OFF)))))

(deftest t45-the-time-guard-and-the-member-guard
  (let [Z (tk/observe-crew T0 (crew (+ A 20000) {:le-testeur {:status :blocked :prompt (assoc QB :since (+ A 20000))}})
                            (+ A 20000) HOLD)]
    (let [r1 (tk/observe-log Z [(assoc L1 :at (+ A 10000))] C (+ A 21000))]
      (is (= :pending (:state (e r1 1))))
      (is (nil? (:by (e r1 1))))
      (is (= :crew-log (:kind (e r1 2)))))
    (let [r2 (tk/observe-log Z [(assoc L1 :at (+ A 15000))] C (+ A 21000))]
      (is (= :stale (:state (e r2 1))))
      (is (= :twin (:by (e r2 1)))))
    (let [r3 (tk/observe-log Z [(assoc L1 :at (+ A 15000) :member :un-automate-monde)] C (+ A 21000))]
      (is (= :pending (:state (e r3 1)))))
    (let [r4 (tk/observe-log Z [(assoc L1 :at (+ A 15000) :prompt-id ID1)] C (+ A 21000))]
      (is (= :pending (:state (e r4 1)))))))

(deftest t46-save-restore
  (let [r2 (tk/restore (tk/save-text G2))]
    (is (= (:entries G2) (:entries r2)))
    (is (= (+ A 9000) (:log-at r2)))
    (is (= 2 (:n r2))))
  (let [rm (tk/restore (tk/save-text M))]
    (is (= :twin (:by (e rm 1))))
    (is (= :stale (:state (e rm 1)))))
  (is (= T0 (tk/restore (tk/save-text T0))))
  (is (= 0 (:log-at T0)))
  (let [text (pr-str {:v 1 :n 1 :seen 1 :log-at 5
                       :entries [{:n 1 :who "Hal" :text "x" :at A :kind :crew-log :member :le-testeur
                                  :log (assoc L1 :by :x)}]})]
    (is (= [] (:entries (tk/restore text)))))
  (is (= 0 (:log-at (tk/restore (pr-str {:v 1 :n 0 :seen 0 :entries [] :log-at "x"})))))
  (is (= 0 (:log-at (tk/restore (pr-str {:v 1 :n 0 :seen 0 :entries [] :log-at -3}))))))

;; pm.talk: members who left, roster log, the switch

(deftest t47-gone?
  (let [Cg (crew A {:le-testeur {:status :idle}})]
    (is (true? (tk/gone? {:kind :crew-log :member :le-coureur} Cg A)))
    (is (true? (tk/gone? {:kind :crew-prompt :member :le-coureur} Cg A)))
    (is (false? (tk/gone? {:kind :crew-log :member :le-testeur} Cg A)))
    (is (false? (tk/gone? {:kind :crew-say :member :un-architecte} Cg A)))
    (is (false? (tk/gone? {:kind :crew-log :member :le-coureur} Cg (+ A 15001))))
    (is (false? (tk/gone? {:kind :crew-log :member :le-coureur} nil A)))
    (is (false? (tk/gone? {:kind :hal :who "Hal"} Cg A))))
  (let [no-titles {:as-of A :agents {:le-coureur {:status :idle}}}]
    (is (false? (tk/gone? {:kind :crew-log :member :le-coureur} no-titles A)))
    (is (true? (tk/gone? {:kind :crew-log :member :le-testeur} no-titles A)))))

(deftest t48-roster-with-the-log
  (is (= [{:member :le-testeur :who "Le Testeur" :status :idle :say? true :expanded? false :log []}
          {:member :un-architecte :who "Un Architecte" :status :unknown :say? false :expanded? false :log []}
          {:member :un-automate-monde :who "Un Automate (monde)" :status :blocked :say? false :expanded? true :log [L2 L3]}]
         (tk/crew-roster C A true [L1 L2 L3] #{:un-automate-monde})))
  (is (every? #(and (false? (:expanded? %)) (= [] (:log %))) (tk/crew-roster C A true [L1 L2 L3] nil)))
  (is (nil? (:log (first (tk/crew-roster C A true))))))

(deftest t49-log-label
  (is (= "Hal allowed `ls` · 5:00 PM" (tk/log-label L1 OFF JH)))
  (is (= "You allowed `node -e 'console.log(6*7)'` · 5:00 PM" (tk/log-label L3 OFF JH)))
  (is (= "Hal declined `curl --version` · 5:01 PM" (tk/log-label (assoc L2 :at (+ A 60000)) OFF JH))))

(deftest t50-the-ill-answer-switch-from-hold-edn
  (is (= {:checked? true :disabled? false :hint "you answer; Hal holds off"} (tk/hold-switch {:on true :at 5} true {} JH)))
  (is (= {:checked? false :disabled? false :hint "Hal answers the easy ones"} (tk/hold-switch nil true nil JH)))
  (is (= {:checked? true :disabled? true :hint "bridge off"} (tk/hold-switch {:on true :at 5} false {})))
  (is (= {:checked? false :disabled? true :hint "sending…"} (tk/hold-switch nil true {:sending? true})))
  (is (= {:checked? false :disabled? false :hint "not sent: bad token"} (tk/hold-switch {:on false :at 5} true {:error "bad token"})))
  (is (true? (:checked? (tk/hold-switch (cl/parse-hold (pr-str (cl/hold-edn true A))) true {}))))
  (is (true? (:hold? (tk/policy nil (cl/parse-hold (pr-str (cl/hold-edn true A))))))))
