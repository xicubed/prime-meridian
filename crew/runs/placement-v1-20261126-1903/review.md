# Review: placement-v1 (crew/runs/placement-v1-20261126-1903)

## Scope
Start commit `82b9e60` = current `HEAD` (no intervening commit), so `git diff`/`git status` is
this round's own clean diff. Files touched: `resources/estate.edn`, `scripts/blueprint_draft.clj`,
`src/pm/ask_build.cljc`, `src/pm/blueprint.cljs`, `src/pm/camera.cljs`, `src/pm/core.cljs`,
`src/pm/lot.cljc`, `test/pm/ask_build_test.cljs` (t29/t33 + the `RULES` comment only, as the
boundary table allows), plus the new `test/pm/placement_test.cljs`.

## Verified independently
- Re-ran `bash .crew/gate.sh` myself: **PASS — 0 warnings; 1688 tests / 8855 assertions**, matching
  `builder-notes.md`'s claim exactly.
- `test/pm/placement_test.cljs` has `t01`–`t54`, one `deftest` per spec-numbered case (54 total,
  114 `is` assertions), transcribed faithfully — spot-checked every group (A rules helpers, B/C
  `:open-kinds` in `spot`/`place`, D the widening search, E `moved-line`, F estate data, G
  `pm.ask-build`, H `pm.camera`, I `pm.blueprint/arrival-at`) against the spec's worked values.
- `src/pm/lot.cljc`'s `place` hand-traced directly against spec §2: pass 1 (`d <= S+eps`, unchanged
  shape) and pass 2 (`S+eps < d <= M+eps`, only when pass 1 is empty, adding `:moved-m`/`:why` from
  **pass 1's** whys) share one candidate list bounded by `M` — no ring-step loop, matching the
  spec's "don't add ring steps" note. Refusal (neither pass) uses `(refusal (concat whys1 whys2) M
  word)`. `spot`'s bad-cell branch correctly distinguishes "open kind, no `y`" (→ "most of it has
  no ground") from a genuinely closed kind (→ `cell-reason`) via `(contains? open kind)`. `refusal`
  itself is byte-identical (now built from `refusal-prefix`/`dominant-why`, same string).
  `:open-kinds [] → #{}` (nothing buildable) is the documented no-special-case behavior.
- Canopy (`T`) is placeable only through `:open-kinds` (default `#{"o"}` when missing/non-sequential,
  confirmed via `open-kinds`'s `sequential?` guard — a string like `"oT"` does NOT count as
  sequential-of-strings for this purpose and falls back to `#{"o"}`, matching test case 1).
  Bushes/trunks (`B`), rocks (`R`), and water (`~`/`:`) still go through unchanged `cell-reason`
  regardless of `:open-kinds` — there is no path that adds them to the open set.
- `pm.camera/fly-decision` hand-traced: check order is no-ghost → card-open → player-moving
  (`now-ms - last-move-ms < quiet-ms`) → in-view (on-screen AND 3-D distance ≤ `near-m`) → fly,
  exactly matching spec §4 and all of test cases 39–48 (including the boundary at exactly
  `near-m`=10 → `:in-view`, exactly `quiet-ms`=2000 ago → `:fly`, and the "no ghost beats everything
  else" ordering in t46). `flight`/`ghost-flight` are the CREW key's maths moved verbatim and reused
  by both `crew-key-tick!` (`core.cljs`, `fly!` now calls `camera/flight`) and `show-arrival!` — one
  flight, not two, per the quest.
- The fly-to never fires while a trial card is open (`:card-open? (some? (bp/card-of w))`) or within
  `:quiet-ms` (2000ms) of a player camera move (`@!camera-moved-at`, stamped on `pointerdown`,
  `pointermove`-while-dragging, and `wheel`, all `{:passive true}` so they never block input) — both
  checked ahead of `:fly` in `fly-decision`'s cond, and `show-arrival!` only calls `camera/frame!`
  when the decision is exactly `:fly`.
- The demo path gets the same flight: `demo-ask!` → `demo-merge!` → `apply-blueprint-file!` (the
  one hook, right after `reset! !world w3`) → `bp/arrival-at` → `show-arrival!` — no second,
  demo-specific hook, matching "wired once." `bp/arrival-at`'s first-merge-announces-nothing
  property (so a page reload never flies) is confirmed by test cases 53/54 against the real
  `merge-file`/demo-file paths.
- No npm deps: `git diff --stat -- package.json package-lock.json` is empty.
- No person/pet/member name appears anywhere in this round's own diff hunks (`lot.cljc`,
  `ask_build.cljc`, `camera.cljs`, the new `arrival-at` in `blueprint.cljs`, `blueprint_draft.clj`,
  and the `core.cljs`/`estate.edn` hunks) — the few `grep` hits for "Michael"/`:hal` in
  `blueprint.cljs` are all pre-existing lines this round didn't touch (confirmed against
  `git diff`'s own hunks, not just a raw file grep).

## Findings

1. **`.crew/roles/builder.md` and `PLAY.md` (le-docteur, spec §5) have not been updated, and
   `PLAY.md`'s current text is now actively wrong.** `log.md` shows only spec/build/gate/review
   steps — no docs step has run. `.crew/roles/builder.md`'s "Build requests from the player (💭)"
   section still says nothing about `:open-kinds`, the widening search, or the two-line
   `blueprint-draft` output; worse, `PLAY.md:79-80` still reads "He picks level, open ground; **if
   a spot is too steep or under trees he says so**" — that's now false under this round's own
   change (shade counts; a spot under a tree is placed, not refused). This is the same pattern as
   two prior rounds (demo-talk-v0's PLAY.md, crew-mirror-v0's `crew/README.md`) — a required,
   spec-text-verbatim deliverable that the quest's own "Done when" and spec §5 call for, not yet
   dispatched. **Fix:** dispatch le-docteur with spec §5's verbatim text before closing the round.

No other findings rise above a style nit — the pure logic (`pm.lot`, `pm.camera`, `pm.ask-build`,
`pm.blueprint/arrival-at`) and its wiring into `pm.core` conform to the spec exactly, independently
re-verified against both the diff and a fresh gate run.
