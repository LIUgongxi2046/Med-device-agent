"""N05 source-only fixtures. Not executed and not production proof."""
import copy
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock
from medical_professional_runtime.host import Binding, RuntimeBlocked, digest
from medical_professional_runtime.supervisor import canonical
from medical_professional_runtime.source_verifier_activity import SourceVerifierHost
from medical_professional_runtime.source_verifier_deployment import require_deployment
from medical_professional_runtime.source_verifier_validation import require_prompt

ID = '10000000-0000-4000-8000-000000000001'
OTHER = '10000000-0000-4000-8000-000000000002'
THIRD = '10000000-0000-4000-8000-000000000003'


def receipt():
    return dict(schema_version='1.0.0', node_kind='N05', tenant_id=ID, run_id=ID, step_id=ID, action_id=OTHER, input_sha256='a'*64, check_id=OTHER, target_kind='CANDIDATE', candidate_id=OTHER, target_revision=THIRD, target_sha256='b'*64, findings_artifact_id=OTHER, findings_receipt_sha256='c'*64, coverage_artifact_id=THIRD, coverage_receipt_sha256='d'*64, coverage_complete=False, status='WAITING_INPUT', professional_approval=False, audit_id=OTHER, event_id=THIRD, issue_proposal_ids=[])


def fixture():
    artifacts = []; objects = []
    for identity, payload in ((OTHER, {'output':{'artifact_kind':'SECTION_CANDIDATE','content':{'claim_records':[],'field_assignments':[],'attachment_bindings':[]}}}), (THIRD, {'nodes':[]})):
        raw = canonical(payload)
        ref = dict(object_key='tenants/'+ID+'/'+identity, object_version='v1', sha256=digest(raw), size_bytes=str(len(raw)), media_type='application/json')
        artifacts.append(dict(artifact_id=identity, sha256=digest(raw), payload=payload)); objects.append(dict(artifact_id=identity, object_ref=ref))
    rules = dict(schema_version='1.0.0', node_kind='N05', revision_id=OTHER, rules=[dict(rule_id=OTHER, sha256='c'*64, content={'fixture':'rule'})])
    source = dict(schema_version='1.0.0', node_kind='N05', tenant_id=ID, plan_revision=OTHER, step_id=OTHER, writer_step_id=THIRD, rule_revision=OTHER, rule_manifest_sha256=digest(canonical(rules)), required_claim_ids=[], required_rules=[dict(version_id=OTHER, content_sha256='c'*64)], candidate_artifact_id=OTHER, candidate_object=objects[0]['object_ref'], body_artifact_id=THIRD, body_object=objects[1]['object_ref'])
    content = dict(session_id=ID, rules=rules, rule_read_receipts=[dict(rule_id=OTHER, receipt_id=THIRD)], artifacts=artifacts, source_chunks=[])
    pins = dict(schema_version='1.0.0', compiler='N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1', input_artifact_id=ID, input_sha256=digest(canonical(source)), input_receipt_sha256='a'*64)
    pins['review_content'] = dict(schema_version='1.0.0', kind='N05_PHYSICAL_CONTEXT', session_id=ID, input_id=ID, input_sha256=pins['input_sha256'], input_receipt_sha256='a'*64, content_sha256=digest(canonical(content)), rules_sha256=digest(canonical(rules)), rule_authorizations_sha256='b'*64, rule_ids=[OTHER], rule_reads=[dict(rule_id=OTHER, sha256='c'*64, receipt_id=THIRD, complete=True)], objects=objects, sources=[])
    pins.update({key:{'fixture':'pin'} for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact')})
    prompt = canonical(dict(schema_version='1.0.0', node_kind='N05', instructions='Review independently; retain insufficient and conflicts.', source_verifier_input=source, review_content=content)).decode()
    pins['launch_sha256'] = digest(prompt.encode())
    return prompt, pins


class SourceVerifierTest(unittest.TestCase):
    def test_original_at07_not_approval(self):
        binding = Binding(ID, ID, ID, ID, 1, 'a'*64, 'b'*64)
        self.assertEqual(SourceVerifierHost._completion(binding, receipt()), receipt())
        for key, value in [('node_kind','N04'), ('professional_approval',True), ('status','PASS'), ('status','SUCCEEDED'), ('target_kind','DOCUMENT')]:
            changed = receipt(); changed[key] = value
            with self.assertRaises(RuntimeBlocked): SourceVerifierHost._completion(binding, changed)

    def test_exact_n05_physical_rule_context(self):
        prompt, pins = fixture()
        require_prompt(prompt, pins['launch_sha256'], pins, pins['input_sha256'])
        for field, value in [('compiler','N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'), ('input_sha256','f'*64)]:
            changed = copy.deepcopy(pins); changed[field] = value
            with self.assertRaises(RuntimeBlocked): require_prompt(prompt, pins['launch_sha256'], changed, pins['input_sha256'])
        changed = copy.deepcopy(pins); changed['review_content']['rule_reads'][0]['complete'] = False
        with self.assertRaises(RuntimeBlocked): require_prompt(prompt, pins['launch_sha256'], changed, pins['input_sha256'])

    def test_explicit_protocol(self):
        protocol = dict(schema_version='1.0.0', revision=ID, namespace='fixture', control_task_queue='control', source_verifier_task_queue='review', workflow_type='medical.task.run', activity='medical.task.source-verifier.execute.v1', execution_kind='TASK', node_kind='N05', model_tool_mode='SOURCE_VERIFIER_N05_V1')
        require_deployment(protocol, digest(canonical(protocol)), 'fixture', 'review')
        protocol['source_verifier_task_queue'] = 'control'
        with self.assertRaises(RuntimeBlocked): require_deployment(protocol, digest(canonical(protocol)), 'fixture', 'control')


class SourceVerifierRecoveryTest(unittest.IsolatedAsyncioTestCase):
    async def test_completed_original_before_expired_deadline_no_resend(self):
        binding = Binding(ID, ID, ID, ID, 1, 'a'*64, 'b'*64)
        launch = dict(state='COMPLETED', purpose='TASK', node_kind='N05', role='SourceVerifier', session_id=OTHER, binding=binding.wire(), prompt_provenance={'compiler':'N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'}, completion=receipt())
        host = SourceVerifierHost.__new__(SourceVerifierHost)
        host.control = SimpleNamespace(open_workflow=AsyncMock(return_value=launch))
        host.deployment = SimpleNamespace(jobs=Path('/fixture/not-created'))
        host.supervisor = SimpleNamespace(prepare=AsyncMock())
        host.relay = SimpleNamespace(register=lambda *_: self.fail('No new bridge for completed original'))
        reference = dict(schemaVersion='1.0.0', executionKind='TASK', tenantId=ID, runId=ID, deadlineEpochMillis=1)
        dispatch = dict(runId=ID, stepId=ID, inputSha256='a'*64, leaseGeneration=1)
        self.assertEqual(await host.execute(reference, dispatch), receipt())
        host.supervisor.prepare.assert_not_awaited()
