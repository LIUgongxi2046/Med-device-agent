"""Staged 078 AST contracts. Does not claim live MySQL, current grants or interceptor integration."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT, RESOURCES, inspect_migration, foreign_keys
D=Path(__file__).parent
DDL=RESOURCES/"db/migration/078-agent-execution-policy.sql"
MAPPER=ROOT/"business/medical-persistence/src/main/resources/medical-mappers/AgentExecutionPolicyMapper.xml"
PREFIX='agent_execution_policy_'
class PolicyStorageTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.parsed,cls.tables=inspect_migration(DDL)
  cls.sql=(DDL).read_text()
  cls.parents={}
  for path in [ROOT/'data-design/core.sql',RESOURCES/'db/migration/002-identity-sources.sql',RESOURCES/'db/migration/003-project-template.sql',RESOURCES/'db/migration/073-agent-publication.sql']:
   for node in sqlglot.parse(re.sub(r'(?m)^--.*$', '', path.read_text()),read='mysql'):
    if isinstance(node,exp.Create) and node.kind=='TABLE':cls.parents[node.this.this.name]=node
  cls.parents.update(cls.tables)
  cls.xml=ET.parse(MAPPER).getroot()
  cls.statements={n.attrib['id']:(' '.join(''.join(n.itertext()).split()),n.tag) for n in cls.xml if n.tag in {'select','insert','update','delete'}}
 def test_exact_additive_four_tables(self):
  self.assertEqual({PREFIX+x for x in ['version','component','decision','state']},set(self.tables));self.assertEqual(4,len(self.parsed));self.assertTrue(all(set(x)=={'create'} for x in self.parsed));self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE)\b')
 def test_complete_scoped_fk_unique_parent_keys_and_types(self):
  columns={n:{c.name:c for c in t.this.expressions if isinstance(c,exp.ColumnDef)} for n,t in self.parents.items()}
  keys={n:{tuple(x.name for x in k.expressions) for k in t.find_all(exp.PrimaryKey)}|{tuple(x.name for x in k.this.expressions) for k in t.find_all(exp.UniqueColumnConstraint)} for n,t in self.parents.items()}
  # Real additive parent unique key from frozen 027, parsed without compatibility fallback.
  alterations=sqlglot.parse(re.sub(r'(?m)^--.*$', '', (RESOURCES/'db/migration/027-template-definition.sql').read_text()),read='mysql')
  for a in alterations:
   if isinstance(a,exp.Alter) and a.this.name in keys:
    keys[a.this.name].update(tuple(x.name for x in k.this.expressions) for k in a.find_all(exp.UniqueColumnConstraint))
  for n,t in self.tables.items():
   for target,local,remote in foreign_keys(t):
    self.assertEqual('tenant_id',local[0]);self.assertIn(remote,keys[target],(n,target,remote))
    for l,r in zip(local,remote):
     self.assertEqual(columns[n][l].kind,columns[target][r].kind,(n,l,target,r))
     self.assertEqual([c.this.name for c in columns[n][l].find_all(exp.CharacterSetColumnConstraint)],[c.this.name for c in columns[target][r].find_all(exp.CharacterSetColumnConstraint)],(n,l,target,r))
 def test_full_metadata_not_null_no_defaults(self):
  for name,table in self.tables.items():
   cols={c.name:c for c in table.this.expressions if isinstance(c,exp.ColumnDef)}
   for field in ['schema_version','schema_manifest_sha256','created_by','created_at','classification']:
    self.assertIn(field,cols);self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in cols[field].constraints));self.assertFalse(any(isinstance(c.kind,exp.DefaultColumnConstraint) for c in cols[field].constraints))
   self.assertEqual('DATETIME(6)',cols['created_at'].kind.sql(dialect='mysql'));self.assertEqual('VARCHAR(36)',cols['project_key'].kind.sql(dialect='mysql'));self.assertEqual(1,len(list(cols['project_key'].find_all(exp.ComputedColumnConstraint))))
 def test_registry_links_bind_actual_content_hashes(self):
  for table,identity,hashfield in [('version','version_id','content_sha256'),('component','component_id','content_sha256'),('decision','decision_record_id','decision_sha256')]:
   self.assertIn(('version_registry',('tenant_id',identity,hashfield),('tenant_id','version_id','content_sha256')),foreign_keys(self.tables[PREFIX+table]))
 def test_scoped_author_and_decision_have_distinct_journal_kind(self):
  for table,identity,kind in [('version','author_record_id','author_kind'),('decision','decision_record_id','decision_kind')]:
   self.assertIn(('agent_publication_record',('tenant_id','project_key','policy_id',identity,kind),('tenant_id','project_key','resource_id','record_id','kind')),foreign_keys(self.tables[PREFIX+table]))
  self.assertIn("author_kind='EXECUTION_POLICY_AUTHOR'",self.sql);self.assertIn("decision_kind='EXECUTION_POLICY_DECISION'",self.sql)
 def test_lifecycle_revision_and_immutable_head_link(self):
  self.assertIn("before_revision=1 AND after_revision=2",self.sql);self.assertIn("before_revision=2 AND after_revision=3",self.sql);self.assertIn("status='DRAFT' AND revision=1 AND latest_decision_id IS NULL",self.sql)
  full=('tenant_id','project_key','policy_id','version_id','latest_decision_id','revision','status')
  self.assertIn((PREFIX+'decision',full,('tenant_id','project_key','policy_id','version_id','decision_record_id','after_revision','status')),foreign_keys(self.tables[PREFIX+'state']))
 def test_schema_classification_json_and_hash_checks(self):
  for name,table in self.tables.items():
   checks=' '.join(c.sql(dialect='mysql') for c in table.find_all(exp.CheckColumnConstraint))
   for value in ['1.0.0','PERSONAL_HEALTH','schema_manifest_sha256','content_sha256']:self.assertIn(value,checks)
  for field in ['fixed_json','content_json','decision_json']:self.assertIn('JSON_VALID('+field+')',self.sql);self.assertIn("JSON_TYPE(CAST("+field+" AS JSON))='OBJECT'",self.sql)
 def test_exact_mapper_ids_and_real_sqlglot_ast(self):
  self.assertEqual({'policy','state','components','decisions','parents','componentParents','source','role','model','list','insertRegistry','insertPolicy','insertState','insertComponent','insertLineage','insertDecision','advanceState','advanceGovernance'},set(self.statements))
  for name,(s,tag) in self.statements.items():
   parsed=sqlglot.parse_one(re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",s),read='mysql');self.assertIsNotNone(parsed,name)
   if tag=='insert':
    self.assertIsInstance(parsed,exp.Insert);self.assertEqual(len(parsed.this.expressions),len(parsed.expression.expressions[0].expressions),name)
 def test_all_required_insert_columns_and_server_metadata_are_present(self):
  for name,(s,tag) in self.statements.items():
   if tag!='insert':continue
   ast=sqlglot.parse_one(re.sub(r'#\{[^}]+\}',"'@'",s),read='mysql');table=ast.this.this.name
   if table not in self.tables:continue
   inserted={c.name for c in ast.this.expressions};required={c.name for c in self.tables[table].this.expressions if isinstance(c,exp.ColumnDef) and any(isinstance(k.kind,exp.NotNullColumnConstraint) for k in c.constraints)}-{'tenant_id'}
   self.assertLessEqual(required,inserted,(name,required-inserted))
   self.assertIn('#{userId}',s);self.assertIn('#{now}',s);self.assertIn('#{schemaManifest}',s)
 def test_mutability_and_actor_cas_are_narrow(self):
  mutations={n for n,(s,k) in self.statements.items() if k in {'update','delete'}};self.assertEqual({'advanceState','advanceGovernance'},mutations)
  cas=self.statements['advanceState'][0]
  for exact in ['d.created_by=#{userId}','status=#{beforeStatus}','revision=#{beforeRevision}','content_sha256=#{contentHash}','project_id <=> #{projectId}']:self.assertIn(exact,cas)
  self.assertIn('FOR UPDATE',self.statements['state'][0]);self.assertIn('FOR UPDATE',self.statements['policy'][0])
if __name__=='__main__':unittest.main(verbosity=2)
