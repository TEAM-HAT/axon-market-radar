#!/usr/bin/env python3
"""Builds the Radar logo from its construction, and every logo file the radar uses.

The mark is an R inside radar rings. Four circles share one centre, at 1/2, 2/3, 5/6 and 1 of the
radius R: the outer ring runs between 5/6 and 1, the inner ring (the R's bowl) between 1/2 and 2/3.
A vertical line at x = -R/2 is the R's stem: the inner ring keeps only its slice left of it, and the
outer ring ends on it at the bottom left. Left of centre, the bowl's inner edge is the horizontal
line y = -R/2, so the counter has a square top-left corner. One diagonal line, y = R/4 + 1.14 x,
is the R's leg: both rings end on it at the lower right.

The "Radar" wordmark is kept as one path in the same units (wordmark-path.txt: centre of the rings
at 0,0 and R = 1, y down). It sits where the original lockup puts it: cap height from -R/2 to
0.575R, the R's stem 0.63R right of the rings.

Run from the repository root: python3 brand/build_logo.py
It writes brand/*.svg, the site icons, and the Android drawables (then run
android/tools/gen_layouts.py, which makes the black-and-white twins of the widget marks).
PNGs need cairosvg (pip install cairosvg).
"""
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

R0, R1, R2, R3 = 1 / 2, 2 / 3, 5 / 6, 1.0
STEM = -0.5            # x of the stem line
TOP = -0.5             # y of the bowl's top-left inner edge
LEG_Y0, LEG_K = 0.25, 1.14   # leg line: y = LEG_Y0 + LEG_K * x

BLUE = "#061AD3"       # the brand blue, from the original lockup
WHITE = "#FFFFFF"
BLACK = "#000000"

MARK_BOX = (-1.0, -1.0, 1.0, math.sqrt(1 - STEM ** 2))   # x0, y0, x1, y1 in mark units
WORD = open(os.path.join(HERE, "wordmark-path.txt")).read().strip()


def leg_hits(r):
    """Where the leg line meets the circle of radius r, on the lower right (larger x)."""
    a = 1 + LEG_K ** 2
    b = 2 * LEG_Y0 * LEG_K
    c = LEG_Y0 ** 2 - r ** 2
    x = (-b + math.sqrt(b * b - 4 * a * c)) / (2 * a)
    return x, LEG_Y0 + LEG_K * x


def f(v, nd=3):
    s = f"{v:.{nd}f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def mark_paths(scale=1.0, ox=0.0, oy=0.0, nd=3):
    """The mark's three shapes as SVG path data, centre at (ox, oy), outer radius `scale`."""
    P = lambda x, y: f"{f(ox + x * scale, nd)} {f(oy + y * scale, nd)}"
    A = lambda r, large, sweep, x, y: f"A{f(r * scale, nd)} {f(r * scale, nd)} 0 {large} {sweep} {P(x, y)}"

    # Outer ring: from its bottom-left end on the stem line, clockwise over the top to the leg line.
    yo3 = math.sqrt(R3 ** 2 - STEM ** 2)
    yo2 = math.sqrt(R2 ** 2 - STEM ** 2)
    qx3, qy3 = leg_hits(R3)
    qx2, qy2 = leg_hits(R2)
    outer = f"M{P(STEM, yo3)}{A(R3, 1, 1, qx3, qy3)}L{P(qx2, qy2)}{A(R2, 1, 0, STEM, yo2)}Z"

    # The slice of the inner ring left of the stem line.
    yc = math.sqrt(R1 ** 2 - STEM ** 2)
    slice_ = f"M{P(STEM, -yc)}{A(R1, 0, 0, STEM, yc)}Z"

    # The bowl: from the point where the top line meets the outer edge, clockwise round to the leg,
    # back along the inner edge to the top, then straight along the top line.
    tx = -math.sqrt(R1 ** 2 - TOP ** 2)
    cx1, cy1 = leg_hits(R1)
    cx0, cy0 = leg_hits(R0)
    bowl = f"M{P(tx, TOP)}{A(R1, 1, 1, cx1, cy1)}L{P(cx0, cy0)}{A(R0, 0, 0, 0, -R0)}Z"
    return [outer, slice_, bowl]


TOKEN = re.compile(r"[MLCZA]|-?\d*\.?\d+(?:e-?\d+)?")


