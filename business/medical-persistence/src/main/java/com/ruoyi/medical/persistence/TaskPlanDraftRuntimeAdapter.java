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

/** Dedicated N01 host. Prepared launch is not process admission. A session is opened only
 * from independently authenticated supervision, and Produced requires original settled model
 * evidence plus independently reread successful closure. No TASK/N02 proof is relabeled. */
public final class TaskPlanDraftRuntimeAdapter {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final TaskPlanDraftProducerAuthority producers;private final TaskPlanDraftCandidateNormalizer normalizer;private final TaskPlanDraftDeployment deployment;private final ServiceCertificateIdentity identities;
    public TaskPlanDraftRuntimeAdapter(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,
            TaskPlanDraftDeployment deployment,ServiceCertificateIdentity identities,TaskPlanDraftCandidateNormalizer normalizer,TaskPlanDraftProducerAuthority producers){this.producers=producers;this.normalizer=normalizer;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.deployment=deployment;this.identities=identities;}

    Envelope prepare(TaskPlanDraftRuntimeAuthority.Verified authority,TaskPlanDraftObjectRegistration.Registered input,
            TaskPlanDraftPromptCompiler.Compiled prompt)throws Exception {
        require(authority,input,prompt);var j=authority.job();
        var rows=db.queryForList("SELECT session_id FROM task_plan_draft_runtime_prepare WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.step(),authority.leaseGeneration());
        if(rows.size()>1)bad();String session=rows.isEmpty()?UUID.randomUUID().toString():str(rows.get(0),"session_id");
        var envelope=compile(authority,input,prompt,session);var launch=launch(authority,input,prompt,envelope,session);
        if(rows.isEmpty()) {
            String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=now();
            var receipt=json.object().put("session_id",session).put("envelope_sha256",schema.hash(envelope)).put("launch_sha256",schema.hash(launch));
            journal(j,audit,event,"task.plan_draft.runtime.prepared",session,receipt,now);
            changed(db.update("INSERT INTO task_plan_draft_runtime_prepare (tenant_id,project_id,run_id,step_id,action_id,lease_generation,session_id,input_artifact_id,input_receipt_sha256,envelope_json,envelope_sha256,launch_json,launch_sha256,deployment_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),j.run(),j.step(),authority.actionId(),authority.leaseGeneration(),session,input.artifactId(),input.receiptSha256(),schema.canonical(envelope),schema.hash(envelope),schema.canonical(launch),schema.hash(launch),deployment.sha256(),audit,event,schema.manifest(),j.actor(),now,j.classification()));
        }
        var result=new Envelope(authority,input,prompt,session,envelope,launch);result.requireUnchanged();return result;
    }
    private void require(TaskPlanDraftRuntimeAuthority.Verified a,TaskPlanDraftObjectRegistration.Registered input,TaskPlanDraftPromptCompiler.Compiled prompt)throws Exception {
        a.requireUnchanged();input.requireUnchanged();prompt.requireFor(a);producers.require(a.current()).requireBytes("PLAN_DRAFT_INPUT",input.bytes().length);
        var j=a.job();if(!j.equals(input.job())||!j.inputHash().equals(input.reference().sha256())||!j.inputHash().equals(WireValues.sha256(input.bytes()))||!deployment.workerService().equals(a.workerService())||!j.action().equals(a.actionId()))deny();
        var admitted=schema.read(j.admissionJson());same(deployment.value().required("role_version"),admitted.required("role_version"));
        var release=a.current().current().releaseManifest();same(deployment.sha256(),WireValues.sha256(ContentReadAuthorization.canonicalBytes(deployment.value())));
        same(deployment.value().required("runtime_manifest_sha256").textValue(),schema.hash(release.runtimeManifest()));
        var profile=release.runtimeManifest().profile().artifact();var pin=deployment.value().required("profile_artifact");
        same(profile.artifactId(),text(pin,"artifact_id"));same(profile.sha256(),text(pin,"sha256"));same(profile.sizeBytes(),text(pin,"size_bytes"));same(profile.mediaType(),text(pin,"media_type"));
        var binding=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND kind='HOST_CONTROL' AND tool_version='PLAN_DRAFT_HOST_V1' AND arguments_sha256=? AND input_sha256=? AND state='BOUND' AND invocation_id IS NULL LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.actionId(),a.leaseGeneration(),j.inputHash(),j.inputHash()));
        if(binding.isEmpty())deny();
    }
    private JsonNode compile(TaskPlanDraftRuntimeAuthority.Verified a,TaskPlanDraftObjectRegistration.Registered input,TaskPlanDraftPromptCompiler.Compiled prompt,String session)throws Exception {
        require(a,input,prompt);var j=a.job();var admitted=schema.read(j.admissionJson());
        var e=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","PLAN_DRAFT").put("node_kind","N01")
                .put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("task_revision",j.taskRevision()).put("run_id",j.run()).put("step_id",j.step())
                .put("action_id",a.actionId()).put("lease_generation",a.leaseGeneration().longValueExact()).put("session_id",session).putNull("plan_revision")
                .put("scope_manifest_id",j.scopeId()).put("scope_snapshot_id",text(admitted,"scope_snapshot_id")).put("input_artifact_id",input.artifactId())
                .put("input_sha256",j.inputHash()).put("input_receipt_sha256",input.receiptSha256()).put("dependency_access_set_id",input.accessSetId())
                .put("deployment_revision",deployment.revision()).put("deployment_sha256",deployment.sha256()).put("runtime_manifest_sha256",text(deployment.value(),"runtime_manifest_sha256"))
                .put("worker_service_uri",a.workerService()).put("compiled_prompt",prompt.launchText()).put("prompt_sha256",prompt.payloadSha256());
        e.set("producer",producers.require(a.current()).canonicalPins());e.set("role_version",admitted.required("role_version"));e.set("model_version",admitted.required("model_version"));e.set("execution_policy",admitted.required("execution_policy"));e.set("prompt_provenance",prompt.canonicalPins());
        if(ContentReadAuthorization.canonicalBytes(e).length>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return e;
    }
    private JsonNode launch(TaskPlanDraftRuntimeAuthority.Verified a,TaskPlanDraftObjectRegistration.Registered input,TaskPlanDraftPromptCompiler.Compiled prompt,JsonNode envelope,String session)throws Exception {
        var j=a.job();var launch=json.object().put("state","PREPARE").put("purpose","PLAN_DRAFT").put("session_id",session).put("node_kind","N01").put("role","Planner")
                .put("runtime_manifest_sha256",text(deployment.value(),"runtime_manifest_sha256")).put("deployment_sha256",deployment.sha256())
                .put("job_directory",text(deployment.value(),"jobs_root")+"/"+j.tenant()+"/"+j.run()+"/"+j.step()+"/"+a.leaseGeneration())
                .put("prompt",prompt.launchText()).put("prompt_sha256",prompt.payloadSha256());
        launch.set("binding",binding(a,schema.hash(envelope)));launch.set("prompt_provenance",prompt.canonicalPins());
        for(String field:List.of("sdk","profile_files","expected_inventory","role_version","profile_artifact"))launch.set(field,deployment.value().required(field));return launch;
    }
    private JsonNode binding(TaskPlanDraftRuntimeAuthority.Verified a,String envelopeHash){var j=a.job();return json.object().put("tenant_id",j.tenant()).put("run_id",j.run()).put("step_id",j.step()).put("action_id",a.actionId()).put("lease_generation",a.leaseGeneration().longValueExact()).put("input_sha256",j.inputHash()).put("step_envelope_sha256",envelopeHash);}
    public final class Envelope {
        private final TaskPlanDraftRuntimeAuthority.Verified authority;private final TaskPlanDraftObjectRegistration.Registered input;private final TaskPlanDraftPromptCompiler.Compiled prompt;
        private final String session;private final JsonNode document,launch;
        private Envelope(TaskPlanDraftRuntimeAuthority.Verified authority,TaskPlanDraftObjectRegistration.Registered input,TaskPlanDraftPromptCompiler.Compiled prompt,String session,JsonNode document,JsonNode launch){this.authority=authority;this.input=input;this.prompt=prompt;this.session=session;this.document=document.deepCopy();this.launch=launch.deepCopy();}
        public void requireUnchanged()throws Exception {require(authority,input,prompt);same(document,compile(authority,input,prompt,session));var j=authority.job();var row=one(db.queryForList("SELECT * FROM task_plan_draft_runtime_prepare WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND input_artifact_id=? AND input_receipt_sha256=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),authority.leaseGeneration(),session,authority.actionId(),input.artifactId(),input.receiptSha256(),j.actor()));same(schema.canonical(document),str(row,"envelope_json"));same(schema.hash(document),str(row,"envelope_sha256"));same(schema.canonical(launch),str(row,"launch_json"));same(schema.hash(launch),str(row,"launch_sha256"));same(deployment.sha256(),str(row,"deployment_sha256"));same(schema.manifest(),str(row,"schema_manifest_sha256"));var receipt=json.object().put("session_id",session).put("envelope_sha256",schema.hash(document)).put("launch_sha256",schema.hash(launch));verifyJournal(j,str(row,"audit_id"),str(row,"event_id"),"task.plan_draft.runtime.prepared",session,receipt);}
        public void requireProductionReady()throws Exception{requireUnchanged();}
        TaskPlanDraftRuntimeAuthority.Verified authority(){authority.requireActive();return authority;}TaskPlanDraftDeployment deployment(){authority.requireActive();return deployment;}
        public String sessionId(){authority.requireActive();return session;}public String inputSha256(){return authority.inputSha256();}
        public String payloadSha256()throws Exception{authority.requireActive();return schema.hash(document);}public JsonNode binding()throws Exception{return TaskPlanDraftRuntimeAdapter.this.binding(authority,payloadSha256());}
        public JsonNode launch()throws Exception{requireUnchanged();return launch.deepCopy();}public JsonNode document()throws Exception{requireUnchanged();return document.deepCopy();}
    }
    /** Response only for the independently authenticated N01 supervisor route. The
     * caller must authenticate the configured supervisor service, never the worker. */
    JsonNode supervisorPlan(HttpServletRequest http,Envelope envelope)throws Exception {
        envelope.requireUnchanged();var identity=identities.require(http);
        same(text(deployment.value(),"supervisor_service_uri"),identity.serviceUri());
        same(text(deployment.value(),"supervisor_certificate_sha256"),identity.certificateSha256());
        same(text(deployment.value(),"supervisor_trust_revision"),identity.trustRevision());
        var j=envelope.authority.job();var expires=one(db.query("SELECT lease_expires_at FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? AND lease_owner=? AND lease_expires_at>UTC_TIMESTAMP(6) LIMIT 2",(r,n)->r.getTimestamp(1).toInstant(),j.tenant(),j.run(),j.step(),envelope.authority.leaseGeneration(),envelope.authority.workerService()));
        var reply=json.object().put("session_id",envelope.sessionId()).put("prepared_envelope_base64",Base64.getEncoder().encodeToString(ContentReadAuthorization.canonicalBytes(envelope.document()))).put("expires_at",expires.toString());
        reply.set("binding",envelope.binding());reply.set("prepared_launch",envelope.launch());
        reply.set("preparation_receipt",json.object().put("session_id",envelope.sessionId()).put("envelope_sha256",envelope.payloadSha256()).put("launch_sha256",schema.hash(envelope.launch())));
        envelope.requireUnchanged();return reply;
    }
    Session admit(HttpServletRequest http,Envelope envelope,TaskPlanDraftSupervisor.Admitted supervision)throws Exception {
        envelope.requireProductionReady();supervision.requireUnchanged();same(envelope.sessionId(),supervision.sessionId());same(envelope.binding(),supervision.binding());same(deployment.sha256(),supervision.deploymentSha256());
        var a=envelope.authority;var j=a.job();var service=identities.require(http);same(a.workerService(),service.serviceUri());
        var start=(ObjectNode)envelope.launch();start.put("state","START").put("isolation_receipt_id",supervision.receiptId()).put("supervision_sha256",supervision.sha256());
        var previous=db.queryForList("SELECT session_id,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.step(),a.leaseGeneration());
        if(previous.isEmpty()){var now=now();String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();journal(j,audit,event,"task.plan_draft.session.opened",envelope.session,start,now);changed(db.update("INSERT INTO professional_runtime_session (tenant_id,project_id,run_id,step_id,lease_generation,session_id,action_id,input_sha256,envelope_sha256,service_uri,certificate_sha256,trust_revision,canonical_start,start_sha256,admission_state,revision,schema_version,schema_manifest_sha256,created_by,created_at,classification,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN',1,'1.0.0',?,?,?,?,?)",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),envelope.session,a.actionId(),j.inputHash(),envelope.payloadSha256(),service.serviceUri(),service.certificateSha256(),service.trustRevision(),schema.canonical(start),schema.hash(start),schema.manifest(),j.actor(),now,j.classification(),now));}
        else {var old=one(previous);same(envelope.session,str(old,"session_id"));same(schema.hash(start),str(old,"start_sha256"));}
        identities.require(http);return session(envelope);
    }
    Session session(Envelope envelope)throws Exception {envelope.requireUnchanged();var result=new Session(envelope,readSession(envelope));result.requireUnchanged();return result;}
    private JsonNode readSession(Envelope e)throws Exception {
        e.requireUnchanged();var a=e.authority;var j=a.job();var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND input_sha256=? AND envelope_sha256=? AND service_uri=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),a.leaseGeneration(),e.session,a.actionId(),j.inputHash(),e.payloadSha256(),a.workerService(),j.actor()));
        if(!Set.of("OPEN","CLOSED").contains(row.get("admission_state")))deny();var start=schema.read(str(row,"canonical_start"));same(schema.hash(start),str(row,"start_sha256"));same(schema.manifest(),str(row,"schema_manifest_sha256"));same("START",text(start,"state"));same("PLAN_DRAFT",text(start,"purpose"));same("N01",text(start,"node_kind"));same(e.binding(),start.required("binding"));same(e.session,text(start,"session_id"));same(deployment.sha256(),text(start,"deployment_sha256"));
        var original=(ObjectNode)start.deepCopy();original.put("state","PREPARE");original.remove(List.of("isolation_receipt_id","supervision_sha256"));same(e.launch,original);
        var audits=db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action='task.plan_draft.session.opened' AND request_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.project(),j.actor(),e.session);same(start,schema.read(one(audits)));
        var events=db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='task.plan_draft.session.opened' LIMIT 2",(r,n)->r.getString(1),j.tenant(),e.session);same(start,schema.read(one(events)));
        return json.object().put("state",str(row,"admission_state")).set("start",start);
    }
    public final class Session {
        private final Envelope envelope;private final JsonNode original;
        private Session(Envelope envelope,JsonNode original){this.envelope=envelope;this.original=original.deepCopy();}
        public void requireUnchanged()throws Exception{envelope.requireUnchanged();same(original.required("start"),readSession(envelope).required("start"));}
        public String state()throws Exception{envelope.authority.requireActive();return text(readSession(envelope),"state");}public String sessionId(){return envelope.sessionId();}public JsonNode binding()throws Exception{return envelope.binding();}
        TaskPlanDraftRuntimeAuthority.Verified authority(){return envelope.authority();}TaskPlanDraftDeployment deployment(){return deployment;}
        public EnterpriseAccess.Admission admission(){return authority().admission();}public String actionId(){return authority().actionId();}public String runId(){return authority().job().run();}public String stepId(){return authority().stepId();}
        public JsonNode launch(){authority().requireActive();return original.required("start").deepCopy();}
    }
    Session close(Session session,TaskPlanDraftSupervisor.Closed closure)throws Exception {
        session.requireUnchanged();closure.requireUnchanged();same(session.sessionId(),closure.sessionId());same(session.binding(),closure.binding());same(deployment.sha256(),closure.deploymentSha256());
        if("OPEN".equals(session.state())){var j=session.authority().job();changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND admission_state='OPEN'",j.tenant(),j.run(),j.step(),session.sessionId(),session.authority().leaseGeneration()));}
        return session(session.envelope);
    }
    void abortAdmission(Session session)throws Exception {
        session.requireUnchanged();var j=session.authority().job();
        if("OPEN".equals(session.state())){var at=now();String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var evidence=json.object().put("session_id",session.sessionId()).put("reason","HOST_ABORT_REQUESTED");evidence.set("binding",session.binding());journal(j,audit,event,"task.plan_draft.session.aborted",session.sessionId(),evidence,at);changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=? WHERE tenant_id=? AND run_id=? AND session_id=? AND admission_state='OPEN'",at,j.tenant(),j.run(),session.sessionId()));}
    }
    Produced produce(Session session,TaskPlanDraftSupervisor.Closed closure,TaskPlanDraftInvocationService.Terminal terminal)throws Exception {
        session.requireUnchanged();closure.requireSuccessfulExit();terminal.requireFor(session,closure);if(!"CLOSED".equals(session.state()))deny();
        if(one(db.query("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='task.plan_draft.session.aborted'",(r,n)->r.getInt(1),session.authority().job().tenant(),session.sessionId()))!=0)deny();
        if(one(db.query("SELECT COUNT(*) FROM professional_runtime_observation WHERE tenant_id=? AND session_id=? AND kind IN ('ADMISSION_CLOSED','HOST_FAILURE')",(r,n)->r.getInt(1),session.authority().job().tenant(),session.sessionId()))!=0)deny();
        var archive=schema.read(new String(closure.sessionExport(),StandardCharsets.UTF_8));ProfessionalRuntimeObservations.closed(archive,Set.of("files","observation"));var observed=archive.required("observation");ProfessionalRuntimeObservations.closed(observed,Set.of("session_id","binding","finish_reason","final_response"));same(session.sessionId(),text(observed,"session_id"));same(session.binding(),observed.required("binding"));same("completed",text(observed,"finish_reason"));
        // Model result registration, not SDK final_response, remains the byte producer.
        var model=terminal.output();var blocks=model.required("blocks");if(!blocks.isArray()||blocks.size()!=1||!"text".equals(blocks.get(0).path("type").asText()))bad();
        String text=blocks.get(0).required("text").textValue();if(text==null||text.getBytes(StandardCharsets.UTF_8).length>1048576)bad();
        var candidate=schema.read(text);TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.PLAN_CANDIDATE,candidate).read();
        var authority=session.authority();var j=authority.job();var admitted=schema.read(j.admissionJson());same(text(admitted.required("template"),"version_id"),text(candidate,"template_version"));same(text(admitted.required("profile"),"version_id"),text(candidate,"profile_revision"));
        var normalized=normalizer.normalize(authority.current(),candidate);candidate=normalized.candidate();
        var output=json.object().put("artifact_kind","PLAN_CANDIDATE");output.set("content",candidate);
        var artifact=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("tenant_id",j.tenant()).put("project_id",j.project()).put("run_id",j.run()).put("step_id",j.step()).put("input_manifest_sha256",j.inputHash()).putNull("plan_revision").put("snapshot_id",text(admitted,"scope_snapshot_id")).put("dependency_access_set_id",terminal.accessSetId()).put("content_sha256",schema.hash(output));artifact.putArray("target_section_ids");artifact.set("output",output);
        byte[] produced=ContentReadAuthorization.canonicalBytes(artifact);producers.require(authority.current()).requireBytes("PLAN_CANDIDATE",produced.length);
        return new Produced(session,closure,terminal,normalized.pins(),produced);
    }
    public final class Produced {
        private final Session session;private final TaskPlanDraftSupervisor.Closed closure;private final TaskPlanDraftInvocationService.Terminal terminal;private final byte[] bytes;private final JsonNode normalization;
        private Produced(Session session,TaskPlanDraftSupervisor.Closed closure,TaskPlanDraftInvocationService.Terminal terminal,JsonNode normalization,byte[] bytes){this.normalization=normalization.deepCopy();this.session=session;this.closure=closure;this.terminal=terminal;this.bytes=bytes.clone();}
        public void requireUnchanged()throws Exception{session.requireUnchanged();closure.requireSuccessfulExit();terminal.requireFor(session,closure);if(!Arrays.equals(bytes,produce(session,closure,terminal).bytes))bad();}
        public TaskPlanDraftRuntimeAuthority.Verified authority(){return session.authority();}public byte[] bytes()throws Exception{requireUnchanged();return bytes.clone();}
        public String sessionId(){return session.sessionId();}public String outputSha256()throws Exception{authority().requireActive();return WireValues.sha256(bytes);}
        public JsonNode normalizationPins()throws Exception{requireUnchanged();return normalization.deepCopy();}
        public JsonNode dependencyManifest()throws Exception{requireUnchanged();return terminal.dependencyManifest();}
        public List<String> contentRoots()throws Exception{requireUnchanged();return terminal.contentRoots();}
        public String accessSetId()throws Exception{requireUnchanged();return terminal.accessSetId();}
        public JsonNode invocationPins()throws Exception{requireUnchanged();return terminal.canonicalPins();}
        public JsonNode supervisionPins()throws Exception{requireUnchanged();var n=json.object().put("session_id",sessionId()).put("receipt_id",closure.receiptId()).put("sha256",closure.sha256()).put("deployment_sha256",closure.deploymentSha256());n.set("binding",closure.binding());return n;}
    }
    private void journal(TaskPlanDraftJobs.Job j,String audit,String event,String action,String target,JsonNode evidence,java.time.LocalDateTime now)throws Exception{changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),audit,j.actor(),action,schema.canonical(evidence),target,j.run(),schema.manifest(),j.actor(),now,j.classification()));changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",j.tenant(),event,target,action,schema.canonical(evidence),now));}
    private void verifyJournal(TaskPlanDraftJobs.Job j,String audit,String event,String action,String target,JsonNode evidence)throws Exception{same(evidence,schema.read(one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND request_id=? AND trace_id=? AND result='SUCCESS' AND schema_manifest_sha256=? LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.project(),audit,j.actor(),action,target,j.run(),schema.manifest()))));same(evidence,schema.read(one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),j.tenant(),event,target,action))));}
    private java.time.LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static String str(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
