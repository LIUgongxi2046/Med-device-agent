package com.ruoyi.medical.identity;

/** An additional release gate, never a substitute for native permissions or domain authorization. */
public interface FrameworkBindingAuthority {
    Binding require(String operationId, String method, String path);
    record Binding(String releaseId, String environmentId, String manifestSha256) { }
}
