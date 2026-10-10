package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class DocumentFrontendStructureTest {
    @Test void acceptsTheCompleteFrontendTableEditsWithStableFieldAndNodeIdentities() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var result = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/document-frontend-structure.json")));
        assertEquals("SIMULATION", result.required("data_kind").textValue());
        assertFalse(result.required("formal_customer_acceptance").booleanValue());
        var schemas = SchemaCatalogue.fromClasspath(); var body = result.required("body");
        assertEquals(body, DocumentBodySemantics.verify(schemas, body, new DocumentBodySemantics.Limits(10000, 10000, 100000)));
        var payload = schemas.bind("interfaces", body, "DocumentBody");
        assertEquals(result.required("body_sha256").textValue(), WireValues.sha256(payload.canonicalBytes()));
        var identities = new HashSet<String>();
        for (var node : body.required("nodes")) assertTrue(identities.add(node.required("node_id").textValue()));
        assertTrue(identities.contains(result.required("text_node_id").textValue()));
        assertTrue(identities.contains(result.required("formula_node_id").textValue()));
        assertEquals(result.required("field_id").textValue(), body.required("fields").get(0).required("field_id").textValue());
        assertEquals("12.500", body.required("fields").get(0).required("decimal_value").textValue());
        var table = java.util.stream.StreamSupport.stream(body.required("nodes").spliterator(), false)
            .filter(node -> node.required("node_id").textValue().equals(result.required("table_node_id").textValue())).findFirst().orElseThrow();
        var cells = table.required("content").required("cells");
        assertEquals(4, cells.size()); assertEquals(result.required("first_cell_id").textValue(), cells.get(0).required("cell_id").textValue());
        assertEquals(2, cells.get(0).required("node_ids").size());
    }
}
