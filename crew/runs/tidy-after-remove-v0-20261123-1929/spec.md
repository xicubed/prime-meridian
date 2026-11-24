# Spec: tidy-after-remove-v0 (TASKS T-16)

Quest: `.crew/quests/tidy-after-remove-v0.md`. Three small changes, one round, all pure
where they can be: (A) the lobby signpost goes and the lobby stays an errand spot,
(B) the build card stays on screen up close, (C) a real build's sign drops "proposed: ".
Nothing here names a person, pet, member or blueprint in `src/`. No `::k` across
namespaces. No npm deps. No writes to `state/` from tests.

## 0. Who touches what

| Member | May edit | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/plan.cljs` (`lobby-item`), `src/pm/pick.cljs` (`screen-of` 3-arity, `pin-min-y`, `card-pin-gap-px`), `src/pm/ui.cljs` (`trial-card-height`), `src/pm/core.cljs` (`trial-card!` only), `src/pm/blueprint.cljs` (`sign-summary`, `sign-text`), `src/pm/scene.cljs` (the `with-lobby` docstring only, if you like); `resources/estate.edn`: **the `:lobby` entry and its comment only**; `crew/runs/tidy-after-remove-v0-20261123-1929/builder-notes.md` | `test/`, `docs/`, `TASKS.md`, any other `estate.edn` key, `state/` |
| **tester** (le-testeur) | `test/pm/plan_test.cljs`: rewrite `t42` and `t43` only (§4.1); new `test/pm/tidy_after_remove_test.cljs` (every other case below) | `src/`, `resources/`, docs, the other existing tests |
| **le-docteur** | `TASKS.md`: a new row T-16 (§5); `docs/GDD.md` §2.4: the Lobby row's note (§5) | code, tests, everything else |

The builder and tester work at the same time from this spec alone.

---

## A. The lobby has no sign; it is still an errand spot

### A.1 Data (`resources/estate.edn`)

Before:
```clojure
:lobby {:at [2.7 6.18 -23.6] :yaw 0
        :text "Lobby & kitchen — bring your questions here"
        :look {:parts [...post... ...board...]}}
```
After (only `:text` and `:look` go; `:at` and `:yaw` stay byte-identical):
```clojure
 ;; The lobby, with your kitchen (GDD §2.4, §4.8): home base, where agents
 ;; bring their questions. It's the kit's cottage (the estate's one real
 ;; house, upstream on raised ground), so nothing is drawn here: no sign
 ;; (Michael, Nov 23). Only an errand spot (`bb hal-say --at lobby`), probed
 ;; 6.18 m at the cottage's steps.
 :lobby {:at [2.7 6.18 -23.6] :yaw 0}
