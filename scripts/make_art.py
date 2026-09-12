"""Generate the tiny pixel-art assets used by Rival Realms.

Keeping the source here makes the first-release art reproducible instead of
embedding opaque binary assets with no explanation.
"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/rivalrealms"


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


def rect(pixels, width, x0, y0, x1, y1, color):
    for y in range(max(0, y0), min(y1, len(pixels) // width)):
        for x in range(max(0, x0), min(x1, width)):
            pixels[y * width + x] = color


def skin(name, face, hair, shirt, trim, pants, hat):
    p = canvas(64, 64)
    dark = tuple(max(0, c - 40) for c in face[:3]) + (255,)
    light = tuple(min(255, c + 35) for c in face[:3]) + (255,)
    # Vanilla player skin UV islands.
    rect(p, 64, 8, 8, 16, 16, face)
    rect(p, 64, 8, 0, 16, 8, hair)
    rect(p, 64, 0, 8, 8, 16, dark)
    rect(p, 64, 16, 8, 24, 16, light)
    rect(p, 64, 10, 11, 11, 12, (25, 25, 25, 255))
    rect(p, 64, 13, 11, 14, 12, (25, 25, 25, 255))
    rect(p, 64, 11, 14, 13, 15, dark)
    # outer-layer hair or hat.
    rect(p, 64, 40, 8, 48, 16, hat)
    rect(p, 64, 48, 8, 56, 16, hat)
    rect(p, 64, 40, 0, 56, 8, hat)
    # torso.
    rect(p, 64, 20, 20, 28, 32, shirt)
    rect(p, 64, 20, 16, 28, 20, trim)
    rect(p, 64, 16, 20, 20, 32, dark)
    rect(p, 64, 28, 20, 32, 32, light)
    # arms.
    for x in (44, 36):
        rect(p, 64, x, 20, x + 4, 32, shirt)
        rect(p, 64, x, 16, x + 4, 20, trim)
        rect(p, 64, x, 32, x + 4, 36, face)
        rect(p, 64, x, 20, x + 1, 32, dark)
    # legs.
    for x in (20, 36):
        rect(p, 64, x, 52, x + 4, 64, pants)
        rect(p, 64, x, 48, x + 4, 52, trim)
        rect(p, 64, x, 52, x + 1, 64, dark)
    # belt and small insignia.
    rect(p, 64, 20, 30, 28, 32, trim)
    rect(p, 64, 23, 26, 25, 28, (240, 205, 70, 255))
    png(ROOT / "textures/entity/survivor" / f"{name}.png", 64, 64, p)


def tile(path, base, accent, pixelsize=16):
    p = canvas(pixelsize, pixelsize, base)
    for i in range(0, pixelsize, 4):
        rect(p, pixelsize, i, (i * 3) % pixelsize, min(i + 1, pixelsize), min((i * 3) % pixelsize + 2, pixelsize), accent)
    for i in range(2, pixelsize, 5):
        rect(p, pixelsize, 0, i, pixelsize, i + 1, accent)
    png(ROOT / "textures/block" / f"{path}.png", pixelsize, pixelsize, p)


def item(path, base, accent, emblem):
    p = canvas(16, 16)
    rect(p, 16, 3, 2, 13, 14, base)
    rect(p, 16, 4, 3, 12, 13, accent)
    for x, y in emblem:
        rect(p, 16, x, y, x + 1, y + 1, (245, 225, 130, 255))
    png(ROOT / "textures/item" / f"{path}.png", 16, 16, p)


def egg(path, shell, shadow, spots):
    """A crisp custom spawn egg icon; the culture colors are visible in the tab."""
    p = canvas(16, 16)
    rect(p, 16, 5, 1, 11, 2, shell)
    rect(p, 16, 4, 2, 12, 4, shell)
    rect(p, 16, 3, 4, 13, 12, shell)
    rect(p, 16, 4, 12, 12, 14, shell)
    rect(p, 16, 5, 14, 11, 15, shadow)
    rect(p, 16, 3, 6, 4, 11, shadow)
    rect(p, 16, 12, 6, 13, 11, shadow)
    for x, y in spots:
        rect(p, 16, x, y, x + 2, y + 2, shadow)
    rect(p, 16, 6, 3, 8, 4, (255, 255, 255, 180))
    png(ROOT / "textures/item" / f"{path}.png", 16, 16, p)


def ship_texture(path, hull, trim, sail):
    p = canvas(64, 32)
    # BoatEntityRenderer uses a compact 64x32 UV layout. These broad pixel
    # bands keep the custom hull readable even at normal gameplay distance.
    rect(p, 64, 2, 18, 62, 28, hull)
    rect(p, 64, 8, 16, 56, 20, trim)
    rect(p, 64, 12, 20, 52, 24, hull)
    rect(p, 64, 18, 20, 46, 22, trim)
    rect(p, 64, 27, 4, 30, 20, trim)
    rect(p, 64, 28, 2, 29, 18, (65, 44, 31, 255))
    rect(p, 64, 31, 5, 45, 18, sail)
    rect(p, 64, 32, 7, 44, 9, trim)
    rect(p, 64, 32, 14, 44, 16, trim)
    rect(p, 64, 49, 6, 53, 21, trim)
    png(ROOT / "textures/entity" / f"{path}.png", 64, 32, p)


def medieval_map():
    p = canvas(16, 16, (0, 0, 0, 0))
    rect(p, 16, 2, 1, 14, 15, (210, 176, 105, 255))
    rect(p, 16, 3, 2, 13, 14, (238, 211, 145, 255))
    rect(p, 16, 4, 4, 6, 5, (83, 133, 92, 255))
    rect(p, 16, 7, 3, 9, 6, (83, 133, 92, 255))
    rect(p, 16, 9, 8, 12, 10, (83, 133, 92, 255))
    rect(p, 16, 6, 10, 8, 13, (69, 119, 168, 255))
    rect(p, 16, 10, 4, 11, 5, (190, 55, 48, 255))
    rect(p, 16, 9, 5, 12, 6, (190, 55, 48, 255))
    png(ROOT / "textures/item/medieval_map.png", 16, 16, p)


skin("knight", (225, 177, 132, 255), (55, 38, 28, 255), (42, 74, 128, 255), (185, 48, 48, 255), (47, 52, 65, 255), (185, 48, 48, 255))
skin("pirate", (184, 126, 80, 255), (35, 23, 18, 255), (35, 89, 91, 255), (191, 50, 42, 255), (35, 39, 47, 255), (28, 28, 35, 255))
skin("outlaw", (205, 148, 99, 255), (67, 39, 22, 255), (129, 76, 36, 255), (210, 171, 64, 255), (75, 49, 34, 255), (169, 116, 51, 255))
skin("sky_captain", (220, 167, 125, 255), (54, 29, 68, 255), (93, 55, 145, 255), (62, 207, 204, 255), (40, 45, 68, 255), (50, 142, 170, 255))

tile("crown_brick", (52, 61, 75, 255), (91, 111, 132, 255))
tile("castle_stone", (101, 105, 112, 255), (150, 154, 159, 255))
tile("castle_tiles", (48, 52, 61, 255), (92, 101, 117, 255))
tile("royal_wood", (73, 43, 31, 255), (128, 77, 45, 255))
tile("ship_planks", (105, 60, 34, 255), (156, 96, 43, 255))
tile("frontier_planks", (151, 83, 37, 255), (202, 134, 59, 255))
tile("airship_metal", (63, 106, 119, 255), (109, 181, 190, 255))
tile("realm_banner", (104, 45, 112, 255), (211, 71, 91, 255))

item("recruitment_contract", (221, 201, 153, 255), (127, 88, 49, 255), [(7, 5), (8, 6), (9, 7), (6, 8), (10, 8), (7, 10), (9, 10)])
item("revolver", (62, 66, 70, 255), (143, 95, 49, 255), [(7, 4), (8, 4), (9, 4), (8, 8), (8, 9)])
item("flintlock", (67, 63, 57, 255), (150, 90, 38, 255), [(8, 4), (8, 5), (7, 9), (9, 9)])
item("pirate_boat", (111, 60, 31, 255), (228, 199, 75, 255), [(5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5)])
item("royal_longsword", (77, 81, 92, 255), (211, 180, 75, 255), [(8, 2), (8, 3), (8, 4), (8, 5), (8, 6), (8, 7), (7, 8), (6, 9), (5, 10), (4, 11), (3, 12)])
item("royal_coin", (194, 138, 40, 255), (239, 205, 73, 255), [(6, 5), (7, 4), (8, 5), (7, 6), (8, 7), (9, 6)])
item("royal_jewelry", (103, 70, 135, 255), (222, 190, 62, 255), [(6, 5), (7, 4), (8, 5), (9, 6), (8, 8), (7, 9)])
medieval_map()
egg("survivor_spawn_egg", (214, 172, 111, 255), (104, 66, 52, 255), [(6, 6), (9, 9)])
egg("knight_spawn_egg", (79, 111, 178, 255), (177, 44, 53, 255), [(6, 6), (9, 9)])
egg("pirate_spawn_egg", (38, 116, 116, 255), (187, 48, 43, 255), [(6, 7), (9, 5)])
egg("outlaw_spawn_egg", (183, 109, 45, 255), (213, 177, 64, 255), [(6, 6), (9, 9)])
egg("sky_captain_spawn_egg", (103, 70, 164, 255), (56, 202, 201, 255), [(6, 6), (9, 8)])
egg("merchant_ship_spawn_egg", (54, 128, 150, 255), (222, 190, 62, 255), [(6, 6), (9, 9)])
egg("pirate_ship_spawn_egg", (56, 56, 68, 255), (184, 48, 43, 255), [(6, 7), (9, 5)])
ship_texture("merchant_ship", (91, 48, 33, 255), (222, 190, 62, 255), (238, 234, 205, 255))
ship_texture("pirate_ship", (45, 43, 49, 255), (184, 48, 43, 255), (33, 33, 41, 255))
item("icon",  (39, 31, 56, 255), (64, 179, 190, 255), [(7, 3), (6, 4), (7, 4), (8, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (7, 6), (7, 7), (7, 8), (6, 9), (7, 9), (8, 9), (7, 10), (7, 11)])
# Make the icon larger while retaining the same crisp emblem.
png(ROOT / "icon.png", 64, 64, [((39, 31, 56, 255) if (x + y) % 7 else (64, 179, 190, 255)) for y in range(64) for x in range(64)])
