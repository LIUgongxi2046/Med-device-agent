"""Actual repository SQL-column regression, kept separate from frozen084 results."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re, unittest
import sqlglot
from sqlglot import exp
from test_migration_bundle import ROOT,RESOURCES,sqlglot_migration_source
MAPPER=ROOT/'business/medical-persistence/src/main/resources/medical-mappers/FormalEvaluationPolicyMapper.xml'
def query(path,legacy=False):
 text=path.read_text()
 if legacy:text=text.replace("WHEN 'SOURCE_VERSION' THEN s.classification","WHEN 'SOURCE_VERSION' THEN sv.classification",1)
 node=ET.fromstring(text).find("select[@id='dependencyClassification']")
 return sqlglot.parse_one(re.sub(r'#\{[^}]+\}',"'value'",''.join(node.itertext())),read='mysql')
def catalogue():
 tables={}
 for path in [ROOT/'data-design/core.sql',*sorted((RESOURCES/'db/migration').glob('*.sql'))]:
  for stmt in sqlglot.parse('\n'.join(line for line in path.read_text().splitlines() if not line.lstrip().startswith('--')),read='mysql'):
   if isinstance(stmt,exp.Create) and isinstance(stmt.this,exp.Schema):tables[stmt.this.this.name]={c.name for c in stmt.this.expressions if isinstance(c,exp.ColumnDef)}
   elif isinstance(stmt,exp.Alter):
    for c in stmt.find_all(exp.ColumnDef):tables.setdefault(stmt.this.name,set()).add(c.name)
 return tables
class Columns(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.tables=catalogue()
 def missing(self,stmt):
  aliases={t.alias_or_name:t.name for t in stmt.find_all(exp.Table)}
  return {(aliases.get(c.table,c.table),c.name) for c in stmt.find_all(exp.Column) if c.table and c.name not in self.tables[aliases.get(c.table,c.table)]}
 def test_frozen_bug_is_reproduced_not_hidden(self):self.assertEqual({('source_version','classification')},self.missing(query(MAPPER,legacy=True)))
 def test_every_fixed_select_join_where_column_exists(self):self.assertEqual(set(),self.missing(query(MAPPER)))
 def test_real_owner_source_has_classification_but_version_does_not(self):self.assertNotIn('classification',self.tables['source_version']);self.assertIn('classification',self.tables['source']);self.assertIn('classification',self.tables['rights_revision'])
 def test_only_exact_invalid_column_changed(self):
  import hashlib
  text=MAPPER.read_text();self.assertNotIn("WHEN 'SOURCE_VERSION' THEN sv.classification",text)
  legacy=text.replace("WHEN 'SOURCE_VERSION' THEN s.classification","WHEN 'SOURCE_VERSION' THEN sv.classification",1)
  self.assertEqual("a5ce5894ccc8f110104178805156927b249a02d2cff5be575ac5dd56c8c6d5a3",hashlib.sha256(legacy.encode()).hexdigest())
 def test_java_still_rechecks_actual_rights_max(self):
  s=(ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/FormalEvaluationPolicyInputs.java').read_text();self.assertIn('sources.rights(source.rightsRevision())',s);self.assertIn('maxClassification(classification,rights.classification())',s)
if __name__=='__main__':unittest.main(verbosity=2)
