# Spec: placement-v1, build where you look, and show me

Quest: `.crew/quests/placement-v1.md`. Its "Decided" list is final; this spec only says how.
Four parts, all pure where they can be:

1. **pm.lot**: `:open-kinds` (shade counts) and a second, wider search pass up to
   `:max-search-m`, so a refusal only comes when nothing fits that far out. A widened result
   carries `:moved-m` and `:why`.
2. **The line**: `lot/moved-line` fills the estate's `:blueprint-text :moved` template. The
   script prints it, and the demo's twin says it.
3. **Show me**: `pm.camera/fly-decision`, a pure decision function, plus `camera/flight`, the CREW
   key's existing framing maths pulled out so both the key and the new ghost use one flight.
   It is wired once in `apply-blueprint-file!`, which both the dev poll and `demo-ask!` pass
   through.
4. **One command**: the builder role card and PLAY.md text (given verbatim below, for le-docteur).

Out of scope (quest): walkers avoiding builds, the ground sampler, new UI controls.

---

## 1. Data

### `resources/estate.edn` `:build-rules` (whole new value)

```clojure
:build-rules {:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :max-search-m 15 :open-kinds ["o" "T"]
              :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12
              ;; show me (placement-v1): a new ghost from an ask flies the camera to it
              :show-me {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5}}
```

- `:max-search-m`: the second pass searches out to this. Missing, non-number or below
  `:search-m` means no second pass (the old behaviour).
- `:open-kinds`: ground kinds a foot may stand on. Missing or not sequential means `["o"]`.
- `:show-me`: read only by pm.core and pm.camera (pm.lot ignores it). `:near-m` sets "on screen
  and within this many metres means don't fly". `:quiet-ms` sets "no flight this soon after a
  player camera move". `:lift-m` is how far above the ghost's `:at` the camera aims. `:back`
  multiplies the CREW key's offset. `:low` multiplies the offset's y, so the camera sits lower
  and looks from the side, under a canopy.

### `resources/estate.edn` `:blueprint-text`: one new key

```clojure
:moved "Not there: {why}, so I put it {moved} m from {place}."   ; placement-v1: the near ground didn't fit, the build went further out
```

Example output: `Not there: most of it is bushes or walls, so I put it 6.5 m from where you're looking.`

No other estate key changes. The demo's `:demo-builds :lines` stays as it is.

### pm.lot result shapes (EDN)

```clojure
;; found within :search-m: unchanged, no new keys
{:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0}
;; found only in the wider pass (search-m < d <= max-search-m): two new keys
{:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0
 :moved-m 3                                ; = :distance-m
 :why "most of it is bushes or walls"}     ; the most common reason the inner pass refused, or lot/nothing-fits
;; nothing within max-search-m: unchanged shape; the number is max-search-m
{:ok false :reason "no level open ground within 15 m of where you're looking: most of it is bushes or walls"}
```

---

## 2. pm.lot (`src/pm/lot.cljc`): new and changed public vars

The file stays portable (cljc), pure, and names no one.

```clojure
(def refusal-prefix "no level open ground within ")
(def nothing-fits "nothing fits closer")
```
`refusal` must build its string from `refusal-prefix`. Its output stays byte-identical.

**`(open-kinds rules)` → set of strings.** `(set (:open-kinds rules))` when that is sequential,
else `#{"o"}`. `[]` gives `#{}`, meaning nothing is buildable (the user's choice; no special case).

**`(max-search-m rules)` → number.** `(:search-m rules)` when `:max-search-m` is missing or not a
number, else `(max (:search-m rules) (:max-search-m rules))`.

**`(near-rules rules)` → map.** `(assoc rules :max-search-m (:search-m rules))`: the same rules
with the second pass turned off. The demo uses it on its small grid.

**`(dominant-why whys)` → string or nil.** The most frequent string in `whys`. A tie goes to the
alphabetically smaller one, exactly as `refusal` does today. Empty or nil gives nil. `refusal`
must use it (no behaviour change).

**`spot` (same signature `[ctx [x z] yaw]`).** A foot's cell is fine when
`(contains? (open-kinds rules) kind)` and `y` is non-nil, where `rules` is `(:rules ctx)`.
The first bad cell gives a `:why`:
- its kind is open but `y` is nil → `"most of it has no ground"`;
- otherwise → `(cell-reason kind)` (unchanged).

The steep, near and route checks are unchanged and come in the same order.

**`place` (same 6- and 7-arity signatures).** Everything up to building `ctx` is unchanged. Then,
with `S = (:search-m rules)` and `M = (max-search-m rules)`:

- The candidate bounding box is computed with `M` in place of `search-m` (same ceil/floor/eps
  formulas). Candidates are the same `[x z yaw d]` tuples.
- **Pass 1**: the candidates with `d <= S + eps`, the same set as today. If any is accepted,
  return the best one by today's sort key `[(round d·1e6) (round spread·1e6) x z yaw]`, as
  today, with **no new keys**.
- **Pass 2** (only when pass 1 accepted nothing): the candidates with `S + eps < d <= M + eps`.
  If any is accepted, return the best by the same sort key, plus
  `:moved-m d :why (or (dominant-why whys1) nothing-fits)`, where `whys1` are pass 1's whys.
  When `M = S`, pass 2 is empty.
- **Neither pass**: `{:ok false :reason (refusal (concat whys1 whys2) M (:word lm))}`.

Implementation notes:
- Pass 2's verdicts are computed only when pass 1 fails (laziness is fine: `filter` over a
  `map`).
