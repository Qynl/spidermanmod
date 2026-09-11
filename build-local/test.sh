#!/usr/bin/env bash
# Runs the pure-logic unit tests (no Minecraft, no JUnit needed).
# Usage: ./build-local/test.sh   (run ./build-local/build.sh first)
set -euo pipefail
cd "$(dirname "$0")/.."

# Pure-python remap/linkage tests: self-contained, no JDK needed.
python3 build-local/test_remap.py

if [ -z "$(find build-local/classes-named -name '*.class' 2>/dev/null | head -1)" ]; then
  echo "[test] no compiled mod classes; run ./build-local/build.sh first" >&2
  exit 1
fi

JAVAC="${JAVAC:-/tmp/jdk/package/jre/bin/javac}"
JAVA_BIN="$(dirname "$JAVAC")/java"
if [ ! -x "$JAVAC" ]; then
  echo "[test] no javac found (tried \$JAVAC and /tmp/jdk); set JAVAC=/path/to/javac" >&2
  exit 1
fi

rm -rf build-local/test-classes
mkdir -p build-local/test-classes
find src/test/java -name "*.java" > build-local/test-sources.txt
"$JAVAC" -cp "build-local/classes-named:build-local/stub-classes" \
  -d build-local/test-classes @build-local/test-sources.txt
echo "[test] compiled $(wc -l < build-local/test-sources.txt) test files"

"$JAVA_BIN" -cp "build-local/test-classes:build-local/classes-named:build-local/stub-classes" \
  com.spiderman.mod.state.PowersLogicTest
"$JAVA_BIN" -cp "build-local/test-classes:build-local/classes-named:build-local/stub-classes" \
  com.spiderman.mod.client.WheelMathTest
echo "[test] ALL TESTS PASSED"
