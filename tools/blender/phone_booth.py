# The phone booth prop: a red kiosk after the classic London K6 (Giles Gilbert Scott, 1935),
# built from code so it can be re-made and tuned. Ours, so it lives in the public repo.
#
#   /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
#     --python tools/blender/phone_booth.py -- public/assets/props/phone-booth.glb [preview_dir]
#
# Blender is Z-up; the glTF export turns it Y-up, so Blender -Y (the door) faces the game's +z.
# Origin: centre of the base, on the ground. About 0.95 m square, 2.5 m to the top of the crown.
# Material names are the contract with the game (pm.three maps them):
#   booth-red  painted cast iron, a little faded      glass  the panes (game swaps in the kit's glass)
#   sign       the backlit TELEPHONE panels            letters  the black lettering
#   floor      concrete step                            metal  door handle, phone
#   lamp       the ceiling light (glows at night)

import bpy, bmesh, sys, math, os
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
OUT = os.path.abspath(argv[0]) if argv else '/tmp/phone-booth.glb'
PREVIEW = os.path.abspath(argv[1]) if len(argv) > 1 and argv[1] else None
BLEND = os.path.abspath(argv[2]) if len(argv) > 2 else None

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

# --- materials -------------------------------------------------------------------------------
def mat(name, rgb, rough=0.6, metal=0.0, emit=None, emit_strength=0.0, alpha=1.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*rgb, 1.0)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal
    if emit:
        b.inputs['Emission Color'].default_value = (*emit, 1.0)
        b.inputs['Emission Strength'].default_value = emit_strength
    if alpha < 1.0:
        b.inputs['Alpha'].default_value = alpha
        m.surface_render_method = 'BLENDED'
    m.diffuse_color = (*rgb, alpha)          # workbench preview colour
    return m

M = {
    'booth-red': mat('booth-red', (0.52, 0.035, 0.03), rough=0.55),
    'glass':     mat('glass', (0.75, 0.85, 0.88), rough=0.05, alpha=0.35),
    'sign':      mat('sign', (0.95, 0.94, 0.88), rough=0.4, emit=(1.0, 0.95, 0.8), emit_strength=0.3),
    'letters':   mat('letters', (0.03, 0.03, 0.03), rough=0.5),
    'floor':     mat('floor', (0.26, 0.25, 0.23), rough=0.9),
    'metal':     mat('metal', (0.08, 0.08, 0.08), rough=0.35, metal=0.8),
    'lamp':      mat('lamp', (1.0, 0.9, 0.7), rough=0.3, emit=(1.0, 0.85, 0.55), emit_strength=2.0),
}

parts = []

