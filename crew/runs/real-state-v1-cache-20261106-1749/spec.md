# Spec: real state v1 (the game remembers Hal's last word)

Round `real-state-v1-cache` · quest `.crew/quests/real-state-v1-cache.md` · architect: un-architecte · 2026-11-06

Sources: the quest; last round's spec (`crew/runs/real-state-v0-20261106-1739/spec.md`) and its
code as it stands in the working tree (`src/pm/overlay.cljs`, the `pm.core` poll, the 18 tests in
`test/pm/overlay_test.cljs`); GDD §1/§4.6 (sample first, real swapped in place; Hal is the truth);
AGENTS.md (one world atom, pure rules, no new npm deps, 0 warnings).

## 0. Who touches what

| Crew member | May create/edit | Must not touch |
|---|---|---|
| builder (un-automate-monde) | `src/pm/overlay.cljs` (edit), `src/pm/core.cljs` (edit), `README.md` (edit: one sentence in the state-file paragraph) | `test/`, `src/pm/world.cljs`, `plan.cljs`, `scene.cljs`, `three.cljs`, `resources/estate.edn`, `.gitignore`, `shadow-cljs.edn`, GDD, `public/state/`, `assets/`, `public/kit/` |
| tester (le-testeur) | `test/pm/overlay_test.cljs` (edit: **append** t19–t31; t01–t18 and the shared defs stay as they are) | everything else |

- **All 18 existing overlay tests must pass unchanged.** The design below adds a marker only when
  a cache is applied, and removes it with `dissoc`, which is a no-op on worlds that never had it.
- No code may name a mirror. Storage access lives only in `pm.core`; `pm.overlay` stays pure.

### Decisions this spec makes

1. **The cache has the same shape as Hal's file**: `{:as-of ms :mirrors {id {:state {...}}}}`.
   `cache-of` of a world that has only applied Hal's file returns that file's content
   (case 19), and `apply-cache` reuses `apply-file`'s acceptance rules unchanged.
2. **"Cached" is a marker, not a source.** The mirror's `:source` stays `:hal`. The world gets
   `:state-cached? true` from `apply-cache`, and `apply-file` removes it whenever it reads a
   valid file (any map with a valid `:as-of`). The quest's `:state-live? false` was only an
   example; using a marker that is absent when live keeps every v0 world and test identical.
3. **Reaching the file means a valid file.** A 404, unreadable EDN, a non-map or a bad `:as-of`
   all leave the session cached.
4. **The sample-field guarantee is enforced when the cache is read back.** `cache-of` only sees
   the world, so it can't know which fields the sample has. It writes every field of a `:hal`
   mirror's `:state` except `:source`. `apply-cache` drops any field the freshly loaded sample
   doesn't have, by the same rule as the file. The only way a world gains a field is
   `chore-done` writing to an undeclared `:done-at` (case 27).
5. **`apply-cache` only acts on a world that has no `:state-as-of` yet** (a fresh load). Once a
   file or a cache has spoken, a cache is ignored, so a late or second restore can never undo
   the file.
6. **Only `:hal` mirrors are cached.** A chore done on a sample mirror is still forgotten on
   reload (out of scope).
