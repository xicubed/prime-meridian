# Review: crew-dots-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 468 tests / 2120 assertions (up from
440/2043 at the end of `senses-v0`: +28 tests — exactly `test/pm/crew_test.cljs`'s `t01`–`t28`,
one per spec case).

**Isolation method.** `resources/estate.edn`, `src/pm/motion.cljs`, `src/pm/bricks.cljs` and
`src/pm/three.cljs` are also `senses-v0`'s files, and `senses-v0` is still uncommitted (stacked on
top of the now-committed `avoid-v0`/`characters-v0`/`foot-ik-v0`), so a plain `git diff` mixes
both rounds' hunks. I reconstructed each file's `senses-v0`-end state (base commit `fbfbdea` +
`senses-v0`'s own diff, already verified against its spec in the prior review) and diffed that
against the current file to isolate only `crew-dots-v0`'s contribution. `src/pm/core.cljs` is new
territory for this round (untouched by any earlier uncommitted round) and `src/pm/scene.cljs`'s
diff is entirely `senses-v0`'s (its mtime, 16:08, predates this round's `spec` step, 16:21:28) —
confirms `scene.cljs` wasn't touched here, consistent with the boundary table not listing it.
`bb.edn`/`scripts/crew_status.clj`/`TASKS.md` also show as changed, but all three predate this
round's start (mtimes in the 16:08–16:12 window, before `spec` at 16:21:28) — the same `senses-v0`
concurrent-work artifacts flagged in the prior review; excluded here too.

## Conformance checks

- **The poll reuses the existing pattern, keeps the last good file:** `fetch-crew!` is structurally
  identical to `fetch-state!` — the same busy guard, `?t=` cache buster, `{:cache "no-store"}`,
  `.ok` check, `.catch` swallow, `.finally` clearing `:busy?`. On text it only swaps `:crew` into
  `!world` `(when-let [c (crew/parse text)] (when (not= c (:crew @!world)) ...))` — a 404 (`.ok`
  false → `nil` text), a missing file, or garbage all make `crew/parse` return `nil`, so the
  `when-let` never fires and the last good `:crew` value is untouched; it only goes stale later
  through `fresh?`'s own `:as-of` check. `start-crew-poll!` mirrors `start-state-poll!` (fetch now,
  then a 2 s `setInterval`), called right after it in `init!`.
- **Staleness turns dots hollow:** `dot` returns the hollow glyph whenever `(not (fresh? crew now))`
  *or* the agent is missing from `:agents` — a single `or`, so a stale file hollows every dot
  regardless of what's in it. `fresh?` uses `<=` against `stale-ms` (15000), matching the spec's
  "exactly 15000 is fresh, 15001 is stale" edge, confirmed by `t08`/`t15`.
- **Colours come from data:** `colors` starts from `default-colors` and only accepts a
  `:status-dots` override value that is an integer in `[0, 0xffffff]` (`t11` confirms `16777216`
  is rejected and `-1`/`"red"` fall back to the default); `resources/estate.edn`'s new
  `:status-dots` map matches §2.1 verbatim, and `set-dots!`/`walker-path-lines!`-style wiring reads
  the palette only through `(crew/colors world)`, never a literal hex in `pm.bricks`.
- **No walker or agent names in code:** `grep -rni "un-automate|le-testeur|un-architecte|le-coureur|polybot|astronaut|juniper|flora"`
  across every file this round touched (`crew.cljs`, `core.cljs`, `bricks.cljs`, `motion.cljs`,
  `three.cljs`) is empty. `pm.crew` reads agent ids only as the `:agent` key off whatever walker is
  passed in.
- **Nothing beyond names and states surfaces from the status file:** `parse` keeps only `:as-of`
  and `{agent-id {:status}}` — every other key at any level (`:host`, `:title`, `:screen`, a
  non-keyword agent id, a non-map entry) is dropped before the map ever reaches `!world` (`t06`).
  Nothing downstream (`dot`, `label-runs`, `hud-text`, `fetch-crew!`) ever logs or displays the raw
  file text or any field besides the walker's own name (from `estate.edn`, not the file) and the
  parsed status/dot.
- **Pure logic tested:** `pm.crew` has no `js/Date`, fetch or atoms (`now` is always an argument);
  every function in §3 has direct test coverage, and `pulse-alpha`'s hand-worked table (1, 0.9,
  0.7, 0.4, 0.7, 1, 0.9, 0.4 at t = 0..3000) matches both the spec's arithmetic and `t18` exactly
  — independently recomputed here, not just "the test the same author wrote passes."

## Boundary-table compliance

Builder touched exactly: `src/pm/crew.cljs` (new), `src/pm/motion.cljs` (`:agent` only — isolated
diff shows just the two new lines in `walker`, nothing else), `src/pm/three.cljs`
(`set-label-runs!` only, additive), `src/pm/bricks.cljs` (`:colors` state, `set-dots!`, the new
`require`), `src/pm/core.cljs` (the crew-poll block, `frame`, `hud-mirrors-suffix`, `init!`),
`resources/estate.edn` (`:status-dots` plus `:agent` on both walkers, exactly as specced — the
only extra is a one-word comment tweak, "Le Testeur: NVIDIA's mannequin...", harmless). Tester
touched only `test/pm/crew_test.cljs`. `src/pm/scene.cljs`, `public/index.html`, `public/vendor/`,
`resources/content/`, `scripts/crew_status.clj` and `public/state/` are all untouched by this
round.

## Findings

None rise above a style nit. Nothing to flag.

**Verdict: pass.**
