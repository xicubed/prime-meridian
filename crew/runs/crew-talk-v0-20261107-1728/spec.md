# Spec: crew-talk v0 — talk with the crew in the 💭 bubble

Quest: `.crew/quests/crew-talk-v0.md`. Follows `crew-dots-v0` (`pm.crew`, `bb crew-status`).
Architect: un-architecte. Builder: un-automate-monde. Tester: le-testeur.

## 0. Shape of the round

```
herdr ──list/read──▶ bb crew-status ──▶ public/state/crew.edn ──poll 2 s──▶ game (pm.crew/parse)
                         │  pm.prompt (parse screen, risky, id)                │ pm.talk/observe-crew → 💭 lines
                         │  pm.bridge (roster, statuses)                       │ tick / ✕ / reply / say
herdr ◀─send-keys/prompt─ bb crew-bridge (127.0.0.1:8091) ◀──POST /crew/<m>────┘ pm.crew/request-body, outcome
                         │  pm.bridge (validate, decide) + pm.prompt (re-reads the screen NOW)
                         └─▶ public/state/bridge.edn {:port :token :as-of}  ──poll 5 s──▶ game (bridge on/off)
```

- **Pure logic in two new `.cljc` namespaces** that bb and the cljs tests both load:
  `pm.prompt` (screen → prompt, risky, prompt id) and `pm.bridge` (roster, Herdr statuses,
  request validation, decision, replies, argv, log line). **The browser app never requires
  them** (the cljs branch of `pm.prompt` uses Node's `crypto`). Only the bb scripts and the
  `:test` build load them.
- The game side stays in `pm.crew` (file parsing, bridge file, request body, outcome) and
  `pm.talk` (crew lines). Both stay pure; `now` is always an argument.
- **The bridge decides.** It re-reads the member's state and screen at request time and
  recomputes the prompt, its id and whether it's risky with `pm.prompt`. Whatever the game
  sends (or `crew.edn` says) is only a claim.
- Never send keys or prompts to any member while building or testing, and don't start the
  bridge against the live crew. Hal tests it live.

## 1. Files and owners

| Who | May touch |
|---|---|
| **Builder** (un-automate-monde) | new `src/pm/prompt.cljc`, new `src/pm/bridge.cljc`, new `scripts/crew_bridge.clj`, `scripts/crew_status.clj`, `bb.edn`, `.crew/crew.json` (only the `"services"` array), `src/pm/crew.cljs`, `src/pm/talk.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs`, `public/index.html` |
| **Tester** (le-testeur) | new `test/pm/crew_talk_test.cljs` (ns `pm.crew-talk-test`), and nothing else |
| Nobody this round | `shadow-cljs.edn`, `.gitignore` (Hal already made dev-http localhost-only and ignored `crew/bridge.log`), `test/pm/fixtures.cljs`, existing tests, `public/vendor/`, `resources/content/`, the GDD |

The existing tests must keep passing. In particular, `pm.talk-test` t22 asserts
`(= (tk/restore (tk/save-text T0)) T0)` with `T0 = tk/empty-talk`, and `pm.crew-test` t01–t07
assert exact `parse` results for inputs without `:titles` or `:prompt`.

## 2. Data shapes (EDN)

### 2.1 `public/state/crew.edn` (written by `bb crew-status`)

```clojure
{:as-of 1794013200000
 :agents {:un-automate-monde {:status :blocked
                              :prompt {:id "9cc51f012b482b9cf891d1e2c19ed928fbf3d1f6"
                                       :kind :bash
                                       :text "node -e 'console.log(6*7)'"
                                       :risky false}}
          :le-testeur        {:status :done}
          :un-architecte     {:status :working}}
 :titles {:un-architecte "Un Architecte" :un-automate-monde "Un Automate (monde)"
          :le-testeur "Le Testeur" :le-coureur "Le Coureur" :le-critique "Le Critique"}}
```

- `:prompt` appears only on a `:blocked` agent that is in the roster and whose screen could be read.
  It holds exactly `:id` (40 lowercase hex), `:kind` (`:bash` or `:unknown`), `:text` (at most 400
  chars) and `:risky` (boolean). Nothing else from screens goes into the file.
- `:titles` is the roster (§3.2): crew.json members that are enabled, not `external`, and have no
  `machine`. Names and titles only.

### 2.2 `public/state/bridge.edn` (written by `bb crew-bridge`)

```clojure
{:port 8091 :token "0a1b0a1b…(64 lowercase hex)" :as-of 1794013200000}
```

The bridge writes it at start, rewrites `:as-of` every 5 s (same token), and deletes it on a
clean shutdown. The game counts the bridge as **on** when this file parses and is ≤ 15 s old.

### 2.3 Parsed screen and prompt (`pm.prompt`)

```clojure
;; parse-screen
{:kind :bash :lines ["node -e 'console.log(6*7)'"] :full "node -e 'console.log(6*7)'"}
{:kind :unknown :full "Edit file\n…last 6 non-blank lines…"}
;; prompt-of: what goes in crew.edn and what the bridge compares
{:id "9cc51f01…" :kind :bash :text "node -e 'console.log(6*7)'" :risky false}
```

### 2.4 Bridge request and decision (`pm.bridge`)

```clojure
;; req, built by crew_bridge.clj from the http-kit request; :body = decoded JSON (string keys) or :bad-json
{:method "POST" :uri "/crew/le-testeur" :origin "http://localhost:8080"
 :body {"token" "0a1b…" "action" "deny" "prompt-id" "9cc5…" "text" "use bb instead"}}
;; ctx
{:token "0a1b…" :members #{:un-architecte :un-automate-monde :le-testeur :le-coureur :le-critique}}
;; validate → ok
{:ok true :member :le-testeur :action :deny :prompt-id "9cc5…" :text "use bb instead"}
;; any refusal (validate or decide)
{:ok false :status 401 :reason "bad token"}
;; live, read by the bridge at request time
{:status :blocked :prompt {:id "9cc5…" :kind :bash :text "…" :risky false}}
;; decide → ok
{:ok true :steps [[:keys "esc"] [:wait-idle 5000] [:prompt "use bb instead"]] :reason "declined"}
```

### 2.5 Talk crew lines (`pm.talk`)

`empty-talk` gains `:crew-last {}`:

```clojure
:crew-last {:un-automate-monde {:status :blocked :prompt-id "9cc5…"}
            :le-testeur        {:status :done    :prompt-id nil}}
```

There are three new entry kinds (`crew-kinds = #{:crew-prompt :crew-status :crew-say}`). None has
show keys, so they are never captioned or spoken, and `:queue-end` is untouched:

```clojure
{:n 1 :who "Un Automate (monde)" :text "wants to run node -e 'console.log(6*7)'" :at A
 :kind :crew-prompt :member :un-automate-monde
 :prompt {:id "9cc5…" :kind :bash :text "node -e 'console.log(6*7)'" :risky false}
 :state :pending}                       ; :pending :sending :allowed :declined :stale
;; while sending:  + :action :allow|:deny, + :note "use bb instead" (deny with a note only)
;; answered:       :state :allowed|:declined, :answered-at ms (keeps :action, :note)
;; a send failed:  :state :pending, :error "bridge off" (no :action / :note)
{:n 2 :who "Le Testeur" :text "done" :at A :kind :crew-status :member :le-testeur}
{:n 3 :who "You" :text "please rerun the gate" :at A :kind :crew-say :member :le-testeur
 :to "Le Testeur" :state :sending}      ; :sending :sent :failed (+ :error "busy")
```

## 3. Public functions

### 3.1 `pm.prompt` (new, `src/pm/prompt.cljc`, pure, bb + cljs)

```clojure
(ns pm.prompt
  (:require [clojure.string :as str]
            #?(:cljs ["crypto" :as crypto]))
  #?(:clj (:import [java.security MessageDigest])))
```

Constants: `max-text` = 400, `tail-lines` = 6.

**`(parse-screen screen)`** → nil | `{:kind :bash :lines [str…] :full str}` | `{:kind :unknown :full str}`.
- If `screen` is not a string → nil.
- Split with `str/split-lines`. For each line, `str/trimr` it, then:
  - if it matches `^\s*[╭╰]─+[╮╯]$` (a box top or bottom), make it `""`;
  - else if it matches `^\s*│(.*)│$`, keep only the group;
  - then `str/trim`.

  Call the result `ls`.
- `P` = the **last** index whose line is `"Do you want to proceed?"`.
- There must be an option line after `P`: some later line matching `^(❯\s*)?1\.\s+Yes\b`.
- `H` = the last index before `P` whose line is `"Bash command"`.
- `R` = the indices strictly between `H` and `P` whose line matches `^╌{3,}$` (a dashed rule).
- If `P`, the option, `H` and at least two rules exist: take the lines strictly between `R[0]` and
  `R[1]`, drop the blank ones, and call the result `cmd`. If `cmd` is non-empty → `{:kind :bash :lines cmd :full (str/join " " cmd)}`.
- Otherwise → `{:kind :unknown :full (str/join "\n" (take-last 6 (remove str/blank? ls)))}`. The empty
  string gives `{:kind :unknown :full ""}`.

**`(cap-text s)`** → `s` if `(count s)` ≤ 400; otherwise `(str (subs s 0 399) "…")`, which is exactly 400 chars.

**`(prompt-id kind full)`** → the lowercase hex SHA-1 of the UTF-8 bytes of `(str (name kind) "\n" full)`.
- clj: `MessageDigest "SHA-1"`.
- cljs: `(-> (.createHash crypto "sha1") (.update s "utf8") (.digest "hex"))`.
- It hashes the **uncapped** text, so two long commands that differ after char 400 get different ids.

**`(risky? text repo)`** → boolean. Matching is case-insensitive (macOS runs `CURL` as `curl`).
`text` is risky when any of these finds a match anywhere in it:
- `#"(?i)\bgit\b.*\b(add|commit|push|reset|checkout|stash|rm|clean|restore)\b"`
- `#"(?i)\brm\s+-"`, `#"(?i)\bcurl\b"`, `#"(?i)\bwget\b"`, `#"(?i)\bssh\b"`, `#"(?i)\bscp\b"`
- `#"(?i)\bnpm\s+(i|install|publish)\b"`, `#"(?i)\bpip3?\s+install\b"`, `#"(?i)\bbrew\s+install\b"`
- `#"(?i)\bsudo\b"`, `#"(?i)\blaunchctl\b"`
- **or it writes outside the repo.** That means both:
  - the text has a write marker, matching `#"(?i)>|\b(rm|tee|cp|mv|mkdir|touch|ln|rsync|dd|install|chmod|chown|truncate)\b|\bsed\s+-i"`;
  - **and** some token of `(re-seq #"[^\s'\"`;&|<>()=]+" text)` is an outside path. A token is an outside path when it is not one of
    `/dev/null`, `/dev/stdout`, `/dev/stderr`, and it either:
    - starts with `~`;
    - contains a `..` segment (`#"(^|/)\.\.(/|$)"`);
    - starts with `/` and is neither `repo` nor under `(str repo "/")`.

  When `repo` is nil, every absolute path is outside.

