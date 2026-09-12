"""Fast, dependency-free smoke tests for the mod package.

The Gradle build is the authoritative Java test. These checks catch the easy
release failures before Gradle spends time downloading Minecraft mappings:
malformed JSON, missing referenced assets, broken recipe ids, and jars that do
not contain the metadata/resource tree.
"""
from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
MOD_ID = "rivalrealms"


def fail(message: str) -> None:
    raise SystemExit(f"BUGHUNT FAILED: {message}")


def json_files(root: Path):
    return list(root.rglob("*.json"))


def check_json_files() -> None:
    for path in json_files(RESOURCES):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:  # pragma: no cover - script is the test
            fail(f"invalid JSON in {path.relative_to(ROOT)}: {exc}")


def check_asset_references() -> None:
    missing = []

    def inspect(value, key):
        if isinstance(value, dict):
            for child_key, child_value in value.items():
                inspect(child_value, child_key)
        elif isinstance(value, list):
            for child_value in value:
                inspect(child_value, key)
        elif isinstance(value, str) and value.startswith(f"{MOD_ID}:"):
            asset = value.split(":", 1)[1]
            if key in {"parent", "model"}:
                candidate = RESOURCES / "assets" / MOD_ID / "models" / f"{asset}.json"
            else:
                candidate = RESOURCES / "assets" / MOD_ID / "textures" / f"{asset}.png"
            if not candidate.exists():
                missing.append(str(candidate.relative_to(ROOT)))

    for path in json_files(RESOURCES / "assets"):
        try:
            inspect(json.loads(path.read_text(encoding="utf-8")), None)
        except Exception as exc:
            fail(f"could not inspect asset references in {path.relative_to(ROOT)}: {exc}")
    if missing:
        fail("missing referenced assets: " + ", ".join(sorted(set(missing))))


def check_png_assets() -> None:
    """Validate PNG signatures and dimensions without external image tools."""
    for path in RESOURCES.rglob("*.png"):
        data = path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            fail(f"invalid PNG signature: {path.relative_to(ROOT)}")
        if len(data) < 24 or data[12:16] != b"IHDR":
            fail(f"PNG has no IHDR: {path.relative_to(ROOT)}")
        width, height = struct.unpack(">II", data[16:24])
        if not (0 < width <= 2048 and 0 < height <= 2048):
            fail(f"unsafe PNG dimensions in {path.relative_to(ROOT)}: {width}x{height}")

    expected_dimensions = {
        "assets/rivalrealms/textures/entity/survivor/knight.png": (64, 64),
        "assets/rivalrealms/textures/entity/survivor/pirate.png": (64, 64),
        "assets/rivalrealms/textures/entity/survivor/outlaw.png": (64, 64),
        "assets/rivalrealms/textures/entity/survivor/sky_captain.png": (64, 64),
        "assets/rivalrealms/textures/entity/airship.png": (64, 32),
    }
    for relative_path, expected in expected_dimensions.items():
        path = ROOT / "src/main/resources" / relative_path
        data = path.read_bytes() if path.exists() else b""
        actual = struct.unpack(">II", data[16:24]) if len(data) >= 24 else None
        if actual != expected:
            fail(f"unexpected texture dimensions for {relative_path}: {actual}, expected {expected}")


def check_visual_renderers() -> None:
    survivor_renderer = ROOT / "src/client/java/com/rivalrealms/client/SurvivorRenderer.java"
    client_initializer = ROOT / "src/client/java/com/rivalrealms/client/RivalRealmsClient.java"
    airship_renderer = ROOT / "src/client/java/com/rivalrealms/client/AirshipRenderer.java"
    if "ArmorFeatureRenderer" not in survivor_renderer.read_text(encoding="utf-8"):
        fail("survivor renderer does not render equipped armor")
    if "AirshipRenderer::new" not in client_initializer.read_text(encoding="utf-8"):
        fail("airship is still using the generic renderer")
    if "textures/entity/airship.png" not in airship_renderer.read_text(encoding="utf-8"):
        fail("airship renderer has no dedicated texture")
    archetypes = (ROOT / "src/main/java/com/rivalrealms/entity/Archetype.java").read_text(encoding="utf-8")
    for culture in re.findall(r'"([a-z][a-z0-9_]*)",\s*"[A-Z][A-Za-z]+",\s*"[A-Z]', archetypes):
        if not (RESOURCES / "assets/rivalrealms/textures/entity/survivor" / f"{culture}.png").exists():
            fail(f"missing survivor texture for culture: {culture}")


