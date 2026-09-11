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
    # Renderer chain (handstub declares no extends): override lookups from
    # mod renderers must reach EntityRenderer.getTexture/render.
    "net/minecraft/client/render/entity/MobEntityRenderer": [
        "net/minecraft/client/render/entity/LivingEntityRenderer"],
    "net/minecraft/client/render/entity/LivingEntityRenderer": [
        "net/minecraft/client/render/entity/EntityRenderer"],
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
    """Yields (kind, name_index_pos, access_flags, name_cp_index, desc_cp_index)
    for every field/method declaration. Positions are offsets into the given
    tail bytes (the class file region after the constant pool). Sizes never
    change, so indices can be patched in place with struct.pack_into."""
    pos = 2 + 2 + 2  # access_flags, this_class, super_class
    n_ifaces = struct.unpack_from(">H", tail, pos)[0]
    pos += 2 + 2 * n_ifaces
    for kind in ("field", "method"):
        n = struct.unpack_from(">H", tail, pos)[0]
        pos += 2
        for _ in range(n):
            flags = struct.unpack_from(">H", tail, pos)[0]
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
            yield kind, name_pos, flags, name_i, desc_i


def is_mc(path):
    return path.startswith("net/minecraft/") and "/class_" not in path


MOD_PREFIX = "com/spiderman/mod/"


def is_mod(path):
    return path.startswith(MOD_PREFIX)


ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_PROTECTED = 0x0004
ACC_STATIC = 0x0008


def _inheritable(flags):
    """True for members a subclass could override (public/protected, instance)."""
    return bool(flags & (ACC_PUBLIC | ACC_PROTECTED)) and not (flags & ACC_STATIC)


# (name, named-desc) pairs a memberref may keep without intermediary mapping.
# java/* is never remapped, so refs to JDK-declared members resolve as-is;
# ThreadExecutor's runtime execute() override keeps this exact JDK name.
# Fail-closed: any other unresolvable/unmapped ref is a build error.
JDK_KEEP = {
    ("toString", "()Ljava/lang/String;"),
    ("hashCode", "()I"),
    ("equals", "(Ljava/lang/Object;)Z"),
    ("clone", "()Ljava/lang/Object;"),
    ("finalize", "()V"),
    ("getClass", "()Ljava/lang/Class;"),
    ("wait", "()V"),
    ("wait", "(J)V"),
    ("wait", "(JI)V"),
    ("notify", "()V"),
    ("notifyAll", "()V"),
    ("execute", "(Ljava/lang/Runnable;)V"),
}


# (name, named-desc) pairs declared by io.netty.buffer.ByteBuf (never
# remapped): PacketByteBuf inherits them, so MC-owner refs to them keep
# their names. Pinned exactly; extend deliberately if mod code uses more.
NETTY_KEEP = {
    ("readBoolean", "()Z"),
    ("readDouble", "()D"),
}


