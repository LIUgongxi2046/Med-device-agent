package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Pure verification of an exact evaluation ledger and a human's bounded decision.
 *
 * The caller MUST load real fixed evaluation rows, their full ordered score history, and a currently
 * authorized human decision in its own transaction. ExpectedBinding is an equality boundary supplied
 * by that caller, not an authorization token. This verifier does not query grants, experts, external
 * outputs, object storage, or release scopes, and cannot establish any of those facts by accepting IDs.
 * Proposed runtime/release/scope hashes bind the decision's intended target ONLY; the old evaluation
 * contract does not prove that those were actually executed/covered. VerifiedEvidence is therefore
 * intentionally never a production publication permit, even for a genuine complete PASS.
 */
public final class EvaluationAcceptanceEvidenceVerifier {
    public static final String ACCEPTANCE_SCOPE="EVALUATED_ASSERTIONS_ONLY";
    public static final List<String> MISSING_PRODUCTION_EVIDENCE=List.of(
        "CASE_RUN_STEP_INVOCATION_BINDINGS_NOT_IN_CURRENT_CONTRACT",
        "EVALUATED_RUNTIME_BINDING_NOT_IN_CURRENT_CONTRACT",
        "INDEPENDENT_EXPERT_QUALIFICATION_NOT_IN_CURRENT_CONTRACT",
        "APPROVED_STATISTICAL_THRESHOLDS_NOT_IN_CURRENT_CONTRACT",
        "PROJECT_SOURCE_FAMILY_CLUSTERED_VARIANCE_NOT_IN_CURRENT_CONTRACT",
        "RELEASE_SCOPE_COVERAGE_NOT_IN_CURRENT_CONTRACT");

    public static final List<String> MISSING_PRODUCTION_EVIDENCE_WITH_POLICY=List.of(
        "CASE_RUN_STEP_INVOCATION_BINDINGS_NOT_IN_CURRENT_CONTRACT",
        "EVALUATED_RUNTIME_BINDING_NOT_IN_CURRENT_CONTRACT",
        "INDEPENDENT_EXPERT_QUALIFICATION_NOT_IN_CURRENT_CONTRACT",
        "RELEASE_SCOPE_COVERAGE_NOT_IN_CURRENT_CONTRACT",
        "POLICY_AND_SAMPLING_DESIGN_AUTHORIZATION_NOT_VERIFIED_BY_PURE_ENGINE");

    public record Target(String tenant_id,String project_id,String evaluation_id,String definition_sha256,
            String result_sha256,String score_history_sha256,PermissionProtocol.Basis candidate,
            String candidate_dependency_manifest_sha256,String candidate_limits_sha256,
            String candidate_schema_manifest_sha256,String proposed_runtime_manifest_sha256,
            String proposed_release_manifest_sha256,String proposed_release_scope_sha256,String acceptance_policy_sha256) { }
    /** Identity copied from the caller's actual authorized and persisted human decision, never manufactured here. */
    public record DecisionIdentity(String actor_id,String authority_grant_id,String authority_revision,
            String decision_operation_id,String decided_at) { }
    public record ExpectedBinding(Target target,String evaluation_status,String evaluation_revision,
            DecisionIdentity human_decision) { }
    public record AcceptanceDecision(String schema_version,String decision_id,String acceptance_scope,String decision,
            Target target,DecisionIdentity human_decision,List<String> acknowledged_limitations,String reason) {
        public AcceptanceDecision { acknowledged_limitations=List.copyOf(acknowledged_limitations); }
    }
    public record Report(String schema_version,String decision_id,String acceptance_scope,String decision,Target target,
            DecisionIdentity human_decision,List<String> verified_dimensions,List<String> verified_sample_ids,
            List<String> effective_score_ids,int score_history_size,String decision_sha256,boolean production_eligible,
            List<String> still_missing_production_evidence,String statistics_sha256) {
        public Report {
            verified_dimensions=List.copyOf(verified_dimensions);verified_sample_ids=List.copyOf(verified_sample_ids);
            effective_score_ids=List.copyOf(effective_score_ids);still_missing_production_evidence=List.copyOf(still_missing_production_evidence);
        }
    }
    /** A proof of exact local integrity only. It has no public constructor or publication-enabling flag. */
    public static final class VerifiedEvidence {
        private final Report report;
        private final EvaluationAcceptanceStatisticsProtocol.Result statistics;
        private VerifiedEvidence(Report report,EvaluationAcceptanceStatisticsProtocol.Result statistics){this.report=report;this.statistics=statistics;}
        public EvaluationAcceptanceStatisticsProtocol.Result statistics(){return statistics;}
        public Report report(){return report;}
        public boolean productionEligible(){return false;}
        public List<String> stillMissingProductionEvidence(){return report.still_missing_production_evidence();}
        public String decisionSha256(){return report.decision_sha256();}
    }
    public record Limits(int maximumHistory,int maximumCells,long maximumCanonicalBytes) {
        public Limits {
            if(maximumHistory<1||maximumHistory>50000||maximumCells<1||maximumCells>10000
                ||maximumCanonicalBytes<1||maximumCanonicalBytes>134217728L)throw new IllegalArgumentException("Invalid integrity verifier limits");
        }
        public static Limits defaults(){return new Limits(50000,10000,33554432L);}
    }
    private final PermissionSchema json;
    private final EvaluationSchema evaluations;
    private final EvaluationVerdict verdicts;
    private final EvaluationAcceptanceEvidenceSchema local;
    private final Limits limits;
    public EvaluationAcceptanceEvidenceVerifier(PermissionSchema json,EvaluationSchema evaluations,
            EvaluationAcceptanceEvidenceSchema local) {this(json,evaluations,local,Limits.defaults());}
    public EvaluationAcceptanceEvidenceVerifier(PermissionSchema json,EvaluationSchema evaluations,
            EvaluationAcceptanceEvidenceSchema local,Limits limits) {
        this.json=Objects.requireNonNull(json);this.evaluations=Objects.requireNonNull(evaluations);
        this.local=Objects.requireNonNull(local);this.limits=Objects.requireNonNull(limits);this.verdicts=new EvaluationVerdict(evaluations);
    }

