package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.math.*;
import org.springframework.stereotype.Component;
/** Exact approved numeric locator literals, never model-supplied numerical facts. */
@Component public final class ReportScenarioSchema {
 public static final String PREFIX="report-scenario-v1\n",KIND="REPORT_GENERATION";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public record Input(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String decimalLiteral,String unit){}
 public record Calculation(String key,String operation,List<String> inputs,int scale,String rounding,String unit,String sectionId,String prefix,String suffix){public Calculation{inputs=List.copyOf(inputs);}}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,List<Input> inputs,List<Calculation> calculations){public Selection{sectionIds=List.copyOf(sectionIds);inputs=List.copyOf(inputs);calculations=List.copyOf(calculations);}}
 public Selection read(JsonNode value)throws Exception{
  try{var s=json.treeToValue(value,Selection.class);if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||s.inputs().isEmpty()||s.inputs().size()>64||s.calculations().isEmpty()||s.calculations().size()>64||s.sectionIds().isEmpty()||s.sectionIds().size()>64||new HashSet<>(s.sectionIds()).size()!=s.sectionIds().size())bad();s.sectionIds().forEach(WireValues::uuid);
   var keys=new HashSet<String>();for(var i:s.inputs()){ProjectSchema.text(i.key(),128);if(!keys.add(i.key()))bad();WireValues.uuid(i.source().version_id());WireValues.hash(i.source().content_sha256());WireValues.uuid(i.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(i.evidence()));if(!i.source().version_id().equals(i.evidence().sourceVersion())||!i.decimalLiteral().matches("-?(0|[1-9][0-9]{0,35})(\\.[0-9]{1,18})?"))bad();ProjectSchema.text(i.unit(),64);}
   keys.clear();for(var c:s.calculations()){ProjectSchema.text(c.key(),128);if(!keys.add(c.key())||!Set.of("SUM","DIFFERENCE","PRODUCT","RATIO").contains(c.operation())||!Set.of("HALF_EVEN","HALF_UP","DOWN","UNNECESSARY").contains(c.rounding())||c.scale()<0||c.scale()>18||c.inputs().isEmpty()||c.inputs().size()>64||!s.sectionIds().contains(c.sectionId())||c.prefix()==null||c.suffix()==null||c.prefix().length()>512||c.suffix().length()>512)bad();ProjectSchema.text(c.unit(),64);if(Set.of("DIFFERENCE","RATIO").contains(c.operation())&&c.inputs().size()!=2)bad();for(String key:c.inputs())if(s.inputs().stream().noneMatch(i->i.key().equals(key)))bad();if(Set.of("SUM","DIFFERENCE").contains(c.operation()))for(String key:c.inputs())if(s.inputs().stream().noneMatch(i->i.key().equals(key)&&i.unit().equals(c.unit())))bad();}
   return s;
  }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 public JsonNode tree(Object o){return json.valueToTree(o);}
 public Selection definition(String d)throws Exception{if(!d.startsWith(PREFIX))bad();return read(json.readTree(d.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String d=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(d.length()>16000)bad();return d;}
 public void author(JsonNode a)throws Exception{String d=a.required("specification").required("INPUTS").required("definition").asText();if(!d.startsWith(PREFIX))return;var s=definition(d);var roots=new HashSet<PermissionProtocol.Basis>();for(var r:a.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))roots.add(new PermissionProtocol.Basis(r.required("version_id").asText(),r.required("content_sha256").asText()));for(var i:s.inputs())if(!roots.contains(i.source())||!a.required("project_id").asText().equals(i.evidence().projectId()))bad();}
 public static BigDecimal calculate(Selection s,Calculation c){var values=c.inputs().stream().map(k->new BigDecimal(s.inputs().stream().filter(i->i.key().equals(k)).findFirst().orElseThrow().decimalLiteral())).toList();BigDecimal v=switch(c.operation()){case "SUM"->values.stream().reduce(BigDecimal.ZERO,BigDecimal::add);case "PRODUCT"->values.stream().reduce(BigDecimal.ONE,BigDecimal::multiply);case "DIFFERENCE"->values.get(0).subtract(values.get(1));case "RATIO"->values.get(0).divide(values.get(1),c.scale(),RoundingMode.valueOf(c.rounding()));default->throw new IllegalArgumentException();};return v.setScale(c.scale(),RoundingMode.valueOf(c.rounding()));}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
