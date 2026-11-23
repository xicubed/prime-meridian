# Spec: live blueprints v0 (TASKS T-13 part b)

Quest: `.crew/quests/live-blueprints-v0.md`. Start commit `b3d6719`.
An agent runs `bb blueprint-add <member> <file.edn>`; within one 5 s state poll the game merges
`state/blueprints.edn` into `!world`, seats only the new blueprints, draws only their groups, and
the author says one line in 💭. No reload, no remount, nothing else moves.

Everything decided in the quest stands; this spec fixes the names, shapes and numbers.

---

## 1. Who touches what

| Who | May edit / create | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | `src/pm/blueprint_file.cljc` (new), `src/pm/blueprint.cljs`, `src/pm/bridge.cljc`, `src/pm/core.cljs`, `src/pm/scene.cljs`, `scripts/blueprint_add.clj` (new), `scripts/crew_bridge.clj`, `bb.edn`; in the run dir: `builder-notes.md` and `bench-by-creek.edn` (the example blueprint) | `test/`, `resources/` (estate.edn and hal-lines.edn stay as they are), `docs/`, `TASKS.md`, `state/` (never run the command against the real `state/`) |
| **tester** (le-testeur) | `test/pm/blueprint_file_test.cljs` (new), `test/pm/blueprint_live_test.cljs` (new), `test/pm/bridge_real_test.cljs` (append `t04`) | everything else; tests never write files (all pure) |
| **le-docteur** | `TASKS.md` (T-13 line), `docs/GDD.md` (§2.1 Approve row) — wording in §9, applied only after the gate passes | code, tests, `resources/`, `state/` |

Builder and tester work in parallel from this spec only. All tested functions are pure.

---

## 2. Data shapes

### 2.1 `state/blueprints.edn` (gitignored: `/state/` is in `.gitignore`)

```clojure
{:as-of 1763937000000                                   ; ms; bumped on every add/remove
 :blueprints [{:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0
               :summary "proposed: a bench by the creek"
               :look {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]}
               :by :un-automate-monde}]}               ; set by the command, never by the file author
```

Only these keys are ever written for a blueprint: `:id :at :yaw :summary :look :by`
(`:yaw`/`:look` only when present in the input). `:status`, `:seated?`, `:seat-drop`,
`:too-steep?`, `:failed?` in an input are dropped.

### 2.2 A file blueprint inside `!world`

The file entry (select-keys above) plus `:from-file? true :status :pending` when it arrives:

```clojure
{:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0 :summary "proposed: a bench by the creek"
 :look {...} :by :un-automate-monde :from-file? true :status :pending}
```

Estate blueprints never carry `:from-file?`; that is how "estate id" is recognised.

### 2.3 New world keys

| Key | Value | Meaning |
|---|---|---|
| `:blueprints-as-of` | `nil` (never read) or integer | `:as-of` of the last file merged; `nil` ⇒ the next merge is the page-load read |
| `:blueprints-announced` | set of ids | every file id ever added this page session (announced or present at load) |

Not cached in localStorage (the caches only take `:mirrors`), so a reload starts at `nil` again.

### 2.4 Talk entry for the author line

`pm.talk/append` input (existing fn, unchanged):
```clojure
{:who "Un Automate" :text "drew a blueprint: proposed: a bench by the creek. Click it to try it."
 :kind :blueprint :event :blueprint-drawn}
```

---

## 3. `pm.blueprint-file` — new, `src/pm/blueprint_file.cljc`

Portable (bb + cljs). Requires only `clojure.string`. Docstring says: the one validator for
blueprints, shared by the game (`pm.blueprint`), the bridge and `bb blueprint-add`.

```clojure
(def state-path "state/blueprints.edn")
(def empty-file {:as-of 0 :blueprints []})
(def id-re     #"[a-z0-9][a-z0-9-]{0,40}")   ; same as pm.bridge's real-id-re (bridge switches to this one)
(def member-re #"[a-z][a-z0-9-]{0,39}")
(def usage "usage: bb blueprint-add <member> <blueprint.edn> | bb blueprint-add --remove <id>")
```

