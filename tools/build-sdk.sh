#!/usr/bin/env bash
# Builds a compile time SDK (signature only stubs) for Minecraft 1.20.1 + NeoForge 47.
#
# The maven repositories of NeoForge / Mojang are unreachable in this sandbox, but the *sources*
# of everything involved are on GitHub, so the SDK is generated from them:
#
#   Minecraft 1.20.1 (Mojang names) : Blackjack200/minecraft_client_1_20_1
#   NeoForge 1.20.1                 : NeoForged/NeoForge          @ 1.20.1
#   FML                             : NeoForged/FancyModLoader    @ 1.20.1
#   EventBus                        : MinecraftForge/EventBus     @ 6.2.x
#   Brigadier / Gson / JOML / SLF4J : upstream repositories
#
# Everything else (guava, netty, fastutil, ...) only appears inside signatures and is replaced
# by generated placeholders.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${RANDOMBOX_TOOL_DIR:-/tmp/randombox-tools}"
SRC="$WORK/sources"
SDK="$WORK/sdk"

mkdir -p "$SRC"

fetch() { # repo ref name
  if [ ! -d "$SRC/$3" ]; then
    echo ">> fetching $1@$2"
    curl -sL -o "$SRC/$3.tgz" "https://codeload.github.com/$1/tar.gz/refs/heads/$2"
    mkdir -p "$SRC/$3"
    tar xzf "$SRC/$3.tgz" -C "$SRC/$3"
  fi
}

fetch Blackjack200/minecraft_client_1_20_1 master minecraft
fetch NeoForged/NeoForge 1.20.1 neoforge
fetch NeoForged/FancyModLoader 1.20.1 fml
fetch MinecraftForge/EventBus 6.2.x eventbus
fetch Mojang/brigadier master brigadier
fetch google/gson main gson
fetch JOML-CI/JOML main joml
fetch qos-ch/slf4j master slf4j

# python parser
if [ ! -x "$WORK/venv/bin/python" ]; then
  python3 -m venv "$WORK/venv"
  "$WORK/venv/bin/pip" -q install tree_sitter tree_sitter_java
fi

echo ">> generating stubs"
rm -rf "$SDK"
"$WORK/venv/bin/python" "$ROOT/tools/stubgen.py" \
  "$SRC"/minecraft/minecraft_client_1_20_1-master \
  "$SRC"/neoforge/NeoForge-1.20.1/src/main/java \
  "$SRC"/fml/FancyModLoader-1.20.1/core/src/main/java \
  "$SRC"/fml/FancyModLoader-1.20.1/loader/src/main/java \
  "$SRC"/fml/FancyModLoader-1.20.1/languages/java/src/main/java \
  "$SRC"/fml/FancyModLoader-1.20.1/events/src/main/java \
  "$SRC"/eventbus/EventBus-6.2.x/src/main/java \
  "$SRC"/brigadier/brigadier-master/src/main/java \
  "$SRC"/gson/gson-main/gson/src/main/java \
  "$SRC"/joml/JOML-main/src/main/java \
  "$SRC"/slf4j/slf4j-master/slf4j-api/src/main/java \
  "$SDK"

echo ">> applying hand written overrides"
cp -r "$ROOT"/tools/sdkoverrides/* "$SDK"/

echo ">> generating placeholders for the remaining third party types"
"$WORK/venv/bin/python" "$ROOT/tools/mkplaceholders.py" "$SDK"

echo ">> SDK ready: $(find "$SDK" -name '*.java' | wc -l) files in $SDK"
