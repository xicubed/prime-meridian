# Quest: win the day (final project, due Dec 6)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 25. The final project is graded on
playability first, and its video must run "start to win". Today nothing tells a player what to do
or that they won. Michael's win: **do the tutorial, build something by talking to your twin (chat
or voice), and jack out and back in.** **Led by:** Hal. Michael reviews.

## The moment

After the tutorial (the Construct) a small **Today** card sits in the top-left corner with three
lines, each with a read-only checkbox:

1. **Finish the tutorial** (ticked the moment the Construct's last beat ends; already ticked for a
   returning player who has seen it).
2. **Build something with your twin**: ask the twin in 💭 (type or 🎙 talk) for a thing, try it,
   make it real. Ticked when any blueprint becomes **real** after the tutorial ended (the
   tutorial's rock doesn't count, nor do builds that were already real at page load).
3. **Jack out and back in**: ticked when the player jacks out at the phone booth and jacks back
   in (both, in that order, after the tutorial; the tutorial's practice jump doesn't count).

When all three are ticked, a **Day won** card appears in the middle of the screen: a title ("Day
won"), one line from the twin, and one labelled button, **Keep playing**, that closes it. The
Today card then reads "Day won ✓" and stays collapsed. The win shows once per day per browser.

## What exists (read first)

- `src/pm/construct.cljs` (pure; beats 1-5; `free-your-mind` is the last) and its wiring in
  `src/pm/core.cljs` (`!construct-seen?`, localStorage `pm.construct.v1` = "1" once seen).
- Jacking out/in: `pm.world` (`:jack`, `jacked-out?`, `jack-in`), `toggle-jack-out!` and the
  booth's jack-out card (`ui/show-jack-out!`) in `pm.core`.
- Builds: `pm.blueprint` (statuses `:pending :approved :real :removing`, `make-real`,
  `apply-real-builds`), the trial card (Make it real / Undo), `state/builds.edn` via the bridge.
  In the demo build (`pm.core/DEMO`) make-real is refused without a bridge today; a separate quest
  (next round) gives the demo its own build path, so this quest must not depend on how a build
  becomes real, only that its status turns `:real`.
- The twin's lines: `resources/content/hal-lines.edn` via `pm.content` (`c/line`); a new event
  needs a line there or in the estate `:texts`, with a hard-coded fallback marked `;; TODO content`.
- UI: `src/pm/ui.cljs`; `pm.scene/plate` style for in-world labels; the fixed-control rules and
  existing fixed panels (💭 phone bottom-left, ⓘ, the crew drawer on the right edge, the kit's
  camera row and time-of-day pills top-right). The top-left corner is free.
- Storage keys in use: `pm.construct.v1`, `pm.talk.v1`, `pm.crew-sample.v1`, `pm.crew-key.v1`,
  `pm.sources.v1`; `?fresh` clears saved play, `?construct` replays the tutorial.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. Never send keys or prompts to crew members; don't start
  services; no whole-disk `find`; scratch only in your scratchpad; never write `state/` from tests.
  No npm deps.

## Decided (don't reopen)

- **Pure logic in a new `src/pm/today.cljs`** (no atoms, no `js/Date`; `now` passed in): the
  goals' state `{:day "YYYY-MM-DD" :tutorial? :built? :out? :back? :won-at}`, a fn per event
  (`tutorial-done`, `build-real`, `jacked-out`, `jacked-in`) that returns the new state, `won?`,
  `rows` (the three lines with ticked flags, for the UI), and the day rollover (a new local day
  starts a fresh state; the tutorial goal stays ticked because the tutorial is done once). Goal
  texts are data (estate `:today` block: the three row texts, the card title, the win title,
  the button label), not hard-coded strings.
- **Persisted** in localStorage `pm.today.v1` (pr-str of the state); `?fresh` clears it.
- **Wiring in `pm.core`:** call the pure fns from the places that already know: the end of the
  Construct, a blueprint turning `:real` (any path: the trial card's Make it real now, the demo's
  path later), jack-out and jack-in at the booth. Builds already real at load and the tutorial's
  rock don't tick goal 2.
- **UI in `pm.ui`:** the Today card top-left: title "Today", three rows with read-only checkboxes
  (disabled inputs, so a click can't fake them) and the row text; a collapse toggle (labelled
  button) that shrinks it to a pill "Today 2/3"; open by default until won, collapsed after. It
  must not overlap the kit's top-right pills, the caption at top centre, or the 💭 panel when open
  (bottom-left, up to near the top: the card sits above or beside it; measure both). Hidden during
  the tutorial's white room. The Day won card: centred, the game's existing card style, title,
  the twin's line (new `hal-lines` event `:day-won`, a fallback line if absent), and the **Keep
  playing** button; it's also said in the caption and 💭 like the twin's other lines.
- **Works in the demo build and the dev build alike**; no bridge needed for any of it.
- **Everyone's game:** no person, pet or member named in code.

## Out of scope

How the demo builds things from a chat request (next quest); losing (there is no game over);
streaks, scores, weekly cards; changing the tutorial.

## Done when

Gate passes with tests for `pm.today`: each goal ticking from its event; order matters for out →
in; the tutorial's practice jump and rock not counting (the wiring passes a flag the tests can
check); `won?` only with all three; the win recorded once; day rollover (fresh goals, tutorial
kept); `rows` texts from data. The builder leaves `{run}/builder-notes.md` with what Hal clicks to
see each tick and the win, in the dev build (`?fresh` then play) and how to fake a real build
headless if needed.
