package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Actual shared service gates with explicit DB/authority doubles, not end-to-end execution. */
class EvaluationServiceDependencyUseTest {
    final PermissionSchema json=new PermissionSchema();
    final EvaluationSchema schema=new EvaluationSchema(json);
    final EvaluationMapper mapper=mock(EvaluationMapper.class);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final ResourceReadAuthority authority=mock(ResourceReadAuthority.class);
    final EvaluationReferences references=mock(EvaluationReferences.class);
    final EvaluationRuleAuthority rules=mock(EvaluationRuleAuthority.class);
    final SimulationAuthorizedUseEvidenceAuthority authorizedUses=mock(SimulationAuthorizedUseEvidenceAuthority.class);
    @SuppressWarnings("unchecked") final org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> evidenceProvider=mock(org.springframework.beans.factory.ObjectProvider.class);
    final EnterpriseAccess.Admission actor=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);
    final EvaluationService service;
    EvaluationServiceDependencyUseTest() throws Exception {
        when(evidenceProvider.getIfAvailable()).thenReturn(authorizedUses);
        when(actor.context().tenant_id()).thenReturn(EvaluationFixture.id(2));
        when(actor.actorId()).thenReturn(EvaluationFixture.id(5));
        when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,0,0));
        service=new EvaluationService(null,mapper,permissions,authority,references,schema,new EvaluationVerdict(schema),json,new IdentityDirectoryPolicy("TEST"),rules,evidenceProvider);
    }
    Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        var method=EvaluationService.class.getDeclaredMethod(name,types);method.setAccessible(true);
        try {return method.invoke(service,args);}catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception cause)throw cause;throw failure;
        }
    }
    EvaluationProtocol.Create request(String evidence,String environment)throws Exception {
        var value=EvaluationFixture.create(schema,List.of(EvaluationFixture.sample(1000,evidence,"DEVELOPMENT")),List.of("EVAL-02"),1);
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(value);node.put("environment",environment);
        return schema.create(json.canonical(node).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void candidateMetadataGateUsesDefinitionPurposeWithoutChangingConfigurationScope()throws Exception {
        for(var request:List.of(request("SIMULATION","SIMULATION"),request("REAL","SIMULATION"),request("SIMULATION","PRODUCTION"))) {
            reset(authority,mapper);
            when(mapper.candidate(anyMap())).thenAnswer(call->{
                Map<String,Object> values=call.getArgument(0);String id=(String)values.get("candidateId");
                var ref=request.candidates().stream().filter(c->c.version_id().equals(id)).findFirst().orElseThrow();
                return List.of(new EvaluationProtocol.AgentRow(EvaluationFixture.id(2),request.project_id(),id,ref.content_sha256(),ref.content_sha256(),
                    EvaluationFixture.id(30),EvaluationFixture.id(31),"{}","{}","DRAFT","1.0.0","b".repeat(64)));
            });
            var result=invoke("candidates",new Class[]{EnterpriseAccess.Admission.class,EvaluationProtocol.Create.class,boolean.class},actor,request,true);
            assertEquals(request.candidates().size(),((List<?>)result).size());
            verify(authority,times(request.candidates().size())).require(eq(actor),eq(new PermissionProtocol.Scope(EvaluationFixture.id(2),request.project_id(),"CONFIGURATION",EvaluationFixture.id(30))),eq("configuration.read"),eq(EvaluationDependencyUse.forDefinition(request).purpose()));
        }
    }
    @Test void sharedReferenceGateRechecksArtifactsAndRuleUnderTheSameDerivedPurpose()throws Exception {
        for(var request:List.of(request("SIMULATION","SIMULATION"),request("REAL","SIMULATION"))) {
            reset(rules,references,authorizedUses);
            invoke("checkReferences",new Class[]{EnterpriseAccess.Admission.class,EvaluationProtocol.Create.class,List.class},actor,request,List.of());
            var use=EvaluationDependencyUse.forDefinition(request);
            verify(rules).require(actor,request.rule_revision(),false,use);
            if(use==EvaluationDependencyUse.SIMULATION)verify(authorizedUses).require(actor,request);
            else verifyNoInteractions(authorizedUses);
            verify(references).require(eq(actor),eq(request.project_id()),anyList(),eq(use.purpose()));
            verify(references).artifact(request.project_id(),request.samples().get(0).input_ref());
            verify(references).artifact(request.project_id(),request.sample_manifest_artifact(),EvaluationSchema.SAMPLE_MANIFEST_REF);
        }
    }
    @Test void evaluationGovernanceActionsRetainOriginalPurposeAndResourceScope()throws Exception {
        for(String action:List.of("evaluation.create","evaluation.read","evaluation.score","evaluation.finalize")) {
            invoke("require",new Class[]{EnterpriseAccess.Admission.class,String.class,String.class,String.class},actor,EvaluationFixture.id(1),EvaluationFixture.id(3),action);
            verify(authority).require(actor,new PermissionProtocol.Scope(EvaluationFixture.id(2),EvaluationFixture.id(1),"EVALUATION",EvaluationFixture.id(3)),action,"EVALUATION_RESULTS");
        }
    }
    @Test void simulationReadUsesRealWorkerPurposeBoundaryWithoutAnApplicationSession()throws Exception {
        String tenant=EvaluationFixture.id(2),user=EvaluationFixture.id(5),id=EvaluationFixture.id(3),project=EvaluationFixture.id(1);
        var proof=mock(SimulationWorkerAccess.Proof.class);when(proof.actorId()).thenReturn(user);
        var binding=mock(com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View.class);when(binding.mode()).thenReturn("local");when(proof.bindingView()).thenReturn(binding);
        var epochs=new TenantWatermarks(java.math.BigInteger.ONE,java.math.BigInteger.ONE);
        var worker=new EnterpriseAccess.Admission(proof,new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(tenant,"1","1","1"),epochs);
        assertThrows(TenantAccessException.class,worker::subject);
        var grants=mock(PermissionAuthority.class);var granted=mock(PermissionProtocol.GrantRow.class);
        when(permissions.resourceOwner("EVALUATION",id)).thenReturn(List.of(new PermissionProtocol.OwnerRow(id,project)));
        when(grants.findResource(any(),eq(user),eq("local"),eq("evaluation.read"),eq("SIMULATION_EVALUATION"),anyList(),any()))
            .thenReturn(java.util.Optional.of(granted));
        var realAuthority=new ResourceReadAuthority(permissions,grants,json);
        var target=new EvaluationService(null,mapper,permissions,realAuthority,references,schema,new EvaluationVerdict(schema),json,new IdentityDirectoryPolicy("TEST"),rules,evidenceProvider);
        // Stop after the actual authority boundary with an intentionally malformed fixed definition.
        when(mapper.definition(anyMap())).thenReturn(List.of(new EvaluationProtocol.Stored(tenant,project,id,"{}","a".repeat(64),"a".repeat(64),"{}","QUEUED",java.math.BigInteger.ONE,null,null,"1.0.0",schema.manifestSha256(),user,LocalDateTime.of(2026,10,18,0,0),"TEST")));
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,user,java.math.BigInteger.ONE,java.math.BigInteger.ONE,java.math.BigInteger.ONE))) {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                scope.verifyTransaction(epochs);
                assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->target.requireSimulationDefinition(worker,id,"a".repeat(64)));
                verify(grants).findResource(any(),eq(user),eq("local"),eq("evaluation.read"),eq("SIMULATION_EVALUATION"),anyList(),any());
                assertThrows(TenantAccessException.class,()->realAuthority.require(worker,new PermissionProtocol.Scope(tenant,project,"EVALUATION",id),"evaluation.read","EVALUATION_RESULTS"));
                scope.finishTransaction();
            } finally {org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
    }

}
