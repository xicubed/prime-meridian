(ns pm.crew-mirror-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs]
            [pm.prose :as pz] [pm.prompt :as pr] [pm.bridge :as b] [pm.fielder :as f]
            [pm.crew :as c] [pm.talk :as tk]))

(defn fx [k] (.readFileSync fs (str "test/fixtures/crew-mirror/" k ".txt") "utf8"))
(defn v1 [k] (.readFileSync fs (str ".crew/quests/crew-talk-v1/screens/un-automate-monde-" k ".txt") "utf8"))
(def TURN (fx "turn-tools-prose")) (def QUES (fx "question")) (def DIFF (fx "tool-diffs-long"))
(def NOTE (fx "agent-notice"))     (def BASH (fx "bash-multiline-prompt"))
(def R "/repo")
(def A 1794013200000)
(def TOK (apply str (repeat 16 "0a1b")))
(def ID1 "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6")   ; bash  node -e 'console.log(6*7)'
(def ID2 "06b65e49c507048bccab6121f64aaad2518f3b83")   ; bash  curl --version
(def ID3 "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787")   ; bash  ls
(def IDE "6a049a2c7e97372a1caad99f23478183fe038e13")   ; unknown  Edit file · …
(def IDB "808ee8155926e08a967210dcd47d7a8e254cd7ae")   ; bash  BASH, border stripped
(def EDIT-T "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?")
(def PC {:id ID2 :kind :bash :text "curl --version" :risky true :since (- A 8000)})
(def PU {:id IDE :kind :unknown :text EDIT-T :risky true :since (- A 3000) :always 2})
(def PL {:id ID3 :kind :bash :text "ls" :risky false :since (- A 1000)})
(def CR {:as-of A
         :agents {:un-automate-monde {:status :blocked :since (- A 9000) :prompt PC}
                  :le-testeur        {:status :blocked :since (- A 4000) :prompt PU}
                  :le-docteur        {:status :blocked :since (- A 2000) :prompt PL}
                  :le-coureur        {:status :idle :since (- A 2000)}}
         :titles {:un-automate-monde "Un Automate" :le-testeur "Le Testeur"
                  :le-docteur "Le Docteur" :le-coureur "Le Coureur"}})
(def CTX {:crew CR :hold nil :members #{:un-automate-monde :le-testeur :le-docteur :le-coureur}
          :log [] :tried {} :now A :crew-talk {:answer-after-s 5}})
(def P1 "Drafted bench-south-of-house at [8.5 6.86 -30], yaw 90, 4.03 m from the search point — the nearest level open ground past the tree/bush cover near where Michael was looking. Told him in-world.")
(def Q  "The spot under the trees by the lobby has no open ground within 6 m, so I didn't place the bench there. Try the bench behind the house instead?")
(def S-DIFF "Implemented spec §2–4 in pm.talk (new :crew-reply kind, demo-member-line, say-reply, crew-reply, crew-label/restore arms, with-walkers 5-arity), wired on-say and the roster's with-walkers call in pm.core, and added the :member-talk block t…")
(def S-NOTE "New file test/pm/demo_talk_test.cljs (ns pm.demo-talk-test), 206 lines, covering all 27 numbered cases from spec §6: - t01–t05: estate :member-talk block and demo-member-line (both fixtures, no-jumeaux, blank/non-string/nil edge cases) - t…")
(def USAGE "usage: bb crew-answer <member> allow|always|deny [--note \"…\"]")

;; Prose extraction (pm.prose)

(deftest t01-prose-turn-with-tools
  (is (= ["Placed. Now let Michael know." P1] (pz/prose-blocks TURN)))
  (is (= P1 (pz/last-said TURN)))
  (is (= 192 (count P1))))

(deftest t02-prose-question
  (is (= ["PASS done-1687" Q] (pz/prose-blocks QUES)))
  (is (= Q (pz/last-said QUES)))
  (is (true? (pz/question? Q))))

