package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;

/** Closed shape and consistency checks only. Neither JSON nor its digest proves a source read,
 * current rights, completeness of the genuine template, independent approval or registration.
 * Every mutating host boundary must separately require private current authority and real I/O. */
public final class TaskNodeProducerSchema {
    private static final int MAX_BYTES = 8_388_608;
    private static final BigInteger U64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final Set<String> TYPES=Set.of("InputAuthor","InputManifest","InputDecide","InputReceipt","InputView","Recovery");
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final String manifest;
    private final TaskNodeProducerProfile profile;
    TaskNodeProducerProfile profile(){return profile;}

    public TaskNodeProducerSchema() throws IOException { this(TaskNodeProducerProfile.RESEARCH); }
    public TaskNodeProducerSchema(TaskNodeProducerProfile profile) throws IOException {
        this.profile=Objects.requireNonNull(profile);
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(profile.resource()))) {
            bytes = input.readAllBytes();
        }
        manifest = DigestUtils.sha256Hex(bytes);
        try { schema = SchemaCatalogue.fromClasspath().compileApiSchema(json.readTree(bytes)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IOException(failure); }
        for (String type : TYPES) definition(type).initializeValidators();
    }
    public String manifest() { return manifest; }
    public String schemaSha256() { return manifest; }
    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public String canonical(Object value) throws IOException {
        var node = tree(value); bounded(node, 0, new long[]{0}); unicode(node);
        return new String(ContentReadAuthorization.canonicalBytes(node), StandardCharsets.UTF_8);
    }
    public String hash(Object value) throws IOException { return DigestUtils.sha256Hex(canonical(value)); }
    public JsonNode request(String type, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) invalid();
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return validate(type, read(value));
        } catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public JsonNode read(String value) throws IOException {
        if (value == null || value.length() > MAX_BYTES) invalid();
        try {
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) invalid();
            return json.readTree(value);
        } catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public JsonNode validate(String type, JsonNode value) throws IOException {
        if(!TYPES.contains(type)||value==null) invalid();
        bounded(value,0,new long[]{0}); unicode(value); encodedSize(value);
        JsonNode saved=value.deepCopy();
        if(!definition(type).validate(saved).isEmpty()) invalid();
        numericStrings(saved);
        if(Set.of("InputAuthor","InputManifest").contains(type)) {
            if(!java.time.Instant.parse(text(saved,"valid_until")).isAfter(java.time.Instant.parse(text(saved,"valid_from")))) invalid();
            for(String field:List.of("maximum_bytes","maximum_artifacts","maximum_tokens","maximum_calls"))
                if(new BigInteger(text(saved,field)).compareTo(U64_MAX)>0) invalid();
            if(new BigInteger(text(saved,"maximum_artifacts")).compareTo(BigInteger.valueOf(1024))>0) invalid();
        }
        if("InputReceipt".equals(type)&&("DRAFT".equals(text(saved,"status"))!=saved.required("decision_id").isNull()))invalid();
        if("InputView".equals(type)) {
            validate("InputManifest",saved.required("manifest"));
            if(!hash(saved.required("manifest")).equals(text(saved,"rule_sha256")))invalid();
        }
        if(saved.has("schema_manifest_sha256")&&!manifest.equals(text(saved,"schema_manifest_sha256")))invalid();
        return saved;
    }
    private static String text(JsonNode value,String key) {return value.required(key).textValue();}
    private JsonSchema definition(String type) {return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));}
    private static void numericStrings(JsonNode node) {
        if(node.isObject()) {var fields=node.fields();while(fields.hasNext()){var f=fields.next();if(Set.of("revision","expected_revision","head_revision","context_revision","authority_revision","auth_epoch","governance_epoch","size_bytes","maximum_read_bytes","length").contains(f.getKey()) && f.getValue().isTextual() && new BigInteger(f.getValue().textValue()).compareTo(U64_MAX)>0)invalid();}}
        if(node.isContainerNode())for(var child:node)numericStrings(child);
    }
    private void encodedSize(JsonNode value) throws IOException {
        try {json.writeValue(new java.io.OutputStream(){long count;public void write(int value)throws IOException{add(1);}public void write(byte[] value,int off,int len)throws IOException{add(len);}private void add(int len)throws IOException{if(len>MAX_BYTES-count)throw new IOException("SCHEMA_INPUT_LIMIT");count+=len;}},value);}
        catch(IOException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    private static void bounded(JsonNode node,int depth,long[] count) {
        if(node==null || depth>48 || ++count[0]>MAX_BYTES)invalid();
        if(node.isTextual())count[0]+=node.textValue().length();
        if(node.isObject()){var names=node.fieldNames();while(names.hasNext())count[0]+=names.next().length();}
        if(count[0]>MAX_BYTES)invalid();if(node.isContainerNode())for(var child:node)bounded(child,depth+1,count);
    }
    private static void unicode(JsonNode node) throws IOException {
        try {
            if(node.isTextual())StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(node.textValue()));
            if(node.isObject()){var names=node.fieldNames();while(names.hasNext())StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(names.next()));}
            if(node.isContainerNode())for(var child:node)unicode(child);
        }catch(java.nio.charset.CharacterCodingException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
