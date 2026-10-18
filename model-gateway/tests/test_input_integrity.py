import importlib.util,json,hashlib,unittest
from pathlib import Path
from datetime import datetime,timezone
from jsonschema import Draft202012Validator,ValidationError
from medical_model_gateway import input_integrity as m
def encoded(v):return json.dumps(v,separators=(',',':'),sort_keys=True).encode()
def sha(b):return hashlib.sha256(b).hexdigest()
def uid(n):return f'00000000-0000-4000-8000-{n:012d}'
class InputIntegrityTest(unittest.TestCase):
 def setUp(self):
  names=['tenant_id','project_id','initiator_id','task_id','task_revision','run_id','plan_revision','snapshot_id','budget_revision','agent_release_id','principal_id']
  self.input={name:uid(i+1) for i,name in enumerate(names)};self.raw=encoded(self.input)
  self.env={**self.input,'schema_version':'1.0.0','execution_kind':'TASK','principal_id':self.input['principal_id'],'step_id':uid(21),'role_version':uid(22),'input_manifest_id':uid(23),'trace_id':uid(24),'input_manifest_sha256':sha(self.raw),'runtime_manifest_sha256':'a'*64,'node_kind':'N00_INPUT_INTEGRITY','action_scope':['artifact.read'],'lease_generation':1,'deadline_at':'2026-10-18T20:00:00Z','input':{'artifact_id':uid(23),'sha256':sha(self.raw),'schema_ref':'urn:fixture:input:1'}}
  self.validator=Draft202012Validator({'type':'object','additionalProperties':False,'required':names,'properties':{n:{'type':'string'} for n in names}})
 def run_check(self,raw=None,env=None,digest=None,now=None,cap=65536):
  eb=encoded(self.env if env is None else env)
  return m.check_input(eb,digest or sha(eb),self.raw if raw is None else raw,self.validator,'urn:fixture:input:1','a'*64,now or datetime(2026,10,18,19,tzinfo=timezone.utc),cap)
 def test_real_raw_check_outputs_only_technical_admission(self):
  result=self.run_check();self.assertTrue(result['technical_only']);self.assertEqual(result['invocation_ids'],[]);self.assertEqual(result['candidate_ids'],[]);Draft202012Validator({**m._SCHEMA,'$ref':'#/$defs/StepResult'}).validate(result)
 def test_n00_cannot_claim_professional_output_or_waiting_decision(self):
  result=self.run_check();validator=Draft202012Validator({**m._SCHEMA,'$ref':'#/$defs/StepResult'})
  for field in ['artifact_ids','candidate_ids','invocation_ids','missing_requirements']:
   with self.assertRaises(ValidationError):validator.validate({**result,field:[uid(77)]})
  for extra in [{'outcome':'WAITING_INPUT'},{'outcome':'FAILED'},{'error_code':'INVENTED'},{'technical_only':False}]:
   with self.assertRaises(ValidationError):validator.validate({**result,**extra})
 def test_canonical_equivalent_bytes_not_same_object(self):
  changed=json.dumps(self.input,indent=2).encode();self.assertEqual(json.loads(changed),self.input)
  with self.assertRaisesRegex(ValueError,'CONTENT_MISMATCH'):self.run_check(raw=changed)
 def test_envelope_raw_hash_binding(self):
  with self.assertRaisesRegex(ValueError,'CONTENT_MISMATCH'):self.run_check(digest='b'*64)
 def test_wrong_tenant_project_or_revision_even_rehashed(self):
  for name in ['tenant_id','project_id','principal_id','task_revision','run_id','snapshot_id','budget_revision']:
   body={**self.input,name:uid(99)};raw=encoded(body);env={**self.env,'input_manifest_sha256':sha(raw),'input':{**self.env['input'],'sha256':sha(raw)}}
   with self.assertRaisesRegex(ValueError,'INPUT_OWNER_MISMATCH'):self.run_check(raw=raw,env=env)
 def test_expired_and_unzoned_clock(self):
  with self.assertRaisesRegex(ValueError,'DEADLINE_EXCEEDED'):self.run_check(now=datetime(2026,10,18,20,tzinfo=timezone.utc))
  with self.assertRaisesRegex(ValueError,'UTC_CLOCK_REQUIRED'):self.run_check(now=datetime(2026,10,18,19))
 def test_unapproved_node_and_extra_secret_rejected(self):
  for env in [{**self.env,'node_kind':'N04'},{**self.env,'token':'secret'}]:
   with self.assertRaises(ValidationError):self.run_check(env=env)
 def test_runtime_and_schema_substitution(self):
  for env in [{**self.env,'runtime_manifest_sha256':'b'*64},{**self.env,'input':{**self.env['input'],'schema_ref':'https://untrusted.invalid/schema'}}]:
   with self.assertRaisesRegex(ValueError,'CONTENT_MISMATCH'):self.run_check(env=env)
 def test_limits(self):
  with self.assertRaisesRegex(ValueError,'CONTENT_LIMIT'):self.run_check(cap=10)
 def test_duplicate_keys_unsafe_integer_nan_utf8(self):
  for raw in [b'{"a":1,"a":2}',b'{"a":9007199254740992}',b'{"a":NaN}',b'{"a":1e999}',b'"\xff"']:
   with self.assertRaises(ValueError):m.strict_json(raw,65536)
if __name__=='__main__':unittest.main()
