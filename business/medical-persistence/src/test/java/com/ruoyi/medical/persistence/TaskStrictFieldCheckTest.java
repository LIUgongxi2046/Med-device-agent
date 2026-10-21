package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.StrictComparisonPolicyDefinition.*;
import static com.ruoyi.medical.persistence.StrictFieldComparisonEngineTest.*;

/** Contract regressions, not assertions of runtime approval or professional acceptance. */
class TaskStrictFieldCheckTest {
    private final StrictFieldComparisonEngineTest fixture=new StrictFieldComparisonEngineTest();
    private final Version implementation=new Version(id(900),HASH);
    private final TaskStrictFieldCheck checker=new TaskStrictFieldCheck(schema,implementation);
    TaskStrictFieldCheckTest()throws Exception{}
    private ObjectNode target(){var t=fixture.target("literal");entry(t).put("model_context","KNOWN_GLOBAL").putArray("model_ids");entry(t).put("condition_context","KNOWN_GLOBAL").putArray("condition_ids");return t;}
    private ObjectNode request(ObjectNode target,String value)throws Exception {
        var r=fixture.request(target,value);req(r).withObject("rule").put("rule_revision",id(901)).put("rule_id",id(901)).put("rule_sha256",HASH);
        obs(r).put("model_context","KNOWN_GLOBAL").putArray("model_ids");obs(r).put("condition_context","KNOWN_GLOBAL").putArray("condition_ids");return r;
    }
    private TaskStrictFieldCheck.Policy policy(ObjectNode target){
        var combinations=List.of(new Combination(entry(target).required("field_definition_id").textValue(),new Context(ContextMode.GLOBAL,List.of()),new Context(ContextMode.GLOBAL,List.of())));
        return new TaskStrictFieldCheck.Policy(id(901),HASH,new Definition("1.0.0",Operator.TEXT_EXACT,new Precision(PrecisionMode.NOT_APPLICABLE,null),new Unit(UnitMode.EXACT_DECLARED,null),
            new ApplicabilityScope(Applicability.APPLICABLE,null,new Version(id(902),HASH)),CoverageMode.EXACT_DECLARED_COMBINATIONS,combinations,implementation,List.of(new Version(id(903),HASH))));
    }
    @Test void fullEqualCoverageMeansCheckedNotProfessionalPass()throws Exception {
        var t=target();var r=request(t,"literal");var evaluated=checker.evaluate(schema.target(t),schema.request(r),List.of(policy(t)),LIMITS);
        assertEquals(TaskStrictFieldCheck.Disposition.CHECKED,evaluated.disposition());assertEquals(ArtifactProtocol.ComparisonResult.MATCH,evaluated.comparison().entries().get(0).result());
        assertEquals("COMPUTATION_ONLY",evaluated.comparison().assessment());assertTrue(evaluated.waitingEntries().isEmpty());
    }
    @Test void realConflictIsRetainedNotConvertedToFailedTransport()throws Exception {
        var t=target();var evaluated=checker.evaluate(schema.target(t),schema.request(request(t,"other")),List.of(policy(t)),LIMITS);
        assertEquals(TaskStrictFieldCheck.Disposition.CHECKED,evaluated.disposition());assertEquals(ArtifactProtocol.ComparisonResult.CONFLICT,evaluated.comparison().entries().get(0).result());
    }
    @Test void missingSourceWaitsWithoutSyntheticObservation()throws Exception {
        var t=target();var r=request(t,"literal");req(r).putNull("source_observation");var result=checker.evaluate(schema.target(t),schema.request(r),List.of(policy(t)),LIMITS);
        assertEquals(TaskStrictFieldCheck.Disposition.WAITING_INPUT,result.disposition());assertNull(result.comparison().entries().get(0).comparison());
    }
    @Test void approvedPolicyHashOperatorAndIdentityCannotBeSwapped()throws Exception {
        var t=target();for(String field:List.of("rule_revision","rule_id","rule_sha256","operator")){
            var r=request(t,"literal");req(r).withObject("rule").put(field,field.equals("operator")?"DECIMAL_EXACT":field.equals("rule_sha256")?"b".repeat(64):id(999));
            assertThrows(RuntimeException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(policy(t)),LIMITS));}
    }
    @Test void policyDeclaredUnitIsNotInferredFromEqualOperands()throws Exception {
        var t=target();var r=request(t,"literal");entry(t).withObject("candidate_value").put("unit","mm");obs(r).withObject("value").put("unit","mm");
        assertThrows(RuntimeException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(policy(t)),LIMITS));
    }
    @Test void missingAndExtraPoliciesCannotSilentlyFilterEntries()throws Exception {
        var t=target();var r=request(t,"literal");assertThrows(RuntimeException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(),LIMITS));
        var original=policy(t);var extra=new TaskStrictFieldCheck.Policy(id(999),HASH,original.definition());
        assertThrows(RuntimeException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(original,extra),LIMITS));
    }
    @Test void implementationAndIndependentCoverageRemainMandatory()throws Exception {
        var t=target();var r=request(t,"literal");var original=policy(t).definition();
        var changed=new Definition(original.schemaVersion(),original.operator(),original.precision(),original.unit(),original.applicability(),original.coverageMode(),original.coverage(),new Version(id(904),HASH),original.basis());
        assertThrows(IllegalArgumentException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(new TaskStrictFieldCheck.Policy(id(901),HASH,changed)),LIMITS));
    }
    @Test void entryOrderDoesNotPermitOmission()throws Exception {
        var t=target();t.withArray("entries").add(entry(t).deepCopy().put("entry_id",id(80)).put("field_id",id(81)));
        var r=request(t,"literal");assertThrows(RuntimeException.class,()->checker.evaluate(schema.target(t),schema.request(r),List.of(policy(t)),LIMITS));
    }
}