This is a guard, not a proof: the player still reads the command before ticking it.

**`(prompt-of screen repo)`** → nil (screen not a string) or `{:id :kind :text :risky}`:
- `:id` = `(prompt-id kind full)`.
- `:text` = `(cap-text full)`.
- `:risky`:
  - for `:bash`: `(or (> (count full) 400) (risky? full repo) (risky? (str/join "" lines) repo))`. The
    second join catches a token that wrapped mid-word (`cur` / `l --version`). A command too long
    to show in full is risky.
  - for `:unknown`: always `true`.

  Invariant: `:risky false` ⇔ the game may offer the allow box.

**`(screens-to-read agents members)`** → the sorted vector of agent keys whose `:status` is
`:blocked` and that are in the set `members`. The watcher reads screens only for these.

**`(with-prompts agents screens repo)`** → `agents` with `:prompt (prompt-of screen repo)` added to each
`:blocked` agent that has a string under its key in `screens`. Other agents are unchanged.

### 3.2 `pm.bridge` (new, `src/pm/bridge.cljc`, pure, bb + cljs)

Constants:
- `host` = `"127.0.0.1"`, `port` = 8091, `allowed-origin` = `"http://localhost:8080"`;
- `max-text` = 1000, `max-body` = 4096 bytes;
- `actions` = `#{"allow" "deny" "say"}`, `wait-ms` = 5000.

**`(roster crew-json)`** → `{kw title}`, where `crew-json` is the parsed `.crew/crew.json` with keyword keys.
- A member is kept when:
  - `(true? (:enabled m))`;
  - it is not `(true? (:external m))`;
  - `(:machine m)` is nil;
  - and `:name` matches `#"[a-z][a-z0-9_-]{0,31}"`.
- Title = `:title` if it's a non-blank string of ≤ 64 chars, else the name.
- nil, or a map with no `:members` sequence → `{}`.

**`(agent-statuses data)`** → `{kw {:status kw}}`, where `data` is the keywordized JSON of `herdr agent list`.
- Agents come from `(get-in data [:result :agents])`.
- Keep the ones whose `:name` matches the name regex. `:status` = `(keyword (or (:agent_status a) "unknown"))`.
- Anything else → `{}`.

This is `crew_status.clj`'s current logic moved here; the watcher and the bridge both use it.

**`(token-hex bytes)`** → lowercase hex, 2 chars per byte, each byte taken as `(bit-and b 0xff)`.

**`(token= a b)`** → true only when both are strings of equal length with identical chars. It
compares in constant time (OR of XORs over all chars). nil or a non-string on either side → false.

**`(bridge-edn token now)`** → `{:port 8091 :token token :as-of now}`.

**`(cors-headers origin)`** → `{"Access-Control-Allow-Origin" "http://localhost:8080" "Vary" "Origin"}` when `origin`
is exactly `allowed-origin`, else `{}`.

**`(preflight origin)`** → the reply to `OPTIONS`:
- right origin → `{:status 204 :headers {"Access-Control-Allow-Origin" "http://localhost:8080" "Access-Control-Allow-Methods" "POST" "Access-Control-Allow-Headers" "content-type" "Access-Control-Max-Age" "600" "Vary" "Origin"}}`;
- otherwise → `{:status 403 :headers {} :body "bad origin"}`.

**`(validate req ctx)`** → `{:ok true :member kw :action kw :prompt-id str|nil :text str|nil}` or
`{:ok false :status n :reason str}`. The checks run **in this order**, and the first failure wins:
1. `:method` ≠ `"POST"` → 405 `"method not allowed"`.
2. `:uri` doesn't match `#"^/crew/([a-z][a-z0-9_-]{0,31})$"` → 404 `"not found"`.
3. `:body` is not a map → 400 `"bad json"`.
4. **Token.** `(token= (get body "token") (:token ctx))` is false → 401 `"bad token"`. A nil ctx token always fails.
5. **Origin.** `:origin` ≠ `allowed-origin` exactly → 403 `"bad origin"`.
6. **Member.** `(keyword name)` not in `(:members ctx)` → 404 `"unknown member"`.
7. `(get body "action")` not in `actions` → 400 `"bad action"`.
8. For allow and deny: `(get body "prompt-id")` must be a string matching `#"^[0-9a-f]{40}$"`, else 400 `"bad prompt-id"`.
   For say, the prompt id is ignored (`:prompt-id nil`).
9. **Text.** Let `raw = (get body "text")`. For allow it is ignored (`:text nil`). For deny and say:
   - raw is non-nil and not a string → 400 `"bad text"`;
   - raw contains a match of `#"[\x00-\x1f\x7f]"` → 400 `"control characters"`;
   - `t = (str/trim raw)`; blank (or raw nil) → nil, and for say that is 400 `"text required"`;
   - `(count t)` > 1000 → 400 `"text too long"`;
   - `:text` = `t`.

**`(decide v live)`** → `{:ok true :steps [...] :reason str}` or `{:ok false :status n :reason str}`.
`v` is an ok result of `validate`. `live` is `{:status kw :prompt prompt-or-nil}`, read by the bridge **now**.
- `:allow`:
  - status ≠ `:blocked` → 409 `"not blocked"`;
  - prompt nil, or `(:id prompt)` ≠ `:prompt-id` → 409 `"prompt changed"`;
  - `(:kind prompt)` ≠ `:bash` → 403 `"unknown prompt"`;
  - `(:risky prompt)` → 403 `"risky: Hal reviews this one"`;
  - else `{:ok true :steps [[:keys "1"]] :reason "allowed"}`.
- `:deny`: the same first two checks (409). Then `:steps [[:keys "esc"]]`, plus
  `[:wait-idle 5000] [:prompt text]` when `:text` is non-nil, with `:reason "declined"`. Risky and
  unknown prompts can be declined.
- `:say`:
  - status `:idle` or `:done` → `{:ok true :steps [[:prompt text]] :reason "sent"}`;
  - `:blocked` → 409 `"blocked: answer its prompt first"`;
  - anything else → 409 `"busy"`.

**`(reply decision origin)`** → `{:status :headers :body}`, where `:body` is a map that the server JSON-encodes.
- ok → status 200, body `{"ok" true "result" reason}`.
- refusal → its status, body `{"ok" false "reason" reason}`.
- Headers = `{"Content-Type" "application/json"}` merged with `(cors-headers origin)`.

**`(step-argv herdr member step)`** → an argv vector for `babashka.process/sh`. It never goes through a shell, and `member` is a keyword.
- `[:keys k]` → `[herdr "agent" "send-keys" m k]`
- `[:wait-idle ms]` → `[herdr "agent" "wait" m "--until" "idle" "--until" "done" "--timeout" (str ms)]`
- `[:prompt t]` → `[herdr "agent" "prompt" m "--" t]`. The `--` stops a text like `--wait` from being read as a flag; Hal checks this live.
- `[:read]` → `[herdr "agent" "read" m "--source" "visible"]`
- `[:list]` → `[herdr "agent" "list"]`
- anything else → nil.

**`(log-line iso member action status reason)`** → `"<iso> <member> <action> <status> <reason>"`.
- member and action may be keywords or strings. nil → `"-"`.
- A member not matching the name regex, or an action not in `actions`, → `"?"`.
- The line never contains the token or the text. bb formats `iso`.

