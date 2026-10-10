package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateErrorResponseTest {
    @Test void unapprovedSourcesReturnACompleteNonRetryableError() throws Exception {
        requireError(ErrorCode.SOURCE_NOT_READY);
    }

    @Test void pendingRightsReturnACompleteNonRetryableError() throws Exception {
        requireError(ErrorCode.RIGHTS_PENDING);
    }

    @Test void dependencyCapacityReturnsACompleteNonRetryableError() throws Exception {
        requireError(ErrorCode.CONTEXT_LIMIT);
    }

    @Test void changedProjectProfileReturnsACompleteNonRetryableError() throws Exception {
        requireError(ErrorCode.RESULT_VERSION_MISMATCH);
    }

    @Test void planMaintenanceReturnsTheOriginalBusinessError() throws Exception {
        for (var code : java.util.List.of(ErrorCode.PLAN_STALE, ErrorCode.PLAN_CYCLE, ErrorCode.PLAN_UNCONFIRMED,
                ErrorCode.CONDITION_UNKNOWN, ErrorCode.DEPENDENCY_UNAVAILABLE, ErrorCode.LINEAGE_CYCLE, ErrorCode.LINEAGE_REVIEW_REQUIRED)) {
            requireError(code);
        }
    }

    private void requireError(ErrorCode code) throws Exception {
        String requestId = UUID.randomUUID().toString();
        var payload = JsonMapper.builder().build().readTree(new IdentityErrorResponseWriter().encode(code, requestId));
        var result = TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(), InterfaceBindings.ERROR, payload).read();
        assertEquals(code.name(), result.code());
        assertEquals(requestId, result.requestId());
        assertEquals("1.0.0", result.schemaVersion());
        assertFalse(result.message().isBlank());
        assertFalse(result.retryable());
        assertTrue(result.allowedActions().isEmpty());
        assertTrue(result.details().isEmpty());
        assertNull(result.resourceRevision());
        assertNull(result.operationId());
        assertEquals(9, payload.size());
    }
}
