package com.ruoyi.medical.workflow;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Synthetic contract-only fixtures. Deferred: this source has not been executed. */
class FormalEvaluationProtocolTest {
    private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    private static final String H="a".repeat(64);
    private FormalEvaluationProtocol.Reference reference(String kind,List<FormalEvaluationProtocol.Step> nodes){return new FormalEvaluationProtocol.Reference("1.0.0",kind,"EVALUATION_VERIFICATION",id(1),id(2),id(3),id(4),id(5),id(6),0,id(7),H,H,id(8),H,id(9),H,nodes,"eval-control","eval-professional",60000,5000,1000);}
    @Test void taskAuthorityCannotBeRelabelled(){assertThrows(IllegalArgumentException.class,()->reference("TASK",List.of(new FormalEvaluationProtocol.Step(id(10),"N01",H))));}
    @Test void emptyOrDuplicateObligationsAreRejected(){assertThrows(IllegalArgumentException.class,()->reference("SYSTEM_JOB",List.of()));var n=new FormalEvaluationProtocol.Step(id(10),"N01",H);assertThrows(IllegalArgumentException.class,()->reference("SYSTEM_JOB",List.of(n,n)));}
    @Test void unknownRemainsExplicitAndBoundToOriginalNode(){var step=new FormalEvaluationProtocol.Step(id(10),"N01",H);var r=reference("SYSTEM_JOB",List.of(step));var result=new FormalEvaluationProtocol.NodeReceipt("1.0.0",id(5),id(10),H,"UNKNOWN",id(11),H);assertEquals("UNKNOWN",result.state());assertDoesNotThrow(()->result.require(r,step));assertThrows(IllegalArgumentException.class,()->result.require(r,new FormalEvaluationProtocol.Step(id(12),"N01",H)));}
    @Test void deliveryAcceptedProofCannotBeCallerConstructed(){for(var c:FormalEvaluationDelivery.Accepted.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));}
}
