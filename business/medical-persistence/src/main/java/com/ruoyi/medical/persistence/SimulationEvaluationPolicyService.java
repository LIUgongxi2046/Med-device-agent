package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import static com.ruoyi.medical.persistence.SimulationEvaluationStore.row;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Explicit authenticated creation only. Existing grants are checked, never fabricated or upgraded. */
final class SimulationEvaluationPolicyService {
    private final EnterpriseAccess access;private final SimulationEvaluationStore store;
    private final SimulationInvocationSchema schema;private final SimulationQuotaPermissionPolicy quota;
    private final SimulationRetentionPermissionPolicy retention;
    SimulationEvaluationPolicyService(EnterpriseAccess access,SimulationEvaluationStore store,SimulationInvocationSchema schema,
            SimulationQuotaPermissionPolicy quota,SimulationRetentionPermissionPolicy retention){this.access=access;this.store=store;this.schema=schema;this.quota=quota;this.retention=retention;}
    JsonNode enterprise(ApplicationSessionIdentity.Verified subject,String context,JsonNode request)throws Exception {
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var grant=quota.requireEnterprise(a);String id=text(request,"enterprise_account_id");
            var existing=store.account(id);var counter=counter();
            if(!existing.isEmpty()){
                var saved=single(existing);if(!"ENTERPRISE".equals(str(saved,"scope"))||count(saved,"max_units")!=request.path("maximum_units").longValue()
                    ||count(saved,"max_calls")!=request.path("maximum_calls").longValue()||!grant.grantId().equals(str(saved,"approval_grant_id"))
                    ||!grant.revision().toString().equals(str(saved,"approval_grant_revision"))||!"ACTIVE".equals(str(saved,"status"))
                    ||!schema.hash(counter).equals(str(saved,"counter_sha256"))||!schema.manifest().equals(str(saved,"schema_manifest_sha256")))conflict();
            }else{
                var values=account(a,id,null,null,request.path("maximum_units").longValue(),request.path("maximum_calls").longValue(),grant,"INTERNAL");
                store.insert("simulation_quota_account",values);store.event(null,a.actorId(),id,"simulation.quota.created",schema.canonical(request),schema.manifest(),"INTERNAL");
            }
            quota.requireEnterprise(a);return receipt("account_id",id);
        }).get());
    }
    JsonNode retention(ApplicationSessionIdentity.Verified subject,String context,JsonNode request)throws Exception {
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);String project=nullable(request,"project_id"),evaluation=text(request,"evaluation_id"),id=text(request,"retention_policy_id");
            var grant=retention.requireEvaluation(a,project,evaluation);var evaluationRow=store.evaluation(evaluation);if(!Objects.equals(project,str(evaluationRow,"project_id")))denied();String classification=str(evaluationRow,"classification");var until=PermissionAuthority.time(text(request,"retain_until"));
            if(!until.isAfter(store.now()))denied();var existing=store.retention(id);
            if(!existing.isEmpty()){
                var saved=single(existing);if(!Objects.equals(project,str(saved,"project_id"))||!evaluation.equals(str(saved,"evaluation_id"))
                    ||!until.equals(SimulationInvocationAuthority.time(saved,"retain_until"))||count(saved,"maximum_record_bytes")!=request.path("maximum_record_bytes").longValue()
                    ||!grant.grantId().equals(str(saved,"approval_grant_id"))||!grant.revision().toString().equals(str(saved,"approval_grant_revision"))
                    ||!"ACTIVE".equals(str(saved,"status"))||!schema.manifest().equals(str(saved,"schema_manifest_sha256")))conflict();
            }else{
                var values=base(a,project,classification);values.putAll(row("policy_id",id,"evaluation_id",evaluation,"status","ACTIVE","retain_until",until,
                    "maximum_record_bytes",request.path("maximum_record_bytes").longValue(),"approval_grant_id",grant.grantId(),"approval_grant_revision",grant.revision(),"revision",1));
                store.insert("simulation_retention_policy",values);store.event(project,a.actorId(),id,"simulation.retention.created",schema.canonical(request),schema.manifest(),classification);
            }
            retention.requireEvaluation(a,project,evaluation);return receipt("policy_id",id);
        }).get());
    }
    Map<String,Object> account(EnterpriseAccess.Admission a,String id,String project,String run,long units,long calls,PermissionProtocol.GrantRow grant,String classification)throws Exception {
        var counter=counter();var value=base(a,project,classification);
        value.putAll(row("account_id",id,"scope",run==null?"ENTERPRISE":"PARENT_RUN","parent_run_id",run,"max_units",units,"max_calls",calls,
            "spent_units",0,"reserved_units",0,"spent_calls",0,"reserved_calls",0,"counter",schema.canonical(counter),"counter_sha256",schema.hash(counter),
            "approval_grant_id",grant.grantId(),"approval_grant_revision",grant.revision(),"status","ACTIVE","revision",1,"updated_at",store.now()));return value;
    }
    JsonNode counter()throws Exception {try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-counter-identity.json"))){return schema.parse(in.readAllBytes());}}
    private Map<String,Object> base(EnterpriseAccess.Admission a,String project,String classification){return row("tenant_id",a.context().tenant_id(),"project_id",project,
        "schema_version","1.0.0","schema_manifest_sha256",schema.manifest(),"created_by",a.actorId(),"created_at",store.now(),"classification",classification);}
    private JsonNode receipt(String field,String id){return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put(field,id).put("status","ACTIVE").put("production_eligible",false);}
    static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
}
