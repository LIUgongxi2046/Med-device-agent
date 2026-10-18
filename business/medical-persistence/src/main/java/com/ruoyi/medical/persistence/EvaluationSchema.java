package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class EvaluationSchema {
    static final String SAMPLE_MANIFEST_REF="urn:medical:evaluation-results:1.0.0#/$defs/SampleManifest";
    static final String EXPECTED_ITEMS_REF="urn:medical:evaluation-results:1.0.0#/$defs/ExpectedItems";
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifest;
    public EvaluationSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json=json; shared=SchemaCatalogue.fromClasspath(); byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/evaluation-results.schema.json"))) { bytes=input.readAllBytes(); }
        shared.requirePublished(SAMPLE_MANIFEST_REF,DigestUtils.sha256Hex(bytes));
        shared.requirePublished(EXPECTED_ITEMS_REF,DigestUtils.sha256Hex(bytes));
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes), SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var identity=json.object(); identity.put("schema_version","1.0.0");
        identity.put("schema",DigestUtils.sha256Hex(bytes)); identity.put("permission_manifest",json.manifestSha256()); manifest=hash(identity);
    }
    EvaluationProtocol.Create create(byte[] bytes) throws IOException {
        var request=bind("Create",bytes,EvaluationProtocol.Create.class); ProjectSchema.text(request.reason(),2000);
        var samples=new HashSet<String>(); var familySplits=new HashMap<String,String>();var inputFamilies=new HashMap<String,String>();
        for(var sample:request.samples()) {
            if(!samples.add(sample.sample_id())) invalid();
            if(!hash(expectedItems(sample)).equals(sample.expected_ref().content_sha256()))invalid();
            if(sample.expectations().stream().map(EvaluationProtocol.Expectation::dimension).distinct().count()!=sample.expectations().size())invalid();
            for(var expectation:sample.expectations()) {
                if(!expectation.dimension().equals("EVAL-11")&&!expectation.control_item_ids().isEmpty())invalid();
            }
            var split=familySplits.putIfAbsent(sample.source_family(),sample.split());
            if(split!=null&&!split.equals(sample.split())) invalid();
            var family=inputFamilies.putIfAbsent(sample.input_ref().version_id(),sample.source_family());if(family!=null&&!family.equals(sample.source_family()))invalid();
        }
        if(request.candidates().stream().map(PermissionProtocol.Basis::version_id).distinct().count()!=request.candidates().size()) invalid();
        if((long)request.samples().size()*request.candidates().size()*request.required_dimensions().size()*request.variance_policy().repeat_count()>10000) invalid();
        // The supplied sample list must be the exact frozen artifact, not an editable subset.
        if(!hash(json.tree(request.samples())).equals(request.sample_manifest_artifact().content_sha256())) invalid();
        return request;
    }
    JsonNode expectedItems(EvaluationProtocol.Sample sample) {
        var value=json.object().put("schema_version","1.0.0").put("sample_id",sample.sample_id()).put("source_family",sample.source_family()).put("document_type",sample.document_type());
        value.set("input_ref",json.tree(sample.input_ref()));value.set("expectations",json.tree(sample.expectations()));validate("ExpectedItems",value);return value;
    }
    EvaluationProtocol.Append append(byte[] bytes) throws IOException {
        var request=bind("Append",bytes,EvaluationProtocol.Append.class); PermissionSchema.revision(request.expected_revision());
        ProjectSchema.text(request.reason(),2000);
        if((request.numerator()==null)!=(request.denominator()==null)||(request.control_numerator()==null)!=(request.control_denominator()==null)) invalid();
        if(request.status().equals("SCORED") && (request.numerator()==null||request.output_ref()==null||request.evidence_refs().isEmpty())) invalid();
        if(!request.status().equals("SCORED") && (request.numerator()!=null||request.control_numerator()!=null)) invalid();
        if(request.numerator()!=null && new java.math.BigInteger(request.numerator()).compareTo(new java.math.BigInteger(request.denominator()))>0) invalid();
        if(request.control_numerator()!=null && new java.math.BigInteger(request.control_numerator()).compareTo(new java.math.BigInteger(request.control_denominator()))>0) invalid();
        return request;
    }
    EvaluationProtocol.Finalize finalizeRequest(byte[] bytes) throws IOException { var r=bind("Finalize",bytes,EvaluationProtocol.Finalize.class); PermissionSchema.revision(r.expected_revision()); return r; }
    EvaluationProtocol.Definition definition(String value) throws IOException {
        var d=bind("Definition",value.getBytes(java.nio.charset.StandardCharsets.UTF_8),EvaluationProtocol.Definition.class);
        if(!create(json.canonicalObject(d.request()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(d.request())) invalid();
        PermissionSchema.revision(d.authority_revision()); return d;
    }
    EvaluationProtocol.Score score(String value) throws IOException {
        var s=bind("Score",value.getBytes(java.nio.charset.StandardCharsets.UTF_8),EvaluationProtocol.Score.class);
        append(json.canonicalObject(s.observation()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); PermissionSchema.revision(s.authority_revision()); return s;
    }
    private <T> T bind(String name,byte[] bytes,Class<T> type)throws IOException { var node=reader.readTree(bytes); validate(name,node); return reader.treeToValue(node,type); }
    void validate(String name,JsonNode node) { if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty()) invalid(); }
    String hash(Object value)throws IOException { return DigestUtils.sha256Hex(json.canonicalObject(value)); }
    String requestHash(String path,Object request,String context,String session)throws IOException {
        var value=json.object(); value.put("method","POST");value.put("path",path);value.put("schema_version","1.0.0");value.putNull("query");
        value.put("context_header",context);value.put("session_id",session);value.set("body",json.tree(request));return hash(value);
    }
    JsonNode envelope(JsonNode value,String requestId,String revision) { var e=json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,revision,value));shared.validate("interfaces",e,"Envelope");return e; }
    JsonNode receipt(String value)throws IOException {
        var response=read(value);shared.validate("interfaces",response,"Envelope");validate("Receipt",response.required("data"));
        var data=response.required("data");PermissionSchema.revision(data.required("revision").textValue());
        if(!response.required("resource_revision").equals(data.required("revision")))invalid();
        if(data.required("verdict").isNull()!=data.required("result_sha256").isNull())invalid();
        if(!data.required("verdict").isNull()) {
            String status=switch(data.required("verdict").textValue()){case "PASS"->"COMPLETED";case "FAIL"->"FAILED";default->"INSUFFICIENT_EVIDENCE";};
            if(!status.equals(data.required("status").textValue())||!data.required("score_id").isNull())invalid();
        }else if(!java.util.Set.of("QUEUED","RUNNING").contains(data.required("status").textValue()))invalid();
        return response;
    }
    JsonNode read(String value)throws IOException { return reader.readTree(value); }
    String manifestSha256(){return manifest;}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
