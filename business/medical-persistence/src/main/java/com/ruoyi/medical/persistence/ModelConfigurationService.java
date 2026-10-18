package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.ModelConfigurationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Persists fixed DRAFT definitions. Publishing and provider execution are deliberately separate gates. */
@Component
public final class ModelConfigurationService {
    private static final String PURPOSE = "MODEL_CONFIGURATION";
    private final EnterpriseAccess access;
    private final ModelConfigurationMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final TenantWatermarkMapper watermarks;
    private final PermissionSchema json;
    private final ModelConfigurationSchema schema;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;

    public ModelConfigurationService(EnterpriseAccess access, ModelConfigurationMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, TenantWatermarkMapper watermarks, PermissionSchema json,
            ModelConfigurationSchema schema, IdentityDirectoryPolicy policy, @Qualifier("masterDataSource") DataSource master) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.watermarks = watermarks; this.json = json; this.schema = schema; this.policy = policy; jdbc = new JdbcTemplate(master);
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String versionId) {
        return read(subject, context, versionId, false);
    }
    public JsonNode readSimulation(ApplicationSessionIdentity.Verified subject, String context, String versionId) {
        return read(subject, context, versionId, true);
    }
    private JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String versionId, boolean simulation) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); return read(admission, versionId, simulation);
        }));
    }

    JsonNode read(EnterpriseAccess.Admission admission, String versionId) throws IOException { return read(admission, versionId, false); }
    JsonNode read(EnterpriseAccess.Admission admission, String versionId, boolean simulation) throws IOException {
        if(admission.simulationWorker() && !simulation) denied(ErrorCode.ACCESS_DENIED);
        var values = values(admission, simulation); values.put("versionId", versionId);
        var stored = single(mapper.stored(values), ErrorCode.OBJECT_UNAVAILABLE);
        if (!values.get("mode").equals(stored.executionMode())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        require(admission, stored.projectId(), stored.configId(), "configuration.read");
        var fixed = original(values, stored); values.put("configId", stored.configId());
        var head = head(values); boolean projectWritable = project(head.projectId(), false);
        if (!Objects.equals(head.projectId(), fixed.project_id())) mismatch();
        var view = json.object(); view.put("schema_version", "1.0.0"); view.put("tenant_id", fixed.tenant_id());
        view.set("project_id", json.tree(fixed.project_id())); view.put("config_id", fixed.config_id());
        view.put("model_config_revision", fixed.model_config_revision()); view.set("based_on_revision", json.tree(fixed.based_on_revision()));
        view.set("definition", schema.publicDefinition(fixed.definition())); view.put("content_sha256", stored.contentHash());
        view.put("status", "DRAFT"); view.put("current_model_revision", head.currentVersion()); view.put("revision", head.revision().toString());
        view.put("created_by", fixed.created_by()); view.put("created_at", fixed.created_at());
        view.put("dependency_validation", simulation ? "DECLARED_SIMULATION_BINDING_ONLY" : "REGISTRY_REFERENCE_ONLY");
        var actions = json.object().arrayNode().add("configuration.read");
        if (!admission.simulationWorker() && projectWritable && "DRAFT".equals(head.status())
                && authority.find(admission, scope(admission, head.projectId(), head.configId()), "model.configure", PURPOSE).isPresent()) actions.add("model.configure");
        view.set("allowed_actions", actions);
        return schema.envelope("View", view, UUID.randomUUID().toString(), head.revision().toString());
    }

    public JsonNode save(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) {
        return save(subject, context, key, bytes, false);
    }
    public JsonNode saveSimulation(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) {
        return save(subject, context, key, bytes, true);
    }
    private JsonNode save(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes, boolean simulation) {
        return access.execute(subject, admission -> checked(() -> {
            var request = schema.save(bytes, simulation); var values = values(admission, simulation);
            var witness = require(admission, request.project_id(), request.config_id(), "model.configure");
            values.put("key", key); values.put("requestHash", schema.hash(request, context, subject.sessionId()));
            var prior = mapper.currentOperation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty()) return replay(admission, values, request, prior.get(0));
            current(admission, context); project(request.project_id(), true);
            String configId = request.config_id() == null ? UUID.randomUUID().toString() : request.config_id();
            values.put("configId", configId); values.put("projectId", request.project_id());
            if (request.config_id() != null) {
                var before = head(values);
                if (!Objects.equals(before.projectId(), request.project_id())) denied(ErrorCode.ACCESS_DENIED);
                if (!"DRAFT".equals(before.status())) denied(ErrorCode.REVISION_CONFLICT);
                if (!before.revision().toString().equals(request.expected_revision())
                        || !before.currentVersion().equals(request.based_on_revision().version_id())) denied(ErrorCode.REVISION_CONFLICT);
                values.put("versionId", before.currentVersion());
                var original = single(mapper.stored(values), ErrorCode.CONTENT_MISMATCH); original(values, original);
                if (!original.configId().equals(configId) || !original.contentHash().equals(request.based_on_revision().content_sha256())) mismatch();
            }
            // Registry existence is not approval, credential verification, price qualification, or network consent.
            dependencies(request.project_id(), schema.dependencies(request.based_on_revision(), request.definition()));
            String versionId = UUID.randomUUID().toString();
            var fixed = new ModelConfigurationProtocol.Fixed("1.0.0", admission.context().tenant_id(), request.project_id(), configId,
                    versionId, request.based_on_revision(), request.definition(), request.reason(), witness.grantId(),
                    witness.revision().toString(), subject.user().user_id(), time((LocalDateTime) values.get("now")));
            schema.verify("Fixed", json.tree(fixed)); String content = json.canonicalObject(fixed);
            values.put("versionId", versionId); values.put("definition", request.definition()); values.put("content", content);
            values.put("contentHash", DigestUtils.sha256Hex(content)); values.put("limits", json.canonicalObject(request.definition().limits()));
            if (request.definition() instanceof ModelConfigurationProtocol.Definition external) values.put("dataPolicy", json.canonicalObject(external.data_policy()));
            else values.put("simulationDefinition", json.canonicalObject(request.definition()));
            if (request.config_id() == null) one(mapper.insertHead(values));
            one(mapper.insertVersion(values)); one(mapper.insertModel(values)); one(mapper.insertDefinition(values));
            for (var ref : schema.dependencies(request.based_on_revision(), request.definition())) {
                values.put("upstreamId", ref.version_id()); one(mapper.insertLineage(values));
            }
            if (request.config_id() == null) one(mapper.initializeHead(values));
            else {
                values.put("expected", request.expected_revision()); values.put("oldVersion", request.based_on_revision().version_id());
                one(mapper.advanceHead(values));
            }
            var head = head(values);
            String expected = request.config_id() == null ? "1" : PermissionSchema.next(request.expected_revision());
            if (!versionId.equals(head.currentVersion()) || !expected.equals(head.revision().toString())) mismatch();
            var saved = single(mapper.stored(values), ErrorCode.CONTENT_MISMATCH);
            if (!original(values, saved).equals(fixed)) mismatch();
            return complete(admission, values, head, saved, witness);
        }));
    }

    ModelConfigurationProtocol.Fixed requireCurrentExternal(EnterpriseAccess.Admission admission,
            String versionId, String purpose, String targetProject) throws IOException {
        if (admission.simulationWorker()) denied(ErrorCode.ACCESS_DENIED);
        var values = values(admission, false); values.put("versionId", versionId);
        var stored = single(mapper.stored(values), ErrorCode.OBJECT_UNAVAILABLE);
        if (!"EXTERNAL".equals(stored.executionMode())) denied(ErrorCode.ACCESS_DENIED);
        var fixed = original(values, stored); var head = head(values);
        if (!versionId.equals(head.currentVersion()) || !"DRAFT".equals(head.status())) denied(ErrorCode.SCOPE_STALE);
        if (fixed.project_id() != null && !fixed.project_id().equals(targetProject)) denied(ErrorCode.ACCESS_DENIED);
        if (!project(fixed.project_id(), false)) denied(ErrorCode.ACCESS_DENIED);
        authority.require(admission, scope(admission, fixed.project_id(), fixed.config_id()), "configuration.read", purpose);
        return fixed;
    }

    private ModelConfigurationProtocol.Fixed original(Map<String,Object> values, ModelConfigurationProtocol.Stored stored) throws IOException {
        if (!values.get("mode").equals(stored.executionMode())) mismatch();
        values.put("configId", stored.configId());
        var owner = head(values);
        if (!java.util.Objects.equals(owner.projectId(), stored.projectId())) mismatch();
        var fixed = schema.fixed(stored.definition());
        if (!stored.executionMode().equals(ModelConfigurationSchema.mode(fixed.definition()))) mismatch();
        String contentHash = DigestUtils.sha256Hex(json.canonicalObject(fixed));
        if (!stored.versionId().equals(values.get("versionId")) || !fixed.tenant_id().equals(TenantScope.requireCurrent().tenantId()) || !stored.tenantId().equals(fixed.tenant_id())
                || !fixed.config_id().equals(stored.configId()) || !fixed.model_config_revision().equals(stored.versionId())
                || !Objects.equals(fixed.project_id(), stored.projectId()) || !contentHash.equals(stored.contentHash())
                || !contentHash.equals(stored.registryHash()) || !"1.0.0".equals(stored.schemaVersion())
                || !values.get("manifest").equals(stored.manifest()) || !"DRAFT".equals(stored.status())
                || !policy.classification().equals(stored.classification()) || !fixed.created_by().equals(stored.createdBy())
                || !fixed.created_at().equals(time(stored.createdAt()))
                || fixed.based_on_revision() != null && fixed.based_on_revision().version_id().equals(fixed.model_config_revision())) mismatch();
        if (!schema.projection(fixed.definition()).equals(schema.read(stored.projection()))) mismatch();
        values.put("versionId", stored.versionId());
        var refs = schema.dependencies(fixed.based_on_revision(), fixed.definition());
        if (!mapper.parents(values).equals(refs.stream().map(PermissionProtocol.Basis::version_id).toList())) mismatch();
        dependencies(stored.projectId(), refs);
        return fixed;
    }

    private void dependencies(String project, List<PermissionProtocol.Basis> refs) {
        for (var ref : refs) {
            var registered = single(permissions.basis(ref.version_id()), ErrorCode.CONTENT_MISMATCH);
            if (!registered.id().equals(ref.version_id()) || !registered.sha256().equals(ref.content_sha256())
                    || registered.ownerProjectId() != null && !registered.ownerProjectId().equals(project)) mismatch();
        }
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, Map<String,Object> values,
            ModelConfigurationProtocol.Save request, SelectionOperationRow saved) throws IOException {
        if (!saved.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(saved.state()) || !Integer.valueOf(201).equals(saved.httpStatus())
                || !"1.0.0".equals(saved.schemaVersion()) || !values.get("manifest").equals(saved.schemaManifestSha256())) mismatch();
        var response = schema.receipt(saved.responseRef(), "SIMULATION".equals(values.get("mode"))); var receipt = response.required("data");
        values.put("versionId", saved.targetId()); var stored = single(mapper.stored(values), ErrorCode.CONTENT_MISMATCH);
        require(admission, stored.projectId(), stored.configId(), "model.configure");
        var fixed = original(values, stored);
        if (!request.definition().equals(fixed.definition()) || !request.reason().equals(fixed.reason())
                || !Objects.equals(request.project_id(), fixed.project_id()) || !Objects.equals(request.based_on_revision(), fixed.based_on_revision())
                || request.config_id() != null && !request.config_id().equals(fixed.config_id())
                || !fixed.created_by().equals(admission.subject().user().user_id())
                || !saved.operationId().equals(receipt.required("operation_id").textValue())
                || !saved.targetId().equals(receipt.required("model_config_revision").textValue())
                || !fixed.tenant_id().equals(receipt.required("tenant_id").textValue())
                || !json.tree(fixed.project_id()).equals(receipt.required("project_id"))
                || !fixed.config_id().equals(receipt.required("config_id").textValue())
                || !stored.contentHash().equals(receipt.required("content_sha256").textValue())) mismatch();
        // The original fixed revision survives later head movement; replay never creates a replacement version.
        return response;
    }

    private JsonNode complete(EnterpriseAccess.Admission admission, Map<String,Object> values,
            ModelConfigurationProtocol.Head head, ModelConfigurationProtocol.Stored stored, PermissionProtocol.GrantRow witness) throws IOException {
        var context = advanceContext(admission, values);
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.put("tenant_id", head.tenantId()); receipt.set("project_id", json.tree(head.projectId())); receipt.put("config_id", head.configId());
        receipt.put("model_config_revision", stored.versionId()); receipt.put("content_sha256", stored.contentHash());
        receipt.put("status", "DRAFT"); receipt.put("revision", head.revision().toString()); receipt.set("current_context", json.tree(context));
        if ("SIMULATION".equals(values.get("mode"))) { receipt.put("execution_mode", "SIMULATION"); receipt.put("production_eligible", false); }
        var response = schema.envelope("Receipt", receipt, requestId, head.revision().toString());
        values.put("operationId", operationId); values.put("response", json.canonical(response)); one(mapper.saveOperation(values));
        // Deliberately omit the input, endpoint, credential reference path/version and free-form reason from logs/events.
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
        audit.put("request_sha256", (String) values.get("requestHash")); audit.set("after", receipt);
        audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
        String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        one(permissions.saveAudit(admission.subject().user().user_id(), head.projectId(), auditId, "model.configuration_saved",
                json.canonical(audit), requestId, (String) values.get("manifest"), policy.classification()));
        one(permissions.saveEvent(admission.subject().user().user_id(), eventId, head.configId(), head.revision().toString(),
                "model.configuration_saved", json.canonical(receipt)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId);
        values.put("resulting", head.revision().toString());
        if (!schema.read(single(mapper.audit(values), ErrorCode.CONTENT_MISMATCH)).equals(audit)
                || !schema.read(single(mapper.event(values), ErrorCode.CONTENT_MISMATCH)).equals(receipt)) mismatch();
        var operation = single(mapper.currentOperation(values), ErrorCode.CONTENT_MISMATCH);
        if (!operationId.equals(operation.operationId()) || !stored.versionId().equals(operation.targetId())
                || !values.get("requestHash").equals(operation.requestSha256()) || !schema.receipt(operation.responseRef(), "SIMULATION".equals(values.get("mode"))).equals(response)) mismatch();
        return response;
    }

    private BusinessSessionProtocol.Context advanceContext(EnterpriseAccess.Admission admission, Map<String,Object> values) {
        values.put("governanceEpoch", admission.context().governance_epoch()); values.put("authEpoch", admission.context().auth_epoch());
        String governance = PermissionSchema.next(admission.context().governance_epoch()), revision = PermissionSchema.next(admission.context().context_revision());
        one(mapper.advanceGovernance(values));
        one(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, revision, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(),
                admission.context().tenant_id(), admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var expected = new BusinessSessionProtocol.Context(admission.context().tenant_id(), revision, admission.context().auth_epoch(), governance);
        var current = jdbc.query("""
                SELECT tenant_id,context_revision,auth_epoch,governance_epoch FROM platform_session_context
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=?
                """, (row, ordinal) -> new BusinessSessionProtocol.Context(row.getString(1), row.getString(2), row.getString(3), row.getString(4)),
                admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(), admission.context().tenant_id());
        var epochs = Objects.requireNonNull(watermarks.lockCurrentTenant());
        if (!current.equals(List.of(expected)) || !epochs.authEpoch().toString().equals(expected.auth_epoch())
                || !epochs.governanceEpoch().toString().equals(expected.governance_epoch())) mismatch();
        return expected;
    }

    private ModelConfigurationProtocol.Head head(Map<String,Object> values) {
        var row = single(mapper.head(values), ErrorCode.OBJECT_UNAVAILABLE);
        if (!row.tenantId().equals(TenantScope.requireCurrent().tenantId()) || !row.configId().equals(values.get("configId"))
                || !values.get("mode").equals(row.executionMode()) || "SIMULATION".equals(row.executionMode()) && !"DRAFT".equals(row.status())
                || row.currentVersion() == null || !policy.classification().equals(row.classification())
                || PermissionSchema.revision(row.revision().toString()).signum() <= 0) mismatch();
        return row;
    }
    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission, String project, String config, String action) throws IOException {
        return authority.require(admission, scope(admission, project, config), action, PURPOSE);
    }
    private static PermissionProtocol.Scope scope(EnterpriseAccess.Admission admission, String project, String config) {
        return config != null ? new PermissionProtocol.Scope(admission.context().tenant_id(), project, "CONFIGURATION", config)
                : new PermissionProtocol.Scope(admission.context().tenant_id(), project, project == null ? "ENTERPRISE" : "PROJECT",
                        project == null ? admission.context().tenant_id() : project);
    }
    private boolean project(String id, boolean writing) {
        if (id == null) return true;
        String status = single(permissions.projectStatus(id), ErrorCode.OBJECT_UNAVAILABLE);
        boolean writable = List.of("PENDING", "ACTIVE").contains(status);
        if (writing && !writable) denied(ErrorCode.ACCESS_DENIED);
        return writable;
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission, boolean simulation) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.actorId());
        values.put("mode", simulation ? "SIMULATION" : "EXTERNAL"); values.put("manifest", schema.manifestSha256(simulation));
        values.put("classification", policy.classification());
        values.put("now", Objects.requireNonNull(permissions.databaseTime())); return values;
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void current(EnterpriseAccess.Admission admission, String header) {
        if (!admission.context().context_revision().equals(header)) denied(ErrorCode.CONTEXT_CHANGED);
    }
    private static <T> T checked(ThrowingSupplier<T> work) {
        return work.get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException("MODEL_CONFIGURATION_INVALID", failure));
    }
    private static <T> T single(List<T> rows, ErrorCode code) { if (rows.size() != 1) denied(code); return Objects.requireNonNull(rows.get(0)); }
    private static void one(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { denied(ErrorCode.CONTENT_MISMATCH); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