def word_path(scale=1.0, ox=0.0, oy=0.0, nd=3):
    """The wordmark (absolute M, L, C and Z only) moved and scaled like mark_paths."""
    out, xy = [], 0
    for tok in TOKEN.findall(WORD):
        if tok.isalpha():
            out.append(tok)
            xy = 0
            continue
        v = float(tok)
        v = ox + v * scale if xy == 0 else oy + v * scale
        out.append(("" if out[-1].isalpha() else " ") + f(v, nd))
        xy ^= 1
    return "".join(out)


def word_box():
    """The wordmark's drawn extent, sampling its curves."""
    pts, cur, cmd, nums = [], (0.0, 0.0), None, []

    def flush():
        nonlocal cur
        if cmd in ("M", "L"):
            for i in range(0, len(nums), 2):
                cur = (nums[i], nums[i + 1])
                pts.append(cur)
        elif cmd == "C":
            for i in range(0, len(nums), 6):
                p0, p1, p2, p3 = cur, nums[i:i + 2], nums[i + 2:i + 4], nums[i + 4:i + 6]
                for k in range(41):
                    t = k / 40
                    m = 1 - t
                    pts.append((m ** 3 * p0[0] + 3 * m * m * t * p1[0] + 3 * m * t * t * p2[0] + t ** 3 * p3[0],
                                m ** 3 * p0[1] + 3 * m * m * t * p1[1] + 3 * m * t * t * p2[1] + t ** 3 * p3[1]))
                cur = tuple(p3)

    for tok in TOKEN.findall(WORD):
        if tok.isalpha():
            flush()
            cmd, nums = tok, []
        else:
            nums.append(float(tok))
    flush()
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


WORD_BOX = word_box()
LOCKUP_BOX = (MARK_BOX[0], MARK_BOX[1], WORD_BOX[2], MARK_BOX[3])


# ---- SVG -------------------------------------------------------------------------------------------
def svg_doc(w, h, body, vb=None):
    vb = vb or f"0 0 {f(w)} {f(h)}"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(w)}" height="{f(h)}" viewBox="{vb}">'
            f"{body}</svg>\n")


def shapes(fill, scale, ox, oy, word=False, nd=3):
    d = "".join(mark_paths(scale, ox, oy, nd))
    body = f'<path fill="{fill}" d="{d}"/>'
    if word:
        body += f'<path fill="{fill}" fill-rule="evenodd" d="{word_path(scale, ox, oy, nd)}"/>'
    return body


def mark_svg(fill, height=256):
    x0, y0, x1, y1 = MARK_BOX
    s = height / (y1 - y0)
    w = (x1 - x0) * s
    return svg_doc(w, height, shapes(fill, s, -x0 * s, -y0 * s))


def lockup_svg(fill, height=128, bg=None, pad=0.0):
    """The mark and the wordmark side by side. `pad` is in units of R, on every side."""
    x0, y0, x1, y1 = LOCKUP_BOX
    s = height / (y1 - y0 + 2 * pad)
    w = (x1 - x0 + 2 * pad) * s
    back = f'<rect width="{f(w)}" height="{f(height)}" fill="{bg}"/>' if bg else ""
    return svg_doc(w, height, back + shapes(fill, s, (pad - x0) * s, (pad - y0) * s, word=True))


def tile_svg(size, radius_frac, mark_frac, bg=BLUE, fg=WHITE, full_bleed=False):
    """A square tile with the mark centred on its rings. mark_frac is R as a fraction of the size."""
    r = size * mark_frac
    rx = 0 if full_bleed else size * radius_frac
    back = f'<rect width="{size}" height="{size}" rx="{f(rx)}" fill="{bg}"/>'
    return svg_doc(size, size, back + shapes(fg, r, size / 2, size / 2))


def png(svg, path, size):
    import cairosvg
    cairosvg.svg2png(bytestring=svg.encode(), write_to=path, output_width=size, output_height=size)


# ---- Android vector drawables -------------------------------------------------------------------------
def vector(w_dp, h_dp, vw, vh, paths, comment):
    body = "".join(
        f'\n    <path android:fillColor="{c}"{" android:fillType=\"evenOdd\"" if eo else ""} android:pathData="{d}" />'
        for c, d, eo in paths)
    return (f"<!-- {comment} Generated by brand/build_logo.py. -->\n"
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{f(w_dp)}dp" android:height="{f(h_dp)}dp"\n'
            f'    android:viewportWidth="{f(vw)}" android:viewportHeight="{f(vh)}">{body}\n</vector>\n')


