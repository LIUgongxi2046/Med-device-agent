package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual orchestration, synthetic transaction/database boundaries. Does not claim real SQL rollback coverage. */
class TaskCreatePreparationOrderTest {
    static class Fixture {
        final EnterpriseAccess access = mock(EnterpriseAccess.class);
        final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class, RETURNS_DEEP_STUBS);
        final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
        final TaskInputSchema inputs = mock(TaskInputSchema.class);
        final ResourceReadAuthority resources = mock(ResourceReadAuthority.class);
        final TaskPlanWriteMapper writes = mock(TaskPlanWriteMapper.class);
        final TaskCreateReleaseAdmission provider = mock(TaskCreateReleaseAdmission.class);
        final TaskCreateService service;
        final TaskProtocol.ValidatedCreate input;
        Fixture() throws Exception {
            String id = "00000000-0000-4000-8000-000000000001";
            input = new TaskProtocol.ValidatedCreate(new TaskProtocol.Create("1.0.0", id, id, "TECHNICAL_REQUIREMENTS", id, id,
                    new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS, List.of(), List.of(), false, null, null),
                    id, "Test only", null), "{}", "a".repeat(64), "b".repeat(64), "c".repeat(64));
            when(inputs.create(any(byte[].class))).thenReturn(input);
            when(admission.context().context_revision()).thenReturn("1");
            when(admission.context().tenant_id()).thenReturn(id);
            when(admission.subject().user().user_id()).thenReturn(id);
            when(admission.watermarks().authEpoch()).thenReturn(java.math.BigInteger.ONE);
            when(admission.watermarks().governanceEpoch()).thenReturn(java.math.BigInteger.ONE);
            when(access.execute(eq(subject), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try { return ((Function<EnterpriseAccess.Admission, Object>) call.getArgument(1)).apply(admission); }
                finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            });
            ObjectProvider<TaskCreateReleaseAdmission> providers = mock(ObjectProvider.class);
            when(providers.getIfAvailable()).thenReturn(provider);
            var identity = mock(IdentityDirectoryPolicy.class); when(identity.classification()).thenReturn("INTERNAL");
            service = new TaskCreateService(access, inputs, new PermissionSchema(), resources,
                    mock(TemplateApplicabilityService.class), mock(ScopeSelectionAuthority.class), mock(ScopeSnapshotStore.class),
                    mock(ScopeSnapshotSchema.class), mock(AgentReleaseStore.class), mock(AgentReleaseScopeAuthority.class), providers,
                    mock(TemplateDependencyPolicy.class), identity, mock(PermissionMapper.class), writes, mock(TaskMapper.class),
                    mock(TaskService.class), mock(TaskPlanService.class), mock(DependencyLineage.class));
        }
        void create() { service.create(subject, "1", "synthetic-task-key", new byte[]{'{', '}'}); }
    }
    @Test void failedPreparationIsOutsideTransactionAndCannotBeginWrites() throws Exception {
        var f = new Fixture(); when(f.provider.prepare(f.subject, "1", f.input)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        });
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class, f::create).code());
        verify(f.writes).operation(any()); verify(f.writes, never()).beginOperation(any());
        verify(f.provider, never()).require(any(), any(), any(), any(), any());
    }
    @Test void conflictingOriginalKeyIsRejectedBeforeAnyPhysicalPreparation() throws Exception {
        var f = new Fixture(); var row = mock(SelectionOperationRow.class); when(row.requestSha256()).thenReturn("different");
        when(f.writes.operation(any())).thenReturn(List.of(row));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, assertThrows(TenantAccessException.class, f::create).code());
        verifyNoInteractions(f.provider); verify(f.writes, never()).beginOperation(any());
    }
    @Test void creationAuthorityIsCheckedBeforePublicationMetadataOrBytes() throws Exception {
        var f = new Fixture(); doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources).require(any(), any(), eq("task.create"), eq("TASK_CREATION"));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, f::create).code());
        verifyNoInteractions(f.provider, f.writes);
    }
    @Test void changedContextCannotReachPreparation() throws Exception {
        var f = new Fixture(); when(f.admission.context().context_revision()).thenReturn("2");
        assertEquals(ErrorCode.CONTEXT_CHANGED, assertThrows(TenantAccessException.class, f::create).code());
        verifyNoInteractions(f.provider, f.resources, f.writes);
    }
}
