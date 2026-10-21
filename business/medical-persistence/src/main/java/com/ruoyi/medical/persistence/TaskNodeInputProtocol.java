package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Objects;

/** Closed fixed N02 data. Constructing these values grants no execution or registration authority. */
public final class TaskNodeInputProtocol {
    private TaskNodeInputProtocol() { }

    public record SourceSelection(String sourceVersion, String parseVersion, String indexVersion,
            String approvalRevision, String bindingRevision, String rightsRevision, String purpose) {
        public SourceSelection {
            for (String id : List.of(sourceVersion, parseVersion, indexVersion, approvalRevision, bindingRevision, rightsRevision)) WireValues.uuid(id);
            if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("SOURCE_PURPOSE_REQUIRED");
        }
    }

    public record ReplayArtifact(String artifactId, String sha256, String objectVersion) {
        public ReplayArtifact {
            WireValues.uuid(artifactId); WireValues.hash(sha256);
            if (objectVersion == null || objectVersion.isBlank() || objectVersion.length() > 256) throw new IllegalArgumentException("OBJECT_VERSION_REQUIRED");
        }
    }

    public record Predecessor(String stepId,String resultArtifactId,String resultSha256,String resultObjectVersion,
            String resultReceiptSha256,String evidenceArtifactId,String evidenceSha256,String evidenceObjectVersion,
            String evidenceReceiptSha256,String completionReceiptSha256) {
        public Predecessor {
            for(String id:List.of(stepId,resultArtifactId,evidenceArtifactId))WireValues.uuid(id);
            for(String hash:List.of(resultSha256,resultReceiptSha256,evidenceSha256,evidenceReceiptSha256,completionReceiptSha256))WireValues.hash(hash);
            for(String version:List.of(resultObjectVersion,evidenceObjectVersion))if(version.isBlank()||version.length()>256)throw new IllegalArgumentException("PREDECESSOR_OBJECT_VERSION_REQUIRED");
            if(resultArtifactId.equals(evidenceArtifactId))throw new IllegalArgumentException("DISTINCT_PREDECESSOR_ARTIFACTS_REQUIRED");
        }
    }

    public record Input(String schemaVersion, String tenantId, String projectId, String taskId,
            String runId, String stepId, String graphId, String graphSha256, String nodeKind,
            String questionId, String questionKey, String questionText, String sectionId,
            String planRevision, String planSha256, String snapshotId, String snapshotSha256,
            String replaySha256, ReplayArtifact replayArtifact, String replayReceiptSha256,
            PermissionProtocol.Basis roleVersion, PermissionProtocol.Basis executionPolicyVersion,
            List<SourceSelection> fixedSources, List<RunTokenAuthoritySchema.Permission> nodePermissions, List<Predecessor> predecessors) {
        public Input(String schemaVersion, String tenantId, String projectId, String taskId,
            String runId, String stepId, String graphId, String graphSha256, String nodeKind,
            String questionId, String questionKey, String questionText, String sectionId,
            String planRevision, String planSha256, String snapshotId, String snapshotSha256,
            String replaySha256, ReplayArtifact replayArtifact, String replayReceiptSha256,
            PermissionProtocol.Basis roleVersion, PermissionProtocol.Basis executionPolicyVersion,
            List<SourceSelection> fixedSources, List<RunTokenAuthoritySchema.Permission> nodePermissions) {
            this(schemaVersion,tenantId,projectId,taskId,runId,stepId,graphId,graphSha256,nodeKind,questionId,questionKey,questionText,sectionId,planRevision,planSha256,snapshotId,snapshotSha256,replaySha256,replayArtifact,replayReceiptSha256,roleVersion,executionPolicyVersion,fixedSources,nodePermissions,List.of());
        }
        public Input {
            if (!"1.0.0".equals(schemaVersion) || !"N02".equals(nodeKind)) throw new IllegalArgumentException("N02_INPUT_VERSION_REQUIRED");
            for (String id : List.of(tenantId, projectId, taskId, runId, stepId, graphId, questionId, sectionId, planRevision, snapshotId)) WireValues.uuid(id);
            for (String hash : List.of(graphSha256, planSha256, snapshotSha256, replaySha256, replayReceiptSha256)) WireValues.hash(hash);
            if (questionKey == null || questionKey.isBlank() || questionKey.length() > 128 || questionText == null || questionText.isBlank() || questionText.length() > 16384) throw new IllegalArgumentException("FIXED_QUESTION_REQUIRED");
            Objects.requireNonNull(replayArtifact); Objects.requireNonNull(roleVersion); Objects.requireNonNull(executionPolicyVersion);
            if (!replaySha256.equals(replayArtifact.sha256())) throw new IllegalArgumentException("REPLAY_DIGEST_MISMATCH");
            fixedSources = List.copyOf(fixedSources); nodePermissions = List.copyOf(nodePermissions); predecessors = List.copyOf(predecessors);
            if (fixedSources.size() > 512 || nodePermissions.size() > 256 || predecessors.size() > 512) throw new IllegalArgumentException("INPUT_CAPACITY_EXCEEDED");
            if (fixedSources.stream().map(SourceSelection::sourceVersion).distinct().count() != fixedSources.size()
                    || nodePermissions.stream().distinct().count() != nodePermissions.size()) throw new IllegalArgumentException("DUPLICATE_INPUT_SELECTION");
            if(predecessors.stream().map(Predecessor::stepId).distinct().count()!=predecessors.size()
                    ||predecessors.stream().anyMatch(p->stepId.equals(p.stepId())))throw new IllegalArgumentException("INVALID_PREDECESSOR_STEPS");
            var predecessorArtifacts=new java.util.HashSet<String>();
            for(var predecessor:predecessors)if(!predecessorArtifacts.add(predecessor.resultArtifactId())||!predecessorArtifacts.add(predecessor.evidenceArtifactId()))throw new IllegalArgumentException("DUPLICATE_PREDECESSOR_ARTIFACTS");
            for (var permission : nodePermissions) {
                if (!tenantId.equals(permission.scope().tenant_id()) || permission.scope().project_id() != null && !projectId.equals(permission.scope().project_id())) throw new IllegalArgumentException("INPUT_PERMISSION_SCOPE_MISMATCH");
            }
        }
    }
}
