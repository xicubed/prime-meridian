(ns pm.crew-talk-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs]
            [pm.prompt :as pr] [pm.bridge :as b] [pm.crew :as c] [pm.talk :as tk]
            [pm.world :as w] [pm.fixtures :as fx] [pm.jumeaux :as j]))

(def FX (.readFileSync fs ".crew/quests/crew-talk-v0/claude-bash-prompt.txt" "utf8")) ; gate runs from the repo root
(def R "/repo")
(def A 1794013200000)                       ; Fri 2026-11-06 5:00 PM PST (offset -480)
(def OFF -480)
(def ID1 "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6")   ; bash\nnode -e 'console.log(6*7)'
(def ID2 "06b65e49c507048bccab6121f64aaad2518f3b83")   ; bash\ncurl --version
(def ID3 "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787")   ; bash\nls
(def IDU "6ab9d1b13187a245f840a03f3d012f58b728a7f4")   ; the EDIT screen below
(def P1 {:id ID1 :kind :bash :text "node -e 'console.log(6*7)'" :risky false})
(def P2 {:id ID2 :kind :bash :text "curl --version" :risky true})
(def P3 {:id ID3 :kind :bash :text "ls" :risky false})
(def PU {:id IDU :kind :unknown :text "Edit file\n3. No" :risky true})   ; game-side only
(def TOK (apply str (repeat 16 "0a1b")))   ; 64 hex
(def TI {:un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur" :un-architecte "Un Architecte"})
(defn crew [at agents] {:as-of at :agents agents :titles TI})
(def C1 (crew A {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :working}}))
(def T0 tk/empty-talk)
(def T1 (tk/observe-crew T0 C1 A))
(defn e [t n] (tk/entry t n))
(defn lines [& ls] (str/join "\n" ls))
(def BOX (lines "╭────────────────────────────╮" "│ Bash command               │" "│                            │"
                 "│ ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌ │" "│ node -e 'console.log(6*7)' │"
                 "│ ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌ │" "│ Do you want to proceed?    │"
                 "│ ❯ 1. Yes                   │" "│   2. No                    │" "╰────────────────────────────╯"))
(def WRAP (lines " Bash command" " ╌╌╌╌╌╌╌╌╌╌" " node -e 'const a = [1, 2, 3]; console.log(a.map(x"
                  " => x * 2).join(\" \"))'" " ╌╌╌╌╌╌╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No"))
(def WRAPBOX (lines "╭──────────╮" "│ Bash command │" "│ ╌╌╌╌╌╌╌╌╌╌ │" "│ node -e 'const a = [1, 2, 3]; console.log(a.map(x │"
                     "│ => x * 2).join(\" \"))' │" "│ ╌╌╌╌╌╌╌╌╌╌ │" "│ Do you want to proceed? │" "│ ❯ 1. Yes │" "│   2. No │" "╰──────────╯"))
(def WRAPPED "node -e 'const a = [1, 2, 3]; console.log(a.map(x => x * 2).join(\" \"))'")
(def EDIT (lines "⏺ Update(src/pm/core.cljs)" "" " Edit file" " src/pm/core.cljs" " Do you want to make this edit to core.cljs?"
                  " ❯ 1. Yes" "   2. Yes, allow all edits during this session (shift+tab)" "   3. No" "" " Esc to cancel · Tab to amend"))
(defn dialog [cmd-line] (lines " Bash command" " ╌╌╌╌" cmd-line " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes"))
(def MEMBERS #{:un-architecte :un-automate-monde :le-testeur :le-coureur :le-critique})
(def CTX {:token TOK :members MEMBERS})
(def OK-ORIGIN "http://localhost:8080")
(defn rq [member body] {:method "POST" :uri (str "/crew/" member) :origin OK-ORIGIN :body body})
(def ALLOW {"token" TOK "action" "allow" "prompt-id" ID1})
(def JH (j/names {:jumeaux {:player {:name "Michael" :sex :m} :twin {:name "Hal"}}}))

;; pm.prompt: parsing

(deftest t01-real-fixture
  (is (= {:kind :bash :lines ["node -e 'console.log(6*7)'"] :full "node -e 'console.log(6*7)'"}
         (pr/parse-screen FX)))
  (is (= P1 (pr/prompt-of FX R))))

(deftest t02-box-variant
  (is (= P1 (pr/prompt-of BOX R)))
  (is (= ["node -e 'console.log(6*7)'"] (:lines (pr/parse-screen BOX)))))

(deftest t03-wrapped-command
  (is (= {:id "6d92151b8d86fcbaa5daf8d4a7d4371f67275005" :kind :bash :text WRAPPED :risky false}
         (pr/prompt-of WRAP R)))
  (is (= {:id "6d92151b8d86fcbaa5daf8d4a7d4371f67275005" :kind :bash :text WRAPPED :risky false}
         (pr/prompt-of WRAPBOX R)))
  (is (= ["node -e 'const a = [1, 2, 3]; console.log(a.map(x" "=> x * 2).join(\" \"))'"]
         (:lines (pr/parse-screen WRAP)))))

(deftest t04-mid-word-wrap-caught
  (is (= {:id "8213920f9386e868c8b99d614356546e33901ad7" :kind :bash :text "cur l --version" :risky true}
         (pr/prompt-of (lines " Bash command" " ╌╌╌╌╌╌╌╌╌╌" " cur" " l --version" " ╌╌╌╌╌╌╌╌╌╌"
                               " Do you want to proceed?" " ❯ 1. Yes" "   2. No")
                        R))))

(deftest t05-id-stability
  (is (= P1 (pr/prompt-of (str "⏺ earlier\n  stuff\n" FX) R)))
  (is (= ID1 (:id (pr/prompt-of (-> FX
                                    (str/replace "❯ 1. Yes" "  1. Yes")
                                    (str/replace "   4. No" " ❯ 4. No"))
                                 R))))
  (is (= ID1 (pr/prompt-id :bash "node -e 'console.log(6*7)'")))
  (is (= ID2 (pr/prompt-id :bash "curl --version")))
  (is (= ID3 (pr/prompt-id :bash "ls")))
  (is (= "2d7bb5572221afa7d7fb30c8d19d3f693bfeee14" (pr/prompt-id :unknown "")))
  (is (not= ID1 (pr/prompt-id :unknown "node -e 'console.log(6*7)'"))))

(deftest t06-unknown-shape-edit-prompt
  (is (= {:id IDU :kind :unknown
          :text "src/pm/core.cljs\nDo you want to make this edit to core.cljs?\n❯ 1. Yes\n2. Yes, allow all edits during this session (shift+tab)\n3. No\nEsc to cancel · Tab to amend"
          :risky true}
         (pr/prompt-of EDIT R))))

(deftest t07-shapes-not-a-bash-prompt
  (is (= :unknown (:kind (pr/prompt-of (str/replace FX "Do you want to proceed?" "Proceed?") R))))
  (is (= :unknown (:kind (pr/prompt-of (str/replace FX "1. Yes" "1. Sure") R))))
  (is (= {:id "8d69882bd93f4c34fbc0ee49ae03b279014a582e" :kind :unknown
          :text "Bash command\n╌╌╌╌\nls\nDo you want to proceed?\n❯ 1. Yes" :risky true}
         (pr/prompt-of (lines " Bash command" " ╌╌╌╌" " ls" " Do you want to proceed?" " ❯ 1. Yes") R)))
  (is (true? (:risky (pr/prompt-of (str/replace FX "Do you want to proceed?" "Proceed?") R))))
  (is (true? (:risky (pr/prompt-of (str/replace FX "1. Yes" "1. Sure") R)))))

(deftest t08-empty-and-non-string-screens
  (is (= {:id "2d7bb5572221afa7d7fb30c8d19d3f693bfeee14" :kind :unknown :text "" :risky true}
         (pr/prompt-of "" R)))
  (is (= {:kind :unknown :full ""} (pr/parse-screen "")))
  (is (nil? (pr/prompt-of nil R)))
  (is (nil? (pr/prompt-of 42 R)))
  (is (nil? (pr/parse-screen nil))))

(deftest t09-400-char-cap
  (let [L (str "echo " (apply str (repeat 500 "a")))
        p (pr/prompt-of (dialog (str " " L)) R)]
    (is (= "f8381cf075bacab20b88ba54c086a57f22e1b6fd" (:id p)))
    (is (= 400 (count (:text p))))
    (is (= (str (subs L 0 399) "…") (:text p)))
    (is (true? (:risky p))))
  (let [L4 (str "echo " (apply str (repeat 395 "a")))]
    (is (= {:id "478cb20acb9ae9876a7c695af4a7b5e8eb014b86" :kind :bash :text L4 :risky false}
           (pr/prompt-of (dialog (str " " L4)) R))))
  (is (= "abc" (pr/cap-text "abc")))
  (is (= 400 (count (pr/cap-text (apply str (repeat 401 "x"))))))
  (is (= 400 pr/max-text))
  (is (= 6 pr/tail-lines)))

;; pm.prompt: risky

(deftest t10-risky-driver-patterns
  (doseq [s ["git add ." "git commit -m x" "git push" "git reset --hard" "git checkout main"
             "git stash" "git rm x" "git clean -fd" "git restore x" "git -C /repo push"
             "git status && git push"
             "rm -rf out" "curl --version" "CURL --version" "wget x" "ssh host" "scp a b:c"
             "ls; curl x"
             "npm i" "npm install three" "npm publish" "pip install x" "pip3 install x"
             "brew install x" "sudo ls" "launchctl list"]]
    (is (true? (pr/risky? s R)) s)))

(deftest t11-risky-benign
  (doseq [s ["node -e 'console.log(6*7)'" "bb test" "ls"
             "git status" "git diff" "git log --oneline"
             "npm init" "npm run x" "npx shadow-cljs compile app"
             ".crew/gate.sh" "rm out.txt" "touch notes.md"
             "digit add" "curling" "scurl" "sshd"]]
    (is (false? (pr/risky? s R)) s)))

(deftest t12-writes-outside-repo
  (doseq [s ["echo hi > /tmp/x" "echo a >/repo2/x" "cp a ~/b" "cp a ../b"
             "mkdir /repo/../x" "rm /etc/x" "sed -i s/a/b/ /etc/x" "cd /tmp && touch x"
             "tee -a /var/log/x"]]
    (is (true? (pr/risky? s R)) s))
  (doseq [s ["echo hi > out.txt" "ls > /dev/null" "node x.js 2>&1" "bash .crew/gate.sh > /repo/out.txt"
             "cp a /repo/b" "cat /etc/hosts" "cat ../README.md"]]
    (is (false? (pr/risky? s R)) s))
  (is (true? (pr/risky? "cp a /repo/b" nil))))

;; pm.prompt: watcher helpers

(deftest t13-screens-to-read
  (is (= [:le-critique :le-testeur]
         (pr/screens-to-read {:le-testeur {:status :blocked} :un-automate-monde {:status :working}
                               :other {:status :blocked} :le-critique {:status :blocked}}
                              #{:le-testeur :le-critique :un-automate-monde})))
  (is (= [] (pr/screens-to-read {} MEMBERS))))

(deftest t14-with-prompts
  (is (= {:le-testeur {:status :blocked :prompt P1} :un-automate-monde {:status :working}
          :le-critique {:status :blocked}}
         (pr/with-prompts {:le-testeur {:status :blocked} :un-automate-monde {:status :working}
                            :le-critique {:status :blocked}}
                           {:le-testeur FX :un-automate-monde FX :le-critique nil}
                           R))))

;; pm.bridge

(deftest t15-constants
  (is (= "127.0.0.1" b/host))
  (is (= 8091 b/port))
  (is (= "http://localhost:8080" b/allowed-origin))
  (is (= 1000 b/max-text))
  (is (= 4096 b/max-body))
  (is (= #{"allow" "deny" "say"} b/actions))
  (is (= 5000 b/wait-ms)))

(def CJ {:members [{:name "hal" :title "Hal" :external true :enabled true}
                    {:name "un-architecte" :title "Un Architecte" :enabled true}
                    {:name "un-automate-monde" :title "Un Automate (monde)" :enabled true}
                    {:name "le-testeur" :title "Le Testeur" :enabled true}
                    {:name "le-coureur" :title "Le Coureur" :enabled true}
                    {:name "le-coureur-omy" :title "Le Coureur (omarchy)" :enabled true :machine "omarchy"}
                    {:name "le-critique" :title "Le Critique" :enabled true :external false}
                    {:name "le-docteur" :title "Le Docteur" :enabled false}
                    {:name "Bad Name" :title "X" :enabled true}
                    {:name "sans-titre" :enabled true}
                    {:name "long" :title (apply str (repeat 65 "t")) :enabled true}]})

(deftest t16-roster
  (is (= {:un-architecte "Un Architecte" :un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur"
          :le-coureur "Le Coureur" :le-critique "Le Critique" :sans-titre "sans-titre" :long "long"}
         (b/roster CJ)))
  (is (= {} (b/roster nil)))
  (is (= {} (b/roster {:members "x"}))))

(deftest t17-agent-statuses
  (is (= {:le-testeur {:status :done} :un-automate-monde {:status :blocked} :x {:status :unknown}}
         (b/agent-statuses {:result {:agents [{:name "le-testeur" :agent_status "done"}
                                               {:name "un-automate-monde" :agent_status "blocked"}
                                               {:name "Bad Name" :agent_status "idle"}
                                               {:name "x" :agent_status nil}
                                               {:agent_status "idle"}]}})))
  (is (= {} (b/agent-statuses nil)))
  (is (= {} (b/agent-statuses {:result {}}))))

(deftest t18-token-helpers
  (is (= "0001ff10ff" (b/token-hex [0 1 255 16 -1])))
  (is (true? (b/token= TOK TOK)))
  (is (false? (b/token= TOK (str (subs TOK 0 63) "c"))))
  (is (false? (b/token= TOK (str TOK "0"))))
  (is (false? (b/token= nil nil)))
  (is (false? (b/token= TOK nil)))
  (is (false? (b/token= TOK 5)))
  (is (= {:port 8091 :token TOK :as-of A} (b/bridge-edn TOK A))))

(deftest t19-validate-happy-paths
  (is (= {:ok true :member :le-testeur :action :allow :prompt-id ID1 :text nil}
         (b/validate (rq "le-testeur" ALLOW) CTX)))
  (is (= {:ok true :member :le-testeur :action :allow :prompt-id ID1 :text nil}
         (b/validate (rq "le-testeur" (assoc ALLOW "text" "x")) CTX)))
  (is (= {:ok true :member :un-automate-monde :action :deny :prompt-id ID1 :text "use bb instead"}
         (b/validate (rq "un-automate-monde" (assoc ALLOW "action" "deny" "text" "  use bb instead ")) CTX)))
  (is (= {:ok true :member :un-automate-monde :action :deny :prompt-id ID1 :text nil}
         (b/validate (rq "un-automate-monde" (assoc ALLOW "action" "deny" "text" "   ")) CTX)))
  (is (= {:ok true :member :le-testeur :action :say :prompt-id nil :text "please rerun the gate"}
         (b/validate (rq "le-testeur" {"token" TOK "action" "say" "text" "please rerun the gate" "prompt-id" ID1}) CTX)))
  (let [v (b/validate (rq "un-automate-monde" (assoc ALLOW "action" "deny" "text" (apply str (repeat 1000 "a")))) CTX)]
    (is (true? (:ok v)))
    (is (= 1000 (count (:text v))))))

(deftest t20-validate-refusals
  (is (= {:ok false :status 405 :reason "method not allowed"}
         (b/validate (assoc (rq "le-testeur" ALLOW) :method "GET") CTX)))
  (doseq [uri ["/crew/" "/crew/Le-Testeur" "/crew/le-testeur/x" "/x"]]
    (is (= {:ok false :status 404 :reason "not found"}
           (b/validate (assoc (rq "le-testeur" ALLOW) :uri uri) CTX))
        uri))
  (doseq [body [:bad-json ["a"] nil]]
    (is (= {:ok false :status 400 :reason "bad json"}
           (b/validate (assoc (rq "le-testeur" ALLOW) :body body) CTX))
        body))
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (rq "le-testeur" (dissoc ALLOW "token")) CTX)))
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (rq "le-testeur" (assoc ALLOW "token" (str (subs TOK 0 63) "c"))) CTX)))
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (rq "le-testeur" (assoc ALLOW "token" (str TOK "0"))) CTX)))
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (rq "le-testeur" (assoc ALLOW "token" 5)) CTX)))
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (rq "le-testeur" (assoc ALLOW "token" nil)) {:token nil :members MEMBERS})))
  (doseq [origin ["http://127.0.0.1:8080" "http://localhost:8080/" "https://localhost:8080"
                  "http://localhost:8081" "null" nil]]
    (is (= {:ok false :status 403 :reason "bad origin"}
           (b/validate (assoc (rq "le-testeur" ALLOW) :origin origin) CTX))
        origin))
  (doseq [m ["hal" "le-coureur-omy" "le-docteur" "zz"]]
    (is (= {:ok false :status 404 :reason "unknown member"}
           (b/validate (rq m ALLOW) CTX))
        m))
  (is (= {:ok false :status 400 :reason "bad action"}
         (b/validate (rq "le-testeur" (assoc ALLOW "action" "approve")) CTX)))
  (is (= {:ok false :status 400 :reason "bad action"}
         (b/validate (rq "le-testeur" (dissoc ALLOW "action")) CTX)))
  (doseq [pid [(str/upper-case ID1) (subs ID1 0 39) nil 5]]
    (is (= {:ok false :status 400 :reason "bad prompt-id"}
           (b/validate (rq "le-testeur" (assoc ALLOW "prompt-id" pid)) CTX))
        pid))
  (is (= {:ok false :status 400 :reason "bad prompt-id"}
         (b/validate (rq "le-testeur" (assoc ALLOW "action" "deny" "prompt-id" nil)) CTX)))
  (is (= {:ok false :status 400 :reason "text required"}
         (b/validate (rq "le-testeur" {"token" TOK "action" "say"}) CTX)))
  (is (= {:ok false :status 400 :reason "text required"}
         (b/validate (rq "le-testeur" {"token" TOK "action" "say" "text" "  "}) CTX)))
  (doseq [action ["deny" "say"]]
    (is (= {:ok false :status 400 :reason "text too long"}
           (b/validate (rq "le-testeur" (assoc ALLOW "action" action "text" (apply str (repeat 1001 "a")))) CTX))
        action)
    (is (= {:ok false :status 400 :reason "bad text"}
           (b/validate (rq "le-testeur" (assoc ALLOW "action" action "text" 5)) CTX))
        action)
    (doseq [t ["line1\nline2" "\u001b[A" "tab\there"]]
      (is (= {:ok false :status 400 :reason "control characters"}
             (b/validate (rq "le-testeur" (assoc ALLOW "action" action "text" t)) CTX))
          [action t]))))

