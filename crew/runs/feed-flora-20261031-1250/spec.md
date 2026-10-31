# Spec: feed Flora (the worm loop)

Round `feed-flora` · quest `.crew/quests/feed-flora.md` · architect: un-architecte · 2026-10-31

Sources: GDD §1 (pillars 1–2, "the snap"), §2.1 ("Mark a chore done": one per due window),
§2.5 (mirror shape, "a second example from the same code path"), §3.1 (the `chore-done` event).

## 0. Who touches what

| Crew member | May create/edit | Must not touch |
|---|---|---|
| builder (le-codeur-monde) | `resources/estate.edn` (new), `src/pm/world.cljs` (new), `src/pm/core.cljs` (edit) | anything under `test/`, `shadow-cljs.edn`, `pm.three`, `pm.audio`, GDD |
| tester (le-testeur) | `test/pm/world_test.cljs` (new) | everything else |

`shadow-cljs.edn` already has the `:test` build and `resources`/`test` on `:source-paths`
(uncommitted in the working tree); nobody changes it.

**Rule for both:** no code may name a specific mirror (`:flora`, `:juniper`, worms, hay) except
`resources/estate.edn` and the tests. `pm.core` finds Flora through the estate's `:hud` data.

## 1. Data: `resources/estate.edn`

Exactly this content (comments optional). Tests depend on these values.

```clojure
{:hud {:mirrors [:flora]                 ; mirrors whose status-line goes on the HUD, in order
       :keys    {"f" [:flora :feed]}}    ; lower-case key -> [mirror-id chore-id]
 :mirrors
 [{:id :flora :name "Flora" :kind :creature :model :axolotl :home :canal
   :state  {:source :sample :stock 15 :last-fed nil}
   :rules  [{:when [:due? :feed]  :say  "I'm hungry"}
            {:when [:<= :stock 2] :sign "Worms left: {stock}"}]
   :chores [{:id :feed :every "2d" :at "17:00" :who :me
             :consumes [:stock 1] :done-at :last-fed}]}

  {:id :juniper :name "Juniper" :kind :creature :model :horse :home :barn
   :state  {:source :sample :bales 6 :last-hayed nil}
   :rules  [{:when [:due? :hay]   :say  "Hay, please?"}
            {:when [:<= :bales 3] :sign "Bales left: {bales}"}]
   :chores [{:id :hay :every "12h" :at "07:00" :who :me
             :consumes [:bales 1] :done-at :last-hayed}]}]}
```

Shape notes (additions to GDD §2.5, all optional keys):

- `:state` holds mocked values this round; `:source :sample` marks it as a pretend connection
  (the bridge replaces it later). Times are epoch **ms** or `nil` (never done).
- Chore `:done-at` names the `:state` field that records when the chore was last done. If a chore
  has no `:done-at`, the time is kept at `[:done <chore-id>]` on the mirror instead.
- Chore `:at` (time of day) is carried as data only; `due?` does **not** use it this round
  (it needs a time zone; that's the keeper/reminder's job later).
- A chore with no parseable `:every` (e.g. `{:once true}`) is due until done once, then never again.
- Rule text may contain `{field}` placeholders, filled from the mirror's `:state` (so the jar shows
  the real count, pillar 1). A placeholder whose field is missing or `nil` is left as written.
- `:name` is the display name; without it the HUD uses `(name id)`.
- `:hud` is top-level: which mirrors the HUD lists, and which keys send which chore.

## 2. World shape (what `load-estate` returns)

```clojure
{:hud     {:mirrors [:flora] :keys {"f" [:flora :feed]}}   ; passed through unchanged
 :mirrors {:flora   {:id :flora   ... exactly as in the file ...}
           :juniper {:id :juniper ... exactly as in the file ...}}
 :log     []}
```

`pm.core` merges this into `!world` next to its existing `:t` and `:spin`. All `pm.world`
functions read/write only `:mirrors`, `:log`, `:hud`, and leave other keys alone.

Log entry = the event map as given, plus `:result`, plus `:reason` when ignored:

```clojure
{:event :chore-done :mirror :flora :chore :feed :at 1800000000000 :via :key :who :me :result :counted}
{:event :chore-done :mirror :flora :chore :feed :at 1800000060000 :via :sms :who :me :result :ignored :reason :not-due}
```

