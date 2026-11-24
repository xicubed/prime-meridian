# Spec: view share v0 (TASKS T-19)

Quest: `.crew/quests/view-share-v0.md`. Start commit `6b469db`.
The game posts the player's camera, the ground spot at the screen's centre, and every walker's
position to the bridge's new `POST /view`. The bridge writes the private `state/view.edn`. Then
`bb blueprint-draft … --near here|<walker-id>` and the new `bb where` can read it, so the twin no
longer needs a screenshot to know where the player is looking. Nothing new appears on screen.

Everything the quest records as decided stands. This spec fixes the names, shapes, thresholds,
messages and order of checks, and works the test values out by hand.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/view.cljc` (new), `src/pm/bridge.cljc` (the `/view` route, `view-uri`, `log-line`'s action set), `src/pm/three.cljs` (`rig-view`, `center-hit`), `src/pm/core.cljs` (the frame-loop `view-tick!` and its post), `src/pm/lot.cljc` (§5 additions only; every existing fn keeps its behaviour), `scripts/crew_bridge.clj` (the `:view` branch), `scripts/blueprint_draft.clj` (reads the view), `scripts/where.clj` (new), `bb.edn` (the `where` task only); in the run dir: `builder-notes.md` | `test/`, `resources/`, `docs/`, `TASKS.md`, `src/pm/camera.cljs`, `src/pm/bricks.cljs`, `src/pm/crew.cljs`, `state/` (never written by hand tests: use temp files with `PM_VIEW_FILE`, `PM_GROUND_FILE`, `PM_BLUEPRINTS_FILE`). Don't start or restart the bridge |
| **tester** (le-testeur) | `test/pm/view_test.cljs` (new, ns `pm.view-test`); **add** `t43`–`t49` to `test/pm/lot_test.cljs`; **add** `t05`–`t08` to `test/pm/bridge_real_test.cljs` | every existing assertion; everything else. Tests are pure: no files, no three.js |
| **le-docteur** | `TASKS.md`: the T-19 row (§9), after the gate passes | everything else |

Hal updates the role cards himself. Builder and tester work in parallel from this spec only.

---

## 2. Data shapes

### 2.1 The POST body (game → bridge, JSON)

```json
{"token": "…", "action": "view",
 "camera":  {"pos": [1.23, 5.68, -10], "target": [0, 0, -2.35]},
 "center":  [1.5, 1.7, -8.2],
 "walkers": [{"id": "b-walker", "pos": [-5, 1.6, -14]}, {"id": "hal", "pos": [2.5, 1.8, -7]}]}
```
`"center"` is `null` when the centre ray hits no ground mesh. Walkers are sorted by id. Every number is
rounded to the centimetre. The `"center"` key is always present, even when it is `null`.

### 2.2 A snapshot (inside the game, `pm.view`)

```clojure
{:camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]}
 :center [1.5 1.7 -8.2]                       ; or nil
 :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}
```

### 2.3 `state/view.edn` (gitignored; written only by the bridge)

```clojure
{:as-of 1795550000000
 :camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]}
 :center [1.5 1.7 -8.2]
 :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}
```

### 2.4 Post state (a `defonce` atom in `pm.core`, plain data)

```clojure
{:last nil :last-at nil :in-flight? false :checked-at 0}   ; :last = the snapshot last sent
```

---

## 3. `src/pm/view.cljc` (ns `pm.view`)

Portable (bb + cljs). Pure: no I/O, no atoms, no `js/Date`, no three.js. Requires `clojure.string`
and `pm.lot` (for `cell`, `fmt`, `nearest-landmark`, `no-view`). No `::k` keys.

### 3.1 Constants

```clojure
(def max-coord 2000)          ; metres, the same bound as pm.camera/max-coord
(def max-walkers 32)
(def post-every-ms 2000)
(def keep-alive-ms 30000)
(def camera-move-m 0.05)      ; pos or target moved more than this → changed
(def center-move-m 0.1)
(def walker-move-m 0.25)
(def fresh-ms 120000)
(def eps 1e-9)
(def no-view lot/no-view)     ; "I can't see where you're looking: the game isn't sending its view"
(def stale-view "I can't see where you're looking: the game isn't open")
```

### 3.2 `(vec3? v)` → boolean

True when `v` is sequential and has exactly 3 elements, each a number that is finite
(`#?(:clj (Double/isFinite (double x)) :cljs (js/isFinite x))`) and has `|x| <= max-coord`.

