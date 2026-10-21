"""Bounded offline static HTML text projection; never a browser or resource loader."""
from dataclasses import dataclass, field
from html.parser import HTMLParser
import hashlib
import re
import sys

REVISION = "HTML-STATIC-TEXT-1"
VOID = frozenset("area base br col embed hr img input link meta param source track wbr".split())
EXCLUDED = frozenset("script style template noscript iframe object embed".split())
BLOCK = frozenset("address article aside blockquote div dl fieldset footer form h1 h2 h3 h4 h5 h6 header hr main nav ol p pre section table ul".split())

@dataclass
class Element:
    tag: str
    path: str
    hidden: bool = False
    children: list = field(default_factory=list)
    counts: dict = field(default_factory=dict)

class Projection(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Element("", "")
        self.stack = [self.root]
        self.elements = self.characters = self.excluded = self.recoveries = 0

    def handle_starttag(self, tag, attrs):
        if not re.fullmatch(r"[a-z][a-z0-9:_-]*", tag) or len(attrs) > 256:
            raise ValueError("HTML_ELEMENT_INVALID")
        # Explicit, versioned optional-end-tag recovery, not a browser DOM claim.
        closable = {"li": {"li"}, "dt": {"dt", "dd"}, "dd": {"dt", "dd"},
                    "tr": {"tr"}, "td": {"td", "th"}, "th": {"td", "th"}, "option": {"option"}}
        for index in range(len(self.stack)-1, 0, -1):
            current = self.stack[index].tag
            if current in closable.get(tag, set()) or current == "p" and tag in BLOCK:
                self.stack = self.stack[:index]
                self.recoveries += 1
                break
        if len(self.stack) >= 128 or self.elements >= 100000:
            raise ValueError("HTML_STRUCTURE_LIMIT")
        parent = self.stack[-1]
        parent.counts[tag] = parent.counts.get(tag, 0) + 1
        path = f"{parent.path}/{tag}[{parent.counts[tag]}]"
        if len(path) > 4096:
            raise ValueError("HTML_PATH_LIMIT")
        attributes = dict(attrs)
        style = re.sub(r"\s+", "", attributes.get("style") or "").lower()
        hidden = parent.hidden or tag in EXCLUDED or "hidden" in attributes or (attributes.get("aria-hidden") or "").lower() == "true" or "display:none" in style or "visibility:hidden" in style
        node = Element(tag, path, hidden)
        parent.children.append(node)
        self.elements += 1
        self.excluded += int(hidden and not parent.hidden)
        if tag not in VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        for index in range(len(self.stack)-1, 0, -1):
            if self.stack[index].tag == tag:
                self.recoveries += int(index != len(self.stack)-1)
                self.stack = self.stack[:index]
                return
        self.recoveries += 1

    def handle_data(self, data):
        self.characters += len(data)
        if self.characters > 16000000:
            raise ValueError("HTML_TEXT_LIMIT")
        if not self.stack[-1].hidden:
            self.stack[-1].children.append(data)

    def unknown_decl(self, data):
        raise ValueError("HTML_DECLARATION_UNSUPPORTED")


def tree(content):
    encoding = "utf-8"
    if content.startswith(b"\xef\xbb\xbf"):
        encoding = "utf-8-sig"
    elif content.startswith((b"\xff\xfe", b"\xfe\xff")):
        encoding = "utf-16"
    else:
        meta = re.search(br"<meta\b[^>]*charset\s*=\s*['\"]?\s*([a-zA-Z0-9_-]+)", content[:8192], re.I)
        if meta:
            encoding = meta.group(1).decode("ascii").lower()
            if encoding not in {"utf-8", "utf8", "windows-1252", "iso-8859-1", "latin1", "gb18030", "gbk", "big5", "shift_jis"}:
                raise ValueError("HTML_ENCODING_UNSUPPORTED")
    text = content.decode(encoding, errors="strict")
    if "\x00" in text:
        raise ValueError("HTML_NUL_INVALID")
    parser = Projection()
    parser.feed(text)
    parser.close()
    return parser, encoding


def parse_html(content, context):
    parser, encoding = tree(content)
    original = hashlib.sha256(content).hexdigest()
    def walk(node):
        if node.hidden:
            return 0
        offset = 0
        for child in node.children:
            if isinstance(child, str):
                if child.strip():
                    if not node.path:
                        raise ValueError("HTML_TEXT_OUTSIDE_ELEMENT")
                    context.node(child, {"schema_version": "1.0.0", "kind": "HTML", "original_sha256": original,
                        "address": {"kind": "HTML_ELEMENT", "element_path": node.path,
                                    "text_start": offset, "text_end": offset + len(child)}})
                offset += len(child)
            else:
                offset += walk(child)
        return offset
    walk(parser.root)
    context.manifest.update({"html_parser_revision": REVISION, "html_original_sha256": original,
        "html_source_evidence_sha256": context.bindings.catalogue.digests["source-evidence-v2"],
        "html_encoding": encoding, "html_projection": "STATIC_DESCENDANT_TEXT_CODEPOINTS",
        "html_python_version": sys.version.split()[0], "html_script_execution": False,
        "html_css_execution": False, "html_external_assets": "NOT_FETCHED",
        "html_element_count": parser.elements, "html_excluded_count": parser.excluded,
        "html_recovery_count": parser.recoveries})
