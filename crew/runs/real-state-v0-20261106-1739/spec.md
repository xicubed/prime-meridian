# Spec: real state v0 (Hal's file)

Round `real-state-v0` · quest `.crew/quests/real-state-v0.md` · architect: un-architecte · 2026-11-06

Sources: GDD §1 ("batteries not included": sample parts first, real ones swapped in place),
§2.5 (mirrors are data; a second example from the same code path), §4.6 (the swap to real needs
no code changes), AGENTS.md (one world atom, pure rules, absolute URLs under `<base href="/kit/">`,
no new npm deps, no secrets).

## 0. Who touches what

| Crew member | May create/edit | Must not touch |
|---|---|---|
| builder (un-automate-monde) | `src/pm/overlay.cljs` (new), `src/pm/core.cljs` (edit), `README.md` (edit: the "Run" section only) | `test/`, `src/pm/world.cljs`, `src/pm/plan.cljs`, `src/pm/scene.cljs`, `src/pm/three.cljs`, `resources/estate.edn`, `.gitignore`, `shadow-cljs.edn`, GDD, `public/state/` (never create Hal's file), `assets/`, `public/kit/` |
| tester (le-testeur) | `test/pm/overlay_test.cljs` (new) | everything else, including `world_test.cljs` and `plan_test.cljs` |

- `.gitignore` already lists `/public/state/` (uncommitted in the working tree); nobody changes it.
- `world/status-line` is **unchanged**, so the existing `world_test` t27–t29 still pass. The HUD
  gets the source tag from a new `overlay/hud-line`.
- **No code may name a mirror** (`:flora`, `:juniper`, stock, bales, worms...) outside the tests.
  The overlay works from each mirror's own `:state` and `:chores`.
- `shadow-cljs.edn` already picks up `pm.overlay-test` (`:ns-regexp "-test$"`).

### Decisions this spec makes

1. **A new pure namespace, `pm.overlay`**, rather than growing `pm.world`. It needs only
   `cljs.reader`, `clojure.string` and `pm.world`.
2. **`:source` lives in `:state`**, as it does in the sample estate. The overlay sets
   `[:state :source] :hal`; the file can never set `:source` itself.
3. **A mirror turns `:hal` only when at least one field from the file is accepted.** A listed
   mirror whose fields are all rejected keeps its sample source, so the HUD never claims "(Hal)"
   for data it didn't take.
4. **Types follow the field's current value.** A field named by one of the mirror's chores'
   `:done-at` is a time and accepts a finite number or `nil`. Any other field accepts only the
   same kind as its current value: a finite number, a string, a keyword or a boolean. A field
   whose current value is `nil` and isn't a time accepts nothing. The overlay never adds keys,
   so "current keys" is always the sample's key set.
5. **`:as-of` must be a non-negative integer and must differ from the last applied one**
   (stored top-level as `:state-as-of`). An older but different `:as-of` still applies, since
   Hal's file is the truth; an equal one is a no-op, so an in-game F press survives polling.
6. **A valid new `:as-of` is recorded even if no mirror changes**, so an unchanged file is cheap
   on every later poll.
7. Mirrors the file doesn't list are left alone, even if an earlier file listed them.

## 1. Data

Hal's file (private, gitignored, may not exist), served at `/state/mirrors.edn`:

```clojure
{:as-of 1794013200000                       ; ms; Hal bumps it on every change
 :mirrors {:flora {:state {:stock 37 :last-fed 1794013200000}}}}
```

`1794013200000` is Fri 2026-11-06 17:00 PST (2026-11-07 01:00 UTC). Flora's `:every "2d"` makes
her due at `1794186000000`, Sun 2026-11-08 17:00 PST.

The world after applying it (only these keys change):

```clojure
{:state-as-of 1794013200000
 :mirrors {:flora {... :state {:source :hal :stock 37 :last-fed 1794013200000} ...}
           :juniper {... unchanged: :state {:source :sample :bales 6 :last-hayed nil} ...}}
 ... every other key unchanged (:hud :sounds :log :t :spin ...)}
```

## 2. `src/pm/overlay.cljs`: namespace `pm.overlay` (builder)

Requires `[cljs.reader :as reader]`, `[clojure.string :as str]` (if used), `[pm.world :as w]`.
Pure: no `js/fetch`, no `js/Date`, no atoms. Public functions with exactly these signatures:

### `(parse-file text)` → value or `nil`
- `text` not a string → `nil`.
- Otherwise `(try (reader/read-string text) (catch :default _ nil))`. Any reader error (bad EDN,
  EOF, duplicate map keys, unknown tag) → `nil`. Returns whatever was read, map or not, and
  `apply-file` decides.

### `(apply-file world file)` → world
In order; the first failure returns `world` **unchanged** (the same value):
1. `file` isn't a map.
2. `a` = `(:as-of file)` isn't an integer ≥ 0 (`(and (integer? a) (not (neg? a)))`: rejects nil,
   strings, decimals, negatives and `js/Infinity`).
