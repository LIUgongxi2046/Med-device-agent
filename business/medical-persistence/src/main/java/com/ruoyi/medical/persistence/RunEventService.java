package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.RunReadMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class RunEventService {
    private final EnterpriseAccess access;
    private final RunReadService runs;
    private final RunReadMapper mapper;
    private final ResourceReadAuthority resources;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public RunEventService(EnterpriseAccess access, RunReadService runs, RunReadMapper mapper, ResourceReadAuthority resources,
            PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.runs = runs; this.mapper = mapper; this.resources = resources; this.json = json;
        shared = SchemaCatalogue.fromClasspath();
    }

    public Batch read(ApplicationSessionIdentity.Verified subject, String context, String runId, String after) {
        WireValues.uuid(runId); PermissionSchema.revision(context); if (after != null) WireValues.uuid(after);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> read(admission, context, runId, after)).get());
    }

    Batch read(EnterpriseAccess.Admission admission, String context, String runId, String after) throws IOException {
        // 原运行锁协调快照、持久序号和子资源；每次读取重新检查会话、企业和许可。
        var snapshot = runs.snapshot(admission, context, runId).required("data");
        var headValues = new HashMap<String,Object>(); headValues.put("userId", admission.subject().user().user_id()); headValues.put("runId", runId);
        String tenant = admission.context().tenant_id();
        String project = text(reader.readTree(single(mapper.head(headValues), ErrorCode.CONTENT_MISMATCH)), "project_id", true);
        String task = text(snapshot, "task_id", true);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("runId", runId); values.put("projectId", project);
        var streams = mapper.stream(values);
        if (streams.isEmpty()) {
            if (after != null || !snapshot.required("event_cursor").isNull()) fail(ErrorCode.CURSOR_EXPIRED);
            return new Batch(null, null, "0", List.of());
        }
        var stream = reader.readTree(single(streams, ErrorCode.CONTENT_MISMATCH));
        String streamId = text(stream, "stream_id", true); WireValues.uuid(streamId);
        BigInteger end = WireValues.revision(text(stream, "sequence", true)), sequence = BigInteger.ZERO;
        values.put("streamId", streamId);
        if (after != null) {
            values.put("eventId", after);
            var cursor = reader.readTree(single(mapper.eventCursor(values), ErrorCode.CURSOR_EXPIRED));
            sequence = WireValues.revision(text(cursor, "sequence", true));
            if (sequence.signum() <= 0 || sequence.compareTo(end) > 0) fail(ErrorCode.CURSOR_EXPIRED);
        }
        values.put("sequence", sequence.toString());
        var events = new ArrayList<JsonNode>(); int bytes = 0; String cursor = after;
        var originals = mapper.events(values);
        if (originals.isEmpty() && sequence.compareTo(end) < 0) fail(ErrorCode.CURSOR_EXPIRED);
        for (String original : originals) {
            var row = reader.readTree(original); var event = row.required("event");
            shared.validate("interfaces", event, "PublicEvent");
            if (!json.manifestSha256().equals(text(row, "schema_manifest", true))
                    || !tenant.equals(text(event, "tenant_id", true)) || !project.equals(text(event, "project_id", true))
                    || !task.equals(text(event, "task_id", true)) || !runId.equals(text(event, "run_id", true))) fail(ErrorCode.CONTENT_MISMATCH);
            BigInteger next = WireValues.revision(text(event, "sequence", true));
            if (!next.equals(sequence.add(BigInteger.ONE)) || next.compareTo(end) > 0) fail(ErrorCode.CURSOR_EXPIRED);
            WireValues.revision(text(event, "resource_revision", true)); authorize(admission, project, task, runId, event);
            int length = json.canonical(event).getBytes(StandardCharsets.UTF_8).length;
            if (length > 1024 * 1024) fail(ErrorCode.CONTEXT_LIMIT);
            if (bytes + length > 1024 * 1024) break;
            bytes += length; events.add(event); sequence = next; cursor = text(event, "event_id", true);
        }
        return new Batch(streamId, cursor, sequence.toString(), events);
    }

    private void authorize(EnterpriseAccess.Admission admission, String project, String task, String runId, JsonNode event) throws IOException {
        String kind = text(event, "resource_kind", true), id = text(event, "resource_id", true);
        String scopeKind, scopeId, action, purpose;
        switch (kind) {
            case "RUN", "STEP" -> { scopeKind = "TASK"; scopeId = task; action = "run.read"; purpose = "RUN_READING"; }
            case "WAITING_ITEM", "CANDIDATE", "DOCUMENT", "SOURCE_VERSION", "EXPORT", "NOTIFICATION" -> {
                var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
                values.put("kind", kind); values.put("id", id); values.put("projectId", project); values.put("taskId", task); values.put("runId", runId);
                var target = reader.readTree(single(mapper.eventAuthority(values), ErrorCode.ACCESS_DENIED));
                scopeKind = text(target, "kind", true); scopeId = text(target, "id", true);
                project = target.required("project_id").isNull() ? null : text(target, "project_id", true);
                action = switch (kind) { case "WAITING_ITEM" -> "work_item.read"; case "CANDIDATE" -> "candidate.read"; case "DOCUMENT" -> "document.read";
                    case "SOURCE_VERSION" -> "source.read"; case "EXPORT" -> "export.read"; default -> "notification.read"; };
                purpose = switch (kind) { case "WAITING_ITEM" -> "WORK_ITEM_READING"; case "CANDIDATE" -> "CANDIDATE_READING";
                    case "DOCUMENT" -> "DOCUMENT_BODY"; default -> "RUN_EVENTS"; };
            }
            default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, scopeKind, scopeId), action, purpose);
    }

    public record Batch(String streamId, String cursor, String sequence, List<JsonNode> events) {
        public Batch { WireValues.revision(sequence); events = events.stream().<JsonNode>map(JsonNode::deepCopy).toList(); }
    }
    private static String text(JsonNode value, String name, boolean required) {
        var field = value.get(name); if (!required && field == null) return null;
        if (field == null || !field.isTextual()) fail(ErrorCode.CONTENT_MISMATCH); return field.textValue();
    }
    private static String single(List<String> values, ErrorCode code) { if (values.size() != 1) fail(code); return values.get(0); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
