import unittest
from medical_model_gateway.formal_invocation_adapter import InvocationProfile
from medical_model_gateway.plan_draft_gateway import PlanDraftGatewayAdapter

ID='10000000-0000-4000-8000-000000000001'
class PlanDraftGatewayTest(unittest.TestCase):
    def test_dedicated_route_same_business_purpose(self):
        self.assertEqual(InvocationProfile.PLAN_DRAFT_V1.value[1],'TASK')
        self.assertEqual(InvocationProfile.PLAN_DRAFT_V1.path(ID,'begin-send'),'/internal/v1/plan-draft-invocations/'+ID+'/begin-send')
        self.assertEqual(InvocationProfile.TASK_V1.path(ID,'begin-send'),'/internal/v1/task-invocations/'+ID+'/begin-send')
    def test_task_adapter_cannot_be_relabelled(self):
        class ExistingTask:
            _profile=InvocationProfile.TASK_V1
        with self.assertRaises(ValueError):PlanDraftGatewayAdapter(ExistingTask(),ID,lambda:None)
    def test_no_caller_route_override(self):
        with self.assertRaises(ValueError):InvocationProfile.PLAN_DRAFT_V1.path(ID,'../../receipts')
