#!/usr/bin/env python3
"""Remaps compiled mod classes from yarn-named to intermediary.

Rewrites the constant pool of every .class file: net/minecraft/* class, method
and field references become their intermediary (class_/method_/field_) forms.
Fabric/brigadier/mixin/gson/slf4j/lwjgl/JDK references stay named (they ship
that way at runtime). Mixin target strings (simple names) are left for the
refmap, which refmap.py generates from the same database.

@Shadow members are renamed to their intermediary target names. This is the
offline equivalent of loom's flow, where the Mixin annotation processor
resolves shadows against the target class and emits out-mappings that the
jar remapper consumes (shadow declarations must match the obfuscated
target members, since the runtime matches them by exact name).

Usage: python3 build-local/remap.py <in-dir> <out-dir>
"""

import os
import re
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from yarn_db import load  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# (owner, name, desc) -> intermediary name, for references whose erased
# descriptor does not match tiny exactly. Populated as discrepancies surface.
MANUAL = {
    # ("net/minecraft/registry/entry/RegistryEntry", "value", "()Ljava/lang/Object;"): "",
}

CLASS_RE = re.compile(r"L([\w/$]+);")


# Hierarchy links the stubs omit (lookup-only; no stub impact).
EXTRA_SUPERS = {
    "net/minecraft/entity/Entity": ["net/minecraft/world/entity/EntityLike"],
    "net/minecraft/registry/entry/RegistryEntry$Reference": [
        "net/minecraft/registry/entry/RegistryEntry"],
    "net/minecraft/client/gui/screen/Screen": ["net/minecraft/client/gui/Element"],
}

MIXIN_SRC_DIR = os.path.join(ROOT, "src/main/java/com/spiderman/mod/mixin")

_PRIMITIVE_DESC = {
    "boolean": "Z", "byte": "B", "char": "C", "short": "S",
    "int": "I", "long": "J", "float": "F", "double": "D", "void": "V",
}


def _resolve_type(name, imports, pkg, where):
    """Resolves a source-level type name to a field descriptor."""
    name = name.strip()
    dims = 0
    while name.endswith("[]"):
        dims += 1
        name = name[:-2].strip()
    name = re.sub(r"<.*>", "", name).strip()  # drop generics
    if name in _PRIMITIVE_DESC:
        base = _PRIMITIVE_DESC[name]
    else:
        if "." in name:
            full = name
        elif name in imports:
            full = imports[name]
        else:
            raise SystemExit(f"[remap] cannot resolve type {name!r} ({where})")
        base = "L" + full.replace(".", "/") + ";"
    return "[" * dims + base


def parse_mixin_shadows():
    """Derives @Shadow field targets from the mixin sources.

    Returns [(mixin_owner, field_name, field_desc_named, target_owner_yarn)].
    @Shadow methods are rejected loudly: none exist today, and silently
    skipping them would produce a jar that crashes at runtime.
    """
    found = []
    if not os.path.isdir(MIXIN_SRC_DIR):
        return found
    for fn in sorted(os.listdir(MIXIN_SRC_DIR)):
        if not fn.endswith(".java"):
            continue
        src = open(os.path.join(MIXIN_SRC_DIR, fn), encoding="utf-8").read()
        m = re.search(r"@Mixin\(\s*([\w.$]+)\.class", src)
        if not m:
            continue  # helper class, not a mixin
        if "targets" in src.split("@Mixin", 1)[1].split(")", 1)[0]:
            raise SystemExit(f"[remap] string @Mixin targets unsupported ({fn})")
        pkg = re.search(r"^package ([\w.]+);", src, re.M).group(1)
        imports = {}
        for im in re.finditer(r"^import (?!static)([\w.]+);", src, re.M):
            imports[im.group(1).split(".")[-1]] = im.group(1)
        target = m.group(1)
        if "." not in target:
            if target not in imports:
                raise SystemExit(f"[remap] cannot resolve @Mixin target {target!r} ({fn})")
            target = imports[target]
        target_owner = target.replace(".", "/")
        cls = re.search(r"\bclass (\w+)", src).group(1)
        mixin_owner = pkg.replace(".", "/") + "/" + cls
        for sm in re.finditer(r"@Shadow\b", src):
            tail = src[sm.end():]
            semi = tail.find(";")
            if semi < 0:
                raise SystemExit(f"[remap] malformed @Shadow ({fn})")
            decl = tail[:semi]
            if "(" in decl:
                raise SystemExit(f"[remap] @Shadow methods unsupported ({fn}): "
                                 f"add handling before building")
            decl = re.sub(r"@\w+(?:\([^)]*\))?", "", decl)
            decl = re.sub(r"\b(public|protected|private|static|final|transient|volatile)\b",
                           "", decl).strip()
            dm = re.match(r"([\w.$<>\[\], ?]+?)\s+(\w+)$", decl)
            if not dm:
                raise SystemExit(f"[remap] cannot parse @Shadow decl ({fn}): {decl!r}")
            type_name, field_name = dm.group(1), dm.group(2)
            desc = _resolve_type(type_name, imports, pkg, f"{fn} {field_name}")
            found.append((mixin_owner, field_name, desc, target_owner))
    return found


