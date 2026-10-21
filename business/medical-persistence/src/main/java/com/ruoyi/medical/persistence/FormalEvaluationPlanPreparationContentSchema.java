package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Additive closed transport. The original090 schema bytes and persisted manifest remain unchanged. */
public final class FormalEvaluationPlanPreparationContentSchema {
    private final FormalEvaluationPlanPreparationSchema original;
    private final JsonSchema root;
    public FormalEvaluationPlanPreparationContentSchema(FormalEvaluationPlanPreparationSchema original)throws Exception {
        this.original=Objects.requireNonNull(original);var documents=new HashMap<String,String>();
        for(String name:List.of("formal-evaluation-plan-preparation.schema.json","formal-evaluation-plan-preparation-content.schema.json")) {
            try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/"+name))) {
                String text=new String(in.readAllBytes(),StandardCharsets.UTF_8);documents.put(original.read(text).required("$id").asText(),text);
            }
        }
        var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,b->b.schemaLoaders(l->l.schemas(iri->{
            String value=documents.get(iri);if(value==null)throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return value;
        })));
        root=factory.getSchema(SchemaLocation.of("urn:medical:formal-evaluation-plan-preparation-content:1.0.0"),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        root.initializeValidators();
    }
    JsonNode request(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length<1||bytes.length>FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
        return validate("Request",original.read(text));
    }
    JsonNode validate(String name,JsonNode value){
        if(value==null||!root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return value;
    }
}
