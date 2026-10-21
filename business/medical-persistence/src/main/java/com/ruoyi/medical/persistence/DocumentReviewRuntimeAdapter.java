package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Dedicated N07 saved-document host. Prepared launch is not process admission. A session is opened only
 * from independently authenticated supervision, and Produced requires original settled model
 * evidence plus independently reread successful closure. No N01 or N02 proof is relabeled. */
public final class DocumentReviewRuntimeAdapter {
    private final JdbcTemplate db;private final TaskSourceVerifierSchema schema;private final PermissionSchema json;
    private final DocumentReviewExecutionService executions;private final DocumentReviewDeployment deployment;private final ServiceCertificateIdentity identities;
    public DocumentReviewRuntimeAdapter(DataSource data,TaskSourceVerifierSchema schema,PermissionSchema json,
            DocumentReviewDeployment deployment,ServiceCertificateIdentity identities,DocumentReviewExecutionService executions){this.executions=executions;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.deployment=deployment;this.identities=identities;}

    Envelope prepare(DocumentReviewRuntimeAuthority.Verified authority,DocumentReviewPromptCompiler.Compiled prompt)throws Exception {
        require(authority,prompt);var j=authority.job();
        var rows=db.queryForList("SELECT session_id FROM document_review_runtime_prepare WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.step(),authority.leaseGeneration());
        if(rows.size()>1)bad();String session=rows.isEmpty()?sessionId(authority):str(rows.get(0),"session_id");
        var envelope=compile(authority,prompt,session);var launch=launch(authority,prompt,envelope,session);
        if(rows.isEmpty()) {
            String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=now();
            var receipt=json.object().put("session_id",session).put("envelope_sha256",schema.hash(envelope)).put("launch_sha256",schema.hash(launch));
            journal(j,audit,event,"document.review.runtime.prepared",session,receipt,now);
            changed(db.update("INSERT INTO document_review_runtime_prepare (tenant_id,project_id,run_id,step_id,action_id,lease_generation,session_id,envelope_json,envelope_sha256,launch_json,launch_sha256,deployment_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),j.run(),j.step(),authority.actionId(),authority.leaseGeneration(),session,schema.canonical(envelope),schema.hash(envelope),schema.canonical(launch),schema.hash(launch),deployment.sha256(),audit,event,json.manifestSha256(),j.actor(),now,j.classification()));
        }
        var result=new Envelope(authority,prompt,session,envelope,launch);result.requireUnchanged();return result;
    }
    /** Stable host allocation identity before physical context reads; never derived from writer session. */
    static String sessionId(DocumentReviewRuntimeAuthority.Verified authority)throws Exception {
        authority.requireUnchanged();var j=authority.job();return UUID.nameUUIDFromBytes(("CHECK_DOCUMENT_N07_SESSION_V1|"+j.tenant()+"|"+j.run()+"|"+j.step()+"|"+authority.leaseGeneration()).getBytes(StandardCharsets.US_ASCII)).toString();
    }
    private String hostKind(DocumentReviewRuntimeAuthority.Verified a)throws Exception{return one(db.query("SELECT kind FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2",(r,n)->r.getString(1),a.job().tenant(),a.job().run(),a.stepId(),a.actionId()));}
    private void require(DocumentReviewRuntimeAuthority.Verified a,DocumentReviewPromptCompiler.Compiled prompt)throws Exception {
        a.requireUnchanged();prompt.requireFor(a);var j=a.job();var admitted=schema.read(j.admissionJson());
        same(j.inputHash(),schema.hash(executions.input(j)));same(a.document().body(),executions.input(j).required("document_body"));
        if(!deployment.workerService().equals(a.workerService())||!"HOST_CONTROL".equals(hostKind(a)))deny();
        same(deployment.value().required("role_version"),admitted.required("role_version"));var runtime=admitted.required("release_manifest").required("runtime_manifest");
        same(text(deployment.value(),"runtime_manifest_sha256"),schema.hash(runtime));same(deployment.value().required("profile_artifact"),runtime.required("profile").required("artifact"));
        one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND kind='HOST_CONTROL' AND tool_version='CHECK_DOCUMENT_HOST_V1' AND arguments_sha256=? AND input_sha256=? AND state='BOUND' AND invocation_id IS NULL LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.actionId(),a.leaseGeneration(),j.inputHash(),j.inputHash()));
    }
    private JsonNode compile(DocumentReviewRuntimeAuthority.Verified a,DocumentReviewPromptCompiler.Compiled prompt,String session)throws Exception {
        require(a,prompt);var j=a.job();var admitted=schema.read(j.admissionJson());var h=a.document().header();
        var e=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","CHECK_DOCUMENT").put("node_kind","N07")
                .put("tenant_id",j.tenant()).put("project_id",j.project()).put("document_id",j.document()).put("document_revision",j.documentRevision()).put("run_id",j.run()).put("step_id",j.step())
                .put("action_id",a.actionId()).put("lease_generation",a.leaseGeneration().longValueExact()).put("session_id",session)
                .put("body_sha256",h.bodySha256()).put("baseline_sha256",a.document().baselineSha256()).put("input_sha256",j.inputHash())
                .put("deployment_revision",deployment.revision()).put("deployment_sha256",deployment.sha256()).put("runtime_manifest_sha256",text(deployment.value(),"runtime_manifest_sha256"))
                .put("worker_service_uri",a.workerService()).put("compiled_prompt",prompt.launchText()).put("prompt_sha256",prompt.payloadSha256());
        e.set("role_version",admitted.required("role_version"));e.set("model_version",admitted.required("model_version"));e.set("execution_policy_version",admitted.required("execution_policy_version"));e.set("prompt_provenance",prompt.canonicalPins());
        if(ContentReadAuthorization.canonicalBytes(e).length>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return e;
    }
    private JsonNode launch(DocumentReviewRuntimeAuthority.Verified a,DocumentReviewPromptCompiler.Compiled prompt,JsonNode envelope,String session)throws Exception {
        var j=a.job();var launch=json.object().put("state","PREPARE").put("purpose","SYSTEM_JOB").put("job_type","CHECK_DOCUMENT").put("session_id",session).put("node_kind","N07").put("role","SourceVerifier")
                .put("runtime_manifest_sha256",text(deployment.value(),"runtime_manifest_sha256")).put("deployment_sha256",deployment.sha256())
                .put("job_directory",text(deployment.value(),"jobs_root")+"/"+j.tenant()+"/"+j.run()+"/"+j.step()+"/"+a.leaseGeneration())
                .put("prompt",prompt.launchText()).put("prompt_sha256",prompt.payloadSha256());
        launch.set("binding",binding(a,schema.hash(envelope)));launch.set("prompt_provenance",prompt.canonicalPins());
        for(String field:List.of("sdk","profile_files","expected_inventory","role_version","profile_artifact"))launch.set(field,deployment.value().required(field));return launch;
    }
    private JsonNode binding(DocumentReviewRuntimeAuthority.Verified a,String envelopeHash){var j=a.job();return json.object().put("tenant_id",j.tenant()).put("run_id",j.run()).put("step_id",j.step()).put("action_id",a.actionId()).put("lease_generation",a.leaseGeneration().longValueExact()).put("input_sha256",j.inputHash()).put("step_envelope_sha256",envelopeHash);}
    public final class Envelope {
        private final DocumentReviewRuntimeAuthority.Verified authority;private final DocumentReviewPromptCompiler.Compiled prompt;
        private final String session;private final JsonNode document,launch;
        private Envelope(DocumentReviewRuntimeAuthority.Verified authority,DocumentReviewPromptCompiler.Compiled prompt,String session,JsonNode document,JsonNode launch){this.authority=authority;this.prompt=prompt;this.session=session;this.document=document.deepCopy();this.launch=launch.deepCopy();}
        public void requireUnchanged()throws Exception {require(authority,prompt);same(document,compile(authority,prompt,session));var j=authority.job();var row=one(db.queryForList("SELECT * FROM document_review_runtime_prepare WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),authority.leaseGeneration(),session,authority.actionId(),j.actor()));same(schema.canonical(document),str(row,"envelope_json"));same(schema.hash(document),str(row,"envelope_sha256"));same(schema.canonical(launch),str(row,"launch_json"));same(schema.hash(launch),str(row,"launch_sha256"));same(deployment.sha256(),str(row,"deployment_sha256"));same(json.manifestSha256(),str(row,"schema_manifest_sha256"));var receipt=json.object().put("session_id",session).put("envelope_sha256",schema.hash(document)).put("launch_sha256",schema.hash(launch));verifyJournal(j,str(row,"audit_id"),str(row,"event_id"),"document.review.runtime.prepared",session,receipt);}
        public void requireProductionReady()throws Exception{requireUnchanged();}
        DocumentReviewRuntimeAuthority.Verified authority(){authority.requireActive();return authority;}DocumentReviewDeployment deployment(){authority.requireActive();return deployment;}
        public String sessionId(){authority.requireActive();return session;}public String inputSha256(){return authority.inputSha256();}
        public String payloadSha256()throws Exception{authority.requireActive();return schema.hash(document);}public JsonNode binding()throws Exception{return DocumentReviewRuntimeAdapter.this.binding(authority,payloadSha256());}
        public JsonNode launch()throws Exception{requireUnchanged();return launch.deepCopy();}public JsonNode document()throws Exception{requireUnchanged();return document.deepCopy();}
    }
    Session admit(HttpServletRequest http,Envelope envelope,DocumentReviewSupervisor.Admitted supervision)throws Exception {
        envelope.requireProductionReady();supervision.requireUnchanged();same(envelope.sessionId(),supervision.sessionId());same(envelope.binding(),supervision.binding());same(deployment.sha256(),supervision.deploymentSha256());
        var a=envelope.authority;var j=a.job();var service=identities.require(http);same(a.workerService(),service.serviceUri());
        var start=(ObjectNode)envelope.launch();start.put("state","START").put("isolation_receipt_id",supervision.receiptId()).put("supervision_sha256",supervision.sha256());
        var previous=db.queryForList("SELECT session_id,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.step(),a.leaseGeneration());
        if(previous.isEmpty()){var now=now();String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();journal(j,audit,event,"document.review.session.opened",envelope.session,start,now);changed(db.update("INSERT INTO professional_runtime_session (tenant_id,project_id,run_id,step_id,lease_generation,session_id,action_id,input_sha256,envelope_sha256,service_uri,certificate_sha256,trust_revision,canonical_start,start_sha256,admission_state,revision,schema_version,schema_manifest_sha256,created_by,created_at,classification,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN',1,'1.0.0',?,?,?,?,?)",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),envelope.session,a.actionId(),j.inputHash(),envelope.payloadSha256(),service.serviceUri(),service.certificateSha256(),service.trustRevision(),schema.canonical(start),schema.hash(start),json.manifestSha256(),j.actor(),now,j.classification(),now));}
        else {var old=one(previous);same(envelope.session,str(old,"session_id"));same(schema.hash(start),str(old,"start_sha256"));}
        identities.require(http);return session(envelope);
    }
    Session session(Envelope envelope)throws Exception {envelope.requireUnchanged();var result=new Session(envelope,readSession(envelope));result.requireUnchanged();return result;}
    private JsonNode readSession(Envelope e)throws Exception {
        e.requireUnchanged();var a=e.authority;var j=a.job();var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND input_sha256=? AND envelope_sha256=? AND service_uri=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),e.session,a.actionId(),j.inputHash(),e.payloadSha256(),a.workerService(),j.actor()));
        if(!Set.of("OPEN","CLOSED").contains(row.get("admission_state")))deny();var start=schema.read(str(row,"canonical_start"));same(schema.hash(start),str(row,"start_sha256"));same(json.manifestSha256(),str(row,"schema_manifest_sha256"));same("START",text(start,"state"));same("SYSTEM_JOB",text(start,"purpose"));same("N07",text(start,"node_kind"));same(e.binding(),start.required("binding"));same(e.session,text(start,"session_id"));same(deployment.sha256(),text(start,"deployment_sha256"));
        var original=(ObjectNode)start.deepCopy();original.put("state","PREPARE");original.remove(List.of("isolation_receipt_id","supervision_sha256"));same(e.launch,original);
        var audits=db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action='document.review.session.opened' AND request_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.project(),j.actor(),e.session);same(start,schema.read(one(audits)));
        var events=db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='document.review.session.opened' LIMIT 2",(r,n)->r.getString(1),j.tenant(),e.session);same(start,schema.read(one(events)));
        return json.object().put("state",str(row,"admission_state")).set("start",start);
    }
    public final class Session {
        private final Envelope envelope;private final JsonNode original;
        private Session(Envelope envelope,JsonNode original){this.envelope=envelope;this.original=original.deepCopy();}
        public void requireUnchanged()throws Exception{envelope.requireUnchanged();same(original.required("start"),readSession(envelope).required("start"));}
        public String state()throws Exception{envelope.authority.requireActive();return text(readSession(envelope),"state");}public String sessionId(){return envelope.sessionId();}public JsonNode binding()throws Exception{return envelope.binding();}
        DocumentReviewRuntimeAuthority.Verified authority(){return envelope.authority();}DocumentReviewDeployment deployment(){return deployment;}
        public EnterpriseAccess.Admission admission(){return authority().admission();}public String actionId(){return authority().actionId();}public String runId(){return authority().job().run();}public String stepId(){return authority().stepId();}
        public JsonNode launch(){authority().requireActive();return original.required("start").deepCopy();}
    }
    Session close(Session session,DocumentReviewSupervisor.Closed closure)throws Exception {
        session.requireUnchanged();closure.requireUnchanged();same(session.sessionId(),closure.sessionId());same(session.binding(),closure.binding());same(deployment.sha256(),closure.deploymentSha256());
        if("OPEN".equals(session.state())){var j=session.authority().job();changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND admission_state='OPEN'",j.tenant(),j.run(),j.step(),session.sessionId(),session.authority().leaseGeneration()));}
        return session(session.envelope);
    }
    void abortAdmission(Session session)throws Exception {
        session.requireUnchanged();var j=session.authority().job();
        if("OPEN".equals(session.state())){var at=now();String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var evidence=json.object().put("session_id",session.sessionId()).put("reason","HOST_ABORT_REQUESTED");evidence.set("binding",session.binding());journal(j,audit,event,"document.review.session.aborted",session.sessionId(),evidence,at);changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=? WHERE tenant_id=? AND run_id=? AND session_id=? AND admission_state='OPEN'",at,j.tenant(),j.run(),session.sessionId()));}
    }
    private void journal(DocumentReviewExecutionService.Job j,String audit,String event,String action,String target,JsonNode evidence,java.time.LocalDateTime now)throws Exception{changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),audit,j.actor(),action,schema.canonical(evidence),target,j.run(),json.manifestSha256(),j.actor(),now,j.classification()));changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",j.tenant(),event,target,action,schema.canonical(evidence),now));}
    private void verifyJournal(DocumentReviewExecutionService.Job j,String audit,String event,String action,String target,JsonNode evidence)throws Exception{same(evidence,schema.read((String)one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND request_id=? AND trace_id=? AND result='SUCCESS' AND schema_manifest_sha256=? LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.project(),audit,j.actor(),action,target,j.run(),json.manifestSha256()))));same(evidence,schema.read((String)one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),j.tenant(),event,target,action))));}
    private java.time.LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static String str(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
