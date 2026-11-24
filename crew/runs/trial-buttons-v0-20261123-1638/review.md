# Review — trial-buttons-v0 (TASKS T-14)

Scope: `git diff` for the 7 modified files (`resources/estate.edn`, `src/pm/blueprint.cljs`,
`src/pm/core.cljs`, `src/pm/pick.cljs`, `src/pm/scene.cljs`, `src/pm/three.cljs`, `src/pm/ui.cljs`)
plus a full read of the one new file (`test/pm/trial_card_test.cljs`), against `spec.md` and the
quest. `c06b0f2` (the quest+spec commit, code/tests unchanged) is the baseline; nothing else is
stacked on it.

## Gate — verified independently

Re-ran `bash .crew/gate.sh`: `[:app]`/`[:test]` 0 warnings, `Ran 1191 tests containing 7385
assertions, 0 failures, 0 errors`, `bb content-test` 5/42 clean — matches `gate-1.txt` exactly.
Cross-checked structurally too: committed `deftest` count at `HEAD` is 1154, working tree is
1191, a delta of 37 — exactly `test/pm/trial_card_test.cljs`'s own count (u1–u8=8, t1–t3=3,
o1–o9=9, c1–c6=6, p1–p8=8, e1–e3=3 = 37), matching the spec's own "Total: 37 cases" tally with no
discrepancy this time.

## Point-by-point

- **Make it real reuses `make-real!`, no second bridge path, can't post twice**: `on-blueprint-
  click!`'s `:approved` branch and the card's button both now call the one new `real-click!`,
  which is the only caller of `make-real!` in the diff — `post-real!`/`crew/real-build-body`
  aren't called from anywhere else new. `real-click!` guards at the core level (`(and (nil?
  @!saving) (= :approved (bp/status @!world id)))`) before setting `!saving` and calling
  `make-real!`, so the double-post guard is structural, not just a disabled DOM attribute — the
  UI-level `:disabled?` (both buttons, whenever *any* id is saving, confirmed by test `c4`) is a
  second, independent layer on top of it. `make-real!`'s `done` thunk fires exactly once on every
  branch (no-bridge `say!`; bridge ok; bridge not-ok) and is what clears `!saving`.
- **Undo is the pure `unapprove`, not a reject, and the ghost redraws**: `unapprove` only flips
  `:approved`→`:pending` via the existing `set-status`, `identical?` on pending/real/rejected/
  unknown ids (`u2`–`u5`); never touches `:trial-order`. `pm.scene`'s new `:pending` branch in the
  status-sync loop only fires when the *previous* status was `:approved`, calling `unlight-glows!`
  then `ghost!` on the already-mounted group (no remount) — traced `ghost!`/`unghost!`'s own
  bookkeeping (`userData.ghostWas`/`ghostEdges`) by hand: `unghost!` removes the edge lines and
  restores colours in place, so a later `ghost!` re-clones cleanly with no stacked duplicate edges
  and no lost original colour. A freshly-drawn `:pending` with no prior `:approved` still no-ops,
  as before.
- **Placement fn pure, tested, hides off-screen/behind camera**: `pm.pick/screen-of` touches no
  DOM/`js/Date`; hand-traced its NDC→px math and the `visible?` rule (`|x|>1`, `|y|>1`, or `z>1`)
  against `P1`–`P8`, including the inclusive-edge case (`P3`) and both malformed-input shapes
  (`P7` bad point, `P8` bad size) — all match.
- **One card at a time, gone after real/undo/reject/reload**: `card-owner` only ever returns an
  id still `:approved`; real/undo/reject all move it out of that status, so the next frame's
  `trial-card` call returns a different owner or `nil` for that id (`o4`–`o6`). `:trial-order` is
  a `:blueprints-text`-sibling world key never written through `on-world-change!`'s cache path
  (that still only fires on `:mirrors`/`:state-as-of` changes, unmodified by this round) or
  `localStorage` — confirmed by reading `overlay/cache-of`'s call site; a reload starts fresh.
- **Clicks on the card never reach the canvas**: `pm.ui`'s card element gets `stopPropagation` on
  exactly the spec's list (`pointerdown pointerup mousedown mouseup click dblclick contextmenu
  wheel`), which blocks the window-level `pointerup` pick listener from ever seeing a card click
  (the canvas itself is never hit-tested since the card sits visually on top); each button repeats
  `stopPropagation` on its own `click` as a second layer.
- **Buttons use `.pm-btn`; labels are data in `:blueprint-text`**: button `className` is `"pm-btn
  trial-" + action`; grepped `src/pm` for the literal strings `"Make it real"`/`"Undo"`/`"Saving"`
  — zero hits outside `resources/estate.edn` and the test file, so every label flows from
  `:make-real`/`:undo`/`:saving` through `trial-card`, never hard-coded.
- **No member/pet/blueprint named in code, no npm deps**: grepped `src/` for crew codenames and
  for specific blueprint ids (`bench-by-mailbox`, `bench-by-creek`, …) — the only hit
  (`pick.cljs`'s pre-existing `le-testeur` docstring example) predates this round's diff (outside
  the added hunk). `package.json` has no diff.
- **Boundary table**: `git status` matches the table exactly — builder's files
  (`blueprint.cljs`, `pick.cljs`, `three.cljs`, `scene.cljs`, `ui.cljs`, `core.cljs`,
  `resources/estate.edn`) plus the tester's one new file (`trial_card_test.cljs`); nothing in
  `test/` (besides that one file), `public/index.html`, `docs/`, `TASKS.md`, or `state/` touched.
  `resources/estate.edn`'s diff is confined to the `:blueprint-text` map, nothing else in that
  file moved.

## Findings

None. Every spec rule in §3 (`approve-blueprint`'s `:trial-order` splice, `unapprove`,
`card-owner`'s `:trial-order`-then-fallback order, `trial-card`'s word-presence and saving-label
logic, `screen-of`'s visibility rule) traces cleanly to matching code and a passing, hand-checked
test; the impure wiring in §4 (`top-center`/`project-ndc`/`unlight-glows!`, the scene's new
`:pending` branch, `pm.ui`'s card element, `pm.core`'s `!saving`/`real-click!`/`undo-trial!`/
`trial-card!`) matches its hand-tested description and introduces no second path to the bridge,
no duplicate-edge/lost-colour bug on repeated ghost/unghost cycles, and no leak of card clicks to
the canvas.
