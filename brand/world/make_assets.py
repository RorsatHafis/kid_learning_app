"""Builds the child world's web images from the owner's Figma artwork (Y44).

Sources are the original image fills of the owner's Figma file "Bookeyond Kid" (child world), kept in source/ by their
Figma hash. Run from the repository root:  python brand/world/make_assets.py
Writes WebP files to frontend/src/assets/world/, sized for their largest use at 2x.
"""
from pathlib import Path

from PIL import Image

SOURCE = Path(__file__).parent / "source"
OUT = Path(__file__).parents[2] / "frontend" / "src" / "assets" / "world"

# name: (source file, width in px, quality)
ASSETS = {
    # The painted river village: the world's stage (desktop and tablet), and its second view for a subject's journey.
    "river-village": ("03d50b245cb4bfd87db5316b63c4eccb14bd13ff.jpg", 1584, 80),
    "river-village-sm": ("03d50b245cb4bfd87db5316b63c4eccb14bd13ff.jpg", 900, 78),
    "river-bend": ("6e26c259ca55554d7093d4b00900bb04770eb6f0.jpg", 1584, 80),
    "river-bend-sm": ("6e26c259ca55554d7093d4b00900bb04770eb6f0.jpg", 900, 78),
    # A square view of the path to the river, for narrow screens.
    "river-path": ("36d05337395c533fc14c767a69c5842832658ef1.png", 512, 82),
    # Destinations, each a subject's place (transparent).
    "maths-bridge": ("6c5e596398a0db15aa7608a98b111ec77c12b28b.png", 640, 84),
    "khmer-pavilion": ("67e95d28f98c3adcd6396e2264a16c9500c21eec.png", 640, 84),
    "english-treehouse": ("0f5ee70f90ffa57b2d2f678edae920227984703e.png", 640, 84),
    "lotus-pond": ("7273c23a03a0530fb80897ba6a2a86d479ab8a6c.png", 480, 84),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (source, width, quality) in ASSETS.items():
        image = Image.open(SOURCE / source)
        keep_alpha = image.mode == "RGBA" and image.getchannel("A").getextrema()[0] < 250
        image = image.convert("RGBA" if keep_alpha else "RGB")
        if image.width > width:
            image = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
        target = OUT / f"{name}.webp"
        image.save(target, "WEBP", quality=quality, method=6)
        print(f"{target.name}: {image.width}x{image.height}, {target.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
