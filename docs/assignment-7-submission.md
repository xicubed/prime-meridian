# Assignment 7: Complete AI Dev Pipeline — Prime Meridian

*Submission text, ready to paste into the form. Michael Lopez, Nov 2026. The long-form working
notes are in [assignment-7-pipeline.md](assignment-7-pipeline.md). The password for the playable
link goes in the submission box only, never in this public repo.*

## Student & game overview

- **Student name:** Mike Lopez
- **Capstone game title:** Prime Meridian
- **Game concept brief:** An agent crew finds itself in a virtual world, there to do your bidding
  and to build the game itself. An in-game pet reminds you to feed her: she's the digital twin of
  your real pet. Whatever you want to build, do or see, it's your game, your life. Your digital
  twin runs the estate and sends you back to real life at closing time: *"I can open the
  browser. Leaving your world is up to you."*

## Deliverable 1: playable link

- **Playable game link:** https://pm.perstitio.us (self-published on AWS Lightsail). Username
  `guest33`; the password is in the submission box. No install, no account: it runs in the
  browser. Click once anywhere for sound.

## Deliverable 2: pipeline source code & engine integration

- **Pipeline repository link:** https://github.com/xicubed/prime-meridian. The content pipeline
  is [`content/`](../content/) (`pipeline.clj`, `STYLE.md`, `requests/`, every run in `runs/`); the
  agent crew that builds the game is [`crew/`](../crew/README.md) + `.crew/`, running on
  [crew-up](https://github.com/xicubed/crew-up).
- **Pipeline run video link:** https://pm.perstitio.us/video/pipeline-run.mp4 (same username and password as the game; 6:39, narrated). A narrated screen recording of one real run
  (`bb content mirror-lines`, Nov 22): the request, the Generator's draft, the free validator,
  the Evaluator's score, the Refiner's edit, the second score clearing the bar, the table
  installed into the game's data file, and the run's cost log.

### Integration breakdown

- **Target game engine:** three.js (WebGL in the browser), driven from ClojureScript (shadow-cljs).
- **Automated flow description:** A run writes its winning draft straight to
  `resources/content/<table>.edn`. EDN is the game's own data format, the same format as the
  estate itself, so there is no conversion step. The game inlines those files at compile time;
  the dev watch rebuilds on save, and `bb demo-deploy` builds and publishes the release in one
  command. In the scene, `hal-say` picks a line for an event (jacking in, a chore done, closing
  time) and shows it in the twin's speech bubble, caption and voice. Flora and Juniper's
  hungry/fed lines and signs, and the Clerk's sample inbox, take the same path. No copy, no paste,
  no reformatting. The one manual step is publishing: a human runs `bb demo-deploy`. The same
  holds for the agent crew's own output: a member's blueprint goes from one command
  (`bb blueprint-draft`) to a ghost in the running game within 5 seconds, with no reload.

## Deliverable 3: pipeline audit & cost analysis

### Pipeline production & functionality

- **What did the pipeline produce?** Everything the characters say in the playable build:
  - **Hal's lines** (`resources/content/hal-lines.edn`, run
    [`hal-lines-20261121-2011`](../content/runs/hal-lines-20261121-2011/log.md), 3 drafts, scored 6.8 → 7.5 → 8.3 against a pass bar of 8):
    21 events × 3–5 variants. Jacking in, coming back, three kinds of jack-out, chores done/not
    due/undone, the next small step, the habit coach, the morning card, blueprints, a stalled
    agent, last call, closing time, staying late, memorial pets, the weekly card.
  - **Flora's and Juniper's lines and signs** (`mirror-lines.edn`, run
    [`mirror-lines-20261122-1241`](../content/runs/mirror-lines-20261122-1241/log.md), the one in
    the video, 2 drafts, scored 6.8 → 8.0): hungry, fed, waiting, plus memorial and virtual modes.
  - **The sample inbox** (`sample-inbox.edn`): the Clerk's pretend messages, including the
    suspicious one with the Clerk's warning.
  - Not from this pipeline: the world (a licensed scene kit), the code (built by the agent crew,
    the second pipeline), the music (made with Suno), and the red phone box (a Blender script).
