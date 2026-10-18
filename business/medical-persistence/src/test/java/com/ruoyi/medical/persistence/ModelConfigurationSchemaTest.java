package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelConfigurationSchemaTest {
    private static final String HASH = "a".repeat(64);
    private static final String ID = "a8142a6f-c813-45e0-b68b-df9743474915";
    private static final String OTHER_ID = "281f9dfa-ae07-440a-852b-8d1cc1d0b4ca";
    private final PermissionSchema json;
    private final ModelConfigurationSchema schema;
    ModelConfigurationSchemaTest() throws Exception { json = new PermissionSchema(); schema = new ModelConfigurationSchema(json); }

    private ObjectNode request() throws Exception {
        return (ObjectNode) schema.read("""
                {"schema_version":"1.0.0","project_id":null,"config_id":null,"expected_revision":"0","based_on_revision":null,
                 "reason":"Engineering fixture for fixed draft persistence",
                 "definition":{"provider":"engineering-fixture","base_url":"https://provider.invalid/v1","region":"fixture-region",
                  "model_id":"fixture-model","model_version":"fixture-version","auth_ref":"secret://engineering/reference",
                  "credential_reference_id":"a8142a6f-c813-45e0-b68b-df9743474915","credential_revision":"fixture-v1",
                  "limits":{"context_window":"128000","max_output_tokens":"8000","max_concurrency":"2",
                   "requests_per_minute":"10","tokens_per_minute":"100000","deadline_seconds":60,"temperature_enabled":false,
                   "reasoning_efforts":["none"],"stop_limit":0},
                  "price_basis":{"version_id":"a8142a6f-c813-45e0-b68b-df9743474915","content_sha256":"%s"},"currency":"CNY",
                  "data_policy":{"allowed_classifications":["INTERNAL"],"allowed_regions":["fixture-region"],
                   "allow_training":false,"retention_days":"0","policy_basis":{"version_id":"281f9dfa-ae07-440a-852b-8d1cc1d0b4ca","content_sha256":"%s"}},
                  "request_parameter_schema_ref":"urn:medical:model-parameters:1.0.0","request_parameter_schema_sha256":"%s",
                  "model_protocol_revision":"1.0.0"}}
                """.formatted(HASH, HASH, HASH));
    }
    private ModelConfigurationProtocol.Save save(ObjectNode value) throws Exception { return schema.save(json.canonical(value).getBytes(StandardCharsets.UTF_8)); }
    private ObjectNode definition(ObjectNode request) { return (ObjectNode) request.required("definition"); }

    @Test void acceptsCompleteNewAndAppendDraftsWithExplicitCas() throws Exception {
        var value = request(); var saved = save(value); assertNull(saved.config_id());
        assertEquals("0", saved.expected_revision()); assertEquals("8000", saved.definition().limits().max_output_tokens());
        value.put("config_id", UUID.randomUUID().toString()); value.put("expected_revision", "1");
        value.set("based_on_revision", json.tree(new PermissionProtocol.Basis(UUID.randomUUID().toString(), HASH)));
        assertEquals("1", save(value).expected_revision());
    }
    @Test void everyRequiredFieldAndEveryNestedDefinitionIsClosed() throws Exception {
        var full = request(); var fields = new java.util.ArrayList<String>(); full.fieldNames().forEachRemaining(fields::add);
        for (var field : fields) { var value = full.deepCopy(); value.remove(field); assertThrows(SchemaRejection.class, () -> save(value), field); }
        for (String path : List.of("", "/definition", "/definition/limits", "/definition/data_policy", "/definition/price_basis")) {
            var value = request(); ((ObjectNode) value.at(path)).put("unexpected", "value");
            assertThrows(SchemaRejection.class, () -> save(value), path);
        }
        var defs = new java.util.ArrayList<String>(); definition(full).fieldNames().forEachRemaining(defs::add);
        for (String field : defs) { var value = request(); definition(value).remove(field); assertThrows(SchemaRejection.class, () -> save(value), field); }
    }
    @Test void rejectsDuplicateFieldsTrailingContentAndOversizedBodies() throws Exception {
        var body = json.canonical(request());
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.save((body + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.save(
                body.replace("\"schema_version\":\"1.0.0\"", "\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"").getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.save(new byte[65537]));
    }
    @Test void rejectsIncompleteOrOverflowingCasState() throws Exception {
        for (String revision : List.of("1", "01", "-1", "18446744073709551616")) {
            var value = request(); value.put("expected_revision", revision); assertThrows(SchemaRejection.class, () -> save(value));
        }
        var value = request(); value.put("config_id", ID); assertThrows(SchemaRejection.class, () -> save(value));
        value.put("expected_revision", "1"); assertThrows(SchemaRejection.class, () -> save(value));
        value.set("based_on_revision", json.tree(new PermissionProtocol.Basis(OTHER_ID, HASH))); save(value);
        value.put("expected_revision", "18446744073709551615"); assertThrows(SchemaRejection.class, () -> save(value));
    }
    @Test void rejectsEndpointCredentialsQueriesFragmentsAndAmbiguousPaths() throws Exception {
        for (String endpoint : List.of("http://provider.invalid/v1", "https://secret@provider.invalid/v1", "https://provider.invalid/v1?api_key=secret",
                "https://provider.invalid/v1#fragment", "https://provider.invalid/a/../v1", "https://provider.invalid:0/v1", "https://provider.invalid:65536/v1", "file:///tmp/model")) {
            var value = request(); definition(value).put("base_url", endpoint); assertThrows(SchemaRejection.class, () -> save(value), endpoint);
        }
    }
    @Test void limitsMustFitStorageAndProviderContract() throws Exception {
        for (String field : List.of("context_window", "max_output_tokens", "max_concurrency", "requests_per_minute", "tokens_per_minute")) {
            for (String count : List.of("0", "-1", "01", "18446744073709551616")) {
                var value = request(); ((ObjectNode) value.at("/definition/limits")).put(field, count);
                assertThrows(SchemaRejection.class, () -> save(value), field + ":" + count);
            }
        }
        var value = request(); ((ObjectNode) value.at("/definition/limits")).put("max_output_tokens", "128001");
        assertThrows(SchemaRejection.class, () -> save(value));
        for (int deadline : List.of(0, 301)) { var invalid = request(); ((ObjectNode) invalid.at("/definition/limits")).put("deadline_seconds", deadline); assertThrows(SchemaRejection.class, () -> save(invalid)); }
    }
    @Test void rejectsPolicyRegionMismatchAndConflictingDependencyHashes() throws Exception {
        var region = request(); definition(region).put("region", "unlisted"); assertThrows(SchemaRejection.class, () -> save(region));
        var value = request(); ((ObjectNode) value.at("/definition/data_policy/policy_basis")).put("version_id", ID).put("content_sha256", "b".repeat(64));
        var conflict = value; assertThrows(SchemaRejection.class, () -> save(conflict));
        var excessive = request(); ((ObjectNode) excessive.at("/definition/data_policy")).put("retention_days", "18446744073709551616");
        assertThrows(SchemaRejection.class, () -> save(excessive));
    }
    @Test void typedInputAndPublicViewsDoNotExposeCredentialLocationOrVersion() throws Exception {
        var input = save(request()); var publicValue = schema.publicDefinition(input.definition());
        assertFalse(publicValue.has("auth_ref")); assertFalse(publicValue.has("credential_revision"));
        assertEquals(ID, publicValue.required("credential_reference_id").textValue());
        assertFalse(input.toString().contains("secret://")); assertFalse(input.definition().toString().contains("fixture-v1"));
        schema.verify("PublicDefinition", publicValue);
        ((ObjectNode) publicValue).put("auth_ref", input.definition().auth_ref());
        assertThrows(SchemaRejection.class, () -> schema.verify("PublicDefinition", publicValue));
    }
    @Test void fixedDefinitionHashCoversCredentialVersionPolicyAndParameters() throws Exception {
        var a = save(request()); var b = request(); definition(b).put("credential_revision", "fixture-v2");
        assertNotEquals(schema.hash(a, "1", "fixture-session"), schema.hash(save(b), "1", "fixture-session"));
        assertNotEquals(schema.hash(a, "1", "fixture-session"), schema.hash(a, "2", "fixture-session"));
        assertNotEquals(schema.hash(a, "1", "fixture-session"), schema.hash(a, "1", "other-session"));
        String manifest = json.manifestSha256(); assertNotEquals(manifest, schema.manifestSha256());
        new ModelConfigurationSchema(json); assertEquals(manifest, json.manifestSha256());
    }
    @Test void fixedContentRoundTripsWithoutDefaultsOrSecretBearingStringOutput() throws Exception {
        var request = save(request());
        var fixed = new ModelConfigurationProtocol.Fixed("1.0.0", ID, null, OTHER_ID, UUID.randomUUID().toString(), null,
                request.definition(), request.reason(), UUID.randomUUID().toString(), "1", ID, "2026-10-16T00:00:00Z");
        assertEquals(fixed, schema.fixed(json.canonicalObject(fixed)));
        assertFalse(fixed.toString().contains("secret://"));
        var invalid = (ObjectNode) json.tree(fixed); invalid.put("status", "PUBLISHED");
        assertThrows(SchemaRejection.class, () -> schema.fixed(json.canonical(invalid)));
    }
    @Test void mapperSqlParsesAndAllStatementsAreTenantAndSubjectBound() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(
                new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var values = new java.util.HashMap<String,Object>(); values.put("userId", ID); values.put("definition", save(request()).definition());
        for (String key : List.of("configId", "versionId", "oldVersion", "upstreamId", "key", "auditId", "eventId", "requestId")) values.put(key, UUID.randomUUID().toString());
        var identity = new TenantScope.Identity(OTHER_ID, ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
        try (var ignored = TenantScope.open(identity)) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String name : List.of("head", "stored", "parents", "insertHead", "insertVersion", "insertModel", "insertDefinition", "insertLineage",
                    "initializeHead", "advanceHead", "currentOperation", "saveOperation", "advanceGovernance", "audit", "event")) {
                String statement = "com.ruoyi.medical.persistence.mapper.ModelConfigurationMapper." + name;
                TenantScope.requireSubjectParameter(statement, values);
                assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                        () -> TenantScope.requireSubjectParameter(statement, Map.of("userId", OTHER_ID))).code());
                String sql = configuration.getMappedStatement(statement).getBoundSql(values).getSql();
                assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql), name);
                String scoped = interceptor.parserSingle(sql, null);
                assertTrue(scoped.contains(OTHER_ID), name); assertTrue(scoped.toLowerCase().contains("tenant_id"), name);
                if (name.equals("advanceHead")) { assertTrue(sql.contains("current_version=#{oldVersion}") || sql.contains("current_version=?")); assertTrue(sql.contains("status='DRAFT'")); }
            }
        }
    }
}
