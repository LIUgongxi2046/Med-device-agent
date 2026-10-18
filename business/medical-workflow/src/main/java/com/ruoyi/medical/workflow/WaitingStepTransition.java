package com.ruoyi.medical.workflow;

import java.util.Objects;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Structural check only: callers must first consume the real persisted decision. */
public final class WaitingStepTransition {
 private WaitingStepTransition(){}
 public static boolean valid(Step before,Receipt waiting,Receipt resolved){
  return before.status()==State.WAITING_INPUT && waiting!=null && waiting.status()==State.WAITING_INPUT
   && Objects.equals(before.receiptId(),waiting.receiptId()) && Objects.equals(before.waitingItemId(),waiting.waitingItemId())
   && before.waitingItemId()!=null && before.stepId().equals(waiting.stepId()) && before.inputSha256().equals(waiting.inputSha256())
   && before.leaseGeneration()==waiting.leaseGeneration() && resolved.status()==State.QUEUED
   && before.stepId().equals(resolved.stepId()) && before.inputSha256().equals(resolved.inputSha256())
   && before.leaseGeneration()==resolved.leaseGeneration() && resolved.waitingItemId()==null && resolved.outputs().isEmpty()
   && !waiting.receiptId().equals(resolved.receiptId());
 }
}