def android_mark(fill, height_dp, comment):
    x0, y0, x1, y1 = MARK_BOX
    s = 100.0
    vw, vh = (x1 - x0) * s, (y1 - y0) * s
    d = "".join(mark_paths(s, -x0 * s, -y0 * s, 2))
    return vector(height_dp * vw / vh, height_dp, vw, vh, [(fill, d, False)], comment)


def android_lockup(fill, height_dp, comment):
    x0, y0, x1, y1 = LOCKUP_BOX
    s = 100.0
    vw, vh = (x1 - x0) * s, (y1 - y0) * s
    ox, oy = -x0 * s, -y0 * s
    return vector(height_dp * vw / vh, height_dp, vw, vh,
                  [(fill, "".join(mark_paths(s, ox, oy, 2)), False), (fill, word_path(s, ox, oy, 2), True)], comment)


def android_tile_mark(size_dp, view, mark_r, fill, tile=None, tile_rx=0.0, comment=""):
    paths = []
    if tile:
        rx = tile_rx
        e = view
        paths.append((tile, f"M{f(rx)},0 H{f(e - rx)} A{f(rx)},{f(rx)} 0 0,1 {f(e)},{f(rx)} V{f(e - rx)} "
                            f"A{f(rx)},{f(rx)} 0 0,1 {f(e - rx)},{f(e)} H{f(rx)} A{f(rx)},{f(rx)} 0 0,1 0,{f(e - rx)} "
                            f"V{f(rx)} A{f(rx)},{f(rx)} 0 0,1 {f(rx)},0 Z", False))
    paths.append((fill, "".join(mark_paths(mark_r, view / 2, view / 2, 3)), False))
    return vector(size_dp, size_dp, view, view, paths, comment)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)


def main():
    # Brand files.
    for name, fill in (("", BLUE), ("-white", WHITE), ("-black", BLACK)):
        write(os.path.join(HERE, f"radar-mark{name}.svg"), mark_svg(fill))
        write(os.path.join(HERE, f"radar-lockup{name}.svg"), lockup_svg(fill))
    write(os.path.join(HERE, "radar-icon.svg"), tile_svg(512, 0.2237, 0.32))

    # Site icons: rounded tiles for "any", full bleed where the platform masks the icon itself.
    write(os.path.join(ROOT, "favicon.svg"), tile_svg(64, 0.2237, 0.34))
    png(tile_svg(512, 0.2237, 0.32), os.path.join(ROOT, "icon-192.png"), 192)
    png(tile_svg(512, 0.2237, 0.32), os.path.join(ROOT, "icon-512.png"), 512)
    png(tile_svg(512, 0, 0.29, full_bleed=True), os.path.join(ROOT, "icon-maskable-512.png"), 512)
    png(tile_svg(180, 0, 0.30, full_bleed=True), os.path.join(ROOT, "apple-touch-icon.png"), 180)
    banner = lockup_svg(WHITE, 400, bg=BLUE, pad=0.9)
    import cairosvg
    cairosvg.svg2png(bytestring=banner.encode(), write_to=os.path.join(HERE, "radar-banner.png"), output_height=800)

    # Android: the app's logo, the launcher icon and the widget header marks.
    dr = os.path.join(RES, "drawable")
    write(os.path.join(dr, "logo_mark.xml"), android_mark("#FFFFFF", 18, "The Radar mark, white; tint it where needed."))
    write(os.path.join(dr, "logo_lockup.xml"), android_lockup("#FFFFFF", 40, "The Radar mark and wordmark, white; tint it where needed."))
    write(os.path.join(dr, "ic_launcher_foreground.xml"),
          android_tile_mark(108, 108, 24, "#FFFFFF", comment="Launcher icon: the white mark in the adaptive icon's safe zone."))
    write(os.path.join(dr, "mark.xml"),
          android_tile_mark(16, 16, 7.6, "@color/white", comment="Widget header mark for blue and black bands."))
    write(os.path.join(dr, "mark_solid.xml"),
          android_tile_mark(16, 16, 5.6, "@color/white", tile="@color/blue", tile_rx=3.6,
                            comment="Widget header mark for white bands: the mark on a blue tile."))
    print("mark box", MARK_BOX, "word box", tuple(round(v, 4) for v in WORD_BOX), "lockup box",
          tuple(round(v, 4) for v in LOCKUP_BOX))


if __name__ == "__main__":
    main()
