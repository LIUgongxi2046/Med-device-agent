package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real classpath JSON schemas and selection semantics; no database or storage. */
class AnnotationAnchorSchemaTest {
    PermissionSchema json;
    AnnotationAnchorSchema schema;
    ObjectNode input;

    @BeforeEach void setup() throws Exception {
        json = new PermissionSchema();
        schema = new AnnotationAnchorSchema(json, new AnnotationThreadSchema(json));
        input = json.object().put("schema_version", "1.0.0")
            .put("document_revision", id(1)).put("expected_revision", "2")
            .put("expected_document_revision", "3").put("expected_anchor_id", id(2))
            .put("expected_anchor_revision", "1").put("reason", "Checked the new text")
            .put("human_confirmed", true);
        input.set("selection", json.tree(new CommonProtocol.Selection(id(1), List.of(
            new CommonProtocol.TextSelection("TEXT", id(3), "a".repeat(64), 0, 1, "b".repeat(64))))));
    }
    @Test void validCreateRoundTripsAndHashBindsContextSessionAndTarget() throws Exception {
        var request = schema.create(bytes(input));
        assertEquals(input, json.tree(request));
        String hash = schema.requestHash(id(4), request, "1", "session");
        assertEquals(hash, schema.requestHash(id(4), request, "1", "session"));
        assertNotEquals(hash, schema.requestHash(id(5), request, "1", "session"));
        assertNotEquals(hash, schema.requestHash(id(4), request, "2", "session"));
        assertNotEquals(hash, schema.requestHash(id(4), request, "1", "other"));
    }
    @Test void rejectsUnknownDuplicateTrailingAndMalformedUtf8() throws Exception {
        var unknown = input.deepCopy().put("actor_id", id(4));
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(unknown)));
        String valid = json.canonical(input);
        assertThrows(Exception.class, () -> schema.create((valid.substring(0, valid.length()-1) + ",\"reason\":\"duplicate\"}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> schema.create((valid + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> schema.create(new byte[]{(byte)0xc3, 0x28}));
    }
    @Test void requiresHumanConfirmationMatchingRevisionAndPositiveCas() {
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("human_confirmed", false))));
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("document_revision", id(9)))));
        for (String field : List.of("expected_revision", "expected_document_revision", "expected_anchor_revision")) {
            assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put(field, "0"))));
            assertThrows(RuntimeException.class, () -> schema.create(bytes(input.deepCopy().put(field, "99999999999999999999"))));
        }
    }
    @Test void enforcesRequestAndReasonLimits() {
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
            () -> schema.create(new byte[AnnotationAnchorSchema.MAXIMUM_REQUEST_BYTES + 1])).code());
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("reason", "x".repeat(16001)))));
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("reason", "   "))));
    }
    @Test void originalAnchorRequiresRevisionOneNullReasonAndAuthenticSelectionHash() throws Exception {
        var anchor = json.object().put("anchor_id", id(2)).put("document_revision", id(1))
            .put("selection_artifact_id", id(5)).put("selection_sha256", schema.hash(input.required("selection")))
            .put("annotation_revision", "1").put("actor_id", id(6)).put("created_at", "2026-10-18T00:00:00Z")
            .putNull("reason").putNull("previous_anchor_id");
        anchor.set("selection", input.required("selection"));
        schema.anchor(anchor);
        assertThrows(SchemaRejection.class, () -> schema.anchor(anchor.deepCopy().put("annotation_revision", "2")));
        assertThrows(SchemaRejection.class, () -> schema.anchor(anchor.deepCopy().put("reason", "invented")));
        assertThrows(SchemaRejection.class, () -> schema.anchor(anchor.deepCopy().put("selection_sha256", "c".repeat(64))));
        var next = anchor.deepCopy().put("annotation_revision", "3").put("previous_anchor_id", id(7)).put("reason", "Verified");
        schema.anchor(next);
        assertThrows(SchemaRejection.class, () -> schema.anchor(next.deepCopy().put("previous_anchor_id", id(2))));
    }
    byte[] bytes(ObjectNode value) throws Exception { return json.canonical(value).getBytes(StandardCharsets.UTF_8); }
    static String id(int n) { return AnnotationAnchorServiceTest.id(n); }
}
