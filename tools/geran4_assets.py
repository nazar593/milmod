"""Import the supplied Geran-4 geometry and atlas into the MilMod mesh format."""
import json
from pathlib import Path
import struct

from import_models import compile_mesh, texture_dimensions

SOURCES = Path(__file__).resolve().parent / "model_sources"
ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"


def write_geran4(root):
    geometry = SOURCES / "geran4.geo.json"
    texture = SOURCES / "geran4.png"
    data = json.loads(geometry.read_text(encoding="utf-8"))
    png = texture.read_bytes()
    if png[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack_from(">II", png, 16) != texture_dimensions(data, False):
        raise ValueError("Geran-4 geometry and PNG atlas dimensions differ")

    bones = data["minecraft:geometry"][0]["bones"]
    for bone in bones:
        # This model's left side is positive X in the source geometry, while
        # the MilMod damage mask identifies the negative-X side as part one.
        name = bone["name"]
        if name in ("wing_l", "wing_r", "fin_l", "fin_r"):
            bone["name"] = name[:-1] + ("r" if name.endswith("l") else "l")
        for cube in bone["cubes"]:
            # The supplied atlas puts the tips of the cutout polygons at the
            # opposite V end from the common Minecraft mesh face convention.
            for direction in ("up", "down"):
                face = cube["uv"].get(direction)
                if face is not None:
                    face["uv"][1] += face["uv_size"][1]
                    face["uv_size"][1] *= -1

    mesh, dimensions = compile_mesh("geran4", data, False, 2.9)
    (root / "mesh/geran4.milmesh").write_bytes(mesh)
    (root / "textures/entity/geran4.png").write_bytes(png)
    provenance = root / "model-provenance.json"
    metadata = json.loads(provenance.read_text(encoding="utf-8")) if provenance.exists() else {}
    metadata["geran4"] = {
        "source": "User-supplied Geran-4 geometry and texture",
        "geometry": "tools/model_sources/geran4.geo.json",
        "texture": "tools/model_sources/geran4.png",
        "generator": "tools/geran4_assets.py",
        "dimensions_blocks": dimensions,
    }
    provenance.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    write_geran4(ASSETS)
