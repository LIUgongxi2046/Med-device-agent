package com.ruoyi.medical.persistence;

public record OperationRow(String operationId, String subjectId, String targetId, String state, boolean expired) { }
