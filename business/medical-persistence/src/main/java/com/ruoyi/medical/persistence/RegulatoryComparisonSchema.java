package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** A fixed selection, not a model prompt. Stored inside the immutable B14 INPUTS definition. */
@Component public final class RegulatoryComparisonSchema {
 public static final String SCENARIO_KIND="REGULATION_COMPARISON",PREFIX="regulatory-comparison-v1\n";
 private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
  .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public record Edition(PermissionProtocol.Basis source,String parseVersion,String approvalRevision,String rightsRevision,String lawIdentifier,String edition,String jurisdiction,String effectiveDate){}
 public record Clause(String clauseKey,DatasetProtocol.EvidenceRef baseline,DatasetProtocol.EvidenceRef target,PermissionProtocol.Basis applicabilityRule){}
 public record Selection(String schemaVersion,String scenarioKind,String asOfDate,String coverageMode,Edition baseline,Edition target,List<Clause> clauses){public Selection{clauses=List.copyOf(clauses);}}
 public Selection read(JsonNode value)throws Exception{
  try{return readValidated(value);}catch(IllegalArgumentException|NullPointerException rejected){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 private Selection readValidated(JsonNode value)throws Exception{
  var s=reader.treeToValue(value,Selection.class);
  if(!"1.0.0".equals(s.schemaVersion())||!SCENARIO_KIND.equals(s.scenarioKind())||!"SELECTED_CLAUSES".equals(s.coverageMode()))bad();
  java.time.LocalDate.parse(s.asOfDate());edition(s.baseline());edition(s.target());
  if(s.baseline().source().version_id().equals(s.target().source().version_id())||!s.baseline().lawIdentifier().equals(s.target().lawIdentifier())||!s.baseline().jurisdiction().equals(s.target().jurisdiction())||s.clauses().isEmpty()||s.clauses().size()>64)bad();
  var keys=new HashSet<String>();var refs=new HashSet<DatasetProtocol.EvidenceRef>();
  for(var c:s.clauses()){
   ProjectSchema.text(c.clauseKey(),128);if(!keys.add(c.clauseKey())||c.baseline()==null&&c.target()==null)bad();
   evidence(c.baseline(),s.baseline());evidence(c.target(),s.target());
   for(var r:Arrays.asList(c.baseline(),c.target()))if(r!=null&&!refs.add(r))bad();
   if(c.applicabilityRule()!=null)basis(c.applicabilityRule());
  }
  return s;
 }
 public Selection definition(String value)throws Exception{if(value==null||!value.startsWith(PREFIX))bad();return read(reader.readTree(value.substring(PREFIX.length())));}
 public String definition(JsonNode value)throws Exception{var s=read(value);String text=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(s)),java.nio.charset.StandardCharsets.UTF_8);if(text.length()>16000)bad();return text;}
 public JsonNode tree(Object value){return reader.valueToTree(value);}
 public List<DatasetProtocol.EvidenceRef> evidence(Selection s){var out=new ArrayList<DatasetProtocol.EvidenceRef>();for(var c:s.clauses()){if(c.baseline()!=null)out.add(c.baseline());if(c.target()!=null)out.add(c.target());}return List.copyOf(out);}
 public List<PermissionProtocol.Basis> roots(Selection s){var out=new LinkedHashSet<PermissionProtocol.Basis>();out.add(s.baseline().source());out.add(s.target().source());for(var c:s.clauses())if(c.applicabilityRule()!=null)out.add(c.applicabilityRule());return List.copyOf(out);}
 public void author(JsonNode author)throws Exception{
  String definition=author.required("specification").required("INPUTS").required("definition").asText();
  if(!definition.startsWith(PREFIX))return;
  var selected=definition(definition);for(var ref:evidence(selected))if(!author.required("project_id").asText().equals(ref.projectId()))bad();var required=new HashSet<>(roots(selected));
  var actual=new HashSet<PermissionProtocol.Basis>();for(var field:List.of("INPUTS","APPLICABLE_KNOWLEDGE","TASK_RULES"))for(var ref:author.required("specification").required(field).required("evidence"))actual.add(new PermissionProtocol.Basis(ref.required("version_id").asText(),ref.required("content_sha256").asText()));
  if(!actual.containsAll(required))bad();
  var knowledge=new HashSet<String>();for(var ref:author.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))knowledge.add(ref.required("version_id").asText());
  if(!knowledge.contains(selected.baseline().source().version_id())||!knowledge.contains(selected.target().source().version_id()))bad();
 }
 private void edition(Edition e){Objects.requireNonNull(e);basis(e.source());WireValues.uuid(e.parseVersion());WireValues.uuid(e.approvalRevision());WireValues.uuid(e.rightsRevision());ProjectSchema.text(e.lawIdentifier(),256);ProjectSchema.text(e.edition(),256);ProjectSchema.text(e.jurisdiction(),256);java.time.LocalDate.parse(e.effectiveDate());}
 private void evidence(DatasetProtocol.EvidenceRef e,Edition edition)throws Exception{if(e==null)return;new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(e));if(!edition.source().version_id().equals(e.sourceVersion())||!edition.parseVersion().equals(e.parseVersion())||!edition.approvalRevision().equals(e.approvalRevision()))bad();}
 private static void basis(PermissionProtocol.Basis b){WireValues.uuid(b.version_id());WireValues.hash(b.content_sha256());}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
