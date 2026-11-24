"""Convert the user's archived visual models to MilMod's client-only mesh format."""
import argparse
import base64
from collections import defaultdict
import json
from math import cos, radians, sin, sqrt
from pathlib import Path
import struct
import zipfile

SELECTION = {
    "buk_m3": ("BUK_M3_Java_GeckoLib.zip", "/geo/buk_m3.geo.json", "/textures/entity/buk_m3.png", 6.9),
    "shahed_238": ("Shahed_238_Blockbench.zip", "/shahed_238.geo.json", "/shahed_238.png", 2.5),
    "m113": ("M113_Desert.bbmodel", "/M113_Desert.bbmodel", None, 4.3),
    "tor_m1": ("Airfield_1.6.0.zip", "/tor_m1.geo.json", "/tor_m1.png", 5.8),
    "iskander_k": ("Iskander_K(1).bbmodel", "/Iskander_K(1).bbmodel", None, 7.5),
    "beacon_marker": ("Air_PDA_Bedrock_v1.1.0.mcaddon", "/beacon_marker.geo.json", "/textures/entity/airpda_beacon_marker.png", 0.55),
    "fp1": ("FP1_Bedrock_Models.zip", "/fp1.geo.json", "/fp1.png", 2.8),
    "launcher": ("Air_PDA_Bedrock_v1.1.0.mcaddon", "/launcher.geo.json", "/textures/entity/airpda_launcher.png", 2.2),
    "fp1_ramp": ("FP1_Bedrock_Models.zip", "/fp1_ramp.geo.json", "/fp1_ramp.png", 2.8),
    "mavic3t": ("Mavic3T_Bedrock_Model.zip", "/mavic3t.geo.json", "/mavic3t.png", 0.85),
    "gaz_tigr": ("Vehicle_Models_Bedrock.zip", "/gaz_tigr.geo.json", "/gaz_tigr.png", 4.0),
    "l39_albatros": ("Vehicle_Models_Bedrock.zip", "/l39_albatros.geo.json", "/l39_albatros.png", 5.3),
    "mohajer_6": ("Vehicle_Models_Bedrock.zip", "/mohajer_6.geo.json", "/mohajer_6.png", 3.5),
    "t64bv": ("Vehicle_Models_Bedrock.zip", "/t64bv.geo.json", "/t64bv.png", 6.0),
}

FACES = {
    "north": (1, 0, 2, 3), "south": (4, 5, 7, 6),
    "west": (0, 4, 6, 2), "east": (5, 1, 3, 7),
    "down": (0, 1, 5, 4), "up": (6, 7, 3, 2),
}


def rotate(point, pivot, angles):
    if not any(angles):
        return tuple(point)
    x, y, z = (v - c for v, c in zip(point, pivot))
    rx, ry, rz = map(radians, angles)
    # Blockbench's Euler XYZ matrix multiplies a vertex right to left.
    x, y = x * cos(rz) - y * sin(rz), x * sin(rz) + y * cos(rz)
    x, z = x * cos(ry) + z * sin(ry), -x * sin(ry) + z * cos(ry)
    y, z = y * cos(rx) - z * sin(rx), y * sin(rx) + z * cos(rx)
    return tuple(v + c for v, c in zip((x, y, z), pivot))


def box_uv(uv, size):
    u, v = uv
    x, y, z = map(abs, size)
    return {
        "west": (u, v + z, u + z, v + z + y),
        "north": (u + z, v + z, u + z + x, v + z + y),
        "east": (u + z + x, v + z, u + 2 * z + x, v + z + y),
        "south": (u + 2 * z + x, v + z, u + 2 * z + 2 * x, v + z + y),
        "up": (u + z, v, u + z + x, v + z),
        "down": (u + z + x, v, u + z + 2 * x, v + z),
    }


def cube_faces(a, b, face_uv, transforms):
    x1, y1, z1 = a
    x2, y2, z2 = b
    corners = [(x1, y1, z1), (x2, y1, z1), (x1, y2, z1), (x2, y2, z1),
               (x1, y1, z2), (x2, y1, z2), (x1, y2, z2), (x2, y2, z2)]
    for rot, pivot in transforms:
        corners = [rotate(point, pivot, rot) for point in corners]
    for direction, info in face_uv.items():
        if direction not in FACES:
            continue
        (u1, v1, u2, v2), angle = info
        uv = [(u1, v2), (u2, v2), (u2, v1), (u1, v1)]
        shift = round(angle / 90) % 4
        uv = uv[shift:] + uv[:shift]
        yield tuple((corners[index], *tex) for index, tex in zip(FACES[direction], uv))


