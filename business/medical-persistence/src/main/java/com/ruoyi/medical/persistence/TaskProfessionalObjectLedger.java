package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit v2 branch of the075 journal. Only private098/host producer proofs enter;
 * old v1 artifact.register paths cannot produce or recover these objects. */
public final class TaskProfessionalObjectLedger {
 private final JdbcTemplate db;private final PermissionSchema json;private final TaskProfessionalSchema schema;
 public TaskProfessionalObjectLedger(DataSource data,PermissionSchema json,TaskProfessionalSchema schema){this(new JdbcTemplate(data),json,schema);}
 TaskProfessionalObjectLedger(JdbcTemplate db,PermissionSchema json,TaskProfessionalSchema schema){this.db=db;db.setQueryTimeout(5);this.json=json;this.schema=schema;}
 public Intent prepare(TaskNodeProducerAuthority.LeasedProduction authority,TaskProfessionalRuntimeService.Envelope envelope)throws Exception {
  envelope.requireProductionReady();authority.requireProduction(envelope);return prepare(envelope.admission(),authority,envelope.input(),"STEP_ENVELOPE",envelope.sessionId(),envelope.bytes());
 }
 public Intent prepare(TaskNodeProducerAuthority.LeasedProduction authority,TaskResearchExecutionService.Produced output)throws Exception {
  authority.requireProduction(output);return prepare(output.session().admission(),authority,output.session().input(),output.kind(),output.sessionId(),output.bytes(),null,output.schemaRef(),output.schemaSha256());
 }
 public Intent prepare(TaskNodeProducerAuthority.LeasedProduction authority,TaskInvocationContext.Prepared exact)throws Exception {
  authority.requireProduction(exact);return prepare(exact.admission(),authority,exact.input(),"TASK_EXACT_INPUT",exact.sessionId(),exact.bytes(),exact);
 }
 private Intent prepare(EnterpriseAccess.Admission a,TaskNodeProducerAuthority.LeasedProduction authority,TaskNodeInputAuthority.Verified input,String kind,String session,byte[] bytes)throws Exception {return prepare(a,authority,input,kind,session,bytes,null);}
 private Intent prepare(EnterpriseAccess.Admission a,TaskNodeProducerAuthority.LeasedProduction authority,TaskNodeInputAuthority.Verified input,String kind,String session,byte[] bytes,TaskInvocationContext.Prepared exact)throws Exception {return prepare(a,authority,input,kind,session,bytes,exact,schema.ref(kind),schema.manifest(kind));}
 private Intent prepare(EnterpriseAccess.Admission a,TaskNodeProducerAuthority.LeasedProduction authority,TaskNodeInputAuthority.Verified input,String kind,String session,byte[] bytes,TaskInvocationContext.Prepared exact,String ref,String document)throws Exception {
  TaskProfessionalRuntimeService.current(a);authority.requireUnchanged();input.requireUnchanged();var i=a.formalProof().identity();
  var payload=schema.read(kind,bytes,ref,document);String canonical=json.canonical(payload),digest=WireValues.sha256(bytes);if(!Arrays.equals(bytes,canonical.getBytes(StandardCharsets.UTF_8)))bad();
  String key=exact==null?key(i.run(),i.step(),i.generation(),kind):exactKey(exact);
  String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND task_id=? AND created_by=? AND execution_kind='TASK' FOR UPDATE",(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),input.taskId(),i.actor()));
  var rows=rows(a,key);if(!rows.isEmpty()){var original=read(a,one(rows),exact);if(!original.canonical.equals(canonical)||!original.kind.equals(kind)||!original.ref.equals(ref)||!original.document.equals(document)||!original.session.equals(session))bad();return original;}
  String write=UUID.randomUUID().toString(),artifact=UUID.randomUUID().toString();var intent=new S3ObjectWriter.WriteIntent(write,"runtime/"+i.tenant()+"/"+i.run()+"/"+write+"/"+artifact,digest,Integer.toString(bytes.length),"application/json");
  count(db.update("""
    INSERT INTO runtime_object_intent(tenant_id,project_id,run_id,step_id,write_key,write_id,planned_artifact_id,write_kind,object_key,object_sha256,size_bytes,canonical_payload,schema_ref,payload_schema_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,'2.0.0',?,?,UTC_TIMESTAMP(6),?)
    """,i.tenant(),i.project(),i.run(),i.step(),key,write,artifact,kind,intent.objectKey(),digest,bytes.length,canonical,ref,document,document,i.actor(),classification));
  count(db.update("""
    INSERT INTO runtime_object_state(tenant_id,project_id,run_id,write_id,stage,revision,updated_at,schema_version,schema_manifest_sha256,created_by,created_at,classification)
    SELECT tenant_id,project_id,run_id,write_id,'PREPARED',1,created_at,'1.0.0',schema_manifest_sha256,created_by,created_at,classification FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND schema_version='2.0.0'
    """,i.tenant(),i.project(),i.run(),write));
  var prepared=new Intent(i.tenant(),i.project(),i.run(),i.step(),i.actor(),key,artifact,kind,session,intent,canonical,ref,document,classification);String witness=witness(prepared);
  count(db.update("""
    INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
    SELECT tenant_id,project_id,?,created_by,'runtime.professional.prepared',?,'SUCCESS',write_id,run_id,'1.0.0',schema_manifest_sha256,created_by,created_at,classification FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND schema_version='2.0.0'
    """,UUID.randomUUID().toString(),witness,i.tenant(),i.project(),i.run(),write));
  count(db.update("""
    INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at)
    SELECT tenant_id,write_id,run_id,1,'runtime.professional.prepared',?,created_at FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND schema_version='2.0.0'
    """,witness,i.tenant(),i.project(),i.run(),write));
  authority.requireUnchanged();input.requireUnchanged();return read(a,one(rows(a,key)),exact);
 }
 public Attempt attempt(TaskNodeProducerAuthority.LeasedProduction a,TaskProfessionalRuntimeService.Envelope e,Intent intent)throws Exception{e.requireProductionReady();a.requireProduction(e);samePayload(intent,"STEP_ENVELOPE",e.sessionId(),e.bytes());return attempt(e.admission(),intent,true);}
 public Attempt attempt(TaskNodeProducerAuthority.LeasedProduction a,TaskResearchExecutionService.Produced p,Intent intent)throws Exception{a.requireProduction(p);samePayload(intent,p.kind(),p.sessionId(),p.bytes());return attempt(p.session().admission(),intent,true);}
 public Attempt requireAttempt(TaskNodeProducerAuthority.LeasedProduction a,TaskProfessionalRuntimeService.Envelope e,Intent intent)throws Exception{e.requireProductionReady();a.requireProduction(e);samePayload(intent,"STEP_ENVELOPE",e.sessionId(),e.bytes());return attempt(e.admission(),intent,false);}
 public Attempt requireAttempt(TaskNodeProducerAuthority.LeasedProduction a,TaskResearchExecutionService.Produced p,Intent intent)throws Exception{a.requireProduction(p);samePayload(intent,p.kind(),p.sessionId(),p.bytes());return attempt(p.session().admission(),intent,false);}
 public Attempt attempt(TaskNodeProducerAuthority.LeasedProduction a,TaskInvocationContext.Prepared p,Intent intent)throws Exception{a.requireProduction(p);samePayload(intent,"TASK_EXACT_INPUT",p.sessionId(),p.bytes());return attempt(p.admission(),intent,true,p);}
 public Attempt requireAttempt(TaskNodeProducerAuthority.LeasedProduction a,TaskInvocationContext.Prepared p,Intent intent)throws Exception{a.requireProduction(p);samePayload(intent,"TASK_EXACT_INPUT",p.sessionId(),p.bytes());return attempt(p.admission(),intent,false,p);}
 private static void samePayload(Intent intent,String kind,String session,byte[] bytes){if(!intent.kind.equals(kind)||!intent.session.equals(session)||!Arrays.equals(intent.bytes(),bytes))bad();}
 private Attempt attempt(EnterpriseAccess.Admission a,Intent expected,boolean create)throws Exception{return attempt(a,expected,create,null);}
 private Attempt attempt(EnterpriseAccess.Admission a,Intent expected,boolean create,TaskInvocationContext.Prepared exact)throws Exception {
  TaskProfessionalRuntimeService.current(a);var i=a.formalProof().identity();var actual=read(a,one(rows(a,expected.key)),exact);if(!actual.equalsIntent(expected))bad();
  var attempts=db.queryForList("SELECT a.* FROM runtime_object_attempt a JOIN runtime_object_intent i ON i.tenant_id=a.tenant_id AND i.project_id=a.project_id AND i.run_id=a.run_id AND i.write_id=a.write_id WHERE a.tenant_id=? AND a.project_id=? AND a.run_id=? AND a.write_id=? FOR UPDATE",i.tenant(),i.project(),i.run(),actual.writeIntent().writeId());if(attempts.size()>1)bad();
  var state=one(db.queryForList("SELECT * FROM runtime_object_state WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? FOR UPDATE",i.tenant(),i.project(),i.run(),actual.writeIntent().writeId()));metadata(state,actual);String stage=str(state,"stage");long revision=number(state,"revision");
  if(!attempts.isEmpty()){metadata(attempts.get(0),actual);if(!"ATTEMPTED".equals(stage)||revision!=2)bad();return new Attempt(actual,true);}
  if(!create||!"PREPARED".equals(stage)||revision!=1)bad();
  count(db.update("""
    INSERT INTO runtime_object_attempt(tenant_id,project_id,run_id,write_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
    SELECT tenant_id,project_id,run_id,write_id,'1.0.0',schema_manifest_sha256,created_by,UTC_TIMESTAMP(6),classification FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND schema_version='2.0.0'
    """,i.tenant(),i.project(),i.run(),actual.writeIntent().writeId()));
  count(db.update("UPDATE runtime_object_state SET stage='ATTEMPTED',revision=2,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND stage='PREPARED' AND revision=1",i.tenant(),i.project(),i.run(),actual.writeIntent().writeId()));return new Attempt(actual,false);
 }
 private void metadata(Map<String,Object> row,Intent i){for(var pair:Map.of("schema_version","1.0.0","schema_manifest_sha256",i.document,"created_by",i.actor,"classification",i.classification).entrySet())if(!pair.getValue().equals(str(row,pair.getKey())))bad();}
 private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,String key){var i=a.formalProof().identity();return db.queryForList("SELECT * FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND write_key=? AND schema_version='2.0.0' AND created_by=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),key,i.actor());}
 private Intent read(EnterpriseAccess.Admission a,Map<String,Object> row,TaskInvocationContext.Prepared exact)throws Exception {
  var i=a.formalProof().identity();String kind=str(row,"write_kind");schema.requireDeclaration(kind,str(row,"schema_ref"),str(row,"payload_schema_sha256"));if(!str(row,"payload_schema_sha256").equals(str(row,"schema_manifest_sha256")))bad();
  var value=schema.read(kind,str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8),str(row,"schema_ref"),str(row,"payload_schema_sha256"));String canonical=json.canonical(value);byte[] bytes=canonical.getBytes(StandardCharsets.UTF_8);if(!WireValues.sha256(bytes).equals(str(row,"object_sha256"))||bytes.length!=number(row,"size_bytes"))bad();
  if(kind.equals("TASK_EXACT_INPUT")){if(exact==null)bad();exact.requireUnchanged();if(!Arrays.equals(bytes,exact.bytes()))bad();}else if(exact!=null)bad();
  String session=kind.equals("TASK_EXACT_INPUT")?exact.sessionId():kind.equals("EVIDENCE_BUNDLE")?sessionForEvidence(a,row):value.required("session_id").textValue();WireValues.uuid(session);
  var intent=new Intent(i.tenant(),i.project(),i.run(),i.step(),i.actor(),str(row,"write_key"),str(row,"planned_artifact_id"),kind,session,new S3ObjectWriter.WriteIntent(str(row,"write_id"),str(row,"object_key"),str(row,"object_sha256"),Long.toString(number(row,"size_bytes")),"application/json"),canonical,str(row,"schema_ref"),str(row,"payload_schema_sha256"),str(row,"classification"));
  if(!(exact==null?key(i.run(),i.step(),i.generation(),kind):exactKey(exact)).equals(intent.key)||!new Attempt(intent,true).expectedObjectKey().equals(intent.writeIntent().objectKey()))bad();
  String witness=witness(intent);var audits=db.queryForList("SELECT a.resource_ref FROM audit_event a JOIN runtime_object_intent i ON i.tenant_id=a.tenant_id AND i.project_id=a.project_id AND i.write_id=a.request_id AND i.run_id=a.trace_id AND i.created_by=a.created_by AND i.created_by=a.subject_id AND i.created_at=a.created_at AND i.classification=a.classification AND i.schema_manifest_sha256=a.schema_manifest_sha256 WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.write_id=? AND a.action='runtime.professional.prepared' AND a.result='SUCCESS' AND a.reason_code IS NULL AND a.schema_version='1.0.0' LIMIT 2",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());
  if(!witness.equals(json.canonical(schema.parse(str(one(audits),"resource_ref")))))bad();
  var events=db.queryForList("SELECT e.payload FROM outbox_event e JOIN runtime_object_intent i ON i.tenant_id=e.tenant_id AND i.write_id=e.event_id AND i.run_id=e.aggregate_id AND i.created_at=e.occurred_at WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.write_id=? AND e.aggregate_revision=1 AND e.event_type='runtime.professional.prepared' LIMIT 2",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());if(!witness.equals(json.canonical(schema.parse(str(one(events),"payload")))))bad();return intent;
 }
 private String sessionForEvidence(EnterpriseAccess.Admission a,Map<String,Object> row){var i=a.formalProof().identity();return one(db.query("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND created_by=? AND service_uri=? AND admission_state='CLOSED' FOR UPDATE",(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),i.step(),i.generation(),i.actor(),i.workerService()));}
 private String witness(Intent i)throws Exception {var node=json.object().put("schema_version","2.0.0").put("tenant_id",i.tenant).put("project_id",i.project).put("run_id",i.run).put("step_id",i.step).put("created_by",i.actor).put("write_key",i.key).put("artifact_id",i.artifact).put("session_id",i.session).put("kind",i.kind).put("schema_ref",i.ref).put("payload_schema_sha256",i.document);node.set("write_intent",json.tree(i.intent));return json.canonical(node);}
 static String key(String run,String step,String generation,String kind)throws java.security.NoSuchAlgorithmException{return WireValues.sha256(("professional-v2/"+run+"/"+step+"/"+generation+"/"+kind).getBytes(StandardCharsets.US_ASCII));}
 static String exactKey(TaskInvocationContext.Prepared exact)throws Exception{exact.requireUnchanged();return exactKey(exact.runId(),exact.stepId(),exact.leaseGeneration(),exact.modelRequestId(),exact.modelRequestSha256());}
 static String exactKey(String run,String step,String generation,String request,String receipt)throws Exception{WireValues.uuid(request);WireValues.hash(receipt);return WireValues.sha256(("professional-v2/"+run+"/"+step+"/"+generation+"/TASK_EXACT_INPUT/"+request+"/"+receipt).getBytes(StandardCharsets.US_ASCII));}
 public static final class Intent {
  private final String tenant,project,run,step,actor,key,artifact,kind,session,canonical,ref,document,classification;private final S3ObjectWriter.WriteIntent intent;
  private Intent(String tenant,String project,String run,String step,String actor,String key,String artifact,String kind,String session,S3ObjectWriter.WriteIntent intent,String canonical,String ref,String document,String classification){this.tenant=tenant;this.project=project;this.run=run;this.step=step;this.actor=actor;this.key=key;this.artifact=artifact;this.kind=kind;this.session=session;this.intent=intent;this.canonical=canonical;this.ref=ref;this.document=document;this.classification=classification;}
  public S3ObjectWriter.WriteIntent writeIntent(){return intent;}public byte[] bytes(){return canonical.getBytes(StandardCharsets.UTF_8);}public String artifactId(){return artifact;}public String kind(){return kind;}public String schemaRef(){return ref;}public String schemaSha256(){return document;}public String sessionId(){return session;}public String classification(){return classification;}public String key(){return key;}
  private boolean equalsIntent(Intent other){return tenant.equals(other.tenant)&&project.equals(other.project)&&run.equals(other.run)&&step.equals(other.step)&&actor.equals(other.actor)&&key.equals(other.key)&&artifact.equals(other.artifact)&&kind.equals(other.kind)&&session.equals(other.session)&&intent.equals(other.intent)&&canonical.equals(other.canonical)&&ref.equals(other.ref)&&document.equals(other.document)&&classification.equals(other.classification);}
 }
 public static final class Attempt implements RuntimeObjectAttempt {
  private final Intent intent;private final boolean prior;private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();private Attempt(Intent intent,boolean prior){this.intent=intent;this.prior=prior;}
  public Intent intent(){return intent;}public String tenantId(){return intent.tenant;}public String actorId(){return intent.actor;}public String expectedObjectKey(){return "runtime/"+intent.tenant+"/"+intent.run+"/"+intent.intent.writeId()+"/"+intent.artifact;}public S3ObjectWriter.WriteIntent writeIntent(){return intent.intent;}public byte[] bytes(){return intent.bytes();}public boolean priorAttempt(){return prior;}public TransferMode claimTransferMode(){return prior||claimed.getAndSet(true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
 }
 private static String str(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return value.toString();}private static long number(Map<String,Object> row,String key){return Long.parseLong(str(row,key));}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void count(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
