"""Outlines the Bookeyond Kid wordmark for print: glyphs from the self-hosted Bricolage
Grotesque at the exact weights, optical size, kerning and tracking the live logo uses
(positions measured in Chromium, see measure.json)."""
import json, os, sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
m = json.load(open(os.path.join(HERE, "measure.json")))

fonts = {}
for w in (750, 420):
    f = TTFont(os.path.join(HERE, "bg-opsz.woff2"))
    fonts[w] = instantiateVariableFont(f, {"wght": w, "opsz": 48})

# In the product the mark never shrinks (shrink-0): text starts after the full mark + gap.
SHIFT = (m["mark"]["x"] + m["mark"]["h"] + 450) - m["chars"][0]["x"]
BASE = m["baseline"]

def glyph_paths(chars):
    """[(weight, svg path d)] and overall bounds, in px at 1000px type."""
    parts, box = [], [1e9, 1e9, -1e9, -1e9]
    for c in chars:
        if c["ch"].isspace():
            continue
        font = fonts[c["weight"]]
        gs = font.getGlyphSet()
        name = font.getBestCmap()[ord(c["ch"])]
        x0 = c["x"] + SHIFT
        t = (1, 0, 0, -1, x0, BASE)  # font units == px at 1000px/1000upm; flip y
        pen = SVGPathPen(gs)
        gs[name].draw(TransformPen(pen, t))
        bp = BoundsPen(gs)
        gs[name].draw(TransformPen(bp, t))
        if bp.bounds:
            b = bp.bounds
            box = [min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3])]
        parts.append((c["weight"], pen.getCommands()))
    return parts, box

parts, box = glyph_paths(m["chars"])

TONES = {
    "ink": {"lead": "#11214D", "kid": "#4468B8", "outer": "#182E66", "inner": "#2E4F9E", "pages": "#F6F7FB", "spark": "#F3A83B", "water": "#6E8FD0"},
    "paper": {"lead": "#FFFFFF", "kid": "#C9D6EF", "outer": "#FFFFFF", "inner": "#C9D6EF", "pages": "#182E66", "spark": "#FFD27A", "water": "#A2B8E3"},
}

MARK = [
    ("outer", None, "M32 43.2 C25.2 37.2 13.4 35.6 5.6 38 C4.6 38.3 4 39.2 4 40.2 L4 49.6 C4 50.8 5.1 51.6 6.2 51.3 C14.2 49.2 25.4 50.4 32 56 C38.6 50.4 49.8 49.2 57.8 51.3 C58.9 51.6 60 50.8 60 49.6 L60 40.2 C60 39.2 59.4 38.3 58.4 38 C50.6 35.6 38.8 37.2 32 43.2 Z"),
    ("pages", "stroke", "M9 45.2 C16 43.3 23.6 44 28.8 48.2"),
    ("pages", "stroke", "M55 45.2 C48 43.3 40.4 44 35.2 48.2"),
    ("water", None, "M30.8 44 C23.6 43.6 17.8 38.8 16.4 30.4 C23 31 28.6 35.6 30.8 44 Z"),
    ("water", None, "M33.2 44 C40.4 43.6 46.2 38.8 47.6 30.4 C41 31 35.4 35.6 33.2 44 Z"),
    ("inner", None, "M32 46.4 C25 42.2 23.4 33.6 27.6 25.9 C29 23.3 30.5 20.6 32 17.6 C33.5 20.6 35 23.3 36.4 25.9 C40.6 33.6 39 42.2 32 46.4 Z"),
    ("water", "soft", "M32 46.4 C28.4 42.4 27.9 34.8 32 26.2 C36.1 34.8 35.6 42.4 32 46.4 Z"),
    ("spark", None, "M32 1.8 C32.7 6.3 34.6 8.2 39 8.9 C34.6 9.6 32.7 11.5 32 16 C31.3 11.5 29.4 9.6 25 8.9 C29.4 8.2 31.3 6.3 32 1.8 Z"),
]

def mark_svg(tone, transform):
    c = TONES[tone]
    out = [f'<g transform="{transform}">']
    for key, kind, d in MARK:
        if kind == "stroke":
            out.append(f'<path d="{d}" fill="none" stroke="{c[key]}" stroke-width="1.2" stroke-linecap="round" opacity="0.8"/>')
        elif kind == "soft":
            out.append(f'<path d="{d}" fill="{c[key]}" opacity="0.55"/>')
        else:
            out.append(f'<path d="{d}" fill="{c[key]}"/>')
    out.append("</g>")
    return "".join(out)

def words_svg(tone):
    c = TONES[tone]
    lead = "".join(d for w, d in parts if w == 750)
    kid = "".join(d for w, d in parts if w == 420)
    return f'<path fill="{c["lead"]}" d="{lead}"/><path fill="{c["kid"]}" d="{kid}"/>'

def fmt(v):
    return f"{v:.1f}"

def write(name, tone, with_mark):
    pad = 40
    x0, y0, x1, y1 = box
    mk = m["mark"]
    if with_mark:
        x0 = min(x0, mk["x"])
        y0 = min(y0, mk["y"])
        y1 = max(y1, mk["y"] + mk["h"])
    x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
    w, h = x1 - x0, y1 - y0
    body = []
    if with_mark:
        s = mk["h"] / 64
        body.append(mark_svg(tone, f"translate({fmt(mk['x'])} {fmt(mk['y'])}) scale({s:.4f})"))
    body.append(words_svg(tone))
    title = "Bookeyond Kid" + (" logo" if with_mark else " wordmark")
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{fmt(x0)} {fmt(y0)} {fmt(w)} {fmt(h)}" '
           f'width="{fmt(w / 10)}" height="{fmt(h / 10)}" role="img" aria-label="Bookeyond Kid">'
           f"<title>{title}</title>" + "".join(body) + "</svg>\n")
    open(os.path.join(OUT, name), "w", encoding="utf-8").write(svg)
    print(name, fmt(w), "x", fmt(h))

for tone in ("ink", "paper"):
    write(f"bookeyond-kid-wordmark-{tone}.svg", tone, False)
    write(f"bookeyond-kid-logo-{tone}.svg", tone, True)
