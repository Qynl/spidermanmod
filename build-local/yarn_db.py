#!/usr/bin/env python3
"""Parse Yarn Enigma .mapping files (or a merged tiny v2 file) into a queryable DB.

Produces mappings in both directions:
  classes:  yarn_binary_name <-> intermediary_binary_name
  methods:  (owner_yarn, name_yarn, desc_yarn) <-> (owner_inter, name_inter, desc_inter)
  fields:   same shape as methods

Sources (first available wins, later ones fill gaps):
  1. build-local/yarn-1.21.1.tiny  (complete, incl. auto-mapped constants; vendored via CI)
  2. $YARN_DIR/mappings (default /tmp/yarn1211/mappings) Enigma files (methods/fields, no constants)

Usage:
  python3 yarn_db.py build            # build cache -> /tmp/yarn_db_cache.json
  python3 yarn_db.py find <ClassName> # show class + members (fuzzy)
  python3 yarn_db.py m <owner> <name> # show method overloads
  python3 yarn_db.py f <owner> <name> # show field
"""
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TINY_PATH = os.path.join(REPO_ROOT, "build-local", "yarn-1.21.1.tiny")
CACHE_PATH = os.environ.get("YARN_CACHE", "/tmp/yarn_db_cache.json")
YARN_DIR = os.environ.get("YARN_DIR", "/tmp/yarn1211")


def _parse_enigma_file(path, classes, methods, fields):
    """Parse one Enigma .mapping file. Appends into dicts."""
    stack = []  # (depth, inter_full, yarn_full_or_None)
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if not line.strip():
                continue
            depth = 0
            while line.startswith("\t"):
                depth += 1
                line = line[1:]
            parts = line.split(" ")
            kind = parts[0]
            if kind == "CLASS":
                while stack and stack[-1][0] >= depth:
                    stack.pop()
                inter_short = parts[1]
                yarn_short = parts[2] if len(parts) > 2 else None
                if stack:
                    p_inter, p_yarn = stack[-1][1], stack[-1][2]
                    inter_full = p_inter + "$" + inter_short.split("/")[-1]
                    if yarn_short and "/" in yarn_short:
                        yarn_full = yarn_short
                    elif yarn_short and p_yarn:
                        yarn_full = p_yarn + "$" + yarn_short
                    else:
                        yarn_full = None
                else:
                    inter_full = inter_short
                    yarn_full = yarn_short
                if yarn_full is None:
                    yarn_full = inter_full
                stack.append((depth, inter_full, yarn_full))
                classes[yarn_full] = inter_full
            elif kind in ("METHOD", "FIELD") and stack:
                inter_name = parts[1]
                # Enigma: METHOD <obf> [<deobf>] <desc>  /  FIELD <obf> [<deobf>] <desc>
                if len(parts) > 3:
                    yarn_name, desc = parts[2], parts[3]
                elif len(parts) > 2:
                    yarn_name, desc = inter_name, parts[2]
                else:
                    continue
                owner_inter, owner_yarn = stack[-1][1], stack[-1][2]
                if yarn_name is None:
                    yarn_name = inter_name
                key = (owner_yarn, kind, inter_name, desc)
                if seen is not None and key in seen:
                    continue
                if seen is not None:
                    seen.add(key)
                target = methods if kind == "METHOD" else fields
                target.setdefault(owner_yarn, []).append(
                    {"n": yarn_name, "i": inter_name, "d": desc, "oi": owner_inter}
                )
            # ARG / COMMENT ignored


def _translate_desc(desc, classmap_y2i):
    """Translate a descriptor's class refs using yarn->inter class map (for enigma descs
    this is a no-op lookup since enigma descs are already intermediary; used for tiny)."""
    def repl(m):
        name = m.group(1)
        return "L" + classmap_y2i.get(name, name) + ";"
    return re.sub(r"L([^;]+);", repl, desc)


def _parse_tiny(path, classes, methods, fields):
    with open(path, encoding="utf-8", errors="replace") as f:
        header = f.readline().strip().split("\t")
        assert header[0] == "tiny" and header[1] == "2", f"bad tiny header: {header}"
        # namespaces: header[3], header[4] -> e.g. intermediary, named
        cur_owner_i = None
        cur_owner_n = None
        for raw in f:
            line = raw.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            if line.startswith("c\t"):
                p = line.split("\t")
                cur_owner_i, cur_owner_n = p[1], p[2] if len(p) > 2 else p[1]
                if cur_owner_n not in classes:
                    classes[cur_owner_n] = cur_owner_i
            elif line.startswith("\tm\t") and cur_owner_n:
                # member lines have a LEADING tab: ['', 'm', desc, inter, named?]
                p = line.split("\t")
                desc, inter_name = p[2], p[3]
                yarn_name = p[4] if len(p) > 4 else inter_name
                methods.setdefault(cur_owner_n, []).append(
                    {"n": yarn_name, "i": inter_name, "d": desc, "oi": cur_owner_i}
                )
            elif line.startswith("\tf\t") and cur_owner_n:
                p = line.split("\t")
                desc, inter_name = p[2], p[3]
                yarn_name = p[4] if len(p) > 4 else inter_name
                fields.setdefault(cur_owner_n, []).append(
                    {"n": yarn_name, "i": inter_name, "d": desc, "oi": cur_owner_i}
                )
            # params/comments ignored


