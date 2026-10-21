package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Immutable B21 author inputs. Publication and source approvals are genuine B14/source records. */
@Component public final class RegistrationDossierSchema {
 public static final String PREFIX="registration-dossier-v1\n", KIND="REGISTRATION_DOSSIER";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public record Source(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal,String nature){}
 public record Decision(String value,String sourceKey){}
 public record Path(Decision applicationType,Decision deviceType,Decision classification,Decision classificationCode,Decision jurisdiction,Decision regulationEdition,PermissionProtocol.Basis templateVersion,String templateSourceKey,List<String> originalCertificateSourceKeys,List<String> changeHistorySourceKeys){}
 public record Directory(String code,String title,String parent,String sectionId,String responsible,String applicability,String rationale,List<String> decisionSourceKeys,List<String> documentKeys,String page,String signatureRequirement){}
 public record Document(String key,String directoryCode,String sourceKey,String number,String version,String status,String subject,String validThrough,String approvalSourceKey,List<String> recordSourceKeys,List<String> attachmentSourceKeys){}
 public record EvidenceUsage(String sourceKey,String documentKey,String subject,String recordedAt,String validThrough,String approvalSourceKey){}
 public record Field(String key,String directoryCode,JsonNode value,String sourceKey){}
 public record Procedure(String documentKey,String scope,String responsibility,String workflow,List<String> recordSourceKeys){}
 public record Condition(String key,String applicability,String rationale,List<String> sourceKeys){}
 public record Body(String key,String nodeId,String directoryCode,JsonNode content,List<String> sourceKeys,List<String> fieldKeys,List<String> documentKeys){}
 public record TemplateNode(String code,String title,String parent,List<String> requiredFieldKeys,boolean requiresDocument,boolean requiresRecord,boolean requiresAttachment){}
 public record TemplateCondition(String key,List<String> directoryCodes){}
 public record Template(String applicationType,String deviceType,String regulationEdition,PermissionProtocol.Basis templateVersion,List<TemplateNode> nodes,List<TemplateCondition> conditions){}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,String asOfDate,Path path,List<Source> sources,List<Directory> directory,List<Document> documents,List<EvidenceUsage> evidenceUsage,List<Field> fields,List<Procedure> procedures,List<Condition> conditions,List<Body> body){}
 public Selection read(JsonNode n)throws Exception{try{
  var s=json.treeToValue(n,Selection.class);
  if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||s.sectionIds().isEmpty()||s.sectionIds().size()>128||s.sources().isEmpty()||s.sources().size()>512||s.directory().isEmpty()||s.directory().size()>512||s.body().size()>1024||s.documents().size()>512||s.fields().size()>1024||s.evidenceUsage().size()>1024)bad();
  unique(s.sectionIds());s.sectionIds().forEach(WireValues::uuid);date(s.asOfDate());
  var sources=new HashSet<String>();for(var x:s.sources()){
   text(x.key());if(!sources.add(x.key())||!Set.of("REGULATION","APPROVED_TEMPLATE","APPROVED_DECISION","REGISTERED_DOCUMENT","ORIGINAL_RECORD","ORIGINAL_ATTACHMENT","DRAFT").contains(x.nature()))bad();
   WireValues.uuid(x.source().version_id());WireValues.hash(x.source().content_sha256());WireValues.uuid(x.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(x.evidence()));if(!x.source().version_id().equals(x.evidence().sourceVersion()))bad();text(x.literal());
  }
  var p=s.path();WireValues.uuid(p.templateVersion().version_id());WireValues.hash(p.templateVersion().content_sha256());for(var d:List.of(p.applicationType(),p.deviceType(),p.classification(),p.classificationCode(),p.jurisdiction(),p.regulationEdition())){text(d.value());if(!sources.contains(d.sourceKey()))bad();}
  if(!sources.contains(p.templateSourceKey())||!Set.of("FIRST","RENEWAL","CHANGE_REGISTRATION","CHANGE_FILING","UNKNOWN").contains(p.applicationType().value())||!Set.of("NON_IVD","IVD","UNKNOWN").contains(p.deviceType().value()))bad();refs(p.originalCertificateSourceKeys(),sources,false);refs(p.changeHistorySourceKeys(),sources,false);
  var codes=new HashSet<String>();for(var d:s.directory()){
   text(d.code());text(d.title());text(d.responsible());optional(d.parent());optional(d.rationale());optional(d.page());text(d.signatureRequirement());
   if(!codes.add(d.code())||!s.sectionIds().contains(d.sectionId())||!status(d.applicability()))bad();refs(d.decisionSourceKeys(),sources,false);unique(d.documentKeys());
  }
  for(var d:s.directory())if(!d.parent().isEmpty()&&(!codes.contains(d.parent())||!d.code().startsWith(d.parent()+".")))bad();
  var documents=new HashSet<String>();for(var d:s.documents()){
   text(d.key());text(d.version());optional(d.number());text(d.subject());optional(d.validThrough());optional(d.approvalSourceKey());if(!d.validThrough().isEmpty())date(d.validThrough());
   if(!documents.add(d.key())||!codes.contains(d.directoryCode())||!sources.contains(d.sourceKey())||!d.approvalSourceKey().isEmpty()&&!sources.contains(d.approvalSourceKey())||!Set.of("APPROVED","DRAFT","MISSING","EXPIRED","UNKNOWN").contains(d.status()))bad();refs(d.recordSourceKeys(),sources,false);refs(d.attachmentSourceKeys(),sources,false);
  }
  for(var d:s.directory()){refs(d.documentKeys(),documents,false);for(String key:d.documentKeys())if(s.documents().stream().noneMatch(x->x.key().equals(key)&&x.directoryCode().equals(d.code())))bad();}
  for(var d:s.documents())if(s.directory().stream().noneMatch(x->x.code().equals(d.directoryCode())&&x.documentKeys().contains(d.key())))bad();
  var usages=new HashSet<String>();for(var u:s.evidenceUsage()){if(!usages.add(u.documentKey()+":"+u.sourceKey())||!sources.contains(u.sourceKey())||!sources.contains(u.approvalSourceKey())||!documents.contains(u.documentKey()))bad();text(u.subject());date(u.recordedAt());optional(u.validThrough());if(!u.validThrough().isEmpty())date(u.validThrough());}
  var fields=new HashSet<String>();for(var f:s.fields()){text(f.key());optional(f.sourceKey());if(!fields.add(f.directoryCode()+":"+f.key())||!codes.contains(f.directoryCode())||!f.sourceKey().isEmpty()&&!sources.contains(f.sourceKey())||f.value()==null||f.value().isNull())bad();}
  var procedures=new HashSet<String>();for(var p1:s.procedures()){if(!procedures.add(p1.documentKey())||!documents.contains(p1.documentKey()))bad();optional(p1.scope());optional(p1.responsibility());optional(p1.workflow());refs(p1.recordSourceKeys(),sources,false);}
  var conditions=new HashSet<String>();for(var c:s.conditions()){text(c.key());optional(c.rationale());if(!conditions.add(c.key())||!status(c.applicability()))bad();refs(c.sourceKeys(),sources,false);}
  var bodies=new HashSet<String>();var bodyNodes=new HashSet<String>();for(var b:s.body()){
   text(b.key());WireValues.uuid(b.nodeId());if(!bodyNodes.add(b.nodeId())||!bodies.add(b.key())||!codes.contains(b.directoryCode())||b.content()==null||!b.content().isObject()||!b.content().path("kind").isTextual())bad();text(projection(b.content()));refs(b.sourceKeys(),sources,true);unique(b.fieldKeys());refs(b.documentKeys(),documents,false);
   for(String k:b.fieldKeys())if(!fields.contains(b.directoryCode()+":"+k))bad();for(String k:b.documentKeys())if(s.documents().stream().noneMatch(d->d.key().equals(k)&&d.directoryCode().equals(b.directoryCode())))bad();
  }return s;
 }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public Template template(String literal)throws Exception{
  var t=json.readValue(literal,Template.class);text(t.applicationType());text(t.deviceType());text(t.regulationEdition());WireValues.uuid(t.templateVersion().version_id());WireValues.hash(t.templateVersion().content_sha256());if(t.nodes()==null||t.nodes().isEmpty()||t.nodes().size()>512||t.conditions()==null||t.conditions().size()>128)bad();
  var codes=new HashSet<String>();for(var n:t.nodes()){text(n.code());text(n.title());optional(n.parent());if(!codes.add(n.code())||n.requiredFieldKeys()==null)bad();unique(n.requiredFieldKeys());n.requiredFieldKeys().forEach(RegistrationDossierSchema::text);}
  for(var n:t.nodes())if(!n.parent().isEmpty()&&(!codes.contains(n.parent())||!n.code().startsWith(n.parent()+".")))bad();
  var conditions=new HashSet<String>();for(var c:t.conditions()){text(c.key());if(!conditions.add(c.key()))bad();refs(c.directoryCodes(),codes,true);}return t;
 }
 public JsonNode tree(Object value){return json.valueToTree(value);}public JsonNode parse(String literal)throws Exception{return json.readTree(literal);}
 public Selection definition(String d)throws Exception{if(!d.startsWith(PREFIX))bad();return read(json.readTree(d.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String d=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(d.length()>200000)bad();return d;}
 public void author(JsonNode a)throws Exception{
  String d=a.required("specification").required("INPUTS").required("definition").asText();if(!d.startsWith(PREFIX))return;var s=definition(d);var roots=new HashSet<PermissionProtocol.Basis>();for(var r:a.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))roots.add(new PermissionProtocol.Basis(r.required("version_id").asText(),r.required("content_sha256").asText()));for(var x:s.sources())if(!roots.contains(x.source())||!a.required("project_id").asText().equals(x.evidence().projectId()))bad();boolean template=false;for(var t:a.required("applicability").required("templates"))if(tree(s.path().templateVersion()).equals(t))template=true;if(!template)bad();
 }
 static String projection(JsonNode n){if(n==null)return "";if(n.isTextual())return n.textValue();var parts=new ArrayList<String>();n.elements().forEachRemaining(x->parts.add(projection(x)));return String.join(" ",parts);}
 static boolean contains(JsonNode content,JsonNode value){if(content.equals(value))return true;if(value.isTextual()&&content.isTextual())return content.asText().contains(value.asText());for(var child:content)if(contains(child,value))return true;return false;}
 private static boolean status(String s){return Set.of("REQUIRED","NOT_APPLICABLE","UNKNOWN").contains(s);}
 private static void refs(List<String> refs,Set<String> allowed,boolean required){if(refs==null||required&&refs.isEmpty()||refs.size()>512||!allowed.containsAll(refs))bad();unique(refs);}
 private static void unique(List<String> v){if(new HashSet<>(v).size()!=v.size())bad();}
 private static void date(String s){try{java.time.LocalDate.parse(s);}catch(java.time.DateTimeException e){bad();}}private static void text(String s){ProjectSchema.text(s,200000);}private static void optional(String s){if(s==null||s.length()>16000)bad();}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
