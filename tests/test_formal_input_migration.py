"""079 isolated real JSQLParser+SQLGlot checks. No Maven, global build or MySQL execution."""
from pathlib import Path
import re,unittest,json,hashlib
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT,RESOURCES,inspect_migration,foreign_keys
from test_migration_bundle import sqlglot_migration_source,parse_068_native_statements
P=ROOT/'business/medical-persistence/src/main'
TABLE='formal_evaluation_input_registration'
READS={'candidateVersions','original','lockActor','lockSource','databaseTime','registration','operation','operationById','audit','event'}
WRITES={'insertVersion','insertAccessSet','insertManifest','insertMember','insertLineage','insertArtifact','insertBinding','insertRegistration','saveOperation','saveAudit','saveEvent'}
def bound(sql):return sqlglot.parse_one(re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",sql),read='mysql')
class FormalInputMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path=RESOURCES/'db/migration/079-formal-evaluation-input.sql';cls.sql=cls.path.read_text();cls.parsed,cls.tables=inspect_migration(cls.path);cls.table=cls.tables[TABLE]
  cls.columns={c.name:c for c in cls.table.this.expressions if isinstance(c,exp.ColumnDef)}
  cls.xml=ET.parse(P/'resources/medical-mappers/FormalEvaluationInputMapper.xml').getroot();cls.statements={n.attrib['id']:(n.tag,' '.join(''.join(n.itertext()).split())) for n in cls.xml if n.tag in ('select','insert','update','delete')}
  cls.parents={};cls.keys={};cls.parentcols={}
  paths=[ROOT/'data-design/core.sql']+[RESOURCES/'db/migration'/name for name in ['002-identity-sources.sql','005-document-evidence.sql','009-conversation-events-receipts.sql','025-artifact-content-registry.sql']]
  for path in paths:
   for statement in sqlglot.parse(sqlglot_migration_source(path),read='mysql'):
    if isinstance(statement,exp.Create):
     name=statement.this.this.name;cls.parents[name]=statement;cls.keys[name]={tuple(c.name for c in k.expressions) for k in statement.find_all(exp.PrimaryKey)}|{tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint)};cls.parentcols[name]={c.name:c for c in statement.this.expressions if isinstance(c,exp.ColumnDef)}
    elif isinstance(statement,exp.Alter):
     name=statement.this.name;cls.keys.setdefault(name,set()).update(tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint));cls.parentcols.setdefault(name,{}).update({c.name:c for c in statement.find_all(exp.ColumnDef)})
 def test_real_parser_accepts_exact_one_additive_table(self):
  self.assertEqual([TABLE],list(self.tables));self.assertEqual(1,len(self.parsed));self.assertEqual({'create'},set(self.parsed[0]));self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE)\b')
 def test_068_compatibility_is_not_used(self):
  with self.assertRaisesRegex(ValueError,'068_COMPATIBILITY_SCOPE_ONLY'):parse_068_native_statements(self.path)
 def test_all_required_metadata_nonnull_no_defaults_and_no_mutable_state(self):
  self.assertEqual(33,len(self.columns));self.assertTrue({'schema_version','schema_manifest_sha256','created_by','created_at','classification'}<=self.columns.keys());self.assertFalse({'revision','updated_at','status'}&self.columns.keys())
  for name,col in self.columns.items():
   self.assertFalse(list(col.find_all(exp.DefaultColumnConstraint)),name)
   if name not in ('project_id','project_key'):self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in col.constraints),name)
 def test_project_is_always_enterprise_and_generated_key_preserves_null_scope(self):
  computed=list(self.table.find_all(exp.ComputedColumnConstraint));self.assertEqual(1,len(computed));self.assertTrue(computed[0].args['persisted']);self.assertIn("COALESCE(project_id, 'ENTERPRISE')",computed[0].this.sql(dialect='mysql'));self.assertIn("CHECK(project_id IS NULL AND resource_kind='SOURCE' AND artifact_kind='FORMAL_EVALUATION_INPUT')",self.sql)
 def test_exact_primary_and_original_key_operation_audit_uniqueness(self):
  self.assertEqual([('tenant_id','artifact_id')],[tuple(c.name for c in k.expressions) for k in self.table.find_all(exp.PrimaryKey)])
  self.assertEqual({('tenant_id','operation_id'),('tenant_id','created_by','idempotency_key'),('tenant_id','audit_id')},{tuple(c.name for c in k.this.expressions) for k in self.table.find_all(exp.UniqueColumnConstraint)})
 def test_all_eight_foreign_keys_are_full_unique_parent_keys_with_exact_types(self):
  fks=foreign_keys(self.table);self.assertEqual(8,len(fks))
  for target,local,remote in fks:
   self.assertEqual('tenant_id',local[0]);self.assertIn(remote,self.keys[target],(target,remote));self.assertEqual(len(local),len(remote))
   for left,right in zip(local,remote):self.assertEqual(self.columns[left].kind,self.parentcols[target][right].kind,(target,left,right))
 def test_artifact_binding_fk_preserves_complete_owner_schema_and_manifest(self):
  self.assertIn(('artifact_content_binding',('tenant_id','project_key','artifact_id','resource_kind','source_id','schema_ref','document_schema_sha256','access_set_id','dependency_sha256'),('tenant_id','project_key','artifact_id','resource_kind','resource_id','schema_ref','schema_document_sha256','dependency_access_set_id','dependency_manifest_sha256')),foreign_keys(self.table))
 def test_original_source_and_rights_are_linked_without_nullable_tuple_shortcuts(self):
  fks=foreign_keys(self.table);self.assertIn(('source_version',('tenant_id','source_id','source_version'),('tenant_id','source_id','source_version')),fks);self.assertIn(('rights_revision',('tenant_id','source_version','rights_revision'),('tenant_id','source_version','rights_revision')),fks);self.assertIn(('artifact',('tenant_id','project_key','artifact_id','document_sha256','object_version'),('tenant_id','project_key','artifact_id','sha256','object_version')),fks)
 def test_schema_hash_json_and_frozen_classifications_are_checked(self):
  for name in self.columns:
   if name.endswith('sha256'):self.assertIn(name+" REGEXP '^[a-f0-9]{64}$'",self.sql)
  for name in ('canonical_document','canonical_request','authorization_json','receipt_json'):self.assertIn('JSON_VALID('+name+')',self.sql)
  self.assertIn("classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')",self.sql);self.assertNotIn('CONFIDENTIAL',self.sql);self.assertIn("CHECK(schema_version='1.0.0'",self.sql)
 def test_request_receipt_identity_checks_match_fixed_columns(self):
  for path,column in [('source.version_id','source_version'),('source.content_sha256','document_sha256'),('rights_revision','rights_revision'),('purpose','purpose')]:self.assertIn("JSON_EXTRACT(canonical_request,'$."+path+"')) <=> "+column,self.sql)
  for path,column in [('artifact.version_id','artifact_id'),('artifact.content_sha256','document_sha256'),('operation_id','operation_id')]:self.assertIn("JSON_EXTRACT(receipt_json,'$."+path+"')) <=> "+column,self.sql)
 def test_mapper_exact_twenty_immutable_statements(self):
  self.assertEqual(READS|WRITES,set(self.statements));self.assertEqual(21,len(self.statements));self.assertTrue(all(self.statements[id][0]=='insert' for id in WRITES));self.assertTrue(all(self.statements[id][0]=='select' for id in READS))
  java=(P/'java/com/ruoyi/medical/persistence/mapper/FormalEvaluationInputMapper.java').read_text();self.assertEqual(READS|WRITES,set(re.findall(r'\b(\w+)\(Map<String,Object> values\)',java)))
 def test_every_mapper_sql_is_real_sqlglot_ast(self):
  for id,(_,sql) in self.statements.items():self.assertIsInstance(bound(sql),(exp.Insert,exp.Select),id)
 def test_registration_insert_maps_every_non_generated_column_exactly(self):
  stmt=bound(self.statements['insertRegistration'][1]);self.assertEqual(TABLE,stmt.this.this.name);columns=[c.name for c in stmt.this.expressions];values=stmt.expression.expressions[0].expressions
  self.assertEqual(set(self.columns)-{'tenant_id','project_key'},set(columns));self.assertEqual(len(columns),len(values));mapping={c:v.this for c,v in zip(columns,values)}
  self.assertEqual('SOURCE',mapping.pop('resource_kind'));self.assertEqual('FORMAL_EVALUATION_INPUT',mapping.pop('artifact_kind'))
  for col,value in mapping.items():expected='schemaManifest' if col=='schema_manifest_sha256' else col.split('_')[0]+''.join(p.title() for p in col.split('_')[1:]);self.assertEqual('@row.'+expected,value,col)
 def test_registration_read_maps_all_thirty_record_fields(self):
  rm=self.xml.find("resultMap[@id='registration']/constructor");self.assertEqual(30,len(rm));self.assertEqual([c for c in self.columns if c not in ('resource_kind','artifact_kind','project_key')],[arg.attrib['column'] for arg in rm]);self.assertIn('project_id IS NULL',self.statements['registration'][1])
 def test_actual_actor_lock_serializes_same_key_across_different_sources(self):
  self.assertEqual(bound('SELECT user_id FROM tenant_member WHERE user_id=#{userId} FOR UPDATE'),bound(self.statements['lockActor'][1]));self.assertEqual(bound('SELECT source_version FROM source_version WHERE source_version=#{sourceVersion} FOR UPDATE'),bound(self.statements['lockSource'][1]))
 def test_all_reads_are_uncached_including_history(self):
  for node in self.xml:
   if node.tag=='select':self.assertEqual('false',node.attrib['useCache']);self.assertEqual('true',node.attrib['flushCache'])
 def test_generic_lookup_uses_original_actor_operation_and_key(self):
  sql=self.statements['operation'][1];self.assertIn('subject_id=#{userId}',sql);self.assertIn("operation_name='FORMAL-EVALUATION-INPUT-REGISTER'",sql);self.assertIn('idempotency_key=#{key}',sql)
  sql=self.statements['operationById'][1];self.assertIn('subject_id=#{creatorId}',sql);self.assertIn('created_by=#{creatorId}',sql);self.assertIn('idempotency_key=#{key}',sql);self.assertIn('error_code IS NULL AND revision=1 AND updated_at=created_at',sql)
 def test_operation_audit_outbox_are_exactly_completed_and_same_time(self):
  sql=self.statements['saveOperation'][1];self.assertIn("'COMPLETED',201",sql);self.assertIn('NULL',sql);self.assertIn('#{now},#{classification},1,#{now}',sql)
  self.assertIn("'formal.evaluation.input_registered'",self.statements['saveAudit'][1]);self.assertIn("'formal.evaluation.input_registered'",self.statements['saveEvent'][1]);self.assertIn('occurred_at=#{now}',self.statements['event'][1]);self.assertIn('created_at=#{now}',self.statements['audit'][1])
 def test_zero_copy_insert_uses_actual_reference_fields_and_fixed_kind(self):
  sql=self.statements['insertArtifact'][1];self.assertIn("'FORMAL_EVALUATION_INPUT'",sql)
  for field in ('objectKey','objectVersion','sha256','sizeBytes','mediaType'):self.assertIn('#{reference.'+field+'}',sql)
  self.assertIn("NULL,#{artifactId},'SOURCE',#{sourceId}",self.statements['insertBinding'][1])
 def test_no_sim_agent_task_or_implicit_grant_in_producer_classes(self):
  for file in (P/'java/com/ruoyi/medical/persistence').glob('FormalEvaluationInput*.java'):
   source=file.read_text();self.assertNotRegex(source,r'\b(?:Simulation\w+|Agent\w+|Task\w+)\b',file.name);self.assertNotRegex(source,r'\b(?:insertGrant|saveGrant|writeDocumentProjection)\s*\(',file.name)
 def test_input_schema_hash_remains_exact_trusted_descriptor(self):
  self.assertEqual('4df9e483516bc858837897bb27bcc0a9df7d561d84b90800215fdc1e525aedda',hashlib.sha256((P/'resources/formal-evaluation-input.schema.json').read_bytes()).hexdigest())
if __name__=='__main__':unittest.main(verbosity=2)
