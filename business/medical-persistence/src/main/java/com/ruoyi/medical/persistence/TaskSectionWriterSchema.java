package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;

/** Strict N04 serialization; parsed bytes grant no execution authority. */
public final class TaskSectionWriterSchema {
    public static final String INPUT_REF="urn:medical:task-section-writer-input:1.0.0";
    public static final int MAXIMUM_BYTES=8*1024*1024;
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    public String manifest()throws Exception{try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/task-section-writer-input.schema.json"))){return WireValues.sha256(in.readAllBytes());}}
    public JsonNode read(byte[] bytes)throws Exception {if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return parser.readTree(bytes);}
    public JsonNode read(String text)throws Exception{return read(text.getBytes(StandardCharsets.UTF_8));}
    public TaskSectionWriterInput input(byte[] bytes)throws Exception{var node=read(bytes);try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/task-section-writer-input.schema.json"))){var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(in),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());if(!schema.validate(node).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}return parser.treeToValue(node,TaskSectionWriterInput.class);}
    public JsonNode tree(Object value){return parser.valueToTree(value);}
    public byte[] bytes(Object value)throws Exception{return ContentReadAuthorization.canonicalBytes(value instanceof JsonNode?value:tree(value));}
    public String canonical(Object value)throws Exception{return new String(bytes(value),StandardCharsets.UTF_8);}
    public String hash(Object value)throws Exception{return WireValues.sha256(bytes(value));}
    public ArtifactProtocol.SectionDraft draft(JsonNode node)throws Exception{return TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.SECTION_DRAFT,node).read();}
    public ArtifactProtocol.SectionDraft draft(JsonNode node,String declaredRef,String declaredSha)throws Exception {
        return ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.SECTION_DRAFT,node,declaredRef,declaredSha).read();
    }

}
