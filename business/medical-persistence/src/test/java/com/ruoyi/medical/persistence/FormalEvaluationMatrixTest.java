package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationCoverageTest.*;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;
class FormalEvaluationMatrixTest {
 final PermissionSchema json=new PermissionSchema();FormalEvaluationMatrixTest()throws Exception{}
 Start start(){return new Start("1.0.0",id(1),H,"1",b(2),b(3),b(4),b(5));}
 EvaluationProtocol.Sample sample(int i,String evidence){return new EvaluationProtocol.Sample(id(i),id(i+100),"ACCEPTANCE","TECHNICAL",evidence,b(i+200),b(i+300),List.of());}
 EvaluationProtocol.Definition definition(List<EvaluationProtocol.Sample> samples,int repeats){var request=new EvaluationProtocol.Create("1.0.0",id(6),b(7),b(8),samples,List.of(b(2)),b(9),List.of("E01"),new EvaluationProtocol.Variance(repeats,"FIXED","APPROVED"),b(11),"PRODUCTION","test fixture");return new EvaluationProtocol.Definition("1.0.0",id(12),id(1),request,List.of(),List.of(),id(13),"1",id(14),"2026-10-18T00:00:00Z");}
 AgentExecutionPolicyProtocol.NodeBinding binding(String node){return new AgentExecutionPolicyProtocol.NodeBinding(node,b(15),node.equals("N00")?null:b(16),node.equals("N00")?null:b(17),null,json.object().put("temperature","0"),List.of());}
 @Test void fullMatrixDerivesFromAllSamplesRepeatsAndPolicyNodes()throws Exception{var actual=FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL"),sample(21,"REAL")),3),start(),H,List.of(binding("N01"),binding("N02")),json);assertEquals(12,actual.size());assertEquals(Set.of(0,1,2),actual.stream().map(n->n.cell().repeatIndex()).collect(java.util.stream.Collectors.toSet()));}
 @Test void rejectsSimulationEvidence(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"SIMULATION")),1),start(),H,List.of(binding("N01")),json));}
 @Test void rejectsDuplicateSamples(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL"),sample(20,"REAL")),1),start(),H,List.of(binding("N01")),json));}
 @Test void rejectsDuplicatePolicyNodes(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL")),1),start(),H,List.of(binding("N01"),binding("N01")),json));}
 @Test void noZeroRepeats(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL")),0),start(),H,List.of(binding("N01")),json));}
 @Test void boundedRepeats(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL")),101),start(),H,List.of(binding("N01")),json));}
 @Test void candidateMustBeOriginalMember(){var start=new Start("1.0.0",id(1),H,"1",b(99),b(3),b(4),b(5));assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL")),1),start,H,List.of(binding("N01")),json));}
 @Test void emptyNodesCannotPretendComplete(){assertThrows(TenantAccessException.class,()->FormalEvaluationExecutionAdmission.matrix(definition(List.of(sample(20,"REAL")),1),start(),H,List.of(),json));}
}
