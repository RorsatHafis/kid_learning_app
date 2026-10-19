"""
Run from anywhere: python brand/official/make_assets.py (needs Pillow).

The official Bookeyond logo (supplied by the owner as a 640x640 JPEG) prepared for the product, without
redrawing it: the badge is cut out of its white square (only the area outside the circle becomes
transparent), and - as the owner allowed - its deep blue is carried into the platform's deep river teal
(tonle-800 #154049). Every shape, the lettering, the proportions and the grey rim are the supplied pixels.
"""
from pathlib import Path
from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / "brand/official/bookeyond-logo-source.jpg"
OFFICIAL = REPO / "brand/official"
ASSETS = REPO / "frontend/src/assets/brand"
PUBLIC = REPO / "frontend/public"
OFFICIAL.mkdir(parents=True, exist_ok=True)
ASSETS.mkdir(parents=True, exist_ok=True)

NAVY = (12, 59, 103)      # the logo's blue, as supplied
TEAL = (21, 64, 73)       # tonle-800, the platform's deep river
PAPER = (246, 243, 236)   # the platform's warm paper

src = Image.open(SRC).convert("RGB")
W, H = src.size


def circle_mask(size, scale=4):
    """The badge's circle, anti-aliased (drawn 4x and reduced)."""
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).ellipse((0, 0, size * scale - 1, size * scale - 1), fill=255)
    return big.resize((size, size), Image.LANCZOS)


def recolour(img, target):
    """Pixels that are a blend of white and the logo's blue become the same blend of white and `target`."""
    out = img.copy()
    px = out.load()
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            t = (255 - r) / (255 - NAVY[0])
            if t <= 0.02:
                continue
            t = min(1.0, t)
            pg = 255 - t * (255 - NAVY[1])
            pb = 255 - t * (255 - NAVY[2])
            if abs(g - pg) < 30 and abs(b - pb) < 30:  # on the white-to-blue line: the grey rim isn't
                px[x, y] = tuple(round(255 * (1 - t) + c * t) for c in target)
    return out


def cut(img):
    rgba = img.convert("RGBA")
    rgba.putalpha(circle_mask(W))
    return rgba


original = cut(src)
platform = cut(recolour(src, TEAL))

original.save(OFFICIAL / "bookeyond-logo-original.png", optimize=True)
platform.save(OFFICIAL / "bookeyond-logo-platform.png", optimize=True)

# The product's copy: crisp up to a 112px badge on a 2x screen.
platform.resize((320, 320), Image.LANCZOS).save(ASSETS / "bookeyond-logo.webp", "WEBP", quality=90, method=6)

# Browser tab and home-screen icons: the badge itself (transparent around it for the tab; on warm paper
# where the platform fills transparency with black, as iOS does).
platform.resize((64, 64), Image.LANCZOS).save(PUBLIC / "favicon.png", optimize=True)
for name, size, pad in (("apple-touch-icon.png", 180, 12), ("icon-512.png", 512, 32)):
    tile = Image.new("RGBA", (size, size), PAPER + (255,))
    badge = platform.resize((size - 2 * pad, size - 2 * pad), Image.LANCZOS)
    tile.alpha_composite(badge, (pad, pad))
    tile.convert("RGB").save(PUBLIC / name, optimize=True)
print("ok")