(deftest t21-validate-check-order
  (is (= 401 (:status (b/validate (assoc (rq "le-testeur" (dissoc ALLOW "token")) :origin "http://evil.example") CTX))))
  (is (= 401 (:status (b/validate (assoc (rq "le-testeur" (assoc ALLOW "token" "bad")) :origin "http://evil.example") CTX))))
  (is (= 403 (:status (b/validate (assoc (rq "hal" ALLOW) :origin "http://evil.example") CTX))))
  (is (= 404 (:status (b/validate (rq "hal" (assoc ALLOW "action" "approve")) CTX))))
  (is (= 400 (:status (b/validate (rq "le-testeur" {"token" TOK "action" "approve"}) CTX))))
  (is (= 405 (:status (b/validate (assoc (rq "le-testeur" (assoc ALLOW "token" "bad")) :method "GET") CTX)))))

(deftest t22-preflight-and-cors
  (is (= {:status 204 :headers {"Access-Control-Allow-Origin" "http://localhost:8080"
                                 "Access-Control-Allow-Methods" "POST"
                                 "Access-Control-Allow-Headers" "content-type"
                                 "Access-Control-Max-Age" "600" "Vary" "Origin"}}
         (b/preflight OK-ORIGIN)))
  (is (= {:status 403 :headers {} :body "bad origin"} (b/preflight "http://evil.example")))
  (is (= {:status 403 :headers {} :body "bad origin"} (b/preflight nil)))
  (is (= {"Access-Control-Allow-Origin" "http://localhost:8080" "Vary" "Origin"} (b/cors-headers OK-ORIGIN)))
  (is (= {} (b/cors-headers "http://evil.example"))))

