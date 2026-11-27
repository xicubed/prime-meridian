# Quest: the crew in your phone, mirrored with Herdr (crew-mirror-v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 26, after shooting the bench scene. **Led
by:** Hal.

## The moment

Michael asked Hal in 💭 for a bench. Un Automate (the builder) worked in his Herdr pane: he
explained in prose why the spot failed, asked "try bench behind house instead?", told Michael
"I told him in-world", and stopped on several multi-line permission prompts. **None of that
reached the game.** Michael had to tab to Herdr to learn what was going on, and answer prompts
there. His words:

> It should live in Herdr too, but they should more or less mirror each other (maybe with
> summaries in game). If I talk to him in game I should see it in Herdr and the game, and if I
> see what he's doing in Herdr, the pertinent results should flash back to the game. You (the
> twin) answer him with permissions, since you're smarter than him.

## What exists (read first)

- `scripts/crew_status.clj` (service `bb crew-status`): polls `herdr agent list` every 2 s,
  writes names + states + a parsed blocked prompt to `state/crew.edn`; hashes working screens
  only for `:since` (stall detection). **Never copies screen text** except the parsed prompt.
- `src/pm/prompt.cljc` (`read-prompt`: screen → `{:id :kind :text :risky}`; multi-line and
  bordered commands often come out `:unknown`).
- `scripts/crew_fielder.clj` + `src/pm/fielder.cljc`: answers routine prompts after
  `:crew-talk :answer-after-s`, never risky/unknown; holds while `state/hold.edn` is on.
  `:crew-talk :surface-after-s`: a prompt the twin hasn't answered goes to the player.
- `scripts/crew_bridge.clj` + `src/pm/bridge.cljc`: the game's allow / deny / say POSTs →
  `herdr` (a say is typed into the member's pane: that half of the mirror works today), the
  `/events` socket (`hal-say`, `ask-player`), `relay-to-hal!` (a say to the twin goes into the
  twin's chat through `scripts/hal_relay.py`).
- `scripts/hal_say.clj` (`bb hal-say "line" [--as <member>] [--at <where>]`): **open bug:** a
  `--as <member>` line shows only on that member's speech plate, never in 💭.
- `src/pm/talk.cljs` (the 💭 phone: contacts, per-member threads, `crew-label`, the prompt
  lines with ☐ allow / ✕ / reply; `:unknown` prompts show ⚠ with only ✕/reply today),
  `src/pm/crew_log.cljc` + `state/crew-log.edn` (who answered what, `:by :twin|:player`).
- Conventions: `.crew/CONVENTIONS.md`, gate `.crew/gate.sh`, `AGENTS.md`. Members never
  commit. Never read `assets/private/`, `public/kit/`, `state/` (except to reason about file
  shapes from code). No npm deps.

## Decided (don't reopen)

1. **Herdr stays the source; the game mirrors it.** Nothing is answered or said only in the
   game: every game action already goes to the pane; this round makes the pane's pertinent
   output come back.
2. **The member's own words come back, nothing else.** For Claude members, the assistant's
   prose lines (Claude Code marks them `⏺`; tool calls, tool output, diffs and file contents are
   not prose and never cross). When a member's turn ends (working → idle/done), its **last prose
   block** becomes one line in that member's 💭 thread (`:kind :crew-said`, quiet: no badge),
   trimmed to ~240 characters with "…" (the summary; the full text stays in Herdr). Pure
   extraction fn (screen text → prose lines), tested on real screen fixtures
   (`test/fixtures/` — capture them from a pane, check for paths/PII first). The watcher writes
   these to a new gitignored `state/crew-said.edn` (rolling, last ~20 per member); the game
   polls it like `crew.edn`. Hermes members (no `⏺`): out of scope this round.
3. **A question gets a hand up.** If that last prose block ends in `?` and the member is idle,
   the member's row and walker get the "!" (same as a prompt), and the line reads as a question;
   the player answers with the thread's existing reply box (the say path types it into the pane).
   The "!" clears when the member is working again or the player replies.
4. **Every permission prompt is answerable in the game**, `:unknown` shapes included: the line
   shows the command as read (first ~3 lines, then "…") and **Allow / No** (and **Always** when
   the dialog offers "don't ask again"), sent with the prompt id as today. Risky prompts keep
   the ⚠ and their reason, and still get Allow: it is the player's call.
5. **The twin answers first, not the player** (Michael: the twin is smarter than the members).
   Routine prompts: the fielder, as today. Every prompt the fielder leaves (risky, unknown) is
   **sent to the twin's chat** through the existing relay (`[from the crew] <member> asks to run:
   <command>` plus the risky reason), and the twin answers with a new **`bb crew-answer <member>
   allow|always|deny [--note "…"]`** that goes through the bridge's same prompt-id/hash check
   (stale if the dialog changed). The game shows such a prompt as "<twin> is reviewing" and then
   who answered. The player can still answer first (stale handling as today), and the hold
   switch ("I'll answer") stops both the fielder and the forwarding.
6. **`hal-say --as <member>` lines land in that member's 💭 thread** (fixes the open bug), as
   well as on the speech plate.
7. Everyone's game: no person, pet or member named in code; the twin's name from `:jumeaux`.
   No screen text in `state/crew.edn` beyond what's there today.

## Out of scope

Hermes members' prose; streaming every line while a member works (only end-of-turn summaries);
a full transcript viewer in the game; changing Herdr.

## Done when

Gate passes with tests for: the prose extraction (real fixtures: a turn with tool calls and
prose; a question; a bordered/wrapped prompt on screen), the summary trim, the question "!",
the unknown-prompt Allow line, `crew-answer` validation (allow/always/deny, stale id), the
forwarding decision (fielder leaves it → forward; hold on → nothing), and `--as` lines in the
member's thread. The builder's `{run}/builder-notes.md` says how Hal checks it live: restart the
services, prompt a member with a question, stage an unknown-shape prompt, answer it with `bb
crew-answer`, and what to read in 💭, `state/crew-said.edn` and `crew/bridge.log`.
