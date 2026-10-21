package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RetrievalMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties(RetrievalPolicy.class)
public final class RetrievalService {
    private final EnterpriseAccess access;
    private final RetrievalMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final SourceReadAuthority sources;
    private final RetrievalSchema schema;
    private final OperationSchema operations;
    private final PermissionSchema json;
    private final RetrievalPolicy policy;
    private final IdentityDirectoryPolicy identities;

    public RetrievalService(EnterpriseAccess access, RetrievalMapper mapper, PermissionMapper permissions,
            PermissionAuthority authority, SourceReadAuthority sources, RetrievalSchema schema,
            OperationSchema operations, PermissionSchema json, RetrievalPolicy policy, IdentityDirectoryPolicy identities) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.sources = sources; this.schema = schema; this.operations = operations; this.json = json;
        this.policy = policy; this.identities = identities;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key,
            RetrievalProtocol.SearchInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            return create(admission, context, key, input, subject.sessionId(), null);
        }).get());
    }

    JsonNode create(EnterpriseAccess.Admission admission, String context, String key,
            RetrievalProtocol.SearchInput input, String requestIdentity, TaskResearchToolAccess.Fixed fixed) throws IOException {
            current(admission, context); if (fixed == null) requireProject(admission, input.project_id());
            var values = values(admission); values.put("projectId", input.project_id()); values.put("key", key);
            values.put("environment", policy.environment()); values.put("region", policy.region());
            values.put("queryText", input.query()); values.put("querySha256", DigestUtils.sha256Hex(input.query().getBytes(StandardCharsets.UTF_8)));
            values.put("requestHash", fixed == null ? schema.requestHash(input, context, requestIdentity)
                    : DigestUtils.sha256Hex(json.canonical(json.tree(Map.of("operation", "TASK_AT01_RETRIEVAL",
                        "input_receipt_identity", requestIdentity, "body", input)))));
            if (fixed != null) values.put("fixedSources", fixed.sources());
            var previous = mapper.operation(values);
            if (!previous.isEmpty()) return replay(previous, values);
            var candidates = mapper.candidates(values);
            if (candidates.size() > policy.maximumResults()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var hits = new java.util.ArrayList<RetrievalProtocol.Hit>();
            int rank = 1;
            for (var candidate : candidates) {
                if (fixed != null) fixed.require(candidate);
                verifyCandidate(admission, input.project_id(), candidate);
                hits.add(new RetrievalProtocol.Hit(BigInteger.valueOf(rank++), candidate.sourceId(), candidate.sourceVersion(),
                        candidate.parseVersion(), candidate.indexVersion(), candidate.chunkId(), candidate.approvalRevision(),
                        candidate.rightsRevision(), candidate.rightsPurpose(), candidate.title(), candidate.excerpt(),
                        candidate.excerptTruncated(), candidate.locator(), candidate.locatorKind(), candidate.quoteHash(), candidate.relevance()));
            }
            var selection = json.object().arrayNode();
            for (var hit : hits) {
                var item = json.object(); item.put("source_version", hit.sourceVersion()); item.put("parse_version", hit.parseVersion());
                item.put("index_version", hit.indexVersion()); item.put("chunk_id", hit.chunkId());
                item.put("approval_revision", hit.approvalRevision()); item.put("quote_hash", hit.quoteHash()); selection.add(item);
            }
            String retrievalId = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString();
            String requestId = UUID.randomUUID().toString(), selectionSha = DigestUtils.sha256Hex(json.canonical(selection));
            values.put("retrievalId", retrievalId); values.put("operationId", operationId); values.put("selectionSha256", selectionSha);
            values.put("resultCount", hits.size()); values.put("status", hits.isEmpty() ? "EMPTY" : "FOUND");
            values.put("authEpoch", admission.context().auth_epoch()); values.put("governanceEpoch", admission.context().governance_epoch());
            values.put("now", Objects.requireNonNull(permissions.databaseTime())); values.put("classification", identities.classification());
            values.put("schemaManifest", schema.manifestSha256());
            one(mapper.insertQuery(values));
            for (var hit : hits) { values.put("hit", hit); one(mapper.insertHit(values)); }
            var operation = new InterfaceProtocol.Operation(operationId, retrievalId, InterfaceProtocol.OperationStatus.COMPLETED,
                    "/api/operations/" + operationId);
            var response = operations.operation(operation, requestId); values.put("response", json.canonical(response));
            one(mapper.saveOperation(values));
            var audit = json.object(); audit.put("retrieval_id", retrievalId); audit.put("project_id", input.project_id());
            audit.put("query_sha256", values.get("querySha256").toString()); audit.put("selection_sha256", selectionSha);
            audit.put("result_count", Integer.toString(hits.size()));
            one(permissions.saveAudit(admission.actorId(), input.project_id(), UUID.randomUUID().toString(),
                    "retrieval.created", json.canonical(audit), requestId, schema.manifestSha256(), identities.classification()));
            var stored = single(mapper.query(values), ErrorCode.CONTENT_MISMATCH);
            var saved = single(mapper.operation(values), ErrorCode.CONTENT_MISMATCH);
            if (!stored.retrievalId().equals(retrievalId) || !stored.querySha256().equals(values.get("querySha256"))
                    || !stored.selectionSha256().equals(selectionSha) || !stored.resultCount().equals(BigInteger.valueOf(hits.size()))
                    || !saved.operationId().equals(operationId) || !saved.targetId().equals(retrievalId)
                    || !saved.requestSha256().equals(values.get("requestHash"))
                    || !schema.operationReceipt(saved.responseRef()).equals(response)) mismatch();
            return response;
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String retrievalId,
            RetrievalProtocol.PageQuery page) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            return read(admission, context, retrievalId, page, null);
        }).get());
    }

    JsonNode read(EnterpriseAccess.Admission admission, String context, String retrievalId,
            RetrievalProtocol.PageQuery page, TaskResearchToolAccess.Fixed fixed) throws IOException {
            current(admission, context); WireValues.uuid(retrievalId);
            var values = values(admission); values.put("retrievalId", retrievalId);
            var query = single(mapper.query(values), ErrorCode.OBJECT_UNAVAILABLE);
            if (!query.subjectId().equals(admission.actorId()) || !query.createdBy().equals(query.subjectId())) denied(ErrorCode.ACCESS_DENIED);
            if (!query.tenantId().equals(admission.context().tenant_id()) || !query.retrievalId().equals(retrievalId)
                    || !"VERIFICATION".equals(query.purpose()) || !"FULLTEXT".equals(query.mode())
                    || !query.status().equals(query.resultCount().signum() == 0 ? "EMPTY" : "FOUND")) mismatch();
            if (fixed == null) requireProject(admission, query.projectId());
            else {
                if (!fixed.projectId().equals(query.projectId()) || !fixed.questionText().equals(query.queryText())) mismatch();
                values.put("key", fixed.retrievalKey());
                var operation = single(mapper.operation(values), ErrorCode.OBJECT_UNAVAILABLE);
                if (!retrievalId.equals(operation.targetId())) denied(ErrorCode.ACCESS_DENIED);
            }
            BigInteger offsetValue = BigInteger.valueOf((long) page.page() - 1L).multiply(BigInteger.valueOf(page.pageSize()));
            if (offsetValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            if (query.resultCount().signum() > 0 && offsetValue.compareTo(query.resultCount()) >= 0) denied(ErrorCode.OBJECT_UNAVAILABLE);
            int offset = offsetValue.intValueExact(); values.put("offset", offset); values.put("pageSize", page.pageSize());
            var hits = mapper.hits(values);
            int expected = query.resultCount().subtract(offsetValue).min(BigInteger.valueOf(page.pageSize())).intValueExact();
            if (hits.size() != expected) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
            var items = json.object().arrayNode();
            for (var hit : hits) {
                if (fixed != null) fixed.require(hit);
                verifyHit(admission, query.projectId(), hit);
                var item = json.object(); item.put("rank", hit.rank().intValueExact()); item.put("source_id", hit.sourceId());
                item.put("source_version", hit.sourceVersion()); item.put("parse_version", hit.parseVersion());
                item.put("index_version", hit.indexVersion()); item.put("chunk_id", hit.chunkId());
                item.put("approval_revision", hit.approvalRevision()); item.put("title", hit.title()); item.put("excerpt", hit.excerpt());
                item.put("excerpt_truncated", hit.excerptTruncated()); item.put("locator_kind", hit.locatorKind());
                item.set("locator", schema.locator(hit.locator())); item.put("quote_hash", hit.quoteHash());
                item.put("relevance", hit.relevance().stripTrailingZeros().toPlainString()); schema.verify("Hit", item); items.add(item);
            }
            var info = json.object(); info.put("page", page.page()); info.put("page_size", page.pageSize());
            info.put("total", query.resultCount().toString()); info.put("selection_sha256", query.selectionSha256());
            BigInteger delivered = offsetValue.add(BigInteger.valueOf(hits.size()));
            if (delivered.compareTo(query.resultCount()) < 0) info.put("next_page", Math.addExact(page.page(), 1)); else info.putNull("next_page");
            var result = json.object(); result.put("schema_version", "1.0.0"); result.put("retrieval_id", retrievalId);
            result.put("project_id", query.projectId()); result.put("query", query.queryText()); result.put("purpose", query.purpose());
            result.put("mode", query.mode()); result.put("status", query.status()); result.put("created_at", time(query.createdAt()));
            result.set("items", items); result.set("page_info", info);
            return schema.page(result, UUID.randomUUID().toString());
    }

    private JsonNode replay(List<SelectionOperationRow> rows, Map<String,Object> values) throws IOException {
        var operation = single(rows, ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus())
                || operation.responseRef() == null || operation.targetId() == null) mismatch();
        return schema.operationReceipt(operation.responseRef());
    }

    private void verifyCandidate(EnterpriseAccess.Admission admission, String projectId, RetrievalProtocol.Candidate value) throws IOException {
        verifySource(admission, projectId, value.sourceId(), value.sourceVersion(), value.rightsRevision(), value.rightsPurpose());
        WireValues.uuid(value.parseVersion()); WireValues.uuid(value.indexVersion()); WireValues.uuid(value.chunkId());
        WireValues.uuid(value.approvalRevision()); WireValues.hash(value.quoteHash()); schema.locator(value.locator());
        if (!value.locatorKind().equals(schema.locator(value.locator()).required("kind").textValue())
                || value.relevance().signum() < 0 || value.excerpt().isEmpty() || value.excerpt().codePointCount(0, value.excerpt().length()) > 1200) mismatch();
    }

    private void verifyHit(EnterpriseAccess.Admission admission, String projectId, RetrievalProtocol.Hit value) throws IOException {
        verifySource(admission, projectId, value.sourceId(), value.sourceVersion(), value.rightsRevision(), value.rightsPurpose());
        WireValues.revision(value.rank().toString()); WireValues.uuid(value.parseVersion()); WireValues.uuid(value.indexVersion());
        WireValues.uuid(value.chunkId()); WireValues.uuid(value.approvalRevision()); WireValues.hash(value.quoteHash());
        if (!value.locatorKind().equals(schema.locator(value.locator()).required("kind").textValue()) || value.relevance().signum() < 0) mismatch();
    }

    private void verifySource(EnterpriseAccess.Admission admission, String projectId, String sourceId,
            String sourceVersion, String rightsRevision, String rightsPurpose) throws IOException {
        var proof = sources.require(admission, new SourceReadProtocol.ReadRequirement(sourceVersion, rightsRevision, projectId,
                null, null, null, SourceReadProtocol.Use.REVIEW, rightsPurpose, policy.environment(), policy.region(), null));
        if (!proof.source().sourceId().equals(sourceId) || !proof.source().sourceVersion().equals(sourceVersion)
                || !proof.rights().rightsRevision().equals(rightsRevision)) mismatch();
    }

    private void requireProject(EnterpriseAccess.Admission admission, String projectId) throws IOException {
        if (admission.formalWorker()) admission.formalProof().require(new PermissionProtocol.Scope(
                admission.context().tenant_id(), projectId, "PROJECT", projectId), "project.read", "PROJECT_READING");
        authority.requireProject(admission.context().tenant_id(), admission.bindingView().mode(), projectId,
                "project.read", "PROJECT_READING", permissions.resourceGrants(admission.actorId(), "project.read"));
    }

    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.actorId());
        values.put("identityMode", admission.bindingView().mode()); return values;
    }
    private static void current(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) denied(ErrorCode.CONTEXT_CHANGED);
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void one(int value) { if (value != 1) mismatch(); }
    private static <T> T single(List<T> values, ErrorCode empty) {
        if (values.isEmpty()) denied(empty); if (values.size() != 1) mismatch(); return values.get(0);
    }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
