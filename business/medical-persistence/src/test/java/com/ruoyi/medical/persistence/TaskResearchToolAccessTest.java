package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real bounded orchestration with explicit token/input-authority and physical transport doubles.
 * Does not claim deployed service identity, database, provider or S3 acceptance. */
class TaskResearchToolAccessTest {
    final FormalWorkerAccess workers=mock(FormalWorkerAccess.class);
    final TaskNodeInputAuthority inputs=mock(TaskNodeInputAuthority.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final RetrievalService retrieval=mock(RetrievalService.class);
    final SourceReadAuthority sources=mock(SourceReadAuthority.class);
    final SimulationFixtureMapper originals=mock(SimulationFixtureMapper.class);
    final SourceUploadTransport transport=mock(SourceUploadTransport.class);
    final RetrievalPolicy policy=mock(RetrievalPolicy.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);
    final HttpServletRequest http=mock(HttpServletRequest.class);
    final TaskResearchToolAccess tools=new TaskResearchToolAccess(workers,inputs,retrieval,sources,originals,transport,policy);
    final String tenant=id(1),project=id(2),actor=id(3),run=id(4),step=id(5),question=id(6),source=id(7),snapshot=id(8);
    final TaskNodeInputProtocol.SourceSelection selected=new TaskNodeInputProtocol.SourceSelection(source,id(9),id(10),id(11),id(12),id(13),"DOCUMENT_BODY");
    final AtomicInteger admissions=new AtomicInteger();
    TaskResearchToolAccessTest() throws Exception {
        when(admission.formalWorker()).thenReturn(true);when(admission.formalProof().identity().executionKind()).thenReturn("TASK");
        when(admission.actorId()).thenReturn(actor);
        when(inputs.require(admission,run,step)).thenReturn(input);
        when(input.tenantId()).thenReturn(tenant);when(input.projectId()).thenReturn(project);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);
        when(input.inputId()).thenReturn(id(14));when(input.receiptSha256()).thenReturn("a".repeat(64));when(input.inputSha256()).thenReturn("b".repeat(64));
        when(input.graphId()).thenReturn(id(15));when(input.graphSha256()).thenReturn("c".repeat(64));when(input.snapshotId()).thenReturn(snapshot);
        when(input.questionId()).thenReturn(question);when(input.questionText()).thenReturn("Confirmed question?");when(input.fixedSources()).thenReturn(List.of(selected));
        when(policy.environment()).thenReturn("TEST");when(policy.region()).thenReturn("LOCAL");
        when(workers.executeAction(eq(http),eq("token-double"),any(),any())).thenAnswer(call->{
            admissions.incrementAndGet(); return ((BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,?>)call.getArgument(3)).apply(admission,null);
        });
    }
    RunTokenAuthority.ActionRequest action(String operation,String target) throws Exception {
        return new RunTokenAuthority.ActionRequest(run,step,id(16),BigInteger.ONE,"b".repeat(64),TaskResearchToolAccess.argumentsSha256(operation,target),
                "TOOL","fixed-tool",new PermissionProtocol.Scope(tenant,project,"SOURCE",source),"source.read","DOCUMENT_BODY");
    }
    SourceReadProtocol.VerifiedSource sourceProof(String hash) {
        var row=new SourceReadProtocol.SourceRow(tenant,id(17),source,project,"PROJECT","INTERNAL",selected.rightsRevision(),hash,hash,project);
        var selection=new SourceReadProtocol.SelectionRow(tenant,project,snapshot,id(17),source,selected.parseVersion(),selected.indexVersion(),selected.approvalRevision(),selected.bindingRevision(),
                "COMPLETE","READY",BigInteger.ONE,BigInteger.ONE,selected.approvalRevision(),"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE,"APPROVE","d".repeat(64),"APPLICABLE","ACTIVE");
        return new SourceReadProtocol.VerifiedSource(row,null,selection,null,null,null,null);
    }
    @Test void retrievalUsesOnlyVerifiedQuestionAndFixedSelections() throws Exception {
        when(sources.require(eq(admission),any())).thenReturn(sourceProof("e".repeat(64)));
        var response=JsonNodeFactory.instance.objectNode().put("actual","persisted-retrieval");
        when(retrieval.create(eq(admission),eq("0"),anyString(),any(),anyString(),any())).thenAnswer(call->{
            RetrievalProtocol.SearchInput query=call.getArgument(3);TaskResearchToolAccess.Fixed fixed=call.getArgument(5);
            assertEquals("Confirmed question?",query.query());assertEquals(List.of(selected),fixed.sources());return response;
        });
        var handle=tools.open(http,"token-double",action("AT-01:create",question));
        assertEquals(response,tools.createRetrieval(handle));assertTrue(admissions.get()>=2);
        verify(inputs,atLeast(3)).require(admission,run,step);verify(input,atLeast(3)).requireUnchanged();
    }
    @Test void changedReceiptAndForeignSourceFailBeforePhysicalRead() throws Exception {
        var handle=tools.open(http,"token-double",action("AT-02:source",source));
        when(input.receiptSha256()).thenReturn("f".repeat(64));
        assertThrows(TenantAccessException.class,()->tools.readSource(handle,source));verifyNoInteractions(transport);
        when(input.receiptSha256()).thenReturn("a".repeat(64));
        var foreign=tools.open(http,"token-double",action("AT-02:source",id(99)));
        assertThrows(TenantAccessException.class,()->tools.readSource(foreign,id(99)));verifyNoInteractions(transport);
    }
    @Test void actionArgumentsCannotBeReusedForAnotherOperation() throws Exception {
        var handle=tools.open(http,"token-double",action("AT-01:create",question));
        assertThrows(TenantAccessException.class,()->tools.readSource(handle,source));verifyNoInteractions(transport,sources,retrieval);
    }
    @Test void sourceReadsActualBinaryAndRechecksBeforeEachSegmentAndConsumption() throws Exception {
        byte[] bytes=new byte[]{0,1,(byte)255,9};String hash=WireValues.sha256(bytes);
        when(sources.require(eq(admission),any())).thenReturn(sourceProof(hash));
        var original=new SimulationInvocationSources.Original(tenant,source,hash,"tenants/"+tenant+"/source/original","v1",BigInteger.valueOf(bytes.length),"application/pdf");
        when(originals.original(anyMap())).thenReturn(List.of(original));
        var reader=mock(S3ObjectReader.class);var object=mock(S3ObjectReader.VerifiedObject.class);
        when(object.reference()).thenReturn(original.reference());when(object.fullSha256()).thenReturn(hash);when(object.chunk(0,bytes.length)).thenReturn(bytes);
        when(reader.readVerified(eq(original.reference()),any())).thenReturn(object);
        when(transport.readTaskDocument(eq(tenant),eq(actor),anyInt(),any(),any())).thenAnswer(call->{
            S3ObjectReader.Admission guard=call.getArgument(3);
            var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(10);}};
            return ((SourceUploadTransport.DocumentRead<?>)call.getArgument(4)).apply(reader,timed);
        });
        var handle=tools.open(http,"token-double",action("AT-02:source",source));var result=tools.readSource(handle,source);
        assertArrayEquals(bytes,result.bytes());assertTrue(admissions.get()>=7);verify(object).close();
        result.bytes()[0]=100;assertEquals(0,result.bytes()[0]);
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(inputs).require(admission,run,step);
        assertThrows(TenantAccessException.class,result::bytes);
    }
    @Test void revocationDuringPhysicalReadPreventsReturningBytes() throws Exception {
        byte[] bytes={1,2,3};String hash=WireValues.sha256(bytes);
        when(sources.require(eq(admission),any())).thenReturn(sourceProof(hash));
        var original=new SimulationInvocationSources.Original(tenant,source,hash,"tenants/"+tenant+"/original","v1",BigInteger.valueOf(bytes.length),"application/pdf");
        when(originals.original(anyMap())).thenReturn(List.of(original));
        var reader=mock(S3ObjectReader.class);var object=mock(S3ObjectReader.VerifiedObject.class);
        when(object.reference()).thenReturn(original.reference());when(object.fullSha256()).thenReturn(hash);
        when(object.chunk(0,bytes.length)).thenAnswer(call->{
            when(input.receiptSha256()).thenReturn("f".repeat(64));return bytes;
        });
        when(reader.readVerified(eq(original.reference()),any())).thenReturn(object);
        when(transport.readTaskDocument(eq(tenant),eq(actor),anyInt(),any(),any())).thenAnswer(call->{
            S3ObjectReader.Admission guard=call.getArgument(3);
            var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(10);}};
            return ((SourceUploadTransport.DocumentRead<?>)call.getArgument(4)).apply(reader,timed);
        });
        var handle=tools.open(http,"token-double",action("AT-02:source",source));
        assertThrows(TenantAccessException.class,()->tools.readSource(handle,source));verify(object).close();
    }
    @Test void alteredFixedSelectionIsRejected() {
        var fixed=new TaskResearchToolAccess.Fixed(tenant,project,actor,snapshot,question,"Question",List.of(selected),"a".repeat(64));
        var hit=new RetrievalProtocol.Hit(BigInteger.ONE,id(17),source,selected.parseVersion(),id(99),id(18),selected.approvalRevision(),selected.rightsRevision(),"DOCUMENT_BODY","Title","Excerpt",false,"{}","PAGE","a".repeat(64),java.math.BigDecimal.ONE);
        assertThrows(TenantAccessException.class,()->fixed.require(hit));
    }
    static String id(int value){return String.format("10000000-0000-4000-8000-%012d",value);}
}