def check_mod_json() -> None:
    mod = json.loads((RESOURCES / "fabric.mod.json").read_text(encoding="utf-8"))
    if mod.get("id") != MOD_ID:
        fail("fabric.mod.json has the wrong id")
    for source_set, entrypoints in mod.get("entrypoints", {}).items():
        if source_set not in {"main", "client"}:
            continue
        for entrypoint in entrypoints:
            class_path = ROOT / "src" / source_set / "java" / Path(entrypoint.replace('.', '/')).with_suffix('.java')
            if not class_path.exists():
                fail(f"entrypoint source is missing: {entrypoint}")
            source = class_path.read_text(encoding="utf-8")
            class_name = entrypoint.rsplit(".", 1)[-1]
            constructors = re.findall(r"\b(public|protected|private)\s+" + re.escape(class_name) + r"\s*\(", source)
            if constructors and "public" not in constructors:
                fail(f"Fabric entrypoint is not publicly constructible: {entrypoint}")


def check_runtime_safety() -> None:
    """Catch common server-crash regressions before the Gradle build starts."""
    java_root = ROOT / "src/main/java"
    java_files = list(java_root.rglob("*.java"))
    forbidden = {
        "System.exit(": "hard process exit",
        "Runtime.getRuntime().halt(": "hard process halt",
        "Thread.sleep(": "blocking server thread sleep",
    }
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        for token, description in forbidden.items():
            if token in text:
                fail(f"{description} in {path.relative_to(ROOT)}")

    required_guards = {
        "src/main/java/com/rivalrealms/world/RealmEvents.java": (
            "isChunkLoaded",
            "surfacePosition",
            "RivalRealms.LOGGER.error",
        ),
        "src/main/java/com/rivalrealms/world/StructureBuilder.java": (
            "getBottomY()",
            "getTopY()",
            "buildScattered",
            "buildScatteredFortress",
            "buildScatteredTown",
            "buildScatteredHarbor",
            "buildCitadel",
            "buildRoyalCity",
            "buildShipyard",
            "buildAirshipYard",
            "buildScatteredSkyport",
            "buildScatteredOutpost",
        ),
        "src/main/java/com/rivalrealms/world/RealmState.java": (
            "MAX_BASES",
            "Math.min(savedBases.size(), MAX_BASES)",
        ),
        "src/main/java/com/rivalrealms/entity/SurvivorEntity.java": (
            "nextTargetScan",
            "owner == null",
        ),
        "src/main/java/com/rivalrealms/world/WorldSettlementGenerator.java": (
            "SITE_SPACING",
            "areaLoaded",
            "markGeneratedSite",
            "planFor",
        ),
    }
    for relative_path, markers in required_guards.items():
        path = ROOT / relative_path
        if not path.exists():
            fail(f"runtime safety source is missing: {relative_path}")
        text = path.read_text(encoding="utf-8")
        missing = [marker for marker in markers if marker not in text]
        if missing:
            fail(f"runtime safety guard missing from {relative_path}: {', '.join(missing)}")

    initializer = (ROOT / "src/main/java/com/rivalrealms/RivalRealms.java").read_text(encoding="utf-8")
    if "ServerChunkEvents.CHUNK_LOAD" not in initializer:
        fail("scattered settlement generator is not registered")

    workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
    for marker in ("python3 scripts/bughunt.py", "clean check build", "Upload release-ready jars"):
        if marker not in workflow:
            fail(f"CI bug-hunt/build step missing: {marker}")


def check_jar(jar_path: Path) -> None:
    if not jar_path.exists():
        fail(f"jar does not exist: {jar_path}")
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        required = {
            "fabric.mod.json",
            "assets/rivalrealms/lang/en_us.json",
            "assets/rivalrealms/textures/entity/survivor/knight.png",
            "assets/rivalrealms/textures/entity/airship.png",
            "com/rivalrealms/RivalRealms.class",
            "com/rivalrealms/client/AirshipRenderer.class",
        }
        missing = sorted(required - names)
        if missing:
            fail("jar is missing: " + ", ".join(missing))
        bad = jar.testzip()
        if bad:
            fail(f"corrupt jar entry: {bad}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    check_json_files()
    check_asset_references()
    check_png_assets()
    check_mod_json()
    check_visual_renderers()
    check_runtime_safety()
    if args.jar:
        check_jar(args.jar)
    print("BUGHUNT PASSED: metadata, JSON, assets and package structure look healthy.")


if __name__ == "__main__":
    main()
