"""Generate the clean Forpost-R mesh and its tapered game item from visual references."""
import json
import struct
from math import cos, pi, sin

from PIL import Image, ImageDraw

from import_models import FACES, cube_faces, normal


AIRFRAME_COLORS = (
    (221, 228, 231), (190, 201, 208), (165, 179, 189),
    (234, 239, 240), (154, 171, 180), (79, 100, 111),
    (45, 52, 61), (118, 137, 151), (128, 158, 175),
    (102, 124, 137), (173, 188, 197), (216, 225, 229),
)
KAB_COLORS = ((226, 232, 237), (196, 209, 218), (178, 192, 205),
              (242, 247, 249), (145, 182, 200), (174, 208, 224), (121, 157, 178))


def prism(low, high, shade, transform=()):
    uv = {side: ((0, 0, 0, 0), 0) for side in FACES}
    return [([point for point, _, _ in quad], shade)
            for quad in cube_faces(low, high, uv, transform)]


def octagon(z, half_width, low, high, bevel):
    return [(-half_width + bevel, high, z), (half_width - bevel, high, z),
            (half_width, high - bevel, z), (half_width, low + bevel, z),
            (half_width - bevel, low, z), (-half_width + bevel, low, z),
            (-half_width, low + bevel, z), (-half_width, high - bevel, z)]


def loft(rings, shades, close=True):
    if close:
        yield rings[0], shades[0]
        yield list(reversed(rings[-1])), shades[-1]
    for before, after in zip(rings, rings[1:]):
        for index, shade in enumerate(shades):
            next_index = (index + 1) % len(shades)
            yield [before[index], after[index], after[next_index], before[next_index]], shade


def wing(sign):
    def profile(x, z, chord, y, height):
        points = [(sign*x, y + height*.24, z), (sign*x, y + height*.68, z + chord*.14),
                  (sign*x, y + height*.64, z + chord*.62), (sign*x, y + height*.06, z + chord),
                  (sign*x, y, z + chord*.88), (sign*x, y - height*.32, z + chord*.30)]
        return points if sign > 0 else list(reversed(points))

    sections = [profile(*spec) for spec in (
        (.16, -.50, .58, .756, .085), (.64, -.48, .56, .756, .077),
        (1.40, -.425, .50, .764, .064), (2.07, -.365, .39, .778, .045),
        (2.15, -.323, .32, .78, .040))]
    shades = (3, 0, 10, 2, 1, 4)
    if sign < 0:
        shades = tuple(reversed(shades[:-1])) + (shades[-1],)
    return [(list(reversed(points)), shade) for points, shade in loft(sections, shades)]


def tire(cx, cy, cz, width, radius, shade=6):
    def rim(x):
        return [(x, cy + radius*cos(pi*(index+.5)/4),
                 cz + radius*sin(pi*(index+.5)/4)) for index in range(8)]
    return [(list(reversed(points)), shade) for points, shade in
            loft([rim(cx - width / 2), rim(cx + width / 2)], (shade,) * 8)]


def split_at_hinge(faces, hinge):
    def clip(vertices, behind):
        result = []
        def keep(v):
            return v[2] >= hinge if behind else v[2] <= hinge
        for before, after in zip(vertices[-1:] + vertices[:-1], vertices):
            if keep(before) != keep(after):
                ratio = (hinge - before[2]) / (after[2] - before[2])
                intersection = tuple(a + (b - a) * ratio for a, b in zip(before, after))
                if not result or result[-1] != intersection:
                    result.append(intersection)
            if keep(after) and (not result or result[-1] != after):
                result.append(after)
        if len(result) > 1 and result[0] == result[-1]:
            result.pop()
        return result

    front, rear = [], []
    for vertices, shade in faces:
        for destination, behind in ((front, False), (rear, True)):
            clipped = clip(vertices, behind)
            if len(clipped) >= 3:
                destination.append((clipped, shade))
    return front, rear


