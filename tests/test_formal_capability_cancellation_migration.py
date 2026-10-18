"""082 immutable cancellation tombstone contracts, not a database execution test."""
import unittest
from sqlglot import exp
from runtime_migration_support import *
FILE='082-formal-capability-cancellation.sql'
class FormalCapabilityCancellationMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(c):c.path=RESOURCES/'db/migration'/FILE;c.native,c.tables=inspect_migration(c.path);c.ast,_=ast_tables(c.path)
 def test_one_exact_additive_table_without_seed(self):
  self.assertEqual({'formal_capability_cancellation'},set(self.tables));self.assertEqual(1,len(self.native));self.assertEqual(1,len(self.ast));self.assertIsInstance(self.ast[0],exp.Create)
  registered(self,FILE,self.tables);self.assertNotRegex(self.path.read_text(),r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
 def test_tenant_fk_actual_parent_uniqueness_and_types(self):standard_metadata(self,self.tables);parent_contracts(self,self.tables)
 def test_original_and_cancel_keys_are_actor_scoped_unique(self):
  keys=unique_keys(self.tables['formal_capability_cancellation'])
  for key in [('tenant_id','created_by','cancel_key'),('tenant_id','created_by','original_kind','original_key'),('tenant_id','operation_id')]:self.assertIn(key,keys)
  cols=columns(self.tables['formal_capability_cancellation']);self.assertNotIn('revision',cols);self.assertNotIn('updated_at',cols)
  for name in ['cancel_key','original_key']:
   self.assertIn('VARCHAR(128)',cols[name].sql(dialect='mysql'));self.assertIn('ascii_bin',cols[name].sql(dialect='mysql'))
 def test_only_author_request_kind_and_object_json_are_accepted(self):
  checks=' '.join(k.sql(dialect='mysql') for k in self.tables['formal_capability_cancellation'].find_all(exp.CheckColumnConstraint))
  self.assertIn("original_kind = 'FORMAL_CAPABILITY_AUTHOR'",checks);self.assertIn("JSON_TYPE(fixed_json) = 'OBJECT'",checks)
 def test_cancellation_is_immutable_and_mapper_subject_required(self):
  tenant_catalogues(self,self.tables);s=mapper_prefix(self,'FormalCapabilityCancellationMapper')
  self.assertEqual({'cancellationByOriginal','cancellationByKey','cancellationOperation','cancellationAudit','cancellationEvent','insertCancelOperation','insertCancellation'},set(s))
  self.assertFalse(any(n.tag in {'update','delete'} for n in s.values()))
  for key in ['cancellationByOriginal','cancellationByKey']:
   text=''.join(s[key].itertext());self.assertIn('created_by=#{userId}',text);self.assertIn('LIMIT 2',text)
 def test_recovery_requires_original_operation_audit_and_outbox(self):
  s=mapper_prefix(self,'FormalCapabilityCancellationMapper');operation=''.join(s['cancellationOperation'].itertext())
  for part in ["operation_name='FORMAL-CAPABILITY-CANCEL'","state='COMPLETED'",'request_sha256=#{requestHash}','target_id=#{cancellationId}','subject_id=#{userId}','created_by=#{userId}','http_status=200']:
   self.assertIn(part,operation)
  for key in ['cancellationAudit','cancellationEvent']:self.assertIn("formal.capability.request.cancelled",''.join(s[key].itertext()))
