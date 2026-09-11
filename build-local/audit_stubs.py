#!/usr/bin/env python3
"""Audit generated stubs against real API sources (kind/static/descriptor).

Compares every type + member in build-local/gen-stubs/ against the truth
corpora (yarn MC sources, fabric-loader, fabric-api, brigadier, gson,
slf4j, netty, mixin). Catches the IncompatibleClassChangeError bug class:
  - CLASS vs INTERFACE kind mismatches (invokevirtual vs invokeinterface)
  - static vs instance mismatches
  - descriptor (erasure) mismatches -> NoSuchMethodError/NoSuchFieldError
  - phantom members with no truth at all

Usage: python3 build-local/audit_stubs.py [--emit-kinds PATH]
Exit 1 if any ERROR finding, else 0.
"""

import glob
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import apiparser

STUBS_DIR = os.path.join(HERE, "gen-stubs")

# (package prefix, source dirs) — first existing file wins. Order matters:
# more specific loader prefixes before the general fabric prefix.
TRUTH_ROOTS = [
    ("net/fabricmc/loader/", ["/tmp/loader-ref/src/main/java"]),
    ("net/fabricmc/api/", ["/tmp/loader-ref/src/main/java"]),
    ("net/fabricmc/", sorted(glob.glob("/tmp/fabric_ref2/*/src/main/java"))
     + sorted(glob.glob("/tmp/fabric_ref2/*/src/client/java"))),
    ("net/minecraft/", ["/tmp/mc-yarn/minecraft/src"]),
    ("com/mojang/brigadier/", ["/tmp/tp-brigadier/src/main/java"]),
    ("com/google/gson/", ["/tmp/tp-gson/gson/src/main/java"]),
    ("org/slf4j/", ["/tmp/tp-slf4j/slf4j-api/src/main/java"]),
    ("io/netty/", sorted(glob.glob("/tmp/tp-netty/*/src/main/java"))),
    ("org/spongepowered/", ["/tmp/mixin-src/src/main/java"]),
]

# Binaries with no fetchable truth (offline sandbox). Each pin was verified
# by hand against stable public API knowledge + mod usage; any deviation of
# the stub from the pin is an ERROR, and pin use always warns for visibility.
# Values: kind -> "CLASS"/"INTERFACE"; members as (name, is_static, desc).
PINNED = {
    # org.lwjgl.glfw.GLFW is a final class of public static final int key
    # codes. The mod only reads GLFW_KEY_* constants (compile-time inlined).
    "org/lwjgl/glfw/GLFW": {
        "kind": "CLASS",
        "methods": set(),
        "fields": "STATIC_INT_CONSTANTS",
    },
    # com.mojang.authlib.GameProfile is a class; mod calls getName() only.
    "com/mojang/authlib/GameProfile": {
        "kind": "CLASS",
        "methods": {("getId", False, "()Ljava/util/UUID;"),
                    ("getName", False, "()Ljava/lang/String;")},
        "fields": set(),
    },
    # org.joml.Quaternionf is a class; stub is an empty placeholder and the
    # mod never references it (kept so remap tooling sees a stable owner).
    "org/joml/Quaternionf": {
        "kind": "CLASS",
        "methods": set(),
        "fields": set(),
    },
    # net.fabricmc.api.ModInitializer: stable loader interface.
    "net/fabricmc/api/ModInitializer": {
        "kind": "INTERFACE",
        "methods": {("onInitialize", False, "()V")},
        "fields": set(),
    },
    # net.fabricmc.api.ClientModInitializer: stable loader interface.
    "net/fabricmc/api/ClientModInitializer": {
        "kind": "INTERFACE",
        "methods": {("onInitializeClient", False, "()V")},
        "fields": set(),
    },
    # net.fabricmc.fabric.api.event.Event: generic final class; the mod only
    # calls register(T) on Event fields (erased to (Object)V).
    "net/fabricmc/fabric/api/event/Event": {
        "kind": "CLASS",
        "methods": {("register", False, "(Ljava/lang/Object;)V")},
        "fields": set(),
    },
}

_findings = []
_counts = {}


