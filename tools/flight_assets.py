"""Build original block meshes, item models, and attributed game sound loops."""
import argparse
import json
from pathlib import Path
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw
from scipy import signal

from geran4_assets import write_geran4
from airframe_updates import update_airframes
from import_models import compile_mesh, FACES
from reference_models import make_models

PALETTE = [
    (205, 213, 207), (159, 170, 166), (105, 119, 110), (59, 79, 77),
    (41, 47, 56), (76, 94, 106), (188, 201, 208), (240, 161, 56),
    (83, 102, 66), (126, 143, 103), (39, 73, 88), (63, 170, 151),
]

def box(x, y, z, w, h, d, color, rotation=None, pivot=None):
    uv = {side: {"uv": [4 * color + 0.5, 0.5], "uv_size": [3, 3]} for side in FACES}
    result = dict(origin=[x, y, z], size=[w, h, d], uv=uv)
    if rotation:
        result.update(rotation=rotation, pivot=pivot or [x + w / 2, y + h / 2, z + d / 2])
    return result

def bone(name, cubes, pivot=None, parent=None):
    value = dict(name=name, cubes=cubes, pivot=pivot or [0, 0, 0])
    if parent is not None:
        value["parent"] = parent
    return value

def rail(dark=False):
    span = 36 if dark else 33
    half = span / 2
    tilt = 9 if dark else 10
    deck = 6.0
    steel = 4 if dark else 5
    ribs = []
    for x in [-4.9, 4.0]:
        ribs.extend([box(x,deck,-half,0.9,0.85,span,steel,[tilt,0,0],[0,deck,0]),
                     box(x+0.17,deck+0.66,-half,0.55,0.28,span,6,[tilt,0,0],[0,deck,0])])
    for z in [-14,-8,-2,4,10,14]:
        ribs.append(box(-4.90,deck-0.25,z,9.80,0.50,0.83,steel,[tilt,0,0],[0,deck,0]))
    supports = []
    for z in [-11, 10]:
        y = deck - np.tan(np.deg2rad(tilt))*z + 0.15
        for sign in [-1,1]:
            x = sign*4.45
            foot_x = x + sign*2.9
            angle = np.rad2deg(np.arctan2(sign*2.9,y))
            height = float(np.hypot(y,2.9))
            supports.append(box(x-0.31,y-height,z-0.46,0.62,height,0.92,4,
                                [0,0,angle],[x,y,z]))
            supports.append(box(foot_x-0.85,0,z-1.1,1.7,0.45,2.2,4))
        supports.append(box(-6.45,0.46,z-0.28,12.9,0.38,0.56,3))
    cradle = [box(-2.6,deck+0.35,-0.8,5.2,0.65,4.8,8,[tilt,0,0],[0,deck,0]),
              box(-1.7,deck+0.92,0.2,3.4,0.21,2.6,9,[tilt,0,0],[0,deck,0])]
    return [bone("frame", ribs+supports),bone("cradle", cradle,[0,deck,0])]
 
def item_model(root, name, parts, display, front=None):
    textures = {"particle": "milmod:item/palette", "color": "milmod:item/palette"}
    if front:
        textures["screen"] = "milmod:item/tablet_screen"
    elements = []
    for minimum, maximum, shade in parts:
        faces = {f: {"uv": [shade+0.1, 0.1, shade+0.9, 0.9], "texture": "#color"} for f in FACES}
        if front is not None and len(elements) == front:
            faces["north"] = {"uv":[0,0,16,16],"texture":"#screen"}
            faces["south"] = faces["north"]
        elements.append({"from":minimum,"to":maximum,"faces":faces})
    (root/"models/item"/(name+".json")).write_text(json.dumps(
        {"elements":elements,"textures":textures,"display":display},indent=2)+"\n")

