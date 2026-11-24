"""Build MilMod's Orlan-10 and KAB visuals from the supplied game references."""
import json
from pathlib import Path

from PIL import Image

from import_models import FACES, compile_mesh, cube_faces
from forpost_visuals import write_forpost, write_kab


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"
ORLAN_COLORS = (
    (228, 234, 234), (214, 221, 223), (199, 208, 211), (181, 193, 200),
    (241, 244, 242), (219, 225, 230), (171, 183, 186), (93, 106, 113),
    (43, 49, 55), (130, 155, 167), (71, 78, 86), (210, 217, 217),
)


def palette(path, colors, swatch, side):
    texture = Image.new("RGB", (side, 16), colors[0])
    for i, color in enumerate(colors):
        texture.paste(color, (i * swatch, 0, (i + 1) * swatch, 16))
    texture.save(path)


def surface(upper, lower, top, bottom, sides):
    """Connect two equal-length rims, both ordered with an up-facing normal."""
    n = len(upper)
    yield upper, top
    yield list(reversed(lower)), bottom
    for i in range(n):
        j = (i + 1) % n
        yield [upper[j], upper[i], lower[i], lower[j]], sides


def cuboid(low, high, shade):
    for face in cube_faces(low, high, {f: ((0, 0, 1, 1), 0) for f in FACES}, ()):
        yield [point for point, _, _ in face], shade


def airfoil(sign, start, end, front, back, y, tip_front, top, edge):
    rim = [(sign * start, y, front), (sign * start, y, back),
           (sign * end, y, back), (sign * end, y, tip_front)]
    if sign < 0:
        rim.reverse()
    low = [(x, yy - (0.27 if abs(x) > 35 else 0.65), z) for x, yy, z in rim]
    return surface(rim, low, top, 2, edge)


def fuselage():
    # z, half width, top, bottom. Proportions follow the top-view image.
    stations = [
        (-31.7, 0.67, 1.85, 1.22), (-30.65, 1.23, 2.64, 0.90),
        (-29.65, 2.04, 3.12, 0.70), (-17.9, 2.15, 3.27, 0.35),
        (-8.5, 2.14, 3.22, 0.40), (0.8, 2.07, 2.90, 0.60),
        (5.8, 1.73, 2.54, 0.83), (15.7, 1.09, 2.12, 0.98),
        (28.8, 0.94, 1.89, 1.06), (31.7, 0.82, 1.71, 1.10),
    ]
    rings = []
    for z, w, high, low in stations:
        rings.append([(-w * 0.68, high, z), (w * 0.68, high, z),
                      (w, high - 0.43, z), (w, low + 0.34, z),
                      (w * 0.68, low, z), (-w * 0.68, low, z),
                      (-w, low + 0.34, z), (-w, high - 0.43, z)])
    yield rings[0], 3
    yield list(reversed(rings[-1])), 2
    for previous, current in zip(rings, rings[1:]):
        for i, shade in enumerate((0, 4, 1, 2, 3, 2, 1, 4)):
            j = (i + 1) % 8
            yield [previous[i], current[i], current[j], previous[j]], shade
    yield from cuboid((-0.74, 0, -22), (0.74, 0.87, -20.4), 2)
    yield from cuboid((-0.58, 0.12, -22.07), (0.58, 0.62, -21.86), 7)


def orlan_geometry():
    body = list(fuselage())
    propeller = list(cuboid((-10.05, 1.28, -31.34), (10.05, 1.68, -30.12), 8))
    tail = []
    for sign in (-1, 1):
        tail.extend(airfoil(sign, 0.82, 11.45, 19.52, 23.37, 2.0, 19.52, 0, 2))
        tail.extend(airfoil(sign, 11.45, 14.8, 19.52, 23.37, 2.0, 19.63, 5, 2))
        tail.extend(airfoil(sign, 0.82, 11.45, 23.46, 26.75, 1.93, 23.46, 2, 3))
        tail.extend(airfoil(sign, 11.45, 14.8, 23.46, 26.75, 1.93, 23.46, 4, 3))
    fin = [(15.3, 1.83), (19.8, 5.04), (26.1, 5.04), (28.7, 1.83)]
    tail.extend(surface([(0.34, y, z) for z, y in fin],
                        [(-0.34, y, z) for z, y in fin], 1, 0, 2))

    groups = {"body": (body, [0, 0, 0]), "tail": (tail, [0, 0, 0]),
              "propeller": (propeller, [0, 1.48, -30.73])}
    for sign, name in [(-1, "wing_l"), (1, "wing_r")]:
        panels = []
        for start, end, front_outer, front_inner, shade in [
            (2.0, 35.55, -17.98, -17.98, 0),
            (35.55, 37.0, -17.98, -17.98, 5),
            (37.0, 40.15, -12.10, -17.98, 4),
        ]:
            panels.extend(airfoil(sign, start, end, front_inner, -12.04, 3.14, front_outer, shade, 2))
        for start, end, shade in [(2.0, 5.8, 1), (5.85, 35.55, 5), (35.61, 40.15, 4)]:
            panels.extend(airfoil(sign, start, end, -11.90, -8.66, 3.04, -11.90, shade, 3))
        groups[name] = (panels, [0, 0, 0])

    elements = []
    outliner = []
    for name, (faces, pivot) in groups.items():
        ids = []
        for i, (points, shade) in enumerate(faces):
            key = f"{name}_{i}"
            names = [str(index) for index in range(len(points))]
            ids.append(key)
            elements.append({
                "uuid": key, "type": "mesh", "origin": [0, 0, 0],
                "vertices": dict(zip(names, points)),
                "faces": {"0": {"vertices": names, "uv": dict.fromkeys(names, [shade * 4 + 2, 8])}},
            })
        outliner.append({"name": name, "origin": pivot, "children": ids})
    return {"resolution": {"width": 64, "height": 16}, "elements": elements, "outliner": outliner}


def make_models(root):
    mesh, _ = compile_mesh("orlan_10", orlan_geometry(), True, 2.2)
    (root / "mesh/orlan_10.milmesh").write_bytes(mesh)
    palette(root / "textures/entity/orlan_10.png", ORLAN_COLORS, 4, 64)

    write_forpost(root)
    write_kab(root)


if __name__ == "__main__":
    make_models(ROOT)
