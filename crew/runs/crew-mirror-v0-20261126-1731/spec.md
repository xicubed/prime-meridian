# Spec: the crew in your phone, mirrored with Herdr (crew-mirror-v0)

Quest: `.crew/quests/crew-mirror-v0.md`. Start commit `4bd14de`.

Herdr stays the source and the game mirrors it. This round brings five things back from the panes:

1. A Claude member's **last prose block** of a turn becomes one quiet line in its 💭 thread.
2. A turn that ends in a **question** puts a "!" on that member's row and walker.
3. **Every permission prompt** gets **Allow / No**, plus **Always** when the dialog offers it, and
   multi-line commands read correctly.
4. Prompts the fielder leaves are **forwarded to the twin's chat**, and the twin answers them with
   **`bb crew-answer`** through the bridge's prompt-id check.
5. **`hal-say --as <member>`** lines land in that member's thread.

Everything the quest records as decided stands. This spec fixes names, shapes, rules and wiring, and
works out the test values. Every expected value below was computed by running the stated rules over
the fixtures in a scratch implementation, not by eye.

---

## 0. What I found (read before building)

1. **Real screens: what a Claude Code pane looks like.** I captured these with
   `herdr agent read <m> --source visible|recent`; the fixtures are in §12.
   - **Prose:** a line starting `⏺ ` at column 0, then continuation lines indented two spaces.
     Later paragraphs of the same message also start with two spaces, after a blank line. So do
     bullet lists, with 4-space continuations, and code-ish lines with 3 spaces.
   - **Tool call:** `⏺ Update(src/pm/talk.cljs)`, `⏺ Write(…)` or `⏺ Explore(…)`, and also a prose-like
     description such as `⏺ Running 1 shell command…` or `⏺ Listing all undeclared-var warnings`. The
     line directly under it starts `  ⎿`, followed by output and diff lines.
   - **Collapsed tool group:** after a blank line, `  Read 1 file`, `  Ran 1 shell command`,
     `  Searched for 2 patterns, listed 1 directory, ran 4` (wrapped), `  Made 1 scratchpad edit +6`,
     `  Listed 1 directory · 1 failed`. These have no `⏺`, so they look like a prose paragraph until
     you read the words.
   - **Notices:** `⏺ Agent "…" finished · 25s` is a background-agent notice, not prose.
   - **Turn edges:** `❯ ` at column 0 is the user's prompt, and `✻ <Verb> for 18s · done 5:18 PM`
     is the end-of-turn footer. `※ recap: …` and a right-aligned `new task? /clear …` hint can follow
     the footer.
   - **Composer:** at the very bottom, `───…` / `❯ <typed text>` / `───…` / `⏵⏵ accept edits …`. The
     composer can hold unsent text (`❯ bb where`), and that is never prose.
2. **The multi-line Bash dialog reads wrong today.** I staged a real 15-line command on
   le-coureur (fixture `bash-multiline-prompt.txt`). Claude Code draws a **left-only border**
   `│ ` on every command line. `pm.prompt/strip-line` only strips two-sided `│…│`, so today's
   `read-prompt` returns `:kind :bash` with `"│ mkdir -p … │ for name …"` in `:text` (id
   `df88029f…`). §4.1 fixes this. That dialog also offers only `1. Yes / 2. No`, with no
   "don't ask again".
   *Side note on the staging:* the command ran. Esc didn't cancel the dialog in time, and neither
   log shows who allowed it. It only wrote two lines into `/private/tmp/pm-fixture/`, which I checked
   and removed.
3. **"Always" exists on real dialogs.** `crew-talk-v1/screens/*` offer
   `2. Yes, and don’t ask again for: node out/scratch.js` (curly ’) and
   `2. Yes, allow reading from /private/tmp from this project`. The synthetic Edit/Fetch fixtures in
   `crew_talk_v1_test` offer `2. Yes, allow all edits during this session` and
   `2. Yes, and don't ask again for example.com`. `3. Yes, and switch to auto mode` is **not** an
   always: it changes the member's whole mode.
4. **Existing tests pin today's maps.** `crew_talk_v1_test` t01–t07 compare `read-prompt` /
   `prompt-of` results with `=`, so **no new key may appear in `read-prompt`'s map**. Hence
   `:always` comes from a separate fn (§4.2) and is attached by `crew_status.clj`.
   `crew_talk_test` and `crew_fielder_v0_test` pin `b/decide :allow` refusing unknown (403
   "unknown prompt") and risky (403 "risky: your twin reviews this one"), and pin `tk/crew-controls`
   and `tk/allowable?`. These stay exactly as they are: the fielder relies on `decide :allow` as its
   safety net. The player and the twin get **new** actions, `:approve` and `:always` (§5).
   `crew_talk_test` t29 pins `crew/parse` dropping unknown prompt keys, so only a valid `:always` is
   kept (§7). `(= T0 (tk/restore (tk/save-text T0)))` is pinned, so a new talk key is restored only
   when it is non-empty.
5. **The `--as` bug.** `hal-say --as m` sends a `:say` agent-event. `world/apply-event` sets only
   `[:says m]`, the speech plate, and only the twin's say also goes to `:hal` → `talk/observe` →
   💭. So a member's line never reaches 💭. The fix is on the game side (§8.6). `hal_say.clj` needs
   only its doc text changed.
6. **Who can read what.** `pm.prompt` (cljs) needs Node's `crypto`, so it is **never** in the
   browser build. The new `pm.prose` is pure and portable, so the game may require it. The risk
   *reason* is computed on the bb side only: it goes to the twin's chat. The game keeps today's ⚠
   and "<twin> reviews this one".
7. `dev_http` serves any `state/<name>.edn` matching `[a-z0-9][a-z0-9-]{0,40}\.edn`, and `/state/`
   is gitignored. So `state/crew-said.edn` and `state/crew-forwarded.edn` need no config.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | **New:** `src/pm/prose.cljc` (ns `pm.prose`, §3); `scripts/crew_answer.clj` (§11.4). **Edit:** `src/pm/prompt.cljc` (§4: the left-border strip, `always-option`, `with-always`, `risk-reason`, `risk-reasons`); `src/pm/bridge.cljc` (§5); `src/pm/fielder.cljc` (§6, additions only; `verdict`/`due`/`attempt` unchanged); `src/pm/crew.cljs` (§7: `parse` only); `src/pm/talk.cljs` (§8); `src/pm/ui.cljs` (§9: `render-crew-row!` only); `src/pm/core.cljs` (§10); `scripts/crew_status.clj`, `scripts/crew_bridge.clj`, `scripts/crew_fielder.clj` (§11); `scripts/hal_say.clj` (header comment only, §11.5); `bb.edn` (new `crew-answer` task, and the `hal-say` doc string, §11.6); in the run dir `builder-notes.md` (§14). `resources/estate.edn` is **not** needed | `test/`, `test/fixtures/`, `resources/`, `src/pm/world.cljs`, `src/pm/events.cljc`, `src/pm/crew_log.cljc`, `public/`, `docs/`, `TASKS.md`, `crew/README.md`, `PLAY.md`, `.crew/`, `state/` (never read it except to reason about shapes), `assets/private/`, `public/kit/`. No npm deps. Don't start or restart services: Hal does |
| **tester** (le-testeur) | **New:** `test/pm/crew_mirror_test.cljs` (ns `pm.crew-mirror-test`, §13); `test/fixtures/crew-mirror/*.txt` (the five files in §12, copied verbatim). Only new tests | every existing test file and assertion; everything else. The tests are pure: no herdr, no network, no `js/Date`, no DOM. Files are read only with `fs.readFileSync` (fixtures, `.crew/quests/crew-talk-*` screens), as `crew_talk_v1_test` does |
| **le-docteur** | `crew/README.md`: the crew-talk part of **Run it** (step 2, the services paragraph: what `crew-status`, `crew-bridge` and `crew-fielder` now also do, plus `bb crew-answer`, §15), after the gate passes | everything else. **PLAY.md: no change.** The player-facing 💭 changes only with real agents and a bridge, and PLAY.md describes the demo build, which has neither |

---

## 2. Data shapes

### 2.1 `state/crew-said.edn` (new, gitignored)

Written by `scripts/crew_status.clj` and polled by the game.

```clojure
{:as-of 1794100002000
 :said {:un-automate-monde [{:at 1794099990000
                             :text "Drafted bench-south-of-house at [8.5 6.86 -30], yaw 90, …"
                             :question? false}]
        :le-coureur        [{:at 1794100001000
                             :text "The spot under the trees by the lobby has no open ground within 6 m, so I didn't place the bench there. Try the bench behind the house instead?"
                             :question? true}]}}
```

- One item per **turn end**, holding the summary (≤ 240 chars, §3.4) and never the full block.
  `:question?` is computed from the **full** block.
- At most **20** items per member, oldest dropped. `:at` strictly increases per member.
- Claude members only: Herdr's `"agent": "claude"`. Hermes members never appear.

### 2.2 `state/crew-forwarded.edn` (new, gitignored)

Written by `scripts/crew_fielder.clj` and read by `bb crew-answer`.

```clojure
{:as-of 1794100005000
 :forwarded {:le-testeur {:id "6a049a2c7e97372a1caad99f23478183fe038e13" :at 1794100005000}}}
```

The last prompt forwarded to the twin, per member.

### 2.3 `state/crew.edn`: one optional key per prompt

A blocked member's `:prompt` may gain `:always n`: the option number (an integer, 2–9) that the
dialog offers for "don't ask again" / "allow … this session/project". It is absent when there is
none. This is not screen text, and nothing else in crew.edn changes.

```clojure
{:status :blocked :prompt {:id "…" :kind :unknown :text "Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs?" :risky true :since 1794100000000 :always 2}}
```

### 2.4 Talk entries (`pm.talk`)

