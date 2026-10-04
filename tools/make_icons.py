#!/usr/bin/env python3
"""
Regenerate the complete Android launcher-icon set from a single source image.

    python3 tools/make_icons.py                       # built-in brand mark
    python3 tools/make_icons.py --source art/logo.png  # your own artwork
    python3 tools/make_icons.py --source art/logo.png --bg 064E3B --mark FFFFFF

Every raster this project ships is produced by this script, so nothing binary
ever has to be hand-edited (and nothing can be mangled by a text pipeline the
way the original WebP assets were -- see ERROR_AUDIT.md 1.1).

Emits, under app/src/main/res/:
    mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png            48/72/96/144/192
    mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png      circular, real alpha
    mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png 108dp adaptive layer
    mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
    values/ic_launcher_background.xml
    drawable/ic_launcher_background.xml  (vector gradient, kept for compat)
plus tools/playstore/play-store-icon.png at the 512x512 the Play Console requires.

Requires only the standard library.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool as P

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "app", "src", "main", "res")

MASTER = 1024            # render once at high res, area-downscale to every target
MARK_RATIO = 66.0 / 108.0   # Android adaptive-icon safe zone: 66dp inside 108dp

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def hexrgb(s: str):
    s = s.strip().lstrip("#")
    if len(s) == 8 and s.upper().startswith(("0X",)):
        s = s[2:]
    if len(s) == 8:                       # AARRGGBB
        s = s[2:]
    if len(s) != 6:
        raise SystemExit("--bg/--mark must be 6 hex digits, got %r" % s)
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def to_hex(c) -> str:
    """(r,g,b) tuple -> 'RRGGBB'."""
    return "%02X%02X%02X" % tuple(max(0, min(255, int(round(v)))) for v in c)


def darken(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def lighten(c, f):
    return tuple(max(0, min(255, int(round(v + (255 - v) * f)))) for v in c)


# --------------------------------------------------------------------------
# Brand mark: an eight-pointed star (rub el hizb), the same diamond geometry
# the app already draws in DecorativeDiamond / IslamicHeaderDecoration.
# --------------------------------------------------------------------------

def render_mark_layer(size: int, mark: tuple, bg: tuple) -> P.Image:
    """Eight-pointed star on transparency, tip-to-tip = MARK_RATIO of `size`."""
    _, _, rows = P.blank(size, size)
    c = size / 2.0
    # All eight tips sit on one circle of radius `diag`, so the star exactly
    # fills the adaptive-icon safe zone: 66dp of content inside a 108dp canvas.
    diag = size * MARK_RATIO / 2.0
    h = diag / (2 ** 0.5)                # half-side of the axis-aligned square
    edge = max(1.0, size / 512.0)        # ~1px feather at 512, scales with size

    # subtle drop shadow so the mark lifts off the background
    sh_dx = sh_dy = size * 0.006
    sh_rad = size * 0.012
    shadow = darken(bg, 0.55)

    for y in range(size):
        row = rows[y]
        py = (y + 0.5) - c
        apy = abs(py)
        for x in range(size):
            px = (x + 0.5) - c
            apx = abs(px)
            o = x * 4

            # --- shadow pass (same shape, offset down-right) ---
            d_sh = _star_sdf(abs(px - sh_dx), abs(py - sh_dy), h, diag)
            a_sh = _cover(d_sh, sh_rad) * 0.35
            if a_sh > 0:
                row[o] = shadow[0]
                row[o + 1] = shadow[1]
                row[o + 2] = shadow[2]
                row[o + 3] = int(round(a_sh * 255))

            # --- mark pass ---
            cov = _cover(_star_sdf(apx, apy, h, diag), edge)
            if cov <= 0:
                continue
            # gentle top-left -> bottom-right shading, ~10% range, so the mark
            # reads as one clean surface rather than a metal gradient
            t = (px + py) / (2.0 * diag) if diag else 0.0
            t = max(-1.0, min(1.0, t))
            shade = 1.0 - 0.10 * ((t + 1.0) / 2.0)      # 1.00 .. 0.90
            col = tuple(max(0, min(255, int(round(mark[i] * shade)))) for i in range(3))
            # composite over whatever the shadow already laid down
            sa = row[o + 3] / 255.0
            oa = cov + sa * (1 - cov)
            if oa > 0:
                for i in range(3):
                    row[o + i] = int(round((col[i] * cov + row[o + i] * sa * (1 - cov)) / oa))
            row[o + 3] = int(round(oa * 255))
    return size, size, rows


def _star_sdf(ax: float, ay: float, h: float, diag: float) -> float:
    """
    Signed Euclidean distance to the eight-pointed star, in the first quadrant
    (ax, ay >= 0). Negative inside. The star is the union of an axis-aligned
    square of half-side h and a 45-degree-rotated square of half-diagonal diag,
    so the union's SDF is the min of the two.
    """
    # axis-aligned square
    qx = max(ax - h, 0.0)
    qy = max(ay - h, 0.0)
    outside = (qx * qx + qy * qy) ** 0.5
    inside = min(max(ax - h, ay - h), 0.0)
    d_box = outside + inside
    # rotated square (diamond): |x| + |y| <= diag
    d_diamond = (ax + ay - diag) / (2 ** 0.5)
    return min(d_box, d_diamond)


def _cover(dist: float, feather: float) -> float:
    """Turn a signed distance into 0..1 coverage with an anti-aliased edge."""
    return max(0.0, min(1.0, -dist / max(feather, 1e-6) + 0.5))


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] * t + b[i] * (1 - t))) for i in range(3))


def render_bg_layer(size: int, bg: tuple) -> P.Image:
    """Full-bleed diagonal gradient background."""
    _, _, rows = P.blank(size, size)
    lo, hi = darken(bg, 0.72), lighten(bg, 0.16)
    for y in range(size):
        row = rows[y]
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1)) if size > 1 else 0.0
            col = _mix(hi, lo, t)
            o = x * 4
            row[o] = col[0]
            row[o + 1] = col[1]
            row[o + 2] = col[2]
            row[o + 3] = 255
    return size, size, rows


def render_source_mark(size: int, source_path: str) -> P.Image:
    """Use the caller's artwork as the mark, letterboxed into the safe zone."""
    src = P.decode_png_file(source_path)
    sw, sh, _ = src
    box = int(round(size * MARK_RATIO))
    return P.fit_inside(src, box, box, pad_frac=0.06)


