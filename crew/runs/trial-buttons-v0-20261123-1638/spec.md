# Spec: trial buttons v0 (TASKS T-14)

Quest: `.crew/quests/trial-buttons-v0.md`. Start commit `c06b0f2`.
A trial build (status `:approved`) gets a small card above it with two labelled buttons,
**Make it real** (today's second click, guarded against double posts) and **Undo** (back to the
blue ghost). The card follows the item on screen and hides when the item is off screen or behind
the camera. The quest's decisions stand; this spec fixes the names, shapes and numbers.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/blueprint.cljs`, `src/pm/pick.cljs`, `src/pm/three.cljs`, `src/pm/scene.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs`; `resources/estate.edn` **only the `:blueprint-text` map** (§2.1); in the run dir: `builder-notes.md` | `test/`, `public/index.html` (the card's CSS is injected from `pm.ui`), the rest of `resources/`, `docs/`, `TASKS.md`, `state/` |
| **tester** (le-testeur) | `test/pm/trial_card_test.cljs` (new) | everything else |
| **le-docteur** | `TASKS.md` (new T-14 row, done), `docs/GDD.md` §2.1 Approve row — wording in §8, applied after the gate passes | code, tests, `resources/`, `state/` |

Builder and tester work in parallel from this spec only. Everything tested is pure.

---

## 2. Data

### 2.1 `resources/estate.edn` `:blueprint-text` (the only change to the file)

```clojure
:blueprint-text {:hint      "click to build · right-click to dismiss"                ; unchanged
                 :trial     "trial: try it out"                                      ; was "trial · click to make it real · right-click to undo": the buttons say it now
                 :make-real "Make it real"                                           ; the trial card's first button
                 :undo      "Undo"                                                   ; the trial card's second button: back to the ghost
                 :saving    "Saving…"                                                ; the first button while the bridge records it (disabled)
                 :real      "Made real. It stays."                                   ; unchanged
                 :no-bridge "Start the bridge (bb crew-bridge) to make it real."     ; unchanged
                 :steep     "the ground's too steep here, so it won't build; ask for a flatter spot"   ; unchanged
                 :failed    "that one won't build, so the estate stays as it was"}  ; unchanged
```
(`…` is the single character U+2026.) Keep the end-of-line comments style.

### 2.2 New world key `:trial-order`

A vector of blueprint ids in the order they were last approved (made a trial), each id at most
once. Set only by `approve-blueprint`; never cached or persisted (a reload clears trials, as
today). Stale ids (since made real, undone or rejected) may stay in it; the readers filter by
status.

### 2.3 Card data (`trial-card`'s return)

```clojure
{:id :bench-by-creek
 :buttons [{:action :real :label "Make it real" :disabled? false}
           {:action :undo :label "Undo"         :disabled? false}]}
```

### 2.4 Screen placement (`screen-of`'s return)

```clojure
{:x 400 :y 300 :visible? true}   ; CSS px inside the canvas: the card's bottom-centre point
```

---

## 3. Pure functions

### 3.1 `pm.blueprint`

**`approve-blueprint [world id]`** — changed: as today, plus when `id` is the `:id` of some entry
in `(:blueprints world)`, `:trial-order` becomes `(conj (vec (remove #{id} (:trial-order world))) id)`.
Unknown `id` → `world` unchanged (no `:trial-order` key added). Statuses are set exactly as today.

**`unapprove [world id]`** → world. New.
If `(status world id)` is `:approved`, that blueprint's `:status` becomes `:pending` (via the
existing `set-status`; nothing else on the entry changes, `:trial-order` untouched). Otherwise
return `world` itself (**identical**): pending, real, rejected and unknown ids are no-ops. Docstring:
"Undo on a trial build (TASKS T-14): back to its blue ghost, so it can be tried again later. Not a
reject."

**`card-owner [world]`** → id or nil. New.
`(or (last (filter #(= :approved (status world %)) (:trial-order world)))
     (:id (last (filter #(= :approved (:status %)) (all world)))))`
