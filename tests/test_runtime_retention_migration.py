"""074 parsed storage contracts; no live database or deletion-worker assertion."""
import unittest
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT,RESOURCES,inspect_migration,foreign_keys,assert_registered
MIGRATION='db/migration/074-runtime-retention.sql'
class RuntimeRetentionMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.sql=(RESOURCES/MIGRATION).read_text();cls.statements,cls.tables=inspect_migration(RESOURCES/MIGRATION)
 def test_exact_additive_table_registration_without_grants(self):
  self.assertEqual({'runtime_retention_policy','runtime_retention_state','runtime_retention_retirement','runtime_retention_cancellation'},set(self.tables))
  assert_registered(self,MIGRATION,'db/migration/073-agent-publication.sql',self.tables)
  self.assertNotRegex(self.sql,r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
 def test_all_references_are_tenant_composite(self):
  for table in self.tables.values():
   for target,local,remote in foreign_keys(table):self.assertEqual('tenant_id',local[0]);self.assertEqual('tenant_id',remote[0])
  self.assertIn(('operation_record',('tenant_id','operation_id'),('tenant_id','operation_id')),foreign_keys(self.tables['runtime_retention_cancellation']))
 def test_cancel_identity_and_policy_cas_are_explicit(self):
  self.assertRegex(self.sql,r'UNIQUE KEY uq_retention_cancel_original\(tenant_id,created_by,original_kind,original_key\)')
  self.assertRegex(self.sql,r'UNIQUE KEY uq_retention_cancel_key\(tenant_id,created_by,cancel_key\)')
  self.assertIn('revision=1 AND retired_at IS NULL AND decision_id IS NULL',self.sql)
  self.assertIn('revision=2 AND retired_at IS NOT NULL AND decision_id IS NOT NULL',self.sql)
 def test_only_state_is_mutable_under_actual_interceptor_catalogue(self):
  text=(ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ProtectedTenantInterceptor.java').read_text()
  for name in ['runtime_retention_policy','runtime_retention_retirement','runtime_retention_cancellation']:self.assertIn('"'+name+'"',text)
  self.assertNotIn('"runtime_retention_state"',text)
 def test_retirement_reference_is_additive_and_exact(self):
  changes=[s for s in self.statements if 'table' in s]
  self.assertEqual(1,len(changes));self.assertEqual('runtime_retention_state',changes[0]['table']);self.assertEqual('ADD',changes[0]['operation'])
  self.assertIn('FOREIGN KEY (tenant_id, decision_id)',changes[0]['definition'])