### 3.3 `(round-cm x)` → number

`(/ (Math/round (* x 100)) 100)` (JS `Math.round`, JVM `Math/round`). The result is a double on the JVM.

### 3.4 `(snapshot cam center spots)` → snapshot or nil

- `cam`: `{:pos [x y z] :target [x y z]}` or nil. If it is nil, or either vector fails `vec3?`, the result is **`nil`** (nothing to post).
- `center`: `[x y z]` or nil. It becomes nil unless it passes `vec3?`.
- `spots`: `[{:id kw :pos [x y z] …} …]` (as from `pm.bricks/spots`). Drop any spot whose `:id` is not a keyword or whose `:pos` fails `vec3?`. Sort the rest by `(name :id)` and keep the first `max-walkers`.
- Every number kept is run through `round-cm` (`mapv`).

Result: `{:camera {:pos … :target …} :center c :walkers {id pos …}}` (`:walkers` is a plain map).

### 3.5 `(changed? last snap)` → boolean

Use `d3` for Euclidean distance in 3D. True when any of these holds:
- `last` is nil;
- `d3(pos) > camera-move-m + eps` or `d3(target) > camera-move-m + eps`;
- exactly one of the two centres is nil, or both are present and `d3 > center-move-m + eps`;
- the walker key sets differ, or some walker has `d3 > walker-move-m + eps`.

Otherwise false.

### 3.6 `(post? state snap now opts)` → boolean

`opts` = `{:demo? bool :bridge-on? bool}`. Take the first rule that matches, in this order:

1. `(:demo? opts)` → false
2. `(not (:bridge-on? opts))` → false
3. `(:in-flight? state)` → false
4. `snap` nil → false
5. `(:last-at state)` nil → **true** (the first post)
6. `(- now last-at) < post-every-ms` → false
7. `(changed? (:last state) snap)` → true
8. `(- now last-at) >= keep-alive-ms` → true
9. otherwise → false

### 3.7 `(sent state snap now)` / `(settled state)` → state

`sent` → `(assoc state :last snap :last-at now :in-flight? true)`. Call it at send time, so a refused or failed post is not retried until something changes or the keep-alive comes due.
`settled` → `(assoc state :in-flight? false)`. Call it on every completion, both success and failure.

### 3.8 `(view-url bridge)` → string

`(str "http://127.0.0.1:" (:port bridge) "/view")`.

### 3.9 `(body bridge snap)` → JSON-ready map (string keys)

```clojure
{"token" (:token bridge) "action" "view"
 "camera" {"pos" pos "target" target}
 "center" center                                   ; nil → JSON null
 "walkers" [{"id" (name id) "pos" pos} …]}         ; sorted by id name
```

### 3.10 `(view-edn v now)` → map

`v` is the successful result of `pm.bridge/validate` for `/view` (§4). The result is
`{:as-of now :camera (:camera v) :center (:center v) :walkers (:walkers v)}`.

### 3.11 `(fresh raw now)` → `{:ok true :view raw}` | `{:ok false :reason s}`

- `raw` is not a map, or its `:as-of` is not a number → `{:ok false :reason no-view}`.
- `(- now as-of) > fresh-ms` → `{:ok false :reason stale-view}`. Exactly 120 s still counts as fresh, and a future `:as-of` is fresh too.
- Otherwise → `{:ok true :view raw}`.

### 3.12 `(ground-word ground [x z])` → string

- `ground` not a map → `"ground unknown (no ground map)"`.
- Otherwise, by `(:kind (lot/cell ground [x z]))`: `"o"` → `"open ground"`, `"T"` → `"under trees"`, `"B"` → `"bushes or walls"`, `"R"` → `"rocks"`, `":"` → `"creek bed"`, `"~"` → `"water"`, anything else → `"no ground"`.

