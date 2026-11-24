"""Produce MilMod's original heavy-effects textures and OGG audio."""
import json
import math
from pathlib import Path
import random
import struct
import subprocess

from PIL import Image

root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/milmod'
(root/'particles').mkdir(exist_ok=True)
(root/'textures/particle').mkdir(exist_ok=True)

for name in ['flash_core','fire_smoke_plume','explosion_spark']:
    p = Image.new('RGBA',(64,64))
    smoke = Image.open(root/'textures/effect/smoke.png').crop((0,0,64,64))
    for y in range(64):
        for x in range(64):
            u,v=(x-31.5)/32,(y-31.5)/32
            rad=math.hypot(u,v)
            if name=='fire_smoke_plume':
                p.putpixel((x,y),smoke.getpixel((x,y)))
                continue
            alpha=max(0, math.exp(-(u*u+v*v)*7)-.005) if name=='flash_core' else max(0,1-math.hypot(u*1.65,v*.74))**2
            p.putpixel((x,y),(255,255,255,round(alpha*255)))
    p.save(root/f'textures/particle/{name}.png')
    (root/f'particles/{name}.json').write_text(json.dumps({'textures':[f'milmod:{name}']})+'\n')

sound_json = root/'sounds.json'
sounds = json.loads(sound_json.read_text())
for name, length in [('explosion_crack',2.8),('explosion_muffled',2.8),('glass_rattle',.72),
                     ('tinnitus',1.25),('heartbeat',.78),('falling_whistle',.75)]:
    rng=random.Random(208+len(name))
    samples=[]
    rumble=0
    for i in range(round(22050*length)):
        t=i/22050
        noise=rng.uniform(-1,1)
        rumble += .030*(noise-rumble)
        decay=math.exp(-t*1.45)
        if name.startswith('explosion'):
            crack=(noise*.66+rumble*.34)*math.exp(-t*13)*.64 if name.endswith('crack') else rumble*.45*math.exp(-t*5)
            val=(crack + math.sin(2*math.pi*(43*t+8*(1-math.exp(-5*t))))*.38*decay
                 +rumble*.58*decay)*min(1,t*330)
        elif name=='glass_rattle':
            val=(math.sin(t*17600)+math.sin(t*21900)*.35)*math.exp(-t*5)*.09*(.43+.57*abs(math.sin(t*78)))
        elif name=='tinnitus':
            val=.16*math.sin(2*math.pi*3000*t)
        elif name=='heartbeat':
            gain=sum(math.exp(-((t-c)/.035)**2) for c in (.12,.29))
            val=.53*math.sin(2*math.pi*53*t)*gain
        else:
            val=.22*math.sin(2*math.pi*472*t)+.04*math.sin(2*math.pi*944*t)+noise*.012
        if name in ('tinnitus','falling_whistle'):
            val*=min(1,t*150,(length-t)*150)
        samples.append(round(max(-1,min(1,val))*29000))
    subprocess.run(['ffmpeg','-v','error','-y','-f','s16le','-ar','22050','-ac','1','-i','pipe:0',
                    '-c:a','libvorbis',str(root/f'sounds/{name}.ogg')],input=struct.pack(f'<{len(samples)}h',*samples),check=True)
    sounds[name]={'sounds':[f'milmod:{name}']}
sound_json.write_text(json.dumps(sounds,indent=2)+'\n')
