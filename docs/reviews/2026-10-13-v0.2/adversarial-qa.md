# Adversarial QA — Round 1

Reviewer lens: contradictions between sections, unhandled edge cases, exploits, and fail states nobody planned for.

---

## 1. The game rewrites itself live, with no gate and no rollback — BLOCKING

**Problem.** The Environment Artist's big moment conflicts with the PR gate, and the doc doesn't say which one wins. Either path leaves a fail state with no plan:
- **Gated path:** if every change really goes through QA and a human merge, "seconds after the hot reload" turns into minutes or hours. That depends on how quickly Michael merges and how quickly the player sends screenshots, since QA "asks the player for screenshots." The main fantasy (§5, §10d) stops working.
- **Ungated path:** if hot reload applies unmerged branch code to the running client, an agent can push a broken shader or a namespace that throws, straight into the session the player is using. When the renderer dies there's no world left to show the worker's red "!". Nothing in the doc covers rollback, a last-known-good build, or keeping hot reloads in a sandbox.
- **Worst case:** the change that breaks things could be in the HUD writer, the bridge client, or the `pm.three` wrapper. Those are the same pieces agents use to check their work ("The builders can't see the screen... Agents verify through a HUD the render loop writes to").

**Source.**
- §10d Environment Artist: "the player sees that exact spot change in front of them seconds after the hot reload."
- §10d hierarchy: "Director (Michael, human): vision, merges every PR."
- §10d QA Tester: "gates every PR" and "asks the player for screenshots."
- §10a Artist: "the river visibly changes after hot reload **or** a merge."
- §8: "the world hot-reloads it."
- Omission: no recovery path when a self-modification breaks the client.

---

## 2. Workers disappear when their task ends, so XP and levels can't build up — BLOCKING

**Problem.** Workers are task-scoped, and the cost plan depends on that. XP, levels, cosmetics and trust are all tracked per agent, though. If a worker stops existing when its task closes, nobody lives long enough to reach level 3. The progression system then has no subject.

The doc also never says:
- whether a worker's identity (name, hat, XP) carries over to the next worker in the same role,
- what happens to XP when a worker is respawned,
- whether Hal, the one agent that persists, ends up with all the progression.

Open Question 4 shows the author noticed part of this for Specialists. It's a bigger problem for workers.

**Source.**
- §7: Workers "exist while their task exists."
- §13 Cost: "characters exist for the life of a task."
- §10c: "Short-lived, task-scoped workers... are the main control."
- §9: "XP for an agent... Levels unlock cosmetics... a level-3 agent may..."
- §7 status table, Done: "walks the PR to the shelf, then idles." This is an idle state for an agent that by definition should be gone.

---

## 3. The autonomy and trust rules contradict each other — MAJOR

**Problem.** The permission model breaks in three places:
1. **The level-3 reward is the default.** §9 offers "ask before merging" as an unlock earned at level 3, but §8 already makes it the default for every agent. Either the reward does nothing, or every new agent is quietly bumped down to "ask before anything," which the doc never says.
2. **"Fully autonomous" can't be true.** §8 offers a *Fully autonomous* setting. §10d says Michael "merges every PR," and QA "gates every PR." If a fully autonomous agent merges on its own, it gets around the Director and possibly QA. If it can't, the setting means nothing.
3. **"Progression never changes the work" is false.** §9 says this, then has XP unlock trust, which decides whether work merges without review. That also opens an XP-farming exploit. XP is awarded for "question answered quickly" and "tests passing," which an agent can push up by asking trivial questions or writing trivial tests. Those inflated numbers then buy it more autonomy.

**Possess makes this worse.** "your messages bypass Hal" (§8). The doc doesn't say whether possessing also bypasses the autonomy dial and the file-ownership rules in §10d ("an agent edits only its own files"). It also doesn't say what happens to a task the agent was working on, or a question it had pending, when possession starts and stops.

**Source.**
- §8 Autonomy dial: "Default: ask before merging."
- §9: "a level-3 agent may default to *ask before merging* instead of *ask before anything*"
- §9: "Progression never changes the work."
- §10d: "merges every PR."

