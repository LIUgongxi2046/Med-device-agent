package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;

final class LocalAccountConflict extends RuntimeException {
    private final ErrorCode code;
    LocalAccountConflict(ErrorCode code) { super(code.name()); this.code = code; }
    ErrorCode code() { return code; }
}