### 3.3 `scripts/crew_bridge.clj` (new) and `bb crew-bridge`

**Start-up**
- `(require '[org.httpkit.server :as http] '[babashka.process :as p] '[cheshire.core :as json] '[pm.bridge :as b] '[pm.prompt :as pr])`.
- Token = `(b/token-hex <32 bytes from java.security.SecureRandom>)` (64 hex).
- Write `public/state/bridge.edn` atomically (tmp file + rename) with `(pr-str (b/bridge-edn token now))`.
  Owner-only permissions where the OS allows. Rewrite it every 5 s with the same token, and delete
  it in a shutdown hook.
- `(http/run-server handler {:ip b/host :port b/port :max-body b/max-body :legacy-return-value? false})`.
  If the port is busy, print one line and exit 1.

**For each request**, in order. Handle requests one at a time (`locking` one lock).
1. `OPTIONS` → `(b/preflight origin)`.
2. Body over `max-body` → 413 `"too large"`.
3. Decode the JSON (`json/parse-string body`, string keys) or use `:bad-json`.
4. `ctx` = the token plus `(set (keys (b/roster crew-json)))`, reading `.crew/crew.json` per request.
5. Run `b/validate`.
6. If ok, read **live**:
   - status from `herdr agent list` through `b/agent-statuses` (a missing agent → `:unknown`);
   - when it's `:blocked`, prompt = `(pr/prompt-of <herdr agent read m --source visible> repo)`, where
     repo is the canonical path of the working directory.
7. `b/decide`.
8. Run the steps, each with `p/sh (b/step-argv herdr m step)`:
   - a non-zero exit → 502 `"herdr failed"`;
   - `:wait-idle` failing skips the `:prompt` step and turns the reason into `"declined; note not sent (still busy)"` (still 200).
9. Reply with `(b/reply decision origin)`, the body JSON-encoded.
10. Append `(b/log-line iso member action status reason)` plus a newline to `crew/bridge.log`, for
    **every** request, including refusals and OPTIONS. Member comes from the URI, action from the body. The token and the text are never logged.

**`bb.edn`**: add `:paths ["src"]` so bb loads `pm/*.cljc`, and a task:
`crew-bridge {:doc "Local bridge for the game's 💭: allow / no / reply to crew prompts (127.0.0.1:8091)" :task (apply shell "bb" "scripts/crew_bridge.clj" *command-line-args*)}`.

**`.crew/crew.json` `"services"`**: append
`{"name": "crew-bridge", "cmd": "bb crew-bridge", "note": "127.0.0.1:8091: the game's 💭 allow / no / reply → herdr (token in public/state/bridge.edn)"}`.

### 3.4 `scripts/crew_status.clj` (watcher)

- `agents` uses `b/agent-statuses`.
- Each loop:
  - `members` = `(b/roster crew-json)`, read from `.crew/crew.json`; a read error → `{}`.
  - For each member in `(pr/screens-to-read agents (set (keys members)))`, read its screen with
    `p/sh (b/step-argv herdr m [:read])`. A failure means no screen.
  - Then `(pr/with-prompts agents screens repo)`.
- Write `{:as-of now :agents … :titles members}`. "Rewrite on change or every 10 s" is unchanged; a prompt change counts as a change.
- Update the ns docstring: names, states, titles and parsed prompts only.

### 3.5 `pm.crew` (changed)

**`parse`** (changed):
- Each agent value is still `{:status s}`, plus `:prompt` when `s` is `:blocked` and the prompt is valid:
  - a map whose `:id` matches `#"^[0-9a-f]{40}$"`;
  - `:kind` ∈ `#{:bash :unknown}`;
  - `:text` a string of ≤ 400 chars;
  - `:risky` a boolean.

  The prompt is reduced to exactly these 4 keys. An invalid prompt is dropped and the agent kept.
- Top-level `:titles` is kept **only when the input has a map there**. It keeps entries with a keyword
  key and a non-blank string of ≤ 64 chars (possibly ending up `{}`). With no `:titles` in the input, the
  result has no `:titles` key, so t01–t07 still hold.

New functions:
- **`(parse-bridge text)`** → `{:port :token :as-of}` or nil.
  - Requirements: an EDN map; `:port` an integer in 1..65535; `:token` matching `#"^[0-9a-f]{64}$"`; `:as-of` an integer ≥ 0.
  - Extra keys are dropped. Garbage, nil or a non-string → nil.
- **`(bridge-on? bridge now)`** → `(and (map? bridge) (<= (- now (:as-of bridge)) stale-ms))`.
- **`(bridge-url bridge member)`** → `(str "http://127.0.0.1:" (:port bridge) "/crew/" (name member))`.
- **`(request-body bridge action prompt-id text)`** → `{"token" … "action" (name action)}`, plus `"prompt-id"`
  when non-nil and `"text"` when non-nil. core turns it into JSON with `clj->js` and `JSON.stringify`.
- **`(outcome status reason)`**:
  - status 200..299 → `{:result :ok}`;
  - 409 → `{:result :stale :reason reason}`;
  - nil (network error) → `{:result :error :reason "bridge off"}`;
  - any other status → `{:result :error :reason (or reason "refused")}`.

### 3.6 `pm.talk` (changed: crew lines)

`pm.talk` now requires `pm.crew`, for `fresh?` only.

New constants:
- `crew-kinds` = `#{:crew-prompt :crew-status :crew-say}`;
- `prompt-states` = `#{:pending :sending :allowed :declined :stale}`;
- `say-states` = `#{:sending :sent :failed}`;
- `you` = `"You"`.

`empty-talk` gains `:crew-last {}`.

All crew lines are added like `add-chore` adds its line:
- `n' = (inc n)`;
- conj, then `take-last cap`;
- `:seen` follows `n'` when `:open?`;
- no show keys, and `:queue-end` is untouched.

**`(observe-crew talk crew now)`** → talk. `crew` is a `pm.crew/parse` result (it may carry `:titles`).
- `talk` nil → `empty-talk`. If `crew` is nil or not `(pm.crew/fresh? crew now)` → return `talk` unchanged (**identical**).
- First, every `:pending` crew-prompt line whose `:member` is **not** a key of `(:agents crew)` becomes `:stale`.
- Then for each member `m` in `(sort (keys (:agents crew)))`:
  - Setup:
    - `s` = its status;
    - `p` = its `:prompt`;
    - `last` = `(get-in talk [:crew-last m])`;
    - `who` = `(get (:titles crew) m (name m))`;
    - `eff` (the prompt id now) = `(:id p)` if `s` is `:blocked` and `p` is present; `(:prompt-id last)` if `:blocked` without `p` (a missed screen
      read changes nothing); otherwise nil.
  1. Every `:pending` crew-prompt line of `m` whose `(:id (:prompt e))` ≠ `eff` → `:state :stale`.
     `:sending` lines are left alone; the reply decides them.
  2. If `s` is `:blocked`, `p` is present and `(:id p)` ≠ `(:prompt-id last)`, append
     `{:who who :text <prompt text> :at now :kind :crew-prompt :member m :prompt p :state :pending}`.
     The prompt text is:
     - `:bash` → `(str "wants to run " (:text p))`;
     - `:unknown` → `(str "is waiting at a prompt: " (str/replace (:text p) "\n" " / "))`.
  3. If `s` is `:done`, `last` is non-nil and `(:status last)` ≠ `:done`, append
     `{:who who :text "done" :at now :kind :crew-status :member m}`. On a member's first sighting nothing is logged.
  4. Set `:crew-last m` to `{:status s :prompt-id eff}`.
- If the result is `=` to `talk`, return `talk` itself (identical).

**`(crew-send talk n action note)`** → talk. Only a `:pending` `:crew-prompt` entry `n` changes.
- `:allow` only when `(allowable? e)`; the note is ignored.
- `:deny` is always allowed. `note` → `(str/trim note)`: blank → none; > 1000 chars → refuse (talk unchanged).
- Result: `(-> e (dissoc :error :note) (assoc :state :sending :action action) (cond-> note (assoc :note note)))`.
- Anything else (wrong state, wrong kind, unknown action, unknown `n`) → talk unchanged (identical).

**`(crew-say talk member who text now)`** → talk with
`{:who "You" :text t :at now :kind :crew-say :member member :to who :state :sending}` appended.
- `t` = trimmed `text`.
- Unchanged (identical) when `t` is blank or over 1000 chars, `member` isn't a keyword, or `who` is blank.

**`(crew-result talk n outcome now)`** → talk. Only an entry `n` in `:sending` changes.
- crew-prompt:
  - `:ok` → `(assoc e :state (if (= :allow (:action e)) :allowed :declined) :answered-at now)`;
  - `:stale` → `(assoc e :state :stale)`;
  - `:error` → `(-> e (dissoc :action :note) (assoc :state :pending :error reason))`.
- crew-say:
  - `:ok` → `(assoc e :state :sent)`;
  - `:stale` or `:error` → `(assoc e :state :failed :error (or reason "refused"))`.
- Anything else → unchanged (identical).

