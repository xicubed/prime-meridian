# Spec: calls-v0: calls in the room (TASKS T-11)

Quest: `.crew/quests/calls-v0.md`. Architect: un-architecte. Builder: un-automate-monde.
Tester: le-testeur. Reviewer: le-critique.

## 0. Shape

A call is **one key in the world atom** and a handful of **pure functions**. pm.core
wires them to the DOM and speech each frame. Nothing new persists, so a reload ends a call.

```
📞 click ──▶ core/start-call! ──▶ (call/start-call w member now)   world {:call {:with :hal :since t}}
                                   !talk-ui :call-heard ← (:n talk)
talk-tick!, every frame:
  (call/end-reason call (ui/thread) (:open? talk)) ≠ nil  ──▶ core/end-call!  (stop-speech!, stop-listening!)
  (talk/call-lines talk contact-of with heard)            ──▶ speak each with (talk/call-voice w with e)
                                                              heard ← (:n talk)
  caption path: global voice speaks e only if (not (talk/call-line? e contact-of with))
🎙 click ──▶ ui/listen! (lang = twin's voice) ── interim ──▶ composer text (call/mic-step)
                                              └─ final ───▶ core/on-say member who text
                                                             └─ (call/say-route w member text): :answer | :say | nil
bricks/set-dots! … call ──▶ (crew/plate-name crew w call) ──▶ "Hal · on a call" on the one plate
```

Decided in the quest and not reopened here: one call at a time, End button, the mic, the
spoken-lines rule, the plate text, and the three ways a call ends. This spec settles the
points the quest leaves open (§6 lists them).

## 1. Who touches what

| Who | May touch |
|---|---|
| **Builder** (un-automate-monde) | **new** `src/pm/call.cljs` (ns `pm.call`); `src/pm/talk.cljs` (add 3 fns, §3.1, nothing else); `src/pm/crew.cljs` (add `on-call-suffix` + a 3-arity of `plate-name`, §3.3, the 2-arity unchanged); `src/pm/bricks.cljs` (a 5-arity of `set-dots!`, §4.3, the 4-arity delegates); `src/pm/ui.cljs` (§4.2); `src/pm/core.cljs` (§4.1); `public/index.html` (CSS only, §4.4); **new** `crew/runs/calls-v0-20261122-1708/note.md` (§7) |
| **Tester** (le-testeur) | **new** `test/pm/calls_test.cljs` (ns `pm.calls-test`), nothing else |
| **Hal** | the dev-build hand checks (§7), `bb demo-build`, the GDD line (§6, item 6) and the commit |
| **Nobody** this round | `resources/estate.edn`, `resources/content/*` (no event fits, §3.2 `no-mic-line`), `shadow-cljs.edn`, `bb.edn`, `scripts/*`, `docs/GDD.md`, every existing test, `assets/private/`, `public/kit/` |

No npm deps. No `::k` keys across namespaces: pm.call uses plain keys (`:with :since :status
:why :member :text :route :agent`), and core keeps passing `:pm.ui/unread` spelled out as today.
Nothing in code names a person, pet or member. The twin is always `world/twin-walker`.

## 2. Data shapes

### 2.1 The call (world atom, `pm.core/!world`)

```clojure
{:call {:with :hal :since 1800000000000}}       ; a call with the twin (his walker's :agent)
{:call {:with :le-testeur :since 1800000060000}}  ; a crew member (roster :member)
{:call {:with :juniper :since 1800000120000}}     ; a creature (its mirror id)
{}                                                ; no :call key = no call
```