### 3.13 `(where-lines view now ground lms)` → vector of strings

`view` is assumed fresh. `lms` is the caller's `(lot/landmarks ground estate file-bps)`: the 3-arity, with no view names. `near(p)` is `(lot/nearest-landmark lms p)`, and its suffix is `(str (lot/fmt d) " m from " word)`.

1. `(str "view " (max 0 (quot (- now as-of) 1000)) " s old")`
2. Centre nil → `"looking at the sky"`. Otherwise, with `[x z]` taken from the centre:
   `(str "looking at [" (fmt x) " " (fmt z) "]: " (ground-word ground [x z]))`, followed by `(str ", " suffix)` when `near` is non-nil.
3. One line per walker, sorted by id name: `(str (name id) " at [" (fmt x) " " (fmt z) "]")`, followed by `(str ": " suffix)` when `near` is non-nil.

---

## 4. `src/pm/bridge.cljc` additions

`pm.bridge` now also requires `pm.view` (cljc).

- `(def view-uri "/view")`.
- `validate`: the route check also accepts `view-uri`. The method, route, json, token and origin guards stay first, in their current order. After the origin check, add a `(= uri view-uri)` branch. Each check below is tried in order, and the first failure wins with status **400**:
  1. `(get body "action")` ≠ `"view"` → `"bad action"`
  2. `camera = (get body "camera")` is not a map, or `(get camera "pos")` or `(get camera "target")` fails `view/vec3?` → `"bad camera"`
  3. `(contains? body "center")` is false, or the value is neither nil nor `vec3?` → `"bad center"`
  4. `(get body "walkers")` is not sequential (a vector, or a seq on the JVM) → `"bad walkers"`
  5. it has more than `view/max-walkers` entries → `"too many walkers"`
  6. any entry is not a map, or its `"id"` is not a string matching `real-id-re`, or its `"pos"` fails `vec3?`, or two entries share an id → `"bad walker"`

  When every check passes, return
  `{:ok true :action :view :camera {:pos (vec pos) :target (vec target)} :center (some-> center vec) :walkers (into {} (map (fn [w] [(keyword id) (vec pos)])))}`.
  Numbers are stored as given; the game has already rounded them.
- `log-line`: the action names it accepts now include `"view"` (the set becomes `#{"hold" "ws" "view"}` alongside `actions`).

## 4b. `scripts/crew_bridge.clj`

- `(def view-file (or (System/getenv "PM_VIEW_FILE") "state/view.edn"))`.
- In `handle`, once `v` is ok and `(:action v)` is `:view`:
  - `(write-edn! view-file (view/view-edn v (System/currentTimeMillis)))`.
  - On success, reply 200 `{"ok" true "result" "seen"}` with `cors-headers`, and **do not log it**.
  - If the write fails, log `(log! nil :view 500 "view not saved")` and reply 500 `{"ok" false "reason" "view not saved"}`.
- Refusals keep going through the existing `log!` path. They now show as `… - view 400 bad camera`.
- The 413 "too large" guard (`max-body` 4096) is unchanged. A body with 32 walkers at about 55 bytes each, plus the camera and token, fits.

---

## 5. `src/pm/lot.cljc` additions

```clojure
(def no-view "I can't see where you're looking: the game isn't sending its view")
(def sky "I can't tell where you're looking: the middle of your screen is sky")
(def usage "usage: bb blueprint-draft <member> <draft.edn> --near <landmark|here|walker|x,z> [--dry-run] | bb blueprint-draft --landmarks")
```
`usage` replaces the current string. `parse-args` is unchanged: `here` and walker ids already parse as keywords.

### 5.1 `(view-landmarks view estate)` → sorted map

- `:here {:word "where you're looking" :xz [x z]}`, when `(:center view)` is non-nil.
- One entry per `[id [x y z]]` in `(:walkers view)`: `{:word w :xz [x z]}`. `w` is the `:name` of the estate walker (`(:walkers estate)`) with that `:id`, else `(words id)`.

