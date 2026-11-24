# Spec: blueprint draft v0 (TASKS T-13, the last piece)

Quest: `.crew/quests/blueprint-draft-v0.md`. Start commit `94f9706`.
A crew member writes a **draft** (a blueprint with no `:at`/`:yaw`) and runs
`bb blueprint-draft <member> <draft.edn> --near <landmark>`. The pure `pm.lot` finds a level,
open spot near that landmark on the kit's sampled ground map, clear of everything else, and the
command hands the full blueprint to the existing `scripts/blueprint_add.clj`.

Everything the quest records as decided stands. This spec fixes the names, shapes, order of checks,
rounding and messages, and works the test values by hand.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/lot.cljc` (new), `scripts/blueprint_draft.clj` (new), `bb.edn` (add the `blueprint-draft` task only), `resources/estate.edn` (add the top-level `:build-rules` with its comment, nothing else); in the run dir: `builder-notes.md` | `test/`, every other `src/` file (incl. `pm.blueprint-file`, `pm.blueprint`), `scripts/blueprint_add.clj`, `docs/`, `TASKS.md`, `state/` (never written: hand tests use temp files + `PM_BLUEPRINTS_FILE`/`PM_BUILDS_FILE`; `state/ground.edn` is only *read*) |
| **tester** (le-testeur) | `test/pm/lot_test.cljs` (new, ns `pm.lot-test`) | everything else. Tests are pure: they never read `state/ground.edn` or `resources/estate.edn`; every grid, estate and rules map is built in the test from §5 |
| **le-docteur** | `TASKS.md`: a note on the T-13 row (wording in §7), after the gate passes. `AGENTS.md` and `README.md` have **no** line listing bb commands (checked), so nothing there | code, tests, `resources/`, `state/`, `docs/GDD.md` |

Builder and tester work in parallel from this spec only.

---

## 2. Data shapes

### 2.1 Ground map (`state/ground.edn`, gitignored; read only by the script)

```clojure
;; comment header (edn/read-string skips it)
{:as-of 1795550768074
 :origin [-12 -26] :step 0.5 :cols 45 :rows 61
 :kinds ["TTTTooo::~~ …" …]     ; one string per row r (z = z0 + r·step), one char per column c (x = x0 + c·step)
 :y     [[1.62 1.63 nil …] …]}  ; ground height (m) per cell, same layout; nil = no ground
```
Kinds: `o` open (the only buildable kind), `T` canopy, `B` bush/wall, `R` rocks, `:` creek bed,
`~` water, space = no ground. A row string shorter than `:cols` reads as space past its end.

### 2.2 Draft (what the member writes)

```clojure
{:id :bench-by-creek
 :summary "proposed: a bench by the creek"
 :look {:parts [{:shape :box :size [1.2 0.1 0.4]  :at [0 0.45 0]     :color :brown}
                {:shape :box :size [0.1 0.4 0.1]  :at [0.5 0.2 0]    :color :brown}
                {:shape :box :size [0.1 0.4 0.1]  :at [-0.5 0.2 0]   :color :brown}]}}
```
No `:at`, no `:yaw` (ignored if present). Parts sit on y 0 = the ground.

### 2.3 Rules (`resources/estate.edn`, new top-level key; the builder adds exactly this)

```clojure
 ;; Where bb blueprint-draft may put a member's draft (pm.lot, TASKS T-13): candidates every
 ;; :step-m within :search-m of the landmark, turned to each of :yaws (degrees); every foot on
 ;; open ground (state/ground.edn), feet at most :max-spread-m apart in height, the item's
 ;; circle :gap-m clear of every other thing's and :route-m clear of every walker's route.
 :build-rules {:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12}
```
Tests never read these numbers: they build their own rules maps (§5).

### 2.4 Internal shapes (returned by public functions)

```clojure
;; a thing (something the draft must keep clear of)
{:id :phone-booth :xz [4.9 -6.6] :r 0.6 :word "the phone booth"}
;; a landmark (sorted map, keyword -> one of)
{:phone-booth {:word "the phone booth" :xz [4.9 -6.6]}
 :creek       {:word "the creek" :cells [[-2.5 -26.0] …]}}
