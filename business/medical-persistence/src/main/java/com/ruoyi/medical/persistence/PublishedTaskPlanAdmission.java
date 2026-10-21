package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actual 086/078 proof for fixed-source confirmation. Does not authorize a run or network query. */
@Component
public final class PublishedTaskPlanAdmission implements TaskPlanReleaseAdmission {
    private static final String PURPOSE = "PLAN_MAINTENANCE";
    private final ObjectProvider<AgentPublicationAuthority> publications;
    private final AgentExecutionPolicyAuthority policies;
    private final AgentReleaseScopeAuthority scopes;
    private final TaskPlanService plans;
    private final TemplateVersionStore templates;
    private final TaskInputSchema inputs;
    private final TemplateDependencyPolicy limits;
    private final PermissionSchema json;

    public PublishedTaskPlanAdmission(ObjectProvider<AgentPublicationAuthority> publications,
            AgentExecutionPolicyAuthority policies, AgentReleaseScopeAuthority scopes, TaskPlanService plans,
            TemplateVersionStore templates, TaskInputSchema inputs, TemplateDependencyPolicy limits, PermissionSchema json) {
        this.publications = publications; this.policies = policies; this.scopes = scopes; this.plans = plans;
        this.templates = templates; this.inputs = inputs; this.limits = limits; this.json = json;
    }

    @Override public TaskPlanReleaseAdmission prepare(ApplicationSessionIdentity.Verified subject, String context,
            TaskService.VerifiedTask task, TaskPlanWriteProtocol.View draft) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) fail(ErrorCode.ACCESS_DENIED);
        var authority = publications.getIfAvailable();
        if (authority == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        requireNetworkDisabled(task);
        try {
            var prepared = authority.prepare(subject, context, task.head().agentReleaseId(), PURPOSE, false);
            return (admission, actual, currentDraft, scope) -> {
                // The response envelope includes a new request ID on each read; it is not fixed task identity.
                if (!task.head().equals(actual.head()) || !task.input().equals(actual.input()) || !draft.equals(currentDraft))
                    fail(ErrorCode.SCOPE_STALE);
                return requirePrepared(authority, prepared, admission, actual, currentDraft, scope);
            };
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IOException("PLAN_PUBLICATION_PREPARATION_FAILED", e); }
    }

    @Override public Verified require(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View draft, AgentReleaseScopeAuthority.VerifiedScope scope) {
        throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private Verified requirePrepared(AgentPublicationAuthority authority, AgentPublicationAuthority.PreparedPublication prepared,
            EnterpriseAccess.Admission admission, TaskService.VerifiedTask task, TaskPlanWriteProtocol.View draft,
            AgentReleaseScopeAuthority.VerifiedScope scope) throws IOException {
        try {
            var head = task.head(); requireNetworkDisabled(task);
            if (admission.simulationWorker() || !"plan.confirm".equals(scope.action())
                    || !head.projectId().equals(scope.target().project_id()) || !"TASK".equals(scope.target().resource_kind())
                    || !head.taskId().equals(scope.target().resource_id()) || !head.tenantId().equals(scope.target().tenant_id())
                    || !head.agentReleaseId().equals(scope.release().release().releaseId())) fail(ErrorCode.ACCESS_DENIED);
            if (!"PLAN_PENDING".equals(head.status()) || !"DRAFT".equals(draft.status())
                    || !head.taskRevision().equals(draft.taskRevision()) || !head.templateVersion().equals(draft.templateVersion())
                    || !head.snapshotId().equals(draft.snapshotId()) || !head.profileRevision().equals(draft.profileRevision()))
                fail(ErrorCode.PLAN_STALE);
            var published = authority.require(admission, head.agentReleaseId(), PURPOSE, prepared);
            if (!published.manifest().equals(scope.release().content())) fail(ErrorCode.CONTENT_MISMATCH);
            var policy = policies.requireByVersion(admission, published.executionPolicyId(), published.executionPolicySha256(),
                    head.projectId(), scope.classification(), PURPOSE);
            AgentExecutionPolicyAuthority.requireManifest(policy.definition(), published.manifest().manifest());
            if (!"DOCUMENT_BODY".equals(policy.businessPurpose()) || !limits.environment().equals(policy.definition().environment())
                    || !limits.region().equals(policy.definition().region()) || "SIMULATION".equals(policy.definition().environment()))
                fail(ErrorCode.ACCESS_DENIED);
            var template = templates.historicalTarget(admission, head.templateVersion(), PURPOSE);
            if (!published.manifest().manifest().templateVersions().contains(new AgentReleaseProtocol.VersionRef(
                    head.templateVersion(), template.content_sha256()))) fail(ErrorCode.CONTENT_MISMATCH);
            var selected = sourceProof(admission, task);
            PublishedTaskCreateAdmission.requireSelection(policy.definition(), selected.stream().map(source ->
                    new PermissionProtocol.Basis(source.source().sourceVersion(), source.source().originalSha256())).toList());
            var versions = new TreeSet<>(published.dependencyVersions()); versions.addAll(policy.dependencyVersions());
            versions.add(head.agentReleaseId()); versions.add(scope.release().release().manifestArtifactId());
            published.manifest().dependencies().forEach(v -> versions.add(v.versionId()));
            published.manifest().artifacts().forEach(v -> versions.add(v.artifactId()));
            scope.scopes().forEach(v -> versions.add(v.scopeRevision()));
            scope.roles().forEach(v -> versions.add(v.origin().roleVersion()));
            if (versions.size() > limits.maximumVersions()) fail(ErrorCode.CONTEXT_LIMIT);
            var proof = json.object().arrayNode();
            var value = proof.addObject().put("kind", "PUBLISHED_TASK_PLAN_ADMISSION")
                    .put("release_id", head.agentReleaseId()).put("approval_sha256", published.approvalSha256())
                    .put("execution_policy_version", policy.versionId()).put("execution_policy_sha256", policy.contentSha256())
                    .put("source_business_purpose", policy.businessPurpose());
            value.set("publication", published.authorizations()); value.set("execution_policy", policy.authorizations());
            value.set("fixed_sources", json.tree(selected));
            return new Verified() {
                public JsonNode authorizations() { return proof.deepCopy(); }
                public List<String> dependencyVersions() { return List.copyOf(versions); }
                public void requireUnchanged() throws IOException {
                    try {
                        published.requireUnchanged(); policy.requireUnchanged(); scopes.requireUnchanged(scope);
                        if (!selected.equals(sourceProof(admission, task))) fail(ErrorCode.SCOPE_STALE);
                    } catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new IOException("PLAN_PUBLICATION_RECHECK_FAILED", e); }
                }
            };
        } catch (RuntimeException e) { throw e; }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("PLAN_PUBLICATION_ADMISSION_FAILED", e); }
    }

    private List<SourceReadProtocol.VerifiedSource> sourceProof(EnterpriseAccess.Admission admission,
            TaskService.VerifiedTask task) throws IOException {
        var head = task.head();
        return plans.sourceProof(admission, head.projectId(), head.taskId(), head.taskRevision(), head.snapshotId(), head.profileRevision(), true);
    }
    private void requireNetworkDisabled(TaskService.VerifiedTask task) throws IOException {
        var input = inputs.create(task.input().requestJson().getBytes(StandardCharsets.UTF_8));
        PublishedTaskCreateAdmission.requireRequestScope(input);
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
