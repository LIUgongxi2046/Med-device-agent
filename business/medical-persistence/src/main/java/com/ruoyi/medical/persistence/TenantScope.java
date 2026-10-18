package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.math.BigInteger;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TenantScope implements AutoCloseable {
    private static final ThreadLocal<TenantScope> CURRENT = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    private final Identity identity;
    private final String discoveryTenantId;
    private final String discoverySubjectId;
    private boolean discoveryTenantLocked;
    private boolean transactionVerified;
    private boolean invitation;
    private boolean provisioning;
    private static final java.util.Set<String> PROVISIONING_STATEMENTS = java.util.Set.of(
            "insertTenant", "insertBinding", "insertMember", "insertBasis", "insertGrant", "saveEvent", "state", "grants", "lockTenant");
    private static final java.util.Set<String> INVITATION_STATEMENTS = java.util.Set.of("role", "currentInvitation", "pending",
            "inviterDelegations", "currentOperation", "insertBinding", "insertMember", "insertInvitation", "acceptInvitation",
            "activateMember", "expire", "saveOperation", "saveAudit", "saveEvent", "roleHead", "insertOrigin", "origins");
    private static final java.util.Set<String> INVITATION_PERMISSION_STATEMENTS = java.util.Set.of(
            "targetMember", "targetBinding", "basis", "resourceOwner", "projectStatus", "databaseTime", "currentGrant", "insertGrant", "advanceAuthEpoch");

    // 只有同包服务端身份核查入口能够建立上下文。
    record Identity(String tenantId, String subjectId, BigInteger contextRevision,
                    BigInteger authEpoch, BigInteger governanceEpoch) {
        Identity {
            WireValues.uuid(Objects.requireNonNull(tenantId));
            WireValues.uuid(Objects.requireNonNull(subjectId));
            WireValues.revision(Objects.requireNonNull(contextRevision).toString());
            WireValues.revision(Objects.requireNonNull(authEpoch).toString());
            WireValues.revision(Objects.requireNonNull(governanceEpoch).toString());
        }
    }

    private TenantScope(Identity identity) { this.identity = identity; discoveryTenantId = null; discoverySubjectId = null; }

    private TenantScope(ApplicationSessionIdentity.Verified subject, String tenantId) {
        Objects.requireNonNull(subject);
        WireValues.uuid(tenantId);
        identity = null; discoveryTenantId = tenantId; discoverySubjectId = subject.user().user_id();
    }

    static TenantScope openDiscovery(ApplicationSessionIdentity.Verified subject, String tenantId) {
        if (CURRENT.get() != null) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        TenantScope scope = new TenantScope(subject, tenantId);
        CURRENT.set(scope);
        return scope;
    }

    static TenantScope openInvitation(ApplicationSessionIdentity.Verified subject, String tenantId) {
        TenantScope scope = openDiscovery(subject, tenantId);
        scope.invitation = true;
        return scope;
    }

    static TenantScope openProvisioning(ApplicationSessionIdentity.Verified subject, String tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        TenantScope scope = openDiscovery(subject, tenantId);
        scope.provisioning = true;
        return scope;
    }

    static TenantScope open(Identity identity) {
        if (identity == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (CURRENT.get() != null) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        TenantScope scope = new TenantScope(identity);
        CURRENT.set(scope);
        return scope;
    }

    static TenantScope requireCurrent() {
        TenantScope scope = CURRENT.get();
        if (scope == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return scope;
    }

    String tenantId() { return discoveryTenantId == null ? identity.tenantId() : discoveryTenantId; }
    String subjectId() { return discoveryTenantId == null ? identity.subjectId() : discoverySubjectId; }

    static void requireSubjectParameter(String statementId, Object parameter) {
        if (statementId.equals("com.ruoyi.medical.persistence.mapper.SourceReadMapper.snapshotCandidate")
                || statementId.equals("com.ruoyi.medical.persistence.mapper.SourceReadMapper.scopeCandidates")
                || statementId.equals("com.ruoyi.medical.persistence.mapper.SourceReadMapper.collectionMembers")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.ProjectMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.MemberGovernanceReadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.TaskMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RunReadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RunControlMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.TaskPlanRelationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AgentReleaseMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ScenarioCatalogueMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ScopeSnapshotWriteMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.CollectionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.TemplateMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.OperationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ContentReadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.UploadTransferMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceUploadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentReadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AnnotationReplyMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AnnotationDecisionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AnnotationThreadMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AnnotationAnchorMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AnnotationAnchorReconciliationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentRevisionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ExportMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.BudgetMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ModelConfigurationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ConfigurationAuthoringMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RuleVersionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.EvaluationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationInputArtifactMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationAuthorizedUseEvidenceMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationEvaluationPreparationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.GruMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentReviewReturnMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentTechnicalCheckMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentRevisionDirectoryMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.BusinessAuditMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RuntimeRetentionMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.FormalAgentCandidateMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.FormalAgentAcceptanceMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.FormalEvaluationPolicyMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ConnectionVerificationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ModelEligibilityMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.AgentExecutionPolicyMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.FormalEvaluationInputMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ModelVerificationInputMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.ModelEnablementPolicyMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.FormalCapabilityCancellationMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.GruRecordExportMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RunCreateMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.RetrievalMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceExactMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceRightsMapper.")
                || statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceDeletionMapper.")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceProcessingMapper.")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceIndexMapper.")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.SourceCompletionMapper.")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.EnterpriseRoleMapper.")
                && java.util.Set.of("managers", "currentOperation", "saveOperation", "insertVersion", "insertRole", "insertPublication", "insertHead", "advanceHead")
                   .contains(statementId.substring(statementId.lastIndexOf('.') + 1))) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.EnterpriseProvisioningMapper.")) {
            requireSubject(parameter); return;
        }
        if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.InvitationMapper.")
                && java.util.Set.of("currentOperation", "saveOperation", "saveAudit", "saveEvent", "insertInvitation", "insertBinding", "insertMember", "insertOrigin")
                   .contains(statementId.substring(statementId.lastIndexOf('.') + 1))) {
            requireSubject(parameter); return;
        }
        if (!statementId.equals("com.ruoyi.medical.persistence.mapper.TenantMemberMapper.currentMember")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.TenantMemberMapper.currentActions")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.TenantSelectionMapper.currentOperation")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.TenantSelectionMapper.saveOperation")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.TenantSelectionMapper.saveAudit")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.TenantSelectionMapper.saveEvent")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.currentDelegations")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.currentBinding")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.currentOperation")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.insertGrant")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.saveOperation")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.saveAudit")
                && !statementId.equals("com.ruoyi.medical.persistence.mapper.PermissionMapper.saveEvent")) return;
        requireSubject(parameter);
    }

    private static void requireSubject(Object parameter) {
        TenantScope scope = requireCurrent();
        String subjectId = scope.discoveryTenantId == null ? scope.identity.subjectId() : scope.discoverySubjectId;
        if (!(parameter instanceof java.util.Map<?, ?> values) || !subjectId.equals(values.get("userId"))) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
    }

    static void requireSqlAccess(String statementId) {
        TenantScope scope = requireCurrent();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        if (scope.provisioning) {
            if (statementId.startsWith("com.ruoyi.medical.persistence.mapper.EnterpriseProvisioningMapper.")
                    && PROVISIONING_STATEMENTS.contains(statementId.substring(statementId.lastIndexOf('.') + 1))) return;
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        if (statementId.equals("com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper.lockCurrentTenant")) return;
        if (scope.discoveryTenantId != null) {
            if (scope.invitation && scope.discoveryTenantLocked
                    && (statementId.startsWith("com.ruoyi.medical.persistence.mapper.InvitationMapper.")
                            && INVITATION_STATEMENTS.contains(statementId.substring(statementId.lastIndexOf('.') + 1))
                        || statementId.startsWith("com.ruoyi.medical.persistence.mapper.PermissionMapper.")
                            && INVITATION_PERMISSION_STATEMENTS.contains(statementId.substring(statementId.lastIndexOf('.') + 1)))) return;
            if (scope.discoveryTenantLocked && statementId.equals("com.ruoyi.medical.persistence.mapper.TenantMemberMapper.currentMember")) return;
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        if (!scope.transactionVerified) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    void lockDiscovery(TenantWatermarks current) {
        if (CURRENT.get() != this || discoveryTenantId == null || discoveryTenantLocked || current == null
                || !TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        discoveryTenantLocked = true;
    }

    void verifyTransaction(TenantWatermarks current) {
        if (CURRENT.get() != this || transactionVerified || discoveryTenantId != null) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        if (current == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (!identity.authEpoch().equals(current.authEpoch())
                || !identity.governanceEpoch().equals(current.governanceEpoch())) {
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        transactionVerified = true;
    }

    void finishTransaction() {
        if (CURRENT.get() != this) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        transactionVerified = false;
        discoveryTenantLocked = false;
    }

    @Override public void close() {
        if (Thread.currentThread() != owner || CURRENT.get() != this || transactionVerified || discoveryTenantLocked) {
            throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        }
        CURRENT.remove();
    }
}
