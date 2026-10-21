package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** A bounded, approved statistical selection. No discovery result or inferred market fact is an input. */
@Component public final class MarketAnalysisScenarioSchema {
 public static final String PREFIX="market-analysis-scenario-v1\n",KIND="MARKET_ANALYSIS";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public record Scope(String geography,String population,String productScope,String statisticalDefinition,String countingMethod,String currency,String priceBasis,String periodStart,String periodEnd,String exclusions,String limitations){}
 public record Observation(String inputKey,String periodStart,String periodEnd){}
 public record Context(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal){}
 public record Conclusion(String key,String sectionId,String prefix,String instruction,List<String> inputKeys,List<String> calculationKeys){public Conclusion{inputKeys=List.copyOf(inputKeys);calculationKeys=List.copyOf(calculationKeys);}}
 public record Selection(String schemaVersion,String scenarioKind,String evidencePolicy,Scope scope,List<String> sectionIds,List<ReportScenarioSchema.Input> inputs,List<ReportScenarioSchema.Calculation> calculations,List<Observation> observations,List<Context> context,List<Conclusion> conclusions,PermissionProtocol.Basis conclusionReviewRule,String conclusionReviewInstruction){public Selection{sectionIds=List.copyOf(sectionIds);inputs=List.copyOf(inputs);calculations=List.copyOf(calculations);observations=List.copyOf(observations);context=List.copyOf(context);conclusions=List.copyOf(conclusions);}}
 public Selection read(JsonNode value)throws Exception{
  try{
   var s=json.treeToValue(value,Selection.class);
   if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||!"APPROVED_PINNED_SOURCES_ONLY".equals(s.evidencePolicy()))bad();
   new ReportScenarioSchema().read(reportSelection(s));
   var scope=s.scope();for(String text:List.of(scope.geography(),scope.population(),scope.productScope(),scope.statisticalDefinition(),scope.countingMethod(),scope.currency(),scope.priceBasis(),scope.exclusions(),scope.limitations()))ProjectSchema.text(text,1000);
   LocalDate start=date(scope.periodStart()),end=date(scope.periodEnd());if(start.isAfter(end))bad();
   var inputKeys=new HashSet<String>();for(var i:s.inputs())inputKeys.add(i.key());var observationKeys=new HashSet<String>();
   for(var o:s.observations()){if(!observationKeys.add(o.inputKey())||!inputKeys.contains(o.inputKey())||date(o.periodStart()).isBefore(start)||date(o.periodEnd()).isAfter(end)||date(o.periodStart()).isAfter(date(o.periodEnd())))bad();}if(!observationKeys.equals(inputKeys))bad();
   // Each statistic explicitly inherits this single frozen geography/population/definition. No silent aggregation or conversion.
   var calculationKeys=new HashSet<String>();for(var c:s.calculations())calculationKeys.add(c.key());
   if(s.context().isEmpty()||s.context().size()>32||s.conclusions().isEmpty()||s.conclusions().size()>32)bad();var keys=new HashSet<String>();
   for(var c:s.context()){ProjectSchema.text(c.key(),128);if(!keys.add(c.key()))bad();basis(c.source());WireValues.uuid(c.rightsRevision());ProjectSchema.text(c.literal(),4000);new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(c.evidence()));if(!c.source().version_id().equals(c.evidence().sourceVersion()))bad();}
   keys.clear();var prefixes=new HashSet<String>();var covered=new HashSet<String>();
   for(var c:s.conclusions()){ProjectSchema.text(c.key(),128);ProjectSchema.text(c.prefix(),256);ProjectSchema.text(c.instruction(),1000);if(!keys.add(c.key())||!prefixes.add(c.prefix())||!s.sectionIds().contains(c.sectionId())||c.inputKeys().isEmpty()||c.calculationKeys().isEmpty()||!inputKeys.containsAll(c.inputKeys())||!calculationKeys.containsAll(c.calculationKeys())||new HashSet<>(c.inputKeys()).size()!=c.inputKeys().size()||new HashSet<>(c.calculationKeys()).size()!=c.calculationKeys().size())bad();covered.add(c.sectionId());for(String k:c.calculationKeys()){var calculation=s.calculations().stream().filter(x->x.key().equals(k)).findFirst().orElseThrow();if(!calculation.sectionId().equals(c.sectionId())||!c.inputKeys().containsAll(calculation.inputs()))bad();}}
   if(!covered.equals(new HashSet<>(s.sectionIds())))bad();for(String a:prefixes)for(String b:prefixes)if(!a.equals(b)&&a.startsWith(b))bad();
   basis(s.conclusionReviewRule());ProjectSchema.text(s.conclusionReviewInstruction(),2000);return s;
  }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 static LocalDate date(String value){if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))bad();try{return LocalDate.parse(value);}catch(java.time.DateTimeException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public JsonNode tree(Object value){return json.valueToTree(value);}
 JsonNode reportSelection(Selection s){return new ReportScenarioSchema().tree(new ReportScenarioSchema.Selection("1.0.0",ReportScenarioSchema.KIND,s.sectionIds(),s.inputs(),s.calculations()));}
 JsonNode arithmeticContext(JsonNode fixed)throws Exception{var copy=(ObjectNode)fixed.deepCopy();copy.set("selection",reportSelection(read(fixed.required("selection"))));return copy;}
 public Selection definition(String value)throws Exception{if(!value.startsWith(PREFIX))bad();return read(json.readTree(value.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String value=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(value.length()>16000)bad();return value;}
 public void author(JsonNode request)throws Exception{
  String definition=request.required("specification").required("INPUTS").required("definition").asText();if(!definition.startsWith(PREFIX))return;var s=definition(definition);var knowledge=new HashSet<PermissionProtocol.Basis>();for(var r:request.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))knowledge.add(basis(r));
  for(var i:s.inputs())if(!knowledge.contains(i.source())||!request.required("project_id").asText().equals(i.evidence().projectId()))bad();for(var c:s.context())if(!knowledge.contains(c.source())||!request.required("project_id").asText().equals(c.evidence().projectId()))bad();
  var rules=new HashSet<PermissionProtocol.Basis>();for(var r:request.required("specification").required("TASK_RULES").required("evidence"))rules.add(basis(r));if(!rules.contains(s.conclusionReviewRule()))bad();
 }
 private static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.required("version_id").asText(),n.required("content_sha256").asText());}
 private static void basis(PermissionProtocol.Basis b){WireValues.uuid(b.version_id());WireValues.hash(b.content_sha256());}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
