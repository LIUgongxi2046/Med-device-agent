package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CandidateStructuralComparisonTest {
 @Test void candidateIdentityNeverMasqueradesAsDocumentRevision()throws Exception{
  var schema=new DocumentStructuralComparisonSchema(true);var engine=new DocumentStructuralComparisonEngine(schema);
  String candidate=id(),target=id();var request=new InterfaceProtocol.CandidateCompareRequest(candidate,target);var body=body();
  var output=engine.compareCandidate(request,body,body,limits());assertEquals("FIXED_CANDIDATE_STRUCTURE_CORE",output.required("kind").asText());assertEquals(candidate,output.at("/request/candidate_id").asText());assertFalse(output.required("request").has("left_revision"));assertEquals(output,schema.validate(output));
  assertThrows(IllegalArgumentException.class,()->engine.compare(new InterfaceProtocol.CompareRequest(id(),id(),id()),body,body,limits()));
  assertThrows(IllegalArgumentException.class,()->new DocumentStructuralComparisonEngine(new DocumentStructuralComparisonSchema()).compareCandidate(request,body,body,limits()));
 }
 @Test void forgedTargetOrRecordInvalidatesFixedResult()throws Exception{
  var schema=new DocumentStructuralComparisonSchema(true);var engine=new DocumentStructuralComparisonEngine(schema);var result=engine.compareCandidate(new InterfaceProtocol.CandidateCompareRequest(id(),id()),body(),body(),limits());
  ((com.fasterxml.jackson.databind.node.ObjectNode)result.required("request")).put("target_document_revision",id());assertThrows(TenantAccessException.class,()->schema.validate(result));
 }
 @Test void elapsedBudgetRejectsAlreadyExpiredDeadlineWithoutSleeping()throws Exception{
  var budgetType=Class.forName(DocumentStructuralComparisonEngine.class.getName()+"$Budget");
  var constructor=budgetType.getDeclaredConstructor(DocumentStructuralComparisonEngine.Limits.class);constructor.setAccessible(true);
  var take=budgetType.getDeclaredMethod("take",long.class);take.setAccessible(true);
  var fresh=constructor.newInstance(limits());assertDoesNotThrow(()->take.invoke(fresh,0L));
  var finite=new DocumentStructuralComparisonEngine.Limits(100,100,1000,100000,100000,1000,10000,1000000,1000);
  var budget=constructor.newInstance(finite);
  var start=budgetType.getDeclaredField("start");start.setAccessible(true);start.setLong(budget,System.nanoTime()-2_000_000_000L);
  // Zero work isolates the actual elapsed-time guard; no sleep or exact nanosecond-boundary claim.
  var thrown=assertThrows(java.lang.reflect.InvocationTargetException.class,()->take.invoke(budget,0L));
  assertEquals(ErrorCode.CONTEXT_LIMIT,assertInstanceOf(TenantAccessException.class,thrown.getCause()).code());
 }
 private static InterfaceProtocol.DocumentBody body(){String root=id();return new InterfaceProtocol.DocumentBody("1.0.0",id(),List.of(new InterfaceProtocol.Node(root,null,0,List.of(),List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT,"Actual body"))),List.of(),List.of(),List.of());}
 // These are identity/integrity tests, not wall-clock performance tests. Allow cold schema validation
 // on shared CI while retaining finite time and every deterministic capacity bound unchanged.
 private static DocumentStructuralComparisonEngine.Limits limits(){return new DocumentStructuralComparisonEngine.Limits(100,100,1000,100000,100000,1000,10000,1000000,60000);}
 private static String id(){return UUID.randomUUID().toString();}
}
