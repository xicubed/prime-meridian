# Quest: the twin knows where you're looking (and where everyone is)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 24: "lets send it to you so you know the
location without a screenshot / high count for tokens". He asks only his twin (Hal) for things
("put a bench here", "put one by him"); the twin works out where and hands the job to a crew
member. Today the twin can only guess "here" from a screenshot of his browser, which is slow and
costs image tokens. The game already knows: it saves the camera every 2 s (`pm.camera`) and
knows every walker's position (`pm.bricks/spots`). This quest sends that to a private file the
twin and the crew's commands can read. **Led by:** Hal. Michael reviews. **Nothing new on
screen.**

## What exists (read first)

- `src/pm/camera.cljs`: `current` (camera pos + orbit target), `save-tick!` (every
  `save-every-ms`, only when moved: `changed?`), called from the frame loop in `pm.core`.
- `src/pm/bricks.cljs` `spots`: `[{:id :agent :name :pos [x y z]}]` for every walker.
- `src/pm/three.cljs` `drop-ray` (a downward ray against the kit's meshes) and the kit handle
  `window.hiroshi` (`THREE scene camera rig renderer`). The kit's ground meshes are named
  `SM_Hiroshi_Terrain`, `…Gravel…`, `…Rocks…` (see how `pm.three`/`pm.blueprint` find ground).
- The bridge: `src/pm/bridge.cljc` `validate` (POST routes `/crew/<member>`, `/hold`, `/real`:
  token + exact game Origin, `max-body`), `scripts/crew_bridge.clj` `handle` (writes
  `state/hold.edn`, `state/builds.edn` atomically with `write-edn!`; logs every request to the
  gitignored `crew/bridge.log`). The game posts with `post-real!` in `src/pm/core.cljs` using
  `(:bridge @!world)` (port + token, read from `/state/bridge.edn`); `bridge-on?`.
- `src/pm/lot.cljc` + `scripts/blueprint_draft.clj` (T-13, today): `bb blueprint-draft <member>
  <draft.edn> --near <landmark|x,z>` places a draft near a named landmark or a spot;
  `landmarks`, `resolve-landmark`, `coord-near`, `parse-args`.
- The demo build (`pm.core/DEMO true`) skips every bridge/state call; it must stay that way.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  or restart services or the bridge (Hal restarts it after the round); no whole-disk `find`;
  scratch files only in your own scratchpad; never write `state/` from tests or hand tests
  (temp files + env overrides).** No npm deps.

## Decided (don't reopen)

- **A new bridge route `POST /view`**, same guards as `/hold` (token, Origin, size), body
  `{"token" … "action" "view" "camera" {"pos" [x y z] "target" [x y z]} "center" [x y z] | null
  "walkers" [{"id" "<walker id>" "pos" [x y z]} …]}`. Every number finite and within the
  `pm.camera` coordinate bound; ids match the bridge's id pattern; at most 32 walkers; anything
  else → 400 with a reason. Pure validation in `pm.bridge` (tested); the bridge writes
  **`state/view.edn`** atomically: `{:as-of <ms> :camera {:pos [..] :target [..]} :center
  [x y z]|nil :walkers {:<id> [x y z] …}}`. **Successful `/view` posts are not logged** to
  `crew/bridge.log` (they'd flood it); refusals are.
- **The game posts it** from the frame loop via a small pure helper (new `src/pm/view.cljc`):
  at most every 2 s, and only when something changed since the last post (camera pos/target
  moved > 5 cm, center moved > 10 cm, or any walker moved > 25 cm, or appeared/disappeared),
  plus once 30 s after the last post even if nothing changed (so `:as-of` shows the page is
  alive). Never in the demo build, never when the bridge is off, never while a post is in
  flight. Rounded to centimetres.
- **`:center`** is where the screen's centre ray hits the kit's ground meshes (raycast from the
  camera through NDC (0, 0)); `nil` when it hits nothing (looking at the sky). Interop for the
  ray lives in `pm.three`; the decision logic in `pm.view`.
- **The crew's command learns two new names** (`pm.lot`, `scripts/blueprint_draft.clj`):
  `--near here` = the view's `:center`, and `--near <walker-id>` = that walker's position (e.g.
  `--near un-automate-monde` for "by you"). Both read `state/view.edn` (`PM_VIEW_FILE`
  overrides) and only when it's fresh (`:as-of` within 120 s of now); otherwise refused with a
  line saying why ("I can't see where you're looking: the game isn't open" / "…isn't sending
  its view"). `--landmarks` lists `here` and the walkers when the view is fresh. `now` is an
  argument in pure fns.
- **A `bb where` command** (`scripts/where.clj`, `bb.edn` task) prints, from `state/view.edn`,
  one short block the twin reads instead of a screenshot: how old the view is; the centre spot
  `[x z]`, its ground kind from `state/ground.edn` (open / under trees / creek …) and the
  nearest named landmark with its distance; then each walker's `[x z]` and nearest landmark.
  Pure formatting in `pm.view` (tested); exit 1 with a reason when there's no fresh view.
- **Everyone's game:** nothing in code names a person, pet or member; walker ids are data.
  No `::k` keys across namespaces.

## Out of scope

Showing members' 💭 replies in their threads (a separate bug); any UI; changing how blueprints
are drawn or approved; the demo build; restarting the bridge (Hal does it and reloads the game).

## Done when

Gate passes with tests for: `/view` validation (good body; each bad field refused with its
reason; wrong token/origin); the post decision (first post, throttle, each change threshold,
the 30 s keep-alive, demo/bridge-off/in-flight = no post); the view file's shape from a body;
`--near here` and `--near <walker>` placing from a fresh view and refusing a stale or missing
one; `bb where`'s formatting (fresh view, nil center, no ground map). The builder leaves
`{run}/builder-notes.md`: what was built, what was hand-tested (with temp files), and exactly
what Hal runs after restarting the bridge to see `state/view.edn` update while he moves the
camera.
