package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;

/**
 * Derived only from a schema-validated create request or a verified stored definition.
 * The definition already binds evidence classes in its immutable sample manifest.
 * This selects dependency consumption, never evaluation governance or execution permission.
 */
enum EvaluationDependencyUse {
    RESULTS(EvaluationReferences.PURPOSE),
    SIMULATION("SIMULATION_EVALUATION");

    private final String purpose;
    EvaluationDependencyUse(String purpose) { this.purpose = purpose; }
    String purpose() { return purpose; }

    static EvaluationDependencyUse forDefinition(EvaluationProtocol.Create request) {
        if (request == null) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        boolean simulation = !request.samples().isEmpty()
            && request.samples().stream().allMatch(sample -> "SIMULATION".equals(sample.evidence_class()));
        // Existing ordinary evaluations may record SIMULATION evidence in another environment.
        // They keep the existing purpose; only an entirely simulated, explicitly simulated
        // definition selects the restricted simulation dependency path.
        return simulation && "SIMULATION".equals(request.environment()) ? SIMULATION : RESULTS;
    }
}
