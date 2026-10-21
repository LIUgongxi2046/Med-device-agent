package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Closed local historical 084 display contract, never an execution or approval grant. */
public final class FormalEvaluationPolicyReadSchema {
 private final JsonSchema schema;
 public FormalEvaluationPolicyReadSchema(){
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-policy-read.schema.json"))){
   schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  }catch(IOException failure){throw new UncheckedIOException(failure);}
  schema.initializeValidators();
 }
 public JsonNode validate(JsonNode value){
  if(value==null||!schema.validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return value;
 }
}
