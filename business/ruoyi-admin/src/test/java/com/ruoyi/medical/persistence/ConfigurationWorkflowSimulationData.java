package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Narrow synthetic authorization fixture. It never creates agents, releases, tasks or source approvals. */
public final class ConfigurationWorkflowSimulationData implements AutoCloseable {
    public final String tenant = id(), otherTenant = id(), artifact = id();
    public final String artifactHash;
    private final String actor, outsider, basis = id(), price = id(), policy = id(), accessSet = id();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PermissionSchema json;
    private final Path artifactFile;
    private final LocalDateTime now;
    private final String basisHash, priceHash, policyHash;

    public ConfigurationWorkflowSimulationData(javax.sql.DataSource source, Path root, String actor, String outsider,
            PermissionSchema json) throws Exception {
        this.actor = actor; this.outsideCheck(outsider); this.outsider = outsider; this.json = json;
        jdbc = new JdbcTemplate(source); transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        now = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        basisHash = hash(json.tree(Map.of("data_kind", "SIMULATION", "purpose", "CONFIGURATION_HTTP_AUTHORIZATION")));
        priceHash = hash(json.tree(Map.of("data_kind", "SIMULATION", "purpose", "UNAPPROVED_PRICE_REFERENCE")));
        policyHash = hash(json.tree(Map.of("data_kind", "SIMULATION", "purpose", "NO_PROVIDER_CALLS")));
        artifactFile = root.resolve(".implementation-work/configuration-workflow-simulation/" + tenant + "/basis.json");
        Files.createDirectories(artifactFile.getParent());
        var text = json.tree(new InterfaceProtocol.TextContent("TEXT", "SIMULATION self-authored rule test evidence; no clinical claim or external source.", List.of()));
        SchemaCatalogue.fromClasspath().bind("interfaces", text, "TextContent");
        byte[] bytes = json.canonical(text).getBytes(StandardCharsets.UTF_8);
        Files.write(artifactFile, bytes); artifactHash = DigestUtils.sha256Hex(bytes);
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            seed(root, bytes); return null;
        }).get());
    }

    private void outsideCheck(String user) { if (actor.equals(user)) throw new IllegalArgumentException("TWO_DISTINCT_SIMULATION_USERS_REQUIRED"); }

    private void seed(Path root, byte[] bytes) throws Exception {
        for (String scope : List.of(tenant, otherTenant)) insert("tenant", row("tenant_id", scope, "auth_epoch", 1, "governance_epoch", 1));
        member(tenant, actor); member(otherTenant, outsider);
        for (String scope : List.of(tenant, otherTenant)) version(scope, basis, "SIMULATION_BASIS", basisHash);
        version(tenant, price, "SIMULATION_PRICE", priceHash); version(tenant, policy, "SIMULATION_POLICY", policyHash);
        for (String scope : List.of(tenant, otherTenant)) {
            String user = scope.equals(tenant) ? actor : outsider;
            for (String action : List.of("model.configure", "configuration.read")) grant(scope, user, action, List.of("MODEL_CONFIGURATION"));
            for (String action : List.of("rule.write", "rule.read", "rule.approve", "artifact.read"))
                grant(scope, user, action, List.of("TEMPLATE_MAINTENANCE"));
        }
        var manifest = new ArtifactRegistryProtocol.DependencyManifest("1.0.0", tenant, null, accessSet, List.of());
        String manifestHash = hash(json.tree(manifest));
        version(tenant, accessSet, "DEPENDENCY_ACCESS_SET", manifestHash);
        insert("dependency_access_set", metadata(tenant, actor, row("access_set_id", accessSet,
            "manifest_sha256", manifestHash, "content_sha256", manifestHash)));
        insert("dependency_access_manifest", schema(metadata(tenant, actor, row("access_set_id", accessSet,
            "manifest_json", json.canonicalObject(manifest), "manifest_sha256", manifestHash, "content_sha256", manifestHash,
            "member_count", 0)), new DependencyManifestSchema().manifestSha256()));
        version(tenant, artifact, "ARTIFACT", artifactHash);
        String objectKey = root.relativize(artifactFile).toString().replace('\\', '/');
        var reference = new InterfaceProtocol.ObjectRef(objectKey, "SIMULATION-1", artifactHash, Integer.toString(bytes.length), "application/json");
        String interfacesHash = interfacesHash();
        insert("artifact", schema(metadata(tenant, actor, row("artifact_id", artifact, "artifact_kind", "SIMULATION_RULE_BASIS",
            "object_ref", StoredObjectReferences.encode(reference), "object_key", objectKey, "object_version", reference.objectVersion(),
            "sha256", artifactHash, "size_bytes", bytes.length, "media_type", reference.mediaType(),
            "dependency_access_set_id", accessSet, "content_sha256", artifactHash)), interfacesHash));
        insert("artifact_content_binding", metadata(tenant, actor, row("artifact_id", artifact, "resource_kind", "ARTIFACT", "resource_id", artifact,
            "schema_ref", textSchemaRef(), "schema_document_sha256", interfacesHash,
            "dependency_access_set_id", accessSet, "dependency_manifest_sha256", manifestHash)));
        // The artifact is freshly authored, has no upstream sources, and the complete dependency manifest is genuinely empty.
        // No source rights, release decision, candidate execution or customer approval is manufactured here.
    }

    private void member(String scope, String user) {
        String member = id();
        insert("tenant_member", metadata(scope, user, row("member_id", member, "user_id", user, "status", "ACTIVE",
            "valid_from", now.minusMinutes(1), "revision", 1, "updated_at", now)));
        insert("identity_binding", metadata(scope, user, row("identity_id", id(), "user_id", user, "mode", "local", "local_user_id", user,
            "status", "ACTIVE", "revision", 1, "updated_at", now)));
        insert("platform_member_route", metadata(scope, user, row("member_id", member, "user_id", user, "revision", 1, "updated_at", now)));
    }

    private void grant(String scope, String user, String action, List<String> purposes) throws Exception {
        var condition = new PermissionProtocol.Condition("1.0.0", List.of("local"), purposes,
            new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), List.of(), List.of()));
        String encoded = json.canonicalObject(condition); json.condition(encoded);
        insert("permission_grant", schema(metadata(scope, user, row("grant_id", id(), "subject_id", user,
            "resource_kind", "ENTERPRISE", "resource_id", scope, "action", action, "condition", encoded,
            "status", "ACTIVE", "valid_from", now.minusMinutes(1), "basis_version", basis, "revision", 1, "updated_at", now)), json.manifestSha256()));
    }

    public ObjectNode modelCreate(String label) {
        var definition = new ModelConfigurationProtocol.Definition("SIMULATION-provider", "https://provider.invalid/v1", "LOCAL",
            "SIMULATION-model-" + label, "SIMULATION-v1", "secret://simulation/not-a-provider-credential", id(), "SIMULATION-NOT-AVAILABLE",
            new ModelConfigurationProtocol.Limits("4096", "256", "1", "1", "4096", 30, false, List.of("none"), 0),
            new PermissionProtocol.Basis(price, priceHash), "CNY",
            new ModelConfigurationProtocol.DataPolicy(List.of("INTERNAL"), List.of("LOCAL"), false, "0", new PermissionProtocol.Basis(policy, policyHash)),
            "urn:medical:simulation:model-parameters:1.0.0", policyHash, "1.0.0");
        return (ObjectNode) json.tree(new ModelConfigurationProtocol.Save("1.0.0", null, null, "0", null, definition,
            "SIMULATION draft storage only; no provider, price or release approval"));
    }

    public ObjectNode modelAppend(ObjectNode first, JsonNode receipt, String version) {
        var body = first.deepCopy(); body.set("config_id", receipt.required("config_id"));
        body.set("expected_revision", receipt.required("revision"));
        body.set("based_on_revision", json.tree(new PermissionProtocol.Basis(receipt.required("model_config_revision").textValue(), receipt.required("content_sha256").textValue())));
        ((ObjectNode) body.required("definition")).put("model_version", version); return body;
    }

    public JsonNode ruleCreate() {
        var expression = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
            "#attributes['simulation_state'] == 'READY'", List.of("simulation_state"), List.of());
        return json.tree(new RuleVersionProtocol.VersionInput("1.0.0", null, RuleVersionSchema.CONTENT_REF,
            new TemplateApplicabilityProtocol.RuleContent("1.0.0", RuleExpression.ENGINE, expression,
                List.of(new PermissionProtocol.Basis(artifact, artifactHash))), "SIMULATION workflow only; no clinical validation"));
    }

    public Map<String,Long> counts() {
        var result = new LinkedHashMap<String,Long>();
        for (String table : List.of("model_configuration_definition", "model_config_revision", "configuration_head", "rule_definition", "rule_version",
                "rule_approval_decision", "version_registry", "lineage_edge", "operation_record", "audit_event", "outbox_event"))
            result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Long.class, tenant));
        return result;
    }

    public void revokeModelReading() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND subject_id=? AND action='configuration.read'", tenant, actor);
    }

    public long releaseCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM release_record WHERE tenant_id IN (?,?)", Long.class, tenant, otherTenant); }
    public static String interfacesHash() throws Exception { return SchemaCatalogue.fromClasspath().bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256(); }
    public static String textSchemaRef() throws Exception { return SchemaCatalogue.fromClasspath().schemaRef("interfaces", "TextContent"); }

    private void version(String scope, String value, String kind, String digest) { insert("version_registry", row("tenant_id", scope, "version_id", value, "entity_kind", kind, "content_sha256", digest)); }
    private Map<String,Object> metadata(String scope, String user, Map<String,Object> value) { value.put("tenant_id", scope); value.put("created_by", user); value.put("created_at", now); value.put("classification", "SIMULATION"); return value; }
    private Map<String,Object> schema(Map<String,Object> value, String digest) { value.put("schema_version", "1.0.0"); value.put("schema_manifest_sha256", digest); return value; }
    private String hash(JsonNode value) throws Exception { return DigestUtils.sha256Hex(json.canonical(value)); }
    private void insert(String table, Map<String,Object> values) {
        jdbc.update("INSERT INTO " + table + " (" + String.join(",", values.keySet().stream().map(k -> "`" + k + "`").toList())
            + ") VALUES (" + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Map<String,Object> row(Object... fields) { var value = new LinkedHashMap<String,Object>(); for (int i=0;i<fields.length;i+=2) value.put((String)fields[i], fields[i+1]); return value; }

    @Override public void close() throws Exception {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM platform_session_context WHERE tenant_id IN (?,?)", tenant, otherTenant);
            jdbc.update("UPDATE configuration_head SET current_version=NULL WHERE tenant_id IN (?,?)", tenant, otherTenant);
            // Only these randomly generated tenants are touched. Foreign keys stay enabled throughout cleanup.
            for (String table : List.of("rule_approval_decision", "rule_definition", "rule_version", "model_configuration_definition", "model_config_revision",
                    "configuration_head", "artifact_content_binding", "artifact", "dependency_access_manifest", "dependency_access_set",
                    "lineage_edge", "audit_event", "outbox_event", "operation_record", "permission_grant", "version_registry",
                    "platform_member_route", "identity_binding", "tenant_member", "tenant"))
                jdbc.update("DELETE FROM " + table + " WHERE tenant_id IN (?,?)", tenant, otherTenant);
        });
        Files.deleteIfExists(artifactFile); Files.deleteIfExists(artifactFile.getParent());
    }
}
