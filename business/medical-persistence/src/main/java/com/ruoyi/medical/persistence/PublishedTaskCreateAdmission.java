package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.ResourceProtocol;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Consumes real 086 publication and approved 078 policy; never manufactures release evidence. */
@Component
public final class PublishedTaskCreateAdmission implements TaskCreateReleaseAdmission {
    private static final String PURPOSE = "TASK_CREATION";
    private final ObjectProvider<AgentPublicationAuthority> publications;
    private final AgentExecutionPolicyAuthority policies;
    private final AgentReleaseScopeAuthority scopes;
    private final ScopeSelectionAuthority selections;
    private final TemplateDependencyPolicy limits;
    private final PermissionSchema json;
    private final ScenarioSpecificationService scenarios;

    public PublishedTaskCreateAdmission(ObjectProvider<AgentPublicationAuthority> publications,
            AgentExecutionPolicyAuthority policies, AgentReleaseScopeAuthority scopes,
            ScopeSelectionAuthority selections, TemplateDependencyPolicy limits, PermissionSchema json, ScenarioSpecificationService scenarios) {
        this.publications = publications; this.policies = policies; this.scopes = scopes;
        this.selections = selections; this.limits = limits; this.json = json; this.scenarios = scenarios;
    }

    @Override public TaskCreateReleaseAdmission prepare(ApplicationSessionIdentity.Verified subject,
            String context, TaskProtocol.ValidatedCreate input) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) fail(ErrorCode.ACCESS_DENIED);
        var authority = publications.getIfAvailable();
        if (authority == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        requireRequestScope(input);
        try {
            var prepared = authority.prepare(subject, context, input.request().agent_release_id(), PURPOSE, false);
            return new TaskCreateReleaseAdmission() {
                public AgentExecutionPolicyAuthority.VerifiedPolicy sourcePolicy(EnterpriseAccess.Admission admission,
                        TaskProtocol.ValidatedCreate actual) throws IOException {
                    if (!input.equals(actual)) fail(ErrorCode.CONTENT_MISMATCH);
                    try {
                        var published = authority.require(admission, actual.request().agent_release_id(), PURPOSE, prepared);
                        var policy = policies.requireByVersion(admission, published.executionPolicyId(),
                                published.executionPolicySha256(), actual.request().project_id(), PURPOSE);
                        AgentExecutionPolicyAuthority.requireManifest(policy.definition(), published.manifest().manifest());
                        return policy;
                    } catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new IOException("TASK_SOURCE_POLICY_FAILED", e); }
                }
                public Verified require(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate actual,
                        TemplateApplicabilityService.Target template, ScopeSelectionAuthority.Selection selected,
                        AgentReleaseScopeAuthority.VerifiedScope scope) throws IOException {
                    if (!input.equals(actual)) fail(ErrorCode.CONTENT_MISMATCH);
                    return requirePrepared(authority, prepared, admission, actual, template, selected, scope);
                }
            };
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IOException("TASK_PUBLICATION_PREPARATION_FAILED", e); }
    }

    /** An unprepared call cannot read object bytes while holding the enterprise transaction. */
    @Override public Verified require(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            TemplateApplicabilityService.Target template, ScopeSelectionAuthority.Selection selection,
            AgentReleaseScopeAuthority.VerifiedScope scope) {
        throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private Verified requirePrepared(AgentPublicationAuthority authority,
            AgentPublicationAuthority.PreparedPublication prepared, EnterpriseAccess.Admission admission,
            TaskProtocol.ValidatedCreate input, TemplateApplicabilityService.Target template,
            ScopeSelectionAuthority.Selection selection, AgentReleaseScopeAuthority.VerifiedScope scope) throws IOException {
        try {
            requireRequestScope(input);
            var request = input.request();
            if (admission.simulationWorker() || !"task.create".equals(scope.action())
                    || !PURPOSE.equals(template.purpose()) || template.admission() != admission
                    || !request.project_id().equals(scope.target().project_id())
                    || !request.agent_release_id().equals(scope.release().release().releaseId())) fail(ErrorCode.ACCESS_DENIED);
            var publication = authority.require(admission, request.agent_release_id(), PURPOSE, prepared);
            if (!publication.manifest().equals(scope.release().content())) fail(ErrorCode.CONTENT_MISMATCH);
            var policy = policies.requireByVersion(admission, publication.executionPolicyId(),
                    publication.executionPolicySha256(), request.project_id(), scope.classification(), PURPOSE);
            AgentExecutionPolicyAuthority.requireManifest(policy.definition(), publication.manifest().manifest());
            requireSelection(policy.definition(), selection.sources().stream().map(source ->
                    new PermissionProtocol.Basis(source.source().sourceVersion(), source.source().originalSha256())).toList());
            if (!limits.environment().equals(policy.definition().environment())
                    || !limits.region().equals(policy.definition().region())
                    || "SIMULATION".equals(policy.definition().environment())) fail(ErrorCode.ACCESS_DENIED);
            var templateRef = new AgentReleaseProtocol.VersionRef(request.template_version(), template.view().template().content_sha256());
            if (!publication.manifest().manifest().templateVersions().contains(templateRef)) fail(ErrorCode.CONTENT_MISMATCH);
            var scenario = scenarios.requireTask(admission, input, publication, selection.sources().stream().map(source -> new PermissionProtocol.Basis(source.source().sourceVersion(), source.source().originalSha256())).toList());
            var versions = new TreeSet<>(publication.dependencyVersions()); versions.addAll(scenario.dependencyVersions()); versions.addAll(policy.dependencyVersions());
            versions.add(request.agent_release_id()); versions.add(scope.release().release().manifestArtifactId());
            publication.manifest().dependencies().forEach(v -> versions.add(v.versionId()));
            publication.manifest().artifacts().forEach(v -> versions.add(v.artifactId()));
            scope.scopes().forEach(v -> versions.add(v.scopeRevision()));
            scope.roles().forEach(v -> versions.add(v.origin().roleVersion()));
            if (versions.size() > limits.maximumVersions()) fail(ErrorCode.CONTEXT_LIMIT);
            var proofs = json.object().arrayNode();
            var proof = proofs.addObject().put("kind", "PUBLISHED_TASK_CREATE_ADMISSION")
                    .put("release_id", request.agent_release_id()).put("approval_sha256", publication.approvalSha256())
                    .put("execution_policy_version", policy.versionId()).put("execution_policy_sha256", policy.contentSha256())
                    .put("source_business_purpose", policy.businessPurpose());
            proof.set("scenario", scenario.authorizations());
            proof.set("publication", publication.authorizations()); proof.set("execution_policy", policy.authorizations());
            proof.set("selected_sources", json.tree(selection.sources()));
            proof.set("source_entry_authorizations", json.tree(selection.entryGrants()));
            return new Verified() {
                public JsonNode authorizations() { return proofs.deepCopy(); }
                public List<String> dependencyVersions() { return List.copyOf(versions); }
                public List<ResourceProtocol.Requirement> missingRequirements() { return List.of(); }
                public void requireUnchanged() throws IOException {
                    try {
                        scenario.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged(); scopes.requireUnchanged(scope);
                        if (!selection.equals(selections.requireForTaskCreation(admission, input, policy,
                                limits.environment(), limits.region(), limits.limits()))) fail(ErrorCode.SCOPE_STALE);
                    } catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new IOException("TASK_PUBLICATION_RECHECK_FAILED", e); }
                }
            };
        } catch (RuntimeException e) { throw e; }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("TASK_PUBLICATION_ADMISSION_FAILED", e); }
    }

    static void requireRequestScope(TaskProtocol.ValidatedCreate input) {
        if (input.request().scope_selection().networkEnabled()) fail(ErrorCode.NETWORK_SCOPE_DENIED);
    }

    static void requireSelection(AgentExecutionPolicyProtocol.Definition policy, List<PermissionProtocol.Basis> selected) {
        if (!"FIXED_SOURCE_VERSIONS".equals(policy.sourceSelection().mode())) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        // External query approval has no production producer yet. Never infer it from policy metadata.
        if (policy.networkPolicy().externalRequestsAllowed()) fail(ErrorCode.NETWORK_SCOPE_DENIED);
        if (new java.util.HashSet<>(selected).size() != selected.size()) fail(ErrorCode.CONTENT_MISMATCH);
        if (!policy.sourceSelection().sources().containsAll(selected)) fail(ErrorCode.ACCESS_DENIED);
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