**`(allowable? e)`** → true for a `:crew-prompt` in `:pending` whose prompt is `:kind :bash` with `:risky false`.

**`(prompt-waiting? talk)`** → true when some `:crew-prompt` entry is `:pending` or `:sending`. This drives the red badge.

**`(crew-controls e bridge-on?)`** → nil unless `e` is a `:crew-prompt` in `:pending` or `:sending`. Otherwise:
- `:sending` → `{:allow? (allowable-kind) :disabled? true :hint "sending…"}`;
- `:pending` → `{:allow? (allowable? e) :disabled? (not bridge-on?) :hint (when-not bridge-on? "bridge off")}`.

`allowable-kind` means bash and not risky. `:allow? false` means no checkbox at all (only ✕ and reply).

**`(crew-label e offset-min)`** → string, or nil for a non-crew entry.
- **crew-prompt**: base = `(str (when (:risky (:prompt e)) "⚠ ") who " · " text)`. The suffix depends on `:state`:
  - `:pending` → `(when risky " · Hal reviews this one")` followed by `(when error (str " · not sent: " error))`;
  - `:sending` → `" · sending…"`;
  - `:allowed` → `(str " · ☑ allowed " (clock-text answered-at offset-min))`;
  - `:declined` → `(str " · ✕ declined " (clock-text answered-at offset-min) (when note (str " · “" note "”")))`;
  - `:stale` → `" · already answered"`.
- **crew-status**: `(str who " · " text)`.
- **crew-say**: `(str "You → " to ": " text)` followed by the state:
  - `:sending` → `" · sending…"`;
  - `:sent` → `""`;
  - `:failed` → `(str " · not sent: " error)`.

**`(crew-roster crew now bridge-on?)`** → the 💭 roster strip, so every member is in the bubble, walker or not.
- crew nil → `[]`.
- Members = `(keys (:titles crew))` ∪ `(keys (:agents crew))`, sorted.
- Each row is `{:member m :who (get titles m (name m)) :status st :say? b}`:
  - `st` = the agent's status when crew is fresh and has the agent, else `:unknown`;
  - `b` = `(and bridge-on? (contains? #{:idle :done} st))`.

**Persistence (changed)**
- **`save-text`** adds:
  - the top-level `:crew-last`;
  - the entry keys `:member :prompt :state :action :note :answered-at :error :to`.
- **`restore`** keeps a crew entry only when it passes the existing checks, `:member` is a keyword, and:
  - **crew-prompt**: its `:prompt` is valid (the `pm.crew` prompt rule) and `:state` ∈ `prompt-states`. It keeps
    `:n :who :text :at :kind :member :prompt :state`, plus `:action`, `:note` (string) and `:answered-at` (number) when present.
    `:error` is dropped. A `:sending` line comes back `:pending`, without `:action` or `:note`.
  - **crew-status**: keeps `:n :who :text :at :kind :member`.
  - **crew-say**: `:to` is non-blank and `:state` ∈ `say-states`. It keeps `:to :state`, plus `:error` when `:failed`.
    A `:sending` line comes back `{:state :failed :error "not confirmed"}`.
- **`drop-repeats`** passes crew entries through untouched, like boxes.
- **`:crew-last`** is restored from a map with keyword keys and values
  `{:status keyword :prompt-id (nil | 40 hex)}`. Bad entries are dropped; a missing or non-map value → `{}`.

### 3.7 `pm.ui`, `pm.core`, `public/index.html` (not unit-tested; the live check is Hal's)

**`public/index.html`**
- Add `<div id="crew-roster"></div>` inside `#talk-panel`, before `#talk-list`.
- CSS:
  - `#talk-toggle.alert #talk-badge`: a red pill, the blocked-dot colour `#e5484d`;
  - crew rows: the ✕ button, a small reply `<input>`, a muted `.hint`, and `.risky` (⚠) styling.

**`pm.ui`**
- `(set-badge! text alert?)` toggles class `alert` on `#talk-toggle`.
- `render-panel!` takes one more argument, `crew` = `{:bridge-on? bool :on-allow (fn [n]) :on-deny (fn [n note])}`, and
  renders crew entries (`(contains? talk/crew-kinds (:kind e))`) with `render-crew-row!`:
  - the time;
  - when `crew-controls` gives `:allow? true`, a checkbox (ticking calls on-allow);
  - when controls are non-nil, a ✕ button (calls `(on-deny n nil)`) and a reply input (Enter calls
    `(on-deny n value)`). All of these are disabled when `:disabled?`;
  - the `crew-label` text, and the `:hint` when present.
  - A re-render keeps the value and focus of a non-empty reply input (keyed by `data-n`).
- `(render-roster! rows on-say)` fills `#crew-roster` with one row per member: its dot glyph or status, the title, and a
  "say…" input that is enabled only when `:say?`. Enter calls `(on-say member who value)`. A re-render
  keeps a typed value too.

**`pm.core`**
- `bridge-url-file "/state/bridge.edn"`, `bridge-poll-ms 5000`, and `!bridge-poll`, with the same busy-guard,
  cache-buster and `no-store` pattern as `fetch-crew!`:
  - a response that's not ok → `(:bridge w)` nil;
  - ok → `(crew/parse-bridge text)`, swapped in only when it differs;
  - a network error → left as is.
- Started from `init!`.
- `talk-tick!`: `t' = (-> t (talk/observe w now) (talk/observe-crew (:crew w) now))`.
  - The badge uses `(talk/badge-text t')` and `(talk/prompt-waiting? t')`; re-set it only when either changes.
  - Re-render the panel when the entries **or** `bridge-on?` change.
  - Re-render the roster (`talk/crew-roster`) when its rows change.
- on-allow and on-deny:
  - `(swap! !world update :talk talk/crew-send n action note)`;
  - if the entry is now `:sending`, POST `(crew/request-body b action (:id (:prompt e)) note)` to
    `(crew/bridge-url b (:member e))` with `{:method "POST" :headers {"Content-Type" "application/json"}}`;
  - map the reply to `(crew/outcome status (.-reason json))` (a network failure → `(crew/outcome nil nil)`);
  - then `(swap! !world update :talk talk/crew-result n outcome (js/Date.now))`.
- on-say: `crew-say`, then POST action `:say` with the text, using the new line's `n`.
- Nothing is posted when `bridge-on?` is false (the controls are disabled anyway).
- The game must be opened at `http://localhost:8080`, since the bridge checks that exact Origin.

## 4. Test cases (tester: `test/pm/crew_talk_test.cljs`)

```clojure
(ns pm.crew-talk-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs]
            [pm.prompt :as pr] [pm.bridge :as b] [pm.crew :as c] [pm.talk :as tk]))

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
```

All hashes were computed with a bb prototype of §3.1 and cross-checked with `shasum -a 1`
(`printf "bash\nnode -e 'console.log(6*7)'" | shasum` → `9cc51f01…`).

### pm.prompt: parsing

1. **The real fixture.**
   - `(pr/parse-screen FX)` = `{:kind :bash :lines ["node -e 'console.log(6*7)'"] :full "node -e 'console.log(6*7)'"}`.
   - `(pr/prompt-of FX R)` = `P1`.
2. **Box variant.**
   - `(pr/prompt-of BOX R)` = `P1` (the same id as the fixture).
   - `(:lines (pr/parse-screen BOX))` = `["node -e 'console.log(6*7)'"]`.
3. **Wrapped command.**
   - `(pr/prompt-of WRAP R)` = `{:id "6d92151b8d86fcbaa5daf8d4a7d4371f67275005" :kind :bash :text WRAPPED :risky false}`.
   - `(pr/prompt-of WRAPBOX R)` = the same map.
   - `(:lines (pr/parse-screen WRAP))` = `["node -e 'const a = [1, 2, 3]; console.log(a.map(x" "=> x * 2).join(\" \"))'"]`.
4. **A token wrapped mid-word is still caught.** `(pr/prompt-of (lines " Bash command" " ╌╌╌╌╌╌╌╌╌╌" " cur" " l --version" " ╌╌╌╌╌╌╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No") R)`
   = `{:id "8213920f9386e868c8b99d614356546e33901ad7" :kind :bash :text "cur l --version" :risky true}`.
5. **Id stability.**
   - `(pr/prompt-of (str "⏺ earlier\n  stuff\n" FX) R)` = `P1` (a scrolled conversation).
   - `(:id (pr/prompt-of (-> FX (str/replace "❯ 1. Yes" "  1. Yes") (str/replace "   4. No" " ❯ 4. No")) R))` = `ID1` (the cursor moved).
   - `(pr/prompt-id :bash "node -e 'console.log(6*7)'")` = `ID1`.
   - `(pr/prompt-id :bash "curl --version")` = `ID2`.
   - `(pr/prompt-id :bash "ls")` = `ID3`.
   - `(pr/prompt-id :unknown "")` = `"2d7bb5572221afa7d7fb30c8d19d3f693bfeee14"`.
   - `(not= ID1 (pr/prompt-id :unknown "node -e 'console.log(6*7)'"))`.
