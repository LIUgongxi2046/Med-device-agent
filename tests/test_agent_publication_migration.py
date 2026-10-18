"""073 additive capability metadata storage: real parsers, not a live MySQL claim."""
import json,re,unittest
from pathlib import Path
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT,RESOURCES,inspect_migration,foreign_keys,assert_registered
MIGRATION='db/migration/073-agent-publication.sql'
class AgentPublicationMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.sql=(RESOURCES/MIGRATION).read_text();cls.statements,cls.tables=inspect_migration(RESOURCES/MIGRATION)
 def test_registered_additive_two_tables_without_grants_or_seeds(self):
  self.assertEqual({'agent_publication_record','evaluation_acceptance_policy_decision'},set(self.tables))
  self.assertEqual(2,len(self.statements));assert_registered(self,MIGRATION,'db/migration/072-business-audit-read-index.sql',self.tables)
  self.assertNotRegex(self.sql,r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
 def test_tenant_and_exact_content_registry_links(self):
  refs=foreign_keys(self.tables['agent_publication_record'])
  self.assertIn(('version_registry',('tenant_id','record_id','content_sha256'),('tenant_id','version_id','content_sha256')),refs)
  self.assertIn(('operation_record',('tenant_id','operation_id'),('tenant_id','operation_id')),refs)
  for table in self.tables.values():
   for target,local,remote in foreign_keys(table):self.assertEqual('tenant_id',local[0]);self.assertEqual('tenant_id',remote[0])
 def test_unique_original_key_and_immutable_guard(self):
  self.assertRegex(self.sql,r'UNIQUE KEY uq_agent_publication_original_key\(tenant_id,created_by,kind,idempotency_key\)')
  text=(ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ProtectedTenantInterceptor.java').read_text()
  for name in self.tables:self.assertIn('"'+name+'"',text)
 def test_capability_controller_does_not_expose_reserved_policy_writes(self):
  path=ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AgentAuthoringController.java';text=path.read_text()
  self.assertIn('/api/agent-authoring/capabilities',text)
  self.assertNotIn('/policies',text);self.assertNotIn('/acceptance',text)
  self.assertIn('matchIfMissing=false',text)
