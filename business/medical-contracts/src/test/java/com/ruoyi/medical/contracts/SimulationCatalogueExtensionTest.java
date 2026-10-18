package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationCatalogueExtensionTest {
    private final Path root = Path.of(System.getProperty("medical.projectRoot"));
    private final ObjectMapper json = new ObjectMapper();
    @Test void packagedSimulationPublicationIsPinnedAndBoundToOriginalSources() throws Exception {
        var catalogue = SchemaCatalogue.fromClasspath();
        var extension = json.readTree(Files.readAllBytes(root.resolve(SchemaCatalogue.SIMULATION_EXTENSION_PATH)));
        for (var entry : extension.required("schemas")) {
            String path = entry.required("path").asText(), name = entry.required("name").asText();
            byte[] source = Files.readAllBytes(root.resolve(path));
            try (var stream = SchemaCatalogue.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream); assertArrayEquals(source, stream.readAllBytes());
            }
            String digest = WireValues.sha256(source);
            assertEquals(entry.required("sha256").asText(), digest);
            assertEquals(name, catalogue.requirePublished(catalogue.schemaRef(name, null), digest).name());
            var names = json.readTree(source).required("$defs").fieldNames();
            while (names.hasNext()) assertEquals(name, catalogue.requirePublished(catalogue.schemaRef(name, names.next()), digest).name());
        }
        assertEquals("urn:medical:simulation-execution:1.0.0#/$defs/SimulationInvocationAdmission", catalogue.schemaRef("simulation-execution", "SimulationInvocationAdmission"));
    }
    @Test void alteredExtensionAndTokenCounterCannotAcquirePublishedIdentity() throws Exception {
        byte[] base=Files.readAllBytes(root.resolve("contracts/catalogue.json"));
        byte[] evaluation=Files.readAllBytes(root.resolve(SchemaCatalogue.EXTENSION_PATH));
        byte[] simulation=Files.readAllBytes(root.resolve(SchemaCatalogue.SIMULATION_EXTENSION_PATH));
        byte[] changed=java.util.Arrays.copyOf(simulation, simulation.length+1);
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalogue.publishedManifest(base,evaluation,changed));
        var catalogue=SchemaCatalogue.fromClasspath();
        var counter=json.createObjectNode().put("unit","CANONICAL_UTF8_BYTE")
            .put("revision","LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1")
            .put("implementation_sha256","a".repeat(64)).put("measurement_schema_sha256","b".repeat(64));
        catalogue.validate("simulation-execution",counter,"CounterIdentity");
        counter.put("unit","TOKEN");
        assertThrows(SchemaRejection.class, () -> catalogue.validate("simulation-execution",counter,"CounterIdentity"));
    }
    @Test void contextRequiresExactSourceRolesAndPromptContentIdentity() throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();
        var context=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(root.resolve("contracts/fixtures/simulation-context.synthetic.json")));
        catalogue.validate("simulation-execution",context,"SimulationContextManifest");
        var missing=context.deepCopy(); missing.remove("prompt_revision");
        assertThrows(SchemaRejection.class, () -> catalogue.validate("simulation-execution",missing,"SimulationContextManifest"));
        var wrong=context.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)wrong.required("source_manifest").get(0)).put("kind","EXACT_INPUT");
        assertThrows(SchemaRejection.class, () -> catalogue.validate("simulation-execution",wrong,"SimulationContextManifest"));
    }
    @Test void candidateDependenciesRejectUnversionedAndDuplicateEntries() throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();
        var value=json.createObjectNode().put("schema_version","1.0.0").put("manifest_kind","SIMULATION_CANDIDATE_DEPENDENCIES");
        var entries=value.putArray("dependencies");
        for(int n=1;n<=3;n++) entries.addObject().put("version_id",String.format("%08d-1111-4111-8111-111111111111",n)).put("content_sha256",String.valueOf(n).repeat(64));
        catalogue.validate("simulation-execution",value,"SimulationCandidateDependencyManifest");
        entries.add(entries.get(0).deepCopy());
        assertThrows(SchemaRejection.class, () -> catalogue.validate("simulation-execution",value,"SimulationCandidateDependencyManifest"));
        entries.remove(entries.size()-1); value.remove("manifest_kind");
        assertThrows(SchemaRejection.class, () -> catalogue.validate("simulation-execution",value,"SimulationCandidateDependencyManifest"));
    }
}
