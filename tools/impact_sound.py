"""Render an original stylized impact effect into the MilMod resource tree."""
import argparse
import json
import subprocess
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt

RATE = 32000


def generate(root):
    rng = np.random.default_rng(4050)
    t = np.arange(int(2.1 * RATE)) / RATE
    noise = rng.normal(0, 1, len(t))
    crack = sosfilt(butter(2, [290, 4900], btype="bandpass", fs=RATE, output="sos"), noise)
    pressure = sosfilt(butter(3, [32, 116], btype="bandpass", fs=RATE, output="sos"), noise)
    tail = sosfilt(butter(2, [55, 440], btype="bandpass", fs=RATE, output="sos"), rng.normal(0, 1, len(t)))
    tone = np.sin(2 * np.pi * (76 * t - 21 * t * t))
    burst = 0.48 * crack * np.exp(-t * 31) + 1.8 * pressure * np.exp(-t * 9)
    burst += 0.23 * tone * np.exp(-t * 13) + 0.66 * tail * np.exp(-t * 4.3)
    burst *= (1 - np.exp(-t * 420)) * np.minimum(1, (2.1 - t) * 60)
    burst = np.tanh(2.0 * burst)
    pcm = np.rint(burst * 28000).astype("<i2")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i",
                    "pipe:0", "-c:a", "libvorbis", "-q:a", "5", str(root / "sounds" / "impact_blast.ogg")],
                   input=pcm.tobytes(), check=True)
    sound_map = root / "sounds.json"
    events = json.loads(sound_map.read_text())
    events["impact_blast"] = {"subtitle": "subtitles.milmod.impact", "sounds": ["milmod:impact_blast"]}
    sound_map.write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resource_root", type=Path)
    generate(parser.parse_args().resource_root)
