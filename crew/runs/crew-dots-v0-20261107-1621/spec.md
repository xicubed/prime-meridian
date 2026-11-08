# Spec: crew-dots v0: a Herdr status dot before each walker's name

Quest: `.crew/quests/crew-dots-v0.md` (TASKS W3-0, first slice). It builds on the working tree
after `senses-v0` (walkers already carry `:senses :speed :facing :aware :glance`).

## 1. Shape of the change

```
bb crew-status ──writes every 2 s──▶ public/state/crew.edn
                                          │  fetch every 2 s (pm.core, same pattern as mirrors.edn)
                                          ▼
                          pm.crew/parse ── nil (garbage) → keep the last good one
                                          │  valid
                                          ▼
                              !world :crew {:as-of .. :agents {..}}
                         ┌────────────────┴─────────────────┐
         each frame: bricks/set-dots! crew now     every 30 frames: HUD "crew: 2/2 live"
         pm.crew/dot + label-runs per walker        pm.crew/hud-text
         redraw a label only when its runs change
```

- New pure namespace **`pm.crew`**: parsing, freshness, dot per agent, label runs and text, HUD
  text, pulse. All of it is tested.
- `pm.motion/walker` gains `:agent`.
- `pm.three` gains `set-label-runs!` (a label drawn as coloured pieces).
- `pm.bricks` redraws walker labels.
- `pm.core` polls and adds the HUD segment.

Nothing names a walker or an agent in code. From the file, only agent ids and statuses survive
parsing; extra keys are dropped.

## 2. Data

### 2.1 `resources/estate.edn` (builder; then `touch src/pm/world.cljs`)

Under `:motion`, after `:senses`:

```clojure
:status-dots {:working 0xf2b134   ; Herdr's colours for the crew's states (dot before the name)
              :blocked 0xe5484d   ; also pulses slowly: waiting for a human
              :done    0x46a758
              :idle    0x9aa0a6
              :unknown 0x9aa0a6}
```

On each `:walkers` entry, add `:agent` (the same value as its `:id` today):

```clojure
{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde ...}
{:id :le-testeur        :name "Le Testeur"  :agent :le-testeur ...}
```

### 2.2 The status file (written by `scripts/crew_status.clj`; not edited this round)

```clojure
{:as-of 1794097290232
 :agents {:le-testeur {:status :done} :un-architecte {:status :working} :un-automate-monde {:status :done}}}
```

### 2.3 Parsed crew (stored in `!world` under `:crew`)

```clojure
{:as-of 1794097290232 :agents {:le-testeur {:status :done} :un-automate-monde {:status :working}}}
```

Statuses are always one of `#{:idle :working :blocked :done :unknown}`.

### 2.4 A dot

```clojure
{:glyph "●" :color 0xf2b134 :pulse? false}   ; filled: the agent's status colour
{:glyph "○" :color 0x9aa0a6 :pulse? false}   ; hollow: no file, stale file, or agent absent
```

### 2.5 Label runs (what `set-label-runs!` draws, left to right, centred as a whole)

```clojure
[{:text "● " :color 0xf2b134 :alpha 1} {:text "Un Automate" :color nil :alpha 1}]   ; nil colour = the label's ink
```

## 3. `pm.crew` (new, pure)

The namespace requires only `cljs.reader` and `clojure.string`. No `js/Date`, no fetch, no atoms;
`now` is always an argument, in ms.

### Constants

```clojure
(def statuses #{:idle :working :blocked :done :unknown})
(def stale-ms 15000)
(def pulse-ms 2000)
(def default-colors {:working 0xf2b134 :blocked 0xe5484d :done 0x46a758 :idle 0x9aa0a6 :unknown 0x9aa0a6})
```

### `(parse text)` → crew map or nil

1. When `text` isn't a string, return nil. Otherwise read it with `cljs.reader/read-string` in a
   `try`; a throw gives nil.
2. The value must be a map, `:as-of` must satisfy `(and (integer? a) (not (neg? a)))`, and
   `:agents` must be a map. Otherwise return nil.
3. Each agents entry `[k v]`:
   - drop it when `k` isn't a keyword or `v` isn't a map;
   - otherwise keep it as `{:status s}`, where `s = (:status v)` when it is in `statuses`, else
     `:unknown`. Other keys in `v` are dropped.
