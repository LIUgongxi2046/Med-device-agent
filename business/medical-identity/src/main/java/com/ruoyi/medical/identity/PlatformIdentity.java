package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.util.Objects;

record PlatformIdentity(String identityId, String userId, BigInteger revision, VerifiedOidcIdentity binding) {
    PlatformIdentity {
        WireValues.uuid(identityId);
        WireValues.uuid(userId);
        Objects.requireNonNull(binding);
        WireValues.revision(revision.toString());
        if (revision.signum() <= 0) throw new IllegalArgumentException("IDENTITY_REVISION_INVALID");
    }

    @Override public String toString() { return "PlatformIdentity"; }
}
