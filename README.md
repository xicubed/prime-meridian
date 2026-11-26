# Prime Meridian

> What is even real anymore? Gamifying the agents — for all and none.

## ▶ Play it

**https://pm.perstitio.us** — runs in the browser, nothing to install, no account. It asks for a
username (`guest33`) and a password (in the course submission; ask Michael). The world takes
about a minute to load the first time.

**How to play, start to win: [PLAY.md](PLAY.md).**

Assignment 7 write-up: [docs/assignment-7-submission.md](docs/assignment-7-submission.md)

A cozy 3D management game where the characters are real AI agents doing real work. Rooms are projects. Talk to the agents, possess one to steer, and watch the world change as they build it.

- **Design**: [docs/GDD.md](docs/GDD.md)
- **Stack**: ClojureScript (shadow-cljs) + three.js, Babashka bridge, Hermes agents. No TypeScript.
- **Agent crew** (Assignment 3): [crew/README.md](crew/README.md): five AI agents that build the game, with a Mermaid diagram of roles and data flow. The crew runs on [crew-up](https://github.com/xicubed/crew-up), an MIT-licensed plugin you can use in your own projects.
- **Status**: the purchased island kit is the world (loaded locally, not in this repo); Flora's feeding loop and stand-ins are built by the crew.

## Pipeline audit (Assignment 7)

**What the pipeline produces.** Everything the characters say: Hal's lines (21 events × 3–5
variants), Flora's and Juniper's lines and signs, and the Clerk's sample inbox. A run
(`bb content <table>`) has a Generator (Sonnet) draft the whole table from a request file and the
design doc's named sections, a free validator check its shape, an Evaluator (Opus) score it 0–10
against [`content/STYLE.md`](content/STYLE.md), and a Refiner rewrite only what scored down, until
a draft clears 8.0. The winner is written straight to `resources/content/*.edn`, the game's own
data format, and the game compiles it in. Every run is committed with its drafts, scores and costs
in [`content/runs/`](content/runs/).

**Manual steps left.** (1) Publishing: a person runs `bb demo-deploy`. (2) Deciding when to
spend on a run. (3) Reviewing and committing the new table. (4) Keeping the request's event
list in step with the game. **To remove them:** a CI job (request changed → run → game tests →
commit → deploy) with a usage-meter budget guard in place of a person, and the event list read
from the game's own table. It needs an API key in CI instead of our subscription.

**One architectural change.** We compile generated content into the build, so every new line
needs a rebuild and redeploy. Instead: fetch `resources/content/*.edn` at runtime with the
compiled table as the fallback (the pattern the game already uses for the twin's real-world
state). The pipeline would end at "write the file".

**Cost.** The recorded run (`mirror-lines-20261122-1241`, in the video) cost **$1.30** over 4
calls. The **Evaluator** is the dearest step at $0.74, then the Refiner at $0.37 (both Opus) and
the Generator at $0.19 (Sonnet). That's sustainable for one developer: about a dollar or two per
table, run only when content changes (under $25/month at API prices for weekly refreshes of
three tables), and $0 marginal on the flat subscription we actually use.

**Mid-project cost cut.** *Before:* to place a build "here", the twin read a screenshot of the
game, ~1,850 input tokens per look, and still guessed. *After:* the game sends its camera and
the ground point at the screen's centre as text (`bb where`), ~120 tokens, about 15× less, with
exact coordinates. These two token counts are calculated, not logged: the image by Anthropic's
size formula (width × height ÷ 750), the readout by its length (~4 characters a token). The $1.30
is logged per call, as API-equivalent cost; on our flat subscription the extra cost was $0.
Details: [docs/assignment-7-submission.md](docs/assignment-7-submission.md).

## Make it yours: your own agents, your own world

The hosted game at pm.perstitio.us is the test drive: everything there is SAMPLE, and only a
pretend twin answers. Swapping in your own parts takes more setup the further down this list you go:

| | What you do | Who answers you | What you install | Status |
|---|---|---|---|---|
| **1. Your key, in the browser** | Paste an AI API key into the hosted game; it stays in your browser | Your twin, played by that model with the game's own prompt (it can place builds). The crew stays SAMPLE | Nothing | Planned, after the course |
| **2. Your agent, the game on your machine** | Clone this repo, run the game and `bb crew-bridge` | Your own agent as your twin, in its own chat and in-world (`bb hal-say`) | This repo, Node, a JDK, Babashka | Works today with Hermes (`scripts/hal_relay.py`); another agent needs its own relay and needs to know the game's protocol (`[from the game]` turns in, `bb hal-say` out) |
| **3. Your agent, the hosted game** | Run only the bridge on your machine and pair it with the hosted page | Your agent, as in 2 | The bridge script and Babashka | Planned: the hosted page calls your bridge on `127.0.0.1` |
| **4. The whole crew** | Run Herdr and crew-up beside the game | Your twin plus a crew of coding agents that build the game with you ([crew/README.md](crew/README.md)) | As in 2, plus the list below | Works today: this is how the game is built |

What to clone:

- **The game and your twin (2):** this repo only. The bridge, `hal-say` and the prompt fielder
  are Babashka scripts here.
- **The crew (4):** also [crew-up](https://github.com/xicubed/crew-up), plus Herdr ≥ 0.9,
  Claude Code (logged in) and `jq`. If your manager runs in a Claude Code pane, install crew-up
  as a plugin, with no clone (`claude plugin marketplace add xicubed/crew-up`, then `claude plugin
  install crew-up@crew-up`, then `/crew-up`). If your manager drives Herdr from outside (as Hermes
  does here), clone crew-up to `~/src/crew-up` and run its `bin/crew-up`.

**The world is not in this repo.** The estate (the island, the cottage, the creek) is a licensed
scene kit, [Hiroshi's Island](https://3ditions.drop3.app/watch/hiroshis-island/) by 3DITIONS. Its
licence covers one compiled product and forbids handing out the pack. pm.perstitio.us serves it
only compiled into our demo, behind a login, and a clone can't load it from there. A clone
without the kit runs on a placeholder scene. To get the world:

- **The same island:** buy the kit and copy its built player into `public/kit/` (gitignored):
  `rsync -a --delete <kit>/dist/ public/kit/`.
- **A world of your own** (the Old West, a space station): the game talks to a world through a
  small handle, and everything else is data. The world is a three.js build in `public/kit/` that
  sets `window.hiroshi` to `{scene, camera, renderer, THREE, rig}`. The rig has
  `setHour("day"|"golden"|"blue"|"night")` for time of day, plus an orbit camera. Then
  `resources/estate.edn` describes it: the meshes you can stand on (`:motion :ground`), the
  materials our props borrow (`:kit-materials`), the sky schedule (`:sky`), and the landmarks and
  walkers' spots. The rules, the crew, the mirrors and your twin carry over unchanged.
  Honest caveat: the handle is still named after the island kit, and the code assumes its four
  sky states. Making the world a clean plug-in (a documented interface plus a second sample world
  on the same path) is planned work.

## Run

```bash
npm install
npx shadow-cljs watch app     # http://localhost:8080
```

The game also reads a twin's state file at `state/mirrors.edn` (served as
`/state/mirrors.edn`, same-origin only, by `pm.dev-http`; it lives outside `public/` because
shadow-cljs lets any origin read what it serves from there). That file is private, gitignored, and never committed; without it,
every mirror just runs on its sample state. To try it: `mkdir -p state`, then save
an example like Flora's there, and the HUD line changes from `Flora (sample) · stock 15`
to `Flora (Hal) · stock 37`. The game re-reads the file every 5 s and applies it whenever
its `:as-of` changes, and it can only update fields a mirror already has — it can't add
new ones. The game also remembers the last state it got (plus in-game changes since) in
the browser's `localStorage` under `pm.state.v1`, so if the file is missing on the next
start the HUD shows that state as `Flora (Hal, cached)` until the file is back; a newer
file always wins. The game reads Hal's lines, the creatures' lines and the sample inbox
from `resources/content/*.edn`, generated by `bb content`; never hand-edited. Captions show
what Hal and the creatures say; 🔊 reads them aloud with the browser's speech; 💭 scrolls
back (kept in `localStorage` under `pm.talk.v1`, last 100 lines). Click a creature to do
its chore; in 💭, untick a chore to undo it. The F/G keys are gone. A creature's
request in 💭 is its todo; ticked when the chore's done, untick to undo, tick to
do it again.

Built by Michael Lopez with Hal Niner (bot). Course project, fall 2026.

Credits and third-party licenses (the kit, NVIDIA's MotionBricks model, ONNX Runtime, MuJoCo,
music, characters): [public/CREDITS.md](public/CREDITS.md); the license texts are in
[public/licenses/](public/licenses/) and are served with the game.
