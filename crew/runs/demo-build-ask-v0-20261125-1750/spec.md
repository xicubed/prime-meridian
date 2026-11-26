# Spec: ask the demo's twin to build something (final project, F-2)

Quest: `.crew/quests/demo-build-ask-v0.md`. Start commit `06d7428` (win-the-day-v0 has landed).
In the demo build only, a request typed or said to the twin in 💭 ("put a bench here") is parsed
from data, and the ground around the target is measured in the browser. `pm.lot/place` picks a
level, open spot, and the new blueprint goes into a demo "file" kept in localStorage. It reaches the
world through the existing `apply-blueprint-file!` path, so it seats, draws and announces itself.
The twin then says where he put it. In the demo, Make it real and Remove record into the same
localStorage key instead of the bridge.

Everything the quest records as decided stands. This spec fixes the names, the shapes, the order of
checks and the four looks, and works out the test values by hand.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/ask_build.cljc` (new, ns `pm.ask-build`); `src/pm/three.cljs` (only the new `ground-grid`, §4); `src/pm/core.cljs` (only the wiring in §5); `resources/estate.edn` (only the new `:demo-builds` block, §2.1, copied exactly); in the run dir: `builder-notes.md` (§8) | `test/`, `src/pm/lot.cljc`, `src/pm/blueprint.cljs`, `src/pm/blueprint_file.cljc`, `src/pm/world.cljs`, `src/pm/today.cljs`, `src/pm/ui.cljs`, `docs/`, `TASKS.md`, `state/`, `public/`. No npm deps. Don't start services |
| **tester** (le-testeur) | `test/pm/ask_build_test.cljs` (new, ns `pm.ask-build-test`): only new tests, §7 | every existing test and assertion; everything else. The tests are pure: no localStorage, no DOM, no three.js, no `js/Date`, no files |
| **le-docteur** | `TASKS.md`: the F-2 row (§9), after the gate passes | everything else |

The builder and the tester work in parallel, from this spec only. The tester pins the `:demo-builds`
strings and numbers, so the builder copies §2.1 character for character.

---

## 2. Data

### 2.1 The estate's `:demo-builds` block (`resources/estate.edn`, new top-level key)

Insert it right after the `:blueprints [...]` vector, with this comment:

```clojure
 ;; The demo's pretend twin builds what you ask for in 💭 (pm.ask-build; demo build only, GDD:
 ;; every connection has an in-game pretend version). :words and :places are whole words or
 ;; phrases, lowercase; :looks follow the blueprint look format above (grounded on y 0, every
 ;; part at least 0.08 m, palette keys). The ground is measured in the browser, never shipped:
 ;; :sampler says how (cover mesh names, Hal's T/B heights, the ray height).
 :demo-builds
 {:looks  {:bench     {:summary "A bench"
                       :look {:parts [{:shape :box :size [1.1 0.1 0.4]   :at [0 0.45 0]        :color :brown}   ; seat
                                      {:shape :box :size [1.1 0.4 0.08]  :at [0 0.7 -0.16]     :color :brown}   ; back
                                      {:shape :box :size [0.08 0.4 0.08] :at [0.45 0.2 0.15]   :color :brown}   ; legs
                                      {:shape :box :size [0.08 0.4 0.08] :at [0.45 0.2 -0.15]  :color :brown}
                                      {:shape :box :size [0.08 0.4 0.08] :at [-0.45 0.2 0.15]  :color :brown}
                                      {:shape :box :size [0.08 0.4 0.08] :at [-0.45 0.2 -0.15] :color :brown}]}}
           :lamp-post {:summary "A lamp post"
                       :look {:parts [{:shape :box :size [0.3 0.1 0.3]   :at [0 0.05 0]        :color :tin}     ; base
                                      {:shape :box :size [0.12 2.2 0.12] :at [0 1.2 0]         :color :tin}     ; post
                                      {:shape :box :size [0.3 0.3 0.3]   :at [0 2.45 0]        :color :gold}]}} ; lamp
           :table     {:summary "A table"
                       :look {:parts [{:shape :box :size [1.0 0.08 0.7]  :at [0 0.74 0]        :color :brown}   ; top
                                      {:shape :box :size [0.08 0.7 0.08] :at [0.42 0.35 0.27]  :color :brown}   ; legs
                                      {:shape :box :size [0.08 0.7 0.08] :at [0.42 0.35 -0.27] :color :brown}
                                      {:shape :box :size [0.08 0.7 0.08] :at [-0.42 0.35 0.27] :color :brown}
                                      {:shape :box :size [0.08 0.7 0.08] :at [-0.42 0.35 -0.27] :color :brown}]}}
           :planter   {:summary "A planter"
                       :look {:parts [{:shape :box :size [0.8 0.4 0.4]   :at [0 0.2 0]         :color :brown}   ; box
                                      {:shape :box :size [0.6 0.16 0.24] :at [0 0.48 0]        :color :gold}]}}};flowers
  :words  {"bench" :bench "seat" :bench
           "lamp post" :lamp-post "lamppost" :lamp-post "lamp" :lamp-post "light" :lamp-post
           "table" :table
           "planter" :planter "flowers" :planter "flower box" :planter "plant pot" :planter}
  :places {"here" :here "there" :here
           "mailbox" :mailbox "booth" :phone-booth "phone booth" :phone-booth "workshop" :workshop
           "creek" :creek "stream" :creek "water" :creek}
  :anchors {:creek [-0.9 -15.7]}   ; where "the creek" is measured around: open water by the axolotl
  :lines  {:placed    "{summary}, drawn near {place}. Try it, and make it real if you like it."
           :refused   "I can't put that there: {reason}."
           :help      "I can build a bench, a lamp post, a table or a planter. Try: put a bench here."
           :made-real "Made real. It stays in this browser."
           :sky       "The middle of your screen is sky. Look at the ground and ask me again."}
  :sampler {:cover  ["Leaves" "CanopyMass" "WoodlandNear" "TreeWood" "Rocks" "Gravel" "creek"]
            :water  ["creek"] :rocks ["Rocks"] :gravel ["Gravel"]
            :tree-m 2 :bush-m 0.35   ; cover > 2 m above the ground: T; 0.35-2 m: B (Hal's sampler)
            :pad-m 1                 ; measured past search-m, so a look's feet stay on the grid
            :from-y 40}}             ; rays start this high (above every canopy)
```
(The `;flowers` comment after the closing braces is fine to drop or move; the data is what's pinned.)

### 2.2 The demo file (localStorage `pm.demo-builds.v1`, `pr-str`)

```clojure
{:as-of      1795597200000
 :blueprints [{:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look {...} :by :hal-twin-agent-id}]
 :real       [:bench-1 :bench-by-mailbox]   ; made real in this browser; estate sample ids count too
 :removed    [:table-1]}                    ; removed in this browser: never comes back, id never reused
```
The quest names the shape as `{:as-of :blueprints :real}`. This spec adds `:removed`, because
removing an estate sample blueprint has to survive a reload, and `bp/apply-real-builds` already
reads `:removed`. `:by` is the twin walker's `:agent` keyword, from data and never written as a
literal.

### 2.3 A request

```clojure
{:look :bench :near :here}          ; :near is :here or a landmark keyword from :places
{:help true}                        ; no look word in the text
```

---

## 3. `src/pm/ask_build.cljc`: public functions

`(ns pm.ask-build (:require [clojure.string :as str] [pm.lot :as lot] [pm.blueprint-file :as bf]))`.
It is portable and pure: no atoms, no I/O, no `js/Date`, no three.js. It does **not** require
`pm.content`, `pm.blueprint` or `pm.plan`, because those are `.cljs`. Use reader conditionals only
for maths, the way `pm.lot` does. The ns docstring says all this. No person, pet or member is named.

`ctx`, used by `ask` and `plan`, is
`{:estate raw :world w :file f :rules r :here [x y z]|nil :by kw :now ms}`. Here `:estate` is the
**raw** estate (`cljs.reader/read-string` of `estate.edn`, with `:mirrors` still a vector, which is
what `pm.lot` expects; `w/load-estate` turns it into a map and `lot/things` would break). `:world`
is the live world (for its `:blueprints` and their statuses), `:file` is the demo file,
`:rules` is `:build-rules`, `:here` is the screen-centre ground hit, and `:by` is the twin's agent.
The `:demo-builds` data is always read from `(:demo-builds (:estate ctx))`.

| # | Signature | Returns / rules |
|---|---|---|
| 1 | `(normalize-text s)` | `" " + tokens joined by " " + " "`, where tokens are `(str/lower-case s)` split on `#"[^a-z0-9]+"` with blanks dropped. A non-string or blank `s` gives `" "`. For example, `"Put a SEAT, here!"` gives `" put a seat here "`. Letters outside a–z split words; that's a known limit. |
| 2 | `(find-word table text)` | `table` maps phrases to keywords. Each phrase is normalized the same way (without pads) and searched for as `(str " " phrase " ")` inside `(normalize-text text)`. Returns the keyword of the match that starts **earliest**. On a tie, the longer phrase wins, then the smaller keyword `name`. Returns nil when nothing matches. |
| 3 | `(parse-request demo text)` | `demo` is the `:demo-builds` map. If `(find-word (:words demo) text)` is nil, returns `{:help true}`. Otherwise returns `{:look <it> :near (or (find-word (:places demo) text) :here)}`. |
| 4 | `(taken-ids world file)` | The set of every `:id` in `(:blueprints world)`, every `:id` in `(:blueprints file)`, and every keyword in `(:real file)` and `(:removed file)`. |
| 5 | `(next-id look taken)` | `(keyword (str (name look) "-" n))` for the smallest integer `n >= 1` not in `taken`. |
| 6 | `(target-xz estate demo near here)` | The `[x z]` to measure around. `:here` gives `[(nth here 0) (nth here 2)]` when `here` is a 3-vector of numbers, else nil. A keyword whose `(:at (get estate near))` is a 3-vector of numbers gives `[x z]` from it. Otherwise `(get-in demo [:anchors near])` when that's a 2-vector of numbers, else nil. |
| 7 | `(grid-spec [tx tz] rules pad-m)` | `{:origin [x0 z0] :step s :cols c :rows r}`, with `s = (:step-m rules)` and `h = (+ (:search-m rules) pad-m)`. `x0 = s·floor((tx−h)/s + 1e-9)`, `x1 = s·ceil((tx+h)/s − 1e-9)`, `c = round((x1−x0)/s) + 1`. z works the same way. Row `r` is `z = z0 + r·s` and column `c` is `x = x0 + c·s`, the layout `lot/cell` reads, and the origin sits on the step grid, so `lot/place`'s candidates land exactly on cells. |
| 8 | `(name-has? needles name)` | True when `name` is a string containing any of `needles` (case-insensitive substring). |
| 9 | `(cover-name? sampler name)` | `(name-has? (:cover sampler) name)`. Core uses it to pick the cover meshes. |
| 10 | `(cell-kind sampler g c)` | `g` is the first ground-mesh hit `[y name]` or nil; `c` is the first hit of ground ∪ cover, `[y name]` or nil (a nil `c` counts as `g`). The checks run in this order, and the first one that holds wins. `g` nil: `" "`. The name of `c` has a `:water` needle: `"~"`. A `:rocks` needle: `"R"`. A `:gravel` needle in the name of `c` or `g`: `":"`. `h = (first c) − (first g)`; `h > tree-m + 1e-9`: `"T"`. `h >= bush-m − 1e-9`: `"B"`. Otherwise `"o"`. |
| 11 | `(sample-cell sampler g c)` | `{:kind (cell-kind sampler g c) :y (when g (/ (round (* 100 (first g))) 100))}`, so y is rounded to the centimetre. |
| 12 | `(fill s vars)` | `{word}` placeholders filled from `vars` (keywords). A nil or missing var keeps the placeholder. A non-string `s` gives nil. This is the same behaviour as `pm.content/fill`, re-done here because this ns is cljc. |
| 13 | `(lot-estate estate world)` | `estate` with `:blueprints` replaced by those of `(:blueprints world)` that are `bf/valid?` and whose `(or (:status bp) :pending)` is in `#{:pending :approved :real :removing}`, in order. Rejected and removed builds don't block a spot; ghosts and builds do. |
| 14 | `(ask ctx text)` | The step that comes before measuring. With `demo = (:demo-builds (:estate ctx))` and `req = (parse-request demo text)`: help gives `{:say (get-in demo [:lines :help])}`. Then `xz = (target-xz estate demo (:near req) (:here ctx))`. If `xz` is nil and near is `:here`, returns `{:say (get-in demo [:lines :sky])}`. If `xz` is nil for any other near, returns `{:say (fill refused {:reason (str "I don't know a place called " (name near))})}`. Otherwise returns `{:req req :target xz :spec (grid-spec xz (:rules ctx) (get-in demo [:sampler :pad-m]))}`. |
| 15 | `(plan ctx req ground)` | After measuring. `ground` is a `pm.lot` ground map (or nil). If `look-def = (get-in demo [:looks (:look req)])` is nil, returns `{:ok false :say help}`. Otherwise: `id = (next-id (:look req) (taken-ids world file))`, `draft = {:id id :summary (:summary look-def) :look (:look look-def)}`, `est = (lot-estate estate world)`, `seen = (when (= :here near) {:ok true :view {:center here :walkers {}}})`, and `res = (lot/place ground est [] rules draft near seen)`. When `(:ok res)`: `bp = (assoc (lot/blueprint draft res) :by by)` and `word = (:word (get (lot/landmarks ground est [] (:view seen)) near))`, and it returns `{:ok true :id id :blueprint bp :file (add-build file bp now) :say (fill placed {:summary (str/replace summary #"\.$" "") :place word})}`. Otherwise it returns `{:ok false :say (fill refused {:reason (:reason res)})}`. |
| 16 | `empty-file` (def) | `{:as-of 0 :blueprints [] :real [] :removed []}` |
| 17 | `(normalize-file f)` | A non-map gives `empty-file`. `:as-of` is kept when it's an integer, else 0. `:blueprints` keeps only `bf/valid?` maps, first per id, in order. `:real` and `:removed` keep only keywords, distinct, in order. Other keys are dropped. |
| 18 | `(save-text f)` | `(pr-str (normalize-file f))` |
| 19 | `(restore text)` | `(normalize-file <read-string of text>)` inside a try (`cljs.reader` / `clojure.edn` by reader conditional). nil, `""` and garbage give `empty-file`. Never throws. |
| 20 | `(add-build file bp now)` | Normalizes `file`. If `bp`'s id is already in `:blueprints`, returns the normalized file unchanged. Otherwise `conj`s `bp` and sets `:as-of (max now (inc as-of))`. |
| 21 | `(mark-real file id now)` | Normalizes. If `id` is already in `:real` or is in `:removed`, returns it unchanged. Otherwise `conj`s onto `:real` and bumps `:as-of` the same way. |
| 22 | `(mark-removed file id now)` | Normalizes. If `id` is already in `:removed`, returns it unchanged. Otherwise drops `id` from `:blueprints` and `:real`, `conj`s it onto `:removed`, and bumps `:as-of`. |
| 23 | `(builds-of file)` | `{:real (:real f) :removed (:removed f)}` of the normalized file, the shape `bp/apply-real-builds` takes. |

---

## 4. `pm.three`: `ground-grid` (the only interop)

```clojure
(defn ground-grid
  "The ground around a target as a pm.lot ground map, measured in the browser (never shipped:
   the kit's licence forbids extracted datasets). Two downward rays per cell from `from-y`:
   the first hit of `ground-meshes`, and the first hit of `cover-meshes` (ground plus cover).
   `cell-fn` (pure, pm.ask-build/sample-cell partially applied) turns them into {:kind :y}.
   Never throws: a failed ray counts as no hit."
  [ground-meshes cover-meshes {:keys [origin step cols rows]} from-y cell-fn]
  ...)
```
- For row `r` in `0..rows-1` and column `c` in `0..cols-1`: `x = x0 + c·step` and `z = z0 + r·step`.
  `g = (first (down-hits ground-meshes x z from-y))` and `h = (first (down-hits cover-meshes x z from-y))`
  (`down-hits` is three.cljs:734; both are `[y "mesh/material"]` or nil), then `(cell-fn g h)`.
- Returns `{:origin origin :step step :cols cols :rows rows :kinds [<row string> ...] :y [[y ...] ...]}`,
  where each row string is `(apply str (map :kind row-cells))` and each y row is `(mapv :y row-cells)`.
- Empty meshes give every cell `" "` (no hit).

---

## 5. `pm.core` wiring (name every hook)

Add `[pm.ask-build :as ab]`.

### 5.1 New state (next to `today-key`)
```clojure
(def demo-builds-key "pm.demo-builds.v1")
(defonce ^:private !demo-file (atom ab/empty-file))
(defonce ^:private !cover-meshes (atom []))
(def ^:private raw-estate (cljs.reader/read-string world/estate-edn))   ; :mirrors still a vector, for pm.lot
```

### 5.2 New private fns
```clojure
(defn- save-demo-file! []
  (try (.setItem js/localStorage demo-builds-key (ab/save-text @!demo-file)) (catch :default _ nil)))

(defn- demo-merge!
  "The demo file into the world through the one existing path (seat, draw, author's line)."
  []
  (reset! !last-builds (ab/builds-of @!demo-file))
  (apply-blueprint-file! @!demo-file))

(defn- twin-reply! [text at]
  (when-let [ev (world/twin-says @!world text)] (swap! !world world/apply-event ev at)))

(defn- cover-meshes!
  "Ground ∪ the kit's cover meshes (ab/cover-name?), found once and cached."
  [^js h] ...)   ; (into (view-ground-meshes! h) (t/find-meshes (.-scene h) #(ab/cover-name? sampler %))), cached when non-empty

(defn- demo-ask!
  "The demo's pretend twin: a build request typed or said in 💭 (the demo build only)."
  [text at]
  (let [^js h  (kit/handle)
        ctx    {:estate raw-estate :world @!world :file @!demo-file :rules (:build-rules @!world)
                :here (when h (t/center-hit (.-camera h) (view-ground-meshes! h)))
                :by (:agent (world/twin-walker @!world)) :now (js/Date.now)}
        r      (ab/ask ctx text)]
    (if-let [s (:say r)]
      (twin-reply! s at)
      (let [sampler (get-in raw-estate [:demo-builds :sampler])
            t0      (js/performance.now)
            ground  (when h (t/ground-grid (view-ground-meshes! h) (cover-meshes! h) (:spec r)
                                           (:from-y sampler) (partial ab/sample-cell sampler)))
            _       (js/console.info "Prime Meridian: ground grid" (:cols (:spec r)) "x" (:rows (:spec r))
                                     "in" (js/Math.round (- (js/performance.now) t0)) "ms")
            p       (ab/plan ctx (:req r) ground)]
        (when (:ok p)
          (reset! !demo-file (:file p))
          (save-demo-file!)
          (demo-merge!))
        (twin-reply! (:say p) at)))))

(defn- demo-make-real! [id done]
  (swap! !world bp/make-real id)              ; a status turning :real: the Today goal ticks (watch)
  (swap! !demo-file ab/mark-real id (js/Date.now))
  (save-demo-file!)
  (say! :blueprint-real (get-in @!world [:demo-builds :lines :made-real]))
  (done))

(defn- demo-remove! [id]
  (let [now (js/Date.now)]
    (swap! !world bp/finish-removal id now)
    (swap! !demo-file ab/mark-removed id now)
    (save-demo-file!)))
```
With no kit (scaffold), `ground` is nil. `lot/place` then refuses with its no-ground reason, and the
twin says it. That's acceptable.

### 5.3 Hooks into existing fns (exact places)

| Where | Change |
|---|---|
| `on-say` (core.cljs:794), the branch `(when (and (not (bridge-on?)) (= member (:agent (world/twin-walker @!world)))) …)` | in the body: `(if DEMO (demo-ask! text (+ now 1)) (when-let [ev (world/twin-says @!world (world/no-bridge-line @!world))] (swap! …)))`. Use the routed `text` (`(:text r)`). The dev build without a bridge keeps the no-bridge line, and with a bridge nothing changes. 🎙 reaches here through `pm.call`, so no separate hook is needed. |
| `make-real!` (core.cljs:1338) | first branch: `(if DEMO (demo-make-real! id done) <the existing if-not bridge-on? …>)` |
| `remove-click!` (core.cljs:~1376), after `(reset! !saving id)` | `(if DEMO (do (demo-remove! id) (reset! !saving nil)) <the existing if-not bridge-on? …>)` |
| `fresh-start!` | inside the `try`: `(.removeItem js/localStorage demo-builds-key)`; after it: `(reset! !demo-file ab/empty-file)` |
| `init!`, the `(if DEMO (do …))` branch, first thing | `(reset! !demo-file (ab/restore (try (.getItem js/localStorage demo-builds-key) (catch :default _ nil))))` then `(demo-merge!)`. **Always**, even with an empty file: this first merge is `merge-file`'s `first?` (which announces nothing), so the first real request afterwards *is* announced. It runs inside win-the-day's `!real-from-file?` wrap (apply-blueprint-file!), so builds already real at load don't tick the Today goal. |

`apply-blueprint-file!`, `bp/make-real`, `bp/finish-removal` and the trial card are reused as they
are. `start-kit!`'s `seat-blueprints!` seats anything merged before the kit's ground was up.

---

## 6. Behaviour, stated once

- **Demo only.** Every new path is behind `DEMO`. The dev build is unchanged, with or without a bridge.
- **What the player sees:** the twin's bubble and 💭 line (`twin-reply!`, the same path as the
  no-bridge reply). When a build lands, there is also the author's line from `bp/author-line`
  ("drew a blueprint: A bench. Click it to try it.") and the neon-blue ghost. The trial card's Make
  it real then records it into `pm.demo-builds.v1`, and the twin says `:made-real`.
- **Reload** restores every demo build with its status: pending ones come back pending, real ones
  real, and removed ones stay gone. A build that was only a trial (`:approved`) comes back pending,
  the same trial rule as estate blueprints. `?fresh` clears the key.

---

## 7. Test cases (`test/pm/ask_build_test.cljs`, ns `pm.ask-build-test`)

Requires: `[cljs.test :refer [deftest is]] [cljs.reader :as reader] [pm.ask-build :as ab] [pm.lot :as lot] [pm.blueprint :as bp] [pm.world :as w]`.

Fixtures:
```clojure
(def RAW   (reader/read-string w/estate-edn))       ; the raw estate (mirrors a vector)
(def DB    (:demo-builds RAW))
(def SAMP  (:sampler DB))
(def RULES (:build-rules RAW))                      ; {:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12}
(def E0    {:demo-builds DB :mirrors [] :walkers []})            ; an empty estate
(def DB2   (assoc DB :words {"banc" :bench} :places {"ici" :here "poste" :post}))
(def E1    {:demo-builds DB2 :post {:at [0 1 0]} :mirrors [] :walkers []})   ; one place at the origin
(defn grid [kind] {:origin [-7 -7] :step 0.5 :cols 29 :rows 29
                   :kinds (vec (repeat 29 (apply str (repeat 29 kind)))) :y (vec (repeat 29 (vec (repeat 29 1))))})
(def OPEN  (grid "o")) (def TREES (grid "T"))
(defn ctx [estate world file here] {:estate estate :world world :file file :rules RULES :here here :by :tw :now 5000})
(def BENCH (get-in DB [:looks :bench :look]))
(def BP1   {:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw})
(def BP2   {:id :table-1 :at [3 1 3] :yaw 0 :summary "A table" :look (get-in DB [:looks :table :look]) :by :tw})
(def ≈ (fn [a b] (< (js/Math.abs (- a b)) 1e-6)))
```

**Parser**

| # | Test | Expected |
|---|---|---|
| t01 | `normalize-text` | `"Put a SEAT, here!"` → `" put a seat here "`; `"  lamp   post "` → `" lamp post "`; `nil` → `" "`; `""` → `" "`; `42` → `" "` |
| t02 | look + "here" | `(ab/parse-request DB "put a bench here")` = `{:look :bench :near :here}` |
| t03 | synonym, case, punctuation, a place | `"Put a SEAT by the mailbox!"` → `{:look :bench :near :mailbox}` |
| t04 | a two-word look and a two-word place | `"can you build a lamp post near the phone booth?"` → `{:look :lamp-post :near :phone-booth}` |
| t05 | no place word means here | `"a table"` → `{:look :table :near :here}` |
| t06 | a planter synonym by the creek | `"flowers by the creek please"` → `{:look :planter :near :creek}` |
| t07 | the earliest look wins | `"a planter and a bench"` → `{:look :planter :near :here}` |
| t08 | the earliest place wins | `"table by the mailbox near the creek"` → `{:look :table :near :mailbox}` |
| t09 | one word, other places | `"a lamppost at the workshop"` → `{:look :lamp-post :near :workshop}`; `"a light by the booth"` → `{:look :lamp-post :near :phone-booth}` |
| t10 | the help cases | `{:help true}` for `"hello there"`, `""`, `nil`, `"   "`, `"benches"` (whole words only), `"a tablecloth"` |
| t11 | `find-word` ties | `(ab/find-word {"lamp" :a "lamp post" :b} "a lamp post")` = `:b` (same start, the longer phrase wins); `(ab/find-word {"zz" :z "aa" :a} "zz")` = `:z`; `(ab/find-word {} "x")` = nil |
| t12 | the words come from data (a second table) | `(ab/parse-request DB2 "un banc ici")` = `{:look :bench :near :here}`; `(ab/parse-request DB2 "un banc pres de la poste")` = `{:look :bench :near :post}`; `(ab/parse-request DB2 "put a bench here")` = `{:help true}` |

**Ids, targets, grid**

| # | Test | Expected |
|---|---|---|
| t13 | `next-id` | `(ab/next-id :bench #{})` = `:bench-1`; with `#{:bench-1 :bench-2}`, `:bench-3`; with `#{:bench-2}`, `:bench-1`; `(ab/next-id :lamp-post #{:lamp-post-1})` = `:lamp-post-2` |
| t14 | `taken-ids` over the world and the file | `(ab/taken-ids {:blueprints [{:id :bench-1} {:id :bench-by-mailbox}]} {:blueprints [{:id :bench-2}] :real [:bench-3] :removed [:bench-4]})` = `#{:bench-1 :bench-by-mailbox :bench-2 :bench-3 :bench-4}`, and `next-id :bench` on it gives `:bench-5`; `(ab/taken-ids nil nil)` = `#{}` |
| t15 | `target-xz` | `(ab/target-xz RAW DB :mailbox nil)` = `[4 -8]`; `:phone-booth` → `[4.9 -6.6]`; `:workshop` → `[-6.2 -3.5]`; `:creek` → `[-0.9 -15.7]` (anchor); `:here` with `[1 2 3]` → `[1 3]`; `:here` with nil → nil; `:nowhere` → nil |
| t16 | `grid-spec` | `(ab/grid-spec [0 0] RULES 1)` = `{:origin [-7 -7] :step 0.5 :cols 29 :rows 29}`; `(ab/grid-spec [4 -8] RULES 1)` = `{:origin [-3 -15] :step 0.5 :cols 29 :rows 29}`; `(ab/grid-spec [0.2 0.3] RULES 1)` = `{:origin [-7 -7] :step 0.5 :cols 30 :rows 30}` (x1 = z1 = 7.5); `(ab/grid-spec [3.5 -9.25] RULES 1)` = `{:origin [-3.5 -16.5] :step 0.5 :cols 29 :rows 30}` |

**Sampler**

| # | Test | Expected (`TER` = `"SM_Hiroshi_Terrain/MAT"`, `GRV` = `"SM_Hiroshi_Gravel/MAT"`) |
|---|---|---|
| t17 | open, and no ground | `(ab/cell-kind SAMP [1.5 TER] [1.5 TER])` = `"o"`; `(ab/cell-kind SAMP [1.5 TER] nil)` = `"o"`; `(ab/cell-kind SAMP nil nil)` = `" "`; `(ab/cell-kind SAMP nil [3 "Oak_Leaves"])` = `" "` |
| t18 | trees and bushes by height | with `g` = `[1.5 TER]`: `c` `[5.2 "Oak_CanopyMass"]` → `"T"`; `[2.0 "Hedge_Leaves"]` → `"B"`; `[3.5 "X_Leaves"]` → `"B"` (2.0 m is not > 2); `[3.6 "X_Leaves"]` → `"T"`; `[1.85 "X_Leaves"]` → `"B"` (0.35 m); `[1.7 "X_Leaves"]` → `"o"` (0.2 m) |
| t19 | water, rocks, gravel (in that order) | `(ab/cell-kind SAMP [1.2 GRV] [1.6 "SM_creek_water"])` = `"~"`; `(ab/cell-kind SAMP [1.5 TER] [1.9 "Bank_Rocks"])` = `"R"`; `(ab/cell-kind SAMP [1.2 GRV] [1.2 GRV])` = `":"`; `(ab/cell-kind SAMP [1.5 TER] [1.52 "Path_Gravel"])` = `":"`; `(ab/cell-kind SAMP [1.5 TER] [1.5 "x_CREEK"])` = `"~"` (case-insensitive) |
| t20 | `sample-cell` rounds y to the cm | `(ab/sample-cell SAMP [1.8249 TER] [1.8249 TER])` = `{:kind "o" :y 1.82}`; `(ab/sample-cell SAMP [1.826 TER] nil)` = `{:kind "o" :y 1.83}`; `(ab/sample-cell SAMP nil nil)` = `{:kind " " :y nil}` |
| t21 | `cover-name?` / `name-has?` | true for `"Oak_Leaves"`, `"SM_WoodlandNear_01"`, `"Bank_Rocks/MAT"`, `"creek_surface"`; false for `"SM_Hiroshi_Terrain"`, `nil`, `""`; `(ab/name-has? ["ab"] "xABy")` true |

**Data and helpers**

| # | Test | Expected |
|---|---|---|
| t22 | the four looks are buildable | for each of `:bench :lamp-post :table :planter` in `(:looks DB)`: `:summary` is a non-blank string; every part is a `:box` with 3 sizes each `>= 0.08`; every `:color` is in `#{:brown :tin :gold}`; the lowest part bottom `(min (- at-y (/ size-y 2)))` is within 1e-9 of 0 (none below); `(seq (lot/feet look))` is truthy |
| t23 | feet by hand | `(lot/feet bench)` = `[[0.45 0.15] [0.45 -0.15] [-0.45 0.15] [-0.45 -0.15]]`; lamp post → `[[0 0]]`; table → `[[0.42 0.27] [0.42 -0.27] [-0.42 0.27] [-0.42 -0.27]]`; planter → `[[0 0] [-0.4 -0.2] [-0.4 0.2] [0.4 -0.2] [0.4 0.2]]` |
| t24 | look radii by hand (≈) | bench 0.5852349955 (½√1.37); lamp post 0.2121320344 (½√0.18); table 0.6103277808 (½√1.49); planter 0.4472135955 (½√0.8) |
| t25 | the data resolves | every value of `(:words DB)` is a key of `(:looks DB)`; every value of `(:places DB)` is `:here`, or `(ab/target-xz RAW DB v nil)` is non-nil; `(:lines DB)` has non-blank strings at `:placed :refused :help :made-real :sky`; `(get-in DB [:lines :help])` = `"I can build a bench, a lamp post, a table or a planter. Try: put a bench here."`; nothing names anyone: `(some #(clojure.string/includes? (pr-str DB) %) ["Michael" "Hal" "Flora" "Juniper"])` is nil |
| t26 | `fill` | `(ab/fill "{a} and {b}" {:a 1 :b "x"})` = `"1 and x"`; `(ab/fill "{a} {zz}" {:a 1})` = `"1 {zz}"`; `(ab/fill nil {})` = nil |
| t27 | `lot-estate` keeps what blocks | world `{:blueprints [{:id :a :at [0 0 0] :summary "a"} {:id :b :at [0 0 0] :summary "b" :status :rejected} {:id :c :at [0 0 0] :summary "c" :status :real} {:id :d :at [0 0 0]} {:id :e :at [0 0 0] :summary "e" :status :removing}]}`: `(mapv :id (:blueprints (ab/lot-estate {:x 1} world)))` = `[:a :c :e]`, and `(:x …)` = 1 |

**ask / plan**

| # | Test | Expected |
|---|---|---|
| t28 | `ask`: help and sky | `(ab/ask (ctx E0 {} ab/empty-file [0 1 0]) "hello")` = `{:say "I can build a bench, a lamp post, a table or a planter. Try: put a bench here."}`; `(ab/ask (ctx E0 {} ab/empty-file nil) "put a bench here")` = `{:say "The middle of your screen is sky. Look at the ground and ask me again."}` |
| t29 | `ask`: ready to measure | `(ab/ask (ctx E0 {} ab/empty-file [0 1 0]) "put a bench here")` = `{:req {:look :bench :near :here} :target [0 0] :spec {:origin [-7 -7] :step 0.5 :cols 29 :rows 29}}` |
| t30 | `ask`: a place with no position | with `E2 = (assoc-in E0 [:demo-builds :places] {"moon" :moon})`: `(ab/ask (ctx E2 {} ab/empty-file [0 1 0]) "a bench by the moon")` = `{:say "I can't put that there: I don't know a place called moon."}` |
| t31 | `plan`: open ground, here | `(ab/plan (ctx E0 {} ab/empty-file [0 1 0]) {:look :bench :near :here} OPEN)` = `{:ok true :id :bench-1 :blueprint {:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw} :file {:as-of 5000 :blueprints [<that blueprint>] :real [] :removed []} :say "A bench, drawn near where you're looking. Try it, and make it real if you like it."}` |
| t32 | `plan`: keeps clear of a place (worked by hand) | `(ab/plan (ctx E1 {} ab/empty-file [0 1 0]) {:look :bench :near :here} OPEN)`: `:ok` true, `(:at (:blueprint r))` = `[-2.5 1 0]`, `:yaw` 0. (The post's r is 0.6, the bench's 0.585235 and the gap 1.2, so d ≥ 2.385235. The nearest grid distance is 2.5, and the smallest x among those is −2.5.) Near the post instead (`{:look :bench :near :post}`, here nil): the same `:at`, and `:say` = `"A bench, drawn near the post. Try it, and make it real if you like it."` |
| t33 | `plan`: refused under trees | `(ab/plan (ctx E0 {} ab/empty-file [0 1 0]) {:look :bench :near :here} TREES)` = `{:ok false :say "I can't put that there: no level open ground within 6 m of where you're looking: most of it is under trees."}` |
| t34 | `plan`: the id avoids the world and the file | world `{:blueprints [{:id :bench-1 :at [50 1 50] :summary "x"}]}` and file `{:blueprints [] :real [] :removed [:bench-2]}`: `(:id (ab/plan (ctx E0 world file [0 1 0]) {:look :bench :near :here} OPEN))` = `:bench-3`, still at `[0 1 0]` (the far one doesn't block) |
| t35 | `plan`: the other looks, and a bad look | with OPEN at here `[0 1 0]`: `:lamp-post` → `:id :lamp-post-1`, `:at [0 1 0]`; `:table` → `:table-1`; `:planter` → `:planter-1`, `:say` starting `"A planter, drawn near"`; `{:look :rocket :near :here}` → `{:ok false :say <the help line>}`; `ground` nil → `:ok false` and `:say` = `"I can't put that there: no ground map (state/ground.edn): the twin samples it from the kit."` |

**The demo file**

| # | Test | Expected |
|---|---|---|
| t36 | `empty-file`, `normalize-file` | `ab/empty-file` = `{:as-of 0 :blueprints [] :real [] :removed []}`; `(ab/normalize-file {:as-of 1.5 :blueprints [BP1 {:id :x} BP1 7] :real [:a "b" :a] :removed nil :junk 1})` = `{:as-of 0 :blueprints [BP1] :real [:a] :removed []}` |
| t37 | `save-text` / `restore` round trip | `F = (-> ab/empty-file (ab/add-build BP1 1000) (ab/mark-real :bench-1 1100) (ab/mark-removed :bench-by-mailbox 1200))`; `(ab/restore (ab/save-text F))` = `F`; `ab/empty-file` for `nil`, `""`, `"{:as-of"`, `"[1 2]"` |
| t38 | `add-build` adds once | `(ab/add-build ab/empty-file BP1 1000)` = `{:as-of 1000 :blueprints [BP1] :real [] :removed []}`; adding `BP1` again at 2000 leaves it equal (`:as-of` still 1000); `(:as-of (ab/add-build {:as-of 1000 :blueprints [] :real [] :removed []} BP2 900))` = 1001 |
| t39 | `mark-real` | `(ab/mark-real (ab/add-build ab/empty-file BP1 1000) :bench-1 1500)` = `{:as-of 1500 :blueprints [BP1] :real [:bench-1] :removed []}`; a second `mark-real` leaves it equal; `(ab/mark-real {:removed [:x]} :x 10)` = `{:as-of 0 :blueprints [] :real [] :removed [:x]}` |
| t40 | `mark-removed` | `(ab/mark-removed <t39's first result> :bench-1 1600)` = `{:as-of 1600 :blueprints [] :real [] :removed [:bench-1]}`; an estate id: `(ab/mark-removed ab/empty-file :bench-by-mailbox 10)` = `{:as-of 10 :blueprints [] :real [] :removed [:bench-by-mailbox]}`; a second remove leaves it equal |
| t41 | `builds-of` | `(ab/builds-of {:real [:a] :removed [:b] :blueprints []})` = `{:real [:a] :removed [:b]}`; `(ab/builds-of nil)` = `{:real [] :removed []}` |

**The merge, as core does it** (`E = (w/load-estate w/estate-edn)`, `W1 = (:world (bp/merge-file E ab/empty-file))`)

| # | Test | Expected |
|---|---|---|
| t42 | the first merge at load announces nothing | `(:blueprints-as-of W1)` = 0; `(:added (bp/merge-file E ab/empty-file))` = `[]` |
| t43 | a new build is added and announced once | `F1 = (ab/add-build ab/empty-file BP1 1000)`, `r = (bp/merge-file W1 F1)`: `(:added r)` = `[:bench-1]`, `(:announce r)` = `[:bench-1]`, `(bp/status (:world r) :bench-1)` = `:pending`; `(:by (first (filter #(= :bench-1 (:id %)) (:blueprints (:world r)))))` = `:tw`; merging `F1` again into `(:world r)` gives `:added []` and an `identical?` world; `F2 = (ab/add-build F1 BP2 2000)` merged into `(:world r)` gives `:added [:table-1]` |
| t44 | the real list is applied (demo builds and estate samples) | `F3 = (ab/mark-real (ab/mark-real F1 :bench-1 1500) :bench-by-mailbox 1500)`; `W3 = (bp/apply-real-builds (:world (bp/merge-file W1 F3)) (ab/builds-of F3))`: `(bp/status W3 :bench-1)` = `:real` and `(bp/status W3 :bench-by-mailbox)` = `:real`; `:lamp-post-by-canal` stays `:pending` |
| t45 | a removal sticks across a reload | `F5 = (-> F3 (ab/mark-removed :bench-1 1600) (ab/mark-removed :bench-by-mailbox 1600))`; on a fresh world, `W5 = (bp/apply-real-builds (:world (bp/merge-file W1 F5)) (ab/builds-of F5))`: no blueprint `:bench-1` in `(:blueprints W5)`, and `(bp/status W5 :bench-by-mailbox)` = `:rejected`; `(ab/next-id :bench (ab/taken-ids W5 F5))` = `:bench-2` (a removed id is never reused) |

That is 45 test cases. The tester may split a row into several `deftest`s, keeping the `tNN` prefixes.

---

## 8. `builder-notes.md` (the builder writes it in the run dir)

How Hal checks it headless, in the demo build:
1. `bb demo-build`, then serve `dist/demo` at a site root (for example `python3 -m http.server -d dist/demo 8090`)
   and open `/?fresh`; jack in and finish the Construct.
2. Aim the camera at open grass, open 💭, open the twin's thread, and type `put a bench here`, then try
   `a lamp post by the mailbox`, `hello`, and the same request while looking at the sky.
3. What to read back from devtools:
   - the world's blueprints:
     `cljs.core.pr_str(cljs.core.mapv(x => cljs.core.select_keys(x, cljs.core.vector(cljs.core.keyword("id"), cljs.core.keyword("status"), cljs.core.keyword("at"))), cljs.core.get(cljs.core.deref(pm.core._BANG_world), cljs.core.keyword("blueprints"))))`
     (the new `:bench-1`, `:pending`, its `:at`)
   - the twin's line: the last 💭 entry, or the console's `twin-says` event
   - `localStorage.getItem("pm.demo-builds.v1")`, which gains `:bench-1`; after Make it real, `:real [:bench-1]`;
     after Remove it, `:removed [...]`
   - the console line `Prime Meridian: ground grid 29 x 29 in N ms`, with N recorded (the aim is under ~50).
     If it's over, record it, and how big the grid was.
4. Reload: the bench is still there with its status; `?fresh` clears it.
5. Confirm that the dev build (`watch app`, no bridge) still answers with the no-bridge line.

---

## 9. TASKS.md row (le-docteur, after the gate passes)

Under `## Final project (due Dec 6)`, after F-1:

```
- [x] Nov 25 (crew round demo-build-ask-v0) F-2 Ask the demo's twin to build something (Michael, Nov 25: the win's "build something by talking to your twin"). In the demo build only, "put a bench here" (typed or said in 💭) is parsed from estate data (:demo-builds: four looks, words, places, lines); the ground around the target is measured in the browser (two rays per cell, never shipped) and pm.lot/place picks a level, open spot; the twin says where it went (or why not), and the ghost arrives through the existing blueprint-file path. Make it real and Remove record into localStorage pm.demo-builds.v1 in the demo (estate samples too); reload keeps them, ?fresh clears. Pure pm.ask-build. Open: the grid's timing on slow machines; letters outside a–z split words in the parser.
```

---

## 10. Out of scope

Real agents in the demo; any language model in the browser; new UI controls; walkers avoiding
builds; changing `pm.lot`'s rules.
