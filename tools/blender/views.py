# Render 4 workbench views (persp/top/side/front) of the largest mesh so a model can be checked
# without seeing a viewport. Run:
#   /Applications/Blender.app/Contents/MacOS/Blender -b <file.blend> --python blend_views.py -- <outdir>
# then tile them for one vision call (Hermes venv Python has PIL):
#   python -c "from PIL import Image;n=['persp','top','side','front'];ims=[Image.open(f'<outdir>/v_{x}.png') for x in n];W,H=ims[0].size;g=Image.new('RGB',(W*2,H*2));[g.paste(im,((i%2)*W,(i//2)*H)) for i,im in enumerate(ims)];g.save('<outdir>/grid.png')"
import bpy, sys, mathutils

out = sys.argv[sys.argv.index('--') + 1] if '--' in sys.argv else '/tmp'
sc = bpy.context.scene
meshes = [o for o in bpy.data.objects if o.type == 'MESH']
ob = max(meshes, key=lambda o: len(o.data.polygons))
for o in meshes:
    o.hide_render = o is not ob  # hide Blender's default cube etc.
sc.render.engine = 'BLENDER_WORKBENCH'
sc.display.shading.light = 'STUDIO'
sc.display.shading.show_cavity = True
sc.render.resolution_x, sc.render.resolution_y = 900, 700
bb = [ob.matrix_world @ mathutils.Vector(c) for c in ob.bound_box]
ctr = sum(bb, mathutils.Vector()) / 8
r = max((v - ctr).length for v in bb)
cam = bpy.data.objects.get('Camera')
if cam is None:
    cam = bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera'))
    sc.collection.objects.link(cam)
cam.data.clip_end = r * 100
sc.camera = cam
for name, d in [('persp', (1, -1, 0.7)), ('top', (0, 0, 1)), ('side', (1, 0, 0)), ('front', (0, -1, 0))]:
    cam.location = ctr + mathutils.Vector(d).normalized() * r * 3.2
    cam.rotation_euler = (0, 0, 0) if name == 'top' else (ctr - cam.location).to_track_quat('-Z', 'Y').to_euler()
    sc.render.filepath = f'{out}/v_{name}.png'
    bpy.ops.render.render(write_still=True)
print('OBJECT', ob.name, 'CENTER', tuple(round(c, 2) for c in ctr), 'RADIUS', round(r, 2))
