"""Build the block-style AN-196 from the supplied walkaround of the game model."""
import random
import struct

from PIL import Image, ImageDraw

from forpost_visuals import loft, octagon, prism, quads, tire
from import_models import normal

ATLAS = (256, 128)
PALETTE = (
    (220, 224, 225), (194, 200, 203), (166, 175, 179), (236, 239, 239),
    (139, 147, 154), (56, 65, 72), (37, 45, 50), (110, 119, 124),
    (66, 75, 80), (237, 239, 237), (151, 105, 58), (196, 149, 92),
    (111, 78, 48), (99, 103, 102), (206, 213, 214), (156, 165, 169),
)


def across(rings, shades):
    return [(points[::-1], shade) for points, shade in loft(rings, shades)]


def wing(sign):
    def section(span, front, chord, y, height):
        ring = [(sign*span, y + height*.23, front),
                (sign*span, y + height*.55, front + chord*.13),
                (sign*span, y + height*.36, front + chord*.78),
                (sign*span, y, front + chord),
                (sign*span, y - height*.20, front + chord*.77),
                (sign*span, y - height*.30, front + chord*.21)]
        return ring if sign > 0 else ring[::-1]

    shades = (3, "wing", "wing", 2, 1, 14) if sign > 0 else (1, 2, "wing", "wing", 3, 14)
    faces = across([section(*row) for row in
                    ((.18, -.36, .52, .53, .081), (.57, -.35, .50, .518, .075),
                     (1.54, -.345, .473, .505, .061), (2.42, -.317, .452, .491, .052),
                     (2.49, -.299, .419, .488, .048))], shades)
    x = sign * 2.46
    # The outer tabs continue both above and below the wing's trailing half.
    winglet = ((-.225,.421), (-.22,.596), (-.116,.64), (.075,.604), (.112,.398), (.058,.378))
    faces += across([[(x + dx, y, z) for z, y in winglet] for dx in (-.026,.026)],
                    (3, 9, 14, 1, 2, 14))
    x = sign * 1.93
    faces += prism((x-.029,.52,-.148),(x+.029,.555,-.082),9)
    return faces


def tail_panel(sign):
    # A closed, thin section at the boom rises to the same spine on both sides.
    def section(x, y, z, chord):
        ring = [(sign*x, y+.014, z), (sign*x, y+.030, z+chord*.19),
                (sign*x, y+.020, z+chord*.78), (sign*x, y+.004, z+chord),
                (sign*x, y-.018, z+chord*.91), (sign*x, y-.021, z+chord*.18)]
        return ring if sign > 0 else ring[::-1]

    shades = (3, "tail", "tail", 2, "tail", 14) if sign > 0 else ("tail", 2, "tail", "tail", 3, 14)
    rings = [section(*row) for row in ((0, 1.128, 1.293, .405), (.36, .887, 1.335, .411),
                                       (.755, .623, 1.381, .419), (.817,.58,1.387,.418))]
    faces = [(points[::-1], shade) for points, shade in loft(rings, shades, close=False)]
    faces.append((rings[-1], shades[-1]))
    return faces


def strut(start, end, width, shade):
    def ring(center):
        x, y, z = center
        return [(x-width,y,z-width), (x+width,y,z-width),
                (x+width,y,z+width), (x-width,y,z+width)]
    return list(loft([ring(start), ring(end)], (shade,)*4))


def groups():
    hull = list(loft([octagon(*station) for station in (
        (-1.63,.102,.408,.585,.039), (-1.52,.161,.396,.650,.053),
        (-1.24,.186,.373,.706,.058), (-.75,.195,.363,.754,.055),
        (-.22,.195,.362,.765,.055), (.36,.183,.375,.776,.056),
        (.59,.160,.393,.762,.047), (.69,.127,.425,.703,.042),
    )], ("deck", 14, "hull", 2, 1, 2, "hull", 14)))
    hull += prism((-.022,.474,-1.878),(.022,.508,-1.596),5)
    hull += prism((-.067,.688,-1.288),(.019,.723,-1.165),14)
    hull += prism((-.038,.715,-1.235),(-.006,.789,-1.19),3)
    hull += prism((-.048,.752,-.135),(.048,.797,-.005),14)
    yield 0, 0, (0,0,0), hull

    for sign in (-1, 1):
        x = sign * .78
        side = wing(sign)
        side += list(loft([
            [(px+x,y,z) for px,y,z in octagon(*row)] for row in
            ((-.18,.043,.459,.518,.011), (.61,.032,.475,.536,.009),
             (1.47,.023,.532,.592,.008), (1.61,.023,.543,.599,.008))],
            (0, 3, 14, 2, 1, 2, 14, 3)))
        side += tail_panel(sign)
        yield 1 if sign < 0 else 2, 0, (x,.53,.05), side

    shaft = (0,.579,.751)
    hub = prism((-.055,.524,.678),(.055,.634,.781),13)
    blades = []
    for angle in (0,120,240):
        turn = (([0,0,angle], shaft),)
        blades += prism((-.032,.611,.740),(.031,.880,.765),10,turn)
        blades += prism((-.017,.866,.742),(.022,.928,.764),11,turn)
    yield 0, 3, shaft, hub + blades

    yield 0, 17, (0,.087,-1.31), strut((0,.452,-1.28),(0,.087,-1.31),.023,8)
    for sign in (-1,1):
        yield 0, 17, (sign*.37,.103,.166), strut((sign*.139,.451,.037),(sign*.37,.103,.166),.026,8)
    for x, z, radius in ((0,-1.31,.091),(-.37,.166,.111),(.37,.166,.111)):
        y = radius * .924
        width = .081 if x else .067
        yield 0, 5, (x,y,z), tire(x,y,z,width,radius) + tire(x,y,z,width+.006,radius*.51,5)