- The result equals "the nearest fit within M", but it must be built as two passes so `:why`
  comes from pass 1.
- Don't add ring steps.

**`(moved-line template word result)` → string or nil.** nil unless `template` is a string and
`(:moved-m result)` is a number. Otherwise it fills the `{name}` placeholders in `template`
(`#"\{([a-z0-9-]+)\}"`; an unknown one stays as written):
- `{why}` → `(or (:why result) nothing-fits)`
- `{place}` → `word`
- `{moved}` → `(fmt (max 0.5 (/ (round* (* 2 moved-m)) 2)))`, which is the nearest half metre,
  never below 0.5.

Unchanged: `feet look-radius turn feet-at cell segments seg-dist things landmarks
view-landmarks coord-near resolve-landmark landmark-lines nearest-landmark fmt refusal placed-line
blueprint parse-args usage`.

### `scripts/blueprint_draft.clj`

- `:draft` success (dry run or real): print `(lot/placed-line …)` as today, then, **only when
  `(:moved-m result)`**, a second stdout line:
  `(lot/moved-line (get-in estate [:blueprint-text :moved]) (:word lm) result)` (skip it if
  that is nil).
- Exit codes are unchanged. A refusal stays the one stderr line `blueprint-draft: <reason>`,
  exit 1.
- The header comment's "level, open spot" becomes "a level spot on open ground or under a
  tree's crown, searching out to :max-search-m".

---

## 3. pm.ask-build (`src/pm/ask_build.cljc`): the demo's pretend twin

**`ask` (same signature).** The ready result gets `:wide-spec` when
`(> (lot/max-search-m rules) (:search-m rules))`:
`:wide-spec (grid-spec xz (assoc rules :search-m (lot/max-search-m rules)) pad-m)`.
Otherwise there is no `:wide-spec` key. `grid-spec` itself is unchanged (it still reads
`:search-m`).

**`plan` (same signature `[ctx req ground]`).**
- The refusal branch also returns `:reason (:reason res)`, as
  `{:ok false :say <refused line> :reason <lot reason>}`. The unknown-look branch is unchanged:
  `{:ok false :say help}`, no `:reason`.
- On success, when `(:moved-m res)`, `:say` becomes `(str moved " " placed)`, where
  `moved = (lot/moved-line (get-in estate [:blueprint-text :moved]) word res)` and `placed` is
  today's filled `:placed` line. When `moved` is nil (no template), `:say` is just `placed`.
- Every other key is unchanged.

**`(widen? asked p1)` → boolean (new).** Answers "measure the wide grid and plan again?". True
iff `(map? (:wide-spec asked))`, `(false? (:ok p1))`, `(string? (:reason p1))` and
`(str/starts-with? (:reason p1) lot/refusal-prefix)`.

---

## 4. Show me: pm.camera (`src/pm/camera.cljs`) pure part

```clojure
(def show-defaults {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5})
```

**`(on-screen? ndc)` → boolean.** True iff `ndc` is a vector of 3 finite numbers with
`(<= -1 x 1)`, `(<= -1 y 1)` and `(<= -1 z 1)`. (A point behind the camera projects to z > 1.)

**`(fly-decision show m)` → keyword.** `show` is merged over `show-defaults` (nil is fine). `m` is
`{:ghost [x y z] :camera [x y z]|nil :on-screen? bool :card-open? bool :last-move-ms ms|nil
:now-ms ms}`. The first match wins:

1. `:ghost` isn't 3 finite numbers → `:no-ghost`
2. `:card-open?` → `:card-open`
3. `:last-move-ms` is a number and `(< (- now-ms last-move-ms) quiet-ms)` → `:player-moving`
4. `:on-screen?`, `:camera` is 3 numbers, and the 3-D distance camera→ghost `<= near-m` → `:in-view`
5. otherwise → `:fly`

