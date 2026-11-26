# Spec: win the day v0 (final project, F-1)

Quest: `.crew/quests/win-the-day-v0.md`. Start commit `c98177d`.
After the Construct, a **Today** card in the top-left lists three goals with read-only checkboxes:
finish the tutorial, build something with your twin, jack out and back in. When all three are
ticked, a centred **Day won** card shows once (title, one twin line, **Keep playing**), and the Today
card collapses to "Day won ✓". The logic is pure (`pm.today`). `pm.core` feeds it events from the
places that already know about them, and `pm.ui` draws it. No bridge is needed, so it works the same
in the demo build and the dev build.

Everything the quest records as decided stands. This spec fixes the names, the shapes, the
placement numbers and the order of checks, and works out the test values by hand.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/today.cljs` (new, ns `pm.today`); `src/pm/core.cljs` (only the wiring in §5); `src/pm/ui.cljs` (only `set-today!` and `show-day-won!`, §6, plus their private helpers); `resources/estate.edn` (only the new `:today` block, §2.3, copied exactly); `resources/content/hal-lines.edn` (only the new `:day-won` entry, §2.4, copied exactly); in the run dir: `builder-notes.md` (§8) | `test/`, `src/pm/content.cljs` (do **not** add `:day-won` to `c/hal-events`: `content_test` t20 pins that set), `src/pm/construct.cljs`, `src/pm/blueprint.cljs`, `src/pm/world.cljs`, `public/index.html`, `docs/`, `TASKS.md`, `state/`, `content/requests/`. No npm deps. Don't start services |
| **tester** (le-testeur) | `test/pm/today_test.cljs` (new, ns `pm.today-test`): only new tests, §7 | every existing test and assertion; everything else. The tests are pure: no localStorage, no DOM, no `js/Date`, no files |
| **le-docteur** | `TASKS.md`: the F-1 row (§9), after the gate passes | everything else |

The builder and the tester work in parallel, from this spec only. The tester pins the exact strings
in §2.3 and §2.4, so the builder copies them character for character.

---

## 2. Data shapes

### 2.1 The day's state (`pm.today`, persisted as `pr-str` in localStorage `pm.today.v1`)

```clojure
{:day       "2026-11-25"   ; local calendar day, "YYYY-MM-DD"
 :tutorial? true           ; goal 1: the Construct is done (kept across days)
 :built?    false          ; goal 2: a blueprint turned :real after the tutorial, this day
 :out?      true           ; half of goal 3: jacked out at the booth after the tutorial
 :back?     false          ; goal 3: jacked back in after that jack-out
 :won-at    nil}           ; epoch ms of the moment all three were ticked; nil until then
```
`nil` (nothing saved yet) is a valid input to every fn. It acts like a state from no day at all.

### 2.2 Constants (`pm.today`, public `def`s)

```clojure
(def goal-count 3)
(def margin 14)          ; px from the top and left edges (the 💭 panel's own left, index.html:82)
(def card-w 300)         ; px, the open card's width
(def pill-h 40)          ; px, the collapsed pill's height (the bottom-left buttons' height, index.html:34)
(def panel-top 72)       ; px, the 💭 panel's top (index.html:82); the pill must end above it
(def bottom-clear 250)   ; px kept free at the bottom for the bottom-left buttons (their top edge is at 210 px from the bottom) + a gap
(def min-card-h 120)     ; px, the smallest max-height the card gets
(def narrow-vw 720)      ; px; below this width the card is always a pill
(def fallback-win-line "Day won. What you built stays built.")   ;; TODO content
```

### 2.3 The estate's `:today` block (`resources/estate.edn`, new top-level key)

Insert it right after the `:construct-script` block (after its closing `:land-camera … }}` line),
with this comment:

```clojure
 ;; The day's three goals (pm.today; the final project's win loop): the Today card in the
 ;; top-left once the Construct is done, and the Day won card when all three are ticked.
 :today {:title     "Today"
         :rows      {:tutorial "Finish the tutorial"
                     :build    "Build something with your twin"
                     :jack     "Jack out and back in"}
         :hints     {:build "Ask your twin in 💭 (type or 🎙 talk) for a thing, try it, make it real."
                     :jack  "Click the phone booth to jack out, then jack back in."}
         :pill      "{title} {done}/{total}"   ; the collapsed card
         :won       "Day won ✓"               ; the collapsed card once the day is won
         :collapse  "Hide"                    ; the open card's collapse button
         :win-title "Day won"
         :button    "Keep playing"}
