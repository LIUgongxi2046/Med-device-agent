package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source-level regression cases; no execution in the current work session. */
class TaskCandidateAssemblyResumeReaderTest {
    @Test void onlyRoutingAndIndependentlyCheckedRuleIdentityNormalize()throws Exception {
        var json=new JsonMapper();var original=json.readTree("{\"run_id\":\"old\",\"rule_revision\":\"old-rule-set\",\"rule_manifest_sha256\":\"old-hash\",\"baseline_sha256\":\"baseline\",\"candidates\":[{\"candidate_id\":\"actual\",\"review_receipt_sha256\":\"actual-review\"}],\"cross_file_inputs\":{\"physical_reads\":[{\"read_digest\":\"actual-read\"}]}}");
        var current=(ObjectNode)original.deepCopy();current.put("run_id","new").put("rule_revision","new-rule-set").put("rule_manifest_sha256","new-hash");
        assertEquals(TaskCandidateAssemblyResumeReader.semanticInput(original),TaskCandidateAssemblyResumeReader.semanticInput(current));
        ((ObjectNode)current.required("candidates").get(0)).put("review_receipt_sha256","replaced");
        assertNotEquals(TaskCandidateAssemblyResumeReader.semanticInput(original),TaskCandidateAssemblyResumeReader.semanticInput(current));
    }
    @Test void changedExternalPhysicalReadCannotNormalizeAway()throws Exception {
        var original=new JsonMapper().readTree("{\"cross_file_inputs\":{\"physical_reads\":[{\"read_digest\":\"original\"}]}}");
        var current=(ObjectNode)original.deepCopy();((ObjectNode)current.required("cross_file_inputs").required("physical_reads").get(0)).put("read_digest","changed");
        assertNotEquals(TaskCandidateAssemblyResumeReader.semanticInput(original),TaskCandidateAssemblyResumeReader.semanticInput(current));
    }
}
