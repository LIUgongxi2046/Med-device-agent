package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ContentChunk;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ContentReadMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class ArtifactContentService {
    private final EnterpriseAccess access;
    private final ArtifactSourceAuthority authority;
    private final ContentReadMapper mapper;
    private final SchemaCatalogue schemas;
    private final String responseManifest;

    public ArtifactContentService(EnterpriseAccess access, ArtifactSourceAuthority authority, ContentReadMapper mapper)
            throws IOException, NoSuchAlgorithmException {
        this.access = Objects.requireNonNull(access); this.authority = Objects.requireNonNull(authority); this.mapper = Objects.requireNonNull(mapper);
        schemas = SchemaCatalogue.fromClasspath();
        responseManifest = schemas.bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256();
    }

    /** A receipt proves this server's actual byte read, never browser receipt or complete consumption. */
    ContentChunk readPage(ApplicationSessionIdentity.Verified subject, ContentReadProtocol.ReadIntent intent,
            ObjectContentPaging paging, S3ObjectReader.TimedAdmission transport, int limitBytes, int maximumContentBytes,
            String cursor, String requestId, String domainStateSha256, Runnable requireDomainCurrent) throws Exception {
        Objects.requireNonNull(subject); Objects.requireNonNull(intent); Objects.requireNonNull(paging);
        Objects.requireNonNull(transport); Objects.requireNonNull(requireDomainCurrent); WireValues.uuid(requestId); WireValues.hash(domainStateSha256);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        if (intent.context().use() == SourceReadProtocol.Use.MODEL_SEND) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        requireDomainCurrent.run();
        var initial = admit(subject, intent); var registration = initial.verified().registration();
        var artifact = registration.artifact(); var binding = registration.binding();
        if (maximumContentBytes < 1 || artifact.sizeBytes().signum() <= 0
                || artifact.sizeBytes().compareTo(BigInteger.valueOf(maximumContentBytes)) > 0)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        String cursorAuthorization = WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of(
            "content_authorization_sha256", initial.authorization(), "domain_state_sha256", domainStateSha256)));
        Supplier<String> current = () -> {
            requireDomainCurrent.run(); requireCurrent(subject, intent, initial.authorization()); return cursorAuthorization;
        };
        var page = paging.read(artifact.reference(), current, limitBytes, cursor, transport);
        current.get(); transport.requireCurrent();
        String receipt = save(subject, intent, initial.authorization(), page, requestId);
        current.get(); transport.requireCurrent();
        return new ContentChunk(binding.resourceId(), artifact.artifactId(), binding.schemaRef(), page.mediaType(), page.encoding(),
            page.content(), page.chunkSha256(), page.fullSha256(), page.coverage(), receipt);
    }

    CompleteRead readComplete(ApplicationSessionIdentity.Verified subject, ContentReadProtocol.ReadIntent intent,
            ObjectContentPaging paging, int limitBytes, int maximumContentBytes, String requestId) throws Exception {
        Objects.requireNonNull(subject); Objects.requireNonNull(intent); Objects.requireNonNull(paging); WireValues.uuid(requestId);
        // 外部对象读取不继承企业事务，每次实际I/O独立执行当前准入。
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        if (intent.context().use() == SourceReadProtocol.Use.MODEL_SEND) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var initial = admit(subject, intent);
        var reference = initial.verified().registration().artifact().reference();
        var assembly = new ObjectContentAssembly(reference, maximumContentBytes);
        Supplier<String> current = () -> requireCurrent(subject, intent, initial.authorization());
        var receipts = new ArrayList<String>(); String cursor = null;
        do {
            var page = paging.read(reference, current, limitBytes, cursor);
            assembly.accept(page);
            // 回执只能由本方法的真实读取产生，客户端不能提交Page或字节声明。
            String receipt = save(subject, intent, initial.authorization(), page, requestId);
            receipts.add(receipt); cursor = page.coverage().nextCursor();
        } while (cursor != null);
        byte[] complete = assembly.content(); current.get();
        return new CompleteRead(this, subject, intent, initial.authorization(), complete, receipts);
    }

    final class CompleteRead {
        private final ArtifactContentService service;
        private final EvidenceReadAccess subject;
        private final ContentReadProtocol.ReadIntent intent;
        private final String authorization;
        private final byte[] bytes;
        private final List<String> receipts;

        private CompleteRead(ArtifactContentService service, ApplicationSessionIdentity.Verified subject, ContentReadProtocol.ReadIntent intent,
                String authorization, byte[] bytes, List<String> receipts) {
            this(service,EvidenceReadAccess.human(service.access,subject),intent,authorization,bytes,receipts);
        }
        private CompleteRead(ArtifactContentService service,EvidenceReadAccess subject,ContentReadProtocol.ReadIntent intent,
                String authorization,byte[] bytes,List<String> receipts) {
            this.service = service; this.subject = subject; this.intent = intent; this.authorization = authorization;
            this.bytes = bytes.clone(); this.receipts = List.copyOf(receipts);
        }

        void consume(Consumer<byte[]> consumer) {
            Objects.requireNonNull(consumer); service.requireCurrent(subject, intent, authorization);
            consumer.accept(bytes.clone());
        }

        void consumeJson(Consumer<SchemaCatalogue.ValidatedPayload> consumer) throws IOException {
            Objects.requireNonNull(consumer);
            service.requireCurrent(subject, intent, authorization);
            var required = intent.artifact();
            var payload = schemas.bindPublished(required.schemaRef(), required.schemaDocumentSha256(), bytes);
            service.requireCurrent(subject, intent, authorization); consumer.accept(payload);
        }
        SchemaCatalogue.ValidatedPayload jsonPayload() throws IOException {
            service.requireCurrent(subject, intent, authorization);
            var required = intent.artifact();
            var payload = schemas.bindPublished(required.schemaRef(), required.schemaDocumentSha256(), bytes);
            service.requireCurrent(subject, intent, authorization); return payload;
        }

        /** Exact current source-rights pins are part of the original read, not inferred lineage edges. */
        List<String> dependencyVersions() {
            var current=service.admit(subject,intent);
            if(!authorization.equals(current.authorization()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var ids=new java.util.TreeSet<String>(current.verified().versions().keySet());ids.add(intent.artifactId());
            for(var source:current.verified().sources().values())ids.add(source.rights().rightsRevision());
            return List.copyOf(ids);
        }
        List<String> receiptIds() { service.requireCurrent(subject, intent, authorization); return receipts; }
    }

    CompleteRead readVerifiedComplete(ApplicationSessionIdentity.Verified subject, ContentReadProtocol.ReadIntent intent,
            S3ObjectReader reader, S3ObjectReader.TimedAdmission transport, int maximumBytes, String requestId) throws Exception {
        return readVerifiedComplete(EvidenceReadAccess.human(access,subject),intent,reader,transport,maximumBytes,requestId);
    }
    CompleteRead readVerifiedComplete(EvidenceReadAccess subject,ContentReadProtocol.ReadIntent intent,
            S3ObjectReader reader,S3ObjectReader.TimedAdmission transport,int maximumBytes,String requestId)throws Exception {
        Objects.requireNonNull(subject); Objects.requireNonNull(intent); Objects.requireNonNull(reader); Objects.requireNonNull(transport);
        WireValues.uuid(requestId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        if (intent.context().use() == SourceReadProtocol.Use.MODEL_SEND) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var initial = admit(subject, intent); var reference = initial.verified().registration().artifact().reference();
        var objectSize = WireValues.revision(reference.sizeBytes());
        if (maximumBytes < 1 || objectSize.compareTo(java.math.BigInteger.valueOf(maximumBytes)) > 0)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        int size = objectSize.intValueExact();
        S3ObjectReader.TimedAdmission current = new S3ObjectReader.TimedAdmission() {
            @Override public void requireCurrent() {
                transport.requireCurrent(); ArtifactContentService.this.requireCurrent(subject, intent, initial.authorization()); transport.requireCurrent();
            }
            @Override public java.time.Duration remainingTime() { requireCurrent(); return transport.remainingTime(); }
        };
        byte[] bytes = new byte[size];
        try (var verified = reader.readVerified(reference, current)) {
            for (int start = 0; start < size;) {
                int length = Math.min(S3ObjectReader.MAX_SEGMENT_BYTES, size - start);
                System.arraycopy(verified.chunk(start, length), 0, bytes, start, length); start += length;
            }
            current.requireCurrent();
            String range = ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(reference), 0, size);
            var page = new ObjectContentPaging.Page(com.ruoyi.medical.contracts.InterfaceProtocol.Encoding.BASE64,
                java.util.Base64.getEncoder().encodeToString(bytes), reference.mediaType(), WireValues.sha256(bytes), verified.fullSha256(),
                "0", Integer.toString(size), new com.ruoyi.medical.contracts.CommonProtocol.Coverage(range, range, true, null));
            String receipt = save(subject, intent, initial.authorization(), page, requestId);
            current.requireCurrent(); return new CompleteRead(this, subject, intent, initial.authorization(), bytes, List.of(receipt));
        }
    }

    private Admission admit(ApplicationSessionIdentity.Verified subject,ContentReadProtocol.ReadIntent intent){return admit(EvidenceReadAccess.human(access,subject),intent);}
    private Admission admit(EvidenceReadAccess subject,ContentReadProtocol.ReadIntent intent) {
        return subject.execute(admission -> ThrowingSupplier.of(() -> {
            if (!intent.contextRevision().equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var verified = authority.require(admission, intent.artifactId(), intent.artifact(), intent.sources(), intent.context(), intent.upstreamArtifacts());
            subject.requireVersion(intent.artifactId(),verified.registration().artifact().sha256());
            return new Admission(verified, ContentReadAuthorization.digest(admission, intent, verified,subject));
        }).get());
    }

    private String requireCurrent(ApplicationSessionIdentity.Verified subject, ContentReadProtocol.ReadIntent intent, String original) {
        return requireCurrent(EvidenceReadAccess.human(access,subject),intent,original);
    }
    private String requireCurrent(EvidenceReadAccess subject,ContentReadProtocol.ReadIntent intent,String original) {
        String current = admit(subject, intent).authorization();
        if (!original.equals(current)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return current;
    }

    private String save(ApplicationSessionIdentity.Verified subject,ContentReadProtocol.ReadIntent intent,String original,ObjectContentPaging.Page page,String requestId){return save(EvidenceReadAccess.human(access,subject),intent,original,page,requestId);}
    private String save(EvidenceReadAccess subject,ContentReadProtocol.ReadIntent intent,String original,
            ObjectContentPaging.Page page,String requestId) {
        return subject.execute(admission -> ThrowingSupplier.of(() -> {
            var verified = authority.require(admission, intent.artifactId(), intent.artifact(), intent.sources(), intent.context(), intent.upstreamArtifacts());
            subject.requireVersion(intent.artifactId(),verified.registration().artifact().sha256());
            if (!original.equals(ContentReadAuthorization.digest(admission, intent, verified,subject))) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var registered = verified.registration(); var artifact = registered.artifact(); var binding = registered.binding();
            String user = admission.actorId(), receiptId = UUID.randomUUID().toString(), auditId = UUID.randomUUID().toString();
            var values = new HashMap<String, Object>(); values.put("userId", user); values.put("receiptId", receiptId); values.put("auditId", auditId);
            var time = Objects.requireNonNull(mapper.databaseTime(values));
            var header = new ContentReadProtocol.HeaderRow(artifact.tenantId(), artifact.projectId(), receiptId, binding.resourceId(), artifact.artifactId(),
                user, null, null, null, page.coverage().selectionSha256(), page.coverage().deliveredSha256(), page.fullSha256(),
                admission.watermarks().authEpoch(), admission.watermarks().governanceEpoch(), time, user, time, artifact.classification());
            var range = new ContentReadProtocol.RangeRow(artifact.tenantId(), artifact.projectId(), receiptId, artifact.artifactId(), binding.resourceKind(),
                binding.resourceId(), binding.schemaRef(), binding.schemaDocumentSha256(), binding.accessSetId(), binding.dependencyManifestSha256(),
                ObjectContentPaging.objectDigest(artifact.reference()), WireValues.revision(page.startByte()), WireValues.revision(page.endByte()),
                artifact.sizeBytes(), page.chunkSha256(), intent.artifact().purpose(), original, new BigInteger(admission.context().context_revision()),
                user, time, artifact.classification());
            var chunk = new ContentChunk(binding.resourceId(), artifact.artifactId(), binding.schemaRef(), page.mediaType(), page.encoding(),
                page.content(), page.chunkSha256(), page.fullSha256(), page.coverage(), receiptId);
            // 使用完整公开Schema核验传输字段，私有对象地址不进入响应或审计。
            var chunkTree = new com.fasterxml.jackson.databind.json.JsonMapper().setPropertyNamingStrategy(
                com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).valueToTree(chunk);
            schemas.validate("interfaces", chunkTree, "ContentChunk");
            String resource = new String(ContentReadAuthorization.canonicalBytes(Map.of("resource_kind", binding.resourceKind(), "resource_id", binding.resourceId(),
                "artifact_id", artifact.artifactId(), "read_receipt_id", receiptId, "authorization_sha256", original,
                "selection_sha256", header.selectionSha256(), "delivered_sha256", header.deliveredSha256())), StandardCharsets.UTF_8);
            var audit = new ContentReadProtocol.AuditRow(artifact.tenantId(), artifact.projectId(), auditId, user, "artifact.content.read", resource,
                "SUCCESS", null, requestId, requestId, "1.0.0", responseManifest, user, time, artifact.classification());
            values.put("header", header); values.put("range", range); values.put("audit", audit);
            if (mapper.saveHeader(values) != 1 || mapper.saveRange(values) != 1 || mapper.saveAudit(values) != 1) mismatch();
            var headers = mapper.header(values); var ranges = mapper.range(values); var audits = mapper.audit(values);
            if (headers.size() != 1 || !header.equals(headers.get(0)) || ranges.size() != 1 || !range.equals(ranges.get(0)) || audits.size() != 1) mismatch();
            var saved = audits.get(0);
            var comparable = new ContentReadProtocol.AuditRow(saved.tenantId(), saved.projectId(), saved.auditId(), saved.subjectId(), saved.action(), resource,
                saved.result(), saved.reasonCode(), saved.requestId(), saved.traceId(), saved.schemaVersion(), saved.schemaManifest(), saved.createdBy(), saved.createdAt(), saved.classification());
            if (!audit.equals(comparable) || !ContentReadAuthorization.sameJson(resource, saved.resource())) mismatch();
            return receiptId;
        }).get());
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private record Admission(ArtifactSourceAuthority.VerifiedSources verified, String authorization) { }
}