def member_decl_positions(tail):
    """Yields (kind, name_index_pos, name_cp_index, desc_cp_index) for every
    field/method declaration. Positions are offsets into the given tail bytes
    (the class file region after the constant pool). Sizes never change, so
    indices can be patched in place with struct.pack_into."""
    pos = 2 + 2 + 2  # access_flags, this_class, super_class
    n_ifaces = struct.unpack_from(">H", tail, pos)[0]
    pos += 2 + 2 * n_ifaces
    for kind in ("field", "method"):
        n = struct.unpack_from(">H", tail, pos)[0]
        pos += 2
        for _ in range(n):
            pos += 2  # access_flags
            name_i = struct.unpack_from(">H", tail, pos)[0]
            name_pos = pos
            pos += 2
            desc_i = struct.unpack_from(">H", tail, pos)[0]
            pos += 2
            n_attr = struct.unpack_from(">H", tail, pos)[0]
            pos += 2
            for _ in range(n_attr):
                alen = struct.unpack_from(">I", tail, pos + 2)[0]
                pos += 6 + alen
            yield kind, name_pos, name_i, desc_i


def is_mc(path):
    return path.startswith("net/minecraft/") and "/class_" not in path


class Hierarchy:
    """Superclass + interface map (slash names) from stubs."""

    def __init__(self):
        self.supers = {}

    def add(self, cls, supers):
        if cls not in self.supers:
            self.supers[cls] = []
        for s in supers:
            if s not in self.supers[cls]:
                self.supers[cls].append(s)

    def load_surface(self, path):
        cur = None
        for raw in open(path, encoding="utf-8"):
            line = raw.split("#", 1)[0].rstrip()
            if not line.strip():
                continue
            if line[0] not in (" ", "\t"):
                toks = line.split()
                if toks[0] not in ("CLASS", "INTERFACE", "HOLDER"):
                    continue
                cur = toks[1]
                rest = " ".join(toks[2:])
                m = re.search(r"EXTENDS (\S+)", rest)
                if m:
                    self.add(cur, [m.group(1)])
                m = re.search(r"IMPLEMENTS (.+)$", rest)
                if m:
                    self.add(cur, [x.strip() for x in m.group(1).split(",")])

    def load_handstubs(self, path):
        src = open(path, encoding="utf-8").read()
        for m in re.finditer(
                r"package ([\w.]+);[\s\S]*?public (?:abstract |final )?(?:class|interface|@interface|record|enum) (\w+)"
                r"(?: extends ([\w., <>?]+?))?(?: implements ([\w., <>?]+?))? ?\{",
                src):
            pkg, cls, ext, impl = m.group(1), m.group(2), m.group(3), m.group(4)
            full = (pkg + "." + cls).replace(".", "/")
            supers = []
            for part in (ext, impl):
                if not part:
                    continue
                for name in part.split(","):
                    name = name.strip().split("<")[0].strip()
                    if "." not in name:
                        name = pkg + "." + name
                    supers.append(name.replace(".", "/"))
            if supers:
                self.add(full, supers)


