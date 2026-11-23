# Review — live-blueprints-v0 (TASKS T-13b)

Scope: `git status`/`git diff` for the 7 modified files (`bb.edn`, `scripts/crew_bridge.clj`,
`src/pm/blueprint.cljs`, `src/pm/bridge.cljc`, `src/pm/core.cljs`, `src/pm/scene.cljs`,
`test/pm/bridge_real_test.cljs`) plus full reads of the 4 new files (`src/pm/blueprint_file.cljc`,
`scripts/blueprint_add.clj`, `test/pm/blueprint_file_test.cljs`, `test/pm/blueprint_live_test.cljs`),
against `spec.md` and the quest. `f551a10` (the quest+spec commit, code-only no-op) is the
effective baseline; nothing else is stacked under this round.

One scope note: `replies/review-le-critique.txt` + its `.status` file in this run dir are a raw
terminal capture of a **second, parallel dispatch of this same review prompt** (same wording,
same timestamp window) — at the point captured it had only just started reading files and hadn't
written anything. I did not use it as corroboration; this review was produced independently from
the diff/spec/gate.

## Gate — verified independently, not just read

Re-ran `bash .crew/gate.sh` myself: `[:app]` 0 warnings, `[:test]` 0 warnings, `Ran 1154 tests
containing 7326 assertions, 0 failures, 0 errors`, `bb content-test` 5/42 clean — matches
`gate-1.txt` exactly. The gate's own `tail -40` drops the alphabetically-early
`Testing pm.blueprint-file-test` / `pm.blueprint-live-test` headers from the printed transcript
(same in my own run), so I cross-checked structurally instead: `deftest` count is 1093 at
baseline (`f551a10`) vs 1154 now, a delta of 61, exactly `31 (blueprint_file_test) + 29
(blueprint_live_test) + 1 (bridge_real_test's new t04)` — the new suites did run, not just compile.

## Point-by-point

- **One validator, not copied into the bb script**: `pm.blueprint-file/problems`+`valid?` is the
  only rule set. `pm.blueprint/all` now filters with `bf/valid?` (old private `valid-blueprint?`
  deleted). `scripts/blueprint_add.clj` requires `pm.blueprint-file` and calls `bf/add`/`remove-id`
  (which call `problems` internally) — no re-implementation of the rules in the script.
- **Merge is pure, tested, no-op on unchanged `:as-of`**: `merge-file` takes no `js/Date`/atoms/
  three.js; hand-traced steps 1–9 of spec §4 against the code line by line (estate-ids via
  `:from-file?` absence, incoming = valid ∧ not-estate ∧ distinct-by-id in file order, kept/removed
  via the `:real`/`:approved` keep-rule, announce gated by `first?`/`:blueprints-announced`) — all
  match. `(identical? W1 (:world r))` on an unchanged `:as-of` is both implemented (early-return of
  the original `world`) and asserted (`m3`).
- **Seated and drawn without remounting or moving anything else**: `pm.core/apply-blueprint-file!`
  only calls `bp/seat-ids` with `(:added r)` (never `seat-all`) once the kit's ground is up, and
  `pm.scene/sync-blueprint-draw!` mounts/unmounts only the ids `bp/drawable-changes` names,
  via per-group `t/by-name`/`t/remove!`/`mount-item!` — no call to `mount!` after the initial one.
  `ghost-plan`+`built-plan` together cover exactly the `#{:pending :approved :real}` status set
  `drawable-changes` computes `:add` from, so a newly-added id gets the right ghost-vs-built mesh.
  Checked the kit-not-up-yet path too: `start-state-poll!` runs before `kit/load!` in `init!`, so a
  blueprint arriving before the kit exists sits unseated in `!world` until `start-kit!`'s
  `seat-blueprints!` (now sharing `ground-at-fn`) seats everything and the first `mount!` draws it
  already seated — matches spec §7's documented fallback.
- **Author line once per new id, not for load-time ones**: `first? = (nil?
  (:blueprints-as-of world))` makes `:announce []` on the page-load merge; afterwards `announce`
  subtracts `:blueprints-announced`, and that set only ever grows. Traced against `m1/m2/m9/m12`.
- **Bridge reads the file at request time**: `crew_bridge.clj`'s `make-real!` calls `(read-edn
  blueprints-file)` inline inside the per-request handler (same function that also calls
  `(estate)` fresh each time), not at process start.
- **Writes atomic; tests never touch `state/`**: `blueprint_add.clj` writes a `.tmp` then
  `Files/move` with `ATOMIC_MOVE`+`REPLACE_EXISTING`. All three new/changed test files are 100%
  pure data in/out — no `slurp`/`spit`/`state/` reference anywhere in them. `state/` on disk has
  no `blueprints.edn`, confirming the builder never ran the command for real.
- **DEMO skips the new poll**: `fetch-blueprints!` is only ever invoked from inside
  `start-state-poll!`, which `init!` calls only in the non-`DEMO` branch (`(if DEMO (do
  start-sample-says! ...) (do (start-state-poll!) ...))`) — unchanged by this round.
- **No member/pet/person named in code, no `::k` keys, no npm deps**: grepped `src/`/`scripts/`
  for every crew codename — the only hits are pre-existing, untouched files (`crew_log.cljc`,
  `talk.cljs`, `pick.cljs`, `juice.cljs`, `crew_status.clj`); none of this round's new/changed
  files reference one. `grep '::[a-z]'` over the new blueprint files: no hits. `package.json` has
  no diff.

## Findings

None rise above a style nit:

- `src/pm/blueprint.cljs:149` (`merge-file`'s `let`) binds a local named `new` — legal and
  compiles clean (0 warnings), but `new` is a special-form token elsewhere in Clojure, so it's a
  one-word rename (`added`/`new-bps`) worth doing on the next touch rather than today.
- `spec.md`'s own tally ("Total: 63 cases") undercounts by one against its own numbered rows (`P1–
  P7, N1–N4, D1–D11, R1–R5, U1–U4` = 31, `M1–M14, S1–S3, A1–A7, C1–C5` = 29, `B1–B4` = 4; 31+29+4 =
  64) — a pre-existing arithmetic slip in the architect's spec text, not a tester gap: every single
  labelled case is present and passing.

Conformance is clean: boundary table respected exactly (builder's and tester's file lists match
`git status` with nothing left over), purity holds, and every merge/seat/author-line/draw rule in
spec §4 traces to matching code and a passing test.