def report(severity, code, message):
    _findings.append((severity, code, message))
    _counts[code] = _counts.get(code, 0) + 1


# Well-known JDK outer types with nested types (no JDK sources offline;
# needed so Map.Entry resolves to Map$Entry instead of Map/Entry).
JDK_SEED = {"java/util/Map", "java/lang/Thread", "java/lang/Character",
            "java/util/Optional", "java/util/concurrent/CompletableFuture"}

# Binary index shared by all indexed parses (phase 1 collects, phase 2 uses).
BINARY_INDEX = set(JDK_SEED)
_plain_cache = {}
_indexed_cache = {}


def parse_plain(path):
    """Parse without an index; harvest every binary into BINARY_INDEX."""
    if path not in _plain_cache:
        try:
            info = apiparser.parse_java(path)
        except Exception as exc:  # noqa: BLE001 — audit must not crash
            report("ERROR", "PARSE_FAIL", "%s: %s" % (path, exc))
            info = None
        _plain_cache[path] = info
        if info is not None:
            for t in info.get("types", []):
                BINARY_INDEX.add(t["binary"])
    return _plain_cache[path]


def parse_indexed(path):
    """Parse with the frozen binary index (correct $ resolution)."""
    if path not in _indexed_cache:
        try:
            _indexed_cache[path] = apiparser.parse_java(path, BINARY_INDEX)
        except Exception as exc:  # noqa: BLE001 — audit must not crash
            report("ERROR", "PARSE_FAIL", "%s: %s" % (path, exc))
            _indexed_cache[path] = None
    return _indexed_cache[path]


def _ident_char(c):
    return c.isalnum() or c == "_" or c == "$"


def scan_mod_usage(src_roots):
    """Map simple type name -> {new, extends, super_empty} over mod sources."""
    usage = {}
    for root in src_roots:
        for dirpath, _dn, files in os.walk(root):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                try:
                    with open(os.path.join(dirpath, fn), encoding="utf-8",
                              errors="replace") as fh:
                        text = fh.read()
                except OSError:
                    continue
                for keyword, key in (("new ", "new"), ("extends ", "extends")):
                    start = 0
                    while True:
                        i = text.find(keyword, start)
                        if i < 0:
                            break
                        start = i + 1
                        if i > 0 and _ident_char(text[i - 1]):
                            continue
                        j = i + len(keyword)
                        while j < len(text) and text[j] in " \t\n\r":
                            j += 1
                        k = j
                        while k < len(text) and _ident_char(text[k]):
                            k += 1
                        name = text[j:k]
                        if not name:
                            continue
                        if key == "new":
                            while k < len(text) and text[k] in " \t\n\r":
                                k += 1
                            if k >= len(text) or text[k] != "(":
                                continue
                            a = k + 1
                            while a < len(text) and text[a] in " \t\n\r":
                                a += 1
                            if a < len(text) and text[a] == ")":
                                usage.setdefault(name, {})["new_empty"] = True
                            else:
                                usage.setdefault(name, {})["new_args"] = True
                            continue
                        usage.setdefault(name, {})["extends"] = True
                start = 0
                while True:
                    i = text.find("super(", start)
                    if i < 0:
                        break
                    start = i + 1
                    j = i + len("super(")
                    while j < len(text) and text[j] in " \t\n\r":
                        j += 1
                    if j < len(text) and text[j] == ")":
                        usage.setdefault("", {})["super_empty_file"] = True
                        break
                if usage.get("", {}).pop("super_empty_file", False):
                    for kw in ("extends ",):
                        s2 = 0
                        while True:
                            i = text.find(kw, s2)
                            if i < 0:
                                break
                            s2 = i + 1
                            if i > 0 and _ident_char(text[i - 1]):
                                continue
                            j = i + len(kw)
                            while j < len(text) and text[j] in " \t\n\r":
                                j += 1
                            k = j
                            while k < len(text) and _ident_char(text[k]):
                                k += 1
                            if text[j:k]:
                                usage.setdefault(text[j:k], {})["super_empty"] = True
    usage.pop("", None)
    return usage


MOD_USAGE = {}


