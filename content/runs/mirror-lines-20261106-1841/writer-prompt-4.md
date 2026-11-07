# Source: docs/GDD.md (Prime Meridian's game design document), the sections this content draws on

## 1. Executive Summary

*You wake up. At least you think you're awake. Someone had better damn well make some coffee... Your lips pull in the hot liquid as you boot up. Who are you kidding? You don't boot up anymore. Your agents are always on. Let's see what they came up with while you slept.*

*Then you jack in. The estate fades up out of the dark grid. The workshop lights are still on, a stack of blueprints waits on the shelf, and Hal is already walking over with the morning card. A worker who got stuck at 3 a.m. paces under a red "!" (waiting for human input); one answer and he's back at his desk. You approve last night's river, and the new water ripples into place. Down at the canal, Flora the axolotl drifts up from the weeds: "I'm hungry." Then the phone booth rings. It's your kid. Time to go.*

**Prime Meridian** is a cozy 3D management game in the browser where the characters are real AI agents doing real work. You walk a small medium-poly estate whose rooms are your projects and your life: a workshop for each code repo, a canal where your real axolotl lives, a front office for your business, a phone booth where the real world calls in. You talk to the agents living there, hand them work, answer their questions, and watch the world change as they build it. Think of the Matrix, TRON, and the OASIS, with one difference: the OASIS keeps you in while your real life decays, and this world nudges you out (time to feed the pets, time to work out) or walks you to the door (bedtime). Some agents are closer to NPCs: programs that load tools and skills as needed and help with the paperwork and organizing of real life.

| At a glance | |
|---|---|
| **Genre** | cozy management / life sim whose cast is real AI agents |
| **Platform** | the estate renders in a desktop browser (three.js, WebGPU with WebGL2 fallback), with a small local bridge next to the player's own agent. But the game is played **everywhere your agent lives**: your editor (Zed, where the crew builds), your phone (SMS, iMessage, Messenger: the phone booth), your mail, calendar, and reminders, your website and blog (your twin edits posts and writes the daily dev log), every online account your agent holds (for the author: Google, Claude, Hermes, Muse, Twilio, Stripe, GitHub, WordPress), across every machine you connect |
| **Audience** | people who run a personal AI agent, or want one, and not only coders: the same twin that ships code orders the weekly groceries for pickup or files a business's paperwork. v1 is built for one player (the author, on Hermes); a **demo mode** with pretend connections lets anyone else (classmates, the instructor) play with no accounts, and a **novice path** (§2.8) takes a less technical player from zero accounts to a first real connection. **Not for** someone who wants a traditional video game with no AI in it: this is a life sim whose cast does real work. |
| **Session** | one day is one round: jack in, play, put in night-shift orders; the game closes at bedtime (10:30 pm by default) |

**Batteries not included.** Prime Meridian isn't a car off the lot; it's a kit car you assemble yourself. The game ships the chassis: the world, the rules, the crew's bodies. You bring the parts: your own agent and AI logins, your tools and accounts, your life. Before you bolt anything in, take a test drive: every connection comes with a sample version (a pretend inbox, phone, ledger, and scripted agents), so anyone can play with no accounts and swap in the real parts one at a time.

**Core loop:** *observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world.* Alongside it runs the **reverse loop**: a real chore comes due (Flora the axolotl is hungry, Biscuit the dog needs his walk), the world calls you out, you do it in real life, and the world updates.

**Win / loss**

- **Win the day.** Each day is a round. You win it when every "!" got an answer, your own chores are done, and you jacked out by bedtime.
- **Win a chapter.** Each week a new part of life comes online (pets, household, gym, business, mail). You beat that chapter when it runs for a full week with you doing only the human parts and the agents doing the rest.
- **No game over.** A missed day leaves the estate a little overgrown. It is never ruined and recovers when you return. There are no streaks to break and nothing to shame you.
- **The only real loss is in real life:** Flora goes hungry, you miss your kid's game, you stay jacked in while life piles up. That's the *Ready Player One* ending, and the game is built to prevent it.
- **The long-term win is needing the game less,** measured two ways that only count together: your agents need you less (fewer "!"s and approvals per task) and you live more (fewer hours jacked in, chores still done in person). Handing everything to fully autonomous agents doesn't win on its own. The author's target curve: about ten hours a week in the game the first month, about two a year later. Your twin shows the trend on a weekly card on the notice board, never as a score.

