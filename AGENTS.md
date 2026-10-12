# AGENTS.md — how to work in this repo

Read this before touching anything. It applies to humans and agents alike.

## What this is
Prime Meridian: a 3D management game where the characters are real AI agents. Design lives in `docs/GDD.md` — read it first; it defines the vocabulary (estate, workshop, possess, autonomy dial, status markers).

## Stack
- **ClojureScript** compiled by **shadow-cljs** (JVM: `/opt/homebrew/opt/openjdk@21`). Entry `pm.core/init!`, hot-reload hook `pm.core/reload!`.
- **three.js** from npm. All three.js interop goes through `src/pm/three.cljs`. Do not sprinkle raw `THREE/...` calls elsewhere; extend the wrapper instead.
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
- Never commit to `main`. Branch per task: `feat/<task-id>-short-name`, `fix/...`, `docs/...`.
- One task per PR. PR body: what, why, how verified (paste the compile line and what you saw running).
- Hal (hal-niner) opens PRs; Michael merges. Agents do not merge.
- Do not commit `public/js/`, `node_modules/`, `.shadow-cljs/` (gitignored).

## Tasks
Open work is in `TASKS.md`. Take the top unclaimed item, mark it `[~] <your name>` in a commit on your branch, do it, open the PR, mark it `[x]` with the PR number in that PR.

## Don'ts
- No new npm deps without a line in the PR body saying why.
- No physics engine in v1.
- No secrets in the repo. Credentials come from 1Password via `~/.hermes/bin/op` on Hal's machine only.