### `(problems bp)` → vector of strings, `[]` when valid
Exactly today's `valid-blueprint?` rules (no new rule, so `pm.blueprint/all` keeps every
blueprint it keeps today):
- not a map → `["not a map"]` (nothing else checked)
- otherwise, in this order, each failing rule adds one string:
  1. `(keyword? (:id bp))` else `":id must be a keyword"`
  2. `:at` a vector of exactly 3 numbers, else `":at must be [x y z], three numbers"`
  3. `:summary` a string, not blank, else `":summary must be a non-blank string"`

### `(valid? bp)` → boolean: `(empty? (problems bp))`

### `(normalize file)` → `{:as-of int :blueprints [map ...]}`
- `file` not a map (incl. nil) → `empty-file`
- `:as-of`: the value when `integer?`, else `0`
- `:blueprints`: when sequential, a vector of its **map** elements in order (non-maps dropped);
  else `[]`. Other top-level keys dropped. Entries are not validated here.

### `(add file estate-ids member bp now)` → `{:ok file' :id id}` | `{:refused reason}`
`file` raw (normalized first), `estate-ids` a set of keywords, `member` a keyword, `now` ms.
Checks in this order, first failure wins:
1. `(problems bp)` non-empty → `{:refused (str "invalid blueprint: " (str/join "; " problems))}`
2. `(re-matches id-re (subs (str id) 1))` fails → `{:refused (str "id " id " must be lowercase letters, digits and dashes")}` (`(str :x)` prints `":x"`; a namespaced id fails because of the `/`)
3. id ∈ `estate-ids` → `{:refused (str "id " id " is already in the estate")}`
4. id already in the file → `{:refused (str "id " id " is already in state/blueprints.edn")}`
5. else `{:ok {:as-of (max now (inc as-of)) :blueprints (conj bps entry)} :id id}` with
   `entry = (assoc (select-keys bp [:id :at :yaw :summary :look]) :by member)`.

`(max now (inc as-of))` guarantees `:as-of` always changes, so the game never misses a write.

### `(remove-id file estate-ids id now)` → `{:ok file' :id id}` | `{:refused reason}`
1. id ∈ `estate-ids` → `{:refused (str "id " id " is in the estate; only state/blueprints.edn entries can be removed")}`
2. id not in the (normalized) file → `{:refused (str "no blueprint " id " in state/blueprints.edn")}`
3. else `{:ok {:as-of (max now (inc as-of)) :blueprints <bps without id, order kept>} :id id}`

### `(parse-args args)` → `{:mode :add :member kw :path s}` | `{:mode :remove :id kw}` | `{:usage usage}`
`args` a seq of strings (may be nil).
- `["--remove" s]`: strip one leading `:` from `s`; if it matches `id-re` → `{:mode :remove :id (keyword s')}`
- `[m p]` (m ≠ `"--remove"`): `m` matches `member-re` and `p` not blank → `{:mode :add :member (keyword m) :path p}`
- anything else → `{:usage usage}`

---

## 4. `pm.blueprint` — `src/pm/blueprint.cljs`

- Require `[pm.blueprint-file :as bf]`. Delete the private `valid-blueprint?`; `all` filters with
  `bf/valid?`. All existing tests must still pass unchanged.

### `(merge-file world file)` → `{:world w' :added [ids] :removed [ids] :announce [ids]}`
`file` is the raw parsed `state/blueprints.edn`, or `nil` when it doesn't exist (404).
1. `f = (bf/normalize file)`. If `(= (:as-of f) (:blueprints-as-of world))` → return
   `{:world world :added [] :removed [] :announce []}` with `world` **identical** (no-op).
2. `first? = (nil? (:blueprints-as-of world))`; `bps = (or (:blueprints world) [])`.
3. `estate-ids` = ids of `bps` entries without `:from-file?`.
4. `incoming` = entries of `f` that are `bf/valid?`, id ∉ `estate-ids`, distinct by id (**first one wins**), in file order.
5. `kept` = `bps` in order, dropping a `:from-file?` entry whose id ∉ incoming ids **unless** its
   status (`:status`, default `:pending`) is `:real` or `:approved`. Kept entries are unchanged
   (status, seat, look, summary — an edited file entry with an existing id changes nothing).
6. `new` = incoming entries whose id ∉ ids of `kept`, each
   `(assoc (select-keys e [:id :at :yaw :summary :look :by]) :from-file? true :status :pending)`.
