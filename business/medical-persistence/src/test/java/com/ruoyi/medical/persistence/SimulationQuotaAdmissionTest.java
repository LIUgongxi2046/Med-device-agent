package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SimulationQuotaAdmissionTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    final SimulationInvocationStore store=mock(SimulationInvocationStore.class);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final PermissionAuthority grants=mock(PermissionAuthority.class);
    final SimulationInvocationAuthority authority=new SimulationInvocationAuthority(store,schema,null,permissions,grants,null,null);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final Map<String,Object> account=new HashMap<>();JsonNode proof;
    SimulationQuotaAdmissionTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        proof=schema.request(SimulationInvocationSchemaTest.resource("admission")).required("authority_proof");
        var subject=mock(ApplicationSessionIdentity.Verified.class);var identity=mock(AuthenticatedIdentityBinding.class);
        String actor=proof.path("actor_id").asText();when(admission.subject()).thenReturn(subject);when(admission.actorId()).thenReturn(actor);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(actor,"test","test","USER"));when(subject.binding()).thenReturn(identity);
        when(identity.view()).thenReturn(new AuthenticatedIdentityBinding.View("local",actor,null,null));when(admission.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",actor,null,null));
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(proof.path("tenant_id").asText(),"1","1","1"));
        when(store.now()).thenReturn(LocalDateTime.of(2026,10,18,0,0));
        var grant=mock(PermissionProtocol.GrantRow.class);when(grant.grantId()).thenReturn("approved-grant");when(grant.revision()).thenReturn(BigInteger.ONE);
        when(permissions.resourceGrants(actor,"model.simulation.quota.configure")).thenReturn(List.of(grant));
        when(grants.requireResource(any(),eq(actor),eq("local"),eq("model.simulation.quota.configure"),eq("SIMULATION_EVALUATION"),anyList(),any())).thenReturn(grant);
        account.put("status","ACTIVE");account.put("schema_version","1.0.0");account.put("schema_manifest_sha256",schema.manifest());account.put("revision",1);
        account.put("counter",schema.canonical(proof.required("counter")));account.put("counter_sha256",schema.hash(proof.required("counter")));
        account.put("scope","ENTERPRISE");account.put("approval_grant_id","approved-grant");account.put("approval_grant_revision",1);
        account.put("max_units",100);account.put("max_calls",1);account.put("spent_units",0);account.put("reserved_units",100);account.put("spent_calls",0);account.put("reserved_calls",1);
    }
    @Test void lastReservedEnterpriseAndParentCallMayDispatchWithoutAnotherFreeSlot()throws Exception {
        assertDoesNotThrow(()->authority.account(admission,proof,account,true,0));
        account.put("scope","PARENT_RUN");account.put("parent_run_id",proof.path("parent_run_id").asText());account.put("project_id",proof.path("project_id").asText());
        assertDoesNotThrow(()->authority.account(admission,proof,account,false,0));
        assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,false,1));
    }
    @Test void newReservationRejectsEitherExhaustedDimension() {
        assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,true,1));
        account.put("reserved_units",0);assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,true,1));
        account.put("reserved_calls",0);assertDoesNotThrow(()->authority.account(admission,proof,account,true,100));
    }
    @Test void countersCannotBeNegativeOverdrawnOrBeyondCanonicalIntegerBound() {
        for(Object value:List.of(-1,101,new BigInteger("9007199254740992"))) {
            account.put("reserved_units",value);assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,true,0));
        }
    }
    @Test void parentScopeAndApprovedGrantRevisionAreNotCallerAssertions() {
        account.put("project_id",proof.path("project_id").asText());assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,true,0));
        account.remove("project_id");account.put("approval_grant_revision",2);assertThrows(TenantAccessException.class,()->authority.account(admission,proof,account,true,0));
    }
}