```

### 2.4 The twin's line (`resources/content/hal-lines.edn`, new entry)

Add it after the `:jack-in` vector, with the comment shown. Like `:close-up`, it is hand-added
until the next pipeline run:

```clojure
 ;; :day-won was added by hand for the win loop (win-the-day-v0); add it to
 ;; content/requests/hal-lines.edn before the next `bb content hal-lines` regenerates the table.
 :day-won
 ["That's the day won. What you built stays built."
  "Day won. Go live the rest of it; the estate keeps."
  "All three done. Same again tomorrow, if you like."
  "You built something, left, and came back. That's the whole game."],
```

### 2.5 What `rows` returns (for the UI)

```clojure
[{:id :tutorial :text "Finish the tutorial"            :hint nil :ticked? true}
 {:id :build    :text "Build something with your twin" :hint "Ask your twin in 💭 …" :ticked? false}
 {:id :jack     :text "Jack out and back in"           :hint "Click the phone booth …" :ticked? false}]
```
The order is always `:tutorial :build :jack`. `:hint` is `nil` once the row is ticked.

### 2.6 What `layout` returns

```clojure
{:mode :card :left 14 :top 14 :width 300 :max-height 550}   ; open
{:mode :pill :left 14 :top 14 :width nil :max-height nil}   ; collapsed
```

---

## 3. `src/pm/today.cljs`: public functions

`(ns pm.today (:require [cljs.reader :as reader] [pm.content :as c] [pm.blueprint :as bp]))`.
The ns docstring says it is pure: no atoms, no three.js, no DOM, no `js/Date`. `now` is epoch ms and
`offset-min` is minutes east of UTC (`pm.core/tz-offset-min`, the same convention `pm.closing` uses).
No person, pet or member is named anywhere in it.

The event fns `tutorial-done`, `build-real`, `jacked-out` and `jacked-in` all follow these rules:
- If `now` is not a finite number, they return `today` **unchanged** (it stays `nil` if it was `nil`).
- Otherwise they first roll the state forward with `(current today now offset-min)`, then apply the
  event, and then record the win: if `won?` holds and `:won-at` is nil, `(assoc :won-at now)`.
- A goal that is already ticked stays ticked. Nothing ever un-ticks a goal within the same day.

| # | Signature | Returns / rules |
|---|---|---|
| 1 | `(day-of now offset-min)` | `"YYYY-MM-DD"` for the local day of `now` shifted by `offset-min` minutes. Returns `nil` when `now` is not a finite number (nil, NaN, ±Infinity, a string). A non-number `offset-min` counts as 0. Pure integer maths: `days = floor((now + off*60000) / 86400000)`, then the civil-from-days algorithm (no `js/Date`), so it also works for negative `now`. Month and day are zero-padded to 2 digits. |
| 2 | `(fresh day tutorial?)` | `{:day day :tutorial? (boolean tutorial?) :built? false :out? false :back? false :won-at nil}` |
| 3 | `(current today now offset-min)` | The day rollover. Bad `now`: returns `today` unchanged. If `(:day today)` equals `(day-of now offset-min)`, it returns `today` **identical** (the same object). Otherwise it returns `(fresh (day-of now offset-min) (:tutorial? today))`, so `nil` gives `(fresh d false)`. |
| 4 | `(tutorial-done today now offset-min)` | Sets `:tutorial? true`. |
| 5 | `(build-real today now offset-min opts)` | `opts` is `{:practice? bool :at-load? bool}` (nil is fine). Sets `:built? true` only when `:tutorial?` is already true and neither `:practice?` nor `:at-load?` is truthy. |
| 6 | `(jacked-out today now offset-min opts)` | `opts` is `{:practice? bool}`. Sets `:out? true` only when `:tutorial?` is true and `:practice?` is not truthy. |
| 7 | `(jacked-in today now offset-min opts)` | `opts` is `{:practice? bool}`. Sets `:back? true` only when `:out?` is already true and `:practice?` is not truthy. |
| 8 | `(won? today)` | `(boolean (and (:tutorial? today) (:built? today) (:back? today)))` |
| 9 | `(just-won? before after)` | `(and (nil? (:won-at before)) (number? (:won-at after)))`, as a boolean. |
| 10 | `(done-count today)` | The number of truthy values among `:tutorial? :built? :back?`, from 0 to 3. `:out?` alone counts for nothing. |
| 11 | `(rows today texts)` | §2.5. `texts` is the estate's `:today` block. `:text` is `(get-in texts [:rows id] "")` and `:hint` is `(when-not ticked? (get-in texts [:hints id]))`. `:ticked?` is `:tutorial?`, `:built?` and `:back?` respectively, as booleans. A nil `texts` gives `""` texts and nil hints. |
| 12 | `(pill today texts)` | When `won?`: `(or (:won texts) <the pill text below>)`. Otherwise: `(c/fill template {:title (or (:title texts) "") :done (done-count today) :total goal-count})`, where `template` is `(:pill texts)` if it's a string, else `"{title} {done}/{total}"`. |
| 13 | `(card-open? today choice)` | `choice` is the player's last toggle (`true`, `false` or `nil`). If it's a boolean, returns it. If it's nil, returns `(not (won? today))`. |
| 14 | `(layout {:keys [vw vh panel-open? open?]})` | `:card` mode only when `open?`, `(not panel-open?)`, `vw` and `vh` are both numbers, and `vw >= narrow-vw`. Then `{:mode :card :left margin :top margin :width (min card-w (- vw 28)) :max-height (max min-card-h (- vh bottom-clear))}`. Otherwise `{:mode :pill :left margin :top margin :width nil :max-height nil}`. |
| 15 | `(win-line table seed)` | `(or (c/line table [:day-won] {} seed) fallback-win-line)` |
| 16 | `(win-card texts line)` | `{:title (:win-title texts) :line line :button (:button texts)}` |
| 17 | `(save-text today)` | `(pr-str today)` |
| 18 | `(restore text)` | Reads `text` with `cljs.reader/read-string` inside a try. Returns the map when the result is a map whose `:day` is a string. Returns `nil` for nil, `""`, garbage, a non-map, or a map without a string `:day`. Never throws. |
| 19 | `(real-ids world)` | The set of `:id`s of `(bp/all world)` whose `:status` is `:real` or `:removing`. A removal preview still counts as built, so "Keep it" (`:removing` → `:real`) never looks like a new build. |
| 20 | `(transitions before after)` | A vector of the events between two worlds, in this order: `:build-real` if `(real-ids after)` holds any id that `(real-ids before)` doesn't; then `:jacked-out` if `(:out? (:jack after))` is truthy and `(:out? (:jack before))` isn't; `:jacked-in` for the reverse. With no change it returns `[]`. `nil` worlds are fine. |

The tutorial's rock is the Construct's own DOM drawing (`pm.ui` `.pm-rock`), not a blueprint, so it
can never appear in `real-ids`. The practice jump uses `construct/jump-out`/`jump-in` (`:construct`
keys), not `:jack`, so it never shows up in `transitions`. The wiring still passes `:practice?`
(§5.4) for a replay through `?construct`, and the tests pin that flag.

---

## 4. Persistence

- Key: `pm.today.v1`. Value: `(today/save-text state)`, written every time `!today` changes (§5.2).
- Read once in `init!` with `today/restore`. Anything it can't read counts as `nil` (a fresh day).
- `?fresh` removes `pm.today.v1` in `fresh-start!` and resets `!today` to nil.
- `?construct` does not touch it. A replaying player keeps today's ticks, and nothing ticks while the
  Construct runs (`:practice? true`).

---

## 5. `pm.core` wiring (name every hook)

Add `[pm.today :as today]` to the ns requires. `pm.jumeaux :as j` and `pm.content :as content` are
already required.

### 5.1 New state (next to `construct-key`, core.cljs:423)
```clojure
(def today-key "pm.today.v1")
(defonce ^:private !today (atom nil))              ; the day's goals (pm.today state)
(defonce ^:private !today-open (atom nil))         ; the player's last collapse toggle: true/false/nil
(defonce ^:private !real-from-file? (atom false))  ; true only inside the swaps that apply state/builds.edn
```

### 5.2 New private fns
```clojure
(defn- save-today! []
  (try (.setItem js/localStorage today-key (today/save-text @!today)) (catch :default _ nil)))

