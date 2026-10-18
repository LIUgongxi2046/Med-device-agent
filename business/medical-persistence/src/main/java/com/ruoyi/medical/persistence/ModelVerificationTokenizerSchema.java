package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component public final class ModelVerificationTokenizerSchema {
 private final JsonSchema reply;
 public ModelVerificationTokenizerSchema()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/model-verification-tokenizer-candidates.schema.json"))){var root=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());root.initializeValidators();reply=root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Candidates"));}}
 public JsonNode validate(JsonNode value){if(value==null||!reply.validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return value;}
}
