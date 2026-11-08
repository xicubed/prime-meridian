# Spec: crew-fielder v0, the twin fields the crew's prompts as a service; les jumeaux in data

Quest: `.crew/quests/crew-fielder-v0.md`. Follows `crew-talk-v1` (`crew/runs/crew-talk-v1-20261108-0931/spec.md`).
Contract: crew-up README, section "Answering the crew's prompts" (crew-up 0.2.4).
GDD: §1 "Les jumeaux" (already written, line 45), §2 "Talk and permission prompts" (line 81, reworded below).

## 0. Shape of the round

Four pieces, all plain data and pure functions. The only new I/O is one bb script.

1. **`pm.jumeaux`** (new, portable `.cljc`) reads `:jumeaux` from the estate (or the world: `load-estate` keeps every top-level key) into one flat **names map**: twin name, player name, sex, twin word, pair word. It never throws and falls back to defaults.
2. **No `"Hal"` in code.** Every display string that said Hal now takes the twin's name from the names map. `pm.talk/hal-name` is deleted. Functions that already receive the world (`talk/observe`, `overlay/hud-line`, `content/hal-segment`) read the names from it. Functions that don't (`observe-log`, `crew-label`, `log-label`, `hold-switch`, `overlay/source-tag`) get a new arity with a trailing `jum` argument (a names map). Their old arities stay, so existing callers and tests still compile with 0 warnings, and they use the defaults ("Your twin"). The bridge's 403 reason becomes `"risky: your twin reviews this one"`. Rule: `grep -rnw Hal src/ scripts/` prints nothing. That includes comments and docstrings, so reword them to "the twin".
3. **Log role keys.** `pm.crew-log` uses `:by` ∈ `#{:twin :player}`. Old `:hal` and `:michael` entries are **upgraded on read** to `:twin` and `:player` (in `parse`, `append`, `talk/observe-log`, `talk/log-label`, `talk/crew-label`, `talk/restore`) and are never dropped. The bridge writes `:player`.
4. **`bb crew-fielder`**: the decision is pure in `pm.fielder` (new `.cljc`). The I/O is `scripts/crew_fielder.clj`. It answers only what `pm.prompt` (via crew.edn) calls `:bash` + `:risky false`, only while hold is off, only after `:answer-after-s`, and only if `b/decide` accepts the **live** re-read. It sends `1`, logs `{:by :twin :action :allowed}`, and never declines.

**The internal `:hal` keyword is NOT renamed.** It appears as the world key `:hal` (pm.content), `:kind :hal` on talk entries (persisted in every player's saved talk history), `:voices {:hal …}` (estate data), and `:source :hal` (overlay: the twin's `state/mirrors.edn` contract, written outside this repo). There are about 80 test lines on top of that. Renaming would touch half the game and break saved histories, and the player never sees it. It stays as an internal keyword that means "the twin", like `:me` in chores. Only *display strings* and the *crew-log `:by` role* change.

Unchanged, and nobody edits them: `pm.prompt` (parser, `risky?`, two-poll `observe-prompts`), `scripts/crew_status.clj` logic, `pm.crew`, `pm.dev-http`, `public/index.html`, `.gitignore` (already ignores `/crew/fielder.log`), `public/vendor/`, `resources/content/`.

## 1. Who touches what

| Who | Files | What |
|---|---|---|
| **un-automate-monde** (builder) | `src/pm/jumeaux.cljc` (new) | §3.1 |
| | `src/pm/crew_log.cljc` | §3.2: `bys`, `legacy-bys`, `role`, `upgrade-entry`; `parse`/`append` upgrade |
| | `src/pm/bridge.cljc` | §3.3: one reason string |
| | `src/pm/fielder.cljc` (new) | §3.4 |
| | `src/pm/talk.cljs` | §3.5: delete `hal-name`; names arities; legacy upgrade; docstrings |
| | `src/pm/overlay.cljs` | §3.6: `source-tag` 3-arity, `hud-line` reads world; docstrings |
| | `src/pm/content.cljs` | §3.6: `hal-segment` reads world; `hal-events` docstring |
| | `src/pm/ui.cljs` | §3.7: pass `jum` to `crew-label` / `log-label` |
| | `src/pm/core.cljs` | §3.7: compute `(j/names @!world)` and pass it; console string; docstring |
| | `scripts/crew_fielder.clj` (new) | §3.8 |
| | `scripts/crew_bridge.clj` | `:michael` → `:player` in `append-crew-log!` |
| | `scripts/crew_status.clj` | header comment only ("like Hal's mirrors.edn" → "like the twin's mirrors.edn") |
| | `bb.edn` | new `crew-fielder` task (§2.4) |
| | `resources/estate.edn` | add `:jumeaux`; add `:answer-after-s 5` to `:crew-talk`; update its comment (§2.1) |
| **le-testeur** (tester) | `test/pm/crew_fielder_v0_test.cljs` (new, ns `pm.crew-fielder-v0-test`) | t01–t44 (§4) |
| | `test/pm/crew_talk_v1_test.cljs`, `test/pm/crew_talk_test.cljs`, `test/pm/overlay_test.cljs` | **only** the edits listed in §4.8 (old expectations that this quest changes on purpose) |
| **le-docteur** (docs) | `docs/GDD.md` | replace the "Talk and permission prompts" paragraph (§5.1) |
| | `crew/README.md` | services paragraph and the step-3 line (§5.2) |
| **Hal** (manager) | `.crew/crew.json` | add the `crew-fielder` service (§5.3) |
| | `.crew/rounds/*.json` | `"onBlocked": "wait"` on rounds run with the fielder |
| | outside the repo | retire the private driver scripts; live checks (§6) |
| **nobody** | `src/pm/prompt.cljc`, `src/pm/crew.cljs`, `src/pm/world.cljs`, `src/pm/dev_http.clj`, `public/**`, `resources/content/**`, `.gitignore`, `state/*` | — |

Builder and tester work in parallel from this spec alone. The tester's file references only the public functions in §3.

## 2. Data shapes

### 2.1 `resources/estate.edn` (top of the file, next to `:hud`)

```clojure
 :jumeaux {:player {:name "Michael" :sex :m}   ; les jumeaux: the player and their twin; onboarding asks (TASKS O-1)
           :twin   {:name "Hal"}}              ; the twin's word follows the player's :sex (:m jumeau, :f jumelle)
 :crew-talk {:surface-after-s 60   ; a prompt the twin hasn't answered after this long goes to the player
             :answer-after-s 5}    ; bb crew-fielder waits this long, then answers a routine prompt
```

