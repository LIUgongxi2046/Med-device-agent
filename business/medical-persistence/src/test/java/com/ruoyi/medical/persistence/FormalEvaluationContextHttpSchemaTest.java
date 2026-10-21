package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

class FormalEvaluationContextHttpSchemaTest {
    final FormalEvaluationContextHttpSchema schema = new FormalEvaluationContextHttpSchema();
    final PermissionSchema json = new PermissionSchema();
    FormalEvaluationContextHttpSchemaTest() throws Exception { }
    byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    String query() { return "{\"schema_version\":\"1.0.0\",\"project_id\":\""+id(1)+"\",\"limit\":20,\"cursor\":null}"; }
    @Test void frozen088DefinitionsRemainByteIndependentAndSemanticallyExact() throws Exception {
        var mapper = new ObjectMapper();
        var original = mapper.readTree(getClass().getResourceAsStream("/formal-evaluation-context.schema.json"));
        var http = mapper.readTree(getClass().getResourceAsStream("/formal-evaluation-context-http.schema.json"));
        var entries=original.required("$defs").fields(); while(entries.hasNext()) { var e=entries.next(); assertEquals(e.getValue(),http.required("$defs").required(e.getKey())); }
    }
    @Test void closedQueryRejectsOffsetsSortsTenantAndOversizedPages() throws Exception {
        schema.request("Query",bytes(query()));
        for(String extra:List.of("offset","sort","tenant_id")) assertThrows(SchemaRejection.class,()->schema.request("Query",bytes(query().replace("\"cursor\":null", "\"cursor\":null,\""+extra+"\":\"injected\""))));
        assertThrows(SchemaRejection.class,()->schema.request("Query",bytes(query().replace("20", "21"))));
        assertThrows(SchemaRejection.class,()->schema.request("Query",bytes(query().replace("\"project_id\":\""+id(1)+"\"", "\"project_id\":null"))));
    }
    @Test void duplicateAndTrailingJsonAreRejected() {
        assertThrows(Exception.class,()->schema.request("Query",bytes(query().replace("\"limit\":20", "\"limit\":1,\"limit\":20"))));
        assertThrows(Exception.class,()->schema.request("Query",bytes(query()+" {}")));
    }
    @Test void recoveryMustHaveExactTypedOriginalAndNoActorOverride() throws Exception {
        var request=json.object().put("schema_version","1.0.0").put("kind","AUTHOR").put("original_key","original-context-key");
        request.set("request",json.tree(new FormalEvaluationContextProtocol.Author("1.0.0",definition(),"Original request")));
        schema.request("Recovery",bytes(json.canonical(request)));
        request.put("actor_id",id(8));assertThrows(SchemaRejection.class,()->schema.request("Recovery",bytes(json.canonical(request))));
        request.remove("actor_id");request.put("kind","DECISION");assertThrows(SchemaRejection.class,()->schema.request("Recovery",bytes(json.canonical(request))));
    }
    @Test void availabilityCannotClaimBoundPreparation() {
        var view=json.object().put("schema_version","1.0.0").put("maximum_page_size",20).putNull("unavailable_reason");
        view.set("current_context",json.tree(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(id(2),"1","1","1")));
        view.putObject("features").put("context_bindings",true).put("bound_preparation",true);
        assertThrows(SchemaRejection.class,()->schema.validate("Availability",view));
    }
}