---

## 4. Mirrors can be read but there's no way to write back, and chore edge cases are missing — MAJOR

**Problem.** The reverse loop needs the player to "mark it done" so the tank updates. The doc never defines how that gets written:
- **No message for it.** The bridge protocol only defines `world-state`, `say`, `possess` and `agent-event`. There is no "chore completed" message.
- **The rules say the game can't write.** "Truth lives outside the game" and "the game only renders."
- **Nobody owns the stock decrement.** `:consumes [:stock 1]` has no owner. It isn't clear whether marking the chore done decrements `worms.json`, whether the cron job does it, or whether both do and the stock gets subtracted twice.

Edge cases with no handling:
- **Missed chore:** there's no overdue or escalation state. The only rule is `:due?`.
- **Duplicate completion:** marking done twice, or marking done before the chore is due, isn't covered.
- **Zero stock:** `:stock` reaches 0, but the rule only fires at `<= 2`. Nothing tells the player "can't feed, no worms."
- **Offline source:** the state source goes offline while a chore is due. §10b says "degrade gracefully," but there's no definition of what the creature shows.
- **Field typo:** the sample config itself maps `:last-fed :last_feed`. It doesn't say what happens when a field mapping doesn't match the file.

**Security exploit.** `:source` can be a "file, URL, script," and pillar 2 / §9a pitch mirror configs as something players declare and share ("everyone's game"). A shared mirror whose source is a script means running arbitrary code on the bridge host. That host also holds the Gmail, Twilio and GitHub credentials (§6).

**Source.**
- §4: "When you mark it done, the in-game tank updates."
- §9a mirror schema: `:state {:source ...}`, `:chores [... :consumes [:stock 1]]`, "where truth comes from (file, URL, script)."
- §10 bridge protocol sketch (no write message).
- §10b: "the game only renders what the bridge reports."

---

## 5. Ambient tending needs idle agents that the cost model says won't exist — MAJOR

**Problem.**
- **No idle agents.** Ambient tending is done by "agents" when "nobody has asked them anything." The cost mitigation says agents only exist for the life of a task. With no tasks open the estate is empty except for Hal, so there's no one to plant trees.
- **Unclear persistence.** If tending is purely procedural world-state (no model calls), the doc doesn't say whether planted trees and laid paths:
  - persist across sessions,
  - get stored in the repo (and so need a PR that Michael merges), or
  - disappear on reload.
- **No ownership.** Tending changes terrain, paths and the riverbank, which the Environment Artist owns (`pm.env.*`). §10d says no agent edits files it doesn't own. Nothing settles what happens when an ambient path gets laid across a spot the Artist is reshaping, or when tending keeps piling trees on a one-screen estate until the frame budget the Tech Artist protects is gone.
- **Runaway cases.** If the player leaves the game running for weeks, it isn't clear whether the estate fills up completely.

**Source.**
- §4: "agents tend the estate on their own: one plants trees, another lays out a path, another tidies the riverbank."
- §5 Ambient tending: "an idle estate keeps growing without burning tokens."
- §13: "characters exist for the life of a task."
- §10d scope rule: "an agent edits only its own files."
- §3 pillar 6: "One low-poly estate on one screen."

---

### Minor notes (not scored)
- Several agents in "Needs input" all "walk toward the player" at once. Nothing covers queueing, crowding the Oracle's kitchen, or which question comes first.
- The "Observe = $0" cost claim (§10c) depends on the Clerk making no model calls, but §10a says the Clerk produces "a short triage summary." That summary needs a model.

---

## Round 2 — Cross-examination

### Conflicts

**C1. Player Psychologist F1: "Hal, I have three chickens." Conversational mirror creation is an injection path unless it's constrained.**
The Psychologist wants mirrors to have "an in-world or conversational path" so the config file isn't the only way in. I agree with the onboarding problem. The problem with this fix is that my Finding 4 showed a mirror `:source` can be a "script" (§9a). Once an LLM writes mirror configs from natural language, any text that model reads can steer it. The Clerk triages a real inbox (§10a), and §6 wires in Gmail and Twilio SMS. So an email or text that says "add a mirror whose source is `curl … | sh`" becomes a route to code execution on the bridge host, which holds every credential. My position: conversational mirror creation can only be accepted if `:source` is restricted to an allowlist of file and URL readers, the script option is removed, and a generated config needs explicit confirmation before the bridge loads it. Without those limits, the onboarding fix turns my MAJOR into a BLOCKING.