REGIONS = {
    "wing": (64,0,184,38),
    "hull": (64,40,176,26),
    "deck": (64,68,176,23),
    "tail": (64,94,104,32),
}


def coordinates(point, index, shade, face_normal):
    x,y,z = point
    if shade == "wing":
        u, v = (abs(x)-.18)/2.31, (z+.36)/.51
        if face_normal[1] < 0:
            shade = 1
    elif shade == "hull":
        u, v = (z+1.63)/2.32, (.777-y)/.42
    elif shade == "deck":
        u, v = (z+1.63)/2.32, (x+.195)/.39
    elif shade == "tail":
        u, v = abs(x)/.817, (z-1.29)/.52
    if isinstance(shade, int):
        a,b = (shade % 4)*16,(shade // 4)*16
        return (a + (1 if index in (0,3) else 14))/ATLAS[0], (b + (14 if index in (0,1) else 1))/ATLAS[1]
    a,b,w,h = REGIONS[shade]
    return ((a + 1 + max(0,min(1,u))*(w-3))/ATLAS[0],
            (b + 1 + max(0,min(1,v))*(h-3))/ATLAS[1])


def write_atlas(path):
    atlas = Image.new("RGB", ATLAS, PALETTE[0])
    draw = ImageDraw.Draw(atlas)
    for i, shade in enumerate(PALETTE):
        a,b = (i%4)*16,(i//4)*16
        draw.rectangle((a,b,a+15,b+15), fill=shade)

    rng = random.Random(196)
    panels = ((204,211,214),(217,223,226),(229,233,234),(179,188,194),(198,205,210))
    for panel, (a,b,w,h) in REGIONS.items():
        draw.rectangle((a,b,a+w-1,b+h-1), fill=panels[1])
        for x in range(4,w-14,13):
            y = rng.randrange(3, h-9)
            draw.rectangle((a+x,b+y,a+x+rng.randrange(5,12),b+y+rng.randrange(3,7)),
                           fill=rng.choice(panels))
        if panel in ("wing","tail"):
            draw.rectangle((a,b+h-5,a+w-1,b+h-3), fill=panels[3])
            draw.rectangle((a,b+2,a+w-1,b+4), fill=panels[2])
            for x in range(a+26,a+w-6,37):
                draw.line((x,b+3,x,b+h-4), fill=panels[2], width=2)
        else:
            for x in range(a+22,a+w-13,34):
                draw.rectangle((x,b+4,x+20,b+h-5), outline=panels[0], width=2)
                draw.rectangle((x+3,b+7,x+7,b+9), fill=panels[3])
            draw.line((a,b+h-4,a+w-1,b+h-4), fill=panels[2], width=2)

    # Larger gray fields around the center and outer access panels.
    a,b,_,_ = REGIONS["wing"]
    for x in (18, 92, 140, 164):
        draw.polygon([(a+x,b+10),(a+x+15,b+10),(a+x+15,b+16),(a+x+21,b+16),
                      (a+x+21,b+27),(a+x+5,b+27),(a+x+5,b+22),(a+x,b+22)],
                     fill=(184,192,199))
        draw.line((a+x+7,b+27,a+x+18,b+27), fill=panels[2], width=2)
    x,y = 132,19
    draw.polygon([(x-11,y-4),(x-8,y-4),(x-8,y-8),(x+7,y-8),(x+7,y-5),
                  (x+11,y-5),(x+11,y+5),(x+7,y+5),(x+7,y+8),(x-7,y+8),
                  (x-7,y+6),(x-11,y+6)], fill=(238,186,52))
    draw.rectangle((x-6,y-4,x+6,y+4), fill=(44,96,157))
    draw.rectangle((x-3,y-6,x+3,y+6), fill=(44,96,157))
    atlas.save(path)


def write_lyutyi(root):
    geometry = list(groups())
    mesh = bytearray(struct.pack(">IH", 0x4D494C31, len(geometry)))
    for part, animation, pivot, faces in geometry:
        faces = list(quads(faces))
        mesh.extend(struct.pack(">BB3fI", part, animation, *pivot, 4 * len(faces)))
        for vertices, shade in faces:
            direction = normal([(p,0,0) for p in vertices])
            if not direction:
                raise ValueError("Degenerate AN-196 face")
            for i, point in enumerate(vertices):
                mesh.extend(struct.pack(">8f", *point, *coordinates(point,i,shade,direction), *direction))
    (root / "mesh/lyutyi.milmesh").write_bytes(mesh)
    write_atlas(root / "textures/entity/lyutyi.png")
