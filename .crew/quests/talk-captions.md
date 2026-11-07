# Quest: Hal you can hear and read (captions, voice, and a thought bubble to scroll back)

**Game:** Prime Meridian. **Asked by:** Michael: "it's hard to read, that's for
sure. Maybe you can speak the lines with captions and a thought bubble to click
on to scroll back through the chats." **Led by:** Hal.

## The moment we're building

Today Hal's line is a few words inside the tiny status line at the top, gone
after 6 s. After this round:

1. **Captions.** Whenever someone speaks (Hal's `:jack-in` / `:chore-done`
   lines, and a creature's bubble when its text changes, e.g. Flora's "I'm
   hungry"), a caption appears near the bottom centre of the screen in large,
   readable type: the speaker's name, then the line ("Hal — You're jacked in.
   Let's get going."). It stays a few seconds (longer for longer lines), then
   fades. The status line no longer carries Hal's text.
2. **Voice.** A 🔊 button next to the brook control. Off by default (browsers
   only allow speech after a click); the first click turns it on and says a
   short line. While on, every new caption is also spoken with the browser's
   built-in speech (`window.speechSynthesis`, no new dependencies). Hal and the
   creatures get different voices or at least different pitch/rate, set as
   data. Clicking again turns it off and stops any speech. Without
   `speechSynthesis` the button hides itself.
3. **The thought bubble.** A 💭 button (bottom left) with a small count of
   lines said since it was last opened. Clicking it opens a panel listing the
   history, oldest at the top, newest at the bottom, scrolled to the bottom,
   each entry with the time (`5:42 PM`), speaker and text; it scrolls when
   long. Clicking an entry speaks it again (when voice is on). Clicking 💭
   again or pressing Escape closes it. The history survives a reload
   (localStorage, capped, e.g. the last 100 lines), the same way the state
   cache does.

## What exists (read first)

- `src/pm/content.cljs` (pure): `hal-say`, `hal-on-chore`, `hal-text`,
  `hal-segment`; Hal's current line lives at `(:hal world)` = `{:event :text :until}`.
- `src/pm/plan.cljs` / `pm.scene`: creature labels and their text per frame
  (`label-texts`, `label-changes`).
- `src/pm/core.cljs`: the frame loop, `hud!`, `hud-mirrors-suffix`, keys
  (`on-key!`, capture phase on window; must ignore keys typed into inputs and
  must not swallow Escape when the panel is closed), the brook button wiring
  (`wire-kit-brook-ui!`), the localStorage pattern in the state cache.
- `public/index.html`: `#hud`, `#brook` controls. The page has `<base href="/kit/">`.
  The kit's own UI is on screen too (camera buttons along the bottom, a
  studio panel on the right): keep the caption above the bottom button bar and
  the 💭 panel clear of both.
- Gate: `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`.
  Don't edit `resources/content/*.edn` (generated).

## Scope

1. **Pure talk log** (new namespace, tested): append an entry `{:who :text :at
   :kind}` when a new line is said; no duplicate when the same line is still
   showing (a line re-derived every frame must be logged once); creature
   entries only when a mirror's `:say` text changes to a non-empty value; cap
   the length; unread count since last opened; caption duration from text
   length (min/max); serialize/restore with garbage tolerated. Test both
   mirrors and Hal.
2. **DOM layer** (a new UI namespace; plain DOM interop, no npm deps, no
   three.js): the caption, the 🔊 toggle, the 💭 button + panel, speech via
   `speechSynthesis` behind one small function (so it can be skipped in tests
   and when unsupported). Styles live in `index.html`'s `<style>` or are set
   from code, readable over the scene (dark translucent card, ~20-24 px text).
3. Remove Hal's text from the status line (keep `mail …` and the rest).
4. Voices as data: an entry in `resources/estate.edn` (or a small config map)
   for Hal and for creatures: `{:rate :pitch :lang}`; never a mirror name in code.

## Out of scope

Typing back to Hal (the "calculator" panel is a later round), recorded voice
files, lip sync, the creatures' 3D bubbles themselves.

## Done when

The gate passes with the talk-log tests; live (Hal checks): at start a caption
"Hal — <jack-in line>" appears in large text and the status line no longer has
it; 💭 shows a count, opens a scrollable history with times, closes on click /
Escape, and the history is still there after a reload; 🔊 turns speech on and
off (Hal confirms `speechSynthesis.speaking` flips during a line).