def walk_supers(start, supers_fn):
    """Yields classes nearest-first over supers_fn(class) -> [supers]."""
    seen = set()
    queue = [start]
    while queue:
        o = queue.pop(0)
        if o in seen:
            continue
        seen.add(o)
        yield o
        queue.extend(supers_fn(o))


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
    def __init__(self, declmap=None):
        self.db = load()
        self.classes = self.db["classes"]
        # {mod owner: {"super": owner|None, "ifaces": [...],
        #   "fields": {(n,d): flags}, "methods": {(n,d): flags}}} (named forms)
        self.declmap = declmap if declmap is not None else {}
        self._declmemo = {}
        self.def_renamed = 0
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
        # Class-entry content: a/b/C, [La/b/C; (any depth), L...; form,
        # primitives or JDK/fabric paths (returned unchanged).
        prefix = ""
        while path.startswith("["):
            prefix += "["
            path = path[1:]
        if path.startswith("L") and path.endswith(";"):
            inner = path[1:-1]
            if not is_mc(inner):
                return prefix + path
            try:
                return prefix + "L" + self.classes[inner] + ";"
            except KeyError:
                raise SystemExit(f"[remap] MISSING class mapping: {inner}")
        if not is_mc(path):
            return prefix + path
        try:
            return prefix + self.classes[path]
        except KeyError:
            raise SystemExit(f"[remap] MISSING class mapping: {path}")

    def map_desc(self, desc):
        def sub(m):
            return "L" + self.map_class(m.group(1)) + ";"
        return CLASS_RE.sub(sub, desc)

    def _scan(self, owner, name, desc, is_field):
        """Walks the MC hierarchy; returns (exact_match_or_None, name_only)."""
        bucket = self.db["fields"] if is_field else self.db["methods"]
        name_only = []
        for o in walk_supers(owner, lambda c: self.h.supers.get(c, [])):
            for m in bucket.get(o, []):
                if m["n"] != name:
                    continue
                if m.get("dn", m["d"]) == desc:
                    return m, name_only
                name_only.append((o, m))
        return None, name_only

    def lookup(self, owner, name, desc, is_field):
        """Returns (inter_name, inter_desc); the tiny desc is authoritative."""
        key = (owner, name, desc)
        if key in MANUAL:
            return MANUAL[key]
        m, name_only = self._scan(owner, name, desc, is_field)
        if m is not None:
            return m["i"], m["d"]
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

    def tiny_has(self, owner, name, desc, is_field):
        """True if lookup() would map (exact, or the single-name field fallback)."""
        m, name_only = self._scan(owner, name, desc, is_field)
        return m is not None or (is_field and len(name_only) == 1)

    def _mod_supers(self, cls):
        d = self.declmap.get(cls)
        if not d:
            return []
        out = [d["super"]] if d["super"] else []
        return out + d["ifaces"]

    def resolve(self, owner, name, desc, is_field, exclude_self=False,
                inheritable_only=False):
        """Nearest declaration of (name, exact-desc) up the hierarchy.

        Walks mod bytecode declarations plus the MC stub hierarchy.
        Returns ("mc", mc_class) | ("mod", mod_class, flags) | None.
        """
        def supers_fn(c):
            if is_mod(c):
                return self._mod_supers(c)
            return self.h.supers.get(c, [])
        fields_bucket = self.db["fields"]
        methods_bucket = self.db["methods"]
        mc_name_only = []
        first = True
        for o in walk_supers(owner, supers_fn):
            if first and exclude_self:
                first = False
                continue
            first = False
            if is_mod(o):
                d = self.declmap.get(o)
                if d is None:
                    continue
                decls = d["fields"] if is_field else d["methods"]
                if (name, desc) in decls:
                    flags = decls[(name, desc)]
                    if inheritable_only and not _inheritable(flags):
                        continue  # private/static mod decl: not an override link
                    return ("mod", o, flags)
                continue
            if not is_mc(o):
                continue  # fabric/JDK/Record/...: unknowable, keep walking
            bucket = fields_bucket if is_field else methods_bucket
            for m in bucket.get(o, []):
                if m["n"] != name:
                    continue
                if m.get("dn", m["d"]) == desc:
                    return ("mc", o)
                mc_name_only.append((o, m))
        if is_field and len(mc_name_only) == 1:
            return ("mc", mc_name_only[0][0])
        return None

    def decl_mapping(self, cls, name, desc, is_field, flags):
        """Mapping for a MOD declaration: MC overrides -> intermediary.

        Fields, statics, constructors, private/package-private methods and
        members with no MC ancestor declaration keep their names (hiding and
        uniqueness are name-exact at runtime). Instance overrides must match
        the ancestor's intermediary name or dispatch misses them.
        """
        memo_key = (cls, name, desc, is_field, flags)
        if memo_key in self._declmemo:
            return self._declmemo[memo_key]
        if (name in ("<init>", "<clinit>") or is_field or (flags & ACC_STATIC)
                or not (flags & (ACC_PUBLIC | ACC_PROTECTED))):
            out = (name, self.map_desc(desc))
        else:
            r = self.resolve(cls, name, desc, False, exclude_self=True,
                             inheritable_only=True)
            if r is None:
                out = (name, self.map_desc(desc))  # mod-unique member
            elif r[0] == "mc":
                out = self.lookup(r[1], name, desc, False)
            else:
                out = self.decl_mapping(r[1], name, desc, False, r[2])
        self._declmemo[memo_key] = out
        return out

    def member_mapping(self, owner, name, desc, is_field):
        """(iname, idesc) for a memberref, or None to defer to the shadow pass.

        Every ref resolves to its declaring class first: MC-declared members
        map to intermediary (even when qualified by a mod subclass owner, as
        javac emits for inherited access); mod-declared members follow their
        declaration's mapping so defs and call sites stay consistent.
        """
        if name in ("<init>", "<clinit>"):
            return name, self.map_desc(desc)
        if (owner, name, desc) in self.shadow:
            return None  # @Shadow usage: pass 2b owns it
        if is_mod(owner):
            r = self.resolve(owner, name, desc, is_field)
            if r is None:
                if (name, desc) in JDK_KEEP or (name, desc) in NETTY_KEEP:
                    key = (owner, name, desc)
                    if key not in self._warned:
                        self._warned.add(key)
                        print(f"[remap] WARN keep-name (JDK/netty decl): "
                              f"{owner} {name} {desc}")
                    return name, self.map_desc(desc)
                raise SystemExit(
                    f"[remap] UNRESOLVABLE mod-owner ref: {owner} {name} {desc}")
            if r[0] == "mc":
                return self.lookup(r[1], name, desc, is_field)
            return self.decl_mapping(r[1], name, desc, is_field, r[2])
        if is_mc(owner):
            if ((owner, name, desc) not in MANUAL
                    and not self.tiny_has(owner, name, desc, is_field)
                    and (name, desc) not in JDK_KEEP
                    and (name, desc) not in NETTY_KEEP):
                raise SystemExit(
                    f"[remap] UNMAPPED mc-owner ref: {owner} {name} {desc}")
            return self.lookup(owner, name, desc, is_field)
        return name, self.map_desc(desc)  # fabric/JDK/mixin/...: keep


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

    def find_nat(self, name, desc):
        for i in range(1, len(self.cp)):
            e = self.cp[i]
            if e is not None and e["tag"] == 12:
                if self.cp[e["ref"]]["str"] == name \
                        and self.cp[e["ref2"]]["str"] == desc:
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


