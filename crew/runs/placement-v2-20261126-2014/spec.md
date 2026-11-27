# Spec: placement-v2, put it where I'm looking

Quest: `.crew/quests/placement-v2.md`. Its "Decided" list is final. This round has four parts:

1. **"Here" is exact.** The build goes at the view's `:center`, facing the player's camera, with
   no ground check, search, gap/route rule or refusal:
   - Dev: `bb blueprint-draft <member> <draft> --at here|x,z`, backed by the pure
     `pm.view/at-here` and `pm.view/at-xz`.
   - Demo: `pm.ask-build/plan-here`, which skips the grid and `pm.lot/place`.
2. **Facing.** `pm.lot/face-yaw` turns the look's front (its local +z) toward the camera,
   snapped to 45°.
3. **No too-steep gate anywhere.** `seat` still levels the item and stretches its legs, and
   keeps `:seat-drop`. `:too-steep?`, `max-seat-drop-m`, `refuse-steep`, the `:steep` sign
   line, the estate's `:steep` words and the `apply-approved!` gate all go.
4. **No flight for a here build.** `fly-decision` gets a centre rule, so a ghost that lands in
   the middle of the screen doesn't fly. Named places keep v1's flight.

Named places are unchanged: `--near <landmark>`, `lot/place`, the v1 widening, `ab/plan`'s two
passes. `lot/spot`'s `"too steep"` is a search rule for named places (Decided 4), not a gate on
a build, so it stays. `--near here` keeps working as before. The role card simply stops using it.

Out of scope (quest): the ground sampler, walkers avoiding builds, overlap with other builds.

---

## 1. Data shapes (EDN)

```clojure
;; state/view.edn (unchanged shape, written by the bridge from the game's POST /view)
{:as-of 100000 :camera {:pos [2 10 -3] :target [2 1 2]} :center [2.2 1.0 1.9] :walkers {}}
;; :center nil = the middle of the screen is sky

;; an "at" result (pm.view/at-here, pm.view/at-xz): the input to lot/blueprint and lot/at-line
{:ok true :at [2.2 1.0 1.9] :yaw 180 :landmark :here :facing? true}
{:ok true :at [1.5 1.25 2] :yaw 0 :landmark [1.5 2] :facing? false}
{:ok false :reason "the middle of your screen is sky: look at the ground and ask again"}

;; parse-args for --at
{:mode :at :member :member-a :path "d.edn" :at :here :dry-run false}
{:mode :at :member :member-a :path "d.edn" :at [1.5 -2] :dry-run true}

;; a seated blueprint: no :too-steep? key any more
{:id :bench :at [0 1.49 0] :yaw 0 :summary "…" :look {…} :seated? true :seat-drop 1.26}
```

`resources/estate.edn`: delete the `:steep` line from `:blueprint-text`. Nothing else changes
(`:build-rules` is untouched).

---

## 2. pm.lot (`src/pm/lot.cljc`)

```clojure
(def here-word "where you're looking")   ; view-landmarks' :here word now comes from this (same text)
```

**`(face-yaw at camera)` → integer degrees, one of 0 45 90 … 315.** `at` and `camera` are
`[x y z]`. Let `dx = cx − x` and `dz = cz − z`.
- If either argument isn't 3 numbers, or `dx² + dz² < 1e-12` (the camera straight above), it
  returns `0`.
- Otherwise `θ = atan2(dx, dz)` in degrees, and the result is `(mod (* 45 (round* (/ θ 45))) 360)`.
  `round*` is the file's own (`Math/round` / `js/Math.round`: half rounds toward +∞ on both, so
  bb and cljs agree).

Convention: with the file's `turn`, local +z at yaw θ points along world `[sin θ, cos θ]`. So
`(turn [0 1] (face-yaw at cam))` is the snapped unit vector from `at` toward the camera.
**A look's front is its local +z** (the demo bench's back sits at z −0.16, so the sitter faces +z).

**`(at-line draft result)` → string.** It is
`(str "placed " (name (:id draft)) (when (= :here (:landmark result)) " where you're looking,") " at [" (fmt x) " " (fmt y) " " (fmt z) "] yaw " (fmt yaw) (when (:facing? result) ", facing the camera"))`.

