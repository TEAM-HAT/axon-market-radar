# Radar logo

The Market Radar logo: an R inside radar rings, and the "Radar" wordmark beside it. White on the brand blue
`#061AD3` is the main version; it also works in the blue on white, and in black or white alone.

## Construction

Everything is measured in R, the radius of the outer ring.

- Four circles share one centre, at 1/2, 2/3, 5/6 and 1 R. The outer ring runs between 5/6 and 1, the inner ring
  (the R's bowl) between 1/2 and 2/3, so the rings and the gap between them are all R/6 wide.
- The vertical line x = -R/2 is the R's stem. The inner ring keeps only its slice to the left of it, and the outer
  ring ends on it at the bottom left.
- The horizontal line y = -R/2 is the top of the counter, which gives it a square top-left corner.
- The diagonal y = R/4 + 1.14 x is the R's leg: both rings end on it at the lower right.
- The wordmark's capitals run from y = -R/2 to 0.575 R, and its R starts 0.63 R to the right of the rings.

The wordmark is drawn as one path in the same units (`wordmark-path.txt`). Both "a"s are the same glyph.

## Files

| File | What it is |
| --- | --- |
| `radar-lockup.svg`, `-white`, `-black` | Mark and wordmark, in blue, white and black, tight to the drawing |
| `radar-mark.svg`, `-white`, `-black` | The mark alone |
| `radar-icon.svg` | The app icon: the white mark on a blue tile |
| `radar-banner.png` | The white lockup on blue, 1,964 x 800, for sharing |

Leave clear space of at least R/2 around the lockup, and do not set it smaller than 16 px tall on screen.

## Rebuilding

`python3 brand/build_logo.py` (from the repository root) writes the files above, the site icons (`favicon.svg`,
`icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`) and the Android drawables
(`logo_mark`, `logo_lockup`, `ic_launcher_foreground`, and the widget header marks `mark` and `mark_solid`).
Then run `python3 android/tools/gen_layouts.py` for the black-and-white twins of the widget marks.
PNGs need `pip install cairosvg`.
