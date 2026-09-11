#!/usr/bin/env python3
"""Post-build verification.

1. No named MC refs leak into remapped classes.
2. Jar layout OK (manifest, fabric metadata, mixin config, refmap).
3. Refmap schema matches what the Mixin runtime ReferenceMapper reads, and
   every @Inject(method = "...") target in the mixin sources has an entry.
4. @Shadow members were renamed to intermediary names in the remapped
   mixin classes (runtime matches them by exact name).
"""

import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remap import (CLASS_RE, ClassFile, Mapper, is_mc,  # noqa: E402
                   parse_mixin_shadows)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXIN_SRC_DIR = os.path.join(ROOT, "src/main/java/com/spiderman/mod/mixin")


def check_leftovers(classes_dir, mapper):
    # Named paths whose intermediary differs are the only true leftovers
    # (intermediary legitimately keeps readable names like MinecraftServer).
    leftover = {}
    n = 0
    for root, _ds, files in os.walk(classes_dir):
        for fn in files:
            if not fn.endswith(".class"):
                continue
            n += 1
            with open(os.path.join(root, fn), "rb") as f:
                cf = ClassFile(f.read())
            for i in range(1, len(cf.cp)):
                e = cf.cp[i]
                if e is None or e["tag"] != 1 or "net/minecraft/" not in e["str"]:
                    continue
                paths = [e["str"]] if is_mc(e["str"]) else CLASS_RE.findall(e["str"])
                for path in paths:
                    if is_mc(path) and mapper.classes.get(path, path) != path:
                        leftover.setdefault(
                            os.path.relpath(os.path.join(root, fn), classes_dir), []) \
                            .append(path[:110])
    if leftover:
        print("[verify] LEFTOVER named refs:")
        for f, ss in sorted(leftover.items()):
            print(" ", f)
            for s in sorted(set(ss))[:12]:
                print("   ", s)
        raise SystemExit("[verify] FAILED")
    print(f"[verify] {n} classes clean: no named MC refs")


def mixin_inject_targets():
    """{mixin classRef: [annotation reference strings]} from sources."""
    out = {}
    for fn in sorted(os.listdir(MIXIN_SRC_DIR)):
        if not fn.endswith(".java"):
            continue
        src = open(os.path.join(MIXIN_SRC_DIR, fn), encoding="utf-8").read()
        if "@Mixin" not in src:
            continue
        pkg = re.search(r"^package ([\w.]+);", src, re.M).group(1)
        cls = re.search(r"\bclass (\w+)", src).group(1)
        refs = re.findall(r"@Inject\(method\s*=\s*\"([^\"]+)\"", src)
        if refs:
            out[pkg.replace(".", "/") + "/" + cls] = refs
    return out


def check_refmap(jar):
    raw = zipfile.ZipFile(jar).read("spiderman-refmap.json").decode("utf-8")
    refmap = json.loads(raw)
    mappings = refmap.get("mappings")
    if not isinstance(mappings, dict):
        raise SystemExit("[verify] refmap: top-level 'mappings' object missing")
    data = refmap.get("data", {})
    for ctx, ctxmap in data.items():
        if ctxmap != mappings:
            raise SystemExit(f"[verify] refmap: data[{ctx!r}] differs from mappings")
    want = mixin_inject_targets()
    n = 0
    for mixin, refs in sorted(want.items()):
        per_mixin = mappings.get(mixin)
        if not isinstance(per_mixin, dict):
            raise SystemExit(f"[verify] refmap: no section for {mixin}")
        for ref in refs:
            if ref not in per_mixin:
                raise SystemExit(f"[verify] refmap: {mixin} missing {ref!r}")
            val = per_mixin[ref]
            # MemberInfo form: Lowner;name(desc)ret for methods, Lowner;name:desc for fields.
            m_ok = re.fullmatch(r"L[^;]+;[^(:]+\(.*\).+", val)
            f_ok = re.fullmatch(r"L[^;]+;[^:]+:.+", val)
            if not (m_ok or f_ok):
                raise SystemExit(f"[verify] refmap: bad value for {mixin} {ref!r}: {val!r}")
            if "/class_" not in val.split(";")[0]:
                raise SystemExit(f"[verify] refmap: non-intermediary owner in {val!r}")
            n += 1
    print(f"[verify] refmap OK: {n} injector targets across {len(want)} mixins")


def check_shadows(classes_dir, mapper):
    shadows = parse_mixin_shadows()
    if not shadows:
        print("[verify] no @Shadow members to check")
        return
    for mixin_owner, name, _desc, _target in shadows:
        path = os.path.join(classes_dir, mixin_owner + ".class")
        with open(path, "rb") as f:
            cf = ClassFile(f.read())
        inter = mapper.shadow[(mixin_owner, name, _desc)]
        utf8s = {cf.utf(i) for i in range(1, len(cf.cp))
                 if cf.cp[i] is not None and cf.cp[i]["tag"] == 1}
        if name in utf8s:
            raise SystemExit(f"[verify] {mixin_owner}: yarn shadow {name!r} survived remap")
        if inter not in utf8s:
            raise SystemExit(f"[verify] {mixin_owner}: intermediary {inter!r} missing")
    print(f"[verify] shadows OK: {len(shadows)} member(s) renamed to intermediary")


def main():
    classes_dir, jar = sys.argv[1], sys.argv[2]
    mapper = Mapper()
    check_leftovers(classes_dir, mapper)
    names = zipfile.ZipFile(jar).namelist()
    print(f"[verify] jar entries: {len(names)}")
    for need in ("META-INF/MANIFEST.MF", "fabric.mod.json",
                 "spiderman.mixins.json", "spiderman-refmap.json"):
        assert need in names, need
    print("[verify] jar layout OK")
    check_refmap(jar)
    check_shadows(classes_dir, mapper)


if __name__ == "__main__":
    main()
