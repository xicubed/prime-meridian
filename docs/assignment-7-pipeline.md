# Assignment 7: the complete AI dev pipeline (due Sat Nov 29, 2026, 11:59 PM ET = 8:59 PM PT)

*Working document, started Nov 21. The assignment's own template is in the headings below; each
field is either **filled** (we know it), **known gap** (we know what's needed), or **open** (needs a
decision from Michael). The rubric is 10 points; the playable link is a gate — missing or broken =
max 50% on everything. Hal keeps this file current; the final version becomes the submission plus a
~1-page audit at the top of `README.md`.*

## The rubric, and where we stand

| Criterion | Pts | Where we stand |
|---|---|---|
| Playable link (stranger plays within 2 min, instructions at top of README) | 2.0 | **Open.** No public URL yet. `bb demo-build` makes `dist/demo/` (441 MB, static). Host + kit license + README instructions needed. |
| Pipeline-to-game connection (content traceably produced by the pipeline; anti-slop) | 3.0 | **Strong.** `content/pipeline.clj` → `resources/content/*.edn`; every line Hal, Flora and Juniper speak, and the sample inbox, come from committed runs with logs. Needs the run video. |
| Engine integration (output lands in the engine, no manual reformatting; 1 documented manual step allowed) | 2.0 | **Strong, one manual step.** The pipeline writes EDN the game inlines at compile time (`rc/inline`). The one step: a rebuild (`shadow-cljs` watch does it on save; the release build is a command). Document it. |
| Cost analysis (actual run; most expensive step; solo-dev sustainability) | 2.0 | **Known gap.** `claude -p` returns `total_cost_usd` and token counts; `call-model` captures them but `run-request` only logs output tokens. Add cost to the log + `scores.edn`, then one real run gives the number. |
| Pipeline audit (~1 page in README: output, manual steps, one architectural change, before/after cost reduction) | 1.0 | **Mostly known.** Content below; needs the before/after numbers. |