**`parse-args`.** Three new accepted forms (`v` is `"here"` → `:here`, or `(coord-near v)` →
`[x z]`; anything else is usage):
- `[member path "--at" v]` → `{:mode :at :member (keyword member) :path path :at <v> :dry-run false}`
- `[member path "--at" v "--dry-run"]` → the same with `:dry-run true`
- `[member path "--dry-run" "--at" v]` → the same with `:dry-run true`

Every old form is unchanged. `usage` becomes:
`"usage: bb blueprint-draft <member> <draft.edn> --near <landmark|here|walker|x,z> [--dry-run] | bb blueprint-draft <member> <draft.edn> --at <here|x,z> [--dry-run] | bb blueprint-draft --landmarks"`.

Unchanged: `place`, `spot`, `refusal`, the v1 helpers and everything else.

---

## 3. pm.view (`src/pm/view.cljc`): reading the view for `--at`

```clojure
(def here-ms 30000)        ; --at here refuses a view older than this
(def keep-alive-ms 10000)  ; was 30000: a still camera re-posts every 10 s, so an open game's view is never near 30 s old
(def here-stale "I can't see where you're looking: the game's view is more than 30 s old; look at the ground and ask again")
(def here-sky   "the middle of your screen is sky: look at the ground and ask again")
```

**`(at-here raw now)` → an at result.** `raw` is the parsed `state/view.edn` (or nil); `now` is
in ms. The first match wins:
1. `raw` isn't a map, or `(:as-of raw)` isn't a number → `{:ok false :reason no-view}` (the existing `lot/no-view` text)
2. `(> (- now as-of) here-ms)` → `{:ok false :reason here-stale}` (exactly 30 000 ms old is still fine)
3. `(:center raw)` fails `vec3?` (nil = sky) → `{:ok false :reason here-sky}`
4. otherwise → `{:ok true :at (vec center) :yaw (lot/face-yaw center pos) :landmark :here :facing? (vec3? pos)}`,
   with `pos = (get-in raw [:camera :pos])` (a missing camera gives yaw 0 and `:facing? false`).

**`(at-xz [x z] ground raw now)` → an at result, always `:ok true` (no checks).**
- `y` is `(:y (lot/cell ground [x z]))` when `ground` is a map and that is a number, else `0`.
  (The game seats it on the real ground anyway.)
- The view "faces" when `raw` is a map, `:as-of` is a number, `(<= (- now as-of) here-ms)`,
  and `pos` passes `vec3?`.
- Result: `{:ok true :at [x y z] :yaw (if faces (lot/face-yaw [x y z] pos) 0) :landmark [x z] :facing? faces}`.

`fresh`, `fresh-ms` and `where-lines` are unchanged. (`--near here` and `bb where` still use the
2-minute rule.)

### `scripts/blueprint_draft.clj`: the `:at` mode

- Add a `:at` case next to `:draft`:
  ```clojure
  :at
  (let [{:keys [path member at dry-run]} parsed
        draft  (or (read-edn path) (fail! (str "can't read " path)))
        raw    (read-edn (view-file))
        result (if (= :here at) (view/at-here raw (System/currentTimeMillis))
                                (view/at-xz at ground raw (System/currentTimeMillis)))]
    (if-not (:ok result)
      (fail! (:reason result))
      ;; then exactly the :draft success path: lot/blueprint, bf/problems, dry-run or bb blueprint-add,
      ;; but print (lot/at-line draft result) instead of placed-line/moved-line
      ...))
  ```
- No ground map is needed for `--at` (`ground` may be nil). The `:landmarks` and `:draft` modes
  are unchanged. Factor the shared "validate, add, print" tail into one local fn if you like.
- A refusal is the one stderr line `blueprint-draft: <reason>`, exit 1. Usage errors exit 2.
- Header comment: add the `--at <here|x,z>` line. "--at puts the draft's base exactly at the
  middle of the player's screen (state/view.edn's :center, at most 30 s old) or at x,z, its
  front (+z) turned to face the camera; no ground checks."

---

## 4. The demo's here path (`src/pm/ask_build.cljc`, `src/pm/core.cljs`)

**`(ab/here? asked)` → boolean.** `(= :here (get-in asked [:req :near]))`. A help or sky result
(no `:req`) gives false.

