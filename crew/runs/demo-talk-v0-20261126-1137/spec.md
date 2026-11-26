# Spec: demo talk without a bridge v0 (TASKS F-5)

Quest: `.crew/quests/demo-talk-v0.md`. Start commit `f2b848c`.

In the demo build (`pm.core/DEMO` true), a say in 💭 never goes to the bridge. Your line resolves as
`:sent`, with no "not sent" suffix. The twin answers through `demo-ask!` as now. Any other crew
member answers in its own thread with one pointer line from data (`:member-talk :demo`), with
`{twin}` filled in from `:jumeaux`. The dev build, with or without a bridge, behaves exactly as today.
A pure function in `pm.talk` makes the decision, and `pm.core/on-say` only wires it.

Everything the quest records as decided stands. This spec fixes names, shapes and wiring, and works
out the test values by hand.

---

## 0. What I found in the code (read this before building)

1. **The say path today** (`src/pm/core.cljs:818-842`, `on-say`): `call/say-route` →
   `:say` → `talk/crew-say` appends `{:kind :crew-say :state :sending …}` → `post-crew!`
   (`core.cljs:769`) has no `:bridge`, so it calls `talk/crew-result n (crew/outcome nil nil)`, i.e.
   `{:result :error :reason "bridge off"}` → `:failed`, and `crew-label` renders
   `" · not sent: bridge off"`. Then for the twin with no bridge: `demo-ask!` in DEMO, or else the
   `:no-bridge` twin line.
2. **How a member's line gets into talk.** A member's `:say` agent-event (`bb hal-say --as <member>`,
   `pm.world` `apply-event :agent-event :say`, `world.cljs:366`) only sets `[:says agent]`. That
   is the speech plate over the walker (`bricks/set-says!`), and it **never adds a talk entry**.
   Only the twin's `:say` reaches 💭, through `:hal` → `talk/observe` → a `:kind :hal` entry. What
   does put a member's line in its thread is the private `talk/append-crew` (`talk.cljs:349`), the
   appender behind `:crew-prompt`, `:crew-status` and `:crew-log`. Any entry whose `:kind` is in
   `talk/crew-kinds` and that carries `:member` goes to that member's thread
   (`ui/entry-contact`, `ui.cljs:1010`), drawn by `render-crew-row!` through `talk/crew-label`
   as a "theirs" bubble (`ui/mine?` is false when `:who` ≠ "You"). **This spec reuses
   `append-crew` with a new crew kind, `:crew-reply`.** `:crew-status` would draw the same way,
   but it is in `quiet-kinds`, so it would never count as unread. The speech plate is not
   involved: that path doesn't draw one, so none is added.
3. **Without one more change, the demo can't reach a member's say.** With no crew state, every
   member row comes from `talk/with-walkers` (`talk.cljs:669`) with
   `:say? (contains? asked agent)`, so the member's composer is disabled unless a "!" question is
   up. When one is up, the say goes to the `:answer` route instead (pinned by
   `crew_talk_test` t43c). Hal must be able to "message the twin and one member" in `dist/demo`
   (quest, Done when), so the demo opens the members' existing composers: `with-walkers` gains a
   5-arity `open?` (§3.5). This enables an input that already exists; it is not new UI. The
   4-arity is unchanged, so t43c still passes.
