package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class IdentityErrorResponseWriter {
    private final JsonMapper json = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    public void write(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        String requestId = UUID.randomUUID().toString();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        response.getOutputStream().write(encode(code, requestId));
    }

    public byte[] encode(ErrorCode code, String requestId) throws IOException {
        return json.writeValueAsBytes(body(code, requestId));
    }

    static InterfaceProtocol.Error body(ErrorCode code, String requestId) {
        String message = switch (code) {
            case AUTHENTICATION_REQUIRED -> "需要有效的应用会话";
            case AUTHENTICATION_FAILED -> "身份验证失败";
            case ACCESS_DENIED -> "访问权限不足";
            case CONTEXT_CHANGED -> "当前企业上下文已经变化";
            case SCOPE_STALE -> "当前授权或治理版本已经变化";
            case CURSOR_EXPIRED -> "原查询水位已经变化，需要重新查询";
            case CURSOR_INVALID -> "查询游标与原范围不一致";
            case RESULT_VERSION_MISMATCH -> "指定项目属性版本已经变化";
            case IDEMPOTENCY_CONFLICT -> "相同请求身份的参数已经变化";
            case REVISION_CONFLICT -> "当前账户版本或管理状态不允许此操作";
            case CONTENT_MISMATCH -> "当前内容、来源或摘要不一致";
            case OBJECT_UNAVAILABLE -> "当前授权范围内无法读取指定对象或版本";
            case BUDGET_EXCEEDED -> "批准预算不足以覆盖已用和在途占用";
            case REVISION_LIMIT -> "当前版本无法继续增加";
            case SCHEMA_UNSUPPORTED -> "请求的 Schema 版本不受支持";
            case SCHEMA_INVALID -> "请求或响应不符合指定 Schema";
            case SOURCE_NOT_READY -> "资料处理或人工核准尚未完成";
            case RIGHTS_PENDING -> "当前资料权利尚待审查";
            case PLAN_STALE -> "当前任务或计划版本已经变化";
            case PLAN_CYCLE -> "计划依赖存在循环，需要调整章节或问题依赖";
            case PLAN_UNCONFIRMED -> "计划尚未满足人工确认条件";
            case CONDITION_UNKNOWN -> "计划存在尚未确定的适用条件或责任人员";
            case DEPENDENCY_UNAVAILABLE -> "计划依赖版本当前不可采用";
            case LINEAGE_CYCLE -> "内容来源关系存在循环";
            case LINEAGE_REVIEW_REQUIRED -> "完整来源关系尚未完成核验";
            case CONTEXT_LIMIT -> "完整依赖超出当前配置容量";
            case FILE_TOO_LARGE -> "文件大小超出当前服务器限制";
            case FILE_TYPE_UNSUPPORTED -> "文件格式或声明媒体类型不受支持";
            case ACTION_STALE -> "上传状态或操作期限已经变化";
            case STEP_NOT_READY -> "当前上传分段尚未完成前置操作";
            case LEASE_STALE -> "本次传输期限或并发名额已经失效";
            case RATE_LIMITED -> "当前并发传输容量已经用尽";
            default -> throw new IllegalArgumentException("IDENTITY_ERROR_CODE_INVALID");
        };
        return new InterfaceProtocol.Error("1.0.0", code.name(), message, requestId, null, List.of(), false, null, List.of());
    }
}
