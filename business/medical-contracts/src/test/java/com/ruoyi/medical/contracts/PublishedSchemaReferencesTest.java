package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublishedSchemaReferencesTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path root;
    private static SchemaCatalogue catalogue;
    private static JsonNode manifest, dataset;
    private static byte[] datasetBytes;
    private static String reference, digest;

    @BeforeAll static void load() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        manifest = JSON.readTree(Files.readAllBytes(root.resolve("contracts/catalogue.json")));
        datasetBytes = Files.readAllBytes(root.resolve("data-design/sample_dataset.json"));
        dataset = JSON.readTree(datasetBytes);
        reference = catalogue.schemaRef("dataset", null);
        digest = manifest.required("schemas").get(0).required("sha256").textValue();
    }

    private static SchemaCatalogue.ValidatedPayload bind(String ref, String hash, byte[] body) throws Exception {
        return catalogue.bindPublished(ref, hash, body);
    }

    private static Throwable rejection(String ref, String hash, byte[] body) {
        return assertThrows(Exception.class, () -> catalogue.bindPublished(ref, hash, body));
    }

    @Test void everyPublishedDocumentAndDefinitionRetainsItsExactOriginalIdentity() throws Exception {
        int count = 0;
        for (JsonNode entry : manifest.required("schemas")) {
            String name = entry.required("name").textValue(), path = entry.required("path").textValue();
            byte[] bytes = Files.readAllBytes(root.resolve(path));
            String hash = WireValues.sha256(bytes);
            assertEquals(entry.required("sha256").textValue(), hash);
            JsonNode document = (path.endsWith(".yaml") ? new YAMLMapper() : JSON).readTree(bytes);
            var identity = catalogue.requirePublished(catalogue.schemaRef(name, null), hash);
            assertEquals(name, identity.name());
            assertNull(identity.definition());
            assertEquals(hash, identity.schemaSha256());
            assertEquals(catalogue.schemaRef(name, null), identity.schemaRef());
            count++;
            var definitions = document.required("$defs").fieldNames();
            while (definitions.hasNext()) {
                String definition = definitions.next(), ref = catalogue.schemaRef(name, definition);
                var fixed = catalogue.requirePublished(ref, hash);
                assertEquals(name, fixed.name());
                assertEquals(definition, fixed.definition());
                assertEquals(ref, fixed.schemaRef());
                assertEquals(hash, fixed.schemaSha256());
                count++;
            }
        }
        assertEquals(320, count);
    }

    @Test void actualCompleteDatasetAndOriginalLocatorsUseTheFixedPublishedReference() throws Exception {
        var actual = bind(reference, digest, datasetBytes);
        var expected = catalogue.bind("dataset", dataset, null);
        assertEquals(expected, actual);
        assertEquals(dataset, JSON.readTree(actual.canonicalBytes()));
        byte[] returned = actual.canonicalBytes();
        returned[0] ^= 1;
        assertArrayEquals(expected.canonicalBytes(), actual.canonicalBytes());
        for (JsonNode record : dataset.required("records")) {
            JsonNode locator = record.required("locator");
            var fixed = bind(catalogue.schemaRef("dataset", "locator"), digest, JSON.writeValueAsBytes(locator));
            assertEquals(catalogue.bind("dataset", locator, "locator"), fixed);
        }
    }

    @Test void unknownReferencesAliasesAndDifferentDocumentDigestsAreRejected() throws Exception {
        String anotherDigest = manifest.required("schemas").get(1).required("sha256").textValue();
        for (String ref : new String[]{reference + "#", reference + "?", reference + "/", reference.toUpperCase(java.util.Locale.ROOT),
                catalogue.schemaRef("dataset", "locator").replace("locator", "%6cocator")}) {
            SchemaRejection denied = assertInstanceOf(SchemaRejection.class, rejection(ref, digest, datasetBytes));
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, denied.code());
            assertFalse(denied.toString().contains(ref));
        }
        for (String hash : new String[]{anotherDigest, digest.toUpperCase(java.util.Locale.ROOT), null}) {
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,
                    assertInstanceOf(SchemaRejection.class, rejection(reference, hash, datasetBytes)).code());
        }
    }

    @Test void originalSchemaRejectsChangedFieldsWithoutExposingThePayload() throws Exception {
        ObjectNode changed = dataset.deepCopy();
        String version = dataset.required("schema_version").textValue() + "-changed";
        changed.put("schema_version", version);
        SchemaRejection denied = assertInstanceOf(SchemaRejection.class,
                rejection(reference, digest, JSON.writeValueAsBytes(changed)));
        assertEquals(ErrorCode.SCHEMA_INVALID, denied.code());
        assertFalse(denied.toString().contains(version));
        assertNull(denied.getCause());
    }

    @Test void duplicateFieldsAdditionalJsonRootsAndNonUtf8BytesAreRejected() throws Exception {
        var duplicate = new ByteArrayOutputStream();
        try (var output = JSON.getFactory().createGenerator(duplicate)) {
            output.writeStartObject();
            output.writeStringField("schema_version", dataset.required("schema_version").textValue());
            for (var fields = dataset.fields(); fields.hasNext();) {
                var field = fields.next(); output.writeFieldName(field.getKey()); output.writeTree(field.getValue());
            }
            output.writeEndObject();
        }
        assertInstanceOf(JsonProcessingException.class, rejection(reference, digest, duplicate.toByteArray()));
        var additional = new ByteArrayOutputStream();
        additional.write(datasetBytes); additional.write('\n'); additional.write(datasetBytes);
        assertInstanceOf(JsonProcessingException.class, rejection(reference, digest, additional.toByteArray()));
        assertInstanceOf(CharacterCodingException.class,
                rejection(reference, digest, new String(datasetBytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_16)));
    }

    @Test void actualScalarAndLocatorTypesRemainClosedAndPreciselyValidated() throws Exception {
        String scalarRef = catalogue.schemaRef("interfaces", "Hash");
        String scalarHash = manifest.required("schemas").get(1).required("sha256").textValue();
        for (JsonNode source : dataset.required("sources")) {
            JsonNode hash = source.required("sha256");
            assertEquals(catalogue.bind("interfaces", hash, "Hash"), bind(scalarRef, scalarHash, JSON.writeValueAsBytes(hash)));
            assertEquals(ErrorCode.SCHEMA_INVALID,
                    assertInstanceOf(SchemaRejection.class, rejection(scalarRef, scalarHash, JSON.writeValueAsBytes(source))).code());
        }
        assertEquals(ErrorCode.SCHEMA_INVALID,
                    assertInstanceOf(SchemaRejection.class, rejection(reference, digest, new byte[0])).code());
    }

    @Test void classpathConsumersShareTheCompletePublishedCatalogue() throws Exception {
        assertSame(catalogue, SchemaCatalogue.fromClasspath(), "同一制品的发布Schema必须共享完整校验结果");
        var project = SchemaCatalogue.fromProject(root);
        assertNotSame(catalogue, project);
        assertEquals(catalogue.requirePublished(reference, digest), project.requirePublished(reference, digest));
        assertEquals(catalogue.bindPublished(reference, digest, datasetBytes), project.bindPublished(reference, digest, datasetBytes));
    }

    @Test void concurrentConsumersValidateActualPublicOriginalsIndependently() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var expected = catalogue.bindPublished(reference, digest, datasetBytes);
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<SchemaCatalogue.ValidatedPayload>>();
            for (int ordinal = 0; ordinal < 8; ordinal++) tasks.add(() -> catalogue.bindPublished(reference, digest, datasetBytes));
            for (var result : executor.invokeAll(tasks)) {
                var actual = result.get(); assertEquals(expected, actual);
                assertEquals(dataset, JSON.readTree(actual.canonicalBytes()));
                byte[] changed = actual.canonicalBytes(); changed[0] ^= 1;
                assertArrayEquals(expected.canonicalBytes(), actual.canonicalBytes());
            }
        } finally { executor.shutdownNow(); }
    }
}
