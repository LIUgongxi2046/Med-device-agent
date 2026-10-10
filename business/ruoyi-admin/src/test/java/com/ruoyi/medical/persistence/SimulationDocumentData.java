package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.DatasetProtocol;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public final class SimulationDocumentData implements AutoCloseable {
    public final String document = id(), initialRevision = id(), accessSet = id(), bodySchema = id();
    public final String sectionNode = id(), fieldDefinition = id(), field = id(), evidenceLink = id();
    public final String rootNode = id(), paragraph = id(), textNode = id();
    public final String source = id(), sourceVersion = id(), parse = id(), chunk = id(), approval = id();
    public final String index = id(), binding = id(), rights = id(), plan = id(), section = id();
    public final String interfacesHash;
    public String template, profile, snapshot, taskRevision, release;
    private final SimulationTaskData tasks;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PermissionSchema json;
    private final TaskInputSchema inputs;
    private final TemplateDefinitionSchema templates;
    private final SourceRightsSchema rightsSchema;
    private final SchemaCatalogue schemas;
    private final String actor;
    private final Path storage;
    private final String quote = "SIMULATION 医疗设备样例长度为 12.500 mm，仅用于功能开发验证。";
    private String dependencyHash;
    private List<String> dependencies;

    public SimulationDocumentData(javax.sql.DataSource dataSource, Path projectRoot, String actor, SimulationTaskData tasks,
            PermissionSchema json, TaskInputSchema inputs, TemplateDefinitionSchema templates, SourceRightsSchema rightsSchema) throws Exception {
        this.tasks = tasks; this.actor = actor; this.json = json; this.inputs = inputs; this.templates = templates; this.rightsSchema = rightsSchema;
        jdbc = new JdbcTemplate(dataSource); transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        schemas = SchemaCatalogue.fromClasspath();
        interfacesHash = schemas.bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256();
        storage = projectRoot.resolve(".implementation-work/runtime/object-storage");
        var current = jdbc.queryForMap("SELECT * FROM task_revision WHERE tenant_id=? AND task_id=?", tasks.tenant, tasks.first);
        template = id(); profile = (String) current.get("profile_revision");
        snapshot = id(); release = (String) current.get("agent_release_id");
    }

    public void initialize() throws Exception {
        var current = jdbc.queryForMap("SELECT * FROM task_revision WHERE tenant_id=? AND task_id=?", tasks.tenant, tasks.first);
        var original = object(quote.getBytes(StandardCharsets.UTF_8), "text/plain");
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            seedSource(original); seedGrants(); seedTemplate(); seedTaskAndPlan(current); seedDependencies(); return null;
        }).get());
        var body = body(false); byte[] bodyBytes = canonical(body); var originalBody = object(bodyBytes, "application/json");
        var fields = object(canonical(new InterfaceProtocol.DocumentFieldProjection("1.0.0", initialRevision, originalBody.sha256(), body.fields())), "application/json");
        var evidence = object(canonical(new InterfaceProtocol.DocumentEvidenceProjection("1.0.0", initialRevision, originalBody.sha256(), body.evidenceLinks(), body.factUsages())), "application/json");
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            seedDocument(originalBody, fields, evidence); return null;
        }).get());
    }

    public byte[] updatedBody() throws Exception { return canonical(body(true)); }

    private InterfaceProtocol.DocumentBody body(boolean edited) {
        var ids = edited ? List.of(field) : List.<String>of(); var links = edited ? List.of(evidenceLink) : List.<String>of();
        var nodes = List.of(new InterfaceProtocol.Node(rootNode, null, 0, List.of(), List.of(),
                new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT, "SIMULATION 综述资料")),
            new InterfaceProtocol.Node(sectionNode, rootNode, 0, List.of(), List.of(),
                new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.SECTION, "SIMULATION 产品描述")),
            new InterfaceProtocol.Node(paragraph, sectionNode, 0, List.of(), List.of(),
                new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.PARAGRAPH, "SIMULATION 尺寸描述")),
            new InterfaceProtocol.Node(textNode, paragraph, 0, ids, links,
                new InterfaceProtocol.TextContent("TEXT", edited ? quote + "人工编辑记录：保留原始精度。" : "SIMULATION 待编辑正文", List.of())));
        var values = edited ? List.of(new InterfaceProtocol.FieldValue(field, textNode, fieldDefinition, "12.500", "12.500", "mm",
                "SIMULATION-MODEL-A", "SIMULATION-ROOM-TEMPERATURE", links)) : List.<InterfaceProtocol.FieldValue>of();
        var locator = new DatasetProtocol.TextLocator("TXT", "UTF-8", "PRESERVE", 0, quote.codePointCount(0, quote.length()), 1, 1);
        var reference = new DatasetProtocol.EvidenceRef("1.0.0", tasks.tenant, tasks.project, source, sourceVersion, parse, chunk,
            DatasetProtocol.LocatorKind.TXT, locator, DigestUtils.sha256Hex(quote.getBytes(StandardCharsets.UTF_8)), approval);
        return new InterfaceProtocol.DocumentBody("1.0.0", template, nodes, values,
            edited ? List.of(new InterfaceProtocol.EvidenceLink(evidenceLink, List.of(textNode), InterfaceProtocol.Relation.SUPPORT, reference)) : List.of(), List.of());
    }

    private void seedSource(InterfaceProtocol.ObjectRef original) throws Exception {
        insert("source", row("source_id", source, "owner_project_id", null, "ownership", "ENTERPRISE", "title", "SIMULATION 设备尺寸与格式资料", "classification", "INTERNAL"));
        version(sourceVersion, null, "SOURCE_VERSION", original.sha256());
        insert("source_version", metadata(row("source_id", source, "source_version", sourceVersion, "version_no", 1, "original_sha256", original.sha256(),
            "object_key", original.objectKey(), "object_version", original.objectVersion(), "media_type", original.mediaType(), "size_bytes", original.sizeBytes(),
            "publisher_version", "SIMULATION-1", "provenance", serialize(row("data_kind", "SIMULATION", "purpose", "DOCUMENT_DEVELOPMENT")), "rights_revision", rights), false));
        var rightsBody = row("schema_version", "1.0.0", "tenant_id", tasks.tenant, "rights_revision", rights, "source_version", sourceVersion,
            "purpose", "DOCUMENT_BODY", "allowed_actions", List.of("READ", "STORE", "ENTERPRISE_USE"), "providers", List.of(), "regions", List.of("LOCAL"),
            "valid_from", null, "valid_to", null, "classification", "INTERNAL", "evidence_manifest", row("owner", "SIMULATION 样例企业",
                "license", "SIMULATION 功能开发资料授权", "environments", List.of("LOCAL"), "classifications", List.of("INTERNAL"), "authorization_evidence", List.of(original)));
        String rightsHash = hash(rightsBody); version(rights, null, "RIGHTS_REVISION", rightsHash);
        insert("rights_revision", schema(metadata(row("rights_revision", rights, "source_version", sourceVersion, "purpose", "DOCUMENT_BODY",
            "allowed_actions", serialize(rightsBody.get("allowed_actions")), "providers", "[]", "regions", "[\"LOCAL\"]", "valid_from", null, "valid_to", null,
            "status", "APPROVED", "evidence_manifest", serialize(rightsBody.get("evidence_manifest")), "content_sha256", rightsHash), true), rightsSchema.manifestSha256()));
        insert("parse_version", row("source_version", sourceVersion, "parse_version", parse, "processor_manifest", serialize(row("data_kind", "SIMULATION", "processor", "UTF-8")),
            "content_sha256", original.sha256(), "artifact_key", original.objectKey(), "parse_status", "COMPLETE", "failure_manifest", "[]"));
        var locator = new DatasetProtocol.TextLocator("TXT", "UTF-8", "PRESERVE", 0, quote.codePointCount(0, quote.length()), 1, 1);
        insert("source_chunk", row("source_version", sourceVersion, "parse_version", parse, "chunk_id", chunk, "ordinal", 0, "node_id", id(),
            "locator", serialize(locator), "quote_hash", original.sha256(), "raw_text", quote, "search_text", quote));
        insert("index_manifest", row("source_version", sourceVersion, "parse_version", parse, "index_version", index, "mode", "FULLTEXT", "model_config_revision", null,
            "dimension", null, "config_sha256", hash(row("mode", "FULLTEXT")), "manifest_sha256", original.sha256(), "expected_count", 1, "verified_count", 1, "status", "READY"));
        insert("approval_decision", row("source_version", sourceVersion, "parse_version", parse, "approval_revision", approval, "decision", "APPROVE", "reviewer_id", actor,
            "reviewed_manifest_sha256", original.sha256(), "reason", "SIMULATION 开发资料批准", "decided_at", tasks.updated));
        insert("source_use_gate", row("source_version", sourceVersion, "parse_version", parse, "approval_revision", approval,
            "upload_status", "RECEIVED", "approval_status", "APPROVED", "lifecycle", "ACTIVE", "revision", 1));
        insert("project_source_binding", row("project_id", tasks.project, "source_version", sourceVersion, "binding_revision", binding,
            "applicability", "APPLICABLE", "decision_manifest", serialize(row("data_kind", "SIMULATION", "reason", "开发资料适用"))));
    }

    private void seedGrants() throws Exception {
        for (String action : List.of("document.read", "document.write", "task.read", "project.read", "dependency.read", "template.read", "template.write", "configuration.read", "artifact.read", "source.read", "plan.confirm")) {
            var condition = row("schema_version", "1.0.0", "identity_modes", List.of("local"), "purposes", List.of("DOCUMENT_BODY"),
                "delegation", row("actions", List.of(), "role_ids", List.of(), "resource_kinds", List.of(), "project_ids", List.of(), "resource_ids", List.of()));
            json.condition(serialize(condition));
            insert("permission_grant", schema(metadata(row("grant_id", id(), "subject_id", actor, "project_id", null, "resource_kind", "ENTERPRISE", "resource_id", tasks.tenant,
                "action", action, "condition", serialize(condition), "status", "ACTIVE", "valid_from", tasks.updated.minusDays(1), "basis_version", sourceVersion,
                "revision", 1, "updated_at", tasks.updated), true), json.manifestSha256()));
        }
    }

    private void seedTemplate() throws Exception {
        var basis = new PermissionProtocol.Basis(sourceVersion, jdbc.queryForObject("SELECT original_sha256 FROM source_version WHERE tenant_id=? AND source_version=?", String.class, tasks.tenant, sourceVersion));
        var always = new TemplateProtocol.Condition("ALWAYS", null, null, List.of());
        var rule = new TemplateProtocol.EvidenceRule("NARRATIVE", List.of(), List.of(), null, List.of(), true, true);
        var node = new TemplateProtocol.Node(sectionNode, null, "SIMULATION.1", "SIMULATION 产品描述", 1, always, true, "NARRATIVE", rule, List.of(basis), "ENTERPRISE_SUPPLEMENT", List.of("保留设备型号、单位、条件与原始数值"));
        var definition = new TemplateProtocol.Field(fieldDefinition, sectionNode, "length", "SIMULATION 长度", "样例设备的长度", "NUMBER",
            new TemplateProtocol.Constraints(null, 12, null, null, "mm", List.of(basis), List.of(), List.of()), always, true, rule,
            new TemplateProtocol.OutputMapping(sectionNode, null, null, false, 1), null, null, List.of(basis));
        var content = new TemplateProtocol.Content("1.0.0", "OVERVIEW", "FIRST_REGISTRATION", false, null, List.of(node), List.of(definition), List.of(), List.of(), List.of(),
            new TemplateProtocol.ConditionSchema(List.of(), always, "UNKNOWN"), List.of(basis),
            new TemplateProtocol.FormatProfile(basis, List.of("DOCX", "PDF"), basis, List.of(basis), List.of(), "DECLARED_ORDINAL"));
        var validated = templates.version(canonical(new TemplateProtocol.VersionInput("1.0.0", null, "urn:medical:templates:1.0.0#/$defs/Content", content, "SIMULATION 专业模板开发资料")));
        version(template, null, "TEMPLATE_VERSION", validated.content_sha256());
        insert("template_version", schema(templateMetadata(row("template_version", template, "document_type", "OVERVIEW", "application_path", "FIRST_REGISTRATION", "status", "APPROVED",
            "basis_manifest", serialize(content.basis_manifest()), "format_profile", serialize(content.format_profile()), "condition_schema", serialize(content.condition_schema()),
            "content_sha256", validated.content_sha256())), validated.schema_manifest_sha256()));
        String grant = grant("template.write");
        insert("template_definition", schema(templateMetadata(row("template_version", template, "content_json", validated.canonical_content(), "content_sha256", validated.content_sha256(),
            "base_version", null, "base_version_sha256", null, "reason", "SIMULATION 专业模板开发资料", "authority_grant", grant, "authority_revision", 1, "revision", 1)), validated.schema_manifest_sha256()));
        insert("requirement_node", schema(templateMetadata(row("template_version", template, "node_id", sectionNode, "parent_id", null, "official_number", node.official_number(), "title", node.title(),
            "ordinal", 1, "condition", serialize(always), "required", true, "content_mode", "NARRATIVE", "evidence_rule", serialize(rule))), validated.schema_manifest_sha256()));
        insert("field_definition", schema(templateMetadata(row("template_version", template, "field_id", fieldDefinition, "node_id", sectionNode, "value_type", "NUMBER", "constraints", serialize(definition.constraints()),
            "condition", serialize(always), "evidence_rule", serialize(rule), "output_mapping", serialize(definition.output_mapping()))), validated.schema_manifest_sha256()));
        insert("template_definition_dependency", schema(templateMetadata(row("template_version", template, "upstream_version", sourceVersion, "upstream_sha256", basis.content_sha256(), "ordinal", 1)), validated.schema_manifest_sha256()));
        edge(template, sourceVersion, "TEMPLATE_DEFINITION");
    }

    private void seedTaskAndPlan(Map<String,Object> original) throws Exception {
        var member = row("source_id", source, "source_version", sourceVersion, "parse_version", parse, "index_version", index, "approval_revision", approval,
            "binding_revision", binding, "rights_revision", rights, "original_sha256", jdbc.queryForObject("SELECT original_sha256 FROM source_version WHERE tenant_id=? AND source_version=?", String.class, tasks.tenant, sourceVersion));
        var scope = row("mode", "SOURCE_VERSIONS", "collection_versions", List.of(), "source_versions", List.of(sourceVersion), "network_enabled", false, "network_policy_revision", null, "approved_query_revision", null);
        var manifest = row("schema_version", "1.0.0", "tenant_id", tasks.tenant, "project_id", tasks.project, "snapshot_id", snapshot, "profile_revision", profile,
            "scope_selection", scope, "auth_epoch", "1", "governance_epoch", "1", "members", List.of(member));
        version(snapshot, tasks.project, "SCOPE_SNAPSHOT", hash(manifest));
        insert("scope_snapshot", row("project_id", tasks.project, "snapshot_id", snapshot, "manifest", serialize(manifest), "manifest_sha256", hash(manifest),
            "auth_epoch", 1, "governance_epoch", 1, "created_at", tasks.updated));
        insert("scope_member", row("project_id", tasks.project, "snapshot_id", snapshot, "source_id", source, "source_version", sourceVersion, "parse_version", parse,
            "index_version", index, "approval_revision", approval, "binding_revision", binding));
        String oldTask = (String) original.get("task_revision");
        var request = inputs.read(jdbc.queryForObject("SELECT request_json FROM task_input_revision WHERE tenant_id=? AND task_revision=?", String.class, tasks.tenant, oldTask));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).set("scope_selection", json.tree(scope));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("template_version", template);
        var validated = inputs.create(canonical(request));
        taskRevision = id(); version(taskRevision, tasks.project, "TASK_REVISION", validated.request_sha256());
        insert("task_revision", metadata(row("project_id", tasks.project, "task_id", tasks.first, "task_revision", taskRevision, "profile_revision", profile, "snapshot_id", snapshot,
            "template_version", template, "agent_release_id", release, "parent_revision", oldTask, "plan_revision", null, "reason", "SIMULATION 计划确认", "content_sha256", validated.request_sha256()), true));
        var candidate = new ArtifactProtocol.PlanCandidate(template, profile, List.of(sectionNode),
            List.of(new ArtifactProtocol.PlanSection(section, List.of(sectionNode), "SIMULATION 完成尺寸描述", actor, ArtifactProtocol.Method.HUMAN_PROVIDED, List.of(), List.of())), List.of(), List.of());
        var bound = TypedSchemaBindings.bind(schemas, ArtifactBindings.PLAN_CANDIDATE, json.tree(candidate)); String planHash = DigestUtils.sha256Hex(bound.payload().canonicalBytes());
        var previousCandidate = new ArtifactProtocol.PlanCandidate((String) original.get("template_version"), profile, List.of(), List.of(), List.of(), List.of());
        String previousHash = DigestUtils.sha256Hex(canonical(previousCandidate));
        String previousPlan = id(), decision = id(), operation = id();
        for (String current : List.of(previousPlan, plan)) {
            boolean confirmed = current.equals(plan); String contentHash = confirmed ? planHash : previousHash;
            version(current, tasks.project, "PLAN_REVISION", contentHash);
            insert("plan_revision", metadata(row("project_id", tasks.project, "plan_revision", current, "task_id", tasks.first, "task_revision", current.equals(plan) ? taskRevision : oldTask,
                "template_version", confirmed ? template : original.get("template_version"), "snapshot_id", confirmed ? snapshot : original.get("snapshot_id"),
                "status", confirmed ? "CONFIRMED" : "DRAFT", "decision_id", null, "content_sha256", contentHash), true));
        }
        var simulation = row("data_kind", "SIMULATION", "purpose", "DOCUMENT_DEVELOPMENT"); String marked = serialize(simulation), markedHash = hash(simulation);
        insert("operation_record", schema(metadata(row("operation_id", operation, "subject_id", actor, "operation_name", "SIMULATION_PLAN_CONFIRMATION", "idempotency_key", operation,
            "request_sha256", markedHash, "state", "COMPLETED", "http_status", 200, "response_ref", marked, "target_id", tasks.first, "revision", 1, "updated_at", tasks.updated), true), json.manifestSha256()));
        insert("task_plan_decision", schema(metadata(row("project_id", tasks.project, "task_id", tasks.first, "decision_id", decision, "operation_id", operation,
            "target_plan_revision", previousPlan, "target_plan_sha256", previousHash, "basis_task_revision", oldTask, "resulting_task_revision", taskRevision, "resulting_plan_revision", plan,
            "decision", "CONFIRM", "from_status", "PLAN_PENDING", "to_status", "READY", "expected_revision", 1, "resulting_revision", 2, "reason", "SIMULATION 开发计划批准",
            "request_json", marked, "baseline_manifest", marked, "baseline_sha256", markedHash, "dependency_authorizations", "[]", "receipt_json", marked, "receipt_sha256", markedHash,
            "authority_grant", grant("plan.confirm"), "authority_revision", 1, "auth_epoch", 1, "governance_epoch", 1), true), json.manifestSha256()));
        jdbc.update("UPDATE plan_revision SET decision_id=? WHERE tenant_id=? AND plan_revision=?", decision, tasks.tenant, plan);
        jdbc.update("UPDATE task_revision SET plan_revision=? WHERE tenant_id=? AND task_revision=?", plan, tasks.tenant, taskRevision);
        jdbc.update("UPDATE task_head SET current_revision=?,status='READY',revision=2 WHERE tenant_id=? AND task_id=?", taskRevision, tasks.tenant, tasks.first);
        insert("plan_content_revision", schema(metadata(row("project_id", tasks.project, "task_id", tasks.first, "task_revision", taskRevision, "plan_revision", plan, "template_version", template,
            "snapshot_id", snapshot, "content_sha256", planHash, "content_json", bound.payload().canonicalJson(), "base_version", null, "base_version_sha256", null,
            "operation_id", operation, "expected_revision", 1, "reason", "SIMULATION 开发计划", "schema_ref", bound.payload().schemaRef(),
            "authority_grant", grant("plan.confirm"), "authority_revision", 1, "auth_epoch", 1, "governance_epoch", 1), true), json.manifestSha256()));
        insert("plan_section", schema(metadata(row("project_id", tasks.project, "plan_revision", plan, "section_id", section, "template_version", template, "owner_id", actor,
            "goal", candidate.sections().get(0).goal(), "requirements", serialize(List.of(sectionNode)), "evidence_scope", "[]", "method", "HUMAN_PROVIDED", "position", 0), true), json.manifestSha256()));
        insert("plan_section_node", metadata(row("project_id", tasks.project, "plan_revision", plan, "section_id", section, "template_version", template, "node_id", sectionNode, "position", 0), true));
    }

    private void seedDependencies() throws Exception {
        dependencies = List.of(taskRevision, plan, snapshot, profile, template, release, sourceVersion).stream().sorted().toList();
        for (String version : dependencies) if (!version.equals(template) && !version.equals(sourceVersion)) edge(version, sourceVersion, "SIMULATION_DOCUMENT_INPUT");
        var members = dependencies.stream().map(value -> new ArtifactRegistryProtocol.MemberRow(value, rights)).toList();
        var manifest = new ArtifactRegistryProtocol.DependencyManifest("1.0.0", tasks.tenant, tasks.project, accessSet, members); dependencyHash = hash(manifest);
        version(accessSet, tasks.project, "DEPENDENCY_ACCESS_SET", dependencyHash);
        insert("dependency_access_set", metadata(row("project_id", tasks.project, "access_set_id", accessSet, "manifest_sha256", dependencyHash, "content_sha256", dependencyHash), true));
        insert("dependency_access_manifest", schema(metadata(row("project_id", tasks.project, "access_set_id", accessSet, "manifest_json", serialize(manifest), "manifest_sha256", dependencyHash,
            "content_sha256", dependencyHash, "member_count", members.size()), true), new DependencyManifestSchema().manifestSha256()));
        for (String upstream : dependencies) {
            insert("dependency_access_member", metadata(row("access_set_id", accessSet, "upstream_version_id", upstream, "rights_revision", rights), true));
            edge(accessSet, upstream, "SIMULATION_DOCUMENT_INPUT");
        }
    }

    private void seedDocument(InterfaceProtocol.ObjectRef body, InterfaceProtocol.ObjectRef fields, InterfaceProtocol.ObjectRef evidence) throws Exception {
        DocumentBodySemantics.verify(schemas, json.tree(body(false)), new DocumentBodySemantics.Limits(1000, 1000, 10000));
        version(bodySchema, null, "DOCUMENT_BODY_SCHEMA", interfacesHash);
        insert("document_head", metadata(row("project_id", tasks.project, "document_id", document, "current_document_revision", null, "task_id", tasks.first, "status", "DRAFT", "revision", 1, "updated_at", tasks.updated), true));
        version(initialRevision, tasks.project, "DOCUMENT_REVISION", body.sha256());
        insert("document_revision", row("project_id", tasks.project, "document_revision", initialRevision, "document_id", document, "snapshot_id", snapshot, "body_sha256", body.sha256(), "body_object_key", body.objectKey()));
        String bodyId = artifact(body, "DOCUMENT_BODY", "DocumentBody"), fieldsId = artifact(fields, "DOCUMENT_FIELDS", "DocumentFieldProjection"), evidenceId = artifact(evidence, "DOCUMENT_EVIDENCE", "DocumentEvidenceProjection");
        insert("document_revision_meta", metadata(row("project_id", tasks.project, "document_revision", initialRevision, "document_id", document, "task_id", tasks.first, "task_revision", taskRevision,
            "snapshot_id", snapshot, "body_object_version", body.objectVersion(), "schema_revision", "1.0.0", "body_schema_revision", bodySchema, "body_artifact_id", bodyId,
            "body_sha256", body.sha256(), "plan_revision", plan, "template_version", template, "issue_watermark", 0, "dependency_access_set_id", accessSet, "content_sha256", body.sha256()), true));
        insert("document_revision_state", metadata(row("project_id", tasks.project, "document_revision", initialRevision, "issue_watermark", 0, "evidence_watermark", 0, "revision", 1, "updated_at", tasks.updated), true));
        jdbc.update("UPDATE document_head SET current_document_revision=? WHERE tenant_id=? AND document_id=?", initialRevision, tasks.tenant, document);
        for (var node : body(false).nodes()) insert("document_node", metadata(row("project_id", tasks.project, "document_revision", initialRevision, "node_id", node.nodeId(), "parent_id", node.parentId(),
            "ordinal", node.ordinal(), "node_kind", json.tree(node.content()).required("kind").textValue(), "node_sha256", hash(node)), true));
        for (String upstream : dependencies) edge(initialRevision, upstream, "SIMULATION_DOCUMENT_INPUT");
        if (bodyId.equals(fieldsId) || bodyId.equals(evidenceId) || fieldsId.equals(evidenceId)) throw new IllegalStateException("SIMULATION_PROJECTION_IDENTITY_INVALID");
    }

    private String artifact(InterfaceProtocol.ObjectRef object, String kind, String definition) throws Exception {
        String artifact = id(); version(artifact, tasks.project, "ARTIFACT", object.sha256());
        insert("artifact", schema(metadata(row("project_id", tasks.project, "artifact_id", artifact, "document_revision", initialRevision, "artifact_kind", kind, "object_ref", StoredObjectReferences.encode(object),
            "object_key", object.objectKey(), "object_version", object.objectVersion(), "sha256", object.sha256(), "size_bytes", object.sizeBytes(), "media_type", object.mediaType(),
            "dependency_access_set_id", accessSet, "content_sha256", object.sha256()), true), interfacesHash));
        insert("artifact_content_binding", metadata(row("project_id", tasks.project, "artifact_id", artifact, "resource_kind", "DOCUMENT", "resource_id", document,
            "schema_ref", schemas.schemaRef("interfaces", definition), "schema_document_sha256", interfacesHash, "dependency_access_set_id", accessSet, "dependency_manifest_sha256", dependencyHash), true));
        for (String upstream : dependencies) edge(artifact, upstream, "SIMULATION_DOCUMENT_INPUT"); return artifact;
    }

    private InterfaceProtocol.ObjectRef object(byte[] bytes, String mediaType) throws Exception {
        var deployment = json.tree(new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(storage.resolve("deployment.json"))));
        var credentials = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(storage.resolve("service-secret.json")));
        if (!deployment.required("service_id").equals(credentials.required("service_id"))) throw new IllegalStateException("SIMULATION_OBJECT_SERVICE_MISMATCH");
        Path spool = Files.createDirectories(storage.resolve("document-simulation-" + document), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path content = Files.createTempFile(spool, "content-", ".json", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(content, bytes);
        try (var writer = new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(), "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK,
                StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.required("access_key").textValue(), credentials.required("secret_key").textValue())), spool, 1048576, 1048576)) {
            return writer.writeVerified(new S3ObjectWriter.WriteIntent(id(), "simulation/documents/" + tasks.tenant + "/" + document + "/" + id(), DigestUtils.sha256Hex(bytes), String.valueOf(bytes.length), mediaType), content,
                () -> { if (jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member m JOIN project p ON p.tenant_id=m.tenant_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND p.project_id=? AND p.status='ACTIVE'", Integer.class,
                        tasks.tenant, actor, tasks.project) != 1) throw new IllegalStateException("SIMULATION_INPUT_AUTHORITY_UNAVAILABLE"); });
        } finally { Files.delete(content); }
    }

    private String grant(String action) { return jdbc.queryForObject("SELECT grant_id FROM permission_grant WHERE tenant_id=? AND action=? AND resource_kind='ENTERPRISE'", String.class, tasks.tenant, action); }
    private byte[] canonical(Object value) throws Exception { return json.canonical(json.tree(value)).getBytes(StandardCharsets.UTF_8); }
    private String serialize(Object value) throws Exception { return json.canonical(json.tree(value)); }
    private String hash(Object value) throws Exception { return DigestUtils.sha256Hex(canonical(value)); }
    private void edge(String consumer, String upstream, String kind) { insert("lineage_edge", row("consumer_version_id", consumer, "upstream_version_id", upstream, "relation_kind", kind, "actor_id", actor, "decision_ref", null, "created_at", tasks.updated)); }
    private void version(String version, String project, String kind, String hash) { insert("version_registry", row("version_id", version, "owner_project_id", project, "entity_kind", kind, "content_sha256", hash)); }
    private Map<String,Object> metadata(Map<String,Object> value, boolean classification) { value.put("created_by", actor); value.put("created_at", tasks.updated); if (classification) value.put("classification", "INTERNAL"); return value; }
    private Map<String,Object> templateMetadata(Map<String,Object> value) { metadata(value, true); value.put("classification", "LOCAL_AUTH_METADATA"); return value; }
    private Map<String,Object> schema(Map<String,Object> value, String hash) { value.put("schema_version", "1.0.0"); value.put("schema_manifest_sha256", hash); return value; }
    private void insert(String table, Map<String,Object> value) {
        value.put("tenant_id", tasks.tenant);
        jdbc.update("INSERT INTO " + table + " (" + String.join(",", value.keySet().stream().map(key -> "`" + key + "`").toList()) + ") VALUES ("
            + String.join(",", Collections.nCopies(value.size(), "?")) + ")", value.values().toArray());
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Map<String,Object> row(Object... pairs) {
        var result = new LinkedHashMap<String,Object>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]); return result;
    }

    @Override public void close() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE document_head SET current_document_revision=NULL WHERE tenant_id=?", tasks.tenant);
            jdbc.update("UPDATE artifact SET document_revision=NULL WHERE tenant_id=?", tasks.tenant);
            jdbc.update("UPDATE task_revision SET plan_revision=NULL WHERE tenant_id=?", tasks.tenant);
            jdbc.update("UPDATE plan_revision SET decision_id=NULL WHERE tenant_id=?", tasks.tenant);
            for (String table : List.of("document_evidence_projection", "evidence_link", "claim", "fact_usage", "field_value")) jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", tasks.tenant);
            var nodes = jdbc.queryForList("SELECT document_revision,node_id,parent_id FROM document_node WHERE tenant_id=?", tasks.tenant);
            var graph = new org.jgrapht.graph.DirectedAcyclicGraph<String,org.jgrapht.graph.DefaultEdge>(org.jgrapht.graph.DefaultEdge.class);
            nodes.forEach(node -> graph.addVertex(node.get("document_revision") + ":" + node.get("node_id")));
            nodes.forEach(node -> { if (node.get("parent_id") != null) graph.addEdge(node.get("document_revision") + ":" + node.get("parent_id"), node.get("document_revision") + ":" + node.get("node_id")); });
            var ordered = new ArrayList<String>(); var iterator = new org.jgrapht.traverse.TopologicalOrderIterator<>(graph); iterator.forEachRemaining(ordered::add); Collections.reverse(ordered);
            for (String identity : ordered) {
                var node = nodes.stream().filter(value -> identity.equals(value.get("document_revision") + ":" + value.get("node_id"))).findFirst().orElseThrow();
                jdbc.update("DELETE FROM document_node WHERE tenant_id=? AND document_revision=? AND node_id=?", tasks.tenant, node.get("document_revision"), node.get("node_id"));
            }
            for (String table : List.of("document_section", "section_revision", "document_revision_state",
                    "content_read_range", "content_read_receipt", "object_upload_part", "artifact_upload_binding", "object_upload_session", "artifact_upload_grant", "document_revision_meta", "document_revision", "document_head",
                    "plan_content_revision", "plan_section_node", "plan_section", "task_plan_decision", "plan_revision", "artifact_content_binding", "lineage_edge", "dependency_access_member", "dependency_access_manifest",
                    "scope_member", "project_source_binding", "source_use_gate", "approval_decision", "index_manifest", "source_chunk", "parse_version", "rights_revision", "source_version", "source",
                    "template_definition_dependency", "field_definition", "requirement_node", "template_definition")) jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", tasks.tenant);
            jdbc.update("DELETE FROM task_input_revision WHERE tenant_id=? AND task_revision=?", tasks.tenant, taskRevision);
            jdbc.update("UPDATE task_head SET current_revision=NULL WHERE tenant_id=? AND current_revision=?", tasks.tenant, taskRevision);
            jdbc.update("DELETE FROM task_revision WHERE tenant_id=? AND task_revision=?", tasks.tenant, taskRevision);
        });
    }
}
