package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

/** Historical policy/provenance seams are explicit doubles; no execution proof is created by a read. */
class FormalEvaluationContextReadAuthorityTest {
    final FormalEvaluationContextDependenciesTest f=new FormalEvaluationContextDependenciesTest();
    final AgentExecutionPolicyAuthority.HistoricalPolicy historical=mock(AgentExecutionPolicyAuthority.HistoricalPolicy.class);
    final AgentPublicationDependencies provenance=mock(AgentPublicationDependencies.class);
    final FormalEvaluationContextReadAuthority reads=new FormalEvaluationContextReadAuthority(f.candidates,f.policies,provenance,f.json);
    FormalEvaluationContextReadAuthorityTest()throws Exception {
        when(f.candidates.require(f.a,b(2),id(1),"AGENT_PUBLICATION")).thenReturn(f.candidate);
        when(f.policies.requireHistoricalByVersion(f.a,id(3),"a".repeat(64),id(1),"INTERNAL","AGENT_PUBLICATION")).thenReturn(historical);
        when(historical.definition()).thenReturn(f.policyDefinition);
        when(provenance.require(eq(f.a),eq(id(1)),anyList(),eq("AGENT_PUBLICATION"))).thenReturn(new AgentPublicationDependencies.Checked(List.of(id(4),id(5),id(6)),f.json.object().put("current_read_rights",true)));
    }
    interface Work {void run()throws Exception;}
    void tx(Work work)throws Exception {f.open();try{work.run();}finally{f.close();}}
    @Test void disabledCandidateAndDisabledPolicyRemainReadableButCannotBeExecutionAuthority()throws Exception {
        when(f.candidate.status()).thenReturn("DISABLED");
        when(f.policies.requireByVersion(any(),anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new TenantAccessException(ErrorCode.SOURCE_NOT_READY));
        tx(()->{reads.require(f.a,definition());assertThrows(TenantAccessException.class,()->f.deps.require(f.a,definition()));});
        verify(historical).requireUnchanged();verify(f.policies,never()).requireByVersion(any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void supersededOrDisabledToolDoesNotRequireCurrentMutableHeadForHistory()throws Exception {
        when(f.components.require(any(),any(),eq("TOOL"),anyString(),anyString())).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
        tx(()->reads.require(f.a,definition()));
        verifyNoInteractions(f.components,f.capabilities);
        verify(provenance,times(2)).require(eq(f.a),eq(id(1)),eq(List.of(b(4),b(5),b(6))),eq("AGENT_PUBLICATION"));
    }
    @Test void governanceHistoryWorksWithGovernanceOnlyReadGrantAndNeverRequiresEvaluationResults()throws Exception {
        var scope=new PermissionProtocol.Scope(id(100),id(1),"CONFIGURATION",id(500));
        var now=java.time.LocalDateTime.of(2026,10,18,20,0);
        var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("AGENT_PUBLICATION"),
                new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));
        var grant=new PermissionProtocol.GrantRow(id(501),id(101),id(1),"CONFIGURATION",id(500),"configuration.read",
                f.json.canonicalObject(condition),"ACTIVE",now.minusDays(1),null,id(120),"1.0.0",f.json.manifestSha256(),java.math.BigInteger.ONE);
        var permissions=new PermissionAuthority(f.json);
        doAnswer(call->{permissions.requireResource(scope,id(101),"local","configuration.read",call.getArgument(3),List.of(grant),now);return f.candidate;})
                .when(f.candidates).require(eq(f.a),eq(b(2)),eq(id(1)),anyString());
        tx(()->reads.require(f.a,definition()));
        verify(f.candidates).require(f.a,b(2),id(1),"AGENT_PUBLICATION");
        verify(f.candidates,never()).require(any(),any(),anyString(),eq("EVALUATION_RESULTS"));
        verify(f.policies).requireHistoricalByVersion(f.a,id(3),"a".repeat(64),id(1),"INTERNAL","AGENT_PUBLICATION");
        verify(provenance,times(2)).require(eq(f.a),eq(id(1)),anyList(),eq("AGENT_PUBLICATION"));
        assertThrows(TenantAccessException.class,()->permissions.requireResource(scope,id(101),"local","configuration.read","EVALUATION_RESULTS",List.of(grant),now));
    }
    @Test void currentSourceOrConfigurationReadRevocationStillBlocksHistory()throws Exception {
        when(provenance.require(any(),anyString(),anyList(),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        tx(()->assertThrows(TenantAccessException.class,()->reads.require(f.a,definition())));
    }
    @Test void historicalBindingMustStillMatchExactOriginalCandidateAndPolicyNodes()throws Exception {
        f.manifest.putArray("tool_versions");
        tx(()->assertThrows(TenantAccessException.class,()->reads.require(f.a,definition())));
        verifyNoInteractions(provenance);
    }
    @Test void changingReadRightsWithinReadIsRejected()throws Exception {
        when(provenance.require(eq(f.a),eq(id(1)),anyList(),eq("AGENT_PUBLICATION"))).thenReturn(new AgentPublicationDependencies.Checked(List.of(),f.json.object().put("grant","first")),new AgentPublicationDependencies.Checked(List.of(),f.json.object().put("grant","changed")));
        tx(()->assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->reads.require(f.a,definition())).code()));
    }
}
