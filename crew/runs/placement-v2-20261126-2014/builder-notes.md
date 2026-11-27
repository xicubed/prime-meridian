# Builder notes: placement-v2

Gate: 0 warnings, 1737 tests, 0 failures (includes `pm.placement-v2-test`, already landed).

## Changed

- `src/pm/lot.cljc`: `here-word`, `face-yaw`, `at-line`; `parse-args` takes `--at <here|x,z>`
  (plus `--dry-run` in both orders); `usage` updated; `view-landmarks` now uses `here-word`.
- `src/pm/view.cljc`: `here-ms` (30000), `keep-alive-ms` 30000 -> 10000, `here-stale`/`here-sky`;
  `at-here` and `at-xz`.
- `scripts/blueprint_draft.clj`: new `:at` mode (reads `state/view.edn` directly, no ground map
  needed); factored the validate/add/print tail into `finish!`, shared by `:draft` and `:at`.
- `src/pm/ask_build.cljc`: `here?`, `plan-here` (places at `:here`/`:camera` with no grid, no
  `lot/place`).
- `src/pm/blueprint.cljs`: `seat` drops `:too-steep?` (keeps `:seat-drop`); deleted
  `max-seat-drop-m` and `refuse-steep`; `sign-text` drops the `steep` branch.
- `src/pm/camera.cljs`: `center-m` (1.0); `fly-decision` gets a `:center` rule (`:at-center`)
  between `:player-moving` and `:in-view`.
- `src/pm/core.cljs`: `apply-approved!` drops the too-steep gate; `show-arrival!` passes `:center`
  (`t/center-hit`); `demo-ask!` routes `:here` requests through `ab/plan-here` instead of
  measuring a grid.
- `resources/estate.edn`: deleted `:blueprint-text :steep`.

Grepped `too-steep?`/`max-seat-drop-m`/`refuse-steep` across `src/`, `scripts/`, `resources/`
afterward: nothing left outside what the spec kept (`merge-file`'s existing strip of stale file
keys needed no change).

## How Hal checks it live

1. Look at a spot (a slope by the lobby is a good one).
2. `bb where` — note the centre point it prints.
3. `bb blueprint-draft <member> <draft.edn> --at here --dry-run`, then the same without
   `--dry-run`, and read the printed line (`placed <id> where you're looking, at [...] yaw ...,
   facing the camera`).
4. Check `state/blueprints.edn`: `:at` is exactly the centre point from step 2, `:yaw` faces the
   camera.
5. The ghost appears in the middle of the screen, facing him, and the camera does **not** fly
   (console prints `camera: at-center`).
6. Click it, then Make it real, on the slope: no "too steep" anywhere, and the legs stretch to
   meet the ground.
7. In the demo build, "a bench here" in 💭 does the same, with no ground-grid line in the
   console (the grid log only fires for a named place now).

Didn't hit the hidden-tab timing note from §11 live (nothing in this round needed a `--at here`
while the tab was covered) — flagging it here per the spec in case Hal's own live check does.
