"""Regenerate every pixel-art asset for Rival Realms.

The old generator drew flat rectangles. This one is a real (if small) pixel
studio: deterministic noise, shading passes, outlines and palettes, plus
entity sheets whose regions are painted to match the exact UV layout of the
code-built airship and sailing-ship models.

Standard library only, so it runs in CI alongside the bughunt preflight.
"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/rivalrealms"
TEX = ROOT / "textures"

# ---------------------------------------------------------------- png plumbing


def png(path: Path, width: int, height: int, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels[y * width + x])

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def canvas(width, height, color=(0, 0, 0, 0)):
    return [color] * (width * height)


def px(p, w, x, y, c):
    if 0 <= x < w and 0 <= y < len(p) // w:
        p[y * w + x] = c


def get(p, w, x, y):
    h = len(p) // w
    if 0 <= x < w and 0 <= y < h:
        return p[y * w + x]
    return (0, 0, 0, 0)


def rect(p, w, x0, y0, x1, y1, color):
    h = len(p) // w
    for y in range(max(0, y0), min(y1, h)):
        for x in range(max(0, x0), min(x1, w)):
            p[y * w + x] = color


def hline(p, w, x0, x1, y, color):
    rect(p, w, x0, y, x1, y + 1, color)


def vline(p, w, x, y0, y1, color):
    rect(p, w, x, y0, x + 1, y1, color)


def disc(p, w, cx, cy, r, color):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r * 0.6:
                px(p, w, x, y, color)


def jitter(p, w, seed, amount, alpha_only=False):
    """Deterministic per-pixel brightness noise for texture grain."""
    state = seed & 0xffffffff

    def nxt():
        nonlocal state
        state = (1103515245 * state + 12345) & 0x7fffffff
        return state

    h = len(p) // w
    for y in range(h):
        for x in range(w):
            c = p[y * w + x]
            if c[3] == 0:
                continue
            d = (nxt() % (2 * amount + 1)) - amount
            r = max(0, min(255, c[0] + d))
            g = max(0, min(255, c[1] + d))
            b = max(0, min(255, c[2] + d))
            p[y * w + x] = (r, g, b, c[3])


def darken(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


def brighten(c, f):
    return (min(255, int(c[0] * f + 12)), min(255, int(c[1] * f + 12)),
            min(255, int(c[2] * f + 12)), c[3])


def outline(p, w, color, alpha_threshold=64):
    h = len(p) // w
    src = list(p)
    for y in range(h):
        for x in range(w):
            if src[y * w + x][3] > alpha_threshold:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[ny * w + nx][3] > alpha_threshold:
                    p[y * w + x] = color
                    break


# ----------------------------------------------------------------- palettes

BRICK = {
    "base": (47, 49, 56, 255), "mortar": (28, 29, 35, 255),
    "hi": (60, 63, 71, 255), "gold": (197, 158, 71, 255),
}
STONE = {
    "base": (138, 141, 146, 255), "mortar": (95, 99, 104, 255),
    "hi": (158, 161, 166, 255),
}
TILES = {
    "a": (42, 45, 51, 255), "b": (52, 55, 62, 255),
    "hi": (66, 70, 78, 255), "mortar": (30, 32, 37, 255),
}
ROYALWOOD = {"base": (58, 43, 32, 255), "dark": (44, 32, 24, 255),
             "hi": (72, 54, 40, 255), "gold": (185, 148, 66, 255)}
SHIPWOOD = {"base": (107, 81, 56, 255), "dark": (82, 61, 42, 255),
            "hi": (126, 96, 67, 255)}
FRONTIER = {"base": (160, 89, 54, 255), "dark": (128, 69, 41, 255),
            "hi": (184, 108, 68, 255)}
METAL = {"base": (154, 163, 173, 255), "dark": (113, 121, 127, 255),
         "hi": (184, 192, 200, 255), "copper": (176, 120, 90, 255)}


# ------------------------------------------------------------------- blocks

def brick_texture(path, pal, rows=4, gold_chance=10, seed=7):
    p = canvas(16, 16, pal["mortar"])
    bh = 16 // rows
    state = seed
    for row in range(rows):
        offset = (row % 2) * 4
        y0 = row * bh
        for bx in range(-1, 3):
            x0 = bx * 8 + offset
            rect(p, 16, x0, y0, x0 + 7, y0 + bh - 1, pal["base"])
            hline(p, 16, x0, x0 + 7, y0, pal.get("hi", brighten(pal["base"], 1.15)))
            vline(p, 16, x0, y0, y0 + bh - 1, darken(pal["base"], 0.8))
            state = (state * 31 + 17) & 0xffff
            if state % gold_chance == 0 and "gold" in pal:
                px(p, 16, x0 + 2 + state % 4, y0 + 1 + state % (bh - 1), pal["gold"])
    jitter(p, 16, seed, 6)
    png(path, 16, 16, p)


def castle_stone(path):
    p = canvas(16, 16, STONE["mortar"])
    blocks = [(0, 0, 8, 8), (8, 0, 8, 8), (0, 8, 5, 8), (5, 8, 6, 8), (11, 8, 5, 8)]
    for i, (x, y, w, h) in enumerate(blocks):
        rect(p, 16, x, y, x + w - 1, y + h - 1, STONE["base"])
        hline(p, 16, x, x + w - 1, y, STONE["hi"])
        vline(p, 16, x, y, y + h - 1, STONE["hi"])
        hline(p, 16, x, x + w - 1, y + h - 1, darken(STONE["base"], 0.85))
        # a chipped corner speck
        if i % 2 == 0:
            px(p, 16, x + w - 2, y + h - 2, darken(STONE["base"], 0.75))
    jitter(p, 16, 11, 7)
    png(path, 16, 16, p)


def castle_tiles(path):
    p = canvas(16, 16)
    for y in range(4):
        for x in range(4):
            c = TILES["a"] if (x + y) % 2 == 0 else TILES["b"]
            rect(p, 16, x * 4, y * 4, x * 4 + 3, y * 4 + 3, c)
            hline(p, 16, x * 4, x * 4 + 3, y * 4, TILES["hi"])
            vline(p, 16, x * 4, y * 4, y * 4 + 3, TILES["hi"])
    jitter(p, 16, 23, 4)
    png(path, 16, 16, p)


def plank_texture(path, pal, seed, gold_inlay=False):
    p = canvas(16, 16, pal["base"])
    for row in range(4):
        y0 = row * 4
        hline(p, 16, 0, 16, y0 + 3, pal["dark"])
        hline(p, 16, 0, 16, y0, brighten(pal["base"], 1.1))
        # knots / grain dashes
        state = seed + row * 97
        for _ in range(3):
            state = (state * 31 + 7) & 0xffff
            gx = state % 14
            gy = y0 + 1 + (state >> 4) % 2
            vline(p, 16, gx, gy, gy + 1, pal["dark"])
        if gold_inlay and row % 2 == 1:
            px(p, 16, 3 + row * 3, y0 + 1, ROYALWOOD["gold"])
            px(p, 16, 4 + row * 3, y0 + 1, ROYALWOOD["gold"])
    jitter(p, 16, seed, 5)
    png(path, 16, 16, p)


def airship_metal(path):
    p = canvas(16, 16, METAL["base"])
    # panels
    for (x0, y0, w, h) in ((0, 0, 8, 8), (8, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8)):
        rect(p, 16, x0, y0, x0 + w - 1, y0 + h - 1, METAL["base"])
        hline(p, 16, x0, x0 + w - 1, y0, METAL["hi"])
        hline(p, 16, x0, x0 + w - 1, y0 + h - 1, METAL["dark"])
        vline(p, 16, x0, y0, y0 + h - 1, METAL["hi"])
        vline(p, 16, x0 + w - 1, y0, y0 + h - 1, METAL["dark"])
    # rivets
    for (x, y) in ((1, 1), (6, 1), (9, 1), (14, 1), (1, 6), (14, 6),
                   (1, 9), (14, 9), (1, 14), (14, 14), (6, 14), (9, 14)):
        px(p, 16, x, y, METAL["dark"])
        px(p, 16, x, y + 1 if y < 15 else y, brighten(METAL["hi"], 1.0))
    # copper corner trims
    rect(p, 16, 0, 0, 1, 1, METAL["copper"])
    rect(p, 16, 14, 14, 15, 15, METAL["copper"])
    jitter(p, 16, 41, 4)
    png(path, 16, 16, p)


def realm_banner_block():
    # flag: royal purple field with gold border and a crown mark
    p = canvas(16, 16, (0, 0, 0, 0))
    purple = (86, 44, 130, 255)
    deep = (64, 32, 99, 255)
    gold = (212, 172, 78, 255)
    rect(p, 16, 0, 0, 15, 15, purple)
    rect(p, 16, 0, 0, 15, 2, deep)
    hline(p, 16, 0, 15, 3, gold)
    # crown
    rect(p, 16, 5, 7, 10, 10, gold)
    for x in (5, 7, 9):
        px(p, 16, x, 6, gold)
    px(p, 16, 7, 8, deep)
    px(p, 16, 6, 12, deep)
    px(p, 16, 9, 12, deep)
    jitter(p, 16, 77, 5)
    png(TEX / "block/realm_banner.png", 16, 16, p)

    # pole
    q = canvas(16, 16, (0, 0, 0, 0))
    wood = (96, 70, 46, 255)
    rect(q, 16, 6, 0, 9, 16, wood)
    vline(q, 16, 6, 0, 16, brighten(wood, 1.2))
    vline(q, 16, 9, 0, 16, darken(wood, 0.75))
    hline(q, 16, 5, 10, 0, gold)
    hline(q, 16, 5, 10, 15, gold)
    png(TEX / "block/banner_pole.png", 16, 16, q)


# -------------------------------------------------------------------- items

GOLD = (222, 178, 84, 255)
GOLD_D = (158, 118, 48, 255)
STEEL = (176, 186, 198, 255)
STEEL_D = (122, 132, 146, 255)
WOOD = (110, 76, 44, 255)
WOOD_D = (82, 55, 32, 255)
IRON = (148, 150, 154, 255)
IRON_D = (104, 106, 112, 255)


def revolver_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    # barrel
    rect(p, 16, 8, 4, 14, 6, IRON)
    hline(p, 16, 8, 14, 4, brighten(IRON, 1.15))
    hline(p, 16, 8, 14, 6, IRON_D)
    # cylinder
    rect(p, 16, 4, 3, 8, 8, STEEL)
    px(p, 16, 5, 5, IRON_D)
    px(p, 16, 6, 6, IRON_D)
    px(p, 16, 5, 7, IRON_D)
    # frame + hammer
    rect(p, 16, 3, 5, 5, 9, IRON_D)
    rect(p, 16, 2, 3, 3, 5, IRON)
    # grip (warm wood, angled)
    for i in range(6):
        vline(p, 16, 2 + i // 2, 9 + i, 10 + i, WOOD if i % 2 == 0 else WOOD_D)
    rect(p, 16, 1, 9, 3, 12, WOOD)
    hline(p, 16, 1, 3, 9, brighten(WOOD, 1.2))
    # trigger guard
    px(p, 16, 5, 9, IRON_D)
    px(p, 16, 6, 10, IRON_D)
    outline(p, 16, (20, 16, 14, 255))
    png(path, 16, 16, p)


def flintlock_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    # long barrel
    rect(p, 16, 6, 6, 15, 8, IRON_D)
    hline(p, 16, 6, 15, 6, IRON)
    # brass furniture
    rect(p, 16, 5, 5, 7, 9, GOLD)
    hline(p, 16, 5, 7, 5, brighten(GOLD, 1.15))
    # lock plate
    rect(p, 16, 3, 7, 5, 10, IRON)
    px(p, 16, 2, 6, IRON)  # hammer
    # full wooden stock sweeping down
    for i in range(8):
        rect(p, 16, max(0, 1 - i // 3), 9 + i, 6 + i // 2, 10 + i, WOOD)
    rect(p, 16, 0, 12, 4, 15, WOOD)
    hline(p, 16, 0, 4, 12, brighten(WOOD, 1.2))
    hline(p, 16, 0, 4, 15, WOOD_D)
    # brass butt cap
    rect(p, 16, 0, 14, 4, 15, GOLD_D)
    outline(p, 16, (18, 14, 10, 255))
    png(path, 16, 16, p)


def longsword_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    # blade (diagonal, ice-steel with bright edge)
    for i in range(9):
        x = 5 + i
        y = 10 - i
        px(p, 16, x, y, STEEL)
        px(p, 16, x, y - 1, brighten(STEEL, 1.25))
        px(p, 16, x + 1 if x < 15 else x, y, STEEL_D)
    px(p, 16, 14, 1, (236, 245, 252, 255))
    # crossguard
    rect(p, 16, 3, 10, 6, 12, GOLD)
    px(p, 16, 2, 9, GOLD)
    px(p, 16, 7, 13, GOLD)
    hline(p, 16, 3, 6, 10, brighten(GOLD, 1.2))
    # grip + pommel
    px(p, 16, 4, 12, (74, 48, 30, 255))
    px(p, 16, 3, 13, (74, 48, 30, 255))
    px(p, 16, 2, 14, GOLD)
    outline(p, 16, (24, 26, 34, 255))
    png(path, 16, 16, p)


def coin_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    disc(p, 16, 8, 8, 6, GOLD)
    disc(p, 16, 8, 8, 6, GOLD)
    # rim
    for a in range(0, 360, 20):
        import math
        x = 8 + int(5.4 * math.cos(a * math.pi / 180))
        y = 8 + int(5.4 * math.sin(a * math.pi / 180))
        px(p, 16, x, y, GOLD_D)
    # crown stamp
    rect(p, 16, 5, 7, 10, 10, GOLD_D)
    for x in (5, 7, 9):
        px(p, 16, x, 6, GOLD_D)
    px(p, 16, 7, 8, brighten(GOLD, 1.3))
    hline(p, 16, 6, 9, 4, brighten(GOLD, 1.4))
    px(p, 16, 3, 10, brighten(GOLD, 1.25))
    png(path, 16, 16, p)


def jewelry_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    gold_base = (212, 170, 80, 255)
    # band
    disc(p, 16, 8, 10, 5, gold_base)
    rect(p, 16, 3, 9, 12, 12, (0, 0, 0, 0))
    # gems
    disc(p, 16, 8, 5, 2, (86, 200, 148, 255))       # emerald
    px(p, 16, 8, 4, (168, 240, 205, 255))
    disc(p, 16, 4, 8, 2, (66, 118, 220, 255))       # sapphire
    px(p, 16, 4, 7, (150, 190, 250, 255))
    disc(p, 16, 12, 8, 2, (214, 68, 88, 255))       # ruby
    px(p, 16, 12, 7, (244, 140, 150, 255))
    px(p, 16, 8, 10, GOLD_D)
    px(p, 16, 5, 11, GOLD_D)
    px(p, 16, 11, 11, GOLD_D)
    outline(p, 16, (40, 26, 10, 255))
    png(path, 16, 16, p)


def map_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    parchment = (216, 192, 135, 255)
    dark = (150, 124, 78, 255)
    ink = (74, 58, 38, 255)
    rect(p, 16, 1, 1, 14, 14, parchment)
    rect(p, 16, 1, 1, 14, 2, brighten(parchment, 1.1))
    rect(p, 16, 1, 13, 14, 14, dark)
    rect(p, 16, 13, 1, 14, 14, dark)
    # coastline squiggle
    for x in range(3, 12):
        px(p, 16, x, 4 + (x % 3), ink)
    # mountains
    px(p, 16, 5, 7, ink)
    px(p, 16, 6, 6, ink)
    px(p, 16, 7, 7, ink)
    px(p, 16, 9, 7, ink)
    px(p, 16, 10, 6, ink)
    px(p, 16, 11, 7, ink)
    # X marks the realm
    px(p, 16, 9, 10, (168, 48, 48, 255))
    px(p, 16, 10, 11, (168, 48, 48, 255))
    px(p, 16, 10, 9, (168, 48, 48, 255))
    px(p, 16, 9, 11, (168, 48, 48, 255))
    # compass
    px(p, 16, 3, 11, ink)
    px(p, 16, 2, 12, ink)
    px(p, 16, 4, 12, ink)
    jitter(p, 16, 91, 5)
    outline(p, 16, (92, 72, 40, 255))
    png(path, 16, 16, p)


def contract_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    paper = (234, 226, 204, 255)
    ink = (86, 78, 66, 255)
    rect(p, 16, 2, 1, 13, 14, paper)
    rect(p, 16, 2, 1, 13, 2, brighten(paper, 1.06))
    rect(p, 16, 2, 13, 13, 14, darken(paper, 0.88))
    for y in (4, 6, 8):
        hline(p, 16, 4, 11, y, ink)
    hline(p, 16, 4, 8, 10, ink)
    # wax seal
    disc(p, 16, 11, 12, 2, (168, 44, 44, 255))
    px(p, 16, 11, 12, (206, 92, 92, 255))
    jitter(p, 16, 17, 3)
    outline(p, 16, (70, 62, 48, 255))
    png(path, 16, 16, p)


def bottle_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    glass = (168, 199, 210, 140)
    glass_hi = (208, 231, 240, 190)
    # bottle body
    rect(p, 16, 4, 6, 11, 13, glass)
    rect(p, 16, 6, 3, 9, 6, glass)
    rect(p, 16, 7, 1, 8, 3, WOOD_D)   # cork
    hline(p, 16, 7, 8, 1, WOOD)
    vline(p, 16, 4, 6, 13, glass_hi)
    hline(p, 16, 4, 11, 6, glass_hi)
    # little ship inside
    rect(p, 16, 5, 10, 10, 11, WOOD_D)
    hline(p, 16, 5, 10, 10, WOOD)
    vline(p, 16, 7, 6, 9, WOOD_D)
    rect(p, 16, 8, 6, 10, 9, (236, 228, 208, 255))
    outline(p, 16, (44, 60, 70, 255))
    png(path, 16, 16, p)


def cannonball_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    iron_ball = (44, 44, 50, 255)
    disc(p, 16, 8, 9, 5, iron_ball)
    disc(p, 16, 6, 7, 2, (92, 94, 102, 255))
    px(p, 16, 6, 6, (150, 152, 160, 255))
    hline(p, 16, 5, 11, 13, (24, 24, 28, 255))
    # fuse glint
    px(p, 16, 12, 4, GOLD)
    px(p, 16, 13, 3, (240, 160, 60, 255))
    outline(p, 16, (12, 12, 14, 255))
    png(path, 16, 16, p)


def airship_kit_texture(path):
    p = canvas(16, 16, (0, 0, 0, 0))
    crate = (128, 96, 58, 255)
    crate_d = (98, 72, 44, 255)
    rect(p, 16, 2, 6, 13, 14, crate)
    hline(p, 16, 2, 13, 6, brighten(crate, 1.18))
    hline(p, 16, 2, 13, 10, crate_d)
    rect(p, 16, 2, 6, 3, 14, METAL["copper"])
    rect(p, 16, 12, 6, 13, 14, METAL["copper"])
    # balloon folded on top
    disc(p, 16, 8, 4, 3, (126, 84, 168, 255))
    px(p, 16, 7, 2, (176, 136, 216, 255))
    px(p, 16, 9, 5, (92, 60, 128, 255))
    outline(p, 16, (40, 30, 20, 255))
    png(path, 16, 16, p)


def spawn_egg_texture(path, base, spot):
    p = canvas(16, 16, (0, 0, 0, 0))
    hi = brighten(base, 1.25)
    lo = darken(base, 0.72)
    # egg silhouette rows (x0, x1) per y
    rows = [(6, 9), (5, 10), (4, 11), (4, 11), (3, 12), (3, 12), (3, 12), (3, 12),
            (2, 13), (2, 13), (2, 13), (2, 13), (3, 12), (3, 12), (4, 11), (5, 10)]
    for y, (x0, x1) in enumerate(rows):
        hline(p, 16, x0, x1 + 1, y, base)
    vline(p, 16, 4, 4, 9, hi)
    vline(p, 16, 5, 2, 8, hi)
    vline(p, 16, 11, 5, 12, lo)
    vline(p, 16, 12, 8, 13, lo)
    # spots
    state = sum(base) | 1
    for _ in range(7):
        state = (state * 31 + 13) & 0xffff
        sx = 3 + state % 10
        sy = 3 + (state >> 5) % 10
        px(p, 16, sx, sy, spot)
        px(p, 16, sx + 1, sy, darken(spot, 0.85))
    outline(p, 16, (18, 16, 20, 255))
    png(path, 16, 16, p)


# ------------------------------------------------------------------ skins

def skin_texture(name, face, hair, shirt, trim, pants, hat, hat_kind="cap", eye=(24, 24, 28, 255)):
    p = canvas(64, 64)
    face_d = darken(face, 0.82)
    face_l = brighten(face, 1.12)
    shirt_d = darken(shirt, 0.8)
    shirt_l = brighten(shirt, 1.12)

    def shade_box(x0, y0, x1, y1):
        vline(p, 64, x0, y0, y1, None or (0, 0, 0, 0))  # placeholder, replaced below

    # ---- head
    rect(p, 64, 8, 8, 16, 16, face)
    rect(p, 64, 8, 0, 16, 8, hair)
    rect(p, 64, 0, 8, 8, 16, face_d)
    rect(p, 64, 16, 8, 24, 16, face_l)
    # eyes + brows
    px(p, 64, 10, 11, eye)
    px(p, 64, 13, 11, eye)
    px(p, 64, 10, 10, darken(hair, 0.8))
    px(p, 64, 13, 10, darken(hair, 0.8))
    # mouth
    rect(p, 64, 11, 14, 13, 15, darken(face, 0.7))
    # side shading
    vline(p, 64, 8, 8, 16, face_d)
    vline(p, 64, 15, 8, 16, face_l)

    # ---- hat layer (outer head)
    hat_d = darken(hat, 0.78)
    rect(p, 64, 40, 8, 48, 16, hat)
    rect(p, 64, 48, 8, 56, 16, hat_d)
    rect(p, 64, 40, 0, 56, 8, hat)
    if hat_kind == "wide":        # outlaw brim hat
        rect(p, 64, 32, 8, 40, 12, hat)
        rect(p, 64, 56, 8, 64, 12, hat)
        rect(p, 64, 32, 0, 64, 4, hat)
        hline(p, 64, 40, 56, 0, hat_d)
    elif hat_kind == "band":      # pirate bandana + skull dot
        rect(p, 64, 40, 8, 56, 12, hat)
        hline(p, 64, 40, 56, 8, brighten(hat, 1.3))
        px(p, 64, 44, 11, (236, 236, 236, 255))
        px(p, 64, 45, 11, (236, 236, 236, 255))
    elif hat_kind == "goggles":   # sky captain goggles on hat band
        hline(p, 64, 40, 56, 8, (160, 168, 178, 255))
        px(p, 64, 44, 8, (110, 220, 235, 255))
        px(p, 64, 48, 8, (110, 220, 235, 255))
    elif hat_kind == "helm":      # knight nasal helm
        rect(p, 64, 40, 8, 56, 16, hat)
        rect(p, 64, 40, 0, 56, 8, hat)
        rect(p, 64, 47, 8, 49, 15, brighten(hat, 1.2))   # nasal bar
        hline(p, 64, 40, 56, 15, hat_d)
        px(p, 64, 43, 11, (16, 16, 18, 255))
        px(p, 64, 52, 11, (16, 16, 18, 255))

    # ---- torso
    rect(p, 64, 20, 20, 28, 32, shirt)
    rect(p, 64, 20, 16, 28, 20, trim)
    rect(p, 64, 16, 20, 20, 32, shirt_d)
    rect(p, 64, 28, 20, 32, 32, shirt_l)
    # tabard / chest stripe
    rect(p, 64, 23, 20, 25, 32, trim)
    vline(p, 64, 24, 20, 32, brighten(trim, 1.2))
    # belt
    rect(p, 64, 20, 30, 28, 32, (52, 38, 28, 255))
    px(p, 64, 23, 30, GOLD)
    px(p, 64, 24, 30, GOLD_D)

    # ---- arms
    for base_x in (44, 36):
        rect(p, 64, base_x, 20, base_x + 4, 32, shirt)
        rect(p, 64, base_x, 16, base_x + 4, 20, trim)
        rect(p, 64, base_x, 32, base_x + 4, 36, face)
        vline(p, 64, base_x, 20, 32, shirt_d)
        vline(p, 64, base_x + 3, 20, 32, shirt_l)
        vline(p, 64, base_x, 32, 36, face_d)

    # ---- legs
    for base_x in (20, 36):
        rect(p, 64, base_x, 52, base_x + 4, 64, pants)
        rect(p, 64, base_x, 48, base_x + 4, 52, trim)
        vline(p, 64, base_x, 52, 64, darken(pants, 0.78))
        # boot cuff
        rect(p, 64, base_x, 60, base_x + 4, 64, (36, 30, 26, 255))

    # boot soles + glove cuffs
    for base_x in (20, 36):
        hline(p, 64, base_x, base_x + 4, 63, (22, 20, 18, 255))

    png(TEX / "entity/survivor" / f"{name}.png", 64, 64, p)


# ------------------------------------------------------- vehicle textures

def paint_airship(path):
    """128x128 sheet matching AirshipEntityModel's UV plan."""
    p = canvas(128, 128, (0, 0, 0, 0))
    canvas_light = (226, 214, 186, 255)
    canvas_dark = (198, 182, 150, 255)
    stripe = (120, 78, 150, 255)     # royal violet stripe
    gold = (212, 172, 78, 255)

    # --- envelope body box (0,0)-(112,60): top/bottom faces + side ring
    rect(p, 128, 0, 0, 112, 40, canvas_light)
    for y in range(0, 40, 8):
        hline(p, 128, 0, 112, y, canvas_dark)
    # side faces (0,40)-(112,60): vertical stripes + bands
    for x in range(0, 112, 12):
        rect(p, 128, x, 40, x + 5, 60, canvas_light)
        rect(p, 128, x + 6, 40, x + 11, 60, canvas_dark)
        rect(p, 128, x + 2, 40, x + 4, 60, stripe)
    hline(p, 128, 0, 112, 40, brighten(canvas_light, 1.08))
    hline(p, 128, 0, 112, 42, gold)
    hline(p, 128, 0, 112, 43, gold)
    for y in range(57, 60):
        hline(p, 128, 0, 112, y, darken(canvas_dark, 0.85))
    # seam rivets along the equator
    for x in range(4, 112, 16):
        px(p, 128, x, 52, darken(canvas_dark, 0.7))

    # --- nose cap (0,62)-(36,84) & tail cap (38,62)-(74,84)
    for (x0, x1) in ((0, 36), (38, 74)):
        rect(p, 128, x0, 62, x1, 84, canvas_light)
        for y in range(62, 84, 6):
            hline(p, 128, x0, x1, y, canvas_dark)
        hline(p, 128, x0, x1, 70, stripe)
        hline(p, 128, x0, x1, 71, stripe)

    # --- fins (78,62)-(128,84)
    for (x0, x1) in ((78, 102), (104, 128)):
        rect(p, 128, x0, 62, x1, 84, canvas_dark)
        rect(p, 128, x0 + 3, 66, x1 - 3, 80, stripe)
        hline(p, 128, x0, x1, 62, canvas_light)

    # --- pods (0,88)-(50,104): riveted steel
    for (x0, x1) in ((0, 24), (26, 50)):
        rect(p, 128, x0, 88, x1, 104, METAL["base"])
        hline(p, 128, x0, x1, 88, METAL["hi"])
        hline(p, 128, x0, x1, 103, METAL["dark"])
        for x in range(x0 + 2, x1, 6):
            px(p, 128, x, 96, METAL["dark"])

    # --- propeller blades (52,88)-(70,104): dark bronze
    for x0 in (52, 62):
        rect(p, 128, x0, 88, x0 + 8, 104, (92, 66, 40, 255))
        hline(p, 128, x0, x0 + 8, 88, (126, 94, 60, 255))
        hline(p, 128, x0, x0 + 8, 103, (62, 44, 26, 255))

    # --- gondola (72,86)-(128,112): warm wood with railing
    rect(p, 128, 72, 86, 128, 112, SHIPWOOD["base"])
    for y in range(86, 112, 4):
        hline(p, 128, 72, 128, y + 3, SHIPWOOD["dark"])
    rect(p, 128, 72, 86, 128, 89, brighten(SHIPWOOD["hi"], 1.05))
    hline(p, 128, 72, 128, 89, GOLD)
    for x in range(74, 128, 10):
        vline(p, 128, x, 90, 111, darken(SHIPWOOD["base"], 0.9))
    jitter(p, 128, 5, 4)
    png(path, 128, 128, p)


