# Quest: feet on the real ground (foot IK v0), and no hop

**Game:** Prime Meridian. **Asked by:** Michael: "I want the feet right lol." He watched Le
Testeur dodge Un Automate ("a little jig to get by, then a hop") and go around a rock.
**Led by:** Hal. Follows `motion-bricks-v0`, `avoid-v0`, `characters-v0`. Task M-1.

## Why

MotionBricks generates motion on flat ground. Today each frame one ray under the walker's root
finds the ground and the whole figure is lifted to it, so on a slope the uphill foot sinks and
the downhill foot floats, and when the root's ray lands on lower ground (a detour toward the
creek's gravel edge, ~2 m below the bank) the whole body drops or jumps: the hop.

## The moment we're building

Walking up and across the bank, each foot lands on the grass where it really is: the uphill foot
higher, the downhill foot lower, the planted foot tilted with the slope, the knees bending to
make up the difference and the hips dropping a little toward the lower foot. A walker stepping
aside never hops or sinks: it doesn't detour onto ground far below or above it, and its height
follows its feet, not a single point.

## How (after `character.update(dt)` each frame, in both kinds of body)

1. **Leg chains as data.** Per rig, the three joints of each leg plus the toe:
   SOMA mannequin `LeftLeg → LeftShin → LeftFoot (→ LeftToeBase)`, `Right…` the same;
   Mixamo `LeftUpLeg → LeftLeg → LeftFoot (→ LeftToeBase)` (names may carry a `mixamorig`
   prefix; match the way the port's own `lookup` does: exact, then without the prefix).
   In `resources/estate.edn` under `:motion :legs {:soma {...} :mixamo {...}}`.
2. **Ground under each foot.** Ray down at each foot's world x,z against the `:ground` meshes
   (as today). The model's own floor is the walker's base height `h0`; a foot's correction is
   `d = ground-under-foot − h0`, smoothed per foot.
3. **Pelvis.** Lower the whole walker by `max(0, −min(dL, dR))` (so the lower foot can reach),
   capped (data, 0.35 m), smoothed. The base height `h0` itself comes from the two feet's ground
   (the lower of the two planted feet), not from one ray under the root: this removes the hop.
4. **Two-bone IK.** Move each foot's target by its `d` and solve hip–knee–ankle analytically
   (law of cosines), keeping the knee bending the way it already bends (pole = the current knee),
   clamped when out of reach. Only the vertical correction: the generated stride stays.
5. **Foot tilt.** When a foot is planted (within 4 cm of the generated floor and nearly still),
   turn it to the ground's normal from the ray, at most 25°, blended in and out over ~0.1 s.
6. **Detours stay on walkable ground.** The avoidance planner rejects a detour point whose ground
   is missing or more than 0.4 m (data) above or below the walker's base height, and tries the
   other side / waits, as for a blocked point.
7. `?feet` (debug) draws each foot's ground point and target as small markers.

## What exists (read first)

`pm.bricks` (characters, `update(dt)`, mannequin `robot.joints` by name, retargeted character
bones via `character.robot`/the GLTF scene), `pm.motion` (ground hits, smoothing), the avoidance
planner, `t/drop-ray` (add a variant that also returns the hit's face normal if needed, through
`pm.three`), the vendored port (read `SOMAMannequin`, `RetargetedCharacter` and how `setPose`
writes joints; never edit it). Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`,
`public/state/`; don't edit `public/vendor/` or `resources/content/`.

## Out of scope

Stairs, jumping, hands/props, sitting, riding (the same IK will serve the stirrups later), the
kinematic figures (they have no legs).

## Done when

The gate passes with tests for the pure parts: two-bone IK (reachable, unreachable clamp, knee
side kept), pelvis drop and cap, base height from feet, planted detection, normal → tilt with the
cap, the detour ground-step rule, leg-name lookup with and without a prefix. Live (Hal checks
with `?bricks` and `?feet`): walking across the slope, feet sit on the grass (no visible float or
sink), the dodge near the creek edge has no hop, 60 fps.
