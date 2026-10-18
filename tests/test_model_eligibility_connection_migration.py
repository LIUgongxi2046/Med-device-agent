"""077 offline native DDL and mapper-prefix structure; no credentials or provider I/O."""
import unittest
from sqlglot import exp
from runtime_migration_support import *
FILE='077-model-eligibility-connection.sql'
TABLES={'runtime_connection_intent','runtime_connection_start','runtime_connection_completed','model_eligibility'}
class ModelEligibilityConnectionMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(c):c.path=RESOURCES/'db/migration'/FILE;c.native,c.tables=inspect_migration(c.path);c.ast,_=ast_tables(c.path)
 def test_exact_additive_immutable_registration(self):
  self.assertEqual(TABLES,set(self.tables));self.assertEqual(4,len(self.native));self.assertTrue(all(isinstance(s,exp.Create) for s in self.ast));registered(self,FILE,TABLES);tenant_catalogues(self,TABLES)
 def test_tenant_parent_fk_contracts(self):standard_metadata(self,self.tables);parent_contracts(self,self.tables)
 def test_original_actor_key_and_single_model_eligibility(self):
  self.assertIn(('tenant_id','created_by','idempotency_key'),unique_keys(self.tables['runtime_connection_intent']))
  self.assertIn(('tenant_id','model_revision'),unique_keys(self.tables['model_eligibility']))
  checks=' '.join(k.sql(dialect='mysql') for k in self.tables['model_eligibility'].find_all(exp.CheckColumnConstraint));self.assertIn("status = 'ENABLED'",checks);self.assertIn('revision = 1',checks)
 def test_actual_mapper_namespaces_have_subject_prefix(self):
  for mapper in ['ConnectionVerificationMapper','ModelEligibilityMapper']:mapper_prefix(self,mapper)
 def test_no_seeds_grants_or_mutable_existing_credential_classification(self):
  self.assertNotRegex(self.path.read_text(),r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
  text=(JAVA/'ProtectedTenantInterceptor.java').read_text().split('IMMUTABLE_TABLES =',1)[1].split(';',1)[0]
  for name in ['credential_reference','connection_verification']:self.assertNotIn('"'+name+'"',text)
