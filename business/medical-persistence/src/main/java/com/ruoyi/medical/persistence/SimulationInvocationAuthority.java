package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A separate restricted evaluation authority. It cannot issue production RunTokens. */
final class SimulationInvocationAuthority {
    private final SimulationInvocationStore store;
    private final SimulationInvocationSchema schema;
    private final SimulationExecutionPermissionPolicy policy;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final SimulationRunTokenVerifier tokens;
    private final SimulationEvaluationCandidateAuthority candidates;
    SimulationInvocationAuthority(SimulationInvocationStore store,SimulationInvocationSchema schema,
            SimulationExecutionPermissionPolicy policy,PermissionMapper permissions,PermissionAuthority grants,SimulationRunTokenVerifier tokens,SimulationEvaluationCandidateAuthority candidates) {
        this.store=store; this.schema=schema; this.policy=policy; this.permissions=permissions; this.grants=grants; this.tokens=tokens; this.candidates=candidates;
    }
    Checked require(jakarta.servlet.http.HttpServletRequest http,EnterpriseAccess.Admission admission,JsonNode request,ServiceCertificateIdentity.Verified service) throws Exception {
        var proof=request.required("authority_proof"); var id=request.required("binding").required("identity");
        String actor=admission.actorId(),tenant=admission.context().tenant_id(),project=nullable(proof,"project_id");
        if(!actor.equals(text(proof,"actor_id"))||!tenant.equals(text(proof,"tenant_id"))||!service.serviceUri().equals(text(proof,"service_id"))) denied();
        var row=store.authority(text(proof,"authority_id"));
        schema.validate("SimulationExecutionAuthorityProof",schema.parse(str(row,"proof")));
        if(!schema.canonical(proof).equals(schema.canonical(schema.parse(str(row,"proof"))))
            ||!schema.hash(proof).equals(str(row,"proof_sha256"))||!"ACTIVE".equals(str(row,"status"))
            ||!schema.manifest().equals(str(row,"schema_manifest_sha256"))||!"1.0.0".equals(str(row,"schema_version"))) denied();
        for(String field:List.of("authority_id","authority_revision","evaluation_id","evaluation_run_id","step_id","action_id","grant_id","grant_revision","lease_generation","token_jti","retention_policy_id"))
            if(!proof.path(field).asText().equals(str(row,field))) denied();
        if(!text(proof,"parent_run_id").equals(str(row,"run_id"))||!Objects.equals(project,str(row,"project_id"))
            ||!actor.equals(str(row,"created_by"))||!service.serviceUri().equals(str(row,"service_uri"))) denied();
        LocalDateTime now=store.now(),expires=PermissionAuthority.time(text(proof,"expires_at"));
        if(!now.isBefore(expires)||PermissionAuthority.time(text(proof,"issued_at")).isAfter(now)
            ||!expires.equals(time(row,"expires_at"))||!PermissionAuthority.time(text(proof,"issued_at")).equals(time(row,"created_at"))) denied();
        tokens.require(http,admission,proof,str(row,"principal_id"),service);
        var grant=policy.require(admission,project,text(proof,"evaluation_id"));
        if(!grant.grantId().equals(text(proof,"grant_id"))||!grant.revision().toString().equals(proof.path("grant_revision").asText())) denied();
        var job=store.job(text(proof,"parent_run_id"));
        if(!text(proof,"evaluation_run_id").equals(str(job,"run_id"))||!"SYSTEM_JOB".equals(str(job,"execution_kind"))
            ||!"SIMULATION_EVALUATION".equals(str(job,"job_type"))||!"EVALUATION".equals(str(job,"target_kind"))
            ||!text(proof,"evaluation_id").equals(str(job,"target_id"))||!actor.equals(str(job,"initiator_id"))
            ||!Objects.equals(project,str(job,"project_id"))||!"RUNNING".equals(str(job,"status"))||job.get("ended_at")!=null) denied();
        var principal=store.principal(str(row,"principal_id"));
        if(!"ACTIVE".equals(str(principal,"status"))||!text(proof,"parent_run_id").equals(str(principal,"run_id"))
            ||!Objects.equals(project,str(principal,"project_id"))||!actor.equals(str(principal,"initiator_id"))) denied();
        var step=store.step(id);
        if(!"RUNNING".equals(str(step,"status"))||!service.serviceUri().equals(str(step,"lease_owner"))
            ||count(step,"lease_generation")!=proof.path("lease_generation").longValue()
            ||!PermissionAuthority.time(text(proof,"lease_expires_at")).equals(time(step,"lease_expires_at"))
            ||!now.isBefore(time(step,"lease_expires_at"))) throw new TenantAccessException(ErrorCode.LEASE_STALE);
        candidates.require(admission,proof,str(step,"role_version"),text(proof,"model_config_revision"));
        var action=store.action(id);
        if(!text(proof,"input_sha256").equals(str(action,"input_sha256"))||!text(proof,"parameters_sha256").equals(str(action,"arguments_sha256"))
            ||count(action,"lease_generation")!=proof.path("lease_generation").longValue()||!"MODEL".equals(str(action,"kind"))
            ||!text(proof,"model_config_revision").equals(str(action,"tool_version"))
            ||!str(action,"run_id").equals(str(action,"origin_run_id"))||!str(action,"step_id").equals(str(action,"origin_step_id"))
            ||!str(action,"action_id").equals(str(action,"origin_action_id"))) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var configuration=store.config(text(proof,"model_config_revision"));
        if(!"SIMULATION".equals(str(configuration,"execution_mode"))||!"DRAFT".equals(str(configuration,"status"))
            ||!Objects.equals(project,str(configuration,"project_id"))) denied();
        var context=store.context(text(proof,"context_manifest_id"));
        if(!"SIMULATION".equals(str(context,"execution_mode"))||!Objects.equals(project,str(context,"project_id"))) denied();
        requireClassification(str(job,"classification"),str(context,"classification"),str(configuration,"classification"),
            str(row,"classification"),str(principal,"classification"),str(store.evaluation(text(proof,"evaluation_id")),"classification"));
        var retention=store.retention(text(proof,"retention_policy_id"));
        if(!"ACTIVE".equals(str(retention,"status"))||!Objects.equals(project,str(retention,"project_id"))
            ||!text(proof,"evaluation_id").equals(str(retention,"evaluation_id"))||!schema.manifest().equals(str(retention,"schema_manifest_sha256"))
            ||!"1.0.0".equals(str(retention,"schema_version"))||count(retention,"revision")==0
            ||!PermissionAuthority.time(text(proof,"retention_deadline")).equals(time(retention,"retain_until"))
            ||!now.isBefore(time(retention,"retain_until"))||schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>count(retention,"maximum_record_bytes")) denied();
        var retentionGrant=grants.requireResource(new PermissionProtocol.Scope(tenant,project,"EVALUATION",text(proof,"evaluation_id")),actor,
            admission.bindingView().mode(),"model.simulation.retention.configure","SIMULATION_EVALUATION",
            permissions.resourceGrants(actor,"model.simulation.retention.configure"),now);
        if(!retentionGrant.grantId().equals(str(retention,"approval_grant_id"))||!retentionGrant.revision().toString().equals(str(retention,"approval_grant_revision"))) denied();
        return new Checked(str(configuration,"config_id"),str(job,"classification"),action);
    }
    /** Retention approval is independent of expired execution grants and dispatch leases. */
    void requireFactRetention(JsonNode request,long bytes) throws Exception {
        var proof=request.required("authority_proof");var retention=store.retention(text(proof,"retention_policy_id"));
        var now=store.now();
        if(!"ACTIVE".equals(str(retention,"status"))||!Objects.equals(nullable(proof,"project_id"),str(retention,"project_id"))
            ||!text(proof,"evaluation_id").equals(str(retention,"evaluation_id"))||!schema.manifest().equals(str(retention,"schema_manifest_sha256"))
            ||!"1.0.0".equals(str(retention,"schema_version"))||count(retention,"revision")==0
            ||!PermissionAuthority.time(text(proof,"retention_deadline")).equals(time(retention,"retain_until"))
            ||!now.isBefore(time(retention,"retain_until"))||bytes>count(retention,"maximum_record_bytes")) denied();
        String approver=str(retention,"created_by");
        var grant=single(permissions.currentGrant(str(retention,"approval_grant_id")));
        var binding=single(permissions.targetBinding(approver));
        var approved=grants.requireResource(new PermissionProtocol.Scope(text(proof,"tenant_id"),nullable(proof,"project_id"),"EVALUATION",text(proof,"evaluation_id")),
            approver,binding.mode(),"model.simulation.retention.configure","SIMULATION_EVALUATION",List.of(grant),now);
        if(!approved.revision().toString().equals(str(retention,"approval_grant_revision"))) denied();
    }
    void account(EnterpriseAccess.Admission admission,JsonNode proof,Map<String,Object> account,boolean enterprise,long units) throws IOException {
        if(!"ACTIVE".equals(str(account,"status"))||!"1.0.0".equals(str(account,"schema_version"))
            ||!schema.manifest().equals(str(account,"schema_manifest_sha256"))||count(account,"revision")==0
            ||!schema.hash(proof.required("counter")).equals(str(account,"counter_sha256"))
            ||!schema.canonical(proof.required("counter")).equals(schema.canonical(schema.parse(str(account,"counter"))))) denied();
        if(enterprise ? !"ENTERPRISE".equals(str(account,"scope"))||account.get("project_id")!=null||account.get("parent_run_id")!=null
            : !"PARENT_RUN".equals(str(account,"scope"))||!text(proof,"parent_run_id").equals(str(account,"parent_run_id"))||!Objects.equals(nullable(proof,"project_id"),str(account,"project_id"))) denied();
        String tenant=admission.context().tenant_id(),actor=admission.actorId();
        var scope=enterprise?new PermissionProtocol.Scope(tenant,null,"ENTERPRISE",tenant)
            :new PermissionProtocol.Scope(tenant,nullable(proof,"project_id"),"EVALUATION",text(proof,"evaluation_id"));
        var grant=grants.requireResource(scope,actor,admission.bindingView().mode(),"model.simulation.quota.configure","SIMULATION_EVALUATION",
            permissions.resourceGrants(actor,"model.simulation.quota.configure"),store.now());
        if(!grant.grantId().equals(str(account,"approval_grant_id"))||!grant.revision().toString().equals(str(account,"approval_grant_revision"))) denied();
        long maximum=count(account,"max_units"),spent=count(account,"spent_units"),reserved=count(account,"reserved_units");
        long calls=count(account,"max_calls"),spentCalls=count(account,"spent_calls"),reservedCalls=count(account,"reserved_calls");
        if(maximum==0||calls==0||spent>maximum||reserved>maximum-spent||spentCalls>calls||reservedCalls>calls-spentCalls) denied();
        if(units>0&&(units>maximum-spent-reserved||reservedCalls>=calls-spentCalls)) throw new TenantAccessException(ErrorCode.BUDGET_EXCEEDED);
        if(!enterprise&&calls>proof.path("maximum_calls").longValue()) denied();
    }
    static LocalDateTime time(Map<String,Object> row,String key) {
        Object value=row.get(key); if(value instanceof LocalDateTime time)return time;
        if(value instanceof java.sql.Timestamp time)return time.toLocalDateTime(); denied(); return null;
    }
    static void requireClassification(String target,String... inputs) {
        var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");if(target==null) denied();int rank=levels.indexOf(target);
        if(rank<0) denied();
        for(String input:inputs) if(input==null||!levels.contains(input)||levels.indexOf(input)>rank) denied();
    }
    record Checked(String configId,String classification,Map<String,Object> action) { }
}