**Why a game, not a terminal?** You could do everything in this game at a terminal. The author does today: agents in split panes, texts to his bot, logs scrolling past. It works, and it's a wall of text. Every tool for running AI agents is a list (terminal splits, chat tabs, task queues), and lists stop working past two or three agents. Prime Meridian **gamifies the same work**: instead of tailing a log you walk up to an agent and talk; instead of a status column you see who's typing, who's stuck, and who's walking toward you with a "!"; instead of a cron job you watch Hal pin the morning brief on the board. Same agents, same real results, a natural 3D interface on top, and the fun is the point. Strategy games solved "how does one human track many things at once" decades ago with a spatial map, alerts you can see from across it, and details on demand. Ralv.ai put agent windows under an RTS camera; Prime Meridian goes further: the agents are embodied characters in a place, and the place is both the interface and the thing being built. The author's bot already runs his mail, calendar, reminders, texts, website, and an axolotl's feeding schedule, so the world is alive on day one without inventing a plot. Kyle Kukshtel's essay [*Your Game Is a Harness*](https://kylekukshtel.com/ai-harness-engineering-game-development-claude-code) argues any game can be its own agent harness; Prime Meridian turns that around: **the harness is the game.** Getting wrapped up in the harness isn't a distraction from playing, it is playing, and the cut line in §4.8 keeps each week's harness work visible on the estate.

**The cast: you're Neo.** The feeling is *The Matrix* crossed with *TRON*: a human with a presence in a world of programs.

- **You are Neo.** You jack in, you load programs ("I know kung fu"), and the whole world turns on you: you know what no program can (what you want, what matters, what "good" looks like), so the agents come to you.
- **Your agent is your digital twin** (for the author, his bot Hal), and the one-wow agent. It's your body in the digital world, always on while you're out: it carries your loadout, runs your errands, hands work to the crew, and spruces up the half-barren estate. You're its hands in the physical world.
- **The Oracle is the author** (Michael): the human who designed the path, knows where it leads, and approves every change the crew makes. On his own machine he happens to be both: the Oracle who designs the game and Neo who plays it. But eventually you become your own Oracle and study the psychology of the humans and bots.
- **The Architect is the architecture.** Not a character you play: the system that designs and rebuilds the world. For the author it's Hermes running inside his editor (Zed), driving Herdr, which reaches across machines (the Mac where the agent lives, a GPU box for local models, physics, and Blender) and puts every crew session side by side. In-game the Architect's twin sits in **the Architect's room**, inside a wall of monitors, one per live agent session on any machine, and that wall is all he sees. You see the whole game, with him in his room of monitors... like when Dorothy visited Oz: the world he "created" is just a little code, yet it expands out into real life.

*Film references (The Matrix, TRON, Ready Player One, The Wizard of Oz) are shorthand for feelings you already recognize, not the films' characters or lines; the shipped game uses original names and dialogue.*

**Design pillars**

1. **Real, not simulated.** Every character is the twin of something real (a live agent session, a real pet, a real person), so when a worker sits down with a red "!" it's true, and when Flora's jar says "2 worms left" it's the count you reported.
2. **Everyone's game, built for one first.** The code has no idea what an axolotl is: your creatures, chores, household, and business are data you declare, so the estate mirrors *your* life. v1 is tuned for one player; the data model keeps it open to everyone.
3. **Spatial over textual.** From the default camera you can tell who's busy, who's blocked, and who needs you by where they stand and what floats over their heads, before reading a word.
4. **Tell them in the world, not in a terminal.** What's the point of seeing your agents if you have to go back to a terminal to tell them anything? You walk up and talk ("these rocks all look the same; make each one different"), and you watch the result appear, because the game is built by its own agents.
5. **It never adds work.** Every action in the game is a real one or an optional one: no feeding a pet that doesn't exist, no busywork, and the agents work the same with the game closed.
6. **It sends you back to your life.** Ten hours a week in it the first month, two a year later, and more time for everything else: it calls you out when life needs you and walks you to the door at bedtime.

---


---

### 2.3 A day (one round)

1. **Jack in.** The screen dissolves into the estate and Hal says "Welcome back." You see what changed overnight and what's waiting for your approval.
2. **Play.** Answer the "!"s, approve blueprints, assign new work, tend your mirrors.
3. **Get jacked out when life needs you.** The phone booth (the hardline) rings and the world freezes gently. Who can pull you out, in priority order:

