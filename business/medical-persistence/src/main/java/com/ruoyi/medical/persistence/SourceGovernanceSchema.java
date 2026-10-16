package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class SourceGovernanceSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String manifest;

    public SourceGovernanceSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        byte[] document;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/source-governance.schema.json"))) {
            document = input.readAllBytes();
        }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(document),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for (String name : java.util.List.of("CatalogueItem", "Listing", "ProcessingQueueItem", "ProcessingQueuePage",
                "ArtifactCatalogueItem", "ArtifactCataloguePage",
                "ArtifactSubmissionInput", "ArtifactSubmissionReceipt",
                "FolderCreateInput", "FolderDecisionInput",
                "FolderView", "FolderList", "FolderReceipt", "OrganizationItemInput", "OrganizationInput",
                "OrganizationReceiptItem", "OrganizationReceipt", "ParsedContentItem", "OriginalContent", "ParsedContent",
                "UsageItem", "UsagePage", "ProcessingInput", "ProcessingReceipt", "LifecycleInput", "LifecycleReceipt",
                "DeletionReceiptInput", "DeletionReceipt", "DeletionItemView", "DeletionJobView",
                "DecisionInput", "BindingInput", "BindingReceipt", "BindingView", "SourceVersionSummary",
                "Review", "View", "Receipt")) definition(name).initializeValidators();
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("source-governance.schema.json", DigestUtils.sha256Hex(document));
        identity.put("backend-design/interfaces.schema.yaml", DigestUtils.sha256Hex(resource("backend-design/interfaces.schema.yaml")));
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    SourceGovernanceProtocol.DecisionInput input(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("DecisionInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.DecisionInput.class);
        WireValues.uuid(result.parse_version()); WireValues.uuid(result.index_version()); WireValues.uuid(result.rights_revision());
        if (result.binding_revision() != null) WireValues.uuid(result.binding_revision());
        WireValues.hash(result.reviewed_manifest_sha256()); PermissionSchema.revision(result.expected_revision());
        ProjectSchema.text(result.reason(), 2000); return result;
    }

    SourceGovernanceProtocol.BindingInput binding(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("BindingInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.BindingInput.class);
        WireValues.uuid(result.source_version()); WireValues.uuid(result.profile_revision());
        WireValues.hash(result.profile_sha256());
        if (result.expected_binding_revision() != null) WireValues.uuid(result.expected_binding_revision());
        ProjectSchema.text(result.reason(), 2000); return result;
    }

    SourceGovernanceProtocol.ProcessingInput processing(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("ProcessingInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.ProcessingInput.class);
        WireValues.uuid(result.project_id()); WireValues.uuid(result.config_revision());
        ProjectSchema.text(result.reason(), 2000); return result;
    }

    SourceGovernanceProtocol.ArtifactSubmissionInput artifactSubmission(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("ArtifactSubmissionInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.ArtifactSubmissionInput.class);
        if (result.target_project_id() != null) WireValues.uuid(result.target_project_id());
        if ("ENTERPRISE".equals(result.ownership()) != (result.target_project_id() == null)) invalid();
        WireValues.uuid(result.maintainer_id()); WireValues.uuid(result.reviewer_id());
        var evidence = new java.util.HashSet<String>();
        for (var item : result.original_evidence()) {
            WireValues.uuid(item.source_version()); WireValues.uuid(item.rights_revision());
            if (!evidence.add(item.source_version())) invalid();
        }
        var projects = new java.util.HashSet<String>();
        for (var project : result.applicable_project_ids()) {
            WireValues.uuid(project); if (!projects.add(project)) invalid();
        }
        if (result.target_project_id() != null && !projects.contains(result.target_project_id())) invalid();
        ProjectSchema.text(result.category(), 32); ProjectSchema.text(result.title(), 512);
        ProjectSchema.text(result.rights().license(), 4096);
        return result;
    }

    SourceGovernanceProtocol.LifecycleInput lifecycle(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("LifecycleInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.LifecycleInput.class);
        if (result.policy_revision() != null) WireValues.uuid(result.policy_revision());
        PermissionSchema.revision(result.expected_revision());
        ProjectSchema.text(result.reason(), 2000); return result;
    }

    SourceGovernanceProtocol.DeletionReceiptInput deletionReceipt(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("DeletionReceiptInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.DeletionReceiptInput.class);
        WireValues.uuid(result.deletion_id()); WireValues.uuid(result.item_id()); WireValues.hash(result.target_sha256());
        PermissionSchema.revision(result.expected_revision()); PermissionSchema.revision(result.expected_lifecycle_revision());
        return result;
    }

    SourceGovernanceProtocol.FolderCreateInput folderCreate(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("FolderCreateInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.FolderCreateInput.class);
        if (result.owner_project_id() != null) WireValues.uuid(result.owner_project_id());
        if (result.parent_id() != null) WireValues.uuid(result.parent_id());
        if ("ENTERPRISE".equals(result.ownership()) != (result.owner_project_id() == null)) invalid();
        ProjectSchema.text(result.category(), 32); ProjectSchema.text(result.name(), 256); return result;
    }

    SourceGovernanceProtocol.FolderDecisionInput folderDecision(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("FolderDecisionInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.FolderDecisionInput.class);
        if (result.target_parent_id() != null) WireValues.uuid(result.target_parent_id());
        PermissionSchema.revision(result.expected_revision());
        if ("RENAME".equals(result.decision())) {
            ProjectSchema.text(result.name(), 256);
            if (result.target_parent_id() != null) invalid();
        } else if (result.name() != null) invalid();
        if ("DELETE".equals(result.decision()) && result.target_parent_id() != null) invalid();
        return result;
    }

    SourceGovernanceProtocol.OrganizationInput organization(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("OrganizationInput", node);
        var result = reader.treeToValue(node, SourceGovernanceProtocol.OrganizationInput.class);
        var ids = new java.util.HashSet<String>();
        for (var item : result.items()) {
            WireValues.uuid(item.item_id()); WireValues.uuid(item.source_version());
            if (!ids.add(item.item_id())) invalid();
            if (item.folder_id() != null) WireValues.uuid(item.folder_id());
            if (item.expected_revision() != null) PermissionSchema.revision(item.expected_revision());
            if ("MOVE".equals(item.decision())) {
                if (item.labels() != null) invalid();
            } else if ("LABEL".equals(item.decision())) {
                if (item.labels() == null || item.folder_id() != null) invalid();
                for (var label : item.labels()) ProjectSchema.text(label, 64);
            } else if (item.folder_id() != null || item.labels() != null || item.expected_revision() != null) invalid();
        }
        return result;
    }

    JsonNode envelope(String definition, JsonNode value, String requestId, String revision) {
        verify(definition, value); WireValues.uuid(requestId);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }

    JsonNode receipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("Receipt", result.required("data")); return result;
    }

    String requestHash(String id, SourceGovernanceProtocol.DecisionInput input, String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST");
        request.put("path", "/api/source-versions/" + id + "/approval-decisions"); request.put("schema_version", "1.0.0");
        request.putNull("query"); request.put("context_header", context); request.put("session_id", session);
        request.set("body", json.tree(input)); return DigestUtils.sha256Hex(json.canonical(request));
    }

    String bindingHash(String projectId, SourceGovernanceProtocol.BindingInput input, String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST");
        request.put("path", "/api/projects/" + projectId + "/source-bindings"); request.put("schema_version", "1.0.0");
        request.putNull("query"); request.put("context_header", context); request.put("session_id", session);
        request.set("body", json.tree(input)); return DigestUtils.sha256Hex(json.canonical(request));
    }

    String processingHash(String sourceVersion, SourceGovernanceProtocol.ProcessingInput input,
            String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST");
        request.put("path", "/api/source-versions/" + sourceVersion + "/processing-attempts");
        request.put("schema_version", "1.0.0"); request.putNull("query");
        request.put("context_header", context); request.put("session_id", session);
        request.set("body", json.tree(input)); return DigestUtils.sha256Hex(json.canonical(request));
    }

    String artifactSubmissionHash(String artifactId, SourceGovernanceProtocol.ArtifactSubmissionInput input,
            String context, String session) throws IOException {
        return requestHash("/api/artifacts/" + artifactId + "/source-submissions", input, context, session);
    }

    String lifecycleHash(String sourceVersion, SourceGovernanceProtocol.LifecycleInput input,
            String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST");
        request.put("path", "/api/source-versions/" + sourceVersion + "/lifecycle-decisions");
        request.put("schema_version", "1.0.0"); request.putNull("query");
        request.put("context_header", context); request.put("session_id", session);
        request.set("body", json.tree(input)); return DigestUtils.sha256Hex(json.canonical(request));
    }

    String folderCreateHash(SourceGovernanceProtocol.FolderCreateInput input, String context, String session) throws IOException {
        return requestHash("/api/source-folders", input, context, session);
    }

    String folderDecisionHash(String folderId, SourceGovernanceProtocol.FolderDecisionInput input,
            String context, String session) throws IOException {
        return requestHash("/api/source-folders/" + folderId + "/decisions", input, context, session);
    }

    String organizationHash(SourceGovernanceProtocol.OrganizationItemInput input, String context, String session) throws IOException {
        return requestHash("/api/source-organization-decisions", input, context, session);
    }

    String deletionReceiptHash(String itemId, SourceGovernanceProtocol.DeletionReceiptInput input,
            String context, String session) throws IOException {
        return requestHash("/api/deletion-items/" + itemId + "/receipts", input, context, session);
    }

    private String requestHash(String path, Object input, String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST"); request.put("path", path);
        request.put("schema_version", "1.0.0"); request.putNull("query"); request.put("context_header", context);
        request.put("session_id", session); request.set("body", json.tree(input));
        return DigestUtils.sha256Hex(json.canonical(request));
    }

    JsonNode bindingReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("BindingReceipt", result.required("data")); return result;
    }

    JsonNode processingReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("ProcessingReceipt", result.required("data")); return result;
    }

    JsonNode artifactSubmissionReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("ArtifactSubmissionReceipt", result.required("data")); return result;
    }

    JsonNode lifecycleReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("LifecycleReceipt", result.required("data")); return result;
    }

    JsonNode folderReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("FolderReceipt", result.required("data")); return result;
    }

    JsonNode organizationReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("OrganizationReceiptItem", result.required("data")); return result;
    }

    JsonNode deletionReceiptResponse(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        verify("DeletionReceipt", result.required("data")); return result;
    }

    String manifestSha256() { return manifest; }
    JsonNode read(String value) throws IOException { return reader.readTree(value); }
    void verify(String definition, JsonNode value) { if (value == null || !definition(definition).validate(value).isEmpty()) invalid(); }

    private JsonSchema definition(String name) {
        return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
    }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
