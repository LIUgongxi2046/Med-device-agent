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
    static int publishedDefinitionCount(java.nio.file.Path root) throws Exception {
        var files = new java.util.ArrayList<java.nio.file.Path>();
        files.add(root.resolve("contracts/catalogue.json"));
        try (var entries = java.nio.file.Files.list(root.resolve("contracts/extensions"))) {
            entries.filter(p -> p.getFileName().toString().endsWith(".catalogue.json")).forEach(files::add);
        }
        var names = new HashSet<String>(); int count = 0;
        for (var file : files) for (var entry : JSON.readTree(java.nio.file.Files.readAllBytes(file)).required("schemas")) {
            assertTrue(names.add(entry.required("name").textValue()), "Duplicate schema publication");
            String path = entry.required("path").textValue();
            byte[] bytes = java.nio.file.Files.readAllBytes(root.resolve(path));
            assertEquals(entry.required("sha256").textValue(), WireValues.sha256(bytes));
            var document = (path.endsWith(".yaml") ? new com.fasterxml.jackson.dataformat.yaml.YAMLMapper() : JSON).readTree(bytes);
            if (document.has("$defs")) assertTrue(document.get("$defs").isObject());
            count += document.path("$defs").size();
        }
        return count;
    }
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
            // EvidenceRef is the explicit versioned V1/V2 Java union. Frozen V1
            // validation still rejects the two V2-only kinds (SourceEvidenceV2Test).
            if (type.getRawClass() == DatasetProtocol.LocatorKind.class
                    && expected.equals(Set.of(JSON.valueToTree("PDF"), JSON.valueToTree("DOCX"), JSON.valueToTree("XLSX"), JSON.valueToTree("CSV"), JSON.valueToTree("TXT"), JSON.valueToTree("MARKDOWN"), JSON.valueToTree("PPTX")))) {
                expected.add(JSON.valueToTree("IMAGE")); expected.add(JSON.valueToTree("HTML"));
            }
            assertEquals(expected, actual, label);
        }
        if (schema.has("items")) { assertTrue(type.isCollectionLikeType(), label); enumField(schema.required("items"), type.getContentType(), label); }
    }
}
