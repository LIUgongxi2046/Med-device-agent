package com.ruoyi.medical.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;

/** Exact supported tariff arithmetic. A Schedule is data, never an approval or read proof. */
public final class FormalInvocationPricing {
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigInteger MAX_COUNT = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private FormalInvocationPricing() { }
    public record Schedule(String priceVersion, String currency, BigDecimal uncachedInputPerMillion,
            BigDecimal cachedInputPerMillion, BigDecimal cacheWritePerMillion, BigDecimal outputPerMillion, String rounding) {
        public Schedule {
            WireValues.uuid(priceVersion);
            if (currency == null || !currency.matches("[A-Z]{3}") || !"CEILING_8_DECIMAL_PLACES".equals(rounding)) invalid();
            rate(uncachedInputPerMillion); rate(cachedInputPerMillion); rate(cacheWritePerMillion); rate(outputPerMillion);
        }
    }
    public record Usage(BigInteger uncachedInputTokens, BigInteger cachedInputTokens, BigInteger cacheWriteTokens, BigInteger outputTokens) {
        public Usage { count(uncachedInputTokens); count(cachedInputTokens); count(cacheWriteTokens); count(outputTokens); count(uncachedInputTokens.add(cachedInputTokens).add(cacheWriteTokens).add(outputTokens)); }
        public BigInteger totalTokens() { return uncachedInputTokens.add(cachedInputTokens).add(cacheWriteTokens).add(outputTokens); }
    }
    public static BigDecimal amount(Schedule price, Usage usage) {
        Objects.requireNonNull(price); Objects.requireNonNull(usage);
        var total = price.uncachedInputPerMillion().multiply(new BigDecimal(usage.uncachedInputTokens()))
                .add(price.cachedInputPerMillion().multiply(new BigDecimal(usage.cachedInputTokens())))
                .add(price.cacheWritePerMillion().multiply(new BigDecimal(usage.cacheWriteTokens())))
                .add(price.outputPerMillion().multiply(new BigDecimal(usage.outputTokens())))
                .divide(MILLION, 8, RoundingMode.CEILING);
        if (total.precision() > 24) invalid();
        return total;
    }
    /** Preserve provider unknowns. A zero tariff can determine money without inventing a token count. */
    public record ObservedUsage(BigInteger uncachedInputTokens, BigInteger cachedInputTokens,
            BigInteger cacheWriteTokens, BigInteger outputTokens, BigInteger reportedTotalTokens) {
        public ObservedUsage {
            for (var v : java.util.Arrays.asList(uncachedInputTokens,cachedInputTokens,cacheWriteTokens,outputTokens,reportedTotalTokens)) if(v!=null) count(v);
            BigInteger known=BigInteger.ZERO;
            for(var v:java.util.Arrays.asList(uncachedInputTokens,cachedInputTokens,cacheWriteTokens,outputTokens))if(v!=null)known=known.add(v);
            count(known);
            if(reportedTotalTokens!=null && (known.compareTo(reportedTotalTokens)>0 || uncachedInputTokens!=null&&cachedInputTokens!=null&&cacheWriteTokens!=null&&outputTokens!=null&&!known.equals(reportedTotalTokens)))invalid();
        }
    }
    public static java.util.Optional<BigDecimal> observedAmount(Schedule price,ObservedUsage usage) {
        var rates=java.util.List.of(price.uncachedInputPerMillion(),price.cachedInputPerMillion(),price.cacheWritePerMillion(),price.outputPerMillion());
        var counts=java.util.Arrays.asList(usage.uncachedInputTokens(),usage.cachedInputTokens(),usage.cacheWriteTokens(),usage.outputTokens());
        BigDecimal amount=BigDecimal.ZERO;
        for(int i=0;i<rates.size();i++){if(counts.get(i)==null){if(rates.get(i).signum()!=0)return java.util.Optional.empty();}else amount=amount.add(rates.get(i).multiply(new BigDecimal(counts.get(i))));}
        amount=amount.divide(MILLION,8,RoundingMode.CEILING);if(amount.precision()>24)invalid();return java.util.Optional.of(amount);
    }
    /** Reserve the expensive input class when cache outcome is not known before sending. */
    public static BigDecimal upperAmount(Schedule price, BigInteger maximumInputTokens, BigInteger maximumOutputTokens) {
        count(maximumInputTokens); count(maximumOutputTokens);
        var max = price.uncachedInputPerMillion().max(price.cachedInputPerMillion()).max(price.cacheWritePerMillion());
        return amount(new Schedule(price.priceVersion(), price.currency(), max, max, max, price.outputPerMillion(), price.rounding()),
                new Usage(maximumInputTokens, BigInteger.ZERO, BigInteger.ZERO, maximumOutputTokens));
    }
    private static void count(BigInteger value) { if (value == null || value.signum() < 0 || value.compareTo(MAX_COUNT) > 0) invalid(); }
    private static void rate(BigDecimal value) { if (value == null || value.signum() < 0 || value.scale() > 8 || value.precision() > 24) invalid(); }
    private static void invalid() { throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE); }
}
