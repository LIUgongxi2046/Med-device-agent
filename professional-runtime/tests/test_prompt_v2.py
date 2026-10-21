"""Offline V2 wire integrity tests, not registered artifacts or execution authority."""
import base64
from copy import deepcopy
from dataclasses import replace
import unittest
from uuid import uuid4

from medical_professional_runtime.host import RuntimeBlocked, digest, require_prompt, strict_json
from medical_professional_runtime.supervisor import canonical
import test_supervisor_plan as plan_tests

COMPILER='N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_PREDECESSOR_EVIDENCE_V2'


def fixture():
    tenant=str(uuid4());step=str(uuid4());source=str(uuid4());rights=str(uuid4())
    bundle={key:[] for key in ('retrieval_ids','question_coverage','selected_refs','conflict_refs','missing_requirement_ids','read_receipt_ids')}
    predecessor={'step_id':str(uuid4()),'result_artifact_id':str(uuid4()),'result_sha256':'a'*64,'result_object_version':'result-original','result_receipt_sha256':'b'*64,'evidence_artifact_id':str(uuid4()),'evidence_sha256':digest(canonical(bundle)),'evidence_object_version':'evidence-original','evidence_receipt_sha256':'c'*64,'completion_receipt_sha256':'d'*64}
    node={'schema_version':'1.0.0','node_kind':'N02','tenant_id':tenant,'step_id':step,'predecessors':[deepcopy(predecessor)]}
    document={'schema_version':'2.0.0','node_kind':'N02','instructions':'Exact registered evidence only. ${literal}','task_node_input':node,'predecessor_evidence':[{'predecessor':deepcopy(predecessor),'evidence_bundle':bundle}]}
    text=canonical(document).decode();sha=digest(text.encode())
    def package(version,kind):
        return {'artifact':{'version_id':str(uuid4()),'content_sha256':'f'*64},'object_ref':{'objectKey':'fixture','objectVersion':'original','sha256':'f'*64,'sizeBytes':'2','mediaType':'application/json'},'schema_ref':f'urn:medical:task-professional-package:{version}#/$defs/{kind}Package','schema_sha256':'9'*64,'authorization_sha256':'8'*64,'content_members':[]}
    provenance={'schema_version':'2.0.0','compiler':COMPILER,'input_artifact_id':str(uuid4()),'input_sha256':digest(canonical(node)),'input_receipt_sha256':'1'*64,'launch_sha256':sha,'role_basis':{'fixture':'explicit data double'},'prompt_basis':{'fixture':'explicit data double'},'role_package':package('1.0.0','Role'),'prompt_package':package('2.0.0','Prompt'),'profile_package':package('2.0.0','Profile'),'adapter_artifact':{'fixture':'explicit data double'},'predecessor_evidence':[{'predecessor':deepcopy(predecessor),'object_ref':{'objectKey':'tenants/'+tenant+'/evidence','objectVersion':'evidence-original','sha256':predecessor['evidence_sha256'],'sizeBytes':str(len(canonical(bundle))),'mediaType':'application/json'},'schema_ref':'https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/EvidenceBundle','schema_sha256':'7'*64,'content_roots':[source],'content_members':[{'upstream_version_id':source,'rights_revision':rights}]}]}
    return text,sha,provenance


