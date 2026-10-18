package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Independently versioned human-reanchor protocol; frozen shared contracts are unchanged. */
@Component
public final class AnnotationAnchorSchema {
 static final int MAXIMUM_HISTORY=100, MAXIMUM_REQUEST_BYTES=1_048_576, MAXIMUM_LIST_BYTES=16_777_216;
 private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
   .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
   .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 private final PermissionSchema json; private final AnnotationThreadSchema threads; private final JsonSchema schema; private final String manifest;
 public AnnotationAnchorSchema(PermissionSchema json,AnnotationThreadSchema threads) throws IOException,NoSuchAlgorithmException {
  this.json=json;this.threads=threads;byte[] bytes;
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/annotation-anchors.schema.json"))){bytes=in.readAllBytes();}
  var document=reader.readTree(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  schema.initializeValidators();var names=document.required("$defs").fieldNames();while(names.hasNext())definition(names.next()).initializeValidators();
  manifest=hash(json.object().put("schema_version","1.0.0").put("annotation-anchors.schema.json",DigestUtils.sha256Hex(bytes)).put("permission_manifest",json.manifestSha256()));
 }
 AnnotationAnchorProtocol.Create create(byte[] bytes)throws IOException {
  if(bytes==null||bytes.length>MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  var n=read(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());validate("Create",n);
  var r=reader.treeToValue(n,AnnotationAnchorProtocol.Create.class);
  for(String v:List.of(r.expectedRevision(),r.expectedDocumentRevision(),r.expectedAnchorRevision())){if(PermissionSchema.revision(v).signum()<=0)invalid();PermissionSchema.next(v);}
  json.requireId(r.documentRevision());json.requireId(r.expectedAnchorId());AnnotationDecisionSchema.content(r.reason());
  threads.selection(n.required("selection"));if(!r.documentRevision().equals(r.selection().documentRevision())||!r.humanConfirmed())invalid();return r;
 }
 JsonNode read(String value)throws IOException {var n=reader.readTree(value);if(n==null)invalid();return n;}
 AnnotationAnchorProtocol.Command command(String value)throws IOException{return reader.treeToValue(read(value),AnnotationAnchorProtocol.Command.class);}
 void validate(String name,JsonNode n){if(n==null||!definition(name).validate(n).isEmpty())invalid();}
 void anchor(JsonNode n)throws IOException{validate("Anchor",n);threads.selection(n.required("selection"));
  var revision=PermissionSchema.revision(n.required("annotation_revision").asText());if(revision.signum()<=0)invalid();
  if(n.required("previous_anchor_id").isNull()){if(!java.math.BigInteger.ONE.equals(revision)||!n.required("reason").isNull())invalid();}
  else{if(revision.compareTo(java.math.BigInteger.ONE)<=0||n.required("previous_anchor_id").equals(n.required("anchor_id"))||n.required("reason").isNull())invalid();AnnotationDecisionSchema.content(n.required("reason").asText());}
  if(!n.required("document_revision").equals(n.required("selection").required("document_revision"))||!hash(n.required("selection")).equals(n.required("selection_sha256").asText()))invalid();}
 JsonNode envelope(String name,JsonNode data,String requestId,String revision)throws IOException{
  validate(name,data);if(!data.required("revision").asText().equals(revision))invalid();
  var response=json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,revision,data));threads.shared().validate("interfaces",response,"Envelope");
  if(json.canonical(response).getBytes(StandardCharsets.UTF_8).length>MAXIMUM_LIST_BYTES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return response;
 }
 JsonNode receipt(String value)throws IOException{
  var response=read(value);threads.shared().validate("interfaces",response,"Envelope");var d=response.required("data");validate("Receipt",d);anchor(d.required("effective_anchor"));
  var unsigned=((ObjectNode)d).deepCopy();unsigned.remove("receipt_sha256");
  if(!hash(unsigned).equals(d.required("receipt_sha256").asText())||!hash(d.required("effective_anchor")).equals(d.required("effective_anchor_sha256").asText())
    ||!response.required("resource_revision").equals(d.required("revision"))||!PermissionSchema.next(d.required("previous_revision").asText()).equals(d.required("revision").asText())
    ||!PermissionSchema.next(d.required("previous_document_revision").asText()).equals(d.required("document_head_revision").asText())
    ||!PermissionSchema.next(d.required("previous_anchor_revision").asText()).equals(d.required("anchor_revision").asText())
    ||!d.required("revision").equals(d.required("effective_anchor").required("annotation_revision"))
    ||!d.required("document_revision").equals(d.required("effective_anchor").required("document_revision"))
    ||!d.required("previous_anchor_id").equals(d.required("effective_anchor").required("previous_anchor_id")))invalid();return response;
 }
 String requestHash(String id,AnnotationAnchorProtocol.Create request,String context,String session)throws IOException{
  var n=json.object().put("method","POST").put("path","/api/annotation-threads/"+id+"/reanchors").put("schema_manifest_sha256",manifest).put("context_header",context).put("session_id",session);n.set("body",json.tree(request));return hash(n);
 }
 String hash(Object n)throws IOException{return DigestUtils.sha256Hex(json.canonicalObject(n));}
 String manifestSha256(){return manifest;}
 private JsonSchema definition(String name){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
