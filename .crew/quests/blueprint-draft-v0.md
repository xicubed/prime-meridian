# Quest: a crew member places what you ask for (TASKS T-13, the last piece)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 22 (T-13, the demo scene "a bot builds
something, and you finish it from the wireframe"). You ask a crew member in 💭 ("put a bench by
the creek"); the member draws the thing's look and runs one command that **finds a level, open
spot near the place you named, clear of everything else**, and proposes the blueprint. Its
neon-blue ghost appears while you watch (that part exists), you click it to try it, then make
it real. A member can't see the world, so the command does the looking: it reads a ground map
sampled from the kit, never guesses. **Led by:** Hal. Michael reviews.

## What exists (read first)

- `scripts/blueprint_add.clj` + `src/pm/blueprint_file.cljc` (T-13b, Nov 23): `bb blueprint-add
  <member> <file.edn>` validates a blueprint and appends it to the private
  `state/blueprints.edn`; the game's 5 s poll seats it (`pm.blueprint/seat`: level at the highest
  foot, lower feet stretched; more than `max-seat-drop-m` of drop is refused) and draws it, and
  its author says "drew a blueprint: …" in 💭. `PM_BLUEPRINTS_FILE` / `PM_BUILDS_FILE` override
  the paths (use them in any hand test; never write the real `state/`).
- `resources/estate.edn`: places with an `:at` (`:mailbox`, `:phone-booth`, `:workshop`,
  `:lobby`, `:creature-spike`), each mirror's `:placed` items (`:at`, `:scale`, `:look`), the
  sample `:blueprints`, the walkers' `:route`s, `:motion :obstacles` (circles). The comment
  under `:blueprints` documents the look/part format (`:at` is a part's centre from the item's
  base, y up; `:yaw` in degrees, +90 points +x at world -z: world = at + (c·dx + s·dz, −s·dx +
  c·dz)).
- **New, made by Hal before this round: `state/ground.edn`** (gitignored; derived from the
  licensed kit, so it never goes in the repo). The kit's ground on a 0.5 m grid:
  `{:as-of <ms> :origin [x0 z0] :step 0.5 :cols 45 :rows 61 :kinds ["row" …] :y [[…] …]}`.
  Row r is z = z0 + r·step, column c is x = x0 + c·step. `:kinds` is one string per row, one
  char per cell: `o` open terrain (the only buildable kind), `T` under canopy, `B` bush/wall at
  body height, `R` rocks, `:` creek bed / canal gravel, `~` water, space = no ground. `:y` is the
  ground height (m) per cell, `nil` where there is none. Read its header comment. Tests must not
  read it: build tiny grids in the test.
- `bb hal-say "line" --as <member>` (`scripts/hal_say.clj`): a member says a line in-world.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests or hand tests (use temp files + the env overrides).** No npm
  deps.

## Decided (don't reopen)

- **Pure placement in a new `src/pm/lot.cljc`** (cljc: bb and the cljs tests both load it; no
  I/O, no `js/Date`). It takes the ground map, the estate, the file blueprints, the rules and a
  draft, and returns either `{:ok true :at [x y z] :yaw deg :landmark k :distance-m d :spread-m s}`
  or `{:ok false :reason "<one line a player can read>"}`.
- **The draft** a member writes is one map: `{:id :kw :summary "…" :look {:parts […]}}` (no
  `:at`, no `:yaw`; the command adds them). Parts sit on y 0 = the ground.
- **Feet** come from the look: every part whose bottom (`at-y − size-y/2`, rotation ignored) is
  within 2 cm of 0. A grounded part at most 0.3 m across (x and z) is one foot at its centre;
  a wider one gives its four xz corners and its centre. A look with no grounded part is refused
  ("nothing touches the ground").
- **Rules are data** in `resources/estate.edn`, a new top-level `:build-rules` (builder adds it,
  with a comment): `{:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :gap-m 1.2 :route-m 0.8
  :max-spread-m 0.12}`. Tests read their numbers from a rules map they build, never restate the
  estate's.
- **A spot is good when** (candidates on the `:step-m` grid within `:search-m` of the landmark,
  each yaw in `:yaws`): every foot's cell (nearest cell) is `o` with a height; the feet's height
  spread ≤ `:max-spread-m`; the item's footprint circle (radius = half the diagonal of the look's
  xz bounding box) stays ≥ `:gap-m` edge to edge from every other thing's circle; and ≥
  `:route-m` from every walker `:route` segment (point-to-segment distance minus the radius).
  "Every other thing": the places with an `:at` (radius 0.6 m), every mirror `:placed` item
  (radius from its look's bounding box × its `:scale`, else 0.6 m), every estate and file
  blueprint (from its look), and the `:motion :obstacles` circles (their `:r`). The returned
  `:at` y is the **highest** foot's ground height (the game's seat does the rest).
- **Pick** the candidate closest to the landmark, ties broken by the smaller spread, then
  smaller x, then smaller z, then smaller yaw (deterministic; tests pin it).
- **Landmarks**: every place with an `:at` by its key (`:mailbox`, `:phone-booth`, `:workshop`,
  `:lobby`), every mirror `:placed` item as `:<mirror>-<item>` (e.g. a jar), every blueprint by
  its id, and `:creek` = the nearest `:` or `~` cell of the ground map. Distance to a landmark is
  centre to centre (to the nearest creek cell for `:creek`).
- **Refusals say why** in a line he can read, naming the most common reason among the rejected
  candidates: "no level open ground within 6 m of the workshop: most of it is under trees" /
  "…: too close to the phone booth" / "…: on a walker's path" / "…: too steep"; plus unknown
  landmark ("I don't know a place called …; I know: …") and no ground map ("no ground map
  (state/ground.edn): the twin samples it from the kit").
- **The command:** `bb blueprint-draft <member> <draft.edn> --near <landmark> [--dry-run]`
  (`scripts/blueprint_draft.clj`, a `bb.edn` task like `blueprint-add`). It reads
  `state/ground.edn` (`PM_GROUND_FILE` overrides), the estate, and the blueprints file
  (`PM_BLUEPRINTS_FILE`), places the draft, prints one line: `placed <id> <distance> m from
  the <landmark> at [x y z] yaw N (feet within S cm)`, and unless `--dry-run` hands the full
  blueprint to the **existing** `scripts/blueprint_add.clj` (write it to a temp file and run that
  script with the same env, so there is one writer and one validator). Exit 0 placed, 1 refused
  (the reason on stderr), 2 usage. `bb blueprint-draft --landmarks` lists the landmark names
  (one per line, with the place's word for it). No new keys in the blueprint shape.
- **Everyone's game:** nothing in code names a person, pet, member or a specific prop; landmark
  names come from the estate's keys. No `::k` keys across namespaces.

## Out of scope

The member's role-card section on answering build requests (Hal writes it after this round);
editing the game or the bridge (the ghost, trial and make-real paths are done); re-sampling the
ground map; walkers avoiding builds (T-7); a reject button; the demo build.

## Done when

Gate passes with tests for `pm.lot` on small hand-built grids and estates: feet from a look
(narrow legs, a wide base, nothing grounded); a level open spot found and its `:at`/`:yaw`
exact; each refusal reason (canopy, steep, too close, on a route, unknown landmark); the
highest-foot y; yaw rotation of the feet (a 90° candidate's feet land where the formula says);
`:creek` as a landmark; the tie-break order. The builder leaves `{run}/builder-notes.md`: what
was built, a `--dry-run` it ran against the real ground map with `PM_BLUEPRINTS_FILE` pointing
at a temp copy (its printed line), and the exact command Hal runs to see a bench appear live.
