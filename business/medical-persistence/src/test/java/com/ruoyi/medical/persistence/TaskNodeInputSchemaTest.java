package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskNodeInputSchemaTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String HASH = "a".repeat(64);
    private static TaskNodeInputProtocol.Input input() {
        var basis = new PermissionProtocol.Basis(ID, HASH);
        return new TaskNodeInputProtocol.Input("1.0.0", ID, ID, ID, ID, ID, ID, HASH, "N02", ID,
                "question-1", "What evidence supports the fixed question?", ID, ID, HASH, ID, HASH,
                HASH, new TaskNodeInputProtocol.ReplayArtifact(ID, HASH, "physical-version"), HASH,
                basis, basis, List.of(new TaskNodeInputProtocol.SourceSelection(ID, ID, ID, ID, ID, ID, "TASK_EXECUTION")), List.of());
    }
    @Test void roundTripHasRealCanonicalDigestAndClosedTypedSources() throws Exception {
        var schema = new TaskNodeInputSchema();
        var value = input();
        assertEquals(value, schema.read(schema.bytes(value)));
        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(schema.bytes(value)), schema.hash(schema.tree(value)));
        assertThrows(UnsupportedOperationException.class, () -> value.fixedSources().clear());
        assertTrue(schema.manifest().matches("[a-f0-9]{64}"));
    }
    @Test void rejectsCallerAuthorityClaimsUnknownFieldsAndDuplicateKeys() throws Exception {
        var schema = new TaskNodeInputSchema();
        var value = (ObjectNode) schema.tree(input());
        value.put("approved", true);
        assertThrows(Exception.class, () -> schema.read(schema.canonical(value).getBytes(StandardCharsets.UTF_8)));
        String raw = schema.canonical(schema.tree(input()));
        String duplicate = "{\"node_kind\":\"N02\"," + raw.substring(1);
        assertThrows(Exception.class, () -> schema.read(duplicate.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> schema.read((raw + " {}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsWrongNodeMissingPinsAndNestedExtraFields() throws Exception {
        var schema = new TaskNodeInputSchema();
        var wrong = (ObjectNode) schema.tree(input());
        wrong.put("node_kind", "N01");
        assertThrows(Exception.class, () -> schema.read(schema.canonical(wrong).getBytes(StandardCharsets.UTF_8)));
        var missing = (ObjectNode) schema.tree(input());
        missing.remove("replay_receipt_sha256");
        assertThrows(Exception.class, () -> schema.read(schema.canonical(missing).getBytes(StandardCharsets.UTF_8)));
        var nested = (ObjectNode) schema.tree(input());
        ((ObjectNode) nested.required("fixed_sources").get(0)).put("allow_all", true);
        assertThrows(Exception.class, () -> schema.read(schema.canonical(nested).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsReplayHashSubstitutionAndDuplicateSources() throws Exception {
        var schema = new TaskNodeInputSchema();
        var wrong = (ObjectNode) schema.tree(input());
        wrong.put("replay_sha256", "b".repeat(64));
        assertThrows(Exception.class, () -> schema.read(schema.canonical(wrong).getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new TaskNodeInputProtocol.ReplayArtifact(ID, HASH, " "));
        assertThrows(IllegalArgumentException.class, () -> new TaskNodeInputProtocol.SourceSelection(ID, ID, ID, ID, ID, ID, ""));
    }
    private static TaskNodeInputProtocol.Predecessor predecessor(String step) {
        return new TaskNodeInputProtocol.Predecessor(step,"22222222-2222-4222-8222-222222222222",HASH,"result-v1",HASH,
                "33333333-3333-4333-8333-333333333333",HASH,"evidence-v1",HASH,"b".repeat(64));
    }
    private TaskNodeInputProtocol.Input withPredecessors(List<TaskNodeInputProtocol.Predecessor> predecessors){
        var i=input();return new TaskNodeInputProtocol.Input(i.schemaVersion(),i.tenantId(),i.projectId(),i.taskId(),i.runId(),i.stepId(),i.graphId(),i.graphSha256(),i.nodeKind(),i.questionId(),i.questionKey(),i.questionText(),i.sectionId(),i.planRevision(),i.planSha256(),i.snapshotId(),i.snapshotSha256(),i.replaySha256(),i.replayArtifact(),i.replayReceiptSha256(),i.roleVersion(),i.executionPolicyVersion(),i.fixedSources(),i.nodePermissions(),predecessors);
    }
    @Test void predecessorVersionsAndIndependentCompletionReceiptRoundTrip()throws Exception {
        var schema=new TaskNodeInputSchema();var predecessor=predecessor("44444444-4444-4444-8444-444444444444");
        var value=withPredecessors(List.of(predecessor));var parsed=schema.read(schema.bytes(value));
        assertEquals(value,parsed);assertNotEquals(predecessor.resultReceiptSha256(),predecessor.completionReceiptSha256());
        assertEquals("evidence-v1",parsed.predecessors().get(0).evidenceObjectVersion());
        assertThrows(UnsupportedOperationException.class,()->parsed.predecessors().clear());
    }
    @Test void missingPredecessorsAndCallerInventedApprovalAreRejected()throws Exception {
        var schema=new TaskNodeInputSchema();var missing=(ObjectNode)schema.tree(input());missing.remove("predecessors");
        assertThrows(Exception.class,()->schema.read(schema.canonical(missing).getBytes(StandardCharsets.UTF_8)));
        var value=(ObjectNode)schema.tree(withPredecessors(List.of(predecessor("44444444-4444-4444-8444-444444444444"))));
        ((ObjectNode)value.required("predecessors").get(0)).put("completed",true);
        assertThrows(Exception.class,()->schema.read(schema.canonical(value).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void selfAndDuplicatePredecessorsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->withPredecessors(List.of(predecessor(ID))));
        var predecessor=predecessor("44444444-4444-4444-8444-444444444444");
        assertThrows(IllegalArgumentException.class,()->withPredecessors(List.of(predecessor,predecessor)));
    }

}
