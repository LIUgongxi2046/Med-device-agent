package com.ruoyi.medical.persistence;

import java.math.BigInteger;

public record ScenarioCatalogueRow(String tenantId, String projectId, String configurationId, String versionId,
        String name, String status, String contentSha256, BigInteger configurationRevision,
        String registeredProjectId, String registeredKind, String registeredSha256) { }