def box(name, size, at, material):
    """A box `size` (x y z) centred at `at`."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector(size), verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector(at), verts=bm.verts)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    scene.collection.objects.link(ob)
    me.materials.append(M[material])
    parts.append(ob)
    return ob

def arch(name, width, depth, z0, rise, thick_axis, material, seg=16):
    """A segmental pediment: a slab `width` wide whose top is an arc rising `rise` above z0,
    `depth` thick along `thick_axis` ('x' or 'y'). Bottom at z0 - 0.06."""
    bm = bmesh.new()
    prof = []
    for i in range(seg + 1):
        u = -width / 2 + width * i / seg
        prof.append((u, z0 + rise * (1 - (2 * u / width) ** 2)))
    pts = [(-width / 2, z0 - 0.06)] + prof + [(width / 2, z0 - 0.06)]
    front, back = [], []
    for (u, z) in pts:
        if thick_axis == 'y':
            front.append(bm.verts.new((u, -depth / 2, z))); back.append(bm.verts.new((u, depth / 2, z)))
        else:
            front.append(bm.verts.new((-depth / 2, u, z))); back.append(bm.verts.new((depth / 2, u, z)))
    bm.faces.new(front); bm.faces.new(list(reversed(back)))
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new([front[i], front[j], back[j], back[i]])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    scene.collection.objects.link(ob)
    me.materials.append(M[material])
    parts.append(ob)
    return ob

# --- dimensions (m) --------------------------------------------------------------------------
W = 0.88            # body, outside
POST = 0.09         # corner posts
PLINTH_H = 0.08
GLAZE_Z0, GLAZE_Z1 = 0.36, 1.86
SIGN_Z0, SIGN_Z1 = 1.93, 2.10
CORNICE_Z = 2.14
ROOF_RISE = 0.17
BAR = 0.022         # glazing bars
ROWS = 8
COLS = (0.25, 0.5, 0.25)   # the K6's wider middle column

# --- base, posts, lower panels, roof ---------------------------------------------------------
box('step', (W + 0.10, W + 0.10, PLINTH_H), (0, 0, PLINTH_H / 2), 'floor')
for sx in (-1, 1):
    for sy in (-1, 1):
        box(f'post{sx}{sy}', (POST, POST, CORNICE_Z - PLINTH_H),
            (sx * (W - POST) / 2, sy * (W - POST) / 2, (CORNICE_Z + PLINTH_H) / 2), 'booth-red')
inner = W - 2 * POST
box('floor-in', (inner, inner, 0.02), (0, 0, PLINTH_H + 0.01), 'floor')

def side(axis, sign, door=False):
    """One face: solid kick panel, glazing (panes + bars), the TELEPHONE sign above.
    axis 'y' = a face normal along y (front/back), 'x' = along x (left/right)."""
    off = sign * (W / 2 - 0.03)          # face plane, a little inside the post faces
    def put(name, du, dz, su, sz, thick, material, out=0.0):
        o = off + sign * out
        if axis == 'y':
            box(name, (su, thick, sz), (du, o, dz), material)
        else:
            box(name, (thick, su, sz), (o, du, dz), material)
    tag = f'{axis}{"+" if sign > 0 else "-"}'
    # kick panel and the rail under the sign
    put(f'kick{tag}', 0, (PLINTH_H + GLAZE_Z0) / 2, inner, GLAZE_Z0 - PLINTH_H, 0.04, 'booth-red')
    put(f'rail{tag}', 0, (GLAZE_Z1 + SIGN_Z0) / 2, inner, SIGN_Z0 - GLAZE_Z1, 0.05, 'booth-red')
    # glass: one sheet behind the bars
    put(f'glass{tag}', 0, (GLAZE_Z0 + GLAZE_Z1) / 2, inner, GLAZE_Z1 - GLAZE_Z0, 0.006, 'glass', out=-0.01)
    # vertical bars between the three columns, plus the frame sides
    u = -inner / 2
    xs = [u]
    for c in COLS:
        u += c * inner
        xs.append(u)
    for i, x in enumerate(xs):
        w = 0.045 if i in (0, len(xs) - 1) else BAR
        put(f'vbar{tag}{i}', x, (GLAZE_Z0 + GLAZE_Z1) / 2, w, GLAZE_Z1 - GLAZE_Z0, 0.035, 'booth-red')
    # horizontal bars: ROWS rows of panes
    for r in range(ROWS + 1):
        z = GLAZE_Z0 + (GLAZE_Z1 - GLAZE_Z0) * r / ROWS
        h = 0.045 if r in (0, ROWS) else BAR
        put(f'hbar{tag}{r}', 0, z, inner, h, 0.035, 'booth-red')
    # the sign: a white backlit panel with black letters, framed red
    put(f'signframe{tag}', 0, (SIGN_Z0 + SIGN_Z1) / 2, inner + 0.02, SIGN_Z1 - SIGN_Z0 + 0.04, 0.03, 'booth-red', out=-0.012)
    put(f'sign{tag}', 0, (SIGN_Z0 + SIGN_Z1) / 2, inner - 0.04, SIGN_Z1 - SIGN_Z0 - 0.03, 0.01, 'sign', out=0.006)
    letters(axis, sign, off + sign * 0.013)
    if door:
        # handle on the door's opening side, and a thin seam where the door meets its frame
        put(f'handle{tag}', inner / 2 - 0.08, 1.05, 0.03, 0.16, 0.05, 'metal', out=0.03)

def letters(axis, sign, plane):
    bpy.ops.object.text_add()
    t = bpy.context.object
    t.data.body = 'TELEPHONE'
    t.data.align_x = 'CENTER'; t.data.align_y = 'CENTER'
    t.data.size = 0.085
    t.data.extrude = 0.003
    t.data.space_character = 1.15
    bpy.ops.object.convert(target='MESH')
    ob = bpy.context.object
    ob.name = f'letters{axis}{sign}'
    ob.data.materials.clear(); ob.data.materials.append(M['letters'])
    # text lies in its local XY plane facing +Z: stand it up on the face, reading from outside
    ob.rotation_euler = (math.radians(90), 0, 0)
    bpy.ops.object.transform_apply(rotation=True)
    zc = (SIGN_Z0 + SIGN_Z1) / 2
    if axis == 'y':
        if sign > 0:
            ob.rotation_euler = (0, 0, math.radians(180))
        ob.location = (0, plane, zc)
    else:
        ob.rotation_euler = (0, 0, math.radians(90 if sign > 0 else -90))
        ob.location = (plane, 0, zc)
    bpy.ops.object.transform_apply(location=True, rotation=True)
    parts.append(ob)

side('y', -1, door=True)   # front: the door (game +z)
side('y', +1)
side('x', -1)
side('x', +1)

# cornice, the four domed pediments (two crossing arches), and the cap
box('cornice', (W + 0.06, W + 0.06, 0.06), (0, 0, CORNICE_Z + 0.03), 'booth-red')
arch('roof-y', W + 0.02, W + 0.02, CORNICE_Z + 0.12, ROOF_RISE, 'y', 'booth-red')
arch('roof-x', W + 0.02, W + 0.02, CORNICE_Z + 0.12, ROOF_RISE, 'x', 'booth-red')
box('cap', (0.36, 0.36, 0.06), (0, 0, CORNICE_Z + 0.12 + ROOF_RISE + 0.01), 'booth-red')
bpy.ops.mesh.primitive_uv_sphere_add(radius=0.1, segments=16, ring_count=8,
                                     location=(0, 0, CORNICE_Z + 0.12 + ROOF_RISE + 0.05))
dome = bpy.context.object; dome.name = 'finial'
dome.scale = (1, 1, 0.55); bpy.ops.object.transform_apply(scale=True)
dome.data.materials.append(M['booth-red']); parts.append(dome)
# a crown boss over each sign (the K6's embossed crown, simplified)
for (dx, dy, rot) in ((0, -1, 0), (0, 1, 0), (-1, 0, 90), (1, 0, 90)):
    bpy.ops.mesh.primitive_cylinder_add(radius=0.055, depth=0.03, vertices=16,
        location=(dx * (W / 2 + 0.02), dy * (W / 2 + 0.02), CORNICE_Z + 0.16),
        rotation=(math.radians(90), 0, math.radians(rot)))
    c = bpy.context.object; c.name = f'crown{dx}{dy}'
    c.data.materials.append(M['booth-red']); parts.append(c)

# inside: the phone on the back wall, the ceiling lamp
box('phone', (0.2, 0.12, 0.26), (0, inner / 2 - 0.08, 1.25), 'metal')
box('lamp', (0.14, 0.14, 0.04), (0, 0, CORNICE_Z - 0.04), 'lamp')

# --- weathering: vines up two corners, like the sprigs at the cottage's foundation ------------
import random
random.seed(7)
M['leaf'] = mat('leaf', (0.035, 0.085, 0.022), rough=0.7)
M['stem'] = mat('stem', (0.12, 0.10, 0.05), rough=0.8)

def leaf(bm, at, out, up, size):
    """A small pointed leaf (6 verts) at `at`, its blade along `up`, facing `out`."""
    side = up.cross(out).normalized()
    tip = at + up * size
    pts = [at, at + up * size * 0.35 + side * size * 0.32, at + up * size * 0.7 + side * size * 0.22,
           tip, at + up * size * 0.7 - side * size * 0.22, at + up * size * 0.35 - side * size * 0.32]
    vs = [bm.verts.new(p + out * 0.004 * (i % 2)) for i, p in enumerate(pts)]
    bm.faces.new(vs)

def vine(name, corner, out_dir, height, leaves):
    """A stem wandering up one corner post with leaves on alternate sides; a clump at its foot."""
    bm_leaf, bm_stem = bmesh.new(), bmesh.new()
    cx, cy = corner
    out = Vector(out_dir).normalized()
    prev = None
    n = 14
    pts = []
    for i in range(n + 1):
        t = i / n
        wob = math.sin(t * 9.0 + cx * 3) * 0.035
        p = Vector((cx, cy, PLINTH_H + t * height)) + out * (0.055 + wob * 0.3) + Vector((-out.y, out.x, 0)) * wob
        pts.append(p)
    for a, b in zip(pts, pts[1:]):
        d = (b - a)
        bmesh.ops.create_cone(bm_stem, cap_ends=False, segments=4, radius1=0.006, radius2=0.005, depth=d.length,
                              matrix=__import__('mathutils').Matrix.Translation((a + b) / 2) @ d.to_track_quat('Z', 'Y').to_matrix().to_4x4())
    for k in range(leaves):
        t = (k + 0.5) / leaves
        i = min(int(t * n), n - 1)
        p = pts[i].lerp(pts[i + 1], t * n - i)
        sgn = 1 if k % 2 else -1
        side = Vector((-out.y, out.x, 0)) * sgn
        up = (Vector((0, 0, 1)) * 0.6 + side * 0.8 + out * 0.2).normalized()
        leaf(bm_leaf, p, (out + side * 0.3).normalized(), up, 0.05 + random.random() * 0.035 * (1 - t * 0.6))
    for k in range(16):                                  # the clump at the foot
        a = random.random() * math.pi - math.pi / 2
        d = out * math.cos(a) + Vector((-out.y, out.x, 0)) * math.sin(a)
        base = Vector((cx, cy, PLINTH_H)) + out * 0.06 + d * random.random() * 0.12
        up = (Vector((0, 0, 1)) + d * 0.9).normalized()
        leaf(bm_leaf, base, d, up, 0.07 + random.random() * 0.05)
    for bm, matname, nm in ((bm_leaf, 'leaf', name + '-leaves'), (bm_stem, 'stem', name + '-stem')):
        me = bpy.data.meshes.new(nm); bm.to_mesh(me); bm.free()
        ob = bpy.data.objects.new(nm, me); scene.collection.objects.link(ob)
        me.materials.append(M[matname]); parts.append(ob)

c = W / 2
vine('vine-fl', (-c, -c), (-0.7, -0.7, 0), 0.95, 22)   # front-left corner, beside the door
vine('vine-br', (c, c), (0.7, 0.7, 0), 0.55, 12)       # back-right, shorter

# --- join, unwrap, bake the weathered paint -------------------------------------------------
for o in parts:
    o.select_set(False)
bpy.ops.object.select_all(action='DESELECT')
for o in parts:
    o.select_set(True)
bpy.context.view_layer.objects.active = parts[0]
bpy.ops.object.join()
booth = bpy.context.object
booth.name = 'phone-booth'
bpy.ops.object.shade_flat()
for p in booth.data.polygons:          # leaves: smooth looks softer
    if booth.data.materials[p.material_index].name == 'leaf':
        p.use_smooth = True

bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.004)
bpy.ops.object.mode_set(mode='OBJECT')

def weather(m, size, moss_h, rust):
    """Faded paint, rust streaks under the joints, moss creeping up from the ground, baked into
    an image so the game gets a plain texture (no procedural nodes in glTF)."""
    nt = m.node_tree; N = nt.nodes; L = nt.links
    bsdf = N['Principled BSDF']
    base = tuple(bsdf.inputs['Base Color'].default_value)
    tc = N.new('ShaderNodeTexCoord')
    sep = N.new('ShaderNodeSeparateXYZ'); L.new(tc.outputs['Object'], sep.inputs[0])
    def noise(scale, detail, stretch=(1, 1, 1)):
        mp = N.new('ShaderNodeMapping'); mp.inputs['Scale'].default_value = stretch
        L.new(tc.outputs['Object'], mp.inputs[0])
        nz = N.new('ShaderNodeTexNoise'); nz.inputs['Scale'].default_value = scale
        nz.inputs['Detail'].default_value = detail; L.new(mp.outputs[0], nz.inputs['Vector'])
        return nz.outputs['Fac']
    def ramp(fac, a, b):
        r = N.new('ShaderNodeMapRange'); r.inputs['From Min'].default_value = a
        r.inputs['From Max'].default_value = b; L.new(fac, r.inputs['Value']); return r.outputs['Result']
    def mix(a_col, b_col, fac):
        mx = N.new('ShaderNodeMix'); mx.data_type = 'RGBA'
        L.new(fac, mx.inputs['Factor'])
        for sock, v in ((mx.inputs[6], a_col), (mx.inputs[7], b_col)):
            if isinstance(v, tuple): sock.default_value = v
            else: L.new(v, sock)
        return mx.outputs[2]
    faded = (base[0] * 1.12 + 0.03, base[1] * 1.6 + 0.02, base[2] * 1.5 + 0.015, 1)
    col = mix(base, faded, ramp(noise(3.0, 6), 0.45, 0.8))                   # sun-faded patches
    if rust:
        streak = ramp(noise(9.0, 4, (6, 6, 0.5)), 0.52, 0.66)                 # long vertical streaks
        col = mix(col, (0.16, 0.06, 0.025, 1), streak)
    # moss: strong at the ground, fading out by moss_h, broken up by noise
    h = N.new('ShaderNodeMapRange'); h.inputs['From Min'].default_value = 0.0
    h.inputs['From Max'].default_value = moss_h; h.inputs['To Min'].default_value = 1.0
    h.inputs['To Max'].default_value = 0.0; L.new(sep.outputs['Z'], h.inputs['Value'])
    mm = N.new('ShaderNodeMath'); mm.operation = 'MULTIPLY'
    L.new(h.outputs['Result'], mm.inputs[0]); L.new(ramp(noise(7.0, 8), 0.3, 0.55), mm.inputs[1])
    col = mix(col, (0.055, 0.09, 0.025, 1), mm.outputs[0])
    img = bpy.data.images.new(m.name + '-baked', size, size)
    tex = N.new('ShaderNodeTexImage'); tex.image = img
    N.active = tex
    return col, bsdf, tex, img

scene.render.engine = 'CYCLES'
scene.cycles.samples = 4
scene.cycles.device = 'CPU'
for name, size, moss_h, rust in (('booth-red', 1024, 0.7, True), ('floor', 256, 0.3, False)):
    m = M[name]
    col, bsdf, tex, img = weather(m, size, moss_h, rust)
    # bake: show the weathered colour as emission, bake EMIT, then wire the image in
    nt = m.node_tree
    em = nt.nodes.new('ShaderNodeEmission'); nt.links.new(col, em.inputs['Color'])
    out = nt.nodes['Material Output']
    old = out.inputs['Surface'].links[0].from_socket
    nt.links.new(em.outputs[0], out.inputs['Surface'])
    # Cycles bakes every material's faces into its *active* image node: give each material a
    # temporary node on this image (other islands land in unused UV space), then remove them.
    temps = []
    for other in booth.data.materials:
        if other is m:
            other.node_tree.nodes.active = tex
            continue
        t = other.node_tree.nodes.new('ShaderNodeTexImage'); t.image = img
        other.node_tree.nodes.active = t; temps.append((other, t))
    bpy.ops.object.bake(type='EMIT', margin=4)
    for other, t in temps:
        other.node_tree.nodes.remove(t)
    nt.links.new(old, out.inputs['Surface'])
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    img.pack()
    print('phone_booth: baked', name)

# --- export ----------------------------------------------------------------------------------
os.makedirs(os.path.dirname(OUT), exist_ok=True)
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', use_selection=True,
                          export_apply=True, export_yup=True)
tris = sum(len(p.vertices) - 2 for p in booth.data.polygons)
print(f'phone_booth: wrote {OUT} · {tris} triangles · {len(booth.data.materials)} materials')
if BLEND:
    os.makedirs(os.path.dirname(BLEND), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=BLEND)
    print('phone_booth: saved', BLEND)

# --- previews (workbench, material colours) --------------------------------------------------
if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    scene.render.engine = 'BLENDER_EEVEE'
    scene.eevee.taa_render_samples = 32
    scene.world = bpy.data.worlds.new('w'); scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.7, 0.78, 0.8, 1)
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.9
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 3.5; sun.rotation_euler = (math.radians(50), 0, math.radians(-30))
    scene.collection.objects.link(sun)
    gnd = bpy.data.meshes.new('gnd'); bmg = bmesh.new()
    bmesh.ops.create_grid(bmg, x_segments=1, y_segments=1, size=3); bmg.to_mesh(gnd); bmg.free()
    g = bpy.data.objects.new('gnd', gnd); scene.collection.objects.link(g)
    gnd.materials.append(mat('grass', (0.18, 0.3, 0.1), rough=0.9))
    scene.render.resolution_x, scene.render.resolution_y = 900, 1200
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam); scene.camera = cam
    cam.data.lens = 50
    for nm, (az, el, dist, tz) in {'front': (-35, 12, 6.2, 1.25), 'back': (145, 18, 6.2, 1.25),
                                   'base': (-40, 14, 2.6, 0.55)}.items():
        a, e = math.radians(az), math.radians(el)
        tgt = Vector((0, 0, tz))
        cam.location = tgt + Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e))) * dist
        cam.rotation_euler = (tgt - cam.location).to_track_quat('-Z', 'Y').to_euler()
        scene.render.filepath = os.path.join(PREVIEW, f'booth_{nm}.png')
        bpy.ops.render.render(write_still=True)
    print('phone_booth: previews in', PREVIEW)