6. **Unknown shape (an Edit prompt).**
   - `(pr/prompt-of EDIT R)` = `{:id IDU :kind :unknown :text "src/pm/core.cljs\nDo you want to make this edit to core.cljs?\n❯ 1. Yes\n2. Yes, allow all edits during this session (shift+tab)\n3. No\nEsc to cancel · Tab to amend" :risky true}`.
     That is the last 6 non-blank lines, trimmed.
7. **Shapes that are not a Bash prompt → `:unknown`.**
   - `(:kind (pr/prompt-of (str/replace FX "Do you want to proceed?" "Proceed?") R))` = `:unknown`.
   - `(:kind (pr/prompt-of (str/replace FX "1. Yes" "1. Sure") R))` = `:unknown`.
   - With only one rule, `(pr/prompt-of (lines " Bash command" " ╌╌╌╌" " ls" " Do you want to proceed?" " ❯ 1. Yes") R)`
     = `{:id "8d69882bd93f4c34fbc0ee49ae03b279014a582e" :kind :unknown :text "Bash command\n╌╌╌╌\nls\nDo you want to proceed?\n❯ 1. Yes" :risky true}`.
   - Every `:unknown` result has `:risky true`.
8. **Empty and non-string screens.**
   - `(pr/prompt-of "" R)` = `{:id "2d7bb5572221afa7d7fb30c8d19d3f693bfeee14" :kind :unknown :text "" :risky true}`.
   - `(pr/parse-screen "")` = `{:kind :unknown :full ""}`.
   - `(pr/prompt-of nil R)`, `(pr/prompt-of 42 R)` and `(pr/parse-screen nil)` are all nil.
9. **The 400-char cap.**
   - Long command: `L = (str "echo " (apply str (repeat 500 "a")))` (505 chars). Let `p = (pr/prompt-of (dialog (str " " L)) R)`.
     - `(:id p)` = `"f8381cf075bacab20b88ba54c086a57f22e1b6fd"`, `(count (:text p))` = 400.
     - `(:text p)` = `(str (subs L 0 399) "…")`.
     - `(:risky p)` = true: too long to show in full, though nothing else in it is risky.
   - Exactly 400: `L4 = (str "echo " (apply str (repeat 395 "a")))` (400 chars). Then `(pr/prompt-of (dialog (str " " L4)) R)`
     = `{:id "478cb20acb9ae9876a7c695af4a7b5e8eb014b86" :kind :bash :text L4 :risky false}`.
   - `(pr/cap-text "abc")` = `"abc"`, and `(count (pr/cap-text (apply str (repeat 401 "x"))))` = 400.
   - `pr/max-text` = 400, `pr/tail-lines` = 6.

### pm.prompt: risky

10. **Each driver pattern → true** (`(pr/risky? x R)`):
    - git: `"git add ."`, `"git commit -m x"`, `"git push"`, `"git reset --hard"`, `"git checkout main"`, `"git stash"`,
      `"git rm x"`, `"git clean -fd"`, `"git restore x"`, `"git -C /repo push"`, `"git status && git push"`;
    - `"rm -rf out"`, `"curl --version"`, `"CURL --version"`, `"wget x"`, `"ssh host"`, `"scp a b:c"`, `"ls; curl x"`;
    - `"npm i"`, `"npm install three"`, `"npm publish"`, `"pip install x"`, `"pip3 install x"`, `"brew install x"`, `"sudo ls"`, `"launchctl list"`.
11. **Benign → false:**
    - the quest's three: `"node -e 'console.log(6*7)'"`, `"bb test"`, `"ls"`;
    - read-only git: `"git status"`, `"git diff"`, `"git log --oneline"`;
    - npm and npx: `"npm init"`, `"npm run x"`, `"npx shadow-cljs compile app"`;
    - other benign commands: `".crew/gate.sh"`, `"rm out.txt"` (no flag, inside the repo), `"touch notes.md"`;
    - word-boundary checks: `"digit add"`, `"curling"`, `"scurl"`, `"sshd"`.
12. **Writes outside the repo:**
    - → true:
      - redirects out of the repo: `"echo hi > /tmp/x"`, `"echo a >/repo2/x"` (a prefix but not under `/repo/`);
      - copies: `"cp a ~/b"`, `"cp a ../b"`;
      - other writers: `"mkdir /repo/../x"`, `"rm /etc/x"`, `"sed -i s/a/b/ /etc/x"`, `"cd /tmp && touch x"`, `"tee -a /var/log/x"`.
    - → false:
      - redirects that stay put: `"echo hi > out.txt"`, `"ls > /dev/null"`, `"node x.js 2>&1"`, `"bash .crew/gate.sh > /repo/out.txt"`;
      - a copy inside the repo: `"cp a /repo/b"`;
      - no write marker: `"cat /etc/hosts"`, `"cat ../README.md"`.
    - With no repo, `(pr/risky? "cp a /repo/b" nil)` → true.

### pm.prompt: watcher helpers

13. **`screens-to-read`.** `(pr/screens-to-read {:le-testeur {:status :blocked} :un-automate-monde {:status :working} :other {:status :blocked} :le-critique {:status :blocked}} #{:le-testeur :le-critique :un-automate-monde})`
    = `[:le-critique :le-testeur]`. `(pr/screens-to-read {} MEMBERS)` = `[]`.
14. **`with-prompts`.** `(pr/with-prompts {:le-testeur {:status :blocked} :un-automate-monde {:status :working} :le-critique {:status :blocked}} {:le-testeur FX :un-automate-monde FX :le-critique nil} R)`
    = `{:le-testeur {:status :blocked :prompt P1} :un-automate-monde {:status :working} :le-critique {:status :blocked}}`.

### pm.bridge

15. **Constants.** `b/host` = `"127.0.0.1"`, `b/port` = 8091, `b/allowed-origin` = `"http://localhost:8080"`,
    `b/max-text` = 1000, `b/max-body` = 4096, `b/actions` = `#{"allow" "deny" "say"}`, `b/wait-ms` = 5000.
16. **Roster.** `CJ` mirrors crew.json:
    ```clojure
    {:members [{:name "hal" :title "Hal" :external true :enabled true}
               {:name "un-architecte" :title "Un Architecte" :enabled true}
               {:name "un-automate-monde" :title "Un Automate (monde)" :enabled true}
               {:name "le-testeur" :title "Le Testeur" :enabled true}
               {:name "le-coureur" :title "Le Coureur" :enabled true}
               {:name "le-coureur-omy" :title "Le Coureur (omarchy)" :enabled true :machine "omarchy"}
               {:name "le-critique" :title "Le Critique" :enabled true :external false}
               {:name "le-docteur" :title "Le Docteur" :enabled false}
               {:name "Bad Name" :title "X" :enabled true}
               {:name "sans-titre" :enabled true}
               {:name "long" :title (apply str (repeat 65 "t")) :enabled true}]}
    ```
    `(b/roster CJ)` = `{:un-architecte "Un Architecte" :un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur" :le-coureur "Le Coureur" :le-critique "Le Critique" :sans-titre "sans-titre" :long "long"}`.
    Also `(b/roster nil)` = `{}` and `(b/roster {:members "x"})` = `{}`.
17. **Agent statuses.** `(b/agent-statuses {:result {:agents [{:name "le-testeur" :agent_status "done"} {:name "un-automate-monde" :agent_status "blocked"} {:name "Bad Name" :agent_status "idle"} {:name "x" :agent_status nil} {:agent_status "idle"}]}})`
    = `{:le-testeur {:status :done} :un-automate-monde {:status :blocked} :x {:status :unknown}}`.
    `(b/agent-statuses nil)` = `{}` and `(b/agent-statuses {:result {}})` = `{}`.
18. **Token helpers.**
    - `(b/token-hex [0 1 255 16 -1])` = `"0001ff10ff"`.
    - `(b/token= TOK TOK)` → true. False for each of:
      - `(b/token= TOK (str (subs TOK 0 63) "c"))` (one char changed);
      - `(b/token= TOK (str TOK "0"))` (one char longer);
      - `(b/token= nil nil)`, `(b/token= TOK nil)`, `(b/token= TOK 5)`.
    - `(b/bridge-edn TOK A)` = `{:port 8091 :token TOK :as-of A}`.
19. **Validate: the happy paths.**
    - Allow: `(b/validate (rq "le-testeur" ALLOW) CTX)` = `{:ok true :member :le-testeur :action :allow :prompt-id ID1 :text nil}`.
      With `"text" "x"` added → the same result (text ignored).
    - Deny with a note: `(b/validate (rq "un-automate-monde" (assoc ALLOW "action" "deny" "text" "  use bb instead ")) CTX)`
      = `{:ok true :member :un-automate-monde :action :deny :prompt-id ID1 :text "use bb instead"}`.
      With `"text" "   "` → `:text nil`.
    - Say: `(b/validate (rq "le-testeur" {"token" TOK "action" "say" "text" "please rerun the gate" "prompt-id" ID1}) CTX)`
      = `{:ok true :member :le-testeur :action :say :prompt-id nil :text "please rerun the gate"}`.
    - Text at the limit: `"text"` of 1000 `"a"` (deny) is ok, and its `:text` count is 1000.
