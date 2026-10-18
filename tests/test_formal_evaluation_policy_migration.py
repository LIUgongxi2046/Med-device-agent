"""084 additive migration: actual JSQLParser and SQLGlot, no MySQL or deployment claim."""
from pathlib import Path
import re,unittest
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import inspect_migration, foreign_keys, ROOT, RESOURCES
from test_migration_bundle import sqlglot_migration_source
P=ROOT/'business/medical-persistence/src/main'
class PolicyMigrationTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.sql=(RESOURCES/'db/migration/084-formal-evaluation-policy.sql').read_text();cls.parsed,cls.tables=inspect_migration(RESOURCES/'db/migration/084-formal-evaluation-policy.sql');cls.table=cls.tables['formal_evaluation_policy_input'];cls.xml=ET.parse(P/'resources/medical-mappers/FormalEvaluationPolicyMapper.xml').getroot()
 def test_additive_only_one_immutable_table(self):
  self.assertEqual(['formal_evaluation_policy_input'],list(self.tables));self.assertEqual(1,len(self.parsed));self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE)\b')
 def test_exact_nonnull_columns(self):
  columns={c.name:c for c in self.table.this.expressions if isinstance(c,exp.ColumnDef)};self.assertEqual({'tenant_id','record_id','content_sha256','snapshot_json','snapshot_sha256','input_schema_sha256','policy_schema_sha256'},set(columns))
  for c in columns.values():self.assertTrue(list(c.find_all(exp.NotNullColumnConstraint)));self.assertFalse(list(c.find_all(exp.DefaultColumnConstraint)))
 def test_exact_registry_and_journal_foreign_keys(self):
  self.assertEqual({('agent_publication_record',('tenant_id','record_id'),('tenant_id','record_id')),('version_registry',('tenant_id','record_id','content_sha256'),('tenant_id','version_id','content_sha256'))},set(foreign_keys(self.table)))
 def test_foreign_key_parent_keys_and_types(self):
  parents={};keys={};cols={}
  for path in [ROOT/'data-design/core.sql',RESOURCES/'db/migration/025-artifact-content-registry.sql',RESOURCES/'db/migration/027-template-definition.sql',RESOURCES/'db/migration/073-agent-publication.sql']:
   for s in sqlglot.parse(sqlglot_migration_source(path),read='mysql'):
    if isinstance(s,exp.Create):
     name=s.this.this.name;keys[name]={tuple(x.name for x in k.expressions) for k in s.find_all(exp.PrimaryKey)}|{tuple(x.name for x in k.this.expressions) for k in s.find_all(exp.UniqueColumnConstraint)};cols[name]={c.name:c for c in s.this.expressions if isinstance(c,exp.ColumnDef)}
    elif isinstance(s,exp.Alter):keys.setdefault(s.this.name,set()).update(tuple(x.name for x in k.this.expressions) for k in s.find_all(exp.UniqueColumnConstraint))
  child={c.name:c for c in self.table.this.expressions if isinstance(c,exp.ColumnDef)}
  for name,local,remote in foreign_keys(self.table):
   self.assertIn(remote,keys[name]);
   for left,right in zip(local,remote):self.assertEqual(child[left].kind,cols[name][right].kind)
 def test_hash_and_json_constraints(self):
  for c in ['content_sha256','snapshot_sha256','input_schema_sha256','policy_schema_sha256']:self.assertIn(c+" REGEXP '^[a-f0-9]{64}$'",self.sql)
  self.assertIn('JSON_VALID(snapshot_json)',self.sql)
 def test_exact_mapper_sql_ast_and_no_external_state_updates(self):
  nodes=[n for n in self.xml if n.tag in ('select','insert','update','delete')];self.assertEqual({'snapshot','insertSnapshot','dependencyClassification'},{n.attrib['id'] for n in nodes})
  for node in nodes:
   sql=re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",''.join(node.itertext()));self.assertIsInstance(sqlglot.parse_one(sql,read='mysql'),(exp.Select,exp.Insert));self.assertNotIn(node.tag,('update','delete'))
 def test_mapper_insert_leaves_tenant_to_guard(self):
  node=self.xml.find("insert[@id='insertSnapshot']");sql=re.sub(r'#\{([^}]+)\}',lambda m:"'@"+m[1]+"'",''.join(node.itertext()));stmt=sqlglot.parse_one(sql,read='mysql');self.assertEqual({'record_id','content_sha256','snapshot_json','snapshot_sha256','input_schema_sha256','policy_schema_sha256'},{c.name for c in stmt.this.expressions})
if __name__=='__main__':unittest.main()
