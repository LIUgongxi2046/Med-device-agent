package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateOperationSchemaTest {
    @Test void templateFailuresUseThePublishedHttpStatus() {
        for (var code : List.of(ErrorCode.SCHEMA_INVALID, ErrorCode.SCHEMA_UNSUPPORTED, ErrorCode.SOURCE_NOT_READY,
                ErrorCode.RIGHTS_PENDING, ErrorCode.CONTEXT_LIMIT, ErrorCode.CONTENT_MISMATCH)) {
            assertEquals(422, TemplateController.errorStatus(code));
        }
        assertEquals(403, TemplateController.errorStatus(ErrorCode.ACCESS_DENIED));
        assertEquals(404, TemplateController.errorStatus(ErrorCode.OBJECT_UNAVAILABLE));
        for (var code : List.of(ErrorCode.CONTEXT_CHANGED, ErrorCode.SCOPE_STALE, ErrorCode.IDEMPOTENCY_CONFLICT,
                ErrorCode.REVISION_CONFLICT)) assertEquals(409, TemplateController.errorStatus(code));
        assertThrows(IllegalArgumentException.class, () -> TemplateController.errorStatus(ErrorCode.INVOCATION_UNKNOWN));
    }

    @Test void allResponseReferencesResolveAgainstTheOriginalCompleteDefinition() throws Exception {
        var json = new PermissionSchema(); var definitions = new TemplateDefinitionSchema(json, RegistrationDirectory.fromClasspath());
        var operations = new TemplateOperationSchema(json, definitions);
        assertTrue(operations.manifestSha256().matches("[a-f0-9]{64}"));
        assertNotEquals(definitions.manifestSha256(), operations.manifestSha256());
    }

    @Test void actualReferenceCatalogueCannotBeReadAsACreatedCustomerTemplate() throws Exception {
        var json = new PermissionSchema(); var definitions = new TemplateDefinitionSchema(json, RegistrationDirectory.fromClasspath());
        var operations = new TemplateOperationSchema(json, definitions);
        String actual;
        try (var input = getClass().getResourceAsStream("/registration-directory-2021-121.json")) {
            assertNotNull(input); actual = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThrows(SchemaRejection.class, () -> operations.receipt(actual));
    }

    @Test void noApprovalFieldsOrExecutableActionsCanBeAddedToTheDraftReceipt() throws Exception {
        var json = JsonMapper.builder().build();
        try (var input = getClass().getResourceAsStream("/template-operations.schema.json")) {
            assertNotNull(input); var defs = json.readTree(input).required("$defs");
            for (String name : List.of("Stored", "Created")) assertFalse(defs.required(name).required("additionalProperties").booleanValue());
            assertEquals("DRAFT", defs.required("Stored").required("properties").required("status").required("const").textValue());
            assertTrue(defs.required("Created").required("properties").required("allowed_actions").required("const").isEmpty());
            assertFalse(defs.required("Stored").required("properties").has("approved_by"));
        }
    }
}
