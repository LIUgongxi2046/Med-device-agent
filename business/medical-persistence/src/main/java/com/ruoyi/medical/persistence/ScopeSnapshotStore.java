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
        var ordered = selected.sources().stream().sorted(Comparator.comparing(source -> source.source().sourceVersion())).toList();
        var members = ordered.stream().map(source -> {
            var row = Objects.requireNonNull(source.selection());
            return new TaskProtocol.ScopeSnapshotMember(source.source().sourceId(), source.source().sourceVersion(), row.parseVersion(),
                    row.indexVersion(), row.approvalRevision(), row.bindingRevision(), source.rights().rightsRevision(), source.source().originalSha256());
        }).toList();
        String snapshot = UUID.randomUUID().toString();
        var watermarks = admission.watermarks();
        var manifest = new TaskProtocol.ScopeSnapshotManifest("1.0.0", tenant, project, snapshot, request.profile_revision(),
                request.scope_selection(), watermarks.authEpoch().toString(), watermarks.governanceEpoch().toString(), members);
        String content = json.canonicalObject(manifest), hash = DigestUtils.sha256Hex(content);
        var expectedRows = members.stream().map(member -> new DocumentUploadBasisProtocol.MemberRow(tenant, project, snapshot,
                member.source_id(), member.source_version(), member.parse_version(), member.index_version(), member.approval_revision(), member.binding_revision())).toList();
        snapshots.require(content, hash, tenant, project, snapshot, request.profile_revision(), watermarks.authEpoch(),
                watermarks.governanceEpoch(), request.scope_selection(), expectedRows);
        var dependencies = new TreeSet<String>(); dependencies.add(request.profile_revision());
        dependencies.addAll(request.scope_selection().collectionVersions());
        for (var member : members) dependencies.addAll(List.of(member.source_version(), member.parse_version(), member.index_version(),
                member.approval_revision(), member.binding_revision(), member.rights_revision()));
        if (request.scope_selection().networkEnabled()) {
            dependencies.add(request.scope_selection().networkPolicyRevision()); dependencies.add(request.scope_selection().approvedQueryRevision());
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
        var reloaded = snapshots.require(stored.manifest(), hash, tenant, project, snapshot, request.profile_revision(),
                watermarks.authEpoch(), watermarks.governanceEpoch(), request.scope_selection(), tasks.members(user, project, snapshot));
        if (!manifest.equals(reloaded) || !List.copyOf(dependencies).equals(mapper.parents(values))) mismatch();
        for (int index = 0; index < members.size(); index++) ScopeSnapshotSchema.requireSource(members.get(index), ordered.get(index));
        if (!profile.equals(projects.fixedProfile(admission, project, request.profile_revision(), purpose))
                || !selected.equals(selections.require(admission, validated, purpose, environment, region, limits))
                || !grant.equals(authority.require(admission, projectScope, "task.create", purpose)))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new Stored(stored, reloaded, selected, List.copyOf(dependencies));
    }

    private static void one(int count) { if (count != 1) mismatch(); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
