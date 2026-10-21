package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Pure DC03 body construction. This grants neither consent nor write authority.
 * Selection is a set of existing non-document subtree roots, not arbitrary model JSON.
 * Both inputs must separately come from complete, authorized physical reads. */
final class DocumentCandidateAdoptionMerge {
    private DocumentCandidateAdoptionMerge() { }
    static JsonNode merge(SchemaCatalogue schemas, JsonNode base, JsonNode candidate,
            Collection<String> selectedRoots, Collection<String> candidateTargets,
            DocumentBodySemantics.Limits limits) throws java.io.IOException {
        return merge(schemas,base,candidate,selectedRoots,candidateTargets,limits,schemas.schemaRef("interfaces","DocumentBody"),schemas.schemaDigest("interfaces"));
    }
    static JsonNode merge(SchemaCatalogue schemas,JsonNode base,JsonNode candidate,Collection<String> selectedRoots,Collection<String> candidateTargets,DocumentBodySemantics.Limits limits,String schemaRef,String schemaSha)throws java.io.IOException {
        base=DocumentBodySemantics.verify(schemas,base,limits,schemaRef,schemaSha);
        candidate=DocumentBodySemantics.verify(schemas,candidate,limits,schemaRef,schemaSha);
        if(!base.required("template_version").equals(candidate.required("template_version"))) invalid();
        var old=index(base.required("nodes"),"node_id");var proposed=index(candidate.required("nodes"),"node_id");
        var roots=new LinkedHashSet<>(selectedRoots);var allowed=new HashSet<>(candidateTargets);
        if(roots.isEmpty()||roots.size()!=selectedRoots.size()||!allowed.containsAll(roots))invalid();
        for(String root:roots){
            WireValues.uuid(root);var a=old.get(root);var b=proposed.get(root);
            if(a==null||b==null||a.required("parent_id").isNull()||b.required("parent_id").isNull()
                    ||!a.required("parent_id").equals(b.required("parent_id"))
                    ||!a.required("ordinal").equals(b.required("ordinal")))invalid();
            // Selecting overlapping roots makes scope/confirmation ambiguous.
            for(String parent=parent(a);parent!=null;parent=parent(old.get(parent)))if(roots.contains(parent))invalid();
        }
        Set<String> removed=closure(old,roots), inserted=closure(proposed,roots);
        for(String id:inserted)if(old.containsKey(id)&&!removed.contains(id))invalid();
        var result=(ObjectNode)base.deepCopy();var nodes=result.putArray("nodes");
        // Unselected node values are copied exactly; no reparenting or ordinal normalization.
        for(var n:base.required("nodes"))if(!removed.contains(text(n,"node_id")))nodes.add(n.deepCopy());
        for(var n:candidate.required("nodes"))if(inserted.contains(text(n,"node_id")))nodes.add(n.deepCopy());
        var fields=result.putArray("fields");var fieldIds=new HashSet<String>();var keptFields=new HashSet<String>();var newFields=new HashSet<String>();
        for(var f:base.required("fields"))if(!removed.contains(text(f,"node_id"))){add(fields,fieldIds,f,"field_id");keptFields.add(text(f,"field_id"));}
        for(var f:candidate.required("fields"))if(inserted.contains(text(f,"node_id"))){add(fields,fieldIds,f,"field_id");newFields.add(text(f,"field_id"));}
        var links=new LinkedHashMap<String,JsonNode>();
        for(var l:base.required("evidence_links"))link(links,l,old.keySet(),removed);
        for(var l:candidate.required("evidence_links"))link(links,l,inserted,Set.of());
        var outLinks=result.putArray("evidence_links");links.values().forEach(outLinks::add);
        var facts=new LinkedHashMap<String,ObjectNode>();
        for(var f:base.required("fact_usages"))fact(facts,f,difference(old.keySet(),removed),keptFields);
        for(var f:candidate.required("fact_usages"))fact(facts,f,inserted,newFields);
        var outFacts=result.putArray("fact_usages");facts.values().forEach(outFacts::add);
        return DocumentBodySemantics.verify(schemas,result,limits,schemaRef,schemaSha);
    }
    private static void link(Map<String,JsonNode> out,JsonNode l,Set<String> allowed,Set<String> removed){
        var copy=(ObjectNode)l.deepCopy();var nodes=copy.putArray("node_ids");
        for(var n:l.required("node_ids"))if(allowed.contains(n.asText())&&!removed.contains(n.asText()))nodes.add(n.deepCopy());
        if(nodes.isEmpty())return;
        String id=text(l,"evidence_link_id");var prior=out.get(id);
        if(prior==null){out.put(id,copy);return;}
        var a=(ObjectNode)prior.deepCopy();var b=copy.deepCopy();a.remove("node_ids");b.remove("node_ids");
        if(!a.equals(b))invalid();
        var merged=(ArrayNode)prior.required("node_ids");var seen=new HashSet<String>();merged.forEach(n->seen.add(n.asText()));
        for(var n:nodes)if(seen.add(n.asText()))merged.add(n.deepCopy());
    }
    private static void fact(Map<String,ObjectNode> out,JsonNode f,Set<String> nodes,Set<String> fields){
        var ns=new TreeSet<String>();var fs=new TreeSet<String>();
        for(var n:f.required("node_ids"))if(nodes.contains(n.asText()))ns.add(n.asText());
        for(var n:f.required("field_ids"))if(fields.contains(n.asText()))fs.add(n.asText());
        if(ns.isEmpty()&&fs.isEmpty())return;
        String id=text(f,"fact_version_id");var value=out.computeIfAbsent(id,k->JsonNodeFactory.instance.objectNode().put("fact_version_id",id));
        if(value.has("node_ids"))value.required("node_ids").forEach(n->ns.add(n.asText()));
        if(value.has("field_ids"))value.required("field_ids").forEach(n->fs.add(n.asText()));
        var n=value.putArray("node_ids");ns.forEach(n::add);var v=value.putArray("field_ids");fs.forEach(v::add);
    }
    private static Map<String,JsonNode> index(JsonNode array,String key){var result=new LinkedHashMap<String,JsonNode>();for(var n:array)if(result.put(text(n,key),n)!=null)invalid();return result;}
    private static Set<String> closure(Map<String,JsonNode> nodes,Set<String> roots){var result=new HashSet<>(roots);boolean changed;do{changed=false;for(var n:nodes.values())if(result.contains(parent(n)))changed|=result.add(text(n,"node_id"));}while(changed);return result;}
    private static Set<String> difference(Set<String> all,Set<String> removed){var result=new HashSet<>(all);result.removeAll(removed);return result;}
    private static void add(ArrayNode out,Set<String> seen,JsonNode value,String key){if(!seen.add(text(value,key)))invalid();out.add(value.deepCopy());}
    private static String parent(JsonNode n){return n==null||n.required("parent_id").isNull()?null:text(n,"parent_id");}
    private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static void invalid(){throw new TenantAccessException(ErrorCode.CANDIDATE_STALE);}
}