(declare on-day-won!)
(defn- today-event!
  "Feeds one event fn of pm.today (f today now offset-min & args); saves, and shows the win once."
  [f & args]
  (let [before @!today
        after  (apply f before (js/Date.now) (tz-offset-min) args)]
    (when (not= before after)
      (reset! !today after)
      (save-today!)
      (when (today/just-won? before after) (on-day-won! (:won-at after))))))

(defn- on-today-world!
  "Watch on !world: a blueprint turning :real, and jack-out / jack-in at the booth."
  [_ _ old new]
  (when-not (identical? old new)
    (let [practice? (not @!construct-seen?)]
      (doseq [e (today/transitions old new)]
        (case e
          :build-real (today-event! today/build-real {:practice? practice? :at-load? @!real-from-file?})
          :jacked-out (today-event! today/jacked-out {:practice? practice?})
          :jacked-in  (today-event! today/jacked-in  {:practice? practice?}))))))

(defn- close-day-won! [] (ui/show-day-won! nil nil))

(defn- on-day-won! [now]
  (let [line (today/win-line content/hal-lines (content/day-number now))]
    (reset! !today-open nil)   ; back to the default: collapsed once won
    (ui/show-day-won! (assoc (today/win-card (:today @!world) line) :who (j/twin (j/names @!world)))
                      close-day-won!)
    (say! :day-won line)))     ; caption + 💭, like the twin's other lines (talk/observe picks up :hal)

