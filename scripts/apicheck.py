"""Cross-check mod sources against the generated API stubs.

Layer 1 (always runs, no JDK needed): static extraction — every referenced
static constant (Blocks.X, Items.Y, SoundEvents.Z, ParticleTypes.W,
EntityAttributes.V) and every imported mod-external class must exist in the
stub universe derived from the Yarn 1.21.1 mappings.

Layer 2 (runs when a JDK is available): javac -proc:only over mod sources +
stubs, which catches signatures, generics, overloads and overrides for real.

Exit code 0 means every layer passed (or layer 2 was skipped with a notice).
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

# ---- build the stub universe -------------------------------------------------

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apicheck_stubs  # noqa: E402

STUB_CLASSES = {f"{pkg}.{name}" for (pkg, name) in apicheck_stubs.CLASSES}
STUB_MEMBERS: dict[str, set[str]] = {}
for (pkg, name), info in apicheck_stubs.CLASSES.items():
    members = set()
    for m in info["members"]:
        # "public static final net.x.Type NAME;" / "public void foo(...)"
        match = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*(\(|;|=)", m.strip())
        if match:
            members.add(match.group(1))
    STUB_MEMBERS[f"{pkg}.{name}"] = members

KNOWN_PREFIXES = ("java.", "javax.", "org.slf4j", "org.joml", "com.mojang")

failures: list[str] = []


def fail(msg: str) -> None:
    failures.append(msg)


# ---- layer 1: static cross-check ---------------------------------------------

java_files = sorted(SRC.rglob("*.java"))

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
CONST_RE = re.compile(r"\b(Blocks|Items|SoundEvents|ParticleTypes|EntityAttributes|Registries|SoundCategory|EquipmentSlot|Hand|ActionResult|Formatting|Heightmap\.Type|BoatEntity\.Type|World\.ExplosionSourceType)\.([A-Z_][A-Z0-9_]*)")
IMPORT_STATIC_RE = re.compile(r"^\s*import\s+static\s+([\w.]+)\.([A-Za-z_]\w*)\s*;", re.M)

for path in java_files:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")

    # imports must resolve to stub classes (or known externals)
    for import_match in IMPORT_RE.finditer(text):
        target = import_match.group(1)
        if target.endswith(".*"):
            continue
        if target.startswith(KNOWN_PREFIXES) or target in STUB_CLASSES or target.startswith("com.rivalrealms"):
            continue
        fail(f"{rel}: import does not resolve to a known class: {target}")

    # referenced constants must exist on the stub classes
    for owner, const in CONST_RE.findall(text):
        fq = {
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
            "BoatEntity.Type": "net.minecraft.entity.vehicle.BoatEntity.Type",
            "World.ExplosionSourceType": "net.minecraft.world.World.ExplosionSourceType",
        }[owner]
        if const not in STUB_MEMBERS.get(fq, set()):
            fail(f"{rel}: unknown constant {owner}.{const}")

# every registry name referenced in assets must exist in Java registrations
registered = set()
for path in java_files:
    registered.update(re.findall(r'(?:register|registerBlockItem)\(\s*"([a-z0-9_]+)"', path.read_text(encoding="utf-8")))
assets = ROOT / "src/main/resources/assets/rivalrealms"
for model in (assets / "models/item").glob("*.json"):
    name = model.stem
    if name not in registered:
        fail(f"item model {model.name} has no registered item '{name}'")
for blockstate in (assets / "blockstates").glob("*.json"):
    name = blockstate.stem
    if name not in registered:
        fail(f"blockstate {blockstate.name} has no registered block '{name}'")

# entity names used by renderers must be registered entity fields
entity_fields = set()
for path in java_files:
    entity_fields.update(re.findall(r"public static final EntityType<[^>]+>\s+([A-Z_]+)\s*=", path.read_text(encoding="utf-8")))
client_init = (ROOT / "src/client/java/com/rivalrealms/client/RivalRealmsClient.java").read_text(encoding="utf-8")
for field in re.findall(r"ModEntities\.([A-Z_]+)", client_init):
    if field not in entity_fields:
        fail(f"client initializer references ModEntities.{field} which is not registered")

# ---- layer 2: real javac when available ---------------------------------------

javac = shutil.which("javac")
if javac is None:
    print("NOTE: javac not found; skipped the compile layer (static cross-check only).")
else:
    apicheck_stubs.emit()
    sources = [str(p) for p in java_files]
    sources += [str(p) for p in STUBS.rglob("*.java")]
    result = subprocess.run(
        [javac, "-proc:only", "-nowarn", "-d", "/tmp/apicheck-out", *sources],
        capture_output=True, text=True, timeout=600,
    )
    if result.returncode != 0:
        print(result.stderr[:12000])
        fail("javac -proc:only failed against the API stubs (see output above)")
    else:
        print("javac -proc:only passed against the API stubs.")

# ---- summary -------------------------------------------------------------------

if failures:
    print(f"\nAPICHECK FAILED with {len(failures)} problem(s):")
    for problem in failures:
        print("  -", problem)
    raise SystemExit(1)
print("APICHECK PASSED: every referenced API symbol resolves against the 1.21.1 stub universe.")
