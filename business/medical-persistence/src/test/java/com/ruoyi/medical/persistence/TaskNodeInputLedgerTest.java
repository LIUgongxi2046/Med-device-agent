package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class TaskNodeInputLedgerTest extends TaskNodeInputFixture {
    TaskNodeInputLedgerTest()throws Exception{}
    @Test void oneImmutableIntentAndAttemptOnly()throws Exception {
        var first=prepare();assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,first.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,prepare().claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,recover().claimTransferMode());
        assertEquals(1,rows("task_node_input_intent").size());assertEquals(1,rows("task_node_input_attempt").size());
        assertEquals(2,rows("audit_event").size());assertEquals(2,rows("outbox_event").size());
    }
    @Test void originalRequestPinsExactInputCommandAndProducerWithoutMutatingReplay()throws Exception {
        var first=prepare();var intent=schema.readTree((String)only("task_node_input_intent").get("canonical_intent"));
        assertEquals(schema.tree(input),intent.required("original_request").required("input"));
        assertEquals(bootstrap.command(),intent.required("original_request").required("command"));
        assertEquals(producer.authorizations(),intent.required("original_request").required("producer"));
        assertEquals(actor,first.actorId());assertEquals(run,first.runId());assertEquals(step,first.stepId());
        assertTrue(rows("task_run").isEmpty());assertTrue(rows("run_step").isEmpty());assertTrue(rows("runtime_node_definition").isEmpty());
    }
    @Test void secondKeyCannotProduceSecondObjectForStep()throws Exception {
        prepare();var error=assertThrows(TenantAccessException.class,()->tx(()->ledger.prepare(proof,"replacement-key-0001")));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,error.code());assertEquals(1,rows("task_node_input_intent").size());
    }
    @Test void missingOriginalOperationOrAttemptNeverRestartsWrite()throws Exception {
        prepare();rows("task_node_input_attempt").clear();assertThrows(TenantAccessException.class,this::prepare);
        assertTrue(rows("task_node_input_attempt").isEmpty());rows("operation_record").clear();assertThrows(TenantAccessException.class,this::prepare);
    }
    @Test void expiredInitialTransferRequiresOriginalRecovery()throws Exception {
        var first=prepare();now=now.plusMinutes(6);
        assertEquals(ErrorCode.ACTION_STALE,assertThrows(TenantAccessException.class,()->tx(()->{ledger.requireTransfer(proof,first);return null;})).code());
        var recovered=recover();tx(()->{ledger.requireTransfer(proof,recovered);return null;});
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,recovered.claimTransferMode());
    }
    @Test void changedApprovalCannotReplaceOriginalRequest()throws Exception {
        prepare();String original=(String)only("task_node_input_intent").get("canonical_intent");
        when(producer.authorizations()).thenReturn(json.object().put("replacement_approval",true));
        assertThrows(TenantAccessException.class,this::recover);assertEquals(original,only("task_node_input_intent").get("canonical_intent"));
    }
    @Test void missingOrAlteredOriginalJournalFailsClosed()throws Exception {
        prepare();only("task_node_input_attempt").put("created_by",id(999));assertThrows(TenantAccessException.class,this::recover);
    }
    @Test void sourceRightsChangeCannotResumeOriginalInput()throws Exception {
        prepare();when(content.members()).thenReturn(List.of(new ArtifactRegistryProtocol.MemberRow(id(20),id(999))));
        assertThrows(TenantAccessException.class,this::recover);assertEquals(1,rows("task_node_input_attempt").size());
    }
    @Test void absentOriginalRecoveryNeverCreatesRows(){assertThrows(TenantAccessException.class,this::recover);assertTrue(rows("task_node_input_intent").isEmpty());}
    @Test void changedPredecessorReceiptCannotReplaceOriginalInput()throws Exception {
        prepare();var i=input;
        var predecessor=new TaskNodeInputProtocol.Predecessor(id(50),id(51),hash('a'),"result-v1",hash('b'),id(52),hash('c'),"evidence-v1",hash('d'),hash('e'));
        input=new TaskNodeInputProtocol.Input(i.schemaVersion(),i.tenantId(),i.projectId(),i.taskId(),i.runId(),i.stepId(),i.graphId(),i.graphSha256(),i.nodeKind(),i.questionId(),i.questionKey(),i.questionText(),i.sectionId(),i.planRevision(),i.planSha256(),i.snapshotId(),i.snapshotSha256(),i.replaySha256(),i.replayArtifact(),i.replayReceiptSha256(),i.roleVersion(),i.executionPolicyVersion(),i.fixedSources(),i.nodePermissions(),List.of(predecessor));
        assertThrows(TenantAccessException.class,this::recover);assertEquals(1,rows("task_node_input_intent").size());
    }

}