def set_nat_str(cf, nat, which, s, protected=None):
    """Write a NameAndType name/desc, duplicating the Utf8 if shared.

    `protected` is a set of cp indices backing field/method declarations:
    javac interns those Utf8s with NATs, and an in-place rewrite would
    silently rename the declaration too.
    """
    idx = nat["ref"] if which == "name" else nat["ref2"]
    if cf.utf(idx) == s:
        return
    if protected is not None and idx in protected:
        ni = cf.find_utf(s) or cf.add_utf(s)
        if which == "name":
            nat["ref"] = ni
        else:
            nat["ref2"] = ni
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
    this_owner = rel[:-6].replace(os.sep, "/") if rel.endswith(".class") else None
    # 2) Every memberref resolves to its declaring class, then maps.
    # Refs never rewrite NAT Utf8s in place: a changed ref is repointed at a
    # deduplicated (name, desc) NAT, so javac's constant sharing is always safe.
    for i in range(1, len(cf.cp)):
        e = cf.cp[i]
        if e is None or e["tag"] not in (9, 10, 11):
            continue
        owner = orig_class[e["ref"]]
        nat = cf.cp[e["ref2"]]
        name, desc = cf.utf(nat["ref"]), cf.utf(nat["ref2"])
        mapping = mapper.member_mapping(owner, name, desc, e["tag"] == 9)
        if mapping is None:
            continue  # @Shadow usage: pass 2b owns it
        iname, idesc = mapping
        if iname == name and idesc == desc:
            continue
        dup = cf.find_nat(iname, idesc)
        if dup is None:
            ni = cf.find_utf(iname) or cf.add_utf(iname)
            di = cf.find_utf(idesc) or cf.add_utf(idesc)
            dup = cf.add_nat(ni, di)
        e["ref2"] = dup
    # 2c) Method declarations overriding MC members -> intermediary names.
    # Without this, dispatch misses the override (silent) or mod call sites
    # resolved through the subclass keep yarn names the runtime lacks (loud).
    if this_owner is not None and is_mod(this_owner):
        tail = bytearray(cf.tail)
        for kind, name_pos, flags, name_i, desc_i in member_decl_positions(bytes(tail)):
            if kind != "method":
                continue
            dname, ddesc = cf.utf(name_i), cf.utf(desc_i)
            iname, _idesc = mapper.decl_mapping(this_owner, dname, ddesc, False, flags)
            if iname == dname:
                continue
            # Duplicate-on-write: the yarn Utf8 may back call-site NATs that
            # legitimately keep their own mapping.
            ni = cf.find_utf(iname) or cf.add_utf(iname)
            struct.pack_into(">H", tail, name_pos, ni)
            mapper.def_renamed += 1
        cf.tail = bytes(tail)
    # 2b) @Shadow members declared by this mixin class -> intermediary
    # target names (loom equivalent: AP out-mappings). Runs before step 3
    # so descriptors still match their named forms.
    by_name = {(name, desc): inter for (o, name, desc), inter in mapper.shadow.items()
               if o == this_owner}
    if by_name:
        decl_cp = set()
        for _k, _p, _f, name_i, desc_i in member_decl_positions(cf.tail):
            decl_cp.add(name_i)
            decl_cp.add(desc_i)
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
            set_nat_str(cf, nat, "name", inter, decl_cp)
            mapper.shadow_applied.add((this_owner, name, desc))
            if rest:
                ni = cf.find_utf(name) or cf.add_utf(name)
                di = cf.find_utf(desc) or cf.add_utf(desc)
                dup = cf.add_nat(ni, di)
                for (i, _t, _o) in rest:
                    cf.cp[i]["ref2"] = dup
        tail = bytearray(cf.tail)
        for _kind, name_pos, _flags, name_i, desc_i in member_decl_positions(bytes(tail)):
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


