# Spec: crew-talk v1 — Hal fields the crew's prompts, Michael sees what matters

Quest: `.crew/quests/crew-talk-v1.md`. Follows `crew-talk-v0` (spec and review in
`crew/runs/crew-talk-v0-20261107-1728/`). Architect: un-architecte. Builder: un-automate-monde.
Tester: le-testeur. Docs: le-docteur.

## 0. What v0 actually got wrong, and the shape of v1

I ran v0's `pm.prompt/prompt-of` on the six fixture screens in
`.crew/quests/crew-talk-v1/screens/`. **It parses all six correctly** (command text, kind `:bash`,
not risky). The `:unknown` lines Michael saw came from the other screens: Herdr reported
`blocked` while Claude was only thinking, and v0 turned *any* blocked screen into a prompt (no
dialog → `:unknown`, built from the last 6 lines, i.e. the `· Cogitating… (5m 16s …)` status
line). So v1:

1. **Keeps** `parse-screen` / `prompt-of` / `with-prompts` as they are (the v0 tests t01–t14 stay
   green) and **locks the six real screens in with tests**.
2. **Adds a gate**, `prompt-screen?`: a screen counts as a prompt only when it ends in a real
   permission dialog (a `Do you want to …?` line followed by the numbered options block, then
   only the footer). `read-prompt` = gate + `prompt-of` (plus a readable text for `:unknown`). The
   watcher **and** the bridge use `read-prompt`. Not a dialog → the member is reported `:working`.
3. **Adds the two-poll rule** in the watcher (`observe-prompts`): a prompt is published only when
   the same prompt id is on screen on two consecutive polls ≥ 2000 ms apart. The published prompt
   carries `:since` (ms, first sighting).
4. **Adds the fielding policy** in the game: Hal's driver answers by default; a prompt becomes a
   💭 line with boxes (and the red badge) only when it is **surfaced**: risky, `:unknown`, waiting
   ≥ `:surface-after-s` (60, in `resources/estate.edn`), or the "I'll answer" switch is on.
5. **Adds two state files**: `state/crew-log.edn` (who answered what; written by Hal's driver and
   by the bridge) and `state/hold.edn` (the switch; written only by the bridge's new `hold`
   action).

```
herdr ─list/read─▶ bb crew-status ──▶ state/crew.edn ─────────poll 2 s─▶ game: pm.crew/parse
                   pm.prompt/observe-prompts                              pm.talk/observe-crew (policy)
                   (read-prompt + two-poll, :since)                       → surfaced prompt lines (boxes, badge)
Hal's driver ─(answers easy prompts unless hold)─▶ state/crew-log.edn ─poll 2 s─▶ pm.crew-log/parse
             ◀─────────── reads state/hold.edn                            pm.talk/observe-log
                                                                          → quiet lines, "already answered by …"
bb crew-bridge (127.0.0.1:8091)                                           game: tick / ✕ / reply / say /
  POST /crew/<m> allow|deny|say  (re-reads screen NOW with read-prompt)  ◀──── "I'll answer" switch
  POST /hold {"on":bool} → state/hold.edn ─────────────────────poll 2 s─▶ pm.crew-log/parse-hold
  on a successful allow/deny → appends {:by :michael} to state/crew-log.edn
```

**The bridge still decides** whether a prompt may be allowed (live re-read, its own risky check).
The game's `surfaced?` only decides what Michael is shown. Nothing about risky classification
moves into the game.

**Never send keys or prompts to crew members, never start the bridge or the watcher against the
live crew** (builder and tester alike). Hal tests live.

## 1. Who touches what

| Who | May touch |
|---|---|
| **Builder** (un-automate-monde) | `src/pm/prompt.cljc`, `src/pm/bridge.cljc`, **new** `src/pm/crew_log.cljc` (ns `pm.crew-log`), `scripts/crew_status.clj`, `scripts/crew_bridge.clj`, `src/pm/crew.cljs`, `src/pm/talk.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs`, `public/index.html`, `resources/estate.edn` (only: add the top-level `:crew-talk` key, §2.5) |
| **Tester** (le-testeur) | **new** `test/pm/crew_talk_v1_test.cljs` (ns `pm.crew-talk-v1-test`), nothing else |
| **Docs** (le-docteur) | `docs/GDD.md`: one short paragraph (≤ 5 lines) under the **Talk** verb (§2, the table row "Click an agent and a speech bubble opens…") stating the policy: Hal fields the crew's prompts, Michael sees risky / unreadable / overdue ones, the "I'll answer" switch takes over. Nothing else. |
| **Hal** (outside the repo) | his round driver: writes `state/crew-log.edn` per §2.3, reads `state/hold.edn`, holds off while `:on true`; restarts services; all live checks |
| **Nobody** this round | `shadow-cljs.edn`, `.gitignore` (`/state/` is already ignored), `src/pm/dev_http.clj` (its `/state/<name>.edn` regex already serves `crew-log.edn` and `hold.edn`), `bb.edn`, `.crew/crew.json`, `test/pm/fixtures.cljs`, **all existing tests** (incl. `test/pm/crew_talk_test.cljs`), the fixture screens, `public/vendor/`, `resources/content/`, `assets/private/`, `public/kit/` |

The existing tests must keep passing unchanged. In particular `pm.crew-talk-test` asserts:
`b/actions` = `#{"allow" "deny" "say"}` (t15), exact `:crew-last` maps such as
`{:status :done :prompt-id nil}`, exact `crew-roster` rows (3-arity), `prompt-of` on the
one-option `dialog` helper returning `:bash`, `(= T0 (restore (save-text T0)))`, and the
3-arity `observe-crew` putting a line up for a benign prompt. Every compatibility rule below
exists for one of these.

## 2. Data shapes (EDN)

### 2.1 `state/crew.edn` (watcher) — one new key on a prompt

```clojure
{:as-of 1794013202000
 :agents {:le-testeur        {:status :blocked
                              :prompt {:id "8d319583b6740bf75c9a1f4ba183adb09767f717"
                                       :kind :bash
                                       :text "grep -n \"File:\" /tmp/full-compile.txt"
                                       :risky false
                                       :since 1794013200000}}   ; NEW: first poll that saw this id
          :un-automate-monde {:status :working}}                 ; blocked on Herdr, but not a dialog
 :titles {:le-testeur "Le Testeur" :un-automate-monde "Un Automate (monde)"}}
```

- `:prompt` now appears only on a member that is `:blocked` **and** whose screen passed
  `prompt-screen?` on two consecutive polls ≥ 2000 ms apart with the same id.
- `:since` is a non-negative integer (ms). Old files without it stay valid.

### 2.2 Parsed prompt (`pm.prompt/read-prompt`)

```clojure
{:id "06efeed48734418f09576328d1ea39249fa7811e" :kind :bash
 :text "npx shadow-cljs compile scratch 2>&1 | tail -30 && node out/scratch.js" :risky false}
;; a real dialog the parser can't read: context lines + the question, joined with " · "
{:id "6a049a2c7e97372a1caad99f23478183fe038e13" :kind :unknown
 :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true}
```

