package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** Host-selected N05 review scope. Model output cannot shrink claims, rules, or target sections. */
public record TaskSourceVerifierInput(String schemaVersion,String nodeKind,String tenantId,String projectId,String taskId,
    String runId,String stepId,String inputId,String graphId,String graphSha256,String replaySha256,
    String planRevision,String planSha256,String snapshotId,PermissionProtocol.Basis roleVersion,
    PermissionProtocol.Basis executionPolicyVersion,PermissionProtocol.Basis modelVersion,PermissionProtocol.Basis submitToolVersion,
    String candidateId,String candidateArtifactId,ObjectRef candidateObject,String candidateReceiptSha256,
    String bodyArtifactId,ObjectRef bodyObject,String writerStepId,List<String> targetSections,
    String ruleRevision,String ruleManifestSha256,List<String> requiredClaimIds,List<PermissionProtocol.Basis> requiredRules,
    List<TaskNodeInputProtocol.SourceSelection> fixedSources,List<RunTokenAuthoritySchema.Permission> nodePermissions){
    public TaskSourceVerifierInput {
        if(!"1.0.0".equals(schemaVersion)||!"N05".equals(nodeKind))throw new IllegalArgumentException("N05_INPUT_REQUIRED");
        for(String id:List.of(tenantId,projectId,taskId,runId,stepId,inputId,graphId,planRevision,snapshotId,candidateId,candidateArtifactId,bodyArtifactId,writerStepId,ruleRevision))WireValues.uuid(id);
        for(String hash:List.of(graphSha256,replaySha256,planSha256,candidateReceiptSha256,ruleManifestSha256))WireValues.hash(hash);
        for(Object value:List.of(roleVersion,executionPolicyVersion,modelVersion,submitToolVersion,candidateObject,bodyObject))Objects.requireNonNull(value);
        targetSections=List.copyOf(targetSections);requiredClaimIds=List.copyOf(requiredClaimIds);requiredRules=List.copyOf(requiredRules);fixedSources=List.copyOf(fixedSources);nodePermissions=List.copyOf(nodePermissions);
        targetSections.forEach(WireValues::uuid);requiredClaimIds.forEach(WireValues::uuid);
        if(stepId.equals(writerStepId)||targetSections.isEmpty()||targetSections.size()>512||requiredClaimIds.size()>10000||requiredRules.isEmpty()||requiredRules.size()>10000||fixedSources.size()>512||nodePermissions.isEmpty()||nodePermissions.size()>256
            ||new HashSet<>(targetSections).size()!=targetSections.size()||new HashSet<>(requiredClaimIds).size()!=requiredClaimIds.size()
            ||requiredRules.stream().map(PermissionProtocol.Basis::version_id).distinct().count()!=requiredRules.size()
            ||fixedSources.stream().map(TaskNodeInputProtocol.SourceSelection::sourceVersion).distinct().count()!=fixedSources.size()
            ||new HashSet<>(nodePermissions).size()!=nodePermissions.size())throw new IllegalArgumentException("N05_INPUT_SCOPE_INVALID");
        for(var p:nodePermissions)if(!tenantId.equals(p.scope().tenant_id())||p.scope().project_id()!=null&&!projectId.equals(p.scope().project_id()))throw new IllegalArgumentException("N05_PERMISSION_SCOPE");
    }
}