def paint_ship(path, hull, hull_dark, sail, sail_dark, trim, flag, emblem="crown"):
    """256x128 sheet matching SailingShipEntityModel's UV plan.

    Regions: hull box (0,0)-(176,70) with deck top face at (60,0)-(88,10),
    underside (88,0)-(116,10) and the side strip (0,60)-(176,70); fore/aft
    castles (0,70)-(56,88) and (56,70)-(112,88); mast (176,0)-(188,33);
    yard (188,0)-(256,4); sail (0,96)-(66,121); pennant (70,96)-(96,104).
    """
    p = canvas(256, 128, (0, 0, 0, 0))

    # --- hull underside (88,0)-(116,10): tarred shadow planks
    rect(p, 256, 88, 0, 116, 10, hull_dark)
    for x in range(88, 116, 5):
        vline(p, 256, x, 0, 10, darken(hull_dark, 0.85))

    # --- hull deck top face (60,0)-(88,10): planked deck with caulking
    rect(p, 256, 60, 0, 88, 10, brighten(hull, 1.12))
    for x in range(60, 88, 4):
        vline(p, 256, x, 0, 10, darken(hull, 0.9))
    hline(p, 256, 60, 88, 4, darken(hull, 0.88))

    # --- hull side strip (0,60)-(176,70): gunwale, trim band, waterline
    rect(p, 256, 0, 60, 176, 70, hull)
    hline(p, 256, 0, 176, 60, brighten(hull, 1.25))       # gunwale cap
    hline(p, 256, 0, 176, 61, trim)
    hline(p, 256, 0, 176, 66, hull_dark)
    hline(p, 256, 0, 176, 68, darken(hull_dark, 0.72))    # waterline
    hline(p, 256, 0, 176, 69, darken(hull_dark, 0.6))
    for x in range(4, 176, 18):
        px(p, 256, x, 63, darken(hull, 0.78))             # tar seams / rivets
        px(p, 256, x, 64, darken(hull, 0.78))

    # --- castles (0,70)-(56,88) & (56,70)-(112,88): raised deck wood
    for (x0, x1) in ((0, 56), (56, 112)):
        rect(p, 256, x0, 70, x1, 88, brighten(hull, 1.08))
        for y in range(70, 88, 4):
            hline(p, 256, x0, x1, y + 3, hull_dark)
        hline(p, 256, x0, x1, 70, trim)
        for x in range(x0 + 4, x1, 8):
            vline(p, 256, x, 72, 86, darken(hull, 0.9))

    # --- mast (176,0)-(188,33) & yard (188,0)-(256,4)
    rect(p, 256, 176, 0, 188, 33, hull)
    vline(p, 256, 177, 0, 33, brighten(hull, 1.3))
    vline(p, 256, 185, 0, 33, hull_dark)
    rect(p, 256, 188, 0, 256, 4, hull)
    hline(p, 256, 188, 256, 0, brighten(hull, 1.2))
    hline(p, 256, 188, 256, 3, hull_dark)

    # --- sail (0,96)-(66,121): canvas with seams, trim band, emblem
    rect(p, 256, 0, 96, 66, 121, sail)
    for y in range(99, 118, 4):
        hline(p, 256, 0, 66, y, sail_dark)
    hline(p, 256, 0, 66, 96, brighten(sail, 1.12))
    rect(p, 256, 0, 115, 66, 119, trim)
    cx = 33
    if emblem == "crown":
        rect(p, 256, cx - 5, 101, cx + 5, 108, GOLD)
        for x in (cx - 5, cx, cx + 5):
            rect(p, 256, x, 99, x + 1, 101, GOLD)
        rect(p, 256, cx - 1, 103, cx + 2, 106, sail)
    elif emblem == "skull":
        disc(p, 256, cx, 104, 4, (238, 238, 238, 255))
        rect(p, 256, cx - 4, 107, cx + 4, 110, (238, 238, 238, 255))
        px(p, 256, cx - 2, 103, (24, 24, 28, 255))
        px(p, 256, cx + 2, 103, (24, 24, 28, 255))
        rect(p, 256, cx - 1, 109, cx + 1, 110, (24, 24, 28, 255))
    elif emblem == "gull":
        hline(p, 256, cx - 6, cx - 1, 104, (24, 40, 44, 255))
        hline(p, 256, cx + 1, cx + 6, 104, (24, 40, 44, 255))
        px(p, 256, cx - 1, 103, (24, 40, 44, 255))
        px(p, 256, cx + 1, 103, (24, 40, 44, 255))

    # --- pennant (70,96)-(96,104)
    rect(p, 256, 70, 96, 96, 104, flag)
    rect(p, 256, 92, 96, 96, 104, (0, 0, 0, 0))           # swallow-tail notch
    hline(p, 256, 70, 92, 96, brighten(flag, 1.3))

    jitter(p, 256, 9, 3)
    png(path, 256, 128, p)