20. **Validate: refusals.** Each result is `{:ok false :status s :reason r}`.
    - Method and path:
      - `(assoc (rq "le-testeur" ALLOW) :method "GET")` → 405 `"method not allowed"`;
      - `:uri` `"/crew/"`, `"/crew/Le-Testeur"`, `"/crew/le-testeur/x"` or `"/x"` → 404 `"not found"`.
    - Body: `:body` `:bad-json`, `["a"]` or nil → 400 `"bad json"`.
    - Token → 401 `"bad token"` for each of:
      - no token: `(dissoc ALLOW "token")`;
      - a wrong value: `"token" (str (subs TOK 0 63) "c")`;
      - a longer token: `"token" (str TOK "0")`;
      - a non-string: `"token" 5`;
      - a nil ctx token: `(b/validate (rq "le-testeur" (assoc ALLOW "token" nil)) {:token nil :members MEMBERS})`.
    - Origin → 403 `"bad origin"` for `:origin` `"http://127.0.0.1:8080"`, `"http://localhost:8080/"`, `"https://localhost:8080"`,
      `"http://localhost:8081"`, `"null"` or nil.
    - Member → 404 `"unknown member"` for members `"hal"` (external), `"le-coureur-omy"` (remote), `"le-docteur"` (disabled), `"zz"`.
    - Action → 400 `"bad action"` for `"action"` `"approve"` or nil.
    - Prompt id → 400 `"bad prompt-id"` for an allow with `"prompt-id"` `(str/upper-case ID1)`, `(subs ID1 0 39)`, nil or 5, and for a deny with nil.
    - Text:
      - say with no text, or text `"  "` → 400 `"text required"`;
      - deny or say with `"text"` 1001 `"a"` → 400 `"text too long"`;
      - `"text" 5` → 400 `"bad text"`;
      - `"text"` `"line1\nline2"`, `"\u001b[A"` or `"tab\there"` → 400 `"control characters"`.
21. **Validate: the check order.**
    - Wrong token and wrong origin → 401.
    - Right token, wrong origin, unknown member `"hal"` → 403.
    - Right token and origin, member `"hal"`, action `"approve"` → 404 `"unknown member"`.
    - Member ok, action `"approve"`, no prompt-id → 400 `"bad action"`.
    - Method `"GET"` with a bad token → 405.
22. **Preflight and CORS.**
    - `(b/preflight OK-ORIGIN)` = `{:status 204 :headers {"Access-Control-Allow-Origin" "http://localhost:8080" "Access-Control-Allow-Methods" "POST" "Access-Control-Allow-Headers" "content-type" "Access-Control-Max-Age" "600" "Vary" "Origin"}}`.
    - `(b/preflight "http://evil.example")` = `{:status 403 :headers {} :body "bad origin"}`; the same for nil.
    - `(b/cors-headers OK-ORIGIN)` = `{"Access-Control-Allow-Origin" "http://localhost:8080" "Vary" "Origin"}`.
    - `(b/cors-headers "http://evil.example")` = `{}`.
23. **Decide: allow.** Let `VA = {:ok true :member :le-testeur :action :allow :prompt-id ID1 :text nil}`.
    - `(b/decide VA {:status :blocked :prompt P1})` = `{:ok true :steps [[:keys "1"]] :reason "allowed"}`.
    - `{:status :working :prompt nil}` → `{:ok false :status 409 :reason "not blocked"}`.
    - `{:status :blocked :prompt P3}` and `{:status :blocked :prompt nil}` → 409 `"prompt changed"`.
    - Risky: `(b/decide (assoc VA :prompt-id ID2) {:status :blocked :prompt P2})` → 403 `"risky: Hal reviews this one"`.
    - Unknown: `(b/decide (assoc VA :prompt-id IDU) {:status :blocked :prompt (assoc PU :risky false)})` → 403 `"unknown prompt"`.
      The kind is checked before risky; `:risky false` here proves it.
24. **Decide: deny.** Let `VD = (assoc VA :action :deny)`.
    - `(b/decide VD {:status :blocked :prompt P1})` = `{:ok true :steps [[:keys "esc"]] :reason "declined"}`.
    - With a note: `(b/decide (assoc VD :text "use bb instead") {:status :blocked :prompt P1})` = `{:ok true :steps [[:keys "esc"] [:wait-idle 5000] [:prompt "use bb instead"]] :reason "declined"}`.
    - Risky: `(b/decide (assoc VD :prompt-id ID2) {:status :blocked :prompt P2})` = `{:ok true :steps [[:keys "esc"]] :reason "declined"}`.
    - Not blocked: `{:status :idle}` → 409 `"not blocked"`.
    - Mismatch: `{:status :blocked :prompt P3}` → 409 `"prompt changed"`.
25. **Decide: say.** Let `VS = {:ok true :member :le-testeur :action :say :prompt-id nil :text "hi"}`.
    - `:idle` and `:done` → `{:ok true :steps [[:prompt "hi"]] :reason "sent"}`.
    - `:blocked` → 409 `"blocked: answer its prompt first"`.
    - `:working` or `:unknown` → 409 `"busy"`.
26. **Reply.**
    - `(b/reply {:ok true :steps [[:keys "1"]] :reason "allowed"} OK-ORIGIN)` = `{:status 200 :headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "http://localhost:8080" "Vary" "Origin"} :body {"ok" true "result" "allowed"}}`.
    - `(b/reply {:ok false :status 401 :reason "bad token"} "http://evil.example")` = `{:status 401 :headers {"Content-Type" "application/json"} :body {"ok" false "reason" "bad token"}}`.
27. **Argv** (no shell, member as a plain name).
    - `(b/step-argv "herdr" :le-testeur [:keys "1"])` = `["herdr" "agent" "send-keys" "le-testeur" "1"]`.
    - `[:keys "esc"]` → `["herdr" "agent" "send-keys" "le-testeur" "esc"]`.
    - `[:wait-idle 5000]` → `["herdr" "agent" "wait" "le-testeur" "--until" "idle" "--until" "done" "--timeout" "5000"]`.
    - `[:prompt "use bb instead"]` → `["herdr" "agent" "prompt" "le-testeur" "--" "use bb instead"]`.
    - `[:prompt "--wait"]` → `["herdr" "agent" "prompt" "le-testeur" "--" "--wait"]`.
    - `[:read]` → `["herdr" "agent" "read" "le-testeur" "--source" "visible"]`.
    - `[:list]` → `["herdr" "agent" "list"]`.
    - `[:shell "ls"]` → nil.
28. **Log line.**
    - `(b/log-line "2026-11-08T00:52:00Z" "le-testeur" "allow" 200 "allowed")` = `"2026-11-08T00:52:00Z le-testeur allow 200 allowed"`.
    - `(b/log-line "T" :le-testeur :say 200 "sent")` = `"T le-testeur say 200 sent"`.
    - `(b/log-line "T" "../etc" "rm -rf" 404 "not found")` = `"T ? ? 404 not found"`.
    - `(b/log-line "T" nil nil 405 "method not allowed")` = `"T - - 405 method not allowed"`.
    - With `L = (b/log-line "T" "le-testeur" "deny" 200 "declined")`, `(str/includes? L TOK)` is false.

### pm.crew

29. **parse keeps a valid prompt on a blocked agent only.** Write the input as `(pr-str …)`.
    - `(c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc P1 :extra 1)} :b {:status :working :prompt P1}}}))`
      = `{:as-of 5 :agents {:a {:status :blocked :prompt P1} :b {:status :working}}}`.
    - Invalid prompts are dropped and the agent kept: for each `bad` below, `(c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt bad}}}))` = `{:as-of 5 :agents {:a {:status :blocked}}}`.
      - an id that is uppercase or too short: `(assoc P1 :id (str/upper-case ID1))`, `(assoc P1 :id "abc")`;
      - a wrong kind: `(assoc P1 :kind :edit)`;
      - text over 400 chars: `(assoc P1 :text (apply str (repeat 401 "x")))`;
      - risky not a boolean: `(assoc P1 :risky "no")`;
      - not a map: `"P1"`.
    - Text at the limit is kept: a 400-char text survives.
    - The unknown prompt: `(c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt PU}}}))` keeps `PU`.
30. **parse `:titles`.**
    - `(c/parse (pr-str {:as-of 5 :agents {} :titles {:le-testeur "Le Testeur" :x "" "s" "S" :y 3 :z (apply str (repeat 65 "t"))}}))`
      = `{:as-of 5 :agents {} :titles {:le-testeur "Le Testeur"}}`.
    - `(c/parse "{:as-of 5 :agents {} :titles [1]}")` = `{:as-of 5 :agents {}}` (no `:titles` key).
    - `(c/parse "{:as-of 5 :agents {} :titles {}}")` = `{:as-of 5 :agents {} :titles {}}`.
31. **The bridge file.** Let `B = {:port 8091 :token TOK :as-of A}`.
    - `(c/parse-bridge (pr-str (assoc B :extra 1)))` = `B`.
    - Round trip: `(c/parse-bridge (pr-str (b/bridge-edn TOK A)))` = `B`.
    - nil for each of:
      - a bad port: `(pr-str (assoc B :port 0))`, `:port 65536`, `:port "8091"`;
      - a bad token: `:token (subs TOK 0 63)`, `:token (str/upper-case TOK)`;
      - a bad time: `(pr-str (dissoc B :as-of))`, `:as-of -1`;
      - not a bridge file: `"{:port"`, `""`, nil, `42`.
    - `(c/bridge-on? B (+ A 15000))` → true, `(c/bridge-on? B (+ A 15001))` → false, `(c/bridge-on? nil A)` → false.
