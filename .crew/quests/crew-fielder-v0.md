# Quest: the twin fields the crew's prompts as a service; les jumeaux in data (crew-fielder v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8, after crew-talk v1: "how can we transplant
your part into a reusable area if we deploy this game and someone else runs [Herdr] as the
mastermind?" **Led by:** Hal. Follows `crew-talk-v1`.

## The game moment

A player who isn't Michael installs the game and runs the crew with their own agent (Hermes, Claude
Code, anything that can drive Herdr, or no agent at all). Their twin still answers the crew's routine
prompts, 💭 still shows the quiet lines with *their* twin's name, and only risky / unreadable /
overdue prompts reach them. Nothing in code knows the twin is called Hal.

## Decided (Michael, Nov 8): don't reopen

- **Hal stays** as the name of Michael's twin: it's data, not code.
- **Les jumeaux** = the pair, the player and their twin. One block of data holds them:
  ```clojure
  :jumeaux {:player {:name "Michael" :sex :m}   ; asked once in onboarding (TASKS O-1)
            :twin   {:name "Hal"}}
  ```
  The twin's word follows **the player's sex** (it's *their* twin): `:m` → *jumeau*, `:f` →
  *jumelle*; the pair is *jumeaux* (`:m`) or *jumelles* (`:f`). This is NOT the crew's
  cloud/local gender rule; that rule stays for crew members only.
- **The log keys don't change with the player's sex:** `:by :twin` / `:by :player` (the crew-up
  README contract, crew-up ≥ 0.2.4, section "Answering the crew's prompts"). Display names come
  from `:jumeaux`.

## Scope

1. **`:jumeaux` in `resources/estate.edn`** (the shape above, Michael's values as the sample) and a
   pure reader in `pm.world` (or a new small ns): twin name, player name, twin word, pair word.
   Missing block → `{:twin {:name "Your twin"}}`-style defaults, word *jumeau*; a bad `:sex`
   falls back the same way, never an error. A second example in the tests (a `:f` player whose
   twin is "Ada") from the same code path.
2. **No `"Hal"` in code:** `pm.talk/hal-name` and every `"Hal"` / `"by Hal"` / `"Hal reviews this
   one"` string become the twin's name from data (the bridge's 403 reason too: it may say
   "your twin reviews this one", or take the name from the estate). The `:hal` *event kind* in
   talk/world may stay an internal keyword if renaming it would touch half the game; say which in
   the spec.
3. **Log role keys:** `pm.crew-log` accepts `:by` ∈ `#{:twin :player}`; the bridge writes
   `:player`. Old entries with `:hal` / `:michael` (Michael's live `state/crew-log.edn`) are read as
   `:twin` / `:player`, never dropped.
4. **`bb crew-fielder`** (`scripts/crew_fielder.clj`, logic pure in a `.cljc` ns, new `bb.edn` task):
   the twin's automatic half, replacing Hal's private driver scripts. Every poll (2 s) it reads
   `state/crew.edn` and `state/hold.edn`; for each member whose published prompt (already two-poll
   confirmed by the watcher) is `:kind :bash` and `:risky false`, and only while hold is off, it
   waits `:answer-after-s` (data in `:crew-talk`, default 5), re-reads that member's screen,
   answers only if `read-prompt` still gives the same `:id` (the bridge's `decide` rules, reused, not
   copied), sends `1`, and appends `{:by :twin :action :allowed}` to `state/crew-log.edn`. It never
   declines and never answers risky or `:unknown` prompts (those wait for the player or the
   manager). Same roster check as the bridge; Herdr called with an argument vector, no shell.
   It logs to `crew/fielder.log` (gitignored like `crew/bridge.log`). Hal adds it to `crew.json`
   `services` and the round files get `"onBlocked": "wait"` (crew-up 0.2.4).
5. **One classifier:** risky is `pm.prompt/risky?` only. Nothing in the fielder has its own list.

## Out of scope

The onboarding question itself (TASKS O-1, the Construct); a policy block in `crew.json`; the
Architect's room; any change to the watcher's two-poll rule or the parser.

## What exists (read first)

`src/pm/prompt.cljc`, `src/pm/bridge.cljc`, `src/pm/crew_log.cljc`, `src/pm/talk.cljs`,
`pm.world`, `pm.core`, `scripts/crew_bridge.clj`, `scripts/crew_status.clj`, `bb.edn`,
`resources/estate.edn`, the crew-talk-v1 run (`crew/runs/crew-talk-v1-20261108-0931/`), crew-up's
README section "Answering the crew's prompts". Gate `.crew/gate.sh`. Don't read `assets/private/`,
`public/kit/`; you may read `state/*.edn`. Don't edit `public/vendor/` or `resources/content/`.
**Never send keys or prompts to crew members, and don't start the bridge, watcher or fielder
yourselves.**

## Done when

Gate passes with tests for: the `:jumeaux` reader (Michael's sample, the `:f`/"Ada" example,
missing block, bad `:sex`); no `"Hal"` literal left in `src/` (a test or the critic's grep); the
`:by` keys incl. reading old `:hal`/`:michael` entries; the fielder's pure decision (benign → answer
after the delay; risky, unknown, hold on, changed id, not in roster, already answered → no). Live
(Hal): a round with `"onBlocked": "wait"` where the fielder answers the routine prompts and Hal
answers none, 💭 shows "Hal allowed …" read from data, a staged risky prompt waits for Michael, and
with "I'll answer" on the fielder holds.
