# Quest: the creature's request is the todo (checkbox on "Juniper · Feels like hay time")

**Game:** Prime Meridian. **Asked by:** Michael: "in the chat have a checkbox for like the
8:03 PM [x] Juniper Feels like hay time, that way you can see he was fed. And if I want to feed
him again I uncheck it and check it again." **Led by:** Hal. Follows round `click-chores`.

## The moment we're building

In the 💭 history, a creature's line that asks for a chore (its bubble came from a `:due?`
rule, e.g. Juniper's "Feels like hay time", Flora's "I'm hungry") carries a real checkbox:

- **Unticked** while the chore is still owed. Ticking it does the chore right there, the same
  as clicking the creature in the scene (`:via :chat`), and Hal says his `:chore-done` line.
- **Ticked** once a chore that answers it counted (by click or by the box), with the time:
  `8:03 PM ☑ Juniper · Feels like hay time · fed 8:16 PM`.
- **Unticking** undoes that chore exactly as `click-chores` built (done-time and stock back,
  bubble back if due, Hal's `:chore-undone` line). **Ticking again** redoes it. So "feed him
  again" is untick + tick.
- The separate "☑ Juniper's hay" chore line from `click-chores` goes away: the request line is
  the one todo. A chore done with no request in the history (nothing had asked yet) still shows
  as its own ticked line, so every chore has a box.
- Old lines with no chore behind them (Hal's lines, a sign) have no box.

## What exists (read first)

`click-chores` (run under `crew/runs/click-chores-*`): picking, `chore-at-click`, undo/redo,
the talk log's chore entries and checkbox UI. `src/pm/talk.cljs` (entries, `observe`, which
creature line came from which rule), `src/pm/ui.cljs`, `src/pm/core.cljs`, `src/pm/world.cljs`.
Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`; don't edit
`resources/content/*.edn`.

## Done when

The gate passes with tests for: a request line gets a box; the chore that counts after it ticks
it; ticking an open request does the chore; untick undoes; tick again redoes; old histories
restore (a request entry without the new fields shows a box from the current state, or none).
Live: feed Juniper by clicking him → his "hay time" line shows ☑ with the time; untick → his
bubble comes back; tick → fed again.
