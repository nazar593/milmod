"""Generate an alpha-only lens frame for the Forpost camera."""
from math import hypot
from pathlib import Path

from PIL import Image

SIZE = 512
destination = (Path(__file__).resolve().parents[1]
               / "src/main/resources/assets/milmod/textures/gui/forpost_camera.png")
frame = Image.new("RGBA", (SIZE, SIZE))
pixels = frame.load()

for row in range(SIZE):
    y = abs((row + .5) / SIZE * 2 - 1)
    for column in range(SIZE):
        x = abs((column + .5) / SIZE * 2 - 1)
        edge = ((x / .967) ** 5 + (y / .930) ** 5) ** .2
        if edge > 1.004:
            alpha = 255
        else:
            shade = min(1, max(0, (hypot(x, y) - .36) / .94))
            rim = min(1, max(0, (edge - .91) / .094))
            alpha = round(min(255, shade ** 2 * 112 + rim ** 4 * 232))
        pixels[column, row] = (3, 7, 11, alpha)

frame.save(destination)
