package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent EVAL professional session producer. Preparing an envelope never admits a process;
 * only an authenticated independent supervisor can open its exact immutable original session. */
public final class FormalEvaluationNodeRuntime {
    private final org.springframework.beans.factory.ObjectProvider<FormalEvaluationDeterministicRuntime> deterministic;private final JdbcTemplate db;private final PermissionSchema json;private final FormalEvaluationNodeLeases leases;
    private final FormalEvaluationJobs jobs;private final FormalEvaluationLaunchLedger launches;private final FormalEvaluationNodeInputs inputs;private final FormalEvaluationRuntimeObjects runtimeObjects;private final FormalEvaluationSupervisor supervisor;private final FormalEvaluationInvocationService invocations;private final FormalEvaluationNodeOutcomes nodeOutcomes;private final FormalEvaluationToolReceipts tools;
    private final FormalEvaluationDeployments deployments;private final ServiceCertificateIdentity identities;
    private final JsonMapper parser=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalEvaluationNodeRuntime(DataSource data,PermissionSchema json,FormalEvaluationNodeLeases leases,FormalEvaluationDeployments deployments,ServiceCertificateIdentity identities,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,FormalEvaluationNodeInputs inputs,FormalEvaluationRuntimeObjects runtimeObjects,FormalEvaluationSupervisor supervisor,FormalEvaluationInvocationService invocations,FormalEvaluationNodeOutcomes nodeOutcomes,FormalEvaluationToolReceipts tools,org.springframework.beans.factory.ObjectProvider<FormalEvaluationDeterministicRuntime> deterministic){
        this.deterministic=deterministic;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.leases=leases;this.deployments=deployments;this.identities=identities;this.jobs=jobs;this.launches=launches;this.inputs=inputs;this.runtimeObjects=runtimeObjects;this.supervisor=supervisor;this.invocations=invocations;this.nodeOutcomes=nodeOutcomes;this.tools=tools;
    }
    public Envelope prepare(FormalEvaluationNodeLeases.Lease lease)throws Exception {
        if("N03".equals(lease.node().node().nodeKind())||FormalEvaluationExecutionProtocol.deterministic(lease.node().node()))deny();
        lease.requireDispatchCurrent();requireDeployment(lease);var deployment=deployment(lease);var j=lease.job();
        one(db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? FOR UPDATE",j.tenantId(),j.runId(),lease.stepId()));
        var rows=preparedRows(lease);
        if(rows.isEmpty()){
            String session=UUID.randomUUID().toString(),audit=id(),event=id();var envelope=compile(lease,session);var launch=launch(lease,envelope,session);var at=now();
            var receipt=json.object().put("session_id",session).put("envelope_sha256",hash(envelope)).put("launch_sha256",hash(launch));
            journal(j,audit,event,"evaluation.runtime.prepared",session,receipt,at);
            changed(db.update("INSERT INTO formal_evaluation_runtime_prepare (tenant_id,project_id,run_id,step_id,action_id,lease_generation,session_id,envelope_json,envelope_sha256,launch_json,launch_sha256,deployment_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),j.runId(),lease.stepId(),lease.actionId(),lease.generation(),session,canonical(envelope),hash(envelope),canonical(launch),hash(launch),deployment.sha256(),audit,event,json.manifestSha256(),j.actorId(),at,j.classification()));
        }else if(rows.size()!=1)bad();
        return requireEnvelope(lease);
    }
    /** The only host-launch response. An uncertain response is recover-only, never reissued. */
    public JsonNode issueLaunch(Envelope envelope)throws Exception {
        envelope.requireProductionReady();var l=envelope.lease();l.requireDispatchCurrent();var j=l.job();
        one(db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? FOR UPDATE",j.tenantId(),j.runId(),l.stepId()));
        if(!db.queryForList("SELECT session_id FROM formal_evaluation_host_launch_attempt WHERE tenant_id=? AND session_id=? LIMIT 2",j.tenantId(),envelope.sessionId()).isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        var stored=runtimeObjects.readOriginal(l.admission(),j.runId(),l.stepId(),"STEP_ENVELOPE");same(envelope.payloadSha256(),stored.artifact().sha256());
        String audit=id(),event=id();var at=now();var launch=envelope.launch();journal(j,audit,event,"evaluation.host.launch_attempted",envelope.sessionId(),launch,at);
        changed(db.update("INSERT INTO formal_evaluation_host_launch_attempt (tenant_id,project_id,run_id,step_id,session_id,launch_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),j.runId(),l.stepId(),envelope.sessionId(),hash(launch),audit,event,json.manifestSha256(),j.actorId(),at,j.classification()));return launch;
    }
    public Envelope requireEnvelope(FormalEvaluationNodeLeases.Lease lease)throws Exception {
        lease.requireCurrent();requireDeployment(lease);var deployment=deployment(lease);var row=one(preparedRows(lease));String session=text(row,"session_id");
        var stored=parser.readTree(text(row,"envelope_json"));lease.input().context().requireOriginalPins(lease.admission(),stored.required("context_reads"));
        var envelope=compile(lease,session,stored.required("context_reads"));var launch=launch(lease,envelope,session);var j=lease.job();
        same(canonical(envelope),row.get("envelope_json"));same(hash(envelope),row.get("envelope_sha256"));same(canonical(launch),row.get("launch_json"));same(hash(launch),row.get("launch_sha256"));
        same(j.projectId(),row.get("project_id"));same(j.actorId(),row.get("created_by"));same(deployment.sha256(),row.get("deployment_sha256"));same("1.0.0",row.get("schema_version"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));
        var receipt=json.object().put("session_id",session).put("envelope_sha256",hash(envelope)).put("launch_sha256",hash(launch));verifyJournal(j,text(row,"audit_id"),text(row,"event_id"),"evaluation.runtime.prepared",session,receipt);
        return new Envelope(lease,session,envelope,launch);
    }
    private FormalEvaluationDeployment deployment(FormalEvaluationNodeLeases.Lease lease){return deployments.require(lease.node().node().nodeKind());}
    private void requireDeployment(FormalEvaluationNodeLeases.Lease lease)throws Exception {
        var deployment=deployment(lease);lease.requireCurrent();same(deployment.workerService(),lease.workerService());same(lease.node().node().cell().runtimeManifestSha256(),text(deployment.value(),"runtime_manifest_sha256"));
        same(json.tree(lease.node().node().role()),deployment.value().required("role_version"));
        same(role(lease.node().node().nodeKind()),text(deployment.value(),"role_name"));
        var manifest=lease.input().context().content().required("candidate_manifest").required("runtime_manifest");
        same(lease.node().node().cell().runtimeManifestSha256(),hash(manifest));
        same(deployment.value().required("profile_artifact"),manifest.required("profile").required("artifact"));
        var tools=json.object();for(var tool:parser.readTree(lease.input().bytes()).required("tools")){var function=tool.required("function");String name=text(function,"name");if(tools.has(name))bad();tools.put(name,hash(function.required("parameters")));}same(tools,deployment.value().required("expected_inventory").required("tool_schema_hashes"));
    }
    private JsonNode compile(FormalEvaluationNodeLeases.Lease lease,String session)throws Exception {return compile(lease,session,lease.input().context().pins());}
    private JsonNode compile(FormalEvaluationNodeLeases.Lease lease,String session,JsonNode contextPins)throws Exception {
        requireDeployment(lease);var deployment=deployment(lease);var j=lease.job();var n=lease.node().node();
        var envelope=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","EVALUATION_VERIFICATION")
            .put("tenant_id",j.tenantId()).put("project_id",j.projectId()).put("execution_id",j.executionId()).put("evaluation_id",n.cell().evaluationId())
            .put("run_id",j.runId()).put("step_id",lease.stepId()).put("node_kind",n.nodeKind()).put("node_sha256",lease.node().nodeSha256())
            .put("action_id",lease.actionId()).put("lease_generation",lease.generation()).put("session_id",session)
            .put("input_sha256",lease.input().sha256()).put("runtime_manifest_sha256",n.cell().runtimeManifestSha256())
            .put("deployment_revision",deployment.revision()).put("deployment_sha256",deployment.sha256()).put("worker_service_uri",lease.workerService());
        envelope.set("node",json.tree(n));envelope.set("input",json.tree(lease.input().artifact()));envelope.set("compiler",lease.input().evidence());envelope.set("context_reads",contextPins.deepCopy());
        if(canonical(envelope).getBytes(StandardCharsets.UTF_8).length>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return envelope;
    }
    private JsonNode provenance(FormalEvaluationNodeLeases.Lease lease)throws Exception {
        return json.object().put("schema_version","1.0.0").put("compiler","EVALUATION_REGISTERED_EXACT_INPUT_V1")
            .put("node_kind",lease.node().node().nodeKind()).put("input_artifact_id",lease.input().artifact().artifactId())
            .put("input_sha256",lease.input().sha256()).put("launch_sha256",lease.input().sha256())
            .set("original_compiler",lease.input().evidence());
    }
    private JsonNode launch(FormalEvaluationNodeLeases.Lease lease,JsonNode envelope,String session)throws Exception {
        var deployment=deployment(lease);var j=lease.job();String prompt=new String(lease.input().bytes(),StandardCharsets.UTF_8);same(lease.input().sha256(),org.apache.commons.codec.digest.DigestUtils.sha256Hex(prompt));
        var launch=json.object().put("state","PREPARE").put("purpose","EVALUATION_VERIFICATION").put("job_type","EVALUATION_VERIFICATION")
            .put("session_id",session).put("node_kind",lease.node().node().nodeKind()).put("role",role(lease.node().node().nodeKind()))
            .put("runtime_manifest_sha256",lease.node().node().cell().runtimeManifestSha256()).put("deployment_sha256",deployment.sha256())
            .put("job_directory",text(deployment.value(),"jobs_root")+"/"+j.tenantId()+"/"+j.runId()+"/"+lease.stepId()+"/"+lease.generation())
            .put("prompt",prompt).put("prompt_sha256",lease.input().sha256());
        launch.set("binding",binding(lease,hash(envelope)));launch.set("prompt_provenance",provenance(lease));
        for(String key:List.of("sdk","profile_files","expected_inventory","role_version","profile_artifact"))launch.set(key,deployment.value().required(key));return launch;
    }
    private JsonNode binding(FormalEvaluationNodeLeases.Lease lease,String hash){return json.object().put("tenant_id",lease.job().tenantId()).put("run_id",lease.job().runId()).put("step_id",lease.stepId()).put("action_id",lease.actionId()).put("lease_generation",lease.generation()).put("input_sha256",lease.input().sha256()).put("step_envelope_sha256",hash);}
    public final class Envelope {
        private final FormalEvaluationNodeLeases.Lease lease;private final String session;private final JsonNode document,launch;
        private Envelope(FormalEvaluationNodeLeases.Lease lease,String session,JsonNode document,JsonNode launch){this.lease=lease;this.session=session;this.document=document.deepCopy();this.launch=launch.deepCopy();}
        public void requireUnchanged()throws Exception{lease.requireCurrent();var fresh=requireEnvelope(lease);same(session,fresh.session);same(document,fresh.document);same(launch,fresh.launch);}
        public void requireProductionReady()throws Exception{requireUnchanged();}
        public FormalEvaluationNodeLeases.Lease lease(){return lease;}public FormalEvaluationDeployment deployment(){return FormalEvaluationNodeRuntime.this.deployment(lease());}
        public String sessionId(){lease.admission();return session;}public String inputSha256(){return lease.input().sha256();}
        public String payloadSha256()throws Exception{lease.admission();return hash(document);}public JsonNode binding()throws Exception{return FormalEvaluationNodeRuntime.this.binding(lease,payloadSha256());}
        public JsonNode launch()throws Exception{requireUnchanged();return launch.deepCopy();}public JsonNode document()throws Exception{requireUnchanged();return document.deepCopy();}
    }
    public Session admit(HttpServletRequest http,Envelope envelope,FormalEvaluationSupervisor.Admitted supervisor)throws Exception {
        envelope.requireProductionReady();supervisor.requireUnchanged();same(envelope.sessionId(),supervisor.sessionId());same(envelope.binding(),supervisor.binding());same(envelope.deployment().sha256(),supervisor.deploymentSha256());
        var lease=envelope.lease();var j=lease.job();var peer=identities.require(http);same(lease.workerService(),peer.serviceUri());
        var start=(ObjectNode)envelope.launch();start.put("state","START").put("isolation_receipt_id",supervisor.receiptId()).put("supervision_sha256",supervisor.sha256());
        var old=db.queryForList("SELECT session_id,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",j.tenantId(),j.runId(),lease.stepId(),lease.generation());
        if(old.isEmpty()){
            var at=now();journal(j,id(),id(),"evaluation.session.opened",envelope.sessionId(),start,at);
            changed(db.update("INSERT INTO professional_runtime_session (tenant_id,project_id,run_id,step_id,lease_generation,session_id,action_id,input_sha256,envelope_sha256,service_uri,certificate_sha256,trust_revision,canonical_start,start_sha256,admission_state,revision,schema_version,schema_manifest_sha256,created_by,created_at,classification,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN',1,'1.0.0',?,?,?,?,?)",j.tenantId(),j.projectId(),j.runId(),lease.stepId(),lease.generation(),envelope.sessionId(),lease.actionId(),envelope.inputSha256(),envelope.payloadSha256(),peer.serviceUri(),peer.certificateSha256(),peer.trustRevision(),canonical(start),hash(start),json.manifestSha256(),j.actorId(),at,j.classification(),at));
        }else{var row=one(old);same(envelope.sessionId(),row.get("session_id"));same(hash(start),row.get("start_sha256"));}
        identities.require(http);return session(envelope);
    }
    public Session requireSession(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service,String sessionId)throws Exception {
        var lease=leases.require(a,ref,step,input,service);var envelope=requireEnvelope(lease);same(sessionId,envelope.sessionId());return session(envelope);
    }
    public Session session(Envelope envelope)throws Exception{return new Session(envelope,readSession(envelope));}
    private JsonNode readSession(Envelope e)throws Exception {
        e.requireUnchanged();var l=e.lease();var j=l.job();var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? AND action_id=? AND input_sha256=? AND envelope_sha256=? AND service_uri=? AND created_by=? LIMIT 2",j.tenantId(),j.projectId(),j.runId(),l.stepId(),l.generation(),e.sessionId(),l.actionId(),e.inputSha256(),e.payloadSha256(),l.workerService(),j.actorId()));
        if(!Set.of("OPEN","CLOSED").contains(row.get("admission_state")))deny();var start=parser.readTree(text(row,"canonical_start"));same(hash(start),row.get("start_sha256"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));same("START",text(start,"state"));same("EVALUATION_VERIFICATION",text(start,"purpose"));same(e.binding(),start.required("binding"));same(e.sessionId(),text(start,"session_id"));same(e.deployment().sha256(),text(start,"deployment_sha256"));
        var original=(ObjectNode)start.deepCopy();original.put("state","PREPARE");original.remove(List.of("isolation_receipt_id","supervision_sha256"));same(e.launch,original);
        same(canonical(start),one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action='evaluation.session.opened' AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),j.tenantId(),j.projectId(),j.actorId(),e.sessionId(),j.runId())));
        same(canonical(start),one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='evaluation.session.opened' LIMIT 2",(r,n)->r.getString(1),j.tenantId(),e.sessionId())));
        return json.object().put("state",text(row,"admission_state")).set("start",start);
    }
    public final class Session {
        private final Envelope envelope;private final JsonNode original;
        private Session(Envelope envelope,JsonNode original){this.envelope=envelope;this.original=original.deepCopy();}
        public void requireCurrent()throws Exception{envelope.requireUnchanged();same(original.required("start"),readSession(envelope).required("start"));}
        public void requireUnchanged()throws Exception{requireCurrent();}
        public void requireExecutionCurrent()throws Exception{requireCurrent();lease().requireDispatchCurrent();same("OPEN",state());}
        public FormalEvaluationNodeLeases.Lease lease(){return envelope.lease();}public Envelope envelope(){return envelope;}public FormalEvaluationDeployment deployment(){return FormalEvaluationNodeRuntime.this.deployment(lease());}
        public EnterpriseAccess.Admission admission(){return lease().admission();}public String sessionId(){return envelope.sessionId();}public JsonNode binding()throws Exception{return envelope.binding();}
        public JsonNode launch(){lease().admission();return original.required("start").deepCopy();}public String state()throws Exception{return text(readSession(envelope),"state");}
    }
    public Session requireRecoverySession(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service,String sessionId)throws Exception {
        var lease=leases.requireRecovery(a,ref,step,input,service);var envelope=requireEnvelope(lease);same(sessionId,envelope.sessionId());return session(envelope);
    }
    public JsonNode closeAdmission(Session session)throws Exception {
        session.requireCurrent();var l=session.lease();var j=l.job();
        if("OPEN".equals(session.state())){var at=now();var evidence=json.object().put("schema_version","1.0.0").put("session_id",session.sessionId()).put("admission","CLOSED");evidence.set("binding",session.binding());journal(j,id(),id(),"evaluation.session.admission_closed",session.sessionId(),evidence,at);changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND session_id=? AND admission_state='OPEN'",j.tenantId(),session.sessionId()));}
        return json.object().put("session_id",session.sessionId()).put("admission","CLOSED");
    }
    public Session close(Session session,FormalEvaluationSupervisor.Closed closed)throws Exception {
        session.requireCurrent();closed.requireUnchanged();same(session.sessionId(),closed.sessionId());same(session.binding(),closed.binding());same(session.deployment().sha256(),closed.deploymentSha256());
        if("OPEN".equals(session.state())){var l=session.lease();var j=l.job();changed(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND admission_state='OPEN'",j.tenantId(),j.runId(),l.stepId(),session.sessionId(),l.generation()));}
        return session(session.envelope());
    }
    /** Finalize only physically registered originals produced by a genuine business checker. */
    public FormalEvaluationProtocol.NodeReceipt finish(FormalEvaluationNodeOutcomes.Checked checked,
            FormalEvaluationRuntimeObjects.Registered envelopeObject,FormalEvaluationRuntimeObjects.Registered exportObject,
            FormalEvaluationRuntimeObjects.Registered outputObject,FormalEvaluationRuntimeObjects.Registered resultObject)throws Exception {
        checked.requireUnchanged();tools.requireCompleteOriginal(checked.session().admission(),checked.session().lease().job().runId(),checked.session().lease().stepId(),checked.session().sessionId());for(var object:List.of(envelopeObject,exportObject,outputObject,resultObject))object.requireUnchanged();
        var session=checked.session();var lease=session.lease();var j=lease.job();var a=lease.admission();same("CLOSED",session.state());
        var envelope=runtimeObjects.readOriginal(a,j.runId(),lease.stepId(),"STEP_ENVELOPE");var exported=runtimeObjects.readOriginal(a,j.runId(),lease.stepId(),"SESSION_EXPORT");
        var output=runtimeObjects.readOriginal(a,j.runId(),lease.stepId(),"BUSINESS_OUTPUT");var result=runtimeObjects.readOriginal(a,j.runId(),lease.stepId(),"STEP_RESULT");
        same(envelope.artifact(),envelopeObject.artifact());same(exported.artifact(),exportObject.artifact());same(output.artifact(),outputObject.artifact());same(result.artifact(),resultObject.artifact());
        var expected=(ObjectNode)checked.document();expected.set("output_artifact",json.tree(output.artifact()));same(expected,parser.readTree(result.payload()));same(checked.outputDocument(),parser.readTree(output.payload()));
        if(!db.queryForList("SELECT receipt_id FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 1",j.tenantId(),j.runId()).isEmpty())deny();
        String state=text(expected,"outcome");if(!Set.of("SUCCEEDED","WAITING_INPUT","FAILED").contains(state))bad();
        var prior=db.queryForList("SELECT * FROM formal_evaluation_node_outcome WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",j.tenantId(),j.runId(),lease.stepId());
        if(!prior.isEmpty())return originalNodeReceipt(a,j.runId(),lease.stepId());
        String receipt=id(),audit=id(),event=id();var evidence=json.object().put("schema_version","1.0.0").put("receipt_id",receipt).put("run_id",j.runId()).put("step_id",lease.stepId()).put("node_sha256",lease.node().nodeSha256()).put("session_id",session.sessionId()).put("state",state).put("closed_receipt_id",checked.closed().receiptId()).put("closed_sha256",checked.closed().sha256());
        evidence.set("step_envelope",json.tree(envelope.artifact()));evidence.set("session_export",json.tree(exported.artifact()));evidence.set("step_result",json.tree(result.artifact()));evidence.set("business_output",json.tree(output.artifact()));evidence.set("invocations",json.tree(checked.invocations()));
        var at=now();journal(j,audit,event,"evaluation.node.completed",receipt,evidence,at);String stepHash=hash(evidence),runHash=launches.requireOriginalAccepted(a,j);
        changed(db.update("INSERT INTO formal_evaluation_node_outcome (tenant_id,project_id,run_id,step_id,receipt_id,session_id,state,canonical_receipt,receipt_sha256,output_artifact_id,output_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),j.runId(),lease.stepId(),receipt,session.sessionId(),state,canonical(evidence),stepHash,output.artifact().artifactId(),output.artifact().sha256(),audit,event,json.manifestSha256(),j.actorId(),at,j.classification()));
        var record=new FormalEvaluationExecutionProtocol.Record(lease.node().node(),j.runId(),lease.stepId(),lease.generation(),lease.input().artifact(),envelope.artifact(),result.artifact(),exported.artifact(),checked.invocations(),List.of(output.artifact()),List.of(),runHash,stepHash);
        changed(db.update("INSERT INTO formal_evaluation_node_result (tenant_id,project_id,run_id,step_id,lease_generation,canonical_record,record_sha256,session_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),j.runId(),lease.stepId(),lease.generation(),canonical(json.tree(record)),hash(json.tree(record)),session.sessionId(),json.manifestSha256(),j.actorId(),at,j.classification()));
        // The real business state, never merely process exit, advances the node.
        changed(db.update("UPDATE run_step SET status=?,output_ref=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND lease_generation=?",state,canonical(json.tree(output.artifact())),j.tenantId(),j.runId(),lease.stepId(),lease.generation()));
        return originalNodeReceipt(a,j.runId(),lease.stepId());
    }
    public Cancellation requestCancellation(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference)throws Exception {
        var job=jobs.byRun(a,reference.runId());launches.requireAccepted(a,job,reference);one(db.queryForList("SELECT run_id FROM system_job_run WHERE tenant_id=? AND run_id=? FOR UPDATE",job.tenantId(),job.runId()));
        var rows=db.queryForList("SELECT * FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 2",job.tenantId(),job.runId());
        if(rows.isEmpty()){
            same("RUNNING",one(db.query("SELECT status FROM system_job_run WHERE tenant_id=? AND run_id=? AND ended_at IS NULL",(r,n)->r.getString(1),job.tenantId(),job.runId())));
            var sessions=json.object().putArray("sessions");for(var row:db.queryForList("SELECT session_id,envelope_json,launch_json,envelope_sha256,launch_sha256 FROM formal_evaluation_runtime_prepare WHERE tenant_id=? AND run_id=? ORDER BY step_id",job.tenantId(),job.runId())){var envelope=parser.readTree(text(row,"envelope_json"));var launch=parser.readTree(text(row,"launch_json"));same(hash(envelope),row.get("envelope_sha256"));same(hash(launch),row.get("launch_sha256"));var pin=sessions.addObject().put("session_id",text(row,"session_id"));pin.set("binding",launch.required("binding"));pin.put("node_kind",text(launch,"node_kind"));}
            String receipt=id(),audit=id(),event=id();var evidence=json.object().put("schema_version","1.0.0").put("receipt_id",receipt).put("run_id",job.runId()).put("admission_sha256",job.admissionSha256()).put("state","CANCEL_REQUESTED");evidence.set("original_sessions",sessions);evidence.set("expected_nodes",json.tree(reference.steps()));var at=now();journal(job,audit,event,"evaluation.cancel.requested",receipt,evidence,at);
            changed(db.update("INSERT INTO formal_evaluation_cancel_intent (tenant_id,project_id,run_id,receipt_id,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),receipt,canonical(evidence),hash(evidence),audit,event,json.manifestSha256(),job.actorId(),at,job.classification()));
        }
        db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND admission_state='OPEN'",job.tenantId(),job.runId());return requireCancellation(a,reference);
    }
    public Cancellation requireCancellation(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference)throws Exception {
        var job=jobs.byRun(a,reference.runId());launches.requireAccepted(a,job,reference);var row=one(db.queryForList("SELECT * FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 2",job.tenantId(),job.runId()));
        same(job.projectId(),row.get("project_id"));same(job.actorId(),row.get("created_by"));same(job.classification(),row.get("classification"));same("1.0.0",row.get("schema_version"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));var evidence=parser.readTree(text(row,"canonical_receipt"));same(hash(evidence),row.get("receipt_sha256"));same(job.runId(),text(evidence,"run_id"));same(job.admissionSha256(),text(evidence,"admission_sha256"));same("CANCEL_REQUESTED",text(evidence,"state"));same(text(row,"receipt_id"),text(evidence,"receipt_id"));same(canonical(evidence),row.get("canonical_receipt"));if(!evidence.required("original_sessions").isArray()||evidence.required("original_sessions").size()>reference.steps().size())bad();same(json.tree(reference.steps()),evidence.required("expected_nodes"));verifyJournal(job,text(row,"audit_id"),text(row,"event_id"),"evaluation.cancel.requested",text(row,"receipt_id"),evidence);
        var actual=db.query("SELECT session_id FROM formal_evaluation_runtime_prepare WHERE tenant_id=? AND run_id=? ORDER BY step_id",(r,n)->r.getString(1),job.tenantId(),job.runId());var fixed=new ArrayList<String>();for(var session:evidence.required("original_sessions"))fixed.add(text(session,"session_id"));same(actual,fixed);
        if(!db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND admission_state='OPEN' LIMIT 1",job.tenantId(),job.runId()).isEmpty())deny();return new Cancellation(a,reference,job,evidence,text(row,"receipt_sha256"));
    }
    /** All prepared sessions stay obligations. A missing receipt is not a terminated process. */
    public JsonNode cancellationTerminations(Cancellation proof)throws Exception {
        proof.requireCurrent();var result=parser.createArrayNode();var job=proof.job;
        for(var original:proof.document().required("original_sessions")){
            String session=text(original,"session_id");var binding=original.required("binding");String step=text(binding,"step_id");
            var prepared=one(db.queryForList("SELECT launch_json,launch_sha256 FROM formal_evaluation_runtime_prepare WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? LIMIT 2",job.tenantId(),job.runId(),step,session));
            var launch=parser.readTree(text(prepared,"launch_json"));same(hash(launch),prepared.get("launch_sha256"));same(binding,launch.required("binding"));same(text(original,"node_kind"),text(launch,"node_kind"));
            var pin=result.addObject().put("session_id",session).put("step_id",step);
            var attempts=db.queryForList("SELECT launch_sha256 FROM formal_evaluation_host_launch_attempt WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? LIMIT 2",job.tenantId(),job.runId(),step,session);
            if(attempts.isEmpty()){
                if(!db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND session_id=? LIMIT 1",job.tenantId(),session).isEmpty())bad();
                pin.put("disposition","HOST_LAUNCH_NEVER_ISSUED").put("launch_sha256",text(prepared,"launch_sha256"));
            }else{
                same(prepared.get("launch_sha256"),one(attempts).get("launch_sha256"));
                var closed=supervisor.requireOriginalTerminated(proof.admission(),job,step,session,binding,deployments.require(text(original,"node_kind")));
                pin.put("disposition","ORIGINAL_PROCESS_TERMINATED").put("closed_receipt_id",closed.receiptId()).put("closed_sha256",closed.sha256());
            }
        }
        return result;
    }
    public final class Cancellation {
        private final EnterpriseAccess.Admission admission;private final FormalEvaluationProtocol.Reference reference;private final FormalEvaluationJobs.Job job;private final JsonNode receipt;private final String hash;
        private Cancellation(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,FormalEvaluationJobs.Job job,JsonNode receipt,String hash){admission=a;this.reference=reference;this.job=job;this.receipt=receipt.deepCopy();this.hash=hash;}
        public void requireCurrent()throws Exception{same(receipt,requireCancellation(admission,reference).receipt);}
        public EnterpriseAccess.Admission admission(){FormalEvaluationExecutionAdmission.current(admission);return admission;}public String tenantId(){admission();return job.tenantId();}public String runId(){admission();return job.runId();}public String projectId(){admission();return job.projectId();}public String actorId(){admission();return job.actorId();}public String receiptId(){admission();return text(receipt,"receipt_id");}public String sha256(){admission();return hash;}public JsonNode document(){admission();return receipt.deepCopy();}
    }
    public FormalEvaluationProtocol.NodeReceipt waiting(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,FormalEvaluationStageInputPreparation.Waiting proof)throws Exception {
        proof.requireCurrent(a);var job=jobs.byRun(a,reference.runId());launches.requireAccepted(a,job,reference);if(!db.queryForList("SELECT receipt_id FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 1",job.tenantId(),job.runId()).isEmpty())deny();same(job.runId(),proof.runId());same(step.stepId(),proof.stepId());if(!reference.steps().contains(step))bad();
        var old=db.queryForList("SELECT * FROM formal_evaluation_node_waiting WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",job.tenantId(),job.runId(),step.stepId());if(!old.isEmpty())return originalWaiting(a,job,step,one(old));
        if(!db.queryForList("SELECT step_id FROM formal_evaluation_node_dispatch WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1",job.tenantId(),job.runId(),step.stepId()).isEmpty())bad();
        String receipt=id(),audit=id(),event=id();var evidence=json.object().put("schema_version","1.0.0").put("receipt_id",receipt).put("run_id",job.runId()).put("step_id",step.stepId()).put("node_sha256",step.nodeSha256()).put("state","WAITING_INPUT").put("reason_code",proof.reasonCode());evidence.set("missing_requirements",json.tree(proof.missingRequirements()));var at=now();journal(job,audit,event,"evaluation.node.waiting",receipt,evidence,at);
        changed(db.update("INSERT INTO formal_evaluation_node_waiting (tenant_id,project_id,run_id,step_id,receipt_id,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),step.stepId(),receipt,canonical(evidence),hash(evidence),audit,event,json.manifestSha256(),job.actorId(),at,job.classification()));
        proof.requireCurrent(a);changed(db.update("UPDATE run_step SET status='WAITING_INPUT',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND status='READY' AND lease_generation=0",job.tenantId(),job.runId(),step.stepId()));return originalNodeReceipt(a,job.runId(),step.stepId());
    }
    private FormalEvaluationProtocol.NodeReceipt originalWaiting(EnterpriseAccess.Admission a,FormalEvaluationJobs.Job job,FormalEvaluationProtocol.Step step,Map<String,Object> row)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);same(job.projectId(),row.get("project_id"));same(job.actorId(),row.get("created_by"));same(job.classification(),row.get("classification"));same("1.0.0",row.get("schema_version"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));var evidence=parser.readTree(text(row,"canonical_receipt"));same(hash(evidence),row.get("receipt_sha256"));same(job.runId(),text(evidence,"run_id"));same(step.stepId(),text(evidence,"step_id"));same(step.nodeSha256(),text(evidence,"node_sha256"));same("WAITING_INPUT",text(evidence,"state"));if(!evidence.required("missing_requirements").isArray()||evidence.required("missing_requirements").isEmpty())bad();verifyJournal(job,text(row,"audit_id"),text(row,"event_id"),"evaluation.node.waiting",text(row,"receipt_id"),evidence);same("WAITING_INPUT",one(db.query("SELECT status FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=0 LIMIT 2",(r,n)->r.getString(1),job.tenantId(),job.runId(),step.stepId())));return new FormalEvaluationProtocol.NodeReceipt("1.0.0",job.runId(),step.stepId(),step.nodeSha256(),"WAITING_INPUT",text(row,"receipt_id"),text(row,"receipt_sha256"));
    }
    public FormalEvaluationProtocol.NodeReceipt unknownOriginal(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step)throws Exception {
        var job=jobs.byRun(a,reference.runId());launches.requireAccepted(a,job,reference);if(!reference.steps().contains(step))bad();var row=one(db.queryForList("SELECT * FROM formal_evaluation_node_dispatch WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",job.tenantId(),job.runId(),step.stepId()));var evidence=parser.readTree(text(row,"canonical_dispatch"));same(hash(evidence),row.get("dispatch_sha256"));same(step.nodeSha256(),row.get("node_sha256"));same(job.actorId(),row.get("created_by"));same(job.projectId(),row.get("project_id"));verifyJournal(job,text(row,"audit_id"),text(row,"event_id"),"evaluation.node.dispatched",text(row,"command_id"),evidence);return new FormalEvaluationProtocol.NodeReceipt("1.0.0",job.runId(),step.stepId(),step.nodeSha256(),"UNKNOWN",text(row,"command_id"),text(row,"dispatch_sha256"));
    }
    public FormalEvaluationProtocol.NodeReceipt originalNodeReceipt(EnterpriseAccess.Admission a,String run,String step)throws Exception {
        if(!db.queryForList("SELECT step_id FROM formal_evaluation_deterministic_outcome WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1",a.context().tenant_id(),run,step).isEmpty())return deterministic.getObject().originalReceipt(a,run,step);
        var waiting=db.queryForList("SELECT * FROM formal_evaluation_node_waiting WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",a.context().tenant_id(),run,step);if(!waiting.isEmpty()){var job=jobs.original(a,run);var node=one(job.nodes().stream().filter(n->n.stepId().equals(step)).toList());return originalWaiting(a,job,new FormalEvaluationProtocol.Step(step,node.node().nodeKind(),node.nodeSha256()),one(waiting));}
        var original=originalOutcome(a,run,step);var row=original.row();return new FormalEvaluationProtocol.NodeReceipt("1.0.0",run,step,original.node().nodeSha256(),text(row,"state"),text(row,"receipt_id"),text(row,"receipt_sha256"));
    }
    private record Outcome(FormalEvaluationJobs.Job job,FormalEvaluationJobs.NodeRow node,Map<String,Object> row,JsonNode evidence){}
    private Outcome originalOutcome(EnterpriseAccess.Admission a,String run,String step)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);var job=jobs.original(a,run);var node=one(job.nodes().stream().filter(n->n.stepId().equals(step)).toList());
        var row=one(db.queryForList("SELECT * FROM formal_evaluation_node_outcome WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",job.tenantId(),run,step));
        same(job.projectId(),row.get("project_id"));same(job.actorId(),row.get("created_by"));same(job.classification(),row.get("classification"));same("1.0.0",row.get("schema_version"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));
        var evidence=parser.readTree(text(row,"canonical_receipt"));same(hash(evidence),row.get("receipt_sha256"));same(canonical(evidence),row.get("canonical_receipt"));for(String key:List.of("run_id","step_id","receipt_id","session_id","state"))same(row.get(key),text(evidence,key));same(node.nodeSha256(),text(evidence,"node_sha256"));
        verifyJournal(job,text(row,"audit_id"),text(row,"event_id"),"evaluation.node.completed",text(row,"receipt_id"),evidence);
        same(row.get("state"),one(db.query("SELECT status FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",(r,n)->r.getString(1),job.tenantId(),run,step)));
        return new Outcome(job,node,Collections.unmodifiableMap(new LinkedHashMap<>(row)),evidence);
    }
    public Completed requireOriginalCompleted(EnterpriseAccess.Admission a,String run,String step)throws Exception {
        var originalJob=jobs.original(a,run);var originalNode=one(originalJob.nodes().stream().filter(n->n.stepId().equals(step)).toList());
        if(FormalEvaluationExecutionProtocol.deterministic(originalNode.node()))return new Completed(a,deterministic.getObject().requireCompleted(a,run,step));
        var outcome=originalOutcome(a,run,step);same("SUCCEEDED",outcome.row().get("state"));var j=outcome.job();var node=outcome.node();var evidence=outcome.evidence();String session=text(evidence,"session_id");
        var row=one(db.queryForList("SELECT * FROM formal_evaluation_node_result WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",j.tenantId(),run,step));
        same(j.projectId(),row.get("project_id"));same(j.actorId(),row.get("created_by"));same(j.classification(),row.get("classification"));same(session,row.get("session_id"));same("1.0.0",row.get("schema_version"));same(json.manifestSha256(),row.get("schema_manifest_sha256"));
        var record=parser.readValue(text(row,"canonical_record"),FormalEvaluationExecutionProtocol.Record.class);same(hash(json.tree(record)),row.get("record_sha256"));same(canonical(json.tree(record)),row.get("canonical_record"));same(node.node(),record.node());same(run,record.runId());same(step,record.stepId());same(1L,record.leaseGeneration());same(record.leaseGeneration(),((Number)row.get("lease_generation")).longValue());same(record.stepReceiptSha256(),outcome.row().get("receipt_sha256"));same(record.runReceiptSha256(),launches.requireOriginalAccepted(a,j));
        var envelope=runtimeObjects.readOriginal(a,run,step,"STEP_ENVELOPE");var exported=runtimeObjects.readOriginal(a,run,step,"SESSION_EXPORT");var output=runtimeObjects.readOriginal(a,run,step,"BUSINESS_OUTPUT");var result=runtimeObjects.readOriginal(a,run,step,"STEP_RESULT");
        same(record.stepEnvelope(),envelope.artifact());same(record.sessionExport(),exported.artifact());same(record.stepResult(),result.artifact());same(List.of(output.artifact()),record.actualArtifacts());if(!record.checkIds().isEmpty())bad();
        for(var object:List.of(envelope,exported,output,result))same(session,object.sessionId());same(json.tree(record.stepEnvelope()),evidence.required("step_envelope"));same(json.tree(record.stepResult()),evidence.required("step_result"));same(json.tree(record.sessionExport()),evidence.required("session_export"));same(json.tree(output.artifact()),evidence.required("business_output"));same(json.tree(record.invocations()),evidence.required("invocations"));
        var prepared=parser.readTree(envelope.payload());same("EVALUATION_VERIFICATION",text(prepared,"job_type"));same(json.tree(node.node()),prepared.required("node"));same(json.tree(record.inputManifest()),prepared.required("input"));
        inputs.requireOriginal(a,run,new FormalEvaluationProtocol.Step(step,node.node().nodeKind(),node.nodeSha256()),record.inputManifest(),prepared.required("compiler"));
        var binding=json.object().put("tenant_id",j.tenantId()).put("run_id",run).put("step_id",step).put("action_id",text(prepared,"action_id")).put("lease_generation",record.leaseGeneration()).put("input_sha256",record.inputManifest().sha256()).put("step_envelope_sha256",record.stepEnvelope().sha256());
        var closed=supervisor.requireOriginalClosed(a,j,step,session,binding,deployments.require(node.node().nodeKind()));closed.requireSuccessfulExit();same(closed.receiptId(),text(evidence,"closed_receipt_id"));same(closed.sha256(),text(evidence,"closed_sha256"));
        for(var object:List.of(exported,output,result)){same(closed.receiptId(),object.closedReceiptId());same(closed.sha256(),object.closedSha256());}
        same(exported.artifact().sha256(),text(closed.exportPin(),"sha256"));
        same("CLOSED",one(db.query("SELECT admission_state FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND run_id=? AND step_id=? LIMIT 2",(r,n)->r.getString(1),j.tenantId(),session,run,step)));
        var terminal=parser.readTree(result.payload());same("SUCCEEDED",text(terminal,"outcome"));same(binding,terminal.required("binding"));same(session,text(terminal,"session_id"));same(json.tree(output.artifact()),terminal.required("output_artifact"));same(parser.readTree(output.payload()),terminal.required("output"));
        var ordered=db.query("SELECT i.invocation_id FROM formal_evaluation_invocation_context c JOIN invocation i ON i.tenant_id=c.tenant_id AND i.run_id=c.run_id AND i.step_id=c.step_id AND i.action_id=c.action_id WHERE c.tenant_id=? AND c.run_id=? AND c.step_id=? AND c.session_id=? ORDER BY c.ordinal LIMIT 1001",(r,n)->r.getString(1),j.tenantId(),run,step,session);
        same(ordered,record.invocations().stream().map(FormalEvaluationExecutionProtocol.Invocation::invocationId).toList());if(ordered.isEmpty())bad();same(json.tree(ordered),terminal.required("invocation_ids"));
        for(var invocation:record.invocations())invocations.requireOriginal(j.tenantId(),run,step,session,invocation);
        JsonNode actual=invocations.requireOriginalResult(j.tenantId(),run,step,session,record.invocations().get(record.invocations().size()-1));tools.requireCompleteOriginal(a,run,step,session);
        nodeOutcomes.requireOriginalOutcome(a,node.node(),j.projectId(),prepared.required("compiler"),actual,terminal);
        same(output.artifact().artifactId(),outcome.row().get("output_artifact_id"));same(output.artifact().sha256(),outcome.row().get("output_sha256"));
        return new Completed(a,record,session,closed.receiptId(),closed.sha256(),new PermissionProtocol.Basis(output.artifact().artifactId(),output.artifact().sha256()));
    }
    public final class Completed {
        private final FormalEvaluationDeterministicRuntime.Completed deterministicProof;private final EnterpriseAccess.Admission admission;private final FormalEvaluationExecutionProtocol.Record record;private final String session,closed,closedHash;private final PermissionProtocol.Basis output;
        private Completed(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Record r,String session,String closed,String closedHash,PermissionProtocol.Basis output){deterministicProof=null;admission=a;record=r;this.session=session;this.closed=closed;this.closedHash=closedHash;this.output=output;}
        private Completed(EnterpriseAccess.Admission a,FormalEvaluationDeterministicRuntime.Completed proof){deterministicProof=proof;admission=a;record=proof.record();output=proof.output();session=null;closed=null;closedHash=null;}
        public boolean deterministic(){FormalEvaluationExecutionAdmission.current(admission);return deterministicProof!=null;}
        public String deterministicReceiptId(){if(!deterministic())deny();return deterministicProof.receiptId();}
        public String deterministicReceiptSha256(){if(!deterministic())deny();return deterministicProof.receiptSha256();}
        public void requireCurrent()throws Exception{if(deterministicProof!=null){deterministicProof.requireCurrent();return;}var original=requireOriginalCompleted(admission,record.runId(),record.stepId());same(record,original.record);same(session,original.session);same(closed,original.closed);same(closedHash,original.closedHash);same(output,original.output);}
        public FormalEvaluationExecutionProtocol.Record record(){FormalEvaluationExecutionAdmission.current(admission);return record;}public String sessionId(){FormalEvaluationExecutionAdmission.current(admission);return session;}public String closedReceiptId(){FormalEvaluationExecutionAdmission.current(admission);return closed;}public String closedSha256(){FormalEvaluationExecutionAdmission.current(admission);return closedHash;}public PermissionProtocol.Basis output(){FormalEvaluationExecutionAdmission.current(admission);return output;}
    }
    private List<Map<String,Object>> preparedRows(FormalEvaluationNodeLeases.Lease l){return db.queryForList("SELECT * FROM formal_evaluation_runtime_prepare WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",l.job().tenantId(),l.job().runId(),l.stepId(),l.generation());}
    private void journal(FormalEvaluationJobs.Job j,String audit,String event,String action,String target,JsonNode evidence,java.time.LocalDateTime now)throws Exception{changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),audit,j.actorId(),action,canonical(evidence),target,j.runId(),json.manifestSha256(),j.actorId(),now,j.classification()));changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,?,?,?)",j.tenantId(),event,target,action,canonical(evidence),now));}
    private void verifyJournal(FormalEvaluationJobs.Job j,String audit,String event,String action,String target,JsonNode evidence)throws Exception{same(canonical(evidence),one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND request_id=? AND trace_id=? AND result='SUCCESS' AND schema_manifest_sha256=? LIMIT 2",(r,n)->r.getString(1),j.tenantId(),j.projectId(),audit,j.actorId(),action,target,j.runId(),json.manifestSha256())));same(canonical(evidence),one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),j.tenantId(),event,target,action)));}
    private String canonical(JsonNode n)throws Exception{return json.canonical(n);}private String hash(JsonNode n)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(n));}
    private java.time.LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static String role(String node){return switch(node){case "N01"->"Planner";case "N02"->"Researcher";case "N03"->"FieldVerifier";case "N04"->"SectionWriter";case "N05","N07"->"SourceVerifier";case "N06"->"Assembler";default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};}
    private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}private static String text(Map<String,Object> row,String k){if(!(row.get(k) instanceof String))bad();return (String)row.get(k);}
    private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