    public VerifiedEvidence verify(EvaluationProtocol.Definition definition,EvaluationProtocol.Result result,
            List<EvaluationProtocol.Score> fullHistory,AcceptanceDecision decision,ExpectedBinding expected)throws IOException {
        return verify(definition,result,fullHistory,decision,expected,null);
    }
    public VerifiedEvidence verify(EvaluationProtocol.Definition definition,EvaluationProtocol.Result result,
            List<EvaluationProtocol.Score> fullHistory,AcceptanceDecision decision,ExpectedBinding expected,
            EvaluationAcceptanceStatisticsProtocol.Policy policy)throws IOException {
        if(definition==null||result==null||fullHistory==null||decision==null||expected==null)mismatch();
        if(fullHistory.size()>limits.maximumHistory())limit();
        var history=List.copyOf(fullHistory);
        long matrixSize=(long)definition.request().samples().size()*definition.request().candidates().size()
            *definition.request().required_dimensions().size()*definition.request().variance_policy().repeat_count();
        if(matrixSize>limits.maximumCells()||result.cells().size()>limits.maximumCells()||result.effective_scores().size()>limits.maximumCells())limit();
        // Sum actual canonical UTF-8, not UTF-16 length; caller-selected bounds are enforced before matrix/recomputation.
        long bytes=0;for(Object value:List.of(definition,result,history,decision,expected)) {
            bytes+=json.canonicalObject(value).getBytes(StandardCharsets.UTF_8).length;if(bytes>limits.maximumCanonicalBytes())limit();
        }
        local.validateExpected(expected);decision=local.validateDecision(decision);
        if(!expected.target().equals(decision.target())||!expected.human_decision().equals(decision.human_decision()))mismatch();
        var limitations=policy==null?MISSING_PRODUCTION_EVIDENCE:MISSING_PRODUCTION_EVIDENCE_WITH_POLICY;
        if(!new HashSet<>(decision.acknowledged_limitations()).equals(new HashSet<>(limitations)))mismatch();
        // Rebind through the existing closed schemas and semantics, even when the caller supplied typed records.
        definition=evaluations.definition(json.canonicalObject(definition));
        evaluations.validate("Result",json.tree(result));
        String definitionHash=evaluations.hash(definition);var target=expected.target();
        if(!definition.tenant_id().equals(target.tenant_id())||!Objects.equals(definition.request().project_id(),target.project_id())
            ||!definition.evaluation_id().equals(target.evaluation_id())||!definitionHash.equals(target.definition_sha256()))mismatch();
        if(!evaluations.hash(history).equals(target.score_history_sha256())||!evaluations.hash(result).equals(target.result_sha256()))mismatch();
        String status=switch(result.verdict()){case "PASS"->"COMPLETED";case "FAIL"->"FAILED";default->"INSUFFICIENT_EVIDENCE";};
        if(!status.equals(expected.evaluation_status())
            ||!Integer.toString(history.size()+2).equals(expected.evaluation_revision()))mismatch();

        // Exact candidate identities and immutable snapshots are retained; no editable candidate subset.
        var candidates=definition.request().candidates();var snapshots=definition.candidate_snapshots();
        if(candidates.size()!=snapshots.size())mismatch();
        var candidateIds=new HashSet<String>();EvaluationProtocol.Candidate selected=null;
        for(int i=0;i<candidates.size();i++) {
            var basis=candidates.get(i);var snapshot=snapshots.get(i);
            if(!candidateIds.add(snapshot.version_id())||!basis.version_id().equals(snapshot.version_id())
                ||!basis.content_sha256().equals(snapshot.content_sha256()))mismatch();
            if(basis.equals(target.candidate()))selected=snapshot;
        }
        if(selected==null||!evaluations.hash(selected.dependency_manifest()).equals(target.candidate_dependency_manifest_sha256())
            ||!evaluations.hash(selected.limits()).equals(target.candidate_limits_sha256())
            ||!selected.schema_manifest_sha256().equals(target.candidate_schema_manifest_sha256()))mismatch();
        var sampleIds=definition.request().samples().stream().map(EvaluationProtocol.Sample::sample_id).sorted().toList();
        if(!sampleIds.containsAll(definition.approved_expectation_samples())
            ||!definition.approved_expectation_samples().equals(definition.approved_expectation_samples().stream().sorted().toList()))mismatch();
        Instant created=Instant.parse(definition.created_at()),decided=Instant.parse(decision.human_decision().decided_at());
        if(decided.isBefore(created))mismatch();
        for(int i=0;i<history.size();i++) {
            var score=evaluations.score(json.canonicalObject(history.get(i)));
            if(!Integer.toString(i+1).equals(score.observation().expected_revision()))mismatch();
            if(snapshots.stream().anyMatch(c->c.version_id().equals(score.observation().cell().agent_version_id())
                &&c.created_by().equals(score.scorer_id())))mismatch();
            var scored=Instant.parse(score.created_at());if(scored.isBefore(created)||scored.isAfter(decided))mismatch();
        }
        var recomputed=verdicts.compute(definition,definitionHash,history);
        if(!json.canonicalObject(recomputed).equals(json.canonicalObject(result)))mismatch();
        EvaluationAcceptanceStatisticsProtocol.Result statistics=null;
        if(policy==null) {if(target.acceptance_policy_sha256()!=null)mismatch();}
        else {
            if(!evaluations.hash(policy).equals(target.acceptance_policy_sha256())
                ||!policy.runtime_manifest_sha256().equals(target.proposed_runtime_manifest_sha256())
                ||!policy.scope_sha256().equals(target.proposed_release_scope_sha256()))mismatch();
            statistics=new EvaluationAcceptanceStatisticsEngine(json,evaluations,local).compute(definition,result,history,policy);
        }
        if("ACCEPT".equals(decision.decision())) {
            boolean pass=statistics==null?"PASS".equals(result.verdict())&&result.missing_requirements().isEmpty()
                &&result.cells().stream().allMatch(c->"PASS".equals(c.status())):"PASS".equals(statistics.verdict());
            if(!pass||result.cells().isEmpty()
                ||!"PRODUCTION".equals(definition.request().environment())
                ||definition.request().samples().stream().anyMatch(s->!"REAL".equals(s.evidence_class())||!"ACCEPTANCE".equals(s.split())))
                throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        }
        var report=new Report("1.0.0",decision.decision_id(),ACCEPTANCE_SCOPE,decision.decision(),target,decision.human_decision(),
            definition.request().required_dimensions().stream().sorted().toList(),sampleIds,
            result.effective_scores().stream().map(EvaluationProtocol.Score::score_id).sorted().toList(),history.size(),
            evaluations.hash(decision),false,limitations,statistics==null?null:evaluations.hash(statistics));
        local.validate("Report",json.tree(report));return new VerifiedEvidence(report,statistics);
    }
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}
