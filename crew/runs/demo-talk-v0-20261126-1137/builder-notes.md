# Builder notes: checking demo talk in `dist/demo`, headless

1. Build and serve the demo:
   ```
   bb demo-build          # release build, DEMO=true, into dist/demo/
   cd dist/demo && python3 -m http.server 8787
   ```
   Open `http://localhost:8787/` headless (e.g. a headless browser or
   `curl`-free automation that can click/type — the page needs JS).

2. Open 💭 (the phone) and tap the twin's contact row (Hal). Type
   `put a bench here` into its composer and press Enter. Read back the new
   last `li.theirs`/`li.mine` row under `#talk-list`:
   - `#talk-list li.mine .text` → exactly `You → Hal: put a bench here`
     (no ` · not sent` suffix).
   - The twin still answers and builds as before (`demo-ask!`): watch for
     the build event / the twin's next line, unchanged from today.

3. Go back to contacts and tap any other crew member's row (e.g. Le
   Critique). Its composer is enabled —
   `input[data-member="le-critique"]`'s placeholder reads
   `Text Le Critique…` (no "!" question needed to open it; the demo opens
   every member's say… box). Type `hello` into it and press Enter. Read
   back:
   - `#talk-list li.mine .text` → `You → Le Critique: hello` (no suffix).
   - A following `#talk-list li.theirs .text` →
     `Le Critique · Ask Hal, who runs the crew. In the full game I can
     answer you myself.` (the twin's name comes from the estate's
     `:jumeaux`).
   - Back on the contacts list, that member's row shows an unread badge of
     `1` until its thread is opened again (the badge clears on
     `talk/mark-seen`, i.e. tapping the row).

4. DOM selectors used above: `#talk-list` is the thread's `<ul>`; each row
   is an `<li class="mine">` or `<li class="theirs">`; the visible line is
   its `span.text` (`crew-label`'s output for a crew row, the raw `:text`
   for others). Each roster row's composer is
   `input[data-member="<agent-keyword-name>"]` (e.g.
   `input[data-member="hal"]` for the twin).

5. Sanity check the dev build still fails the old way: `npx shadow-cljs
   watch app`, bridge off (no `bb crew-bridge` running), say anything to
   a member or the twin. `#talk-list li.mine .text` still ends
   ` · not sent: bridge off`, and the twin still only gets the `:no-bridge`
   estate line (no `demo-ask!`).

## What changed

- `src/pm/talk.cljs`: `crew-kinds` gains `:crew-reply`; new `demo-member-line`,
  `say-reply` (the pure demo/post decision) and `crew-reply` (appends a
  member's pointer-line reply, the mirror of `crew-say`); `crew-label` gets
  a `:crew-reply` arm; `valid-restored-entry?`/`clean-restored-entry` gain
  `:crew-reply` arms so a saved reply round-trips; `with-walkers` gets a
  5-arity (`open?`) that opens every listed member's say… box in the demo.
- `src/pm/core.cljs`: `on-say` now asks `talk/say-reply` for the plan before
  deciding to POST; the demo branch resolves the entry `:sent` locally and
  answers via `demo-ask!` (twin) or `talk/crew-reply` (member, or nothing
  when `:member-talk` has no line). The dev build's path (bridge POST, then
  the twin's own no-bridge line) is unchanged. The roster's `with-walkers`
  call now passes `DEMO` as `open?`.
- `resources/estate.edn`: new top-level `:member-talk {:demo "Ask {twin}, …"}`
  block, after `:twin-talk`.

Gate: `GATE: PASS — 0 warnings; Ran 1577 tests containing 8518 assertions.`
(the tester's `test/pm/demo_talk_test.cljs`, 27 cases, is included in that
run — all passing.)
