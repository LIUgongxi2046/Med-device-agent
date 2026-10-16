package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class SourceExactSchema {
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final String manifest;
    public SourceExactSchema(PermissionSchema json) throws Exception {
        this.json=json; shared=SchemaCatalogue.fromClasspath();
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-exact.schema.json"))) { bytes=input.readAllBytes(); }
        var document=reader.readTree(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var names=document.required("$defs").fieldNames();
        while(names.hasNext()) definition(names.next()).initializeValidators();
        var identity=json.object(); identity.put("schema_version","1.0.0"); identity.put("source-exact.schema.json",DigestUtils.sha256Hex(bytes));
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/contracts/catalogue.json"))) {
            identity.put("contracts/catalogue.json",DigestUtils.sha256Hex(input.readAllBytes()));
        }
        manifest=DigestUtils.sha256Hex(json.canonical(identity));
    }
    SourceExactProtocol.PublicationInput publication(byte[] bytes) throws IOException {
        var node=reader.readTree(bytes); require("PublicationInput",node);
        var value=reader.treeToValue(node,SourceExactProtocol.PublicationInput.class);
        ProjectSchema.text(value.reason(),4000); PermissionSchema.revision(value.expected_revision());
        var unique=new HashSet<String>();
        for(var field:value.fields()) {
            checkField(field);
            var identity=json.object(); identity.put("field_kind",field.field_kind()); identity.put("chunk_id",field.chunk_id());
            identity.put("start",field.start_codepoint()); identity.put("end",field.end_codepoint());
            if(field.alias_value()==null) identity.putNull("alias_value"); else identity.put("alias_value",field.alias_value());
            if(!unique.add(json.canonical(identity))) invalid();
        }
        return value;
    }
    SourceExactProtocol.SearchInput search(byte[] bytes) throws IOException {
        var node=reader.readTree(bytes); require("SearchInput",node);
        var value=reader.treeToValue(node,SourceExactProtocol.SearchInput.class); literal(value.query()); return value;
    }
    void checkField(SourceExactProtocol.Field field) throws IOException {
        require("Field",json.tree(field)); literal(field.raw_value());
        if(field.start_codepoint()<0 || field.end_codepoint()<=field.start_codepoint()
                || (field.alias_value()==null)!=(field.alias_reason()==null)) invalid();
        if(field.alias_value()!=null) {
            literal(field.alias_value()); ProjectSchema.text(field.alias_reason(),4000);
            if(field.alias_value().equals(field.raw_value())) invalid();
        }
    }
    void verifyField(SourceExactProtocol.Field field,String chunk,String raw,String quote,String locator) throws IOException {
        checkField(field);
        verifyRange(field.raw_value(),field.chunk_id(),field.quote_hash(),field.locator_sha256(),field.start_codepoint(),field.end_codepoint(),chunk,raw,quote,locator);
    }
    void verifyRange(String value,String chunkId,String quoteHash,String locatorHash,int start,int end,String chunk,String raw,String quote,String locator) throws IOException {
        literal(value); WireValues.hash(quote); shared.validate("dataset",reader.readTree(locator),"locator");
        int count=raw.codePointCount(0,raw.length());
        if(!chunkId.equals(chunk) || !quoteHash.equals(quote) || start<0 || end<=start
                || !DigestUtils.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(quote)
                || !DigestUtils.sha256Hex(json.canonical(reader.readTree(locator))).equals(locatorHash) || end>count) mismatch();
        String actual=raw.substring(raw.offsetByCodePoints(0,start),raw.offsetByCodePoints(0,end)); if(!actual.equals(value)) mismatch();
    }
    JsonNode parse(String value) throws IOException { return reader.readTree(value); }
    JsonNode receipt(String value) throws IOException {
        var result=parse(value); shared.validate("interfaces",result,"Envelope"); return result;
    }
    SourceExactProtocol.PublicationInput storedInput(JsonNode node) throws IOException {
        return publication(json.canonical(node).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    JsonNode locator(String value) throws IOException { var node=reader.readTree(value); shared.validate("dataset",node,"locator"); return node; }
    void require(String name,JsonNode value) { if(value==null || !definition(name).validate(value).isEmpty()) invalid(); }
    JsonNode envelope(String definition,JsonNode data,String request,String revision) {
        require(definition,data); var value=json.tree(new InterfaceProtocol.Envelope("1.0.0",request,revision,data));
        shared.validate("interfaces",value,"Envelope"); return value;
    }
    String requestHash(String path,Object input,String context,String session) throws IOException {
        var request=json.object(); request.put("method","POST"); request.put("path",path); request.put("schema_version","1.0.0");
        request.putNull("query"); request.put("context_header",context); request.put("session_id",session); request.set("body",json.tree(input));
        return DigestUtils.sha256Hex(json.canonical(request));
    }
    String manifestSha256() { return manifest; }
    private static void literal(String value) throws IOException {
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>512
                || value.codePoints().anyMatch(code -> code>=0xd800 && code<=0xdfff)) invalid();
        java.nio.charset.StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
    }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