**`(ab/plan-here ctx req)` → the same shapes as `plan`.** It never measures or calls `lot/place`.
`ctx` is `plan`'s ctx plus `:camera` (`[x y z]` or nil). The first match wins:
1. `(get-in demo [:looks (:look req)])` is nil → `{:ok false :say (get-in demo [:lines :help])}`
2. `(:here ctx)` isn't 3 numbers → `{:ok false :say (get-in demo [:lines :sky])}`
3. Otherwise:
   - `id = (next-id look (taken-ids world file))`
   - `draft = {:id id :summary (:summary look-def) :look (:look look-def)}`
   - `bp = (assoc (lot/blueprint draft {:at (vec here) :yaw (lot/face-yaw here (:camera ctx))}) :by by)`
   - result: `{:ok true :id id :blueprint bp :file (add-build file bp now) :say (fill (get-in demo [:lines :placed]) {:summary <summary minus a trailing "."> :place lot/here-word})}`

`ask`, `plan` and `widen?` are unchanged. `ask` still returns `:spec`/`:wide-spec` for `:here`;
`demo-ask!` just doesn't use them.

**`demo-ask!` in pm.core:** after `r (ab/ask ctx text)` and the `:say` branch:
```clojure
(if (ab/here? r)
  (let [p (ab/plan-here (assoc ctx :camera (when h (:pos (t/rig-view h)))) (:req r))]
    (when (:ok p) (reset! !demo-file (:file p)) (save-demo-file!) (demo-merge!))
    (twin-reply! (:say p) at))
  <placement-v1's two-pass measure/plan block, unchanged>)
```
`:here` in `ctx` is already `t/center-hit` on the ground meshes. That is the same point the dev
build posts as `:center`.

---

## 5. No too-steep gate (`src/pm/blueprint.cljs`, `src/pm/core.cljs`, estate)

- **`bp/seat`:** same signature and the same levelling/stretching maths. The returned bp gets
  `:at`, `:seated? true` and `:seat-drop most`, and **no `:too-steep?` key**. Update the
  docstring and the section comment above it (no "won't build").
- **Delete** `bp/max-seat-drop-m` and `bp/refuse-steep`.
- **`bp/sign-text`:** drop `steep` from the destructure and its `cond` branch. A pending ghost's
  second line is `:hint` whatever its `:seat-drop`.
