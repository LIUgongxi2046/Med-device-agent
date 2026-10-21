package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;

public final class TypedSchemaBindings {
    private static final ObjectMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private TypedSchemaBindings() { }
    public enum ScalarWire { NONE, UUID, NULLABLE_UUID, UUIDS, REVISION, TIMESTAMP, MEASUREMENT_DECIMAL }
    public static final class Definition<T> {
        private final String schema;
        private final String name;
        private final JavaType type;
        private final ScalarWire wire;
        private Definition(String schema, String name, JavaType type, ScalarWire wire) {
            this.schema = schema; this.name = name; this.type = type; this.wire = wire;
        }
        public String schema() { return schema; }
        public String name() { return name; }
        public JavaType type() { return type; }
    }
    public static <T> Definition<T> definition(String schema, String name, Class<T> type) {
        return new Definition<>(schema, name, JSON.constructType(type), ScalarWire.NONE);
    }
    public static <T> Definition<T> definition(String schema, String name, TypeReference<T> type, ScalarWire wire) {
        return new Definition<>(schema, name, JSON.constructType(type), wire);
    }
    public static <T> Definition<T> definition(String schema, String name, Class<T> type, ScalarWire wire) {
        return new Definition<>(schema, name, JSON.constructType(type), wire);
    }
    public static <T> Definition<T> withSchema(Definition<T> original, String schema) {
        return new Definition<>(schema, original.name, original.type, original.wire);
    }
    public static final class Binding<T> {
        private final Definition<T> definition;
        private final SchemaCatalogue.ValidatedPayload payload;
        private Binding(Definition<T> definition, SchemaCatalogue.ValidatedPayload payload) { this.definition = definition; this.payload = payload; }
        public Definition<T> definition() { return definition; }
        public SchemaCatalogue.ValidatedPayload payload() { return payload; }
        public T read() throws IOException { return JSON.readValue(payload.canonicalJson(), definition.type()); }
    }
    public static <T> Binding<T> bind(SchemaCatalogue catalogue, Definition<T> definition, JsonNode value) throws IOException {
        var saved = catalogue.bind(definition.schema(), value, definition.name());
        // 标量表示与 DTO 构造校验分别执行；保存内容仅由验证后的规范 JSON 维护。
        switch (definition.wire) {
            case NONE -> { }
            case UUID -> WireValues.uuid(value.textValue());
            case NULLABLE_UUID -> { if (!value.isNull()) WireValues.uuid(value.textValue()); }
            case UUIDS -> { for (JsonNode id : value) WireValues.uuid(id.textValue()); }
            case REVISION -> WireValues.revision(value.textValue());
            case TIMESTAMP -> WireValues.utcTime(value.textValue());
            case MEASUREMENT_DECIMAL -> WireValues.decimal(value.textValue(), 38, 12);
        }
        var binding = new Binding<>(definition, saved);
        T typed = binding.read();
        var restored = catalogue.bind(definition.schema(), JSON.valueToTree(typed), definition.name());
        if (!saved.canonicalJson().equals(restored.canonicalJson())) throw new IllegalArgumentException("DTO_ROUND_TRIP_CHANGED");
        return binding;
    }
}
