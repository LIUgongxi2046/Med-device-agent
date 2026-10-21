package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: authored but not executed under the current skip-tests instruction. */
class TaskResumeSemanticInputsTest {
    private final JsonMapper json=new JsonMapper();
    @Test void writerRoutingMayChangeButOriginalEvidenceCannot()throws Exception {
        var old=json.readTree("{\"run_id\":\"old\",\"step_id\":\"old-step\",\"field_check_receipt_sha256\":\"actual-field-proof\",\"predecessors\":[{\"step_id\":\"old-research\",\"result_artifact_id\":\"original-result\",\"completion_receipt_sha256\":\"original-commit\"}]}");
        var next=old.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)next).put("run_id","successor").put("step_id","successor-step");
        ((com.fasterxml.jackson.databind.node.ObjectNode)next.required("predecessors").get(0)).put("step_id","successor-research");
        assertDoesNotThrow(()->TaskResumeSemanticInputs.require(old,next,"N04"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)next.required("predecessors").get(0)).put("completion_receipt_sha256","copied-or-replaced");
        assertThrows(TenantAccessException.class,()->TaskResumeSemanticInputs.require(old,next,"N04"));
    }
    @Test void verifierRequiresExactCandidateAndRules()throws Exception {
        var old=json.readTree("{\"candidate_id\":\"original\",\"body_object\":{\"sha256\":\"body\"},\"rule_revision\":\"old-local\",\"rule_manifest_sha256\":\"old-manifest\",\"required_rules\":[{\"version_id\":\"approved\",\"content_sha256\":\"rule\"}]}");
        var next=(com.fasterxml.jackson.databind.node.ObjectNode)old.deepCopy();next.put("rule_revision","new-local").put("rule_manifest_sha256","new-manifest");
        assertDoesNotThrow(()->TaskResumeSemanticInputs.require(old,next,"N05"));next.put("candidate_id","replacement");
        assertThrows(TenantAccessException.class,()->TaskResumeSemanticInputs.require(old,next,"N05"));
    }
    @Test void strictFieldTargetAndPolicyRemainExact()throws Exception {
        var old=json.readTree("{\"target_artifact_id\":\"original-target\",\"policies\":[{\"entry_id\":\"entry\",\"policy\":{\"version_id\":\"approved\"}}],\"predecessors\":[{\"step_id\":\"prior\",\"result_artifact_id\":\"original-result\"}]}");
        var next=(com.fasterxml.jackson.databind.node.ObjectNode)old.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)next.required("predecessors").get(0)).put("step_id","successor");
        assertDoesNotThrow(()->TaskResumeSemanticInputs.require(old,next,"N03"));next.put("target_artifact_id","replacement");
        assertThrows(TenantAccessException.class,()->TaskResumeSemanticInputs.require(old,next,"N03"));
    }
    @Test void writerCannotDiscardChangedFieldOutput()throws Exception {
        var old=json.readTree("{\"field_check_receipt_sha256\":\"original\",\"predecessors\":[]}");
        var next=(com.fasterxml.jackson.databind.node.ObjectNode)old.deepCopy();next.put("field_check_receipt_sha256","new");
        assertThrows(TenantAccessException.class,()->TaskResumeSemanticInputs.require(old,next,"N04"));
    }
}
