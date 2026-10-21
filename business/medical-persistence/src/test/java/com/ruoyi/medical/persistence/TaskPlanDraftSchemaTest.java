package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskPlanDraftSchemaTest {
    final TaskPlanDraftSchema schema=new TaskPlanDraftSchema();
    TaskPlanDraftSchemaTest()throws Exception{}
    String id(int n){return new UUID(0,n).toString();}
    ObjectNode start()throws Exception{
        var n=(ObjectNode)schema.read("{}");n.put("schema_version","1.0.0").put("task_revision",id(1)).put("expected_revision","1")
            .put("agent_release_id",id(2)).put("currency","CNY").put("maximum_amount","1.25").put("maximum_tokens","100").put("reason","Generate an actual draft");
        int i=10;for(String key:new String[]{"template","profile","execution_policy","enterprise_budget","parent_budget"})
            n.putObject(key).put("version_id",id(i++)).put("content_sha256","a".repeat(64));
        return n;
    }
    @Test void originalBytesPreservedButCanonicalDigestStable()throws Exception{
        var n=start();String raw=n.toPrettyString();var actual=schema.start(raw.getBytes(StandardCharsets.UTF_8));
        assertEquals(raw,actual.raw());assertEquals(schema.canonical(n),actual.canonical());
        assertEquals(actual.request(),schema.start(schema.canonical(n).getBytes(StandardCharsets.UTF_8)).request());
    }
    @Test void duplicateFieldsTrailingTokensAndInvalidUtf8Reject()throws Exception{
        var raw=start().toString();
        assertThrows(Exception.class,()->schema.start((raw+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->schema.start(raw.replace("\"schema_version\":\"1.0.0\"","\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"").getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class,()->schema.start(new byte[]{(byte)0xc3,(byte)0x28}));
    }
    @Test void approvalClaimsAndUnknownFieldsAreNotAccepted()throws Exception{
        var n=start();n.put("approved",true);assertThrows(SchemaRejection.class,()->schema.start(n.toString().getBytes(StandardCharsets.UTF_8)));
        n.remove("approved");n.put("plan_revision",id(300));assertThrows(SchemaRejection.class,()->schema.start(n.toString().getBytes(StandardCharsets.UTF_8)));
    }
    @Test void sameAccountBasisAndNegativeLimitsReject()throws Exception{
        var n=start();n.set("parent_budget",n.get("enterprise_budget"));assertThrows(SchemaRejection.class,()->schema.start(n.toString().getBytes(StandardCharsets.UTF_8)));
        var other=start();other.put("maximum_amount","-1");assertThrows(SchemaRejection.class,()->schema.start(other.toString().getBytes(StandardCharsets.UTF_8)));
    }
    @Test void unknownDefinitionCannotBecomeAnOpenSchema()throws Exception{
        assertThrows(SchemaRejection.class,()->schema.validate("CallerApproval",start()));
    }
}
