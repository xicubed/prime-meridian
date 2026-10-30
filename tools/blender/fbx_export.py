# Export the rig + skinned mesh as a Y-up FBX for Maya/3ds Max (no leaf bones, no animation).
# Run: Blender -b flora-rigged.blend --python fbx_export.py -- <out.fbx>
import bpy, sys
out = sys.argv[sys.argv.index('--') + 1]
for o in bpy.context.scene.objects: o.select_set(o.name in ('Flora', 'FloraRig'))
bpy.ops.export_scene.fbx(filepath=out, use_selection=True, object_types={'ARMATURE', 'MESH'},
    axis_forward='-Z', axis_up='Y', apply_unit_scale=True, apply_scale_options='FBX_SCALE_NONE',
    add_leaf_bones=False, primary_bone_axis='Y', secondary_bone_axis='X',
    use_armature_deform_only=True, bake_anim=False, mesh_smooth_type='FACE', path_mode='STRIP')
