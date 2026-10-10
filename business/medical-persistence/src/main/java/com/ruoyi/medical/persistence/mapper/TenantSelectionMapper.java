package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantSelectionMapper {
    List<SelectionOperationRow> currentOperation(@Param("userId") String userId, @Param("key") String key);
    int saveOperation(@Param("userId") String userId, @Param("operationId") String operationId,
            @Param("key") String key, @Param("sha256") String sha256, @Param("response") String response,
            @Param("targetId") String targetId, @Param("manifest") String manifest, @Param("classification") String classification);
    int saveAudit(@Param("userId") String userId, @Param("auditId") String auditId,
            @Param("resource") String resource, @Param("requestId") String requestId,
            @Param("manifest") String manifest, @Param("classification") String classification);
    int saveEvent(@Param("userId") String userId, @Param("eventId") String eventId,
            @Param("contextId") String contextId, @Param("revision") String revision, @Param("payload") String payload);
}
