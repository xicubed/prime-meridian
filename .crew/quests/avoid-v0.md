# Quest: walkers go around each other, Juniper and the rocks (avoidance v0)

**Game:** Prime Meridian. **Asked by:** Michael, watching `motion-bricks-v0`: "they walk right
through each other! … if they run into each other they have to go around, and other objects."
**Led by:** Hal. Follows `motion-bricks-v0` (`pm.motion`, `pm.bricks`).

## The moment we're building

Un Automate and Le Testeur pace opposite ways along the west bank, so they meet head-on every
lap, right beside Juniper. Now, a second or two before they'd touch, each steps aside and they
pass, then carry on to their waypoint. Neither walks through Juniper or the rocks. It works the
same in the default (kinematic) mode and with `?bricks`, where the detour is just another
`moveTo` and MotionBricks generates the walk around.

## Rules (the planner; pure, in `pm.motion` or a new `pm.avoid`)

- **Bodies are circles on the ground (x, z).** A walker has `:radius` (data, default 0.3 m).
  Static things have **footprints**: a placed item may carry `:footprint [[dx dz r] …]`, circles
  in the item's own frame (rotated by its `:yaw`, scaled by its `:scale`, offset to its `:at`),
  so a long horse is two or three circles. Kit scenery that isn't ours goes in
  `:motion :obstacles [{:at [x z] :r r}]`. Nothing names a mirror or a walker in code.
- **Look ahead.** Every planner tick (≈0.25 s) a walker checks its straight path to its current
  target over a horizon (data, 2 s): static circles, and other walkers at their positions moved
  along their current velocity. Clearance = the two radii + a margin (0.15 m).
- **Detour.** If the path is blocked, it aims at a detour point beside the blocking circle,
  **on its own right first** (both walkers keep right, so a head-on meeting doesn't become a
  dance), far enough to clear it. If that point is inside any obstacle or the path to it is
  blocked, it tries the left. If both are blocked it waits in place (≤ 2 s), then tries again.
  Once its path to the real target is clear, it goes back to the target.
- **Targets inside an obstacle** (a waypoint too close to Juniper): stop at the nearest free
  point on the line to it, count that as arrived, and go on.
- No change to route order, pauses, ground height, or the `?bricks` loading.

## Data (`resources/estate.edn`; `touch src/pm/world.cljs` after editing)

- Juniper's body: `:footprint [[0.75 0 0.5] [0 0 0.5] [-0.6 0 0.45]]` (his local +x is his
  nose; his body is ~2.3 m long).
- Flora's jar: `:footprint [[0 0 0.35]]`. Flora herself is in the water: none.
- `:motion :obstacles [{:at [-4.8 -8.2] :r 0.7} {:at [-4.3 -12.0] :r 0.6}]` (rock clusters Hal
  probed), `:motion :radius 0.3`, `:motion :horizon-s 2`.

## For checking it

- HUD: the `motion:` segment adds the number of walkers currently detouring, e.g.
  `motion: kinematic · 1 detour`.
- `?paths` draws each walker's circle, its current target and detour point, and the footprints,
  as thin lines on the ground (debug only, through `pm.three`).

## Out of scope

Navigation meshes, physics and bumping, foot placement on slopes (foot IK), sitting, riding.

## Done when

The gate passes with tests for: head-on meeting (both pass on their right, never closer than
the two radii); a crossing at a right angle; a static circle on the line; right side blocked so
it takes the left; both blocked so it waits and then goes; a target inside a footprint; a clear
path makes no detour; footprints rotate with `:yaw` and scale. Live (Hal checks, both modes):
over several laps they never overlap each other or Juniper, and they keep their routes.
