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


def _block_depth(p, w, base, hi, lo, seed=3):
    """Shared bevel: lit top edge, shadowed bottom, occasional chips."""
    hline(p, w, 0, w, 0, hi)
    hline(p, w, 0, w, w - 1, lo)
    vline(p, w, 0, 0, w, hi)
    vline(p, w, w - 1, 0, w, lo)
    rnd = {"n": seed}
    def nxt():
        rnd["n"] = (rnd["n"] * 1103515245 + 12345) & 0x7fffffff
        return rnd["n"]
    for _ in range(3):
        x, y = nxt() % w, nxt() % w
        px(p, w, x, y, lo)
        px(p, w, (x + 1) % w, y, darken(lo, 0.9))


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
    # brushed steel streaks
    for i in range(5):
        x = (i * 7 + 3) % 16
        for y in range((i * 5) % 8, (i * 5) % 8 + 5):
            px(p, 16, x, y, brighten(METAL["base"], 1.06))
    jitter(p, 16, 41, 4)
    _block_depth(p, 16, METAL["base"], METAL["hi"], METAL["dark"], 11)
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
    save16(path, p)


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
    save16(path, p)


def longsword_texture(path):
    """The Royal Longsword: a proper fullered greatblade - ice steel with a
    bright cutting edge and dark spine, gemmed gold crossguard, wire-wrapped
    grip and a ruby-set pommel."""
    p = canvas(16, 16, (0, 0, 0, 0))
    blade_hi = (238, 244, 252, 255)
    blade = (198, 208, 222, 255)
    blade_d = (148, 158, 174, 255)
    fuller = (170, 182, 198, 255)
    # blade: diagonal with a bright top edge, dark spine, sunken fuller
    for i in range(10):
        x = 4 + i
        y = 11 - i
        px(p, 16, x, y - 1, blade_hi)                # cutting edge
        px(p, 16, x, y, blade)
        px(p, 16, x + 1, y, fuller)                  # fuller line
        if x + 2 < 16:
            px(p, 16, x + 2, y, blade_d)             # spine shadow
    px(p, 16, 14, 1, (255, 255, 255, 255))           # tip glint
    px(p, 16, 13, 2, blade_hi)
    # crossguard: gold bar with darker underside and a sapphire centre
    rect(p, 16, 2, 9, 6, 11, GOLD)
    hline(p, 16, 2, 6, 9, brighten(GOLD, 1.22))
    hline(p, 16, 2, 6, 11, GOLD_D)
    px(p, 16, 1, 8, GOLD); px(p, 16, 7, 12, GOLD)    # quillon tips
    px(p, 16, 4, 10, (66, 118, 220, 255))            # sapphire
    px(p, 16, 4, 10, (66, 118, 220, 255))
    px(p, 16, 4, 9, (150, 190, 250, 255))
    # grip: dark leather with wire wraps
    for (gx, gy) in ((4, 12), (3, 13)):
        px(p, 16, gx, gy, (64, 42, 26, 255))
    px(p, 16, 3, 12, (108, 74, 44, 255))             # wire wrap
    px(p, 16, 2, 13, (108, 74, 44, 255))
    # pommel: gold disc set with a ruby
    px(p, 16, 2, 14, GOLD); px(p, 16, 1, 15, GOLD_D)
    px(p, 16, 1, 14, (214, 68, 88, 255))             # ruby
    outline(p, 16, (24, 26, 34, 255))
    save16(path, p)


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
    save16(path, p)


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
    save16(path, p)


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
    save16(path, p)


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
    save16(path, p)


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
    save16(path, p)


