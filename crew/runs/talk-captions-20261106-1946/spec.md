# Spec: talk captions (Hal you can hear and read)

Quest: `.crew/quests/talk-captions.md`. Round dir: `crew/runs/talk-captions-20261106-1946/`.

This round builds on the content-in-game round, which is in the tree but not committed (125 tests pass).

Whenever Hal or a creature says a new line:
- it goes into a **talk log** (pure `pm.talk`, in the world atom at `:talk`);
- a **caption** shows it in large type, one line at a time;
- the line is **spoken** while voice is on.

A 💭 panel lists the history, which survives a reload. Hal's text leaves the status line.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/talk.cljs` (new), `src/pm/ui.cljs` (new), `src/pm/core.cljs`, `public/index.html` (`<style>` and markup only, `<base>` untouched), `resources/estate.edn` (`:voices`, Juniper's `:voice`), `README.md` (one sentence) | everything else, especially `resources/content/*.edn`, `src/pm/content.cljs`, `world.cljs`, `plan.cljs`, `overlay.cljs`, `scene.cljs`, all tests |
| tester (`le-testeur`) | `test/pm/talk_test.cljs` (new) | every `src/`, `resources/` and `public/` file, and the existing tests |

Rules for everyone:
- No npm deps, no three.js in `pm.ui`, 0 warnings.
- No code names a mirror; `"Hal"` is the twin, not a mirror.
- Storage and speech failures are silent.
- Nobody reads `assets/private/`, `public/kit/` or `public/state/`.

## 1. Data

### 1.1 Talk state: `(:talk world)`

```clojure
{:entries   [{:n 1 :who "Hal"   :text "Welcome back." :at 1794013200000 :kind :hal
              :show-at 1794013200000 :show-until 1794013202500}
             {:n 2 :who "Flora" :text "I'm hungry"    :at 1794013200000 :kind :creature :mirror :flora
              :show-at 1794013202500 :show-until 1794013205000}]
 :n         2          ; entries ever appended (monotonic; numbers the next entry)
 :seen      0          ; :n when the panel was last opened/closed
 :open?     false      ; 💭 panel open (not saved)
 :voice?    false      ; 🔊 on (not saved; browsers need a click per page load)
 :queue-end 1794013205000   ; when the last scheduled caption ends (not saved)
 :last-hal  ["Welcome back." 1794013206000]          ; [text until] of the last Hal line logged (not saved)
 :last-say  {:flora "I'm hungry" :juniper "Hay, please?"}}  ; last :say text seen per mirror (not saved)
```

- `:show-at` and `:show-until` exist only on entries logged in this session. Restored entries never caption again.
- The dedupe memory (`:last-hal`, `:last-say`) is per session. After a reload, the new jack-in greeting and the creatures' current bubbles are logged again. That's a new session, not a per-frame duplicate.

### 1.2 Saved form (localStorage key `"pm.talk.v1"`, `pr-str` text)

```clojure
{:v 1 :n 2 :seen 0
 :entries [{:n 1 :who "Hal" :text "Welcome back." :at 1794013200000 :kind :hal}
           {:n 2 :who "Flora" :text "I'm hungry" :at 1794013200000 :kind :creature :mirror :flora}]}
```

### 1.3 Voices as data (`resources/estate.edn`, builder)

Add a top-level key, after `:hud`:

```clojure
:voices {:hal      {:rate 1.0 :pitch 0.9 :lang "en-US"}   ; Hal: even, a little low
         :creature {:rate 1.1 :pitch 1.6 :lang "en-US"}}  ; creatures: higher, quicker
```

Juniper gets a mirror-level override (a horse sounds lower), placed after her `:name`:
`:voice {:rate 0.9 :pitch 0.8}`.

Lookup order is `default-voice` ← `(:voices world)` at the entry's `:kind` ← the entry's mirror `:voice`. Each layer applies only its valid fields:
- `:rate`: a number in [0.1, 10]
- `:pitch`: a number in [0, 2]
- `:lang`: a non-blank string

## 2. `pm.talk` (new, `src/pm/talk.cljs`; pure)

Requires: `cljs.reader`, `clojure.string`, `[pm.world :as w]`. No atoms, no `js/Date`, no DOM.

Every function that takes `talk` treats nil as `empty-talk`.

