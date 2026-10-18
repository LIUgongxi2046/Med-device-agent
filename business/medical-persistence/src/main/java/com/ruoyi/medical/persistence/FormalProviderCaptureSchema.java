package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.json.JsonMapper;import com.networknt.schema.*;import com.ruoyi.medical.contracts.*;import java.util.*;
/** Exact local raw-entity container profile; no frozen catalogue expansion. */
public final class FormalProviderCaptureSchema {
 public static final String REF="urn:medical:provider-response-capture:1.0.0#/$defs/CaptureArtifact";
 public static final String SHA256="99e4a445fac2018857fd989ba2e0d5df1605d31fce28add1865c1498990c935d";
 private final JsonSchema schema;
 public FormalProviderCaptureSchema()throws Exception{byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/provider-response-capture.schema.json"))){bytes=in.readAllBytes();}if(!SHA256.equals(WireValues.sha256(bytes)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new JsonMapper().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}
 public void require(JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("CaptureArtifact")).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