7. A mirror that came from the cache and isn't in the next file keeps Hal's last values. It
   shows `(Hal)` once the session is live (Hal's last word for it is still the cache).

## 1. Data

Cache text in `localStorage`, key `"pm.state.v1"`, written with `pr-str`:

```clojure
{:as-of 1794013200000 :mirrors {:flora {:state {:stock 36 :last-fed 1794186000000}}}}
```

(Here Hal's file said 37 at `:as-of` A = Fri 17:00 PST, and Michael pressed F at Sun 17:00 PST,
`1794186000000`.)

World keys this round adds or uses (top level, next to `:state-as-of`):

```clojure
{:state-as-of  1794013200000   ; last applied :as-of, from a file or the cache (v0)
 :state-cached? true            ; present only while the state came from the cache and no valid file has been read
 ...}
```

## 2. `src/pm/overlay.cljs` (builder)

Unchanged signatures and behaviour: `parse-file`, `hud-line` (it now passes the cached flag, see
below). Private `accept?` is unchanged. Refactor the merge loop of `apply-file` into a private
helper, e.g. `(merge-entries world entries)` → `[world' changed?]`, where `changed?` is true when
at least one field of at least one mirror was accepted. Both `apply-file` and `apply-cache` use it.

### `(apply-file world file)` → world (two small changes)
1. `file` isn't a map → `world` (as before).
2. `:as-of` isn't an integer ≥ 0 → `world` (as before).
3. `(= a (:state-as-of world))` → **`(dissoc world :state-cached?)`** (no state change; the session is now live).
4. Otherwise merge as before, `(assoc :state-as-of a)`, then **`(dissoc :state-cached?)`**.

On a world without `:state-cached?`, results are identical to v0.

### `(cache-of world)` → map or `nil`
- `a` = `(:state-as-of world)`. If `a` isn't an integer ≥ 0 → `nil`.
- `hal` = every `[id m]` in `(:mirrors world)` with `(= :hal (get-in m [:state :source]))`.
  If there are none → `nil`.
- Else `{:as-of a :mirrors {id {:state (dissoc (:state m) :source)} ...}}` for each mirror in `hal`
  (`nil` values kept, e.g. `:last-fed nil`).

### `(apply-cache world cache)` → world
In order; the first failure returns `world` unchanged:
1. `cache` isn't a map.
2. `a` = `(:as-of cache)` isn't an integer ≥ 0.
3. `(:state-as-of world)` is not `nil` (not a fresh world).
4. `[w' changed?]` = `(merge-entries world (:mirrors cache))`, with `:mirrors` treated as `{}`
   when it isn't a map. Not `changed?` → `world`.

Then `(assoc w' :state-as-of a :state-cached? true)`.

### `(source-tag source)` and `(source-tag source cached?)` → string or `nil`
- 1-arity: unchanged (`:hal` → `"Hal"`, other keyword → its name, else `nil`).
- 2-arity: `(and (= source :hal) cached?)` → `"Hal, cached"`; otherwise the 1-arity result.

### `(hud-line world mirror-id now)`
As v0, but `tag` = `(source-tag source (:state-cached? world))`. Sample mirrors still read
`(sample)` in a cached session.

## 3. `src/pm/core.cljs` (builder)

```clojure
(def cache-key "pm.state.v1")
(defonce ^:private !cache-text (atom nil))   ; last text read or written
```
- `(defn- read-cache [])` → `(try (.getItem js/localStorage cache-key) (catch :default _ nil))`.
- `(defn- write-cache! [text])` → `(try (.setItem js/localStorage cache-key text) (catch :default _ nil))`.
  Both are silent; touching `js/localStorage` at all can throw (private mode, blocked storage,
  quota), so it stays inside the `try`.
- `(defn- restore-cache! [])`: `text` = `(read-cache)`; `(reset! !cache-text text)`;
  `(when-let [c (overlay/parse-file text)] (swap! !world overlay/apply-cache c))`.
- `(defn- on-world-change! [_ _ old new])`: return at once when
  `(and (identical? (:mirrors old) (:mirrors new)) (= (:state-as-of old) (:state-as-of new)))`.
  This is the every-frame `:t` tick, so it costs nothing. Otherwise, when
  `(overlay/cache-of new)` is non-nil, `text` = `(pr-str it)`; if `(not= text @!cache-text)`, reset
  `!cache-text` and `(write-cache! text)`. A nil cache writes nothing and removes nothing.
- `init!`, in this order: keydown listener (as now) → `(restore-cache!)` →
  `(add-watch !world ::state-cache on-world-change!)` → `(start-state-poll!)` → `kit/load!`.
  The cache is applied before the first fetch, and the watch covers both a file being applied
  and a chore being done (F).
- Everything else is unchanged (`fetch-state!` may keep its `console.info` on an `:as-of` change).

Expected live: file present → `Flora (Hal) · stock 37`. Move the file away and reload →
`Flora (Hal, cached) · stock 37`. Put it back → `Flora (Hal) · stock 37` within about 5 s. Press F
when due, then reload with the file unchanged → stock 36 survives; the tag reads `(Hal, cached)`,
then `(Hal)` once the file is read.

## 4. `README.md` (builder)

One sentence added to the state-file paragraph, e.g.: "The game also remembers the last state it
got (plus in-game changes since) in the browser's `localStorage` under `pm.state.v1`, so if the
file is missing on the next start the HUD shows that state as `Flora (Hal, cached)` until the
file is back; a newer file always wins."

## 5. Test cases: append to `test/pm/overlay_test.cljs`

Keep t01–t18 and the shared defs (`A D2 W ev HF W2 fl ju on-flora X`). Add:

```clojure
(def WK (w/chore-done W2 (ev :flora :feed (+ A D2) :key)))     ; F pressed when due: stock 36, last-fed 1794186000000
(def CK {:as-of A :mirrors {:flora {:state {:stock 36 :last-fed 1794186000000}}}})
(def RC (o/apply-cache W (o/parse-file (pr-str (o/cache-of WK)))))   ; restart offline
(def HB {:as-of A :mirrors {:flora {:state {:stock 2}} :juniper {:state {:bales 0}}}})
(def WB (o/apply-file W HB))
(def HJ {:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed A}}}})
```

19. `t19-cache-of`: `(o/cache-of W2)` = `HF`; `(o/cache-of WK)` = `CK`; `(o/cache-of W)` = `nil` (all sample);
    `(o/cache-of (assoc W :state-as-of A))` = `nil` (nothing from Hal); `(o/cache-of (dissoc W2 :state-as-of))` = `nil`;
    `(o/cache-of (assoc W2 :state-as-of "A"))` = `nil`.
20. `t20-cache-of-both-mirrors`: `(o/cache-of WB)` =
    `{:as-of A :mirrors {:flora {:state {:stock 2 :last-fed nil}} :juniper {:state {:bales 0 :last-hayed nil}}}}`;
    `(o/cache-of (o/apply-file W HJ))` = `{:as-of A :mirrors {:juniper {:state {:bales 3 :last-hayed 1794013200000}}}}` (sample Flora not cached).
21. `t21-cache-roundtrip`: `(o/parse-file (pr-str CK))` = `CK`;
    `(fl RC)` = `{:source :hal :stock 36 :last-fed 1794186000000}`; `(:state-as-of RC)` = `1794013200000`;
    `(:state-cached? RC)` = `true`; `(ju RC)` = `{:source :sample :bales 6 :last-hayed nil}`;
    `(dissoc RC :mirrors :state-as-of :state-cached?)` = `(dissoc W :mirrors)`; `(:log RC)` = `[]`.
22. `t22-cached-hud`: `(o/hud-line RC :flora (+ A D2 1000))` = `"Flora (Hal, cached) · stock 36"`;
    `(o/hud-line RC :juniper A)` = `"Juniper (sample) · bales 6 · Hay, please?"`;
    `RC2 = (o/apply-cache W HF)`: `(o/hud-line RC2 :flora (+ A 1000))` = `"Flora (Hal, cached) · stock 37"` (the quest's offline check, not the sample's 15);
    `(o/hud-line RC2 :flora (+ A D2))` = `"Flora (Hal, cached) · stock 37 · I'm hungry"`;
    `(o/source-tag :hal true)` = `"Hal, cached"`; `(o/source-tag :hal false)` = `"Hal"`; `(o/source-tag :hal nil)` = `"Hal"`;
    `(o/source-tag :sample true)` = `"sample"`; `(o/source-tag nil true)` = `nil`.
23. `t23-same-as-of-goes-live`: `L = (o/apply-file RC HF)`: `L` = `(dissoc RC :state-cached?)`;
    `(fl L)` = `{:source :hal :stock 36 :last-fed 1794186000000}` (the local F press is kept);
    `(contains? L :state-cached?)` = `false`; `(o/hud-line L :flora (+ A D2 1000))` = `"Flora (Hal) · stock 36"`;
    applying the file again: `(o/apply-file L HF)` = `L`.
24. `t24-different-file-wins`: `H2 = (on-flora (+ A 60000) {:stock 35 :last-fed (+ A 60000)})`, `N = (o/apply-file RC H2)`:
    `(fl N)` = `{:source :hal :stock 35 :last-fed 1794013260000}`; `(:state-as-of N)` = `1794013260000`;
    `(contains? N :state-cached?)` = `false`; `(o/hud-line N :flora (+ A 61000))` = `"Flora (Hal) · stock 35"`;
    a partial file keeps the cached fields it doesn't list: `(fl (o/apply-file RC (on-flora (+ A 60000) {:stock 35})))` =
    `{:source :hal :stock 35 :last-fed 1794186000000}`;
    older but different also wins: `(:stock (fl (o/apply-file RC (on-flora (- A 1) {:stock 40}))))` = `40`.
25. `t25-unreachable-file-stays-cached`: `(o/apply-file RC f)` = `RC` for `f` in `nil`, `"x"`, `[]`,
    `{:as-of "A"}`, `{:mirrors {}}`, `(on-flora -1 {:stock 1})`, `(o/parse-file "<html>404</html>")`;
    a valid file with nothing accepted still goes live: `(o/apply-file RC {:as-of A})` = `(dissoc RC :state-cached?)`;
    `(o/apply-file RC {:as-of (+ A 1)})` = `(assoc (dissoc RC :state-cached?) :state-as-of 1794013200001)`.
26. `t26-garbage-caches`: `(o/apply-cache W c)` = `W` for `c` in: `nil`, `"x"`, `[]`, `42`, `{}`,
    `(o/parse-file "{:as-of 1")`, `{:as-of "1794013200000" :mirrors {:flora {:state {:stock 37}}}}`,
    `{:as-of -1 :mirrors {:flora {:state {:stock 37}}}}`, `{:as-of 1.5 :mirrors {:flora {:state {:stock 37}}}}`,
    `{:as-of A}`, `{:as-of A :mirrors [:flora]}`, `{:as-of A :mirrors {:rex {:state {:stock 1}}}}`,
    `{:as-of A :mirrors {:flora 37}}`, `{:as-of A :mirrors {:flora {:state {:stock "37" :mood :happy :source :hal}}}}`.
27. `t27-cache-acceptance`: `(fl (o/apply-cache W {:as-of A :mirrors {:flora {:state {:stock 37 :mood :happy :source :sample :last-fed "x"}}}}))` =
    `{:source :hal :stock 37 :last-fed nil}` (only sample fields, the right types, `:source` not settable).
    A field the sample lacks is dropped on read:
    `P = (w/load-estate (pr-str {:mirrors [{:id :pond :state {:n 3} :chores [{:id :top :every "1d" :consumes [:n 1] :done-at :last}]}]}))`;
    `P1 = (w/chore-done (o/apply-file P {:as-of 1 :mirrors {:pond {:state {:n 5}}}}) (ev :pond :top 1000 :key))`:
    `(get-in P1 [:mirrors :pond :state])` = `{:n 4 :source :hal :last 1000}`;
    `(o/cache-of P1)` = `{:as-of 1 :mirrors {:pond {:state {:n 4 :last 1000}}}}`;
    `(get-in (o/apply-cache P (o/cache-of P1)) [:mirrors :pond :state])` = `{:n 4 :source :hal}`.
28. `t28-cache-only-on-fresh-world`: `(o/apply-cache W2 CK)` = `W2` (a file already applied);
    `(o/apply-cache RC HF)` = `RC` (a cache already applied); `(o/apply-cache (assoc W :state-as-of 0) CK)` = `(assoc W :state-as-of 0)`.
29. `t29-both-mirrors-cached-then-live`: `RB = (o/apply-cache W (o/cache-of WB))`:
    `(o/hud-line RB :flora A)` = `"Flora (Hal, cached) · stock 2 · I'm hungry · Worms left: 2"`;
    `(o/hud-line RB :juniper A)` = `"Juniper (Hal, cached) · bales 0 · Hay, please? · Bales left: 0"`;
    `LB = (o/apply-file RB HB)` (same `:as-of`): `(o/hud-line LB :flora A)` = `"Flora (Hal) · stock 2 · I'm hungry · Worms left: 2"`;
    `(o/hud-line LB :juniper A)` = `"Juniper (Hal) · bales 0 · Hay, please? · Bales left: 0"`;
    Juniper alone: `RJ = (o/apply-cache W (o/cache-of (o/apply-file W HJ)))`: `(o/hud-line RJ :juniper (+ A 1000))` =
    `"Juniper (Hal, cached) · bales 3 · Bales left: 3"`; `(o/hud-line RJ :flora A)` = `"Flora (sample) · stock 15 · I'm hungry"`.
30. `t30-local-changes-survive-restarts`: `L = (o/apply-file RC HF)` (case 23);
    `L2 = (w/chore-done L (ev :flora :feed (+ A D2 D2) :key))` (due again Tue 17:00 PST, `1794358800000`):
    `(:stock (fl L2))` = `35`; `(o/cache-of L2)` = `{:as-of A :mirrors {:flora {:state {:stock 35 :last-fed 1794358800000}}}}`;
    a second restart with the file still unchanged: `(fl (o/apply-file (o/apply-cache W (o/cache-of L2)) HF))` =
    `{:source :hal :stock 35 :last-fed 1794358800000}`;
    pressing F when not due changes nothing, so it's nothing to cache: `(o/cache-of (w/chore-done RC (ev :flora :feed (+ A D2 1000) :key)))` = `CK`.
31. `t31-renamed-copy`: `WP = (assoc-in W [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet :name "Pet"))`,
    `RP = (o/apply-file WP {:as-of A :mirrors {:pet {:state {:stock 37 :last-fed A}}}})`:
    `(o/cache-of RP)` = `{:as-of A :mirrors {:pet {:state {:stock 37 :last-fed 1794013200000}}}}`;
    `CP = (o/apply-cache WP (o/cache-of RP))`: `(o/hud-line CP :pet (+ A 1000))` = `"Pet (Hal, cached) · stock 37"`;
    `(o/hud-line CP :flora A)` = `"Flora (sample) · stock 15 · I'm hungry"`.

That makes 31 overlay tests: t01–t18 unchanged, t19–t31 new. Every new entry point is covered
(`cache-of`, `apply-cache`, the 2-arity `source-tag`, the changed `apply-file` and `hud-line`) for
both mirrors, a renamed copy, a generic mirror, garbage caches and the three precedence cases
(no file / same `:as-of` / different `:as-of`).

## 6. What's not unit-tested (Hal checks live)

`localStorage` read/write and the watch in `pm.core`: the offline restart shows
`Flora (Hal, cached) · stock 37`; restoring the file flips it to `(Hal)` within about 5 s; an F
press survives a reload; storage failures (private window) are silent and the game behaves as v0.

## 7. Out of scope / follow-ups for Hal

- Writing back to Hal (F presses reaching his file and reminder), the feed card UI, clearing the
  cache from the UI (for now: delete the `pm.state.v1` key in the browser's dev tools).
- GDD §4.6 could note that the game keeps Hal's last word locally and that Hal's newer file
  always wins. That's a manager follow-up.
