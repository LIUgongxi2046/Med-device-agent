import copy
import hashlib
import json
import unittest
from medical_professional_runtime.host import Binding, ProfessionalHost, RuntimeBlocked
from medical_professional_runtime.professional_activity_v2 import ProfessionalHostV2, require_deployment

ID='10000000-0000-4000-8000-000000000001'
class WaitingV2Test(unittest.TestCase):
    def setUp(self):
        self.binding=Binding(ID,ID,ID,ID,1,'a'*64,'b'*64)
        self.waiting={'schemaVersion':'2.0.0','outcome':'WAITING_CONTROL','runId':ID,'stepId':ID,'inputSha256':'a'*64,'leaseGeneration':1,'waitingReceiptId':ID,'waitingReceiptSha256':'c'*64}
        self.value={'schemaVersion':'2.0.0','kind':'WAITING_CONTROL','completion':None,'waiting':self.waiting}
    def test_true_waiting_has_no_fabricated_artifact(self):
        self.assertEqual(ProfessionalHostV2._completion(self.binding,self.value),self.value)
        with self.assertRaises(RuntimeBlocked): ProfessionalHost._completion(self.binding,self.value)
    def test_closed_union_and_pins(self):
        for field,value in [('leaseGeneration',True),('inputSha256','d'*64),('outcome','SUCCEEDED')]:
            wrong=copy.deepcopy(self.value);wrong['waiting'][field]=value
            with self.assertRaises(RuntimeBlocked): ProfessionalHostV2._completion(self.binding,wrong)
        wrong=copy.deepcopy(self.value);wrong['completion']={'resultArtifactId':ID}
        with self.assertRaises(RuntimeBlocked): ProfessionalHostV2._completion(self.binding,wrong)
    def test_protocol_is_not_selected_by_queue_only(self):
        p={'schema_version':'2.0.0','revision':ID,'namespace':'medical','control_task_queue':'control','professional_task_queue':'professional','workflow_type':'medical.task.run','professional_activity':'medical.professional.step.v2','waiting_activity':'medical.run.commit-waiting.v2','version_marker':'task-professional-outcome-v2','load_activity':'medical.run.load.v2','recovery_activity':'medical.run.recover-step.v2','recovery_poll_millis':1000,'undispatched_failure_activity':'medical.run.fail-undispatched.v2','execute_stage_activity':'medical.run.execute-stage.v2','commit_stage_activity':'medical.run.commit-stage.v2','recover_stage_activity':'medical.run.recover-stage.v2'}
        digest=hashlib.sha256(json.dumps(p,sort_keys=True,separators=(',',':')).encode()).hexdigest()
        self.assertEqual(require_deployment(p,digest,'medical','professional'),p)
        with self.assertRaises(RuntimeBlocked):require_deployment(p,'0'*64,'medical','professional')
        with self.assertRaises(RuntimeBlocked):require_deployment(p,digest,'other','professional')
