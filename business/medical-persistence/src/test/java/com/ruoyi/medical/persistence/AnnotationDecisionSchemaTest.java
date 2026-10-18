package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationDecisionSchemaTest {
    private final PermissionSchema json = new PermissionSchema();
    private final AnnotationDecisionSchema schema = new AnnotationDecisionSchema(json);
    AnnotationDecisionSchemaTest() throws Exception { }
    private ObjectNode request() { return json.object().put("document_revision", AnnotationDecisionServiceTest.DOCUMENT_REVISION)
            .put("expected_revision", "1").put("expected_document_revision", "1").put("decision", "RESOLVE").put("reason", "原文意见 👩‍🔬\n\t 保留空格 "); }
    private byte[] bytes(Object value) throws Exception { return json.canonicalObject(value).getBytes(StandardCharsets.UTF_8); }

    @Test void preservesExactUnicodeWhitespaceAndDoesNotAcceptAuthorOrStatusFromCaller() throws Exception {
        var value = request(); assertEquals(value.required("reason").asText(), schema.create(bytes(value)).reason());
        for (String field : List.of("actor_id", "tenant_id", "annotation_id", "status", "created_at", "signature_id")) {
            var bad = value.deepCopy().put(field, "injected");
            assertThrows(SchemaRejection.class, () -> schema.create(bytes(bad)), field);
        }
    }
    @Test void rejectsEmptyWhitespaceNullMalformedUnicodeAndNonTextControls() throws Exception {
        for (String text : List.of("", " \t\n", "\u2003", "\u0000", "\u0001", "\ud800", "\udc00", "x\ud800x"))
            assertThrows(SchemaRejection.class, () -> AnnotationDecisionSchema.content(text));
        assertThrows(SchemaRejection.class, () -> AnnotationDecisionSchema.content(null));
        var bad = request().putNull("reason"); assertThrows(SchemaRejection.class, () -> schema.create(bytes(bad)));
        assertDoesNotThrow(() -> AnnotationDecisionSchema.content("\ud83d\ude00\n\t\r"));
    }
    @Test void strictJsonRejectsDuplicateAndTrailingInputAndMissingFields() throws Exception {
        String value = json.canonical(request());
        assertThrows(Exception.class, () -> schema.create((value + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> schema.create(value.replace("\"reason\":", "\"reason\":\"first\",\"reason\":").getBytes(StandardCharsets.UTF_8)));
        for (String field : List.of("reason", "document_revision", "expected_revision", "expected_document_revision", "decision")) {
            var bad = request(); bad.remove(field); assertThrows(SchemaRejection.class, () -> schema.create(bytes(bad)));
        }
    }
    @Test void positiveIncrementableRevisionAndLowercaseUuidAreRequired() throws Exception {
        for (String revision : List.of("0", "01", "-1", "18446744073709551615", "18446744073709551616"))
            assertThrows(SchemaRejection.class, () -> schema.create(bytes(request().put("expected_revision", revision))));
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(request().put("document_revision", "ABCDEF00-0000-4000-8000-000000000001"))));
        assertDoesNotThrow(() -> schema.create(bytes(request().put("expected_revision", "18446744073709551614"))));
        for(String revision:List.of("0","01","-1","18446744073709551615","18446744073709551616"))
            assertThrows(SchemaRejection.class,()->schema.create(bytes(request().put("expected_document_revision",revision))));
        for(String action:List.of("REANCHOR","CONFIRM","RETURN","SIGN"))
            assertThrows(SchemaRejection.class,()->schema.create(bytes(request().put("decision",action))));
    }
    @Test void textAndRawRequestCapacityAreExplicitAndNeverTruncate() throws Exception {
        String limit = "😀".repeat(16000);
        assertEquals(limit, schema.create(bytes(request().put("reason", limit))).reason());
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(request().put("reason", limit + "x"))));
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
                () -> schema.create(new byte[65537])).code());
    }
    @Test void eachAnnotationStatusHasAClosedVersionedRepresentation() {
        for (String status : List.of("OPEN", "RESOLVED", "RETURNED", "REANCHOR_REQUIRED"))
            assertDoesNotThrow(() -> schema.validate("Status", json.tree(status)));
        for (String status : List.of("APPROVED", "SIGNED", "PASS"))
            assertThrows(SchemaRejection.class, () -> schema.validate("Status", json.tree(status)));
    }
}