- **`pm.core/apply-approved!`:** delete the `(and blueprint (:too-steep? blueprint))` branch.
  `breaks-build?` and `:else` are unchanged. The make-real paths (`real-click!`, `make-real!`,
  `demo-make-real!`, the bridge's `/real`) have no steep check today, so once the approval
  isn't refused, Make it real works.
- **Estate:** remove `:steep` from `:blueprint-text`.
- Nothing else reads `:too-steep?` (checked with grep across src/, scripts/ and test/).
  `merge-file` already strips file keys, so a stale `:too-steep?` in a file is dropped as before.

---

## 6. No flight for a here build (`src/pm/camera.cljs`, `src/pm/core.cljs`)

```clojure
(def center-m 1.0)   ; a new ghost this close (xz) to the screen's centre point is "here": no flight
```

`fly-decision`'s `m` gains an optional `:center` (`[x y z]` or nil). A new rule goes **after
`:player-moving` and before `:in-view`**:

> `:on-screen?`, `:center` finite 3 numbers, and the xz distance ghost↔center `<= center-m` → `:at-center`

Every other rule and the order are unchanged. Without `:center`, the result is exactly v1's.
`show-defaults` doesn't change.

`show-arrival!` passes `:center (t/center-hit (.-camera h) (view-ground-meshes! h))`.

Effect:
- A here ghost, dev or demo, lands at the centre point: `:at-center`, no flight.
- A named-place ghost is usually away from the centre, so v1's rules decide.
- If he panned away before the 5 s poll, it isn't at the centre any more, and v1's rules
  decide (fly if off screen).

---

## 7. Role card and PLAY.md (le-docteur, verbatim)

### `.crew/roles/builder.md`: replace the whole "## Build requests from the player (💭)" section with

```markdown
## Build requests from the player (💭)

The player can message you from the estate's 💭 phone; the bridge types his words into your
pane as a plain prompt. When he asks for a thing in the world ("a bench here", "a lamp by the
mailbox"), that is a build request: answer it with a blueprint, never by editing
`resources/estate.edn` or any source file. A build request is **two commands, no more**:

1. Write a draft in your scratchpad: `{:id :<new-kebab-id> :summary "proposed: <a few words>"
   :look {:parts [...]}}`, parts as documented under `:blueprints` in `resources/estate.edn`
   (shapes box/cylinder/cone/sphere; colours from the palette keys used there, e.g. `:brown`
   for timber, `:tin` for metal). Real-world size, y 0 is the ground, every leg or base
   touching y 0, **its front toward +z** (a bench's seat side, a sign's face), at most 12
   parts, nothing thinner than 0.08 m (he has to click it). Ids must be new: never reuse a
   removed one.
2. **One** `bb blueprint-draft`:
   - **"here", or no place at all:** `bb blueprint-draft <your-name> <draft.edn> --at here`. It
     goes exactly in the middle of his screen, turned to face his camera: no ground checks, no
     moving it. If Hal gives you a spot, `--at x,z` does the same there. It refuses only when the
     game's view is missing or more than 30 s old, or he's looking at the sky.
   - **A named place** ("by the mailbox", "by the creek", "by you"):
     `bb blueprint-draft <your-name> <draft.edn> --near <landmark>` (`bb blueprint-draft
     --landmarks` lists them; a walker id for "by you / by him"). It finds level ground nearby,
     up to 15 m, and prints a second line if it had to move.

   The ghost appears within 5 s and "drew a blueprint" lands in 💭 by itself.
3. **One** `bb hal-say --as <your-name> "<line>"`: a few words on what you drew; if it moved,
   its second printed line; if it refused, the reason ("look at the ground and ask again").

Don't read or script around the ground map or the view (`state/ground.edn`,
`state/view.edn`, `python3`, `node`, `bb -e`): the command already reports where it went or
why not. If a build looks wrong where it landed, he removes it and asks again. Then reply in
your pane with the command's printed line(s). Don't propose more than he asked for.
```

### `PLAY.md`: replace the "- **Build:**" bullet with

```markdown
- **Build:** Hal can build a **bench**, a **lamp post**, a **table** or a **planter**.
  Say **here** (or no place at all) and it goes exactly in the middle of your screen, turned to
  face you, on any ground: a slope just gets longer legs. Or name a place, the **mailbox**, the
  **phone booth**, the **workshop** or the **creek**, and he finds a level spot near it and the
  camera flies over to show you. Example: *"a bench here"*. If it doesn't look right, remove it,
  move a little and ask again.
```

---

## 8. Who touches what

| Member | May edit | Must not |
|---|---|---|
| **un-automate-monde** (builder) | `src/pm/lot.cljc`, `src/pm/view.cljc`, `src/pm/ask_build.cljc`, `src/pm/blueprint.cljs`, `src/pm/camera.cljs`, `src/pm/core.cljs`, `scripts/blueprint_draft.clj`; `resources/estate.edn` **only** to delete `:blueprint-text :steep`; writes `crew/runs/placement-v2-20261126-2014/builder-notes.md` | anything under `test/`, `.crew/`, `PLAY.md`, `docs/` |
| **le-testeur** (tester) | new file `test/pm/placement_v2_test.cljs` (ns `pm.placement-v2-test`); the old-test edits listed in §10, and only those | `src/`, `scripts/`, `resources/`, any other line of an existing test |
| **le-docteur** (docs) | `.crew/roles/builder.md` "Build requests from the player (💭)" section and `PLAY.md`'s "Build" bullet, both verbatim from §7 | everything else |

---

## 9. Test cases (`test/pm/placement_v2_test.cljs`)

Requires `[cljs.test :refer [deftest is]]`, `[clojure.string :as str]`, `[cljs.reader :as reader]`,
`[pm.lot :as lot]`, `[pm.view :as view]`, `[pm.ask-build :as ab]`, `[pm.blueprint :as bp]`,
`[pm.camera :as camera]`, `[pm.blueprint-file :as bf]`, `[pm.world :as w]`.
Numbers are compared within 1e-9 where marked ≈.

### Fixtures

```clojure
(def V   {:as-of 100000 :camera {:pos [2 10 -3] :target [2 1 2]} :center [2.2 1.0 1.9] :walkers {}})
(def GB  {:origin [0 0] :step 0.5 :cols 9 :rows 9                         ; all bushes: --at never looks
          :kinds (vec (repeat 9 "BBBBBBBBB")) :y (vec (repeat 9 (vec (repeat 9 1.25))))})
(def RAW (reader/read-string w/estate-edn))
(def DB  (:demo-builds RAW))
(def RULES (:build-rules RAW))
(def BENCH (get-in DB [:looks :bench :look]))
(def E0  {:demo-builds DB :mirrors [] :walkers []})
(def Em  (assoc E0 :mailbox {:at [4 1 -8]}))
(defn hctx [estate world here camera]
  {:estate estate :world world :file ab/empty-file :rules RULES :here here :camera camera :by :tw :now 5000})
(defn steep-bench []   ; pm.blueprint-seat-test's bench: feet at local x ±0.45
  {:id :bench :at [0 0 0] :yaw 0 :summary "proposed: a bench"
   :look {:parts [{:shape :box :size [1.1 0.1 0.4] :at [0 0.45 0] :color :brown}
                  {:shape :box :size [0.08 0.45 0.08] :at [0.45 0.225 0] :color :brown}
                  {:shape :box :size [0.08 0.45 0.08] :at [-0.45 0.225 0] :color :brown}]}})
```

### A. Facing the camera (`lot/face-yaw`)

1. At `[0 0 0]`, the eight compass cameras give:
   `[0 0 10]` → 0, `[10 0 10]` → 45, `[10 0 0]` → 90, `[10 0 -10]` → 135,
   `[0 0 -10]` → 180, `[-10 0 -10]` → 225, `[-10 0 0]` → 270, `[-10 0 10]` → 315.
2. The front faces the camera: for each case in 1, `(lot/turn [0 1] yaw)` ≈ the unit xz vector
   toward the camera. For example 45 gives `[0.7071067811865476 0.7071067811865476]`, 135 gives
   `[0.7071… −0.7071…]` and 270 gives `[−1 0]`. Use 1e-9.
3. Snapping: at `[0 1 0]`, camera `[10 5 4]` → 90 (atan2(10,4) = 68.20°, /45 = 1.52 → 2);
   camera `[10 5 5]` → 45 (63.43°, /45 = 1.41 → 1).
4. Height is ignored: camera `[0 50 10]` at `[0 1 0]` → 0.
5. Degenerate cases: camera straight above, `(lot/face-yaw [2 1 3] [2 30 3])` → 0. Also
   `(lot/face-yaw [2 1 3] nil)` → 0 and `(lot/face-yaw nil [0 0 10])` → 0.
6. Not at the origin: `(lot/face-yaw [2.2 1.0 1.9] [2 10 -3])` → 180
   (dx −0.2, dz −4.9, atan2 = −177.66°, /45 = −3.95 → −4, so −180, mod 360 = 180).
7. `lot/here-word` = `"where you're looking"`. `(:word (:here (lot/view-landmarks V {})))` is still that text.

### B. `--at here`: the view (`view/at-here`)

8. Fresh view: `(view/at-here V 110000)` = `{:ok true :at [2.2 1.0 1.9] :yaw 180 :landmark :here :facing? true}`.
   This is exactly `:center`, unmoved.
9. Boundary: `(view/at-here V 130000)` (exactly 30 s old) → `:ok true`, the same map as 8.
10. Stale: `(view/at-here V 130001)` = `{:ok false :reason view/here-stale}`. Also
    `view/here-stale` = `"I can't see where you're looking: the game's view is more than 30 s old; look at the ground and ask again"`.
11. Missing view: `(view/at-here nil 110000)`, `(view/at-here "junk" 110000)` and
    `(view/at-here (dissoc V :as-of) 110000)` each = `{:ok false :reason lot/no-view}`
    (`"I can't see where you're looking: the game isn't sending its view"`).
12. Sky: `(view/at-here (assoc V :center nil) 110000)` = `{:ok false :reason view/here-sky}`. Also
    `view/here-sky` = `"the middle of your screen is sky: look at the ground and ask again"`.
    `(view/at-here (assoc V :center [1 2]) 110000)` gives the same.
13. Order (stale before sky): `(view/at-here (assoc V :center nil) 200000)` → `:reason view/here-stale`.
14. No camera: `(view/at-here (assoc V :camera nil) 110000)` = `{:ok true :at [2.2 1.0 1.9] :yaw 0 :landmark :here :facing? false}`.
15. `view/here-ms` = 30000. `view/fresh` is unchanged: `(view/fresh V 200000)` = `{:ok true :view V}` (100 s < 120 s).

### C. `--at x,z` (`view/at-xz`)

16. With the ground map and a fresh view: `(view/at-xz [1.5 2] GB V 110000)` =
    `{:ok true :at [1.5 1.25 2] :yaw 180 :landmark [1.5 2] :facing? true}`.
    The cell is a bush (B) and it still places. Yaw: dx 0.5, dz −5, atan2 = 174.29°, which
    snaps to 180.
17. No ground map: `(view/at-xz [1.5 2] nil V 110000)` = `{:ok true :at [1.5 0 2] :yaw 180 :landmark [1.5 2] :facing? true}`.
18. No view: `(view/at-xz [1.5 2] GB nil 110000)` = `{:ok true :at [1.5 1.25 2] :yaw 0 :landmark [1.5 2] :facing? false}`.
    A stale view gives the same: `(view/at-xz [1.5 2] GB V 200000)`.
19. Off the grid: `(view/at-xz [50 50] GB V 110000)` = `{:ok true :at [50 0 50] :yaw 225 :landmark [50 50] :facing? true}`
    (dx −48, dz −53, atan2 = −137.83°, /45 = −3.06 → −3, so −135, which is 225).

### D. The command's pure parts (`lot/parse-args`, `lot/at-line`, `lot/blueprint`)

20. `(lot/parse-args ["member-a" "d.edn" "--at" "here"])` = `{:mode :at :member :member-a :path "d.edn" :at :here :dry-run false}`.
21. `(lot/parse-args ["member-a" "d.edn" "--at" "1.5,-2" "--dry-run"])` = `{:mode :at :member :member-a :path "d.edn" :at [1.5 -2] :dry-run true}`.
22. `(lot/parse-args ["member-a" "d.edn" "--dry-run" "--at" "here"])` = `{:mode :at :member :member-a :path "d.edn" :at :here :dry-run true}`.
23. Each of these gives `{:usage lot/usage}`: `["member-a" "d.edn" "--at" "mailbox"]`,
    `["member-a" "d.edn" "--at"]`, `["member-a" "d.edn" "--at" "here" "--near" "creek"]`,
    `["member-a" "d.edn" "--at" "here" "--fast"]` and `["Member" "d.edn" "--at" "here"]`.
24. The old forms are unchanged: `(lot/parse-args ["member-a" "d.edn" "--near" "creek"])` =
    `{:mode :draft :member :member-a :path "d.edn" :near :creek :dry-run false}`. Also
    `(str/includes? lot/usage "--at <here|x,z>")` → true.
25. `(lot/at-line {:id :bench-3} (view/at-here V 110000))` = `"placed bench-3 where you're looking, at [2.2 1 1.9] yaw 180, facing the camera"`.
26. `(lot/at-line {:id :bench-3} (view/at-xz [1.5 2] nil nil 0))` = `"placed bench-3 at [1.5 0 2] yaw 0"`.
27. The blueprint is exact: `(lot/blueprint {:id :bench-3 :summary "proposed: a bench" :look BENCH} (view/at-here V 110000))` =
    `{:id :bench-3 :at [2.2 1.0 1.9] :yaw 180 :summary "proposed: a bench" :look BENCH}`, and `bf/valid?` of it is true.

### E. The demo's here path (`ab/here?`, `ab/plan-here`)

28. `(ab/here? (ab/ask (hctx E0 {} [0 1 0] nil) "put a bench here"))` → true. The same with
    `"a bench please"` (no place word means here) → true. With
    `(ab/ask (hctx Em {} [0 1 0] nil) "a bench by the mailbox")` → false.
    `(ab/here? {:say "x"})` → false and `(ab/here? nil)` → false.
29. Places at the centre, facing the camera:
    `(ab/plan-here (hctx E0 {} [1.5 1.2 -2] [1.5 6 8]) {:look :bench :near :here})` =
    `{:ok true :id :bench-1 :blueprint {:id :bench-1 :at [1.5 1.2 -2] :yaw 0 :summary "A bench" :look BENCH :by :tw} :file {:as-of 5000 :blueprints [<that blueprint>] :real [] :removed []} :say "A bench, drawn near where you're looking. Try it, and make it real if you like it."}`.
30. Another facing: camera `[-8 6 -2]` (dx −9.5, dz 0) → `(:yaw (:blueprint p))` = 270, and `(:at (:blueprint p))` is still `[1.5 1.2 -2]`.
31. No search and no gap: an existing blueprint at the very spot,
    `world = {:blueprints [{:id :bench-1 :at [1.5 1.2 -2] :summary "x"}]}`, gives `(:ok p)` true,
    `(:id p)` `:bench-2` and `(:at (:blueprint p))` `[1.5 1.2 -2]`.
32. No camera: `(hctx E0 {} [1.5 1.2 -2] nil)` → `(:yaw (:blueprint p))` = 0.
33. Sky: `(ab/plan-here (hctx E0 {} nil [0 5 5]) {:look :bench :near :here})` =
    `{:ok false :say "The middle of your screen is sky. Look at the ground and ask me again."}`.
34. Unknown look: `{:look :rocket :near :here}` → `{:ok false :say (get-in DB [:lines :help])}`.
35. Named places still measure: `(ab/ask (hctx Em {} [0 1 0] nil) "a bench by the mailbox")` =
    `{:req {:look :bench :near :mailbox} :target [4 -8] :spec {:origin [-3 -15] :step 0.5 :cols 29 :rows 29} :wide-spec {:origin [-12 -24] :step 0.5 :cols 65 :rows 65}}`.
    Hand check, wide: h = 16. x0 = 0.5·floor(−24) = −12 and x1 = 20, so cols = 1 + 64 = 65.
    z0 = 0.5·floor(−48) = −24 and z1 = 8, so rows = 65.

### F. No too-steep gate (`bp/seat`, `bp/sign-text`, approve, make-real)

36. A steep bank still seats: `b = (bp/seat (steep-bench) (fn [x _] (+ 0.86 (* 1.4 x))))`. Then:
    - `(:at b)` ≈ `[0 1.49 0]` (the +x foot's ground is 0.86 + 0.63);
    - `(:seat-drop b)` ≈ 1.26 (the −x foot's ground is 0.23);
    - `(:seated? b)` is true;
    - `(contains? b :too-steep?)` is false.
37. The low leg reaches the ground: in `b` from 36, the −x foot (part index 2) has size y ≈ 1.71
    (0.45 + 1.26) and at y ≈ −0.405 (0.225 − 0.63). Its bottom,
    `(+ 1.49 -0.405 (- (/ 1.71 2)))`, ≈ 0.23. The +x foot is unchanged:
    `[0.08 0.45 0.08]` at `[0.45 0.225 0]`.
38. The sign is just the hint: with
    `w = (bp/seat-all {:blueprint-text {:hint "click to build" :steep "too steep here"} :blueprints [(steep-bench)]} (fn [x _] (* 1.4 x)))`
    and `bk = (first (bp/pending w))`: `(:seat-drop bk)` ≈ 1.26, and `(bp/sign-text w bk)` =
    `"proposed: a bench\nclick to build"`.
39. It builds and can be made real: `(bp/status (bp/approve-blueprint w :bench) :bench)` → `:approved`,
    then `(bp/status (bp/make-real (bp/approve-blueprint w :bench) :bench) :bench)` → `:real`.
40. The estate's words lost `:steep`: `(contains? (:blueprint-text RAW) :steep)` → false. The other
    keys are still there, for example `(get-in RAW [:blueprint-text :hint])` = `"click to build · right-click to dismiss"`.
41. Seating flat ground is unchanged apart from the key:
    `(bp/seat (steep-bench) (fn [_ _] 1.5))` gives `:at` `[0 1.5 0]`, `:seat-drop` 0, and no `:too-steep?`.

### G. No flight for a here ghost (`camera/fly-decision`)

`base = {:ghost [10 1 0] :camera [0 5 0] :on-screen? true :card-open? false :last-move-ms nil :now-ms 100000}`
(the camera is 10.77 m away, so v1 alone would say `:fly`).

42. `camera/center-m` = 1.0.
43. `(camera/fly-decision nil (assoc base :center [10.3 1 0.4]))` → `:at-center` (xz distance 0.5).
44. Boundary: `:center [11 1 0]` → `:at-center` (distance exactly 1). `:center [12 1 0]` → `:fly`.
45. xz only: `:center [10 50 0]` → `:at-center`.
46. Off screen: `(assoc base :on-screen? false :center [10 1 0])` → `:fly`.
47. Order: `(assoc base :center [10 1 0] :card-open? true)` → `:card-open`;
    `(assoc base :center [10 1 0] :last-move-ms 99500)` → `:player-moving`;
    `(assoc base :ghost [3 1 4] :camera [0 1 0] :center [20 1 20])` → `:in-view` (v1's rule, centre far).
48. v1 is unchanged without `:center`: `(camera/fly-decision nil base)` → `:fly`, and
    `(camera/fly-decision nil (assoc base :center nil))` → `:fly`.

### H. Named places unchanged (`lot/place`)

49. `(lot/place GB {:well {:at [2 1 2]}} [] {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1} {:id :s :summary "s" :look {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0]}]}} :well)` =
    `{:ok false :reason "no level open ground within 1 m of the well: most of it is bushes or walls"}`.
    A named place still checks the ground.

---

## 10. Existing tests whose expectations change

These are the only old-test edits:

- **`test/pm/blueprint_seat_test.cljs`**
  - `s01-flat-ground-lifts-to-the-ground-and-keeps-the-parts`: replace
    `(is (false? (:too-steep? b)))` with `(is (not (contains? b :too-steep?)))`.
  - `s02-slope-levels-at-the-high-foot-and-stretches-the-low-one`: the same replacement. The
    `:seat-drop` 0.09 assertion stays.
  - `s03-the-bank-is-too-steep`: it asserted `(:too-steep? b)` true and
    `(> (:seat-drop b) bp/max-seat-drop-m)`, and that var is deleted. Rename it
    `s03-the-bank-still-seats` and assert `(:at b)` ≈ `[0 1.49 0]`, `(:seat-drop b)` ≈ 1.26,
    `(:seated? b)` true and `(not (contains? b :too-steep?))` (the §9 #36 values).
  - `s08-seat-all-and-the-steep-sign-and-caption`: it asserted `:too-steep?` true, the sign
    `"proposed: a bench\ntoo steep here"`, and `refuse-steep`'s caption and status (the fn is
    deleted). Rename it `s08-seat-all-a-steep-ghost-signs-and-builds-like-any-other` and assert
    the sign is `"proposed: a bench\nclick to build"` and approve → `:approved` (the §9 #38–39 values).
- **`test/pm/blueprint_live_test.cljs`**
  - `s1-seat-ids-seats-only-the-given-id`: the expected
    `(assoc (fbp CB) :at [1.0 1.5 -9.0] :seated? true :seat-drop 0 :too-steep? false)` drops
    `:too-steep? false`.
  - `s2-seat-ids-second-blueprint`: the same for
    `(assoc (fbp PL) :at [-2.0 1.5 -4.0] :seated? true :seat-drop 0 :too-steep? false)`.
  - `m11-file-keys-stripped`: **unchanged**. Its input still carries `:too-steep? true`, and
    `merge-file` still strips it.
- **`test/pm/view_test.cljs`** `v05-post?`: `keep-alive-ms` goes from 30000 to 10000. Line 77,
  `(false? (v/post? st S0 30999 on))`, becomes `(false? (v/post? st S0 10999 on))`. Line 78,
  `(true? (v/post? st S0 31000 on))`, becomes `(true? (v/post? st S0 11000 on))`
  (`st` was sent at 1000).

Checked and **unchanged**:
- `lot_test` t17, t19, t20 and t30, and `placement_test` #4, #5: lot's `"too steep"` is the
  named-place search rule, kept by Decided 4.
- `lot_test` t40/t41: they compare against the `lot/usage` var, not a literal.
- `ask_build_test`: `ask`, `plan` and `widen?` are unchanged.
- `placement_test` #38–#48: `fly-decision` without `:center` is v1's.

---

## 11. Notes for the builder (and Done when)

- **Hidden tab.** While the game's tab is hidden or covered, its view timer may run only about
  once a minute. A `--at here` then can refuse as stale. That is the decided 30 s rule. The
  lower `keep-alive-ms` covers the usual case (the game in view, camera still). Say so in the
  builder notes if the live check hits it.
- **Done when:** the gate passes (0 warnings, §9 #1–49 and the §10 edits). `builder-notes.md`
  says how Hal checks it live:
  - look at a spot (a slope by the lobby is a good one);
  - run `bb where` (the centre point);
  - run `bb blueprint-draft <member> <draft> --at here --dry-run`, then without `--dry-run`,
    and read the printed line;
  - check `state/blueprints.edn`: `:at` is exactly the centre, and `:yaw` faces the camera;
  - the ghost appears in the middle of the screen, facing him, and the camera doesn't fly
    (console `camera: at-center`);
  - click it, then Make it real, on the slope: no "too steep" anywhere, and the legs stretch;
  - in the demo build, "a bench here" in 💭 does the same with no grid in the console.
