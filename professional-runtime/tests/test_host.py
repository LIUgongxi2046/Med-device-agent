"""Bounded fixture SDK child processes; not professional or model acceptance evidence."""
import asyncio
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from uuid import uuid4
from medical_professional_runtime.host import *

class BridgeFixture:
    socket=Path('/unused/socket'); token_path=Path('/unused/token'); inventory_path=Path('/unused/inventory')
    async def close(self): pass

class ControlFixture:
    def __init__(self, launch, completion): self.launch=launch; self.completion=completion; self.events=[]; self.revoke=False
    async def open_bridge(self,binding,session,job):
        bridge=BridgeFixture(); bridge.inventory_path=job/'fixture-inventory.json'; bridge.inventory_path.write_text(json.dumps({'role':self.launch['role'],'plugins':[{'name':n} for n in self.launch['expected_inventory']['plugin_names']],'tools':[{'name':n,'parameters_sha256':h} for n,h in self.launch['expected_inventory']['tool_schema_hashes'].items()],'providers':self.launch['expected_inventory']['providers'],'models':self.launch['expected_inventory']['models'],'model_stream_schema_ref':self.launch['expected_inventory']['model_stream_schema_ref'],'configuration_sha256':'e'*64})); return bridge
    async def open(self,*_): self.events.append('open'); return self.launch
    async def heartbeat(self,*_):
        self.events.append('heartbeat')
        if self.revoke: raise RuntimeBlocked('AUTH_REVOKED')
    async def close_admission(self,*_): self.events.append('close')
    async def finish(self,binding,session,observation,files): self.events.append('finish'); self.observation=observation; return self.completion
    async def failed(self,*args): self.events.append('failed'); self.failure=args

class HostTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name); self.jobs=self.root/'jobs'; self.jobs.mkdir(mode=0o700)
        ids=[str(uuid4()) for _ in range(5)]
        self.reference={'tenantId':ids[0],'runId':ids[1],'deadlineEpochMillis':int((time.time()+10)*1000)}
        self.dispatch={'runId':ids[1],'stepId':ids[2],'actionId':ids[3],'leaseGeneration':1,'inputSha256':'a'*64,'stepEnvelopeSha256':'b'*64}
        self.binding=Binding.from_workflow(self.reference,self.dispatch)
        self.job=self.jobs/ids[0]/ids[1]/ids[2]/'1'
        self.profile=self.root/'profile.json'; self.profile.write_text('{}')
        self.launch={'binding':self.binding.wire(),'state':'START','node_kind':'N04','role':'SectionWriter','purpose':'EVALUATION_VERIFICATION','session_id':ids[4],'isolation_receipt_id':str(uuid4()),'runtime_manifest_sha256':'c'*64,'job_directory':str(self.job),'prompt':'Bounded fixture prompt','profile_files':[{'source':str(self.profile),'relative':'profiles/medical-agent/test.json','sha256':digest(b'{}')}], 'sdk':{'model':'APPROVED_TEST_ROUTE','reasoning_effort':None,'max_tokens':10,'initialize_timeout_seconds':1,'request_timeout_seconds':1,'shutdown_timeout_seconds':1,'dsh_bin':'/unused','sdk_version':'1.0.0','runtime_version':'1.0.0'}}
        self.launch['expected_inventory']={'plugin_names':['bounded-fixture-plugin'],'tool_schema_hashes':{'bounded-fixture-tool':'f'*64},'providers':[{'id':'enterprise-model','name':'企业模型接口'}],'models':[{'id':'APPROVED_TEST_ROUTE'}],'model_stream_schema_ref':'urn:bounded-fixture:model-stream'}
        self.completion={'runId':ids[1],'stepId':ids[2],'inputSha256':'a'*64,'leaseGeneration':1,'status':'FAILED','resultArtifactId':str(uuid4()),'resultSha256':'d'*64}
        self.control=ControlFixture(self.launch,self.completion)
    def tearDown(self): self.temp.cleanup()
    def host(self,code,limit=16000):
        runner=self.root/'fixture.py'; runner.write_text(code)
        return ProfessionalHost(Deployment(Path(sys.executable),runner,digest(runner.read_bytes()),self.jobs,limit,.02,.1),self.control)
    def good(self,reason='completed'):
        return "import sys,json,os\ns=json.load(sys.stdin)\nprint(json.dumps({'session_id':s['session_id'],'binding':s['binding'],'finish_reason':"+repr(reason)+",'final_response':os.environ.get('UNAUTHORIZED_PARENT_SECRET','absent')}))"
    async def test_real_child_clean_env_and_no_success_inference(self):
        os.environ['UNAUTHORIZED_PARENT_SECRET']='must-not-inherit'
        try: result=await self.host(self.good()).execute(self.reference,self.dispatch)
        finally: del os.environ['UNAUTHORIZED_PARENT_SECRET']
        self.assertEqual(result['status'],'FAILED'); self.assertEqual(self.control.observation['final_response'],'absent'); self.assertIn('finish',self.control.events)
        self.assertTrue(self.job.exists())
    async def test_original_completion_does_not_spawn(self):
        self.launch.update(state='COMPLETED',completion=self.completion)
        result=await self.host('raise Exception()').execute(self.reference,self.dispatch)
        self.assertEqual(result,self.completion); self.assertFalse(self.job.exists())
    async def test_revoked_lease_closes_before_failure(self):
        self.control.revoke=True
        with self.assertRaisesRegex(RuntimeBlocked,'AUTH_REVOKED'): await self.host(self.good()).execute(self.reference,self.dispatch)
        self.assertEqual(self.control.events[-2:],['close','failed']); self.assertNotIn('finish',self.control.events)
    async def test_deadline_kills_real_sleeping_child(self):
        self.reference['deadlineEpochMillis']=int((time.time()+.15)*1000)
        with self.assertRaisesRegex(RuntimeBlocked,'DEADLINE'): await self.host('import time; time.sleep(20)').execute(self.reference,self.dispatch)
        self.assertTrue(self.control.failure[-1]); self.assertNotIn('finish',self.control.events)
    async def test_output_bound_kills_child(self):
        with self.assertRaisesRegex(RuntimeBlocked,'OBSERVATION_LIMIT'): await self.host("print('x'*30000)").execute(self.reference,self.dispatch)
        self.assertNotIn('finish',self.control.events)
    async def test_wrong_session_never_registers(self):
        with self.assertRaisesRegex(RuntimeBlocked,'SESSION_BINDING'): await self.host("print('{}')").execute(self.reference,self.dispatch)
    async def test_stale_backend_completion_rejected(self):
        self.completion['leaseGeneration']=2
        with self.assertRaisesRegex(RuntimeBlocked,'COMPLETION'): await self.host(self.good()).execute(self.reference,self.dispatch)
    async def test_max_tokens_not_promoted_to_success(self):
        await self.host(self.good('max-tokens')).execute(self.reference,self.dispatch)
        self.assertEqual(self.control.observation['finish_reason'],'max-tokens')
    async def test_model_verification_rejected(self):
        self.launch['purpose']='MODEL_VERIFICATION'
        with self.assertRaisesRegex(RuntimeBlocked,'LAUNCH_BINDING'): await self.host(self.good()).execute(self.reference,self.dispatch)
    async def test_simulation_route_rejected(self):
        self.launch['sdk']['model']='SIMULATION'
        with self.assertRaisesRegex(RuntimeBlocked,'UNAPPROVED'): await self.host(self.good()).execute(self.reference,self.dispatch)
    async def test_profile_traversal_rejected(self):
        self.launch['profile_files'][0]['relative']='../outside'
        with self.assertRaisesRegex(RuntimeBlocked,'PROFILE_PATH'): await self.host(self.good()).execute(self.reference,self.dispatch)
    async def test_runner_hash_rejected(self):
        runner=self.root/'runner.py'; runner.write_text('')
        with self.assertRaisesRegex(RuntimeBlocked,'HASH'): ProfessionalHost(Deployment(Path(sys.executable),runner,'f'*64,self.jobs),self.control)
    async def test_unknown_finish_rejected(self):
        with self.assertRaisesRegex(RuntimeBlocked,'TERMINAL'): await self.host(self.good('invented')).execute(self.reference,self.dispatch)
    async def test_cancel_cleans_owned_process(self):
        task=asyncio.create_task(self.host('import time; time.sleep(20)').execute(self.reference,self.dispatch))
        await asyncio.sleep(.1); task.cancel()
        with self.assertRaises(asyncio.CancelledError): await task
        self.assertEqual(self.control.events[-2:],['close','failed']); self.assertTrue(self.control.failure[-1])
    async def test_n00_never_starts_model(self):
        self.launch['node_kind']='N00'
        with self.assertRaisesRegex(RuntimeBlocked,'NODE_ROLE'): await self.host(self.good()).execute(self.reference,self.dispatch)
        self.assertFalse(self.job.exists())
    async def test_verifier_cannot_run_writer_role(self):
        self.launch['node_kind']='N05'
        with self.assertRaisesRegex(RuntimeBlocked,'NODE_ROLE'): await self.host(self.good()).execute(self.reference,self.dispatch)
    async def test_duplicate_local_attempt_does_not_cancel_original(self):
        host=self.host(self.good()); await host.execute(self.reference,self.dispatch)
        self.control.events=[]
        with self.assertRaisesRegex(RuntimeBlocked,'ORIGINAL_SESSION'): await host.execute(self.reference,self.dispatch)
        self.assertNotIn('close',self.control.events); self.assertNotIn('failed',self.control.events)
    def test_duplicate_json(self):
        with self.assertRaises(RuntimeBlocked): strict_json(b'{"id":1,"id":2}')
    def test_binding_generation_bool(self):
        self.dispatch['leaseGeneration']=True
        with self.assertRaises(RuntimeBlocked): Binding.from_workflow(self.reference,self.dispatch)
if __name__=='__main__': unittest.main()
