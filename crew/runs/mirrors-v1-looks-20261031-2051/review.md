# Review: mirrors-v1-looks (2026-10-31 21:01 round)

## Conformance summary

Verified independently (re-ran `.crew/gate.sh` rather than trusting `gate-note.md`):
`Build completed (0 warnings)` for `:app` and `:test`; `Ran 66 tests containing 305 assertions.
0 failures, 0 errors.` — matches the gate note exactly. 66 = the 32 pre-existing `world_test.cljs`
tests (file untouched) + 34 in `plan_test.cljs` (16 kept byte-identical — t01, t02, t05–t14, t16,
t19, t20, t21 — 6 rewritten with the spec's new expected values — t03, t04, t15, t17, t18, t22 —
and 12 new, t23–t34), matching spec §7's "34 cases in all" exactly.

Boundary table (spec §0): builder's diff touches exactly `resources/estate.edn` (only the
`:placed` vectors and the one display-scale comment — rest of the file, including `:sounds` and
the chores, byte-identical), `src/pm/plan.cljs`, `src/pm/three.cljs` (diff confirmed limited to
`group` and `stand-in`, no other function touched), and `src/pm/scene.cljs`. `src/pm/core.cljs`
and `src/pm/world.cljs` are untouched (`git diff --stat` empty for both), as the spec requires —
the HUD's new `parts N` picks up through the existing `scene/summary` call, no core.cljs edit
needed. Tester's diff touches only `test/pm/plan_test.cljs`; `test/pm/world_test.cljs` untouched.

Required checks, all pass:
- **npm three leakage**: grepped `src/` for direct `THREE.`/`js/THREE` construction outside
  `pm.three`'s `(three)` indirection — none; every new geometry (`SphereGeometry`, `ConeGeometry`)
  and `group`'s new `:rot` is looked up via `(.-X (three))` same as v0. `pm.scene` still only
  requires `pm.three`/`pm.plan`, never touches `js/THREE`, the npm module, or `window.hiroshi`.
- **No per-frame mesh rebuild**: `mount-item!`/`t/stand-in`/`t/group` are called only from
  `mount!`, itself guarded by `(when-not @!mounted ...)`; `update!` (called every frame from
  `core.cljs`, unchanged) only calls `t/set-label-text!` on existing sprites for changed label
  keys — confirmed by reading the full `scene.cljs`, not just the diff.
- **No mirror-specific names in code**: grepped `src/` for `:flora`/`:juniper`/axolotl/horse/
  worm/jar/gill/mane — the only hits are the two palette keys `:gill`/`:mane` in `scene.cljs`,
  which spec §0 explicitly allows ("Palette keys are colours named for what they colour in
  general... any creature may use them").
- **parts/scale/yaw as data only**: `pm.plan`'s `look-parts`/`parts-top`/`scene-plan` take
  `:scale`/`:yaw`/`:look` straight from the item map and never branch on a mirror id or name;
  `pm.scene`'s `mount-item!` reads `:parts`/`:yaw` generically off the plan item. Confirmed with
  `t34-same-code-both-looks`, which swaps Juniper's look onto Flora's item and gets Juniper's
  parts back scaled by Flora's `:scale` — the same code path serving two different looks, as the
  quest requires.

Hand-traced a sample of spec §7 against the implementation (not just "tests pass"): `look-parts`'
single-shape back-compat (`t24`, confirmed `h/2+h/2=h` exactly as the spec promises bit-for-bit),
strict part validation and fallback-to-default-part (`t26`, all 7 deliberately-malformed parts in
the spec's `toy-edn` dropped/handled exactly as listed), scale applied to size and offset but not
`:rot` (`t27`), and the real Flora/Juniper geometry (`t31`–`t33`, hand-checked gill heights above
the 1.55 m waterline and the axolotl's ~1.02 m span) all match the spec's hand-checked values.

## Findings

No findings against the diff: naming, the three.js indirection, the build-once/update-only-labels
rule, and parts/scale/yaw as pure data all match spec and quest. One purely cosmetic observation,
not worth ranking as a real finding: `test/pm/plan_test.cljs:45` defines `part-of`, one of the
shared fixtures the spec's §7 code block lists verbatim, but no test case actually calls it — it's
mandated by the spec as written, not a tester slip, and the gate's 0-warning compile confirms
shadow-cljs doesn't flag it either.