(deftest t03-prose-tool-calls-and-diffs
  (let [blocks (pz/prose-blocks DIFF)
        last-block (pz/last-said DIFF)]
    (is (= 2 (count blocks)))
    (is (= "Now let's view the final diff stat to report." (first blocks)))
    (is (= 721 (count last-block)))
    (is (str/starts-with? last-block "Implemented spec §2–4 in pm.talk"))
    (is (str/ends-with? last-block "changed). done-5271"))
    (is (not (some #(str/includes? % "⎿") blocks)))
    (is (not (some #(str/includes? % "Couldn't edit") blocks)))
    (is (not (some #(str/includes? % "lass=\"theirs\"") blocks)))))

(deftest t04-prose-notices-explore-bullets
  (let [blocks (pz/prose-blocks NOTE)
        last-block (pz/last-said NOTE)]
    (is (= 3 (count blocks)))
    (is (= "Good, file doesn't exist yet. Let me peek at crew_talk_test.cljs for style conventions while the Explore agent runs."
           (first blocks)))
    (is (= "Now I have everything confirmed. Writing the test file per spec §6." (second blocks)))
    (is (not (some #(str/includes? % "Agent \"") blocks)))
    (is (not (some #(str/includes? % "Explore(") blocks)))
    (is (not (some #(str/includes? % "Write(") blocks)))
    (is (= 1121 (count last-block)))
    (is (str/ends-with? last-block "that's expected per the brief. done-3540"))
    (is (not (str/includes? last-block "new task?")))))

(deftest t05-prose-dialog-on-screen
  (is (= [] (pz/prose-blocks BASH)))
  (is (nil? (pz/last-said BASH))))

(deftest t06-prose-synthetic-edges
  (is (= [] (pz/prose-blocks "⏺ Bash(ls)\n")))
  (is (= [] (pz/prose-blocks "⏺ Running 1 shell command…\n  ⎿  $ ls\n")))
  (is (= ["Done." "Next."] (pz/prose-blocks "⏺ Done.\n\n  Ran 1 shell command\n\n⏺ Next.")))
  (is (= ["Hi."] (pz/prose-blocks "⏺ Hi.\n\n※ recap: did a thing\n  more\n")))
  (is (= ["One two three"] (pz/prose-blocks "⏺ One\n  two\n\n  three\n\n✻ Baked for 1s")))
  (is (nil? (pz/last-said "⏺ Old answer.\n\n❯ new ask\n\n  Ran 1 shell command\n")))
  (is (= ["Old answer."] (pz/prose-blocks "⏺ Old answer.\n\n❯ new ask\n\n  Ran 1 shell command\n")))
  (is (nil? (pz/last-said nil)))
  (is (nil? (pz/last-said "")))
  (is (= [] (pz/prose-blocks nil))))

;; Summary, question, turn end

(deftest t07-summary-unchanged-and-trimmed
  (is (= P1 (pz/summary P1)))
  (is (= S-DIFF (pz/summary (pz/last-said DIFF))))
  (is (= 240 (count (pz/summary (pz/last-said DIFF)))))
  (is (= S-NOTE (pz/summary (pz/last-said NOTE))))
  (is (= 240 (count (pz/summary (pz/last-said NOTE))))))

(deftest t08-summary-edge-lengths
  (let [a240 (apply str (repeat 240 "a"))
        a241 (apply str (repeat 241 "a"))
        a238sp (str (apply str (repeat 238 "a")) " bcd")]
    (is (= a240 (pz/summary a240)))
    (let [r241 (pz/summary a241)]
      (is (= 240 (count r241)))
      (is (str/ends-with? r241 "aaa…")))
    (let [r242 (pz/summary a238sp)]
      (is (= 239 (count r242)))
      (is (= (str (apply str (repeat 238 "a")) "…") r242)))
    (is (nil? (pz/summary nil)))))

(deftest t09-question-predicate
  (is (true? (pz/question? Q)))
  (is (true? (pz/question? (str Q "  "))))
  (is (false? (pz/question? P1)))
  (is (false? (pz/question? "Is it? Yes.")))
  (is (false? (pz/question? nil))))

(deftest t10-turn-ended
  (is (true? (pz/turn-ended? :working :idle)))
  (is (true? (pz/turn-ended? :working :done)))
  (is (true? (pz/turn-ended? :blocked :idle)))
  (is (false? (pz/turn-ended? :idle :idle)))
  (is (false? (pz/turn-ended? nil :idle)))
  (is (false? (pz/turn-ended? :working :working)))
  (is (false? (pz/turn-ended? :working :blocked))))

(deftest t11-claude-members
  (is (= #{:un-automate-monde}
         (pz/claude-members {:result {:agents [{:name "un-automate-monde" :agent "claude"}
                                                {:name "le-critique" :agent "hermes"}
                                                {:name "Bad" :agent "claude"}
                                                {:name "le-testeur"}]}})))
  (is (= #{} (pz/claude-members nil))))

;; crew-said.edn rules

(deftest t12-add-said-fresh
  (is (= {:as-of 1000 :said {:le-coureur [{:at 1000 :text Q :question? true}]}}
         (pz/add-said nil :le-coureur Q 1000))))

(deftest t13-add-said-identical-and-append
  (let [s1 (pz/add-said nil :le-coureur Q 1000)]
    (is (identical? s1 (pz/add-said s1 :le-coureur Q 2000)))
    (is (= [{:at 1000 :text Q :question? true} {:at 1001 :text P1 :question? false}]
           (get-in (pz/add-said s1 :le-coureur P1 1000) [:said :le-coureur])))))

(deftest t14-add-said-long-text
  (is (= {:at 5 :text S-DIFF :question? false}
         (get-in (pz/add-said nil :un-automate-monde (pz/last-said DIFF) 5) [:said :un-automate-monde 0]))))

(deftest t15-add-said-cap-at-20
  (let [said (reduce (fn [s i] (pz/add-said s :le-coureur (str "t" i) (inc i))) nil (range 0 21))]
    (is (= 20 (count (get-in said [:said :le-coureur]))))
    (is (= {:at 2 :text "t1" :question? false} (first (get-in said [:said :le-coureur]))))))

(deftest t16-add-said-invalid-input
  (is (nil? (pz/add-said nil "le-coureur" Q 1)))
  (let [s1 (pz/add-said nil :le-coureur Q 1000)]
    (is (identical? s1 (pz/add-said s1 :le-coureur "  " 5)))))

(deftest t17-parse-said-roundtrip-and-filtering
  (let [s1 (pz/add-said nil :le-coureur Q 1000)
        s2 (pz/add-said s1 :le-coureur P1 1000)]
    (is (= s2 (pz/parse-said (pr-str s2)))))
  (is (= {:as-of 3 :said {:le-coureur [{:at 5 :text "fine" :question? false}]}}
         (pz/parse-said (pr-str {:as-of 3
                                  :said {:le-coureur [{:at -1 :text "x" :question? false}
                                                       {:at 2 :text "" :question? false}
                                                       {:at 4 :text "ok" :question? "yes"}
                                                       {:at 5 :text "fine" :question? false :extra 1}]
                                         :Bad [{:at 1 :text "x" :question? false}]}}))))
  (is (nil? (pz/parse-said "nope")))
  (is (nil? (pz/parse-said "[1]")))
  (is (nil? (pz/parse-said nil))))

;; Prompts: reading, Always, reasons (pm.prompt)

(deftest t18-bordered-wrapped-prompt
  (let [p (pr/read-prompt BASH R)]
    (is (= IDB (:id p)))
    (is (= :bash (:kind p)))
    (is (true? (:risky p)))
    (is (= 400 (count (:text p))))
    (is (str/starts-with? (:text p)
                           "mkdir -p /private/tmp/pm-fixture for name in bench lamp-post table planter; do printf"))
    (is (str/ends-with? (:text p) "…"))
    (is (false? (str/includes? (:text p) "│"))))
  (let [ls (:lines (pr/parse-screen BASH))]
    (is (= 15 (count ls)))
    (is (= "mkdir -p /private/tmp/pm-fixture" (first ls)))
    (is (= "cat /private/tmp/pm-fixture/places.txt" (last ls)))))

(deftest t19-always-option
  (is (= 2 (pr/always-option (v1 "190664ad70"))))
  (is (= 2 (pr/always-option (v1 "49184610d7"))))
  (is (= 2 (pr/always-option (.readFileSync fs ".crew/quests/crew-talk-v0/claude-bash-prompt.txt" "utf8"))))
  (is (nil? (pr/always-option BASH)))
  (is (nil? (pr/always-option nil)))
  (is (nil? (pr/always-option TURN))))

(deftest t20-always-option-synthetic-dialogs
  (is (= 2 (pr/always-option
             (str/join "\n" [" Edit file" " src/pm/core.cljs" " Do you want to make this edit to core.cljs?"
                              " ❯ 1. Yes" "   2. Yes, allow all edits during this session (shift+tab)" "   3. No"]))))
  (is (= 2 (pr/always-option
             (str/join "\n" [" Fetch" " https://example.com/notes" " Do you want to allow Claude to fetch this content?"
                              " ❯ 1. Yes" "   2. Yes, and don't ask again for example.com"
                              "   3. No, and tell Claude what to do differently (esc)"]))))
  (is (nil? (pr/always-option
              (str/join "\n" [" Bash command" " ╌╌╌" " ls" " ╌╌╌" " Do you want to proceed?" " ❯ 1. Yes"
                               "   2. Yes, and switch to auto mode · auto" "      mode handles these prompts for you"
                               "   3. No"])))))

(deftest t21-with-always
  (is (= {:m {:status :blocked :prompt (assoc PC :always 2)} :w {:status :working}}
         (pr/with-always {:m {:status :blocked :prompt PC} :w {:status :working}}
                          {:m (v1 "49184610d7") :w (v1 "49184610d7")})))
  (is (= {:m {:status :blocked :prompt PC} :w {:status :working}}
         (pr/with-always {:m {:status :blocked :prompt PC} :w {:status :working}}
                          {:m BASH :w (v1 "49184610d7")})))
  (is (= {:m {:status :blocked :prompt PC} :w {:status :working}}
         (pr/with-always {:m {:status :blocked :prompt PC} :w {:status :working}} {}))))

(deftest t22-risk-reason
  (is (= "writes outside the repo" (pr/risk-reason :bash (:text (pr/read-prompt BASH R)) R)))
  (is (= "reaches the network" (pr/risk-reason :bash "curl --version" R)))
  (is (= "changes git state" (pr/risk-reason :bash "git push origin main" R)))
  (is (= "deletes files" (pr/risk-reason :bash "rm -rf out" R)))
  (is (= "reaches another machine" (pr/risk-reason :bash "ssh host ls" R)))
  (is (= "installs software" (pr/risk-reason :bash "npm install x" R)))
  (is (= "runs as root" (pr/risk-reason :bash "sudo ls" R)))
  (is (= "changes system services" (pr/risk-reason :bash "launchctl list" R)))
  (is (= "too long to check" (pr/risk-reason :bash (str "echo " (apply str (repeat 395 "a"))) R)))
  (is (nil? (pr/risk-reason :bash "ls" R)))
  (is (= "not a plain shell command" (pr/risk-reason :unknown EDIT-T R)))
  (is (= 11 (count pr/risk-reasons))))

;; Bridge (pm.bridge)

(defn rq [body] {:method "POST" :uri "/crew/le-testeur" :origin b/allowed-origin :body (merge {"token" TOK} body)})
(def ctx {:token TOK :members #{:le-testeur}})

(deftest t23-validate-approve
  (is (= {:ok true :member :le-testeur :action :approve :prompt-id IDE :text nil :by :twin}
         (b/validate (rq {"action" "approve" "prompt-id" IDE "by" "twin"}) ctx))))

(deftest t24-validate-always-ignores-text-no-by
  (let [r (b/validate (rq {"action" "always" "prompt-id" IDE "text" "ignored"}) ctx)]
    (is (= {:ok true :member :le-testeur :action :always :prompt-id IDE :text nil} r))
    (is (not (contains? r :by)))))

(deftest t25-validate-bad-prompt-id-and-by
  (is (= {:ok false :status 400 :reason "bad prompt-id"} (b/validate (rq {"action" "approve"}) ctx)))
  (is (= {:ok false :status 400 :reason "bad by"}
         (b/validate (rq {"action" "approve" "prompt-id" IDE "by" "hal"}) ctx))))

(deftest t26-validate-deny-and-allow-unchanged
  (is (= {:ok true :member :le-testeur :action :deny :prompt-id IDE :text "no thanks" :by :player}
         (b/validate (rq {"action" "deny" "prompt-id" IDE "text" "  no thanks " "by" "player"}) ctx)))
  (is (= {:ok true :member :le-testeur :action :allow :prompt-id IDE :text nil}
         (b/validate (rq {"action" "allow" "prompt-id" IDE}) ctx))))

(deftest t27-decide-approve-always
  (let [LU {:status :blocked :prompt (dissoc PU :always :since) :always 2}]
    (is (= {:ok true :steps [[:keys "1"]] :reason "allowed"}
           (b/decide {:action :approve :prompt-id IDE} LU)))
    (is (= {:ok true :steps [[:keys "2"]] :reason "allowed always"}
           (b/decide {:action :always :prompt-id IDE} LU)))
    (is (= {:ok false :status 409 :reason "no always option"}
           (b/decide {:action :always :prompt-id IDE} (dissoc LU :always))))
    (is (= {:ok false :status 409 :reason "prompt changed"}
           (b/decide {:action :approve :prompt-id ID1} LU)))
    (is (= {:ok false :status 409 :reason "not blocked"}
           (b/decide {:action :approve :prompt-id IDE} {:status :idle})))
    (is (= {:ok true :steps [[:keys "1"]] :reason "allowed"}
           (b/decide {:action :approve :prompt-id ID2} {:status :blocked :prompt (dissoc PC :since)})))
    (is (= {:ok false :status 403 :reason "unknown prompt"}
           (b/decide {:action :allow :prompt-id IDE} LU)))))

(deftest t28-log-action
  (is (= [:allowed :allowed :allowed :declined nil]
         (mapv b/log-action [:allow :approve :always :deny :say]))))

(deftest t29-log-line-new-actions
  (is (= "T le-testeur approve 200 allowed by the twin"
         (b/log-line "T" "le-testeur" "approve" 200 "allowed by the twin")))
  (is (= "T le-testeur always 200 allowed always"
         (b/log-line "T" :le-testeur :always 200 "allowed always")))
  (is (= "T le-testeur forward 200 forwarded to the twin"
         (b/log-line "T" :le-testeur :forward 200 "forwarded to the twin"))))

(deftest t30-step-argv-read-recent-and-relay
  (is (= ["herdr" "agent" "read" "le-coureur" "--source" "recent" "--lines" "200"]
         (b/step-argv "herdr" :le-coureur [:read-recent])))
  (is (= ["py" "scripts/hal_relay.py" "s1" "hi"]
         (b/relay-argv "py" "scripts/hal_relay.py" "s1" "hi")))
  (is (true? (b/relay-ok? "{\"ok\": true, \"status\": \"queued\"}")))
  (is (false? (b/relay-ok? "{\"ok\": false}")))
  (is (false? (b/relay-ok? nil))))

;; Forwarding and crew-answer (pm.fielder)

(deftest t31-forwardable-basic
  (is (= [{:member :le-testeur :prompt PU} {:member :un-automate-monde :prompt PC}]
         (f/forwardable CTX nil))))

(deftest t32-forwardable-already-forwarded
  (is (= [{:member :le-testeur :prompt PU}]
         (f/forwardable CTX {:forwarded {:un-automate-monde {:id ID2 :at (- A 5000)}}})))
  (is (= [{:member :le-testeur :prompt PU} {:member :un-automate-monde :prompt PC}]
         (f/forwardable CTX {:forwarded {:un-automate-monde {:id ID1 :at 1}}}))))

(deftest t33-forwardable-hold-on
  (is (= [] (f/forwardable (assoc CTX :hold {:on true :at 1}) nil))))

(deftest t34-forwardable-answered-in-log
  (is (= [{:member :un-automate-monde :prompt PC}]
         (f/forwardable (assoc CTX :log [{:at (- A 100) :member :le-testeur :action :allowed :by :twin
                                           :prompt-id IDE :text EDIT-T}])
                         nil))))

(deftest t35-forwardable-stale-and-roster
  (is (= [] (f/forwardable (assoc-in CTX [:crew :as-of] (- A 20000)) nil)))
  (is (= [{:member :le-testeur :prompt PU}]
         (f/forwardable (assoc CTX :members #{:le-testeur}) nil)))
  (is (= [] (f/forwardable (assoc CTX :crew nil) nil))))

(deftest t36-forward-text-bash
  (is (= (str "[from the crew] Un Automate asks to run: curl --version (risky: reaches the network) · "
              "answer with: bb crew-answer un-automate-monde allow|deny [--note \"…\"]")
         (f/forward-text "Un Automate" :un-automate-monde PC "reaches the network"))))

(deftest t37-forward-text-unknown-and-no-reason
  (is (= (str "[from the crew] Le Testeur asks to run: Edit file · src/pm/core.cljs · "
              "Do you want to make this edit to core.cljs? (risky: not a plain shell command) · "
              "answer with: bb crew-answer le-testeur allow|always|deny [--note \"…\"]")
         (f/forward-text "Le Testeur" :le-testeur PU "not a plain shell command")))
  (is (= "[from the crew] Le Docteur asks to run: ls · answer with: bb crew-answer le-docteur allow|deny [--note \"…\"]"
         (f/forward-text "Le Docteur" :le-docteur PL nil))))

(deftest t38-record-and-parse-forwarded
  (is (= {:as-of A :forwarded {:le-testeur {:id IDE :at A}}}
         (f/record-forward nil :le-testeur PU A)))
  (is (= {:as-of A :forwarded {:le-testeur {:id IDE :at A}}}
         (f/parse-forwarded (pr-str (f/record-forward nil :le-testeur PU A)))))
  (is (= {:as-of 1 :forwarded {:b {:id IDE :at 2}}}
         (f/parse-forwarded (pr-str {:as-of 1 :forwarded {:a {:id "abc" :at 1} :b {:id IDE :at 2}}}))))
  (is (nil? (f/parse-forwarded "x"))))

(deftest t39-answer-request-allow-and-always
  (is (= {:ok true :member :le-testeur :action "approve" :prompt-id IDE :text nil}
         (f/answer-request ["le-testeur" "allow"] CR nil)))
  (is (= {:ok true :member :le-testeur :action "always" :prompt-id IDE :text nil}
         (f/answer-request ["le-testeur" "always"] CR nil))))

(deftest t40-answer-request-deny-with-note
  (is (= {:ok true :member :un-automate-monde :action "deny" :prompt-id ID2 :text "use bb instead"}
         (f/answer-request ["un-automate-monde" "deny" "--note" "  use bb instead  "] CR nil))))

(deftest t41-answer-request-forwarded-id-wins
  (is (= {:ok true :member :un-automate-monde :action "approve" :prompt-id ID1 :text nil}
         (f/answer-request ["un-automate-monde" "allow"] CR {:forwarded {:un-automate-monde {:id ID1 :at 5}}}))))

(deftest t42-answer-request-validation
  (is (= {:ok false :reason USAGE} (f/answer-request [] CR nil)))
  (is (= {:ok false :reason USAGE} (f/answer-request ["le-testeur"] CR nil)))
  (is (= {:ok false :reason USAGE} (f/answer-request ["le-testeur" "yes"] CR nil)))
  (is (= {:ok false :reason "bad member"} (f/answer-request ["Le Testeur" "allow"] CR nil)))
  (is (= {:ok false :reason "--note goes with deny"} (f/answer-request ["le-testeur" "allow" "--note" "x"] CR nil)))
  (is (= {:ok false :reason USAGE} (f/answer-request ["le-testeur" "deny" "--note"] CR nil)))
  (is (= {:ok false :reason USAGE} (f/answer-request ["le-testeur" "deny" "--force"] CR nil)))
  (is (= {:ok false :reason "empty note"} (f/answer-request ["le-testeur" "deny" "--note" "   "] CR nil)))
  (is (= {:ok false :reason "note too long"}
         (f/answer-request ["le-testeur" "deny" "--note" (apply str (repeat 1001 "a"))] CR nil)))
  (is (= {:ok false :reason "control characters in note"}
         (f/answer-request ["le-testeur" "deny" "--note" "a\tb"] CR nil)))
  (is (= {:ok false :reason "no prompt from le-coureur to answer"} (f/answer-request ["le-coureur" "allow"] CR nil)))
  (is (= {:ok false :reason "no prompt from le-testeur to answer"} (f/answer-request ["le-testeur" "allow"] nil nil))))

(deftest t43-answer-body
  (is (= {"token" TOK "action" "deny" "prompt-id" IDE "by" "twin" "text" "no"}
         (f/answer-body {:ok true :member :le-testeur :action "deny" :prompt-id IDE :text "no"} TOK)))
  (let [body (f/answer-body {:ok true :member :le-testeur :action "deny" :prompt-id IDE :text nil} TOK)]
    (is (not (contains? body "text")))))

;; crew.edn parse (pm.crew)

(deftest t44-crew-parse-always
  (is (= 2 (get-in (c/parse (pr-str {:as-of 5 :agents {:t {:status :blocked :prompt (assoc (dissoc PU :since) :always 2)}}}))
                    [:agents :t :prompt :always])))
  (doseq [bad [1 "2" 10]]
    (let [parsed (c/parse (pr-str {:as-of 5 :agents {:t {:status :blocked :prompt (assoc (dissoc PU :since) :always bad)}}}))]
      (is (= IDE (get-in parsed [:agents :t :prompt :id])))
      (is (not (contains? (get-in parsed [:agents :t :prompt]) :always))))))

;; 💭 (pm.talk)

(def SAID {:as-of A
           :said {:le-coureur [{:at (- A 3600000) :text "old" :question? false}
                                {:at (- A 2000) :text Q :question? true}]
                  :un-automate-monde [{:at (- A 1000) :text P1 :question? false}]}})
(def CS {:as-of A
         :agents {:le-coureur {:status :idle :since (- A 2000)} :un-automate-monde {:status :working}}
         :titles {:le-coureur "Le Coureur" :un-automate-monde "Un Automate"}})

(deftest t45-observe-said-basic
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (= [{:n 1 :who "Le Coureur" :text Q :at (- A 2000) :kind :crew-said :member :le-coureur :question? true}
            {:n 2 :who "Un Automate" :text P1 :at (- A 1000) :kind :crew-said :member :un-automate-monde :question? false}]
           (:entries t1)))
    (is (= {:le-coureur (- A 2000) :un-automate-monde (- A 1000)} (:said-at t1)))
    (is (= 0 (:queue-end t1)))))

(deftest t46-observe-said-identical-and-new-item
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (identical? t1 (tk/observe-said t1 SAID CS A)))
    (let [said2 (update-in SAID [:said :un-automate-monde] conj {:at A :text "Next." :question? false})
          t2 (tk/observe-said t1 said2 CS A)]
      (is (= 3 (count (:entries t2))))
      (let [last-e (last (:entries t2))]
        (is (= 3 (:n last-e)))
        (is (= "Next." (:text last-e)))))))

(deftest t47-crew-label-crew-said
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (= (str "Le Coureur asks: " Q) (tk/crew-label (tk/entry t1 1) 0)))
    (is (= (str "Un Automate · " P1) (tk/crew-label (tk/entry t1 2) 0)))))

(deftest t48-crew-said-quiet
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (= 0 (tk/unread t1)))
    (is (false? (tk/counts? (tk/entry t1 1))))
    (is (true? (contains? tk/crew-kinds :crew-said)))))

(deftest t49-said-questions
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (= {"le-coureur" {:text Q :since (- A 2000)}} (tk/said-questions t1 CS A)))))

(deftest t50-said-questions-clears
  (let [t1 (tk/observe-said nil SAID CS A)]
    (is (= {} (tk/said-questions t1 (assoc-in CS [:agents :le-coureur :status] :working) A)))
    (is (= {} (tk/said-questions t1 (assoc-in CS [:agents :le-coureur] {:status :idle :since (+ A 4000)}) A)))
    (let [t1' (tk/crew-say t1 :le-coureur "Le Coureur" "yes, try it" A)]
      (is (= {} (tk/said-questions t1' CS A))))
    (is (= {} (tk/said-questions t1 (assoc CS :as-of (- A 20000)) A)))
    (let [t1' (tk/crew-say t1 :le-coureur "Le Coureur" "yes, try it" A)
          n   (:n t1')
          t1'' (tk/crew-result t1' n {:result :error :reason "bridge off"} A)]
      (is (= {"le-coureur" {:text Q :since (- A 2000)}} (tk/said-questions t1'' CS A))))))

(deftest t51-observe-says-as-lines
  (let [WS {:jumeaux {:twin {:name "Hal"}}
            :walkers [{:agent :hal :name "Hal"} {:agent :un-automate-monde :name "Un Automate"}]
            :says {"un-automate-monde" {:text "Drafted the bench behind the house." :since A}
                   "hal" {:text "On it." :since A}}}
        t1 (tk/observe-says nil WS)]
    (is (= [{:n 1 :who "Un Automate" :text "Drafted the bench behind the house." :at A
             :kind :crew-reply :member :un-automate-monde}]
           (:entries t1)))
    (is (= {"un-automate-monde" A} (:says-at t1)))
    (is (identical? t1 (tk/observe-says t1 WS)))
    (is (= "Un Automate · Drafted the bench behind the house." (tk/crew-label (tk/entry t1 1) 0)))
    (is (true? (tk/counts? (tk/entry t1 1))))))

(deftest t52-observe-says-second-twin-and-member-fallback
  (let [WA {:jumeaux {:twin {:name "Ada"}}
            :walkers [{:agent :ada :name "Ada"} {:agent :hal :name "Hal"}]
            :says {"hal" {:text "Hi." :since A} "ada" {:text "Hello." :since A}}}
        t1 (tk/observe-says nil WA)]
    (is (= 1 (count (:entries t1))))
    (is (= :hal (:member (tk/entry t1 1))))
    (is (= "Hal" (:who (tk/entry t1 1))))
    (is (= "Hi." (:text (tk/entry t1 1))))))

(deftest t53-prompt-controls
  (let [PE {:n 1 :kind :crew-prompt :state :pending :member :le-testeur :prompt (dissoc PU :since)}]
    (is (= {:allow? true :always? true :disabled? false :hint nil} (tk/prompt-controls PE true)))
    (is (= {:allow? true :always? false :disabled? true :hint "bridge off"}
           (tk/prompt-controls (update PE :prompt dissoc :always) false)))
    (is (= {:allow? true :always? true :disabled? true :hint "sending…"}
           (tk/prompt-controls (assoc PE :state :sending) true)))
    (is (nil? (tk/prompt-controls (assoc PE :state :allowed) true)))
    (is (nil? (tk/crew-controls PE true)))))

(deftest t54-crew-send-and-result-unknown-prompt
  (let [tp (tk/observe-crew nil {:as-of A :agents {:le-testeur {:status :blocked :prompt (dissoc PU :since)}}
                                   :titles {:le-testeur "Le Testeur"}} A)]
    (let [sent (tk/crew-send tp 1 :approve nil)]
      (is (= :sending (:state (tk/entry sent 1))))
      (is (= :approve (:action (tk/entry sent 1)))))
    (is (identical? tp (tk/crew-send tp 1 :allow nil)))
    (let [allowed (tk/crew-result (tk/crew-send tp 1 :approve nil) 1 {:result :ok} A)]
      (is (= :allowed (:state (tk/entry allowed 1))))
      (is (str/ends-with? (tk/crew-label (tk/entry allowed 1) -480) " · ☑ allowed 5:00 PM")))
    (let [always (tk/crew-result (tk/crew-send tp 1 :always nil) 1 {:result :ok} A)]
      (is (str/ends-with? (tk/crew-label (tk/entry always 1) -480) " · ☑ always allowed 5:00 PM")))
    (let [tp2 (tk/observe-crew nil {:as-of A :agents {:le-testeur {:status :blocked :prompt (dissoc PU :since :always)}}
                                      :titles {:le-testeur "Le Testeur"}} A)]
      (is (identical? tp2 (tk/crew-send tp2 1 :always nil))))))

(deftest t55-command-as-read-preview
  (let [tb (tk/observe-crew nil {:as-of A :agents {:un-automate-monde
                                                    {:status :blocked
                                                     :prompt {:id IDB :kind :bash
                                                              :text (:text (pr/read-prompt BASH R))
                                                              :risky true}}}
                                  :titles {:un-automate-monde "Un Automate"}} A)
        e1 (tk/entry tb 1)]
    (is (= 173 (count (:text e1))))
    (is (str/starts-with? (:text e1)
                           "wants to run mkdir -p /private/tmp/pm-fixture for name in bench lamp-post table planter; do printf"))
    (is (str/ends-with? (:text e1) "…"))
    (let [label (tk/crew-label e1 0 {:twin "Hal"})]
      (is (str/starts-with? label "⚠ Un Automate · wants to run mkdir -p"))
      (is (str/ends-with? label "… · Hal reviews this one")))
    (is (= "ls" (tk/prompt-preview "ls")))
    (is (= 160 (count (tk/prompt-preview (apply str (repeat 161 "x"))))))))

(deftest t56-persistence
  (let [t1 (tk/observe-said nil SAID CS A)
        restored (tk/restore (tk/save-text t1))]
    (is (= (:entries t1) (:entries restored)))
    (is (= (:said-at t1) (:said-at restored))))
  (is (not (contains? (tk/restore (tk/save-text tk/empty-talk)) :said-at)))
  (is (= [] (:entries (tk/restore (pr-str {:v 1 :n 1
                                            :entries [{:n 1 :who "Le Coureur" :text "x" :at A :kind :crew-said}]}))))))

(deftest t-manager-compaction-notice-is-not-prose
  ;; Claude Code's idle-compaction notice is drawn with a bullet but is not the member's words
  ;; (seen live on a member's pane during the crew-mirror-v0 check).
  (is (nil? (pz/last-said "⏺ Compacted while idle, before the prompt cache\n  expired · turn off with \"idleCompaction\":\n  false in settings.json\n")))
  (is (= "Done." (pz/last-said "⏺ Done.\n\n⏺ Compacted while idle, before the prompt cache\n  expired\n"))))
