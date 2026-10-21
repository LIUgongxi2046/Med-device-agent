package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FormalEvaluationNodeInputObjectSchemaTest extends NodeInputObjectFixture {
    FormalEvaluationNodeInputObjectSchemaTest() throws Exception { }

    @Test void duplicateUnknownTrailingOrMalformedUtf8RequestIsRejected() throws Exception {
        String raw = checked.original().body();
        for (String bad : List.of(raw + " {}", raw.replace("{", "{\"unexpected\":true,"),
                raw.replace("\"node_kind\":\"N01\"", "\"node_kind\":\"N01\",\"node_kind\":\"N01\""))) {
            assertThrows(SchemaRejection.class, () -> schema.request(bad.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(SchemaRejection.class, () -> schema.request(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }

    @Test void onlyDocumentN01MayRegisterAndNoProviderOrObjectFieldsAreAccepted() throws Exception {
        var raw = (ObjectNode) schema.read(checked.original().body());
        for (String node : List.of("N00", "N02", "N09")) {
            raw.put("node_kind", node);
            assertThrows(SchemaRejection.class, () -> schema.request(schema.canonical(raw).getBytes(StandardCharsets.UTF_8)));
        }
        raw.put("node_kind", "N01").put("artifact_id", id(999));
        assertThrows(SchemaRejection.class, () -> schema.request(schema.canonical(raw).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void stableProjectionIgnoresOnlyThreeExplicitReceiptArrays() throws Exception {
        var original = checked.evidence();
        var fresh = (ObjectNode) original.deepCopy();
        ((ObjectNode) fresh.required("tool_original")).putArray("receipt_ids").add(id(900));
        assertEquals(FormalEvaluationNodeInputObjectDependencies.stableEvidence(original),
                FormalEvaluationNodeInputObjectDependencies.stableEvidence(fresh));
        assertNotEquals(original, fresh);
        ((ObjectNode) fresh.required("tool_original").required("object")).put("object_version", "new-version");
        assertNotEquals(FormalEvaluationNodeInputObjectDependencies.stableEvidence(original),
                FormalEvaluationNodeInputObjectDependencies.stableEvidence(fresh));
    }

    @Test void contextRightsAndTimestampFieldsAreNeverRecursivelyRemoved() throws Exception {
        var original = (ObjectNode) checked.evidence();
        original.putObject("source_authority").put("checked_at", "2026-10-18").put("rights_revision", id(701));
        var fresh = original.deepCopy();
        ((ObjectNode) fresh.required("source_authority")).put("checked_at", "2026-10-19");
        assertNotEquals(FormalEvaluationNodeInputObjectDependencies.stableEvidence(original),
                FormalEvaluationNodeInputObjectDependencies.stableEvidence(fresh));
    }

    @Test void missingDuplicateOrNewReceiptShapesCannotBeNormalizedAway() throws Exception {
        var original = (ObjectNode) checked.evidence();
        ((ObjectNode) original.required("prompt_original")).putArray("receipt_ids");
        assertThrows(TenantAccessException.class, () -> FormalEvaluationNodeInputObjectDependencies.stableEvidence(original));
        var extra = (ObjectNode) checked.evidence();
        ((ObjectNode) extra.required("prompt_original")).put("new_unknown_read_profile", "ignored?");
        assertThrows(TenantAccessException.class, () -> FormalEvaluationNodeInputObjectDependencies.stableEvidence(extra));
    }

    @Test void requestHashRetainsOriginalBodyActorContextAndOperationNamespace() throws Exception {
        String raw = checked.original().body();
        String hash = schema.requestHash(tenant, actor, "1", raw);
        assertNotEquals(hash, schema.requestHash(tenant, id(99), "1", raw));
        assertNotEquals(hash, schema.requestHash(tenant, actor, "2", raw));
        assertNotEquals(hash, schema.requestHash(tenant, actor, "1", raw + "\n"));
    }
}
