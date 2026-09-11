#!/usr/bin/env python3
"""Generates spiderman-refmap.json for the mixins.

Mirrors what the Mixin annotation processor writes (and what the Mixin
runtime ReferenceMapper reads):

  {"mappings": {<mixin classRef>: {<raw annotation string>: <remapped MemberInfo>}}}

The key is the mixin class internal name (slashes) and the EXACT target
string from the @Inject(method = "...") annotation. The value is the
MemberInfo form ``L<owner>;<name><desc>`` in intermediary names, which the
runtime parses back into owner/name/desc.

A loom-built jar gets this file from the Mixin AP at compile time; this
offline build generates the identical schema from the vendored yarn tiny
file. The mapping is also duplicated under "data" for the obfuscation
contexts a runtime might select ("searge" default, "named:intermediary"
loom-style); content is identical so any context resolves the same.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from yarn_db import load  # noqa: E402

# (mixin classRef, annotation reference, kind, target owner yarn,
#  target name yarn, target desc yarn/named)
TARGETS = [
    ("com/spiderman/mod/mixin/EntityMixin", "handleFallDamage", "method",
     "net/minecraft/entity/Entity", "handleFallDamage",
     "(FFLnet/minecraft/entity/damage/DamageSource;)Z"),
    ("com/spiderman/mod/mixin/GameRendererMixin", "getFov", "method",
     "net/minecraft/client/render/GameRenderer", "getFov",
     "(Lnet/minecraft/client/render/Camera;FZ)D"),
    ("com/spiderman/mod/mixin/BipedEntityModelMixin", "positionRightArm", "method",
     "net/minecraft/client/render/entity/model/BipedEntityModel", "positionRightArm",
     "(Lnet/minecraft/entity/LivingEntity;)V"),
    ("com/spiderman/mod/mixin/BipedEntityModelMixin", "positionLeftArm", "method",
     "net/minecraft/client/render/entity/model/BipedEntityModel", "positionLeftArm",
     "(Lnet/minecraft/entity/LivingEntity;)V"),
    ("com/spiderman/mod/mixin/HeldItemRendererMixin", "renderArm", "method",
     "net/minecraft/client/render/item/HeldItemRenderer", "renderArm",
     "(Lnet/minecraft/client/util/math/MatrixStack;"
     "Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Arm;)V"),
]

# Context copies of the default mapping set (identical content; the runtime
# falls back to top-level "mappings" when its context key is absent).
CONTEXTS = ("searge", "named:intermediary")


def main():
    db = load()
    mappings = {}
    for mixin, reference, kind, owner, name, desc in TARGETS:
        bucket = db["fields"] if kind == "field" else db["methods"]
        cands = [m for m in bucket.get(owner, [])
                 if m["n"] == name and m.get("dn", m["d"]) == desc]
        if len(cands) != 1:
            raise SystemExit(
                f"[refmap] {len(cands)} candidates for {owner} {name} {desc}")
        hit = cands[0]
        if kind == "method":
            # MemberInfo.toString() form: Lowner;name + desc.
            value = f"L{hit['oi']};{hit['i']}{hit['d']}"
        else:
            value = f"L{hit['oi']};{hit['i']}:{hit['d']}"
        per_mixin = mappings.setdefault(mixin, {})
        if reference in per_mixin and per_mixin[reference] != value:
            raise SystemExit(
                f"[refmap] conflicting values for {mixin} {reference!r}")
        per_mixin[reference] = value
    data = {ctx: {m: dict(refs) for m, refs in mappings.items()}
            for ctx in CONTEXTS}
    out = sys.argv[1]
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"mappings": mappings, "data": data}, f, indent=2)
    n = sum(len(v) for v in mappings.values())
    print(f"[refmap] wrote {n} mappings for {len(mappings)} mixins -> {out}")


if __name__ == "__main__":
    main()