```clojure
;; a member's end-of-turn summary: new kind, quiet (no badge)
{:n 7 :who "Le Coureur" :text "The spot under … instead?" :at 1794100001000
 :kind :crew-said :member :le-coureur :question? true}
;; a hal-say --as line: reuses :crew-reply (counts, like the demo pointer line)
{:n 8 :who "Un Automate" :text "Drafted the bench behind the house." :at 1794100003000
 :kind :crew-reply :member :un-automate-monde}
;; a prompt answered with the new actions: :action :approve | :always, :state :allowed
```

Two new top-level talk keys:

- `:said-at {member-kw at}`: the last crew-said `:at` seen per member. It is persisted.
- `:says-at {"agent-name" since}`: the last `--as` say seen per agent. It is **not** persisted.

### 2.5 Bridge request bodies (POST `/crew/<member>`)

These bodies are new. `allow`, `deny` and `say` are unchanged.

```json
{"token": "<64 hex>", "action": "approve", "prompt-id": "<40 hex>", "by": "twin"}
{"token": "<64 hex>", "action": "always",  "prompt-id": "<40 hex>"}
{"token": "<64 hex>", "action": "deny",    "prompt-id": "<40 hex>", "text": "use bb instead", "by": "twin"}
```

`"by"` is optional: `"twin"` or `"player"`, defaulting to the player. It decides the crew-log's `:by`.

---

## 3. `pm.prose` (new: `src/pm/prose.cljc`)

```clojure
(ns pm.prose
  "A Claude Code pane's own words: the assistant's prose blocks (⏺) out of a
   Herdr screen, never tool calls, tool output, diffs or file contents; the
   end-of-turn summary and question test; state/crew-said.edn's rules. Pure,
   portable (bb + cljs); may be required by pm.core. Never names a member."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

(def max-said 20)
(def summary-max 240)
(def said-grace-ms 5000)   ; see pm.talk/said-questions
```

### 3.1 Line rules

Apply these to each line after `str/trimr`. In this table, `\|` is a markdown-escaped `|`: the
real regexes are `#"^❯(?: |$)"` and `#"^⏺ (?:Agent|Background command|Task) \""`.

| name | regex / test | meaning |
|---|---|---|
| `rule-re` | `#"^─{3,}$"` | a full-width rule (composer edge or dialog top) |
| `user-re` | `#"^❯(?: \|$)"` (i.e. `❯` then a space or end) at column 0 | the user's prompt, or the composer |
| bullet | `(str/starts-with? l "⏺")` | a message starts |
| `tool-head-re` | `#"^⏺ [A-Za-z_][A-Za-z0-9_.:-]*\("` | `⏺ Name(` is a tool call |
| `notice-re` | `#"^⏺ (?:Agent\|Background command\|Task) \""` | a background notice |
| `result-re` | `#"^  ⎿"` | tool output follows |
| `summary-re` | `#"^  [A-Z][a-z]+(?: for)? \d+ \S"` | a collapsed tool group (`  Read 1 file`, `  Searched for 2 patterns, …`, `  Made 1 scratchpad edit +6`, `  Running 1 shell command…`) |
| para line | starts with two spaces, not blank, and not `result-re` | a line of a paragraph |

### 3.2 `(prose-blocks screen)` → vector of strings

Every prose block on the screen in order, each joined into one line. `[]` for a nil, blank or
non-string screen. The algorithm:

1. `ls` = `(mapv str/trimr (str/split-lines screen))`.
2. **Cut the composer.** Find the last index `i` with `(re-matches rule-re (ls i))` whose next line
   matches `user-re`. If found, keep `(subvec ls 0 i)`. Otherwise keep everything (for example, a
   dialog is up).
3. Scan for lines starting with `⏺`. For a block starting at `i`:
   - **Paragraph 1** is line `i` plus the para lines directly under it, up to the first line that
     isn't one.
   - The block is **not prose**, and is skipped, when line `i` matches `tool-head-re` or
     `notice-re`, **or** the line right after paragraph 1 matches `result-re`.
   - **Later paragraphs (prose only):** while the next line is blank, skip the blank run. If the
     next non-blank line is a para line and **does not** match `summary-re`, it starts another
     paragraph that runs over consecutive para lines. Anything else ends the block: `⏺`, `✻`, `❯`,
     `※`, `─`, a summary line, `  ⎿`, or the end of the lines.
   - Scanning continues after the block, so lines inside a tool block's output are never read as
     block starts unless they begin with `⏺`.
4. A block's text is `(subs first-line 1)` followed by all its other lines, joined with `" "`, then
   `(str/replace #"\s+" " ")` and `str/trim`.

### 3.3 `(last-said screen)` → string or nil

Let `u` be the index of the last line (after the composer cut) matching `user-re`, or -1. The
result is the text of the last prose block that **starts after `u`**, or nil when there is none:
the latest turn said nothing in prose. `nil` and non-string input give nil.

### 3.4 `(summary text)` → string

When `(count text)` ≤ 240, the text unchanged. Otherwise
`(str (str/trimr (subs text 0 239)) "…")`, so the result has at most 240 chars. A non-string gives
nil.

### 3.5 `(question? text)` → boolean

`(and (string? text) (str/ends-with? (str/trimr text) "?"))`.

### 3.6 `(turn-ended? prev now)` → boolean

`(and (contains? #{:working :blocked} prev) (contains? #{:idle :done} now))`.

### 3.7 `(claude-members herdr-list)` → set of keywords

`herdr-list` is the parsed `herdr agent list` JSON with keyword keys, as `b/agent-statuses` takes
it: `{:result {:agents [{:name "…" :agent "claude" …} …]}}`. The result holds `(keyword name)` for
every entry whose `:agent` is `"claude"` and whose `:name` matches `#"^[a-z][a-z0-9_-]{0,31}$"`.
Anything malformed gives `#{}`.

### 3.8 `(add-said said member text now)` → said map

- `said` nil reads as `{:as-of now :said {}}`.
- If `member` is not a keyword, `text` is not a non-blank string, or `now` is not an integer, return
  `said` **unchanged** (identical, nil stays nil).
- `s = (summary (str/trim text))`, `q = (question? text)`, and `items = (get-in said [:said member] [])`.
- If `(= s (:text (peek items)))`, return `said` **identical**: a re-read of the same turn.
- Otherwise `at = (if (seq items) (max now (inc (:at (peek items)))) now)`. Conj
  `{:at at :text s :question? q}`, keep `(take-last max-said …)` as a vector, and set `:as-of now`.

### 3.9 `(parse-said text)` → `{:as-of :said}` or nil

Read the EDN, never throwing. When the value is not a map, or its `:said` is not a map, return nil.
`:as-of` is kept when it is an integer ≥ 0, else 0. For each `[k items]`: `k` is a keyword whose
name matches the member regex, and `items` is sequential. Keep the items that are maps with `:at`
an integer ≥ 0, `:text` a non-blank string of ≤ 240 chars, and `:question?` a boolean, as
`(select-keys it [:at :text :question?])`, `take-last 20`, as a vector. Members left with no valid
items are dropped.

---

## 4. `pm.prompt` changes (`src/pm/prompt.cljc`)

### 4.1 The left-border strip, in `parse-screen` only

The `cmd` vector (the lines between the two `╌` rules) is built as today. Then each line that
matches `#"^│\s?(.*)$"` is replaced by `(str/trim group-1)`, and lines that are blank afterwards are
dropped (`(remove str/blank?)` as now). Nothing else in `parse-screen` changes. Two-sided `│…│` is
still handled first by `strip-line`. Existing fixtures have no left-only border, so their ids and
texts are unchanged.

### 4.2 `(always-option screen)` → integer or nil

When `(prompt-screen? screen)`, find the question line `q` (the last line matching `question-re`,
the same as `dialog-info`). For the lines after it (each `str/trim`med), match
`#"^(?:❯\s*)?(\d{1,2})\.\s+(.*)$"`. The result is the **first** option number `n ≥ 2` whose text
matches `#"(?i)^Yes, (?:and don['’]t ask again\b|allow\b)"`. Otherwise nil, and nil for a
non-dialog or non-string screen.

### 4.3 `(with-always agents screens)` → agents

For each `[m v]` in agents with `(= :blocked (:status v))`, a map `(:prompt v)`, and a string
`(get screens m)` where `(always-option that-screen)` is an integer `n`, assoc `[m :prompt :always]`
to `n`. Every other entry is unchanged.

### 4.4 `(risk-reason kind text repo)` → string or nil

Checks in order; the first match wins:

1. `(not= kind :bash)` → `"not a plain shell command"`
2. the first of the `risky-res` regexes that `re-find`s `text` → its phrase from the public vector
   `risk-reasons`, which runs parallel to `risky-res`:
   `["changes git state" "deletes files" "reaches the network" "reaches the network" "reaches another machine" "reaches another machine" "installs software" "installs software" "installs software" "runs as root" "changes system services"]`
3. `(and (re-find write-marker-re text) (some #(outside-path? repo %) (re-seq token-re text)))` →
   `"writes outside the repo"`
