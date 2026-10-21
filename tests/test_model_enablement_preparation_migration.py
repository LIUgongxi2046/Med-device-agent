"""087 isolated real-parser storage contracts. No MySQL, provider or permission acceptance is claimed."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re, unittest, json
import sqlglot
from sqlglot import exp
from migration_source_parser import parse_schema_source
from test_simulation_input_artifact_migration import ROOT, RESOURCES, inspect_migration, foreign_keys
from test_migration_bundle import parse_068_native_statements
P=ROOT/'business/medical-persistence/src/main'
TABLE='model_enablement_policy_preparation'
READS={'classifications','original','lockActor','lockSource','databaseTime','stored','operation','operationById','audit','event'}
WRITES={'insertStored','insertOperation','insertAudit','insertEvent'}
FIELDS=['tenant_id','project_id','preparation_id','config_id','model_revision','profile_revision','source_id','source_version','rights_revision','source_sha256','original_object','original_authorization_sha256','snapshot_id','snapshot_sha256','parse_version','canonical_request','request_body_sha256','operation_request_sha256','idempotency_key','request_context','request_session','authorization_json','authorization_sha256','receipt_json','operation_id','audit_id','schema_manifest_sha256','created_by','created_at','classification']
RENAME={'request_body_sha256':'requestSha256','idempotency_key':'originalKey','request_context':'originalContext','request_session':'originalSession','schema_manifest_sha256':'workflowSha256'}
def camel(s):return s.split('_')[0]+''.join(p.title() for p in s.split('_')[1:])
def bound(s):return sqlglot.parse_one(re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",s),read='mysql')
def keys(table):return {tuple(c.name for c in k.expressions) for k in table.find_all(exp.PrimaryKey)}|{tuple(c.name for c in k.this.expressions) for k in table.find_all(exp.UniqueColumnConstraint)}
def catalogue():
 tables={};uniques={}
 for path in [ROOT/'data-design/core.sql',*sorted((RESOURCES/'db/migration').glob('*.sql'))]:
  for stmt in parse_schema_source(path.read_text())[0]:
   if isinstance(stmt,exp.Create) and isinstance(stmt.this,exp.Schema):
    name=stmt.this.this.name;tables[name]={c.name:c for c in stmt.this.expressions if isinstance(c,exp.ColumnDef)};uniques[name]=keys(stmt)
   elif isinstance(stmt,exp.Alter):
    name=stmt.this.name;tables.setdefault(name,{}).update({c.name:c for c in stmt.find_all(exp.ColumnDef)});uniques.setdefault(name,set()).update(keys(stmt))
 return tables,uniques
class EnablementMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path=RESOURCES/'db/migration/087-model-enablement-policy-preparation.sql';cls.sql=cls.path.read_text();cls.parsed,cls.tables=inspect_migration(cls.path);cls.table=cls.tables[TABLE];cls.columns={c.name:c for c in cls.table.this.expressions if isinstance(c,exp.ColumnDef)}
  cls.xml=ET.parse(P/'resources/medical-mappers/ModelEnablementPolicyMapper.xml').getroot();cls.statements={n.attrib['id']:(n.tag,' '.join(''.join(n.itertext()).split())) for n in cls.xml if n.tag in ('select','insert','update','delete')}
  cls.parentcols,cls.parentkeys=catalogue();cls.parentcols[TABLE]=cls.columns
  cls.schema=json.loads((P/'resources/model-enablement-policy-preparation.schema.json').read_text())
 def test_real_jsqlparser_exact_one_additive_table(self):
  self.assertEqual(1,len(self.parsed));self.assertEqual({TABLE},set(self.tables));self.assertEqual({'create'},set(self.parsed[0]));self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|TRUNCATE|GRANT|REVOKE)\b')
 def test_no_068_compatibility_escape(self):
  with self.assertRaisesRegex(ValueError,'068_COMPATIBILITY_SCOPE_ONLY'):parse_068_native_statements(self.path)
 def test_exact_closed_thirty_four_columns(self):
  self.assertEqual(set(FIELDS)|{'schema_version','configuration_kind','business_purpose','project_key'},set(self.columns));self.assertEqual(34,len(self.columns));self.assertFalse({'revision','updated_at','status'}&self.columns.keys())
 def test_all_metadata_nonnull_no_defaults(self):
  for name,col in self.columns.items():
   self.assertFalse(list(col.find_all(exp.DefaultColumnConstraint)),name)
   if name!='project_key':self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in col.constraints),name)
  self.assertEqual('DATETIME(6)',self.columns['created_at'].kind.sql('mysql'))
 def test_generated_scope_cannot_bypass_project_owner(self):
  computed=list(self.table.find_all(exp.ComputedColumnConstraint));self.assertEqual(1,len(computed));self.assertTrue(computed[0].args['persisted']);self.assertEqual("COALESCE(project_id, 'ENTERPRISE')",computed[0].this.sql('mysql'))
  self.assertIn("configuration_kind='MODEL' AND business_purpose='MODEL_ENABLEMENT'",self.sql)
 def test_primary_original_actor_key_operation_audit_uniqueness(self):
  self.assertEqual({('tenant_id','preparation_id'),('tenant_id','operation_id'),('tenant_id','created_by','idempotency_key'),('tenant_id','audit_id')},keys(self.table))
 def test_all_sixteen_fks_full_parent_unique_and_type(self):
  fks=foreign_keys(self.table);self.assertEqual(16,len(fks))
  for table,local,remote in fks:
   self.assertEqual('tenant_id',local[0]);self.assertEqual('tenant_id',remote[0]);self.assertIn(remote,self.parentkeys[table],(table,remote));self.assertEqual(len(local),len(remote))
   for l,r in zip(local,remote):
    self.assertIn(r,self.parentcols[table]);self.assertEqual(self.columns[l].kind,self.parentcols[table][r].kind,(table,l,r))
 def test_source_identity_rights_and_real_parse_are_one_scoped_tuple(self):
  fks=foreign_keys(self.table)
  for table,cols in [('source_version',('tenant_id','source_id','source_version')),('rights_revision',('tenant_id','source_version','rights_revision')),('parse_version',('tenant_id','source_version','parse_version'))]:self.assertIn((table,cols,cols),fks)
 def test_snapshot_contains_exact_source_parse_member(self):
  cols=('tenant_id','project_id','snapshot_id','source_version','parse_version');self.assertIn(('scope_member',cols,cols),foreign_keys(self.table));cols=cols[:3];self.assertIn(('scope_snapshot',cols,cols),foreign_keys(self.table))
 def test_source_and_snapshot_hashes_bind_registry_parent(self):
  for id,hash in [('source_version','source_sha256'),('snapshot_id','snapshot_sha256')]:self.assertIn(('version_registry',('tenant_id',id,hash),('tenant_id','version_id','content_sha256')),foreign_keys(self.table))
 def test_model_and_profile_cannot_move_to_another_project(self):
  fks=foreign_keys(self.table);self.assertIn(('configuration_head',('tenant_id','project_key','config_id','configuration_kind'),('tenant_id','project_key','config_id','kind')),fks);self.assertIn(('model_config_revision',('tenant_id','config_id','model_revision'),('tenant_id','config_id','model_config_revision')),fks);self.assertIn(('model_configuration_definition',('tenant_id','model_revision'),('tenant_id','model_config_revision')),fks);cols=('tenant_id','project_id','profile_revision');self.assertIn(('project_profile_revision',cols,cols),fks)
 def test_operation_audit_outbox_all_required_parents(self):
  for table,l,r in [('operation_record','operation_id','operation_id'),('audit_event','audit_id','audit_id'),('outbox_event','operation_id','event_id')]:self.assertIn((table,('tenant_id',l),('tenant_id',r)),foreign_keys(self.table))
 def test_every_hash_and_frozen_classification_is_checked(self):
  for name in self.columns:
   if name.endswith('sha256'):self.assertIn(name+" REGEXP '^[a-f0-9]{64}$'",self.sql)
  self.assertIn("classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')",self.sql);self.assertNotIn('CONFIDENTIAL',self.sql);self.assertIn("schema_version='1.0.0'",self.sql)
 def test_original_object_actual_five_fields_and_bounded_bytes(self):
  self.assertIn('JSON_LENGTH(original_object) <=> 5',self.sql);self.assertIn("JSON_EXTRACT(original_object,'$.sha256')) <=> source_sha256",self.sql)
  for path in ('object_key','object_version','media_type','size_bytes'):self.assertIn("JSON_EXTRACT(original_object,'$."+path+"')",self.sql)
  self.assertIn('AS UNSIGNED) BETWEEN 1 AND 2097152',self.sql)
 def test_original_required_keys_cannot_escape_check_via_sql_null(self):
  for key,kind in [('object_key','STRING'),('object_version','STRING'),('media_type','STRING'),('size_bytes','INTEGER')]:self.assertIn("JSON_TYPE(JSON_EXTRACT(original_object,'$."+key+"')) <=> '"+kind+"'",self.sql)
  self.assertIn("JSON_TYPE(JSON_EXTRACT(receipt_json,'$.created_at')) <=> 'STRING'",self.sql)
 def test_all_json_objects_are_valid_nonempty_bounded(self):
  for name,max in [('original_object',8192),('canonical_request',8192),('authorization_json',4194304),('receipt_json',16384)]:self.assertIn('JSON_VALID('+name+')',self.sql);self.assertIn("JSON_TYPE(CAST("+name+" AS JSON)) <=> 'OBJECT'",self.sql);self.assertIn('OCTET_LENGTH('+name+') BETWEEN 2 AND '+str(max),self.sql)
 def test_closed_request_and_receipt_counts_agree_with_real_schema(self):
  for name,doc in [('Register','canonical_request'),('Receipt','receipt_json')]:
   shape=self.schema['$defs'][name];self.assertFalse(shape['additionalProperties']);self.assertEqual(set(shape['required']),set(shape['properties']));self.assertIn('JSON_LENGTH('+doc+') <=> '+str(len(shape['required'])),self.sql)
 def test_every_request_identity_is_null_safe_bound(self):
  for path,col in [('schema_version','schema_version'),('project_id','project_id'),('model_config_revision','model_revision'),('profile_revision','profile_revision'),('policy_basis.version_id','source_version'),('policy_basis.content_sha256','source_sha256'),('rights_revision','rights_revision'),('business_purpose','business_purpose')]:self.assertIn("JSON_EXTRACT(canonical_request,'$."+path+"')) <=> "+col,self.sql)
 def test_receipt_exact_evidence_snapshot_and_operation_identities(self):
  for path,col in [('preparation_id','preparation_id'),('operation_id','operation_id'),('request_body_sha256','request_body_sha256'),('created_by','created_by'),('scope_snapshot_basis.version_id','snapshot_id'),('scope_snapshot_basis.content_sha256','snapshot_sha256'),('data_policy_evidence.source_version','source_version'),('data_policy_evidence.rights_revision','rights_revision'),('data_policy_evidence.project_id','project_id'),('data_policy_evidence.snapshot_id','snapshot_id'),('data_policy_evidence.parse_version','parse_version')]:self.assertIn("JSON_EXTRACT(receipt_json,'$."+path+"')) <=> "+col,self.sql)
 def test_mapper_exact_fourteen_read_or_append_statements(self):
  self.assertEqual(READS|WRITES,set(self.statements));self.assertEqual(14,len(self.statements));self.assertTrue(all(self.statements[id][0]=='select' for id in READS));self.assertTrue(all(self.statements[id][0]=='insert' for id in WRITES));java=(P/'java/com/ruoyi/medical/persistence/mapper/ModelEnablementPolicyMapper.java').read_text();self.assertEqual(READS|WRITES,set(re.findall(r'\b(\w+)\(Map<String,Object> values\)',java)))
 def test_every_mapper_statement_has_actual_sqlglot_ast(self):
  for id,(_,sql) in self.statements.items():self.assertIsInstance(bound(sql),(exp.Select,exp.Union,exp.Insert),id)
 def test_every_selected_joined_filtered_and_inserted_column_really_exists(self):
  for id,(_,sql) in self.statements.items():
   stmt=bound(sql)
   if isinstance(stmt,exp.Insert):
    self.assertIn(stmt.this.this.name,self.parentcols)
    for col in stmt.this.expressions:self.assertIn(col.name,self.parentcols[stmt.this.this.name],(id,col.name))
   for query in stmt.find_all(exp.Select):
    aliases={t.alias_or_name:t.name for t in query.find_all(exp.Table)}
    for c in query.find_all(exp.Column):
     if c.table:self.assertIn(c.name,self.parentcols[aliases[c.table]],(id,aliases[c.table],c.name))
     elif aliases:self.assertTrue(any(c.name in self.parentcols[t] for t in aliases.values()),(id,c.name))
 def test_no_fabricated_classification_columns_or_missing_owner(self):
  for name in ('source_version','parse_version','index_manifest','approval_decision','project_source_binding'):self.assertNotIn('classification',self.parentcols[name])
  stmt=bound(self.statements['classifications'][1]);queries=list(stmt.find_all(exp.Select));self.assertEqual(5,len(queries));self.assertEqual({'SOURCE_OWNER','RIGHTS','MODEL','MODEL_DEFINITION','PROFILE'},{q.expressions[0].this.this for q in queries});sql=self.statements['classifications'][1];self.assertIn('s.classification AS classification',sql);self.assertNotIn('sv.classification',sql);self.assertIn('ORDER BY kind',sql)
 def test_source_and_model_definition_classification_identity_is_real(self):
  sql=self.statements['classifications'][1]
  for text in ['sv.original_sha256 AS sha256','r.content_sha256 AS sha256','m.content_sha256 AS sha256','d.content_sha256 AS sha256','p.content_sha256 AS sha256','s.tenant_id=sv.tenant_id AND s.source_id=sv.source_id','r.source_version=#{sourceVersion} AND r.rights_revision=#{rightsRevision}','m.project_id=#{projectId}','p.project_id=#{projectId} AND p.profile_revision=#{profileRevision}']:self.assertIn(text,sql)
 def test_insert_every_non_generated_column_exactly(self):
  stmt=bound(self.statements['insertStored'][1]);self.assertEqual(TABLE,stmt.this.this.name);cols=[c.name for c in stmt.this.expressions];vals=stmt.expression.expressions[0].expressions;self.assertEqual(set(self.columns)-{'tenant_id','project_key'},set(cols));self.assertEqual(len(cols),len(vals));mapping={c:v.this for c,v in zip(cols,vals)}
  for col,value in [('schema_version','1.0.0'),('configuration_kind','MODEL'),('business_purpose','MODEL_ENABLEMENT')]:self.assertEqual(value,mapping.pop(col))
  for col,val in mapping.items():self.assertEqual('@row.'+RENAME.get(col,camel(col)),val,col)
 def test_readback_exact_thirty_field_record_order(self):
  rm=self.xml.find("resultMap[@id='stored']/constructor");self.assertEqual(FIELDS,[arg.attrib['column'] for arg in rm]);query=bound(self.statements['stored'][1]);self.assertEqual(FIELDS,[c.name for c in query.expressions]);self.assertIn('LIMIT 2',self.statements['stored'][1]);self.assertIn("business_purpose='MODEL_ENABLEMENT'",self.statements['stored'][1])
 def test_every_read_disables_caches_and_actual_locks_are_scoped(self):
  for node in self.xml:
   if node.tag=='select':self.assertEqual('false',node.attrib['useCache']);self.assertEqual('true',node.attrib['flushCache'])
  self.assertEqual(bound('SELECT user_id FROM tenant_member WHERE user_id=#{userId} FOR UPDATE'),bound(self.statements['lockActor'][1]));self.assertEqual(bound('SELECT source_version FROM source_version WHERE source_version=#{sourceVersion} FOR UPDATE'),bound(self.statements['lockSource'][1]))
 def test_generic_original_key_lookup_and_history_actor_are_distinct(self):
  sql=self.statements['operation'][1]
  for text in ['subject_id=#{userId}',"operation_name='MODEL-ENABLEMENT-POLICY-PREPARE'",'idempotency_key=#{key}']:self.assertIn(text,sql)
  sql=self.statements['operationById'][1]
  for text in ['subject_id=#{creatorId}','created_by=#{creatorId}','operation_id=#{operationId}','idempotency_key=#{key}','error_code IS NULL','revision=1','updated_at=created_at','classification=#{classification}']:self.assertIn(text,sql)
 def test_audit_outbox_recheck_full_original_metadata(self):
  audit=self.statements['audit'][1]
  for text in ['audit_id=#{auditId}','project_id=#{projectId}','subject_id=#{creatorId}','created_by=#{creatorId}','created_at=#{now}','request_id=#{operationId}','trace_id=#{operationId}',"action='model.enablement.policy_prepared'","result='SUCCESS'",'reason_code IS NULL',"schema_version='1.0.0'",'schema_manifest_sha256=#{operationManifest}','classification=#{classification}']:self.assertIn(text,audit)
  event=self.statements['event'][1]
  for text in ['event_id=#{operationId}','aggregate_id=#{preparationId}','aggregate_revision=1',"event_type='model.enablement.policy_prepared'",'occurred_at=#{now}']:self.assertIn(text,event)
 def test_append_operation_is_completed_201_and_original_target(self):
  sql=self.statements['insertOperation'][1];self.assertIn("'COMPLETED',201,#{response},#{preparationId},NULL",sql);self.assertIn('#{now},#{classification},1,#{now}',sql)
  for id in ('insertOperation','insertAudit'):self.assertIn('#{userId}',self.statements[id][1]);self.assertIn('#{operationManifest}',self.statements[id][1])
 def test_preparation_never_inserts_artifact_version_grant_or_provider_result(self):
  targets={bound(self.statements[id][1]).this.this.name for id in WRITES};self.assertEqual({TABLE,'operation_record','audit_event','outbox_event'},targets);self.assertNotRegex(self.sql,r'\bSIMULATION\b|MODEL_VERIFICATION|DOCUMENT_BODY')
if __name__=='__main__':unittest.main(verbosity=2)
