# Spec: the creature's request is the todo

Quest: `.crew/quests/request-todos.md`. Round dir: `crew/runs/request-todos-20261106-2102/`.

This builds on `click-chores` (in the tree, 179 tests). A creature's 💭 line that came from a `:due?` rule is a **request**: it carries the chore it asks for (`:asks`) and a box (`:done?`).

- A counted chore ticks the **latest open box** for that mirror and chore. A separate "Juniper's hay" line is added only when nothing open asked for it.
- Ticking an open request does the chore with `:via :chat`.
- Unticking undoes it with click-chores' `undo-chore`. Ticking again redoes it.
- Requests the world no longer owes, and older ones a new request replaces, lose their box.
- Old histories get a box from the current state, or none.

All rules are pure and tested. No code names a mirror.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/world.cljs`, `src/pm/talk.cljs`, `src/pm/chores.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs`, `public/index.html` (`<style>` only), `resources/estate.edn` (`:past` on the two chores), `README.md` (one sentence) | `resources/content/*.edn`, `content.cljs`, `overlay.cljs`, `plan.cljs`, `scene.cljs`, `three.cljs`, `pick.cljs`, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/requests_test.cljs` (new), plus exactly the edits E1–E2 in §6.1 | every `src/`, `resources/`, `public/` file |

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. No npm deps, 0 warnings.

## 1. Data

### 1.1 A request entry (a creature line, `:kind :creature`)

```clojure
;; open: the chore is owed
{:n 2 :who "Juniper" :text "Hay, please?" :at 1794024180000 :kind :creature :mirror :juniper
 :asks :hay :done? false :show-at … :show-until …}
;; answered: a chore that counted after it
{… :asks :hay :done? true
 :chore {:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at 1794024960000
         :prev-at nil :consumed [:bales 1]}}
;; unticked again: :done? false, and the :chore record is kept, so tick = redo
```

A plain creature line has no `:asks`, `:done?` or `:chore`. Hal's lines never do.

### 1.2 Boxes

A **box** is an entry that has a checkbox:
- a request (`:asks` is a keyword), or
- a click-chores chore line (`:kind :chore`).

| Term | Meaning |
|---|---|
| **box chore** | `(:asks e)` for a request, `(:chore (:chore e))` for a chore line |
| **open box for [m c]** | the **latest** box with `:mirror m`, box chore `c`, and `:done?` not true |
| **record-less open request** | a request with `:done?` false and no `:chore`. This is the only kind of box that can lose its box. |

### 1.3 Estate

Each of the two chores gets a past-tense word for the label (`resources/estate.edn`):
- Flora's `{:id :feed …}` gets `:past "fed"`;
- Juniper's `{:id :hay …}` gets `:past "fed"`.

## 2. `pm.world` additions (builder)

- `(asks world mirror-id now)` → a vector of the distinct chore ids, in rule order, of the mirror's rules that meet all three:
  - `:when` is `[:due? c]`;
  - the rule holds at `now`;
  - its `:say` resolves (through `line-for`) to a non-nil string.

  Unknown mirror → `[]`.
- `(chore-past world mirror-id chore-id)` → the chore's `:past` when it is a non-blank string; otherwise `"done"`, including for an unknown mirror or chore.

## 3. `pm.talk` changes (builder)

### New public functions

| Signature | Behaviour |
|---|---|
| `(box? e)` | true for a request or a `:kind :chore` entry |
| `(box-chore e)` | per §1.2; nil for a non-box |
| `(open-box talk m c)` | the latest open box for [m c] (§1.2), else nil |
| `(link-chore talk log-entry who now)` | See below |
| `(box-label entry offset-min past)` | See below |

**`link-chore`**
- `r = (chore-record log-entry)`; nil `r` → `talk` unchanged.
- When `(open-box talk (:mirror r) (:chore r))` is entry `n`: set that entry's `:done? true` and `:chore r`. No new entry is added.
- Otherwise `(add-chore talk log-entry who now)`.

**`box-label`**
- `:kind :chore` → `(chore-label entry offset-min)`, unchanged.
- Request → `(str who " · " text)`, plus `(str " · " past " " (clock-text (:at (:chore entry)) offset-min))` when `:done?` is true.
- Non-box → nil.

### Changes to existing functions

- `append` also takes an `:asks` key. When it's a keyword, the entry gets `:asks c :done? false`.
- `set-done` now applies to any box (requests too), with the same behaviour. A non-box is unchanged (identical).
- `save-text` also keeps `:asks`.
- `restore`, for an entry with a keyword `:asks`:
  - it keeps `:asks`;
  - it keeps `:chore` only when `(chore-record (:chore e))` is valid, then cleaned;
  - `:done?` is `(and (true? (:done? e)) (some? cleaned-chore))`;
  - when `:asks` isn't a keyword, all three fields are dropped and it stays a plain line.
  - Other validation is unchanged.
- `drop-repeats` passes every box through and doesn't track it (requests too).
- `last-says` is unchanged: requests are bubbles.

### `observe` (the Hal part is unchanged)

For each mirror id in sorted order, compute:
- `s = (say-text world id now)`
- `prev = (get-in t [:last-say id])`
- `asks = (w/asks world id now)`
- `c = (first asks)`

**When `s ≠ prev`:**
1. `(assoc-in t [:last-say id] s)`.
2. **Strip the unasked.** Every record-less open request of `id` whose `:asks` isn't in `(set asks)` loses its box: `dissoc :asks :done?`. The world no longer owes it (e.g. Hal's file fed her), so there's no chore behind it.
3. When `s` is blank, stop.
4. **When `c` is non-nil:**
   - **supersede**: every record-less open request for `[id c]` loses its box;
   - then `append` the line with `:asks c`.
5. **Otherwise** `append` it plain, as today.

**When `s = prev`, adopt (old histories).** Only when all of these hold:
- `s` is non-blank;
- `c` is non-nil;
- `(open-box t id c)` is nil;
- the latest non-`:chore` entry with `:mirror id` has `:text` = `s` and no `:asks`.

Then that entry gets `:asks c :done? false`. In every other case `t` is unchanged.

`observe` still returns `talk` itself when nothing changed. Once a box exists, adoption can't fire again.

## 4. `pm.chores` changes (builder)

- **`click`**: when the chore counts, `(update :talk tk/link-chore last-entry who now)` replaces `add-chore`.
- **`untick world table n now`** applies to any box (`tk/box?`) with `:done? true`, not just `:kind :chore`.
  - When the undo applies (`:undone`), after the Hal line and `set-done n false nil`, also do `(assoc-in [:talk :last-say m] (tk/say-text w1 m now))`. The bubble that comes back isn't logged again: the unticked box is the todo.
  - Everything else is unchanged.
- **`tick world table n now offset-min`** applies to any box with `:done? false`. Let `m = (:mirror e)` and `c = (tk/box-chore e)`.
  1. `w1`:
     - when `e` has a `:chore` record: `(w/redo-chore world (:chore e) now)` (`:via :tick`);
     - otherwise: `(w/chore-done world {:event :chore-done :mirror m :chore c :at now :via :chat :who :me})`.
  2. When it's counted: `(-> w1 (c/hal-on-chore table) (update :talk tk/set-done n true (tk/chore-record (peek (:log w1)))))`.
  3. Otherwise: the `:chore-not-due` line for `[m c]` from `world` (as today). When last or next is unknown, `world` is unchanged.
- `chore-at-click` is unchanged.

## 5. DOM (builder; checked live)

**`pm.ui/render-panel!`** becomes `(render-panel! entries offset-of label-of on-pick on-check keep-scroll?)`.
- Every `tk/box?` entry renders through the checkbox row:
  - its text is `(label-of entry)`;
  - class `"chore"` plus `" undone"` for an unticked `:kind :chore`;
  - class `"chore request"` for a request (never struck through: an open request is a todo).
- Other entries render as today.

**`pm.core`**
- `label-of` is `(fn [e] (talk/box-label e (offset-of (or (:at (:chore e)) (:at e))) (world/chore-past @!world (:mirror e) (talk/box-chore e))))`. Pass it to `render-panel!`.
- `on-check` is unchanged (`chores/tick` / `chores/untick` by `:n`, then `:listed nil`).

**`index.html`**: `#talk-list li.request .text { font-weight: 500; }`.

**`README.md`**: one sentence. A creature's request in 💭 is its todo; ticked when the chore's done, untick to undo, tick to do it again.

## 6. Tests

### 6.1 Edits to existing tests (tester)

**E1. `talk_test.cljs`**
- In the `E2` def, add `:asks :feed :done? false`. In `E3`, add `:asks :hay :done? false`.
- In `t09`, the expected `(peek (:entries T6))` map gains `:asks :feed :done? false`.
- In `t10`, the expected peek map gains `:asks :hay :done? false`.
- Nothing else changes. t06, t13, t14, t20 and t22 follow from E2/E3: `restore` keeps `:asks`/`:done?`, and `bare` drops only the show keys.

**E2. `chores_test.cljs`**
- **t12 is rewritten.** A click after untick now re-ticks the unticked box instead of adding box 2.
  - Replace the `U2` lines with:
    - `U2 = (ch/click U H :juniper (+ T1 121000) OFF)`: `(count (:entries (:talk U2)))` → 1; `(:done? (e1 U2))` → true; `(:chore (e1 U2))` → `{:id "juniper/hay@1794025081000" :mirror :juniper :chore :hay :at 1794025081000 :prev-at nil :consumed [:bales 1]}`.
    - `X0 = (w/chore-done U (ev :juniper :hay (+ T1 121000) :click))` (done outside the box).
  - The `X` lines become `X = (ch/tick X0 H 1 (+ T1 130000) OFF)`, with the same four assertions and values as today: the Hal line `"last 8:18 PM, next 8:18 AM"` until `1794025096000`; `:done?` false; log count 3; the state `{:source :sample :bales 5 :last-hayed 1794025081000}`.
- **t20**: `(tk/set-done (:talk C1) 1 false nil)` → `= (:talk U)` becomes `(assoc (tk/set-done (:talk C1) 1 false nil) :last-say {:juniper "Hay, please?"})` → `= (:talk U)`.

That leaves 179 existing tests: content 28, overlay 32, plan 34, talk 28, world 29, chores 28.

### 6.2 `test/pm/requests_test.cljs` (new, `ns pm.requests-test`)

Requires: `cljs.test`, `[pm.chores :as ch]`, `[pm.talk :as tk]`, `[pm.world :as w]`, `[pm.overlay :as o]`, `[pm.fixtures :as fx]`.

```clojure
(def A    1794013200000)          ; 5:00 PM PST, day 20764
(def R0   1794024180000)          ; A + 3h03m = 8:03 PM: when the creatures ask
(def T1   1794024960000)          ; 8:16 PM: Juniper fed
(def H12  43200000)
(def NEXT 1794096000000)          ; first ms of day 20765 (Nov 7 4:00 PM PST)
(def OFF  -480)
(def W  (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))
(def H  {:chore-done    ["{name}: {chore} done" "did {chore} for {name}"]
         :chore-not-due ["last {last-at}, next {next-at}"]
         :chore-undone  ["undid {name}'s {chore}" "took back {chore}"]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn st [world id] (get-in world [:mirrors id :state]))
(defn en [world n] (tk/entry (:talk world) n))
(defn texts [t] (mapv :text (:entries t)))
(def RJ {:id "juniper/hay@1794024960000" :mirror :juniper :chore :hay :at T1 :prev-at nil :consumed [:bales 1]})
(def TR (tk/observe tk/empty-talk W R0))                  ; Flora and Juniper ask at 8:03 PM
(def WR (assoc W :talk TR))
(def K  (ch/click WR H :juniper T1 OFF))                  ; Juniper clicked at 8:16 PM
(def KO (assoc K :talk (tk/observe (:talk K) K T1)))     ; ...and the next frame observed
(def KU (ch/untick KO H 2 (+ T1 120000)))                 ; unticked at 8:18 PM
(def KT (ch/tick KU H 2 (+ T1 180000) OFF))               ; ticked again at 8:19 PM
(def WN (w/chore-done WR (ev :juniper :hay R0 :key)))     ; hay done outside the boxes
(def WJ (w/chore-done W (ev :juniper :hay A :key)))       ; Juniper hayed at 5 PM, before anyone asked
```

Hal seeds are `day + (count log)`:
- K: log 1 → 20765 → `"did hay for Juniper"`
- KU: log 2 → 20766 → `"undid Juniper's hay"`
- KT: log 3 → 20767 → `"did hay for Juniper"`

Clock times at OFF: R0 → 8:03 PM, R0+12h → 8:03 AM, T1 → 8:16 PM, T1+3 min → 8:19 PM.

#### Requests in the world

**t01-asks-both-mirrors**
- `(w/asks W :juniper R0)` → `[:hay]`; `(w/asks W :flora R0)` → `[:feed]`
- `(w/asks WJ :juniper R0)` → `[]` (hayed at 5 PM and not due till 5 AM); `(w/asks W :rex R0)` → `[]`
- Setup: `X = (w/load-estate (pr-str {:mirrors [{:id :x :state {} :chores [{:id :c :every "1h"} {:id :d :every "1h"}] :rules [{:when [:due? :c] :sign "s"} {:when [:due? :d] :say :missing} {:when [:due? :c] :say "c1"} {:when [:due? :c] :say "c2"}]}]}))`
  - `(w/asks X :x A)` → `[:c]` (a sign isn't a request, an unresolved say isn't either, and duplicates collapse)
  - `(w/asks (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1} :rules [{:when [:< :water 5] :say "thirsty"}]}]})) :fern A)` → `[]`

**t02-chore-past**
- `(w/chore-past W :juniper :hay)` → `"fed"`; `(w/chore-past W :flora :feed)` → `"fed"`
- `(w/chore-past (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})) :fern :mist)` → `"done"`
- `(w/chore-past W :rex :feed)` → `"done"`

#### Request lines carry a box

**t03-a-request-line-gets-a-box**
- `(:entries TR)` → `[{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora :asks :feed :done? false :show-at 1794024180000 :show-until 1794024182500} {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false :show-at 1794024182500 :show-until 1794024185000}]`
- `(identical? TR (tk/observe TR W (+ R0 16)))` → true

**t04-box-and-open-box**
- `(tk/box? (tk/entry TR 2))` → true; `(tk/box-chore (tk/entry TR 2))` → `:hay`
- `(tk/box? {:n 1 :kind :hal :text "x"})` → false; `(tk/box-chore {:kind :hal})` → nil
- `(tk/box? {:kind :chore :chore RJ})` → true; `(tk/box-chore {:kind :chore :chore RJ})` → `:hay`
- `(:n (tk/open-box TR :juniper :hay))` → 2; `(:n (tk/open-box TR :flora :feed))` → 1
- `(tk/open-box TR :flora :hay)` → nil; `(tk/open-box (:talk K) :juniper :hay)` → nil (ticked)
- `(:n (tk/open-box (:talk KU) :juniper :hay))` → 2 (unticked again: it's the todo)

**t05-link-chore**
- Setup: `L = (peek (:log (w/chore-done W (ev :juniper :hay T1 :click))))`
- `(tk/link-chore TR L "Juniper" T1)` → `(tk/set-done TR 2 true RJ)`, which has 2 entries and entry 2 with `:done? true :chore RJ`
- `(tk/link-chore tk/empty-talk L "Juniper" T1)` → `= (tk/add-chore tk/empty-talk L "Juniper" T1)` (nothing asked, so it gets its own line)
- `(tk/link-chore TR {:result :counted} "Juniper" T1)` → `= TR`

#### Click, tick, untick

**t06-click-ticks-the-request**
- `(count (:entries (:talk K)))` → 2 (no separate "Juniper's hay" line)
- `(en K 2)` → `(assoc (tk/entry TR 2) :done? true :chore RJ)`
- `(:done? (en K 1))` → false (Flora is still asking)
- `(:text (:hal K))` → `"did hay for Juniper"`; `(st K :juniper)` → `{:source :sample :bales 5 :last-hayed 1794024960000}`
- `(texts (:talk KO))` → `["I'm hungry" "Hay, please?" "did hay for Juniper"]`; `(:done? (en KO 2))` → true

**t07-a-chore-nobody-asked-for-gets-its-own-line**
- `TJ = (tk/observe tk/empty-talk WJ R0)`; `(texts TJ)` → `["I'm hungry"]`
- `CJ = (ch/click (assoc WJ :talk TJ) H :juniper (+ A H12) OFF)`:
  - `(en CJ 2)` → `{:n 2 :who "Juniper" :text "Juniper's hay" :at 1794056400000 :kind :chore :mirror :juniper :chore {:id "juniper/hay@1794056400000" :mirror :juniper :chore :hay :at 1794056400000 :prev-at 1794013200000 :consumed [:bales 1]} :done? true}`
  - `(:text (:hal CJ))` → `"Juniper: hay done"` (log 2, seed 20766)
  - `(:bales (st CJ :juniper))` → 4

**t08-ticking-an-open-request-does-the-chore** (both mirrors, `:via :chat`)
- `TJ2 = (ch/tick WR H 2 T1 OFF)`:
  - `(peek (:log TJ2))` → `{:event :chore-done :mirror :juniper :chore :hay :at T1 :via :chat :who :me :result :counted :id "juniper/hay@1794024960000" :prev-at nil :consumed [:bales 1]}`
  - `(en TJ2 2)` → `(assoc (tk/entry TR 2) :done? true :chore RJ)`; `(count (:entries (:talk TJ2)))` → 2
  - `(:text (:hal TJ2))` → `"did hay for Juniper"`; `(:bales (st TJ2 :juniper))` → 5
- `TF = (ch/tick WR H 1 T1 OFF)`:
  - `(:chore (en TF 1))` → `{:id "flora/feed@1794024960000" :mirror :flora :chore :feed :at T1 :prev-at nil :consumed [:stock 1]}`
  - `(:stock (st TF :flora))` → 14; `(:text (:hal TF))` → `"did feed for Flora"`

**t09-ticking-a-request-that's-not-due**
- `TN = (ch/tick WN H 2 T1 OFF)`:
  - `(:hal TN)` → `{:event :chore-not-due :text "last 8:03 PM, next 8:03 AM" :until 1794024966000}`
  - `(:done? (en TN 2))` → false; `(count (:log TN))` → 1

**t10-untick-undoes-and-the-bubble-isn't-relogged**
- `(st KU :juniper)` → `{:source :sample :bales 6 :last-hayed nil}`
- `(:text (:hal KU))` → `"undid Juniper's hay"`
- `(en KU 2)` → `(assoc (en KO 2) :done? false)` (the record is kept)
- `(get-in (:talk KU) [:last-say :juniper])` → `"Hay, please?"`
- `(w/markers KU :juniper (+ T1 120000))` → `[{:say "Hay, please?"}]` (the bubble is back)
- `(texts (tk/observe (:talk KU) KU (+ T1 120000)))` → `["I'm hungry" "Hay, please?" "did hay for Juniper" "undid Juniper's hay"]` (no second "Hay, please?")

**t11-tick-again-redoes**
- `(peek (:log KT))` has `:via :tick`, `:result :counted` and `:id "juniper/hay@1794025140000"`
- `(:chore (en KT 2))` → `{:id "juniper/hay@1794025140000" :mirror :juniper :chore :hay :at 1794025140000 :prev-at nil :consumed [:bales 1]}`; `(:done? (en KT 2))` → true
- `(st KT :juniper)` → `{:source :sample :bales 5 :last-hayed 1794025140000}`; `(:text (:hal KT))` → `"did hay for Juniper"`

**t12-guards**
- `(ch/untick WR H 2 T1)` → `= WR` (an open request has nothing to undo)
- `(ch/tick K H 2 (+ T1 1000) OFF)` → `= K` (already ticked)
- `(ch/untick KO H 3 T1)` → `= KO` and `(ch/tick KO H 3 T1 OFF)` → `= KO` (entry 3 is Hal's line, not a box)
- `(ch/tick WR H 9 T1 OFF)` → `= WR`

#### Which lines keep their box

**t13-a-request-no-longer-owed-loses-its-box**
- `TS = (tk/observe TR WN (+ R0 1000))`
- `(tk/entry TS 2)` → `{:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :show-at 1794024182500 :show-until 1794024185000}`
- `(tk/entry TS 1)` → `(tk/entry TR 1)`; `(count (:entries TS))` → 2
- `(:done? (en KO 2))` → true (a request a chore answered is never stripped)

**t14-asking-again-gets-a-new-box-and-the-next-chore-ticks-it**
- `KN = (tk/observe (:talk KO) KO NEXT)`:
  - `(count (:entries KN))` → 4
  - `(tk/entry KN 4)` has `:text "Hay, please?"`, `:at NEXT`, `:asks :hay` and `:done? false`
  - `(:done? (tk/entry KN 2))` → true
- `CN = (ch/click (assoc KO :talk KN) H :juniper NEXT OFF)`:
  - `(:chore (tk/entry (:talk CN) 4))` → `{:id "juniper/hay@1794096000000" :mirror :juniper :chore :hay :at NEXT :prev-at T1 :consumed [:bales 1]}`
  - `(:done? (tk/entry (:talk CN) 4))` → true; `(count (:entries (:talk CN)))` → 4
  - `(:text (:hal CN))` → `"did hay for Juniper"` (log 2, day 20765 → seed 20767)

**t15-a-new-request-replaces-a-stale-one**
- Setup: `WL = (assoc W :lines {:mirrors {:flora {:hungry ["h0" "h1" "h2" "h3" "h4"]} :juniper {:hungry ["hay0" "hay1" "hay2"]}}})`, `TL = (tk/observe tk/empty-talk WL R0)`, `TL2 = (tk/observe TL WL NEXT)`
- `(texts TL)` → `["h1" "hay1"]`
- `(texts TL2)` → `["h1" "hay1" "h2" "hay2"]`
- `(:asks (tk/entry TL2 1))` → nil; `(:asks (tk/entry TL2 2))` → nil; `(:asks (tk/entry TL2 3))` → `:feed`; `(:asks (tk/entry TL2 4))` → `:hay`
- `(:n (tk/open-box TL2 :flora :feed))` → 3

#### Labels and state helpers

**t16-box-label**
- `(tk/box-label (tk/entry TR 2) OFF "fed")` → `"Juniper · Hay, please?"`
- `(tk/box-label (en K 2) OFF "fed")` → `"Juniper · Hay, please? · fed 8:16 PM"`
- `(tk/box-label (en KU 2) OFF "fed")` → `"Juniper · Hay, please?"`
- `(tk/box-label (en KT 2) OFF "fed")` → `"Juniper · Hay, please? · fed 8:19 PM"`
- `(tk/box-label {:n 1 :who "Juniper" :text "Juniper's hay" :kind :chore :chore RJ :done? true} OFF "fed")` → `"Juniper's hay · 8:16 PM"`
- `(tk/box-label (en KO 3) OFF "fed")` → nil

**t17-set-done-on-requests**
- `(:done? (tk/entry (tk/set-done TR 2 true RJ) 2))` → true; `(:chore (tk/entry (tk/set-done TR 2 true RJ) 2))` → `RJ`
- `(tk/entry (tk/set-done (:talk K) 2 false nil) 2)` → `(assoc (en K 2) :done? false)`
- `(identical? (:talk KO) (tk/set-done (:talk KO) 3 true RJ))` → true

#### History across reloads

**t18-requests-survive-a-reload**
- `RK = (tk/restore (tk/save-text (:talk K)))`:
  - `(:entries RK)` → `[{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora :asks :feed :done? false} {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? true :chore RJ}]`
- `WRL = (-> W (o/apply-play (o/play-of KO W)) (assoc :talk (tk/restore (tk/save-text (:talk KO)))))` and `UR = (ch/untick WRL H 2 (+ T1 120000))`:
  - `(st UR :juniper)` → `{:source :sample :bales 6 :last-hayed nil}`
  - `(:text (:hal UR))` → `"took back hay"` (a fresh log: seed 20765)
  - `(:done? (en UR 2))` → false

**t19-restore-cleans-request-fields**
- Setup: `rq (fn [m] (pr-str {:v 1 :n 1 :seen 1 :entries [(merge {:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper} m)]}))` and `one (fn [m] (first (:entries (tk/restore (rq m)))))`
- `(one {:asks :hay :done? true})` → `{… :asks :hay :done? false}` (no record, so it can't be ticked)
- `(one {:asks :hay :done? "yes" :chore RJ})` → `{… :asks :hay :done? false :chore RJ}`
- `(one {:asks :hay :done? true :chore {:id ""}})` → `{… :asks :hay :done? false}`
- `(one {:asks "hay" :done? true :chore RJ})` → `{:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper}` (plain)
- `(one {:asks :hay :done? true :chore RJ})` → `{… :asks :hay :done? true :chore RJ}`

**t20-repeated-requests-both-stay**
- `(mapv :n (:entries (tk/restore (pr-str {:v 1 :n 2 :seen 2 :entries [{:n 1 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? true :chore RJ} {:n 2 :who "Juniper" :text "Hay, please?" :at (+ R0 60000) :kind :creature :mirror :juniper :asks :hay :done? false}]}))))` → `[1 2]`

**t21-an-old-history-gets-a-box-from-the-current-state**
- Setup: `OLD = (pr-str {:v 1 :n 2 :seen 2 :entries [{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora} {:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper}]})` and `RT = (tk/restore OLD)`
- `(:asks (tk/entry RT 1))` → nil
- `AD = (tk/observe RT W T1)`:
  - `(count (:entries AD))` → 2
  - `(tk/entry AD 1)` → `{:n 1 :who "Flora" :text "I'm hungry" :at R0 :kind :creature :mirror :flora :asks :feed :done? false}`
  - `(tk/entry AD 2)` → `{:n 2 :who "Juniper" :text "Hay, please?" :at R0 :kind :creature :mirror :juniper :asks :hay :done? false}`
  - `(identical? AD (tk/observe AD W (+ T1 16)))` → true
- `(ch/click (assoc W :talk AD) H :juniper T1 OFF)` gives entry 2 `:done? true :chore RJ` and 2 entries

**t22-an-old-history-gets-no-box-when-the-line-isn't-standing**
- `AJ = (tk/observe RT WJ T1)`: Juniper isn't asking now
  - `(:asks (tk/entry AJ 2))` → nil; `(:asks (tk/entry AJ 1))` → `:feed`; `(count (:entries AJ))` → 2
- `OLD2 = (pr-str {:v 1 :n 1 :seen 1 :entries [{:n 1 :who "Juniper" :text "Feels like hay time" :at R0 :kind :creature :mirror :juniper}]})` and `A2 = (tk/observe (tk/restore OLD2) W T1)`:
  - `(:asks (tk/entry A2 1))` → nil
  - `(texts A2)` → `["Feels like hay time" "I'm hungry" "Hay, please?"]`
  - `(:asks (tk/entry A2 3))` → `:hay`

#### The live flow end to end

**t23-feed-him-again-is-untick-then-tick**
- Click: `(tk/box-label (en KO 2) OFF "fed")` → `"Juniper · Hay, please? · fed 8:16 PM"`; `(:bales (st KO :juniper))` → 5
- Untick: `(w/markers KU :juniper (+ T1 120000))` → `[{:say "Hay, please?"}]`; `(:bales (st KU :juniper))` → 6; `(tk/box-label (en KU 2) OFF "fed")` → `"Juniper · Hay, please?"`
- Tick: `(:bales (st KT :juniper))` → 5; `(w/markers KT :juniper (+ T1 180000))` → `[]`
- `(count (:entries (:talk KT)))` → 3 (Flora's request, Juniper's request and Hal's first line; the click, untick and tick added no Juniper lines)

**Totals:** 23 new tests in `requests_test`. The 179 existing tests remain after E1–E2. That makes **202** tests.

## 7. Live check (Hal)

- **At start.** 💭 shows `8:03 PM ☐ Juniper · Feels like hay time` (today's variant).
- **Click Juniper.** The box ticks: `☑ Juniper · Feels like hay time · fed 8:16 PM`. Hal's line follows, and no separate "Juniper's hay" line appears.
- **Untick.** The bubble comes back, the bales go up by one, and Hal says a `:chore-undone` line. No duplicate request line appears.
- **Tick.** Fed again.
- **Ticking Flora's open "I'm hungry" box** feeds her (`:via :chat`).
- **Old 💭 histories** show a box only on each creature's standing line.

## 8. Not in this round

- Auto-ticking a request that Hal's state file satisfied: it simply loses its box.
- Requests from rules other than `:due?`.
- Write-back to Hal's file.
