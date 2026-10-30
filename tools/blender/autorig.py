# Auto-rig a four-legged swimmer sculpt (built for Flora the axolotl): decimate to game-res, build a bone chain from the mesh's medial
# axis, skin with automatic weights, straighten the swept tail, apply as rest pose, scale to meters,
# UV unwrap, save .blend and export .glb.
# Run: Blender -b <sculpt.blend> --python autorig.py -- <out_dir> [target_tris] [length_m] [mouth_mm] [gill_retract_deg]
#   mouth_mm = snout_y,snout_z,corner_y,corner_z,half_width in the final rig's space (read off views.py renders)
import bpy, bmesh, heapq, math, sys
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree

args = sys.argv[sys.argv.index('--') + 1:]
OUT = args[0]
TARGET_TRIS = int(args[1]) if len(args) > 1 else 12000
LENGTH_M = float(args[2]) if len(args) > 2 else 0.22
# mouth line in FINAL space (mm, head toward -Y): snout_y, snout_z, corner_y, corner_z, half_width
MOUTH = [float(x) for x in (args[3] if len(args) > 3 else '-110,45.5,-94,43.5,22').split(',')]
GILL_RETRACT = math.radians(float(args[4]) if len(args) > 4 else 50)  # rest pose sweeps gills back

for n in ('Cube', 'Camera', 'Light'):  # Blender's default scene leftovers
    o = bpy.data.objects.get(n)
    if o: bpy.data.objects.remove(o)
ob = max((o for o in bpy.data.objects if o.type == 'MESH'), key=lambda o: len(o.data.polygons))  # the sculpt
ob.name = 'Flora'
bpy.context.view_layer.objects.active = ob
ob.select_set(True)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
me = ob.data
V = [v.co.copy() for v in me.vertices]

# ---------- analysis on the full-res sculpt ----------
adj = [[] for _ in V]
for e in me.edges:
    a, b = e.vertices; d = (V[a] - V[b]).length
    adj[a].append((b, d)); adj[b].append((a, d))
snout = min(range(len(V)), key=lambda i: V[i].y)
D = [1e18] * len(V); D[snout] = 0; h = [(0, snout)]
while h:
    d, u = heapq.heappop(h)
    if d > D[u]: continue
    for w, l in adj[u]:
        if d + l < D[w]: D[w] = d + l; heapq.heappush(h, (D[w], w))
tailtip = max(range(len(V)), key=lambda i: D[i])
GEO = D[tailtip]

bvh = BVHTree.FromPolygons(V, [p.vertices[:] for p in me.polygons])
mn = Vector([min(v[i] for v in V) for i in range(3)]); mx = Vector([max(v[i] for v in V) for i in range(3)])
STEP = 1.25
interior = []  # (point, depth, band-geo)
nx, ny, nz = [int((mx[i] - mn[i]) / STEP) + 1 for i in range(3)]
for i in range(nx):
    for j in range(ny):
        for k in range(nz):
            p = Vector((mn.x + i * STEP, mn.y + j * STEP, mn.z + k * STEP))
            loc, nor, fi, dist = bvh.find_nearest(p)
            if loc is None or (p - loc).dot(nor) >= 0: continue
            poly = me.polygons[fi]
            vi = min(poly.vertices, key=lambda q: (V[q] - loc).length)
            interior.append((p, dist, D[vi]))
print('INTERIOR', len(interior))

def depth_at(p):
    loc, nor, fi, dist = bvh.find_nearest(p)
    return dist if (p - loc).dot(nor) < 0 else -dist

# medial centerline: per geodesic band, the deepest interior point
NB = 48
bands = [[] for _ in range(NB)]
for p, dep, g in interior:
    bands[min(NB - 1, int(g / GEO * NB))].append((dep, p))
cl = [V[snout].copy()]
for b in bands:
    if not b: continue
    b.sort(key=lambda t: -t[0])
    top = [p for dep, p in b if dep >= 0.85 * b[0][0]]
    cl.append(sum(top, Vector()) / len(top))
