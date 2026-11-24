"""Repair the FP-1 animation axes and generate the updated aircraft visuals."""
import json
from pathlib import Path

from import_models import compile_mesh
from forpost_visuals import loft, octagon, quads
from lyutyi_visual import write_lyutyi


def fp1_nose():
    # The square aft ring shares its seam with the original hull. Chamfers
    # on the intermediate rings meet at their vertices and close the tip.
    rings = [octagon(*section) for section in (
        (-32.8, .94, 4.56, 6.47, .29),
        (-31.77, 1.78, 3.88, 7.13, .49),
        (-28.80, 3.47, 2.36, 8.85, .88),
        (-24.05, 5.20, 1, 10.20, 0),
    )]
    # Sample from inside each opaque atlas patch, including the flipped top and belly UVs.
    swatches = ((110.5, 4.5), (110.5, 4.5), (82.5, 4.5), (82.5, 4.5),
                (134.5, 4.5), (34.5, 4.5), (34.5, 4.5), (110.5, 4.5))
    faces = [tuple((point, u, v) for point in vertices)
             for vertices, (u, v) in quads(loft(rings, swatches, close=False))]
    for vertices, _ in quads([(rings[0], 0)]):
        faces.append(tuple((point, 8, 4) for point in vertices))
    return (("nose_shell", 0, 0, (0, 0, 0)), faces)


def update_airframes(root):
    fp1 = Path(__file__).resolve().parent / "model_sources/fp1.geo.json"
    data = json.loads(fp1.read_text(encoding="utf-8"))
    bones = {bone["name"]: bone for bone in data["minecraft:geometry"][0]["bones"]}
    body = bones["fuselage"]["cubes"]
    nose = fp1_nose()
    bones["fuselage"]["cubes"] = [cube for cube in body if cube["origin"][2] >= -24.051]

    prop = bones["propeller"]
    blade_uv = prop["cubes"][0]["uv"]
    shaft = [0, 7.55, 26.12]
    prop["pivot"] = shaft
    prop["cubes"] = [
        {"origin": [-.87, 6.68, 25.88], "size": [1.74, 1.74, .71], "uv": blade_uv},
        *({"origin": [.54, 6.97, 26.02], "size": [6.4, 1.16, .43],
           "pivot": shaft, "rotation": [0, 0, angle], "uv": blade_uv} for angle in (33, 213)),
    ]
    for cube in bones["tail"]["cubes"]:
        rotation = cube.get("rotation")
        if rotation:
            tilt = min(abs(rotation[2]), 180 - abs(rotation[2]))
            rotation[2] = tilt if cube["pivot"][0] < 0 else -tilt
    mesh, bounds = compile_mesh("fp1", data, False, 2.8, (nose,))
    (root / "mesh/fp1.milmesh").write_bytes(mesh)
    write_lyutyi(root)
    iskander = fp1.with_name("iskander_k.bbmodel")
    truck_mesh, truck_bounds = compile_mesh("iskander_k", json.loads(iskander.read_text()), True, 7.5)
    (root / "mesh/iskander_k.milmesh").write_bytes(truck_mesh)
    provenance = root / "model-provenance.json"
    metadata = json.loads(provenance.read_text(encoding="utf-8")) if provenance.exists() else {}
    metadata["fp1"] = {**metadata.get("fp1", {}), "geometry": "tools/model_sources/fp1.geo.json",
                       "generator": "tools/airframe_updates.py", "dimensions_blocks": bounds}
    metadata["lyutyi"] = {"source": "Original MilMod game mesh",
                          "visual_reference": "User-supplied Minecraft walkaround (1000004834.mp4)",
                          "generator": "tools/lyutyi_visual.py"}
    metadata["iskander_k"] = {**metadata.get("iskander_k", {}),
                               "geometry": "tools/model_sources/iskander_k.bbmodel",
                               "generator": "tools/airframe_updates.py", "dimensions_blocks": truck_bounds}
    provenance.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    from item_icons import ASSETS
    update_airframes(ASSETS)