32. **URL, body, outcome.**
    - `(c/bridge-url B :le-testeur)` = `"http://127.0.0.1:8091/crew/le-testeur"`.
    - Request bodies:
      - `(c/request-body B :allow ID1 nil)` = `{"token" TOK "action" "allow" "prompt-id" ID1}`;
      - `(c/request-body B :deny ID1 "use bb instead")` = `{"token" TOK "action" "deny" "prompt-id" ID1 "text" "use bb instead"}`;
      - `(c/request-body B :say nil "hi")` = `{"token" TOK "action" "say" "text" "hi"}`.
    - Outcomes:
      - `(c/outcome 200 nil)` and `(c/outcome 204 nil)` = `{:result :ok}`;
      - `(c/outcome 409 "prompt changed")` = `{:result :stale :reason "prompt changed"}`;
      - `(c/outcome 403 "risky: Hal reviews this one")` = `{:result :error :reason "risky: Hal reviews this one"}`;
      - `(c/outcome 401 nil)` = `{:result :error :reason "refused"}`;
      - `(c/outcome nil nil)` = `{:result :error :reason "bridge off"}`.

### pm.talk: crew lines

33. **First sighting of a blocked member gives a prompt line; nothing for the others.**
    - `(:entries T1)` = `[{:n 1 :who "Un Automate (monde)" :text "wants to run node -e 'console.log(6*7)'" :at A :kind :crew-prompt :member :un-automate-monde :prompt P1 :state :pending}]`.
    - `(:n T1)` = 1, `(:seen T1)` = 0, `(tk/unread T1)` = 1, `(:queue-end T1)` = 0.
    - `(:crew-last T1)` = `{:le-testeur {:status :working :prompt-id nil} :un-automate-monde {:status :blocked :prompt-id ID1}}`.
    - With the panel open: `(:seen (tk/observe-crew (tk/open-panel T0) C1 A))` = 1.
    - `(tk/caption T1 A)` = nil (crew lines are never captioned).
    - `tk/empty-talk` has `:crew-last {}`.
34. **Unchanged, stale, nil → the same talk (`identical?`).**
    - `(tk/observe-crew T1 C1 (+ A 2000))`, `(tk/observe-crew T1 (crew (+ A 2000) (:agents C1)) (+ A 2000))`,
      `(tk/observe-crew T0 C1 (+ A 15001))`, `(tk/observe-crew T0 nil A)` and `(tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :blocked}}) (+ A 2000))`
      are all identical to their input: a blocked member whose screen wasn't read changes nothing.
    - `(tk/observe-crew T0 C1 (+ A 15000))` is not identical to `T0` (still fresh).
35. **"Done" only on a transition, for the second member too.**
    - `T2 = (tk/observe-crew T1 (crew (+ A 4000) {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :done}}) (+ A 4000))`.
      - `(e T2 2)` = `{:n 2 :who "Le Testeur" :text "done" :at (+ A 4000) :kind :crew-status :member :le-testeur}`;
      - `(get-in T2 [:crew-last :le-testeur])` = `{:status :done :prompt-id nil}`;
      - observing the same crew again at `(+ A 6000)` → identical.
    - First sighting already done: `(tk/observe-crew T0 (crew A {:le-testeur {:status :done}}) A)` has `:entries []`, `:n 0` and
      `:crew-last {:le-testeur {:status :done :prompt-id nil}}`.
    - A member without a title: `(:who (e (tk/observe-crew T0 (crew A {:le-critique {:status :blocked :prompt PU}}) A) 1))` = `"le-critique"`.
      Its `:text` = `"is waiting at a prompt: Edit file / 3. No"`.
36. **Prompt lifecycle on the crew side.**
    - The second member gets a risky line: `T3 = (tk/observe-crew T2 (crew (+ A 6000) {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :blocked :prompt P2}}) (+ A 6000))`.
      `(e T3 3)` = `{:n 3 :who "Le Testeur" :text "wants to run curl --version" :at (+ A 6000) :kind :crew-prompt :member :le-testeur :prompt P2 :state :pending}`.
    - The first member unblocks: `T4 = (tk/observe-crew T3 (crew (+ A 8000) {:un-automate-monde {:status :working} :le-testeur {:status :blocked :prompt P2}}) (+ A 8000))`.
      - `(:state (e T4 1))` = `:stale` and `(:state (e T4 3))` = `:pending`;
      - `(get-in T4 [:crew-last :un-automate-monde])` = `{:status :working :prompt-id nil}`;
      - `(:n T4)` = 3.
    - The same prompt comes back after an unblock: `T5 = (tk/observe-crew T4 (crew (+ A 10000) {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :blocked :prompt P2}}) (+ A 10000))`.
      `(e T5 4)` = `{:n 4 :who "Un Automate (monde)" :text "wants to run node -e 'console.log(6*7)'" :at (+ A 10000) :kind :crew-prompt :member :un-automate-monde :prompt P1 :state :pending}`, and `(:n T5)` = 4.
    - A new prompt while one is pending: `T6 = (tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :blocked :prompt P3}}) (+ A 2000))`.
      `(:state (e T6 1))` = `:stale`, `(:text (e T6 2))` = `"wants to run ls"`, `(:state (e T6 2))` = `:pending`.
    - A member that vanishes from a fresh file: `(:state (e (tk/observe-crew T1 (crew (+ A 2000) {}) (+ A 2000)) 1))` = `:stale`.
37. **Allow round trip.**
    - `S = (tk/crew-send T1 1 :allow "ignored")`; `(e S 1)` = `(assoc (e T1 1) :state :sending :action :allow)`.
    - `Y = (tk/crew-result S 1 {:result :ok} (+ A 60000))`; `(e Y 1)` = `(assoc (e T1 1) :state :allowed :action :allow :answered-at (+ A 60000))`.
    - `(tk/crew-label (e Y 1) OFF)` = `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ☑ allowed 5:01 PM"`.
    - The line survives a crew poll that still shows the same blocked prompt: `(:state (e (tk/observe-crew Y (crew (+ A 61000) (:agents C1)) (+ A 61000)) 1))` = `:allowed`.
38. **Deny, with a note and without.**
    - `D = (tk/crew-send T1 1 :deny "  use bb instead ")`: `(e D 1)` = `(assoc (e T1 1) :state :sending :action :deny :note "use bb instead")`.
    - `D2 = (tk/crew-result D 1 {:result :ok} (+ A 60000))`: `(:state (e D2 1))` = `:declined`, `(:answered-at (e D2 1))` = `(+ A 60000)`.
    - `(tk/crew-label (e D2 1) OFF)` = `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ✕ declined 5:01 PM · “use bb instead”"`.
    - Without a note: `(tk/crew-label (e (tk/crew-result (tk/crew-send T1 1 :deny nil) 1 {:result :ok} (+ A 60000)) 1) OFF)`
      = `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · ✕ declined 5:01 PM"`.
      `(tk/crew-send T1 1 :deny "   ")` has no `:note` key.
    - A note over 1000 chars → `(identical? T1 (tk/crew-send T1 1 :deny (apply str (repeat 1001 "a"))))`.
39. **Risky and unknown prompts: no allow, deny works.**
    - Allowing a risky line changes nothing: `(identical? T3 (tk/crew-send T3 3 :allow nil))`.
    - Denying it works: `(tk/crew-label (e (tk/crew-result (tk/crew-send T3 3 :deny nil) 3 {:result :ok} (+ A 60000)) 3) OFF)` = `"⚠ Le Testeur · wants to run curl --version · ✕ declined 5:01 PM"`.
    - `(tk/crew-label (e T3 3) OFF)` = `"⚠ Le Testeur · wants to run curl --version · Hal reviews this one"`.
    - The unknown line: `U = (tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt PU}}) A)`.
      - `(tk/crew-label (e U 1) OFF)` = `"⚠ Le Testeur · is waiting at a prompt: Edit file / 3. No · Hal reviews this one"`;
      - `(identical? U (tk/crew-send U 1 :allow nil))`.
    - `(tk/allowable? (e T1 1))` → true; `(tk/allowable? (e T3 3))` → false; `(tk/allowable? (e U 1))` → false; `(tk/allowable? (e T2 2))` → false.