cl.append(V[tailtip].copy())
sm = [cl[0]] + [(cl[i - 1] + cl[i] * 2 + cl[i + 1]) / 4 for i in range(1, len(cl) - 1)] + [cl[-1]]
arc = [0.0]
for i in range(1, len(sm)): arc.append(arc[-1] + (sm[i] - sm[i - 1]).length)
L = arc[-1]
def at(s):
    s = max(0, min(L, s))
    for i in range(1, len(arc)):
        if arc[i] >= s:
            t = (s - arc[i - 1]) / max(1e-9, arc[i] - arc[i - 1])
            return sm[i - 1].lerp(sm[i], t)
    return sm[-1].copy()
def nearest_arc(p, horiz=True):
    best = None
    for k in range(400):
        s = L * k / 399; q = at(s); dv = (q - p)
        if horiz: dv.z = 0
        if best is None or dv.length < best[0]: best = (dv.length, s)
    return best[1]
print('CENTERLINE length', round(L, 1), 'points', len(sm))

# feet: ground vertices clustered into 4 by quadrant around the body midpoint
gz = mn.z
ground = [V[i] for i in range(len(V)) if V[i].z < gz + 1.5 and D[i] > 5 and all(D[w] <= D[i] for w, _ in adj[i])]  # toe tips
bodymid = at(L * 0.3)
axis = (at(L * 0.4) - at(L * 0.15)); axis.z = 0; axis.normalize()   # head -> tail, horizontal
side = Vector((axis.y, -axis.x, 0))  # +side = character's left (+X when facing -Y)
# exclude the tail (it rests on the ground too): only vertices within body arc range
feet = {}
for g in ground:
    s = nearest_arc(g)
    if s > L * 0.55: continue
    f = 'F' if (g - bodymid).dot(axis) < 0 else 'B'
    lr = 'L' if (g - bodymid).dot(side) > 0 else 'R'
    feet.setdefault(f + lr, []).append(g)
feet = {k: sum(v, Vector()) / len(v) for k, v in feet.items()}
print('FEET', {k: tuple(round(c, 1) for c in v) for k, v in feet.items()})

s_sh = nearest_arc((feet['FL'] + feet['FR']) / 2)
s_hip = nearest_arc((feet['BL'] + feet['BR']) / 2)
s_neck = s_sh - 0.35 * (s_hip - s_sh)
print('ARCS neck/shoulder/hip', round(s_neck, 1), round(s_sh, 1), round(s_hip, 1), 'of', round(L, 1))

def leg_axis(foot, s_attach):
    body = at(s_attach)
    top = body.z - 0.25 * (body.z - gz)
    pts = []; xy = foot.copy()
    z = gz + STEP
    while z < top:
        lay = [p for p, dep, g in interior if abs(p.z - z) < STEP / 2 and (Vector((p.x - xy.x, p.y - xy.y))).length < 5]
        if lay:
            c = sum(lay, Vector()) / len(lay); xy = Vector((c.x, c.y, 0))
            pts.append(Vector((c.x, c.y, z)))
        z += STEP
    return body, pts

arm = bpy.data.armatures.new('FloraRig'); rig = bpy.data.objects.new('FloraRig', arm)
bpy.context.scene.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
eb = arm.edit_bones
def bone(name, a, b, parent=None, connect=False):
    x = eb.new(name); x.head = a; x.tail = b
    if parent: x.parent = eb[parent]; x.use_connect = connect
    return x

neck = at(s_neck)
bone('head', neck, V[snout].lerp(neck, 0.15))
sp = [at(s_neck + (s_hip - s_neck) * k / 3) for k in range(4)]
for k in range(3):
    bone(f'spine_{k+1:02d}', sp[k], sp[k + 1], 'head' if k == 0 else f'spine_{k:02d}', k > 0)
NT = 8
# tail joints spaced so segments shorten toward the tip
ts = [s_hip + (L - s_hip) * (1 - (1 - k / NT) ** 1.3) for k in range(NT + 1)]
for k in range(NT):
    bone(f'tail_{k+1:02d}', at(ts[k]), at(ts[k + 1]), 'spine_03' if k == 0 else f'tail_{k:02d}', True)