4. Return `{:as-of a :agents <cleaned map>}`. `:agents {}` is valid (Herdr isn't running), and
   top-level keys other than `:as-of` and `:agents` are dropped.

### `(fresh? crew now)` → boolean

`(and (map? crew) (<= (- now (:as-of crew)) stale-ms))`. A future `:as-of` (negative age) counts
as fresh. An age of exactly 15000 is fresh; 15001 is stale.

### `(colors world)` → colour map

`default-colors`, with each of the five keys replaced by
`(get-in world [:motion :status-dots k])` when that value is an integer between 0 and 0xffffff.
Other keys are ignored.

### `(dot crew agent now palette)` → dot

- When `(fresh? crew now)` is false, or `agent` isn't in `(:agents crew)`, the dot is hollow:
  `{:glyph "○" :color (:idle palette) :pulse? false}`.
- Otherwise, with `s` the agent's status: `{:glyph "●" :color (or (get palette s) (:unknown palette)) :pulse? (= s :blocked)}`.

### `(pulse-alpha t)` → number, rounded to 0.1

`a = 0.4 + 0.6 × (0.5 + 0.5·cos(2π·t / pulse-ms))`, then `(/ (js/Math.round (* a 10)) 10)`.
It is 1 at t = 0, 0.4 at t = 1000 and periodic. The rounding means a blocked label is redrawn
only when the value steps (about 12 times per period).

### `(label-runs d nm now)` → runs

`[{:text (str (:glyph d) " ") :color (:color d) :alpha (if (:pulse? d) (pulse-alpha now) 1)} {:text nm :color nil :alpha 1}]`

### `(label-text d nm)` → string

`(str (:glyph d) " " nm)`, e.g. `"● Un Automate"`. Used for tests and the console; the sprite
draws the runs.

### `(live-count crew agents now)` → integer

The number of entries of `agents` (a seq of agent keywords, one per walker, duplicates counted)
present in `(:agents crew)`. It is 0 when not fresh.

### `(hud-text crew agents now)` → string or nil

- nil when `agents` is empty.
- `"crew: no status"` when `crew` is nil.
- `"crew: stale"` when it is not fresh.
- Otherwise `(str "crew: " (live-count crew agents now) "/" (count agents) " live")`.

## 4. Changes elsewhere

### `pm.motion/walker`

Add `:agent`: `(:agent entry)` when it is a keyword, else the walker's `:id`. Every other field is
unchanged. `walkers` passes it through untouched.

### `pm.three/set-label-runs!` `[sprite runs]`

- Same canvas, paper and rounded rectangle as `set-label-text!`; it never makes a new texture.
- Font fit: the same loop (bold, starting at 56 px, shrinking while the total measured width of
  all runs is over 472 and the size is over 12).
- Draw each run left to right from x = `(w - total) / 2`, middle baseline, `textAlign "left"`.
  The fill is the run's `:color` (via `hex->css`), or the label's ink when nil, with
  `ctx.globalAlpha` set to the run's `:alpha`. Reset `globalAlpha` to 1 afterwards.
- `needsUpdate` true, visible true. Empty or nil runs hide the sprite, as an empty text does.
- Returns the sprite. `set-label-text!` is unchanged.

### `pm.bricks`

- In `start!`, store `:colors (crew/colors world)` in `!state`.
- New public **`(set-dots! crew now)`**: no-op before `start!`. For each walker and view:
  - `runs = (crew/label-runs (crew/dot crew (:agent w) now colors) (:name w) now)`;
  - when `(not= runs (:runs view))`, call `(t/set-label-runs! (:label view) runs)` and store
    `:runs` on the view.

  The first call draws every label, so `build-view!` can keep creating the label with the plain
  name. Redraws happen only on change: a status change, or a pulse step while blocked. The
  views' other fields are untouched, and fallback keeps `:runs`.
- Requires `pm.crew`.

### `pm.core`

- `(def crew-url "/state/crew.edn")`, `(def crew-poll-ms 2000)`, and
  `(defonce ^:private !crew-poll (atom {:timer nil :busy? false}))`.
- `fetch-crew!` follows `fetch-state!` exactly: busy guard, `?t=` cache buster,
  `{:cache "no-store"}`, `.ok` check, `.catch` swallow, `.finally` clearing busy. On text,
  `(when-let [c (crew/parse text)] (when (not= c (:crew @!world)) (swap! !world assoc :crew c)))`.
  A missing file, a 404 or garbage leaves the last good crew in place, and it goes stale on its
  own.
- `start-crew-poll!` is like `start-state-poll!` (fetch now, then `setInterval` 2 s), called from
  `init!` after `start-state-poll!`.
- In `frame`, after `bricks/tick!`: `(bricks/set-dots! (:crew @!world) (js/Date.now))`. It runs in
  both kit and scaffold mode; it is a no-op without walkers.
- In `hud-mirrors-suffix`, after the bricks segment:
  `(when-let [s (crew/hud-text (:crew w) (mapv :agent (m/walkers w)) now)] (str " · " s))`.
  The text lands in the ⓘ panel's `#hud`; `public/index.html` is unchanged.

Nothing else from the file is shown or logged beyond these dots and counts.

## 5. Who touches what

| Crew member | Files |
|---|---|
| **builder** `un-automate-monde` | new `src/pm/crew.cljs`; `src/pm/motion.cljs` (`:agent` only); `src/pm/three.cljs` (`set-label-runs!` only); `src/pm/bricks.cljs`; `src/pm/core.cljs`; `resources/estate.edn` (`:status-dots`, `:agent` ×2); `touch src/pm/world.cljs` |
| **tester** `le-testeur` | new `test/pm/crew_test.cljs` (ns `pm.crew-test`). No existing test edited. |
| nobody | `public/index.html`, `public/vendor/`, `resources/content/`, `scripts/crew_status.clj`, `public/state/`, `assets/private/`, `public/kit/` |

Existing tests must pass unchanged. No test compares whole walker maps.

## 6. Fixtures (tester)

```clojure
(ns pm.crew-test
  (:require [cljs.test :refer [deftest is]]
            [pm.crew :as c] [pm.motion :as m] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def T0 1794097290232)
(def C {:as-of T0 :agents {:un-automate-monde {:status :working} :le-testeur {:status :blocked}
                           :un-architecte {:status :done}}})
(def P c/default-colors)
(def agents [:un-automate-monde :le-testeur])
```

## 7. Test cases

All expected values were worked out by hand: colour integers (0xf2b134 = 15905076,
0xe5484d = 15026253, 0x46a758 = 4630360, 0x9aa0a6 = 10133670) and pulse values (§8).

### Parsing

1. **A real file.** `(c/parse "{:as-of 1794097290232, :agents {:le-testeur {:status :done}, :un-automate-monde {:status :working}}}")`
   → `{:as-of 1794097290232 :agents {:le-testeur {:status :done} :un-automate-monde {:status :working}}}`.
2. **Herdr not running.** `(c/parse "{:as-of 5 :agents {}}")` → `{:as-of 5 :agents {}}`.
3. **Not EDN, or not a string.** `(c/parse "{:as-of")` → nil; `(c/parse "")` → nil; `(c/parse nil)`
   → nil; `(c/parse 42)` → nil; `(c/parse "#foo/bar 1")` → nil.
4. **Wrong top level.** These all give nil:
   - `"[1 2]"`
   - `"{:agents {}}"` (no `:as-of`)
   - `"{:as-of 1.5 :agents {}}"`
   - `"{:as-of -1 :agents {}}"`
   - `"{:as-of \"5\" :agents {}}"`
   - `"{:as-of 5 :agents [1]}"`
   - `"{:as-of 5}"`
5. **Unknown statuses become `:unknown`.**
   `(c/parse "{:as-of 5 :agents {:a {:status :sleeping} :b {:status \"working\"} :c {} :d {:status :blocked}}}")`
   → `:agents {:a {:status :unknown} :b {:status :unknown} :c {:status :unknown} :d {:status :blocked}}`.
6. **Garbage entries dropped, extra keys stripped.**
   `(c/parse "{:as-of 5 :host \"x\" :agents {\"s\" {:status :idle} :n 3 :e {:status :done :title \"secret\" :screen \"...\"}}}")`
   → exactly `{:as-of 5 :agents {:e {:status :done}}}` (no `:host`, no `:title`, no `:screen`).
7. **All five statuses kept.** `(c/parse "{:as-of 0 :agents {:a {:status :idle} :b {:status :working} :c {:status :blocked} :d {:status :done} :e {:status :unknown}}}")`
   keeps all five statuses as given. `:as-of 0` is valid.

### Freshness

8. **Edges.**
   - `(c/fresh? C (+ T0 15000))` → true.
   - `(c/fresh? C (+ T0 15001))` → false.
   - `(c/fresh? C T0)` → true.
   - `(c/fresh? C (- T0 60000))` → true (future `:as-of`).
   - `(c/fresh? nil T0)` → false.
9. **Constants.** `c/stale-ms` = 15000, `c/pulse-ms` = 2000, and `c/statuses` = `#{:idle :working :blocked :done :unknown}`.

### Colours

10. **From the estate.**
    - `(c/colors W)` → `{:working 15905076 :blocked 15026253 :done 4630360 :idle 10133670 :unknown 10133670}`.
    - `(c/colors {})` → the same (= `c/default-colors`).
11. **Validation.** `(c/colors {:motion {:status-dots {:working 255 :done -1 :idle "red" :blocked 16777216 :extra 1}}})`
    → `{:working 255 :blocked 15026253 :done 4630360 :idle 10133670 :unknown 10133670}`.

### Dots

12. **One per status.** With `C` at `now = T0`, the dot for each agent:

    | Agent | Dot |
    |---|---|
    | `:un-automate-monde` | `{:glyph "●" :color 15905076 :pulse? false}` |
    | `:le-testeur` | `{:glyph "●" :color 15026253 :pulse? true}` |
    | `:un-architecte` | `{:glyph "●" :color 4630360 :pulse? false}` |

13. **Idle and unknown.** `(c/dot {:as-of T0 :agents {:a {:status :idle} :b {:status :unknown}}} :a T0 P)`
    → `{:glyph "●" :color 10133670 :pulse? false}`; `:b` gives the same.
14. **Missing agent → hollow.** `(c/dot C :le-coureur T0 P)` → `{:glyph "○" :color 10133670 :pulse? false}`.
15. **Stale → every dot hollow.** At `(+ T0 15001)`:
    - `(c/dot C :un-automate-monde (+ T0 15001) P)` → `{:glyph "○" :color 10133670 :pulse? false}`;
    - `:le-testeur` gives the same (no pulse when stale).
    - At `(+ T0 15000)` the first agent's dot is still filled.
16. **No file → hollow.** `(c/dot nil :un-automate-monde T0 P)` → `{:glyph "○" :color 10133670 :pulse? false}`.
17. **The palette comes from data.** With `P2 = (assoc P :working 255 :idle 1)`:
    - `(c/dot C :un-automate-monde T0 P2)` → colour 255;
    - `(c/dot C :nobody T0 P2)` → `{:glyph "○" :color 1 :pulse? false}`;
    - with `(dissoc P :done)`, the dot for `:un-architecte` takes `:unknown`'s 10133670.

### Pulse and labels

18. **`pulse-alpha`.** The value at each t (ms):

    | t | 0 | 250 | 500 | 1000 | 1500 | 2000 | 2250 | 3000 |
    |---|---|---|---|---|---|---|---|---|
    | `pulse-alpha` | 1 | 0.9 | 0.7 | 0.4 | 0.7 | 1 | 0.9 | 0.4 |

19. **Label text.**
    - `(c/label-text (c/dot C :un-automate-monde T0 P) "Un Automate")` → `"● Un Automate"`.
    - `(c/label-text (c/dot C :le-coureur T0 P) "Le Testeur")` → `"○ Le Testeur"`.
    - `(c/label-text (c/dot nil :le-testeur T0 P) "Le Testeur")` → `"○ Le Testeur"`.
20. **Label runs, working.** `(c/label-runs (c/dot C :un-automate-monde T0 P) "Un Automate" 1000)`
    → `[{:text "● " :color 15905076 :alpha 1} {:text "Un Automate" :color nil :alpha 1}]`.
    The alpha is 1 even at t = 1000, because the dot doesn't pulse.
21. **Label runs, blocked, pulsing.**
    - `(c/label-runs (c/dot C :le-testeur T0 P) "Le Testeur" 1000)` → `[{:text "● " :color 15026253 :alpha 0.4} {:text "Le Testeur" :color nil :alpha 1}]`.
    - At now 500 the first alpha is 0.7.
    - At now 0 and 2000 the runs are equal (=), so there is no redraw.
22. **Label runs, hollow.** `(c/label-runs (c/dot C :x T0 P) "Le Testeur" 0)` →
    `[{:text "○ " :color 10133670 :alpha 1} {:text "Le Testeur" :color nil :alpha 1}]`.

### HUD and walkers

23. **HUD text.**
    - `(c/hud-text C agents T0)` → `"crew: 2/2 live"`.
    - `(c/hud-text (update C :agents dissoc :le-testeur) agents T0)` → `"crew: 1/2 live"`.
    - `(c/hud-text {:as-of T0 :agents {}} agents T0)` → `"crew: 0/2 live"`.
24. **HUD when stale, missing or with no walkers.**
    - `(c/hud-text C agents (+ T0 15001))` → `"crew: stale"`.
    - `(c/hud-text nil agents T0)` → `"crew: no status"`.
    - `(c/hud-text C [] T0)` → nil.
25. **`live-count`.**
    - `(c/live-count C agents T0)` → 2.
    - `(c/live-count C [:un-automate-monde :un-automate-monde :x] T0)` → 2.
    - `(c/live-count C agents (+ T0 20000))` → 0.
26. **Walker `:agent`.**
    - `(mapv :agent (m/walkers W))` → `[:un-automate-monde :le-testeur]`.
    - `(:agent (m/walker {:id :a :route [[0 0]]}))` → `:a`.
    - `(:agent (m/walker {:id :a :route [[0 0]] :agent :b}))` → `:b`.
    - `(:agent (m/walker {:id :a :route [[0 0]] :agent "b"}))` → `:a`.
27. **Both estate walkers end to end** (the second one included). Parse `S = (c/parse "{:as-of 1000 :agents {:un-automate-monde {:status :working} :le-testeur {:status :idle}}}")`.
    For each walker `wk` of `(m/walkers W)`, at `now = 3000` with `(c/colors W)`, `(c/label-text (c/dot S (:agent wk) 3000 (c/colors W)) (:name wk))` gives:
    - `["● Un Automate" "● Le Testeur"]`;
    - at now 16001: `["○ Un Automate" "○ Le Testeur"]`;
    - the `:color`s at 3000 are `[15905076 10133670]`.
28. **Keep the last good file.** This is the pure part of the poll rule, simulated in the test:

    ```clojure
    (defn keep-good [prev text] (or (c/parse text) prev))
    ```

    - From `prev = C`, `(keep-good C "garbage{")` → `C`, and `(keep-good C nil)` → `C`.
    - `(keep-good C "{:as-of 9 :agents {}}")` → `{:as-of 9 :agents {}}`.
    - After a garbage read, the kept `C` turns stale on time:
      `(c/dot (keep-good C "x") :un-automate-monde (+ T0 15001) P)` is hollow.

## 8. Hand arithmetic

- `pulse-alpha(t) = 0.4 + 0.6 × (0.5 + 0.5·cos(2πt/2000))`:

  | t | cos | a | rounded |
  |---|---|---|---|
  | 0 | 1 | 1.0 | 1 |
  | 250 | cos(π/4) = 0.70711 | 0.4 + 0.6 × 0.85355 = 0.91213 | 0.9 |
  | 500 | cos(π/2) = 0 | 0.4 + 0.3 = 0.7 | 0.7 |
  | 1000 | cos(π) = −1 | 0.4 | 0.4 |
  | 1500 | 0 | 0.7 | 0.7 |
  | 2000 | 1 | 1.0 | 1 (period) |

  At 2250 and 3000 the values repeat those at 250 and 1000.
- Staleness: the watcher rewrites at least every 10 s while running, and the poll adds up to 2 s,
  so a fresh file is at most ~12 s old (< 15 s). Within ~3 s of a state change, the watcher's
  next 2 s tick plus the game's next 2 s poll can add up to ~4 s at worst; Hal checks "~3 s".
- 0xffffff = 16777215, so 16777216 is rejected; 255 and 0 are accepted.

## 9. Done when

- `bash .crew/gate.sh` passes: 0 warnings, and all tests pass, including the 28 cases in
  `pm.crew-test`.
- Live, Hal checks with `bb crew-status` running:
  - `● Un Automate` and `● Le Testeur` over their heads, in colours that match
    `herdr agent list` within ~3 s, and a blocked one pulsing;
  - the ⓘ panel reads `crew: 2/2 live`;
  - after the watcher stops, within 15 s both dots turn `○` grey and the panel reads
    `crew: stale`;
  - the frame rate stays at 60 fps.
