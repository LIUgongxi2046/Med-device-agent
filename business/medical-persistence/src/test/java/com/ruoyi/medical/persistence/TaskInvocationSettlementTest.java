package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Uses actual076 accounting mutations with a verified TASK original boundary fixture. */
class TaskInvocationSettlementTest extends FormalInvocationSettlementTest {
    TaskInvocationFixture task;
    @BeforeEach void taskBranch()throws Exception {
        task=new TaskInvocationFixture();var records=mock(TaskInvocationRecords.class);
        when(records.supports(tenant,id(8))).thenReturn(true);when(records.byInvocation(tenant,inv)).thenReturn(task.binding);
        var field=FormalInvocationSettlement.class.getDeclaredField("tasks");field.setAccessible(true);field.set(service,records);
    }
    @Test void settledTaskRequestDoesNotCompleteRunOrNode()throws Exception {
        run();verify(db,times(2)).update(startsWith("UPDATE budget_account SET reserved="),any(Object[].class));
        verify(db,never()).update(startsWith("UPDATE run_step"),any(Object[].class));
        verify(db,never()).update(startsWith("UPDATE system_job_run"),any(Object[].class));
        verify(db,never()).update(startsWith("UPDATE task_run"),any(Object[].class));
    }
    @Test void modelToolCallsSettleButAreNotQuestionCompletion()throws Exception {
        var result=task.schema.parse("{\"reason\":{\"kind\":\"tool-calls\"},\"provider_request_id\":\"actual-response\"}");
        when(outcome.result()).thenReturn(result);when(resultObject.canonicalPayload()).thenReturn(result);run();
        assertTrue(mockingDetails(db).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("update")&&call.getArgument(0).toString().startsWith("UPDATE invocation SET status=")&&"SUCCEEDED".equals(call.getArgument(1))));
        verify(db,never()).update(startsWith("UPDATE run_step"),any(Object[].class));
    }
}
