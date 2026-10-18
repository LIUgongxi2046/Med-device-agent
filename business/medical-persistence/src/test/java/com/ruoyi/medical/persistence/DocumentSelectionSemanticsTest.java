package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.CommonBindings;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure algorithm checks. The Unicode/table vectors mirror the existing SIMULATION frontend checks. */
public final class DocumentSelectionSemanticsTest {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private static final String REVISION = id(90);
    // Independently calculated by the existing frontend json-canonicalize implementation.
    private static final String TEXT_NODE_SHA = "e0f0e50817a861bec16aafdfecd212f15acb509d15d817d5fd324d38548eac93";
    private static final String TEXT_QUOTE_SHA = "49de475c698dafea83a851698246f6abad13ccaa7cff6a592e59e9cc787a04f6";
    private static final String TABLE_NODE_SHA = "3b4804d2a09ad412490f90479ed05a3349f5ae65f16dfb40ef1c8e6ceb47be69";
    private final SchemaCatalogue schemas = SchemaCatalogue.fromClasspath();
    public DocumentSelectionSemanticsTest() throws Exception { }

    @Test void matchesFrontendFullNodeQuoteAndCanonicalSelectionGoldenBytes() throws Exception {
        var body = body();
        var selection = select(new CommonProtocol.TextSelection("TEXT", id(3), TEXT_NODE_SHA, 1, 6, TEXT_QUOTE_SHA));
        var verified = verify(body, selection);
        String expected = "{\"document_revision\":\"" + REVISION + "\",\"segments\":[{\"end_cp\":6,\"kind\":\"TEXT\","
                + "\"node_id\":\"" + id(3) + "\",\"node_sha256\":\"" + TEXT_NODE_SHA + "\","
                + "\"quote_sha256\":\"" + TEXT_QUOTE_SHA + "\",\"start_cp\":1}]}";
        assertEquals(TEXT_NODE_SHA, nodeSha(node(body, 3)));
        assertEquals(selection, verified.selection());
        assertEquals(expected, verified.canonicalJson());
        assertEquals("2a979f5888230748e9095286e24b63af12e47642166fff81707dc2e335acce8e", verified.sha256());
        assertEquals(sha(expected), verified.sha256());
        assertEquals(id(3), verified.anchorNodeId());
        assertThrows(UnsupportedOperationException.class, () -> verified.selection().segments().clear());
    }

