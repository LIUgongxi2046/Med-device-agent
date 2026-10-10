package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

public final class CommonProtocol {
    private CommonProtocol() { }
    public enum Access { PASS, FAIL }
    public enum Support { SUPPORTED, CONTRADICTED, INSUFFICIENT, NOT_APPLICABLE }
    public enum ScopeMode { PROJECT_APPROVED, COLLECTION_VERSIONS, SOURCE_VERSIONS, COMBINED }

    public record Coverage(String selectionSha256, String deliveredSha256, boolean complete, String nextCursor) {
        public Coverage {
            if (complete ? nextCursor != null : nextCursor == null || nextCursor.isEmpty()) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }
    public record ArtifactRef(String artifactId, String sha256, String schemaRef, Coverage coverage) {
        public ArtifactRef { ids(artifactId); }
    }
    public record Selection(String documentRevision, List<SelectionSegment> segments) {
        public Selection { ids(documentRevision); segments = List.copyOf(segments); }
    }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = TextSelection.class, name = "TEXT"), @JsonSubTypes.Type(value = ImageSelection.class, name = "IMAGE"), @JsonSubTypes.Type(value = TableSelection.class, name = "TABLE")})
    public sealed interface SelectionSegment permits TextSelection, ImageSelection, TableSelection { String kind(); }
    public record TextSelection(String kind, String nodeId, String nodeSha256, long startCp, long endCp, String quoteSha256) implements SelectionSegment {
        public TextSelection { constant(kind, "TEXT"); ids(nodeId); }
    }
    public record ImageSelection(String kind, String nodeId, String imageArtifactId, String imageSha256) implements SelectionSegment {
        public ImageSelection { constant(kind, "IMAGE"); ids(nodeId, imageArtifactId); }
    }
    public record TableSelection(String kind, String nodeId, List<String> rowIds, List<String> columnIds, List<String> cellIds, String tableSha256) implements SelectionSegment {
        public TableSelection { constant(kind, "TABLE"); ids(nodeId); rowIds = copyIds(rowIds); columnIds = copyIds(columnIds); cellIds = copyIds(cellIds); }
    }
    public record SourceScope(ScopeMode mode, List<String> collectionVersions, List<String> sourceVersions,
                              boolean networkEnabled, String networkPolicyRevision, String approvedQueryRevision) {
        public SourceScope {
            collectionVersions = copyIds(collectionVersions); sourceVersions = copyIds(sourceVersions);
            if (mode == null) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
            if ((mode == ScopeMode.PROJECT_APPROVED && (!collectionVersions.isEmpty() || !sourceVersions.isEmpty()))
                || (mode == ScopeMode.COLLECTION_VERSIONS && !sourceVersions.isEmpty())
                || (mode == ScopeMode.SOURCE_VERSIONS && !collectionVersions.isEmpty())) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
            if (networkEnabled) ids(networkPolicyRevision, approvedQueryRevision);
            else if (networkPolicyRevision != null || approvedQueryRevision != null) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED");
        }
    }
    public record EvidenceDecision(String evidenceLinkId, Selection selection, Access access, Access location,
                                   Support support, String reason, String ruleRevision) {
        public EvidenceDecision { ids(evidenceLinkId); nullableIds(ruleRevision); }
    }

    static void ids(String... values) { for (String value : values) WireValues.uuid(value); }
    static void nullableIds(String... values) { for (String value : values) if (value != null) WireValues.uuid(value); }
    static void revisions(String... values) { for (String value : values) WireValues.revision(value); }
    static void times(String... values) { for (String value : values) WireValues.utcTime(value); }
    static List<String> copyIds(List<String> values) { var saved = List.copyOf(values); for (String value : saved) WireValues.uuid(value); return saved; }
    static void constant(String actual, String expected) { if (!expected.equals(actual)) throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED"); }
}