40. **Stale and error outcomes; invalid calls.**
    - `(e (tk/crew-result S 1 {:result :stale :reason "prompt changed"} (+ A 60000)) 1)` = `(assoc (e T1 1) :state :stale :action :allow)`;
      its label is `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · already answered"`.
    - `F = (tk/crew-result D 1 {:result :error :reason "bridge off"} (+ A 60000))`.
      - `(e F 1)` = `(assoc (e T1 1) :error "bridge off")` (`:state :pending`, no `:action` or `:note`);
      - its label is `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · not sent: bridge off"`.
      - Sending again clears the error: `(e (tk/crew-send F 1 :allow nil) 1)` = `(assoc (e T1 1) :state :sending :action :allow)`.
    - `(tk/crew-label (e S 1) OFF)` = `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · sending…"`.
    - Each of these is identical to its input:
      - a result for a line that isn't sending: `(tk/crew-result T1 1 {:result :ok} A)`;
      - a send on a line that isn't pending: `(tk/crew-send Y 1 :deny nil)`, `(tk/crew-send S 1 :allow nil)`;
      - a send for a missing line: `(tk/crew-send T1 99 :allow nil)`;
      - a status line: `(tk/crew-send T2 2 :deny nil)`;
      - an unknown action: `(tk/crew-send T1 1 :approve nil)`.
41. **Say lifecycle.**
    - `Q = (tk/crew-say T1 :le-testeur "Le Testeur" " please rerun the gate " (+ A 1000))`:
      - `(e Q 2)` = `{:n 2 :who "You" :text "please rerun the gate" :at (+ A 1000) :kind :crew-say :member :le-testeur :to "Le Testeur" :state :sending}`;
      - its label is `"You → Le Testeur: please rerun the gate · sending…"`.
    - Sent: `(tk/crew-label (e (tk/crew-result Q 2 {:result :ok} (+ A 2000)) 2) OFF)` = `"You → Le Testeur: please rerun the gate"`.
    - Stale: `(e (tk/crew-result Q 2 {:result :stale :reason "busy"} (+ A 2000)) 2)` = `(assoc (e Q 2) :state :failed :error "busy")`;
      its label is `"You → Le Testeur: please rerun the gate · not sent: busy"`.
    - Error: `(:error (e (tk/crew-result Q 2 {:result :error :reason "bridge off"} (+ A 2000)) 2))` = `"bridge off"`.
    - Identical to the input: `(tk/crew-say T1 :le-testeur "Le Testeur" "   " A)`, the same with 1001 `"a"`, `(tk/crew-say T1 "le-testeur" "Le Testeur" "hi" A)`
      and `(tk/crew-say T1 :le-testeur "" "hi" A)`.
    - `(tk/crew-label (e T2 2) OFF)` = `"Le Testeur · done"`.
    - `(tk/crew-label {:n 9 :who "Hal" :text "hi" :at A :kind :hal} OFF)` = nil.
42. **The badge and the controls.**
    - `prompt-waiting?` (drives the red badge):
      - false for `T0`, for `Y` (allowed), and for `(tk/observe-crew T1 (crew (+ A 2000) {:un-automate-monde {:status :working}}) (+ A 2000))` (its only line went stale);
      - true for `T1`, `S` (sending) and `T3`.
    - `(tk/crew-controls (e T1 1) true)` = `{:allow? true :disabled? false :hint nil}`.
    - `(tk/crew-controls (e T1 1) false)` = `{:allow? true :disabled? true :hint "bridge off"}`.
    - `(tk/crew-controls (e T3 3) true)` = `{:allow? false :disabled? false :hint nil}`.
    - `(tk/crew-controls (e T3 3) false)` = `{:allow? false :disabled? true :hint "bridge off"}`.
    - `(tk/crew-controls (e S 1) true)` and `(tk/crew-controls (e S 1) false)` = `{:allow? true :disabled? true :hint "sending…"}`.
    - `(tk/crew-controls (e Y 1) true)`, `(tk/crew-controls (e T2 2) true)` and `(tk/crew-controls (e Q 2) true)` = nil.
43. **The roster strip: every member, walker or not.**
    - `(tk/crew-roster C1 A true)` = `[{:member :le-testeur :who "Le Testeur" :status :working :say? false} {:member :un-architecte :who "Un Architecte" :status :unknown :say? false} {:member :un-automate-monde :who "Un Automate (monde)" :status :blocked :say? false}]`.
    - Let `CR = (crew A {:le-testeur {:status :done} :un-architecte {:status :idle} :un-automate-monde {:status :blocked :prompt P1} :le-critique {:status :working}})`.
      `(mapv (juxt :member :who :say?) (tk/crew-roster CR A true))`
      = `[[:le-critique "le-critique" false] [:le-testeur "Le Testeur" true] [:un-architecte "Un Architecte" true] [:un-automate-monde "Un Automate (monde)" false]]`.
    - `(mapv :say? (tk/crew-roster CR A false))` = `[false false false false]` (bridge off).
    - `(mapv (juxt :status :say?) (tk/crew-roster CR (+ A 15001) true))` = `[[:unknown false] [:unknown false] [:unknown false] [:unknown false]]` (stale).
    - `(tk/crew-roster nil A true)` = `[]`.
44. **Persistence.**
    - Let `ZZ` = `D2` followed by:
      1. `(tk/crew-say … :le-testeur "Le Testeur" "hi" (+ A 61000))`, giving n 2, sending;
      2. `(tk/observe-crew … (crew (+ A 62000) {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :done}}) (+ A 62000))`.

      Expected after step 2: n 2 is the say line. T1 had recorded `:le-testeur` as `:working`, so step 2 adds
      `{:n 3 :who "Le Testeur" :text "done" :at (+ A 62000) :kind :crew-status :member :le-testeur}`, and `(:n ZZ)` = 3.
    - Then `R2 = (tk/restore (tk/save-text ZZ))`:
      - `(e R2 1)` = `(e ZZ 1)` (declined, with its note and `:answered-at`);
      - `(e R2 2)` = `(assoc (e ZZ 2) :state :failed :error "not confirmed")`;
      - `(e R2 3)` = `(e ZZ 3)`, and `(:n R2)` = 3;
      - `(:crew-last R2)` = `(:crew-last ZZ)` = `{:un-automate-monde {:status :blocked :prompt-id ID1} :le-testeur {:status :done :prompt-id nil}}`;
      - `(identical? R2 (tk/observe-crew R2 (crew (+ A 64000) {:un-automate-monde {:status :blocked :prompt P1} :le-testeur {:status :done}}) (+ A 64000)))` (no repeats on reload).
    - A sending prompt line comes back pending: `(e (tk/restore (tk/save-text S)) 1)` = `(e T1 1)`.
    - Two done lines a minute apart both survive a restore. Let `DD` be:
      1. `T2`;
      2. observed with `{:le-testeur {:status :working}}` at `(+ A 50000)`, with `:as-of` `(+ A 50000)`;
      3. observed with `{:le-testeur {:status :done}}` at `(+ A 110000)`.

      Then `(count (filter #(= :crew-status (:kind %)) (:entries (tk/restore (tk/save-text DD)))))` = 2.
    - `(tk/restore (tk/save-text T0))` = `T0`. The existing t22 still holds.
    - Bad crew entries are dropped on restore. Write the input with `(pr-str {:v 1 :n 4 :seen 0 :entries [...]})` holding:
      1. a valid status line: `{:n 1 :who "Le Testeur" :text "done" :at A :kind :crew-status :member :le-testeur}`;
      2. that line with `:member` removed and `:n 2`;
      3. `(assoc (e T1 1) :n 3 :prompt (assoc P1 :id "xyz"))`;
      4. `(assoc (e T1 1) :n 4 :state :maybe)`.

      Then `(mapv :n (:entries (tk/restore that)))` = `[1]`.
      `(:crew-last (tk/restore (pr-str {:v 1 :n 0 :seen 0 :entries [] :crew-last {:a {:status :done :prompt-id nil} :b {:status "x"} "c" {:status :idle} :d {:status :blocked :prompt-id "xyz"}}})))` = `{:a {:status :done :prompt-id nil}}`.
45. **Mirror talk is untouched by crew lines.**
    - `(tk/observe (tk/observe-crew T0 C1 A) W A)`, with `W` = `(assoc (pm.world/load-estate pm.world/estate-edn) :lines pm.fixtures/lines-v0)` as in `pm.talk-test`, keeps `(e … 1)` = `(e T1 1)`.
    - It appends the creature lines after it, with `(:n …)` = 3: Flora n 2, Juniper n 3.
    - To set this up, require `pm.world` and `pm.fixtures` in the test ns.

**45 test cases.**

## 5. Notes for the builder

- `.cljc` portability:
  - SHA-1 and token comparison need reader conditionals: `#?(:clj (int (.charAt s i)) :cljs (.charCodeAt s i))`.
  - Hex is done by hand with `"0123456789abcdef"`; there's no `format` in cljs.
  - The regex literals above (`(?i)`, `\b`, `\x00`) mean the same thing in Java and JS.
- Keep `pm.prompt` and `pm.bridge` out of `pm.core`'s requires: the browser build must not pull in `crypto`.
- `pm.crew` re-states the 40-hex prompt rule (it can't require `pm.bridge`), and `pm.talk` restore reuses `pm.crew`'s prompt validation.
- Known limits, accepted for v0:
  - the same command blocked twice within one 2–4 s poll gap shows as one line;
  - a prompt can change between the bridge's re-read and its key press (requests are serialized, and the window is milliseconds);
  - the risky check is a heuristic; the player reads the command.
- After building, run `.crew/gate.sh`. Don't run `bb crew-bridge`, and don't call `herdr agent send-keys` or `herdr agent prompt`.
