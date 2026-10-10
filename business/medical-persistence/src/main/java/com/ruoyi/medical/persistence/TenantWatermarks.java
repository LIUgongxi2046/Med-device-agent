package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.util.Objects;

public record TenantWatermarks(BigInteger authEpoch, BigInteger governanceEpoch) {
    public TenantWatermarks {
        WireValues.revision(Objects.requireNonNull(authEpoch).toString());
        WireValues.revision(Objects.requireNonNull(governanceEpoch).toString());
    }
}
