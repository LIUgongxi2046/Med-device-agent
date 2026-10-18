package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class ConfigurationAuthoringSchema {
    static final int MAXIMUM_REQUEST_BYTES=65_536;
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json;
    private final SchemaCatalogue catalogue;
    private final JsonSchema schema;
    private final String manifest;
    public ConfigurationAuthoringSchema(PermissionSchema json)throws Exception {
        this.json=json;catalogue=SchemaCatalogue.fromClasspath();
        byte[] bytes=resource("configuration-authoring.schema.json");
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var identity=json.object();
        for(String name:List.of("configuration-authoring.schema.json","agent-release.schema.json","contracts/catalogue.json"))
            identity.put(name,DigestUtils.sha256Hex(resource(name)));
        manifest=DigestUtils.sha256Hex(json.canonical(identity));
    }
    JsonNode request(byte[] bytes,boolean agent)throws IOException {
        if(bytes.length>MAXIMUM_REQUEST_BYTES)invalid();var value=reader.readTree(bytes);validate(value,agent);return value;
    }
    void validate(JsonNode value,boolean agent) {
        verify(agent?"Agent":"Capability",value);
        if(agent) {
            ProjectSchema.text(value.path("name").textValue(),100);
            var ids=new HashSet<String>();for(String field:List.of("role_version","prompt_revision","model_config_revision"))
                if(!ids.add(value.path(field).path("version_id").textValue()))invalid();
            var limits=value.path("limits");
            if(number(limits,"max_input_tokens").add(number(limits,"max_output_tokens")).compareTo(number(limits,"max_total_tokens"))>0)invalid();
            if(limits.path("heartbeat_interval_seconds").intValue()>=limits.path("heartbeat_timeout_seconds").intValue())invalid();
        } else for(String field:List.of("input_schema_ref","output_schema_ref")) {
            var binding=value.path(field);catalogue.requirePublished(binding.path("schema_ref").textValue(),binding.path("schema_sha256").textValue());
        }
    }
    private static BigInteger number(JsonNode node,String field){return PermissionSchema.revision(node.path(field).textValue());}
    JsonNode read(String value)throws IOException{return reader.readTree(value);}
    String hash(JsonNode value)throws IOException{return DigestUtils.sha256Hex(json.canonical(value));}
    String requestHash(JsonNode request,String context,String session,boolean agent)throws IOException {
        var value=json.object();value.put("method","POST");value.put("path",agent?"/api/simulation-agent-versions":"/api/capability-versions");
        value.put("context_header",context);value.put("session_id",session);value.set("body",request);return hash(value);
    }
    void verify(String definition,JsonNode value){
        if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
    }
    JsonNode envelope(JsonNode data,String requestId){
        var response=json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,"1",data));
        verify(data.has("operation_id")?"ReceiptEnvelope":data.path("definition").has("dependency_manifest")?"AgentViewEnvelope":"CapabilityViewEnvelope",response);
        return response;
    }
    String manifest(){return manifest;}
    void manifest(JsonNode value){catalogue.validate("simulation-execution",value,"SimulationCandidateDependencyManifest");}
    private byte[] resource(String name)throws IOException{try(var stream=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))){return stream.readAllBytes();}}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