def make_visuals(root):
    palette = Image.new("RGB",(4*len(PALETTE),4))
    draw = ImageDraw.Draw(palette)
    for i,c in enumerate(PALETTE):
        draw.rectangle((i*4,0,i*4+3,3),fill=c)
    for key,bones,length in [
        ("orlan_rail",rail(),3),
        ("fp1_ramp",rail(True),2.8),
    ]:
        geom={"minecraft:geometry":[{"description":{"texture_width":48,"texture_height":4},"bones":bones}]}
        mesh,size=compile_mesh(key,geom,False,length)
        (root/"mesh"/(key+".milmesh")).write_bytes(mesh)
        palette.save(root/"textures/entity"/(key+".png"))
        if key == "fp1_ramp":
            continue
        (root/"models/item"/(key+".json")).write_text(json.dumps(
            {"parent":"item/generated","textures":{"layer0":"milmod:item/"+key}})+"\n")
        icon=Image.new("RGBA",(32,32))
        pen=ImageDraw.Draw(icon)
        if key=="orlan_rail":
            pen.polygon([(6,25),(23,7),(27,9),(10,28)],fill=PALETTE[5])
            pen.line((10,26,26,11),fill=PALETTE[6],width=2)
            pen.line((7,26,25,6),fill=PALETTE[6],width=2)
        else:
            pen.polygon([(16,4),(19,11),(29,16),(29,19),(19,17),(18,23),(23,25),(23,28),(16,26),
                         (9,28),(9,25),(14,23),(13,17),(3,19),(3,16),(13,11)],fill=PALETTE[0])
            pen.line((16,8,16,24),fill=PALETTE[5],width=2)
        icon.save(root/"textures/item"/(key+".png"))
    item_palette=Image.new("RGB",(16,16))
    item_draw=ImageDraw.Draw(item_palette)
    for i,c in enumerate(PALETTE):
        item_draw.line((i,0,i,15),fill=c)
    item_palette.save(root/"textures/item/palette.png")
    screen=Image.new("RGB",(32,32),(23,47,49))
    draw=ImageDraw.Draw(screen)
    for i in range(0,32,4):
        draw.line((i,0,i,31),fill=(43,72,72));draw.line((0,i,31,i),fill=(43,72,72))
    draw.line([(12,0),(14,7),(10,15),(16,24),(16,31)],fill=(47,109,145),width=4)
    draw.polygon([(23,11),(19,17),(25,17)],fill=(211,237,221))
    screen.save(root/"textures/item/tablet_screen.png")
    transforms={"gui":{"rotation":[15,18,0],"scale":[0.95,0.95,0.95]},
        "firstperson_righthand":{"rotation":[0,150,-5],"translation":[1,3,0],"scale":[0.95,0.95,0.95]},
        "firstperson_lefthand":{"rotation":[0,-150,5],"translation":[1,3,0],"scale":[0.95,0.95,0.95]},
        "thirdperson_righthand":{"rotation":[80,0,0],"scale":[0.60,0.60,0.60]},
        "ground":{"translation":[0,2,0],"scale":[0.32,0.32,0.32]}}
    item_model(root,"controller",[([0,3,6.8],[16,13,9.2],4),([1.2,4.1,6.65],[14.8,11.9,9.35],10)],transforms,1)
    compact={"gui":{"rotation":[25,40,0]},"ground":{"translation":[0,3,0],"scale":[0.5]*3}}
    item_model(root,"training_capsule",[
        ([5.2,5.2,4],[10.8,10.8,10.5],8),([6,6,10.5],[10,10,12],7),
        ([6.1,6.1,3],[9.9,9.9,4.4],11),([6,10.8,6],[10,12.0,8.8],5)],compact)
    make_models(root)
    write_geran4(root)
    update_airframes(root)