def build_declmap(src_dir):
    """Reads every class's declarations from PRE-remap bytes.

    Returns {owner: {"super": owner|None, "ifaces": [...],
    "fields": {(name, desc): flags}, "methods": {(name, desc): flags}}}
    in named forms, so memberrefs can resolve to their declaring class.
    """
    out = {}
    for root, _ds, files in os.walk(src_dir):
        for fn in files:
            if not fn.endswith(".class"):
                continue
            with open(os.path.join(root, fn), "rb") as f:
                cf = ClassFile(f.read())
            orig = {}
            for i in range(1, len(cf.cp)):
                e = cf.cp[i]
                if e is not None and e["tag"] == 7:
                    orig[i] = cf.utf(e["ref"])
            this_i, sup_i = struct.unpack_from(">HH", cf.tail, 2)
            n_ifaces = struct.unpack_from(">H", cf.tail, 6)[0]
            ifaces = [orig[struct.unpack_from(">H", cf.tail, 8 + 2 * k)[0]]
                      for k in range(n_ifaces)]
            fields, methods = {}, {}
            for kind, _pos, flags, name_i, desc_i in member_decl_positions(cf.tail):
                box = fields if kind == "field" else methods
                box[(cf.utf(name_i), cf.utf(desc_i))] = flags
            out[orig[this_i]] = {
                "super": orig.get(sup_i) if sup_i else None,
                "ifaces": ifaces, "fields": fields, "methods": methods,
            }
    return out


def main():
    src_dir, dst_dir = sys.argv[1], sys.argv[2]
    mapper = Mapper(build_declmap(src_dir))
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
    print(f"[remap] renamed {mapper.def_renamed} override def(s) to intermediary")
    missing = set(mapper.shadow) - mapper.shadow_applied
    if missing:
        raise SystemExit(f"[remap] shadow members never matched: {sorted(missing)}")
    if mapper.shadow:
        print(f"[remap] renamed {len(mapper.shadow_applied)} shadow member(s) "
              f"in mixin classes")


if __name__ == "__main__":
    main()
