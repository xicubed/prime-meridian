# Review: talk-captions

Reviewed against `crew/runs/talk-captions-20261106-1946/spec.md` and
`.crew/quests/talk-captions.md`, using `git diff` for the 9 modified tracked files and
reading all new files `git status` lists.

## Scope note

This round stacks, uncommitted, on top of `content-in-game` in the same shared tree (`log.md`'s
`spec` step already shows the 8-file/64-insertion state from that round before `build` starts).
`test/pm/world_test.cljs`, `plan_test.cljs`, `overlay_test.cljs` are in `git status` as modified,
but I diffed them against the content-in-game round's own diff (byte-for-byte identical) — no
further change landed on them this round, consistent with this round's boundary table ("must not
touch... the existing tests"). `resources/estate.edn`, `README.md`, `src/pm/core.cljs` likewise
carry content-in-game's hunks plus this round's own additions, isolated below by re-running
`git diff` and checking each hunk against this round's scope only.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → 0 warnings (`:app`, `:test`),
  `Ran 151 tests containing 922 assertions. 0 failures, 0 errors.` Matches `gate-note.md`
  exactly (125 existing + 26 new in `talk-test`).
- **Boundary table**: `git diff --stat` shows exactly 9 tracked files; the three new files
  (`src/pm/talk.cljs`, `src/pm/ui.cljs`, `test/pm/talk_test.cljs`) plus the builder-allowed
  edits to `core.cljs`, `public/index.html` (`<style>`/markup only — `<base href="/kit/">` line
  untouched), `resources/estate.edn` (only `:voices` and Juniper's `:voice` added), and one more
  README sentence. No test file other than the new `talk_test.cljs` changed this round.
- **`pm.talk` purity and tested**: requires only `cljs.reader`, `clojure.string`, `pm.world` —
  no atoms, no `js/Date`, no DOM calls anywhere in the file (confirmed by reading it end to
  end). All 23 public functions/defs hand-traced against the spec table match signature-for-
  signature, including edge handling (`nil` talk treated as `empty-talk` everywhere via
  `(or talk empty-talk)`, `append`'s identical-value return on a validation failure, `caption-
  ms`'s clamp order, `restore`'s counter/cap logic). 26 new tests in `talk_test.cljs` exercise
  every function, both mirrors, a generic code-unnamed mirror (`:fern`), and the content round's
  real `hal-say`/`hal-on-chore` wiring.
- **A line is logged once, not per frame**: `observe` returns the input `talk` itself
  (`identical?`) when nothing changed — `t07-logged-once-not-per-frame` drives 60 simulated
  16 ms frames through an unchanged Hal line and asserts `identical?` holds throughout, and that
  a fresh run over the same 60 frames produces exactly 3 entries (not 60). The dedupe keys
  (`:last-hal [text until]`, `:last-say` per mirror) are compared before any `append`, so a
  value re-derived every frame from `(:hal world)`/`markers` is only appended on its first
  sighting, and a changed `:until` (a repeated line) is correctly treated as a new utterance
  (`t08`).
- **Speech only after a click, stops on toggle-off**: `:voice?` starts `false` in `empty-talk`
  and the *only* place that flips it is `wire-talk-ui!`'s `#voice-toggle` click handler
  (`swap! !world update :talk talk/toggle-voice`) — `talk-tick!`'s automatic
  `(ui/speak! ...)` for a new caption is gated by `(:voice? t')`, so it can never fire before
  that first click. The same click handler calls `(ui/stop-speech!)` on the turn-off branch, and
  `pick!` (a history-row click) calls `stop-speech!` before speaking again, so a mid-line click
  cuts the previous utterance. `ui/speak!`/`stop-speech!` are the only two functions touching
  `speechSynthesis`, both wrapped in `try/catch`.
- **Keys typed into inputs and the kit's own keys aren't broken**: `close-key?` requires
  `open?` true and the target tag to not be `"INPUT"`/`"TEXTAREA"`/`"SELECT"` (`t18` covers all
  three plus a closed-panel Escape, which must fall through). `init!`'s keydown handler only
  intercepts (`preventDefault`/`stopPropagation`) when `close-key?` is true; otherwise it calls
  the pre-existing `on-key! e` unchanged — so with the panel closed, or a key typed into a form
  field, or any non-Escape key, the event reaches `on-key!` (and therefore the kit's own
  listeners downstream) exactly as before this round.
- **Storage failures silent**: `read-talk`/`write-talk!` are `try/catch :default _ nil`
  wrappers, the same pattern as the existing `read-cache`/`write-cache!`; `restore-talk!`'s
  `talk/restore` never throws (parses in its own try/catch and falls back to `empty-talk`
  on anything invalid, per `t23-restore-garbage`).
- **No npm deps**: `git diff --stat -- package.json package-lock.json` is empty — no change to
  either file. `pm.ui` touches only global browser APIs (`speechSynthesis`,
  `SpeechSynthesisUtterance`, `document`) and requires only `pm.talk`; no `three` reference in
  `pm.ui` or `pm.talk` (`grep -i three` on both returns nothing).
- **No mirror named in code**: `grep -ri "flora|juniper|stock|bales|worms"` over
  `src/pm/talk.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs` returns nothing. `"Hal"` appears only
  as `tk/hal-name`, the twin's name, not a mirror id — matching the quest's explicit carve-out.
  The mirror ids/voices live in `resources/estate.edn` (data), not code.
- **`resources/content/*.edn` untouched**: `stat` shows mtimes of 18:46–18:51 PST, unchanged
  from the content-in-game review and still well before this round's 19:46 start — confirms no
  write during this round either.
- **Core wiring matches spec section 5 point-by-point**: `hud-mirrors-suffix` now emits only
  `content/mail-line`, dropping the old `content/hud-segments` call (Hal's text is off the
  status line, per the quest); the talk-cache watch (`on-talk-change!`) skips unless `:talk`
  changed, mirroring the existing state-cache watch's pattern; `talk-tick!` runs right after
  `scene/update!` in `frame` and implements all four of its sub-steps (observe-and-swap,
  caption-show/hide keyed on `:n`, badge update, panel render keyed on `[(:n t') true]`) exactly
  as specified, with the implementation's extra `(some? e)`/`(nil? e)` guards in the `cond`
  correctly disambiguating what would otherwise be two independently-true `when`s in the spec's
  prose (a caption that just expired shouldn't both "show" a nil entry and "hide"); `pick!`,
  `wire-talk-ui!`, the Escape-aware keydown listener, and `init!`'s ordering
  (keydown → `restore-talk!` → `wire-talk-ui!` → `::talk-cache` watch → `restore-cache!` →
  `::state-cache` watch → `start-state-poll!` → `kit/load!`) all match the spec's code blocks.
  `jack-in!`'s call sites and `on-key!`'s wrap are unchanged from the content-in-game round
  (not touched again here).
- **`public/index.html`**: the new markup (`#voice-toggle` inside `#brook`, then `#caption`,
  `#talk-toggle`, `#talk-panel`) and all nine style rules match the spec's blocks verbatim;
  `<base href="/kit/">` is untouched.
- **`resources/estate.edn`**: only the top-level `:voices` map (after `:hud`) and Juniper's
  `:voice` override (after her `:name`) were added; nothing else in the file moved beyond what
  content-in-game already changed.

## Findings

None. Every scope item (pure, tested talk log; once-per-line logging; click-gated speech that
stops on toggle-off; inputs/kit keys unaffected; silent storage failures; no new deps; no mirror
names in code; untouched generated content) traces to a specific diff hunk and a specific
passing test, the test-file boundary was respected (only the new `talk_test.cljs`), and the
independently re-run gate matches the claimed numbers exactly.
