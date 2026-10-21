package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;

/** N05 strict serialization. Parsing never grants review or source authority. */
public final class TaskSourceVerifierSchema {
    public static final String INPUT_REF="urn:medical:task-source-verifier-input:1.0.0";
    public static final String ENVELOPE_REF="urn:medical:task-source-verifier-dispatch:1.0.0#/$defs/Envelope";
    public static final String ENVELOPE_MANIFEST=envelopeManifest();
    private static String envelopeManifest(){try(var in=java.util.Objects.requireNonNull(TaskSourceVerifierSchema.class.getResourceAsStream("/task-source-verifier-dispatch.schema.json"))){return WireValues.sha256(in.readAllBytes());}catch(Exception e){throw new IllegalStateException(e);}}
    public static final int MAXIMUM_BYTES=16*1024*1024;
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    public JsonNode read(byte[] bytes)throws Exception {if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return parser.readTree(bytes);}
    public JsonNode read(String value)throws Exception{return read(value.getBytes(StandardCharsets.UTF_8));}
    public JsonNode tree(Object value){return parser.valueToTree(value);}
    public byte[] bytes(Object value)throws Exception{return ContentReadAuthorization.canonicalBytes(value instanceof JsonNode?value:tree(value));}
    public String canonical(Object value)throws Exception{return new String(bytes(value),StandardCharsets.UTF_8);}
    public String hash(Object value)throws Exception{return WireValues.sha256(bytes(value));}
    public String manifest()throws Exception{try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/task-source-verifier-input.schema.json"))){return WireValues.sha256(in.readAllBytes());}}
    public TaskSourceVerifierInput input(byte[] bytes)throws Exception{var node=read(bytes);try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/task-source-verifier-input.schema.json"))){var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(in),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());if(!schema.validate(node).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}return parser.treeToValue(node,TaskSourceVerifierInput.class);}
    public void coverage(JsonNode node)throws Exception{try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/task-source-verifier-coverage.schema.json"))){var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(in),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());if(!schema.validate(node).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
    public ArtifactProtocol.ReviewFindings findings(JsonNode node)throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();catalogue.validate("artifacts",node,"ReviewFindings");
        return parser.treeToValue(node,ArtifactProtocol.ReviewFindings.class);
    }
    public ArtifactProtocol.ReviewFindings findings(JsonNode node,String declaredRef,String declaredSha)throws Exception {
        return ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.REVIEW_FINDINGS,node,declaredRef,declaredSha).read();
    }

}
