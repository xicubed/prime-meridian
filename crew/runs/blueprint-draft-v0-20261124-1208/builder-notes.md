# Builder notes — blueprint-draft-v0 (T-13, the last piece)

## What was built

- `src/pm/lot.cljc` (new, ns `pm.lot`): pure placement. `feet`, `look-radius`, `turn`,
  `feet-at`, `cell`, `segments`, `seg-dist`, `things`, `landmarks`, `landmark-lines`, `spot`,
  `refusal`, `fmt`, `placed-line`, `blueprint`, `place`, `parse-args`, per the spec's exact
  shapes, check order, rounding and refusal strings. All `Math/...` calls go through private
  wrappers with `#?(:clj ... :cljs js/Math...)` (the convention already in `pm.creature.cljc`)
  so the file compiles clean for both bb and the cljs tests.
- `scripts/blueprint_draft.clj` (new): all I/O, no decisions. Reads `PM_GROUND_FILE` or
  `state/ground.edn` (nil if missing/unreadable), `resources/estate.edn`, `:build-rules` from
  it (stderr + exit 1 if absent), and the blueprints file (`PM_BLUEPRINTS_FILE` or
  `state/blueprints.edn`, via `pm.blueprint-file/normalize`). `--landmarks` prints
  `landmark-lines`. A draft: reads the EDN, calls `pm.lot/place`, refused → stderr + exit 1;
  placed → builds the blueprint, checks `bf/problems`, then either prints `placed-line`
  (`--dry-run`, nothing written) or writes it to a temp file and runs the existing
  `bb scripts/blueprint_add.clj <member> <tmp>` (`{:continue true}`, stdio inherited), deleting
  the temp file after — one writer, one validator.
- `bb.edn`: added the `blueprint-draft` task (same shape/exit propagation as `blueprint-add`).
- `resources/estate.edn`: added the top-level `:build-rules` with its comment, exactly as the
  spec gives it (`{:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :gap-m 1.2 :route-m 0.8
  :max-spread-m 0.12}`). Nothing else in that file touched.

Gate: `.crew/gate.sh` → 0 warnings, 1437 tests, 7917 assertions, 0 failures (`pm.lot-test`
already landed from the tester's parallel work and is green).

## Hand tests (no `state/` ever written)

`--landmarks` against the real `resources/estate.edn` + `state/ground.edn` (read-only):

```
bench-by-creek: the bench by creek
bench-by-mailbox: the bench by mailbox
creature-spike: the creature spike
creek: the creek
flora-body: Flora's body
flora-jar: Flora's jar
juniper-body: Juniper's body
lamp-post-by-canal: the lamp post by canal
lobby: the lobby
mailbox: the mailbox
phone-booth: the phone booth
workshop: the workshop
```

`--dry-run` against the real ground map, `PM_BLUEPRINTS_FILE` pointing at a temp copy of
`state/blueprints.edn` (a bench-by-creek draft under this run dir, see below):

```
$ cp state/blueprints.edn /tmp/pm-blueprints-dryrun.edn
$ PM_BLUEPRINTS_FILE=/tmp/pm-blueprints-dryrun.edn bb blueprint-draft un-automate-monde \
    crew/runs/blueprint-draft-v0-20261124-1208/bench-by-creek.edn --near creek --dry-run
placed bench-by-creek 1.58 m from the creek at [3 3.22 -19] yaw 135 (feet within 12 cm)
$ diff state/blueprints.edn /tmp/pm-blueprints-dryrun.edn   # unchanged either way
```

Exit 0, nothing written to `/tmp/pm-blueprints-dryrun.edn` or to the real `state/`.

## The command Hal runs to see a bench appear live

The draft lives at `crew/runs/blueprint-draft-v0-20261124-1208/bench-by-creek.edn`:

```
bb blueprint-draft un-automate-monde crew/runs/blueprint-draft-v0-20261124-1208/bench-by-creek.edn --near creek
```

(No `--dry-run`, no env overrides — this one does write `state/blueprints.edn` for real, via
`bb scripts/blueprint_add.clj`, so its neon-blue ghost appears on the next 5 s poll.)