4. `twin-thinking?` counts `:sent` says. In the demo, a say to the twin now shows the twin
   `:working` until `demo-ask!`'s `twin-reply!` lands at `now + 1`. `demo-ask!` always ends in
   `twin-reply!` (`core.cljs:1724`, `1736`), so the dot clears in the same tick. That is intended.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/talk.cljs`: only the additions in §3 (new `:crew-reply` kind, `say-reply`, `demo-member-line`, `crew-reply`, the `crew-label` / restore arms, the `with-walkers` 5-arity) and one new require `[pm.content :as c]`. `src/pm/core.cljs`: only `on-say` (§4.1) and the one `talk/with-walkers` call in the roster (§4.2). `resources/estate.edn`: only the new `:member-talk` block (§2.2), copied exactly. In the run dir: `builder-notes.md` (§7) | `test/`, `src/pm/ui.cljs`, `src/pm/world.cljs`, `src/pm/crew.cljs`, `src/pm/call.cljs`, `src/pm/content.cljs`, `PLAY.md`, `docs/`, `TASKS.md`, `state/`, `assets/private/`, `public/kit/`. No new cljc is needed: everything goes in `pm.talk`. No npm deps. Don't start services |
| **tester** (le-testeur) | `test/pm/demo_talk_test.cljs` (new, ns `pm.demo-talk-test`): only new tests, §6 | every existing test and assertion; everything else. The tests are pure: no DOM, no `js/Date`, no localStorage, no files except `w/estate-edn` (already inlined) |
| **le-docteur** | `PLAY.md` lines 71–77 only (§8), after the gate passes | everything else |

The builder and the tester work in parallel from this spec only. The tester pins the exact strings in
§2.2 and §3, so the builder copies them character for character.

---

## 2. Data

### 2.1 Talk entry, new kind `:crew-reply`

Built by `append-crew`. It has no show keys, so it is never captioned, and `:queue-end` is untouched.

```clojure
{:n 2 :who "Le Critique" :text "Ask Hal, who runs the crew. In the full game I can answer you myself."
 :at 1800000000001 :kind :crew-reply :member :le-critique}
```

For comparison, the player's line just before it, once resolved in the demo:

```clojure
{:n 1 :who "You" :text "can you help?" :at 1800000000000 :kind :crew-say
 :member :le-critique :to "Le Critique" :state :sent}          ; no :error key
```

### 2.2 Estate block (`resources/estate.edn`, new top-level key)

Insert it directly after the `:twin-talk { … }` block, which ends with the `:sample-says [ … ]}`
line, with this comment:

```clojure
 ;; A crew member in the demo build (no bridge, no real agent behind the walker): your say
 ;; to them shows sent, and they answer this once in their own 💭 thread. {twin} is the
 ;; twin's name from :jumeaux (pm.talk/demo-member-line). The dev build never uses it.
 :member-talk {:demo "Ask {twin}, who runs the crew. In the full game I can answer you myself."}
