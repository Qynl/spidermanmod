#!/usr/bin/env python3
"""Parse .java sources into kinds/members for stub linkage auditing.

Shared by audit_stubs.py (one-shot stub-vs-truth comparison using /tmp
reference checkouts) and verify.py (in-build drift check of gen-stubs
against the frozen build-local/api-kinds.txt).

The parser is deliberately narrow: package, imports, type declarations
(class/interface/enum/record/@interface, incl. nesting), extends/implements,
method/ctor/field declarations with static flags and normalized (erased)
descriptors. Anything exotic that fails to parse surfaces as an audit gap
for manual review rather than a silent pass.
"""

import os
import re

PRIMITIVE = {
    "boolean": "Z", "byte": "B", "char": "C", "short": "S",
    "int": "I", "long": "J", "float": "F", "double": "D",
}

JLANG = {
    "Object", "String", "Class", "Integer", "Long", "Boolean", "Double",
    "Float", "Short", "Byte", "Character", "Void", "Enum", "Record",
    "Thread", "Runnable", "Comparable", "Iterable", "Exception",
    "RuntimeException", "Error", "Throwable", "StringBuilder", "StringBuffer",
    "Math", "System", "Override", "Deprecated", "SuppressWarnings",
    "FunctionalInterface", "SafeVarargs",
}

# First words that prove a "Type name(...)" match is a statement, not a decl.
KEYWORDS = {
    "return", "new", "if", "else", "for", "while", "do", "switch", "case",
    "catch", "throw", "throws", "assert", "break", "continue", "yield",
    "instanceof", "this", "super", "import", "package", "record", "class",
    "interface", "enum", "synchronized", "try", "finally", "var",
}

MODS = ("public|protected|private|static|final|abstract|default|synchronized|"
        "native|strictfp|sealed|non-sealed|transient|volatile")
MODIFIERS = set(MODS.replace("|", " ").split())

ANNOT = r"(?:@\w+(?:\s*\([^(){};]*\))?\s*)*"
# Two paren levels (annotation args in prefixes/params); atoms have
# disjoint first-chars so matching stays linear.
PAR2 = r"\((?:[^(){};]|\([^(){};]*\))*\)"
BAL1_PARENS = r"(?:[^;{}()]|\((?:[^(){};]\([^(){};]*\))*\))*"
TYPE_CHARS = r"[\w.$<>\[\]?&,\s]"

TYPE_PAT = (r"(?<![.\w])(?P<tkind>@interface|class|interface|enum|record)"
            r"\s+(?P<tname>\w+)(?P<textras>[^;{}]*?)(?=[;{])")
# NOTE: METHOD/FIELD match a coarse shape and validate in Python. Earlier
# versions with lazy TYPE + name interplay backtracked superlinearly on
# whitespace runs; the shapes below are linear (greedy prefix with disjoint
# atoms, lazy whole-decl up to a literal ';'). Requires Python 3.11+.
# Prefix excludes bare "=" so field initializers with calls never match
# as methods (FIELD owns them); the name lookbehind stops the greedy
# prefix from splitting a word (`AtomicInteger(` must not yield `r`).
METHOD_PAT = (r"(?:^|[;{}])(?P<prefix>(?:(?!record\s+\w)[^{};()=]|" + PAR2 + r")*)"
              r"(?<![\w$])(?P<name>\w+)\s*\((?P<params>" + BAL1_PARENS + r")\)"
              r"\s*(?:(?:throws|default)\s+[^{};]+)?\s*(?=[;{])")
CTOR_PAT = (r"(?:^|[;{}])\s*+" + ANNOT +
            r"(?P<cmods>(?:(?:" + MODS + r")\s+)*)"
            r"(?P<cname>\w+)\s*\((?P<cparams>" + BAL1_PARENS + r")\)"
            r"\s*(?:(?:throws|default)\s+[^{};]+)?\s*(?=[;{])")
FIELD_PAT = (r"(?:^|[;{}])\s*+(?:(?P<fwhole>[^{};]++)\s*+(?=;)|(?P<fpre>[^{};=]++)\s*+(?==))")

