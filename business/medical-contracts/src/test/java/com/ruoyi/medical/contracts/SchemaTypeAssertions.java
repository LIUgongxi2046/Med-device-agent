package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class SchemaTypeAssertions {
    static final ObjectMapper JSON = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private SchemaTypeAssertions() { }

    static Map<String, JsonNode> properties(JsonNode root, JsonNode schema) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (schema.has("allOf")) for (JsonNode member : schema.required("allOf")) {
            JsonNode inherited = member.has("$ref") ? root.at(JsonPointer.compile(member.required("$ref").asText().substring(1))) : member;
            assertFalse(inherited.isMissingNode());
            properties(root, inherited).forEach((name, value) -> assertNull(result.put(name, value), name));
        }
        if (schema.has("properties")) schema.required("properties").fields().forEachRemaining(entry -> assertNull(result.put(entry.getKey(), entry.getValue()), entry.getKey()));
        return result;
    }

    static void object(JsonNode root, JsonNode schema, JavaType type) {
        var expected = properties(root, schema);
        Set<String> actual = new HashSet<>();
        for (var property : JSON.getSerializationConfig().introspect(type).findProperties()) {
            actual.add(property.getName());
            JsonNode field = expected.get(property.getName());
            assertNotNull(field, type + "." + property.getName());
            enumField(field, property.getPrimaryType(), type + "." + property.getName());
        }
        assertEquals(expected.keySet(), actual, type.toString());
        assertEquals(expected.keySet(), required(root, schema), "全部当前对象字段均需提供");
    }

    private static Set<String> required(JsonNode root, JsonNode schema) {
        Set<String> result = new HashSet<>();
        if (schema.has("allOf")) for (JsonNode member : schema.required("allOf")) {
            JsonNode inherited = member.has("$ref") ? root.at(JsonPointer.compile(member.required("$ref").asText().substring(1))) : member;
            result.addAll(required(root, inherited));
        }
        if (schema.has("required")) for (JsonNode field : schema.required("required")) result.add(field.asText());
        return result;
    }

    private static void enumField(JsonNode schema, JavaType type, String label) {
        if (schema.has("enum") && schema.required("enum").get(0).isTextual()) {
            assertTrue(type.isEnumType(), label);
            Set<JsonNode> actual = new HashSet<>();
            for (Object value : type.getRawClass().getEnumConstants()) actual.add(JSON.valueToTree(value));
            Set<JsonNode> expected = new HashSet<>();
            for (JsonNode value : schema.required("enum")) expected.add(value);
            assertEquals(expected, actual, label);
        }
        if (schema.has("items")) { assertTrue(type.isCollectionLikeType(), label); enumField(schema.required("items"), type.getContentType(), label); }
    }
}
