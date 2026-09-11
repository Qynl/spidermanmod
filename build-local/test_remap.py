#!/usr/bin/env python3
"""Pure-python remap/linkage tests. No JDK, no Minecraft: safe anywhere.

Covers the whole inherited-member bug class without compiling anything:
pinned tiny mappings, resolve/decl_mapping/member_mapping units, an
end-to-end remap_class run on hand-crafted classfiles (including the Utf8
aliasing regression), and gate positive/negative checks.

Usage: python3 build-local/test_remap.py
"""

import contextlib
import io
import os
import struct
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remap import (Mapper, build_declmap, remap_class)  # noqa: E402
from verify import check_linkage  # noqa: E402

PASS = FAIL = 0


def check(cond, label):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("FAIL:", label)


def expect_raise(fn, label):
    global PASS, FAIL
    try:
        fn()
    except SystemExit:
        PASS += 1
        return
    FAIL += 1
    print("FAIL (no SystemExit):", label)


VEC = "Lnet/minecraft/util/math/Vec3d;"
ID = "Lnet/minecraft/util/Identifier;"

# (named owner, yarn name, named desc, expected intermediary name)
PINS = [
    ("net/minecraft/entity/mob/SpiderEntity", "createSpiderAttributes",
     "()Lnet/minecraft/entity/attribute/DefaultAttributeContainer$Builder;",
     "method_26923"),
    ("net/minecraft/entity/Entity", "setPos", "(DDD)V", "method_23327"),
    ("net/minecraft/entity/Entity", "setVelocity", "(DDD)V", "method_18800"),
    ("net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
     "method_37908"),
    ("net/minecraft/entity/Entity", "discard", "()V", "method_31472"),
    ("net/minecraft/entity/Entity", "getVelocity", "()" + VEC, "method_18798"),
    ("net/minecraft/entity/Entity", "setVelocity", "(" + VEC + ")V", "method_18799"),
    ("net/minecraft/entity/Entity", "getPos", "()" + VEC, "method_19538"),
    ("net/minecraft/entity/Entity", "initDataTracker",
     "(Lnet/minecraft/entity/data/DataTracker$Builder;)V", "method_5693"),
    ("net/minecraft/entity/Entity", "readCustomDataFromNbt",
     "(Lnet/minecraft/nbt/NbtCompound;)V", "method_5749"),
    ("net/minecraft/entity/Entity", "writeCustomDataToNbt",
     "(Lnet/minecraft/nbt/NbtCompound;)V", "method_5652"),
    ("net/minecraft/entity/Entity", "tick", "()V", "method_5773"),
    ("net/minecraft/entity/mob/MobEntity", "initGoals", "()V", "method_5959"),
    ("net/minecraft/entity/ai/goal/MeleeAttackGoal", "attack",
     "(Lnet/minecraft/entity/LivingEntity;)V", "method_6288"),
    ("net/minecraft/entity/projectile/ProjectileEntity", "onBlockHit",
     "(Lnet/minecraft/util/hit/BlockHitResult;)V", "method_24920"),
    ("net/minecraft/client/gui/screen/Screen", "close", "()V", "method_25419"),
    ("net/minecraft/client/gui/screen/Screen", "keyPressed", "(III)Z", "method_25404"),
    ("net/minecraft/client/gui/screen/Screen", "mouseClicked", "(DDI)Z", "method_25402"),
    ("net/minecraft/client/gui/screen/Screen", "render",
     "(Lnet/minecraft/client/gui/DrawContext;IIF)V", "method_25394"),
    ("net/minecraft/client/gui/screen/Screen", "shouldPause", "()Z", "method_25421"),
    ("net/minecraft/client/render/entity/EntityRenderer", "getTexture",
     "(Lnet/minecraft/entity/Entity;)" + ID, "method_3931"),
    ("net/minecraft/client/render/entity/SpiderEntityRenderer", "getTexture",
     "(Lnet/minecraft/entity/mob/SpiderEntity;)" + ID, "method_4123"),
    ("net/minecraft/network/packet/CustomPayload", "getId",
     "()Lnet/minecraft/network/packet/CustomPayload$Id;", "method_56479"),
    ("net/minecraft/registry/entry/RegistryEntry", "value",
     "()Ljava/lang/Object;", "comp_349"),
    # Genuinely unmapped (n == i): keeps are correct, silently.
    ("net/minecraft/client/render/VertexConsumerProvider", "getBuffer",
     "(Lnet/minecraft/client/render/RenderLayer;)"
     "Lnet/minecraft/client/render/VertexConsumer;", "getBuffer"),
    ("net/minecraft/util/math/RotationAxis", "rotationDegrees",
     "(F)Lorg/joml/Quaternionf;", "rotationDegrees"),
]
FIELD_PINS = [
    ("net/minecraft/entity/mob/MobEntity", "goalSelector",
     "Lnet/minecraft/entity/ai/goal/GoalSelector;", "field_6201"),
    ("net/minecraft/entity/Entity", "age", "I", "field_6012"),
    ("net/minecraft/client/gui/screen/Screen", "width", "I", "field_22789"),
    ("net/minecraft/client/gui/screen/Screen", "height", "I", "field_22790"),
    ("net/minecraft/client/gui/screen/Screen", "textRenderer",
     "Lnet/minecraft/client/font/TextRenderer;", "field_22793"),
    ("net/minecraft/client/gui/screen/Screen", "client",
     "Lnet/minecraft/client/MinecraftClient;", "field_22787"),
]