(def VA {:ok true :member :le-testeur :action :allow :prompt-id ID1 :text nil})

(deftest t23-decide-allow
  (is (= {:ok true :steps [[:keys "1"]] :reason "allowed"}
         (b/decide VA {:status :blocked :prompt P1})))
  (is (= {:ok false :status 409 :reason "not blocked"}
         (b/decide VA {:status :working :prompt nil})))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (b/decide VA {:status :blocked :prompt P3})))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (b/decide VA {:status :blocked :prompt nil})))
  (is (= {:ok false :status 403 :reason "risky: your twin reviews this one"}
         (b/decide (assoc VA :prompt-id ID2) {:status :blocked :prompt P2})))
  (is (= {:ok false :status 403 :reason "unknown prompt"}
         (b/decide (assoc VA :prompt-id IDU) {:status :blocked :prompt (assoc PU :risky false)}))))

(def VD (assoc VA :action :deny))

(deftest t24-decide-deny
  (is (= {:ok true :steps [[:keys "esc"]] :reason "declined"}
         (b/decide VD {:status :blocked :prompt P1})))
  (is (= {:ok true :steps [[:keys "esc"] [:wait-idle 5000] [:prompt "use bb instead"]] :reason "declined"}
         (b/decide (assoc VD :text "use bb instead") {:status :blocked :prompt P1})))
  (is (= {:ok true :steps [[:keys "esc"]] :reason "declined"}
         (b/decide (assoc VD :prompt-id ID2) {:status :blocked :prompt P2})))
  (is (= {:ok false :status 409 :reason "not blocked"}
         (b/decide VD {:status :idle})))
  (is (= {:ok false :status 409 :reason "prompt changed"}
         (b/decide VD {:status :blocked :prompt P3}))))