def composite_mark_on_bg(mark: P.Image, bg: P.Image) -> P.Image:
    mw, mh, _ = mark
    bw, bh, _ = bg
    canvas = P.resize_area(bg, mw, mh) if (bw, bh) != (mw, mh) else bg
    return P.paste_center(canvas, mark)


# --------------------------------------------------------------------------
# Emitters
# --------------------------------------------------------------------------

def place_mark_on_canvas(mark: P.Image, size: int) -> P.Image:
    """Centre a mark layer on a transparent `size` x `size` canvas."""
    mw, mh, _ = mark
    scaled = P.resize_area(mark, size, size) if (mw, mh) != (size, size) else mark
    return P.paste_center(P.blank(size, size), scaled)


def write(path: str, img: P.Image) -> None:
    w, h, rows = img
    os.makedirs(os.path.dirname(path), exist_ok=True)
    P.write_png(path, w, h, rows)
    print("    %-58s %4dx%-4d %8d B"
          % (os.path.relpath(path, REPO), w, h, os.path.getsize(path)))


XML_ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""

XML_BG_COLOR = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Flat fallback for the adaptive-icon backdrop; the live background is
         the gradient in drawable/ic_launcher_background.xml. Both are kept in
         sync with tools/make_icons.py --bg. -->
    <color name="ic_launcher_background">#{bg}</color>
