# Review: demo-build-ask-v0 (20261125-1750)

## Scope note
`replies/review-le-critique.txt` is again a live capture of this same reviewing session's own
pane (identical banner/prompt pattern to the win-the-day-v0 round), not independent
corroboration — disregarded as evidence.

`log.md` records a manager hand-fix: gates 1-3 FAILed purely because the tester's fixture named
a helper `≈` (U+2248), an invalid JS identifier, which crashed the compiled test bundle before
any test could run (confirmed in `gate-1.txt`/`gate-3.txt`: `SyntaxError: Invalid or unexpected
token` on `pm.ask_build_test.≈`). The builder can't touch tests, so its fix rounds couldn't
help. Hal renamed it to `approx=` in `test/pm/ask_build_test.cljs` — verified on disk: exactly
5 occurrences (1 `def` + 4 uses in `t24`), nothing else in the file changed by the rename. This
is a legitimate repair of a tester-introduced naming bug, not a boundary violation, and gate 4
(re-run independently below) confirms it's the only change needed.

## Conformance summary
- Gate re-run independently (not just read from `gate-note.md`): `bash .crew/gate.sh` →
  **PASS**, 0 warnings, `Ran 1546 tests containing 8415 assertions, 0 failures, 0 errors`;
  babashka 5/42 assertions. 1546 − 1501 (prior round's total) = 45, matching the new
  `ask_build_test.cljs`'s own 45 `deftest`s (`t01`…`t45`, confirmed present, none skipped) and
  the manager's note exactly.
- Boundary table: diff touches exactly `src/pm/ask_build.cljc` (new), `src/pm/three.cljs`
  (only `ground-grid` appended after `down-hits` — nothing else in the file changed),
  `src/pm/core.cljs` (only the §5 wiring: `ab` require, new atoms/`raw-estate` next to
  `today-key`, `demo-ask!`/`demo-make-real!`/`demo-remove!`/`demo-merge!`/`cover-meshes!`/
  `twin-reply!`/`save-demo-file!`, and the hooks in `on-say`, `make-real!`, `remove-click!`,
  `fresh-start!`, `init!`), `resources/estate.edn` (only the `:demo-builds` block, inserted
  verbatim right after the `:blueprints […]` vector's close). `package.json`/`package-
  lock.json` diff empty — no npm deps. `pm.lot.cljc`, `pm.blueprint.cljs`,
  `pm.blueprint_file.cljc`, `pm.world.cljs`, `pm.today.cljs`, `pm.ui.cljs`, `TASKS.md`, `state/`,
  `public/` all untouched, as required.
- DEMO-only gating: `on-say`'s no-bridge branch is `(if DEMO (demo-ask! …) (when-let [ev
  (world/twin-says …)] …))`; `make-real!` is `(if DEMO (demo-make-real! …) (if-not (bridge-on?)
  … …))`; `remove-click!` is `(if DEMO (do (demo-remove! id) …) (if-not (bridge-on?) … …))`;
  `init!`'s demo-file restore+`demo-merge!` sits inside the existing `(if DEMO (do …))` branch.
  With `DEMO` false (dev build), every one of these falls through to the pre-existing
  bridge-gated behaviour unchanged, so the dev build without a bridge still refuses
  `make-real!`/remove exactly as before.
- Purity: `pm.ask_build.cljc` has no atoms, no `js/Date`, no three.js — grepped clean. Reader
  conditionals used only for maths (`floor*`/`ceil*`/`round*`) and EDN parsing (`restore`), as
  the spec allows (the same pattern `pm.lot` uses). `:words`/`:places` are read from the `demo`
  argument (estate data), never hard-coded in the parser.
- Ground never persisted: `ground-grid` (three.cljs) computes two rays per cell live and
  returns a transient map that `ab/plan` consumes once per request; grepped every
  `!demo-file`/`demo-builds-key` read/write site in `core.cljs` — only `:blueprints`/`:real`/
  `:removed` (via `ab/add-build`/`mark-real`/`mark-removed`/`save-text`) ever reach
  `localStorage`. No ground/grid data is written to a file or shipped.
- Placement path: `ab/plan` calls `lot/place` (existing, untouched `pm.lot.cljc`); a successful
  plan's blueprint is folded into `!demo-file` and reaches the world only via `demo-merge!` →
  the existing `apply-blueprint-file!` (also untouched) — no new drawing/seating code.
- `pm.demo-builds.v1`: `demo-make-real!`/`demo-remove!` both write through
  `save-demo-file!`→`demo-builds-key = "pm.demo-builds.v1"`; `fresh-start!` now also
  `removeItem`s that key and resets `!demo-file` to `ab/empty-file`.
- Four looks (bench, lamp post, table, planter) in `estate.edn`'s `:demo-builds` match spec
  §2.1 byte-for-byte; `t22` (independently re-verified against the implementation) confirms
  every part is a box ≥0.08 m in a `#{:brown :tin :gold}` colour, grounded at y 0, with a
  non-empty `lot/feet` footprint (clickable via the same box-footprint mechanism every other
  blueprint uses — `pm.lot`/click handling are reused unmodified).
- Naming: grepped `ask_build.cljc`, the new `three.cljs`/`core.cljs` hunks, and
  `ask_build_test.cljs` for Michael/Hal/Flora/Juniper/Pepper — the only hit is a pre-existing
  comment at `three.cljs:380` (`ghost-fill`, untouched by this diff, predates this round).

## Findings
None rise above a style nit. Hand-traced a sample of the harder worked values (`find-word`'s
tie-break order in `t11`/`t07`, `grid-spec`'s rounding in `t16`, `plan`'s clearance maths in
`t32`) against the delivered `pm.ask-build.cljc` and they match; the independent gate re-run
confirms the full 45-case suite passes for real, not just per the stored gate note.
