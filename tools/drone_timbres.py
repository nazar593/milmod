"""Synthesize original RPM layers for Minecraft, without measured engine signatures."""
import json
import math
from pathlib import Path
import struct
import subprocess

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod"
RATE = 22050
# Artistic base tones, pulse rates and harmonic weights.
VOICES = {
    "mavic3t": (342, 31, .26, .08),
    "orlan_10": (157, 14, .46, .15),
    "forpost_r": (132, 12, .64, .24),
    "mohajer_6": (116, 17, .58, .32),
    "lyutyi": (174, 19, .75, .19),
    "fp1": (191, 23, .53, .31),
    "shahed_238": (931, 13, .30, .16),
    "geran4": (1177, 17, .37, .21),
    "l39_albatros": (834, 10, .41, .20),
}


def samples(fundamental, flutter, second, third):
    for index in range(RATE):
        angle = math.tau * index / RATE
        pulse = .78 + .22 * math.sin(angle * flutter)
        sound = (math.sin(angle * fundamental)
                 + second * math.sin(angle * (2 * fundamental + flutter))
                 + third * math.sin(angle * (3 * fundamental - flutter))
                 + .12 * math.sin(angle * (fundamental - 2 * flutter)))
        yield round(16000 * pulse * sound / (1.12 + second + third))


def main():
    manifest = ASSETS / "sounds.json"
    definitions = json.loads(manifest.read_text())
    for model, params in VOICES.items():
        name = f"{model}_texture_loop"
        pcm = struct.pack(f"<{RATE}h", *samples(*params))
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                        "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "4", str(ASSETS / "sounds" / f"{name}.ogg")],
                       input=pcm, check=True)
        definitions[name] = {"sounds": [f"milmod:{name}"]}
    manifest.write_text(json.dumps(definitions, indent=2) + "\n")


if __name__ == "__main__":
    main()
