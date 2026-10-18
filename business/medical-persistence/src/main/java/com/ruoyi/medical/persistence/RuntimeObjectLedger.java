package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Immutable preparation and at-most-one initial object send. Must run inside
 * FormalWorkerAccess, after the fixed published payload producer validates bytes.
 * The planned artifact UUID is never returned as a registered artifact receipt.
 */
public final class RuntimeObjectLedger {
    private final JdbcTemplate jdbc;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    private final int maximumBytes;
    private final RuntimeReplayOriginalAuthority originals;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public enum Kind { REPLAY_MANIFEST, STEP_ENVELOPE, STEP_RESULT }
    public RuntimeObjectLedger(DataSource data,PermissionSchema json,ResourceReadAuthority resources,int maximumBytes){
        jdbc=new JdbcTemplate(data);this.json=json;this.resources=resources;this.originals=new RuntimeReplayOriginalAuthority(data,json);
        if(maximumBytes<1)throw new IllegalArgumentException("OBJECT_CAPACITY_REQUIRED");this.maximumBytes=maximumBytes;
    }
    RuntimeObjectLedger(JdbcTemplate jdbc,PermissionSchema json,ResourceReadAuthority resources,int maximumBytes,RuntimeReplayOriginalAuthority originals){this.originals=Objects.requireNonNull(originals);this.jdbc=jdbc;this.json=json;this.resources=resources;if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;}
    Intent prepareReplay(EnterpriseAccess.Admission a,byte[] canonicalPayload,RuntimeReplaySchema.VerifiedInputs verified,String classification)throws Exception {
        Objects.requireNonNull(verified);originals.require(a,canonicalPayload);
        var identity=a.formalProof().identity();var content=verified.content();
        if(!WireValues.sha256(canonicalPayload).equals(verified.rawSha256())
            || !identity.run().equals(verified.runId())
            || !identity.tenant().equals(content.path("tenant_id").asText())
            || !identity.project().equals(content.path("project_id").asText())
            || !identity.actor().equals(content.path("initiator_id").asText())
            || !identity.principal().equals(content.path("principal_id").asText())
            || !canonical(identity.scope()).equals(json.canonical(content.required("scope_manifest")))
            || !canonical(identity.capabilities()).equals(json.canonical(content.required("principal_manifest"))))fail(ErrorCode.CONTENT_MISMATCH);
        return prepare(a,Kind.REPLAY_MANIFEST,canonicalPayload,RuntimeReplaySchema.REF,verified.schemaSha256(),classification);
    }
    private Intent prepare(EnterpriseAccess.Admission a,Kind kind,byte[] canonicalPayload,String schemaRef,String schemaDigest,String classification)throws Exception {
        var identity=authorize(a);Objects.requireNonNull(kind);WireValues.hash(schemaDigest);
        String actualClassification=one(jdbc.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? FOR UPDATE",(r,n)->r.getString(1),identity.tenant(),identity.project(),identity.run(),identity.actor()));
        if(!actualClassification.equals(classification))fail(ErrorCode.CONTENT_MISMATCH);
        if(canonicalPayload.length<1 || canonicalPayload.length>maximumBytes)fail(ErrorCode.CONTEXT_LIMIT);
        String payload=new String(canonicalPayload,java.nio.charset.StandardCharsets.UTF_8);
        var value=reader.readTree(payload);
        if(!java.util.Arrays.equals(canonicalPayload,json.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)))fail(ErrorCode.CONTENT_MISMATCH);
        String fixedRef=switch(kind){case REPLAY_MANIFEST->"urn:medical:replay-manifest:1.0.0";case STEP_ENVELOPE->"urn:medical:runtime-step:1.0.0#/$defs/StepEnvelope";case STEP_RESULT->"urn:medical:runtime-step:1.0.0#/$defs/StepResult";};
        if(!fixedRef.equals(schemaRef))fail(ErrorCode.SCHEMA_INVALID);
        if(!identity.run().equals(value.path("run_id").asText()) || kind!=Kind.STEP_RESULT && (!identity.tenant().equals(value.path("tenant_id").asText()) || !identity.project().equals(value.path("project_id").asText())))fail(ErrorCode.CONTENT_MISMATCH);
        if(kind!=Kind.REPLAY_MANIFEST && (!identity.step().equals(value.path("step_id").asText()) || !identity.generation().equals(value.path("lease_generation").asText())))fail(ErrorCode.CONTENT_MISMATCH);
        String key=WireValues.sha256((identity.run()+"/"+(kind==Kind.REPLAY_MANIFEST?"RUN":identity.step()+"/"+identity.generation())+"/"+kind).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var existing=read(a,key);
        String digest=WireValues.sha256(canonicalPayload);
        if(!existing.isEmpty()){
            var intent=one(existing);if(intent.kind!=kind || !intent.schemaRef.equals(schemaRef)||!intent.schemaDigest.equals(schemaDigest)||!intent.intent.sha256().equals(digest)||!intent.canonicalPayload.equals(payload))fail(ErrorCode.CONTENT_MISMATCH);return intent;
        }
        String write=UUID.randomUUID().toString(),artifact=UUID.randomUUID().toString();
        var intent=new S3ObjectWriter.WriteIntent(write,"runtime/"+identity.tenant()+"/"+identity.run()+"/"+write+"/"+artifact,digest,Integer.toString(canonicalPayload.length),"application/json");
        int count=jdbc.update("""
            INSERT INTO runtime_object_intent(tenant_id,project_id,run_id,step_id,write_key,write_id,planned_artifact_id,write_kind,
              object_key,object_sha256,size_bytes,canonical_payload,schema_ref,payload_schema_sha256,schema_version,schema_manifest_sha256,
              created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,identity.tenant(),identity.project(),identity.run(),identity.step(),key,write,artifact,kind.name(),intent.objectKey(),digest,canonicalPayload.length,payload,schemaRef,schemaDigest,schemaDigest,identity.actor(),classification);
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);
        count=jdbc.update("""
            INSERT INTO runtime_object_state(tenant_id,project_id,run_id,write_id,stage,revision,updated_at,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            SELECT tenant_id,project_id,run_id,write_id,'PREPARED',1,UTC_TIMESTAMP(6),schema_version,schema_manifest_sha256,created_by,created_at,classification
            FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=?
            """,identity.tenant(),identity.project(),identity.run(),write,identity.actor());
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);
        var prepared=new Intent(key,artifact,kind,intent,payload,schemaRef,schemaDigest,identity.tenant(),identity.actor(),identity.run());String witness=witness(a,prepared);
        count=jdbc.update("""
            INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            SELECT tenant_id,project_id,?,created_by,'runtime.object.prepared',?,'SUCCESS',write_id,run_id,schema_version,schema_manifest_sha256,created_by,created_at,classification
            FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=?
            """,UUID.randomUUID().toString(),witness,identity.tenant(),identity.project(),identity.run(),write,identity.actor());
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);
        count=jdbc.update("""
            INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at)
            SELECT tenant_id,write_id,run_id,1,'runtime.object.prepared',?,created_at
            FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=?
            """,witness,identity.tenant(),identity.project(),identity.run(),write,identity.actor());
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);
        var stored=one(read(a,key));if(!stored.intent.equals(intent)||!stored.canonicalPayload.equals(payload))fail(ErrorCode.CONTENT_MISMATCH);return stored;
    }
    Attempt attempt(EnterpriseAccess.Admission a,Intent expected)throws Exception {return attempt(a,expected,true);}
    Attempt requireAttempt(EnterpriseAccess.Admission a,Intent expected)throws Exception {return attempt(a,expected,false);}
    private Attempt attempt(EnterpriseAccess.Admission a,Intent expected,boolean create)throws Exception {
        var identity=authorize(a,create);var actual=one(read(a,expected.key));
        if(!actual.equalsIntent(expected))fail(ErrorCode.CONTENT_MISMATCH);
        if(actual.kind==Kind.REPLAY_MANIFEST)originals.require(a,actual.bytes());
        var values=jdbc.query("SELECT a.write_id FROM runtime_object_attempt a JOIN runtime_object_intent i ON i.tenant_id=a.tenant_id AND i.project_id=a.project_id AND i.run_id=a.run_id AND i.write_id=a.write_id AND i.created_by=a.created_by AND i.schema_version=a.schema_version AND i.schema_manifest_sha256=a.schema_manifest_sha256 AND i.classification=a.classification AND a.created_at>=i.created_at WHERE a.tenant_id=? AND a.project_id=? AND a.run_id=? AND a.write_id=? AND a.created_by=? FOR UPDATE",(r,n)->r.getString(1),identity.tenant(),identity.project(),identity.run(),actual.intent.writeId(),identity.actor());
        if(values.size()>1)fail(ErrorCode.CONTENT_MISMATCH);
        String stage=one(jdbc.query("""
            SELECT stage FROM runtime_object_state WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=?
              AND schema_version='1.0.0' AND schema_manifest_sha256=?
              AND ((stage='PREPARED' AND revision=1) OR (stage='ATTEMPTED' AND revision=2)) FOR UPDATE
            """,(r,n)->r.getString(1),identity.tenant(),identity.project(),identity.run(),actual.intent.writeId(),identity.actor(),actual.schemaDigest));
        if(!values.isEmpty()){if(!"ATTEMPTED".equals(stage))fail(ErrorCode.CONTENT_MISMATCH);return new Attempt(actual,true);}
        if(!create || !"PREPARED".equals(stage))fail(ErrorCode.CONTENT_MISMATCH);
        int count=jdbc.update("""
            INSERT INTO runtime_object_attempt(tenant_id,project_id,run_id,write_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            SELECT tenant_id,project_id,run_id,write_id,schema_version,schema_manifest_sha256,created_by,UTC_TIMESTAMP(6),classification
            FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=?
            """,identity.tenant(),identity.project(),identity.run(),actual.intent.writeId(),identity.actor());
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);
        count=jdbc.update("""
            UPDATE runtime_object_state SET stage='ATTEMPTED',revision=2,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? AND stage='PREPARED' AND revision=1
            """,identity.tenant(),identity.project(),identity.run(),actual.intent.writeId(),identity.actor());
        if(count!=1)fail(ErrorCode.REVISION_CONFLICT);return new Attempt(actual,false);
    }
    private FormalWorkerAccess.Identity authorize(EnterpriseAccess.Admission a)throws Exception {return authorize(a,true);}
    private FormalWorkerAccess.Identity authorize(EnterpriseAccess.Admission a,boolean registering)throws Exception {
        var p=a.formalProof();var i=p.identity();
        if(!"TASK".equals(i.executionKind())||i.project()==null)fail(ErrorCode.ACCESS_DENIED);
        String task=one(jdbc.query("SELECT task_id FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND status IN ('QUEUED','RUNNING') AND ended_at IS NULL AND dispatch_closed_at IS NULL FOR UPDATE",(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),i.actor()));
        resources.require(a,new PermissionProtocol.Scope(i.tenant(),i.project(),"TASK",task),registering?"artifact.register":"task.read","TASK_EXECUTION");return i;
    }
    private List<Intent> read(EnterpriseAccess.Admission a,String key)throws java.io.IOException{
        var i=a.formalProof().identity();var rows=jdbc.query("""
            SELECT write_id,planned_artifact_id,write_kind,object_key,object_sha256,size_bytes,canonical_payload,schema_ref,payload_schema_sha256
            FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_key=? AND created_by=? FOR UPDATE
            """,(r,n)->new Intent(key,r.getString("planned_artifact_id"),Kind.valueOf(r.getString("write_kind")),new S3ObjectWriter.WriteIntent(r.getString("write_id"),r.getString("object_key"),r.getString("object_sha256"),r.getString("size_bytes"),"application/json"),canonical(r.getString("canonical_payload")),r.getString("schema_ref"),r.getString("payload_schema_sha256"),i.tenant(),i.actor(),i.run()),i.tenant(),i.project(),i.run(),key,i.actor());
        for(var row:rows)verifyWitness(a,row);return rows;
    }
    private String witness(EnterpriseAccess.Admission a,Intent intent)throws java.io.IOException{var i=a.formalProof().identity();var node=json.object().put("schema_version","1.0.0").put("tenant_id",i.tenant()).put("project_id",i.project()).put("run_id",i.run()).put("actor_id",i.actor()).put("write_key",intent.key).put("artifact_id",intent.plannedArtifact).put("kind",intent.kind.name()).put("schema_ref",intent.schemaRef).put("payload_schema_sha256",intent.schemaDigest);node.set("write_intent",json.tree(intent.intent));return json.canonical(node);}
    private void verifyWitness(EnterpriseAccess.Admission a,Intent intent)throws java.io.IOException{
        var i=a.formalProof().identity();String expected=witness(a,intent);
        var audit=one(jdbc.query("""
            SELECT a.resource_ref FROM runtime_object_intent i JOIN audit_event a ON a.tenant_id=i.tenant_id AND a.project_id=i.project_id
              AND a.request_id=i.write_id AND a.trace_id=i.run_id AND a.subject_id=i.created_by AND a.created_by=i.created_by
              AND a.created_at=i.created_at AND a.classification=i.classification AND a.schema_version=i.schema_version AND a.schema_manifest_sha256=i.schema_manifest_sha256
            WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.write_id=? AND i.created_by=? AND a.action='runtime.object.prepared' AND a.result='SUCCESS' AND a.reason_code IS NULL LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),intent.intent.writeId(),i.actor()));
        var event=one(jdbc.query("""
            SELECT e.payload FROM runtime_object_intent i JOIN outbox_event e ON e.tenant_id=i.tenant_id AND e.event_id=i.write_id
              AND e.aggregate_id=i.run_id AND e.aggregate_revision=1 AND e.occurred_at=i.created_at
            WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.write_id=? AND i.created_by=? AND e.event_type='runtime.object.prepared' LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),intent.intent.writeId(),i.actor()));
        if(!expected.equals(canonical(audit))||!expected.equals(canonical(event)))fail(ErrorCode.CONTENT_MISMATCH);
    }
    private String canonical(String value){try{return json.canonical(reader.readTree(value));}catch(Exception e){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}}
    public static final class Intent {
        private final String key,plannedArtifact,canonicalPayload,schemaRef,schemaDigest,tenant,actor,run;private final Kind kind;private final S3ObjectWriter.WriteIntent intent;
        private Intent(String key,String plannedArtifact,Kind kind,S3ObjectWriter.WriteIntent intent,String payload,String schemaRef,String schemaDigest,String tenant,String actor,String run){this.tenant=tenant;this.actor=actor;this.run=run;this.key=key;this.plannedArtifact=plannedArtifact;this.kind=kind;this.intent=intent;canonicalPayload=payload;this.schemaRef=schemaRef;this.schemaDigest=schemaDigest;}
        public S3ObjectWriter.WriteIntent writeIntent(){return intent;}public byte[] bytes(){return canonicalPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        String plannedArtifact(){return plannedArtifact;}String schemaRef(){return schemaRef;}String schemaDigest(){return schemaDigest;}Kind kind(){return kind;}String key(){return key;}
        private boolean equalsIntent(Intent other){return key.equals(other.key)&&plannedArtifact.equals(other.plannedArtifact)&&kind==other.kind&&intent.equals(other.intent)&&canonicalPayload.equals(other.canonicalPayload)&&schemaRef.equals(other.schemaRef)&&schemaDigest.equals(other.schemaDigest);}
    }
    public static final class Attempt implements RuntimeObjectAttempt {
        private final Intent intent;private final boolean prior;private final java.util.concurrent.atomic.AtomicBoolean consumed=new java.util.concurrent.atomic.AtomicBoolean();
        private Attempt(Intent intent,boolean prior){this.intent=intent;this.prior=prior;}
        public String tenantId(){return intent.tenant;}public String actorId(){return intent.actor;}public String expectedObjectKey(){return "runtime/"+intent.tenant+"/"+intent.run+"/"+intent.writeIntent().writeId()+"/"+intent.plannedArtifact;}
        public Intent intent(){return intent;}public S3ObjectWriter.WriteIntent writeIntent(){return intent.writeIntent();}public byte[] bytes(){return intent.bytes();}public boolean priorAttempt(){return prior;}public RuntimeObjectAttempt.TransferMode claimTransferMode(){return prior||consumed.getAndSet(true)?RuntimeObjectAttempt.TransferMode.RECOVER_ONLY:RuntimeObjectAttempt.TransferMode.INITIAL_WRITE;}
    }
    private static <T>T one(List<T> values){if(values.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);return values.get(0);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