— the most recent trial; the fallback covers a world whose `:approved` entries came from data with
no `:trial-order`. nil when no blueprint is `:approved`.

**`trial-card [world saving]`** → card (§2.3) or nil. New.
`saving` is the id whose make-real request is in flight, or nil.
- nil when `(card-owner world)` is nil, or any of `:make-real`, `:undo`, `:saving` in
  `(:blueprint-text world)` is missing, not a string, or blank (no card without its words).
- else `{:id owner :buttons [{:action :real :label (if (= saving owner) saving-text make-real-text) :disabled? (some? saving)}
                              {:action :undo :label undo-text :disabled? (some? saving)}]}`
  (both buttons are disabled while **any** make-real request is in flight, so nothing can post
  twice or undo a build the bridge is recording).

**`card-lift-m`** — `(def card-lift-m 0.3)`: metres above the item's bounding-box top.

### 3.2 `pm.pick`

**`screen-of [ndc size]`** → `{:x :y :visible?}`. New. No DOM, no `js/Date`.
`ndc` = `[x y z]` (a point projected through the camera), `size` = `[w h]` canvas px.
- If `ndc` isn't 3 finite numbers, or `w`/`h` isn't a finite positive number →
  `{:x 0 :y 0 :visible? false}`.
- else `:x (* (/ (+ x 1) 2) w)`, `:y (* (/ (- 1 y) 2) h)` (no rounding), and
  `:visible? (and (<= -1 x 1) (<= -1 y 1) (<= z 1))` — off an edge is `|x|>1` or `|y|>1`, behind
  the camera is `z>1`; the edges themselves are visible.

---

## 4. Impure wiring (hand-tested; not unit-tested)

### 4.1 `pm.three` (new fns, interop only here)
- `(top-center [obj])` → `[cx max-y cz]` of obj's world bounding box (`Box3.setFromObject`, after
  `updateMatrixWorld`), or nil when not finite / on error (like `top-y`).
- `(project-ndc [camera [x y z]])` → `[nx ny nz]` (`Vector3.project(camera)`), or nil.
- `(unlight-glows! [obj])` → obj: for every descendant with `userData.light`, remove that light
  from its parent, `dispose` it if it can, and delete `userData.light` (reverses `light-glows!`, so
  an undone lamp stops shining and `sync-glows!` stops repainting its bulb).

### 4.2 `pm.scene`
- `sync-blueprints!` status loop gets a `:pending` branch: when the previous status was
  `:approved` (an Undo), find the group `(pr-str k)` and run `(t/unlight-glows! g)` then
  `(t/ghost! g)` (`ghost!` clones the materials and records their colours again, so a later trial
  can `unghost!` it). A `:pending` with no previous `:approved` does nothing (as today). The sign
  sprite stays; its text flips from the trial line to the hint through the existing
  `label-changes` path (ghost-plan vs built-plan text).
- `(css-color [kw])` → `"#rrggbb"` for a palette key (`:sign-paper` → `"#f6ead0"`, `:sign-ink` →
  `"#4a3420"`), so the card uses the in-world plate's colours without hex literals in feature code.

### 4.3 `pm.ui` — `(set-trial-card! card pos colors on-action)`
- `card` from `trial-card` or nil; `pos` = `{:left px :top px :visible? bool}` (page px);
  `colors` = `{:paper "#…" :ink "#…"}`; `on-action` = `(fn [action id])`.
- One element, created on first use and appended to `document.body`: `div#pm-trial-card`.
  Children rebuilt only when `(pr-str card)` changes (this runs every frame); position set every
  call. `card` nil or `(not (:visible? pos))` → `display:none`.
- Buttons, in order: `button.pm-btn.trial-real` (`data-action="real"`) and
  `button.pm-btn.trial-undo` (`data-action="undo"`); `textContent` = `:label`; `disabled` =
  `:disabled?`. Click → `(on-action action (:id card))` unless disabled.
