package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Actual grant evaluator; store/identity rows are explicit boundary doubles. */
class SimulationFactRetentionTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();final PermissionSchema json=new PermissionSchema();
    final SimulationInvocationStore store=mock(SimulationInvocationStore.class);final PermissionMapper permissions=mock(PermissionMapper.class);
    final SimulationRunTokenVerifier tokens=mock(SimulationRunTokenVerifier.class);final SimulationExecutionPermissionPolicy execute=mock(SimulationExecutionPermissionPolicy.class);
    final SimulationInvocationAuthority target=new SimulationInvocationAuthority(store,schema,execute,permissions,new PermissionAuthority(json),tokens,mock(SimulationEvaluationCandidateAuthority.class));
    JsonNode request,proof;Map<String,Object> retention;LocalDateTime now;String approver,grantId;
    SimulationFactRetentionTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        request=schema.request(SimulationInvocationSchemaTest.resource("admission"));proof=request.required("authority_proof");
        now=PermissionAuthority.time(proof.required("issued_at").textValue());approver=proof.required("actor_id").textValue();grantId=UUID.randomUUID().toString();
        retention=new HashMap<>();retention.put("status","ACTIVE");retention.put("project_id",proof.required("project_id").isNull()?null:proof.required("project_id").textValue());retention.put("evaluation_id",proof.required("evaluation_id").textValue());
        retention.put("schema_manifest_sha256",schema.manifest());retention.put("schema_version","1.0.0");retention.put("revision",1);retention.put("retain_until",PermissionAuthority.time(proof.required("retention_deadline").textValue()));retention.put("maximum_record_bytes",1000);retention.put("created_by",approver);retention.put("approval_grant_id",grantId);retention.put("approval_grant_revision",1);
        when(store.retention(anyString())).thenReturn(retention);when(store.now()).thenReturn(now);
        when(permissions.targetBinding(approver)).thenReturn(List.of(new PermissionProtocol.BindingRow(approver,UUID.randomUUID().toString(),"local",null,null,approver,"ACTIVE")));
        when(permissions.currentGrant(grantId)).thenReturn(List.of(grant("ACTIVE",BigInteger.ONE)));
    }
    PermissionProtocol.GrantRow grant(String status,BigInteger revision)throws Exception {
        var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("SIMULATION_EVALUATION"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of()));
        return new PermissionProtocol.GrantRow(grantId,approver,(String)retention.get("project_id"),"EVALUATION",proof.required("evaluation_id").textValue(),"model.simulation.retention.configure",json.canonicalObject(condition),status,now.minusSeconds(1),now.plusHours(1),UUID.randomUUID().toString(),"1.0.0",json.manifestSha256(),revision);
    }
    @Test void independentRetentionApprovalNeverNeedsNewExecutionGrantOrLease()throws Exception {
        target.requireFactRetention(request,1000);verifyNoInteractions(tokens,execute);verify(store,never()).step(any());verify(store,never()).principal(any());
    }
    @Test void shrunkCapacityAndDeadlineBlockFurtherPersistence() {
        assertThrows(TenantAccessException.class,()->target.requireFactRetention(request,1001));
        when(store.now()).thenReturn((LocalDateTime)retention.get("retain_until"));assertThrows(TenantAccessException.class,()->target.requireFactRetention(request,0));
    }
    @Test void revokedRetentionAndChangedApprovalRevisionCannotReuseOldPermission()throws Exception {
        retention.put("status","REVOKED");assertThrows(TenantAccessException.class,()->target.requireFactRetention(request,1));retention.put("status","ACTIVE");
        when(permissions.currentGrant(grantId)).thenReturn(List.of(grant("REVOKED",BigInteger.ONE)));assertThrows(TenantAccessException.class,()->target.requireFactRetention(request,1));
        when(permissions.currentGrant(grantId)).thenReturn(List.of(grant("ACTIVE",BigInteger.TWO)));assertThrows(TenantAccessException.class,()->target.requireFactRetention(request,1));
    }
}