4. `(>= (count text) max-text)` → `"too long to check"` (crew.edn's text is capped at 400)
5. otherwise nil

The caller falls back to `"flagged risky"` when this is nil but `:risky` is true (§6.2).

---

## 5. `pm.bridge` changes (`src/pm/bridge.cljc`)

- `actions` becomes `#{"allow" "deny" "say" "approve" "always"}`. `log-line` therefore names the new
  actions, and also accepts `"forward"` in its non-crew set: `#{"hold" "ws" "view" "forward"}`.
- **`validate`, the member branch.** The order of checks is: unknown member (404), bad action
  (400), then:
  - **prompt-id** is required for `#{:allow :deny :approve :always}`, else 400 `"bad prompt-id"`.
  - **by:** if the body has `"by"` and it is not `"twin"` or `"player"`, 400 `"bad by"`.
  - **text:** `:approve` and `:always` → `{:text nil}`, like `:allow`. `:deny` and `:say` are as
    today.

  The result is today's map, plus `:by (keyword by)` **only** when `"by"` was present and the
  action is not `:say`. A body without `"by"` gives exactly today's map.
- **`decide`, new arms.** `:allow`, `:deny` and `:say` are unchanged. `live` may now carry `:always`.

  ```clojure
  :approve (cond (not= status :blocked)                         {:ok false :status 409 :reason "not blocked"}
                 (or (nil? prompt) (not= (:id prompt) prompt-id)) {:ok false :status 409 :reason "prompt changed"}
                 :else {:ok true :steps [[:keys "1"]] :reason "allowed"})
  :always  (cond (not= status :blocked)                         {:ok false :status 409 :reason "not blocked"}
                 (or (nil? prompt) (not= (:id prompt) prompt-id)) {:ok false :status 409 :reason "prompt changed"}
                 (not (integer? (:always live)))                {:ok false :status 409 :reason "no always option"}
                 :else {:ok true :steps [[:keys (str (:always live))]] :reason "allowed always"})
  ```

  Unknown and risky prompts are allowed here on purpose: it is the player's or the twin's call.
- `(log-action action)` → `:allowed` for `#{:allow :approve :always}`, `:declined` for `:deny`, and
  nil otherwise.
- `step-argv`: new step `[:read-recent]` →
  `[herdr "agent" "read" m "--source" "recent" "--lines" "200"]`.
- `(relay-argv python script session text)` → `[python script session text]`.
  `(relay-ok? out)` → `(and (string? out) (str/includes? out "\"ok\": true"))`. These are the same
  argv and success test `crew_bridge.clj`'s `relay-to-hal!` uses today, now shared.

---

## 6. `pm.fielder` additions (`src/pm/fielder.cljc`)

`verdict`, `due`, `attempt`, `log-entry` and `remember` are unchanged. `pm.fielder` still doesn't
require `pm.prompt`: the reason is passed in.

### 6.1 `(forwardable ctx forwarded)` → vector of `{:member :prompt}`

The members, in sorted order, whose `(verdict ctx m)` is `:unknown` or `:risky`, **and**
`(not= (:id p) (get-in forwarded [:forwarded m :id]))`, **and** with no log entry `x` in
`(:log ctx)` that has `(= m (:member x))`, `(= (:id p) (:prompt-id x))`, and
`(or (not (integer? (:since p))) (>= (:at x) (:since p)))`. Here `p` is the member's crew.edn
prompt. `verdict` already returns `:hold`, `:stale`, `:not-in-roster` or `:not-blocked` first, so
**hold on → `[]`**. A nil crew gives `[]`.

### 6.2 `(forward-text title member p reason)` → string

```clojure
(str "[from the crew] " title " asks to run: " (:text p)
     (when reason (str " (risky: " reason ")"))
     " · answer with: bb crew-answer " (name member) " "
     (if (integer? (:always p)) "allow|always|deny" "allow|deny")
     " [--note \"…\"]")
```

The script passes `reason = (or (pr/risk-reason (:kind p) (:text p) repo) (when (:risky p) "flagged risky"))`.

### 6.3 `(record-forward forwarded member p now)` → forwarded

`(-> (or forwarded {}) (assoc :as-of now) (assoc-in [:forwarded member] {:id (:id p) :at now}))`

### 6.4 `(parse-forwarded text)` → map or nil

Read the EDN, never throwing. A non-map gives nil. `:forwarded` keeps the entries `[k {:id :at}]`
where `k` is a keyword, `:id` matches `#"^[0-9a-f]{40}$"` and `:at` is an integer ≥ 0. `:as-of` is
an integer ≥ 0, else 0.

### 6.5 `(answer-request argv crew forwarded)` → `{:ok true …}` or `{:ok false :reason …}`

`argv` is the CLI's argument vector of strings. The checks run in order:

1. `(< (count argv) 2)` → `{:ok false :reason "usage: bb crew-answer <member> allow|always|deny [--note \"…\"]"}` (call this string **USAGE**)
2. `argv[0]` must match `#"^[a-z][a-z0-9_-]{0,31}$"`, else `"bad member"`.
3. `argv[1]` must be in `#{"allow" "always" "deny"}`, else USAGE.
4. The rest is either `[]` or exactly `["--note" note]`, else USAGE.
5. A note with allow/always → `"--note goes with deny"`.
6. The note is `str/trim`med. Blank → `"empty note"`. More than 1000 chars → `"note too long"`. A
   `#"[\x00-\x1f\x7f]"` match in the raw note → `"control characters in note"`.
7. `id` = the forwarded id `(get-in forwarded [:forwarded m :id])` when it is a 40-hex string, else
   `(get-in crew [:agents m :prompt :id])`. Neither gives `(str "no prompt from " m " to answer")`.
   **The forwarded id wins**: the twin answers the prompt it was shown, and if the dialog has
   changed since, the bridge says stale.
8. → `{:ok true :member (keyword m) :action ({"allow" "approve" "always" "always" "deny" "deny"} choice) :prompt-id id :text note-or-nil}`

### 6.6 `(answer-body req token)` → JSON-ready map

```clojure
(cond-> {"token" token "action" (:action req) "prompt-id" (:prompt-id req) "by" "twin"}
  (some? (:text req)) (assoc "text" (:text req)))
```

---

## 7. `pm.crew/parse` (`src/pm/crew.cljs`)

When a blocked prompt is valid, it becomes
`(cond-> (select-keys p [:id :kind :text :risky :since]) (and (integer? (:always p)) (<= 2 (:always p) 9)) (assoc :always (:always p)))`.
An invalid `:always` is dropped and the prompt kept. `valid-prompt?` is unchanged.

---

## 8. `pm.talk` changes (`src/pm/talk.cljs`)

Add the require `[pm.crew :as crew]` (already there) and nothing new from `pm.prose`: talk reads
the already-parsed said map.

### 8.1 Kinds

- `crew-kinds` gains `:crew-said`, and `quiet-kinds` gains `:crew-said`: it is quiet and never counts.
- Both restore `case` forms need an arm, or a saved line throws:
  - `valid-restored-entry?`: `:crew-said true`.
  - `clean-restored-entry`: `:crew-said (assoc (select-keys e [:n :who :text :at :kind :member]) :question? (true? (:question? e)))`.
- `save-text` adds `:question?` to the entry keys, and `:said-at (:said-at talk)` at the top level.
  `restore` reads `:said-at` as a map of keyword → integer ≥ 0, and assocs it **only when
  non-empty**, like `:seen-by`.

### 8.2 `(observe-said talk said crew-state now)` → talk

- `talk` nil reads as `empty-talk`. When `said` is not a map with a map `:said`, return `talk`.
- For each member `m` in `(sort (keys (:said said)))`, with items sorted by `:at`:
  - `since = (get-in talk [:said-at m] 0)`.
  - Append, through `append-crew`, every item with `(> (:at it) since)` **and**
    `(> (:at it) (- now quiet-ms))` as
    `{:who (get-in crew-state [:titles m] (name m)) :text (:text it) :at (:at it) :kind :crew-said :member m :question? (true? (:question? it))}`.
  - Then set `[:said-at m]` to `(max since (max-of all its items' :at))`. Old items are skipped but
    still counted as seen.
- Return `talk` **identical** when the result equals it.

### 8.3 `(said-questions talk crew-state now)` → `{"agent-name" {:text :since}}`

For each member `m` with at least one `:crew-said` entry, let `e` be its latest one (by `:n`).
Include `{(name m) {:text (:text e) :since (:at e)}}` when **all** of these hold:

1. `(true? (:question? e))`.
2. `(crew/fresh? crew-state now)`, and m's status is `:idle` or `:done`.
3. m's agent `:since` is not an integer, or `(<= since (+ (:at e) said-grace-ms))`. Any later
   status change means it worked again. `said-grace-ms` is 5000, defined here as well, as
   `(def said-grace-ms 5000)`.
4. There is no `:crew-say` entry with `(= m (:member x))`, `(>= (:at x) (:at e))`, and `:state` in
   `#{:sending :sent}`: the player hasn't replied. A `:failed` say doesn't count.

The value shape matches `:asks-player`, so `with-questions` and `bricks/set-dots!` take it as is.

### 8.4 `crew-label` arms

```clojure
:crew-said (if (:question? e) (str (:who e) " asks: " (:text e)) (str (:who e) " · " (:text e)))
```

And in the `:crew-prompt` `:allowed` case:
`(str " · ☑ " (if (= :always (:action e)) "always allowed " "allowed ") (clock-text (:answered-at e) offset-min))`.
The `:allow` text is unchanged.

### 8.5 Prompts: Allow / Always / No

- `(def preview-chars 160)` and
  `(defn prompt-preview [s] (if (<= (count s) preview-chars) s (str (subs s 0 (dec preview-chars)) "…")))`.
  The private `crew-prompt-text` applies it to `(:text p)` before building `"wants to run …"` /
  `"is waiting at a prompt: …"`. Existing pinned texts are all under 160 chars.
- **`(prompt-controls e bridge-on?)`** (new). nil unless `e` is a `:crew-prompt` in `:pending` or
  `:sending`. With `a = (integer? (:always (:prompt e)))`:
  - `:sending` → `{:allow? true :always? a :disabled? true :hint "sending…"}`
  - `:pending` → `{:allow? true :always? a :disabled? (not bridge-on?) :hint (when-not bridge-on? "bridge off")}`

  `crew-controls` and `allowable?` are unchanged, and kept for their tests.
- **`crew-send`** accepts `#{:allow :deny :approve :always}`. The guard becomes
  `(or (= action :deny) (and (= action :allow) (allowable? e)) (= action :approve) (and (= action :always) (integer? (:always (:prompt e)))))`.
  Everything else is as today.
- **`crew-result`**, `:crew-prompt :ok`: `:state` becomes
  `(if (contains? #{:allow :approve :always} (:action x)) :allowed :declined)`.

### 8.6 `(observe-says talk world)` → talk (the `--as` fix)

- `talk` nil reads as `empty-talk`. `twin = (some-> (w/twin-walker world) :agent name)`.
- For each `[agent {:keys [text since]}]` in `(sort-by key (:says world))` where `agent` is a
  string, `(not= agent twin)`, text is non-blank, since is a finite number, and
  `(> since (get-in talk [:says-at agent] -1))`:
  - `who` = the `:name` of the walker whose `(name (:agent w))` is `agent`, else
    `(get-in world [:crew :titles (keyword agent)])`, else `agent`.
  - Append through the existing `crew-reply`:
    `(crew-reply t (keyword agent) who text since)`. Then `(assoc-in [:says-at agent] since)`.
- Return `talk` **identical** when nothing was added. The twin's own says still go through
  `observe` / `:hal` only.

---

## 9. `pm.ui/render-crew-row!` (`src/pm/ui.cljs`)

- Use `(talk/prompt-controls entry bridge-on?)` instead of `crew-controls`, and **drop the
  checkbox**.
- When there are controls, draw in order:
  - a button **"Allow"** (class `allow`) → `(on-approve (:n entry))`;
  - when `:always?`, a button **"Always"** (class `always`) → `(on-always (:n entry))`;
  - the existing deny button, relabelled **"No"** (class `deny`) → `(on-deny (:n entry) nil)`;
  - the existing `reply…` input (Enter → deny with the note).

  All of these are disabled per `:disabled?`, and the hint span is shown as today.
- `crew` destructures `on-approve` and `on-always` alongside `on-allow` / `on-deny`. No other UI
  changes.

---

## 10. `pm.core` wiring (`src/pm/core.cljs`)

1. **Poll the said file.** Add `(def crew-said-url "/state/crew-said.edn")`, plus a
   `!crew-said-poll`, `fetch-crew-said!` and `start-crew-said-poll!`, copied from the crew-log poll
   (`core.cljs:262-283`). Parse with `prose/parse-said`, and when the result differs, swap it into
   `:crew-said` on the world. Start it next to `start-crew-log-poll!` in the non-DEMO branch
   (`core.cljs:~1940`). Require `[pm.prose :as prose]`.
2. **`talk-tick!`** (`core.cljs:1003`): thread two more steps after `observe-log`:
   ```clojure
   (talk/observe-said (:crew-said @!world) (:crew @!world) now)
   (talk/observe-says @!world)
   ```
3. **Questions.** In the roster block (`core.cljs:~1051`), compute
   `said-q (talk/said-questions t' (:crew w) now)` and `asks (merge said-q (:asks-player w))`. Use
   `asks` for `asked` (`(set (map keyword (keys asks)))`) and for `(talk/with-questions asks)`. In
   `frame`, pass
   `(merge (talk/said-questions (:talk @!world) (:crew @!world) (js/Date.now)) (:asks-player @!world))`
   to `bricks/set-dots!` instead of `(:asks-player @!world)`. **Never** write `said-q` into the
   world's `:asks-player`: a reply must take the `:say` route, which types into the pane, not
   `send-answer!`.
4. **Answers.** Add
   ```clojure
   (defn- on-answer! [n action]
     (swap! !world update :talk talk/crew-send n action nil)
     (let [e (talk/entry (:talk @!world) n)]
       (when (= :sending (:state e))
         (post-crew! (:member e) action (:id (:prompt e)) nil n))))
   ```
   Then pass `:on-approve #(on-answer! % :approve)` and `:on-always #(on-answer! % :always)` in the
   `render-panel!` crew map (`core.cljs:~1047`). `on-allow` and `on-deny` stay.

---

## 11. Scripts

### 11.1 `scripts/crew_status.clj`: the watcher writes `crew-said.edn`

- `(def said-file "state/crew-said.edn")`. Read `herdr agent list` **once** per poll: keep the
  parsed JSON, and derive both `b/agent-statuses` and `prose/claude-members` from it.
- After `observe-prompts`, apply `(pr/with-always agents screens)`, using the screens already read
  for blocked members. Then publish as today.
- The loop carries `said`, starting from `(prose/parse-said (slurp-or-nil said-file))`. On each poll,
  for each roster member `m` in the claude set, sorted, where
  `(prose/turn-ended? (get-in prev [m :status]) (get-in agents [m :status]))`:
  - read `(b/step-argv herdr m [:read-recent])`;
  - when `(prose/last-said out)` is a string, `said ← (prose/add-said said m text now)`.

  When `said` is not `identical?` to before, write it atomically (`.tmp` + rename, as `write!`
  does). `--once` never writes said. The first poll has `prev = {}`, so nothing is extracted at
  startup.
- Update the header comment: this script now reads a member's recent screen at turn end to pick
  out its prose, and still never copies raw screens into crew.edn.

### 11.2 `scripts/crew_bridge.clj`

- `live-for` adds `:always (pr/always-option out)` to the live map when the member is blocked: the
  same `out` as the prompt, never inside `:prompt`.
- After a successful run, append to the crew-log when `(b/log-action (:action v))` is non-nil, with
  `:by (or (:by v) :player)`. That replaces today's `#{:allow :deny}` check and hard-coded
  `:player`.
- `log!` reason: append `" by the twin"` when `(= :twin (:by v))`. For example
  `"<iso> le-testeur approve 200 allowed by the twin"`.
- `relay-to-hal!` uses `b/relay-argv` and `b/relay-ok?`. Its behaviour is unchanged.

### 11.3 `scripts/crew_fielder.clj`: forwarding

- Add `(def forwarded-file "state/crew-forwarded.edn")` and `(def hal-session-file "state/hal-session")`.
  Read the session with the same regex `crew_bridge.clj` uses. The python path and `PYTHONPATH`
  are the same as the bridge's.
- The loop carries `forwarded`, starting from `(f/parse-forwarded (slurp-or-nil forwarded-file))`.
  After the due answers on each poll, for each `{:keys [member prompt]}` in
  `(f/forwardable (ctx-now tried) forwarded)`:
  1. Re-read hold. If it is on, stop.
  2. With no session, `log!` `"no twin chat (state/hal-session): not forwarding"` once per
     process, and stop.
  3. Build `text` with `f/forward-text`, where the title comes from crew.edn's `:titles` and the
     reason as in §6.2.
  4. Dry-run: log `"dry-run: would forward"`, and record in memory only.
  5. Otherwise run `(p/sh {:extra-env …} (b/relay-argv …))`. On `(and (zero? exit) (b/relay-ok? out))`,
     log `(log! member 200 "forwarded to the twin")`; else log 502 `"forward failed"`. Record with
     `f/record-forward` **either way**, and write the file atomically. A failed relay isn't
     retried: the prompt is in 💭 for the player anyway.
- `log!` gains an `action` parameter: `:allow` for answers as today, `:forward` for forwards. The
  text is never logged.

### 11.4 `scripts/crew_answer.clj` (new)

The twin's half of a forwarded prompt:

```
bb crew-answer <member> allow|always|deny [--note "…"]
```

1. `r = (f/answer-request (vec *command-line-args*) (read-edn "state/crew.edn") (f/parse-forwarded (slurp-or-nil "state/crew-forwarded.edn")))`.
   When it is not ok, print `crew-answer: <reason>` to stderr and exit 1.
2. If `state/bridge.edn` is missing, print `crew-answer: the bridge isn't running (bb crew-bridge)`
   and exit 1.
3. POST with `babashka.http-client` to `http://127.0.0.1:<port>/crew/<member>`, with headers
   `Origin: b/allowed-origin` and `Content-Type: application/json`, body
   `(json/generate-string (f/answer-body r token))`, and `:throw false`.
4. On 200, print `<member>: <result>` (e.g. `le-testeur: allowed`) and exit 0. Otherwise print
   `crew-answer: <member>: <reason>` to stderr, adding ` (stale: the dialog changed; nothing was sent)`
   on 409, and exit 1.

The bridge re-reads the live screen and checks the id (`decide`), so a changed dialog is never
answered.

### 11.5 `scripts/hal_say.clj`

Header comment only: a `--as <member>` line now also lands in that member's 💭 thread (the game's
`talk/observe-says`), as well as on its speech plate.

