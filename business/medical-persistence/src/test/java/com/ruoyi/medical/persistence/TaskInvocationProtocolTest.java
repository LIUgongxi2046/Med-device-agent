package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.CommonProtocol;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskInvocationProtocolTest {
    @Test void requestDataCannotBeUsedAsPrivateSendOrTerminalProof(){
        for(var type:List.of(TaskInvocationService.BoundAction.class,TaskInvocationService.Prepared.class,TaskInvocationService.Terminal.class,TaskInvocationRecords.Binding.class))
            for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        assertTrue(FormalInvocationBinding.class.isSealed());
    }
    @Test void taskNodeInputIsNeverRelabeledAsExactInput()throws Exception {
        var fixture=new TaskInvocationFixture();var r=fixture.request;
        var wrong=new CommonProtocol.ArtifactRef(r.node().node_input_artifact_id(),fixture.hash,r.exact_input().schemaRef(),r.exact_input().coverage());
        assertThrows(IllegalArgumentException.class,()->new TaskInvocationProtocol.Request(r.schema_version(),r.execution_kind(),r.node_kind(),r.node(),r.model_config_revision(),r.business_purpose(),wrong,r.scope_snapshot_basis(),r.tokenizer_basis(),r.role_basis(),r.prompt_basis(),r.rule_bases(),r.enterprise_budget_basis(),r.parent_budget_basis(),r.maximum_input_tokens(),r.request_parameters()));
    }
    @Test void nodeLeaseUsesExactUnsignedIntegerNotFloatingPoint()throws Exception {
        var f=new TaskInvocationFixture();var n=f.request.node();
        assertThrows(IllegalArgumentException.class,()->new TaskInvocationProtocol.Node(n.tenant_id(),n.project_id(),n.task_id(),n.run_id(),n.principal_id(),n.step_id(),n.host_action_id(),n.session_id(),"18446744073709551616",n.worker_service_uri(),n.node_input_artifact_id(),n.node_input_sha256(),n.node_input_receipt_sha256(),n.supervisor_receipt_id(),n.supervisor_receipt_sha256(),n.classification()));
    }
    @Test void taskToolCallSuccessCannotCompleteTaskNode()throws Exception {
        var f=new TaskInvocationFixture();assertTrue(FormalInvocationSettlement.successfulResult(true,"tool-calls"));
        assertFalse(FormalInvocationSettlement.successfulResult(false,"tool-calls"));
        assertFalse(FormalInvocationSettlement.finishesRun(f.binding));
        assertFalse(FormalInvocationSettlement.successfulResult(true,"max-tokens"));
    }
    @Test void capturedToolCallsRequireExactIdNameAndArguments()throws Exception {
        var schema=new FormalInvocationSchema();var result=schema.parse("{\"reason\":{\"kind\":\"tool-calls\"},\"blocks\":[{\"type\":\"tool-call\",\"id\":\"call-1\",\"name\":\"AT-02\",\"arguments\":\"{\\\"source\\\":\\\"fixed\\\"}\"}]}");
        var arguments=schema.parse("{\"source\":\"fixed\"}");
        assertDoesNotThrow(()->TaskInvocationService.verifyToolCall(List.of(result),"call-1","AT-02",arguments,schema));
        assertThrows(TenantAccessException.class,()->TaskInvocationService.verifyToolCall(List.of(result),"invented","AT-02",arguments,schema));
        assertThrows(TenantAccessException.class,()->TaskInvocationService.verifyToolCall(List.of(result),"call-1","AT-01",arguments,schema));
        assertThrows(TenantAccessException.class,()->TaskInvocationService.verifyToolCall(List.of(result),"call-1","AT-02",schema.parse("{\"source\":\"other\"}"),schema));
        assertThrows(TenantAccessException.class,()->TaskInvocationService.verifyToolCall(List.of(result,result),"call-1","AT-02",arguments,schema));
    }
    @Test void stoppedTextCannotPretendToHaveAuthorizedTools()throws Exception {
        var schema=new FormalInvocationSchema();var result=schema.parse("{\"reason\":{\"kind\":\"stop\"},\"blocks\":[{\"type\":\"tool-call\",\"id\":\"call-1\",\"name\":\"AT-02\",\"arguments\":\"{}\"}]}");
        assertThrows(TenantAccessException.class,()->TaskInvocationService.verifyToolCall(List.of(result),"call-1","AT-02",schema.parse("{}"),schema));
    }
}