A second example (tests only, never in the estate): `{:jumeaux {:player {:name "Claire" :sex :f} :twin {:name "Ada"}}}`.

### 2.2 The names map (output of `j/names`, the `jum` argument everywhere)

```clojure
{:player "Michael" :sex :m :twin "Hal" :twin-word "jumeau" :pair-word "jumeaux"}   ; Michael's estate
{:player "Claire" :sex :f :twin "Ada" :twin-word "jumelle" :pair-word "jumelles"}  ; the :f example
{:player "You" :sex nil :twin "Your twin" :twin-word "jumeau" :pair-word "jumeaux"} ; defaults (missing / bad block)
```

### 2.3 `state/crew-log.edn` entries (shape unchanged; `:by` values change)

```clojure
{:at 1794013205000 :member :le-testeur :action :allowed :by :twin :prompt-id "2b56b6f2…" :text "ls"}   ; the fielder (or the manager)
{:at 1794013209000 :member :un-automate-monde :action :allowed :by :player :prompt-id "9cc51f01…" :text "node -e 'console.log(6*7)'"} ; the bridge
;; legacy, still in Michael's live file: read as the two above
{… :by :hal …}      → :by :twin
{… :by :michael …}  → :by :player
```

### 2.4 `bb.edn`

```clojure
  crew-fielder {:doc  "The twin's automatic half: answers routine crew prompts after :answer-after-s; holds while state/hold.edn is on: bb crew-fielder [--dry-run]"
                :task (apply shell "bb" "scripts/crew_fielder.clj" *command-line-args*)}
```

### 2.5 The fielder's poll context (pure input to `pm.fielder`)

```clojure
{:crew      {:as-of ms :agents {member {:status kw :prompt {:id :kind :text :risky :since}}} :titles {…}}  ; parsed state/crew.edn, or nil
 :hold      {:on bool :at ms}         ; (cl/parse-hold …), or nil
 :members   #{:le-testeur …}           ; (set (keys (b/roster crew-json))), same roster check as the bridge
 :log       [crew-log entries]         ; (cl/parse …), or nil
 :tried     {member {:id sha1 :since ms}}   ; the fielder's own memory: one attempt per sighting
 :now       ms
 :crew-talk {:answer-after-s 5 …}}     ; the estate's :crew-talk, or nil
```

## 3. Public functions

### 3.1 `pm.jumeaux` (`src/pm/jumeaux.cljc`, requires only `clojure.string`)

```clojure
(def default-twin "Your twin")
(def default-player "You")
(def max-name 32)
```

- **`(twin-word sex)`** → `"jumelle"` when `sex` is `:f`, else `"jumeau"` (`:m`, nil, anything).
- **`(pair-word sex)`** → `"jumelles"` when `:f`, else `"jumeaux"`.
- **`(names data)`** → the names map (§2.2). `data` is any map holding `:jumeaux` (the estate, or the world). Rules:
  - `b` = `(:jumeaux data)` when `data` is a map and it is a map, else `{}`. Same for `(:player b)` and `(:twin b)`: anything that isn't a map counts as `{}`.
  - A name is **valid** when it is a string, has no control characters (`[\x00-\x1f\x7f]`), and is non-blank and at most `max-name` characters **after trimming**. The trimmed string is used. Otherwise use the default: `:twin` → `default-twin`, `:player` → `default-player`.
  - `:sex` is `(:sex player)` when it is exactly `:m` or `:f`, else `nil`.
  - `:twin-word` = `(twin-word sex)`, `:pair-word` = `(pair-word sex)`.
  - Never throws (nil, strings, numbers, vectors all give the defaults).
- **`(def defaults (names nil))`**
- **`(twin jum)`** → `(:twin jum)` when `jum` is a map and that is a non-blank string, else `default-twin`. Every display function uses this, so a nil or bad `jum` is safe.

### 3.2 `pm.crew-log` (changes)

```clojure
(def bys #{:twin :player})
(def legacy-bys {:hal :twin :michael :player})
```

- **`(role by)`** → `:twin`/`:player` for those two; `:twin` for `:hal`, `:player` for `:michael`; else `nil`.
- **`(upgrade-entry e)`** → when `e` is a map and `(role (:by e))` is non-nil, `(assoc e :by (role (:by e)))`; otherwise `e` unchanged (nil stays nil, `{:by :x}` stays as is).
- **`valid-entry?`**: unchanged, except that `:by` must be in the new `bys` (strict: a legacy key is invalid *until upgraded*).
- **`parse`**: `(->> v (map upgrade-entry) (filter valid-entry?) (mapv select-keys…) (take-last max-entries) vec)`. Legacy entries come back upgraded and are never dropped.
- **`append`**: the base is `(filter valid-entry? (map upgrade-entry log))`, and `e` is upgraded before it is validated. Everything else is unchanged.
- `entry`, `for-member`, `hold-edn`, `parse-hold`, `hold-on?`: unchanged. `entry` passes `by` through as given.

### 3.3 `pm.bridge` (change)

- `decide` `:allow` on a risky prompt → `{:ok false :status 403 :reason "risky: your twin reviews this one"}`. Nothing else changes.

### 3.4 `pm.fielder` (`src/pm/fielder.cljc`, requires `pm.bridge`, `pm.crew-log`; never required by pm.core)

```clojure
(def default-answer-after-s 5)
(def every-ms 2000)
(def stale-ms 15000)   ; must equal pm.crew/stale-ms (test t28)
```

- **`(answer-after-s crew-talk)`** → `(:answer-after-s crew-talk)` when it is a number with `0 ≤ n ≤ 3600`, else `default-answer-after-s`. Non-map → default.
- **`(verdict ctx member)`** → one keyword. The checks run in this order and the first match wins (`a` = `(get-in ctx [:crew :agents member])`, `p` = `(:prompt a)`):
  1. `:hold`: `(cl/hold-on? (:hold ctx))`
  2. `:stale`: crew isn't a map, or `(:as-of crew)` isn't a number, or `(- now as-of) > stale-ms`. Same rule as `pm.crew/fresh?`: an age of exactly 15000 is fresh.
  3. `:not-in-roster`: `member` is not in `(:members ctx)` (nil counts as empty)
  4. `:not-blocked`: `(:status a)` ≠ `:blocked`, or `p` isn't a map
  5. `:unknown`: `(:kind p)` ≠ `:bash`
  6. `:risky`: `(:risky p)` is not exactly `false`
  7. `:no-since`: `(:since p)` is not an integer
  8. `:answered`: `(get (:tried ctx) member)` = `{:id (:id p) :since (:since p)}`, **or** some entry `x` in `(:log ctx)` has `(:member x)` = member, `(:prompt-id x)` = `(:id p)`, an integer `(:at x)`, and `(:at x) ≥ (:since p)` (any `:by`, any `:action`)
  9. `:too-soon`: `(- now (:since p)) < (* 1000 (answer-after-s (:crew-talk ctx)))`
  10. `:answer`
