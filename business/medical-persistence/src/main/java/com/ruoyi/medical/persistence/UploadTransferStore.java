package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.UploadTransferProtocol.BindingRow;
import com.ruoyi.medical.persistence.UploadTransferProtocol.GrantRow;
import com.ruoyi.medical.persistence.UploadTransferProtocol.PartRow;
import com.ruoyi.medical.persistence.UploadTransferProtocol.SessionRow;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class UploadTransferStore {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.UploadTransferMapper.";
    private static final BigInteger MAX_PART_NO = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final UploadTransferMapper mapper;
    private final UploadAuthorizationSchema authorizations;

    public UploadTransferStore(UploadTransferMapper mapper, UploadAuthorizationSchema authorizations) {
        this.mapper = Objects.requireNonNull(mapper); this.authorizations = Objects.requireNonNull(authorizations);
    }

    // 这些内部方法由领域消费者在已核查业务授权的EnterpriseAccess短事务内调用。
    SessionRow session(EnterpriseAccess.Admission admission, String uploadId) throws IOException {
        var values = values(admission, uploadId);
        var row = one(mapper.session(values));
        identity(admission, row.tenantId(), row.subjectId(), row.createdBy());
        WireValues.uuid(row.uploadId()); positive(row.expectedSize()); unsigned(row.receivedSize()); positive(row.revision());
        if (!row.uploadId().equals(uploadId) || !UploadTransferProtocol.PART_BYTES.equals(row.maxPartBytes())
                || row.receivedSize().compareTo(row.expectedSize()) > 0) mismatch();
        key(row.objectKey(), row.objectKeySha256()); metadata(row.schemaVersion(), row.schemaManifestSha256(), row.classification());
        times(row.createdAt(), row.updatedAt(), row.expiresAt());
        if (!Set.of("ACTIVE", "COMPLETING", "COMPLETED", "CANCELLED", "EXPIRED", "FAILED").contains(row.state())) mismatch();
        WireValues.boundedString(row.mediaType(), 128); if (row.mediaType().isEmpty()) mismatch();
        if (row.expectedSha256() != null) WireValues.hash(row.expectedSha256());
        if ("SOURCE".equals(row.purpose())) {
            if (row.projectId() == null || row.sourceUploadId() == null || row.grantId() != null) mismatch();
            WireValues.uuid(row.projectId()); WireValues.uuid(row.sourceUploadId());
            var source = one(mapper.source(values));
            identity(admission, source.tenantId(), row.subjectId(), source.createdBy());
            if (!source.uploadId().equals(row.sourceUploadId()) || !source.projectId().equals(row.projectId())
                    || !source.expectedSize().equals(row.expectedSize()) || !Objects.equals(source.expectedSha256(), row.expectedSha256())
                    || !source.expiresAt().equals(row.expiresAt())) mismatch();
        } else {
            if (!Set.of("DOCUMENT_BODY", "AGENT_ARTIFACT").contains(row.purpose()) || row.grantId() == null
                    || row.sourceUploadId() != null || row.expectedSha256() == null) mismatch();
            WireValues.uuid(row.grantId());
            var grant = one(mapper.grant(values));
            identity(admission, grant.tenantId(), grant.subjectId(), grant.createdBy());
            if (!grant.grantId().equals(row.grantId()) || !Objects.equals(grant.projectId(), row.projectId())
                    || !grant.purpose().equals(row.purpose()) || !grant.objectKey().equals(row.objectKey())
                    || !grant.sha256().equals(row.expectedSha256()) || !grant.sizeBytes().equals(row.expectedSize())
                    || !grant.mediaType().equals(row.mediaType()) || !grant.expiresAt().equals(row.expiresAt())) mismatch();
        }
        if (row.objectVersion() == null) {
            if (row.contentSha256() != null || row.objectRef() != null || "COMPLETED".equals(row.state())) mismatch();
        } else {
            var reference = new ObjectRef(row.objectKey(), row.objectVersion(), row.contentSha256(), row.expectedSize().toString(), row.mediaType());
            UploadTransferObjects.decode(row.objectRef(), reference);
            if (row.expectedSha256() != null && !row.expectedSha256().equals(reference.sha256())) mismatch();
            if ("COMPLETED".equals(row.state()) && !row.expectedSize().equals(row.receivedSize())) mismatch();
        }
        return row;
    }

    BindingRow binding(EnterpriseAccess.Admission admission, String uploadId) throws IOException {
        var session = session(admission, uploadId);
        if ("SOURCE".equals(session.purpose())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var values = values(admission, uploadId); var row = one(mapper.binding(values)); var grant = one(mapper.grant(values));
        identity(admission, row.tenantId(), row.subjectId(), row.createdBy());
        if (!row.uploadId().equals(uploadId) || !row.grantId().equals(session.grantId())
                || !Objects.equals(row.projectId(), session.projectId()) || !row.purpose().equals(session.purpose())
                || !Objects.equals(row.documentId(), grant.documentId()) || !Objects.equals(row.runId(), grant.runId())
                || !Objects.equals(row.stepId(), grant.stepId())) mismatch();
        if ("DOCUMENT_BODY".equals(row.purpose())) {
            if (row.projectId() == null || row.documentId() == null || row.runId() != null || row.stepId() != null
                    || row.principalId() != null || row.leaseGeneration() != null) mismatch();
            WireValues.uuid(row.documentId()); unsigned(row.expectedRevision()); unsigned(row.contextRevision());
        } else {
            if (row.documentId() != null || row.expectedRevision() != null || row.contextRevision() != null) mismatch();
            WireValues.uuid(row.runId()); WireValues.uuid(row.stepId()); WireValues.uuid(row.principalId()); positive(row.leaseGeneration());
        }
        WireValues.uuid(row.dependencyAccessSetId()); WireValues.hash(row.dependencyManifestSha256());
        WireValues.hash(row.schemaDocumentSha256()); WireValues.hash(row.authorizationSha256());
        authorizations.requireBinding(row, grant);
        metadata(row.schemaVersion(), row.schemaManifestSha256(), row.classification());
        return row;
    }

    SessionRow createSourceSession(EnterpriseAccess.Admission admission, SessionRow row) throws IOException {
        values(admission, row.uploadId()); initial(admission, row);
        if (!"SOURCE".equals(row.purpose())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        changed(mapper.saveSession(withRow(values(admission, row.uploadId()), row)));
        var saved = session(admission, row.uploadId()); equal(row, saved); return saved;
    }

    BindingRow createArtifactSession(EnterpriseAccess.Admission admission, GrantRow grant, SessionRow row,
            BindingRow binding) throws IOException {
        values(admission, row.uploadId()); initial(admission, row);
        identity(admission, grant.tenantId(), grant.subjectId(), grant.createdBy());
        identity(admission, binding.tenantId(), binding.subjectId(), binding.createdBy());
        authorizations.requireBinding(binding, grant);
        if ("SOURCE".equals(row.purpose()) || !"ISSUED".equals(grant.status()) || grant.artifactId() != null
                || grant.objectVersion() != null || grant.objectIdentitySha256() != null
                || !BigInteger.ONE.equals(grant.revision()) || !grant.createdAt().equals(grant.updatedAt())) mismatch();
        changed(mapper.saveGrant(withRow(values(admission, row.uploadId()), grant)));
        changed(mapper.saveSession(withRow(values(admission, row.uploadId()), row)));
        changed(mapper.saveBinding(withRow(values(admission, row.uploadId()), binding)));
        equal(row, session(admission, row.uploadId())); equal(grant, one(mapper.grant(values(admission, row.uploadId()))));
        var saved = binding(admission, row.uploadId()); equal(binding, saved); return saved;
    }

    PartRow reserve(EnterpriseAccess.Admission admission, PartRow row) throws IOException {
        var values = values(admission, row.uploadId()); var session = session(admission, row.uploadId()); active(values, session);
        identity(admission, row.tenantId(), session.subjectId(), row.createdBy()); partMetadata(session, row);
        if (!"RESERVED".equals(row.state()) || !BigInteger.ONE.equals(row.revision()) || row.objectVersion() != null
                || row.objectRef() != null || !row.createdAt().equals(row.updatedAt())) mismatch();
        if (!mapper.part(partValues(values, row.partNo())).isEmpty()) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        changed(mapper.savePart(withRow(values, row)));
        var saved = part(admission, row.uploadId(), row.partNo()); equal(row, saved); return saved;
    }

    PartRow part(EnterpriseAccess.Admission admission, String uploadId, long partNo) throws IOException {
        return partIfPresent(admission, uploadId, partNo).orElseThrow(() -> new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }

    java.util.Optional<PartRow> partIfPresent(EnterpriseAccess.Admission admission, String uploadId, long partNo) throws IOException {
        var values = values(admission, uploadId); var session = session(admission, uploadId);
        range(session.expectedSize(), partNo);
        var rows = mapper.part(partValues(values, partNo));
        if (rows.isEmpty()) return java.util.Optional.empty();
        var row = one(rows); identity(admission, row.tenantId(), session.subjectId(), row.createdBy());
        partMetadata(session, row); storedPart(row); return java.util.Optional.of(row);
    }

    List<PartRow> parts(EnterpriseAccess.Admission admission, String uploadId, long afterPart, int limit) throws IOException {
        var values = values(admission, uploadId); var session = session(admission, uploadId);
        if (afterPart < 0 || afterPart > MAX_PART_NO.longValueExact() || limit < 1 || limit > 100)
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        values.put("afterPart", afterPart); values.put("limit", limit); var rows = List.copyOf(mapper.parts(values));
        if (rows.size() > limit) mismatch();
        long previous = afterPart;
        for (var row : rows) {
            identity(admission, row.tenantId(), session.subjectId(), row.createdBy()); partMetadata(session, row); storedPart(row);
            if (row.partNo() <= previous) mismatch(); previous = row.partNo();
        }
        return rows;
    }

    PartRow beginPart(EnterpriseAccess.Admission admission, String uploadId, long partNo, String expected, String writeId) throws IOException {
        var values = mutation(admission, uploadId, expected); var session = session(admission, uploadId); active(values, session);
        var row = part(admission, uploadId, partNo); revision(row.revision(), expected); writingIdentity(row, writeId);
        if (!"RESERVED".equals(row.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        values.put("partNo", partNo); values.put("writeId", writeId); changed(mapper.beginPart(values));
        return changedPart(admission, row, "WRITING", null);
    }

    PartRow storePart(EnterpriseAccess.Admission admission, String uploadId, long partNo, String expected, String writeId,
            ObjectRef actual) throws IOException {
        var values = mutation(admission, uploadId, expected); var session = session(admission, uploadId); active(values, session);
        var row = part(admission, uploadId, partNo); revision(row.revision(), expected); writingIdentity(row, writeId);
        if (!"WRITING".equals(row.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        if (!row.objectKey().equals(actual.objectKey()) || !row.sha256().equals(actual.sha256())
                || !row.sizeBytes().toString().equals(actual.sizeBytes()) || !"application/octet-stream".equals(actual.mediaType())) mismatch();
        values.put("partNo", partNo); values.put("writeId", writeId); values.put("objectVersion", actual.objectVersion());
        values.put("objectRef", UploadTransferObjects.encode(actual)); changed(mapper.storePart(values));
        var saved = changedPart(admission, row, "STORED", actual);
        var receive = mutation(admission, uploadId, session.revision().toString());
        var received = mapper.storedSize(receive); unsigned(received);
        if (!received.equals(session.receivedSize().add(row.sizeBytes()))) mismatch();
        receive.put("receivedSize", received); changed(mapper.receive(receive));
        var updated = session(admission, uploadId);
        if (!updated.revision().equals(session.revision().add(BigInteger.ONE))
                || !updated.receivedSize().equals(session.receivedSize().add(row.sizeBytes()))) mismatch();
        return saved;
    }

    PartRow failPart(EnterpriseAccess.Admission admission, String uploadId, long partNo, String expected, String writeId) throws IOException {
        var values = mutation(admission, uploadId, expected); var session = session(admission, uploadId); active(values, session);
        var row = part(admission, uploadId, partNo); revision(row.revision(), expected); writingIdentity(row, writeId);
        if (!"WRITING".equals(row.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        values.put("partNo", partNo); values.put("writeId", writeId); changed(mapper.failPart(values));
        return changedPart(admission, row, "FAILED", null);
    }

    SessionRow beginCompletion(EnterpriseAccess.Admission admission, String uploadId, String expected) throws IOException {
        var values = mutation(admission, uploadId, expected); var row = session(admission, uploadId); revision(row.revision(), expected); active(values, row);
        if (!row.receivedSize().equals(row.expectedSize())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        BigInteger end = BigInteger.ZERO; long after = 0;
        while (true) {
            var page = parts(admission, uploadId, after, 100);
            if (page.isEmpty()) break;
            for (var part : page) {
                if (!"STORED".equals(part.state()) || part.partNo() != after + 1 || !part.startByte().equals(end))
                    throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
                after = part.partNo(); end = part.endByte();
            }
        }
        if (!end.equals(row.expectedSize())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        changed(mapper.beginCompletion(values)); return changedSession(admission, row, "COMPLETING", null);
    }

    SessionRow complete(EnterpriseAccess.Admission admission, String uploadId, String expected, ObjectRef actual) throws IOException {
        var values = mutation(admission, uploadId, expected); var row = session(admission, uploadId); revision(row.revision(), expected);
        if (!"COMPLETING".equals(row.state()) || !row.expiresAt().isAfter(mapper.databaseTime(values)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        activeOrigin(values, row);
        if (!row.objectKey().equals(actual.objectKey()) || !row.expectedSize().toString().equals(actual.sizeBytes())
                || !row.mediaType().equals(actual.mediaType()) || row.expectedSha256() != null && !row.expectedSha256().equals(actual.sha256())) mismatch();
        values.put("objectVersion", actual.objectVersion()); values.put("contentSha256", actual.sha256());
        values.put("objectRef", UploadTransferObjects.encode(actual)); changed(mapper.complete(values));
        return changedSession(admission, row, "COMPLETED", actual);
    }

    SessionRow cancel(EnterpriseAccess.Admission admission, String uploadId, String expected) throws IOException {
        var values = mutation(admission, uploadId, expected); var row = session(admission, uploadId); revision(row.revision(), expected);
        if (!Set.of("ACTIVE", "COMPLETING").contains(row.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        changed(mapper.cancel(values)); return changedSession(admission, row, "CANCELLED", null);
    }

    SessionRow expire(EnterpriseAccess.Admission admission, String uploadId, String expected) throws IOException {
        var values = mutation(admission, uploadId, expected); var row = session(admission, uploadId); revision(row.revision(), expected);
        if (!Set.of("ACTIVE", "COMPLETING").contains(row.state()) || row.expiresAt().isAfter(mapper.databaseTime(values)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        changed(mapper.expire(values)); return changedSession(admission, row, "EXPIRED", null);
    }

    private void initial(EnterpriseAccess.Admission admission, SessionRow row) {
        identity(admission, row.tenantId(), row.subjectId(), row.createdBy());
        if (!"ACTIVE".equals(row.state()) || !BigInteger.ONE.equals(row.revision()) || row.receivedSize().signum() != 0
                || row.objectVersion() != null || row.objectRef() != null || row.contentSha256() != null
                || !row.createdAt().equals(row.updatedAt()) || !row.expiresAt().isAfter(mapper.databaseTime(values(admission, row.uploadId())))) mismatch();
        if (partCount(row.expectedSize()).compareTo(MAX_PART_NO) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
    }

    private void active(Map<String, Object> values, SessionRow row) {
        if (!"ACTIVE".equals(row.state()) || !row.expiresAt().isAfter(mapper.databaseTime(values)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        activeOrigin(values, row);
    }

    private void activeOrigin(Map<String, Object> values, SessionRow row) {
        if ("SOURCE".equals(row.purpose())) {
            if (!Set.of("PENDING", "UPLOADING").contains(one(mapper.source(values)).status()))
                throw new TenantAccessException(ErrorCode.ACTION_STALE);
        } else if (!Set.of("ISSUED", "UPLOADING").contains(one(mapper.grant(values)).status()))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }

    private PartRow changedPart(EnterpriseAccess.Admission admission, PartRow before, String state, ObjectRef reference) throws IOException {
        var saved = part(admission, before.uploadId(), before.partNo());
        var expected = new PartRow(before.tenantId(), before.projectId(), before.uploadId(), before.partNo(), before.writeId(),
            before.totalSize(), before.maxPartBytes(), before.startByte(), before.endByte(), before.sizeBytes(), before.sha256(),
            before.objectKey(), before.objectKeySha256(), state, reference == null ? null : reference.objectVersion(),
            reference == null ? null : UploadTransferObjects.encode(reference), before.schemaVersion(), before.schemaManifestSha256(),
            before.createdBy(), before.createdAt(), before.classification(), before.revision().add(BigInteger.ONE), saved.updatedAt());
        equal(expected, saved); return saved;
    }

    private SessionRow changedSession(EnterpriseAccess.Admission admission, SessionRow before, String state, ObjectRef reference) throws IOException {
        var saved = session(admission, before.uploadId());
        var expected = new SessionRow(before.tenantId(), before.projectId(), before.uploadId(), before.subjectId(), before.purpose(),
            before.sourceUploadId(), before.grantId(), before.expectedSize(), before.expectedSha256(), before.receivedSize(), before.mediaType(),
            before.objectKey(), before.objectKeySha256(), before.maxPartBytes(), before.expiresAt(), state,
            reference == null ? before.objectVersion() : reference.objectVersion(), reference == null ? before.contentSha256() : reference.sha256(),
            reference == null ? before.objectRef() : UploadTransferObjects.encode(reference), before.schemaVersion(), before.schemaManifestSha256(),
            before.createdBy(), before.createdAt(), before.classification(), before.revision().add(BigInteger.ONE), saved.updatedAt());
        equal(expected, saved); return saved;
    }

    private static void partMetadata(SessionRow session, PartRow row) throws IOException {
        if (!row.uploadId().equals(session.uploadId()) || !Objects.equals(row.projectId(), session.projectId())
                || !row.totalSize().equals(session.expectedSize()) || !row.maxPartBytes().equals(session.maxPartBytes())) mismatch();
        WireValues.uuid(row.writeId()); WireValues.hash(row.sha256()); positive(row.revision());
        var range = range(row.totalSize(), row.partNo());
        if (!range.start().equals(row.startByte()) || !range.end().equals(row.endByte()) || !range.size().equals(row.sizeBytes())) mismatch();
        key(row.objectKey(), row.objectKeySha256()); metadata(row.schemaVersion(), row.schemaManifestSha256(), row.classification());
        if (row.createdAt() == null || row.updatedAt() == null || row.updatedAt().isBefore(row.createdAt())) mismatch();
        if (!Set.of("RESERVED", "WRITING", "STORED", "FAILED").contains(row.state())) mismatch();
    }

    private static void storedPart(PartRow row) throws IOException {
        if (row.objectVersion() == null) {
            if (row.objectRef() != null || "STORED".equals(row.state())) mismatch();
        } else {
            if (!"STORED".equals(row.state())) mismatch();
            UploadTransferObjects.decode(row.objectRef(), new ObjectRef(row.objectKey(), row.objectVersion(), row.sha256(),
                row.sizeBytes().toString(), "application/octet-stream"));
        }
    }

    static BigInteger partCount(BigInteger total) {
        positive(total); return total.subtract(BigInteger.ONE).divide(UploadTransferProtocol.PART_BYTES).add(BigInteger.ONE);
    }

    static PartRange range(BigInteger total, long partNo) {
        if (partNo < 1 || BigInteger.valueOf(partNo).compareTo(MAX_PART_NO) > 0) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        var count = partCount(total); if (count.compareTo(MAX_PART_NO) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (BigInteger.valueOf(partNo).compareTo(count) > 0) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        var start = BigInteger.valueOf(partNo - 1).multiply(UploadTransferProtocol.PART_BYTES);
        var end = start.add(UploadTransferProtocol.PART_BYTES).min(total); return new PartRange(start, end, end.subtract(start));
    }

    record PartRange(BigInteger start, BigInteger end, BigInteger size) { }

    private static Map<String, Object> values(EnterpriseAccess.Admission admission, String uploadId) {
        if (admission == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        TenantScope.requireSqlAccess(PREFIX + "databaseTime");
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id()))
            throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var values = new HashMap<String, Object>(); values.put("userId", admission.subject().user().user_id());
        TenantScope.requireSubjectParameter(PREFIX + "databaseTime", values); WireValues.uuid(uploadId);
        values.put("uploadId", uploadId); values.put("lock", true); return values;
    }

    private static Map<String, Object> mutation(EnterpriseAccess.Admission admission, String uploadId, String expected) {
        var values = values(admission, uploadId); positive(WireValues.revision(expected));
        values.put("expected", expected); return values;
    }

    private static Map<String, Object> partValues(Map<String, Object> values, long partNo) { values.put("partNo", partNo); return values; }
    private static Map<String, Object> withRow(Map<String, Object> values, Object row) { values.put("row", row); return values; }
    private static <T> T one(List<T> rows) {
        if (rows.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return rows.get(0);
    }
    private static void identity(EnterpriseAccess.Admission admission, String tenant, String subject, String createdBy) {
        if (!admission.context().tenant_id().equals(tenant) || !admission.subject().user().user_id().equals(subject)
                || !subject.equals(createdBy)) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private static void revision(BigInteger actual, String expected) {
        if (!actual.equals(WireValues.revision(expected))) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }
    private static void writingIdentity(PartRow row, String writeId) {
        WireValues.uuid(writeId); if (!row.writeId().equals(writeId)) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void positive(BigInteger value) { unsigned(value); if (value.signum() <= 0) mismatch(); }
    private static void unsigned(BigInteger value) { WireValues.revision(Objects.requireNonNull(value).toString()); }
    private static void key(String key, String digest) throws IOException {
        WireValues.boundedString(key, 1024); WireValues.hash(digest);
        StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(key));
        if (key.isEmpty() || !DigestUtils.sha256Hex(key.getBytes(StandardCharsets.UTF_8)).equals(digest)) mismatch();
    }
    private static void metadata(String schema, String manifest, String classification) {
        WireValues.boundedString(schema, 32); WireValues.hash(manifest); WireValues.boundedString(classification, 24);
        if (schema.isEmpty() || classification.isEmpty()) mismatch();
    }
    private static void times(LocalDateTime created, LocalDateTime updated, LocalDateTime expires) {
        if (created == null || updated == null || expires == null || updated.isBefore(created) || !expires.isAfter(created)) mismatch();
    }
    private static void equal(Object expected, Object actual) throws IOException {
        JsonNode expectedNode = JSON.valueToTree(expected); JsonNode actualNode = JSON.valueToTree(actual);
        for (String field : Set.of("objectRef", "authorizationManifest")) {
            if (expectedNode.hasNonNull(field)) ((com.fasterxml.jackson.databind.node.ObjectNode) expectedNode)
                .set(field, JSON.readTree(expectedNode.required(field).textValue()));
            if (actualNode.hasNonNull(field)) ((com.fasterxml.jackson.databind.node.ObjectNode) actualNode)
                .set(field, JSON.readTree(actualNode.required(field).textValue()));
        }
        if (!expectedNode.equals(actualNode)) mismatch();
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