- **What manual steps remain?**
  1. **Publishing:** a human runs `bb demo-deploy` after a run (the dev build picks it up by
     itself).
  2. **Deciding when to run:** a run costs money, so a person starts it.
  3. **Commit:** the regenerated table is reviewed and committed by Hal (the agents never commit).
  4. **The request file follows the game:** when the game grows a new event, the request must
     list it. Once a hand-added line was dropped by a later run; a failing game test caught it.
- **What would it take to eliminate them?** A CI job on a request change: run, gate (the game's
  tests), commit, deploy. Behind it, a budget guard instead of a person (run only when the usage
  meter shows headroom and the request changed), and the request's event list read from the
  game's own event table so the two can't drift. The one real blocker is CI needing a
  non-interactive model login: an API key instead of our subscription, which changes the cost
  model below from $0 marginal to the dollars in the logs.

### Architectural reflection

- **Current architectural decision to change:** compiling the generated content into the game.
  It keeps the build self-contained and easy to test, but it welds content to code: every new
  line needs a rebuild and a redeploy, so the pipeline can't finish on its own.
- **Specific alternative:** load `resources/content/*.edn` at runtime. The game fetches the file
  from its own site, caches it with its `:as-of` stamp, and falls back to the compiled table if
  the fetch fails. That's the same pattern the game already uses for the twin's real-world state
  (`state/mirrors.edn`) and agent blueprints (`state/blueprints.edn`). The pipeline then ends at
  "write the file"; publishing a new line is a file copy, no build.

### Cost analysis

- **Total actual run cost:** **$1.30** (run `mirror-lines-20261122-1241`, 4 model calls, as
  reported per call by `claude -p` and logged in the run's [`cost.edn`](../content/runs/mirror-lines-20261122-1241/cost.edn)).
- **Most expensive pipeline step:** the **Evaluator** (Claude Opus 5.5): $0.74 of the $1.30 over
  its 2 calls; the single dearest call was the Evaluator scoring draft 2 ($0.38). The Refiner
  (also Opus) was $0.37, the Generator (Sonnet 5, one pass) $0.19.
- **Solo/small-team sustainability:** Yes. A table costs about a dollar or two per run and only
  needs a run when its content changes. Three tables refreshed weekly is under $25 a month at API
  prices, and on a flat subscription (how we ran it) the marginal cost is $0. The real constraint
  is the subscription's usage window, not money. The expensive part is a person reading the
  logs, which is also the part that keeps the lines from being slop.

### Mid-project cost-reduction change

- **Strategy/prompting approach — Before:** To build something where the player was looking
  ("put a bench here"), the twin took a **screenshot** of the game's window and had the model work
  out the spot from the picture. **After:** the game itself sends the twin its camera, the
  ground point at the centre of the screen and every walker's position (`state/view.edn`, read
  with `bb where`), as a few lines of text. The crew's placement command then finds level, open
  ground near that point from a ground map, so no model has to look at a picture at all.
- **Token / API cost — Before:** ~1,850 input tokens per "where is here?" (one 2880×1628
  screenshot, resized to 1568×886 for the model: width × height ÷ 750), and it still left the
  spot a guess. **After:** ~120 tokens (the 480-character `bb where` readout), about **15× less**,
  with exact coordinates. (A second change from the content loop: the Refiner moved to Opus after
  the first scored run, after which Hal's lines cleared the bar in 3 drafts, where the run before had spent all 4 and
  never passed (6.9, 7.9, 7.7, 7.9): prompt input per run 345 KB → 261 KB, about 86k → 65k
  tokens, though each Opus token costs more.)
- **How these numbers were got:** the before/after token counts are calculated, not logged. The
  screenshot's from Anthropic's image formula (width × height ÷ 750 at the size the model sees);
  the readout's and the prompts' from their length (~4 characters a token). Only the $1.30 run
  is logged per call, and that figure is the API-equivalent cost `claude -p` reports; we ran on
  a flat subscription, so the extra money actually spent was $0.
