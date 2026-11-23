# Quest: a blueprint an agent adds appears while you watch (TASKS T-13, part b)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 22 (T-13, the demo scene "a bot builds
something, and you finish it from the wireframe"). You ask in 💭 ("put a bench by the creek"), a
crew member drafts the blueprint, and its neon-blue ghost appears in the world **without a page
reload**, with a line from whoever drew it. Click = trial build, second click = real
(`state/builds.edn`), as today. **Led by:** Hal. Michael reviews.

## What exists (read first)

- `resources/estate.edn` `:blueprints`: the shipped sample blueprints (`:id :at :yaw :summary
  :look {:parts [...]}`), compiled into the game (`world/estate-edn`); `!world` in
  `src/pm/core.cljs` is a `defonce` built from it once. That is why a new entry only shows after
  a reload today. The comment block under `:blueprints` documents the look/part format.
- `src/pm/blueprint.cljs` (pure, tested): `all`/`pending` (validation: `valid-blueprint?`),
  statuses, `approve-blueprint`, `apply-real-builds`, `seat` / `seat-all` (ground under each
  foot; `:too-steep?`).
- `src/pm/core.cljs`: `fetch-builds!` + `start-state-poll!` (every `state-poll-ms`, 5 s) read
  `/state/builds.edn`; `seat-blueprints!` seats every blueprint once, in `start-kit!`, before
  `scene/mount!` draws the world. `src/pm/scene.cljs` draws ghosts (`pm.three/ghost!`, neon
  blue) and signs (`pm.scene/plate`, two-line text).
- `src/pm/dev_http.clj`: serves any `/state/<name>.edn` same-origin only (no change needed for a
  new file there).
- `scripts/crew_bridge.clj` `make-real!`: records a build only when its id is one of the
  estate's blueprint ids (`(set (map :id (:blueprints e)))`).
- `bb hal-say` (`bb.edn`, `scripts/`) is the model for a small bb command an agent runs.
- The demo build (`pm.core/DEMO true`) skips every `/state/` poll; it must stay that way.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests (use temp files).** No npm deps.

## Decided (don't reopen)

- **Agent blueprints live in a gitignored file, not in `estate.edn`:** `state/blueprints.edn`,
  `{:as-of <ms> :blueprints [<blueprint> ...]}`, same blueprint shape as the estate's plus
  `:by <member-keyword>` (who drew it). The public sample estate never changes when an agent
  proposes something.
- **Agents add one with a bb command:** `bb blueprint-add <member> <file.edn>` (the file holds
  one blueprint map). It validates with `pm.blueprint`'s own rules (one validator, not a copy;
  make `valid-blueprint?` public or add a public `problems` fn that says what's wrong), refuses an
  id already in the estate or the file (exit 1, one line saying why), sets `:by`, appends, bumps
  `:as-of`, writes atomically (temp file + rename), prints `added <id>`. `bb blueprint-add
  --remove <id>` takes one out (only from the file; estate ones can't be removed). Exit codes: 0
  ok, 1 refused, 2 usage.
- **The game merges them live:** the 5 s state poll also reads `/state/blueprints.edn`; when
  `:as-of` changes, a pure fn in `pm.blueprint` merges the file into the world: new ids are
  added (pending), ids removed from the file disappear unless they are `:real` or a trial is
  standing, estate ids always win over a file entry with the same id, and existing entries keep
  their status. Then the new ones are **seated** (the same `seat`, only the new ones) and
  **drawn** without a reload and without redrawing or moving anything else (no remount of the
  whole estate; add/remove that blueprint's group only). Real builds from `builds.edn` apply to
  file blueprints the same way as estate ones.
- **The author says it:** each newly arrived blueprint adds one talk entry from its `:by`
  member: `"drew a blueprint: <summary>. Click it to try it."` (text from
  `resources/content/hal-lines.edn` or the estate `:texts` if an event fits; otherwise one
  hard-coded fallback with a `;; TODO content` comment). Once per id, not per poll, and not for
  blueprints already present at page load.
- **The bridge accepts them:** `make-real!` takes ids from the estate **and** from
  `state/blueprints.edn` (read at request time, not at start-up).
- **Persistence:** on reload, file blueprints come back as pending (or real, per `builds.edn`).
  Rejecting an agent blueprint is out of scope (`--remove` is the way for now).
- **Everyone's game:** nothing in code names a person, pet or member; `:by` is data. No `::k`
  keys across namespaces. `now` is an argument in pure fns, never `js/Date`.

## Out of scope

How a member decides what to build or where (a later quest teaches the role card); a reject
button; editing an existing blueprint; the demo build; any new panel or control.

## Done when

Gate passes with tests for: the merge fn (new id added pending; existing status kept; estate id
wins; removed id dropped; removed-but-real kept; unchanged `:as-of` is a no-op); the bb command's
pure part (valid add, duplicate id refused, invalid blueprint refused with the reason, remove);
the bridge's real-build id set including file ids; the author line once per new id. The builder
leaves a short note in the run directory: what was built, what was tested by hand, and what Hal
should run to see it live (a `bb blueprint-add` example with a blueprint file the manager will
place; the builder does not run it against the real `state/`).