3. `(= a (:state-as-of world))`.

Then:
4. `entries` = `(:mirrors file)` if it's a map, else `{}`.
5. For each `[id entry]` in `entries` where `(get-in world [:mirrors id])` exists, `entry` is a
   map and `(:state entry)` is a map: `accepted` = the `[k v]` pairs of `(:state entry)` that pass
   `accept?` (below). When `accepted` is non-empty, merge it into `[:mirrors id :state]` and set
   `[:mirrors id :state :source]` to `:hal`. Otherwise leave the mirror alone.
6. `(assoc world :state-as-of a)`.

`accept?` for mirror `m`, key `k`, value `v` (private), where `state` = `(:state m)`,
`times` = `(set (keep :done-at (:chores m)))` and `num?` = `(and (number? v) (js/isFinite v))`:
- `k` is `:source` → false.
- `(contains? state k)` is false → false (the file can't invent fields).
- `k` in `times` → `(or (nil? v) (num? v))`.
- Otherwise, by the current value `cur` = `(get state k)`: a finite number → `(num? v)`;
  a string → `(string? v)`; a keyword → `(keyword? v)`; a boolean → `(boolean? v)`;
  anything else, including `nil` → false.

### `(source-tag source)` → string or `nil`
`:hal` → `"Hal"`; any other keyword → `(name source)` (e.g. `:sample` → `"sample"`); anything
else → `nil`.

### `(hud-line world mirror-id now)` → string or `nil`
- `line` = `(w/status-line world mirror-id now)`; `nil` → `nil`.
- `tag` = `(source-tag (get-in world [:mirrors mirror-id :state :source]))`; `nil` → `line` as is.
- Otherwise insert `" (" tag ")"` right after the name part:
  `name-part` = `(or (:name mirror) (name mirror-id))`, giving
  `(str name-part " (" tag ")" (subs line (count name-part)))`.
  E.g. `"Flora · stock 37"` → `"Flora (Hal) · stock 37"`.

## 3. `src/pm/core.cljs` (builder)

- Require `[pm.overlay :as overlay]`.
- `(def state-url "/state/mirrors.edn")` (absolute: the page has `<base href="/kit/">`).
  `(def state-poll-ms 5000)`.
- `(defonce ^:private !state-poll (atom {:timer nil :busy? false}))`.
- `(defn- fetch-state! [])`: when `:busy?` is false, set it true and run
  `(js/fetch (str state-url "?t=" (js/Date.now)) #js {:cache "no-store"})`. Then: response
  `.ok` → `.text`, else resolve `nil`. Then
  `(when-let [f (overlay/parse-file text)] (swap! !world overlay/apply-file f))`. `.catch` →
  nothing (silent: no console error, no HUD change). `.finally` → `:busy?` false. At most one fetch
  is in flight, so responses can't arrive out of order.
  Optional: when `:state-as-of` changed, one `js/console.info` line such as
  `"Prime Meridian: state from Hal as of <as-of>"`. Never log a failure.
- `(defn- start-state-poll! [])`: if `:timer` is nil, call `fetch-state!` now and store
  `(js/setInterval fetch-state! state-poll-ms)` in `:timer`. The `defonce` atom keeps hot reloads
  from starting a second timer.
- `init!`: call `(start-state-poll!)` after adding the keydown listener and before `kit/load!`, so
  both the kit and the scaffold get it.
- `hud-mirrors-suffix`: replace `world/status-line` with `overlay/hud-line` (same arguments).
- Everything else is unchanged. `on-key!` still changes only the page's world. No new npm deps.

Expected live: with no file, the HUD shows `Flora (sample) · stock 15 · I'm hungry`. With
the file above, `Flora (Hal) · stock 37` and no bubble until Sun 17:00 PST. When Hal writes a
new `:as-of` with `:stock 36`, the HUD shows `Flora (Hal) · stock 36` within about 5 s, with no
reload.

## 4. `README.md`, "Run" section (builder)

Add one paragraph after the code block, in plain words. It should say:
- the game reads a twin's state file at `public/state/mirrors.edn` (served as `/state/mirrors.edn`);
- the file is private, gitignored, and never committed;
- without it, every mirror runs on its sample state;
- how to try it: `mkdir -p public/state`, then save the example from §1 there;
- the HUD then shows `Flora (Hal) · stock 37`;
- the game re-reads the file every 5 s and applies it when `:as-of` changes;
- it can only update fields a mirror already has.

No emails, phone numbers or private paths.

## 5. Test cases: `test/pm/overlay_test.cljs`, namespace `pm.overlay-test`

Require `[cljs.test :refer [deftest is]]`, `[pm.world :as w]`, `[pm.overlay :as o]`. Name each
`deftest` `tNN-<slug>`. Shared values:

```clojure
(def A 1794013200000)                 ; Fri 2026-11-06 17:00 PST
(def D2 172800000)                    ; "2d"
(def W  (w/load-estate w/estate-edn))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def HF {:as-of A :mirrors {:flora {:state {:stock 37 :last-fed A}}}})
(def W2 (o/apply-file W HF))
(defn fl [world] (get-in world [:mirrors :flora :state]))
(defn ju [world] (get-in world [:mirrors :juniper :state]))
(defn on-flora [as-of st] {:as-of as-of :mirrors {:flora {:state st}}})
(def X (w/load-estate (pr-str {:mirrors [{:id :fern :state {:water 5 :sun 3}
                                           :chores [{:id :mist :every "90m"}]}
                                          {:id :panel :state {:text "hi" :on true :mode :a :n 1}}]})))
```

1. `t01-hal-file-applied`: `(fl W2)` = `{:source :hal :stock 37 :last-fed 1794013200000}`;
   `(:state-as-of W2)` = `1794013200000`; `(ju W2)` = `{:source :sample :bales 6 :last-hayed nil}`;
   `(get-in W2 [:mirrors :juniper])` = `(get-in W [:mirrors :juniper])`;
   `(dissoc (get-in W2 [:mirrors :flora]) :state)` = `(dissoc (get-in W [:mirrors :flora]) :state)`;
   `(dissoc W2 :mirrors :state-as-of)` = `(dissoc W :mirrors)`; `(fl W)` = `{:source :sample :stock 15 :last-fed nil}` (input not mutated).
2. `t02-no-bubble-until-due`: `(w/markers W2 :flora (+ A 1000))` = `[]`;
   `(w/markers W2 :flora (+ A 172799999))` = `[]`;
   `(w/markers W2 :flora (+ A D2))` = `[{:say "I'm hungry"}]` (at `1794186000000`, Sun 17:00 PST);
   `(w/due? W2 :flora :feed 1794186000000)` = `true`; `(w/markers W :flora A)` = `[{:say "I'm hungry"}]` (sample, never fed).
3. `t03-hud-line`: `(o/hud-line W :flora A)` = `"Flora (sample) · stock 15 · I'm hungry"`;
   `(o/hud-line W2 :flora (+ A 1000))` = `"Flora (Hal) · stock 37"`;
   `(o/hud-line W2 :flora (+ A D2))` = `"Flora (Hal) · stock 37 · I'm hungry"`;
   `(o/hud-line W :juniper A)` = `"Juniper (sample) · bales 6 · Hay, please?"`;
   `(o/hud-line (w/load-estate "{:mirrors [{:id :rock}]}") :rock A)` = `"rock"` (no source, no tag);
   `(o/hud-line W :rex A)` = `nil`; `(w/status-line W2 :flora (+ A 1000))` = `"Flora · stock 37"` (status-line unchanged).
4. `t04-source-tag`: `(o/source-tag :hal)` = `"Hal"`; `(o/source-tag :sample)` = `"sample"`;
   `(o/source-tag :phone)` = `"phone"`; `(o/source-tag nil)` = `nil`; `(o/source-tag "hal")` = `nil`.
5. `t05-same-as-of-no-change`: `(o/apply-file W2 HF)` = `W2`;
   `K = (w/chore-done W2 (ev :flora :feed (+ A D2) :key))` (an in-game F press when due): `(:stock (fl K))` = `36`,
   `(:last-fed (fl K))` = `1794186000000`; `(o/apply-file K HF)` = `K` (an unchanged file doesn't undo it).
6. `t06-new-as-of-follows`: `H2 = (on-flora (+ A 60000) {:stock 36})`:
   `(fl (o/apply-file W2 H2))` = `{:source :hal :stock 36 :last-fed 1794013200000}` (unlisted fields kept);
   `(:state-as-of (o/apply-file W2 H2))` = `1794013260000`;
   `(fl (o/apply-file K H2))` = `{:source :hal :stock 36 :last-fed 1794186000000}` (K from case 5);
   older but different: `(fl (o/apply-file W2 (on-flora (- A 1) {:stock 40})))` = `{:source :hal :stock 40 :last-fed 1794013200000}`,
   and its `:state-as-of` = `1794013199999`.
7. `t07-cant-invent`: `(fl (o/apply-file W (on-flora A {:stock 37 :mood :happy :weight 80})))` =
   `{:source :hal :stock 37 :last-fed nil}`;
   `R = (o/apply-file W {:as-of A :mirrors {:rex {:state {:stock 1}}}})`: `(:mirrors R)` = `(:mirrors W)`,
   `(contains? (:mirrors R) :rex)` = `false`, `R` = `(assoc W :state-as-of A)`.
8. `t08-source-not-settable`: `(fl (o/apply-file W (on-flora A {:source :sample :stock 37})))` =
   `{:source :hal :stock 37 :last-fed nil}`;
   `(o/apply-file W (on-flora A {:source :phone}))` = `(assoc W :state-as-of A)` (nothing accepted, still `:sample`).
9. `t09-wrong-types`: each of these leaves the world equal to `(assoc W :state-as-of A)`:
   `(on-flora A {:stock "37" :last-fed "yesterday"})`, `(on-flora A {:stock nil})` (stock isn't a time),
   `(on-flora A {:stock js/Infinity})`, `(on-flora A {:stock js/NaN})`, `(on-flora A {:last-fed :today})`,
   `(on-flora A {:last-fed js/Infinity})`.
   Accepted: `(:stock (fl (o/apply-file W (on-flora A {:stock 37.5}))))` = `37.5`;
   a partial file: `(fl (o/apply-file W (on-flora A {:stock 37 :last-fed "x"})))` = `{:source :hal :stock 37 :last-fed nil}`;
   a nil time: `(fl (o/apply-file W2 (on-flora (+ A 1) {:last-fed nil})))` = `{:source :hal :stock 37 :last-fed nil}`.
10. `t10-bad-as-of`: for each `f` in `[{:mirrors {:flora {:state {:stock 37}}}}` (missing),
    `(on-flora "1794013200000" {:stock 37})`, `(on-flora -1 {:stock 37})`, `(on-flora 1.5 {:stock 37})`,
    `(on-flora nil {:stock 37})`, `(on-flora js/Infinity {:stock 37})]`: `(o/apply-file W f)` = `W`.
    `(:stock (fl (o/apply-file W (on-flora 0 {:stock 37}))))` = `37` (0 is a valid `:as-of`).
11. `t11-bad-shapes`: `(o/apply-file W x)` = `W` for `x` in `nil`, `[]`, `"x"`, `5`, `'(1 2)`, `:as-of`.
    Each of these = `(assoc W :state-as-of A)`: `{:as-of A}`, `{:as-of A :mirrors [:flora]}`,
    `{:as-of A :mirrors {:flora 37}}`, `{:as-of A :mirrors {:flora {:state [1]}}}`,
    `{:as-of A :mirrors {:flora {:stock 37}}}` (no `:state` wrapper).
12. `t12-juniper-from-file`: `HJ = {:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed A}}}}`, `WJ = (o/apply-file W HJ)`:
    `(ju WJ)` = `{:source :hal :bales 3 :last-hayed 1794013200000}`; `(fl WJ)` = `(fl W)`;
    `(w/markers WJ :juniper (+ A 1000))` = `[{:sign "Bales left: 3"}]`;
    `(w/markers WJ :juniper (+ A 43200000))` = `[{:say "Hay, please?"} {:sign "Bales left: 3"}]` (12 h later, `1794056400000`);
    `(o/hud-line WJ :juniper (+ A 1000))` = `"Juniper (Hal) · bales 3 · Bales left: 3"`;
    `(o/hud-line WJ :flora A)` = `"Flora (sample) · stock 15 · I'm hungry"`.
13. `t13-both-mirrors`: `HB = {:as-of A :mirrors {:flora {:state {:stock 2}} :juniper {:state {:bales 0}}}}`, `WB = (o/apply-file W HB)`:
    `(fl WB)` = `{:source :hal :stock 2 :last-fed nil}`; `(ju WB)` = `{:source :hal :bales 0 :last-hayed nil}`;
    `(o/hud-line WB :flora A)` = `"Flora (Hal) · stock 2 · I'm hungry · Worms left: 2"`;
    `(o/hud-line WB :juniper A)` = `"Juniper (Hal) · bales 0 · Hay, please? · Bales left: 0"`.
14. `t14-rules-run-on-real-state`: `(w/chore-done W2 (ev :flora :feed (+ A 1000) :key))`: stock `37`,
    `(last (:log ...))` = `(assoc (ev :flora :feed (+ A 1000) :key) :result :ignored :reason :not-due)`;
    `(w/chore-done W2 (ev :flora :feed (+ A D2) :key))`: stock `36`, last-fed `1794186000000`, last log `:result :counted`.
15. `t15-parse-file`: `(o/parse-file "{:as-of 1794013200000 :mirrors {:flora {:state {:stock 37 :last-fed 1794013200000}}}}")` = `HF`;
    `(o/parse-file "; Hal's file\n{:as-of 5}")` = `{:as-of 5}`; `(o/parse-file "[1 2]")` = `[1 2]`;
    `nil` for each of `"{:as-of 1"`, `"{:a 1 :a 2}"`, `"#foo 1"`, `nil`, `42`;
    `(o/parse-file "")` is `nil`, whether the reader returns nil or throws;
    end to end, a 404 page body leaves the world alone: `(o/apply-file W (o/parse-file "<html>404</html>"))` = `W`.
16. `t16-other-world-keys-kept`: `R = (o/apply-file (assoc W :t 5 :spin 0.6) HF)`: `(:t R)` = `5`, `(:spin R)` = `0.6`,
    `(:log R)` = `[]`, `(:hud R)` = `(:hud W)`, `(:sounds R)` = `(:sounds W)`.
17. `t17-renamed-copy`: `WP = (assoc-in W [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet :name "Pet"))`,
    `RP = (o/apply-file WP {:as-of A :mirrors {:pet {:state {:stock 37 :last-fed A}}}})`:
    `(get-in RP [:mirrors :pet :state])` = `{:source :hal :stock 37 :last-fed 1794013200000}`;
    `(fl RP)` = `{:source :sample :stock 15 :last-fed nil}`;
    `(o/hud-line RP :pet (+ A 1000))` = `"Pet (Hal) · stock 37"`; `(o/hud-line RP :flora A)` = `"Flora (sample) · stock 15 · I'm hungry"`.
18. `t18-other-field-kinds`: on X:
    `X2 = (o/apply-file X {:as-of 1 :mirrors {:fern {:state {:water nil :sun 4}}}})`: `(get-in X2 [:mirrors :fern :state])` =
    `{:water 5 :sun 4 :source :hal}` (nil isn't allowed: fern has no `:done-at` times);
    `(o/hud-line X :fern 0)` = `"fern"`; `(o/hud-line X2 :fern 0)` = `"fern (Hal)"`;
    `X3 = (o/apply-file X {:as-of 1 :mirrors {:panel {:state {:text "yo" :on false :mode :b}}}})`:
    `(get-in X3 [:mirrors :panel :state])` = `{:text "yo" :on false :mode :b :n 1 :source :hal}`;
    `(o/apply-file X {:as-of 2 :mirrors {:panel {:state {:text 1 :on "yes" :mode "b" :n "2"}}}})` = `(assoc X :state-as-of 2)`.

There are 18 cases. Every entry point is covered: `parse-file`, `apply-file`, `source-tag` and
`hud-line`, on both mirrors (Juniper via HJ and HB), plus a renamed copy and generic mirrors.

## 6. What's not unit-tested (Hal checks live)

The fetch and poll in `pm.core`: no file means silent sample state; with the file, `Flora (Hal) ·
stock 37` and no bubble; after a file edit (new `:as-of`, stock 36) the HUD follows within about
5 s with no reload; there are no console errors from the game's own code (the browser may
still show its own 404 line for the missing file).

## 7. Out of scope / follow-ups for Hal

- Writing back from the game (F still changes only the page), the feed card UI, and Hal's side
  of updating the file.
- GDD §2.5/§4.6 could gain one line: a twin's file at `/state/mirrors.edn` is the first real
  connection for mirrors (`:source :hal`), can only update fields that already exist, and is
  applied by `:as-of`. That's a manager follow-up.
