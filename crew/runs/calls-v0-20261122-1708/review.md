# Review: calls-v0 (crew/runs/calls-v0-20261122-1708)

Scope: `git diff` on `public/index.html`, `src/pm/bricks.cljs`, `src/pm/core.cljs`,
`src/pm/crew.cljs`, `src/pm/talk.cljs`, `src/pm/ui.cljs`, plus the two new files
`src/pm/call.cljs` and `test/pm/calls_test.cljs`, against `spec.md` and the quest. The round's
own `replies/review-le-critique.txt` is a recursive capture of this same reviewing session
(it literally echoes this conversation's earlier "say hi" turn) — not independent evidence,
disregarded.

## Conformance

- Gate re-run independently (not just trusted from `gate-note.md`): `bash .crew/gate.sh` →
  `GATE: PASS — 0 warnings; Ran 1083 tests containing 7186 assertions.` Matches `gate-note.md`
  verbatim.
- Test-count math verified against the real baseline rather than the nearest run dir (whose
  gate-note.md was stale, pre-dating a later commit): `git show HEAD:test/**` deftest count is
  1012, working tree is 1083 → **+71**, matching the spec's "71 cases" exactly. `pm.calls-test`
  confirmed present in the raw test-runner output (`Testing pm.calls-test`), not just assumed
  from a truncated gate transcript. All 71 labels c01–c71 present and hand-traced against the
  spec's expected values (call-lines/call-line?, call-voice, start/end-call identity semantics,
  end-reason precedence, call-button/mic-shown?, mic-step's full table including the "`:off`
  sticks" and "anything else → normalised mic" rows, say-route, plate-name/mark-on-call/
  glyph-name) — no mismatches found.
- One call at a time / ending stops speech+recognition: `call/start-call` replaces any existing
  call (one `:call` key, swap not conj, tests c22–c25); `end-call!` (`pm.core`) runs
  `call/end-call`, `ui/stop-speech!`, `ui/stop-listening!`, `mic-step [:stop]`; `stop-listening!`
  clears all four recogniser handlers *before* `.abort`, so a stray `onresult`/`onerror` can't
  fire after a call ends. `talk-tick!` calls `end-call!` on any `call/end-reason` (closed,
  switched) before the frame's other work reads `:call`.
- Only the called contact's new entries are spoken: `talk/call-lines`/`call-line?` are pure and
  tested (c01–c14); `pm.core` feeds them `(:with call')`/`(:call-heard @!talk-ui)` and the
  caption path explicitly excludes any entry that's a call-line for the live call
  (`not (talk/call-line? e contact-of (call/call-with @!world))`), so nothing is said twice.
- Recognised text has no second path: `on-mic!`'s `:on-final` handler calls `(on-say member who
  text)` directly — the same fn typed Enter uses — and `on-say` itself is now a pure dispatch on
  `call/say-route`'s `:answer`/`:say`/nil, tested in isolation (c58–c61).
- 44×40 corner-badge rule: `public/index.html` joins `#crew-roster .pm-btn.call, #crew-roster
  .pm-btn.mic` into the existing selector list (not a copy) and adds the override rule verbatim
  as specced (font/white-space/overflow only — width/height/box-sizing untouched). Plate style
  untouched: `bricks.cljs`'s `scene/plate` call sites (`:height`/`:canvas`/`:font-max`) are
  outside this round's diff; only the label text (`crew/plate-name`) and arity changed.
- Nothing speaks before the first click: a call can only exist via `start-call!`, which sets
  `!gesture?` itself; the call-speaking `doseq` in `talk-tick!` is additionally gated on
  `@!gesture?` directly; the mic/no-mic-line path only runs inside `on-mic!`'s click handler or
  its recogniser callbacks, which can't fire before that click. `test/pm/calls_test.cljs` never
  requires `pm.ui`/`pm.core` and never calls `speak!`/recognition — a headless run turns
  nothing on.
- No person/pet/member named in code: new/changed source only uses plain keys and the
  pre-existing `:kind :hal` idiom (already used the same way elsewhere in `pm.talk`/`pm.ui`
  before this round) — no new hardcoded display name or literal contact id in logic.
- No npm deps: `git diff -- package.json package-lock.json` is empty.
- DEMO paths compile: `pm.core/DEMO` branches live in the same compilation unit the `:app`
  gate target already compiles (goog-define, not advanced-mode dead-code elimination), and that
  build is part of the 0-warnings gate result above; `bb demo-build` itself is explicitly Hal's
  check per the boundary table, not re-run here.
- Boundary table: each file's diff is confined to exactly the functions/arities the table
  names for that role (verified hunk-by-hunk) — no stray edits to `resources/estate.edn`,
  `resources/content/*`, `shadow-cljs.edn`, `bb.edn`, `scripts/*`, `docs/GDD.md`, or any
  pre-existing test file.

## Findings

1. `crew/runs/calls-v0-20261122-1708/note.md:1` — the builder's note is 32 lines; spec §7 caps
   it at ≤ 25. Trim the file list/change summary (e.g. fold the per-namespace bullet list into
   fewer lines) before the round is considered fully spec-conformant.

Nothing else rises above that: the pure-fn test coverage, the call/mic/plate wiring, and the
CSS all match the spec's literal text, and the independently-run gate confirms the claimed
PASS.