SCREEN = "net/minecraft/client/gui/screen/Screen"


def blank(flags=1):
    return {"super": "java/lang/Object", "ifaces": [],
            "fields": {}, "methods": {}}


def main():
    mp = Mapper()

    # T1: pinned tiny mappings (name pinned; tiny desc must equal the
    # translated named desc, cross-checking the classes/methods tables).
    for o, n, d, want_i in PINS:
        got_i, got_d = mp.lookup(o, n, d, False)
        check(got_i == want_i, f"lookup {n} -> {got_i} (want {want_i})")
        check(got_d == mp.map_desc(d), f"tiny desc consistent for {n}")
    for o, n, d, want_i in FIELD_PINS:
        got_i, got_d = mp.lookup(o, n, d, True)
        check(got_i == want_i, f"field {n} -> {got_i} (want {want_i})")
        check(got_d == mp.map_desc(d), f"tiny field desc consistent for {n}")
    # Foreign-declared members are NOT in tiny (keeps, not mappings).
    check(mp._scan("net/minecraft/network/RegistryByteBuf", "readBoolean",
                   "()Z", False)[0] is None, "readBoolean not in tiny")
    check(mp._scan("net/minecraft/network/RegistryByteBuf", "readDouble",
                   "()D", False)[0] is None, "readDouble not in tiny")

    # T2: resolve / decl_mapping / member_mapping units on synthetic decls.
    decl = {
        "com/spiderman/mod/Foo": {
            "super": SCREEN, "ifaces": [], "fields": {},
            "methods": {(("close", "()V")): 1, (("helper", "()V")): 2}},
        "com/spiderman/mod/Bar": {
            "super": "com/spiderman/mod/Foo", "ifaces": [], "fields": {},
            "methods": {(("close", "()V")): 1}},
        "com/spiderman/mod/Rec": {
            "super": "java/lang/Record",
            "ifaces": ["net/minecraft/network/packet/CustomPayload"],
            "fields": {},
            "methods": {(("getId", "()Lnet/minecraft/network/packet/"
                                    "CustomPayload$Id;")): 1}},
    }
    mu = Mapper(decl)
    FOO, BAR, REC = ("com/spiderman/mod/Foo", "com/spiderman/mod/Bar",
                     "com/spiderman/mod/Rec")
    check(mu.resolve(FOO, "close", "()V", False) == ("mod", FOO, 1),
          "resolve own decl first")
    check(mu.resolve(FOO, "width", "I", True)
          == ("mc", "net/minecraft/client/gui/screen/Screen"),
          "resolve inherited field to MC")
    check(mu.resolve(FOO, "keyPressed", "(III)Z", False)
          == ("mc", "net/minecraft/client/gui/Element"),
          "resolve walks MC interfaces")
    check(mu.resolve(FOO, "close", "()V", False, exclude_self=True)
          == ("mc", SCREEN), "resolve above self reaches MC")
    check(mu.resolve(BAR, "close", "()V", False, exclude_self=True)
          == ("mod", FOO, 1), "resolve crosses mod supers")
    check(mu.resolve(REC, "getId", "()Lnet/minecraft/network/packet/"
                     "CustomPayload$Id;", False, exclude_self=True)
          == ("mc", "net/minecraft/network/packet/CustomPayload"),
          "resolve record iface decl")
    check(mu.resolve(FOO, "bogus", "()V", False) is None, "resolve miss is None")
    check(mu.resolve(FOO, "helper", "()V", False, exclude_self=True,
                     inheritable_only=True) is None,
          "private decl is not an override link")

    check(mu.decl_mapping(FOO, "close", "()V", False, 1) == ("method_25419", "()V"),
          "override def maps")
    check(mu.decl_mapping(BAR, "close", "()V", False, 1) == ("method_25419", "()V"),
          "override def maps through mod super")
    check(mu.decl_mapping(REC, "getId", "()Lnet/minecraft/network/packet/"
                          "CustomPayload$Id;", False, 1)[0] == "method_56479",
          "record getId maps")
    check(mu.decl_mapping(FOO, "helper", "()V", False, 2) == ("helper", "()V"),
          "private def keeps")
    check(mu.decl_mapping(FOO, "close", "()V", False, 0x0009)[0] == "close",
          "static def keeps")
    check(mu.decl_mapping(FOO, "close", "()V", False, 0)[0] == "close",
          "package-private def keeps")
    check(mu.decl_mapping(FOO, "unique", "()V", False, 1) == ("unique", "()V"),
          "unique def keeps")
    check(mu.decl_mapping(FOO, "width", "I", True, 4)[0] == "width",
          "field def keeps")

    check(mu.member_mapping(FOO, "close", "()V", False) == ("method_25419", "()V"),
          "self ref to override maps")
    check(mu.member_mapping(FOO, "width", "I", True) == ("field_22789", "I"),
          "self ref to inherited field maps")
    check(mu.member_mapping(BAR, "close", "()V", False) == ("method_25419", "()V"),
          "inherited override ref maps")
    check(mu.member_mapping(FOO, "toString", "()Ljava/lang/String;", False)
          == ("toString", "()Ljava/lang/String;"), "JDK keep passes")
    expect_raise(lambda: mu.member_mapping(FOO, "bogus", "()V", False),
                 "unresolvable mod-owner ref fails closed")
    check(mu.member_mapping(SCREEN, "close", "()V", False) == ("method_25419", "()V"),
          "mc-owner ref maps")
    check(mu.member_mapping("net/minecraft/client/MinecraftClient", "execute",
                            "(Ljava/lang/Runnable;)V", False)
          == ("execute", "(Ljava/lang/Runnable;)V"), "execute keep passes")
    check(mu.member_mapping("net/minecraft/network/RegistryByteBuf", "readBoolean",
                            "()Z", False) == ("readBoolean", "()Z"),
          "netty keep passes")
    expect_raise(lambda: mu.member_mapping(SCREEN, "bogus", "()V", False),
                 "unmapped mc-owner ref fails closed")
    check(mu.member_mapping("net/fabricmc/fabric/api/Foo", "bar", "()V", False)
          == ("bar", "()V"), "fabric owner keeps")
    check(mu.member_mapping(FOO, "<init>", "()V", False) == ("<init>", "()V"),
          "ctor keeps")

    check(mu.map_class("[[Lnet/minecraft/entity/Entity;")
          == "[[Lnet/minecraft/class_1297;", "multi-dim array maps")
    check(mu.map_class("[Lnet/minecraft/entity/Entity;")
          == "[Lnet/minecraft/class_1297;", "array maps")
    check(mu.map_class("[Ljava/lang/String;") == "[Ljava/lang/String;",
          "JDK array untouched")
    expect_raise(lambda: mu.map_class("net/minecraft/nope/Missing"),
                 "missing class fails closed")

    # T3: end-to-end remap_class on hand-crafted classfiles.
    e2e()

    print(f"[test_remap] {PASS} passed, {FAIL} failed")
    return 1 if FAIL else 0


