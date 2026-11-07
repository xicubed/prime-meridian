# Spec: click the animal to do its chore; chores in 💭 are checkboxes

Quest: `.crew/quests/click-chores.md`. Round dir: `crew/runs/click-chores-20261106-2034/`.

This builds on the uncommitted content and talk rounds now in the tree (155 tests: content 28, overlay 32, plan 34, talk 28, world 33).

- **Click.** Clicking a creature (any of its meshes, e.g. Flora's jar) does its first due chore through the existing `chore-done`, with `:via :click`. If nothing is due, Hal says when it was last done and when it's next due.
- **Checkbox.** Each counted chore becomes a checkbox entry in the talk log, ticked. Unticking undoes it from a record the log entry now carries. Ticking again redoes it if it's due.
- **No chore keys.** F and G are removed; Escape stays.

All rules are pure and tested. No code names a mirror.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/world.cljs`, `src/pm/chores.cljs` (new), `src/pm/pick.cljs` (new), `src/pm/talk.cljs`, `src/pm/content.cljs` (only `hal-events`), `src/pm/three.cljs` (one helper), `src/pm/scene.cljs` (two getters), `src/pm/ui.cljs`, `src/pm/core.cljs`, `public/index.html` (`<style>` only), `resources/estate.edn` (remove `:hud :keys`), `README.md` (one sentence) | `resources/content/*.edn`, `src/pm/overlay.cljs`, `src/pm/plan.cljs`, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/chores_test.cljs` (new), plus exactly the edits E1–E5 in §7.1 | every `src/`, `resources/`, `public/` file |

No npm deps, 0 warnings, storage failures stay silent. Nobody reads `assets/private/`, `public/kit/` or `public/state/`. Hal-kept mirrors (`:source :hal`) keep today's rule: a click or undo changes only the game's copy.

## 1. Data

### 1.1 A counted log entry now records what undo needs (`pm.world/chore-done`)

```clojure
{:event :chore-done :mirror :juniper :chore :hay :at 1794024960000 :via :click :who :me
 :result :counted
 :id "juniper/hay@1794024960000"   ; (event-id event)
 :prev-at nil                      ; the chore's done-time before this one (nil = never)
 :consumed [:bales 1]}             ; [field amount actually taken]; absent when nothing was consumed
```

- `:consumed` is `[field (- old new)]`, so it is `[:bales 0]` when the stock was already 0.
- It's absent when the chore has no `:consumes` or the field isn't a number.
- Ignored entries are unchanged.

### 1.2 The undo log entry (`pm.world/undo-chore`)

```clojure
{:event :chore-undone :mirror :juniper :chore :hay :at 1794025080000
 :id "juniper/hay@1794024960000" :result :undone}
;; or, when it can't apply:
{... :result :ignored :reason :unknown-mirror | :unknown-chore | :not-current}
```

### 1.3 A chore record (what a talk entry keeps)

`(select-keys counted-entry [:id :mirror :chore :at :prev-at :consumed])`:

```clojure
{:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at 1794024960000
 :prev-at nil :consumed [:bales 1]}
```

A record is **valid** when all of these hold:
- `:id` is a non-blank string;
- `:mirror` and `:chore` are keywords;
- `:at` is a finite number;
- `:prev-at` is absent, nil or a finite number;
- `:consumed` is absent, nil or `[keyword finite-number]`.

### 1.4 A chore talk entry (`:kind :chore`)

```clojure
{:n 1 :who "Juniper" :text "Juniper's hay" :at 1794024960000 :kind :chore :mirror :juniper
 :chore {...record...} :done? true}
```

- It has no `:show-at`/`:show-until`: a checkbox is never captioned or spoken.
- It is saved and restored with `:chore` and `:done?`, so undo works after a reload. The game's state survives through the existing play/state caches.

### 1.5 Estate and content

- `resources/estate.edn`: `:hud` becomes `{:mirrors [:flora]}`. Delete the `:keys` entry and its comment.
- `pm.content/hal-events` becomes:

```clojure
{:jack-in #{} :chore-done #{:name :chore}
 :chore-not-due #{:name :chore :last-at :next-at} :chore-undone #{:name :chore}}
```

Both new events are already in `hal-lines.edn`. A table without them makes Hal stay silent (`hal-say` returns the world unchanged).

## 2. `pm.world` (builder)

- **Remove** `key-event`.
- `(event-id ev)` → `(str (name mirror) "/" (name chore) "@" at)` when `:mirror` and `:chore` are keywords and `:at` is a number; otherwise nil.
- `(last-done-at world mirror-id chore-id)` → the private `last-done` value for that chore (nil when unknown or never done).
- `(next-due world mirror-id chore-id)` → `(+ last interval)` when `last` is a number and `(parse-every (:every chore))` gives an interval; otherwise nil.
  - It matches `due?` exactly: `due?` is true iff `now >= next-due` (or never done).
  - `due?` ignores `:at`, so `next-due` does too.
- `chore-done`, in the counted branch, also assocs onto the log entry:
  - `:id (event-id event)`;
  - `:prev-at` (the last-done value before recording);
  - `:consumed` (per §1.1).
  The world change itself is unchanged.
- `(undo-chore world record now)`:
  1. Unknown mirror → log `:ignored :unknown-mirror`. Unknown chore → `:unknown-chore`.
  2. `(last-done-at …)` ≠ `(:at record)` → log `:ignored :not-current`. (Something changed it since: Hal's file, or a later redo.)
  3. Otherwise:
     - restore the done-time: with `:done-at`, `(assoc-in [:mirrors m :state done-at] prev-at)`. Without it: `(update-in [:mirrors m :done] dissoc chore)` when `prev-at` is nil, else `assoc-in` it.
     - when `:consumed [f d]` and `d` is a number and the state's `f` is a number, add `d` back to `f`;
     - log `:undone` (§1.2).
  The log entry's `:event`, `:mirror`, `:chore`, `:at` (= now) and `:id` come from the record.
- `(redo-chore world record now)` → `(chore-done world {:event :chore-done :mirror (:mirror record) :chore (:chore record) :at now :via :tick :who :me})`.

## 3. `pm.chores` (new, pure; requires `pm.world`, `pm.content`, `pm.talk`)

`name-of` (private) is `(or (:name mirror) (name id))`.

| Signature | Behaviour |
|---|---|
| `(chore-at-click world mirror-id now)` | See below |
| `(click world table mirror-id now offset-min)` | See below |
| `(untick world table n now)` | See below |
| `(tick world table n now offset-min)` | See below |

**`chore-at-click`**
- nil when the mirror is unknown or has no `:chores`.
- The **first** chore in `:chores` order for which `w/due?` holds at `now` → `{:due? true :mirror m :chore c :event {:event :chore-done :mirror m :chore c :at now :via :click :who :me}}`.
- Otherwise use the first chore → `{:due? false :mirror m :chore c :last-at (w/last-done-at …) :next-at (w/next-due …)}`.

**`click`**
- `now` not a finite number, or `chore-at-click` nil → `world` unchanged.
- **Due:**
  1. `w1 = (w/chore-done world event)`.
  2. When `(:result (peek (:log w1)))` is `:counted`: `(-> w1 (c/hal-on-chore table) (update :talk tk/add-chore (peek (:log w1)) (name-of …) now))`.
  3. Otherwise `w1`.
- **Not due:**
  - When `:last-at` and `:next-at` are both numbers: `(c/hal-say world table :chore-not-due {:name (name-of …) :chore (name c) :last-at (tk/clock-text last-at offset-min) :next-at (tk/clock-text next-at offset-min)} now)`.
  - Otherwise `world` unchanged.
  - Either way nothing goes in `:log`.

**`untick`**
- Applies only when entry `n` of `(:talk world)` exists with `:kind :chore` and `:done? true`; otherwise `world` unchanged.
- `w1 = (w/undo-chore world (:chore e) now)`.
- When the new log entry is `:undone`: `(-> w1 (c/hal-say table :chore-undone {:name (name-of …) :chore (name (:chore (:chore e)))} now) (update :talk tk/set-done n false nil))`.
- Otherwise `w1`: the ignored log entry only, with the talk and Hal unchanged.

**`tick`**
- Applies only when entry `n` exists with `:kind :chore` and `:done? false`; otherwise `world` unchanged.
- **Chore due at `now`:**
  1. `w1 = (w/redo-chore world (:chore e) now)`.
  2. When it's counted: `(-> w1 (c/hal-on-chore table) (update :talk tk/set-done n true (tk/chore-record (peek (:log w1)))))`. The same box is re-ticked with the new record; no new entry.
- **Not due:** the same `:chore-not-due` line as `click` (the box stays unticked).

## 4. `pm.pick` (new, pure; requires `cljs.reader`)

| Def / signature | Behaviour |
|---|---|
| `click-max-px` · `click-max-ms` | `6` · `400` |
| `(click? down up)` | Both maps with finite `:x :y :t`, `dx²+dy² < 36`, and `0 ≤ (- (:t up) (:t down)) < 400`. Otherwise false, so a drag is not a click. |
| `(ndc client-x client-y rect)` | With `rect` `{:left :top :width :height}` and width, height > 0: `[(- (* 2 (/ (- cx left) width)) 1) (+ (* -2 (/ (- cy top) height)) 1)]`. Otherwise nil. |
| `(key-of-name s)` | `s` must match `#"^(\[[^\]]*\])(?:/\d+)?$"`. Read group 1 (try/catch). Return it when it's a 2-vector whose first element is a keyword; otherwise nil. Mesh names are `"[:flora :body]/3"` and group names `"[:flora :body]"` (pm.scene). |
| `(pick-mirror plan hits)` | `hits`: a sequence of name chains, nearest hit first, each chain being the object's name then its ancestors'. Return the `:mirror` of the plan item whose `:key` equals the first `key-of-name` found, scanning hits in order and names in order. nil if none. |
| `(clickable? world mirror-id)` | true when the mirror exists and `(seq (:chores mirror))` |

## 5. `pm.talk` additions (builder)

| Signature | Behaviour |
|---|---|
| `(chore-record entry)` | `(select-keys entry [:id :mirror :chore :at :prev-at :consumed])` when the result is valid (§1.3); otherwise nil |
| `(add-chore talk log-entry who now)` | See below |
| `(entry talk n)` | The entry with `:n` n, else nil |
| `(set-done talk n done? record)` | When entry `n` is `:kind :chore`: assoc `:done? done?`, plus `:chore record` when `record` is a map. Otherwise `talk` unchanged (identical). |
| `(chore-label entry offset-min)` | `(str text " · " (clock-text (:at (:chore entry)) offset-min))`, e.g. `"Juniper's hay · 8:16 PM"`. nil entry → nil. |

**`add-chore`**
- Takes `r = (chore-record log-entry)`. Returns `talk` unchanged (`talk` nil → `empty-talk`) unless `r`, a non-blank `who` and a finite `now` are all present.
- Otherwise it appends `{:n n' :who who :text (str who "'s " (name (:chore r))) :at now :kind :chore :mirror (:mirror r) :chore r :done? true}`:
  - no show keys, and `:queue-end` is untouched;
  - capped like `append`;
  - `:seen n'` when `:open?`.

**Changes to existing functions**
- `save-text` also keeps `:chore` and `:done?`.
- `restore` keeps a `:kind :chore` entry only when its `:chore` is a valid record and `:done?` is a boolean. It cleans the record with `chore-record`; other chore entries are dropped.
- `drop-repeats` passes chore entries through and doesn't track them, so two "Flora's feed" boxes a minute apart both stay.
- `last-says` ignores chore entries.
- `observe`, `append` and `caption` are unchanged; chore entries never caption, because they have no show keys.

## 6. DOM and wiring (builder; checked live)

### `pm.three`

`(pick-names root camera [x y])`:
- with the kit's THREE: `Raycaster` → `.setFromCamera` (Vector2 x y) → `.intersectObject root true`;
- returns a vector, nearest hit first, of each hit's name chain (object, then parents up to and including `root`, skipping blank names);
- wrapped in try/catch → `[]`.

### `pm.scene`

`(mounted-root)` and `(current-plan)` return the mounted `:root` and `:plan`, or nil.

### `pm.ui`

`render-panel!` becomes `(render-panel! entries offset-of on-pick on-check keep-scroll?)`.
- **Chore entries** render as `<li class="chore">` (plus class `undone` when not `:done?`) containing:
  - the `.time` span;
  - `<label>` wrapping `<input type="checkbox" data-n=N>` (`checked` = `:done?`) and a `.text` span with `(tk/chore-label e offset)`.
  - The input's `change` event calls `(on-check entry (.-checked input))`.
  - Rows don't call `on-pick`. Space toggles the focused box natively.
- **Other entries** render as today.
- Before clearing, remember the `data-n` of a focused checkbox and `scrollTop`. Afterwards, refocus that checkbox if it's still there.
- Scroll: with `keep-scroll?`, restore `scrollTop`; otherwise scroll to the bottom as today.

### `index.html` styles

```css
#talk-list li.chore label { cursor: pointer; }
#talk-list li.chore input { margin: 0 6px 0 0; vertical-align: middle; }
#talk-list li.chore.undone .text { text-decoration: line-through; opacity: .6; }
```

### `pm.core`

Requires `[pm.chores :as chores]` and `[pm.pick :as pick]`.

1. **Keys.**
   - Delete `on-key!` and its docstring.
   - The capture keydown listener keeps only the Escape branch: when `close-key?` holds, close the panel; otherwise do nothing (no preventDefault). The kit gets every key, including F and G.
2. **Pointer (`wire-pointer! h`, called in `start-kit!` after `scene/mount!`).** `canvas` is `(.. h -renderer -domElement)`; `offset-now` is `(- (.getTimezoneOffset (js/Date.)))`.
   - **Press.** `pointerdown` on the canvas (passive; `isPrimary` and `button` 0) stores `{:x clientX :y clientY :t timeStamp}`.
   - **Release.** `pointerup` on `window` (capture, passive) runs only when there is a stored press; it clears the press. When `(pick/click? down up)`:
     1. `m = (pick/pick-mirror (scene/current-plan) (t/pick-names (scene/mounted-root) (.-camera h) (pick/ndc x y rect)))`, with `rect` from `getBoundingClientRect`;
     2. when `(pick/clickable? @!world m)`: `(swap! !world chores/click content/hal-lines m (js/Date.now) (offset-now))` and console-log the last log entry.
     - Never call `preventDefault` or `stopPropagation`, so the kit's camera drag is untouched.
   - **Hover.** On `pointermove` over the canvas with `buttons` 0, at most every 50 ms, pick the same way.
     - Over a clickable mirror: set `canvas.style.cursor` to `"pointer"`, saving the previous value first.
     - Otherwise: restore the saved value.
3. **Checkbox.** `on-check` is `(fn [e checked?] …)`:
   - `(swap! !world (fn [w] (if checked? (chores/tick w content/hal-lines (:n e) (js/Date.now) (offset-now)) (chores/untick w content/hal-lines (:n e) (js/Date.now)))))`;
   - then `(swap! !talk-ui assoc :listed nil)`, so the panel re-renders from state even when the undo didn't apply and the box must snap back.
4. **Panel re-render.** In `talk-tick!`, `:listed` now holds the `(:entries t')` it rendered. Re-render when the current entries aren't `identical?` to it. Pass `keep-scroll?` true when `(:n t')` equals the `:n` of the last render.

`README.md`: one sentence. Click a creature to do its chore; in 💭, untick a chore to undo it. The F/G keys are gone.

## 7. Tests

### 7.1 Edits to existing tests (tester)

**E1. `world_test.cljs`**
- In `t03-load-real-estate`, the `:hud` assertion becomes `(is (= {:mirrors [:flora]} (:hud W)))`.
- In `t11`, `(:log W1)` becomes `[(assoc fed :result :counted :id "flora/feed@1800000000000" :prev-at nil :consumed [:stock 1])]`.
- **Delete** `t30-key-f`, `t31-key-second-mirror`, `t32-key-end-to-end` and `t40-g-hays-the-second-mirror`, plus the `;; key-event` comment. The world tests go from 33 to 29.

**E2. `plan_test.cljs`**
- Rename `t12-key-f-end-to-end` to `t12-feed-end-to-end`.
- In it, `(w/key-event W "F" T0)` becomes `fed`, and `(w/key-event K "f" (+ T0 5000))` becomes `(ev :flora :feed (+ T0 5000) :key)`.
- In the `label-changes` test (around line 254), `(w/key-event W "F" T0)` becomes `fed`.
- Expected values are unchanged.

**E3. `content_test.cljs`**
- Every `(w/key-event X "f" t)` becomes `(ev :flora :feed t :key)`, with the same world and time: the `K` def, lines ~170, ~191, ~274 and ~279.
- In `t20`, the set becomes `#{:jack-in :chore-done :chore-not-due :chore-undone}`.
- In `t28`:
  - `(c/missing-hal {})` → `#{:jack-in :chore-done :chore-not-due :chore-undone}`
  - `{:jack-in [] :chore-done "x"}` → `#{:jack-in :chore-not-due :chore-undone}`
  - `{:jack-in ["a" 7] :chore-done ["b"]}` → `#{:jack-in :chore-not-due :chore-undone}`

**E4. `talk_test.cljs`**: `(w/key-event W "f" (+ A 1000))` (~line 339) becomes `(ev :flora :feed (+ A 1000) :key)`.

**E5. `overlay_test.cljs`**: no edit.

That leaves 151 existing tests (content 28, overlay 32, plan 34, talk 28, world 29).

### 7.2 `test/pm/chores_test.cljs` (new, `ns pm.chores-test`)

Requires: `cljs.test`, `[pm.chores :as ch]`, `[pm.pick :as pk]`, `[pm.talk :as tk]`, `[pm.world :as w]`, `[pm.content :as c]`, `[pm.overlay :as o]`, `[pm.plan :as p]`, `[pm.fixtures :as fx]`.

```clojure
(def A   1794013200000)          ; Fri 2026-11-06 17:00 PST (day 20764)
(def T1  1794024960000)          ; A + 3h16m = 8:16 PM PST (day 20764)
(def D2  172800000)
(def H12 43200000)
(def OFF -480)                   ; PST
(def W (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(def H {:chore-done    ["{name}: {chore} done" "did {chore} for {name}"]
        :chore-not-due ["last {last-at}, next {next-at}"]
        :chore-undone  ["undid {name}'s {chore}" "took back {chore}"]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn st [world id] (get-in world [:mirrors id :state]))
(defn e1 [world] (tk/entry (:talk world) 1))
(def RJ {:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at T1 :prev-at nil :consumed [:bales 1]})
(def C1 (ch/click W H :juniper T1 OFF))                    ; Juniper hayed by a click at 8:16 PM
(def U  (ch/untick C1 H 1 (+ T1 120000)))                 ; ...and unticked at 8:18 PM
(def FX (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})))
(def WF (o/apply-file W {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}}))  ; Hal keeps Flora
```

Hal seeds are `day + (count log)`. Day 20764 covers A through T1+180000; A+D2 is day 20766.

Clock times at OFF: T1 → 8:16 PM; T1+12h → 8:16 AM; T1+2 min 1 s → 8:18 PM; T1+3 min → 8:19 PM; A → 5:00 PM; A+90 min → 6:30 PM.

#### World: records, next-due, undo

**t01-event-id**
- `(w/event-id {:mirror :juniper :chore :hay :at T1})` → `"juniper/hay@1794024960000"`
- `{:mirror nil :chore :hay :at T1}` → nil; `{:mirror :juniper :chore :hay :at nil}` → nil

**t02-counted-entry-records-undo**
- `(peek (:log C1))` → `{:event :chore-done :mirror :juniper :chore :hay :at T1 :via :click :who :me :result :counted :id "juniper/hay@1794024960000" :prev-at nil :consumed [:bales 1]}`
- `(peek (:log (w/chore-done C1 (ev :juniper :hay (+ T1 1000) :click))))` → `(assoc (ev :juniper :hay (+ T1 1000) :click) :result :ignored :reason :not-due)` (ignored entries are unchanged)
- `(:consumed (peek (:log (w/chore-done (assoc-in W [:mirrors :juniper :state :bales] 0) (ev :juniper :hay T1 :click)))))` → `[:bales 0]`
- `(contains? (peek (:log (w/chore-done FX (ev :fern :mist A :click)))) :consumed)` → false
- `(:prev-at (peek (:log (w/chore-done WF (ev :flora :feed (+ A D2) :click)))))` → `1794013200000`

**t03-next-due-and-last-done**
- `(w/next-due W :juniper :hay)` → nil; `(w/last-done-at W :juniper :hay)` → nil
- `(w/next-due C1 :juniper :hay)` → `1794068160000`; `(w/last-done-at C1 :juniper :hay)` → `T1`
- `(w/next-due WF :flora :feed)` → `1794186000000`
- `(w/due? C1 :juniper :hay 1794068159999)` → false; `(w/due? C1 :juniper :hay 1794068160000)` → true (matches `due?` exactly)
- `(w/next-due W :rex :feed)` → nil; `(w/next-due W :juniper :nope)` → nil
- `(w/next-due (assoc-in W [:mirrors :juniper :chores 0 :every] "bad") :juniper :hay)` → nil, and the same with `:last-hayed T1` set

#### Click

**t04-chore-at-click**
- `(ch/chore-at-click W :juniper T1)` → `{:due? true :mirror :juniper :chore :hay :event {:event :chore-done :mirror :juniper :chore :hay :at T1 :via :click :who :me}}`
- `(ch/chore-at-click C1 :juniper (+ T1 60000))` → `{:due? false :mirror :juniper :chore :hay :last-at 1794024960000 :next-at 1794068160000}`
- `(:due? (ch/chore-at-click C1 :juniper 1794068159999))` → false; `(:due? (ch/chore-at-click C1 :juniper 1794068160000))` → true
- `(ch/chore-at-click W :rex T1)` → nil; `(ch/chore-at-click (w/load-estate "{:mirrors [{:id :stone :state {}}]}") :stone T1)` → nil

**t05-first-due-chore-wins**
- Setup: `M = (w/chore-done (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"} {:id :repot :every "1w"}]}]})) (ev :fern :mist A :click))`
- `(:chore (ch/chore-at-click M :fern (+ A 1000)))` → `:repot`
- `(ch/chore-at-click (w/chore-done M (ev :fern :repot (+ A 1000) :click)) :fern (+ A 2000))` → `{:due? false :mirror :fern :chore :mist :last-at A :next-at 1794018600000}`

**t06-click-juniper-when-due**
- `(st C1 :juniper)` → `{:source :sample :bales 5 :last-hayed 1794024960000}`
- `(count (:log C1))` → 1
- `(:hal C1)` → `{:event :chore-done :text "did hay for Juniper" :until 1794024966000}` (seed 20765)
- `(:entries (:talk C1))` → `[{:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper :chore RJ :done? true}]`
- `(w/markers C1 :juniper (+ T1 1000))` → `[]` (the hungry bubble is gone)
- `(w/markers C1 :flora T1)` → `[{:say "I'm hungry"}]` (the other mirror is untouched)

**t07-click-again-not-due-with-times**
- `C2 = (ch/click C1 H :juniper (+ T1 60000) OFF)`
- `(:hal C2)` → `{:event :chore-not-due :text "last 8:16 PM, next 8:16 AM" :until 1794025026000}`
- `(= (:log C2) (:log C1))`, `(= (:mirrors C2) (:mirrors C1))` and `(= (:talk C2) (:talk C1))` → true (nothing logged)
- `(ch/click C1 H :juniper nil OFF)` → `= C1`; `(ch/click W H :rex T1 OFF)` → `= W`

**t08-click-flora-the-second-mirror**
- `FC = (ch/click W H :flora T1 OFF)`:
  - `(st FC :flora)` → `{:source :sample :stock 14 :last-fed 1794024960000}`
  - `(:text (:hal FC))` → `"did feed for Flora"`
  - `(:text (e1 FC))` → `"Flora's feed"`
  - `(:chore (e1 FC))` → `{:id "flora/feed@1794024960000" :mirror :flora :chore :feed :at T1 :prev-at nil :consumed [:stock 1]}`
- `(:text (:hal (ch/click FC H :flora (+ T1 60000) OFF)))` → `"last 8:16 PM, next 8:16 PM"` (2 d later)
- `(w/markers FC :juniper T1)` → `[{:say "Hay, please?"}]`

**t09-hal-kept-flora-undo-restores-hal's-values**
- `(:text (:hal (ch/click WF H :flora T1 OFF)))` → `"last 5:00 PM, next 5:00 PM"`
- `FD = (ch/click WF H :flora (+ A D2) OFF)`:
  - `(st FD :flora)` → `{:source :hal :stock 36 :last-fed 1794186000000}`
  - `(:text (:hal FD))` → `"did feed for Flora"` (seed 20767)
  - `(:chore (e1 FD))` → `{:id "flora/feed@1794186000000" :mirror :flora :chore :feed :at 1794186000000 :prev-at 1794013200000 :consumed [:stock 1]}`
- `FU = (ch/untick FD H 1 (+ A D2 1000))`:
  - `(st FU :flora)` → `{:source :hal :stock 37 :last-fed 1794013200000}`
  - `(:text (:hal FU))` → `"undid Flora's feed"` (seed 20768)

#### Untick and tick

**t10-untick-restores-done-time-and-bales**
- `(st U :juniper)` → `{:source :sample :bales 6 :last-hayed nil}`, and `(= (get-in U [:mirrors :juniper]) (get-in W [:mirrors :juniper]))` → true
- `(peek (:log U))` → `{:event :chore-undone :mirror :juniper :chore :hay :at 1794025080000 :id "juniper/hay@1794024960000" :result :undone}`
- `(:hal U)` → `{:event :chore-undone :text "undid Juniper's hay" :until 1794025086000}` (seed 20766)
- `(e1 U)` → `(assoc (e1 C1) :done? false)`; `(count (:entries (:talk U)))` → 1
- `(w/markers U :juniper (+ T1 120000))` → `[{:say "Hay, please?"}]` (the bubble is back)

**t11-tick-again-redoes-when-due**
- `R = (ch/tick U H 1 (+ T1 180000) OFF)`
- `(peek (:log R))` → `{:event :chore-done :mirror :juniper :chore :hay :at 1794025140000 :via :tick :who :me :result :counted :id "juniper/hay@1794025140000" :prev-at nil :consumed [:bales 1]}`
- `(st R :juniper)` → `{:source :sample :bales 5 :last-hayed 1794025140000}`
- `(:text (:hal R))` → `"did hay for Juniper"` (seed 20767)
- `(e1 R)` → `{:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper :chore {:id "juniper/hay@1794025140000" :mirror :juniper :chore :hay :at 1794025140000 :prev-at nil :consumed [:bales 1]} :done? true}`
- `(count (:entries (:talk R)))` → 1
- `(tk/chore-label (e1 R) OFF)` → `"Juniper's hay · 8:19 PM"`
- `(:via (peek (:log (w/redo-chore U RJ (+ T1 180000)))))` → `:tick`

**t12-tick-when-not-due-stays-unticked**
- `U2 = (ch/click U H :juniper (+ T1 121000) OFF)`: Juniper is hayed again by a new click, giving box 2
  - `(count (:entries (:talk U2)))` → 2; `(:done? (tk/entry (:talk U2) 2))` → true
- `X = (ch/tick U2 H 1 (+ T1 130000) OFF)`:
  - `(:hal X)` → `{:event :chore-not-due :text "last 8:18 PM, next 8:18 AM" :until 1794025096000}`
  - `(:done? (e1 X))` → false
  - `(count (:log X))` → 3
  - `(st X :juniper)` → `{:source :sample :bales 5 :last-hayed 1794025081000}`

**t13-untick-guards**
- `CG = (assoc-in C1 [:mirrors :juniper :state :last-hayed] (+ T1 5000))` and `UG = (ch/untick CG H 1 (+ T1 120000))`:
  - `(peek (:log UG))` → `{:event :chore-undone :mirror :juniper :chore :hay :at 1794025080000 :id "juniper/hay@1794024960000" :result :ignored :reason :not-current}`
  - `(:bales (st UG :juniper))` → 5; `(:done? (e1 UG))` → true; `(= (:hal UG) (:hal CG))` → true
- `(ch/untick U H 1 (+ T1 200000))` → `= U` (already unticked)
- `(ch/untick C1 H 7 T1)` → `= C1`; `(ch/tick C1 H 1 T1 OFF)` → `= C1` (already ticked)
- With `CO = (assoc C1 :talk (tk/observe (:talk C1) C1 T1))`: `(ch/untick CO H 2 T1)` → `= CO` (entry 2 is Hal's line, not a box)
- `(peek (:log (w/undo-chore W {:id "x" :mirror :rex :chore :feed :at 1 :prev-at nil} A)))` → `{:event :chore-undone :mirror :rex :chore :feed :at A :id "x" :result :ignored :reason :unknown-mirror}`
- `(:reason (peek (:log (w/undo-chore W {:id "x" :mirror :juniper :chore :nope :at 1} A))))` → `:unknown-chore`

**t14-zero-stock-and-a-chore-without-done-at**
- `J0 = (ch/click (assoc-in W [:mirrors :juniper :state :bales] 0) H :juniper T1 OFF)`:
  - `(:consumed (:chore (e1 J0)))` → `[:bales 0]`
  - `(st (ch/untick J0 H 1 (+ T1 1000)) :juniper)` → `{:source :sample :bales 0 :last-hayed nil}` (no bale is invented)
- `FC = (ch/click FX H :fern A OFF)`:
  - `(get-in FC [:mirrors :fern :done])` → `{:mist 1794013200000}`
  - `(:text (:hal FC))` → `"did mist for fern"`
  - `(:text (e1 FC))` → `"fern's mist"`
  - `(:chore (e1 FC))` → `{:id "fern/mist@1794013200000" :mirror :fern :chore :mist :at A :prev-at nil}`
- `(:text (:hal (ch/click FC H :fern (+ A 60000) OFF)))` → `"last 5:00 PM, next 6:30 PM"`
- `FU = (ch/untick FC H 1 (+ A 1000))`:
  - `(get-in FU [:mirrors :fern :done])` → `{}`
  - `(w/due? FU :fern :mist (+ A 1000))` → true
  - `(:text (:hal FU))` → `"undid fern's mist"`

**t15-once-chore-not-due-is-silent**
- Setup: `RK = (w/load-estate (pr-str {:mirrors [{:id :rock :state {} :chores [{:id :polish :once true}]}]}))` and `R1 = (ch/click RK H :rock A OFF)`
- `(:result (peek (:log R1)))` → `:counted`
- `(ch/chore-at-click R1 :rock (+ A 1000))` → `{:due? false :mirror :rock :chore :polish :last-at A :next-at nil}`
- `(ch/click R1 H :rock (+ A 1000) OFF)` → `= R1`

**t16-missing-hal-events-fall-back-silently**
- `(ch/click W {} :juniper T1 OFF)`:
  - `(:hal …)` → nil
  - `(:bales (st … :juniper))` → 5
  - `(count (:entries (:talk …)))` → 1
- `(ch/click C1 {} :juniper (+ T1 60000) OFF)` → `= C1`
- `(ch/untick C1 {} 1 (+ T1 120000))`:
  - `(:bales (st … :juniper))` → 6
  - `(:done? (e1 …))` → false
  - `(:hal …)` → `= (:hal C1)`

**t17-real-hal-lines**
- `(:text (:hal (ch/click C1 c/hal-lines :juniper (+ T1 60000) OFF)))` ∈ `(set (map #(c/fill % {:name "Juniper" :chore "hay" :last-at "8:16 PM" :next-at "8:16 AM"}) (:chore-not-due c/hal-lines)))`, and contains no `"{"`
- `(:text (:hal (ch/untick C1 c/hal-lines 1 (+ T1 120000))))` ∈ the same fill of `(:chore-undone c/hal-lines)` with `{:name "Juniper" :chore "hay"}`, and contains no `"{"`

#### Talk

**t18-add-chore**
- `(tk/caption (:talk C1) T1)` → nil (a box is never captioned)
- `(:queue-end (:talk C1))` → 0; `(tk/unread (:talk C1))` → 1
- `(tk/chore-record (peek (:log C1)))` → `RJ`; `(tk/chore-record {:id "" :mirror :a :chore :b :at 1})` → nil
- each → `= tk/empty-talk`:
  - `(tk/add-chore tk/empty-talk nil "Juniper" T1)`
  - `(tk/add-chore tk/empty-talk (peek (:log C1)) "" T1)`
  - `(tk/add-chore tk/empty-talk (peek (:log C1)) "Juniper" nil)`
- `(:seen (tk/add-chore (tk/open-panel tk/empty-talk) (peek (:log C1)) "Juniper" T1))` → 1

**t19-hal's-line-next-to-the-box**
- `TO = (tk/observe (:talk C1) C1 T1)`:
  - `(mapv :text (:entries TO))` → `["Juniper's hay" "did hay for Juniper" "I'm hungry"]`
  - `(mapv :kind (:entries TO))` → `[:chore :hal :creature]`
  - `(:n (tk/caption TO T1))` → 2
  - `(:show-until (tk/entry TO 2))` → `1794024962500`
  - `(:show-at (tk/entry TO 3))` → `1794024962500`

**t20-set-done-and-label**
- `(tk/set-done (:talk C1) 1 false nil)` → `= (:talk U)`
- `(identical? (:talk C1) (tk/set-done (:talk C1) 9 false nil))` → true
- With `TO` from t19: `(identical? TO (tk/set-done TO 2 false nil))` → true
- `(tk/chore-label (e1 C1) OFF)` → `"Juniper's hay · 8:16 PM"`; `(tk/chore-label nil OFF)` → nil

**t21-undo-survives-a-reload**
- `(first (:entries (tk/restore (tk/save-text (:talk U)))))` → `(e1 U)`, which is `{:n 1 :who "Juniper" :text "Juniper's hay" :at T1 :kind :chore :mirror :juniper :chore RJ :done? false}`
- `WR = (-> W (o/apply-play (o/play-of C1 W)) (assoc :talk (tk/restore (tk/save-text (:talk C1)))))`:
  - `(st WR :juniper)` → `{:source :sample :bales 5 :last-hayed 1794024960000}`
  - `UR = (ch/untick WR H 1 (+ T1 120000))`:
    - `(st UR :juniper)` → `{:source :sample :bales 6 :last-hayed nil}`
    - `(:text (:hal UR))` → `"took back hay"` (a fresh log, so the seed is 20765)
    - `(:done? (e1 UR))` → false
- `(o/play-of U W)` → nil (an undone sample chore leaves nothing to save)

**t22-restore-validates-chore-entries**
- Setup:

```clojure
(def RT (pr-str {:v 1 :n 6 :seen 6 :entries
  [{:n 1 :who "Flora" :text "I'm hungry" :at 10 :kind :creature :mirror :flora}
   {:n 2 :who "Flora" :text "Flora's feed" :at 20 :kind :chore :mirror :flora
    :chore {:id "flora/feed@20" :mirror :flora :chore :feed :at 20 :prev-at nil :consumed [:stock 1]} :done? true}
   {:n 3 :who "Flora" :text "Flora's feed" :at 30 :kind :chore :mirror :flora
    :chore {:id "flora/feed@30" :mirror :flora :chore :feed :at 30 :prev-at 20} :done? false}
   {:n 4 :who "Flora" :text "Flora's feed" :at 40 :kind :chore :mirror :flora :chore "x" :done? true}
   {:n 5 :who "Flora" :text "Flora's feed" :at 50 :kind :chore :mirror :flora
    :chore {:id "flora/feed@50" :mirror :flora :chore :feed :at 50 :consumed [:stock "1"]} :done? true}
   {:n 6 :who "Flora" :text "Flora's feed" :at 60 :kind :chore :mirror :flora
    :chore {:id "flora/feed@60" :mirror :flora :chore :feed :at 60} :done? "yes"}]}))
```

- `(mapv :n (:entries (tk/restore RT)))` → `[1 2 3]` (boxes 2 and 3 are both kept despite the same text within 30 min)
- `(:chore (tk/entry (tk/restore RT) 3))` → `{:id "flora/feed@30" :mirror :flora :chore :feed :at 30 :prev-at 20}`
- `(:last-say (tk/restore RT))` → `{:flora "I'm hungry"}` (boxes aren't bubbles)

#### Pointer picking

**t23-a-drag-isn't-a-click** (`d = {:x 100 :y 100 :t 1000}`)
- `(pk/click? d {:x 103 :y 104 :t 1300})` → true (5 px, 300 ms)
- `{:x 104 :y 105 :t 1300}` → false (√41 px, a drag)
- `{:x 100 :y 106 :t 1100}` → false (6 px is not under 6)
- `{:x 100 :y 100 :t 1400}` → false (400 ms); `{:x 100 :y 100 :t 1399}` → true
- `{:x 100 :y 100 :t 900}` → false
- `(pk/click? nil {:x 1 :y 1 :t 1})` → false; `(pk/click? d {:x "1" :y 1 :t 1})` → false
- `pk/click-max-px` → 6; `pk/click-max-ms` → 400

**t24-ndc** (`r = {:left 0 :top 0 :width 800 :height 600}`)
- `(pk/ndc 400 300 r)` → `[0 0]`; `(pk/ndc 0 0 r)` → `[-1 1]`; `(pk/ndc 800 600 r)` → `[1 -1]`; `(pk/ndc 200 450 r)` → `[-0.5 -0.5]`
- `(pk/ndc 150 75 {:left 100 :top 50 :width 200 :height 100})` → `[-0.5 0.5]`
- `(pk/ndc 1 1 {:left 0 :top 0 :width 0 :height 10})` → nil

**t25-key-of-name**
- `"[:flora :body]/3"` → `[:flora :body]`; `"[:flora :jar]"` → `[:flora :jar]`; `"[:zed 0]/1"` → `[:zed 0]`
- → nil: `"pm-mirrors"`, `""`, `nil`, `"[:flora]"`, `"[\"a\" :b]"`, `"[:a :b]/x"`, `"[:a :b"`, `"{:a 1}"`

**t26-pick-mirror** (`P = (p/scene-plan W A)`)
- `(pk/pick-mirror P [["[:juniper :body]/2" "[:juniper :body]" "pm-mirrors"]])` → `:juniper`
- `[["[:flora :jar]/0" "[:flora :jar]"]]` → `:flora` (her worm jar is hers)
- `[["Water" "Scene"] ["[:flora :body]/1" "[:flora :body]"]]` → `:flora`
- `[["[:juniper :body]/0"] ["[:flora :body]/0"]]` → `:juniper` (nearest wins)
- `[[nil "[:flora :body]"]]` → `:flora`
- → nil: `[["Water"]]`, `[]`, `nil`, `[["[:rex :body]/0"]]` (not in the plan)

**t27-clickable**
- `(pk/clickable? W :flora)` → true; `(pk/clickable? W :juniper)` → true
- `(pk/clickable? W :rex)` → false; `(pk/clickable? (w/load-estate "{:mirrors [{:id :stone}]}") :stone)` → false

**t28-no-chore-keys**
- `(:hud W)` → `{:mirrors [:flora]}`; `(contains? (:hud W) :keys)` → false

**Totals:** 28 new tests in `chores_test`. 151 existing tests remain after E1–E5. That makes **179** tests.

## 8. Live check (Hal)

- **Click Juniper when she's due.**
  - Caption: **Hal** — a `:chore-done` line.
  - Her hay bubble goes.
  - 💭 shows ☑ `Juniper's hay · 8:16 PM` above Hal's line.
- **Click her again.** A `:chore-not-due` line with `8:16 PM` / `8:16 AM`.
- **Untick the box.**
  - The line strikes through.
  - The bubble `Hay, please?` comes back.
  - Hal says a `:chore-undone` line.
  - After a reload the box is still unticked, and ticking it redoes the chore.
- **Clicking Flora's jar** feeds Flora.
- **Pointer and keys.**
  - The hand cursor shows over the creatures only.
  - Dragging the camera never triggers a chore.
  - F and G now do only what the kit does.
  - Escape still closes 💭.

## 9. Not in this round

Writing back to Hal's file, the calculator/chat panel, new chores or creatures, and clicking the 3D bubble sprites (only meshes are picked).