- **`(due ctx)`** → a vector of `{:member m :prompt p}`, one for every `m` in `(sort (keys (get-in ctx [:crew :agents])))` whose verdict is `:answer`, in that order. Gives `[]` when there are none, including when crew is nil.
- **`(attempt prompt live)`** → `(b/decide {:action :allow :prompt-id (:id prompt) :text nil} live)`. `live` = `{:status kw :prompt (pr/read-prompt screen repo)}`, read right now. This is the bridge's rule set, reused: not blocked / prompt changed (409), unknown / risky (403), else `{:ok true :steps [[:keys "1"]] :reason "allowed"}`.
- **`(log-entry now member live)`** → `(cl/entry now member :allowed :twin (:prompt live))` when `(:prompt live)` is a map, else `nil`.
- **`(remember tried member prompt)`** → `(assoc (or tried {}) member {:id (:id prompt) :since (:since prompt)})`.

The fielder has no risk list of its own. "Risky" is whatever `pm.prompt/risky?` (via `prompt-of`) put in crew.edn, re-checked live by `pr/read-prompt` inside `attempt`.

### 3.5 `pm.talk` (changes; `(:require … [pm.jumeaux :as j])`)

- Delete `(def hal-name "Hal")`.
- **`observe`** (signature unchanged): the `:kind :hal` entry's `:who` is `(j/twin (j/names world))`.
- **`observe-log`**: `([talk log crew-state now] (observe-log talk log crew-state now nil))` and `([talk log crew-state now jum] …)`. Every log entry goes through `cl/upgrade-entry` first. The quiet line is written when `(= :twin (:by x))` (it was `:hal`), with `:who (j/twin jum)` and `:log` set to the **upgraded** entry. `mark-answered` stores the upgraded `:by`. Otherwise the same as v1.
- **`log-label`**: `([x off] (log-label x off nil))`, `([x off jum] …)`. who = `(case (cl/role (:by x)) :twin (j/twin jum) :player "You" "")`.
- **`crew-label`**: `([e off] (crew-label e off nil))`, `([e off jum] …)`.
  - `:pending` risky → `(str " · " (j/twin jum) " reviews this one")`.
  - `:stale` → `" · already answered"` plus `(case (cl/role (:by e)) :twin (str " by " (j/twin jum)) :player " by you" "")`.
  - All other branches are unchanged.
- **`hold-switch`**: `([hold bridge-on? ui] (hold-switch hold bridge-on? ui nil))`, `([hold bridge-on? ui jum] …)`. The hints become `(str "you answer; " (j/twin jum) " holds off")` and `(str (j/twin jum) " answers the easy ones")`. Everything else is unchanged.
- **`restore`**:
  - A `:crew-prompt` entry keeps `:by (cl/role (:by e))` when that is non-nil (so `:hal` becomes `:twin`, `:michael` becomes `:player`, and `:x` is dropped).
  - A `:crew-log` entry is valid when `(cl/valid-entry? (cl/upgrade-entry (:log e)))`, and it is kept with `:log (cl/upgrade-entry (:log e))`.
  - The saved `:who` text is kept as is.
- Docstrings and comments: "Hal" → "the twin" (and "shown to Michael" → "shown to the player").

### 3.6 `pm.overlay`, `pm.content` (changes; both `(:require [pm.jumeaux :as j])`)

- **`overlay/source-tag`**:
  - `([source] (source-tag source nil nil))`, `([source cached?] (source-tag source cached? nil))`, `([source cached? jum] …)`.
  - `:hal` → `(j/twin jum)`, or `(str (j/twin jum) ", cached")` when `cached?` is truthy.
  - Another keyword → `(name source)`. Anything else → nil.
- **`overlay/hud-line`** (signature unchanged): calls `(source-tag source (:state-cached? world) (j/names world))`.
- **`content/hal-segment`** (signature unchanged): `(str (j/twin (j/names world)) ": " t)`.
- Docstrings and comments: "Hal" → "the twin".

### 3.7 `pm.ui`, `pm.core` (wiring; not unit-tested, live check §6)

- `ui/render-panel!`: the `crew` map gains `:jumeaux jum`. `render-crew-row!` calls `(talk/crew-label entry off (:jumeaux crew))`.
- `ui/render-roster!`: becomes `[rows on-say on-expand offset-of jum]` and calls `(talk/log-label x off jum)`.
- `core/talk-tick!`: `(let [jum (j/names @!world)] …)`, then passes it to `observe-log` (5-arity), `hold-switch` (4-arity), the render-panel crew map, and `render-roster!`.
- The console line becomes `"Prime Meridian: state from your twin as of"`, and the docstring at core.cljs:193 is reworded.

### 3.8 `scripts/crew_fielder.clj` (I/O only; no decisions)

The header comment says what it does, that it never declines, never answers risky/unknown prompts, and holds while `state/hold.edn` is on. It is the twin's automatic half and replaces the private driver scripts.

```
requires: babashka.process p, cheshire.core json, clojure.edn edn, clojure.java.io io,
          pm.bridge b, pm.prompt pr, pm.crew-log cl, pm.fielder f
files: state/crew.edn, state/hold.edn, state/crew-log.edn, .crew/crew.json, resources/estate.edn, log crew/fielder.log
herdr = (or (System/getenv "HERDR") "herdr"); repo = canonical path of "."
dry-run? = (some #{"--dry-run"} args)

loop with tried = {}:
  ctx = {:crew (read-edn crew.edn) :hold (cl/parse-hold (slurp-or-nil hold)) :log (cl/parse (slurp-or-nil crew-log))
         :members (set (keys (b/roster (crew-json)))) :crew-talk (:crew-talk (read-edn estate.edn))
         :now (System/currentTimeMillis) :tried tried}
  for {:keys [member prompt]} in (f/due ctx):
    if (cl/hold-on? (cl/parse-hold (slurp-or-nil hold)))   ; re-read just before acting
      skip (no remember)
    else
      live   = live-for member     ; exactly like crew_bridge.clj: `herdr agent list` → b/agent-statuses;
                                   ; when :blocked, `herdr agent read <m> --source visible` → pr/read-prompt out repo
      d      = (f/attempt prompt live)
      tried  = (f/remember tried member prompt)
      dry-run?   → log! member "-" "dry-run: would allow" (when ok) / (:status d) (:reason d)
      (:ok d)    → run each step via (p/sh (b/step-argv herdr member step)); all exit 0 →
                     append (f/log-entry now member live) with cl/append + tmp-file rename; log! 200 "allowed"
                   any non-zero → log! 502 "herdr failed" (no crew-log entry)
      else       → log! (:status d) (:reason d)
  sleep f/every-ms; recur with tried
```

