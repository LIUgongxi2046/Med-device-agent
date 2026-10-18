package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual service/schema; isolated persistence and transaction rollback model. No database execution claimed. */
class DocumentReviewReturnServiceTest {
    static final String TENANT=id(1),ACTOR=id(2),PROJECT=id(3),DOCUMENT=id(4),REVISION=id(5),TASK=id(6),TASKREV=id(7),CHECK=id(8),ASSIGNEE=id(9);
    static final String HASH="a".repeat(64),KEY="review-return-key-000001";
    EnterpriseAccess access; PermissionSchema json; DocumentReviewReturnSchema schema; DocumentReviewReturnService service;
    DocumentReviewReturnMapper mapper; PermissionMapper permissions; ResourceReadAuthority authority;
    TaskPlanService plans; PermissionAuthority targetAuthority; DocumentReadService documents; ApplicationSessionIdentity.Verified subject;
    DocumentReadService.FixedRead fixed; DocumentReadProtocol.Header header;
    List<String> checks; Map<String,Object> saved; SelectionOperationRow operation; String audit,event;
    String taskStatus="REVIEW_PENDING"; boolean failCas,failEvent; int writes;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        json=new PermissionSchema(); schema=new DocumentReviewReturnSchema(json);
        mapper=mock(DocumentReviewReturnMapper.class); permissions=mock(PermissionMapper.class); authority=mock(ResourceReadAuthority.class);
        plans=mock(TaskPlanService.class); targetAuthority=mock(PermissionAuthority.class); documents=mock(DocumentReadService.class); subject=mock(ApplicationSessionIdentity.Verified.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR,"fixture","Fixture","USER"));
        var context=new BusinessSessionProtocol.Context(TENANT,"1","1","1");
        var admission=new EnterpriseAccess.Admission(subject,id(10),context,new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        access=mock(EnterpriseAccess.class);
        when(access.execute(eq(subject),any())).thenAnswer(call->{
            var oldSaved=saved; var oldOperation=operation; var oldAudit=audit; var oldEvent=event; int oldWrites=writes;
            try { return ((Function<EnterpriseAccess.Admission,Object>)call.getArgument(1)).apply(admission); }
            catch(RuntimeException failure) { saved=oldSaved;operation=oldOperation;audit=oldAudit;event=oldEvent;writes=oldWrites;throw failure; }
        });
        fixed=mock(DocumentReadService.FixedRead.class); header=mock(DocumentReadProtocol.Header.class);
        when(fixed.header()).thenReturn(header); when(fixed.digest()).thenReturn(HASH);
        when(fixed.watermarks()).thenReturn(new DocumentReadProtocol.Watermarks(BigInteger.ONE,BigInteger.ONE));
        when(header.tenantId()).thenReturn(TENANT);when(header.projectId()).thenReturn(PROJECT);when(header.documentId()).thenReturn(DOCUMENT);
        when(header.documentRevision()).thenReturn(REVISION);when(header.currentDocumentRevision()).thenReturn(REVISION);
        when(header.revision()).thenReturn(BigInteger.ONE);when(header.status()).thenReturn("REVIEW_PENDING");when(header.projectStatus()).thenReturn("ACTIVE");
        when(header.taskId()).thenReturn(TASK);when(header.taskRevision()).thenReturn(TASKREV);when(header.bodySha256()).thenReturn(HASH);
        when(header.classification()).thenReturn("INTERNAL");
        when(documents.readFixed(subject,"1",DOCUMENT,REVISION)).thenReturn(fixed);
        var grant=mock(PermissionProtocol.GrantRow.class);when(grant.grantId()).thenReturn(id(11));when(grant.revision()).thenReturn(BigInteger.ONE);
        when(authority.require(any(),any(),anyString(),anyString())).thenReturn(grant);
        when(plans.creationOwner(any(),eq(PROJECT),eq(TASK),eq(ASSIGNEE))).thenReturn(grant);
        when(permissions.targetBinding(ASSIGNEE)).thenReturn(List.of(new PermissionProtocol.BindingRow(ASSIGNEE,id(30),"local",null,null,ASSIGNEE,"ACTIVE")));
        when(targetAuthority.requireResource(any(),anyString(),anyString(),anyString(),anyString(),anyList(),any())).thenReturn(grant);
        when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,12,0,0));
        checks=List.of(json.canonical(check(true)));
        when(mapper.task(anyMap())).thenAnswer(call->List.of(new DocumentReviewReturnProtocol.Task(TASK,TASKREV,taskStatus,BigInteger.ONE)));
        when(mapper.checks(anyMap())).thenAnswer(call->checks);when(mapper.confirmations(anyMap())).thenReturn(List.of(id(12)));when(mapper.invalidations(anyMap())).thenReturn(List.of(id(12)));
        when(mapper.operation(anyMap())).thenAnswer(call->operation==null?List.of():List.of(operation));
        when(mapper.saveOperation(anyMap())).thenAnswer(call->{
            saved=new HashMap<>((Map<String,Object>)call.getArgument(0));writes++;
            operation=new SelectionOperationRow(str("operationId"),str("requestHash"),"COMPLETED",200,str("response"),DOCUMENT,"1.0.0",schema.manifestSha256());return 1;
        });
        
        when(mapper.insertDecision(anyMap())).thenAnswer(c->{writes++;return 1;});when(mapper.insertAssignment(anyMap())).thenAnswer(c->{writes++;return 1;});
        when(mapper.invalidate(anyMap())).thenAnswer(c->{writes++;return 1;});when(mapper.advanceDocument(anyMap())).thenReturn(1);
        when(mapper.advanceTask(anyMap())).thenAnswer(c->failCas?0:1);
        when(mapper.stored(anyMap())).thenAnswer(c->List.of(new DocumentReviewReturnProtocol.Stored(str("assignmentId"),str("returnId"),str("operationId"),str("request"),str("requestHash"),str("response"),schema.manifestSha256())));
        when(mapper.decision(anyMap())).thenAnswer(c->List.of(json.canonical(schema.read(str("response")).required("data").required("decision"))));
        when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(c->{audit=c.getArgument(4);return 1;});
        when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(c->{if(failEvent)throw new IllegalStateException("rollback fixture");event=c.getArgument(5);return 1;});
        when(mapper.historyOperation(anyMap())).thenAnswer(c->operation==null?List.of():List.of(operation));
        when(mapper.historyAudit(anyMap())).thenAnswer(c->audit==null?List.of():List.of(audit));
        when(mapper.audit(anyMap())).thenAnswer(c->audit==null?List.of():List.of(audit));when(mapper.event(anyMap())).thenAnswer(c->event==null?List.of():List.of(event));
        service=new DocumentReviewReturnService(access,documents,mapper,authority,permissions,plans,targetAuthority,schema,json);
    }
    @Test void noChecksAllowsRevisionRequestWithoutAnyFormalReviewOrApproval() throws Exception {
        checks=List.of();var basis=basis();assertTrue(basis.required("return_available").booleanValue());
        assertFalse(basis.required("formal_review_available").booleanValue());assertEquals("NOT_VERIFIED",basis.required("formal_check_currentness").asText());
        assertTrue(basis.required("checks").isEmpty());
        assertNull(operation);assertEquals(0,writes);
    }
    @Test void failedChecksRemainInformationalAndNeverBecomeConfirmation() throws Exception {
        checks=List.of(json.canonical(check(false)));assertTrue(basis().required("return_available").booleanValue());
        assertFalse(basis().required("formal_review_available").booleanValue());assertEquals(0,writes);
    }
    @Test void realCompleteBaselineCreatesReturnAssignmentInvalidationAndOriginalReceipt() throws Exception {
        var request=request();var result=service.decide(subject,"1",KEY,DOCUMENT,request);
        assertEquals("RETURNED",result.required("data").required("task_status").asText());
        assertEquals("WAITING_INPUT",result.required("data").required("decision").required("assignment").required("status").asText());
        assertEquals(id(12),result.required("data").required("invalidated_review_ids").get(0).asText());
        verify(mapper).advanceDocument(anyMap());verify(mapper).advanceTask(anyMap());verify(mapper).insertAssignment(anyMap());
        assertEquals(4,writes);schema.receipt(json.canonical(result));
    }
    @Test void staleCheckSetRejectsBeforeAnyWrite() throws Exception {
        var request=request();var changed=check(true);changed.put("record_sha256","b".repeat(64));checks=List.of(json.canonical(changed));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
    }
    @Test void revokedReviewGrantRejectsBeforeAnyWrite() throws Exception {
        var request=request();when(authority.require(any(),any(),eq("review.decide"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
    }
    @Test void casFailureRollsBackOperationDecisionAssignmentAndAudit() throws Exception {
        var request=request();failCas=true;assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));
        assertNull(operation);assertNull(saved);assertNull(audit);assertEquals(0,writes);
    }
    @Test void outboxFailureRollsBackAllRecords() throws Exception {
        var request=request();failEvent=true;assertThrows(RuntimeException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertNull(operation);assertNull(saved);assertEquals(0,writes);
    }
    @Test void replayAndRecoveryAfterTaskTransitionKeepTheOriginalReceipt() throws Exception {
        var request=request();var result=service.decide(subject,"1",KEY,DOCUMENT,request);taskStatus="RETURNED";checks=List.of();
        assertEquals(result,service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(result,service.recover(subject,"1",KEY,DOCUMENT,REVISION));
        verify(mapper,times(1)).saveOperation(anyMap());assertEquals(4,writes);
    }
    @Test void changedRequestCannotReuseOriginalKey() throws Exception {
        var request=request();service.decide(subject,"1",KEY,DOCUMENT,request);
        var edited=(ObjectNode)schema.read(new String(request,java.nio.charset.StandardCharsets.UTF_8));
        edited.put("reason","不同理由");
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,json.canonical(edited).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        verify(mapper,times(1)).saveOperation(anyMap());
    }
    @Test void assignmentReadKeepsImmutableReceiptAndDerivesSupersessionWithoutCompletion() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());
        when(mapper.assignments(anyMap())).thenAnswer(c->mapper.stored(c.getArgument(0)));
        when(mapper.task(anyMap())).thenReturn(List.of(new DocumentReviewReturnProtocol.Task(TASK,TASKREV,"RETURNED",BigInteger.TWO)));
        var current=service.assignments(subject,"1",DOCUMENT,REVISION).required("data").required("items").get(0);
        assertEquals("OPEN",current.required("effective_state").asText());
        when(mapper.task(anyMap())).thenReturn(List.of(new DocumentReviewReturnProtocol.Task(TASK,TASKREV,"REVIEW_PENDING",BigInteger.valueOf(3))));
        var old=service.assignments(subject,"1",DOCUMENT,REVISION).required("data").required("items").get(0);
        assertEquals("INACTIVE",old.required("effective_state").asText());
        assertEquals(current.required("assignment"),old.required("assignment"));
        assertEquals("WAITING_INPUT",old.required("assignment").required("status").asText());
    }
    @Test void assigneeDirectoryUsesExactTaskEligibilityWithoutTaskCreationGrant() throws Exception {
        when(mapper.assigneeCandidates(anyMap())).thenReturn(List.of(new TenantMemberRow(TENANT,id(21),ASSIGNEE,"ACTIVE",LocalDateTime.of(2020,1,1,0,0),null,BigInteger.ONE),
                new TenantMemberRow(TENANT,id(22),id(23),"ACTIVE",LocalDateTime.of(2020,1,1,0,0),null,BigInteger.ONE)));
        when(plans.creationOwner(any(),eq(PROJECT),eq(TASK),eq(id(23)))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        var items=service.assignees(subject,"1",DOCUMENT,REVISION).required("data").required("items");
        assertEquals(1,items.size());assertEquals(ASSIGNEE,items.get(0).required("user_id").asText());assertTrue(items.get(0).required("display_name").isNull());
        verify(authority,never()).require(any(),any(),eq("task.create"),anyString());assertEquals(0,writes);
    }
    @Test void independentHistoryNeverPretendsToBeFormalReviewDecisions() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());
        when(mapper.assignments(anyMap())).thenAnswer(c->mapper.stored(c.getArgument(0)));
        var item=service.history(subject,"1",DOCUMENT,REVISION).required("data").required("items").get(0);
        assertEquals("RETURN_FOR_REVISION",item.required("decision").required("decision").asText());
        assertFalse(item.required("decision").has("review_id"));assertFalse(item.required("decision").has("batch_id"));
        assertEquals(id(12),item.required("invalidated_review_ids").get(0).asText());
    }
    @Test void tamperedPersistedDecisionCannotRecoverOriginalOperation() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());when(mapper.decision(anyMap())).thenReturn(List.of("{}"));
        assertThrows(TenantAccessException.class,()->service.recover(subject,"1",KEY,DOCUMENT,REVISION));
    }
    @Test void databaseCurrentFlagCannotPromoteFailedCheckToEligibility() throws Exception {
        var invalid=check(true);invalid.put("status","FAILED");checks=List.of(json.canonical(invalid));
        assertThrows(TenantAccessException.class,()->basis());assertEquals(0,writes);
    }
    @Test void noChecksStillPersistsOnlyIndependentRevisionRequestAndAssignment() throws Exception {
        checks=List.of();var response=service.decide(subject,"1",KEY,DOCUMENT,request()).required("data");
        assertEquals("RETURN_FOR_REVISION",response.required("decision").required("decision").asText());
        assertFalse(response.required("decision").has("review_id"));assertFalse(response.required("decision").has("batch_id"));
        assertTrue(response.required("decision").required("baseline_manifest").required("checks").isEmpty());assertEquals(4,writes);
    }
    @Test void signedDocumentCannotBeReopenedEvenWhenTaskHeadStillSaysPending() throws Exception {
        var request=request();when(header.status()).thenReturn("SIGNED");
        var failure=assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));
        assertEquals(ErrorCode.DOCUMENT_SIGNED,failure.code());assertEquals(0,writes);
    }
    @Test void actualSignatureBlocksReturnEvenWithUnsignedHeader() throws Exception {
        var request=request();when(mapper.signatures(anyMap())).thenReturn(List.of(id(40)));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
    }
    @Test void terminalAndRunningTaskStatesCannotBeReopenedByReviewPermission() throws Exception {
        var request=request();for(String status:List.of("SIGNED","CANCELLED","CANCEL_REQUESTED","FAILED","RUNNING","READY","SIGN_PENDING")) {
            taskStatus=status;assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
        }
    }
    @Test void unreadableRecipientCannotReceiveAssignment() throws Exception {
        var request=request();when(targetAuthority.requireResource(any(),eq(ASSIGNEE),anyString(),eq("document.read"),anyString(),anyList(),any()))
                .thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
    }
    @Test void recipientRevocationAtCommitRollsBackIndependentDecisionAndAssignment() throws Exception {
        var request=request();var valid=targetAuthority.requireResource(new PermissionProtocol.Scope(TENANT,PROJECT,"TASK",TASK),ASSIGNEE,"local","work_item.read","WORK_ITEM_READING",List.of(),LocalDateTime.of(2026,10,18,12,0));
        when(targetAuthority.requireResource(any(),eq(ASSIGNEE),anyString(),eq("work_item.read"),anyString(),anyList(),any()))
                .thenReturn(valid).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);assertNull(operation);
    }
    @Test void newBodyIsRevisionAvailableNeverAutomaticallyCompletedOrResubmitted() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());when(mapper.assignments(anyMap())).thenAnswer(c->mapper.stored(c.getArgument(0)));
        when(mapper.task(anyMap())).thenReturn(List.of(new DocumentReviewReturnProtocol.Task(TASK,TASKREV,"RETURNED",BigInteger.TWO)));
        when(header.currentDocumentRevision()).thenReturn(id(41));
        var item=service.assignments(subject,"1",DOCUMENT,REVISION).required("data").required("items").get(0);
        assertEquals("REVISION_AVAILABLE",item.required("effective_state").asText());assertFalse(item.required("resubmission_available").booleanValue());
        assertEquals("CURRENT_CHECK_VERIFIER_UNAVAILABLE",item.required("resubmission_blocker").asText());
        assertEquals("WAITING_INPUT",item.required("assignment").required("status").asText());
    }
    @Test void actualPermissionAuthorityRequiresExactRecipientDocumentAndWorkScope() throws Exception {
        var request=request();service=new DocumentReviewReturnService(access,documents,mapper,authority,permissions,plans,new PermissionAuthority(json),schema,json);
        doAnswer(c->{Map<String,Object> values=c.getArgument(0);String action=values.get("targetAction").toString();
            return List.of(recipientGrant(action,"document.read".equals(action)?id(90):TASK));}).when(mapper).targetGrants(anyMap());
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
        doAnswer(c->{Map<String,Object> values=c.getArgument(0);String action=values.get("targetAction").toString();
            return "document.read".equals(action)?List.of(recipientGrant(action,DOCUMENT)):List.of();}).when(mapper).targetGrants(anyMap());
        assertThrows(TenantAccessException.class,()->service.decide(subject,"1",KEY,DOCUMENT,request));assertEquals(0,writes);
        doAnswer(c->{Map<String,Object> values=c.getArgument(0);String action=values.get("targetAction").toString();
            return List.of(recipientGrant(action,"document.read".equals(action)?DOCUMENT:TASK));}).when(mapper).targetGrants(anyMap());
        assertEquals("RETURNED",service.decide(subject,"1",KEY,DOCUMENT,request).required("data").required("task_status").asText());assertEquals(4,writes);
    }
    @Test void readOnlyOriginalKeyRecoverySurvivesLostReviewWritePermission() throws Exception {
        var receipt=service.decide(subject,"1",KEY,DOCUMENT,request());
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).require(any(),any(),eq("review.decide"),eq("DOCUMENT_REVIEW"));
        assertEquals(receipt,service.recover(subject,"1",KEY,DOCUMENT,REVISION));
    }
    @Test void independentHistoryRejectsMissingOrCorruptedOriginalLedger() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());
        when(mapper.assignments(anyMap())).thenAnswer(c->mapper.stored(c.getArgument(0)));
        assertEquals(1,service.history(subject,"1",DOCUMENT,REVISION).required("data").required("items").size());
        when(mapper.historyOperation(anyMap())).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->service.history(subject,"1",DOCUMENT,REVISION));
        when(mapper.historyOperation(anyMap())).thenAnswer(c->List.of(operation));
        when(mapper.historyAudit(anyMap())).thenReturn(List.of("{}"));
        assertThrows(RuntimeException.class,()->service.history(subject,"1",DOCUMENT,REVISION));
    }
    @Test void completeHistoricalAuditAuthorityWitnessIsImmutableButNotComparedWithCurrentGrants() throws Exception {
        var receipt=service.decide(subject,"1",KEY,DOCUMENT,request());String original=audit;
        for(String field:List.of("schema_version","authority_grant_id","authority_revision","assignee_authority_sha256","authority_witness_sha256","authority_witness")){
            var changed=(ObjectNode)schema.read(original);changed.remove(field);audit=json.canonical(changed);
            assertThrows(TenantAccessException.class,()->service.recover(subject,"1",KEY,DOCUMENT,REVISION),field);audit=original;
        }
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).require(any(),any(),eq("review.decide"),anyString());
        assertEquals(receipt,service.recover(subject,"1",KEY,DOCUMENT,REVISION));
    }
    @Test void changedInternalWitnessAndRecomputedDigestStillCannotMatchOriginalAudit() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());var original=saved.get("request");var changed=(ObjectNode)schema.read(original.toString());
        ((ObjectNode)changed.required("authority_witness").required("review")).put("revision","999");
        changed.put("authority_witness_sha256",schema.hash(changed.required("authority_witness")));saved.put("request",json.canonical(changed));
        assertThrows(TenantAccessException.class,()->service.recover(subject,"1",KEY,DOCUMENT,REVISION));saved.put("request",original);
    }
    @Test void receiptMustMatchActualOriginalReviewInvalidationsExactly() throws Exception {
        service.decide(subject,"1",KEY,DOCUMENT,request());
        for(var broken:List.of(List.<String>of(),List.of(id(99)),List.of(id(12),id(12)),List.of(id(12),id(99)))){
            when(mapper.invalidations(anyMap())).thenReturn(broken);
            assertThrows(TenantAccessException.class,()->service.recover(subject,"1",KEY,DOCUMENT,REVISION));
        }
        when(mapper.invalidations(anyMap())).thenReturn(List.of(id(12)));
        assertNotNull(service.recover(subject,"1",KEY,DOCUMENT,REVISION));
    }
    private PermissionProtocol.GrantRow recipientGrant(String action,String resource) throws Exception {
        boolean document="document.read".equals(action);
        String condition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),
                List.of(document?"DOCUMENT_BODY":"WORK_ITEM_READING"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null)));
        return new PermissionProtocol.GrantRow(document?id(70):id(71),ASSIGNEE,PROJECT,document?"DOCUMENT":"TASK",resource,action,condition,
                "ACTIVE",LocalDateTime.of(2020,1,1,0,0),null,id(72),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    private JsonNode basis() throws Exception {return service.basis(subject,"1",DOCUMENT,REVISION).required("data");}
    private byte[] request() throws Exception {
        var basis=basis();var request=json.object().put("document_revision",REVISION).put("expected_revision",basis.required("revision").asText())
                .put("expected_task_revision",basis.required("task_head_revision").asText()).put("reason","缺乏证据，退回修改")
                .put("assignee_id",ASSIGNEE);request.set("basis",basis);
        return json.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private ObjectNode check(boolean current) {return json.object().put("record_sha256",HASH).put("check_id",CHECK).put("status",current?"SUCCEEDED":"FAILED")
            .put("record_complete",current).put("input_sha256",HASH).put("rule_manifest_id",id(13)).put("evidence_watermark","1").put("issue_watermark","1")
            .putNull("invalidation_reason").put("coverage_artifact_id",id(14)).put("findings_artifact_id",id(15));}
    private String str(String key) {return saved.get(key).toString();}
    static String id(int n) {return DocumentReviewReturnSchemaTest.id(n);}
}
