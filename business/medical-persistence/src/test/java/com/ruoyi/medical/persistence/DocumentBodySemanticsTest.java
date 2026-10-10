package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class DocumentBodySemanticsTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DocumentBodySemantics.Limits LIMITS = new DocumentBodySemantics.Limits(10000, 10000, 100000);
    private final SchemaCatalogue schemas = SchemaCatalogue.fromClasspath();
    public DocumentBodySemanticsTest() throws Exception { }

    @Test void keepsTheCompleteActualRepositoryDocument() throws Exception {
        var body = repositoryBody(); var before = body.deepCopy();
        assertEquals(before, DocumentBodySemantics.verify(schemas, body, LIMITS));
        assertEquals(before, body);
    }

    @Test void rejectsDisconnectedDuplicateAndCyclicNodes() throws Exception {
        var missing = repositoryBody(); node(missing, 1).put("parent_id", id()); rejected(missing);
        var duplicate = repositoryBody(); node(duplicate, 1).put("node_id", node(duplicate, 0).required("node_id").textValue()); rejected(duplicate);
        var cyclic = repositoryBody(); node(cyclic, 1).put("parent_id", node(cyclic, 2).required("node_id").textValue()); rejected(cyclic);
        var order = repositoryBody(); node(order, 1).put("ordinal", 0);
        node(order, 0).putNull("parent_id");
        var second = node(order, 1).deepCopy(); second.put("node_id", id()); order.withArray("nodes").add(second); rejected(order);
        var root = repositoryBody(); node(root, 1).putNull("parent_id"); rejected(root);
    }

    @Test void rejectsReferencesThatDoNotBelongToTheDocument() throws Exception {
        var field = repositoryBody(); node(field, 2).withArray("field_ids").add(id()); rejected(field);
        var evidence = repositoryBody(); node(evidence, 2).withArray("evidence_link_ids").add(id()); rejected(evidence);
        var fact = repositoryBody(); var usage = fact.withArray("fact_usages").addObject();
        usage.put("fact_version_id", id()); usage.putArray("node_ids").add(id()); usage.putArray("field_ids"); rejected(fact);
    }

    @Test void validatesTheActualDirectoryAsACompleteTable() throws Exception {
        var body = directoryBody(); assertEquals(body, DocumentBodySemantics.verify(schemas, body, LIMITS));
        var table = node(body, 3).withObject("content"); var first = (ObjectNode) table.withArray("cells").get(0);
        var overlap = body.deepCopy(); node(overlap, 3).withObject("content").withArray("cells").add(first.deepCopy().put("cell_id", id())); rejected(overlap);
        var overflow = body.deepCopy(); ((ObjectNode) node(overflow, 3).withObject("content").withArray("cells").get(0)).put("row_span", 10000); rejected(overflow);
        var hole = body.deepCopy(); node(hole, 3).withObject("content").withArray("cells").remove(0); rejected(hole);
        var foreign = body.deepCopy(); ((ObjectNode) node(foreign, 3).withObject("content").withArray("cells").get(0)).put("row_id", id()); rejected(foreign);
        var misplaced = body.deepCopy(); node(misplaced, 4).put("parent_id", node(misplaced, 0).required("node_id").textValue()); rejected(misplaced);
        var merged = body.deepCopy(); var mergedContent = node(merged, 3).withObject("content"); mergedContent.withArray("column_ids").add(id());
        for (var cell : mergedContent.withArray("cells")) ((ObjectNode) cell).put("column_span", 2);
        assertEquals(merged, DocumentBodySemantics.verify(schemas, merged, LIMITS));
    }

    @Test void theUploadConsumerRequiresTheOriginalCanonicalDocumentBytes() throws Exception {
        var body = repositoryBody(); var payload = schemas.bind("interfaces", body, "DocumentBody");
        var published = schemas.requirePublished(payload.schemaRef(), payload.schemaSha256());
        assertEquals(payload, DocumentBodySemantics.published(schemas, published, payload.canonicalBytes(), LIMITS));
        var changed = body.deepCopy(); node(changed, 2).put("parent_id", id());
        var changedPayload = schemas.bind("interfaces", changed, "DocumentBody");
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class,
                () -> DocumentBodySemantics.published(schemas, published, changedPayload.canonicalBytes(), LIMITS)).code());
        byte[] formatted = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                () -> DocumentBodySemantics.published(schemas, published, formatted, LIMITS)).code());
    }

    @Test void rejectsInvalidUnicodeAndRequiresExplicitCapacity() throws Exception {
        var unicode = repositoryBody(); node(unicode, 2).withObject("content").put("text", Character.toString((char) 0xd800)); rejected(unicode);
        var excessive = repositoryBody();
        var failure = assertThrows(TenantAccessException.class, () -> DocumentBodySemantics.verify(schemas, excessive,
                new DocumentBodySemantics.Limits(2, 10000, 100000)));
        assertEquals(ErrorCode.CONTEXT_LIMIT, failure.code());
        assertThrows(IllegalArgumentException.class, () -> new DocumentBodySemantics.Limits(0, 1, 1));
    }

    private void rejected(JsonNode body) {
        var failure = assertThrows(TenantAccessException.class, () -> DocumentBodySemantics.verify(schemas, body, LIMITS));
        assertEquals(ErrorCode.SCHEMA_INVALID, failure.code());
    }

    private static ObjectNode repositoryBody() throws Exception {
        String text = Files.readString(root().resolve("backend-design/接口补充设计.md"));
        var body = JSON.createObjectNode(); body.put("schema_version", "1.0.0"); body.put("template_version", id());
        body.putArray("nodes"); body.putArray("fields"); body.putArray("evidence_links"); body.putArray("fact_usages");
        var document = add(body, null, 0, "DOCUMENT"); document.withObject("content").put("title", "接口补充设计");
        var paragraph = add(body, document.required("node_id").textValue(), 0, "PARAGRAPH"); paragraph.withObject("content").put("title", "原始工程文档");
        var original = add(body, paragraph.required("node_id").textValue(), 0, "TEXT");
        original.withObject("content").put("text", text).putArray("marks");
        return body;
    }

    private static ObjectNode directoryBody() throws Exception {
        var body = repositoryBody(); var table = add(body, node(body, 0).required("node_id").textValue(), 1, "TABLE");
        var content = table.withObject("content"); var rows = content.putArray("row_ids"); var columns = content.putArray("column_ids");
        String column = id(); columns.add(column); var cells = content.putArray("cells"); content.put("caption", "当前开发记录目录");
        List<Path> paths;
        try (var entries = Files.list(root().resolve("docs/development"))) { paths = entries.filter(Files::isRegularFile).sorted().toList(); }
        int ordinal = 0;
        for (var path : paths) {
            String row = id(); rows.add(row);
            var text = add(body, table.required("node_id").textValue(), ordinal++, "TEXT");
            text.withObject("content").put("text", path.getFileName().toString()).putArray("marks");
            var cell = cells.addObject(); cell.put("cell_id", id()); cell.put("row_id", row); cell.put("column_id", column);
            cell.put("row_span", 1); cell.put("column_span", 1); cell.putArray("node_ids").add(text.required("node_id").textValue());
        }
        assertFalse(paths.isEmpty()); return body;
    }

    private static ObjectNode add(ObjectNode body, String parent, int ordinal, String kind) {
        var node = body.withArray("nodes").addObject(); node.put("node_id", id()); node.put("parent_id", parent); node.put("ordinal", ordinal);
        node.putArray("field_ids"); node.putArray("evidence_link_ids"); node.putObject("content").put("kind", kind); return node;
    }
    private static ObjectNode node(JsonNode body, int index) { return (ObjectNode) body.required("nodes").get(index); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Path root() { return Path.of(System.getProperty("medical.projectRoot")); }
}
