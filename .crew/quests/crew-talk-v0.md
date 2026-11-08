# Quest: talk with the crew in the 💭 bubble (crew-talk v0, W3-0 next slice)

**Game:** Prime Meridian. **Asked by:** Michael: "put their lines in the thought bubble! like the
checkbox, and we could 'check' it to approve or whatever... or even type something to them."
Rule (Michael's choice): risky prompts show in the game, but there you can only say no or type a
reply; Hal reviews the risky ones. **Led by:** Hal. Follows `crew-dots-v0`.

## The moment we're building

Un Automate gets stuck at a prompt. His dot goes red, 💭's badge turns red, and the bubble shows:

> ☐ **Un Automate** · wants to run `node -e 'console.log(6*7)'`  [✓ allow] [✕ no] [reply…]

Michael ticks it → the agent continues, the line becomes ☑ allowed 4:52 PM. Or ✕ → it's told
no. Or he types "use bb instead" → it's told no, with that note. For an idle member he can type a
message to it from its line. A risky prompt (see below) shows ⚠ and no allow box, just ✕ and reply,
plus "Hal reviews this one".

Every member of the crew is in the bubble, walker or not (the architect, runner and critic have no
walker yet): the bubble is the crew's channel, the walkers are just some of them on the estate.

## Pieces

1. **Watcher** (`scripts/crew_status.clj`, Hal's, you may edit): for a blocked member, also read
   its screen (`herdr agent read <name> --source visible`) and add
   `:prompt {:id "<sha-1 of the prompt block>" :kind :bash :text "<the command>" :risky bool}`.
   Parse Claude Code's permission prompt (fixture: `.crew/quests/crew-talk-v0/claude-bash-prompt.txt`,
   a real screen; also handle the variant drawn inside `│ … │` box borders, and long commands that
   wrap). Unknown prompt shapes: `:kind :unknown`, `:text` = the last few non-blank lines, never
   allowable from the game. `:text` capped at 400 chars. Nothing else from screens goes in the file.
2. **Bridge** (new, `scripts/crew_bridge.clj`, `bb crew-bridge`): a tiny HTTP server
   (babashka's `org.httpkit.server`) on **127.0.0.1:8091 only**. On start it makes a random token and
   writes `public/state/bridge.edn` `{:port 8091 :token "<hex>"}` (gitignored dir, and the game
   server is localhost-only). It accepts only `POST /crew/<member>` with JSON
   `{"token":…, "action":"allow"|"deny"|"say", "prompt-id":…, "text":…}` and checks, in order: the
   token; the `Origin` header is exactly `http://localhost:8080` (CORS preflight answered for that
   origin only); the member is in `.crew/crew.json`; then by action:
   - `allow`: the member is blocked now, the prompt it shows **now** hashes to `prompt-id`, and the
     prompt is not risky and not `:unknown` → `herdr agent send-keys <m> 1`.
   - `deny`: blocked and the id matches → `herdr agent send-keys <m> esc`; with `text`, then wait
     for it to be idle (≤5 s) and `herdr agent prompt <m> <text>`.
   - `say`: the member is idle or done (not working, not blocked) → `herdr agent prompt <m> <text>`.
   Text ≤ 1000 chars, single argument (no shell). Everything else → 4xx with a reason. Every
   request is appended to `crew/bridge.log` (time, member, action, result; not the token) —
   gitignored. Risky = same patterns as Hal's driver: `git (add|commit|push|reset|checkout|stash|rm|clean|restore)`,
   `rm -`, `curl`, `wget`, `ssh`, `scp`, `npm (i|install|publish)`, `pip install`, `brew install`,
   `sudo`, `launchctl`; plus anything writing outside the repo. **The bridge decides risky, not the game.**
   Add it to `.crew/crew.json` `"services"` (Hal will restart services) and to `bb.edn`.
3. **Game** (`pm.talk`, `pm.core`, the 💭 panel): crew lines from `crew.edn` — a prompt line per
   blocked member (☐ / ✕ / reply; ⚠ for risky), a status line when a member finishes
   ("Le Testeur · done"), your own messages ("You → Le Testeur: …"). Ticking posts `allow` with the
   line's prompt id; the line shows the result (☑ allowed / ✕ declined / "already answered" if the
   id no longer matches). Crew lines persist like chore lines (no repeats on reload). 💭's badge is
   red while any prompt is waiting. No bridge running → the boxes are disabled with "bridge off".
   Members are named by their roster `title` (crew.json), never hard-coded.

## Tests (the gate runs the cljs tests; put the pure logic in .cljc where bb and cljs both use it)

Prompt parsing on the fixture and the box variant, wrapped commands, unknown shapes; risky
classification (each pattern; benign `node -e`, `bb test`, `ls` not risky); prompt-id stability;
the bridge's request validation as a pure function (token, origin, member, action rules, length
limits) without starting a server; the game's line building and state transitions (pending →
allowed/declined/stale), the badge, the disabled state.

## What exists (read first)

`scripts/crew_status.clj`, `bb.edn`, `.crew/crew.json`, `pm.talk` (lines, checkboxes, persistence),
`pm.core` (state polls), `public/index.html` (💭 panel). Gate `.crew/gate.sh`. Don't read
`assets/private/`, `public/kit/`; don't edit `public/vendor/` or `resources/content/`. **Never send
keys or prompts to any member yourselves** and don't start the bridge against the live crew: Hal
tests it live.

## Out of scope

Walking over to the player, the "!" above heads, Hermes/Codex prompt shapes beyond `:unknown`,
members' replies in the bubble, remote (omarchy) members.

## Done when

The gate passes with the tests above. Live (Hal): a staged harmless prompt shows in 💭, ticking it
lets the member continue; ✕ with a note declines it; a staged risky prompt (`curl --version`, then declined) shows ⚠
with no allow; `curl` from another origin is refused; 60 fps unchanged.
