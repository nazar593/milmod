"""Generate original short game sound cues. Requires ffmpeg with libvorbis."""
from pathlib import Path
import io
import json
import math
import random
import struct
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"
MODELS = ("orlan_10", "fp1", "geran4", "shahed_238", "mavic3t", "forpost_r", "mohajer_6", "lyutyi", "l39_albatros")
EVENTS = {"launch": .55, "flyby": .51, "failure": .39, "crash": .76}
RATE = 22050

def cue(model_index, event):
    randomizer = random.Random(807 + model_index * 7 + list(EVENTS).index(event))
    base = 67 + model_index * 11
    samples = []
    noise = 0.0
    phase = 0.0
    seconds = EVENTS[event]
    for sample in range(round(RATE * seconds)):
        t = sample / RATE
        f = t / seconds
        noise += .14 * (randomizer.uniform(-1, 1) - noise)
        if event == "launch":
            frequency, tone, hiss = base * (.74 + f * 1.35), .65, .35
            gain = min(1, f * 20) * (1 - f) ** 1.2
        elif event == "flyby":
            frequency, tone, hiss = base * (1.70 - f * .62), .37, .63
            gain = math.sin(math.pi * f) ** 1.6
        elif event == "failure":
            frequency, tone, hiss = base * (1.78 - f * 1.49), .5, .5
            gain = min(1, f * 25) * (1 - f) ** 1.4 * (.6 + .4 * math.cos(80 * t))
        else:
            frequency, tone, hiss = base * (1.22 - f * .80), .24, .76
            gain = min(1, f * 95) * (1 - f) ** 3
        phase += math.tau * frequency / RATE
        harmonic = math.sin(phase) * .64 + math.sin(phase * 2.0) * .24 + math.sin(phase * 3.0) * .12
        samples.append(round((harmonic * tone + noise * hiss) * gain * 19500))
    raw = io.BytesIO()
    with wave.open(raw, 'wb') as output:
        output.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(struct.pack(f'<{len(samples)}h', *samples))
    return raw.getvalue()

if __name__ == '__main__':
    definitions = ROOT / 'sounds.json'
    data = json.loads(definitions.read_text())
    for index, model in enumerate(MODELS):
        for event in EVENTS:
            name = f'{model}_{event}'
            path = ROOT / 'sounds' / f'{name}.ogg'
            subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'wav', '-i', 'pipe:0',
                            '-c:a', 'libvorbis', '-q:a', '4', str(path)], input=cue(index, event), check=True)
            data[name] = {'sounds': [f'milmod:{name}']}
    definitions.write_text(json.dumps(data, indent=2) + '\n')
