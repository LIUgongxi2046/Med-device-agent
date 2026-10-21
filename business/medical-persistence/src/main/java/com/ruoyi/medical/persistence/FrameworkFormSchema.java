package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** Closed, declarative form vocabulary. No URLs, scripts, HTML, callbacks or arbitrary JSON Schema refs. */
@Component
public final class FrameworkFormSchema {
    public static final int MAX_BYTES=65536;
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json; private final JsonSchema contract;
    final String digest;
    public FrameworkFormSchema(PermissionSchema json)throws Exception {
        this.json=json;
        byte[] bytes;try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/framework-form.schema.json"))){bytes=input.readAllBytes();}
        digest=WireValues.sha256(bytes);
        contract=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        contract.initializeValidators();
    }
    JsonNode read(byte[] bytes)throws Exception{if(bytes.length>MAX_BYTES)bad();var n=parser.readTree(bytes);if(n==null)bad();return n;}
    JsonNode read(String text)throws Exception{return parser.readTree(text);}
    void validate(String definition,JsonNode value){if(!contract.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())bad();}
    void form(JsonNode value){validate("Form",value);var keys=new HashSet<String>();for(var f:value.required("fields")){
        String key=f.required("key").asText();if(!keys.add(key)||Set.of("constructor","prototype","__proto__").contains(key))bad();
        boolean select="SELECT".equals(f.required("type").asText());if(select==f.required("options").isEmpty())bad();
    }}
    String hash(JsonNode value)throws Exception{return WireValues.sha256(json.canonical(value).getBytes(StandardCharsets.UTF_8));}
    static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
