package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.DocumentEvidenceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public final class DocumentBodySemantics {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private DocumentBodySemantics() { }
    public record Limits(int maximumNodes, int maximumTableCells, int maximumRelationships) {
        public Limits {
            if (maximumNodes < 1 || maximumTableCells < 1 || maximumRelationships < 1)
                throw new IllegalArgumentException("DOCUMENT_CAPACITY_REQUIRED");
        }
    }

    static SchemaCatalogue.ValidatedPayload published(SchemaCatalogue schemas, SchemaCatalogue.PublishedSchema required,
            byte[] bytes, Limits limits) throws IOException {
        Objects.requireNonNull(required); Objects.requireNonNull(bytes); Objects.requireNonNull(limits);
        var declared = schemas.requirePublished(required.schemaRef(), required.schemaSha256());
        if (!java.util.Set.of("interfaces", "document-body-v2").contains(declared.name()) || !"DocumentBody".equals(declared.definition())) invalid();
        var payload = schemas.bindPublished(required.schemaRef(), required.schemaSha256(), bytes);
        verify(schemas, JSON.readTree(payload.canonicalJson()), limits, required.schemaRef(), required.schemaSha256());
        // 正文制品保存规范字节，版本摘要与完整对象摘要保持相同。
        if (!Arrays.equals(bytes, payload.canonicalBytes())) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        return payload;
    }
    public static JsonNode verify(SchemaCatalogue schemas, JsonNode value, Limits limits) throws IOException {
        return verify(schemas, value, limits, schemas.schemaRef("interfaces", "DocumentBody"), schemas.schemaDigest("interfaces"));
    }
    /** The declared publication must come from the actual approved role or registered body schema,
     * never from an untrusted payload version. The legacy overload remains strictly v1. */
    public static JsonNode verify(SchemaCatalogue schemas, JsonNode value, Limits limits,
            String declaredRef, String declaredSha) throws IOException {
        Objects.requireNonNull(schemas); Objects.requireNonNull(limits);
        var original = Objects.requireNonNull(value).deepCopy();
        unicode(original);
        var body = DocumentEvidenceBindings.bind(schemas, InterfaceBindings.DOCUMENT_BODY, original, declaredRef, declaredSha).read();
        if (original.required("nodes").size() > limits.maximumNodes()) capacity();
        var relationships = new Relationships(limits.maximumRelationships());
        var nodes = new LinkedHashMap<String,InterfaceProtocol.Node>();
        var graph = new DefaultDirectedGraph<String,DefaultEdge>(DefaultEdge.class);
        var positions = new HashSet<Position>(); String root = null;
        for (var node : body.nodes()) {
            if (nodes.putIfAbsent(node.nodeId(), node) != null || !positions.add(new Position(node.parentId(), node.ordinal()))) invalid();
            graph.addVertex(node.nodeId());
            if (node.content() instanceof InterfaceProtocol.ContainerContent container
                    && container.kind() == InterfaceProtocol.ContainerKind.DOCUMENT) {
                if (root != null || node.parentId() != null) invalid(); root = node.nodeId();
            }
            relationships.take(node.fieldIds().size()); relationships.take(node.evidenceLinkIds().size());
        }
        if (root == null) invalid();
        for (var node : body.nodes()) {
            if (node.nodeId().equals(root)) continue;
            var parent = nodes.get(node.parentId());
            if (parent == null || !(parent.content() instanceof InterfaceProtocol.ContainerContent
                    || parent.content() instanceof InterfaceProtocol.TableContent)) invalid();
            if (graph.addEdge(parent.nodeId(), node.nodeId()) == null) invalid(); relationships.take(1);
        }
        if (new CycleDetector<>(graph).detectCycles()) invalid();
        var visited = new HashSet<String>(); var pending = new ArrayDeque<String>(); pending.push(root);
        while (!pending.isEmpty()) {
            String id = pending.pop(); if (!visited.add(id)) invalid();
            for (var edge : graph.outgoingEdgesOf(id)) pending.push(graph.getEdgeTarget(edge));
        }
        if (visited.size() != nodes.size()) invalid();
        var rowIds = new HashSet<String>(); var columnIds = new HashSet<String>(); var cellIds = new HashSet<String>();
        long cellCount = 0;
        for (var node : body.nodes()) if (node.content() instanceof InterfaceProtocol.TableContent table) {
            cellCount += table.cells().size(); if (cellCount > limits.maximumTableCells()) capacity();
            table(node, table, nodes, rowIds, columnIds, cellIds, relationships);
        }
        references(body, nodes, relationships);
        // 返回独立完整对象；此结果只证明结构，不授予模板、来源或正文采用许可。
        return original.deepCopy();
    }

    private static void table(InterfaceProtocol.Node node, InterfaceProtocol.TableContent table,
            Map<String,InterfaceProtocol.Node> nodes, Set<String> allRows, Set<String> allColumns, Set<String> allCells,
            Relationships relationships) {
        var rows = positions(table.rowIds(), allRows); var columns = positions(table.columnIds(), allColumns);
        relationships.take(table.rowIds().size()); relationships.take(table.columnIds().size());
        var rectangles = new ArrayList<Rectangle>(); var owned = new HashSet<String>();
        var ordered = new ArrayList<InterfaceProtocol.Cell>(); var area = BigInteger.ZERO;
        for (var cell : table.cells()) {
            if (!allCells.add(cell.cellId())) invalid();
            Integer row = rows.get(cell.rowId()), column = columns.get(cell.columnId());
            if (row == null || column == null || cell.rowSpan() > rows.size() - row || cell.columnSpan() > columns.size() - column) invalid();
            var rectangle = new Rectangle(row, row + (int) cell.rowSpan(), column, column + (int) cell.columnSpan());
            for (var previous : rectangles) if (rectangle.overlaps(previous)) invalid();
            rectangles.add(rectangle); ordered.add(cell);
            area = area.add(BigInteger.valueOf(cell.rowSpan()).multiply(BigInteger.valueOf(cell.columnSpan())));
            relationships.take(cell.nodeIds().size()); relationships.take(1);
            for (String id : cell.nodeIds()) {
                var target = nodes.get(id);
                if (target == null || !node.nodeId().equals(target.parentId()) || !owned.add(id)) invalid();
            }
        }
        // 面积和不重叠共同证明完整覆盖，不为合并单元格创建庞大的二维数组。
        if (!area.equals(BigInteger.valueOf(rows.size()).multiply(BigInteger.valueOf(columns.size())))) invalid();
        ordered.sort(Comparator.comparingInt((InterfaceProtocol.Cell cell) -> rows.get(cell.rowId()))
                .thenComparingInt(cell -> columns.get(cell.columnId())));
        var declared = ordered.stream().flatMap(cell -> cell.nodeIds().stream()).toList();
        var actual = nodes.values().stream().filter(child -> node.nodeId().equals(child.parentId()))
                .sorted(Comparator.comparingLong(InterfaceProtocol.Node::ordinal)).map(InterfaceProtocol.Node::nodeId).toList();
        if (!declared.equals(actual)) invalid();
    }

    private static Map<String,Integer> positions(List<String> values, Set<String> all) {
        var result = new HashMap<String,Integer>();
        for (int index = 0; index < values.size(); index++) {
            String id = values.get(index);
            if (!all.add(id) || result.putIfAbsent(id, index) != null) invalid();
        }
        return result;
    }

    private static void references(InterfaceProtocol.DocumentBody body, Map<String,InterfaceProtocol.Node> nodes, Relationships relationships) {
        var fields = new HashMap<String,InterfaceProtocol.FieldValue>();
        var evidence = new HashMap<String,InterfaceProtocol.EvidenceLink>();
        relationships.take(body.fields().size()); relationships.take(body.evidenceLinks().size()); relationships.take(body.factUsages().size());
        for (var field : body.fields()) {
            var node = nodes.get(field.nodeId());
            if (fields.putIfAbsent(field.fieldId(), field) != null || node == null || !node.fieldIds().contains(field.fieldId())) invalid();
            relationships.take(field.evidenceLinkIds().size());
        }
        for (var link : body.evidenceLinks()) {
            if (evidence.putIfAbsent(link.evidenceLinkId(), link) != null) invalid();
            relationships.take(link.nodeIds().size());
            for (String id : link.nodeIds()) {
                var node = nodes.get(id);
                if (node == null || !node.evidenceLinkIds().contains(link.evidenceLinkId())) invalid();
            }
        }
        for (var node : nodes.values()) {
            for (String id : node.fieldIds()) {
                var field = fields.get(id); if (field == null || !node.nodeId().equals(field.nodeId())) invalid();
            }
            for (String id : node.evidenceLinkIds()) {
                var link = evidence.get(id); if (link == null || !link.nodeIds().contains(node.nodeId())) invalid();
            }
        }
        for (var field : fields.values()) for (String id : field.evidenceLinkIds()) {
            var link = evidence.get(id); if (link == null || !link.nodeIds().contains(field.nodeId())) invalid();
        }
        var factTargets = new HashSet<FactTarget>();
        for (var usage : body.factUsages()) {
            relationships.take(usage.nodeIds().size()); relationships.take(usage.fieldIds().size());
            if (usage.nodeIds().isEmpty() && usage.fieldIds().isEmpty()) invalid();
            for (String id : usage.nodeIds()) {
                if (!nodes.containsKey(id) || !factTargets.add(new FactTarget(usage.factVersionId(), id, null))) invalid();
            }
            for (String id : usage.fieldIds()) {
                var field = fields.get(id);
                if (field == null || !factTargets.add(new FactTarget(usage.factVersionId(), field.nodeId(), id))) invalid();
            }
        }
    }

    private static void unicode(JsonNode value) {
        var pending = new ArrayDeque<JsonNode>(); pending.push(value);
        var encoder = StandardCharsets.UTF_8.newEncoder();
        while (!pending.isEmpty()) {
            var item = pending.pop();
            if (item.isTextual() && !encoder.canEncode(item.textValue())) invalid();
            if (item.isContainerNode()) item.elements().forEachRemaining(pending::push);
        }
    }
    private record Position(String parent, long ordinal) { }
    private record FactTarget(String fact, String node, String field) { }
    private record Rectangle(int firstRow, int endRow, int firstColumn, int endColumn) {
        boolean overlaps(Rectangle other) {
            return firstRow < other.endRow && other.firstRow < endRow && firstColumn < other.endColumn && other.firstColumn < endColumn;
        }
    }
    private static final class Relationships {
        private final int maximum;
        private long count;
        Relationships(int maximum) { this.maximum = maximum; }
        void take(int size) { count += size; if (count > maximum) capacity(); }
    }
    private static void capacity() { throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); }
    private static void invalid() { throw new TenantAccessException(ErrorCode.SCHEMA_INVALID); }
}
