package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Live authenticated preparation authority, not a caller-supplied mapping or business approval. */
final class TaskFieldTargetBasis {
    private static final Set<String> ACTIONS = Set.of("task.read", "task.field_mapping.write", "task.field_target.produce");
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final ResourceReadAuthority resources;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy identity;
    private final TaskInputSchema inputs;
    TaskFieldTargetBasis(TaskService tasks, TaskPlanService plans, ResourceReadAuthority resources,
            PermissionSchema json, IdentityDirectoryPolicy identity, TaskInputSchema inputs) {
        this.tasks=tasks; this.plans=plans; this.resources=resources; this.json=json; this.identity=identity; this.inputs=inputs;
    }
    Verified require(EnterpriseAccess.Admission admission, String taskId, String action) throws Exception {
        if (!ACTIONS.contains(action) || admission.simulationWorker() || admission.formalWorker() || admission.controlWorker() || admission.recoveryWorker()) denied();
        var state=read(admission,taskId,action);
        return new Verified(admission,taskId,action,state);
    }
    private State read(EnterpriseAccess.Admission admission,String taskId,String action) throws Exception {
        var task=tasks.fixedTask(admission,taskId); var head=task.head();
        var scope=new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",taskId);
        var grant=resources.require(admission,scope,action,purpose(action));
        if (head.planRevision()==null) throw new TenantAccessException(ErrorCode.PLAN_STALE);
        var plan=plans.readFixed(admission,task,head.planRevision(),true);
        var execution=plans.execution(admission,task,plan);
        var pins=json.object(); pins.put("project_id",head.projectId()); pins.put("task_revision",head.taskRevision());
        pins.put("task_input_sha256",task.input().requestHash()); pins.put("plan_revision",plan.planRevision());
        pins.put("plan_sha256",plan.contentSha256()); pins.put("template_version",head.templateVersion());
        pins.put("template_sha256",execution.template().view().template().content_sha256());
        pins.put("profile_revision",head.profileRevision()); pins.put("scope_snapshot",head.snapshotId());
        var evidence=json.object(); evidence.set("pins",pins); evidence.put("tenant_id",head.tenantId());
        evidence.put("task_id",taskId); evidence.put("actor_id",admission.actorId()); evidence.put("action",action);
        evidence.put("purpose",purpose(action)); evidence.put("classification",identity.classification());
        evidence.put("context_revision",admission.context().context_revision()); evidence.set("grant",TaskFieldAuthorityWitness.tree(grant));
        evidence.set("template_grant",TaskFieldAuthorityWitness.tree(execution.template().grant()));
        evidence.set("template_dependencies",TaskFieldAuthorityWitness.tree(inputs.read(execution.template().dependencyAuthorizations())));
        evidence.set("sources",TaskFieldAuthorityWitness.tree(execution.sources())); evidence.set("owners",TaskFieldAuthorityWitness.tree(execution.owners()));
        evidence.set("conditions",json.tree(execution.template().view().conditions()));
        evidence.put("auth_epoch",admission.watermarks().authEpoch().toString());
        evidence.put("governance_epoch",admission.watermarks().governanceEpoch().toString());
        return new State(task,plan,execution,pins,evidence,org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(evidence)),grant);
    }
    private record State(TaskService.VerifiedTask task,TaskPlanWriteProtocol.View plan,TaskPlanService.Confirmation execution,
            ObjectNode pins,ObjectNode authorizations,String sha256,PermissionProtocol.GrantRow grant) { }
    final class Verified {
        private final EnterpriseAccess.Admission admission;
        private final String taskId,action;
        private final State state;
        private final Thread thread=Thread.currentThread();
        private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission admission,String taskId,String action,State state) {
            this.admission=admission;this.taskId=taskId;this.action=action;this.state=state;active();
        }
        void requireUnchanged() throws Exception {
            active(); if(!state.sha256().equals(read(admission,taskId,action).sha256()))
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        void requireAction(String expected){active();if(!action.equals(expected))denied();}
        void requirePins(JsonNode request) {
            active(); state.pins().fields().forEachRemaining(entry->{
                if(!Objects.equals(entry.getValue(),request.get(entry.getKey())))
                    throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
            });
        }
        EnterpriseAccess.Admission admission(){active();return admission;}
        TaskService.VerifiedTask task(){active();return state.task();}
        TaskPlanWriteProtocol.View plan(){active();return state.plan();}
        TaskPlanService.Confirmation execution(){active();return state.execution();}
        PermissionProtocol.GrantRow grant(){active();return state.grant();}
        ObjectNode pins(){active();return state.pins().deepCopy();}
        JsonNode authorizations(){active();return state.authorizations().deepCopy();}
        String authorizationsSha256(){active();return state.sha256();}
        String classification(){active();return identity.classification();}
        private void active(){
            if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())denied();
            TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.TaskFieldTargetBasis");
            var actual=TransactionSynchronizationManager.getResourceMap();
            if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))denied();
        }
    }
    private static String purpose(String action){return "task.read".equals(action)?"TASK_READING":"STRICT_FIELD_PREPARATION";}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
