package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real classpath JSON schemas and reconciliation semantics; no database or storage. */
class AnnotationAnchorReconciliationSchemaTest {
    PermissionSchema json;
    AnnotationAnchorReconciliationSchema schema;
    ObjectNode input;

    @BeforeEach void setup() throws Exception {
        json = new PermissionSchema();
        schema = new AnnotationAnchorReconciliationSchema(json, new AnnotationThreadSchema(json));
        input = json.object().put("schema_version", "1.0.0")
            .put("document_revision", id(1)).put("expected_revision", "2")
            .put("expected_document_revision", "3").put("expected_anchor_id", id(2))
            .put("expected_anchor_revision", "1").put("reason", "Checked the new text")
            .put("human_confirmed", true);
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
    @Test void requiresHumanConfirmationNoReplacementSelectionAndPositiveCas() {
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("human_confirmed", false))));
        var selection = input.deepCopy(); selection.set("selection", json.object());
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(selection)));
        for (String field : List.of("expected_revision", "expected_document_revision", "expected_anchor_revision")) {
            assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put(field, "0"))));
            assertThrows(RuntimeException.class, () -> schema.create(bytes(input.deepCopy().put(field, "99999999999999999999"))));
        }
    }
    @Test void enforcesRequestAndReasonLimits() {
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
            () -> schema.create(new byte[AnnotationAnchorReconciliationSchema.MAXIMUM_REQUEST_BYTES + 1])).code());
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("reason", "x".repeat(16001)))));
        assertThrows(SchemaRejection.class, () -> schema.create(bytes(input.deepCopy().put("reason", "   "))));
    }
    @Test void receiptAuthenticatesStatusCasOriginAndUnchangedPointer() throws Exception {
        var receipt = validReceipt();
        var encoded = signedEnvelope(receipt);
        assertEquals(encoded, schema.receipt(json.canonical(encoded)));
        assertDoesNotThrow(() -> schema.receipt(json.canonical(signedEnvelope(receipt.deepCopy().put("previous_status", "RESOLVED")))));
        for (var item : java.util.Map.of("previous_status", "RETURNED", "status", "OPEN", "anchor_revision", "2",
                "anchor_id", id(99), "revision", "4", "document_head_revision", "5", "reason", "   ",
                "origin_anchor_sha256", "invalid").entrySet()) {
            var changed = receipt.deepCopy().put(item.getKey(), item.getValue());
            assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(signedEnvelope(changed))), item.getKey());
        }
        var tampered = encoded.deepCopy(); ((ObjectNode) tampered.required("data")).put("reason", "Changed after signing");
        assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(tampered)));
        var wrongEnvelopeRevision = ((ObjectNode) encoded.deepCopy()).put("resource_revision", "9");
        assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(wrongEnvelopeRevision)));
        var injected = receipt.deepCopy().put("selection_artifact_id", id(8));
        assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(signedEnvelope(injected))));
        var missingOrigin = receipt.deepCopy(); missingOrigin.remove("origin_anchor_sha256");
        assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(signedEnvelope(missingOrigin))));
    }
    @Test void everyReceiptRevisionAndWatermarkMustFitUnsigned64Bits() throws Exception {
        for (String field : List.of("previous_revision", "revision", "previous_document_revision",
                "document_head_revision", "anchor_revision", "issue_watermark", "evidence_watermark")) {
            var overflow = validReceipt().put(field, "18446744073709551616");
            assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(signedEnvelope(overflow))), field);
        }
        var valid = validReceipt().put("issue_watermark", "18446744073709551615")
            .put("evidence_watermark", "18446744073709551615");
        assertDoesNotThrow(() -> schema.receipt(json.canonical(signedEnvelope(valid))));
        var envelope = (ObjectNode) signedEnvelope(validReceipt());
        envelope.put("resource_revision", "18446744073709551616");
        assertThrows(SchemaRejection.class, () -> schema.receipt(json.canonical(envelope)));
    }
    @Test void requestHashBindsEveryCasAndReasonAndManifestIsIndependent() throws Exception {
        var original = schema.create(bytes(input));
        String hash = schema.requestHash(id(4), original, "1", "session");
        for (var item : java.util.Map.of("document_revision", id(9), "expected_revision", "4",
                "expected_document_revision", "4", "expected_anchor_id", id(9),
                "expected_anchor_revision", "2", "reason", "Different reason").entrySet())
            assertNotEquals(hash, schema.requestHash(id(4), schema.create(bytes(input.deepCopy().put(item.getKey(), item.getValue()))), "1", "session"));
        assertNotEquals(new AnnotationAnchorSchema(json, new AnnotationThreadSchema(json)).manifestSha256(), schema.manifestSha256());
        try (var stream = getClass().getResourceAsStream("/annotation-anchor-reconciliations.schema.json")) {
            assertNotNull(stream);
            assertEquals("urn:medical:annotation-anchor-reconciliations:1.0.0",
                schema.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).required("$id").asText());
        }
    }
    ObjectNode validReceipt() {
        var receipt = json.object().put("schema_version", "1.0.0").put("document_id", id(10))
            .put("document_revision", id(1)).put("annotation_id", id(2)).put("operation_id", id(3))
            .put("previous_revision", "2").put("revision", "3").put("previous_status", "OPEN")
            .put("status", "REANCHOR_REQUIRED").put("previous_document_revision", "3")
            .put("document_head_revision", "4").put("anchor_id", id(2)).put("anchor_revision", "1")
            .put("issue_watermark", "1").put("evidence_watermark", "1").put("transition_id", id(4))
            .put("actor_id", id(5)).put("created_at", "2026-10-18T00:00:00Z")
            .put("reason", "Verified missing selection").put("origin_anchor_sha256", "a".repeat(64));
        receipt.putArray("invalidated_review_ids").add(id(6)); return receipt;
    }
    com.fasterxml.jackson.databind.JsonNode signedEnvelope(ObjectNode receipt) throws Exception {
        receipt.remove("receipt_sha256"); receipt.put("receipt_sha256", schema.hash(receipt));
        var envelope = json.object().put("schema_version", "1.0.0").put("request_id", id(7))
            .put("resource_revision", receipt.required("revision").asText());
        envelope.set("data", receipt); return envelope;
    }
    byte[] bytes(ObjectNode value) throws Exception { return json.canonical(value).getBytes(StandardCharsets.UTF_8); }
    static String id(int n) { return AnnotationAnchorServiceTest.id(n); }
}
