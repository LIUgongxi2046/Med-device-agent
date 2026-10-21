package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormalEvaluationPlanPreparationLedgerTest extends PlanPreparationFixture {
    FormalEvaluationPlanPreparationLedgerTest() throws Exception { }

    @Test void oneInitialClaimAndAllPersistentRetriesRecoverOnly() throws Exception {
        var first = prepare();
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE, first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, prepare().claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, recover().claimTransferMode());
        assertEquals(1, rows("formal_evaluation_plan_preparation_intent").size());
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
        assertEquals(2, rows("audit_event").size());
        assertEquals(2, rows("outbox_event").size());
    }

    @Test void originalBodyActorContextAndKeyAreBoundIndependentlyFromExecution() throws Exception {
        var attempt = prepare();
        var intent = schema.read((String) only("formal_evaluation_plan_preparation_intent").get("canonical_intent"));
        assertEquals(checked.original().body(), intent.required("original_body").textValue());
        assertEquals(key, intent.required("idempotency_key").textValue());
        assertEquals(actor, attempt.actorId());
        assertNotEquals(attempt.operationId(), execution);
        assertNotEquals(attempt.writeIntent().writeId(), execution);
        assertEquals("1", intent.required("context_revision").textValue());
    }

    @Test void sameTypedOriginalMayUseDifferentWhitespace() throws Exception {
        prepare();
        var original = schema.request((checked.original().body() + "\n").getBytes(StandardCharsets.UTF_8));
        checked = new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(), checked.actor(), checked.context(),
                checked.project(), original, checked.evaluation(), checked.node(), checked.contextBinding(), checked.bytes(),
                checked.evidence(), checked.stable(), checked.dependencies(),checked.preview(),checked.writing());
        assertTrue(prepare().priorAttempt());
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void secondKeyCannotCreateAnotherObjectForTheSameOriginalNode() throws Exception {
        prepare();
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, assertThrows(TenantAccessException.class,
                () -> tx(() -> ledger.prepare(admission, "replacement-original-key", proof))).code());
        assertEquals(1, rows("formal_evaluation_plan_preparation_intent").size());
    }

    @Test void missingOriginalOperationCannotRestartPut() throws Exception {
        prepare(); rows("operation_record").clear();
        assertThrows(TenantAccessException.class, this::prepare);
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void missingDurableAttemptWithRevisionTwoCannotCreateAnother() throws Exception {
        prepare(); rows("formal_evaluation_plan_preparation_attempt").clear();
        assertThrows(TenantAccessException.class, this::prepare);
        assertTrue(rows("formal_evaluation_plan_preparation_attempt").isEmpty());
    }

    @Test void operationRollbackDoesNotAllowSecondAttempt() throws Exception {
        prepare(); only("operation_record").put("revision", 1);
        assertThrows(TenantAccessException.class, this::prepare);
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void originalExpiryStopsInitialTransferButAllowsOnlyOriginalRecovery() throws Exception {
        var first = prepare(); now = now.plusMinutes(6);
        assertEquals(ErrorCode.ACTION_STALE, assertThrows(TenantAccessException.class,
                () -> tx(() -> { ledger.requireTransfer(admission, first, proof); return null; })).code());
        var recovered = recover();
        tx(() -> { ledger.requireTransfer(admission, recovered, proof); return null; });
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY, recovered.claimTransferMode());
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void recoveryDoesNotCreateMissingIntent() {
        assertThrows(TenantAccessException.class, this::recover);
        assertTrue(rows("formal_evaluation_plan_preparation_intent").isEmpty());
        assertTrue(rows("operation_record").isEmpty());
    }

    @Test void mutatedStableRightsOrObjectProvenanceCannotResume() throws Exception {
        prepare();
        var stable = (com.fasterxml.jackson.databind.node.ObjectNode) schema.read(checked.stable());
        stable.put("rights_changed", true);
        checked = new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(), checked.actor(), checked.context(),
                checked.project(), checked.original(), checked.evaluation(), checked.node(), checked.contextBinding(), checked.bytes(),
                checked.evidence(), schema.canonical(stable), checked.dependencies(),checked.preview(),checked.writing());
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, this::recover).code());
    }

    @Test void replacementRegistrationGrantCannotReauthorizeOriginalUncertainWrite() throws Exception {
        prepare();
        var evidence=checked.evidence().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)evidence.required("artifact_registration").required("grant")).put("replacement_grant_id",id(999));
        checked = new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),checked.original(),checked.evaluation(),checked.node(),checked.contextBinding(),checked.bytes(),evidence,checked.stable(),checked.dependencies(),checked.preview(),true);
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,this::recover).code());
        assertEquals(1,rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void revokedPrivateSourceContextOrModelSendProofStopsBeforeNewWrite() throws Exception {
        prepare();
        when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class, this::recover);
        assertEquals(1, rows("formal_evaluation_plan_preparation_attempt").size());
    }

    @Test void corruptedAuditOrAttemptMetadataFailsClosed() throws Exception {
        prepare(); only("formal_evaluation_plan_preparation_attempt").put("classification", "PUBLIC");
        assertThrows(TenantAccessException.class, this::recover);
    }

    @Test void missingOriginalJournalBlocksRetry() throws Exception {
        prepare(); rows("audit_event").remove(0);
        assertThrows(TenantAccessException.class, this::recover);
    }

    @Test void proofCannotBeUsedInAnotherLedgerInstanceOrWithoutTenantTransaction() throws Exception {
        var attempt = prepare();
        assertThrows(TenantAccessException.class, () -> ledger.require(admission, attempt, proof));
        var another = new FormalEvaluationPlanPreparationLedger(db, schema, json);
        assertThrows(TenantAccessException.class, () -> tx(() -> another.require(admission, attempt, proof)));
        assertTrue(java.lang.reflect.Modifier.isPrivate(FormalEvaluationPlanPreparationLedger.Attempt.class.getDeclaredConstructors()[0].getModifiers()));
    }
}
