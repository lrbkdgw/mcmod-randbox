#!/usr/bin/env bash
# Compiles the mod without access to the NeoForge / Mojang maven repositories.
#
#   1. tools/build-sdk.sh generates a compile time SDK out of the *sources* of Minecraft 1.20.1,
#      NeoForge 47, FML, EventBus, Brigadier, Gson, JOML and SLF4J (all fetched from GitHub).
#   2. src/main/java is compiled against that SDK with the Eclipse batch compiler.
#   3. The classes and the processed resources are packed into build/libs/<mod_id>-<version>-dev.jar.
#
# The result is exactly what `./gradlew build` produces *before* the reobfuscation step, i.e. it is
# compiled against official (Mojang) names. To get a jar that can be dropped into a 1.20.1 NeoForge
# installation, run `./gradlew build` in an environment that can reach maven.neoforged.net, which
# additionally remaps the official names to the SRG names used at runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${RANDOMBOX_TOOL_DIR:-/tmp/randombox-tools}"
SDK="$WORK/sdk"
OUT="$ROOT/build/offline-classes"
LIBS="$ROOT/build/libs"

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

# --- the Minecraft / NeoForge SDK ---------------------------------------------------
if [ ! -d "$SDK" ]; then
  "$ROOT/tools/build-sdk.sh"
fi

echo ">> compiling src/main/java against the Minecraft 1.20.1 / NeoForge 47 API"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT/src/main/java" -name '*.java' > "$WORK/mod-sources.txt"
set +e
"$WORK/jre/bin/java" -Xmx3g -cp "$WORK/ecj.jar" org.eclipse.jdt.internal.compiler.batch.Main \
  -source 17 -target 17 -nowarn -proc:none -proceedOnError \
  -sourcepath "$SDK" -d "$OUT" "@$WORK/mod-sources.txt" > "$WORK/compile.log" 2>&1
set -e

# Errors inside the generated SDK stubs are expected (it is a best effort reconstruction of
# thousands of classes); only errors in the mod itself matter.
python3 - "$WORK/compile.log" "$ROOT" <<'PY'
import sys
log, root = sys.argv[1], sys.argv[2]
blocks = open(log, encoding="utf8", errors="replace").read().split("----------")
mine = [b.strip() for b in blocks if "ERROR in " + root + "/src" in b]
for b in mine:
    print(b)
print(">> errors in mod sources: %d" % len(mine))
sys.exit(1 if mine else 0)
PY

echo ">> packaging"
rm -rf "$WORK/jarroot"
mkdir -p "$WORK/jarroot"
# only the mod classes, not the SDK classes ECJ compiled from the sourcepath
mkdir -p "$WORK/jarroot/com"
cp -r "$OUT/com/randombox" "$WORK/jarroot/com"/
cp -r "$ROOT/src/main/resources/." "$WORK/jarroot"/

# same variable expansion as gradle's processResources
python3 - "$ROOT" "$WORK/jarroot" <<'PY'
import os, re, sys
root, jarroot = sys.argv[1], sys.argv[2]
props = {}
for line in open(os.path.join(root, "gradle.properties"), encoding="utf8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
for rel in ("META-INF/mods.toml", "pack.mcmeta"):
    path = os.path.join(jarroot, rel)
    if not os.path.exists(path):
        continue
    text = open(path, encoding="utf8").read()
    text = re.sub(r"\$\{(\w+)\}", lambda m: props.get(m.group(1), m.group(0)), text)
    open(path, "w", encoding="utf8").write(text)
PY

mkdir -p "$LIBS"
MOD_ID=$(grep '^mod_id=' "$ROOT/gradle.properties" | cut -d= -f2)
MOD_VERSION=$(grep '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2)
JAR="$LIBS/$MOD_ID-$MOD_VERSION-dev.jar"
rm -f "$JAR"
(cd "$WORK/jarroot" && zip -qr "$JAR" .)

mkdir -p "$ROOT/dist"
cp "$JAR" "$ROOT/dist/"

echo ">> OK"
echo "   mod classes : $(find "$OUT/com/randombox" -name '*.class' | wc -l)"
echo "   jar     : $JAR (copied to dist/)"