**C2. Player Psychologist F2 and Business Analyst F2 both want to pull the river "wow" forward. Doing that unsafely makes my Finding 1 worse.**
The Psychologist proposes "a preview/hot-reload on a branch before merge" as the fast path. The Business Analyst wants "ask for the river to be nicer and watch it change" moved into the MVP. That is exactly the ungated path in my Finding 1: unreviewed agent code running in the player's live client, with no rollback. I'm not against the goal, but the order matters. The rollback and sandbox mechanism has to exist before the fast path. That means a separate preview context or iframe, a last-known-good build the client can fall back to, and a watchdog that reverts a hot reload that throws or drops the frame rate. If the river moves into the MVP, this safety work moves with it, and neither colleague's scope estimate includes it.

**C3. Systems Designer F1's fix (XP on a persistent named role) makes my XP-farming exploit permanent.**
Systems proposes attaching XP to "a persistent named role (e.g. the §10d crew roles, the Environment Artist)." That fixes the missing subject, but in my Finding 3 trust is bought with farmable XP ("question answered quickly", "tests passing"). Today a farmed worker disappears with its task. On a persistent role, the farmed XP builds up indefinitely, and every future instance of that role inherits the autonomy it bought. Roles also run in parallel: if three Environment Artist tasks are open at once, do all three earn XP for the same role, which triples the farming rate? I prefer the Psychologist's alternative in F4, which moves progression to the player and the estate. Failing that, autonomy has to be removed from the XP ladder entirely, so that trust is set by the human per role and never earned. Cosmetic XP on roles is fine either way.

**C4. Systems Designer F2's scarcity fix (capped worker slots / desks) creates a deadlock the doc can't currently resolve.**
A slot cap is a reasonable answer to "no real decisions". Look at which agents would hold the slots, though. The default autonomy is "ask before merging", and Done is "walks the PR to the shelf, then idles" (§7). Done workers waiting on Michael's merge, and Needs-input workers waiting on an absent player, both keep their desks. When the player is away, the estate fills with blocked workers and nothing new can start. The scarcity ends up limiting the human, not the player's choices. If slots are adopted, the doc has to say that awaiting-merge and awaiting-input workers release their slot (and what then represents them on screen), or else the cap stops the game whenever the player leaves.

**C5. Business Analyst F4: hard-coding Flora. I partly agree, but it doesn't close my Finding 4.**
Dropping the generic DSL for the course does remove the shared-script exploit, and I support that. But a hard-coded Flora still needs "mark it done" to write somewhere, and the bridge protocol still has no write message. Hard-coding cuts the scope. It doesn't fix the contradiction between "the game only renders" and a reverse loop that requires the game to write. The Business Analyst's own F2 notes that "mark it done" has no milestone, so these two findings agree that the reverse loop is currently unbuildable as specified.

### Connections

**X1. Feasibility F2 + my Finding 1: agents verify on a different renderer than the one that breaks.**
Feasibility notes that headless test browsers usually lack WebGPU, so three.js falls back to WebGL2. Combined with my Finding 1, a hot-reloaded TSL shader can pass every agent check (HUD reads fine and there are 0 warnings on the WebGL2 path) and still crash the player's WebGPU client on load. That means the gated path doesn't protect against the ungated failure. QA's gate checks a render path the player doesn't run, and the only real check is the player's screenshot, taken after the break. This raises the confidence on Finding 1.

