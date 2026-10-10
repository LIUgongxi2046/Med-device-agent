package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.contracts.SchemaTypeAssertions.JSON;

class InterfaceBindingsTest {
    private static SchemaCatalogue catalogue;
    private static JsonNode schema, dataset;
    @BeforeAll static void load() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        schema = new YAMLMapper().readTree(Files.readAllBytes(root.resolve("backend-design/interfaces.schema.yaml")));
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
    }

    @Test void everyOriginalInterfaceAndInheritedObjectHasAnExplicitBinding() {
        Set<String> expected = new HashSet<>(); schema.required("$defs").fieldNames().forEachRemaining(expected::add);
        Set<String> actual = new HashSet<>(); int objects = 0;
        for (var definition : InterfaceBindings.definitions()) {
            assertEquals("interfaces", definition.schema()); assertTrue(actual.add(definition.name()));
            JsonNode item = schema.required("$defs").required(definition.name());
            if (item.has("properties")) { SchemaTypeAssertions.object(schema, item, definition.type()); objects++; }
        }
        assertEquals(104, expected.size()); assertEquals(expected, actual); assertEquals(95, objects);
        for (var definition : CommonBindings.definitions()) assertTrue(InterfaceBindings.definitions().contains(definition));
        assertTrue(InterfaceProtocol.NodeContent.class.isSealed()); assertEquals(5, InterfaceProtocol.NodeContent.class.getPermittedSubclasses().length);
        assertTrue(InterfaceProtocol.EventPayload.class.isSealed()); assertEquals(5, InterfaceProtocol.EventPayload.class.getPermittedSubclasses().length);
    }

    @Test void allNineEventMappingsMatchTheOriginalConditionalBranches() {
        JsonNode branches = schema.required("$defs").required("PublicEvent").required("oneOf");
        assertEquals(9, branches.size()); assertEquals(9, InterfaceProtocol.EventType.values().length);
        for (var type : InterfaceProtocol.EventType.values()) {
            String name = JSON.valueToTree(type).asText(); JsonNode found = null;
            for (JsonNode branch : branches) if (name.equals(branch.required("properties").required("event_type").required("const").asText())) found = branch;
            assertNotNull(found, name);
            JsonNode properties = found.required("properties"); var mapping = InterfaceProtocol.eventMapping(type);
            assertEquals(properties.required("resource_kind").required("const").asText(), mapping.resourceKind().name());
            JsonNode payload = properties.required("payload"); String reference = payload.required("$ref").asText();
            assertEquals(JsonPointer.compile(reference.substring(1)).last().getMatchingProperty(), mapping.payloadType().getSimpleName());
            JsonNode read = payload.has("properties") ? payload.required("properties").required("read_operation_id")
                : schema.at(JsonPointer.compile(reference.substring(1))).required("properties").required("read_operation_id");
            assertEquals(read.required("const").asText(), mapping.readOperationId());
        }
    }

    @Test void everyReferencedFieldKeepsTheOriginalJavaTypeAndGenericMember() {
        Map<URI, JavaType> types = new HashMap<>();
        for (var definition : InterfaceBindings.definitions()) types.put(URI.create(catalogue.schemaRef(definition.schema(), definition.name())), definition.type());
        for (var definition : DatasetBindings.definitions()) types.put(URI.create(catalogue.schemaRef(definition.schema(), definition.name())), definition.type());
        URI base = URI.create(catalogue.schemaRef("interfaces", null));
        for (var definition : InterfaceBindings.definitions()) {
            var properties = SchemaTypeAssertions.properties(schema, schema.required("$defs").required(definition.name()));
            for (var property : JSON.getSerializationConfig().introspect(definition.type()).findProperties())
                references(properties.get(property.getName()), property.getPrimaryType(), base, types);
        }
    }

    private static void references(JsonNode field, JavaType type, URI base, Map<URI, JavaType> types) {
        if (field.isBoolean()) { assertTrue(field.booleanValue()); assertEquals(JsonNode.class, type.getRawClass()); return; }
        if (field.has("$ref")) {
            JavaType expected = types.get(base.resolve(field.required("$ref").asText()));
            assertNotNull(expected);
            if (type.getRawClass().isSealed()) assertTrue(type.getRawClass().isAssignableFrom(expected.getRawClass()), type + " / " + expected);
            else assertEquals(expected, type);
        }
        if (field.has("items")) { assertTrue(type.isCollectionLikeType()); references(field.required("items"), type.getContentType(), base, types); }
        if (field.has("oneOf")) for (JsonNode branch : field.required("oneOf")) references(branch, type, base, types);
    }

    @Test void originalReferencesAndExtensibleBasesRetainTheirDeclaredMeaning() throws Exception {
        assertEquals(DatasetProtocol.EvidenceRef.class, InterfaceProtocol.EvidenceLink.class.getRecordComponents()[3].getType());
        assertEquals(DatasetProtocol.EvidenceRef.class, InterfaceProtocol.InternalSourceReadRequest.class.getRecordComponents()[4].getType());
        assertEquals(String.class, InterfaceProtocol.OperationLookup.class.getRecordComponents()[0].getType());
        assertEquals(String.class, InterfaceProtocol.ManagementOperation.class.getRecordComponents()[0].getType());
        assertEquals(String.class, InterfaceProtocol.ContextRequest.class.getRecordComponents()[9].getType());
        assertNotNull(InterfaceProtocol.OpenObject.class.getDeclaredMethod("additional", String.class, JsonNode.class).getAnnotation(JsonAnySetter.class));
        assertNotNull(InterfaceProtocol.OpenObject.class.getDeclaredMethod("additionalProperties").getAnnotation(JsonAnyGetter.class));
        for (var definition : java.util.List.of(InterfaceBindings.REVIEW_BASELINE, InterfaceBindings.STEP_IDENTITY, InterfaceBindings.ACTION_IDENTITY)) {
            JsonNode item = schema.required("$defs").required(definition.name());
            assertFalse(item.has("additionalProperties")); assertFalse(item.has("unevaluatedProperties"));
            assertTrue(InterfaceProtocol.OpenObject.class.isAssignableFrom(definition.type().getRawClass()));
        }
        for (var definition : InterfaceBindings.definitions()) assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, definition, dataset));
    }

    @Test void actualRegistrationReadUsesAnIndependentTechnicalEnvelope() throws Exception {
        String execution = UUID.randomUUID().toString();
        var envelope = JSON.createObjectNode().put("schema_version", "1.0.0").put("request_id", execution).putNull("resource_revision").set("data", dataset.deepCopy());
        var bound = TypedSchemaBindings.bind(catalogue, InterfaceBindings.ENVELOPE, envelope);
        assertEquals(execution, bound.read().requestId()); assertNull(bound.read().resourceRevision());
        assertEquals(dataset, bound.read().data());
        ((com.fasterxml.jackson.databind.node.ObjectNode) bound.read().data()).remove("sources");
        assertEquals(dataset, bound.read().data());
        var nullData = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.deepCopy()).putNull("data");
        assertTrue(TypedSchemaBindings.bind(catalogue, InterfaceBindings.ENVELOPE, nullData).read().data().isNull());
        var overflow = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.deepCopy()).put("resource_revision", java.math.BigInteger.ONE.shiftLeft(64).toString());
        var failure = assertThrows(ValueInstantiationException.class, () -> TypedSchemaBindings.bind(catalogue, InterfaceBindings.ENVELOPE, overflow));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("revision 超出 BIGINT UNSIGNED 范围", failure.getCause().getMessage());
    }
}
