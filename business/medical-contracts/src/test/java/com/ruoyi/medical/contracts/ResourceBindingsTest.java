package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceBindingsTest {
    private static final ObjectMapper JSON = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static SchemaCatalogue catalogue;
    private static JsonNode common;
    private static JsonNode resources;
    private static JsonNode dataset;
    private static Path root;

    @BeforeAll
    static void load() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        var yaml = new YAMLMapper();
        common = yaml.readTree(Files.readAllBytes(root.resolve("backend-design/interfaces.schema.yaml")));
        resources = yaml.readTree(Files.readAllBytes(root.resolve("backend-design/resource-details.schema.yaml")));
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
    }
    private static Set<String> names(List<TypedSchemaBindings.Definition<?>> definitions) {
        Set<String> result = new HashSet<>();
        for (var definition : definitions) assertTrue(result.add(definition.name()));
        return result;
    }
    private static void references(JsonNode schema, String namespace, Set<String> visited, Set<String> names) {
        if (schema.isObject() && schema.has("$ref")) {
            URI target = URI.create(catalogue.schemaRef(namespace, null)).resolve(schema.required("$ref").asText());
            if (visited.add(target.toString())) {
                String targetNamespace = target.getPath().endsWith("/interfaces.schema.yaml") ? "interfaces" : "resource-details";
                JsonPointer pointer = JsonPointer.compile(target.getFragment());
                JsonNode definition = (targetNamespace.equals("interfaces") ? common : resources).at(pointer);
                assertFalse(definition.isMissingNode());
                if (targetNamespace.equals("interfaces")) names.add(pointer.last().getMatchingProperty());
                references(definition, targetNamespace, visited, names);
            }
        }
        if (schema.isContainerNode()) for (JsonNode child : schema) references(child, namespace, visited, names);
    }

    @Test
    void everyResourceAndReferencedCommonDefinitionHasAnExplicitType() {
        Set<String> expected = new HashSet<>();
        resources.required("$defs").fieldNames().forEachRemaining(expected::add);
        assertEquals(27, expected.size());
        assertEquals(expected, names(ResourceBindings.definitions()));
        Set<String> referenced = new HashSet<>();
        references(resources, "resource-details", new HashSet<>(), referenced);
        assertEquals(16, referenced.size());
        Set<String> commonNames = names(CommonBindings.definitions());
        assertEquals(17, commonNames.size());
        assertTrue(commonNames.remove("NullableHash"));
        assertEquals(referenced, commonNames);
    }

    @Test
    void everyObjectPropertyAndEnumMatchesTheOriginalSchema() {
        int objects = 0;
        for (var group : Map.of("interfaces", CommonBindings.definitions(), "resource-details", ResourceBindings.definitions()).entrySet()) {
            JsonNode definitions = (group.getKey().equals("interfaces") ? common : resources).required("$defs");
            for (var definition : group.getValue()) {
                JsonNode schema = definitions.required(definition.name());
                if (!schema.has("properties")) continue;
                var bean = JSON.getSerializationConfig().introspect(definition.type());
                Set<String> actual = new HashSet<>();
                for (var property : bean.findProperties()) {
                    actual.add(property.getName());
                    JsonNode field = schema.required("properties").required(property.getName());
                    if (field.has("enum")) {
                        Class<?> type = property.getPrimaryType().getRawClass();
                        assertTrue(type.isEnum());
                        Set<String> constants = new HashSet<>();
                        for (Object value : type.getEnumConstants()) constants.add(value.toString());
                        Set<String> expected = new HashSet<>();
                        for (JsonNode value : field.required("enum")) expected.add(value.asText());
                        assertEquals(expected, constants, definition.name() + "." + property.getName());
                    }
                }
                Set<String> expected = new HashSet<>();
                schema.required("properties").fieldNames().forEachRemaining(expected::add);
                assertEquals(expected, actual, definition.name());
                objects++;
            }
        }
        assertEquals(27, objects);
        assertTrue(CommonProtocol.SelectionSegment.class.isSealed());
        Set<String> selections = new HashSet<>();
        for (Class<?> type : CommonProtocol.SelectionSegment.class.getPermittedSubclasses()) selections.add(type.getSimpleName());
        assertEquals(Set.of("TextSelection", "ImageSelection", "TableSelection"), selections);
    }

    @Test
    void actualRegisteredScalarsAndTechnicalIdentityRemainIndependent() throws Exception {
        for (JsonNode source : dataset.required("sources")) {
            assertEquals(source.required("sha256").asText(), TypedSchemaBindings.bind(catalogue, CommonBindings.HASH, source.required("sha256")).read());
            assertEquals(source.required("size_bytes").asText(), TypedSchemaBindings.bind(catalogue, CommonBindings.REVISION, JSON.valueToTree(source.required("size_bytes").asText())).read());
            assertEquals(source.required("size_bytes").asText(), TypedSchemaBindings.bind(catalogue, ResourceBindings.REVISION, JSON.valueToTree(source.required("size_bytes").asText())).read());
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.ID, source.required("source_id")));
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ResourceBindings.ID, source.required("source_id")));
            var text = TypedSchemaBindings.bind(catalogue, ResourceBindings.TEXTS, source.required("allowed_use"));
            var changed = text.read(); changed.clear();
            assertEquals(JSON.convertValue(source.required("allowed_use"), List.class), text.read());
        }
        String execution = UUID.randomUUID().toString();
        assertEquals(execution, TypedSchemaBindings.bind(catalogue, CommonBindings.ID, JSON.valueToTree(execution)).read());
        var ids = TypedSchemaBindings.bind(catalogue, ResourceBindings.IDS, JSON.valueToTree(List.of(execution)));
        ids.read().clear();
        assertEquals(List.of(execution), ids.read());
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.ID, JSON.valueToTree(execution.toUpperCase())));
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ResourceBindings.IDS, JSON.valueToTree(List.of(execution.toUpperCase()))));
        assertNull(TypedSchemaBindings.bind(catalogue, CommonBindings.NULLABLE_ID, JSON.nullNode()).read());
        assertNull(TypedSchemaBindings.bind(catalogue, CommonBindings.NULLABLE_HASH, JSON.nullNode()).read());
        assertNull(TypedSchemaBindings.bind(catalogue, CommonBindings.NULLABLE_TEXT, JSON.nullNode()).read());
        String maximum = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE).toString();
        assertEquals(maximum, TypedSchemaBindings.bind(catalogue, ResourceBindings.REVISION, JSON.valueToTree(maximum)).read());
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ResourceBindings.REVISION, JSON.valueToTree(BigInteger.ONE.shiftLeft(64).toString())));
        assertEquals(dataset.required("created_at").asText(), TypedSchemaBindings.bind(catalogue, CommonBindings.TIMESTAMP, dataset.required("created_at")).read());
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.TIMESTAMP, JSON.valueToTree(dataset.required("created_at").asText().replace("+00:00", "+08:00"))));
        for (var definition : CommonBindings.definitions()) assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, definition, dataset));
        for (var definition : ResourceBindings.definitions()) assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, definition, dataset));
    }

    @Test
    void actualWholeFileReadRetainsCoverageAndRejectsAnIncompleteCursor() throws Exception {
        for (JsonNode source : dataset.required("sources")) {
            byte[] bytes = Files.readAllBytes(root.resolve(source.required("source_ref").asText()));
            String digest = WireValues.sha256(bytes);
            assertEquals(source.required("sha256").asText(), digest);
            var coverage = JSON.createObjectNode().put("selection_sha256", digest).put("delivered_sha256", digest).put("complete", true).putNull("next_cursor");
            var bound = TypedSchemaBindings.bind(catalogue, CommonBindings.COVERAGE, coverage);
            assertTrue(bound.read().complete());
            assertEquals(digest, bound.read().selectionSha256());
            assertNull(bound.read().nextCursor());
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.COVERAGE, coverage.deepCopy().put("complete", false)));
        }
    }
}
