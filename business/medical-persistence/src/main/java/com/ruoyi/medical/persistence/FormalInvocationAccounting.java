package com.ruoyi.medical.persistence;
import java.math.*;import java.util.*;import com.ruoyi.medical.contracts.ErrorCode;
/** Accounting dimensions remain independent; known money is never evidence of complete usage. */
public final class FormalInvocationAccounting {
 private FormalInvocationAccounting(){}
 public record Determination(Optional<BigDecimal> amount,Optional<BigInteger> tokens,Optional<BigInteger> calls,boolean providerTerminal){
  public Determination{Objects.requireNonNull(amount);Objects.requireNonNull(tokens);Objects.requireNonNull(calls);}
  public boolean maySettleAll(){return providerTerminal&&amount.isPresent()&&tokens.isPresent()&&calls.isPresent();}
 }
 public static Determination determine(FormalInvocationPricing.Schedule price,FormalInvocationPricing.ObservedUsage usage,BigInteger actualBillableCalls,boolean terminal){
  if(actualBillableCalls!=null&&(actualBillableCalls.signum()<0||actualBillableCalls.compareTo(BigInteger.ONE)>0))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  var amount=FormalInvocationPricing.observedAmount(price,usage);
  // Total comes only from authenticated provider usage, never the reserved/tokenized upper bound.
  return new Determination(amount,Optional.ofNullable(usage.reportedTotalTokens()),Optional.ofNullable(actualBillableCalls),terminal);
 }
}