7. `announced = (or (:blueprints-announced world) #{})`;
   `announce = (if first? [] (ids of new that ∉ announced))`.
8. `w' = (assoc world :blueprints (into kept new) :blueprints-as-of (:as-of f) :blueprints-announced (into announced (map :id new)))`
9. `:added` = ids of `new`; `:removed` = ids dropped in step 5, in their old order.

### `(seat-ids world ids ground-at)` → world
Like `seat-all`, but only blueprints whose id ∈ `(set ids)` go through `seat`. Others unchanged.
`ids` empty/unknown → `=` to `world`.

### `(author-line world id)` → talk-append map (§2.4) | nil
- nil unless blueprint `id` is in `(:blueprints world)` with a keyword `:by`.
- `:who`: the `:name` of the first `(:walkers world)` entry whose `(name (:agent w))` = `(name by)`;
  else `(get-in world [:crew :titles by])` when a non-blank string; else `(name by)`.
- `:text`: `(str "drew a blueprint: " s ". Click it to try it.")` where `s` is `:summary` with
  one trailing `"."` removed if present. Hard-coded with `;; TODO content: move to the content
  pipeline (no hal-lines event fits)` above it (hal-lines.edn is generated; `:blueprint-ready` is
  the twin's tray line, not an author's).
- `:kind :blueprint`, `:event :blueprint-drawn`.

