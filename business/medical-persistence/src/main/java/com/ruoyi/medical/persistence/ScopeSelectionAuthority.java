package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ScopeSelectionAuthority {
    private final SourceReadMapper mapper;
    private final SourceReadAuthority sources;
    private final ResourceReadAuthority resources;
    private final CollectionAuthority collectionAuthority;

    public ScopeSelectionAuthority(SourceReadMapper mapper, SourceReadAuthority sources, ResourceReadAuthority resources,
            CollectionAuthority collectionAuthority) {
        this.mapper = mapper; this.sources = sources; this.resources = resources; this.collectionAuthority = collectionAuthority;
    }

    record Selection(List<SourceReadProtocol.VerifiedSource> sources,
            List<SourceReadProtocol.CollectionMember> collectionMembers,
            Map<String,VersionResourceAuthority.VerifiedVersion> collectionVersions) {
        Selection { sources = List.copyOf(sources); collectionMembers = List.copyOf(collectionMembers); collectionVersions = Map.copyOf(collectionVersions); }
    }

    Selection require(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            String purpose, String environment, String region, DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(input);
        return require(admission, input.request().project_id(), input.request().scope_selection(), purpose, environment, region, limits);
    }

    Selection require(EnterpriseAccess.Admission admission, String project, CommonProtocol.SourceScope scope,
            String purpose, String environment, String region, DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(admission); Objects.requireNonNull(scope); Objects.requireNonNull(limits);
        String statement = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.scopeCandidates";
        String user = admission.subject().user().user_id();
        WireValues.uuid(project);
        TenantScope.requireSqlAccess(statement);
        TenantScope.requireSubjectParameter(statement, Map.of("userId", user));
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project), "project.read", purpose);
        int maximum = limits.maximumVersions();
        if (maximum == Integer.MAX_VALUE || scope.sourceVersions().size() > maximum || scope.collectionVersions().size() > maximum) capacity();
        int bound = maximum + 1;
        var expected = new HashSet<>(scope.sourceVersions());
        var collections = new HashMap<String,VersionResourceAuthority.VerifiedVersion>();
        var members = new ArrayList<SourceReadProtocol.CollectionMember>();
        var parses = new HashMap<String,String>();
        var graphVersions = new HashSet<String>(); var relations = new HashSet<SourceReadProtocol.ParentRow>();
        if (expected.size() != scope.sourceVersions().size() || new HashSet<>(scope.collectionVersions()).size() != scope.collectionVersions().size()) mismatch();
        for (String id : scope.collectionVersions()) {
            var proof = collectionAuthority.require(admission, id, purpose, environment, region, limits);
            var graph = proof.lineage();
            graphVersions.addAll(graph.versions().keySet()); relations.addAll(graph.graph().edgeSet());
            if (graphVersions.size() > maximum || relations.size() > limits.maximumRelations()) capacity();
            var authorized = proof.versions();
            var collection = authorized.get(id);
            if (collection == null || collection.domain() != VersionResourceProtocol.Domain.COLLECTION_VERSION) mismatch();
            if (collection.owner().versionProjectId() != null && !project.equals(collection.owner().versionProjectId())) denied();
            collections.put(id, collection);
            var actual = proof.manifest().members().stream().map(member -> new SourceReadProtocol.CollectionMember(
                    proof.manifest().tenant_id(), id, member.source_version(), member.parse_version())).toList();
            if (actual.size() > maximum) capacity();
            for (var member : actual) {
                if (!admission.context().tenant_id().equals(member.tenantId()) || !id.equals(member.collectionVersion())) mismatch();
                WireValues.uuid(member.sourceVersion()); WireValues.uuid(member.parseVersion());
                String previous = parses.putIfAbsent(member.sourceVersion(), member.parseVersion());
                if (previous != null && !previous.equals(member.parseVersion())) notReady();
                expected.add(member.sourceVersion()); members.add(member);
                if (expected.size() > maximum || members.size() > limits.maximumRelations()) capacity();
            }
        }
        // 显式版本在选择之前核查当前权限，不能通过缺少索引隐藏越权请求。
        for (String id : expected) {
            var source = source(id);
            sources.require(admission, new SourceReadProtocol.ReadRequirement(id, source.rightsRevision(), project,
                    null, null, null, SourceReadProtocol.Use.REVIEW, purpose, environment, region, null));
        }
        var candidates = mapper.scopeCandidates(user, project, scope.mode().name(), scope.sourceVersions(), scope.collectionVersions(), bound);
        if (candidates.size() > maximum) capacity();
        var selected = new ArrayList<SourceReadProtocol.VerifiedSource>(); var unique = new HashSet<String>();
        for (var row : candidates) {
            if (!admission.context().tenant_id().equals(row.tenantId()) || !project.equals(row.projectId()) || row.snapshotId() != null) mismatch();
            var source = source(row.sourceVersion());
            if (scope.mode() == CommonProtocol.ScopeMode.PROJECT_APPROVED && resources.find(admission,
                    new PermissionProtocol.Scope(source.tenantId(), source.ownerProjectId(), "SOURCE", source.sourceId()), "source.read", purpose).isEmpty()) continue;
            // 多个可用解析、索引或者适用决定需要明确选择；不推断最新版本。
            if (!unique.add(row.sourceVersion()) || parses.containsKey(row.sourceVersion()) && !parses.get(row.sourceVersion()).equals(row.parseVersion())) notReady();
            selected.add(sources.requireCandidate(admission, new SourceReadProtocol.SnapshotRequirement(row.sourceVersion(),
                    source.rightsRevision(), project, row.parseVersion(), row.indexVersion(), row.approvalRevision(), row.bindingRevision(),
                    purpose, environment, region)));
        }
        if (scope.mode() != CommonProtocol.ScopeMode.PROJECT_APPROVED && !unique.equals(expected)) notReady();
        return new Selection(selected, members, collections);
    }

    private SourceReadProtocol.SourceRow source(String id) {
        var actual = mapper.source(id);
        if (actual.size() != 1) denied();
        return actual.get(0);
    }
    private static void capacity() { throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void notReady() { throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
