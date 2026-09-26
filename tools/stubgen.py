#!/usr/bin/env python3
"""Turn real Java sources into signature only stubs.

Used to build a compile time SDK for Minecraft 1.20.1 / NeoForge 47 out of the published
sources, in environments where the maven artifacts are unreachable. Method bodies and field
initialisers are dropped, annotations are removed; everything that matters for type checking
(class hierarchy, generics, signatures) is kept verbatim.
"""
import os
import re
import sys

from tree_sitter import Language, Parser
import tree_sitter_java

PARSER = Parser(Language(tree_sitter_java.language()))

PRIMITIVE_DEFAULT = {
    "int": "0", "long": "0L", "short": "(short) 0", "byte": "(byte) 0", "char": "'\\0'",
    "float": "0.0F", "double": "0.0D", "boolean": "false",
}

DROP_MODIFIERS = {"final", "native", "synchronized", "strictfp", "transient", "volatile", "sealed", "non-sealed"}


class StubBuilder:
    def __init__(self, source: bytes):
        self.src = source
        self.blank = bytearray(source)
        tree = PARSER.parse(source)
        self.root = tree.root_node
        self._blank_annotations(self.root)

    # ---------------------------------------------------------------- helpers
    def _blank_annotations(self, node):
        for child in node.children:
            if child.type in ("marker_annotation", "annotation"):
                for i in range(child.start_byte, child.end_byte):
                    self.blank[i] = 0x20
            else:
                self._blank_annotations(child)

    def text(self, node) -> str:
        return bytes(self.blank[node.start_byte:node.end_byte]).decode("utf8", "replace")

    def raw(self, node) -> str:
        return self.src[node.start_byte:node.end_byte].decode("utf8", "replace")

    def slice(self, start: int, end: int) -> str:
        return bytes(self.blank[start:end]).decode("utf8", "replace")

    @staticmethod
    def modifiers(node, drop=DROP_MODIFIERS):
        mods = []
        for child in node.children:
            if child.type == "modifiers":
                for m in child.children:
                    if m.type in ("marker_annotation", "annotation"):
                        continue
                    if m.type in drop:
                        continue
                    mods.append(m.type)
        return mods

    # ---------------------------------------------------------------- members
    def method(self, node, in_interface: bool) -> str:
        body = node.child_by_field_name("body")
        end = body.start_byte if body is not None else node.end_byte
        head = self.slice(node.start_byte, end).strip().rstrip(";").strip()
        for mod in ("native ", "synchronized ", "strictfp ", "final "):
            head = head.replace(mod, "")
        mods = self.modifiers(node)
        is_abstract = "abstract" in mods
        if in_interface and body is None and "static" not in mods and "default" not in mods:
            return head + ";"
        if is_abstract:
            return head + ";"
        return head + " { throw new RuntimeException(\"stub\"); }"

    def constructor(self, node) -> str:
        body = node.child_by_field_name("body")
        end = body.start_byte if body is not None else node.end_byte
        head = self.slice(node.start_byte, end).strip()
        return head + " { throw new RuntimeException(\"stub\"); }"

    LITERALS = {"decimal_integer_literal", "hex_integer_literal", "octal_integer_literal",
                "binary_integer_literal", "decimal_floating_point_literal", "hex_floating_point_literal",
                "string_literal", "character_literal", "true", "false", "null_literal", "unary_expression"}

    def field(self, node, in_interface: bool) -> str:
        type_node = node.child_by_field_name("type")
        if type_node is None:
            return ""
        type_text = self.text(type_node).strip()
        mods = [m for m in self.modifiers(node)]
        out = []
        for child in node.children:
            if child.type != "variable_declarator":
                continue
            name_node = child.child_by_field_name("name")
            if name_node is None:
                continue
            name = self.text(name_node).strip()
            dims = ""
            for sub in child.children:
                if sub.type == "dimensions":
                    dims += self.text(sub)
            full_type = type_text + dims
            if in_interface:
                literal = child.child_by_field_name("value")
                if literal is not None and literal.type in self.LITERALS:
                    value = self.text(literal).strip()
                else:
                    value = PRIMITIVE_DEFAULT.get(full_type, "null")
                out.append("%s %s %s = %s;" % (" ".join(mods), full_type, name, value))
            else:
                out.append("%s %s %s;" % (" ".join(mods), full_type, name))
        return "\n".join(out)

    # ----------------------------------------------------------------- bodies
    def type_body(self, body_node, kind: str, type_name: str) -> str:
        parts = []
        has_no_arg_ctor = False
        ctor_count = 0
        for child in body_node.children:
            t = child.type
            if t == "method_declaration":
                parts.append(self.method(child, kind == "interface"))
            elif t == "constructor_declaration":
                if kind in ("enum", "record"):
                    continue
                ctor_count += 1
                params = child.child_by_field_name("parameters")
                if params is not None and self.text(params).strip() == "()":
                    has_no_arg_ctor = True
                parts.append(self.constructor(child))
            elif t in ("field_declaration", "constant_declaration"):
                if kind == "record" and "static" not in self.modifiers(child):
                    continue  # record components already declare these fields
                parts.append(self.field(child, kind == "interface"))
            elif t in ("class_declaration", "interface_declaration", "enum_declaration",
                       "record_declaration", "annotation_type_declaration"):
                parts.append(self.type_declaration(child))
            elif t == "compact_constructor_declaration":
                continue
        if kind == "class" and ctor_count and not has_no_arg_ctor:
            # every stub class gets a no-arg constructor so subclass stubs keep compiling
            parts.insert(0, "protected %s() { throw new RuntimeException(\"stub\"); }" % type_name)
        return "\n".join(p for p in parts if p.strip())

    def type_declaration(self, node) -> str:
        t = node.type
        name_node = node.child_by_field_name("name")
        name = self.text(name_node).strip() if name_node is not None else "Unknown"
        body = node.child_by_field_name("body")

        if t == "annotation_type_declaration":
            elements = []
            if body is not None:
                for child in body.children:
                    if child.type == "annotation_type_element_declaration":
                        elements.append(self.text(child).strip())
                    elif child.type in ("class_declaration", "interface_declaration", "enum_declaration",
                                        "record_declaration", "annotation_type_declaration"):
                        elements.append(self.type_declaration(child))
                    elif child.type == "field_declaration":
                        elements.append(self.field(child, True))
            return "public @interface %s {\n%s\n}" % (name, "\n".join(elements))

        head_end = body.start_byte if body is not None else node.end_byte
        head = self.slice(node.start_byte, head_end).strip()
        head = re.sub(r"^(private|protected|public)\s+", "", head)
        head = "public " + head
        for mod in ("final ", "sealed ", "non-sealed ", "strictfp "):
            head = head.replace(mod, "")
        head = re.sub(r"\bpermits\s+[^\{]+", "", head)

        if t == "enum_declaration":
            constants = []
            decls = []
            if body is not None:
                for child in body.children:
                    if child.type == "enum_constant":
                        cname = child.child_by_field_name("name")
                        constants.append(self.text(cname).strip())
                    elif child.type == "enum_body_declarations":
                        decls.append(self.type_body(child, "enum", name))
            inner = ", ".join(constants) + (";" if decls else ";")
            return "%s {\n%s\n%s\n}" % (head, inner, "\n".join(decls))

        kind = {"class_declaration": "class", "interface_declaration": "interface",
                "record_declaration": "record"}[t]
        inner = self.type_body(body, kind, name) if body is not None else ""
        return "%s {\n%s\n}" % (head, inner)

    # ------------------------------------------------------------------ file
    def build(self):
        package = ""
        imports = []
        types = []
        for child in self.root.children:
            if child.type == "package_declaration":
                package = self.text(child).strip()
            elif child.type == "import_declaration":
                imports.append(self.text(child).strip())
            elif child.type in ("class_declaration", "interface_declaration", "enum_declaration",
                                "record_declaration", "annotation_type_declaration"):
                types.append(self.type_declaration(child))
        return package, imports, types


def convert(path: str):
    with open(path, "rb") as handle:
        source = handle.read()
    builder = StubBuilder(source)
    return builder.build()


def main():
    roots = sys.argv[1:-1]
    out_root = sys.argv[-1]
    written = 0
    failed = []
    for root in roots:
        for dirpath, _dirs, files in os.walk(root):
            for name in files:
                if not name.endswith(".java") or name == "package-info.java":
                    continue
                path = os.path.join(dirpath, name)
                try:
                    package, imports, types = convert(path)
                except Exception as exc:  # pragma: no cover
                    failed.append((path, str(exc)))
                    continue
                if not types:
                    continue
                rel = os.path.relpath(path, root)
                target = os.path.join(out_root, rel)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "w", encoding="utf8") as handle:
                    if package:
                        handle.write(package + "\n\n")
                    for imp in imports:
                        if imp.startswith("import static"):
                            continue
                        handle.write(imp + "\n")
                    handle.write("\n")
                    for text in types:
                        handle.write(text + "\n\n")
                written += 1
    print("wrote %d stub files, %d failures" % (written, len(failed)))
    for path, exc in failed[:10]:
        print("  FAIL", path, exc)


if __name__ == "__main__":
    main()
