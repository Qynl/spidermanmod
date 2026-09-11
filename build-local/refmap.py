#!/usr/bin/env python3
"""Generates spiderman-refmap.json (named -> intermediary) for the mixins."""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from yarn_db import load  # noqa: E402

TARGETS = [
    ("method", "net/minecraft/entity/Entity", "handleFallDamage",
     "(FFLnet/minecraft/entity/damage/DamageSource;)Z"),
    ("method", "net/minecraft/client/render/GameRenderer", "getFov",
     "(Lnet/minecraft/client/render/Camera;FZ)D"),
    ("method", "net/minecraft/client/render/entity/model/BipedEntityModel", "positionRightArm",
     "(Lnet/minecraft/entity/LivingEntity;)V"),
    ("method", "net/minecraft/client/render/entity/model/BipedEntityModel", "positionLeftArm",
     "(Lnet/minecraft/entity/LivingEntity;)V"),
    ("field", "net/minecraft/client/render/entity/model/BipedEntityModel", "rightArm",
     "Lnet/minecraft/client/model/ModelPart;"),
    ("field", "net/minecraft/client/render/entity/model/BipedEntityModel", "leftArm",
     "Lnet/minecraft/client/model/ModelPart;"),
    ("method", "net/minecraft/client/render/item/HeldItemRenderer", "renderArm",
     "(Lnet/minecraft/client/util/math/MatrixStack;"
     "Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Arm;)V"),
]


def main():
    db = load()
    mappings = {}
    for kind, owner, name, desc in TARGETS:
        bucket = db["fields"] if kind == "field" else db["methods"]
        cands = [m for m in bucket.get(owner, [])
                 if m["n"] == name and m.get("dn", m["d"]) == desc]
        if len(cands) != 1:
            raise SystemExit(f"[refmap] {len(cands)} candidates for {owner} {name} {desc}")
        if kind == "method":
            mappings[f"{owner}.{name}{desc}"] = cands[0]["i"]
        else:
            mappings[f"{owner}.{name}:{desc}"] = cands[0]["i"]
    for owner in sorted({t[1] for t in TARGETS}):
        mappings[owner] = db["classes"][owner]
    out = sys.argv[1]
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"mappings": {"named:intermediary": mappings}}, f, indent=2)
    print(f"[refmap] wrote {len(mappings)} mappings -> {out}")


if __name__ == "__main__":
    main()
