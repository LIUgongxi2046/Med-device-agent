package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.networknt.schema.*;
import java.util.Objects;

/** Closed additive DC-04 request contract. */
final class SharedFactLifecycleCodec {
 final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 final String manifest; private final JsonNode schema;
 SharedFactLifecycleCodec()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/shared-fact-lifecycle.schema.json"))){byte[] bytes=in.readAllBytes();manifest=WireValues.sha256(bytes);schema=json.read(bytes);}}
 JsonNode request(String name,byte[] bytes)throws Exception{
  if(bytes.length>128*1024)throw new SchemaRejection(ErrorCode.FILE_TOO_LARGE);
  var value=json.read(bytes);var target=schema.required("$defs").required(name);
  var compiled=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(target,SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  if(!compiled.validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return value;
 }
}