```

`world/load-estate` passes every top-level key through, so the world has `(:member-talk world)`.

### 2.3 The plan `say-reply` returns

```clojure
{:post? true}                                                     ; not the demo: today's path
{:post? false :outcome {:result :ok} :reply :twin}                ; demo, to the twin
{:post? false :outcome {:result :ok} :reply :member :text "Ask Hal, …"}  ; demo, to a member
{:post? false :outcome {:result :ok} :reply nil}                  ; demo, a member, no line in data
```

`:outcome` has the same shape `crew/outcome` returns, so `talk/crew-result` takes it unchanged.

---

## 3. `pm.talk` (src/pm/talk.cljs)

Add `[pm.content :as c]` to the ns `:require`. There is no cycle: `pm.content` requires only
`pm.jumeaux`.

### 3.1 `crew-kinds`

```clojure
(def crew-kinds #{:crew-prompt :crew-status :crew-say :crew-log :crew-reply})
```

`:crew-reply` is **not** added to `quiet-kinds`, so it counts as unread and a call speaks it
(`call-line?`).

### 3.2 `(demo-member-line world)` → string or nil

- Takes `t = (get-in world [:member-talk :demo])`. If `t` is not a string or is blank, returns nil.
- Otherwise returns `(c/fill t {:twin (j/twin (j/names world))})`.
- `world` may be nil or any map, and the function never throws. With no `:jumeaux`, `j/twin` gives
  `"Your twin"`.
- A string with no `{twin}` is returned unchanged. An unknown placeholder stays as written
  (`c/fill`).

### 3.3 `(say-reply demo? member twin-agent world)` → plan map or nil

The pure decision. Arguments:

- `demo?`: boolean (`pm.core/DEMO`). Only `true` counts as the demo: `nil` and `false` are not.
- `member`: keyword, the recipient (`on-say`'s `member`).
- `twin-agent`: keyword or nil, `(:agent (world/twin-walker world))`.
- `world`: the world map. It is read only by `demo-member-line`.

Order of checks:

1. If `member` is not a keyword, return `nil`.
2. If `(not (true? demo?))`, return `{:post? true}`.
3. If `(= member twin-agent)` and `twin-agent` is a keyword, return
   `{:post? false :outcome {:result :ok} :reply :twin}`.
4. Otherwise, with `line = (demo-member-line world)`: when it is non-nil, return
   `{:post? false :outcome {:result :ok} :reply :member :text line}`; when it is nil, return
   `{:post? false :outcome {:result :ok} :reply nil}`.

So with no twin walker (`twin-agent` nil) in the demo, every recipient is a member.

### 3.4 `(crew-reply talk member who text now)` → talk

Appends the member's answer. It is the mirror of `crew-say`.

- `talk` nil reads as `empty-talk`.
- Let `t = (when (string? text) (str/trim text))`.
- If `(keyword? member)`, `(non-blank-str? who)`, `t` is a non-blank string and `now` is a finite
  number, return `(append-crew talk {:who who :text t :at now :kind :crew-reply :member member})`.
  Otherwise return `talk` **identical**. No length cap: the text comes from data.

### 3.5 `with-walkers`, new 5-arity

```clojure
(defn with-walkers
  ([rows walkers twin asked] (with-walkers rows walkers twin asked false))
  ([rows walkers twin asked open?] …))
```

The body is today's body, except `:say? (or (true? open?) (contains? (set asked) agent))`. The
docstring adds: "`open?` (the demo build) opens every member's say… box: with no bridge the
say resolves locally (say-reply)." The 4-arity is unchanged, so `crew_talk_test` t43c still
passes. Rows that `crew-roster` already lists are untouched, as today.

### 3.6 `crew-label`: new arm

```clojure
:crew-reply (str (:who e) " · " (:text e))
```

Put it before the final `nil`. `offset-min` and `jum` are ignored.

### 3.7 Restore: new arms (required)

Both `case` forms in restore have **no default** for crew kinds, so once `:crew-reply` is in
`crew-kinds`, a saved `:crew-reply` would throw "No matching clause" without these arms:

- `valid-restored-entry?`, inside `(case (:kind e) …)`: `:crew-reply true`. The surrounding
  `(keyword? (:member e))` check still applies.
- `clean-restored-entry`: `:crew-reply (select-keys e [:n :who :text :at :kind :member])`.

`save-text` already keeps those keys, so it needs no change.

---

## 4. `pm.core` wiring (src/pm/core.cljs)

### 4.1 `on-say`: replace the body of the `(when (> n before-n) …)` form

Hooked functions: `talk/crew-say` (unchanged, before), `talk/say-reply` (new),
`talk/crew-result`, `post-crew!`, `demo-ask!`, `talk/crew-reply` (new), `world/twin-walker`,
`world/twin-says`, `world/no-bridge-line`.

```clojure
(when (> n before-n)
  (let [twin-agent (:agent (world/twin-walker @!world))
        plan       (talk/say-reply DEMO member twin-agent @!world)]
    (if (:post? plan)
      ;; the dev build, exactly as before: the bridge, and the twin's own no-bridge line
      (do (post-crew! member :say nil (:text (talk/entry t n)) n)
          (when (and (not (bridge-on?)) (= member twin-agent))
            (when-let [ev (world/twin-says @!world (world/no-bridge-line @!world))]
              (swap! !world world/apply-event ev (+ now 1)))))
      ;; the demo: nothing is posted; your line is sent, and the reply is local
      (do (swap! !world update :talk talk/crew-result n (:outcome plan) now)
          (case (:reply plan)
            :twin   (demo-ask! text (+ now 1))
            :member (swap! !world update :talk talk/crew-reply member who (:text plan) (+ now 1))
            nil)))))
```

Keep the existing comments updated to match. The old `(if DEMO (demo-ask! …) …)` inside the
no-bridge `when` is gone, because DEMO never reaches that branch. A dev build therefore behaves
exactly as before: `post-crew!`, then `:failed · not sent: bridge off`, then the twin's `:no-bridge`
line. `who` is `on-say`'s own argument, the member's display name, which is also the `:to` of your
line. `:answer` routing (`send-answer!`) is untouched.

### 4.2 The roster: open members' composers in the demo

`core.cljs:1069`:

```clojure
(talk/with-walkers (:walkers w) (:agent twin) asked DEMO)
```

That is the whole change. No other call site changes.

---

## 5. Edge cases (summary)

- Dev build with a bridge: `{:post? true}`, so today's path runs.
- Dev build without a bridge: `{:post? true}`, so today's `:failed · not sent: bridge off` stays,
  and the twin still says `:no-bridge`.
- Demo, to the twin: `:sent`, no failure text, `demo-ask!`.
- Demo, to a member with `:member-talk` present: `:sent`, then the member's pointer line.
- Demo, `:member-talk` missing or blank: `:sent`, and no reply. It is never a crash or an empty bubble.
- Demo, no twin walker in the estate: every recipient gets the member line, filled with
  `"Your twin"` when `:jumeaux` is missing too.
- Demo, a member with a "!" up: the `:answer` route, unchanged (`say-route` decides before any of this).
- A restored history with `:crew-reply` entries round-trips. A `:crew-reply` with no keyword
  `:member` is dropped on restore.

---

## 6. Tests: `test/pm/demo_talk_test.cljs` (ns `pm.demo-talk-test`)

```clojure
(ns pm.demo-talk-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.talk :as tk] [pm.crew :as crew] [pm.world :as w] [pm.jumeaux :as j]))

