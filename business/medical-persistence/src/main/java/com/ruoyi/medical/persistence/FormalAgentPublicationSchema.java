package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
/** Separate bounded authoring contract; all release manifests retain their published contract. */
@Component public final class FormalAgentPublicationSchema {
 public static final int MAXIMUM_BYTES=1048576;
 private final PermissionSchema json;private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 private final JsonSchema schema;private final String manifest;
 public FormalAgentPublicationSchema(PermissionSchema json)throws IOException {this.json=json;byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-agent-publication.schema.json"))){bytes=in.readAllBytes();}schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());manifest=hash(json.object().put("schema_version","1.0.0").put("workflow_sha256",DigestUtils.sha256Hex(bytes)).put("permission_manifest",json.manifestSha256()));}
 public JsonNode request(String definition,byte[] bytes)throws IOException {if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)invalid();return validate(definition,reader.readTree(bytes));}
 public JsonNode validate(String definition,JsonNode value)throws IOException {if(value==null||canonical(value).getBytes(StandardCharsets.UTF_8).length>MAXIMUM_BYTES||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();return value;}
 public JsonNode read(String value)throws IOException{return reader.readTree(value);}
 public String canonical(Object value)throws IOException{return json.canonical(json.tree(value));}
 public String hash(Object value)throws IOException{return DigestUtils.sha256Hex(canonical(value));}
 public String manifest(){return manifest;}
 static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
