package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;

/** Current legal-use checks shared by live evaluation and settled-result readers.
 * Accepts an already schema/hash-verified definition. Deliberately never loads scores,
 * result references or evaluation lifecycle: terminal history still requires current legal use.
 */
final class SimulationEvaluationLegalUseAuthority {
    private SimulationEvaluationLegalUseAuthority() { }
    static RuleVersionProtocol.Validated require(EnterpriseAccess.Admission admission,
            EvaluationProtocol.Create request, EvaluationRuleAuthority rules,
            SimulationAuthorizedUseEvidenceAuthority evidence) throws IOException {
        if (EvaluationDependencyUse.forDefinition(request) != EvaluationDependencyUse.SIMULATION)
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        if (evidence == null || rules == null)
            throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        try {
            evidence.require(admission, request);
            // writing=true selects the existing strict current APPROVED decision/receipt chain.
            // This is a read-only check; it does not create a rule or confer a grant.
            return rules.require(admission, request.rule_revision(), true, EvaluationDependencyUse.SIMULATION);
        } catch (IOException | RuntimeException failure) { throw failure; }
        catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("SIMULATION_LEGAL_USE_READ_FAILED", failure);
        }
    }
}
