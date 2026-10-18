package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.Objects;

public final class VersionResourceProtocol {
    private VersionResourceProtocol() { }

    public enum Domain {
        SOURCE_VERSION, PARSE_VERSION, INDEX_MANIFEST, APPROVAL_DECISION, RIGHTS_REVISION, SOURCE_BINDING,
        SOURCE_EXACT_REVISION, EXACT_RETRIEVAL,
        COLLECTION_VERSION, PROJECT_PROFILE, SCOPE_SNAPSHOT, TEMPLATE_VERSION, RULE_VERSION, TASK_REVISION,
        PLAN_REVISION, DOCUMENT_REVISION, SECTION_REVISION, SHARED_FACT, ARTIFACT, DEPENDENCY_ACCESS_SET,
        CONTEXT_MANIFEST, CANDIDATE, CAPABILITY_VERSION, MODEL_CONFIG, AGENT_VERSION, RELEASE_RECORD, RELEASE_SCOPE,
        CONNECTOR_CONFIG, EVALUATION, TASK_SUMMARY, BUDGET_REVISION, CONVERSATION_MESSAGE, ROLE_DEFINITION,
        SIGNATURE, EXPORT_MANIFEST, MANAGEMENT_RELEASE, IMPACT_MANIFEST, APPLICABILITY_DECISION,
        AGENT_EXECUTION_POLICY, AGENT_SOURCE_SCOPE_POLICY, AGENT_RETRIEVAL_PROFILE, AGENT_NETWORK_POLICY
    }

    public record OwnerRow(String tenantId, String versionId, String versionProjectId, String resourceProjectId,
            String resourceKind, String resourceId, String contentSha256, String sourceVersion,
            String conversationId, String conversationOwnerId, String conversationStatus) { }

    public record DomainOwnerRow(String domain, OwnerRow owner) { }

    public record ReadContext(SourceReadProtocol.Use use, String conversationId) {
        public ReadContext {
            Objects.requireNonNull(use);
            if (conversationId != null) WireValues.uuid(conversationId);
        }
    }
}
