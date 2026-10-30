# Head close-ups (3/4 front, side, top) with the jaw/gills posed: JAW=0.45 opens, GILL=-0.8 flares.
# Run: JAW=0.45 GILL=0 Blender -b flora-rigged.blend --python head_test.py -- <outdir>
import bpy, sys, os, math
from mathutils import Vector
out=sys.argv[sys.argv.index('--')+1]
rig=bpy.data.objects['FloraRig']; pb=rig.pose.bones
J=float(os.environ.get('JAW','0')); G=float(os.environ.get('GILL','0'))
pb['jaw'].rotation_mode='XYZ'; pb['jaw'].rotation_euler=(J,0,0)
for b in pb:
    if b.name.startswith('gill_'): b.rotation_mode='XYZ'; b.rotation_euler=(G if b.name.endswith('_01') else G*0.4,0,0)
sc=bpy.context.scene
for o in bpy.data.objects: o.hide_render = o.type!='MESH' or o.name=='Cube'
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.light='STUDIO'; sc.display.shading.color_type='MATERIAL'; sc.display.shading.show_cavity=True
sc.render.resolution_x=sc.render.resolution_y=600
cam=bpy.data.objects.new('C',bpy.data.cameras.new('C')); sc.collection.objects.link(cam); sc.camera=cam
cam.data.type='ORTHO'; cam.data.ortho_scale=0.1
ctr=Vector((0,-0.085,0.05))
for name,d in (('front34',(0.6,-1,0.25)),('side',(1,-0.05,0.05)),('top',(0.01,0.2,1))):
    cam.location=ctr+Vector(d).normalized()*0.3
    cam.rotation_euler=(ctr-cam.location).to_track_quat('-Z','Y').to_euler()
    sc.render.filepath=f'{out}/{name}.png'; bpy.ops.render.render(write_still=True)