### `(drawable-changes drawn world)` → `{:add [ids] :remove #{ids}}`
`drawn` = set of ids the scene has groups for (nil = #{}).
- `:add`: ids from `(all world)` in order, status ∈ `#{:pending :approved :real}`, id ∉ `drawn`.
- `:remove`: ids in `drawn` that are not ids of any entry in `(all world)`.
(A `:rejected` blueprint is neither: the existing status sync already removed its mesh.)

---

## 5. `pm.bridge` — `src/pm/bridge.cljc`

- Require `[pm.blueprint-file :as bf]`; `real-id-re` uses `bf/id-re` (same pattern).
- `(known-build-ids estate file)` → set: `(set (keep :id (:blueprints estate)))` ∪ ids of the
  entries of `(bf/normalize file)` that are `bf/valid?`. Both args may be nil.

`scripts/crew_bridge.clj` `make-real!`: `(def blueprints-file ...)` beside `builds-file` (same
repo-relative style); `:real-build` uses `(b/known-build-ids e (read-edn blueprints-file))`, read
at request time.

---

## 6. `bb blueprint-add` — `scripts/blueprint_add.clj` + `bb.edn`

`bb.edn` task (keeps exit codes 1 and 2 — plain `shell` would turn them into an exception):
```clojure
blueprint-add {:doc  "An agent proposes a blueprint: its ghost appears in the estate (state/blueprints.edn): bb blueprint-add <member> <blueprint.edn> | bb blueprint-add --remove <id>"
               :task (let [r (apply shell {:continue true} "bb" "scripts/blueprint_add.clj" *command-line-args*)]
                       (System/exit (:exit r)))}
```
Script (all I/O; logic is `bf/*`):
1. `(bf/parse-args *command-line-args*)`; `:usage` → print it to stderr, exit **2**.
2. Target file: env `PM_BLUEPRINTS_FILE` or `bf/state-path` (the override is for hand tests in a temp dir).
3. Read target: missing → nil; exists but not EDN or not a map → stderr
   `blueprint-add: state/blueprints.edn is not readable EDN; fix or delete it`, exit 1.
4. `estate-ids` = `(set (keep :id (:blueprints (edn/read-string (slurp "resources/estate.edn")))))`.
5. Add: read `<path>` with `edn/read-string` (missing/unreadable → stderr
   `blueprint-add: can't read <path>`, exit 1); `(bf/add file estate-ids member bp now)`.
   Remove: `(bf/remove-id file estate-ids id now)`.
6. `:refused` → stderr `blueprint-add: <reason>`, exit **1**.
7. `:ok` → write atomically (`make-parents`, spit `<target>.tmp`, `java.nio.file.Files/move` with
   `ATOMIC_MOVE` + `REPLACE_EXISTING`), print `added <name>` / `removed <name>`
   (`(name id)`, e.g. `added bench-by-creek`), exit 0.

Known limit (say so in the notes, don't fix): two agents adding in the same instant can lose one
write; removing a file blueprint that was made real keeps it this session but it won't come back
after a reload.

---

## 7. `pm.core` / `pm.scene` wiring (hand-tested, not unit-tested)

**core** (all inside the existing non-DEMO `start-state-poll!`; the demo still polls nothing):
1. `(def blueprints-url "/state/blueprints.edn")`; `(defonce ^:private !last-builds (atom nil))`;
   `(defonce ^:private !kit (atom nil))` set in `start-kit!` before `seat-blueprints!`.
2. `fetch-builds!` stores the parsed builds map in `!last-builds` before applying it.
3. `fetch-blueprints!`: GET `?t=now`, `no-store`. 404 → `(apply-blueprint-file! nil)`; 200 and
   `overlay/parse-file` gives a map → `(apply-blueprint-file! f)`; anything else → nothing.
   Called first in each tick (initial call + interval fn): `(fetch-blueprints!) (fetch-state!) (fetch-builds!)`.
4. `apply-blueprint-file! [f]`, synchronous: `r (bp/merge-file @!world f)`; if `(:world r)` is
   identical to `@!world` stop. Else `w = (-> (:world r) (bp/apply-real-builds @!last-builds))`,
   then if `@!kit` has ground meshes, `(bp/seat-ids w (:added r) ground-fn)` (refactor
   `seat-blueprints!` so the ground-at fn comes from one helper); then for each id in
   `(:announce r)` with a non-nil `bp/author-line`, `(update w :talk talk/append line now)`;
   `(reset! !world w)`; `console.info` the added/removed ids. Kit not up yet → `start-kit!`'s
   `seat-all` seats them before `mount!` (seat is idempotent via `:seated?`).

**scene**:
1. `mount!` stores `:bp-drawn` = set of ids of every `[:blueprint id]` item it mounted.
2. In `sync-blueprints!`, **before** the existing status loop:
   `{:keys [add remove]} (bp/drawable-changes (:bp-drawn m) world)`. For each `remove` id: remove
   group `(pr-str [:blueprint id])` if found and its label sprite if any; dissoc from `:labels`,
   `:texts`. For each `add` id: the item with `:key [:blueprint id]` from
   `(into (bp/ghost-plan world) (bp/built-plan world))` → `mount-item!` into root; store its sprite
   in `:labels` and text in `:texts`. Update `:bp-drawn`. The status loop then runs as today (a
   newly added `:real` one gets its glow lit by the existing `:real` branch).
3. Nothing else is created, removed or moved; no remount.

---

## 8. Test cases (hand-checked)

Shared fixtures (each new test file defines what it uses):

```clojure
(def LOOK  {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]}) ; no feet (0.4 ≠ 0.4/2)
(def CB-IN {:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0 :summary "proposed: a bench by the creek" :look LOOK})
(def PL-IN {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path" :look LOOK})
(def CB (assoc CB-IN :by :un-automate-monde))
(def PL (assoc PL-IN :by :le-testeur))          ; second member, second blueprint: nothing is specific to one
(def E-BENCH {:id :bench-by-mailbox :at [4.0 1.66 -6.5] :yaw 90 :summary "proposed: a bench by the mailbox"})
(def E-LAMP  {:id :lamp-post-by-canal :at [3.8 0.63 -5.0] :summary "proposed: a lamp post by the canal"})
(def E #{:bench-by-mailbox :lamp-post-by-canal})
(def W0 {:blueprints [E-BENCH E-LAMP]
         :walkers [{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde}
                   {:id :le-testeur :name "Le Testeur" :agent :le-testeur}]})
(defn fbp [bp] (assoc bp :from-file? true :status :pending))
(defn ids [w] (mapv :id (:blueprints w)))
(def W1 (:world (bp/merge-file W0 {:as-of 1000 :blueprints [CB]})))       ; page load: CB present
(def W2 (:world (bp/merge-file W1 {:as-of 2000 :blueprints [CB PL]})))    ; PL arrives live
(def G  (fn [_ z] (if (neg? z) 1.5 0.5)))                                  ; ground-at
```
Derived (hand-checked): `W1` = W0 + `:blueprints [E-BENCH E-LAMP (fbp CB)]`, `:blueprints-as-of 1000`,
`:blueprints-announced #{:bench-by-creek}`. `W2` = `:blueprints [E-BENCH E-LAMP (fbp CB) (fbp PL)]`,
`:blueprints-as-of 2000`, `:blueprints-announced #{:bench-by-creek :planter}`.

### 8.1 `test/pm/blueprint_file_test.cljs` (`pm.blueprint-file-test`, requires `pm.blueprint-file :as bf`, `pm.blueprint :as bp`)

| # | Call | Expected |
|---|---|---|
| P1 | `(bf/problems CB-IN)`, `(bf/problems CB)`, `(bf/valid? CB-IN)` | `[]`, `[]`, `true` |
| P2 | `(bf/problems :junk)`, `(bf/problems nil)` | `["not a map"]` both |
| P3 | `(bf/problems (dissoc CB-IN :id))`, `(bf/problems (assoc CB-IN :id "bench"))` | `[":id must be a keyword"]` both |
| P4 | `:at` = `[1 2]`, `["x" 2 3]`, `'(1 2 3)` | `[":at must be [x y z], three numbers"]` each |
| P5 | `(assoc CB-IN :summary "  ")`, `(dissoc CB-IN :summary)` | `[":summary must be a non-blank string"]` both |
| P6 | `(bf/problems {})` | `[":id must be a keyword" ":at must be [x y z], three numbers" ":summary must be a non-blank string"]` |
| P7 | one validator: `(count (bp/all {:blueprints [CB-IN (dissoc CB-IN :id) (assoc CB-IN :summary "")]}))` | `1` |
| N1 | `(bf/normalize nil)` | `{:as-of 0 :blueprints []}` |
| N2 | `(bf/normalize {:as-of 7 :blueprints (list CB :junk PL) :x 1})` | `{:as-of 7 :blueprints [CB PL]}` |
| N3 | `(bf/normalize {:as-of "7" :blueprints :x})` | `{:as-of 0 :blueprints []}` |
| N4 | `(bf/normalize [1 2])` | `{:as-of 0 :blueprints []}` |
| D1 | `(bf/add nil E :un-automate-monde CB-IN 1000)` | `{:ok {:as-of 1000 :blueprints [CB]} :id :bench-by-creek}` |
| D2 | `(bf/add {:as-of 1000 :blueprints [CB]} E :le-testeur PL-IN 2000)` | `{:ok {:as-of 2000 :blueprints [CB PL]} :id :planter}` |
| D3 | `(bf/add {:as-of 5000 :blueprints [CB]} E :le-testeur PL-IN 4000)` | `(get-in r [:ok :as-of])` = `5001` |
| D4 | `(bf/add nil E :le-testeur (assoc CB-IN :by :someone-else :status :real :seated? true) 10)` | `{:ok {:as-of 10 :blueprints [(assoc CB-IN :by :le-testeur)]} :id :bench-by-creek}` |
| D5 | `(bf/add nil E :le-testeur (assoc PL-IN :id :bench-by-mailbox) 10)` | `{:refused "id :bench-by-mailbox is already in the estate"}` |
| D6 | `(bf/add {:as-of 1000 :blueprints [CB]} E :le-testeur CB-IN 2000)` | `{:refused "id :bench-by-creek is already in state/blueprints.edn"}` |
| D7 | `(bf/add nil E :le-testeur (dissoc PL-IN :summary) 10)` | `{:refused "invalid blueprint: :summary must be a non-blank string"}` |
| D8 | `(bf/add nil E :le-testeur {:look LOOK} 10)` | `{:refused "invalid blueprint: :id must be a keyword; :at must be [x y z], three numbers; :summary must be a non-blank string"}` |
| D9 | `(bf/add nil E :le-testeur [1 2] 10)` | `{:refused "invalid blueprint: not a map"}` |
| D10 | `(bf/add nil E :le-testeur (assoc PL-IN :id :Planter_1) 10)` | `{:refused "id :Planter_1 must be lowercase letters, digits and dashes"}` |
| D11 | command → game: `f = (:ok (bf/add (:ok (bf/add nil E :un-automate-monde CB-IN 1000)) E :le-testeur PL-IN 2000))`; `(bp/merge-file W0 f)` | `:added [:bench-by-creek :planter]`, `:announce []` (page load), `(ids (:world r))` = `[:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter]` |
| R1 | `(bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :planter 3000)` | `{:ok {:as-of 3000 :blueprints [CB]} :id :planter}` |
| R2 | `(bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :bench-by-mailbox 3000)` | `{:refused "id :bench-by-mailbox is in the estate; only state/blueprints.edn entries can be removed"}` |
| R3 | `(bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :gazebo 3000)` | `{:refused "no blueprint :gazebo in state/blueprints.edn"}` |
| R4 | `(bf/remove-id {:as-of 5000 :blueprints [CB]} E :bench-by-creek 10)` | `{:ok {:as-of 5001 :blueprints []} :id :bench-by-creek}` |
| R5 | `(bf/remove-id nil E :planter 10)` | `{:refused "no blueprint :planter in state/blueprints.edn"}` |
| U1 | `(bf/parse-args ["un-automate-monde" "/tmp/x.edn"])`, `(bf/parse-args ["le-testeur" "bench.edn"])` | `{:mode :add :member :un-automate-monde :path "/tmp/x.edn"}`, `{:mode :add :member :le-testeur :path "bench.edn"}` |
| U2 | `(bf/parse-args ["--remove" "planter"])` | `{:mode :remove :id :planter}` |
| U3 | `(bf/parse-args ["--remove" ":planter"])` | `{:mode :remove :id :planter}` |
| U4 | each of `nil` `[]` `["le-testeur"]` `["Le Testeur" "f.edn"]` `["le-testeur" ""]` `["--remove"]` `["--remove" "Bad!"]` `["--remove" "x" "y"]` `["a" "b" "c"]` | `{:usage bf/usage}` each |

### 8.2 `test/pm/blueprint_live_test.cljs` (`pm.blueprint-live-test`, requires `pm.blueprint :as bp`, `pm.talk :as talk`)

**merge-file**

| # | Call | Expected |
|---|---|---|
| M1 | `(bp/merge-file W0 {:as-of 1000 :blueprints [CB PL]})` (page load) | `:added [:bench-by-creek :planter]`, `:removed []`, `:announce []`; `(:blueprints w)` = `[E-BENCH E-LAMP (fbp CB) (fbp PL)]`; `:blueprints-as-of 1000`; `:blueprints-announced #{:bench-by-creek :planter}` |
| M2 | new id added pending, announced: `(bp/merge-file W1 {:as-of 2000 :blueprints [CB PL]})` | `:added [:planter]`, `:announce [:planter]`, `(:world r)` = `W2`, `(bp/status W2 :planter)` = `:pending` |
| M3 | unchanged `:as-of` is a no-op: `(bp/merge-file W1 {:as-of 1000 :blueprints [CB PL]})` | `(identical? W1 (:world r))` true; `:added [] :removed [] :announce []` |
| M4 | existing keeps status (and everything else): `(bp/merge-file (bp/approve-blueprint W1 :bench-by-creek) {:as-of 2000 :blueprints [(assoc CB :at [9 9 9] :summary "changed")]})` | `(nth (:blueprints w) 2)` = `(assoc (fbp CB) :status :approved)`; `:added []` |
| M5 | estate id wins: `(bp/merge-file W0 {:as-of 3000 :blueprints [(assoc PL :id :bench-by-mailbox) CB]})` | `(ids w)` = `[:bench-by-mailbox :lamp-post-by-canal :bench-by-creek]`; `(first (:blueprints w))` = `E-BENCH`; `:added [:bench-by-creek]` |
| M6 | removed id dropped: `(bp/merge-file W2 {:as-of 3000 :blueprints [CB]})` | `:removed [:planter]`; `(ids w)` = `[:bench-by-mailbox :lamp-post-by-canal :bench-by-creek]` |
| M7 | removed-but-rejected also dropped: `(bp/merge-file (bp/reject-blueprint W2 :planter) {:as-of 3000 :blueprints [CB]})` | `:removed [:planter]` |
| M8 | removed-but-real and removed-but-trial kept: `W3 = (-> W2 (bp/approve-blueprint :planter) (bp/apply-real-builds {:real [:bench-by-creek]}))`; `(bp/merge-file W3 {:as-of 3000 :blueprints []})` | `:removed []`; `(mapv :status (bp/all w))` = `[:pending :pending :real :approved]`; `:blueprints-as-of 3000` |
| M9 | once per id: `W4 = (:world (bp/merge-file W2 {:as-of 3000 :blueprints [CB]}))`; `(bp/merge-file W4 {:as-of 4000 :blueprints [CB PL]})` | `:added [:planter]`, `:announce []` |
| M10 | invalid/duplicate file entries: `(bp/merge-file W1 {:as-of 2000 :blueprints [{:id "x" :at [0 0 0] :summary "s"} :junk PL (assoc PL :summary "second")]})` | `:added [:planter]`, `:announce [:planter]`; `(last (:blueprints w))` = `(fbp PL)` |
| M11 | file keys stripped: `(bp/merge-file W1 {:as-of 2000 :blueprints [(assoc PL :status :real :seated? true :too-steep? true :failed? true :seat-drop 9)]})` | `(last (:blueprints w))` = `(fbp PL)` |
| M12 | missing file at load, then created live: `Wn = (:world (bp/merge-file W0 nil))`; then `(bp/merge-file Wn {:as-of 1000 :blueprints [CB]})` | `Wn` = `(assoc W0 :blueprints-as-of 0 :blueprints-announced #{})`; second: `:added [:bench-by-creek]`, `:announce [:bench-by-creek]` |
| M13 | world without `:blueprints`: `(bp/merge-file {} {:as-of 5 :blueprints [PL]})` | `:world` = `{:blueprints [(fbp PL)] :blueprints-as-of 5 :blueprints-announced #{:planter}}`; `:announce []` |
| M14 | real builds apply to file blueprints (reload persistence): `(-> W0 (bp/merge-file {:as-of 2000 :blueprints [CB PL]}) :world (bp/apply-real-builds {:real [:planter]}))` | `(mapv :status (bp/all w))` = `[:pending :pending :pending :real]` |

**seat-ids** (`G`: ground 1.5 where z < 0; LOOK has no feet, so it sits on the ground at its centre, drop 0)

| # | Call | Expected |
|---|---|---|
| S1 | `(bp/seat-ids W2 [:bench-by-creek] G)` | `(nth bps 2)` = `(assoc (fbp CB) :at [1.0 1.5 -9.0] :seated? true :seat-drop 0 :too-steep? false)`; `(nth bps 3)` = `(fbp PL)`; first two = `E-BENCH E-LAMP` |
| S2 | second blueprint: `(bp/seat-ids W2 #{:planter} G)` | `(nth bps 3)` = `(assoc (fbp PL) :at [-2.0 1.5 -4.0] :seated? true :seat-drop 0 :too-steep? false)`; `(nth bps 2)` = `(fbp CB)` |
| S3 | `(bp/seat-ids W2 [] G)`, `(bp/seat-ids W2 [:gazebo] G)` | `=` W2 both |

**author-line**

| # | Call | Expected |
|---|---|---|
| A1 | `(bp/author-line W1 :bench-by-creek)` | `{:who "Un Automate" :text "drew a blueprint: proposed: a bench by the creek. Click it to try it." :kind :blueprint :event :blueprint-drawn}` |
| A2 | `(bp/author-line W2 :planter)` | `{:who "Le Testeur" :text "drew a blueprint: proposed: a planter by the path. Click it to try it." :kind :blueprint :event :blueprint-drawn}` |
| A3 | no walker, crew title: `w = (:world (bp/merge-file (assoc W0 :crew {:titles {:le-docteur "Le Docteur"}}) {:as-of 1 :blueprints [(assoc PL :by :le-docteur)]}))`; `(:who (bp/author-line w :planter))` | `"Le Docteur"` |
| A4 | neither: same with `:by :night-owl` and no `:crew` | `:who "night-owl"` |
| A5 | trailing period: `w = (:world (bp/merge-file W0 {:as-of 1 :blueprints [(assoc PL :summary "proposed: a gate.")]}))`; `(:text (bp/author-line w :planter))` | `"drew a blueprint: proposed: a gate. Click it to try it."` |
| A6 | `(bp/author-line W1 :bench-by-mailbox)` (estate, no `:by`), `(bp/author-line W1 :gazebo)` | `nil`, `nil` |
| A7 | into the talk log: `t = (talk/append nil (bp/author-line W1 :bench-by-creek) 5000)`; `(select-keys (first (:entries t)) [:n :who :text :kind :event :at])` | `{:n 1 :who "Un Automate" :text "drew a blueprint: proposed: a bench by the creek. Click it to try it." :kind :blueprint :event :blueprint-drawn :at 5000}` |

**drawable-changes**

| # | Call | Expected |
|---|---|---|
| C1 | `(bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal} W1)` | `{:add [:bench-by-creek] :remove #{}}` |
| C2 | `(bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter} W1)` | `{:add [] :remove #{:planter}}` |
| C3 | `(bp/drawable-changes nil W0)` | `{:add [:bench-by-mailbox :lamp-post-by-canal] :remove #{}}` |
| C4 | rejected isn't drawn: `(bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek} (bp/reject-blueprint W2 :planter))` | `{:add [] :remove #{}}` |
| C5 | a real file blueprint is: `(bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek} (bp/apply-real-builds W2 {:real [:planter]}))` | `{:add [:planter] :remove #{}}` |

### 8.3 `test/pm/bridge_real_test.cljs` — append `t04-known-build-ids` (define CB/PL/E-BENCH/E-LAMP locally)

| # | Call | Expected |
|---|---|---|
| B1 | `(b/known-build-ids {:blueprints [E-BENCH E-LAMP]} {:as-of 1 :blueprints [CB PL {:id "bad" :at [0 0 0] :summary "s"}]})` | `#{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter}` |
| B2 | `(b/known-build-ids {:blueprints [E-BENCH E-LAMP]} nil)`, `(b/known-build-ids nil nil)` | `#{:bench-by-mailbox :lamp-post-by-canal}`, `#{}` |
| B3 | `(b/add-real-build {:real [:lamp-post-by-canal]} :planter (b/known-build-ids {:blueprints [E-BENCH E-LAMP]} {:as-of 1 :blueprints [PL]}) 9)`; same with file `nil` | `{:as-of 9 :real [:lamp-post-by-canal :planter]}`; `nil` |
| B4 | file ids pass request validation: `(b/validate (req {"action" "build" "id" "bench-by-creek"}) ctx)` | `{:ok true :action :real-build :id :bench-by-creek}` |

Total: 63 cases (P7 N4 D11 R5 U4 · M14 S3 A7 C5 · B4). Existing tests stay green unchanged.

---

## 9. Wording for le-docteur (after the gate passes)

- **`TASKS.md` T-13**, append to the line (don't mark it done; the demo-script slot is still open):
  > Nov 23: (b) done: a crew member proposes with `bb blueprint-add <member> <blueprint.edn>` (kept in the private `state/blueprints.edn`; `--remove <id>` takes one out), and within one 5-second poll its ghost appears in the estate, seated, with no reload, and the member says "drew a blueprint: … Click it to try it." in 💭. Two clicks still make it a trial, then real.
- **`docs/GDD.md` §2.1, Approve row**, append in the row's existing *Built* style:
  > *Built (Nov 23): a crew member's blueprint arrives while you watch: `bb blueprint-add` writes it to the private `state/blueprints.edn`, the estate's ghost appears within seconds without a reload, and whoever drew it says so in 💭. The public sample estate never changes when an agent proposes something.*

---

## 10. Builder's notes (`crew/runs/live-blueprints-v0-20261123-1331/builder-notes.md`)

What was built; what was checked by hand (with `PM_BLUEPRINTS_FILE` pointing at a temp file, never
`state/`): add, duplicate, invalid, `--remove`, exit codes 0/1/2; the compiled page with a
blueprint appearing without reload if the builder can watch it. And for Hal, with the example
`crew/runs/live-blueprints-v0-20261123-1331/bench-by-creek.edn` (a bench like the estate's, `:id
:bench-by-creek`, on level grass the builder picks; the game seats it and its sign says if it's
too steep):

```bash
bb blueprint-add un-automate-monde crew/runs/live-blueprints-v0-20261123-1331/bench-by-creek.edn   # → added bench-by-creek
bb blueprint-add --remove bench-by-creek                                                           # → removed bench-by-creek
```
Plus the two known limits from §6.