def forpost_groups():
    body = list(loft([octagon(*station) for station in (
        (-1.90, .067, .27, .41, .032), (-1.76, .119, .25, .51, .04),
        (-1.37, .196, .235, .69, .075), (-.91, .237, .218, .77, .096),
        (-.31, .252, .211, .78, .105), (.19, .253, .22, .83, .104),
        (.32, .247, .239, .95, .10), (.65, .218, .28, .952, .087),
        (.98, .162, .42, .858, .069), (1.105, .091, .56, .786, .042),
    )], (0, 3, 1, 2, 4, 2, 1, 3)))

    body += prism((-.049, .681, -1.17), (.049, .940, -1.069), 7)
    antenna = [(-.058, .907, -1.18), (.058, .907, -1.18), (.058, .907, -1.06), (-.058, .907, -1.06)]
    antenna_cap = [(-.111, .997, -1.224), (.111, .997, -1.224), (.111, .997, -1.016), (-.111, .997, -1.016)]
    body += list(loft([antenna, antenna_cap], (1,) * 4))
    body += list(loft([octagon(*station) for station in (
        (-.89, .066, .155, .232, .022), (-.76, .089, .124, .235, .025),
        (-.64, .065, .134, .229, .025),
    )], (1, 10, 2, 7, 5, 7, 2, 10)))
    groups = [(0, 0, (0, 0, 0), body)]

    for sign in (-1, 1):
        x = sign * .665
        part = 1 if sign < 0 else 2
        assembly, aileron = split_at_hinge(wing(sign), -.025)
        groups.append((part, 18 if sign < 0 else 19, (0, .78, -.025), aileron))
        boom = list(loft([
            [(px + x, py, pz) for px, py, pz in octagon(*station)]
            for station in ((-.096, .052, .749, .821, .014),
                            (.43, .045, .751, .807, .013),
                            (1.787, .026, .758, .799, .009))], (0, 3, 1, 2, 4, 2, 1, 3)))
        assembly += boom
        # H-tail fins, attached directly to both booms and the crosspiece.
        fin = [(1.52,.71), (1.51,.887), (1.57,1.077), (1.78,1.077), (1.90,.965), (1.90,.687)]
        rings = [[(x+dx,y,z) for z,y in fin] for dx in (-.024,.024)]
        fin_faces = [(list(reversed(points)), shade) for points, shade in loft(rings, (0,3,0,1,4,2))]
        fixed_fin, rudder = split_at_hinge(fin_faces, 1.77)
        assembly += fixed_fin
        groups.append((part, 21, (x, .87, 1.77), rudder))
        # Permanent visual mount meets both the wing skin and the detachable game item.
        mount = [(px + sign, py, pz) for px, py, pz in octagon(-.402,.061,.653,.759,.015)]
        mount_back = [(px + sign, py, pz) for px, py, pz in octagon(-.066,.061,.653,.759,.015)]
        assembly += list(loft([mount, mount_back], (1, 1, 10, 2, 5, 7, 10, 1)))
        groups.append((part, 0, (sign * .665, .77, -.25), assembly))

    crosspiece = list(loft([
        [(x,y,z) for y,z in ((.809,1.585),(.818,1.645),(.807,1.815),
                           (.768,1.846),(.756,1.78),(.769,1.626))]
        for x in (-.672,.672)], (3, 0, 10, 2, 1, 4)))
    tail, elevator = split_at_hinge([(list(reversed(points)), shade) for points, shade in crosspiece], 1.755)
    groups.append((0, 0, (0, 0, 0), tail))
    groups.append((0, 20, (0, .788, 1.755), elevator))

    motor = prism((-.062,.618,1.091),(.062,.744,1.174), 9)
    prop_center = (0, .681, 1.174)
    for angle in (16, 136, 256):
        motor += prism((-.025,.694,1.157),(.032,1.096,1.186), 8,
                       (([0, 0, angle], prop_center),))
    groups.append((0, 3, prop_center, motor))

    for x, y, z, radius in [(0,.091,-1.575,.095), (-.296,.104,.125,.105), (.296,.104,.125,.105)]:
        attachment_x = x*.64
        support = [
            [(xx + point_x, yy, zz) for point_x, zz, _ in octagon(0, .033, z-.032, z+.032,.012)]
            for xx, yy in [(x,y), (attachment_x,.282)]]
        groups.append((0, 17, (x, y, z), list(loft(support, (1,3,1,10,4,10,1,3)))))
        wheel = tire(x, y, z, .073, radius)
        for direction in (-1, 1):
            wheel.extend(tire(x + direction * .036, y, z, .008, radius * .53, shade=7))
        groups.append((0, 5, (x, y, z), wheel))
    return groups


def kab_faces():
    # One closed faceted shell. The blue portion has straight taper planes
    # running from the smaller tip to the full-width shoulder.
    rings = [octagon(z, half, 8-half, 8+half, bevel) for z, half, bevel in
             ((.28,.80,.28), (3.68,2.12,.70), (4.29,2.12,.70), (14.78,2.12,.70))]
    rings = [[(x+8,y,z) for x,y,z in ring] for ring in rings]
    faces = [(rings[0],4), (list(reversed(rings[-1])),1)]
    for start, end, shades in zip(rings, rings[1:], (
            (5,5,4,6,6,6,4,5), (3,3,3,3,3,3,3,3), (0,0,1,2,2,2,1,0))):
        faces.extend(loft([start,end], shades, close=False))
    for front, rear, span in ((5.22,6.62,3.65), (11.86,13.85,4.03)):
        for angle in (-45,45):
            # Retain only exposed fins, avoiding overlapping internal faces.
            for sign in (-1,1):
                if sign > 0:
                    low, high = [10.34,7.84,front], [8+span,8.16,rear]
                else:
                    low, high = [8-span,7.84,front], [5.66,8.16,rear]
                faces.extend(prism(low, high, 0, (([0,0,angle],[8,8,8]),)))
    return faces


