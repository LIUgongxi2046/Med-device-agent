from pathlib import Path
import unittest, re
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import inspect_migration,foreign_keys,ROOT,RESOURCES
from test_migration_bundle import sqlglot_migration_source
P=ROOT/'business/medical-persistence/src/main'
class CandidateMigrationTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.parsed,cls.tables=inspect_migration(RESOURCES/'db/migration/080-agent-publication.sql');cls.table=cls.tables['formal_agent_candidate_binding']
 def test_exact_single_table(self):self.assertEqual(['formal_agent_candidate_binding'],list(self.tables));self.assertEqual(1,len(self.parsed))
 def test_metadata(self):
  cols={c.name:c for c in self.table.this.expressions if isinstance(c,exp.ColumnDef)}
  for key in ['tenant_id','version_id','schema_version','schema_manifest_sha256','classification','created_by','created_at']:
   self.assertTrue(list(cols[key].find_all(exp.NotNullColumnConstraint)));self.assertFalse(list(cols[key].find_all(exp.DefaultColumnConstraint)))
 def test_parent_unique_keys_and_types(self):
  keys={};cols={}
  paths=[ROOT/'data-design/core.sql']+[RESOURCES/'db/migration'/n for n in ['002-identity-sources.sql','007-capability-model-budget.sql','025-artifact-content-registry.sql','027-template-definition.sql','073-agent-publication.sql','078-agent-execution-policy.sql']]
  for p in paths:
   if p.name.startswith(('080','084','086')):continue
   for s in sqlglot.parse(sqlglot_migration_source(p),read='mysql'):
    if isinstance(s,exp.Create):
     n=s.this.this.name;keys[n]={tuple(x.name for x in k.expressions) for k in s.find_all(exp.PrimaryKey)}|{tuple(x.name for x in k.this.expressions) for k in s.find_all(exp.UniqueColumnConstraint)};cols[n]={c.name:c for c in s.this.expressions if isinstance(c,exp.ColumnDef)}
    elif isinstance(s,exp.Alter):keys.setdefault(s.this.name,set()).update(tuple(x.name for x in k.this.expressions) for k in s.find_all(exp.UniqueColumnConstraint))
  child={c.name:c for c in self.table.this.expressions if isinstance(c,exp.ColumnDef)}
  for name,local,remote in foreign_keys(self.table):
   self.assertIn(remote,keys[name])
   for l,r in zip(local,remote):self.assertEqual(child[l].kind,cols[name][r].kind)
 def test_mapper_ids(self):
  root=ET.parse(P/'resources/medical-mappers/FormalAgentCandidateMapper.xml').getroot();self.assertEqual({'candidate','parents','list','insertCandidate','runtimeAuthor','runtimeList'},{n.attrib['id'] for n in root if n.tag in ('select','insert','update','delete')})
if __name__=='__main__':unittest.main()