### 2.3 `state/crew-log.edn` (Hal's driver and the bridge) — NEW

An EDN **vector**, oldest first, append-only, at most 200 entries (older ones dropped). Each:

```clojure
[{:at 1794013205000 :member :le-testeur :action :allowed :by :hal
  :prompt-id "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787" :text "ls"}
 {:at 1794013209000 :member :un-automate-monde :action :declined :by :michael
  :prompt-id "06b65e49c507048bccab6121f64aaad2518f3b83" :text "curl --version"}]
```

| key | rule |
|---|---|
| `:at` | integer ms ≥ 0, **strictly increasing** through the file (a writer bumps a tie or an earlier clock to last `:at` + 1; `append` does it) |
| `:member` | keyword, name matches `[a-z][a-z0-9_-]{0,31}` |
| `:action` | `:allowed` or `:declined` |
| `:by` | `:hal` (the driver) or `:michael` (the bridge, for a game click) |
| `:prompt-id` | 40 lowercase hex: the `:id` of the prompt that was answered (= `pm.prompt/prompt-id`; the driver can load `pm.prompt` with `bb -cp src`) |
| `:text` | the command (`:text` of the prompt), non-blank string ≤ 400 chars |

Writers read the file, `pm.crew-log/append`, and write it atomically (`.tmp` + rename). Extra
keys are dropped by `parse`. No notes, no screens, no tokens.

### 2.4 `state/hold.edn` (bridge only) — NEW

```clojure
{:on true :at 1794013200000}
```

Written by the bridge's `hold` action only. Missing or unreadable file = off. Hal's driver
answers nothing while `:on` is `true`. The game's switch shows this file, never a local guess.

### 2.5 Policy data, `resources/estate.edn` — NEW top-level key

```clojure
 :crew-talk {:surface-after-s 60}   ; a prompt Hal hasn't answered after this long goes to Michael
```

### 2.6 Talk (`pm.talk`)

`empty-talk` gains `:log-at 0` (the `:at` of the newest crew-log entry already folded in):

```clojure
{:entries [] :n 0 :seen 0 :open? false :voice? false :queue-end 0
 :last-hal nil :last-say {} :crew-last {} :log-at 0}
```

