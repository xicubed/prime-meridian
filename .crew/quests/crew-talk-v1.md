# Quest: Hal fields the crew's prompts, Michael sees what matters (crew-talk v1)

**Game:** Prime Meridian. **Asked by:** Michael, after trying v0 live ("it was a little rough, I
could only check one"; "I don't know yet if I want to talk directly to the bots or have you field
the questions and surface the necessary ones"). Agreed: **Hal fields by default and surfaces only
what needs Michael; Michael can take over any time.** **Led by:** Hal. Follows `crew-talk-v0`.

## What went wrong in v0 (fix all three)

1. **Prompt shapes.** v0 parsed one captured screen. Real rounds show others (no `│` box borders,
   wrapped commands, different headers and option lists), so most became `:unknown` (⚠, no box).
   Fixtures: every prompt screen captured during round crew-walkers-v0, in
   `.crew/quests/crew-talk-v1/screens/` (Hal copies them in, checked for secrets). Parse them all.
2. **Phantom prompts.** Herdr sometimes reports `blocked` for a moment while Claude is just
   thinking; the watcher then published the status line (`· Cogitating… (5m 16s …)`) as an unknown
   prompt. Only publish a prompt when the screen really shows a permission prompt (Claude Code's
   "Do you want to proceed?" / numbered options block); otherwise report the member as `:working`.
   Also require it on two consecutive polls (≥ 2 s apart) before publishing.
3. **Racing Hal's driver.** Fixed by the policy below.

## The policy

- **Hal fields by default.** Hal's round driver (outside this repo) answers read-only/test prompts.
  It will record each answer in `state/crew-log.edn` (Hal adds that; the format is yours to define
  in the spec: append-only vector of `{:at ms :member kw :action :allowed|:declined :by :hal
  :text "<command>"}`, last 200). The game shows those as quiet, read-only lines in 💭: "Hal allowed
  `bb test` for Le Testeur · 9:41 PM". No badge.
- **Surfaced to Michael** (red badge, boxes as in v0): risky prompts, prompts the parser can't read
  (`:unknown`, shown readably, never allowable from the game), and anything waiting longer than
  `:surface-after-s` (60, data) that Hal hasn't answered.
- **"I'll answer" switch** in 💭 (off by default). On: every prompt is surfaced to Michael with
  boxes, and Hal's driver holds off. The bridge gets a new action `hold` (`{"on": bool}`), same
  token/origin rules, which writes `state/hold.edn` `{:on bool :at ms}`; the driver reads it. The
  switch shows its state from that file, so it survives reloads and reflects the truth.
- Whoever answers second sees "already answered by Hal / by you" (from the log), not an error.

## Also

- Each member's line can show its last few log entries when expanded (tap the name).
- Lines for members that left (not in `crew.edn`) fade out, not error.
- Nothing about risky classification moves into the game: the bridge still decides.

## What exists (read first)

`scripts/crew_status.clj`, `scripts/crew_bridge.clj`, `src/pm/prompt.cljc`, `src/pm/bridge.cljc`,
`pm.talk`, `pm.crew`, `pm.core`, `pm.ui`, `public/index.html`, the v0 quest/run
(`crew/runs/crew-talk-v0-20261107-1728/`). Gate `.crew/gate.sh`. Don't read `assets/private/`,
`public/kit/`; you may read `state/*.edn`. Don't edit `public/vendor/` or `resources/content/`.
**Never send keys or prompts to crew members, and don't start the bridge or watcher yourselves.**

## Done when

Gate passes with tests for: every fixture screen parsed (command text, kind, risky) and the
thinking screens rejected; the two-poll rule; the log format and lines (quiet vs surfaced);
`:surface-after-s`; the `hold` action's validation; the switch's state from `hold.edn`; "already
answered by …". Live (Hal): a round where Michael sees only quiet lines; a staged risky prompt
surfaces; with "I'll answer" on, Hal's driver holds and Michael's ticks go through.
