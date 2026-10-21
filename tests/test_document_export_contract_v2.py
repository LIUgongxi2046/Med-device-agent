"""Deferred unit checks for the additive authenticated export boundary. No converter calls."""
import hashlib
import hmac
from pathlib import Path
import unittest
from uuid import uuid4

import rfc8785
from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.errors import SchemaValidationError
from medical_document.export_contract import authenticate, verify_authentication, V2_BODY_REF

ROOT = Path(__file__).resolve().parents[1]

class ExportCommandV2Test(unittest.TestCase):
    def setUp(self):
        self.catalogue = SchemaCatalogue(ROOT)
        self.key = bytes(range(32))
        self.request = {
            'schema_version': '2.0.0', 'body_schema_ref': V2_BODY_REF,
            'body_schema_sha256': self.catalogue.digests['document-body-v2'],
            **{key: str(uuid4()) for key in ('tenant_id', 'project_id', 'document_revision', 'attempt_id', 'template_version', 'font_manifest_revision')},
            'body_sha256': 'a' * 64, 'template_sha256': 'b' * 64,
            'purpose': 'FORMAL_BODY', 'input_data_kind': 'REGISTERED', 'format': 'DOCX',
            'fonts': [{'path': '/private/work/font.ttf', 'sha256': 'c' * 64}], 'assets': [],
            'converter': {'revision': str(uuid4()), 'path': '/private/converter', 'version': 'pinned', 'timeout_seconds': 60, 'sha256': 'd' * 64},
            'body_path': '/private/work/body.json', 'template_path': '/private/work/template.docx',
            'catalogue_root': str(ROOT), 'output_directory': '/private/work', 'layout_profile': None,
        }

    def signed(self, request=None):
        content = rfc8785.dumps(request or self.request)
        tag = hmac.new(self.key, content, hashlib.sha256).hexdigest()
        return content, (self.key.hex() + ':' + tag).encode('ascii')

    def test_exact_authenticated_registered_body_contract(self):
        content, proof = self.signed()
        value, authority = authenticate(content, proof, self.catalogue)
        self.assertEqual(value, self.request)
        self.assertEqual(authority.command_sha256, hashlib.sha256(content).hexdigest())
        self.assertEqual(authority.body_sha256, self.request['body_sha256'])
        self.assertEqual(authority.attempt_id, self.request['attempt_id'])

    def test_payload_version_is_not_authority(self):
        content, _ = self.signed()
        with self.assertRaises(ValueError):
            verify_authentication(content, b'')

    def test_modified_signed_command_rejected(self):
        content, proof = self.signed()
        with self.assertRaises(ValueError):
            verify_authentication(content.replace(b'FORMAL_BODY', b'REVIEW_PACKAGE'), proof)

    def test_signed_wrong_registered_digest_rejected(self):
        self.request['body_schema_sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            authenticate(*self.signed(), self.catalogue)

    def test_signed_v1_ref_cannot_select_v2(self):
        self.request['body_schema_ref'] = self.catalogue.schema_ref('interfaces', 'DocumentBody')
        with self.assertRaises(SchemaValidationError):
            authenticate(*self.signed(), self.catalogue)

    def test_noncanonical_command_rejected_even_with_valid_tag(self):
        content, _ = self.signed()
        content += b'\n'
        proof = (self.key.hex() + ':' + hmac.new(self.key, content, hashlib.sha256).hexdigest()).encode()
        with self.assertRaises(ValueError):
            authenticate(content, proof, self.catalogue)
