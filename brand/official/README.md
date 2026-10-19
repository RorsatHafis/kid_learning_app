# Bookeyond — official logo

The official Bookeyond logo, supplied by the owner on 2026-10-19 (PD-34 resolved). It replaces the
product mark drafted in Y33–Y38, which was never the Bookeyond logo; the drafted print files and their
build tools were retired (they remain in git history).

| File | What it is |
|---|---|
| `bookeyond-logo-source.jpg` | The logo exactly as supplied (640 × 640). Keep it unchanged. |
| `bookeyond-logo-original.png` | The badge in its original blue, cut out of its white square (transparent outside the circle). |
| `bookeyond-logo-platform.png` | The same badge with its blue carried into the platform's deep river, tonle-800 `#154049` (the owner allowed the colour to match the platform). Used by the product. |
| `make_assets.py` | Rebuilds the two PNGs, the product's `frontend/src/assets/brand/bookeyond-logo.webp` and the app icons in `frontend/public` from the source. |

Nothing is redrawn: the symbol, the "bookeyond" lettering (with its infinity "oo"), the proportions and
the grey rim are the supplied pixels. Only the area outside the circle is made transparent, and the
recolouring moves each white-to-blue blend to the same white-to-teal blend, leaving the grey rim as it is.

## The Bookeyond Kid lockup

`frontend/src/components/brand/Logo.tsx`: the badge, with **KID** set centred underneath it in Alegreya
700, spaced capitals, about a third of the badge's width - a product label, never a competing badge
beside it. Bookeyond stays the lead. On dark grounds the badge gets a fine light ring and KID is white.
Sizes: 38 px (headers), 56, 84 and 128 px. The badge alone (`BrandMark`) is used where space is tight
(the child's space, phone headers, the staff rail on a phone) and for the app icons.

A vector master of the logo would sharpen it at very large sizes (print, signage); ask the owner for
one rather than tracing the JPEG.
