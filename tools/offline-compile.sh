#!/usr/bin/env bash
# Offline type check of the mod sources.
#
# The normal way to build this mod is:
#
#     ./gradlew build          (needs access to maven.neoforged.net / Mojang libraries)
#
# This script exists for environments without access to the Minecraft / NeoForge maven
# repositories. It compiles src/main/java against the hand written API stubs in
# tools/apistubs with the Eclipse batch compiler, which verifies that the whole mod is
# syntactically valid and internally consistent.
#
# The produced classes are a compile check only, they are NOT a runnable mod jar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${RANDOMBOX_TOOL_DIR:-/tmp/randombox-tools}"
OUT="$ROOT/build/offline-classes"

mkdir -p "$WORK"

# --- a JRE (jdk4py wheel from PyPI) -------------------------------------------------
if [ ! -x "$WORK/jre/bin/java" ]; then
  echo ">> downloading a JRE"
  pip download jdk4py -d "$WORK/wheel" --no-deps -q
  unzip -q -o "$WORK"/wheel/jdk4py-*.whl -d "$WORK/wheelx"
  cp -r "$WORK"/wheelx/jdk4py/java-runtime "$WORK/jre"
fi

# --- the Eclipse batch compiler (shipped inside an npm package) ---------------------
if [ ! -f "$WORK/ecj.jar" ]; then
  echo ">> downloading the Eclipse batch compiler"
  curl -sSL -o "$WORK/ecj.tgz" \
    "$(curl -s https://registry.npmjs.org/@ctxo/lang-java-analyzer | \
       python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["versions"][d["dist-tags"]["latest"]]["dist"]["tarball"])')"
  tar xzf "$WORK/ecj.tgz" -C "$WORK"
  cp "$WORK"/package/jar/ctxo-jdt-analyzer-17.jar "$WORK/ecj.jar"
fi

echo ">> compiling"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT/tools/apistubs" "$ROOT/src/main/java" -name '*.java' > "$WORK/sources.txt"
"$WORK/jre/bin/java" -cp "$WORK/ecj.jar" org.eclipse.jdt.internal.compiler.batch.Main \
  -source 17 -target 17 -nowarn -d "$OUT" "@$WORK/sources.txt"

echo ">> OK: $(find "$OUT" -name '*.class' | wc -l) classes in $OUT"
