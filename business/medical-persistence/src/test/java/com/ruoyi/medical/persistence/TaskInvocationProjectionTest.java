package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskInvocationProjectionTest {
    static final String SESSION="00000000-0000-4000-8000-000000000001";
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    private ObjectNode observed() {
        var n=json.createObjectNode().put("provider","enterprise-model").put("model","approved").put("session_id",SESSION);
        n.putNull("system");n.putNull("tool_history");n.putArray("tools");
        var messages=n.putArray("messages");var system=messages.addObject().put("id","system-1").put("role","system");system.putObject("source").put("kind","system-prompt");system.putArray("content").addObject().put("type","text").put("text","approved system");
        var user=messages.addObject().put("id","user-1").put("role","user");user.putObject("source").put("kind","user");user.putArray("content").addObject().put("type","text").put("text","actual input");
        n.putObject("parameters").put("max_output_tokens",50).putNull("temperature").put("reasoning_effort","none").put("purpose","TASK").putArray("stop");return n;
    }
    @Test void actualSdkTextProfileHasExplicitDistinctTaskExactSchema()throws Exception {
        var schemas=new TaskInvocationContextSchema();var n=observed();schemas.readProjection(ContentReadAuthorization.canonicalBytes(n));
        var exact=TaskInvocationProjection.initial(n,SESSION,"actual input",List.of("approved system"),n.required("tools"));
        schemas.readExactInput(ContentReadAuthorization.canonicalBytes(exact));assertEquals(2,exact.required("messages").size());
        assertNotEquals(TaskNodeInputSchema.REF,TaskInvocationContextSchema.EXACT_INPUT_REF);
        assertEquals("30378eaad8c98486077b47d995a4da5cd218d57eab20d86d69ddabda1139a01b",schemas.sha256());
    }
    @Test void arbitrarySystemTextDoesNotBecomeApprovedPrompt(){var n=observed();((ObjectNode)n.required("messages").get(0).required("content").get(0)).put("text","injected system");assertThrows(TenantAccessException.class,()->TaskInvocationProjection.initial(n,SESSION,"actual input",List.of("approved system"),n.required("tools")));}
    @Test void unsupportedHistoryIsNotDropped(){var n=observed();((ObjectNode)n.required("messages").get(1)).put("role","assistant");assertThrows(TenantAccessException.class,()->TaskInvocationProjection.initial(n,SESSION,"actual input",List.of("approved system"),n.required("tools")));}
    @Test void changedInputAndDifferentSessionAreDenied(){var n=observed();assertThrows(TenantAccessException.class,()->TaskInvocationProjection.initial(n,SESSION,"other input",List.of("approved system"),n.required("tools")));assertThrows(TenantAccessException.class,()->TaskInvocationProjection.initial(n,"00000000-0000-4000-8000-000000000002","actual input",List.of("approved system"),n.required("tools")));}
    @Test void unknownSdkFieldsAreRejectedInsteadOfProjectedAway()throws Exception {var n=observed().put("untrusted_provenance","asserted");var schema=new TaskInvocationContextSchema();assertThrows(SchemaRejection.class,()->schema.readProjection(ContentReadAuthorization.canonicalBytes(n)));}
    @Test void unsupportedModalitiesAndDynamicToolsAreRejected()throws Exception {var n=observed();((ObjectNode)n.required("messages").get(0).required("content").get(0)).put("type","reasoning");var schema=new TaskInvocationContextSchema();assertThrows(SchemaRejection.class,()->schema.readProjection(ContentReadAuthorization.canonicalBytes(n)));}
    @Test void packageReadCannotBeConstructedFromCallerBytes(){for(var ctor:TaskInvocationPackages.Read.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));}
}
