package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.CollectionMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class CollectionService {
    private final EnterpriseAccess access;
    private final CollectionMapper mapper;
    private final PermissionMapper permissions;
    private final CollectionAuthority authority;
    private final CollectionSchema schema;
    private final PermissionSchema json;
    private final CollectionPolicy policy;
    private final IdentityDirectoryPolicy identities;

    public CollectionService(EnterpriseAccess access, CollectionMapper mapper, PermissionMapper permissions,
            CollectionAuthority authority, CollectionSchema schema, PermissionSchema json,
            CollectionPolicy policy, IdentityDirectoryPolicy identities) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.schema = schema; this.json = json; this.policy = policy; this.identities = identities;
    }
    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) throws IOException {
        var request = schema.create(bytes);
        return mutate(subject, context, key, null, request.owner_project_id(), request.content(), null, null, request.reason(), request);
    }
    public JsonNode version(ApplicationSessionIdentity.Verified subject, String context, String key, String id, byte[] bytes) throws IOException {
        var request = schema.version(bytes);
        return mutate(subject, context, key, id, null, request.content(), request.expected_revision(), request.base_version(), request.reason(), request);
    }
    private JsonNode mutate(ApplicationSessionIdentity.Verified subject, String context, String key, String id, String requestedProject,
            CollectionProtocol.Content content, String expected, PermissionProtocol.Basis base, String reason, Object request) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, context);
            var head = id == null ? null : authority.head(admission, id);
            String project = head == null ? requestedProject : head.projectId();
            var grant = authority.grant(admission, project, "collection.write", content.purpose());
            authority.grant(admission, project, "collection.read", content.purpose());
            String operationName = id == null ? "KN-11" : "KN-12";
            var values = CollectionAuthority.values(admission); values.put("operationName", operationName); values.put("key", key);
            values.put("requestHash", schema.requestHash(id == null ? "/api/collections" : "/api/collections/" + id + "/versions",
                    request, context, subject.sessionId()));
            var prior = mapper.operation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty() && !prior.get(0).requestSha256().equals(values.get("requestHash")))
                throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var members = authority.prepare(admission, project, content, base);
            if (!prior.isEmpty()) {
                var operation = prior.get(0);
                if (!operation.state().equals("COMPLETED") || !Objects.equals(operation.httpStatus(), 201)
                        || !operation.schemaVersion().equals("1.0.0") || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
                var response = schema.receipt(operation.responseRef()); var recorded = response.required("data");
                if (!recorded.required("operation_id").textValue().equals(operation.operationId())) mismatch();
                var actual = authority.require(admission, operation.targetId(), content.purpose(), policy.environment(), policy.region(), policy.limits());
                var manifest = actual.manifest();
                if (id != null && !manifest.collection_id().equals(id) || !Objects.equals(project, manifest.owner_project_id())
                        || !manifest.title().equals(content.title()) || !manifest.purpose().equals(content.purpose())
                        || !manifest.members().equals(members) || !Objects.equals(base, manifest.base_version()) || !manifest.reason().equals(reason)
                        || !manifest.created_by().equals(subject.user().user_id())
                        || !json.tree(authority.view(actual)).equals(recorded.required("collection"))) mismatch();
                return response;
            }
            if (head != null) {
                if (!head.revision().toString().equals(expected) || !head.currentVersion().equals(base.version_id())) conflict();
                var previous = authority.require(admission, base.version_id(), content.purpose(), policy.environment(), policy.region(), policy.limits());
                if (!previous.head().collectionId().equals(id) || !previous.version().contentSha256().equals(base.content_sha256())) conflict();
            }
            String collectionId = id == null ? UUID.randomUUID().toString() : id, versionId = UUID.randomUUID().toString();
            String revision = head == null ? "1" : PermissionSchema.next(expected);
            var now = Objects.requireNonNull(permissions.databaseTime());
            var manifest = new CollectionProtocol.Manifest("1.0.0", admission.context().tenant_id(), collectionId, versionId, project,
                    content.title(), content.purpose(), members, base, revision, reason, grant.grantId(), grant.revision().toString(),
                    subject.user().user_id(), now.toInstant(ZoneOffset.UTC).toString());
            schema.verify("Manifest", json.tree(manifest));
            String canonical = json.canonicalObject(manifest), hash = DigestUtils.sha256Hex(canonical);
            values.put("collectionId", collectionId); values.put("versionId", versionId); values.put("projectId", project);
            values.put("title", content.title()); values.put("purpose", content.purpose()); values.put("manifest", canonical);
            values.put("hash", hash); values.put("schemaManifest", schema.manifestSha256()); values.put("now", now);
            values.put("classification", identities.classification()); values.put("expected", head == null ? "1" : expected);
            values.put("resulting", revision); values.put("baseId", base == null ? null : base.version_id());
            if (head == null) one(mapper.insertHead(values));
            one(mapper.insertRegisteredVersion(values)); one(mapper.insertVersion(values));
            for (var member : members) {
                values.put("member", member); one(mapper.insertMember(values));
                relation(values, member.source_version(), "COLLECTION_SOURCE");
                relation(values, member.parse_version(), "COLLECTION_PARSE");
                relation(values, member.rights_revision(), "COLLECTION_RIGHTS");
            }
            if (base != null) relation(values, base.version_id(), "COLLECTION_BASE");
            if (mapper.advanceHead(values) != 1) conflict();
            var proof = authority.require(admission, versionId, content.purpose(), policy.environment(), policy.region(), policy.limits());
            if (!proof.manifest().equals(manifest) || !proof.head().currentVersion().equals(versionId) || !proof.head().revision().toString().equals(revision)) mismatch();
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var created = json.tree(new CollectionProtocol.Created("1.0.0", operationId, authority.view(proof)));
            var response = schema.envelope("Created", created, requestId, revision);
            values.put("operationId", operationId); values.put("response", json.canonical(response)); one(mapper.saveOperation(values));
            var audit = authorization(admission, proof); audit.put("operation_id", operationId); audit.set("request", json.tree(request));
            audit.set("before", json.tree(base)); audit.set("after", created); audit.put("authority_grant_id", grant.grantId());
            audit.put("authority_revision", grant.revision().toString()); schema.verify("Audit", audit);
            var event = authorization(admission, proof); event.set("creation", created); schema.verify("Event", event);
            String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
            String action = head == null ? "collection.created" : "collection.version_created";
            one(permissions.saveAudit(subject.user().user_id(), project, auditId, action, json.canonical(audit), requestId,
                    schema.manifestSha256(), identities.classification()));
            one(permissions.saveEvent(subject.user().user_id(), eventId, collectionId, revision, action, json.canonical(event)));
            values.put("auditId", auditId); values.put("eventId", eventId); values.put("action", action); values.put("requestId", requestId);
            if (!schema.read(single(mapper.audit(values))).equals(audit) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
            var operation = single(mapper.operation(values));
            if (!operation.operationId().equals(operationId) || !operation.targetId().equals(versionId)
                    || !operation.requestSha256().equals(values.get("requestHash")) || !schema.receipt(operation.responseRef()).equals(response)) mismatch();
            authority.require(admission, versionId, content.purpose(), policy.environment(), policy.region(), policy.limits());
            return response;
        }).get());
    }

    public JsonNode history(ApplicationSessionIdentity.Verified subject, String context, String collectionId, CollectionProtocol.Query query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, context); var head = authority.head(admission, collectionId);
            if (head.currentVersion() == null) mismatch();
            if (head.revision().compareTo(java.math.BigInteger.valueOf(policy.maximumVersions())) > 0)
                throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var current = authority.require(admission, head.currentVersion(),
                    currentPurpose(admission, head.currentVersion()), policy.environment(), policy.region(), policy.limits());
            if (query.watermark() != null && !head.revision().toString().equals(query.watermark())) conflict();
            var values = CollectionAuthority.values(admission); values.put("collectionId", collectionId);
            // PageHelper 负责计数、分页及线程状态清理；企业拦截器继续核验其计数查询。
            var page = PageHelper.startPage(query.page(), query.pageSize()).<CollectionProtocol.Version>doSelectPageInfo(() -> mapper.versions(values));
            if (page.getTotal() > policy.maximumVersions()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            if (page.getTotal() != head.revision().longValueExact()) mismatch();
            var items = json.object().arrayNode();
            for (var row : page.getList()) {
                var proof = row.versionId().equals(current.version().versionId()) ? current : authority.require(admission, row.versionId(),
                        row.purpose(), policy.environment(), policy.region(), policy.limits());
                if (!proof.head().collectionId().equals(collectionId) || !proof.version().equals(row)) mismatch();
                items.add(json.tree(authority.view(proof)));
            }
            var data = json.object(); data.put("schema_version", "1.0.0"); data.put("collection_id", collectionId);
            data.set("owner_project_id", json.tree(head.projectId())); data.put("title", head.title());
            data.put("current_version", head.currentVersion()); data.put("revision", head.revision().toString()); data.set("items", items);
            var filter = json.object(); filter.put("collection_id", collectionId); filter.put("page_size", query.pageSize());
            filter.put("subject_id", subject.user().user_id()); filter.put("tenant_id", admission.context().tenant_id());
            filter.put("watermark", head.revision().toString());
            var pageInfo = json.object(); pageInfo.put("page", query.page()); pageInfo.put("page_size", query.pageSize());
            pageInfo.put("total", Long.toString(page.getTotal())); pageInfo.put("watermark", head.revision().toString());
            pageInfo.put("filter_sha256", DigestUtils.sha256Hex(json.canonical(filter))); data.set("page_info", pageInfo);
            return schema.envelope("Page", data, UUID.randomUUID().toString(), head.revision().toString());
        }).get());
    }
    private String currentPurpose(EnterpriseAccess.Admission admission, String versionId) {
        var values = CollectionAuthority.values(admission); values.put("versionId", versionId); return single(mapper.version(values)).purpose();
    }
    private com.fasterxml.jackson.databind.node.ObjectNode authorization(EnterpriseAccess.Admission admission, CollectionAuthority.Proof proof) {
        var result = json.object(); result.put("schema_version", "1.0.0"); result.put("context_revision", admission.context().context_revision());
        result.put("auth_epoch", admission.watermarks().authEpoch().toString()); result.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
        var sourceValues = json.object().arrayNode();
        for (var source : new TreeMap<>(proof.sources()).values()) {
            var value = json.object(); value.put("source_version", source.source().sourceVersion()); value.put("rights_revision", source.rights().rightsRevision());
            value.put("rights_sha256", source.rights().contentSha256()); value.put("grant_id", source.sourceGrant().grantId());
            value.put("grant_revision", source.sourceGrant().revision().toString()); sourceValues.add(value);
        }
        result.set("sources", sourceValues); var versions = json.object().arrayNode();
        for (var version : new TreeMap<>(proof.versions()).values()) {
            var value = json.object(); value.put("version_id", version.version().id()); value.put("content_sha256", version.version().contentSha256());
            value.put("domain", version.domain().name()); value.put("resource_kind", version.owner().resourceKind()); value.put("resource_id", version.owner().resourceId());
            value.put("grant_id", version.resourceGrant().grantId()); value.put("grant_revision", version.resourceGrant().revision().toString()); versions.add(value);
        }
        result.set("versions", versions); return result;
    }
    private void relation(Map<String,Object> values, String upstream, String kind) {
        values.put("upstreamId", upstream); values.put("relationKind", kind); one(mapper.insertLineage(values));
    }
    private static void context(EnterpriseAccess.Admission admission, String revision) {
        if (!admission.context().context_revision().equals(revision)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void conflict() { throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
}
