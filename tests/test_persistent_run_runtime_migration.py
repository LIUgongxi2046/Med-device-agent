"""075 native parser + AST contracts only; no MySQL or Temporal execution."""
import unittest
from sqlglot import exp
from runtime_migration_support import *
FILE='075-persistent-run-runtime.sql'
TABLES={'run_runtime_intent','step_runtime_attempt','run_runtime_receipt','runtime_object_intent','runtime_object_attempt','runtime_object_state','runtime_node_definition','runtime_governance_manifest','runtime_governance_member','runtime_governance_edge','runtime_registered_object'}
class PersistentRunRuntimeMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.path=RESOURCES/'db/migration'/FILE;c.native,c.tables=inspect_migration(c.path);c.ast,_=ast_tables(c.path)
 def test_exact_native_ast_and_registration(self):
  self.assertEqual(TABLES,set(self.tables));self.assertEqual(11,len(self.native));self.assertEqual(11,len(self.ast));self.assertTrue(all(isinstance(a,exp.Create) for a in self.ast));registered(self,FILE,TABLES)
 def test_actual_tenant_fk_parent_types_and_unique_keys(self):standard_metadata(self,self.tables);parent_contracts(self,self.tables)
 def test_only_stage_row_is_mutable(self):tenant_catalogues(self,TABLES,{'runtime_object_state'})
 def test_phase_and_metadata_checks_preserved(self):
  for name,t in self.tables.items():
   checks=' '.join(k.sql(dialect='mysql') for k in t.find_all(exp.CheckColumnConstraint));self.assertIn('PERSONAL_HEALTH',checks)
   self.assertIn(('schema_manifest_sha256','^[a-f0-9]{64}$'),{(r.this.name,r.expression.this) for r in t.find_all(exp.RegexpLike)})
   if name!='runtime_object_state':self.assertFalse({'revision','updated_at'}&set(columns(t)),name)
  checks=' '.join(k.sql(dialect='mysql') for k in self.tables['runtime_object_state'].find_all(exp.CheckColumnConstraint))
  self.assertIn("stage = 'PREPARED' AND revision = 1",checks);self.assertIn("stage = 'ATTEMPTED' AND revision = 2",checks)
 def test_registered_object_binds_original_attempt_governance_and_physical_version(self):
  f=foreign_keys(self.tables['runtime_registered_object']);key=('tenant_id','project_id','run_id','write_id')
  self.assertIn(('runtime_object_attempt',key,key),f);self.assertIn(('runtime_governance_manifest',key,key),f)
  self.assertIn(('artifact',('tenant_id','project_key','artifact_id','object_sha256','object_version'),('tenant_id','project_key','artifact_id','sha256','object_version')),f)
 def test_governance_edges_require_both_declared_members(self):
  f=foreign_keys(self.tables['runtime_governance_edge']);prefix=('tenant_id','project_id','run_id','write_id')
  for side in ['consumer_version_id','upstream_version_id']:
   self.assertIn(('runtime_governance_member',prefix+(side,),prefix+('upstream_version_id',)),f)