| Def / signature | Value / behaviour |
|---|---|
| `hal-name` | `"Hal"` |
| `cap` | `100` |
| `caption-min-ms` · `caption-max-ms` · `caption-base-ms` · `caption-ms-per-char` | `2500` · `9000` · `1200` · `60` |
| `default-voice` | `{:rate 1 :pitch 1 :lang "en-US"}` |
| `empty-talk` | `{:entries [] :n 0 :seen 0 :open? false :voice? false :queue-end 0 :last-hal nil :last-say {}}` |
| `(caption-ms text)` | Clamp `base + per-char × (count text)` to [min, max]. A non-string counts as length 0, giving 2500. |
| `(say-text world mirror-id now)` | `(str/join " · " (keep :say (w/markers world mirror-id now)))`. Unknown mirror → `""`. |
| `(append talk {:keys [who text kind mirror]} now)` | See below |
| `(observe talk world now)` | See below |
| `(caption talk now)` | The entry with numbers `:show-at` ≤ `now` < `:show-until`, newest first. Otherwise nil, including when `now` isn't a number. |
| `(caption-text entry)` | `(str who " — " text)` (em dash). nil entry → nil. |
| `(unread talk)` | 0 when `:open?`. Otherwise the count of entries with `:n` > `:seen`. |
| `(badge-text talk)` | `""` when unread is 0, `"99+"` when unread > 99, else `(str unread)` |
| `(open-panel talk)` | `(assoc talk :open? true :seen (:n talk))` |
| `(close-panel talk)` | `(assoc talk :open? false :seen (:n talk))` |
| `(toggle-panel talk)` | `open-panel` when closed, `close-panel` when open |
| `(toggle-voice talk)` | `(update talk :voice? not)` |
| `(close-key? key open? tag)` | true only when `key` is `"Escape"`, `open?` is truthy, and `tag` isn't `"INPUT"`, `"TEXTAREA"` or `"SELECT"` |
| `(clock-text at offset-min)` | See below |
| `(voice-for world entry)` | §1.3 lookup → `{:rate :pitch :lang}`. Always complete. |
| `(save-text talk)` | `(pr-str {:v 1 :n n :seen seen :entries (mapv #(select-keys % [:n :who :text :at :kind :mirror]) entries)})` |
| `(restore text)` | See below. Never throws. |

### `append`

1. Return `talk` unchanged (the identical value) when any of these fail:
   - `who` is a non-blank string;
   - `text` is a non-blank string;
   - `now` is a finite number.
2. Otherwise build the entry:
   - `n' = (inc n)`
   - `start = (max now queue-end)`
   - `end = (+ start (caption-ms text))`
   - entry: `{:n n' :who who :text text :at now :kind k :show-at start :show-until end}`, plus `:mirror` only when it is a keyword.
   - `k` is `kind` when it is a keyword, else `:other`.
3. `conj` it to `:entries`. Keep the last `cap` entries, as a vector.
4. Set `:n n'` and `:queue-end end`. When `:open?`, also set `:seen n'`: lines arriving while the panel is open are read.

Captions never overlap. They queue in order.

### `observe`

Run once per frame. Returns `talk` itself (`identical?`) when nothing changed. Steps, in order:

1. **Hal.**
   - Inputs: `h = (:hal world)`. Valid when `(:text h)` is a string, `(:until h)` is a number, and `now < until`.
   - When valid and `[text until]` ≠ `:last-hal`: append `{:who hal-name :text text :kind :hal}` at `now`, and set `:last-hal [text until]`.
   - The same text with a new `:until` is a new utterance and is logged.
   - A line that has already expired when first observed is not logged.
2. **Mirrors**, over `(sort (keys (:mirrors world)))`:
   - `s = (say-text world id now)` and `prev = (get-in talk [:last-say id])`.
   - When `s` ≠ `prev`, set `[:last-say id] s`.
   - When `s` ≠ `prev` and `s` is non-empty, also append `{:who (or (:name m) (name id)) :text s :kind (if (keyword? (:kind m)) (:kind m) :creature) :mirror id}`.
   - So the first sighting of an empty bubble logs nothing, and signs (`:sign`) are never speech.

### `clock-text`

- `at` must be a finite number, else return `""`. `offset-min` is the minutes to add to UTC; a non-number counts as 0.
- `ms = (mod (+ at (* offset-min 60000)) 86400000)`, `h = floor(ms/3600000)`, `m = floor((ms mod 3600000)/60000)`.
- Result: `"<h12>:<mm> <AM|PM>"`, where `h12 = (mod h 12)` with 0 shown as 12, `mm` is zero-padded, and AM means `h < 12`.

### `restore`

1. Parse with `cljs.reader/read-string` in try/catch. Anything that isn't a map with `:v 1` → `empty-talk`.
2. **Entries.** `(:entries m)` must be sequential, otherwise use `[]`. Keep only maps with all of:
   - `:n` a positive integer
   - `:who` and `:text` non-blank strings
   - `:at` a finite number
   - `:kind` a keyword

   For each kept entry:
   - reduce it to `:n :who :text :at :kind`;
   - add `:mirror` only when it is a keyword;
   - drop any show keys.

   Sort by `:n` ascending and keep the last `cap`.
3. **Counters.**
   - `n`: the largest of `(:n m)` when it is an integer ≥ 0, the largest entry `:n`, and 0.
   - `seen`: `(:seen m)` clamped to [0, n] when it is an integer; otherwise `n`.
4. Return `(assoc empty-talk :entries entries :n n :seen seen)`.

## 3. `pm.ui` (new, `src/pm/ui.cljs`; DOM and speech only, no game logic)

Every DOM lookup is `(js/document.getElementById id)`, and a missing element is a no-op. All speech calls are in try/catch and fail silently.

