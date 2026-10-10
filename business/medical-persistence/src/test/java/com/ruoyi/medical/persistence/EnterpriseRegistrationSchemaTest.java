package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnterpriseRegistrationSchemaTest {
    @Test void emptyOwnedQueueProducesValidEnvelope() throws Exception {
        var schema = new EnterpriseRegistrationSchema();
        var page = schema.object(); page.put("schema_version", "1.0.0"); page.putArray("items"); page.putNull("next_cursor");
        var result = schema.envelope("Page", page, java.util.UUID.randomUUID().toString(), null);
        assertEquals(page, result.required("data"));
        assertTrue(result.required("resource_revision").isNull());
    }
    @Test void missingCorporateDataIsRejected() throws Exception {
        var schema = new EnterpriseRegistrationSchema();
        assertThrows(SchemaRejection.class, () -> schema.request("{}".getBytes(StandardCharsets.UTF_8), java.util.UUID.randomUUID().toString()));
        assertThrows(SchemaRejection.class, () -> schema.request("null".getBytes(StandardCharsets.UTF_8), java.util.UUID.randomUUID().toString()));
    }
    @Test void missingKeyAndOversizedRequestAreRejected() throws Exception {
        var schema = new EnterpriseRegistrationSchema();
        assertThrows(SchemaRejection.class, () -> schema.request("{}".getBytes(StandardCharsets.UTF_8), null));
        assertThrows(SchemaRejection.class, () -> schema.request(new byte[32769], java.util.UUID.randomUUID().toString()));
    }
    @Test void duplicateJsonFieldsAreRejectedByNativeParser() throws Exception {
        var schema = new EnterpriseRegistrationSchema();
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request(
                "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8), java.util.UUID.randomUUID().toString()));
    }
    @Test void schemaManifestIsBoundToActualResources() throws Exception {
        assertTrue(new EnterpriseRegistrationSchema().manifest().matches("[0-9a-f]{64}"));
    }
    @Test void trailingJsonValueIsRejectedByNativeParser() throws Exception {
        var schema = new EnterpriseRegistrationSchema();
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request(
                "{} {}".getBytes(StandardCharsets.UTF_8), java.util.UUID.randomUUID().toString()));
    }
}