# CTOR before METHOD: `public Foo(` would otherwise match METHOD with a
# bogus "public" return type (the regex backtracks the mods group).
COMBINED = re.compile(TYPE_PAT + "|" + CTOR_PAT + "|" + METHOD_PAT + "|"
                      + FIELD_PAT + r"|(?P<brace>[{}])")


def strip_java(src):
    """Blanks comments/strings/chars/text-blocks, preserving newlines."""
    out = []
    i, n = 0, len(src)
    state = None
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        nxt2 = src[i + 2] if i + 2 < n else ""
        if state is None:
            if c == "/" and nxt == "/":
                state = "line"
                out += [" ", " "]
                i += 2
            elif c == "/" and nxt == "*":
                state = "block"
                out += [" ", " "]
                i += 2
            elif c == '"' and nxt == '"' and nxt2 == '"':
                state = "text"
                out += [" ", " ", " "]
                i += 3
            elif c == '"':
                state = "str"
                out.append(" ")
                i += 1
            elif c == "'":
                state = "char"
                out.append(" ")
                i += 1
            else:
                out.append(c)
                i += 1
        elif state == "line":
            if c == "\n":
                state = None
                out.append(c)
            else:
                out.append(" ")
            i += 1
        elif state == "block":
            if c == "*" and nxt == "/":
                state = None
                out += [" ", " "]
                i += 2
            else:
                out.append("\n" if c == "\n" else " ")
                i += 1
        elif state == "str":
            if c == "\\":
                esc = src[i + 1] if i + 1 < n else ""
                out.append(" ")
                out.append("\n" if esc == "\n" else " ")
                i += 2
            elif c == '"':
                state = None
                out.append(" ")
                i += 1
            else:
                out.append("\n" if c == "\n" else " ")
                i += 1
        elif state == "char":
            if c == "\\":
                out += [" ", " "]
                i += 2
            elif c == "'":
                state = None
                out.append(" ")
                i += 1
            else:
                out.append(" ")
                i += 1
        elif state == "text":
            if c == '"' and nxt == '"' and nxt2 == '"':
                state = None
                out += [" ", " ", " "]
                i += 3
            elif c == "\\":
                esc = src[i + 1] if i + 1 < n else ""
                out.append(" ")
                out.append("\n" if esc == "\n" else " ")
                i += 2
            else:
                out.append("\n" if c == "\n" else " ")
                i += 1
    return "".join(out)


def split_toplevel(s, sep=","):
    """Splits on sep ignoring <>()[] nesting."""
    parts, depth, cur = [], 0, []
    opens = {"<", "(", "["}
    closes = {">", ")", "]"}
    for c in s:
        if c in opens:
            depth += 1
            cur.append(c)
        elif c in closes:
            depth = max(0, depth - 1)
            cur.append(c)
        elif c == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    parts.append("".join(cur))
    return parts


def erase_generics(t):
    """Removes <...> groups (nested-aware)."""
    out, depth = [], 0
    for c in t:
        if c == "<":
            depth += 1
        elif c == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            out.append(c)
    return "".join(out)


def _resolve_dotted(dotted, index):
    """Resolves a.b.C.D to binary, preferring Outer$Inner when the outer
    type is known in the index."""
    parts = dotted.split(".")
    if index is not None:
        for i in range(len(parts) - 1, 0, -1):
            prefix = "/".join(parts[:i])
            if prefix in index:
                return prefix + "$" + "$".join(parts[i:])
    return "/".join(parts)


def _balanced_angle(s):
    """If s starts with '<', returns inner text up to its match, else None."""
    if not s.startswith("<"):
        return None
    depth = 0
    for i, c in enumerate(s):
        if c == "<":
            depth += 1
        elif c == ">":
            depth -= 1
            if depth == 0:
                return s[1:i]
    return None


