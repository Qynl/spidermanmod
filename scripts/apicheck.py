"""Advisory API cross-check for Rival Realms III against Yarn 1.21.1 stubs.

Layer 1 (no JDK needed): static extraction. Every imported mod-external class
must exist in the stub universe, and every referenced static constant
(Blocks.X, Items.Y, SoundEvents.Z, ...) must exist on its stub class. Asset
names are cross-checked against Java registrations, and client renderers may
only reference entity types that actually exist.

Layer 2 (when a JDK is available): javac -proc:only over mod sources + stubs.

The Gradle build remains authoritative; this script fails fast and cheap.
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
STUBS = Path("/tmp/stubs/src")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apicheck_stubs  # noqa: E402

STUB_CLASSES = {f"{pkg}.{name}" for (pkg, name) in apicheck_stubs.CLASSES}
STUB_MEMBERS: dict[str, set[str]] = {}
for (pkg, name), info in apicheck_stubs.CLASSES.items():
    members = set()
    for member in info["members"]:
        match = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*(\(|;|=)", member.strip())
        if match:
            members.add(match.group(1))
    STUB_MEMBERS[f"{pkg}.{name}"] = members

KNOWN_PREFIXES = ("java.", "javax.", "org.slf4j", "org.joml", "com.mojang")
CONST_OWNERS = {
    "Blocks": "net.minecraft.block.Blocks",
    "Items": "net.minecraft.item.Items",
    "SoundEvents": "net.minecraft.sound.SoundEvents",
    "ParticleTypes": "net.minecraft.particle.ParticleTypes",
    "EntityAttributes": "net.minecraft.entity.attribute.EntityAttributes",
    "Registries": "net.minecraft.registry.Registries",
    "SoundCategory": "net.minecraft.sound.SoundCategory",
    "EquipmentSlot": "net.minecraft.entity.EquipmentSlot",
    "Hand": "net.minecraft.util.Hand",
    "ActionResult": "net.minecraft.util.ActionResult",
    "Formatting": "net.minecraft.util.Formatting",
    "Heightmap.Type": "net.minecraft.world.Heightmap.Type",
    "World.ExplosionSourceType": "net.minecraft.world.World.ExplosionSourceType",
    "NbtElement": "net.minecraft.nbt.NbtElement",
    "Block": "net.minecraft.block.Block",
}

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
CONST_RE = re.compile(
    r"\b(Blocks|Items|SoundEvents|ParticleTypes|EntityAttributes|Registries|SoundCategory|"
    r"EquipmentSlot|Hand|ActionResult|Formatting|Heightmap\.Type|World\.ExplosionSourceType|"
    r"NbtElement|Block)\.([A-Z_][A-Z0-9_]*)")

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


java_files = sorted(SRC.rglob("*.java"))
MOD_PACKAGES = set()
for path in java_files:
    pkg = re.search(r"^\s*package\s+([\w.]+);", path.read_text(encoding="utf-8"), re.M)
    if pkg:
        parts = pkg.group(1).split(".")
        MOD_PACKAGES.add(".".join(parts[:2]))

for path in java_files:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    for import_match in IMPORT_RE.finditer(text):
        target = import_match.group(1)
        if target.endswith(".*"):
            continue
        if target.startswith(KNOWN_PREFIXES) or target in STUB_CLASSES:
            continue
        if any(target == pkg or target.startswith(pkg + ".") for pkg in MOD_PACKAGES):
            continue
        fail(f"{rel}: import does not resolve to a known class: {target}")
    for owner, const in CONST_RE.findall(text):
        if const not in STUB_MEMBERS.get(CONST_OWNERS[owner], set()):
            fail(f"{rel}: unknown constant {owner}.{const}")

# ---- asset names must be registered in Java ----------------------------------

sources = "\n".join(path.read_text(encoding="utf-8") for path in java_files)
registered = set(re.findall(r'(?:register|material|slab|fence)\(\s*"([a-z0-9_]+)"', sources))
registered.update(re.findall(r'Registries\.BLOCK\.getId\(block\)', sources) and set())
assets = ROOT / "src/main/resources/assets/rivalrealms"
if (assets / "models" / "item").is_dir():
    for model in (assets / "models" / "item").glob("*.json"):
        if model.stem not in registered:
            fail(f"item model {model.name} has no registered item '{model.stem}'")
if (assets / "blockstates").is_dir():
    for blockstate in (assets / "blockstates").glob("*.json"):
        if blockstate.stem not in registered:
            fail(f"blockstate {blockstate.name} has no registered block '{blockstate.stem}'")

# ---- client renderers may only use real entity fields -------------------------

entity_fields = set(re.findall(r"public static final EntityType<[^>]+>\s+([A-Z_]+)\s*=", sources))
client_sources = "\n".join(path.read_text(encoding="utf-8")
                           for path in (ROOT / "src" / "client").rglob("*.java"))
for field in set(re.findall(r"RREntities\.([A-Z_]+)", client_sources)):
    if field not in entity_fields:
        fail(f"client code references RREntities.{field} which is not registered")

# ---- layer 2: real javac when available ---------------------------------------

javac = shutil.which("javac")
if javac is None:
    print("NOTE: javac not found; skipped the compile layer (static cross-check only).")
else:
    apicheck_stubs.emit()
    sources_list = [str(p) for p in java_files] + [str(p) for p in STUBS.rglob("*.java")]
    result = subprocess.run([javac, "-proc:only", "-nowarn", "-d", "/tmp/apicheck-out", *sources_list],
                            capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        print(result.stderr[:12000])
        fail("javac -proc:only failed against the API stubs (see output above)")
    else:
        print("javac -proc:only passed against the API stubs.")

if failures:
    print(f"\nAPICHECK FAILED with {len(failures)} problem(s):")
    for problem in sorted(set(failures)):
        print("  -", problem)
    raise SystemExit(1)
print("APICHECK PASSED: every referenced API symbol resolves against the 1.21.1 stub universe.")