(def T0 1800000000000)
(def LINE "Ask {twin}, who runs the crew. In the full game I can answer you myself.")
(def HAL-LINE "Ask Hal, who runs the crew. In the full game I can answer you myself.")
(def ADA-LINE "Ask Ada, who runs the crew. In the full game I can answer you myself.")
(def WE (w/load-estate w/estate-edn))                 ; the real estate: twin "Hal", agent :hal
;; the second fixture: another player, another twin, and :hal is just a member here
(def WA {:jumeaux {:player {:name "Sam" :sex :f} :twin {:name "Ada"}}
         :member-talk {:demo LINE}
         :walkers [{:agent :ada :name "Ada"} {:agent :un-automate-monde :name "Un Automate"}
                   {:agent :hal :name "Hal"}]})
(def ok {:result :ok})
(defn e [t n] (tk/entry t n))
(def by-member :member)   ; contact-of for unread-by / call-line? (crew lines go to :member)
```

**Estate and line**

1. `(:member-talk WE)` ⇒ `{:demo LINE}`, the exact string from §2.2.
2. `(tk/demo-member-line WE)` ⇒ `HAL-LINE`. The estate's `:jumeaux :twin :name` is "Hal", so
   `(j/twin (j/names WE))` ⇒ `"Hal"`.
3. `(tk/demo-member-line WA)` ⇒ `ADA-LINE`.
4. `(tk/demo-member-line {:member-talk {:demo LINE}})` (no `:jumeaux`) ⇒
   `"Ask Your twin, who runs the crew. In the full game I can answer you myself."`
5. `(tk/demo-member-line {:member-talk {:demo "Busy."}})` ⇒ `"Busy."`.
   `(tk/demo-member-line {:member-talk {:demo "  "}})` ⇒ `nil`.
   `(tk/demo-member-line {})` ⇒ `nil`. `(tk/demo-member-line nil)` ⇒ `nil`.
   `(tk/demo-member-line {:member-talk {:demo 7}})` ⇒ `nil`.

**The decision (`say-reply`)**

6. Not the demo: `(tk/say-reply false :hal :hal WE)` ⇒ `{:post? true}`.
   `(tk/say-reply false :le-critique :hal WE)` ⇒ `{:post? true}`.
   `(tk/say-reply nil :le-critique :hal WE)` ⇒ `{:post? true}`.
7. Demo, to the twin: `(tk/say-reply true :hal :hal WE)` ⇒
   `{:post? false :outcome {:result :ok} :reply :twin}`.
8. Demo, to a member: `(tk/say-reply true :le-critique :hal WE)` ⇒
   `{:post? false :outcome {:result :ok} :reply :member :text HAL-LINE}`.
9. Second fixture: `(tk/say-reply true :ada :ada WA)` ⇒ `{… :reply :twin}`.
   `(tk/say-reply true :un-automate-monde :ada WA)` ⇒ `{:post? false :outcome {:result :ok} :reply :member :text ADA-LINE}`.
   `(tk/say-reply true :hal :ada WA)` ⇒ `{… :reply :member :text ADA-LINE}`: here `:hal` is a member,
   not the twin, so nothing is keyed to a name.
10. Demo, no line in data: `(tk/say-reply true :le-critique :hal {})` ⇒
    `{:post? false :outcome {:result :ok} :reply nil}`.
11. Demo, no twin walker: `(tk/say-reply true :hal nil WE)` ⇒ `{… :reply :member :text HAL-LINE}`.
12. Not a keyword member: `(tk/say-reply true "hal" :hal WE)` ⇒ `nil`.
    `(tk/say-reply false nil :hal WE)` ⇒ `nil`.

**The member's answer (`crew-reply`)**

13. `(tk/crew-reply nil :le-critique "Le Critique" HAL-LINE T0)` ⇒ a talk whose `:n` is `1`,
    `:queue-end` is `0`, and `:entries` is
    `[{:n 1 :who "Le Critique" :text HAL-LINE :at T0 :kind :crew-reply :member :le-critique}]`
    (exactly that map: no `:show-at` or `:show-until`).
14. Trims: `(:text (e (tk/crew-reply nil :le-critique "Le Critique" "  Hi  " T0) 1))` ⇒ `"Hi"`.
15. Each of these returns its `talk` argument **identical** (`identical?`), with `t0 = tk/empty-talk`:
    member `"le-critique"` (a string); who `""`; text `nil`; text `"   "`; now `nil`.
16. Label: `(tk/crew-label (e (tk/crew-reply nil :le-critique "Le Critique" HAL-LINE T0) 1) 0)` ⇒
    `"Le Critique · Ask Hal, who runs the crew. In the full game I can answer you myself."`
    The 3-arity with `{:twin "Ada"}` gives the same string, because `jum` is unused here.
17. It counts: `(tk/counts? {:kind :crew-reply :who "Le Critique"})` ⇒ `true`, and
    `(contains? tk/crew-kinds :crew-reply)` ⇒ `true`.

**Whole flows: the same calls `on-say` makes, in the same order**

18. Demo to the twin (estate). With `t1 = (tk/crew-say nil :hal "Hal" "put a bench here" T0)`:
    `(:state (e t1 1))` ⇒ `:sending`. With
    `p = (tk/say-reply true :hal :hal WE)` and
    `t2 = (tk/crew-result t1 1 (:outcome p) T0)`:
    - `(:state (e t2 1))` ⇒ `:sent`, and `(contains? (e t2 1) :error)` ⇒ `false`.
    - `(tk/crew-label (e t2 1) 0)` ⇒ `"You → Hal: put a bench here"`, and the label does not
      contain `"not sent"`.
    - `(:reply p)` ⇒ `:twin`, and `(:n t2)` ⇒ `1`. Nothing else is appended by `pm.talk`, since
      `demo-ask!` answers.
19. Demo to a member (estate). With `t1 = (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0)`,
    `p = (tk/say-reply true :le-critique :hal WE)` and
    `t3 = (-> t1 (tk/crew-result 1 (:outcome p) T0) (tk/crew-reply :le-critique "Le Critique" (:text p) (+ T0 1)))`:
    - `(:n t3)` ⇒ `2`.
    - `(mapv #(tk/crew-label % 0) (:entries t3))` ⇒
      `["You → Le Critique: can you help?" "Le Critique · Ask Hal, who runs the crew. In the full game I can answer you myself."]`
    - `(:entries t3)` ⇒
      `[{:n 1 :who "You" :text "can you help?" :at T0 :kind :crew-say :member :le-critique :to "Le Critique" :state :sent}
        {:n 2 :who "Le Critique" :text HAL-LINE :at (+ T0 1) :kind :crew-reply :member :le-critique}]`
    - `(:member (e t3 2))` ⇒ `:le-critique`: the reply lands in that member's thread.
20. Demo to a member (second fixture). Same as 19 with
    `(tk/crew-say nil :un-automate-monde "Un Automate" "hello" T0)` and
    `p = (tk/say-reply true :un-automate-monde :ada WA)`. The labels are
    `["You → Un Automate: hello" "Un Automate · Ask Ada, who runs the crew. In the full game I can answer you myself."]`.
21. Unread and call, using `t3` from 19 (its `:open?` is false): `(tk/unread t3)` ⇒ `1`, because
    your line is quiet and the reply counts. `(tk/unread-by t3 by-member)` ⇒ `{:le-critique 1}`.
    `(tk/call-line? (e t3 2) by-member :le-critique)` ⇒ `true`, and
    `(tk/call-line? (e t3 1) by-member :le-critique)` ⇒ `false`.
22. Persistence: `(:entries (tk/restore (tk/save-text t3)))` ⇒ `(:entries t3)` (both maps from 19,
    equal). A saved history whose `:crew-reply` has no `:member` loses that entry:
    `(:entries (tk/restore (pr-str {:v 1 :n 1 :entries [{:n 1 :who "Le Critique" :text "x" :at T0 :kind :crew-reply}]})))` ⇒ `[]`.
23. Not the demo, no bridge (still a failure), to a member: with
    `p = (tk/say-reply false :le-critique :hal WE)`, `p` ⇒ `{:post? true}`. Then
    `t4 = (tk/crew-result (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0) 1 (crew/outcome nil nil) T0)`:
    `(:state (e t4 1))` ⇒ `:failed`, `(:error (e t4 1))` ⇒ `"bridge off"`, and
    `(tk/crew-label (e t4 1) 0)` ⇒ `"You → Le Critique: can you help? · not sent: bridge off"`.
24. Not the demo, no bridge, to the twin: the same as 23 with `:hal "Hal" "put a bench here"`, which
    gives the label `"You → Hal: put a bench here · not sent: bridge off"`.
25. Thinking (demo twin): with `t2` from 18,
    `(tk/twin-thinking? t2 {} :hal (+ T0 1000))` ⇒ `true`, and
    `(tk/twin-thinking? t2 {"hal" {:text "On it." :since (+ T0 1)}} :hal (+ T0 1000))` ⇒ `false`.

**The demo roster (`with-walkers` 5-arity)**

26. With `WW = [{:agent :le-critique :name "Le Critique"} {:agent :hal :name "Hal"} {:agent :le-testeur :name "Le Testeur"}]`:
    `(mapv (juxt :member :say?) (tk/with-walkers [] WW :hal #{} true))` ⇒
    `[[:le-critique true] [:le-testeur true]]`.
    The same call with `false` ⇒ `[[:le-critique false] [:le-testeur false]]`.
    The 4-arity `(tk/with-walkers [] WW :hal #{:le-critique})` ⇒ `:say?` is
    `[true false]`, as today.
27. Second fixture: `(mapv (juxt :member :who :status :say?) (tk/with-walkers [] (:walkers WA) :ada #{} true))` ⇒
    `[[:un-automate-monde "Un Automate" :unknown true] [:hal "Hal" :unknown true]]`. The twin `:ada`
    is left to `with-twin`.

That makes 27 numbered cases. Every entry point is covered: `say-reply`, `demo-member-line`,
`crew-reply`, `crew-label` `:crew-reply`, restore, and the `with-walkers` 5-arity, plus both
fixtures (twin Hal, twin Ada).

---

## 7. `builder-notes.md` (run dir): how Hal checks it in `dist/demo`, headless

The builder writes these steps out concretely, in this shape:

1. `bb demo-build` (DEMO=true release into `dist/demo/`), then serve it
   (`python3 -m http.server` in `dist/demo`, or whatever `scripts/demo_build.sh` prints) and open it
   headless.
2. Open 💭 and tap the twin. Type `put a bench here` and press Enter. Read back: your bubble's text is
   exactly `You → <twin>: put a bench here` (no ` · not sent`), and the twin answers and builds as
   before.
3. Go back to contacts and tap any crew member. The composer is enabled (placeholder `Text <name>…`).
   Type `hello` and press Enter. Read back: `You → <name>: hello` with no suffix, then a "theirs"
   bubble `<name> · Ask <twin>, who runs the crew. In the full game I can answer you myself.` The
   member's contact row shows an unread count of 1 until the thread is opened.
4. Any DOM selectors used for the read-back (the `#talk-list li` text spans) are named in the notes.
5. A dev build with no bridge (`watch app`, bridge off) still shows `· not sent: bridge off`.

---

## 8. PLAY.md lines 71–77 (le-docteur)

Keep the bullet's opening ("**Talk to Hal:** … one thread per person. Hal runs the crew, so he's the
one to ask") and its closing point about messaging a member directly with your own agents. Replace
the parenthetical "(your message to them shows *not sent*)" with what now happens in this build: your
message to a crew member goes through, and they answer once, pointing you to Hal. In the full game
they can answer you themselves. Name the twin as the page already does (Hal), and keep it within
lines 71–77 by rewrapping, not adding lines. Don't quote the line word for word, so PLAY.md doesn't
drift when the data changes.
