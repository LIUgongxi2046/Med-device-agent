import copy
import unittest
from medical_professional_runtime.host import RuntimeBlocked, digest
from medical_professional_runtime.supervisor import canonical
from medical_professional_runtime.plan_draft_validation import require_prompt

ID='10000000-0000-4000-8000-000000000001'
class PlanDraftValidationTest(unittest.TestCase):
    def setUp(self):
        self.source={'schema_version':'1.0.0','execution_kind':'SYSTEM_JOB','job_type':'PLAN_DRAFT','node_kind':'N01','plan_revision':None}
    def compiled(self,source):
        prompt=canonical({'schema_version':'1.0.0','node_kind':'N01','instructions':'Generate a draft only.','plan_draft_input':source}).decode()
        pins={'schema_version':'1.0.0','compiler':'N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1','input_artifact_id':ID,'input_sha256':digest(canonical(source)),'input_receipt_sha256':'a'*64,'launch_sha256':digest(prompt.encode())}
        pins.update({k:{'actual':'pin'} for k in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact')})
        return prompt,pins
    def test_exact_n01_input_and_null_plan(self):
        prompt,pins=self.compiled(self.source)
        require_prompt(prompt,pins['launch_sha256'],pins,pins['input_sha256'])
    def test_task_or_fabricated_plan_cannot_be_relabelled(self):
        for key,value in [('execution_kind','TASK'),('job_type','MODEL_VERIFICATION'),('node_kind','N02'),('plan_revision',ID)]:
            source=copy.deepcopy(self.source);source[key]=value;prompt,pins=self.compiled(source)
            with self.assertRaises(RuntimeBlocked):require_prompt(prompt,pins['launch_sha256'],pins,pins['input_sha256'])
    def test_mutated_registered_input_is_rejected(self):
        prompt,pins=self.compiled(self.source)
        with self.assertRaises(RuntimeBlocked):require_prompt(prompt,pins['launch_sha256'],pins,'b'*64)
    def test_closed_provenance(self):
        prompt,pins=self.compiled(self.source);pins['grant']='synthetic'
        with self.assertRaises(RuntimeBlocked):require_prompt(prompt,pins['launch_sha256'],pins,pins['input_sha256'])
    def test_dedicated_completion_requires_actual_waiting_receipt(self):
        from medical_professional_runtime.plan_draft_activity import PlanDraftHost
        from medical_professional_runtime.host import Binding
        binding=Binding(ID,ID,ID,ID,1,'a'*64,'b'*64)
        receipt={'schemaVersion':'1.0.0','runId':ID,'stepId':ID,'inputSha256':'a'*64,'receiptId':ID,'receiptSha256':'c'*64,'state':'WAITING_INPUT','candidateArtifactId':ID,'waitingItemId':ID}
        self.assertEqual(PlanDraftHost._completion(binding,receipt),receipt)
        receipt['state']='SUCCEEDED'
        with self.assertRaises(RuntimeBlocked):PlanDraftHost._completion(binding,receipt)
