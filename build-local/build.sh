#!/usr/bin/env bash
# Local production build: named compile -> intermediary remap -> jar.
# Run from anywhere: build-local/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."
JAVAC="${JAVAC:-/tmp/jdk/package/jre/bin/javac}"
OUT_JAR="build-local/spiderman-1.0.0.jar"

echo "[build] generating stubs..."
rm -rf build-local/gen-stubs build-local/stub-classes build-local/classes-named build-local/classes build-local/jar-work
python3 build-local/stubgen.py
python3 build-local/handstubs.py

echo "[build] compiling stubs..."
mkdir -p build-local/stub-classes build-local/classes-named build-local/classes build-local/jar-work
find "$PWD/build-local/gen-stubs" -name "*.java" > /tmp/stub-sources.txt
"$JAVAC" -encoding UTF-8 -nowarn -d build-local/stub-classes @/tmp/stub-sources.txt

echo "[build] compiling mod (named)..."
find src/main/java -name "*.java" > /tmp/mod-sources.txt
"$JAVAC" -encoding UTF-8 -nowarn -cp build-local/stub-classes \
  -d build-local/classes-named @/tmp/mod-sources.txt

echo "[build] remapping to intermediary..."
python3 build-local/remap.py build-local/classes-named build-local/classes

echo "[build] generating refmap..."
python3 build-local/refmap.py build-local/jar-work/spiderman-refmap.json

echo "[build] assembling jar..."
cp -r src/main/resources/* build-local/jar-work/
# Same ${version} expansion Loom's processResources performs.
MOD_VERSION=$(grep -m1 '^mod_version=' gradle.properties | cut -d= -f2)
sed -i "s/\\\${version}/$MOD_VERSION/" build-local/jar-work/fabric.mod.json
python3 - build-local/jar-work/spiderman.mixins.json <<'EOF'
import json, sys
p = sys.argv[1]
cfg = json.load(open(p))
cfg.setdefault("refmap", "spiderman-refmap.json")
json.dump(cfg, open(p, "w"), indent=2)
EOF
python3 - build-local/classes build-local/jar-work "$OUT_JAR" <<'EOF'
import os, sys, zipfile
classes, work, out = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    # A manifest first entry is what makes this a normal jar (rather than a
    # plain zip) for upload validators and archive tools.
    z.writestr("META-INF/MANIFEST.MF",
               "Manifest-Version: 1.0\nCreated-By: spidermanmod local build\n")
    for root, _ds, files in os.walk(classes):
        for fn in sorted(files):
            if fn.endswith(".class"):
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, classes))
    for root, _ds, files in os.walk(work):
        for fn in sorted(files):
            full = os.path.join(root, fn)
            z.write(full, os.path.relpath(full, work))
print(f"[build] wrote {out}")
EOF

echo "[build] verifying..."
python3 build-local/verify.py build-local/classes "$OUT_JAR"
# Standard Gradle location, so the jar is also where `gradlew build` puts it.
mkdir -p build/libs
cp "$OUT_JAR" build/libs/
echo "[build] DONE: $OUT_JAR (also copied to build/libs/)"