RATE=24000
LOOPS={
    "mavic3t":("quad",1.11,135,4600),
    "fp1":("prop",1.23,175,2500),
    "forpost_r":("prop",0.81,62,2950),
    "mohajer_6":("prop",0.94,105,3650),
    "orlan_10":("prop",1.46,235,3200),
    "lyutyi":("prop",0.70,75,2150),
    "geran4":("jet",1.25,165,5200),
    "shahed_238":("jet",1.13,125,4100),
    "l39_albatros":("jet",0.91,68,6400),
    "buk_m3":("diesel",0.77,44,1500),
    "tor_m1":("diesel",0.90,52,2550),
    "m113":("diesel",1.14,82,2150),
    "t64bv":("diesel",0.68,35,1840),
    "gaz_tigr":("diesel",1.24,68,4100),
    "iskander_k":("diesel",0.99,49,3200),
}
EFFECTS={
    "beacon_marker":(0.33,780,0.10),"launcher":(0.36,183,0.47),
    "fp1_ramp":(0.42,145,0.49),"orlan_rail":(0.32,223,0.41),
    "attach":(0.21,290,0.58),"release":(0.25,440,0.42),"damage":(0.37,95,0.75),
    "crash":(0.68,54,0.76),"start":(0.51,285,0.14),
}
def make_audio(root, recordings):
    samples={}
    for name in ["quad","prop","jet","diesel"]:
        with wave.open(str(recordings/(name+".wav"))) as w:
            assert w.getframerate()==RATE
            samples[name]=np.frombuffer(w.readframes(w.getnframes()),dtype="<i2").astype(float)/32768
    sounds={}
    def save(name,array):
        path=root/"sounds"/(name+".ogg")
        peak=max(np.max(np.abs(array)),0.001)
        pcm=np.rint(np.clip(array*0.79/peak,-1,1)*32767).astype("<i2")
        subprocess.run(["ffmpeg","-v","error","-y","-f","s16le","-ar",str(RATE),"-ac","1","-i","pipe:0","-c:a","libvorbis","-q:a","5",str(path)],input=pcm.tobytes(),check=True)
        sounds[name]={"sounds":["milmod:"+name]}
    variants={}
    for name,(source,rate,low,high) in LOOPS.items():
        data=samples[source]
        needed=int(RATE*4.7*rate)
        stride=RATE//2
        offsets=range(0,len(data)-needed,stride)
        best=sorted(offsets,key=lambda i:np.mean(data[i:i+needed]**2),reverse=True)
        idx=variants.get(source,0)
        variants[source]=idx+1
        start=best[min(idx,len(best)-1)] if best else 0
        tune=np.interp(start+np.arange(int(4.7*RATE))*rate,np.arange(len(data)),data)
        tune=signal.sosfiltfilt(signal.butter(3,[low,high],fs=RATE,btype="bandpass",output="sos"),tune)
        # Crossfade end to start, retaining a seamless per-model loop.
        n=RATE*7//10
        blend=0.5-0.5*np.cos(np.linspace(0,np.pi,n))
        tune=np.concatenate((tune[-n:]*(1-blend)+tune[:n]*blend,tune[n:-n]))
        save(name,tune)
    for i,(name,(duration,hz,mix)) in enumerate(EFFECTS.items()):
        t=np.arange(int(duration*RATE))/RATE
        noise=np.random.default_rng(321+i).normal(0,1,len(t))
        noise=signal.sosfilt(signal.butter(2,[max(34,hz/2),min(9000,hz*9)],fs=RATE,btype="bandpass",output="sos"),noise)
        tone=0.65*np.sin(2*np.pi*hz*t*(1-0.22*t))+0.24*np.sin(2*np.pi*1.76*hz*t)
        envelope=(1-np.exp(-t*450))*np.exp(-t*(5/duration))
        save(name,envelope*((1-mix)*tone+mix*2.5*noise))
    (root/"sounds.json").write_text(json.dumps(sounds,indent=2)+"\n")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recordings",type=Path)
    parser.add_argument("assets",type=Path)
    args=parser.parse_args()
    for path in ["models/item","textures/item","sounds"]:
        (args.assets/path).mkdir(parents=True,exist_ok=True)
    make_visuals(args.assets)
    make_audio(args.assets,args.recordings)
if __name__=="__main__":
    main()
