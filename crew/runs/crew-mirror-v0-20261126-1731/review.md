# Review: crew-mirror-v0 (crew/runs/crew-mirror-v0-20261126-1731)

## Scope
Start commit `4bd14de` = current `HEAD` (no stray intervening commit, unlike the prior round), so a
plain `git diff`/`git status` is this round's own clean diff — no reconstruction needed.
`.crew/quests/placement-v1.md` (untracked, shown in `git status`) is a **future** round's quest
("Runs after crew-mirror-v0, same checkout") — excluded from this round's scope, noted for the
record only.

This review was split across four parallel passes (prose/prompt/bridge; fielder/scripts;
crew/talk/ui/core wiring; tests/fixtures/gate/boundary) and the single most severe finding below
was independently re-verified by me directly against the source before writing it up.

## Verified independently
- Re-ran `bash .crew/gate.sh` myself: **PASS — 0 warnings; 1633 tests / 8738 assertions**, matching
  `gate-note.md`'s claim after the manager's hand-fix. `pm.crew-mirror-test` genuinely runs (confirmed
  via `node out/test.js` directly — it's only missing from the gate script's own `tail -40` transcript
  because `crew-mirror` sorts early alphabetically, a script-truncation artifact, not a real gap).
- **Manager hand-fix** (`log.md`): confirmed `git diff -- test/pm/crew_talk_test.cljs
  test/pm/crew_talk_v1_test.cljs` is confined to exactly 4 lines — the bridge action-set literal
  (`#{"allow" "deny" "say"}` → includes `approve`/`always`) in two tests, and the bad-action example
  swapped from `"approve"` (now a real action) to `"nope"` in two others. Spec §0 item 4 explicitly
  predicted this exact conflict ahead of build. Legitimate correction of a spec-flagged test/spec
  conflict, not a cover for an implementation bug.
- Boundary table: every modified/new file maps to its row (builder: 12 edited + `src/pm/prose.cljc` +
  `scripts/crew_answer.clj` new; tester: `test/pm/crew_mirror_test.cljs` (56 `deftest`s, one per
  spec-numbered case, 216 `is` assertions) + the 5 fixture files, all present with matching line
  counts). `package.json`/`package-lock.json` diff is empty (no npm deps). Fixtures were swept for
  leaked paths/PII per the spec's own §12 disclosure claim — clean, aside from one harmless
  `"110.8k tokens"` UI-hint string.
- `pm.prose.cljc`'s extraction rules were executed against the spec's own 30 worked test vectors
  (not just read) and confirmed pure and correctly excluding tool calls, `⎿` output, collapsed
  tool-group summaries, notices, and diff/file content from every prose block — the "only prose
  crosses, never tool output/paths/file contents" property holds structurally in `pm.prose`,
  `pm.bridge`'s `validate`/`decide`, and `crew_answer.clj`'s staleness path (same `answer-request` →
  `decide` check as every other answer route; no separate, weaker check).

## Findings (ranked)

