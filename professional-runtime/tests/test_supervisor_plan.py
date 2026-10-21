"""Independent loader orchestration with explicit backend/inspection doubles.

Reads only self-created fixture files. No sockets, kernel inspection, process
launch, actual credentials, object store or live isolation are used.
"""
import base64
from copy import deepcopy
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from medical_professional_runtime.host import RuntimeBlocked, digest, strict_json
from medical_professional_runtime.supervisor import canonical
from medical_professional_runtime.supervisor_plan import BackendPlanLoader, OperatorSlot, binding_from_wire
from test_supervisor import make_fixture


class BackendDouble:
    maximum=1024*1024
    def __init__(self,reply):self.reply=reply;self.calls=[]
    async def request(self,method,path,body):
        self.calls.append((method,path,deepcopy(body)))
        value=deepcopy(self.reply)
        if 'expires_at' not in value:value['expires_at']=(datetime.now(timezone.utc)+timedelta(seconds=4)).isoformat()
        return canonical(value)


class PlanLoaderTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        fixture=make_fixture(self.temp.name);self.fixture=fixture;self.session=fixture.plan.session_id
        source=self.root/'published-profile.json';source.write_bytes(b'{"socket":"@@MEDICAL_SOCKET_PATH@@","token":"@@MEDICAL_TOKEN_PATH@@","inventory":"@@MEDICAL_INVENTORY_PATH@@"}')
        self.source=source;self.socket_root=self.root/'sockets';self.socket_root.mkdir();jobs=self.root/'jobs';jobs.mkdir()
        self.deployment={'schema_version':'1.0.0','deployment_revision':fixture.plan.deployment_revision,'runtime_manifest_sha256':fixture.plan.runtime_manifest_sha256,'jobs_root':str(jobs),'role_name':'Researcher','role_version':{'version_id':'fixture-version','content_sha256':'7'*64},'profile_artifact':{'artifact_id':'fixture-artifact','sha256':'8'*64,'size_bytes':'1','media_type':'application/json'},'maximum_object_bytes':self.fixture.plan.maximum_bytes,'sdk':{'model':'approved','reasoning_effort':None,'max_tokens':100,'initialize_timeout_seconds':10,'request_timeout_seconds':30,'shutdown_timeout_seconds':3,'dsh_bin':'/operator/sdk','sdk_version':'1.0.0','runtime_version':'1.0.0'},'profile_files':[{'source':str(source),'relative':'profiles/medical-agent/profile.json','sha256':digest(source.read_bytes())}],'expected_inventory':{'plugin_names':['fixture'],'tool_schema_hashes':{'AT-01':'a'*64},'models':[{'id':'approved'}],'providers':[{'id':'enterprise-model','name':'企业模型接口'}],'model_stream_schema_ref':'fixture-model-stream'}}
        self.deployment_bytes=canonical(self.deployment)
        self.slot=OperatorSlot(self.deployment_bytes,fixture.plan.python,fixture.plan.runner,fixture.plan.runner_sha256,fixture.inspector.config,fixture.plan.policy)
        self.binding=dict(fixture.plan.binding);envelope=strict_json(fixture.plan.registered_envelope);envelope['deployment_sha256']=digest(self.deployment_bytes)
        self.envelope=canonical(envelope);self.binding['step_envelope_sha256']=digest(self.envelope)
        job=jobs/self.binding['tenant_id']/self.binding['run_id']/self.binding['step_id']/str(self.binding['lease_generation'])
        self.launch={'state':'PREPARE','purpose':'TASK','node_kind':'N02','role':'Researcher','session_id':self.session,'binding':self.binding,'deployment_sha256':digest(self.deployment_bytes),'runtime_manifest_sha256':fixture.plan.runtime_manifest_sha256,'job_directory':str(job),'prompt':envelope['compiled_prompt'],'prompt_sha256':envelope['prompt_sha256'],'prompt_provenance':envelope['prompt_provenance'],**{key:deepcopy(self.deployment[key]) for key in ('sdk','profile_files','expected_inventory','role_version','profile_artifact')}}
        self.reply={'binding':self.binding,'session_id':self.session,'prepared_launch':self.launch,'registered_envelope_base64':base64.b64encode(self.envelope).decode(),'object_ref':{'objectKey':'fixed/original/envelope','objectVersion':'version-original','sha256':digest(self.envelope),'sizeBytes':str(len(self.envelope)),'mediaType':'application/json'},'registration_receipt_sha256':'9'*64}
        self.backend=BackendDouble(self.reply);self.loader=BackendPlanLoader(self.backend,(self.slot,))
    async def load(self):return await self.loader.load(self.session,self.binding,self.socket_root)
    async def test_physically_returned_envelope_and_local_package_build_exact_plan(self):
        plan=await self.load()
        self.assertEqual(self.backend.calls,[('POST','/internal/v1/professional-runtime/supervisor-plan',{'binding':self.binding,'session_id':self.session})])
        self.assertEqual(plan.registered_envelope,self.envelope)
        self.assertEqual(strict_json(plan.payload)['prompt'],self.launch['prompt'])
        self.assertEqual(strict_json(plan.payload)['prompt_provenance'],self.launch['prompt_provenance'])
        expected=self.source.read_bytes()
        base=self.socket_root/self.session.replace('-','')
        for marker,path in ((b'@@MEDICAL_SOCKET_PATH@@',base/'s'),(b'@@MEDICAL_TOKEN_PATH@@',base/'t'),(b'@@MEDICAL_INVENTORY_PATH@@',base/'inventory.json')):expected=expected.replace(marker,str(path).encode())
        self.assertEqual(plan.inspector.config.profile_files[0].sha256,digest(expected))
        self.assertEqual(plan.inspector.config.profile_files[0].path,plan.job/'home/profiles/medical-agent/profile.json')
        self.assertEqual(set(plan.environment),{'HOME','TMPDIR','PATH','LANG','LC_ALL'})
    async def test_revalidate_independently_reads_again_and_rejects_changed_original(self):
        plan=await self.load();await self.loader.require_current(plan);self.assertEqual(len(self.backend.calls),2)
        self.reply['registration_receipt_sha256']='a'*64
        with self.assertRaisesRegex(RuntimeBlocked,'SUPERVISOR_PLAN_CHANGED'):await self.loader.require_current(plan)
    async def test_duplicate_session_does_not_allocate_or_fetch_again(self):
        await self.load()
        with self.assertRaisesRegex(RuntimeBlocked,'RECOVERY_REQUIRED'):await self.load()
        self.assertEqual(len(self.backend.calls),1)
    async def test_unknown_or_exhausted_local_slot_does_not_create_resources(self):
        self.loader.used.add(0)
        with self.assertRaisesRegex(RuntimeBlocked,'SLOT_UNAVAILABLE'):await self.load()
        self.assertEqual(self.loader.assigned,{})
    async def test_unknown_deployment_is_not_installed(self):
        self.launch['deployment_sha256']='f'*64
        with self.assertRaisesRegex(RuntimeBlocked,'SLOT_UNAVAILABLE'):await self.load()
    async def test_any_local_deployment_field_substitution_is_rejected(self):
        for field in ('sdk','profile_files','expected_inventory','role_version','profile_artifact','role','runtime_manifest_sha256'):
            original=deepcopy(self.launch[field]);self.launch[field]=({'invalid':True} if isinstance(original,dict) else [] if isinstance(original,list) else 'other')
            with self.subTest(field=field),self.assertRaises(Exception):await self.load()
            self.launch[field]=original
        self.assertFalse(self.loader.used)
    async def test_mismatched_binding_or_session_is_not_plan_authority(self):
        original=self.reply['session_id'];self.reply['session_id']='00000000-0000-4000-8000-000000000001'
        with self.assertRaisesRegex(RuntimeBlocked,'PLAN_MISMATCH'):await self.load()
        self.reply['session_id']=original;self.reply['binding']={}
        with self.assertRaisesRegex(RuntimeBlocked,'PLAN_MISMATCH'):await self.load()
    async def test_malformed_expired_or_overlong_authority_is_rejected(self):
        for value in ('invalid',None,datetime.now().isoformat(),(datetime.now(timezone.utc)-timedelta(seconds=1)).isoformat(),(datetime.now(timezone.utc)+timedelta(seconds=30)).isoformat()):
            self.reply['expires_at']=value
            with self.subTest(value=value),self.assertRaisesRegex(RuntimeBlocked,'PLAN_EXPIRED'):await self.load()
    async def test_physical_bytes_and_exact_object_reference_are_required(self):
        for key,value in (('sha256','f'*64),('sizeBytes','1'),('mediaType','text/plain'),('objectVersion','')):
            original=self.reply['object_ref'][key];self.reply['object_ref'][key]=value
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeBlocked,'ENVELOPE_MISMATCH'):await self.load()
            self.reply['object_ref'][key]=original
        self.reply['registered_envelope_base64']=base64.b64encode(b'{}').decode()
        with self.assertRaisesRegex(RuntimeBlocked,'ENVELOPE_MISMATCH'):await self.load()
    async def test_missing_extra_or_nonobject_reply_is_rejected(self):
        self.reply['asserted_process_exited']=True
        with self.assertRaisesRegex(RuntimeBlocked,'PLAN_MISMATCH'):await self.load()
    async def test_unknown_launch_fields_are_not_silently_dropped(self):
        self.launch['caller_asserted_isolation']=True
        with self.assertRaisesRegex(RuntimeBlocked,'PLAN_MISMATCH'):await self.load()
    async def test_profile_file_change_is_not_a_valid_compilation(self):
        self.source.write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeBlocked,'FILE_HASH_MISMATCH'):await self.load()
        self.assertFalse(self.loader.used)
    async def test_job_path_is_derived_not_backend_suggested_arbitrary_path(self):
        self.launch['job_directory']=str(self.root/'unrelated')
        with self.assertRaisesRegex(RuntimeBlocked,'JOB_PATH'):await self.load()
    async def test_registered_compiled_prompt_text_is_exact(self):
        envelope=strict_json(self.envelope);envelope['compiled_prompt']='other';raw=canonical(envelope)
        self.binding['step_envelope_sha256']=digest(raw);self.reply['registered_envelope_base64']=base64.b64encode(raw).decode();self.reply['object_ref'].update(sha256=digest(raw),sizeBytes=str(len(raw)))
        with self.assertRaisesRegex(RuntimeBlocked,'REGISTERED_ENVELOPE_MISMATCH'):await self.load()
    def test_pool_does_not_allow_alias_of_same_cgroup(self):
        with self.assertRaisesRegex(RuntimeBlocked,'DUPLICATE_OPERATOR'):BackendPlanLoader(self.backend,(self.slot,self.slot))
    def test_caller_pid_or_extra_binding_is_rejected(self):
        with self.assertRaisesRegex(RuntimeBlocked,'BINDING_INVALID'):binding_from_wire(self.binding|{'pid':123})
