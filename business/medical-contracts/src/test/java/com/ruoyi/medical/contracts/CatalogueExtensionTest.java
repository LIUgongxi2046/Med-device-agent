package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueExtensionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE_SHA256 = "dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915";
    private final Path root = Path.of(System.getProperty("medical.projectRoot"));
    private byte[] base() throws Exception { return Files.readAllBytes(root.resolve("contracts/catalogue.json")); }
    private byte[] extension() throws Exception { return Files.readAllBytes(root.resolve(SchemaCatalogue.EXTENSION_PATH)); }

    @Test void additivePublicationPreservesEveryLegacyIdentityAndTheExactBaseBytes() throws Exception {
        byte[] original = base();
        assertEquals(BASE_SHA256, WireValues.sha256(original));
        JsonNode merged = SchemaCatalogue.publishedManifest(original, extension());
        assertEquals(14, merged.required("schemas").size());
        assertEquals(13, JSON.readTree(original).required("schemas").size());
        var catalogue = SchemaCatalogue.fromClasspath();
        for (JsonNode entry : JSON.readTree(original).required("schemas")) {
            String name = entry.required("name").asText(), hash = entry.required("sha256").asText();
            assertEquals(hash, catalogue.requirePublished(catalogue.schemaRef(name, null), hash).schemaSha256());
        }
        assertEquals("urn:medical:evaluation-results:1.0.0#/$defs/ExpectedItems", catalogue.schemaRef("evaluation-results", "ExpectedItems"));
        assertArrayEquals(original, base());
    }
    @Test void modifiedExtensionBytesCannotSelectANewTrustRoot() throws Exception {
        byte[] changed = java.util.Arrays.copyOf(extension(), extension().length + 1); changed[changed.length - 1] = ' ';
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base(), changed));
    }
    @Test void extensionCannotBindToDifferentBaseCatalogueBytes() throws Exception {
        byte[] changed = java.util.Arrays.copyOf(base(), base().length + 1); changed[changed.length - 1] = ' ';
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(changed, extension()));
        ObjectNode changedReference = (ObjectNode) JSON.readTree(extension()); changedReference.put("base_catalogue_sha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base(), JSON.writeValueAsBytes(changedReference)));
    }
    @Test void alteredExtensionsCannotOverrideLegacyNamesOrUrisOrReadArbitraryPaths() throws Exception {
        JsonNode original = JSON.readTree(extension());
        for (String field : new String[]{"name", "path", "sha256"}) {
            ObjectNode changed = original.deepCopy(); ObjectNode entry = (ObjectNode) changed.required("schemas").get(0);
            entry.put(field, field.equals("name") ? "dataset" : field.equals("path") ? "../../outside.schema.json" : "0".repeat(64));
            assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base(), JSON.writeValueAsBytes(changed)), field);
        }
        ObjectNode duplicate = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.required("schemas")).add(duplicate.required("schemas").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base(), JSON.writeValueAsBytes(duplicate)));
        ObjectNode sameUri = original.deepCopy(); ((ObjectNode) sameUri.required("schemas").get(0)).put("name", "dataset-alias").put("path", "data-design/dataset.schema.json");
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base(), JSON.writeValueAsBytes(sameUri)));
    }
}
