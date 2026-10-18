package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fixed-version location and digest checks only; this does not grant document access.
 * The caller supplies the complete fixed body after its ordinary domain/read proof.
 */
public final class DocumentSelectionSemantics {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private DocumentSelectionSemantics() { }

    public record Verified(CommonProtocol.Selection selection, String canonicalJson, String sha256, String anchorNodeId) { }

    public static Verified verify(SchemaCatalogue schemas, String documentRevision,
            InterfaceProtocol.DocumentBody body, CommonProtocol.Selection selection) throws IOException {
        Objects.requireNonNull(schemas); Objects.requireNonNull(body); Objects.requireNonNull(documentRevision);
        if (selection == null || selection.segments().isEmpty()) invalid();
        SchemaCatalogue.ValidatedPayload payload;
        try {
            payload = schemas.bind("interfaces", JSON.valueToTree(selection), "Selection");
        } catch (SchemaRejection rejected) {
            if (rejected.code() == ErrorCode.SCHEMA_INVALID) invalid();
            throw rejected;
        }
        if (!documentRevision.equals(selection.documentRevision())) stale();
        // The published binding checks every value, including unpaired Unicode surrogates
        // and numbers that a browser could not represent exactly. Never normalize text.
        schemas.bind("interfaces", JSON.valueToTree(body), "DocumentBody");
        var ordered = orderedNodes(body);
        var indices = new HashMap<String,Integer>();
        var children = new HashMap<String,List<InterfaceProtocol.Node>>();
        for (int index = 0; index < ordered.size(); index++) {
            var node = ordered.get(index); indices.put(node.nodeId(), index);
            children.computeIfAbsent(node.parentId(), unused -> new ArrayList<>()).add(node);
        }
        var covered = new HashSet<String>();
        int previous = -1;
        String anchor = null;
        for (var segment : selection.segments()) {
            String id = nodeId(segment);
            Integer index = indices.get(id);
            // One segment per node also rejects non-overlapping duplicate text ranges.
            if (index == null || index <= previous || covered.contains(id)) invalid();
            previous = index;
            if (anchor == null) anchor = id;
            var node = ordered.get(index);
            if (segment instanceof CommonProtocol.TextSelection text) text(schemas, node, text);
            else if (segment instanceof CommonProtocol.ImageSelection image) image(node, image);
            else if (segment instanceof CommonProtocol.TableSelection table) {
                var roots = table(schemas, node, table);
                var pending = new ArrayDeque<InterfaceProtocol.Node>();
                for (String root : roots) {
                    Integer childIndex = indices.get(root);
                    if (childIndex == null || !node.nodeId().equals(ordered.get(childIndex).parentId())) invalid();
                    pending.push(ordered.get(childIndex));
                }
                // Cell.node_ids contains direct roots; every descendant belongs to the
                // selected cell, including nested tables and their text/image children.
                while (!pending.isEmpty()) {
                    var child = pending.pop();
                    if (!covered.add(child.nodeId())) invalid();
                    children.getOrDefault(child.nodeId(), List.of()).forEach(pending::push);
                }
            }
            else invalid();
        }
        return new Verified(selection, payload.canonicalJson(), digest(payload.canonicalBytes()), anchor);
    }