(defn- today-tick!
  "Every frame: the day rollover, then the Today card (hidden until the Construct is seen)."
  [now]
  (let [before @!today
        after  (today/current before now (tz-offset-min))]
    (when-not (identical? before after) (reset! !today after) (save-today!)))
  (ui/set-today!
    (when @!construct-seen?
      (let [s @!today texts (:today @!world)]
        {:layout   (today/layout {:vw (.-innerWidth js/window) :vh (.-innerHeight js/window)
                                  :panel-open? (boolean (:open? (:talk @!world)))
                                  :open? (today/card-open? s @!today-open)})
         :title    (:title texts)
         :collapse (:collapse texts)
         :rows     (today/rows s texts)
         :pill     (today/pill s texts)}))
    (fn [open?] (reset! !today-open open?))))
```
`say!` is defined at core.cljs:1253, after these fns, so add it to the existing `declare` at
core.cljs:613 (or put these fns after `say!`). Either way, keep 0 warnings.

### 5.3 Hooks into existing fns (exact places)

| Where | What to add |
|---|---|
| `construct-tick!` (core.cljs:526-528), inside the `when` that sets `!construct-seen?` true | after the `.setItem … construct-key "1"`: `(today-event! today/tutorial-done)`. This is goal 1, ticked the moment the last beat (`free-your-mind`) ends. |
| `fresh-start!` (core.cljs:429) | inside the `try`: `(.removeItem js/localStorage today-key)`; after `(reset! !construct-seen? false)`: `(reset! !today nil)` |
| `init!` (core.cljs:1733), right after `(fresh-start!)` | `(reset! !today (today/restore (try (.getItem js/localStorage today-key) (catch :default _ nil))))` |
| `init!`, right after `(add-watch !world ::play-cache on-play-change!)` (that is, after `restore-play!`/`restore-cache!` have run, so restoring saved play can't tick anything) | `(add-watch !world ::today on-today-world!)` then `(when @!construct-seen? (today-event! today/tutorial-done))`. A returning player who has seen the Construct gets goal 1 ticked. |
| `frame` (core.cljs:1023), right after `(construct-tick! (js/Date.now))` | `(today-tick! (js/Date.now))` |
| `fetch-builds!` (core.cljs:132) | wrap the `(swap! !world bp/apply-real-builds b)` so that `!real-from-file?` is true only during it: `(reset! !real-from-file? true) (try (swap! …) (finally (reset! !real-from-file? false)))` |
| `apply-blueprint-file!` (core.cljs:1652) | the same wrap around `(reset! !world w3)` (it applies `@!last-builds`) |

Any other place that calls `bp/apply-real-builds` on `!world` gets the same wrap (grep
`apply-real-builds`). **Do not** hook `make-real!` or `toggle-jack-out!` directly. The watch catches
every path that turns a status `:real` (today's trial card, the demo's path next round) and every
change to `:jack` (the booth click, the jack-out card's button, a bridge `:jack-out` event). The
watch calls `say!`, which swaps `!world` from inside the watch. That's fine: the nested swap
changes neither `:jack` nor any status, so its `transitions` is `[]`.

### 5.4 The flags, stated once
- `:practice?` = `(not @!construct-seen?)` when the event happens: the Construct is running, first
  time or a `?construct` replay.
- `:at-load?` = `@!real-from-file?`: the status came from `state/builds.edn` (builds already real when
  the page loaded, or made real in another tab), not from a click in this page.

---

## 6. `pm.ui`: the two cards

Both functions are called every frame or on an event, so like `show-jack-out!` (ui.cljs:606) they
keep a `(pr-str state)` key in `dataset.key` and rebuild only when it changes. Use the colours
already in use (the 💭 panel's `rgba(20,24,32,.88)` / `#f3efe2`, the jack-out card's white card);
add no new palette.

### 6.1 `(set-today! state on-toggle)`
- One element `#pm-today`, created on first use and appended to `body`. `state` nil means
  `display:none` (the Construct is running, the white room included).
- `position:fixed; left:{left}px; top:{top}px; z-index:205` (above the bottom-left buttons at 200,
  below the 💭 panel and HUD at 220, the jack-out veil at 450 and the win card at 460).
  `background:rgba(20,24,32,.88); color:#f3efe2; border-radius:10px; font:15px/1.4 system-ui,sans-serif`.