def build():
    classes, methods, fields = {}, {}, {}
    if os.path.isfile(TINY_PATH):
        print(f"[yarn_db] using complete tiny file: {TINY_PATH}", file=sys.stderr)
        _parse_tiny(TINY_PATH, classes, methods, fields)
    else:
        print("[yarn_db] tiny not vendored yet; CI fetch-deps will provide it.", file=sys.stderr)
    have_tiny = bool(classes)
    mdir = os.path.join(YARN_DIR, "mappings")
    if have_tiny:
        print("[yarn_db] tiny is complete; skipping enigma merge", file=sys.stderr)
    if os.path.isdir(mdir) and not have_tiny:
        print(f"[yarn_db] merging enigma files from: {mdir}", file=sys.stderr)
        n = 0
        for root, _ds, files in os.walk(mdir):
            for fn in files:
                if fn.endswith(".mapping"):
                    _parse_enigma_file(os.path.join(root, fn), classes, methods, fields)
                    n += 1
        print(f"[yarn_db] parsed {n} enigma files", file=sys.stderr)
    else:
        print(f"[yarn_db] WARNING: {mdir} missing", file=sys.stderr)
    # inter -> yarn class map
    i2n = {v: k for k, v in classes.items()}
    # translate enigma (intermediary) descriptors to named where possible
    for bucket in (methods, fields):
        for owner, members in bucket.items():
            for m in members:
                d = m["d"]
                if ("Lnet/minecraft/class_" in d or "Lnet/minecraft/" in d) and "Lnet/minecraft/class_" in d:
                    m["dn"] = _translate_desc(d, i2n)
                else:
                    m["dn"] = d
    db = {"classes": classes, "i2n": i2n, "methods": methods, "fields": fields}
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(db, f)
    print(f"[yarn_db] classes={len(classes)} methodOwners={len(methods)} fieldOwners={len(fields)} -> {CACHE_PATH}",
          file=sys.stderr)
    return db


def load():
    if not os.path.isfile(CACHE_PATH):
        return build()
    with open(CACHE_PATH, encoding="utf-8") as f:
        return json.load(f)


def _norm_owner(q):
    return q.replace(".", "/")


def cmd_find(db, q):
    qn = _norm_owner(q)
    hits = [c for c in db["classes"] if c.endswith(qn) or qn in c]
    if not hits:
        print(f"no class matching {q!r}")
        return
    for c in sorted(hits)[:8]:
        print(f"CLASS {c}  <->  {db['classes'][c]}")
        for m in db["methods"].get(c, [])[:60]:
            print(f"   M {m['n']} {m.get('dn', m['d'])}   [inter: {m['i']}]")
        for m in db["fields"].get(c, [])[:60]:
            print(f"   F {m['n']} {m.get('dn', m['d'])}   [inter: {m['i']}]")


def cmd_member(db, bucket, owner_q, name):
    oqn = _norm_owner(owner_q)
    owners = [c for c in db[bucket] if c.endswith(oqn) or c == oqn]
    if not owners:
        # try class search then members
        cand = [c for c in db["classes"] if c.endswith(oqn)]
        print(f"no {bucket} for owner {owner_q!r}; class candidates: {cand[:5]}")
        return
    for o in sorted(owners)[:5]:
        print(f"== {o}  (inter {db['classes'].get(o, '?')})")
        found = False
        for m in db[bucket].get(o, []):
            if m["n"] == name:
                print(f"   {m['n']} {m.get('dn', m['d'])}   [inter: {m['i']}]")
                found = True
        if not found:
            print(f"   (no member named {name!r}; total {len(db[bucket].get(o, []))})")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "build":
        build()
    elif len(sys.argv) >= 3 and sys.argv[1] == "find":
        cmd_find(load(), sys.argv[2])
    elif len(sys.argv) >= 4 and sys.argv[1] in ("m", "f"):
        cmd_member(load(), "methods" if sys.argv[1] == "m" else "fields", sys.argv[2], sys.argv[3])
    else:
        print(__doc__)
