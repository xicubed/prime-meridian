# Spec: content in the game

Quest: `.crew/quests/content-in-game.md`. Round dir: `crew/runs/content-in-game-20261106-1852/`.

The three generated files reach the screen:
- Flora's and Juniper's bubbles and signs come from `mirror-lines.edn`, picked by day.
- Hal greets the player at start and speaks after a counted chore.
- The HUD shows the pretend mailbox.

Everything new is pure, plain data. Code never names a mirror.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/content.cljs` (new), `src/pm/world.cljs`, `src/pm/plan.cljs`, `src/pm/core.cljs`, `resources/estate.edn`, `README.md` (one sentence) | everything else, especially `resources/content/*.edn` (generated), `content/`, `src/pm/overlay.cljs`, `src/pm/scene.cljs`, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/content_test.cljs` (new), `test/pm/fixtures.cljs` (new), and the setup lines E1–E3 in `test/pm/world_test.cljs`, `test/pm/plan_test.cljs`, `test/pm/overlay_test.cljs` | every `src/` and `resources/` file. No existing assertion changes. |

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. No new npm deps. 0 warnings.

## 1. Data

### 1.1 Generated files (read-only; shapes as they are today)

```clojure
;; resources/content/hal-lines.edn: event -> variants
{:jack-in    ["Welcome back." "You're in. Let's see what's waiting for you." ...]   ; 5
 :chore-done ["{name}'s {chore}: done. That one counts." ...]                       ; 5
 ...}                                                                               ; other events unused this round
;; resources/content/mirror-lines.edn
{:mirrors {:flora   {:hungry [...5] :fed [...5] :low ["Worms left: {stock}" ...5] :stock-sign ["Worms: {stock}" ...5]}
           :juniper {:hungry [...5] :fed [...5] :low ["Bales left: {bales}" ...5] :stock-sign [...5]}
           :biscuit {...}}
 :modes {:memorial {...} :virtual {...}}}
;; resources/content/sample-inbox.edn
{:mailbox "…" :messages [{:id :brief-1 :kind :hal-brief ... :sample true} ...
                         {:id :bait-1 :kind :suspicious ... :clerk-note "Flagged: ..."}]}
```

A **variant list** is either a vector (or list) of strings or a single string.

### 1.2 Rules reference lines by key (`resources/estate.edn`, builder)

A rule's `:say`/`:sign` and a placed item's `:idle` may now be a **keyword**. A keyword means "this mirror's line at that key": `[:mirrors <mirror-id> <key>]` in the line table. A string still works exactly as before (filled from `:state`). Change only these values:

```clojure
;; Flora
:rules [{:when [:due? :feed]  :say  :hungry}
        {:when [:<= :stock 2] :sign :low}]
;; Flora's jar
{:id :jar ... :label :sign :idle :stock-sign ...}
;; Juniper
:rules [{:when [:due? :hay]   :say  :hungry}
        {:when [:<= :bales 3] :sign :low}]
```

Add one comment line above `:mirrors`:
`;; :say/:sign/:idle: a string filled from :state, or a keyword naming this mirror's line in resources/content/mirror-lines.edn`.

Juniper has no `:idle` item. `:fed` lines stay unused this round.

### 1.3 The line table a world speaks from

`(:lines world)` holds a table shaped like `mirror-lines.edn`, used when it is a map. Otherwise the world uses `pm.content/mirror-lines`. Core never sets `:lines`; it exists so tests (and later a language switch) can pin a table. Because the default is read live rather than stored in the world, a regenerated file shows up after a hot reload.

### 1.4 The seed (stable per day, never per frame)

- Mirror lines: `seed = (day-number now) + (name-code mirror-id)`.
  - `day-number` is UTC days, `floor(now / 86400000)`.
  - `name-code` is the sum of the char codes of `(name mirror-id)`.
  - The day rolls over at 16:00 PST (00:00 UTC).
- Variant index: `(mod seed n)`. With n ≥ 2 distinct variants, consecutive days always differ.
- Hal lines: `seed = (day-number now) + (count (:log world))`, computed once when Hal speaks and stored. The HUD only reads the stored text.

Worked values (A = `1794013200000`, Fri 2026-11-06 17:00 PST, which is day 20764 at 01:00 UTC):
- `name-code :flora` = 102+108+111+114+97 = **532**.
- `:juniper` = 106+117+110+105+112+101+114 = **765**.
- `:pet` = **329**. `:fern` = **427**.
- Flora's seed at A = 21296. Juniper's = 21529.

### 1.5 Hal in the world

```clojure
{:hal {:event :jack-in :text "Welcome back." :until 1794013206000}}   ; absent until Hal first speaks
```

`:until` is the time Hal speaks plus `hal-ms` (6000). The HUD shows `Hal: <text>` while `now < :until`.

## 2. `pm.content` (new, `src/pm/content.cljs`, pure, no `pm.*` requires)

Requires: `cljs.reader`, `clojure.string`, `shadow.resource`. Paths without `./` resolve from the classpath root, as `pm.world/estate-edn` does with `"estate.edn"`.

| Signature | Returns / behaviour |
|---|---|
| `(parse text)` | `(reader/read-string text)` in try/catch. Returns the value only if it's a map; otherwise nil. Non-string input → nil. |
| `hal-lines` | `(parse (rc/inline "content/hal-lines.edn"))` |
| `mirror-lines` | `(parse (rc/inline "content/mirror-lines.edn"))` |
| `sample-inbox` | `(parse (rc/inline "content/sample-inbox.edn"))` |
| `hal-events` | `{:jack-in #{} :chore-done #{:name :chore}}`: the Hal events the game uses → the placeholders it fills |
| `hal-ms` | `6000` |
| `(day-number now)` | `(js/Math.floor (/ now 86400000))` when `now` is a finite number; otherwise 0 |
| `(name-code k)` | Sum of the char codes of `(name k)` when `k` is a keyword; otherwise 0 |
| `(fill s vars)` | `s` not a string → nil. Otherwise replace each `{field}` matching `#"\{([a-z0-9-]+)\}"` with `(str v)` for `v = (get vars (keyword field))`. A nil or missing `v` keeps the placeholder visible. Never throws. (This is today's `pm.world/fill` body, moved here.) |
| `(placeholders s)` | Set of keywords of the `{field}`s in `s`. Non-string → `#{}`. |
| `(line table path vars seed)` | See below |
| `(used-lines world)` | Set of `[mirror-id key]` for every keyword `:say`/`:sign` in a mirror's `:rules` and every keyword `:idle` in its `:placed`, over all of `(:mirrors world)` |
| `(missing-lines world table)` | Subset of `(used-lines world)` whose `(get-in table [:mirrors id key])` is not *usable* |
| `(missing-hal table)` | Set of the `hal-events` keys whose `(get table k)` is not *usable* |
| `(hal-say world table event vars now)` | See below |
| `(hal-text world now)` | `(:text (:hal world))` when it is a string, `now` and `:until` are numbers, and `(< now until)`; otherwise nil |
| `(hal-segment world now)` | `(str "Hal: " text)` when `hal-text` is non-nil; otherwise nil |
| `(hal-on-chore world table)` | See below |
| `(mail-counts inbox)` | See below |
| `(mail-line inbox)` | nil when `mail-counts` is nil. Otherwise `"mail M · F flagged"`, or `"mail M"` when F = 0. |
| `(hud-segments world inbox now)` | Vector of the non-nil values of `[(mail-line inbox) (hal-segment world now)]`, in that order |

A value is **usable** when it is a string, or a non-empty sequential whose every element is a string.

### `(line table path vars seed)`

1. `table` not a map, or `path` not `sequential?` → nil.
2. `v = (get-in table path)`.
   - String → `(fill v vars)`.
   - Non-empty sequential → `s` is `seed` when `(integer? seed)`, otherwise 0. Pick `(nth (vec v) (mod s (count v)))`. Return `(fill picked vars)` when `picked` is a string, otherwise nil.
   - Anything else (nil, map, empty vector, number) → nil.

### `(hal-say world table event vars now)`

When `now` is a number and `(line table [event] vars (+ (day-number now) (count (:log world))))` gives a string `t`, return:

```clojure
(assoc world :hal {:event event :text t :until (+ now hal-ms)})
```

It replaces any earlier `:hal`. Otherwise return `world` unchanged (the identical value).

### `(hal-on-chore world table)`

Take `e = (peek (:log world))`. If `(:result e)` is `:counted`, call `hal-say` with:
- `table`, event `:chore-done`, and `now` = `(:at e)`;
- `vars` = `{:name (or (get-in world [:mirrors (:mirror e) :name]) (name (:mirror e))) :chore (name (:chore e))}`.

Otherwise (empty log, or the last entry ignored) return `world` unchanged. Call it right after `pm.world/chore-done`, which always appends exactly one log entry.

### `(mail-counts inbox)`

- `inbox` not a map, or `(:messages inbox)` not `sequential?` → nil.
- Otherwise look only at the messages that are maps; other entries are ignored.
  - **flagged**: a map with a `:clerk-note` that is a non-blank string.
  - **mail**: every other map.
- Return `{:mail M :flagged F}`.

Today's file gives `{:mail 9 :flagged 1}`, which matches the Clerk's own summary ("9 messages, 1 held back").

## 3. `pm.world` changes (builder)

- `(:require ... [pm.content :as c])`.
- `(fill s state)` stays public and becomes `(c/fill s state)`. Existing behaviour is unchanged; a non-string truthy `s` now returns nil instead of throwing.
- New public `(line-for world mirror-id x now)`:

```clojure
(let [state (get-in world [:mirrors mirror-id :state])
      table (let [t (:lines world)] (if (map? t) t c/mirror-lines))]
  (cond (string? x)  (c/fill x state)
        (keyword? x) (c/line table [:mirrors mirror-id x] state
                             (+ (c/day-number now) (c/name-code mirror-id)))
        :else        nil))
```

- `markers`: each held rule maps each of its `:say`/`:sign` values through `line-for`. Keys that resolve to nil are dropped, and a marker left empty is dropped. So `{:say :missing}` yields no marker, and `{:say :missing :sign "x"}` yields `{:sign "x"}`.
- `status-line`, `due?`, `chore-done`, `key-event` and `load-estate` are unchanged (`load-estate` does not add `:lines`).

## 4. `pm.plan` change (builder)

In `scene-plan`, the idle passed to `label-text` is:

```clojure
(let [idle (:idle item)] (if (keyword? idle) (w/line-for world mirror-id idle now) idle))
```

String and other idles pass through exactly as today. `label-text`'s signature and behaviour are unchanged.

## 5. `pm.core` changes (builder; not unit-tested, checked live)

- Require `[pm.content :as content]`.
- `(defn- jack-in! [] (swap! !world content/hal-say content/hal-lines :jack-in {} (js/Date.now)))`.
  - Call it in `start-kit!` just before `(js/requestAnimationFrame frame)`, and in `start-scaffold!`'s renderer callback just before its `requestAnimationFrame`.
  - The greeting runs when the HUD starts, not in `init!`: the kit can take longer than 6 s to load. `reload!` does not greet.
- `on-key!`: `(swap! !world (fn [w] (content/hal-on-chore (world/chore-done w ev) content/hal-lines)))`. The console log of the last entry stays.
- `hud-mirrors-suffix`: after the mirror lines and before the scene summary, append `" · " s` for each `s` in `(content/hud-segments w content/sample-inbox (js/Date.now))`. Example: `… · Flora (Hal) · stock 12 · Any worms going? · mail 9 · 1 flagged · Hal: You're jacked in. Let's get going. · scene: …`.
- The `::state-cache` watch already skips changes where `:mirrors` is identical, so `:hal` writes never touch the cache.

`README.md`: one sentence saying the game reads Hal's lines, the creatures' lines and the sample inbox from `resources/content/*.edn` (generated by `bb content`; never hand-edited).

## 6. Tests

### 6.1 Setup edits to existing files (tester; assertions untouched)

**F0. `test/pm/fixtures.cljs`** (new, `ns pm.fixtures`, not a `-test` ns):

```clojure
(def lines-v0
  "One-variant line table holding the estate's pre-content strings, so tests
   written before the content round keep their exact expected texts."
  {:mirrors {:flora   {:hungry ["I'm hungry"]   :low ["Worms left: {stock}"] :stock-sign ["Worms: {stock}"]}
             :juniper {:hungry ["Hay, please?"] :low ["Bales left: {bales}"] :stock-sign ["Bales: {bales}"]}}})
```

**E1–E3.** In `world_test.cljs`, `plan_test.cljs` and `overlay_test.cljs`:
- add `[pm.fixtures :as fx]` to the requires;
- change `(def W (w/load-estate w/estate-edn))` to `(def W (assoc (w/load-estate w/estate-edn) :lines fx/lines-v0))`.

With one variant per key, the seed doesn't matter, so all 97 existing tests (32 + 34 + 31) keep their expected values.

**E2b.** In `plan_test.cljs` `t17-renamed-copy`, the copy needs lines under its own id. Change the `WP` binding to:

```clojure
(-> W (assoc-in [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet))
      (assoc-in [:lines :mirrors :pet] (get-in W [:lines :mirrors :flora])))
```

Its assertions stay the same.

### 6.2 `test/pm/content_test.cljs` (new, `ns pm.content-test`)

Requires: `cljs.test`, `clojure.set :as set`, `pm.content :as c`, `pm.world :as w`, `pm.plan :as p`, `pm.overlay :as o`.

```clojure
(def A       1794013200000)        ; Fri 2026-11-06 17:00 PST = day 20764, 01:00 UTC
(def DAY-END (+ A 82799999))       ; 1794095999999, last ms of day 20764
(def NEXT    (+ A 82800000))       ; 1794096000000, first ms of day 20765
(def D2      172800000)
(def W  (w/load-estate w/estate-edn))          ; real estate, real table (no :lines)
(def LT {:mirrors {:flora   {:hungry ["h0" "h1" "h2" "h3" "h4"] :low ["low {stock}"]
                             :stock-sign ["jar {stock}" "worms {stock}"]}
                   :juniper {:hungry ["hay0" "hay1" "hay2"] :low ["bales {bales}" "few {bales}"]
                             :stock-sign ["barn {bales}"]}}})
(def WT (assoc W :lines LT))
(def L  {:mirrors {:flora {:hungry ["h0" "h1 {stock}" "h2"]}
                   :juniper {:low "bales {bales}"}
                   :odd {:empty [] :num [7] :mixed ["ok" 7]}}
         :jack-in ["hi" "hello {name}"]})
(def P  [:mirrors :flora :hungry])
(def H  {:jack-in ["hi" "hello" "hey"]
         :chore-done ["{name}: {chore} done" "did {chore} for {name}"]
         :empty []})
(def IB {:mailbox "demo"
         :messages [{:id :a :kind :chore}
                    {:id :b :kind :suspicious :clerk-note "Flagged: bait."}
                    {:id :c :kind :question}
                    "junk"
                    {:id :d :clerk-note ""}
                    {:id :e :clerk-note "  "}]})
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(defn texts [world now] (p/label-texts (p/scene-plan world now)))
(defn st [world id k v] (assoc-in world [:mirrors id :state k] v))
(defn variants [table path] (let [v (get-in table path)] (if (string? v) [v] (vec v))))
(defn filled [table path vars] (set (map #(c/fill % vars) (variants table path))))
(def MX (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 1}
          :rules  [{:when [:< :water 5] :say "thirsty {water}"}
                   {:when [:< :water 5] :sign :dry}
                   {:when [:< :water 5] :say :missing}
                   {:when [:< :water 5] :say :dry :sign "sign {water}"}]
          :placed [{:id :pot  :at [0 0 0] :label :sign :idle :calm}
                   {:id :leaf :at [1 0 0] :label :say  :idle "leaf {water}"}]}]})))
(def MXL (assoc MX :lines {:mirrors {:fern {:dry ["dry {water}"] :calm ["calm {water}"]}}}))
(def FX (w/load-estate (pr-str {:mirrors [{:id :fern :state {} :chores [{:id :mist :every "90m"}]}]})))
(def K  (w/chore-done W (w/key-event W "f" A)))     ; Flora fed by the F key at A (counted)
(def K1 (c/hal-on-chore K H))
(def S  (c/hal-say W H :jack-in {} A))
```

Seeds used below (all hand-checked):

| What | Seed | Picks |
|---|---|---|
| Flora at A | 21296 | 5 variants → 1 · 2 → 0 · 1 → 0 |
| Flora at NEXT | 21297 | 5 → 2 · 2 → 1 |
| Juniper at A | 21529 | 3 → 1 · 2 → 1 |
| Juniper at NEXT | 21530 | 3 → 2 · 2 → 0 |
| Hal at A, log 0 | 20764 | 3 → 1 · 2 → 0 |
| Hal at A, log 1 | 20765 | 2 → 1 |
| Hal at NEXT, log 0 | 20765 | 3 → 2 |
| Hal at A+D2, log 3 | 20766 + 3 = 20769 | 2 → 1 |

#### Content basics

**t01-parse**
- `(c/parse "{:a 1}")` → `{:a 1}`
- `(c/parse ";; GENERATED\n{:a [\"x\"]}")` → `{:a ["x"]}`
- each of `(c/parse "[1 2]")`, `(c/parse "{")`, `(c/parse "")`, `(c/parse nil)`, `(c/parse 7)` → `nil`
- `(map? c/hal-lines)`, `(map? c/mirror-lines)`, `(map? (:mirrors c/mirror-lines))`, `(map? c/sample-inbox)`, `(vector? (:messages c/sample-inbox))` → all true

**t02-fill**
- `(c/fill "a {x} {y-2} {Z} {q}" {:x 1 :y-2 "b"})` → `"a 1 b {Z} {q}"`
- `(c/fill "{n}" {:n 0})` → `"0"`
- `(c/fill "x {a}" nil)` → `"x {a}"`
- `(c/fill nil {})` → `nil`; `(c/fill 7 {})` → `nil`
- `(w/fill "Worms: {stock}" {:stock 15})` → `"Worms: 15"`; `(w/fill nil {})` → `nil`

**t03-day-number**
- `A` → 20764; `DAY-END` → 20764; `NEXT` → 20765
- `0` → 0; `86399999` → 0; `86400000` → 1; `-1` → -1
- `nil`, `##NaN`, `##Inf`, `"5"` → 0

**t04-name-code**
- `:flora` → 532; `:juniper` → 765; `:pet` → 329; `:fern` → 427; `:a` → 97
- `nil` → 0; `"flora"` → 0

**t05-placeholders**
- `"Worms left: {stock}"` → `#{:stock}`
- `"{name}'s {chore}: done. That one counts."` → `#{:name :chore}`
- `"{a} {a}"` → `#{:a}`
- `"Welcome back."`, `"{Z}"` and `nil` → `#{}`

#### `line`

**t06-line-picks-by-seed** (`(c/line L P {} s)`)
- s 0 → `"h0"`; s 1 → `"h1 {stock}"`; s 2 → `"h2"`; s 3 → `"h0"`; s 4 → `"h1 {stock}"`
- s -1 → `"h2"`; s 21296 → `"h2"` (21296 mod 3 = 2)

**t07-line-fills**
- `(c/line L P {:stock 4} 1)` → `"h1 4"`; `{:stock 0}` → `"h1 0"`; `{:stock nil}` → `"h1 {stock}"`
- `(c/line L P nil 1)` → `"h1 {stock}"`
- `(c/line L [:jack-in] {:name "Flora"} 1)` → `"hello Flora"`
- `(c/line L [:jack-in] {:name "Flora" :extra 1} 0)` → `"hi"`

**t08-line-seed-stable**
- `(c/line L P {} 7)` twice → equal, both `"h1 {stock}"`
- seed `nil`, `1.5` or `"2"` → `"h0"` (treated as 0)

**t09-line-missing-paths** (all → `nil`)
- `(c/line L [:mirrors :fern :hungry] {} 0)`; `[:mirrors :flora :fed]`; `[:mirrors :flora]` (a map); `[]` (the table itself); `[:stay-late]`
- path `nil`; path `:jack-in` (not sequential); table `nil`; table `"x"`

**t10-line-shapes**
- `(c/line L [:mirrors :juniper :low] {:bales 3} 9)` → `"bales 3"` (single string, seed ignored)
- `[:mirrors :odd :empty]` seed 0 → `nil`; `[:mirrors :odd :num]` seed 0 → `nil`
- `[:mirrors :odd :mixed]`: seed 0 → `"ok"`, seed 1 → `nil`, seed 2 → `"ok"`

#### Keyword rules in the world (both mirrors)

**t11-line-for**
- `(w/line-for WT :flora :stock-sign A)` → `"jar 15"`; at `NEXT` → `"worms 15"`
- `(w/line-for WT :flora "Worms: {stock}" A)` → `"Worms: 15"`
- `(w/line-for WT :juniper :stock-sign A)` → `"barn 6"`
- `(w/line-for WT :flora :fed A)` → `nil`; `(w/line-for WT :flora 7 A)` → `nil`; `(w/line-for WT :flora nil A)` → `nil`
- `(w/line-for WT :fern :hungry A)` → `nil` (no such mirror)
- `(w/line-for (assoc W :lines {}) :flora :hungry A)` → `nil` (a pinned empty table; no fallback)

**t12-flora-keyword-rules**
- `(w/markers WT :flora A)` → `[{:say "h1"}]`
- `(w/markers (st WT :flora :stock 2) :flora A)` → `[{:say "h1"} {:sign "low 2"}]`
- `(w/markers WT :flora DAY-END)` → `[{:say "h1"}]` (same day, same line)
- `(w/markers WT :flora NEXT)` → `[{:say "h2"}]` (next day, next variant)
- `(w/markers (w/chore-done WT (w/key-event WT "f" A)) :flora (+ A 1000))` → `[]`

**t13-juniper-keyword-rules**
- `(w/markers WT :juniper A)` → `[{:say "hay1"}]`
- `(w/markers (st WT :juniper :bales 3) :juniper A)` → `[{:say "hay1"} {:sign "few 3"}]`
- `(w/markers (st WT :juniper :bales 3) :juniper NEXT)` → `[{:say "hay2"} {:sign "bales 3"}]`
- `(w/markers (st WT :juniper :bales 0) :juniper A)` → `[{:say "hay1"} {:sign "few 0"}]`
- With `JT = (w/chore-done WT (ev :juniper :hay A :key))`:
  - `(w/markers JT :juniper (+ A 1000))` → `[]`
  - `(w/markers JT :juniper (+ A 43200000))` → `[{:say "hay1"}]` (due again, still day 20764)

**t14-status-and-hud-lines**
- `(w/status-line WT :flora A)` → `"Flora · stock 15 · h1"`
- `(w/status-line (st WT :flora :stock 2) :flora A)` → `"Flora · stock 2 · h1 · low 2"`
- `(o/hud-line WT :juniper A)` → `"Juniper (sample) · bales 6 · hay1"`
- `(o/hud-line (o/apply-file WT {:as-of A :mirrors {:flora {:state {:stock 2}}}}) :flora A)` → `"Flora (Hal) · stock 2 · h1 · low 2"`

**t15-scene-labels-keyword-idle**
- `(texts WT A)` → `{[:flora :body] "h1" [:flora :jar] "jar 15" [:juniper :body] "hay1"}`
- `(texts WT NEXT)` → `{[:flora :body] "h2" [:flora :jar] "worms 15" [:juniper :body] "hay2"}`
- `(texts (w/chore-done WT (w/key-event WT "f" A)) (+ A 1000))` → `{[:flora :body] "" [:flora :jar] "jar 14" [:juniper :body] "hay1"}`
- `(texts (st WT :flora :stock 2) A)` → `{[:flora :body] "h1" [:flora :jar] "low 2" [:juniper :body] "hay1"}`

**t16-no-per-frame-flicker**
- `(every? #(= (texts WT A) (texts WT (+ A (* 16 %)))) (range 60))` → true. Same for `W`.
- `(p/label-changes (texts WT A) (p/scene-plan WT (+ A 16)))` → `{}`. Same for `W`.
- `(p/label-changes (texts WT DAY-END) (p/scene-plan WT NEXT))` → `{[:flora :body] "h2" [:flora :jar] "worms 15" [:juniper :body] "hay2"}`

**t17-string-and-keyword-rules-mixed** (a generic mirror; the code names none)
- `(w/markers MXL :fern A)` → `[{:say "thirsty 1"} {:sign "dry 1"} {:say "dry 1" :sign "sign 1"}]`
- `(w/markers MX :fern A)` → `[{:say "thirsty 1"} {:sign "sign 1"}]` (the real table has no `:fern`; string rules still work)
- `(w/status-line MXL :fern A)` → `"fern · thirsty 1 · dry 1 · dry 1 · sign 1"`
- `(texts MXL A)` → `{[:fern :pot] "dry 1 · sign 1" [:fern :leaf] "thirsty 1 · dry 1"}`
- `(texts (st MXL :fern :water 9) A)` → `{[:fern :pot] "calm 9" [:fern :leaf] "leaf 9"}`
- `(texts (st MX :fern :water 9) A)` → `{[:fern :pot] "" [:fern :leaf] "leaf 9"}`

#### The real generated files

These check membership, not exact strings, so regenerating the content doesn't break them. Coverage tests t19, t20 and t28 catch a key that goes missing.

**t18-real-lines-both-mirrors**
- `(w/markers W :flora A)`: one marker; its `:say` ∈ `(filled c/mirror-lines [:mirrors :flora :hungry] {:stock 15})`
- `(second (w/markers (st W :flora :stock 2) :flora A))`: its `:sign` ∈ `(filled c/mirror-lines [:mirrors :flora :low] {:stock 2})`
- `(get (texts W A) [:flora :jar])` ∈ `(filled c/mirror-lines [:mirrors :flora :stock-sign] {:stock 15})`
- `(w/markers W :juniper A)`: one marker; its `:say` ∈ the filled `[:mirrors :juniper :hungry]` set
- `(second (w/markers (st W :juniper :bales 3) :juniper A))`: its `:sign` ∈ `(filled c/mirror-lines [:mirrors :juniper :low] {:bales 3})`
- `(= (w/markers W :flora A) (w/markers W :flora DAY-END))` and the same for `:juniper`; `(= (texts W A) (texts W DAY-END))`
- no value of `(texts (st W :flora :stock 2) A)` contains `"{"`
- `(= (w/markers (assoc W :lines "bad") :flora A) (w/markers W :flora A))` (a non-map `:lines` → the default table)

**t19-coverage-mirror-lines**
- `(c/used-lines W)` → `#{[:flora :hungry] [:flora :low] [:flora :stock-sign] [:juniper :hungry] [:juniper :low]}`. This also proves the estate uses keywords.
- `(c/missing-lines W c/mirror-lines)` → `#{}`
- for every `[id k]` in `(c/used-lines W)` and every `s` in `(variants c/mirror-lines [:mirrors id k])`: `(set/subset? (c/placeholders s) (set (keys (get-in W [:mirrors id :state]))))` → true. So a regenerated `{worms}` typo fails.

**t20-coverage-hal-lines**
- `(set (keys c/hal-events))` → `#{:jack-in :chore-done}`
- `(c/missing-hal c/hal-lines)` → `#{}`
- for each `[e allowed]` in `c/hal-events` and every `s` in `(variants c/hal-lines [e])`: `(set/subset? (c/placeholders s) allowed)` → true

#### Hal in the HUD

**t21-hal-jack-in**
- `(:hal S)` → `{:event :jack-in :text "hello" :until 1794013206000}`
- `(c/hal-text S A)` → `"hello"`; `(c/hal-text S (+ A 5999))` → `"hello"`; `(c/hal-text S (+ A 6000))` → `nil`
- `(c/hal-text W A)` → `nil`; `(c/hal-text S nil)` → `nil`
- `(c/hal-segment S (+ A 1000))` → `"Hal: hello"`; `(c/hal-segment S (+ A 6000))` → `nil`
- `(= (dissoc S :hal) W)` → true; `c/hal-ms` → 6000

**t22-hal-say-edges**
- `(:text (:hal (c/hal-say W H :jack-in {} NEXT)))` → `"hey"`
- `(c/hal-say W H :stay-late {} A)`, `(c/hal-say W H :empty {} A)`, `(c/hal-say W H :jack-in {} nil)` and `(c/hal-say W nil :jack-in {} A)` → each `= W`
- `(:text (:hal (c/hal-say W H :chore-done {:name "Flora" :chore "feed"} A)))` → `"Flora: feed done"`
- `(:hal (c/hal-say S H :chore-done {:name "Juniper" :chore "hay"} (+ A 2000)))` → `{:event :chore-done :text "Juniper: hay done" :until 1794013208000}` (replaces the greeting)
- `(c/hal-text (c/hal-say W c/hal-lines :jack-in {} A) A)` ∈ `(set (variants c/hal-lines [:jack-in]))`

**t23-hal-after-f-flora**
- `(:result (peek (:log K)))` → `:counted`
- `(:hal K1)` → `{:event :chore-done :text "did feed for Flora" :until 1794013206000}`
- With `K2 = (w/chore-done K1 (w/key-event K1 "f" (+ A 1000)))`:
  - `(:result (peek (:log K2)))` → `:ignored`
  - `(c/hal-on-chore K2 H)` → `= K2` (an ignored press says nothing new)
  - `(c/hal-text K2 (+ A 1000))` → `"did feed for Flora"`; `(c/hal-text K2 (+ A 6000))` → `nil`
- `(:hal (c/hal-on-chore (w/chore-done K2 (w/key-event K2 "f" (+ A D2))) H))` → `{:event :chore-done :text "did feed for Flora" :until 1794186006000}`
- `(c/hal-text (c/hal-on-chore K c/hal-lines) A)` ∈ `(filled c/hal-lines [:chore-done] {:name "Flora" :chore "feed"})` and contains no `"{"`

**t24-hal-after-chore-others**
- `(:hal (c/hal-on-chore (w/chore-done W (ev :juniper :hay A :key)) H))` → `{:event :chore-done :text "did hay for Juniper" :until 1794013206000}`
- `(c/hal-text (c/hal-on-chore (w/chore-done FX (ev :fern :mist A :key)) H) A)` → `"did mist for fern"` (no `:name` → the id's name)
- `(c/hal-on-chore W H)` → `= W` (empty log)
- `(let [R (w/chore-done W (ev :rock :feed A :key))] (= (c/hal-on-chore R H) R))` → true

#### Mailbox

**t25-mail-toy**
- `(c/mail-counts IB)` → `{:mail 4 :flagged 1}`; `(c/mail-line IB)` → `"mail 4 · 1 flagged"`
- `{:messages [{:id :a}]}` → `{:mail 1 :flagged 0}`, line `"mail 1"`
- `{:messages []}` → `{:mail 0 :flagged 0}`, line `"mail 0"`
- `{:messages [{:clerk-note "x"} {:clerk-note "y"}]}` → `{:mail 0 :flagged 2}`, line `"mail 0 · 2 flagged"`
- `{:messages (list {:id :a})}` → `{:mail 1 :flagged 0}`
- `nil`, `"x"`, `{}` and `{:messages "x"}` → `mail-counts` and `mail-line` both `nil`

**t26-mail-real-file** (with `{:keys [mail flagged]} (c/mail-counts c/sample-inbox)`)
- `(integer? mail)`, `(pos? mail)`, `(>= flagged 1)`
- `(= (+ mail flagged) (count (:messages c/sample-inbox)))`
- `(c/mail-line c/sample-inbox)` → `(str "mail " mail " · " flagged " flagged")`. With today's file that's `"mail 9 · 1 flagged"`.
- every message with a non-blank string `:clerk-note` has `:kind :suspicious`; every message has `:sample true`

**t27-hud-segments**
- `(c/hud-segments K1 IB (+ A 1000))` → `["mail 4 · 1 flagged" "Hal: did feed for Flora"]`
- `(c/hud-segments K1 IB (+ A 6000))` → `["mail 4 · 1 flagged"]`
- `(c/hud-segments S nil A)` → `["Hal: hello"]`
- `(c/hud-segments W nil A)` → `[]`

**t28-coverage-catches-a-broken-regeneration**
- `(c/missing-lines W (update-in c/mirror-lines [:mirrors :flora] dissoc :low))` → `#{[:flora :low]}`
- `(c/missing-lines W (assoc-in c/mirror-lines [:mirrors :juniper :hungry] []))` → `#{[:juniper :hungry]}`
- `(c/missing-lines W {})` → all five pairs of t19; `(c/missing-lines W nil)` → the same five
- `(c/used-lines MX)` → `#{[:fern :dry] [:fern :missing] [:fern :calm]}`; `(c/missing-lines MX (:lines MXL))` → `#{[:fern :missing]}`
- `(c/missing-hal (dissoc c/hal-lines :jack-in))` → `#{:jack-in}`
- `(c/missing-hal {})` → `#{:jack-in :chore-done}`
- `(c/missing-hal {:jack-in [] :chore-done "x"})` → `#{:jack-in}`
- `(c/missing-hal {:jack-in ["a" 7] :chore-done ["b"]})` → `#{:jack-in}`

**Totals:** 28 new tests in `content_test`; 97 existing tests with setup-only edits; 125 tests after the round.

## 7. Live check (for Hal; from today's files, day 20764 = Nov 6 16:00 → Nov 7 16:00 PST)

- Flora's bubble when due: `"Any worms going?"` (hungry index 1).
- Her jar reads `"Worm jar: N"`, or `"Low worms: N"` when N ≤ 2.
- Juniper's bubble: `"Feels like hay time"` (index 4).
- At start, the HUD shows `Hal: You're jacked in. Let's get going.` for about 6 s (seed 20764, index 4).
- The first counted F (log 1) gives `Hal: Flora's feed: done. That one counts.` (seed 20765, index 0). F only counts while Flora is due; if Hal's state file says she was fed recently, the press is ignored and Hal stays quiet.
- The HUD always shows `mail 9 · 1 flagged`.
- From Nov 7 16:00 PST, every mirror line moves to its next variant.

## 8. Not in this round

- Opening the mailbox; memorial and virtual creatures; Hal's other events.
- `:fed` lines (no rule fires after feeding yet).
- A GDD edit. §2.5's string example stays valid; keyword lines are an addition that Hal may note in the GDD.
