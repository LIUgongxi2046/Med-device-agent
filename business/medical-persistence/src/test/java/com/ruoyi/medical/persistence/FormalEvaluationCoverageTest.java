package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;
/** Pure contract/coverage tests. Records below are explicit synthetic values, never original production proofs. */
class FormalEvaluationCoverageTest {
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}static String H="a".repeat(64);
 static PermissionProtocol.Basis b(int n){return new PermissionProtocol.Basis(id(n),H);}static Artifact artifact(int n){return new Artifact(id(n),H,"original-version","urn:fixture");}
 Node node(int sample,int repeat,String kind,boolean model){return new Node(new Cell(id(1),H,b(2),b(3),id(sample),id(4),"ACCEPTANCE",repeat,b(5),b(6),H),kind,b(7),model?b(8):null,model?b(9):null,H);}
 FormalEvaluationExecutionProtocol.Record record(Node n,int run,int step,int invocation){return new FormalEvaluationExecutionProtocol.Record(n,id(run),id(step),1,artifact(20),artifact(21),artifact(22),artifact(23),invocation==0?List.of():List.of(new Invocation(id(invocation),id(invocation+1000),artifact(invocation+2000),artifact(invocation+3000),H,H,H)),List.of(artifact(24)),List.of(),H,H);}
 @Test void exactCompleteTwoRepeatTwoNodeMatrix(){var nodes=List.of(node(10,0,"N01",true),node(10,0,"N02",true),node(10,1,"N01",true),node(10,1,"N02",true));var records=List.of(record(nodes.get(0),30,40,100),record(nodes.get(1),30,41,101),record(nodes.get(2),31,42,102),record(nodes.get(3),31,43,103));assertDoesNotThrow(()->FormalEvaluationCoverage.exact(nodes,records));}
 @Test void missingCellFails(){var n=node(10,0,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n),List.of()));}
 @Test void duplicateExpectedCellFails(){var n=node(10,0,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n,n),List.of(record(n,30,40,100),record(n,30,41,101))));}
 @Test void nodeModelRequiresInvocation(){var n=node(10,0,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n),List.of(record(n,30,40,0))));}
 @Test void nonModelCannotLaunderInvocation(){var n=node(10,0,"N00",false);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n),List.of(record(n,30,40,100))));}
 @Test void cannotShareRunAcrossRepeats(){var n=node(10,0,"N01",true);var m=node(10,1,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n,m),List.of(record(n,30,40,100),record(m,30,41,101))));}
 @Test void cannotSplitCellAcrossRuns(){var n=node(10,0,"N01",true);var m=node(10,0,"N02",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n,m),List.of(record(n,30,40,100),record(m,31,41,101))));}
 @Test void invocationCannotBeCountedTwice(){var n=node(10,0,"N01",true);var m=node(10,0,"N02",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n,m),List.of(record(n,30,40,100),record(m,30,41,100))));}
 @Test void cannotSubstituteSample(){var n=node(10,0,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n),List.of(record(node(11,0,"N01",true),30,40,100))));}
 @Test void cannotSubstituteNode(){var n=node(10,0,"N01",true);assertThrows(TenantAccessException.class,()->FormalEvaluationCoverage.exact(List.of(n),List.of(record(node(10,0,"N00",false),30,40,0))));}
 @Test void artifactReadsAreUniqueExactVersions(){var n=node(10,0,"N01",true);var r=record(n,30,40,100);assertEquals(7,FormalEvaluationCoverage.artifacts(List.of(r)).size());}
 @Test void proofConstructorsArePrivate(){for(var type:List.of(FormalEvaluationExecutionEvidence.Prepared.class,FormalEvaluationExecutionEvidence.VerifiedEvaluationExecutionEvidence.class,FormalEvaluationExecutionEvidence.HistoricalEvaluationExecutionEvidence.class,FormalEvaluationExecutionAdmission.Prepared.class,FormalEvaluationExecutionInputs.Prepared.class,FormalEvaluationExecutionScopes.Prepared.class))for(var constructor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));}
}
