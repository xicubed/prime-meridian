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
- **Les jumeaux.** You and your twin are *les jumeaux*, and your twin is *ton jumeau* or *ta jumelle*, matching your sex (it's your twin), which onboarding asks once along with the twin's name. Both live in data (`:jumeaux` in `estate.edn`): the author's twin is Hal, and nothing in the code knows that name.
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
{:hud {:mirrors [:flora]}                 ; mirrors whose status-line goes on the HUD, in order
 :jumeaux {:player {:name "Michael" :sex :m}   ; les jumeaux: the player and their twin; onboarding asks (TASKS O-1)
           :twin   {:name "Hal"}}              ; the twin's word follows the player's :sex (:m jumeau, :f jumelle)
 :crew-talk {:surface-after-s 60   ; a prompt the twin hasn't answered after this long goes to the player
             :answer-after-s 5}    ; bb crew-fielder waits this long, then answers a routine prompt
 ;; The twin without a bridge (GDD §2.1, §4.6; the demo build, or a dev build with
 ;; bb crew-bridge off): his say… box stays open, and he answers that he can't hear
 ;; from here. In the demo he also speaks a SAMPLE line now and then (bb hal-say is
 ;; what says his real ones), so the estate has a voice with nothing real behind it.
 :twin-talk {:no-bridge "Out here I only hear you through the hardline. In your own game this box reaches me where I live: your agent's chat."
             :sample-every-s 45
             :sample-says ["Mail's quiet. Two newsletters, nothing for you."
                           "The worker finished the rock. I checked it: round enough."
                           "Flora's fed. Worm count's holding."
                           "Nothing's on fire. Walk the creek if you like."
                           "Calendar's clear until tonight."]}
 ;; Closing time (GDD §2.3, §4.8): a rule the player sets, not a lock the
 ;; software imposes ("you asked me to send you home"). Last call shows 15
 ;; minutes earlier; at bedtime the estate goes to night and the twin says
 ;; goodnight (pm.closing). Declared data, so a night-shift nurse's estate
 ;; closes at 9am instead -- just a different string here.
 :bedtime "22:30"
 ;; The sky follows the clock (pm.sky): local times the scenery kit's own
 ;; lighting changes (its four states: :day :golden :blue :night). These are
 ;; roughly a November day in the northern US; a player elsewhere, or in
 ;; summer, writes their own. The kit's buttons still pick a sky by hand
 ;; until the next change here.
 :sky [["06:45" :blue] ["07:15" :golden] ["08:15" :day]
       ["16:00" :golden] ["16:45" :blue] ["17:30" :night]]
 ;; The soundtrack (pm.music): instrumental, made with Suno on Michael's
 ;; Premier plan (public/assets/audio/LICENSES.md). One track at a time,
 ;; crossfading: the Construct's theme, the dojo while sparring, then the sky's
 ;; hour picks. Night has no track of its own yet, so it keeps blue hour's.
 :music {:volume 0.3 :fade-s 4
         :tracks {:construct "/assets/audio/music/nuclear-vision.mp3"   ; class demo only: a remix (LICENSES.md)
                  :dojo      "/assets/audio/music/the-dojo.mp3"
                  :afternoon "/assets/audio/music/afternoon-in-the-simulation.mp3"
                  :golden    "/assets/audio/music/golden-hour-uplink.mp3"
                  :blue      "/assets/audio/music/blue-hour-signal.mp3"}
         :by-sky {:day :afternoon :golden :golden :blue :blue :night :blue}}
 ;; The one-time ask at bedtime (pm.closing/asking-to-stay?): its words.
 :stay-late-ask {:ask "Staying past closing?" :button "Stay" :leave "Close up"
                :sign "Closed for the night. Click the booth to come back in."}
 ;; The scenery kit's own night lights, as a built lamp's bulb (:glow) finds
 ;; and follows them: its warm lamps' colour and its cottage lantern's mesh.
 ;; A built lamp shines as bright as theirs, so it lights up at dusk with them.
 ;; SAMPLE or REAL, per object (ⓘ panel): what the twin says when a REAL
 ;; mirror's chore can't be recorded, since a real thing mustn't change only on screen.
 ;; The Construct (GDD §2.6, §2.7; pm.construct): the first five minutes for a new
 ;; player. The twin's lines are original, drafted for this scene (the film is only
 ;; the beat sheet). The rack lists your own agent first: it's chosen for you.
 :construct-script
 {:speaker  "your twin"     ; shown under the twin's name above each of its lines
  :sample   "SAMPLE"        ; the practice worker's tag
  :blueprint-who "Blueprint"
  :rock-labels ["now" "proposed"]   ; the two rocks side by side while you decide
  :start    "Jack in"   ; the first click is also what lets the browser play sound
  :voice    {:on "🔊 Voice on" :off "🔇 Voice off"}   ; the switch beside it (same as the 🔊 button)
  :opening  "This is the white room. Nothing in here is real yet. We load what you need, then we go make it real."
  :loaded   "Felt that? That's me, the real me, plugged in."
  :programs [{:id :own-agent :name "Your own agent"}
             {:id :mail :name "Mail"} {:id :calendar :name "Calendar"} {:id :github :name "GitHub"}
             {:id :blender :name "Blender"} {:id :stripe :name "Stripe"} {:id :notion :name "Notion"}
             {:id :maps :name "Maps"} {:id :smart-home :name "Smart home"}]
  ;; Beat 3, the sparring match (GDD §2.7): "the whole loop in miniature."
  ;; A sample-badged worker takes a practice job, gets stuck, raises a "!",
  ;; you answer, a blueprint of the rounder rock appears, and approving it
  ;; changes the rock -- five minutes in, you've played the whole game.
  :sparring {:worker     {:name "A Worker"}
             :job        "Try it on something small first: tell that worker to make this rock rounder."
             :working    "Rounding the practice rock..."
             :ask        "I can round it more, but I want your word before I touch it again. Rounder, like this?"
             :blueprint  "A rounder rock, same place."
             :done       "Approved. Watch the rock, not me."}
  ;; Beat 4, the jump program (GDD §2.3, §2.7, §4.8): the first pretend
  ;; jack-out. The twin shows the hardline and the one rule that matters,
  ;; the phone rings with a pretend chore, you jack out, and you jack back
  ;; in -- all of it sample, so nothing real is ever at stake here.
  :jump {:show "That's the hardline: the phone booth. One rule matters here -- when real life calls, you go."
         :ring "It's ringing: a pretend chore, the kettle's boiled over. Jack out and see what happens."
         :out  "You're out. The estate holds everything right where it is until you jack back in."
         :back "Nothing broke. Jack out, jack back in, and the work waited for you -- that's the whole trick."}
  ;; Beat 5, free your mind (GDD §2.7, §4.8): the Construct's last beat. The
  ;; white room dissolves into the half-barren estate -- only the buildings
  ;; for the programs loaded so far -- and the twin steps back.
  :free {:line "I can open the door. Walking through it is your part."}
  ;; what a click does at each interactive moment, shown under the line
  :hints {:ask "Yes, rounder" :blueprint "Approve" :ring "Jack out" :out "Jack back in"}   ; the buttons
  ;; where the camera looks as your agent's building (the workshop) lands in beat 5
  :land-camera {:target [-6.2 2.1 -3.5] :offset [3.8 2.3 4.1]}}
 ;; A due chore of yours is one of the day's "!"s (GDD §2.1): this mark leads the
 ;; creature's speech until you do it (click her, tick it in 💭, or text the twin).
 :needs-you-mark "!"
 :real-text {:no-bridge    "That one's real: start the bridge (bb crew-bridge) to record it."
             :not-recorded "Couldn't record that for real"}
 :kit-lights {:lamp-color "#ffd28a" :lantern-mesh "SM_Hiroshi_Cottage_Lantern"}
 ;; The juice meter (GDD §2.1, §4.4, §4.8): each AI login's usage window,
 ;; as the tokens the bridge has seen (:tokens-used) against the plan's
 ;; cap (:cap) -- pm.juice/estimate-juice turns the two into a 0-1 share
 ;; used -- and its own local refill time ("HH:MM" 24h, same
 ;; declared-clock convention as :bedtime). The HUD shows the estimated
 ;; share left and the refill time; the twin also warns once a login's
 ;; estimate crosses 80%. Sample data, like every connection with no real
 ;; version yet (:connections below) -- the bridge fills this for real
 ;; once it reports token counts.
 ;; SAMPLE: read off the clock (pm.juice/sample-login): 5-hour windows anchored at 14:00,
 ;; usage climbing to 83% by the end of each; `bb juice` writes the REAL one (state/juice.edn)
 ;; by asking an idle Claude member for /usage, and the game shows that while it's fresh.
 :logins {:claude {:name "Claude" :sample {:window-h 5 :anchor "14:00" :peak 0.83}}}
 :connections [:mail :phone :chores :payments]   ; connections with no real version yet (GDD §4.6): demo mode badges each SAMPLE
 :creature-spike {:flag "pony" :kind :horse :seed 1     ; ?pony[=<kind>][&seed=n]: MuJoCo creature spike (pony-spike-v0)
                  :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4} ; east bank, terrain 1.79 m (Hal probed); yaw in degrees
 :voices {:hal      {:rate 1.0 :pitch 0.9 :lang "en-US"}   ; Hal: even, a little low
          :creature {:rate 1.1 :pitch 1.6 :lang "en-US"}}  ; creatures: higher, quicker
 ;; Sounds placed in the world. Each point is an emitter [x y z gain] in the
 ;; kit's world metres: along the creek's centerline at its water surface,
 ;; loudest at the three cascades, a quiet one over the still pool.
 :sounds
 [{:id :creek :loop "/assets/audio/brook.mp3" :ref-distance 6 :rolloff 1.2
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
 :motion  {:bricks {:lib   "/vendor/three-motionbricks-0.2.0/index.mjs"
                     :rig   :soma
                     :model "/models/soma/"   ; local copy (gitignored public/models/soma/; from repalash.com/three-motionbricks/soma-model/)
                     :backend :webgpu}   ; :auto (WebGPU if a warm generation is fast enough), :webgpu or :wasm
            :ground ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]   ; meshes you can stand on (name prefixes)
            :speed  1.2                                          ; kinematic m/s
            :obstacles [{:at [-4.8 -8.2] :r 0.7} {:at [-4.3 -12.0] :r 0.6}]   ; the bank rocks
            :radius 0.3
            :horizon-s 2
            :display-scale 1.0   ; whole walker, both modes, and its avoidance radius (Hal tunes, ~1.4)
            :senses {:sight-m 8 :sight-deg 110   ; sees others within 8 m inside ±55° of where it faces
                     :hear-m 1.2                  ; hears others within 1.2 m (× their speed / walking pace, if faster)
                     :react-s 0.4                 ; must perceive someone this long, continuously, to notice them
                     :forget-s 1.5}               ; forgets them after this long unperceived
            :status-dots {:working 0xf2b134   ; Herdr's colours for the crew's states (dot before the name)
                          :blocked 0xe5484d   ; also pulses slowly: waiting for a human
                          :done    0x46a758
                          :idle    0x9aa0a6
                          :unknown 0x9aa0a6}
            :legs {:soma   {:left  ["LeftLeg" "LeftShin" "LeftFoot" "LeftToeBase"]
                            :right ["RightLeg" "RightShin" "RightFoot" "RightToeBase"]}
                   :mixamo {:left  ["mixamorig:LeftUpLeg" "mixamorig:LeftLeg" "mixamorig:LeftFoot" "mixamorig:LeftToeBase"]
                            :right ["mixamorig:RightUpLeg" "mixamorig:RightLeg" "mixamorig:RightFoot" "mixamorig:RightToeBase"]}}
            :feet {:drop-cap 0.35
                   :step-max 0.4}}
 ;; Wandering (pm.motion/wander-spots): walkable spots on the kit's terrain,
 ;; probed on a 1.5 m grid (ground first, or low grass; no water, rocks, bushes,
 ;; walls or roofs; no steep steps between neighbours; clear of placed things),
 ;; split by the creek so nobody wades across. A walker with :wander <area>
 ;; roams its area's spots within :radius of home, picking the next at random. (Hal)
 :wander {:radius 9
          :areas {:north-west [[-6.5 -8.5] [-6.5 -7.0] [-8.0 -5.5] [-6.5 -5.5] [-5.0 -5.5] [-8.0 -4.0] [-9.5 -2.5] [-8.0 -2.5] [-9.5 -1.0]]
                  :east       [[7.0 -13.0] [8.5 -13.0] [10.0 -13.0] [7.0 -11.5] [8.5 -11.5] [10.0 -11.5] [4.0 -10.0] [5.5 -10.0] [7.0 -10.0] [8.5 -10.0] [4.0 -8.5] [8.5 -8.5] [1.0 -7.0] [2.5 -7.0]]
                  :south      [[-3.5 -25.0] [-9.5 -23.5] [-6.5 -23.5] [-5.0 -23.5] [-8.0 -22.0] [-6.5 -22.0] [-5.0 -22.0] [-11.0 -20.5] [-9.5 -20.5] [-6.5 -20.5] [-5.0 -20.5] [-11.0 -19.0] [-8.0 -19.0] [-6.5 -19.0] [-5.0 -19.0] [-6.5 -17.5] [-6.5 -16.0] [-3.5 -16.0] [-6.5 -14.5] [-5.0 -14.5] [-3.5 -14.5]]}}
 ;; The crew key (pm.ui/render-crew-key!): who's on the estate; click a name
 ;; to fly to them (the camera sits at this offset and follows them).
 :crew-key {:title "Crew" :offset [3.6 2.4 2.4]}   ; x is flipped to the creek's side
 :walkers [{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde
            :wander :north-west
            :route [[-6.5 -6.5] [-6.0 -10.0] [-5.0 -14.0]] :pause-s 3
            :character {:file "/assets/characters/polybot.glb" :rig :mixamo}
            :key-color 0x6fbf5a}                                                   ; the crew key's swatch: Polybot is green
           {:id :le-testeur :name "Le Testeur" :agent :le-testeur
            :wander :south
            :route [[-5.0 -14.0] [-6.0 -10.0] [-6.5 -6.5]] :pause-s 5}            ; NVIDIA's mannequin, untinted (white)
           {:id :un-architecte :name "Un Architecte" :agent :un-architecte
            :wander :east
            :route [[6.0 -4.0] [4.0 -6.0] [3.5 -7.5]] :pause-s 8 :tint 0x4a7bd0}   ; east bank, north side: walks slowly, stops to think
           {:id :le-coureur :name "Le Coureur" :agent :le-coureur
            :wander :east
            :route [[3.0 -8.5] [5.0 -10.0] [3.0 -12.0] [2.2 -10.0]] :loop true
            :speed 1.9 :pause-s 1 :tint 0xe8833a}                                 ; east bank, south side: a longer, quicker loop
           {:id :le-critique :name "Le Critique" :agent :le-critique
            :wander :north-west
            :route [[-8.0 -4.0] [-7.2 -5.2]] :pause-s 6 :face [-6.0 -10.0]
            :tint 0x8e5cc4}                                                       ; woods' edge, north end of the west bank, watching the pair
           {:id :le-docteur :name "Le Docteur" :agent :le-docteur
            :wander :south
            :route [[-6.0 -17.5] [-6.0 -20.0]] :pause-s 6 :tint 0x2f9e8f}
           ;; Hal, the twin: the player's own bot (GDD §2.1), on the estate. The
           ;; mannequin inverted: black body, white joints. Posted by the phone
           ;; booth and mailbox (his errands start there), roaming the east bank.
           {:id :hal :name "Hal" :agent :hal :wander :east
            :route [[2.5 -7.0] [4.0 -8.5]] :pause-s 5
            :colors {:body 0x1b1b1f :joints 0xf4f2ee}}]       ; south plateau by Flora; the rock at [-5 -17] is not walkable
 ;; The lobby, with your kitchen (GDD §2.4, §4.8): home base, where agents
 ;; bring their questions. It's the kit's cottage (the estate's one real
 ;; house, upstream on raised ground): just a signpost at its steps, probed
 ;; 6.18 m. (Hal; the Director chose the cottage over a new building.)
 :lobby {:at [2.7 6.18 -23.6] :yaw 0
         :text "Lobby & kitchen — bring your questions here"
         :look {:parts
                [{:shape :box :size [0.1 1.4 0.1]   :at [0 0.7 0]  :color :brown}     ; post
                 {:shape :box :size [0.9 0.4 0.06]  :at [0 1.3 0]  :color :tin}]}}    ; board
 ;; The mailbox (GDD §2.4): the inbox needing attention; overflows when it
 ;; does. Backed by the sample inbox, me@acme.com (GDD §4.6), with no
 ;; accounts connected — pm.plan/mailbox-item reads its unread count from
 ;; there, never from :state here. East bank, near the creature-spike's
 ;; probed terrain height (1.79 m).
 :mailbox {:at [5.2 1.79 -7.3] :yaw 0
           :look {:parts
                  [{:shape :cylinder :size [0.08 0.9 0.08]  :at [0 0.45 0]      :color :brown}      ; post
                   {:shape :box      :size [0.16 0.18 0.34] :at [0 0.99 0]      :color :stand-in}   ; the box
                   {:shape :box      :size [0.03 0.1 0.03]  :at [0.08 1.0 0.14] :rot [0 0 25] :color :gold}]}} ; flag up
 ;; The card while you're jacked out at the booth (pm.ui/show-jack-out!)
 :jack-card {:who "The phone booth rang · you're jacked out"
             :note "Real life comes first. The estate holds everything right where it is until you jack back in."}
 ;; The inbox the mailbox opens (pm.ui/show-mail!): its words
 :mail-panel {:title "Mailbox · me@acme.com" :close "Close" :sample "SAMPLE"}
 ;; The phone booth (GDD §2.3, §4.8): the hardline. When a jack-out event
 ;; rings it, its sign (pm.plan/phone-booth-item) shows the reason while the
 ;; estate is frozen (pm.world/jack-out-text); empty otherwise. East bank,
 ;; near the mailbox, terrain probed at 1.71 m, clear of Un Architecte's route (Hal).
 :phone-booth {:at [4.9 1.71 -6.6] :yaw 0
               :look {:parts
                      [{:shape :box :size [0.9 2.0 0.9]   :at [0 1.0   0]    :color :booth-red}   ; body
                       {:shape :box :size [1.0 0.15 1.0]  :at [0 2.075 0]    :color :booth-red}   ; roof
                       {:shape :box :size [0.7 1.5 0.05]  :at [0 1.1 0.43]   :color :glass}]}}    ; glass panel
 ;; The workshop (GDD §2.4, §4.8): one per project, standing for a code
 ;; repo -- this one stands for the game's own. Desks are open tasks;
 ;; shelves fill as work lands. Open-air, at the edge of the west-bank
 ;; clearing (the only open ground there is), probed 1.63-1.79 m, about 2 m
 ;; off the walker route that crosses the clearing. (Hal)
 :workshop {:at [-6.2 1.69 -3.5] :yaw 0
            :text "Workshop — this repo. Desks: open tasks. Shelves: landed work."
            :look {:parts
                   [{:shape :box :size [0.3 1.2 2.0]   :at [-0.6 0.6 0]    :color :tin}      ; shelves
                    {:shape :box :size [0.5 0.46 0.8]  :at [0.25 0.23 -0.55] :color :brown}  ; desk 1
                    {:shape :box :size [0.5 0.46 0.8]  :at [0.25 0.23 0.55]  :color :brown}]}} ; desk 2
 ;; Blueprints (GDD §2.1, §4.5, §4.8): an agent-made change first shows in
 ;; place as a ghost with a one-line summary, before the player approves it.
 ;; Approving one that can't become real geometry (pm.blueprint/breaks-build?)
 ;; never reaches the live world -- pm.core/apply-approved! reverts to the
 ;; last working build instead, so the lamp post below always bounces back
 ;; on approval, no matter what the player does to it. Demo mode until real
 ;; agents propose these: a bench by the mailbox, east bank, clear of Un
 ;; Architecte's route; a lamp post with a generation-gone-wrong shape.
 :blueprint-text {:hint      "click to build · right-click to dismiss"                ; shown on every pending ghost's sign
                  :trial     "trial · click to make it real · right-click to undo"    ; a build is a trial: gone on reload
                  :real      "Made real. It stays."                                   ; twin's caption once it's recorded
                  :no-bridge "Start the bridge (bb crew-bridge) to make it real."     ; no bridge, nothing real changes
                  :failed    "that one won't build, so the estate stays as it was"}  ; after a refused approval
 :blueprints [{:id :bench-by-mailbox :at [4.4 0.86 -4.6] :yaw 0   ; open ground north of the booth, probed 0.86 m (Hal)
               :summary "proposed: a bench by the mailbox"
               :look {:parts
                      [{:shape :box :size [1.1 0.1 0.4]  :at [0 0.45 0] :color :brown}    ; seat
                       {:shape :box :size [0.08 0.45 0.08] :at [ 0.45 0.225 0.15] :color :brown}  ; legs
                       {:shape :box :size [0.08 0.45 0.08] :at [ 0.45 0.225 -0.15] :color :brown}
                       {:shape :box :size [0.08 0.45 0.08] :at [-0.45 0.225 0.15] :color :brown}
                       {:shape :box :size [0.08 0.45 0.08] :at [-0.45 0.225 -0.15] :color :brown}]}}
              {:id :lamp-post-by-canal :at [3.8 0.63 -5.0] :yaw 0  ; probed 0.63 m (Hal)
               :summary "proposed: a lamp post by the canal"
               :look {:parts [{:shape :box :size [0.3 2.4 0.3] :at [0 1.2 0] :color :tin}            ; the post: drawable and thick enough to click
                              {:shape :cone :size [0.7 0.4 0.7] :at [0 2.75 0] :color :tin}          ; the shade: a big click target
                              {:shape :sphere :size [0.3 0.3 0.3] :at [0 2.45 0] :color :gold :glow true}]}}]   ; the bulb: lights up like the kit's lamps once built
 ;; Display scale (standing rule): creatures and crew are drawn big enough to
 ;; see from the kit's camera views without zooming in, about the size of a
 ;; mid-size rock (~1 m). Real size doesn't matter; proportions do. Author a
 ;; look at real proportions and set the item's :scale to reach display size.
 ;; Look parts: {:shape :size [sx sy sz] :at [dx dy dz] :rot [rx ry rz] :color};
 ;; :at is the part's centre from the item's base (+x forward, y up), :rot in
 ;; degrees. :yaw (degrees about y) turns the item; +90 points +x at world -z.
 ;; :say/:sign/:idle: a string filled from :state, or a keyword naming this mirror's line in resources/content/mirror-lines.edn
 :mirrors
 [{:id :flora :name "Flora" :kind :creature :model :axolotl :home :canal
   :state  {:source :sample :stock 15 :last-fed nil}
   :rules  [{:when [:due? :feed]  :say  :hungry}
            {:when [:<= :stock 2] :sign :low}]
   ;; :fx :snap marks the chore whose chore-done plays the signature moment
   ;; on screen (GDD §2.2, §4.8): worm drop, lunge, bubble pop, jar -1.
   :chores [{:id :feed :every "2d" :at "17:00" :who :me
             :consumes [:stock 1] :done-at :last-fed :past "fed" :fx :snap}]
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
            {:id :jar :at [1.4 1.81 -15.7] :scale 4 :label :sign :idle :stock-sign   ; on the bank rocks (top 1.81); real jar 0.124 m, drawn ~0.5 m
             :footprint [[0 0 0.0875]]   ; 0.35 m in the world (scale 4)
             :look {:parts
                    [{:shape :cylinder :size [0.08 0.11 0.08]   :at [0 0.055 0] :color :glass}  ; jar
                     {:shape :cylinder :size [0.088 0.014 0.088] :at [0 0.117 0] :color :tin}]}}]} ; lid

  {:id :juniper :name "Juniper" :kind :creature :model :horse :home :barn
   :voice  {:rate 0.9 :pitch 0.8}   ; a horse sounds lower
   :state  {:source :sample :bales 6 :last-hayed nil}
   :rules  [{:when [:due? :hay]   :say  :hungry}
            {:when [:<= :bales 3] :sign :low}]
   :chores [{:id :hay :every "12h" :at "07:00" :who :me
             :consumes [:bales 1] :done-at :last-hayed :past "fed"}]
   ;; Juniper's :placed
   :placed [{:id :body :at [-5.0 2.5 -10.0] :yaw 90 :label :say   ; west bank; long axis along world z, side-on to the creek camera
             :footprint [[0.75 0 0.5] [0 0 0.5] [-0.6 0 0.45]]
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

# Source: content/STYLE.md (the rules; score against these and nothing else)

# Prime Meridian style guide (for the content loop)

*The aesthetic and narrative rules every generated line is scored against. Nothing here is new: each rule is lifted from [`docs/GDD.md`](../docs/GDD.md) and cites its section. The Evaluator (`content/pipeline.clj`) scores a draft on these rules, 0–10 each, with a reason; the Refiner fixes what scored low. If a rule isn't in the GDD, it isn't here.*

## A. Who's talking, and to whom

1. **The twin speaks to the player as a companion, not a butler and not a drill sergeant.** He is the player's own agent, "the one wow agent", their digital twin (§2.1, §3.1). Short lines, warm, wry, plain; he says what happened and what's next, never performs.
2. **The player is Neo**: an embodied person with a presence in a world of programs (§1 "The cast: you're Neo"). Lines address *you*; nobody talks about the player in the third person.
3. **Characters are the twins of something real** (§1, §2.5): a crew member is a real agent session, a creature is a real pet with real chores. Lines come from that reality (a worm count, a stalled session), never from invented lore.
4. **Film references are shorthand for feelings, never lines or characters** (§1: "*The Matrix* crossed with *TRON*"; "not the films' characters or lines"). "Jack in", "the Construct", "programs", "the hardline" are allowed vocabulary; quoting the films is not.
5. **The crew are French-named after Monet's circle** (Un Architecte, Le Testeur, Le Critique, Le Docteur, Le Coureur, Un Automate) (§4.2); use those names, never job titles like "the QA agent".

## B. What the world is

6. **A day is one round; closing time is a rule the player set** (§2.3): the twin's last call and goodnight are gentle reminders of the player's own bedtime, never a lock. Night arrives at closing time (§5).
7. **Jacking out is good**: real life rings (the phone booth, a household call, a creature, an appointment) and the estate waits; "nothing broke, the work waited for you" (§2.3, §2.1).
8. **Everything starts as a pretend version: SAMPLE until made real** (§4.6, §2.8). Sample content says so when it matters, and never pretends to be a real message, order or person. Pretend addresses only.
9. **Agents raise a "!" only by asking** (§3.1 `ask_player`), and approvals come grouped with a before → proposed view (§2.1 "Approve"). Lines about work in progress describe it as a blueprint waiting, not as something already changed.
10. **Limits are part of the world** (§4.4): usage caps and stalls are stated plainly ("Claude's used 83% till 7 PM"; "its desk is freed"), never as errors or apologies.

## C. What's never said

11. **No XP, levels, points, scores, streaks, high scores** (§1, §2.6: progression is street cred and your loadout, "no number on screen").
12. **Never shames, never compares, never judges food or bodies**; as coach the twin uses the player's own words and goals (§3.2). A missed chore is logged, not scolded.
13. **Purchases and messages to other people need the player's approval**; the twin reads, drafts, reminds, and the player signs (§3.2). Lines never claim to have sent or bought anything.
14. **Memorial and virtual creatures have no needs and never speak as if alive** (§2.5).
15. **No real person's private details** (§4.7; the repo is public): no phone numbers, emails, real addresses, real names outside the cast.

## D. Look and length (aesthetic)

16. **Bubble-length**: one or two sentences; a status plate carries at most ~64 characters, a caption ~120 (§2.1 "status bubbles"; `pm.scene/plate`).
17. **Medium-poly, warm, soft** (§5): the voice matches the diorama, cozy and specific, never corporate ("Your request has been processed") and never edgy.
18. **Variety is real variety**: 3–5 variants per event say different things, not the same line reworded (content README §4 "Usefulness").
19. **Placeholders exactly as declared** (`{name}`, `{chore}`, `{count}`...): a line reads correctly with any value ("{count} items", never "{count} things").

## How a draft is scored

Each rule family is a criterion; the Evaluator returns `{:score :reason}` for each, 0–10, and an overall score (the mean, weighted: C ×2, A ×1.5, B ×1, D ×1). A draft passes at **8.0 or better** with no criterion under 6; anything else goes to the Refiner with the reasons. Specificity is scored on A and B together: a stranger reading the lines should know they belong to Prime Meridian (the twin, the estate, the crew's names, SAMPLE/REAL, closing time), not to a generic cozy game.


===

# The Generator's draft: Mirror bubbles and signs (creatures that mirror real life)

What was asked:

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

The draft:

{:mirrors
 {:flora
  {:hungry
   ["I'm hungry."
    "Worms, please?"
    "Feeling peckish."
    "Jar's looking far away."
    "Any worms going spare?"],
   :fed
   ["Mm, worms."
    "That hit the spot."
    "Full and happy."
    "Worms: delivered."
    "Better now."],
   :low
   ["Worms: {stock} left"
    "Low stock: {stock}"
    "Jar getting low: {stock}"
    "Running low — {stock} left"
    "Worms: {stock}, getting low"],
   :stock-sign
   ["Worms: {stock}"
    "Worm jar: {stock}"
    "Jar count: {stock}"
    "Worms on hand: {stock}"
    "Stock: {stock}"]},
  :juniper
  {:hungry
   ["Hay, please?"
    "Manger's empty."
    "Getting hungry."
    "Hay time, no?"
    "Nosing the gate."],
   :fed
   ["Mm, hay." "That's better." "Full now." "Good hay." "Settled in."],
   :low
   ["Hay: {bales} left"
    "Bales left: {bales}"
    "Running low — {bales} left"
    "Hay stock: {bales}"
    "Low on hay: {bales}"],
   :stock-sign
   ["Hay: {bales}"
    "Bales: {bales}"
    "Hay stock: {bales}"
    "On hand: {bales}"
    "Bales on hand: {bales}"]},
  :biscuit
  {:walk-due
   ["Walk time?"
    "Leash is right there."
    "Ready when you are."
    "At the door."
    "Walk, Bisco?"],
   :walked
   ["Good walk."
    "That hit the spot."
    "Back and happy."
    "Thanks for that."
    "Tired, in a good way."],
   :porch
   ["Nice day for a nap."
    "Watching the yard."
    "Just resting here."
    "Keeping watch."
    "All good here."]}},
 :modes
 {:memorial
  {:plaque
   ["{name}, {years}. A good dog, missed."
    "{name} — {years}. Slept in every sunny spot."
    "{name}, {years}. Followed everywhere, loyally."
    "In memory: {name}, {years}."
    "{name} — {years}. A steady, gentle friend."],
   :hal-note
   ["{name}'s moved to memorial now — nothing's asked of them, just a quiet spot in the sun."
    "I moved {name} to memorial. They're remembered now, not tended to — and that's right."
    "{name}'s in memorial. No tasks, no tending — just a place for them in the sun."
    "I've set {name} up in memorial. Nothing's expected of them anymore, only remembered."
    "{name} doesn't need anything from us now. Memorial — just a spot to remember them by."]},
  :virtual
  {:idle
   ["{name}'s just here, keeping company."
    "{name}'s happy right where they are."
    "{name} doesn't need a thing."
    "{name}'s around, no fuss."
    "{name}'s content, watching the water."]}}}


===

# Score it

Score the draft on each criterion, 0-10, with a reason of one to three sentences that quotes the lines behind the score. 10 means every line keeps every rule in that family; take points off per violation, more for a rule broken in several lines. Under each criterion list the violations: which key/line, which STYLE.md rule number, what's wrong, and the fix (the rewritten line, or how to rewrite it). A criterion with no violations has an empty vector and a reason saying what it does well.

- A: who's talking, and to whom (rules 1-5)
- B: what the world is (rules 6-10)
- C: what's never said (rules 11-15)
- D: look and length (rules 16-19)
- S: specificity: could a stranger tell these lines belong to Prime Meridian?

Request-specific notes (they fall under A and B):
6. Mirror specifics: a creature's lines must match its real chores and counter (Flora: worms every 2 days; Juniper: hay every 12 hours; Biscuit: a daily walk, the leash by the door); the sign is the count the player reported (§1 pillar 'real, not simulated'); a missed chore is one steady overdue state that never escalates (§2.3 pressure ceiling), so :hungry lines don't guilt or escalate; memorial lines are kind and truthful.


Return exactly:

{:A {:score 0-10 :reason "..." :violations [{:where "which key/line" :rule 3 :issue "..." :fix "..."}]}
 :B {:score 0-10 :reason "..." :violations [...]}
 :C {:score 0-10 :reason "..." :violations [...]}
 :D {:score 0-10 :reason "..." :violations [...]}
 :S {:score 0-10 :reason "..." :violations [...]}}

No :verdict, no pass/fail: the pipeline applies the threshold.