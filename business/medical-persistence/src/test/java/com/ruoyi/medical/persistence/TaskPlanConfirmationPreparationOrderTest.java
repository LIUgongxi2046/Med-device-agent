package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real service orchestration with synthetic transaction/database boundaries, not SQL rollback coverage. */
class TaskPlanConfirmationPreparationOrderTest {
    private static final String ID="00000000-0000-4000-8000-000000000001";
    static class Fixture {
        final EnterpriseAccess access=mock(EnterpriseAccess.class);
        final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);
        final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
        final TaskService tasks=mock(TaskService.class);
        final TaskPlanService plans=mock(TaskPlanService.class);
        final TaskPlanWriteMapper mapper=mock(TaskPlanWriteMapper.class);
        final PermissionMapper permissions=mock(PermissionMapper.class);
        final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
        final TaskPlanDecisionSchema schema=mock(TaskPlanDecisionSchema.class);
        final TaskPlanConfirmationStore confirmations=mock(TaskPlanConfirmationStore.class);
        final TaskService.VerifiedTask fixed=mock(TaskService.VerifiedTask.class,RETURNS_DEEP_STUBS);
        final TaskPlanWriteProtocol.View draft=mock(TaskPlanWriteProtocol.View.class);
        final IdentityDirectoryPolicy identity=mock(IdentityDirectoryPolicy.class);
        final TaskPlanReturnService service;
        Fixture() throws Exception {
            when(access.execute(eq(subject),any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try { return ((Function<EnterpriseAccess.Admission,Object>)call.getArgument(1)).apply(admission); }
                finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            });
            when(admission.context().context_revision()).thenReturn("1");
            when(admission.context().tenant_id()).thenReturn(ID); when(identity.classification()).thenReturn("INTERNAL");
            when(admission.subject().user().user_id()).thenReturn(ID); when(subject.user().user_id()).thenReturn(ID);
            when(tasks.fixedTask(admission,ID)).thenReturn(fixed);
            when(fixed.head().tenantId()).thenReturn(ID); when(fixed.head().projectId()).thenReturn(ID);
            when(fixed.head().taskId()).thenReturn(ID); when(fixed.head().status()).thenReturn("PLAN_PENDING");
            when(fixed.head().taskRevision()).thenReturn(ID);
            var head=mock(TaskPlanWriteProtocol.Head.class); when(head.planRevision()).thenReturn(ID);
            when(plans.planHead(any())).thenReturn(head); when(plans.readFixed(admission,fixed,ID,false)).thenReturn(draft);
            when(draft.status()).thenReturn("DRAFT"); when(draft.taskRevision()).thenReturn(ID);
            when(schema.request(any())).thenReturn(new TaskPlanDecisionProtocol.Request("1.0.0",ID,"1","CONFIRM","Synthetic test",null));
            service=new TaskPlanReturnService(access,tasks,plans,mapper,permissions,resources,schema,
                    new PermissionSchema(),identity,confirmations);
        }
        com.fasterxml.jackson.databind.JsonNode completedOriginal() throws Exception {
            var json=new PermissionSchema(); String h="a".repeat(64);
            String authorizations="[]", authHash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(authorizations);
            var baseline=new TaskPlanDecisionProtocol.Baseline("1.0.0",ID,ID,ID,ID,ID,h,ID,ID,ID,h,h,h,h,
                    "PLAN_PENDING","1","1","1","1","1",authHash);
            var request=new TaskPlanDecisionProtocol.Request("1.0.0",ID,"1","CONFIRM","Synthetic test",baseline);
            when(schema.request(any())).thenReturn(request); when(schema.manifestSha256()).thenReturn(h);
            when(schema.read(anyString())).thenAnswer(call -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)call.getArgument(0)));
            var row=mock(TaskPlanDecisionProtocol.Row.class);
            when(row.requestJson()).thenReturn("{}"); when(row.receiptJson()).thenReturn("saved");
            when(row.tenantId()).thenReturn(ID); when(row.projectId()).thenReturn(ID); when(row.taskId()).thenReturn(ID);
            when(row.createdBy()).thenReturn(ID); when(row.operationId()).thenReturn(ID); when(row.decisionId()).thenReturn(ID);
            when(row.targetPlanRevision()).thenReturn(ID); when(row.targetPlanSha256()).thenReturn(h);
            when(row.basisTaskRevision()).thenReturn(ID); when(row.resultingTaskRevision()).thenReturn(ID);
            when(row.resultingPlanRevision()).thenReturn(ID); when(row.reason()).thenReturn("Synthetic test");
            when(row.decision()).thenReturn("CONFIRM"); when(row.fromStatus()).thenReturn("PLAN_PENDING"); when(row.toStatus()).thenReturn("READY");
            when(row.expectedRevision()).thenReturn(java.math.BigInteger.ONE); when(row.resultingRevision()).thenReturn(java.math.BigInteger.TWO);
            when(row.schemaVersion()).thenReturn("1.0.0"); when(row.schemaManifest()).thenReturn(h); when(row.classification()).thenReturn("INTERNAL");
            when(row.authorityGrant()).thenReturn(ID); when(row.authorityRevision()).thenReturn(java.math.BigInteger.ONE);
            when(row.authEpoch()).thenReturn(java.math.BigInteger.ONE); when(row.governanceEpoch()).thenReturn(java.math.BigInteger.ONE);
            var time=java.time.LocalDateTime.of(2026,1,1,0,0); when(row.createdAt()).thenReturn(time);
            when(row.baselineManifest()).thenReturn(json.canonicalObject(baseline));
            when(row.baselineSha256()).thenReturn(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonicalObject(baseline)));
            when(row.dependencyAuthorizations()).thenReturn(authorizations);
            when(draft.contentSha256()).thenReturn(h); when(draft.snapshotId()).thenReturn(ID);
            when(draft.templateVersion()).thenReturn(ID); when(draft.profileRevision()).thenReturn(ID);
            var response=json.object().put("request_id",ID).put("schema_version","1.0.0").put("revision","2");
            response.set("data",json.object().put("synthetic_receipt",true));
            when(schema.response(anyString())).thenReturn(response); when(schema.envelope(any(),eq(ID))).thenReturn(response);
            when(row.receiptSha256()).thenReturn(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(response)));
            var operation=mock(SelectionOperationRow.class);
            var canonical=json.object().put("operation_id","TK-04").put("task_id",ID); canonical.set("input",json.tree(request));
            when(operation.requestSha256()).thenReturn(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(canonical)));
            when(operation.state()).thenReturn("COMPLETED"); when(operation.httpStatus()).thenReturn(200);
            when(operation.targetId()).thenReturn(ID); when(operation.schemaVersion()).thenReturn("1.0.0");
            when(operation.schemaManifestSha256()).thenReturn(h); when(operation.operationId()).thenReturn(ID); when(operation.responseRef()).thenReturn("saved");
            when(mapper.operation(any())).thenReturn(List.of(operation)); when(mapper.decision(any())).thenReturn(List.of(row));
            var receipt=new TaskPlanDecisionProtocol.Receipt("1.0.0",ID,ID,ID,ID,ID,ID,h,ID,ID,ID,"CONFIRM","PLAN_PENDING","READY",
                    "1","2",ID,time.toInstant(java.time.ZoneOffset.UTC).toString(),"Synthetic test");
            var audit=json.object().put("operation_id",ID).put("decision_id",ID); audit.set("receipt",json.tree(receipt)); audit.set("baseline_manifest",json.tree(baseline));
            when(mapper.decisionAudit(any())).thenReturn(List.of(json.canonical(audit)));
            when(mapper.decisionEvent(any())).thenReturn(List.of(json.canonical(response)));
            return response;
        }
        TaskPlanConfirmationStore.Prepared newConfirmation() throws Exception {
            var json=new PermissionSchema(); String h="a".repeat(64);
            when(permissions.databaseTime()).thenReturn(java.time.LocalDateTime.of(2026,1,1,0,0));
            when(schema.manifestSha256()).thenReturn(h);
            when(fixed.head().revision()).thenReturn(java.math.BigInteger.ONE);
            when(fixed.head().snapshotId()).thenReturn(ID); when(fixed.head().templateVersion()).thenReturn(ID);
            when(fixed.head().profileRevision()).thenReturn(ID); when(fixed.head().contentHash()).thenReturn(h);
            when(fixed.input().requestHash()).thenReturn(h); when(fixed.input().scopeHash()).thenReturn(h); when(fixed.input().requirementsHash()).thenReturn(h);
            when(draft.planRevision()).thenReturn(ID); when(draft.contentSha256()).thenReturn(h);
            var head=plans.planHead(java.util.Map.of()); when(head.revision()).thenReturn(java.math.BigInteger.ONE);
            when(admission.watermarks().authEpoch()).thenReturn(java.math.BigInteger.ONE);
            when(admission.watermarks().governanceEpoch()).thenReturn(java.math.BigInteger.ONE);
            var grant=mock(PermissionProtocol.GrantRow.class); when(grant.revision()).thenReturn(java.math.BigInteger.ONE);
            when(grant.grantId()).thenReturn(ID); when(grant.basisVersion()).thenReturn(ID);
            when(resources.require(any(),any(),any(),any())).thenReturn(grant);
            var prepared=mock(TaskPlanConfirmationStore.Prepared.class); when(prepared.authorizations()).thenReturn(json.object().arrayNode());
            when(confirmations.prepareExternal(subject,"1",fixed,draft,false)).thenAnswer(call->{
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new TaskPlanConfirmationStore.External(mock(TaskPlanReleaseAdmission.class),null);
            });
            when(confirmations.prepareVerified(eq(admission),eq(fixed),eq(draft),any(),any())).thenAnswer(call->{
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return prepared;
            });
            var baseline=new TaskPlanDecisionProtocol.Baseline("1.0.0",ID,ID,ID,ID,ID,h,ID,ID,ID,h,h,h,h,
                    "PLAN_PENDING","1","1","1","1","1",org.apache.commons.codec.digest.DigestUtils.sha256Hex("[]"));
            when(schema.request(any())).thenReturn(new TaskPlanDecisionProtocol.Request("1.0.0",ID,"1","CONFIRM","Synthetic test",baseline));
            when(mapper.beginOperation(any())).thenReturn(1); when(mapper.insertDecision(any())).thenReturn(1); when(mapper.bindDecision(any())).thenReturn(1);
            when(confirmations.save(eq(admission),eq(prepared),any(),any())).thenAnswer(call->{
                java.util.Map<String,Object> values=call.getArgument(3); values.put("taskRevision",ID); values.put("resultingPlanRevision",ID);
                values.put("resultingPlanHeadRevision","2"); return new TaskPlanConfirmationStore.Result(ID,ID,h);
            });
            var response=json.object().put("request_id",ID); response.set("data",json.object()); when(schema.envelope(any(),any())).thenReturn(response);
            return prepared;
        }
        void decide(){service.decide(subject,"1","synthetic-plan-key",ID,new byte[]{'{','}'});}
        void rejectPreparation() throws Exception {
            when(confirmations.prepareExternal(subject,"1",fixed,draft,false)).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            });
        }
    }
    @Test void basisPhysicalPreparationIsOutsideTransaction() throws Exception {
        var f=new Fixture(); f.rejectPreparation();
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,
                () -> f.service.basis(f.subject,"1",ID,"CONFIRM")).code());
        verify(f.confirmations).prepareExternal(f.subject,"1",f.fixed,f.draft,false);
        verify(f.confirmations,never()).prepareVerified(any(),any(),any(),any(),any()); verifyNoInteractions(f.mapper);
    }
    @Test void decisionChecksOriginalBeforePhysicalPreparationAndNeverWritesOnFailure() throws Exception {
        var f=new Fixture(); f.rejectPreparation();
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,f::decide).code());
        var order=inOrder(f.mapper,f.confirmations); order.verify(f.mapper).operation(any());
        order.verify(f.confirmations).prepareExternal(f.subject,"1",f.fixed,f.draft,false);
        verify(f.mapper,never()).beginOperation(any());
    }
    @Test void originalConflictDoesNotConsultLivePublicationOrModel() throws Exception {
        var f=new Fixture(); var original=mock(SelectionOperationRow.class); when(original.requestSha256()).thenReturn("different");
        when(f.mapper.operation(any())).thenReturn(List.of(original));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,f::decide).code());
        verifyNoInteractions(f.confirmations); verify(f.mapper,never()).beginOperation(any());
    }
    @Test void deniedCurrentPermissionPrecedesObjectPreparation() throws Exception {
        var f=new Fixture(); doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.resources)
                .require(any(),any(),eq("plan.confirm"),eq("PLAN_MAINTENANCE"));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,f::decide).code());
        verifyNoInteractions(f.confirmations,f.mapper);
    }
    @Test void changedContextPrecedesAllTaskOrPublicationReads() throws Exception {
        var f=new Fixture(); when(f.admission.context().context_revision()).thenReturn("2");
        assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,f::decide).code());
        verifyNoInteractions(f.confirmations,f.tasks,f.mapper);
    }
    @Test void explicitOriginalReadNeverPreparesLivePublication() throws Exception {
        var f=new Fixture(); when(f.fixed.head().revision()).thenReturn(java.math.BigInteger.ONE);
        assertTrue(f.service.original(f.subject,"1","synthetic-plan-key",ID).path("data").isNull());
        verifyNoInteractions(f.confirmations);
    }
    @Test void completedConfirmationReplayDoesNotRenewPublicationOrPolicy() throws Exception {
        var f=new Fixture(); var response=f.completedOriginal();
        when(f.confirmations.prepareExternal(any(),any(),any(),any(),anyBoolean())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        assertEquals(response,f.service.decide(f.subject,"1","synthetic-plan-key",ID,new byte[]{'{','}'}));
        verify(f.confirmations,never()).prepareExternal(any(),any(),any(),any(),anyBoolean());
        verify(f.confirmations).verifyResult(eq(f.admission),eq(f.fixed),eq(f.draft),any(),any());
        verify(f.mapper,never()).beginOperation(any());
    }
    @Test void originalAppearingAfterPreparationWinsOverNewConfirmation() throws Exception {
        var f=new Fixture(); var response=f.completedOriginal();
        var original=f.mapper.operation(java.util.Map.of());
        when(f.mapper.operation(any())).thenReturn(List.of(),original);
        when(f.confirmations.prepareExternal(f.subject,"1",f.fixed,f.draft,false)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new TaskPlanConfirmationStore.External(mock(TaskPlanReleaseAdmission.class),null);
        });
        assertEquals(response,f.service.decide(f.subject,"1","synthetic-plan-key",ID,new byte[]{'{','}'}));
        verify(f.confirmations).prepareExternal(f.subject,"1",f.fixed,f.draft,false);
        verify(f.confirmations,never()).prepareVerified(any(),any(),any(),any(),any()); verify(f.mapper,never()).beginOperation(any());
    }
    @Test void returnBasisDoesNotRequirePublicationProvider() throws Exception {
        var f=new Fixture(); var sentinel=new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        when(f.plans.sourceProof(any(),any(),any(),any(),any(),any(),eq(false))).thenThrow(sentinel);
        var grant=mock(PermissionProtocol.GrantRow.class); when(grant.revision()).thenReturn(java.math.BigInteger.ONE);
        when(f.resources.require(any(),any(),any(),any())).thenReturn(grant);
        assertSame(sentinel,assertThrows(TenantAccessException.class,()->f.service.basis(f.subject,"1",ID,"RETURN")));
        verifyNoInteractions(f.confirmations);
    }

    @Test void failedTaskHeadCasCannotReturnSuccessOrCompleteOperation() throws Exception {
        var f=new Fixture(); f.newConfirmation(); when(f.mapper.advanceTaskHead(any())).thenReturn(0);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,f::decide).code());
        verify(f.confirmations).prepareVerified(eq(f.admission),eq(f.fixed),eq(f.draft),any(),any());
        verify(f.confirmations).save(eq(f.admission),any(),any(),any()); verify(f.mapper).advanceTaskHead(any());
        verify(f.mapper,never()).completeOperation(any()); verify(f.permissions,never()).saveEvent(any(),any(),any(),any(),any(),any());
    }
    @Test void returnDecisionDoesNotRequirePublicationProvider() throws Exception {
        var f=new Fixture(); when(f.schema.request(any())).thenReturn(new TaskPlanDecisionProtocol.Request("1.0.0",ID,"1","RETURN","Synthetic test",null));
        var sentinel=new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        when(f.plans.sourceProof(any(),any(),any(),any(),any(),any(),eq(false))).thenThrow(sentinel);
        var grant=mock(PermissionProtocol.GrantRow.class); when(grant.revision()).thenReturn(java.math.BigInteger.ONE);
        when(f.resources.require(any(),any(),any(),any())).thenReturn(grant);
        assertSame(sentinel,assertThrows(TenantAccessException.class,f::decide)); verifyNoInteractions(f.confirmations);
        verify(f.mapper,never()).beginOperation(any());
    }

    @Test void postWriteRecheckFailureCannotPublishSuccess() throws Exception {
        var f=new Fixture(); var prepared=f.newConfirmation(); when(f.mapper.advanceTaskHead(any())).thenReturn(1);
        var after=mock(TaskService.VerifiedTask.class,RETURNS_DEEP_STUBS);
        when(after.head().taskRevision()).thenReturn(ID); when(after.head().status()).thenReturn("READY");
        when(after.head().revision()).thenReturn(java.math.BigInteger.TWO);
        var originalInput=f.fixed.input(); when(after.input()).thenReturn(originalInput);
        when(f.tasks.fixedTask(f.admission,ID)).thenReturn(f.fixed,f.fixed,f.fixed,f.fixed,after);
        when(f.plans.readFixed(f.admission,after,ID,false)).thenReturn(f.draft);
        var content=mock(com.ruoyi.medical.contracts.ArtifactProtocol.PlanCandidate.class);
        when(f.draft.content()).thenReturn(content);
        var confirmed=mock(TaskPlanWriteProtocol.View.class); when(confirmed.content()).thenReturn(content);
        when(confirmed.status()).thenReturn("CONFIRMED"); when(confirmed.taskRevision()).thenReturn(ID); when(confirmed.contentSha256()).thenReturn("a".repeat(64));
        when(f.plans.readFixed(f.admission,after,ID,true)).thenReturn(confirmed);
        var beforeHead=f.plans.planHead(java.util.Map.of()); var afterHead=mock(TaskPlanWriteProtocol.Head.class);
        when(afterHead.planRevision()).thenReturn(ID); when(afterHead.revision()).thenReturn(java.math.BigInteger.TWO);
        when(f.plans.planHead(any())).thenReturn(beforeHead,beforeHead,afterHead);
        var sentinel=new TenantAccessException(ErrorCode.SCOPE_STALE);
        doThrow(sentinel).when(f.confirmations).requireUnchanged(f.admission,prepared);
        assertSame(sentinel,assertThrows(TenantAccessException.class,f::decide));
        verify(f.mapper).advanceTaskHead(any()); verify(f.confirmations).requireUnchanged(f.admission,prepared);
        verify(f.mapper,never()).completeOperation(any()); verify(f.permissions,never()).saveEvent(any(),any(),any(),any(),any(),any());
    }

}
