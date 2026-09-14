"""Fast, dependency-free release checks for Rival Realms III.

Gradle remains the authoritative Java build; these checks catch the cheap
release failures first: malformed JSON, assets referenced but missing,
recipes or loot pointing at unregistered items, translation keys used in
code but absent from en_us.json, sound events without audio files, and PNG
assets with unsafe or wrong dimensions.
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
ASSETS = RESOURCES / "assets/manhunt"
MOD_ID = "manhunt"

EXPECTED_DIMENSIONS = {
    "assets/manhunt/textures/entity/hunter.png": (64, 64),
    "assets/manhunt/icon.png": (128, 128),
}


def fail(message: str) -> None:
    raise SystemExit(f"BUGHUNT FAILED: {message}")


def json_files(root: Path):
    return sorted(root.rglob("*.json"))


def registered_names() -> set[str]:
    """Item and block registry names (sounds are registered separately)."""
    names: set[str] = set()
    for source_path in (ROOT / "src/main/java").rglob("*.java"):
        source = source_path.read_text(encoding="utf-8")
        names.update(re.findall(r'(?:register|material|blockItem|slab|fence)\(\s*"([a-z0-9_]+)"', source))
    return names


def check_json() -> None:
    for path in json_files(RESOURCES):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON in {path.relative_to(ROOT)}: {exc}")


def check_asset_references() -> None:
    names = registered_names()
    missing = []
    for path in json_files(ASSETS / "models") + json_files(ASSETS / "blockstates"):
        data = json.loads(path.read_text(encoding="utf-8"))

        def walk(node):
            if isinstance(node, dict):
                for key, value in node.items():
                    if isinstance(value, str) and value.startswith(f"{MOD_ID}:"):
                        asset = value.split(":", 1)[1]
                        if key in ("parent", "model"):
                            if not (ASSETS / "models" / f"{asset}.json").exists():
                                missing.append(f"{path.name}: missing model {asset}")
                        elif key in ("layer0", "all", "particle") or key in (
                                "north", "south", "east", "west", "up", "down",
                                "metal", "glass", "board", "pole", "cloth", "wood",
                                "steel", "carriage", "barrel", "ball"):
                            if not (ASSETS / "textures" / f"{asset}.png").exists():
                                missing.append(f"{path.name}: missing texture {asset}")
                    else:
                        walk(value)
            elif isinstance(node, list):
                for child in node:
                    walk(child)

        walk(data)
        if path.parent.name == "blockstates" and path.stem not in names:
            missing.append(f"blockstate {path.name} has no registered block")
    for model in (ASSETS / "models" / "item").glob("*.json"):
        if model.stem not in names:
            missing.append(f"item model {model.name} has no registered item")
    if missing:
        fail("; ".join(sorted(set(missing))))


def check_sounds() -> None:
    if not (ASSETS / "sounds.json").exists():
        return
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    for event, definition in sounds.items():
        for entry in definition.get("sounds", []):
            name = entry["name"] if isinstance(entry, dict) else entry
            if name.startswith("minecraft:"):
                continue
            rel = name.split(":", 1)[1]
            target = ASSETS / "sounds" / f"{rel}.ogg"
            if not target.exists():
                fail(f"sound event {event} references missing file {rel}.ogg")


def check_lang() -> None:
    lang = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    sources = "\n".join(path.read_text(encoding="utf-8")
                        for path in (ROOT / "src/main/java").rglob("*.java"))
    for key in set(re.findall(r'Text\.translatable\(\s*"([a-z0-9_.]+)"', sources)):
        if key.endswith("."):
            continue  # dynamic suffix concatenated at runtime
        if key not in lang:
            fail(f"translation key used in code but missing from en_us.json: {key}")
    names = registered_names()
    block_names: set[str] = set()
    for source_path in (ROOT / "src/main/java").rglob("*.java"):
        block_names |= set(re.findall(
            r'(?:registerBlock|slab|fence|material)\(\s*"([a-z0-9_]+)"',
            source_path.read_text(encoding="utf-8")))
    for name in names:
        prefix = "block" if name in block_names else "item"
        key = f"{prefix}.{MOD_ID}.{name}"
        if key not in lang:
            fail(f"missing lang key {key}")


def check_recipes_and_loot() -> None:
    names = registered_names()
    for path in json_files(RESOURCES / "data" / MOD_ID / "recipe"):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        result = recipe.get("result", {}).get("id", "")
        if result.startswith(f"{MOD_ID}:") and result.split(":", 1)[1] not in names:
            fail(f"{path.name}: recipe result {result} is not registered")
        for ingredient in recipe.get("ingredients", []):
            item = ingredient.get("item", "")
            if item.startswith(f"{MOD_ID}:") and item.split(":", 1)[1] not in names:
                fail(f"{path.name}: ingredient {item} is not registered")
        for row in recipe.get("pattern", []):
            pass
        for key, entry in recipe.get("key", {}).items():
            item = entry.get("item", "")
            if item.startswith(f"{MOD_ID}:") and item.split(":", 1)[1] not in names:
                fail(f"{path.name}: key {key} uses unregistered {item}")
    for path in json_files(RESOURCES / "data" / MOD_ID / "loot_table"):
        table = json.loads(path.read_text(encoding="utf-8"))
        for pool in table.get("pools", []):
            for entry in pool.get("entries", []):
                item = entry.get("name", "")
                if item.startswith(f"{MOD_ID}:") and item.split(":", 1)[1] not in names:
                    fail(f"{path.name}: loot entry {item} is not registered")


def check_pngs() -> None:
    for path in RESOURCES.rglob("*.png"):
        data = path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            fail(f"invalid PNG: {path.relative_to(ROOT)}")
        width, height = struct.unpack(">II", data[16:24])
        if not (0 < width <= 2048 and 0 < height <= 2048):
            fail(f"unsafe PNG dimensions in {path.relative_to(ROOT)}: {width}x{height}")
        expected = EXPECTED_DIMENSIONS.get(str(path.relative_to(ROOT)).replace("\\", "/"))
        if expected and (width, height) != expected:
            fail(f"{path.relative_to(ROOT)} is {width}x{height}, expected {expected[0]}x{expected[1]}")


def check_imports() -> None:
    """Every bare capitalised type token in a .java file must resolve to an
    import, a same-file declaration, a same-package class or java.lang.
    Catches the 'cannot find symbol' class of compile failure without a JDK."""
    java_lang = {"String", "Integer", "Math", "Boolean", "Byte", "Character", "Double",
                 "Exception", "RuntimeException", "IllegalArgumentException",
                 "IllegalStateException", "Float", "Long", "Object", "Runnable",
                 "Short", "System", "Thread", "Void", "Number", "Class", "Iterable",
                 "Comparable", "Enum", "Record", "Override", "Deprecated",
                 "FunctionalInterface", "SuppressWarnings", "Error", "Throwable",
                 "StringBuilder", "StringBuffer", "StrictMath", "IndexOutOfBoundsException",
                 "NullPointerException", "UnsupportedOperationException"}
    # Nested types inherited from Minecraft supertypes (Block.Settings,
    # Item.Settings, AbstractBlock.Settings ...) are legal bare in subclasses.
    inherited_nested = {"Settings", "Type", "Builder"}
    files = sorted((ROOT / "src").rglob("*.java"))
    pkg_classes: dict[str, set[str]] = {}
    enum_constants: set[str] = set()
    for path in files:
        text = path.read_text(encoding="utf-8")
        pkg = re.search(r"package\s+([\w.]+);", text).group(1)
        cls = re.search(r"(?:class|record|enum|interface)\s+(\w+)", text)
        pkg_classes.setdefault(pkg, set()).add(cls.group(1) if cls else "")
        enum_constants |= set(re.findall(r"^\s{4,8}([A-Z][A-Z0-9_]*)\s*[,(]", text, re.M))
    for path in files:
        text = path.read_text(encoding="utf-8")
        stripped = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
        stripped = re.sub(r"//.*", " ", stripped)
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', ' "" ', stripped)
        stripped = re.sub(r"'(?:[^'\\]|\\.)*'", " '' ", stripped)
        pkg = re.search(r"package\s+([\w.]+);", text).group(1)
        imports = set(re.findall(r"import\s+(?:static\s+)?[\w.]+\.(\w+);", text))
        declared = set(re.findall(r"(?:class|record|enum|interface)\s+(\w+)", stripped))
        declared |= set(re.findall(r"<\s*([A-Z])\s*(?:extends[^>]*?)?>", stripped))
        declared |= set(re.findall(r"(?:static\s+)?(?:final\s+)?[\w<>,\[\] .?]+\s+([A-Z][A-Z0-9_]*)\s*=", stripped))
        declared |= set(re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\s*[,(]", stripped, re.M))
        for enum_body in re.findall(r"enum\s+\w+\s*\{([^}]*)\}", stripped):
            declared |= {c.strip().split("(")[0].split("=")[0].strip()
                         for c in enum_body.split(",") if c.strip()}
        body = "\n".join(line for line in stripped.split("\n")
                         if not line.strip().startswith(("import ", "package ")))
        for token in sorted(set(re.findall(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)", body))):
            if token in imports or token in declared or token in java_lang:
                continue
            if token in pkg_classes.get(pkg, set()) or len(token) == 1:
                continue
            if token in inherited_nested or token in enum_constants:
                continue
            fail(f"{path.relative_to(ROOT)}: type '{token}' used but never imported or declared")


def check_internal_static_calls() -> None:
    """Static calls between mod classes must match a declared overload's arity.
    Catches renamed or re-argued internal APIs without needing a compiler."""
    files = sorted((ROOT / "src").rglob("*.java"))
    declared: dict[str, dict[str, list[tuple[int, bool]]]] = {}
    for path in files:
        text = re.sub(r"/\*.*?\*/", " ", path.read_text(encoding="utf-8"), flags=re.S)
        text = re.sub(r"//.*", " ", text)
        cls = re.search(r"(?:class|record|enum|interface)\s+(\w+)", text)
        if not cls:
            continue
        methods: dict[str, list[tuple[int, bool]]] = {}
        for match in re.finditer(r"(?:public|private|protected)\s+(?:static\s+)?[\w<>,\[\] .?]+\s+(\w+)\s*\(", text):
            name = match.group(1)
            if name in ("if", "for", "while", "switch", "catch"):
                continue
            start = match.end()
            depth = 1
            i = start
            while i < len(text) and depth:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            params = text[start:i - 1].strip()
            arity = 0 if not params else params.count(",") + 1
            varargs = "..." in params
            methods.setdefault(name, []).append((arity, varargs))
        declared[cls.group(1)] = methods
    for path in files:
        text = re.sub(r"/\*.*?\*/", " ", path.read_text(encoding="utf-8"), flags=re.S)
        text = re.sub(r"//.*", " ", text)
        for match in re.finditer(r"\b([A-Z]\w+)\.(\w+)\s*\(", text):
            cls, name = match.groups()
            if cls not in declared or name not in declared[cls]:
                continue
            start = match.end()
            depth = 1
            i = start
            while i < len(text) and depth:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            args = text[start:i - 1].strip()
            arity = 0 if not args else _split_args(args)
            ok = any(a == arity or (v and arity >= a - 1) for a, v in declared[cls][name])
            if not ok:
                fail(f"{path.relative_to(ROOT)}: {cls}.{name} called with {arity} args; "
                     f"declared {[a for a, _ in declared[cls][name]]}")


def _split_args(args: str) -> int:
    depth = 0
    count = 1
    in_string = False
    in_char = False
    i = 0
    while i < len(args):
        ch = args[i]
        if in_string:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_string = False
        elif in_char:
            if ch == "\\":
                i += 1
            elif ch == "'":
                in_char = False
        elif ch == '"':
            in_string = True
        elif ch == "'":
            in_char = True
        elif ch in "([<":
            depth += 1
        elif ch in ")]>":
            depth -= 1
        elif ch == "," and depth == 0:
            count += 1
        i += 1
    return count


def check_jar(jar: Path) -> None:
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
    for required in ("fabric.mod.json", "assets/manhunt/lang/en_us.json",
                     "assets/manhunt/icon.png", "assets/manhunt/textures/entity/hunter.png"):
        if required not in names:
            fail(f"built jar is missing {required}")
    if not any(name.endswith(".class") and name.endswith("com/manhunt/Manhunt.class")
               for name in names):
        fail("built jar has no Manhunt entrypoint class")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    check_json()
    check_asset_references()
    check_sounds()
    check_lang()
    check_recipes_and_loot()
    check_pngs()
    check_imports()
    check_internal_static_calls()
    if args.jar:
        check_jar(args.jar)
    print("BUGHUNT PASSED: resources, recipes, loot, lang, sounds and PNGs are coherent.")


if __name__ == "__main__":
    main()
