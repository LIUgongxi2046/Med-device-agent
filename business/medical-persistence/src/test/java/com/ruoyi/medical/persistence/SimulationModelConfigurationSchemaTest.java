package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationModelConfigurationSchemaTest {
    private final PermissionSchema json = new PermissionSchema();
    private final ModelConfigurationSchema schema = new ModelConfigurationSchema(json);
    SimulationModelConfigurationSchemaTest() throws Exception { }
    private ModelConfigurationProtocol.Save save(ObjectNode value) throws Exception {
        return schema.save(json.canonical(value).getBytes(StandardCharsets.UTF_8), true);
    }
    private ObjectNode request() { return SimulationModelConfigurationFixtures.request(json); }
    private ObjectNode definition(ObjectNode request) { return (ObjectNode) request.required("definition"); }

    @Test void explicitCredentialFreeVariantRoundTripsAndNeverUsesExternalSchema() throws Exception {
        var request = request(); var value = save(request);
        assertInstanceOf(ModelConfigurationProtocol.SimulationDefinition.class, value.definition());
        assertEquals(request, json.tree(value)); assertTrue(schema.dependencies(null, value.definition()).isEmpty());
        assertEquals(request.required("definition"), schema.publicDefinition(value.definition()));
        assertThrows(SchemaRejection.class, () -> schema.save(json.canonical(request).getBytes(StandardCharsets.UTF_8)));
        var fixed = new ModelConfigurationProtocol.Fixed("1.0.0", SimulationModelConfigurationFixtures.CASE, null,
                SimulationModelConfigurationFixtures.CASE, SimulationModelConfigurationFixtures.CASE, null, value.definition(), value.reason(),
                SimulationModelConfigurationFixtures.CASE, "1", SimulationModelConfigurationFixtures.CASE, "2026-10-18T00:00:00Z");
        assertEquals(fixed, schema.fixed(json.canonicalObject(fixed)));
    }
    @Test void rejectsProviderSecretsEndpointApprovalsAndOpenEndedMetadata() {
        for (String field : List.of("provider", "base_url", "region", "auth_ref", "credential_reference_id", "credential_revision",
                "price_basis", "currency", "data_policy", "connection_verification_id", "execution_approved")) {
            var value = request(); definition(value).put(field, "forbidden"); assertThrows(SchemaRejection.class, () -> save(value), field);
        }
        for (String path : List.of("", "/definition", "/definition/case_binding", "/definition/limits")) {
            var value = request(); ((ObjectNode)value.at(path)).put("unexpected", true); assertThrows(SchemaRejection.class, () -> save(value), path);
        }
    }
    @Test void everyExplicitSimulationBindingIsRequiredAndPinned() {
        var fields = new java.util.ArrayList<String>(); definition(request()).fieldNames().forEachRemaining(fields::add);
        for (String field : fields) { var value=request(); definition(value).remove(field); assertThrows(SchemaRejection.class, () -> save(value), field); }
        for (String field : List.of("execution_mode", "simulator_id", "simulator_version", "provenance_schema_ref", "provenance_schema_sha256",
                "provider_charge_amount", "charge_basis", "model_id", "model_version")) {
            var value=request(); definition(value).put(field, "wrong"); assertThrows(SchemaRejection.class, () -> save(value), field);
        }
        var promoted=request(); definition(promoted).put("production_eligible", true); assertThrows(SchemaRejection.class, () -> save(promoted));
        var verified=request(); ((ObjectNode)definition(verified).required("case_binding")).put("validation", "VERIFIED");
        assertThrows(SchemaRejection.class, () -> save(verified));
    }
    @Test void rejectsOverflowLimitsMalformedCasDuplicateFieldsAndTrailingBytes() throws Exception {
        var value=request(); ((ObjectNode)definition(value).required("limits")).put("max_concurrency", "18446744073709551616");
        assertThrows(SchemaRejection.class, () -> save(value));
        var cas=request(); cas.put("expected_revision", "1"); assertThrows(SchemaRejection.class, () -> save(cas));
        String body=json.canonical(request());
        assertThrows(Exception.class, () -> schema.save((body+" {}").getBytes(StandardCharsets.UTF_8),true));
        assertThrows(Exception.class, () -> schema.save(body.replace("\"execution_mode\":\"SIMULATION\"", "\"execution_mode\":\"SIMULATION\",\"execution_mode\":\"SIMULATION\"").getBytes(StandardCharsets.UTF_8),true));
    }
    @Test void sourceProvenanceBytesAndLegacyContractsRemainExactlyPinned() throws Exception {
        var root=Path.of(System.getProperty("medical.projectRoot"));
        byte[] source=Files.readAllBytes(root.resolve("model-gateway/src/medical_model_gateway/simulation-execution.schema.json"));
        try(var stream=getClass().getClassLoader().getResourceAsStream("model-simulation-provenance.schema.json")) {
            assertNotNull(stream); assertArrayEquals(source,stream.readAllBytes());
        }
        assertEquals(SimulationModelConfigurationFixtures.PROVENANCE_HASH,DigestUtils.sha256Hex(source));
        assertEquals("8f76403cbe5922ee1e2a5eb8658915962a79de2001345a0a56ea73f01efe5630",schema.manifestSha256());
        assertNotEquals(schema.manifestSha256(),schema.manifestSha256(true));
        for (var entry : java.util.Map.of("model-configuration.schema.json","0ff65a1d7c939fed8042372f93486da2f5790122736a5aed69ceb075bfffef1b",
                "configuration-discovery.schema.json","84bcf78bc35cad4f9b0e5e61e2a010719f8f0ec4f5bc2d6b24bc616d9f4fadfb",
                "permission-decisions.schema.json","fca270edf7d9697f340333c15992ecaa77fff4a8f1ac7bc51d2ffa8d56094843",
                "contracts/catalogue.json","dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915").entrySet()) {
            try(var stream=getClass().getClassLoader().getResourceAsStream(entry.getKey())) { assertNotNull(stream); assertEquals(entry.getValue(),DigestUtils.sha256Hex(stream.readAllBytes())); }
        }
    }
}
