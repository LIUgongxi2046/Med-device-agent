package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.ScopeSnapshotWriteMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class ScopeSnapshotStore {
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.ScopeSnapshotWriteMapper.insertSnapshot";
    private final ScopeSnapshotWriteMapper mapper;
    private final TaskMapper tasks;
    private final PermissionMapper permissions;
    private final ScopeSelectionAuthority selections;
    private final ResourceReadAuthority authority;
    private final ProjectService projects;
    private final TaskInputSchema inputs;
    private final ScopeSnapshotSchema snapshots;
    private final PermissionSchema json;

    public ScopeSnapshotStore(ScopeSnapshotWriteMapper mapper, TaskMapper tasks, PermissionMapper permissions,
            ScopeSelectionAuthority selections, ResourceReadAuthority authority, ProjectService projects,
            TaskInputSchema inputs, ScopeSnapshotSchema snapshots, PermissionSchema json) {
        this.mapper = mapper; this.tasks = tasks; this.permissions = permissions; this.selections = selections;
        this.authority = authority; this.projects = projects; this.inputs = inputs; this.snapshots = snapshots; this.json = json;
    }

    record Stored(TaskProtocol.Snapshot snapshot, TaskProtocol.ScopeSnapshotManifest manifest,
            ScopeSelectionAuthority.Selection selection, List<String> dependencyVersions) {
        Stored { dependencyVersions = List.copyOf(dependencyVersions); }
    }

    Stored save(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate supplied,
            String purpose, String environment, String region, DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(admission); Objects.requireNonNull(supplied);
        TenantScope.requireSqlAccess(STATEMENT);
        String tenant = admission.context().tenant_id(), user = admission.subject().user().user_id();
        if (!TenantScope.requireCurrent().tenantId().equals(tenant)) denied();
        var values = new HashMap<String,Object>(); values.put("userId", user);
        TenantScope.requireSubjectParameter(STATEMENT, values);
        // 调用方提供的结构值不能代替原正文校验和当前来源检查。
        var validated = inputs.create(supplied.canonical_request().getBytes(StandardCharsets.UTF_8));
        if (!validated.equals(supplied)) mismatch();
        var request = validated.request(); String project = request.project_id(); values.put("projectId", project);
        var projectScope = new PermissionProtocol.Scope(tenant, project, "PROJECT", project);
        var grant = authority.require(admission, projectScope, "task.create", purpose);
        var profile = projects.fixedProfile(admission, project, request.profile_revision(), purpose);
        if (!"ACTIVE".equals(profile.project().status())) denied();
        var selected = selections.require(admission, validated, purpose, environment, region, limits);
        var result = persist(admission, project, request.profile_revision(), request.scope_selection(), selected, limits);
        if (!profile.equals(projects.fixedProfile(admission, project, request.profile_revision(), purpose))
                || !selected.equals(selections.require(admission, validated, purpose, environment, region, limits))
                || !grant.equals(authority.require(admission, projectScope, "task.create", purpose)))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return result;
    }


    /** Separate business entry: caller also binds configId to its actual current project-owned model revision. */
    Stored saveForModelVerification(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String configId, String purpose,
            String environment, String region, DependencyLineage.Limits limits) throws IOException {
        if (!java.util.Set.of("DOCUMENT_BODY", "MODEL_VERIFICATION").contains(purpose)) denied();
        return saveIndependent(admission, project, profileRevision, scope,
                new PermissionProtocol.Scope(admission.context().tenant_id(), project, "CONFIGURATION", configId),
                IndependentEntry.MODEL_VERIFICATION, purpose, environment, region, limits);
    }

    /** The evaluation consumer separately binds evaluationId to its immutable definition and approved policy. */
    Stored saveForEvaluation(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String evaluationId, String purpose,
            String environment, String region, DependencyLineage.Limits limits) throws IOException {
        return saveIndependent(admission, project, profileRevision, scope,
                new PermissionProtocol.Scope(admission.context().tenant_id(), project, "EVALUATION", evaluationId),
                IndependentEntry.EVALUATION, purpose, environment, region, limits);
    }

    Stored readForEvaluation(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String evaluationId, String purpose,
            String environment, String region, DependencyLineage.Limits limits, PermissionProtocol.Basis basis) throws IOException {
        return readIndependent(admission, project, profileRevision, scope,
                new PermissionProtocol.Scope(admission.context().tenant_id(), project, "EVALUATION", evaluationId),
                IndependentEntry.EVALUATION, purpose, environment, region, limits, basis);
    }
    Stored readForModelVerification(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String configId, String purpose,
            String environment, String region, DependencyLineage.Limits limits, PermissionProtocol.Basis basis) throws IOException {
        if (!java.util.Set.of("DOCUMENT_BODY", "MODEL_VERIFICATION").contains(purpose)) denied();
        return readIndependent(admission, project, profileRevision, scope,
                new PermissionProtocol.Scope(admission.context().tenant_id(), project, "CONFIGURATION", configId),
                IndependentEntry.MODEL_VERIFICATION, purpose, environment, region, limits, basis);
    }
    private Stored readIndependent(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, PermissionProtocol.Scope resource,
            IndependentEntry entry, String purpose, String environment, String region,
            DependencyLineage.Limits limits, PermissionProtocol.Basis basis) throws IOException {
        WireValues.uuid(project); WireValues.uuid(profileRevision); WireValues.uuid(resource.resource_id());
        authority.require(admission, resource, entryAction(entry),
                entryPurpose(entry));
        var profile = projects.fixedProfile(admission, project, profileRevision, purpose);
        if (!"ACTIVE".equals(profile.project().status())) denied();
        var selected = selections.require(admission, project, scope, purpose, environment, region, limits);
        var bounded = new HashMap<String,Object>(); bounded.put("userId", admission.actorId()); bounded.put("projectId", project); bounded.put("snapshotId", basis.version_id()); bounded.put("maximumBytes", 2097152); bounded.put("maximumMembers", (long)limits.maximumVersions()+1);
        var found = mapper.boundedSnapshot(bounded);
        if (found.size() != 1) mismatch(); var row = found.get(0);
        if (!admission.context().tenant_id().equals(row.tenantId()) || !project.equals(row.projectId()) || !basis.version_id().equals(row.snapshotId())) mismatch();
        if (!basis.content_sha256().equals(row.manifestHash()) || !row.manifestHash().equals(row.registryHash())) mismatch();
        var manifest = snapshots.require(row.manifest(), basis.content_sha256(), admission.context().tenant_id(), project,
                basis.version_id(), profileRevision, row.authEpoch(), row.governanceEpoch(), scope,
                mapper.boundedMembers(bounded));
        var ordered = selected.sources().stream().sorted(Comparator.comparing(source -> source.source().sourceVersion())).toList();
        if (ordered.size() != manifest.members().size()) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        for (int i=0; i<ordered.size(); i++) {
            var member=manifest.members().get(i); var current=ordered.get(i); ScopeSnapshotSchema.requireSource(member,current);
            var selectionRow=current.selection();
            if (selectionRow==null || !member.parse_version().equals(selectionRow.parseVersion()) || !member.index_version().equals(selectionRow.indexVersion())
                    || !member.approval_revision().equals(selectionRow.approvalRevision()) || !member.binding_revision().equals(selectionRow.bindingRevision()))
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        var expected = new TreeSet<String>(); expected.add(profileRevision); expected.addAll(scope.collectionVersions());
        for (var member : manifest.members()) expected.addAll(List.of(member.source_version(), member.parse_version(),
                member.index_version(), member.approval_revision(), member.binding_revision(), member.rights_revision()));
        if (scope.networkEnabled()) { expected.add(scope.networkPolicyRevision()); expected.add(scope.approvedQueryRevision()); }
        if (expected.size()>limits.maximumVersions() || expected.size()>limits.maximumRelations())
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var values = new HashMap<String,Object>(); values.put("userId",admission.actorId()); values.put("projectId",project); values.put("snapshotId",basis.version_id());
        values.put("maximumParents",(long)limits.maximumRelations()+1);
        if (!List.copyOf(expected).equals(mapper.boundedParents(values))) mismatch();
        return new Stored(row, manifest, selected, List.copyOf(expected));
    }

    private enum IndependentEntry { MODEL_VERIFICATION, EVALUATION, MODEL_ENABLEMENT }
    private static String entryAction(IndependentEntry entry) { return switch(entry) { case MODEL_VERIFICATION -> "model.verify"; case EVALUATION -> "evaluation.run"; case MODEL_ENABLEMENT -> "model.enable"; }; }
    private static String entryPurpose(IndependentEntry entry) { return switch(entry) { case MODEL_VERIFICATION -> "MODEL_VERIFICATION"; case EVALUATION -> "EVALUATION_RESULTS"; case MODEL_ENABLEMENT -> "MODEL_ENABLEMENT"; }; }
    Stored saveForModelEnablement(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String configId,
            String environment, String region, DependencyLineage.Limits limits) throws IOException {
        return saveIndependent(admission,project,profileRevision,scope,new PermissionProtocol.Scope(admission.context().tenant_id(),project,"CONFIGURATION",configId),IndependentEntry.MODEL_ENABLEMENT,"MODEL_ENABLEMENT",environment,region,limits);
    }
    Stored readForModelEnablement(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, String configId,
            String environment, String region, DependencyLineage.Limits limits, PermissionProtocol.Basis basis) throws IOException {
        return readIndependent(admission,project,profileRevision,scope,new PermissionProtocol.Scope(admission.context().tenant_id(),project,"CONFIGURATION",configId),IndependentEntry.MODEL_ENABLEMENT,"MODEL_ENABLEMENT",environment,region,limits,basis);
    }
    private Stored saveIndependent(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, PermissionProtocol.Scope resource,
            IndependentEntry entry, String purpose, String environment, String region,
            DependencyLineage.Limits limits) throws IOException {
        WireValues.uuid(project); WireValues.uuid(profileRevision); WireValues.uuid(resource.resource_id());
        String action = entryAction(entry);
        String entryPurpose = entryPurpose(entry);
        var grant = authority.require(admission, resource, action, entryPurpose);
        var profile = projects.fixedProfile(admission, project, profileRevision, purpose);
        if (!"ACTIVE".equals(profile.project().status())) denied();
        var selected = selections.require(admission, project, scope, purpose, environment, region, limits);
        var result = persist(admission, project, profileRevision, scope, selected, limits);
        if (!grant.equals(authority.require(admission, resource, action, entryPurpose))
                || !profile.equals(projects.fixedProfile(admission, project, profileRevision, purpose))
                || !selected.equals(selections.require(admission, project, scope, purpose, environment, region, limits)))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return result;
    }

    /** One private persistence implementation; no controller-supplied grant/action can invoke this core. */
    private Stored persist(EnterpriseAccess.Admission admission, String project, String profileRevision,
            com.ruoyi.medical.contracts.CommonProtocol.SourceScope scope, ScopeSelectionAuthority.Selection selected,
            DependencyLineage.Limits limits) throws IOException {
        TenantScope.requireSqlAccess(STATEMENT);
        String tenant = admission.context().tenant_id(), user = admission.actorId();
        if (!TenantScope.requireCurrent().tenantId().equals(tenant)) denied();
        var values = new HashMap<String,Object>(); values.put("userId", user); values.put("projectId", project);
        TenantScope.requireSubjectParameter(STATEMENT, values);
        var ordered = selected.sources().stream().sorted(Comparator.comparing(source -> source.source().sourceVersion())).toList();
        var members = ordered.stream().map(source -> {
            var row = Objects.requireNonNull(source.selection());
            return new TaskProtocol.ScopeSnapshotMember(source.source().sourceId(), source.source().sourceVersion(), row.parseVersion(),
                    row.indexVersion(), row.approvalRevision(), row.bindingRevision(), source.rights().rightsRevision(), source.source().originalSha256());
        }).toList();
        String snapshot = UUID.randomUUID().toString();
        var watermarks = admission.watermarks();
        var manifest = new TaskProtocol.ScopeSnapshotManifest("1.0.0", tenant, project, snapshot, profileRevision,
                scope, watermarks.authEpoch().toString(), watermarks.governanceEpoch().toString(), members);
        String content = json.canonicalObject(manifest), hash = DigestUtils.sha256Hex(content);
        if (content.getBytes(StandardCharsets.UTF_8).length>2097152) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var expectedRows = members.stream().map(member -> new DocumentUploadBasisProtocol.MemberRow(tenant, project, snapshot,
                member.source_id(), member.source_version(), member.parse_version(), member.index_version(), member.approval_revision(), member.binding_revision())).toList();
        snapshots.require(content, hash, tenant, project, snapshot, profileRevision, watermarks.authEpoch(),
                watermarks.governanceEpoch(), scope, expectedRows);
        var dependencies = new TreeSet<String>(); dependencies.add(profileRevision);
        dependencies.addAll(scope.collectionVersions());
        for (var member : members) dependencies.addAll(List.of(member.source_version(), member.parse_version(), member.index_version(),
                member.approval_revision(), member.binding_revision(), member.rights_revision()));
        if (scope.networkEnabled()) {
            dependencies.add(scope.networkPolicyRevision()); dependencies.add(scope.approvedQueryRevision());
        }
        if (dependencies.size() > limits.maximumVersions() || dependencies.size() > limits.maximumRelations())
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var now = Objects.requireNonNull(permissions.databaseTime());
        values.put("snapshotId", snapshot); values.put("manifest", content); values.put("hash", hash); values.put("now", now);
        values.put("authEpoch", watermarks.authEpoch().toString()); values.put("governanceEpoch", watermarks.governanceEpoch().toString());
        one(mapper.insertVersion(values)); one(mapper.insertSnapshot(values));
        for (var member : members) { values.put("item", member); one(mapper.insertMember(values)); }
        for (String id : dependencies) { WireValues.uuid(id); values.put("upstreamId", id); one(mapper.insertLineage(values)); }
        var saved = tasks.snapshot(user, project, snapshot);
        if (saved.size() != 1) mismatch(); var stored = saved.get(0);
        if (!tenant.equals(stored.tenantId()) || !project.equals(stored.projectId()) || !snapshot.equals(stored.snapshotId())
                || !hash.equals(stored.manifestHash()) || !hash.equals(stored.registryHash()) || !now.equals(stored.createdAt())
                || !watermarks.authEpoch().equals(stored.authEpoch()) || !watermarks.governanceEpoch().equals(stored.governanceEpoch())) mismatch();
        var reloaded = snapshots.require(stored.manifest(), hash, tenant, project, snapshot, profileRevision,
                watermarks.authEpoch(), watermarks.governanceEpoch(), scope, tasks.members(user, project, snapshot));
        if (!manifest.equals(reloaded) || !List.copyOf(dependencies).equals(mapper.parents(values))) mismatch();
        for (int index = 0; index < members.size(); index++) ScopeSnapshotSchema.requireSource(members.get(index), ordered.get(index));
        return new Stored(stored, reloaded, selected, List.copyOf(dependencies));
    }

    private static void one(int count) { if (count != 1) mismatch(); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
