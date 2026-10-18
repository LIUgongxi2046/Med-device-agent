import copy,json,os,subprocess,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import medical_model_gateway,rfc8785
BASE=Path(__file__).resolve().parents[2]
FIXTURES=BASE/'business/medical-persistence/src/main/resources/fixtures/provider-capture'
from medical_model_gateway.formal_receipt_store import FormalReceiptStore
from medical_model_gateway.dispatch_guard import DurableDispatchGuard
class ReceiptTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.path=Path(self.tmp.name);self.store=FormalReceiptStore(self.path,16777216)
  f=FIXTURES;self.capture=json.loads((f/'actual-sdk-capture.json').read_bytes());self.model=json.loads((f/'actual-sdk-model-result.json').read_bytes());self.m=self.capture['metadata']
  self.request=dict(schema_version='1.0.0',tenant_id='11111111-1111-4111-8111-111111111111',original_actor_id='22222222-2222-4222-8222-222222222222',model_result=self.model,provider_capture=self.capture)
  self.ids=(self.m['invocation_id'],self.m['dispatch_attempt_id'],self.m['receipt_id'],self.m['canonical_request_body_sha256'])
  with DurableDispatchGuard(self.path) as g:g.claim(*self.ids)
 def tearDown(self):self.tmp.cleanup()
 def test_original_roundtrip_is_exact_and_private(self):
  self.assertEqual(self.store.save(self.request),rfc8785.dumps(self.request));self.assertEqual(self.store.save(self.request),rfc8785.dumps(self.request))
  for p in self.path.iterdir():self.assertEqual(p.stat().st_mode&0o777,0o600)
 def test_new_process_reads_identical_original_without_send(self):
  original=self.store.save(self.request)
  script="import sys;from pathlib import Path;from medical_model_gateway.formal_receipt_store import FormalReceiptStore;sys.stdout.buffer.write(FormalReceiptStore(Path(sys.argv[1]),16777216).read(*sys.argv[2:]))"
  p=subprocess.run([sys.executable,'-c',script,str(self.path),*self.ids[:2]],capture_output=True)
  self.assertEqual(p.returncode,0,p.stderr);self.assertEqual(p.stdout,original)
 def test_original_bytes_changed_rejected(self):
  self.store.save(self.request);p=self.path/('receipt-'+self.ids[1]+'-model.json');p.write_bytes(p.read_bytes()+b' ')
  with self.assertRaises(ValueError):self.store.read(*self.ids[:2])
 def test_manifest_identity_changed_rejected(self):
  self.store.save(self.request);p=self.path/('receipt-'+self.ids[1]+'-manifest.json');m=json.loads(p.read_bytes());m['receipt_id']=self.request['tenant_id'];p.write_bytes(rfc8785.dumps(m))
  with self.assertRaises(ValueError):self.store.read(*self.ids[:2])
 def test_schema_extra_field_rejected_before_write(self):
  self.capture['unexpected']=True
  with self.assertRaises(Exception):self.store.save(self.request)
  self.assertEqual(len(list(self.path.iterdir())),1)
 def test_raw_body_digest_mismatch_rejected(self):
  self.capture['body_base64']='YQ=='
  with self.assertRaises(ValueError):self.store.save(self.request)
 def test_missing_original_marker_rejected(self):
  next(self.path.iterdir()).unlink()
  with self.assertRaises(ValueError):self.store.save(self.request)
 def test_different_original_cannot_overwrite(self):
  self.store.save(self.request);before={p.name:p.read_bytes() for p in self.path.iterdir()};self.model['provider_request_id']='changed'
  with self.assertRaises(ValueError):self.store.save(self.request)
  self.assertEqual(before,{p.name:p.read_bytes() for p in self.path.iterdir()})
 def test_fsync_failure_has_no_committed_manifest(self):
  with patch('medical_model_gateway.formal_receipt_store.os.fsync',side_effect=OSError('injected')):
   with self.assertRaises(OSError):self.store.save(self.request)
  with self.assertRaises(FileNotFoundError):self.store.read(*self.ids[:2])
  with DurableDispatchGuard(self.path) as g:
   with self.assertRaises(Exception):g.claim(*self.ids)
 def test_partial_manifest_never_returns_success(self):
  with patch.object(self.store,'_publish',side_effect=OSError('injected')):
   with self.assertRaises(OSError):self.store.save(self.request)
  with self.assertRaises(FileNotFoundError):self.store.read(*self.ids[:2])
 def test_symlink_original_rejected(self):
  self.store.save(self.request);p=self.path/('receipt-'+self.ids[1]+'-model.json');raw=p.read_bytes();p.unlink();other=self.path/'other';other.write_bytes(raw);other.chmod(0o600);p.symlink_to(other)
  with self.assertRaises(OSError):self.store.read(*self.ids[:2])
 def test_combined_cap_rejected(self):
  small=FormalReceiptStore(self.path,100)
  with self.assertRaises(ValueError):small.save(self.request)
 def test_adapter_restart_recovery_calls_only_receipts(self):
  import asyncio
  from medical_model_gateway.formal_invocation_adapter import FormalInvocationAdapter
  from medical_model_gateway.raw_response import CapturePolicy
  self.store.save(self.request)
  class Gateway:
   async def post(s,path,body,token=None):
    self.assertEqual(path,'/internal/v1/invocations/'+self.ids[0]+'/receipts');self.assertEqual(rfc8785.dumps(body),rfc8785.dumps(self.request));return 'recorded'
  class NoCredentials:
   def provider(*args):raise AssertionError('must not send')
  # Recovery needs only the journal and original gateway; no SDK/provider path is invoked.
  adapter=object.__new__(FormalInvocationAdapter);adapter._gateway=Gateway();adapter._receipts=FormalReceiptStore(self.path,16777216)
  self.assertEqual(asyncio.run(adapter.recover(*self.ids[:2])),'recorded')
 def test_actual_adapter_publishes_before_returning_held_receipt(self):
  import asyncio
  from datetime import datetime,timedelta,timezone
  from medical_model_gateway.formal_invocation_adapter import FormalInvocationAdapter
  from medical_model_gateway.raw_response import CapturePolicy
  from medical_model_gateway.provider import ProviderCompletion
  from medical_contracts.model_stream import ModelResult
  body=json.loads((FIXTURES/'approved-provider-body.json').read_bytes())
  admitted=dict(invocation_id=self.ids[0],new_send_admission=True,tenant_id=self.request['tenant_id'],original_actor_id=self.request['original_actor_id'],context_manifest_id=self.model['context_manifest_id'],model_config_revision=self.model['model_config_revision'],dispatch_attempt_id=self.ids[1],dispatch_generation='1',service_uri='spiffe://test/gateway',deadline_at=(datetime.now(timezone.utc)+timedelta(seconds=120)).isoformat(),credential_reference_id=self.request['tenant_id'],credential_revision='fixed',canonical_provider_body_sha256=self.ids[3],provider_body=body)
  test=self
  class Gateway:
   async def post(s,path,payload,token=None):
    test.assertTrue(path.endswith('/begin-send'));return admitted
  class Raw:
   def artifact(s,maximum):return rfc8785.dumps(test.capture)
  model=ModelResult.model_validate(self.model)
  class Provider:
   async def stream(s,*args,**kwargs):
    yield ProviderCompletion(provider_request_id=model.provider_request_id,response_model='deepseek-chat',blocks=model.blocks,usage=model.usage,reason=model.reason,raw_response=Raw())
  class Credentials:
   def provider(s,*args):return Provider()
  adapter=FormalInvocationAdapter(Gateway(),Credentials(),self.path,CapturePolicy(True,1048576,1048576,120),16777216)
  with patch('medical_model_gateway.formal_invocation_adapter.uuid4',return_value=self.ids[2]):held=asyncio.run(adapter.dispatch(self.ids[0],{},'actual-token-boundary'))
  self.assertEqual(held.body,self.store.read(*self.ids[:2]));self.assertEqual(held.body,rfc8785.dumps(self.request))
 def test_worker_driver_obtains_all_ids_and_token_from_real_gateway_contract(self):
  import asyncio
  from medical_model_gateway.formal_invocation_adapter import FormalVerificationWorker,HeldReceipt
  test=self;calls=[];lease=dict(verification_id=self.request['tenant_id'],run_id=self.request['original_actor_id'],step_id=self.ids[2],lease_generation='1')
  issued=dict(verification_id=lease['verification_id'],run_id=lease['run_id'],step_id=lease['step_id'],token='server-issued-fixture',worker_action={'lease_generation':'1'},model_config_revision=self.model['model_config_revision'],context_manifest_id=self.model['context_manifest_id'],request_parameters={'max_output_tokens':16})
  class Gateway:
   async def post(s,path,body,token=None):
    calls.append((path,body,token))
    if path.endswith('/claim'):return {'found':True,'lease':lease}
    if path.endswith('/issue'):return issued
    if path=='/internal/v1/invocations':return {'verification_id':lease['verification_id'],'invocation_id':test.ids[0]}
    raise AssertionError(path)
  class Adapter:
   async def dispatch(s,invocation,action,token):test.assertEqual(token,issued['token']);test.assertEqual(invocation,test.ids[0]);return HeldReceipt(invocation,b'original')
   async def record(s,held):test.assertEqual(held.body,b'original');return {'status':'SUCCEEDED'}
  self.assertEqual(asyncio.run(FormalVerificationWorker(Gateway(),Adapter()).run_once(self.request['tenant_id'])),{'status':'SUCCEEDED'})
  self.assertEqual([c[0] for c in calls],['/internal/v1/model-verification-workers/claim','/internal/v1/model-verification-workers/issue','/internal/v1/invocations']);self.assertEqual(calls[0][1],{'schema_version':'1.0.0','tenant_id':self.request['tenant_id']})
 def test_worker_empty_queue_has_no_token_or_model_call(self):
  import asyncio
  from medical_model_gateway.formal_invocation_adapter import FormalVerificationWorker
  class Gateway:
   async def post(s,path,body,token=None):self.assertTrue(path.endswith('/claim'));return {'found':False,'lease':None}
  self.assertEqual(asyncio.run(FormalVerificationWorker(Gateway(),object()).run_once(self.request['tenant_id'])),{'state':'IDLE'})
 def test_deployment_entry_reads_only_private_bounded_configuration(self):
  from medical_model_gateway.formal_verification_worker import private_json
  p=self.path/'deployment.json';p.write_text('{"schema_version":"1.0.0"}');p.chmod(0o600);self.assertEqual(private_json(p),{'schema_version':'1.0.0'});p.chmod(0o644)
  with self.assertRaises(ValueError):private_json(p)
if __name__=='__main__':unittest.main()
