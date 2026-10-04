#!/usr/bin/env python3
"""
Minimal, dependency-free PNG codec + image helpers.

The repo previously shipped binary assets that had been mangled by a text
pipeline (see ERROR_AUDIT.md 1.1). This module exists so that every raster in
the project can be regenerated deterministically from source artwork without
requiring Pillow, ImageMagick or a JDK in the build environment.

Supports 8-bit PNG colour types 0/2/4/6, non-interlaced.
"""

from __future__ import annotations

import struct
import zlib
from typing import List, Tuple

Pixel = bytearray          # one row, RGBA, 4 bytes per pixel
Image = Tuple[int, int, List[Pixel]]   # (width, height, rows)

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


# --------------------------------------------------------------------------
# Decoding
# --------------------------------------------------------------------------

def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def decode_png(data: bytes) -> Image:
    """Decode an 8-bit non-interlaced PNG into (w, h, [RGBA rows])."""
    if data[:8] != PNG_MAGIC:
        raise ValueError(
            "not a PNG file (magic %r). If this came from git, the blob may be "
            "corrupted -- see ERROR_AUDIT.md 1.1." % data[:8].hex(" ")
        )

    pos = 8
    ihdr = None
    idat = bytearray()
    palette = None
    trns = None

    while pos + 8 <= len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        crc_stored = struct.unpack(">I", data[pos + 8 + length:pos + 12 + length])[0]
        crc_calc = zlib.crc32(data[pos + 4:pos + 8 + length]) & 0xFFFFFFFF
        if crc_stored != crc_calc:
            raise ValueError("corrupt PNG: bad CRC in %s chunk" % ctype.decode("latin1"))
        pos += 12 + length

        if ctype == b"IHDR":
            w, h, depth, colour, comp, filt, interlace = struct.unpack(">IIBBBBB", chunk)
            ihdr = (w, h, depth, colour, comp, filt, interlace)
        elif ctype == b"PLTE":
            palette = chunk
        elif ctype == b"tRNS":
            trns = chunk
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"IEND":
            break

    if ihdr is None:
        raise ValueError("PNG has no IHDR chunk")

    w, h, depth, colour, comp, filt, interlace = ihdr
    if depth != 8:
        raise ValueError("only 8-bit PNGs are supported (got bit depth %d)" % depth)
    if interlace:
        raise ValueError("interlaced PNGs are not supported")
    if comp or filt:
        raise ValueError("unsupported PNG compression/filter method")

    src_ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    bpp = src_ch                      # bytes per pixel (depth == 8)
    stride = w * bpp

    raw = zlib.decompress(bytes(idat))
    if len(raw) < h * (stride + 1):
        raise ValueError("truncated IDAT: expected %d bytes, got %d"
                         % (h * (stride + 1), len(raw)))

    # Undo per-scanline filters.
    prev = bytearray(stride)
    out_rows: List[bytearray] = []
    for y in range(h):
        base = y * (stride + 1)
        ftype = raw[base]
        line = bytearray(raw[base + 1:base + 1 + stride])
        if ftype == 0:
            pass
        elif ftype == 1:                                   # Sub
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ftype == 2:                                   # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:                                   # Average
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:                                   # Paeth
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                c = prev[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + _paeth(a, prev[i], c)) & 0xFF
        else:
            raise ValueError("unknown PNG filter type %d on row %d" % (ftype, y))
        out_rows.append(line)
        prev = line

    # Normalise everything to RGBA.
    rows: List[Pixel] = []
    for line in out_rows:
        if colour == 6:
            rows.append(bytearray(line))
        elif colour == 2:
            r = bytearray(w * 4)
            for x in range(w):
                r[x * 4:x * 4 + 3] = line[x * 3:x * 3 + 3]
                r[x * 4 + 3] = 255
            rows.append(r)
        elif colour == 4:
            r = bytearray(w * 4)
            for x in range(w):
                g = line[x * 2]
                r[x * 4] = r[x * 4 + 1] = r[x * 4 + 2] = g
                r[x * 4 + 3] = line[x * 2 + 1]
            rows.append(r)
        elif colour == 0:
            r = bytearray(w * 4)
            for x in range(w):
                g = line[x]
                r[x * 4] = r[x * 4 + 1] = r[x * 4 + 2] = g
                r[x * 4 + 3] = 255
            rows.append(r)
        elif colour == 3:
            if palette is None:
                raise ValueError("paletted PNG without PLTE chunk")
            alpha = 255
            r = bytearray(w * 4)
            for x in range(w):
                idx = line[x]
                r[x * 4:x * 4 + 3] = palette[idx * 3:idx * 3 + 3]
                if trns and idx < len(trns):
                    alpha = trns[idx]
                r[x * 4 + 3] = alpha if (trns and idx < len(trns)) else 255
            rows.append(r)
        else:
            raise ValueError("unsupported PNG colour type %d" % colour)
    return w, h, rows


