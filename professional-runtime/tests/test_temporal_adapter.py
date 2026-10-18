"""Actual installed Temporal activity context with bounded subprocess fixture host."""
from dataclasses import replace
import unittest
import inspect
from temporalio.testing import ActivityEnvironment
from temporalio import activity
from temporalio.exceptions import ApplicationError
from medical_professional_runtime.temporal_activity import ProfessionalActivities
import test_host as fixture

class TemporalAdapterTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self): self.fixture=fixture.HostTest(); self.fixture.setUp()
    def tearDown(self): self.fixture.tearDown()
    def environment(self):
        env=ActivityEnvironment()
        env.info=replace(env.info,workflow_id=self.fixture.reference['tenantId']+'/'+self.fixture.reference['runId'])
        return env
    async def test_actual_registered_name_and_two_wire_arguments(self):
        f=self.fixture; adapter=ProfessionalActivities(f.host(f.good()))
        self.assertEqual(activity._Definition.from_callable(adapter.execute).name,'medical.professional.step')
        beats=[]; env=self.environment(); env.on_heartbeat=lambda *args: beats.append(args)
        result=await env.run(adapter.execute,f.reference,f.dispatch)
        self.assertEqual(result,f.completion); self.assertTrue(beats)
    async def test_workflow_owner_cannot_be_spoofed(self):
        f=self.fixture; adapter=ProfessionalActivities(f.host(f.good()))
        with self.assertRaises(ApplicationError) as error: await ActivityEnvironment().run(adapter.execute,f.reference,f.dispatch)
        self.assertTrue(error.exception.non_retryable); self.assertFalse(f.control.events)
    async def test_revocation_has_non_retryable_safe_error(self):
        f=self.fixture; f.control.revoke=True; adapter=ProfessionalActivities(f.host(f.good()))
        with self.assertRaises(ApplicationError) as error: await self.environment().run(adapter.execute,f.reference,f.dispatch)
        self.assertEqual(error.exception.type,'AUTH_REVOKED'); self.assertTrue(error.exception.non_retryable)
    async def test_actual_installed_harness_api_compatible(self):
        from deepseek_harness import DeepSeekHarnessConfig,DeepSeekHarness
        fields=inspect.signature(DeepSeekHarnessConfig).parameters
        self.assertTrue({'runtime_cwd','dsh_home','request_timeout_seconds','shutdown_timeout_seconds','dsh_bin'}.issubset(fields))
        self.assertIn('session_id',inspect.signature(DeepSeekHarness.run).parameters)
if __name__=='__main__': unittest.main()
