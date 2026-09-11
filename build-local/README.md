# Local (offline) build toolchain

This directory contains a from-scratch reimplementation of what Fabric Loom does,
so the mod can be compiled, remapped and packaged **without network access**:

```
src/main/java --(1)--> build-local/classes-named --(2)--> build-local/classes --(3)--> spiderman-1.0.0.jar
```

1. **Compile** `javac` against generated API stubs (`stubgen.py` turns the
   hand-maintained Yarn surface in `api-surface.txt` + `handstubs.py` into
   compilable `.java` stubs under `gen-stubs/`).
2. **Remap** Yarn names to Intermediary (`remap.py`) using the vendored
   CC0 `yarn-1.21.1.tiny` mappings — the exact same mapping Loom applies.
   A mini class-hierarchy walker resolves inherited members; members that
   genuinely come from outside Mojang code (Netty `ByteBuf`, JDK) keep
   their names, exactly as production Loom output does.
3. **Package** classes + resources, generate the mixin refmap (`refmap.py`),
   expand `${version}` in `fabric.mod.json`, and run the jar integrity
   checks (`verify.py`).

## Scripts

| File | Purpose |
| --- | --- |
| `build.sh` | Full pipeline: stubs -> compile -> remap -> refmap -> jar -> verify |
| `test.sh` | Compiles + runs `src/test/java` pure-logic unit tests (no JUnit needed) |
| `stubgen.py` | Generates compilable stubs from `api-surface.txt` + `handstubs.py` |
| `handstubs.py` | Hand-written stubs for generic/complex vanilla + Fabric types |
| `api-surface.txt` | Declared vanilla/Fabric API surface (930 entries) the mod may touch |
| `yarn_db.py` | Tiny-mappings query helper used by the remapper |
| `remap.py` | Bytecode remapper: Yarn -> Intermediary with hierarchy walking |
| `refmap.py` | Generates `spiderman-refmap.json` for the mixins |
| `verify.py` | Jar integrity checks: no named refs, layout, refmap, assets |
| `make_assets.py` | Procedural asset generator (spider texture, sounds, lang) |
| `yarn-1.21.1.tiny` | Vendored Yarn mappings (CC0), refreshed by `fetch-deps.yml` |

## Notes

- `loom` in `build.gradle` remains the canonical developer build
  (`./gradlew build`); this toolchain exists so the jar can also be built
  in a sandbox without Maven access. Both produce an equivalent jar.
- Build outputs (`classes/`, `classes-named/`, `gen-stubs/`, `jar-work/`,
  `stub-classes/`, `test-classes/`, `*.jar`) are git-ignored.
