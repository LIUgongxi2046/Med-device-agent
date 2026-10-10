package com.ruoyi.medical.contracts;

import java.util.Objects;

public final class SchemaRejection extends IllegalArgumentException {
    private final ErrorCode code;

    public SchemaRejection(ErrorCode code) {
        super(message(code));
        this.code = code;
    }

    private static String message(ErrorCode code) {
        return switch (Objects.requireNonNull(code)) {
            case SCHEMA_INVALID -> "SCHEMA_VALIDATION_FAILED";
            case SCHEMA_UNSUPPORTED -> "SCHEMA_UNSUPPORTED";
            default -> throw new IllegalArgumentException("Schema 拒绝类型不符合发布集合");
        };
    }

    public ErrorCode code() { return code; }
}
