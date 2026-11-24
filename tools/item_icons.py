"""Render 64 px inventory sprites from the existing MilMod models."""
import argparse
import json
import math
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from import_models import cube_faces
from forpost_visuals import kab_faces, quads


ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"
ITEM_MODELS = ASSETS / "models/item"
ITEM_TEXTURES = ASSETS / "textures/item"
SIZE = 64
SUPERSAMPLING = 3
VOLUME_ITEMS = ("controller", "training_capsule")
VIEW = {
    "controller": (16, 19),
    "training_capsule": (30, 23),
    "training_kab": (147, 32),
    "forpost_r": (145, 52),
    "orlan_10": (148, 57),
    "mavic3t": (32, 55),
    "beacon_marker": (32, 32),
    "fp1_ramp": (43, 38),
    "orlan_rail": (-48, 33),
    "launcher": (42, 42),
}
AIRCRAFT = {
    "forpost_r", "fp1", "geran4", "l39_albatros", "lyutyi", "mohajer_6",
    "orlan_10", "shahed_238",
}


def mesh_faces(path):
    data = path.read_bytes()
    magic, count = struct.unpack_from(">4sH", data)
    if magic != b"MIL1":
        raise ValueError(f"Unknown mesh format in {path}")
    offset = 6
    groups = []
    for _ in range(count):
        *_, vertices = struct.unpack_from(">BB3fI", data, offset)
        offset += 18
        group = np.frombuffer(data, dtype=">f4", count=vertices * 8, offset=offset)
        groups.append(group.reshape(-1, 4, 8))
        offset += vertices * 32
    if offset != len(data):
        raise ValueError(f"Mesh size mismatch in {path}")
    return np.concatenate(groups)


def element_faces(model):
    by_texture = {}
    for element in model["elements"]:
        rotation = element.get("rotation")
        transforms = ()
        if rotation:
            if rotation.get("rescale"):
                raise ValueError("Rescaled item elements are unsupported")
            angles = [rotation["angle"] if axis == rotation["axis"] else 0 for axis in "xyz"]
            transforms = ((angles, rotation["origin"]),)
        for direction, options in element["faces"].items():
            material = model["textures"][options["texture"].removeprefix("#")]
            face, = cube_faces(
                element["from"], element["to"],
                {direction: (options["uv"], options.get("rotation", 0))}, transforms,
            )
            xyz = np.array([vertex[0] for vertex in face], dtype=float)
            uv = np.array([vertex[1:] for vertex in face], dtype=float) / 16
            normal = np.cross(xyz[1] - xyz[0], xyz[2] - xyz[0])
            normal /= np.linalg.norm(normal)
            by_texture.setdefault(material, []).append(
                np.column_stack((xyz, uv, np.broadcast_to(normal, (4, 3))))
            )
    return [(ASSETS / "textures" / (material.split(":")[1] + ".png"), np.array(faces))
            for material, faces in by_texture.items()]


def kab_item_faces():
    faces = []
    for vertices, shade in quads(kab_faces()):
        xyz = np.asarray(vertices, dtype=float) / 16
        direction = np.cross(xyz[1] - xyz[0], xyz[2] - xyz[0])
        direction /= np.linalg.norm(direction)
        uv = np.broadcast_to(((shade + .5) / 16, .5), (4, 2))
        faces.append(np.column_stack((xyz, uv, np.broadcast_to(direction, (4, 3)))))
    return [(ITEM_TEXTURES / "kab_materials.png", np.asarray(faces))]


def draw_triangle(canvas, depth, texture, xy, z, uv, light):
    side = canvas.shape[0]
    left, top = np.maximum(0, np.floor(xy.min(axis=0)).astype(int))
    right, bottom = np.minimum(side, np.ceil(xy.max(axis=0)).astype(int))
    if left >= right or top >= bottom:
        return
    a, b = xy[1:] - xy[0]
    determinant = a[0] * b[1] - a[1] * b[0]
    if abs(determinant) < 1e-9:
        return
    y, x = np.ogrid[top:bottom, left:right]
    x = x + 0.5 - xy[0, 0]
    y = y + 0.5 - xy[0, 1]
    second = (x * b[1] - y * b[0]) / determinant
    third = (a[0] * y - a[1] * x) / determinant
    first = 1 - second - third
    distance = first * z[0] + second * z[1] + third * z[2]
    depth_region = depth[top:bottom, left:right]
    mask = (first >= -1e-7) & (second >= -1e-7) & (third >= -1e-7) & (distance > depth_region)
    if not mask.any():
        return
    coordinates = first[mask, None] * uv[0] + second[mask, None] * uv[1] + third[mask, None] * uv[2]
    height, width = texture.shape[:2]
    tex_x = np.clip((coordinates[:, 0] * width).astype(int), 0, width - 1)
    tex_y = np.clip((coordinates[:, 1] * height).astype(int), 0, height - 1)
    pixels = texture[tex_y, tex_x].copy()
    opaque = pixels[:, 3] >= 110
    pixels[:, :3] = np.clip(pixels[:, :3] * light + 9, 0, 255)
    mask[mask] = opaque
    canvas[top:bottom, left:right][mask] = pixels[opaque]
    depth_region[mask] = distance[mask]


