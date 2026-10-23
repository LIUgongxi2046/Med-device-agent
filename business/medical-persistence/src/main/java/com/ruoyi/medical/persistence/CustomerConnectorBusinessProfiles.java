package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class CustomerConnectorBusinessProfiles {
    private static final String ACTION="connector.business_profile.created";
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final PermissionMapper permissions;
    private final JsonSchema schema;
    private final String manifest;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public CustomerConnectorBusinessProfiles(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,PermissionMapper permissions)throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.permissions=permissions;byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/connector-business-profile.schema.json"))){bytes=input.readAllBytes();}
        manifest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();
    }
    JsonNode input(byte[] bytes)throws IOException {var value=reader.readTree(bytes);validate("Create",value);profile(value.required("profile"));return value;}
    String manifest(){return manifest;}
    void profile(JsonNode value){validate("Profile",value);ProjectSchema.text(value.required("name").asText(),200);ProjectSchema.text(value.required("description").asText(),4000);}
    boolean ownerActive(EnterpriseAccess.Admission a,JsonNode record){
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)))",Boolean.class,a.context().tenant_id(),record.required("profile").required("owner_user_id").asText()));
    }
    JsonNode save(EnterpriseAccess.Admission a,Map<String,Object> definition,JsonNode profile)throws Exception {
        profile(profile);var record=record(a,definition,profile);if(!ownerActive(a,record))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        changed(db.update("INSERT INTO customer_connector_business_profile(tenant_id,config_revision,definition_sha256,profile_json,profile_sha256,owner_user_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),str(definition,"config_revision"),str(definition,"definition_sha256"),json.canonical(profile),record.required("profile_sha256").asText(),profile.required("owner_user_id").asText(),manifest,str(definition,"created_by"),KnowledgeFeedbackProofs.time(definition.get("created_at")),str(definition,"classification")));
        String request=UUID.randomUUID().toString();changed(permissions.saveAudit(a.actorId(),str(definition,"project_id"),UUID.randomUUID().toString(),ACTION,json.canonical(record),request,manifest,str(definition,"classification")));changed(permissions.saveEvent(a.actorId(),UUID.randomUUID().toString(),str(definition,"config_revision"),"1",ACTION,json.canonical(record)));
        same(record,read(a,definition));return record;
    }
    JsonNode read(EnterpriseAccess.Admission a,Map<String,Object> definition)throws Exception {
        same(a.context().tenant_id(),str(definition,"tenant_id"));String id=str(definition,"config_revision");var rows=db.queryForList("SELECT * FROM customer_connector_business_profile WHERE tenant_id=? AND config_revision=?",a.context().tenant_id(),id);
        if(rows.isEmpty()){Integer count=db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action=? AND JSON_UNQUOTE(JSON_EXTRACT(resource_ref,'$.config_revision'))=?",Integer.class,a.context().tenant_id(),ACTION,id);if(count==null||count!=0)mismatch();return null;}
        if(rows.size()!=1)mismatch();var row=rows.get(0);var profile=reader.readTree(str(row,"profile_json"));profile(profile);var record=record(a,definition,profile);
        same(record.required("profile_sha256").asText(),str(row,"profile_sha256"));same(str(definition,"definition_sha256"),str(row,"definition_sha256"));same(manifest,str(row,"schema_manifest_sha256"));same("1.0.0",str(row,"schema_version"));same(str(definition,"classification"),str(row,"classification"));same(str(definition,"created_by"),str(row,"created_by"));same(KnowledgeFeedbackProofs.time(definition.get("created_at")),KnowledgeFeedbackProofs.time(row.get("created_at")));same(profile.required("owner_user_id").asText(),str(row,"owner_user_id"));
        var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action=? AND result='SUCCESS' AND schema_manifest_sha256=? AND JSON_UNQUOTE(JSON_EXTRACT(resource_ref,'$.config_revision'))=?",a.context().tenant_id(),str(definition,"project_id"),str(definition,"created_by"),ACTION,manifest,id);if(audits.size()!=1)mismatch();same(record,reader.readTree(str(audits.get(0),"resource_ref")));
        var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=?",a.context().tenant_id(),id,ACTION);if(events.size()!=1)mismatch();same(record,reader.readTree(str(events.get(0),"payload")));return record;
    }
    JsonNode approval(EnterpriseAccess.Admission a,Map<String,Object> definition,String hash)throws Exception {
        var record=read(a,definition);if(record==null){if(hash!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return null;}
        if(hash==null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);WireValues.hash(hash);same(record.required("profile_sha256").asText(),hash);return record;
    }
    String decisionHash(JsonNode body,JsonNode record)throws Exception {return CustomerConnectorProtocol.hash(record==null?body:json.tree(Map.of("decision",body,"business_profile_sha256",record.required("profile_sha256").asText(),"business_profile_schema_sha256",manifest)));}
    private JsonNode record(EnterpriseAccess.Admission a,Map<String,Object> definition,JsonNode profile)throws Exception {
        if("PROJECT".equals(profile.required("scope_kind").asText())&&!str(definition,"project_id").equals(profile.required("scope_project_id").asText()))mismatch();
        var result=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("config_revision",str(definition,"config_revision")).put("definition_sha256",str(definition,"definition_sha256")).put("profile_sha256",CustomerConnectorProtocol.hash(profile)).put("schema_manifest_sha256",manifest).put("created_by",str(definition,"created_by")).put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(definition.get("created_at"))));result.set("profile",profile);validate("Stored",result);return result;
    }
    private void validate(String name,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
    private static void changed(int count){if(count!=1)mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
