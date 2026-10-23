package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class TaskCreationAgentDirectorySchema {
    record Query(int limit,String after,String q) { }
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    public TaskCreationAgentDirectorySchema(PermissionSchema json)throws IOException,java.security.NoSuchAlgorithmException {
        this.json=json;shared=SchemaCatalogue.fromClasspath();var reader=JsonMapper.builder().build();var documents=new HashMap<String,String>();
        for(String name:List.of("task-creation-agent-directory.schema.json","projects.schema.json")) {
            try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/"+name))) {
                var definition=reader.readTree(input);documents.put(definition.required("$id").asText(),reader.writeValueAsString(definition));
            }
        }
        var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,builder->builder.schemaLoaders(loaders->loaders.schemas(iri->{
            var value=documents.get(iri);if(value==null)throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return value;
        })));
        schema=factory.getSchema(SchemaLocation.of("urn:medical:task-creation-agent-directory:1.0.0"),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();for(String kind:List.of("Query","Filters","Entry","Directory"))definition(kind).initializeValidators();
    }
    Query query(Map<String,String[]> parameters) {
        var value=json.object();
        for(var entry:parameters.entrySet()) {
            if(entry.getValue()==null||entry.getValue().length!=1||entry.getValue()[0]==null)invalid();String text=entry.getValue()[0];
            if(entry.getKey().equals("limit")){if(!text.matches("(?:[1-9]|[1-9][0-9]|100)"))invalid();value.put("limit",Integer.parseInt(text));}else value.put(entry.getKey(),text);
        }
        verify("Query",value);var query=new Query(value.has("limit")?value.required("limit").intValue():20,text(value,"after"),text(value,"q"));
        if(query.q()!=null)ProjectSchema.text(query.q(),256);return query;
    }
    JsonNode filters(Query query){var value=json.object().put("limit",query.limit());value.set("q",json.tree(query.q()));verify("Filters",value);return value;}
    JsonNode envelope(JsonNode value) {
        verify("Directory",value);if(!value.required("tenant_id").equals(value.required("current_context").required("tenant_id"))
                ||value.required("items").size()>value.required("query").required("limit").intValue()
                ||value.required("complete").booleanValue()!=value.required("next_after").isNull())mismatch();
        String previous=null;var agents=new HashSet<String>();
        for(var item:value.required("items")) {
            String id=item.required("release_id").asText();if(previous!=null&&previous.compareTo(id)>=0||!agents.add(item.required("agent_version_id").asText()))mismatch();previous=id;
            var templates=new HashSet<String>();for(var basis:item.required("template_versions"))if(!templates.add(basis.required("version_id").asText()))mismatch();
        }
        var result=json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),value.required("current_context").required("context_revision").asText(),value));
        shared.validate("interfaces",result,"Envelope");return result;
    }
    private JsonSchema definition(String kind){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind));}
    private void verify(String kind,JsonNode value){if(!definition(kind).validate(value).isEmpty())invalid();}
    private static String text(JsonNode value,String key){return value.has(key)?value.required(key).asText():null;}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
