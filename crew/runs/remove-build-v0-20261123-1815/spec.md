# Spec: remove a real build, try-first (TASKS T-15)

Quest: `.crew/quests/remove-build-v0.md`. Start commit `bbee7b1`. Builds on rounds
`live-blueprints-v0` (state/blueprints.edn, `merge-file`, `bb blueprint-add`) and `trial-buttons-v0`
(the trial card, `!saving`, `set-trial-card!`).

Click a real build → the one card shows **Remove…** · **Close**. Remove… → the item becomes a red
**removal ghost** whose sign reads "will be removed", and the card shows **Remove it** · **Keep it**.
Keep it puts it back exactly as it was. Remove it posts `/real` `remove`; on success the build is
`:rejected` (vanishes) and listed in `state/builds.edn` `:removed`, so it stays gone on every reload.
No bridge or a refusal: back to real, the twin says why. The quest's decisions stand; this spec
fixes names, shapes and numbers.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/blueprint.cljs`, `src/pm/blueprint_file.cljc`, `src/pm/bridge.cljc`, `src/pm/crew.cljs`, `src/pm/three.cljs`, `src/pm/scene.cljs`, `src/pm/ui.cljs`, `src/pm/core.cljs`; `scripts/crew_bridge.clj`, `scripts/blueprint_add.clj`; `resources/estate.edn` **only the `:blueprint-text` map** (§2.1); in the run dir: `builder-notes.md` | `test/`, `public/`, the rest of `resources/`, `docs/`, `TASKS.md`, `state/` (hand tests use `PM_BUILDS_FILE` / `PM_BLUEPRINTS_FILE` temp files) |
| **tester** (le-testeur) | `test/pm/remove_build_test.cljs` (new) | everything else; all cases are pure, no files written |
| **le-docteur** | `TASKS.md` (new T-15 row), `docs/GDD.md` §2.1 Approve row — wording in §8, applied after the gate passes | code, tests, `resources/`, `state/` |

Builder and tester work in parallel from this spec only. Existing tests must stay green unchanged.

---

## 2. Data

### 2.1 `resources/estate.edn` `:blueprint-text` — add these keys (existing ones unchanged)

```clojure
:remove           "Remove…"                                          ; a real build's card: preview the removal
:close            "Close"                                            ; a real build's card: close it, nothing changes
:remove-it        "Remove it"                                        ; the removal preview's card: record it
:keep             "Keep it"                                          ; the removal preview's card: back as it was
:removing         "will be removed"                                  ; the removal ghost's sign, second line
:removing-wait    "Removing…"                                        ; Remove it while the bridge records it (disabled)
:removed          "Removed. It's gone for good."                     ; twin's caption once it's recorded
:no-bridge-remove "Start the bridge (bb crew-bridge) to remove it."  ; no bridge (or refused): nothing changes
```
(`…` is U+2026, as in `:saving`.)

### 2.2 Statuses and world keys

- `pm.blueprint/statuses` = `#{:pending :approved :real :removing :rejected}`. `:removing` = a real
  build being previewed for removal (only reachable from `:real`, only leaves to `:real` or `:rejected`).
