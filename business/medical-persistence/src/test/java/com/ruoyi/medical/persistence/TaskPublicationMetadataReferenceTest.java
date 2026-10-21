package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual metadata-only resolver; acceptance/SQL are explicit synthetic boundaries. */
class TaskPublicationMetadataReferenceTest {
    static class Fixture {
        final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class, RETURNS_DEEP_STUBS);
        final AgentReleaseStore.Stored release = mock(AgentReleaseStore.Stored.class, RETURNS_DEEP_STUBS);
        final FormalAgentAcceptanceStore acceptances = mock(FormalAgentAcceptanceStore.class);
        final FormalAgentAcceptanceStore.Loaded original = mock(FormalAgentAcceptanceStore.Loaded.class, RETURNS_DEEP_STUBS);
        final JdbcTemplate db = mock(JdbcTemplate.class);
        final AgentPublicationAuthority authority;
        Fixture() throws Exception {
            authority = new AgentPublicationAuthority(null, mock(DataSource.class), acceptances, null, null, null,
                    null, null, null, null, null, null);
            ReflectionTestUtils.setField(authority, "db", db);
            when(release.admission()).thenReturn(admission); when(release.purpose()).thenReturn("TASK_CREATION");
            when(release.release().releaseId()).thenReturn("release"); when(release.agent().agentVersionId()).thenReturn("candidate");
            when(admission.context().tenant_id()).thenReturn("tenant");
            when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(new PermissionProtocol.Basis("acceptance", "a".repeat(64))));
            when(acceptances.metadata(eq(admission), any(), eq("TASK_CREATION"))).thenReturn(original);
            when(original.row().releaseId()).thenReturn("release"); when(original.row().candidateVersionId()).thenReturn("candidate");
            var manifest = release.content(); when(original.manifest()).thenReturn(manifest);
            when(original.candidate().binding().policyVersionId()).thenReturn("policy");
            when(original.candidate().binding().policySha256()).thenReturn("b".repeat(64));
        }
        PermissionProtocol.Basis read() throws Exception { return authority.taskSourcePolicyReference(admission, release, "TASK_CREATION"); }
    }
    @Test void exactRegistrationAcceptanceManifestReturnsOnlyPolicyIdentity() throws Exception {
        var f = new Fixture(); assertEquals(new PermissionProtocol.Basis("policy", "b".repeat(64)), f.read());
        // Transport/evidence/live-approval dependencies are deliberately absent: metadata cannot mint their proofs.
        verify(f.db).query(contains("JOIN formal_agent_publication_registration"), any(RowMapper.class), eq("tenant"), eq("release"));
    }
    @Test void missingRegistrationJoinCannotFallBackToCandidatePolicy() throws Exception {
        var f = new Fixture(); when(f.db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertEquals(ErrorCode.OBJECT_UNAVAILABLE, assertThrows(TenantAccessException.class, f::read).code());
        verifyNoInteractions(f.acceptances);
    }
    @Test void differentAcceptedManifestAndCandidateReject() throws Exception {
        var f = new Fixture(); when(f.original.row().candidateVersionId()).thenReturn("another");
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, f::read).code());
        when(f.original.row().candidateVersionId()).thenReturn("candidate");
        when(f.original.manifest()).thenReturn(mock(AgentReleaseProtocol.Validated.class));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, f::read).code());
    }
    @Test void anotherAdmissionOrPurposeCannotReuseStoredRelease() throws Exception {
        var f = new Fixture(); when(f.release.purpose()).thenReturn("RUN_CREATION");
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, f::read).code());
        verifyNoInteractions(f.db, f.acceptances);
    }
}