(def VS {:ok true :member :le-testeur :action :say :prompt-id nil :text "hi"})

(deftest t25-decide-say
  (is (= {:ok true :steps [[:prompt "hi"]] :reason "sent"} (b/decide VS {:status :idle})))
  (is (= {:ok true :steps [[:prompt "hi"]] :reason "sent"} (b/decide VS {:status :done})))
  (is (= {:ok false :status 409 :reason "blocked: answer its prompt first"} (b/decide VS {:status :blocked})))
  (is (= {:ok false :status 409 :reason "busy"} (b/decide VS {:status :working})))
  (is (= {:ok false :status 409 :reason "busy"} (b/decide VS {:status :unknown}))))

(deftest t26-reply
  (is (= {:status 200 :headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "http://localhost:8080" "Vary" "Origin"}
          :body {"ok" true "result" "allowed"}}
         (b/reply {:ok true :steps [[:keys "1"]] :reason "allowed"} OK-ORIGIN)))
  (is (= {:status 401 :headers {"Content-Type" "application/json"} :body {"ok" false "reason" "bad token"}}
         (b/reply {:ok false :status 401 :reason "bad token"} "http://evil.example"))))

(deftest t27-argv
  (is (= ["herdr" "agent" "send-keys" "le-testeur" "1"] (b/step-argv "herdr" :le-testeur [:keys "1"])))
  (is (= ["herdr" "agent" "send-keys" "le-testeur" "esc"] (b/step-argv "herdr" :le-testeur [:keys "esc"])))
  (is (= ["herdr" "agent" "wait" "le-testeur" "--until" "idle" "--until" "done" "--timeout" "5000"]
         (b/step-argv "herdr" :le-testeur [:wait-idle 5000])))
  (is (= ["herdr" "agent" "prompt" "le-testeur" "use bb instead"]
         (b/step-argv "herdr" :le-testeur [:prompt "use bb instead"])))
  (is (= ["herdr" "agent" "prompt" "le-testeur" " --wait"]
         (b/step-argv "herdr" :le-testeur [:prompt "--wait"])))
  (is (= ["herdr" "agent" "read" "le-testeur" "--source" "visible"]
         (b/step-argv "herdr" :le-testeur [:read])))
  (is (= ["herdr" "agent" "list"] (b/step-argv "herdr" :le-testeur [:list])))
  (is (nil? (b/step-argv "herdr" :le-testeur [:shell "ls"]))))

