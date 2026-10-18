package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAuthoringSchemaTest {
    private final PermissionSchema json=new PermissionSchema();
    private final ConfigurationAuthoringSchema schema=new ConfigurationAuthoringSchema(json);
    ConfigurationAuthoringSchemaTest()throws Exception { }
    ObjectNode capability()throws Exception {
        var node=json.object();node.put("schema_version","1.0.0");node.put("execution_mode","SIMULATION");node.putNull("project_id");node.put("kind","ROLE");
        node.set("source_artifact",json.tree(EvaluationFixture.ref(1)));
        byte[] bytes;try(var stream=getClass().getClassLoader().getResourceAsStream("agent-release.schema.json")){bytes=Objects.requireNonNull(stream).readAllBytes();}
        var binding=json.object().put("schema_ref","urn:medical:agent-release:1.0.0").put("schema_sha256",DigestUtils.sha256Hex(bytes));
        node.set("input_schema_ref",binding);node.set("output_schema_ref",binding.deepCopy());node.putArray("tool_scope");return node;
    }
    ObjectNode agent()throws Exception {
        var node=json.object();node.put("schema_version","1.0.0");node.put("execution_mode","SIMULATION");node.putNull("project_id");node.put("name","Simulation draft");
        node.set("role_version",json.tree(EvaluationFixture.ref(1)));node.set("prompt_revision",json.tree(EvaluationFixture.ref(2)));node.set("model_config_revision",json.tree(EvaluationFixture.ref(3)));
        var limits=json.object();var definition=schema.read(new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("configuration-authoring.schema.json")).readAllBytes(),StandardCharsets.UTF_8));
        definition.at("/$defs/Limits/properties").fields().forEachRemaining(e->{var property=e.getValue();if(property.has("const"))limits.set(e.getKey(),property.get("const"));
            else if("integer".equals(property.path("type").textValue())||property.path("$ref").asText().endsWith("/Seconds"))limits.put(e.getKey(),30);
            else limits.put(e.getKey(),e.getKey().equals("currency")?"CNY":"10");});
        limits.put("heartbeat_interval_seconds",5);limits.put("max_total_tokens","20");node.set("limits",limits);return node;
    }
    @Test void acceptsOnlyClosedDraftCapabilityAndSimulationAgent()throws Exception {
        assertDoesNotThrow(()->schema.validate(capability(),false));var prompt=capability().put("kind","PROMPT");schema.validate(prompt,false);schema.validate(agent(),true);
    }
    @Test void rejectsEveryMissingRequiredFieldAndCallerAuthority()throws Exception {
        for(boolean agent:List.of(false,true)) {
            var valid=agent?agent():capability();var keys=new ArrayList<String>();valid.fieldNames().forEachRemaining(keys::add);
            for(String key:keys){var broken=valid.deepCopy();broken.remove(key);assertThrows(SchemaRejection.class,()->schema.validate(broken,agent),key);}
            for(String key:List.of("status","approved","dependency_manifest","tenant_id","created_by","content_sha256")) {
                var broken=valid.deepCopy();broken.put(key,"forged");assertThrows(SchemaRejection.class,()->schema.validate(broken,agent),key);
            }
        }
    }
    @Test void refusesUnpublishedSchemaAndRuntimeTools()throws Exception {
        var unknown=capability();((ObjectNode)unknown.get("input_schema_ref")).put("schema_sha256","f".repeat(64));assertThrows(RuntimeException.class,()->schema.validate(unknown,false));
        var tools=capability();tools.withArray("tool_scope").add("shell");assertThrows(SchemaRejection.class,()->schema.validate(tools,false));
        var kind=capability().put("kind","TOOL");assertThrows(SchemaRejection.class,()->schema.validate(kind,false));
    }
    @Test void refusesDuplicateKindsPaidLimitsAndInconsistentTokens()throws Exception {
        var duplicate=agent();duplicate.set("prompt_revision",duplicate.get("role_version"));assertThrows(SchemaRejection.class,()->schema.validate(duplicate,true));
        var paid=agent();((ObjectNode)paid.get("limits")).put("max_cost_amount","1");assertThrows(SchemaRejection.class,()->schema.validate(paid,true));
        var tokens=agent();((ObjectNode)tokens.get("limits")).put("max_total_tokens","1");assertThrows(SchemaRejection.class,()->schema.validate(tokens,true));
    }
    @Test void strictParserRejectsDuplicateTrailingAndOversizedBodies()throws Exception {
        String body=json.canonical(capability());assertThrows(Exception.class,()->schema.request((body+" {}").getBytes(StandardCharsets.UTF_8),false));
        assertThrows(Exception.class,()->schema.request(body.replace("\"kind\":\"ROLE\"","\"kind\":\"ROLE\",\"kind\":\"PROMPT\"").getBytes(StandardCharsets.UTF_8),false));
        assertThrows(SchemaRejection.class,()->schema.request(new byte[65_537],false));
    }
    @Test void idempotencyIdentityBindsPathSessionContextAndBody()throws Exception {
        var request=capability();String hash=schema.requestHash(request,"1",EvaluationFixture.id(7),false);
        assertNotEquals(hash,schema.requestHash(request,"2",EvaluationFixture.id(7),false));
        assertNotEquals(hash,schema.requestHash(request,"1",EvaluationFixture.id(8),false));
        assertNotEquals(hash,schema.requestHash(request,"1",EvaluationFixture.id(7),true));
        assertNotEquals(hash,schema.requestHash(request.put("kind","PROMPT"),"1",EvaluationFixture.id(7),false));
    }
    @Test void validatesClosedReadEnvelopeAndRejectsInjectedReadAuthority()throws Exception {
        var fixed=json.object().put("schema_version","1.0.0").put("tenant_id",EvaluationFixture.id(90)).put("config_id",EvaluationFixture.id(91))
                .put("version_id",EvaluationFixture.id(92)).put("created_by",EvaluationFixture.id(93)).put("created_at","2026-10-18T00:00:00Z");
        fixed.set("request",capability());var view=json.object();view.set("definition",fixed);view.put("content_sha256","a".repeat(64));
        view.put("status","DRAFT");view.put("revision","1");view.put("production_eligible",false);
        var envelope=schema.envelope(view,EvaluationFixture.id(94));assertEquals("1",envelope.path("resource_revision").textValue());
        view.put("approved",true);assertThrows(SchemaRejection.class,()->schema.envelope(view,EvaluationFixture.id(94)));
    }

}
