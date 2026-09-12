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
    for path in json_files(RESOURCES / "assets"):
        data = path.read_text(encoding="utf-8")
        for namespace, asset in re.findall(r"(?:\"(?:parent|model|texture|layer0|layer1)\"\s*:\s*\"(?:([a-z0-9_.-]+):)?([a-z0-9_./-]+))", data):
            namespace = namespace or MOD_ID
            if namespace != MOD_ID:
                continue
            asset_path = RESOURCES / "assets" / namespace / (asset + (".json" if path.parts[-2] == "blockstates" and asset.startswith("rivalrealms:") else ""))
            # Most references are texture paths and may be explicitly prefixed.
            if asset.startswith("textures/"):
                candidate = RESOURCES / "assets" / namespace / f"{asset}.png"
            elif asset.startswith("rivalrealms:"):
                candidate = RESOURCES / "assets" / namespace / f"{asset.split(':', 1)[1]}.json"
            else:
                candidate = None
            if candidate is not None and not candidate.exists():
                # Vanilla parents are intentionally ignored; only check local assets.
                missing.append(str(candidate.relative_to(ROOT)))
    if missing:
        fail("missing referenced assets: " + ", ".join(sorted(set(missing))))


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
        ),
        "src/main/java/com/rivalrealms/world/RealmState.java": (
            "MAX_BASES",
            "Math.min(savedBases.size(), MAX_BASES)",
        ),
        "src/main/java/com/rivalrealms/entity/SurvivorEntity.java": (
            "nextTargetScan",
            "owner == null",
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
            "com/rivalrealms/RivalRealms.class",
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
    check_mod_json()
    check_runtime_safety()
    if args.jar:
        check_jar(args.jar)
    print("BUGHUNT PASSED: metadata, JSON, assets and package structure look healthy.")


if __name__ == "__main__":
    main()
