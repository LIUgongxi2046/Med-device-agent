package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/** Exact original bytes; shared published data shape does not confer simulation identity or provenance. */
@Component public final class ModelVerificationInputSchema {
 public static final String REF="urn:medical:simulation-execution:1.0.0#/$defs/ExactInput";
 public static final String ARTIFACT_KIND="EXACT_INPUT";
 public static final int MAXIMUM_BYTES=2*1024*1024;
 private final SchemaCatalogue catalogue;private final PermissionSchema json;
 public ModelVerificationInputSchema(PermissionSchema json)throws Exception{this.json=json;catalogue=SchemaCatalogue.fromClasspath();if(!REF.equals(catalogue.schemaRef("simulation-execution","ExactInput")))throw new IllegalStateException("ExactInput schema identity changed");}
 public String schemaSha256(){return catalogue.schemaDigest("simulation-execution");}
 public JsonNode document(byte[] raw)throws IOException{
  if(raw==null||raw.length==0||raw.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var bound=catalogue.bindPublished(REF,schemaSha256(),raw);
  JsonNode value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bound.canonicalJson());
  if(!Arrays.equals(raw,json.canonical(value).getBytes(StandardCharsets.UTF_8)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  return value;
 }
}
