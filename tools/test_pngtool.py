#!/usr/bin/env python3
"""Self-test for tools/pngtool.py -- run with: python3 tools/test_pngtool.py"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pngtool as P

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
failures = []


def check(label, cond, extra=""):
    print(("  PASS  " if cond else "  FAIL  ") + label + (("  " + extra) if extra else ""))
    if not cond:
        failures.append(label)


def chunk(t, p):
    return (struct.pack(">I", len(p)) + t + p
            + struct.pack(">I", zlib.crc32(t + p) & 0xFFFFFFFF))


print("1. encode -> decode round trip (RGBA, gradients + hard edges + alpha ramp)")
W = H = 64
_, _, rows = P.blank(W, H)
for y in range(H):
    for x in range(W):
        o = x * 4
        rows[y][o] = (x * 4) & 0xFF
        rows[y][o + 1] = (y * 4) & 0xFF
        rows[y][o + 2] = 128 if (x // 8 + y // 8) % 2 else 17
        rows[y][o + 3] = min(255, (x + y) * 2)
data = P.encode_png(W, H, rows)
w2, h2, rows2 = P.decode_png(data)
check("pixel-exact", (w2, h2) == (W, H) and all(bytes(a) == bytes(b) for a, b in zip(rows, rows2)),
      "%d PNG bytes" % len(data))

print("2. all five PNG scanline filter types")
def encode_filtered(width, height, rows_, ftype):
    raw = bytearray()
    prev = bytearray(width * 4)
    for row in rows_:
        raw.append(ftype)
        line = bytearray(row)
        if ftype == 1:
            for i in range(4, width * 4):
                line[i] = (row[i] - row[i - 4]) & 0xFF
        elif ftype == 2:
            for i in range(width * 4):
                line[i] = (row[i] - prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(width * 4):
                a = row[i - 4] if i >= 4 else 0
                line[i] = (row[i] - ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(width * 4):
                a = row[i - 4] if i >= 4 else 0
                c = prev[i - 4] if i >= 4 else 0
                line[i] = (row[i] - P._paeth(a, prev[i], c)) & 0xFF
        raw += line
        prev = bytearray(row)
    return (P.PNG_MAGIC
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))

for ft, nm in enumerate(["None", "Sub", "Up", "Average", "Paeth"]):
    _, _, r2 = P.decode_png(encode_filtered(W, H, rows, ft))
    check("filter %d (%s)" % (ft, nm), all(bytes(a) == bytes(b) for a, b in zip(rows, r2)))

print("3. colour types other than RGBA")
def enc_ct(width, height, ct, raw_rows):
    raw = bytearray()
    for r in raw_rows:
        raw.append(0)
        raw += r
    return (P.PNG_MAGIC
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, ct, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))

_, _, rr = P.decode_png(enc_ct(8, 8, 0, [bytearray([(x * 8) & 0xFF] * 8) for x in range(1)] * 8))
check("ct=0 grayscale -> RGBA", list(rr[0][0:4]) == [0, 0, 0, 255])
_, _, rr = P.decode_png(enc_ct(8, 8, 2, [bytearray([9, 64, 200] * 8) for _ in range(8)]))
check("ct=2 truecolor -> RGBA", list(rr[0][0:4]) == [9, 64, 200, 255])
_, _, rr = P.decode_png(enc_ct(8, 8, 4, [bytearray([9, 128] * 8) for _ in range(8)]))
check("ct=4 gray+alpha -> RGBA", list(rr[0][0:4]) == [9, 9, 9, 128])
pal = bytes([255, 0, 0, 0, 255, 0])
rawp = P.PNG_MAGIC + chunk(b"IHDR", struct.pack(">IIBBBBB", 4, 4, 8, 3, 0, 0, 0)) \
    + chunk(b"PLTE", pal) + chunk(b"tRNS", bytes([128, 255])) \
    + chunk(b"IDAT", zlib.compress(bytes(sum([[0] + [0, 1] * 2] * 4, [])), 9)) + chunk(b"IEND", b"")
_, _, rr = P.decode_png(rawp)
check("ct=3 paletted + tRNS -> RGBA", list(rr[0][0:4]) == [255, 0, 0, 128] and list(rr[0][4:8]) == [0, 255, 0, 255])

print("4. area resampling")
_, _, crows = P.blank(64, 64, (200, 100, 50, 255))
for target, nm in [(16, "downscale 64->16"), (128, "upscale 64->128"), (64, "identity 64->64")]:
    w, h, rr = P.resize_area((64, 64, crows), target, target)
    flat = all(rr[y][x * 4:x * 4 + 4] == bytearray([200, 100, 50, 255])
               for y in range(h) for x in range(w))
    check("flat colour preserved on " + nm, flat and (w, h) == (target, target))

print("5. masks")
_, _, srows = P.blank(32, 32, (10, 200, 90, 255))
w, h, rr = P.apply_circle_mask((32, 32, srows))
check("circle mask: corner transparent, centre opaque", rr[0][3] == 0 and rr[16][16 * 4 + 3] == 255)
w, h, rr = P.apply_rounded_mask((32, 32, srows))
check("rounded mask: corner transparent, centre opaque", rr[0][3] == 0 and rr[16][16 * 4 + 3] == 255)

print("6. fit_inside preserves aspect and pads")
_, _, tall = P.blank(20, 40, (1, 2, 3, 255))
w, h, rr = P.fit_inside((20, 40, tall), 100, 100, pad_frac=0.0)
opaque = [(y, x) for y in range(h) for x in range(w) if rr[y][x * 4 + 3] > 0]
xs = [t[1] for t in opaque]
ys = [t[0] for t in opaque]
check("centred, correct aspect, transparent surround",
      (w, h) == (100, 100) and (max(xs) - min(xs)) == 49 and (max(ys) - min(ys)) == 99
      and rr[0][0 * 4 + 3] == 0, "bbox %dx%d at (%d,%d)"
      % (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, min(xs), min(ys)))

print("7. write_png verifies its own output on disk")
tmp = os.path.join(ROOT, "tools", "_rt.png")
P.write_png(tmp, W, H, rows)
w, h, r2 = P.decode_png_file(tmp)
check("written file decodes identical", all(bytes(a) == bytes(b) for a, b in zip(rows, r2)))
os.remove(tmp)

print("8. corrupt repo assets are rejected loudly, not silently accepted")
for rel in ["app/src/test/screenshots/greeting.png", "app/src/main/res/drawable/small_deed.webp"]:
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        print("  SKIP  %s (already removed)" % rel)
        continue
    try:
        P.decode_png_file(p)
        check("rejects " + os.path.basename(rel), False, "was ACCEPTED")
    except Exception as e:
        check("rejects " + os.path.basename(rel), True, type(e).__name__)

print("9. truncated / bad-CRC PNGs raise rather than returning garbage")
try:
    P.decode_png(data[:len(data) // 2])
    check("truncated PNG raises", False)
except Exception:
    check("truncated PNG raises", True)
bad = bytearray(data)
bad[40] ^= 0xFF
try:
    P.decode_png(bytes(bad))
    check("bad-CRC PNG raises", False)
except ValueError as e:
    check("bad-CRC PNG raises", True, str(e)[:40])

print()
print("FAILURES: %d" % len(failures))
for f in failures:
    print("  - " + f)
sys.exit(1 if failures else 0)
