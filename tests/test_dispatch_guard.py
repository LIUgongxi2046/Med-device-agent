import asyncio,hashlib,json,os,stat,subprocess,sys,tempfile,unittest,uuid
from pathlib import Path
from unittest.mock import patch
import medical_model_gateway
stage=Path(os.environ['MEDICAL_DISPATCH_STAGE']) if os.environ.get('MEDICAL_DISPATCH_STAGE') else Path(medical_model_gateway.__file__).resolve().parent.parent
medical_model_gateway.__path__.insert(0,str(stage/'medical_model_gateway'))
from medical_model_gateway.dispatch_guard import DurableDispatchGuard,DispatchGuardError
from medical_model_gateway.raw_response import ProviderResponseCapture,CapturePolicy,CapturingTransport,CaptureError
from medical_model_gateway.provider import DeepSeekProvider
import httpx2,rfc8785
class GuardTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.path=Path(self.tmp.name);self.path.chmod(0o700);self.ids=(str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4()),'a'*64)
 def tearDown(self):self.tmp.cleanup()
 def test_private_marker_exact_identity_and_mode(self):
  with DurableDispatchGuard(self.path) as g:
   self.assertIsNone(g.read(*self.ids));g.claim(*self.ids);v=g.read(*self.ids);self.assertEqual(v['dispatch_attempt_id'],self.ids[1]);self.assertEqual(v['state'],'DISPATCH_RESERVED_NO_RETRY')
   with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
  f=next(self.path.iterdir());self.assertEqual(stat.S_IMODE(f.stat().st_mode),0o600);self.assertNotIn('authorization',f.read_text());self.assertNotIn('Bearer',f.read_text())
 def test_new_process_cannot_reuse_original_attempt(self):
  with DurableDispatchGuard(self.path) as g:g.claim(*self.ids)
  script="import sys;from pathlib import Path;import medical_model_gateway;medical_model_gateway.__path__.insert(0,sys.argv[1]);from medical_model_gateway.dispatch_guard import DurableDispatchGuard,DispatchGuardError\nwith DurableDispatchGuard(Path(sys.argv[2])) as g:\n try:g.claim(*sys.argv[3:])\n except DispatchGuardError as e:print(str(e));sys.exit(23)\n"
  p=subprocess.run([sys.executable,'-c',script,str(stage/'medical_model_gateway'),str(self.path),*self.ids],capture_output=True,text=True)
  self.assertEqual(p.returncode,23,p.stderr);self.assertIn('RECONCILE_ONLY',p.stdout)
 def test_two_instances_contend_without_duplicate_claim(self):
  from concurrent.futures import ThreadPoolExecutor
  def claim(_):
   with DurableDispatchGuard(self.path) as g:
    try:g.claim(*self.ids);return True
    except DispatchGuardError:return False
  with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(claim,range(2)))
  self.assertEqual(sum(results),1)
 def test_fsync_failure_keeps_marker_and_refuses_next_send(self):
  with DurableDispatchGuard(self.path) as g:
   with patch('medical_model_gateway.dispatch_guard.os.fsync',side_effect=OSError('private failure')):
    with self.assertRaisesRegex(DispatchGuardError,'UNCERTAIN'):g.claim(*self.ids)
   self.assertEqual(len(list(self.path.iterdir())),1)
   with self.assertRaisesRegex(DispatchGuardError,'ALREADY_RESERVED'):g.claim(*self.ids)
 def test_partial_write_preserves_blocking_marker(self):
  with DurableDispatchGuard(self.path) as g:
   with patch('medical_model_gateway.dispatch_guard.os.write',return_value=0):
    with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
   with self.assertRaises(DispatchGuardError):g.read(*self.ids)
   with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
 def test_public_directory_symlink_missing_and_closed_are_rejected(self):
  self.path.chmod(0o755)
  with self.assertRaises(DispatchGuardError):DurableDispatchGuard(self.path)
  self.path.chmod(0o700);link=self.path/'link';link.symlink_to(self.path)
  with self.assertRaises(DispatchGuardError):DurableDispatchGuard(link)
  with self.assertRaises(DispatchGuardError):DurableDispatchGuard(self.path/'absent')
  g=DurableDispatchGuard(self.path);g.close()
  with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
 def test_marker_symlink_hardlink_corruption_and_changed_identity_are_not_repaired(self):
  with DurableDispatchGuard(self.path) as g:
   g.claim(*self.ids);marker=next(self.path.iterdir())
   wrong=list(self.ids);wrong[0]=str(uuid.uuid4())
   with self.assertRaises(DispatchGuardError):g.read(*wrong)
   os.link(marker,self.path/'hardlink')
   with self.assertRaises(DispatchGuardError):g.read(*self.ids)
   (self.path/'hardlink').unlink();marker.write_text('{}')
   with self.assertRaises(DispatchGuardError):g.read(*self.ids)
   with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
 def test_directory_privacy_change_is_checked_before_claim(self):
  with DurableDispatchGuard(self.path) as g:
   self.path.chmod(0o755)
   with self.assertRaisesRegex(DispatchGuardError,'NOT_PRIVATE'):g.claim(*self.ids)
   self.assertEqual(list(self.path.iterdir()),[])
  self.path.chmod(0o700)
 def test_marker_symlink_never_follows_or_overwrites_target(self):
  target=self.path/'target';target.write_text('keep');target.chmod(0o600)
  marker=self.path/('dispatch-'+self.ids[1]+'.json');marker.symlink_to(target)
  with DurableDispatchGuard(self.path) as g:
   with self.assertRaises(DispatchGuardError):g.read(*self.ids)
   with self.assertRaises(DispatchGuardError):g.claim(*self.ids)
  self.assertEqual(target.read_text(),'keep')
 def test_invalid_identity_cannot_create_path(self):
  with DurableDispatchGuard(self.path) as g:
   for values in [('bad',*self.ids[1:]),(self.ids[0],'../escape',*self.ids[2:]),(*self.ids[:3],'B'*64)]:
    with self.assertRaises(DispatchGuardError):g.claim(*values)
  self.assertEqual(list(self.path.iterdir()),[])
