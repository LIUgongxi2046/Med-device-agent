package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormalEvaluationNodeInputObjectLedgerTest extends NodeInputObjectFixture {
    FormalEvaluationNodeInputObjectLedgerTest() throws Exception { }

    @Test
    void insertUsesStableColumnOrderAndKeepsEachValuePairedWithItsColumn() {
        var original = new LinkedHashMap<String, Object>();
        original.put("version_id", id(701));
        original.put("tenant_id", tenant);
        original.put("owner_project_id", project);
        original.put("entity_kind", "ARTIFACT");
        original.put("content_sha256", hash('f'));

        var reversed = new LinkedHashMap<String, Object>();
        var reversedKeys = new ArrayList<>(original.keySet());
        Collections.reverse(reversedKeys);
        reversedKeys.forEach(column -> reversed.put(column, original.get(column)));
        assertNotEquals(new ArrayList<>(original.keySet()), new ArrayList<>(reversed.keySet()));

        // The fixture captures the actual production JDBC SQL and reconstructs rows
        // from its bind arguments; the database itself is an explicit boundary double.
        ledger.insert("version_registry", original);
        ledger.insert("version_registry", reversed);

        String expectedSql = "INSERT INTO version_registry "
                + "(content_sha256,entity_kind,owner_project_id,tenant_id,version_id) VALUES (?,?,?,?,?)";
        assertEquals(List.of(expectedSql, expectedSql), executedSql);
        assertEquals(List.of(original, original), rows("version_registry"));
    }

    @Test void oneInitialClaimAndAllPersistentRetriesRecoverOnly() throws Exception {
        var first = prepare();
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE, first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, prepare().claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, recover().claimTransferMode());
        assertEquals(1, rows("formal_evaluation_node_input_intent").size());
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
        assertEquals(2, rows("audit_event").size());
        assertEquals(2, rows("outbox_event").size());
    }

    @Test void originalBodyActorContextAndKeyAreBoundIndependentlyFromExecution() throws Exception {
        var attempt = prepare();
        var intent = schema.read((String) only("formal_evaluation_node_input_intent").get("canonical_intent"));
        assertEquals(checked.original().body(), intent.required("original_body").textValue());
        assertEquals(key, intent.required("idempotency_key").textValue());
        assertEquals(actor, attempt.actorId());
        assertNotEquals(attempt.operationId(), execution);
        assertNotEquals(attempt.writeIntent().writeId(), execution);
        assertEquals("1", intent.required("context_revision").textValue());
    }

    @Test void sameKeyWithEquivalentCanonicalRequestRecoversOriginal() throws Exception {
        prepare();
        var original = schema.request((checked.original().body() + "\n").getBytes(StandardCharsets.UTF_8));
        checked = new FormalEvaluationNodeInputObjectAuthority.Checked(checked.tenant(), checked.actor(), checked.context(),
                checked.project(), original, checked.evaluation(), checked.node(), checked.contextBinding(), checked.bytes(),
                checked.evidence(), checked.stable(), checked.dependencies());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,prepare().claimTransferMode());
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
    }

    @Test void secondKeyCannotCreateAnotherObjectForTheSameOriginalNode() throws Exception {
        prepare();
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, assertThrows(TenantAccessException.class,
                () -> tx(() -> ledger.prepare(admission, "replacement-original-key", proof))).code());
        assertEquals(1, rows("formal_evaluation_node_input_intent").size());
    }

    @Test void missingOriginalOperationCannotRestartPut() throws Exception {
        prepare(); rows("operation_record").clear();
        assertThrows(TenantAccessException.class, this::prepare);
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
    }

    @Test void missingDurableAttemptWithRevisionTwoCannotCreateAnother() throws Exception {
        prepare(); rows("formal_evaluation_node_input_attempt").clear();
        assertThrows(TenantAccessException.class, this::prepare);
        assertTrue(rows("formal_evaluation_node_input_attempt").isEmpty());
    }

    @Test void operationRollbackDoesNotAllowSecondAttempt() throws Exception {
        prepare(); only("operation_record").put("revision", 1);
        assertThrows(TenantAccessException.class, this::prepare);
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
    }

    @Test void originalExpiryStopsInitialTransferButAllowsOnlyOriginalRecovery() throws Exception {
        var first = prepare(); now = now.plusMinutes(6);
        assertEquals(ErrorCode.ACTION_STALE, assertThrows(TenantAccessException.class,
                () -> tx(() -> { ledger.requireTransfer(admission, first, proof); return null; })).code());
        var recovered = recover();
        tx(() -> { ledger.requireTransfer(admission, recovered, proof); return null; });
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, recovered.claimTransferMode());
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
    }

    @Test void recoveryDoesNotCreateMissingIntent() {
        assertThrows(TenantAccessException.class, this::recover);
        assertTrue(rows("formal_evaluation_node_input_intent").isEmpty());
        assertTrue(rows("operation_record").isEmpty());
    }

    @Test void freshReadReceiptsMayChangeButOriginalReceiptsRemainImmutable() throws Exception {
        prepare(); String original = (String) only("formal_evaluation_node_input_intent").get("canonical_intent");
        var evidence = (com.fasterxml.jackson.databind.node.ObjectNode) checked.evidence();
        ((com.fasterxml.jackson.databind.node.ObjectNode) evidence.required("prompt_original")).putArray("receipt_ids").add(id(999));
        checked = new FormalEvaluationNodeInputObjectAuthority.Checked(checked.tenant(), checked.actor(), checked.context(),
                checked.project(), checked.original(), checked.evaluation(), checked.node(), checked.contextBinding(), checked.bytes(),
                evidence, checked.stable(), checked.dependencies());
        assertTrue(recover().priorAttempt());
        assertEquals(original, only("formal_evaluation_node_input_intent").get("canonical_intent"));
    }

    @Test void mutatedStableRightsOrObjectProvenanceCannotResume() throws Exception {
        prepare();
        var stable = (com.fasterxml.jackson.databind.node.ObjectNode) schema.read(checked.stable());
        stable.put("rights_changed", true);
        checked = new FormalEvaluationNodeInputObjectAuthority.Checked(checked.tenant(), checked.actor(), checked.context(),
                checked.project(), checked.original(), checked.evaluation(), checked.node(), checked.contextBinding(), checked.bytes(),
                checked.evidence(), schema.canonical(stable), checked.dependencies());
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, this::recover).code());
    }

    @Test void revokedPrivateSourceContextOrModelSendProofStopsBeforeNewWrite() throws Exception {
        prepare();
        when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class, this::recover);
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
    }

    @Test void corruptedAuditOrAttemptMetadataFailsClosed() throws Exception {
        prepare(); only("formal_evaluation_node_input_attempt").put("classification", "PUBLIC");
        assertThrows(TenantAccessException.class, this::recover);
    }

    @Test void missingOriginalJournalBlocksRetry() throws Exception {
        prepare(); rows("audit_event").remove(0);
        assertThrows(TenantAccessException.class, this::recover);
    }

    @Test void proofCannotBeUsedInAnotherLedgerInstanceOrWithoutTenantTransaction() throws Exception {
        var attempt = prepare();
        assertThrows(TenantAccessException.class, () -> ledger.require(admission, attempt, proof));
        var another = new FormalEvaluationNodeInputObjectLedger(db, schema, json);
        assertThrows(TenantAccessException.class, () -> tx(() -> another.require(admission, attempt, proof)));
        assertTrue(java.lang.reflect.Modifier.isPrivate(FormalEvaluationNodeInputObjectLedger.Attempt.class.getDeclaredConstructors()[0].getModifiers()));
    }
}
