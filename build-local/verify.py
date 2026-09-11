#!/usr/bin/env python3
"""Post-build verification: no named MC refs leak into remapped classes."""

import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remap import CLASS_RE, ClassFile, Mapper, is_mc  # noqa: E402


def main():
    classes_dir, jar = sys.argv[1], sys.argv[2]
    mapper = Mapper()
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
    names = zipfile.ZipFile(jar).namelist()
    print(f"[verify] jar entries: {len(names)}")
    for need in ("fabric.mod.json", "spiderman.mixins.json", "spiderman-refmap.json"):
        assert need in names, need
    print("[verify] jar layout OK")


if __name__ == "__main__":
    main()