### 11.6 `bb.edn`

- Add:
  `crew-answer {:doc "The twin answers a crew prompt forwarded to its chat (through the bridge's prompt-id check): bb crew-answer <member> allow|always|deny [--note \"…\"]" :task (let [r (apply shell {:continue true} "bb" "scripts/crew_answer.clj" *command-line-args*)] (System/exit (:exit r)))}`.
- The `hal-say` doc: replace "(the twin's line also captioned, spoken, kept in 💭)" with "(kept in
  that speaker's 💭 thread; the twin's line is also captioned and spoken)".

---

## 12. Fixtures: `test/fixtures/crew-mirror/` (tester copies verbatim)

These are real captures from this machine's panes on 2026-11-26 (`herdr agent read`). They were
checked for paths and PII:

- No `/Users/…`, emails or tokens.
- The two longer windows start below the Claude Code banner, which shows `~/src/…` and the plan.
- The only paths are repo-relative ones and the staged `/private/tmp/pm-fixture`.
- "Michael" is the project owner's name, already public throughout the repo.

Copy each block's lines exactly, between the fences, with each file ending in one `\n`. The rules
`str/trimr` every line, so trailing whitespace doesn't matter. Copies also sit at
`/private/tmp/claude-504/-Users-hal-src-prime-meridian/52cf05bc-6fac-4fd6-b157-5eac19364a7a/scratchpad/fx/*.txt`
while that scratchpad lasts. The spec text is authoritative.

### 12.1 `turn-tools-prose.txt` (35 lines; un-automate-monde, visible; idle, with typed-but-unsent `❯ bb where`)

