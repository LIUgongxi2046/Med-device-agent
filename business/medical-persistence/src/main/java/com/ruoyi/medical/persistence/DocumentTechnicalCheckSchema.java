package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Local technical-only contract. Its coverage never represents professional review coverage. */
public final class DocumentTechnicalCheckSchema {
    public static final String KIND="STRUCTURE_FIXED_REFERENCES";
    public static final String CHECKER="urn:medical:document-technical-check:structure-fixed-references:1.0.0";
    public static final String REF="urn:medical:document-technical-check:1.0.0#/$defs/";
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema validator;
    private final SchemaCatalogue catalogue;
    private final String manifest;
    private final String schemaDocumentSha256;

    public DocumentTechnicalCheckSchema() throws Exception {
        catalogue=SchemaCatalogue.fromClasspath();
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-technical-check.schema.json"))){
            byte[] bytes=in.readAllBytes();
            schemaDocumentSha256=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
            validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
            validator.initializeValidators();
            manifest=hash(Map.of("schema_version","1.0.0","checker_id",CHECKER,
                "document-technical-check.schema.json",schemaDocumentSha256,"interfaces",bodySchemaSha256()));
        }
    }
    public String ref(String definition){if(!java.util.Set.of("Input","Result","Coverage","Request","Receipt","Read","Recovery","ExecutionManifest").contains(definition))invalid();return REF+definition;}
    public String manifest(){return manifest;}
    public String schemaDocumentSha256(){return schemaDocumentSha256;}
    public String bodySchemaRef(){return catalogue.schemaRef("interfaces","DocumentBody");}
    public String bodySchemaSha256(){return catalogue.schemaDigest("interfaces");}
    SchemaCatalogue catalogue(){return catalogue;}
    public JsonNode read(String value)throws IOException{return json.readTree(value);}
    public String canonical(Object value)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}
    public String hash(Object value)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}
    public ObjectNode input(JsonNode value){
        var result=shape("Input",value);
        if(!bodySchemaRef().equals(result.required("body_schema_ref").textValue())
            ||!bodySchemaSha256().equals(result.required("body_schema_sha256").textValue()))mismatch();
        return result;
    }
    public ObjectNode coverage(JsonNode value){
        var result=shape("Coverage",value);
        if(!result.required("selection_sha256").equals(result.required("delivered_sha256")))mismatch();
        return result;
    }
    public ObjectNode result(JsonNode value)throws IOException{
        var result=shape("Result",value);var input=input(result.required("input"));var coverage=coverage(result.required("coverage"));
        String digest=hash(input);
        if(!digest.equals(result.required("input_sha256").textValue())
            ||!digest.equals(coverage.required("selection_sha256").textValue()))mismatch();
        return result;
    }
    public ObjectNode parse(String definition,byte[] bytes,int maximumBytes)throws IOException{
        if(maximumBytes<1)throw new IllegalArgumentException("DOCUMENT_CHECK_CAPACITY_REQUIRED");
        if(bytes==null||bytes.length==0)invalid();
        if(bytes.length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        try{return validate(definition,json.readTree(bytes));}
        catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    public ObjectNode validate(String definition,JsonNode value)throws IOException{
        ref(definition);
        if("Input".equals(definition))return input(value);
        if("Result".equals(definition))return result(value);
        if("Coverage".equals(definition))return coverage(value);
        var result=shape(definition,value);
        if("Request".equals(definition)){
            if(PermissionSchema.revision(result.required("expected_revision").textValue()).signum()<=0)invalid();
            ProjectSchema.text(result.required("reason").textValue(),2000);
        }else if("ExecutionManifest".equals(definition)){
            if(!manifest.equals(result.required("schema_manifest_sha256").textValue()))mismatch();
        }else if("Recovery".equals(definition)){
            if("COMPLETED".equals(result.required("state").textValue())){
                var receipt=validate("Receipt",result.required("receipt"));
                if(!result.required("check_id").equals(receipt.required("check_id"))
                    ||!result.required("request_body_sha256").equals(receipt.required("request_body_sha256")))mismatch();
            }
        }else if("Read".equals(definition)){
            var receipt=validate("Receipt",result.required("receipt"));
            var findings=result(result.required("result"));
            var delivered=coverage(result.required("coverage"));
            var input=findings.required("input");
            if(!receipt.required("input_sha256").equals(findings.required("input_sha256"))
                ||!receipt.required("document_id").equals(input.required("document_id"))
                ||!receipt.required("document_revision").equals(input.required("document_revision"))
                ||!receipt.required("technical_result").equals(findings.required("technical_result"))
                ||!delivered.equals(findings.required("coverage"))
                ||!hash(delivered).equals(receipt.required("coverage_artifact").required("content_sha256").textValue())
                ||!hash(findings).equals(receipt.required("findings_artifact").required("content_sha256").textValue()))mismatch();
        }
        return result;
    }
    private ObjectNode shape(String definition,JsonNode value){
        if(value==null||!value.isObject()||!validator.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
        return (ObjectNode)value.deepCopy();
    }
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