`view` nil → `(sorted-map)`.

### 5.2 `(landmarks ground estate file-blueprints view)` (new 4-arity)

Start from the 3-arity result, unchanged. Then add each `view-landmarks` entry whose key is not already present: estate names win. The 3-arity behaves exactly as today.

### 5.3 `(nearest-landmark lms [x z])` → `{:id k :word w :distance-m d}` | nil

Distance works as in `place`: centre to centre, or to the nearest cell for `:cells`. Take the minimum; on a tie the first in `lms` order wins (that is, the smaller name). Empty `lms` → nil.

### 5.4 `(place ground estate file-blueprints rules draft near seen)` (new 7-arity)

`seen` is `(view/fresh raw now)` or nil. The 6-arity is `(place … near nil)`. Steps:

1. `ground` not a map → `no-ground-map`. This is unchanged and still comes first.
2. `view = (when (:ok seen) (:view seen))`; `lms = (landmarks ground estate file-blueprints view)`.
3. `near` is a vector (`x,z`): unchanged. `near` resolves in `lms`: carry on as today, using the landmark's `:xz` or `:cells`.
4. `near` does not resolve:
   - `near` = `:here` and `view` is non-nil (so the centre is nil) → `{:ok false :reason sky}`.
   - `near` = `:here`, or `near` is the `:id` of a walker in `(:walkers estate)`, and `view` is nil → `{:ok false :reason (or (:reason seen) no-view)}`.
   - Otherwise → the current unknown-landmark reason, listing every key of `lms` (view names included).

Walkers and the view's centre are **not** things to keep clear of. `things` is unchanged.

---

## 6. `src/pm/three.cljs` and `src/pm/core.cljs`

- `pm.three/rig-view [^js h]` → `{:pos [x y z] :target [x y z]}` from `(.. h -camera -position)` and `(.. h -rig -orbit -target)`, or nil when there is no rig or orbit. Never throws.
- `pm.three/center-hit [^js camera meshes]` → `[x y z]` of the nearest hit, or nil. Use a `Raycaster` with `.setFromCamera` (Vector2 0 0) and `.intersectObjects (into-array meshes) true`. Empty meshes, no hit, or any exception → nil. Untested (interop); the decision logic lives in `pm.view`.
- `pm.core`:
  - `(defonce ^:private !view (atom {:last nil :last-at nil :in-flight? false :checked-at 0}))`.
  - A ground-mesh cache: `(t/find-meshes (.-scene h) #(m/ground-name? % prefixes))` with `prefixes` = `(get-in @!world [:motion :ground])`, the same set the blueprints are seated on. Find them once, when the vector is non-empty.
  - `view-tick! [h now]`, called right after `camera/save-tick!` in the frame loop:
    - `(when-not DEMO …)`.
    - Return at once unless `(- now (:checked-at @!view)) >= view/post-every-ms`. If it is due, set `:checked-at`, so the raycast runs at most every 2 s.
    - `snap = (view/snapshot (t/rig-view h) (t/center-hit (.-camera h) meshes) (bricks/spots))`.
    - When `(view/post? @!view snap now {:demo? DEMO :bridge-on? (bridge-on?)})`:
      - `swap! view/sent`;
      - `js/fetch (view/view-url b)` as POST, with JSON `(view/body b snap)`;
      - in `.finally` (or in both `.then` and `.catch`), `swap! view/settled`.
    - Nothing is said on screen; errors are ignored.

---

## 7. `scripts/blueprint_draft.clj`, `scripts/where.clj`, `bb.edn`

- `blueprint_draft.clj`:
  - `view-file` = `PM_VIEW_FILE` or `state/view.edn`; a missing or unreadable file reads as nil.
  - `seen = (view/fresh raw (System/currentTimeMillis))`.
  - `--landmarks` prints `(lot/landmark-lines (lot/landmarks ground estate file-bps (when (:ok seen) (:view seen))))`.
  - A draft calls the 7-arity `place` with `seen`. The word for `placed-line` comes from the same 4-arity `lms`.