```text

❯ Second build request from Michael (via Hal): "make
  another bench here". He is looking at x,z
  10.23,-23.67, under trees again. Hal checked: --near
  9,-26 lands on the nearest level open ground (about
  [8.5 -30]). Draft a second bench with a NEW id (not
  the one you just placed, not bench-behind-house)
  via bb blueprint-draft un-automate-monde <draft>
  --near 9,-26, then bb hal-say "<where it went>" --as
  un-automate-monde. Go straight to the command; no
  ground-map checks needed.

  Made 1 scratchpad edit +6, ran 1 shell command

⏺ Placed. Now let Michael know.

  Ran 1 shell command

⏺ Drafted bench-south-of-house at [8.5 6.86 -30], yaw
  90, 4.03 m from the search point — the nearest level
  open ground past the tree/bush cover near where
  Michael was looking. Told him in-world.

✻ Baked for 18s · done 5:18 PM

※ recap: Finished the demo-talk-v0 build task (gate
  passing) and drafted two requested benches near the
  lobby as blueprints. No next action pending; waiting
  on Michael/Hal for further requests. (turn off recaps
  in /config)

───────────────────────────────────────────────────────
❯ bb where
───────────────────────────────────────────────────────
  ⏵⏵ accept edits on (shift+tab to cycle)
```

### 12.2 `question.txt` (35 lines; le-coureur, visible; a staged question)

```text


  Ran 1 shell command

⏺ Write(crew/runs/demo-talk-v0-20261126-…/gate-note.md)
  ⎿  Wrote 3 lines to crew/runs/demo-talk-v0-20261
     126-1137/gate-note.md
      1 PASS
      2 Build completed. (0 warnings)
      3 Ran 1577 tests containing 8518 assertion
        s.

⏺ PASS

  done-1687

✻ Crunched for 26s · done 11:46 AM

❯ Fixture capture for the crew-mirror spec. Use no
  tools and do nothing else: reply with exactly these
  two sentences, as written. The spot under the trees
  by the lobby has no open ground within 6 m, so I
  didn't place the bench there. Try the bench behind
  the house instead?

⏺ The spot under the trees by the lobby has no open
  ground within 6 m, so I didn't place the bench there.
  Try the bench behind the house instead?

✻ Crunched for 1s · done 5:38 PM

───────────────────────────────────────────────────────
❯
───────────────────────────────────────────────────────
  ⏵⏵ accept edits on (shift+tab to cycle)
```

### 12.3 `tool-diffs-long.txt` (58 lines; un-automate-monde, recent window; tool calls with ⎿ and diffs, a long multi-paragraph final block, no `❯` in view)

```text
⏺ Update(crew/runs/demo-talk-v0-2026…/builder-notes.md)
  ⎿  Couldn't edit file

  Read 1 file

⏺ Update(crew/runs/demo-talk-v0-2026…/builder-notes.md)
  ⎿  Added 3 lines, removed 3 lines
      34  4. DOM selectors used above: `#talk-li
          st` is the thread's `<ul>`; each row
      35     is an `<li class="mine">` or `<li c
          lass="theirs">`; the visible line is
      36     its `span.text` (`crew-label`'s out
          put for a crew row, the raw `:text`
      37 -   for others). The composer input is
         -`#talk-panel input[placeholder]`
      38 -   (or whatever id/selector the roster
         - strip gives each member's row —
      39 -   same element Hal would click).
      37 +   for others). Each roster row's comp
         +oser is
      38 +   `input[data-member="<agent-keyword-
         +name>"]` (e.g.
      39 +   `input[data-member="hal"]` for the
         +twin).
      40
      41  5. Sanity check the dev build still fa
          ils the old way: `npx shadow-cljs
      42     watch app`, bridge off (no `bb crew
          -bridge` running), say anything to

⏺ Now let's view the final diff stat to report.

  Ran 1 shell command

⏺ Implemented spec §2–4 in pm.talk (new :crew-reply
  kind, demo-member-line, say-reply, crew-reply,
  crew-label/restore arms, with-walkers 5-arity), wired
  on-say and the roster's with-walkers call in
  pm.core, and added the :member-talk block to
  resources/estate.edn.

  Gate: GATE: PASS — 0 warnings; Ran 1577 tests
  containing 8518 assertions. (includes the tester's
  27-case demo_talk_test.cljs, all passing).

   resources/estate.edn |  4 ++++
   src/pm/core.cljs     | 25 +++++++++++--------
   src/pm/talk.cljs     | 68
  ++++++++++++++++++++++++++++++++++++++++++----------
   3 files changed, 75 insertions(+), 22 deletions(-)

  crew/runs/demo-talk-v0-20261126-1137/builder-notes.md
  written (headless dist/demo check steps + what
  changed).

  done-5271