def _parse_tvars(inner, ctx):
    """Parses 'T extends Bound, U' into {name: erased bound desc}."""
    out = {}
    for seg in split_toplevel(inner):
        flat = " ".join(seg.split())
        if " extends " in flat:
            name, bound = flat.split(" extends ", 1)
            name = name.strip()
            bound = bound.split("&")[0].strip()
            if not name or not bound:
                continue
            out[name] = norm_type(bound, ctx)
        else:
            name = flat.strip()
            if name:
                out[name] = "Ljava/lang/Object;"
    return out


def norm_type(raw, ctx):
    """Normalizes a source-level type to an erased descriptor string.

    ctx: {pkg, imports, enclosing (binary or None), index (or None)}.
    Unknown simple names resolve deterministically (same-package guess),
    so stub-vs-truth comparisons stay meaningful even when unresolvable.
    """
    t = raw.strip()
    t = re.sub(r"@\w+(?:\([^()]*\))?\s*", "", t)
    t = t.replace("...", "[]")
    dims = t.count("[]")
    t = t.replace("[]", "").strip()
    t = erase_generics(t).strip()
    if " " in t:
        t = t.split()[-1]
    if not t or t == "?":
        base = "Ljava/lang/Object;"
    elif t in PRIMITIVE:
        base = PRIMITIVE[t]
    elif t == "void":
        base = "V"
    elif t in ctx.get("tvars", {}):
        base = ctx["tvars"][t]
    elif re.fullmatch(r"[A-Z]", t):
        base = "Ljava/lang/Object;"  # unbounded type variable erasure
    elif "." in t:
        first = t.split(".")[0]
        imp = ctx["imports"].get(first)
        if imp is not None:
            rest = t[len(first) + 1:]
            base = "L" + imp + "$" + rest.replace(".", "$") + ";"
        elif first in JLANG:
            base = "Ljava/lang/" + t.replace(".", "$") + ";"
        else:
            idx = ctx.get("index")
            cand = None
            if idx is not None and ctx.get("pkg") and "." in t:
                parts = t.split(".")
                probe = ctx["pkg"] + "/" + parts[0] + "$" + "$".join(parts[1:])
                if probe in idx:
                    cand = probe
            base = "L" + (cand if cand is not None else _resolve_dotted(t, idx)) + ";"
    elif t in ctx["imports"]:
        base = "L" + ctx["imports"][t] + ";"
    elif t in JLANG:
        base = "Ljava/lang/" + t + ";"
    else:
        enc = ctx.get("enclosing")
        idx = ctx.get("index")
        hit = None
        if enc is not None and idx is not None:
            if t == enc.split("$")[-1]:
                hit = enc  # self reference: Builder inside EntityType$Builder
            else:
                e = enc
                while e is not None and hit is None:
                    if e + "$" + t in idx:
                        hit = e + "$" + t
                    e = e.rsplit("$", 1)[0] if "$" in e else None
        if hit is not None:
            base = "L" + hit + ";"
        else:
            base = "L" + ctx["pkg"] + "/" + t + ";"
    return "[" * dims + base


def parse_params(raw, ctx):
    """Returns [norm_desc, ...] for a raw parameter list."""
    raw = raw.strip()
    if not raw:
        return []
    out = []
    for p in split_toplevel(raw):
        p = re.sub(r"@\w+(?:\([^()]*\))?\s*", "", p).strip()
        p = re.sub(r"\bfinal\b", "", p).strip()
        if not p:
            continue
        m = re.match(r"^(.*\S)\s+(\w+)((?:\s*\[\s*\])*)$", p)
        if m:
            typ, _name, extra = m.group(1), m.group(2), m.group(3)
            typ = typ + "[]" * extra.count("[")
        else:
            typ = p
        out.append(norm_type(typ, ctx))
    return out


def _ctx(info, enclosing):
    _tvars = {}
    if enclosing is not None:
        for _t in info.get("types", []):
            if _t.get("binary") == enclosing:
                _tvars = dict(_t.get("tvars") or {})
                break
    return {"pkg": info["pkg"], "imports": info["imports"],
            "enclosing": enclosing, "index": info.get("index"),
            "tvars": _tvars}


