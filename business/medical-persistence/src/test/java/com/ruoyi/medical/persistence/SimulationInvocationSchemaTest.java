package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationInvocationSchemaTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    SimulationInvocationSchemaTest()throws Exception{}
    static byte[] resource(String name)throws Exception { try(var in=SimulationInvocationSchemaTest.class.getResourceAsStream("/simulation-ledger/"+name+".json")) { return java.util.Objects.requireNonNull(in).readAllBytes(); } }
    ObjectNode request()throws Exception { return (ObjectNode)schema.request(resource("admission")); }
    @Test void realPythonCanonicalPayloadAndReceiptAreCompatible()throws Exception {
        var request=request(); var payload=schema.receipt(request,resource("result"),resource("provenance"));
        assertEquals("RECORDED_UNSETTLED",payload.path("status").asText()); assertTrue(payload.path("result").path("usage").isNull());
        assertEquals(resource("admission").length,schema.canonical(request).getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void measuresUtf8BytesRatherThanCharsOrTokens()throws Exception {
        var request=request(); String input=schema.canonical(request.required("exact_input"));
        assertTrue(input.getBytes(StandardCharsets.UTF_8).length>input.length());
        ((ObjectNode)request.path("binding").path("exact_input")).put("units",input.length());
        assertThrows(TenantAccessException.class,()->schema.check(request));
    }
    @Test void actualInputCannotBeReplacedByClaimedDigest()throws Exception {
        var request=request(); ((ObjectNode)request.path("exact_input").path("messages").get(0)).put("content","changed");
        assertThrows(TenantAccessException.class,()->schema.check(request));
    }
    @Test void parametersFixtureAndSettingsMustMatchActualBytes()throws Exception {
        for(String field:List.of("request_parameters","settings","simulation_case")) {
            var request=request(); ((ObjectNode)request.path(field)).put("unapproved",true);
            assertThrows(RuntimeException.class,()->schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test void allSharedIdentitiesAreBoundToAuthority()throws Exception {
        for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","context_manifest_id","model_config_revision")) {
            var request=request(); ((ObjectNode)request.path("binding").path("identity")).put(field,"00000000-0000-0000-0000-000000000099");
            assertThrows(TenantAccessException.class,()->schema.check(request));
        }
    }
    @Test void counterCannotBeAnArbitraryMatchingClaim()throws Exception {
        var request=request(); ((ObjectNode)request.path("authority_proof").path("counter")).put("implementation_sha256","f".repeat(64));
        ((ObjectNode)request.path("binding").path("exact_input").path("counter")).put("implementation_sha256","f".repeat(64));
        assertThrows(TenantAccessException.class,()->schema.check(request));
    }
    @Test void capsAreFiniteAndNoOverflowIsAllowed() {
        assertEquals(10,SimulationInvocationSchema.sum(4,6));
        assertThrows(SchemaRejection.class,()->SimulationInvocationSchema.sum(SimulationInvocationSchema.MAX,1));
        assertThrows(SchemaRejection.class,()->SimulationInvocationSchema.sum(-1,1));
    }
    @Test void duplicateKeysAndTrailingPayloadAreRejected()throws Exception {
        String value=new String(resource("admission"),StandardCharsets.UTF_8);
        assertThrows(Exception.class,()->schema.request((value+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->schema.request(value.replaceFirst("\\{","{\"schema_version\":\"1.0.0\",").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void noBooleanAuthorityBypassOrEvaluationCrudPermission()throws Exception {
        var request=request(); ((ObjectNode)request.path("authority_proof")).put("authorized",true);
        assertThrows(SchemaRejection.class,()->schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8)));
        var wrong=request(); ((ObjectNode)wrong.path("authority_proof")).put("action","evaluation.create");
        assertThrows(SchemaRejection.class,()->schema.request(schema.canonical(wrong).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void receiptMustRemainCanonicalAndCannotChangeFixedResult()throws Exception {
        var request=request(); assertThrows(TenantAccessException.class,()->schema.receipt(request,(new String(resource("result"),StandardCharsets.UTF_8)+"\n").getBytes(StandardCharsets.UTF_8),resource("provenance")));
        var result=(ObjectNode)schema.parse(resource("result")); ((ObjectNode)result.path("blocks").get(0)).put("text","replacement");
        assertThrows(TenantAccessException.class,()->schema.receipt(request,schema.canonical(result).getBytes(StandardCharsets.UTF_8),resource("provenance")));
    }
    @Test void sourceReceiptCannotInventProviderUsageOrBackendProvenance()throws Exception {
        var request=request(); var provenance=(ObjectNode)schema.parse(resource("provenance")); provenance.put("execution_scope","BACKEND_ARTIFACT");
        assertThrows(SchemaRejection.class,()->schema.receipt(request,resource("result"),schema.canonical(provenance).getBytes(StandardCharsets.UTF_8)));
        provenance.put("execution_scope","LOCAL_COMPONENT").put("usage_origin","SIMULATION_FIXTURE");
        assertThrows(TenantAccessException.class,()->schema.receipt(request,resource("result"),schema.canonical(provenance).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void noRolePublicationOrProductionAuthorityIsGrantedBySchema()throws Exception {
        var request=request(); ((ObjectNode)request.path("authority_proof")).put("production_eligible",true);
        assertThrows(SchemaRejection.class,()->schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void storedInvocationCannotDowngradeSourceContextOrConfigurationClassification() {
        assertDoesNotThrow(()->SimulationInvocationAuthority.requireClassification("PERSONAL_HEALTH","PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH"));
        assertThrows(TenantAccessException.class,()->SimulationInvocationAuthority.requireClassification("INTERNAL","PERSONAL_HEALTH"));
        assertThrows(TenantAccessException.class,()->SimulationInvocationAuthority.requireClassification("INTERNAL","UNKNOWN"));
    }
}
