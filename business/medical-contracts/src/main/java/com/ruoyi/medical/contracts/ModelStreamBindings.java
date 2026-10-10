package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.List;

public final class ModelStreamBindings {
    private static final ObjectMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    private ModelStreamBindings() { }

    public static final class Definition<T> {
        private final String name;
        private final Class<T> type;
        private Definition(String name, Class<T> type) { this.name = name; this.type = type; }
        public String name() { return name; }
        public Class<T> type() { return type; }
    }

    public static final Definition<String> ID = new Definition<>("Id", String.class);
    public static final Definition<String> TEXT = new Definition<>("Text", String.class);
    public static final Definition<String> NULLABLE_TEXT = new Definition<>("NullableText", String.class);
    public static final Definition<String> HASH = new Definition<>("Hash", String.class);
    public static final Definition<Long> TOKEN_COUNT = new Definition<>("TokenCount", Long.class);
    public static final Definition<ModelProtocol.StreamRequest> STREAM_REQUEST = new Definition<>("StreamRequest", ModelProtocol.StreamRequest.class);
    public static final Definition<ModelProtocol.RequestParameters> REQUEST_PARAMETERS = new Definition<>("RequestParameters", ModelProtocol.RequestParameters.class);
    public static final Definition<ModelProtocol.CancelRequest> CANCEL_REQUEST = new Definition<>("CancelRequest", ModelProtocol.CancelRequest.class);
    public static final Definition<ModelProtocol.InvocationStatus> INVOCATION_STATUS = new Definition<>("InvocationStatus", ModelProtocol.InvocationStatus.class);
    public static final Definition<ModelProtocol.Failure> FAILURE = new Definition<>("Failure", ModelProtocol.Failure.class);
    public static final Definition<ModelProtocol.TextBlock> TEXT_BLOCK = new Definition<>("TextBlock", ModelProtocol.TextBlock.class);
    public static final Definition<ModelProtocol.ToolBlock> TOOL_BLOCK = new Definition<>("ToolBlock", ModelProtocol.ToolBlock.class);
    public static final Definition<ModelProtocol.Block> BLOCK = new Definition<>("Block", ModelProtocol.Block.class);
    public static final Definition<ModelProtocol.Usage> USAGE = new Definition<>("Usage", ModelProtocol.Usage.class);
    public static final Definition<ModelProtocol.FinishReason> FINISH_REASON = new Definition<>("FinishReason", ModelProtocol.FinishReason.class);
    public static final Definition<ModelProtocol.Accepted> ACCEPTED = new Definition<>("Accepted", ModelProtocol.Accepted.class);
    public static final Definition<ModelProtocol.BlockStart> BLOCK_START = new Definition<>("BlockStart", ModelProtocol.BlockStart.class);
    public static final Definition<ModelProtocol.TextDelta> TEXT_DELTA = new Definition<>("TextDelta", ModelProtocol.TextDelta.class);
    public static final Definition<ModelProtocol.ToolDelta> TOOL_DELTA = new Definition<>("ToolDelta", ModelProtocol.ToolDelta.class);
    public static final Definition<ModelProtocol.BlockEnd> BLOCK_END = new Definition<>("BlockEnd", ModelProtocol.BlockEnd.class);
    public static final Definition<ModelProtocol.UsageEvent> USAGE_EVENT = new Definition<>("UsageEvent", ModelProtocol.UsageEvent.class);
    public static final Definition<ModelProtocol.FinishEvent> FINISH_EVENT = new Definition<>("FinishEvent", ModelProtocol.FinishEvent.class);
    public static final Definition<ModelProtocol.ErrorEvent> ERROR_EVENT = new Definition<>("ErrorEvent", ModelProtocol.ErrorEvent.class);
    public static final Definition<ModelProtocol.StreamEvent> STREAM_EVENT = new Definition<>("StreamEvent", ModelProtocol.StreamEvent.class);
    public static final Definition<ModelProtocol.ModelResult> MODEL_RESULT = new Definition<>("ModelResult", ModelProtocol.ModelResult.class);

    public static List<Definition<?>> definitions() {
        return List.of(ID, TEXT, NULLABLE_TEXT, HASH, TOKEN_COUNT, STREAM_REQUEST, REQUEST_PARAMETERS, CANCEL_REQUEST, INVOCATION_STATUS,
            FAILURE, TEXT_BLOCK, TOOL_BLOCK, BLOCK, USAGE, FINISH_REASON, ACCEPTED, BLOCK_START, TEXT_DELTA, TOOL_DELTA, BLOCK_END,
            USAGE_EVENT, FINISH_EVENT, ERROR_EVENT, STREAM_EVENT, MODEL_RESULT);
    }

    public static final class Binding<T> {
        private final Definition<T> definition;
        private final SchemaCatalogue.ValidatedPayload payload;
        private Binding(Definition<T> definition, SchemaCatalogue.ValidatedPayload payload) { this.definition = definition; this.payload = payload; }
        public Definition<T> definition() { return definition; }
        public SchemaCatalogue.ValidatedPayload payload() { return payload; }
        public T read() throws IOException { return JSON.readValue(payload.canonicalJson(), definition.type()); }
    }

    public static <T> Binding<T> bind(SchemaCatalogue catalogue, Definition<T> definition, JsonNode input) throws IOException {
        var saved = catalogue.bind("model-stream", input, definition.name());
        if (definition == ID) WireValues.uuid(input.textValue());
        var binding = new Binding<>(definition, saved);
        T typed = binding.read();
        // 检查 Jackson 类型映射完整保留原字段、空值与规范内容。
        var restored = catalogue.bind("model-stream", JSON.valueToTree(typed), definition.name());
        if (!saved.canonicalJson().equals(restored.canonicalJson())) throw new IllegalArgumentException("DTO_ROUND_TRIP_CHANGED");
        return binding;
    }
}
