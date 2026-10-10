package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplatePublishedSchemaTest {
    private final Path root = Path.of(System.getProperty("medical.projectRoot"));
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void allTemplateDefinitionsRetainTheirDeclaredPublishedIdentity() throws Exception {
        var catalogue = SchemaCatalogue.fromProject(root);
        for (var entry : documents().entrySet()) {
            byte[] bytes = Files.readAllBytes(root.resolve(entry.getValue()));
            var document = json.readTree(bytes); String uri = document.required("$id").textValue();
            String digest = WireValues.sha256(bytes);
            assertEquals(uri, catalogue.requirePublished(uri, digest).schemaRef());
            assertEquals(uri, catalogue.schemaRef(entry.getKey(), null));
            assertEquals(entry.getKey(), catalogue.requirePublished(uri, digest).name());
            for (var names = document.required("$defs").fieldNames(); names.hasNext();) {
                String definition = names.next(); String reference = uri + "#/$defs/" + definition;
                assertEquals(definition, catalogue.requirePublished(reference, digest).definition());
                assertEquals(reference, catalogue.schemaRef(entry.getKey(), definition));
            }
        }
    }

    @Test void actualPublicOriginalDigestsUseTheTemplateHashDefinition() throws Exception {
        var catalogue = SchemaCatalogue.fromProject(root);
        byte[] schema = Files.readAllBytes(root.resolve(documents().get("templates")));
        String digest = WireValues.sha256(schema), reference = "urn:medical:templates:1.0.0#/$defs/Hash";
        var actual = json.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        for (var source : actual.required("sources")) {
            var hash = source.required("sha256");
            assertEquals(hash, json.readTree(catalogue.bindPublished(reference, digest, json.writeValueAsBytes(hash)).canonicalBytes()));
            var rejected = assertThrows(SchemaRejection.class, () -> catalogue.bindPublished(reference, digest, json.writeValueAsBytes(source)));
            assertEquals(ErrorCode.SCHEMA_INVALID, rejected.code());
        }
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class,
                () -> catalogue.bindPublished("urn:medical:templates:1.0.0#/$defs/Content", digest, json.writeValueAsBytes(actual))).code());
    }

    @Test void templateAliasesAndDifferentOriginalDigestsRemainRejected() throws Exception {
        var catalogue = SchemaCatalogue.fromProject(root);
        String digest = WireValues.sha256(Files.readAllBytes(root.resolve(documents().get("templates"))));
        String original = "urn:medical:templates:1.0.0#/$defs/Content";
        assertEquals(original, catalogue.requirePublished(original, digest).schemaRef());
        for (String reference : java.util.List.of(original + "?", original.toUpperCase(java.util.Locale.ROOT),
                "https://schemas.medical-agent.invalid/1.0.0/" + documents().get("templates") + "#/$defs/Content"))
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class,
                    () -> catalogue.requirePublished(reference, digest)).code());
        String other = WireValues.sha256(Files.readAllBytes(root.resolve(documents().get("template-operations"))));
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class,
                () -> catalogue.requirePublished(original, other)).code());
    }

    @Test void immutableClasspathAndCurrentProjectExposeTheSameCompleteTemplateSchemas() throws Exception {
        var packaged = SchemaCatalogue.fromClasspath(); var project = SchemaCatalogue.fromProject(root);
        for (var entry : documents().entrySet()) {
            String digest = WireValues.sha256(Files.readAllBytes(root.resolve(entry.getValue())));
            String uri = json.readTree(Files.readAllBytes(root.resolve(entry.getValue()))).required("$id").textValue();
            assertEquals(project.requirePublished(uri, digest), packaged.requirePublished(uri, digest));
        }
    }

    private static Map<String,String> documents() {
        return Map.of("templates", "business/medical-persistence/src/main/resources/templates.schema.json",
                "template-operations", "business/medical-persistence/src/main/resources/template-operations.schema.json");
    }
}
