package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** AS-07–11 structural normalization. It never asserts independent claim coverage or adoption.
 * All original evidence values remain untouched; only host-owned local identities are rewritten. */
public final class TaskSectionWriterNormalizer {
    private final TaskSectionWriterSchema json;private final SchemaCatalogue schemas;private final DocumentBodySemantics.Limits limits;
    public TaskSectionWriterNormalizer(TaskSectionWriterSchema json,DocumentBodySemantics.Limits limits)throws Exception {
        this.json=Objects.requireNonNull(json);this.limits=Objects.requireNonNull(limits);schemas=SchemaCatalogue.fromClasspath();
    }
    /** Data-only implementation is package private. Production must obtain its Fixed from the private input authority. */
    Normalized normalize(Fixed fixed,String actionId,JsonNode projection)throws Exception {
        WireValues.uuid(actionId);var input=fixed.input();var contract=fixed.evidenceContract();var draft=json.draft(projection,contract.artifactRef("SectionDraft"),contract.artifactSha());
        if(!input.bodySchemaRevision().equals(draft.bodySchemaRevision())||!contract.bodySha().equals(input.bodySchemaSha256()))bad();
        var body=DocumentBodySemantics.verify(schemas,json.read(draft.bodyJson()),limits,contract.bodyRef(),contract.bodySha());
        if(!input.templateVersion().equals(body.required("template_version").textValue()))bad();
        var originalNodes=index(fixed.baseline().required("nodes"),"node_id");var nodes=index(body.required("nodes"),"node_id");
        var targets=new LinkedHashSet<>(input.targetSections());var sections=new HashSet<String>();var nodeSections=new LinkedHashMap<String,String>();
        for(var n:nodes.values()){
            String kind=text(n.required("content"),"kind"),id=text(n,"node_id");
            if("SECTION".equals(kind)){String owner=fixed.sectionOwners().get(id);if(owner==null||!targets.contains(owner))bad();sections.add(owner);}
            if(originalNodes.containsKey(id)){
                var original=originalNodes.get(id);
                if(!kind.equals(text(original.required("content"),"kind")))bad();
                if(!"DOCUMENT".equals(kind)&&!targets.contains(fixed.sectionOwners().get(section(originalNodes,id))))bad();
            }
            if(!"DOCUMENT".equals(kind))nodeSections.put(id,fixed.sectionOwners().get(section(nodes,id)));
        }
        if(!targets.equals(sections)||!targets.containsAll(nodeSections.values()))bad();
        // A single N04 chapter owns its structural document wrapper. Multi-chapter output must
        // be split into chapter steps, rather than attributing a wrapper to an arbitrary section.
        if(targets.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var identities=new LinkedHashMap<String,String>();var types=new HashMap<String,String>();
        for(var n:nodes.values())local(identities,types,actionId,"node",text(n,"node_id"),originalNodes.containsKey(text(n,"node_id")));
        Set<String> existingRows=tableIds(fixed.baseline(),"row_ids"),existingColumns=tableIds(fixed.baseline(),"column_ids"),existingCells=cellIds(fixed.baseline());
        for(var n:nodes.values())if("TABLE".equals(text(n.required("content"),"kind"))){var c=n.required("content");
            for(var old:originalNodes.values())if("TABLE".equals(text(old.required("content"),"kind"))){var oc=old.required("content");boolean sameTable=text(old,"node_id").equals(text(n,"node_id"));
                for(var row:c.required("row_ids"))if(contains(oc.required("row_ids"),row.textValue())&&!sameTable)bad();
                for(var column:c.required("column_ids"))if(contains(oc.required("column_ids"),column.textValue())&&!sameTable)bad();
                for(var cell:c.required("cells"))for(var prior:oc.required("cells"))if(text(cell,"cell_id").equals(text(prior,"cell_id"))&&(!sameTable||!text(cell,"row_id").equals(text(prior,"row_id"))||!text(cell,"column_id").equals(text(prior,"column_id"))))bad();
            }
            for(var row:c.required("row_ids"))local(identities,types,actionId,"row",row.textValue(),existingRows.contains(row.textValue()));
            for(var column:c.required("column_ids"))local(identities,types,actionId,"column",column.textValue(),existingColumns.contains(column.textValue()));
            for(var cell:c.required("cells"))local(identities,types,actionId,"cell",text(cell,"cell_id"),existingCells.contains(text(cell,"cell_id")));
        }
        var originalEvidence=index(fixed.baseline().required("evidence_links"),"evidence_link_id");
        for(var link:body.required("evidence_links")){
            String id=text(link,"evidence_link_id");if(originalEvidence.containsKey(id)&&!originalEvidence.get(id).equals(link))bad();
            local(identities,types,actionId,"evidence_link",id,originalEvidence.containsKey(id));
        }
        var claimIds=new HashSet<String>();for(var claim:draft.claimRecords()){
            if(!claimIds.add(claim.claimId()))bad();local(identities,types,actionId,"claim",claim.claimId(),false);
            anchor(claim.anchor(),nodes,body);if(claim.claimKind()!=ArtifactProtocol.ClaimKind.TRANSITION&&claim.evidenceLinks().isEmpty())bad();
        }
        var issueKeys=new HashSet<String>();for(var issue:draft.issueProposals()){
            if(!issueKeys.add(issue.issueKey())||!claimIds.containsAll(issue.claimIds()))bad();anchor(issue.anchor(),nodes,body);
        }
        var allEvidence=new LinkedHashSet<DatasetProtocol.EvidenceRef>();
        var typedBody=DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,body,contract.bodyRef(),contract.bodySha()).read();
        for(var link:typedBody.evidenceLinks())allEvidence.add(link.evidence());
        for(var claim:draft.claimRecords())for(var link:claim.evidenceLinks())allEvidence.add(link.evidenceRef());
        for(var field:draft.fieldAssignments())allEvidence.addAll(field.evidenceRefs());
        for(var attachment:draft.attachmentBindings()){
            if(!targets.contains(fixed.sectionOwners().get(attachment.requirementId()))||!nodes.containsKey(attachment.nodeId())||attachment.evidenceRefs().isEmpty())bad();
            for(var evidence:attachment.evidenceRefs())if(!attachment.sourceVersion().equals(evidence.sourceVersion()))bad();
            allEvidence.addAll(attachment.evidenceRefs());
        }
        for(var issue:draft.issueProposals())allEvidence.addAll(issue.evidenceRefs());
        if(!new HashSet<>(fixed.evidence()).containsAll(allEvidence))bad();
        for(var ref:allEvidence)if(!input.tenantId().equals(ref.tenantId())||!input.projectId().equals(ref.projectId()))bad();
        requireFields(fixed,draft,typedBody,nodes);
        // Models cannot inject a real attachment merely by naming an artifact or source.
        for(var n:nodes.values()){
            var c=n.required("content");String kind=text(c,"kind");
            if("IMAGE".equals(kind)||"FORMULA".equals(kind)&&!c.required("rendered_artifact_id").isNull()){
                var old=originalNodes.get(text(n,"node_id"));if(old==null||!old.required("content").equals(c))throw new TenantAccessException(ErrorCode.SOURCE_MISSING);
            }
        }
        var rewritten=(ObjectNode)rewrite(body,identities);DocumentBodySemantics.verify(schemas,rewritten,limits,contract.bodyRef(),contract.bodySha());
        var companion=(ObjectNode)rewrite(projection,identities);companion.put("body_json",json.canonical(rewritten));
        for(var issue:companion.required("issue_proposals"))((ObjectNode)issue).put("issue_key",derived(actionId,"issue",text(issue,"issue_key")));
        // Rewrites alter structured node bytes. Anchors are rebound only after validating
        // the original anchor, and hashes are computed over the resulting exact bytes.
        rehashAnchors(companion,rewritten);
        var normalized=json.draft(companion,contract.artifactRef("SectionDraft"),contract.artifactSha());var manifest=new ArrayList<ArtifactProtocol.NodeManifestItem>();
        for(var n:rewritten.required("nodes")){
            String id=text(n,"node_id"),kind=text(n.required("content"),"kind");
            String source=identities.entrySet().stream().filter(e->e.getValue().equals(id)).findFirst().orElseThrow().getKey();
            String section="DOCUMENT".equals(kind)?input.targetSections().get(0):nodeSections.get(source);
            // Fields and rows have their own explicit accompanying records, not a guessed single ID.
            manifest.add(new ArtifactProtocol.NodeManifestItem(id,n.required("parent_id").isNull()?null:text(n,"parent_id"),section,
                    ArtifactProtocol.NodeKind.valueOf("DOCUMENT".equals(kind)?"ROOT":kind),null,null,json.hash(n)));
        }
        var pins=json.tree(Map.of("implementation","N04_SECTION_NORMALIZATION_V1","action_id",actionId,
                "model_projection_sha256",json.hash(projection),"body_sha256",json.hash(rewritten),"identity_map",identities,
                "issue_identity_map",issueKeys.stream().collect(java.util.stream.Collectors.toMap(k->k,k->derived(actionId,"issue",k)))));
        return new Normalized(json.bytes(rewritten),normalized,manifest,List.copyOf(allEvidence),pins);
    }
    private void requireFields(Fixed fixed,ArtifactProtocol.SectionDraft draft,InterfaceProtocol.DocumentBody body,Map<String,JsonNode> nodes)throws Exception {
        var checks=new HashMap<FieldKey,ArtifactProtocol.FieldComparison>();
        for(var check:fixed.fields().entries()){
            if(check.result()!=ArtifactProtocol.ComparisonResult.MATCH)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            if(checks.putIfAbsent(new FieldKey(check.fieldId(),check.rowId()),check)!=null)bad();
        }
        if(!fixed.fields().missingRequirementIds().isEmpty()||fixed.fields().methodCoverage().stream().anyMatch(m->m.result()!=ArtifactProtocol.CoverageResult.COMPLETE))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var assignments=new HashSet<FieldKey>();var originalFields=index(fixed.baseline().required("fields"),"field_id");
        var actualFields=new HashMap<String,InterfaceProtocol.FieldValue>();body.fields().forEach(f->{if(actualFields.putIfAbsent(f.fieldId(),f)!=null)bad();});
        for(var f:draft.fieldAssignments()){
            var key=new FieldKey(f.fieldId(),f.rowId());var check=checks.get(key);var actual=actualFields.get(f.fieldId());
            if(!assignments.add(key)||check==null||actual==null||!nodes.containsKey(f.nodeId())||!actual.nodeId().equals(f.nodeId())
                    ||!check.candidateValue().equals(f.value())||!check.evidenceRefs().equals(f.evidenceRefs())
                    ||!originalFields.containsKey(f.fieldId())||!Objects.equals(actual.model(),nullable(originalFields.get(f.fieldId()),"model"))||!Objects.equals(actual.condition(),nullable(originalFields.get(f.fieldId()),"condition"))
                    ||!Objects.equals(actual.originalValue(),f.value().rawText())||!Objects.equals(actual.decimalValue(),f.value().decimalValue())||!Objects.equals(actual.unit(),f.value().unit()))bad();
            // A model may reuse a fixed fact usage, never fabricate an approved fact version.
            if(f.factVersion()!=null){boolean exists=false;for(var usage:fixed.baseline().required("fact_usages"))
                if(f.factVersion().equals(text(usage,"fact_version_id"))&&contains(usage.required("field_ids"),f.fieldId()))exists=true;if(!exists)bad();}
        }
        if(!assignments.equals(checks.keySet())||actualFields.size()!=assignments.size())bad();
        var expectedFacts=JsonNodeFactory.instance.arrayNode();for(var usage:fixed.baseline().required("fact_usages")){
            var retained=(ObjectNode)usage.deepCopy();var selectedNodes=retained.putArray("node_ids");for(var n:usage.required("node_ids"))if(nodes.containsKey(n.textValue()))selectedNodes.add(n);var selectedFields=retained.putArray("field_ids");for(var f:usage.required("field_ids"))if(actualFields.containsKey(f.textValue()))selectedFields.add(f);if(!selectedNodes.isEmpty()||!selectedFields.isEmpty())expectedFacts.add(retained);
        }if(!expectedFacts.equals(json.tree(body.factUsages())))bad();
    }
    private void anchor(ArtifactProtocol.Anchor anchor,Map<String,JsonNode> nodes,JsonNode body)throws Exception {
        if(anchor instanceof ArtifactProtocol.TextAnchor a){
            var node=nodes.get(a.nodeId());if(node==null||!"TEXT".equals(text(node.required("content"),"kind")))bad();String text=text(node.required("content"),"text");
            int length=text.codePointCount(0,text.length());if(a.start()<0||a.end()<=a.start()||a.end()>length)bad();
            String slice=text.substring(text.offsetByCodePoints(0,Math.toIntExact(a.start())),text.offsetByCodePoints(0,Math.toIntExact(a.end())));
            if(!WireValues.sha256(slice.getBytes(StandardCharsets.UTF_8)).equals(a.textSha256()))bad();
        }else if(anchor instanceof ArtifactProtocol.StructuredAnchor a){
            if(!a.contentSha256().equals(json.hash(structured(nodes,body,a.nodeId(),a.rowId(),a.fieldId()))))bad();
        }else bad();
    }
    private static JsonNode structured(Map<String,JsonNode> nodes,JsonNode body,String nodeId,String row,String field){
        var node=nodes.get(nodeId);if(node==null)bad();
        if(field!=null){for(var f:body.required("fields"))if(field.equals(text(f,"field_id"))&&nodeId.equals(text(f,"node_id"))){if(row!=null)bad();return f;}bad();}
        if(row!=null){var c=node.required("content");if(!"TABLE".equals(text(c,"kind"))||!contains(c.required("row_ids"),row))bad();
            var selected=JsonNodeFactory.instance.arrayNode();for(var cell:c.required("cells"))if(row.equals(text(cell,"row_id")))selected.add(cell);if(selected.isEmpty())bad();return selected;}
        return node;
    }
    private void rehashAnchors(ObjectNode companion,JsonNode body)throws Exception {
        var nodes=index(body.required("nodes"),"node_id");for(String key:List.of("claim_records","issue_proposals"))for(var item:companion.required(key)){
            var anchor=(ObjectNode)item.required("anchor");if("STRUCTURED".equals(text(anchor,"kind")))anchor.put("content_sha256",json.hash(structured(nodes,body,text(anchor,"node_id"),nullable(anchor,"row_id"),nullable(anchor,"field_id"))));
        }
    }
    private static JsonNode rewrite(JsonNode n,Map<String,String> ids){
        if(n.isArray()){var out=JsonNodeFactory.instance.arrayNode();n.forEach(x->out.add(rewrite(x,ids)));return out;}
        if(!n.isObject())return n.deepCopy();var out=JsonNodeFactory.instance.objectNode();n.fields().forEachRemaining(e->{String key=e.getKey();var v=e.getValue();
            if(Set.of("node_id","parent_id","row_id","column_id","cell_id","claim_id","evidence_link_id").contains(key)&&v.isTextual())out.put(key,ids.getOrDefault(v.textValue(),v.textValue()));
            else if(Set.of("node_ids","row_ids","column_ids","claim_ids","evidence_link_ids").contains(key)&&v.isArray()){var a=out.putArray(key);v.forEach(x->a.add(ids.getOrDefault(x.textValue(),x.textValue())));}
            else out.set(key,rewrite(v,ids));});return out;
    }
    private static void local(Map<String,String> map,Map<String,String> types,String action,String type,String id,boolean existing){
        if(types.putIfAbsent(id,type)!=null)bad();String value=existing?id:derived(action,type,id);if(map.containsValue(value))bad();map.put(id,value);
    }
    private static String derived(String action,String type,String id){return UUID.nameUUIDFromBytes(("N04/"+action+"/"+type+"/"+id).getBytes(StandardCharsets.UTF_8)).toString();}
    private static Set<String> tableIds(JsonNode body,String key){var values=new HashSet<String>();for(var n:body.required("nodes"))if("TABLE".equals(text(n.required("content"),"kind")))n.required("content").required(key).forEach(x->values.add(x.textValue()));return values;}
    private static Set<String> cellIds(JsonNode body){var values=new HashSet<String>();for(var n:body.required("nodes"))if("TABLE".equals(text(n.required("content"),"kind")))n.required("content").required("cells").forEach(x->values.add(text(x,"cell_id")));return values;}
    private static Map<String,JsonNode> index(JsonNode array,String key){var out=new LinkedHashMap<String,JsonNode>();for(var n:array)if(out.putIfAbsent(text(n,key),n)!=null)bad();return out;}
    private static String section(Map<String,JsonNode> nodes,String id){var seen=new HashSet<String>();while(id!=null){if(!seen.add(id))bad();var n=nodes.get(id);if(n==null)bad();if("SECTION".equals(text(n.required("content"),"kind")))return id;id=nullable(n,"parent_id");}bad();return null;}
    private static boolean contains(JsonNode a,String id){for(var n:a)if(id.equals(n.textValue()))return true;return false;}
    private static String nullable(JsonNode n,String key){return n.required(key).isNull()?null:text(n,key);}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private record FieldKey(String field,String row){}
    record Fixed(TaskSectionWriterInput input,JsonNode baseline,ArtifactProtocol.FieldCheckResult fields,List<DatasetProtocol.EvidenceRef> evidence,Map<String,String> sectionOwners,TaskWriterEvidenceContract evidenceContract){
        Fixed(TaskSectionWriterInput input,JsonNode baseline,ArtifactProtocol.FieldCheckResult fields,List<DatasetProtocol.EvidenceRef> evidence,Map<String,String> sectionOwners){this(input,baseline,fields,evidence,sectionOwners,new TaskWriterEvidenceContract(false));}
        Fixed{sectionOwners=Map.copyOf(sectionOwners);Objects.requireNonNull(input);baseline=baseline.deepCopy();Objects.requireNonNull(fields);evidence=List.copyOf(evidence);}
        @Override public JsonNode baseline(){return baseline.deepCopy();}
    }
    record Normalized(byte[] body,ArtifactProtocol.SectionDraft draft,List<ArtifactProtocol.NodeManifestItem> manifest,List<DatasetProtocol.EvidenceRef> evidence,JsonNode pins){
        Normalized{body=body.clone();manifest=List.copyOf(manifest);evidence=List.copyOf(evidence);pins=pins.deepCopy();}
        @Override public byte[] body(){return body.clone();}@Override public JsonNode pins(){return pins.deepCopy();}
    }
}