- On the card, `stopPropagation` for `pointerdown pointerup mousedown mouseup click dblclick
  contextmenu wheel` (core's pick listens for `pointerup` on `window`, the kit's controls on the
  canvas), so no pan and no pick on the item behind.
- Styles injected once (`style#pm-trial-card-style`, after `construct-css!` so `.pm-btn` exists):
  ```css
  #pm-trial-card{position:fixed;z-index:150;transform:translate(-50%,-100%);display:flex;gap:8px;
    padding:8px;border-radius:12px;border:2px solid <ink>;background:<paper>}
  #pm-trial-card .pm-btn{box-sizing:border-box;height:40px;padding:0 14px;border-radius:10px;
    font:600 14px/1 system-ui,sans-serif;white-space:nowrap;animation:none}
  #pm-trial-card .pm-btn:disabled{opacity:.5;cursor:default}
  ```
  (40 px high, 10 px corners: the phone row's button box; 8 px between buttons. The `<ink>`/`<paper>`
  values come from `colors`; set them on the element's style, not as literals in the CSS string.)

### 4.4 `pm.core`
1. `(defonce ^:private !saving (atom nil))` — the id whose make-real request is in flight.
2. `make-real!` takes a second arg `done` (0-arity fn) and calls it exactly once in every branch
   (no bridge: after `say!`; bridge: in the post callback, ok or not). Words and bridge calls unchanged.
3. `(real-click! [id])`: only when `@!saving` is nil and `(bp/status @!world id)` is `:approved`:
   `(reset! !saving id)`, `(make-real! id #(reset! !saving nil))`. `on-blueprint-click!`'s
   `:approved` branch calls `real-click!` (so clicking the item and the button are one path).
4. `(undo-trial! [id])`: when `@!saving` is nil, `(swap! !world bp/unapprove id)`.
5. `(trial-card! [h])`, called each frame right after `scene/update!` when `(kit/handle)`:
   `card (bp/trial-card @!world @!saving)`; when card, `g (t/by-name (scene/mounted-root) (pr-str [:blueprint (:id card)]))`,
   `[x y z] (t/top-center g)`, `ndc (t/project-ndc (.-camera h) [x (+ y bp/card-lift-m) z])`,
   `rect` = the renderer canvas's `getBoundingClientRect`, `p (pick/screen-of ndc [(.-width rect) (.-height rect)])`;
   then `(ui/set-trial-card! card {:left (+ (.-left rect) (:x p)) :top (+ (.-top rect) (:y p)) :visible? (:visible? p)} colors on-action)`
   with `on-action` dispatching `:real` → `real-click!`, `:undo` → `undo-trial!`. Missing group/top →
   `:visible? false`. No kit (scaffold) → the card stays hidden.
6. Right-click still calls `bp/reject-blueprint` (unchanged). The demo build has no bridge, so Make it
   real gives the existing `:no-bridge` line there (unchanged).

---

## 5. Test cases — `test/pm/trial_card_test.cljs` (hand-checked)

```clojure
(ns pm.trial-card-test
  (:require [cljs.test :refer [deftest is]]
            [pm.blueprint :as bp]
            [pm.pick :as pk]
            [pm.world :as w]))

(def TXT {:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"})
(def B1  {:id :bench   :at [1 2 3] :summary "proposed: a bench"})
(def B2  {:id :planter :at [0 0 0] :summary "proposed: a planter"})   ; the second blueprint: nothing is specific to one
(def W   {:blueprint-text TXT :blueprints [B1 B2]})
(def ap  bp/approve-blueprint)
(def BTN (fn [real-label disabled?] [{:action :real :label real-label :disabled? disabled?}
                                     {:action :undo :label "Undo" :disabled? disabled?}]))
```

### `unapprove`
| # | Call | Expected |
|---|---|---|
| U1 | `(-> W (ap :bench) (bp/unapprove :bench))` | `(bp/status w :bench)` = `:pending`; `(mapv :id (bp/pending w))` = `[:bench :planter]` |
| U2 | `(bp/unapprove W :bench)` (pending) | `identical?` to `W` |
| U3 | `w = (bp/apply-real-builds W {:real [:bench]})`; `(bp/unapprove w :bench)` | `identical?` to `w`; status still `:real` |
| U4 | `w = (bp/reject-blueprint W :bench)`; `(bp/unapprove w :bench)` | `identical?` to `w`; status still `:rejected` |
| U5 | `(bp/unapprove W :gazebo)` | `identical?` to `W` |
| U6 | second blueprint: `(-> W (ap :bench) (ap :planter) (bp/unapprove :planter))` | `(mapv :status (bp/all w))` = `[:approved :pending]` |
| U7 | try again: `(-> W (ap :bench) (bp/unapprove :bench) (ap :bench))` | `(bp/status w :bench)` = `:approved`; `(bp/card-owner w)` = `:bench` |
| U8 | signs: `(:text (first (bp/ghost-plan (-> (assoc-in W [:blueprint-text :hint] "click to build") (ap :bench) (bp/unapprove :bench)))))`; `(:text (first (bp/built-plan (ap W :bench))))` | `"proposed: a bench\nclick to build"`; `"proposed: a bench\ntrial: try it out"` |

### `:trial-order`
| # | Call | Expected |
|---|---|---|
| T1 | `(:trial-order (ap W :bench))` | `[:bench]` |
| T2 | `(:trial-order (-> W (ap :bench) (ap :planter) (ap :bench)))` | `[:planter :bench]` |
| T3 | `(ap W :gazebo)` | `(= W r)` true, `(contains? r :trial-order)` false |

### `card-owner`
| # | Call | Expected |
|---|---|---|
| O1 | `(bp/card-owner W)` | `nil` |
| O2 | `(bp/card-owner (ap W :bench))` | `:bench` |
| O3 | `(bp/card-owner (-> W (ap :bench) (ap :planter)))` | `:planter` |
| O4 | latest made real: `(-> W (ap :bench) (ap :planter) (bp/make-real :planter))` | `:bench` |
| O5 | latest undone: `(-> W (ap :bench) (ap :planter) (bp/unapprove :planter))` | `:bench` |
| O6 | latest rejected: `(-> W (ap :bench) (ap :planter) (bp/reject-blueprint :planter))` | `:bench` |
| O7 | only trial made real: `(-> W (ap :bench) (bp/make-real :bench))` | `nil` |
| O8 | no `:trial-order`: `(bp/card-owner {:blueprints [(assoc B1 :status :approved) (assoc B2 :status :approved)]})` | `:planter` |
| O9 | re-tried moves to the end: `(-> W (ap :bench) (ap :planter) (bp/unapprove :bench) (ap :bench))` | `:bench` |

### `trial-card`
| # | Call | Expected |
|---|---|---|
| C1 | `(bp/trial-card W nil)` | `nil` |
| C2 | `(bp/trial-card (ap W :bench) nil)` | `{:id :bench :buttons (BTN "Make it real" false)}` |
| C3 | saving this one: `(bp/trial-card (ap W :bench) :bench)` | `{:id :bench :buttons (BTN "Saving…" true)}` |
| C4 | saving an older one: `(bp/trial-card (-> W (ap :bench) (ap :planter)) :bench)` | `{:id :planter :buttons (BTN "Make it real" true)}` |
| C5 | words missing: `(bp/trial-card (ap (update W :blueprint-text dissoc :undo) :bench) nil)`; `(bp/trial-card (ap (assoc-in W [:blueprint-text :make-real] " ") :bench) nil)`; `(bp/trial-card (ap (update W :blueprint-text dissoc :saving) :bench) nil)` | `nil` each |
| C6 | second blueprint: `(bp/trial-card (ap W :planter) nil)` | `{:id :planter :buttons (BTN "Make it real" false)}` |

### `screen-of` (`pk/screen-of`)
| # | Call | Expected |
|---|---|---|
| P1 | `(pk/screen-of [0 0 0.5] [800 600])` | `{:x 400 :y 300 :visible? true}` |
| P2 | `(pk/screen-of [0.5 -0.5 0.9] [800 600])` | `{:x 600 :y 450 :visible? true}` |
| P3 | edges are on screen: `(pk/screen-of [-1 1 0] [1000 500])`, `(pk/screen-of [1 -1 1] [1000 500])` | `{:x 0 :y 0 :visible? true}`, `{:x 1000 :y 500 :visible? true}` |
| P4 | off the right / left: `[1.2 0 0.5]`, `[-1.01 0 0.5]` with `[800 600]` | `:visible? false` both |
| P5 | off the top / bottom: `[0 1.5 0.5]`, `[0 -1.2 0.5]` with `[800 600]` | `:visible? false` both |
| P6 | behind the camera: `(pk/screen-of [0 0 1.2] [800 600])` | `{:x 400 :y 300 :visible? false}` |
| P7 | bad point: `(pk/screen-of nil [800 600])`, `(pk/screen-of [js/NaN 0 0] [800 600])`, `(pk/screen-of [0 0] [800 600])` | `{:x 0 :y 0 :visible? false}` each |
| P8 | bad size: `(pk/screen-of [0 0 0] [0 600])`, `(pk/screen-of [0 0 0] nil)` | `{:x 0 :y 0 :visible? false}` each |

### The estate's words
| # | Call | Expected |
|---|---|---|
| E1 | `t = (:blueprint-text (w/load-estate w/estate-edn))`; for each of `:trial :make-real :undo :saving` | a string, not blank |
| E2 | `(select-keys t [:trial :make-real :undo :saving])` | `{:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"}` |
| E3 | the shipped estate gets a card: `(bp/trial-card (ap (w/load-estate w/estate-edn) :bench-by-mailbox) nil)`; `bp/card-lift-m` | `{:id :bench-by-mailbox :buttons (BTN "Make it real" false)}`; `0.3` |

Total: 37 cases (U8 T3 O9 C6 P8 E3). Existing tests stay green unchanged (`t23` uses its own
`{:trial "trial"}`; `m4` compares the blueprint entry, which `approve-blueprint` doesn't change).

---

## 6. Builder's note (`crew/runs/trial-buttons-v0-20261123-1638/builder-notes.md`)

What was built, what was checked by hand, and what Hal should click when running headless:
- the card: `#pm-trial-card` (hidden: `display:none`)
- Make it real: `#pm-trial-card .trial-real` (`[data-action="real"]`; reads `Saving…` and is
  `:disabled` while the request is in flight)
- Undo: `#pm-trial-card .trial-undo` (`[data-action="undo"]`)
- a ghost to click first: the group named `[:blueprint :bench-by-mailbox]` in the kit's scene
  (via the existing pick), then the card appears above it; after Undo the item is blue again and
  the card is gone; Make it real without the bridge shows the `:no-bridge` caption.

---

## 7. Out of scope (unchanged)
Buttons on a pending ghost; the morning tray; a reject button; trials across reloads; the demo
build's bridge.

---

## 8. Wording for le-docteur (after the gate passes)

- **`TASKS.md`**: a new row directly under T-13, in the done style of T-11:
  > - [x] Nov 23 (crew round trial-buttons-v0) T-14 A trial build gets labelled buttons (Michael, Nov 23: "i didnt realize u needed to click it twice"; his rule: every action is a labelled button). Click a blue ghost and it builds as a trial with a small card above it: **Make it real** (records it through the bridge; reads "Saving…" while it does, so it can't post twice) and **Undo** (back to the blue ghost, to try again later; not a reject). The card follows the item as the camera moves and hides when it's off screen; the trial's sign now just says "trial: try it out". Right-click still dismisses.
- **`docs/GDD.md` §2.1, Approve row**: append after the Nov 23 *Built* note, same style:
  > *Built (Nov 23): a trial build shows a card above it with two labelled buttons, **Make it real** and **Undo** (back to the ghost), instead of a hidden second click.*
