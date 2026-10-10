package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.identity.logout")
public record LogoutPersistencePolicy(String actorId, Integer recoveryBatchSize, Duration recoveryInterval) {
    public LogoutPersistencePolicy {
        if (actorId == null || recoveryBatchSize == null || recoveryInterval == null) {
            throw new IllegalArgumentException("LOGOUT_PERSISTENCE_CONFIGURATION_REQUIRED");
        }
        WireValues.uuid(actorId);
        if (recoveryBatchSize <= 0 || recoveryInterval.isNegative() || recoveryInterval.isZero()) {
            throw new IllegalArgumentException("LOGOUT_PERSISTENCE_CONFIGURATION_INVALID");
        }
    }
}
