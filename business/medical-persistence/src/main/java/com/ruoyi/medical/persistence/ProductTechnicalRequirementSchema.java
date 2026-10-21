package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.math.*;
import org.springframework.stereotype.Component;
/** B20: author-specified real scope, approved by the ordinary B14 independent review/publication. */
@Component public final class ProductTechnicalRequirementSchema {
 public static final String PREFIX="product-technical-requirement-v1\n", KIND="PRODUCT_TECHNICAL_REQUIREMENT";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public record Source(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal){}
 public record Scope(String key,String model,String configuration,String condition,String indicator,List<String> sourceKeys){}
 public record Standard(String key,String designation,String edition,String sourceKey){}
 public record Method(String key,List<String> scopeKeys,List<String> sourceKeys,List<String> standardKeys,String validationApplicability,List<String> validationSourceKeys,String text){}
 public record Conversion(String key,String inputUnit,String outputUnit,String multiplier,String offset,int scale,String rounding,String formulaSourceKey){}
 public record Indicator(String scopeKey,String kind,String requirement,String comparison,String lower,String upper,String unit,int scale,String sourceKey,String lowerSourceKey,String upperSourceKey,String methodKey,String conversionKey,String status){}
 public record Applicability(String group,String status,List<String> sourceKeys){}
 public record Body(String key,String sectionId,String group,JsonNode content,List<String> sourceKeys,List<String> scopeKeys){
  public String text(){return textProjection(content);}
 }
 private static String textProjection(JsonNode n){if(n==null)return "";if(n.isTextual())return n.textValue();var parts=new ArrayList<String>();n.elements().forEachRemaining(x->parts.add(textProjection(x)));return String.join(" ",parts);}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,List<Source> sources,List<Scope> scopes,List<Standard> standards,List<Method> methods,List<Conversion> conversions,List<Indicator> indicators,List<Applicability> applicability,List<Body> body){}
 public Selection read(JsonNode n)throws Exception{try{
  Selection s=json.treeToValue(n,Selection.class);if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||s.sectionIds().isEmpty()||s.sectionIds().size()>64||s.sources().isEmpty()||s.scopes().isEmpty()||s.scopes().size()>256||s.sources().size()>256||s.body().size()>512)bad();unique(s.sectionIds());s.sectionIds().forEach(WireValues::uuid);
  var sources=new HashSet<String>();for(var x:s.sources()){text(x.key());if(!sources.add(x.key()))bad();WireValues.uuid(x.source().version_id());WireValues.hash(x.source().content_sha256());WireValues.uuid(x.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(x.evidence()));if(!x.source().version_id().equals(x.evidence().sourceVersion()))bad();text(x.literal());}
  var scopes=new HashSet<String>();var combinations=new HashSet<List<String>>();for(var x:s.scopes()){text(x.key());text(x.model());text(x.configuration());text(x.condition());text(x.indicator());if(!scopes.add(x.key())||!combinations.add(List.of(x.model(),x.configuration(),x.condition(),x.indicator())))bad();refs(x.sourceKeys(),sources,true);}
  var standards=new HashSet<String>();for(var x:s.standards()){text(x.key());if(!standards.add(x.key()))bad();optional(x.designation());optional(x.edition());if(!sources.contains(x.sourceKey()))bad();}
  var methods=new HashSet<String>();for(var x:s.methods()){text(x.key());if(!methods.add(x.key())||!Set.of("REQUIRED","NOT_APPLICABLE","UNKNOWN").contains(x.validationApplicability()))bad();refs(x.scopeKeys(),scopes,true);refs(x.sourceKeys(),sources,true);refs(x.standardKeys(),standards,false);refs(x.validationSourceKeys(),sources,false);optional(x.text());}
  var conversions=new HashSet<String>();for(var x:s.conversions()){text(x.key());if(!conversions.add(x.key())||!sources.contains(x.formulaSourceKey()))bad();text(x.inputUnit());text(x.outputUnit());decimal(x.multiplier());decimal(x.offset());precision(x.scale());if(!Set.of("UNNECESSARY","HALF_EVEN","HALF_UP","DOWN").contains(x.rounding()))bad();}
  var indicators=new HashSet<String>();for(var x:s.indicators()){if(!scopes.contains(x.scopeKey())||!indicators.add(x.scopeKey())||!Set.of("NUMERIC","QUALITATIVE").contains(x.kind())||!Set.of("CONFIRMED","UNKNOWN","CONFLICT").contains(x.status()))bad();optional(x.requirement());optional(x.unit());optional(x.methodKey());optional(x.conversionKey());optional(x.sourceKey());optional(x.lowerSourceKey());optional(x.upperSourceKey());for(String key:List.of(x.lowerSourceKey(),x.upperSourceKey()))if(!key.isEmpty()&&!sources.contains(key))bad();if(!x.sourceKey().isEmpty()&&!sources.contains(x.sourceKey()))bad();if(!x.methodKey().isEmpty()&&!methods.contains(x.methodKey()))bad();if(!x.conversionKey().isEmpty()&&!conversions.contains(x.conversionKey()))bad();if(!Set.of("EQ","LE","LT","GE","GT","RANGE","NONE").contains(x.comparison()))bad();precision(x.scale());if(!x.lower().isEmpty())decimal(x.lower());if(!x.upper().isEmpty())decimal(x.upper());}
  var groups=new HashSet<String>();for(var x:s.applicability()){if(!Set.of("SOFTWARE","IVD_METHOD","TERMS","APPENDIX").contains(x.group())||!groups.add(x.group())||!Set.of("REQUIRED","NOT_APPLICABLE","UNKNOWN").contains(x.status()))bad();refs(x.sourceKeys(),sources,!"UNKNOWN".equals(x.status()));}if(groups.size()!=4)bad();
  var body=new HashSet<String>();for(var x:s.body()){text(x.key());if(!body.add(x.key())||!s.sectionIds().contains(x.sectionId())||!Set.of("IDENTITY","MODELS","SOFTWARE","INDICATOR","METHOD","IVD_METHOD","TERMS","APPENDIX").contains(x.group()))bad();if(x.content()==null||!x.content().isObject()||!x.content().path("kind").isTextual())bad();text(x.text());refs(x.sourceKeys(),sources,true);refs(x.scopeKeys(),scopes,false);}
  return s;
 }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public JsonNode tree(Object value){return json.valueToTree(value);}
 public Selection definition(String d)throws Exception{if(!d.startsWith(PREFIX))bad();return read(json.readTree(d.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String d=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(d.length()>16000)bad();return d;}
 public void author(JsonNode a)throws Exception{String d=a.required("specification").required("INPUTS").required("definition").asText();if(!d.startsWith(PREFIX))return;var s=definition(d);var roots=new HashSet<PermissionProtocol.Basis>();for(var r:a.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))roots.add(new PermissionProtocol.Basis(r.required("version_id").asText(),r.required("content_sha256").asText()));for(var x:s.sources())if(!roots.contains(x.source())||!a.required("project_id").asText().equals(x.evidence().projectId()))bad();}
 static BigDecimal convert(String original,Conversion c){return new BigDecimal(original).multiply(new BigDecimal(c.multiplier())).add(new BigDecimal(c.offset())).setScale(c.scale(),RoundingMode.valueOf(c.rounding()));}
 private static void refs(List<String> refs,Set<String> allowed,boolean required){if(refs==null||required&&refs.isEmpty()||refs.size()>256||!allowed.containsAll(refs))bad();unique(refs);}
 private static void unique(List<String> v){if(new HashSet<>(v).size()!=v.size())bad();}
 private static void text(String v){ProjectSchema.text(v,8000);}private static void optional(String v){if(v==null||v.length()>8000)bad();}
 private static void precision(int n){if(n<0||n>18)bad();}private static void decimal(String v){if(v==null||!v.matches("-?(0|[1-9][0-9]{0,35})(\\.[0-9]{1,18})?"))bad();}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