(deftest t28-log-line
  (is (= "2026-11-08T00:52:00Z le-testeur allow 200 allowed"
         (b/log-line "2026-11-08T00:52:00Z" "le-testeur" "allow" 200 "allowed")))
  (is (= "T le-testeur say 200 sent" (b/log-line "T" :le-testeur :say 200 "sent")))
  (is (= "T ? ? 404 not found" (b/log-line "T" "../etc" "rm -rf" 404 "not found")))
  (is (= "T - - 405 method not allowed" (b/log-line "T" nil nil 405 "method not allowed")))
  (let [L (b/log-line "T" "le-testeur" "deny" 200 "declined")]
    (is (false? (str/includes? L TOK)))))

;; pm.crew

(deftest t29-parse-keeps-valid-prompt-on-blocked-only
  (is (= {:as-of 5 :agents {:a {:status :blocked :prompt P1} :b {:status :working}}}
         (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc P1 :extra 1)}
                                              :b {:status :working :prompt P1}}}))))
  (doseq [bad [(assoc P1 :id (str/upper-case ID1)) (assoc P1 :id "abc") (assoc P1 :kind :edit)
               (assoc P1 :text (apply str (repeat 401 "x"))) (assoc P1 :risky "no") "P1"]]
    (is (= {:as-of 5 :agents {:a {:status :blocked}}}
           (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt bad}}})))
        bad))
  (let [t400 (apply str (repeat 400 "x"))]
    (is (= {:as-of 5 :agents {:a {:status :blocked :prompt (assoc P1 :text t400)}}}
           (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc P1 :text t400)}}})))))
  (is (= {:as-of 5 :agents {:a {:status :blocked :prompt PU}}}
         (c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt PU}}})))))

(deftest t30-parse-titles
  (is (= {:as-of 5 :agents {} :titles {:le-testeur "Le Testeur"}}
         (c/parse (pr-str {:as-of 5 :agents {}
                            :titles {:le-testeur "Le Testeur" :x "" "s" "S" :y 3
                                     :z (apply str (repeat 65 "t"))}}))))
  (is (= {:as-of 5 :agents {}} (c/parse "{:as-of 5 :agents {} :titles [1]}")))
  (is (= {:as-of 5 :agents {} :titles {}} (c/parse "{:as-of 5 :agents {} :titles {}}"))))

(def B {:port 8091 :token TOK :as-of A})

(deftest t31-the-bridge-file
  (is (= B (c/parse-bridge (pr-str (assoc B :extra 1)))))
  (is (= B (c/parse-bridge (pr-str (b/bridge-edn TOK A)))))
  (doseq [bad [(pr-str (assoc B :port 0)) (pr-str (assoc B :port 65536)) (pr-str (assoc B :port "8091"))
               (pr-str (assoc B :token (subs TOK 0 63))) (pr-str (assoc B :token (str/upper-case TOK)))
               (pr-str (dissoc B :as-of)) (pr-str (assoc B :as-of -1))
               "{:port" "" nil 42]]
    (is (nil? (c/parse-bridge bad)) bad))
  (is (true? (c/bridge-on? B (+ A 15000))))
  (is (false? (c/bridge-on? B (+ A 15001))))
  (is (false? (c/bridge-on? nil A))))

(deftest t32-url-body-outcome
  (is (= "http://127.0.0.1:8091/crew/le-testeur" (c/bridge-url B :le-testeur)))
  (is (= {"token" TOK "action" "allow" "prompt-id" ID1} (c/request-body B :allow ID1 nil)))
  (is (= {"token" TOK "action" "deny" "prompt-id" ID1 "text" "use bb instead"}
         (c/request-body B :deny ID1 "use bb instead")))
  (is (= {"token" TOK "action" "say" "text" "hi"} (c/request-body B :say nil "hi")))
  (is (= {:result :ok} (c/outcome 200 nil)))
  (is (= {:result :ok} (c/outcome 204 nil)))
  (is (= {:result :stale :reason "prompt changed"} (c/outcome 409 "prompt changed")))
  (is (= {:result :error :reason "risky: Hal reviews this one"} (c/outcome 403 "risky: Hal reviews this one")))
  (is (= {:result :error :reason "refused"} (c/outcome 401 nil)))
  (is (= {:result :error :reason "bridge off"} (c/outcome nil nil))))