def decode_png_file(path: str) -> Image:
    with open(path, "rb") as fh:
        return decode_png(fh.read())


# --------------------------------------------------------------------------
# Encoding
# --------------------------------------------------------------------------

def encode_png(width: int, height: int, rows: List[Pixel], level: int = 9) -> bytes:
    """Encode RGBA rows to PNG bytes (8-bit, colour type 6, non-interlaced)."""
    if len(rows) != height:
        raise ValueError("row count %d != height %d" % (len(rows), height))

    raw = bytearray()
    for row in rows:
        if len(row) != width * 4:
            raise ValueError("row length %d != width*4 %d" % (len(row), width * 4))
        raw.append(0)                     # filter type 0 (None) -- zlib handles the rest
        raw += row

    def chunk(ctype: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + ctype + payload
                + struct.pack(">I", zlib.crc32(ctype + payload) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (PNG_MAGIC
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), level))
            + chunk(b"IEND", b""))


def write_png(path: str, width: int, height: int, rows: List[Pixel]) -> None:
    data = encode_png(width, height, rows)
    with open(path, "wb") as fh:
        fh.write(data)
    # Fail loudly rather than committing another mangled asset.
    with open(path, "rb") as fh:
        verify = fh.read()
    if verify != data:
        raise IOError("round-trip verification failed for %s" % path)
    decode_png(verify)


# --------------------------------------------------------------------------
# Transforms
# --------------------------------------------------------------------------

def blank(width: int, height: int, rgba: Tuple[int, int, int, int] = (0, 0, 0, 0)) -> Image:
    row = bytearray()
    for _ in range(width):
        row += bytes(rgba)
    return width, height, [bytearray(row) for _ in range(height)]


def resize_area(src: Image, dw: int, dh: int) -> Image:
    """Area-average resample. Correct filter for icon downscaling (no aliasing)."""
    sw, sh, rows = src
    if (sw, sh) == (dw, dh):
        return sw, sh, [bytearray(r) for r in rows]

    out: List[Pixel] = []
    for dy in range(dh):
        y0 = dy * sh / dh
        y1 = (dy + 1) * sh / dh
        iy0, iy1 = int(y0), max(int(y0) + 1, int(round(y1)))
        iy1 = min(iy1, sh)
        dst = bytearray(dw * 4)
        for dx in range(dw):
            x0 = dx * sw / dw
            x1 = (dx + 1) * sw / dw
            ix0, ix1 = int(x0), max(int(x0) + 1, int(round(x1)))
            ix1 = min(ix1, sw)

            # Accumulate alpha straight and colour premultiplied by alpha, both
            # weighted by how much of each source pixel this destination pixel
            # covers. Premultiplying first is what stops a fully transparent
            # pixel's RGB from bleeding a coloured fringe into resized edges.
            r = g = b = 0.0     # sum of channel * (alpha/255) * weight
            a = 0.0             # sum of alpha * weight        (alpha in 0..255)
            wsum = 0.0          # sum of weight
            for sy in range(iy0, iy1):
                # vertical coverage of this source row
                wy = min(sy + 1, y1) - max(sy, y0)
                if wy <= 0:
                    continue
                srow = rows[sy]
                for sx in range(ix0, ix1):
                    wx = min(sx + 1, x1) - max(sx, x0)
                    if wx <= 0:
                        continue
                    wgt = wx * wy
                    off = sx * 4
                    sa = srow[off + 3] / 255.0
                    r += srow[off] * sa * wgt
                    g += srow[off + 1] * sa * wgt
                    b += srow[off + 2] * sa * wgt
                    a += srow[off + 3] * wgt
                    wsum += wgt
            o = dx * 4
            if wsum > 0:
                dst[o + 3] = max(0, min(255, int(round(a / wsum))))
                if a > 0:
                    # un-premultiply: colour = 255 * sum(c*alpha*w) / sum(alpha*w)
                    dst[o] = max(0, min(255, int(round(255.0 * r / a))))
                    dst[o + 1] = max(0, min(255, int(round(255.0 * g / a))))
                    dst[o + 2] = max(0, min(255, int(round(255.0 * b / a))))
        out.append(dst)
    return dw, dh, out