| Signature | Behaviour |
|---|---|
| `(speech?)` | true when `window.speechSynthesis` and `window.SpeechSynthesisUtterance` both exist |
| `(speak! text voice)` | When `(speech?)` and `text` is a string: build `SpeechSynthesisUtterance.` with `text`, set `.rate`, `.pitch` and `.lang` from `voice`, then `.speak`. This is the only function that starts speech. |
| `(stop-speech!)` | `.cancel` when `(speech?)` |
| `(show-caption! entry)` | `#caption-who` textContent = `:who`; `#caption-text` = `(str " — " text)`; add class `show` to `#caption` |
| `(hide-caption!)` | Remove class `show`. The text stays during the CSS fade-out. |
| `(set-badge! text)` | `#talk-badge` textContent = `text` |
| `(render-panel! entries offset-of on-pick)` | Replace `#talk-list`'s children with one `<li>` per entry, oldest first. Each has three spans, `.time` (`(talk/clock-text at (offset-of at))`), `.who` and `.text`, all set with **textContent** (never innerHTML). A click on an `<li>` calls `(on-pick entry)`. Then scroll `#talk-panel` to the bottom (`scrollTop = scrollHeight`). |
| `(set-panel-open! open?)` | Set or clear the `hidden` attribute on `#talk-panel` |
| `(set-voice-button! on?)` | `#voice-toggle` textContent `"🔊 voice on"` / `"🔊 voice off"`; toggle class `on` |
| `(hide-voice-button!)` | `#voice-toggle` `style.display = "none"` |

## 4. `public/index.html` (builder)

Markup:
- `<button id="voice-toggle">🔊 voice off</button>` inside `#brook`, right after `#brook-toggle`. It is styled by the existing `#brook button` rule.
- After `#brook`:

```html
<div id="caption" aria-live="polite"><b id="caption-who"></b><span id="caption-text"></span></div>
<button id="talk-toggle" title="What was said">💭<span id="talk-badge"></span></button>
<div id="talk-panel" hidden><ol id="talk-list"></ol></div>
```

Styles (in the existing `<style>`):

| Element | Style |
|---|---|
| `#caption` | `position: fixed; left: 50%; bottom: 96px; transform: translateX(-50%); max-width: min(760px, calc(100vw - 32px)); z-index: 210; padding: 12px 20px; border-radius: 12px; background: rgba(20,24,32,.80); color: #f3efe2; font: 22px/1.35 system-ui, sans-serif; text-align: center; opacity: 0; transition: opacity .5s; pointer-events: none;` |
| `#caption.show` | `opacity: 1;` |
| `#talk-toggle` | `position: fixed; left: 14px; bottom: 64px; z-index: 200; font: 22px system-ui; background: rgba(0,0,0,.35); border: 0; border-radius: 8px; padding: 4px 10px; cursor: pointer;` |
| `#talk-badge` | `font: 600 12px system-ui; color: #f3efe2; margin-left: 4px;`. Empty text shows nothing. |
| `#talk-panel` | `position: fixed; left: 14px; bottom: 116px; width: min(380px, calc(100vw - 28px)); max-height: 50vh; overflow-y: auto; z-index: 220; background: rgba(20,24,32,.88); color: #f3efe2; border-radius: 10px; font: 16px/1.4 system-ui, sans-serif;` |
| `#talk-list` | `list-style: none; margin: 0; padding: 8px 12px;` |
| `#talk-list li` | `padding: 4px 0; cursor: pointer;` |
| `.time` | `opacity: .6; margin-right: 6px;` |
| `.who` | `font-weight: 600; margin-right: 6px;` |
| `#talk-panel[hidden]` | `display: none;` |

The caption's `bottom: 96px` keeps it above the kit's bottom button bar. The panel sits bottom-left, above `#brook` and `#talk-toggle`, clear of the kit's right-hand studio panel. Hal tunes the two `bottom` values live if the kit's bar overlaps.

## 5. `pm.core` changes (builder; checked live)

Requires: `[pm.talk :as talk]` and `[pm.ui :as ui]`.

1. **Status line.** In `hud-mirrors-suffix`, replace the `content/hud-segments` line with:

   ```clojure
   (when-let [m (content/mail-line content/sample-inbox)] (str " · " m))
   ```

   Hal's text leaves the HUD. `content/hud-segments` and `hal-segment` stay as they are (tested, no longer called).
2. **Storage.**
   - Add `(def talk-key "pm.talk.v1")` and `(defonce ^:private !talk-text (atom nil))`.
   - `read-talk` and `write-talk!` are try/catch wrappers like `read-cache` and `write-cache!`.
   - `restore-talk!` does `(reset! !talk-text text)` then `(swap! !world assoc :talk (talk/restore text))`.
   - A watch, `(add-watch !world ::talk-cache on-talk-change!)`, skips unless `(:talk old)` and `(:talk new)` are not identical. Otherwise it computes `(talk/save-text (:talk new))` and writes it only when it differs from `@!talk-text`.
