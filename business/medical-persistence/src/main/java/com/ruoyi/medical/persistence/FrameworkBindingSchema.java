package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public final class FrameworkBindingSchema {
    static final int MAX_BYTES=262144;
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json;private final FrameworkBindingCatalogue catalogue;private final JsonSchema schema;private final SchemaCatalogue contracts;
    final String schemaSha256;
    public FrameworkBindingSchema(PermissionSchema json,FrameworkBindingCatalogue catalogue)throws Exception {
        this.json=json;this.catalogue=catalogue;contracts=SchemaCatalogue.fromClasspath();
        byte[] bytes;try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/framework-binding.schema.json"))){bytes=stream.readAllBytes();}
        schemaSha256=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }
    JsonNode read(byte[] bytes)throws Exception {if(bytes.length>MAX_BYTES)bad();return parser.readTree(bytes);}
    JsonNode read(String value)throws Exception{return read(value.getBytes(StandardCharsets.UTF_8));}
    void validate(String definition,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())bad();}
    String hash(JsonNode value)throws Exception{return WireValues.sha256(json.canonical(value).getBytes(StandardCharsets.UTF_8));}
    String actionDigest()throws Exception {
        var pins=json.object();pins.set("actions",catalogue.document(json));pins.put("framework_schema_sha256",schemaSha256);
        for(String name:List.of("framework-job-command.schema.json","framework-form.schema.json","framework-binding-native.schema.json","framework-binding-native-actions.json"))try(var input=getClass().getResourceAsStream("/"+name)) {
            // The optional admin module owns this contract. Absence prevents producing/consuming a release, not startup.
            if(input==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            pins.put(name,WireValues.sha256(input.readAllBytes()));
        }
        return hash(pins);
    }
    void manifest(JsonNode m)throws Exception {
        validate("Manifest",m);if(!actionDigest().equals(m.required("action_catalogue_sha256").asText()))bad();
        var ids=new HashSet<String>();var operations=new HashSet<String>();
        for(var module:m.required("modules")) {
            contracts.bind("interfaces",module,"ManagementModule");
            if(!ids.add(module.required("module_id").asText()))bad();
            if(!"BOUND".equals(module.required("binding_status").asText()))continue;
            if(!m.required("source_commit").equals(module.required("source_commit")))bad();
            if(!module.required("route_name").asText().matches("[A-Za-z][A-Za-z0-9_-]{0,99}")
                ||!module.required("component_ref").asText().matches("[A-Za-z0-9_/-]{1,200}")
                ||module.required("component_ref").asText().contains(".."))bad();
            var permissions=new HashSet<String>();
            for(var op:module.required("operations")) {
                var a=catalogue.require(op.required("operation_id").asText());
                if(!operations.add(a.operation_id())||!a.module_id().equals(module.required("module_id").asText()))bad();
                var expected=json.tree(a).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)expected).remove(List.of("module_id","environment"));
                if(!expected.equals(op))bad();
                if(!"ANY".equals(a.environment())&&!(a.environment().equals(m.required("runtime_environment").asText())||"DESIGN_OR_DEVELOPMENT".equals(a.environment())&&Set.of("DESIGN","DEVELOPMENT").contains(m.required("runtime_environment").asText())))bad();
                permissions.add(a.permission_code());
            }
            var declared=new HashSet<String>();for(var p:module.required("permission_codes"))if(!declared.add(p.asText()))bad();
            if(!declared.equals(permissions))bad();
        }
        if(ids.size()!=18)bad();
    }
    static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
