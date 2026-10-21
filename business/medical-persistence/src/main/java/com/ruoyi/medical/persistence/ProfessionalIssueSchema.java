package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Closed additive RV-02/03 wire contracts. Caller cannot submit findings or a success flag. */
public final class ProfessionalIssueSchema {
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 private final com.networknt.schema.JsonSchema root,output;private final String manifest;
 public ProfessionalIssueSchema()throws Exception{
  byte[] request,result;
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/professional-issue.schema.json"))){request=in.readAllBytes();}
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/professional-issue-result.schema.json"))){result=in.readAllBytes();}
  var factory=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012);var config=com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();root=factory.getSchema(json.read(request),config);output=factory.getSchema(json.read(result),config);
  manifest=json.hash(Map.of("schema_version","1.0.0","professional-issue.schema.json",WireValues.sha256(request),"professional-issue-result.schema.json",WireValues.sha256(result)));
 }
 public JsonNode request(byte[] bytes)throws Exception{if(bytes.length>32768)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var n=json.read(bytes);validate(n);return n;}
 public void validate(JsonNode n){if(!root.validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 public JsonNode result(JsonNode n){if(!output.validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return n;}
 public String manifest(){return manifest;}
}