**`(flight at lift back offset)` → `{:target [x y z] :offset [dx dy dz]}` or nil.** These are the
CREW key's `fly!` maths, moved here unchanged. nil unless `at` and `offset` are 3 numbers each.
`[x y z] = at` and `[ox oy oz] = offset`. `ox' = (if (pos? x) (- (abs ox)) (abs ox))` (the camera
comes in from the creek's side). Result:
`{:target [x (+ y lift) z] :offset [(* back ox') (* back oy) (* back oz)]}`.

**`(ghost-flight at offset show)` → same shape or nil.** With `s = (merge show-defaults show)`:
`(flight at (:lift-m s) (:back s) [ox (* (:low s) oy) oz])`, nil when `offset` isn't 3 numbers.
With the estate's CREW offset `[3.6 2.4 2.4]` it frames from 6.29 m at about 15° above level.

Interop stays as it is (`frame!` does the move).

### pm.blueprint (`src/pm/blueprint.cljs`): one new pure fn

**`(arrival-at world ids)` → `[x y z]` or nil.** Picks the last id in `ids` that names a
blueprint in `(:blueprints world)` whose `:at` is 3 numbers, and returns that `:at`. Empty ids,
or none resolving, gives nil. The caller passes `merge-file`'s `:announce`, which is already
"new since page load, not the first merge".

### pm.core wiring (`src/pm/core.cljs`)

- Require `[pm.lot :as lot]`.
- Next to `!follow` (~l.1282): `(defonce ^:private !camera-moved-at (atom nil))`, the time
  (`js/Date.now`) of the player's last camera move.
- In `wire-pointer!`:
  - the existing canvas `pointerdown` handler also does `(reset! !camera-moved-at (js/Date.now))`;
  - add a canvas `pointermove` listener that does the same when `(pos? (.-buttons e))` (a drag
    longer than 2 s keeps counting);
  - add a canvas `wheel` listener that does the same.
  - All three are `#js {:passive true}`.
- **The CREW flight, shared.** In `crew-key-tick!`, `fly!` becomes
  `(when-let [{:keys [target offset]} (camera/flight [x y z] lift (or back 1) (get-in w [:crew-key :offset]))] (camera/frame! h target offset))`.
  Behaviour is identical.
- New `(defn- show-arrival! [at] …)`, defined just above `apply-blueprint-file!`:
  ```clojure
  (when-let [^js h (kit/handle)]
    (let [w    @!world
          show (get-in w [:build-rules :show-me])
          fl   (camera/ghost-flight at (get-in w [:crew-key :offset]) show)
          d    (camera/fly-decision show {:ghost at :camera (:pos (t/rig-view h))
                                          :on-screen? (camera/on-screen? (when fl (t/project-ndc (.-camera h) (:target fl))))
                                          :card-open? (some? (bp/card-of w))
                                          :last-move-ms @!camera-moved-at :now-ms (js/Date.now)})]
      (when (and fl (= :fly d))
        (camera/frame! h (:target fl) (:offset fl))
        (reset! !follow nil))
      (js/console.info "Prime Meridian: new ghost at" (pr-str at) "camera:" (name d))))
  ```
- `apply-blueprint-file!`: right after the `try`/`reset! !world w3`, add
  `(when-let [at (bp/arrival-at @!world (:announce r))] (show-arrival! at))`.
  - **Dev:** the 5 s poll's `fetch-blueprints!` already calls it. The first merge at load
    announces nothing, so a reload never flies.
  - **Demo:** `demo-ask!` → `demo-merge!` → `apply-blueprint-file!`, so the twin's new build
    flies through the same line. No second hook. (`init!`'s first `demo-merge!` is the first
    merge and announces nothing.)
- `demo-ask!`, two passes so the big grid is only measured when needed:
  ```clojure
  (let [... r (ab/ask ctx text)]
    (if-let [s (:say r)]
      (twin-reply! s at)
      (let [measure (fn [spec] (when h (let [t0 (js/performance.now)
                                            g  (t/ground-grid (view-ground-meshes! h) (cover-meshes! h) spec
                                                              (:from-y sampler) (partial ab/sample-cell sampler))]
                                        (js/console.info "Prime Meridian: ground grid" (:cols spec) "x" (:rows spec)
                                                         "in" (js/Math.round (- (js/performance.now) t0)) "ms")
                                        g)))
            p1 (ab/plan (update ctx :rules lot/near-rules) (:req r) (measure (:spec r)))
            p  (if (ab/widen? r p1) (ab/plan ctx (:req r) (measure (:wide-spec r))) p1)]
        (when (:ok p) (reset! !demo-file (:file p)) (save-demo-file!) (demo-merge!))
        (twin-reply! (:say p) at))))
  ```
  With the estate's rules the wide grid is 65×65 instead of 29×29. Builder notes should give the
  logged ms from one live widened ask.

---

## 5. The builder role card and PLAY.md (le-docteur, verbatim)

### `.crew/roles/builder.md`: replace the whole "## Build requests from the player (💭)" section with

```markdown
## Build requests from the player (💭)

The player can message you from the estate's 💭 phone; the bridge types his words into your
pane as a plain prompt. When he asks for a thing in the world ("put a bench by the creek",
"a bench here, in the shade"), that is a build request: answer it with a blueprint, never by
editing `resources/estate.edn` or any source file. A build request is **two commands, no more**:

1. Write a draft in your scratchpad: `{:id :<new-kebab-id> :summary "proposed: <a few words>"
   :look {:parts [...]}}`, parts as documented under `:blueprints` in `resources/estate.edn`
   (shapes box/cylinder/cone/sphere; colours from the palette keys used there, e.g. `:brown`
   for timber, `:tin` for metal). Real-world size, y 0 is the ground, every leg or base
   touching y 0, at most 12 parts, nothing thinner than 0.08 m (he has to click it). Ids must be
   new: never reuse a removed one.
2. **One** `bb blueprint-draft <your-name> <draft.edn> --near <spot>`. `<spot>` is the `x,z` Hal
   gives you, else `here` (the middle of his screen) when he says "here", else a landmark from
   his words (`bb blueprint-draft --landmarks` lists them) or a walker id for "by you / by him".
   Shade under trees is fine. If the spot itself doesn't fit, the command moves the build to the
   nearest spot that does (up to 15 m) and prints a second line saying so; it refuses only when
   nothing fits. The ghost appears within 5 s, the camera flies to it, and "drew a blueprint"
   lands in 💭 by itself.
3. **One** `bb hal-say --as <your-name> "<line>"`: if it moved, its second printed line; if it
   refused, the reason and a landmark to try instead; otherwise a few words on what you drew.

Don't read or script around the ground map (`state/ground.edn`, `python3`, `node`, `bb -e`):
the command already reports where it went or why not. Then reply in your pane with the
command's printed line(s). Don't propose more than he asked for.
```

### `PLAY.md`: replace the "- **Build:**" bullet with

```markdown
- **Build:** Hal can build a **bench**, a **lamp post**, a **table** or a **planter**, near
  **here** (the middle of your screen), the **mailbox**, the **phone booth**, the **workshop**
  or the **creek**. Example: *"a lamp post by the mailbox"*. Shade under the trees is fine. If
  the exact spot is too steep, crowded or in a bush, he puts it at the nearest spot that fits
  and tells you how far he moved it, and the camera flies over to show you.
```

---

## 6. Who touches what

| Member | May edit | Must not |
|---|---|---|
| **un-automate-monde** (builder) | `src/pm/lot.cljc`, `src/pm/ask_build.cljc`, `src/pm/camera.cljs`, `src/pm/blueprint.cljs`, `src/pm/core.cljs`, `scripts/blueprint_draft.clj`; `resources/estate.edn` **only** `:build-rules` (whole value, §1) and the `:moved` key in `:blueprint-text`; writes `crew/runs/placement-v1-20261126-1903/builder-notes.md` | anything under `test/`, other estate keys, `.crew/`, `PLAY.md`, `docs/` |
| **le-testeur** (tester) | new file `test/pm/placement_test.cljs` (ns `pm.placement-test`); in `test/pm/ask_build_test.cljs` only `t29` and `t33` (see §8) plus the `RULES` comment on line 13 | `src/`, `scripts/`, `resources/`, any other existing test |
| **le-docteur** (docs) | `.crew/roles/builder.md` "Build requests from the player (💭)" section; `PLAY.md` "Build" bullet (§5 text, verbatim) | everything else (the GDD needs no change: the quest's decisions sit inside GDD §2.1/§4.8's blueprint flow) |

---

## 7. Test cases (`test/pm/placement_test.cljs`)

Requires `[cljs.test :refer [deftest is]]`, `[cljs.reader :as reader]`, `[pm.lot :as lot]`,
`[pm.ask-build :as ab]`, `[pm.camera :as camera]`, `[pm.blueprint :as bp]`, `[pm.world :as w]`.
Use the same `approx=` as `lot_test.cljs` (1e-9, and maps must have equal key sets) for lot
results, and `(< (js/Math.abs (- a b)) 1e-9)` per number elsewhere.

### Fixtures

```clojure
(def stool {:id :stool :summary "a stool"
            :look {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0] :color :brown}]}})   ; one foot [0 0], radius 0.1414
(defn rules [m] (merge {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1} m))
(defn grid [origin n row y] {:origin origin :step 0.5 :cols n :rows n
                             :kinds (vec (repeat n row)) :y (vec (repeat n (vec (repeat n y))))})
(def GT9 (grid [0 0] 9 "TTTTTTTTT" 1.0))                 ; canopy, x z in 0..4
(def GF9 (grid [0 0] 9 "ooooooooo" 1.0))                 ; open,   x z in 0..4
(def GW  (grid [-3 -3] 13 "BBBBBBBBBBBBo" 1.0))          ; bush everywhere but the column x = 3; x z in -3..3
(def GK  {:origin [0 0] :step 0.5 :cols 6 :rows 1 :kinds ["oTBR:~"] :y [[1.0 1.1 1.2 1.3 0.2 0.1]]})
(def SHADE (rules {:open-kinds ["o" "T"]}))
(def MOVED "Not there: {why}, so I put it {moved} m from {place}.")
(defn ctx [g r] {:ground g :rules r :feet (lot/feet (:look stool)) :radius (lot/look-radius (:look stool) 1)
                 :things [] :segments []})
(def nest-here {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [9 1 9]}]}
                          {:id :wren :name "Wren" :placed [{:id :nest :at [0 1 0]}]}]})
(def jar-here  {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [0 1 0]}]}
                          {:id :wren :name "Wren" :placed [{:id :nest :at [9 1 9]}]}]})
```

### A. Rules helpers

1. `(lot/open-kinds {})` → `#{"o"}`; `(lot/open-kinds {:open-kinds ["o" "T"]})` → `#{"o" "T"}`;
   `(lot/open-kinds {:open-kinds nil})` → `#{"o"}`; `(lot/open-kinds {:open-kinds "oT"})` → `#{"o"}`.
2. `(lot/max-search-m {:search-m 6})` → `6`; `{:search-m 6 :max-search-m 15}` → `15`;
   `{:search-m 6 :max-search-m 4}` → `6`; `{:search-m 6 :max-search-m "15"}` → `6`.
3. `(lot/near-rules {:search-m 6 :max-search-m 15 :gap-m 1})` → `{:search-m 6 :max-search-m 6 :gap-m 1}`.
4. `(lot/dominant-why ["too steep" "most of it is under trees" "too steep"])` → `"too steep"`;
   `(lot/dominant-why ["too steep" "most of it is under trees"])` → `"most of it is under trees"`;
   `(lot/dominant-why [])` → `nil`; `(lot/dominant-why nil)` → `nil`.
5. `lot/refusal-prefix` = `"no level open ground within "`; `lot/nothing-fits` = `"nothing fits closer"`;
   `(lot/refusal ["too steep"] 6 "the well")` still → `"no level open ground within 6 m of the well: too steep"`.

### B. `:open-kinds` in `spot`

6. `(lot/spot (ctx GK SHADE) [0.5 0] 0)` approx= `{:ok true :y 1.1 :spread 0}` (canopy open).
7. With `(ctx GK SHADE)`: `[0 0]` approx= `{:ok true :y 1.0 :spread 0}`; `[1.0 0]` →
   `{:ok false :why "most of it is bushes or walls"}`; `[1.5 0]` → `"most of it is rocks"`;
   `[2.0 0]` and `[2.5 0]` → `"most of it is in the creek"`; `[3.0 0]` → `"most of it has no ground"`.
8. `(lot/spot (ctx GK (rules {})) [0.5 0] 0)` → `{:ok false :why "most of it is under trees"}`
   (the default is `["o"]`); the same with `(rules {:open-kinds ["o"]})`.
9. Canopy with no height: `g = {:origin [0 0] :step 0.5 :cols 1 :rows 1 :kinds ["T"] :y [[nil]]}`:
   `(lot/spot (ctx g SHADE) [0 0] 0)` → `{:ok false :why "most of it has no ground"}`;
   `(lot/spot (ctx g (rules {})) [0 0] 0)` → `{:ok false :why "most of it is under trees"}`.

### C. `:open-kinds` in `place` (both mirrors)

10. `(lot/place GT9 {} [] SHADE stool [2 2])` approx=
    `{:ok true :at [2 1.0 2] :yaw 0 :landmark [2 2] :distance-m 0 :spread-m 0}` (no `:moved-m`).
11. `(lot/place GT9 {} [] (rules {}) stool [2 2])` →
    `{:ok false :reason "no level open ground within 1 m of [2 2]: most of it is under trees"}`.
12. Second mirror under a crown: `E = {:mirrors [{:id :pip :name "Pip" :placed [{:id :jar :at [9 1 9]}]} {:id :wren :name "Wren" :placed [{:id :nest :at [2 1 2]}]}]}`,
    `r = (rules {:open-kinds ["o" "T"] :gap-m 0.5 :search-m 2})`:
    `(lot/place GT9 E [] r stool :wren-nest)` approx=
    `{:ok true :at [1 1.0 1] :yaw 0 :landmark :wren-nest :distance-m 1.4142135623730951 :spread-m 0}`.
    Hand check: clear of the nest needs d − 0.1414 − 0.6 ≥ 0.5, so d ≥ 1.2414; the closest grid
    points are (±1, ±1) at √2; the tie breaks on x then z, giving [1 1].
13. Same as 12 with `:open-kinds ["o"]` → `{:ok false :reason "no level open ground within 2 m of Wren's nest: most of it is under trees"}`.
14. First mirror, mirrored: swap the two `:at`s in 12 (jar at `[2 1 2]`, nest at `[9 1 9]`),
    near `:pip-jar` → same result with `:landmark :pip-jar`.

### D. The widening search

15. Found in pass 2: `(lot/place GW {} [] (rules {:max-search-m 4}) stool [0 0])` approx=
    `{:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0 :moved-m 3 :why "most of it is bushes or walls"}`.
    Hand check: every point within 1 m is bush; the only open column is x = 3; its nearest point
    is (3, 0) at 3.
16. Inclusive edge: `(rules {:max-search-m 3})` → the same map as 15.
17. Refusal past max: `(rules {:max-search-m 2})` →
    `{:ok false :reason "no level open ground within 2 m of [0 0]: most of it is bushes or walls"}`
    (all 2 m points are inside the grid and bush).
18. No second pass: `(rules {})`, and also `(rules {:max-search-m 0.5})` (below search-m), →
    `{:ok false :reason "no level open ground within 1 m of [0 0]: most of it is bushes or walls"}`.
19. Pass 1 wins, no new keys: `(rules {:search-m 3 :max-search-m 4})` approx=
    `{:ok true :at [3 1.0 0] :yaw 0 :landmark [0 0] :distance-m 3 :spread-m 0}`.
20. Pass 1 has no candidates at all: `(lot/place GF9 {:well {:at [0.3 1 0.3]}} [] (rules {:search-m 0.2 :max-search-m 1}) stool :well)` approx=
    `{:ok true :at [0 1.0 1] :yaw 0 :landmark :well :distance-m 0.7615773105863908 :spread-m 0 :moved-m 0.7615773105863908 :why "nothing fits closer"}`.
    Hand check: no grid point lies within 0.2 of (0.3, 0.3). Clearing the well (r 0.6) needs
    d ≥ 0.7414. (0.5, 1) and (1, 0.5) at 0.728 are too close; (0, 1) and (1, 0) at √0.58 pass;
    x breaks the tie, giving [0 1].
21. Second mirror, widened: `(lot/place GW nest-here [] (rules {:max-search-m 4}) stool :wren-nest)`
    approx= `{:ok true :at [3 1.0 0] :yaw 0 :landmark :wren-nest :distance-m 3 :spread-m 0 :moved-m 3 :why "most of it is bushes or walls"}`.
22. First mirror, widened: `(lot/place GW jar-here [] (rules {:max-search-m 4}) stool :pip-jar)` → as 21 with `:landmark :pip-jar`.
23. Widened near `:here` (7-arity): `(lot/place GW {} [] (rules {:max-search-m 4}) stool :here {:ok true :view {:center [0 1 0] :walkers {}}})`
    approx= `{:ok true :at [3 1.0 0] :yaw 0 :landmark :here :distance-m 3 :spread-m 0 :moved-m 3 :why "most of it is bushes or walls"}`.

### E. The line

24. `(lot/moved-line MOVED "where you're looking" {:moved-m 3 :why "most of it is bushes or walls"})` →
    `"Not there: most of it is bushes or walls, so I put it 3 m from where you're looking."`
25. Half-metre rounding: `:moved-m 6.5249` → `"… 6.5 m …"`; `6.76` → `"7"`; `0.7615773105863908` → `"1"`;
    `0.2` → `"0.5"` (floor of 0.5). Check each with `clojure.string/includes?` on `"put it <n> m from"`.
26. Results 20 and 21 through it: `(lot/moved-line MOVED "the well" <result 20>)` →
    `"Not there: nothing fits closer, so I put it 1 m from the well."`;
    `(lot/moved-line MOVED "Wren's nest" <result 21>)` →
    `"Not there: most of it is bushes or walls, so I put it 3 m from Wren's nest."`
27. A nil `:why` falls back: `{:moved-m 3}` → `"Not there: nothing fits closer, so I put it 3 m from the well."` (word `"the well"`).
28. nil cases: no `:moved-m` (result 19) → `nil`; template `nil` → `nil`; an unknown placeholder
    stays: `(lot/moved-line "{why}/{zz}" "x" {:moved-m 3 :why "w"})` → `"w/{zz}"`.
29. The estate's words: `RAW = (reader/read-string w/estate-edn)`.
    `(lot/moved-line (get-in RAW [:blueprint-text :moved]) "where you're looking" {:moved-m 6.4 :why "most of it is bushes or walls"})` →
    `"Not there: most of it is bushes or walls, so I put it 6.5 m from where you're looking."`

### F. Estate data

30. `(:build-rules RAW)` = `{:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :max-search-m 15 :open-kinds ["o" "T"] :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12 :show-me {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5}}`.
31. No names: none of `"Michael" "Hal" "Flora" "Juniper"` appears in `(pr-str (:build-rules RAW))`
    or in `(get-in RAW [:blueprint-text :moved])`.

### G. The demo's pretend twin (pm.ask-build)

Fixtures: `DB = (:demo-builds RAW)`, `E0 = {:demo-builds DB :mirrors [] :walkers []}`,
`E0m = (assoc E0 :blueprint-text {:moved MOVED})`,
`R4 = {:step-m 0.5 :yaws [0] :search-m 1 :max-search-m 4 :gap-m 0 :route-m 0 :max-spread-m 0.1}`,
`(defn actx [estate rules] {:estate estate :world {} :file ab/empty-file :rules rules :here [0 1 0] :by :tw :now 5000})`,
and `GW1 = (grid [-3 -3] 13 "BBBBBBBBBBBBo" 1)` (y the integer 1, as ask_build_test's grids).

32. `(ab/ask (actx E0 R4) "put a lamp here")` =
    `{:req {:look :lamp-post :near :here} :target [0 0] :spec {:origin [-2 -2] :step 0.5 :cols 9 :rows 9} :wide-spec {:origin [-5 -5] :step 0.5 :cols 21 :rows 21}}`.
33. `(ab/ask (actx E0 (lot/near-rules R4)) "put a lamp here")` has no `:wide-spec` key:
    `{:req {:look :lamp-post :near :here} :target [0 0] :spec {:origin [-2 -2] :step 0.5 :cols 9 :rows 9}}`.
34. Pass 1 refuses with a reason: `(ab/plan (actx E0m (lot/near-rules R4)) {:look :lamp-post :near :here} GW1)` =
    `{:ok false :say "I can't put that there: no level open ground within 1 m of where you're looking: most of it is bushes or walls." :reason "no level open ground within 1 m of where you're looking: most of it is bushes or walls"}`.
35. Pass 2 places and says so: `p = (ab/plan (actx E0m R4) {:look :lamp-post :near :here} GW1)`:
    `(:ok p)` true; `(:id p)` `:lamp-post-1`; `(:at (:blueprint p))` `[3 1 0]`; `(:by (:blueprint p))` `:tw`;
    `(:say p)` = `"Not there: most of it is bushes or walls, so I put it 3 m from where you're looking. A lamp post, drawn near where you're looking. Try it, and make it real if you like it."`
36. No template, no moved sentence: same as 35 with `E0` →
    `(:say p)` = `"A lamp post, drawn near where you're looking. Try it, and make it real if you like it."`.
37. `widen?`: with `a = (ab/ask (actx E0 R4) "put a lamp here")` and `p1` = result 34:
    `(ab/widen? a p1)` → `true`;
    `(ab/widen? (dissoc a :wide-spec) p1)` → `false`;
    `(ab/widen? a {:ok true})` → `false`;
    `(ab/widen? a {:ok false :say "help"})` → `false` (no `:reason`);
    `(ab/widen? a {:ok false :say "x" :reason lot/no-ground-map})` → `false`.

### H. Show me (pm.camera)

Let `S` be `camera/show-defaults`, and `base = {:ghost [10 1 0] :camera [0 5 0] :on-screen? false :card-open? false :last-move-ms nil :now-ms 100000}`.

38. `camera/show-defaults` = `{:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5}`.
39. `(camera/fly-decision S base)` → `:fly` (new ghost off screen).
40. On screen and near: `(assoc base :ghost [3 1 4] :camera [0 1 0] :on-screen? true)` → `:in-view` (distance 5).
41. Boundary: `(assoc base :ghost [6 1 8] :camera [0 1 0] :on-screen? true)` → `:in-view` (distance exactly 10).
42. On screen but far: `(assoc base :ghost [12 1 0] :camera [0 1 0] :on-screen? true)` → `:fly`.
43. Near but off screen: `(assoc base :ghost [3 1 4] :camera [0 1 0])` → `:fly`.
44. Trial card open: `(assoc base :card-open? true)` → `:card-open`.
45. Recent player move: `(assoc base :last-move-ms 99000)` → `:player-moving` (1000 ms ago);
    `(assoc base :last-move-ms 98000)` → `:fly` (exactly 2000 ms ago is past the quiet time).
46. Order: `(assoc base :card-open? true :last-move-ms 99500)` → `:card-open`;
    `(assoc base :ghost nil :card-open? true)` → `:no-ghost`; `(assoc base :ghost [1 2])` → `:no-ghost`.
47. No camera: `(assoc base :camera nil :on-screen? true)` → `:fly`.
48. `show` nil uses the defaults: `(camera/fly-decision nil (assoc base :ghost [3 1 4] :camera [0 1 0] :on-screen? true))` → `:in-view`;
    and `(camera/fly-decision {:near-m 4} <same m>)` → `:fly`.
49. `on-screen?`: `[0 0 0.5]` → true; `[1 -1 0.99]` → true; `[1.01 0 0.5]` → false; `[0 0 1.2]` → false;
    `[0 -1.01 0.5]` → false; `nil` → false; `[0 0]` → false; `[js/NaN 0 0.5]` → false.
50. `flight` (the CREW maths): `(camera/flight [2 1 3] 1.0 1 [3.6 2.4 2.4])` → `{:target [2 2.0 3] :offset [-3.6 2.4 2.4]}`;
    `(camera/flight [-2 1 3] 0.3 1 [3.6 2.4 2.4])` → `{:target [-2 1.3 3] :offset [3.6 2.4 2.4]}`;
    `(camera/flight [0 0 0] 1.0 1.8 [3.6 2.4 2.4])` → `{:target [0 1.0 0] :offset [6.48 4.32 4.32]}` (x = 0 is not positive);
    `(camera/flight [0 0] 1 1 [3.6 2.4 2.4])` → nil; `(camera/flight [0 0 0] 1 1 nil)` → nil. Compare numbers within 1e-9.
51. `ghost-flight`: `(camera/ghost-flight [2 1 3] [3.6 2.4 2.4] nil)` →
    `{:target [2 1.6 3] :offset [-5.04 1.68 3.36]}` (within 1e-9). Its offset's length is
    `6.285984409780221`, which is in the quest's 6–8 m. `(camera/ghost-flight [2 1 3] nil nil)` → nil.
    For the other bank: `(camera/ghost-flight [-2 1 3] [3.6 2.4 2.4] {:back 1})` →
    `{:target [-2 1.6 3] :offset [3.6 1.2 2.4]}`.

### I. Which ghost (pm.blueprint)

52. `W = {:blueprints [{:id :a :at [1 2 3]} {:id :b :at [4 5 6]} {:id :c :at [1 2]}]}`:
    `(bp/arrival-at W [:a :b])` → `[4 5 6]`; `(bp/arrival-at W [:b :a])` → `[1 2 3]`;
    `(bp/arrival-at W [:a :c])` → `[1 2 3]` (:c's :at is bad); `(bp/arrival-at W [:a :zz])` → `[1 2 3]`;
    `(bp/arrival-at W [])` → nil; `(bp/arrival-at W nil)` → nil; `(bp/arrival-at {} [:a])` → nil.
53. Through the poll's merge, so a reload never flies:
    `B = {:id :stool-1 :at [1 1 2] :yaw 0 :summary "a stool" :look (:look stool) :by :tw}`,
    `r0 = (bp/merge-file {} {:as-of 1 :blueprints [B]})` → `(bp/arrival-at (:world r0) (:announce r0))` → nil (first merge).
    `r1 = (bp/merge-file (:world (bp/merge-file {} {:as-of 1 :blueprints []})) {:as-of 2 :blueprints [B]})` →
    `(:announce r1)` = `[:stool-1]` and `(bp/arrival-at (:world r1) (:announce r1))` → `[1 1 2]`.
54. The demo file takes the same path:
    `W1 = (:world (bp/merge-file (w/load-estate w/estate-edn) ab/empty-file))`,
    `r = (bp/merge-file W1 (ab/add-build ab/empty-file B 1000))` →
    `(bp/arrival-at (:world r) (:announce r))` → `[1 1 2]`.

---

## 8. Existing tests: what changes

**`test/pm/lot_test.cljs`: no expectation changes.** I checked every `spot`/`place` test
(t16–t19, t22–t37, t42, t46–t48, and t38/t39 that reuse t22's result). All of them build their
rules with the `rules` helper, which has neither `:open-kinds` nor `:max-search-m`. So
`open-kinds` is `#{"o"}`, the canopy refusals stand (t16's `[0.5 0]`, t19, t29, t42's GT9), and
`M = S`, so pass 2 is empty and every refusal string keeps its `S`. The ok results come from
pass 1, so no `:moved-m`/`:why` keys appear, and the `approx=` key-set checks still pass.

**`test/pm/ask_build_test.cljs`: two expectations change** (the real estate's `RULES` now has
`:max-search-m 15` and canopy open):

- **t29-ask-ready-to-measure**: old
  `{:req {:look :bench :near :here} :target [0 0] :spec {:origin [-7 -7] :step 0.5 :cols 29 :rows 29}}`.
  New: the same plus `:wide-spec {:origin [-16 -16] :step 0.5 :cols 65 :rows 65}`.
  Hand check: h = 15 + 1, origin 0.5·floor(−32) = −16, cols 1 + 32/0.5 = 65.
- **t33-plan-refused-under-trees**: old `{:ok false :say "I can't put that there: no level open
  ground within 6 m of where you're looking: most of it is under trees."}`. Rename it
  `t33-plan-under-trees` with two assertions:
  - (a) `(ab/plan (ctx E0 {} ab/empty-file [0 1 0]) {:look :bench :near :here} TREES)` = exactly
    t31's expected map: `{:ok true :id :bench-1 :blueprint {:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw} :file {:as-of 5000 :blueprints [<same blueprint>] :real [] :removed []} :say "A bench, drawn near where you're looking. Try it, and make it real if you like it."}`.
  - (b) the old rule still refuses:
    `(ab/plan (assoc (ctx E0 {} ab/empty-file [0 1 0]) :rules (assoc RULES :open-kinds ["o"] :max-search-m 6)) {:look :bench :near :here} TREES)` =
    `{:ok false :say "I can't put that there: no level open ground within 6 m of where you're looking: most of it is under trees." :reason "no level open ground within 6 m of where you're looking: most of it is under trees"}`.
- Line 13's comment becomes the new `:build-rules` value (cosmetic).

Unchanged and checked: t16 (`grid-spec` still reads `:search-m`); t31, t32, t34 and t35 (pass 1
finds a spot on OPEN, so there are no new keys); t35's nil-ground case checks only `:ok`/`:say`,
which are unchanged even though `:reason` is now present. No other test reads the real
`:build-rules` or `:blueprint-text`.

---

## 9. Done when (from the quest)

- The gate passes (0 warnings, all tests including §7 1–54).
- `builder-notes.md` says how Hal checks it live:
  - in the dev build, stand the camera on the shade under the trees by the lobby, then ask for
    "a bench here";
  - read `state/blueprints.edn` (the new id, its `:at`), the blueprint-draft output (a second
    line when it moved), the camera (it frames the ghost from the side unless it was already on
    screen and near, or Hal had just moved the camera or a trial card was open), the console
    (`new ghost at … camera: <decision>`), and 💭 (the member's `--as` line);
  - in the demo build, the same ask in 💭 and the logged grid ms.
