package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent supervisor request/authentication boundary, with explicit certificate authority
 * doubles. No process, deployed mTLS trust or successful gate release is claimed. */
class TaskProfessionalSupervisorGateTest {
    final PermissionSchema json=new PermissionSchema();final DataSource data=mock(DataSource.class);
    final ServiceCertificateIdentity identities=mock(ServiceCertificateIdentity.class);
    final ServiceCertificateIdentity.Verified peer=mock(ServiceCertificateIdentity.Verified.class);
    final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);
    final MockHttpServletRequest http=new MockHttpServletRequest();final TaskProfessionalSupervisorGate gate;
    TaskProfessionalSupervisorGateTest()throws Exception {
        var d=json.object().put("supervisor_service_uri","spiffe://test/supervisor").put("supervisor_certificate_sha256","a".repeat(64)).put("supervisor_trust_revision",id(1));when(deployment.value()).thenReturn(d);when(deployment.workerService()).thenReturn("spiffe://test/worker");
        when(identities.require(http)).thenReturn(peer);when(peer.serviceUri()).thenReturn("spiffe://test/supervisor");when(peer.certificateSha256()).thenReturn("a".repeat(64));when(peer.trustRevision()).thenReturn(id(1));
        gate=new TaskProfessionalSupervisorGate(data,mock(TenantTransaction.class),identities,deployment,mock(PermissionMapper.class),mock(PermissionAuthority.class),mock(PlatformMemberIdentityStatus.class),json,new TaskNodeProducerSchema(),new TaskDelegationSchema(json),mock(SourceUploadTransport.class),new TaskProfessionalSchema());
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    ObjectNode request(){var value=json.object().put("session_id",id(6)).put("isolation_receipt_id",id(7)).put("supervisor_instance_id",id(8)).put("supervision_sha256","b".repeat(64)).put("process_unit_sha256","c".repeat(64));var b=value.putObject("binding").put("tenant_id",id(2)).put("run_id",id(3)).put("step_id",id(4)).put("action_id",id(5)).put("lease_generation",1).put("input_sha256","d".repeat(64)).put("step_envelope_sha256","e".repeat(64));return value;}
    JsonNode plan(){var request=request();request.remove(List.of("isolation_receipt_id","supervisor_instance_id","supervision_sha256","process_unit_sha256"));return request;}
    @Test void workerCertificateIsNotSupervisorEvenWithAllCallerPins()throws Exception {when(peer.serviceUri()).thenReturn("spiffe://test/worker");assertThrows(TenantAccessException.class,()->gate.check(http,request()));verifyNoInteractions(data);}
    @Test void changedCertificateOrTrustCannotReadPreparedEnvelope()throws Exception {when(peer.certificateSha256()).thenReturn("f".repeat(64));assertThrows(TenantAccessException.class,()->gate.plan(http,plan()));when(peer.certificateSha256()).thenReturn("a".repeat(64));when(peer.trustRevision()).thenReturn(id(99));assertThrows(TenantAccessException.class,()->gate.plan(http,plan()));verifyNoInteractions(data);}
    @Test void currentTrustFailureStopsBeforeAnyDatabaseOrObjectRead()throws Exception {when(identities.require(http)).thenThrow(new SecurityException("revoked"));assertThrows(SecurityException.class,()->gate.check(http,request()));verifyNoInteractions(data);}
    @Test void selfSupervisorDeploymentIsRejected()throws Exception {when(deployment.workerService()).thenReturn("spiffe://test/supervisor");assertThrows(TenantAccessException.class,()->gate.plan(http,plan()));verifyNoInteractions(data);}
    @Test void callerFlagsCannotReplaceExactGateProcessIdentity(){var value=request().put("isolated",true);assertThrows(TenantAccessException.class,()->TaskProfessionalSupervisorGate.validateRequest(value));var missing=request();missing.remove("process_unit_sha256");assertThrows(TenantAccessException.class,()->TaskProfessionalSupervisorGate.validateRequest(missing));}
    @Test void supervisorNetworkEntryCannotBeReusedInsideWorkerTransaction()throws Exception {TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThrows(TenantAccessException.class,()->gate.check(http,request()));assertThrows(TenantAccessException.class,()->gate.plan(http,plan()));verifyNoInteractions(identities,data);}finally{TransactionSynchronizationManager.clear();}}
    @Test void gateProofCannotBeConstructedByCaller(){for(var constructor:TaskProfessionalSupervisorGate.Verified.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));}
}
