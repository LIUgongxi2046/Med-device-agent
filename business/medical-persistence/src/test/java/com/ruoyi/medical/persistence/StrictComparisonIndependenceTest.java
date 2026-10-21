package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Test source added during code-first phase; execution is deliberately deferred. */
class StrictComparisonIndependenceTest {
    @Test void sameAuthorCannotApprove(){assertThrows(TenantAccessException.class,()->StrictComparisonPolicyService.requireIndependent("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000001"));}
    @Test void distinctActorsPassOnlyTheSeparationCheck(){assertDoesNotThrow(()->StrictComparisonPolicyService.requireIndependent("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000002"));}
    @Test void malformedActorIsNotAnApprovalIdentity(){assertThrows(IllegalArgumentException.class,()->StrictComparisonPolicyService.requireIndependent("author","approver"));}
}
