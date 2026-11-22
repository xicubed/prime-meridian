# Prime Meridian — the game page (draft)

*Draft Nov 21 for the public page at lotect.com/prime-meridian and the README's top block. Audience:
Michael, the graders, classmates. Everything in brackets is an asset still to make.*

---

[POSTER — header image, 1920×1080; square 1080×1080 for thumbnails]

# Prime Meridian

*What is even real anymore?*

[TRAILER — 45–60 s, embedded; unlisted YouTube or mp4]

**[▶ Play in the browser](https://pm.perstitio.us)** — desktop Chrome or Edge, no account. The build
is behind a PIN while the world's assets are licensed for private use; ask Michael for it. Click once
when it opens: browsers need a tap before the brook can play.

## A cozy estate run by your AI agents

Prime Meridian is a small 3D estate in your browser where the characters are real AI agents doing
real work. Each worker on the lawn is a live coding session; the axolotl in the creek is a real pet
with a real feeding day; the phone booth rings when real life needs you. Your digital twin — Hal, for
the author — walks over with the morning card, raises a "!" when someone needs a word, and shows you
every change as a blueprint before it's real.

The game's one rule: it sends you back to life. A day is one round; it closes at your bedtime; a
missed day leaves the estate a little overgrown and never ruined. No points, no streaks, nothing to
shame you. The long-term win is needing the game less.

Everything starts as a **SAMPLE** — a pretend inbox, a pretend crew — and becomes **REAL** when you
plug your own agent in. The demo plays entirely on samples, so a stranger can walk the estate in
two minutes with nothing to set up.

## What to do in the first two minutes

1. **Jack in.** The Construct meets you first; load the programs, the estate fades up.
2. **Find the "!".** A crew member needs an answer. Click them — in the world or in the CREW tab on
   the right.
3. **Approve something.** A blueprint shows *now → proposed*; approve it and the world changes.
4. **Talk to Hal.** Click him, type in the 💭 box. In the demo he tells you he's only reachable from
   a real game — which is the point.
5. **Click Flora.** Her chore isn't due; Hal tells you when it is.

## Screenshots

[SHOT 1 — the estate at golden hour, crew wandering, one "!" up]
[SHOT 2 — the 💭 phone open on a thread, unread badges on the roster]
[SHOT 3 — the hardline ringing: the world frozen, "Someone needs you" on the plate]

## How it's made

The whole thing is built *by* the agents it depicts. A crew of six AI agents (French-named after
Monet's circle — Un Architecte, Le Testeur, Le Critique, Le Docteur, Le Coureur, Un Automate)
builds the game from quests, with Hal as their manager and Michael approving. Hal's voice lines are
written by a scored content loop that grades every draft against the game's own design document and
rewrites only what fell short — no human edits a line. The code, the crew's logs, and every content
run are public:

- Repo: https://github.com/xicubed/prime-meridian
- The design document: `docs/GDD.md`
- The crew: `crew/README.md` · the content loop: `content/`
- The build journal: lotect.com/category/prime-meridian

ClojureScript + three.js. World: [Hiroshi's Island](https://3ditions.drop3.app/watch/hiroshis-island/)
by 3DITIONS, used under license. Built in the ELVTR AI game-development course, fall 2026.

---

## README top block (markdown)

```markdown
<p align="center"><img src="docs/poster.jpg" alt="Prime Meridian" width="800"></p>

# Prime Meridian

> What is even real anymore?

**[▶ Play in the browser](https://pm.perstitio.us)** · [watch the trailer](TRAILER-URL) · [game page](https://lotect.com/prime-meridian)

Desktop Chrome/Edge, no account. The demo is PIN-locked while the world's assets are licensed for
private use (PIN in the course submission / ask Michael). Click once when it opens for sound.
First two minutes: jack in → find the "!" → approve the blueprint → click Hal and type → click Flora.
```
