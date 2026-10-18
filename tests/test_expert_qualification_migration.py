"""081 real JSQLParser/SQLGlot checks; isolated and no fallback, Maven or MySQL execution."""
from pathlib import Path
import re,unittest,json
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT,RESOURCES,inspect_migration,foreign_keys
from test_migration_bundle import sqlglot_migration_source,parse_068_native_statements
P=ROOT/'business/medical-persistence/src/main'
J=P/'java/com/ruoyi/medical/persistence'
TABLES={'expert_qualification_record','expert_qualification','expert_qualification_state','expert_score_binding','expert_score_authority_witness'}
def bound(sql):return sqlglot.parse_one(re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",sql),read='mysql')
class ExpertQualificationMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path=RESOURCES/'db/migration/081-expert-qualification.sql';cls.sql=cls.path.read_text();cls.parsed,cls.tables=inspect_migration(cls.path)
  cls.xml=ET.parse(P/'resources/medical-mappers/ExpertQualificationMapper.xml').getroot();cls.statements={n.attrib['id']:(n.tag,' '.join(''.join(n.itertext()).split())) for n in cls.xml if n.tag in ('select','insert','update','delete')};cls.parents={};cls.keys={};cls.cols={}
  paths=[ROOT/'data-design/core.sql']+[RESOURCES/'db/migration'/n for n in ['002-identity-sources.sql','008-governance-evaluation-connector.sql','009-conversation-events-receipts.sql','056-evaluation-results.sql','025-artifact-content-registry.sql','027-template-definition.sql']]+[cls.path]
  for path in paths:
   for statement in sqlglot.parse(sqlglot_migration_source(path),read='mysql'):
    if isinstance(statement,exp.Create):
     name=statement.this.this.name;cls.parents[name]=statement;cls.keys[name]={tuple(c.name for c in k.expressions) for k in statement.find_all(exp.PrimaryKey)}|{tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint)};cls.cols[name]={c.name:c for c in statement.this.expressions if isinstance(c,exp.ColumnDef)}
    elif isinstance(statement,exp.Alter):
     name=statement.this.name;cls.keys.setdefault(name,set()).update(tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint));cls.cols.setdefault(name,{}).update({c.name:c for c in statement.find_all(exp.ColumnDef)})
 def test_exact_five_additive_tables_real_jsqlparser(self):self.assertEqual(TABLES,set(self.tables));self.assertEqual(5,len(self.parsed));self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE)\b')
 def test_068_fallback_is_forbidden(self):
  with self.assertRaisesRegex(ValueError,'068_COMPATIBILITY_SCOPE_ONLY'):parse_068_native_statements(self.path)
 def test_metadata_is_nonnull_without_defaults_on_every_table(self):
  for t,columns in self.cols.items():
   if t not in TABLES:continue
   for n in ['schema_version','schema_manifest_sha256','created_by','created_at','classification']:
    self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in columns[n].constraints),(t,n));self.assertFalse(list(columns[n].find_all(exp.DefaultColumnConstraint)))
 def test_full_fk_tuples_have_exact_unique_parent_and_types(self):
  count=0
  for t,table in self.tables.items():
   for target,local,remote in foreign_keys(table):
    count+=1;self.assertEqual('tenant_id',local[0]);self.assertEqual(len(local),len(set(local)),(t,local));self.assertIn(remote,self.keys[target],(t,target,remote))
    for left,right in zip(local,remote):self.assertEqual(self.cols[t][left].kind,self.cols[target][right].kind,(t,target,left,right))
  self.assertGreaterEqual(count,25)
 def test_every_scoped_table_has_stored_enterprise_safe_project_key(self):
  for t,table in self.tables.items():
   computed=list(table.find_all(exp.ComputedColumnConstraint));self.assertEqual(1,len(computed),t);self.assertTrue(computed[0].args['persisted']);self.assertIn("COALESCE(project_id, 'ENTERPRISE')",computed[0].this.sql(dialect='mysql'))
 def test_qualification_links_actual_author_record_kind_scope_and_hash(self):self.assertIn(('expert_qualification_record',('tenant_id','project_key','qualification_id','author_record_id','author_kind','content_sha256'),('tenant_id','project_key','qualification_id','record_id','kind','content_sha256')),foreign_keys(self.tables['expert_qualification']))
 def test_score_and_witness_link_exact_original_cell_and_definition(self):
  for t in ['expert_score_binding','expert_score_authority_witness']:
   self.assertIn(('evaluation_score_observation',('tenant_id','evaluation_id','sample_id','candidate_id','dimension','repeat_index','score_id'),('tenant_id','evaluation_id','sample_id','agent_version_id','dimension','repeat_index','score_id')),foreign_keys(self.tables[t]));self.assertIn(('evaluation_definition',('tenant_id','evaluation_id','definition_sha256'),('tenant_id','evaluation_id','content_sha256')),foreign_keys(self.tables[t]))
 def test_binding_links_exact_qualification_and_independent_approval(self):
  fks=foreign_keys(self.tables['expert_score_binding']);self.assertIn(('expert_qualification',('tenant_id','qualification_id','qualification_sha256'),('tenant_id','qualification_id','content_sha256')),fks);self.assertIn(('expert_qualification_record',('tenant_id','qualification_id','approval_id','approval_kind','approval_sha256'),('tenant_id','qualification_id','record_id','kind','content_sha256')),fks)
 def test_original_key_and_operation_are_unique(self):self.assertIn(('tenant_id','created_by','kind','idempotency_key'),self.keys['expert_qualification_record']);self.assertIn(('tenant_id','operation_id'),self.keys['expert_qualification_record'])
 def test_hash_json_and_classification_checks_are_complete(self):
  for t,table in self.tables.items():
   sql=table.sql(dialect='mysql')
   for name in self.cols[t]:
    if name.endswith('sha256'):self.assertIn("REGEXP_LIKE("+name+", '^[a-f0-9]{64}$')",sql)
    if name.endswith('_json'):self.assertIn('JSON_VALID('+name+')',sql)
   self.assertIn("classification IN ('PUBLIC', 'INTERNAL', 'RESTRICTED', 'PERSONAL_HEALTH')",sql);self.assertNotIn('CONFIDENTIAL',sql)
 def test_mapper_all_actual_ids_match_java(self):
  self.assertEqual(set(self.statements),set(re.findall(r'\b(\w+)\(Map<String,Object> values\)',(J/'mapper/ExpertQualificationMapper.java').read_text())));self.assertEqual(34,len(self.statements))
 def test_every_mapper_statement_has_actual_ast(self):
  for id,(_,sql) in self.statements.items():self.assertIsInstance(bound(sql),(exp.Insert,exp.Select,exp.Update),id)
 def test_only_state_cas_can_update(self):
  self.assertEqual(['advanceHead'],[id for id,(kind,sql) in self.statements.items() if kind=='update']);self.assertFalse(any(kind=='delete' for kind,sql in self.statements.values()));sql=self.statements['advanceHead'][1]
  for field in ['qualification_id=#{qualificationId}','status=#{previousStatus}','revision=#{expected}','decision_id <=> #{previousDecision}']:self.assertIn(field,sql)
 def test_actor_and_state_lock_are_real_uncached_for_update(self):
  for id in ['lockActor','lockHead']:self.assertIn('FOR UPDATE',self.statements[id][1])
  for n in self.xml:
   if n.tag=='select':self.assertEqual('false',n.attrib['useCache']);self.assertEqual('true',n.attrib['flushCache'])
 def test_insert_columns_cover_every_non_generated_column(self):
  for id,t in [('insertJournal','expert_qualification_record'),('insertQualification','expert_qualification'),('insertHead','expert_qualification_state'),('insertScoreBinding','expert_score_binding'),('insertScoreAuthority','expert_score_authority_witness')]:
   stmt=bound(self.statements[id][1]);self.assertEqual(t,stmt.this.this.name);self.assertEqual(set(self.cols[t])-{'tenant_id','project_key'},{c.name for c in stmt.this.expressions},id)
 def test_witness_only_joins_real_original_score_operation(self):
  sql=self.statements['originalScoreOperation'][1]
  for piece in ["operation_name='EV-02'",'subject_id=#{creatorId}','created_at=#{witness.createdAt}','target_id=#{witness.evaluationId}','error_code IS NULL']:self.assertIn(piece,sql)
 def test_actual_subject_prefix_and_immutable_tables_registered_in_actual_guard(self):
  self.assertIn('mapper.ExpertQualificationMapper.',(J/'TenantScope.java').read_text());guard=(J/'ProtectedTenantInterceptor.java').read_text()
  for table in TABLES-{'expert_qualification_state'}:self.assertIn('"'+table+'"',guard)
 def test_no_new_grant_model_call_or_score_generation_in_new_service(self):
  service=(J/'ExpertQualificationService.java').read_text();self.assertNotRegex(service,r'\b(?:insertScore|new EvaluationProtocol.Score|insertGrant|saveGrant|invokeModel)\b')
if __name__=='__main__':unittest.main(verbosity=2)
