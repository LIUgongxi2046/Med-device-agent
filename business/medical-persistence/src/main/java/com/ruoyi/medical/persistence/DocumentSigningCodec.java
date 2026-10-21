package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
final class DocumentSigningCodec {
 final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();final String manifest;private final com.networknt.schema.JsonSchema schema;
 DocumentSigningCodec()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-signing-v1.schema.json"))){byte[] bytes=in.readAllBytes();manifest=WireValues.sha256(bytes);schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(json.read(bytes));}}
 JsonNode request(String name,byte[] bytes)throws Exception{if(bytes==null||bytes.length>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var n=json.read(bytes);validate(name,n);return n;}
 void validate(String name,JsonNode n){if(!schema.getSubSchema(new com.networknt.schema.JsonNodePath(com.networknt.schema.PathType.JSON_POINTER).append("$defs").append(name)).validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