- `where.clj` (new):
  - Any argument → stderr `usage: bb where`, exit 2.
  - Read the view (`PM_VIEW_FILE`), the ground (`PM_GROUND_FILE` or `state/ground.edn`), `resources/estate.edn` and the file blueprints (`PM_BLUEPRINTS_FILE`).
  - `seen` not ok → stderr `where: <reason>`, exit 1.
  - Otherwise print `(view/where-lines view now ground (lot/landmarks ground estate file-bps))`, one per line, and exit 0.
- `bb.edn`:
  ```clojure
  where {:doc  "Where the player is looking and where everyone is, from the game's view (state/view.edn): bb where"
         :task (let [r (apply shell {:continue true} "bb" "scripts/where.clj" *command-line-args*)]
                 (System/exit (:exit r)))}
  ```
- `builder-notes.md` covers:
  - what was built;
  - hand tests with a temp `PM_VIEW_FILE`: a fresh one, a stale one (`:as-of` 0), a missing one; `bb where` and `blueprint-draft --near here --dry-run` for each, with the output;
  - exactly what Hal runs after restarting the bridge: reload the game, `watch -n 2 cat state/view.edn` (or `bb where` twice) while he orbits the camera.

---

## 8. Test cases

### Fixtures for `test/pm/view_test.cljs`

```clojure
(def S0 {:camera {:pos [0 10 0] :target [0 0 0]} :center [1 1.5 1] :walkers {:a [0 1 0] :b [5 1 5]}})
(def S1 (assoc-in S0 [:camera :pos] [0 11 0]))
(def on {:demo? false :bridge-on? true})
(def GW {:origin [0 0] :step 0.5 :cols 9 :rows 9
         :kinds (conj (vec (repeat 8 "ooooooooo")) ":::::::::")      ; z = 4 is the creek bed
         :y (vec (repeat 9 (vec (repeat 9 1.0))))})
(def EV {:mailbox {:at [3.5 1 2]} :well {:at [0 1 0]}})
(def VV {:as-of 1000000 :camera {:pos [2 10 -5] :target [2 1.7 2]} :center [2 1.7 2]
         :walkers {:hal [3.5 1.8 2.5] :b-walker [0.5 1.6 0.5]}})
```

**v01 vec3?** — true for `[1 2 3]`, `[2000 0 -2000]`, `'(1 2 3)`. False for `[1 2]`, `[1 2 "3"]`, `[2000.01 0 0]`, `[js/NaN 0 0]`, `[js/Infinity 0 0]`, `nil`.

**v02 round-cm** — `1.234` → `1.23`; `-6.527` → `-6.53`; `2` → `2`; `0.004` → `0`.

**v03 snapshot**
- `(snapshot {:pos [1.234 5.678 -9.999] :target [0.001 0 -2.346]} [1.5 1.7049 -8.2] [{:id :hal :agent :hal :name "Hal" :pos [2.501 1.8 -7.0]} {:id :b-walker :pos [-5.0 1.6 -14.0]}])` → `{:camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}`.
- Cam `nil` → `nil`; cam `{:pos [1 2 3000] :target [0 0 0]}` → `nil`.
- Center `[3000 0 0]` → `:center nil`; center `nil` → `:center nil`.
- A spot with `:pos [js/NaN 0 0]` is dropped.
- 33 spots `:w00`…`:w32` (ids `(keyword (str "w" (if (< i 10) "0" "") i)))`, pos `[0 0 0]`) → 32 walkers, `:w00`…`:w31`.

**v04 changed?**
- `(changed? nil S0)` → true; `(changed? S0 S0)` → false.
- pos `[0 10.05 0]` → false (exactly 5 cm); pos `[0 10.06 0]` → true.
- target `[0.03 0 0.03]` → false (0.042); target `[0.04 0 0.04]` → true (0.057).
- center `[1 1.5 1.1]` → false (10 cm); `[1 1.5 1.11]` → true; center `nil` → true.
- `(changed? (assoc S0 :center nil) (assoc S0 :center nil))` → false.
- walker `:a` `[0.2 1 0]` → false; `[0.3 1 0]` → true.
- adding `:c [9 1 9]` → true; dropping `:b` → true.

