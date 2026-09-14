#!/usr/bin/env python3
"""Pixel studio for Manhunt: the hunter's 64x64 player-UV skin and the mod icon.

Stdlib only. Deterministic: the same seed paints the same scars every run.
Run: python3 tools/make_art.py
"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/manhunt"

COAT = (38, 36, 42, 255)
COAT_DARK = (26, 24, 30, 255)
COAT_LIGHT = (52, 49, 58, 255)
HOOD = (30, 28, 34, 255)
SCARF = (142, 32, 34, 255)
SCARF_DARK = (104, 22, 24, 255)
SKIN = (196, 158, 128, 255)
SKIN_SHADE = (168, 132, 104, 255)
EYE = (232, 214, 120, 255)
STRAP = (96, 70, 44, 255)
STRAP_DARK = (70, 50, 32, 255)
PANTS = (64, 62, 70, 255)
PANTS_DARK = (46, 44, 52, 255)
BOOT = (34, 30, 28, 255)


class Canvas:
    def __init__(self, width: int, height: int):
        self.width = width
        self.height = height
        self.px = [(0, 0, 0, 0)] * (width * height)

    def put(self, x: int, y: int, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.px[y * self.width + x] = color

    def rect(self, x0: int, y0: int, x1: int, y1: int, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, color)

    def hline(self, x0: int, x1: int, y: int, color):
        self.rect(x0, y, x1, y, color)

    def vline(self, x: int, y0: int, y1: int, color):
        self.rect(x, y0, x, y1, color)

    def noise(self, x0: int, y0: int, x1: int, y1: int, colors, seed: int):
        state = seed
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                state = (state * 1103515245 + 12345) & 0x7FFFFFFF
                self.put(x, y, colors[(state >> 8) % len(colors)])

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(b"\x00" + b"".join(struct.pack("<4B", *c) for c in row)
                       for row in (self.px[y * self.width:(y + 1) * self.width]
                                   for y in range(self.height)))
        def chunk(tag, data):
            block = tag + data
            return struct.pack(">I", len(data)) + block + struct.pack(">I", zlib.crc32(block))
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


def box(canvas: Canvas, x: int, y: int, w: int, h: int, base, shade, seed: int):
    """Fill one UV face with cloth grain."""
    canvas.noise(x, y, x + w - 1, y + h - 1, [base, base, shade], seed)


def hunter_skin() -> Canvas:
    c = Canvas(64, 64)
    # ---- head: hooded, scarf across the mouth, pale eyes -------------------
    box(c, 8, 0, 8, 8, HOOD, COAT_DARK, 11)          # top
    box(c, 16, 0, 8, 8, HOOD, COAT_DARK, 12)         # bottom
    box(c, 0, 8, 4, 8, HOOD, COAT_DARK, 13)          # right
    box(c, 8, 8, 8, 8, SKIN, SKIN_SHADE, 14)         # face
    box(c, 16, 8, 4, 8, HOOD, COAT_DARK, 15)         # left
    box(c, 24, 8, 8, 8, HOOD, COAT_DARK, 16)         # back
    c.rect(8, 8, 15, 9, HOOD)                        # hood brim over brow
    c.put(10, 11, EYE)
    c.put(13, 11, EYE)
    c.hline(8, 15, 14, SCARF)                        # scarf over the mouth
    c.hline(8, 15, 15, SCARF_DARK)
    c.rect(0, 10, 3, 15, SCARF_DARK)                 # scarf wraps the sides
    c.rect(16, 10, 19, 15, SCARF_DARK)
    c.rect(24, 12, 31, 15, SCARF_DARK)               # and the back of the neck
    # ---- hat layer: the hood itself ----------------------------------------
    box(c, 40, 0, 8, 8, HOOD, COAT_DARK, 21)
    box(c, 48, 0, 8, 8, HOOD, COAT_DARK, 22)
    c.rect(32, 8, 35, 15, HOOD)
    c.rect(40, 8, 47, 9, HOOD)                       # brim shadow
    c.put(40, 8, (0, 0, 0, 0))
    c.put(47, 8, (0, 0, 0, 0))
    c.rect(48, 8, 51, 15, HOOD)
    c.rect(56, 8, 63, 15, HOOD)
    # ---- body: coat, baldric, scarf knot ------------------------------------
    box(c, 20, 16, 8, 4, COAT, COAT_DARK, 31)
    box(c, 28, 16, 8, 4, COAT, COAT_DARK, 32)
    box(c, 16, 20, 4, 12, COAT, COAT_DARK, 33)
    box(c, 20, 20, 8, 12, COAT, COAT_DARK, 34)
    box(c, 28, 20, 4, 12, COAT, COAT_DARK, 35)
    box(c, 32, 20, 8, 12, COAT, COAT_DARK, 36)
    c.hline(20, 27, 20, SCARF)                       # scarf ends on the chest
    c.put(23, 21, SCARF_DARK)
    c.put(24, 21, SCARF_DARK)
    for i in range(9):                               # baldric strap
        c.put(21 + i % 8, 22 + i, STRAP)
        c.put(22 + i % 8, 22 + i, STRAP_DARK)
    c.hline(20, 27, 29, STRAP_DARK)                  # belt
    c.put(23, 29, EYE)                               # buckle
    c.rect(32, 22, 39, 23, COAT_LIGHT)               # back yoke
    # ---- arms: coat sleeves, strap cuffs ------------------------------------
    for ax, ay in ((40, 16), (32, 48)):
        box(c, ax + 4, ay, 4, 4, COAT, COAT_DARK, 41 + ax)
        box(c, ax + 8, ay, 4, 4, COAT, COAT_DARK, 42 + ax)
        box(c, ax, ay + 4, 4, 12, COAT, COAT_DARK, 43 + ax)
        box(c, ax + 4, ay + 4, 4, 12, COAT, COAT_DARK, 44 + ax)
        box(c, ax + 8, ay + 4, 4, 12, COAT, COAT_DARK, 45 + ax)
        box(c, ax + 12, ay + 4, 4, 12, COAT, COAT_DARK, 46 + ax)
        c.hline(ax + 4, ax + 7, ay + 13, STRAP)      # cuff
        c.hline(ax, ax + 3, ay + 13, STRAP)
        c.hline(ax + 8, ax + 11, ay + 13, STRAP)
        c.hline(ax + 12, ax + 15, ay + 13, STRAP)
        c.rect(ax + 4, ay + 14, ax + 7, ay + 15, SKIN)   # hands
        c.rect(ax, ay + 14, ax + 3, ay + 15, SKIN_SHADE)
        c.rect(ax + 8, ay + 14, ax + 11, ay + 15, SKIN)
        c.rect(ax + 12, ay + 14, ax + 15, ay + 15, SKIN_SHADE)
    # ---- legs: trousers into boots ------------------------------------------
    for lx, ly in ((0, 16), (16, 48)):
        box(c, lx + 4, ly, 4, 4, PANTS, PANTS_DARK, 51 + lx)
        box(c, lx + 8, ly, 4, 4, PANTS, PANTS_DARK, 52 + lx)
        box(c, lx, ly + 4, 4, 12, PANTS, PANTS_DARK, 53 + lx)
        box(c, lx + 4, ly + 4, 4, 12, PANTS, PANTS_DARK, 54 + lx)
        box(c, lx + 8, ly + 4, 4, 12, PANTS, PANTS_DARK, 55 + lx)
        box(c, lx + 12, ly + 4, 4, 12, PANTS, PANTS_DARK, 56 + lx)
        c.rect(lx, ly + 10, lx + 15, ly + 15, BOOT)  # boots
        c.hline(lx + 4, lx + 7, ly + 10, BOOT)
    return c


def icon() -> Canvas:
    c = Canvas(128, 128)
    c.rect(0, 0, 127, 127, (18, 16, 20, 255))
    for i in range(128):                             # vignette grain
        for j in range(128):
            if (i * 7 + j * 13) % 29 == 0:
                c.put(i, j, (24, 22, 27, 255))
    # hooded silhouette
    c.rect(40, 34, 87, 96, HOOD)
    c.rect(44, 30, 83, 40, HOOD)
    c.rect(36, 44, 43, 92, COAT_DARK)
    c.rect(84, 44, 91, 92, COAT_DARK)
    c.rect(52, 52, 75, 74, SKIN_SHADE)               # face in shadow
    c.rect(52, 52, 75, 56, HOOD)
    c.put(58, 62, EYE)
    c.put(59, 62, EYE)
    c.put(68, 62, EYE)
    c.put(69, 62, EYE)
    c.rect(52, 68, 75, 74, SCARF_DARK)
    c.rect(40, 92, 87, 96, COAT_DARK)
    # crosshair over him
    red = (196, 44, 46, 255)
    import math
    for angle in range(0, 360):
        rad = math.radians(angle)
        c.put(64 + round(52 * math.cos(rad)), 64 + round(52 * math.sin(rad)), red)
        c.put(64 + round(49 * math.cos(rad)), 64 + round(49 * math.sin(rad)), red)
    c.rect(62, 4, 65, 26, red)
    c.rect(62, 102, 65, 124, red)
    c.rect(4, 62, 26, 65, red)
    c.rect(102, 62, 124, 65, red)
    return c


def main():
    hunter_skin().save(OUT / "textures/entity/hunter.png")
    icon().save(OUT / "icon.png")
    print("manhunt art forged: hunter skin + icon")


if __name__ == "__main__":
    main()