class Mapper:
    def __init__(self):
        self.db = load()
        self.classes = self.db["classes"]
        self.h = Hierarchy()
        self._warned = set()
        self.h.load_surface(os.path.join(ROOT, "build-local/api-surface.txt"))
        self.h.load_handstubs(os.path.join(ROOT, "build-local/handstubs.py"))
        for cls, supers in EXTRA_SUPERS.items():
            self.h.add(cls, supers)
        # (mixin_owner, name, desc_named) -> intermediary name
        self.shadow = {}
        self.shadow_applied = set()
        for mixin_owner, name, desc, target_owner in parse_mixin_shadows():
            cands = [m for m in self.db["fields"].get(target_owner, [])
                     if m["n"] == name and m.get("dn", m["d"]) == desc]
            if len(cands) != 1:
                raise SystemExit(
                    f"[remap] {len(cands)} shadow candidates for "
                    f"{target_owner} {name} {desc}")
            self.shadow[(mixin_owner, name, desc)] = cands[0]["i"]

    def map_class(self, path):
        if not is_mc(path):
            return path
        if path.startswith("["):
            return "[" + self.map_class(path[1:])
        try:
            return self.classes[path]
        except KeyError:
            raise SystemExit(f"[remap] MISSING class mapping: {path}")

    def map_desc(self, desc):
        def sub(m):
            return "L" + self.map_class(m.group(1)) + ";"
        return CLASS_RE.sub(sub, desc)

    def lookup(self, owner, name, desc, is_field):
        """Returns (inter_name, inter_desc); the tiny desc is authoritative."""
        key = (owner, name, desc)
        if key in MANUAL:
            return MANUAL[key]
        bucket = self.db["fields"] if is_field else self.db["methods"]
        seen = set()
        queue = [owner]
        name_only = []
        while queue:
            o = queue.pop(0)
            if o in seen:
                continue
            seen.add(o)
            for m in bucket.get(o, []):
                if m["n"] != name:
                    continue
                if m.get("dn", m["d"]) == desc:
                    return m["i"], m["d"]
                name_only.append((o, m))
            queue.extend(self.h.supers.get(o, []))
        if is_field and len(name_only) == 1:
            _o, m = name_only[0]
            return m["i"], m["d"]
        if not is_field and (owner, name, desc) not in self._warned:
            # Presumed non-MC declaration (netty ByteBuf, JDK Object, ...):
            # keep the name, translate the descriptor.
            self._warned.add((owner, name, desc))
            print(f"[remap] WARN keep-name (non-MC decl?): {owner} {name} {desc}")
            return name, self.map_desc(desc)
        if not is_field:
            return name, self.map_desc(desc)
        kind = "field" if is_field else "method"
        raise SystemExit(f"[remap] MISSING {kind}: {owner} {name} {desc}")


# ---------------- classfile ----------------

class ClassFile:
    def __init__(self, data):
        self.data = data
        self.pos = 8  # skip magic + version
        self.cp = [None]  # 1-based; entries are dicts
        count = self.u2()
        idx = 1
        while idx < count:
            tag = self.u1()
            e = {"tag": tag}
            if tag == 1:  # Utf8
                n = self.u2()
                e["raw"] = self.data[self.pos:self.pos + n]
                e["str"] = e["raw"].decode("utf-8", errors="surrogateescape")
                self.pos += n
            elif tag in (3, 4):  # int/float
                e["raw"] = self.data[self.pos:self.pos + 4]
                self.pos += 4
            elif tag in (5, 6):  # long/double (two slots)
                e["raw"] = self.data[self.pos:self.pos + 8]
                self.pos += 8
                self.cp.append(e)
                self.cp.append(None)
                idx += 2
                continue
            elif tag in (7, 8, 16, 19, 20):  # single u2 ref
                e["ref"] = self.u2()
            elif tag in (9, 10, 11, 12, 17, 18):  # two u2 refs
                e["ref"] = self.u2()
                e["ref2"] = self.u2()
            elif tag == 15:  # MethodHandle
                e["kind"] = self.u1()
                e["ref"] = self.u2()
            else:
                raise SystemExit(f"[remap] bad constant tag {tag}")
            self.cp.append(e)
            idx += 1
        self.tail = self.data[self.pos:]

    def u1(self):
        v = self.data[self.pos]
        self.pos += 1
        return v

    def u2(self):
        v = struct.unpack(">H", self.data[self.pos:self.pos + 2])[0]
        self.pos += 2
        return v

    def utf(self, i):
        return self.cp[i]["str"]

    def set_utf(self, i, s):
        raw = s.encode("utf-8", errors="surrogateescape")
        self.cp[i]["raw"] = raw
        self.cp[i]["str"] = s

    def add_utf(self, s):
        raw = s.encode("utf-8", errors="surrogateescape")
        self.cp.append({"tag": 1, "raw": raw, "str": s})
        return len(self.cp) - 1

    def add_nat(self, name_i, desc_i):
        self.cp.append({"tag": 12, "ref": name_i, "ref2": desc_i})
        return len(self.cp) - 1

    def find_utf(self, s):
        for i in range(1, len(self.cp)):
            e = self.cp[i]
            if e is not None and e["tag"] == 1 and e["str"] == s:
                return i
        return None

    def serialize(self):
        out = bytearray(self.data[:8])
        out += struct.pack(">H", len(self.cp))
        for e in self.cp[1:]:
            if e is None:
                continue
            tag = e["tag"]
            out.append(tag)
            if tag == 1:
                out += struct.pack(">H", len(e["raw"])) + e["raw"]
            elif tag in (3, 4, 5, 6):
                out += e["raw"]
            elif tag in (7, 8, 16, 19, 20):
                out += struct.pack(">H", e["ref"])
            elif tag in (9, 10, 11, 12, 17, 18):
                out += struct.pack(">HH", e["ref"], e["ref2"])
            elif tag == 15:
                out.append(e["kind"])
                out += struct.pack(">H", e["ref"])
        out += self.tail
        return bytes(out)