- New world key `:card-open` — id of the real (or removing) build whose card the player opened, or
  absent. Not cached or persisted (a reload closes it, and a reload never shows `:removing`: the
  status isn't saved, the builds file says `:real`).

### 2.3 `state/builds.edn`

```clojure
{:as-of 1763950000000 :real [:bench-by-creek] :removed [:lamp-post-by-canal]}
```
`:real` and `:removed`: sorted, distinct keyword vectors. An id is never in both after the bridge
writes it; if a hand-edited file has both, `:removed` wins. `:removed` appears once anything has
been removed and is kept from then on; a file that never had a removal keeps today's
`{:as-of :real}` shape.

### 2.4 Card (generalised T-14 card)

```clojure
{:id :lamp-post-by-canal :mode :real                ; :trial | :real | :removing
 :buttons [{:action :remove :label "Remove…" :disabled? false}
           {:action :close  :label "Close"   :disabled? false}]}
```
Actions by mode: `:trial` → `:real` `:undo` (unchanged); `:real` → `:remove` `:close`;
`:removing` → `:remove-it` `:keep`. The DOM classes follow the existing `trial-<action>` rule.

---

## 3. Pure functions

### 3.1 `pm.blueprint`

Existing fns, changed:
- **`sign-text`**: new first-matching branch `(= :removing (:status bp))` → the `:removing` tail
  (summary + `"\n"` + tail; just the summary when the word is missing/blank, like the others).
- **`built-plan`**: items for statuses `#{:approved :real :removing}`. A `:removing` item is like a
  real one plus `:ghost? true :tint :removal :label :sign :label-at [x (+ y top p/label-lift) z]
  :text (sign-text world bp)`. `:approved`/`:real` items unchanged (a real one still has no label).
- **`drawable-changes`**: drawable set becomes `#{:pending :approved :real :removing}`.
- **`merge-file`**: the "kept even when the file drops it" set becomes `#{:real :approved :removing}`.
- **`apply-real-builds [world builds]`**: `real` = keyword ids in `(:real builds)`, `removed` =
  keyword ids in `(:removed builds)` (`builds` not a map → both empty). Both empty → `world`
  **identical**. Else each blueprint: id ∈ removed → `:rejected`; else id ∈ real → `:real`, except
  one currently `:removing` stays `:removing` (the 5 s poll re-applies this file mid-preview);
  else unchanged.
- Docstrings that say a real build "can't be dismissed" (`make-real`, `apply-real-builds`) now say it
  can be removed only through the card's try-first step (TASKS T-15).

New fns (each returns `world` **identical** when it doesn't apply):

**`(start-removal world id)`**: status `:real` → `:removing`, and `:card-open id`.

**`(keep-it world id)`**: status `:removing` → `:real`; dissoc `:card-open` when it is `id`.
(A previewed removal kept is exactly the world before the preview, see R3.)

**`(finish-removal world id now)`**: status `:removing` → `:rejected`; dissoc `:card-open` when it is
`id`; when `:removed` is a non-blank string, `:hal {:event :blueprint-removed :text <it> :until (+ now c/hal-ms)}`
(`c/hal-ms` = 6000).

**`(fail-removal world id reason now)`**: status `:removing` → `:real`, `:card-open` kept (the card
shows Remove… · Close again, to retry); when `:no-bridge-remove` is a non-blank string,
`:hal {:event :blueprint-remove-refused :text t :until (+ now c/hal-ms)}` with
`t = (if reason (str word " (" reason ")") word)`.

**`(open-card world id)`**: status of `id` is `:real` → first, if another id `o = (:card-open world)`
is `:removing`, set `o` back to `:real`; then `:card-open id`. Otherwise identical.

**`(close-card world)`**: no `:card-open` → identical. Else: if that id is `:removing`, set it back
to `:real`; dissoc `:card-open`.

**`(card-of world)`** → `{:id :mode}` | nil. `o = (:card-open world)`, `s = (status world o)`:
`s = :removing` → `{:id o :mode :removing}`; `s = :real` → `{:id o :mode :real}`; else
`(when-let [t (card-owner world)] {:id t :mode :trial})`; else nil. (An open real card is shown in
front of a standing trial; closing it brings the trial's card back.)

**`(card world saving)`** → card (§2.4) | nil. `saving` = id whose bridge request is in flight, or nil;
`d = (some? saving)`.
- `:trial` → `(some-> (trial-card world saving) (assoc :mode :trial))` (T-14 unchanged; `trial-card` stays).
- `:real` → needs `:remove` `:close` non-blank, else nil:
  `{:id o :mode :real :buttons [{:action :remove :label remove :disabled? d} {:action :close :label close :disabled? d}]}`
- `:removing` → needs `:remove-it` `:keep` `:removing-wait` non-blank, else nil:
  `{:id o :mode :removing :buttons [{:action :remove-it :label (if (= saving o) removing-wait remove-it) :disabled? d} {:action :keep :label keep :disabled? d}]}`

### 3.2 `pm.bridge` (cljc)

- **`validate`** `/real`: actions `#{"build" "mirror" "remove"}`; `"remove"` with a valid `"id"` →
  `{:ok true :action :remove-build :id (keyword id)}` (same id rule and 400s as `"build"`).
- **`(remove-real-build builds id now)`** → file | nil. nil unless `id` ∈ keyword ids of
  `(:real builds)` (an unknown, never-real or already-removed id is refused). Else
  `{:as-of now :real (vec (sort (distinct (remove #{id} real)))) :removed (vec (sort (distinct (conj removed id))))}`.
- **`add-real-build`** (changed): nil also when `id` ∈ `(:removed builds)` (a removed build can't be
  made real again; add it with a new id). Output keeps `:removed` when it is non-empty:
  `(cond-> {:as-of now :real ...} (seq removed) (assoc :removed (vec (sort (distinct removed)))))`.
  Unchanged otherwise (existing `t02` stays green).

### 3.3 `pm.crew`
- **`(remove-build-body bridge id)`** → `{"token" (:token bridge) "action" "remove" "id" (name id)}`.

### 3.4 `pm.blueprint-file` (cljc)
- **`(removed-ids builds)`** → set of keyword ids in `(:removed builds)`; `#{}` for nil/non-map.
- **`add`** gains a 6-arity `(add file estate-ids member bp now removed-ids)`; the 5-arity calls it
  with `#{}` (nil also means `#{}`). Check order: invalid → bad id shape → in the estate →
  **id ∈ removed-ids → `{:refused (str "id " id " was removed; pick a new id")}`** → already in the file.

---

## 4. Impure wiring (hand-tested)

**`scripts/crew_bridge.clj` `make-real!`**: new `:remove-build` case:
`(b/remove-real-build (read-edn builds-file) (:id v) now)` → written with `write-edn!` →
`{:ok true :reason "removed"}`; nil → `{:ok false :reason "not a real build"}`; write failure →
`{:ok false :reason "not saved"}`. `state/blueprints.edn` is not touched.

**`scripts/blueprint_add.clj`**: builds path `(or (System/getenv "PM_BUILDS_FILE") "state/builds.edn")`;
missing or unreadable → `#{}` (never block an add on the bridge's file); pass
`(bf/removed-ids builds)` as the 6th arg. Refusal prints `blueprint-add: id :x was removed; pick a new id`, exit 1.

**`pm.three/ghost!`**: `(ghost! obj)` = `(ghost! obj :proposed)`; `(ghost! obj tint)` with
`(def ghost-tints {:proposed {:fill ghost-fill :glow ghost-glow :edge ghost-edge}
                   :removal  {:fill 0xff5a4a :glow 0xff5a4a :edge 0xffb0a0}})` (unknown tint →
`:proposed`); same opacity (0.3), edges, userData and clone behaviour as today. `unghost!` unchanged.

**`pm.scene`**:
- `mount-item!` passes `(:tint item)` to `ghost!` when `:ghost?`.
- `sync-blueprints!` status loop, new/changed branches:
  - `:removing` (from `:real`): `(t/unlight-glows! g)`, `(t/ghost! g :removal)`; its sign: if no sprite
    in `:labels` for `k` (a build mounted as real has none), build one from the current plan item for
    `k` (`label-opts :sign label-at text`, add to root, store in `:labels` and `:texts`); then
    `set-visible! true` and set its text.
  - `:real` (also from `:removing`, i.e. Keep it / failure): the existing branch (unghost, light
    glows, hide the sign) is exactly right; keep it.
  - `:rejected`: before removing the group, `(t/unlight-glows! g)` (dispose the lamp's light).
- Labels keep their text through the existing `label-changes` path (built-plan gives the removing text).

**`pm.ui/set-trial-card!`**: no structural change (buttons come from `card`); add
`#pm-trial-card .trial-remove-it{min-width:118px}` beside `.trial-real` so "Removing…" doesn't jump.

**`pm.core`**:
1. `trial-card!` uses `(bp/card @!world @!saving)`; `on-action`: `:real` → `real-click!`,
   `:undo` → `undo-trial!`, `:remove` → `(swap! !world bp/start-removal id)`, `:close` →
   `(swap! !world bp/close-card)`, `:remove-it` → `remove-click!`, `:keep` → `(swap! !world bp/keep-it id)`;
   all but `:remove-it`'s in-flight path are no-ops while `@!saving` is set.
2. `on-blueprint-click!` (ignored entirely while `@!saving`): `:pending` → `close-card` then
   `apply-approved!`; `:approved` → `close-card` then `real-click!`; `:real` → `(bp/open-card id)`;
   `:removing` → nothing. Right-click unchanged (`reject-blueprint` is still a no-op on real/removing).
3. `(remove-click! [id])`: only when `@!saving` is nil and status is `:removing`: `(reset! !saving id)`;
   no bridge → `(swap! !world bp/fail-removal id nil now)`, clear; else
   `(post-real! #(crew/remove-build-body % id) (fn [ok? reason] (swap! !world (if ok? #(bp/finish-removal % id now) #(bp/fail-removal % id reason now))) (reset! !saving nil)))`.
   (`now` = `(js/Date.now)` at the callback.)
4. Load order is unchanged: `fetch-builds!` → `apply-real-builds` (now with `:removed`) and the
   blueprint-file merge applies `@!last-builds` too, so a removed estate or file build comes up
   `:rejected` and is never drawn.

---

## 5. Test cases — `test/pm/remove_build_test.cljs` (hand-checked)

```clojure
(ns pm.remove-build-test
  (:require [cljs.test :refer [deftest is]]
            [pm.blueprint :as bp]
            [pm.blueprint-file :as bf]
            [pm.bridge :as b]
            [pm.crew :as crew]
            [pm.world :as w]))

(def TXT {:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"
          :remove "Remove…" :close "Close" :remove-it "Remove it" :keep "Keep it"
          :removing "will be removed" :removing-wait "Removing…"
          :removed "Removed. It's gone for good."
          :no-bridge-remove "Start the bridge (bb crew-bridge) to remove it."})
(def B1 {:id :lamp    :at [1 2 3] :summary "a lamp post"})                       ; an estate build
(def B2 {:id :bench   :at [0 0 0] :summary "a bench" :from-file? true :by :le-testeur}) ; a file build: the second kind
(def B3 {:id :planter :at [5 0 5] :summary "a planter"})                         ; stays pending
(def W  (bp/apply-real-builds {:blueprint-text TXT :blueprints [B1 B2 B3]} {:real [:lamp :bench]}))
(def ap bp/approve-blueprint)
(defn st [w] (mapv :status (bp/all w)))
(def ctx {:token "t" :members #{}})
(defn req [body] {:method "POST" :uri b/real-uri :origin b/allowed-origin :body (assoc body "token" "t")})
(def LOOK  {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]})
(def PL-IN {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path" :look LOOK})
(def E #{:bench-by-mailbox :lamp-post-by-canal})
```
Derived: `(st W)` = `[:real :real :pending]`; W has no `:card-open`.

### Statuses, start-removal, keep-it
| # | Call | Expected |
|---|---|---|
| S1 | `bp/statuses` | `#{:pending :approved :real :removing :rejected}` |
| R1 | `w = (bp/start-removal W :lamp)` | `(st w)` = `[:removing :real :pending]`; `(:card-open w)` = `:lamp` |
| R2 | no-ops: `(bp/start-removal W :planter)`; `(bp/start-removal (ap W :planter) :planter)` on that world; `(bp/start-removal (bp/reject-blueprint W :planter) :planter)` on that world; `(bp/start-removal W :gazebo)`; `w1 = (bp/start-removal W :lamp)`, `(bp/start-removal w1 :lamp)` | each `identical?` to its input world |
| R3 | kept is as it was: `(-> W (bp/start-removal :lamp) (bp/keep-it :lamp))` | `(= W r)` true |
| R4 | keep-it no-ops: `(bp/keep-it W :lamp)` (real), `(bp/keep-it W :planter)`, `(bp/keep-it W :gazebo)` | `identical?` to `W` each |
| R5 | second build: `(bp/status (bp/start-removal W :bench) :bench)`; `(-> W (bp/start-removal :bench) (bp/keep-it :bench))` | `:removing`; `(= W r)` true |

### finish-removal, fail-removal
| # | Call | Expected |
|---|---|---|
| F1 | `w = (bp/finish-removal (bp/start-removal W :lamp) :lamp 1000)` | `(st w)` = `[:rejected :real :pending]`; `(contains? w :card-open)` false; `(:hal w)` = `{:event :blueprint-removed :text "Removed. It's gone for good." :until 7000}` |
| F2 | `(bp/finish-removal W :lamp 1000)` (real, not previewed) | `identical?` to `W` |
| F3 | no words: `(-> W (update :blueprint-text dissoc :removed) (bp/start-removal :lamp) (bp/finish-removal :lamp 1000))` | status `:lamp` = `:rejected`; `(:hal w)` = `nil` |
| F4 | after F1's `w`: `(bp/card-of w)`; `(mapv :key (bp/built-plan w))`; `(bp/drawable-changes #{:lamp :bench :planter} w)` | `nil`; `[[:blueprint :bench]]`; `{:add [] :remove #{}}` |
| X1 | `w = (bp/fail-removal (bp/start-removal W :lamp) :lamp nil 1000)` | `(st w)` = `[:real :real :pending]`; `(:card-open w)` = `:lamp`; `(:hal w)` = `{:event :blueprint-remove-refused :text "Start the bridge (bb crew-bridge) to remove it." :until 7000}`; `(bp/card-of w)` = `{:id :lamp :mode :real}` |
| X2 | `(:text (:hal (bp/fail-removal (bp/start-removal W :bench) :bench "not a real build" 1000)))` | `"Start the bridge (bb crew-bridge) to remove it. (not a real build)"` |
| X3 | `(bp/fail-removal W :lamp nil 1000)` | `identical?` to `W` |

### The card's owner and mode
| # | Call | Expected |
|---|---|---|
| O1 | `(bp/card-of W)` | `nil` |
| O2 | `(bp/card-of (bp/open-card W :lamp))` | `{:id :lamp :mode :real}` |
| O3 | `(bp/open-card W :planter)`, `(bp/open-card W :gazebo)` | `identical?` to `W` both |
| O4 | `(bp/card-of (bp/start-removal W :lamp))` | `{:id :lamp :mode :removing}` |
| O5 | `(bp/card-of (ap W :planter))` | `{:id :planter :mode :trial}` |
| O6 | `(bp/card-of (-> W (ap :planter) (bp/open-card :lamp)))`; same then `bp/close-card` | `{:id :lamp :mode :real}`; `{:id :planter :mode :trial}` |
| O7 | another build clicked mid-preview: `w = (-> W (bp/start-removal :lamp) (bp/open-card :bench))` | `(st w)` = `[:real :real :pending]`; `(bp/card-of w)` = `{:id :bench :mode :real}` |
| O8 | `(-> W (bp/start-removal :lamp) bp/close-card)` | `(= W r)` true |
| O9 | `(bp/close-card W)` | `identical?` to `W` |
| O10 | stale: `(bp/card-of (assoc W :card-open :planter))` | `nil` |

### The card's buttons
| # | Call | Expected |
|---|---|---|
| C1 | `(bp/card W nil)` | `nil` |
| C2 | `(bp/card (bp/open-card W :lamp) nil)` | `{:id :lamp :mode :real :buttons [{:action :remove :label "Remove…" :disabled? false} {:action :close :label "Close" :disabled? false}]}` |
| C3 | `(bp/card (bp/start-removal W :lamp) nil)` | `{:id :lamp :mode :removing :buttons [{:action :remove-it :label "Remove it" :disabled? false} {:action :keep :label "Keep it" :disabled? false}]}` |
| C4 | in flight: `(bp/card (bp/start-removal W :lamp) :lamp)` | `{:id :lamp :mode :removing :buttons [{:action :remove-it :label "Removing…" :disabled? true} {:action :keep :label "Keep it" :disabled? true}]}` |
| C5 | `(bp/card (ap W :planter) nil)` | `{:id :planter :mode :trial :buttons [{:action :real :label "Make it real" :disabled? false} {:action :undo :label "Undo" :disabled? false}]}` |
| C6 | words missing: `(bp/card (bp/open-card (update W :blueprint-text dissoc :close) :lamp) nil)`; `(bp/card (bp/start-removal (update W :blueprint-text dissoc :removing-wait) :lamp) nil)` | `nil` both |
| C7 | second build: `(bp/card (bp/open-card W :bench) nil)` | `{:id :bench :mode :real :buttons [{:action :remove :label "Remove…" :disabled? false} {:action :close :label "Close" :disabled? false}]}` |

### Sign, plans, drawing, merge
| # | Call | Expected |
|---|---|---|
| P1 | `(bp/sign-text W (assoc B1 :status :removing))` | `"a lamp post\nwill be removed"` |
| P2 | `items = (bp/built-plan (bp/start-removal W :lamp))`; `(select-keys (first items) [:key :ghost? :tint :label :text])`; `(second items)` | `{:key [:blueprint :lamp] :ghost? true :tint :removal :label :sign :text "a lamp post\nwill be removed"}`; `(count items)` = 2, second has `:key [:blueprint :bench]`, no `:ghost?`, no `:label` |
| P3 | `(mapv :key (bp/ghost-plan (bp/start-removal W :lamp)))` | `[[:blueprint :planter]]` |
| P4 | `(bp/drawable-changes #{:bench :planter} (bp/start-removal W :lamp))` | `{:add [:lamp] :remove #{}}` |
| P5 | a previewed file build the file drops is kept: `w0 = (-> (bp/merge-file {:blueprint-text TXT :blueprints [B1]} {:as-of 1 :blueprints [(dissoc B2 :from-file?)]}) :world (bp/apply-real-builds {:real [:bench]}) (bp/start-removal :bench))`; `r = (bp/merge-file w0 {:as-of 2 :blueprints []})` | `(:removed r)` = `[]`; `(bp/status (:world r) :bench)` = `:removing` |

### apply-real-builds with `:removed`
| # | Call | Expected |
|---|---|---|
| A1 | `(st (bp/apply-real-builds {:blueprints [B1 B2 B3]} {:real [:lamp] :removed [:bench]}))` | `[:real :rejected :pending]` |
| A2 | `:removed` beats `:real`: `{:real [:lamp :bench] :removed [:bench]}` | `[:real :rejected :pending]` |
| A3 | a file build: `wf = (:world (bp/merge-file {:blueprints [B1]} {:as-of 1 :blueprints [(dissoc B2 :from-file?)]}))`; `(bp/status (bp/apply-real-builds wf {:removed [:bench]}) :bench)`; an estate build: `(bp/status (bp/apply-real-builds wf {:removed [:lamp]}) :lamp)` | `:rejected`; `:rejected` |
| A4 | the 5 s re-apply keeps a preview: `(bp/status (bp/apply-real-builds (bp/start-removal W :lamp) {:real [:lamp :bench]}) :lamp)` | `:removing` |
| A5 | removed beats removing: `(bp/status (bp/apply-real-builds (bp/start-removal W :lamp) {:real [:bench] :removed [:lamp]}) :lamp)` | `:rejected` |
| A6 | `(bp/apply-real-builds W nil)`, `(bp/apply-real-builds W {:real [] :removed []})` | `identical?` to `W` both |
| A7 | never drawn: `(bp/drawable-changes nil (bp/apply-real-builds {:blueprints [B1 B2 B3]} {:removed [:lamp]}))` | `{:add [:bench :planter] :remove #{}}` |

### The bridge
| # | Call | Expected |
|---|---|---|
| BR1 | `(b/remove-real-build {:as-of 5 :real [:bench :lamp]} :lamp 9)` | `{:as-of 9 :real [:bench] :removed [:lamp]}` |
| BR2 | `(b/remove-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :bench 12)` | `{:as-of 12 :real [] :removed [:bench :lamp]}` |
| BR3 | refused: `(b/remove-real-build {:real [:bench]} :lamp 9)`; already removed `(b/remove-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :lamp 12)`; `(b/remove-real-build nil :lamp 9)` | `nil` each |
| BR4 | shape: `(set (keys (b/remove-real-build {:real [:lamp]} :lamp 9)))` | `#{:as-of :real :removed}` |
| BR5 | add keeps `:removed`: `(b/add-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :planter #{:bench :lamp :planter} 12)` | `{:as-of 12 :real [:bench :planter] :removed [:lamp]}` |
| BR6 | a removed id can't be made real: `(b/add-real-build {:real [] :removed [:lamp]} :lamp #{:lamp} 12)` | `nil` |
| BR7 | `(b/validate (req {"action" "remove" "id" "lamp-post-by-canal"}) ctx)`; `(:status (b/validate (req {"action" "remove" "id" "../x"}) ctx))` | `{:ok true :action :remove-build :id :lamp-post-by-canal}`; `400` |
| BR8 | `(crew/remove-build-body {:token "t" :port 8091} :lamp-post-by-canal)` | `{"token" "t" "action" "remove" "id" "lamp-post-by-canal"}` |

### `bb blueprint-add` refusing a removed id
| # | Call | Expected |
|---|---|---|
| D1 | `(bf/add nil E :le-testeur PL-IN 10 #{:planter})` | `{:refused "id :planter was removed; pick a new id"}` |
| D2 | `(bf/add nil E :le-testeur PL-IN 10 #{:other})` | `{:ok {:as-of 10 :blueprints [(assoc PL-IN :by :le-testeur)]} :id :planter}` |
| D3 | 5-arity = empty removed set: `(= (bf/add nil E :le-testeur PL-IN 10) (bf/add nil E :le-testeur PL-IN 10 #{}))`; nil set: `(:ok (bf/add nil E :le-testeur PL-IN 10 nil))` | `true`; non-nil |
| D4 | estate beats removed: `(bf/add nil #{:planter} :le-testeur PL-IN 10 #{:planter})` | `{:refused "id :planter is already in the estate"}` |
| D5 | removed beats already-in-file: `(bf/add {:as-of 1 :blueprints [(assoc PL-IN :by :x)]} E :le-testeur PL-IN 10 #{:planter})` | `{:refused "id :planter was removed; pick a new id"}` |
| D6 | `(bf/removed-ids {:real [:x] :removed [:a "b" :c]})`, `(bf/removed-ids nil)` | `#{:a :c}`, `#{}` |

### The estate's words
| # | Call | Expected |
|---|---|---|
| E1 | `t = (:blueprint-text (w/load-estate w/estate-edn))`; each of `:remove :close :remove-it :keep :removing :removing-wait :removed :no-bridge-remove` | a string, not blank |
| E2 | `(select-keys t [:remove :close :remove-it :keep :removing :removing-wait :removed :no-bridge-remove])` | the eight values of §2.1, exactly |
| E3 | the shipped estate: `(bp/card (bp/open-card (bp/apply-real-builds (w/load-estate w/estate-edn) {:real [:lamp-post-by-canal]}) :lamp-post-by-canal) nil)` | `{:id :lamp-post-by-canal :mode :real :buttons [{:action :remove :label "Remove…" :disabled? false} {:action :close :label "Close" :disabled? false}]}` |

Total: 59 cases (S1 R5 F4 X3 O10 C7 P5 A7 BR8 D6 E3). Expected `:until` values: `1000 + c/hal-ms (6000)` = `7000`.

---

## 6. Builder's note (`crew/runs/remove-build-v0-20261123-1815/builder-notes.md`)

What was built; what was checked by hand (bridge-less paths in the page; the bridge rule and
`bb blueprint-add` refusal against temp files via `PM_BUILDS_FILE` / `PM_BLUEPRINTS_FILE`, never
`state/`); and what Hal should click headless:
- a real build first (e.g. `[:blueprint :lamp-post-by-canal]` once it's in `state/builds.edn`
  `:real`), via the existing pick → `#pm-trial-card` shows
- Remove…: `#pm-trial-card [data-action="remove"]` (`.trial-remove`) → the item turns red, its sign reads "will be removed"
- Close: `#pm-trial-card [data-action="close"]` (`.trial-close`)
- Remove it: `#pm-trial-card [data-action="remove-it"]` (`.trial-remove-it`; reads "Removing…", disabled, while in flight)
- Keep it: `#pm-trial-card [data-action="keep"]` (`.trial-keep`)
- with no bridge, Remove it → back to real, caption "Start the bridge (bb crew-bridge) to remove it."

Known limits to state: a removal can't be undone (add the blueprint again with a new id); like
`:real`, if `state/builds.edn` arrives after the first draw on a reload, a removed build can flash
for one poll before it's taken down.

---

## 7. Out of scope (unchanged)
Removing non-blueprint things (mailbox, creatures, kit props); moving/editing a build; a bot-drafted
removal; undoing a removal.

---

## 8. Wording for le-docteur (after the gate passes)

- **`TASKS.md`**: a new row directly under T-14, in the same done style:
  > - [x] Nov 23 (crew round remove-build-v0) T-15 Remove a real build, try-first (Michael, Nov 23: "once its made real its there for good … should we have the undo or delete come up"). Click a real build and the same card shows **Remove…** and **Close**; Remove… previews the loss (the item turns into a see-through red removal ghost, its sign reads "will be removed") and offers **Remove it** and **Keep it**. Keep it puts it back as it was; Remove it records the removal through the bridge (`state/builds.edn` `:removed`, reads "Removing…" while it does), and the build is gone on every reload. No bridge: nothing changes and the twin says why. A removed id can't be reused (`bb blueprint-add` refuses it; pick a new id).
- **`docs/GDD.md` §2.1, Approve row**: in the row's first sentences, change "Approve it and it becomes real; reject it and it vanishes." to
  > Approve it and it becomes real; reject it and it vanishes. A real build can be removed again, the same try-first way: the removal shows as a red ghost before you confirm it.

  and append after the T-14 *Built* note, same style:
  > *Built (Nov 23): a real build can be removed. Click it for **Remove…**, see it as a red removal ghost, then **Remove it** (recorded through the bridge; gone on every reload) or **Keep it**.*