## 3. `src/pm/world.cljs`: namespace `pm.world`

Requires: `[cljs.reader :as reader]`, `[clojure.string :as str]`, `[shadow.resource :as rc]`.
No three.js, no `js/Date`, no atoms, no side effects. Every function below is public with exactly
this signature; private helpers are up to the builder.

### `estate-edn`
`(def estate-edn (rc/inline "estate.edn"))`: the file's text, inlined at compile time from the
classpath root (`resources/` is on it). A string.

### `(parse-every s)` → ms integer or `nil`
- `s` matches `^(\d+)([smhdw])$` exactly → number × unit; units `s` 1000, `m` 60000, `h` 3600000,
  `d` 86400000, `w` 604800000.
- Anything else (nil, number, `""`, decimals, upper case, spaces, missing number or unit) → `nil`.

### `(load-estate text)` → world
- `text`: EDN string with a top-level map. Read with `cljs.reader/read-string`.
- Returns the map with `:mirrors` turned from a vector into a map keyed by `:id`, and `:log` set to `[]`.
  Other top-level keys (e.g. `:hud`) pass through unchanged.
- Missing or empty `:mirrors` → `{}`. Mirrors without `:id` are dropped. Duplicate ids: the last wins.
- Invalid EDN: the reader's error propagates (no catch).

### `(due? world mirror-id chore-id now)` → boolean
In order:
1. Mirror or chore (looked up by `:id` in the mirror's `:chores`) not found → `false`.
2. Last-done time (`[:state <:done-at>]`, or `[:done <chore-id>]` when no `:done-at`) is `nil` → `true`.
3. `(parse-every (:every chore))` is `nil` → `false`.
4. Otherwise `(>= (- now last) interval)`. Exactly one interval later **is** due. `now` before `last` → `false`.

### `(chore-done world event)` → world
`event`: `{:event :chore-done :mirror kw :chore kw :at ms :via kw :who kw}` (`:event`, `:via`, `:who`
are only logged). Checks in this order; the first failure ignores the event:

| check | ignored with `:reason` |
|---|---|
| mirror `(:mirror event)` exists | `:unknown-mirror` |
| chore `(:chore event)` exists on it | `:unknown-chore` |
| `(:at event)` is a number | `:no-time` |
| `(due? world mirror chore at)` | `:not-due` |

- Ignored: world unchanged except `(conj log (assoc event :result :ignored :reason r))`.
- Counted:
  1. `:consumes [field n]`: if `[:state field]` is a number, set it to `(max 0 (- v n))`;
     if it's missing or not a number, leave the state alone (don't create the key). No `:consumes`
     means nothing is consumed.
  2. Record `at` at the last-done path (see `due?` step 2).
  3. `(conj log (assoc event :result :counted))`.
- A count of 0 still counts (the feeding happened; the time is recorded; the stock stays 0).

### `(markers world mirror-id now)` → vector of maps
- For each rule in `:rules` order whose `:when` holds, one map: `(select-keys rule [:say :sign])` with
  placeholders filled. Rules with neither key are skipped. Unknown mirror or no rules → `[]`.
- Conditions:
  - `[:due? chore-id]` → `(due? world mirror-id chore-id now)`.
  - `[op field n]`, op one of `:< :<= := :>= :>` → true only when `[:state field]` and `n` are both
    numbers and the comparison holds.
  - Anything else → false.
- Placeholders: `#"\{([a-z0-9-]+)\}"` → `(str v)` where `v` = `(get state (keyword name))`; when `v` is
  `nil`, keep the placeholder text as written.

### `(status-line world mirror-id now)` → string or `nil`
- Unknown mirror → `nil`.
- Parts joined with `" · "` (space, U+00B7, space):
  1. `(:name mirror)`, else `(name mirror-id)`;
  2. counter, if the mirror's **first** chore with a `:consumes` names a field whose state value is
     non-nil: `"<field name> <value>"`, e.g. `"stock 15"`;
  3. each marker's text in order, `:say` before `:sign` within a marker.

### `(key-event world key now)` → event map or `nil`
- Looks up `(str/lower-case key)` in `[:hud :keys]`. Found `[m c]` →
  `{:event :chore-done :mirror m :chore c :at now :via :key :who :me}`. Not found, `key` nil, or
  no `:hud` → `nil`.

## 4. `src/pm/core.cljs` changes (builder)

