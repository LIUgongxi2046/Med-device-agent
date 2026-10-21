package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskResearchCompletionChecksTest {
 private final RuleExpression engine=new RuleExpression();private final ObjectMapper json=new ObjectMapper();
 private TemplateApplicabilityProtocol.RuleContent rule(){return new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,
  new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"#attributes['ready'] == true",List.of("ready"),List.of()),List.of());}
 @Test void exactConfirmedCompletionBindingIsMandatory(){assertThrows(TenantAccessException.class,()->TaskResearchCompletionChecks.evaluate(engine,"r",List.of("other"),rule(),List.of()));}
 @Test void missingFrozenAttributeStaysUnknown(){var r=TaskResearchCompletionChecks.evaluate(engine,"r",List.of("r"),rule(),List.of());assertEquals(TaskResearchCompletionChecks.Outcome.UNKNOWN,r.outcome());assertEquals(List.of("ready"),r.missingInputKeys());}
 @Test void explicitUnknownCannotBeConvertedToFalse(){var a=new ProjectProtocol.Attribute("ready","UNKNOWN",null,List.of(),"not established");assertEquals(TaskResearchCompletionChecks.Outcome.UNKNOWN,TaskResearchCompletionChecks.evaluate(engine,"r",List.of("r"),rule(),List.of(a)).outcome());}
 @Test void trueMeansOnlyDeclaredCheckSatisfied(){var a=new ProjectProtocol.Attribute("ready","KNOWN",json.valueToTree(true),List.of(),null);assertEquals(TaskResearchCompletionChecks.Outcome.SATISFIED,TaskResearchCompletionChecks.evaluate(engine,"r",List.of("r"),rule(),List.of(a)).outcome());}
 @Test void falseDoesNotPass(){var a=new ProjectProtocol.Attribute("ready","KNOWN",json.valueToTree(false),List.of(),null);assertEquals(TaskResearchCompletionChecks.Outcome.NOT_SATISFIED,TaskResearchCompletionChecks.evaluate(engine,"r",List.of("r"),rule(),List.of(a)).outcome());}
 @Test void duplicateConfirmedRulesAreRejected(){assertThrows(TenantAccessException.class,()->TaskResearchCompletionChecks.evaluate(engine,"r",List.of("r","r"),rule(),List.of()));}
 @Test void projectionExcludesOnlyDerivedStatusAndChecks()throws Exception{var b=json.readTree("{\"retrieval_ids\":[],\"question_coverage\":[{\"question_id\":\"q\",\"status\":\"VERIFIED\",\"completion_check_ids\":[\"c\"],\"evidence_links\":[],\"missing_requirement_ids\":[]}],\"selected_refs\":[],\"conflict_refs\":[],\"missing_requirement_ids\":[],\"read_receipt_ids\":[]}");var p=TaskResearchCompletionChecks.candidateProjection(b);assertFalse(p.required("question_coverage").get(0).has("status"));assertTrue(b.required("question_coverage").get(0).has("status"));assertEquals(List.of("EVIDENCE_REQUIRED"),TaskResearchCompletionChecks.blockingCoverage(p,List.of()));}
}
