package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.DocumentEvidenceBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DocumentBodyDomainAuthority {
    private final TemplateVersionStore templates;
    private final UploadIssuanceMapper mapper;
    private final PermissionSchema json;
    private final SharedFactConsumptionAuthority facts;
    public DocumentBodyDomainAuthority(TemplateVersionStore templates, UploadIssuanceMapper mapper, PermissionSchema json, SharedFactConsumptionAuthority facts) {
        this.templates = templates; this.mapper = mapper; this.json = json; this.facts=facts;
    }
    public record Chunk(String locator, String quoteHash, String rawText) { }

    void require(EnterpriseAccess.Admission admission, UploadAuthorizationAuthority.VerifiedDocument current,
            SchemaCatalogue schemas, JsonNode value) throws IOException, NoSuchAlgorithmException {
        var body = DocumentEvidenceBindings.bind(schemas, InterfaceBindings.DOCUMENT_BODY, value,current.publishedSchema().schemaRef(),current.publishedSchema().schemaSha256()).read();
        if (!body.templateVersion().equals(current.basis().taskRevision().templateVersion())) mismatch();
        var template = templates.applicationTarget(admission, body.templateVersion(), "DOCUMENT_BODY").content();
        var definitions = new HashMap<String,TemplateProtocol.Node>();
        template.nodes().forEach(node -> definitions.put(node.node_id(), node));
        var nodes = new HashMap<String,InterfaceProtocol.Node>();
        body.nodes().forEach(node -> nodes.put(node.nodeId(), node));
        for (var node : body.nodes()) {
            if (node.content() instanceof InterfaceProtocol.ContainerContent content
                    && content.kind() == InterfaceProtocol.ContainerKind.SECTION) {
                var definition = definitions.get(node.nodeId());
                if (definition == null) mismatch();
                var parent = nodes.get(node.parentId());
                String expectedParent = definition.parent_id();
                if (expectedParent == null) {
                    if (parent == null || !(parent.content() instanceof InterfaceProtocol.ContainerContent container)
                            || container.kind() != InterfaceProtocol.ContainerKind.DOCUMENT) mismatch();
                } else if (!expectedParent.equals(node.parentId())) mismatch();
            }
            if (node.content() instanceof InterfaceProtocol.ImageContent image) {
                var artifact = image(current, image.artifactId());
                if (!image.sha256().equals(artifact.sha256())) mismatch();
            }
            if (node.content() instanceof InterfaceProtocol.FormulaContent formula && formula.renderedArtifactId() != null)
                image(current, formula.renderedArtifactId());
        }
        var fields = new HashMap<String,TemplateProtocol.Field>();
        template.field_definitions().forEach(field -> fields.put(field.field_id(), field));
        for (var field : body.fields()) {
            var definition = fields.get(field.fieldDefinitionId());
            if (definition == null || !definition.node_id().equals(section(nodes, field.nodeId()))
                    || !definition.output_mapping().node_id().equals(definition.node_id())) mismatch();
        }
        for (var link : body.evidenceLinks()) {
            var evidence = link.evidence();
            if (!admission.context().tenant_id().equals(evidence.tenantId())
                    || !current.document().projectId().equals(evidence.projectId())) mismatch();
            var source = current.basis().verifiedSources().get(evidence.sourceVersion());
            if (source == null || !source.source().sourceId().equals(evidence.sourceId()) || source.selection() == null
                    || !source.selection().parseVersion().equals(evidence.parseVersion())
                    || !source.selection().approvalRevision().equals(evidence.approvalRevision())) mismatch();
            var parameters = Map.<String,Object>of("userId", admission.subject().user().user_id(),
                "sourceVersion", evidence.sourceVersion(), "parseVersion", evidence.parseVersion(), "chunkId", evidence.chunkId());
            TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper.sourceChunk", parameters);
            var chunks = mapper.sourceChunk(parameters);
            if (chunks.size() != 1) mismatch();
            var chunk = chunks.get(0);
            var locator = JsonMapper.builder().build().readTree(Objects.requireNonNull(chunk.locator()));
            if (!evidence.quoteHash().equals(chunk.quoteHash())
                    || !chunk.quoteHash().equals(WireValues.sha256(chunk.rawText().getBytes(StandardCharsets.UTF_8)))
                    || !evidence.locatorKind().name().equals(locator.required("kind").textValue())
                    || !json.canonical(json.tree(evidence.locator())).equals(json.canonical(locator))) mismatch();
        }
        for (var usage : body.factUsages()) {
            facts.requireConsumable(admission,current.document().projectId(),usage.factVersionId());
            var fact = current.dependencies().versions().get(usage.factVersionId());
            if (fact == null || fact.domain() != VersionResourceProtocol.Domain.SHARED_FACT
                    || !current.document().projectId().equals(fact.owner().resourceProjectId())) mismatch();
        }
    }

    private static String section(Map<String,InterfaceProtocol.Node> nodes, String id) {
        var node = nodes.get(id);
        while (node != null) {
            if (node.content() instanceof InterfaceProtocol.ContainerContent content
                    && content.kind() == InterfaceProtocol.ContainerKind.SECTION) return node.nodeId();
            node = nodes.get(node.parentId());
        }
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static ArtifactRegistryProtocol.ArtifactRow image(UploadAuthorizationAuthority.VerifiedDocument current, String id) {
        var version = current.dependencies().versions().get(id);
        if (version == null || version.domain() != VersionResourceProtocol.Domain.ARTIFACT
                || version.artifactRegistration() == null) mismatch();
        var artifact = version.artifactRegistration().artifact();
        if (!artifact.mediaType().startsWith("image/")) mismatch();
        return artifact;
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