;; pm.talk: crew lines

(deftest t33-first-sighting-gives-prompt-line
  (is (= [{:n 1 :who "Un Automate (monde)" :text "wants to run node -e 'console.log(6*7)'" :at A
           :kind :crew-prompt :member :un-automate-monde :prompt P1 :state :pending}]
         (:entries T1)))
  (is (= 1 (:n T1)))
  (is (= 0 (:seen T1)))
  (is (= 1 (tk/unread T1)))
  (is (= 0 (:queue-end T1)))
  (is (= {:le-testeur {:status :working :prompt-id nil} :un-automate-monde {:status :blocked :prompt-id ID1}}
         (:crew-last T1)))
  (is (= 1 (:seen (tk/observe-crew (tk/open-panel T0) C1 A))))
  (is (nil? (tk/caption T1 A)))
  (is (= {} (:crew-last tk/empty-talk))))

(deftest t34-unchanged-stale-nil-identical
  (is (identical? T1 (tk/observe-crew T1 C1 (+ A 2000))))
  (is (identical? T1 (tk/observe-crew T1 (crew (+ A 2000) (:agents C1)) (+ A 2000))))
  (is (identical? T0 (tk/observe-crew T0 C1 (+ A 15001))))
  (is (identical? T0 (tk/observe-crew T0 nil A)))
  (is (identical? T1 (tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :blocked}}) (+ A 2000))))
  (is (not (identical? T0 (tk/observe-crew T0 C1 (+ A 15000))))))

(deftest t35-done-only-on-transition
  (let [T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                            (+ A 4000))]
    (is (= {:n 2 :who "Le Testeur" :text "done" :at (+ A 4000) :kind :crew-status :member :le-testeur}
           (e T2 2)))
    (is (= {:status :done :prompt-id nil} (get-in T2 [:crew-last :le-testeur])))
    (is (identical? T2 (tk/observe-crew T2 (crew (+ A 6000) {:un-automate-monde {:status :blocked :prompt P1}
                                                              :le-testeur {:status :done}})
                                         (+ A 6000)))))
  (let [first-done (tk/observe-crew T0 (crew A {:le-testeur {:status :done}}) A)]
    (is (= [] (:entries first-done)))
    (is (= 0 (:n first-done)))
    (is (= {:le-testeur {:status :done :prompt-id nil}} (:crew-last first-done))))
  (let [critique (tk/observe-crew T0 (crew A {:le-critique {:status :blocked :prompt PU}}) A)]
    (is (= "le-critique" (:who (e critique 1))))
    (is (= "is waiting at a prompt: Edit file / 3. No" (:text (e critique 1))))))

(deftest t36-prompt-lifecycle-on-crew-side
  (let [T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                            (+ A 4000))
        T3 (tk/observe-crew T2 (crew (+ A 6000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :blocked :prompt P2}})
                             (+ A 6000))
        T4 (tk/observe-crew T3 (crew (+ A 8000) {:un-automate-monde {:status :working}
                                                  :le-testeur {:status :blocked :prompt P2}})
                             (+ A 8000))
        T5 (tk/observe-crew T4 (crew (+ A 10000) {:un-automate-monde {:status :blocked :prompt P1}
                                                   :le-testeur {:status :blocked :prompt P2}})
                             (+ A 10000))
        T6 (tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :blocked :prompt P3}})
                             (+ A 2000))]
    (is (= {:n 3 :who "Le Testeur" :text "wants to run curl --version" :at (+ A 6000)
            :kind :crew-prompt :member :le-testeur :prompt P2 :state :pending}
           (e T3 3)))
    (is (= :stale (:state (e T4 1))))
    (is (= :pending (:state (e T4 3))))
    (is (= {:status :working :prompt-id nil} (get-in T4 [:crew-last :un-automate-monde])))
    (is (= 3 (:n T4)))
    (is (= {:n 4 :who "Un Automate (monde)" :text "wants to run node -e 'console.log(6*7)'" :at (+ A 10000)
            :kind :crew-prompt :member :un-automate-monde :prompt P1 :state :pending}
           (e T5 4)))
    (is (= 4 (:n T5)))
    (is (= :stale (:state (e T6 1))))
    (is (= "wants to run ls" (:text (e T6 2))))
    (is (= :pending (:state (e T6 2))))
    (is (= :stale (:state (e (tk/observe-crew T1 (crew (+ A 2000) {}) (+ A 2000)) 1))))))

(deftest t37-allow-round-trip
  (let [S (tk/crew-send T1 1 :allow "ignored")
        Y (tk/crew-result S 1 {:result :ok} (+ A 60000))]
    (is (= (assoc (e T1 1) :state :sending :action :allow) (e S 1)))
    (is (= (assoc (e T1 1) :state :allowed :action :allow :answered-at (+ A 60000)) (e Y 1)))
    (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ☑ allowed 5:01 PM"
           (tk/crew-label (e Y 1) OFF)))
    (is (= :allowed (:state (e (tk/observe-crew Y (crew (+ A 61000) (:agents C1)) (+ A 61000)) 1))))))

(deftest t38-deny-with-and-without-note
  (let [D (tk/crew-send T1 1 :deny "  use bb instead ")
        D2 (tk/crew-result D 1 {:result :ok} (+ A 60000))]
    (is (= (assoc (e T1 1) :state :sending :action :deny :note "use bb instead") (e D 1)))
    (is (= :declined (:state (e D2 1))))
    (is (= (+ A 60000) (:answered-at (e D2 1))))
    (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ✕ declined 5:01 PM · “use bb instead”"
           (tk/crew-label (e D2 1) OFF))))
  (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ✕ declined 5:01 PM"
         (tk/crew-label (e (tk/crew-result (tk/crew-send T1 1 :deny nil) 1 {:result :ok} (+ A 60000)) 1) OFF)))
  (is (not (contains? (e (tk/crew-send T1 1 :deny "   ") 1) :note)))
  (is (identical? T1 (tk/crew-send T1 1 :deny (apply str (repeat 1001 "a"))))))

