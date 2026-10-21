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
            Map<String,VersionResourceAuthority.VerifiedVersion> collectionVersions,
            Map<String,PermissionProtocol.GrantRow> entryGrants) {
        Selection { sources = List.copyOf(sources); collectionMembers = List.copyOf(collectionMembers); collectionVersions = Map.copyOf(collectionVersions); entryGrants = Map.copyOf(entryGrants); }
        Selection(List<SourceReadProtocol.VerifiedSource> sources, List<SourceReadProtocol.CollectionMember> members,
                Map<String,VersionResourceAuthority.VerifiedVersion> collections) { this(sources, members, collections, Map.of()); }
    }

    Selection require(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            String purpose, String environment, String region, DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(input);
        return require(admission, input.request().project_id(), input.request().scope_selection(), purpose, environment, region, limits);
    }

    Selection require(EnterpriseAccess.Admission admission, String project, CommonProtocol.SourceScope scope,
            String purpose, String environment, String region, DependencyLineage.Limits limits) throws IOException {
        return require(admission, project, scope, purpose, environment, region, limits, null, null);
    }

    /** The publication consumer supplies a real approved policy, never a client-selected rights purpose. */
    Selection requireForTaskCreation(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, String environment, String region,
            DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(input);
        return requireForTaskCreation(admission, input.request().project_id(), input.request().scope_selection(),
                policy, environment, region, limits);
    }

    Selection requireForTaskCreation(EnterpriseAccess.Admission admission, String project, CommonProtocol.SourceScope scope,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, String environment, String region,
            DependencyLineage.Limits limits) throws IOException {
        Objects.requireNonNull(policy);
        policy.requireConsumer(admission, project, "TASK_CREATION");
        // This task entry supports the current 078 document workflow only; never substitute a default purpose.
        if (!"DOCUMENT_BODY".equals(policy.businessPurpose())) denied();
        if (scope.networkEnabled() || policy.definition().networkPolicy().externalRequestsAllowed())
            throw new TenantAccessException(ErrorCode.NETWORK_SCOPE_DENIED);
        if (!environment.equals(policy.definition().environment()) || !region.equals(policy.definition().region())) denied();
        var fixed = policy.definition().sourceSelection();
        if (!"FIXED_SOURCE_VERSIONS".equals(fixed.mode())) denied();
        var allowed = new HashMap<String,String>();
        for (var source : fixed.sources()) if (allowed.putIfAbsent(source.version_id(), source.content_sha256()) != null) mismatch();
        var result = require(admission, project, scope, policy.businessPurpose(), environment, region, limits,
                "TASK_CREATION", allowed);
        policy.requireConsumer(admission, project, "TASK_CREATION");
        return result;
    }

    private Selection require(EnterpriseAccess.Admission admission, String project, CommonProtocol.SourceScope scope,
            String purpose, String environment, String region, DependencyLineage.Limits limits, String entryPurpose, Map<String,String> allowedSources) throws IOException {
        Objects.requireNonNull(admission); Objects.requireNonNull(scope); Objects.requireNonNull(limits);
        String statement = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.scopeCandidates";
        String user = admission.subject().user().user_id();
        WireValues.uuid(project);
        TenantScope.requireSqlAccess(statement);
        TenantScope.requireSubjectParameter(statement, Map.of("userId", user));
        var entryGrants = new HashMap<String,PermissionProtocol.GrantRow>();
        var projectScope = new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project);
        resources.require(admission, projectScope, "project.read", purpose);
        entryGrant(admission, projectScope, "project.read", entryPurpose, entryGrants);
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
            if (entryPurpose != null) {
                String ownerProject = proof.head().projectId();
                entryGrant(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), ownerProject,
                        ownerProject == null ? "ENTERPRISE" : "PROJECT",
                        ownerProject == null ? admission.context().tenant_id() : ownerProject),
                        "collection.read", entryPurpose, entryGrants);
            }
            var graph = proof.lineage();
            graphVersions.addAll(graph.versions().keySet()); relations.addAll(graph.graph().edgeSet());
            if (graphVersions.size() > maximum || relations.size() > limits.maximumRelations()) capacity();
            var authorized = proof.versions();
            // Preserve the original operation-purpose grants for every collection ancestor, not only its head.
            if (entryPurpose != null) for (var version : authorized.values()) {
                var owner = version.owner();
                entryGrant(admission, new PermissionProtocol.Scope(owner.tenantId(), owner.resourceProjectId(),
                        owner.resourceKind(), owner.resourceId()), version.resourceGrant().action(), entryPurpose, entryGrants);
                if (version.versionProjectGrant() != null) entryGrant(admission,
                        new PermissionProtocol.Scope(owner.tenantId(), owner.versionProjectId(), "PROJECT", owner.versionProjectId()),
                        version.versionProjectGrant().action(), entryPurpose, entryGrants);
            }
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
            if (allowedSources != null && !source.originalSha256().equals(allowedSources.get(id))) denied();
            entryGrant(admission, new PermissionProtocol.Scope(source.tenantId(), source.ownerProjectId(), "SOURCE", source.sourceId()),
                    "source.read", entryPurpose, entryGrants);
            sources.require(admission, new SourceReadProtocol.ReadRequirement(id, source.rightsRevision(), project,
                    null, null, null, SourceReadProtocol.Use.REVIEW, purpose, environment, region, null));
        }
        var candidates = mapper.scopeCandidates(user, project, scope.mode().name(), scope.sourceVersions(), scope.collectionVersions(), bound);
        if (candidates.size() > maximum) capacity();
        var selected = new ArrayList<SourceReadProtocol.VerifiedSource>(); var unique = new HashSet<String>();
        for (var row : candidates) {
            if (!admission.context().tenant_id().equals(row.tenantId()) || !project.equals(row.projectId()) || row.snapshotId() != null) mismatch();
            if (allowedSources != null && scope.mode() == CommonProtocol.ScopeMode.PROJECT_APPROVED
                    && !allowedSources.containsKey(row.sourceVersion())) continue;
            var source = source(row.sourceVersion());
            if (allowedSources != null && !source.originalSha256().equals(allowedSources.get(row.sourceVersion()))) denied();
            if (scope.mode() == CommonProtocol.ScopeMode.PROJECT_APPROVED && resources.find(admission,
                    new PermissionProtocol.Scope(source.tenantId(), source.ownerProjectId(), "SOURCE", source.sourceId()), "source.read", purpose).isEmpty()) continue;
            var sourceScope = new PermissionProtocol.Scope(source.tenantId(), source.ownerProjectId(), "SOURCE", source.sourceId());
            if (entryPurpose != null && scope.mode() == CommonProtocol.ScopeMode.PROJECT_APPROVED
                    && resources.find(admission, sourceScope, "source.read", entryPurpose).isEmpty()) continue;
            entryGrant(admission, sourceScope, "source.read", entryPurpose, entryGrants);
            // 多个可用解析、索引或者适用决定需要明确选择；不推断最新版本。
            if (!unique.add(row.sourceVersion()) || parses.containsKey(row.sourceVersion()) && !parses.get(row.sourceVersion()).equals(row.parseVersion())) notReady();
            selected.add(sources.requireCandidate(admission, new SourceReadProtocol.SnapshotRequirement(row.sourceVersion(),
                    source.rightsRevision(), project, row.parseVersion(), row.indexVersion(), row.approvalRevision(), row.bindingRevision(),
                    purpose, environment, region)));
        }
        if (scope.mode() != CommonProtocol.ScopeMode.PROJECT_APPROVED && !unique.equals(expected)) notReady();
        return new Selection(selected, members, collections, entryGrants);
    }

    private void entryGrant(EnterpriseAccess.Admission admission, PermissionProtocol.Scope scope, String action,
            String purpose, Map<String,PermissionProtocol.GrantRow> proof) throws IOException {
        if (purpose == null) return;
        var grant = resources.require(admission, scope, action, purpose);
        String key = scope.resource_kind() + ":" + scope.resource_id() + ":" + action;
        var previous = proof.putIfAbsent(key, grant);
        if (previous != null && !previous.equals(grant)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
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