def default_ctor_verdict(binary):
    """Stub-only ()V ctor: real risk only if the mod references it."""
    simple = binary.split("$")[-1].split("/")[-1]
    u = MOD_USAGE.get(simple, {})
    if u.get("new_empty"):
        report("ERROR", "DEFAULT_CTOR_USED",
               "%s: mod calls new %s() but truth has no ()V "
               "(NoSuchMethodError when executed)" % (binary, simple))
    elif u.get("new_args"):
        report("INFO", "DEFAULT_CTOR",
               "%s: mod news %s with args (binds a real ctor); "
               "stub-only ()V unreferenced" % (binary, simple))
    elif u.get("extends"):
        if u.get("super_empty"):
            report("ERROR", "DEFAULT_CTOR_USED",
                   "%s: mod subclass calls super() but truth has no ()V"
                   % binary)
        else:
            report("WARN", "DEFAULT_CTOR_EXTENDS",
                   "%s: extended by mod; verify subclass super(...) binds "
                   "a real truth ctor" % binary)
    else:
        report("INFO", "DEFAULT_CTOR",
               "%s: stub-only ()V, unreferenced by mod sources" % binary)


def truth_path(outer_binary):
    """Map an outer binary name to a truth .java path, or None."""
    rel = outer_binary + ".java"
    for prefix, dirs in TRUTH_ROOTS:
        if not outer_binary.startswith(prefix):
            continue
        for d in dirs:
            p = os.path.join(d, rel)
            if os.path.isfile(p):
                return p
    return None


def find_type(info, binary):
    if info is None:
        return None
    for t in info.get("types", []):
        if t.get("binary") == binary:
            return t
    return None


def file_imports(path):
    """Map simple name -> binary for single-type imports of a file."""
    out = {}
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                s = line.strip()
                if not s.startswith("import ") or s.startswith("import static "):
                    continue
                dotted = s[len("import "):].strip().rstrip(";").strip()
                if dotted.endswith(".*") or "." not in dotted:
                    continue
                out[dotted.split(".")[-1]] = dotted.replace(".", "/")
    except OSError:
        pass
    return out


def resolve_parent(raw, owner_binary, owner_file):
    """Resolve a super/iface raw name to a binary with an existing truth file."""
    if not raw:
        return None
    raw = raw.strip()
    if "<" in raw:  # strip generics: Foo<Bar> -> Foo
        raw = raw[:raw.index("<")].strip()
    if " " in raw:  # annotations on use: @A Foo -> Foo
        raw = raw.split()[-1]
    candidates = []
    if "." in raw:
        candidates.append(raw.replace(".", "/"))
    else:
        pkg = owner_binary.rsplit("/", 1)[0] if "/" in owner_binary else ""
        if pkg:
            candidates.append(pkg + "/" + raw)
        candidates.append(file_imports(owner_file).get(raw, ""))
        candidates.append("java/lang/" + raw)
        # same-file nested: Owner$Inner
        candidates.append(owner_binary + "$" + raw)
    for cand in candidates:
        if cand and truth_path(cand.split("$")[0]):
            return cand
    return None


def truth_hierarchy(binary):
    """Yield (typeinfo, depth) for binary + its supertypes (BFS)."""
    seen = set()
    queue = [(binary, 0)]
    while queue:
        b, depth = queue.pop(0)
        if b in seen:
            continue
        seen.add(b)
        outer = b.split("$")[0]
        path = truth_path(outer)
        if path is None:
            continue
        ti = find_type(parse_indexed(path), b)
        if ti is None:
            continue
        yield ti, depth
        parents = []
        if ti.get("super"):
            parents.append(ti["super"])
        elif ti.get("super_raw"):
            r = resolve_parent(ti["super_raw"], b, path)
            if r:
                parents.append(r)
        for iface in ti.get("ifaces") or []:
            parents.append(iface)
        if not ti.get("ifaces"):
            for raw in ti.get("ifaces_raw") or []:
                r = resolve_parent(raw, b, path)
                if r:
                    parents.append(r)
        for p in parents:
            if p not in ("java/lang/Object", "java/lang/Enum",
                         "java/lang/Record") and p not in seen:
                queue.append((p, depth + 1))