def quads(faces):
    for vertices, shade in faces:
        if len(vertices) == 4:
            yield vertices, shade
        else:
            for i in range(1, len(vertices)-1):
                yield [vertices[0], vertices[i], vertices[i+1], vertices[i+1]], shade


def face_uv(index, shade, side):
    # Neutral panels and faint panel edges are painted onto the atlas.
    return (
        ((1 if index in (0,3) else 14) + (shade % 4) * 16) / side,
        ((14 if index in (0,1) else 1) + (shade // 4) * 16) / side)


def write_forpost(root):
    groups = forpost_groups()
    mesh = bytearray(struct.pack(">IH", 0x4D494C31, len(groups)))
    for damage, animation, center, faces in groups:
        converted = [(vertices, shade) for vertices, shade in quads(faces)
                     if normal([(point, 0, 0) for point in vertices]) is not None]
        mesh.extend(struct.pack(">BB3fI", damage, animation, *center, 4 * len(converted)))
        for vertices, shade in converted:
            direction = normal([(point, 0, 0) for point in vertices])
            if direction is None:
                raise ValueError("Degenerate Forpost face")
            for index, point in enumerate(vertices):
                mesh.extend(struct.pack(">8f", *point, *face_uv(index, shade, 64), *direction))
    (root / "mesh/forpost_r.milmesh").write_bytes(mesh)

    atlas = Image.new("RGB", (64,64), AIRFRAME_COLORS[0])
    draw = ImageDraw.Draw(atlas)
    for index, color in enumerate(AIRFRAME_COLORS):
        x, y = 16 * (index % 4), 16 * (index // 4)
        draw.rectangle((x,y,x+15,y+15), fill=color)
        if index not in (5,6,7,8,9):
            low = tuple(channel - 12 for channel in color)
            highlight = tuple(min(255, channel + 7) for channel in color)
            draw.line((x+2,y+2,x+13,y+2), fill=highlight)
            draw.line((x+2,y+12,x+13,y+12), fill=low)
            draw.rectangle((x+9,y+5,x+12,y+6), fill=low)
    atlas.save(root / "textures/entity/forpost_r.png")


def write_kab(root):
    models = root / "models/item"
    lines = ["mtllib training_kab.mtl", "o training_kab", "usemtl palette"]
    for shade in range(len(KAB_COLORS)):
        lines.append(f"vt {(shade+.5)/16:.7f} 0.5")
    vertex_offset = 1
    for vertices, shade in quads(kab_faces()):
        if vertices[-1] == vertices[-2]:
            vertices = vertices[:-1]
        for point in vertices:
            lines.append("v " + " ".join(f"{value/16:.7f}" for value in point))
        lines.append("f " + " ".join(f"{vertex_offset+i}/{shade+1}" for i in range(len(vertices))))
        vertex_offset += len(vertices)
    (models / "training_kab.obj").write_text("\n".join(lines)+"\n")
    (models / "training_kab.mtl").write_text("newmtl palette\nKd 1.0 1.0 1.0\nmap_Kd #color\n")

    texture = Image.new("RGB",(16,16),KAB_COLORS[0])
    draw = ImageDraw.Draw(texture)
    for index,color in enumerate(KAB_COLORS):
        draw.line((index,0,index,15),fill=color)
    texture.save(root / "textures/item/kab_materials.png")

    resource = "milmod:item/kab_materials"
    wrapper = {
        "loader": "forge:separate_transforms",
        "textures": {"particle": resource},
        "base": {
            "loader": "forge:obj",
            "model": "milmod:models/item/training_kab.obj",
            "automatic_culling": False,
            "textures": {"color": resource, "particle": resource},
            "display": {"ground": {"translation": [0,3,0], "scale": [.5]*3}},
        },
        "perspectives": {"gui": {
            "loader": "forge:item_layers", "parent": "minecraft:item/generated",
            "textures": {"layer0": "milmod:item/training_kab"},
        }},
    }
    (models / "training_kab.json").write_text(json.dumps(wrapper, indent=2)+"\n")
