package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure contract tests. No database, provider, deployment or independent professional review is asserted. */
class TaskPlanDraftCandidatePolicyTest {
    private final TaskPlanDraftCandidatePolicy policy = new TaskPlanDraftCandidatePolicy(new TaskPlanPolicy(), new PermissionSchema());
    TaskPlanDraftCandidatePolicyTest() throws Exception { }
    static String id(int n) { return new UUID(0, n).toString(); }
    TemplateApplicabilityProtocol.TemplateView view(List<TemplateApplicabilityProtocol.ConditionResult> conditions) {
        var nodes = List.of(node(10), node(11));
        var fields = List.of(new TemplateProtocol.Field(id(20), id(10), "field", "Field", "Required field", "TEXT", null,
                null, true, null, null, id(22), null, List.of()));
        var attachments = List.of(new TemplateProtocol.Attachment(id(21), id(11), "Evidence", "FILE", "REQUIRED", null,
                true, "1", "1", List.of(), List.of(), null, 1));
        var repeats = List.of(new TemplateProtocol.Repeat(id(22), id(10), "Rows", List.of(id(20)), "1", "2", List.of(), true));
        var content = new TemplateProtocol.Content("1.0.0", "REGISTRATION_DIRECTORY", "INITIAL", false, "fixture", nodes,
                fields, repeats, attachments, List.of(), null, List.of(), null);
        var template = new TemplateProtocol.Stored(id(1), id(4), "APPROVED", "1", content, "a".repeat(64), null,
                "Fixture", id(6), "1", id(7), "2026-10-20T00:00:00Z", List.of());
        return new TemplateApplicabilityProtocol.TemplateView(template, "APPLICABLE", conditions, List.of(), "b".repeat(64));
    }
    TemplateProtocol.Node node(int n) { return new TemplateProtocol.Node(id(n), null, Integer.toString(n), "Section " + n,
            n, null, true, "TEXT", null, List.of(), "CONTENT", List.of()); }
    List<TaskPlanDraftCandidatePolicy.SectionProposal> proposals(String owner, List<String> rules) {
        return List.of(new TaskPlanDraftCandidatePolicy.SectionProposal(id(10), "Research", owner, ArtifactProtocol.Method.MODEL_ASSISTED, List.of(), rules),
                new TaskPlanDraftCandidatePolicy.SectionProposal(id(11), "Supply actual evidence", owner, ArtifactProtocol.Method.HUMAN_PROVIDED, List.of(id(10)), rules));
    }
    List<TaskPlanDraftCandidatePolicy.QuestionProposal> questions() {
        return List.of(new TaskPlanDraftCandidatePolicy.QuestionProposal("question_1", id(10), "What evidence supports this section?", List.of(id(30)), List.of()));
    }
    TaskPlanDraftCandidatePolicy.Inspected expand(TemplateApplicabilityProtocol.TemplateView view,
            List<TaskPlanDraftCandidatePolicy.SectionProposal> sections, List<TaskPlanDraftCandidatePolicy.QuestionProposal> questions) throws Exception {
        return policy.expand(id(1), id(2), id(3), id(5), view, sections, questions, Set.of(id(30)));
    }
    @Test void fullDirectoryFieldsAttachmentsAndRepeatsAreTransitivelyCovered() throws Exception {
        var result = expand(view(List.of()), proposals(id(40), List.of(id(30))), questions());
        assertEquals(List.of(id(10), id(11)), result.validation().candidate().requirementNodeIds());
        assertEquals(List.of(id(20)), result.coverage().get(0).fieldIds());
        assertEquals(List.of(id(22)), result.coverage().get(0).repeatIds());
        assertEquals(List.of(id(21)), result.coverage().get(1).attachmentIds());
        assertEquals(List.of("question_1"), result.coverage().get(0).questionKeys());
        assertTrue(result.missing().isEmpty());
        var sections = result.validation().candidate().sections();
        assertEquals(List.of(sections.get(0).sectionId()), sections.get(1).dependsOnSectionIds());
        assertThrows(UnsupportedOperationException.class, () -> result.coverage().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.coverage().get(0).fieldIds().clear());
    }
    @Test void deterministicRecoveryKeepsIdsButDifferentRunCannotReuseThem() throws Exception {
        var view = view(List.of()); var proposal = proposals(id(40), List.of(id(30)));
        var first = expand(view, proposal, questions());
        assertEquals(first, expand(view, proposal, questions()));
        var other = policy.expand(id(1), id(200), id(3), id(5), view, proposal, questions(), Set.of(id(30)));
        assertNotEquals(first.validation().candidate().sections().get(0).sectionId(), other.validation().candidate().sections().get(0).sectionId());
    }
    @Test void unknownFieldConditionCannotDisappearAndMissingWorkRemainsVisible() throws Exception {
        var condition = new TemplateApplicabilityProtocol.ConditionResult("FIELD", id(20), null, null, List.of(), "UNKNOWN", List.of("attribute"));
        var result = expand(view(List.of(condition)), proposals(null, List.of()), List.of());
        assertEquals(List.of(id(20)), result.validation().candidate().pendingConditionIds());
        assertTrue(result.missing().stream().anyMatch(m -> m.kind().equals("OWNER_UNASSIGNED")));
        assertTrue(result.missing().stream().anyMatch(m -> m.kind().equals("COMPLETION_RULE_MISSING")));
        assertTrue(result.missing().contains(new TaskPlanDraftCandidatePolicy.MissingItem("RESEARCH_QUESTION_MISSING", id(10))));
        var c = result.validation().candidate();
        var omitted = new ArtifactProtocol.PlanCandidate(c.templateVersion(), c.profileRevision(), c.requirementNodeIds(), c.sections(), c.questions(), List.of());
        assertEquals(ErrorCode.CONDITION_UNKNOWN, assertThrows(TenantAccessException.class,
                () -> policy.inspect(omitted, id(5), view(List.of(condition)), Set.of())).code());
    }
    @Test void missingOrDuplicateTemplateNodeProposalIsRejected() {
        assertThrows(TenantAccessException.class, () -> expand(view(List.of()), proposals(null, List.of()).subList(0, 1), List.of()));
        var duplicate = new ArrayList<>(proposals(null, List.of())); duplicate.add(duplicate.get(0));
        assertThrows(TenantAccessException.class, () -> expand(view(List.of()), duplicate, List.of()));
    }
    @Test void sectionCycleAndQuestionCycleAreRejected() {
        var cycle = new ArrayList<>(proposals(id(40), List.of(id(30))));
        cycle.set(0, new TaskPlanDraftCandidatePolicy.SectionProposal(id(10), "Research", id(40), ArtifactProtocol.Method.MODEL_ASSISTED, List.of(id(11)), List.of(id(30))));
        assertEquals(ErrorCode.PLAN_CYCLE, assertThrows(TenantAccessException.class, () -> expand(view(List.of()), cycle, questions())).code());
        var question = new TaskPlanDraftCandidatePolicy.QuestionProposal("question_1", id(10), "Question", List.of(id(30)), List.of("question_1"));
        assertEquals(ErrorCode.PLAN_CYCLE, assertThrows(TenantAccessException.class,
                () -> expand(view(List.of()), proposals(id(40), List.of(id(30))), List.of(question))).code());
    }
    @Test void modelCannotIntroduceForeignNodesOrUnapprovedCompletionRules() {
        var foreign = new TaskPlanDraftCandidatePolicy.QuestionProposal("question_1", id(999), "Question", List.of(id(30)), List.of());
        assertThrows(TenantAccessException.class, () -> expand(view(List.of()), proposals(id(40), List.of(id(30))), List.of(foreign)));
        assertThrows(RuntimeException.class, () -> expand(view(List.of()), proposals(id(40), List.of(id(31))), questions()));
    }
    @Test void staleProfileAndUnapprovedTemplateAreRejected() throws Exception {
        var fixed = view(List.of()); var result = expand(fixed, proposals(id(40), List.of(id(30))), questions());
        assertEquals(ErrorCode.RESULT_VERSION_MISMATCH, assertThrows(TenantAccessException.class,
                () -> policy.inspect(result.validation().candidate(), id(500), fixed, Set.of(id(30)))).code());
        var t = fixed.template(); var disabled = new TemplateProtocol.Stored(t.tenant_id(), t.template_version(), "DISABLED", t.revision(),
                t.content(), t.content_sha256(), t.base_version(), t.reason(), t.authority_grant_id(), t.authority_revision(), t.created_by(), t.created_at(), t.dependency_refs());
        var invalid = new TemplateApplicabilityProtocol.TemplateView(disabled, fixed.applicability(), fixed.conditions(), List.of(), fixed.dependency_authorizations_sha256());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> policy.inspect(result.validation().candidate(), id(5), invalid, Set.of(id(30)))).code());
    }
}
