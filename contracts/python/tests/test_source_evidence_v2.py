"""Deferred regression source, intentionally not executed in the no-test stage."""
import unittest
from pathlib import Path
from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.source_evidence import bind_evidence

class EvidenceV2Tests(unittest.TestCase):
    def test_html_explicit_version_and_legacy_rejection(self):
        c = SchemaCatalogue(Path(__file__).resolve().parents[3])
        value = dict(schema_version="2.0.0", tenant_id="tenant", project_id="project", source_id="source",
                     source_version="version", parse_version="parse", chunk_id="chunk", locator_kind="HTML",
                     quote_hash="b" * 64, approval_revision="revision", locator=dict(schema_version="1.0.0",
                     kind="HTML", original_sha256="a" * 64, address=dict(kind="HTML_ELEMENT",
                     element_path="/html[1]/body[1]/p[1]", text_start=0, text_end=4)))
        self.assertEqual(bind_evidence(c, value).read().locator.kind, "HTML")
        with self.assertRaises(Exception):
            bind_evidence(c, {**value, "schema_version": "1.0.0"})