    private static List<InterfaceProtocol.Node> orderedNodes(InterfaceProtocol.DocumentBody body) {
        var nodes = new HashMap<String,InterfaceProtocol.Node>();
        var children = new HashMap<String,List<InterfaceProtocol.Node>>();
        var positions = new HashSet<SiblingPosition>();
        for (var node : body.nodes()) {
            if (nodes.putIfAbsent(node.nodeId(), node) != null
                    || !positions.add(new SiblingPosition(node.parentId(), node.ordinal()))) invalid();
            children.computeIfAbsent(node.parentId(), unused -> new ArrayList<>()).add(node);
        }
        for (var node : body.nodes()) if (node.parentId() != null && !nodes.containsKey(node.parentId())) invalid();
        children.values().forEach(group -> group.sort(Comparator.comparingLong(InterfaceProtocol.Node::ordinal)));
        var pending = new ArrayDeque<InterfaceProtocol.Node>();
        pushReverse(pending, children.getOrDefault(null, List.of()));
        var result = new ArrayList<InterfaceProtocol.Node>();
        var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (!visited.add(node.nodeId())) invalid();
            result.add(node);
            pushReverse(pending, children.getOrDefault(node.nodeId(), List.of()));
        }
        if (visited.size() != nodes.size()) invalid();
        return result;
    }

    private static void pushReverse(ArrayDeque<InterfaceProtocol.Node> pending, List<InterfaceProtocol.Node> group) {
        for (int index = group.size() - 1; index >= 0; index--) pending.push(group.get(index));
    }

    private static void text(SchemaCatalogue schemas, InterfaceProtocol.Node node,
            CommonProtocol.TextSelection selection) throws IOException {
        if (!(node.content() instanceof InterfaceProtocol.TextContent)) invalid();
        String original = ((InterfaceProtocol.TextContent) node.content()).text();
        int size = original.codePointCount(0, original.length());
        if (selection.startCp() < 0 || selection.endCp() <= selection.startCp() || selection.endCp() > size) invalid();
        if (!nodeDigest(schemas, node).equals(selection.nodeSha256())) stale();
        String quote = original.substring(original.offsetByCodePoints(0, (int) selection.startCp()),
                original.offsetByCodePoints(0, (int) selection.endCp()));
        if (!digest(quote.getBytes(StandardCharsets.UTF_8)).equals(selection.quoteSha256())) stale();
    }

    private static void image(InterfaceProtocol.Node node, CommonProtocol.ImageSelection selection) {
        if (!(node.content() instanceof InterfaceProtocol.ImageContent)) invalid();
        var content = (InterfaceProtocol.ImageContent) node.content();
        if (!content.artifactId().equals(selection.imageArtifactId()) || !content.sha256().equals(selection.imageSha256())) stale();
    }

    private static List<String> table(SchemaCatalogue schemas, InterfaceProtocol.Node node,
            CommonProtocol.TableSelection selection) throws IOException {
        if (!(node.content() instanceof InterfaceProtocol.TableContent)) invalid();
        var content = (InterfaceProtocol.TableContent) node.content();
        var rows = positions(content.rowIds()); var columns = positions(content.columnIds());
        var cells = new ArrayList<CellPosition>(); var identities = new HashSet<String>();
        var area = BigInteger.ZERO;
        for (var cell : content.cells()) {
            Integer row = rows.get(cell.rowId()), column = columns.get(cell.columnId());
            if (!identities.add(cell.cellId()) || row == null || column == null
                    || cell.rowSpan() < 1 || cell.columnSpan() < 1
                    || cell.rowSpan() > rows.size() - row || cell.columnSpan() > columns.size() - column) invalid();
            var position = new CellPosition(cell, row, row + (int) cell.rowSpan(), column, column + (int) cell.columnSpan());
            for (var existing : cells) if (position.overlaps(existing)) invalid();
            cells.add(position);
            area = area.add(BigInteger.valueOf(cell.rowSpan()).multiply(BigInteger.valueOf(cell.columnSpan())));
        }
        if (!area.equals(BigInteger.valueOf(rows.size()).multiply(BigInteger.valueOf(columns.size())))) invalid();
        cells.sort(Comparator.comparingInt(CellPosition::row).thenComparingInt(CellPosition::column));
        var requested = new HashSet<>(selection.cellIds());
        if (requested.isEmpty() || requested.size() != selection.cellIds().size()) invalid();
        var selected = cells.stream().filter(position -> requested.contains(position.cell().cellId())).toList();
        if (!selected.stream().map(position -> position.cell().cellId()).toList().equals(selection.cellIds())) invalid();
        var selectedRows = new HashSet<String>(); var selectedColumns = new HashSet<String>();
        for (var position : selected) {
            selectedRows.addAll(content.rowIds().subList(position.row(), position.endRow()));
            selectedColumns.addAll(content.columnIds().subList(position.column(), position.endColumn()));
        }
        if (!content.rowIds().stream().filter(selectedRows::contains).toList().equals(selection.rowIds())
                || !content.columnIds().stream().filter(selectedColumns::contains).toList().equals(selection.columnIds())) invalid();
        if (!nodeDigest(schemas, node).equals(selection.tableSha256())) stale();
        return selected.stream().flatMap(position -> position.cell().nodeIds().stream()).toList();
    }

    private static Map<String,Integer> positions(List<String> ids) {
        var result = new HashMap<String,Integer>();
        for (int index = 0; index < ids.size(); index++) if (result.putIfAbsent(ids.get(index), index) != null) invalid();
        return result;
    }
    private static String nodeId(CommonProtocol.SelectionSegment segment) {
        if (segment instanceof CommonProtocol.TextSelection text) return text.nodeId();
        if (segment instanceof CommonProtocol.ImageSelection image) return image.nodeId();
        if (segment instanceof CommonProtocol.TableSelection table) return table.nodeId();
        throw new TenantAccessException(ErrorCode.SELECTION_INVALID);
    }
    private static String nodeDigest(SchemaCatalogue schemas, InterfaceProtocol.Node node) throws IOException {
        // Frontend document-selection.ts hashes the FULL Node, not just its content.
        return digest(schemas.bind("interfaces", JSON.valueToTree(node), "Node").canonicalBytes());
    }
    private static String digest(byte[] bytes) {
        try { return WireValues.sha256(bytes); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA256_UNAVAILABLE", unavailable); }
    }
    private record SiblingPosition(String parentId, long ordinal) { }
    private record CellPosition(InterfaceProtocol.Cell cell, int row, int endRow, int column, int endColumn) {
        boolean overlaps(CellPosition other) {
            return row < other.endRow && other.row < endRow && column < other.endColumn && other.column < endColumn;
        }
    }
    private static void invalid() { throw new TenantAccessException(ErrorCode.SELECTION_INVALID); }
    private static void stale() { throw new TenantAccessException(ErrorCode.SELECTION_STALE); }
}