(deftest t39-risky-and-unknown-no-allow-deny-works
  (let [T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                            (+ A 4000))
        T3 (tk/observe-crew T2 (crew (+ A 6000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :blocked :prompt P2}})
                             (+ A 6000))
        U (tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt PU}}) A)]
    (is (identical? T3 (tk/crew-send T3 3 :allow nil)))
    (is (= "⚠ Le Testeur · wants to run curl --version · ✕ declined 5:01 PM"
           (tk/crew-label (e (tk/crew-result (tk/crew-send T3 3 :deny nil) 3 {:result :ok} (+ A 60000)) 3) OFF)))
    (is (= "⚠ Le Testeur · wants to run curl --version · Hal reviews this one" (tk/crew-label (e T3 3) OFF JH)))
    (is (= "⚠ Le Testeur · is waiting at a prompt: Edit file / 3. No · Hal reviews this one"
           (tk/crew-label (e U 1) OFF JH)))
    (is (identical? U (tk/crew-send U 1 :allow nil)))
    (is (true? (tk/allowable? (e T1 1))))
    (is (false? (tk/allowable? (e T3 3))))
    (is (false? (tk/allowable? (e U 1))))
    (is (false? (tk/allowable? (e T2 2))))))

(deftest t40-stale-and-error-outcomes-invalid-calls
  (let [S (tk/crew-send T1 1 :allow "ignored")
        D (tk/crew-send T1 1 :deny "  use bb instead ")
        Y (tk/crew-result S 1 {:result :ok} (+ A 60000))
        T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                             (+ A 4000))]
    (is (= (assoc (e T1 1) :state :stale :action :allow)
           (e (tk/crew-result S 1 {:result :stale :reason "prompt changed"} (+ A 60000)) 1)))
    (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · already answered"
           (tk/crew-label (e (tk/crew-result S 1 {:result :stale :reason "prompt changed"} (+ A 60000)) 1) OFF)))
    (let [F (tk/crew-result D 1 {:result :error :reason "bridge off"} (+ A 60000))]
      (is (= (assoc (e T1 1) :error "bridge off") (e F 1)))
      (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · not sent: bridge off"
             (tk/crew-label (e F 1) OFF)))
      (is (= (assoc (e T1 1) :state :sending :action :allow) (e (tk/crew-send F 1 :allow nil) 1))))
    (is (= "Un Automate (monde) · wants to run node -e 'console.log(6*7)' · sending…" (tk/crew-label (e S 1) OFF)))
    (is (identical? T1 (tk/crew-result T1 1 {:result :ok} A)))
    (is (identical? Y (tk/crew-send Y 1 :deny nil)))
    (is (identical? S (tk/crew-send S 1 :allow nil)))
    (is (identical? T1 (tk/crew-send T1 99 :allow nil)))
    (is (identical? T2 (tk/crew-send T2 2 :deny nil)))
    (is (identical? T1 (tk/crew-send T1 1 :approve nil)))))

(deftest t41-say-lifecycle
  (let [Q (tk/crew-say T1 :le-testeur "Le Testeur" " please rerun the gate " (+ A 1000))
        T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                             (+ A 4000))]
    (is (= {:n 2 :who "You" :text "please rerun the gate" :at (+ A 1000) :kind :crew-say :member :le-testeur
            :to "Le Testeur" :state :sending}
           (e Q 2)))
    (is (= "You → Le Testeur: please rerun the gate · sending…" (tk/crew-label (e Q 2) OFF)))
    (is (= "You → Le Testeur: please rerun the gate"
           (tk/crew-label (e (tk/crew-result Q 2 {:result :ok} (+ A 2000)) 2) OFF)))
    (is (= (assoc (e Q 2) :state :failed :error "busy")
           (e (tk/crew-result Q 2 {:result :stale :reason "busy"} (+ A 2000)) 2)))
    (is (= "You → Le Testeur: please rerun the gate · not sent: busy"
           (tk/crew-label (e (tk/crew-result Q 2 {:result :stale :reason "busy"} (+ A 2000)) 2) OFF)))
    (is (= "bridge off" (:error (e (tk/crew-result Q 2 {:result :error :reason "bridge off"} (+ A 2000)) 2))))
    (is (identical? T1 (tk/crew-say T1 :le-testeur "Le Testeur" "   " A)))
    (is (identical? T1 (tk/crew-say T1 :le-testeur "Le Testeur" (apply str (repeat 1001 "a")) A)))
    (is (identical? T1 (tk/crew-say T1 "le-testeur" "Le Testeur" "hi" A)))
    (is (identical? T1 (tk/crew-say T1 :le-testeur "" "hi" A)))
    (is (= "Le Testeur · done" (tk/crew-label (e T2 2) OFF)))
    (is (nil? (tk/crew-label {:n 9 :who "Hal" :text "hi" :at A :kind :hal} OFF)))))

