# AGENTS.md — how to work in this repo

Read this before touching anything. It applies to humans and agents alike.

## What this is
Prime Meridian: a 3D management game where the characters are real AI agents. Design lives in `docs/GDD.md` — read it first; it defines the vocabulary (estate, workshop, possess, autonomy dial, status markers).

## Stack
- **ClojureScript** compiled by **shadow-cljs** (JVM: `/opt/homebrew/opt/openjdk@21`). Entry `pm.core/init!`, hot-reload hook `pm.core/reload!`.
- **three.js 0.186** via **`three/webgpu`** (`WebGPURenderer`, falls back to WebGL2 automatically; init is async — see `pm.three/init-renderer!`). This matches the Hiroshi's Island kit (three 0.186.1, TSL node materials) so its water/wind/sky code can be ported directly. Import from `"three/webgpu"` (and `"three/tsl"` for node materials), never bare `"three"` — mixing the two breaks materials.
- All three.js interop goes through `src/pm/three.cljs`. Do not sprinkle raw `THREE/...` calls elsewhere; extend the wrapper instead.
- Licensed kit lives in `assets/private/` (gitignored, never commit): `kit/source/src/*.ts` is readable reference for water (`water.ts`, `cozy/bounded-concept-water.ts`), wind (`cozy/vegetation-wind.ts`), daylight presets (`daylight.ts`, `time-transition.ts`), DSLR camera (`cozy/photo-camera.ts`). License: one commercial project (this one). Port ideas and code into CLJS; do not copy the GLB into the repo.
- **Babashka** for scripts and (later) the bridge server. No TypeScript, ever.
- World state is one atom (`pm.core/!world`). Render reads it; nothing else writes to the scene directly.

## Build / run / check
```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:$PATH" JAVA_HOME=/opt/homebrew/opt/openjdk@21
npm install
npx shadow-cljs compile app      # must finish with "0 warnings"
npx shadow-cljs watch app        # dev server on http://localhost:8080 with hot reload
```
A change is not done until `compile app` reports **0 warnings** and the page runs (the HUD counter advances). Type-hint interop locals with `^js` to silence inference warnings — do not disable the warnings.

## Conventions
- Namespaces: `pm.core` (entry/loop), `pm.three` (interop), `pm.world` (state + rules), `pm.scene` (state → three objects), `pm.bridge` (websocket to agents). Create them as needed; keep each small.
- Flat-shaded low-poly only. Palette in `pm.scene` as a map; no magic hex literals in feature code.
- Functions that mutate three.js objects end in `!`.
- Keep `docs/GDD.md` the source of truth; if a task changes the design, update the GDD in the same PR.

## Git
- Work on `main`. Commit as you go (small commits, clear messages); **push once at the end of the day**. The day's push includes `docs/session-<date>.md`, Hal's daily blog post: what was built, what was decided, what's open.
- Each commit's author shows who did it: Michael, or Hal (hal-niner).
- Never force-push or rewrite pushed history. Branches and PRs are only for risky or experimental work, and only when Michael asks for one.
- **When more than one agent works at once** (the crew, GDD §10d), each gets its own `git worktree` on a branch (`../pm-<agent>`, branch `crew/<agent>/<task>`) so they never share a working tree. Hal merges finished worktree branches into `main` locally and removes the worktree; the end-of-day push is unchanged. Until then, single-agent days stay on `main`.
- Before the push, the build gate must pass (`compile app` → 0 warnings) if any code changed.
- Do not commit `public/js/`, `node_modules/`, `.shadow-cljs/` (gitignored).

## Tasks
Open work is in `TASKS.md`. Take the top unclaimed item, mark it `[~] <your name>`, do it, and mark it `[x] <date>` in the commit that finishes it.

## Don'ts
- No new npm deps without a line in the PR body saying why.
- No physics engine in v1.
- No secrets in the repo. Credentials come from 1Password via `~/.hermes/bin/op` on Hal's machine only.
