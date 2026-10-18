"""068 actual DDL AST contracts; source validation does not imply live MySQL acceptance."""
import hashlib,json,re,unittest
import sqlglot
from sqlglot import exp
from test_simulation_preparation_migration import mapper_statements,inserted_values
from test_simulation_input_artifact_migration import ROOT,RESOURCES,inspect_migration,foreign_keys,assert_registered
MIGRATION='db/migration/068-document-technical-check.sql'
MANIFEST='document_technical_check_manifest';CHECK='document_technical_check'
class DocumentTechnicalCheckMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  from test_migration_bundle import parse_068_native_statements
  cls.sql=(RESOURCES/MIGRATION).read_text();cls.parsed=parse_068_native_statements(RESOURCES/MIGRATION)
  cls.tables={statement.this.this.name:statement for statement in cls.parsed if isinstance(statement,exp.Create)}
 def test_registration_precise_count_additive_no_seed(self):
  assert_registered(self,MIGRATION,'db/migration/067-document-review-return.sql',self.tables)
  self.assertEqual({MANIFEST,CHECK},set(self.tables))
  coverage=json.loads((RESOURCES/'db/migration/coverage.json').read_text())
  self.assertEqual({MANIFEST,CHECK},set(coverage['required_tables']['document_technical_check']))
  self.assertEqual(281,sum(map(len,coverage['required_tables'].values())))
  self.assertEqual(318,sum(map(len,coverage['required_tables'].values()))+len(coverage['platform_tables'])+len(coverage['framework_tables']))
  self.assertNotRegex(self.sql,r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|GRANT|REVOKE)\b')
 def test_001_through_067_frozen_against_part15(self):
  paths=sorted(p for p in (RESOURCES/'db/migration').iterdir() if p.suffix in ('.sql','.xml') and p.name[:3].isdigit() and int(p.name[:3])<=67)
  self.assertEqual(66,len(paths))
  self.assertEqual('6a793fe61e80a1583b019478b2764ce12ab879c8c860ff5855e774f2ef20d1cf',hashlib.sha256(b''.join(p.name.encode()+b'\0'+p.read_bytes()+b'\0' for p in paths)).hexdigest())
 def test_exact_workflow_constraint_preserves_all_other_job_requirements(self):
  # Parse executable ALTER directly after removing only complete Liquibase metadata comments.
  from test_migration_bundle import normalize_liquibase_comments
  statements=sqlglot.parse(normalize_liquibase_comments(self.sql),read='mysql')
  alters=[s for s in statements if isinstance(s,exp.Alter)]
  self.assertEqual(1,len(alters));alter=alters[0]
  self.assertEqual('system_job_run',alter.this.name)
  self.assertEqual(2,len(alter.args['actions']))
  drop=alter.args['actions'][0];self.assertIsInstance(drop,exp.Drop)
  self.assertEqual('CHECK',drop.kind);self.assertEqual(['ck_system_simulation_workflow'],[table.name for table in drop.args['tables']])
  checks=list(alter.find_all(exp.CheckColumnConstraint));self.assertEqual(1,len(checks))
  expected=sqlglot.parse_one("SELECT 1 WHERE (job_type IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK') AND workflow_id IS NULL) OR (job_type NOT IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK') AND workflow_id IS NOT NULL)",read='mysql').args['where'].this
  self.assertEqual(expected,checks[0].this)
 def test_actual_complete_scoped_foreign_keys(self):
  self.assertEqual({('project',('tenant_id','project_id'),('tenant_id','project_id')),
   ('version_registry',('tenant_id','execution_manifest_id','manifest_sha256'),('tenant_id','version_id','content_sha256')),
   ('tenant_member',('tenant_id','created_by'),('tenant_id','user_id'))},foreign_keys(self.tables[MANIFEST]))
  expected={
   (MANIFEST,('tenant_id','project_id','execution_manifest_id','manifest_sha256'),('tenant_id','project_id','execution_manifest_id','manifest_sha256')),
   ('check_run',('tenant_id','project_id','check_id','document_revision'),('tenant_id','project_id','check_id','document_revision')),
   ('document_revision_meta',('tenant_id','project_id','document_id','document_revision'),('tenant_id','project_id','document_id','document_revision')),
   ('execution_registry',('tenant_id','project_key','run_id'),('tenant_id','project_key','run_id')),
   ('system_job_run',('tenant_id','run_id'),('tenant_id','run_id')),
   ('version_registry',('tenant_id','execution_manifest_id','manifest_sha256'),('tenant_id','version_id','content_sha256')),
   ('dependency_access_manifest',('tenant_id','project_key','dependency_access_set_id','dependency_manifest_sha256'),('tenant_id','project_key','access_set_id','manifest_sha256')),
   ('operation_record',('tenant_id','operation_id'),('tenant_id','operation_id')),
   ('tenant_member',('tenant_id','created_by'),('tenant_id','user_id'))}
  for kind in ('coverage','findings'):expected.add(('artifact',('tenant_id','project_key',kind+'_artifact_id','dependency_access_set_id'),('tenant_id','project_key','artifact_id','dependency_access_set_id')))
  self.assertEqual(expected,foreign_keys(self.tables[CHECK]))
 def test_immutable_canonical_metadata_hash_and_identity_constraints(self):
  expected_unique={MANIFEST:{('tenant_id','project_id','manifest_sha256'),('tenant_id','project_id','execution_manifest_id','manifest_sha256')},CHECK:{('tenant_id','operation_id'),('tenant_id','run_id')}}
  for name,table in self.tables.items():
   self.assertEqual(('tenant_id','project_id','execution_manifest_id' if name==MANIFEST else 'check_id'),tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions))
   self.assertEqual(expected_unique[name],{tuple(c.name for c in key.this.expressions) for key in table.find_all(exp.UniqueColumnConstraint)})
   columns={c.name:c for c in table.this.expressions if isinstance(c,exp.ColumnDef)}
   self.assertFalse({'updated_at','revision','status'} & columns.keys())
   for key,column in columns.items():
    if key!='project_key':self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in column.constraints),key)
   hashes={node.this.name:node.expression.this for node in table.find_all(exp.RegexpLike)}
   self.assertEqual({key:'^[a-f0-9]{64}$' for key in columns if key.endswith('sha256')},hashes)
  checks=' '.join(c.this.sql(dialect='mysql') for c in self.tables[CHECK].find_all(exp.CheckColumnConstraint))
  self.assertIn("execution_mode = 'SYNC_REQUEST'",checks)
  self.assertIn('coverage_artifact_id <> findings_artifact_id',checks)
  for column in ('baseline_json','canonical_coverage','canonical_findings','canonical_input','canonical_manifest','canonical_request','coverage_intent','coverage_reference','findings_intent','findings_reference','receipt_json'):
   self.assertIn('JSON_VALID('+column+')',checks)
 def test_mapper_statements_actor_scope_and_insert_only_sidecars(self):
  mapper,statements=mapper_statements('DocumentTechnicalCheckMapper')
  self.assertEqual(39,len(statements))
  java=(ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/DocumentTechnicalCheckMapper.java').read_text()
  self.assertEqual(set(statements),set(re.findall(r'\b(\w+)\(Map<String,\s*Object> values\)',java)))
  for name,(element,statement,text) in statements.items():
   if element.tag=='select':self.assertEqual('false',element.attrib['useCache']);self.assertEqual('true',element.attrib['flushCache'])
   if isinstance(statement,(exp.Update,exp.Delete)):self.assertNotIn(statement.this.name,{MANIFEST,CHECK})
   if isinstance(statement,exp.Insert):self.assertNotIn('tenant_id',inserted_values(statement))
  for name,actor in [('operation','userId'),('operationById','creatorId')]:
   self.assertIn('subject_id=#{'+actor+'}',statements[name][2]);self.assertIn("operation_name='DOCUMENT-TECHNICAL-CHECK'",statements[name][2])
  for name in ('lockHead','operation'):
   locks=statements[name][1].args.get('locks',[]);self.assertEqual(1,len(locks));self.assertTrue(locks[0].args.get('update'))
  for name in ('updateCommand','completeOperation'):
   for clause in ('subject_id=#{userId}',"operation_name='DOCUMENT-TECHNICAL-CHECK'",'idempotency_key=#{key}','request_sha256=#{requestHash}',"state='PROCESSING'",'revision=#{operationRevision}'):
    self.assertIn(clause,statements[name][2])
  job=inserted_values(statements['insertJob'][1]);self.assertEqual('DOCUMENT_TECHNICAL_CHECK',job['job_type'].this);self.assertIsInstance(job['workflow_id'],exp.Null)
  for name,table in [('insertRegistration',CHECK),('insertManifest',MANIFEST)]:
   columns={c.name for c in self.tables[table].this.expressions if isinstance(c,exp.ColumnDef)}
   self.assertEqual(columns-{'tenant_id','project_key'},set(inserted_values(statements[name][1])))
  project={c.name:c for c in self.tables[CHECK].this.expressions if isinstance(c,exp.ColumnDef)}['project_key'].sql(dialect='mysql')
  self.assertIn('GENERATED ALWAYS AS (project_id) STORED',project)
 def test_existing_server_immutable_and_actor_guards_registered(self):
  java=ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
  immutable=re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);',(java/'ProtectedTenantInterceptor.java').read_text(),re.S).group(1)
  for table in (MANIFEST,CHECK):self.assertIn('"'+table+'"',immutable)
  self.assertIn('statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentTechnicalCheckMapper.")',(java/'TenantScope.java').read_text())
if __name__=='__main__':unittest.main()