def crop_center(src: Image, cw: int, ch: int) -> Image:
    sw, sh, rows = src
    if cw > sw or ch > sh:
        raise ValueError("crop %dx%d larger than source %dx%d" % (cw, ch, sw, sh))
    x0 = (sw - cw) // 2
    y0 = (sh - ch) // 2
    out = [bytearray(r[x0 * 4:(x0 + cw) * 4]) for r in rows[y0:y0 + ch]]
    return cw, ch, out


def paste_center(base: Image, overlay: Image) -> Image:
    """Composite `overlay` (RGBA) onto the centre of `base`."""
    bw, bh, brows = base
    ow, oh, orows = overlay
    x0 = (bw - ow) // 2
    y0 = (bh - oh) // 2
    out = [bytearray(r) for r in brows]
    for y in range(oh):
        ty = y0 + y
        if ty < 0 or ty >= bh:
            continue
        brow, orow = out[ty], orows[y]
        for x in range(ow):
            tx = x0 + x
            if tx < 0 or tx >= bw:
                continue
            so = x * 4
            sa = orow[so + 3] / 255.0
            if sa == 0:
                continue
            do = tx * 4
            da = brow[do + 3] / 255.0
            oa = sa + da * (1 - sa)
            if oa == 0:
                continue
            for c in range(3):
                brow[do + c] = int(round(
                    (orow[so + c] * sa + brow[do + c] * da * (1 - sa)) / oa))
            brow[do + 3] = int(round(oa * 255))
    return bw, bh, out


def apply_circle_mask(src: Image, feather: float = 1.0) -> Image:
    """Alpha-mask to a centred circle, with an anti-aliased edge."""
    w, h, rows = src
    cx, cy, rad = w / 2.0, h / 2.0, min(w, h) / 2.0
    out = []
    for y in range(h):
        row = bytearray(rows[y])
        dy = (y + 0.5) - cy
        for x in range(w):
            dx = (x + 0.5) - cx
            d = (dx * dx + dy * dy) ** 0.5
            cov = max(0.0, min(1.0, (rad - d) / max(feather, 1e-6) + 0.5))
            o = x * 4
            row[o + 3] = int(round(row[o + 3] * cov))
        out.append(row)
    return w, h, out


def apply_rounded_mask(src: Image, radius_frac: float = 0.18,
                       feather: float = 1.0) -> Image:
    """Alpha-mask to a rounded square (legacy pre-API-26 launcher shape)."""
    w, h, rows = src
    r = min(w, h) * radius_frac
    out = []
    for y in range(h):
        row = bytearray(rows[y])
        py = y + 0.5
        for x in range(w):
            px = x + 0.5
            # distance to the rounded-rect interior
            dx = max(r - px, px - (w - r), 0.0)
            dy = max(r - py, py - (h - r), 0.0)
            d = (dx * dx + dy * dy) ** 0.5
            cov = 1.0 if d <= 0 else max(0.0, min(1.0, (r - d) / max(feather, 1e-6) + 0.5))
            o = x * 4
            row[o + 3] = int(round(row[o + 3] * cov))
        out.append(row)
    return w, h, out


def fit_inside(src: Image, bw: int, bh: int, pad_frac: float = 0.0) -> Image:
    """Scale `src` to fit a (bw x bh) box, keeping aspect, on a transparent canvas."""
    sw, sh, _ = src
    avail_w = bw * (1.0 - pad_frac)
    avail_h = bh * (1.0 - pad_frac)
    scale = min(avail_w / sw, avail_h / sh)
    nw, nh = max(1, int(round(sw * scale))), max(1, int(round(sh * scale)))
    scaled = resize_area(src, nw, nh)
    canvas = blank(bw, bh)
    return paste_center(canvas, scaled)
