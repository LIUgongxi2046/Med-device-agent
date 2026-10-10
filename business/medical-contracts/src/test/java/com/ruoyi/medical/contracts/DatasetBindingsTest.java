package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.contracts.SchemaTypeAssertions.JSON;

class DatasetBindingsTest {
    private static SchemaCatalogue catalogue;
    private static JsonNode schema, dataset;
    @BeforeAll static void load() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        catalogue = SchemaCatalogue.fromClasspath();
        schema = JSON.readTree(Files.readAllBytes(root.resolve("data-design/dataset.schema.json")));
        dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
    }

    @Test void completeOriginalDefinitionsAndInlineObjectsHaveExplicitTypes() {
        Set<String> expected = new HashSet<>(); schema.required("$defs").fieldNames().forEachRemaining(expected::add);
        Set<String> actual = new HashSet<>();
        int objects = 0;
        for (var definition : DatasetBindings.definitions()) {
            assertTrue(actual.add(definition.name()));
            JsonNode item = schema.required("$defs").required(definition.name());
            if (item.has("properties")) { SchemaTypeAssertions.object(schema, item, definition.type()); objects++; }
        }
        assertEquals(13, expected.size()); assertEquals(expected, actual); assertEquals(10, objects);
        SchemaTypeAssertions.object(schema, schema, DatasetBindings.DATASET.type());
        var inline = Map.of(
            "/$defs/pdf/properties/region/oneOf/0", DatasetProtocol.WholePage.class,
            "/$defs/pdf/properties/region/oneOf/1", DatasetProtocol.Box.class,
            "/$defs/docx/properties/address/oneOf/0", DatasetProtocol.ParagraphAddress.class,
            "/$defs/docx/properties/address/oneOf/1", DatasetProtocol.CellAddress.class,
            "/$defs/docx/properties/address/oneOf/2", DatasetProtocol.NodeAddress.class,
            "/$defs/pptx/properties/table_cell", DatasetProtocol.TableCell.class);
        inline.forEach((pointer, type) -> SchemaTypeAssertions.object(schema, schema.at(JsonPointer.compile(pointer)), JSON.constructType(type)));
        assertTrue(DatasetProtocol.Locator.class.isSealed()); assertEquals(7, DatasetProtocol.Locator.class.getPermittedSubclasses().length);
        assertTrue(DatasetProtocol.PdfRegion.class.isSealed()); assertEquals(2, DatasetProtocol.PdfRegion.class.getPermittedSubclasses().length);
        assertTrue(DatasetProtocol.DocxAddress.class.isSealed()); assertEquals(3, DatasetProtocol.DocxAddress.class.getPermittedSubclasses().length);
    }

    @Test void actualDatasetAndEveryRegisteredMemberRoundTripWithoutChangingPermission() throws Exception {
        var binding = TypedSchemaBindings.bind(catalogue, DatasetBindings.DATASET, dataset);
        var typed = binding.read();
        assertEquals(catalogue.bind("dataset", dataset, null).canonicalJson(), binding.payload().canonicalJson());
        assertEquals(dataset.required("dataset_version").asText(), typed.datasetVersion());
        assertEquals(dataset.required("created_at").asText(), typed.createdAt());
        assertEquals(11, typed.sources().size()); assertEquals(12, typed.records().size());
        assertThrows(UnsupportedOperationException.class, () -> typed.sources().clear());
        for (JsonNode source : dataset.required("sources")) {
            var member = TypedSchemaBindings.bind(catalogue, DatasetBindings.SOURCE, source).read();
            assertEquals(source.required("source_id").asText(), member.sourceId());
            assertEquals(source.required("sha256").asText(), member.sha256());
            assertEquals(source.required("size_bytes").longValue(), member.sizeBytes());
            assertEquals(DatasetProtocol.ProductionStatus.PENDING_APPROVAL, member.productionStatus());
            assertFalse(member.allowedUse().contains(DatasetProtocol.AllowedUse.APPROVED_PRODUCTION));
            assertThrows(UnsupportedOperationException.class, () -> member.allowedUse().clear());
            assertEquals(member.sourceId(), TypedSchemaBindings.bind(catalogue, DatasetBindings.ID, source.required("source_id")).read());
        }
        for (JsonNode record : dataset.required("records")) {
            var member = TypedSchemaBindings.bind(catalogue, DatasetBindings.RECORD, record).read();
            assertEquals(record.required("text").asText(), member.text()); assertFalse(member.formalEvidenceEligible());
            var locator = TypedSchemaBindings.bind(catalogue, DatasetBindings.LOCATOR, record.required("locator")).read();
            assertEquals(record.required("locator").required("kind").asText(), locator.kind());
            assertEquals(member.locator(), locator);
            var definition = switch (locator.kind()) {
                case "PDF" -> DatasetBindings.PDF; case "DOCX" -> DatasetBindings.DOCX;
                case "XLSX" -> DatasetBindings.XLSX; case "CSV" -> DatasetBindings.CSV;
                case "TXT" -> DatasetBindings.TEXT; case "MARKDOWN" -> DatasetBindings.MARKDOWN;
                case "PPTX" -> DatasetBindings.PPTX; default -> throw new IllegalArgumentException("SCHEMA_UNSUPPORTED");
            };
            assertEquals(locator, TypedSchemaBindings.bind(catalogue, definition, record.required("locator")).read());
        }
    }

    @Test void missingRegisteredFieldsAndWrongActualEnumValuesAreRejected() {
        for (JsonNode source : dataset.required("sources")) {
            var missing = source.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("production_status");
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, DatasetBindings.SOURCE, missing));
            var wrong = ((com.fasterxml.jackson.databind.node.ObjectNode) source.deepCopy()).put("production_status", source.required("allowed_use").get(0).asText());
            assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, DatasetBindings.SOURCE, wrong));
        }
        for (var definition : DatasetBindings.definitions()) assertThrows(IllegalArgumentException.class, () -> TypedSchemaBindings.bind(catalogue, definition, dataset));
    }
}
