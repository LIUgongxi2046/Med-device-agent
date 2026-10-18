package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure comparison tests using the real published body binder and structural validator. */
class DocumentStructuralComparisonEngineTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DocumentStructuralComparisonEngine.Limits LIMITS =
            new DocumentStructuralComparisonEngine.Limits(128, 32, 512, 1_000_000, 2_000_000, 256, 100_000, 20_000_000, 60_000);
    private static final InterfaceProtocol.CompareRequest REQUEST = new InterfaceProtocol.CompareRequest(id(1), id(2), id(3));
    private final DocumentStructuralComparisonSchema schema = new DocumentStructuralComparisonSchema();
    private final DocumentStructuralComparisonEngine engine = new DocumentStructuralComparisonEngine(schema);
    DocumentStructuralComparisonEngineTest() throws Exception { }

    @Test void equalCompleteTypedBodiesYieldOnlyUnchangedRecordsWithoutInventedReferences() throws Exception {
        var body = body(); var before = body.deepCopy(); var result = compare(body, body);
        assertTrue(result.required("records").size() > body.required("nodes").size());
        for (var record : result.required("records")) {
            assertEquals("UNCHANGED", record.required("status").asText());
            assertEquals(record.required("before_json"), record.required("after_json"));
        }
        assertTrue(result.required("affected_node_ids").isEmpty());
        assertTrue(result.required("left_affected_section_ids").isEmpty());
        assertTrue(result.required("right_affected_section_ids").isEmpty());
        assertEquals("NOT_PERFORMED", result.required("semantic_assessment").asText());
        for (String absent : List.of("artifact_id", "run_id", "operation_id", "review_ready", "merged_document")) assertFalse(result.has(absent));
        assertEquals(before, body);
        assertEquals(schema.canonical(body), schema.canonical(typed(body)));
        assertEquals(schema.canonical(body.required("evidence_links").get(0)), record(result, "EVIDENCE", id(70)).required("before_json").asText());
        checkCoverage(result);
    }

    @Test void deterministicResultsBindRequestBodyAndActualSchemaIdentities() throws Exception {
        var body = body(); var first = compare(body, body); assertEquals(first, compare(body, body));
        assertEquals(schema.hash(body), first.required("left_body_sha256").asText());
        assertEquals(schema.schemaSha256(), first.required("schema_sha256").asText());
        assertEquals(schema.catalogue.schemaDigest("interfaces"), first.required("body_schema_sha256").asText());
        var swapped = engine.compare(new InterfaceProtocol.CompareRequest(id(1), id(3), id(2)), typed(body), typed(body), LIMITS);
        assertNotEquals(first.required("input_sha256"), swapped.required("input_sha256"));
        var changed = body.deepCopy(); text(changed, 13, "changed");
        assertNotEquals(first.required("input_sha256"), compare(body, changed).required("input_sha256"));
    }

    @Test void sameFixedRevisionCannotDescribeDifferentBodyBytes() throws Exception {
        var left = body(); var right = body(); text(right, 13, "changed under the same immutable revision");
        var sameRevision = new InterfaceProtocol.CompareRequest(id(1), id(2), id(2));
        var a = typed(left); var b = typed(right);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> engine.compare(sameRevision, a, b, LIMITS)).code());
        assertTrue(engine.compare(sameRevision, a, a, LIMITS).at("/coverage/complete").booleanValue());
    }

    @Test void emojiAndCombiningMarksUseExactUnnormalizedCodePointRanges() throws Exception {
        var left = body(); var right = body(); text(left, 13, "A\uD83D\uDE00e\u0301Z"); text(right, 13, "A\uD83D\uDE00e\u0300Z");
        var result = compare(left, right); var change = record(result, "NODE", id(13)).required("text_change");
        selection(change.required("left"), 3, 4, "\u0301"); selection(change.required("right"), 3, 4, "\u0300");
        text(right, 13, "A\uD83D\uDE00\u00E9Z"); change = record(compare(left, right), "NODE", id(13)).required("text_change");
        selection(change.required("left"), 2, 4, "e\u0301"); selection(change.required("right"), 2, 3, "\u00E9");
        assertEquals(List.of(id(13)), strings(result.required("affected_node_ids")));
        assertEquals(List.of(id(11)), strings(result.required("left_affected_section_ids")));
    }

    @Test void insertionsDeletionsAndEmptyTextKeepExactEmptySideAnchors() throws Exception {
        var left = body(); var right = body(); text(left, 13, "ab"); text(right, 13, "a\uD83D\uDE00b");
        var change = record(compare(left, right), "NODE", id(13)).required("text_change");
        selection(change.required("left"), 1, 1, ""); selection(change.required("right"), 1, 2, "\uD83D\uDE00");
        change = record(compare(right, left), "NODE", id(13)).required("text_change");
        selection(change.required("left"), 1, 2, "\uD83D\uDE00"); selection(change.required("right"), 1, 1, "");
        text(left, 13, ""); text(right, 13, "\uD83D\uDE00\u0301");
        change = record(compare(left, right), "NODE", id(13)).required("text_change");
        selection(change.required("left"), 0, 0, ""); selection(change.required("right"), 0, 2, "\uD83D\uDE00\u0301");
    }

    @Test void prefixSuffixAlgorithmKeepsOneExactRangeAcrossSeparatedEdits() throws Exception {
        var left = body(); var right = body(); text(left, 13, "aXmiddleYz"); text(right, 13, "aPmiddleQz");
        var change = record(compare(left, right), "NODE", id(13)).required("text_change");
        selection(change.required("left"), 1, 9, "XmiddleY"); selection(change.required("right"), 1, 9, "PmiddleQ");
    }

    @Test void formattingChangePreservesActualNodeAndDoesNotInventTextReplacement() throws Exception {
        var left = body(); var right = body(); node(right, 13).withObject("content").withArray("marks").add("BOLD");
        var changed = record(compare(left, right), "NODE", id(13)); assertEquals("CHANGED", changed.required("status").asText());
        assertEquals("BOLD", snippet(changed, "after").at("/content/marks/0").asText());
        var change = changed.required("text_change"); int end = "A\uD83D\uDE00e\u0301Z".codePointCount(0, "A\uD83D\uDE00e\u0301Z".length());
        selection(change.required("left"), end, end, ""); selection(change.required("right"), end, end, "");
    }

    @Test void nodeAdditionRemovalAndContainerMovesRetainRealParentIdentities() throws Exception {
        var left = body(); var right = body();
        add(right, 15, 12, 2, "TEXT").withObject("content").put("text", "new").putArray("marks");
        var added = record(compare(left, right), "NODE", id(15)); assertEquals("ADDED", added.path("status").asText());
        assertTrue(added.path("before_json").isNull()); assertTrue(added.path("before_sha256").isNull());
        assertEquals(id(12), snippet(added, "after").path("parent_id").asText());
        var removed = record(compare(right, left), "NODE", id(15)); assertEquals("REMOVED", removed.path("status").asText()); assertTrue(removed.path("after_json").isNull());
        var moved = body(); add(moved, 16, 10, 1, "SECTION").withObject("content").put("title", "Other section");
        node(moved, 14).put("parent_id", id(16)).put("ordinal", 0);
        var result = compare(left, moved); assertEquals("CHANGED", record(result, "NODE", id(14)).path("status").asText());
        assertEquals(List.of(id(11)), strings(result.path("left_affected_section_ids")));
        assertEquals(List.of(id(16)), strings(result.path("right_affected_section_ids")));
    }

    @Test void tableRowAndColumnReorderRetainCellIdentitiesAndExactAxisOrder() throws Exception {
        for (String axis : List.of("row_ids", "column_ids")) {
            var left = body(); var right = body(); reverse(table(right).withArray(axis)); reorderTableChildren(right);
            var result = compare(left, right);
            assertEquals("CHANGED", record(result, "ORDER", id(20) + ":" + axis).path("status").asText());
            for (int cell = 41; cell <= 44; cell++) assertEquals("UNCHANGED", record(result, "TABLE_CELL", id(20) + ":" + id(cell)).path("status").asText());
            assertEquals(strings(table(right).path(axis)), strings(snippet(record(result, "ORDER", id(20) + ":" + axis), "after")));
            assertTrue(strings(result.path("affected_node_ids")).contains(id(20)));
        }
    }

    @Test void tableCellSerializationReorderIsExplicitEvenWithoutGeometryChange() throws Exception {
        var left = body(); var right = body(); reverse(table(right).withArray("cells"));
        var result = compare(left, right); assertEquals("CHANGED", record(result, "ORDER", id(20) + ":cells").path("status").asText());
        for (int cell = 41; cell <= 44; cell++) assertEquals("UNCHANGED", record(result, "TABLE_CELL", id(20) + ":" + id(cell)).path("status").asText());
    }

    @Test void tableSpanMergePreservesBothNodesAndReportsRemovedCell() throws Exception {
        var left = body(); var right = body(); var cells = table(right).withArray("cells");
        var first = (ObjectNode) cells.get(0); first.put("column_span", 2).withArray("node_ids").add(id(22)); cells.remove(1);
        var result = compare(left, right); var merged = record(result, "TABLE_CELL", id(20) + ":" + id(41));
        assertEquals("CHANGED", merged.path("status").asText()); assertEquals(2, snippet(merged, "after").path("column_span").intValue());
        assertEquals(List.of(id(21), id(22)), strings(snippet(merged, "after").path("node_ids")));
        assertEquals("REMOVED", record(result, "TABLE_CELL", id(20) + ":" + id(42)).path("status").asText());
    }

    @Test void tableNodeBindingChangesRemainDistinctFromCellTextChanges() throws Exception {
        var left = body(); var right = body(); var cells = table(right).withArray("cells");
        ((ObjectNode) cells.get(0)).putArray("node_ids").add(id(22)); ((ObjectNode) cells.get(1)).putArray("node_ids").add(id(21)); reorderTableChildren(right);
        var result = compare(left, right);
        for (int cell : List.of(41, 42)) assertEquals("CHANGED", record(result, "TABLE_CELL", id(20) + ":" + id(cell)).path("status").asText());
        assertEquals(id(22), snippet(record(result, "TABLE_CELL", id(20) + ":" + id(41)), "after").at("/node_ids/0").asText());
    }

    @Test void fieldEvidenceAndFactChangesPreserveCompleteFixedReferences() throws Exception {
        var left = body(); var right = body();
        ((ObjectNode) right.withArray("fields").get(0)).put("original_value", "11 mg").put("decimal_value", "11");
        ((ObjectNode) right.withArray("evidence_links").get(0)).put("relation", "CONFLICT");
        ((ObjectNode) right.withArray("fact_usages").get(0)).withArray("node_ids").add(id(14));
        var result = compare(left, right);
        for (var pair : List.of(List.of("FIELD", id(50)), List.of("EVIDENCE", id(70)), List.of("FACT", id(80)))) assertEquals("CHANGED", record(result, pair.get(0), pair.get(1)).path("status").asText());
        assertEquals(schema.canonical(left.at("/evidence_links/0/evidence")), schema.canonical(snippet(record(result, "EVIDENCE", id(70)), "after").path("evidence")));
        assertEquals(schema.canonical(right.required("fact_usages")), record(result, "FACT", id(80)).path("after_json").asText());
        assertTrue(strings(result.path("affected_node_ids")).containsAll(List.of(id(13), id(14))));
    }

    @Test void repeatedFactVersionWithDistinctTargetsIsValidAndNeverOverwritten() throws Exception {
        var left = body(); var extra = left.withArray("fact_usages").addObject().put("fact_version_id", id(80));
        extra.putArray("node_ids").add(id(14)); extra.putArray("field_ids");
        var right = left.deepCopy(); var equal = compare(left, right); var fact = record(equal, "FACT", id(80));
        assertEquals("UNCHANGED", fact.path("status").asText()); assertEquals(2, snippet(fact, "before").size());
        assertEquals(schema.canonical(left.path("fact_usages")), fact.path("before_json").asText());
        reverse(right.withArray("fact_usages"));
        assertEquals("CHANGED", record(compare(left, right), "FACT", id(80)).path("status").asText());
        assertEquals(List.of(id(80), id(80)), strings(snippet(record(equal, "ORDER", "fact_usages"), "before")));
    }

    @Test void factVersionReplacementIsRemovalAndAdditionWithoutInventedLineage() throws Exception {
        var left = body(); var right = body(); ((ObjectNode) right.withArray("fact_usages").get(0)).put("fact_version_id", id(81));
        var result = compare(left, right);
        assertEquals("REMOVED", record(result, "FACT", id(80)).path("status").asText());
        assertEquals("ADDED", record(result, "FACT", id(81)).path("status").asText());
        assertFalse(result.has("fact_lineage")); assertTrue(strings(result.path("affected_node_ids")).contains(id(13)));
    }

    @Test void fieldOnlyFactAssociationResolvesItsActualNodeAndSection() throws Exception {
        var left = body(); var usage = (ObjectNode) left.withArray("fact_usages").get(0); usage.putArray("node_ids");
        var right = left.deepCopy(); ((ObjectNode) right.withArray("fact_usages").get(0)).put("fact_version_id", id(81));
        var result = compare(left, right);
        assertEquals(List.of(id(13)), strings(result.path("affected_node_ids")));
        assertEquals(List.of(id(11)), strings(result.path("left_affected_section_ids")));
        assertEquals(List.of(id(11)), strings(result.path("right_affected_section_ids")));
    }

    @Test void nodeSerializationOrderAndTemplateMetadataAreNeverSilentlyDropped() throws Exception {
        var left = body(); var right = body(); reverse(right.withArray("nodes")); right.put("template_version", id(99));
        var result = compare(left, right);
        assertEquals("CHANGED", record(result, "ORDER", "nodes").path("status").asText());
        assertEquals("CHANGED", record(result, "BODY_METADATA", "body").path("status").asText());
        assertEquals(left.required("nodes").size(), result.required("affected_node_ids").size());
        for (var node : left.required("nodes")) assertEquals("UNCHANGED", record(result, "NODE", node.path("node_id").asText()).path("status").asText());
    }

    @Test void fieldEvidenceAndFactArrayOrderIsComparedWithoutChangingStableRecords() throws Exception {
        var left = body();
        var field = ((ObjectNode) left.withArray("fields").get(0)).deepCopy().put("field_id", id(51)).put("field_definition_id", id(61)).put("node_id", id(14));
        field.putArray("evidence_link_ids").add(id(71)); left.withArray("fields").add(field); node(left, 14).withArray("field_ids").add(id(51));
        var link = ((ObjectNode) left.withArray("evidence_links").get(0)).deepCopy().put("evidence_link_id", id(71));
        link.putArray("node_ids").add(id(14)); left.withArray("evidence_links").add(link); node(left, 14).withArray("evidence_link_ids").add(id(71));
        var fact = left.withArray("fact_usages").addObject().put("fact_version_id", id(81)); fact.putArray("node_ids").add(id(14)); fact.putArray("field_ids").add(id(51));
        var right = left.deepCopy();
        for (String category : List.of("fields", "evidence_links", "fact_usages")) reverse(right.withArray(category));
        var result = compare(left, right);
        for (String category : List.of("fields", "evidence_links", "fact_usages")) assertEquals("CHANGED", record(result, "ORDER", category).path("status").asText());
        for (var pair : List.of(List.of("FIELD", id(50)), List.of("FIELD", id(51)), List.of("EVIDENCE", id(70)), List.of("EVIDENCE", id(71)), List.of("FACT", id(80)), List.of("FACT", id(81))))
            assertEquals("UNCHANGED", record(result, pair.get(0), pair.get(1)).path("status").asText());
    }

    @Test void malformedGraphReferencesAndInvalidTableCoverageFailInsteadOfReturningPartialSuccess() throws Exception {
        for (int mutation = 0; mutation < 4; mutation++) {
            var bad = body();
            if (mutation == 0) node(bad, 13).put("parent_id", id(999));
            if (mutation == 1) node(bad, 14).withArray("field_ids").add(id(999));
            if (mutation == 2) table(bad).withArray("cells").remove(0);
            if (mutation == 3) ((ObjectNode) table(bad).withArray("cells").get(0)).putArray("node_ids").add(id(999));
            var typedBad = bind(bad); var valid = typed(body());
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, valid, typedBad, LIMITS)).code());
        }
    }

    @Test void eachExplicitCapacityFailsClosedWithoutAPartialResult() throws Exception {
        var typed = typed(body());
        for (int dimension = 0; dimension < 8; dimension++) {
            var limit = smaller(dimension);
            assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, typed, typed, limit)).code(), "capacity dimension " + dimension);
        }
        assertThrows(IllegalArgumentException.class, () -> new DocumentStructuralComparisonEngine.Limits(0, 1, 1, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DocumentStructuralComparisonEngine.Limits(1, 1, 1, 1, 1, 1, 1, 1, 86_400_001));
    }

    @Test void fullUtf8InputLimitIsIndependentOfTextCodePointCapacity() throws Exception {
        var body = body(); text(body, 13, "\uD83D\uDE00".repeat(100)); var typed = typed(body());
        int bytes = ContentReadAuthorization.canonicalBytes(typed).length;
        assertTrue(bytes > schema.canonical(typed).length());
        var exact = new DocumentStructuralComparisonEngine.Limits(128, 32, 512, bytes, 2_000_000, 256, 100_000, 20_000_000, 60_000);
        assertTrue(engine.compare(REQUEST, typed, typed, exact).at("/coverage/complete").booleanValue());
        var shortInput = new DocumentStructuralComparisonEngine.Limits(128, 32, 512, bytes - 1, 2_000_000, 256, 100_000, 20_000_000, 60_000);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, typed, typed, shortInput)).code());
    }

    @Test void addedAndRemovedTextCountsTowardTheExactCombinedCodePointBudget() throws Exception {
        var left = body(); var right = body();
        add(right, 15, 12, 2, "TEXT").withObject("content").put("text", "\uD83D\uDE00\u0301".repeat(12)).putArray("marks");
        int points = 0;
        for (var body : List.of(left, right)) for (var node : body.path("nodes")) if ("TEXT".equals(node.at("/content/kind").asText())) {
            String value = node.at("/content/text").asText(); points += value.codePointCount(0, value.length());
        }
        var a = typed(left); var b = typed(right);
        var exact = new DocumentStructuralComparisonEngine.Limits(128, 32, 512, 1_000_000, 2_000_000, 256, points, 20_000_000, 60_000);
        var below = new DocumentStructuralComparisonEngine.Limits(128, 32, 512, 1_000_000, 2_000_000, 256, points - 1, 20_000_000, 60_000);
        assertTrue(engine.compare(REQUEST, a, b, exact).at("/coverage/complete").booleanValue());
        assertTrue(engine.compare(REQUEST, b, a, exact).at("/coverage/complete").booleanValue());
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, a, b, below)).code());
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, b, a, below)).code());
    }

    @Test void evenRehashedTextRangesMustQuoteTheActualFixedNodeAtThoseOffsets() throws Exception {
        var left = body(); var right = body(); text(left, 13, "ABC"); text(right, 13, "AXC");
        var result = compare(left, right);
        var selected = (ObjectNode) record(result, "NODE", id(13)).at("/text_change/left"); selected.put("start", 0).put("end", 1);
        result.withObject("coverage").put("delivered_sha256", schema.hash(result.path("records")));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> schema.validate(result)).code());
    }

    @Test void returnedCoreIsClosedAndCoverageCannotClaimTruncatedSuccess() throws Exception {
        var result = compare(body(), body()); var extra = result.deepCopy().put("review_ready", true);
        assertThrows(SchemaRejection.class, () -> schema.validate(extra));
        var partial = result.deepCopy(); partial.withObject("coverage").put("complete", false);
        assertThrows(SchemaRejection.class, () -> schema.validate(partial));
        var truncated = result.deepCopy(); truncated.withArray("records").remove(0);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> schema.validate(truncated)).code());
        var changed = result.deepCopy(); ((ObjectNode) changed.withArray("records").get(0)).put("before_sha256", "f".repeat(64));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> schema.validate(changed)).code());
        var validated = schema.validate(result); validated.put("kind", "changed"); assertNotEquals(validated, result);
    }

    @Test void verifiesTheCompleteResultAgainstBothActualTypedInputs() throws Exception {
        var left = body(); var right = body(); text(right, 13, "A different fixed text");
        var a = typed(left); var b = typed(right); var result = engine.compare(REQUEST, a, b, LIMITS); var before = result.deepCopy();
        var verified = engine.verifyAgainstInputs(REQUEST, a, b, result, LIMITS);
        assertEquals(schema.canonical(result), schema.canonical(verified)); assertEquals(before, result);
        verified.put("semantic_assessment", "tampered"); assertEquals(before, result);
        assertEquals(schema.canonical(before), schema.canonical(engine.verifyAgainstInputs(REQUEST, a, b, JSON.readTree(schema.canonical(before)), LIMITS)));
    }

    @Test void rejectsTruncatedRecordsEvenWhenCountAndDeliveredHashAreRecomputed() throws Exception {
        var left = body(); var right = body(); text(right, 13, "Changed node must not disappear");
        var result = compare(left, right); var records = result.withArray("records");
        for (int i = 0; i < records.size(); i++) if ("NODE".equals(records.get(i).path("category").asText()) && id(13).equals(records.get(i).path("identity").asText())) { records.remove(i); break; }
        result.put("record_count", records.size()); rehashDelivered(result);
        assertTrue(result.at("/coverage/complete").booleanValue());
        // Self-consistent hashes cannot attest that the full fixed input was delivered.
        assertEquals(schema.canonical(result), schema.canonical(schema.validate(result)));
        rejectedAgainstInputs(left, right, result);
    }

    @Test void rejectsFabricatedRecordIdentityEvenWhenDeliveredHashIsRecomputed() throws Exception {
        var left = body(); var right = body(); var result = compare(left, right);
        ((ObjectNode) record(result, "FIELD", id(50))).put("identity", id(999)); rehashDelivered(result);
        rejectedAgainstInputs(left, right, result);
    }

    @Test void rejectsInventedAndOmittedNodesInEachDerivedAffectedIdList() throws Exception {
        var left = body(); var right = body(); text(right, 13, "Changed"); var result = compare(left, right);
        for (String key : List.of("affected_node_ids", "left_affected_section_ids", "right_affected_section_ids")) {
            assertFalse(result.required(key).isEmpty());
            var invented = result.deepCopy(); invented.withArray(key).add(id(999)); rejectedAgainstInputs(left, right, invented);
            var omitted = result.deepCopy(); omitted.withArray(key).removeAll(); rejectedAgainstInputs(left, right, omitted);
        }
    }

    @Test void rejectsDifferentLeftBodyHashEvenWhenInputAndSelectionHashesAreRecomputed() throws Exception {
        var left = body(); var right = body(); text(right, 13, "Different bytes"); var result = compare(left, right);
        assertNotEquals(result.required("left_body_sha256"), result.required("right_body_sha256"));
        result.set("left_body_sha256", result.required("right_body_sha256")); rehashInput(result);
        assertEquals(schema.canonical(result), schema.canonical(schema.validate(result)));
        rejectedAgainstInputs(left, right, result);
    }

    @Test void genuineImageAndFormulaMetadataChangesRetainExactBeforeAndAfterContent() throws Exception {
        var left = body(); node(left, 14).set("content", imageContent(201, "Original image", "Original caption"));
        var formula = add(left, 15, 12, 2, "FORMULA").withObject("content");
        formula.put("latex", "x+y").put("description", "Original formula").putNull("rendered_artifact_id");
        var right = left.deepCopy(); node(right, 14).set("content", imageContent(202, "Changed image", "Changed caption"));
        node(right, 15).withObject("content").put("latex", "x-y").put("description", "Changed formula").put("rendered_artifact_id", id(203));
        var result = compare(left, right);
        for (int id : List.of(14, 15)) {
            var changed = record(result, "NODE", id(id)); assertEquals("CHANGED", changed.path("status").asText()); assertTrue(changed.path("text_change").isNull());
            assertEquals(schema.canonical(node(left, id)), changed.path("before_json").asText());
            assertEquals(schema.canonical(node(right, id)), changed.path("after_json").asText());
        }
        assertEquals(id(201), snippet(record(result, "NODE", id(14)), "before").at("/content/artifact_id").asText());
        assertEquals(id(202), snippet(record(result, "NODE", id(14)), "after").at("/content/artifact_id").asText());
        assertEquals(id(203), snippet(record(result, "NODE", id(15)), "after").at("/content/rendered_artifact_id").asText());
        assertEquals(List.of(id(14), id(15)), strings(result.path("affected_node_ids")));
    }

    @Test void sameStableNodeChangingFromTextToImageDoesNotInventTextRanges() throws Exception {
        var left = body(); var right = body(); node(right, 14).set("content", imageContent(201, "Replacement", "Actual supplied caption"));
        var result = compare(left, right); var changed = record(result, "NODE", id(14));
        assertEquals("CHANGED", changed.path("status").asText()); assertTrue(changed.path("text_change").isNull());
        assertEquals("TEXT", snippet(changed, "before").at("/content/kind").asText());
        assertEquals("IMAGE", snippet(changed, "after").at("/content/kind").asText());
        assertEquals("untouched", snippet(changed, "before").at("/content/text").asText());
        assertEquals(id(201), snippet(changed, "after").at("/content/artifact_id").asText());
        assertEquals(List.of(id(14)), strings(result.path("affected_node_ids")));
    }

    @Test void identicalFactUsageTargetsAreRejectedRatherThanGroupedAsDistinctFacts() throws Exception {
        var bad = body(); bad.withArray("fact_usages").add(bad.required("fact_usages").get(0).deepCopy());
        var typedBad = bind(bad); var valid = typed(body());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, valid, typedBad, LIMITS)).code());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, typedBad, valid, LIMITS)).code());
    }

    @Test void quadraticTableWorkIsRejectedBeforeAnInvalidTableReachesSemanticValidation() throws Exception {
        var bad = body(); var cells = table(bad).withArray("cells"); var prototype = (ObjectNode) cells.get(0).deepCopy(); cells.removeAll();
        int count = 1024;
        for (int i = 0; i < count; i++) cells.add(prototype.deepCopy().put("cell_id", id(2000 + i)));
        // Individually schema-valid cells overlap: real semantic validation must reject them.
        var typedBad = bind(bad); var valid = typed(body());
        long bytes = ContentReadAuthorization.canonicalBytes(typedBad).length;
        long pairChecks = (long) count * (count - 1) / 2;
        long smallWork = bytes + 4096;
        assertTrue(smallWork < pairChecks, "The budget must fit canonical input bytes but not the rectangle pairs");
        var limited = new DocumentStructuralComparisonEngine.Limits(128, count, 10_000, 1_000_000, 2_000_000, 256, 100_000, smallWork, 60_000);
        var generous = new DocumentStructuralComparisonEngine.Limits(128, count, 10_000, 1_000_000, 2_000_000, 256, 100_000, 20_000_000, 60_000);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, typedBad, valid, limited)).code());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, typedBad, valid, generous)).code());
    }

    @Test void tinyOutputBudgetsRejectDuringRecordAccumulationAndVerification() throws Exception {
        var body = typed(body()); var complete = engine.compare(REQUEST, body, body, LIMITS);
        int firstRecordBytes = ContentReadAuthorization.canonicalBytes(complete.required("records").get(0)).length;
        assertTrue(firstRecordBytes > 1);
        for (int bytes : List.of(1, firstRecordBytes - 1, firstRecordBytes + 1)) {
            var limited = new DocumentStructuralComparisonEngine.Limits(128, 32, 512, 1_000_000, bytes, 256, 100_000, 20_000_000, 60_000);
            assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(REQUEST, body, body, limited)).code());
            assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.verifyAgainstInputs(REQUEST, body, body, complete, limited)).code());
        }
    }

    private void rejectedAgainstInputs(ObjectNode left, ObjectNode right, JsonNode result) throws Exception {
        var a = typed(left); var b = typed(right);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> engine.verifyAgainstInputs(REQUEST, a, b, result, LIMITS)).code());
    }
    private void rehashDelivered(ObjectNode result) throws Exception {
        result.withObject("coverage").put("delivered_sha256", schema.hash(result.required("records")));
    }
    private void rehashInput(ObjectNode result) throws Exception {
        var input = result.deepCopy();
        for (String key : List.of("input_sha256", "record_count", "records", "affected_node_ids", "left_affected_section_ids", "right_affected_section_ids", "coverage")) input.remove(key);
        String hash = schema.hash(input); result.put("input_sha256", hash); result.withObject("coverage").put("selection_sha256", hash);
    }
    private static ObjectNode imageContent(int artifact, String alt, String caption) throws Exception {
        return JSON.createObjectNode().put("kind", "IMAGE").put("artifact_id", id(artifact))
                .put("sha256", WireValues.sha256(("fixture image bytes " + artifact).getBytes(StandardCharsets.UTF_8))).put("alt_text", alt).put("caption", caption);
    }

    private ObjectNode compare(ObjectNode left, ObjectNode right) throws Exception {
        return engine.compare(REQUEST, typed(left), typed(right), LIMITS);
    }
    private InterfaceProtocol.DocumentBody typed(ObjectNode body) throws Exception {
        DocumentBodySemantics.verify(schema.catalogue, body, new DocumentBodySemantics.Limits(128, 32, 512));
        return bind(body);
    }
    private InterfaceProtocol.DocumentBody bind(ObjectNode body) throws Exception {
        return TypedSchemaBindings.bind(schema.catalogue, InterfaceBindings.DOCUMENT_BODY, body).read();
    }
    private void checkCoverage(JsonNode result) throws Exception {
        assertEquals(result.path("records").size(), result.path("record_count").intValue());
        assertTrue(result.at("/coverage/complete").booleanValue()); assertTrue(result.at("/coverage/next_cursor").isNull());
        assertEquals(result.path("input_sha256"), result.at("/coverage/selection_sha256"));
        assertEquals(schema.hash(result.path("records")), result.at("/coverage/delivered_sha256").asText());
    }
    private static JsonNode record(JsonNode result, String category, String id) {
        var matches = new ArrayList<JsonNode>();
        result.required("records").forEach(r -> { if (category.equals(r.path("category").asText()) && id.equals(r.path("identity").asText())) matches.add(r); });
        assertEquals(1, matches.size(), category + ":" + id); return matches.get(0);
    }
    private static JsonNode snippet(JsonNode record, String side) throws Exception { return JSON.readTree(record.required(side + "_json").asText()); }
    private static void selection(JsonNode value, int start, int end, String quote) throws Exception {
        assertEquals(start, value.path("start").intValue()); assertEquals(end, value.path("end").intValue()); assertEquals(quote, value.path("quote").asText());
        assertEquals(quote.codePointCount(0, quote.length()), end - start);
        assertEquals(WireValues.sha256(quote.getBytes(StandardCharsets.UTF_8)), value.path("quote_sha256").asText());
    }
    private static List<String> strings(JsonNode array) { var out = new ArrayList<String>(); array.forEach(v -> out.add(v.asText())); return out; }
    private static void reverse(ArrayNode array) { var copy = new ArrayList<JsonNode>(); array.forEach(copy::add); Collections.reverse(copy); array.removeAll(); copy.forEach(array::add); }
    private static ObjectNode node(ObjectNode body, int id) { for (var n : body.required("nodes")) if (id(id).equals(n.path("node_id").asText())) return (ObjectNode) n; throw new AssertionError("missing fixture node " + id); }
    private static ObjectNode table(ObjectNode body) { return node(body, 20).withObject("content"); }
    private static void text(ObjectNode body, int id, String text) { node(body, id).withObject("content").put("text", text); }
    private static void reorderTableChildren(ObjectNode body) {
        var table = table(body); var rows = strings(table.path("row_ids")); var cols = strings(table.path("column_ids")); var cells = new ArrayList<JsonNode>(); table.path("cells").forEach(cells::add);
        cells.sort(Comparator.comparingInt((JsonNode c) -> rows.indexOf(c.path("row_id").asText())).thenComparingInt(c -> cols.indexOf(c.path("column_id").asText())));
        int ordinal = 0; for (var cell : cells) for (var child : cell.path("node_ids")) for (var n : body.path("nodes")) if (child.equals(n.path("node_id"))) ((ObjectNode) n).put("ordinal", ordinal++);
    }
    private static DocumentStructuralComparisonEngine.Limits smaller(int field) {
        return new DocumentStructuralComparisonEngine.Limits(field == 0 ? 1 : 128, field == 1 ? 1 : 32, field == 2 ? 1 : 512,
                field == 3 ? 1 : 1_000_000, field == 4 ? 1 : 2_000_000, field == 5 ? 1 : 256, field == 6 ? 1 : 100_000, field == 7 ? 1 : 20_000_000, 60_000);
    }
    private static ObjectNode body() throws Exception {
        var body = JSON.createObjectNode().put("schema_version", "1.0.0").put("template_version", id(4));
        for (String key : List.of("nodes", "fields", "evidence_links", "fact_usages")) body.putArray(key);
        add(body, 10, null, 0, "DOCUMENT").withObject("content").put("title", "Fixture document");
        add(body, 11, 10, 0, "SECTION").withObject("content").put("title", "Section");
        add(body, 12, 11, 0, "PARAGRAPH").withObject("content").put("title", "Paragraph");
        add(body, 13, 12, 0, "TEXT").withObject("content").put("text", "A\uD83D\uDE00e\u0301Z").putArray("marks");
        add(body, 14, 12, 1, "TEXT").withObject("content").put("text", "untouched").putArray("marks");
        var table = add(body, 20, 11, 1, "TABLE").withObject("content").put("caption", "2 by 2");
        table.putArray("row_ids").add(id(31)).add(id(32)); table.putArray("column_ids").add(id(33)).add(id(34)); var cells = table.putArray("cells");
        for (int i = 0; i < 4; i++) {
            add(body, 21 + i, 20, i, "TEXT").withObject("content").put("text", "Cell " + i).putArray("marks");
            cells.addObject().put("cell_id", id(41 + i)).put("row_id", id(31 + i / 2)).put("column_id", id(33 + i % 2)).put("row_span", 1).put("column_span", 1).putArray("node_ids").add(id(21 + i));
        }
        node(body, 13).withArray("field_ids").add(id(50)); node(body, 13).withArray("evidence_link_ids").add(id(70));
        var field = body.withArray("fields").addObject().put("field_id", id(50)).put("node_id", id(13)).put("field_definition_id", id(60)).put("original_value", "10 mg").put("decimal_value", "10").put("unit", "mg");
        field.putNull("model").putNull("condition").putArray("evidence_link_ids").add(id(70));
        var evidence = body.withArray("evidence_links").addObject().put("evidence_link_id", id(70)).put("relation", "SUPPORT"); evidence.putArray("node_ids").add(id(13));
        var reference = evidence.putObject("evidence").put("schema_version", "1.0.0").put("tenant_id", id(101)).put("project_id", id(102))
                .put("source_id", id(103)).put("source_version", id(104)).put("parse_version", id(105)).put("chunk_id", id(106)).put("approval_revision", id(107))
                .put("locator_kind", "TXT").put("quote_hash", WireValues.sha256("fixture quote".getBytes(StandardCharsets.UTF_8)));
        reference.putObject("locator").put("kind", "TXT").put("encoding", "UTF-8").put("newline_policy", "PRESERVE").put("char_start", 0).put("char_end", 13).put("line_start", 1).put("line_end", 1);
        var fact = body.withArray("fact_usages").addObject().put("fact_version_id", id(80)); fact.putArray("node_ids").add(id(13)); fact.putArray("field_ids").add(id(50));
        return body;
    }
    private static ObjectNode add(ObjectNode body, int n, Integer parent, int ordinal, String kind) {
        var node = body.withArray("nodes").addObject().put("node_id", id(n)).put("ordinal", ordinal); if (parent == null) node.putNull("parent_id"); else node.put("parent_id", id(parent));
        node.putArray("field_ids"); node.putArray("evidence_link_ids"); node.putObject("content").put("kind", kind); return node;
    }
    private static String id(int n) { return "00000000-0000-4000-8000-%012d".formatted(n); }
}