;; a cell
{:kind "o" :y 1.62}          ; outside the grid: {:kind " " :y nil}
;; a spot verdict
{:ok true :y 1.62 :spread 0.03}   |   {:ok false :why "most of it is under trees"}
;; place's result (decided by the quest; no other keys)
{:ok true :at [x y z] :yaw 90 :landmark :creek :distance-m 1.5 :spread-m 0.06}
{:ok false :reason "no level open ground within 6 m of the workshop: most of it is under trees"}
```

---

## 3. `src/pm/lot.cljc` (ns `pm.lot`)

Portable (bb + cljs): pure data in, pure data out. No I/O, no atoms, no `js/Date`, no three.js, no
`::k` keys. Requires only `clojure.string` and `pm.blueprint-file` (for `id-re`, `member-re`).
Nothing names a person, pet, member or prop: every name comes from the data.

### 3.1 Constants

```clojure
(def eps 1e-9)                 ; every <=, >= on metres uses it (see each function)
(def default-radius-m 0.6)     ; a place, or an item with no measurable look
(def grounded-m 0.02)          ; a part's bottom within this of 0 touches the ground
(def narrow-m 0.3)             ; a grounded part at most this across (x and z) is one foot
(def usage "usage: bb blueprint-draft <member> <draft.edn> --near <landmark> [--dry-run] | bb blueprint-draft --landmarks")
(def no-ground-map "no ground map (state/ground.edn): the twin samples it from the kit")
(def nothing-grounded "nothing touches the ground")
```

Reasons a spot is refused (`:why`), exact strings:

| Cause | `:why` |
|---|---|
| foot cell kind `T` | `"most of it is under trees"` |
| foot cell kind `B` | `"most of it is bushes or walls"` |
| foot cell kind `R` | `"most of it is rocks"` |
| foot cell kind `:` or `~` | `"most of it is in the creek"` |
| any other kind (space, outside the grid), or kind `o` with `:y` nil | `"most of it has no ground"` |
| feet spread too large | `"too steep"` |
| too close to a thing | `"too close to <thing's :word>"` |
| on a route | `"on a walker's path"` |

Words: `(words k)` = `(name k)` with every `-` replaced by a space (private helper).

### 3.2 `(feet look)` → vector of `[dx dz]`

The look's feet, in part order. A part counts only if it has `:size` `[sx sy sz]` and `:at`
`[ax ay az]` (three numbers each). It is **grounded** when `|ay − sy/2| <= grounded-m + eps`
(`:rot` ignored). A grounded part with `sx <= narrow-m + eps` **and** `sz <= narrow-m + eps` gives
one foot `[ax az]`; any other grounded part gives five, in this order:
`[ax az] [ax−hx az−hz] [ax−hx az+hz] [ax+hx az−hz] [ax+hx az+hz]` (hx = sx/2, hz = sz/2).
Duplicates are kept. `nil` look, no `:parts`, or no grounded part → `[]`.

### 3.3 `(look-radius look scale)` → number or nil

Half the diagonal of the xz bounding box of every part that has `:size` and `:at` (rotation
ignored: part x spans `ax ± sx/2`, z spans `az ± sz/2`), times `scale`:
`(* scale 0.5 (Math/sqrt (+ (* w w) (* d d))))`. No such part (or `nil` look) → `nil`.
The circle is centred on the item's `:at`, not on the box's centre.

### 3.4 `(turn [dx dz] yaw)` → `[wx wz]`

The quest's formula with `c = cos(yaw°)`, `s = sin(yaw°)`, each **rounded to 12 decimals**
(`(/ (Math/round (* v 1e12)) 1e12)`, so cos 90° is exactly 0):
`[(+ (* c dx) (* s dz)) (+ (* (- s) dx) (* c dz))]`. +90 turns local +x to world −z.

### 3.5 `(feet-at feet [x z] yaw)` → vector of `[x z]`

Each foot turned by `yaw` and added to `[x z]`, same order as `feet`.

### 3.6 `(cell ground [x z])` → `{:kind s :y h}`

Nearest cell: `c = (Math/round (/ (- x x0) step))`, `r = (Math/round (/ (- z z0) step))`.
Inside when `0 <= c < :cols` and `0 <= r < :rows`. `:kind` is the one-character **string** at
column `c` of row string `r` (`" "` past the string's end); `:y` is `(get-in ground [:y r c])`.
Outside → `{:kind " " :y nil}`.

### 3.7 `(segments estate)` → vector of `[[ax az] [bx bz]]`

For each walker in `(:walkers estate)`, in order, whose `:route` is a non-empty sequence of `[x z]`:
one segment per consecutive pair; plus `[last first]` when `:loop` is true and the route has ≥ 2
points; a one-point route gives `[[p p]]`. No walkers → `[]`.

### 3.8 `(seg-dist p a b)` → number

Distance from point `p` to segment `a`–`b` (all `[x z]`): if `a = b`, `|p − a|`; else
`t = clamp(((p−a)·(b−a)) / |b−a|², 0, 1)` and `|p − (a + t(b−a))|`.

### 3.9 `(things estate file-blueprints)` → vector of things, sorted by `(name :id)`

`:at` is "valid" when it is a vector of 3 numbers; `:xz` is `[(at 0) (at 2)]`.

1. **Places:** every top-level `[k v]` of `estate` where `v` is a map with a valid `:at`:
   `{:id k :r default-radius-m :word (str "the " (words k))}` (0.6 even when it has a `:look`).
2. **Mirror items:** for each mirror in `(:mirrors estate)`, each map in its `:placed` with a
   keyword `:id` and a valid `:at`: id `(keyword (str (name mirror-id) "-" (name item-id)))`,
   `:r` `(or (look-radius (:look item) (or (:scale item) 1)) default-radius-m)`,
   `:word` `(str (or (:name mirror) (words mirror-id)) "'s " (words item-id))`.
3. **Blueprints:** `(:blueprints estate)` then `file-blueprints`, each map with a keyword `:id`
   and a valid `:at`: `:r` as for mirror items, `:word` `(str "the " (words id))`.
4. **Obstacles:** `(:obstacles (:motion estate))`, the i-th (from 0) with `:at` `[x z]` and a
   number `:r`: `{:id (keyword (str "obstacle-" (inc i))) :xz at :r r :word "an obstacle"}`.

Concatenate in that order, then a **stable** `sort-by (comp name :id)`. Entries missing an `:at`
are skipped silently. Same-id entries are all kept.

### 3.10 `(landmarks ground estate file-blueprints)` → sorted map

`(into (sorted-map) …)` of `{id {:word w :xz [x z]}}` from `things` groups 1–3 (not obstacles);
when an id repeats, the first in the order places, mirror items, estate blueprints, file
blueprints wins. Plus `:creek {:word "the creek" :cells cells}` when `ground` is a map with at
least one `:` or `~` cell and no `:creek` landmark exists yet; `cells` are those cells' centres
`[(+ x0 (* c step)) (+ z0 (* r step))]` in row-major order (row 0 first, then column).

### 3.11 `(landmark-lines lms)` → vector of strings

`(str (name k) ": " word)` for each entry, in the map's (sorted) order.

### 3.12 `(spot ctx [x z] yaw)` → verdict

`ctx` = `{:ground g :rules rules :feet feet :radius r :things things :segments segs}`
(`rules` needs `:max-spread-m :gap-m :route-m`). Checks in this order; the first failure wins:

1. **Ground:** for each world foot (`feet-at`), in order, its `cell`. The first foot whose kind is
   not `"o"` or whose `:y` is nil → `{:ok false :why <kind reason, §3.1>}`.
2. **Steep:** `spread = max(ys) − min(ys)`; `spread > max-spread-m + eps` → `"too steep"`.
3. **Too close:** for each thing, `clearance = |[x z] − :xz| − radius − (:r thing)`. If any
   `clearance < gap-m − eps`, name the thing with the **smallest** clearance (tie: the earlier
   one in `things`) → `(str "too close to " word)`.
4. **Route:** any segment with `(seg-dist [x z] a b) − radius < route-m − eps` → `"on a walker's path"`.

Else `{:ok true :y (apply max ys) :spread spread}`.

### 3.13 `(refusal whys search-m word)` → string

`whys` = every rejected candidate's `:why` (one per point×yaw). The most frequent one; a tie goes
to the lexicographically smallest string. Result
`(str "no level open ground within " (fmt search-m) " m of " word ": " why)`; with no `whys`,
just `(str "no level open ground within " (fmt search-m) " m of " word)`.

### 3.14 `(place ground estate file-blueprints rules draft near)` → result

`near` is a keyword. In order:

1. `ground` not a map → `{:ok false :reason no-ground-map}`.
2. `lms = (landmarks ground estate file-blueprints)`; `near` not in it →
   `{:ok false :reason (str "I don't know a place called " (name near) "; I know: " (str/join ", " (map name (keys lms))))}`.
3. `fs = (feet (:look draft))` empty → `{:ok false :reason nothing-grounded}`.
4. `radius = (or (look-radius (:look draft) 1) default-radius-m)`; build `ctx` with
   `(things estate file-blueprints)` and `(segments estate)`.
5. **Candidates** (world-anchored grid: x = kx·step-m, z = kz·step-m, integer k, computed by
   multiplication, never accumulation). Distance `d`: for an `:xz` landmark, `|[x z] − xz|`; for
   `:creek`, the minimum over its `:cells`. Range of k: from `ceil((lo − S)/step − eps)` to
   `floor((hi + S)/step + eps)` where lo/hi are the landmark's x (z) — for `:creek`, the min/max
   over its cells. Keep points with `d <= search-m + eps`. Enumerate kx ascending, kz ascending,
   then each yaw in `(:yaws rules)` order; `spot` each (point, yaw). (Builder: compute `d` once per
   point, not per yaw.)
6. Accepted ones: pick the minimum by the key
   `[(Math/round (* d 1e6)) (Math/round (* spread 1e6)) x z yaw]` (vector `compare`): closest,
   then smaller spread, then smaller x, smaller z, smaller yaw. Return
   `{:ok true :at [x (:y verdict) z] :yaw yaw :landmark near :distance-m d :spread-m spread}`
   (`d`, `spread` unrounded).
7. None accepted → `{:ok false :reason (refusal whys (:search-m rules) word)}` with the
   landmark's `:word`.

### 3.15 `(fmt x)` → string

`n = (Math/round (* x 100))`; sign `"-"` only when `n < 0`; `q = |n| quot 100`, `r = |n| rem 100`:
`r = 0` → `"q"`; `r` a multiple of 10 → `"q.(r/10)"`; else `"q.rr"` (two digits).

### 3.16 `(placed-line draft word result)` → string

`(str "placed " (name (:id draft)) " " (fmt d) " m from " word " at [" (fmt x) " " (fmt y) " " (fmt z) "] yaw " (fmt yaw) " (feet within " (Math/round (* spread 100)) " cm)")`
— the integer cm printed with no decimals.

### 3.17 `(blueprint draft result)` → map

`{:id (:id draft) :at (:at result) :yaw (:yaw result) :summary (:summary draft) :look (:look draft)}`
— exactly these five keys (no new keys in the blueprint shape).

### 3.18 `(parse-args args)` → map

`args` a seq of strings, may be nil.
- `["--landmarks"]` → `{:mode :landmarks}`.
- `[member path & rest]` with `member` matching `bf/member-re`, `path` non-blank and not starting
  with `--`, and `rest` exactly one of `["--near" n]`, `["--near" n "--dry-run"]`,
  `["--dry-run" "--near" n]`, where `n` minus one leading `:` matches `bf/id-re` →
  `{:mode :draft :member (keyword member) :path path :near (keyword n') :dry-run bool}`.
- Anything else → `{:usage usage}`.

---

## 4. `scripts/blueprint_draft.clj` and `bb.edn`

All I/O; every decision is `pm.lot`'s.

- `bb.edn` task, beside `blueprint-add`, same exit propagation:
  ```clojure
  blueprint-draft {:doc  "A member places a draft near a landmark, on level open ground (state/ground.edn), and proposes it: bb blueprint-draft <member> <draft.edn> --near <landmark> [--dry-run] | bb blueprint-draft --landmarks"
                   :task (let [r (apply shell {:continue true} "bb" "scripts/blueprint_draft.clj" *command-line-args*)]
                           (System/exit (:exit r)))}
  ```
- Reads: ground = `PM_GROUND_FILE` or `state/ground.edn` (missing or unreadable → `nil`);
  estate = `resources/estate.edn`; rules = `(:build-rules estate)` (missing → stderr
  `blueprint-draft: resources/estate.edn has no :build-rules`, exit 1); file blueprints =
  `(:blueprints (bf/normalize <PM_BLUEPRINTS_FILE or bf/state-path, nil if missing>))`.
- `{:usage …}` → usage on stderr, **exit 2**.
- `--landmarks` → print `landmark-lines`, one per line, exit 0.
- Draft: read the EDN (unreadable → stderr `blueprint-draft: can't read <path>`, exit 1); `place`.
  Refused → stderr `blueprint-draft: <reason>`, **exit 1**. Placed → `bp = (blueprint draft result)`;
  `(bf/problems bp)` non-empty → stderr `blueprint-draft: invalid draft: …` (joined `"; "`), exit 1.
  - `--dry-run` → print `placed-line` (word = the landmark's `:word`), exit 0. Nothing written.
  - else write `(pr-str bp)` to a temp file (`java.io.File/createTempFile`), run
    `bb scripts/blueprint_add.clj <member> <tmp>` with the same environment (`{:continue true}`,
    its stdout/stderr passed through), delete the temp file; exit 0 → print `placed-line`, exit 0;
    non-zero → exit 1 (blueprint-add's own stderr says why). One writer, one validator.
- `builder-notes.md` (run dir): what was built; a `--dry-run` against the real ground map with
  `PM_BLUEPRINTS_FILE` pointing at a temp copy of `state/blueprints.edn`, and its printed line;
  the output of `--landmarks`; the exact command Hal runs to see a bench appear live (a draft
  file under the run dir, `--near creek`, no `--dry-run`).

---

## 5. Test fixtures (build these in `test/pm/lot_test.cljs`)

`approx=`: a recursive comparison, numbers within `1e-9`, maps/vectors structurally, everything
else with `=`. Use it wherever a number is computed; `=` for strings.

```clojure
;; looks
(def look-stool {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0] :color :brown}]})
(def look-bench {:parts [{:shape :box :size [1.2 0.1 0.4] :at [0 0.45 0]  :color :brown}   ; seat, bottom 0.4
                         {:shape :box :size [0.1 0.4 0.1] :at [0.5 0.2 0]  :color :brown}
                         {:shape :box :size [0.1 0.4 0.1] :at [-0.5 0.2 0] :color :brown}]})
(def look-base  {:parts [{:shape :box :size [1.0 0.2 0.6] :at [0.5 0.1 0]}]})          ; wide, off-centre
(def look-float {:parts [{:shape :box :size [0.5 0.5 0.5] :at [0 1 0]}]})
(def stool {:id :stool :summary "a stool" :look look-stool})
(def bench {:id :bench :summary "a bench" :look look-bench})
(def floater {:id :floater :summary "a floater" :look look-float})

;; radii (hand-worked): stool sqrt(0.08)/2 = 0.1414213562373095
;;                      bench sqrt(1.6)/2  = 0.6324555320336759

(defn rules [m] (merge {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1} m))

(defn grid "n×n, origin [0 0], step 0.5, every row = row-str, every height h"
  [n row-str h] {:origin [0 0] :step 0.5 :cols n :rows n
                 :kinds (vec (repeat n row-str)) :y (vec (repeat n (vec (repeat n h))))})
(def G7  (grid 7 "ooooooo" 1.0))     ; x, z in 0..3
(def GF9 (grid 9 "ooooooooo" 1.0))   ; x, z in 0..4
(def GT9 (grid 9 "TTTTTTTTT" 1.0))
(def GSL9 (assoc GF9 :y (vec (repeat 9 [1.0 1.2 1.4 1.6 1.8 2.0 2.2 2.4 2.6]))))   ; slope in x

(def GK {:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1.0 1.1 1.2 1.3 0.2 0.1]]})
(def GO {:origin [-12 -26] :step 0.5 :cols 2 :rows 2 :kinds ["oT" "Bo"] :y [[1 2] [3 4]]})
(def GC2 {:origin [0 0] :step 0.5 :cols 3 :rows 2 :kinds ["o:o" "~oo"] :y [[1 0.2 1] [0.1 1 1]]})

;; the two-mirror estate
(def E2 {:hud {:mirrors [:pip]}
         :mailbox {:at [4.0 1.75 -8.0] :yaw 0 :look {:parts [{:shape :box :size [3 1 3] :at [0 0.5 0]}]}}
         :crew-key {:title "Crew" :offset [3.6 2.4 2.4]}
         :motion {:obstacles [{:at [-4.8 -8.2] :r 0.7} {:at [1 1] :r 0.5}]}
         :mirrors [{:id :pip :name "Pip"
                    :placed [{:id :jar :at [1 1 1] :scale 4
                              :look {:parts [{:shape :cylinder :size [0.08 0.11 0.08] :at [0 0.055 0]}]}}]}
                   {:id :wren :name "Wren" :placed [{:id :nest :at [-2 1 -2]}]}]
         :blueprints [{:id :bench-a :at [3 1 3] :summary "s" :look look-bench}]})
(def fb2 [{:id :shed :at [5 1 5] :summary "s" :look {:parts [{:shape :box :size [2 1 2] :at [0 0.5 0]}]}}
          {:id :no-at :summary "s"}])
```

---

## 6. Test cases (name each `deftest` `tNN-…`)

### feet, radius, turn

**t01 feet-narrow-legs** — `(feet look-bench)` → `[[0.5 0] [-0.5 0]]` (the seat's bottom is 0.4: not grounded).

**t02 feet-wide-base** — `(feet look-base)` → `[[0.5 0] [0 -0.3] [0 0.3] [1.0 -0.3] [1.0 0.3]]` (approx).

**t03 feet-nothing-grounded** — `(feet look-float)` → `[]`; `(feet nil)` → `[]`; `(feet {:parts []})` → `[]`.

**t04 feet-2cm** — `(feet {:parts [{:size [0.1 0.4 0.1] :at [0 0.215 0]} {:size [0.1 0.4 0.1] :at [1 0.225 0]} {:size [0.1 0.4 0.1] :at [2 0.185 0]}]})`
→ `[[0 0] [2 0]]` (bottoms 0.015, 0.025, −0.015).

**t05 feet-narrow-threshold** — `(feet {:parts [{:size [0.3 0.2 0.3] :at [0 0.1 0]}]})` → `[[0 0]]`;
`(feet {:parts [{:size [0.31 0.2 0.1] :at [0 0.1 0]}]})` →
`[[0 0] [-0.155 -0.05] [-0.155 0.05] [0.155 -0.05] [0.155 0.05]]` (approx).

**t06 feet-rot-ignored-and-sizeless-skipped** —
`(feet {:parts [{:size [0.1 0.4 0.1] :at [0 0.2 0] :rot [0 0 90]} {:shape :box :at [1 0 1]}]})` → `[[0 0]]`.

**t07 look-radius** — `(look-radius look-bench 1)` ≈ `0.6324555320336759`;
`(look-radius look-bench 2)` ≈ `1.2649110640673518`; `(look-radius look-base 1)` ≈ `0.5830951894845301`
(box x 0..1, z −0.3..0.3); `(look-radius look-stool 1)` ≈ `0.1414213562373095`;
`(look-radius {:parts []} 1)` → `nil`; `(look-radius nil 1)` → `nil`.

**t08 turn** (approx) — `(turn [1 0] 0)` → `[1 0]`; `(turn [1 0] 90)` → `[0 -1]`; `(turn [0 1] 90)` → `[1 0]`;
`(turn [1 0] 45)` → `[0.707106781187 -0.707106781187]`; `(turn [0.5 0] 135)` → `[-0.3535533905935 -0.3535533905935]`;
`(turn [1 0] 180)` → `[-1 0]`.

**t09 feet-at-yaw-90** (approx) — `(feet-at (feet look-base) [2 2] 90)` → `[[2 1.5] [1.7 2] [2.3 2] [1.7 1] [2.3 1]]`;
`(feet-at (feet look-base) [2 2] 0)` → `[[2.5 2] [2 1.7] [2 2.3] [3 1.7] [3 2.3]]`.

### cells, segments

**t10 cell** — on `GK`: `[0 0]` → `{:kind "o" :y 1.0}`; `[0.74 0]` → `{:kind "T" :y 1.1}`; `[1.26 0]` → `{:kind "R" :y 1.3}`;
`[0.2 0.1]` → `{:kind "o" :y 1.0}`; `[3.0 0]`, `[0 0.5]`, `[-0.3 0]` → `{:kind " " :y nil}`.
On `GO` (origin offset, row = z): `[-11.5 -25.5]` → `{:kind "o" :y 4}`; `[-12 -25.5]` → `{:kind "B" :y 3}`; `[-11.5 -26]` → `{:kind "T" :y 2}`.

**t11 segments** — `(segments {:walkers [{:id :a :route [[0 0] [1 0] [1 1]]} {:id :b :route [[0 0] [1 0] [1 1]] :loop true} {:id :c :route [[2 2]]} {:id :d}]})`
→ `[[[0 0] [1 0]] [[1 0] [1 1]] [[0 0] [1 0]] [[1 0] [1 1]] [[1 1] [0 0]] [[2 2] [2 2]]]`; `(segments {})` → `[]`.

**t12 seg-dist** (approx) — `(seg-dist [0 1] [-1 0] [1 0])` → `1.0`; `(seg-dist [3 0] [-1 0] [1 0])` → `2.0`;
`(seg-dist [3 4] [0 0] [0 0])` → `5.0`; `(seg-dist [0.5 0.5] [0 0] [4 0])` → `0.5`.

### things, landmarks (both mirrors)

**t13 things** (approx) — `(things E2 fb2)` →
```clojure
[{:id :bench-a    :xz [3 3]       :r 0.6324555320336759 :word "the bench a"}
 {:id :mailbox    :xz [4.0 -8.0]  :r 0.6                :word "the mailbox"}     ; a place: 0.6 despite its look
 {:id :obstacle-1 :xz [-4.8 -8.2] :r 0.7                :word "an obstacle"}
 {:id :obstacle-2 :xz [1 1]       :r 0.5                :word "an obstacle"}
 {:id :pip-jar    :xz [1 1]       :r 0.2262741699796952 :word "Pip's jar"}       ; sqrt(0.0128)/2 × 4
 {:id :shed       :xz [5 5]       :r 1.4142135623730951 :word "the shed"}
 {:id :wren-nest  :xz [-2 -2]     :r 0.6                :word "Wren's nest"}]    ; second mirror, no look
```
(`:hud`, `:crew-key`, `:mirrors`, `:motion` are not places; `:no-at` is skipped.)

**t14 landmarks** — `(keys (landmarks GC2 E2 fb2))` → `[:bench-a :creek :mailbox :pip-jar :shed :wren-nest]`;
`(:creek …)` → `{:word "the creek" :cells [[0.5 0] [0 0.5]]}` (approx); `(:wren-nest …)` → `{:word "Wren's nest" :xz [-2 -2]}`;
`(:pip-jar …)` → `{:word "Pip's jar" :xz [1 1]}`. `(keys (landmarks nil E2 fb2))` → `[:bench-a :mailbox :pip-jar :shed :wren-nest]` (no creek; obstacles never).

**t15 landmark-lines** — `(landmark-lines (landmarks GC2 E2 fb2))` →
`["bench-a: the bench a" "creek: the creek" "mailbox: the mailbox" "pip-jar: Pip's jar" "shed: the shed" "wren-nest: Wren's nest"]`.

### spot (each check, and their order)

`(defn ctx [g look things segs r] {:ground g :rules r :feet (feet look) :radius (look-radius look 1) :things things :segments segs})`

**t16 spot-kinds** — `c = (ctx GK look-stool [] [] (rules {}))`, yaw 0:
`[0 0]` → `{:ok true :y 1.0 :spread 0}` (approx); `[0.5 0]` → `"most of it is under trees"`; `[1.0 0]` → `"most of it is bushes or walls"`;
`[1.5 0]` → `"most of it is rocks"`; `[2.0 0]` and `[2.5 0]` → `"most of it is in the creek"`; `[3.0 0]` → `"most of it has no ground"`
(each `{:ok false :why …}`). On `{:origin [0 0] :step 0.5 :cols 2 :rows 1 :kinds ["o "] :y [[nil 1.0]]}`: `[0 0]` and `[0.5 0]` → `"most of it has no ground"`.

**t17 spot-steep-and-highest-foot** — ground `{:origin [0 0] :step 0.5 :cols 5 :rows 1 :kinds ["ooooo"] :y [[1.0 1.0 1.1 1.0 1.25]]}`,
bench, `(rules {})` (max spread 0.1), yaw 0:
`[0.5 0]` → `{:ok true :y 1.1 :spread 0.1}` (feet at x 1.0 and 0; 0.10000000000000009 passes by `eps`);
`[1.0 0]` → `{:ok true :y 1.0 :spread 0}`; `[1.5 0]` → `{:ok false :why "too steep"}` (1.25 − 1.1 = 0.15).

**t18 spot-near-and-route** — on `GF9`, stool, things `[{:id :well :xz [2 2] :r 0.6 :word "the well"}]`,
segments `[[[0 0] [4 0]]]`, `(rules {:gap-m 0.5 :route-m 0.5})`, yaw 0:
`[1 2]` → `"too close to the well"` (1 − 0.1414 − 0.6 = 0.2586 < 0.5); `[0.5 2]` → ok `:y 1.0 :spread 0`;
`[0.5 0.5]` → `"on a walker's path"` (0.5 − 0.1414 = 0.3586 < 0.5); `[0.5 1.0]` → ok.
With things `[well {:id :post :xz [1.5 2] :r 0.2 :word "the post"}]`: `[1 2]` → `"too close to the post"`
(post 0.5 − 0.1414 − 0.2 = 0.1586 < well 0.2586).

**t19 spot-check-order** — (a) ground `{:origin [0 0] :step 0.5 :cols 4 :rows 1 :kinds ["oooT"] :y [[1.0 1.0 1.5 1.0]]}`, bench,
things `[{:id :well :xz [0.5 0] :r 0.6 :word "the well"}]`, `(rules {:gap-m 0.5})`, yaw 0:
`[1.0 0]` → `"most of it is under trees"` (first foot at x 1.5; also too close); `[0.5 0]` → `"too steep"` (1.5 vs 1.0; also too close).
(b) `GF9`, stool, things well at `[2 2]` r 0.6, segments `[[[0 2] [4 2]]]`, `(rules {:gap-m 0.5 :route-m 0.5})`: `[1.5 2]` → `"too close to the well"` (also on the path).

### refusal, fmt

**t20 refusal** — `(refusal ["too steep" "most of it is under trees" "too steep"] 6 "the workshop")` →
`"no level open ground within 6 m of the workshop: too steep"`;
`(refusal ["too steep" "most of it is under trees"] 1.5 "the well")` → `"no level open ground within 1.5 m of the well: most of it is under trees"` (tie → smaller string);
`(refusal [] 6 "the creek")` → `"no level open ground within 6 m of the creek"`.

**t21 fmt** — `6`→`"6"`, `6.0`→`"6"`, `2.5`→`"2.5"`, `1.2345`→`"1.23"`, `0.07`→`"0.07"`, `-0.5`→`"-0.5"`,
`-0.004`→`"0"`, `12.3`→`"12.3"`, `1.4142135623730951`→`"1.41"`.

### place: found (every landmark kind, both mirrors)

**t22 place-found-at-a-place** — `(place G7 {:mailbox {:at [1.5 1.0 1.5]}} [] (rules {:yaws [0 90] :search-m 2 :gap-m 0.5 :route-m 0.5}) stool :mailbox)`
→ `{:ok true :at [0.5 1.0 0.5] :yaw 0 :landmark :mailbox :distance-m 1.4142135623730951 :spread-m 0}` (approx).
Worked: clear needs d ≥ 0.1414 + 0.6 + 0.5 = 1.2414; grid offsets (i,j)·0.5 give d = 0.5·√(i²+j²); i²+j² = 5 (1.118) fails,
8 (1.414) passes: (0.5,0.5) (0.5,2.5) (2.5,0.5) (2.5,2.5), both yaws, spread 0 → smaller x, smaller z, smaller yaw.

**t23 place-tie-x-before-z** (file blueprint as landmark) — `G7` with row 1 = `"oBooooo"` (cell x 0.5, z 0.5 is `B`),
estate `{}`, file `[{:id :shed :at [1.5 1.0 1.5] :summary "a shed" :look {:parts [{:shape :box :size [1.0 1.0 1.0] :at [0 0.5 0]}]}}]`,
`(rules {:yaws [0 90] :search-m 2 :gap-m 0.5})`, stool, `:shed` →
`{:ok true :at [0.5 1.0 2.5] :yaw 0 :landmark :shed :distance-m 1.4142135623730951 :spread-m 0}`.
Worked: shed r = √2/2 = 0.7071; needs d ≥ 1.3485 → the same four diagonal points; (0.5,0.5) is a bush;
x before z picks (0.5,2.5), not (2.5,0.5).

**t24 place-second-mirror** — ground `{:origin [-4 -4] :step 0.5 :cols 9 :rows 9 :kinds (vec (repeat 9 "ooooooooo")) :y (vec (repeat 9 (vec (repeat 9 2.0))))}`,
estate `{:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [5 1 5]}]} {:id :wren :name "Wren" :placed [{:id :nest :at [-2 1 -2]}]}]}`,
`(rules {:search-m 2 :gap-m 0.5})`, stool, `:wren-nest` →
`{:ok true :at [-3.0 2.0 -3.0] :yaw 0 :landmark :wren-nest :distance-m 1.4142135623730951 :spread-m 0}`.
(Same geometry as t22 around (−2,−2); the jar is far away.)

**t25 place-yaw-90** — ground `{:origin [0 0] :step 0.5 :cols 5 :rows 5 :kinds (vec (repeat 5 "TToTT")) :y [[1.0 1.0 1.0 1.0 1.0] [1.0 1.0 1.0 1.0 1.0] [1.06 1.06 1.06 1.06 1.06] [1.0 1.0 1.0 1.0 1.0] [1.0 1.0 1.0 1.0 1.0]]}`
(only x = 1.0 is open; z = 1.0 sits 6 cm high), estate `{:corner {:at [1.0 1.0 -1.0]}}`,
`(rules {:yaws [0 45 90 135] :search-m 2})`, bench, `:corner` →
`{:ok true :at [1.0 1.06 0.5] :yaw 90 :landmark :corner :distance-m 1.5 :spread-m 0.06}` (approx).
Worked: yaw 90 turns the legs [±0.5 0] to [0 ∓0.5], so at (1, 0.5) they land on (1, 0) h 1.0 and (1, 1.0) h 1.06
(y = the highest foot). Yaw 0 puts the legs at x ± 0.5 (canopy); 45°/135° put them 0.707 apart in x, two columns apart, one always `T`.
Gap 0 still needs d ≥ 0.6325 + 0.6 = 1.2325, so (1, 0) (d 1.0) fails; (1, 1.0) is d 2.0, farther.

**t26 place-creek-water** — ground `{:origin [0 0] :step 0.5 :cols 5 :rows 1 :kinds ["~~ooo"] :y [[0.1 0.1 1.0 1.2 1.4]]}`,
estate `{}`, `(rules {:search-m 2})`, stool, `:creek` →
`{:ok true :at [1.0 1.0 0] :yaw 0 :landmark :creek :distance-m 0.5 :spread-m 0}` (distance to the nearest `~` cell, (0.5, 0); x −0.5 has no ground).

**t27 place-creek-spread-before-x** — ground
`{:origin [0 0] :step 0.5 :cols 7 :rows 4 :kinds [":::::::" "ooooooo" "ooooooo" "ooooooo"] :y [[0.2 0.2 0.2 0.2 0.2 0.2 0.2] [1.04 1.0 1.03 1.02 1.0 1.05 1.005] [1.0 1.0 1.0 1.0 1.0 1.0 1.0] [1.0 1.0 1.0 1.0 1.0 1.0 1.0]]}`,
estate `{}`, `(rules {:yaws [0 90] :search-m 0.5})`, bench, `:creek` →
`{:ok true :at [2.5 1.005 0.5] :yaw 0 :landmark :creek :distance-m 0.5 :spread-m 0.005}` (approx).
Worked: only z = 0.5, yaw 0, x 0.5..2.5 survive (z 0 has a leg in the creek or off the map; yaw 90 puts a leg on row 0);
their spreads (legs at x ± 0.5): x 0.5 → 0.01, 1.0 → 0.02, 1.5 → 0.03, 2.0 → 0.03, 2.5 → 0.005. Smaller spread beats smaller x;
y is the higher leg, 1.005.

**t28 place-yaw-tie** — t22's call with `:yaws [90 0]` → same result (yaw 0); with `:yaws [90]` →
`{:ok true :at [0.5 1.0 0.5] :yaw 90 :landmark :mailbox :distance-m 1.4142135623730951 :spread-m 0}`.

### place: refusals

**t29 refuse-canopy** — `(place GT9 {:well {:at [2 1 2]}} [] (rules {:yaws [0 90] :gap-m 0.5 :route-m 0.5}) stool :well)`
→ `{:ok false :reason "no level open ground within 1 m of the well: most of it is under trees"}`.

**t30 refuse-steep** — `(place GSL9 {:well {:at [2 1 2]}} [] (rules {}) bench :well)` →
`{:ok false :reason "no level open ground within 1 m of the well: too steep"}`
(legs two columns apart: 0.4 m on every one of the 13 points; ground is checked before closeness).

**t31 refuse-too-close** — `(place GF9 {:phone-booth {:at [2 1 2]}} [] (rules {:gap-m 0.5}) stool :phone-booth)` →
`{:ok false :reason "no level open ground within 1 m of the phone booth: too close to the phone booth"}` (needs d ≥ 1.2414 > 1).

**t32 refuse-too-close-names-the-nearest** — t31 plus file `[{:id :shed :at [2.5 1 2] :summary "s" :look {:parts [{:shape :box :size [2 1 2] :at [0 0.5 0]}]}}]` →
`"no level open ground within 1 m of the phone booth: too close to the shed"` (shed clearance = d_s − 1.5556 ≤ d_b − 1.0556 < booth's d_b − 0.7414, everywhere).

**t33 refuse-route** — `(place GF9 {:gate {:at [2 1 2]} :walkers [{:id :w :route [[0 2] [4 2]]}]} [] (rules {:search-m 1.5 :route-m 2}) stool :gate)` →
`"no level open ground within 1.5 m of the gate: on a walker's path"`.
Worked: 29 points (i²+j² ≤ 9); 9 with d < 0.7414 are too close; the other 20 are at most 1.5 from the line z = 2 (< 2 + 0.1414): 20 > 9.

**t34 refuse-unknown-landmark** — `(place {:origin [0 0] :step 0.5 :cols 2 :rows 1 :kinds ["o:"] :y [[1.0 0.2]]} {:well {:at [2 1 2]}} [] (rules {}) stool :pond)` →
`{:ok false :reason "I don't know a place called pond; I know: creek, well"}`; the same with `floater` → the same reason (landmark before feet).

**t35 refuse-no-ground-map** — `(place nil {:well {:at [2 1 2]}} [] (rules {}) stool :well)` and `(place nil {} [] (rules {}) floater :pond)` →
`{:ok false :reason "no ground map (state/ground.edn): the twin samples it from the kit"}`.

**t36 refuse-nothing-grounded** — `(place GF9 {:well {:at [2 1 2]}} [] (rules {}) floater :well)` → `{:ok false :reason "nothing touches the ground"}`.

**t37 refuse-no-candidates (world-anchored grid)** — `(place GF9 {:well {:at [0.3 1 0.3]}} [] (rules {:search-m 0.2}) stool :well)` →
`{:ok false :reason "no level open ground within 0.2 m of the well"}` (nearest grid point (0.5,0.5) is 0.283 away).

### the command's pure parts

**t38 placed-line** — `(placed-line {:id :bench-by-creek} "the creek" {:ok true :at [2.5 1.04 -0.5] :yaw 90 :landmark :creek :distance-m 1.4142135623730951 :spread-m 0.034})` →
`"placed bench-by-creek 1.41 m from the creek at [2.5 1.04 -0.5] yaw 90 (feet within 3 cm)"`;
`(placed-line stool "the mailbox" <t22's result>)` → `"placed stool 1.41 m from the mailbox at [0.5 1 0.5] yaw 0 (feet within 0 cm)"`.

**t39 blueprint** — `(blueprint (assoc stool :at [9 9 9] :extra 1) <t22's result>)` →
`{:id :stool :at [0.5 1.0 0.5] :yaw 0 :summary "a stool" :look look-stool}` (exactly these keys), and `(pm.blueprint-file/valid? it)` → `true`.

**t40 parse-args** —
`["--landmarks"]` → `{:mode :landmarks}`;
`["member-a" "d.edn" "--near" "creek"]` → `{:mode :draft :member :member-a :path "d.edn" :near :creek :dry-run false}`;
`["member-a" "d.edn" "--near" ":phone-booth" "--dry-run"]` → `{… :near :phone-booth :dry-run true}`;
`["member-a" "d.edn" "--dry-run" "--near" "creek"]` → `{… :near :creek :dry-run true}`;
each of `nil`, `[]`, `["member-a" "d.edn"]`, `["member-a" "d.edn" "--near"]`, `["member-a" "d.edn" "--near" "Creek!"]`,
`["Member" "d.edn" "--near" "creek"]`, `["member-a" "d.edn" "--near" "creek" "--fast"]`, `["--landmarks" "x"]` → `{:usage pm.lot/usage}`.

40 cases. Gate: `.crew/gate.sh` → 0 warnings, all tests pass.

---

## 7. le-docteur's T-13 note (after the gate passes)

Append to the T-13 row in `TASKS.md`: `Nov 24: a member places what you ask for: bb blueprint-draft <member> <draft.edn> --near <landmark> finds a level open spot near the place you named on the kit's sampled ground (state/ground.edn), clear of other things and the walkers' routes, and proposes it through bb blueprint-add; --landmarks lists the names, --dry-run only prints. The rules are :build-rules in resources/estate.edn (pm.lot).`
