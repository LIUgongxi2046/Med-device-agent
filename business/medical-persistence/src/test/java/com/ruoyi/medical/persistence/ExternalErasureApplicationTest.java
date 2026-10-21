package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.erasure.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Normal controller/service/adapter/journal path with explicit synthetic authority fixture.
 * Authenticated identity/source-governance seam is mocked; no database or real source purge proof. */
class ExternalErasureApplicationTest {
    @TempDir Path directory;
    @Test void normalControllerConsumesSyntheticExactCopyAndOriginalRecovery()throws Exception {
        Instant now=Instant.now();Scope scope=new Scope("sim-tenant","sim-source","sim-offline",Kind.OFFLINE,"r1");
        Copy copy=new Copy(scope,"sim-copy","v1","a".repeat(64),null,Hold.CLEAR,null);
        Approval approval=new Approval("sim-approval",scope,"sim-frozen-inventory","sim-declarer","sim-reviewer",now.plusSeconds(3600),Proof.SIMULATED);
        var api=new SyntheticErasureService(scope,List.of(copy),now,1);
        var registry=new ExternalErasureRegistry(List.of(new ExternalErasureRegistry.Binding(
            new ExternalErasureRegistry.Configuration(scope.scopeId(),Kind.OFFLINE,"r1",Proof.SIMULATED,"synthetic-only",null,null),api,new ExternalErasureFileJournal(directory))));
        var authority=mock(SourceCopyAuthority.class);var identities=mock(ApplicationSessionIdentity.class);
        var identity=mock(ApplicationSessionIdentity.Verified.class);
        when(identities.requireCurrent(any(),any())).thenReturn(identity);
        when(authority.externalOperation(eq(identity),eq("1"),eq("sim-source"),eq("sim-approval"),eq("sim-offline"),any()))
            .thenAnswer(invocation->((ActionWithApproval<?>)invocation.getArgument(5)).run(approval));
        var service=new ExternalErasureService(authority,registry);var controller=new ExternalErasureController(service,identities);
        var request=new MockHttpServletRequest();request.addHeader("X-Schema-Version",VERSION);request.addHeader("X-Context-Revision","1");request.addHeader("Idempotency-Key","synthetic-erasure-key");
        byte[] body=registry.mapper.writeValueAsBytes(new ExternalErasureService.EraseRequest(VERSION,"sim-approval","sim-offline",copy));
        var receipt=controller.erase("sim-source",body,request,new MockHttpServletResponse(),null);
        assertEquals("SIMULATED",receipt.path("proof").asText());assertEquals("ACKNOWLEDGED",receipt.path("outcome").asText());
        var recovered=controller.recovery("sim-source","sim-approval","sim-offline","synthetic-erasure-key",request,new MockHttpServletResponse(),null);
        assertEquals(receipt,recovered);assertEquals(1,api.deleteCalls());
        verify(authority,atLeast(4)).externalOperation(eq(identity),eq("1"),eq("sim-source"),eq("sim-approval"),eq("sim-offline"),any());
    }
    @Test void registeredSyntheticInventoryCannotApproveRealSourceScope() {
        Instant now=Instant.now();Scope scope=new Scope("sim-tenant","sim-source","sim-offline",Kind.OFFLINE,"r1");
        var api=new SyntheticErasureService(scope,List.of(),now,1);
        var registry=new ExternalErasureRegistry(List.of(new ExternalErasureRegistry.Binding(
            new ExternalErasureRegistry.Configuration(scope.scopeId(),Kind.OFFLINE,"r1",Proof.SIMULATED,"synthetic-only",null,null),api,new ExternalErasureFileJournal(directory))));
        var scopes=registry.mapper.createArrayNode();scopes.addObject().put("scope_id","sim-offline").put("kind","OFFLINE");
        assertThrows(IllegalStateException.class,()->registry.observe(scopes,"sim-tenant","sim-source","sim-a","sim-d","sim-r",now.plusSeconds(300),null,Set.of(),false));
    }
    @Test void noRegistryCannotHideKnownUncertainOrWithdrawnProviderSend()throws Exception {
        var registry=new ExternalErasureRegistry(List.of());
        assertThrows(IllegalStateException.class,()->registry.observe(registry.mapper.createArrayNode(),"tenant","source","a","d","r",Instant.now().plusSeconds(300),null,Set.of("known-UNKNOWN-operation"),true));
    }
    @Test void contractRejectsInventedFieldsAndSimulationPromotion()throws Exception {
        var mapper=JsonMapper.builder().build();var schema=mapper.readTree(getClass().getClassLoader().getResourceAsStream("external-erasure-v1.schema.json"));
        var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        var valid=mapper.createObjectNode().put("schemaVersion",VERSION).put("authorityId","a").put("scopeId","s");
        valid.putObject("copy").putObject("scope");valid.put("complete",true);
        var root=schema.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)root).put("$ref","#/$defs/EraseRequest");
        assertFalse(factory.getSchema(root).validate(valid).isEmpty());
        var absence=mapper.createObjectNode().put("schemaVersion",VERSION).put("proof","SIMULATED").put("snapshot","snapshot").put("observedAt",Instant.now().toString()).put("productionPurgeAllowed",true);
        absence.putObject("scope").put("tenant","t").put("source","s").put("scopeId","id").put("kind","OFFLINE").put("revision","r");
        ((com.fasterxml.jackson.databind.node.ObjectNode)root).put("$ref","#/$defs/Absence");assertFalse(factory.getSchema(root).validate(absence).isEmpty());
    }
}
