package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** Closed N04 data, never a permission, provider result or candidate registration. */
public record TaskSectionWriterInput(String schemaVersion,String nodeKind,String tenantId,String projectId,String taskId,
        String runId,String stepId,String inputId,String graphId,String graphSha256,String replaySha256,
        String planRevision,String planSha256,String snapshotId,PermissionProtocol.Basis roleVersion,
        PermissionProtocol.Basis executionPolicyVersion,PermissionProtocol.Basis modelVersion,PermissionProtocol.Basis submitToolVersion,
        String documentId,String baseDocumentRevision,String bodySchemaRevision,String bodySchemaSha256,String templateVersion,
        List<String> targetSections,String fieldCheckArtifactId,ObjectRef fieldCheckObject,String fieldCheckReceiptSha256,
        List<TaskNodeInputProtocol.Predecessor> predecessors,List<TaskNodeInputProtocol.SourceSelection> fixedSources,
        List<RunTokenAuthoritySchema.Permission> nodePermissions) {
    public TaskSectionWriterInput {
        if(!"1.0.0".equals(schemaVersion)||!"N04".equals(nodeKind))throw new IllegalArgumentException("N04_INPUT_REQUIRED");
        for(String id:List.of(tenantId,projectId,taskId,runId,stepId,inputId,graphId,planRevision,snapshotId,documentId,
                baseDocumentRevision,bodySchemaRevision,templateVersion,fieldCheckArtifactId))WireValues.uuid(id);
        for(String hash:List.of(graphSha256,replaySha256,planSha256,bodySchemaSha256,fieldCheckReceiptSha256))WireValues.hash(hash);
        Objects.requireNonNull(roleVersion);Objects.requireNonNull(executionPolicyVersion);Objects.requireNonNull(modelVersion);
        Objects.requireNonNull(submitToolVersion);Objects.requireNonNull(fieldCheckObject);
        targetSections=List.copyOf(targetSections);predecessors=List.copyOf(predecessors);fixedSources=List.copyOf(fixedSources);nodePermissions=List.copyOf(nodePermissions);
        targetSections.forEach(WireValues::uuid);
        if(targetSections.isEmpty()||targetSections.size()>512||predecessors.size()>512
                ||fixedSources.isEmpty()||fixedSources.size()>512||nodePermissions.isEmpty()||nodePermissions.size()>256)
            throw new IllegalArgumentException("N04_INPUT_CAPACITY");
        if(new HashSet<>(targetSections).size()!=targetSections.size()||new HashSet<>(nodePermissions).size()!=nodePermissions.size()
                ||predecessors.stream().map(TaskNodeInputProtocol.Predecessor::stepId).distinct().count()!=predecessors.size()
                ||predecessors.stream().anyMatch(p->stepId.equals(p.stepId()))
                ||fixedSources.stream().map(TaskNodeInputProtocol.SourceSelection::sourceVersion).distinct().count()!=fixedSources.size())
            throw new IllegalArgumentException("N04_DUPLICATE_OR_SELF_PIN");
        for(var p:nodePermissions)if(!tenantId.equals(p.scope().tenant_id())||p.scope().project_id()!=null&&!projectId.equals(p.scope().project_id()))
            throw new IllegalArgumentException("N04_PERMISSION_SCOPE");
    }
}