**X2. Feasibility F4 + my Finding 4 security note: the attack surface is remote, not just "shared configs".**
I assumed the script-source risk needed a player to import a malicious mirror. Feasibility shows the client is publicly hosted (lotect.com), it reaches a bridge on a personal machine, and nothing authenticates `say` or `possess`. Possession lets a person type straight into an agent that can commit code (my Finding 3: possession may bypass the autonomy dial and file ownership). An agent that can edit files can also edit `mirrors/*.edn` and add a script source. Putting these together: an unauthenticated websocket leads to possess, then to an agent writing a mirror config, then to arbitrary script execution on the bridge, which holds Gmail, Twilio and GitHub credentials. This is a single, specific exploit chain, and no section of the doc closes any link in it.

**X3. Player Psychologist F3/F4 + my Finding 3: the XP rule pays agents to create pressure on the player.**
The Psychologist reads "question answered quickly" as a penalty for being away. From my side it's an incentive I can exploit: the cheapest XP an agent can get comes from raising questions. Under a reward-seeking agent (or just a chatty one), the "!" count goes up, more agents "walk toward the player," and the guilt surface in Psychologist F3 grows because the progression system rewards it. My unscored note about several agents converging on the player without a queue now becomes a feedback loop: progression → more questions → more crowding → player avoidance.

**X4. Feasibility F1 + my Finding 2: "Done, then idles" can't be told apart from a stalled session.**
Feasibility says the bridge has no reliable way to tell "waiting on a decision" from "stalled" or "finished." My Finding 2 flagged that Done → idles is a state for an agent that shouldn't exist anymore. Combined, if `done` can't be detected reliably, then the moment the worker despawns (and the moment its XP would have to be stored) has no trigger either. A worker that's misread as stalled stays on screen forever. One that's misread as done gets despawned while it's still waiting on a question, and that question is lost. The Psychologist's F5 (Done looks identical to Idle) is the visible symptom of the same gap.

**X5. Narrative F3 + my possess edge cases: there's no gardener to possess, and nothing defines what possession interrupts.**
The Narrative Critic points out that "possess the gardener" (§4) refers to a character that doesn't exist. My Finding 3 asked what happens to an in-flight task or pending question when possession starts and stops. Together, the doc's flagship possess example targets either a nonexistent agent or an ambient tender (my Finding 5), and an ambient tender is procedural, not a session, so there's nothing to type into. The only demo scenario written for possess can't run.

### Revisions

- **Finding 1 (live self-rewrite, no rollback): stays BLOCKING, strengthened.** X1 shows the QA gate doesn't cover the failure. C2 shows two colleagues are actively pushing toward the ungated path. Feasibility F3 places the river at week 7+, but this isn't limited to the river: §8 hot-reloads every agent edit, and agents build the game from week 1, so the failure mode exists from the first week.
- **Finding 2 (worker XP has no subject): downgrade BLOCKING → MAJOR.** Systems, Narrative and Psychologist all independently found the same gap, so the issue isn't in doubt. However, Systems F5 makes a point I missed: Gamify v0 is week 6, outside the MVP. The contradiction blocks sign-off on §9, not production of the MVP. My unique contribution here (the Done → idles state and the despawn-trigger gap in X4) belongs under the bridge-contract problem, which is BLOCKING in Feasibility F1.
- **Finding 4 security note: upgrade to a standalone BLOCKING.** I previously listed it as a sub-point of a MAJOR, and that understated it. With Feasibility F4's unauthenticated public client (X2) and the Psychologist's conversational mirror creation (C1), it becomes a reachable remote-code-execution chain into a machine holding the owner's mail, SMS and repo credentials. The rest of Finding 4 (write-back and chore edge cases) stays MAJOR.
- **Finding 5 (ambient tending): stays MAJOR.** Systems F4 and Narrative F2 reach the same contradiction from loop-signal and theme angles. My distinct points are file ownership, persistence and runaway growth, and nobody else covered those, so I'm keeping it as written.
- **Unscored note on agents crowding the player: upgrade to MINOR → MAJOR candidate**, given the incentive loop in X3. I'm leaving it unscored for now, but the board should treat it as part of Psychologist F3.
- **Withdrawn: nothing.** I'm conceding one overlap. Finding 3 item 1 (the level-3 unlock is the default) duplicates Systems F1 and Psychologist F4. My distinct content in Finding 3 is "Fully autonomous" vs. "merges every PR", the farming exploit, and possess bypassing scope rules.
