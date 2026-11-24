"""Replace visibly uneven exterior panels in the supplied Minecraft meshes."""
import argparse
import math
from pathlib import Path
import struct

from PIL import Image

HEADER = struct.Struct(">BB3fI")
VERTEX = struct.Struct(">8f")


def read(path):
    data = path.read_bytes()
    magic, count = struct.unpack_from(">4sH", data)
    if magic != b"MIL1":
        raise ValueError(path)
    offset = 6
    groups = []
    for _ in range(count):
        part, action, x, y, z, vertices = HEADER.unpack_from(data, offset)
        offset += HEADER.size
        values = [VERTEX.unpack_from(data, offset + i * VERTEX.size) for i in range(vertices)]
        offset += vertices * VERTEX.size
        groups.append(((part, action, x, y, z), values))
    assert offset == len(data)
    return groups


def write(path, groups):
    data = bytearray(struct.pack(">4sH", b"MIL1", len(groups)))
    for header, vertices in groups:
        data.extend(HEADER.pack(*header, len(vertices)))
        for vertex in vertices:
            data.extend(VERTEX.pack(*vertex))
    path.write_bytes(data)


def texel(texture, color):
    pixels = texture.load()
    w, h = texture.size
    candidates = ((x, y) for y in range(h) for x in range(w) if pixels[x, y][3] > 240)
    x, y = min(candidates, key=lambda point: sum((a - b) ** 2 for a, b in zip(pixels[point][:3], color)))
    return ((x + .5) / w, (y + .5) / h)


def loft(rings, uv):
    def face(points, tex):
        p, q, r = points[:3]
        a = [x-y for x,y in zip(q,p)]
        b = [x-y for x,y in zip(r,p)]
        n = (a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0])
        length = math.sqrt(sum(x*x for x in n))
        if length > 1e-7:
            return [(*point, *tex, *(value/length for value in n)) for point in points]
        return []

    result = []
    for i, (before, after) in enumerate(zip(rings, rings[1:])):
        for j in range(len(before)):
            k = (j+1) % len(before)
            result.extend(face((before[j], before[k], after[k], after[j]), uv(i,j)))
    for ring in (list(reversed(rings[0])), rings[-1]):
        for k in range(1, len(ring)-1):
            result.extend(face((ring[0], ring[k], ring[k+1], ring[k+1]), uv(0,0)))
    return result


def shell(stations, texture, base, accent=None, collar=-1):
    rings = []
    for z, cx, cy, width, height in stations:
        rings.append([(cx+width*math.cos(math.pi*(i+.5)/4),
                       cy+height*math.sin(math.pi*(i+.5)/4), z) for i in range(8)])
    palette = [texel(texture,[max(0,c-d) for c in base]) for d in (0,8,16)]
    band = texel(texture,accent) if accent else palette[0]
    return loft(rings, lambda row, face: band if row == collar else palette[0 if face < 4 else 1])


def patch(model, root, original):
    groups = read(original / "mesh" / f"{model}.milmesh")
    with Image.open(root / "textures/entity" / f"{model}.png") as png:
        atlas = png.convert("RGBA")
    if model == "shahed_238":
        groups[6] = (groups[6][0], shell([
            (-1.25,0,.11,.050,.046),(-1.14,0,.112,.075,.061),(-1.10,0,.112,.083,.069),
            (-1.065,0,.112,.085,.068),(-.90,0,.11,.091,.071),(-.31,0,.11,.091,.071)],
            atlas,(57,65,67),(221,178,63),2))
        groups[7] = (groups[7][0], shell([
            (-.33,0,.11,.091,.071),(-.15,0,.11,.109,.091),(.62,0,.11,.111,.093),
            (1.07,0,.11,.087,.082),(1.25,0,.10,.072,.064)],atlas,(54,62,64)))
    elif model == "geran4":
        groups[5] = (groups[5][0], shell([
            (1.005,0,.206,.137,.108),(1.17,0,.207,.145,.113),
            (1.36,0,.21,.121,.090),(1.45,0,.21,.091,.071)], atlas,(184,193,203)))
    else:
        grey = [texel(atlas, (shade,shade,min(255,shade+6))) for shade in (225,210,188)]
        for group, side in ((2,1),(6,-1)):
            rings = []
            for x, front, back, y, height in ((.14,-.696,.19,.794,.064),
                                               (.77,-.67,.17,.797,.061),
                                               (2.28,-.58,.13,.800,.047),
                                               (2.87,-.53,.084,.806,.033),
                                               (2.89,-.49,.047,.806,.027)):
                chord = back-front
                ring = [(side*x,y+dy*height,front+dz*chord)
                        for dy,dz in ((.12,0),(.88,.18),(.76,.64),(.14,1),(-.3,.81),(-.45,.24))]
                rings.append(ring if side > 0 else list(reversed(ring)))
            groups[group] = (groups[group][0],loft(rings,lambda row,face: grey[0 if face < 3 else 1]))
    write(root / "mesh" / f"{model}.milmesh", groups)


if __name__ == "__main__":
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("original", type=Path)
    args.add_argument("output", type=Path)
    paths = args.parse_args()
    for name in ("shahed_238", "geran4", "mohajer_6"):
        patch(name, paths.output, paths.original)
