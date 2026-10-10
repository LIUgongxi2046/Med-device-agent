package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedContractTest {
    private static Path root;
    private static JsonNode dataset;
    private static SchemaCatalogue catalogue;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void load() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        catalogue = SchemaCatalogue.fromClasspath();
    }

    @Test
    void actualDatasetAndAllDefinitionsUseTheSameRelease() throws Exception {
        assertDoesNotThrow(() -> catalogue.validate("dataset", dataset, null));
        assertEquals(307, catalogue.definitionCount());
        SchemaCatalogue current = SchemaCatalogue.fromProject(root);
        assertEquals(current.bind("dataset", dataset, null).canonicalJson(), catalogue.bind("dataset", dataset, null).canonicalJson());
        assertEquals(dataset, JSON.readTree(catalogue.bind("dataset", dataset, null).canonicalBytes()));
    }

    @Test
    void changedFieldsVersionAndTimestampAreRejected() {
        ObjectNode fields = dataset.deepCopy();
        fields.put("production_approved", true);
        assertThrows(IllegalArgumentException.class, () -> catalogue.validate("dataset", fields, null));
        ObjectNode version = dataset.deepCopy();
        version.put("schema_version", dataset.get("schema_version").asText() + "-changed");
        assertThrows(IllegalArgumentException.class, () -> catalogue.validate("dataset", version, null));
        ObjectNode time = dataset.deepCopy();
        time.put("created_at", dataset.get("created_at").asText().replace('T', 'X'));
        assertThrows(IllegalArgumentException.class, () -> catalogue.validate("dataset", time, null));
        assertThrows(IllegalArgumentException.class, () -> catalogue.validate("https://unregistered.invalid/schema", dataset, null));
    }

    @Test
    void schemaFailuresExposePublishedCodesWithoutRestrictedPayloads() {
        ObjectNode changed = dataset.deepCopy();
        String version = dataset.required("schema_version").asText() + "-changed";
        changed.put("schema_version", version);
        SchemaRejection invalid = assertThrows(SchemaRejection.class, () -> catalogue.validate("dataset", changed, null));
        assertEquals(ErrorCode.SCHEMA_INVALID, invalid.code());
        assertEquals("SCHEMA_VALIDATION_FAILED", invalid.getMessage());
        assertFalse(invalid.toString().contains(version));
        assertNull(invalid.getCause());
        String sourceId = dataset.required("sources").get(0).required("source_id").asText();
        for (Runnable operation : new Runnable[]{() -> catalogue.schemaRef(sourceId, null), () -> catalogue.schemaRef("dataset", sourceId)}) {
            SchemaRejection unsupported = assertThrows(SchemaRejection.class, operation::run);
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, unsupported.code());
            assertEquals("SCHEMA_UNSUPPORTED", unsupported.getMessage());
            assertNull(unsupported.getCause());
        }
    }

    @Test
    void unknownErrorCodesAreRejectedWithoutInputDisclosure() {
        String sourceId = dataset.required("sources").get(0).required("source_id").asText();
        for (String value : new String[]{sourceId, null, ErrorCode.ACCESS_DENIED.name().toLowerCase(java.util.Locale.ROOT)}) {
            SchemaRejection rejected = assertThrows(SchemaRejection.class, () -> ErrorCode.parse(value));
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, rejected.code());
            assertEquals("SCHEMA_UNSUPPORTED", rejected.getMessage());
        }
        assertEquals(ErrorCode.ACCESS_DENIED, ErrorCode.parse(ErrorCode.ACCESS_DENIED.name()));
        assertThrows(IllegalArgumentException.class, () -> new SchemaRejection(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void locatorsReopenActualTextAndRetainOriginalDigests() throws Exception {
        Map<String, JsonNode> sources = new HashMap<>();
        for (JsonNode source : dataset.get("sources")) sources.put(source.get("source_id").asText(), source);
        int read = 0;
        for (JsonNode record : dataset.get("records")) {
            JsonNode locator = record.get("locator");
            catalogue.validate("dataset", locator, "locator");
            String kind = locator.get("kind").asText();
            if (!kind.equals("TXT") && !kind.equals("MD")) continue;
            String text = Files.readString(root.resolve(sources.get(record.get("source_id").asText()).get("source_ref").asText()), StandardCharsets.UTF_8);
            int start = locator.get("char_start").asInt();
            int end = locator.get("char_end").asInt();
            assertEquals(record.get("text").asText(), WireValues.codePointQuote(text, start, end, record.get("quote_hash").asText()));
            assertThrows(IllegalArgumentException.class, () -> WireValues.codePointQuote(text, start, end, record.get("source_version").asText()));
            read++;
        }
        assertTrue(read > 0);
    }

    @Test
    void technicalWireLimitsAreEnforcedWithoutBusinessObjects() {
        String execution = UUID.randomUUID().toString();
        assertEquals(execution, WireValues.uuid(execution).toString());
        assertThrows(IllegalArgumentException.class, () -> WireValues.uuid(execution.toUpperCase()));
        String maximum = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE).toString();
        assertEquals(maximum, WireValues.revision(maximum).toString());
        for (String value : new String[]{maximum + "0", "01", "1.0"}) assertThrows(IllegalArgumentException.class, () -> WireValues.revision(value));
        assertEquals("0.00000001", WireValues.decimal("0.00000001", 24, 8).toPlainString());
        assertEquals("0.000000000001", WireValues.decimal("0.000000000001", 38, 12).toPlainString());
        for (String value : new String[]{"0.000000001", "10000000000000000", "1e-8", "NaN"}) assertThrows(IllegalArgumentException.class, () -> WireValues.decimal(value, 24, 8));
        String time = dataset.get("created_at").asText();
        assertEquals(time.replace("+00:00", "Z"), WireValues.utcTime(time).toString());
        assertThrows(IllegalArgumentException.class, () -> WireValues.utcTime(time.replace("+00:00", "+08:00")));
    }

    @Test
    void currentExecutionProducesTheCrossLanguageSnapshot() throws Exception {
        String executionId = UUID.randomUUID().toString();
        Map<String, String> locators = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, String> textRanges = new LinkedHashMap<>();
        Map<String, String> scalarDigests = new LinkedHashMap<>();
        Map<String, JsonNode> sourceRecords = new HashMap<>();
        for (JsonNode source : dataset.get("sources")) {
            String id = source.get("source_id").asText();
            byte[] original = Files.readAllBytes(root.resolve(source.get("source_ref").asText()));
            String digest = WireValues.sha256(original);
            assertEquals(source.get("sha256").asText(), digest);
            assertEquals(source.get("size_bytes").asLong(), original.length);
            sources.put(id, digest);
            sourceRecords.put(id, source);
            scalarDigests.put(id + "/source_id", WireValues.sha256(catalogue.bind("model-stream", source.required("source_id"), "Text").canonicalBytes()));
            scalarDigests.put(id + "/sha256", WireValues.sha256(catalogue.bind("model-stream", source.required("sha256"), "Hash").canonicalBytes()));
            scalarDigests.put(id + "/size_bytes", WireValues.sha256(catalogue.bind("model-stream", source.required("size_bytes"), "TokenCount").canonicalBytes()));
            scalarDigests.put(id + "/parent_source_id", WireValues.sha256(catalogue.bind("model-stream", source.required("parent_source_id"), "NullableText").canonicalBytes()));
        }
        for (JsonNode record : dataset.get("records")) {
            String id = record.get("record_id").asText();
            JsonNode locator = record.get("locator");
            scalarDigests.put(id + "/text", WireValues.sha256(catalogue.bind("model-stream", record.required("text"), "Text").canonicalBytes()));
            locators.put(id, WireValues.sha256(catalogue.bind("dataset", locator, "locator").canonicalBytes()));
            if (!locator.get("kind").asText().equals("TXT")) continue;
            String text = Files.readString(root.resolve(sourceRecords.get(record.get("source_id").asText()).get("source_ref").asText()), StandardCharsets.UTF_8);
            String quote = WireValues.codePointQuote(text, locator.get("char_start").asInt(), locator.get("char_end").asInt(), record.get("quote_hash").asText());
            textRanges.put(id, WireValues.sha256(quote.getBytes(StandardCharsets.UTF_8)));
        }
        Map<String, String> inputs = new LinkedHashMap<>();
        for (String path : new String[]{"contracts/catalogue.json", "data-design/sample_dataset.json", "data-design/dataset.schema.json", "backend-design/interfaces.schema.yaml", "backend-design/resource-details.schema.yaml", "agent-design/artifacts.schema.json", "agent-design/model-stream.schema.json", "LLD-后端.md", "business/pom.xml", "business/ruoyi-admin/pom.xml", "business/medical-contracts/pom.xml", "scripts/build_business.py"}) inputs.put(path, WireValues.sha256(Files.readAllBytes(root.resolve(path))));
        for (String path : new String[]{"business/medical-persistence/src/main/resources/templates.schema.json",
                "business/medical-persistence/src/main/resources/template-operations.schema.json",
                "business/medical-persistence/src/main/resources/tasks.schema.json",
                "business/medical-persistence/src/main/resources/collections.schema.json",
                "business/medical-persistence/src/main/resources/rule-expressions.schema.json",
                "business/medical-persistence/src/main/resources/template-applicability.schema.json",
                "business/medical-persistence/src/main/resources/agent-release.schema.json",
                "business/medical-persistence/src/main/resources/task-plan-view.schema.json"}) {
            inputs.put(path, WireValues.sha256(Files.readAllBytes(root.resolve(path))));
        }
        try (var paths = Files.walk(root.resolve("business/medical-contracts/src"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList()) {
                inputs.put(root.relativize(path).toString(), WireValues.sha256(Files.readAllBytes(path)));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("execution_id", executionId);
        result.put("inputs", inputs);
        result.put("dataset_sha256", WireValues.sha256(catalogue.bind("dataset", dataset, null).canonicalBytes()));
        result.put("source_sha256", sources);
        result.put("locator_sha256", locators);
        result.put("text_ranges", textRanges);
        result.put("schema_scalar_sha256", scalarDigests);
        result.put("definition_count", catalogue.definitionCount());
        result.put("error_codes", java.util.Arrays.stream(ErrorCode.values()).map(Enum::name).sorted().toList());
        result.put("technical_wire", Map.of("execution_uuid", WireValues.uuid(executionId).toString(), "unsigned_max", WireValues.revision(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE).toString()).toString(), "money_boundary", WireValues.decimal("0.00000001", 24, 8).toPlainString(), "measurement_boundary", WireValues.decimal("0.000000000001", 38, 12).toPlainString(), "utc_time", java.time.format.DateTimeFormatter.ISO_INSTANT.format(WireValues.utcTime(dataset.get("created_at").asText()))));
        String reportPath = ".implementation-work/IC-03-JAVA-SNAPSHOT/executions/" + executionId + "/report.json";
        Path report = root.resolve(reportPath);
        Files.createDirectories(report.getParent());
        byte[] reportBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        Files.write(report, reportBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        Path directory = root.resolve(".implementation-work/IC-03-JAVA-SNAPSHOT");
        Path pointer = directory.resolve(executionId + ".current.json");
        Files.write(pointer, JSON.writeValueAsBytes(Map.of("report_path", reportPath, "sha256", WireValues.sha256(reportBytes))), java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.move(pointer, directory.resolve("current.json"), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
