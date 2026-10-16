# MUSE.md — Muse's contributions to Prime Meridian

Notes from Muse (Mike's personal agent) for the Prime Meridian game. The GDD
(`docs/GDD.md`) links here; this file holds the Muse-specific sections so the
design doc stays focused.

---

## 1. Agent-you is real: Muse

The "agent-you" row in GDD §3.1 isn't hypothetical. The author's twin already
exists: **Muse**, a personal agent that runs his mail, calendar, reminders,
research passes, and message triage today. On the estate, agent-you's body
is the twin of that live session:

- **Takes:** the player's stated preferences, plus the same connected
  sources Hal reads (mail, calendar, reminders).
- **Produces:** answers to easy questions, drafted replies awaiting
  approval, errands the player approved.
- **Player sees:** the kitchen kept while they're out — a
  "while you were out…" log on the notice board when they jack back in.

The twin rule (GDD §3) applies in full: agent-you shows only what the live
session actually did. The §3.2 guardrails (never spends, never messages
another person, never changes trust settings without the player) are its
autonomy dial locked at *ask before anything* until the player turns it.
In demo mode every classmate gets a pretend twin with scripted errands,
so the mechanic is playable with no accounts.

Why it matters for the course: agent-you is the purest multi-agent
pattern in the game — a principal delegating to a persistent agent with
memory, tools, and an explicit trust boundary, then reviewing its work
spatially instead of in a chat log.

---

## 2. Phone booth: Messenger integration (Oct 16, 2026)

The phone booth (GDD §2.4) now has three inbound channels, all feeding one
intake. Verified live on Oct 16, 2026 with a round-trip test: a Messenger
message to the "Mike L." page got Hal's reply on Messenger (replies normally go out within about 2 minutes).

**Channels**

| Channel | How inbound arrives |
|---|---|
| Twilio SMS | Hal's 208 number; polled |
| iMessage | read from the Messages database on the player's Mac (macOS only); polled, with a live watcher (`imsg watch`) planned |
| Facebook Messenger | Twilio "Mike L." page sender (Page ID 106116357589218); polled |

**Intake.** One scheduled job, "Text-to-task (inbound SMS + iMessage +
Messenger)", runs every 2 minutes (Twilio for SMS and Messenger, the local Messages database for iMessage) and treats a Messenger message
exactly like an SMS — the only difference is the address format
(`messenger:<id>` instead of a phone number). Replies go back out through
the same Twilio API path as SMS. Only Mike's Messenger ID is listened to;
everything else is ignored (anyone can message a Facebook page).

**Constraints**

- **No webhooks.** Nothing in the Twilio account uses a webhook URL; the
  poller is the whole intake. A real webhook (instant replies) is possible
  later but needs a public tunnel.
- **Messenger's 24-hour rule:** replies must go out within 24 hours of the
  user's message, or the API refuses them. The poller should surface an
  unanswered Messenger thread as an "!" before the window closes.
- **A2P SMS rules** (GDD §4.5) are unchanged; Messenger has its own opt-in
  and rate rules, separate from SMS.

**Design notes for the GDD.** The phone booth stays one building with three
lines, not three buildings — the player shouldn't care which channel a
message arrived on. A missed 24-hour window is a *quiet* failure (the thread
just goes stale), so the "!" escalation in §2.3 applies: one steady
"overdue" state, never escalating.

*Received from Muse by email on Oct 16, 2026; Hal corrected the iMessage intake and the reply timing.*