**v05 post?**
- `(post? {} S0 1000 on)` → true (first post).
- With `st = {:last S0 :last-at 1000}`:
  - `S1` at 2500 → false (throttle);
  - `S1` at 3000 → true;
  - `S0` at 3000 → false;
  - `S0` at 30999 → false;
  - `S0` at 31000 → true (keep-alive).
- `{:demo? true :bridge-on? true}` with `{}` and `S0` → false.
- `{:demo? false :bridge-on? false}` → false.
- `(assoc st :in-flight? true)` with `S1` at 3000 → false.
- `(post? {} nil 1000 on)` → false.

**v06 sent/settled**
- `(sent {:last nil :last-at nil :in-flight? false} S1 3000)` → `{:last S1 :last-at 3000 :in-flight? true}`.
- `(settled that)` → `{:last S1 :last-at 3000 :in-flight? false}`.

**v07 view-url** — `(view-url {:port 8091 :token "t" :as-of 1})` → `"http://127.0.0.1:8091/view"`.

**v08 body and round trip**
- `snap = {:camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center nil :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}`.
- `(body {:port 8091 :token "tok"} snap)` →
  `{"token" "tok" "action" "view" "camera" {"pos" [1.23 5.68 -10] "target" [0 0 -2.35]} "center" nil "walkers" [{"id" "b-walker" "pos" [-5 1.6 -14]} {"id" "hal" "pos" [2.5 1.8 -7]}]}`.
- `(pm.bridge/validate {:method "POST" :uri pm.bridge/view-uri :origin pm.bridge/allowed-origin :body that} {:token "tok"})` →
  `{:ok true :action :view :camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center nil :walkers {:b-walker [-5 1.6 -14] :hal [2.5 1.8 -7]}}`.

**v09 view-edn**
- `C = {:pos [1 2 3] :target [0 0 0]}`.
- `(view-edn {:ok true :action :view :camera C :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7]}} 1795550000000)` →
  `{:as-of 1795550000000 :camera C :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7]}}`.

**v10 fresh**
- `(fresh VV 1000000)` and `(fresh VV 1120000)` → `{:ok true :view VV}`.
- `(fresh VV 1120001)` → `{:ok false :reason "I can't see where you're looking: the game isn't open"}`.
- `(fresh nil 5)`, `(fresh {:camera {}} 5)`, `(fresh "junk" 5)` → `{:ok false :reason "I can't see where you're looking: the game isn't sending its view"}`.

**v11 ground-word**
- On `{:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1 1 1 1 0.2 0.1]]}`:
  - `[0 0]` → `"open ground"`; `[0.5 0]` → `"under trees"`; `[1 0]` → `"bushes or walls"`;
  - `[1.5 0]` → `"rocks"`; `[2 0]` → `"creek bed"`; `[2.5 0]` → `"water"`; `[3 0]` → `"no ground"`.
- `(ground-word nil [0 0])` → `"ground unknown (no ground map)"`.

**v12 where-lines-fresh** — `(where-lines VV 1012500 GW (pm.lot/landmarks GW EV []))` →
```clojure
["view 12 s old"
 "looking at [2 2]: open ground, 1.5 m from the mailbox"   ; mailbox 1.5, creek 2.0, well 2.83
 "b-walker at [0.5 0.5]: 0.71 m from the well"             ; √0.5
 "hal at [3.5 2.5]: 0.5 m from the mailbox"]               ; creek 1.5
```

**v13 where-lines-sky** — `(where-lines (assoc VV :center nil) 1012500 GW (pm.lot/landmarks GW EV []))` → the same, except that line 2 is `"looking at the sky"`.