def icon_texture(path):
    p = canvas(128, 128, (0, 0, 0, 0))
    # backdrop disc
    disc(p, 128, 64, 66, 56, (34, 26, 44, 255))
    disc(p, 128, 64, 66, 54, (58, 40, 84, 255))
    # crossed longsword (diagonal steel with gold guard)
    for i in range(64):
        x = 22 + i
        y = 104 - i
        for dx in range(4):
            px(p, 128, x + dx, y, (196, 206, 220, 255))
        px(p, 128, x, y - 1, (238, 246, 252, 255))
    for i in range(64):
        x = 22 + i
        y = 24 + i
        for dx in range(4):
            px(p, 128, x + dx, y, (168, 178, 194, 255))
    # banner pole + flag
    rect(p, 128, 86, 14, 92, 114, (86, 62, 40, 255))
    rect(p, 128, 86, 14, 92, 16, (212, 172, 78, 255))
    rect(p, 128, 30, 18, 88, 62, (86, 44, 130, 255))
    rect(p, 128, 30, 18, 88, 24, (64, 32, 99, 255))
    rect(p, 128, 30, 58, 88, 62, (64, 32, 99, 255))
    # gold crown emblem on the flag
    rect(p, 128, 44, 30, 74, 50, (222, 182, 88, 255))
    for x in range(44, 75, 10):
        rect(p, 128, x, 24, x + 3, 30, (222, 182, 88, 255))
    rect(p, 128, 52, 36, 60, 44, (58, 40, 84, 255))
    # coin at the base
    disc(p, 128, 64, 100, 12, (222, 178, 84, 255))
    disc(p, 128, 64, 100, 9, (240, 204, 116, 255))
    rect(p, 128, 58, 96, 70, 102, (170, 128, 56, 255))
    for x in range(58, 71, 4):
        rect(p, 128, x, 92, x + 2, 96, (170, 128, 56, 255))
    png(path, 128, 128, p)


