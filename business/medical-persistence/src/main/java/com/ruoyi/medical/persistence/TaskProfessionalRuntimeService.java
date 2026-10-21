package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bounded N02 private producer. The compiled envelope is not a host/isolation receipt and
 * cannot start a process. Only the separate authenticated supervisor may admit a real session. */
public final class TaskProfessionalRuntimeService {
    public static final String ENVELOPE_REF="urn:medical:task-research-step:1.0.0#/$defs/StepEnvelope";
    public static final String HOST_ACTION="HOST_CONTROL";
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskNodeInputAuthority inputs;
    private final Deployment deployment;private final ServiceCertificateIdentity identities;private final TaskNodeProducerAuthority producers;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskProfessionalRuntimeService(DataSource data,PermissionSchema json,TaskNodeInputAuthority inputs,Deployment deployment,ServiceCertificateIdentity identities,TaskNodeProducerAuthority producers){this(new JdbcTemplate(data),json,inputs,deployment,identities,producers);}
    TaskProfessionalRuntimeService(JdbcTemplate db,PermissionSchema json,TaskNodeInputAuthority inputs,Deployment deployment,ServiceCertificateIdentity identities,TaskNodeProducerAuthority producers){this.db=db;db.setQueryTimeout(5);this.json=json;this.inputs=inputs;this.deployment=Objects.requireNonNull(deployment);this.identities=Objects.requireNonNull(identities);this.producers=Objects.requireNonNull(producers);}

    private static void bindPrompt(ObjectNode value,TaskProfessionalPromptCompiler.CompiledPrompt prompt){value.put("prompt_sha256",prompt.payloadSha256()).put("compiled_prompt",prompt.launchText());value.set("prompt_provenance",prompt.canonicalPins());}

