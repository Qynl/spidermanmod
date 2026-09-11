#!/usr/bin/env python3
"""Generate javac stub sources for Minecraft Yarn API surface used by this mod.

Reads build-local/api-surface.txt, resolves members against yarn_db (tiny v2),
emits compilable stubs under build-local/gen-stubs/ using fully-qualified names.

Rules (see README for rationale):
  - every member declared exactly once, on its yarn-attributed owner
  - NO `final` on fields (prevents javac constant inlining; remap fixes owners)
  - hierarchy links (EXTENDS/IMPLEMENTS) come from api-surface (curated, minimal)
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from yarn_db import load as yarn_load  # noqa: E402

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SURFACE = os.path.join(REPO_ROOT, "build-local", "api-surface.txt")
OUT_DIR = os.path.join(REPO_ROOT, "build-local", "gen-stubs")

PRIM = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int",
        "J": "long", "F": "float", "D": "double", "V": "void"}


def split_args(desc):
    """Split method descriptor args (inside parens) into single-type descriptors."""
    body = desc[1:desc.index(")")]
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "L":
            j = body.index(";", i)
            out.append(body[i:j + 1])
            i = j + 1
        elif c == "[":
            j = i
            while body[j] == "[":
                j += 1
            if body[j] == "L":
                k = body.index(";", j)
                out.append(body[i:k + 1])
                i = k + 1
            else:
                out.append(body[i:j + 1])
                i = j + 1
        else:
            out.append(c)
            i += 1
    return out


def ret_type(desc):
    return desc[desc.index(")") + 1:]


def src(binary):
    """Binary name -> Java source name (dots, including for nested classes)."""
    return binary.replace("/", ".").replace("$", ".")


def jtype(t):
    """Descriptor type -> Java source type (fully qualified)."""
    depth = 0
    while t.startswith("["):
        depth += 1
        t = t[1:]
    if t in PRIM:
        base = PRIM[t]
    elif t.startswith("L") and t.endswith(";"):
        base = src(t[1:-1])
    else:
        raise ValueError(f"bad type desc: {t}")
    return base + "[]" * depth


def default_value(t):
    if t == "boolean":
        return "false"
    if t in ("byte", "short", "int", "char", "long"):
        return "0"
    if t == "float":
        return "0.0f"
    if t == "double":
        return "0.0"
    if t == "void":
        return ""
    return "null"


def binary_to_pkg_cls(binary):
    if "$" in binary:
        outer, inner = binary.split("$", 1)
        pkg = ".".join(outer.split("/")[:-1])
        return pkg, outer.split("/")[-1], inner.replace("$", ".")
    parts = binary.split("/")
    return ".".join(parts[:-1]), parts[-1], None


class Decl:
    def __init__(self, kind, binary):
        self.kind = kind  # CLASS | INTERFACE | HOLDER
        self.binary = binary
        self.extends = None
        self.implements = []
        self.ctors = []
        self.methods = []  # (static, name, desc)
        self.fields = []  # (static, name, desc)
        self.raws = []
        self.typeparams = ""


def parse_surface(path):
    decls, cur = [], None
    for raw in open(path, encoding="utf-8"):
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if line[0] not in (" ", "\t"):
            toks = line.split()
            kind, binary = toks[0], toks[1]
            assert kind in ("CLASS", "INTERFACE", "HOLDER"), line
            cur = Decl(kind, binary)
            rest = " ".join(toks[2:])
            m = re.search(r"EXTENDS (\S+)", rest)
            if m:
                cur.extends = m.group(1)
            m = re.search(r"IMPLEMENTS (.+)$", rest)
            if m:
                cur.implements = [x.strip() for x in m.group(1).split(",")]
            m = re.search(r"TYPEPARAMS (\S+)", rest)
            if m:
                cur.typeparams = m.group(1).replace(";", ",")
            cur.abstract = bool(re.search(r"\bABSTRACT\b", rest))
            decls.append(cur)
        else:
            assert cur is not None, line
            toks = line.split()
            d = toks[0]
            if d == "CTOR":
                cur.ctors.append(toks[1])
            elif d == "METHOD":
                name = toks[1]
                static = "STATIC" in toks
                args = ""
                if "ARGS" in toks:
                    args = toks[toks.index("ARGS") + 1]
                throws = []
                if "THROWS" in toks:
                    throws = [x.strip() for x in toks[toks.index("THROWS") + 1].split(",")]
                cur.methods.append((static, name, args, throws))
            elif d == "FIELD":
                name = toks[1]
                static = "STATIC" in toks
                cur.fields.append((static, name))
            elif d == "RAW":
                cur.raws.append(line.split("RAW", 1)[1].strip())
            else:
                raise ValueError(f"bad directive: {line}")
    return decls


def resolve(db, decl):
    """Resolve METHOD/FIELD names to descriptors via yarn_db. Returns (methods, fields) with descs."""
    out_m, out_f = [], []
    for static, name, args, throws in decl.methods:
        cands = [m for m in db["methods"].get(decl.binary, []) if m["n"] == name]
        if args:
            cands = [m for m in cands if args in m.get("dn", m["d"])]
        if not cands:
            raise SystemExit(f"[stubgen] MISSING method {decl.binary}.{name} ARGS={args!r}")
        if len(cands) > 1:
            raise SystemExit(f"[stubgen] AMBIGUOUS method {decl.binary}.{name}: "
                             + str([c.get("dn", c["d"]) for c in cands]))
        out_m.append((static, name, cands[0].get("dn", cands[0]["d"]), throws))
    for static, name in decl.fields:
        cands = [m for m in db["fields"].get(decl.binary, []) if m["n"] == name]
        if not cands:
            raise SystemExit(f"[stubgen] MISSING field {decl.binary}.{name}")
        out_f.append((static, name, cands[0].get("dn", cands[0]["d"])))
    return out_m, out_f


def emit(decl, methods, fields):
    if decl.kind == "INTERFACE":
        if decl.ctors:
            raise SystemExit(f"STUBGEN ERROR: {decl.binary}: CTOR under INTERFACE is illegal in Java")
        if fields:
            raise SystemExit(f"STUBGEN ERROR: {decl.binary}: FIELD under INTERFACE is forbidden "
                             f"(interface fields need explicit values; use RAW instead)")
        for _n, _t, _s in fields:
            pass
    pkg, cls, inner = binary_to_pkg_cls(decl.binary)
    if inner:
        raise AssertionError("inners handled by grouping")
    L = [f"package {pkg};", ""]
    if decl.kind == "INTERFACE":
        head = f"public interface {cls}{decl.typeparams}"
        if decl.extends:
            head += " extends " + src(decl.extends)
    else:
        head = f"public {'abstract ' if getattr(decl, 'abstract', False) else ''}class {cls}{decl.typeparams}"
        if decl.extends:
            head += " extends " + src(decl.extends)
        if decl.implements:
            head += " implements " + ", ".join(src(i) for i in decl.implements)
    L.append(head + " {")
    for r in decl.raws:
        L.append(f"    {r}")
    for desc in decl.ctors:
        args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
        L.append(f"    public {cls}({', '.join(args)}) {{}}")
    if decl.kind != "INTERFACE" and "()V" not in decl.ctors:
        # Synthetic no-arg keeps subclass stub chains compilable (implicit super()).
        # CI loom build backstops any accidental use in mod code.
        L.append(f"    public {cls}() {{}}")
    for static, name, desc, throws in methods:
        rt = jtype(ret_type(desc))
        args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
        pre = "public static " if static else "public "
        th = (" throws " + ", ".join(src(t) for t in throws)) if throws else ""
        if decl.kind == "INTERFACE":
            if static:
                L.append(f"    static {rt} {name}({', '.join(args)}){th} {{return {default_value(rt)};}}"
                         if rt != "void" else f"    static void {name}({', '.join(args)}){th} {{}}")
            else:
                L.append(f"    {rt} {name}({', '.join(args)}){th};")
        else:
            ret = "" if rt == "void" else f"return {default_value(rt)};"
            L.append(f"    {pre}{rt} {name}({', '.join(args)}){th} {{{ret}}}")
    for static, name, desc in fields:
        t = jtype(desc)
        pre = "public static " if static else "public "
        L.append(f"    {pre}{t} {name};")
    L.append("}")
    return "\n".join(L) + "\n"


def emit_group(outer_decl, inners):
    """Emit outer class with nested static inner classes."""
    pkg, cls, _ = binary_to_pkg_cls(outer_decl.binary)
    L = [f"package {pkg};", ""]
    head = f"public class {cls}{outer_decl.typeparams}"
    if outer_decl.extends:
        head += " extends " + outer_src(decl.extends)
    if outer_decl.implements:
        head += " implements " + ", ".join(src(i) for i in outer_decl.implements)
    L.append(head + " {")
    for _static, name, desc, throws in outer_decl.resolved_m:
        rt = jtype(ret_type(desc))
        args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
        pre = "public static " if _static else "public "
        th = (" throws " + ", ".join(src(t) for t in throws)) if throws else ""
        ret = "" if rt == "void" else f"return {default_value(rt)};"
        L.append(f"    {pre}{rt} {name}({', '.join(args)}){th} {{{ret}}}")
    for _static, name, desc in outer_decl.resolved_f:
        L.append(f"    {'public static ' if _static else 'public '}{jtype(desc)} {name};")
    for desc in outer_decl.ctors:
        args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
        L.append(f"    public {cls}({', '.join(args)}) {{}}")
    if "()V" not in outer_decl.ctors:
        L.append(f"    public {cls}() {{}}")
    for r in outer_decl.raws:
        L.append(f"    {r}")
    for inner_decl in inners:
        _p, _c, inner_name = binary_to_pkg_cls(inner_decl.binary)
        if inner_decl.kind == "INTERFACE":
            if inner_decl.ctors or inner_decl.resolved_f:
                raise SystemExit(f"STUBGEN ERROR: {inner_decl.binary}: inner INTERFACE with CTOR/FIELD")
            L.append(f"    public static interface {inner_name} {{")
            for _static, name, desc, throws in inner_decl.resolved_m:
                rt = jtype(ret_type(desc))
                args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
                th = (" throws " + ", ".join(src(t) for t in throws)) if throws else ""
                L.append(f"        {rt} {name}({', '.join(args)}){th};")
            for r in inner_decl.raws:
                L.append(f"        {r}")
            L.append("    }")
            continue
        L.append(f"    public static class {inner_name} {{")
        for _static, name, desc, throws in inner_decl.resolved_m:
            rt = jtype(ret_type(desc))
            args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
            pre = "public static " if _static else "public "
            th = (" throws " + ", ".join(src(t) for t in throws)) if throws else ""
            ret = "" if rt == "void" else f"return {default_value(rt)};"
            L.append(f"        {pre}{rt} {name}({', '.join(args)}){th} {{{ret}}}")
        for _static, name, desc in inner_decl.resolved_f:
            L.append(f"        {'public static ' if _static else 'public '}{jtype(desc)} {name};")
        for desc in inner_decl.ctors:
            args = [f"{jtype(t)} p{i}" for i, t in enumerate(split_args(desc))]
            L.append(f"        public {inner_name}({', '.join(args)}) {{}}")
        for r in inner_decl.raws:
            L.append(f"        {r}")
        L.append("    }")
    L.append("}")
    return "\n".join(L) + "\n"


def main():
    db = yarn_load()
    decls = parse_surface(SURFACE)
    for d in decls:
        d.resolved_m, d.resolved_f = resolve(db, d)
    # group inners
    outers = {}
    for d in decls:
        if "$" not in d.binary:
            outers[d.binary] = (d, [])
    for d in decls:
        if "$" in d.binary:
            outer = d.binary.split("$")[0]
            assert outer in outers, f"inner {d.binary} without outer decl"
            outers[outer][1].append(d)
    n = 0
    for binary, (d, inners) in sorted(outers.items()):
        pkg, cls, _ = binary_to_pkg_cls(binary)
        src = emit_group(d, inners) if inners else emit(d, d.resolved_m, d.resolved_f)
        # for non-grouped, emit() needs resolved lists; patch: re-resolve via emit signature
        if not inners:
            pass
        path = os.path.join(OUT_DIR, *pkg.split("."), cls + ".java")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(src)
        n += 1
    print(f"[stubgen] wrote {n} stub files -> {OUT_DIR}")


if __name__ == "__main__":
    main()
