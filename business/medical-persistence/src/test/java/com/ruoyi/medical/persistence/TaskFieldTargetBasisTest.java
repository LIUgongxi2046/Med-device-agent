package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real private preparation proof and timestamp-bearing witness encoding; upstream domain boundaries doubled. */
class TaskFieldTargetBasisTest {
 @Test void realTimestampGrantsAndOwnerWitnessesHashDeterministically()throws Exception{var f=new Fixture();f.tx(()->{
  var first=f.require();assertEquals("2026-10-20T00:00:00",first.authorizations().at("/grant/valid_from").asText());
  assertEquals("2026-10-21T00:00:00",first.authorizations().at("/owners/"+f.base.actor+"/valid_to").asText());
  var same=f.require();assertEquals(first.authorizationsSha256(),same.authorizationsSha256());first.requireUnchanged();return null;
 });}
 @Test void proofDoesNotEscapeCallbackOrCrossTransactionResources()throws Exception{var f=new Fixture();var proof=f.tx(f::require);assertThrows(TenantAccessException.class,proof::pins);
  f.tx(()->{var live=f.require();Object key=new Object();TransactionSynchronizationManager.bindResource(key,new Object());try{assertThrows(TenantAccessException.class,live::pins);}finally{TransactionSynchronizationManager.unbindResource(key);}return null;});
 }
 @Test void revokedExactProducerGrantInvalidatesAlreadyMintedProof()throws Exception{var f=new Fixture();f.tx(()->{var proof=f.require();doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(f.resources).require(eq(f.base.admission),any(),eq(TaskFieldTargetProtocol.TARGET_PRODUCE),eq(TaskFieldTargetProtocol.PREPARATION_PURPOSE));assertThrows(TenantAccessException.class,proof::requireUnchanged);return null;});}
 @Test void privateProofRejectsDifferentActionAndChangedPins()throws Exception{var f=new Fixture();f.tx(()->{var proof=f.require();assertThrows(TenantAccessException.class,()->proof.requireAction(TaskFieldTargetProtocol.MAPPING_WRITE));var pins=proof.pins();pins.put("task_revision",TaskFieldTargetSchemaTest.id(88));assertThrows(TenantAccessException.class,()->proof.requirePins(pins));return null;});}
 static final class Fixture {
  final TaskFieldTargetLedgerTest.Fixture base=new TaskFieldTargetLedgerTest.Fixture();
  final TaskPlanService plans=mock(TaskPlanService.class);final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
  final IdentityDirectoryPolicy identity=mock(IdentityDirectoryPolicy.class);final TaskInputSchema inputs=mock(TaskInputSchema.class);
  final TaskFieldTargetBasis basis;
  Fixture()throws Exception{
   var input=mock(TaskProtocol.Input.class);when(input.requestHash()).thenReturn(base.hash);var head=base.basis.task().head();var fixed=new TaskService.VerifiedTask(base.json.object(),head,input);when(base.tasks.fixedTask(base.admission,base.task)).thenReturn(fixed);
   var plan=base.basis.plan();when(plans.readFixed(base.admission,fixed,head.planRevision(),true)).thenReturn(plan);
   var grant=new PermissionProtocol.GrantRow(TaskFieldTargetSchemaTest.id(70),base.actor,base.project,"TASK",base.task,TaskFieldTargetProtocol.TARGET_PRODUCE,"{}","ACTIVE",LocalDateTime.of(2026,10,20,0,0),LocalDateTime.of(2026,10,21,0,0),TaskFieldTargetSchemaTest.id(71),"1.0.0",base.hash,BigInteger.ONE);
   when(resources.require(eq(base.admission),any(),anyString(),anyString())).thenReturn(grant);
   var stored=mock(TemplateProtocol.Stored.class);when(stored.content_sha256()).thenReturn(base.hash);
   var view=new TemplateApplicabilityProtocol.TemplateView(stored,"APPLICABLE",List.of(),List.of(),base.hash);
   var target=new TemplateApplicabilityService.Target(base.admission,"PLAN_MAINTENANCE",null,view,grant,"{}");
   when(plans.execution(base.admission,fixed,plan)).thenReturn(new TaskPlanService.Confirmation(target,null,List.of(),Map.of(base.actor,grant)));
   when(inputs.read("{}")).thenReturn(base.json.object());when(identity.classification()).thenReturn("INTERNAL");basis=new TaskFieldTargetBasis(base.tasks,plans,resources,base.json,identity,inputs);
  }
  TaskFieldTargetBasis.Verified require()throws Exception{return basis.require(base.admission,base.task,TaskFieldTargetProtocol.TARGET_PRODUCE);}
  <T>T tx(TaskFieldTargetLedgerTest.Fixture.Work<T> work)throws Exception{return base.tx(()->{base.scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));try{return work.run();}finally{base.scope.finishTransaction();}});}
 }
}