def _strip_annots(s):
    """Removes @Annotations (depth-aware) from a declaration prefix."""
    out, i, n = [], 0, len(s)
    while i < n:
        if s[i] == "@" and (i == 0 or not (s[i - 1].isalnum() or s[i - 1] == "_")):
            j = i + 1
            while j < n and (s[j].isalnum() or s[j] in "._"):
                j += 1
            while j < n and s[j] in " \t":
                j += 1
            if j < n and s[j] == "(":
                d = 0
                while j < n:
                    if s[j] == "(":
                        d += 1
                    elif s[j] == ")":
                        d -= 1
                        if d == 0:
                            j += 1
                            break
                    j += 1
            out.append(" ")
            i = j
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def parse_java(path, index=None):
    """Parses one .java file. Returns {pkg, imports, types[...]}.

    types: {binary, kind, abstract, is_enum, is_record, is_annotation,
            super_raw, ifaces_raw, file, line, methods, fields}.
    methods: [(name, is_static, norm_desc, line)].
    fields: [(name, is_static, norm_type, line)].
    """
    src = open(path, encoding="utf-8", errors="replace").read()
    stripped = strip_java(src)
    m = re.search(r"^package ([\w.]+);", stripped, re.M)
    pkg = m.group(1).replace(".", "/") if m else ""
    imports = {}
    for im in re.finditer(r"^import (?!static)([\w.]+);", stripped, re.M):
        full = im.group(1)
        imports[full.split(".")[-1]] = full.replace(".", "/")
    info = {"path": path, "pkg": pkg, "imports": imports, "index": index,
            "types": []}
    stack = []  # [depth, binary, typeinfo]
    depth = 0
    scopes = [[0, True]]  # [body_depth, is_type]; file level is type-like
    pending_type = False  # set by TYPE; next '{' opens its body
    for m in COMBINED.finditer(stripped):
        line = stripped.count("\n", 0, m.start()) + 1
        if m.group("brace"):
            if m.group("brace") == "{":
                depth += 1
                scopes.append([depth, pending_type])
                pending_type = False
            else:
                depth -= 1
                while len(scopes) > 1 and scopes[-1][0] > depth:
                    scopes.pop()
                    # A type whose body just closed can own no more members.
                    while stack and stack[-1][0] >= depth:
                        stack.pop()
            continue
        # Anchors consume their character: a match starting on "}" or "{"
        # must account for that brace (no separate event will fire).
        first = stripped[m.start()]
        if first == "}":
            depth -= 1
            while len(scopes) > 1 and scopes[-1][0] > depth:
                scopes.pop()
                # A type whose body just closed can own no more members.
                while stack and stack[-1][0] >= depth:
                    stack.pop()
        elif first == "{":
            depth += 1
            scopes.append([depth, pending_type])
            pending_type = False
        if m.group("tkind"):
            if not scopes[-1][1]:
                # Local class inside a method body: no stable binary
                # name. Its '{' fires separately (non-type scope).
                continue
            while stack and stack[-1][0] >= depth:
                stack.pop()
            kind = {"class": "CLASS", "interface": "INTERFACE",
                    "enum": "CLASS", "record": "CLASS",
                    "@interface": "INTERFACE"}[m.group("tkind")]
            name = m.group("tname")
            binary = (stack[-1][1] + "$" + name) if stack \
                else ((pkg + "/" if pkg else "") + name)
            extras = m.group("textras") or ""
            ti = {"binary": binary, "kind": kind, "abstract": False,
                  "is_enum": m.group("tkind") == "enum",
                  "is_record": m.group("tkind") == "record",
                  "is_annotation": m.group("tkind") == "@interface",
                  "super_raw": None, "ifaces_raw": [], "file": path,
                  "line": line, "methods": [], "fields": [],
                  "match_end": m.end(), "match_start": m.start()}
            seg = re.split(r"[;{}]",
                           stripped[max(0, m.start() - 160):m.start()])[-1]
            if re.search(r"\babstract\b", seg):
                ti["abstract"] = True
            sm = re.search(r"\bextends\s+(" + TYPE_CHARS + r"+?)(?=\s+"
                            r"(?:implements|permits)\b|\s*[;{]|\s*$)", extras)
            if sm:
                if kind == "INTERFACE":
                    ti["ifaces_raw"].extend(
                    [x.strip() for x in split_toplevel(sm.group(1))])
                else:
                    ti["super_raw"] = sm.group(1).strip()
            im = re.search(r"\bimplements\s+(" + TYPE_CHARS + r"+?)(?=\s+"
                            r"permits\b|\s*[;{]|\s*$)", extras)
            if im:
                ti["ifaces_raw"].extend(
                    [x.strip() for x in split_toplevel(im.group(1))])
            ti["tvars"] = {}
            _ex = extras.strip()
            if _ex.startswith("<"):
                _inner = _balanced_angle(_ex)
                if _inner is not None:
                    ti["tvars"] = _parse_tvars(_inner, _ctx(info, binary))
            stack.append([depth, binary, ti])
            info["types"].append(ti)
            # The '{' is lookahead (unconsumed); the next brace event or
            # anchored match opens this type's body.
            pending_type = True
            continue
        # Member matches never consume braces (lookahead ends); '{'
        # always arrives as its own brace event. No bump needed.
        mdepth = depth
        if not stack or stack[-1][0] + 1 != mdepth:
            continue  # only direct members, never method bodies
        _ti = stack[-1][2]
        ctx = _ctx(info, _ti["binary"])
        if m.group("cname") is not None:
            simple = _ti["binary"].split("$")[-1].split("/")[-1]
            if m.group("cname") != simple:
                continue
            if m.group("cname") in KEYWORDS:
                continue
            params = parse_params(m.group("cparams") or "", ctx)
            _ti["methods"].append(
                ("<init>", False, "(" + "".join(params) + ")V", line))
        elif m.group("name") is not None:
            prefix = _strip_annots(m.group("prefix") or "")
            core = re.sub(r"\b(?:" + MODS + r")\b", "", prefix).strip()
            _mt = None
            if core.startswith("<"):
                _inner = _balanced_angle(core)
                if _inner is not None:
                    _mt = _parse_tvars(_inner, ctx)
                    core = core[len(_inner) + 2:].strip()
            if _mt:
                ctx = dict(ctx, tvars=dict(ctx.get("tvars", {}), **_mt))
            if not core:
                continue
            first = core.split()[0]
            if first in KEYWORDS or first in MODIFIERS:
                continue
            params = parse_params(m.group("params") or "", ctx)
            ret = norm_type(core, ctx)
            is_static = re.search(r"\bstatic\b", prefix) is not None
            _ti["methods"].append(
                (m.group("name"), is_static,
                 "(" + "".join(params) + ")" + ret, line))
        elif m.group("fwhole") is not None or m.group("fpre") is not None:
            whole = _strip_annots((m.group("fwhole") or m.group("fpre")) or "")
            if ("(" in whole or ")" in whole) and "=" not in whole:
                continue  # method-shaped; METHOD owns it
            is_static = re.search(r"\bstatic\b", whole) is not None
            core = re.sub(r"\b(?:" + MODS + r")\b", "", whole).strip()
            if not core or "(" in core.split("=")[0]:
                continue
            segs = split_toplevel(core, ",")
            base = None
            for si, seg in enumerate(segs):
                lhs = split_toplevel(seg, "=")[0].strip()
                if si == 0:
                    fm = re.match(r"^(.*\S)\s+(\w+)((?:\s*\[\s*\])*)$", lhs)
                    if not fm:
                        break
                    ftype = fm.group(1)
                    if ftype.strip().split()[0] in KEYWORDS:
                        break
                    base = norm_type(ftype, ctx)
                    nm, dims = fm.group(2), fm.group(3).count("[")
                else:
                    nm2 = re.match(r"^(\w+)((?:\s*\[\s*\])*)$", lhs)
                    if not nm2 or base is None:
                        continue
                    nm, dims = nm2.group(1), nm2.group(2).count("[")
                _ti["fields"].append((nm, is_static, "[" * dims + base, line))
    for ti in info["types"]:
        _finish_type(ti, info, stripped)
    return info


