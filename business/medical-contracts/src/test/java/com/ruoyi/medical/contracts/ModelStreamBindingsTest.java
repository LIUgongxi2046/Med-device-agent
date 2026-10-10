package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelStreamBindingsTest {
    private static final ObjectMapper JSON = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static SchemaCatalogue catalogue;
    private static JsonNode definitions;
    private static JsonNode dataset;

    @BeforeAll
    static void load() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        definitions = JSON.readTree(Files.readAllBytes(root.resolve("agent-design/model-stream.schema.json"))).required("$defs");
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
    }

    @Test
    void everySchemaDefinitionHasAnExplicitJavaType() {
        Set<String> actual = new HashSet<>();
        for (var definition : ModelStreamBindings.definitions()) assertTrue(actual.add(definition.name()));
        Set<String> expected = new HashSet<>();
        definitions.fieldNames().forEachRemaining(expected::add);
        assertEquals(expected, actual);
    }

    @Test
    void recordPropertiesMatchEveryObjectSchema() {
        for (var definition : ModelStreamBindings.definitions()) {
            JsonNode schema = definitions.required(definition.name());
            if (!schema.has("properties")) continue;
            var bean = JSON.getSerializationConfig().introspect(JSON.constructType(definition.type()));
            Set<String> actual = new HashSet<>();
            for (var property : bean.findProperties()) actual.add(property.getName());
            Set<String> expected = new HashSet<>();
            schema.required("properties").fieldNames().forEachRemaining(expected::add);
            assertEquals(expected, actual, definition.name());
        }
    }

    @Test
    void actualRegisteredFieldsRetainIdentityAndRejectIncorrectDomains() throws Exception {
        for (JsonNode source : dataset.required("sources")) {
            var text = ModelStreamBindings.bind(catalogue, ModelStreamBindings.TEXT, source.required("source_id"));
            assertEquals(source.required("source_id").asText(), text.read());
            assertEquals(source.required("sha256").asText(), ModelStreamBindings.bind(catalogue, ModelStreamBindings.HASH, source.required("sha256")).read());
            assertEquals(source.required("size_bytes").asLong(), ModelStreamBindings.bind(catalogue, ModelStreamBindings.TOKEN_COUNT, source.required("size_bytes")).read());
            assertEquals(source.required("parent_source_id").isNull() ? null : source.required("parent_source_id").asText(),
                ModelStreamBindings.bind(catalogue, ModelStreamBindings.NULLABLE_TEXT, source.required("parent_source_id")).read());
            assertThrows(java.io.IOException.class, () -> ModelStreamBindings.bind(catalogue, ModelStreamBindings.TEXT,
                JSON.valueToTree(source.required("source_id").asText() + "\ud800")));
            assertThrows(IllegalArgumentException.class, () -> ModelStreamBindings.bind(catalogue, ModelStreamBindings.ID, source.required("source_id")));
        }
        String execution = UUID.randomUUID().toString();
        assertEquals(execution, ModelStreamBindings.bind(catalogue, ModelStreamBindings.ID, JSON.valueToTree(execution)).read());
        assertThrows(IllegalArgumentException.class, () -> ModelStreamBindings.bind(catalogue, ModelStreamBindings.ID, JSON.valueToTree(execution.toUpperCase())));
        for (var definition : ModelStreamBindings.definitions()) {
            assertThrows(IllegalArgumentException.class, () -> ModelStreamBindings.bind(catalogue, definition, dataset));
        }
    }
}