    @Test void permitsFullTextSupplementaryCharactersAndUnnormalizedCombiningCharacters() throws Exception {
        var body = body();
        assertDoesNotThrow(() -> verify(body, select(text(body, 3, 0, 6))));
        assertDoesNotThrow(() -> verify(body, select(text(body, 3, 1, 2))));
        assertDoesNotThrow(() -> verify(body, select(text(body, 3, 3, 4))));
        var combining = text(body, 3, 2, 4);
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.TextSelection("TEXT", id(3),
                combining.nodeSha256(), 2, 4, sha("é"))));
        assertEquals(sha("😀"), text(body, 3, 1, 2).quoteSha256());
    }

    @Test void verifiesACompleteActualRepositoryDocumentWithoutTruncation() throws Exception {
        String actual = Files.readString(root().resolve("backend-design/接口补充设计.md"));
        var body = replace(body(), 3, new InterfaceProtocol.TextContent("TEXT", actual, List.of()));
        var complete = text(body, 3, 0, actual.codePointCount(0, actual.length()));
        assertEquals(sha(actual), complete.quoteSha256());
        assertEquals(select(complete), verify(body, select(complete)).selection());
    }

    @Test void rejectsEmptyAbsentMismatchedAndOutOfBoundsRanges() throws Exception {
        var body = body();
        rejected(ErrorCode.SELECTION_INVALID, body, select());
        for (long[] range : List.of(new long[]{-1, 1}, new long[]{1, 1}, new long[]{2, 1},
                new long[]{0, 7}, new long[]{0, 9007199254740991L}, new long[]{0, Long.MAX_VALUE})) {
            rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TextSelection("TEXT", id(3),
                    TEXT_NODE_SHA, range[0], range[1], TEXT_QUOTE_SHA)));
        }
        rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TextSelection("TEXT", id(99), TEXT_NODE_SHA, 0, 1, TEXT_QUOTE_SHA)));
        rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TextSelection("TEXT", id(5), TEXT_NODE_SHA, 0, 1, TEXT_QUOTE_SHA)));
        var empty = replace(body, 3, new InterfaceProtocol.TextContent("TEXT", "", List.of()));
        rejected(ErrorCode.SELECTION_INVALID, empty, select(new CommonProtocol.TextSelection("TEXT", id(3), nodeSha(node(empty, 3)), 0, 1, TEXT_QUOTE_SHA)));
    }

    @Test void rejectsDuplicateAndReverseNodesButUsesDepthFirstSiblingOrderRatherThanArrayOrder() throws Exception {
        var body = body(); var first = text(body, 3, 0, 1); var second = text(body, 4, 0, 1);
        rejected(ErrorCode.SELECTION_INVALID, body, select(first, first));
        rejected(ErrorCode.SELECTION_INVALID, body, select(first, text(body, 3, 1, 2)));
        rejected(ErrorCode.SELECTION_INVALID, body, select(second, first));
        var nodes = new ArrayList<>(body.nodes()); Collections.reverse(nodes);
        var reversed = withNodes(body, nodes);
        var complete = select(first, second, image(), table());
        assertEquals(complete, verify(reversed, complete).selection());
        assertEquals(id(3), verify(reversed, complete).anchorNodeId());
    }

    @Test void rejectsAmbiguousMissingCyclicAndUnreachableNodePositions() throws Exception {
        var body = body(); var selection = select(text(body, 3, 0, 1));
        var duplicate = new ArrayList<>(body.nodes()); duplicate.add(node(body, 3));
        rejected(ErrorCode.SELECTION_INVALID, withNodes(body, duplicate), selection);
        rejected(ErrorCode.SELECTION_INVALID, replaceNode(body, 4, positioned(node(body, 4), id(2), 0)), selection);
        rejected(ErrorCode.SELECTION_INVALID, replaceNode(body, 4, positioned(node(body, 4), id(99), 1)), selection);
        rejected(ErrorCode.SELECTION_INVALID, replaceNode(body, 2, positioned(node(body, 2), id(3), 0)), selection);
        rejected(ErrorCode.SELECTION_INVALID, replaceNode(body, 4, positioned(node(body, 4), id(4), 1)), selection);
    }

    @Test void fullNodeDigestIncludesMetadataAndDoesNotAcceptContentOnlyHash() throws Exception {
        var body = body(); var segment = text(body, 3, 0, 1); var original = node(body, 3);
        var changedMarks = replace(body, 3, new InterfaceProtocol.TextContent("TEXT", "甲😀e\u0301\n乙", List.of()));
        rejected(ErrorCode.SELECTION_STALE, changedMarks, select(segment));
        rejected(ErrorCode.SELECTION_STALE, replaceNode(body, 3, positioned(original, id(2), 20)), select(segment));
        rejected(ErrorCode.SELECTION_STALE, replaceNode(body, 3, positioned(original, id(1), 20)), select(segment));
        var changedFields = new InterfaceProtocol.Node(original.nodeId(), original.parentId(), original.ordinal(), List.of(id(70)), List.of(id(71)), original.content());
        rejected(ErrorCode.SELECTION_STALE, replaceNode(body, 3, changedFields), select(segment));
        String contentOnly = WireValues.sha256(schemas.bind("interfaces", JSON.valueToTree(original.content()), "TextContent").canonicalBytes());
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.TextSelection("TEXT", id(3), contentOnly, 0, 1, segment.quoteSha256())));
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.TextSelection("TEXT", id(3), segment.nodeSha256(), 0, 1, "0".repeat(64))));
    }

    @Test void pinsDocumentRevisionWithoutRelocating() throws Exception {
        var body = body(); var selection = new CommonProtocol.Selection(id(89), List.of(text(body, 3, 0, 1)));
        rejected(ErrorCode.SELECTION_STALE, body, selection);
    }

    @Test void imageRequiresTheExactArtifactAndDigest() throws Exception {
        var body = body(); assertEquals(id(5), verify(body, select(image())).anchorNodeId());
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.ImageSelection("IMAGE", id(5), id(61), "a".repeat(64))));
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.ImageSelection("IMAGE", id(5), id(60), "b".repeat(64))));
        rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.ImageSelection("IMAGE", id(3), id(60), "a".repeat(64))));
    }

    @Test void tableMatchesFrontendHashAndOrderedUnionOfSelectedCellSpans() throws Exception {
        var body = body(); var verified = verify(body, select(table()));
        assertEquals(TABLE_NODE_SHA, nodeSha(node(body, 6)));
        assertEquals("52916089873d6d35e14080d7cf0d13967b23b36fac72648571f824079cf886a0", verified.sha256());
        assertEquals(List.of(id(12), id(14)), table().columnIds()); // The unselected middle column is not silently added.
        var spanningColumns = new CommonProtocol.TableSelection("TABLE", id(6), List.of(id(10)), List.of(id(13), id(14)), List.of(id(21)), TABLE_NODE_SHA);
        assertDoesNotThrow(() -> verify(body, select(spanningColumns)));
    }

    @Test void tableRejectsUnknownDuplicateReorderedOrIncorrectCoverageSelections() throws Exception {
        var body = body(); var original = table();
        for (var cells : List.of(List.of(id(23), id(20)), List.of(id(20), id(20)), List.of(id(99)), List.<String>of())) {
            rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TableSelection("TABLE", id(6), original.rowIds(), original.columnIds(), cells, TABLE_NODE_SHA)));
        }
        for (var rows : List.of(List.of(id(10)), List.of(id(11), id(10)), List.of(id(99)), List.<String>of())) {
            rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TableSelection("TABLE", id(6), rows, original.columnIds(), original.cellIds(), TABLE_NODE_SHA)));
        }
        for (var columns : List.of(List.of(id(12)), List.of(id(14), id(12)), List.of(id(12), id(13), id(14)), List.<String>of())) {
            rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TableSelection("TABLE", id(6), original.rowIds(), columns, original.cellIds(), TABLE_NODE_SHA)));
        }
        rejected(ErrorCode.SELECTION_STALE, body, select(new CommonProtocol.TableSelection("TABLE", id(6), original.rowIds(), original.columnIds(), original.cellIds(), "0".repeat(64))));
        rejected(ErrorCode.SELECTION_INVALID, body, select(new CommonProtocol.TableSelection("TABLE", id(3), original.rowIds(), original.columnIds(), original.cellIds(), TABLE_NODE_SHA)));
    }

    @Test void tableRejectsMalformedGeometryWithoutAllocatingASpanSizedGrid() throws Exception {
        var body = body(); var content = (InterfaceProtocol.TableContent) node(body, 6).content();
        var missing = new ArrayList<>(content.cells()); missing.remove(0);
        rejected(ErrorCode.SELECTION_INVALID, replace(body, 6, withCells(content, missing)), select(table()));
        var duplicate = new ArrayList<>(content.cells()); duplicate.add(content.cells().get(0));
        rejected(ErrorCode.SELECTION_INVALID, replace(body, 6, withCells(content, duplicate)), select(table()));
        var overlap = new ArrayList<>(content.cells()); overlap.set(0, cell(23, 11, 13, 1, 1));
        rejected(ErrorCode.SELECTION_INVALID, replace(body, 6, withCells(content, overlap)), select(table()));
        var overflow = new ArrayList<>(content.cells()); overflow.set(0, cell(23, 11, 14, 9007199254740991L, 1));
        rejected(ErrorCode.SELECTION_INVALID, replace(body, 6, withCells(content, overflow)), select(table()));
    }

    @Test void rejectsSelectedCellOverlapWithTextImagesNestedTablesAndTheirDescendants() throws Exception {
        var body = cellDescendants(); var outer = tableSelection(body, 6, List.of(id(10), id(11)), List.of(id(12)), List.of(id(20)));
        // Verify this is a complete domain-valid body, not an ambiguous cell ownership fixture.
        DocumentBodySemantics.verify(schemas, JSON.valueToTree(body), new DocumentBodySemantics.Limits(100, 100, 1000));
        for (int text : List.of(34, 35, 38)) rejected(ErrorCode.SELECTION_INVALID, body, select(outer, text(body, text, 0, 1)));
        rejected(ErrorCode.SELECTION_INVALID, body, select(outer, new CommonProtocol.ImageSelection("IMAGE", id(31), id(60), "a".repeat(64))));
        var nested = tableSelection(body, 32, List.of(id(41)), List.of(id(42)), List.of(id(40)));
        rejected(ErrorCode.SELECTION_INVALID, body, select(outer, nested));
        rejected(ErrorCode.SELECTION_INVALID, body, select(nested, text(body, 35, 0, 1)));
    }

    @Test void permitsDisjointUnselectedCellSubtreesAndUnrelatedNodes() throws Exception {
        var body = cellDescendants();
        var outer = tableSelection(body, 6, List.of(id(10), id(11)), List.of(id(12)), List.of(id(20)));
        assertDoesNotThrow(() -> verify(body, select(text(body, 3, 0, 1), outer, text(body, 33, 0, 1))));
        var otherCell = tableSelection(body, 6, List.of(id(10)), List.of(id(13), id(14)), List.of(id(21)));
        var nested = tableSelection(body, 32, List.of(id(41)), List.of(id(42)), List.of(id(40)));
        assertDoesNotThrow(() -> verify(body, select(otherCell, nested)));
        assertDoesNotThrow(() -> verify(body, select(otherCell, text(body, 35, 0, 1))));
    }

    @Test void selectedTableCoverageRejectsUnknownOrMisparentedCellRoots() throws Exception {
        var body = cellDescendants();
        var outer = tableSelection(body, 6, List.of(id(10), id(11)), List.of(id(12)), List.of(id(20)));
        var removed = withNodes(body, body.nodes().stream().filter(node -> !node.nodeId().equals(id(38))).toList());
        rejected(ErrorCode.SELECTION_INVALID, removed, select(outer));
        var misplaced = replaceNode(body, 38, positioned(node(body, 38), id(1), 20));
        rejected(ErrorCode.SELECTION_INVALID, misplaced, select(outer));
        var content = (InterfaceProtocol.TableContent) node(body, 6).content();
        var duplicateRoots = content.cells().stream().map(cell -> cell.cellId().equals(id(23))
                ? new InterfaceProtocol.Cell(cell.cellId(), cell.rowId(), cell.columnId(), cell.rowSpan(), cell.columnSpan(), List.of(id(38))) : cell).toList();
        var ambiguous = replace(body, 6, withCells(content, duplicateRoots));
        rejected(ErrorCode.SELECTION_INVALID, ambiguous, select(tableSelection(ambiguous, 6,
                List.of(id(10), id(11)), List.of(id(12), id(14)), List.of(id(20), id(23)))));
    }

    @Test void originalSchemaStillRejectsMixedUnknownAndMissingSelectionProperties() throws Exception {
        var value = JSON.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(select(text(body(), 3, 0, 1)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) value.withArray("segments").get(0)).put("image_artifact_id", id(60));
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class,
                () -> TypedSchemaBindings.bind(schemas, CommonBindings.SELECTION, value)).code());
        var missing = JSON.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(select(image()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) missing.withArray("segments").get(0)).remove("image_sha256");
        assertThrows(SchemaRejection.class, () -> TypedSchemaBindings.bind(schemas, CommonBindings.SELECTION, missing));
        assertEquals(ErrorCode.SELECTION_INVALID, ErrorCode.parse("SELECTION_INVALID"));
        assertEquals(ErrorCode.SELECTION_STALE, ErrorCode.parse("SELECTION_STALE"));
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class, () -> ErrorCode.parse("SELECTION_GUESSED")).code());
    }

    @Test void preservesFrozenSharedSchemaAndCatalogueIdentity() throws Exception {
        assertEquals("dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915", WireValues.sha256(Files.readAllBytes(root().resolve("contracts/catalogue.json"))));
        assertEquals("0e2dcc451ca58cefdc4432ec126936a142237000ca786dd69fb82792da433444", WireValues.sha256(Files.readAllBytes(root().resolve("backend-design/interfaces.schema.yaml"))));
    }

    private DocumentSelectionSemantics.Verified verify(InterfaceProtocol.DocumentBody body, CommonProtocol.Selection selection) throws Exception {
        return DocumentSelectionSemantics.verify(schemas, REVISION, body, selection);
    }
    private void rejected(ErrorCode code, InterfaceProtocol.DocumentBody body, CommonProtocol.Selection selection) {
        assertEquals(code, assertThrows(TenantAccessException.class, () -> verify(body, selection)).code());
    }
    private CommonProtocol.TextSelection text(InterfaceProtocol.DocumentBody body, int identity, int start, int end) throws Exception {
        var node = node(body, identity); String text = ((InterfaceProtocol.TextContent) node.content()).text();
        String quote = text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
        return new CommonProtocol.TextSelection("TEXT", node.nodeId(), nodeSha(node), start, end, sha(quote));
    }
    private String nodeSha(InterfaceProtocol.Node node) throws Exception {
        return WireValues.sha256(schemas.bind("interfaces", JSON.valueToTree(node), "Node").canonicalBytes());
    }
    private CommonProtocol.TableSelection tableSelection(InterfaceProtocol.DocumentBody body, int identity,
            List<String> rows, List<String> columns, List<String> cells) throws Exception {
        return new CommonProtocol.TableSelection("TABLE", id(identity), rows, columns, cells, nodeSha(node(body, identity)));
    }
    private static CommonProtocol.Selection select(CommonProtocol.SelectionSegment... segments) { return new CommonProtocol.Selection(REVISION, List.of(segments)); }
    private static CommonProtocol.ImageSelection image() { return new CommonProtocol.ImageSelection("IMAGE", id(5), id(60), "a".repeat(64)); }
    private static CommonProtocol.TableSelection table() { return new CommonProtocol.TableSelection("TABLE", id(6), List.of(id(10), id(11)), List.of(id(12), id(14)), List.of(id(20), id(23)), TABLE_NODE_SHA); }
    private static InterfaceProtocol.DocumentBody body() {
        var table = new InterfaceProtocol.TableContent("TABLE", List.of(id(10), id(11)), List.of(id(12), id(13), id(14)),
                List.of(cell(23, 11, 14, 1, 1), cell(21, 10, 13, 1, 2), cell(20, 10, 12, 2, 1), cell(22, 11, 13, 1, 1)), "SIMULATION 合并表格");
        return new InterfaceProtocol.DocumentBody("1.0.0", id(91), List.of(
                node(1, null, 0, new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT, "SIMULATION 正文")),
                node(2, id(1), 0, new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.SECTION, "SIMULATION 章节")),
                node(3, id(2), 0, new InterfaceProtocol.TextContent("TEXT", "甲😀e\u0301\n乙", List.of(InterfaceProtocol.Mark.BOLD, InterfaceProtocol.Mark.ITALIC, InterfaceProtocol.Mark.UNDERLINE, InterfaceProtocol.Mark.SUPERSCRIPT))),
                node(4, id(2), 1, new InterfaceProtocol.TextContent("TEXT", "丙🧪丁", List.of(InterfaceProtocol.Mark.SUBSCRIPT))),
                node(5, id(1), 1, new InterfaceProtocol.ImageContent("IMAGE", id(60), "a".repeat(64), "SIMULATION 图片", "SIMULATION 图片说明")),
                node(6, id(1), 2, table)), List.of(), List.of(), List.of());
    }
    private static InterfaceProtocol.DocumentBody cellDescendants() {
        var body = body(); var table = (InterfaceProtocol.TableContent) node(body, 6).content();
        var cells = table.cells().stream().map(cell -> new InterfaceProtocol.Cell(cell.cellId(), cell.rowId(), cell.columnId(), cell.rowSpan(), cell.columnSpan(),
                cell.cellId().equals(id(20)) ? List.of(id(30), id(31), id(32), id(38)) : cell.cellId().equals(id(21)) ? List.of(id(33)) : List.<String>of())).toList();
        body = replace(body, 6, withCells(table, cells)); var nodes = new ArrayList<>(body.nodes());
        nodes.add(node(30, id(6), 0, new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.PARAGRAPH, "SIMULATION 单元格")));
        nodes.add(node(34, id(30), 0, new InterfaceProtocol.TextContent("TEXT", "SIMULATION 单元格文字", List.of())));
        nodes.add(node(31, id(6), 1, new InterfaceProtocol.ImageContent("IMAGE", id(60), "a".repeat(64), "SIMULATION 图片", "SIMULATION 图片说明")));
        nodes.add(node(32, id(6), 2, new InterfaceProtocol.TableContent("TABLE", List.of(id(41)), List.of(id(42)),
                List.of(new InterfaceProtocol.Cell(id(40), id(41), id(42), 1, 1, List.of(id(35)))), "SIMULATION 嵌套表格")));
        nodes.add(node(35, id(32), 0, new InterfaceProtocol.TextContent("TEXT", "SIMULATION 嵌套文字", List.of())));
        nodes.add(node(38, id(6), 3, new InterfaceProtocol.TextContent("TEXT", "SIMULATION 直接文字", List.of())));
        nodes.add(node(33, id(6), 4, new InterfaceProtocol.TextContent("TEXT", "SIMULATION 未选择单元格", List.of())));
        return withNodes(body, nodes);
    }
    private static InterfaceProtocol.Cell cell(int identity, int row, int column, long rowSpan, long columnSpan) { return new InterfaceProtocol.Cell(id(identity), id(row), id(column), rowSpan, columnSpan, List.of()); }
    private static InterfaceProtocol.Node node(int identity, String parent, long ordinal, InterfaceProtocol.NodeContent content) { return new InterfaceProtocol.Node(id(identity), parent, ordinal, List.of(), List.of(), content); }
    private static InterfaceProtocol.Node node(InterfaceProtocol.DocumentBody body, int identity) { return body.nodes().stream().filter(node -> node.nodeId().equals(id(identity))).findFirst().orElseThrow(); }
    private static InterfaceProtocol.Node positioned(InterfaceProtocol.Node node, String parent, long ordinal) { return new InterfaceProtocol.Node(node.nodeId(), parent, ordinal, node.fieldIds(), node.evidenceLinkIds(), node.content()); }
    private static InterfaceProtocol.DocumentBody replace(InterfaceProtocol.DocumentBody body, int identity, InterfaceProtocol.NodeContent content) {
        var node = node(body, identity); return replaceNode(body, identity, new InterfaceProtocol.Node(node.nodeId(), node.parentId(), node.ordinal(), node.fieldIds(), node.evidenceLinkIds(), content));
    }
    private static InterfaceProtocol.DocumentBody replaceNode(InterfaceProtocol.DocumentBody body, int identity, InterfaceProtocol.Node replacement) { return withNodes(body, body.nodes().stream().map(node -> node.nodeId().equals(id(identity)) ? replacement : node).toList()); }
    private static InterfaceProtocol.DocumentBody withNodes(InterfaceProtocol.DocumentBody body, List<InterfaceProtocol.Node> nodes) { return new InterfaceProtocol.DocumentBody(body.schemaVersion(), body.templateVersion(), nodes, body.fields(), body.evidenceLinks(), body.factUsages()); }
    private static InterfaceProtocol.TableContent withCells(InterfaceProtocol.TableContent table, List<InterfaceProtocol.Cell> cells) { return new InterfaceProtocol.TableContent(table.kind(), table.rowIds(), table.columnIds(), cells, table.caption()); }
    private static String id(int value) { return "10000000-0000-4000-8000-" + String.format(java.util.Locale.ROOT, "%012d", value); }
    private static String sha(String value) throws Exception { return WireValues.sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static Path root() { return Path.of(System.getProperty("medical.projectRoot")); }
}
