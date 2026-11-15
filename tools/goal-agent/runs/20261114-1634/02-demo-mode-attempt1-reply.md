Both probes match. The feature is implemented, tested, and the build gate passes clean.

FILES: src/pm/overlay.cljs, src/pm/core.cljs, test/pm/overlay_test.cljs

WHAT: Added `pm.overlay/sample-badge` (a visible "SAMPLE" tag for any connection source that isn't the twin's own real file) and `pm.overlay/demo-connections`/`demo-line`, which enumerate every GDD §4.6 connection (mail, phone, chores, payments, agents, plus each creature mirror) and badge it SAMPLE unless a real one is swapped in — agents drop their badge once the bridge is live. Wired `demo-line` into `pm.core`'s HUD suffix, so with zero accounts every connection shows its pretend status on screen.

HOW TO SEE IT: Run the game, click the ⓘ status icon to open the HUD panel — you'll see a trailing `demo: agents SAMPLE · chores SAMPLE · flora SAMPLE · juniper SAMPLE · mail SAMPLE · payments SAMPLE · phone SAMPLE` segment. Connect the twin's bridge and the `agents` entry drops out of the list.