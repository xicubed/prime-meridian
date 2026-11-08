# Review: crew-fielder-v0 (20261108-1146)

## Conformance summary

Gate history: automated gate FAILED 3x (gate-1/2/3) on one pre-existing test the spec's §4.8 edit
list missed (`overlay_test.cljs:177`, `"fern (Hal)"` vs. the correct "fern (Your twin)" for a
hand-built world with no `:jumeaux`, per log.md's manager note). Hal fixed that one expectation
by hand and re-ran the gate; I re-ran it again myself just now: PASS, 0 warnings, 627 tests /
3481 assertions. `git diff -- test/pm/overlay_test.cljs` confirms the live diff is exactly that
one-line fix plus the tester's own listed §4.8 edits — no other drift.

Boundary table (spec §1): builder touched exactly the assigned files (`bb.edn`,
`resources/estate.edn`, `scripts/crew_bridge.clj`, `scripts/crew_status.clj`, `src/pm/bridge.cljc`,
`src/pm/content.cljs`, `src/pm/core.cljs`, `src/pm/crew_log.cljc`, `src/pm/overlay.cljs`,
`src/pm/talk.cljs`, `src/pm/ui.cljs`, plus new `scripts/crew_fielder.clj`, `src/pm/fielder.cljc`,
`src/pm/jumeaux.cljc`). Tester touched only the new `test/pm/crew_fielder_v0_test.cljs` and the
§4.8-listed lines of the three old test files. None of the "nobody" files (`src/pm/prompt.cljc`,
`src/pm/crew.cljs`, `src/pm/world.cljs`, `src/pm/dev_http.clj`, `public/**`, `resources/content/**`,
`.gitignore`, `state/*`) appear in `git status`.

Security-code pass (hand-traced against the diff, not test names):
- **Risk classification has one source.** `pm.fielder` requires only `pm.bridge`/`pm.crew-log`
  (confirmed in `src/pm/fielder.cljc`'s ns form) — no `pm.prompt` require, no local risk list.
  `verdict`'s `:unknown`/`:risky` checks read `:kind`/`:risky` off the already-parsed prompt (which
  `pm.prompt/risky?` populated via the watcher), and `attempt` re-derives the same two checks
  live through `b/decide`, whose `read-prompt`-sourced `:kind`/`:risky` is the only other place
  risk is decided. `scripts/crew_fielder.clj` itself calls `pr/read-prompt` (never a second
  classifier) when building `live`. Risky is `pm.prompt/risky?`, full stop, checked twice
  (published-state gate in `verdict`, live gate in `attempt`/`decide`), never copied.
- **`verdict`'s cond order** re-derived from `src/pm/fielder.cljc`: `:hold` → `:stale` →
  `:not-in-roster` → `:not-blocked` → `:unknown` → `:risky` → `:no-since` → `:answered` →
  `:too-soon` → `:answer`, exactly spec §3.4's numbered order. `:risky` is `(not (false? (:risky
  p)))` — fails closed on `nil`/truthy/non-boolean, only an explicit `false` passes (t35).
- **Hold is checked twice**, at different layers: `verdict`'s `:hold` branch gates what's even
  considered `:answer` at poll time, and `scripts/crew_fielder.clj`'s `handle-one!` re-reads
  `state/hold.edn` fresh immediately before acting (`(if (cl/hold-on? (cl/parse-hold
  (slurp-or-nil hold-file))) tried ...)`), closing the race between `due`'s snapshot and the
  action. On hold, no `remember` call happens (matches spec's "skip (no remember)" exactly), so a
  held sighting is retried once hold lifts rather than silently forgotten.
- **Same prompt id, live re-read.** `attempt` is `(b/decide {:action :allow :prompt-id (:id
  prompt) :text nil} live)` — reused verbatim from `pm.bridge`, not duplicated; `live`'s prompt
  comes from a fresh `herdr agent read … --source visible` + `pr/read-prompt` right before
  acting, and `decide`'s own `(not= (:id prompt) prompt-id)` check (409 "prompt changed") is what
  enforces "same id now as when it was decided due." `scripts/crew_fielder.clj` never bypasses
  this: `d` is always the result of `f/attempt`, never a hand-built `{:ok true}`.
- **Same roster check as the bridge.** `ctx-now` builds `:members` as `(set (keys (b/roster
  (crew-json))))` — the identical call `crew_bridge.clj`'s `handle` makes — not a separate or
  looser membership test.
- **Never declines, never answers `:unknown`.** `log!` always logs action `:allow` (hardcoded);
  there is no code path in `pm.fielder` or `crew_fielder.clj` that constructs a `:deny` decision
  or calls `b/decide` with `:action :deny`. `:unknown` prompts are excluded before `:risky` is
  even checked (`verdict` step 5), and `attempt`'s reused `decide` independently 403s a non-`:bash`
  kind regardless. t41 confirms every `attempt` result's `:steps` is `nil` or exactly
  `[[:keys "1"]]` — never an `:esc`/decline step.
- **Herdr via argument vector, no shell.** Every Herdr call in `crew_fielder.clj` goes through
  `(p/sh (b/step-argv herdr member [...]))`, and `step-argv` (unchanged, shared with the bridge)
  returns a plain vector (`[herdr "agent" "send-keys" m "1"]` etc.) that `babashka.process/sh`
  runs without a shell — no string-interpolated command, no user-controlled text reaches argv
  positions beyond the fixed literal `"1"` and the already-roster-checked `member` keyword.
- **Logs never hold the token or a raw screen.** `log!` calls `b/log-line` with only
  member/action/status/reason (no prompt text, no token — same structural shape as the bridge's
  logger). The crew-log entry (`f/log-entry` → `cl/entry`) carries only `:id`/`:text` pulled off
  the live prompt map, never a screen or a token; `write-crew-log!` writes only
  `state/crew-log.edn`, atomically (tmp + rename), and the fielder never writes `state/hold.edn`
  (only the bridge's `hold` action does that) — matching the disjoint-write-paths pattern from
  crew-talk-v1.
- **No "Hal" literal in code.** Ran `grep -rnw Hal src/ scripts/` myself: no output (exit 1).
  `pm.jumeaux/twin` is the single chokepoint every display path (`talk`, `overlay`, `content`,
  `ui`, `core`, the bridge's 403 reason) goes through for the twin's name; a nil/malformed `jum`
  always falls back to `"Your twin"`, never throws.
- **`:jumeaux` reader's fallbacks** (`pm.jumeaux/names`): non-map `data`/`:jumeaux`/`:player`/
  `:twin` each independently degrade to `{}` rather than throwing; `:sex` is accepted only as
  exactly `:m`/`:f`, else `nil`; a name is used only if it's a control-char-free, non-blank string
  ≤ 32 chars after trimming, else the default. Hand-traced against t03/t04/t05's edge cases
  (non-map blocks, bad `:sex` types, overlength/blank/non-string names) — all match.
- **Old `:hal`/`:michael` log entries still read.** `pm.crew-log/role` and `upgrade-entry` map
  legacy keys to `:twin`/`:player`; `parse` and `append` both run every entry (old base entries
  and the new one) through `upgrade-entry` before `valid-entry?` — `valid-entry?` itself is
  strict on the new `bys` set only, so a legacy entry is invalid *until* upgraded but is always
  upgraded first, never dropped (t11/t12). `pm.talk/observe-log`, `log-label`, `crew-label`, and
  `restore` all go through `cl/role`/`cl/upgrade-entry` too, so a live file with old entries
  (Michael's actual `state/crew-log.edn`, per the quest's "Done when") renders correctly end to
  end, not just at the storage layer.
- **No live interaction.** `replies/build-*.txt` and `fix-*.txt` show only the builder/tester's
  own local shell-approval prompts (git diff, a bb syntax-check) — no `herdr send-keys`/`prompt`,
  no `bb crew-bridge`/`crew-status`/`crew-fielder` invocation. The running `bb crew_bridge.clj` /
  `crew_status.clj` processes (`ps aux`) started 10:07, well before this round's 11:46 `spec`
  step — Hal's own pre-existing live processes, not started by a crew member this round. No
  `bb crew-fielder` process is running at all.
- `replies/review-le-critique.txt` is again a terminal capture of this very reviewing session
  (same tool-call sequence as mine) — not independent corroboration, excluded per the crew-talk-v1
  review's precedent.

## Findings

1. Spec §5 assigns le-docteur a `docs/GDD.md` paragraph replacement and a `crew/README.md`
   edit, and assigns Hal (manager) the `.crew/crew.json` `crew-fielder` service entry. None of
   `docs/GDD.md`, `crew/README.md`, `.crew/crew.json` appear in `git status`, and `log.md` shows
   no `docs` step ran this round (only `spec`/`build`/`gate`×3/`review`). Same gap pattern as
   `crew-talk-v1`'s review: the docs/config deliverables from spec §5 are outstanding and should
   be done (or explicitly deferred) before the round is called finished.
2. The manager's gate-3 fix (`test/pm/overlay_test.cljs:177`) is a correct, spec-consistent
   one-line expectation change (missing-`:jumeaux` world → `"Your twin"`, per spec §2's documented
   fallback), made necessary by a genuine gap in spec §4.8's edit list rather than a build error.
   Not a defect, but worth noting in the spec/process history so a future quest's test-edit list
   double-checks every hand-built (non-estate) world fixture against the new fallback behavior.

No security-relevant findings: the fielder's risk classification, hold/roster/id re-checks,
no-decline/no-unknown guarantee, Herdr invocation shape, and log contents all matched the spec
on hand-inspection, and no crew member or script ran a live Herdr/bridge/fielder action during
the round.
