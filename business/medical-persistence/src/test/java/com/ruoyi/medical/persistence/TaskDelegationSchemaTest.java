package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic closed-contract tests. No approvals, grants, service call or database state is created. */
class TaskDelegationSchemaTest {
    private final PermissionSchema json=new PermissionSchema();
    private final TaskDelegationSchema schema=new TaskDelegationSchema(json);
    TaskDelegationSchemaTest()throws Exception{}
    private static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    private ObjectNode request(){
        var value=json.object().put("schema_version","1.0.0").put("project_id",id(1)).put("release_id",id(2))
                .put("publication_approval_sha256","a".repeat(64)).put("budget_revision",id(3)).put("currency","USD")
                .put("maximum_amount","12.50").put("maximum_tokens","200").put("maximum_calls","2")
                .put("valid_from","2026-10-19T00:00:00Z").put("valid_until","2026-10-20T00:00:00Z")
                .put("classification","INTERNAL").put("reason","Explicit exact task delegation");
        value.set("execution_policy",json.object().put("version_id",id(4)).put("content_sha256","b".repeat(64)));
        value.set("role_version",json.object().put("version_id",id(5)).put("content_sha256","c".repeat(64)));
        value.putArray("operations").addObject().put("selector","TASK").put("action","task.read")
                .put("purpose","TASK_EXECUTION").putNull("model_version").putArray("artifact_kinds");
        return value;
    }
    @Test void acceptsOnlyExplicitBoundedRequest()throws Exception{
        var value=request();assertEquals(value,schema.request("Author",schema.canonical(value).getBytes(StandardCharsets.UTF_8)));
        assertEquals(64,schema.manifest().length());assertEquals(schema.hash(value),schema.hash(schema.read(schema.canonical(value))));
    }
    @Test void rejectsUnknownFieldsAndNetworkActions(){
        var value=request();value.put("network_allowed",true);assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        var other=request();((ObjectNode)other.required("operations").get(0)).put("action","network.query");assertThrows(SchemaRejection.class,()->schema.validate("Author",other));
    }
    @Test void taskSelectorCannotBecomeArtifactWildcard(){
        var value=request();((ObjectNode)value.required("operations").get(0)).put("action","artifact.read");
        assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
    }
    @Test void bootstrapRegistrationRequiresOnlyExplicitReplayKind(){
        var value=request();var op=(ObjectNode)value.required("operations").get(0);op.put("action","artifact.register");
        assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        op.putArray("artifact_kinds").add("REPLAY_MANIFEST");assertDoesNotThrow(()->schema.validate("Author",value));
        op.putArray("artifact_kinds").add("STEP_RESULT");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        op.putArray("artifact_kinds").add("REPLAY_MANIFEST").add("STEP_RESULT");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        op.put("action","task.run");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
    }
    @Test void derivedArtifactsRequireExactKindSet(){
        var value=request();var op=(ObjectNode)value.required("operations").get(0);op.put("selector","REGISTERED_ARTIFACT").put("action","artifact.read");
        assertThrows(SchemaRejection.class,()->schema.validate("Author",value));op.putArray("artifact_kinds").add("REPLAY_MANIFEST");
        assertDoesNotThrow(()->schema.validate("Author",value));op.putArray("artifact_kinds").add("*");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
    }
    @Test void modelOperationRequiresPinnedVersionAndNoArtifactKinds(){
        var value=request();var op=(ObjectNode)value.required("operations").get(0);op.put("selector","MODEL_CONFIG").put("action","model.send");
        assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        op.set("model_version",json.object().put("version_id",id(6)).put("content_sha256","d".repeat(64)));
        assertDoesNotThrow(()->schema.validate("Author",value));op.putArray("artifact_kinds").add("STEP_RESULT");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
    }
    @Test void rejectsZeroMoneyUnboundedCountsAndInvalidPeriod(){
        var value=request();value.put("maximum_amount","0");assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        var counts=request();counts.put("maximum_calls","0");assertThrows(SchemaRejection.class,()->schema.validate("Author",counts));
        var dates=request();dates.put("valid_until",dates.required("valid_from").asText());assertThrows(SchemaRejection.class,()->schema.validate("Author",dates));
    }
    @Test void rejectsDuplicatePermissionsAndJsonFields()throws Exception{
        var value=request();((com.fasterxml.jackson.databind.node.ArrayNode)value.required("operations")).add(value.required("operations").get(0).deepCopy());
        assertThrows(SchemaRejection.class,()->schema.validate("Author",value));
        var raw=schema.canonical(request());var duplicate=raw.substring(0,raw.length()-1)+",\"schema_version\":\"1.0.0\"}";
        assertThrows(java.io.IOException.class,()->schema.request("Author",duplicate.getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.io.IOException.class,()->schema.request("Author",(raw+" {}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void independentApproverRequiredAndDisabledRuleCannotBeReenabled(){
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->TaskDelegationService.requireDecision("DRAFT","APPROVE",id(1),id(1))).code());
        assertDoesNotThrow(()->TaskDelegationService.requireDecision("DRAFT","APPROVE",id(1),id(2)));
        assertDoesNotThrow(()->TaskDelegationService.requireDecision("APPROVED","DISABLE",id(1),id(1)));
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->TaskDelegationService.requireDecision("DISABLED","APPROVE",id(1),id(2))).code());
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->TaskDelegationService.requireDecision("REJECTED","APPROVE",id(1),id(2))).code());
    }
    @Test void idempotencyKeysAreNotOptionalOrBlank(){
        assertThrows(SchemaRejection.class,()->TaskDelegationSchema.key(null));assertThrows(SchemaRejection.class,()->TaskDelegationSchema.key("short"));
        assertDoesNotThrow(()->TaskDelegationSchema.key("task-delegation-original-0001"));
    }
}
