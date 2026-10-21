package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Authenticated tenant-scoped authoring, reading and original-operation recovery. */
public final class FormalEvaluationContextService {
    private final EnterpriseAccess access;
    private final FormalEvaluationContextStore store;
    private final FormalEvaluationContextHttpSchema schema;
    private final FormalEvaluationContextReadAuthority reads;
    private final AgentAuthoringCursor cursors;
    private final PermissionSchema json;

    public FormalEvaluationContextService(EnterpriseAccess access, FormalEvaluationContextStore store,
            FormalEvaluationContextHttpSchema schema, AgentAuthoringCursor cursors, PermissionSchema json, FormalEvaluationContextReadAuthority reads) {
        this.access = access; this.store = store; this.schema = schema; this.cursors = cursors; this.json = json; this.reads = reads;
    }

    public JsonNode author(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        json.headers(context, key);
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            current(a, context);
            return schema.validate("Receipt", json.tree(store.author(a, key, body)));
        }).get());
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        json.headers(context, key);
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            current(a, context);
            return schema.validate("Receipt", json.tree(store.decide(a, key, body)));
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String version) {
        try { WireValues.uuid(version); } catch (IllegalArgumentException invalid) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            current(a, context);
            return schema.validate("View", store.readView(a, version, reads));
        }).get());
    }

    public JsonNode recover(ApplicationSessionIdentity.Verified subject, String context, byte[] body) {
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            current(a, context);
            var request = schema.request("Recovery", body);
            byte[] original = json.canonical(request.required("request")).getBytes(StandardCharsets.UTF_8);
            var receipt = store.recover(a, request.required("kind").asText(), request.required("original_key").asText(), original, reads);
            return schema.validate("Receipt", json.tree(receipt));
        }).get());
    }

    public JsonNode query(ApplicationSessionIdentity.Verified subject, String context, byte[] body) {
        return access.execute(subject, a -> ThrowingSupplier.of(() -> {
            current(a, context);
            var request = schema.request("Query", body);
            String project = request.required("project_id").asText();
            int limit = request.required("limit").intValue();
            var owner = new AgentAuthoringCursor.Owner(a.context().tenant_id(), a.actorId(), subject.sessionId(), context,
                    a.context().auth_epoch(), a.context().governance_epoch(), limit, "088:CONTEXT_BINDING:" + project);
            String token = request.required("cursor").isNull() ? null : request.required("cursor").asText();
            String after = cursors.resolve(token, owner);
            var ids = store.seek(a, project, after, limit);
            if (ids.size() > limit + 1) bad();
            String previous = after;
            for (String id : ids) { WireValues.uuid(id); if (id.compareTo(previous) <= 0) bad(); previous = id; }
            var result = json.object().put("schema_version", "1.0.0");
            var items = result.putArray("items");
            previous = after;
            for (int index = 0; index < Math.min(limit, ids.size()); index++) {
                String id = ids.get(index); previous = id;
                try {
                    JsonNode view = schema.validate("View", store.readView(a, id, reads));
                    var fixed = view.required("fixed"); var definition = fixed.required("definition");
                    if (!project.equals(definition.required("project_id").asText())
                            || !id.equals(view.required("binding").required("version_id").asText())
                            || !a.context().tenant_id().equals(fixed.required("tenant_id").asText())) bad();
                    var item = json.object();
                    for (String key : List.of("binding", "status", "revision")) item.set(key, view.required(key));
                    for (String key : List.of("project_id", "candidate", "execution_policy")) item.set(key, definition.required(key));
                    for (String key : List.of("created_by", "created_at", "classification")) item.set(key, fixed.required(key));
                    items.add(schema.validate("Summary", item));
                } catch (TenantAccessException failure) {
                    // A bounded page may contain fewer accessible records. Never hide journal corruption.
                    if (!Set.of(ErrorCode.ACCESS_DENIED, ErrorCode.SOURCE_NOT_READY, ErrorCode.SOURCE_MISSING).contains(failure.code())) throw failure;
                }
            }
            if (ids.size() > limit) result.put("next_cursor", cursors.issue(owner, previous));
            else result.putNull("next_cursor");
            return schema.validate("Page", result);
        }).get());
    }

    static void current(EnterpriseAccess.Admission admission, String context) {
        PermissionSchema.revision(context);
        if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static void bad() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
