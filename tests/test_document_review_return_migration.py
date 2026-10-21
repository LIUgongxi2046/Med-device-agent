"""Offline real-parser checks of independent return-for-revision. No database claim."""
import hashlib,json,re,sys,unittest
from pathlib import Path
from xml.etree import ElementTree
import sqlglot
from sqlglot import exp
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'))
from test_simulation_input_artifact_migration import inspect_migration,foreign_keys,RESOURCES,assert_registered
MIGRATIONS=RESOURCES/'db/migration'
PERSISTENCE=ROOT/'business/medical-persistence'
JAVA=PERSISTENCE/'src/main/java/com/ruoyi/medical/persistence'
MAPPERS=PERSISTENCE/'src/main/resources/medical-mappers'
RETURN='document_revision_return';ASSIGN='document_revision_return_assignment'
class DocumentReviewReturnMigrationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.sql=(MIGRATIONS/'067-document-review-return.sql').read_text();cls.parsed,cls.tables=inspect_migration(MIGRATIONS/'067-document-review-return.sql')
  cls.mapper=ElementTree.parse(MAPPERS/'DocumentReviewReturnMapper.xml').getroot();cls.statements={}
  for item in cls.mapper:
   if item.tag in ('select','insert','update','delete'):
    text=''.join(item.itertext()).strip()
    if '${' in text:raise AssertionError('Unbounded substitution')
    cls.statements[item.attrib['id']]=(item,sqlglot.parse_one(re.sub(r'#\{[^}]+\}','?',text),read='mysql'),text)
 def test_two_independent_additive_tables_no_formal_review_mutation_or_seed(self):
  self.assertEqual({RETURN,ASSIGN},set(self.tables));self.assertEqual(2,len(self.parsed))
  self.assertNotRegex(self.sql,r'(?im)^\s*(INSERT|UPDATE|DELETE|DROP|TRUNCATE|GRANT|REVOKE|ALTER)\b')
  self.assertEqual(2,len(re.findall(r'^--changeset medical-agent:067-',self.sql,re.M)))
 def test_tenant_document_task_actor_and_operation_fixed_bindings(self):
  table=self.tables[RETURN]
  self.assertEqual(('tenant_id','project_id','return_id'),tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions))
  self.assertEqual({
   ('document_revision_meta',('tenant_id','project_id','document_id','document_revision'),('tenant_id','project_id','document_id','document_revision')),
   ('document_head',('tenant_id','project_id','document_id','task_id'),('tenant_id','project_id','document_id','task_id')),
   ('task_revision',('tenant_id','project_id','task_id','task_revision'),('tenant_id','project_id','task_id','task_revision')),
   ('tenant_member',('tenant_id','assignee_id'),('tenant_id','user_id')),
   ('tenant_member',('tenant_id','actor_id'),('tenant_id','user_id')),
   ('operation_record',('tenant_id','operation_id'),('tenant_id','operation_id'))},foreign_keys(table))
  identity=('tenant_id','project_id','return_id','document_id','document_revision','task_id','task_revision','assignee_id')
  self.assertEqual({(RETURN,identity,identity),('tenant_member',('tenant_id','created_by'),('tenant_id','user_id'))},foreign_keys(self.tables[ASSIGN]))
 def test_initial_assignment_and_decision_immutable_with_complete_request_receipt(self):
  for name,count in ((RETURN,20),(ASSIGN,17)):
   table=self.tables[name];columns={c.name:c for c in table.this.expressions if isinstance(c,exp.ColumnDef)}
   self.assertEqual(count,len(columns))
   for column in columns.values():self.assertTrue(any(isinstance(c.kind,exp.NotNullColumnConstraint) for c in column.constraints))
  checks=' '.join(c.this.sql(dialect='mysql') for c in self.tables[RETURN].find_all(exp.CheckColumnConstraint))
  for field in ('canonical_request','canonical_receipt'):self.assertIn('JSON_VALID('+field+')',checks)
  self.assertIn("decision = 'RETURN_FOR_REVISION'",checks);self.assertIn('created_by = actor_id',checks)
  for _,statement,_ in self.statements.values():
   if isinstance(statement,(exp.Update,exp.Delete)):self.assertNotIn(statement.this.name,{RETURN,ASSIGN})
 def test_return_cas_is_only_pending_to_returned_never_signature_or_formal_decision(self):
  updates={statement.this.name for _,statement,_ in self.statements.values() if isinstance(statement,exp.Update)}
  self.assertEqual({'task_head','document_head'},updates)
  task=self.statements['advanceTask'][2]
  for clause in ("status='RETURNED'","status='REVIEW_PENDING'",'revision=#{expectedTask}','current_revision=#{taskRevision}'):self.assertIn(clause,task)
  document=self.statements['advanceDocument'][2]
  for clause in ('revision=#{expectedDocument}','current_document_revision=#{documentRevision}',"status<>'SIGNED'"):self.assertIn(clause,document)
  inserts={statement.this.this.name for _,statement,_ in self.statements.values() if isinstance(statement,exp.Insert)}
  self.assertEqual({'operation_record',RETURN,ASSIGN,'review_invalidation'},inserts)
  self.assertNotIn('waiting_item',inserts);self.assertNotIn('review_decision',inserts);self.assertNotIn('signature_record',inserts)
 def test_real_current_signature_and_assignee_eligibility_reads(self):
  signatures=self.statements['signatures'][2]
  self.assertIn('document_id=#{documentId}',signatures);self.assertIn('document_revision=#{documentRevision}',signatures)
  grants=self.statements['targetGrants'][2]
  self.assertIn('subject_id=#{assigneeId}',grants);self.assertIn('action=#{targetAction}',grants);self.assertIn("status='ACTIVE'",grants)
  source=(JAVA/'DocumentReviewReturnService.java').read_text()
  self.assertIn('"document.read","DOCUMENT_BODY"',source);self.assertIn('"work_item.read","WORK_ITEM_READING"',source)
  self.assertIn('!assignee.equals(targetEligibility(',source)
 def test_informational_checks_cannot_claim_formal_currentness(self):
  source=self.statements['checks'][2]
  self.assertIn("c.target_kind='DOCUMENT'",source);self.assertIn('c.target_revision=#{documentRevision}',source)
  self.assertIn("'record_complete'",source);self.assertNotIn("'PASS'",source)
  service=(JAVA/'DocumentReviewReturnService.java').read_text()
  self.assertIn('put("formal_review_available",false)',service);self.assertIn('put("formal_check_currentness","NOT_VERIFIED")',service)
  self.assertNotIn('review_baseline',service);self.assertNotIn('insertBatch',service)
 def test_original_operation_recovery_has_actor_scope_and_immutable_joined_evidence(self):
  operation=self.statements['operation'][2]
  self.assertIn("operation_name='DOCUMENT-RETURN-FOR-REVISION-1'",operation);self.assertIn('subject_id=#{userId}',operation)
  joined=self.statements['decision'][2]
  for clause in ('a.return_id=r.return_id','a.document_revision=r.document_revision','a.task_revision=r.task_revision','a.assignee_id=r.assignee_id'):self.assertIn(clause,joined)
  for name,(item,statement,source) in self.statements.items():
   if item.tag=='select':self.assertEqual('false',item.attrib['useCache']);self.assertEqual('true',item.attrib['flushCache'])
 def test_personal_queue_is_actor_scoped_bounded_keyset_and_creator_bound_history(self):
  self.assertIn('UNIQUE KEY uq_revision_assignment_identity (tenant_id, assignment_id)',self.sql)
  self.assertIn('KEY ix_revision_assignment_queue (tenant_id, assignee_id, assignment_id)',self.sql)
  source=self.statements['myAssignments'][2]
  self.assertIn('a.assignee_id=#{userId}',source)
  self.assertIn('a.assignment_id > #{after}',source)
  self.assertIn('ORDER BY a.assignment_id LIMIT #{fetchLimit}',source)
  for clause in ('r.tenant_id=a.tenant_id','r.project_id=a.project_id','r.document_revision=a.document_revision','r.task_revision=a.task_revision','r.assignee_id=a.assignee_id'):
   self.assertIn(clause,source)
  operation=self.statements['historyOperation'][2]
  for clause in ('o.tenant_id=r.tenant_id','o.operation_id=r.operation_id','o.subject_id=r.actor_id','o.created_by=r.actor_id','r.created_by=r.actor_id'):
   self.assertIn(clause,operation)
  audit=self.statements['historyAudit'][2]
  self.assertIn('a.subject_id=r.actor_id',audit);self.assertIn('a.project_id=r.project_id',audit)
  self.assertIn('LIMIT 2 FOR UPDATE',audit)
 def test_historical_witness_remains_private_and_actual_invalidations_are_read_back(self):
  service=(JAVA/'DocumentReviewReturnService.java').read_text()
  self.assertIn('internalRequest(row)',service)
  self.assertIn('audit.equals(audit(',service)
  self.assertIn('authority_witness_sha256',service)
  self.assertIn('mapper.invalidations(values)',service)
  self.assertIn("JSON_EXTRACT(r.canonical_request,'$.request.basis')",self.statements['decision'][2])
  actual=self.statements['invalidations'][2]
  for clause in ('i.tenant_id=r.tenant_id','i.project_id=r.project_id','i.trigger_version=r.document_revision','i.created_by=r.actor_id','i.created_at=r.created_at',"i.reason_code='DOCUMENT_RETURNED_FOR_REVISION'","d.decision='CONFIRM'",'d.document_revision=r.document_revision','i.classification=r.classification','LIMIT 101 FOR UPDATE'):
   self.assertIn(clause,actual)
  for clause in ('a.created_by=r.actor_id','a.trace_id=a.request_id','a.classification=r.classification',"a.schema_version='1.0.0'",'a.reason_code IS NULL'):
   self.assertIn(clause,self.statements['historyAudit'][2])
 def test_metadata_queue_never_claims_body_validation_or_downloads_content(self):
  service=(JAVA/'DocumentRevisionAssignmentQueueService.java').read_text()
  self.assertIn('documents.authorizeMetadata(',service);self.assertIn('documents.requireMetadataCurrent(',service)
  self.assertNotIn('readFixed(',service);self.assertNotIn('FixedRead',service)
  self.assertIn('returns.historyReceipt(',service)
  metadata=(JAVA/'DocumentReadService.java').read_text()
  block=metadata[metadata.index('    MetadataRead authorizeMetadata('):metadata.index('    FixedRead readFixed(')]
  self.assertIn('prepare(admission, context, documentId, revision, true)',block)
  self.assertIn('original.header().documentRevision(), true)',block)
  self.assertIn('return prepare(admission, context, documentId, revision, false);',metadata)
  self.assertIn('original.digest().equals(current.digest())',block)
  self.assertNotIn('load(',block)
 def test_registered_after_066_exact_totals_and_immutable_actor_guards(self):
  assert_registered(self,'db/migration/067-document-review-return.sql','db/migration/066-gru-records.sql',self.tables)
  coverage=json.loads((MIGRATIONS/'coverage.json').read_text())
  self.assertEqual({RETURN,ASSIGN},set(coverage['required_tables']['document_return_for_revision']))
  self.assertEqual(526,sum(map(len,coverage['required_tables'].values())))
  self.assertEqual(565,sum(map(len,coverage['required_tables'].values()))+len(coverage['platform_tables'])+len(coverage['framework_tables']))
  immutable=re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);',(JAVA/'ProtectedTenantInterceptor.java').read_text(),re.S).group(1)
  for table in (RETURN,ASSIGN):self.assertIn('"'+table+'"',immutable)
  self.assertIn('statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentReviewReturnMapper.")',(JAVA/'TenantScope.java').read_text())
 def test_actual_assignment_index_column_order_and_unique_parent_binding(self):
  assignment=self.tables[ASSIGN]
  indexes={key.this.name:tuple(column.name for column in key.expressions) for key in assignment.find_all(exp.IndexColumnConstraint)}
  self.assertEqual({'ix_revision_assignment_queue':('tenant_id','assignee_id','assignment_id'),
   'ix_revision_return_assignee':('tenant_id','project_id','assignee_id','status','created_at')},indexes)
  unique={tuple(column.name for column in key.this.expressions) for key in assignment.find_all(exp.UniqueColumnConstraint)}
  self.assertEqual({('tenant_id','project_id','return_id'),('tenant_id','assignment_id')},unique)
  parent_keys={tuple(column.name for column in key.this.expressions) for key in self.tables[RETURN].find_all(exp.UniqueColumnConstraint)}
  binding=('tenant_id','project_id','return_id','document_id','document_revision','task_id','task_revision','assignee_id')
  self.assertIn(binding,parent_keys)
  self.assertIn((RETURN,binding,binding),foreign_keys(assignment))
 def test_001_through_066_frozen_against_part14_source_package(self):
  # Independently compared every file to the part14 source ZIP before fixing this digest.
  paths=sorted(p for p in MIGRATIONS.iterdir() if p.suffix in ('.sql','.xml') and p.name[:3].isdigit() and int(p.name[:3])<=66)
  self.assertEqual(65,len(paths))
  digest=hashlib.sha256(b''.join(p.name.encode()+b'\0'+p.read_bytes()+b'\0' for p in paths)).hexdigest()
  self.assertEqual('4b4350b647b4608ae3d8c827e1e8ce45386b17ffdc8b3a064d430fbca21a6aed',digest)
 def test_001_through_062_unchanged(self):
  paths=sorted(p for p in (RESOURCES/'db/migration').iterdir() if p.suffix in ('.sql','.xml') and p.name[:3].isdigit() and int(p.name[:3])<=62)
  self.assertEqual(61,len(paths));digest=hashlib.sha256(b''.join(p.name.encode()+b'\0'+p.read_bytes()+b'\0' for p in paths)).hexdigest()
  self.assertEqual('2d397c29d7cd21da85187ffd3473d2f4eef452b404a04f50a3716daa4eed60bf',digest)
if __name__=='__main__':unittest.main()
