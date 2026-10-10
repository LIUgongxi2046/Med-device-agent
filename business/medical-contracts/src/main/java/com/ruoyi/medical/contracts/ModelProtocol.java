package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;

public final class ModelProtocol {
    private ModelProtocol() { }

    public enum ExecutionStatus { RESERVED, DISPATCHING, IN_FLIGHT, SUCCEEDED, FAILED, CANCEL_REQUESTED, CANCELLED, UNKNOWN }
    public enum SettlementStatus { PENDING, SETTLED, DISPUTED }
    public enum Purpose { TASK, COMPACTION, VERIFICATION, EVALUATION }
    public enum CancellationReason { RUN_CANCEL_REQUESTED, DEADLINE_EXCEEDED, AUTH_REVOKED, PROTOCOL_ERROR }
    public enum BlockType { @JsonProperty("text") TEXT, @JsonProperty("tool-call") TOOL_CALL }
    public enum FinishKind {
        @JsonProperty("stop") STOP, @JsonProperty("tool-calls") TOOL_CALLS, @JsonProperty("max-tokens") MAX_TOKENS,
        @JsonProperty("aborted") ABORTED, @JsonProperty("error") ERROR
    }

    public record RequestParameters(long maxOutputTokens, BigDecimal temperature, String reasoningEffort,
                                    List<String> stop, Purpose purpose) {
        public RequestParameters { stop = List.copyOf(stop); }
    }

    public record StreamRequest(String schemaVersion, String runId, String stepId, String actionId,
                                String contextManifestId, String modelConfigRevision, RequestParameters requestParameters,
                                long leaseGeneration) {
        public StreamRequest { requireIds(runId, stepId, actionId, contextManifestId, modelConfigRevision); }
    }

    public record CancelRequest(String schemaVersion, String runId, String stepId, String actionId,
                                String invocationId, CancellationReason reasonCode, long leaseGeneration) {
        public CancelRequest { requireIds(runId, stepId, actionId, invocationId); }
    }

    public record InvocationStatus(String invocationId, String originActionId, String contextManifestId,
                                   String modelConfigRevision, ExecutionStatus executionStatus, SettlementStatus settlementStatus,
                                   String providerRequestId, String resultArtifactId, String resultSha256, String failureCode) {
        public InvocationStatus {
            requireIds(invocationId, originActionId, contextManifestId, modelConfigRevision);
            requireNullableId(resultArtifactId);
        }
    }

    public record Failure(String code, String message, Integer providerStatus, String providerRequestId) { }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = TextBlock.class, name = "text"), @JsonSubTypes.Type(value = ToolBlock.class, name = "tool-call")})
    public sealed interface Block permits TextBlock, ToolBlock { String type(); }

    public record TextBlock(String type, String text) implements Block {
        public TextBlock { requireType(type, "text"); }
    }

    public record ToolBlock(String type, String id, String name, String arguments) implements Block {
        public ToolBlock { requireType(type, "tool-call"); }
    }

    // 未取得用量时保留空值，序列化过程中不会转换为零。
    public record Usage(Long uncachedInputTokens, Long cacheReadTokens, Long cacheWriteTokens, Long outputTokens,
                        Long totalTokens, Long reasoningTokens, String sourceReceiptId) {
        public Usage { requireIds(sourceReceiptId); }
    }

    public record FinishReason(FinishKind kind, Failure failure) {
        public FinishReason {
            if (kind == null) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
            boolean failed = kind == FinishKind.ABORTED || kind == FinishKind.ERROR;
            if (failed != (failure != null)) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Accepted.class, name = "accepted"), @JsonSubTypes.Type(value = BlockStart.class, name = "block_start"),
        @JsonSubTypes.Type(value = TextDelta.class, name = "text_delta"), @JsonSubTypes.Type(value = ToolDelta.class, name = "tool_call_delta"),
        @JsonSubTypes.Type(value = BlockEnd.class, name = "block_end"), @JsonSubTypes.Type(value = UsageEvent.class, name = "usage"),
        @JsonSubTypes.Type(value = FinishEvent.class, name = "finish"), @JsonSubTypes.Type(value = ErrorEvent.class, name = "error")
    })
    public sealed interface StreamPayload permits Accepted, BlockStart, TextDelta, ToolDelta, BlockEnd, UsageEvent, FinishEvent, ErrorEvent {
        String type();
    }

    public record Accepted(String type, ExecutionStatus executionStatus) implements StreamPayload {
        public Accepted {
            requireType(type, "accepted");
            if (executionStatus != ExecutionStatus.RESERVED && executionStatus != ExecutionStatus.DISPATCHING
                && executionStatus != ExecutionStatus.IN_FLIGHT) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }

    public record BlockStart(String type, long index, BlockType blockType) implements StreamPayload {
        public BlockStart { requireType(type, "block_start"); }
    }
    public record TextDelta(String type, long index, String text) implements StreamPayload {
        public TextDelta { requireType(type, "text_delta"); }
    }
    public record ToolDelta(String type, long index, String callId, String name, String argumentsDelta) implements StreamPayload {
        public ToolDelta { requireType(type, "tool_call_delta"); }
    }
    public record BlockEnd(String type, long index, Block block) implements StreamPayload {
        public BlockEnd { requireType(type, "block_end"); }
    }
    public record UsageEvent(String type, Usage usage) implements StreamPayload {
        public UsageEvent { requireType(type, "usage"); }
    }

    public record FinishEvent(String type, FinishReason reason, ExecutionStatus executionStatus, SettlementStatus settlementStatus,
                              String resultArtifactId, String resultSha256, String replayStateId) implements StreamPayload {
        public FinishEvent {
            requireType(type, "finish");
            requireIds(resultArtifactId);
            requireNullableId(replayStateId);
            if (reason == null) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
            ExecutionStatus expected = switch (reason.kind()) {
                case STOP, TOOL_CALLS, MAX_TOKENS -> ExecutionStatus.SUCCEEDED;
                case ABORTED -> ExecutionStatus.CANCELLED;
                case ERROR -> ExecutionStatus.FAILED;
            };
            if (executionStatus != expected) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }

    public record ErrorEvent(String type, Failure failure, ExecutionStatus executionStatus, SettlementStatus settlementStatus) implements StreamPayload {
        public ErrorEvent {
            requireType(type, "error");
            if (executionStatus != ExecutionStatus.FAILED && executionStatus != ExecutionStatus.UNKNOWN
                && executionStatus != ExecutionStatus.CANCEL_REQUESTED) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }

    public record StreamEvent(String schemaVersion, String invocationId, String runId, String stepId, String actionId,
                              String contextManifestId, String modelConfigRevision, long sequence, String emittedAt, StreamPayload payload) {
        public StreamEvent {
            requireIds(invocationId, runId, stepId, actionId, contextManifestId, modelConfigRevision);
            WireValues.utcTime(emittedAt);
        }
    }

    public record ModelResult(String schemaVersion, String invocationId, String contextManifestId, String modelConfigRevision,
                              String providerRequestId, List<Block> blocks, Usage usage, FinishReason reason, String replayStateId) {
        public ModelResult {
            requireIds(invocationId, contextManifestId, modelConfigRevision);
            requireNullableId(replayStateId);
            blocks = List.copyOf(blocks);
        }
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
    }

    private static void requireIds(String... values) {
        for (String value : values) WireValues.uuid(value);
    }

    private static void requireNullableId(String value) {
        if (value != null) WireValues.uuid(value);
    }
}
