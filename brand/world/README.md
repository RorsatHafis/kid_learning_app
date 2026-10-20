# The child's world: painted artwork (Y44)

`source/` holds the original image fills of the owner's Figma file
(https://www.figma.com/design/BhCeqhLnouc8CgoOCOjSvF), named by their Figma image hash. They were downloaded
unchanged through Figma's public image endpoint for that file, viewed without an account. Nothing here was
generated, traced or repainted.

`make_assets.py` resizes them into the WebP files the app ships (`frontend/src/assets/world/`):

| Shipped file | Source | Used for |
|---|---|---|
| `river-village(-sm)` | `03d50b24….jpg` | the child's home, the landing page, sign-in, the parent's scene band |
| `river-bend(-sm)` | `6e26c259….jpg` | inside one subject's journey; the faded backdrop of activities and the shelf |
| `river-path` | `36d05337….png` | the activity intro's cover; the skill garden's bank |
| `maths-bridge` | `6c5e5963….png` | Maths' place |
| `khmer-pavilion` | `67e95d28….png` | Khmer's place |
| `english-treehouse` | `0f5ee70f….png` | English's place |
| `lotus-pond` | `7273c23a….png` | the place of every other subject (science, arts, books) |

`18073b18….png` (a young palm) is kept with the sources but not used yet.

Rebuild after replacing a source: `python brand/world/make_assets.py` (needs Pillow).

## Still missing from the Figma file

Without an account the file can't be selected or exported, and these could not be found among the image fills
it exposes, so they are not in the app yet:

- **Momo's poses** (idle, happy, cheer, encourage, thinking). Until they arrive Momo is drawn in code after the
  Figma character; see `frontend/src/assets/buddy/README.md` for how to drop the approved images in.
- **The story boat** shown on the river in the Figma frames.
