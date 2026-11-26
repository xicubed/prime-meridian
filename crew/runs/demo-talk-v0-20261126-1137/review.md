# Review: demo-talk-v0 (crew/runs/demo-talk-v0-20261126-1137)

## Scope
`git status` shows exactly: `resources/estate.edn`, `src/pm/core.cljs`, `src/pm/talk.cljs` modified,
plus new `test/pm/demo_talk_test.cljs` and this round's own run dir. HEAD (`61df567`) sits one
commit ahead of the spec's stated start commit (`f2b848c`), but that commit only touches
`README.md` (confirmed via `git show --stat`), so it has no bearing on these three files — the
plain `git diff` is this round's full and only contribution, no stacking/reconstruction needed.

## Verified independently
- Re-ran `bash .crew/gate.sh` myself (not just read `gate-note.md`): `GATE: PASS — 0 warnings;
  Ran 1577 tests containing 8518 assertions.` Confirmed `pm.demo-talk-test` actually executes
  (`node out/test.js` output includes `Testing pm.demo-talk-test`) and the new file has exactly
  27 `deftest` forms, matching the spec's own "27 numbered cases" claim case-by-case (t01–t27 map
  onto spec §6 items 1–27, including both fixtures WE/WA).
- `src/pm/talk.cljs`: `say-reply`, `demo-member-line`, `crew-reply`, the `crew-kinds`/`crew-label`/
  restore arms, and the `with-walkers` 5-arity all match spec §3 character-for-character,
  including check order in `say-reply` (member-keyword → demo? → twin-match → data lookup) and the
  `:identical?` short-circuit in `crew-reply`. All three new/changed functions are pure: no
  `js/Date`, atoms, or three.js calls; only require added is `[pm.content :as c]` (no cycle —
  `pm.content` requires only `pm.jumeaux`), matching the boundary table.
- `src/pm/core.cljs` `on-say` (§4.1) and the one `with-walkers` call site (§4.2) match the spec's
  literal code block, including reusing `who` as `on-say`'s own argument. No bridge POST is ever
  issued in the demo branch (`post-crew!` only runs inside `(:post? plan)`, which is `true` only
  when `DEMO` is not `true`) — the DEMO-only / no-bridge-POST-in-demo requirement holds structurally,
  not just by test. The dev-build path (bridge on or off) is untouched byte-for-byte except for
  being reached through the new `plan` indirection, so "dev build behaves exactly as today" holds,
  confirmed by `t23`/`t24` reproducing the `:failed · not sent: bridge off` label for both a member
  and the twin.
- `resources/estate.edn`: the `:member-talk {:demo "Ask {twin}, …"}` block is inserted verbatim,
  directly after `:twin-talk`'s `:sample-says […]}` line, as spec §2.2 requires.
- Member reply lands in the member's own 💭 thread: `crew-reply` tags the entry `:member member`
  (not the twin's agent), and `t19`/`t20`/`t21` confirm `(:member (e t3 2))` is the recipient member
  and that it counts as unread via `unread-by`/`call-line?` keyed by `:member`.
- No person/pet/member name appears in the new code: `talk.cljs`/`core.cljs`/`estate.edn`'s diffs
  use only the `{twin}` placeholder, filled at runtime from `:jumeaux` via `pm.jumeaux/twin`; names
  (`"Hal"`, `"Ada"`, `"Le Critique"`, …) appear only in the test fixtures, which is expected and in
  scope for the tester. `package.json`/`package-lock.json` diff is empty (no npm deps).
- Boundary table: builder's diff is confined to `talk.cljs` (§3 additions only, no unrelated
  function touched), `core.cljs` (only `on-say` and the one roster call site), and the
  `estate.edn` `:member-talk` block; tester's diff is confined to the one new test file; no other
  tracked file is touched this round.

## Findings

1. **PLAY.md (§8) was never updated — the quest's own "Done when" bar isn't met yet.**
   `PLAY.md:71-77` still reads "the crew members don't answer you in this build (your message to
   them shows *not sent*)", which is now false in the demo build this round ships. `log.md` shows
   only `spec`/`build`/`gate`/`review` steps have run; no step has dispatched le-docteur. The gate
   passing doesn't cover this (gate only checks compile+tests), and the quest explicitly lists the
   PLAY.md rewrite as part of "Done when." This is the one outstanding item before the round can
   close — dispatch le-docteur per spec §8 (keep the opening/closing sentences, rewrap within
   lines 71-77, don't quote the data string verbatim) and re-check.

No other findings rise above a style nit — the code change itself is a clean, spec-conformant,
fully-tested match.
