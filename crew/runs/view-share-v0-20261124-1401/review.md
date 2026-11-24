# Review — view-share-v0 (2026-11-24 14:01)

## Scope
Start commit `6b469db` = current `HEAD`, so `git diff` shows exactly this round's work (no
stacking). Diffed every edited file (`bb.edn`, `src/pm/bridge.cljc`, `scripts/crew_bridge.clj`,
`src/pm/core.cljs`, `src/pm/three.cljs`, `src/pm/lot.cljc`, `scripts/blueprint_draft.clj`,
`test/pm/bridge_real_test.cljs`, `test/pm/lot_test.cljs`) and read the four new files
(`src/pm/view.cljc`, `scripts/where.clj`, `test/pm/view_test.cljs`, plus the run dir) in full,
against `spec.md` (483 lines) and the quest in full.

## Verified independently
- Re-ran `bash .crew/gate.sh` myself: **PASS, 0 warnings, 1465 tests / 8049 assertions**,
  matching `gate-note.md`. Baseline check: summed `deftest` across every `test/*.cljs` at the
  start commit `6b469db` → **1439**; this round's new tests (v01–v15 + t05–t08 bridge + t43–t49
  lot = 15+4+7 = **26**, each count confirmed by grep) → 1439+26 = 1465, exactly what the gate
  reports. No test was dropped or weakened; `t22`–`t42` of `lot_test.cljs` are untouched
  (`git diff` only appends `t43`–`t49` at the file's end).
- `/view`'s guard order and status codes match `/hold`'s exactly: method → route (uri now
  includes `view-uri`) → json → token → origin, all before the `(= uri view-uri)` branch; every
  malformed field (`action`, `camera`/pos/target, missing/invalid `center`, non-sequential/
  oversized/malformed/duplicate-id `walkers`) returns 400 with the exact reason string the spec
  gives, in the spec's own check order — confirmed by reading `pm.bridge/validate`'s `cond`
  directly and by `t06-validate-view-refusals`/`t07-validate-view-guards` passing against the
  real `validate` fn (not a mock).
- Successful `/view` posts are not logged: `scripts/crew_bridge.clj`'s `:view` success branch
  (`crew_bridge.clj:313-315`) has no `log!` call; only the 500 write-failure path logs
  (`log! nil :view 500 "view not saved"`). Refusals still go through the existing shared `log!`
  call before the action dispatch, so a bad `/view` POST does show in `crew/bridge.log` as the
  spec requires (`log-line`'s action set now includes `"view"`).
- `state/view.edn` is written through the same `write-edn!` already used for `hold-file`/
  `builds-file`: spit to `<path>.tmp` then `.renameTo` — atomic, and reused unchanged (the
  builder didn't reimplement it).
- Demo/bridge-off/in-flight/throttle all verified structurally, not just by test: `core.cljs`'s
  `view-tick!` wraps its entire body in `(when-not DEMO …)` (no raycast or post even attempted in
  the demo build); `view/post?`'s `cond` checks `:demo?`, then `(not :bridge-on?)`, then
  `:in-flight?` before anything else; `:in-flight?` is set true by `view/sent` at send time and
  cleared by `view/settled` in both the fetch's `.then` and `.catch`, so a failed POST doesn't
  wedge the flag. Two independent throttles exist exactly as the spec separates them: the
  raycast/snapshot itself runs at most every `view/post-every-ms` via `:checked-at` (so
  `bricks/spots` and the ray aren't recomputed every frame), and the actual post decision inside
  `post?` separately enforces the same interval against `:last-at`, plus the 30 s keep-alive —
  matches `v05-post?`'s worked cases (2500 ms → false, 3000 ms → true, 31000 ms keep-alive → true).
- `pm.view` and `pm.lot` stay pure: both files' docstrings and a direct grep show no `js/Date`,
  no atoms, no three.js, no I/O; every function that needs the current time takes `now` as an
  argument (`fresh`, `post?`, `sent`, `view-edn`, `where-lines`) — only the I/O-layer scripts
  (`where.clj`, `blueprint_draft.clj`, `crew_bridge.clj`) call `System/currentTimeMillis` and
  pass it in.
- `--near here` / `--near <walker>` refusals: hand-traced `place`'s 7-arity `cond` against every
  row of t48 (stale view → "the game isn't open"; missing view → "isn't sending its view"; a
  known walker id with no view → same two reasons via `(or (:reason seen) no-view)`; `:here` with
  a view whose `:center` is nil → the new `sky` reason; an unknown name → the existing
  "I don't know a place called…" listing, with view names folded in) — every one matches the
  code's actual branch, not just the test's name, and `ground` not a map still wins first
  regardless of `near` (unchanged `no-ground-map` guard at the top of `place`).
- `test/pm/view_test.cljs`, `t05-t08` bridge additions and `t43-t49` lot additions build every
  fixture inline; no file touches `state/` (confirmed by grep — no bare `state/` path outside
  expected-reason string literals). `builder-notes.md` documents three hand runs, all through
  `PM_VIEW_FILE`/`PM_BLUEPRINTS_FILE` temp files with `--dry-run`.
- No person/pet/member name appears in the new/changed production code introduced by this round
  (`src/pm/view.cljc`, the `/view` additions to `src/pm/bridge.cljc`, `rig-view`/`center-hit` in
  `src/pm/three.cljs`, the `view-tick!` hunk in `src/pm/core.cljs`, `src/pm/lot.cljc`'s §5
  additions, `scripts/where.clj`, the new lines in `scripts/blueprint_draft.clj` and
  `scripts/crew_bridge.clj`) — grepped for Michael/Hal/Pip/Wren/Flora/Phoenix/Juniper and the
  only hits are pre-existing lines outside this round's hunks (e.g. `three.cljs`'s `ghost-fill`
  comment from Nov 22, `crew_bridge.clj`'s pre-existing `hal-session`/`relay-to-hal!` machinery).
- `git diff --stat -- package.json package-lock.json` is empty: no npm deps added.

## Findings
None rise above a style nit. The round is a faithful, hand-traceable implementation of the
spec's validation order, atomic write, logging exclusion, post-decision state machine, and the
view-as-landmarks extension to `pm.lot`, and the gate is genuinely green on a fresh run with the
exact expected test-count delta.
