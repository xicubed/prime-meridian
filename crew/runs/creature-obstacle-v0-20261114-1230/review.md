# Review: creature-obstacle-v0

Scope: `git status`/`git diff` show exactly the round's own working tree (5 modified source
files + 1 new test file + this run dir; start commit `e89f92e` matches current `HEAD`, nothing
stacked from an earlier uncommitted round). No `output/` snapshot was needed.

## Verified independently

- Re-ran `bash .crew/gate.sh` myself (not just read `gate-note.md`): 0 warnings in both builds,
  `Ran 811 tests containing 6343 assertions. 0 failures, 0 errors.` — matches `gate-1.txt`
  exactly. `pm.creature-obstacle-test` contributes 29 `deftest` forms (case01–case29), matching
  the spec's "29 cases" and the prior commit's "782 tests" baseline (782 + 29 = 811).
- Boundary table: only `src/pm/{motion,creature_motion,creature_spike,bricks,scene}.cljs` and
  `test/pm/creature_obstacle_test.cljs` changed; `resources/estate.edn`, `src/pm/core.cljs`,
  `src/pm/creature.cljc`, `src/pm/avoid.cljs` and every existing test are untouched. `scene.cljs`
  diff is the single palette key, nothing else.
- With no extras: `motion.cljs`'s `perceive-all`/`plan-all`/`replan`/`advance` all gained a
  trailing optional-arity that defaults to `nil`/`[]`, and `extra-others` on `nil`/`[]` returns
  `[]`, so the walker-only code path is byte-for-byte what it was (case21 asserts `=` across 400
  frames both ways; `sees?`/`hears?` reduce to the old centre-distance logic when `sd = d`).
- The creature goes through the same senses: `extra-others` turns an extra into an "other" with
  `:obstacle true`, appended after the other walkers inside both `perceive-all` and `plan-all`'s
  `others` — it flows through the unmodified `perceive`/`noticed` exactly like another walker.
- Never planned or pushed: extras are concatenated into each walker's `others`, never into `ws`
  itself, so `plan-all`'s `map-indexed` over `ws` never calls `plan` on the obstacle. There is no
  `push` function anywhere in `motion.cljs`/`avoid.cljs`/`bricks.cljs` at all (grep empty) — no
  contact mechanism exists yet for anything to accidentally hook the obstacle into.
- `ground-circle` covers every capsule by construction (`:r` = max over every transformed
  endpoint of `|E − at| + r_k`, where `:at` is the endpoint bounding box's centre) and the
  tester's case10 independently re-derives each endpoint from the raw rows and asserts the
  inequality, for both the lying and curled horse.
- No species names in any touched `src/` file (`grep -niE 'horse|dog|foal|pony'` empty across
  all five); the test fixtures legitimately use `:horse`/`:dog` per the spec's own test data.
- The lap tests (`case25`–`case28`) build `ws`/`S` from `(w/load-estate w/estate-edn)`'s real
  `m/walkers`/`a/statics`, i.e. the actual estate routes, not synthetic fixtures; `case29`
  confirms `:creature-spike` in that estate is untouched (`{:flag "pony" :kind :horse ...}`).
- `pm.creature-spike` has no require on `pm.bricks`, and `pm.ui` requires only `pm.talk` —
  confirmed by reading both files; no cycle introduced.

## Findings

None rise above a style nit. The round matches the spec's function signatures, formulas, and
boundary table exactly, and the gate's numbers are independently reproducible.
