package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RuleBusinessDefinitionStore {
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final String manifest;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public RuleBusinessDefinitionStore(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource data,PermissionSchema json)throws IOException,java.security.NoSuchAlgorithmException {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/rule-business-definition.schema.json"))){bytes=input.readAllBytes();}
        manifest=WireValues.sha256(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }
    void validate(String name,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    JsonNode createInput(byte[] bytes)throws IOException{var value=reader.readTree(bytes);validate("Create",value);definition(value.required("definition"));return value;}
    String manifest(){return manifest;}
    void definition(JsonNode value){validate("Definition",value);ProjectSchema.text(value.required("name").asText(),200);ProjectSchema.text(value.required("condition").asText(),4000);}
    JsonNode save(EnterpriseAccess.Admission admission,RuleVersionProtocol.Stored rule,JsonNode definition,String operation)throws IOException {
        definition(definition);same(rule.tenant_id(),admission.context().tenant_id());
        var value=json.object().put("schema_version","1.0.0").put("tenant_id",rule.tenant_id()).put("rule_version",rule.rule_version()).put("definition_sha256",hash(definition)).put("rule_content_sha256",rule.content_sha256()).put("schema_manifest_sha256",manifest).put("operation_id",operation).put("created_by",rule.created_by()).put("created_at",rule.created_at());
        value.set("definition",definition);validate("Stored",value);
        if(db.update("INSERT INTO rule_business_definition(tenant_id,rule_version,definition_json,definition_sha256,rule_content_sha256,schema_version,schema_manifest_sha256,operation_id,created_by,created_at,classification) VALUES(?,?,?,?,?,'1.0.0',?,?,?,?,?)",rule.tenant_id(),rule.rule_version(),json.canonical(definition),hash(definition),rule.content_sha256(),manifest,operation,rule.created_by(),java.time.LocalDateTime.ofInstant(java.time.Instant.parse(rule.created_at()),java.time.ZoneOffset.UTC),definition.required("classification").asText())!=1)mismatch();
        return value;
    }
    JsonNode read(EnterpriseAccess.Admission admission,RuleVersionProtocol.Stored rule)throws IOException {
        same(rule.tenant_id(),admission.context().tenant_id());var rows=db.queryForList("SELECT * FROM rule_business_definition WHERE tenant_id=? AND rule_version=?",rule.tenant_id(),rule.rule_version());
        if(rows.isEmpty()){
            var count=db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='rule.draft_created' AND JSON_CONTAINS_PATH(payload,'one','$.business_definition')=1",Integer.class,rule.tenant_id(),rule.rule_version());
            if(count==null||count!=0)mismatch();return null;
        }
        if(rows.size()!=1)mismatch();var row=rows.get(0);var definition=reader.readTree(str(row,"definition_json"));definition(definition);
        same(hash(definition),str(row,"definition_sha256"));same(rule.content_sha256(),str(row,"rule_content_sha256"));same(manifest,str(row,"schema_manifest_sha256"));same("1.0.0",str(row,"schema_version"));same(rule.created_by(),str(row,"created_by"));same(rule.created_at(),DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(row.get("created_at"))));same(definition.required("classification").asText(),str(row,"classification"));
        var value=json.object().put("schema_version","1.0.0").put("tenant_id",rule.tenant_id()).put("rule_version",rule.rule_version()).put("definition_sha256",str(row,"definition_sha256")).put("rule_content_sha256",rule.content_sha256()).put("schema_manifest_sha256",manifest).put("operation_id",str(row,"operation_id")).put("created_by",rule.created_by()).put("created_at",rule.created_at());value.set("definition",definition);validate("Stored",value);
        var operations=db.queryForList("SELECT request_sha256,response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND target_id=? AND operation_name='TP-03' AND state='COMPLETED' AND http_status=201",rule.tenant_id(),str(row,"operation_id"),rule.created_by(),rule.rule_version());if(operations.size()!=1)mismatch();var response=reader.readTree(str(operations.get(0),"response_ref"));
        var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND subject_id=? AND action='rule.draft_created' AND result='SUCCESS' AND request_id=?",rule.tenant_id(),rule.created_by(),response.required("request_id").asText());if(audits.size()!=1)mismatch();var audit=reader.readTree(str(audits.get(0),"resource_ref"));same(value,audit.required("business_definition"));same(response.required("data"),audit.required("after"));
        var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='rule.draft_created'",rule.tenant_id(),rule.rule_version());if(events.size()!=1)mismatch();var event=reader.readTree(str(events.get(0),"payload"));same(value,event.required("business_definition"));same(response.required("data"),event.required("creation"));return value;
    }
    JsonNode approval(EnterpriseAccess.Admission admission,RuleVersionProtocol.Stored rule,String expected)throws IOException {
        var value=read(admission,rule);
        if(value==null){if(expected!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return null;}
        if(expected==null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);WireValues.hash(expected);same(expected,value.required("definition_sha256").asText());return value;
    }
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private String hash(JsonNode value)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(value));}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