def norm_kind(ti):
    # apiparser: enum/record -> CLASS, annotation -> INTERFACE. Bytecode
    # kind is exactly that split.
    return ti.get("kind", "CLASS")


def check_type(stub_ti):
    binary = stub_ti["binary"]
    skind = norm_kind(stub_ti)
    if binary in PINNED:
        pin = PINNED[binary]
        report("WARN", "PINNED_NO_TRUTH",
               "%s: no truth sources offline; checked against hand pin" % binary)
        if skind != pin["kind"]:
            report("ERROR", "PIN_DEVIATION",
                   "%s: stub kind %s != pinned %s" % (binary, skind, pin["kind"]))
        for (name, static, desc, _line) in stub_ti["methods"]:
            if (name, static, desc) not in pin["methods"]:
                report("ERROR", "PIN_DEVIATION",
                       "%s: member %s %s %s not in pin"
                       % (binary, name, "static" if static else "inst", desc))
        if pin["fields"] == "STATIC_INT_CONSTANTS":
            for (name, static, desc, _line) in stub_ti["fields"]:
                if not static or desc != "I":
                    report("ERROR", "PIN_DEVIATION",
                           "%s: field %s must be static int" % (binary, name))
        else:
            for (name, static, desc, _line) in stub_ti["fields"]:
                if (name, static, desc) not in pin["fields"]:
                    report("ERROR", "PIN_DEVIATION",
                           "%s: field %s not in pin" % (binary, name))
        return skind
    chain = list(truth_hierarchy(binary))
    if not chain:
        report("ERROR", "NO_TRUTH",
               "%s: no truth source found (phantom API?)" % binary)
        return skind
    truth_ti = chain[0][0]
    tkind = norm_kind(truth_ti)
    if skind != tkind:
        report("ERROR", "KIND",
               "%s: stub %s but truth %s (%s)"
               % (binary, skind, tkind, truth_ti.get("file")))
    else:
        # abstract-ness: stub-concrete/truth-abstract lets `new` compile
        # then fail at runtime; the reverse is compile-safe.
        if truth_ti.get("abstract") and not stub_ti.get("abstract"):
            report("WARN", "ABSTRACT",
                   "%s: truth abstract, stub concrete (`new` compiles, "
                   "fails at runtime)" % binary)
    # superclass / interfaces (resolution-order info, not linkage-critical
    # unless the mod extends the type — reported, never fatal).
    if (stub_ti.get("super") or None) != (truth_ti.get("super") or None):
        report("WARN", "EXTENDS",
               "%s: stub super %s vs truth super %s"
               % (binary, stub_ti.get("super"), truth_ti.get("super")))
    if set(stub_ti.get("ifaces") or []) != set(truth_ti.get("ifaces") or []):
        report("WARN", "EXTENDS",
               "%s: stub ifaces %s vs truth ifaces %s"
               % (binary, stub_ti.get("ifaces"), truth_ti.get("ifaces")))
    for (name, static, desc, _line) in stub_ti["methods"]:
        check_member(binary, "METHOD", name, static, desc, chain)
    for (name, static, desc, _line) in stub_ti["fields"]:
        if skind == "INTERFACE":
            static = True
        check_member(binary, "FIELD", name, static, desc, chain)
    return skind


