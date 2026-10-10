package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.OperationMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class OperationService {
    private final EnterpriseAccess access;
    private final OperationMapper mapper;
    private final PermissionAuthority authority;
    private final OperationSchema schema;

    public OperationService(EnterpriseAccess access, OperationMapper mapper, PermissionAuthority authority, OperationSchema schema) {
        this.access = access; this.mapper = mapper; this.authority = authority; this.schema = schema;
    }

    public JsonNode lookup(ApplicationSessionIdentity.Verified subject, String context, InterfaceProtocol.OperationLookup request) {
        return access.execute(subject, admission -> {
            var values = values(admission, context); values.put("operationName", request.operationId()); values.put("key", request.idempotencyKey());
            var rows = mapper.lookup(values);
            if (rows.size() > 1) throw new IllegalStateException("原请求唯一身份不完整");
            if (rows.isEmpty()) return schema.result(InterfaceProtocol.LookupStatus.NOT_FOUND, null, UUID.randomUUID().toString());
            var row = rows.get(0);
            if (!row.subjectId().equals(subject.user().user_id())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            if (row.expired()) return schema.result(InterfaceProtocol.LookupStatus.EXPIRED, null, UUID.randomUUID().toString());
            return schema.result(InterfaceProtocol.LookupStatus.FOUND, operation(row), UUID.randomUUID().toString());
        });
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String id) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var values = values(admission, context);
            boolean reader = authority.projectAccess(admission.context().tenant_id(), subject.binding().view().mode(),
                    "operation.read", "OPERATION_READING", mapper.readers(values)).enterprise();
            values.put("enterpriseReader", reader); values.put("operationId", id);
            var rows = mapper.operation(values);
            if (rows.isEmpty()) throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
            if (rows.size() != 1) throw new IllegalStateException("原操作唯一身份不完整");
            var row = rows.get(0);
            if (!reader && !row.subjectId().equals(subject.user().user_id())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            return schema.operation(operation(row), UUID.randomUUID().toString());
        }).get());
    }

    private static Map<String,Object> values(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); return values;
    }

    private static InterfaceProtocol.Operation operation(OperationRow row) {
        return new InterfaceProtocol.Operation(row.operationId(), row.targetId(), InterfaceProtocol.OperationStatus.valueOf(row.state()),
                "/api/operations/" + row.operationId());
    }
}
