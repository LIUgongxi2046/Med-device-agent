package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Additive contract manifest; imported downstream authority validates its own complete request. */
final class GruExecutionSchema {
 private final JsonNode document;private final SchemaCatalogue schemas;private final String manifest;
 GruExecutionSchema(PermissionSchema json)throws Exception {schemas=SchemaCatalogue.fromClasspath();byte[] bytes;
  try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/gru-execution.schema.json"))){bytes=stream.readAllBytes();}
  document=new JsonMapper().readTree(bytes);manifest=WireValues.sha256(json.canonical(json.object().put("gru-execution.schema.json",WireValues.sha256(bytes)).put("permission_manifest",json.manifestSha256())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 void validate(String definition,JsonNode value){var bound=document.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bound).put("$ref","#/$defs/"+definition);if(!schemas.compileApiSchema(bound).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 String manifest(){return manifest;}
}
