# Builder notes — crew-mirror-v0

How Hal checks this live (after the gate passes and Michael has committed):

1. **Restart the services** so they pick up the new code (they were running the old
   build the whole time I worked):
   ```
   # stop the running bb crew-status / bb crew-fielder / bb crew-bridge, then:
   bb crew-bridge &
   bb crew-status &
   bb crew-fielder &
   ```

2. **A question gets a hand up.** Have a crew member finish a turn whose last line
   ends in `?` (or just wait for one to happen naturally). Within a couple of polls:
   - `state/crew-said.edn` gets a new entry under that member's key with `:question? true`.
   - In 💭, that member's row (and its walker) shows the "!", and the thread gets a
     quiet line reading "`<who> asks: <question>`".
   - Reply from the thread's existing box; the "!" clears. It also clears if the member
     starts working again before you reply.

3. **A turn with tool calls and prose.** Any ordinary member turn (edits a file, runs a
   command, then says something) should land as *one* quiet line in 💭 once the member
   goes idle/done — the summary of its last prose block only, never the tool calls or
   diffs. Compare against `herdr agent read <member> --source recent` to confirm it's
   reading the same last `⏺` block.

4. **An unknown-shape prompt (not a plain bash command).** Trigger one (e.g. an Edit
   permission dialog) on a member. In 💭 it should show with **Allow** and **No** (plus
   **Always** if the dialog itself offers a "don't ask again" option) — not just ✕ like
   before. It also gets forwarded once into my Hermes chat (`[from the crew] <member>
   asks to run: ... · answer with: bb crew-answer <member> allow|always|deny`), and
   `state/crew-forwarded.edn` records its id so it isn't forwarded twice.

5. **Answer it with `bb crew-answer`.** From a shell:
   ```
   bb crew-answer <member> allow
   bb crew-answer <member> always      # only if the forwarded line offered it
   bb crew-answer <member> deny --note "use bb instead"
   ```
   Each prints `<member>: <result>` (or `<member>: <reason>` and exits 1 on failure).
   If you wait for the dialog to change first, it should print the stale note instead
   of sending anything. In 💭, the entry resolves to "☑ allowed"/"☑ always allowed"/
   "✕ declined", same as if the player had clicked it.

6. **Hold.** Flip "I'll answer" on (the 💭 hold switch) and confirm no new line gets
   forwarded to my chat while it's on, and the fielder leaves prompts alone too.

7. **`--as` lines.** `bb hal-say "line here" --as <member>` should show the line on
   that member's speech plate *and* as a quiet line in that member's own 💭 thread
   (previously only the plate).

Where to look: `crew/bridge.log` and `crew/fielder.log` for the twin/player attribution
on each decision (`allow`/`approve`/`always`/`forward`, `... by the twin` on bridge.log
entries the twin answered); `state/crew-said.edn` for the raw rolling said-log;
`state/crew-forwarded.edn` for what's been sent to my chat and when.

## Known pre-existing test conflicts (not edited — see reply)

The spec's new `:approve`/`:always` actions deliberately make four already-landed
assertions fail; see the builder's reply for the list and why.
