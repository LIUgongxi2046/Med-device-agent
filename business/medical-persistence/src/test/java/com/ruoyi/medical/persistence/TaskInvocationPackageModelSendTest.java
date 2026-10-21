package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskProfessionalPromptV2Test.id;

/** Fixed-source requirements are shared by legitimate V1 and V2 TASK packages.
 * These data checks do not grant model.send or replace ArtifactSourceAuthority. */
class TaskInvocationPackageModelSendTest {
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final TaskNodeInputProtocol.SourceSelection fixed=new TaskNodeInputProtocol.SourceSelection(id(4),id(5),id(6),id(7),id(8),id(9),"DOCUMENT_BODY");
    SourceReadProtocol.SourceRow source;SourceReadProtocol.SelectionRow selected;SourceReadProtocol.RightsRow rights;
    @BeforeEach void setup(){
        when(input.tenantId()).thenReturn(id(1));when(input.projectId()).thenReturn(id(2));when(input.snapshotId()).thenReturn(id(3));when(input.fixedSources()).thenReturn(List.of(fixed));
        source=new SourceReadProtocol.SourceRow(id(1),id(10),id(4),id(2),"PROJECT","INTERNAL",id(9),"a".repeat(64),"a".repeat(64),id(2));
        selected=new SourceReadProtocol.SelectionRow(id(1),id(2),id(3),id(10),id(4),id(5),id(6),id(7),id(8),"COMPLETE","READY",BigInteger.ONE,BigInteger.ONE,id(7),"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE,"APPROVE","b".repeat(64),"APPLICABLE","ACTIVE");
        rights=new SourceReadProtocol.RightsRow(id(1),id(9),id(4),"DOCUMENT_BODY","[]","[]","[]",LocalDateTime.MIN,LocalDateTime.MAX,"APPROVED","{}","c".repeat(64),"1.0.0","d".repeat(64),"INTERNAL","c".repeat(64),id(2));
    }
    @Test void legitimateV1SourcePackageGetsExactRequiredSnapshotAndParseNotNullGuess()throws Exception{
        var requirement=TaskInvocationPackages.modelRequirement(source,input,"DOCUMENT_BODY","LOCAL","LOCAL","provider-test");
        assertEquals(SourceReadProtocol.Use.MODEL_SEND,requirement.use());assertEquals(id(3),requirement.snapshotId());assertEquals(id(5),requirement.parseVersion());assertEquals(id(9),requirement.rightsRevision());assertEquals("provider-test",requirement.provider());
        assertDoesNotThrow(()->TaskInvocationPackages.requireModelSelection(input,new SourceReadProtocol.VerifiedSource(source,rights,selected,null,null,null,null),"DOCUMENT_BODY"));verify(input,atLeastOnce()).requireUnchanged();
    }
    @Test void missingOrDifferentFixedSourceOrPurposeIsDenied(){
        when(input.fixedSources()).thenReturn(List.of());assertThrows(TenantAccessException.class,()->TaskInvocationPackages.modelRequirement(source,input,"DOCUMENT_BODY","LOCAL","LOCAL","provider-test"));
        when(input.fixedSources()).thenReturn(List.of(fixed));assertThrows(TenantAccessException.class,()->TaskInvocationPackages.modelRequirement(source,input,"UNRELATED_PURPOSE","LOCAL","LOCAL","provider-test"));
    }
    @Test void wrongRightsOrCrossTenantSourceCannotBorrowInputPins(){
        var wrong=new SourceReadProtocol.SourceRow(id(1),id(10),id(4),id(2),"PROJECT","INTERNAL",id(90),source.originalSha256(),source.originalSha256(),id(2));
        assertThrows(TenantAccessException.class,()->TaskInvocationPackages.modelRequirement(wrong,input,"DOCUMENT_BODY","LOCAL","LOCAL","provider-test"));
        when(input.tenantId()).thenReturn(id(99));assertThrows(TenantAccessException.class,()->TaskInvocationPackages.modelRequirement(source,input,"DOCUMENT_BODY","LOCAL","LOCAL","provider-test"));
    }
    @Test void staleSelectionVersionIndexApprovalBindingSnapshotOrRightsIsDenied()throws Exception{
        for(String field:List.of("snapshotId","parseVersion","indexVersion","approvalRevision","bindingRevision","rightsRevision")){
            var selection=mock(SourceReadProtocol.SelectionRow.class,invocation->{try{return invocation.getMethod().invoke(selected,invocation.getArguments());}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
            var currentRights=rights;
            switch(field){case "snapshotId"->when(selection.snapshotId()).thenReturn(id(90));case "parseVersion"->when(selection.parseVersion()).thenReturn(id(90));case "indexVersion"->when(selection.indexVersion()).thenReturn(id(90));case "approvalRevision"->when(selection.approvalRevision()).thenReturn(id(90));case "bindingRevision"->when(selection.bindingRevision()).thenReturn(id(90));default->currentRights=new SourceReadProtocol.RightsRow(id(1),id(90),id(4),"DOCUMENT_BODY","[]","[]","[]",LocalDateTime.MIN,LocalDateTime.MAX,"APPROVED","{}","c".repeat(64),"1.0.0","d".repeat(64),"INTERNAL","c".repeat(64),id(2));}
            var value=new SourceReadProtocol.VerifiedSource(source,currentRights,selection,null,null,null,null);assertThrows(TenantAccessException.class,()->TaskInvocationPackages.requireModelSelection(input,value,"DOCUMENT_BODY"),field);
        }
    }
    @Test void missingCurrentSelectionOrRevokedInputDenies()throws Exception{
        assertThrows(TenantAccessException.class,()->TaskInvocationPackages.requireModelSelection(input,new SourceReadProtocol.VerifiedSource(source,rights,null,null,null,null,null),"DOCUMENT_BODY"));
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(input).requireUnchanged();assertThrows(TenantAccessException.class,()->TaskInvocationPackages.modelRequirement(source,input,"DOCUMENT_BODY","LOCAL","LOCAL","provider-test"));
    }
}
