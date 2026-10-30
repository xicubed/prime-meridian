# Headless inventory of a .blend: objects, tris, UVs, materials, modifiers, armature bones.
# Run: /Applications/Blender.app/Contents/MacOS/Blender -b <file.blend> --python blend_inspect.py \
#        2>&1 | grep -E '^(SCENE|OBJ|IMAGES|MATERIALS)'
import bpy

us = bpy.context.scene.unit_settings
print("SCENE", bpy.context.scene.name, "unit", us.system, us.scale_length)
for o in bpy.data.objects:
    line = (f"OBJ {o.name} type={o.type} parent={o.parent.name if o.parent else None} "
            f"dims={tuple(round(v, 3) for v in o.dimensions)} scale={tuple(round(v, 3) for v in o.scale)}")
    if o.type == 'MESH':
        m = o.data
        tris = sum(len(p.vertices) - 2 for p in m.polygons)
        line += (f" verts={len(m.vertices)} faces={len(m.polygons)} tris={tris} uv={len(m.uv_layers)}"
                 f" mats={[s.material.name if s.material else None for s in o.material_slots]}"
                 f" mods={[md.type for md in o.modifiers]} vgroups={len(o.vertex_groups)}")
    if o.type == 'ARMATURE':
        line += f" bones={[b.name for b in o.data.bones]}"
    print(line)
print("IMAGES", [(i.name, i.filepath, tuple(i.size)) for i in bpy.data.images])
print("MATERIALS", [m.name for m in bpy.data.materials])
