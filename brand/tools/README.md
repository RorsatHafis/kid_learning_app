# Rebuilding the outlined wordmark

1. Copy `frontend/node_modules/@fontsource-variable/bricolage-grotesque/files/bricolage-grotesque-latin-opsz-normal.woff2`
   here as `bg-opsz.woff2`.
2. Measure the live wordmark's glyph positions (kerning + tracking) in Chromium: open
   `measure.html` with Playwright and save, for each character of "Bookeyond" and " Kid", its
   range's left edge, plus the baseline and the mark's box, to `measure.json` (1000px type).
3. `PYTHONPATH=shim python outline.py ../print` — `shim/brotli.py` lets fontTools read WOFF2
   using Node's built-in Brotli, so no extra Python package is needed.

The outlines use weight 750 ("Bookeyond") and 420 ("Kid") at optical size 48, as in
`frontend/src/components/brand/Logo.tsx`. Change both together.