**v14 where-lines-no-ground-map** — `(where-lines VV 1012500 nil (pm.lot/landmarks nil EV []))` →
`["view 12 s old" "looking at [2 2]: ground unknown (no ground map), 1.5 m from the mailbox" "b-walker at [0.5 0.5]: 0.71 m from the well" "hal at [3.5 2.5]: 0.5 m from the mailbox"]`.

**v15 where-lines-bare** — `(where-lines (assoc VV :walkers {}) 999000 GW {})` → `["view 0 s old" "looking at [2 2]: open ground"]` (a future `:as-of` clamps to 0; no landmarks means no suffix).

### Additions to `test/pm/bridge_real_test.cljs`

```clojure
(def GB {"action" "view" "camera" {"pos" [1.23 5.68 -10] "target" [0 0 -2.35]} "center" [1.5 1.7 -8.2]
         "walkers" [{"id" "hal" "pos" [2.5 1.8 -7]} {"id" "b-walker" "pos" [-5 1.6 -14]}]})
(defn- vreq [body] {:method "POST" :uri b/view-uri :origin b/allowed-origin :body (assoc body "token" "t")})
(defn- ws [n] (vec (for [i (range n)] {"id" (str "w" i) "pos" [0 0 0]})))
```

**t05 validate-view-good**
- `(b/validate (vreq GB) ctx)` → `{:ok true :action :view :camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]} :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}`.
- `"center" nil` → ok with `:center nil`.
- `"walkers" []` → ok with `:walkers {}`.
- `"walkers" (ws 32)` → ok, with 32 walkers.

**t06 validate-view-refusals** — each case is `{:ok false :status 400 :reason r}`:

| Body change | Reason |
|---|---|
| `"action" "look"` | `"bad action"` |
| no `"camera"` | `"bad camera"` |
| camera pos `[1 2]` | `"bad camera"` |
| camera target `[0 0 2001]` | `"bad camera"` |
| camera pos `[js/NaN 0 0]` | `"bad camera"` |
| no `"center"` key | `"bad center"` |
| center `[0 0]` | `"bad center"` |
| center `"here"` | `"bad center"` |
| no `"walkers"` | `"bad walkers"` |
| walkers `"x"` | `"bad walkers"` |
| walkers `(ws 33)` | `"too many walkers"` |
| a walker with id `"Hal"` | `"bad walker"` |
| a walker with id `"../x"` | `"bad walker"` |
| a walker with pos `[0 0]` | `"bad walker"` |
| a walker that is `"hal"`, not a map | `"bad walker"` |
| two walkers with id `"hal"` | `"bad walker"` |

**t07 validate-view-guards**
- Token `"no"` → `{:ok false :status 401 :reason "bad token"}`.
- Origin `"http://evil.test"` → `{:ok false :status 403 :reason "bad origin"}`.
- Method `"GET"` → 405.
- Uri `"/views"` → 404.
- Body `"x"` (with uri `/view`) → `{:ok false :status 400 :reason "bad json"}`.

**t08 log-line-view** — `(b/log-line "2026-11-24T14:00:00Z" nil "view" 400 "bad camera")` → `"2026-11-24T14:00:00Z - view 400 bad camera"`.

### Additions to `test/pm/lot_test.cljs` (existing fixtures `GF9`, `stool`, `rules`)

```clojure
(def EW  {:walkers [{:id :walker-a :name "Walker A"} {:id :walker-b}]})
(def EWp (assoc EW :well {:at [0 1 0]}))
(def VW  {:as-of 1000 :camera {:pos [2 10 -3] :target [2 1 2]} :center [2.2 1.0 1.9]
          :walkers {:walker-a [1 1.0 1] :walker-c [3 1.0 3]}})
(def seen-ok {:ok true :view VW})
(def stale {:ok false :reason "I can't see where you're looking: the game isn't open"})
```

**t43 view-landmarks**
- `(view-landmarks VW EW)` → `{:here {:word "where you're looking" :xz [2.2 1.9]} :walker-a {:word "Walker A" :xz [1 1]} :walker-c {:word "walker c" :xz [3 3]}}`, with keys in that order.
- With `:center nil` there is no `:here`.
- `(view-landmarks nil EW)` → `{}`.