for key, s_att, par in (('FL', s_sh, 'spine_01'), ('FR', s_sh, 'spine_01'), ('BL', s_hip, 'spine_03'), ('BR', s_hip, 'spine_03')):
    body, pts = leg_axis(feet[key], s_att)
    wrist = pts[min(1, len(pts) - 1)]
    elbow = pts[len(pts) // 2] if len(pts) > 2 else wrist.lerp(body, 0.5)
    shoulder = body.lerp(Vector((elbow.x, elbow.y, body.z)), 0.6)
    shoulder.z = body.z - 0.3 * (body.z - elbow.z)
    toe = feet[key] + (feet[key] - Vector((wrist.x, wrist.y, feet[key].z))) * 0.6
    toe.z = gz + 0.8
    bone(f'leg_{key}1', shoulder, elbow, par)
    bone(f'leg_{key}2', elbow, wrist, f'leg_{key}1', True)
    bone(f'leg_{key}3', wrist, toe, f'leg_{key}2', True)
    print('LEG', key, [tuple(round(c, 1) for c in v) for v in (shoulder, elbow, wrist, toe)])

# gills: protruding tips in the head region, 3 per side by height
hc = at(s_neck * 0.8)
hr = max(depth_at(hc), 1)
cand = [i for i in range(len(V)) if D[i] < GEO * 0.42 and (V[i] - hc).length > hr * 1.5 and V[i].z > hc.z - hr * 0.4
        and all(D[w] <= D[i] for w, _ in adj[i])]
for lr, sgn in (('L', 1), ('R', -1)):
    tips = sorted([V[i] for i in cand if (V[i] - hc).dot(side) * sgn > hr * 0.5], key=lambda v: v.z)
    if len(tips) < 3: print('GILLS short', lr, len(tips)); continue
    picks = []
    for v in sorted(tips, key=lambda v: -(v - hc).length):
        if all((v - q).length > hr * 0.45 for q in picks): picks.append(v)
        if len(picks) == 3: break
    for gi, tip in enumerate(sorted(picks, key=lambda v: v.z), 1):  # gi 1 = lowest
        base = tip.copy()
        for t in range(1, 80):
            q = tip.lerp(hc, t / 80)
            if depth_at(q) > hr * 0.3: base = q; break
        mid = base.lerp(tip, 0.45)
        b1 = bone(f'gill_{lr}{gi}_01', base, mid, 'head')
        b2 = bone(f'gill_{lr}{gi}_02', mid, tip, f'gill_{lr}{gi}_01', True)
        # roll: local X = the hinge that sweeps the stalk toward the tail, so +X = retract, -X = flare
        hinge = (tip - base).normalized().cross(Vector((0, 1, 0))).normalized()
        for b in (b1, b2): b.align_roll(hinge.cross(b.vector.normalized()))
        print('GILL', lr, gi, tuple(round(c, 1) for c in base), '->', tuple(round(c, 1) for c in tip))
# consistent roll: local Z = world up for the body chain, so yaw (side bend) = local Z, pitch = local X
for b in eb: b.select = b.select_head = b.select_tail = b.name.startswith(('head', 'spine', 'tail'))
bpy.ops.armature.calculate_roll(type='GLOBAL_POS_Z')
bpy.ops.object.mode_set(mode='OBJECT')
print('BONES', [b.name for b in arm.bones])

# ---------- game-res mesh + skin ----------
bpy.context.view_layer.objects.active = ob
tris = sum(len(p.vertices) - 2 for p in me.polygons)
dec = ob.modifiers.new('dec', 'DECIMATE'); dec.ratio = TARGET_TRIS / tris
bpy.ops.object.modifier_apply(modifier='dec')
bpy.ops.object.shade_smooth()
for o in bpy.context.selected_objects: o.select_set(False)
ob.select_set(True); rig.select_set(True); bpy.context.view_layer.objects.active = rig
bpy.ops.object.parent_set(type='ARMATURE_AUTO')
unw = [v.index for v in ob.data.vertices if not any(g.weight > 0 for g in v.groups)]
print('SKIN verts', len(ob.data.vertices), 'unweighted', len(unw))

# ---------- straighten the tail (pose), then make it the rest pose ----------
bpy.ops.object.mode_set(mode='POSE')
pbs = rig.pose.bones
d_body = (pbs['spine_03'].tail - pbs['spine_01'].head); d_body.z *= 0.3; d_body.normalize()
for k in range(1, NT + 1):
    pb = pbs[f'tail_{k:02d}']
    M = pb.matrix.copy(); y = M.col[1].xyz.normalized()
    R = y.rotation_difference(d_body).to_matrix() @ M.to_3x3()
    N = R.to_4x4(); N.translation = M.translation; pb.matrix = N
    bpy.context.view_layer.update()
# gills: the calm axolotl holds them swept back; make that the rest pose, flapping = local X
for pb in pbs:
    if pb.name.startswith('gill_') and pb.name.endswith('_01'):
        pb.rotation_mode = 'XYZ'; pb.rotation_euler = (GILL_RETRACT, 0, 0)
    elif pb.name.startswith('gill_'):
        pb.rotation_mode = 'XYZ'; pb.rotation_euler = (GILL_RETRACT * 0.3, 0, 0)
bpy.context.view_layer.update()
bpy.ops.object.mode_set(mode='OBJECT')
bpy.context.view_layer.objects.active = ob
mod = [m for m in ob.modifiers if m.type == 'ARMATURE'][0]
bpy.ops.object.modifier_apply(modifier=mod.name)
am = ob.modifiers.new('Armature', 'ARMATURE'); am.object = rig
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='POSE'); bpy.ops.pose.armature_apply(selected=False)
bpy.ops.object.mode_set(mode='OBJECT')