def _finish_type(ti, info, stripped):
    ctx = _ctx(info, ti["binary"])
    # Resolve extends/implements to binaries.
    if ti["super_raw"]:
        ti["super"] = _checked_resolve(ti["super_raw"], ctx)
    else:
        ti["super"] = None
    ti["ifaces"] = [_checked_resolve(x, ctx) for x in ti["ifaces_raw"]]
    if ti["is_enum"]:
        enum_bin = "L" + ti["binary"] + ";"
        ti["methods"].append(("values", True, "()[" + enum_bin, ti["line"]))
        ti["methods"].append(("valueOf", True,
                              "(Ljava/lang/String;)" + enum_bin, ti["line"]))
        rest = stripped[ti["match_end"]:]
        seg = None
        if rest.startswith("{"):
            d, i = 1, 1
            while i < len(rest):
                if rest[i] == "{":
                    d += 1
                elif rest[i] == "}":
                    d -= 1
                    if d == 0:
                        break
                elif rest[i] == ";" and d == 1:
                    seg = rest[1:i]
                    break
                i += 1
            if seg is not None:
                for c in split_toplevel(seg):
                    c = re.sub(r"\(.*", "", c, flags=re.S)
                    c = re.sub(r"\{.*", "", c, flags=re.S).strip()
                    if re.fullmatch(r"\w+", c):
                        ti["fields"].append((c, True, enum_bin, ti["line"]))
    if ti["is_record"]:
        # Components: first balanced paren group past the record name.
        header = stripped[ti["match_start"]:ti["match_end"]]
        nm = ti["binary"].split("$")[-1].split("/")[-1]
        comp_raw = ""
        ni = header.find(nm)
        pi = header.find("(", ni + len(nm)) if ni >= 0 else -1
        if pi >= 0:
            i, depth = pi + 1, 1
            while i < len(header) and depth:
                if header[i] == "(":
                    depth += 1
                elif header[i] == ")":
                    depth -= 1
                i += 1
            comp_raw = header[pi + 1:i - 1]
        comps = parse_params(comp_raw, ctx) if comp_raw.strip() else []
        comp_names = []
        if comp_raw.strip():
            for c in split_toplevel(comp_raw):
                c = re.sub(r"@\w+(?:\([^()]*\))?\s*", "", c).strip()
                c = re.sub(r"\bfinal\b", "", c).strip()
                mm = re.match(r"^(.*\S)\s+(\w+)((?:\s*\[\s*\])*)$", c)
                comp_names.append(mm.group(2) if mm else "")
        ti["methods"].append(("<init>", False,
                              "(" + "".join(comps) + ")V", ti["line"]))
        for cname, ctype in zip(comp_names, comps):
            if re.fullmatch(r"\w+", cname):
                ti["methods"].append((cname, False, "()" + ctype, ti["line"]))


def _checked_resolve(raw, ctx):
    t = erase_generics(raw).strip()
    if " " in t:
        t = t.split()[-1]
    return norm_type(t, ctx)[1:-1] if norm_type(t, ctx).startswith("L") \
        else norm_type(t, ctx)


def build_index(java_files):
    """Parses files in two passes (names first so dotted Outer.Inner
    resolves) and returns {binary_name: typeinfo}."""
    first, index = [], {}
    for path in java_files:
        try:
            info = parse_java(path, index=None)
        except Exception as e:  # noqa: BLE001 - audit must not die
            print(f"[apiparser] PARSE-FAIL {path}: {e}")
            continue
        first.append((path, info))
        for ti in info["types"]:
            index.setdefault(ti["binary"], ti)
    # Second pass with the index populated for $ resolution.
    for path, _info in first:
        try:
            info = parse_java(path, index=index)
        except Exception as e:  # noqa: BLE001
            print(f"[apiparser] PARSE-FAIL(pass2) {path}: {e}")
            continue
        for ti in info["types"]:
            index[ti["binary"]] = ti
    return index
