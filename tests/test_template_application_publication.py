"""Deferred regression assertions for immutable v1 and additive application proofs. NOT RUN."""
import hashlib
import json
from pathlib import Path
import unittest
from medical_contracts.catalogue import SchemaCatalogue, _merge_published_catalogue

ROOT = Path(__file__).resolve().parents[1]
BASE = 'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915'
LEGACY = '0c304401621f198af342a823fa51b826a101b2e07ff616d09d3a1f190b01f498'
EXTENSION = 'contracts/extensions/template-application.catalogue.json'

class TemplateApplicationPublicationTest(unittest.TestCase):
    def test_original_bytes_and_new_namespace_are_separate(self):
        self.assertEqual(BASE, hashlib.sha256((ROOT/'contracts/catalogue.json').read_bytes()).hexdigest())
        path = ROOT/'business/medical-persistence/src/main/resources/template-applicability.schema.json'
        self.assertEqual(LEGACY, hashlib.sha256(path.read_bytes()).hexdigest())
        old = json.loads(path.read_bytes())
        self.assertNotIn('FixedView', old['$defs'])
        self.assertNotIn('machine_conditions', old['$defs']['TemplateView']['properties'])
        catalogue = SchemaCatalogue(ROOT)
        self.assertEqual('urn:medical:template-application:1.0.0#/$defs/FixedView', catalogue.schema_ref('template-application','FixedView'))
        view = catalogue.schemas['template-application']['$defs']['TemplateView']
        self.assertTrue({'machine_conditions','human_decisions'}.issubset(view['required']))
        self.assertFalse(view['additionalProperties'])
        extension = json.loads((ROOT/EXTENSION).read_bytes())
        self.assertEqual(BASE, extension['base_catalogue_sha256'])
        for entry in extension['schemas']:
            self.assertEqual(entry['sha256'], hashlib.sha256((ROOT/entry['path']).read_bytes()).hexdigest())
            self.assertEqual(entry['sha256'], catalogue.digests[entry['name']])

    def test_untrusted_extension_bytes_cannot_select_validation(self):
        with self.assertRaisesRegex(ValueError, 'Template application extension digest'):
            _merge_published_catalogue((ROOT/'contracts/catalogue.json').read_bytes(),
                (ROOT/'contracts/extensions/evaluation-results.catalogue.json').read_bytes(),
                template_application_bytes=(ROOT/EXTENSION).read_bytes()+b'\n')

    def test_human_decision_is_explicit_and_not_professional_approval(self):
        catalogue = SchemaCatalogue(ROOT)
        defs = catalogue.schemas['template-application']['$defs']
        self.assertEqual(['APPLICABLE','NOT_APPLICABLE'], defs['HumanDecisionInput']['properties']['applicability']['enum'])
        self.assertIn('reason', defs['HumanDecisionInput']['required'])
        self.assertEqual({'const':False}, defs['HumanDecisionReceipt']['properties']['professional_approval'])
        query = dict(project_id='11111111-1111-4111-8111-111111111111',
                     profile_revision='22222222-2222-4222-8222-222222222222',document_type='REGISTRATION_DIRECTORY')
        catalogue.validate('template-application',query,'FixedQuery')
        with self.assertRaises(Exception):catalogue.validate('template-applicability',query,'FixedQuery')
        with self.assertRaises(Exception):catalogue.validate('template-application',{**query,'approved':True},'FixedQuery')
