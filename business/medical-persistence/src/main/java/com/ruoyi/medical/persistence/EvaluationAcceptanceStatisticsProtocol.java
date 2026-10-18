package com.ruoyi.medical.persistence;

import java.util.List;

/** Local explicit statistical contract. A Policy value is data, never proof that approval actually occurred. */
public final class EvaluationAcceptanceStatisticsProtocol {
    private EvaluationAcceptanceStatisticsProtocol() { }
    public record Rule(String dimension,String method,String direction,String threshold,int repeat_count,int minimum_families) { }
    /** Scope and input versions are knowable before evaluation creation; no circular future definition hash. */
    public record Policy(String schema_version,String policy_id,String tenant_id,String project_id,
            PermissionProtocol.Basis rule_revision,PermissionProtocol.Basis dataset,PermissionProtocol.Basis sample_manifest_artifact,
            List<PermissionProtocol.Basis> candidate_refs,String runtime_manifest_sha256,String scope_sha256,
            String familywise_confidence,PermissionProtocol.Basis sampling_design_evidence,
            String created_at,String approved_at,List<Rule> dimensions) {
        public Policy { candidate_refs=List.copyOf(candidate_refs);dimensions=List.copyOf(dimensions); }
    }
    public record CellValue(EvaluationProtocol.Cell cell,String score_id,String numerator,String denominator,
            String ratio_lower,String ratio_upper) { }
    public record Family(String source_family,List<CellValue> observations,String mean_lower,String mean_upper) {
        public Family { observations=List.copyOf(observations); }
    }
    public record Outcome(String agent_version_id,String dimension,String method,String direction,String threshold,
            int required_repeats,int observed_cells,int expected_cells,int minimum_families,List<Family> families,
            String mean_lower,String mean_upper,String radius_upper,String lower_bound,String upper_bound,
            String adjusted_alpha,String verdict,List<String> reasons) {
        public Outcome { families=List.copyOf(families);reasons=List.copyOf(reasons); }
    }
    public record Result(String schema_version,String algorithm_id,String algorithm_version,String policy_sha256,
            String definition_sha256,String original_result_sha256,String score_set_sha256,
            int statistical_comparisons,String familywise_confidence,String adjusted_alpha,
            List<Outcome> outcomes,String verdict,List<String> missing_requirements) {
        public Result { outcomes=List.copyOf(outcomes);missing_requirements=List.copyOf(missing_requirements); }
    }
}
