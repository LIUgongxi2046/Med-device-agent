package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.time.Duration;
import java.util.Objects;
import software.amazon.awssdk.core.RequestOverrideConfiguration;

final class ObjectRequestDeadline {
    static final Duration MAXIMUM_CALL = Duration.ofSeconds(60);
    static final Duration MAXIMUM_ATTEMPT = Duration.ofSeconds(45);

    private ObjectRequestDeadline() { }

    static void apply(RequestOverrideConfiguration.Builder<?> options, S3ObjectReader.Admission admission) {
        Objects.requireNonNull(options); Objects.requireNonNull(admission).requireCurrent();
        Duration call = MAXIMUM_CALL;
        if (admission instanceof S3ObjectReader.TimedAdmission timed) {
            Duration remaining = Objects.requireNonNull(timed.remainingTime());
            if (remaining.isNegative() || remaining.toMillis() < 1) throw new TenantAccessException(ErrorCode.LEASE_STALE);
            if (remaining.compareTo(call) < 0) call = remaining;
        }
        // SDK取消实际HTTP请求，读取重试仍共享本次原调用截止。
        options.apiCallTimeout(call).apiCallAttemptTimeout(call.compareTo(MAXIMUM_ATTEMPT) < 0 ? call : MAXIMUM_ATTEMPT);
    }
}
