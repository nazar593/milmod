"""Create the original pixel-art goggles, remote and fuel can model sprites."""
import json

from PIL import Image, ImageDraw

from item_icons import ASSETS


def sprites():
    slate, pale, steel = "#233539", "#a8bcc0", "#536d70"
    items = {}
    for name in ("mavic_controller", "mavic_goggles", "flight_fuel"):
        img = Image.new("RGBA", (16,16))
        draw = ImageDraw.Draw(img)
        if name == "mavic_controller":
            draw.polygon([(2,6),(4,4),(11,4),(13,6),(14,12),(12,14),(9,11),(6,11),(3,14),(1,12)], fill=slate)
            draw.rectangle((5,5,10,9), fill=steel)
            draw.line((6,6,9,6), fill="#68d9a5")
            draw.point((3,8), fill=pale)
            draw.point((12,8), fill=pale)
            draw.line((4,2,4,5), fill=slate, width=2)
            draw.line((11,2,11,5), fill=slate, width=2)
        elif name == "mavic_goggles":
            draw.line((1,6,1,10), fill=steel)
            draw.line((14,6,14,10), fill=steel)
            draw.rounded_rectangle((2,4,13,11), radius=2, fill=slate)
            draw.rectangle((3,6,12,9), fill="#568e91")
            draw.line((4,6,11,6), fill="#97cec8")
            draw.rectangle((7,9,8,11), fill=slate)
        else:
            draw.rounded_rectangle((3,4,12,14), radius=1, fill="#986139")
            draw.rectangle((3,4,11,12), fill="#c4924b")
            draw.line((5,2,10,2,10,4), fill="#845a36", width=2)
            draw.rectangle((11,3,13,5), fill=slate)
            draw.line((5,6,9,10), fill="#af783f")
            draw.line((9,6,5,10), fill="#af783f")
        items[name] = img
    return items


if __name__ == "__main__":
    for name, image in sprites().items():
        image.save(ASSETS / "textures/item" / (name + ".png"))
        model = {"parent":"minecraft:item/generated", "textures":{"layer0":f"milmod:item/{name}"}}
        (ASSETS / "models/item" / (name + ".json")).write_text(json.dumps(model, indent=2) + "\n")
