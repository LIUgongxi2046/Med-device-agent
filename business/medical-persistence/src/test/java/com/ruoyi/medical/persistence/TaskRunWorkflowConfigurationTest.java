package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRunWorkflowConfigurationTest {
    final String json="""
        {"revision":"00000000-0000-4000-8000-000000000001","namespace":"fixture-only",
        "service_uri":"spiffe://fixture/worker","control_task_queue":"fixture-control","professional_task_queue":"fixture-professional",
        "run_timeout_millis":60000,"maximum_commands":10,"maximum_steps":3,"maximum_parallel_steps":2,
        "read_timeout_millis":1000,"step_timeout_millis":2000,"schedule_timeout_millis":3000}
        """;
    @Test void deploymentConfigurationIsExplicitClosedAndCanonical()throws Exception{
        var c=TaskRunWorkflowConfiguration.load(json);assertEquals("fixture-only",c.namespace());
        assertEquals(new TaskRunBootstrapSchema().hash(c),new TaskRunBootstrapSchema().hash(TaskRunWorkflowConfiguration.load(json.replace("60000","60000"))));
    }
    @Test void missingUnknownDuplicateCoercedAndInvalidValuesAreRejected(){
        for(String invalid:new String[]{json.replace("\"maximum_commands\":10,",""),json.replace("60000","\"60000\""),json.replace("60000","null"),json.replace("60000","60000.5"),json.replace("\"maximum_steps\":3","\"maximum_steps\":3,\"extra\":1"),json.replace("\"maximum_steps\":3","\"maximum_steps\":3,\"maximum_steps\":3"),json.replace("\"maximum_parallel_steps\":2","\"maximum_parallel_steps\":4"),json+"{}"})
            assertThrows(Exception.class,()->TaskRunWorkflowConfiguration.load(invalid));
    }
}
