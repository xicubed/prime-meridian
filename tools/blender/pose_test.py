# Full-body deformation test: tail wave, body bend, head turn, two legs, jaw (JAW=rad), gills (GILL=rad,
# negative flares). Run: Blender -b flora-rigged.blend --python pose_test.py -- <outdir>
import bpy, sys, math
out = sys.argv[sys.argv.index('--') + 1]
rig = bpy.data.objects['FloraRig']; pb = rig.pose.bones
for k in range(1, 9): pb[f'tail_{k:02d}'].rotation_mode='XYZ'; pb[f'tail_{k:02d}'].rotation_euler = (0, 0, 0.22*math.sin(k*0.9))
for n in ('spine_01','spine_02','spine_03'): pb[n].rotation_mode='XYZ'; pb[n].rotation_euler=(0,0,0.15)
pb['head'].rotation_mode='XYZ'; pb['head'].rotation_euler=(0,0,0.3)
pb['leg_FL1'].rotation_mode='XYZ'; pb['leg_FL1'].rotation_euler=(0.9,0,0)
pb['leg_BR1'].rotation_mode='XYZ'; pb['leg_BR1'].rotation_euler=(-0.7,0,0)
import os
J=float(os.environ.get('JAW','0.4')); G=float(os.environ.get('GILL','-0.8'))
pb['jaw'].rotation_mode='XYZ'; pb['jaw'].rotation_euler=(J,0,0)
for b in pb:
    if b.name.startswith('gill_'): b.rotation_mode='XYZ'; b.rotation_euler=(G if b.name.endswith('_01') else G*0.4,0,0)
import os as _os
exec(open(_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), 'views.py')).read())
