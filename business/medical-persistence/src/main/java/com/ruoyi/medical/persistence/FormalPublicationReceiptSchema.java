package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.networknt.schema.*;import com.ruoyi.medical.contracts.*;import java.io.IOException;import java.util.Objects;
/** Independent local response contract; does not change frozen manifest or authoring hashes. */
public final class FormalPublicationReceiptSchema {
 private final JsonSchema schema;
 public FormalPublicationReceiptSchema()throws IOException{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-agent-publication-workflow.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}schema.initializeValidators();}
 public JsonNode validate(JsonNode n){if(n==null||!schema.validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return n;}
}
