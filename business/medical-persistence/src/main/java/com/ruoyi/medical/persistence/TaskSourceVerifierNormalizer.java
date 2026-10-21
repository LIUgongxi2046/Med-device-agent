package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent review normalization. Source availability is not a semantic support decision.
 * Deterministic position/access failures cannot be overridden by a model. Missing checks are
 * retained as explicit insufficient/blocked findings, never silently omitted from coverage. */
public final class TaskSourceVerifierNormalizer {
    private final TaskSourceVerifierSchema json;
    public TaskSourceVerifierNormalizer(TaskSourceVerifierSchema json){this.json=Objects.requireNonNull(json);}

    Normalized normalize(Fixed fixed,String actionId,JsonNode projection)throws Exception {
        return normalizeTarget(fixed,actionId,projection,ArtifactProtocol.TargetKind.CANDIDATE,null,null);
    }
    /** N07 uses the same conservative coverage algorithm, but never re-labels candidate output. */
    Normalized normalizeDocument(Fixed fixed,String actionId,JsonNode projection)throws Exception {
        return normalizeTarget(fixed,actionId,projection,ArtifactProtocol.TargetKind.DOCUMENT,null,null);
    }
    Normalized normalize(Fixed fixed,String actionId,JsonNode projection,String ref,String sha)throws Exception {return normalizeTarget(fixed,actionId,projection,ArtifactProtocol.TargetKind.CANDIDATE,ref,sha);}
    Normalized normalizeDocument(Fixed fixed,String actionId,JsonNode projection,String ref,String sha)throws Exception {return normalizeTarget(fixed,actionId,projection,ArtifactProtocol.TargetKind.DOCUMENT,ref,sha);}
    private Normalized normalizeTarget(Fixed fixed,String actionId,JsonNode projection,ArtifactProtocol.TargetKind targetKind,String ref,String sha)throws Exception {
        WireValues.uuid(actionId);var model=ref==null?json.findings(projection):json.findings(projection,ref,sha);
        if(model.targetKind()!=targetKind||!fixed.targetId.equals(model.targetRevision())
            ||!fixed.bodySha256.equals(model.targetSha256())||!fixed.ruleRevision.equals(model.ruleRevision())
            ||!fixed.claims.keySet().equals(new LinkedHashSet<>(model.requiredClaimIds()))
            ||!fixed.rules.keySet().equals(new LinkedHashSet<>(model.requiredRuleIds())))bad();
        var claims=indexClaims(model.claimChecks());var rules=indexRules(model.ruleChecks());
        if(!fixed.claims.keySet().containsAll(claims.keySet())||!fixed.rules.keySet().containsAll(rules.keySet()))bad();
        var checkedClaims=new ArrayList<ArtifactProtocol.ClaimCheck>();var checkedRules=new ArrayList<ArtifactProtocol.RuleCheck>();
        var issues=new LinkedHashMap<String,ArtifactProtocol.IssueProposal>();var coverage=new ArrayList<JsonNode>();
        boolean complete=true;
        for(var entry:fixed.claims.entrySet()){
            var claim=entry.getValue();var check=claims.get(entry.getKey());var reads=fixed.reads.getOrDefault(entry.getKey(),List.of());
            boolean located=anchorValid(claim.anchor(),fixed.body);boolean access=true,sourceLocated=true;
            var refs=new LinkedHashSet<DatasetProtocol.EvidenceRef>();claim.evidenceLinks().forEach(l->refs.add(l.evidenceRef()));
            var actual=new HashSet<DatasetProtocol.EvidenceRef>();var receipts=new ArrayList<String>();
            for(var read:reads){
                if(!fixed.sessionId.equals(read.sessionId)||fixed.writerSessionIds.contains(read.sessionId)||!refs.contains(read.evidence)||!actual.add(read.evidence))bad();
                if(read.access&&read.located&&read.complete){WireValues.uuid(read.receiptId);receipts.add(read.receiptId);}
                access&=read.access;sourceLocated&=read.located&&read.complete;
            }
            if(!actual.equals(refs)){access=false;sourceLocated=false;}
            if(claim.claimKind()!=ArtifactProtocol.ClaimKind.TRANSITION&&refs.isEmpty()){access=false;sourceLocated=false;}
            boolean seen=check!=null;complete&=seen&&access&&located&&sourceLocated;
            ArtifactProtocol.SupportCheck support=seen?check.supportCheck():ArtifactProtocol.SupportCheck.INSUFFICIENT;
            String rationale=seen?check.rationale():"The independent reviewer did not cover this required claim.";
            if(!access||!located||!sourceLocated){support=ArtifactProtocol.SupportCheck.INSUFFICIENT;rationale="Independent source access or exact target/source location is incomplete. "+rationale;}
            if(support==ArtifactProtocol.SupportCheck.NOT_APPLICABLE&&claim.claimKind()!=ArtifactProtocol.ClaimKind.TRANSITION)bad();
            if(seen&&(!fixed.rules.keySet().containsAll(check.ruleIds())||!new HashSet<>(receipts).containsAll(check.readReceiptIds())))bad();
            // A SUPPORTED result is only a separate N05 semantic assessment after exact original reads.
            // Neither writer link relation nor read success is promoted to SUPPORTED here.
            var result=new ArtifactProtocol.ClaimCheck(claim.claimId(),access?ArtifactProtocol.AccessCheck.PASS:ArtifactProtocol.AccessCheck.FAIL,
                !access?ArtifactProtocol.LocatorCheck.BLOCKED:located&&sourceLocated?ArtifactProtocol.LocatorCheck.PASS:ArtifactProtocol.LocatorCheck.FAIL,
                support,seen?check.ruleIds():List.of(),receipts,rationale);
            checkedClaims.add(result);
            coverage.add(json.tree(Map.of("kind","CLAIM","id",claim.claimId(),"reviewed",seen,"originals_complete",access&&sourceLocated,"locator_valid",located,"receipt_ids",receipts)));
            if(support==ArtifactProtocol.SupportCheck.CONTRADICTED||support==ArtifactProtocol.SupportCheck.INSUFFICIENT){
                String rule=seen&&!check.ruleIds().isEmpty()?check.ruleIds().get(0):fixed.rules.keySet().iterator().next();
                String code=support==ArtifactProtocol.SupportCheck.CONTRADICTED?"CONFLICTING_CLAIM":seen?"UNSUPPORTED_CLAIM":"UNCOVERED_CLAIM";
                issue(issues,actionId,code,rule,claim.anchor(),List.of(claim.claimId()),rationale,List.copyOf(refs));
            }
        }
        var nodes=new HashSet<String>();fixed.body.required("nodes").forEach(n->nodes.add(n.required("node_id").textValue()));
        for(var entry:fixed.rules.entrySet()){
            var check=rules.get(entry.getKey());var rule=entry.getValue();boolean read=fixed.readRuleIds.contains(rule.ruleId);boolean seen=check!=null;complete&=seen&&read;
            if(seen&&(!nodes.containsAll(check.targetNodeIds())||!fixed.evidence.containsAll(check.evidenceRefs())))bad();
            var result=!seen||!read?ArtifactProtocol.RuleResult.BLOCKED:check.result();
            if(rule.deterministicResult!=null&&rule.deterministicResult!=ArtifactProtocol.RuleResult.PASS)result=rule.deterministicResult;
            if(targetKind==ArtifactProtocol.TargetKind.DOCUMENT&&result==ArtifactProtocol.RuleResult.NOT_APPLICABLE&&rule.deterministicResult!=ArtifactProtocol.RuleResult.NOT_APPLICABLE)result=ArtifactProtocol.RuleResult.BLOCKED;
            String rationale=!seen?"The independent reviewer did not cover this required rule.":!read?"The independent reviewer did not read the original fixed rule.":check.rationale();
            if(rule.deterministicResult!=null&&rule.deterministicResult!=ArtifactProtocol.RuleResult.PASS)rationale="The host recomputed the fixed completion rule as "+rule.deterministicResult+". "+rationale;
            var targets=seen?check.targetNodeIds():rule.targetNodeIds;
            if(result==ArtifactProtocol.RuleResult.PASS&&(targets.isEmpty()||!new HashSet<>(targets).containsAll(rule.targetNodeIds)))bad();
            checkedRules.add(new ArtifactProtocol.RuleCheck(rule.ruleId,targets,result,seen?check.evidenceRefs():List.of(),rationale));
            coverage.add(json.tree(Map.of("kind","RULE","id",rule.ruleId,"reviewed",seen,"original_read",read)));
            if(result==ArtifactProtocol.RuleResult.FAIL||result==ArtifactProtocol.RuleResult.BLOCKED)
                issue(issues,actionId,seen&&read?"RULE_VIOLATION":"UNCOVERED_RULE",rule.ruleId,rule.anchor,List.of(),rationale,seen?check.evidenceRefs():List.of());
        }
        // The writer may omit ClaimRecords. C05 still checks actual saved text/fields,
        // so an empty or conveniently filtered claim list cannot yield complete coverage.
        for(var node:fixed.body.required("nodes"))if("TEXT".equals(node.required("content").path("kind").asText())){
            String id=node.required("node_id").textValue(),text=node.required("content").required("text").textValue();int length=text.codePointCount(0,text.length());var covered=new java.util.BitSet(length);
            for(var claim:fixed.claims.values())if(claim.anchor() instanceof ArtifactProtocol.TextAnchor a&&a.nodeId().equals(id)&&anchorValid(a,fixed.body))covered.set(Math.toIntExact(a.start()),Math.toIntExact(a.end()));
            boolean gap=false;int cp=0;for(int offset=0;offset<text.length();){int value=text.codePointAt(offset);if(!Character.isWhitespace(value)&&!covered.get(cp))gap=true;offset+=Character.charCount(value);cp++;}
            if(gap){complete=false;coverage.add(json.tree(Map.of("kind","TARGET_TEXT","id",id,"reviewed",false)));var anchor=new ArtifactProtocol.TextAnchor("TEXT",id,0,length,WireValues.sha256(text.getBytes(StandardCharsets.UTF_8)));issue(issues,actionId,"UNCOVERED_CONTENT",fixed.rules.keySet().iterator().next(),anchor,List.of(),"Saved text contains content outside all declared claim anchors.",List.of());}
        }
        for(var field:fixed.body.required("fields")){
            String fieldId=field.required("field_id").textValue(),nodeId=field.required("node_id").textValue();boolean covered=false;
            for(var claim:fixed.claims.values())if(claim.anchor() instanceof ArtifactProtocol.StructuredAnchor a&&nodeId.equals(a.nodeId())&&fieldId.equals(a.fieldId())&&anchorValid(a,fixed.body))covered=true;
            if(!covered){complete=false;coverage.add(json.tree(Map.of("kind","TARGET_FIELD","id",fieldId,"reviewed",false)));var anchor=new ArtifactProtocol.StructuredAnchor("STRUCTURED",nodeId,null,fieldId,json.hash(field));issue(issues,actionId,"UNCOVERED_FIELD",fixed.rules.keySet().iterator().next(),anchor,List.of(),"Saved structured field has no exact claim coverage.",List.of());}
        }
        for(var node:fixed.body.required("nodes")){
            String kind=node.required("content").path("kind").asText(),id=node.required("node_id").textValue();
            if(!Set.of("TABLE","IMAGE","FORMULA","ATTACHMENT").contains(kind))continue;
            boolean covered=false;var rows=new HashSet<String>();
            for(var claim:fixed.claims.values())if(claim.anchor() instanceof ArtifactProtocol.StructuredAnchor a&&id.equals(a.nodeId())&&anchorValid(a,fixed.body)){
                if(a.rowId()==null&&a.fieldId()==null)covered=true;else if(a.rowId()!=null)rows.add(a.rowId());
            }
            if(!covered&&"TABLE".equals(kind)){var expected=new HashSet<String>();node.required("content").required("row_ids").forEach(r->expected.add(r.asText()));covered=!expected.isEmpty()&&rows.containsAll(expected);}
            if(!covered){complete=false;coverage.add(json.tree(Map.of("kind","TARGET_NODE","id",id,"reviewed",false)));var anchor=new ArtifactProtocol.StructuredAnchor("STRUCTURED",id,null,null,json.hash(node));issue(issues,actionId,"UNCOVERED_CONTENT",fixed.rules.keySet().iterator().next(),anchor,List.of(),"Saved structured content is not fully covered by exact claim anchors.",List.of());}
        }
        if(targetKind==ArtifactProtocol.TargetKind.DOCUMENT){
            boolean substantive=!fixed.body.required("fields").isEmpty();
            for(var node:fixed.body.required("nodes")){var content=node.required("content");String kind=content.path("kind").asText();if("TEXT".equals(kind)&&!content.path("text").asText().isBlank()||Set.of("IMAGE","FORMULA","TABLE","ATTACHMENT").contains(kind))substantive=true;}
            if(!substantive){complete=false;var rule=fixed.rules.values().iterator().next();issue(issues,actionId,"EMPTY_SAVED_DOCUMENT",rule.ruleId,rule.anchor,List.of(),"The saved revision has no substantive content to professionally review.",List.of());}
        }
        for(var proposed:model.issueProposals()){
            if(!fixed.rules.containsKey(proposed.ruleId())||!fixed.claims.keySet().containsAll(proposed.claimIds())
                ||!fixed.evidence.containsAll(proposed.evidenceRefs())||!anchorValid(proposed.anchor(),fixed.body))bad();
            // Deterministic blocking issues remain present. Semantic proposals cannot lower them.
            String key=derived(actionId,"semantic",proposed.issueKey());
            issues.putIfAbsent(key,new ArtifactProtocol.IssueProposal(key,proposed.code(),"BLOCKING",proposed.ruleId(),proposed.anchor(),proposed.claimIds(),proposed.message(),proposed.evidenceRefs()));
        }
        var output=new ArtifactProtocol.ReviewFindings(targetKind,fixed.targetId,fixed.bodySha256,fixed.ruleRevision,
            List.copyOf(fixed.claims.keySet()),List.copyOf(fixed.rules.keySet()),checkedClaims,checkedRules,List.copyOf(issues.values()),complete);
        if(ref==null)json.findings(json.tree(output));else json.findings(json.tree(output),ref,sha);
        var pins=json.tree(Map.of("implementation",targetKind==ArtifactProtocol.TargetKind.DOCUMENT?"N07_INDEPENDENT_REVIEW_V1":"N05_INDEPENDENT_REVIEW_V1","session_id",fixed.sessionId,"target_revision",fixed.targetId,
            "target_sha256",fixed.bodySha256,"rule_revision",fixed.ruleRevision,"projection_sha256",json.hash(projection),"coverage",coverage,"coverage_complete",complete));
        return new Normalized(output,pins);
    }
    private void issue(Map<String,ArtifactProtocol.IssueProposal> issues,String action,String code,String rule,ArtifactProtocol.Anchor anchor,List<String> claims,String message,List<DatasetProtocol.EvidenceRef> refs){
        String key=derived(action,code,rule+"/"+String.join("/",claims)+"/"+anchor.toString());issues.put(key,new ArtifactProtocol.IssueProposal(key,code,"BLOCKING",rule,anchor,claims,message,refs));
    }
    boolean anchorValid(ArtifactProtocol.Anchor anchor,JsonNode body)throws Exception{
        var nodes=new HashMap<String,JsonNode>();for(var n:body.required("nodes"))if(nodes.put(n.required("node_id").textValue(),n)!=null)bad();
        if(anchor instanceof ArtifactProtocol.TextAnchor a){var n=nodes.get(a.nodeId());if(n==null||!"TEXT".equals(n.required("content").path("kind").asText()))return false;
            String text=n.required("content").required("text").textValue();int length=text.codePointCount(0,text.length());if(a.start()<0||a.end()<=a.start()||a.end()>length)return false;
            String slice=text.substring(text.offsetByCodePoints(0,Math.toIntExact(a.start())),text.offsetByCodePoints(0,Math.toIntExact(a.end())));return WireValues.sha256(slice.getBytes(StandardCharsets.UTF_8)).equals(a.textSha256());}
        if(anchor instanceof ArtifactProtocol.StructuredAnchor a){var n=nodes.get(a.nodeId());if(n==null)return false;JsonNode selected=n;
            if(a.fieldId()!=null){if(a.rowId()!=null)return false;selected=null;for(var f:body.required("fields"))if(a.fieldId().equals(f.required("field_id").textValue())&&a.nodeId().equals(f.required("node_id").textValue())){if(selected!=null)bad();selected=f;}if(selected==null)return false;}
            else if(a.rowId()!=null){var c=n.required("content");if(!"TABLE".equals(c.path("kind").asText()))return false;var row=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();for(var cell:c.required("cells"))if(a.rowId().equals(cell.required("row_id").textValue()))row.add(cell);if(row.isEmpty())return false;selected=row;}
            return json.hash(selected).equals(a.contentSha256());}
        return false;
    }
    private static Map<String,ArtifactProtocol.ClaimCheck> indexClaims(List<ArtifactProtocol.ClaimCheck> values){var out=new LinkedHashMap<String,ArtifactProtocol.ClaimCheck>();for(var v:values)if(out.putIfAbsent(v.claimId(),v)!=null)bad();return out;}
    private static Map<String,ArtifactProtocol.RuleCheck> indexRules(List<ArtifactProtocol.RuleCheck> values){var out=new LinkedHashMap<String,ArtifactProtocol.RuleCheck>();for(var v:values)if(out.putIfAbsent(v.ruleId(),v)!=null)bad();return out;}
    private static String derived(String action,String kind,String key){return "review_"+UUID.nameUUIDFromBytes(("N05/"+action+"/"+kind+"/"+key).getBytes(StandardCharsets.UTF_8)).toString();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    record Read(String sessionId,String receiptId,DatasetProtocol.EvidenceRef evidence,boolean access,boolean located,boolean complete){}
    record Rule(String ruleId,List<String> targetNodeIds,ArtifactProtocol.Anchor anchor,ArtifactProtocol.RuleResult deterministicResult){Rule(String id,List<String> nodes,ArtifactProtocol.Anchor anchor){this(id,nodes,anchor,null);}Rule{targetNodeIds=List.copyOf(targetNodeIds);}}
    record Fixed(String targetId,String bodySha256,String ruleRevision,String sessionId,Set<String> writerSessionIds,JsonNode body,
            Map<String,ArtifactProtocol.ClaimRecord> claims,Map<String,Rule> rules,Map<String,List<Read>> reads,Set<String> readRuleIds,Set<DatasetProtocol.EvidenceRef> evidence){
        Fixed{WireValues.uuid(targetId);WireValues.hash(bodySha256);WireValues.uuid(ruleRevision);WireValues.uuid(sessionId);writerSessionIds=Set.copyOf(writerSessionIds);body=body.deepCopy();claims=Collections.unmodifiableMap(new LinkedHashMap<>(claims));rules=Collections.unmodifiableMap(new LinkedHashMap<>(rules));var copied=new LinkedHashMap<String,List<Read>>();reads.forEach((k,v)->copied.put(k,List.copyOf(v)));reads=Map.copyOf(copied);readRuleIds=Set.copyOf(readRuleIds);evidence=Set.copyOf(evidence);if(rules.isEmpty()||writerSessionIds.contains(sessionId))bad();}
        @Override public JsonNode body(){return body.deepCopy();}
    }
    record Normalized(ArtifactProtocol.ReviewFindings findings,JsonNode pins){Normalized{pins=pins.deepCopy();}@Override public JsonNode pins(){return pins.deepCopy();}}
}
