# builder-notes: win the day v0

What Hal clicks, in the dev build (`npx shadow-cljs watch app`, http://localhost:8080):

1. `/?fresh` → jack in → play the Construct to the end (through the fifth beat,
   free your mind). The Today card appears top-left with **Finish the
   tutorial** ticked; collapse it (its **Hide** button) and it reads
   "Today 1/3".
2. Build: ask the twin in 💭 (type or 🎙 talk) for something, or click a
   pending ghost (the bench) → **Make it real** (needs `bb crew-bridge` in
   the dev build) → goal 2 ticks.
3. Click the phone booth → the jacked-out card → **Jack back in** → goal 3
   ticks → the **Day won** card appears (title, the twin's line, **Keep
   playing**), the same line in the caption and in 💭 → click **Keep
   playing** → the Today card collapses and reads "Day won ✓".
4. Reload the page: no second Day won card shows, and the pill still reads
   "Day won ✓" (it's saved in `localStorage` under `pm.today.v1`; `?fresh`
   clears it and lets the Construct play again).
5. Faking a real build with no bridge (devtools console, once past the
   Construct):
   ```js
   cljs.core.swap_BANG_(pm.core._BANG_world, (w) => pm.blueprint.make_real(pm.blueprint.approve_blueprint(w, cljs.core.keyword("bench-by-mailbox")), cljs.core.keyword("bench-by-mailbox")))
   ```
   The `!world` watch sees the status turn `:real` and ticks goal 2 the same
   way a bridge-backed "Make it real" would.
6. Checking the Today card doesn't overlap anything: at both 1280×800 and
   1440×900, compare `document.getElementById('pm-today').getBoundingClientRect()`
   against the 💭 panel's (`document.getElementById('talk-panel')`, or its
   toggle) and the kit's top-right pills (the camera-row / time-of-day
   buttons) — none of their rects should intersect. With 💭 open, the Today
   card is always the collapsed pill (per `pm.today/layout`), so it can't
   collide with the panel while it's up.

Gate: `.crew/gate.sh` → `GATE: PASS — 0 warnings; Ran 1501 tests containing
8199 assertions.`
