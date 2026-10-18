"""Real SQL parsers only. Does not execute MySQL or attest production evidence."""
from pathlib import Path
import unittest
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT,RESOURCES
from test_migration_bundle import sqlglot_migration_source
from test_simulation_input_artifact_migration import inspect_migration,foreign_keys

class Migration083(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.sql=(RESOURCES/'db/migration/083-formal-evaluation-runtime.sql').read_text()
  cls.parsed,cls.tables=inspect_migration(RESOURCES/'db/migration/083-formal-evaluation-runtime.sql')
 def test_all_foreign_keys_match_complete_unique_parent_keys_and_types(self):
  tables={};keys={};columns={}
  paths=[ROOT/'data-design/core.sql']+[RESOURCES/'db/migration'/name for name in ['002-identity-sources.sql','004-task-execution.sql','007-capability-model-budget.sql','009-conversation-events-receipts.sql','025-artifact-content-registry.sql','027-template-definition.sql']]+[RESOURCES/'db/migration/083-formal-evaluation-runtime.sql']
  for path in paths:
   for stmt in sqlglot.parse(sqlglot_migration_source(path),read='mysql'):
    if isinstance(stmt,exp.Create):
     name=stmt.this.this.name;tables[name]=stmt
     keys[name]={tuple(c.name for c in k.expressions) for k in stmt.find_all(exp.PrimaryKey)}|{tuple(c.name for c in k.this.expressions) for k in stmt.find_all(exp.UniqueColumnConstraint)}
     columns[name]={c.name:c for c in stmt.this.expressions if isinstance(c,exp.ColumnDef)}
    elif isinstance(stmt,exp.Alter):
     name=stmt.this.name;keys.setdefault(name,set()).update(tuple(c.name for c in k.this.expressions) for k in stmt.find_all(exp.UniqueColumnConstraint))
     columns.setdefault(name,{}).update({c.name:c for c in stmt.find_all(exp.ColumnDef)})
  for name,table in self.tables.items():
   for target,local,remote in foreign_keys(table):
    self.assertIn(remote,keys[target],(name,target,remote))
    for left,right in zip(local,remote):self.assertEqual(columns[name][left].kind,columns[target][right].kind,(name,target,left,right))
 def test_six_additive_tables(self):
  self.assertEqual(6,len(self.parsed));self.assertEqual({'formal_evaluation_execution','formal_evaluation_cell','formal_evaluation_node','professional_runtime_session','professional_runtime_observation','formal_evaluation_node_result'},set(self.tables))
  self.assertTrue(all(set(s)=={'create'} for s in self.parsed))
 def test_no_seed_or_shared_mutation(self):
  self.assertNotRegex(self.sql,r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE)\b')
 def test_all_foreign_keys_are_tenant_bound(self):
  for table in self.tables.values():
   for target,local,remote in foreign_keys(table):
    self.assertEqual('tenant_id',local[0]);self.assertEqual('tenant_id',remote[0]);self.assertEqual(len(local),len(remote))
 def test_no_task_run_parent_reinterpretation(self):
  self.assertNotIn('REFERENCES task_run',self.sql)
if __name__=='__main__':unittest.main(verbosity=2)