def check_member(binary, what, name, static, desc, chain):
    if what == "FIELD":
        def members_of(ti):
            return [(n, s if norm_kind(ti) != "INTERFACE" else True, d)
                    for (n, s, d, _l) in ti["fields"]]
    else:
        def members_of(ti):
            return [(n, s, d) for (n, s, d, _l) in ti["methods"]]
    same_name = []  # (owner_binary, depth, is_static, desc)
    for ti, depth in chain:
        for (n, s, d) in members_of(ti):
            if n == name:
                same_name.append((ti["binary"], depth, s, d))
    if not same_name:
        if what == "METHOD" and name == "<init>" and desc == "()V":
            default_ctor_verdict(binary)
            return
        report("ERROR", "PHANTOM",
               "%s: %s %s %s %s has no truth in hierarchy"
               % (binary, what, name, "static" if static else "inst", desc))
        return
    exact = [e for e in same_name if e[2] == static and e[3] == desc]
    if exact and exact[0][1] == 0:
        return  # declared on the type itself: perfect
    if exact:
        report("INFO", "OWNER_SHIFT",
               "%s: %s %s %s found on %s (inherited; legal)"
               % (binary, what, name, desc, exact[0][0]))
        return
    stat_match = [e for e in same_name if e[2] == static]
    if not stat_match:
        report("ERROR", "STATIC",
               "%s: %s %s stub %s but truth %s (descs %s)"
               % (binary, what, name,
                  "static" if static else "instance",
                  "static" if same_name[0][2] else "instance",
                  ",".join(sorted({e[3] for e in same_name}))))
        return
    if what == "METHOD" and name == "<init>" and desc == "()V":
        default_ctor_verdict(binary)
        return
    report("ERROR", "DESC",
           "%s: %s %s stub desc %s vs truth %s"
           % (binary, what, name, desc,
              ",".join(sorted({e[3] for e in stat_match}))))


def main():
    emit = None
    args = sys.argv[1:]
    if "--emit-kinds" in args:
        i = args.index("--emit-kinds")
        emit = args[i + 1] if i + 1 < len(args) else None
    stub_files = sorted(glob.glob(os.path.join(STUBS_DIR, "**", "*.java"),
                                  recursive=True))
    if not stub_files:
        print("no stubs under %s" % STUBS_DIR)
        return 1
    global MOD_USAGE
    MOD_USAGE = scan_mod_usage([os.path.join(HERE, "..", "src", "main", "java"),
                                os.path.join(HERE, "..", "src", "test", "java")])
    # Phase 1: plain parses harvest every binary (stubs + reachable truth
    # hierarchy closure) so phase 2 resolves Outer$Inner correctly.
    stub_binaries = []
    for path in stub_files:
        info = parse_plain(path)
        if info is None:
            continue
        for ti in info.get("types", []):
            stub_binaries.append(ti["binary"])
    to_visit = list(stub_binaries)
    visited = set()
    while to_visit:
        b = to_visit.pop()
        if b in visited or b in PINNED:
            continue
        visited.add(b)
        path = truth_path(b.split("$")[0])
        if path is None:
            continue
        ti = find_type(parse_plain(path), b)
        if ti is None:
            continue
        raws = ([ti.get("super_raw")] if ti.get("super_raw") else []) \
            + list(ti.get("ifaces_raw") or [])
        for raw in raws:
            r = resolve_parent(raw, b, path)
            if r and r not in visited:
                to_visit.append(r)
    # Phase 2: indexed parses + checks.
    kinds = {}
    n_types = 0
    for path in stub_files:
        info = parse_indexed(path)
        if info is None:
            continue
        for ti in info.get("types", []):
            n_types += 1
            kinds[ti["binary"]] = (norm_kind(ti), bool(ti.get("abstract")))
            check_type(ti)
    order = {"ERROR": 0, "WARN": 1, "INFO": 2}
    for sev, code, msg in sorted(_findings, key=lambda f: (order[f[0]], f[1], f[2])):
        print("[%s/%s] %s" % (sev, code, msg))
    n_err = sum(1 for f in _findings if f[0] == "ERROR")
    n_warn = sum(1 for f in _findings if f[0] == "WARN")
    print("audited %d types in %d stub files: %d ERROR, %d WARN"
          % (n_types, len(stub_files), n_err, n_warn))
    if emit:
        with open(emit, "w", encoding="utf-8") as fh:
            fh.write("# stub kind inventory (emitted by audit_stubs.py)\n")
            fh.write("# format: CLASS|INTERFACE <binary> [ABSTRACT]\n")
            for binary in sorted(kinds):
                kind, abstract = kinds[binary]
                fh.write("%s %s%s\n"
                         % (kind, binary, " ABSTRACT" if abstract else ""))
        print("wrote %s (%d types)" % (emit, len(kinds)))
    return 1 if n_err else 0


if __name__ == "__main__":
    sys.exit(main())
