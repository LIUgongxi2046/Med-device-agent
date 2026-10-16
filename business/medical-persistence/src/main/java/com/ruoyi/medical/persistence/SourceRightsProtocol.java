package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class SourceRightsProtocol {
    private SourceRightsProtocol() { }

    public record EvidenceManifest(String owner, String license, List<String> environments,
            List<String> classifications, List<PermissionProtocol.Basis> authorization_evidence) {
        public EvidenceManifest {
            environments=List.copyOf(environments); classifications=List.copyOf(classifications);
            authorization_evidence=List.copyOf(authorization_evidence);
        }
    }
    public record Proposal(String purpose, List<String> allowed_actions, List<String> providers, List<String> regions,
            String valid_from, String valid_to, EvidenceManifest evidence_manifest) {
        public Proposal { allowed_actions=List.copyOf(allowed_actions); providers=List.copyOf(providers); regions=List.copyOf(regions); }
    }
    public record PreviewInput(String schema_version, String decision, Proposal proposal) { }
    public record DecisionInput(String schema_version, String expected_revision, PermissionProtocol.Basis base_rights,
            String decision, Proposal proposal, String reviewed_manifest_sha256, String reason) { }
    public record StateRow(String tenantId, String sourceVersion, String rightsRevision, String lastDecisionId,
            BigInteger revision, LocalDateTime updatedAt, String schemaVersion, String schemaManifest,
            String createdBy, LocalDateTime createdAt, String classification, String sourceCreatedBy,
            String lifecycle, String conversationId, String conversationOwnerId, String conversationStatus) { }
    public record OperationRow(String operationId, String subjectId, String requestSha256, String state,
            Integer httpStatus, String response, String targetId, String schemaVersion, String schemaManifest,
            String requestManifest, String decisionId, String reviewedManifest, String evidenceProof,
            String decisionResponse, String fromRightsRevision, String toRightsRevision, BigInteger resultingRevision,
            String decision, String rightsStatus, String createdBy, LocalDateTime createdAt) { }
    record Evidence(ArtifactSourceAuthority.VerifiedSources authorization, ObjectRef object, JsonNode manifest) { }
    record Prepared(SourceReadProtocol.SourceRow source, SourceReadProtocol.RightsRow rights, StateRow state,
            List<Evidence> evidence, JsonNode manifest, String sha256) {
        Prepared { evidence=List.copyOf(evidence); }
    }
}
