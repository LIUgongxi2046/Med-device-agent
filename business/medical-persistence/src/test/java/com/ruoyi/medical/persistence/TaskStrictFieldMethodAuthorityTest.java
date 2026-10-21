package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TaskStrictFieldMethodAuthorityTest {
    private static final String ID="10000000-0000-4000-8000-000000000001";
    private static final PermissionProtocol.Basis CONTEXT=new PermissionProtocol.Basis(ID,"a".repeat(64));
    @Test void methodEvidenceRequiresRealContextAndBothOriginalSpans(){
        assertThrows(NullPointerException.class,()->new TaskStrictFieldTypedProtocol.MethodEvidence(ID,CONTEXT,new StrictComparisonProtocol.Span(0,4),"test",null,"ISO"));
        assertThrows(IllegalArgumentException.class,()->new TaskStrictFieldTypedProtocol.MethodEvidence(ID,CONTEXT,new StrictComparisonProtocol.Span(0,4),"test",new StrictComparisonProtocol.Span(5,8)," "));
    }
    @Test void fieldOnlyCannotSmuggleMethodCoverage(){
        var method=new TaskStrictFieldTypedProtocol.MethodEvidence(ID,CONTEXT,new StrictComparisonProtocol.Span(0,4),"test",new StrictComparisonProtocol.Span(5,8),"ISO");
        var indicator=new TaskStrictFieldTypedProtocol.Indicator(ID,List.of(method));
        assertThrows(IllegalArgumentException.class,()->new TaskStrictFieldTypedProtocol.RequirementBinding(ID,TaskStrictFieldTypedProtocol.RequirementKind.FIELD_ONLY,List.of(indicator)));
        assertThrows(IllegalArgumentException.class,()->new TaskStrictFieldTypedProtocol.RequirementBinding(ID,TaskStrictFieldTypedProtocol.RequirementKind.METHOD,List.of()));
    }
    @Test void methodIdsCannotCollapseMultipleEvidenceRows(){
        var method=new TaskStrictFieldTypedProtocol.MethodEvidence(ID,CONTEXT,new StrictComparisonProtocol.Span(0,4),"test",new StrictComparisonProtocol.Span(5,8),"ISO");
        assertThrows(IllegalArgumentException.class,()->new TaskStrictFieldTypedProtocol.Indicator(ID,List.of(method,method)));
    }
    @Test void committedOutputExceptionsCannotBeCalledWithoutActualPrivateProof(){
        var resource=new ResourceReadAuthority(null,null,null);
        assertThrows(NullPointerException.class,()->resource.requireStrictFieldOutput(null,null,"artifact.read",TaskStrictFieldNode.PURPOSE,null));
        var versions=new VersionResourceAuthority(null,null,null);
        assertThrows(NullPointerException.class,()->versions.requireStrictFieldOutput(null,null,TaskStrictFieldNode.PURPOSE,null,java.util.Map.of(),null));
    }
    @Test void runtimeOriginalWriterAndPhysicalProofsArePrivatelyMinted(){
        for(Class<?> type:List.of(TaskStrictFieldDispatchReservation.Pending.class,TaskStrictFieldDispatchReservation.Original.class,TaskStrictFieldCompletionAuthority.Original.class,TaskStrictFieldOriginalResult.Original.class,TaskStrictFieldOutputLedger.Attempt.class,TaskStrictFieldOutputLedger.Registered.class,TaskStrictFieldWriterReadAuthority.ForWriter.class,TaskStrictFieldWriterReadAuthority.CommittedRead.class))for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getName());
        assertTrue(RuntimeObjectAttempt.class.isSealed());
    }
}