`:with` is a contact keyword as `pm.ui/entry-contact` returns it, never `:all`. Only
`call/start-call` and `call/end-call` write `:call`. No cache or save writes it (overlay
`cache-of`/`play-of` and `talk/save-text` don't read it), so a reload drops it.

### 2.2 The speech cursor (core-private, `!talk-ui`)

`:call-heard` is the talk `:n` up to which lines have been handed to the call (an integer, or
nil with no call). It's set to `(:n talk)` when a call starts, so older lines aren't spoken,
and moved to `(:n talk')` every tick during the call. `:n` only grows (the 100-entry cap
drops old entries but never renumbers), so this is safe.

### 2.3 The mic (core-private, `!talk-ui :mic`)

```clojure
{:status :idle}                                         ; ready (also what nil means)
{:status :listening :member :hal :text "do you re"}     ; recognising for :hal; :text = interim words
{:status :off :why :refused}                            ; the browser said no to the microphone
{:status :off :why :unsupported}                        ; no SpeechRecognition in this browser
{:status :off :why :no-mic}                             ; no capture device
```

`:off` lasts until the page reloads. Ending a call never clears it.

## 3. Pure functions (tested)

### 3.1 `pm.talk`: three additions

```clojure
(defn call-line?
  "Is talk entry `e` a line the call with `with` speaks? Its contact is `with`
   and it's a message (counts?: not the quiet crew log, a status flip, or the
   player's own line)."
  [e contact-of with])
```
- `true` iff `(keyword? with)`, `(= with (contact-of e))` and `(counts? e)`. `with` nil → `false`.

```clojure
(defn call-lines
  "The entries a call with `with` should speak: newer than `since-n`, from `with`
   (call-line?), in :n order."
  [talk contact-of with since-n])
```
- Returns a **vector** of entries (whole maps) with `(> (:n e) since-n)` and `(call-line? e contact-of with)`, ordered by `:n`.
- `talk` nil → `[]`. `with` nil → `[]`. `since-n` not a number → treated as `0`.

```clojure
(defn call-voice
  "The voice a call with `with` speaks entry `e` in. A creature's line (e has a
   keyword :mirror) uses voice-for, the same as today. Any other line uses the
   twin's voice (voice-for world {:kind :hal}) with the :voice of the walker
   whose :agent is `with` layered on top (each field only when valid, as
   layer-voice already checks)."
  [world with e])
```
- Returns `{:rate :pitch :lang}`. If there's no such walker, or it has no `:voice`, the result is the twin's voice. An invalid field (rate outside 0.1–10, pitch outside 0–2, blank lang) falls through to the twin's value.

### 3.2 `pm.call` (new, pure, requires `clojure.string`, `pm.world`)

```clojure
(def no-mic-line "No mic here. Type to me.")   ;; TODO content: no hal-lines.edn event fits yet
(def call-suffix-glyph "📞")
```

```clojure
(defn start-call [world member now])
```
- `(assoc world :call {:with member :since now})`.
- **Returns `world` unchanged (identical)** when `member` isn't a keyword, is `:all`, or `now` isn't a finite number, and also when a call with this same `member` is already on (its `:since` stays put).
- A call with someone else is replaced: one call at a time.

```clojure
(defn end-call [world])
```
- `(dissoc world :call)`. Returns `world` unchanged when there's no `:call`.

```clojure
(defn call-with [world])
```
- `(:with (:call world))`, or nil.

```clojure
(defn end-reason [call thread open?])
```
- nil when `call` is nil.
- `:closed` when the 💭 panel is closed (`(not open?)`). This wins over `:switched`.
- `:switched` when `(not= thread (:with call))` (back to contacts = nil, `:all`, another contact).
- nil otherwise (the call goes on).

```clojure
(defn call-button [call thread])
```
- nil when `thread` is nil or `:all`: no 📞 there.
- On a call with this thread: `{:label "End" :on? true :title "End the call"}`.
- Otherwise: `{:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}`.

```clojure
(defn mic-shown? [call row])
```
- `true` iff `call` is on with `(:member row)` and `(:kind row)` isn't `:creature`. The 🎙 is only on the composer during a call (§6, item 1). A creature's thread has no composer.

```clojure
(defn mic-step [mic event])
```
`mic` nil counts as `{:status :idle}`. `event` is a vector:

| event | from `:idle` | from `:listening` | from `:off` |
|---|---|---|---|
| `[:start member]` | `{:status :listening :member member :text ""}` | the same (restart) | unchanged |
| `[:interim text]` | unchanged | `(assoc mic :text text)` | unchanged |
| `[:final text]` | `{:status :idle}` | `{:status :idle}` | unchanged |
| `[:end]`, `[:stop]` | `{:status :idle}` | `{:status :idle}` | unchanged |
| `[:error "not-allowed"]`, `[:error "service-not-allowed"]` | `{:status :off :why :refused}` | same | unchanged |
| `[:error "audio-capture"]` | `{:status :off :why :no-mic}` | same | unchanged |
| `[:error <any other code>]` (e.g. "no-speech", "aborted", "network") | `{:status :idle}` | `{:status :idle}` | unchanged |
| `[:unsupported]` | `{:status :off :why :unsupported}` | same | unchanged |
| anything else | the normalised mic (`{:status :idle}` for nil) | unchanged | unchanged |

```clojure
(defn mic-line? [old new])
```
- `true` iff `(= :off (:status new))` and `(not= :off (:status old))`. This is when the twin says `no-mic-line`, once per page.

```clojure
(defn mic-view [mic])
```
| mic | result |
|---|---|
| nil or `{:status :idle}` | `{:label "🎙 Talk" :disabled? false :title "Talk instead of typing"}` |
| `{:status :listening …}` | `{:label "listening…" :disabled? true :title "Listening… it stops when you pause"}` |
| `{:status :off :why :refused}` | `{:label "🎙 Talk" :disabled? true :title "The browser refused the microphone; allow it for this page to talk"}` |
| `{:status :off :why :unsupported}` | `{:label "🎙 Talk" :disabled? true :title "This browser can't hear you (no speech recognition); type instead"}` |
| `{:status :off :why :no-mic}` | `{:label "🎙 Talk" :disabled? true :title "No microphone found; type instead"}` |
| `{:status :off}` with any other / no `:why` | `{:label "🎙 Talk" :disabled? true :title "The mic is off; type instead"}` |

```clojure
(defn say-route [world member text])
```
This is **on-say's decision**, taken out of on-say so it can be tested. `t` = `(str/trim text)`.
- nil when `member` isn't a keyword, `text` isn't a string, or `t` is blank.
- `{:route :answer :agent (name member) :text t}` when `(world/needs-you? world (name member))`.
- `{:route :say :member member :text t}` otherwise. Over-long text is still `:say`: `talk/crew-say` rejects it as it does today.

```clojure
(defn mark-on-call [rows call key-fn])
```
- `(mapv …)`: the row with `(= (:with call) (key-fn row))` gets `:on-call? true`. Every other row is returned **as is** (no `:on-call?` key added). With `call` nil, `(vec rows)`.

```clojure
(defn glyph-name [nm on-call?])
```
- `(str/trim (str nm (when on-call? (str " " call-suffix-glyph))))`. nil `nm` counts as `""`.

### 3.3 `pm.crew`: the plate text

```clojure
(def on-call-suffix " · on a call")
(defn plate-name
  ([crew w] …unchanged…)
  ([crew w call] (cond-> (plate-name crew w)
                   (and (some? (:agent w)) (= (:agent w) (:with call))) (str on-call-suffix))))
```
The 2-arity and its test (crew_walkers_test t15) stay as they are.

## 4. Side-effect code (not unit-tested; reviewed and checked by hand)

### 4.1 `pm.core`

- **`on-say`** becomes a dispatch on `(call/say-route @!world member text)`: nil → nothing. `:answer` → `(send-answer! (:agent r) (:text r))`. `:say` → the current say branch unchanged, fed `(:text r)`, so the no-bridge twin line still follows. Typed text and recognised text both come through here. **There's no second path.**
- **`start-call! [member]`**: `(reset! !gesture? true)` (this is a click), then `(swap! !world call/start-call member (js/Date.now))`, then `(swap! !talk-ui assoc :call-heard (:n (:talk @!world)) :roster nil)`.
- **`end-call! []`**: `(swap! !world call/end-call)`, `(ui/stop-speech!)`, `(ui/stop-listening!)`, `(swap! !talk-ui update :mic call/mic-step [:stop])`, `:call-heard nil`, `:roster nil`.
- **`on-call-button! [member]`**: if `(= member (call/call-with @!world))`, `end-call!`, else `start-call!`.
- **`on-mic! [member who]`**:
  - If `(not (ui/recognition?))`: `mic-step [:unsupported]`.
  - Else `(ui/listen! lang handlers)`, with `lang` = `(:lang (talk/voice-for @!world {:kind :hal}))`. If that returns true, `mic-step [:start member]`. Each handler swaps `:mic` through `mic-step`:
    - interim: `[:interim text]`.
    - final: `[:final text]`, then `(on-say member who text)`.
    - end: `[:end]`.
    - error: `[:error code]`.
  - After every swap: if `(call/mic-line? old new)`, the twin says `call/no-mic-line` via `(world/twin-says w call/no-mic-line)` + `world/apply-event` (bubble, caption and his thread). Nothing happens without a twin walker.
- **`talk-tick!`**, after `t'` is computed, in this order:
  1. `(when (call/end-reason (:call @!world) (ui/thread) (:open? t')) (end-call!))`. This covers End being clicked elsewhere, the 💭 toggle, Escape, ‹ Contacts and any other `set-thread!`.
  2. With a call on and `@!gesture?`: for each `e` in `(talk/call-lines t' contact-of with (:call-heard @!talk-ui))`, run `(call-say! e with)`. Then set `:call-heard (:n t')`. Without the gesture, still move `:call-heard` (lines from before the click are never queued).
  3. The caption branch: `(when (and (:voice? t') (not (talk/call-line? e contact-of (call/call-with @!world)))) (say-entry! e))`, so a line is never said twice.
  4. The roster: `rows` go through `(call/mark-on-call rows call :member)`. The roster cache `key` becomes `[rows previews unread (ui/thread) (:call w) (:mic @!talk-ui)]`. Call `(ui/render-roster! rows on-say on-expand offset-of jum previews' phone)`, where `phone` = `{:call (:call w) :mic (:mic @!talk-ui) :on-call on-call-button! :on-mic on-mic!}`.
  - Move `contact-of` up so steps 2 and 3 can use it (same definition: `#(ui/entry-contact % (:agent twin))`).
- **`call-say! [e with]`**: like `say-entry!` (the same `!spoken` guard, so a line that started playing isn't said again), but with `(talk/call-voice @!world with e)`. It ignores `:voice?`.
- **`crew-key-tick!`**: `rows` (walkers, creatures and places, after the `into`s) go through `(call/mark-on-call rows (:call w) #(or (:agent %) (:id %)))`.
- **`frame`**: pass `(:call @!world)` as the 5th argument of `bricks/set-dots!`.
- **DEMO**: no change. The SAMPLE line is a twin `:say`, which becomes a `:hal` talk entry whose contact is the twin, so a call with the twin speaks it. Both `DEMO` branches must still compile.

### 4.2 `pm.ui`

- **`recognition? []`**: `(boolean (or (.-SpeechRecognition js/window) (.-webkitSpeechRecognition js/window)))`.
- **`listen! [lang {:keys [on-interim on-final on-end on-error]}]`**:
  - Stops any running recogniser first.
  - Creates one with `continuous false`, `interimResults true`, `lang`.
  - `onresult`: joins the transcripts of all results, then calls `on-final` with the joined text if the last result is final, else `on-interim`.
  - `onerror`: `(on-error (.-error e))`. `onend`: `(on-end)`.
  - `.start` is called inside `try`. Returns `true` once started, `false` if unsupported or it threw (calling `(on-error "start-failed")` in that case, which maps to `:idle`). Keep it in `(defonce ^:private !rec (atom nil))`. `^js` hints, 0 warnings.
- **`stop-listening! []`**: clears the four handlers *before* `.abort`, so nothing fires after a call ends. No-op when idle.
- **`render-roster!`**:
  - The new **7-arity** `[rows on-say on-expand offset-of jum previews phone]`. The 6-arity calls it with `phone` nil (no 📞, no 🎙).
  - In a thread: after `‹ Contacts`, if `(call/call-button (:call phone) t)` is non-nil **and** `row` exists, add `<button class="pm-btn call">`. Its textContent is `:label`, plus `title`, plus class `on` when `:on?`. Click → `((:on-call phone) t)`. Header order: back, 📞, name.
  - Composer: when `(call/mic-shown? (:call phone) row)`, add `<button class="pm-btn mic">` after the input, from `(call/mic-view (:mic phone))`. It's disabled when `:disabled?` **or** `(not (:say? row))`. Add class `listening` while listening. Click → `((:on-mic phone) (:member row) (:who row))`.
  - While `(:mic phone)` is listening for this row's member, the input's value is `(:text mic)` (this wins over the kept focused value).
  - The final send is core's `on-say`. ui clears the input on the next render, since the mic is back to idle.
- **`contact-row!`**: the name text is `(call/glyph-name (:who row) (:on-call? row))`. `:who` itself is never changed: it feeds the header, the placeholder and `on-say`'s `who`.
- **`render-crew-key!`**: the name text is `(call/glyph-name name (:on-call? row))`. Add `on-call?` to the destructuring.
- `pm.ui` may require `pm.call`, which has no DOM and no cycle: it requires only `clojure.string` and `pm.world`.

### 4.3 `pm.bricks`

`(set-dots! crew-state asks-player now dots call)`: the same as the 4-arity, but the name
comes from `(crew/plate-name crew-state w call)`. The 4-arity calls it with `call` nil. The
plate style (`scene/plate`, `label-runs`) doesn't change: same plate, new text. Creatures have
no nameplate, so a creature call shows only on the rows (§4.1, §4.2).

### 4.4 `public/index.html` (CSS only)

- **The corner-badge rule.** Add `#crew-roster .pm-btn.call, #crew-roster .pm-btn.mic` to the existing selector list `#talk-toggle, #admin > summary, #views-label { … 44px × 40px … }`. Don't copy the rule; join it. Then one override rule for both: `font: 600 11px/1.1 system-ui, sans-serif; white-space: normal; text-align: center; overflow: hidden; flex: 0 0 auto;`. Add `.pm-btn.mic.listening { font-size: 9px }` and `.pm-btn.call.on { background: #e5484d; border-color: transparent; color: #fff }`. Add `:disabled { opacity: .4; cursor: default }`.
- **8 px gaps.** Change `#crew-roster .thread-head` `gap: 10px` → `gap: 8px`, and give `#crew-roster .composer` `gap: 8px`.
- **Check:** the 📞's `getBoundingClientRect()` reads **width 44, height 40** with the label "📞 Call" and with "End". So does the 🎙's, with "🎙 Talk" and "listening…".

## 5. Test cases (all in `test/pm/calls_test.cljs`, ns `pm.calls-test`)

Requires: `[cljs.test :refer [deftest is]] [pm.talk :as talk] [pm.call :as call] [pm.crew :as crew]`.
Name each deftest after its case (e.g. `c01-call-lines-twin`). The tests don't require `pm.ui`
or `pm.core`, and never call `speak!` or recognition: **a headless test turns nothing on.**

### Fixtures (copy verbatim)

```clojure
(defn contact-of [e]          ; = pm.ui/entry-contact with the twin's agent :hal
  (cond (contains? talk/crew-kinds (:kind e)) (:member e)
        (= :hal (:kind e))                     :hal
        (keyword? (:mirror e))                 (:mirror e)
        :else nil))

(def T {:n 9 :seen 0 :open? false
        :entries [{:n 1 :who "Hal" :text "Morning." :kind :hal}
                  {:n 2 :who "Flora" :text "I'm hungry" :kind :creature :mirror :flora}
                  {:n 3 :who "Le Testeur" :text "wants to run ls" :kind :crew-prompt :member :le-testeur :state :pending}
                  {:n 4 :who "You" :text "hi" :kind :crew-say :member :hal :to "Hal" :state :sent}
                  {:n 5 :who "Hal" :text "Do you read me?" :kind :hal}
                  {:n 6 :who "Juniper" :text "Hay, please?" :kind :creature :mirror :juniper}
                  {:n 7 :who "Hal" :text "allowed `ls` for Le Testeur" :kind :crew-log :member :le-testeur}
                  {:n 8 :who "Le Testeur" :text "done" :kind :crew-status :member :le-testeur}
                  {:n 9 :who "Flora" :text "Worms left: 2" :kind :creature :mirror :flora}]})

(def W {:voices  {:hal {:rate 1.0 :pitch 0.9 :lang "en-US"} :creature {:rate 1.1 :pitch 1.6 :lang "en-US"}}
        :walkers [{:agent :hal :name "Hal"}
                  {:agent :le-testeur :name "Le Testeur" :voice {:pitch 1.2}}
                  {:agent :le-docteur :name "Le Docteur" :voice {:rate 99 :lang ""}}]
        :mirrors {:flora   {:name "Flora" :kind :creature}
                  :juniper {:name "Juniper" :kind :creature :voice {:rate 0.9 :pitch 0.8}}}})

```clojure
(def WQ {:asks-player {"le-testeur" {:text "Ship it?" :since 1}}})
(def ROWS [{:member :hal :who "Hal"} {:member :le-testeur :who "Le Testeur"} {:member :flora :who "Flora" :kind :creature}])
(def KEY-ROWS [{:id :hal :agent :hal :name "Hal"} {:id :juniper :name "Juniper" :kind :creature}
               {:id :phone-booth :name "Phone booth" :kind :place}])
```

### Spoken lines: `talk/call-lines`, `talk/call-line?` (only the called contact, only new lines)

| # | call | expected |
|---|---|---|
| c01 | `(mapv :n (talk/call-lines T contact-of :hal 0))` | `[1 5]` (4 is the player's own line) |
| c02 | `(mapv :n (talk/call-lines T contact-of :hal 1))` | `[5]` |
| c03 | `(talk/call-lines T contact-of :hal 5)` and `… :hal 9` | `[]` and `[]` |
| c04 | `(mapv :n (talk/call-lines T contact-of :flora 0))` | `[2 9]` |
| c05 | `(mapv :n (talk/call-lines T contact-of :flora 2))` | `[9]` |
| c06 | `(mapv :n (talk/call-lines T contact-of :juniper 0))` and `… :juniper 6` | `[6]` and `[]` (the second mirror) |
| c07 | `(mapv :n (talk/call-lines T contact-of :le-testeur 0))` | `[3]` (7 crew-log and 8 status are quiet) |
| c08 | `(talk/call-lines T contact-of nil 0)`, `(talk/call-lines nil contact-of :hal 0)` | `[]`, `[]` |
| c09 | `(mapv :n (talk/call-lines T contact-of :hal nil))` | `[1 5]` (nil since = 0) |
| c10 | `(vector? (talk/call-lines T contact-of :flora 0))`; `(= (get-in T [:entries 1]) (first (talk/call-lines T contact-of :flora 0)))` | `true`; `true` (whole entries) |
| c11 | `(talk/call-line? (get-in T [:entries 4]) contact-of :hal)` (n 5) | `true` |
| c12 | `(talk/call-line? (get-in T [:entries 3]) contact-of :hal)` (n 4, yours) | `false` |
| c13 | `(talk/call-line? (get-in T [:entries 1]) contact-of :hal)` (n 2, Flora's) | `false` |
| c14 | `(talk/call-line? (get-in T [:entries 4]) contact-of nil)` | `false` |

### Voice: `talk/call-voice`

| # | call | expected |
|---|---|---|
| c15 | `(talk/call-voice W :hal (get-in T [:entries 4]))` | `{:rate 1.0 :pitch 0.9 :lang "en-US"}` |
| c16 | `(talk/call-voice W :le-testeur (get-in T [:entries 2]))` | `{:rate 1.0 :pitch 1.2 :lang "en-US"}` |
| c17 | `(talk/call-voice W :le-docteur {:n 20 :who "Le Docteur" :text "x" :kind :crew-prompt :member :le-docteur})` | `{:rate 1.0 :pitch 0.9 :lang "en-US"}` (rate 99 and blank lang ignored) |
| c18 | `(talk/call-voice W :flora (get-in T [:entries 1]))` | `{:rate 1.1 :pitch 1.6 :lang "en-US"}` |
| c19 | `(talk/call-voice W :juniper (get-in T [:entries 5]))` | `{:rate 0.9 :pitch 0.8 :lang "en-US"}` |
| c20 | `(talk/call-voice {} :hal (get-in T [:entries 4]))` | `{:rate 1 :pitch 1 :lang "en-US"}` (talk/default-voice) |
| c21 | `(talk/call-voice W :nobody {:n 21 :who "X" :text "y" :kind :hal})` | `{:rate 1.0 :pitch 0.9 :lang "en-US"}` (no walker: the twin's) |

### Call state: start, end, switch replaces

| # | call | expected |
|---|---|---|
| c22 | `(:call (call/start-call {} :hal 1000))` | `{:with :hal :since 1000}` |
| c23 | `(:call (-> {} (call/start-call :hal 1000) (call/start-call :le-testeur 2000)))` | `{:with :le-testeur :since 2000}` |
| c24 | `(:call (-> {} (call/start-call :flora 1000) (call/start-call :juniper 3000)))` | `{:with :juniper :since 3000}` (both mirrors) |
| c25 | `(let [w (call/start-call {} :hal 1000)] (identical? w (call/start-call w :hal 5000)))` | `true` (same contact: `:since` stays 1000) |
| c26 | `(contains? (-> {} (call/start-call :hal 1000) call/end-call) :call)` | `false` |
| c27 | `(let [w {:t 1}] (identical? w (call/end-call w)))` | `true` |
| c28 | `(let [w {:t 1}] [(identical? w (call/start-call w :all 1)) (identical? w (call/start-call w "hal" 1)) (identical? w (call/start-call w nil 1)) (identical? w (call/start-call w :hal nil)) (identical? w (call/start-call w :hal ##NaN))])` | `[true true true true true]` |
| c29 | `[(call/call-with {}) (call/call-with (call/start-call {} :juniper 7))]` | `[nil :juniper]` |

### Ending: `call/end-reason`

| # | call | expected |
|---|---|---|
| c30 | `(call/end-reason nil :hal true)` | `nil` |
| c31 | `(call/end-reason {:with :hal :since 1} :hal true)` | `nil` |
| c32 | `(call/end-reason {:with :hal :since 1} :hal false)` | `:closed` |
| c33 | `(call/end-reason {:with :hal :since 1} nil true)` | `:switched` (‹ Contacts) |
| c34 | `(call/end-reason {:with :hal :since 1} :le-testeur true)`, `… :all true` | `:switched`, `:switched` |
| c35 | `(call/end-reason {:with :hal :since 1} :le-testeur false)` | `:closed` (closed wins) |
| c36 | `[(call/end-reason {:with :flora :since 1} :flora true) (call/end-reason {:with :juniper :since 1} :flora true)]` | `[nil :switched]` |

### The 📞 button and where the 🎙 shows

| # | call | expected |
|---|---|---|
| c37 | `[(call/call-button nil nil) (call/call-button nil :all) (call/call-button {:with :hal :since 1} :all)]` | `[nil nil nil]` |
| c38 | `(call/call-button nil :hal)`; `(call/call-button nil :juniper)` | both `{:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}` |
| c39 | `(call/call-button {:with :hal :since 1} :hal)` | `{:label "End" :on? true :title "End the call"}` |
| c40 | `(call/call-button {:with :hal :since 1} :flora)` | `{:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}` |
| c41 | `[(call/mic-shown? {:with :hal} {:member :hal}) (call/mic-shown? {:with :hal} {:member :le-testeur}) (call/mic-shown? nil {:member :hal}) (call/mic-shown? {:with :flora} {:member :flora :kind :creature})]` | `[true false false false]` |

### The mic: `call/mic-step`, `call/mic-line?`, `call/mic-view`

| # | call | expected |
|---|---|---|
| c42 | `(call/mic-step nil [:start :hal])`; `(call/mic-step {:status :idle} [:start :le-testeur])` | `{:status :listening :member :hal :text ""}`; `{:status :listening :member :le-testeur :text ""}` |
| c43 | `(call/mic-step {:status :listening :member :hal :text ""} [:interim "do you"])` | `{:status :listening :member :hal :text "do you"}` |
| c44 | `(call/mic-step {:status :idle} [:interim "x"])` | `{:status :idle}` |
| c45 | `(call/mic-step {:status :listening :member :hal :text "do you"} [:final "Do you read me?"])`, `… [:end]`, `… [:stop]` | `{:status :idle}` each |
| c46 | `(call/mic-step {:status :listening :member :hal :text ""} [:error "not-allowed"])`, `(call/mic-step {:status :idle} [:error "service-not-allowed"])` | `{:status :off :why :refused}` each |
| c47 | `(call/mic-step {:status :listening :member :hal :text ""} [:error "audio-capture"])` | `{:status :off :why :no-mic}` |
| c48 | `(call/mic-step {:status :listening :member :hal :text ""} [:error "no-speech"])`, `… [:error "aborted"]`, `… [:error "network"]`, `… [:error "start-failed"]` | `{:status :idle}` each |
| c49 | `(call/mic-step {:status :idle} [:unsupported])` | `{:status :off :why :unsupported}` |
| c50 | with `O {:status :off :why :refused}`: `(call/mic-step O [:start :hal])`, `… [:end]`, `… [:error "no-speech"]`, `… [:unsupported]` | `O` each (`:off` sticks) |
| c51 | `(call/mic-step nil [:bogus])`, `(call/mic-step nil [:end])` | `{:status :idle}`, `{:status :idle}` |
| c52 | `[(call/mic-line? nil {:status :off :why :refused}) (call/mic-line? {:status :listening :member :hal :text ""} {:status :off :why :unsupported}) (call/mic-line? {:status :off :why :refused} {:status :off :why :refused}) (call/mic-line? {:status :listening} {:status :idle})]` | `[true true false false]` |
| c53 | `(call/mic-view nil)`, `(call/mic-view {:status :idle})` | `{:label "🎙 Talk" :disabled? false :title "Talk instead of typing"}` each |
| c54 | `(call/mic-view {:status :listening :member :hal :text "do"})` | `{:label "listening…" :disabled? true :title "Listening… it stops when you pause"}` |
| c55 | `(call/mic-view {:status :off :why :refused})` | `{:label "🎙 Talk" :disabled? true :title "The browser refused the microphone; allow it for this page to talk"}` |
| c56 | `(call/mic-view {:status :off :why :unsupported})`; `(call/mic-view {:status :off :why :no-mic})`; `(call/mic-view {:status :off})` | titles `"This browser can't hear you (no speech recognition); type instead"`; `"No microphone found; type instead"`; `"The mic is off; type instead"`; each `:label "🎙 Talk" :disabled? true` |
| c57 | `call/no-mic-line` | `"No mic here. Type to me."` |

### Recognised text: on-say's decision, `call/say-route`

| # | call | expected |
|---|---|---|
| c58 | `(call/say-route WQ :le-testeur "  yes  ")` | `{:route :answer :agent "le-testeur" :text "yes"}` (pending question → answered) |
| c59 | `(call/say-route WQ :hal "Do you read me?")` | `{:route :say :member :hal :text "Do you read me?"}` |
| c60 | `(call/say-route WQ :le-docteur " hello ")`; `(call/say-route {} :hal " hi ")` | `{:route :say :member :le-docteur :text "hello"}`; `{:route :say :member :hal :text "hi"}` |
| c61 | `[(call/say-route WQ :le-testeur "   ") (call/say-route WQ :hal "") (call/say-route WQ :hal nil) (call/say-route WQ "hal" "hi")]` | `[nil nil nil nil]` |

### Plate and rows, with and without a call

| # | call | expected |
|---|---|---|
| c62 | `(crew/plate-name {:titles {}} {:agent :hal :name "Hal"} {:with :hal :since 1})` | `"Hal · on a call"` |
| c63 | `(crew/plate-name {:titles {}} {:agent :hal :name "Hal"} nil)`; `… {:with :le-testeur :since 1}` | `"Hal"`; `"Hal"` |
| c64 | `(crew/plate-name {:titles {:le-docteur "Le Docteur (docs)"}} {:agent :le-docteur :name "Le Docteur"} {:with :le-docteur :since 1})` | `"Le Docteur (docs) · on a call"` |
| c65 | `(crew/plate-name nil {:agent :x :name "X"} {:with :x})`; `(crew/plate-name nil {:name "X"} {:with nil})` | `"X · on a call"`; `"X"` (nil agent never matches) |
| c66 | `(crew/plate-name {:titles {}} {:agent :hal :name "Hal"})` | `"Hal"` (2-arity untouched) |
| c67 | `(call/mark-on-call ROWS {:with :hal :since 1} :member)` | `[{:member :hal :who "Hal" :on-call? true} {:member :le-testeur :who "Le Testeur"} {:member :flora :who "Flora" :kind :creature}]` |
| c68 | `(call/mark-on-call ROWS {:with :flora :since 1} :member)` | `[{:member :hal :who "Hal"} {:member :le-testeur :who "Le Testeur"} {:member :flora :who "Flora" :kind :creature :on-call? true}]` |
| c69 | `(= ROWS (call/mark-on-call ROWS nil :member))`; `(vector? (call/mark-on-call (seq ROWS) nil :member))` | `true`; `true` |
| c70 | `(mapv :on-call? (call/mark-on-call KEY-ROWS {:with :juniper :since 1} #(or (:agent %) (:id %))))`; the same with `{:with :hal …}` | `[nil true nil]`; `[true nil nil]` (crew key: second mirror, and the twin) |
| c71 | `[(call/glyph-name "Hal" true) (call/glyph-name "Juniper" true) (call/glyph-name "Hal" false) (call/glyph-name "Hal" nil) (call/glyph-name nil true) (call/glyph-name nil false)]` | `["Hal 📞" "Juniper 📞" "Hal" "Hal" "📞" ""]` |

**71 cases.** Every entry point is covered with the twin, a crew member and both mirrors
(`:flora` and `:juniper`).

## 6. Decisions this spec makes (the quest left them open)

1. **🎙 appears only during a call** with that contact (`mic-shown?`). Outside a call, the phone stays text-only, and "ending stops recognition" means the button goes away.
2. **Clicking 🎙 while listening does nothing** (the button is disabled). `continuous false` ends the recognition on the first pause.
3. **Unsupported is found on the click**, not when the page loads. The twin's line comes in answer to the player's tap, never unprompted. After that the button stays disabled, with the reason in its title.
4. **Which lines a call speaks = `talk/counts?`**, the unread rule: the quiet crew log, status flips and the player's own lines aren't spoken.
5. **A crew member's own `:say`** (`bb hal-say --as <member>`) is a bubble and not a talk entry today (`world` routes only the twin's `:say` to `:hal`), so a call with a crew member speaks their prompt lines only. Turning other members' says into talk entries is a follow-up, not this round.
6. **GDD.** §7 open question 2 ("Voice in and out, or text only?") is answered by this round. Hal updates that line at commit, since there's no docs member in this round.

## 7. Note and hand checks

The builder writes `crew/runs/calls-v0-20261122-1708/note.md` (≤ 25 lines): what was built (files and fns), what the gate says, and what the reviewer should click. The builder doesn't start services or the dev server. These checks are **Hal's** in the dev build:

1. Open 💭 → Hal → **📞 Call**. In the console: `getBoundingClientRect()` of `.pm-btn.call` reads 44 × 40, and after the click (**End**, red) still 44 × 40.
2. `bb hal-say "Do you read me?"` → spoken aloud in the twin's voice with voice **off**. The caption shows. Hal's plate reads `Hal · on a call`. His crew key row and contact row read `Hal 📞`.
3. **🎙 Talk** → allow the mic → speak. The interim words fill the composer, the button reads `listening…` (still 44 × 40), and the final words land in Hal's thread as yours (with the bridge off, Hal answers with the no-bridge line, and that's spoken too).
4. End (or ‹ Contacts, or close 💭) stops the speech mid-line. The plate goes back to `Hal`, and the 📞 glyphs go away.
5. Call a creature (Flora, then Juniper): there's a 📞 but no composer and no 🎙, and its next line is spoken in its own voice.
6. Block the mic for the page → 🎙 is disabled with the reason in its title, and Hal says "No mic here. Type to me." once.
7. `bb demo-build`: call Hal, wait ≤ 45 s, and the SAMPLE line is spoken.
