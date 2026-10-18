"""Pinned publication checks; these do not constitute an integrated execution run."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import unittest
from medical_contracts.catalogue import SchemaCatalogue, _merge_published_catalogue

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = 'contracts/extensions/simulation-execution.catalogue.json'

class SimulationContractPublicationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = SchemaCatalogue(ROOT)
        cls.extension = json.loads((ROOT / EXTENSION).read_bytes())

    def test_pinned_entries_match_source_and_frozen_publications(self):
        for path, digest in {
            'contracts/catalogue.json':'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915',
            'contracts/extensions/evaluation-results.catalogue.json':'a1b3dce03c553d0e9166d76c30e5af287c3bb06dfe90c9cc696632a1663c891d',
        }.items():
            self.assertEqual(hashlib.sha256((ROOT/path).read_bytes()).hexdigest(),digest)
        for entry in self.extension['schemas']:
            self.assertEqual(hashlib.sha256((ROOT/entry['path']).read_bytes()).hexdigest(),entry['sha256'])
            self.assertEqual(self.catalogue.digests[entry['name']],entry['sha256'])
        self.assertEqual((ROOT/'model-gateway/src/medical_model_gateway/simulation-execution.schema.json').read_bytes(),
                         (ROOT/'business/medical-persistence/src/main/resources/model-simulation-provenance.schema.json').read_bytes())

    def test_new_extension_raw_bytes_cannot_change_trust(self):
        with self.assertRaisesRegex(ValueError,'扩展清单摘要'):
            _merge_published_catalogue((ROOT/'contracts/catalogue.json').read_bytes(),
                (ROOT/'contracts/extensions/evaluation-results.catalogue.json').read_bytes(),
                (ROOT/EXTENSION).read_bytes()+b'\n')

    def test_counter_is_byte_measurement_not_token_or_financial_authority(self):
        value={'unit':'CANONICAL_UTF8_BYTE','revision':'LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1',
               'implementation_sha256':'a'*64,'measurement_schema_sha256':'b'*64}
        self.catalogue.validate('simulation-execution',value,'CounterIdentity')
        for field,changed in [('unit','TOKEN'),('revision','UNVERIFIED')]:
            invalid={**value,field:changed}
            with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',invalid,'CounterIdentity')
        with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',{**value,'approved':True},'CounterIdentity')

    def test_snapshot_requires_canonical_ids_and_explicit_nonproduction(self):
        ident='11111111-1111-4111-8111-111111111111'
        counter={'unit':'CANONICAL_UTF8_BYTE','revision':'LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1','implementation_sha256':'a'*64,'measurement_schema_sha256':'b'*64}
        quota={'account_id':ident,'maximum_units':100,'maximum_calls':1,'spent_units':0,'reserved_units':0,'spent_calls':0,'reserved_calls':0}
        value={'schema_version':'1.0.0','scope':'PURE_SIMULATION_POLICY_COMPONENT','tenant_id':ident,'parent_run_id':ident,'counter':counter,'enterprise_quota':quota,'parent_quota':quota,'revision':1,'invocations':[],'production_eligible':False}
        self.catalogue.validate('simulation-execution',value,'SimulationSnapshot')
        for field, replacement in [('tenant_id','not-a-uuid'),('parent_run_id','FFFFFFFF-FFFF-4FFF-8FFF-FFFFFFFFFFFF'),('production_eligible',True),('revision',9007199254740992)]:
            with self.assertRaises(Exception): self.catalogue.validate('simulation-execution',{**value,field:replacement},'SimulationSnapshot')
        del value['production_eligible']
        with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',value,'SimulationSnapshot')

    def test_every_authority_field_is_required_and_has_no_approval_boolean(self):
        schema=self.catalogue.schemas['simulation-execution']['$defs']['SimulationExecutionAuthorityProof']
        self.assertEqual(set(schema['properties']),set(schema['required']))
        self.assertFalse(schema['additionalProperties'])
        self.assertEqual(schema['properties']['action'],{'const':'model.simulation.execute'})
        self.assertEqual(schema['properties']['purpose'],{'const':'SIMULATION_EVALUATION'})
        self.assertNotIn('approved',schema['properties'])
        self.assertTrue({'token_jti','agent_version_id','evaluation_definition_sha256'}.issubset(schema['required']))
        admission=self.catalogue.schemas['simulation-execution']['$defs']['SimulationInvocationAdmission']
        self.assertTrue({'exact_input','request_parameters','simulation_case','settings'}.issubset(admission['required']))

    def test_context_has_exact_source_roles_object_identity_and_prompt_binding(self):
        value=json.loads((ROOT/'contracts/fixtures/simulation-context.synthetic.json').read_bytes())
        original=self.catalogue.bind('simulation-execution',value,'SimulationContextManifest').canonical_bytes
        for field in ['prompt_revision','dependency_access_set_id','exact_input_ref','source_manifest']:
            invalid=deepcopy(value); del invalid[field]
            with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',invalid,'SimulationContextManifest')
        for mutate in [lambda v:v['source_manifest'].reverse(),lambda v:v['source_manifest'][0].update(purpose='TASK'),
                       lambda v:v.update(production_eligible=True),lambda v:v.update(approved=True)]:
            invalid=deepcopy(value);mutate(invalid)
            with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',invalid,'SimulationContextManifest')
        # Content identity is canonical full content, never an echoed hash label.
        changed=deepcopy(value);changed['prompt_revision']='22222222-2222-4222-8222-222222222222'
        rebound=self.catalogue.bind('simulation-execution',changed,'SimulationContextManifest').canonical_bytes
        self.assertNotEqual(hashlib.sha256(original).hexdigest(),hashlib.sha256(rebound).hexdigest())

    def test_candidate_dependency_manifest_is_explicit_versioned_and_closed(self):
        value={'schema_version':'1.0.0','manifest_kind':'SIMULATION_CANDIDATE_DEPENDENCIES','dependencies':[
            {'version_id':f'{n:08d}-1111-4111-8111-111111111111','content_sha256':str(n)*64} for n in [1,2,3]]}
        self.catalogue.validate('simulation-execution',value,'SimulationCandidateDependencyManifest')
        for mutate in [lambda v:v.pop('manifest_kind'),lambda v:v.update(schema_version='2.0.0'),
                lambda v:v.update(approved=True),lambda v:v['dependencies'].pop(),
                lambda v:v['dependencies'].append(deepcopy(v['dependencies'][0])),
                lambda v:v['dependencies'][0].update(url='https://unregistered.invalid')]:
            invalid=deepcopy(value);mutate(invalid)
            with self.assertRaises(Exception):self.catalogue.validate('simulation-execution',invalid,'SimulationCandidateDependencyManifest')