1. **`src/pm/ui.cljs:949,963` — the "Allow" button is wired to `on-allow`, not `on-approve`, so it
   silently no-ops on every risky/unknown prompt.** `render-crew-row!` destructures `on-allow` and
   calls `(on-allow (:n entry))` from the Allow button's click handler, even though `core.cljs:1096-97`
   builds and passes a separate `on-approve` for exactly this purpose. `on-allow` (`core.cljs:810`)
   sends `talk/crew-send ... :allow`, which still requires `(allowable? e)` — the *old* gate
   (bash-kind and not risky). `:approve` is the new, ungated action this round adds specifically so
   risky/unknown prompts can be allowed (quest decided item 4: "risky prompts keep the ⚠ and reason,
   and still get Allow: it is the player's call"). I confirmed this by reading both functions and
   `crew-send`'s guard directly: clicking Allow on any `:unknown`-kind or risky prompt today reaches
   `crew-send`, fails the `allowable?` check, and returns `talk` unchanged — nothing is sent. This
   breaks the round's headline feature. **Fix:** wire the Allow button to `on-approve`.

2. **`scripts/crew_fielder.clj` — the hold switch isn't re-read right before forwarding, and three
   smaller forwarding gaps all live in the same function.** Forwarding uses one `ctx` snapshot taken
   at the top of the poll loop; `handle-one!` (routine answers) independently re-reads the hold file
   live, but the forwarding path never does, so hold flipping ON mid-poll (after the due-answer step's
   real shell I/O) doesn't stop an in-flight forward — spec §11.3 step 1 ("Re-read hold. If it is on,
   stop.") is unimplemented as its own check. In the same file: the risk-reason fallback
   `(or (pr/risk-reason ...) (when (:risky p) "flagged risky"))` is dropped (bare `risk-reason` call),
   so a risky prompt matching none of `risk-reason`'s rules gets no `(risky: …)` annotation at all;
   `--dry-run` skips calling `forwardable` entirely instead of logging "dry-run: would forward" and
   recording in memory (spec §11.3 step 4); and the "no twin chat (state/hal-session)" one-time log
   guard (step 2) isn't implemented, so a missing session just fails the relay silently every poll.
   None of these leak any screen text/paths (the "only prose crosses" property holds), but the hold
   gap is a real security-relevant miss for a round explicitly flagged as security-sensitive.

3. **`src/pm/talk.cljs` `said-questions` (§8.3) diverges from spec in three ways.** `said-grace-ms`
   (spec: 5000ms tolerance between a said item's `:at` and the status's `:since`) is never defined or
   used — the freshness check is a bare `(<= (:since a) (:at e))`, so any few-ms skew between the two
   timestamps clears the "!" immediately instead of tolerating 5 seconds. The "no `:since`" case is
   inverted: spec says a missing `:since` should *keep* the "!" up; the delivered code clears it.
   Only `:idle` status is checked, not spec's `#{:idle :done}}`, so a member ending a turn via `:done`
   never raises the "!" at all. **Fix:** add `said-grace-ms`, correct the missing-`:since` fallback
   direction, and check both `:idle` and `:done`.

4. **`src/pm/talk.cljs` `observe-said` (§8.2) only surfaces the single latest item per member, with
   no `quiet-ms` staleness filter.** It does `(peek (vec (get-in said [:said m])))` instead of
   appending every item with `(> at since)` and `(> at (- now quiet-ms))`. Two consequences: a
   long-stale said item on first load is surfaced instead of silently marked seen, and if 2+ new items
   land for one member between polls, all but the newest are dropped instead of each becoming its own
   💭 line. The spec's own test cases (45/46) only exercise single-item scenarios, so the gate doesn't
   catch it. Related, same §8: `observe-says` (§8.6, the `--as` fix) hand-rolls `append-crew` instead
   of reusing `crew-reply` as directed — it skips `crew-reply`'s trim/blank-`who` validation, so an
   `--as` line with stray whitespace is stored untrimmed. Lower severity than the main miss, but a
   real divergence from the explicitly-requested design (reuse `crew-reply`, "the mirror of `crew-say`").

5. **`crew/README.md` (le-docteur, spec §15) hasn't been updated yet.** `log.md` shows only
   spec/build/gate(×3)/review steps — no docs step has run. The "Run it" services paragraph (lines
   ~126-133) still describes the old `crew-status`/`crew-bridge`/`crew-fielder` behavior only (no
   `approve`/`always`, no prompt-forwarding, no `bb crew-answer`), and the Runs table has no
   `crew-mirror-v0` row. Same pattern as the prior round's missing PLAY.md update — the quest's own
   "Done when" and spec §15 both require this before the round can close.

Minor, non-blocking nit: 4 of the 5 new fixture files have a handful of non-breaking-space (U+00A0)
characters where spec.md's fenced text has a plain space (e.g. on `⎿`/`❯` separator lines) — a small
copy-fidelity deviation from "copied verbatim," but harmless to test correctness since those lines
fall on tool-output/composer lines the extraction rules already exclude.
