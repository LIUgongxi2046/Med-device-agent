package com.ruoyi.medical.workflow;
@io.temporal.activity.ActivityInterface
public interface TaskRunReuseActivitiesV1 {
 @io.temporal.activity.ActivityMethod(name="medical.run.reuse-inventory.v1") TaskRunReuseProtocolV1.InventoryV1 reuseInventory(WorkflowProtocol.Reference reference);
 @io.temporal.activity.ActivityMethod(name="medical.run.reuse-assembly.v1") TaskRunReuseProtocolV1.InventoryV1 reuseAssembly(WorkflowProtocol.Reference reference);
}
