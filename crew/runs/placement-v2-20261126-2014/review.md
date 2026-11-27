# Review: placement-v2 (crew/runs/placement-v2-20261126-2014)

## Scope
Start commit `e8a061b` = current `HEAD`, so `git diff`/`git status` is this round's own clean
diff. Files touched: `resources/estate.edn`, `scripts/blueprint_draft.clj`, `src/pm/ask_build.cljc`,
`src/pm/blueprint.cljs`, `src/pm/camera.cljs`, `src/pm/core.cljs`, `src/pm/lot.cljc`,
`src/pm/view.cljc`, the three existing test files listed in spec §10 (exactly the lines §10
names, nothing else), plus the new `test/pm/placement_v2_test.cljs`.

## Verified independently
- Re-ran `bash .crew/gate.sh` myself: **PASS — 0 warnings; 1737 tests / 8964 assertions**, matching
  `builder-notes.md`'s claim.
- `test/pm/placement_v2_test.cljs` has `t01`–`t49`, one `deftest` per spec-numbered case (49
  total), transcribed faithfully against spec §9's worked values — spot-checked every group (face
  yaw, `at-here`/`at-xz`, `parse-args`/`at-line`/`blueprint`, the demo's here path, no-too-steep,
  no-flight, named places unchanged).
- **`--at here` is exact, with only stale/missing/sky refusals.** `view/at-here` hand-traced
  against spec §3: order is (1) `raw` not a map or `:as-of` not a number → `lot/no-view`, (2) older
  than `here-ms` (30000) → `here-stale` (exactly 30000ms old still passes — confirmed by t09's
  boundary case), (3) `:center` fails `vec3?` (covers `nil` = sky) → `here-sky`, (4) else `:ok true
  :at (vec center)` **unchanged from `:center`**, no ground check, no search, no gap/route rule.
  Order is stale-before-sky (t13). `--at x,z` (`at-xz`) is unconditionally `:ok true`: no refusal
  path exists at all, matching "always `:ok true` (no checks)."
- **The yaw faces the camera.** `lot/face-yaw` hand-traced: `atan2(dx, dz)` in degrees, snapped to
  the nearest 45° via `round*`, height (`y`) ignored, returns 0 when either argument isn't 3
  numbers or the camera is directly overhead (`dx²+dz² < eps`). Verified against all 8 compass
  points, the two snapping cases, and the two degenerate cases in spec §9 A — all match. `turn [0
  1] yaw` convention (a look's front is local +z) is honored consistently in `at-here`/`at-xz`/
  `ask-build/plan-here` alike.
- **Too-steep no longer blocks anything, but `seat` still stretches legs.** `bp/seat` keeps its
  stretch-to-ground maths and `:seat-drop` (for information), but no longer sets `:too-steep?` at
  all (confirmed by diff: the key is gone from the `assoc`, not just always-false). `max-seat-drop-m`
  and `refuse-steep` are deleted; `sign-text` drops the `:steep` branch; `apply-approved!`'s
  `(and blueprint (:too-steep? blueprint))` cond clause is removed outright, so a seated ghost on a
  steep slope reaches `breaks-build?`/`:else` same as any other and Make it real proceeds. The
  estate's `:steep` line is deleted from `:blueprint-text`. Grepped `src/`, `scripts/`, `test/` for
  `:too-steep?`/`refuse-steep`/`max-seat-drop-m` outside the touched files — no other reader exists
  (`merge-file`'s pre-existing key-stripping, confirmed unchanged by `m11-file-keys-stripped`
  staying untouched, already drops a stale `:too-steep?` from a file as before).
- **Named places are unchanged.** `lot/place`, `spot`, `refusal`, and the v1 widening pass are
  untouched by this round's diff; `lot/spot`'s "too steep" ground-kind check still exists as the
  named-place search rule (quest Decided 4), confirmed by t49 (a named "well" request still
  refuses on bad ground) and by the explicit "checked and unchanged" list in spec §10 matching the
  actual diff (no hunk touches `place`/`spot`/`ask-build`'s `ask`/`plan`/`widen?`).
- **The demo's here path matches.** `ab/here?`/`ab/plan-here` skip the grid and `lot/place`
  entirely — `plan-here` places directly at `ctx`'s `:here` with `face-yaw` against `ctx`'s
  `:camera`, no measuring. `core.cljs`'s `demo-ask!` branches on `(ab/here? r)` before the
  grid-measuring block, so a "here" ask in the demo never logs a ground-grid line (confirmed by
  reading the wiring directly, matching the quest's "no grid in the console" done-when item) and
  the fly logic also gets `:center` (via `t/center-hit`) in `show-arrival!`, so a here ghost lands
  at `:at-center` and never flies, while a named-place ghost keeps v1's flight rules — confirmed by
  `fly-decision`'s new `:center` clause sitting correctly between `:player-moving` and `:in-view`.
- No npm deps (`package.json`/`package-lock.json` diff empty). No person/pet/member name appears
  in this round's own diff hunks (the only two "Michael"/`:hal` grep hits are a pre-existing,
  untouched comment line and a line being *deleted*, not added, as part of removing `refuse-steep`).

## Findings

1. **`.crew/roles/builder.md` and `PLAY.md` (le-docteur, spec §7) have not been updated — and both
   now describe behavior this round explicitly removes.** `log.md` shows only spec/build/gate/
   review steps; no docs step has run. `.crew/roles/builder.md:36-42` still tells the builder to
   use `--near <spot>` with `here` as one of `--near`'s values ("else `here`... when he says
   'here'"), with the v1 widening/moving/flying language — but this round's whole point is that
   "here" is a **different, exact** mode (`--at here`, no search, no moving, no flight) while
   `--near` is reserved for named places only. Using the stale card, a builder would literally run
   the wrong command for a "here" request. `PLAY.md:77-81` is similarly stale: "if the exact spot
   is too steep... he puts it at the nearest spot that fits... and the camera flies over to show
   you" is now false for a "here" request (no steep refusal exists anywhere any more, and a here
   ghost never flies). This is the same gap flagged in each of the three prior rounds
   (demo-talk-v0, crew-mirror-v0, placement-v1) — a required, spec-text-verbatim deliverable not
   yet dispatched. **Fix:** dispatch le-docteur with spec §7's verbatim text before closing the
   round; this one is higher-priority than the prior rounds' equivalents since the current text
   actively contradicts the new behavior rather than merely omitting it.

No other findings rise above a style nit — the pure logic (`pm.lot/face-yaw`, `pm.view/at-here`/
`at-xz`, `pm.ask-build/here?`/`plan-here`, `pm.camera/fly-decision`'s `:center` rule, and the
`bp/seat`/`apply-approved!` steep-gate removal) and its wiring conform to the spec exactly,
independently re-verified against both the diff and a fresh gate run.
