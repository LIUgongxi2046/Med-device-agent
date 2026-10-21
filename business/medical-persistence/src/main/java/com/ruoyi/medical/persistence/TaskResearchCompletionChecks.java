package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;

/** Deterministic checks of explicitly bound completion rules, not a domain-truth classifier.
 * Data returned here is not an execution, human-approval, or producing capability. */
final class TaskResearchCompletionChecks {
    enum Outcome { SATISFIED, NOT_SATISFIED, UNKNOWN }
    record Result(Outcome outcome,List<String> missingInputKeys) {
        Result {missingInputKeys=List.copyOf(missingInputKeys);}
    }
    private TaskResearchCompletionChecks() { }
    static Result evaluate(RuleExpression engine,String ruleId,List<String> confirmedCompletionRuleIds,
            TemplateApplicabilityProtocol.RuleContent approvedRule,List<ProjectProtocol.Attribute> frozenAttributes) {
        if(confirmedCompletionRuleIds.stream().distinct().count()!=confirmedCompletionRuleIds.size()
                ||!confirmedCompletionRuleIds.contains(ruleId))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        // The explicit confirmed completion binding, not mere use as an applicability rule,
        // supplies the meaning of this Boolean. The underlying safe evaluator is unchanged.
        var result=engine.evaluate(engine.compile(approvedRule.expression()),frozenAttributes);
        return switch(result.status()) {
            case "APPLICABLE"->new Result(Outcome.SATISFIED,List.of());
            case "NOT_APPLICABLE"->new Result(Outcome.NOT_SATISFIED,List.of());
            case "UNKNOWN"->new Result(Outcome.UNKNOWN,result.unknown_input_keys());
            default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        };
    }
    static JsonNode candidateProjection(JsonNode bundle) {
        var copy=bundle.deepCopy();
        if(!copy.isObject()||!copy.path("question_coverage").isArray()||copy.required("question_coverage").size()!=1)
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var question=copy.required("question_coverage").get(0);
        if(!question.isObject())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        ((com.fasterxml.jackson.databind.node.ObjectNode)question).remove(List.of("status","completion_check_ids"));
        return copy;
    }
    static List<String> blockingCoverage(JsonNode candidate,List<String> confirmedRequirements) {
        if(new HashSet<>(confirmedRequirements).size()!=confirmedRequirements.size())bad();
        var requirements=new HashSet<>(confirmedRequirements);var missing=new TreeSet<String>();
        for(var values:List.of(candidate.required("missing_requirement_ids"),candidate.required("question_coverage").get(0).required("missing_requirement_ids"))) {
            if(!values.isArray())bad();for(var value:values){if(!value.isTextual()||!requirements.contains(value.textValue()))bad();missing.add(value.textValue());}
        }
        // Conflicts and absent references cannot be repaired by a Boolean profile rule.
        if(!candidate.required("conflict_refs").isArray()||!candidate.required("selected_refs").isArray()
                ||!candidate.required("question_coverage").get(0).required("evidence_links").isArray())bad();
        if(!candidate.required("conflict_refs").isEmpty())missing.add("UNRESOLVED_EVIDENCE_CONFLICT");
        if(candidate.required("selected_refs").isEmpty()||candidate.required("question_coverage").get(0).required("evidence_links").isEmpty())missing.add("EVIDENCE_REQUIRED");
        return List.copyOf(missing);
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
