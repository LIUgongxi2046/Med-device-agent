"""Execute the actual mapped SELECTs against relational fixtures, and check real MySQL DDL columns.
SQLite covers join/cardinality behavior only; this is not a MySQL deployment claim.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import os,re,json,sqlite3,unittest
import sqlglot
from sqlglot.optimizer.qualify import qualify
from test_evaluation_policy_columns import catalogue, ROOT
MAPPER=Path(os.environ.get('SOURCE_MAPPER',str(ROOT/'business/medical-persistence/src/main/resources/medical-mappers/SourceGovernanceMapper.xml')))
SERVICE=Path(os.environ.get('SOURCE_SERVICE',str(ROOT/'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceGovernanceService.java')))
def query(name,enterprise=False):
 node=ET.parse(MAPPER).getroot().find("select[@id='"+name+"']")
 def render(n):
  text=n.text or ''
  for child in n:
   if child.tag=='choose':text+=render(child.find('otherwise' if enterprise else 'when'))
   else:text+=render(child)
   text+=child.tail or ''
  return text
 return render(node)
class SourceSubmissionParseBinding(unittest.TestCase):
 def setUp(self):
  self.db=sqlite3.connect(':memory:');self.db.row_factory=sqlite3.Row;self.db.create_function('JSON_UNQUOTE',1,lambda s:s)
  self.db.executescript('''CREATE TABLE knowledge_submission(tenant_id,project_id,submission_id,artifact_id,artifact_owner_project_id,target_source_id,target_source_version,sharing_decision,original_evidence,status,created_by,created_at,classification,revision,updated_at);
  CREATE TABLE source_version(tenant_id,source_version,rights_revision);
  CREATE TABLE parse_version(tenant_id,source_version,parse_version,processor_manifest);
  CREATE TABLE outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload);
  INSERT INTO knowledge_submission VALUES('tenant','project','submission','artifact','owner','source','version','decision','{}','PENDING','actor','now','INTERNAL',1,'now');
  INSERT INTO source_version VALUES('tenant','version','rights');''')
 def add_parse(self,id='original',tenant='tenant',version='version',**overrides):
  manifest={'origin':'ARTIFACT_SUBMISSION','artifact_id':'artifact','submission_id':'submission'};manifest.update(overrides)
  self.db.execute('INSERT INTO parse_version VALUES(?,?,?,?)',(tenant,version,id,json.dumps(manifest)))
 def add_event(self,parse='original',**overrides):
  payload={'schema_version':'1.0.0','submission_id':'submission','artifact_id':'artifact','source_id':'source','source_version':'version','sharing_decision':'decision','actor_id':'actor','parse_version':parse};payload.update(overrides)
  self.db.execute('INSERT INTO outbox_event VALUES(?,?,?,?,?,?)',('tenant','event','version',1,'artifact.submitted_as_source',json.dumps(payload)))
 def rows(self,name):
  sql=re.sub(r'#\{(\w+)\}',r':\1',query(name)).replace('FOR UPDATE','')
  return self.db.execute(sql,{'submissionId':'submission','artifactId':'artifact','targetProjectId':'project'}).fetchall()
 def test_both_mapped_queries_reference_real_ddl_columns(self):
  schema={t:{c:'UNKNOWN' for c in cols} for t,cols in catalogue().items()}
  for name in ('artifactSubmission','pendingArtifactSubmission'):
   for enterprise in (False,True):qualify(sqlglot.parse_one(re.sub(r'#\{[^}]+\}',"'value'",query(name,enterprise)),read='mysql'),dialect='mysql',schema=schema,validate_qualify_columns=True)
 def test_zero_matches_preserves_pending_row_and_null_parse(self):
  self.add_parse()
  for name in ('artifactSubmission','pendingArtifactSubmission'):
   rows=self.rows(name);self.assertEqual(1,len(rows));self.assertIsNone(rows[0]['parse_version'])
 def test_multiple_original_matches_remain_multiple_for_single_guard(self):
  self.add_parse('one');self.add_parse('two');self.add_event('one');self.add_event('two')
  for name in ('artifactSubmission','pendingArtifactSubmission'):self.assertEqual(2,len(self.rows(name)))
 def test_reparse_never_replaces_original_even_with_earlier_uuid(self):
  self.add_event('ffffffff-original');self.add_parse('ffffffff-original');self.add_parse('00000000-reparse',origin='REPROCESS');self.add_parse('other-submission',submission_id='different');self.add_parse('other-artifact',artifact_id='different');self.add_parse('other-tenant',tenant='different');self.add_parse('other-version',version='different')
  for name in ('artifactSubmission','pendingArtifactSubmission'):
   rows=self.rows(name);self.assertEqual(['ffffffff-original'],[r['parse_version'] for r in rows])
 def test_worker_overwriting_processor_manifest_preserves_original_event_binding(self):
  self.add_parse();self.add_event()
  self.db.execute('UPDATE parse_version SET processor_manifest=?',(json.dumps({'processor_id':'actual-worker','version':'new-bundle'}),))
  self.add_parse('new-parse',origin='REPROCESS')
  for name in ('artifactSubmission','pendingArtifactSubmission'):self.assertEqual(['original'],[r['parse_version'] for r in self.rows(name)])
 def test_deleted_event_or_missing_parse_does_not_fallback(self):
  self.add_parse('unrelated');self.add_event('missing')
  for name in ('artifactSubmission','pendingArtifactSubmission'):self.assertIsNone(self.rows(name)[0]['parse_version'])
  self.db.execute('DELETE FROM outbox_event')
  for name in ('artifactSubmission','pendingArtifactSubmission'):self.assertIsNone(self.rows(name)[0]['parse_version'])
 def test_wrong_original_event_identity_cannot_supply_parse(self):
  self.add_parse()
  for key in ('submission_id','artifact_id','source_id','source_version','sharing_decision','actor_id','schema_version'):
   self.db.execute('DELETE FROM outbox_event');self.add_event(**{key:'wrong'})
   for name in ('artifactSubmission','pendingArtifactSubmission'):self.assertIsNone(self.rows(name)[0]['parse_version'])
 def test_service_rejects_null_invalid_or_ambiguous_before_writing(self):
  s=SERVICE.read_text();start=s.index('private JsonNode reuseArtifactSubmission(');end=s.index('private JsonNode replayLifecycle(',start);part=s[start:end]
  self.assertLess(part.index('stored.parseVersion() == null'),part.index('String operationId'))
  self.assertLess(part.index('WireValues.uuid(stored.parseVersion())'),part.index('String operationId'))
  self.assertIn('reuseArtifactSubmission(admission, single(pending)',s)
  self.assertIn('single(mapper.artifactSubmission(values))',s)
  self.assertIn('!parseVersion.equals(stored.parseVersion())',s)
 def test_no_arbitrary_parse_order_or_limit(self):
  for name in ('artifactSubmission','pendingArtifactSubmission'):
   s=query(name);self.assertNotIn('ORDER BY',s);self.assertNotIn('LIMIT',s);self.assertIn('LEFT JOIN parse_version',s)
if __name__=='__main__':unittest.main(verbosity=2)
