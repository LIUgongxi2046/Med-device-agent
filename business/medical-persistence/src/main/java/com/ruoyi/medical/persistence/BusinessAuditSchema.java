package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.json.JsonMapper;import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;import com.ruoyi.medical.contracts.*;import java.util.*;import java.time.*;import java.nio.charset.StandardCharsets;
public final class BusinessAuditSchema{
 public static final String REF="urn:medical:business-audit:1.0.0#/$defs/";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema;private final String digest;
 public BusinessAuditSchema()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/business-audit.schema.json"))){byte[] raw=in.readAllBytes();digest=WireValues.sha256(raw);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(raw),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}}
 public String documentSha256(){return digest;}
 JsonNode read(String value,int max)throws Exception{if(value==null)bad();if(value.getBytes(StandardCharsets.UTF_8).length>max)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return json.readTree(value);}
 ObjectNode validate(String kind,JsonNode node){if(!Set.of("Page","Availability","SavedEvent","Query").contains(kind)||node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)).validate(node).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if("Page".equals(kind)){if(node.path("complete").booleanValue()!=node.path("next_cursor").isNull()||node.required("items").size()>node.at("/query/limit").intValue())bad();Instant from=Instant.parse(node.at("/query/from").textValue()),to=Instant.parse(node.at("/query/to").textValue());if(!from.isBefore(to))bad();Set<String> ids=new HashSet<>();Instant prior=null;String priorId=null;for(var item:node.required("items")){String id=item.required("audit_id").textValue();if(!ids.add(id))bad();Instant at=Instant.parse(item.required("occurred_at").textValue());if(at.isBefore(from)||!at.isBefore(to))bad();if(prior!=null&&(at.isAfter(prior)||at.equals(prior)&&id.compareTo(priorId)>=0))bad();prior=at;priorId=id;var target=item.required("target");if(!target.required("project_id").equals(node.at("/query/project_id"))||!target.required("read_url").textValue().equals("/api/documents/"+target.required("document_id").textValue()+"/revisions/"+target.required("document_revision").textValue()))bad();if(!node.at("/query/actor_id").isNull()&&!node.at("/query/actor_id").equals(item.required("actor_id")))bad();if(!node.at("/query/document_id").isNull()&&!node.at("/query/document_id").equals(target.required("document_id")))bad();}}
  return (ObjectNode)node.deepCopy();
 }
 String canonical(Object v)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(v),StandardCharsets.UTF_8);}
 String hash(Object v)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(v));}
 static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
