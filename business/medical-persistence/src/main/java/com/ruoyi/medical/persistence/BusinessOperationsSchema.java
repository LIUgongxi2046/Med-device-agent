package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Additive operations contract; unrelated immutable task schemas are not relaxed. */
final class BusinessOperationsSchema {
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    JsonNode read(String text)throws Exception {return parser.readTree(text);}
    JsonNode request(byte[] bytes,Set<String> fields)throws Exception {
        if(bytes==null || bytes.length>32768)invalid();var value=parser.readTree(bytes);
        if(value==null || !value.isObject() || value.size()!=fields.size())invalid();
        var names=value.fieldNames();while(names.hasNext())if(!fields.contains(names.next()))invalid();return value;
    }
    String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    String canonical(JsonNode node)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(node),java.nio.charset.StandardCharsets.UTF_8);}
    static String text(JsonNode node,String field){var value=node.get(field);if(value==null||!value.isTextual()||value.asText().isBlank())invalid();return value.asText();}
    static String id(JsonNode node,String field){String value=text(node,field);WireValues.uuid(value);return value;}
    static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
