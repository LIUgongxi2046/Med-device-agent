package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.Objects;

public final class TenantAccessException extends RuntimeException {
    private final ErrorCode code;

    public TenantAccessException(ErrorCode code) {
        super(Objects.requireNonNull(code).name());
        this.code = code;
    }

    public ErrorCode code() { return code; }
}