| Priority | Who | Example |
|---|---|---|
| 1 | **People in your household** | "Dinner!", "Dad, ride?" |
| 2 | **Creatures** | Biscuit at the door with his leash; Flora hungry |
| 3 | **Appointments** | your kid's game at 6 on the shared family calendar, so leave by 5:35 (travel time included) |
| 4 | **Your deadlines** | meetings, tasks due |
| — | everything else only rings | new mail, a finished task |

**Coming back in:** you see what changed while you were out, the chore you just did counts toward your street cred (§2.6), and Hal offers the next small step.

4. **Put in night-shift orders.** Overnight work must be ordered by closing. A few desks run on the frontier model (limited by the plan's usage caps); a **local night crew** on open-weight models takes the grind work at no fee. Deciding what gets the few frontier desks is the day's real choice.
5. **Closing time (10:30 pm).** Last call at 10:15. At 10:30 the estate goes to night and Hal says goodnight. Closing time is a rule **you** set ("you asked me to send you home"), not a lock the software imposes: you can stay, but never silently. Your twin walks you to the door and asks once; if you stay, the estate stays at night and the day isn't won, with no other penalty. Agents keep working, and their results wait as blueprints at breakfast. Nobody can ask you overnight, so a stuck agent parks its work and frees its desk. Bedtime is declared data (`{:bedtime "22:30"}`), so a night-shift nurse's estate closes at 9 am instead.

**Pressure ceiling.** A missed chore is one steady "overdue" state that never escalates. Agents waiting on you or blocked release their desks, so nothing deadlocks while you're away.


---

### 2.4 The world

A single estate on one screen with zoom, medium-poly, floating on a dark grid.

| Place | What it represents | Truth comes from |
|---|---|---|
| **Lobby** and **your kitchen** | home; where agents bring their questions | — |
| **Workshops** (one per project) | a code repo. Desks are open tasks; shelves fill as work lands. | GitHub |
| **The canal** | Flora's home, styled after the Xochimilco canals (slow water, a dock, a worm jar) | Flora's state file |
| **Porch** | Biscuit's spot; the leash hangs by the door | Biscuit's chores |
| **Mailbox** | the inbox needing attention; overflows when it does | Gmail |
| **Notice board** | today's calendar and reminders (the morning brief) | Calendar, Reminders |
| **Phone booth** | the hardline: where the real world calls in and where you jack out | SMS, iMessage |
| **Front office** | your business: what sold, what's owed, what needs your signature | Stripe |
| **The Architect's room** | the build system itself: a wall of monitors, one per live agent session across your machines; each screen shows that session's task, status, and machine | the agent multiplexer (Herdr) and its machine list |


---

### 2.5 Mirrors: your life as data

Anything the game reflects from real life is declared in data, never coded. A mirror is a creature, plant, vehicle, business, or counter with a state source, rules, and chores, and **chores belong to people**:

```clojure
{:id :flora   :kind :creature :model :axolotl :home :canal
 :state  {:source "~/.hermes/state/worms.json" :fields {:stock :count :last-fed :last_feed}}
 :rules  [{:when [:due? :feed] :say "I'm hungry"} {:when [:<= :stock 2] :sign "2 worms left!"}]
 :chores [{:id :feed :every "2d" :at "17:00" :who :me :consumes [:stock 1]}]}

{:id :biscuit :kind :creature :model :dog :home :porch :aliases ["Bisco"]
 :chores [{:id :walk :every "1d" :who :me} {:id :feed :every "1d" :who :spouse}]}

{:id :lotect  :kind :business :home :front-office :state {:source :payments}
 :chores [{:id :verify-account :once true :needs :signature}
          {:id :cancel-trial  :by "2026-11-14" :needs :signature}]}
```

- **Add your own creatures: live, virtual, or remembered.** Any pet is a mirror you declare, with a mode:
  - `:live` (the default) mirrors the real animal's state and chores.
  - `:virtual` keeps a creature on the estate with no needs: it never gets hungry and never jacks you out. For when you want the company, not the chores.
  - `:memorial` keeps pets who have died: every dog you've ever had can still lie in the sun on the porch. Memorial pets need nothing, never speak as if they were alive, and only you can add one. When a live pet dies, only you move it to memorial: its chores stop counting toward "your own chores done", and your twin says so kindly and truthfully, never "I'm hungry" from an animal that's gone.

  ```clojure
  {:id :rex :kind :creature :model :dog :home :porch :mode :memorial :years "2004–2016"
   :personality "Barked at every truck. Followed me room to room. Slept in the sunniest spot, snored."}
  ```

  **Personality** works for any creature, in any mode: describe the animal in your own words and that's how it behaves on the estate. An agent reads the description **once**, when you add it, and turns it into behavior settings you approve like any blueprint (how often it barks, whether it follows you, its favorite spot, how much energy it has). After that it runs on simple rules, with no model calls per frame. Barking, following, and snoring are behavior, not speech, so a memorial dog can still bark at the trucks.
- **Nicknames work:** "walked Bisco" and "Biscuit's walked" reach the same dog.
- **You're only reminded of (and jacked out for) your own chores.** Someone else's chore shows as the household being on it, never as your "!".
- **The business is a mirror too.** Pepper keeps it: a free trial about to bill you or a form to sign becomes a folder on her desk with a countdown.
  - *Possible to-do: the front desk.* Catch every message to the business's public channels (the Lotect Messenger page, texts from anyone but the player) without letting strangers steer an agent: a plain script logs them, a Clerk with **no tools** sorts important from noise, and the morning brief gets one line ("Lotect page: 1 customer asking about the release date; 3 spam skipped"). Agents never reply to strangers on their own; the player decides, within Messenger's 24-hour reply window.
- **A second example from the same code path,** so nothing is specific to one life: a horse in a barn with a hay counter; a roommate who waters the shared plant; a farm stand selling eggs through Square.
- **Household members are not players or agents** and get no twin unless they want one. They can ring the hardline, and their shared-calendar events can be your appointments. The game sees only what they share, and anything sent to them needs your approval. *Someday (multiplayer, §7.7):* each household member can bring their own twin, and the twins meet, overlap, trade chores, and negotiate the shared calendar, with every human approving.
- No mirror is required; an empty estate is still a valid estate.


---

## 3. AI Architecture

**The twin rule.** Every character is the in-world body of something real. Your agent is your digital twin: for the author that's Hal, who *is* his actual bot, not a character played by a model: his status bubble is his live session, his memory is the bot's memory, and when the bot learns a skill, Hal gains an ability on the estate. Workers are real coding sessions. Flora and Biscuit are real animals. A twin shows only what its counterpart's real state says; in demo mode the counterpart is a clearly pretend connection, never a fake passed off as real. Virtual and memorial creatures (§2.5) are the declared exception: they show no needs, so nothing pretends to be live.

**A character means a separate identity.** A character gets its own body only if it has its own account, credentials, and memory. Scheduled jobs that run *as* Hal are his **errands**, not extra characters: at 8:15 Hal walks to the notice board and pins the brief; when a text arrives, he answers the phone booth.


### 3.1 What each runtime agent does, through its effect on play

In my game, each agent takes an input and produces an output that the player sees as a visible effect:

| Agent | Takes (input) | Produces (output) | Player sees (effect) |
|---|---|---|---|
| **Your agent: Hal** (your digital twin) | what you say to him in a bubble, by text, or in chat | a one-line confirmation and a task handed to a worker | Hal nods, pins a card to the notice board, and a worker walks out of the lobby toward a workshop desk |
| ↳ Hal's errand: **morning brief** | calendar, mail, reminders | one short morning summary | Hal pins the day's card on the notice board at 8:15 |
| ↳ Hal's errand: **keeper** | a mirror's state and chores | events like `due :feed` and `stock 2` | an "I'm hungry" bubble over Flora and a "2 worms left!" sign on the jar |
| ↳ Hal's errand: **phone booth** | texts you send the bot | a reminder, note, answer, or chore-done | the phone rings and Hal answers; your text becomes a card on the board or Flora's snap |
| **Worker** (one per task, wearing a crew persona) | a task plus its repo | a branch, commits, and a blueprint | typing at a desk with a progress glow, then carrying a finished book to the shelf |
| **Clerk** (mail reader) | the unread inbox | a count plus a short triage summary | the mailbox fills and overflows; clicking it shows the summary |
| **Pepper** (business manager) | the business's payments account and business chores | a short ledger and a list of what needs your signature | Pepper at her front-office desk; a folder appears when something needs you, and she walks it over |
| **Environment Artist** (a worker on the game itself) | "make the river nicer" | a shader or model change to the game | the river visibly changes after hot reload |
| **Night crew** (local models) | overnight grind orders | variants, scraped assets, copy, test runs | workshop lights on at night; a stack of blueprints at breakfast |
| **A second personal agent** (the author's: Muse, see [`MUSE.md`](MUSE.md)) | your stated preferences | answers to easy questions and errands you approved | the kitchen is kept while you're out; a "while you were out…" log when you're back |

**How agents reach the player.** A few explicit events carry the loop in both directions:

```clojure
{:type :agent-event :agent "w-3" :kind :question :text "Rocks: round or jagged?"}  ; agent → you ("!")
{:type :answer      :agent "w-3" :text "jagged"}                                   ; you → agent
{:event :chore-done :mirror :flora :chore :feed :via :sms}                         ; real life → world
{:event :jack-out   :reason :appointment :who :household :text "Game at 6"}        ; life → you
```

An agent raises "!" only by calling an explicit `ask_player` tool, so a "!" always means a real question. Every event carries its source (agent, session, machine); the bridge refuses events from unknown processes, and it marks an agent **stalled** on its own when it stops reporting (for example when its usage cap runs out), so nothing shows busy forever and its keycards come back. Notices the game itself makes (a trust suggestion, the weekly card) look different from an agent's "!" and arrive over a channel no agent can reach.


### 3.2 Guardrails that shape play

- **Purchases and messages to other people always need your approval.** Pepper reads, drafts, and reminds; you sign. Identity documents and bank numbers go straight from you to the provider.
- **A reader with no keys.** The agent that reads untrusted content (mail, web pages) holds no credentials and returns only counts and summaries; agents with keys never read raw untrusted content. That's why the Clerk is its own character. Its summaries go to you, never into an agent holding keycards.
- **Your agent** (and any second agent you run) never spends money, messages another person, or changes trust settings without you, and it acts on your stated preferences, not guesses. As your coach it uses your own goals and words ("You said three workouts a week. One today?"), never shames, never compares, and points to real people if a slump looks like more than a slump.
- **The game learns your life only from what you connect** (receipts, calendar, orders), never from location tracking, and every new building is proposed, never sprung on you.

---


---

# Source: resources/estate.edn

```
{:hud {:mirrors [:flora]                 ; mirrors whose status-line goes on the HUD, in order
       :keys    {"f" [:flora :feed]}}    ; lower-case key -> [mirror-id chore-id]
 ;; Sounds placed in the world. Each point is an emitter [x y z gain] in the
 ;; kit's world metres: along the creek's centerline at its water surface,
 ;; loudest at the three cascades, a quiet one over the still pool.
 :sounds
 [{:id :creek :loop "/assets/audio/brook.mp3" :ref-distance 2.5 :rolloff 1.6
   :points [[ 1.92 2.40 -36.2 0.7]    ; inlet
            [ 1.92 2.40 -35.0 0.7]    ; upstream, under the stone vault
            [ 0.42 2.40 -30.0 0.6]
            [-1.08 2.40 -25.0 0.6]
            [-0.79 2.22 -21.5 0.6]
            [-0.36 1.85 -18.8 1.0]    ; cascade 1
            [-0.27 1.45 -15.7 0.6]
            [-0.64 1.07 -12.6 1.0]    ; cascade 2
            [-1.25 0.71  -9.8 0.6]
            [-1.91 0.37  -7.1 1.0]    ; cascade 3
            [-2.49 0.01  -4.2 0.8]    ; mouth into the pool
            [ 0.30 0.00   4.5 0.25]]}] ; the pool (still water)
 ;; Display scale (standing rule): creatures and crew are drawn big enough to
 ;; see from the kit's camera views without zooming in, about the size of a
 ;; mid-size rock (~1 m). Real size doesn't matter; proportions do. Author a
 ;; look at real proportions and set the item's :scale to reach display size.
 ;; Look parts: {:shape :size [sx sy sz] :at [dx dy dz] :rot [rx ry rz] :color};
 ;; :at is the part's centre from the item's base (+x forward, y up), :rot in
 ;; degrees. :yaw (degrees about y) turns the item; +90 points +x at world -z.
 :mirrors
 [{:id :flora :name "Flora" :kind :creature :model :axolotl :home :canal
   :state  {:source :sample :stock 15 :last-fed nil}
   :rules  [{:when [:due? :feed]  :say  "I'm hungry"}
            {:when [:<= :stock 2] :sign "Worms left: {stock}"}]
   :chores [{:id :feed :every "2d" :at "17:00" :who :me
             :consumes [:stock 1] :done-at :last-fed}]
   ;; Flora's :placed
   :placed [{:id :body :at [-0.9 1.6 -15.7] :scale 6 :yaw 0 :label :say   ; real axolotl, 0.26 m nose to tail, drawn ~1.5 m (a mid-size rock); in open water, clear of the foreground rock; side-on to the creek camera, facing her jar; resting on the surface (~1.55) so the water shader doesn't hide her
             :look {:parts
                    [{:shape :capsule :size [0.12 0.032 0.04]  :at [0.02 0.016 0]   :color :pink}   ; body
                     {:shape :sphere  :size [0.05 0.03 0.056]  :at [0.1 0.017 0]    :color :pink}   ; head, wider than the body
                     {:shape :box     :size [0.09 0.024 0.006] :at [-0.085 0.016 0] :color :pink}   ; tail, flattened sideways
                     ;; gills: three stalks a side, behind the head, fanning up and back
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.082 0.036  0.022] :rot [ 30 0 35] :color :gill}
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.086 0.031  0.027] :rot [ 55 0 25] :color :gill}
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.09  0.026  0.029] :rot [ 80 0 15] :color :gill}
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.082 0.036 -0.022] :rot [-30 0 35] :color :gill}
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.086 0.031 -0.027] :rot [-55 0 25] :color :gill}
                     {:shape :cone :size [0.006 0.024 0.006] :at [0.09  0.026 -0.029] :rot [-80 0 15] :color :gill}
                     ;; legs: four stubs, splayed out and down
                     {:shape :cylinder :size [0.008 0.016 0.008] :at [ 0.06 0.006  0.022] :rot [-60 0 0] :color :pink}
                     {:shape :cylinder :size [0.008 0.016 0.008] :at [ 0.06 0.006 -0.022] :rot [ 60 0 0] :color :pink}
                     {:shape :cylinder :size [0.008 0.016 0.008] :at [-0.03 0.006  0.022] :rot [-60 0 0] :color :pink}
                     {:shape :cylinder :size [0.008 0.016 0.008] :at [-0.03 0.006 -0.022] :rot [ 60 0 0] :color :pink}
                     ;; eyes
                     {:shape :sphere :size [0.008 0.008 0.008] :at [0.112 0.026  0.021] :color :eye}
                     {:shape :sphere :size [0.008 0.008 0.008] :at [0.112 0.026 -0.021] :color :eye}]}}
            {:id :jar :at [1.4 1.81 -15.7] :scale 4 :label :sign :idle "Worms: {stock}"   ; on the bank rocks (top 1.81); real jar 0.124 m, drawn ~0.5 m
             :look {:parts
                    [{:shape :cylinder :size [0.08 0.11 0.08]   :at [0 0.055 0] :color :glass}  ; jar
                     {:shape :cylinder :size [0.088 0.014 0.088] :at [0 0.117 0] :color :tin}]}}]} ; lid

  {:id :juniper :name "Juniper" :kind :creature :model :horse :home :barn
   :state  {:source :sample :bales 6 :last-hayed nil}
   :rules  [{:when [:due? :hay]   :say  "Hay, please?"}
            {:when [:<= :bales 3] :sign "Bales left: {bales}"}]
   :chores [{:id :hay :every "12h" :at "07:00" :who :me
             :consumes [:bales 1] :done-at :last-hayed}]
   ;; Juniper's :placed
   :placed [{:id :body :at [-5.0 2.5 -10.0] :yaw 90 :label :say   ; west bank; long axis along world z, side-on to the creek camera
             :look {:parts
                    [{:shape :capsule  :size [1.3 0.62 0.6]   :at [0 1.2 0]                     :color :brown}  ; barrel
                     {:shape :box      :size [0.3 0.8 0.32]   :at [0.72 1.62 0]  :rot [0 0 -35] :color :brown}  ; neck, leaning forward
                     {:shape :box      :size [0.55 0.26 0.24] :at [1.1 1.9 0]    :rot [0 0 -35] :color :brown}  ; head, nose down
                     {:shape :cone     :size [0.06 0.14 0.05] :at [0.95 2.12  0.07]             :color :brown}  ; ears
                     {:shape :cone     :size [0.06 0.14 0.05] :at [0.95 2.12 -0.07]             :color :brown}
                     {:shape :sphere   :size [0.05 0.05 0.05] :at [1.15 1.98  0.12]             :color :eye}
                     {:shape :sphere   :size [0.05 0.05 0.05] :at [1.15 1.98 -0.12]             :color :eye}
                     {:shape :box      :size [0.08 0.75 0.06] :at [0.62 1.68 0]  :rot [0 0 -35] :color :mane}   ; mane, along the neck
                     {:shape :cylinder :size [0.1 0.7 0.1]    :at [-0.82 1.1 0]  :rot [0 0 -20] :color :mane}   ; tail
                     {:shape :cylinder :size [0.14 0.95 0.14] :at [ 0.48 0.475  0.18]           :color :brown}  ; legs
                     {:shape :cylinder :size [0.14 0.95 0.14] :at [ 0.48 0.475 -0.18]           :color :brown}
                     {:shape :cylinder :size [0.14 0.95 0.14] :at [-0.48 0.475  0.18]           :color :brown}
                     {:shape :cylinder :size [0.14 0.95 0.14] :at [-0.48 0.475 -0.18]           :color :brown}]}}]}]}

```

===

# Your job: Mirror bubbles and signs (creatures that mirror real life)

Mirrors are real-life creatures declared as data (GDD §2.5). Over a creature floats a
short speech bubble (:say) and on its supply sits a sign (:sign); the keeper errand (§3.1)
turns its state into these. Write 3 to 5 different lines for each key below. Bubbles are
what the creature 'says' in the game's light way (an axolotl doesn't talk, but the GDD's
Flora says "I'm hungry"); signs are hand-lettered labels on the jar or the hay pile.

Mirrors in the sample estate (see resources/estate.edn and §2.5):
- :flora    the axolotl in the canal, eats worms every 2 days at 5 pm; state {stock} = worms in the jar
  keys: :hungry (bubble, feeding is due) :fed (bubble, just fed) :low (sign, 2 or fewer left, {stock})
        :stock-sign (the jar's everyday sign, {stock})
- :juniper  the horse in the barn, hay every 12 hours; state {bales}
  keys: :hungry :fed :low ({bales}) :stock-sign ({bales})
- :biscuit  the dog on the porch (nickname Bisco), walked daily, his leash hangs by the door
  keys: :walk-due (bubble, he's at the door) :walked (bubble, after the walk) :porch (idle bubble)

Modes (§2.5), for any creature:
- :memorial  a pet who died. Needs nothing, NEVER speaks as if alive.
  keys: :plaque (the small plaque by its sunny spot; {name}, {years}; not in the pet's voice)
        :hal-note (what Hal says when the player moves a pet to memorial; {name}; kind and truthful)
- :virtual   a creature kept for company, no needs, never calls the player out.
  keys: :idle (its bubble; {name}; no needs, no chores)

## Exact shape

{:mirrors {:flora   {:hungry [..] :fed [..] :low [..] :stock-sign [..]}
           :juniper {:hungry [..] :fed [..] :low [..] :stock-sign [..]}
           :biscuit {:walk-due [..] :walked [..] :porch [..]}}
 :modes   {:memorial {:plaque [..] :hal-note [..]}
           :virtual  {:idle [..]}}}
Each [..] is a vector of 3-5 strings.

## Hard rules (a script checks these)

- exactly the mirrors, modes and keys above; 3-5 distinct lines each
- bubbles and signs at most 32 characters; :plaque at most 60; :hal-note at most 110; :idle at most 40
- placeholders only where listed: {stock} (flora :low/:stock-sign), {bales} (juniper :low/:stock-sign), {name} and {years} (memorial), {name} (virtual :idle)
- memorial and virtual lines name no needs or chores (hungry, feed, fed, worms, hay, walk, leash, due)
- :plaque is never in the pet's own voice (no I, I'm, me, my)
- no XP, scores, streaks, shaming; no film lines
- say only what the event carries and the GDD guarantees: never claim state the event doesn't tell you (what changed, whether work was saved, whether a program is sample or real, the time of day, the weather)
- a count is a label, not a sentence subject: "Worms: {stock}", never "{stock} worms" (the count can be 0 or 1)

===

# Revise your previous draft

Your previous draft:

{:mirrors
 {:flora
  {:hungry
   ["I'm hungry"
    "Any worms going?"
    "Feeding time, right?"
    "Is it five already?"
    "Worms would be nice"],
   :fed
   ["Mm, worms"
    "That hit the spot"
    "Thanks for the worms"
    "Much better"
    "Full now"],
   :low
   ["Worms left: {stock}"
    "Low worms: {stock}"
    "Worms running low: {stock}"
    "Jar running low: {stock}"
    "Low: {stock} left"],
   :stock-sign
   ["Worms: {stock}"
    "Worm jar: {stock}"
    "Jar count: {stock}"
    "Worms in jar: {stock}"
    "Worm stock: {stock}"]},
  :juniper
  {:hungry
   ["Hay, please?"
    "Any hay going?"
    "Hay time?"
    "Hungry for hay"
    "Feels like hay time"],
   :fed
   ["Mm, hay"
    "That's the stuff"
    "Thanks for the hay"
    "Good hay, that"
    "Satisfied now"],
   :low
   ["Bales left: {bales}"
    "Low bales: {bales}"
    "Hay running low: {bales}"
    "Barn running low: {bales}"
    "Low: {bales} left"],
   :stock-sign
   ["Bales: {bales}"
    "Hay bales: {bales}"
    "Bale count: {bales}"
    "Hay stock: {bales}"
    "Bales in barn: {bales}"]},
  :biscuit
  {:walk-due
   ["Walk time?"
    "Leash is right there"
    "Ready for a walk"
    "Time for a walk?"
    "Walk, please?"],
   :walked
   ["That was a good one"
    "Tail's still wagging"
    "Good walk"
    "Needed that"
    "Back and happy"],
   :porch
   ["Just watching the yard"
    "Guarding the porch"
    "Napping in the sun"
    "Keeping an eye out"
    "Resting up"]}},
 :modes
 {:memorial
  {:plaque
   ["{name}, {years}"
    "In memory of {name}, {years}"
    "{name} — {years}"
    "Remembered here: {name}, {years}"
    "{name} rested here, {years}"],
   :hal-note
   ["{name}'s moved to memorial. No needs, no chores — just a place to remember them."
    "Done — {name}'s a memorial now. Nothing to track, just memory."
    "{name}'s in memorial now. No needs anymore — just a spot to remember them."
    "You've moved {name} to memorial. Their chores are off your list now, and their spot stays theirs."
    "{name}'s a memorial now — no needs left, just a place to remember them here."]},
  :virtual
  {:idle
   ["{name}'s just here for company"
    "{name} naps in the sun"
    "{name}'s keeping you company"
    "{name} watches the yard"
    "Just {name}, enjoying the day"]}}}


The critic's blockers on this draft (fix every one; keep what it didn't flag):
- :modes :memorial :hal-note, line 2 'Done — {name}'s a memorial now. Nothing to track, just memory.': Hal answers a pet's death with 'Done' and 'Nothing to track'. That is butler task-confirmation, not the kind, truthful note §2.5 requires. It should not ship for a grief moment. [§2.5 (memorial: your twin says so kindly and truthfully), §1 cast (twin is a companion)] fix: Replace with something like 'I'm sorry about {name}. Their chores are off your list, and the sunny spot stays theirs.'

Blockers the critic raised on EARLIER drafts (already fixed; don't bring any back):
- :flora :hungry "My jar's looking empty": The bubble fires when feeding is due, not when stock is low. The sample estate starts with 15 worms, so Flora would claim an empty jar while the jar's own sign reads "Worms: 15". The bubble and the sign would contradict each other, and the bubble would state something the reported count doesn't say.
- :modes :memorial :hal-note "Done — {name}'s on the porch now as a memorial..." (also the "in the sun" / "sunny spot" lines): The memorial note is a template for any creature, but it puts every memorial pet on the porch. A memorial Flora stays at the canal, so for her the line is false. The sun lines carry the same assumption, which is softer but still wrong for a creature that lives in water.
- :modes :memorial :hal-note, line 4 "I've moved {name} to memorial...": Hal says he moved the pet. The GDD says only the player can add a memorial pet or move a live pet to memorial. The twin reports what the player did; it doesn't do it. A kind note that is not truthful breaks the memorial rule.

Return the complete revised EDN.