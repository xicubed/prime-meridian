# Quest: mirrors v1 (looks you can read from the default cameras)

**Game:** Prime Meridian (`docs/GDD.md` §2.5 mirrors; follows `mirrors-v0`).
**Asked by:** Michael. **Led by:** Hal.

## The moment we're building

From "The creek" camera (about 14 m away) Flora is a pink pill that reads as a
grin: "a Cheshire axolotl". After this round you can tell at a glance that she's
an axolotl: a body, a wide head, three feathery gill stalks on each side, four
stubby legs, a flat tail, two dark eyes, facing upstream with her back at the
water surface. Juniper becomes a recognisable horse shape the same way.

**Display scale (a standing rule, write it into `resources/estate.edn` as a
comment):** creatures and crew are drawn big enough to see from the kit's
camera views without zooming in, about the size of a mid-size rock (~1 m).
Real size doesn't matter; proportions do.

## What exists (read these first)

- `resources/estate.edn`: mirrors with `:placed` items: `:at` (the item's
  **base**, world metres, y up), `:look {:shape :size :color}`, `:label`.
- `src/pm/plan.cljs` (pure, tested in `test/pm/plan_test.cljs`): `scene-plan`
  turns the world into items with `:look`, `:center`, `:label-at`, `:text`.
- `src/pm/scene.cljs`: mounts the plan once through `pm.three` and updates
  only label text per frame; `palette` holds the only hex colours.
- `src/pm/three.cljs`: the wrapper (`stand-in`, `group`, `label`, ...), which
  builds with the kit's THREE when the kit is present (`use-three!`).
- Gate: `.crew/gate.sh`. Don't read `assets/private/` or `public/kit/`.

## Scope

1. **Compound looks as data.** A look may be `{:parts [part ...]}`; a part is
   `{:shape :size [sx sy sz] :at [dx dy dz] :rot [rx ry rz] :color}`, where
   `:at` is the part's centre relative to the item's base in the item's own
   frame (+x = forward, y = up), `:rot` is degrees (optional, default 0s).
   Shapes: `:box :cylinder :capsule :sphere :cone`. The old single-shape look
   stays valid and means one part centred at `[0 h/2 0]`.
2. **Per-item `:scale`** (number, default 1) multiplies every part's size and
   offset, so a look can be authored at real proportions and drawn at display
   size. **Per-item `:yaw`** (degrees about y, default 0) turns the whole item;
   +90 points +x at world -z (upstream in the creek).
3. **Plan:** `scene-plan` always returns normalized `:parts` (scaled), plus the
   item's `:yaw`; `:center` and `:label-at` use the scaled parts' highest top
   (label `label-lift` above it). Invalid parts are dropped; a look with no
   valid parts falls back to the default stand-in. Code never names a mirror.
4. **Scene/wrapper:** each item is one group (positioned at `:at`, turned by
   `:yaw`) holding its parts; still built once, labels updated only on change.
   Add the shapes the wrapper lacks. New colours go in `palette` only (e.g.
   `:gill`, `:eye`, `:mane`).
5. **Data, both mirrors** (the same code path must serve two different looks):
   - Flora, authored at real axolotl proportions (about 0.26 m nose to tail:
     head ~0.05 wide, body ~0.12, tail ~0.09 and flattened sideways), with
     `:scale` so she's ~1 m long, `:yaw 90`, at `[-0.27 1.42 -15.7]` so her
     back sits at the water surface (~1.55) and her gills stand above it.
     Pink body; deeper pink gills; dark eyes.
   - Her worm jar: `:scale` so it's ~0.5 m tall, same spot.
   - Juniper: a horse of parts (body, neck, head, four legs, tail, mane) about
     2 m long and 1.6 m at the withers, `:yaw` so he stands side-on to the
     "The creek" camera (his long axis roughly along world z), same spot.

## Out of scope

Animation, swimming, rigged models, mouse picking, camera changes.

## Done when

`.crew/gate.sh` passes; plan tests cover: single-shape back-compat, parts
normalization, scale applied to sizes and offsets, label above the highest
scaled part, yaw passed through, invalid parts dropped, both mirrors' real data.
Hal checks the look live with screenshots from "The creek" and "Above it all".
