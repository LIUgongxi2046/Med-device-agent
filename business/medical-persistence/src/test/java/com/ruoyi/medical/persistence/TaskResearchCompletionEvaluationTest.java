package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual evaluator and audit/outbox persistence on explicit JDBC/current candidate/rule
 * authority doubles. It never claims a model, provider, source or live DB was executed. */
class TaskResearchCompletionEvaluationTest extends TaskNodeInputFixture {
    final TaskResearchExecutionService.Candidate candidate=mock(TaskResearchExecutionService.Candidate.class);
    final TaskProfessionalRuntimeService.Session session=mock(TaskProfessionalRuntimeService.Session.class);
    final TaskNodeInputAuthority.Verified originalInput=mock(TaskNodeInputAuthority.Verified.class);
    final TaskResearchSourceReader.VerifiedReceipts reads=mock(TaskResearchSourceReader.VerifiedReceipts.class);
    final TaskResearchRuleInputs inputs=mock(TaskResearchRuleInputs.class);
    final TaskResearchRuleInputs.Verified fixed=mock(TaskResearchRuleInputs.Verified.class);
    final TaskResearchWaitingService waiting=mock(TaskResearchWaitingService.class);
    final RuleExpression engine=new RuleExpression();
    final TaskResearchCompletionEvaluation service=new TaskResearchCompletionEvaluation(db,json,engine,inputs,waiting);
    final String sessionId=id(101),action=id(102),inputId=id(103),ruleId=id(104),waitingId=id(105),owner=id(106);
    final JsonNode projection;
    final TemplateApplicabilityProtocol.RuleContent rule=new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"#attributes['ready'] == true",List.of("ready"),List.of()),List.of());
    List<ProjectProtocol.Attribute> attributes=List.of(new ProjectProtocol.Attribute("ready","KNOWN",json.tree(true),List.of(),null));
    TaskResearchCompletionEvaluation.WaitingCause actualCause;
    TaskResearchCompletionEvaluationTest()throws Exception {
        when(session.admission()).thenReturn(admission);when(session.input()).thenReturn(originalInput);when(session.sessionId()).thenReturn(sessionId);when(session.actionId()).thenReturn(action);when(session.generation()).thenReturn("1");
        when(originalInput.tenantId()).thenReturn(tenant);when(originalInput.projectId()).thenReturn(project);when(originalInput.runId()).thenReturn(run);when(originalInput.stepId()).thenReturn(step);when(originalInput.questionId()).thenReturn(input.questionId());when(originalInput.inputId()).thenReturn(inputId);when(originalInput.inputSha256()).thenReturn(hash('a'));when(originalInput.receiptSha256()).thenReturn(hash('b'));
        var value=json.object();value.putArray("retrieval_ids");value.putArray("selected_refs").addObject().put("explicit_source_authority","ref");value.putArray("conflict_refs");value.putArray("missing_requirement_ids");value.putArray("read_receipt_ids");var question=value.putArray("question_coverage").addObject().put("question_id",input.questionId());question.putArray("evidence_links").addObject().put("explicit_evidence_relation","SUPPORT");question.putArray("missing_requirement_ids");projection=value;
        when(candidate.session()).thenReturn(session);when(candidate.projection()).thenAnswer(i->projection.deepCopy());when(candidate.candidateSha256()).thenAnswer(i->WireValues.sha256(ContentReadAuthorization.canonicalBytes(projection)));when(candidate.sourceReceipts()).thenReturn(reads);when(reads.canonicalPins()).thenReturn(json.object().arrayNode());when(candidate.invocationPins()).thenReturn(json.object().arrayNode());
        when(inputs.require(session)).thenReturn(fixed);when(fixed.attributes()).thenAnswer(i->attributes);when(fixed.requirementIds()).thenReturn(List.of());when(fixed.ownerId()).thenReturn(owner);when(fixed.profileId()).thenReturn(id(107));when(fixed.profileSha256()).thenReturn(hash('c'));when(fixed.witness()).thenReturn(json.object().put("explicit_current_authority","profile-rules"));when(fixed.checks()).thenReturn(List.of(new TaskResearchRuleInputs.Check(ruleId,hash('d'),rule,json.object())));
        when(waiting.create(any())).thenAnswer(i->{actualCause=i.getArgument(0);return waitingId;});
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(i->{String sql=i.getArgument(0);if(sql.startsWith("SELECT classification"))return List.of("RESTRICTED");throw new AssertionError(sql);});
    }
    TaskResearchCompletionEvaluation.Evaluated evaluate()throws Exception{return tx(()->service.evaluate(candidate));}
    @Test void actualEvaluatorWritesExactTypedCheckAndOutboxThenReplaysWithoutDuplicates()throws Exception {
        var evaluated=evaluate();assertEquals("SUCCEEDED",evaluated.outcome());assertEquals(ArtifactProtocol.QuestionStatus.VERIFIED,evaluated.questionStatus());assertNull(evaluated.waitingItemId());assertEquals(1,rows("audit_event").size());assertEquals(1,rows("outbox_event").size());
        var proof=new com.fasterxml.jackson.databind.ObjectMapper().readTree(only("audit_event").get("resource_ref").toString());TaskResearchCompletionSchema.require(proof);assertEquals("SATISFIED",proof.required("outcome").textValue());assertEquals(json.tree(attributes),proof.required("attributes"));
        assertEquals(evaluated.completionCheckIds(),evaluate().completionCheckIds());assertEquals(1,rows("audit_event").size());tx(()->{evaluated.requireUnchanged();return null;});verify(waiting,never()).create(any());
    }
    @Test void unknownAndFalseCreateOnlyGenuineWaitingCauseWithTypedActualOutcome()throws Exception {
        attributes=List.of();var evaluated=evaluate();assertEquals("WAITING_INPUT",evaluated.outcome());assertEquals(ArtifactProtocol.QuestionStatus.INSUFFICIENT,evaluated.questionStatus());assertEquals(List.of("FROZEN_PROFILE_INPUT_UNKNOWN"),actualCause.reasons());assertEquals("UNKNOWN",actualCause.receiptPins().get(0).required("outcome").textValue());
        tx(()->{evaluated.requireUnchanged();return null;});verify(waiting).requireOriginal(any(),eq(waitingId));
        rows("audit_event").clear();rows("outbox_event").clear();attributes=List.of(new ProjectProtocol.Attribute("ready","KNOWN",json.tree(false),List.of(),null));evaluate();assertEquals(List.of("DECLARED_COMPLETION_CHECK_NOT_SATISFIED"),actualCause.reasons());assertEquals("NOT_SATISFIED",actualCause.receiptPins().get(0).required("outcome").textValue());
    }
    @Test void missingDeclaredRuleOrEvidenceIsNotGenericVerified()throws Exception {
        when(fixed.checks()).thenReturn(List.of());var evaluated=evaluate();assertEquals("WAITING_INPUT",evaluated.outcome());assertEquals(List.of("APPROVED_COMPLETION_RULE_REQUIRED"),actualCause.reasons());assertTrue(evaluated.completionCheckIds().isEmpty());assertTrue(rows("audit_event").isEmpty());
        ((ObjectNode)projection).putArray("selected_refs");evaluate();assertTrue(actualCause.reasons().contains("EVIDENCE_REQUIRED"));
    }
    @Test void unresolvedConflictOverridesSatisfiedRuleAndMissingOwnerBlocks()throws Exception {
        ((ObjectNode)projection).withArray("conflict_refs").addObject().put("explicit_source_authority","conflict");var evaluated=evaluate();assertEquals(ArtifactProtocol.QuestionStatus.CONFLICT,evaluated.questionStatus());assertEquals(List.of("UNRESOLVED_EVIDENCE_CONFLICT"),actualCause.reasons());
        when(fixed.ownerId()).thenReturn(null);assertThrows(TenantAccessException.class,this::evaluate);
    }
    @Test void changedCurrentFactsCannotReuseSatisfiedCheckReceipt()throws Exception {
        var evaluated=evaluate();attributes=List.of(new ProjectProtocol.Attribute("ready","KNOWN",json.tree(false),List.of(),null));assertThrows(TenantAccessException.class,()->tx(()->{evaluated.requireUnchanged();return null;}));
    }
    @Test void fullAuditOutboxMetadataAndExactRevisionAreRequired()throws Exception {
        evaluate();only("audit_event").put("classification","PUBLIC");assertThrows(TenantAccessException.class,this::evaluate);only("audit_event").put("classification","RESTRICTED");
        only("outbox_event").put("aggregate_revision",new BigInteger("18446744073709551617"));assertThrows(TenantAccessException.class,this::evaluate);only("outbox_event").put("aggregate_revision",1);only("outbox_event").put("occurred_at",now.plusSeconds(1));assertThrows(TenantAccessException.class,this::evaluate);
    }
    @Test void privateCandidateAndCurrentAuthorityMustStayValid()throws Exception {
        doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(candidate).requireUnchanged();assertThrows(TenantAccessException.class,this::evaluate);assertTrue(rows("audit_event").isEmpty());
    }
}
