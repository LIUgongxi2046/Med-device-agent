package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
/** N05 independently checks the saved translation, with fixed semantic rule and literal fidelity. */
final class TranslationScenarioPipeline {
 private TranslationScenarioPipeline(){}
 static List<String> numbers(String text){var out=new ArrayList<String>();var m=Pattern.compile("[-+]?\\p{Nd}+(?:[.,]\\p{Nd}+)*(?:[eE][-+]?\\p{Nd}+)?%?").matcher(text);while(m.find())out.add(m.group());return List.copyOf(out);}
 static List<String> layout(String text){var out=new ArrayList<String>();for(String line:text.split("\\n",-1)){int i=0;while(i<line.length()&&(line.charAt(i)==' '||line.charAt(i)=='\t'))i++;out.add(line.substring(0,i)+(line.isBlank()?"BLANK":"TEXT"));}return List.copyOf(out);}
 static int occurrences(String text,String token){int count=0;for(int from=0;(from=text.indexOf(token,from))>=0;from+=token.length())count++;return count;}
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads,ArtifactProtocol.ReviewFindings findings)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!TranslationScenarioSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new TranslationScenarioSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",TranslationScenarioSchema.KIND,"human_review_required",true,"conclusion_authority","INDEPENDENT_MACHINE_REVIEW_NOT_PROFESSIONAL_APPROVAL"));out.set("specification",fixed.required("specification"));out.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));out.put("source_language",s.sourceLanguage()).put("target_language",s.targetLanguage()).put("layout_policy",s.layoutPolicy()).put("coverage_mode","CURRENT_SECTION_CANDIDATE");out.set("verified_section_ids",schema.tree(input.input().targetSections()));var reasons=out.putArray("unknown_reasons");var rows=out.putArray("segments");
  var rule=input.rules().rules().stream().filter(r->r.ruleId().equals(s.semanticReviewRule().version_id())&&r.sha256().equals(s.semanticReviewRule().content_sha256())).toList();
  if(rule.size()!=1||!new HashSet<>(rule.get(0).content().basis_manifest()).containsAll(s.segments().stream().map(TranslationScenarioSchema.Segment::source).toList()))reasons.add("APPROVED_TRANSLATION_RULE_OR_SOURCE_BASIS_MISSING");
  for(var n:input.target().candidate().nodeManifest())if(!Set.of(ArtifactProtocol.NodeKind.ROOT,ArtifactProtocol.NodeKind.SECTION,ArtifactProtocol.NodeKind.PARAGRAPH,ArtifactProtocol.NodeKind.TEXT).contains(n.kind()))reasons.add("UNSUPPORTED_NON_TEXT_LAYOUT");
  for(String section:input.input().targetSections()){
   var selected=s.segments().stream().filter(x->x.sectionId().equals(section)).toList();var nodes=new ArrayList<JsonNode>();for(var n:input.target().body().required("nodes"))if("TEXT".equals(n.required("content").required("kind").asText())&&input.target().candidate().nodeManifest().stream().anyMatch(m->m.nodeId().equals(n.required("node_id").asText())&&m.sectionId().equals(section)))nodes.add(n);
   if(selected.isEmpty()||selected.size()!=nodes.size()){reasons.add("TRANSLATION_BLOCK_COUNT_MISMATCH:"+section);continue;}
   for(int index=0;index<selected.size();index++){
    var segment=selected.get(index);var node=nodes.get(index);String id=node.required("node_id").asText(),translated=node.required("content").required("text").asText();var row=rows.addObject().put("key",segment.key()).put("node_id",id).put("translated_sha256",WireValues.sha256(translated.getBytes(StandardCharsets.UTF_8)));row.set("evidence_ref",schema.tree(segment.evidence()));var failures=row.putArray("unknown_reasons");
    JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(segment.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
    if(found==null||!found.path("access").asBoolean(false)||!found.path("located").asBoolean(false)||!found.path("complete").asBoolean(false)||!WireValues.sha256(segment.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText()))failures.add("ORIGINAL_LITERAL_NOT_INDEPENDENTLY_VERIFIED");else row.put("source_read_receipt",found.required("receipt_id").asText());
    if(translated.isBlank())failures.add("EMPTY_TRANSLATION_BLOCK");if(!numbers(segment.literal()).equals(numbers(translated)))failures.add("NUMERIC_TOKEN_SEQUENCE_CHANGED");if(!layout(segment.literal()).equals(layout(translated)))failures.add("LINE_BREAK_OR_INDENT_LAYOUT_CHANGED");
    for(String token:segment.protectedTokens())if(occurrences(segment.literal(),token)!=occurrences(translated,token))failures.add("PROTECTED_CITATION_OR_TOKEN_CHANGED:"+token);
    for(var term:s.terminology())if(segment.literal().contains(term.source())&&occurrences(segment.literal(),term.source())!=occurrences(translated,term.target()))failures.add("FIXED_TERMINOLOGY_MISMATCH:"+term.source());
    boolean linked=false;for(var link:input.target().body().required("evidence_links"))if(schema.tree(segment.evidence()).equals(link.required("evidence")))for(var target:link.required("node_ids"))if(id.equals(target.asText()))linked=true;if(!linked)failures.add("TRANSLATION_SOURCE_CITATION_MISSING");
    var claims=input.target().claims().values().stream().filter(c->c.evidenceLinks().stream().anyMatch(l->l.evidenceRef().equals(segment.evidence()))&&whole(c.anchor(),id,translated.length())).toList();
    if(claims.isEmpty()||claims.stream().noneMatch(c->findings.claimChecks().stream().anyMatch(check->check.claimId().equals(c.claimId())&&check.supportCheck()==ArtifactProtocol.SupportCheck.SUPPORTED)))failures.add("INDEPENDENT_FULL_BLOCK_SEMANTIC_SUPPORT_MISSING");
    if(findings.ruleChecks().stream().noneMatch(c->c.ruleId().equals(s.semanticReviewRule().version_id())&&c.targetNodeIds().contains(id)&&c.result()==ArtifactProtocol.RuleResult.PASS))failures.add("INDEPENDENT_LANGUAGE_AND_TRANSLATION_RULE_NOT_PASSED");
    row.put("verified",failures.isEmpty());if(!failures.isEmpty())reasons.add("TRANSLATION_BLOCK_FAILED:"+segment.key());
   }
  }
  out.put("blocked",!reasons.isEmpty());return out;
 }
 private static boolean whole(ArtifactProtocol.Anchor anchor,String node,int length){return anchor instanceof ArtifactProtocol.TextAnchor a&&a.nodeId().equals(node)&&a.start()==0&&a.end()==length||anchor instanceof ArtifactProtocol.StructuredAnchor b&&b.nodeId().equals(node)&&b.rowId()==null&&b.fieldId()==null;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("translation_scenario",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance,ArtifactProtocol.ReviewFindings findings)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads"),findings),provenance.required("normalization").get("translation_scenario")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
