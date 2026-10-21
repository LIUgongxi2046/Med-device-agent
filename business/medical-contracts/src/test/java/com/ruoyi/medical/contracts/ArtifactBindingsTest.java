package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.contracts.SchemaTypeAssertions.JSON;

class ArtifactBindingsTest {
    private static SchemaCatalogue catalogue;
    private static JsonNode schema, dataset;
    private static Path root;
    @BeforeAll static void load() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        schema = JSON.readTree(Files.readAllBytes(root.resolve("agent-design/artifacts.schema.json")));
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
    }

    @Test void allArtifactDefinitionsObjectsReferencesAndGenericsMatchTheOriginalSchema() {
        Set<String> expected = new HashSet<>(); schema.required("$defs").fieldNames().forEachRemaining(expected::add);
        Set<String> actual = new HashSet<>(); Map<URI, JavaType> types = new HashMap<>();
        for (var definition : ArtifactBindings.definitions()) {
            assertEquals("artifacts", definition.schema()); assertTrue(actual.add(definition.name()));
            types.put(URI.create(catalogue.schemaRef(definition.schema(), definition.name())), definition.type());
        }
        for (var definition : DatasetBindings.definitions()) types.put(URI.create(catalogue.schemaRef(definition.schema(), definition.name())), definition.type());
        assertEquals(49, expected.size()); assertEquals(expected, actual);
        int objects = 1;
        object(schema, ArtifactBindings.ARTIFACT.type(), types);
        for (var definition : ArtifactBindings.definitions()) {
            JsonNode item = schema.required("$defs").required(definition.name());
            if (item.has("properties")) { object(item, definition.type(), types); objects++; }
            else references(item, definition.type(), types);
        }
        assertEquals(37, objects);
        assertEquals(DatasetProtocol.EvidenceRef.class, ArtifactBindings.EVIDENCE_REF.type().getRawClass());
        assertEquals(DatasetProtocol.EvidenceRef.class, ArtifactBindings.EVIDENCE_REFS.type().getContentType().getRawClass());
        assertEquals(long.class, ArtifactProtocol.ObjectRef.class.getRecordComponents()[3].getType());
    }

    private static void object(JsonNode item, JavaType type, Map<URI, JavaType> types) {
        SchemaTypeAssertions.object(schema, item, type);
        var properties = SchemaTypeAssertions.properties(schema, item);
        for (var property : JSON.getSerializationConfig().introspect(type).findProperties()) references(properties.get(property.getName()), property.getPrimaryType(), types);
    }
    private static void references(JsonNode field, JavaType type, Map<URI, JavaType> types) {
        if (field.has("$ref")) {
            JavaType expected = types.get(URI.create(catalogue.schemaRef("artifacts", null)).resolve(field.required("$ref").asText()));
            assertNotNull(expected);
            if (type.getRawClass().isSealed()) assertTrue(type.getRawClass().isAssignableFrom(expected.getRawClass()));
            else assertEquals(expected, type);
        }
        if (field.has("items")) { assertTrue(type.isCollectionLikeType()); references(field.required("items"), type.getContentType(), types); }
        if (field.has("oneOf")) for (JsonNode branch : field.required("oneOf")) references(branch, type, types);
        if (field.has("anyOf")) for (JsonNode branch : field.required("anyOf")) references(branch, type, types);
        if (field.has("type") && field.required("type").isTextual() && field.required("type").asText().equals("integer")) assertEquals(long.class, type.getRawClass());
        if (field.has("type") && field.required("type").isTextual() && field.required("type").asText().equals("boolean")) assertEquals(boolean.class, type.getRawClass());
    }

    @Test void allOutputAndAnchorSubtypesKeepTheirOriginalDiscriminatorAndContentType() {
        union("ArtifactOutput", ArtifactProtocol.ArtifactOutput.class, "artifact_kind");
        union("Anchor", ArtifactProtocol.Anchor.class, "kind");
    }
    private static void union(String name, Class<?> type, String discriminator) {
        JsonNode branches = schema.required("$defs").required(name).required("oneOf");
        assertTrue(type.isSealed()); assertEquals(branches.size(), type.getPermittedSubclasses().length);
        var subtypes = type.getAnnotation(JsonSubTypes.class).value(); assertEquals(branches.size(), subtypes.length);
        for (JsonNode branch : branches) {
            String target = JsonPointer.compile(branch.required("$ref").asText().substring(1)).last().getMatchingProperty();
            JsonNode properties = schema.required("$defs").required(target).required("properties");
            String kind = properties.required(discriminator).required("const").asText(); Class<?> found = null;
            for (var subtype : subtypes) if (subtype.name().equals(kind)) found = subtype.value();
            assertNotNull(found, kind); assertEquals(target, found.getSimpleName());
            if (properties.has("content")) {
                String content = JsonPointer.compile(properties.required("content").required("$ref").asText().substring(1)).last().getMatchingProperty();
                assertEquals(content, found.getRecordComponents()[1].getType().getSimpleName());
            }
        }
    }

    @Test void actualRegisteredScalarsRetainTheirMeaningAndRejectInvalidRepresentations() throws Exception {
        for (JsonNode source : dataset.required("sources")) {
            assertEquals(source.required("sha256").asText(), TypedSchemaBindings.bind(catalogue, ArtifactBindings.HASH, source.required("sha256")).read());
            assertEquals(source.required("source_id").asText(), TypedSchemaBindings.bind(catalogue, ArtifactBindings.TEXT, source.required("source_id")).read());
            String size = source.required("size_bytes").asText();
            assertEquals(size, TypedSchemaBindings.bind(catalogue, ArtifactBindings.DECIMAL, JSON.valueToTree(size)).read());
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ArtifactBindings.DECIMAL, JSON.valueToTree(size + ".0000000000001")));
            var keys = TypedSchemaBindings.bind(catalogue, ArtifactBindings.KEYS, source.required("allowed_use"));
            keys.read().clear(); assertEquals(JSON.convertValue(source.required("allowed_use"), List.class), keys.read());
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ArtifactBindings.ID, source.required("source_id")));
        }
        String execution = UUID.randomUUID().toString();
        assertEquals(execution, TypedSchemaBindings.bind(catalogue, ArtifactBindings.ID, JSON.valueToTree(execution)).read());
        var ids = TypedSchemaBindings.bind(catalogue, ArtifactBindings.IDS, JSON.valueToTree(List.of(execution)));
        ids.read().clear(); assertEquals(List.of(execution), ids.read());
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ArtifactBindings.ID, JSON.valueToTree(execution.toUpperCase())));
        assertNull(TypedSchemaBindings.bind(catalogue, ArtifactBindings.NULLABLE_ID, JSON.nullNode()).read());
        assertNull(TypedSchemaBindings.bind(catalogue, ArtifactBindings.NULLABLE_TEXT, JSON.nullNode()).read());
        assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, ArtifactBindings.ARTIFACT, dataset));
        for (var definition : ArtifactBindings.definitions()) assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, definition, dataset));
    }

    @Test void originalTypedCollectionsAndBusinessPublicationCoverAllDefinitions() throws Exception {
        Set<String> actual = new HashSet<>();
        for (var group : List.of(DatasetBindings.definitions(), InterfaceBindings.definitions(), ResourceBindings.definitions(), ArtifactBindings.definitions()))
            for (var definition : group) assertTrue(actual.add(catalogue.schemaRef(definition.schema(), definition.name())));
        for (var definition : ModelStreamBindings.definitions()) assertTrue(actual.add(catalogue.schemaRef("model-stream", definition.name())));
        Set<String> expected = new HashSet<>();
        Set<String> publishedTemplates = new HashSet<>();
        JsonNode manifest = SchemaCatalogue.publishedManifest(Files.readAllBytes(root.resolve("contracts/catalogue.json")), Files.readAllBytes(root.resolve(SchemaCatalogue.EXTENSION_PATH)));
        for (JsonNode entry : manifest.required("schemas")) {
            String path = entry.required("path").asText();
            JsonNode document = (path.endsWith(".yaml") ? new YAMLMapper() : JSON).readTree(Files.readAllBytes(root.resolve(path)));
            String name = entry.required("name").asText();
            var definitions = document.required("$defs").fieldNames();
            while (definitions.hasNext()) {
                String definition = definitions.next();
                String ref = catalogue.schemaRef(name, definition);
                assertTrue(expected.add(ref));
                if (name.equals("templates") || name.equals("template-operations") || name.equals("tasks") || name.equals("collections")
                        || name.equals("rule-expressions") || name.equals("template-applicability") || name.equals("agent-release") || name.equals("task-plan-view") || name.equals("evaluation-results")) {
                    var identity = catalogue.requirePublished(ref, entry.required("sha256").textValue());
                    assertEquals(name, identity.name()); assertEquals(definition, identity.definition());
                    assertTrue(publishedTemplates.add(ref));
                }
            }
        }
        assertEquals(218, actual.size());
        assertEquals(107, publishedTemplates.size());
        assertTrue(java.util.Collections.disjoint(actual, publishedTemplates));
        actual.addAll(publishedTemplates);
        assertEquals(325, expected.size()); assertEquals(expected, actual); assertEquals(SchemaTypeAssertions.publishedDefinitionCount(root), catalogue.definitionCount());
    }
}
