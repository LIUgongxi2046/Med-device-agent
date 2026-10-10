package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DocumentUploadBasisAuthority {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper.";
    private static final String PURPOSE = "DOCUMENT_BODY";
    private final DocumentUploadBasisMapper mapper;
    private final UploadTransferMapper documents;
    private final ArtifactRegistryMapper registry;
    private final SourceReadMapper sourceRows;
    private final ResourceReadAuthority resources;
    private final SourceReadAuthority sources;
    private final ScopeSnapshotSchema snapshots;

    public DocumentUploadBasisAuthority(DocumentUploadBasisMapper mapper, UploadTransferMapper documents, ArtifactRegistryMapper registry,
            SourceReadMapper sourceRows, ResourceReadAuthority resources, SourceReadAuthority sources, ScopeSnapshotSchema snapshots) {
        this.mapper = Objects.requireNonNull(mapper); this.documents = Objects.requireNonNull(documents); this.registry = Objects.requireNonNull(registry);
        this.sourceRows = Objects.requireNonNull(sourceRows); this.resources = Objects.requireNonNull(resources); this.sources = Objects.requireNonNull(sources);
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    DocumentUploadBasisProtocol.Basis require(EnterpriseAccess.Admission admission, String documentId, String expectedRevision,
            DocumentUploadBasisProtocol.Settings settings) throws IOException, NoSuchAlgorithmException {
        if (admission == null) denied(); TenantScope.requireSqlAccess(PREFIX + "task");
        WireValues.uuid(documentId); var expected = WireValues.revision(expectedRevision); Objects.requireNonNull(settings);
        String tenant = admission.context().tenant_id();
        if (!TenantScope.requireCurrent().tenantId().equals(tenant)) denied();
        var values = Map.<String,Object>of("userId", admission.subject().user().user_id(), "documentId", documentId,
            "lock", true, "limit", (long) settings.maximumSources() + 1);
        TenantScope.requireSubjectParameter(PREFIX + "task", values);
        var document = one(documents.document(values));
        if (!tenant.equals(document.tenantId()) || !documentId.equals(document.documentId())) denied();
        String project = document.projectId(); WireValues.uuid(project); WireValues.uuid(document.taskId());
        resources.require(admission, new PermissionProtocol.Scope(tenant, project, "DOCUMENT", documentId), "document.write", PURPOSE);
        resources.require(admission, new PermissionProtocol.Scope(tenant, project, "TASK", document.taskId()), "task.read", PURPOSE);
        if (!"ACTIVE".equals(document.projectStatus())) denied();
        if ("SIGNED".equals(document.status())) throw new TenantAccessException(ErrorCode.DOCUMENT_SIGNED);
        if (!expected.equals(document.revision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var task = one(mapper.task(values)); identity(tenant, project, task.tenantId(), task.projectId());
        if (!document.taskId().equals(task.taskId())) mismatch();
        WireValues.uuid(task.ownerId()); metadata(task.createdBy(), task.createdAt(), task.classification());
        WireValues.revision(task.revision().toString()); text(task.status(), 24);
        if (task.revision().signum() <= 0 || task.updatedAt() == null || task.updatedAt().isBefore(task.createdAt())) mismatch();
        if (task.currentRevision() == null) notReady(); WireValues.uuid(task.currentRevision());
        var revision = one(mapper.taskRevision(values)); identity(tenant, project, revision.tenantId(), revision.projectId());
        if (!task.taskId().equals(revision.taskId()) || !task.currentRevision().equals(revision.taskRevision())) mismatch();
        WireValues.uuid(revision.profileRevision()); WireValues.uuid(revision.snapshotId()); WireValues.uuid(revision.templateVersion());
        WireValues.uuid(revision.agentReleaseId()); if (revision.parentRevision() != null) WireValues.uuid(revision.parentRevision());
        if (revision.taskRevision().equals(revision.parentRevision()) || revision.reason() == null) mismatch();
        metadata(revision.createdBy(), revision.createdAt(), revision.classification());
        registered(revision.taskRevision(), project, revision.contentSha256());
        if (revision.planRevision() == null) notReady(); WireValues.uuid(revision.planRevision());
        var plan = one(mapper.plan(values)); identity(tenant, project, plan.tenantId(), plan.projectId());
        if (!revision.planRevision().equals(plan.planRevision()) || !revision.taskId().equals(plan.taskId())
                || !revision.taskRevision().equals(plan.taskRevision()) || !revision.templateVersion().equals(plan.templateVersion())
                || !revision.snapshotId().equals(plan.snapshotId())) mismatch();
        if (!"CONFIRMED".equals(plan.status()) || plan.decisionId() == null) notReady(); WireValues.uuid(plan.decisionId());
        metadata(plan.createdBy(), plan.createdAt(), plan.classification()); registered(plan.planRevision(), project, plan.contentSha256());
        var snapshot = one(mapper.snapshot(values)); identity(tenant, project, snapshot.tenantId(), snapshot.projectId());
        if (!revision.snapshotId().equals(snapshot.snapshotId()) || snapshot.createdAt() == null) mismatch();
        WireValues.revision(snapshot.authEpoch().toString()); WireValues.revision(snapshot.governanceEpoch().toString());
        var members = List.copyOf(mapper.members(values));
        if (members.size() > settings.maximumSources()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var manifest = snapshots.require(snapshot.manifest(), snapshot.manifestSha256(), tenant, project, snapshot.snapshotId(),
            revision.profileRevision(), snapshot.authEpoch(), snapshot.governanceEpoch(), null, members);
        registered(snapshot.snapshotId(), project, snapshot.manifestSha256());
        var requirements = new ArrayList<SourceReadProtocol.ReadRequirement>();
        var verified = new LinkedHashMap<String,SourceReadProtocol.VerifiedSource>(); String previous = null;
        for (var member : members) {
            identity(tenant, project, member.tenantId(), member.projectId());
            if (!snapshot.snapshotId().equals(member.snapshotId())) mismatch();
            WireValues.uuid(member.sourceId()); WireValues.uuid(member.sourceVersion()); WireValues.uuid(member.parseVersion());
            WireValues.uuid(member.indexVersion()); WireValues.uuid(member.approvalRevision()); WireValues.uuid(member.bindingRevision());
            if (previous != null && previous.compareTo(member.sourceVersion()) >= 0) mismatch(); previous = member.sourceVersion();
            var source = one(sourceRows.source(member.sourceVersion()));
            if (!tenant.equals(source.tenantId()) || !member.sourceId().equals(source.sourceId()) || !member.sourceVersion().equals(source.sourceVersion())) mismatch();
            var requirement = new SourceReadProtocol.ReadRequirement(member.sourceVersion(), source.rightsRevision(), project,
                snapshot.snapshotId(), member.parseVersion(), null, SourceReadProtocol.Use.FORMAL, PURPOSE, settings.environment(), settings.region(), null);
            var current = sources.require(admission, requirement);
            ScopeSnapshotSchema.requireSource(manifest.members().get(requirements.size()), current);
            if (!member.indexVersion().equals(current.selection().indexVersion()) || !member.approvalRevision().equals(current.selection().approvalRevision())
                    || !member.bindingRevision().equals(current.selection().bindingRevision())) mismatch();
            requirements.add(requirement); verified.put(member.sourceVersion(), current);
        }
        // 固定依据仍需与完整DependencyAccessSet和专业正文语义共同核验，不能单独授予上传。
        return new DocumentUploadBasisProtocol.Basis(document, task, revision, plan, snapshot, members, requirements, verified);
    }

    private void registered(String id, String project, String sha256) {
        WireValues.hash(sha256); var version = one(registry.version(id));
        if (!id.equals(version.id()) || !project.equals(version.projectId()) || !sha256.equals(version.contentSha256())) mismatch();
    }

    static DocumentUploadBasisProtocol.Binding bind(DocumentUploadBasisProtocol.Basis basis,
            DocumentUploadBasisProtocol.Settings settings) throws IOException, NoSuchAlgorithmException {
        if (basis == null) denied(); Objects.requireNonNull(settings);
        var value = new LinkedHashMap<String,Object>(); value.put("schema_version", "1.0.0");
        value.put("settings", settings); value.put("basis", basis);
        return new DocumentUploadBasisProtocol.Binding(basis.task().taskId(), basis.taskRevision().taskRevision(),
            basis.plan().planRevision(), basis.snapshot().snapshotId(), WireValues.sha256(ContentReadAuthorization.canonicalBytes(value)), settings);
    }

    static void requireBinding(DocumentUploadBasisProtocol.Basis basis, DocumentUploadBasisProtocol.Binding binding,
            DependencyAccessProtocol.VerifiedAccess dependencies, List<SourceReadProtocol.ReadRequirement> requiredSources) throws IOException, NoSuchAlgorithmException {
        if (basis == null) denied(); Objects.requireNonNull(binding); Objects.requireNonNull(dependencies);
        if (!bind(basis, binding.settings()).equals(binding)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        if (!basis.document().tenantId().equals(dependencies.header().tenantId())
                || !basis.document().projectId().equals(dependencies.header().projectId())
                || !basis.sources().equals(requiredSources) || !basis.verifiedSources().equals(dependencies.sources())) mismatch();
        // 全部固定依据必须位于原完整传递关系中；历史版本读取仍保留独立语义。
        var versions = dependencies.lineage().versions();
        for (String id : List.of(basis.taskRevision().taskRevision(), basis.plan().planRevision(), basis.snapshot().snapshotId(),
                basis.taskRevision().profileRevision(), basis.taskRevision().templateVersion(), basis.taskRevision().agentReleaseId())) {
            if (!versions.containsKey(id)) mismatch();
        }
        for (var entry : Map.of(basis.taskRevision().taskRevision(), basis.taskRevision().contentSha256(),
                basis.plan().planRevision(), basis.plan().contentSha256(), basis.snapshot().snapshotId(), basis.snapshot().manifestSha256()).entrySet()) {
            var version = versions.get(entry.getKey());
            if (!basis.document().projectId().equals(version.projectId()) || !entry.getValue().equals(version.contentSha256())) mismatch();
        }
    }
    private static void identity(String tenant, String project, String actualTenant, String actualProject) {
        if (!tenant.equals(actualTenant) || !project.equals(actualProject)) denied();
    }
    private static void metadata(String createdBy, LocalDateTime createdAt, String classification) {
        WireValues.uuid(createdBy); text(classification, 24); if (createdAt == null) mismatch();
    }
    private static void text(String value, int maximum) { WireValues.boundedString(value, maximum); if (value == null || value.isEmpty()) mismatch(); }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) denied(); return rows.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void notReady() { throw new TenantAccessException(ErrorCode.STEP_NOT_READY); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