def set_nat_str(cf, nat, which, s):
    """Write a NameAndType name/desc, duplicating the Utf8 if shared."""
    idx = nat["ref"] if which == "name" else nat["ref2"]
    if cf.utf(idx) == s:
        return
    for j in range(1, len(cf.cp)):
        e = cf.cp[j]
        if e is not None and e["tag"] == 12 and e is not nat:
            if e["ref"] == idx or e["ref2"] == idx:
                ni = cf.find_utf(s) or cf.add_utf(s)
                if which == "name":
                    nat["ref"] = ni
                else:
                    nat["ref2"] = ni
                return
    cf.set_utf(idx, s)


def remap_class(data, mapper, rel):
    cf = ClassFile(data)
    # Original class paths (memberref owners must resolve pre-remap names).
    orig_class = {}
    for i in range(1, len(cf.cp)):
        e = cf.cp[i]
        if e is not None and e["tag"] == 7:
            orig_class[i] = cf.utf(e["ref"])
    # 1) Class entries -> intermediary paths.
    for i, utf in orig_class.items():
        if is_mc(utf) or (utf.startswith("[") and "net/minecraft/" in utf):
            cf.set_utf(cf.cp[i]["ref"], mapper.map_class(utf))
    # 2) Memberrefs -> intermediary member names (+ translated descs).
    # Group refs by NameAndType to handle javac's constant sharing safely.
    refs_by_nat = {}
    for i in range(1, len(cf.cp)):
        e = cf.cp[i]
        if e is not None and e["tag"] in (9, 10, 11):
            owner = orig_class[e["ref"]]
            refs_by_nat.setdefault(e["ref2"], []).append((i, e["tag"], owner))
    for nat_i, refs in refs_by_nat.items():
        nat = cf.cp[nat_i]
        name, desc = cf.utf(nat["ref"]), cf.utf(nat["ref2"])
        mc = [(i, tag, o) for (i, tag, o) in refs if is_mc(o)]
        others = [(i, tag, o) for (i, tag, o) in refs if not is_mc(o)]
        if not mc:
            continue
        # Distinct mappings needed among MC refs.
        mappings = []
        for (i, tag, o) in mc:
            is_field = tag == 9
            if name in ("<init>", "<clinit>"):
                iname, idesc = name, mapper.map_desc(desc)
            else:
                iname, idesc = mapper.lookup(o, name, desc, is_field)
            mappings.append((i, iname, idesc))
        distinct = []
        for m in mappings:
            if m[1:] not in [d[1:] for d in distinct]:
                distinct.append(m)
        need_dup = len(distinct) > 1 or bool(others)
        if not need_dup:
            i, iname, idesc = distinct[0]
            set_nat_str(cf, nat, "name", iname)
            set_nat_str(cf, nat, "desc", idesc)
            continue
        # First mapping keeps the NAT; every other ref gets a duplicate.
        first = True
        for (i, iname, idesc) in mappings:
            if first:
                set_nat_str(cf, nat, "name", iname)
                set_nat_str(cf, nat, "desc", idesc)
                first = False
                continue
            ni = cf.find_utf(iname) or cf.add_utf(iname)
            di = cf.find_utf(idesc) or cf.add_utf(idesc)
            cf.cp[i]["ref2"] = cf.add_nat(ni, di)
        if others:
            # Non-MC refs sharing this NAT keep the ORIGINAL name/desc.
            ni = cf.find_utf(name) or cf.add_utf(name)
            di = cf.find_utf(desc) or cf.add_utf(desc)
            dup = cf.add_nat(ni, di)
            for (i, tag, o) in others:
                cf.cp[i]["ref2"] = dup
    # 2b/2c) @Shadow members declared by this mixin class -> intermediary
    # target names (loom equivalent: AP out-mappings). Runs before step 3
    # so descriptors still match their named forms.
    this_owner = rel[:-6].replace(os.sep, "/") if rel.endswith(".class") else None
    by_name = {(name, desc): inter for (o, name, desc), inter in mapper.shadow.items()
               if o == this_owner}
    if by_name:
        # Rebuilt from current pool state: step 2 may have moved refs to
        # duplicate NATs, which the pre-step-2 grouping cannot see.
        refs_by_nat2 = {}
        for i in range(1, len(cf.cp)):
            e = cf.cp[i]
            if e is not None and e["tag"] in (9, 10, 11):
                refs_by_nat2.setdefault(e["ref2"], []).append(
                    (i, e["tag"], orig_class[e["ref"]]))
        for nat_i, refs in refs_by_nat2.items():
            nat = cf.cp[nat_i]
            name, desc = cf.utf(nat["ref"]), cf.utf(nat["ref2"])
            inter = by_name.get((name, desc))
            if inter is None:
                continue
            mine = [(i, t, o) for (i, t, o) in refs if o == this_owner]
            rest = [(i, t, o) for (i, t, o) in refs if o != this_owner]
            if not mine:
                continue
            set_nat_str(cf, nat, "name", inter)
            mapper.shadow_applied.add((this_owner, name, desc))
            if rest:
                ni = cf.find_utf(name) or cf.add_utf(name)
                di = cf.find_utf(desc) or cf.add_utf(desc)
                dup = cf.add_nat(ni, di)
                for (i, _t, _o) in rest:
                    cf.cp[i]["ref2"] = dup
        tail = bytearray(cf.tail)
        for _kind, name_pos, name_i, desc_i in member_decl_positions(bytes(tail)):
            dname, ddesc = cf.utf(name_i), cf.utf(desc_i)
            inter = by_name.get((dname, ddesc))
            if inter is None:
                continue
            # Duplicate-on-write: the yarn Utf8 may be shared with debug info.
            ni = cf.find_utf(inter) or cf.add_utf(inter)
            struct.pack_into(">H", tail, name_pos, ni)
            mapper.shadow_applied.add((this_owner, dname, ddesc))
        cf.tail = bytes(tail)
    # 3) Every other Utf8 containing a named MC path is a descriptor,
    # signature or similar -> translate class refs inside it.
    for i in range(1, len(cf.cp)):
        e = cf.cp[i]
        if e is not None and e["tag"] == 1 and "net/minecraft/" in e["str"]:
            s = e["str"]
            if is_mc(s):
                cf.set_utf(i, mapper.map_class(s))
            else:
                new = mapper.map_desc(s)
                # Guard: bare strings (shouldn't exist) would corrupt; only
                # rewrite when it looks like a descriptor/signature.
                if new != s and ("(" in s or "L" in s or ";" in s):
                    cf.set_utf(i, new)
    return cf.serialize()


def main():
    src_dir, dst_dir = sys.argv[1], sys.argv[2]
    mapper = Mapper()
    count = 0
    for root, _ds, files in os.walk(src_dir):
        for fn in files:
            if not fn.endswith(".class"):
                continue
            src = os.path.join(root, fn)
            rel = os.path.relpath(src, src_dir)
            with open(src, "rb") as f:
                data = remap_class(f.read(), mapper, rel)
            dst = os.path.join(dst_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as f:
                f.write(data)
            count += 1
    print(f"[remap] remapped {count} classes -> {dst_dir}")
    missing = set(mapper.shadow) - mapper.shadow_applied
    if missing:
        raise SystemExit(f"[remap] shadow members never matched: {sorted(missing)}")
    if mapper.shadow:
        print(f"[remap] renamed {len(mapper.shadow_applied)} shadow member(s) "
              f"in mixin classes")


if __name__ == "__main__":
    main()