- `:mode :card`: `width:{width}px; max-height:{max-height}px; overflow-y:auto; padding:10px 12px;
  box-sizing:border-box`. A head row holds the title (`:title`, 600 weight, small caps style like
  the crew drawer's label) and, at its right, a `<button>` whose text is `:collapse` ("Hide"); it
  calls `(on-toggle false)`. Then one row per entry of `:rows`: a `<label>` holding
  `<input type="checkbox" disabled>`, `checked` = `:ticked?`, then the `:text`. When `:hint` is
  non-nil, a smaller second line follows (`font-size:12px; opacity:.7`). The inputs stay `disabled`,
  so clicking them changes nothing.
- `:mode :pill`: the element is one `<button>`, `height:40px` (`today/pill-h`), `padding:0 14px`,
  whose text is `:pill` ("Today 2/3", or "Day won ✓"); it calls `(on-toggle true)`. While the 💭
  panel is open the layout is always a pill, so the click only takes effect once 💭 closes. That is
  intended.
- Placement checks (worked out from index.html): the pill spans y 14 to 54, and the 💭 panel starts
  at y 72, so they are 18 px apart. The open card exists only while 💭 is closed. Its bottom edge is
  at most `vh − 236`, and the bottom-left buttons' top edge sits at `vh − 210`, so they are at least
  26 px apart. The card's right edge is at x 314. The game's `#caption` is bottom-centred
  (index.html:73, `bottom:140px`), so the card can't overlap it. Under 720 px wide the card is a pill.

### 6.2 `(show-day-won! state on-close)`
- `card-el "day-won-veil" 460 "rgba(12,14,22,.35)"` (ui.cljs:593), a centred card. `state` nil
  means `display:none`.
- `state` is `{:title :line :button :who}`. It uses the same card box as the jack-out card
  (`background:#fff;border-radius:16px;padding:26px 34px;max-width:620px;text-align:center;box-shadow:0 10px 40px rgba(0,0,0,.3)`),
  calls `construct-css!`, and contains:
  1. a title div holding `:title` ("Day won"), `font:600 28px system-ui; margin-bottom:12px`
  2. `(append-say! card (:line state) (:who state))`, the twin's line under its name
  3. `(append-hint! card (:button state))`, the labelled **Keep playing** button
- The click handler is the same as the jack-out card's: `stopPropagation`, and `(on-close)` only
  when `button-click?`. Backdrop clicks do nothing.

---

## 7. Test cases (`test/pm/today_test.cljs`, ns `pm.today-test`)

Requires: `[cljs.test :refer [deftest is]] [clojure.string] [pm.today :as t] [pm.world :as w] [pm.blueprint :as bp] [pm.content :as c]`.

Fixtures:
```clojure
(def E  (w/load-estate w/estate-edn))
(def TX (:today E))
(def TX2 {:rows {:tutorial "A" :build "B" :jack "C"} :hints {:tutorial "ha" :build "hb" :jack "hc"}
          :title "Jour" :pill "{title} · {done} sur {total}" :won "Gagné" :win-title "Bravo" :button "OK"})
(def A  1795597200000)  ; 2026-11-25T09:00Z
(def H  3600000)        ; one hour
(def B  1795683600000)  ; 2026-11-26T09:00Z
(def D1 "2026-11-25") (def D2 "2026-11-26")
(def S1 (t/tutorial-done nil A 0))   ; the tutorial done, nothing else
(def WON (-> S1 (t/build-real (+ A H) 0 {}) (t/jacked-out (+ A (* 2 H)) 0 {}) (t/jacked-in (+ A (* 3 H)) 0 {})))
(def BP1 :bench-by-mailbox) (def BP2 :lamp-post-by-canal)   ; the estate's two blueprints
(defn real [world id] (bp/make-real (bp/approve-blueprint world id) id))
```

| # | Test | Expected |
|---|---|---|
| t01 | `day-of` | `(t/day-of A 0)` = `"2026-11-25"`; `(t/day-of 1795662000000 -300)` = `"2026-11-25"` (03:00Z is 22:00 the day before at UTC−5); `(t/day-of 1795662000000 0)` = `"2026-11-26"`; `(t/day-of 1798759800000 60)` = `"2027-01-01"` (23:30Z on Dec 31 at UTC+1); `(t/day-of 1835438400000 0)` = `"2028-02-29"`; `(t/day-of 1795649400000 540)` = `"2026-11-26"`; `(t/day-of 0 0)` = `"1970-01-01"`; `(t/day-of -1 0)` = `"1969-12-31"`; `(t/day-of A nil)` = `"2026-11-25"` |
| t02 | `day-of` with a bad `now` | `nil` for `nil`, `##NaN`, `##Inf`, `"x"` (any offset) |
| t03 | `fresh` | `(t/fresh D1 true)` = `{:day "2026-11-25" :tutorial? true :built? false :out? false :back? false :won-at nil}`; `(:tutorial? (t/fresh D1 nil))` = `false` |
| t04 | `current`: same day is identical | `(identical? S1 (t/current S1 (+ A H) 0))` is true; `(t/current nil A 0)` = `(t/fresh D1 false)` |
| t05 | `current`: rollover keeps the tutorial | `(t/current WON B 0)` = `(t/fresh D2 true)`; `(t/current (t/fresh D1 false) B 0)` = `(t/fresh D2 false)` |
| t06 | `current`: the offset decides the day | `(identical? S1 (t/current S1 1795662000000 -300))` is true; `(t/current S1 1795662000000 0)` = `(t/fresh D2 true)` |
| t07 | `current` and the events with a bad `now` | `(t/current S1 nil 0)` = `S1`; `(t/current nil nil 0)` = `nil`; `(t/tutorial-done nil nil 0)` = `nil`; `(t/build-real S1 ##NaN 0 {})` = `S1`; `(t/jacked-out S1 nil 0 {})` = `S1`; `(t/jacked-in S1 nil 0 {})` = `S1` |
| t08 | `tutorial-done` ticks goal 1 | `S1` = `{:day D1 :tutorial? true :built? false :out? false :back? false :won-at nil}`; `(t/tutorial-done S1 (+ A H) 0)` = `S1` |
| t09 | `build-real` ticks goal 2 after the tutorial | `(:built? (t/build-real S1 (+ A H) 0 {}))` = `true`; with `nil` opts, also `true`; `:won-at` still `nil` |
| t10 | `build-real` before the tutorial doesn't count | `(t/build-real nil A 0 {})` = `(t/fresh D1 false)`; `(:built? (t/build-real (t/fresh D1 false) A 0 {}))` = `false` |
| t11 | the tutorial's rock and builds already real at load don't count (the flags) | `(t/build-real S1 A 0 {:practice? true})` = `S1`; `(t/build-real S1 A 0 {:at-load? true})` = `S1`; `(:built? (t/build-real S1 A 0 {:practice? false :at-load? false}))` = `true` |
| t12 | jack out, then in, ticks goal 3 | `(def O (t/jacked-out S1 (+ A H) 0 {}))`: `(:out? O)` = `true`, `(:back? O)` = `false`; `(:back? (t/jacked-in O (+ A (* 2 H)) 0 {}))` = `true` |
| t13 | order matters: in before out does nothing | `(t/jacked-in S1 A 0 {})` = `S1`; then `(t/jacked-out … )` gives `:out? true :back? false` (the earlier jack-in isn't remembered) |
| t14 | the practice jump doesn't count | `(t/jacked-out S1 A 0 {:practice? true})` = `S1`; with `O` from t12, `(t/jacked-in O A 0 {:practice? true})` = `O` |
| t15 | jack before the tutorial doesn't count | `(def P (t/jacked-out (t/fresh D1 false) A 0 {}))`: `(:out? P)` = `false`; `(:back? (-> P (t/tutorial-done (+ A H) 0) (t/jacked-in (+ A (* 2 H)) 0 {})))` = `false` |
| t16 | `won?` only with all three | false for `nil`, `{}`, `S1`, `(t/build-real S1 A 0 {})`, `O`, `(t/jacked-in O A 0 {})`, `{:built? true :back? true}`, `{:tutorial? true :built? true :out? true}`; true for `WON` and for `{:tutorial? true :built? true :back? true}` |
| t17 | the win is recorded once, at the winning event | `(:won-at WON)` = `1795608000000` (A+3h, the jack-in); `(:won-at (-> WON (t/jacked-out (+ A (* 4 H)) 0 {}) (t/jacked-in (+ A (* 5 H)) 0 {}) (t/build-real (+ A (* 5 H)) 0 {})))` = `1795608000000` |
| t18 | winning by the build last | `(:won-at (-> S1 (t/jacked-out (+ A H) 0 {}) (t/jacked-in (+ A (* 2 H)) 0 {}) (t/build-real (+ A (* 3 H)) 0 {})))` = `1795608000000`; the state just before has `:won-at nil` |
| t19 | `just-won?` | `(t/just-won? O' WON)` is true, where `O'` = `(-> S1 (t/build-real (+ A H) 0 {}) (t/jacked-out (+ A (* 2 H)) 0 {}))`; `(t/just-won? WON WON)` is false; `(t/just-won? nil nil)` is false; `(t/just-won? WON (t/current WON B 0))` is false |
| t20 | day rollover with an event | `(t/build-real WON B 0 {})` = `{:day D2 :tutorial? true :built? true :out? false :back? false :won-at nil}`; `(t/won? (t/current WON B 0))` = `false` |
| t21 | `done-count` | `nil` → 0; `S1` → 1; `(t/build-real S1 A 0 {})` → 2; `O` → 1 (out alone counts nothing); `WON` → 3 |
| t22 | `rows` from the estate's texts | `(t/rows S1 TX)` = `[{:id :tutorial :text "Finish the tutorial" :hint nil :ticked? true} {:id :build :text "Build something with your twin" :hint "Ask your twin in 💭 (type or 🎙 talk) for a thing, try it, make it real." :ticked? false} {:id :jack :text "Jack out and back in" :hint "Click the phone booth to jack out, then jack back in." :ticked? false}]`; `(mapv :ticked? (t/rows WON TX))` = `[true true true]` and `(mapv :hint (t/rows WON TX))` = `[nil nil nil]` |
| t23 | `rows` from a second texts map (nothing tied to one estate) | `(t/rows (t/fresh D1 false) TX2)` = `[{:id :tutorial :text "A" :hint "ha" :ticked? false} {:id :build :text "B" :hint "hb" :ticked? false} {:id :jack :text "C" :hint "hc" :ticked? false}]`; `(nth (t/rows O TX2) 2)` = `{:id :jack :text "C" :hint "hc" :ticked? false}`; `(mapv :text (t/rows S1 nil))` = `["" "" ""]`, `(mapv :hint (t/rows S1 {}))` = `[nil nil nil]` |
| t24 | `pill` | `(t/pill S1 TX)` = `"Today 1/3"`; `(t/pill (t/build-real S1 A 0 {}) TX)` = `"Today 2/3"`; `(t/pill WON TX)` = `"Day won ✓"`; `(t/pill S1 TX2)` = `"Jour · 1 sur 3"`; `(t/pill WON TX2)` = `"Gagné"`; `(t/pill S1 {})` = `" 1/3"`; `(t/pill WON {})` = `" 3/3"`; `(t/pill nil TX)` = `"Today 0/3"` |
| t25 | `card-open?` | `(t/card-open? S1 nil)` true; `(t/card-open? WON nil)` false; `(t/card-open? S1 false)` false; `(t/card-open? WON true)` true; `(t/card-open? nil nil)` true |
| t26 | `layout`: open card | `(t/layout {:vw 1280 :vh 800 :panel-open? false :open? true})` = `{:mode :card :left 14 :top 14 :width 300 :max-height 550}`; `(t/layout {:vw 720 :vh 300 :panel-open? false :open? true})` = `{:mode :card :left 14 :top 14 :width 300 :max-height 120}` |
| t27 | `layout`: pill | `{:mode :pill :left 14 :top 14 :width nil :max-height nil}` for each of: `{:vw 1280 :vh 800 :panel-open? true :open? true}`, `{:vw 1280 :vh 800 :panel-open? false :open? false}`, `{:vw 719 :vh 800 :panel-open? false :open? true}`, `{:vw nil :vh 800 :open? true}`, `{}`; and `(< (+ t/margin t/pill-h) t/panel-top)` is true (54 < 72) |
| t28 | `win-line` | `(t/win-line {:day-won ["x" "y"]} 0)` = `"x"`, seed 1 → `"y"`, seed 3 → `"y"`; `(t/win-line {} 0)` = `t/fallback-win-line` = `"Day won. What you built stays built."`; `(t/win-line nil 5)` = the fallback; `(t/win-line c/hal-lines 0)` = `"That's the day won. What you built stays built."`; `(count (:day-won c/hal-lines))` = 4, and no variant contains `"{"` |
| t29 | `win-card` from data | `(t/win-card TX "L")` = `{:title "Day won" :line "L" :button "Keep playing"}`; `(t/win-card TX2 "L")` = `{:title "Bravo" :line "L" :button "OK"}` |
| t30 | `save-text` / `restore` | `(t/restore (t/save-text WON))` = `WON`; `(t/restore (t/save-text S1))` = `S1`; `nil` for `nil`, `""`, `"{:day"`, `"[1 2]"`, `"{:day 5}"`, `"{:built? true}"`; `(t/restore "{:day \"2026-11-25\" :built? true}")` = `{:day "2026-11-25" :built? true}` |
| t31 | `real-ids` on both estate blueprints | `(t/real-ids E)` = `#{}`; `(t/real-ids (real E BP1))` = `#{:bench-by-mailbox}`; `(t/real-ids (real E BP2))` = `#{:lamp-post-by-canal}`; `(t/real-ids (-> E (real BP1) (real BP2)))` = `#{:bench-by-mailbox :lamp-post-by-canal}`; `(t/real-ids (bp/approve-blueprint E BP1))` = `#{}`; `(t/real-ids (bp/start-removal (real E BP1) BP1))` = `#{:bench-by-mailbox}` (removing still counts); `(t/real-ids nil)` = `#{}` |
| t32 | `transitions`: builds, for each blueprint | `(t/transitions E (real E BP1))` = `[:build-real]`; `(t/transitions E (real E BP2))` = `[:build-real]`; `(t/transitions (real E BP1) (-> E (real BP1) (real BP2)))` = `[:build-real]`; `(t/transitions (real E BP1) (real E BP1))` = `[]`; `(t/transitions (bp/start-removal (real E BP1) BP1) (real E BP1))` = `[]` (Keep it); `(t/transitions (real E BP1) E)` = `[]` |
| t33 | `transitions`: the jack | `(t/transitions (assoc E :jack {:out? false}) (assoc E :jack {:out? true :at A}))` = `[:jacked-out]`; the reverse = `[:jacked-in]`; `(t/transitions E (assoc E :jack {:out? true}))` = `[:jacked-out]`; `(t/transitions nil {:jack {:out? true}})` = `[:jacked-out]`; `(t/transitions E (assoc-in E [:construct :jumped-out-at] A))` = `[]` (the practice jump isn't `:jack`); `(t/transitions nil nil)` = `[]` |
| t34 | `transitions`: both in one step, in order | `(t/transitions E (assoc (real E BP2) :jack {:out? true}))` = `[:build-real :jacked-out]` |
| t35 | a whole day, the way the core watch drives it | Start `s` = `nil`. Each step is `(reduce (fn [s e] (case e :build-real (t/build-real s now 0 opts) :jacked-out (t/jacked-out s now 0 opts) :jacked-in (t/jacked-in s now 0 opts))) s (t/transitions w0 w1))`. (1) Construct running, `opts {:practice? true}`: jack out/in worlds → `s` stays `nil`. (2) `(t/tutorial-done s A 0)`. (3) builds.edn applies BP1 at load, `{:at-load? true}` → `:built? false`. (4) The player makes BP2 real, `{}`, at `A+H` → `:built? true`. (5) Jack out at `A+2H`, jack in at `A+3H` → `(t/won? s)` true, `(:won-at s)` = `1795608000000`, `(t/done-count s)` = 3, `(t/pill s TX)` = `"Day won ✓"` |
| t36 | the estate block is complete data and names no one | `TX` has string values at `:title :pill :won :collapse :win-title :button`, `[:rows :tutorial]`, `[:rows :build]`, `[:rows :jack]`, `[:hints :build]`, `[:hints :jack]`; `(some #(clojure.string/includes? (pr-str TX) %) ["Michael" "Hal" "Flora" "Juniper"])` is nil |

That is 36 test cases. The tester may split one row into several `deftest`s, but must keep the
`tNN` prefix numbering.

---

## 8. `builder-notes.md` (the builder writes it in the run dir)

What Hal clicks, in the dev build (`npx shadow-cljs watch app`, http://localhost:8080):
1. `/?fresh` → jack in → play the Construct to the end. The Today card appears top-left with
   **Finish the tutorial** ticked, "Today 1/3" when collapsed.
2. Build: ask the twin in 💭 for something, or click a pending ghost (the bench) → Make it real
   (needs `bb crew-bridge` in the dev build) → goal 2 ticks.
3. Click the phone booth → jacked-out card → **Jack back in** → goal 3 ticks → the **Day won** card,
   the twin's line in the caption and 💭 → **Keep playing** → the card reads "Day won ✓".
4. Reload: no second win card; the pill still reads "Day won ✓".
5. Faking a real build with no bridge (devtools console):
   `cljs.core.swap_BANG_(pm.core._BANG_world, (w) => pm.blueprint.make_real(pm.blueprint.approve_blueprint(w, cljs.core.keyword("bench-by-mailbox")), cljs.core.keyword("bench-by-mailbox")))`.
   The watch sees `:real`, so it ticks the same way.
6. The measured rects (`getBoundingClientRect`) of `#pm-today`, the 💭 panel and the kit's top-right
   pills, at 1280×800 and 1440×900, showing nothing overlaps.

---

## 9. TASKS.md row (le-docteur, after the gate passes)

Add a section `## Final project (due Dec 6)` right before `## Business (Pepper)`, with this row:

```
- [x] Nov 25 (crew round win-the-day-v0) F-1 Win the day (Michael, Nov 25: the final video runs "start to win"). After the Construct a Today card top-left lists three goals with read-only checkboxes: finish the tutorial, build something with your twin (a blueprint turning real after the tutorial; builds real at load and the tutorial's rock don't count), jack out and back in at the booth (in that order, not the practice jump). All three: a Day won card (the twin's :day-won line, Keep playing), once per day per browser; the card then reads "Day won ✓". Pure pm.today (localStorage pm.today.v1, ?fresh clears it), texts in estate.edn :today. Open: add :day-won to content/requests/hal-lines.edn; the demo's own build path (next round).
```

---

## 10. Out of scope

How the demo builds things from a chat request (next quest). Losing, streaks, scores and weekly
cards. Changes to the tutorial. Moving any existing panel.
