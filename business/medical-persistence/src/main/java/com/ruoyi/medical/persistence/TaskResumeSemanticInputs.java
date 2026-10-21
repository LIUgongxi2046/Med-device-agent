package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;

/** Explicit transport identity normalization; every other semantic field remains exact. */
final class TaskResumeSemanticInputs {
    private TaskResumeSemanticInputs(){}
    static void require(JsonNode original,JsonNode successor,String stage){
        var before=normalize(original,stage);var after=normalize(successor,stage);
        if(!FormalTaskNodeAuthority.sameEvidence(before,after))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static JsonNode normalize(JsonNode input,String stage){
        ObjectNode n=((ObjectNode)input).deepCopy();
        n.remove(List.of("run_id","step_id","input_id","graph_id","graph_sha256","replay_sha256"));
        if("N04".equals(stage)||"N03".equals(stage))for(var predecessor:n.required("predecessors"))((ObjectNode)predecessor).remove("step_id");
        // Rule revision IDs are run-local, but the full verified rule member list is not.
        if("N05".equals(stage))n.remove(List.of("rule_revision","rule_manifest_sha256"));
        return n;
    }
}