# ---------- meters, feet on the ground, centered, head toward -Y ----------
co = [v.co for v in ob.data.vertices]
ymin = min(c.y for c in co); ymax = max(c.y for c in co)
s = LENGTH_M / (ymax - ymin)
cx = (min(c.x for c in co) + max(c.x for c in co)) / 2
T = Matrix.Scale(s, 4) @ Matrix.Translation((-cx, -(ymin + ymax) / 2, -min(c.z for c in co)))
ob.data.transform(T)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
for b in arm.edit_bones: b.head = T @ b.head; b.tail = T @ b.tail
bpy.ops.object.mode_set(mode='OBJECT')
co = [v.co for v in ob.data.vertices]
dims = [max(c[i] for c in co) - min(c[i] for c in co) for i in range(3)]
print('FINAL dims (m)', tuple(round(d, 3) for d in dims), 'tris', sum(len(p.vertices) - 2 for p in ob.data.polygons))

# ---------- jaw: bone, hand-shaped weights, lip seam split, mouth bag ----------
import bmesh
sy, sz, cy, cz, hw = [m / 1000 for m in MOUTH]
mx0 = min((v.co for v in ob.data.vertices), key=lambda c: c.y).x   # mouth centred on the snout tip
def mouth_z(y): return sz + (min(y, cy) - sy) * (cz - sz) / (cy - sy)
def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a))); return t * t * (3 - 2 * t)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
jb = arm.edit_bones.new('jaw'); jb.head = Vector((mx0, cy + 0.004, cz - 0.002)); jb.tail = Vector((mx0, sy + 0.004, sz - 0.004))
jb.parent = arm.edit_bones['head']; jb.align_roll(Vector((0, 0, -1)))  # local X = world +X: +X rotation opens
bpy.ops.object.mode_set(mode='OBJECT')
me2 = ob.data
def jaw_w(c):
    ax = abs(c.x - mx0)
    if ax > hw + 0.008 or c.z < 0.026 or c.y > cy + 0.016: return 0.0
    if c.z >= mouth_z(c.y): return 0.0
    w = 1.0 - smooth(cy, cy + 0.016, c.y)           # ramp out behind the mouth corner (throat stretches)
    w *= 1.0 - smooth(hw - 0.002, hw + 0.008, ax)
    w *= smooth(0.026, 0.032, c.z)                    # keep chest/forelimbs off the jaw
    return w
bm = bmesh.new(); bm.from_mesh(me2); bm.faces.ensure_lookup_table()
# cut a clean lip line: bisect the front of the head with the mouth plane, then split along the cut
pn = Vector((0, -(cz - sz) / (cy - sy), 1)).normalized(); pco = Vector((mx0, sy, sz))
reg = [f for f in bm.faces if f.calc_center_median().y < cy and abs(f.calc_center_median().x - mx0) < hw + 0.002
       and abs(f.calc_center_median().z - mouth_z(f.calc_center_median().y)) < 0.004]
rg = set(reg); re = {e for f in reg for e in f.edges}; rv = {v for f in reg for v in f.verts}
cut = bmesh.ops.bisect_plane(bm, geom=list(rg) + list(re) + list(rv), plane_co=pco, plane_no=pn, dist=0.0002)
bm.verts.ensure_lookup_table(); bm.faces.ensure_lookup_table()
on = lambda v: abs((v.co - pco).dot(pn)) < 0.0003
seam = [e for e in cut['geom_cut'] if isinstance(e, bmesh.types.BMEdge) and len(e.link_faces) == 2
        and all(on(v) and v.co.y < cy - 0.001 for v in e.verts)]
