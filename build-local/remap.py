#!/usr/bin/env python3
"""Remaps compiled mod classes from yarn-named to intermediary.

Rewrites the constant pool of every .class file: net/minecraft/* class, method
and field references become their intermediary (class_/method_/field_) forms.
Fabric/brigadier/mixin/gson/slf4j/lwjgl/JDK references stay named (they ship
that way at runtime). Mixin target strings (simple names) are left for the
refmap, which refmap.py generates from the same database.

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


if __name__ == "__main__":
    main()