**t44 landmarks-with-view**
- `(keys (landmarks GF9 EWp [] VW))` → `(:here :walker-a :walker-c :well)`.
- `(:here (landmarks GF9 {:here {:at [4 1 4]}} [] VW))` → `{:word "the here" :xz [4 4]}`: an estate name wins.
- `(landmarks GF9 EWp [])` has no view keys.

**t45 nearest-landmark**
- `lms = {:a {:word "the a" :xz [0 0]} :b {:word "the b" :xz [2 0]}}` as a sorted map:
  - `[1 0]` → `{:id :a :word "the a" :distance-m 1.0}` (tie → the first);
  - `[1.5 0]` → `{:id :b :word "the b" :distance-m 0.5}`.
- `(nearest-landmark {:creek {:word "the creek" :cells [[0 3] [1 3]]}} [1 1])` → `{:id :creek :word "the creek" :distance-m 2.0}`.
- `(nearest-landmark {} [1 1])` → `nil`.

**t46 place-near-here**
- `(place GF9 EW [] (rules {}) stool :here seen-ok)` → `{:ok true :at [2.0 1.0 2.0] :yaw 0 :landmark :here :distance-m 0.22360679774997896 :spread-m 0}` (approx).
  Worked: from the centre (2.2, 1.9), grid point (2, 2) is at √(0.04+0.01) = 0.2236; (2.5, 2) is at 0.316; (2, 1.5) is at 0.447.
- `(:word (resolve-landmark (landmarks GF9 EW [] VW) :here))` → `"where you're looking"`.

**t47 place-near-walkers** (an estate walker, and one only in the view)
- `(place GF9 EW [] (rules {}) stool :walker-a seen-ok)` → `{:ok true :at [1.0 1.0 1.0] :yaw 0 :landmark :walker-a :distance-m 0 :spread-m 0}`.
- `:walker-c` → `{:ok true :at [3.0 1.0 3.0] :yaw 0 :landmark :walker-c :distance-m 0 :spread-m 0}`.

**t48 place-view-refusals** — each case returns `{:ok false :reason …}`:

| Call | Reason |
|---|---|
| `(place GF9 EWp [] (rules {}) stool :here stale)` | `"I can't see where you're looking: the game isn't open"` |
| `… :here nil)` | `"I can't see where you're looking: the game isn't sending its view"` |
| `… :walker-a stale)` | `"I can't see where you're looking: the game isn't open"` |
| `… :walker-b seen-ok)` | `"I don't know a place called walker-b; I know: here, walker-a, walker-c, well"` |
| `… :here {:ok true :view (assoc VW :center nil)})` | `"I can't tell where you're looking: the middle of your screen is sky"` |
| `… :pond stale)` | `"I don't know a place called pond; I know: well"` |
| `(place nil EWp [] (rules {}) stool :here stale)` | `"no ground map (state/ground.edn): the twin samples it from the kit"` |

**t49 landmark-lines-with-view** — `(landmark-lines (landmarks GF9 EWp [] VW))` →
`["here: where you're looking" "walker-a: Walker A" "walker-c: walker c" "well: the well"]`.

**Count:** 15 view + 4 bridge + 7 lot = **26 test cases**. Gate: `.crew/gate.sh` → 0 warnings, and every existing test still passes. That includes lot t40, which compares against `pm.lot/usage` symbolically.

---

## 9. le-docteur's T-19 row (after the gate passes)

Add after T-18 in `TASKS.md`:
`- [x] Nov 24 (crew round view-share-v0) T-19 The twin knows where you're looking (Michael, Nov 24: "lets send it to you so you know the location without a screenshot / high count for tokens"). The game posts its camera, the ground spot at the centre of the screen and every walker's position to the bridge (POST /view, at most every 2 s and only when something moved, plus every 30 s); the bridge keeps it in the private state/view.edn. bb where prints it as a few lines (how old, what ground you're looking at and the nearest landmark, where each walker is), and bb blueprint-draft takes --near here or --near <walker-id>. Nothing new on screen; never in the demo build.`