(The template's "DELIVERABLE 3 (>20 PAGE)" contradicts its own rubric, which says "~1 page in the
README". We write the one page, and this file is the long form it links to.)

## Student & game overview

- **Student:** Michael Lopez
- **Capstone:** Prime Meridian
- **Concept brief (filled):** A cozy 3D estate game where the characters are real AI agents doing
  real work. The player's digital twin (Hal) runs the estate: a crew of six agents (French-named
  after Monet's circle) build the game itself from the game, creatures mirror real pets and their
  real chores, and the game sends the player *back to life* (closing time, the hardline ringing)
  rather than keeping them in. Every connection starts as a SAMPLE and becomes REAL when wired
  up, so the whole loop plays with zero accounts. ClojureScript + three.js in the browser.

## Deliverable 1: playable link — OPEN

**What exists:** `bb demo-build` → `dist/demo/` (release build, DEMO mode: pretend data only, no
twin file, no crew bridge). 441 MB, of which ~196 MB is the island kit (`public/kit/`, gitignored,
licensed).

**Decided (Nov 21): the demo goes up behind a PIN**, so the kit question is deferred to a public
release; a grader with the PIN in the submission still plays within two minutes. Ways to do it:

- **itch.io "Restricted" visibility + password** (free, built in): the page asks for the password,
  then "Run game" in the browser. Cleanest fit — a grader sees a game page, and the password is the
  PIN. Upload limit 1 GB (ours is 441 MB).
- **Cloudflare Pages/Netlify + a client-side PIN screen** in the demo build (a small overlay before
  the kit loads; the PIN check is in JS, so it's a courtesy lock, not security — fine for this).
  Netlify's real password protection is a paid tier.
- **Michael's own server** (not lotect.com, which is WordPress.com): `rsync dist/demo/` to a
  docroot and lock it at the web server — nginx `auth_basic` (one user, `htpasswd`) or a Caddy
  `basicauth` block. A real lock, no third party, no size limit; the browser's password prompt is
  the PIN. Needs HTTPS (Caddy: automatic; nginx: certbot). **Lean (Nov 21).** Fallbacks: itch.io
  restricted; Netlify + PIN overlay.

**Remaining decisions:**

1. **Host.** Options: Netlify (drag-and-drop or CLI; free tier 100 GB/mo bandwidth, 441 MB site is
   fine), Vercel (static; similar), Cloudflare Pages (25 MB per-file limit — check the biggest kit
   GLB), itch.io (HTML5 upload, 1 GB limit; "play in browser" is the format graders expect).
   lotect.com is WordPress.com and can't serve this. **Lean: Netlify or itch.io; itch.io reads as
   "a game" to a grader.**
2. **Kit license — deferred.** Hiroshi's Island (3ditions) is a purchased asset pack. Behind a PIN
   for a grader it's private use; before any public release, read the license in
   `assets/private/` (serving GLBs in a published game is usually fine, redistributing the pack is
   not). The kit never goes in the repo either way.
3. **Two-minute test.** A stranger opens the URL: does the kit's loading screen + the Construct's
   first beat get them playing in under 2 minutes with no instructions? We'll time it on a cold
   cache and a normal connection. Instructions block goes at the **top of `README.md`** (rubric
   says so): the link, three lines on what to do (click an agent, open 💭, click a creature), and
   "no account needed".
4. **Audio.** Browsers need a click before sound; the ▶ brook button handles it. Note it in the
   instructions.

**Known risks:** 441 MB on a slow connection may exceed 2 minutes to first frame. Mitigation: check
what the kit loads up front vs on demand; drop unused kit assets from the demo build; Draco/meshopt
compression of the GLBs if needed (that's a build step, not a redesign).

**Playable link (filled Nov 21):** https://pm.perstitio.us — Apache basic auth on Michael's
Lightsail box (Debian, stock Apache, certbot). User `guest33`, PIN in the submission. Deployed with
`bb demo-deploy` (build + rsync, bot's own deploy key). Still to do: time the cold-cache load; the
README's top block (link, PIN note, three lines of what to do, "click once for sound").

## Two videos (decided Nov 21)

1. **Pipeline run video** (the A7 deliverable): `bb content mirror-lines` with cost logging →
   trajectory → `git diff resources/content/mirror-lines.edn` → `bb demo-deploy` → open the URL,
   PIN, click Flora, she says a new line. ~3 min, silent. Evidence for "traceably produced".
2. **Promo video** (the instructor: a trailer "drives 5× more users"): 45–60 s, music, no
   terminal. Estate fades up, Hal walks to you, a "!" goes up, the hardline rings and the world
   freezes, Flora's jar, name + URL. Shot list: `docs/demo-script.md`. Hal shoots the camera
   moves headless (CDP + screen capture) and cuts with ffmpeg; Michael picks music and records the
   live click-in. Goes at the top of the README above the playable link.

## Deliverable 2: pipeline source & engine integration

- **Pipeline repository (filled):** https://github.com/xicubed/prime-meridian — the pipeline is
  `content/` (`pipeline.clj`, `STYLE.md`, `requests/`, `runs/`, `pipeline_test.clj`); the crew that
  builds the game is `.crew/` + `crew/` on [crew-up](https://github.com/xicubed/crew-up).
- **Pipeline run video — KNOWN GAP.** A screen recording of `bb content <request>` from the terminal
  through to the line appearing in the game: the trajectory printing, `resources/content/*.edn`
  changing, shadow-cljs recompiling, reload, Hal says the new line. 2–4 minutes, no narration needed
  (Michael's call). Host on YouTube unlisted or in the repo release. **Do this run with cost logging
  on, so the same run is the cost-analysis evidence.**
- **Target engine (filled):** WebGL — three.js in the browser via ClojureScript (shadow-cljs). No
  Unity/Unreal/Godot; the "engine" is the game's own runtime.
- **Automated flow (filled, to tighten):** `bb content hal-lines` → Generator/Evaluator/Refiner loop
  → a draft that clears the threshold is written to `resources/content/hal-lines.edn` (EDN, the
  game's native data format) → `pm.content` inlines it at compile time (`rc/inline`) and `hal-say`
  picks a variant by event → the line shows in Hal's bubble, caption and voice. No reformatting, no
  copy-paste, no import step; the one remaining step is the compile, which the dev watch does on
  save and the release build does in one command. Same path for `mirror-lines.edn` (Flora's and
  Juniper's lines and signs) and `sample-inbox.edn` (the Clerk's mailbox).

## Deliverable 3: pipeline audit & cost analysis

### What the pipeline produced (filled; verify against the playable build)

In the playable build, traceable to a committed run directory:

- **Hal's voice lines**, 21 events × 3–5 variants (`resources/content/hal-lines.edn`, run
  `content/runs/hal-lines-20261121-2011/`, score 8.3 on draft 3): jack-in, coming back, the three
  jack-outs, chores done/not due/undone, programs, next small step, coach habit, morning card,
  blueprints, stalled agent, last call, closing time, stay late, memorial, weekly card.
- **Mirror lines** for Flora (axolotl) and Juniper (horse): hungry/fed/sign lines keyed to their
  real chores, plus memorial and virtual modes (`mirror-lines.edn`, Nov 6 runs).
- **The sample inbox**: the Clerk's mailbox of pretend messages incl. a suspicious one with the
  Clerk's note (`sample-inbox.edn`, Nov 6 runs).
- **Not from this pipeline, say so:** the world (purchased kit), the crew's code (built by the agent
  crew, a different pipeline — Assignment 3), walker figures and Hal's model (hand/crew-built),
  the brook audio (synth + file).

### Manual steps that remain (filled)

1. **Rebuild** after a run (`shadow-cljs` compile; automatic under the dev watch, one command for
   release). *The one documented step for the rubric.*
2. **Human approval before a real run** — a run costs tokens, so Hal/Michael decide when to run;
   the loop itself is hands-off from draft 1 to install.
3. **Request edits when the game grows an event**: `:close-up` was hand-added to the generated file
   on Nov 7 and dropped by the Nov 21 regeneration until the request learned it. Fix in place now
   (request owns the events), but the *detection* is a failing game test, i.e. after the fact.
4. **Commit/push** of the regenerated EDN (Hal does git; the crew never commits).
5. **The deploy** (`bb demo-build` + upload) is manual.

### What it would take to eliminate them (filled)

- (1) a file watcher or a `bb content --rebuild` flag that runs the release build; trivial.
- (2) a budget guard instead of a human: run when `juice` (the usage meter) shows headroom and the
  request's hash changed; the cap is the approval.
- (3) derive the request's event list from the game's `hal-events` table at run time (one source of
  truth) so a new event can't be missed.
- (4) a CI job: on request change → run → gate → commit → deploy. Needs a non-interactive Claude
  login in CI (API key), which changes the cost model (below).
- (5) the same CI job's last step.

### Architectural reflection (filled; Michael to confirm)

**Decision to change:** shipping content as a *compile-time inlined EDN* (`rc/inline`). It made the
game self-contained and test-friendly, but it welds content to the build: every regenerated line
needs a compile, and the playable build can't pick up new content without a redeploy.

**Specific alternative:** load `resources/content/*.edn` at runtime (`fetch` same-origin, cached in
localStorage with the file's `:as-of`, same as the twin's `mirrors.edn` already is) with the
compiled table as fallback. The pipeline then ends in "write the file", the game picks it up on
next load, and the deploy is a file copy. (We built the runtime pattern for twin state; extending
it to content is a day.)

*Alternative candidate if preferred:* the crew's shared-checkout "one world" (every agent edits one
tree, Hal gates) vs per-agent worktrees + merge; the GDD chose shared, which cost us concurrent
edits of the same files twice.

### Cost analysis — KNOWN GAP (needs the instrumented run)

- **Total actual run cost:** `$___` — `call-model` already receives `total_cost_usd` and
  `input_tokens`/`output_tokens` from `claude -p --output-format json`; add them to each draft's log
  line and to `scores.edn`, then the video run gives the figure. *Note for the write-up:* we run on a
  Claude subscription, so the dollar figure is Anthropic's modelled API-equivalent cost, which is
  the honest comparable; say so.
- **Most expensive step:** almost certainly the **Evaluator** (Opus, and it reads ~35 KB of GDD +
  STYLE.md + draft every draft, 3–4 times per run), followed by the Refiner (also Opus since
  `e9ea7b9`). The Generator (Sonnet, once) is the cheapest. Confirm with the numbers.
- **Solo/small-team sustainability:** a run is 3–4 drafts × (Evaluator + Refiner) + 1 Generator ≈
  7–9 Opus/Sonnet calls on ~35 KB prompts. Ballpark a few dollars per table per run at API prices,
  three tables, run when content changes (weekly at most) → tens of dollars a month, or $0
  marginal on a subscription. Sustainable; the expensive part is the human reading the logs, not
  the tokens.

### Mid-project cost-reduction change — KNOWN GAP (pick one, measure both sides)

Candidates we actually did, with the logs to prove it:

1. **Validator before the model critic.** Since Assignment 4 the deterministic validator runs first
   and a draft with shape/guardrail errors never reaches the critic. Before/after measurable from the
   Nov 6 run logs (`content/runs/*-20261106-*`: drafts that failed validation and didn't spend a
   critic call) vs a counterfactual of critic-every-draft.
2. **Context by section, not the whole GDD.** The request names GDD sections; `build-context` sends
   only those (~15–25 KB) instead of the full GDD (~120 KB). Before/after is a token count we can
   compute exactly from the files.
3. **Refiner edits only what scored down** (Assignment 5) vs the Assignment 4 writer re-drafting the
   whole table from blockers: fewer drafts to pass (3 vs the Nov 6 runs' 3–5) — measurable in
   drafts × tokens from the logs.

**Lean: (2)** — it's the cleanest before/after (exact byte and token counts, no counterfactual) and
it's genuinely the biggest saving.

## What we know we need to know (the to-do list)

- [x] Host: pm.perstitio.us, PIN-locked, `bb demo-deploy` (Nov 21).
- [ ] Time the 2-minute rule on a cold cache; trim the demo build if it's close.
- [ ] README top block: trailer, link, PIN note, 3-line how-to.
- [ ] Promo video (45–60 s) — see "Two videos".
- [ ] (Later, before a public release) kit license check.
- [ ] README: playable link + 3-line instructions at the very top; the ~1-page audit under it.
- [ ] Pipeline: log `total_cost_usd` + tokens per draft; totals in `log.md` and `scores.edn`.
- [ ] One instrumented real run on screen → the video and the cost figure (`mirror-lines` is a good
      candidate: it hasn't been through the scored loop, so the run is useful too).
- [ ] Compute the before/after token counts for the context-by-section change.
- [ ] Write the architectural reflection in Michael's words (confirm the choice above).
- [ ] Decide: video narrated or silent; where hosted.
- [ ] Session notes + blog post (optional, tradition).
