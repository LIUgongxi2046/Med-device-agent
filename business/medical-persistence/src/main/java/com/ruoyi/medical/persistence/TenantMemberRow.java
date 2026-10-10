package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record TenantMemberRow(String tenantId, String memberId, String userId, String status,
                             LocalDateTime validFrom, LocalDateTime validTo, BigInteger revision) { }
