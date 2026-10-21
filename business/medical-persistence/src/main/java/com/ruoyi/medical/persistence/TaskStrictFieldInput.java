package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** Closed N03 input data. Not an artifact, producer permission, approval or live lease. */
public record TaskStrictFieldInput(String schemaVersion,String nodeKind,String tenantId,String projectId,String taskId,
        String runId,String stepId,String inputId,String graphId,String graphSha256,String replaySha256,
        String planRevision,String planSha256,String snapshotId,PermissionProtocol.Basis roleVersion,
        PermissionProtocol.Basis executionPolicyVersion,PermissionProtocol.Basis toolVersion,
        String targetArtifactId,ObjectRef targetObject,String targetRegistrationSha256,JsonNode targetManifest,
        PermissionProtocol.Basis targetInputCapability,PermissionProtocol.Basis targetInputDecision,
        List<PolicyEntry> policies,List<TaskNodeInputProtocol.SourceSelection> fixedSources,
        List<RunTokenAuthoritySchema.Permission> nodePermissions,List<TaskNodeInputProtocol.Predecessor> predecessors) {
    public record PolicyEntry(String entryId,PermissionProtocol.Basis policy,PermissionProtocol.Basis sourceContext) {
        public PolicyEntry(String entryId,PermissionProtocol.Basis policy){this(entryId,policy,null);}
        public PolicyEntry {WireValues.uuid(entryId);Objects.requireNonNull(policy);}
    }
    public TaskStrictFieldInput {
        if(!java.util.Set.of("1.0.0","2.0.0").contains(schemaVersion)||!"N03".equals(nodeKind))throw new IllegalArgumentException("N03_INPUT_REQUIRED");
        for(String id:List.of(tenantId,projectId,taskId,runId,stepId,inputId,graphId,planRevision,snapshotId,targetArtifactId))WireValues.uuid(id);
        for(String hash:List.of(graphSha256,replaySha256,planSha256,targetRegistrationSha256))WireValues.hash(hash);
        Objects.requireNonNull(roleVersion);Objects.requireNonNull(executionPolicyVersion);Objects.requireNonNull(toolVersion);
        Objects.requireNonNull(targetObject);Objects.requireNonNull(targetInputCapability);Objects.requireNonNull(targetInputDecision);
        targetManifest=Objects.requireNonNull(targetManifest).deepCopy();policies=List.copyOf(policies);fixedSources=List.copyOf(fixedSources);
        nodePermissions=List.copyOf(nodePermissions);predecessors=List.copyOf(predecessors);
        if(policies.isEmpty()||policies.size()>10000||fixedSources.isEmpty()||fixedSources.size()>512||nodePermissions.isEmpty()
                ||nodePermissions.size()>256||predecessors.isEmpty()||predecessors.size()>512)throw new IllegalArgumentException("N03_INPUT_CAPACITY");
        if(policies.stream().map(PolicyEntry::entryId).distinct().count()!=policies.size()
                ||fixedSources.stream().map(TaskNodeInputProtocol.SourceSelection::sourceVersion).distinct().count()!=fixedSources.size()
                ||predecessors.stream().map(TaskNodeInputProtocol.Predecessor::stepId).distinct().count()!=predecessors.size()
                ||nodePermissions.stream().distinct().count()!=nodePermissions.size())throw new IllegalArgumentException("N03_DUPLICATE_PIN");
        for(var permission:nodePermissions)if(!tenantId.equals(permission.scope().tenant_id())
                ||permission.scope().project_id()!=null&&!projectId.equals(permission.scope().project_id()))throw new IllegalArgumentException("N03_PERMISSION_SCOPE");
    }
    @Override public JsonNode targetManifest(){return targetManifest.deepCopy();}
}
