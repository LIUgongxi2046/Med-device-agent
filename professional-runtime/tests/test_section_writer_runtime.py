"""N04 source-only tests. Fixtures are explicit, not production evidence."""
import base64
import copy
from datetime import datetime, timedelta, timezone
from pathlib import Path
import unittest
from unittest.mock import AsyncMock
from medical_professional_runtime.host import Binding, ProfessionalHost, RuntimeBlocked, digest
from medical_professional_runtime.supervisor import canonical
from medical_professional_runtime.section_writer_validation import require_prompt
from medical_professional_runtime.section_writer_activity import SectionWriterHost
from medical_professional_runtime.section_writer_supervisor import SectionWriterBackendPlanLoader
from medical_professional_runtime.section_writer_deployment import require_deployment

ID = '10000000-0000-4000-8000-000000000001'
OTHER = '10000000-0000-4000-8000-000000000002'


def fixture():
    source = {'schema_version':'1.0.0','node_kind':'N04','tenant_id':ID,'plan_revision':OTHER}
    artifact = {'fixture':'physical baseline content'}
    raw = canonical(artifact)
    ref = {'object_key':'tenants/'+ID+'/baseline','object_version':'original-v1','sha256':digest(raw),'size_bytes':str(len(raw)),'media_type':'application/json'}
    content = {'artifacts':[{'artifact_id':OTHER,'sha256':digest(raw),'payload':artifact}],'source_chunks':[]}
    pins = {'schema_version':'1.0.0','compiler':'N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1','input_artifact_id':ID,'input_sha256':digest(canonical(source)),'input_receipt_sha256':'a'*64}
    pins['writer_content'] = {'schema_version':'1.0.0','kind':'N04_PHYSICAL_CONTEXT','input_id':ID,'input_sha256':pins['input_sha256'],'input_receipt_sha256':'a'*64,'content_sha256':digest(canonical(content)),'objects':[{'artifact_id':OTHER,'object_ref':ref}],'sources':[]}
    pins.update({key:{'fixture':'package pin'} for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact')})
    prompt = canonical({'schema_version':'1.0.0','node_kind':'N04','instructions':'Write assigned sections only.','section_writer_input':source,'writer_content':content}).decode()
    pins['launch_sha256'] = digest(prompt.encode())
    return prompt, pins


class WriterValidationTest(unittest.TestCase):
    def test_exact_separate_prompt(self):
        prompt, pins = fixture()
        require_prompt(prompt, pins['launch_sha256'], pins, pins['input_sha256'])

    def test_rejects_relabelled_task_prompt(self):
        prompt, pins = fixture()
        for compiler in ('N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1','N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'):
            changed = copy.deepcopy(pins); changed['compiler'] = compiler
            with self.assertRaises(RuntimeBlocked):
                require_prompt(prompt, pins['launch_sha256'], changed, pins['input_sha256'])

    def test_companion_pins_cannot_replace_physical_content(self):
        prompt, pins = fixture()
        pins['writer_content']['objects'][0]['object_ref']['sha256'] = 'f'*64
        with self.assertRaises(RuntimeBlocked):
            require_prompt(prompt, pins['launch_sha256'], pins, pins['input_sha256'])

    def test_content_hash_does_not_authorize_rewritten_input(self):
        prompt, pins = fixture()
        with self.assertRaises(RuntimeBlocked):
            require_prompt(prompt, pins['launch_sha256'], pins, 'f'*64)

    def test_at06_is_candidate_not_task_success(self):
        binding = Binding(ID, ID, ID, ID, 1, 'a'*64, 'b'*64)
        receipt = {'schema_version':'1.0.0','candidate_id':OTHER,'status':'READY','revision':'1','tenant_id':ID,'run_id':ID,'step_id':ID,'action_id':OTHER,'input_sha256':'a'*64,'candidate_artifact_id':OTHER,'candidate_receipt_sha256':'c'*64,'body_artifact_id':OTHER,'body_sha256':'d'*64,'base_document_revision':OTHER,'audit_id':OTHER,'event_id':OTHER}
        self.assertEqual(SectionWriterHost._completion(binding, receipt), receipt)
        for key, value in [('status','SUCCEEDED'),('tenant_id',OTHER),('revision','2'),('input_sha256','f'*64)]:
            changed = dict(receipt); changed[key] = value
            with self.assertRaises(RuntimeBlocked): SectionWriterHost._completion(binding, changed)

    def test_explicit_deployment_guard(self):
        protocol = {'schema_version':'1.0.0','revision':ID,'namespace':'fixture','control_task_queue':'control','section_writer_task_queue':'writer','workflow_type':'medical.task.run','activity':'medical.task.section-writer.execute.v1','execution_kind':'TASK','node_kind':'N04','model_tool_mode':'SECTION_WRITER_N04_V1'}
        expected = digest(canonical(protocol))
        self.assertEqual(require_deployment(protocol, expected, 'fixture', 'writer'), protocol)
        for key, value in [('node_kind','N02'),('model_tool_mode','PLAN_DRAFT_N01_V1'),('control_task_queue','writer')]:
            changed = dict(protocol); changed[key] = value
            with self.assertRaises(RuntimeBlocked): require_deployment(changed, digest(canonical(changed)), 'fixture', 'writer')


class WriterPlanTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        prompt, pins = fixture()
        envelope = {'tenant_id':ID,'run_id':ID,'step_id':ID,'action_id':ID,'lease_generation':1,'input_sha256':pins['input_sha256'],'session_id':OTHER,'execution_kind':'TASK','node_kind':'N04','plan_revision':ID,'compiled_prompt':prompt,'prompt_sha256':pins['launch_sha256'],'prompt_provenance':pins}
        self.raw = canonical(envelope)
        self.binding = Binding(ID,ID,ID,ID,1,pins['input_sha256'],digest(self.raw))
        self.launch = {'state':'PREPARE','purpose':'TASK','node_kind':'N04','role':'SectionWriter','session_id':OTHER,'binding':self.binding.wire(),'runtime_manifest_sha256':'b'*64,'expected_inventory':{'plugin_names':['fixture'],'tool_schema_hashes':{},'providers':[{'id':'enterprise-model','name':'企业模型接口'}],'models':['fixture']},'sdk':{'model':'fixture','reasoning_effort':None,'max_tokens':10,'initialize_timeout_seconds':10,'request_timeout_seconds':10,'shutdown_timeout_seconds':3,'dsh_bin':'/fixture/runtime','sdk_version':'1.0.0','runtime_version':'1.0.0'},'prompt':prompt,'prompt_sha256':pins['launch_sha256'],'prompt_provenance':pins,'profile_files':[{'fixture':True}]}
        self.reply = {'binding':self.binding.wire(),'session_id':OTHER,'prepared_launch':self.launch,'prepared_envelope_base64':base64.b64encode(self.raw).decode(),'preparation_receipt':{'session_id':OTHER,'envelope_sha256':digest(self.raw),'launch_sha256':digest(canonical(self.launch))},'expires_at':(datetime.now(timezone.utc)+timedelta(seconds=4)).isoformat()}
        self.loader = SectionWriterBackendPlanLoader.__new__(SectionWriterBackendPlanLoader)
        self.loader.transport = type('FixtureTransport', (), {'maximum':1048576,'request':AsyncMock(return_value=canonical(self.reply))})()

    async def test_exact_prepared_ledger_not_object_registration(self):
        reply, envelope = await self.loader._read(self.binding, OTHER)
        self.assertEqual(envelope, self.raw)
        self.assertEqual(reply, self.reply)
        self.loader.transport.request.assert_awaited_once_with('POST','/internal/v1/section-writer-runtime/supervisor-plan',{'binding':self.binding.wire(),'session_id':OTHER})

    async def test_n02_registration_fields_rejected(self):
        self.reply['registered_envelope_base64'] = self.reply.pop('prepared_envelope_base64')
        self.loader.transport.request.return_value = canonical(self.reply)
        with self.assertRaises(RuntimeBlocked): await self.loader._read(self.binding, OTHER)

    async def test_mutated_preparation_receipt_rejected(self):
        self.reply['preparation_receipt']['launch_sha256'] = 'f'*64
        self.loader.transport.request.return_value = canonical(self.reply)
        with self.assertRaises(RuntimeBlocked): await self.loader._read(self.binding, OTHER)

    def test_writer_rejects_evaluation_purpose(self):
        self.launch['purpose'] = 'EVALUATION_VERIFICATION'
        with self.assertRaisesRegex(RuntimeBlocked, 'N04_LAUNCH_IDENTITY_MISMATCH'):
            ProfessionalHost._validate_launch(self.binding, self.launch)

    def test_writer_rejects_researcher_compiler(self):
        self.launch['prompt_provenance']['compiler'] = 'N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'
        with self.assertRaisesRegex(RuntimeBlocked, 'N04_LAUNCH_IDENTITY_MISMATCH'):
            ProfessionalHost._validate_launch(self.binding, self.launch)

    def test_tool_less_only_for_real_writer(self):
        ProfessionalHost._validate_launch(self.binding, self.launch)
        self.launch['expected_inventory']['tool_schema_hashes'] = {'AT-06':'f'*64}
        with self.assertRaises(RuntimeBlocked): ProfessionalHost._validate_launch(self.binding, self.launch)


class WriterRecoveryTest(unittest.IsolatedAsyncioTestCase):
    async def test_original_candidate_recovery_after_deadline_never_prepares_process(self):
        from types import SimpleNamespace
        binding = Binding(ID, ID, ID, ID, 1, 'a'*64, 'b'*64)
        receipt = {'schema_version':'1.0.0','candidate_id':OTHER,'status':'READY','revision':'1','tenant_id':ID,'run_id':ID,'step_id':ID,'action_id':OTHER,'input_sha256':'a'*64,'candidate_artifact_id':OTHER,'candidate_receipt_sha256':'c'*64,'body_artifact_id':OTHER,'body_sha256':'d'*64,'base_document_revision':OTHER,'audit_id':OTHER,'event_id':OTHER}
        launch = {'state':'COMPLETED','purpose':'TASK','node_kind':'N04','role':'SectionWriter','session_id':OTHER,'binding':binding.wire(),'prompt_provenance':{'compiler':'N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'},'completion':receipt}
        host = SectionWriterHost.__new__(SectionWriterHost)
        host.control = SimpleNamespace(open_workflow=AsyncMock(return_value=launch))
        host.deployment = SimpleNamespace(jobs=Path('/fixture/never-created'))
        host.supervisor = SimpleNamespace(prepare=AsyncMock(), open_bridge=AsyncMock())
        host.relay = SimpleNamespace(register=lambda *_: self.fail('Completed recovery must not bind a new bridge'))
        reference = {'schemaVersion':'1.0.0','executionKind':'TASK','tenantId':ID,'runId':ID,'deadlineEpochMillis':1}
        dispatch = {'runId':ID,'stepId':ID,'inputSha256':'a'*64,'leaseGeneration':1}
        self.assertEqual(await host.execute(reference, dispatch), receipt)
        host.control.open_workflow.assert_awaited_once_with(reference, dispatch)
        host.supervisor.prepare.assert_not_awaited(); host.supervisor.open_bridge.assert_not_awaited()

    async def test_current_prepare_does_not_ignore_deadline(self):
        from types import SimpleNamespace
        host = SectionWriterHost.__new__(SectionWriterHost)
        host.control = SimpleNamespace(open_workflow=AsyncMock(return_value={
            'binding':Binding(ID,ID,ID,ID,1,'a'*64,'b'*64).wire(), 'state':'PREPARE'}))
        # Avoid claiming a full admitted fixture here; the test isolates ordering.
        host._validate_launch = lambda *_: None
        host.relay = SimpleNamespace(register=lambda *_: self.fail('Expired launch must not register'))
        reference = {'schemaVersion':'1.0.0','executionKind':'TASK','tenantId':ID,'runId':ID,'deadlineEpochMillis':1}
        dispatch = {'runId':ID,'stepId':ID,'inputSha256':'a'*64,'leaseGeneration':1}
        with self.assertRaisesRegex(RuntimeBlocked, 'N04_DEADLINE_EXCEEDED'):
            await host.execute(reference, dispatch)
