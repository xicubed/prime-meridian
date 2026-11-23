# Quest: calls, in the room (TASKS T-11)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 21: "because I can talk to you and there are
bots that can speak." A 📞 on a contact in the 💭 phone opens a *call*: that contact's lines are
spoken aloud as they arrive, and the player's own voice goes in through the browser's speech
recognition as if typed. First for the twin ("Do you read me, Hal?" made literal), then any crew
member or the creatures. **Led by:** Hal. Michael reviews; this round's footage also goes in the
A7 videos (scene M6: "the agent can reach you where you are").

## What exists (read first)

- `src/pm/ui.cljs`: the 💭 phone (`render-roster!`, `contact-row!`, `render-panel!`,
  `set-thread!`, `thread`), the voice (`speak!`, `stop-speech!`, `set-voice-button!`), the
  caption (`show-caption!`). The twin's lines are already spoken when voice is on (`pm.voice`
  storage key, `pm.core/say-entry!`).
- `src/pm/core.cljs`: `on-say` (typed text → `talk/crew-say` → `post-crew!` to the bridge, or
  `send-answer!` when that member has a pending question); `say-entry!`; the per-frame tick.
- `src/pm/talk.cljs` (pure, tested): the one talk log, entries by contact (`entry-contact` is in
  `pm.ui`), unread counts, `caption`. Every line any contact says is an entry here.
- `src/pm/bricks.cljs` / `pm.scene/plate`: a walker's speech plate and nameplate (one plate style
  for every in-world label; change them together, never a new style).
- The demo build (`pm.core/DEMO`): no bridge; the twin speaks a SAMPLE line every 45 s
  (`!sample-say`), so a call must work with nothing real behind it.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services; no whole-disk `find`; scratch files only in your own scratchpad.** No npm deps.

## Decided (don't reopen)

- **A call is a mode on one thread**, not a new surface (his rule: the phone is a phone). State
  lives in the world atom: `{:call {:with <member> :since <ms>}}` or no `:call`. One call at a
  time; opening another ends the first.
- **📞 is a labelled button** on the thread header next to `‹ Contacts` (44×40 px, 8 px gap,
  the shared corner-badge rule; read its `getBoundingClientRect` open and closed, same numbers).
  During a call it reads **End** (not an icon swap only). Contacts with no thread (`:all`) have
  no 📞. A creature's thread has a 📞 too: its lines are spoken, there is no composer and so no
  mic.
- **Spoken lines:** while a call with X is on, every new talk entry *from X* is spoken with X's
  voice (`speak!` with that walker's `:voice`, falling back to the twin's), whether or not the
  global voice switch is on; the caption still shows. Lines from anyone else are not spoken by the
  call (the global voice rule covers the twin as today). The pure part, "which new entries does
  this call speak", is a function in `pm.talk` (entries since `n` by contact), tested.
- **The player's voice:** the mic button on the composer (**🎙 Talk**, same size rule) starts
  `webkitSpeechRecognition`/`SpeechRecognition` (`continuous false`, `interimResults true`,
  `lang` from the twin's voice); the interim text shows in the composer as it comes; the final
  result is sent exactly as if typed (through `on-say`: a pending question gets answered, else
  it is a say). No server, no cost. The button shows **listening…** while active and goes back
  on `end`/`error`. The browser's permission prompt is the browser's; a refusal or an
  unsupported browser disables the button with a title saying why and the twin says one line
  ("No mic here. Type to me."), from `resources/content/hal-lines.edn` if an event fits, else the
  one hard-coded fallback with a `;; TODO content` comment.
- **The walker shows it:** while on a call, X's nameplate reads `<name> · on a call` through the
  existing plate (data in the walker row, rendered by the one plate fn). Ending the call restores
  it. The crew key row and the contact row show a small 📞 glyph after the name while on a call.
- **Ending:** End button, closing the 💭 panel, or switching thread. Ending stops speech
  (`stop-speech!`) and recognition. A page reload ends it (no persistence).
- **Everyone's game:** nothing names a specific person, pet or member; the twin is
  `world/twin-walker`. No `::k` keys across namespaces (write `:pm.ui/…` in full).
- **Sound rules:** nothing speaks before the player's first click anywhere (the existing gesture
  gate); a headless test must not turn voice on.

## Out of scope

Twilio / real phones (T-12); recording the call; a call to `:all`; changing the SAMPLE say
cadence; any new panel.

## Done when

Gate passes with tests for: the call state (start, end, switch replaces); "entries to speak"
since `n` by contact (ignores other contacts and older entries); the recognised-text path
reuses `on-say`'s decision (a pending question is answered, otherwise a say), tested through
the pure decision fn; the plate text with and without a call. In the dev build: click Hal →
📞 → `bb hal-say "Do you read me?"` → spoken aloud with the plate reading `Hal · on a call`;
🎙 Talk → speak → the words appear in Hal's thread as yours. In a release demo build
(`bb demo-build` is Hal's to run; the builder only checks `DEMO` paths compile): the SAMPLE line
is spoken during a call. A short note of what was built, what was tested by hand, and what the
reviewer should click goes in the run directory.
