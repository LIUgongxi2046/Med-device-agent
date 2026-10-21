package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Structural authority regression tests; authored only, not executed during code-first work. */
class TaskSourceVerifierCommittedReviewBoundaryTest {
    @Test void onlyAuthorityCanMintExactOutputReadProof(){
        var type=TaskSourceVerifierCommittedReviewAuthority.CommittedRead.class;
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void downstreamVerifiedCannotBeConstructedFromReceiptOrArtifactId(){
        for(var constructor:TaskSourceVerifierCommittedReviewAuthority.Verified.class.getDeclaredConstructors())
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        for(var constructor:TaskSourceVerifierStageCommitService.ForConsumer.class.getDeclaredConstructors())
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void exactReadSeamIsNotPublicGenericScopeExpansion()throws Exception{
        var method=ResourceReadAuthority.class.getDeclaredMethod("requireSourceVerifierOutput",EnterpriseAccess.Admission.class,
                com.ruoyi.medical.persistence.PermissionProtocol.Scope.class,String.class,String.class,
                TaskSourceVerifierCommittedReviewAuthority.CommittedRead.class);
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }
}