class PromptV2Test(unittest.TestCase):
    def setUp(self):self.prompt,self.sha,self.provenance=fixture()
    def check(self):require_prompt(self.prompt,self.sha,self.provenance,self.provenance['input_sha256'])
    def document(self,mutate):
        value=strict_json(self.prompt);mutate(value);self.prompt=canonical(value).decode();self.sha=digest(self.prompt.encode());self.provenance['launch_sha256']=self.sha
    def test_explicit_v2_is_accepted_without_claiming_registration_authority(self):self.check()
    def test_v1_default_is_unchanged_and_cannot_silently_consume_v2(self):
        self.provenance['compiler']='N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_unknown_or_missing_top_level_provenance_is_denied(self):
        for key in list(self.provenance):
            with self.subTest(key=key):
                copy=deepcopy(self.provenance);copy.pop(key)
                with self.assertRaises(RuntimeBlocked):require_prompt(self.prompt,self.sha,copy,self.provenance['input_sha256'])
        self.provenance['caller_approved']=True
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_v2_requires_v2_prompt_and_profile_but_original_v1_role(self):
        for key in ('prompt_package','profile_package'):
            with self.subTest(key=key):
                copy=deepcopy(self.provenance);copy[key]['schema_ref']=copy[key]['schema_ref'].replace('2.0.0','1.0.0')
                with self.assertRaises(RuntimeBlocked):require_prompt(self.prompt,self.sha,copy,copy['input_sha256'])
        self.provenance['role_package']['schema_ref']=self.provenance['role_package']['schema_ref'].replace('1.0.0','2.0.0')
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_mixed_package_hashes_are_denied(self):
        self.provenance['profile_package']['schema_sha256']='0'*64
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_missing_extra_or_reordered_evidence_is_denied(self):
        self.document(lambda d:d['predecessor_evidence'].clear())
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_changed_origin_receipt_cannot_reuse_same_input_predecessor(self):
        self.provenance['predecessor_evidence'][0]['predecessor']['completion_receipt_sha256']='0'*64
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_object_version_digest_and_cross_tenant_key_are_denied(self):
        for key,value in [('objectVersion','new-version'),('sha256','0'*64),('objectKey','tenants/another-tenant/evidence'),('mediaType','text/plain')]:
            with self.subTest(key=key):
                copy=deepcopy(self.provenance);copy['predecessor_evidence'][0]['object_ref'][key]=value
                with self.assertRaises(RuntimeBlocked):require_prompt(self.prompt,self.sha,copy,copy['input_sha256'])
    def test_unknown_sdk_claim_in_evidence_is_denied(self):
        self.document(lambda d:d['predecessor_evidence'][0].update({'sdk_verified':True}))
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_raw_answer_is_not_an_evidence_bundle(self):
        self.document(lambda d:d['predecessor_evidence'][0].update({'evidence_bundle':{'answer':'unregistered'}}))
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_missing_rights_revision_is_denied(self):
        del self.provenance['predecessor_evidence'][0]['content_members'][0]['rights_revision']
        with self.assertRaises(RuntimeBlocked):self.check()
    def test_prompt_byte_mutation_is_denied_before_semantic_parsing(self):
        self.prompt+=' '
        with self.assertRaises(RuntimeBlocked):self.check()


class V2SupervisorPlanTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        plan_tests.PlanLoaderTest.setUp(self)
        prompt,sha,pins=fixture();self.binding['input_sha256']=pins['input_sha256']
        self.launch.update(prompt=prompt,prompt_sha256=sha,prompt_provenance=pins)
        envelope=strict_json(self.envelope);envelope.update(input_sha256=pins['input_sha256'],compiled_prompt=prompt,prompt_sha256=sha,prompt_provenance=pins)
        self.envelope=canonical(envelope);self.binding['step_envelope_sha256']=digest(self.envelope)
        self.reply['registered_envelope_base64']=base64.b64encode(self.envelope).decode();self.reply['object_ref'].update(sha256=digest(self.envelope),sizeBytes=str(len(self.envelope)))
    async def load(self):return await self.loader.load(self.session,self.binding,self.socket_root)
    async def test_v2_exact_registered_envelope_reaches_independent_supervisor_plan(self):
        plan=await self.load();self.assertEqual(strict_json(plan.payload)['prompt_provenance'],self.launch['prompt_provenance']);await self.loader.require_current(plan)
    async def test_rehashed_raw_sdk_content_cannot_replace_registered_envelope(self):
        document=strict_json(self.launch['prompt']);document['instructions']='SDK override';self.launch['prompt']=canonical(document).decode();self.launch['prompt_sha256']=digest(self.launch['prompt'].encode());self.launch['prompt_provenance']['launch_sha256']=self.launch['prompt_sha256']
        with self.assertRaisesRegex(RuntimeBlocked,'REGISTERED_ENVELOPE_MISMATCH'):await self.load()
    async def test_changed_original_predecessor_pin_blocks_before_slot_reservation(self):
        self.launch['prompt_provenance']['predecessor_evidence'][0]['object_ref']['objectVersion']='replacement'
        with self.assertRaises(RuntimeBlocked):await self.load()
        self.assertEqual(self.loader.assigned,{})
