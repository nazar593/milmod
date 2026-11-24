"""Build the universal MilMod tripod mesh and its metal texture atlas."""
import struct
import math
from pathlib import Path

from PIL import Image, ImageDraw

from forpost_visuals import prism, loft, quads, face_uv
from import_models import normal

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"
COLORS = [(68,73,60), (89,96,76), (108,115,92), (39,43,39),
          (142,148,130), (71,76,73), (184,144,69), (29,33,31)]


def beam(a, b, width, height, shade):
    dx,dy,dz = (b[i]-a[i] for i in range(3))
    length = math.sqrt(dx*dx+dy*dy+dz*dz)
    unit = (dx/length,dy/length,dz/length)
    tangent_length = math.hypot(dx,dz)
    right = (dz/tangent_length,0,-dx/tangent_length) if tangent_length else (1,0,0)
    up = (unit[1]*right[2]-unit[2]*right[1], unit[2]*right[0]-unit[0]*right[2],
          unit[0]*right[1]-unit[1]*right[0])
    def ring(center):
        return [tuple(center[i]+x*width*right[i]+y*height*up[i] for i in range(3))
                for x,y in [(-1,-1),(1,-1),(1,1),(-1,1)]]
    return list(loft([ring(a),ring(b)], (shade, (shade+1)%6, shade, shade)))


def geometry():
    feet = ((-.56,.06,-.51),(.56,.06,-.51),(0,.06,.88))
    support=[]
    for index, point in enumerate(feet):
        x,y,z=point
        support += prism((x-.14,0,z-.14),(x+.14,.065,z+.14),3)
        support += beam(point,(0,.64,.12),.054,.063,1)
        support += beam(((x+.02),.17,z*.83),(0,.43,.18),.025,.021,0)
        support += prism((x-.055,.06,z-.055),(x+.055,.15,z+.055),5)
    support += prism((-.14,.61,-.04),(.14,.75,.29),2)
    support += prism((-.19,.69,.04),(.19,.79,.22),4)
    deck=[]
    for side in (-1,1):
        x=.145*side
        deck += beam((x,.94,-1.3),(x,.574,1.30),.038,.045,4)
        deck += beam((x,.862,-1.19),(x,.515,1.25),.045,.027,3)
    for z in (-1.14,-.54,.11,.66,1.12):
        y=.745-z*.141
        deck += prism((-.21,y-.047,z-.046),(.21,y,z+.046),1)
    deck += prism((-.258,.864,-1.18),(-.212,.93,-1.00),6)
    deck += prism((.212,.864,-1.18),(.258,.93,-1.00),6)
    return support+deck


def main():
    faces=list(quads(geometry()))
    mesh=bytearray(struct.pack(">IHBB3fI",0x4D494C31,1,0,0,0,0,0,len(faces)*4))
    for points, shade in faces:
        n=normal([(p,0,0) for p in points])
        if n is None:
            raise ValueError("Degenerate tripod face")
        for i,p in enumerate(points):
            mesh.extend(struct.pack(">8f",*p,*face_uv(i,shade,64),*n))
    (ROOT/'mesh/launcher.milmesh').write_bytes(mesh)
    atlas=Image.new('RGB',(64,64),COLORS[0])
    draw=ImageDraw.Draw(atlas)
    for i,color in enumerate(COLORS):
        x,y=(i%4)*16,(i//4)*16
        draw.rectangle((x,y,x+15,y+15),fill=color)
        draw.line((x+2,y+2,x+13,y+2),fill=tuple(min(255,c+15) for c in color))
        draw.line((x+2,y+13,x+13,y+13),fill=tuple(max(0,c-13) for c in color))
    atlas.save(ROOT/'textures/entity/launcher.png')


if __name__ == '__main__':
    main()
