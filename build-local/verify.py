#!/usr/bin/env python3
"""Post-build verification.

1. Exact linkage: every memberref resolves (mod-internal byte-exact, MC via
   tiny + hierarchy, JDK-allowlisted keeps only) and every override def is
   renamed while no unique def is missed. Fails the build loudly otherwise.
2. No named MC refs leak into remapped classes.
3. Jar layout OK (manifest, fabric metadata, mixin config, refmap).
4. Refmap schema matches what the Mixin runtime ReferenceMapper reads, and
   every @Inject(method = "...") target in the mixin sources has an entry.
5. @Shadow members were renamed to intermediary names in the remapped
   mixin classes (runtime matches them by exact name).
"""

import json
import os
import re
import struct
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remap import (CLASS_RE, JDK_KEEP, NETTY_KEEP, ClassFile, Mapper,  # noqa: E402
                   is_mc, is_mod, member_decl_positions,
                   parse_mixin_shadows, walk_supers)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXIN_SRC_DIR = os.path.join(ROOT, "src/main/java/com/spiderman/mod/mixin")


def check_linkage(classes_dir, mapper):
    """Fails closed unless every ref and override def resolves exactly."""
    inv = {v: k for k, v in mapper.classes.items()}

    def rev(path):
        return inv.get(path, path)

    def rev_desc(d):
        return CLASS_RE.sub(lambda m: "L" + rev(m.group(1)) + ";", d)

    def mc_supers_of(owner):
        seen = set()

        def supers_fn(c):
            return mapper.h.supers.get(c, [])

        for s in supers.get(owner, []):
            for o in walk_supers(s, supers_fn):
                seen.add(o)
        return seen

    def tiny_inter(chain, name, desc, is_field):
        bucket = mapper.db["fields"] if is_field else mapper.db["methods"]
        return any(m["i"] == name and m["d"] == desc
                   for o in chain for m in bucket.get(o, []))

    def tiny_named(chain, name, desc, is_field):
        bucket = mapper.db["fields"] if is_field else mapper.db["methods"]
        return any(m["n"] == name and m.get("dn", m["d"]) == desc
                   for o in chain for m in bucket.get(o, []))

    # Jar declaration map (post-remap forms) + supers for hierarchy walks.
    decls, supers, refs_of = {}, {}, {}
    for root, _ds, files in os.walk(classes_dir):
        for fn in sorted(files):
            if not fn.endswith(".class"):
                continue
            rel = os.path.relpath(os.path.join(root, fn), classes_dir)
            with open(os.path.join(root, fn), "rb") as f:
                cf = ClassFile(f.read())
            cls_utf = {}
            for i in range(1, len(cf.cp)):
                e = cf.cp[i]
                if e is not None and e["tag"] == 7:
                    cls_utf[i] = cf.utf(e["ref"])
            refs = []
            for i in range(1, len(cf.cp)):
                e = cf.cp[i]
                if e is not None and e["tag"] in (9, 10, 11):
                    nat = cf.cp[e["ref2"]]
                    refs.append((e["tag"], cls_utf[e["ref"]],
                                 cf.utf(nat["ref"]), cf.utf(nat["ref2"])))
            this_i, sup_i = struct.unpack_from(">HH", cf.tail, 2)
            this = cls_utf[this_i]
            sup = cls_utf.get(sup_i) if sup_i else None
            n_ifaces = struct.unpack_from(">H", cf.tail, 6)[0]
            ifaces = [cls_utf[struct.unpack_from(">H", cf.tail, 8 + 2 * k)[0]]
                      for k in range(n_ifaces)]
            supers[this] = [rev(s) for s in (([sup] if sup else []) + ifaces)]
            fields, methods = {}, {}
            for kind, _pos, flags, ni, di in member_decl_positions(cf.tail):
                box = fields if kind == "field" else methods
                box[(cf.utf(ni), cf.utf(di))] = flags
            decls[this] = (fields, methods)
            refs_of[this] = (rel, refs)

    failures = []
    n_refs = n_defs = n_keep = 0
    shadow_owners = {o for (o, _n, _d) in mapper.shadow}

    def keepable(n, d):
        return (n, rev_desc(d)) in JDK_KEEP or (n, rev_desc(d)) in NETTY_KEEP

    for this in sorted(decls):
        rel, refs = refs_of[this]
        for (tag, o, n, d) in refs:
            n_refs += 1
            is_field = tag == 9
            box = 0 if is_field else 1
            if is_mod(o):
                found = False
                for c in walk_supers(
                        o, lambda c: [s for s in supers.get(c, []) if is_mod(s)]):
                    dd = decls.get(c)
                    if dd is not None and (n, d) in dd[box]:
                        found = True
                        break
                if found:
                    continue
                if keepable(n, d):
                    n_keep += 1
                    continue
                # Intermediary names take many shapes (method_*, comp_349,
                # getBuffer, ...): membership in tiny decides, not prefixes.
                if tiny_inter(mc_supers_of(o), n, d, is_field):
                    continue
                failures.append(f"{rel}: mod-owner ref has no target: {o} {n} {d}")
            elif o.startswith("net/minecraft/"):
                chain = list(walk_supers(
                    rev(o), lambda c: mapper.h.supers.get(c, [])))
                if n in ("<init>", "<clinit>"):
                    continue
                if tiny_inter(chain, n, d, is_field):
                    continue
                if keepable(n, d):
                    n_keep += 1
                    continue
                failures.append(f"{rel}: unmapped mc-owner ref: {o} {n} {d}")
            else:
                if n.startswith("method_") or n.startswith("field_"):
                    failures.append(
                        f"{rel}: non-MC owner with intermediary name: {o} {n} {d}")
        chain = None
        fields, methods = decls[this]
        for (n, d), flags in methods.items():
            n_defs += 1
            if n in ("<init>", "<clinit>") or (flags & 0x0008) \
                    or not (flags & 0x0005):
                continue
            if chain is None:
                chain = mc_supers_of(this)
            if tiny_inter(chain, n, d, False):
                continue  # correctly named (any intermediary shape)
            if tiny_named(chain, n, rev_desc(d), False):
                failures.append(f"{rel}: MISSED OVERRIDE (kept yarn def): {n} {d}")
        for (n, d), _flags in fields.items():
            n_defs += 1
            if n.startswith("field_") and this not in shadow_owners:
                failures.append(f"{rel}: non-shadow intermediary field def: {n} {d}")
    # @Shadow field defs must name the intermediary target member exactly.
    for (o, _n, _d), inter in mapper.shadow.items():
        dd = decls.get(o)
        if dd is None or not any(n == inter for (n, _d) in dd[0]):
            failures.append(f"{o}: shadow def {inter!r} missing from remapped class")
    if failures:
        print("[verify] LINKAGE FAILURES:")
        for f in sorted(failures):
            print("  ", f)
        raise SystemExit(f"[verify] FAILED: {len(failures)} linkage error(s)")
    print(f"[verify] linkage OK: {n_refs} refs resolved, {n_defs} defs checked "
          f"({n_keep} allowlisted JDK/netty keeps)")


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
    check_linkage(classes_dir, mapper)
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
