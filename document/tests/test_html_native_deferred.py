"""Deferred tests: not executed during the code-only implementation session."""
import hashlib
from types import SimpleNamespace
import pytest
from medical_document.html_native import parse_html, tree

class Context:
    def __init__(self):
        self.nodes = []
        self.manifest = {}
        self.bindings = SimpleNamespace(catalogue=SimpleNamespace(digests={"source-evidence-v2": "a" * 64}))
    def node(self, text, locator):
        self.nodes.append((text, locator))

def test_raw_hash_unicode_offsets_and_no_script():
    raw = '<html><body><p>A😀<b>B</b>C</p><script>secret</script><p hidden>hidden</p></body></html>'.encode()
    context = Context()
    parse_html(raw, context)
    assert [text for text, _ in context.nodes] == ['A😀', 'B', 'C']
    assert context.nodes[2][1]['address'] == {'kind': 'HTML_ELEMENT', 'element_path': '/html[1]/body[1]/p[1]', 'text_start': 3, 'text_end': 4}
    assert all(locator['original_sha256'] == hashlib.sha256(raw).hexdigest() for _, locator in context.nodes)
    assert context.manifest['html_external_assets'] == 'NOT_FETCHED'

def test_structure_and_encoding_fail_closed():
    with pytest.raises(ValueError):
        tree(b'<div>' * 128)
    with pytest.raises(ValueError):
        tree(b'<meta charset="utf-7"><p>x</p>')

def test_optional_paragraph_end_has_distinct_address():
    context = Context()
    parse_html(b'<html><body><p>one<p>two</body></html>', context)
    assert [locator['address']['element_path'] for _, locator in context.nodes] == ['/html[1]/body[1]/p[1]', '/html[1]/body[1]/p[2]']
