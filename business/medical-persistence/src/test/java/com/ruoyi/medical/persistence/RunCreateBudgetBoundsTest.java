package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunCreateBudgetBoundsTest {
    private final LocalDateTime now=LocalDateTime.of(2026,10,20,0,0);
    private final BudgetProtocol.Limits limits=new BudgetProtocol.Limits("10.00","10","10","1","1","2026-10-21T00:00:00Z");
    @Test void exactExhaustionPermitsPostReservationButNotNewAdmission()throws Exception{
        var account=JsonMapper.builder().build().readTree("{\"spent\":\"4.00\",\"reserved\":\"6.00\",\"spent_tokens\":\"4\",\"reserved_tokens\":\"6\",\"spent_calls\":\"4\",\"reserved_calls\":\"6\"}");
        assertDoesNotThrow(()->RunCreateBudget.requireWithinBounds(account,limits,now));
        assertThrows(TenantAccessException.class,()->RunCreateBudget.requireAvailable(account,limits,now));
        assertThrows(TenantAccessException.class,()->RunCreateBudget.requireWithinBounds(account,limits,now.plusDays(1)));
    }
    @Test void separateExistingActionGuardsMayReenterAtExactReservedCap()throws Exception{
        var account=JsonMapper.builder().build().readTree("{\"spent\":\"0.00\",\"reserved\":\"10.00\",\"spent_tokens\":\"0\",\"reserved_tokens\":\"10\",\"spent_calls\":\"0\",\"reserved_calls\":\"10\"}");
        // Each guard checks current persisted totals afresh; neither check creates a reservation.
        Runnable dispatchGuard=()->RunCreateBudget.requireWithinBounds(account,limits,now);
        Runnable nextSegmentGuard=()->RunCreateBudget.requireWithinBounds(account,limits,now.plusSeconds(1));
        assertDoesNotThrow(dispatchGuard::run);assertDoesNotThrow(nextSegmentGuard::run);
        assertThrows(TenantAccessException.class,()->RunCreateBudget.requireAvailable(account,limits,now));
        ((com.fasterxml.jackson.databind.node.ObjectNode)account).put("reserved","10.01");
        assertThrows(TenantAccessException.class,dispatchGuard::run);
        assertThrows(TenantAccessException.class,nextSegmentGuard::run);
    }
    @Test void postReservationCannotExceedAnyCeiling()throws Exception{
        var account=JsonMapper.builder().build().readTree("{\"spent\":\"4.00\",\"reserved\":\"6.00\",\"spent_tokens\":\"4\",\"reserved_tokens\":\"6\",\"spent_calls\":\"4\",\"reserved_calls\":\"6\"}");
        for(String field:java.util.List.of("reserved","reserved_tokens","reserved_calls")){
            var over=account.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)over).put(field,"7");
            assertThrows(TenantAccessException.class,()->RunCreateBudget.requireWithinBounds(over,limits,now));
        }
    }
}
