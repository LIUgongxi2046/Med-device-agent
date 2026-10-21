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
    async def admit(self,binding,session):
        self.events.append('admit')
        if self.revoke: raise RuntimeBlocked('AUTH_REVOKED')
        return {'binding':binding.wire(),'session_id':session,'admission':'CURRENT','isolation_receipt_id':str(uuid4()),'supervision_sha256':'f'*64}
    async def heartbeat(self,*_):
        self.events.append('heartbeat')
        if self.revoke: raise RuntimeBlocked('AUTH_REVOKED')
    async def close_admission(self,*_): self.events.append('close')
    async def finish(self,binding,session,observation,files): self.events.append('finish'); self.observation=observation; return self.completion
    async def failed(self,*args): self.events.append('failed'); self.failure=args

class SupervisorFixture:
    """Explicit transport/inspection double with a disposable real child.
    It is not a LinuxSupervisor and cannot provide live isolation acceptance."""
    def __init__(self,runner,control,maximum):self.runner=runner;self.control=control;self.maximum=maximum;self.process=None
    async def open_bridge(self,binding,session,job):return await self.control.open_bridge(binding,session,job)
    async def prepare(self,binding,session,job,payload,environment):
        self.control.events.append('supervisor-prepare')
        process=await asyncio.create_subprocess_exec(sys.executable,'-I',str(self.runner),cwd=job/'workspace',env=environment,stdin=asyncio.subprocess.PIPE,stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.DEVNULL)
        process.payload=payload;self.process=process;return process
    async def release(self,process,response):
        if 'admit' not in self.control.events:raise RuntimeBlocked('ADMIT_MISSING')
        self.control.events.append('release');process.stdin.write(process.payload);await process.stdin.drain();process.stdin.close()
    async def current(self,process):pass
    async def wait(self,process):
        result=bytearray()
        while chunk:=await process.stdout.read(65536):
            result.extend(chunk)
            if len(result)>self.maximum:raise RuntimeBlocked('OBSERVATION_LIMIT')
        await process.wait()
        if process.returncode!=0:raise RuntimeBlocked('HARNESS_PROCESS_FAILED')
        self.control.events.append('supervisor-closed');return bytes(result)
    async def abort(self,process):
        if process.returncode is None:process.kill()
        await process.wait()
    async def abort_session(self,binding,session):
        if self.process is not None:await self.abort(self.process)

class HostTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name); self.jobs=self.root/'jobs'; self.jobs.mkdir(mode=0o700)
        ids=[str(uuid4()) for _ in range(5)]
        self.reference={'tenantId':ids[0],'runId':ids[1],'deadlineEpochMillis':int((time.time()+10)*1000)}
        self.dispatch={'runId':ids[1],'stepId':ids[2],'actionId':ids[3],'leaseGeneration':1,'inputSha256':'a'*64,'stepEnvelopeSha256':'b'*64}
        self.binding=Binding.from_workflow(self.reference,self.dispatch)
        self.job=self.jobs/ids[0]/ids[1]/ids[2]/'1'
        self.profile=self.root/'profile.json'; self.profile.write_text('{}')
        self.launch={'binding':self.binding.wire(),'state':'PREPARE','node_kind':'N02','role':'Researcher','purpose':'EVALUATION_VERIFICATION','session_id':ids[4],'runtime_manifest_sha256':'c'*64,'job_directory':str(self.job),'prompt':'Bounded fixture prompt','profile_files':[{'source':str(self.profile),'relative':'profiles/medical-agent/test.json','sha256':digest(b'{}')}], 'sdk':{'model':'APPROVED_TEST_ROUTE','reasoning_effort':None,'max_tokens':10,'initialize_timeout_seconds':1,'request_timeout_seconds':1,'shutdown_timeout_seconds':1,'dsh_bin':'/unused','sdk_version':'1.0.0','runtime_version':'1.0.0'}}
        self.launch['expected_inventory']={'plugin_names':['bounded-fixture-plugin'],'tool_schema_hashes':{'bounded-fixture-tool':'f'*64},'providers':[{'id':'enterprise-model','name':'企业模型接口'}],'models':[{'id':'APPROVED_TEST_ROUTE'}],'model_stream_schema_ref':'urn:bounded-fixture:model-stream'}
        self.launch['prompt_sha256']=digest(self.launch['prompt'].encode())
        self.launch['prompt_provenance']={'schema_version':'1.0.0','compiler':'N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1','input_artifact_id':str(uuid4()),'input_sha256':self.binding.input_sha256,'input_receipt_sha256':'9'*64,'launch_sha256':self.launch['prompt_sha256'],**{key:{'fixture':'explicit package double, not authority'} for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact')}}
        self.completion={'runId':ids[1],'stepId':ids[2],'inputSha256':'a'*64,'leaseGeneration':1,'status':'FAILED','resultArtifactId':str(uuid4()),'resultSha256':'d'*64}
        self.control=ControlFixture(self.launch,self.completion)
    def tearDown(self): self.temp.cleanup()
    def host(self,code,limit=16000):
        runner=self.root/'fixture.py'; runner.write_text(code)
        return ProfessionalHost(Deployment(Path(sys.executable),runner,digest(runner.read_bytes()),self.jobs,limit,.02,.1),self.control,SupervisorFixture(runner,self.control,limit))
    def good(self,reason='completed'):
        return "import sys,json,os\ns=json.load(sys.stdin)\nprint(json.dumps({'session_id':s['session_id'],'binding':s['binding'],'finish_reason':"+repr(reason)+",'final_response':os.environ.get('UNAUTHORIZED_PARENT_SECRET','absent')}))"
    async def test_real_child_clean_env_and_no_success_inference(self):
        os.environ['UNAUTHORIZED_PARENT_SECRET']='must-not-inherit'
        try: result=await self.host(self.good()).execute(self.reference,self.dispatch)
        finally: del os.environ['UNAUTHORIZED_PARENT_SECRET']
        self.assertEqual(result['status'],'FAILED'); self.assertEqual(self.control.observation['final_response'],'absent'); self.assertIn('finish',self.control.events)
        self.assertTrue(self.job.exists())
        self.assertLess(self.control.events.index('supervisor-prepare'),self.control.events.index('admit'))
        self.assertLess(self.control.events.index('admit'),self.control.events.index('release'))
        self.assertLess(self.control.events.index('supervisor-closed'),self.control.events.index('finish'))
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
        self.assertFalse(self.control.failure[-1]); self.assertNotIn('finish',self.control.events)
    async def test_output_bound_kills_child(self):
        with self.assertRaisesRegex(RuntimeBlocked,'OBSERVATION_LIMIT'): await self.host("import sys; sys.stdin.read(); print('x'*30000)").execute(self.reference,self.dispatch)
        self.assertNotIn('finish',self.control.events)
    async def test_lost_prepare_response_still_cleans_exact_owned_session(self):
        host=self.host('import time; time.sleep(20)');prepare=host.supervisor.prepare
        async def lost(*args):
            await prepare(*args)
            raise RuntimeBlocked('PREPARE_RESPONSE_LOST')
        host.supervisor.prepare=lost
        with self.assertRaisesRegex(RuntimeBlocked,'PREPARE_RESPONSE_LOST'):await host.execute(self.reference,self.dispatch)
        self.assertIsNotNone(host.supervisor.process.returncode)
        self.assertFalse(self.control.failure[-1])
        self.assertNotIn('finish',self.control.events)
    async def test_wrong_session_never_registers(self):
        with self.assertRaisesRegex(RuntimeBlocked,'SESSION_BINDING'): await self.host("import sys; sys.stdin.read(); print('{}')").execute(self.reference,self.dispatch)
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
    async def test_prompt_mismatch_never_starts_child(self):
        self.launch['prompt']='Changed after compiler'
        with self.assertRaisesRegex(RuntimeBlocked,'COMPILED_PROMPT_MISMATCH'):await self.host(self.good()).execute(self.reference,self.dispatch)
        self.assertNotIn('supervisor-prepare',self.control.events)
    async def test_prompt_package_provenance_required(self):
        self.launch.pop('prompt_provenance')
        with self.assertRaisesRegex(RuntimeBlocked,'COMPILED_PROMPT_REQUIRED'):await self.host(self.good()).execute(self.reference,self.dispatch)
        self.assertNotIn('supervisor-prepare',self.control.events)
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
        self.assertEqual(self.control.events[-2:],['close','failed']); self.assertFalse(self.control.failure[-1])
    async def test_n00_never_starts_model(self):
        self.launch['node_kind']='N00'
        with self.assertRaisesRegex(RuntimeBlocked,'NODE_ROLE'): await self.host(self.good()).execute(self.reference,self.dispatch)
        self.assertFalse(self.job.exists())
    async def test_verifier_cannot_run_writer_role(self):
        self.launch['node_kind']='N05'; self.launch['role']='SectionWriter'
        with self.assertRaisesRegex(RuntimeBlocked,'N05_LAUNCH_IDENTITY_MISMATCH'): await self.host(self.good()).execute(self.reference,self.dispatch)
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
