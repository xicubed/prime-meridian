# Review — remove-build-v0 (TASKS T-15)

Scope: `git diff` for the 11 modified files (`resources/estate.edn`, `scripts/blueprint_add.clj`,
`scripts/crew_bridge.clj`, `src/pm/blueprint.cljs`, `src/pm/blueprint_file.cljc`,
`src/pm/bridge.cljc`, `src/pm/core.cljs`, `src/pm/crew.cljs`, `src/pm/scene.cljs`,
`src/pm/three.cljs`, `src/pm/ui.cljs`) plus a full read of the one new file
(`test/pm/remove_build_test.cljs`), against `spec.md` and the quest. `bbee7b1` (the quest+spec
commit, code/tests unchanged) is the baseline; nothing else is stacked on it.

## Gate — verified independently

Re-ran `bash .crew/gate.sh`: `[:app]`/`[:test]` 0 warnings, `Ran 1250 tests containing 7491
assertions, 0 failures, 0 errors`, `bb content-test` 5/42 — matches `gate-1.txt` exactly.
Structural cross-check: committed `deftest` count at `HEAD` is 1191, working tree is 1250, a
delta of exactly 59 — the new file's own count (s1=1, r1–r5=5, f1–f4=4, x1–x3=3, o1–o10=10,
c1–c7=7, p1–p5=5, a1–a7=7, br1–br8=8, d1–d6=6, e1–e3=3 = 59), matching the spec's own "Total: 59
cases" exactly.

## Point-by-point

- **One card, three modes, no second card**: `pm.blueprint/card-of`+`card` are the single source
  of card data (`:trial`/`:real`/`:removing`); `pm.core/trial-card!` just renamed its call from
  `trial-card` to `card` and still drives the one existing `ui/set-trial-card!`/`#pm-trial-card`
  element — the `ui.cljs` diff adds only one CSS rule, no second element or id. `open-card`/
  `close-card` enforce "one at a time": opening a real card while another is mid-`:removing`
  preview puts that one back to `:real` first (`o7`), and closing a real card that was fronting a
  standing trial brings the trial's card back (`o6`).
- **`:removing` pure and tested; Keep it restores the item exactly**: `start-removal`/`keep-it`/
  `finish-removal`/`fail-removal`/`open-card`/`close-card` touch no atom/DOM/three.js, `now` is
  always an argument, and each is `identical?`/`=`-tested for its no-op cases (`r2`, `r4`, `x3`,
  `o3`, `o9`) and its happy path (`r3`: `(-> W (start-removal :lamp) (keep-it :lamp))` `=` `W`
  exactly). For the *visual* restore, hand-traced `pm.scene`'s status loop: the pre-existing
  `:real` branch (`unghost!` + `light-glows!` + hide sign) is unconditional on any status change
  into `:real`, so it already covers `:removing`→`:real` with no new case needed — and since
  `ghost! g :removal` (entering `:removing`) snapshots the then-current real colours into
  `userData.ghostWas` before tinting red, `unghost!` on Keep it restores exactly those colours;
  `light-glows!` re-adds the point light `unlight-glows!` had disposed on entry. Confirmed `ghost!`
  /`unghost!`'s edge-line bookkeeping doesn't double up across a second preview cycle, same as
  the T-14 Undo path.
- **The removal ghost is a colour option on `ghost!`, not a copy**: `ghost!` gained a `tint` arg
  (default `:proposed`) that looks up `{:fill :glow :edge}` from the new `ghost-tints` map;
  `mount-item!` threads `(:tint item)` through; `unghost!` is untouched. No second ghost fn.
- **Remove it reuses `post-real!`/`!saving`, can't post twice**: `remove-click!` is the only new
  caller of the existing generic `post-real!` (body-fn + done-callback, unchanged), using
  `crew/remove-build-body` — no second bridge-posting path. It guards at the core level (`(and
  (nil? @!saving) (= :removing (bp/status @!world id)))`) before setting `!saving`, the same
  pattern as `real-click!`; `bp/card`'s `:removing` mode disables both buttons whenever *any*
  id is saving (`c4`), a second, independent UI-level layer on top.
- **The bridge rule pure, refuses non-real ids, `:removed` beats `:real` on load**:
  `remove-real-build` takes `now` as an argument, refuses (nil) unless `id` ∈ `:real` and ∉
  `:removed` (covers unknown, never-real, and already-removed ids — `br3`). `apply-real-builds`'s
  `cond` checks `removed` before `real`, so a hand-edited file with an id in both resolves to
  `:rejected` (`a2`); the 5 s poll re-applying mid-preview still prefers `:removed` over a
  standing `:removing` (`a5`).
- **`bb blueprint-add` refuses a removed id**: `pm.blueprint-file/add`'s new 6-arity checks
  `removed-ids` after the estate-id check and before the already-in-file check, matching the
  spec's stated order exactly (`d4` estate wins, `d5` removed beats already-in-file); the 5-arity
  still calls it with `#{}` (`d3`). `scripts/blueprint_add.clj` reads `state/builds.edn` (or
  `PM_BUILDS_FILE`) and treats a missing/unreadable file as `#{}`, never blocking an add on the
  bridge's own file.
- **Tests never touch `state/`**: `test/pm/remove_build_test.cljs` is pure data in/out — no
  `slurp`/`spit`/file path anywhere in it.
- **Words are data; no member/pet/blueprint named in code; no npm deps**: grepped `src/pm` and
  `scripts` for every new label string (`"Remove…"`, `"Close"`, `"Remove it"`, `"Keep it"`,
  `"Removing…"`, `"will be removed"`) — zero hits outside `resources/estate.edn` and the test
  file. Grepped `src/` and `scripts/` for crew codenames and specific blueprint ids — the only
  hits (`pick.cljs`'s pre-existing `le-testeur` docstring, `crew_status.clj`'s pre-existing
  comment) predate this round's diff. `package.json` has no diff. No `::k` keys in the touched
  blueprint files.
- **Boundary table**: `git status` matches exactly — builder's files (the 9 `src/pm/*` files
  listed, `scripts/crew_bridge.clj`, `scripts/blueprint_add.clj`, `resources/estate.edn`'s
  `:blueprint-text` block only) plus the tester's one new file; nothing in `test/` (besides that
  file), `public/`, `docs/`, `TASKS.md`, or `state/` touched.

## Findings

None. Every new pure fn in spec §3 traces to matching code and a passing, hand-checked test
(including the trickier ones: `card-of`'s stale-`:card-open` fallback to the trial, `open-card`'s
front-a-trial/evict-a-preview logic, `apply-real-builds`'s three-way `removed`/`removing`/`real`
precedence); the hand-tested wiring in §4 reuses every existing mechanism the quest named
(`!saving`, `post-real!`, `ghost!`, the `:real` status-sync branch) rather than adding parallel
ones, and the one genuinely new wiring piece — `:rejected`'s added `unlight-glows!` call — is a
real, necessary fix for the direct `:real`→`:rejected` jump a remote `state/builds.edn` edit can
cause without this browser ever seeing `:removing` locally.
