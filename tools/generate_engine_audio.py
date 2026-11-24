"""Generate original, mono sound effects for the Minecraft audio mixer."""
import io
import math
import struct
import subprocess
import wave
from pathlib import Path

RATE = 44100
DIR = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod/sounds"


def write(name, seconds, sample):
    frames = bytearray()
    for i in range(int(seconds * RATE)):
        value = max(-1, min(1, sample(i / RATE)))
        frames.extend(struct.pack("<h", round(value * 32767)))
    stream = io.BytesIO()
    with wave.open(stream, "wb") as wav:
        wav.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        wav.writeframes(frames)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5",
                    str(DIR / (name + ".ogg"))], input=stream.getvalue(), check=True)


def tone(t, hz):
    return math.sin(math.tau * hz * t)


def decay(t, rate):
    return min(1, t * 1250) * math.exp(-t * rate)


write("engine_prop_detail", 1, lambda t: (.27 * tone(t, 218) + .10 * tone(t, 436)
      + .06 * tone(t, 654)) * (.79 + .21 * tone(t, 8)))
write("engine_jet_detail", 1, lambda t: (.24 * tone(t, 1220) + .11 * tone(t, 1660)
      + .035 * tone(t, 2460)) * (.82 + .18 * tone(t, 6)))
write("starter_click", .18, lambda t: decay(t, 34) * (.48 * tone(t, 173) + .31 * tone(t, 516)))
write("rail_latch", .36, lambda t: decay(t, 20) * (.45 * tone(t, 91) + .20 * tone(t, 263))
      + (decay(t - .08, 16) * .14 * tone(t, 840) if t >= .08 else 0))
write("tablet_click", .09, lambda t: decay(t, 46) * .35 * tone(t, 930))
write("tablet_error", .25, lambda t: decay(t, 16) * (.24 * tone(t, 347) + .20 * tone(t, 439)))