def geo_uv(cube):
    uv = cube.get("uv", [0, 0])
    if isinstance(uv, list):
        return {direction: (rect, 0) for direction, rect in box_uv(uv, cube["size"]).items()}
    output = {}
    for face, value in uv.items():
        u, v = value["uv"]
        w, h = value.get("uv_size", [1, 1])
        output[face] = ((u, v, u + w, v + h), value.get("uv_rotation", 0))
    return output


def animation(model, name, parent, center):
    low = name.lower()
    is_air = model in {"shahed_238", "fp1", "geran4", "mavic3t", "forpost_r", "l39_albatros", "mohajer_6", "orlan_10", "lyutyi"}
    part = parent[0] if parent else 0
    if is_air:
        if low in {"wings", "02_wings", "ailerons"}:
            part = 1 if center < 0 else 2
        elif any(k in low for k in ("wing", "fin", "arm", "rotor", "flap")):
            if "left" in low or low.endswith("_l"):
                part = 1
            elif "right" in low or low.endswith("_r"):
                part = 2
    if "rotor" in low:
        return part, 1 if part != 2 else 2
    if model == "mohajer_6" and low.startswith("gear_"):
        return part, 17
    if "propeller" in low:
        return part, 3
    if model == "tor_m1" and low == "antenna":
        return part, 4
    if model == "buk_m3" and low == "radar":
        return part, 4
    if model in {"tor_m1", "gaz_tigr", "t64bv", "iskander_k", "forpost_r", "mohajer_6", "orlan_10", "lyutyi"} and "wheel" in low and not "wheels" in low:
        return part, 5
    if model == "m113" and "rear ramp" in low:
        return part, 6
    if model == "m113" and "cargo hatch" in low or model == "l39_albatros" and low == "canopy":
        return part, 7
    if model == "iskander_k" and low.startswith("rear_door"):
        return part, 8 if low.endswith("_l") else 9
    if model == "iskander_k" and low.startswith("roof_hatch"):
        return part, 10 if low.endswith("_l") else 11
    if model == "iskander_k":
        if low.startswith("missile_"):
            return part, 15 if low.endswith("_l") else 16
        if low == "twin_erector" or low.startswith("canister_"):
            return part, 14
    if model == "fp1_ramp" and low == "cradle" or model == "launcher" and low == "body":
        return part, 12
    return part, 0


def geo_groups(model, data):
    geo = data["minecraft:geometry"][0]
    bones = {bone["name"]: bone for bone in geo["bones"]}

    def bone_class(name, center):
        bone = bones[name]
        parent = bone.get("parent")
        return animation(model, name, bone_class(parent, center) if parent in bones else None, center)

    for name, bone in bones.items():
        pivot = bone.get("pivot", [0, 0, 0])
        chain = []
        ancestor = bone
        while ancestor:
            if ancestor.get("rotation"):
                chain.append((ancestor["rotation"], ancestor.get("pivot", [0, 0, 0])))
            ancestor = bones.get(ancestor.get("parent"))
        for cube in bone.get("cubes", []):
            start = cube["origin"]
            end = [coord + length for coord, length in zip(start, cube["size"])]
            center = sum((start[0], end[0])) * 0.5
            inflate = cube.get("inflate", bone.get("inflate", 0))
            a = [value - inflate for value in start]
            b = [value + inflate for value in end]
            transform = [(cube.get("rotation", [0, 0, 0]), cube.get("pivot", pivot)), *chain]
            damage, action = bone_class(name, center)
            animated_pivot = tuple(pivot)
            for angles, anchor in chain:
                animated_pivot = rotate(animated_pivot, anchor, angles)
            key = (name, damage, action, animated_pivot)
            yield key, cube_faces(a, b, geo_uv(cube), transform)


