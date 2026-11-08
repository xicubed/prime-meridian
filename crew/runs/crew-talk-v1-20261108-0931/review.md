# Review: crew-talk-v1 (20261108-0931)

## Conformance summary

Re-ran the gate myself: `bash .crew/gate.sh` → PASS, 0 warnings, 583 tests / 3232 assertions —
matches `gate-note.md`/`gate-1.txt` exactly.

Boundary table (spec §1): builder touched exactly `public/index.html`, `resources/estate.edn`
(only the new `:crew-talk` key — confirmed, nothing else in that file's diff changed),
`scripts/crew_bridge.clj`, `scripts/crew_status.clj`, `src/pm/bridge.cljc`, `src/pm/core.cljs`,
`src/pm/crew.cljs`, `src/pm/prompt.cljc`, `src/pm/talk.cljs`, `src/pm/ui.cljs`, and new
`src/pm/crew_log.cljc`. Tester touched only the new `test/pm/crew_talk_v1_test.cljs`. None of
the "nobody this round" files (`shadow-cljs.edn`, `.gitignore`, `dev_http.clj`, `bb.edn`,
`.crew/crew.json`, `test/pm/fixtures.cljs`, `test/pm/crew_talk_test.cljs`, fixture screens,
`public/vendor/`, `resources/content/`) appear in `git status`. `pm.crew-talk-test` (v0) and the
rest of the suite pass unchanged, confirming the compatibility rules in spec §1 held.

Security-code pass (validate/decide re-derived by hand against `src/pm/bridge.cljc`):
- `validate`'s `cond` order is exactly method → uri (crew-or-hold) → body-map → token → origin →
  hold-branch → crew-branch, matching spec §3.3 and t26's precedence cases (bad token+bad on →
  401; bad origin+bad action → 403) by construction, not just by a passing test name.
- Token check (`token=`) is unchanged, exact-length constant-time compare, fails closed on any
  type/length mismatch. Origin check is exact string equality (`not= origin allowed-origin`),
  case-sensitive, fails closed on `nil`. `/HOLD` (wrong case) correctly 404s.
- `decide` is byte-for-byte unchanged (confirmed via diff — not touched by this round): for
  `:allow` it checks `(:kind prompt) ≠ :bash` → 403 "unknown prompt" *before* the risky check,
  and `read-prompt` always sets `:risky true` on an `:unknown` kind, so an `:unknown` prompt is
  rejected by the bridge twice over (kind gate, then would-be risky gate) even if a crafted
  request got past the game's own `allowable?` (which also requires `:kind :bash` and
  `:risky false` — `src/pm/talk.cljs:413-418`). Two independent, structural layers, not one.
- `hold` validation reuses the same token/exact-origin checks via the shared `cond` chain (not a
  parallel/duplicated check), then only ever writes `state/hold.edn` (`scripts/crew_bridge.clj`'s
  hold branch calls `write-edn!` on `hold-file` alone, no Herdr call, no `crew-log.edn` touch).
  The `/crew/<m>` allow/deny success path writes only `state/crew-log.edn` (`append-crew-log!`),
  so the two state files are written from disjoint code paths.
- The two-poll rule (`pm.prompt/observe-prompts`) only assigns `:prompt`/publishes when the same
  `:id` is seen on two polls with `now − seen-at ≥ confirm-ms` (2000), and both polls had to
  independently pass `prompt-screen?` (a `nil` read-prompt forces `:working` and clears the
  chain, t18) — so a real dialog is required on both polls, not just the first.
- Grepped `scripts/crew_bridge.clj`'s `log!`/`append-crew-log!`: the bridge log line never
  receives the token (only member/action/status/reason), and `pm.crew-log/entry` only pulls
  `:id`/`:text` off the live prompt — structurally, `crew-log.edn` entries have no slot for a
  token or a raw screen. `crew_status.clj`'s `screens-for`/`observe-prompts` never write the raw
  screen to `crew.edn`; only `:status` and the parsed prompt (`:id :kind :text :risky :since`)
  are persisted.
- `src/pm/ui.cljs`'s new rendering (`render-roster!`, `render-hold-switch!`, the `quiet`/`gone`
  classes) only reads fields already produced by the pure `pm.talk`/`pm.crew-log` functions
  (names, statuses, `log-label`/`crew-label` text) — nothing new crosses from a raw screen to the
  DOM.
- No live Herdr call anywhere in `test/pm/crew_talk_v1_test.cljs` — all 50 cases exercise pure
  functions against the six fixture files or hand-built screens/maps. `replies/build-*.txt` show
  only the builder/tester's own local shell-approval prompts (unrelated python/bb commands),
  never a `herdr agent send-keys`/`prompt` invocation or `bb crew-bridge`/`crew-status`. The
  running `bb scripts/crew_bridge.clj` / `crew_status.clj` processes (`ps aux`) started 09:29,
  two minutes before this round's `spec` step (09:31) and state/bridge.edn's mtime trails them
  continuously — these are Hal's pre-existing live processes, not something a crew member in
  this round started.
- `replies/review-le-critique.txt` is a terminal capture of this very reviewing session (its own
  tool-call list matches mine turn for turn) — not independent corroboration; excluded from the
  above, which was verified directly against the diff/spec.

Hand-traced a sample of the 50 test cases against the implementation (not just "tests pass"):
t20-t24 (`pm.crew-log` validity/append/for-member/hold edge cases, including the tie-break and
cap-rotation math), t26 (validate precedence), t45 (the `since − 5000` boundary in
`mark-answered`) all match the code's actual branches, not just the expected literal.

Names used throughout (tests and fixtures) are all real roster members
(`le-testeur`, `un-automate-monde`, `un-architecte`, `le-critique`, `hal`) — nothing invented.

## Findings

1. `.crew/quests/crew-talk-v1.md` and `spec.md` §1 assign le-docteur one short GDD paragraph
   under the Talk verb's row; `docs/GDD.md` is untouched (no diff, mtime 2026-10-31, predates
   this round) and `log.md` shows no `docs` step ever ran this round (only `spec`/`build`/
   `gate`/`review`). Not a security gap, but a missed deliverable from spec §1 — the manager
   should dispatch le-docteur for that paragraph before calling the round done, or explicitly
   defer it.

No other findings rise above a style nit. The security-relevant surface (bridge validation
order, fail-closed token/origin checks, the hold action's scope, the two-poll publish rule, and
what crosses from a screen to a state file to the DOM) all matched the spec on hand-inspection.