3. **The tick.** `(defonce ^:private !talk-ui (atom {:shown nil :badge nil :listed nil}))`. `(talk-tick! now)` is called in `frame` right after `scene/update!` with `(js/Date.now)`:
   1. `t' = (talk/observe (:talk @!world) @!world now)`. When it isn't identical to the current `:talk`, `(swap! !world assoc :talk t')`.
   2. `e = (talk/caption t' now)`.
      - When `(:n e)` ≠ `:shown`: call `(ui/show-caption! e)` and, when `(:voice? t')`, `(ui/speak! (:text e) (talk/voice-for @!world e))`; then set `:shown (:n e)`.
      - When `e` is nil and `:shown` is non-nil: call `(ui/hide-caption!)` and set `:shown nil`.
      - Speech starts when the caption starts, so the two stay together.
   3. When `(talk/badge-text t')` ≠ `:badge`: call `ui/set-badge!` and store it.
   4. When `(:open? t')` and `[(:n t') true]` ≠ `:listed`: call `(ui/render-panel! (:entries t') offset-of pick!)` and set `:listed`. When the panel is closed, set `:listed nil`.
      - `offset-of` is `(fn [at] (- (.getTimezoneOffset (js/Date. at))))`, so DST is right per entry.
4. **`pick!` (entry click).** When `(:voice? (:talk @!world))`: call `(ui/stop-speech!)`, then `(ui/speak! (:text entry) (talk/voice-for @!world entry))`. It doesn't caption again. When voice is off, it does nothing.
5. **`wire-talk-ui!`**, called in `init!` after `restore-talk!`:
   - **`#voice-toggle`.** When `(ui/speech?)` is false, call `ui/hide-voice-button!` and wire nothing else. Otherwise, on click (calling `.stopPropagation`):
     1. `(swap! !world update :talk talk/toggle-voice)`;
     2. then `(ui/set-voice-button! on?)`;
     3. when turned on, `(ui/speak! "Voice on." (talk/voice-for @!world {:kind :hal}))` (spoken only, not logged);
     4. when turned off, `(ui/stop-speech!)`.
   - **`#talk-toggle`.** On click (calling `.stopPropagation`): `(swap! !world update :talk talk/toggle-panel)`, then `(ui/set-panel-open! (:open? (:talk @!world)))`. The next tick renders the list.
6. **Escape.** The capture-phase keydown listener in `init!` becomes:

   ```clojure
   (fn [^js e]
     (if (talk/close-key? (.-key e) (:open? (:talk @!world)) (some-> (.-target e) .-tagName))
       (do (.preventDefault e) (.stopPropagation e)
           (swap! !world update :talk talk/close-panel)
           (ui/set-panel-open! false))
       (on-key! e)))
   ```

   With the panel closed, Escape goes through to `on-key!` and the kit untouched. `on-key!` itself is unchanged.
7. **`init!` order:** keydown → `restore-talk!` → `wire-talk-ui!` → `add-watch ::talk-cache` → `restore-cache!` → `add-watch ::state-cache` → `start-state-poll!` → `kit/load!`. The jack-in greeting is still said in `start-kit!` and `start-scaffold!`, and the tick picks it up.
   - The existing `::state-cache` watch already skips changes where `:mirrors` is identical, so `:talk` writes never touch the state cache.

`README.md` gets one sentence: captions show what Hal and the creatures say; 🔊 reads them aloud with the browser's speech; 💭 scrolls back (kept in `localStorage` under `pm.talk.v1`, last 100 lines).

## 6. Tests: `test/pm/talk_test.cljs` (new, `ns pm.talk-test`)

Requires: `cljs.test`, `[pm.talk :as tk]`, `[pm.world :as w]`, `[pm.content :as c]`, `[pm.fixtures :as fx]`.

```clojure
(def A    1794013200000)        ; Fri 2026-11-06 17:00 PST = day 20764, 01:00 UTC
(def D0   1794009600000)        ; 00:00 UTC that day
(def D2   172800000)
(def H12  43200000)
(def NEXT (+ A 82800000))       ; 1794096000000, first ms of day 20765
(def W  (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))  ; "I'm hungry" / "Hay, please?"
(def WH (assoc W :hal {:event :jack-in :text "Welcome back." :until (+ A 6000)}))
(def T0 tk/empty-talk)
(def T3 (tk/observe T0 WH A))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn hal [text] {:who "Hal" :text text :kind :hal})
(defn texts [t] (mapv :text (:entries t)))
(defn appends [k] (reduce (fn [t i] (tk/append t (hal (str "l" i)) (+ A i))) T0 (range 1 (inc k))))
(def H {:jack-in ["hi" "hello" "hey"] :chore-done ["{name}: {chore} done" "did {chore} for {name}"]})
(def E1 {:n 1 :who "Hal"     :text "Welcome back." :at A :kind :hal
         :show-at A :show-until (+ A 2500)})
(def E2 {:n 2 :who "Flora"   :text "I'm hungry"    :at A :kind :creature :mirror :flora
         :show-at (+ A 2500) :show-until (+ A 5000)})
(def E3 {:n 3 :who "Juniper" :text "Hay, please?"  :at A :kind :creature :mirror :juniper
         :show-at (+ A 5000) :show-until (+ A 7500)})
(defn bare [e] (dissoc e :show-at :show-until))
```

Caption lengths used:

| Text | Length | Duration (ms) |
|---|---|---|
| "Welcome back." | 13 | 1980 → 2500 |
| "I'm hungry" | 10 | 2500 |
| "Hay, please?" | 12 | 2500 |
| "Got it." | 7 | 2500 |
| "did feed for Flora" | 18 | 2280 → 2500 |
| "You're jacked in. Let's get going." | 34 | 1200 + 2040 = 3240 |

#### The talk log

**t01-caption-ms**
- `(tk/caption-ms "Welcome back.")` → 2500; `(tk/caption-ms "You're jacked in. Let's get going.")` → 3240
- strings of length 21 → 2500; 22 → 2520; 30 → 3000; 130 → 9000; 200 → 9000 (e.g. `(apply str (repeat 22 "a"))`)
- `""` → 2500; `nil` → 2500

**t02-say-text-both-mirrors**
- `(tk/say-text W :flora A)` → `"I'm hungry"`; `(tk/say-text W :juniper A)` → `"Hay, please?"`
- `(tk/say-text (w/chore-done W (ev :flora :feed A :key)) :flora (+ A 1))` → `""`
- `(tk/say-text W :rock A)` → `""`

**t03-append**
- `(tk/append T0 (hal "Welcome back.") A)` → `(assoc T0 :entries [(dissoc E1 :mirror)] :n 1 :queue-end (+ A 2500))`. E1 has no `:mirror`, so this equals `(assoc T0 :entries [E1] :n 1 :queue-end (+ A 2500))`.
- Then `(tk/append that (hal "You're jacked in. Let's get going.") (+ A 1000))`: the newest entry is `{:n 2 :who "Hal" :text "You're jacked in. Let's get going." :at (+ A 1000) :kind :hal :show-at (+ A 2500) :show-until (+ A 5740)}` and `:queue-end` is `(+ A 5740)`.
- Then `(hal "Got it.")` at `(+ A 10000)`: newest `:show-at (+ A 10000)`, `:show-until (+ A 12500)` (the queue was idle, so it starts now).

**t04-append-edges**
- each of these is `= T0`:
  - `(tk/append T0 {:who "" :text "x" :kind :hal} A)`
  - `(tk/append T0 {:who nil :text "x"} A)`
  - `(tk/append T0 {:who "Hal" :text "  "} A)`
  - `(tk/append T0 (hal "x") nil)`
  - `(tk/append T0 (hal "x") ##NaN)`
- `(:kind (peek (:entries (tk/append T0 {:who "Hal" :text "x" :kind "hal"} A))))` → `:other`
- `(contains? (peek (:entries (tk/append T0 {:who "F" :text "x" :kind :creature :mirror "flora"} A))) :mirror)` → false
- `(:n (tk/append nil (hal "x") A))` → 1

**t05-cap**, with `TC = (appends 105)`:
- `(count (:entries TC))` → 100; `(vector? (:entries TC))` → true; `(:n TC)` → 105
- `(first (:entries TC))`: `:n` 6, `:text "l6"`; `(peek (:entries TC))`: `:n` 105, `:text "l105"`
- `tk/cap` → 100

**t06-observe-first-frame-hal-and-both-mirrors**
- `(:entries T3)` → `[E1 E2 E3]`
- `(:last-hal T3)` → `["Welcome back." 1794013206000]`
- `(:last-say T3)` → `{:flora "I'm hungry" :juniper "Hay, please?"}`
- `(:n T3)` → 3; `(:queue-end T3)` → `(+ A 7500)`; `(:seen T3)` → 0
- `(texts (tk/observe T0 W A))` → `["I'm hungry" "Hay, please?"]` (no `:hal`, so no Hal entry)

**t07-logged-once-not-per-frame**
- `(identical? T3 (tk/observe T3 WH (+ A 16)))` → true
- `(identical? T3 (reduce (fn [t i] (tk/observe t WH (+ A (* 16 i)))) T3 (range 60)))` → true
- `(count (:entries (reduce (fn [t i] (tk/observe t WH (+ A (* 16 i)))) T0 (range 60))))` → 3

**t08-hal-new-lines**
- `(identical? T3 (tk/observe T3 WH (+ A 6000)))` → true
- `(texts (tk/observe T0 WH (+ A 6000)))` → `["I'm hungry" "Hay, please?"]` (the greeting had already expired)
- With `WH2 = (assoc W :hal {:event :chore-done :text "Got it." :until (+ A 20000)})` and `T4 = (tk/observe T3 WH2 (+ A 14000))`:
  - `(peek (:entries T4))` → `{:n 4 :who "Hal" :text "Got it." :at (+ A 14000) :kind :hal :show-at (+ A 14000) :show-until (+ A 16500)}`
  - `(identical? T4 (tk/observe T4 WH2 (+ A 15000)))` → true
- `(tk/observe T4 (assoc W :hal {:event :chore-done :text "Got it." :until (+ A 30000)}) (+ A 25000))`: 5 entries; the newest has `:n` 5, `:text "Got it."` and `:show-at (+ A 25000)` (the same words said again)
- `(texts (tk/observe T0 (assoc W :hal {:text nil :until (+ A 9000)}) A))` → `["I'm hungry" "Hay, please?"]`

**t09-flora-bubble-changes**
- `F1 = (w/chore-done WH (ev :flora :feed (+ A 1000) :key))` and `T5 = (tk/observe T3 F1 (+ A 1000))`:
  - `(count (:entries T5))` → 3
  - `(:last-say T5)` → `{:flora "" :juniper "Hay, please?"}`
  - `(identical? T3 T5)` → false
- `T6 = (tk/observe T5 F1 (+ A D2 1000))`:
  - `(count (:entries T6))` → 4
  - `(peek (:entries T6))` → `{:n 4 :who "Flora" :text "I'm hungry" :at 1794186001000 :kind :creature :mirror :flora :show-at 1794186001000 :show-until 1794186003500}`
- `(identical? T3 (tk/observe T3 (assoc-in WH [:mirrors :flora :state :stock] 2) (+ A 16)))` → true (a low-worms *sign* isn't speech)

**t10-juniper-bubble-changes**
- `J1 = (w/chore-done WH (ev :juniper :hay (+ A 2000) :key))` and `T7 = (tk/observe T3 J1 (+ A 2000))`:
  - `(count (:entries T7))` → 3
  - `(get-in T7 [:last-say :juniper])` → `""`
- `(peek (:entries (tk/observe T7 J1 (+ A 2000 H12))))` → `{:n 4 :who "Juniper" :text "Hay, please?" :at 1794056402000 :kind :creature :mirror :juniper :show-at 1794056402000 :show-until 1794056404500}`

**t11-day-rollover-both-mirrors**
- Setup: `WL = (assoc W :lines {:mirrors {:flora {:hungry ["h0" "h1" "h2" "h3" "h4"]} :juniper {:hungry ["hay0" "hay1" "hay2"]}}})` and `TL = (tk/observe T0 WL A)`. Seeds: Flora 21296 → index 1, then 2; Juniper 21529 → 1, then 2.
- `(texts TL)` → `["h1" "hay1"]`
- `(identical? TL (tk/observe TL WL (+ A 82799999)))` → true
- `TL2 = (tk/observe TL WL NEXT)`:
  - `(texts TL2)` → `["h1" "hay1" "h2" "hay2"]`
  - `(mapv :who (:entries TL2))` → `["Flora" "Juniper" "Flora" "Juniper"]`
  - `(nth (:entries TL2) 2)` has `:show-at 1794096000000 :show-until 1794096002500`
  - `(nth (:entries TL2) 3)` has `:show-at 1794096002500 :show-until 1794096005000`

**t12-generic-mirror** (the code names none)
- Setup: `G = (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1} :rules [{:when [:< :water 5] :say "thirsty"} {:when [:< :water 5] :say "dry {water}" :sign "x"} {:when [:< :water 5] :sign "only a sign"}]}]}))`
- `(tk/say-text G :fern A)` → `"thirsty · dry 1"`
- `(:entries (tk/observe T0 G A))` → `[{:n 1 :who "fern" :text "thirsty · dry 1" :at A :kind :creature :mirror :fern :show-at A :show-until (+ A 2500)}]`
- `G2 = (w/load-estate (pr-str {:mirrors [{:id :fern :name "Fern" :kind :plant :state {:water 1} :rules [{:when [:< :water 5] :say "thirsty"}]}]}))`; `(peek (:entries (tk/observe T0 G2 A)))` has `:who "Fern"` and `:kind :plant`
- `(tk/say-text (assoc-in G [:mirrors :fern :state :water] 9) :fern A)` → `""`

#### Caption, panel and keys

**t13-caption-schedule** (on T3)
- `A` → E1; `(+ A 2499)` → E1; `(+ A 2500)` → E2; `(+ A 4999)` → E2; `(+ A 5000)` → E3; `(+ A 7499)` → E3
- `(+ A 7500)` → nil; `(- A 1)` → nil
- `(tk/caption T0 A)`, `(tk/caption nil A)` and `(tk/caption T3 nil)` → nil

**t14-caption-text**
- `(tk/caption-text E1)` → `"Hal — Welcome back."`; `(tk/caption-text E2)` → `"Flora — I'm hungry"`
- `(tk/caption-text nil)` → nil

**t15-unread-open-close**
- `(tk/unread T3)` → 3; `(tk/badge-text T3)` → `"3"`
- `O = (tk/open-panel T3)`: `(:open? O)` true; `(:seen O)` 3; `(tk/unread O)` 0; `(tk/badge-text O)` `""`
- `O2 = (tk/append O (hal "x") (+ A 9000))`: `(:seen O2)` 4; `(tk/unread O2)` 0
- `C = (tk/close-panel O2)`: `(:open? C)` false; `(:seen C)` 4; `(tk/unread C)` 0
- `C2 = (tk/append C (hal "y") (+ A 9500))`: `(tk/unread C2)` 1; `(tk/badge-text C2)` `"1"`
- `(tk/unread T0)` → 0; `(tk/badge-text T0)` → `""`
- `(tk/unread (assoc T3 :open? true))` → 0

**t16-badge-cap**
- `(tk/badge-text (appends 99))` → `"99"`
- `(tk/unread (appends 120))` → 100; `(tk/badge-text (appends 120))` → `"99+"`

**t17-toggles**
- `(:voice? T0)` → false; `(:voice? (tk/toggle-voice T0))` → true; `(:voice? (tk/toggle-voice (tk/toggle-voice T0)))` → false
- `(tk/toggle-panel T3)` → `= (tk/open-panel T3)`
- `(tk/toggle-panel (tk/open-panel T3))` → `= (tk/close-panel (tk/open-panel T3))`

**t18-close-key**
- true: `("Escape" true "DIV")`, `("Escape" true nil)`
- false:
  - `("Escape" false "DIV")` (closed: Escape isn't swallowed)
  - `("Escape" true "INPUT")`, `("Escape" true "TEXTAREA")`, `("Escape" true "SELECT")`
  - `("f" true "DIV")`, `("Esc" true nil)`

**t19-clock-text**
- `(tk/clock-text A -480)` → `"5:00 PM"`; `(tk/clock-text (+ A (* 42 60000)) -480)` → `"5:42 PM"`
- `(tk/clock-text A -420)` → `"6:00 PM"`; `(tk/clock-text A 0)` → `"1:00 AM"`
- `(tk/clock-text D0 0)` → `"12:00 AM"`; `(tk/clock-text (+ D0 300000) 0)` → `"12:05 AM"`
- `(tk/clock-text (+ D0 43200000) 0)` → `"12:00 PM"`; `(tk/clock-text (+ D0 86340000) 0)` → `"11:59 PM"`
- `(tk/clock-text D0 -480)` → `"4:00 PM"` (wraps to the day before)
- `(tk/clock-text A nil)` → `"1:00 AM"`; `(tk/clock-text nil -480)` → `""`; `(tk/clock-text ##NaN 0)` → `""`

#### Voices

**t20-voices-from-estate** (W carries the estate's `:voices` and Juniper's `:voice`)
- `(tk/voice-for W {:kind :hal})` → `{:rate 1 :pitch 0.9 :lang "en-US"}`
- `(tk/voice-for W E2)` → `{:rate 1.1 :pitch 1.6 :lang "en-US"}` (Flora: the creature voice)
- `(tk/voice-for W E3)` → `{:rate 0.9 :pitch 0.8 :lang "en-US"}` (Juniper's own override)
- `(tk/voice-for W {:kind :other})`, `(tk/voice-for {} nil)` and `(tk/voice-for nil {:kind :hal})` → `{:rate 1 :pitch 1 :lang "en-US"}`
- `(not= (tk/voice-for W E1) (tk/voice-for W E2))` → true
- `(tk/voice-for (assoc-in W [:mirrors :pony] (assoc (get-in W [:mirrors :juniper]) :id :pony)) {:kind :creature :mirror :pony})` → `= (tk/voice-for W E3)`

**t21-voice-validation**
- `(tk/voice-for {:voices {:hal {:rate 50 :pitch -1 :lang ""}}} {:kind :hal})` → `{:rate 1 :pitch 1 :lang "en-US"}`
- `(tk/voice-for {:voices {:hal {:rate 0.5 :pitch "hi" :lang "fr-FR"}}} {:kind :hal})` → `{:rate 0.5 :pitch 1 :lang "fr-FR"}`
- `(tk/voice-for {:voices {:hal {:rate 0.1 :pitch 2}}} {:kind :hal})` → `{:rate 0.1 :pitch 2 :lang "en-US"}`
- `(tk/voice-for {:voices {:hal {:rate 0.09 :pitch 2.01}}} {:kind :hal})` → `{:rate 1 :pitch 1 :lang "en-US"}`
- `(tk/voice-for {:voices {:creature {:pitch 1.5}} :mirrors {:x {:voice {:pitch 9 :rate 2}}}} {:kind :creature :mirror :x})` → `{:rate 2 :pitch 1.5 :lang "en-US"}` (the bad mirror pitch falls back to the kind's)

#### History across reloads

**t22-save-restore-roundtrip**
- `(string? (tk/save-text T3))` → true
- `R = (tk/restore (tk/save-text T3))` → `= (assoc T0 :entries [(bare E1) (bare E2) (bare E3)] :n 3 :seen 0)`
- `(tk/unread R)` → 3; `(tk/caption R A)` → nil (restored lines don't caption again)
- `RO = (tk/observe R WH (+ A 100))`:
  - `(count (:entries RO))` → 6 (a new session logs its own greeting and bubbles)
  - `(:n (tk/caption RO (+ A 100)))` → 4
- `(tk/restore (tk/save-text (tk/open-panel T3)))` → `:seen` 3, `:open?` false, unread 0
- `(:voice? (tk/restore (tk/save-text (tk/toggle-voice T3))))` → false
- `(tk/restore (tk/save-text T0))` → `= T0`

**t23-restore-garbage** (each → `= tk/empty-talk`)
- `nil`, `7`, `""`, `"{"`, `"[1 2]"`
- `"{:v 2 :n 3}"`, `"{:v 1}"`, `"{:v 1 :entries \"x\"}"`
- `(pr-str {:n 3 :entries []})` (no `:v`)

**t24-restore-drops-bad-entries**
- Setup:

```clojure
(def P (pr-str {:v 1 :n 9 :seen 4
                :entries [{:n 2 :who "Hal" :text "hi" :at 5 :kind :hal}
                          {:n 3 :who "" :text "x" :at 5 :kind :hal}
                          {:n 4 :who "Flora" :text "yo" :at "noon" :kind :creature}
                          "junk"
                          {:n 5 :who "Flora" :text "yo" :at 7 :kind :creature :mirror "flora"}
                          {:n 1 :who "Hal" :text "first" :at 1 :kind :hal}
                          {:n 0 :who "Hal" :text "zero" :at 1 :kind :hal}
                          {:n 6 :who "Hal" :text "k" :at 1 :kind "hal"}
                          {:n 7 :who "Hal" :text "extra" :at 1 :kind :hal :show-at 1 :show-until 99}]}))
```

- `(:entries (tk/restore P))` → `[{:n 1 :who "Hal" :text "first" :at 1 :kind :hal} {:n 2 :who "Hal" :text "hi" :at 5 :kind :hal} {:n 5 :who "Flora" :text "yo" :at 7 :kind :creature} {:n 7 :who "Hal" :text "extra" :at 1 :kind :hal}]`
- `(:n (tk/restore P))` → 9; `(:seen (tk/restore P))` → 4; `(tk/unread (tk/restore P))` → 2
- `(:n (tk/append (tk/restore P) (hal "next") A))` → 10

**t25-restore-counters-and-cap**
- Setup: `one = [{:n 3 :who "Hal" :text "a" :at 1 :kind :hal}]`
- `(tk/restore (pr-str {:v 1 :n 1 :seen 0 :entries one}))`: `:n` 3, `:seen` 0
- `:seen 50` → 3; `:seen -2` → 0; `:seen "x"` → 3 (all with `:n 3` and `one`)
- `(:n (tk/restore (pr-str {:v 1 :n "x" :seen 0 :entries one})))` → 3
- 150 valid entries `{:n i :who "Hal" :text (str "e" i) :at i :kind :hal}` for i in 1..150 with `:seen 0`:
  - `(count (:entries R))` → 100
  - `(:n (first (:entries R)))` → 51
  - `(:n R)` → 150; `(tk/unread R)` → 100

#### Wired to the content round

**t26-end-to-end-with-hal-lines**
- `(:entries (tk/observe T0 (c/hal-say W H :jack-in {} A) A))`:
  - the first entry has `:who "Hal"`, `:text "hello"` (seed 20764 mod 3 = 1)
  - then Flora's and Juniper's entries follow
- `T = (tk/observe T0 W A)`
- `K1 = (c/hal-on-chore (w/chore-done W (w/key-event W "f" (+ A 1000))) H)` (seed 20764 + 1 → `"did feed for Flora"`, `:until (+ A 7000)`)
- `TK = (tk/observe T K1 (+ A 1000))`:
  - `(texts TK)` → `["I'm hungry" "Hay, please?" "did feed for Flora"]`
  - `(peek (:entries TK))` → `{:n 3 :who "Hal" :text "did feed for Flora" :at (+ A 1000) :kind :hal :show-at (+ A 5000) :show-until (+ A 7500)}`
  - `(:last-say TK)` → `{:flora "" :juniper "Hay, please?"}`
  - `(identical? TK (tk/observe TK K1 (+ A 1016)))` → true

**Totals:** 26 new tests in `talk_test`. The 125 existing tests are unchanged and must still pass: the estate's new `:voices` and `:voice` keys touch no asserted value. That makes 151 tests.

## 7. Live check (Hal)

1. **At start.** A caption appears bottom-centre: **Hal** — `You're jacked in. Let's get going.` (today's seed). Flora's and Juniper's current bubbles follow, one after another, about 2.5 s each. The status line shows `… · mail 9 · 1 flagged · scene: …` with no `Hal:` part.
2. **💭.**
   - It shows a count (3 at a fresh start).
   - A click opens the list, oldest first and scrolled to the bottom, with rows like `5:42 PM Hal You're jacked in…`; the count clears.
   - A second click, or Escape, closes it. Escape with the panel closed still reaches the kit.
   - After a reload, the old lines are still listed, followed by the new session's.
3. **🔊.**
   - It is hidden without `speechSynthesis`.
   - The first click shows `🔊 voice on` and says "Voice on."; `speechSynthesis.speaking` is true while it talks. The next caption is spoken as it appears, with Hal's voice lower than Flora's and Juniper lower still.
   - Clicking a row in 💭 says it again.
   - Clicking 🔊 again shows `🔊 voice off` and cuts speech mid-line (`speaking` goes false).
4. **F (when Flora is due).** A **Hal** — `Flora's feed: done. That one counts.` caption, spoken when voice is on. Flora's bubble going empty logs nothing.

## 8. Not in this round

Typing back to Hal, recorded voice files, lip sync, the creatures' 3D bubbles, saving the 🔊 setting, and removing the now-unused `content/hud-segments`.
