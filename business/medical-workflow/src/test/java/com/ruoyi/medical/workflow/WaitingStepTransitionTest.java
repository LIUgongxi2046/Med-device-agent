package com.ruoyi.medical.workflow;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
class WaitingStepTransitionTest {
 final String step=id(1),waiting=id(2),old=id(3),fresh=id(4),hash="a".repeat(64);
 Step before(){return new Step(step,hash,List.of(),true,State.WAITING_INPUT,1,old,waiting,List.of());}
 Receipt previous(){return new Receipt(step,hash,1,State.WAITING_INPUT,old,waiting,List.of());}
 Receipt resolved(){return new Receipt(step,hash,1,State.QUEUED,fresh,null,List.of());}
 @Test void realConsumedResolutionCanQueueOnlySameInputBeforeNewLease(){assertTrue(WaitingStepTransition.valid(before(),previous(),resolved()));}
 @Test void inputLeaseReceiptAndOutputSpoofRefused(){
  for(var r:List.of(new Receipt(step,"b".repeat(64),1,State.QUEUED,fresh,null,List.of()),new Receipt(step,hash,2,State.QUEUED,fresh,null,List.of()),new Receipt(step,hash,1,State.QUEUED,old,null,List.of()),new Receipt(step,hash,1,State.SUCCEEDED,fresh,null,List.of()),new Receipt(step,hash,1,State.QUEUED,fresh,null,List.of(new Output(id(9),hash,"urn:unverified")))))assertFalse(WaitingStepTransition.valid(before(),previous(),r));
 }
 @Test void actualWorkflowDropsOnlyConsumedWaitingReceiptAndKeepsInput()throws Exception {
  var workflow=new TaskWorkflowImpl();var steps=field(workflow,"steps");steps.put(step,before());var receipts=field(workflow,"receipts");receipts.put(step,previous());
  var accept=TaskWorkflowImpl.class.getDeclaredMethod("accept",Receipt.class,boolean.class);accept.setAccessible(true);
  accept.invoke(workflow,resolved(),true);var after=(Step)steps.get(step);assertEquals(State.QUEUED,after.status());assertEquals(hash,after.inputSha256());assertEquals(1,after.leaseGeneration());assertNull(after.receiptId());assertFalse(receipts.containsKey(step));
 }
 @Test void resultOrUnrelatedCommandCannotReopenWaiting()throws Exception{
  var workflow=new TaskWorkflowImpl();field(workflow,"steps").put(step,before());field(workflow,"receipts").put(step,previous());var accept=TaskWorkflowImpl.class.getDeclaredMethod("accept",Receipt.class,boolean.class);accept.setAccessible(true);
  var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->accept.invoke(workflow,resolved(),false));assertTrue(error.getCause().getMessage().contains("IMMUTABLE_STEP_RECEIPT_MISMATCH"));
 }
 @SuppressWarnings("unchecked") Map<String,Object> field(Object object,String name)throws Exception{var f=TaskWorkflowImpl.class.getDeclaredField(name);f.setAccessible(true);return (Map<String,Object>)f.get(object);}
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
