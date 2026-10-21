package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Typed PTR writer inputs and independently recomputed checks of the physically read N04 body. */
final class ProductTechnicalRequirementPipeline {
 private ProductTechnicalRequirementPipeline(){}
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new ProductTechnicalRequirementSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",ProductTechnicalRequirementSchema.KIND,"human_review_required",true,"conclusion_authority","MACHINE_CHECK_NOT_PRODUCT_APPROVAL"));
  var reasons=out.putArray("unknown_reasons");var combinations=out.putArray("combinations");
  for(var scope:s.scopes()){
   var row=combinations.addObject();row.set("scope",schema.tree(scope));var indicator=s.indicators().stream().filter(x->x.scopeKey().equals(scope.key())).findFirst().orElse(null);
   if(indicator==null){reasons.add("INDICATOR_MISSING:"+scope.key());continue;}row.set("original_indicator",schema.tree(indicator));
   if(!"CONFIRMED".equals(indicator.status()))reasons.add("UNRESOLVED_INDICATOR:"+scope.key());
   if(indicator.requirement().isBlank()||indicator.requirement().contains("见随附资料")||indicator.requirement().contains("按供货合同")||indicator.sourceKey().isEmpty())reasons.add("OBJECTIVE_REQUIREMENT_MISSING:"+scope.key());
   if(!indicator.sourceKey().isEmpty()&&!s.sources().stream().filter(x->x.key().equals(indicator.sourceKey())).findFirst().orElseThrow().literal().equals(indicator.requirement()))reasons.add("ORIGINAL_REQUIREMENT_CONFLICT:"+scope.key());
   if("NUMERIC".equals(indicator.kind())){
    if(!exact(s,indicator.lowerSourceKey(),indicator.lower())||!indicator.upper().isEmpty()&&!exact(s,indicator.upperSourceKey(),indicator.upper()))reasons.add("NUMERIC_ORIGINAL_CONFLICT:"+scope.key());
    if(indicator.unit().isBlank()||"NONE".equals(indicator.comparison())||indicator.lower().isEmpty()||"RANGE".equals(indicator.comparison())&&indicator.upper().isEmpty())reasons.add("NUMERIC_REQUIREMENT_INCOMPLETE:"+scope.key());
    if(!indicator.lower().isEmpty()&&new java.math.BigDecimal(indicator.lower()).scale()!=indicator.scale())reasons.add("ORIGINAL_PRECISION_CONFLICT:"+scope.key());
    if(!indicator.upper().isEmpty()&&(new java.math.BigDecimal(indicator.upper()).scale()!=indicator.scale()||!indicator.lower().isEmpty()&&new java.math.BigDecimal(indicator.lower()).compareTo(new java.math.BigDecimal(indicator.upper()))>0))reasons.add("RANGE_OR_PRECISION_CONFLICT:"+scope.key());
   }
   var method=s.methods().stream().filter(x->x.key().equals(indicator.methodKey())).findFirst().orElse(null);
   if(method==null||!method.scopeKeys().contains(scope.key()))reasons.add("METHOD_COVERAGE_MISSING:"+scope.key());
   else{row.set("method",schema.tree(method));if(method.text().isBlank()||method.standardKeys().isEmpty())reasons.add("METHOD_OR_STANDARD_MISSING:"+scope.key());if("UNKNOWN".equals(method.validationApplicability())||"REQUIRED".equals(method.validationApplicability())&&method.validationSourceKeys().isEmpty())reasons.add("METHOD_VALIDATION_UNRESOLVED:"+scope.key());
    for(String key:method.standardKeys()){var standard=s.standards().stream().filter(x->x.key().equals(key)).findFirst().orElseThrow();if(standard.designation().isBlank()||standard.edition().isBlank())reasons.add("STANDARD_EDITION_MISSING:"+scope.key()+":"+key);}
   }
   if(!indicator.conversionKey().isEmpty()){
    var c=s.conversions().stream().filter(x->x.key().equals(indicator.conversionKey())).findFirst().orElseThrow();row.set("approved_formula",schema.tree(c));
    if(!"NUMERIC".equals(indicator.kind())||!c.inputUnit().equals(indicator.unit())||indicator.lower().isEmpty()||new java.math.BigDecimal(c.multiplier()).signum()<=0)reasons.add("CONVERSION_CONTEXT_CONFLICT:"+scope.key());
    else try{row.put("converted_lower",ProductTechnicalRequirementSchema.convert(indicator.lower(),c).toPlainString());if(!indicator.upper().isEmpty())row.put("converted_upper",ProductTechnicalRequirementSchema.convert(indicator.upper(),c).toPlainString());row.put("converted_unit",c.outputUnit()).put("converted_scale",c.scale());}catch(ArithmeticException e){reasons.add("CONVERSION_PRECISION_UNRESOLVED:"+scope.key());}
   }
   for(String group:List.of("MODELS","INDICATOR","METHOD"))if(s.body().stream().noneMatch(b->group.equals(b.group())&&b.scopeKeys().contains(scope.key())))reasons.add("BODY_SCOPE_MISSING:"+scope.key()+":"+group);
  }
  for(var applicability:s.applicability()){if("UNKNOWN".equals(applicability.status()))reasons.add("APPLICABILITY_UNKNOWN:"+applicability.group());if("REQUIRED".equals(applicability.status())&&s.body().stream().noneMatch(b->applicability.group().equals(b.group())))reasons.add("CONDITIONAL_BODY_MISSING:"+applicability.group());}
  if(s.body().stream().noneMatch(b->"IDENTITY".equals(b.group())))reasons.add("IDENTITY_BODY_MISSING");
  out.set("required_body",schema.tree(s.body()));out.set("standards",schema.tree(s.standards()));out.put("blocked",!reasons.isEmpty());return out;
 }
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  JsonNode fixed=input.documentScenario();if(fixed==null||!ProductTechnicalRequirementSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new ProductTechnicalRequirementSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)plan(fixed);result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));var reasons=(com.fasterxml.jackson.databind.node.ArrayNode)result.required("unknown_reasons");
  var actualSections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!actualSections.equals(input.input().targetSections())||!s.sectionIds().containsAll(actualSections))reasons.add("PTR_SECTION_SCOPE_MISMATCH");
  var sourceChecks=result.putArray("source_checks");var verified=new HashSet<String>();
  for(var source:s.sources()){JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(source.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(source.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());var check=sourceChecks.addObject().put("source_key",source.key()).put("verified",ok);check.set("evidence",schema.tree(source.evidence()));if(found!=null)check.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(source.key());else reasons.add("ORIGINAL_SOURCE_NOT_VERIFIED:"+source.key());
  }
  // Extra unapproved prose cannot hide outside the declared PTR blocks.
  for(var node:input.target().body().required("nodes")){
   String id=node.required("node_id").asText();var manifest=input.target().candidate().nodeManifest().stream().filter(n->n.nodeId().equals(id)).findFirst().orElseThrow();
   if(("TEXT".equals(node.required("content").path("kind").asText())||!Set.of(ArtifactProtocol.NodeKind.ROOT,ArtifactProtocol.NodeKind.SECTION,ArtifactProtocol.NodeKind.PARAGRAPH).contains(manifest.kind()))&&!s.body().stream().anyMatch(b->b.sectionId().equals(manifest.sectionId())&&b.content().equals(node.required("content"))))reasons.add("UNAPPROVED_PTR_BODY_NODE:"+id);
  }
  var checks=result.putArray("body_checks");
  for(var b:s.body()){if(!input.input().targetSections().contains(b.sectionId()))continue;var matches=new ArrayList<String>();for(var node:input.target().body().required("nodes"))if(b.content().equals(node.required("content"))&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(node.required("node_id").asText())&&n.sectionId().equals(b.sectionId())))matches.add(node.required("node_id").asText());
   var check=checks.addObject().put("body_key",b.key()).put("section_id",b.sectionId()).put("exact_body",matches.size()==1);if(matches.size()!=1){reasons.add("PTR_BODY_MISSING_OR_CONFLICT:"+b.key());continue;}String node=matches.get(0);check.put("node_id",node);
   var required=new LinkedHashSet<>(b.sourceKeys());for(var a:s.applicability())if(a.group().equals(b.group()))required.addAll(a.sourceKeys());for(String scope:b.scopeKeys()){var sc=s.scopes().stream().filter(x->x.key().equals(scope)).findFirst().orElseThrow();required.addAll(sc.sourceKeys());if("MODELS".equals(b.group())&&(!b.text().contains(sc.model())||!b.text().contains(sc.configuration())||!b.text().contains(sc.condition())))reasons.add("BODY_MODEL_CONFIGURATION_CONFLICT:"+scope);var i=s.indicators().stream().filter(x->x.scopeKey().equals(scope)).findFirst().orElse(null);if(i!=null){if(!i.sourceKey().isEmpty())required.add(i.sourceKey());if(!i.lowerSourceKey().isEmpty())required.add(i.lowerSourceKey());if(!i.upperSourceKey().isEmpty())required.add(i.upperSourceKey());for(var m:s.methods())if(m.key().equals(i.methodKey())){required.addAll(m.sourceKeys());required.addAll(m.validationSourceKeys());for(var st:s.standards())if(m.standardKeys().contains(st.key()))required.add(st.sourceKey());}for(var c:s.conversions())if(c.key().equals(i.conversionKey()))required.add(c.formulaSourceKey());
    if("INDICATOR".equals(b.group())&&(!b.text().contains(i.requirement())||"NUMERIC".equals(i.kind())&&(!b.text().contains(i.lower())||!b.text().contains(i.unit())||!i.upper().isEmpty()&&!b.text().contains(i.upper()))))reasons.add("BODY_INDICATOR_CONFLICT:"+scope);
    if("METHOD".equals(b.group()))for(var m:s.methods())if(m.key().equals(i.methodKey())){if(!b.text().contains(m.text()))reasons.add("BODY_METHOD_CONFLICT:"+scope);for(var st:s.standards())if(m.standardKeys().contains(st.key())&&(!b.text().contains(st.designation())||!b.text().contains(st.edition())))reasons.add("BODY_STANDARD_EDITION_CONFLICT:"+scope);}
    if("INDICATOR".equals(b.group())&&!i.conversionKey().isEmpty())for(var c:s.conversions())if(c.key().equals(i.conversionKey())&&!i.lower().isEmpty())try{if(!b.text().contains(ProductTechnicalRequirementSchema.convert(i.lower(),c).toPlainString())||!b.text().contains(c.outputUnit())||!i.upper().isEmpty()&&!b.text().contains(ProductTechnicalRequirementSchema.convert(i.upper(),c).toPlainString()))reasons.add("BODY_CONVERSION_CONFLICT:"+scope);}catch(ArithmeticException e){reasons.add("BODY_CONVERSION_UNRESOLVED:"+scope);}
   }}
   for(String key:required){if(!verified.contains(key))reasons.add("BODY_SOURCE_UNVERIFIED:"+b.key()+":"+key);var ref=s.sources().stream().filter(x->x.key().equals(key)).findFirst().orElseThrow().evidence();boolean linked=false;for(var link:input.target().body().required("evidence_links"))if(schema.tree(ref).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(node.equals(id.asText()))linked=true;if(!linked)reasons.add("PTR_BODY_CITATION_MISSING:"+b.key()+":"+key);}
  }
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("blocked",!reasons.isEmpty());return result;
 }
 private static boolean exact(ProductTechnicalRequirementSchema.Selection s,String key,String literal){return !key.isEmpty()&&!literal.isEmpty()&&s.sources().stream().anyMatch(x->x.key().equals(key)&&x.literal().equals(literal));}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("product_technical_requirement",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads")),provenance.required("normalization").get("product_technical_requirement")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