</resources>
"""

XML_BG_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:type="linear">
                <item android:offset="0.0" android:color="#FF{hi}" />
                <item android:offset="1.0" android:color="#FF{lo}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""


def write_text(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("    %-58s %s" % (os.path.relpath(path, REPO), "(xml)"))


def remove_corrupt_legacy() -> None:
    """Delete the mangled WebP assets this script replaces."""
    victims = [os.path.join(RES, "drawable", "small_deed.webp")]
    for d in DENSITIES:
        base = os.path.join(RES, "mipmap-%s" % d)
        for n in ("ic_launcher.webp", "ic_launcher_round.webp"):
            victims.append(os.path.join(base, n))
    gone = 0
    for v in victims:
        if os.path.exists(v):
            os.remove(v)
            gone += 1
    if gone:
        print("  removed %d corrupted WebP asset(s)" % gone)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", help="path to a source PNG (your real logo). "
                                     "Omit to render the built-in brand mark.")
    ap.add_argument("--bg", default="064E3B", help="background hex, default 064E3B (emerald-900)")
    ap.add_argument("--mark", default="FFFFFF", help="mark hex, default FFFFFF")
    ap.add_argument("--legacy-shape", choices=("rounded", "circle", "square"),
                    default="rounded", help="mask for the pre-API-26 icon")
    ap.add_argument("--no-clean", action="store_true",
                    help="do not delete the old corrupt WebP assets")
    args = ap.parse_args()

    bg = hexrgb(args.bg)
    mark = hexrgb(args.mark)

    print("Rendering master layers at %dx%d ..." % (MASTER, MASTER))
    bg_layer = render_bg_layer(MASTER, bg)
    if args.source:
        if not os.path.exists(args.source):
            raise SystemExit("source not found: %s" % args.source)
        print("  mark layer <- %s" % args.source)
        mark_layer_src = P.decode_png_file(args.source)
        box = int(round(MASTER * MARK_RATIO))
        fitted = P.fit_inside(mark_layer_src, box, box, pad_frac=0.06)
        mark_layer = P.paste_center(P.blank(MASTER, MASTER), fitted)
    else:
        print("  mark layer <- built-in eight-pointed star")
        mark_layer = render_mark_layer(MASTER, mark, bg)

    composite = composite_mark_on_bg(mark_layer, bg_layer)

    if not args.no_clean:
        remove_corrupt_legacy()

    print("\nLegacy launcher icons (API < 26)")
    for name, density in DENSITIES.items():
        size = int(round(48 * density))
        base = P.resize_area(composite, size, size)
        if args.legacy_shape == "rounded":
            base = P.apply_rounded_mask(base, radius_frac=0.18, feather=max(1.0, size / 192.0))
        elif args.legacy_shape == "circle":
            base = P.apply_circle_mask(base, feather=max(1.0, size / 192.0))
        d = os.path.join(RES, "mipmap-%s" % name)
        write(os.path.join(d, "ic_launcher.png"), base)
        write(os.path.join(d, "ic_launcher_round.png"),
              P.apply_circle_mask(P.resize_area(composite, size, size),
                                  feather=max(1.0, size / 192.0)))

    print("\nAdaptive-icon foreground (108dp, %d%% safe zone)" % round(MARK_RATIO * 100))
    for name, density in DENSITIES.items():
        size = int(round(108 * density))
        fg = P.resize_area(mark_layer, size, size)
        write(os.path.join(RES, "mipmap-%s" % name, "ic_launcher_foreground.png"), fg)

    print("\nXML resources")
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        write_text(os.path.join(RES, "mipmap-anydpi-v26", name), XML_ADAPTIVE)
    write_text(os.path.join(RES, "values", "ic_launcher_background.xml"),
               XML_BG_COLOR.format(bg=to_hex(bg)))
    write_text(os.path.join(RES, "drawable", "ic_launcher_background.xml"),
               XML_BG_VECTOR.format(hi=to_hex(lighten(bg, 0.16)), lo=to_hex(darken(bg, 0.72))))

    print("\nStore listing asset")
    out = os.path.join(REPO, "tools", "playstore")
    write(os.path.join(out, "play-store-icon.png"), P.resize_area(composite, 512, 512))
    write(os.path.join(out, "play-store-icon-round.png"),
          P.apply_circle_mask(P.resize_area(composite, 512, 512), feather=2.0))

    print("\nDone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