    /** Configuration is pinned deployment data, NEVER an isolation/session proof. No guessed values. */
    public static final class Deployment {
        private final JsonNode value;private final String digest;
        public Deployment(JsonNode value,String expectedSha256)throws Exception {
            ProfessionalRuntimeObservations.closed(value,Set.of("schema_version","deployment_revision","worker_service_uri","supervisor_endpoint","supervisor_service_uri","supervisor_certificate_sha256","supervisor_trust_revision","runtime_manifest_sha256","host_tool_version","jobs_root","sdk","profile_files","expected_inventory","maximum_object_bytes","token_lifetime_seconds","supervisor_gate_lifetime_seconds","role_name","role_version","profile_artifact"));
            same("1.0.0",text(value,"schema_version"));WireValues.uuid(text(value,"deployment_revision"));WireValues.hash(expectedSha256);
            for(String field:List.of("supervisor_certificate_sha256","runtime_manifest_sha256"))WireValues.hash(text(value,field));WireValues.uuid(text(value,"supervisor_trust_revision"));
            for(String field:List.of("worker_service_uri","supervisor_service_uri")){var uri=java.net.URI.create(text(value,field));if(!uri.isAbsolute()||uri.getRawFragment()!=null||uri.getRawQuery()!=null)bad();}
            if(text(value,"worker_service_uri").equals(text(value,"supervisor_service_uri")))deny();
            var endpoint=java.net.URI.create(text(value,"supervisor_endpoint"));if(!"https".equals(endpoint.getScheme())||endpoint.getHost()==null||endpoint.getRawUserInfo()!=null||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null||!Set.of("","/").contains(endpoint.getRawPath()))bad();
            String root=text(value,"jobs_root");if(!java.nio.file.Path.of(root).isAbsolute()||!java.nio.file.Path.of(root).normalize().toString().equals(root))bad();
            if(text(value,"host_tool_version").isBlank())bad();
            bounded(value,"maximum_object_bytes",1,67108864);bounded(value,"token_lifetime_seconds",1,300);bounded(value,"supervisor_gate_lifetime_seconds",1,5);
            if(!Set.of("Researcher","NetworkResearcher").contains(text(value,"role_name")))bad();
            var role=value.required("role_version");ProfessionalRuntimeObservations.closed(role,Set.of("version_id","content_sha256"));WireValues.uuid(text(role,"version_id"));WireValues.hash(text(role,"content_sha256"));
            var artifact=value.required("profile_artifact");ProfessionalRuntimeObservations.closed(artifact,Set.of("artifact_id","sha256","size_bytes","media_type"));WireValues.uuid(text(artifact,"artifact_id"));WireValues.hash(text(artifact,"sha256"));if(!text(artifact,"size_bytes").matches("[1-9][0-9]{0,7}")||Long.parseLong(text(artifact,"size_bytes"))>67108864||text(artifact,"media_type").isBlank())bad();
            var sdk=value.required("sdk");ProfessionalRuntimeObservations.closed(sdk,Set.of("model","reasoning_effort","max_tokens","initialize_timeout_seconds","request_timeout_seconds","shutdown_timeout_seconds","dsh_bin","sdk_version","runtime_version"));
            if(text(sdk,"model").isBlank()||"SIMULATION".equals(text(sdk,"model"))||"0.0.0.dev0".equals(text(sdk,"sdk_version"))||!text(sdk,"sdk_version").equals(text(sdk,"runtime_version"))||!java.nio.file.Path.of(text(sdk,"dsh_bin")).isAbsolute())bad();
            for(String f:List.of("max_tokens","initialize_timeout_seconds","request_timeout_seconds","shutdown_timeout_seconds"))bounded(sdk,f,1,1000000);
            var profiles=value.required("profile_files");if(!profiles.isArray()||profiles.isEmpty()||profiles.size()>256)bad();var paths=new HashSet<String>();
            for(var file:profiles){ProfessionalRuntimeObservations.closed(file,Set.of("source","relative","sha256"));WireValues.hash(text(file,"sha256"));String relative=TaskProfessionalProfilePath.require(text(file,"relative"));if(!java.nio.file.Path.of(text(file,"source")).isAbsolute()||!paths.add(relative))bad();}
            var inventory=value.required("expected_inventory");ProfessionalRuntimeObservations.closed(inventory,Set.of("plugin_names","tool_schema_hashes","providers","models","model_stream_schema_ref"));
            if(!inventory.required("plugin_names").isArray()||inventory.required("plugin_names").isEmpty()||!inventory.required("tool_schema_hashes").isObject()||inventory.required("tool_schema_hashes").isEmpty()||!inventory.required("models").isArray()||inventory.required("models").isEmpty())bad();
            var provider=inventory.required("providers");if(!provider.isArray()||provider.size()!=1||!"enterprise-model".equals(text(provider.get(0),"id"))||!"企业模型接口".equals(text(provider.get(0),"name")))bad();
            digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));same(expectedSha256,digest);this.value=value.deepCopy();
        }
        public JsonNode value(){return value.deepCopy();}public String sha256(){return digest;}
        public String revision(){return text(value,"deployment_revision");}public String workerService(){return text(value,"worker_service_uri");}
        public int maximumBytes(){return value.required("maximum_object_bytes").intValue();}
        private static void bounded(JsonNode n,String field,long min,long max){var value=n.required(field);if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<min||value.longValue()>max)bad();}
    }

    public Envelope compileEnvelope(EnterpriseAccess.Admission admission,TaskNodeInputAuthority.Verified input)throws Exception {
        return compileEnvelope(admission,input,null);
    }
    public Envelope compileEnvelope(EnterpriseAccess.Admission admission,TaskNodeInputAuthority.Verified input,TaskProfessionalPromptCompiler.CompiledPrompt prompt)throws Exception {
        current(admission);input.requireUnchanged();var i=admission.formalProof().identity();
        String session;
        var existing=db.queryForList("SELECT canonical_payload FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND write_kind='STEP_ENVELOPE' AND schema_version='2.0.0' AND schema_ref=? AND canonical_payload->>'$.lease_generation'=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),ENVELOPE_REF,i.generation());
        if(existing.size()>1)bad();session=existing.isEmpty()?UUID.randomUUID().toString():text(parse(str(existing.get(0),"canonical_payload")),"session_id");
        var value=compile(admission,input,session);if(prompt!=null){prompt.requireCurrent(admission);bindPrompt((ObjectNode)value,prompt);}return new Envelope(admission,input,session,value,hash(value),prompt);
    }
    private JsonNode compile(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,String session)throws Exception {
        current(a);input.requireUnchanged();var i=a.formalProof().identity();WireValues.uuid(session);
        if(!"TASK".equals(i.executionKind())||!deployment.workerService().equals(i.workerService())||!input.runId().equals(i.run())||!input.stepId().equals(i.step()))deny();
        String arguments=hostArguments(input);var action=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND kind='HOST_CONTROL' AND arguments_sha256=? AND tool_version=? AND state='BOUND' AND invocation_id IS NULL LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),i.action(),input.inputSha256(),i.generation(),arguments,text(deployment.value,"host_tool_version")));same(i.action(),str(action,"action_id"));
        var run=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND task_id=? AND created_by=? AND execution_kind='TASK' AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL AND superseded_by_run_id IS NULL LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),input.taskId(),i.actor()));var replay=parse(str(run,"replay_manifest"));
        same(text(deployment.value,"runtime_manifest_sha256"),hash(replay.required("agent_release").required("runtime_manifest")));
        var policy=replay.required("execution_policy");var nodes=new ArrayList<JsonNode>();for(var node:policy.required("bindings"))if("N02".equals(text(node,"node_kind")))nodes.add(node);var node=one(nodes);
        same(input.manifest().required("role_version"),node.required("role_version"));same(deployment.value.required("role_version"),node.required("role_version"));same(deployment.value.required("profile_artifact"),replay.required("agent_release").required("runtime_manifest").required("profile").required("artifact"));same(input.manifest().required("execution_policy_version"),replay.required("execution_policy_version"));
        var value=json.object().put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N02").put("tenant_id",i.tenant()).put("project_id",i.project()).put("task_id",input.taskId()).put("run_id",i.run()).put("step_id",i.step()).put("action_id",i.action()).put("lease_generation",Long.parseLong(i.generation())).put("session_id",session).put("question_id",input.questionId()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256()).put("runtime_manifest_sha256",hash(replay.required("agent_release").required("runtime_manifest"))).put("agent_release_id",text(replay,"agent_release_id")).put("publication_approval_sha256",text(replay,"publication_approval_sha256")).put("deployment_revision",deployment.revision()).put("deployment_sha256",deployment.sha256()).put("worker_service_uri",i.workerService());
        value.set("role_version",node.required("role_version"));value.set("execution_policy_version",replay.required("execution_policy_version"));value.set("action_scope",json.tree(i.delegatedActions()));
        if(ContentReadAuthorization.canonicalBytes(value).length>deployment.maximumBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return value;
    }
    public static String hostArguments(TaskNodeInputAuthority.Verified input)throws Exception {
        input.requireUnchanged();return WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("operation","N02_HOST_CONTROL","run_id",input.runId(),"step_id",input.stepId(),"input_artifact_id",input.inputId(),"input_sha256",input.inputSha256(),"input_receipt_sha256",input.receiptSha256())));
    }
    public final class Envelope {
        private final EnterpriseAccess.Admission admission;private final TaskNodeInputAuthority.Verified input;private final FormalWorkerAccess.Identity identity;private final String session,digest;private final JsonNode payload;private final TaskProfessionalPromptCompiler.CompiledPrompt prompt;
        private Envelope(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,String session,JsonNode payload,String digest,TaskProfessionalPromptCompiler.CompiledPrompt prompt){this.prompt=prompt;admission=a;this.input=input;identity=a.formalProof().identity();this.session=session;this.payload=payload.deepCopy();this.digest=digest;}
        public void requireUnchanged()throws Exception {current(admission);var fresh=compile(admission,input,session);if(prompt!=null){prompt.requireCurrent(admission);bindPrompt((ObjectNode)fresh,prompt);}if(!identity.equals(admission.formalProof().identity())||!FormalTaskNodeAuthority.sameEvidence(payload,fresh))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public void requireProductionReady()throws Exception{requireUnchanged();if(prompt==null)deny();prompt.requireCurrent(admission);}
        private void active(){current(admission);if(!identity.equals(admission.formalProof().identity()))deny();}
        public byte[] bytes()throws Exception{requireUnchanged();return ContentReadAuthorization.canonicalBytes(payload);}public String payloadSha256(){active();return digest;}
        public String runId(){active();return identity.run();}public String stepId(){active();return identity.step();}public String actionId(){active();return identity.action();}
        public String leaseGeneration(){active();return identity.generation();}public String workerService(){active();return identity.workerService();}
        public String inputId(){active();return input.inputId();}public String inputSha256(){active();return input.inputSha256();}public String sessionId(){active();return session;}
        EnterpriseAccess.Admission admission(){active();return admission;}TaskNodeInputAuthority.Verified input(){active();return input;}
        JsonNode payload(){active();return payload.deepCopy();}Deployment deployment(){active();return deployment;}
    }
    /** Prepared bytes/configuration only. This is not admission and does not release the SDK gate. */
    public JsonNode prepareLaunch(Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,TaskProfessionalHostAccess.InputContent consumed,TaskProfessionalPromptCompiler.CompiledPrompt prompt)throws Exception {
        envelope.requireProductionReady();registered.requireUnchanged();consumed.requireFor(envelope);prompt.requireFor(envelope);same(prompt.payloadSha256(),text(envelope.payload(),"prompt_sha256"));same(prompt.canonicalPins(),envelope.payload().required("prompt_provenance"));
        if(!"STEP_ENVELOPE".equals(registered.kind())||!envelope.sessionId().equals(registered.sessionId())||!envelope.payloadSha256().equals(registered.payloadSha256())||!envelope.actionId().equals(registered.actionId())||!envelope.inputId().equals(registered.inputId()))bad();
        var authority=producers.requireLeased(envelope.admission(),envelope.input());authority.requireHostEnvelopeConsume(envelope,registered);
        String root=text(deployment.value,"jobs_root"),job=root+"/"+envelope.input().tenantId()+"/"+envelope.runId()+"/"+envelope.stepId()+"/"+envelope.leaseGeneration();
        var b=json.object().put("tenant_id",envelope.input().tenantId()).put("run_id",envelope.runId()).put("step_id",envelope.stepId()).put("action_id",envelope.actionId()).put("lease_generation",Long.parseLong(envelope.leaseGeneration())).put("input_sha256",envelope.inputSha256()).put("step_envelope_sha256",envelope.payloadSha256());
        var value=json.object().put("state","PREPARE").put("purpose","TASK").put("session_id",envelope.sessionId()).put("node_kind","N02").put("role",text(deployment.value,"role_name")).put("runtime_manifest_sha256",text(deployment.value,"runtime_manifest_sha256")).put("deployment_sha256",deployment.sha256()).put("job_directory",job).put("prompt",prompt.launchText()).put("prompt_sha256",prompt.payloadSha256());
        value.set("binding",b);value.set("prompt_provenance",prompt.canonicalPins());for(String field:List.of("sdk","profile_files","expected_inventory","role_version","profile_artifact"))value.set(field,deployment.value.required(field).deepCopy());
        envelope.requireUnchanged();authority.requireHostEnvelopeConsume(envelope,registered);return value;
    }
    /** Only the authenticated host plus independently inspected actual SDK may create083. */
    public Session registerSession(jakarta.servlet.http.HttpServletRequest http,Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,TaskProfessionalHostAccess.InputContent consumed,TaskProfessionalPromptCompiler.CompiledPrompt prompt,TaskProfessionalSupervisor.Admitted admitted)throws Exception {
        var start=(ObjectNode)prepareLaunch(envelope,registered,consumed,prompt);admitted.requireUnchanged();
        if(!admitted.sessionId().equals(envelope.sessionId())||!FormalTaskNodeAuthority.sameEvidence(admitted.binding(),start.required("binding"))||!admitted.deploymentSha256().equals(deployment.sha256()))bad();
        var service=identities.require(http);if(!envelope.workerService().equals(service.serviceUri()))deny();
        start.put("state","START").put("isolation_receipt_id",admitted.receiptId()).put("supervision_sha256",admitted.sha256());
        var a=envelope.admission();var i=a.formalProof().identity();String canonical=json.canonical(start),digest=hash(start);
        var previous=db.queryForList("SELECT session_id,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",i.tenant(),i.run(),i.step(),i.generation());
        if(!previous.isEmpty()){var saved=one(previous);same(envelope.sessionId(),str(saved,"session_id"));same(digest,str(saved,"start_sha256"));return requireSession(a,envelope.input(),envelope.sessionId());}
        var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));String classification=registered.receipt().required("classification").textValue();
        changed(db.update("INSERT INTO professional_runtime_session (tenant_id,project_id,run_id,step_id,lease_generation,session_id,action_id,input_sha256,envelope_sha256,service_uri,certificate_sha256,trust_revision,canonical_start,start_sha256,admission_state,revision,schema_version,schema_manifest_sha256,created_by,created_at,classification,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN',1,'1.0.0',?,?,?,?,?)",i.tenant(),i.project(),i.run(),i.step(),i.generation(),envelope.sessionId(),i.action(),envelope.inputSha256(),envelope.payloadSha256(),service.serviceUri(),service.certificateSha256(),service.trustRevision(),canonical,digest,json.manifestSha256(),i.actor(),now,classification,now));
        changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'task.professional.session.opened',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",i.tenant(),i.project(),UUID.randomUUID().toString(),i.actor(),canonical,envelope.sessionId(),i.run(),json.manifestSha256(),i.actor(),now,classification));
        changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'task.professional.session.opened',?,?)",i.tenant(),envelope.sessionId(),i.run(),canonical,now));
        admitted.requireUnchanged();var after=identities.require(http);same(service.serviceUri(),after.serviceUri());same(service.certificateSha256(),after.certificateSha256());same(service.trustRevision(),after.trustRevision());return requireSession(a,envelope.input(),envelope.sessionId());
    }
    public JsonNode heartbeat(Session session,TaskProfessionalSupervisor.Admitted admitted)throws Exception {
        session.requireUnchanged();admitted.requireUnchanged();if(!"OPEN".equals(session.state())||!session.sessionId().equals(admitted.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),admitted.binding())||!session.launch().required("isolation_receipt_id").textValue().equals(admitted.receiptId())||!session.launch().required("supervision_sha256").textValue().equals(admitted.sha256()))deny();
        var reply=json.object().put("session_id",session.sessionId()).put("admission","CURRENT");reply.set("binding",session.binding());return reply;
    }
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}

    /** Private original083 session proof. Construction does not create a session or attest isolation. */
    public Session requireSession(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,String session)throws Exception {
        return requireSession(a,input,session,null,false,null);
    }
    /** Current MODEL_CALL uses its own token and immutable context/action link, never the host action's token. */
    public Session requireCurrentSession(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode originalBinding,String session)throws Exception {
        return requireSession(a,input,session,originalBinding,true,null);
    }
    /** Iterative MODEL_CALL verification pins the exact persisted context, not any context in
     * the same session. The original overload intentionally retains its initial-call behavior. */
    public Session requireCurrentSession(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode originalBinding,String session,String contextId)throws Exception {
        WireValues.uuid(contextId);if(originalBinding==null)deny();
        return requireSession(a,input,session,originalBinding,true,contextId);
    }
    private Session requireSession(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,String session,JsonNode originalBinding,boolean model,String contextId)throws Exception {
        current(a);input.requireUnchanged();var i=a.formalProof().identity();String contextFingerprint=null;
        if(contextId!=null){
            if(!"TASK".equals(i.executionKind()))deny();
            same(i.tenant(),input.tenantId());same(i.project(),input.projectId());same(i.run(),input.runId());same(i.step(),input.stepId());
        }
        var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND input_sha256=? AND service_uri=? AND created_by=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),session,i.generation(),input.inputSha256(),i.workerService(),i.actor()));
        var start=parse(str(row,"canonical_start"));same(hash(start),str(row,"start_sha256"));same(session,text(start,"session_id"));same("TASK",text(start,"purpose"));same("N02",text(start,"node_kind"));same("START",text(start,"state"));same(deployment.sha256(),text(start,"deployment_sha256"));same(text(deployment.value,"runtime_manifest_sha256"),text(start,"runtime_manifest_sha256"));
        var b=ProfessionalRuntimeObservations.jsonBinding(start.required("binding"));same(i.run(),b.run_id());same(i.step(),b.step_id());same(str(row,"action_id"),b.action_id());same(input.inputSha256(),b.input_sha256());same(str(row,"envelope_sha256"),b.step_envelope_sha256());if(b.lease_generation()!=Long.parseLong(i.generation()))bad();
        if(originalBinding!=null)same(originalBinding,start.required("binding"));
        if(contextId!=null)same(i.tenant(),b.tenant_id());
        one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND kind='HOST_CONTROL' AND arguments_sha256=? AND tool_version=? AND invocation_id IS NULL LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),b.action_id(),input.inputSha256(),i.generation(),hostArguments(input),text(deployment.value,"host_tool_version")));
        if(!i.action().equals(b.action_id())){
            if(!model||originalBinding==null)deny();
            var parameters=new ArrayList<Object>(List.of(i.tenant(),i.project()));
            if(contextId!=null)parameters.add(contextId);
            parameters.addAll(List.of(session,i.run(),i.step(),i.action(),i.generation(),input.inputSha256()));
            var context=one(db.queryForList("SELECT c.context_manifest_id,c.source_manifest,c.content_sha256,a.arguments_sha256,a.tool_version,v.content_sha256 registered_sha256 FROM context_manifest c JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.context_manifest_id AND v.owner_project_id=c.project_id JOIN step_action_binding a ON a.tenant_id=c.tenant_id AND a.project_id=c.project_id AND a.run_id=c.source_manifest->>'$.request.node.run_id' AND a.step_id=c.source_manifest->>'$.request.node.step_id' WHERE c.tenant_id=? AND c.project_id=?"+(contextId==null?"":" AND c.context_manifest_id=?")+" AND c.execution_mode='EXTERNAL' AND c.source_manifest->>'$.execution_kind'='TASK' AND c.source_manifest->>'$.request.node.session_id'=? AND a.run_id=? AND a.step_id=? AND a.action_id=? AND a.kind='MODEL_CALL' AND a.lease_generation=? AND a.input_sha256=? LIMIT 2 FOR UPDATE",parameters.toArray()));
            if(contextId!=null){same(contextId,str(context,"context_manifest_id"));contextFingerprint=hash(AgentPublicationWitness.tree(context));}
            var source=parse(str(context,"source_manifest"));var request=source.required("request");var node=request.required("node");same(hash(request),str(context,"arguments_sha256"));same(str(context,"content_sha256"),str(context,"registered_sha256"));same(text(request,"model_config_revision"),str(context,"tool_version"));same(UUID.nameUUIDFromBytes(("TASK_C07_MODEL_CALL:"+str(context,"context_manifest_id")).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),i.action());
            for(var pin:Map.of("tenant_id",i.tenant(),"project_id",i.project(),"run_id",i.run(),"step_id",i.step(),"host_action_id",b.action_id(),"session_id",session,"worker_service_uri",i.workerService(),"node_input_artifact_id",input.inputId(),"node_input_sha256",input.inputSha256(),"node_input_receipt_sha256",input.receiptSha256()).entrySet())same(pin.getValue(),text(node,pin.getKey()));same(i.generation(),node.required("lease_generation").asText());same(i.principal(),text(node,"principal_id"));same(text(start,"isolation_receipt_id"),text(node,"supervisor_receipt_id"));same(text(start,"supervision_sha256"),text(node,"supervisor_receipt_sha256"));
        }
        if(contextId!=null&&contextFingerprint==null)deny(); // A host action is not a context-bound MODEL_CALL.
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='task.professional.session.opened' AND result='SUCCESS' AND request_id=? AND trace_id=? LIMIT 2",i.tenant(),i.project(),i.actor(),i.actor(),session,i.run()));same(start,parse(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.professional.session.opened' LIMIT 2",i.tenant(),session,i.run()));same(start,parse(str(event,"payload")));
        String fingerprint=hash(contextFingerprint==null?AgentPublicationWitness.tree(row):Map.of("session",AgentPublicationWitness.tree(row),"context_sha256",contextFingerprint));
        return new Session(a,input,session,row,start,fingerprint,model,contextId);
    }
    public final class Session {
        private final EnterpriseAccess.Admission admission;private final TaskNodeInputAuthority.Verified input;private final FormalWorkerAccess.Identity identity;private final String id,fingerprint,contextId;private final boolean model;private final Map<String,Object> row;private final JsonNode launch;
        private Session(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,String id,Map<String,Object> row,JsonNode launch,String fingerprint,boolean model,String contextId){this.contextId=contextId;this.model=model;admission=a;this.input=input;identity=a.formalProof().identity();this.id=id;this.row=new LinkedHashMap<>(row);this.launch=launch.deepCopy();this.fingerprint=fingerprint;}
        public void requireUnchanged()throws Exception {current(admission);if(!identity.equals(admission.formalProof().identity())||!fingerprint.equals(requireSession(admission,input,id,model?launch.required("binding"):null,model,contextId).fingerprint))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public String sessionId(){current(admission);return id;}public String runId(){current(admission);return identity.run();}public String stepId(){current(admission);return identity.step();}public String actionId(){current(admission);return text(launch.required("binding"),"action_id");}
        public String generation(){current(admission);return identity.generation();}public String workerService(){current(admission);return identity.workerService();}
        public JsonNode binding(){current(admission);return launch.required("binding").deepCopy();}public JsonNode launch(){current(admission);return launch.deepCopy();}
        TaskNodeInputAuthority.Verified input(){current(admission);return input;}EnterpriseAccess.Admission admission(){current(admission);return admission;}
        String state(){current(admission);return str(row,"admission_state");}Deployment deployment(){current(admission);return deployment;}
    }
    static void current(EnterpriseAccess.Admission a){if(!a.formalWorker()||!TransactionSynchronizationManager.isActualTransactionActive())deny();a.formalProof().requireActive();}
    private JsonNode parse(String value)throws Exception{return reader.readTree(value);}private String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static String str(Map<String,Object> n,String key){if(!(n.get(key) instanceof String))bad();return (String)n.get(key);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return rows.get(0);}
    private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