W = {v.index: jaw_w(v.co) for v in bm.verts}
print('LIP SEAM edges', len(seam))
bmesh.ops.split_edges(bm, edges=seam)
bm.verts.ensure_lookup_table(); bm.faces.ensure_lookup_table()
dl = bm.verts.layers.deform.verify()
jaw_gi = ob.vertex_groups.new(name='jaw').index
fside = {f: (f.calc_center_median() - pco).dot(pn) < 0 for f in bm.faces}
for v in bm.verts:
    w = W.get(v.index, None) if not on(v) else None
    if v.is_boundary and v.co.y < cy and on(v):
        w = 1.0 if sum(fside[f] for f in v.link_faces) * 2 > len(v.link_faces) else 0.0
    if w is None: w = jaw_w(v.co)
    d = v[dl]
    for g in list(d.keys()): d[g] *= (1 - w)
    if w > 0: d[jaw_gi] = w
    tot = sum(d.values())
    if tot > 0:
        for g in list(d.keys()): d[g] /= tot
bm.to_mesh(me2); bm.free()
# mouth bag: dark ellipsoid tucked just inside the lips, top half on head, bottom half on jaw
bag_ctr = Vector((mx0, (sy + cy) / 2 + 0.004, (sz + cz) / 2 - 0.001))
bbm = bmesh.new()
bmesh.ops.create_uvsphere(bbm, u_segments=24, v_segments=12, radius=1.0)
for v in bbm.verts: v.co = Vector((v.co.x * (hw - 0.004), v.co.y * (cy - sy) * 0.45, v.co.z * 0.0035)) + bag_ctr
skin_bvh = BVHTree.FromObject(ob, bpy.context.evaluated_depsgraph_get())
def inside(p, margin=0.0008):
    loc, nor, fi, dist = skin_bvh.find_nearest(p)
    return loc is not None and (p - loc).dot(nor) < 0 and dist > margin
pulled = 0
for v in bbm.verts:
    for _ in range(30):
        if inside(v.co): break
        v.co = bag_ctr + (v.co - bag_ctr) * 0.92; pulled += 1
print('BAG verts pulled inside', pulled)
bag_me = bpy.data.meshes.new('MouthBag'); bbm.to_mesh(bag_me); bbm.free()
bag = bpy.data.objects.new('MouthBag', bag_me); bpy.context.scene.collection.objects.link(bag)
hg = bag.vertex_groups.new(name='head'); jg = bag.vertex_groups.new(name='jaw')
for v in bag_me.vertices:
    w = smooth(bag_ctr.z + 0.0012, bag_ctr.z - 0.0012, v.co.z)
    if w < 1: hg.add([v.index], 1 - w, 'REPLACE')
    if w > 0: jg.add([v.index], w, 'REPLACE')
skin = bpy.data.materials.new('FloraSkin'); skin.diffuse_color = (0.95, 0.62, 0.68, 1)
mouth = bpy.data.materials.new('FloraMouth'); mouth.diffuse_color = (0.35, 0.08, 0.12, 1)
for mat in (skin, mouth):
    mat.use_nodes = True; mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = mat.diffuse_color
ob.data.materials.clear(); ob.data.materials.append(skin); bag_me.materials.append(mouth)
for o in bpy.context.selected_objects: o.select_set(False)
ob.select_set(True); bag.select_set(True); bpy.context.view_layer.objects.active = ob
bpy.ops.object.join()
print('JAW weighted verts', sum(1 for v in ob.data.vertices for g in v.groups if g.group == ob.vertex_groups['jaw'].index and g.weight > 0.01))

# ---------- UVs ----------
bpy.context.view_layer.objects.active = ob
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.003)
bpy.ops.object.mode_set(mode='OBJECT')

bpy.ops.wm.save_as_mainfile(filepath=f'{OUT}/flora-rigged.blend')
for o in bpy.context.scene.objects: o.select_set(o in (ob, rig))
bpy.ops.export_scene.gltf(filepath=f'{OUT}/flora-rigged.glb', export_format='GLB', use_selection=True,
                          export_yup=True, export_skins=True, export_animations=False, export_apply=False)
print('DONE')
