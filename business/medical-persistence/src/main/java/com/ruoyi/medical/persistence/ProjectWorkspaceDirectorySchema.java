package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class ProjectWorkspaceDirectorySchema {
    public record Query(int limit,String after,String q,String status,String responsible) {
        ProjectProtocol.Query projects(String boundary) { return new ProjectProtocol.Query(limit,boundary,null,status,responsible,null,q); }
    }
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    public ProjectWorkspaceDirectorySchema(PermissionSchema json) throws IOException,java.security.NoSuchAlgorithmException {
        this.json=json;shared=SchemaCatalogue.fromClasspath();var reader=JsonMapper.builder().build();var documents=new HashMap<String,String>();
        for(String name:List.of("project-workspace-directory.schema.json","projects.schema.json")) {
            try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/"+name))) {var value=reader.readTree(input);documents.put(value.required("$id").asText(),reader.writeValueAsString(value));}
        }
        var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,builder->builder.schemaLoaders(loaders->loaders.schemas(iri->{String value=documents.get(iri);if(value==null)throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return value;})));
        schema=factory.getSchema(SchemaLocation.of("urn:medical:project-workspace-directory:1.0.0"),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();
        for(String kind:List.of("Query","Filters","Directory"))definition(kind).initializeValidators();
    }
    Query query(Map<String,String[]> parameters) {
        var value=json.object();
        for(var entry:parameters.entrySet()) {
            if(entry.getValue()==null||entry.getValue().length!=1||entry.getValue()[0]==null)invalid();String text=entry.getValue()[0];
            if(entry.getKey().equals("limit")){if(!text.matches("(?:[1-9]|[1-9][0-9]|100)"))invalid();value.put("limit",Integer.parseInt(text));}else value.put(entry.getKey(),text);
        }
        verify("Query",value);var query=new Query(value.has("limit")?value.required("limit").intValue():10,text(value,"after"),text(value,"q"),text(value,"status"),text(value,"responsible_user_id"));
        if(query.q()!=null)ProjectSchema.text(query.q(),256);return query;
    }
    JsonNode filters(Query query) {
        var value=json.object().put("limit",query.limit());value.set("q",json.tree(query.q()));value.set("status",json.tree(query.status()));value.set("responsible_user_id",json.tree(query.responsible()));verify("Filters",value);return value;
    }
    JsonNode envelope(JsonNode value) {
        verify("Directory",value);String tenant=value.required("tenant_id").asText();var filters=value.required("query");var items=value.required("items");
        if(!tenant.equals(value.required("current_context").required("tenant_id").asText())||items.size()>filters.required("limit").intValue())mismatch();
        var total=new BigInteger(value.required("total").asText());WireValues.revision(total.toString());if(total.compareTo(BigInteger.valueOf(items.size()))<0)mismatch();String previous=null;
        for(var item:items) {
            var project=item.required("project");String id=project.required("project_id").asText();if(!tenant.equals(project.required("tenant_id").asText())||previous!=null&&previous.compareTo(id)>=0)mismatch();
            if(!project.required("responsible_user_id").equals(item.required("responsible").required("user_id")))mismatch();
            if(!filters.required("status").isNull()&&!filters.required("status").equals(project.required("status")))mismatch();
            if(!filters.required("responsible_user_id").isNull()&&!filters.required("responsible_user_id").equals(project.required("responsible_user_id")))mismatch();
            var responsible=item.required("responsible");if(!responsible.required("identity_available").booleanValue()&&(!responsible.required("display_name").isNull()||!responsible.required("username").isNull()))mismatch();
            var materials=item.required("materials");for(String key:List.of("registered","approved","not_approved"))WireValues.revision(materials.required(key).asText());
            if(!new BigInteger(materials.required("registered").asText()).equals(new BigInteger(materials.required("approved").asText()).add(new BigInteger(materials.required("not_approved").asText()))))mismatch();
            previous=id;
        }
        var next=value.required("next_after");if(!next.isNull()&&items.size()!=filters.required("limit").intValue())mismatch();
        var response=json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),value.required("current_context").required("context_revision").asText(),value));shared.validate("interfaces",response,"Envelope");return response;
    }
    private JsonSchema definition(String kind){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind));}
    private void verify(String kind,JsonNode value){if(!definition(kind).validate(value).isEmpty())invalid();}
    private static String text(JsonNode value,String key){return value.has(key)?value.required(key).asText():null;}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