✻ Sautéed for 4m 30s · done 11:46 AM
```

### 12.4 `agent-notice.txt` (85 lines; le-testeur, recent window below the banner; an `Explore(` tool, an `Agent "…" finished` notice, bullets, and the `new task?` hint)

```text
❯ Spec: crew/runs/demo-talk-v0-20261126-1137/spec.md
  (quest: .crew/quests/demo-talk-v0.md). Write its
  test cases in the test file the spec assigns the
  tester, and nothing else. The builder is coding in
  parallel.

  When you have completely finished, end your reply
  with a line containing only this: done-3540

  Read 3 files

⏺ Explore(Check world/jumeaux/crew function signatures)
  ⎿  Backgrounded agent (↓ to manage · ctrl+o to…

  Listed 1 directory · 1 failed

⏺ Good, file doesn't exist yet. Let me peek at
  crew_talk_test.cljs for style conventions while the
  Explore agent runs.

  Searched for 2 patterns, read 5 files · 1 failed

⏺ Agent "Check world/jumeaux/crew function signatures"
  finished · 25s

  Searched for 1 pattern

⏺ Now I have everything confirmed. Writing the test
  file per spec §6.

⏺ Write(test/pm/demo_talk_test.cljs)
  ⎿  Wrote 206 lines to
     test/pm/demo_talk_test.cljs
      1 (ns pm.demo-talk-test
      2   (:require [cljs.test :refer [deftest i
        s]]
      3             [clojure.string :as str]
      4             [pm.talk :as tk] [pm.crew :a
        s crew] [pm.world :as w] [pm.jumeaux :as
         j]))
      5
      6 (def T0 1800000000000)
      7 (def LINE "Ask {twin}, who runs the crew
     … +200 lines (~9 KB)

  Ran 1 shell command

⏺ New file test/pm/demo_talk_test.cljs (ns
  pm.demo-talk-test), 206 lines, covering all 27
  numbered cases from spec §6:

  - t01–t05: estate :member-talk block and
    demo-member-line (both fixtures, no-jumeaux,
    blank/non-string/nil edge cases)
  - t06–t12: say-reply decision (not-demo, demo→twin,
    demo→member, second fixture, no line in data, no
    twin walker, invalid member)
  - t13–t17: crew-reply (basic shape, trims,
    identical-on-invalid-args, crew-label arm,
    counts?/crew-kinds)
  - t18–t20: whole flows matching on-say's call order
    (twin, member/estate, member/second fixture)
  - t21–t22: unread/call-line, restore round-trip +
    drop-on-missing-:member
  - t23–t24: non-demo no-bridge still fails with · not
    sent: bridge off (member and twin)
  - t25: twin-thinking? in the demo
  - t26–t27: with-walkers 5-arity (open/closed, 4-arity
    unchanged, second fixture)

  These exercise demo-member-line, say-reply,
  crew-reply, the :crew-reply arms in
  crew-label/restore, and the with-walkers 5-arity —
  none of which exist in pm.talk yet, so the suite will
  fail to compile/run until the builder lands them;
  that's expected per the brief.

  done-3540

✻ Cooked for 2m 7s · done 11:44 AM
               new task? /clear to save 110.8k tokens
───────────────────────────────────────────────────────
❯
───────────────────────────────────────────────────────
  ⏵⏵ accept edits on (shift+tab to cycle)
```

### 12.5 `bash-multiline-prompt.txt` (35 lines; le-coureur, visible, **blocked** on a staged multi-line Bash dialog with a left-only border)

```text
     printf "%s\n" "$name" >>
     /private/tmp/pm-fixture/items.txt
     done
     for name in mailbox phone-booth workshop creek
     lobby; do…

───────────────────────────────────────────────────────
 Bash command
 Fixture capture for crew-mirror spec
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 │ mkdir -p /private/tmp/pm-fixture
 │ for name in bench lamp-post table planter; do
 │   printf "%s\n" "$name" >>
 │ /private/tmp/pm-fixture/items.txt
 │ done
 │ for name in mailbox phone-booth workshop creek
 │ lobby; do
 │   printf "%s\n" "$name" >>
 │ /private/tmp/pm-fixture/places.txt
 │ done
 │ wc -l /private/tmp/pm-fixture/items.txt
 │ wc -l /private/tmp/pm-fixture/places.txt
 │ ls -la /private/tmp/pm-fixture
 │ cat /private/tmp/pm-fixture/items.txt
 │ cat /private/tmp/pm-fixture/places.txt
 │
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 A variable in this command can't be checked before it
 runs

 Do you want to proceed?
 ❯ 1. Yes
   2. No

 Esc to cancel · Tab to amend
```

---

## 13. Tests: `test/pm/crew_mirror_test.cljs` (ns `pm.crew-mirror-test`)

```clojure
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
```

The dashes matter. `§2–4` and `t01–t05` use an **en dash** (–), while `PASS — 0` and `search point
—` use an **em dash** (—). The summaries end in `…` (U+2026).

### Prose extraction (`pm.prose`)

1. **A turn with tools and prose:** `(pz/prose-blocks TURN)` ⇒ `["Placed. Now let Michael know." P1]`.
   `(pz/last-said TURN)` ⇒ `P1`, and `(count P1)` ⇒ `192`. The composer's `❯ bb where`, the
   `Made 1 …` / `Ran 1 …` summaries and the `※ recap` never appear.
2. **A question:** `(pz/prose-blocks QUES)` ⇒ `["PASS done-1687" Q]`, `(pz/last-said QUES)` ⇒ `Q`
   (the earlier turn's "PASS done-1687" is before the last `❯`), and `(pz/question? Q)` ⇒ `true`.
3. **Tool calls and diffs:** `(count (pz/prose-blocks DIFF))` ⇒ `2`, and its first element ⇒
   `"Now let's view the final diff stat to report."` `(pz/last-said DIFF)` has count `721`,
   starts with `"Implemented spec §2–4 in pm.talk"` and ends with `"changed). done-5271"`. No
   `⎿`, `Couldn't edit` or diff text (`"lass=\"theirs\""`) appears in any block.
4. **Notices, Explore and bullets:** `(pz/prose-blocks NOTE)` ⇒ a vector of 3 whose first two are
   `"Good, file doesn't exist yet. Let me peek at crew_talk_test.cljs for style conventions while the Explore agent runs."`
   and `"Now I have everything confirmed. Writing the test file per spec §6."` The `Agent "…"
   finished` notice and `Explore(`/`Write(` are absent. `(pz/last-said NOTE)` has count `1121`,
   ends with `"that's expected per the brief. done-3540"`, and contains no `"new task?"`.
5. **A dialog on screen:** `(pz/prose-blocks BASH)` ⇒ `[]` and `(pz/last-said BASH)` ⇒ `nil`.
6. **Synthetic edges:**
   - `(pz/prose-blocks "⏺ Bash(ls)\n")` ⇒ `[]`.
   - `(pz/prose-blocks "⏺ Running 1 shell command…\n  ⎿  $ ls\n")` ⇒ `[]`.
   - `(pz/prose-blocks "⏺ Done.\n\n  Ran 1 shell command\n\n⏺ Next.")` ⇒ `["Done." "Next."]`.
   - `(pz/prose-blocks "⏺ Hi.\n\n※ recap: did a thing\n  more\n")` ⇒ `["Hi."]`.
   - `(pz/prose-blocks "⏺ One\n  two\n\n  three\n\n✻ Baked for 1s")` ⇒ `["One two three"]`.
   - `(pz/last-said "⏺ Old answer.\n\n❯ new ask\n\n  Ran 1 shell command\n")` ⇒ `nil`, with
     prose-blocks ⇒ `["Old answer."]`.
   - `(pz/last-said nil)`, `(pz/last-said "")` and `(pz/prose-blocks nil)` ⇒ `nil`, `nil`, `[]`.

### Summary, question, turn end

7. `(pz/summary P1)` ⇒ `P1`. `(pz/summary (pz/last-said DIFF))` ⇒ `S-DIFF` (count 240).
   `(pz/summary (pz/last-said NOTE))` ⇒ `S-NOTE` (count 240).
8. `(pz/summary (apply str (repeat 240 "a")))` is unchanged. For 241 a's, the result has count
   `240` and ends with `"aaa…"`. For `(str (apply str (repeat 238 "a")) " bcd")` (242 chars), the
   result is 238 a's + `"…"`, count `239`: the trailing space is trimmed before the ellipsis.
   `(pz/summary nil)` ⇒ `nil`.
9. `pz/question?`: `Q` ⇒ true; `(str Q "  ")` ⇒ true; `P1` ⇒ false; `"Is it? Yes."` ⇒ false; `nil` ⇒ false.
10. `pz/turn-ended?`: `[:working :idle]`, `[:working :done]` and `[:blocked :idle]` ⇒ true;
    `[:idle :idle]`, `[nil :idle]`, `[:working :working]` and `[:working :blocked]` ⇒ false.
11. `(pz/claude-members {:result {:agents [{:name "un-automate-monde" :agent "claude"} {:name "le-critique" :agent "hermes"} {:name "Bad" :agent "claude"} {:name "le-testeur"}]}})`
    ⇒ `#{:un-automate-monde}`. `(pz/claude-members nil)` ⇒ `#{}`.

### crew-said.edn rules

12. `(pz/add-said nil :le-coureur Q 1000)` ⇒
    `{:as-of 1000 :said {:le-coureur [{:at 1000 :text Q :question? true}]}}`.
13. With `s1` from 12: `(pz/add-said s1 :le-coureur Q 2000)` is `identical?` to `s1`.
    `(pz/add-said s1 :le-coureur P1 1000)` ⇒ items
    `[{:at 1000 :text Q :question? true} {:at 1001 :text P1 :question? false}]`, with `:as-of 1000`.
14. The long text: `(get-in (pz/add-said nil :un-automate-monde (pz/last-said DIFF) 5) [:said :un-automate-monde 0])`
    ⇒ `{:at 5 :text S-DIFF :question? false}`.
15. The cap: adding 21 distinct texts `"t0"`…`"t20"` at `1..21` keeps 20 items, the first being
    `{:at 2 :text "t1" :question? false}`.
16. Invalid input is returned as is: `(pz/add-said nil "le-coureur" Q 1)` ⇒ `nil`;
    `(pz/add-said s1 :le-coureur "  " 5)` is `identical?` to `s1`.
17. `(pz/parse-said (pr-str (pz/add-said s1 :le-coureur P1 1000)))` ⇒ the same map.
    `(pz/parse-said (pr-str {:as-of 3 :said {:le-coureur [{:at -1 :text "x" :question? false} {:at 2 :text "" :question? false} {:at 4 :text "ok" :question? "yes"} {:at 5 :text "fine" :question? false :extra 1}] :Bad [{:at 1 :text "x" :question? false}]}}))`
    ⇒ `{:as-of 3 :said {:le-coureur [{:at 5 :text "fine" :question? false}]}}`.
    A 241-char `:text` is dropped. `(pz/parse-said "nope")`, `(pz/parse-said "[1]")` and
    `(pz/parse-said nil)` ⇒ `nil`.

### Prompts: reading, Always, reasons (`pm.prompt`)

18. **The bordered, wrapped prompt:** with `p = (pr/read-prompt BASH R)`:
    `(:id p)` ⇒ `IDB`, `(:kind p)` ⇒ `:bash`, `(:risky p)` ⇒ `true`, and `(count (:text p))` ⇒ `400`.
    `(:text p)` starts with
    `"mkdir -p /private/tmp/pm-fixture for name in bench lamp-post table planter; do printf"` and
    ends with `"…"`, and `(str/includes? (:text p) "│")` ⇒ `false`.
    `(:lines (pr/parse-screen BASH))` has count `15`, first `"mkdir -p /private/tmp/pm-fixture"`
    and last `"cat /private/tmp/pm-fixture/places.txt"`.
19. **Always:** `(pr/always-option (v1 "190664ad70"))` ⇒ `2` ("allow reading from … this project").
    `(pr/always-option (v1 "49184610d7"))` ⇒ `2` ("don’t ask again").
    `(pr/always-option (.readFileSync fs ".crew/quests/crew-talk-v0/claude-bash-prompt.txt" "utf8"))`
    ⇒ `2`. `(pr/always-option BASH)` ⇒ `nil` (Yes/No only). `(pr/always-option nil)` ⇒ `nil`, and
    `(pr/always-option TURN)` ⇒ `nil`.
20. Synthetic dialogs, built with `(str/join "\n" […])`:
    - `[" Edit file" " src/pm/core.cljs" " Do you want to make this edit to core.cljs?" " ❯ 1. Yes" "   2. Yes, allow all edits during this session (shift+tab)" "   3. No"]` ⇒ `2`.
    - `[" Fetch" " https://example.com/notes" " Do you want to allow Claude to fetch this content?" " ❯ 1. Yes" "   2. Yes, and don't ask again for example.com" "   3. No, and tell Claude what to do differently (esc)"]` ⇒ `2`.
    - `[" Bash command" " ╌╌╌" " ls" " ╌╌╌" " Do you want to proceed?" " ❯ 1. Yes" "   2. Yes, and switch to auto mode · auto" "      mode handles these prompts for you" "   3. No"]` ⇒ `nil`: auto mode is not an always.
21. `(pr/with-always {:m {:status :blocked :prompt PC} :w {:status :working}} {:m (v1 "49184610d7") :w (v1 "49184610d7")})`
    ⇒ `{:m {:status :blocked :prompt (assoc PC :always 2)} :w {:status :working}}`. With the screen
    `BASH` for `:m`, the agents are unchanged, and with no screen for `:m` they are unchanged too.
22. `pr/risk-reason`:
    - `(:bash (:text (pr/read-prompt BASH R)) R)` ⇒ `"writes outside the repo"`.
    - `[:bash "curl --version" R]` ⇒ `"reaches the network"`.
    - `[:bash "git push origin main" R]` ⇒ `"changes git state"`.
    - `[:bash "rm -rf out" R]` ⇒ `"deletes files"`.
    - `[:bash "ssh host ls" R]` ⇒ `"reaches another machine"`.
    - `[:bash "npm install x" R]` ⇒ `"installs software"`.
    - `[:bash "sudo ls" R]` ⇒ `"runs as root"`.
    - `[:bash "launchctl list" R]` ⇒ `"changes system services"`.
    - `[:bash (str "echo " (apply str (repeat 395 "a"))) R]` ⇒ `"too long to check"`.
    - `[:bash "ls" R]` ⇒ `nil`.
    - `[:unknown EDIT-T R]` ⇒ `"not a plain shell command"`.

    Also `(count pr/risk-reasons)` ⇒ `11`.

### Bridge (`pm.bridge`)

With `rq = (fn [body] {:method "POST" :uri "/crew/le-testeur" :origin b/allowed-origin :body (merge {"token" TOK} body)})`
and `ctx = {:token TOK :members #{:le-testeur}}`:

23. `(b/validate (rq {"action" "approve" "prompt-id" IDE "by" "twin"}) ctx)` ⇒
    `{:ok true :member :le-testeur :action :approve :prompt-id IDE :text nil :by :twin}`.
24. `(b/validate (rq {"action" "always" "prompt-id" IDE "text" "ignored"}) ctx)` ⇒
    `{:ok true :member :le-testeur :action :always :prompt-id IDE :text nil}`, with no `:by` key.
25. `(b/validate (rq {"action" "approve"}) ctx)` ⇒ `{:ok false :status 400 :reason "bad prompt-id"}`.
    `(b/validate (rq {"action" "approve" "prompt-id" IDE "by" "hal"}) ctx)` ⇒
    `{:ok false :status 400 :reason "bad by"}`.
26. `(b/validate (rq {"action" "deny" "prompt-id" IDE "text" "  no thanks " "by" "player"}) ctx)` ⇒
    `{:ok true :member :le-testeur :action :deny :prompt-id IDE :text "no thanks" :by :player}`.
    `(b/validate (rq {"action" "allow" "prompt-id" IDE}) ctx)` ⇒
    `{:ok true :member :le-testeur :action :allow :prompt-id IDE :text nil}`, today's shape.
27. `decide`, with `LU = {:status :blocked :prompt (dissoc PU :always :since) :always 2}`:
    - `(b/decide {:action :approve :prompt-id IDE} LU)` ⇒ `{:ok true :steps [[:keys "1"]] :reason "allowed"}`.
    - `(b/decide {:action :always :prompt-id IDE} LU)` ⇒ `{:ok true :steps [[:keys "2"]] :reason "allowed always"}`.
    - `(b/decide {:action :always :prompt-id IDE} (dissoc LU :always))` ⇒ `{:ok false :status 409 :reason "no always option"}`.
    - `(b/decide {:action :approve :prompt-id ID1} LU)` ⇒ `{:ok false :status 409 :reason "prompt changed"}` (the stale id).
    - `(b/decide {:action :approve :prompt-id IDE} {:status :idle})` ⇒ `{:ok false :status 409 :reason "not blocked"}`.
    - `(b/decide {:action :approve :prompt-id ID2} {:status :blocked :prompt (dissoc PC :since)})` ⇒ `{:ok true :steps [[:keys "1"]] :reason "allowed"}` (risky: the player's call).
    - Unchanged: `(b/decide {:action :allow :prompt-id IDE} LU)` ⇒ `{:ok false :status 403 :reason "unknown prompt"}`.
28. `(mapv b/log-action [:allow :approve :always :deny :say])` ⇒ `[:allowed :allowed :allowed :declined nil]`.
29. `(b/log-line "T" "le-testeur" "approve" 200 "allowed by the twin")` ⇒ `"T le-testeur approve 200 allowed by the twin"`.
    `(b/log-line "T" :le-testeur :always 200 "allowed always")` ⇒ `"T le-testeur always 200 allowed always"`.
    `(b/log-line "T" :le-testeur :forward 200 "forwarded to the twin")` ⇒ `"T le-testeur forward 200 forwarded to the twin"`.
30. `(b/step-argv "herdr" :le-coureur [:read-recent])` ⇒ `["herdr" "agent" "read" "le-coureur" "--source" "recent" "--lines" "200"]`.
    `(b/relay-argv "py" "scripts/hal_relay.py" "s1" "hi")` ⇒ `["py" "scripts/hal_relay.py" "s1" "hi"]`.
    `(b/relay-ok? "{\"ok\": true, \"status\": \"queued\"}")` ⇒ `true`; `(b/relay-ok? "{\"ok\": false}")`
    ⇒ `false`; `(b/relay-ok? nil)` ⇒ `false`.

### Forwarding and `crew-answer` (`pm.fielder`)

31. `(f/forwardable CTX nil)` ⇒ `[{:member :le-testeur :prompt PU} {:member :un-automate-monde :prompt PC}]`.
    `:le-docteur` is routine and too soon (the fielder's own case), and `:le-coureur` is idle.
32. Already forwarded: `(f/forwardable CTX {:forwarded {:un-automate-monde {:id ID2 :at (- A 5000)}}})`
    ⇒ `[{:member :le-testeur :prompt PU}]`. Forwarding an older id doesn't count:
    `{:forwarded {:un-automate-monde {:id ID1 :at 1}}}` gives both again.
33. **Hold on → nothing:** `(f/forwardable (assoc CTX :hold {:on true :at 1}) nil)` ⇒ `[]`.
34. Answered in the log:
    `(f/forwardable (assoc CTX :log [{:at (- A 100) :member :le-testeur :action :allowed :by :twin :prompt-id IDE :text EDIT-T}]) nil)`
    ⇒ `[{:member :un-automate-monde :prompt PC}]`. With that entry's `:at (- A 5000)`, which is
    before the prompt's `:since`, both come back.
35. Stale crew: `(f/forwardable (assoc-in CTX [:crew :as-of] (- A 20000)) nil)` ⇒ `[]`. Roster:
    `(f/forwardable (assoc CTX :members #{:le-testeur}) nil)` ⇒ `[{:member :le-testeur :prompt PU}]`.
    `(f/forwardable (assoc CTX :crew nil) nil)` ⇒ `[]`.
36. `(f/forward-text "Un Automate" :un-automate-monde PC "reaches the network")` ⇒
    `"[from the crew] Un Automate asks to run: curl --version (risky: reaches the network) · answer with: bb crew-answer un-automate-monde allow|deny [--note \"…\"]"`.
37. `(f/forward-text "Le Testeur" :le-testeur PU "not a plain shell command")` ⇒
    `"[from the crew] Le Testeur asks to run: Edit file · src/pm/core.cljs · Do you want to make this edit to core.cljs? (risky: not a plain shell command) · answer with: bb crew-answer le-testeur allow|always|deny [--note \"…\"]"`.
    `(f/forward-text "Le Docteur" :le-docteur PL nil)` ⇒
    `"[from the crew] Le Docteur asks to run: ls · answer with: bb crew-answer le-docteur allow|deny [--note \"…\"]"`.
38. `(f/record-forward nil :le-testeur PU A)` ⇒ `{:as-of A :forwarded {:le-testeur {:id IDE :at A}}}`.
    `(f/parse-forwarded (pr-str that))` ⇒ the same map.
    `(f/parse-forwarded (pr-str {:as-of 1 :forwarded {:a {:id "abc" :at 1} :b {:id IDE :at 2}}}))`
    ⇒ `{:as-of 1 :forwarded {:b {:id IDE :at 2}}}`. `(f/parse-forwarded "x")` ⇒ `nil`.
39. `(f/answer-request ["le-testeur" "allow"] CR nil)` ⇒
    `{:ok true :member :le-testeur :action "approve" :prompt-id IDE :text nil}`.
    `["le-testeur" "always"]` ⇒ the same with `:action "always"`.
40. `(f/answer-request ["un-automate-monde" "deny" "--note" "  use bb instead  "] CR nil)` ⇒
    `{:ok true :member :un-automate-monde :action "deny" :prompt-id ID2 :text "use bb instead"}`.
41. **The forwarded id wins:**
    `(f/answer-request ["un-automate-monde" "allow"] CR {:forwarded {:un-automate-monde {:id ID1 :at 5}}})`
    ⇒ `{:ok true :member :un-automate-monde :action "approve" :prompt-id ID1 :text nil}`. At the
    bridge that id meets `decide` → 409 `"prompt changed"` (case 27): stale.
42. Validation, each ⇒ `{:ok false :reason …}`:
    - `[]` and `["le-testeur"]` → USAGE;
    - `["le-testeur" "yes"]` → USAGE;
    - `["Le Testeur" "allow"]` → `"bad member"`;
    - `["le-testeur" "allow" "--note" "x"]` → `"--note goes with deny"`;
    - `["le-testeur" "deny" "--note"]` → USAGE;
    - `["le-testeur" "deny" "--force"]` → USAGE;
    - `["le-testeur" "deny" "--note" "   "]` → `"empty note"`;
    - `["le-testeur" "deny" "--note" (apply str (repeat 1001 "a"))]` → `"note too long"`;
    - `["le-testeur" "deny" "--note" "a\tb"]` → `"control characters in note"`;
    - `["le-coureur" "allow"]` with `CR` → `"no prompt from le-coureur to answer"`;
    - `["le-testeur" "allow"]` with `nil nil` → `"no prompt from le-testeur to answer"`.
43. `(f/answer-body {:ok true :member :le-testeur :action "deny" :prompt-id IDE :text "no"} TOK)` ⇒
    `{"token" TOK "action" "deny" "prompt-id" IDE "by" "twin" "text" "no"}`. With `:text nil` there
    is no `"text"` key.

### crew.edn parse (`pm.crew`)

44. `(get-in (c/parse (pr-str {:as-of 5 :agents {:t {:status :blocked :prompt (assoc (dissoc PU :since) :always 2)}}})) [:agents :t :prompt :always])`
    ⇒ `2`. With `:always 1`, `:always "2"` or `:always 10`, the prompt is kept (`:id` ⇒ `IDE`)
    and `:always` is absent.

### 💭 (`pm.talk`)

Fixtures: `SAID = {:as-of A :said {:le-coureur [{:at (- A 3600000) :text "old" :question? false} {:at (- A 2000) :text Q :question? true}] :un-automate-monde [{:at (- A 1000) :text P1 :question? false}]}}`,
`CS = {:as-of A :agents {:le-coureur {:status :idle :since (- A 2000)} :un-automate-monde {:status :working}} :titles {:le-coureur "Le Coureur" :un-automate-monde "Un Automate"}}`.

45. `t1 = (tk/observe-said nil SAID CS A)`: `(:entries t1)` ⇒
    `[{:n 1 :who "Le Coureur" :text Q :at (- A 2000) :kind :crew-said :member :le-coureur :question? true}
      {:n 2 :who "Un Automate" :text P1 :at (- A 1000) :kind :crew-said :member :un-automate-monde :question? false}]`.
    The hour-old `"old"` item is skipped. `(:said-at t1)` ⇒ `{:le-coureur (- A 2000) :un-automate-monde (- A 1000)}`,
    and `(:queue-end t1)` ⇒ `0`.
46. `(tk/observe-said t1 SAID CS A)` is `identical?` to `t1`. With a new item
    `{:at A :text "Next." :question? false}` for `:un-automate-monde`, there are 3 entries and the
    last has `:n 3 :text "Next."`.
47. Labels: `(tk/crew-label (tk/entry t1 1) 0)` ⇒ `(str "Le Coureur asks: " Q)`, and
    `(tk/crew-label (tk/entry t1 2) 0)` ⇒ `(str "Un Automate · " P1)`.
48. Quiet: `(tk/unread t1)` ⇒ `0`, `(tk/counts? (tk/entry t1 1))` ⇒ `false`, and
    `(contains? tk/crew-kinds :crew-said)` ⇒ `true`.
49. **The question "!":** `(tk/said-questions t1 CS A)` ⇒ `{"le-coureur" {:text Q :since (- A 2000)}}`.
    `:un-automate-monde` is left out: its latest line isn't a question, and it is working.
50. "!" clears in each of these cases, every one ⇒ `{}`:
    - the member is working again: `CS` with `:le-coureur {:status :working}`;
    - its status changed later: `:le-coureur {:status :idle :since (+ A 4000)}`, which is past
      `(- A 2000)` + 5000;
    - the player replied: `t1` threaded through
      `(tk/crew-say :le-coureur "Le Coureur" "yes, try it" A)` (`:sending`);
    - stale crew: `(assoc CS :as-of (- A 20000))`.

    But a `:failed` reply keeps it: thread `crew-say` then `(tk/crew-result n {:result :error :reason "bridge off"} A)`
    ⇒ still `{"le-coureur" …}`.
51. **`--as` lines in the member's thread** (twin "Hal"):
    `WS = {:jumeaux {:twin {:name "Hal"}} :walkers [{:agent :hal :name "Hal"} {:agent :un-automate-monde :name "Un Automate"}] :says {"un-automate-monde" {:text "Drafted the bench behind the house." :since A} "hal" {:text "On it." :since A}}}`.
    `(:entries (tk/observe-says nil WS))` ⇒
    `[{:n 1 :who "Un Automate" :text "Drafted the bench behind the house." :at A :kind :crew-reply :member :un-automate-monde}]`.
    The twin's own say is not added here. `(:says-at …)` ⇒ `{"un-automate-monde" A}`. A second
    `observe-says` with the same world is `identical?`. Its label ⇒
    `"Un Automate · Drafted the bench behind the house."` and `counts?` ⇒ `true`.
52. **Second twin:** `WA = {:jumeaux {:twin {:name "Ada"}} :walkers [{:agent :ada :name "Ada"} {:agent :hal :name "Hal"}] :says {"hal" {:text "Hi." :since A} "ada" {:text "Hello." :since A}}}`
    ⇒ one entry `{:who "Hal" :member :hal :text "Hi." …}`: here `:hal` is a member. An agent in no
    walker but in `[:crew :titles]` takes that title, and an agent in neither takes its own name
    string.
53. **Prompt controls:** with `PE = {:n 1 :kind :crew-prompt :state :pending :member :le-testeur :prompt (dissoc PU :since)}`:
    - `(tk/prompt-controls PE true)` ⇒ `{:allow? true :always? true :disabled? false :hint nil}`.
    - `(tk/prompt-controls (update PE :prompt dissoc :always) false)` ⇒ `{:allow? true :always? false :disabled? true :hint "bridge off"}`.
    - `(tk/prompt-controls (assoc PE :state :sending) true)` ⇒ `{:allow? true :always? true :disabled? true :hint "sending…"}`.
    - `(tk/prompt-controls (assoc PE :state :allowed) true)` ⇒ `nil`.
    - Unchanged: `(tk/crew-controls PE true)` ⇒ `nil`, because unknown kinds aren't allowable.
54. **Answering an unknown prompt:** with `tp = (tk/observe-crew nil {:as-of A :agents {:le-testeur {:status :blocked :prompt (dissoc PU :since)}} :titles {:le-testeur "Le Testeur"}} A)`:
    - `(tk/crew-send tp 1 :approve nil)` sets `:state :sending :action :approve`, and
      `(tk/crew-send tp 1 :allow nil)` is `identical?` to `tp`.
    - Resolving with `(tk/crew-result … 1 {:result :ok} A)` gives `:state :allowed`, and
      `(tk/crew-label (tk/entry … 1) -480)` ends with `" · ☑ allowed 5:00 PM"`.
    - The same with `:always` ends with `" · ☑ always allowed 5:00 PM"`.
    - `(tk/crew-send (tk/observe-crew nil {:as-of A :agents {:le-testeur {:status :blocked :prompt (dissoc PU :since :always)}} :titles {:le-testeur "Le Testeur"}} A) 1 :always nil)`
      is identical: no always option.
55. **The command as read:** with
    `tb = (tk/observe-crew nil {:as-of A :agents {:un-automate-monde {:status :blocked :prompt {:id IDB :kind :bash :text (:text (pr/read-prompt BASH R)) :risky true}}} :titles {:un-automate-monde "Un Automate"}} A)`,
    the entry's `:text` has count `173` (`"wants to run "` is 13, plus the 160-char preview),
    starts with `"wants to run mkdir -p /private/tmp/pm-fixture for name in bench lamp-post table planter; do printf"`
    and ends with `"…"`. `(tk/crew-label (tk/entry tb 1) 0 {:twin "Hal"})` starts with
    `"⚠ Un Automate · wants to run mkdir -p"` and ends with `"… · Hal reviews this one"`.
    `(tk/prompt-preview "ls")` ⇒ `"ls"`, and `(count (tk/prompt-preview (apply str (repeat 161 "x"))))` ⇒ `160`.
56. **Persistence:** `(:entries (tk/restore (tk/save-text t1)))` ⇒ `(:entries t1)`, and
    `(:said-at (tk/restore (tk/save-text t1)))` ⇒ `(:said-at t1)`. `(tk/restore (tk/save-text tk/empty-talk))`
    has no `:said-at` key. A saved `:crew-said` entry without a keyword `:member` is dropped.

That makes **56 numbered cases**. They cover every new entry point: `prose-blocks`, `last-said`,
`summary`, `question?`, `turn-ended?`, `claude-members`, `add-said`, `parse-said`, the
`parse-screen` border strip, `always-option`, `with-always`, `risk-reason`, `validate`, `decide`
`:approve`/`:always`, `log-action`, `log-line`, `step-argv :read-recent`, `relay-argv`,
`relay-ok?`, `forwardable`, `forward-text`, `record-forward`, `parse-forwarded`,
`answer-request`, `answer-body`, `crew/parse :always`, `observe-said`, `said-questions`,
`observe-says`, `prompt-controls`, `crew-send`/`crew-result` with the new actions, `crew-label`,
`prompt-preview` and save/restore. Both twins (Hal, Ada) and several members appear, so nothing is
specific to one member.

---

## 14. `builder-notes.md` (run dir): how Hal checks it live

The builder writes these steps out concretely:

1. **Restart the services** (Hal does it): `bb crew-status`, `bb crew-bridge`, `bb crew-fielder`.
   Then `npx shadow-cljs watch app` and open the game.
2. **A question:** `herdr agent prompt <member> "…reply with one sentence that ends in a question…"`.
   When it goes idle, read:
   - `state/crew-said.edn` gets one item with `:question? true`;
   - in 💭, that member's thread shows `<Name> asks: …`, quiet (no badge);
   - its row and walker show the "!";
   - replying in that thread types into the pane (`crew/bridge.log`: `<member> say 200 sent`), and
     the "!" clears.
3. **A turn with tool calls:** any ordinary task. Its last ⏺ block, ≤ 240 chars with "…", is the
   only new line.
4. **An unknown-shape prompt:** stage an Edit/Write outside auto mode, or a long multi-line Bash
   command like the fixture's. Read:
   - `state/crew.edn` has the prompt, with `:always` when offered;
   - 💭 shows `⚠ <Name> · …` with **Allow / No**, and **Always** when offered, plus
     `<twin> reviews this one`;
   - `crew/fielder.log` shows `<member> forward 200 forwarded to the twin`, and the twin's chat
     has the `[from the crew] …` line;
   - `state/crew-forwarded.edn` has its id.
5. **Answer it as the twin:** `bb crew-answer <member> allow`, which prints `<member>: allowed`.
   Read:
   - `crew/bridge.log`: `<member> approve 200 allowed by the twin`;
   - `state/crew-log.edn` gets an entry with `:by :twin`;
   - 💭 shows the line "already answered by <twin>" and the quiet `<twin> allowed `…` for <Name>`.

   Then run it again: `crew-answer: <member>: not blocked (stale: …)`, exit 1.
6. **Hold:** with "I'll answer" on, stage another risky prompt. Nothing is forwarded (no `forward`
   line), and Allow from 💭 sends `approve` with `:by :player`.
7. **`--as`:** `bb hal-say "test line" --as <member>` shows on the plate **and** as a line in that
   member's thread, with a badge.

---

## 15. Docs (le-docteur): `crew/README.md`

Keep it to the **Run it** services paragraph (step 2, around lines 125–140), and add:

- `bb crew-status` also writes `state/crew-said.edn`: each Claude member's last words of a turn,
  which become a quiet line in its 💭 thread; a turn ending in a question raises its "!".
- `bb crew-fielder` also forwards every prompt it won't answer (risky, unreadable) to the twin's
  chat, never while "I'll answer" is on.
- The twin answers those with `bb crew-answer <member> allow|always|deny [--note "…"]`, through the
  bridge's same prompt check (stale if the dialog changed).
- 💭 prompt lines now have Allow / No (and Always).

No PLAY.md change: see §1.

---

## 16. Open, for Hal (not this round)

- The ⚠ line still says "<twin> reviews this one" while "I'll answer" is on, when the twin isn't
  forwarded anything. `crew-label` doesn't know the hold, and the pinned texts keep it. A
  hold-aware label would be a small follow-up.
- `docs/GDD.md` should record the mirror's rules (prose back, questions as "!", the twin answers
  first via `crew-answer`). Hal decides who writes that, since no one is assigned here.
- `tk/crew-controls` is now unused by the UI. It is kept because existing tests pin it.
