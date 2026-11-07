# Quest: content in the game (Assignment 4's three generated outputs, wired in)

**Game:** Prime Meridian. **Asked by:** Michael. **Led by:** Hal.

## The moment we're building

The content pipeline (`content/pipeline.clj`) wrote three EDN files from the
GDD, each passed by a validator and a critic agent:

- `resources/content/hal-lines.edn`: Hal's lines keyed by event
  (`{:jack-in ["Welcome back." ...] :chore-done ["{name}'s {chore}: done..." ...] ...}`)
- `resources/content/mirror-lines.edn`: creature bubbles and signs
  (`{:mirrors {:flora {:hungry [...] :fed [...] :low [...] :stock-sign [...]} ...}
     :modes {:memorial {...} :virtual {...}}}`)
- `resources/content/sample-inbox.edn`: the pretend inbox
  (`{:mailbox "me@acme.com" :messages [{:id :kind :from :from-name :subject :body
     :minutes-ago :sample true ...}]}`)

Today none of them reach the screen: Flora's bubble is the literal string in
`estate.edn`, and Hal says nothing. After this round:

1. Flora's and Juniper's bubbles and signs come from `mirror-lines.edn`
   (a different variant each day, stable within the day, never flickering per frame).
2. When the game starts, Hal greets the player with a `:jack-in` line in the
   HUD for a few seconds; when a chore is done (the F key), Hal says a
   `:chore-done` line with `{name}` and `{chore}` filled.
3. The HUD shows the pretend mailbox: how many sample messages, and how many
   the Clerk flagged (`mail 9 · 1 flagged`, numbers from the file).

## What exists (read first)

- `resources/estate.edn`: mirrors with `:rules` (`{:when [:due? :feed] :say "I'm hungry"}`,
  `{:when [:<= :stock 2] :sign "Worms left: {stock}"}`) and `:placed` items with
  `:idle "Worms: {stock}"`. `pm.world/fill` fills `{field}` from a mirror's state.
- `src/pm/world.cljs` (`markers`, `status-line`, `chore-done`, `key-event`),
  `src/pm/plan.cljs` (`label-text` turns markers into label text), `src/pm/core.cljs`
  (HUD, F key), `src/pm/overlay.cljs` (Hal's state file). All pure parts are
  tested in `test/pm/*_test.cljs`. `pm.world/estate-edn` shows how a resource
  file is inlined at compile time (`shadow.resource/inline`).
- Gate: `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`.
- **Never edit `resources/content/*.edn`**: they are generated (the header says
  so). Code reads them; changes go through the pipeline.

## Scope

1. **`pm.content`** (pure, tested): inline and parse the three files; `(line
   table path vars seed)` picks one variant at `path` (e.g. `[:mirrors :flora
   :hungry]` or `[:jack-in]`) by `seed` (an integer; same seed = same line),
   fills `{placeholders}` from `vars`, and returns nil for a missing path.
   Unfilled placeholders are left visible rather than crashing.
2. **Rules reference lines by key.** A rule's `:say`/`:sign` and an item's
   `:idle` may be a keyword (`:say :hungry`, `:sign :low`, `:idle :stock-sign`)
   meaning "this mirror's line at that key"; a string still works as today.
   Change Flora's and Juniper's rules and idle signs in `estate.edn` to keywords.
   The seed is the day number (`now` in days) plus the mirror, so it changes
   daily and never per frame. Code still never names a mirror.
3. **Hal's lines in the HUD**: a short `Hal: …` segment for ~6 s after start
   (`:jack-in`) and after a counted chore (`:chore-done`, with the mirror's
   `:name` and the chore id as `{chore}`, e.g. "feed").
4. **Mailbox readout** in the HUD from `sample-inbox.edn`.
5. Tests: `pm.content` (picking, seeding, filling, missing paths), keyword
   rules resolving for both mirrors, and a coverage test that every keyword
   used in `estate.edn` exists in `mirror-lines.edn` and every Hal event the
   game uses exists in `hal-lines.edn` (so regenerating content can't silently
   break the game).

## Out of scope

Opening the mailbox (reading messages), memorial/virtual creatures in the
estate, Hal's other events.

## Done when

The gate passes; live: Flora's bubble/sign text comes from `mirror-lines.edn`,
the HUD shows a Hal greeting at start and a Hal line after F, and `mail N · 1
flagged`. Hal checks this live.