# -------------------------------------------------------------------- main

def main():
    # blocks
    brick_texture(TEX / "block/crown_brick.png", BRICK, gold_chance=12, seed=7)
    castle_stone(TEX / "block/castle_stone.png")
    castle_tiles(TEX / "block/castle_tiles.png")
    plank_texture(TEX / "block/royal_wood.png", ROYALWOOD, seed=31, gold_inlay=True)
    plank_texture(TEX / "block/ship_planks.png", SHIPWOOD, seed=37)
    plank_texture(TEX / "block/frontier_planks.png", FRONTIER, seed=43)
    airship_metal(TEX / "block/airship_metal.png")
    realm_banner_block()

    # items
    revolver_texture(TEX / "item/revolver.png")
    flintlock_texture(TEX / "item/flintlock.png")
    longsword_texture(TEX / "item/royal_longsword.png")
    coin_texture(TEX / "item/royal_coin.png")
    jewelry_texture(TEX / "item/royal_jewelry.png")
    map_texture(TEX / "item/medieval_map.png")
    contract_texture(TEX / "item/recruitment_contract.png")
    bottle_texture(TEX / "item/ship_in_a_bottle.png")
    cannonball_texture(TEX / "item/cannonball.png")
    airship_kit_texture(TEX / "item/airship_kit.png")

    # spawn eggs
    spawn_egg_texture(TEX / "item/survivor_spawn_egg.png", (148, 108, 190, 255), (238, 232, 244, 255))
    spawn_egg_texture(TEX / "item/knight_spawn_egg.png", (92, 115, 168, 255), (220, 228, 240, 255))
    spawn_egg_texture(TEX / "item/pirate_spawn_egg.png", (155, 63, 53, 255), (24, 24, 28, 255))
    spawn_egg_texture(TEX / "item/outlaw_spawn_egg.png", (200, 155, 72, 255), (94, 62, 34, 255))
    spawn_egg_texture(TEX / "item/sky_captain_spawn_egg.png", (127, 77, 168, 255), (120, 220, 235, 255))
    spawn_egg_texture(TEX / "item/merchant_ship_spawn_egg.png", (222, 214, 190, 255), (66, 92, 168, 255))
    spawn_egg_texture(TEX / "item/pirate_ship_spawn_egg.png", (36, 32, 38, 255), (178, 52, 52, 255))

    # survivor cultures
    skin_texture("knight", face=(226, 186, 152, 255), hair=(96, 70, 44, 255),
                 shirt=(96, 115, 168, 255), trim=(196, 200, 210, 255),
                 pants=(70, 76, 92, 255), hat=(178, 184, 196, 255), hat_kind="helm")
    skin_texture("pirate", face=(214, 170, 138, 255), hair=(50, 40, 34, 255),
                 shirt=(148, 60, 52, 255), trim=(52, 48, 52, 255),
                 pants=(54, 48, 46, 255), hat=(150, 40, 40, 255), hat_kind="band")
    skin_texture("outlaw", face=(206, 160, 122, 255), hair=(72, 50, 32, 255),
                 shirt=(168, 128, 62, 255), trim=(104, 76, 44, 255),
                 pants=(84, 62, 40, 255), hat=(74, 54, 36, 255), hat_kind="wide")
    skin_texture("sky_captain", face=(222, 182, 148, 255), hair=(188, 188, 196, 255),
                 shirt=(122, 78, 168, 255), trim=(84, 200, 220, 255),
                 pants=(66, 60, 84, 255), hat=(64, 58, 78, 255), hat_kind="goggles")

    # vehicles
    paint_airship(TEX / "entity/airship.png")
    paint_ship(TEX / "entity/merchant_ship.png",
               hull=(110, 84, 51, 255), hull_dark=(84, 62, 38, 255),
               sail=(232, 221, 192, 255), sail_dark=(206, 192, 158, 255),
               trim=(66, 92, 168, 255), flag=(66, 92, 168, 255), emblem="crown")
    paint_ship(TEX / "entity/pirate_ship.png",
               hull=(61, 51, 43, 255), hull_dark=(44, 37, 31, 255),
               sail=(43, 43, 48, 255), sail_dark=(32, 32, 36, 255),
               trim=(140, 44, 44, 255), flag=(24, 22, 26, 255), emblem="skull")
    paint_ship(TEX / "entity/sloop.png",
               hull=(122, 92, 57, 255), hull_dark=(94, 70, 44, 255),
               sail=(226, 234, 232, 255), sail_dark=(198, 210, 208, 255),
               trim=(64, 142, 134, 255), flag=(64, 142, 134, 255), emblem="gull")

    # mod icon (both the fabric.mod.json icon and the legacy item icon slot)
    icon_texture(ROOT / "icon.png")
    icon_texture(TEX / "item/icon.png")

    print(f"regenerated all Rival Realms art under {ROOT}")


if __name__ == "__main__":
    main()
