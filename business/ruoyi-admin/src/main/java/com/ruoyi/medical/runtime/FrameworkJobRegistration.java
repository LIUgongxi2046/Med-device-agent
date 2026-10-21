package com.ruoyi.medical.runtime;

public record FrameworkJobRegistration(long jobId, String jobGroup, String handlerKey,
        Class<? extends FrameworkRegisteredJob> jobClass) {
    public FrameworkJobRegistration {
        if (jobId <= 0 || jobGroup == null || !jobGroup.matches("[A-Za-z0-9_-]{1,64}")
                || handlerKey == null || !handlerKey.matches("[a-z][a-z0-9_.-]{0,95}") || jobClass == null)
            throw new IllegalArgumentException("FRAMEWORK_JOB_REGISTRATION_INVALID");
    }
    public org.quartz.JobKey jobKey() { return new org.quartz.JobKey("registered-" + jobId, jobGroup); }
}