- `log!` writes `(b/log-line (str (Instant/now)) member :allow status reason)` plus a newline to `crew/fielder.log`. It never logs screens or tokens.
- On start it prints and logs `bb crew-fielder: answering routine prompts every 2 s (--dry-run: nothing sent)`.
- Each poll is wrapped in try/catch: an exception is logged and the loop continues.
- `read-edn` = `(try (edn/read-string (slurp f)) (catch Exception _ nil))` (bb reads the estate's hex literals; checked).
- Herdr is always called with an argument vector (`p/sh` on a vector) and never through a shell.
- `--dry-run` sends nothing and writes no crew-log, but still remembers, so each sighting is logged once.

## 4. Test cases (`test/pm/crew_fielder_v0_test.cljs`)

ns requires: `cljs.test`, `clojure.string str`, `["fs" :as fs]`, `pm.jumeaux j`, `pm.crew-log cl`, `pm.bridge b`, `pm.prompt pr`, `pm.crew crew`, `pm.fielder f`, `pm.talk tk`, `pm.overlay o`, `pm.content c`, `pm.world w`, `pm.fixtures fx`.

Shared values (ids checked with `shasum` on `"bash\n" + text`):

```clojure
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
```

### 4.1 pm.jumeaux

**t01: Michael's sample.** `(= NM (j/names JM))`.

**t02: the `:f` / Ada example, same code path.** `(= NA (j/names JA))`.

**t03: missing block → defaults, never an error.** Each of these `(= ND (j/names x))`: `nil`, `{}`, `"x"`, `42`, `[]`, `{:jumeaux nil}`, `{:jumeaux "Hal"}`, `{:jumeaux {}}`, `{:jumeaux {:player "Michael" :twin "Hal"}}` (player and twin not maps). Also `(= ND j/defaults)`.

**t04: bad `:sex` falls back to jumeau / jumeaux, names kept.** For `s` in `[:x "f" :F nil 1]`, `(j/names (assoc-in JA [:jumeaux :player :sex] s))` = `{:player "Claire" :sex nil :twin "Ada" :twin-word "jumeau" :pair-word "jumeaux"}`. The same holds with `:sex` missing: `(update-in JA [:jumeaux :player] dissoc :sex)`.

**t05: name cleaning.** With `(defn tw [n] (:twin (j/names (assoc-in JA [:jumeaux :twin :name] n))))`:
- `(tw "  Ada  ")` → `"Ada"`
- `(tw "")`, `(tw "   ")`, `(tw nil)`, `(tw :Ada)`, `(tw "A\tda")`, `(tw (apply str (repeat 33 "a")))` → `"Your twin"`
- `(tw (apply str (repeat 32 "a")))` → 32 × `"a"`; `(tw (str " " (apply str (repeat 32 "a")) " "))` → 32 × `"a"`
- Player: `(:player (j/names (assoc-in JA [:jumeaux :player :name] "")))` → `"You"`; `(:twin (j/names (assoc-in JA [:jumeaux :player] nil)))` → `"Ada"` (twin kept); and `(:sex …)` of that → `nil`.

**t06: words and `twin`.** `(j/twin-word :m)` `"jumeau"`, `(j/twin-word :f)` `"jumelle"`, `(j/twin-word nil)` `"jumeau"`, `(j/twin-word :x)` `"jumeau"`; `(j/pair-word :m)` `"jumeaux"`, `(j/pair-word :f)` `"jumelles"`, `(j/pair-word nil)` `"jumeaux"`. `(j/twin NA)` `"Ada"`, `(j/twin NM)` `"Hal"`, `(j/twin nil)` `"Your twin"`, `(j/twin {:twin ""})` `"Your twin"`, `(j/twin {:twin 5})` `"Your twin"`, `(j/twin "Ada")` `"Your twin"`.

**t07: the shipped estate.** `(:jumeaux (w/load-estate w/estate-edn))` = `{:player {:name "Michael" :sex :m} :twin {:name "Hal"}}`, `(= NM (j/names (w/load-estate w/estate-edn)))`, and `(= NM (j/names W))` (a world still holds it).

### 4.2 pm.crew-log

**t08: `role`.** `:twin`→`:twin`, `:player`→`:player`, `:hal`→`:twin`, `:michael`→`:player`; `:manager`, `:x`, `"twin"`, `nil` → `nil`.

**t09: `upgrade-entry`.** `(= L1 (cl/upgrade-entry L1old))`, `(= L3 (cl/upgrade-entry L3old))`, `(= L1 (cl/upgrade-entry L1))`, `(= (assoc L1 :by :x) (cl/upgrade-entry (assoc L1 :by :x)))`, `(nil? (cl/upgrade-entry nil))`, `(= {:a 1} (cl/upgrade-entry {:a 1}))`.

**t10: `valid-entry?` is strict on the new keys.** True for `L1`, `L2`, `L3`. False for `L1old`, `L3old`, `(assoc L1 :by :manager)`, `(assoc L1 :by nil)`. `(= #{:twin :player} cl/bys)`, `(= {:hal :twin :michael :player} cl/legacy-bys)`.

**t11: `parse` reads old entries as new, never drops them.**
- `(= [L1 L2 L3] (cl/parse (pr-str [L1old {:bad 1} L2old L3old])))`
- `(= [L1 L3] (cl/parse (pr-str [L1 L3old])))`
- `(= [] (cl/parse (pr-str [(assoc L1 :by :x)])))`
- `(= 200 (count (cl/parse (pr-str (mapv #(assoc L1old :at (+ A %)) (range 205))))))`, and `(:by (first …))` = `:twin`.

**t12: `append` upgrades.** `(= [L1 L2] (cl/append [L1old] L2))`, `(= [L1 L3] (cl/append [L1] L3old))`, `(= [L1] (cl/append [L1] (assoc L2 :by :x)))`, `(= [L1] (cl/append nil L1old))`.

**t13: `entry` with roles.** `(= L1 (cl/entry (+ A 5000) :le-testeur :allowed :twin {:id ID3 :kind :bash :text "ls" :risky false}))` and `(= L3 (cl/entry (+ A 9000) :un-automate-monde :allowed :player {:id ID1 :text "node -e 'console.log(6*7)'"}))`.

### 4.3 pm.bridge

**t14: the 403 reason names no one.**
- `(= {:ok false :status 403 :reason "risky: your twin reviews this one"} (b/decide {:ok true :member :le-testeur :action :allow :prompt-id ID2 :text nil} {:status :blocked :prompt (dissoc QR :since)}))`
- `(= {:ok true :steps [[:keys "1"]] :reason "allowed"} (b/decide {:action :allow :prompt-id ID3 :text nil} {:status :blocked :prompt (dissoc QB :since)}))`

### 4.4 pm.talk: names from data

**t15: `observe` takes the twin's name from the world.** `WA` = `(assoc W :jumeaux (:jumeaux JA) :hal {:event :jack-in :text "Welcome back." :until (+ A 6000)})`.
- `(first (:entries (tk/observe T0 WA A)))` = `{:n 1 :who "Ada" :text "Welcome back." :at A :kind :hal :event :jack-in :show-at A :show-until (+ A 2500)}`
- `(:who (first (:entries (tk/observe T0 (assoc WA :jumeaux (:jumeaux JM)) A))))` → `"Hal"`
- `(:who (first (:entries (tk/observe T0 (dissoc WA :jumeaux) A))))` → `"Your twin"`

**t16: `observe-log`'s quiet line uses the names.**
- `(:entries (tk/observe-log T0 [L1] C (+ A 6000) NA))` = `[{:n 1 :who "Ada" :text "allowed `ls` for Le Testeur" :at (+ A 5000) :kind :crew-log :member :le-testeur :log L1}]`
- `(:log-at …)` = `(+ A 5000)`
- `(tk/crew-label (e (tk/observe-log T0 [L1] C (+ A 6000) NA) 1) OFF)` → `"Ada allowed `ls` for Le Testeur · 5:00 PM"`
- `(:who (e (tk/observe-log T0 [L1] C (+ A 6000) NM) 1))` → `"Hal"`
- `(:who (e (tk/observe-log T0 [L1] C (+ A 6000)) 1))` → `"Your twin"` (4-arity)

**t17: `observe-log` reads legacy entries.**
- `(= (tk/observe-log T0 [L1] C (+ A 6000) NA) (tk/observe-log T0 [L1old] C (+ A 6000) NA))`. In particular `(:log (e … 1))` = `L1` (the upgraded entry).
- `(tk/observe-log T0 [L3old] C (+ A 10000) NA)`: `:entries` `[]` (the player's own answer adds no line), `:log-at` `(+ A 9000)`.

**t18: pending risky / unknown lines name the twin.**
- `Y` = `(tk/observe-crew T0 (crew-at A {:un-automate-monde {:status :blocked :prompt QR}}) A HOLD)`
- `U` = `(tk/observe-crew T0 (crew-at A {:le-testeur {:status :blocked :prompt QU}}) A HOLD)`
- `Z` = `(tk/observe-crew T0 (crew-at A {:le-testeur {:status :blocked :prompt QB}}) A HOLD)`

Expected:
- `(tk/crew-label (e Y 1) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · Ada reviews this one"`
- `(tk/crew-label (e Y 1) OFF NM)` → `"⚠ Un Automate (monde) · wants to run curl --version · Hal reviews this one"`
- `(tk/crew-label (e Y 1) OFF)` → `"⚠ Un Automate (monde) · wants to run curl --version · Your twin reviews this one"`
- `(tk/crew-label (e U 1) OFF NA)` → `"⚠ Le Testeur · is waiting at a prompt: Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs? · Ada reviews this one"`
- `(tk/crew-label (e Z 1) OFF NA)` → `"Le Testeur · wants to run ls"` (benign: no suffix)

**t19: "already answered by …", from new and legacy log entries.** `r` = `(tk/observe-log Y [L2] C (+ A 8000) NA)`.
- `(:state (e r 1))` `:stale`; `(:by (e r 1))` `:twin`
- `(tk/crew-label (e r 1) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by Ada"`
- `(tk/crew-label (e r 1) OFF)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by Your twin"`
- `(e r 2)` = `{:n 2 :who "Ada" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000) :kind :crew-log :member :un-automate-monde :log L2}`
- `(= r (tk/observe-log Y [L2old] C (+ A 8000) NA))`
- `rp` = `(tk/observe-log Y [(assoc L2 :by :player)] C (+ A 8000) NA)`: `(:by (e rp 1))` `:player`, `(count (:entries rp))` `1`, `(tk/crew-label (e rp 1) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by you"`. Also `(= rp (tk/observe-log Y [(assoc L2 :by :michael)] C (+ A 8000) NA))`.
- On an entry that still carries a legacy key:
  - `(tk/crew-label (assoc (e r 1) :by :hal) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by Ada"`
  - `(tk/crew-label (assoc (e r 1) :by :michael) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by you"`
  - `(tk/crew-label (assoc (e r 1) :by :x) OFF NA)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered"`

**t20: `log-label`.**
- `(tk/log-label L1 OFF NA)` → `"Ada allowed `ls` · 5:00 PM"`; `(tk/log-label L1old OFF NA)` → the same
- `(tk/log-label L3 OFF NA)` → `"You allowed `node -e 'console.log(6*7)'` · 5:00 PM"`; `(tk/log-label L3old OFF NA)` → the same
- `(tk/log-label L1 OFF)` → `"Your twin allowed `ls` · 5:00 PM"`
- `(tk/log-label (assoc L2 :at (+ A 60000)) OFF NM)` → `"Hal declined `curl --version` · 5:01 PM"`

**t21: the switch's hints.**
- `(tk/hold-switch {:on true :at 5} true {} NA)` = `{:checked? true :disabled? false :hint "you answer; Ada holds off"}`
- `(tk/hold-switch nil true nil NA)` = `{:checked? false :disabled? false :hint "Ada answers the easy ones"}`
- `(tk/hold-switch {:on true :at 5} true {})` → hint `"you answer; Your twin holds off"`; `(tk/hold-switch nil true nil)` → hint `"Your twin answers the easy ones"`
- `(tk/hold-switch {:on true :at 5} false {} NA)` = `{:checked? true :disabled? true :hint "bridge off"}`

**t22: `restore` upgrades a saved history.** The saved text:

```clojure
(def SAVED-MAP {:v 1 :n 2 :seen 2 :log-at (+ A 7000)
  :entries [{:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
             :member :un-automate-monde :prompt QR :state :stale :by :hal}
            {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
             :kind :crew-log :member :un-automate-monde :log L2old}]})
(def R1 {:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
         :member :un-automate-monde :prompt QR :state :stale :by :twin})
(def R2 {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000)
         :kind :crew-log :member :un-automate-monde :log L2})
```

- `(:entries (tk/restore (pr-str SAVED-MAP)))` = `[R1 R2]` (the saved `:who` text is kept, the `:by` keys are upgraded)
- `(:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 0 :by] :michael))))` = `[(assoc R1 :by :player) R2]`
- `(:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 0 :by] :x))))` = `[(dissoc R1 :by) R2]`
- `(:entries (tk/restore (pr-str (assoc-in SAVED-MAP [:entries 1 :log :by] :x))))` = `[R1]` (an unreadable log entry is still dropped, as in v1)

### 4.5 pm.overlay / pm.content

**t23: `source-tag`.**
- `(o/source-tag :hal false NA)` `"Ada"`
- `(o/source-tag :hal true NA)` `"Ada, cached"`
- `(o/source-tag :hal nil NM)` `"Hal"`
- `(o/source-tag :sample true NA)` `"sample"`
- `(o/source-tag :phone false NA)` `"phone"`
- `(o/source-tag nil true NA)` `nil`
- `(o/source-tag "hal" false NA)` `nil`
- `(o/source-tag :hal)` `"Your twin"`
- `(o/source-tag :hal true)` `"Your twin, cached"`

**t24: `hud-line` reads the world, for both mirrors.** `W2` = `(o/apply-file W {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}})`, `WJ` = `(o/apply-file W {:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed A}}}})`.
- `(o/hud-line (assoc W2 :jumeaux (:jumeaux JA)) :flora (+ A 1000))` → `"Flora (Ada) · stock 37"`
- `(o/hud-line W2 :flora (+ A 1000))` → `"Flora (Hal) · stock 37"`
- `(o/hud-line (dissoc W2 :jumeaux) :flora (+ A 1000))` → `"Flora (Your twin) · stock 37"`
- `(o/hud-line (assoc WJ :jumeaux (:jumeaux JA)) :juniper (+ A 1000))` → `"Juniper (Ada) · bales 3 · Bales left: 3"`
- `(o/hud-line (assoc WJ :jumeaux (:jumeaux JA)) :flora A)` → `"Flora (sample) · stock 15 · I'm hungry"`

**t25: `hal-segment` reads the world.** `S` = `(assoc W :hal {:event :jack-in :text "hello" :until (+ A 6000)})`.
- `(c/hal-segment (assoc S :jumeaux (:jumeaux JA)) (+ A 1000))` → `"Ada: hello"`
- `(c/hal-segment S (+ A 1000))` → `"Hal: hello"`
- `(c/hal-segment (dissoc S :jumeaux) (+ A 1000))` → `"Your twin: hello"`
- `(c/hal-segment (assoc S :jumeaux (:jumeaux JA)) (+ A 6000))` → `nil`

### 4.6 No `"Hal"` in code

**t26.**

```clojure
(defn code-files [] (vec (for [dir ["src/pm" "scripts"] f (.readdirSync fs dir) :when (re-find #"\.clj[cs]?$" f)] (str dir "/" f))))
(defn hal-hits [] (vec (for [p (code-files) [i l] (map-indexed vector (str/split-lines (.readFileSync fs p "utf8")))
                              :when (re-find #"\bHal\b" l)] (str p ":" (inc i)))))
```

- `(= [] (hal-hits))`
- Not vacuous: `(code-files)` contains `"src/pm/talk.cljs"`, `"src/pm/bridge.cljc"`, `"src/pm/jumeaux.cljc"`, `"src/pm/fielder.cljc"`, `"scripts/crew_fielder.clj"`, `"scripts/crew_bridge.clj"`
- `(nil? (re-find #"\bHal\b" "(def x :hal)"))`, `(some? (re-find #"\bHal\b" "Hal's"))`

### 4.7 pm.fielder: the pure decision

**t27: `answer-after-s`.**
- `nil` → 5, `{}` → 5, `{:surface-after-s 60}` → 5
- `{:answer-after-s 10}` → 10, `{:answer-after-s 0}` → 0, `{:answer-after-s 3600}` → 3600, `{:answer-after-s 2.5}` → 2.5
- `{:answer-after-s 3601}` → 5, `{:answer-after-s -1}` → 5, `{:answer-after-s "7"}` → 5

**t28: constants and the estate.**
- `(= 5 f/default-answer-after-s)`, `(= 2000 f/every-ms)`, `(= f/stale-ms crew/stale-ms 15000)`
- `(:crew-talk (w/load-estate w/estate-edn))` = `{:surface-after-s 60 :answer-after-s 5}`
- `(f/answer-after-s (:crew-talk (w/load-estate w/estate-edn)))` → 5
- `(:surface-after-s (tk/policy (:crew-talk (w/load-estate w/estate-edn)) nil))` → 60

**t29: benign → answer after the delay (delay from data).**
- `(v CTX)` → `:answer` (5000 ≥ 5000)
- `(v (assoc CTX :now (+ A 4999)))` → `:too-soon`
- `(v (assoc CTX :crew-talk nil))` → `:answer` (default 5)
- `(v (assoc CTX :crew-talk {:answer-after-s 10}))` → `:too-soon`; with `:now (+ A 10000)` → `:answer`
- `(v (assoc CTX :crew-talk {:answer-after-s 0} :now (+ A 2000)))` → `:answer`

**t30: hold on → no.**
- `(v (assoc CTX :hold {:on true :at A}))` → `:hold`
- `(v (assoc CTX :hold {:on false :at A}))` → `:answer`

**t31: stale crew.edn → no.**
- `(v (assoc CTX :now (+ A 17001)))` → `:stale`
- `(v (assoc CTX :now (+ A 17000)))` → `:answer`
- `(v (assoc CTX :crew nil))` → `:stale`
- `(v (assoc CTX :crew (dissoc CS :as-of)))` → `:stale`

**t32: not in the roster → no.**
- `(v (assoc CTX :members #{:un-automate-monde}))`, `(v (assoc CTX :members #{}))`, `(v (assoc CTX :members nil))` → `:not-in-roster`

**t33: not blocked → no.**
- `(v (assoc CTX :crew (crew-at (+ A 2000) {:le-testeur {:status :working}})))` → `:not-blocked`
- `(v (assoc CTX :crew (crew-at (+ A 2000) {:le-testeur {:status :blocked}})))` → `:not-blocked`
- `(f/verdict CTX :un-automate-monde)` → `:not-blocked` (in the roster, absent from agents)

**t34: unknown → no.** `(v (with-prompt QU))` → `:unknown`; `(v (with-prompt (assoc QB :kind :unknown)))` → `:unknown`.

**t35: risky → no.** `(v (with-prompt QR))` → `:risky`; `(v (with-prompt (assoc QB :risky nil)))` → `:risky`; `(v (with-prompt (assoc QB :risky "false")))` → `:risky`.

**t36: no `:since` → no.** `(v (with-prompt (dissoc QB :since)))` → `:no-since`; `(v (with-prompt (assoc QB :since 1.5)))` → `:no-since`.

**t37: already answered by the fielder (memory) vs a changed id or a new sighting.**
- `(v (assoc CTX :tried {:le-testeur {:id ID3 :since A}}))` → `:answered`
- `(v (assoc CTX :tried {:le-testeur {:id ID3 :since (- A 60000)}}))` → `:answer` (the same command, seen earlier)
- `(v (assoc CTX :tried {:le-testeur {:id ID1 :since A}}))` → `:answer` (changed id)
- `(v (assoc CTX :tried {:un-automate-monde {:id ID3 :since A}}))` → `:answer`

**t38: already answered per the log (anyone).** `LP` = `{:at (+ A 3000) :member :le-testeur :action :allowed :by :player :prompt-id ID3 :text "ls"}`.
- `(v (assoc CTX :log [LP]))` → `:answered`
- `(v (assoc CTX :log [(assoc LP :at A)]))` → `:answered` (at = since)
- `(v (assoc CTX :log [(assoc LP :action :declined :by :twin)]))` → `:answered`
- `(v (assoc CTX :log [(assoc LP :at (dec A))]))` → `:answer` (an earlier ask)
- `(v (assoc CTX :log [(assoc LP :member :un-automate-monde)]))` → `:answer`
- `(v (assoc CTX :log [(assoc LP :prompt-id ID1)]))` → `:answer`

**t39: check order.**
- `(v (assoc (with-prompt QR) :hold {:on true :at A}))` → `:hold`
- `(v (assoc CTX :hold {:on true :at A} :crew nil))` → `:hold`
- `(v (assoc CTX :crew nil :members #{}))` → `:stale`
- `(v (assoc (with-prompt QU) :members #{}))` → `:not-in-roster`
- `(v (with-prompt (dissoc QU :since)))` → `:unknown`
- `(v (with-prompt (dissoc QR :since)))` → `:risky`
- `(v (assoc CTX :now (+ A 1000) :tried {:le-testeur {:id ID3 :since A}}))` → `:answered`

**t40: `due`.**
- `CS3` = `(crew-at (+ A 2000) {:le-testeur {:status :blocked :prompt QB} :un-automate-monde {:status :blocked :prompt QB2} :un-architecte {:status :blocked :prompt QR} :le-critique {:status :blocked :prompt QB}})`
- `(f/due (assoc CTX :crew CS3))` = `[{:member :le-testeur :prompt QB}]`. QB2 is too soon (4000 < 5000), un-architecte is risky, and le-critique is not in MEM.
- `(f/due (assoc CTX :crew CS3 :now (+ A 6000)))` = `[{:member :le-testeur :prompt QB} {:member :un-automate-monde :prompt QB2}]` (sorted by name)
- `(f/due (assoc CTX :crew CS3 :hold {:on true :at A}))` = `[]`
- `(f/due (assoc CTX :crew nil))` = `[]`

**t41: `attempt` is the bridge's `decide`.**
- `(f/attempt QB {:status :blocked :prompt (dissoc QB :since)})` = `{:ok true :steps [[:keys "1"]] :reason "allowed"}`
- `(f/attempt QB {:status :blocked :prompt (dissoc QB2 :since)})` = `{:ok false :status 409 :reason "prompt changed"}`
- `(f/attempt QB {:status :blocked :prompt nil})` = `{:ok false :status 409 :reason "prompt changed"}`
- `(f/attempt QB {:status :working :prompt nil})` = `{:ok false :status 409 :reason "not blocked"}`
- `(f/attempt QR {:status :blocked :prompt (dissoc QR :since)})` = `{:ok false :status 403 :reason "risky: your twin reviews this one"}`
- `(f/attempt QU {:status :blocked :prompt (dissoc QU :since)})` = `{:ok false :status 403 :reason "unknown prompt"}`
- Never declines: for every result above, `(:steps r)` is nil or `[[:keys "1"]]`.

**t42: `attempt` on real screens. The live classifier is `pm.prompt`, so even a crew.edn that claims a prompt is benign can't make the fielder answer a risky one.**
- `(pr/read-prompt LS-SCREEN R)` = `{:id ID3 :kind :bash :text "ls" :risky false}`; `(f/attempt QB {:status :blocked :prompt (pr/read-prompt LS-SCREEN R)})` → `:ok true`
- `(pr/read-prompt CURL-SCREEN R)` = `{:id ID2 :kind :bash :text "curl --version" :risky true}`; `(f/attempt (assoc QR :risky false) {:status :blocked :prompt (pr/read-prompt CURL-SCREEN R)})` = `{:ok false :status 403 :reason "risky: your twin reviews this one"}`
- `(f/attempt {:id IDF1 :kind :bash :text T1X :risky false :since A} {:status :blocked :prompt (pr/read-prompt F1 R)})` → `:ok true`
- `(f/attempt QB {:status :blocked :prompt (pr/read-prompt THINK R)})` = `{:ok false :status 409 :reason "prompt changed"}` (a thinking screen reads as nil)

**t43: `log-entry` and `remember`.**
- `(f/log-entry (+ A 6000) :le-testeur {:status :blocked :prompt (dissoc QB :since)})` = `{:at (+ A 6000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"}`, and `cl/valid-entry?` of it is true
- `(f/log-entry (+ A 6000) :le-testeur {:status :blocked :prompt nil})` → `nil`
- `(f/remember {} :le-testeur QB)` = `{:le-testeur {:id ID3 :since A}}`
- `(f/remember nil :le-testeur QB)` → the same
- `(f/remember {:le-testeur {:id ID1 :since 1} :un-architecte {:id ID2 :since 2}} :le-testeur QB)` = `{:le-testeur {:id ID3 :since A} :un-architecte {:id ID2 :since 2}}`

**t44: answered once, not twice; the same command asked again later is answered again.**
- Poll 1: `(f/due CTX)` = `[{:member :le-testeur :prompt QB}]`
- `live` = `{:status :blocked :prompt (dissoc QB :since)}`, `tried1` = `(f/remember {} :le-testeur QB)`, `log1` = `(cl/append [] (f/log-entry (+ A 5000) :le-testeur live))`, so `log1` = `[{:at (+ A 5000) :member :le-testeur :action :allowed :by :twin :prompt-id ID3 :text "ls"}]`
- Poll 2 (crew.edn still lags): `(v (assoc CTX :now (+ A 7000) :tried tried1 :log log1))` → `:answered`, and `(f/due …)` → `[]`
- The log alone, after a restart with empty memory: `(v (assoc CTX :now (+ A 7000) :tried {} :log log1))` → `:answered`
- A new sighting of the same command: `CTX3` = `(assoc CTX :crew (crew-at (+ A 22000) {:le-testeur {:status :blocked :prompt (assoc QB :since (+ A 20000))}}) :tried tried1 :log log1 :now (+ A 25000))`. `(v CTX3)` → `:answer`; `(v (assoc CTX3 :now (+ A 24999)))` → `:too-soon`
- `(cl/valid-entry? (first log1))` true; `(:by (first (cl/parse (pr-str log1))))` `:twin`

### 4.8 Edits to old tests (tester; nothing else in those files)

These expectations change on purpose: the `:by` keys, the bridge reason, and "Hal" now coming from data. In each file, add `[pm.jumeaux :as j]` to the ns and define `(def JH (j/names {:jumeaux {:player {:name "Michael" :sex :m} :twin {:name "Hal"}}}))`.

`test/pm/crew_talk_v1_test.cljs` (line numbers as of `093978f`):
- 56, 57: `:by :hal` → `:by :twin`; 58: `:by :michael` → `:by :player`
- 221: `(cl/entry … :hal …)` → `:twin`
- 385–387: `G1`, `G2`, `M`: add `JH` as the 5th argument to `tk/observe-log`
- 397, 407, 423, 429: `(tk/crew-label … OFF)` → `(tk/crew-label … OFF JH)` (expected strings unchanged)
- 419, 434, 455, 467: `(= :hal (:by …))` → `(= :twin (:by …))`; 440: `(= :michael …)` → `(= :player …)`
- 502, 503, 504: `(tk/log-label … OFF)` → `(tk/log-label … OFF JH)`
- 507, 508: `(tk/hold-switch … )` → add `JH` as the 4th argument

`test/pm/crew_talk_test.cljs`:
- 319: `"risky: Hal reviews this one"` → `"risky: your twin reviews this one"` (line 425's `c/outcome` pass-through stays)
- 535, 536–537: `(tk/crew-label … OFF)` → `(tk/crew-label … OFF JH)`

`test/pm/overlay_test.cljs`:
- 47: `(o/source-tag :hal)` → `"Your twin"`
- 218: `(o/source-tag :hal true JH)` → `"Hal, cached"`; 219: `(o/source-tag :hal false JH)` → `"Hal"`; 220: `(o/source-tag :hal nil JH)` → `"Hal"`

Everything else in the old files must pass unchanged. That includes every `hud-line`, `hal-segment` and `observe` test expecting "Hal": those worlds come from the estate, which now holds `:jumeaux`.

## 5. Docs (le-docteur) and config (Hal)

### 5.1 `docs/GDD.md`: replace the "Talk and permission prompts" paragraph (§2, line 81) with

> **Talk and permission prompts.** Your twin (Hal, for the author) fields the crew's permission prompts by default. Its automatic half, the fielder (`bb crew-fielder`, a crew-up service), answers routine ones after a short wait (5 s, `:answer-after-s` in `:crew-talk`): a plain shell command that the game's one risk classifier calls safe, and that is still on screen when the fielder looks again. 💭 shows these as quiet log lines under your twin's name. You see prompts that are risky, unreadable, or overdue (unanswered after 60 s), flagged with a red badge; the fielder never answers those, and never declines anything. Flipping the "I'll answer" switch in 💭 hands every prompt to you, and your twin holds off. The answer log records roles, not names (`:by :twin`, `:by :player`), and the names come from `:jumeaux`, so the same game works for any player and any twin.

The §1 "Les jumeaux" bullet (line 45) already says what the quest decided. Leave it as is.

### 5.2 `crew/README.md`

- Services paragraph (around line 126): after "…`bb crew-bridge`, which carries your 💭 allow / no / reply to Herdr (127.0.0.1 only)," add: "and `bb crew-fielder`, the twin's automatic half, which answers routine prompts after 5 s (never risky or unreadable ones, and nothing while 💭's "I'll answer" is on) and logs to `crew/fielder.log`,".
- Step 3 heading line: "(Hal normally does this, and answers the members' permission prompts)" → "(Hal normally does this; with `bb crew-fielder` running, rounds use `"onBlocked": "wait"` and the fielder answers the routine prompts)".
- The round table row is added after the round, as usual.
- Note for Hal, outside this repo: crew-up's README lists `:manager`, `:fielder`, `:player` as example `:by` roles. This game writes `:twin` and `:player`, per the quest.

### 5.3 `.crew/crew.json` (Hal)

```json
{ "name": "crew-fielder", "cmd": "bb crew-fielder", "note": "the twin's automatic half: answers routine crew prompts after :answer-after-s (never risky/unknown, never declines); holds while state/hold.edn is on" }
```

## 6. Live checks (Hal, after the gate)

1. `bb crew-fielder --dry-run` during a round: `crew/fielder.log` shows `… allow - dry-run: would allow` once per routine prompt, about 5 s after its `:since`, and nothing for risky or unknown ones.
2. A round with `"onBlocked": "wait"` and the fielder live: it answers the routine prompts, Hal answers none, and 💭 shows "Hal allowed `…` for …", with the name read from `:jumeaux`. A temporary edit of the twin's name in `estate.edn` to "Ada" shows "Ada allowed …" after a reload. Revert it.
3. A staged risky prompt (e.g. `curl --version`) waits: no fielder line, and after 60 s at most it surfaces to Michael with "⚠ … · Hal reviews this one".
4. With "I'll answer" on, the fielder answers nothing (the log stays quiet), and routine prompts surface at once.
5. Michael's live `state/crew-log.edn` (old `:hal`/`:michael` entries): the roster's expanded rows still show them as "Hal allowed …" / "You allowed …".
6. `grep -rnw Hal src/ scripts/` prints nothing.
