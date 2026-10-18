package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure policy and real existing permission evaluator; creates no real users, grants or runs. */
class SimulationExecutionPermissionPolicyTest {
    final PermissionSchema json=new PermissionSchema();
    final PermissionAuthority evaluator=new PermissionAuthority(json);
    final LocalDateTime now=LocalDateTime.of(2026,10,18,13,0);
    SimulationExecutionPermissionPolicyTest()throws Exception{}
    static String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}
    PermissionProtocol.Scope scope(){return new PermissionProtocol.Scope(id(1),id(2),"EVALUATION",id(3));}
    PermissionProtocol.GrantRow grant(String action,String purpose,String target,String status,LocalDateTime until)throws Exception {
        var c=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),
            new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of()));
        return new PermissionProtocol.GrantRow(id(4),id(5),id(2),"EVALUATION",target,action,json.canonicalObject(c),status,
            now.minusMinutes(1),until,id(6),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    void permitted(List<PermissionProtocol.GrantRow> grants)throws Exception {
        evaluator.requireResource(scope(),id(5),"local",SimulationExecutionPermissionPolicy.ACTION,SimulationExecutionPermissionPolicy.PURPOSE,grants,now);
    }
    @Test void registrationIsBoundedImmutableAndPreservesOriginalPermissionManifest()throws Exception {
        var authority=mock(ResourceReadAuthority.class);var policy=new SimulationExecutionPermissionPolicy(json,authority);
        assertEquals("6345592f8753d1f0a392d773790c2f63c975145d84228226c10720a69ac707c6",json.manifestSha256());
        assertFalse(policy.definition().required("grants_created").booleanValue());
        assertFalse(policy.definition().required("production_eligible").booleanValue());
        assertEquals("EVALUATION",policy.definition().required("resource_kind").textValue());
        assertEquals(64,policy.manifestSha256().length());
        ((com.fasterxml.jackson.databind.node.ObjectNode)policy.definition()).put("action","evaluation.create");
        assertEquals(SimulationExecutionPermissionPolicy.ACTION,policy.definition().required("action").textValue());
        verifyNoInteractions(authority);
    }
    @Test void existingDefinitionReadWriteAndScoringGrantsDoNotAuthorizeExecution()throws Exception {
        for(String action:List.of("evaluation.create","evaluation.read","evaluation.score","evaluation.finalize","configuration.read","model.configure"))
            assertThrows(TenantAccessException.class,()->permitted(List.of(grant(action,SimulationExecutionPermissionPolicy.PURPOSE,id(3),"ACTIVE",now.plusHours(1)))));
        assertThrows(TenantAccessException.class,()->permitted(List.of()));
    }
    @Test void exactCurrentGrantAllowsOnlyItsActualEvaluationPurposeAndLifetime()throws Exception {
        assertDoesNotThrow(()->permitted(List.of(grant(SimulationExecutionPermissionPolicy.ACTION,SimulationExecutionPermissionPolicy.PURPOSE,id(3),"ACTIVE",now.plusSeconds(1)))));
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationExecutionPermissionPolicy.ACTION,"EVALUATION_RESULTS",id(3),"ACTIVE",now.plusHours(1)))));
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationExecutionPermissionPolicy.ACTION,SimulationExecutionPermissionPolicy.PURPOSE,id(7),"ACTIVE",now.plusHours(1)))));
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationExecutionPermissionPolicy.ACTION,SimulationExecutionPermissionPolicy.PURPOSE,id(3),"REVOKED",now.plusHours(1)))));
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationExecutionPermissionPolicy.ACTION,SimulationExecutionPermissionPolicy.PURPOSE,id(3),"ACTIVE",now))));
    }
    @Test void quotaRegistrationCannotGrantExecutionOrFinancialApproval()throws Exception {
        var authority=mock(ResourceReadAuthority.class);var policy=new SimulationQuotaPermissionPolicy(json,authority);
        assertFalse(policy.definition().required("authorizes_execution").booleanValue());
        assertFalse(policy.definition().required("financial_budget_approval").booleanValue());
        assertFalse(policy.definition().required("grants_created").booleanValue());
        assertEquals(64,policy.manifestSha256().length());verifyNoInteractions(authority);
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationQuotaPermissionPolicy.ACTION,SimulationQuotaPermissionPolicy.PURPOSE,id(3),"ACTIVE",now.plusHours(1)))));
    }

    @Test void retentionRegistrationIsSeparateAndDoesNotSubstituteExecution()throws Exception {
        var authority=mock(ResourceReadAuthority.class);var policy=new SimulationRetentionPermissionPolicy(json,authority);
        assertEquals("model.simulation.retention.configure",policy.definition().required("action").textValue());
        assertEquals(List.of("EVALUATION"),new com.fasterxml.jackson.databind.ObjectMapper().convertValue(policy.definition().required("resource_kinds"),List.class));
        assertFalse(policy.definition().required("authorizes_execution").booleanValue());verifyNoInteractions(authority);
        assertThrows(TenantAccessException.class,()->permitted(List.of(grant(SimulationRetentionPermissionPolicy.ACTION,SimulationRetentionPermissionPolicy.PURPOSE,id(3),"ACTIVE",now.plusHours(1)))));
    }

}
