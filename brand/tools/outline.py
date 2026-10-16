"""Outlines the Bookeyond Kid logo for print: the wordmark's glyphs from the self-hosted Alegreya at
the exact weights, kerning and tracking the live logo uses (positions measured in Chromium, see
measure.json), and the mark drawn from the same paths as frontend/src/components/brand/Logo.tsx."""
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
    f = TTFont(os.path.join(HERE, "alegreya.woff2"))
    fonts[w] = instantiateVariableFont(f, {"wght": w})

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
        upm = font["head"].unitsPerEm
        s = 1000 / upm
        gs = font.getGlyphSet()
        name = font.getBestCmap()[ord(c["ch"])]
        x0 = c["x"] + SHIFT
        t = (s, 0, 0, -s, x0, BASE)  # font units to px at 1000px type; flip y
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

# The live logo's colours (tailwind.config.ts tonle-900 / tonle-600 on light; white / tonle-200 on
# dark) and the mark's tones from Logo.tsx.
TONES = {
    "ink": {"lead": "#10323A", "kid": "#22636E", "outer": "#154049", "inner": "#D9667F", "pages": "#F4F6F1", "spark": "#F2A93B", "water": "#F0A7B7"},
    "paper": {"lead": "#FFFFFF", "kid": "#BCD8DA", "outer": "#FFFFFF", "inner": "#F0A7B7", "pages": "#154049", "spark": "#FFD27A", "water": "#FBD3DC"},
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

# Below 36px / 10mm: one solid book, a bold three-petal lotus, a clear spark (Logo.tsx "compact").
MARK_SMALL = [
    ("outer", None, "M32 41 C24 35.5 12.5 34.4 4.5 36.6 C3.6 36.9 3 37.7 3 38.7 L3 51.2 C3 52.4 4.1 53.2 5.2 52.9 C13.5 50.6 24.8 51.6 32 58 C39.2 51.6 50.5 50.6 58.8 52.9 C59.9 53.2 61 52.4 61 51.2 L61 38.7 C61 37.7 60.4 36.9 59.5 36.6 C51.5 34.4 40 35.5 32 41 Z"),
    ("water", None, "M31 45.5 C21.6 45 14.6 38.8 13 28.2 C21.6 29.2 28.4 35 31 45.5 Z"),
    ("water", None, "M33 45.5 C42.4 45 49.4 38.8 51 28.2 C42.4 29.2 35.6 35 33 45.5 Z"),
    ("inner", None, "M32 46.5 C22.4 41.5 20.6 31.4 25.4 23.6 C27.5 20.6 29.8 18.8 32 16.4 C34.2 18.8 36.5 20.6 38.6 23.6 C43.4 31.4 41.6 41.5 32 46.5 Z"),
    ("spark", None, "M32 0.8 C32.8 5.4 34.6 7.2 39.2 8 C34.6 8.8 32.8 10.6 32 15.2 C31.2 10.6 29.4 8.8 24.8 8 C29.4 7.2 31.2 5.4 32 0.8 Z"),
]


def mark_paths(tone, drawing=MARK):
    c = TONES[tone]
    out = []
    for key, kind, d in drawing:
        if kind == "stroke":
            out.append(f'<path d="{d}" fill="none" stroke="{c[key]}" stroke-width="1.2" stroke-linecap="round" opacity="0.8"/>')
        elif kind == "soft":
            out.append(f'<path d="{d}" fill="{c[key]}" opacity="0.55"/>')
        else:
            out.append(f'<path d="{d}" fill="{c[key]}"/>')
    return "".join(out)


def words_svg(tone):
    c = TONES[tone]
    lead = "".join(d for w, d in parts if w == 750)
    kid = "".join(d for w, d in parts if w == 420)
    return f'<path fill="{c["lead"]}" d="{lead}"/><path fill="{c["kid"]}" d="{kid}"/>'


def fmt(v):
    return f"{v:.1f}"


def save(name, svg):
    open(os.path.join(OUT, name), "w", encoding="utf-8").write(svg)


def write_lockup(name, tone, with_mark):
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
        body.append(f'<g transform="translate({fmt(mk["x"])} {fmt(mk["y"])}) scale({s:.4f})">{mark_paths(tone)}</g>')
    body.append(words_svg(tone))
    title = "Bookeyond Kid" + (" logo" if with_mark else " wordmark")
    save(name, f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{fmt(x0)} {fmt(y0)} {fmt(w)} {fmt(h)}" '
               f'width="{fmt(w / 10)}" height="{fmt(h / 10)}" role="img" aria-label="Bookeyond Kid">'
               f"<title>{title}</title>" + "".join(body) + "</svg>\n")
    print(name, fmt(w), "x", fmt(h))


def write_mark(name, tone, drawing, title):
    save(name, '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="256" height="256" role="img" '
               f'aria-label="Bookeyond Kid"><title>{title}</title>{mark_paths(tone, drawing)}</svg>\n')
    print(name)


for tone in ("ink", "paper"):
    write_lockup(f"bookeyond-kid-wordmark-{tone}.svg", tone, False)
    write_lockup(f"bookeyond-kid-logo-{tone}.svg", tone, True)
write_mark("bookeyond-kid-mark-ink.svg", "ink", MARK, "Bookeyond Kid mark")
write_mark("bookeyond-kid-mark-small-ink.svg", "ink", MARK_SMALL, "Bookeyond Kid mark, small sizes")