class CB:
    """Minimal .class builder: pool + header + decls, no method bodies."""

    def __init__(self):
        self.cp = [None]
        self.utf_cache = {}
        self.cls_cache = {}
        self.nat_cache = {}

    def utf(self, s):
        if s not in self.utf_cache:
            self.cp.append(("utf", s))
            self.utf_cache[s] = len(self.cp) - 1
        return self.utf_cache[s]

    def cls(self, path):
        if path not in self.cls_cache:
            self.cp.append(("class", self.utf(path)))
            self.cls_cache[path] = len(self.cp) - 1
        return self.cls_cache[path]

    def nat(self, n, d):
        if (n, d) not in self.nat_cache:
            self.cp.append(("nat", self.utf(n), self.utf(d)))
            self.nat_cache[(n, d)] = len(self.cp) - 1
        return self.nat_cache[(n, d)]

    def mref(self, tag, owner, n, d):
        self.cp.append(("mref", tag, self.cls(owner), self.nat(n, d)))
        return len(self.cp) - 1

    def build(self, this, super, ifaces, fields, methods):
        this_i = self.cls(this)
        sup_i = self.cls(super) if super else 0
        iface_is = [self.cls(x) for x in ifaces]
        for _f, n, d in fields + methods:
            self.utf(n)
            self.utf(d)
        out = bytearray(b"\xca\xfe\xba\xbe\x00\x00\x00\x34")
        out += struct.pack(">H", len(self.cp))
        for e in self.cp[1:]:
            if e[0] == "utf":
                raw = e[1].encode("utf-8")
                out += b"\x01" + struct.pack(">H", len(raw)) + raw
            elif e[0] == "class":
                out += b"\x07" + struct.pack(">H", e[1])
            elif e[0] == "nat":
                out += b"\x0c" + struct.pack(">HH", e[1], e[2])
            else:
                out += bytes([e[1]]) + struct.pack(">HH", e[2], e[3])
        out += struct.pack(">HHHH", 0x0021, this_i, sup_i, len(iface_is))
        for i in iface_is:
            out += struct.pack(">H", i)
        for decls in (fields, methods):
            out += struct.pack(">H", len(decls))
            for (flags, n, d) in decls:
                out += struct.pack(">HHHH", flags, self.utf(n), self.utf(d), 0)
        out += struct.pack(">H", 0)
        return bytes(out)


