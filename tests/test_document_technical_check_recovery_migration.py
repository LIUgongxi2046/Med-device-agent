import sys,re,hashlib,unittest
from pathlib import Path
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
R=Path(__file__).resolve().parents[1]
MIGRATION=R/'business/medical-data/src/main/resources/db/migration/071-document-technical-check-recovery.sql'
MAPPER=R/'business/medical-persistence/src/main/resources/medical-mappers/DocumentTechnicalCheckMapper.xml'
sys.path.insert(0,str(R/'tests'))
from test_migration_bundle import normalize_liquibase_comments
from test_simulation_input_artifact_migration import foreign_keys,inspect_migration
class Contract(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.sql=(MIGRATION).read_text()
  c.ast=sqlglot.parse(normalize_liquibase_comments(c.sql),read='mysql')
  c.native,c.tables=inspect_migration(MIGRATION)
  assert len(c.native)==len(c.ast)==len(c.tables)==2
  c.mapper=ET.parse(MAPPER).getroot()
 def test_only_two_additive_immutable_tables(self):
  self.assertEqual(2,len(self.ast));self.assertEqual({'document_technical_check_intent','document_technical_check_attempt'},set(self.tables))
  for table in self.tables.values():
   cols={x.name:x for x in table.this.expressions if isinstance(x,exp.ColumnDef)}
   self.assertFalse({'revision','updated_at','status'}&cols.keys())
   for col in cols.values():self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in col.constraints))
   self.assertEqual({k:'^[a-f0-9]{64}$' for k in cols if k.endswith('sha256')},{x.this.name:x.expression.this for x in table.find_all(exp.RegexpLike)})
 def test_all_actual_foreign_keys_target_actual_unique_parent_keys(self):
  parents=dict(self.tables)
  for path in [R/'data-design/core.sql',R/'business/medical-data/src/main/resources/db/migration/002-identity-sources.sql',R/'business/medical-data/src/main/resources/db/migration/006-review-signature-export.sql']:
   for t in sqlglot.parse(normalize_liquibase_comments(path.read_text()),read='mysql'):
    if isinstance(t,exp.Create) and isinstance(t.this,exp.Schema):parents[t.this.this.name]=t
  digest_alter=sqlglot.parse_one(normalize_liquibase_comments((R/'business/medical-data/src/main/resources/db/migration/027-template-definition.sql').read_text()).split(';')[0],read='mysql')
  self.assertIsInstance(digest_alter,exp.Alter);self.assertEqual('version_registry',digest_alter.this.name)
  total=0
  for table in self.tables.values():
   for name,childcols,parentcols in foreign_keys(table):
    total+=1;self.assertEqual('tenant_id',childcols[0]);parent=parents[name]
    keys={tuple(x.name for x in k.expressions) for k in parent.find_all(exp.PrimaryKey)}
    keys|={tuple(x.name for x in k.this.expressions) for k in parent.find_all(exp.UniqueColumnConstraint)}
    if name=='version_registry':keys|={tuple(x.name for x in k.this.expressions) for k in digest_alter.find_all(exp.UniqueColumnConstraint)}
    self.assertIn(parentcols,keys,(name,parentcols));self.assertEqual(len(childcols),len(parentcols))
    cc={x.name:x for x in table.this.expressions if isinstance(x,exp.ColumnDef)};pc={x.name:x for x in parent.this.expressions if isinstance(x,exp.ColumnDef)}
    for child,parentcol in zip(childcols,parentcols):
     self.assertEqual(cc[child].kind,pc[parentcol].kind)
     childcharset=[x.sql() for x in cc[child].constraints if isinstance(x.kind,exp.CharacterSetColumnConstraint)]
     parentcharset=[x.sql() for x in pc[parentcol].constraints if isinstance(x.kind,exp.CharacterSetColumnConstraint)]
     self.assertEqual(childcharset,parentcharset)
  self.assertEqual(7,total)
 def test_mapper_all_statements_real_ast_and_ledger_insert_only(self):
  statements={x.attrib['id']:x for x in self.mapper if x.tag in ('select','insert','update','delete')};self.assertEqual(39,len(statements))
  for id,node in statements.items():
   text=''.join(node.itertext());self.assertNotIn('${',text);ast=sqlglot.parse_one(re.sub(r'#\{[^}]+\}','?',text),read='mysql')
   if isinstance(ast,(exp.Delete,exp.Update)):self.assertNotIn(ast.this.name,self.tables)
   if id in ('insertRecoveryIntent','insertRecoveryAttempt'):
    columns={x.name for x in ast.this.expressions};table=self.tables[ast.this.this.name]
    self.assertEqual({x.name for x in table.this.expressions if isinstance(x,exp.ColumnDef)}-{'tenant_id'},columns)
   if id=='recoveryRevision':self.assertTrue(ast.args['locks'][0].args['update']);self.assertIn('subject_id=#{userId}',text);self.assertIn("operation_name='DOCUMENT-TECHNICAL-CHECK'",text)
  for id in ('recoveryIntent','recoveryAttempts'):
   text=''.join(statements[id].itertext())
   for predicate in ('project_id=#{projectId}','operation_id=#{operationId}','check_id=#{checkId}'):self.assertIn(predicate,text)
 def test_original_key_and_exact_metadata_constraints(self):
  table=self.tables['document_technical_check_intent']
  keys={tuple(x.name for x in k.this.expressions) for k in table.find_all(exp.UniqueColumnConstraint)}
  self.assertIn(('tenant_id','created_by','idempotency_key'),keys)
  for table in self.tables.values():
   checks=[x.this.sql(dialect='mysql') for x in table.find_all(exp.CheckColumnConstraint)]
   self.assertIn("schema_version = '1.0.0'",checks)
   self.assertIn("classification IN ('PUBLIC', 'INTERNAL', 'RESTRICTED', 'PERSONAL_HEALTH')",checks)
  key=next(c for c in self.tables['document_technical_check_intent'].this.expressions if isinstance(c,exp.ColumnDef) and c.name=='idempotency_key').sql(dialect='mysql')
  self.assertIn('VARCHAR(128)',key);self.assertIn('CHARACTER SET ascii',key);self.assertIn('COLLATE ascii_bin',key)
 def test_068_is_unchanged(self):
  path=R/'business/medical-data/src/main/resources/db/migration/068-document-technical-check.sql'
  self.assertEqual('488608fe321499210e309bec0cd7479f61dd488bc9ef28cf275cd22df96baec8',hashlib.sha256(path.read_bytes()).hexdigest())
if __name__=='__main__':unittest.main()
