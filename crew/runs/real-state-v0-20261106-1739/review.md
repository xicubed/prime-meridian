# Review: real-state-v0

Reviewed against `crew/runs/real-state-v0-20261106-1739/spec.md` and
`.crew/quests/real-state-v0.md`, at `HEAD` `fd2694e` with the round's uncommitted
working-tree diff on top.

## Scope note

`git status` also shows `.crew/crew.json` and `.gitignore` modified. Per `log.md`, both
changed during the `spec` step (17:39:42), before `build` even started (adding the
`le-coureur-omy` runner and gitignoring `/public/state/`) — pre-existing round setup, not
this round's build output, and outside the builder/tester boundary table anyway. Excluded
below.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → `Build completed. (0 warnings)`
  for both `:app` and `:test`, `Ran 84 tests containing 410 assertions. 0 failures, 0
  errors.` Matches `gate-note.md` exactly.
- **Boundary table**: builder touched exactly `src/pm/overlay.cljs` (new), `src/pm/core.cljs`
  (edit), `README.md` (edit, Run section only — the diff is one inserted paragraph after the
  code block, nothing else in the file moved). Tester touched exactly
  `test/pm/overlay_test.cljs` (new). `src/pm/world.cljs`, `plan.cljs`, `scene.cljs`,
  `three.cljs`, `resources/estate.edn` are untouched, as required.
- **Purity**: `pm.overlay` requires only `cljs.reader` and `pm.world`; no `js/fetch`,
  `js/Date`, or atom use anywhere in the namespace (only `js/isFinite`, a pure predicate).
  All side effects (fetch, polling, the atom, `js/Date.now`, `console.info`) live in
  `pm.core`, matching the spec's namespace split.
- **No field/mirror invention**: `accept?` rejects any key not already in the mirror's
  current `:state` (`(not (contains? state k))` → false) before any type check runs, and
  `apply-file` only touches an entry when `(get-in world [:mirrors id])` already exists —
  unknown ids (`:rex` in t07, `:pet` as a copy in t17) are left untouched. Matches spec §2
  step 5 and quest scope item 1.
- **No mirror named in code**: `grep -ri "flora\|juniper\|stock\|bales\|worms"` over
  `src/pm/overlay.cljs` and `src/pm/core.cljs` returns nothing — the overlay and core work
  only from each mirror's own `:state`/`:chores`, as the spec requires (mirror names appear
  only in `test/pm/overlay_test.cljs`, which is in scope for that).
- **Fetch URL absolute**: `(def state-url "/state/mirrors.edn")`, built as
  `(str state-url "?t=" (js/Date.now))` — a leading-slash absolute URL under
  `<base href="/kit/">`, correct per AGENTS.md/quest.
- **Failures silent**: `.catch (fn [_])` does nothing (no console error, no HUD write);
  a non-OK response resolves `nil` from the first `.then`, which fails `parse-file`'s
  `string?` check and is swallowed by `when-let`; `.finally` always clears `:busy?`, so a
  failed fetch doesn't wedge the single-in-flight guard. `console.info` only fires when
  `:state-as-of` actually changed, never on failure.
- **Polling vs. unchanged `:as-of`**: the single-in-flight guard (`:busy?`) prevents
  overlapping fetches, and `apply-file`'s step 3 (`(= a (:state-as-of world))` → return
  `world` unchanged) makes re-applying the same `:as-of` a true no-op — confirmed by t05
  (`(o/apply-file K HF) = K`, an unchanged file doesn't undo an in-game F press) and t06.
- **Hand-traced logic**: `apply-file`'s check order (map? → valid non-negative integer
  `:as-of` → equal-to-last-applied → merge accepted fields → unconditional
  `(assoc :state-as-of a)`), `accept?`'s branch order (`:source` → unknown key → time field
  via `(:done-at chore)` membership → type-matched by current value), `source-tag`, and
  `hud-line`'s name-part splicing all match the spec text verbatim, and the independently
  re-run test suite (including t17's renamed-mirror case and t18's generic `:fern`/`:panel`
  mirrors, which aren't domain-specific) passes.
- **README**: new paragraph covers every bullet the spec lists (private/gitignored path,
  silent fallback to sample state, `mkdir -p public/state` + save-the-example steps, the
  `Flora (Hal) · stock 37` example, 5 s poll tied to `:as-of` change, can-only-update-existing-
  fields) with no emails/phone numbers/private paths.

## Findings

Nothing rises above a style nit. One minor observation, not a defect:

1. `src/pm/core.cljs:34` — the "log only when `:state-as-of` changed" check reads
   `(:state-as-of @!world)` before and after the `swap!`, which is correct today but would
   silently stop logging if a future change made `apply-file` ever mutate `:state-as-of`
   outside the returned world value (it doesn't — low risk, just worth knowing the
   before/after comparison is the only thing gating that log line).

No conformance, purity, boundary, or security issues found.
