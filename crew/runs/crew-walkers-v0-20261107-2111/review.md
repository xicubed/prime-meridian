# Review: crew-walkers-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 533 tests / 2971 assertions (up from
513/2483 at the end of `crew-talk-v0`, now committed as `1f56ac0`: +20 tests, exactly
`test/pm/crew_walkers_test.cljs`'s `t01`–`t20`, plus the 7 updated existing assertions §5
authorizes). `git diff` here is clean — no stacked-round reconstruction needed.

**Scope note.** `.crew/crew.json` (re-enabling Le Docteur, with a new role note) and `.gitignore`
(ignoring `/public/models/`, the MotionBricks weights) both show as changed, but neither is in
this round's "Files and owners" table. Both mtimes (21:13, 21:17) fall before the `build` step
even started (21:20:28) — `log.md` shows these landed during the architect's `spec` step, i.e.
manager/architect-level roster administration, not the builder's work. Excluded from this review.
`src/pm/three.cljs`, `src/pm/core.cljs`, `bb.edn`, `public/vendor/` and `resources/content/` are
all untouched by this round, consistent with the boundary table listing them as off-limits.

## Conformance checks

- **Walkers, tints, routes and speeds are data; no walker or agent name in code:**
  `resources/estate.edn`'s diff matches spec §2 verbatim — six walker entries, each tint/speed/
  loop/face as literal data, no route point changed from what the quest asked for (see "route
  changes" below). `grep -rn "un-automate|le-testeur|un-architecte|le-coureur|le-critique|le-docteur"`
  across `motion.cljs`, `crew.cljs`, `tint.cljs` and `bricks.cljs` is empty — `pm.motion/walker`
  reads `:tint`/`:loop`/`:face` off the entry map exactly as it already does for `:route`/`:speed`,
  and `pm.bricks` only ever reaches a walker's tint through `(:tint w)`.
- **Materials are cloned per walker, no shared tint:** `pm.tint/tint-object!` always starts a
  fresh `clones` atom scoped to a single call, so two walkers sharing the same source material
  (e.g. the mannequin's `BODY` material) each get their own independent clone — hand-verified
  against the full `t14` scenario by tracing both calls by hand: the first call's clone of `BODY`
  (`0x4775c2`, Un Architecte's blue) and the second call's clone of the same still-pristine `BODY`
  (`0xde7c36`, Le Coureur's orange) land on two different objects, and `BODY`'s own colour
  (`0xf4f2ee`) is unchanged throughout — `.setHex` is only ever called on a clone's own colour
  object, never on the source's. Within one call, the same source material mapping to several
  meshes (`MA`/`MB`) correctly collapses to **one** clone (memoized by the plain JS-object-as-map-
  key, which CLJS resolves by reference identity), matching "no shared tint" at the per-walker
  granularity while still deduplicating clones within a single walker's own mesh tree. I
  independently recomputed the tinted hex values by hand (channel-by-channel `round(c·t/255)`,
  e.g. `0xf4f2ee` × `0x4a7bd0` → `0x4775c2`) rather than trusting the test's own numbers.
- **The generation stagger holds and can't starve a walker forever:** `gate-start` only starts the
  queue head when `:in-flight` is nil *and* `:last-start` ≠ the current frame — hand-traced the
  full `t17` sequence (enqueue 0, enqueue 1 → start 0 at frame 10 → a second start at frame 10
  correctly returns `nil` even though the queue still has an entry → `gate-done 0` frees it → start
  at frame 11 correctly starts 1) and it matches the spec's worked values exactly. No-starvation:
  the queue is a plain FIFO (`gate-enqueue` always `conj`es onto `:queue`, `gate-start` always pops
  `(first (:queue gate))`), so a walker's job can only be passed over by jobs that enqueued earlier
  than it, each of which is popped in turn — no path re-enqueues or reorders an existing job ahead
  of an older one, so every job's wait is bounded by the queue length in front of it, not
  unbounded. `t18`'s `run-gate` harness (both the zero-latency 6-at-once case and the 3-frame-
  latency case) confirms distinct start frames and FIFO order under simulated contention.
- **Kinematic mode handles six:** nothing in `pm.motion`'s touched functions (`walker`, `pick`,
  `step`, `follow`, `plan-all`, `advance`) assumes a fixed walker count — `tick-kinematic!` itself
  is untouched by this diff, inheriting six-walker support automatically from `m/advance`'s
  existing generality. `t20` runs the full 2400-frame, six-walker estate scenario and checks
  pairwise spacing across **all** `C(6,2)=15` pairs every frame (≥0.6 m, model 0.784), every
  walker's static gap (≥0, model 0.155), per-walker pick sequences (Le Coureur's new `:loop`
  sequence `[1 2 3 0 …]` vs. the west-bank pair's unchanged ping-pong), and final leg/bounds
  checks — this is a real stress test of the six-walker path, not just unit coverage of the
  pieces.
- **The title fallback:** `plate-name` is `(get-in crew [:titles (:agent w)])` when it's a
  non-blank string, else `(:name w)` — matches `t15`/`t16` including the blank-string-falls-back-
  to-name case (`{:titles {:x ""}}` → `"X"`) and the nil-crew case. `pm.bricks/set-dots!` now reads
  the name run through `plate-name`, while `build-view!`'s one-time initial label still uses
  `(:name w)` — matching the spec's explicit note that only `set-dots!` changes.
- **Route changes the builder made are explained:** there are none to explain — `resources/estate.edn`'s
  diff matches spec §2 byte-for-byte, and the spec itself (§0, §4 tests 9–11) states the routes
  needed **no** nudging, with the architect's own hand-measured minima (static gap 0.2 m, creek
  distance 3.0 m, rock distance 1.0 m) backing that up. `t09`–`t11` re-verify every route point and
  segment against the real static/creek data and a probe stub, independently confirming the
  architect's claim rather than just trusting it; `build-un-automate-monde.txt`'s reply (mostly a
  captured shell-permission prompt, not prose) shows no additional route edit beyond what the spec
  specified.
- **Pure logic tested:** `pm.tint.cljs` has no requires beyond `pm.motion` (confirmed in its
  `ns` form) and touches three.js objects only through the five methods the spec lists — genuinely
  Node-testable, which the stub-based `t14` exercises directly. `pm.motion`'s new surfaces
  (`tint-hex`, `figure-hexes`, the four gate functions, `next-index`'s 4-arity, the `:face` logic in
  `step`/`follow`) are all free of atoms/DOM/three.js and have direct test coverage; `pm.bricks`'s
  interop additions (`install-gate!`, `run-gate!`, the tint call sites) are explicitly out of unit
  test scope per spec §3.4 ("not unit-tested, Hal checks live"), which the tester correctly
  respected (no bricks.cljs-shaped assertions appear in `crew_walkers_test.cljs`).

## Boundary-table compliance

Builder touched exactly: `resources/estate.edn` (`:walkers` only — `:motion` unchanged, confirmed),
`src/pm/motion.cljs`, `src/pm/crew.cljs` (`plate-name` only), `src/pm/tint.cljs` (new),
`src/pm/bricks.cljs`. Tester touched exactly `test/pm/crew_walkers_test.cljs` (new) plus the seven
authorized existing assertions (`motion_test` t03; `characters_test` t10, t15; `crew_test` t26,
t27; `senses_test` t05) — each diff hunk touches only that one `deftest`, with values matching
§5's table verbatim, and no other test in any of those four files was touched.

## Findings

None. Nothing to flag.

**Verdict: pass.**
