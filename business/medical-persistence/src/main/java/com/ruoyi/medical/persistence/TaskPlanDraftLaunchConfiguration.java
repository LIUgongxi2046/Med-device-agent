package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.WireValues;
/** Explicit deployment data, never launcher acceptance or permission. */
public record TaskPlanDraftLaunchConfiguration(String revision,String controlTaskQueue,String professionalTaskQueue,
        long activityTimeoutMillis,long heartbeatTimeoutMillis) {
    public TaskPlanDraftLaunchConfiguration {
        WireValues.uuid(revision);
        for(String queue:new String[]{controlTaskQueue,professionalTaskQueue})if(queue==null||!queue.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("PLAN_DRAFT_QUEUE_REQUIRED");
        if(activityTimeoutMillis<1||activityTimeoutMillis>86400000||heartbeatTimeoutMillis<1||heartbeatTimeoutMillis>activityTimeoutMillis)throw new IllegalArgumentException("PLAN_DRAFT_TIMEOUT_INVALID");
    }
}