(deftest t42-badge-and-controls
  (let [T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :done}})
                             (+ A 4000))
        T3 (tk/observe-crew T2 (crew (+ A 6000) {:un-automate-monde {:status :blocked :prompt P1}
                                                  :le-testeur {:status :blocked :prompt P2}})
                             (+ A 6000))
        S (tk/crew-send T1 1 :allow "ignored")
        Y (tk/crew-result S 1 {:result :ok} (+ A 60000))
        Q (tk/crew-say T1 :le-testeur "Le Testeur" " please rerun the gate " (+ A 1000))]
    (is (false? (tk/prompt-waiting? T0)))
    (is (false? (tk/prompt-waiting? Y)))
    (is (false? (tk/prompt-waiting? (tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :working}}) (+ A 2000)))))
    (is (true? (tk/prompt-waiting? T1)))
    (is (true? (tk/prompt-waiting? S)))
    (is (true? (tk/prompt-waiting? T3)))
    (is (= {:allow? true :disabled? false :hint nil} (tk/crew-controls (e T1 1) true)))
    (is (= {:allow? true :disabled? true :hint "bridge off"} (tk/crew-controls (e T1 1) false)))
    (is (= {:allow? false :disabled? false :hint nil} (tk/crew-controls (e T3 3) true)))
    (is (= {:allow? false :disabled? true :hint "bridge off"} (tk/crew-controls (e T3 3) false)))
    (is (= {:allow? true :disabled? true :hint "sending…"} (tk/crew-controls (e S 1) true)))
    (is (= {:allow? true :disabled? true :hint "sending…"} (tk/crew-controls (e S 1) false)))
    (is (nil? (tk/crew-controls (e Y 1) true)))
    (is (nil? (tk/crew-controls (e T2 2) true)))
    (is (nil? (tk/crew-controls (e Q 2) true)))))

(deftest t43-roster-strip-every-member
  (is (= [{:member :le-testeur :who "Le Testeur" :status :working :say? false}
          {:member :un-architecte :who "Un Architecte" :status :unknown :say? false}
          {:member :un-automate-monde :who "Un Automate (monde)" :status :blocked :say? false}]
         (tk/crew-roster C1 A true)))
  (let [CR (crew A {:le-testeur {:status :done} :un-architecte {:status :idle}
                     :un-automate-monde {:status :blocked :prompt P1} :le-critique {:status :working}})]
    (is (= [[:le-critique "le-critique" false] [:le-testeur "Le Testeur" true]
            [:un-architecte "Un Architecte" true] [:un-automate-monde "Un Automate (monde)" false]]
           (mapv (juxt :member :who :say?) (tk/crew-roster CR A true))))
    (is (= [false false false false] (mapv :say? (tk/crew-roster CR A false))))
    (is (= [[:unknown false] [:unknown false] [:unknown false] [:unknown false]]
           (mapv (juxt :status :say?) (tk/crew-roster CR (+ A 15001) true)))))
  (is (= [] (tk/crew-roster nil A true))))

(deftest t44-persistence
  (let [D (tk/crew-send T1 1 :deny "  use bb instead ")
        D2 (tk/crew-result D 1 {:result :ok} (+ A 60000))
        step1 (tk/crew-say D2 :le-testeur "Le Testeur" "hi" (+ A 61000))
        ZZ (tk/observe-crew step1 (crew (+ A 62000) {:un-automate-monde {:status :blocked :prompt P1}
                                                      :le-testeur {:status :done}})
                             (+ A 62000))]
    (is (= {:n 3 :who "Le Testeur" :text "done" :at (+ A 62000) :kind :crew-status :member :le-testeur}
           (e ZZ 3)))
    (is (= 3 (:n ZZ)))
    (let [R2 (tk/restore (tk/save-text ZZ))]
      (is (= (e ZZ 1) (e R2 1)))
      (is (= (assoc (e ZZ 2) :state :failed :error "not confirmed") (e R2 2)))
      (is (= (e ZZ 3) (e R2 3)))
      (is (= 3 (:n R2)))
      (is (= {:un-automate-monde {:status :blocked :prompt-id ID1} :le-testeur {:status :done :prompt-id nil}}
             (:crew-last R2)))
      (is (= (:crew-last ZZ) (:crew-last R2)))
      (is (identical? R2 (tk/observe-crew R2 (crew (+ A 64000) {:un-automate-monde {:status :blocked :prompt P1}
                                                                 :le-testeur {:status :done}})
                                           (+ A 64000)))))
    (let [S (tk/crew-send T1 1 :allow "ignored")]
      (is (= (e T1 1) (e (tk/restore (tk/save-text S)) 1))))
    (let [T2 (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1}
                                                    :le-testeur {:status :done}})
                              (+ A 4000))
          DD (-> T2
                 (tk/observe-crew (crew (+ A 50000) {:le-testeur {:status :working}}) (+ A 50000))
                 (tk/observe-crew (crew (+ A 110000) {:le-testeur {:status :done}}) (+ A 110000)))]
      (is (= 2 (count (filter #(= :crew-status (:kind %)) (:entries (tk/restore (tk/save-text DD))))))))
    (is (= T0 (tk/restore (tk/save-text T0))))
    (let [input (pr-str {:v 1 :n 4 :seen 0
                          :entries [{:n 1 :who "Le Testeur" :text "done" :at A :kind :crew-status :member :le-testeur}
                                    (dissoc {:n 2 :who "Le Testeur" :text "done" :at A :kind :crew-status :member :le-testeur} :member)
                                    (assoc (e T1 1) :n 3 :prompt (assoc P1 :id "xyz"))
                                    (assoc (e T1 1) :n 4 :state :maybe)]})]
      (is (= [1] (mapv :n (:entries (tk/restore input))))))
    (is (= {:a {:status :done :prompt-id nil}}
           (:crew-last (tk/restore (pr-str {:v 1 :n 0 :seen 0 :entries []
                                             :crew-last {:a {:status :done :prompt-id nil}
                                                         :b {:status "x"}
                                                         "c" {:status :idle}
                                                         :d {:status :blocked :prompt-id "xyz"}}})))))))

(deftest t45-mirror-talk-untouched-by-crew-lines
  (let [W (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0)
        combined (tk/observe (tk/observe-crew T0 C1 A) W A)]
    (is (= (e T1 1) (e combined 1)))
    (is (= 3 (:n combined)))
    (is (= "Flora" (:who (e combined 2))))
    (is (= "Juniper" (:who (e combined 3))))))
