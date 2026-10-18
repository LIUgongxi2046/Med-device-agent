package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.ruoyi.medical.persistence.DocumentReviewReturnServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentRevisionAssignmentQueueServiceTest {
    DocumentReviewReturnServiceTest f;
    DocumentRevisionAssignmentQueueService queue;
    DocumentReadService.MetadataRead proof;
    DocumentReviewReturnProtocol.QueueCandidate candidate;
    @BeforeEach void setup() throws Exception {
        f=new DocumentReviewReturnServiceTest(); f.setup();
        var basis=f.service.basis(f.subject,"1",DOCUMENT,REVISION).required("data");
        var request=f.json.object().put("document_revision",REVISION).put("expected_revision","1")
                .put("expected_task_revision","1").put("reason","请修改文稿").put("assignee_id",ASSIGNEE);
        request.set("basis",basis);
        f.service.decide(f.subject,"1",KEY,DOCUMENT,f.json.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Authenticate the actual recipient for reads, preserving the real reviewer receipt above.
        when(f.subject.user()).thenReturn(new BusinessSessionProtocol.User(ASSIGNEE,"recipient","Recipient","USER"));
        when(f.subject.sessionId()).thenReturn(id(81));
        proof=mock(DocumentReadService.MetadataRead.class); when(proof.header()).thenReturn(f.header);
        when(f.documents.authorizeMetadata(any(),eq("1"),eq(DOCUMENT),eq(REVISION))).thenReturn(proof);
        f.taskStatus="RETURNED";
        candidate=new DocumentReviewReturnProtocol.QueueCandidate(f.saved.get("assignmentId").toString(),PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ASSIGNEE,
                f.saved.get("returnId").toString(),f.saved.get("operationId").toString());
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of(candidate));
        queue=new DocumentRevisionAssignmentQueueService(f.access,f.documents,f.authority,f.mapper,f.service,f.schema,
                new DocumentRevisionAssignmentQueueSchema(f.json),new DocumentRevisionAssignmentCursor(),f.json);
        clearInvocations(f.documents,f.mapper);
    }
    @Test void ownQueueValidatesActualDecisionAndFixedMetadataWithoutDownloadingBody() throws Exception {
        var result=queue.read(f.subject,"1",10,null).required("data");
        assertEquals(ASSIGNEE,result.required("actor_id").asText()); assertEquals(1,result.required("items").size());
        assertTrue(result.required("complete").booleanValue()); assertTrue(result.required("next_cursor").isNull());
        assertEquals(PROJECT,result.required("items").get(0).required("project_id").asText());
        verify(f.documents,times(1)).authorizeMetadata(any(),eq("1"),eq(DOCUMENT),eq(REVISION));
        verify(f.documents,times(1)).requireMetadataCurrent(any(),eq(proof));
        verify(f.documents,never()).readFixed(any(),anyString(),anyString(),anyString());
        verify(f.mapper).myAssignments(argThat(v->ASSIGNEE.equals(v.get("userId")) && Integer.valueOf(11).equals(v.get("fetchLimit"))));
    }
    @Test void revokedPermissionIsOmittedAndEmptyPageCanStillHaveOpaqueNextCursor() throws Exception {
        var next=new DocumentReviewReturnProtocol.QueueCandidate("ffffffff-ffff-ffff-ffff-ffffffffffff",PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ASSIGNEE,candidate.returnId(),candidate.operationId());
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of(candidate,next));
        when(f.authority.require(any(),any(),eq("task.read"),eq("TASK_READING"))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        var first=queue.read(f.subject,"1",1,null).required("data");
        assertTrue(first.required("items").isEmpty()); assertFalse(first.required("complete").booleanValue());
        assertNotEquals(candidate.assignmentId(),first.required("next_cursor").asText());
        verify(f.mapper,never()).stored(anyMap()); verifyNoInteractions(f.documents);
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of());
        assertTrue(queue.read(f.subject,"1",1,first.required("next_cursor").asText()).required("data").required("complete").booleanValue());
    }
    @Test void unavailableSourceIsOmittedWithoutBlockingLaterCandidateOrExpandingBoundedScan() throws Exception {
        var unavailable=new DocumentReviewReturnProtocol.QueueCandidate("00000000-0000-0000-0000-000000000001",PROJECT,id(99),REVISION,TASK,TASKREV,ASSIGNEE,candidate.returnId(),candidate.operationId());
        var lookahead=new DocumentReviewReturnProtocol.QueueCandidate("ffffffff-ffff-ffff-ffff-ffffffffffff",PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ASSIGNEE,candidate.returnId(),candidate.operationId());
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of(unavailable,candidate,lookahead));
        for(var code:List.of(ErrorCode.SOURCE_NOT_READY,ErrorCode.SOURCE_MISSING)){
            when(f.documents.authorizeMetadata(any(),eq("1"),eq(id(99)),eq(REVISION))).thenThrow(new TenantAccessException(code));
            clearInvocations(f.mapper,f.documents);
            var page=queue.read(f.subject,"1",2,null).required("data");assertEquals(1,page.required("items").size());assertFalse(page.required("complete").booleanValue());assertTrue(page.required("next_cursor").isTextual());
            assertFalse(page.toString().contains(id(99)));assertFalse(page.toString().contains(code.name()));
            verify(f.mapper,times(1)).myAssignments(argThat(v->Integer.valueOf(3).equals(v.get("fetchLimit"))));verify(f.mapper,times(1)).stored(anyMap());
            verify(f.documents,times(2)).authorizeMetadata(any(),eq("1"),anyString(),eq(REVISION));verify(f.documents,never()).readFixed(any(),anyString(),anyString(),anyString());
        }
    }
    @Test void genericUnavailableAndCorruptionAreNotSilentlyHidden() throws Exception {
        for(var code:List.of(ErrorCode.OBJECT_UNAVAILABLE,ErrorCode.CONTENT_MISMATCH)){
            when(f.documents.authorizeMetadata(any(),eq("1"),eq(DOCUMENT),eq(REVISION))).thenThrow(new TenantAccessException(code));
            assertEquals(code,assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",2,null)).code());
        }
    }
    @Test void changedSourceOrDocumentWatermarkRejectsWholeReturnedPage() throws Exception {
        doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.documents).requireMetadataCurrent(any(),eq(proof));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",10,null)).code());
    }
    @Test void permissionRevokedAfterMetadataReadCannotReturnEarlierItem() throws Exception {
        var grant=f.authority.require(null,null,"task.read","TASK_READING");
        doReturn(grant).doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.authority).require(any(),any(),eq("task.read"),eq("TASK_READING"));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",10,null)).code());
    }
    @Test void corruptedJoinedDecisionAndOtherAssigneeAreRejected() {
        when(f.mapper.decision(anyMap())).thenReturn(List.of("{}"));
        assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",10,null));
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of(new DocumentReviewReturnProtocol.QueueCandidate(candidate.assignmentId(),PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ACTOR,candidate.returnId(),candidate.operationId())));
        assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",10,null));
    }
    @Test void metadataProofReusedWithinPageButEveryDecisionReadBack() throws Exception {
        // A duplicate fixed document must not cause a second authorization/download; a mismatched ledger is still rejected.
        var second=new DocumentReviewReturnProtocol.QueueCandidate("ffffffff-ffff-ffff-ffff-ffffffffffff",PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ASSIGNEE,candidate.returnId(),candidate.operationId());
        when(f.mapper.myAssignments(anyMap())).thenReturn(List.of(candidate,second));
        assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",10,null));
        verify(f.documents,times(1)).authorizeMetadata(any(),eq("1"),eq(DOCUMENT),eq(REVISION));
        verify(f.mapper,times(2)).stored(anyMap());
    }
    @Test void maximumPageExaminesOnlyTwentyCandidatesAndNeverDownloadsBody() throws Exception {
        var rows=new ArrayList<DocumentReviewReturnProtocol.QueueCandidate>();
        for(int i=100;i<121;i++) rows.add(new DocumentReviewReturnProtocol.QueueCandidate(id(i),PROJECT,DOCUMENT,REVISION,TASK,TASKREV,ASSIGNEE,candidate.returnId(),candidate.operationId()));
        when(f.mapper.myAssignments(anyMap())).thenReturn(rows);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.authority).require(any(),any(),eq("task.read"),eq("TASK_READING"));
        var page=queue.read(f.subject,"1",20,null).required("data");
        assertTrue(page.required("items").isEmpty());assertFalse(page.required("complete").booleanValue());
        verify(f.authority,times(20)).require(any(),any(),eq("task.read"),eq("TASK_READING"));
        verifyNoInteractions(f.documents);
        verify(f.mapper).myAssignments(argThat(v->Integer.valueOf(21).equals(v.get("fetchLimit"))));
    }
    @Test void inputAndOversizedMapperPageFailClosedBeforeUnboundedRead() {
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->queue.read(f.subject,"1",21,null));
        when(f.mapper.myAssignments(anyMap())).thenReturn(Collections.nCopies(22,candidate));
        assertEquals(ErrorCode.CONTEXT_LIMIT,assertThrows(TenantAccessException.class,()->queue.read(f.subject,"1",20,null)).code());
        verifyNoInteractions(f.documents);
    }
}