def render(groups, angles):
    yaw, pitch = map(math.radians, angles)
    right = np.array([math.cos(yaw), 0, -math.sin(yaw)])
    up = np.array([-math.sin(yaw) * math.sin(pitch), math.cos(pitch), -math.cos(yaw) * math.sin(pitch)])
    toward_viewer = np.cross(right, up)
    points = np.concatenate([quads[:, :, :3].reshape(-1, 3) for _, quads in groups])
    projection = points @ np.array([right, -up]).T
    lower, upper = projection.min(axis=0), projection.max(axis=0)
    side = SIZE * SUPERSAMPLING
    center = (upper + lower) / 2
    scale = (side - 10 * SUPERSAMPLING) / max(upper - lower)
    canvas = np.zeros((side, side, 4), dtype=np.uint8)
    depth = np.full((side, side), -np.inf)
    principal_light = -right * 0.36 + up * 0.80 + toward_viewer * 0.48

    for texture_path, quads in groups:
        with Image.open(texture_path) as source:
            texture = np.asarray(source.convert("RGBA"))
        vertices = quads[:, :, :3]
        xy = (vertices @ np.array([right, -up]).T - center) * scale + side / 2
        z = vertices @ toward_viewer
        normals = quads[:, 0, 5:8]
        lighting = 0.75 + 0.36 * np.maximum(0, normals @ principal_light)
        for index in range(len(quads)):
            uv = quads[index, :, 3:5]
            for triangle in ([0, 1, 2], [0, 2, 3]):
                draw_triangle(canvas, depth, texture, xy[index, triangle], z[index, triangle],
                              uv[triangle], lighting[index])
    # Lift dark surfaces so small sprites retain readable details against a dark UI.
    canvas[:, :, :3] = np.around(255 * (canvas[:, :, :3] / 255) ** 0.83).astype(np.uint8)
    return Image.fromarray(canvas).resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def write_json(path, obj):
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def flat_model(item):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"milmod:item/{item}"}}


def generate(selected=None):
    icons = {}
    for mesh in sorted((ASSETS / "mesh").glob("*.milmesh")):
        item = mesh.stem
        if selected and item not in selected:
            continue
        texture = ASSETS / "textures/entity" / f"{item}.png"
        angles = VIEW.get(item, (39, 50) if item in AIRCRAFT else (31, 30))
        icons[item] = render([(texture, mesh_faces(mesh))], angles)
        write_json(ITEM_MODELS / f"{item}.json", flat_model(item))

    for item in VOLUME_ITEMS:
        if selected and item not in selected:
            continue
        model_path = ITEM_MODELS / f"{item}.json"
        body_path = ITEM_MODELS / f"{item}_body.json"
        model = json.loads(model_path.read_text(encoding="utf-8"))
        if model.get("loader") == "forge:separate_transforms":
            model = json.loads(body_path.read_text(encoding="utf-8"))
        else:
            body_path.write_bytes(model_path.read_bytes())
        icons[item] = render(element_faces(model), VIEW[item])
        write_json(model_path, {
            "loader": "forge:separate_transforms",
            "base": {"parent": f"milmod:item/{item}_body"},
            "textures": {"particle": model["textures"]["particle"]},
            "perspectives": {
                "gui": {"loader": "forge:item_layers", **flat_model(item)},
            },
        })

    if not selected or "training_kab" in selected:
        icons["training_kab"] = render(kab_item_faces(), VIEW["training_kab"])

    for item, icon in icons.items():
        if icon.getbbox() is None:
            raise ValueError(f"Empty icon: {item}")
        icon.save(ITEM_TEXTURES / f"{item}.png")
    return icons


def preview(icons, path):
    width, height, columns = 135, 104, 6
    sheet = Image.new("RGB", (width * columns, height * math.ceil(len(icons) / columns)), "#292c30")
    labels = ImageDraw.Draw(sheet)
    for i, (item, icon) in enumerate(sorted(icons.items())):
        x, y = i % columns * width, i // columns * height
        sheet.paste(icon, (x + 18, y + 5), icon)
        small = icon.resize((16, 16), Image.Resampling.LANCZOS)
        sheet.paste(small, (x + 93, y + 28), small)
        labels.text((x + 13, y + 77), item, fill="#ebedf0")
    sheet.save(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--item", action="append")
    options = parser.parse_args()
    icons = generate(options.item)
    if options.preview:
        preview(icons, options.preview)
    print(f"Rendered {len(icons)} inventory icons")