def bb_groups(model, data):
    elements = {element["uuid"]: element for element in data["elements"]}
    groups = {group["uuid"]: group for group in data.get("groups", [])}

    def walk(children, name="root", pivot=(0, 0, 0), chain=()):
        for item in children:
            if isinstance(item, dict):
                group = groups.get(item.get("uuid"), item)
                group_name = group.get("name", name)
                origin = group.get("origin", pivot)
                transform = (group.get("rotation", [0, 0, 0]), origin)
                yield from walk(item.get("children", []), group_name, origin, (transform, *chain))
                continue
            if item not in elements:
                continue
            elem = elements[item]
            if not elem.get("export", True) or not elem.get("visibility", True) or "pixel_flare" in name:
                continue
            part, action = animation(model, name, None, 0)
            animated_pivot = tuple(pivot)
            for angles, anchor in chain:
                animated_pivot = rotate(animated_pivot, anchor, angles)
            key = (name, part, action, animated_pivot)
            origin = elem.get("origin", [0, 0, 0])
            transforms = [(elem.get("rotation", [0, 0, 0]), origin), *chain]
            if elem.get("type") == "mesh":
                def point(vertex):
                    coords = tuple(v + c for v, c in zip(elem["vertices"][vertex], origin))
                    for angles, center in transforms:
                        coords = rotate(coords, center, angles)
                    return coords
                polygons = []
                for face in elem["faces"].values():
                    vertices = [(point(v), *face["uv"][v]) for v in face["vertices"]]
                    if len(vertices) == 4:
                        polygons.append(tuple(vertices))
                    elif len(vertices) >= 3:
                        polygons.extend((vertices[0], vertices[i], vertices[i + 1], vertices[i + 1])
                                        for i in range(1, len(vertices) - 1))
                yield key, polygons
            else:
                uv = {direction: (face["uv"], face.get("rotation", 0))
                      for direction, face in elem["faces"].items() if "uv" in face and face.get("texture") is not None}
                yield key, cube_faces(elem["from"], elem["to"], uv, transforms)
    yield from walk(data["outliner"])


def normal(face):
    p, q, r = (vertex[0] for vertex in face[:3])
    ax, ay, az = (v - c for v, c in zip(q, p))
    bx, by, bz = (v - c for v, c in zip(r, p))
    direction = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    length = sqrt(sum(value * value for value in direction))
    if length < 0.00001:
        return None
    return tuple(value / length for value in direction)


def texture_dimensions(data, is_bb):
    if is_bb:
        return data["resolution"]["width"], data["resolution"]["height"]
    description = data["minecraft:geometry"][0]["description"]
    return description["texture_width"], description["texture_height"]


def compile_mesh(model, data, is_bb, length, additional=()):
    groups = defaultdict(list)
    for key, source_faces in (bb_groups(model, data) if is_bb else geo_groups(model, data)):
        groups[key].extend(face for face in source_faces if normal(face))
    for key, source_faces in additional:
        groups[key].extend(face for face in source_faces if normal(face))
    all_points = [point for faces in groups.values() for face in faces for point, _, _ in face]
    bounds = [(min(axis), max(axis)) for axis in zip(*all_points)]
    center = (sum(bounds[0]) * 0.5, bounds[1][0], sum(bounds[2]) * 0.5)
    zoom = length / (bounds[2][1] - bounds[2][0])
    width, height = texture_dimensions(data, is_bb)

    def convert(point):
        return tuple((v - origin) * zoom for v, origin in zip(point, center))

    result = bytearray(struct.pack(">IH", 0x4D494C31, len(groups)))
    for (_, part, action, pivot), faces in groups.items():
        result.extend(struct.pack(">BB3fI", part, action, *convert(pivot), len(faces) * 4))
        for face in faces:
            nx, ny, nz = normal(face)
            for point, u, v in face:
                result.extend(struct.pack(">8f", *convert(point), u / width, v / height, nx, ny, nz))
    dimensions = [round((hi - lo) * zoom, 3) for lo, hi in bounds]
    return result, dimensions


def find(entries, source, path):
    return next(entry for entry in entries if entry["source"] == source
                and ("/" + entry["source_path"]).endswith(path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    arguments = parser.parse_args()
    provenance = arguments.output / "model-provenance.json"
    metadata = json.loads(provenance.read_text()) if provenance.exists() else {}
    assets = arguments.output
    (assets / "mesh").mkdir(parents=True, exist_ok=True)
    (assets / "textures/entity").mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(arguments.archive) as archive:
        records = json.loads(archive.read("catalog.json"))["files"]
        for model, (source, geo_path, texture_path, length) in SELECTION.items():
            geometry = find(records, source, geo_path)["path"]
            data = json.loads(archive.read(geometry))
            is_bb = geometry.endswith(".bbmodel")
            if texture_path:
                texture_entry = find(records, source, texture_path)["path"]
                texture = archive.read(texture_entry)
            else:
                encoded = data["textures"][0]["source"]
                texture = base64.b64decode(encoded.split(",", 1)[1], validate=True)
                texture_entry = geometry + "#embedded"
            mesh, dimensions = compile_mesh(model, data, is_bb, length)
            (assets / "mesh" / (model + ".milmesh")).write_bytes(mesh)
            (assets / "textures/entity" / (model + ".png")).write_bytes(texture)
            metadata[model] = {"source": source, "geometry": geometry, "texture": texture_entry,
                               "dimensions_blocks": dimensions}
            print(model, *dimensions, len(mesh))
    (assets / "model-provenance.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
