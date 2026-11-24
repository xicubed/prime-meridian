# Builder notes: view-share-v0 (TASKS T-19)

## What was built

- `src/pm/view.cljc` (new, ns `pm.view`): `vec3?`, `round-cm`, `snapshot`,
  `changed?`, `post?`, `sent`/`settled`, `view-url`, `body`, `view-edn`,
  `fresh`, `ground-word`, `where-lines`. Pure, portable (bb + cljs).
- `src/pm/bridge.cljc`: `view-uri`, the `/view` route added to the method/
  route/json/token/origin guard chain, its own validation branch (action,
  camera, center, walkers, max-walkers, per-walker id/pos/dup checks), and
  `log-line`'s accepted action set extended with `"view"`.
- `src/pm/three.cljs`: `rig-view` (camera pos + rig's orbit target, or nil)
  and `center-hit` (raycast from the camera through screen centre against a
  mesh list, or nil). Both interop-only, never throw.
- `src/pm/core.cljs`: `!view` state, a cached `!view-meshes` (the kit's
  ground meshes, found once), and `view-tick!` (forward-declared, called
  right after `camera/save-tick!` in `frame`): throttles to `view/post-
  every-ms`, builds a snapshot, and POSTs it to the bridge when `view/post?`
  says to. Never in the demo build (`DEMO` goog-define).
- `src/pm/lot.cljc`: `no-view`, `sky` constants; `usage` updated to mention
  `here`/`walker`; `view-landmarks`; `landmarks` is now multi-arity (3-arity
  unchanged, 4-arity merges in the view's `:here`/walkers, estate names
  win); `nearest-landmark`; `place` is now multi-arity (6-arity = 7-arity
  with `seen` nil, unchanged behaviour; 7-arity resolves `:here`/walker-id
  `near` from a fresh view, with the sky/no-view/stale refusals the spec
  gives).
- `scripts/crew_bridge.clj`: `view-file` (`PM_VIEW_FILE` or
  `state/view.edn`), and a `:view` branch in `handle` that writes
  `view/view-edn` atomically, replies 200 without logging on success, logs
  + replies 500 on a write failure.
- `scripts/blueprint_draft.clj`: reads the view (`PM_VIEW_FILE`), computes
  `seen` via `view/fresh`; `--landmarks` and the placed-line's landmark word
  now go through the 4-arity `landmarks` (view names included); a draft
  call uses the 7-arity `place` with `seen`.
- `scripts/where.clj` (new): `bb where` — usage/exit 2 on any argument;
  reads the view, ground, estate and file blueprints; exit 1 with the
  refusal reason when the view isn't fresh; otherwise prints
  `view/where-lines`, one per line, exit 0.
- `bb.edn`: the `where` task.

## Hand-tested (temp files; `state/` never written by hand)

All three runs used `PM_BLUEPRINTS_FILE` pointed at a temp copy of the real
`state/blueprints.edn` (never the real path) and `--dry-run` for every
`blueprint-draft` call, so nothing in `state/` was touched.

**Fresh `PM_VIEW_FILE`** (`:as-of` now, `:center [3.0 6.2 -23.0]`, one walker
`:un-automate-monde`):
```
$ PM_VIEW_FILE=<fresh.edn> bb where
view 33 s old
looking at [3 -23]: open ground, 0.67 m from the lobby
un-automate-monde at [4 -22]: 1.8 m from the lamp post by lobby

$ PM_VIEW_FILE=<fresh.edn> PM_BLUEPRINTS_FILE=<temp copy> \
    bb blueprint-draft un-automate-monde <draft.edn> --near here --dry-run
placed hand-test-item 1.8 m from where you're looking at [4 5.55 -21.5] yaw 0 (feet within 0 cm)

$ PM_VIEW_FILE=<fresh.edn> PM_BLUEPRINTS_FILE=<temp copy> \
    bb blueprint-draft un-automate-monde <draft.edn> --near un-automate-monde --dry-run
placed hand-test-item 0.5 m from Un Automate at [4 5.55 -21.5] yaw 0 (feet within 0 cm)

$ PM_VIEW_FILE=<fresh.edn> bb blueprint-draft --landmarks
(... estate landmarks ..., here: where you're looking, ..., un-automate-monde: Un Automate, ...)
```

**Stale `PM_VIEW_FILE`** (`:as-of 0`):
```
$ PM_VIEW_FILE=<stale.edn> bb where
where: I can't see where you're looking: the game isn't open   (exit 1)

$ PM_VIEW_FILE=<stale.edn> PM_BLUEPRINTS_FILE=<temp copy> \
    bb blueprint-draft un-automate-monde <draft.edn> --near here --dry-run
blueprint-draft: I can't see where you're looking: the game isn't open   (exit 1)
```

**Missing `PM_VIEW_FILE`** (path doesn't exist):
```
$ PM_VIEW_FILE=<missing.edn> bb where
where: I can't see where you're looking: the game isn't sending its view   (exit 1)

$ PM_VIEW_FILE=<missing.edn> PM_BLUEPRINTS_FILE=<temp copy> \
    bb blueprint-draft un-automate-monde <draft.edn> --near here --dry-run
blueprint-draft: I can't see where you're looking: the game isn't sending its view   (exit 1)
```

Also checked: `bb where extra-arg` → `usage: bb where` on stderr, exit 2.

## What Hal runs to see it live

1. Restart the bridge (`bb crew-bridge`) so it picks up the `/view` route.
2. Reload the game at `http://localhost:8080` (not the demo build).
3. In another terminal, either:
   - `watch -n 2 cat state/view.edn`, or
   - run `bb where` twice a few seconds apart,
   while orbiting/panning the kit's camera and walking a crew member
   around. `state/view.edn`'s `:as-of`, `:camera`, `:center` and `:walkers`
   should update at most every 2 s, only after the camera/centre/a walker
   has actually moved (or every 30 s as a keep-alive), and never while the
   demo build is open.
