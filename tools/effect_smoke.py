"""Generate the original four-tile smoke atlas."""
import math
import random
from pathlib import Path

from PIL import Image

image = Image.new("RGBA", (128, 128))
for variant in range(4):
    rng = random.Random(81 + variant)
    blobs = [(0, 0, .60)]
    for index in range(8):
        angle = index * math.tau / 8 + rng.uniform(-.16, .16)
        spread = rng.uniform(.31, .58)
        blobs.append((spread * math.cos(angle), spread * math.sin(angle), rng.uniform(.24, .43)))
    for py in range(64):
        for px in range(64):
            x, y = (px - 31.5) / 31, (py - 31.5) / 31
            weights = [max(0, 1 - math.hypot(x - bx, y - by) / radius) for bx, by, radius in blobs]
            alpha = min(1, 2.85 * max(weights) + .28 * sum(weights))
            alpha *= min(1, max(0, (1.03 - math.hypot(x, y)) * 7))
            luminance = round(max(85, min(255, 228 - 39 * y + 18 * math.sin(x * 11 + y * 6))))
            image.putpixel((px + 64 * (variant % 2), py + 64 * (variant // 2)),
                           (luminance, luminance, luminance, round(255 * alpha)))
path = Path(__file__).resolve().parents[1] / "src/main/resources/assets/milmod/textures/effect/smoke.png"
image.save(path)