```

### A.2 `pm.plan/lobby-item` (changed)

```clojure
(defn lobby-item [world] ...)  ; => item map | nil
```
- Returns **nil** when `(:lobby world)` has no valid `:at` (3 numbers, as today) **or
  when its `:look` is nil/absent**. A lobby with only `:text` (no `:look`) is also nil: no
  floating label without a thing under it.
- With a valid `:at` **and** a non-nil `:look`, the result is exactly today's item:
  `{:key [:lobby :box] :at at :yaw (yaw or 0) :parts (look-parts look nil)
    :center [x (+ y (/ top 2)) z] :label :sign :label-at [x (+ y top label-lift) z]
    :text (if (string? text) text "")}`.
- Docstring updated to say so (no lobby look → nothing drawn; the lobby stays an errand
  destination through its `:at`).

`pm.scene/with-lobby` already conjs only a non-nil item, so with the shipped estate the
scene gets no lobby mesh and no lobby label sprite. No other code change is needed for
the lobby.

**Unchanged on purpose:**
- `pm.world/errand-target` reads `(get-in world [:lobby :at])`, so `--at lobby` still
  walks there.
- The crew key's "Lobby" place row in `pm.core` (`:when (vector? (get-in w [id :at]))`)
  stays: it's the HUD's fly-to button for the cottage, not something drawn at the lobby.

---

## B. The card stays on screen up close

### B.1 `pm.pick/card-pin-gap-px` (new)

```clojure
(def card-pin-gap-px 8)   ; px between a pinned card's top and the canvas's top edge
```

### B.2 `pm.pick/screen-of` (new 3-arity; 2-arity unchanged)

```clojure
(screen-of ndc size)        ; exactly as today (p1–p8 in trial_card_test stay green)
(screen-of ndc size min-y)  ; => {:x :y :visible?}
```
- `ndc` `[x y z]`, `size` `[w h]`: same validity rules as today. Invalid ndc or size →
  `{:x 0 :y 0 :visible? false}` whatever `min-y` is.
- `min-y` nil or not a finite number (NaN, ±Infinity, a string) → **identical to the
  2-arity result**.
- `min-y` finite: px as today, `px-x = (x+1)/2·w`, `px-y = (1−y)/2·h`.
  - `visible?` = `(and (<= -1 x 1) (<= -1 y) (<= z 1))` on the ndc values.
    The **top bound is dropped**: an anchor above the
    top edge is still visible. Off the left/right, below the bottom, or behind the camera
    stays hidden.
  - When visible: `:y` = `(max px-y min-y)` (pinned, so the card's bottom edge is never
    above `min-y`; this also catches an anchor just inside the top whose card would hang off).
    `:x` = `px-x`.
  - When hidden: the result is **exactly** the 2-arity result (same `:x`, `:y`, `false`).
- Pure: no DOM, no js/Date. Docstring updated.

### B.3 `pm.pick/pin-min-y` (new)

```clojure
(pin-min-y base-ndc card-h)   ; => number | nil
```
"Is the item itself on screen vertically?" decided from its **base** (the build group's
world position, projected): the anchor sits above the base, so the item reaches the screen
unless its base is above the top edge or behind the camera.
- Returns `(+ card-h card-pin-gap-px)` when `base-ndc` is a vector of 3 finite numbers
  `[bx by bz]` with `(<= by 1)` and `(<= bz 1)`, and `card-h` is a finite number `>= 0`.
  `bx` is not checked (the anchor's own x decides left/right).
- Otherwise nil (so `screen-of` behaves as the 2-arity: an anchor above the top is hidden).

### B.4 `pm.ui/trial-card-height` (new, DOM, untested)

```clojure
(trial-card-height)   ; => the #pm-trial-card element's offsetHeight in px (0 while hidden/not yet created)
```
Uses `trial-card-el`. (While the card is `display:none` the height is 0, so the first
frame it appears it may sit 8 px from the top; from the next frame it's measured. Fine.)

### B.5 `pm.core/trial-card!` (changed, glue only)

In the `if-let [{:keys [id]} card]` branch:
```clojure
size  [(.-width rect) (.-height rect)]
base  (when g (t/project-ndc (.-camera h) (t/world-pos g)))
min-y (pick/pin-min-y base (ui/trial-card-height))
p     (pick/screen-of ndc size min-y)
```
Everything else in `trial-card!` stays. `:y` is still the card's bottom edge
(`translate(-50%,-100%)`), so a pinned card's top is `card-pin-gap-px` below the canvas top.

---

## C. A real build's sign doesn't say "proposed"

### C.1 `pm.blueprint/sign-summary` (new)

```clojure
(sign-summary bp)   ; => string (or bp's :summary as-is when it isn't a string)
```
- When `(= :real (:status bp))` and `(:summary bp)` is a string: the summary with one
  leading `proposed:` dropped, case-insensitive, along with any whitespace right after the
  colon: `(str/replace-first summary #"(?i)^proposed:\s*" "")`. Keep the pattern as a
  private def (it's the summary format's convention, not a name).
- Any other status (`:pending`, `:approved`, `:removing`, `:rejected`, nil): `(:summary bp)`
  unchanged. Non-string summary: returned unchanged.
- Only a *leading* prefix with the colon: "the proposed: path" and "proposed a gate" are
  unchanged; "proposed: proposed: x" loses only the first.

### C.2 `pm.blueprint/sign-text` (changed)

- First line: `(sign-summary bp)` instead of `(:summary bp)`.
- A `:real` bp has **no second line** (today it falls to the `:else` hint, "click to
  build · right-click to dismiss", which is wrong for a real build). So for `:real`,
  `sign-text` = `(sign-summary bp)`.
- Pending (hint / failed / steep), trial (`:approved`) and `:removing` signs: unchanged,
  byte for byte.

`built-plan` is unchanged: a `:real` item still carries no `:label`/`:text`.

**Note for Hal (headless):** today a `:real` build draws no sign (`built-plan` gives it
none, `pm.scene`'s `:real` branch hides an old trial sprite), and the removal preview's
sign keeps "proposed: …" because the quest keeps `:removing` unchanged. If the
"proposed:" you saw while real was on the red removal preview, that's the one place this
round leaves it.

---

## 4. Test cases

Fixtures for the new file `test/pm/tidy_after_remove_test.cljs` (requires `pm.plan :as p`,
`pm.pick :as pk`, `pm.blueprint :as bp`, `pm.world :as w`, `pm.content :as c`):
```clojure
(def WE (w/load-estate w/estate-edn))          ; the shipped estate
(def TXT {:hint "click to build" :trial "trial: try it out" :removing "will be removed" :failed "won't build"})
(def BL {:id :lamp    :at [1 2 3] :summary "proposed: a lamp post"})
(def BB {:id :bench   :at [0 0 0] :summary "Proposed: a bench"})     ; the second blueprint
(def BP {:id :planter :at [5 0 5] :summary "a planter"})             ; no prefix
(def WB (bp/apply-real-builds {:blueprint-text TXT :blueprints [BL BB BP]} {:real [:lamp :bench :planter]}))
(defn bp-of [w id] (first (filter #(= id (:id %)) (bp/all w))))
(def LOOK {:parts [{:shape :box :size [0.1 1.4 0.1]  :at [0 0.7 0] :color :brown}
                   {:shape :box :size [0.9 0.4 0.06] :at [0 1.3 0] :color :tin}]})
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-9) (map - a b))))
```

### 4.1 Existing `plan_test.cljs` (tester rewrites these two; `t41` stays as is)

- **t42-lobby-real-estate** becomes "the shipped lobby draws nothing":
  `(nil? (p/lobby-item W))`; `(= [2.7 6.18 -23.6] (get-in W [:lobby :at]))`;
  `(= 0 (get-in W [:lobby :yaw]))`; `(nil? (get-in W [:lobby :look]))`;
  `(nil? (get-in W [:lobby :text]))`.
- **t43-lobby-text-falls-back-…** keeps its two assertions but gives each lobby a look:
  `(= "" (:text (p/lobby-item {:lobby {:at [0 0 0] :look LOOK}})))` and
  `(= "" (:text (p/lobby-item {:lobby {:at [0 0 0] :look LOOK :text 7}})))`
  (define `LOOK` locally in plan_test as above).

### 4.2 Lobby (new file)

1. **L1 no look, no text → nil:** `(nil? (p/lobby-item {:lobby {:at [1 2 3] :yaw 0}}))`.
2. **L2 text but no look → nil:** `(nil? (p/lobby-item {:lobby {:at [1 2 3] :text "Lobby"}}))`.
3. **L3 look present → today's item:** `(p/lobby-item {:lobby {:at [1 2 3] :yaw 90 :look LOOK :text "Lobby"}})`
   → `:key [:lobby :box]`, `:at [1 2 3]`, `:yaw 90`, `(count :parts)` = 2, `:label :sign`,
   `:text "Lobby"`, `:center` near `[1 2.75 3]` (top = max(0.7+0.7, 1.3+0.2) = 1.5),
   `:label-at` near `[1 3.8 3]` (2 + 1.5 + 0.3).
4. **L4 shipped lobby keys:** `(= #{:at :yaw} (set (keys (:lobby WE))))`; `(nil? (p/lobby-item WE))`.
5. **L5 the other fixtures still draw** (nothing lobby-specific leaked into the shared
   helpers): `(= [:workshop :box] (:key (p/workshop-item WE)))`,
   `(= [:mailbox :box] (:key (p/mailbox-item WE c/sample-inbox)))`,
   `(= [:phone-booth :box] (:key (p/phone-booth-item WE)))`.
6. **L6 still an errand spot:** `(w/errand-target WE "lobby" [] [2.7 -20.6] 1.2)` → near
   `[2.7 -22.4]` (dz = −23.6 − (−20.6) = −3, d = 3; z = −23.6 + 1.2·3/3 = −22.4; x = 2.7).
7. **L7 already close:** `(w/errand-target WE "lobby" [] [2.7 -23.0] 1.2)` → `[2.7 -23.0]`
   (d = 0.6 ≤ 1.2, stays put).
8. **L8 the errand event to the lobby is recorded:**
   `(get-in (w/apply-event WE {:type :agent-event :agent "hal" :kind :errand :where "lobby" :text "Tea's on." :source SRC} 1800000000000) [:errands "hal"])`
   → `{:where "lobby" :text "Tea's on." :since 1800000000000 :state :pending}`. Use the same
   `SRC` `world_test.cljs` uses: `(def SRC {:agent "hal-say" :session "t" :machine "local"})`.

### 4.3 `screen-of` with min-y (new file; size `[800 600]` unless said)

9. **P1 anchor above the top → pinned:** `(pk/screen-of [0 1.5 0.5] [800 600] 68)` →
   `{:x 400 :y 68 :visible? true}` (raw y = (1−1.5)/2·600 = −150).
10. **P2 far above, off-centre:** `(pk/screen-of [-0.5 3 0.2] [800 600] 68)` → `{:x 200 :y 68 :visible? true}`.
11. **P3 anchor exactly at the top edge:** `(pk/screen-of [0 1 0.5] [800 600] 68)` → `{:x 400 :y 68 :visible? true}`.
12. **P4 inside, near the top (card would hang off):** `(pk/screen-of [0.5 0.9 0.5] [800 600] 68)` → `{:x 600 :y 68 :visible? true}` (raw y ≈ 30).
13. **P5 inside, clear of min-y → unchanged:** `(pk/screen-of [0 0 0.5] [800 600] 68)` → `{:x 400 :y 300 :visible? true}`, and
    `(pk/screen-of [0.5 -0.5 0.9] [800 600] 68)` → `{:x 600 :y 450 :visible? true}` (= p1/p2's 2-arity values).
14. **P6 bottom edge still visible:** `(pk/screen-of [0 -1 0.5] [800 600] 68)` → `{:x 400 :y 600 :visible? true}`.
15. **P7 off the right / left → hidden, same as 2-arity:** for ndc `[1.2 1.5 0.5]`, `[1.2 0 0.5]`, `[-1.01 0 0.5]`:
    `(= (pk/screen-of n [800 600]) (pk/screen-of n [800 600] 68))` and `:visible?` false.
16. **P8 below the bottom → hidden:** `(pk/screen-of [0 -1.2 0.5] [800 600] 68)` equals the 2-arity result; `:visible?` false.
17. **P9 behind the camera → hidden:** `(pk/screen-of [0 1.5 1.2] [800 600] 68)` → `{:x 400 :y -150 :visible? false}`.
18. **P10 no / bad min-y → 2-arity:** for `m` in `nil`, `js/NaN`, `js/Infinity`, `"68"`:
    `(pk/screen-of [0 1.5 0.5] [800 600] m)` → `{:x 400 :y -150 :visible? false}`; and
    `(pk/screen-of [0 0 0.5] [800 600] m)` → `{:x 400 :y 300 :visible? true}`.
19. **P11 bad ndc / size with min-y:** `(pk/screen-of nil [800 600] 68)`, `(pk/screen-of [0 0] [800 600] 68)`,
    `(pk/screen-of [js/NaN 0 0] [800 600] 68)`, `(pk/screen-of [0 0 0] [0 600] 68)`, `(pk/screen-of [0 0 0] nil 68)` → each `{:x 0 :y 0 :visible? false}`.
20. **P12 the gap:** `(= 8 pk/card-pin-gap-px)`.

### 4.4 `pin-min-y` (new file)

21. **M1 base on screen:** `(pk/pin-min-y [0 0 0.5] 52)` → `60`.
22. **M2 base below the bottom (tall item fills the screen):** `(pk/pin-min-y [0 -1.5 0.5] 52)` → `60`.
23. **M3 base at the top edge:** `(pk/pin-min-y [0 1 0.5] 52)` → `60`.
24. **M4 base above the top (whole item above):** `(pk/pin-min-y [0 1.01 0.5] 52)` → `nil`.
25. **M5 base behind the camera:** `(pk/pin-min-y [0 0 1.2] 52)` → `nil`.
26. **M6 base off the side (x not checked):** `(pk/pin-min-y [1.5 0 0.5] 52)` → `60`.
27. **M7 card not measured yet:** `(pk/pin-min-y [0 0 0.5] 0)` → `8`.
28. **M8 bad base:** `nil`, `[0 0]`, `[js/NaN 0 0]`, `"x"` with card-h 52 → each `nil`.
29. **M9 bad card-h:** base `[0 0 0.5]` with `nil`, `-1`, `js/NaN`, `"52"` → each `nil`.
30. **M10 together, item on screen:** `(pk/screen-of [0 1.5 0.5] [800 600] (pk/pin-min-y [0 -0.3 0.5] 52))` → `{:x 400 :y 60 :visible? true}`.
31. **M11 together, item above the top:** `(pk/screen-of [0 1.5 0.5] [800 600] (pk/pin-min-y [0 1.2 0.5] 52))` → `{:x 400 :y -150 :visible? false}`.

### 4.5 Real-build summary (new file)

32. **R1** `(bp/sign-summary {:summary "proposed: a lamp post" :status :real})` → `"a lamp post"`.
33. **R2 case-insensitive:** `"Proposed: a bench"` → `"a bench"`; `"PROPOSED:a pond"` → `"a pond"` (both `:status :real`).
34. **R3 extra spaces:** `"proposed:   a well"` `:real` → `"a well"`.
35. **R4 no prefix:** `"a planter"` `:real` → `"a planter"`.
36. **R5 not leading / no colon:** `"the proposed: path"` → unchanged; `"proposed a gate"` → unchanged (`:real`).
37. **R6 only one:** `"proposed: proposed: x"` `:real` → `"proposed: x"`.
38. **R7 prefix only:** `"proposed:"` `:real` → `""`.
39. **R8 other statuses unchanged:** with summary `"proposed: a lamp post"` and `:status` each of
    `:pending`, `:approved`, `:removing`, `:rejected`, and no `:status` → `"proposed: a lamp post"`.
40. **R9 non-string summary:** `(bp/sign-summary {:status :real})` → `nil`; `{:summary 7 :status :real}` → `7`.

### 4.6 Signs (new file)

41. **G1 real, prefix:** `(bp/sign-text WB (bp-of WB :lamp))` → `"a lamp post"` (no second line).
42. **G2 the second real build:** `(bp/sign-text WB (bp-of WB :bench))` → `"a bench"`.
43. **G3 real, no prefix:** `(bp/sign-text WB (bp-of WB :planter))` → `"a planter"`.
44. **G4 real, no wording in the data:** `(bp/sign-text {} (assoc BL :status :real))` → `"a lamp post"`.
45. **G5 pending unchanged:** `(:text (first (bp/ghost-plan {:blueprint-text TXT :blueprints [BL]})))` → `"proposed: a lamp post\nclick to build"`.
46. **G6 failed unchanged:** `(:text (first (bp/ghost-plan (bp/mark-failed {:blueprint-text TXT :blueprints [BL]} :lamp))))` → `"proposed: a lamp post\nwon't build"`.
47. **G7 trial unchanged:** `(:text (first (bp/built-plan (bp/approve-blueprint {:blueprint-text TXT :blueprints [BL]} :lamp))))` → `"proposed: a lamp post\ntrial: try it out"`; same for BB → `"Proposed: a bench\ntrial: try it out"`.
48. **G8 removing unchanged:** `(:text (first (filter #(= [:blueprint :lamp] (:key %)) (bp/built-plan (bp/start-removal WB :lamp)))))` → `"proposed: a lamp post\nwill be removed"`.
49. **G9 a real build still has no sign in the plan:** the `[:blueprint :lamp]` and `[:blueprint :bench]` items of `(bp/built-plan WB)` have `nil` `:label` and `nil` `:text`.
50. **G10 shipped blueprints, made real:** with
    `WR (bp/apply-real-builds WE {:real [:bench-by-mailbox :lamp-post-by-canal]})`:
    `(bp/sign-text WR (bp-of WR :bench-by-mailbox))` → `"a bench by the mailbox"`,
    `(bp/sign-text WR (bp-of WR :lamp-post-by-canal))` → `"a lamp post by the canal"`.

50 numbered cases (plus the two rewrites in §4.1). Existing tests that must stay green
unchanged: `trial_card_test` p1–p8 (2-arity `screen-of`), `blueprint_test` t20,
`blueprint_seat_test` (pending steep sign), `remove_build_test` p1 (removing sign),
`world_test` t30c (errands), `plan_test` t41.

---

## 5. Docs (le-docteur)

- **TASKS.md:** a new row right after the T-15 row:
  `- [x] Nov 23 (crew round tidy-after-remove-v0) T-16 Tidy-ups after removal (Michael, Nov 23: "ya just remove the sign"). The lobby's signpost by the cottage is gone; the lobby is still where errands go (\`bb hal-say --at lobby\`). Up close to a tall build, its card stays pinned at the top of the screen instead of vanishing. A real build's sign drops "proposed:" from its summary.`
- **docs/GDD.md §2.4**, the Lobby row (keep the row): 
  `| **Lobby** and **your kitchen** | home; where agents bring their questions. It's the kit's cottage, with no sign (Nov 23): an errand spot, not a drawn item | — |`

## 6. Builder note (run dir) must tell Hal what to look at headless

- No lobby post/board/label at the cottage steps (`[:lobby :box]` absent from the scene);
  the crew key's Lobby row still flies to the cottage; `bb hal-say --at lobby` still walks there.
- Up close under a tall build (e.g. the lamp post as a trial, camera at its foot looking
  up): the card sits `card-pin-gap-px` below the canvas top instead of vanishing; pan the
  build off the left/right/bottom or turn away: hidden as before.
- A real build's `sign-text` reads without "proposed:" (and see the note in §C.2: real
  builds draw no sign; the removal preview keeps its "proposed:" first line by design).

## 7. Out of scope

Agents walking to the lobby with questions; any other sign; the kit's cottage; the
removal preview's wording; giving real builds a drawn sign.
