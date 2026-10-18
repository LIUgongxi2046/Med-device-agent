"""076 offline full AST and native parser contracts, not provider/database execution."""
import unittest
from sqlglot import exp
from runtime_migration_support import *
from migration_076_compat import parse_076_native_statements, SHA256
FILE='076-formal-model-invocation.sql'
TABLES={'formal_model_manifest','formal_model_verification_job','formal_invocation_binding','formal_invocation_attempt','formal_invocation_object_intent','formal_invocation_object_attempt','formal_invocation_object_state','formal_invocation_registered_object','formal_invocation_accounting','formal_invocation_cancellation','formal_invocation_uncertainty'}
class FormalModelInvocationMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.path=RESOURCES/'db/migration'/FILE;c.ast=parse_076_native_statements(c.path);c.tables={s.this.this.name:s for s in c.ast[1:]}
 def test_exact_complete_ast_and_registration(self):
  self.assertEqual(TABLES,set(self.tables));self.assertEqual(12,len(self.ast));self.assertIsInstance(self.ast[0],exp.Alter);self.assertTrue(all(isinstance(s,exp.Create) for s in self.ast[1:]));registered(self,FILE,TABLES)
 def test_tenant_fk_parent_unique_keys_and_types(self):standard_metadata(self,self.tables,{'formal_invocation_attempt','formal_invocation_object_attempt','formal_invocation_object_state'});parent_contracts(self,self.tables)
 def test_only_object_stage_state_is_mutable(self):tenant_catalogues(self,TABLES,{'formal_invocation_object_state'})
 def test_model_verification_workflow_exception_is_exact(self):
  text=self.ast[0].sql(dialect='mysql');self.assertEqual('system_job_run',self.ast[0].this.name)
  literals={s.this for s in self.ast[0].find_all(exp.Literal) if s.is_string}
  self.assertEqual({'SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION'},literals)
  self.assertIn('workflow_id IS NULL',text);self.assertIn('NOT workflow_id IS NULL',text)
 def test_no_seed_or_grants_and_all_object_checks_remain(self):
  self.assertNotRegex(self.path.read_text(),r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|GRANT|REVOKE)\b')
  for name in ['formal_invocation_object_intent','formal_invocation_object_state','formal_invocation_registered_object']:
   self.assertTrue(list(self.tables[name].find_all(exp.CheckColumnConstraint)),name)

 def test_project_derived_rows_require_real_tenant_parent(self):
  expected={
   'formal_invocation_attempt':('formal_invocation_binding',('tenant_id','invocation_id'),('tenant_id','invocation_id')),
   'formal_invocation_object_attempt':('formal_invocation_object_intent',('tenant_id','write_id'),('tenant_id','write_id')),
   'formal_invocation_object_state':('formal_invocation_object_intent',('tenant_id','write_id'),('tenant_id','write_id'))}
  for name,parent in expected.items():
   self.assertNotIn('project_id',columns(self.tables[name]));self.assertIn(parent,foreign_keys(self.tables[name]))
   self.assertIn('project_id',columns(self.tables[parent[0]]))

 def test_compatibility_rejects_other_filenames_before_parsing(self):
  with self.assertRaisesRegex(ValueError,'076_COMPATIBILITY_SCOPE_ONLY'):
   parse_076_native_statements(self.path.with_name('075-persistent-run-runtime.sql'))
 def test_compatibility_rejects_any_original_byte_change(self):
  from tempfile import TemporaryDirectory
  with TemporaryDirectory(prefix='medical-076-negative-') as folder:
   changed=Path(folder)/FILE;changed.write_bytes(self.path.read_bytes()+b'\n')
   with self.assertRaisesRegex(ValueError,'076_ORIGINAL_BYTES_CHANGED'):parse_076_native_statements(changed)
 def test_compatibility_rejects_changed_preexisting_job_set(self):
  from tempfile import TemporaryDirectory
  with TemporaryDirectory(prefix='medical-076-prior-') as folder:
   actual=Path(folder)/FILE;actual.write_bytes(self.path.read_bytes())
   prior=Path(folder)/'068-document-technical-check.sql'
   prior.write_text(self.path.with_name(prior.name).read_text().replace("'DOCUMENT_TECHNICAL_CHECK'","'UNAPPROVED_JOB'"))
   with self.assertRaisesRegex(ValueError,'076_PRIOR_WORKFLOW_CHECK_CHANGED'):parse_076_native_statements(actual)
