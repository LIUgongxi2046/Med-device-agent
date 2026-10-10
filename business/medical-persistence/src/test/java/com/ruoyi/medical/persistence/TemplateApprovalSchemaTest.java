package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateApprovalSchemaTest {
    @Test void allApprovalValidatorsInitializeFromTheActualPublishedTemplateDefinition() throws Exception {
        var json = new PermissionSchema();
        var definitions = new TemplateDefinitionSchema(json, RegistrationDirectory.fromClasspath());
        var approval = new TemplateApprovalSchema(json, definitions);
        assertTrue(approval.manifestSha256().matches("[0-9a-f]{64}"));
        assertEquals(approval.manifestSha256(), new TemplateApprovalSchema(json, definitions).manifestSha256());
    }
    @Test void actualReferenceDirectoryCannotBeSubmittedAsAnApprovalDecision() throws Exception {
        var json = new PermissionSchema();
        var approval = new TemplateApprovalSchema(json, new TemplateDefinitionSchema(json, RegistrationDirectory.fromClasspath()));
        try (var input = getClass().getResourceAsStream("/registration-directory-2021-121.json")) {
            assertNotNull(input); byte[] original = input.readAllBytes();
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> approval.decide(original)).code());
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class,
                    () -> approval.receipt(new String(original, java.nio.charset.StandardCharsets.UTF_8))).code());
        }
    }
    @Test void decisionBaselineAndReceiptAreClosedAndCannotAcceptPrivateObjectAddresses() throws Exception {
        try (var input = getClass().getResourceAsStream("/template-approval.schema.json")) {
            assertNotNull(input); var definitions = JsonMapper.builder().build().readTree(input).required("$defs");
            for (String name : List.of("Decide", "Baseline", "Receipt", "Context", "Basis", "ReviewTemplate", "Preview")) {
                var schema = definitions.required(name); assertFalse(schema.required("additionalProperties").booleanValue());
                assertFalse(schema.required("properties").has("object_key")); assertFalse(schema.required("properties").has("object_version"));
            }
            assertTrue(definitions.required("Decide").required("required").toString().contains("baseline_manifest"));
        }
    }
    @Test void allDeclaredActionsHaveExplicitTransitionsAndDisabledVersionsRejectFurtherActions() throws Exception {
        try (var input = getClass().getResourceAsStream("/template-approval.schema.json")) {
            assertNotNull(input); var definitions = JsonMapper.builder().build().readTree(input).required("$defs");
            assertEquals(List.of("APPROVE", "RETURN", "DISABLE"), JsonMapper.builder().build().convertValue(
                    definitions.required("Decide").required("properties").required("decision").required("enum"), List.class));
            assertEquals("APPROVED", TemplateApprovalService.nextStatus("DRAFT", "APPROVE"));
            assertEquals("APPROVED", TemplateApprovalService.nextStatus("RETURNED", "APPROVE"));
            assertEquals("RETURNED", TemplateApprovalService.nextStatus("DRAFT", "RETURN"));
            assertEquals("RETURNED", TemplateApprovalService.nextStatus("APPROVED", "RETURN"));
            for (String status : List.of("DRAFT", "RETURNED", "APPROVED")) assertEquals("DISABLED", TemplateApprovalService.nextStatus(status, "DISABLE"));
            for (var action : definitions.required("Decide").required("properties").required("decision").required("enum"))
                assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class,
                        () -> TemplateApprovalService.nextStatus("DISABLED", action.textValue())).code());
        }
    }
}
