# Rebuilding the brand files

```
node brand/tools/build.mjs
```

Needs Python with fontTools and the frontend's installed dependencies (it uses the frontend's
Playwright and Chromium; set `PLAYWRIGHT_BROWSERS_PATH` if the browsers live elsewhere). It:

1. copies the self-hosted Alegreya (`frontend/node_modules/@fontsource-variable/alegreya/files/alegreya-latin-wght-normal.woff2`)
   here as `alegreya.woff2` (removed again at the end);
2. measures the live lockup's glyph positions (kerning and tracking) in Chromium from
   `measure.html` into `measure.json` (1000px type);
3. outlines the wordmark and writes the logo, wordmark and mark SVGs to `brand/print`
   (`outline.py`; `shim/brotli.py` lets fontTools read WOFF2 using Node's built-in Brotli, so no
   extra Python package is needed);
4. prints the two `-ink` PDFs (vector, no fonts, no background);
5. writes the app icons to `frontend/public`: `icon.svg` (small drawing) and `icon-large.svg`, and
   `favicon.png` (32), `icon-512.png` (512, rounded) and `apple-touch-icon.png` (180, a full square:
   iOS rounds it and would fill transparent corners with black).

The weights (750 / 420), tracking, colours and the mark's paths must match
`frontend/src/components/brand/Logo.tsx`. Change them together.
