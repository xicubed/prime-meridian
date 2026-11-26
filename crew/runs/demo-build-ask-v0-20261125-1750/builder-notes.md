# builder-notes: ask the demo's twin to build something (F-2)

How Hal checks it headless, in the demo build:

1. `bb demo-build`, then serve `dist/demo` at a site root, e.g.:
   ```
   python3 -m http.server -d dist/demo 8090
   ```
   Open `http://localhost:8090/?fresh`, jack in, and play the Construct to
   the end.
2. Aim the camera at open grass, open 💭, open the twin's thread, and type
   `put a bench here`. Then try `a lamp post by the mailbox`, `hello`
   (the help line), and the same request while looking at the sky (aim the
   camera up).
3. What to read back from devtools:
   - the world's blueprints:
     ```js
     cljs.core.pr_str(cljs.core.mapv(x => cljs.core.select_keys(x, cljs.core.vector(cljs.core.keyword("id"), cljs.core.keyword("status"), cljs.core.keyword("at"))), cljs.core.get(cljs.core.deref(pm.core._BANG_world), cljs.core.keyword("blueprints"))))
     ```
     (the new `:bench-1`, `:pending`, its `:at`)
   - the twin's line: the last 💭 entry, or the caption
   - `localStorage.getItem("pm.demo-builds.v1")`, which gains `:bench-1`;
     after the trial card's **Make it real**, `:real [:bench-1]`; after
     **Remove it**, `:removed [...]`
   - the console line `Prime Meridian: ground grid 29 x 29 in N ms`, with N
     recorded (the aim is under ~50 ms; if it's over, record it and how big
     the grid was)
4. Reload the page: the bench is still there with its status kept
   (pending/real/removed). `?fresh` clears `pm.demo-builds.v1` along with
   the rest.
5. Dev build check: `npx shadow-cljs watch app` (no `bb crew-bridge`
   running) still answers with the no-bridge line ("Out here I only hear
   you through the hardline…") — `DEMO` is false there, so `demo-ask!`
   never runs.

## Gate

`.crew/gate.sh`'s `[:app]` and `[:test]` builds both compile with **0
warnings** (`pm.ask-build`, the `ground-grid` addition to `pm.three`, and
the `pm.core` wiring are clean). The gate's **test run itself crashes**,
for a reason outside this spec's builder files:

`test/pm/ask_build_test.cljs` defines `(def ≈ (fn [a b] ...))` (its fixture
for the look-radius comparisons, §7's suggested fixture). `≈` (U+2248) is
not a valid JavaScript identifier character (confirmed directly: `node -e
"var ≈ = 1"` throws `SyntaxError: Invalid or unexpected token`), so the
compiled test bundle fails to parse and the whole `node out/test.js` run
aborts before any test (not just this file's) can execute. That file is
the tester's, not mine to edit.

I could not verify the ask-build logic through the gate for that reason,
so I ran the spec's worked test values by hand against `pm.ask-build`
directly with `bb` (requiring `src` on the classpath, reading
`resources/estate.edn` with `clojure.edn`, skipping only the few cases
that need the `.cljs`-only `pm.blueprint`/`pm.core` merge path). Every one
of the 45 cases' values matched, modulo two JVM-vs-JS numeric-representation
artifacts that don't apply in ClojureScript (Clojure's `=` treats `4.0` and
`4` -- and `91/50` vs `1.82` -- as different; in cljs there is only one
number type, so these are identical there).