def war_weapon_textures():
    """16x16 icons: blunderbuss, halberd, warhorn."""
    # --- blunderbuss: short flared barrel, brass bell, heavy stock
    p = canvas(16, 16)
    iron, iron_l, iron_d = (176, 178, 184, 255), (214, 216, 220, 255), (120, 122, 130, 255)
    brass = (196, 158, 74, 255)
    wood, wood_d = (118, 82, 46, 255), (86, 58, 32, 255)
    for y in range(3, 9):                       # barrel
        px(p, 16, 6, y, iron); px(p, 16, 7, y, iron_l); px(p, 16, 8, y, iron_d)
    for y in range(2, 10):                      # flared bell
        px(p, 16, 4, y, brass); px(p, 16, 5, y, brass)
    px(p, 16, 4, 2, (226, 190, 100, 255)); px(p, 16, 5, 2, brass)
    px(p, 16, 3, 5, brass); px(p, 16, 4, 5, (226, 190, 100, 255))
    for i in range(7):                          # stock
        px(p, 16, 7 + i, 9 + (i * 2) // 3, wood); px(p, 16, 8 + i, 10 + (i * 2) // 3, wood_d)
    px(p, 16, 7, 10, (52, 38, 24, 255))         # trigger guard
    jitter(p, 16, 21, 3)
    png(TEX / "item/blunderbuss.png", 16, 16, p)

    # --- halberd: long ash shaft, crescent blade, top spike
    p = canvas(16, 16)
    steel, steel_l, steel_d = (188, 192, 198, 255), (226, 228, 232, 255), (128, 132, 140, 255)
    ash, ash_d = (140, 104, 60, 255), (104, 76, 42, 255)
    for i in range(13):                         # shaft
        px(p, 16, 7, 3 + i, ash); px(p, 16, 8, 3 + i, ash_d)
    for x in range(3, 9):                       # crescent blade
        px(p, 16, x, 2, steel_l if x in (3, 4) else steel)
        px(p, 16, x, 3, steel_d if x > 5 else steel)
    px(p, 16, 2, 3, steel); px(p, 16, 2, 4, steel_d)
    px(p, 16, 9, 2, steel); px(p, 16, 10, 3, steel); px(p, 16, 10, 4, steel_d)
    px(p, 16, 7, 0, steel_l); px(p, 16, 8, 0, steel); px(p, 16, 7, 1, steel); px(p, 16, 8, 1, steel)  # spike
    px(p, 16, 6, 4, (72, 52, 30, 255)); px(p, 16, 9, 4, (72, 52, 30, 255))  # collar
    jitter(p, 16, 22, 3)
    png(TEX / "item/halberd.png", 16, 16, p)

    # --- warhorn: curved ox horn with mouthpiece and cord
    p = canvas(16, 16)
    horn, horn_l, horn_d = (196, 158, 92, 255), (226, 196, 132, 255), (150, 116, 62, 255)
    cord = (150, 60, 48, 255)
    for i in range(9):                          # body arc
        x, y = 3 + i, 12 - i
        px(p, 16, x, y, horn); px(p, 16, x, y + 1, horn_d)
    px(p, 16, 2, 13, horn_l); px(p, 16, 3, 12, horn_l)      # mouthpiece glint
    px(p, 16, 11, 4, horn_d); px(p, 16, 12, 3, horn_d)      # bell shadow
    px(p, 16, 12, 2, horn)
    for (cx, cy) in ((6, 9), (8, 8)):           # cord wraps
        px(p, 16, cx, cy, cord); px(p, 16, cx + 1, cy, cord)
    jitter(p, 16, 23, 3)
    png(TEX / "item/warhorn.png", 16, 16, p)


def food_textures():
    """Hardtack biscuit, iron-pot stew, drinking horn of mead."""
    # --- hardtack: pale cracker with scorched pierce-holes
    p = canvas(16, 16)
    tack, tack_d = (222, 202, 158, 255), (176, 152, 112, 255)
    disc(p, 16, 8, 8, 6, tack)
    hline(p, 16, 4, 12, 4, (238, 222, 184, 255))
    for (x, y) in ((5, 6), (10, 6), (7, 9), (10, 10), (5, 11)):
        px(p, 16, x, y, tack_d)
    px(p, 16, 3, 8, tack_d); px(p, 16, 13, 8, tack_d)
    save16(TEX / "item/hardtack.png", p)

    # --- frontier stew: iron pot, chunky surface, steam
    p = canvas(16, 16)
    iron, iron_l, iron_d = (74, 76, 82, 255), (108, 112, 120, 255), (48, 50, 56, 255)
    stew = (150, 84, 48, 255)
    rect(p, 16, 3, 7, 13, 13, iron)
    hline(p, 16, 3, 13, 7, iron_l)
    hline(p, 16, 3, 13, 13, iron_d)
    rect(p, 16, 2, 6, 14, 7, iron_l)               # rim
    rect(p, 16, 4, 8, 12, 9, stew)                 # stew surface
    px(p, 16, 5, 8, (196, 132, 70, 255)); px(p, 16, 9, 9, (120, 70, 40, 255))
    px(p, 16, 11, 8, (222, 178, 96, 255))          # carrot bit
    px(p, 16, 6, 9, (226, 214, 180, 255))          # potato chunk
    for (hx, hy) in ((6, 3), (9, 2), (12, 4)):     # steam curls
        px(p, 16, hx, hy, (214, 214, 220, 255))
        px(p, 16, hx + 1, hy - 1, (196, 196, 204, 255))
    save16(TEX / "item/frontier_stew.png", p)

    # --- mead: drinking horn with gold rim and honey glow
    p = canvas(16, 16)
    horn, horn_d = (214, 178, 108, 255), (160, 124, 66, 255)
    mead = (222, 158, 60, 255)
    for i in range(10):
        x = 3 + i
        y = 11 - i
        px(p, 16, x, y, horn)
        px(p, 16, x, y + 1, horn_d)
        if y < 11 and i > 4:
            px(p, 16, x, y + 1, mead)              # honey filling along the belly
    px(p, 16, 3, 12, (240, 210, 130, 255))         # rim glint
    px(p, 16, 12, 2, (238, 238, 226, 255))         # ivory tip
    px(p, 16, 11, 3, (226, 226, 210, 255))
    for (cx, cy) in ((6, 8), (8, 7)):              # cord wraps
        px(p, 16, cx, cy, (140, 52, 44, 255))
    save16(TEX / "item/mead.png", p)


def settlement_block_textures(folder):
    """Ten hand-painted settlement blocks: gold-inlay masonry, a carved
    pillar, the war council table, a weapon rack, trophy skulls, the warm
    hearth lantern, dock crates, a worn road tile and an arrow-slit wall."""
    # -- gilded brick: dressed masonry with gold-seamed celebration stones --
    g = canvas(16, 16, (74, 72, 70, 255))
    state = 5
    for row in range(4):
        offset = (row % 2) * 4
        y0 = row * 4
        for bx in range(-1, 3):
            x0 = bx * 8 + offset
            rect(g, 16, x0, y0, x0 + 7, y0 + 3, (128, 124, 118, 255))
            hline(g, 16, x0, x0 + 7, y0, (150, 146, 138, 255))
            vline(g, 16, x0, y0, y0 + 3, (150, 146, 138, 255))
            hline(g, 16, x0, x0 + 7, y0 + 3, (96, 92, 88, 255))
            state = (state * 31 + 17) & 0xffff
            if state % 3 == 0:                     # gold-seamed stone
                hline(g, 16, x0 + 1, x0 + 6, y0 + 1, GOLD)
                px(g, 16, x0 + 2, y0 + 2, GOLD_D)
                px(g, 16, x0 + 5, y0 + 2, GOLD_D)
            elif state % 3 == 1:
                px(g, 16, x0 + 3 + state % 3, y0 + 2, GOLD)
    jitter(g, 16, 9, 6)
    png(folder / "gilded_brick.png", 16, 16, g)

    # -- crown pillar: fluted column, gilded capital and plinth --
    c = canvas(16, 16)
    rect(c, 16, 2, 4, 13, 12, (140, 136, 128, 255))
    for x in range(3, 13, 2):                       # flutes
        vline(c, 16, x, 4, 12, (162, 158, 148, 255))
        vline(c, 16, x + 1, 4, 12, (110, 106, 100, 255))
    rect(c, 16, 0, 0, 15, 3, GOLD)                  # capital band
    hline(c, 16, 0, 16, 0, brighten(GOLD, 1.25))
    hline(c, 16, 0, 16, 3, GOLD_D)
    for x in (2, 6, 10, 14):                        # dentil notches
        vline(c, 16, x, 1, 2, GOLD_D)
    rect(c, 16, 0, 13, 15, 15, (104, 100, 94, 255))  # plinth
    hline(c, 16, 0, 16, 13, (126, 122, 114, 255))
    px(c, 16, 3, 14, (86, 82, 78, 255)); px(c, 16, 12, 14, (86, 82, 78, 255))
    png(folder / "crown_pillar.png", 16, 16, c)

    # -- war table top: parchment campaign map pinned to dark oak --
    t = canvas(16, 16, (56, 40, 28, 255))
    for y in range(0, 16, 4):                       # plank field
        hline(t, 16, 0, 16, y + 3, (44, 31, 22, 255))
        hline(t, 16, 0, 16, y, (66, 48, 34, 255))
    rect(t, 16, 2, 2, 13, 13, (214, 196, 158, 255))  # parchment
    hline(t, 16, 2, 13, 2, (232, 216, 178, 255))
    vline(t, 16, 2, 2, 13, (232, 216, 178, 255))
    hline(t, 16, 2, 13, 13, (176, 158, 120, 255))
    vline(t, 16, 13, 2, 13, (176, 158, 120, 255))
    vline(t, 16, 8, 3, 12, (96, 118, 168, 255))     # realm border
    for (mx, my, col) in ((5, 5, (168, 56, 48, 255)), (11, 9, (96, 118, 168, 255)),
                          (7, 10, (72, 132, 86, 255))):   # army pins
        px(t, 16, mx, my, col); px(t, 16, mx, my - 1, col)
    rect(t, 16, 3, 3, 4, 4, GOLD)                   # compass rose
    px(t, 16, 3, 3, brighten(GOLD, 1.3))
    px(t, 16, 6, 7, (40, 34, 30, 255)); px(t, 16, 7, 7, (40, 34, 30, 255))  # fleet mark
    png(folder / "war_table_top.png", 16, 16, t)

    # -- war table side: panelled front with a map drawer --
    sd = canvas(16, 16, (52, 37, 26, 255))
    for y in range(0, 16, 4):
        hline(sd, 16, 0, 16, y + 3, (40, 28, 20, 255))
    rect(sd, 16, 2, 4, 13, 11, (62, 45, 32, 255))   # recessed panel
    rect(sd, 16, 3, 10, 6, 12, (48, 34, 24, 255))   # drawer
    px(sd, 16, 4, 11, GOLD); px(sd, 16, 5, 11, GOLD)  # brass pull
    hline(sd, 16, 0, 16, 0, (74, 54, 38, 255))
    png(folder / "war_table_side.png", 16, 16, sd)

    # -- weapon rack (cutout): posts + bar with sword, spear and axe --
    r = canvas(16, 16, (0, 0, 0, 0))
    rect(r, 16, 1, 0, 2, 15, WOOD_D)                # posts
    rect(r, 16, 13, 0, 14, 15, WOOD_D)
    vline(r, 16, 1, 0, 15, WOOD)
    vline(r, 16, 13, 0, 15, WOOD)
    rect(r, 16, 1, 1, 14, 2, WOOD)                  # crossbar
    hline(r, 16, 1, 15, 2, WOOD_D)
    # longsword hung centre
    for i in range(7):
        px(r, 16, 8, 4 + i, STEEL)
    vline(r, 16, 8, 4, 10, brighten(STEEL, 1.2))
    px(r, 16, 8, 3, (226, 234, 244, 255))           # tip glint
    rect(r, 16, 7, 11, 9, 11, GOLD)                 # guard
    vline(r, 16, 8, 12, 13, (74, 48, 30, 255))      # grip
    px(r, 16, 8, 14, GOLD_D)                        # pommel
    # spear hung left
    for i in range(9):
        px(r, 16, 4, 5 + i, (96, 66, 38, 255))
    px(r, 16, 4, 4, STEEL)
    px(r, 16, 4, 3, (226, 234, 244, 255))           # head glint
    # axe hung right
    for i in range(9):
        px(r, 16, 11, 6 + i, (96, 66, 38, 255))
    rect(r, 16, 10, 3, 13, 6, STEEL)
    vline(r, 16, 10, 3, 6, brighten(STEEL, 1.2))
    vline(r, 16, 13, 3, 6, STEEL_D)
    outline(r, 16, (26, 22, 18, 255))
    png(folder / "weapon_rack.png", 16, 16, r)

    # -- trophy skull: old bones stacked under a staring skull --
    k = canvas(16, 16, (146, 138, 120, 255))
    for y in range(0, 16, 5):                       # bone dust strata
        hline(k, 16, 0, 16, y, (128, 120, 104, 255))
    disc(k, 16, 8, 6, 4, (222, 214, 196, 255))      # great skull
    rect(k, 16, 5, 7, 10, 9, (222, 214, 196, 255))  # jaw block
    px(k, 16, 6, 6, (30, 26, 24, 255)); px(k, 16, 9, 6, (30, 26, 24, 255))  # eyes
    px(k, 16, 7, 6, (198, 190, 172, 255)); px(k, 16, 8, 6, (198, 190, 172, 255))
    rect(k, 16, 7, 8, 8, 8, (30, 26, 24, 255))      # nasal pit
    for x in (6, 8):                                # teeth gaps
        vline(k, 16, x, 9, 9, (30, 26, 24, 255))
    disc(k, 16, 2, 12, 2, (206, 198, 180, 255))     # lesser skulls
    px(k, 16, 2, 12, (30, 26, 24, 255))
    disc(k, 16, 13, 13, 2, (206, 198, 180, 255))
    px(k, 16, 13, 13, (30, 26, 24, 255))
    hline(k, 16, 4, 12, 14, (186, 178, 160, 255))   # crossed long bone
    vline(k, 16, 8, 11, 15, (186, 178, 160, 255))
    jitter(k, 16, 13, 5)
    png(folder / "trophy_skull.png", 16, 16, k)

    # -- hearth lantern: black iron cage around a warm amber heart --
    hl = canvas(16, 16, (34, 30, 28, 255))
    rect(hl, 16, 2, 2, 13, 13, (58, 48, 40, 255))   # iron frame
    rect(hl, 16, 3, 3, 12, 12, (250, 168, 72, 255))  # amber glass
    rect(hl, 16, 4, 4, 11, 11, (252, 196, 108, 255))
    rect(hl, 16, 6, 6, 9, 9, (255, 226, 156, 255))   # hot core
    px(hl, 16, 7, 7, (255, 246, 208, 255)); px(hl, 16, 8, 7, (255, 246, 208, 255))
    px(hl, 16, 7, 8, (255, 238, 180, 255)); px(hl, 16, 8, 8, (255, 238, 180, 255))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # corner rivets
        px(hl, 16, x, y, (16, 14, 14, 255))
    rect(hl, 16, 7, 0, 8, 1, (16, 14, 14, 255))      # hanging loop
    png(folder / "hearth_lantern.png", 16, 16, hl)

    # -- supply crate top: strapped lid with a hoist ring --
    ct = canvas(16, 16, (128, 96, 58, 255))
    for y in range(0, 16, 4):
        hline(ct, 16, 0, 16, y + 3, (98, 72, 44, 255))
        hline(ct, 16, 0, 16, y, (144, 110, 68, 255))
    rect(ct, 16, 0, 0, 15, 1, (70, 74, 78, 255))     # iron straps
    rect(ct, 16, 0, 14, 15, 15, (70, 74, 78, 255))
    rect(ct, 16, 6, 6, 9, 9, (94, 98, 104, 255))     # hoist ring plate
    disc(ct, 16, 8, 8, 2, (0, 0, 0, 0))
    disc(ct, 16, 8, 8, 1, (60, 62, 66, 255))
    png(folder / "supply_crate_top.png", 16, 16, ct)

    # -- supply crate side: planks, straps, burnt-in crown brand --
    cs = canvas(16, 16, (120, 90, 54, 255))
    for y in range(0, 16, 4):
        hline(cs, 16, 0, 16, y + 3, (92, 68, 42, 255))
        hline(cs, 16, 0, 16, y, (136, 104, 64, 255))
    rect(cs, 16, 2, 0, 3, 15, (70, 74, 78, 255))     # vertical straps
    rect(cs, 16, 12, 0, 13, 15, (70, 74, 78, 255))
    px(cs, 16, 2, 2, (96, 100, 106, 255)); px(cs, 16, 13, 13, (52, 54, 58, 255))
    for (bx, by) in ((6, 6), (7, 6), (8, 6), (9, 6),  # crown brand
                     (6, 7), (9, 7), (6, 8), (9, 8),
                     (6, 9), (7, 9), (8, 9), (9, 9),
                     (7, 5), (8, 5)):
        px(cs, 16, bx, by, (74, 48, 26, 255))
    jitter(cs, 16, 17, 5)
    png(folder / "supply_crate_side.png", 16, 16, cs)

    # -- road stone: worn cobbles with cart ruts --
    rs = canvas(16, 16, (72, 70, 68, 255))
    stones = [(0, 0, 7, 6), (8, 0, 7, 4), (0, 7, 4, 4), (5, 5, 5, 5),
              (11, 5, 4, 6), (0, 12, 6, 3), (7, 11, 5, 4), (13, 12, 3, 3)]
    state = 3
    for (x0, y0, w, h) in stones:
        shade = 118 + (state % 3) * 14
        state = (state * 31 + 7) & 0xffff
        base = (shade, shade - 4, shade - 8, 255)
        rect(rs, 16, x0, y0, x0 + w - 1, y0 + h - 1, base)
        hline(rs, 16, x0, x0 + w - 1, y0, (shade + 22, shade + 18, shade + 12, 255))
        hline(rs, 16, x0, x0 + w - 1, y0 + h - 1, (shade - 26, shade - 28, shade - 30, 255))
    hline(rs, 16, 0, 16, 3, (58, 56, 54, 255))       # wheel ruts
    hline(rs, 16, 0, 16, 12, (58, 56, 54, 255))
    px(rs, 16, 4, 3, (84, 82, 78, 255)); px(rs, 16, 11, 12, (84, 82, 78, 255))
    jitter(rs, 16, 21, 6)
    png(folder / "road_stone.png", 16, 16, rs)

    # -- arrow slit (cutout): masonry pierced by a cross-shaped sight --
    a = canvas(16, 16, (96, 92, 88, 255))
    blocks = [(0, 0, 8, 5), (8, 0, 8, 5), (0, 5, 5, 5), (5, 5, 6, 5),
              (11, 5, 5, 5), (0, 10, 8, 6), (8, 10, 8, 6)]
    for (x0, y0, w, h) in blocks:
        rect(a, 16, x0, y0, x0 + w - 1, y0 + h - 1, (126, 122, 116, 255))
        hline(a, 16, x0, x0 + w - 1, y0, (148, 144, 136, 255))
        vline(a, 16, x0, y0, y0 + h - 1, (148, 144, 136, 255))
        hline(a, 16, x0, x0 + w - 1, y0 + h - 1, (100, 96, 92, 255))
    jitter(a, 16, 7, 5)
    # cut the sight: tall slit + cross bar, chipped dark rim
    for (x0, y0, x1, y1) in ((7, 3, 8, 12), (4, 7, 11, 8)):
        rect(a, 16, x0, y0, x1, y1, (0, 0, 0, 0))
    for (x0, y0, x1, y1) in ((6, 3, 6, 12), (9, 3, 9, 12),
                             (4, 6, 11, 6), (4, 9, 11, 9)):
        rect(a, 16, x0, y0, x1, y1, (74, 70, 66, 255))
    png(folder / "arrow_slit.png", 16, 16, a)


def cannon_block_textures(folder):
    """Two 16x16 block textures: dark bronze barrel + oak-and-iron carriage."""
    barrel = canvas(16, 16)
    bronze, bronze_d, bronze_l = (86, 92, 92, 255), (56, 62, 64, 255), (128, 136, 136, 255)
    for y in range(0, 16):
        for x in range(0, 16):
            px(barrel, 16, x, y, bronze if (x + y) % 3 else bronze_d)
    for x in range(0, 16):
        px(barrel, 16, x, 3, bronze_l); px(barrel, 16, x, 11, bronze_d)
    for x in (2, 3, 12, 13):  # reinforcing bands
        for y in range(0, 16):
            px(barrel, 16, x, y, bronze_l if x in (2, 3) else bronze_d)
    for y in (7, 8):          # muzzle bore shadow
        for x in (0, 1, 14, 15):
            px(barrel, 16, x, y, (18, 18, 20, 255))
    jitter(barrel, 16, 8, 3)
    save16(folder / "cannon_barrel.png", barrel)

    carriage = canvas(16, 16)
    oak, oak_d, oak_l = (118, 84, 48, 255), (84, 58, 32, 255), (146, 108, 66, 255)
    for y in range(0, 16):
        for x in range(0, 16):
            px(carriage, 16, x, y, oak if y % 4 else oak_d)
    for x in range(0, 16, 5):
        for y in range(0, 16):
            px(carriage, 16, x, y, oak_d)
    for y in range(2, 15, 12):  # iron straps
        for x in range(0, 16):
            px(carriage, 16, x, y, (70, 74, 78, 255))
    for cx in (3, 12):          # wheel hubs
        disc(carriage, 16, cx, 8, 2, oak_d)
        disc(carriage, 16, cx, 8, 1, (54, 56, 60, 255))
    jitter(carriage, 16, 12, 3)
    save16(folder / "cannon_carriage.png", carriage)


def camp_kit_texture(path):
    """16x16: a rolled canvas tent with a peeking campfire and bedroll."""
    p = canvas(16, 16, (0, 0, 0, 0))
    canvas_c, canvas_d = (226, 220, 202, 255), (188, 180, 160, 255)
    rope = (140, 52, 44, 255)
    # rolled tent bundle
    for y in range(4, 10):
        for x in range(2, 13):
            px(p, 16, x, y, canvas_c)
    for x in range(2, 13):
        px(p, 16, x, 4, (240, 236, 222, 255))
        px(p, 16, x, 9, canvas_d)
    for y in range(4, 10):                          # tie straps
        px(p, 16, 4, y, rope); px(p, 16, 10, y, rope)
    # peeking flame
    px(p, 16, 12, 8, (240, 160, 60, 255)); px(p, 16, 13, 7, (252, 200, 110, 255))
    px(p, 16, 12, 7, (252, 200, 110, 255)); px(p, 16, 13, 8, (240, 160, 60, 255))
    # bedroll underneath
    hline(p, 16, 3, 12, 11, (150, 90, 60, 255))
    hline(p, 16, 3, 12, 12, (110, 62, 40, 255))
    px(p, 16, 2, 11, (238, 234, 220, 255)); px(p, 16, 2, 12, (200, 194, 176, 255))
    outline(p, 16, (40, 34, 26, 255))
    save16(path, p)


def treasure_map_texture(path):
    """16x16: a cracked old map with roads, a coastline and a red X."""
    p = canvas(16, 16)
    paper = (214, 196, 158, 255)
    paper_d = (176, 158, 120, 255)
    ink = (70, 52, 36, 255)
    for y in range(0, 16):
        for x in range(0, 16):
            shade = paper if (x * 7 + y * 13) % 9 else paper_d
            px(p, 16, x, y, shade)
    # coastline + water
    for y in range(0, 16):
        px(p, 16, 0, y, (86, 130, 170, 255))
        px(p, 16, 1, y, (86, 130, 170, 255))
    px(p, 16, 2, 3, paper); px(p, 16, 2, 10, paper)
    vline(p, 16, 2, 5, 9, (86, 130, 170, 255))
    # a road and a ridge
    for i in range(9):
        px(p, 16, 4 + i, 12 - i, ink)
        px(p, 16, 5 + i, 12 - i, paper_d)
    for x in (9, 10, 11):
        px(p, 16, x, 3, (120, 120, 108, 255)); px(p, 16, x, 4, (100, 100, 90, 255))
    # little trees
    px(p, 16, 5, 4, (74, 120, 62, 255)); px(p, 16, 6, 5, (74, 120, 62, 255))
    px(p, 16, 12, 12, (74, 120, 62, 255))
    # THE X
    px(p, 16, 11, 8, (178, 48, 42, 255)); px(p, 16, 13, 8, (178, 48, 42, 255))
    px(p, 16, 12, 9, (178, 48, 42, 255))
    px(p, 16, 11, 10, (178, 48, 42, 255)); px(p, 16, 13, 10, (178, 48, 42, 255))
    px(p, 16, 11, 8, (220, 90, 80, 255))
    # burnt corner
    px(p, 16, 15, 0, (60, 44, 30, 255)); px(p, 16, 14, 0, (96, 72, 46, 255)); px(p, 16, 15, 1, (96, 72, 46, 255))
    outline(p, 16, (52, 40, 28, 255))
    save16(path, p)


def farmer_hoe_texture(path):
    """16x16: iron blade with a green field ribbon, worn oak handle.""" 
    p = canvas(16, 16)
    handle = (122, 84, 48, 255)
    handle_d = (92, 62, 34, 255)
    blade = (208, 208, 214, 255)
    blade_l = (238, 238, 242, 255)
    blade_d = (156, 156, 164, 255)
    ribbon = (96, 152, 82, 255)
    # handle diagonal
    for i in range(8):
        x, y = 3 + i, 12 - i
        px(p, 16, x, y, handle)
        px(p, 16, x, y + 1, handle_d)
    px(p, 16, 2, 13, handle_d)
    # iron blade: top-left arc
    for (bx, by) in ((2, 2), (3, 2), (4, 2), (5, 2), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (2, 5)):
        px(p, 16, bx, by, blade)
    px(p, 16, 2, 2, blade_l); px(p, 16, 3, 3, blade_l)
    px(p, 16, 5, 2, blade_d); px(p, 16, 3, 4, blade_d); px(p, 16, 2, 5, blade_d)
    # binding + green ribbon on the shaft
    px(p, 16, 5, 9, (72, 50, 28, 255))
    px(p, 16, 6, 8, ribbon); px(p, 16, 5, 8, ribbon); px(p, 16, 6, 7, ribbon)
    px(p, 16, 4, 10, ribbon)
    jitter(p, 16, 4, 3)
    save16(path, p)


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
    save16(path, p)


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
    save16(path, p)


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
    """A full 64x64 player-layout skin (base + overlay layer).

    Every face of every box is painted, so nothing renders see-through, with
    directional shading, per-culture outfits and facial detail.
    """
    p = canvas(64, 64)
    skin_d, skin_l = darken(face, 0.80), brighten(face, 1.10)
    hair_d, hair_l = darken(hair, 0.72), brighten(hair, 1.15)
    shirt_d, shirt_l = darken(shirt, 0.78), brighten(shirt, 1.12)
    pants_d = darken(pants, 0.75)
    trim_d, trim_l = darken(trim, 0.72), brighten(trim, 1.18)
    BOOT, BOOT_D = (44, 34, 26, 255), (28, 22, 17, 255)
    BELT, BUCKLE = (46, 34, 24, 255), GOLD
    WHITE, INK = (238, 238, 238, 255), (22, 20, 18, 255)

    # ---------------- head (base at origin 0,0; hat overlay at 32,0) ------
    def head(ox, oy, top, side_l, side_r, front, back, bottom):
        rect(p, 64, ox + 8, oy, ox + 16, oy + 8, top)
        rect(p, 64, ox + 16, oy, ox + 24, oy + 8, bottom)
        rect(p, 64, ox, oy + 8, ox + 8, oy + 16, side_r)
        rect(p, 64, ox + 8, oy + 8, ox + 16, oy + 16, front)
        rect(p, 64, ox + 16, oy + 8, ox + 24, oy + 16, side_l)
        rect(p, 64, ox + 24, oy + 8, ox + 32, oy + 16, back)
        vline(p, 64, ox + 8, oy + 8, oy + 16, darken(front, 0.9))
        vline(p, 64, ox + 15, oy + 8, oy + 16, brighten(front, 1.08))

    head(0, 0, hair, skin_l, skin_d, face, hair_d, skin_d)
    hline(p, 64, 8, 16, 8, hair)                      # fringe
    px(p, 64, 8, 8, hair_d); px(p, 64, 15, 8, hair_l)

    # face: brows, eyes with whites + dark iris, nose, mouth
    px(p, 64, 10, 10, darken(hair, 0.85)); px(p, 64, 13, 10, darken(hair, 0.85))
    for bx in (10, 13):
        px(p, 64, bx, 11, WHITE); px(p, 64, bx + 1, 11, WHITE)
    px(p, 64, 11, 11, eye); px(p, 64, 14, 11, eye)
    px(p, 64, 12, 12, darken(face, 0.88))             # nose shade
    hline(p, 64, 11, 13, 14, darken(face, 0.70))      # mouth

    # culture flair on the base face
    if name == "pirate":
        rect(p, 64, 10, 15, 14, 17, hair_d)           # beard
        px(p, 64, 9, 15, hair_d); px(p, 64, 15, 15, hair_d)
        hline(p, 64, 8, 11, 10, INK)                  # eyepatch + strap
        px(p, 64, 10, 11, INK); px(p, 64, 11, 11, INK)
        px(p, 64, 15, 13, GOLD)                       # earring
    elif name == "outlaw":
        for dx in (9, 12, 14):                        # stubble
            px(p, 64, dx, 16 if dx != 12 else 17, darken(face, 0.72))
        hline(p, 64, 13, 15, 9, (150, 90, 70, 255))   # scar
    elif name == "sky_captain":
        px(p, 64, 9, 12, darken(face, 0.82)); px(p, 64, 15, 12, darken(face, 0.82))

    # ---------------- hat overlay head (32,0 region) ----------------------
    hat_d = darken(hat, 0.78)
    hat_l = brighten(hat, 1.14)
    if hat_kind == "helm":
        head(32, 0, hat_l, hat, hat, hat, hat_d, hat_d)
        rect(p, 64, 40, 8, 48, 11, hat)               # open the face: keep front upper as visor
        rect(p, 64, 40, 12, 48, 16, INK)              # face plate
        rect(p, 64, 43, 11, 45, 13, INK)              # eye slit
        px(p, 64, 43, 11, (90, 110, 150, 255)); px(p, 64, 44, 11, (120, 150, 190, 255))
        rect(p, 64, 47, 8, 49, 14, brighten(hat, 1.25))  # nasal bar
        hline(p, 64, 32, 64, 7, hat_d)
        if name == "knight":
            # crimson plume crest + steel rivets along the brow
            rect(p, 64, 43, 0, 44, 6, (172, 52, 44, 255))
            vline(p, 64, 43, 0, 6, (204, 74, 60, 255))
            px(p, 64, 43, 0, (232, 104, 84, 255)); px(p, 64, 44, 0, (232, 104, 84, 255))
            for rx in (33, 36, 56, 59):
                px(p, 64, rx, 10, brighten(hat, 1.35))
    elif hat_kind == "band":
        head(32, 0, hat, hat_l, hat, hat, hat_d, hat_d)
        hline(p, 64, 40, 48, 8, hat_l)                # band over the forehead
        hline(p, 64, 32, 64, 11, hat)                 # wrap the sides/back
        rect(p, 64, 56, 12, 60, 15, hat_d)            # knot tail
        px(p, 64, 43, 10, WHITE); px(p, 64, 44, 10, WHITE)   # skull dot
    elif hat_kind == "wide":
        head(32, 0, hat, hat_l, hat, hat_l, hat_d, hat_d)
        rect(p, 64, 32, 12, 64, 14, hat)              # brim ring
        hline(p, 64, 34, 62, 13, hat_d)
        rect(p, 64, 40, 0, 48, 8, hat)                # crown top
        hline(p, 64, 40, 48, 7, (120, 88, 52, 255))   # hat band
    elif hat_kind == "goggles":
        head(32, 0, hat, hat_l, hat, hat, hat_d, hat_d)
        hline(p, 64, 40, 48, 9, (150, 158, 168, 255)) # strap
        for gx in (42, 46):
            px(p, 64, gx, 9, (36, 40, 48, 255)); px(p, 64, gx + 1, 9, (36, 40, 48, 255))
            px(p, 64, gx, 10, (110, 220, 235, 255)); px(p, 64, gx + 1, 10, (140, 235, 248, 255))
    elif hat_kind == "mohawk":
        # war-paint face + a bone/feather crest strip
        rect(p, 64, 40, 4, 48, 8, (206, 196, 170, 255))       # crest band
        for x in (41, 43, 45, 47):
            px(p, 64, x, 3, (190, 60, 46, 255))               # war plume
        hline(p, 64, 40, 48, 10, (150, 40, 36, 255))          # paint band
        px(p, 64, 42, 12, (150, 40, 36, 255)); px(p, 64, 46, 12, (150, 40, 36, 255))
    elif hat_kind == "straw":
        head(32, 0, hat, hat_l, hat, hat_l, hat_d, hat_d)
        rect(p, 64, 34, 10, 62, 12, hat)                      # wide straw brim
        hline(p, 64, 36, 60, 11, hat_d)
        rect(p, 64, 40, 0, 48, 8, hat)                        # crown
        hline(p, 64, 40, 48, 7, (120, 96, 52, 255))
    else:  # simple cap
        head(32, 0, hat, hat, hat, hat, hat_d, hat_d)

    # ---------------- torso ------------------------------------------------
    rect(p, 64, 20, 16, 28, 20, shirt_d)              # top
    rect(p, 64, 28, 16, 36, 20, pants)                # bottom
    rect(p, 64, 16, 20, 20, 32, shirt_d)              # right
    rect(p, 64, 20, 20, 28, 32, shirt)                # front
    rect(p, 64, 28, 20, 32, 32, shirt_l)              # left
    rect(p, 64, 32, 20, 40, 32, shirt_d)              # back
    vline(p, 64, 20, 20, 32, shirt_d); vline(p, 64, 27, 20, 32, shirt_l)
    rect(p, 64, 20, 30, 28, 32, BELT)
    px(p, 64, 23, 30, BUCKLE); px(p, 64, 24, 31, BUCKLE)

    if name == "knight":
        rect(p, 64, 22, 20, 26, 32, trim)             # tabard
        px(p, 64, 23, 22, GOLD); px(p, 64, 24, 22, GOLD)
        px(p, 64, 23, 23, GOLD); px(p, 64, 24, 23, GOLD); px(p, 64, 22, 23, GOLD); px(p, 64, 25, 23, GOLD)
        for cx in (23, 24, 25):                       # gold crown emblem
            px(p, 64, cx, 25, GOLD)
        px(p, 64, 23, 26, GOLD); px(p, 64, 25, 26, GOLD)
        rect(p, 64, 16, 20, 20, 32, trim_d)           # pauldron stripe
        rect(p, 64, 28, 20, 32, 32, trim_l)
        hline(p, 64, 20, 28, 24, trim_l)              # breastplate ridge
        px(p, 64, 20, 24, trim); px(p, 64, 27, 24, trim_d)
    elif name == "pirate":
        rect(p, 64, 20, 20, 22, 30, trim)             # open vest edges
        rect(p, 64, 26, 20, 28, 30, trim_d)
        rect(p, 64, 20, 26, 28, 28, (150, 44, 44, 255))  # sash
        px(p, 64, 24, 26, GOLD)
        rect(p, 64, 32, 20, 40, 32, shirt_d)
    elif name == "outlaw":
        rect(p, 64, 20, 20, 28, 22, trim)             # poncho shoulders
        for yy in range(24, 31):                      # bandolier
            px(p, 64, 20 + (yy - 24), yy, (58, 44, 30, 255))
            if (yy - 24) % 2 == 0:
                px(p, 64, 21 + (yy - 24), yy, (196, 160, 84, 255))
        rect(p, 64, 32, 20, 40, 32, darken(shirt, 0.9))
    elif name == "sky_captain":
        rect(p, 64, 20, 20, 28, 23, trim)             # flight collar
        for bx in (22, 25):                           # brass buttons
            px(p, 64, bx, 25, GOLD); px(p, 64, bx, 27, GOLD)
        rect(p, 64, 32, 20, 40, 32, shirt_d)

    # ---------------- arms (right base; left mirrored below) ---------------
    def arm(ox, oy, sleeve, hand, sleeve_d, sleeve_l):
        rect(p, 64, ox + 4, oy, ox + 8, oy + 4, sleeve)       # top
        rect(p, 64, ox + 8, oy, ox + 12, oy + 4, hand)        # bottom (hand)
        rect(p, 64, ox, oy + 4, ox + 4, oy + 16, sleeve_d)    # right
        rect(p, 64, ox + 4, oy + 4, ox + 8, oy + 16, sleeve)  # front
        rect(p, 64, ox + 8, oy + 4, ox + 12, oy + 16, sleeve_l)  # left
        rect(p, 64, ox + 12, oy + 4, ox + 16, oy + 16, sleeve_d)  # back
        rect(p, 64, ox + 4, oy + 12, ox + 8, oy + 16, hand)   # hand rows
        vline(p, 64, ox + 4, oy + 4, oy + 12, sleeve_d)
        vline(p, 64, ox + 7, oy + 4, oy + 12, sleeve_l)

    arm(40, 16, shirt, skin_d, shirt_d, shirt)            # right arm
    arm(32, 48, shirt_l, skin_l, shirt_d, shirt_l)        # left arm
    for ox, oy in ((40, 16), (32, 48)):                   # cuffs
        hline(p, 64, ox + 4, ox + 8, oy + 3, trim if name != "knight" else trim_d)
    if name == "knight":                                  # steel bracers
        for ox, oy in ((40, 16), (32, 48)):
            rect(p, 64, ox + 4, oy + 10, ox + 8, oy + 12, trim)
            hline(p, 64, ox + 4, ox + 8, oy + 10, trim_l)
            px(p, 64, ox + 4, oy + 12, trim_d); px(p, 64, ox + 7, oy + 12, GOLD)
    if name == "pirate":                                  # striped sleeves
        for ox, oy in ((40, 16), (32, 48)):
            hline(p, 64, ox, ox + 16, oy + 7, trim_d)

    # ---------------- legs --------------------------------------------------
    def leg(ox, oy):
        rect(p, 64, ox + 4, oy, ox + 8, oy + 4, pants)        # top
        rect(p, 64, ox + 8, oy, ox + 12, oy + 4, BOOT_D)      # bottom
        rect(p, 64, ox, oy + 4, ox + 4, oy + 16, pants_d)
        rect(p, 64, ox + 4, oy + 4, ox + 8, oy + 16, pants)
        rect(p, 64, ox + 8, oy + 4, ox + 12, oy + 16, pants)
        rect(p, 64, ox + 12, oy + 4, ox + 16, oy + 16, pants_d)
        rect(p, 64, ox, oy + 12, ox + 16, oy + 16, BOOT)      # boots
        rect(p, 64, ox + 4, oy + 12, ox + 8, oy + 16, BOOT)
        hline(p, 64, ox, ox + 16, oy + 15, BOOT_D)
        hline(p, 64, ox + 4, ox + 8, oy + 3, trim_d)          # waistband edge

    leg(0, 16)    # right leg
    leg(16, 48)   # left leg
    if name == "knight":                                  # plate greaves
        for ox, oy in ((0, 16), (16, 48)):
            rect(p, 64, ox + 4, oy + 8, ox + 8, oy + 11, trim_d)
            hline(p, 64, ox + 4, ox + 8, oy + 8, trim)
            px(p, 64, ox + 6, oy + 10, GOLD)

    # ---------------- torso overlay (vest / scarf / cloak) ------------------
    if name == "knight":
        rect(p, 64, 20, 36, 28, 48, trim_d)
        rect(p, 64, 32, 36, 40, 48, trim_d)
        hline(p, 64, 20, 28, 36, trim)
    elif name == "pirate":
        rect(p, 64, 16, 36, 20, 48, trim_d)
        rect(p, 64, 36, 36, 40, 48, trim_d)
        hline(p, 64, 20, 28, 44, (150, 44, 44, 255))
    elif name == "outlaw":
        rect(p, 64, 20, 36, 28, 44, trim)
        rect(p, 64, 32, 36, 40, 44, trim_d)
        hline(p, 64, 32, 40, 40, (58, 44, 30, 255))
    elif name == "sky_captain":
        rect(p, 64, 20, 36, 28, 40, trim)             # scarf wrap
        rect(p, 64, 34, 38, 38, 50, trim_d)           # scarf tail
        hline(p, 64, 20, 28, 36, brighten(trim, 1.2))
    elif name == "marauder":
        rect(p, 64, 16, 36, 20, 48, trim_d)           # bone necklace band
        rect(p, 64, 36, 36, 40, 48, trim_d)
        for x in (22, 24, 26):                        # bone beads
            px(p, 64, x, 38, (222, 212, 184, 255))
        rect(p, 64, 32, 36, 40, 48, darken(shirt, 0.85))
    elif name == "hearthfolk":
        rect(p, 64, 20, 36, 28, 44, trim)             # linen apron
        hline(p, 64, 22, 26, 40, hat_d)               # apron pocket seam

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
    # gilded roundel emblem on the envelope flank
    disc(p, 128, 56, 50, 5, GOLD)
    disc(p, 128, 56, 50, 3, stripe)
    px(p, 128, 55, 49, brighten(GOLD, 1.3)); px(p, 128, 58, 52, darken(GOLD, 0.8))

    # --- nose cap (0,62)-(42,84) & tail cap (38,62)-(80,84)
    for (x0, x1) in ((0, 42), (38, 80)):
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


def _ship_side_band(p, x0, x1, hull, hull_dark, trim, ports=None, portholes=None):
    """One long hull face: gunwale, planked topsides with seams and nails,
    wale stripe, waterline. Optional gun ports or gold portholes."""
    rect(p, 256, x0, 10, x1, 20, hull)
    hline(p, 256, x0, x1, 10, brighten(hull, 1.28))       # gunwale cap
    hline(p, 256, x0, x1, 11, trim)                        # cap trim
    for y in range(13, 17):
        hline(p, 256, x0, x1, y, hull if y % 2 == 1 else darken(hull, 0.94))
    for x in range(x0 + 3, x1 - 2, 7):                     # plank seams + nails
        vline(p, 256, x, 13, 16, darken(hull, 0.86))
        px(p, 256, x + 3, 14, darken(hull, 0.72))
    hline(p, 256, x0, x1, 17, hull_dark)                   # main wale
    hline(p, 256, x0, x1, 18, darken(hull_dark, 0.80))     # waterline
    hline(p, 256, x0, x1, 19, darken(hull_dark, 0.62))
    if ports:
        for px_ in ports:
            rect(p, 256, x0 + px_, 13, x0 + px_ + 3, 16, (18, 16, 18, 255))
            hline(p, 256, x0 + px_, x0 + px_ + 3, 13, (140, 44, 44, 255))
    if portholes:
        for px_ in portholes:
            disc(p, 256, x0 + px_, 15, 1, GOLD)
            px(p, 256, x0 + px_, 15, (30, 34, 52, 255))


def paint_ship(path, hull, hull_dark, sail, sail_dark, trim, flag, emblem="crown"):
    """256x128 sheet matching SailingShipEntityModel's box UV plan.

    Hull box is 28x10x60, so its faces live at: deck top (60,0)-(88,10),
    keel (88,0)-(116,10), long sides (0,10)-(60,20) and (88,10)-(148,20),
    bow (60,10)-(88,20), stern (148,10)-(176,20).
    """
    p = canvas(256, 128, (0, 0, 0, 0))
    pirate = emblem == "skull"

    # --- deck top (60,0)-(88,10): planking with a grating hatch
    rect(p, 256, 60, 0, 88, 10, brighten(hull, 1.16))
    for x in range(62, 88, 4):
        vline(p, 256, x, 0, 10, darken(hull, 0.90))
    rect(p, 256, 68, 3, 80, 8, darken(hull, 0.78))         # grating
    for x in range(69, 80, 2):
        vline(p, 256, x, 4, 7, darken(hull, 0.62))
    hline(p, 256, 66, 82, 0, brighten(hull, 1.3))

    # --- keel (88,0)-(116,10): tarred underside
    rect(p, 256, 88, 0, 116, 10, hull_dark)
    for x in range(90, 116, 5):
        vline(p, 256, x, 0, 10, darken(hull_dark, 0.85))
    hline(p, 256, 88, 116, 4, darken(hull_dark, 0.7))      # keel stripe

    # --- long sides (both), bow, stern
    _ship_side_band(p, 0, 60, hull, hull_dark, trim,
                    ports=(14, 40) if pirate else None,
                    portholes=(16, 42) if not pirate else None)
    _ship_side_band(p, 88, 148, hull, hull_dark, trim,
                    ports=(14, 40) if pirate else None,
                    portholes=(16, 42) if not pirate else None)
    # bow (60,10)-(88,20): vertical stems + figure marks
    rect(p, 256, 60, 10, 88, 20, hull)
    hline(p, 256, 60, 88, 10, brighten(hull, 1.28))
    for x in range(62, 88, 5):
        vline(p, 256, x, 11, 18, darken(hull, 0.88))
    hline(p, 256, 60, 88, 18, darken(hull_dark, 0.80))
    if pirate:
        rect(p, 256, 71, 13, 77, 16, (140, 44, 44, 255))   # red maw
        px(p, 256, 73, 14, (238, 238, 238, 255)); px(p, 256, 75, 14, (238, 238, 238, 255))
    else:
        disc(p, 256, 74, 14, 2, GOLD)                      # gilded bow eye
        px(p, 256, 74, 14, (40, 46, 66, 255))
    # stern (148,10)-(176,20): gallery windows
    rect(p, 256, 148, 10, 176, 20, hull)
    hline(p, 256, 148, 176, 10, brighten(hull, 1.28))
    for wx in (152, 160, 168):
        rect(p, 256, wx, 13, wx + 4, 16, GOLD if not pirate else (232, 200, 96, 255))
        px(p, 256, wx + 1, 14, (44, 38, 30, 255))
    hline(p, 256, 148, 176, 18, darken(hull_dark, 0.80))

    # --- castles (0,70)-(56,88) & (56,70)-(112,88)
    for (x0, x1) in ((0, 56), (56, 112)):
        rect(p, 256, x0, 70, x1, 88, brighten(hull, 1.08))
        for y in range(74, 88, 4):
            hline(p, 256, x0, x1, y, darken(hull, 0.90))
        hline(p, 256, x0, x1, 70, trim)
        hline(p, 256, x0, x1, 80, hull_dark)
        for wx in range(x0 + 6, x1 - 5, 12):               # cabin windows
            rect(p, 256, wx, 82, wx + 3, 85, GOLD if not pirate else (226, 196, 96, 255))
        for x in range(x0 + 4, x1, 9):
            px(p, 256, x, 74, darken(hull, 0.78))          # rail posts

    # --- mast (176,0)-(188,33) & yard (188,0)-(256,4)
    rect(p, 256, 176, 0, 188, 33, darken(hull, 0.9))
    vline(p, 256, 177, 0, 33, brighten(hull, 1.2))
    vline(p, 256, 185, 0, 33, darken(hull_dark, 0.8))
    for y in (6, 20):                                      # rope bands
        hline(p, 256, 176, 188, y, (86, 66, 44, 255))
    rect(p, 256, 188, 0, 256, 4, darken(hull, 0.9))
    hline(p, 256, 188, 256, 0, brighten(hull, 1.2))
    for x in range(192, 256, 16):
        rect(p, 256, x, 1, x + 3, 3, (86, 66, 44, 255))    # rigging lashings

    # --- sail (0,96)-(66,121): canvas, vertical seams, hem, emblem
    rect(p, 256, 0, 96, 66, 121, sail)
    for x in range(6, 66, 10):
        vline(p, 256, x, 96, 121, sail_dark)
    for y in (99, 108):
        hline(p, 256, 0, 66, y, darken(sail, 0.94))
    hline(p, 256, 0, 66, 96, brighten(sail, 1.12))
    rect(p, 256, 0, 115, 66, 119, trim)
    hline(p, 256, 0, 66, 115, brighten(trim, 1.2))
    cx = 33
    if emblem == "crown":
        rect(p, 256, cx - 5, 101, cx + 5, 107, GOLD)
        for x in (cx - 5, cx - 1, cx + 3):
            rect(p, 256, x, 99, x + 1, 101, GOLD)
        rect(p, 256, cx - 2, 104, cx + 2, 107, sail)
    elif emblem == "skull":
        disc(p, 256, cx, 103, 4, (238, 238, 238, 255))
        rect(p, 256, cx - 4, 106, cx + 4, 109, (238, 238, 238, 255))
        px(p, 256, cx - 2, 102, (24, 24, 28, 255)); px(p, 256, cx + 2, 102, (24, 24, 28, 255))
        rect(p, 256, cx - 2, 108, cx + 2, 109, (24, 24, 28, 255))
        hline(p, 256, cx - 5, cx + 5, 111, (216, 60, 52, 255))
    elif emblem == "gull":
        hline(p, 256, cx - 6, cx - 1, 104, (26, 46, 48, 255))
        hline(p, 256, cx + 1, cx + 6, 104, (26, 46, 48, 255))
        px(p, 256, cx - 1, 103, (26, 46, 48, 255)); px(p, 256, cx + 1, 103, (26, 46, 48, 255))
        hline(p, 256, cx - 3, cx + 3, 109, trim)

    # --- pennant (70,96)-(96,104): swallow-tail streamer
    rect(p, 256, 70, 96, 96, 104, flag)
    rect(p, 256, 91, 96, 96, 104, (0, 0, 0, 0))            # tail notch
    px(p, 256, 92, 99, (0, 0, 0, 0)); px(p, 256, 93, 100, (0, 0, 0, 0))
    hline(p, 256, 70, 91, 96, brighten(flag, 1.3))
    vline(p, 256, 70, 96, 104, GOLD)

    jitter(p, 256, 9, 3)
    png(path, 256, 128, p)


def paint_galleon(path):
    """256x256 sheet matching GalleonEntityModel's box UV plan.

    Hull 24x14x96: deck (96,0)-(120,24), keel (120,0)-(144,24), gun-deck
    sides (0,24)-(96,38) and (120,24)-(216,38), bow (96,24)-(120,38),
    stern (216,24)-(240,38). Castles, masts, yards, bowsprit, nest, canvas,
    flag and pennant fill the rest.
    """
    p = canvas(256, 256, (0, 0, 0, 0))
    hull = (52, 40, 34, 255)
    hull_l = (74, 58, 46, 255)
    hull_dark = (34, 26, 24, 255)
    trim = (140, 44, 44, 255)
    gold = (226, 192, 92, 255)
    rope = (96, 74, 48, 255)
    sail_c = (52, 50, 56, 255)
    sail_d = (38, 37, 42, 255)

    def band(x0, x1, y0, y1, ports):
        rect(p, 256, x0, y0, x1, y1, hull)
        hline(p, 256, x0, x1, y0, hull_l)                  # gunwale
        hline(p, 256, x0, x1, y0 + 1, trim)
        for y in range(y0 + 3, y1 - 3):
            hline(p, 256, x0, x1, y, hull if y % 2 == 1 else darken(hull, 0.92))
        for x in range(x0 + 4, x1 - 3, 9):
            vline(p, 256, x, y0 + 3, y1 - 3, darken(hull, 0.85))
        hline(p, 256, x0, x1, y1 - 3, hull_dark)           # wale
        hline(p, 256, x0, x1, y1 - 2, darken(hull_dark, 0.85))
        hline(p, 256, x0, x1, y1 - 1, (20, 18, 20, 255))   # waterline
        for gx in ports:                                   # gun ports with red lids
            rect(p, 256, x0 + gx, y0 + 4, x0 + gx + 4, y0 + 9, (16, 14, 16, 255))
            hline(p, 256, x0 + gx, x0 + gx + 4, y0 + 4, trim)
            px(p, 256, x0 + gx + 1, y0 + 5, (60, 52, 44, 255))

    # --- hull faces
    band(0, 96, 24, 38, (8, 30, 52, 74))
    band(120, 216, 24, 38, (8, 30, 52, 74, 96))
    rect(p, 256, 96, 24, 120, 38, hull)                    # bow
    hline(p, 256, 96, 120, 24, hull_l)
    for x in range(99, 120, 6):
        vline(p, 256, x, 25, 34, darken(hull, 0.88))
    rect(p, 256, 104, 27, 112, 31, trim)                   # red maw
    px(p, 256, 106, 28, (238, 238, 238, 255)); px(p, 256, 109, 28, (238, 238, 238, 255))
    hline(p, 256, 96, 120, 35, darken(hull_dark, 0.85))
    rect(p, 256, 216, 24, 240, 38, hull)                   # stern gallery
    hline(p, 256, 216, 240, 24, hull_l)
    for wx in (220, 227, 234):
        rect(p, 256, wx, 27, wx + 5, 32, gold)
        px(p, 256, wx + 2, 29, (44, 38, 30, 255))
    hline(p, 256, 216, 240, 35, darken(hull_dark, 0.85))
    rect(p, 256, 96, 0, 120, 24, brighten(hull, 1.14))     # deck
    for x in range(98, 120, 4):
        vline(p, 256, x, 0, 24, darken(hull, 0.90))
    rect(p, 256, 102, 8, 114, 16, darken(hull, 0.76))      # main hatch
    for x in range(104, 114, 2):
        vline(p, 256, x, 9, 15, darken(hull, 0.6))
    rect(p, 256, 120, 0, 144, 24, hull_dark)               # keel
    hline(p, 256, 120, 144, 12, darken(hull_dark, 0.7))

    # --- castles
    for (x0, x1, y0, y1) in ((0, 58, 110, 133), (60, 132, 110, 140), (140, 184, 110, 128)):
        rect(p, 256, x0, y0, x1, y1, brighten(hull, 1.06))
        for y in range(y0 + 4, y1, 4):
            hline(p, 256, x0, x1, y, darken(hull, 0.90))
        hline(p, 256, x0, x1, y0, trim)
        mid_y = (y0 + y1) // 2
        hline(p, 256, x0, x1, mid_y, hull_dark)
        for wx in range(x0 + 5, x1 - 6, 11):
            rect(p, 256, wx, mid_y + 2, wx + 3, mid_y + 5, gold)
    # aft-topsail driver: gold scrollwork on the stern castle top
    hline(p, 256, 62, 130, 111, gold)

    # --- masts, yards, bowsprit, nest
    for (x0, x1, y0, y1) in ((0, 16, 140, 190), (20, 32, 140, 179)):
        rect(p, 256, x0, y0, x1, y1, darken(hull, 0.9))
        vline(p, 256, x0 + 1, y0, y1, brighten(hull, 1.2))
        for y in range(y0 + 6, y1, 12):
            hline(p, 256, x0, x1, y, rope)
    for (x0, x1, y0, y1) in ((40, 132, 140, 144), (140, 204, 140, 144), (192, 256, 146, 148)):
        rect(p, 256, x0, y0, x1, y1, darken(hull, 0.9))
        hline(p, 256, x0, x1, y0, brighten(hull, 1.15))
        for x in range(x0 + 4, x1 - 3, 14):
            rect(p, 256, x, y0 + 1, x + 3, y0 + 2, rope)
    rect(p, 256, 200, 150, 224, 160, darken(hull, 0.8))    # crow's nest
    hline(p, 256, 200, 224, 150, trim)

    # --- canvas: main (0,196)-(82,227), fore (90,196)-(148,219)
    for (x0, x1, y0, y1) in ((0, 82, 196, 227), (90, 148, 196, 219)):
        rect(p, 256, x0, y0, x1, y1, sail_c)
        for x in range(x0 + 6, x1, 12):
            vline(p, 256, x, y0, y1, sail_d)
        hline(p, 256, x0, x1, y0 + 3, darken(sail_c, 0.9))
        rect(p, 256, x0, y1 - 5, x1, y1 - 1, trim)
    # skull & cross-bones on the main course
    cx = 41
    disc(p, 256, cx, 206, 5, (238, 238, 238, 255))
    rect(p, 256, cx - 5, 210, cx + 5, 214, (238, 238, 238, 255))
    px(p, 256, cx - 3, 205, (24, 24, 28, 255)); px(p, 256, cx + 3, 205, (24, 24, 28, 255))
    rect(p, 256, cx - 2, 211, cx + 2, 213, (24, 24, 28, 255))
    hline(p, 256, cx - 8, cx - 3, 216, (238, 238, 238, 255))
    hline(p, 256, cx + 3, cx + 8, 216, (238, 238, 238, 255))
    hline(p, 256, cx - 6, cx + 6, 217, (238, 238, 238, 255))
    # gull sigil on the fore course
    hline(p, 256, 111, 118, 205, (200, 205, 210, 255))
    px(p, 256, 118, 204, (200, 205, 210, 255)); px(p, 256, 119, 203, (200, 205, 210, 255))

    # --- flag (0,232)-(30,243) & pennant (40,232)-(86,240)
    rect(p, 256, 0, 232, 30, 243, (24, 22, 26, 255))
    disc(p, 256, 15, 237, 3, (238, 238, 238, 255))
    px(p, 256, 13, 236, (24, 24, 28, 255)); px(p, 256, 17, 236, (24, 24, 28, 255))
    hline(p, 256, 12, 18, 241, (216, 60, 52, 255))
    vline(p, 256, 0, 232, 243, GOLD)
    rect(p, 256, 40, 232, 86, 240, trim)
    rect(p, 256, 82, 232, 86, 240, (0, 0, 0, 0))
    hline(p, 256, 40, 82, 232, brighten(trim, 1.3))
    for x in range(46, 80, 8):
        vline(p, 256, x, 232, 240, (24, 22, 26, 255))

    jitter(p, 256, 11, 3)
    png(path, 256, 256, p)


def save16(path, p, outline_color=(30, 26, 24, 255)):
    """Final pass for 16x16 item art: crisp dark outline + a top-left
    light kiss, so icons read cleanly on any hotbar background."""
    outline(p, 16, outline_color)
    for y in range(16):
        for x in range(16):
            c = get(p, 16, x, y)
            if c and c[3] > 64 and (x == 1 or y == 1) and (x + y) % 3 == 0:
                px(p, 16, x, y, brighten(c, 1.22))
    png(path, 16, 16, p)


def icon_texture(path):
    """128x128 mod icon: blood-sunset over the sea, a black-sailed galleon
    closing on a burning watchtower. Reads at every size."""
    p = canvas(128, 128, (0, 0, 0, 0))

    # --- sunset sky bands
    sky = [(252, 132, 60, 255), (240, 104, 62, 255), (214, 74, 66, 255),
           (172, 52, 74, 255), (120, 38, 74, 255), (78, 30, 66, 255)]
    for i, color in enumerate(sky):
        rect(p, 128, 0, i * 12, 128, (i + 1) * 12, color)
    # sun disc half-set on the horizon
    disc(p, 128, 88, 58, 16, (255, 214, 130, 255))
    disc(p, 128, 88, 58, 12, (255, 236, 170, 255))
    # streak clouds
    for (cx, cy, w) in ((24, 22, 34), (48, 34, 26), (92, 18, 30)):
        rect(p, 128, cx, cy, cx + w, cy + 3, (96, 34, 58, 255))
        rect(p, 128, cx + 4, cy - 2, cx + w - 4, cy, (118, 42, 62, 255))

    # --- sea: dark bands with sun glitter
    for i, shade in enumerate(((52, 30, 58, 255), (44, 26, 52, 255), (36, 22, 46, 255), (28, 18, 40, 255))):
        rect(p, 128, 0, 62 + i * 16, 128, 78 + i * 16, shade)
    for i in range(10):  # glitter path under the sun
        x = 84 + (i % 3) * 2
        rect(p, 128, x - i % 4, 64 + i * 5, x + 6 + i % 4, 66 + i * 5, (255, 190, 110, 255))

    # --- the black galleon: hull, two masts, blood-red sails
    rect(p, 128, 14, 74, 66, 86, (16, 12, 16, 255))
    rect(p, 128, 10, 76, 18, 84, (16, 12, 16, 255))   # bow
    rect(p, 128, 60, 70, 70, 80, (24, 18, 22, 255))   # stern castle
    for mx in (26, 46):
        rect(p, 128, mx, 30, mx + 3, 76, (20, 14, 18, 255))
    rect(p, 128, 18, 34, 58, 62, (140, 36, 44, 255))  # main course
    rect(p, 128, 20, 38, 56, 58, (170, 46, 50, 255))
    rect(p, 128, 40, 36, 56, 58, (120, 28, 36, 255))
    # skull emblem on the sail
    disc(p, 128, 34, 47, 5, (232, 228, 224, 255))
    rect(p, 128, 29, 50, 39, 54, (232, 228, 224, 255))
    px(p, 128, 32, 46, (16, 12, 16, 255)); px(p, 128, 36, 46, (16, 12, 16, 255))
    rect(p, 128, 31, 51, 37, 52, (16, 12, 16, 255))
    rect(p, 128, 48, 24, 52, 28, (16, 12, 16, 255))   # pennant
    # gun flashes along the hull
    for gx in (18, 30, 42, 54):
        px(p, 128, gx, 80, (255, 196, 90, 255)); px(p, 128, gx + 1, 80, (255, 232, 150, 255))

    # --- burning watchtower on the near cliff
    rect(p, 128, 0, 70, 26, 128, (30, 24, 30, 255))   # cliff
    rect(p, 128, 4, 34, 22, 88, (52, 48, 56, 255))    # tower
    rect(p, 128, 2, 30, 24, 36, (64, 58, 66, 255))    # parapet
    for y in range(40, 84, 12):
        rect(p, 128, 8, y, 12, y + 5, (24, 20, 26, 255))  # arrow slits
    # flames and smoke from the parapet
    for (fx, fy, fh) in ((8, 22, 8), (13, 18, 12), (18, 24, 6)):
        rect(p, 128, fx, fy, fx + 3, fy + fh, (255, 150, 60, 255))
        rect(p, 128, fx + 1, fy - 3, fx + 2, fy + fh - 3, (255, 210, 110, 255))
    for (sx, sy) in ((12, 10), (16, 6), (10, 2)):
        disc(p, 128, sx, sy, 3, (60, 52, 60, 255))

    # --- gold frame
    for i in range(4):
        hline(p, 128, i, 128 - i, i, (222, 182, 88, 255))
        hline(p, 128, i, 128 - i, 127 - i, (222, 182, 88, 255))
        vline(p, 128, i, i, 128 - i, (222, 182, 88, 255))
        vline(p, 128, 127 - i, i, 128 - i, (222, 182, 88, 255))
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
    farmer_hoe_texture(TEX / "item/farmer_hoe.png")
    camp_kit_texture(TEX / "item/camp_kit.png")
    treasure_map_texture(TEX / "item/treasure_map.png")
    war_weapon_textures()
    food_textures()
    cannon_block_textures(TEX / "block")
    settlement_block_textures(TEX / "block")
    airship_kit_texture(TEX / "item/airship_kit.png")

    # spawn eggs
    spawn_egg_texture(TEX / "item/survivor_spawn_egg.png", (148, 108, 190, 255), (238, 232, 244, 255))
    spawn_egg_texture(TEX / "item/knight_spawn_egg.png", (92, 115, 168, 255), (220, 228, 240, 255))
    spawn_egg_texture(TEX / "item/pirate_spawn_egg.png", (155, 63, 53, 255), (24, 24, 28, 255))
    spawn_egg_texture(TEX / "item/outlaw_spawn_egg.png", (200, 155, 72, 255), (94, 62, 34, 255))
    spawn_egg_texture(TEX / "item/sky_captain_spawn_egg.png", (127, 77, 168, 255), (120, 220, 235, 255))
    spawn_egg_texture(TEX / "item/merchant_ship_spawn_egg.png", (222, 214, 190, 255), (66, 92, 168, 255))
    spawn_egg_texture(TEX / "item/pirate_ship_spawn_egg.png", (36, 32, 38, 255), (178, 52, 52, 255))
    spawn_egg_texture(TEX / "item/galleon_ship_spawn_egg.png", (52, 40, 34, 255), (140, 44, 44, 255))

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
    paint_galleon(TEX / "entity/galleon.png")

    # war & glory cultures
    skin_texture("marauder", face=(198, 148, 118, 255), hair=(28, 24, 24, 255),
                 shirt=(94, 44, 38, 255), trim=(212, 190, 150, 255),
                 pants=(58, 42, 34, 255), hat=(0, 0, 0, 0), hat_kind="mohawk")
    skin_texture("hearthfolk", face=(228, 190, 158, 255), hair=(150, 108, 62, 255),
                 shirt=(122, 155, 78, 255), trim=(214, 200, 168, 255),
                 pants=(96, 74, 52, 255), hat=(206, 178, 96, 255), hat_kind="straw")

    # war & glory eggs
    spawn_egg_texture(TEX / "item/marauder_spawn_egg.png", (148, 43, 43, 255), (24, 22, 20, 255))
    spawn_egg_texture(TEX / "item/hearthfolk_spawn_egg.png", (122, 155, 78, 255), (228, 190, 158, 255))

    # mod icon (both the fabric.mod.json icon and the legacy item icon slot)
    icon_texture(ROOT / "icon.png")
    icon_texture(TEX / "item/icon.png")

    print(f"regenerated all Rival Realms art under {ROOT}")


if __name__ == "__main__":
    main()