- Require `[pm.world :as world]`.
- `(defonce !world (atom (merge {:t 0 :spin 0.6} (world/load-estate world/estate-edn))))`.
- HUD: keep the current string exactly (`"Prime Meridian · week 1 scaffold · " backend " · frame " n
  " · t=" t "s"`) and append, for each id in `(get-in @!world [:hud :mirrors])`, `" · "` plus
  `(world/status-line @!world id (js/Date.now))` (skip nil). At start the HUD ends
  `… · t=0.5s · Flora · stock 15 · I'm hungry`.
- Keys: in `init!` only (not `reload!`, so no double listeners), add a `keydown` listener on
  `js/window` calling `(fn [e] (on-key! e))`. `on-key!` ignores `e.repeat` and any ctrl/meta/alt
  (so Cmd-F doesn't feed Flora); otherwise `(world/key-event @!world (.-key e) (js/Date.now))`, and
  when non-nil `(swap! !world world/chore-done ev)` and `js/console.log` the last log entry (pr-str).
  Pressing F once at start → HUD shows `Flora · stock 14`; pressing again → unchanged, console shows
  `:result :ignored :reason :not-due`.
- `^js` hints on the event; 0 warnings.

## 5. Test cases: `test/pm/world_test.cljs`, namespace `pm.world-test`

Require `[cljs.test :refer [deftest is testing]]` and `[pm.world :as w]`. Name each `deftest`
`tNN-<slug>` as listed. Shared values:

```clojure
(def T0 1800000000000)
(def D2  172800000)   ; "2d"
(def H12  43200000)   ; "12h"
(def W   (w/load-estate w/estate-edn))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def fed  (ev :flora :feed T0 :key))
(def hay  (ev :juniper :hay T0 :key))
(def W1  (w/chore-done W fed))          ; Flora fed once at T0
(def fern-edn
  (pr-str {:mirrors [{:id :fern :kind :plant :home :sill
                      :state {:water 5 :sun 3}
                      :rules [{:when [:< :water 5]   :say "thirsty"}
                              {:when [:>= :sun 3]    :sign "sun {sun}, mood {mood}"}
                              {:when [:= :water 5]   :say "ok" :sign "full"}
                              {:when [:> :missing 1] :say "never"}
                              {:when [:due? :nope]   :say "never2"}
                              {:when [:sparkle :water] :say "never3"}
                              {:when [:due? :mist]   :sign "mist me"}
                              {:when [:= :water 5]}]
                      :chores [{:id :mist :every "90m"}
                               {:id :repot :once true}]}]}))
(def F (w/load-estate fern-edn))
```

### parse-every
1. `t01-parse-every-units`: `"2d"`→172800000, `"12h"`→43200000, `"90m"`→5400000, `"30s"`→30000, `"1w"`→604800000, `"0d"`→0.
2. `t02-parse-every-rejects`: `nil`, `5`, `""`, `"1.5h"`, `"2D"`, `" 2d"`, `"d"`, `"2"`, `"2x"` → all `nil`.

### load-estate
3. `t03-load-real-estate`: `(set (keys (:mirrors W)))` = `#{:flora :juniper}`; `(:log W)` = `[]`;
   `(:hud W)` = `{:mirrors [:flora] :keys {"f" [:flora :feed]}}`;
   `(get-in W [:mirrors :flora :state])` = `{:source :sample :stock 15 :last-fed nil}`;
   `(get-in W [:mirrors :juniper :state])` = `{:source :sample :bales 6 :last-hayed nil}`;
   `(get-in W [:mirrors :juniper :id])` = `:juniper`.
4. `t04-real-chores-parse`: for every mirror in W and every chore, `(w/parse-every (:every chore))` is non-nil
   (flora feed → 172800000, juniper hay → 43200000).
5. `t05-load-edges`: `(w/load-estate "{}")` = `{:mirrors {} :log []}`;
   `(w/load-estate "{:mirrors [] :hud {:mirrors []}}")` = `{:mirrors {} :log [] :hud {:mirrors []}}`;
   `(w/load-estate "{:mirrors [{:id :a :n 1} {:id :a :n 2}]}")` = `{:mirrors {:a {:id :a :n 2}} :log []}`;
   `(w/load-estate "{:mirrors [{:kind :rock}]}")` = `{:mirrors {} :log []}`;
   `(w/load-estate "{:mirrors [")` throws (`(is (thrown? :default ...))`).

### due?
6. `t06-flora-never-fed-is-due`: `(w/due? W :flora :feed T0)` = `true`.
7. `t07-flora-window`: `(w/due? W1 :flora :feed (+ T0 172799999))` = `false`; `(+ T0 172800000)` i.e. 1800172800000 → `true`;
   `(- T0 1000)` → `false`.
8. `t08-juniper-window`: `W2 = (w/chore-done W hay)`; `(w/due? W :juniper :hay T0)` = `true`;
   `(w/due? W2 :juniper :hay (+ T0 43199999))` = `false`; `(+ T0 43200000)` i.e. 1800043200000 → `true`.
9. `t09-due-unknowns`: `(w/due? W :flora :walk T0)` = `false`; `(w/due? W :rex :feed T0)` = `false`.
10. `t10-due-no-done-at-and-once`: on F: `(w/due? F :fern :mist 0)` = `true`; `(w/due? F :fern :repot 0)` = `true`.
    `F2 = (w/chore-done F (ev :fern :mist 1000 :key))`: `(get-in F2 [:mirrors :fern :done :mist])` = `1000`;
    `(w/due? F2 :fern :mist 5400999)` = `false`; `(w/due? F2 :fern :mist 5401000)` = `true`.
    `F3 = (w/chore-done F (ev :fern :repot 100 :key))`: `(get-in F3 [:mirrors :fern :done :repot])` = `100`;
    `(w/due? F3 :fern :repot 10000000000000)` = `false`.

### chore-done
11. `t11-feed-flora`: `(get-in W1 [:mirrors :flora :state :stock])` = `14`; `[... :last-fed]` = `1800000000000`;
    `(:log W1)` = `[(assoc fed :result :counted)]`; `(get-in W1 [:mirrors :juniper])` = `(get-in W [:mirrors :juniper])`.
12. `t12-one-per-window`: `e2 = (ev :flora :feed (+ T0 60000) :sms)`, `W1b = (w/chore-done W1 e2)`:
    stock `14`, last-fed `1800000000000`, `(count (:log W1b))` = `2`,
    `(last (:log W1b))` = `(assoc e2 :result :ignored :reason :not-due)`, `(dissoc W1b :log)` = `(dissoc W1 :log)`.
13. `t13-next-window-counts`: `(w/chore-done W1 (ev :flora :feed (+ T0 172800000) :nfc))` → stock `13`,
    last-fed `1800172800000`, last log `:result :counted`.
14. `t14-ignored-unknowns`: for each event, result world minus `:log` = `(dissoc W :log)` and
    `(last (:log result))` = `(assoc e :result :ignored :reason r)`:
    `(ev :rex :feed T0 :key)` → `:unknown-mirror`; `(ev :flora :walk T0 :key)` → `:unknown-chore`;
    `(dissoc fed :at)` → `:no-time`; `(assoc fed :at "soon")` → `:no-time`;
    `{:event :chore-done :chore :feed :at T0}` (no `:mirror`) → `:unknown-mirror`.
15. `t15-never-below-zero`: `Z = (assoc-in W [:mirrors :flora :state :stock] 0)`; `(w/chore-done Z fed)`:
    stock `0`, last-fed `1800000000000`, last log `:result :counted`.
    `Z5 = (-> W (assoc-in [:mirrors :flora :state :stock] 3) (assoc-in [:mirrors :flora :chores 0 :consumes] [:stock 5]))`;
    `(w/chore-done Z5 fed)` → stock `0`.
16. `t16-missing-counter-field`: `N = (update-in W [:mirrors :flora :state] dissoc :stock)`; after
    `(w/chore-done N fed)`: `(contains? (get-in r [:mirrors :flora :state]) :stock)` = `false`, last-fed `1800000000000`, counted.
17. `t17-hay-juniper`: `W2 = (w/chore-done W hay)`: bales `5`, last-hayed `1800000000000`,
    `(get-in W2 [:mirrors :flora])` = `(get-in W [:mirrors :flora])`.
    Inside the window: `(w/chore-done W2 (ev :juniper :hay (+ T0 43199999) :sms))` → bales `5`, last log `:reason :not-due`.
18. `t18-hay-runs-out`: reduce `chore-done` over W with 7 hay events at `(+ T0 (* k 43200000))`, k = 0..6:
    bales after each = `5 4 3 2 1 0 0`; final last-hayed `1800259200000`; all 7 log entries `:result :counted`.
19. `t19-other-world-keys-kept`: `(w/chore-done (assoc W :t 5 :spin 0.6) fed)` has `:t 5` and `:spin 0.6`.

### markers
20. `t20-flora-hungry`: `(w/markers W :flora T0)` = `[{:say "I'm hungry"}]`.
21. `t21-flora-fed-no-markers`: `(w/markers W1 :flora (+ T0 1000))` = `[]`.
22. `t22-flora-low-jar`: `S2 = (assoc-in W [:mirrors :flora :state :stock] 2)`:
    `(w/markers S2 :flora T0)` = `[{:say "I'm hungry"} {:sign "Worms left: 2"}]`;
    stock `3` → `[{:say "I'm hungry"}]`; stock `0` → `[{:say "I'm hungry"} {:sign "Worms left: 0"}]`.
23. `t23-snap`: `S3 = (assoc-in W [:mirrors :flora :state :stock] 3)`; `(w/markers S3 :flora T0)` = `[{:say "I'm hungry"}]`;
    `(w/markers (w/chore-done S3 fed) :flora (+ T0 1))` = `[{:sign "Worms left: 2"}]` (bubble pops, sign appears).
24. `t24-juniper-markers`: `(w/markers W :juniper T0)` = `[{:say "Hay, please?"}]`;
    with bales `3` → `[{:say "Hay, please?"} {:sign "Bales left: 3"}]`;
    after the first 4 events of case 18 (k = 0..3, bales 2, last-hayed `1800129600000`):
    at `(+ T0 129600001)` → `[{:sign "Bales left: 2"}]`; at `(+ T0 172800000)` → `[{:say "Hay, please?"} {:sign "Bales left: 2"}]`.
25. `t25-conditions-and-placeholders`: `(w/markers F :fern 0)` =
    `[{:sign "sun 3, mood {mood}"} {:say "ok" :sign "full"} {:sign "mist me"}]`.
26. `t26-markers-unknowns`: `(w/markers W :rex T0)` = `[]`; `(w/markers (w/load-estate "{:mirrors [{:id :rock}]}") :rock T0)` = `[]`.

### status-line
27. `t27-status-flora`: `(w/status-line W :flora T0)` = `"Flora · stock 15 · I'm hungry"`;
    `(w/status-line W1 :flora (+ T0 1000))` = `"Flora · stock 14"`;
    S2 from case 22 at T0 → `"Flora · stock 2 · I'm hungry · Worms left: 2"`.
28. `t28-status-juniper`: `(w/status-line W :juniper T0)` = `"Juniper · bales 6 · Hay, please?"`.
29. `t29-status-edges`: `(w/status-line F :fern 0)` = `"fern · sun 3, mood {mood} · ok · full · mist me"`;
    rock (case 26) → `"rock"`; `(w/status-line W :rex T0)` = `nil`.

### key-event
30. `t30-key-f`: `(w/key-event W "f" T0)` = `{:event :chore-done :mirror :flora :chore :feed :at 1800000000000 :via :key :who :me}`;
    `"F"` → the same map; `"g"` → `nil`; `nil` → `nil`; `(w/key-event F "f" T0)` → `nil` (no `:hud`).
31. `t31-key-second-mirror`: `WH = (assoc-in W [:hud :keys "h"] [:juniper :hay])`;
    `(w/key-event WH "H" T0)` = `hay`; `(get-in (w/chore-done WH (w/key-event WH "H" T0)) [:mirrors :juniper :state :bales])` = `5`.
32. `t32-key-end-to-end`: `K = (w/chore-done W (w/key-event W "F" T0))`; stock `14`;
    `(w/chore-done K (w/key-event K "f" (+ T0 5000)))` → stock `14`, last log `:reason :not-due`, `:via :key`.

## 6. Out of scope / follow-ups for Hal

- Not in this round: 3D mirrors, bridge, SMS/NFC, restocking, `:who` reminders, `:at` time-of-day, memorial/virtual.
- GDD §2.5 should get one line on the keys added here (`:done-at`, `{field}` placeholders, `:name`,
  top-level `:hud`); the quest limits this round's diff, so that's a manager follow-up, not a crew edit.
