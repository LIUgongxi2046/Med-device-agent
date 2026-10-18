package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Mocked admission boundaries, not a database/runtime acceptance test. */
class SimulationWorkerAccessTest {
    final String tenant=id(1),actor=id(2),run=id(3),principal=id(4),evaluation=id(5),member=id(6);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final RunTokenAuthorityStore store=mock(RunTokenAuthorityStore.class);
    final TenantTransaction transactions=mock(TenantTransaction.class);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final ServiceCertificateIdentity services=mock(ServiceCertificateIdentity.class);
    final ServiceCertificateIdentity.Verified service=mock(ServiceCertificateIdentity.Verified.class);
    final HttpServletRequest http=mock(HttpServletRequest.class);
    final RunTokenAuthoritySchema schema=new RunTokenAuthoritySchema();
    final RunTokenAuthorityStore.Run persisted=mock(RunTokenAuthorityStore.Run.class);
    final PermissionAuthority grants=mock(PermissionAuthority.class);
    final SimulationWorkerAccess access=new SimulationWorkerAccess(jdbc,store,transactions,permissions,schema,services,grants);
    SimulationWorkerAccessTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        when(services.require(http)).thenReturn(service);when(service.serviceUri()).thenReturn("spiffe://medical/simulation");
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("SELECT initiator_id"))return List.of(actor);
            if(sql.contains("SELECT target_id"))return List.of(evaluation);
            if(sql.contains("SELECT member_id"))return List.of(member);
            if(sql.contains("platform_local_account"))return List.of(actor);
            throw new AssertionError("Unexpected query: "+sql);
        });
        var epochs=new TenantWatermarks(BigInteger.ONE,BigInteger.ONE);
        when(store.epochs(tenant)).thenReturn(epochs);
        when(transactions.execute(any())).thenAnswer(call->((Function<TenantWatermarks,Object>)call.getArgument(0)).apply(epochs));
        when(store.run(run,actor)).thenReturn(persisted);
        when(persisted.tenant()).thenReturn(tenant);when(persisted.actor()).thenReturn(actor);when(persisted.executionActor()).thenReturn(actor);
        when(persisted.kind()).thenReturn("SYSTEM_JOB");when(persisted.principalStatus()).thenReturn("ACTIVE");when(persisted.status()).thenReturn("QUEUED");
        when(persisted.schema()).thenReturn("1.0.0");when(persisted.principalSchema()).thenReturn("1.0.0");
        when(persisted.manifest()).thenReturn(schema.manifestSha256());when(persisted.principalManifest()).thenReturn(schema.manifestSha256());
        when(persisted.principal()).thenReturn(principal);
        var scope=schema.object().put("schema_version","1.0.0").put("tenant_id",tenant).putNull("project_id").put("initiator_id",actor).put("run_id",run).put("execution_kind","SYSTEM_JOB");
        var resource=schema.tree(new PermissionProtocol.Scope(tenant,null,"EVALUATION",evaluation));scope.putArray("resources").add(resource);
        var manifest=scope.deepCopy();manifest.remove(List.of("execution_kind","resources"));manifest.put("principal_id",principal).put("scope_sha256",schema.sha256(scope));
        manifest.putArray("services").addObject().put("service_uri","spiffe://medical/simulation").put("audience",SimulationRunTokenVerifier.AUDIENCE);
        manifest.putArray("permissions").addObject().put("action",SimulationExecutionPermissionPolicy.ACTION).put("purpose","SIMULATION_EVALUATION").set("scope",resource);
        when(persisted.scope()).thenReturn(schema.canonical(scope));when(persisted.capabilities()).thenReturn(schema.canonical(manifest));
        when(permissions.currentBinding(actor)).thenReturn(List.of(new PermissionProtocol.BindingRow(actor,null,"local",null,null,actor,"ACTIVE")));
    }
    @Test void workerAdmissionHasNoSessionAndUsesPersistedActor() {
        String result=access.execute(http,tenant,run,a->{
            assertTrue(a.simulationWorker());assertEquals(actor,a.actorId());assertEquals("local",a.bindingView().mode());
            assertNull(a.contextId());assertThrows(TenantAccessException.class,a::subject);
            assertEquals(BigInteger.ONE,a.watermarks().authEpoch());return "ok";
        });assertEquals("ok",result);verify(store,times(2)).run(run,actor);
    }
    @Test void productionExecutionOrInactivePrincipalNeverReachOperation() {
        when(persisted.kind()).thenReturn("TASK");assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->{fail();return null;}));
        when(persisted.kind()).thenReturn("SYSTEM_JOB");when(persisted.principalStatus()).thenReturn("REVOKED");
        assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->{fail();return null;}));
    }
    @Test void revokedBindingAndWrongRegisteredServiceFailClosed() {
        when(permissions.currentBinding(actor)).thenReturn(List.of());assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->{fail();return null;}));
        when(service.serviceUri()).thenReturn("spiffe://medical/other");assertThrows(SecurityException.class,()->access.execute(http,tenant,run,a->{fail();return null;}));
    }
    @Test void endOfOperationRechecksRevocation() {
        assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->{when(persisted.principalStatus()).thenReturn("REVOKED");return true;}));
    }
    @Test void revokedExecutionGrantRejectsWorkerBeforeAnyOperation() throws Exception {
        when(grants.requireResource(any(),eq(actor),eq("local"),eq(SimulationExecutionPermissionPolicy.ACTION),eq("SIMULATION_EVALUATION"),anyList(),any()))
            .thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->{fail();return null;}));
    }
    @Test void freshCertificateProofWithSameVerifiedFieldsIsAccepted() throws Exception {
        var refreshed=mock(ServiceCertificateIdentity.Verified.class);
        when(refreshed.serviceUri()).thenReturn("spiffe://medical/simulation");
        when(services.require(http)).thenReturn(service,refreshed);
        assertEquals(Boolean.TRUE,access.execute(http,tenant,run,a->true));
    }
    @Test void certificateTrustChangeBeforeReturnIsRejected() throws Exception {
        var changed=mock(ServiceCertificateIdentity.Verified.class);
        when(changed.serviceUri()).thenReturn("spiffe://medical/simulation");when(changed.trustRevision()).thenReturn("changed");
        when(services.require(http)).thenReturn(service,changed);
        assertThrows(TenantAccessException.class,()->access.execute(http,tenant,run,a->true));
    }
    @Test void workerProofCannotBePubliclyConstructed() {
        for(var constructor:SimulationWorkerAccess.Proof.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        assertThrows(NullPointerException.class,()->new EnterpriseAccess.Admission((SimulationWorkerAccess.Proof)null,null,null));
    }
    private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
