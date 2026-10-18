package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.json.JsonMapper;import com.fasterxml.jackson.core.StreamReadFeature;
import com.networknt.schema.*;import com.ruoyi.medical.contracts.*;import java.util.*;import java.io.*;import java.math.BigInteger;
public final class FormalEvaluationContextSchema {
 private final PermissionSchema json;private final JsonSchema root;private final String manifest;
 private final JsonMapper decoder=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
 public FormalEvaluationContextSchema(PermissionSchema json)throws IOException{this.json=json;byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-context.schema.json"))){bytes=in.readAllBytes();}manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);root=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(decoder.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());root.initializeValidators();}
 public String manifest(){return manifest;}public String canonical(JsonNode node)throws IOException{return json.canonical(node);}public String hash(JsonNode node)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(node));}
 JsonNode read(String raw)throws IOException{return decoder.readTree(raw);}
 public FormalEvaluationContextProtocol.BoundStart start(byte[] bytes)throws IOException{return decoder.treeToValue(request("BoundStart",bytes),FormalEvaluationContextProtocol.BoundStart.class);}
 public FormalEvaluationContextProtocol.Author author(byte[] bytes)throws IOException{var n=request("Author",bytes);var value=decoder.treeToValue(n,FormalEvaluationContextProtocol.Author.class);check(value.definition());reason(value.reason());return value;}
 public FormalEvaluationContextProtocol.Decide decide(byte[] bytes)throws IOException{var n=request("Decide",bytes);var value=decoder.treeToValue(n,FormalEvaluationContextProtocol.Decide.class);if(PermissionSchema.revision(value.expectedRevision()).signum()<=0)invalid();reason(value.reason());return value;}
 FormalEvaluationContextProtocol.Definition definition(JsonNode n)throws IOException{validate("Definition",n);var d=decoder.treeToValue(n,FormalEvaluationContextProtocol.Definition.class);check(d);return d;}
 private JsonNode request(String kind,byte[] bytes)throws IOException{if(bytes==null||bytes.length<1||bytes.length>1048576)invalid();var n=decoder.readTree(bytes);validate(kind,n);return n;}
 private void validate(String kind,JsonNode node){if(node==null||!root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)).validate(node).isEmpty())invalid();}
 static void check(FormalEvaluationContextProtocol.Definition d){Set<String> nodes=new HashSet<>();for(var n:d.nodeBindings())if(!nodes.add(n.nodeKind()))invalid();}
 private static void reason(String r){if(r.isBlank()||!r.equals(r.strip())||r.indexOf('\0')>=0)invalid();}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
