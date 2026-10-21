package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private-lifecycle proof for the new exact pre-RN01 input read. No runtime bridge is installed.
 * Proofs are not permissions for task.run, arbitrary artifacts, future artifacts, or legacy 078 selectors. */
public final class TaskFieldTargetInputAuthority {
    private final TaskFieldTargetInputCapabilityService capabilities;
    private final PermissionSchema json;
    public TaskFieldTargetInputAuthority(TaskFieldTargetInputCapabilityService capabilities,PermissionSchema json) {
        this.capabilities=Objects.requireNonNull(capabilities); this.json=Objects.requireNonNull(json);
    }

    /** Explicit capability identity; there is deliberately no choose-first-approved/fallback API. */
    public Verified require(EnterpriseAccess.Admission admission,
            AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,
            String taskId,String capabilityId,String capabilitySha256) throws Exception {
        TaskFieldTargetInputCapabilityService.requireAdmission(admission);
        var loaded=capabilities.requireCurrent(admission,publication,policy,role,capabilityId,capabilitySha256,taskId,null,null);
        return new Verified(admission,publication,policy,role,taskId,loaded);
    }

    public final class Verified {
        private final EnterpriseAccess.Admission admission;
        private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private final PermissionProtocol.Basis role;
        private final String taskId;
        private final TaskFieldTargetInputCapabilityService.Checked original;
        private final Thread thread=Thread.currentThread();
        private final TenantScope tenantScope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission admission,AgentPublicationAuthority.VerifiedPublication publication,
                AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,String taskId,
                TaskFieldTargetInputCapabilityService.Checked original) {
            this.admission=admission; this.publication=publication; this.policy=policy; this.role=role;
            this.taskId=taskId; this.original=original; active(admission);
        }

        /** Every use rechecks the original capability and approval; changed approval never causes reselection. */
        public void requireUnchanged() throws Exception {
            active(admission);
            var fresh=capabilities.requireCurrent(admission,publication,policy,role,original.capabilityId(),
                    original.capabilityHash(),taskId,original.decisionId(),original.decisionHash());
            if(!original.fingerprint().equals(fresh.fingerprint())) stale();
        }

        /** Call again for each physical read segment. This method performs no object I/O and issues no generic token. */
        public void requireRead(EnterpriseAccess.Admission actual,String task,String nodeKind,
                InterfaceProtocol.ObjectRef reference,Collection<String> occurrenceIds,String requestedReadBytes,
                String action,String purpose) throws Exception {
            active(actual); requireUnchanged();
            requireExactRead(original.manifest(),json.tree(reference),taskId,task,nodeKind,occurrenceIds,requestedReadBytes,action,purpose);
        }

        public String capabilityId(){active(admission);return original.capabilityId();}
        public String capabilitySha256(){active(admission);return original.capabilityHash();}
        public String decisionId(){active(admission);return original.decisionId();}
        public String decisionSha256(){active(admission);return original.decisionHash();}
        public JsonNode authorizations() throws Exception {requireUnchanged();return original.authorizations().deepCopy();}
        public JsonNode manifest() throws Exception {requireUnchanged();return original.manifest().deepCopy();}

        private void active(EnterpriseAccess.Admission actual) {
            requireLifetime(admission,actual,thread,tenantScope,transaction);
        }
    }

    /** A lifetime guard alone grants no authority and cannot construct a Verified proof. */
    static void requireLifetime(EnterpriseAccess.Admission bound,EnterpriseAccess.Admission actual,Thread thread,
            TenantScope scope,Map<Object,Object> transaction) {
        if(actual!=bound||thread!=Thread.currentThread()||!TransactionSynchronizationManager.isActualTransactionActive()
                ||transaction.isEmpty()||scope!=TenantScope.requireCurrent()) denied();
        TaskFieldTargetInputCapabilityService.requireAdmission(bound);
        var current=TransactionSynchronizationManager.getResourceMap();
        if(current.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue())) denied();
    }

    /** Data-only exact comparison, kept separate from proof construction for adversarial boundary tests. */
    static void requireExactRead(JsonNode manifest,JsonNode object,String boundTask,String task,String node,
            Collection<String> occurrences,String bytes,String action,String purpose) {
        if(!Objects.equals(boundTask,task)||!Objects.equals(task,manifest.path("task_id").textValue())
                ||!"N03".equals(node)||!"N03".equals(manifest.path("consumer_node_kind").textValue())
                ||!"STRICT_FIELD_TARGET".equals(manifest.path("artifact_kind").textValue())
                ||!"artifact.read".equals(action)||!"artifact.read".equals(manifest.path("action").textValue())
                ||!TaskFieldTargetInputCapabilityService.PURPOSE.equals(purpose)
                ||!purpose.equals(manifest.path("purpose").textValue())||object==null||!object.equals(manifest.path("object_ref"))) denied();
        if(occurrences==null||bytes==null) denied();
        TaskFieldTargetInputCapabilityService.requireCoverage(manifest.required("occurrence_ids"),occurrences);
        var requested=WireValues.revision(bytes); var maximum=WireValues.revision(manifest.required("maximum_read_bytes").textValue());
        var size=WireValues.revision(object.required("size_bytes").textValue());
        if(requested.signum()<=0||requested.compareTo(maximum)>0||requested.compareTo(size)>0) denied();
    }
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
