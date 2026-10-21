package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Projection never converts UNKNOWN or absent evidence to professional PASS. */
final class TaskStrictFieldOutputProjection {
    static ArtifactProtocol.FieldCheckResult project(TaskStrictFieldSchema json,TaskStrictFieldInput input,JsonNode body,String rule,JsonNode replay)throws Exception {
        var target=json.target(input);var request=new StrictFieldComparisonSchema().request(body.required("request"));var entries=new ArrayList<ArtifactProtocol.FieldComparison>();var missing=new TreeSet<String>();var evaluated=new HashMap<String,JsonNode>();var comparison=body.required("evaluation").required("comparison");if(!comparison.isNull())for(var e:comparison.required("entries"))evaluated.put(e.required("entry_id").asText(),e);
        for(var fixed:target.entries()){
            var asked=request.entries().stream().filter(e->e.entryId().equals(fixed.entryId())).findFirst().orElseThrow();var result=evaluated.get(fixed.entryId());
            if(result!=null&&!result.required("comparison").isNull())entries.add(json.bind(result.required("comparison"),ArtifactProtocol.FieldComparison.class));
            else {var source=asked.sourceObservation();var value=source==null?new ArtifactProtocol.FieldValue(ArtifactProtocol.ValueKind.EMPTY,null,null,null,null,List.of(),null):source.value();var evidence=source==null||source.provenance()==null||source.provenance().source()==null?List.<DatasetProtocol.EvidenceRef>of():List.of(source.provenance().source().evidenceRef());entries.add(new ArtifactProtocol.FieldComparison(fixed.fieldId(),fixed.rowId(),fixed.modelIds(),fixed.conditionIds(),value,fixed.candidateValue(),asked.rule().ruleId(),null,source==null?ArtifactProtocol.ComparisonResult.MISSING:ArtifactProtocol.ComparisonResult.UNKNOWN_CONDITION,evidence));missing.add(fixed.nodeId());}
        }
        var methods=new ArrayList<ArtifactProtocol.MethodCoverage>();if(TaskStrictFieldTypedProtocol.typed(input.targetManifest().path("schema_ref").asText())){for(var m:input.targetManifest().required("method_coverage"))methods.add(json.bind(m,ArtifactProtocol.MethodCoverage.class));for(var id:input.targetManifest().required("missing_requirement_ids"))missing.add(id.asText());}else for(var id:replay.required("plan").required("content").required("requirement_node_ids"))missing.add(id.asText());
        for(var id:body.required("evaluation").required("waiting_entries")){var entry=target.entries().stream().filter(e->e.entryId().equals(id.asText())).findFirst().orElseThrow();missing.add(entry.nodeId());}
        return new ArtifactProtocol.FieldCheckResult(input.targetArtifactId(),rule,entries,methods,List.copyOf(missing));
    }
}