def pool_view(data):
    """{refs: [(tag, owner, name, desc)], defs: {kind: [(flags, name, desc)}},
    classes: [paths]} from classfile bytes."""
    from remap import ClassFile, member_decl_positions
    cf = ClassFile(data)
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
    defs = {"field": [], "method": []}
    for kind, _p, flags, ni, di in member_decl_positions(cf.tail):
        defs[kind].append((flags, cf.utf(ni), cf.utf(di)))
    return {"refs": refs, "defs": defs, "classes": sorted(set(cls_utf.values()))}


FOO = "com/spiderman/mod/Foo"
BAR = "com/spiderman/mod/Bar"
MIXIN = "com/spiderman/mod/mixin/BipedEntityModelMixin"
MODELPART = "Lnet/minecraft/client/model/ModelPart;"


def e2e():
    foo = CB()
    # R1 (mod owner) and R2 (MC owner) share NAT (close, ()V), like javac emits.
    foo.mref(10, FOO, "close", "()V")
    foo.mref(10, SCREEN, "close", "()V")
    foo.mref(9, FOO, "width", "I")
    foo.mref(10, FOO, "toString", "()Ljava/lang/String;")
    foo.mref(10, FOO, "unique", "(I)I")
    foo.mref(10, "net/minecraft/client/MinecraftClient", "execute",
             "(Ljava/lang/Runnable;)V")
    foo_bytes = foo.build(FOO, SCREEN, [],
                          [(0x0019, "CONST", "I")],
                          [(1, "<init>", "()V"),
                           (1, "close", "()V"),      # override -> method_25419
                           (2, "close", "(I)V"),     # private: keeps (aliasing!)
                           (1, "unique", "(I)I")])
    bar = CB()
    bar.mref(10, FOO, "unique", "(I)I")
    bar.mref(10, FOO, "close", "()V")
    bar_bytes = bar.build(BAR, "java/lang/Object", [], [],
                          [(1, "<init>", "()V")])
    mix = CB()
    mix.mref(9, MIXIN, "rightArm", MODELPART)
    mix.mref(9, MIXIN, "leftArm", MODELPART)
    mix_bytes = mix.build(MIXIN, "java/lang/Object", [], [
        (1, "rightArm", MODELPART), (1, "leftArm", MODELPART)],
        [(1, "<init>", "()V")])

    with tempfile.TemporaryDirectory() as tmp:
        for rel, data in (("com/spiderman/mod/Foo.class", foo_bytes),
                          ("com/spiderman/mod/Bar.class", bar_bytes),
                          ("com/spiderman/mod/mixin/BipedEntityModelMixin.class",
                           mix_bytes)):
            p = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(data)
        dm = build_declmap(tmp)
        check(dm[FOO]["super"] == SCREEN, "declmap super")
        check(dm[FOO]["methods"][(("close", "()V"))] == 1, "declmap flags")
        check(dm[FOO]["fields"][(("CONST", "I"))] == 0x0019, "declmap field")
        m2 = Mapper(dm)
        out_foo = remap_class(foo_bytes, m2, "com/spiderman/mod/Foo.class")
        out_bar = remap_class(bar_bytes, m2, "com/spiderman/mod/Bar.class")
        out_mix = remap_class(
            mix_bytes, m2, "com/spiderman/mod/mixin/BipedEntityModelMixin.class")

    vf = pool_view(out_foo)
    refs = {(t, o, n, d) for (t, o, n, d) in vf["refs"]}
    check((10, FOO, "method_25419", "()V") in refs, "shared-NAT mod ref maps")
    check((10, "net/minecraft/class_437", "method_25419", "()V") in refs,
          "shared-NAT mc ref maps")
    check((9, FOO, "field_22789", "I") in refs, "inherited field ref maps")
    check((10, FOO, "toString", "()Ljava/lang/String;") in refs, "JDK ref keeps")
    check((10, FOO, "unique", "(I)I") in refs, "unique ref keeps")
    check((10, "net/minecraft/class_310", "execute", "(Ljava/lang/Runnable;)V")
          in refs, "execute keeps with mapped owner")
    check("net/minecraft/class_437" in vf["classes"], "super class entry maps")
    check(not any("close" == n and d == "()V" for (_t, _o, n, d) in vf["refs"]),
          "no yarn close ref survives")
    mdefs = {(n, d): f for (f, n, d) in vf["defs"]["method"]}
    check(mdefs.get(("method_25419", "()V")) == 1, "override def renames")
    check(mdefs.get(("close", "(I)V")) == 2,
          "private same-name def keeps (no aliasing)")
    check(mdefs.get(("unique", "(I)I")) == 1, "unique def keeps")
    check(mdefs.get(("<init>", "()V")) == 1, "ctor def keeps")
    check(("CONST", "I") in {(n, d) for (_f, n, d) in vf["defs"]["field"]},
          "field def keeps")
    check(m2.def_renamed == 1, f"exactly one def renamed ({m2.def_renamed})")

    vb = pool_view(out_bar)
    brefs = {(t, o, n, d) for (t, o, n, d) in vb["refs"]}
    check((10, FOO, "unique", "(I)I") in brefs, "cross-class unique ref keeps")
    check((10, FOO, "method_25419", "()V") in brefs,
          "cross-class override ref maps")

    vm = pool_view(out_mix)
    mrefs = {(o, n, d) for (_t, o, n, d) in vm["refs"]}
    mdefs = {(n, d) for (_f, n, d) in vm["defs"]["field"]}
    check((MIXIN, "field_3401", "Lnet/minecraft/class_630;") in mrefs,
          "shadow usage renames")
    check((MIXIN, "field_27433", "Lnet/minecraft/class_630;") in mrefs,
          "shadow usage renames (leftArm)")
    check(("field_3401", "Lnet/minecraft/class_630;") in mdefs,
          "shadow def renames")
    check(("field_27433", "Lnet/minecraft/class_630;") in mdefs,
          "shadow def renames (leftArm)")
    check(len(m2.shadow_applied) == 2,
          f"both shadows applied ({len(m2.shadow_applied)})")

    # T4: the gate passes the remapped output.
    with tempfile.TemporaryDirectory() as tmp:
        for rel, data in (("com/spiderman/mod/Foo.class", out_foo),
                          ("com/spiderman/mod/Bar.class", out_bar),
                          ("com/spiderman/mod/mixin/BipedEntityModelMixin.class",
                           out_mix)):
            p = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(data)
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                check_linkage(tmp, Mapper())
                gated = True
            except SystemExit:
                gated = False
        check(gated, "gate passes remapped output")
        check("linkage OK" in buf.getvalue(), "gate prints OK summary")

    # T5: the gate fails crafted-broken input (ref + missed override).
    bro = CB()
    bro.mref(10, "com/spiderman/mod/Broken", "getWorld",
             "()Lnet/minecraft/class_1937;")
    bro.mref(10, "com/spiderman/mod/Broken", "close", "()V")
    bro_bytes = bro.build("com/spiderman/mod/Broken",
                          "net/minecraft/class_437", [], [],
                          [(1, "close", "()V")])
    mix2 = CB()
    mix2.mref(9, MIXIN, "field_3401", "Lnet/minecraft/class_630;")
    mix2.mref(9, MIXIN, "field_27433", "Lnet/minecraft/class_630;")
    mix2_bytes = mix2.build(MIXIN, "java/lang/Object", [], [
        (1, "field_3401", "Lnet/minecraft/class_630;"),
        (1, "field_27433", "Lnet/minecraft/class_630;")],
        [(1, "<init>", "()V")])
    with tempfile.TemporaryDirectory() as tmp:
        for rel, data in (("com/spiderman/mod/Broken.class", bro_bytes),
                          ("com/spiderman/mod/mixin/BipedEntityModelMixin.class",
                           mix2_bytes)):
            p = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(data)
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                check_linkage(tmp, Mapper())
                gated = True
            except SystemExit as e:
                gated = False
                msg = str(e)
        out = buf.getvalue()
        check(not gated, "gate fails broken input")
        check("mod-owner ref has no target" in out, "gate names the bad ref")
        check("MISSED OVERRIDE" in out, "gate names the missed override")
        check("2 linkage error(s)" in msg, f"gate counts both ({msg})")


if __name__ == "__main__":
    sys.exit(main())
