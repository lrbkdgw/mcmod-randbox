#!/usr/bin/env python3
"""Create minimal placeholder types for third party libraries.

The generated SDK (see stubgen.py) only contains Minecraft / NeoForge / FML sources. Their
signatures still mention types from guava, netty, fastutil, brigadier, ... Because all method
bodies are stripped, those types only have to *exist* with the right shape, so this script
synthesises empty ones (with the generic arity and the class/interface/throwable role inferred
from how they are used).
"""
import os
import re
import sys

SDK = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else SDK

known = set()
for dirpath, _dirs, files in os.walk(SDK):
    for name in files:
        if name.endswith(".java"):
            rel = os.path.relpath(os.path.join(dirpath, name), SDK)
            known.add(rel[:-5].replace(os.sep, "."))

IMPORT = re.compile(r"^import\s+([\w.]+);", re.M)

needed = {}          # fqn -> simple name
texts = {}

for dirpath, _dirs, files in os.walk(SDK):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        with open(path, encoding="utf8") as handle:
            text = handle.read()
        texts[path] = text
        changed = False
        for fqn in IMPORT.findall(text):
            if fqn in known or fqn.startswith("java.") or fqn.startswith("javax."):
                continue
            simple = fqn.rsplit(".", 1)[-1]
            body = IMPORT.sub("", text)
            if re.search(r"\b%s\b" % re.escape(simple), body):
                needed[fqn] = simple
            else:
                text = text.replace("import %s;\n" % fqn, "")
                changed = True
        if changed:
            with open(path, "w", encoding="utf8") as handle:
                handle.write(text)
            texts[path] = text

all_text = "\n".join(texts.values())


def arity(simple):
    best = 0
    for match in re.finditer(r"\b%s\s*<" % re.escape(simple), all_text):
        i = match.end()
        depth = 1
        commas = 0
        while i < len(all_text) and depth > 0:
            c = all_text[i]
            if c == "<":
                depth += 1
            elif c == ">":
                depth -= 1
            elif c == "," and depth == 1:
                commas += 1
            elif c in ";{\n":
                depth = 0
                commas = 0
                break
            i += 1
        best = max(best, commas + 1)
    return best


def role(simple):
    if re.search(r"throws[^;{]*\b%s\b" % re.escape(simple), all_text):
        return "throwable"
    if re.search(r"implements[^{]*\b%s\b" % re.escape(simple), all_text):
        return "interface"
    if re.search(r"interface\s+\w+[^{]*extends[^{]*\b%s\b" % re.escape(simple), all_text):
        return "interface"
    return "class"


created = 0
for fqn, simple in sorted(needed.items()):
    package, _ = fqn.rsplit(".", 1)
    target = os.path.join(OUT, fqn.replace(".", os.sep) + ".java")
    if os.path.exists(target):
        continue
    os.makedirs(os.path.dirname(target), exist_ok=True)
    n = arity(simple)
    params = "<%s>" % ", ".join("T%d" % i for i in range(n)) if n else ""
    kind = role(simple)
    if kind == "throwable":
        decl = "public class %s%s extends RuntimeException {}" % (simple, params)
    elif kind == "interface":
        decl = "public interface %s%s {}" % (simple, params)
    else:
        decl = "public class %s%s {}" % (simple, params)
    with open(target, "w", encoding="utf8") as handle:
        handle.write("package %s;\n\n%s\n" % (package, decl))
    created += 1

print("placeholders created: %d (of %d external types)" % (created, len(needed)))
