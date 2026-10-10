package com.ruoyi.medical.persistence;

public record SelectionOperationRow(String operationId, String requestSha256, String state, Integer httpStatus,
        String responseRef, String targetId, String schemaVersion, String schemaManifestSha256) { }
