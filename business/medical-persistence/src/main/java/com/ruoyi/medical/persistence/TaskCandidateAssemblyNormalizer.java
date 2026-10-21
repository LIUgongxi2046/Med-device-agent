package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** N06 host-only assembly. The input is data, not authority; only AssemblyAuthority may
 * authorize production. N05 checks remain bound to original candidate bytes, never to
 * this newly assembled body. The body still requires C04 saving and N07 checks. */
public final class TaskCandidateAssemblyNormalizer {
    private final TaskSourceVerifierSchema json;
    private final SchemaCatalogue schemas;
    public TaskCandidateAssemblyNormalizer(TaskSourceVerifierSchema json)throws Exception {this.json=json;schemas=SchemaCatalogue.fromClasspath();}

    Result normalize(Fixed fixed)throws Exception {return normalize(fixed,Map.of());}
    Result normalize(Fixed fixed,Map<String,JsonNode> crossChecks)throws Exception {return normalize(fixed,crossChecks,List.of());}
    Result normalize(Fixed fixed,Map<String,JsonNode> crossChecks,List<String> impactBlockers)throws Exception {
        var blockers=new LinkedHashSet<String>(impactBlockers);
        var expected=new LinkedHashSet<>(fixed.sectionOrder());
        if(expected.isEmpty()||expected.size()!=fixed.sectionOrder().size())bad();
        var bySection=new LinkedHashMap<String,Candidate>();
        for(var c:fixed.candidates()){
            if(c.section().targetSections().size()!=1)bad();
            String section=c.section().targetSections().get(0);
            if(!expected.contains(section)||bySection.putIfAbsent(section,c)!=null)bad();
            if(!fixed.baseRevision().equals(c.section().baseDocumentRevision())||!fixed.bodySchemaRevision().equals(c.section().bodySchemaRevision()))bad();
            if(!json.hash(c.body()).equals(c.bodySha256()))bad();
            if(!c.section().issueProposals().isEmpty())blockers.add("CANDIDATE_ISSUES:"+c.id());
        }
        if(!expected.equals(bySection.keySet()))blockers.add("ASSIGNED_CHAPTER_SET_INCOMPLETE");
        for(var condition:fixed.applicability().conditions())if(!"APPLICABLE".equals(condition.status()))
            blockers.add(("UNKNOWN".equals(condition.status())?"APPLICABILITY_UNKNOWN:":"NOT_APPLICABLE_REQUIRES_HUMAN_DECISION:")+condition.target_id());
        if(!"APPLICABLE".equals(fixed.applicability().applicability()))blockers.add("TEMPLATE_APPLICABILITY_UNRESOLVED");
        if(!fixed.applicability().missing_requirements().isEmpty())blockers.add("TEMPLATE_REQUIREMENTS_INCOMPLETE");
        var baseline=DocumentBodySemantics.verify(schemas,fixed.baseline(),limits(),fixed.contract().bodyRef(),fixed.contract().bodySha());
        String root=null;JsonNode rootNode=null;var baselineNodes=new HashMap<String,JsonNode>();baseline.required("nodes").forEach(n->baselineNodes.put(text(n,"node_id"),n));
        for(var node:baseline.required("nodes"))if("DOCUMENT".equals(text(node.required("content"),"kind"))){root=text(node,"node_id");rootNode=node;}
        if(root==null)bad();
        var body=JsonNodeFactory.instance.objectNode().put("schema_version",fixed.contract().version()).put("template_version",fixed.applicability().template().template_version());
        var nodes=body.putArray("nodes");nodes.add(rootNode.deepCopy());
        var fields=body.putArray("fields");var links=body.putArray("evidence_links");var facts=body.putArray("fact_usages");
        var identities=JsonNodeFactory.instance.arrayNode();var sections=new ArrayList<ArtifactProtocol.SectionVersion>();
        var seenNodes=new HashSet<String>();seenNodes.add(root);var seenFields=new HashSet<String>();var seenClaims=new HashSet<String>();var seenLinks=new HashMap<String,JsonNode>();
        var factNodes=new TreeMap<String,Set<String>>();var factFields=new TreeMap<String,Set<String>>();var fieldFacts=new HashMap<String,String>();
        var evidence=new LinkedHashSet<DatasetProtocol.EvidenceRef>();var checks=new ArrayList<String>();var reviews=new ArrayList<String>();
        var attachmentBindings=new HashMap<String,List<ArtifactProtocol.AttachmentBinding>>();
        var claims=JsonNodeFactory.instance.arrayNode();var assignments=JsonNodeFactory.instance.arrayNode();var attachments=JsonNodeFactory.instance.arrayNode();
        for(String sectionId:fixed.sectionOrder()){
            var c=bySection.get(sectionId);if(c==null)continue;
            var original=DocumentBodySemantics.verify(schemas,c.body(),limits(),fixed.contract().bodyRef(),fixed.contract().bodySha());
            if(!text(body,"template_version").equals(text(original,"template_version")))bad();
            String wrapper=null;for(var n:original.required("nodes"))if("DOCUMENT".equals(text(n.required("content"),"kind")))wrapper=text(n,"node_id");
            if(wrapper==null)bad();
            var map=JsonNodeFactory.instance.objectNode().put("candidate_id",c.id()).put("source_root_id",wrapper).put("assembly_root_id",root);
            var retained=map.putArray("retained_node_ids");map.set("retained_field_ids",json.tree(c.section().fieldAssignments().stream().map(ArtifactProtocol.FieldAssignment::fieldId).toList()));map.set("retained_claim_ids",json.tree(c.section().claimRecords().stream().map(ArtifactProtocol.ClaimRecord::claimId).toList()));
            for(var n:original.required("nodes")){
                String id=text(n,"node_id");if(id.equals(wrapper))continue;if(!seenNodes.add(id))bad();
                var copy=(ObjectNode)n.deepCopy();
                if("SECTION".equals(text(n.required("content"),"kind"))){
                    var originalSection=baselineNodes.get(id);if(originalSection==null||!"SECTION".equals(text(originalSection.required("content"),"kind")))bad();
                    copy.set("parent_id",originalSection.required("parent_id"));copy.set("ordinal",originalSection.required("ordinal"));
                }else if(wrapper.equals(nullable(n,"parent_id")))bad();
                nodes.add(copy);retained.add(id);
            }
            identities.add(map);
            // Root references cannot silently migrate to another chapter's content.
            for(var f:original.required("fields")){if(wrapper.equals(text(f,"node_id"))||!seenFields.add(text(f,"field_id")))bad();fields.add(f.deepCopy());}
            for(var l:original.required("evidence_links")){
                for(var n:l.required("node_ids"))if(wrapper.equals(n.asText()))bad();
                var prior=seenLinks.putIfAbsent(text(l,"evidence_link_id"),l);if(prior!=null&&!prior.equals(l))bad();if(prior==null)links.add(l.deepCopy());
            }
            for(var f:original.required("fact_usages")){
                String version=text(f,"fact_version_id");var ns=factNodes.computeIfAbsent(version,k->new TreeSet<>());var fs=factFields.computeIfAbsent(version,k->new TreeSet<>());
                for(var n:f.required("node_ids")){if(wrapper.equals(n.asText()))bad();ns.add(n.asText());}
                for(var field:f.required("field_ids")){String id=field.asText();var prior=fieldFacts.putIfAbsent(id,version);if(prior!=null&&!prior.equals(version))blockers.add("FACT_VERSION_CONFLICT:"+id);fs.add(id);}
            }
            for(var claim:c.section().claimRecords()){
                if(!seenClaims.add(claim.claimId()))bad();
                if(wrapper.equals(claim.anchor() instanceof ArtifactProtocol.TextAnchor a?a.nodeId():((ArtifactProtocol.StructuredAnchor)claim.anchor()).nodeId()))bad();
                claims.add(json.tree(claim));
            }
            for(var a:c.section().fieldAssignments()){assignments.add(json.tree(a));if(a.factVersion()!=null&&!a.factVersion().equals(fieldFacts.get(a.fieldId())))bad();}
            for(var a:c.section().attachmentBindings()){
                if(!seenNodes.contains(a.nodeId())||a.evidenceRefs().isEmpty()||a.evidenceRefs().stream().anyMatch(e->!a.sourceVersion().equals(e.sourceVersion())))bad();
                attachmentBindings.computeIfAbsent(a.requirementId(),k->new ArrayList<>()).add(a);attachments.add(json.tree(a));
            }
            evidence.addAll(c.evidence());checks.add(c.checkId());reviews.add(c.findingsArtifactId());
            sections.add(new ArtifactProtocol.SectionVersion(sectionId,ArtifactProtocol.VersionKind.CANDIDATE,c.id(),sections.size(),c.bodySha256()));
        }
        for(var version:factNodes.keySet()){
            var usage=JsonNodeFactory.instance.objectNode().put("fact_version_id",version);usage.set("node_ids",json.tree(factNodes.get(version)));usage.set("field_ids",json.tree(factFields.get(version)));facts.add(usage);
        }
        for(var a:fixed.applicability().template().content().attachment_requirements()){
            var actual=attachmentBindings.getOrDefault(a.attachment_requirement_id(),List.of());
            long count=actual.stream().map(ArtifactProtocol.AttachmentBinding::sourceVersion).distinct().count();
            var min=new java.math.BigInteger(a.min_count());var max=a.max_count()==null?null:new java.math.BigInteger(a.max_count());
            if(java.math.BigInteger.valueOf(count).compareTo(min)<0||a.required()&&count==0)blockers.add("ATTACHMENT_MISSING:"+a.attachment_requirement_id());
            if(max!=null&&java.math.BigInteger.valueOf(count).compareTo(max)>0)blockers.add("ATTACHMENT_COUNT_EXCEEDED:"+a.attachment_requirement_id());
        }
        var fieldDefinitions=new HashSet<String>();var sharedValues=new HashMap<String,JsonNode>();
        for(var field:fields){String definition=text(field,"field_definition_id");fieldDefinitions.add(definition);
            var value=JsonNodeFactory.instance.objectNode();for(String key:List.of("original_value","decimal_value","unit","model","condition"))value.set(key,field.required(key));
            String identity=definition+"/"+field.required("model").toString()+"/"+field.required("condition").toString();
            var prior=sharedValues.putIfAbsent(identity,value);if(prior!=null&&!prior.equals(value))blockers.add("CROSS_CHAPTER_FIELD_CONFLICT:"+definition);
        }
        for(var field:fixed.applicability().template().content().field_definitions())if(field.required()&&!fieldDefinitions.contains(field.field_id()))blockers.add("REQUIRED_FIELD_MISSING:"+field.field_id());
        var allowedAttachments=new HashSet<String>();fixed.applicability().template().content().attachment_requirements().forEach(a->allowedAttachments.add(a.attachment_requirement_id()));
        if(!allowedAttachments.containsAll(attachmentBindings.keySet()))bad();
        // Only the separate exact-selection/physical-reader/rule-engine authority supplies
        // these host findings. Missing mappings or reads stay visibly blocked.
        var crossResults=JsonNodeFactory.instance.arrayNode();var requiredCross=new HashSet<String>();
        for(var rule:fixed.applicability().template().content().cross_document_rules()){
            requiredCross.add(rule.rule_id());var check=crossChecks.get(rule.rule_id());
            if(check==null)blockers.add("CROSS_FILE_INPUT_REQUIRED:"+rule.rule_id());
            else {if(!rule.rule_id().equals(text(check,"rule_id")))bad();crossResults.add(check.deepCopy());if(!"PASS".equals(text(check,"status")))blockers.add("CROSS_FILE_"+text(check,"status")+":"+rule.rule_id());}
        }
        if(!requiredCross.containsAll(crossChecks.keySet()))bad();
        var impact=JsonNodeFactory.instance.objectNode();impact.set("changed_node_ids",changed(baseline.required("nodes"),nodes,"node_id"));impact.set("changed_field_ids",changed(baseline.required("fields"),fields,"field_id"));
        impact.set("fact_relationships",facts.deepCopy());impact.put("complete",blockers.stream().noneMatch(b->b.startsWith("CROSS_FILE_INPUT_REQUIRED")||b.startsWith("CROSS_FILE_BLOCKED")||b.startsWith("FACT_IMPACT"))&&expected.equals(bySection.keySet()));
        if(expected.equals(bySection.keySet()))DocumentBodySemantics.verify(schemas,body,limits(),fixed.contract().bodyRef(),fixed.contract().bodySha());
        var report=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N06").put("target_kind","CANDIDATE_SET").put("status",blockers.isEmpty()?"READY":"BLOCKED").put("body_sha256",json.hash(body)).put("document_saved",false).put("n07_complete",false).put("professional_approval",false);
        report.set("cross_file_checks",crossResults);report.set("assigned_section_ids",json.tree(fixed.sectionOrder()));report.set("candidate_ids",json.tree(fixed.candidates().stream().map(Candidate::id).toList()));report.set("identity_mappings",identities);report.set("impact",impact);report.set("blockers",json.tree(blockers));report.set("check_ids",json.tree(checks));report.set("review_ids",json.tree(reviews));
        var manifest=new ArtifactProtocol.AssemblyManifest(ArtifactProtocol.AssemblyKind.CANDIDATE_SET,null,sections,fixed.requirementNodes(),List.copyOf(factNodes.keySet()),List.copyOf(evidence),fixed.ruleRevision(),checks,reviews,List.of(),fixed.applicability().template().content().format_profile().format_revision().version_id());
        schemas.validate(fixed.contract().artifacts(),json.tree(manifest),"AssemblyManifest");
        // Rebind only host-modified structural anchors. This does not migrate N05 approval:
        // the original candidate target/hash remains in the manifest and N07 is still mandatory.
        var anchorMappings=report.putArray("claim_anchor_mappings");
        var nodeIndex=new HashMap<String,JsonNode>();nodes.forEach(n->nodeIndex.put(text(n,"node_id"),n));
        for(var claim:claims){var anchor=(ObjectNode)claim.required("anchor");var mapped=anchorMappings.addObject().put("claim_id",text(claim,"claim_id"));mapped.set("candidate_anchor",anchor.deepCopy());if("STRUCTURED".equals(text(anchor,"kind"))&&anchor.required("row_id").isNull()&&anchor.required("field_id").isNull()){
            var node=nodeIndex.get(text(anchor,"node_id"));if(node==null)bad();anchor.put("content_sha256",json.hash(node));
        }mapped.set("assembly_anchor",anchor.deepCopy());}
        var companion=JsonNodeFactory.instance.objectNode();companion.set("claim_records",claims);companion.set("field_assignments",assignments);companion.set("attachment_bindings",attachments);
        return new Result(manifest,body,companion,report,List.copyOf(blockers));
    }
    private JsonNode changed(JsonNode before,JsonNode after,String key){var old=new TreeMap<String,JsonNode>();var next=new TreeMap<String,JsonNode>();before.forEach(n->old.put(text(n,key),n));after.forEach(n->next.put(text(n,key),n));var ids=new TreeSet<>(old.keySet());ids.addAll(next.keySet());ids.removeIf(id->Objects.equals(old.get(id),next.get(id)));return json.tree(ids);}
    private static DocumentBodySemantics.Limits limits(){return new DocumentBodySemantics.Limits(10000,10000,100000);}
    private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).asText();}
    private static String nullable(JsonNode n,String k){return n.required(k).isNull()?null:text(n,k);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    record Candidate(String id,ArtifactProtocol.SectionCandidate section,JsonNode body,String bodySha256,Set<DatasetProtocol.EvidenceRef> evidence,String checkId,String findingsArtifactId){Candidate{body=body.deepCopy();evidence=Set.copyOf(evidence);}@Override public JsonNode body(){return body.deepCopy();}}
    record Fixed(String baseRevision,String bodySchemaRevision,List<String> sectionOrder,List<String> requirementNodes,TemplateApplicabilityProtocol.TemplateView applicability,JsonNode baseline,String ruleRevision,List<Candidate> candidates,TaskWriterEvidenceContract contract){Fixed(String baseRevision,String bodySchemaRevision,List<String> sectionOrder,List<String> requirementNodes,TemplateApplicabilityProtocol.TemplateView applicability,JsonNode baseline,String ruleRevision,List<Candidate> candidates){this(baseRevision,bodySchemaRevision,sectionOrder,requirementNodes,applicability,baseline,ruleRevision,candidates,new TaskWriterEvidenceContract(false));}Fixed{Objects.requireNonNull(contract);sectionOrder=List.copyOf(sectionOrder);requirementNodes=List.copyOf(requirementNodes);baseline=baseline.deepCopy();candidates=List.copyOf(candidates);}@Override public JsonNode baseline(){return baseline.deepCopy();}}
    record Result(ArtifactProtocol.AssemblyManifest manifest,JsonNode body,JsonNode companion,JsonNode report,List<String> blockers){Result{body=body.deepCopy();companion=companion.deepCopy();report=report.deepCopy();blockers=List.copyOf(blockers);}@Override public JsonNode body(){return body.deepCopy();}@Override public JsonNode companion(){return companion.deepCopy();}@Override public JsonNode report(){return report.deepCopy();}boolean ready(){return blockers.isEmpty();}}
}