class CaptureGuardTests(unittest.IsolatedAsyncioTestCase):
 async def test_durable_marker_precedes_transport_and_restart_never_sends(self):
  body={'model':'deepseek-chat','messages':[{'role':'user','content':'test'}],'stream':True,'reasoning_effort':'none'};digest=hashlib.sha256(rfc8785.dumps(body)).hexdigest();ids=(str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4()));calls=[]
  with tempfile.TemporaryDirectory() as path,DurableDispatchGuard(Path(path)) as g:
   async def upstream(r):
    self.assertIsNotNone(g.read(*ids,digest));calls.append(r);raise httpx2.ConnectError('uncertain transport')
   def capture():return ProviderResponseCapture(CapturePolicy(True,10000,10000,2),*ids,digest,dispatch_guard=g)
   c=capture();transport=CapturingTransport(httpx2.MockTransport(upstream),c)
   with self.assertRaises(httpx2.ConnectError):await transport.handle_async_request(httpx2.Request('POST',c.ENDPOINT,json=body))
   self.assertEqual(c.result().transport_terminal,'TRANSPORT_ERROR');next_capture=capture();transport=CapturingTransport(httpx2.MockTransport(upstream),next_capture)
   with self.assertRaisesRegex(CaptureError,'REQUIRES_RECONCILIATION'):await transport.handle_async_request(httpx2.Request('POST',c.ENDPOINT,json=body))
   with self.assertRaisesRegex(CaptureError,'REQUIRES_RECONCILIATION'):next_capture.result()
   self.assertEqual(len(calls),1)
 async def test_production_client_requires_guard_before_accessing_any_secret(self):
  c=ProviderResponseCapture(CapturePolicy(True,100,100,1),str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4()),'a'*64)
  with self.assertRaisesRegex(CaptureError,'DURABLE_DISPATCH_GUARD_REQUIRED'):DeepSeekProvider(Path('/never-read')).client(c)
if __name__=='__main__':unittest.main()
