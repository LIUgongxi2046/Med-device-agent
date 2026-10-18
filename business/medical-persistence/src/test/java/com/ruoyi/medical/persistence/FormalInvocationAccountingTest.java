package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.math.*;
class FormalInvocationAccountingTest {
 FormalInvocationPricing.Schedule zero(){return new FormalInvocationPricing.Schedule("00000000-0000-4000-8000-000000000001","USD",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"CEILING_8_DECIMAL_PLACES");}
 @Test void zeroMoneyNeverReleasesUnknownTokenOrCallLiability(){var d=FormalInvocationAccounting.determine(zero(),new FormalInvocationPricing.ObservedUsage(null,null,null,null,null),null,true);assertEquals(new BigDecimal("0.00000000"),d.amount().orElseThrow());assertTrue(d.tokens().isEmpty());assertTrue(d.calls().isEmpty());assertFalse(d.maySettleAll());}
 @Test void terminalWithKnownMoneyAndTokensStillNeedsActualCalls(){var d=FormalInvocationAccounting.determine(zero(),new FormalInvocationPricing.ObservedUsage(BigInteger.ONE,BigInteger.ZERO,null,BigInteger.ONE,BigInteger.TWO),null,true);assertTrue(d.tokens().isPresent());assertFalse(d.maySettleAll());}
 @Test void allDimensionsAndActualTerminalAreRequired(){var u=new FormalInvocationPricing.ObservedUsage(BigInteger.ONE,BigInteger.ZERO,null,BigInteger.ONE,BigInteger.TWO);assertFalse(FormalInvocationAccounting.determine(zero(),u,BigInteger.ONE,false).maySettleAll());assertTrue(FormalInvocationAccounting.determine(zero(),u,BigInteger.ONE,true).maySettleAll());}
 @Test void oneAttemptCannotClaimMultipleCalls(){assertThrows(TenantAccessException.class,()->FormalInvocationAccounting.determine(zero(),new FormalInvocationPricing.ObservedUsage(null,null,null,null,null),BigInteger.TWO,true));}
}
