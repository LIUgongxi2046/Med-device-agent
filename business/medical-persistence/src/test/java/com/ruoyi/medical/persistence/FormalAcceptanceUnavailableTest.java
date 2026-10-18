package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.ObjectProvider;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
/** Actual assembler and genuine EvaluationService metadata; missing professional implementation cannot mint any proof. */
class FormalAcceptanceUnavailableTest {
 @Test void currentAndHistoricalPreparationFailWithoutProfessionalEvidence()throws Exception{
  try(var f=new ExpertFixture()){
   var e=new ExpertEvaluationFixture(f);var schema=new FormalAgentPublicationSchema(f.f.json);var candidates=mock(FormalAgentCandidateAuthority.class);var identity=mock(FormalAgentCandidateAuthority.VerifiedCandidateIdentity.class);when(identity.executionPolicy()).thenReturn(new PermissionProtocol.Basis(FormalInputFixture.id(800),"b".repeat(64)));when(candidates.require(any(),any(),isNull(),eq("EVALUATION_RESULTS"))).thenReturn(identity);
   var policy=mock(EvaluationAcceptancePolicyService.class);ObjectProvider<FormalEvaluationExecutionEvidence> runtime=mock(ObjectProvider.class);
   var evidence=new FormalAgentAcceptanceEvidence(f.f.access,e.service,f.evaluationSchema,schema,f.f.json,candidates,policy,f.authority,f.store,runtime,mock(EvaluationAcceptanceStatisticsEngine.class),e.rules,e.refs,mock(AgentPublicationDependencies.class));
   var request=f.f.json.object();request.set("candidate",f.f.json.tree(e.definition.request().candidates().get(0)));request.set("evaluation",f.f.json.tree(new PermissionProtocol.Basis(e.definition.evaluation_id(),e.hash)));request.set("statistics_policy",f.f.json.tree(new PermissionProtocol.Basis(FormalInputFixture.id(801),"c".repeat(64))));request.putArray("score_bindings");
   for(boolean historical:new boolean[]{false,true}){var failure=ExpertQualificationServiceTest.cause(assertThrows(Exception.class,()->evidence.prepare(f.f.subject,"1",request,historical)));assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,((TenantAccessException)failure).code());}
   assertTrue(f.bindings.isEmpty());assertTrue(e.scores.isEmpty());verifyNoInteractions(e.refs);
  }
 }
 @Test void publicMetadataCannotManufactureReadPermitOrAcceptanceSnapshot()throws Exception{
  assertTrue(java.util.Arrays.stream(FormalAgentAcceptanceEvidence.ReadPermit.class.getDeclaredConstructors()).allMatch(x->java.lang.reflect.Modifier.isPrivate(x.getModifiers())));
  assertTrue(java.util.Arrays.stream(FormalAgentAcceptanceEvidence.ScoreBindingPermit.class.getDeclaredConstructors()).allMatch(x->java.lang.reflect.Modifier.isPrivate(x.getModifiers())));
  try(var f=new ExpertFixture()){var e=new ExpertEvaluationFixture(f);f.transaction(()->{assertThrows(TenantAccessException.class,()->e.service.requireFormalAcceptance(f.f.admission,e.definition.evaluation_id(),null));return null;});}
 }
}
