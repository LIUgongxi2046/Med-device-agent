package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormalTaskNodeAuthorityTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void proofCannotBeConstructedByCaller(){
        for(var constructor:FormalTaskNodeAuthority.Verified.class.getDeclaredConstructors())
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void originalDelegationWitnessComparisonPreservesUnsignedRevision()throws Exception{
        var original=json.readTree("{\"approval\":{\"grant_revision\":18446744073709551614},\"rule\":{\"id\":\"original\"}}");
        var same=original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)same.required("approval")).put("grant_revision",new BigInteger("18446744073709551614"));
        assertTrue(FormalTaskNodeAuthority.sameEvidence(original,same));
        ((com.fasterxml.jackson.databind.node.ObjectNode)same.required("approval")).put("grant_revision",new BigInteger("18446744073709551615"));
        assertFalse(FormalTaskNodeAuthority.sameEvidence(original,same));
        assertFalse(FormalTaskNodeAuthority.sameEvidence(json.readTree("{\"revision\":1}"),json.readTree("{\"revision\":\"1\"}")));
        assertFalse(FormalTaskNodeAuthority.sameEvidence(json.readTree("{\"revision\":1}"),json.readTree("{\"revision\":1.0}")));
    }
    @Test void replacementRuleAndExtraWitnessFieldDoNotPass()throws Exception{
        var original=json.readTree("{\"rule\":\"old\",\"approval\":{\"revision\":1}}");
        assertFalse(FormalTaskNodeAuthority.sameEvidence(original,json.readTree("{\"rule\":\"new\",\"approval\":{\"revision\":1}}")));
        assertFalse(FormalTaskNodeAuthority.sameEvidence(original,json.readTree("{\"rule\":\"old\",\"approval\":{\"revision\":1},\"new_grant\":true}")));
    }
    @Test void cumulativeSpentAndReservationsNotPerRequestAreBounded()throws Exception{
        var available=json.readTree("{\"spent\":\"4.00\",\"reserved\":\"5.00\",\"spent_tokens\":\"4\",\"reserved_tokens\":\"5\",\"spent_calls\":\"4\",\"reserved_calls\":\"5\"}");
        assertDoesNotThrow(()->FormalTaskNodeAuthority.requireCumulative(available,"10.00","10","10"));
        assertDoesNotThrow(()->FormalTaskNodeAuthority.requireCumulative(available,"9.00","9","9",false));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"8.99","9","9",false));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"9.00","8","9",false));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"9.00","9","8",false));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"9.00","10","10"));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"10.00","9","10"));
        assertThrows(TenantAccessException.class,()->FormalTaskNodeAuthority.requireCumulative(available,"10.00","10","9"));
    }
}