`crew-kinds` becomes `#{:crew-prompt :crew-status :crew-say :crew-log}`. A `:crew-prompt` entry
may carry `:by` (`:hal`/`:michael`: who answered it when it wasn't Michael from this line). New
quiet entry kind (no show keys, never captioned, never counted as unread):

```clojure
{:n 7 :who "Hal" :text "allowed `ls` for Le Testeur" :at 1794013205000 :kind :crew-log
 :member :le-testeur
 :log {:at 1794013205000 :member :le-testeur :action :allowed :by :hal
       :prompt-id "2b56b6f27f550a2e229f0b87f7e4b1d57f84c787" :text "ls"}}
```

Policy map passed to `observe-crew`: `{:hold? false :surface-after-s 60}`.

### 2.7 Bridge `hold` request

```clojure
;; POST http://127.0.0.1:8091/hold, Origin http://localhost:8080
{"token" "<64 hex>" "action" "hold" "on" true}
;; validate → {:ok true :action :hold :on true}
;; reply    → 200 {"ok" true "result" "on"}      ("off" for false)
```

## 3. Public functions

All new functions are pure; `now` is always an argument (ms).

### 3.1 `pm.prompt` (changed, `src/pm/prompt.cljc`)

Unchanged: `max-text`, `tail-lines`, `parse-screen`, `cap-text`, `prompt-id`, `risky?`,
`prompt-of`, `screens-to-read`, `with-prompts`.

New private regexes (exact):

```clojure
(def ^:private question-re #"^Do you want to .+\?$")
(def ^:private numbered-re #"^(?:❯\s*)?(\d{1,2})\.\s+\S.*$")
(def ^:private footer-re   #"^Esc to cancel\b.*$")
(def ^:private context-stop-re #"^[─╌]{3,}$")
```

**`(def confirm-ms 2000)`**

**`(prompt-screen? screen)` → boolean**
1. Not a string → `false`.
2. `ls` = `(mapv strip-line (str/split-lines screen))` (v0's `strip-line`: trims, strips `│ … │`
   sides, blanks `╭─╮`/`╰─╯` edges).
3. Drop lines **from the end** while they are blank or match `footer-re`.
4. `q` = index of the **last** line matching `question-re`. None → `false`.
5. `opts` = the lines after `q` (to the end of the trimmed `ls`). `true` iff all hold:
   - `opts` is non-empty and contains no blank line;
   - `(first opts)` matches `numbered-re` with number 1;
   - the lines matching `numbered-re` carry 1, 2, …, k in order (consecutive, no gaps), k ≥ 2;
     every other line in `opts` is a continuation of the option above it.

The `❯` cursor may sit on any option. A dialog with only `1. Yes` (k = 1) is **not** a prompt.

**`(read-prompt screen repo)` → prompt map or nil**
- `(prompt-screen? screen)` false → `nil`.
- Else `p` = `(prompt-of screen repo)`. If `(:kind p)` is `:bash` → `p` (identical to v0, same id).
- Else the readable unknown: `ctx` = up to 3 non-blank lines immediately before the question line
  (walking upward; stop at a blank line or a line matching `context-stop-re`), in screen order;
  `t` = `(str/join " · " (conj ctx question-line))`;
  return `{:id (prompt-id :unknown t) :kind :unknown :text (cap-text t) :risky true}`.

**`(observe-prompts agents screens repo seen now)` → `{:agents agents' :seen seen'}`**
- `agents`: `{member {:status kw}}` (from `pm.bridge/agent-statuses`).
- `screens`: `{member screen-string-or-nil}` for the members `screens-to-read` picked.
- `seen`: the previous call's `:seen` (`{member {:id hex :at ms}}`), or `nil` on the first poll.
- For each `[m v]` of `agents`:

| case | `agents'` m | `seen'` m |
|---|---|---|
| status not `:blocked` | `v` unchanged | absent |
| blocked, `(get screens m)` not a string (not read, read failed, not in roster) | `v` unchanged (`{:status :blocked}`, no prompt) | absent |
| blocked, `read-prompt` → nil (not a dialog: thinking, phantom) | `{:status :working}` | absent |
| blocked, prompt `p`; `(get seen m)` has the same `:id` and `now − (:at s)` ≥ `confirm-ms` | `{:status :blocked :prompt (assoc p :since (:at s))}` | `s` (unchanged) |
| blocked, prompt `p`; same `:id` but `now − (:at s)` < `confirm-ms` | `{:status :working}` | `s` (unchanged) |
| blocked, prompt `p`; no `s`, or `s` has another `:id` | `{:status :working}` | `{:id (:id p) :at now}` |

`seen'` holds only the members in the last three rows (so "consecutive" means a poll without the
dialog resets the clock). Other keys of `v` are kept as they are.

### 3.2 `pm.crew-log` (new, `src/pm/crew_log.cljc`, pure, bb + cljs, **may** be required by `pm.core`)

Requires only `clojure.string` and `#?(:clj clojure.edn :cljs cljs.reader)` for reading.

```clojure
(def max-entries 200)
(def max-text 400)
(def actions #{:allowed :declined})
(def bys #{:hal :michael})
```

- **`(valid-entry? e)` → boolean** per the table in §2.3 (`:at` an integer ≥ 0; floats fail).
- **`(parse text)` → vector or nil.** Not a string, unreadable, or not a vector → `nil`. Else the
  valid entries, each `select-keys`'d to `[:at :member :action :by :prompt-id :text]`, in file
  order, last `max-entries`.
- **`(entry now member action by prompt)` → map**
  `{:at now :member member :action action :by by :prompt-id (:id prompt) :text (:text prompt)}`
  (no validation; `append` validates).
- **`(append log e)` → vector.** `base` = `(vec (filter valid-entry? log))` (`nil` log → `[]`).
  If `e` is not `valid-entry?` → `base`. Else, when `base` is non-empty and `(:at e)` ≤ the last
  `:at`, `e` gets `:at` = last `:at` + 1. Result: `(vec (take-last max-entries (conj base e)))`.
- **`(for-member log member n)` → vector**: the last `n` entries of `log` whose `:member` is
  `member`, in file order. `n` ≤ 0 or nothing found → `[]`.
- **`(hold-edn on now)` → `{:on (boolean on) :at now}`** (`(boolean nil)` = false).
- **`(parse-hold text)` → `{:on bool :at int}` or nil**: readable map with boolean `:on` and integer
  `:at` ≥ 0; extra keys dropped; anything else `nil`.
- **`(hold-on? hold)` → `(true? (:on hold))`.**

### 3.3 `pm.bridge` (changed, `src/pm/bridge.cljc`)

- `(def hold-uri "/hold")`. `actions` stays `#{"allow" "deny" "say"}`.
- **`validate`**, new order (the v0 path is unchanged for `/crew/<m>`):
  1. method ≠ `"POST"` → `{:ok false :status 405 :reason "method not allowed"}`
  2. uri is neither `/crew/<m>` (v0 `uri-re`) nor exactly `"/hold"` → 404 `"not found"`
  3. body not a map → 400 `"bad json"`
  4. token → 401 `"bad token"`
  5. origin → 403 `"bad origin"`
  6. uri `"/hold"`: `(get body "action")` ≠ `"hold"` → 400 `"bad action"`; `(get body "on")` not a
     boolean → 400 `"bad on"`; else `{:ok true :action :hold :on <bool>}`. (`:members` is not
     consulted.)
  7. uri `/crew/<m>`: exactly v0 (so `"action":"hold"` there → 400 `"bad action"`).
- **`log-line`**: also prints `hold` for action `:hold`/`"hold"` (v0 prints `?` for anything not
  in `actions`). Everything else unchanged.
- `decide` unchanged (the script handles `:hold` without it, §3.4).

### 3.4 `scripts/crew_bridge.clj` (changed; not unit-tested)

- `live-for`: use `pr/read-prompt` instead of `pr/prompt-of` (a thinking screen → prompt `nil` →
  `decide` answers 409 `"prompt changed"`).
- `(:action v)` = `:hold`: no Herdr call. Write `state/hold.edn` atomically with
  `(pr-str (cl/hold-edn (:on v) now))`, log `- hold 200 on|off`, reply 200
  `{"ok":true,"result":"on"|"off"}` with the CORS headers. A write failure → 500 `"hold not saved"`.
- After a **successful** `allow` or `deny` run (incl. "declined; note not sent"): append
  `(cl/entry now member (if allow :allowed :declined) :michael live-prompt)` to
  `state/crew-log.edn` (read with `cl/parse`, `cl/append`, write `.tmp` + rename; any failure is
  logged to `crew/bridge.log` and does not change the reply).

### 3.5 `scripts/crew_status.clj` (changed; not unit-tested)

- Loop keeps `seen` across polls: `{:keys [agents seen]} (pr/observe-prompts (agents) screens
  (repo-path) seen (System/currentTimeMillis))`, where `screens` comes from `screens-to-read` as
  today. Writes `agents` as before.
- `--once` = two polls `confirm-ms` apart (poll, sleep 2000, poll), writing the second.
- Header comment: the prompt map now has 5 keys (`:since`).

### 3.6 `pm.crew` (changed)

- **`valid-prompt?`**: also requires `:since`, when present, to be an integer ≥ 0. Absent is fine.
- **`parse`**: keeps `:since` (`select-keys` `[:id :kind :text :risky :since]`).
- **`(hold-url bridge)`** → `"http://127.0.0.1:<port>/hold"`.
- **`(hold-body bridge on?)`** → `{"token" (:token bridge) "action" "hold" "on" (boolean on?)}`.

### 3.7 `pm.talk` (changed)

- `(def default-surface-after-s 60)`, `(def roster-log-n 3)`.
- **`(policy crew-talk hold)` → `{:hold? bool :surface-after-s n}`**: `crew-talk` is
  `(:crew-talk world)`; `n` = its `:surface-after-s` when a number in [0, 3600], else 60;
  `:hold?` = `(pm.crew-log/hold-on? hold)`.
- **`(surfaced? prompt now policy)` → boolean.** True iff any: `(:hold? policy)` is true;
  `(:kind prompt)` ≠ `:bash`; `(:risky prompt)` is true; `(:since prompt)` is not an integer (age
  unknown → show it); `now − since` ≥ 1000 × surface-after-s (`policy` nil or a bad value → 60).
- **`observe-crew`** gets a 4-arity `(observe-crew talk crew-state now policy)`; the 3-arity is
  `(observe-crew talk crew-state now {:hold? true :surface-after-s 60})`, which is exactly v0's
  behaviour. Per member (v0 loop, with `id` = `(:id p)` and `last-id` = `(:prompt-id last)`):
  - `eff` and `stale-pending` exactly as v0;
  - `show?` = blocked, `p` present, `(surfaced? p now policy)`;
  - append the prompt line (v0 shape and text) iff `show?` and `id` ≠ `last-id`;
  - `:crew-last` m = `{:status s :prompt-id x}` where `x` = for blocked with `p`:
    `id` if `show?` or `id` = `last-id` (already shown: sticky), else `nil`; blocked without `p`:
    `last-id`; otherwise `nil`;
  - the "done" line, `stale-missing` and "identical when unchanged" as v0.
- **`(observe-log talk log crew-state now)` → talk.** `log` nil or no new entry → `talk`
  (identical). New entries = those with `:at` > `(:log-at talk)` (missing → 0), in order. For each
  new entry `x`:
  1. **mark**: every `:crew-prompt` entry with the same `:member`, `(:id (:prompt e))` =
     `(:prompt-id x)`, `:state` in `#{:pending :sending :stale}` and no `:by`, and with
     `(:at x)` ≥ `since − 5000` where `since` = `(:since (:prompt e))` if an integer else `(:at e)`:
     `assoc :by (:by x)`, and `:pending` → `:stale` (`:sending`/`:stale` keep their state);
  2. **quiet line**: when `(:by x)` = `:hal` and `(:at x)` > `now − quiet-ms`, append (like
     `append-crew`: no show keys, `:queue-end` untouched)
     `` {:who "Hal" :text (str (name-of action) " `" (:text x) "` for " who) :at (:at x) :kind :crew-log :member (:member x) :log x} `` with `name-of` `:allowed`→`"allowed"`,
     `:declined`→`"declined"`, and `who` = `(get-in crew-state [:titles m] (name m))`;
  3. `:log-at` ← `(:at x)`.
- **`(log-label x offset-min)`** → `` "<Hal|You> <allowed|declined> `<text>` · <clock>" ``
  (`:by :hal` → "Hal", `:michael` → "You"; clock = `clock-text (:at x)`). For the roster.
- **`crew-label`**: new `:crew-log` case → `(str (:who e) " " (:text e) " · " (clock-text (:at e) off))`.
  `:crew-prompt` `:stale` → `" · already answered"` + `" by Hal"` (`:by :hal`) / `" by you"`
  (`:by :michael`) / `""`.
- **`unread`** ignores `:crew-log` entries (quiet lines never raise the badge).
- **`(gone? e crew-state now)` → boolean**: true iff `(:kind e)` ∈ `crew-kinds`, `(:member e)` is a
  keyword, `crew-state` is a map and `(crew/fresh? crew-state now)`, and the member is not present:
  present = keys of `(:titles crew-state)` when that is a map, else keys of `(:agents crew-state)`.
  Stale or nil crew state → false (don't fade everything when the watcher is down).
- **`crew-roster`** gets a 5-arity `(crew-roster crew-state now bridge-on? log expanded)`: the
  3-arity rows, each with `:expanded?` = `(contains? (or expanded #{}) m)` and `:log` =
  `(if expanded? (cl/for-member log m roster-log-n) [])`. The 3-arity is unchanged (no new keys).
- **`(hold-switch hold bridge-on? ui)` → `{:checked? :disabled? :hint}`** with `ui` =
  `{:sending? bool :error str-or-nil}` (nil ok): `:checked?` = `(cl/hold-on? hold)`;
  `:disabled?` = `(or (not bridge-on?) (true? (:sending? ui)))`; `:hint` = first of: bridge off →
  `"bridge off"`; sending → `"sending…"`; string error → `(str "not sent: " error)`; checked →
  `"you answer; Hal holds off"`; else `"Hal answers the easy ones"`.
- **`save-text`**: also writes top-level `:log-at`, and entry keys `:by :log`.
- **`restore`**: `:log-at` = saved value when an integer ≥ 0, else 0. `valid-restored-entry?`
  gets a `:crew-log` branch: keyword `:member` and `(cl/valid-entry? (:log e))` (a `case` without
  it would throw). Cleaning: `:crew-log` keeps `[:n :who :text :at :kind :member :log]`;
  `:crew-prompt` keeps `:by` when it is `:hal`/`:michael`.
- `valid-crew-last-entry?` unchanged (`:crew-last` keeps exactly v0's two keys).

### 3.8 `pm.core`, `pm.ui`, `public/index.html` (not unit-tested; Hal checks live)

- `pm.core`: poll `/state/crew-log.edn` and `/state/hold.edn` every 2 s (missing/404 → `nil`)
  into `(:crew-log @!world)` and `(:hold @!world)` via `cl/parse` / `cl/parse-hold`. The talk tick
  becomes `(-> t (talk/observe w now) (talk/observe-crew crew now (talk/policy (:crew-talk w) (:hold w)))
  (talk/observe-log (:crew-log w) crew now))`. On a 409 from the bridge nothing new is needed: the
  line goes `:stale`, and the log marks `:by`.
- The switch: a checkbox **"I'll answer"** at the top of `#talk-panel` (above `#crew-roster`),
  rendered from `hold-switch`; a click POSTs `hold-body` to `hold-url`, sets `{:sending? true}`,
  on 200 re-fetches `hold.edn` at once and clears the ui state, on failure sets `{:error reason}`.
  It never flips itself: `:checked?` always comes from `hold.edn`.
- Roster: tapping a member's name toggles it in an `expanded` set kept in `!talk-ui` (not saved);
  expanded rows list `log-label` lines under the name.
- 💭 list: `:crew-log` rows get class `crew quiet` (smaller, dimmer, no controls); any row with
  `gone?` gets class `gone` (`opacity: .35` with a 1 s transition). No palette hex in feature code
  beyond the CSS in `index.html`.

## 4. Test cases (tester: `test/pm/crew_talk_v1_test.cljs`, ns `pm.crew-talk-v1-test`)

Requires: `cljs.test`, `clojure.string :as str`, `["fs" :as fs]`, `pm.prompt :as pr`,
`pm.bridge :as b`, `pm.crew :as c`, `pm.crew-log :as cl`, `pm.talk :as tk`. Shared defs:

```clojure
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
(def L1 {:at (+ A 5000) :member :le-testeur :action :allowed :by :hal :prompt-id ID3 :text "ls"})
(def L2 {:at (+ A 7000) :member :un-automate-monde :action :declined :by :hal :prompt-id ID2 :text "curl --version"})
(def L3 {:at (+ A 9000) :member :un-automate-monde :action :allowed :by :michael :prompt-id ID1 :text "node -e 'console.log(6*7)'"})
(def TOK (apply str (repeat 16 "0a1b")))
(def CTX {:token TOK :members #{:le-testeur :un-automate-monde}})
(def OK-ORIGIN "http://localhost:8080")
(defn hq [body] {:method "POST" :uri "/hold" :origin OK-ORIGIN :body body})
(def H {"token" TOK "action" "hold" "on" true})
```

### pm.prompt: every fixture screen (command text, kind, risky)

1. **t01 F1** (`190664ad70`, header after `⎿`, option 2 "allow reading from /private/tmp"):
   `(pr/prompt-screen? F1)` → `true`; `(pr/read-prompt F1 R)` →
   `{:id IDF1 :kind :bash :text T1X :risky false}`. (Reads /tmp, writes nothing: not risky.)
2. **t02 F2** (`2929ebc6bf`, command wrapped over two lines, `⏺` header):
   `(pr/read-prompt F2 R)` → `{:id IDF2 :kind :bash :text T2X :risky false}`;
   `(:lines (pr/parse-screen F2))` → `["grep -n \"Use of undeclared Var\"" "/tmp/full-compile.txt"]`.
3. **t03 F3** (`49184610d7`, "This command requires approval" + blank before the question,
   wrapped `| tail` / `-30`): `(pr/read-prompt F3 R)` → `{:id IDF3 :kind :bash :text T3X :risky false}`
   (`2>&1` is a write marker, but no token points outside the repo).
4. **t04 F4** (`62aacd3ea6`, same command with `⏺` header): `(pr/read-prompt F4 R)` → same map as
   t03; ids equal: `(= (:id (pr/read-prompt F3 R)) (:id (pr/read-prompt F4 R)) IDF3)`.
5. **t05 F5** (`b123e48d5a`, diff lines above, different description): `(pr/read-prompt F5 R)` →
   `{:id IDF3 :kind :bash :text T3X :risky false}`.
6. **t06 F6** (`bdf0107df6`, `tail -20`): `(pr/read-prompt F6 R)` →
   `{:id IDF6 :kind :bash :text T6X :risky false}`.
7. **t07 v0's prompt-of agrees on all six**: for each Fi, `(= (pr/prompt-of Fi R) (pr/read-prompt Fi R))`;
   and with the real repo path `(pr/read-prompt F1 "/Users/x/src/prime-meridian")` →
   same as t01 (risky false).
8. **t08 staged risky from a real screen**: `(pr/read-prompt (str/replace F1 T1X "curl --version") R)`
   → `{:id ID2 :kind :bash :text "curl --version" :risky true}`;
   `(pr/read-prompt (str/replace F6 "node out/scratch.js" "rm -rf out") R)` has
   `:kind :bash`, `:risky true`, `:text "npx shadow-cljs compile scratch 2>&1 | tail -20 && rm -rf out"`.

### pm.prompt: the gate (thinking / phantom screens rejected)

9. **t09 thinking screens**: for each of TH1 TH2 TH3 TH4 TH5 TH6: `(pr/prompt-screen? s)` → `false`
   and `(pr/read-prompt s R)` → `nil`. (TH1/TH2: status line only; TH3: an old dialog with output
   after it; TH4: question in prose, no option 1; TH5: one option; TH6: options 1, 3.)
   Contrast, v0 still returns `:unknown` for TH1: `(:kind (pr/prompt-of TH1 R))` → `:unknown`.
10. **t10 non-strings**: `(pr/prompt-screen? nil)`, `(pr/prompt-screen? 42)`, `(pr/prompt-screen? "")`
    → `false`; `(pr/read-prompt nil R)` and `(pr/read-prompt "" R)` → `nil`.
11. **t11 accepted variants**:
    - cursor on the last option: `(pr/read-prompt (-> F3 (str/replace "❯ 1. Yes" "  1. Yes") (str/replace "   4. No" " ❯ 4. No")) R)` → same as t03;
    - no footer: `(pr/read-prompt (str/replace F1 " Esc to cancel · Tab to amend" "") R)` → same as t01;
    - trailing blank lines: `(pr/read-prompt (str F2 "\n\n\n") R)` → same as t02;
    - v0's box variant: `(pr/prompt-screen? (lines "╭────╮" "│ Bash command │" "│ ╌╌╌╌ │" "│ ls │" "│ ╌╌╌╌ │" "│ Do you want to proceed? │" "│ ❯ 1. Yes │" "│   2. No │" "╰────╯"))` → `true`, and `read-prompt` of it → `{:id ID3 :kind :bash :text "ls" :risky false}`.
12. **t12 readable unknowns**:
    - `(pr/read-prompt EDIT R)` → `{:id IDE :kind :unknown :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true}`;
    - `(pr/read-prompt FETCH R)` → `{:id IDFE :kind :unknown :text "Fetch · https://example.com/notes · Do you want to allow Claude to fetch this content?" :risky true}`;
    - context stops at a rule: `(pr/read-prompt (lines " Bash command" " grep -n x" " ╌╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. No") R)` →
      `{:id IDQ :kind :unknown :text "Do you want to proceed?" :risky true}` (one rule only, so not `:bash`);
    - at most 3 context lines: `(:text (pr/read-prompt (lines "" " a" " b" " c" " d" " Do you want to go?" " ❯ 1. Yes" "   2. No") R))` → `"b · c · d · Do you want to go?"`.
13. **t13 constant**: `pr/confirm-ms` → `2000`.

### pm.prompt: the two-poll rule (`observe-prompts`)

Agents `AG` = `{:le-testeur {:status :blocked} :un-automate-monde {:status :blocked} :le-critique {:status :idle}}`,
screens `SC` = `{:le-testeur F1 :un-automate-monde F3}`.

14. **t14 first sighting**: `(pr/observe-prompts AG SC R nil A)` →
    `{:agents {:le-testeur {:status :working} :un-automate-monde {:status :working} :le-critique {:status :idle}}
      :seen {:le-testeur {:id IDF1 :at A} :un-automate-monde {:id IDF3 :at A}}}`.
15. **t15 second poll, 2000 ms later → published with `:since`**: with `S1` = the `:seen` of t14,
    `(pr/observe-prompts AG SC R S1 (+ A 2000))` →
    `{:agents {:le-testeur {:status :blocked :prompt {:id IDF1 :kind :bash :text T1X :risky false :since A}}
               :un-automate-monde {:status :blocked :prompt {:id IDF3 :kind :bash :text T3X :risky false :since A}}
               :le-critique {:status :idle}}
      :seen S1}`; a third poll at `(+ A 4000)` gives the same `:agents` (`:since` still `A`).
16. **t16 too soon**: `(pr/observe-prompts AG SC R S1 (+ A 1999))` → both `{:status :working}`, `:seen S1`.
17. **t17 different prompt resets**: `(pr/observe-prompts AG {:le-testeur F1 :un-automate-monde F6} R S1 (+ A 2000))` →
    `:agents` `:le-testeur` published (`:since A`), `:un-automate-monde {:status :working}`;
    `:seen {:le-testeur {:id IDF1 :at A} :un-automate-monde {:id IDF6 :at (+ A 2000)}}`.
18. **t18 phantom is `:working`, and breaks the chain**:
    - `(pr/observe-prompts AG {:le-testeur TH1 :un-automate-monde TH2} R nil A)` →
      `{:agents {:le-testeur {:status :working} :un-automate-monde {:status :working} :le-critique {:status :idle}} :seen {}}`;
    - dialog at A, thinking at A+2000, dialog at A+4000, dialog at A+6000 for `:le-testeur`
      (screens `{:le-testeur F1}`, `{:le-testeur TH1}`, `{:le-testeur F1}`, `{:le-testeur F1}`, agents `{:le-testeur {:status :blocked}}`, threading `:seen`):
      statuses `:working :working :working :blocked`, and the last prompt's `:since` = `(+ A 4000)`.
19. **t19 unread / not blocked**:
    `(pr/observe-prompts {:le-testeur {:status :blocked} :hal {:status :blocked} :un-automate-monde {:status :working}}
                         {:le-testeur nil} R {:un-automate-monde {:id IDF3 :at A}} (+ A 2000))` →
    `{:agents {:le-testeur {:status :blocked} :hal {:status :blocked} :un-automate-monde {:status :working}} :seen {}}`.

### pm.crew-log

20. **t20 valid-entry?**: `true` for L1, L2, L3 and for `(assoc L1 :text (apply str (repeat 400 "x")))`;
    `false` for: `(dissoc L1 :prompt-id)`, `(assoc L1 :action :allow)`, `(assoc L1 :by :le-testeur)`,
    `(assoc L1 :at -1)`, `(assoc L1 :at 1.5)`, `(assoc L1 :text "")`, `(assoc L1 :text "  ")`,
    `(assoc L1 :text (apply str (repeat 401 "x")))`, `(assoc L1 :member "le-testeur")`,
    `(assoc L1 :member :Bad)`, `(assoc L1 :prompt-id "ABC")`, `nil`, `[]`.
21. **t21 parse**: `(cl/parse (pr-str [L1 {:bad 1} L2]))` → `[L1 L2]`;
    `(cl/parse (pr-str [(assoc L1 :note "x")]))` → `[L1]`; `(cl/parse "{:a 1}")`, `(cl/parse "")`,
    `(cl/parse "[1 2")`, `(cl/parse nil)` → `nil`; `(cl/parse "[]")` → `[]`;
    205 entries `(mapv #(assoc L1 :at (+ A %)) (range 205))` → parse gives 200 entries, first `:at` `(+ A 5)`.
22. **t22 entry and append**:
    `(cl/entry (+ A 5000) :le-testeur :allowed :hal {:id ID3 :kind :bash :text "ls" :risky false})` → `L1`;
    `(cl/append [L1] L2)` → `[L1 L2]`; `(cl/append nil L1)` → `[L1]`;
    tie: `(cl/append [L1] (assoc L2 :at (+ A 5000)))` → `[L1 (assoc L2 :at (+ A 5001))]`;
    earlier clock: `(cl/append [L1] (assoc L2 :at (+ A 1000)))` → `[L1 (assoc L2 :at (+ A 5001))]`;
    invalid new entry: `(cl/append [L1] (assoc L2 :by :x))` → `[L1]`;
    invalid old entries dropped: `(cl/append [{:bad 1} L1] L2)` → `[L1 L2]`;
    cap: `(count (cl/append (mapv #(assoc L1 :at (+ A %)) (range 200)) (assoc L2 :at (+ A 999))))` → `200`,
    whose first `:at` is `(+ A 1)` and last is `(assoc L2 :at (+ A 999))`; `cl/max-entries` → `200`.
23. **t23 for-member**: `(cl/for-member [L1 L2 L3] :un-automate-monde 3)` → `[L2 L3]`;
    `(cl/for-member [L1 L2 L3] :un-automate-monde 1)` → `[L3]`;
    `(cl/for-member [L1 L2 L3] :le-testeur 3)` → `[L1]`; `:le-coureur` → `[]`; n `0` → `[]`; log `nil` → `[]`.
24. **t24 hold file**: `(cl/hold-edn true 5)` → `{:on true :at 5}`; `(cl/hold-edn nil 5)` → `{:on false :at 5}`;
    `(cl/parse-hold "{:on true :at 5}")` → `{:on true :at 5}`; `(cl/parse-hold "{:on false :at 5 :x 1}")` → `{:on false :at 5}`;
    `nil` for `"{:on \"yes\" :at 5}"`, `"{:on true}"`, `"{:on true :at -1}"`, `"[true]"`, `"{:on"`, `nil`;
    `(cl/hold-on? nil)` → `false`, `(cl/hold-on? {:on false :at 5})` → `false`, `(cl/hold-on? {:on true :at 5})` → `true`.

### pm.bridge: the `hold` action

25. **t25 valid hold**: `(b/validate (hq H) CTX)` → `{:ok true :action :hold :on true}`;
    `(b/validate (hq (assoc H "on" false)) CTX)` → `{:ok true :action :hold :on false}`;
    `(b/validate (hq H) {:token TOK :members #{}})` → `{:ok true :action :hold :on true}`; `b/hold-uri` → `"/hold"`.
26. **t26 refusals, in order**:
    - `(assoc H "on" "true")` → `{:ok false :status 400 :reason "bad on"}`; `(dissoc H "on")` → same;
    - `(assoc H "action" "allow")` → `{:ok false :status 400 :reason "bad action"}`;
    - `(assoc H "token" (apply str (repeat 64 "f")))` → 401 `"bad token"`;
    - `(assoc (hq H) :origin "http://evil.example")` → 403 `"bad origin"`; origin `nil` → 403;
    - `(assoc (hq H) :method "GET")` → 405 `"method not allowed"`;
    - `(assoc (hq H) :body :bad-json)` → 400 `"bad json"`;
    - `(assoc (hq H) :uri "/hold/x")` → 404 `"not found"`; `"/HOLD"` → 404;
    - precedence: bad token + bad on → 401; bad origin + bad action → 403;
    - `"action":"hold"` on a member: `(b/validate {:method "POST" :uri "/crew/le-testeur" :origin OK-ORIGIN :body {"token" TOK "action" "hold" "on" true}} CTX)` → 400 `"bad action"`;
    - v0 unchanged: `b/actions` → `#{"allow" "deny" "say"}`.
27. **t27 log-line**: `(b/log-line "2026-11-08T17:00:00Z" nil :hold 200 "on")` → `"2026-11-08T17:00:00Z - hold 200 on"`;
    with `"hold"` (string) → same; `(b/log-line "t" :le-testeur :allow 200 "allowed")` → `"t le-testeur allow 200 allowed"`.

### pm.crew

28. **t28 `:since`**: `(c/valid-prompt? QB)` → `true`; `(c/valid-prompt? (dissoc QB :since))` → `true`;
    `false` for `(assoc QB :since -1)`, `(assoc QB :since "x")`, `(assoc QB :since 1.5)`;
    `(c/parse (pr-str {:as-of 5 :agents {:a {:status :blocked :prompt (assoc QB :since 3)}}}))` →
    `{:as-of 5 :agents {:a {:status :blocked :prompt (assoc QB :since 3)}}}`;
    with `:since -1` → `{:as-of 5 :agents {:a {:status :blocked}}}`.
29. **t29 hold request**: B = `{:port 8091 :token TOK :as-of 1}`: `(c/hold-url B)` → `"http://127.0.0.1:8091/hold"`;
    `(c/hold-body B true)` → `{"token" TOK "action" "hold" "on" true}`; `(c/hold-body B nil)` → `{"token" TOK "action" "hold" "on" false}`.

### pm.talk: policy and `:surface-after-s`

30. **t30 policy**: `(tk/policy {:surface-after-s 30} {:on true :at 5})` → `{:hold? true :surface-after-s 30}`;
    `(tk/policy nil nil)` → `{:hold? false :surface-after-s 60}`;
    `(tk/policy {:surface-after-s -1} {:on false :at 5})`, `{:surface-after-s 3601}`, `{:surface-after-s "x"}` → `:surface-after-s 60`;
    `{:surface-after-s 3600}` → 3600; `{:surface-after-s 0}` → 0; `tk/default-surface-after-s` → 60.
31. **t31 surfaced?**: `(tk/surfaced? QB (+ A 59999) POL)` → `false`; `(+ A 60000)` → `true`;
    `(tk/surfaced? QB A HOLD)` → `true`; `(tk/surfaced? QR A POL)` → `true`; `(tk/surfaced? QU A POL)` → `true`;
    `(tk/surfaced? QN A POL)` → `true` (no `:since`); `(tk/surfaced? QB A {:hold? false :surface-after-s 0})` → `true`;
    `(tk/surfaced? QB A nil)` → `false`; `(tk/surfaced? QB (+ A 60000) nil)` → `true`;
    `(tk/surfaced? QB (+ A 30000) {:hold? false :surface-after-s 30})` → `true`.

### pm.talk: quiet vs surfaced lines (`observe-crew` 4-arity)

`O1` = `(tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt QB} :un-automate-monde {:status :blocked :prompt QR}}) A POL)`.

32. **t32 risky surfaces, benign stays quiet**: `(:entries O1)` →
    `[{:n 1 :who "Un Automate (monde)" :text "wants to run curl --version" :at A :kind :crew-prompt
       :member :un-automate-monde :prompt QR :state :pending}]`;
    `(:crew-last O1)` → `{:le-testeur {:status :blocked :prompt-id nil} :un-automate-monde {:status :blocked :prompt-id ID2}}`;
    `(tk/prompt-waiting? O1)` → `true`; `(tk/unread O1)` → `1`.
33. **t33 still quiet at 30 s, identical**: `(identical? O1 (tk/observe-crew O1 (crew (+ A 30000) (:agents (crew A {:le-testeur {:status :blocked :prompt QB} :un-automate-monde {:status :blocked :prompt QR}}))) (+ A 30000) POL))` → `true`.
34. **t34 overdue at 60 s**: `O3` = same agents, crew as-of `(+ A 60000)`, now `(+ A 60000)`, POL, from O1:
    `(e O3 2)` → `{:n 2 :who "Le Testeur" :text "wants to run ls" :at (+ A 60000) :kind :crew-prompt
    :member :le-testeur :prompt QB :state :pending}`; `(get-in O3 [:crew-last :le-testeur])` →
    `{:status :blocked :prompt-id ID3}`; the same call again at `(+ A 62000)` (crew as-of `(+ A 62000)`) → identical to O3.
35. **t35 unknown surfaces at once**: `(tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt QU}}) A POL)` →
    entry 1 has `:text "is waiting at a prompt: Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?"`,
    `:state :pending`; `(tk/allowable? (e it 1))` → `false`.
36. **t36 hold surfaces everything; 3-arity = hold**:
    `(tk/observe-crew O1 (crew (+ A 2000) <O1's agents>) (+ A 2000) HOLD)` → `(e it 2)` is the `:le-testeur` line with `:at (+ A 2000)`;
    for `C` = `(crew A {:le-testeur {:status :blocked :prompt QB}})`: `(= (tk/observe-crew T0 C A) (tk/observe-crew T0 C A HOLD))` → `true`.
37. **t37 sticky after hold goes off, no duplicate when it comes back**: `S1` = `(tk/observe-crew T0 (crew A {:le-testeur {:status :blocked :prompt QB}}) A HOLD)` (1 entry);
    `S2` = same agents at `(+ A 2000)` with POL → `(identical? S1 S2)` → `true`;
    `S3` = same agents at `(+ A 4000)` with HOLD → `(identical? S2 S3)` → `true`.
38. **t38 Hal answered a quiet one**: `(tk/observe-crew O1 (crew (+ A 2000) {:le-testeur {:status :working} :un-automate-monde {:status :blocked :prompt QR}}) (+ A 2000) POL)` →
    `(count (:entries it))` → `1`; `(get-in it [:crew-last :le-testeur])` → `{:status :working :prompt-id nil}`.

### pm.talk: the log (`observe-log`) and "already answered by …"

`C` = `(crew A {:le-testeur {:status :idle} :un-automate-monde {:status :blocked}})` (titles TI).

39. **t39 a quiet line, no badge**: `G1` = `(tk/observe-log T0 [L1] C (+ A 6000))`:
    `(:entries G1)` → `` [{:n 1 :who "Hal" :text "allowed `ls` for Le Testeur" :at (+ A 5000) :kind :crew-log :member :le-testeur :log L1}] ``;
    `(:log-at G1)` → `(+ A 5000)`; `(tk/unread G1)` → `0`; `(tk/badge-text G1)` → `""`; `(tk/prompt-waiting? G1)` → `false`;
    `(tk/crew-label (e G1 1) OFF)` → `` "Hal allowed `ls` for Le Testeur · 5:00 PM" ``; `(tk/crew-controls (e G1 1) true)` → `nil`.
40. **t40 idempotent, ordered, Michael's own entries make no line**:
    `(identical? G1 (tk/observe-log G1 [L1] C (+ A 8000)))` → `true`;
    `G2` = `(tk/observe-log G1 [L1 L2 L3] C (+ A 10000))`: `(count (:entries G2))` → `2`;
    `(e G2 2)` → `` {:n 2 :who "Hal" :text "declined `curl --version` for Un Automate (monde)" :at (+ A 7000) :kind :crew-log :member :un-automate-monde :log L2} ``;
    `(:log-at G2)` → `(+ A 9000)`; `(tk/crew-label (e G2 2) OFF)` → `` "Hal declined `curl --version` for Un Automate (monde) · 5:00 PM" ``;
    `(identical? T0 (tk/observe-log T0 nil C A))` and `(identical? T0 (tk/observe-log T0 [] C A))` → `true`;
    titles missing: `(:text (e (tk/observe-log T0 [L1] nil (+ A 6000)) 1))` → `` "allowed `ls` for le-testeur" ``.
41. **t41 old entries advance `:log-at` but add no line**: `(tk/observe-log T0 [L1] C (+ A 5000 1800000))` →
    `:entries []`, `:log-at (+ A 5000)`; at `(+ A 5000 1799999)` → one entry.
42. **t42 Hal first → Michael's surfaced line reads "already answered by Hal"**:
    `M` = `(tk/observe-log O1 [L2] C (+ A 8000))`: `(e M 1)` → its `:state :stale` and `:by :hal`;
    `(e M 2)` is the `:crew-log` line; `(tk/prompt-waiting? M)` → `false`; `(tk/unread M)` → `1` (the prompt line only);
    `(tk/crew-label (e M 1) OFF)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by Hal"`;
    `(tk/crew-controls (e M 1) true)` → `nil`.
43. **t43 Michael clicked second (409 first, log after)**:
    `X` = `(-> O1 (tk/crew-send 1 :deny nil) (tk/crew-result 1 {:result :stale :reason "prompt changed"} (+ A 7500)))`:
    `(tk/crew-label (e X 1) OFF)` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered"`;
    after `(tk/observe-log X [L2] C (+ A 8000))` → `"⚠ Un Automate (monde) · wants to run curl --version · already answered by Hal"`.
    A `:sending` line is marked but keeps its state: `(:state (e (tk/observe-log (tk/crew-send O1 1 :deny nil) [L2] C (+ A 8000)) 1))` → `:sending`, `:by :hal`.
44. **t44 "already answered by you"**: `Y` = `(tk/observe-crew T0 (crew A {:un-automate-monde {:status :blocked :prompt QN}}) A HOLD)`;
    `(tk/observe-log Y [L3] C (+ A 10000))` → entry 1 `:state :stale`, `:by :michael`, entry count `1`
    (no quiet line for `:michael`), `:log-at (+ A 9000)`; its label →
    `"Un Automate (monde) · wants to run node -e 'console.log(6*7)' · already answered by you"`.
45. **t45 the time guard and the member guard**:
    `Z` = `(tk/observe-crew T0 (crew (+ A 20000) {:le-testeur {:status :blocked :prompt (assoc QB :since (+ A 20000))}}) (+ A 20000) HOLD)`;
    `(tk/observe-log Z [(assoc L1 :at (+ A 10000))] C (+ A 21000))` → entry 1 still `:pending`, no `:by`; entry 2 is the quiet line;
    `(assoc L1 :at (+ A 15000))` instead → entry 1 `:stale`, `:by :hal` (boundary, since − 5000);
    an entry for the wrong member `(assoc L1 :at (+ A 15000) :member :un-automate-monde)` → entry 1 still `:pending`;
    wrong id `(assoc L1 :at (+ A 15000) :prompt-id ID1)` → still `:pending`.
46. **t46 save / restore**: `(tk/restore (tk/save-text G2))` has `:entries` = `(:entries G2)`, `:log-at` `(+ A 9000)`, `:n 2`;
    `(e (tk/restore (tk/save-text M)) 1)` keeps `:by :hal` and `:state :stale`;
    `(= T0 (tk/restore (tk/save-text T0)))` → `true`; `(:log-at T0)` → `0`;
    a saved `:crew-log` entry with `:log` `(assoc L1 :by :x)` is dropped on restore (string built with `pr-str` of
    `{:v 1 :n 1 :seen 1 :log-at 5 :entries [{:n 1 :who "Hal" :text "x" :at A :kind :crew-log :member :le-testeur :log (assoc L1 :by :x)}]}` → `:entries []`);
    `:log-at "x"` or `-3` in a saved text → `0`.

### pm.talk: members who left, roster log, the switch

47. **t47 gone?**: `Cg` = `(crew A {:le-testeur {:status :idle}})` (titles TI):
    `(tk/gone? {:kind :crew-log :member :le-coureur} Cg A)` → `true`;
    `(tk/gone? {:kind :crew-prompt :member :le-coureur} Cg A)` → `true`;
    `(tk/gone? {:kind :crew-log :member :le-testeur} Cg A)` → `false`;
    `(tk/gone? {:kind :crew-say :member :un-architecte} Cg A)` → `false` (in titles);
    stale: `(tk/gone? {:kind :crew-log :member :le-coureur} Cg (+ A 15001))` → `false`; `nil` crew → `false`;
    a creature line `{:kind :hal :who "Hal"}` → `false`;
    no titles: `{:as-of A :agents {:le-coureur {:status :idle}}}` → `:le-coureur` `false`, `:le-testeur` `true`.
48. **t48 roster with the log**: `(tk/crew-roster C A true [L1 L2 L3] #{:un-automate-monde})` →
    `[{:member :le-testeur :who "Le Testeur" :status :idle :say? true :expanded? false :log []}
      {:member :un-architecte :who "Un Architecte" :status :unknown :say? false :expanded? false :log []}
      {:member :un-automate-monde :who "Un Automate (monde)" :status :blocked :say? false :expanded? true :log [L2 L3]}]`;
    `(tk/crew-roster C A true [L1 L2 L3] nil)` → every row `:expanded? false :log []`;
    the 3-arity `(tk/crew-roster C A true)` → rows without `:expanded?`/`:log` (`(:log (first it))` → `nil`).
49. **t49 log-label**: `(tk/log-label L1 OFF)` → `` "Hal allowed `ls` · 5:00 PM" ``;
    `(tk/log-label L3 OFF)` → `` "You allowed `node -e 'console.log(6*7)'` · 5:00 PM" ``;
    `(tk/log-label (assoc L2 :at (+ A 60000)) OFF)` → `` "Hal declined `curl --version` · 5:01 PM" ``.
50. **t50 the "I'll answer" switch from `hold.edn`**:
    `(tk/hold-switch {:on true :at 5} true {})` → `{:checked? true :disabled? false :hint "you answer; Hal holds off"}`;
    `(tk/hold-switch nil true nil)` → `{:checked? false :disabled? false :hint "Hal answers the easy ones"}`;
    `(tk/hold-switch {:on true :at 5} false {})` → `{:checked? true :disabled? true :hint "bridge off"}`;
    `(tk/hold-switch nil true {:sending? true})` → `{:checked? false :disabled? true :hint "sending…"}`;
    `(tk/hold-switch {:on false :at 5} true {:error "bad token"})` → `{:checked? false :disabled? false :hint "not sent: bad token"}`;
    round trip: `(tk/hold-switch (cl/parse-hold (pr-str (cl/hold-edn true A))) true {})` → `:checked? true`;
    and the policy follows the file: `(:hold? (tk/policy nil (cl/parse-hold (pr-str (cl/hold-edn true A)))))` → `true`.

All 50 cases cover both crew members used in the fixtures' roles (`:le-testeur` and
`:un-automate-monde`) wherever a function takes a member, so nothing is specific to one.

## 5. Notes for the builder

- `numbered-re` and `question-re` must behave the same in bb (Java regex) and cljs (JS regex):
  use only `(?:…)`, `\d`, `\s`, `\S`, `.`; no lookbehind, no flags.
- Don't change `prompt-of`/`parse-screen`: t07 relies on them agreeing with `read-prompt` on the
  fixtures, and the v0 suite relies on them as they are.
- `observe-crew`'s 4-arity must return the **identical** talk when nothing changed (t33, t37),
  as v0's does: keep the final `(if (= result talk) talk result)`.
- `observe-log` must not touch `:queue-end` or `:seen` semantics beyond what `append-crew` does.
- Hal's driver isn't in this repo; its contract is §2.3 / §2.4 only.

## 6. Live checks (Hal)

A round where Michael sees only quiet lines; a staged risky prompt (`curl --version`) surfaces
with ⚠ and the red badge; a benign prompt left unanswered surfaces after 60 s; with "I'll answer"
on, Hal's driver holds and Michael's ticks go through (log shows `:by :michael`); a thinking
member never shows a prompt; the switch survives a reload; 60 fps unchanged.
