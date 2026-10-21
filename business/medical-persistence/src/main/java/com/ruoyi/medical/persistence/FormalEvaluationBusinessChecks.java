package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Data-only independent EVAL normalization. Its inputs must come from original compiler and
 * original session receipts. No TASK token, model assertion or structure check grants approval. */
final class FormalEvaluationBusinessChecks {
 private final PermissionSchema json;
 private final JsonMapper mapper=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final TaskSourceVerifierSchema reviews=new TaskSourceVerifierSchema();
 FormalEvaluationBusinessChecks(PermissionSchema json){this.json=json;}
 record Result(JsonNode output,String state){}
 Result check(String kind,String session,String action,JsonNode compiler,JsonNode projection,String ref,String digest,List<JsonNode> receipts)throws Exception {
  var contract=Contract.require(kind,ref,digest);
  if(!projection.isNull())SchemaCatalogue.fromClasspath().bindPublished(ref,digest,json.canonical(projection).getBytes(StandardCharsets.UTF_8));
  var blockers=new LinkedHashSet<String>();JsonNode normalized=null,body=null;
  for(var receipt:receipts)if("AT-08".equals(receipt.path("tool_id").asText()))blockers.add("UNANSWERED_BUSINESS_QUESTION:"+receipt.path("receipt_id").asText());
  var fixed=compiler.path("node_business_original");
  if(!fixed.isObject()||!compiler.has("case_original"))blockers.add("APPROVED_BUSINESS_INPUT_MISSING");
  else {
   var reads=sourceReads(receipts);var predecessors=compiler.path("predecessor_outputs");
   switch(kind){
    case "N02" -> normalized=research(fixed,compiler,projection,reads,receipts,blockers,ref,digest);
    case "N03" -> {physicalReads(compiler,reads);normalized=fields(fixed,compiler,projection,reads,blockers,contract);}
    case "N04" -> {normalized=writer(fixed,projection,predecessors,reads,blockers,contract);body=mapper.readTree(normalized.required("body_json").asText());}
    case "N05","N07" -> normalized=review(kind,session,action,fixed,compiler,projection,predecessors,reads,receipts,blockers,contract);
    case "N06" -> {var assembled=assemble(fixed,compiler,projection,predecessors,blockers,contract);normalized=assembled.get(0);body=assembled.get(1);}
    default -> bad();
   }
  }
  boolean v2=contract.v2();
  var result=json.object().put("schema_version",v2?"2.0.0":"1.0.0").put("node_kind",kind).put("projection_schema_ref",ref).put("projection_schema_sha256",digest).put("professional_approval",false);
  result.set("projection",Set.of("N03","N06").contains(kind)&&projection.isNull()&&normalized!=null?normalized.deepCopy():projection.deepCopy());result.set("normalized",normalized==null?NullNode.instance:normalized);result.set("body",body==null?NullNode.instance:body);result.set("blockers",json.tree(blockers));
  FormalEvaluationBusinessSchema.validateOrPublished(FormalEvaluationBusinessSchema.refFor(result),FormalEvaluationBusinessSchema.digest(FormalEvaluationBusinessSchema.refFor(result)),json.canonical(result).getBytes(StandardCharsets.UTF_8));
  return new Result(result,blockers.isEmpty()?"SUCCEEDED":"WAITING_INPUT");
 }
 Result checkDeterministic(String executionId,String action,JsonNode compiler)throws Exception {return checkDeterministic("N03",executionId,action,compiler);}
 Result checkDeterministic(String kind,String executionId,String action,JsonNode compiler)throws Exception {if(!Set.of("N03","N06").contains(kind))bad();var catalogue=SchemaCatalogue.fromClasspath();return check(kind,executionId,action,compiler,NullNode.instance,catalogue.schemaRef("artifacts","N03".equals(kind)?"FieldCheckResult":"AssemblyManifest"),catalogue.schemaDigest("artifacts"),List.of());}
 Result checkDeterministic(String kind,String executionId,String action,JsonNode compiler,String declaredRef,String declaredSha)throws Exception {if(!Set.of("N03","N06").contains(kind))bad();return check(kind,executionId,action,compiler,NullNode.instance,declaredRef,declaredSha,List.of());}
 private record Contract(boolean v2,String ref,String sha) {
  static Contract require(String kind,String ref,String sha)throws Exception {var c=SchemaCatalogue.fromClasspath();var p=c.requirePublished(ref,sha);String definition=switch(kind){case "N02"->"EvidenceBundle";case "N03"->"FieldCheckResult";case "N04"->"SectionDraft";case "N05","N07"->"ReviewFindings";case "N06"->"AssemblyManifest";default->throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);};if(!Set.of("artifacts","artifacts-v2").contains(p.name())||!definition.equals(p.definition()))bad();return new Contract("artifacts-v2".equals(p.name()),ref,sha);}
  String version(){return v2?"2.0.0":"1.0.0";}
  String bodyName(){return v2?"document-body-v2":"interfaces";}
  JsonNode body(JsonNode value)throws Exception {var c=SchemaCatalogue.fromClasspath();return DocumentBodySemantics.verify(c,value,new DocumentBodySemantics.Limits(10000,10000,100000),c.schemaRef(bodyName(),"DocumentBody"),c.schemaDigest(bodyName()));}
  void predecessor(JsonNode p)throws Exception {var output=p.required("output");var original=require(p.required("node_kind").asText(),output.required("projection_schema_ref").asText(),output.required("projection_schema_sha256").asText());if(v2!=original.v2)bad();}
 }
 private void physicalReads(JsonNode compiler,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads)throws Exception {for(var read:compiler.required("physical_source_reads")){WireValues.uuid(read.required("receipt_id").asText());var ref=SourceEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),read.required("evidence_ref")).read();reads.computeIfAbsent(ref,k->new ArrayList<>()).add(read);}}
 private static String literal(JsonNode read){return read.has("content")?read.required("content").asText():read.required("response").required("payload").required("content").asText();}
 private Map<DatasetProtocol.EvidenceRef,List<JsonNode>> sourceReads(List<JsonNode> receipts)throws Exception {
  var result=new LinkedHashMap<DatasetProtocol.EvidenceRef,List<JsonNode>>();for(var receipt:receipts)if("AT-02".equals(receipt.path("tool_id").asText())){
   var response=receipt.required("response");if(!"SUCCEEDED".equals(response.path("status").asText())||!response.path("coverage").path("complete").asBoolean())continue;
   var payload=response.required("payload");var evidence=SourceEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),payload.required("evidence_ref")).read();
   result.computeIfAbsent(evidence,k->new ArrayList<>()).add(receipt);
  }return result;
 }
 private JsonNode research(JsonNode fixed,JsonNode compiler,JsonNode projection,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads,List<JsonNode> receipts,Set<String> blockers,String declaredRef,String declaredSha)throws Exception {
  var bundle=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,projection,declaredRef,declaredSha).read();var required=new LinkedHashMap<String,JsonNode>();for(var q:fixed.required("questions"))if(required.put(q.required("question_id").asText(),q)!=null)bad();
  var proposed=new HashMap<String,ArtifactProtocol.QuestionCoverage>();for(var q:bundle.questionCoverage())if(proposed.put(q.questionId(),q)!=null||!required.containsKey(q.questionId()))bad();
  var retrievals=new HashSet<String>();var readIds=new HashSet<String>();for(var r:receipts){if("AT-01".equals(r.path("tool_id").asText()))retrievals.add(r.required("receipt_id").asText());if("AT-02".equals(r.path("tool_id").asText()))readIds.add(r.required("receipt_id").asText());}
  if(!retrievals.containsAll(bundle.retrievalIds())||!readIds.containsAll(bundle.readReceiptIds()))bad();
  var missing=new TreeSet<>(bundle.missingRequirementIds());var coverage=new ArrayList<ArtifactProtocol.QuestionCoverage>();
  for(var entry:required.entrySet()){
   var q=proposed.get(entry.getKey());var links=q==null?List.<ArtifactProtocol.EvidenceLink>of():q.evidenceLinks();boolean complete=q!=null&&links.stream().anyMatch(link->link.relation()==ArtifactProtocol.Relation.SUPPORT);for(var link:links){complete&=reads.containsKey(link.evidenceRef());if(link.relation()==ArtifactProtocol.Relation.CONFLICT){complete=false;blockers.add("QUESTION_EVIDENCE_CONFLICT:"+entry.getKey());}}
   // Completion-rule checks require their own independent execution proof; model IDs are not one.
   if(q!=null&&!q.completionCheckIds().isEmpty())bad();for(var ruleId:entry.getValue().required("completion_rule_ids"))if(!completion(entry.getKey(),ruleId.asText(),fixed,compiler,reads,blockers))complete=false;
   var status=!complete?ArtifactProtocol.QuestionStatus.INSUFFICIENT:q.status();var qm=new TreeSet<String>();if(q!=null)qm.addAll(q.missingRequirementIds());
   if(status!=ArtifactProtocol.QuestionStatus.VERIFIED){for(var id:entry.getValue().required("requirement_ids"))qm.add(id.asText());blockers.add("QUESTION_"+status+":"+entry.getKey());}missing.addAll(qm);
   coverage.add(new ArtifactProtocol.QuestionCoverage(entry.getKey(),status,links,List.of(),List.copyOf(qm)));
  }
  for(var evidence:bundle.selectedRefs())if(!reads.containsKey(evidence))bad();for(var evidence:bundle.conflictRefs())if(!reads.containsKey(evidence))bad();
  if(!missing.isEmpty()||!bundle.conflictRefs().isEmpty())blockers.add("RESEARCH_EVIDENCE_INCOMPLETE_OR_CONFLICTING");
  return json.tree(new ArtifactProtocol.EvidenceBundle(bundle.retrievalIds(),coverage,bundle.selectedRefs(),bundle.conflictRefs(),List.copyOf(missing),bundle.readReceiptIds()));
 }
 private JsonNode fields(JsonNode fixed,JsonNode compiler,JsonNode projection,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads,Set<String> blockers,Contract contract)throws Exception {
  same(contract.version(),fixed.required("target").required("schema_version").asText());same(contract.version(),fixed.required("request").required("schema_version").asText());
  var schema=new StrictFieldComparisonSchema();schema.target(fixed.required("target"));schema.request(fixed.required("request"));
  var targetJson=(ObjectNode)fixed.required("target").deepCopy();var requestJson=(ObjectNode)fixed.required("request").deepCopy();
  for(var entry:targetJson.required("entries"))bindReads(entry.path("provenance"),reads);
  for(var entry:requestJson.required("entries")){bindReads(entry.path("source_observation").path("provenance"),reads);for(var targetEntry:targetJson.required("entries"))if(entry.required("entry_id").equals(targetEntry.required("entry_id")))((ObjectNode)entry).put("target_entry_sha256",schema.hash(targetEntry));}
  requestJson.put("target_sha256",schema.hash(targetJson));var target=schema.target(targetJson);var request=schema.request(requestJson);
  var policies=new ArrayList<TaskStrictFieldCheck.Policy>();for(var p:fixed.required("policies"))policies.add(new TaskStrictFieldCheck.Policy(p.required("revision").asText(),p.required("sha256").asText(),mapper.treeToValue(p.required("definition"),StrictComparisonPolicyDefinition.Definition.class)));
  var implementation=mapper.treeToValue(fixed.required("implementation"),StrictComparisonPolicyDefinition.Version.class);
  var engine=new TaskStrictFieldCheck(schema,implementation);var evaluated=engine.evaluate(target,request,policies,new StrictFieldComparisonEngine.Limits(10000,1000,10000,16777216,16777216,1000000,10000000,30000));
  var byId=new HashMap<String,StrictFieldComparisonProtocol.EntryResult>();if(evaluated.comparison()!=null)for(var r:evaluated.comparison().entries())byId.put(r.entryId(),r);
  var entries=new ArrayList<ArtifactProtocol.FieldComparison>();var missing=new TreeSet<String>();
  for(var entry:target.entries()){
   var requested=request.entries().stream().filter(r->r.entryId().equals(entry.entryId())).findFirst().orElseThrow();var check=byId.get(entry.entryId());var source=requested.sourceObservation();boolean sourceRead=source!=null&&provenance(source.provenance(),reads)&&sourceAuthority(compiler,source.provenance());boolean candidateRead=provenance(entry.provenance(),reads)&&sourceAuthority(compiler,entry.provenance());
   if(check==null||check.comparison()==null||!sourceRead||!candidateRead){
    var sourceValue=source==null?new ArtifactProtocol.FieldValue(ArtifactProtocol.ValueKind.EMPTY,null,null,null,null,List.of(),null):source.value();
    entries.add(new ArtifactProtocol.FieldComparison(entry.fieldId(),entry.rowId(),entry.modelIds(),entry.conditionIds(),sourceValue,entry.candidateValue(),requested.rule().ruleId(),null,!sourceRead?ArtifactProtocol.ComparisonResult.MISSING:ArtifactProtocol.ComparisonResult.UNKNOWN_CONDITION,List.of()));missing.add(entry.nodeId());blockers.add("FIELD_INPUT_INCOMPLETE:"+entry.entryId());
   }else{entries.add(check.comparison());if(check.result()!=ArtifactProtocol.ComparisonResult.MATCH)blockers.add("FIELD_"+check.result()+":"+entry.entryId());}
  }
  var methods=new ArrayList<ArtifactProtocol.MethodCoverage>();for(var m:fixed.required("method_requirements")){
   var evidence=new ArrayList<DatasetProtocol.EvidenceRef>();for(var e:m.required("evidence_refs"))evidence.add(mapper.treeToValue(e,DatasetProtocol.EvidenceRef.class));var ids=strings(m.required("method_ids"));boolean complete=!ids.isEmpty()&&!evidence.isEmpty()&&reads.keySet().containsAll(evidence);
   if(!complete)blockers.add("METHOD_COVERAGE_INCOMPLETE:"+m.required("indicator_id").asText());methods.add(new ArtifactProtocol.MethodCoverage(m.required("indicator_id").asText(),m.required("model_id").asText(),m.required("condition_id").asText(),ids,evidence,complete?ArtifactProtocol.CoverageResult.COMPLETE:ArtifactProtocol.CoverageResult.INCOMPLETE));
  }
  var normalized=json.tree(new ArtifactProtocol.FieldCheckResult(fixed.required("target_artifact_id").asText(),fixed.required("rule_revision").asText(),entries,methods,List.copyOf(missing)));
  // Pure deterministic comparison: no provider result or fabricated model call is required.
  ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.FIELD_CHECK_RESULT,normalized,contract.ref(),contract.sha()).read();return normalized;
 }
 private boolean sourceAuthority(JsonNode compiler,StrictFieldComparisonProtocol.Provenance p){if(p==null||p.source()==null)return false;for(var proof:compiler.path("source_value_originals"))if(json.tree(p.source()).equals(proof.path("source")))return true;return false;}
 private void bindReads(JsonNode value,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads)throws Exception {
  if(!value.isObject())return;var p=mapper.treeToValue(value,StrictFieldComparisonProtocol.Provenance.class);var ids=json.object().arrayNode();if(p.source()==null||p.source().evidenceRef()==null)return;
  for(var r:reads.getOrDefault(p.source().evidenceRef(),List.of())){String text=literal(r);int length=text.codePointCount(0,text.length());if(p.startCodepoint()<0||p.endCodepoint()>length||p.endCodepoint()<=p.startCodepoint())continue;String slice=text.substring(text.offsetByCodePoints(0,p.startCodepoint()),text.offsetByCodePoints(0,p.endCodepoint()));if(WireValues.sha256(slice.getBytes(StandardCharsets.UTF_8)).equals(p.rawTextSha256()))ids.add(r.required("receipt_id").asText());}if(!ids.isEmpty())((ObjectNode)value).set("read_receipt_ids",ids);
 }
 private boolean provenance(StrictFieldComparisonProtocol.Provenance p,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads)throws Exception {
  if(p==null||p.source()==null||p.source().evidenceRef()==null||p.readReceiptIds().isEmpty())return false;
  var originals=reads.getOrDefault(p.source().evidenceRef(),List.of());var found=new HashSet<String>();for(var r:originals){String text=literal(r);int length=text.codePointCount(0,text.length());if(p.startCodepoint()<0||p.endCodepoint()>length||p.endCodepoint()<=p.startCodepoint())continue;String slice=text.substring(text.offsetByCodePoints(0,p.startCodepoint()),text.offsetByCodePoints(0,p.endCodepoint()));if(WireValues.sha256(slice.getBytes(StandardCharsets.UTF_8)).equals(p.rawTextSha256()))found.add(r.required("receipt_id").asText());}return found.containsAll(p.readReceiptIds());
 }
 private JsonNode writer(JsonNode fixed,JsonNode projection,JsonNode predecessors,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads,Set<String> blockers,Contract contract)throws Exception {
  var draft=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.SECTION_DRAFT,projection,contract.ref(),contract.sha()).read();contract.body(fixed.required("baseline"));same(fixed.required("body_schema_revision").asText(),draft.bodySchemaRevision());
  var body=contract.body(mapper.readTree(draft.bodyJson()));
  same(fixed.required("baseline").required("template_version"),body.required("template_version"));var owners=fixed.required("section_owners");var required=new HashSet<>(strings(fixed.required("target_sections")));var actual=new HashSet<String>();
  for(var n:body.required("nodes"))if("SECTION".equals(n.path("content").path("kind").asText())){String owner=owners.path(n.required("node_id").asText()).asText();if(owner.isEmpty()||!required.contains(owner))bad();if(!actual.add(owner))bad();}if(!actual.equals(required))blockers.add("WRITER_ASSIGNED_SECTION_SET_INCOMPLETE");
  var nodeIndex=new HashMap<String,JsonNode>();for(var n:body.required("nodes"))nodeIndex.put(n.required("node_id").asText(),n);
  for(var n:body.required("nodes"))if(!"DOCUMENT".equals(n.path("content").path("kind").asText())){var current=n;var visited=new HashSet<String>();while(!"SECTION".equals(current.path("content").path("kind").asText())){if(!visited.add(current.required("node_id").asText()))bad();current=nodeIndex.get(current.path("parent_id").asText());if(current==null||"DOCUMENT".equals(current.path("content").path("kind").asText()))bad();}}
  var verifier=new TaskSourceVerifierNormalizer(reviews);var ids=new HashSet<String>();for(var claim:draft.claimRecords()){if(!ids.add(claim.claimId())||!verifier.anchorValid(claim.anchor(),body))bad();for(var link:claim.evidenceLinks())if(!reads.containsKey(link.evidenceRef()))blockers.add("WRITER_ORIGINAL_SOURCE_READ_MISSING:"+claim.claimId());}
  var fieldResults=predecessors(predecessors,"N03");if(fieldResults.size()!=1)blockers.add("ORIGINAL_FIELD_PREDECESSOR_REQUIRED");else{
   contract.predecessor(fieldResults.get(0));var fields=mapper.treeToValue(fieldResults.get(0).required("output").required("normalized"),ArtifactProtocol.FieldCheckResult.class);var checks=new HashMap<String,ArtifactProtocol.FieldComparison>();for(var check:fields.entries())checks.put(check.fieldId()+"/"+check.rowId(),check);
   var assigned=new HashSet<String>();for(var assignment:draft.fieldAssignments()){
    String key=assignment.fieldId()+"/"+assignment.rowId();var check=checks.get(key);if(check==null||!assigned.add(key)||check.result()!=ArtifactProtocol.ComparisonResult.MATCH)blockers.add("WRITER_FIELD_COMPARISON_MISSING:"+key);
    else {same(check.candidateValue(),assignment.value());same(check.evidenceRefs(),assignment.evidenceRefs());}
    var found=new ArrayList<JsonNode>();for(var field:body.required("fields"))if(assignment.fieldId().equals(field.required("field_id").asText()))found.add(field);if(found.size()!=1)bad();var field=found.get(0);same(assignment.nodeId(),field.required("node_id").asText());same(assignment.value().rawText(),nullable(field,"original_value"));same(assignment.value().decimalValue(),nullable(field,"decimal_value"));same(assignment.value().unit(),nullable(field,"unit"));var originals=new ArrayList<JsonNode>();for(var original:fixed.required("baseline").required("fields"))if(assignment.fieldId().equals(original.required("field_id").asText()))originals.add(original);if(originals.size()!=1)bad();for(String fieldKey:List.of("field_definition_id","model","condition"))same(originals.get(0).required(fieldKey),field.required(fieldKey));
   }if(!assigned.equals(checks.keySet())||body.required("fields").size()!=assigned.size()||!fields.missingRequirementIds().isEmpty()||fields.methodCoverage().stream().anyMatch(m->m.result()!=ArtifactProtocol.CoverageResult.COMPLETE))blockers.add("WRITER_FIELD_OR_METHOD_COVERAGE_INCOMPLETE");
  }
  for(var link:body.required("evidence_links")){var evidence=mapper.treeToValue(link.required("evidence"),DatasetProtocol.EvidenceRef.class);if(!reads.containsKey(evidence))blockers.add("WRITER_BODY_SOURCE_READ_MISSING");}
  for(var attachment:draft.attachmentBindings())for(var evidence:attachment.evidenceRefs())if(!reads.containsKey(evidence)||!attachment.sourceVersion().equals(evidence.sourceVersion()))blockers.add("WRITER_ATTACHMENT_ORIGINAL_MISSING");
  // Existing binary content and fact identities may be retained, not invented by a model.
  for(var node:body.required("nodes"))if(Set.of("IMAGE","FORMULA","ATTACHMENT").contains(node.path("content").path("kind").asText())){boolean found=false;for(var old:fixed.required("baseline").required("nodes"))if(old.equals(node))found=true;if(!found)blockers.add("WRITER_BINARY_ORIGINAL_MISSING:"+node.required("node_id").asText());}
  for(var fact:body.required("fact_usages")){boolean found=false;for(var old:fixed.required("baseline").required("fact_usages"))if(old.equals(fact))found=true;if(!found)blockers.add("WRITER_FACT_AUTHORITY_MISSING");}
  if(!draft.issueProposals().isEmpty())blockers.add("WRITER_REPORTED_ISSUES");return projection.deepCopy();
 }
 private JsonNode review(String kind,String session,String action,JsonNode fixed,JsonNode compiler,JsonNode projection,JsonNode predecessors,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads,List<JsonNode> receipts,Set<String> blockers,Contract contract)throws Exception {
  JsonNode body,claims;String target;var writers=new HashSet<String>();
  for(var writer:predecessors(predecessors,"N04"))writers.add(writer.required("session_id").asText());if(writers.contains(session))bad();
  if("N07".equals(kind)){
   var saved=compiler.path("saved_body_original");if(!saved.isObject()){blockers.add("APPROVED_SAVED_DOCUMENT_BODY_REQUIRED");return null;}
   if(contract.v2()){var c=SchemaCatalogue.fromClasspath();same(c.schemaRef(contract.bodyName(),"DocumentBody"),saved.required("body_schema_ref").asText());same(c.schemaDigest(contract.bodyName()),saved.required("body_schema_sha256").asText());}same(fixed.required("document_id"),saved.required("document_id"));same(fixed.required("document_revision"),saved.required("document_revision"));same(fixed.required("body_sha256"),saved.required("body_sha256"));body=saved.required("body");same(hash(body),saved.required("body_sha256").asText());target=saved.required("document_revision").asText();claims=fixed.required("claim_records");
  }else{
   var candidates=predecessors(predecessors,"N04");if(candidates.size()!=1){blockers.add("ORIGINAL_WRITER_PREDECESSOR_REQUIRED");return null;}var writer=candidates.get(0);contract.predecessor(writer);body=writer.required("output").required("body");claims=writer.required("output").required("normalized").required("claim_records");target=writer.required("artifact").required("artifact_id").asText();
  }
  contract.body(body);
  var requiredClaims=new LinkedHashMap<String,ArtifactProtocol.ClaimRecord>();var claimReads=new LinkedHashMap<String,List<TaskSourceVerifierNormalizer.Read>>();var evidence=new HashSet<DatasetProtocol.EvidenceRef>();
  for(var raw:claims){var claim=mapper.treeToValue(raw,ArtifactProtocol.ClaimRecord.class);if(requiredClaims.put(claim.claimId(),claim)!=null)bad();var actual=new ArrayList<TaskSourceVerifierNormalizer.Read>();var seen=new HashSet<DatasetProtocol.EvidenceRef>();for(var link:claim.evidenceLinks()){evidence.add(link.evidenceRef());if(seen.add(link.evidenceRef())&&reads.containsKey(link.evidenceRef()))actual.add(new TaskSourceVerifierNormalizer.Read(session,reads.get(link.evidenceRef()).get(0).required("receipt_id").asText(),link.evidenceRef(),true,true,true));}claimReads.put(claim.claimId(),actual);}
  var rules=new LinkedHashMap<String,TaskSourceVerifierNormalizer.Rule>();var readRules=new HashSet<String>();for(var rule:fixed.required("rules")){
   String id=rule.required("rule_id").asText();var anchor=mapper.treeToValue(rule.required("anchor"),ArtifactProtocol.Anchor.class);var approved=rule.has("rule_revision")?businessRule(compiler,rule.required("rule_revision")):null;if(rules.put(id,new TaskSourceVerifierNormalizer.Rule(id,strings(rule.required("target_node_ids")),anchor,approved==null?ArtifactProtocol.RuleResult.BLOCKED:null))!=null)bad();
   for(var receipt:receipts)if("AT-03".equals(receipt.path("tool_id").asText())){var payload=receipt.required("response").required("payload");if("application/json".equals(payload.path("media_type").asText())){var content=mapper.readTree(payload.required("content").asText());if(approved!=null&&containsExact(content,json.tree(approved)))readRules.add(id);}}
  }
  if(rules.isEmpty()){blockers.add("APPROVED_REVIEW_RULES_MISSING");return null;}
  var data=new TaskSourceVerifierNormalizer.Fixed(target,hash(body),fixed.required("rule_revision").asText(),session,writers,body,requiredClaims,rules,claimReads,readRules,evidence);
  var normalizer=new TaskSourceVerifierNormalizer(reviews);var normalized="N07".equals(kind)?normalizer.normalizeDocument(data,action,projection,contract.ref(),contract.sha()):normalizer.normalize(data,action,projection,contract.ref(),contract.sha());var findings=normalized.findings();
  if(!findings.coverageComplete())blockers.add("INDEPENDENT_REVIEW_COVERAGE_INCOMPLETE");if(!findings.issueProposals().isEmpty()||findings.claimChecks().stream().anyMatch(c->c.supportCheck()!=ArtifactProtocol.SupportCheck.SUPPORTED&&c.supportCheck()!=ArtifactProtocol.SupportCheck.NOT_APPLICABLE)||findings.ruleChecks().stream().anyMatch(r->r.result()!=ArtifactProtocol.RuleResult.PASS))blockers.add("INDEPENDENT_REVIEW_BLOCKING_FINDINGS");return reviews.tree(findings);
 }
 private List<JsonNode> assemble(JsonNode fixed,JsonNode compiler,JsonNode projection,JsonNode predecessors,Set<String> blockers,Contract contract)throws Exception {
  if(!projection.isNull())SchemaCatalogue.fromClasspath().bindPublished(contract.ref(),contract.sha(),json.canonical(projection).getBytes(StandardCharsets.UTF_8));var expected=strings(fixed.required("section_order"));if(new HashSet<>(expected).size()!=expected.size()||expected.isEmpty())bad();var writers=predecessors(predecessors,"N04");var reviews=predecessors(predecessors,"N05");
  for(var p:writers)contract.predecessor(p);for(var p:reviews)contract.predecessor(p);var baseline=contract.body(fixed.required("baseline"));var body=(ObjectNode)baseline.deepCopy();var nodes=body.putArray("nodes");String root=null;for(var n:baseline.required("nodes"))if("DOCUMENT".equals(n.path("content").path("kind").asText())){nodes.add(n);root=n.required("node_id").asText();}if(root==null)bad();
  var fields=body.putArray("fields");var links=body.putArray("evidence_links");var facts=body.putArray("fact_usages");var sectionBodies=new HashMap<String,JsonNode>();var sourceBySection=new HashMap<String,JsonNode>();
  for(var writer:writers){var b=writer.required("output").required("body");var owners=writer.path("business_original").path("section_owners");for(var n:b.required("nodes"))if("SECTION".equals(n.path("content").path("kind").asText())){String nodeId=n.required("node_id").asText();String section=owners.path(nodeId).asText();if(section.isEmpty()){blockers.add("ORIGINAL_CHAPTER_ASSIGNMENT_REQUIRED:"+nodeId);continue;}if(!expected.contains(section)||sectionBodies.put(section,b)!=null)bad();sourceBySection.put(section,writer);}}
  if(!sectionBodies.keySet().equals(new HashSet<>(expected))){blockers.add("ASSIGNED_CHAPTER_SET_INCOMPLETE");return Arrays.asList(null,null);}var seenNodes=new HashSet<String>();seenNodes.add(root);var seenFields=new HashSet<String>();var seenLinks=new HashMap<String,JsonNode>();var shared=new HashMap<String,JsonNode>();
  var versions=new ArrayList<ArtifactProtocol.SectionVersion>();var findingsIds=new ArrayList<String>();var evidence=new LinkedHashSet<DatasetProtocol.EvidenceRef>();var processed=new HashSet<String>();
  for(String section:expected){var b=sectionBodies.get(section);if(b==null)continue;var writer=sourceBySection.get(section);same(body.required("template_version"),b.required("template_version"));String candidateId=writer.required("artifact").required("artifact_id").asText();if(!processed.add(candidateId)){versions.add(new ArtifactProtocol.SectionVersion(section,ArtifactProtocol.VersionKind.CANDIDATE,candidateId,versions.size(),hash(b)));continue;}
   boolean reviewed=false;for(var review:reviews){var f=review.required("output").required("normalized");if(writer.required("artifact").required("artifact_id").equals(f.path("target_revision"))&&hash(b).equals(f.path("target_sha256").asText())&&f.path("coverage_complete").asBoolean()&&f.path("issue_proposals").isEmpty()){reviewed=true;findingsIds.add(review.required("artifact").required("artifact_id").asText());}}
   if(!reviewed)blockers.add("CHAPTER_INDEPENDENT_REVIEW_REQUIRED:"+section);
   for(var n:b.required("nodes"))if(!"DOCUMENT".equals(n.path("content").path("kind").asText())){String id=n.required("node_id").asText();if(!seenNodes.add(id))bad();var copy=(ObjectNode)n.deepCopy();if("SECTION".equals(n.path("content").path("kind").asText())){copy.put("parent_id",root);String owner=writer.required("business_original").required("section_owners").required(id).asText();copy.put("ordinal",expected.indexOf(owner));}nodes.add(copy);}
   for(var f:b.required("fields")){if(!seenFields.add(f.required("field_id").asText()))bad();fields.add(f);String key=f.required("field_definition_id").asText()+"/"+f.required("model")+"/"+f.required("condition");var value=json.object();for(String k:List.of("original_value","decimal_value","unit"))value.set(k,f.required(k));var old=shared.putIfAbsent(key,value);if(old!=null&&!old.equals(value))blockers.add("CROSS_CHAPTER_FIELD_CONFLICT:"+key);}
   for(var link:b.required("evidence_links")){var old=seenLinks.putIfAbsent(link.required("evidence_link_id").asText(),link);if(old!=null&&!old.equals(link))bad();if(old==null)links.add(link);evidence.add(mapper.treeToValue(link.required("evidence"),DatasetProtocol.EvidenceRef.class));}
   for(var fact:b.required("fact_usages")){ObjectNode existing=null;for(var prior:facts)if(prior.required("fact_version_id").equals(fact.required("fact_version_id")))existing=(ObjectNode)prior;if(existing==null)facts.add(fact.deepCopy());else for(String key:List.of("node_ids","field_ids")){var merged=new TreeSet<>(strings(existing.required(key)));merged.addAll(strings(fact.required(key)));existing.set(key,json.tree(merged));}}versions.add(new ArtifactProtocol.SectionVersion(section,ArtifactProtocol.VersionKind.CANDIDATE,writer.required("artifact").required("artifact_id").asText(),versions.size(),hash(b)));
  }
  var fieldFacts=new HashMap<String,String>();for(var fact:facts)for(var fieldId:fact.required("field_ids")){String prior=fieldFacts.putIfAbsent(fieldId.asText(),fact.required("fact_version_id").asText());if(prior!=null&&!prior.equals(fact.required("fact_version_id").asText()))blockers.add("ASSEMBLY_FIELD_FACT_CONFLICT:"+fieldId.asText());}
  var definitionFacts=new HashMap<String,String>();for(var field:fields){String version=fieldFacts.get(field.required("field_id").asText());if(version!=null){String key=field.required("field_definition_id")+"/"+field.required("model")+"/"+field.required("condition");String prior=definitionFacts.putIfAbsent(key,version);if(prior!=null&&!prior.equals(version))blockers.add("CROSS_CHAPTER_FACT_VERSION_CONFLICT:"+key);}}
  var applicability=mapper.treeToValue(fixed.required("applicability"),TemplateApplicabilityProtocol.TemplateView.class);if(!"APPLICABLE".equals(applicability.applicability())||!applicability.missing_requirements().isEmpty()||applicability.conditions().stream().anyMatch(c->!"APPLICABLE".equals(c.status())))blockers.add("ASSEMBLY_APPLICABILITY_INCOMPLETE");
  // No fabricated cross-file PASS: original external mappings/reads must be independently computed.
  crossFiles(fixed,compiler,body,applicability,blockers);
  for(var requirement:applicability.template().content().field_definitions())if(requirement.required()){boolean found=false;for(var f:fields)if(requirement.field_id().equals(f.required("field_definition_id").asText()))found=true;if(!found)blockers.add("ASSEMBLY_REQUIRED_FIELD_MISSING:"+requirement.field_id());}
  for(var attachment:applicability.template().content().attachment_requirements()){
   var sources=new HashSet<String>();for(var writer:writers)for(var a:writer.required("output").required("normalized").required("attachment_bindings"))if(attachment.attachment_requirement_id().equals(a.required("requirement_id").asText())){if(!seenNodes.contains(a.required("node_id").asText())||a.required("evidence_refs").isEmpty())bad();sources.add(a.required("source_version").asText());}
   var count=java.math.BigInteger.valueOf(sources.size());if(count.compareTo(new java.math.BigInteger(attachment.min_count()))<0||attachment.required()&&sources.isEmpty())blockers.add("ASSEMBLY_ATTACHMENT_MISSING:"+attachment.attachment_requirement_id());if(attachment.max_count()!=null&&count.compareTo(new java.math.BigInteger(attachment.max_count()))>0)blockers.add("ASSEMBLY_ATTACHMENT_COUNT_EXCEEDED:"+attachment.attachment_requirement_id());
  }
  if(blockers.isEmpty())contract.body(body);
  var factIds=new TreeSet<String>();for(var fact:facts)factIds.add(fact.required("fact_version_id").asText());var manifest=new ArtifactProtocol.AssemblyManifest(ArtifactProtocol.AssemblyKind.CANDIDATE_SET,null,versions,strings(fixed.required("requirement_nodes")),List.copyOf(factIds),List.copyOf(evidence),fixed.required("rule_revision").asText(),List.of(),findingsIds,List.of(),applicability.template().content().format_profile().format_revision().version_id());return List.of(json.tree(manifest),body);
 }
 private boolean completion(String question,String ruleId,JsonNode fixed,JsonNode compiler,Map<DatasetProtocol.EvidenceRef,List<JsonNode>> reads,Set<String> blockers)throws Exception {
  var matching=new ArrayList<JsonNode>();for(var input:fixed.path("completion_inputs"))if(question.equals(input.path("question_id").asText())&&ruleId.equals(input.path("rule_id").asText()))matching.add(input);
  if(matching.size()!=1){blockers.add("RESEARCH_RULE_MAPPING_REQUIRED:"+ruleId);return false;}var input=matching.get(0);var rule=businessRule(compiler,input.required("rule_revision"));if(rule==null){blockers.add("APPROVED_COMPLETION_RULE_REQUIRED:"+ruleId);return false;}
  var attributes=new ArrayList<ProjectProtocol.Attribute>();var keys=new HashSet<String>();for(var selector:input.required("selectors")){
   String key=selector.required("input_key").asText();if(!keys.add(key))bad();var evidence=mapper.treeToValue(selector.required("evidence_ref"),DatasetProtocol.EvidenceRef.class);JsonNode value=NullNode.instance;
   var actual=reads.get(evidence);if(actual!=null&&!actual.isEmpty()){String text=actual.get(0).required("response").required("payload").required("content").asText();int start=selector.required("start").intValue(),end=selector.required("end").intValue();if(start<0||end<=start||end>text.codePointCount(0,text.length()))bad();String literal=text.substring(text.offsetByCodePoints(0,start),text.offsetByCodePoints(0,end));String kind=selector.required("value_kind").asText();value=switch(kind){case "TEXT"->TextNode.valueOf(literal);case "DECIMAL"->{if(!literal.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"))yield NullNode.instance;yield DecimalNode.valueOf(new java.math.BigDecimal(literal));}case "BOOLEAN"->"true".equals(literal)?BooleanNode.TRUE:"false".equals(literal)?BooleanNode.FALSE:NullNode.instance;default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);};}
   attributes.add(new ProjectProtocol.Attribute(key,value.isNull()?"UNKNOWN":"KNOWN",value,List.of(),"Original independent EVAL source selector"));
  }
  same(new HashSet<>(rule.expression().input_keys()),keys);var engine=new RuleExpression();var result=TaskResearchCompletionChecks.evaluate(engine,ruleId,List.of(ruleId),rule,attributes);if(result.outcome()!=TaskResearchCompletionChecks.Outcome.SATISFIED){blockers.add("RESEARCH_RULE_"+result.outcome()+":"+ruleId);return false;}return true;
 }
 private TemplateApplicabilityProtocol.RuleContent businessRule(JsonNode compiler,JsonNode basis)throws Exception {
  for(var rule:compiler.path("business_rules"))if(basis.equals(rule.required("rule_revision"))){same(basis.required("content_sha256").asText(),rule.required("sha256").asText());return mapper.treeToValue(rule.required("content"),TemplateApplicabilityProtocol.RuleContent.class);}return null;
 }
 private void crossFiles(JsonNode fixed,JsonNode compiler,JsonNode body,TemplateApplicabilityProtocol.TemplateView applicability,Set<String> blockers)throws Exception {
  if(applicability.template().content().cross_document_rules().isEmpty())return;
  if(!fixed.path("cross_file_inputs").isObject()){blockers.add("CROSS_FILE_MAPPING_REQUIRED");return;}
  var input=mapper.treeToValue(fixed.required("cross_file_inputs"),TaskCandidateAssemblyCrossFileProtocol.Input.class);var rules=new HashMap<String,TaskCandidateAssemblyCrossFileProtocol.Rule>();for(var r:input.rules())if(rules.put(r.ruleId(),r)!=null)bad();
  var loaded=new HashMap<String,JsonNode>();for(var d:compiler.path("cross_file_originals")){same(hash(d.required("body")),d.required("body_sha256").asText());if(loaded.put(d.required("document_revision").asText(),d)!=null)bad();}
  for(var required:applicability.template().content().cross_document_rules()){
   var selected=rules.get(required.rule_id());if(selected==null){blockers.add("CROSS_FILE_MAPPING_REQUIRED:"+required.rule_id());continue;}same(json.tree(required.rule_revision()),json.tree(selected.ruleRevision()));var rule=businessRule(compiler,json.tree(selected.ruleRevision()));if(rule==null){blockers.add("APPROVED_CROSS_FILE_RULE_REQUIRED:"+required.rule_id());continue;}
   var attributes=new ArrayList<ProjectProtocol.Attribute>();var keys=new HashSet<String>();var types=new HashSet<String>();var localFields=new HashSet<String>();boolean incomplete=false;
   for(var selector:selected.inputs()){
    if(!keys.add(selector.inputKey()))bad();JsonNode source=body;if("DOCUMENT".equals(selector.sourceKind())){var original=loaded.get(selector.documentRevision());var documents=input.documents().stream().filter(d->d.documentRevision().version_id().equals(selector.documentRevision())).toList();if(documents.size()!=1)bad();types.add(documents.get(0).documentType());if(original==null){incomplete=true;source=null;}else{same(documents.get(0).documentId(),original.required("document_id").asText());same(documents.get(0).bodyArtifact().content_sha256(),original.required("body_sha256").asText());source=original.required("body");}}
    JsonNode value=NullNode.instance;if(source!=null){var found=new ArrayList<JsonNode>();for(var field:source.required("fields"))if(selector.fieldId().equals(field.required("field_id").asText()))found.add(field);if(found.size()>1)bad();if(found.size()==1){var field=found.get(0);if("CANDIDATE".equals(selector.sourceKind()))localFields.add(field.required("field_definition_id").asText());if(selector.factVersion()!=null){boolean bound=false;for(var fact:source.required("fact_usages"))if(selector.factVersion().version_id().equals(fact.required("fact_version_id").asText()))for(var id:fact.required("field_ids"))if(selector.fieldId().equals(id.asText()))bound=true;if(!bound)bad();}value=field.required(selector.valueSlot().toLowerCase(Locale.ROOT));if(!value.isNull()&&"DECIMAL_VALUE".equals(selector.valueSlot()))value=DecimalNode.valueOf(new java.math.BigDecimal(value.asText()));}}
    attributes.add(new ProjectProtocol.Attribute(selector.inputKey(),value.isNull()?"UNKNOWN":"KNOWN",value,List.of(),"Exact approved EVAL cross-file selector"));
   }
   if(!keys.equals(new HashSet<>(rule.expression().input_keys()))||!localFields.equals(new HashSet<>(required.field_ids()))||!types.containsAll(required.related_document_types()))incomplete=true;
   if(incomplete){blockers.add("CROSS_FILE_INPUT_COVERAGE_INCOMPLETE:"+required.rule_id());continue;}var engine=new RuleExpression();var result=engine.evaluate(engine.compile(rule.expression()),attributes);if(!"APPLICABLE".equals(result.status()))blockers.add("CROSS_FILE_"+result.status()+":"+required.rule_id());
  }
 }
 private List<JsonNode> predecessors(JsonNode values,String kind){var result=new ArrayList<JsonNode>();for(var p:values)if(kind.equals(p.path("node_kind").asText()))result.add(p);return result;}
 private static boolean containsExact(JsonNode value,JsonNode wanted){if(value.equals(wanted))return true;for(var child:value)if(containsExact(child,wanted))return true;return false;}
 private static List<String> strings(JsonNode array){if(!array.isArray())bad();var values=new ArrayList<String>();for(var n:array){if(!n.isTextual())bad();values.add(n.asText());}return List.copyOf(values);}
 private String hash(JsonNode n)throws Exception{return WireValues.sha256(json.canonical(n).getBytes(StandardCharsets.UTF_8));}
 private static String nullable(JsonNode n,String key){return n.required(key).isNull()?null:n.required(key).asText();}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
