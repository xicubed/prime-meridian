# calls-v0: builder's note

**New:** `src/pm/call.cljs` (`pm.call`) — `start-call`/`end-call`/`call-with`,
`end-reason`, `call-button`, `mic-shown?`, `mic-step`/`mic-line?`/`mic-view`,
`say-route`, `mark-on-call`, `glyph-name`, `no-mic-line`/`call-suffix-glyph`.

**Changed:**
- `pm.talk`: `call-line?`, `call-lines`, `call-voice`.
- `pm.crew`: `on-call-suffix`, 3-arity `plate-name` (2-arity untouched).
- `pm.bricks`: 5-arity `set-dots!` (4-arity delegates with `call` nil).
- `pm.ui`: `recognition?`, `listen!`, `stop-listening!`; `render-roster!` is
  now 7-arity (📞 in the thread header, 🎙 on the composer, mic text
  override); `contact-row!`/`render-crew-key!` show the 📞 glyph via
  `call/glyph-name`.
- `pm.core`: `on-say` dispatches on `call/say-route`; new `start-call!`,
  `end-call!`, `on-call-button!`, `on-mic!`, `call-say!`; `talk-tick!` ends
  a stale call, speaks its lines, skips the caption-path double-speak, marks
  the roster `:on-call?`, and passes `phone` to `render-roster!`;
  `crew-key-tick!` marks the key; `frame` passes `:call` to `set-dots!`.
- `public/index.html`: CSS only — 📞/🎙 join the 44×40 button rule, their own
  label styling, `.mic.listening`, `.call.on`, `:disabled`, 8px gaps.

**Gate:** `GATE: PASS — 0 warnings; Ran 1083 tests containing 7186
assertions.` (`pm.calls-test` isn't written yet — tester's file.)

**For the reviewer to click** (Hal's hand checks, §7): open 💭 → Hal → 📞
Call, check the button's box is 44×40 before and after (End, red); `bb
hal-say` while on the call, voice off, caption still shows, plate reads
"Hal · on a call"; 🎙 Talk → speak → interim fills the composer → final
lands as yours; End/‹ Contacts/closing 💭 stops mid-line and drops the 📞
glyphs; call a creature (no composer, no 🎙); block the mic for the page
(🎙 disabled, twin says the no-mic line once); `bb demo-build` and call Hal.
