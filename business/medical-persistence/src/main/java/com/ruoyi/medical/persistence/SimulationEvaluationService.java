package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import static com.ruoyi.medical.persistence.SimulationEvaluationStore.row;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Genuine restricted evaluation producer. No Temporal start, model execution or result settlement is implied. */
public final class SimulationEvaluationService {
    private final EnterpriseAccess access;private final SimulationWorkerAccess workers;private final SimulationEvaluationStore store;
    private final SimulationInvocationStore ledger;private final SimulationInvocationSchema schema;private final SimulationEvaluationSchema api;
    private final org.springframework.beans.factory.ObjectProvider<SimulationInputArtifactService> inputArtifacts;
    private final SimulationContextProducer contexts;private final SimulationEvaluationCandidateAuthority candidates;
    private final EvaluationService evaluationDefinitions;
    private final EvaluationMapper evaluations;private final EvaluationSchema evaluationSchema;private final RunTokenAuthoritySchema manifests;
    private final SimulationExecutionPermissionPolicy execute;private final SimulationQuotaPermissionPolicy quota;
    private final SimulationRetentionPermissionPolicy retention;private final SimulationEvaluationPolicyService policies;
    private final SimulationEvaluationTokenIssuer issuer;private final ServiceCertificateIdentity services;private final ServiceCertificateTrustStore trust;
    SimulationEvaluationService(EnterpriseAccess access,SimulationWorkerAccess workers,SimulationEvaluationStore store,SimulationInvocationStore ledger,
        SimulationInvocationSchema schema,SimulationEvaluationSchema api,SimulationContextProducer contexts,SimulationEvaluationCandidateAuthority candidates,
        EvaluationMapper evaluations,EvaluationSchema evaluationSchema,RunTokenAuthoritySchema manifests,SimulationExecutionPermissionPolicy execute,
        SimulationQuotaPermissionPolicy quota,SimulationRetentionPermissionPolicy retention,SimulationEvaluationPolicyService policies,
        SimulationEvaluationTokenIssuer issuer,ServiceCertificateIdentity services,ServiceCertificateTrustStore trust,
        org.springframework.beans.factory.ObjectProvider<SimulationInputArtifactService> inputArtifacts,EvaluationService evaluationDefinitions){
        this.access=access;this.workers=workers;this.store=store;this.ledger=ledger;this.schema=schema;this.api=api;this.contexts=contexts;
        this.candidates=candidates;this.evaluations=evaluations;this.evaluationSchema=evaluationSchema;this.manifests=manifests;this.execute=execute;
        this.quota=quota;this.retention=retention;this.policies=policies;this.issuer=issuer;this.services=services;this.trust=trust;this.inputArtifacts=inputArtifacts;this.evaluationDefinitions=evaluationDefinitions;
    }
    public JsonNode start(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        JsonNode request=api.read("Start",bytes);schema.validate("RequestParameters",request.required("request_parameters"));
        var prepared=contexts.prepare(subject,request);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            SimulationEvaluationPolicyService.current(a,context);contexts.require(a,prepared);
            var definition=definition(a,request,prepared);var grant=execute.require(a,nullable(request,"project_id"),text(request,"evaluation_id"));
            approved(a,request);registeredService(text(request,"service_uri"));
            var previous=store.cell(request);
            if(!previous.isEmpty()){
                var saved=single(previous);sameStart(a,saved,request,prepared);candidate(a,saved,request);return receipt(saved);
            }
            String run=id(),principal=id(),step=id(),action=id(),contextId=id(),invocation=id(),parent=id(),scopeId=id();
            String project=nullable(request,"project_id"),actor=a.actorId(),tenant=a.context().tenant_id();
            var storedContext=contexts.persist(a,prepared,contextId,text(request,"prompt_revision"),text(request,"agent_version_id"));
            String classification=classification(definition.classification(),storedContext.classification(),str(ledger.config(text(request,"model_config_revision")),"classification"));
            var identity=object();identity.put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("step_id",step)
                .put("action_id",action).put("invocation_id",invocation).put("context_manifest_id",contextId).put("model_config_revision",text(request,"model_config_revision"));
            var scope=object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run).put("execution_kind","SYSTEM_JOB");
            var resource=object().put("tenant_id",tenant).put("project_id",project).put("resource_kind","EVALUATION").put("resource_id",text(request,"evaluation_id"));scope.putArray("resources").add(resource);
            manifests.scopeManifest(schema.canonical(scope),tenant,project,actor,run,"SYSTEM_JOB");
            store.insert("version_registry",row("tenant_id",tenant,"version_id",scopeId,"owner_project_id",project,"entity_kind","SCOPE_MANIFEST","content_sha256",schema.hash(scope)));
            var scopeRow=base(a,project,classification,manifests.manifestSha256());scopeRow.putAll(row("scope_manifest_id",scopeId,"manifest",schema.canonical(scope),"content_sha256",schema.hash(scope)));store.insert("simulation_scope_manifest",scopeRow);
            var registry=base(a,project,classification,manifests.manifestSha256());registry.putAll(row("run_id",run,"execution_kind","SYSTEM_JOB","scope_manifest",schema.canonical(scope)));store.insert("execution_registry",registry);
            var job=base(a,project,classification,manifests.manifestSha256());job.putAll(row("run_id",run,"execution_kind","SYSTEM_JOB","job_type","SIMULATION_EVALUATION","initiator_id",actor,
                "input_manifest",schema.canonical(request),"config_revision",text(request,"model_config_revision"),"target_kind","EVALUATION","target_id",text(request,"evaluation_id"),
                "target_revision",text(request,"evaluation_id"),"input_sha256",prepared.registration().inputSha256(),"scope_manifest_id",scopeId,"rule_manifest_id",evaluationSchema.definition(definition.definition()).request().rule_revision().version_id(),
                "status","QUEUED","workflow_id",null,"revision",1,"updated_at",store.now()));store.insert("system_job_run",job);
            var principalManifest=object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run).put("principal_id",principal).put("scope_sha256",schema.hash(scope));
            principalManifest.putArray("services").add(object().put("service_uri",text(request,"service_uri")).put("audience",SimulationRunTokenVerifier.AUDIENCE));
            var permission=object().put("action",SimulationExecutionPermissionPolicy.ACTION).put("purpose",SimulationExecutionPermissionPolicy.PURPOSE);permission.set("scope",resource);principalManifest.putArray("permissions").add(permission);
            manifests.principalManifest(schema.canonical(principalManifest),tenant,project,actor,run,principal,scope);
            var principalRow=base(a,project,classification,manifests.manifestSha256());principalRow.putAll(row("principal_id",principal,"initiator_id",actor,"run_id",run,"capability_manifest",schema.canonical(principalManifest),"status","ACTIVE","revision",1,"updated_at",store.now()));store.insert("run_principal",principalRow);
            var stepRow=base(a,project,classification,schema.manifest());stepRow.putAll(row("run_id",run,"step_id",step,"role_version",text(request,"role_version"),"input_sha256",prepared.registration().inputSha256(),"status","READY","lease_generation",0,"revision",1,"updated_at",store.now()));store.insert("run_step",stepRow);
            var actionRow=base(a,project,classification,schema.manifest());actionRow.putAll(row("run_id",run,"step_id",step,"action_id",action,"origin_run_id",run,"origin_step_id",step,"origin_action_id",action,
                "input_sha256",prepared.registration().inputSha256(),"kind","MODEL","arguments_sha256",prepared.registration().parametersSha256(),"tool_version",text(request,"model_config_revision"),"state","BOUND","lease_generation",0,"revision",1,"updated_at",store.now()));store.insert("step_action_binding",actionRow);
            var parentGrant=quota.requireEvaluation(a,project,text(request,"evaluation_id"));store.insert("simulation_quota_account",policies.account(a,parent,project,run,request.path("parent_maximum_units").longValue(),request.path("maximum_calls").longValue(),parentGrant,classification));
            var fixed=base(a,project,classification,api.manifest());fixed.putAll(row("job_id",run,"run_id",run,"principal_id",principal,"step_id",step,"action_id",action,"context_manifest_id",contextId,"invocation_id",invocation,"scope_manifest_id",scopeId,"parent_account_id",parent,
                "definition_sha256",text(request,"evaluation_definition_sha256"),"service_uri",text(request,"service_uri"),"grant_id",grant.grantId(),"grant_revision",grant.revision(),"repeat_index",request.path("repeat_index").intValue(),"status","READY","revision",1,"updated_at",store.now(),
                "start_request",schema.canonical(request),"start_request_sha256",schema.hash(request),"source_binding",schema.canonical(sourceBinding(prepared)),"source_binding_sha256",schema.hash(sourceBinding(prepared)),"prepared_payload",schema.canonical(payload(request,prepared)),"prepared_payload_sha256",schema.hash(payload(request,prepared))));
            for(String field:List.of("evaluation_id","sample_id","agent_version_id","role_version","model_config_revision","prompt_revision","case_id","enterprise_account_id","retention_policy_id"))fixed.put(field,text(request,field));
            store.insert("simulation_evaluation_job",fixed);candidate(a,fixed,request);contexts.require(a,prepared);approved(a,request);
            store.event(project,actor,run,"simulation.evaluation.created",schema.canonical(receipt(fixed)),api.manifest(),classification);return receipt(store.job(run));
        }).get());
    }
    public JsonNode claim(HttpServletRequest http,byte[] bytes)throws Exception {
        var input=api.read("Claim",bytes);String tenant=text(input,"tenant_id"),run=text(input,"run_id");
        JsonNode start=workers.execute(http,tenant,run,a->ThrowingSupplier.of(()->schema.parse(str(store.job(run),"start_request"))).get());
        var prepared=contexts.prepare(http,tenant,run,start);
        return workers.execute(http,tenant,run,a->ThrowingSupplier.of(()->{
            var job=store.job(run);sameStart(a,job,start,prepared);candidate(a,job,start);definition(a,start,prepared);approved(a,start);
            if(!"READY".equals(str(job,"status"))||!input.path("expected_revision").asText().equals(str(job,"revision")))throw new TenantAccessException(ErrorCode.LEASE_STALE);
            Duration lifetime=Duration.ofSeconds(input.path("lease_seconds").longValue());if(lifetime.compareTo(issuer.maximumLifetime())>0)denied();
            var expires=store.now().plus(lifetime);store.claim(job,expires);contexts.require(a,prepared);approved(a,start);
            store.event(nullable(start,"project_id"),a.actorId(),run,"simulation.evaluation.claimed",schema.canonical(receipt(store.job(run))),api.manifest(),str(job,"classification"));
            var result=receipt(store.job(run));result.put("lease_generation","1").put("lease_expires_at",instant(expires));return result;
        }).get());
    }
    public JsonNode status(HttpServletRequest http,byte[] bytes)throws Exception {
        var input=api.read("Status",bytes);return workers.execute(http,text(input,"tenant_id"),text(input,"run_id"),a->{var job=store.job(text(input,"run_id"));if(!a.actorId().equals(str(job,"created_by")))denied();return receipt(job);});
    }
    public Issued issue(HttpServletRequest http,byte[] bytes)throws Exception {
        var input=api.read("Issue",bytes);String tenant=text(input,"tenant_id"),run=text(input,"run_id");
        JsonNode start=workers.execute(http,tenant,run,a->ThrowingSupplier.of(()->schema.parse(str(store.job(run),"start_request"))).get());
        var prepared=contexts.prepare(http,tenant,run,start);var service=services.require(http);
        return workers.execute(http,tenant,run,a->ThrowingSupplier.of(()->{
            var job=store.job(run);sameStart(a,job,start,prepared);candidate(a,job,start);definition(a,start,prepared);approved(a,start);
            if(!"CLAIMED".equals(str(job,"status"))||!input.path("expected_revision").asText().equals(str(job,"revision"))||!"1".equals(input.path("lease_generation").asText()))throw new TenantAccessException(ErrorCode.LEASE_STALE);
            var identity=identity(job);var step=ledger.step(identity);var action=ledger.action(identity);
            if(!"RUNNING".equals(str(step,"status"))||count(step,"lease_generation")!=1||!service.serviceUri().equals(str(step,"lease_owner"))
                ||!"BOUND".equals(str(action,"state"))||count(action,"lease_generation")!=1||action.get("invocation_id")!=null)throw new TenantAccessException(ErrorCode.LEASE_STALE);
            var expires=SimulationInvocationAuthority.time(step,"lease_expires_at");if(!store.now().isBefore(expires))throw new TenantAccessException(ErrorCode.LEASE_STALE);
            var token=issuer.issue(tenant,nullable(start,"project_id"),a.actorId(),run,str(job,"step_id"),id(),a.watermarks().authEpoch(),store.now(),expires,Duration.ofSeconds(input.path("lifetime_seconds").longValue()));
            var request=admission(a,job,start,prepared,token,expires);var proof=request.required("authority_proof");schema.request(schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var tokenRow=base(a,nullable(start,"project_id"),str(job,"classification"),schema.manifest());tokenRow.putAll(row("jti",token.jti(),"principal_id",str(job,"principal_id"),"run_id",run,"step_id",str(job,"step_id"),
                "audience",SimulationRunTokenVerifier.AUDIENCE,"action_scope","[\"model.simulation.execute\"]","auth_epoch",a.watermarks().authEpoch(),"issued_at",token.issuedAt(),"expires_at",token.expiresAt(),"token_sha256",token.tokenSha256(),"service_uri",service.serviceUri(),
                "policy_revision",token.policy().revision(),"policy_sha256",token.policy().contentSha256(),"lease_generation",1,"revision",1,"updated_at",store.now()));store.insert("principal_token",tokenRow);
            var authority=base(a,nullable(start,"project_id"),str(job,"classification"),schema.manifest());authority.put("created_at",token.issuedAt());authority.putAll(row("authority_id",text(proof,"authority_id"),"authority_revision",1,"run_id",run,"step_id",str(job,"step_id"),"action_id",str(job,"action_id"),
                "evaluation_id",text(start,"evaluation_id"),"evaluation_run_id",run,"principal_id",str(job,"principal_id"),"grant_id",text(proof,"grant_id"),"grant_revision",proof.path("grant_revision").longValue(),"service_uri",service.serviceUri(),"lease_generation",1,
                "proof",schema.canonical(proof),"proof_sha256",schema.hash(proof),"status","ACTIVE","expires_at",token.expiresAt(),"retention_policy_id",text(start,"retention_policy_id"),"token_jti",token.jti()));store.insert("simulation_execution_authority",authority);
            store.issued(job,text(proof,"authority_id"),schema.canonical(request));contexts.require(a,prepared);candidate(a,job,start);approved(a,start);issuer.recheck(token);
            if(!store.now().isBefore(token.expiresAt())||!store.now().isBefore(expires))throw new TenantAccessException(ErrorCode.LEASE_STALE);
            store.event(nullable(start,"project_id"),a.actorId(),run,"simulation.evaluation.issued",schema.canonical(receipt(store.job(run))),api.manifest(),str(job,"classification"));
            return new Issued(token.raw(),request,receipt(store.job(run)));
        }).get());
    }
    private EvaluationProtocol.Stored definition(EnterpriseAccess.Admission a,JsonNode request,SimulationContextProducer.Prepared prepared)throws Exception {
        var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("evaluationId",text(request,"evaluation_id"));var rows=evaluations.definition(values);if(rows.size()!=1)denied();var stored=rows.get(0);
        var proof=object();proof.setAll((ObjectNode)request);proof.put("tenant_id",a.context().tenant_id());candidates.requireDefinition(a.context().tenant_id(),nullable(request,"project_id"),proof,stored);
        var definition=evaluationDefinitions.requireSimulationDefinition(a,text(request,"evaluation_id"),text(request,"evaluation_definition_sha256"));var samples=definition.request().samples().stream().filter(s->s.sample_id().equals(text(request,"sample_id"))).toList();
        if(samples.size()!=1||request.path("repeat_index").intValue()>=definition.request().variance_policy().repeat_count())denied();
        if(EvaluationDependencyUse.forDefinition(definition.request())!=EvaluationDependencyUse.SIMULATION)denied();
        var registeredInputs=inputArtifacts.getIfAvailable();
        if(registeredInputs==null)denied();
        var sample=samples.get(0);
        registeredInputs.requireRegisteredInput(a,prepared.inputArtifactPreparation(),sample.input_ref());
        long measured=schema.canonical(prepared.exactInput()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if(measured>request.path("maximum_input_units").longValue()||SimulationInvocationSchema.sum(measured,request.path("maximum_output_units").longValue())>request.path("parent_maximum_units").longValue())denied();
        return stored;
    }
    private void candidate(EnterpriseAccess.Admission a,Map<String,Object> job,JsonNode request)throws Exception {
        var proof=object();proof.setAll((ObjectNode)request);proof.put("tenant_id",a.context().tenant_id()).put("context_manifest_id",str(job,"context_manifest_id"));
        candidates.require(a,proof,text(request,"role_version"),text(request,"model_config_revision"));
        var grant=execute.require(a,nullable(request,"project_id"),text(request,"evaluation_id"));
        if(!grant.grantId().equals(str(job,"grant_id"))||!grant.revision().toString().equals(str(job,"grant_revision")))denied();
    }
    private void approved(EnterpriseAccess.Admission a,JsonNode request)throws Exception {
        String project=nullable(request,"project_id"),evaluation=text(request,"evaluation_id");execute.require(a,project,evaluation);quota.requireEvaluation(a,project,evaluation);
        var grant=quota.requireEnterprise(a);var account=single(store.account(text(request,"enterprise_account_id")));var counter=policies.counter();
        if(!"ENTERPRISE".equals(str(account,"scope"))||account.get("project_id")!=null||account.get("parent_run_id")!=null||!"ACTIVE".equals(str(account,"status"))
            ||!schema.manifest().equals(str(account,"schema_manifest_sha256"))||!grant.grantId().equals(str(account,"approval_grant_id"))||!grant.revision().toString().equals(str(account,"approval_grant_revision"))
            ||!schema.hash(counter).equals(str(account,"counter_sha256"))||!schema.canonical(counter).equals(schema.canonical(schema.parse(str(account,"counter"))))
            ||request.path("parent_maximum_units").longValue()>count(account,"max_units")||request.path("maximum_calls").longValue()>count(account,"max_calls"))denied();
        var keepGrant=retention.requireEvaluation(a,project,evaluation);var keep=single(store.retention(text(request,"retention_policy_id")));
        if(!Objects.equals(project,str(keep,"project_id"))||!evaluation.equals(str(keep,"evaluation_id"))||!"ACTIVE".equals(str(keep,"status"))||!schema.manifest().equals(str(keep,"schema_manifest_sha256"))
            ||!keepGrant.grantId().equals(str(keep,"approval_grant_id"))||!keepGrant.revision().toString().equals(str(keep,"approval_grant_revision"))||!store.now().isBefore(SimulationInvocationAuthority.time(keep,"retain_until")))denied();
    }
    private void sameStart(EnterpriseAccess.Admission a,Map<String,Object> job,JsonNode request,SimulationContextProducer.Prepared prepared)throws Exception {
        var contextProof=object();contextProof.setAll((ObjectNode)request);contextProof.put("tenant_id",a.context().tenant_id()).put("actor_id",a.actorId()).put("context_manifest_id",str(job,"context_manifest_id"));contexts.require(a,prepared,contextProof);if(!a.actorId().equals(str(job,"created_by"))||!schema.hash(request).equals(str(job,"start_request_sha256"))||!schema.canonical(request).equals(schema.canonical(schema.parse(str(job,"start_request")))))conflict();
        if(!schema.hash(sourceBinding(prepared)).equals(str(job,"source_binding_sha256"))||!schema.hash(payload(request,prepared)).equals(str(job,"prepared_payload_sha256"))
            ||!api.manifest().equals(str(job,"schema_manifest_sha256")))denied();
        for(String field:List.of("evaluation_id","sample_id","agent_version_id","role_version","model_config_revision","prompt_revision","case_id","enterprise_account_id","retention_policy_id","service_uri"))if(!text(request,field).equals(str(job,field)))denied();
        if(!Objects.equals(nullable(request,"project_id"),str(job,"project_id"))||request.path("repeat_index").longValue()!=count(job,"repeat_index")
            ||!text(request,"evaluation_definition_sha256").equals(str(job,"definition_sha256")))denied();
        if(!schema.hash(schema.parse(str(job,"source_binding"))).equals(str(job,"source_binding_sha256"))||!schema.hash(schema.parse(str(job,"prepared_payload"))).equals(str(job,"prepared_payload_sha256")))denied();
        var parent=single(store.account(str(job,"parent_account_id")));var parentGrant=quota.requireEvaluation(a,nullable(request,"project_id"),text(request,"evaluation_id"));
        if(!"PARENT_RUN".equals(str(parent,"scope"))||!"ACTIVE".equals(str(parent,"status"))||!str(job,"run_id").equals(str(parent,"parent_run_id"))
            ||!Objects.equals(nullable(request,"project_id"),str(parent,"project_id"))||count(parent,"max_units")!=request.path("parent_maximum_units").longValue()||count(parent,"max_calls")!=request.path("maximum_calls").longValue()
            ||!parentGrant.grantId().equals(str(parent,"approval_grant_id"))||!parentGrant.revision().toString().equals(str(parent,"approval_grant_revision"))||!schema.manifest().equals(str(parent,"schema_manifest_sha256"))
            ||!schema.hash(policies.counter()).equals(str(parent,"counter_sha256")))denied();
        registeredService(str(job,"service_uri"));
    }
    private JsonNode admission(EnterpriseAccess.Admission a,Map<String,Object> job,JsonNode start,SimulationContextProducer.Prepared prepared,SimulationEvaluationTokenIssuer.Issued token,LocalDateTime lease)throws Exception {
        var proof=object().put("schema_version","1.0.0").put("input_data_kind","SIMULATION").put("production_eligible",false).put("action",SimulationExecutionPermissionPolicy.ACTION).put("purpose",SimulationExecutionPermissionPolicy.PURPOSE);
        for(String field:List.of("evaluation_id","agent_version_id","evaluation_definition_sha256","project_id","model_config_revision","case_id","enterprise_account_id","retention_policy_id","maximum_input_units","maximum_output_units","maximum_calls"))proof.set(field,start.required(field));
        for(String field:List.of("step_id","action_id","context_manifest_id","parent_account_id","grant_id"))proof.put(field,str(job,field));
        proof.put("tenant_id",a.context().tenant_id()).put("actor_id",a.actorId()).put("evaluation_run_id",str(job,"run_id")).put("parent_run_id",str(job,"run_id"))
            .put("service_id",str(job,"service_uri")).put("grant_revision",count(job,"grant_revision")).put("lease_generation",1).put("authority_id",id()).put("authority_revision","1")
            .put("token_jti",token.jti()).put("issued_at",instant(token.issuedAt())).put("expires_at",instant(token.expiresAt())).put("lease_expires_at",instant(lease));
        var keep=single(store.retention(text(start,"retention_policy_id")));proof.put("retention_deadline",instant(SimulationInvocationAuthority.time(keep,"retain_until")));
        var binding=object();binding.set("identity",identity(job));binding.put("configuration_sha256",prepared.configuration().path("content_sha256").asText());
        binding.put("case_id",prepared.registration().caseId()).put("case_sha256",schema.hash(prepared.fixture())).put("case_receipt_id",prepared.registration().caseReceiptId()).put("settings_sha256",schema.hash(prepared.settings())).put("parameters_sha256",schema.hash(start.required("request_parameters")));
        for(String field:List.of("configuration_sha256","case_sha256","settings_sha256","parameters_sha256"))proof.set(field,binding.required(field));proof.put("input_sha256",schema.hash(prepared.exactInput()));proof.set("counter",policies.counter());
        var measured=object().put("kind","EXACT_INPUT").put("content_sha256",schema.hash(prepared.exactInput())).put("units",schema.canonical(prepared.exactInput()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);measured.set("counter",policies.counter());binding.set("exact_input",measured);
        var caps=object();caps.set("maximum_input_units",start.required("maximum_input_units"));caps.set("maximum_output_units",start.required("maximum_output_units"));binding.set("caps",caps);
        var local=object();local.set("identity",identity(job));local.set("case",prepared.fixture());local.set("settings",prepared.settings());local.set("parameters",start.required("request_parameters"));local.set("exact_input",prepared.exactInput());
        var catalogue=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();local.put("provenance_schema_sha256",catalogue.schemaDigest("local-simulation-provenance")).put("model_result_schema_sha256",catalogue.schemaDigest("model-stream"));binding.put("local_request_sha256",schema.hash(local));
        var result=object().put("schema_version","1.0.0");result.set("authority_proof",proof);result.set("binding",binding);result.set("exact_input",prepared.exactInput());result.set("request_parameters",start.required("request_parameters"));result.set("simulation_case",prepared.fixture());result.set("settings",prepared.settings());
        if(schema.canonical(result).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>count(keep,"maximum_record_bytes"))denied();return result;
    }
    private ObjectNode identity(Map<String,Object> job){var value=object();for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","invocation_id","context_manifest_id","model_config_revision"))value.put(field,str(job,field));return value;}
    private JsonNode sourceBinding(SimulationContextProducer.Prepared prepared){return prepared.sourceBinding();}
    private JsonNode payload(JsonNode start,SimulationContextProducer.Prepared prepared){var value=object();var configuration=object();for(String field:List.of("schema_version","tenant_id","project_id","config_id","model_config_revision","based_on_revision","definition","content_sha256","created_by","created_at"))configuration.set(field,prepared.configuration().required(field));value.set("configuration",configuration);value.set("simulation_case",prepared.fixture());value.set("exact_input",prepared.exactInput());value.set("settings",prepared.settings());value.set("request_parameters",start.required("request_parameters"));return value;}
    private void registeredService(String uri)throws Exception {if(!trust.read().registeredCertificates().containsKey(uri))denied();}
    private Map<String,Object> base(EnterpriseAccess.Admission a,String project,String classification,String manifest){return row("tenant_id",a.context().tenant_id(),"project_id",project,"schema_version","1.0.0","schema_manifest_sha256",manifest,"created_by",a.actorId(),"created_at",store.now(),"classification",classification);}
    private static String classification(String...values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int highest=0;for(String value:values){int rank=levels.indexOf(value);if(rank<0)denied();highest=Math.max(highest,rank);}return levels.get(highest);}
    private static ObjectNode object(){return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();}
    private static String id(){return UUID.randomUUID().toString();}private static String instant(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}
    private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
    private static ObjectNode receipt(Map<String,Object> job){return object().put("schema_version","1.0.0").put("run_id",str(job,"run_id")).put("status",str(job,"status")).put("revision",str(job,"revision")).put("execution_mode","SIMULATION").put("production_eligible",false);}
    public record Issued(String token,JsonNode admission,JsonNode receipt){@Override public String toString(){return "IssuedSimulationEvaluation[redacted]";}}
}
