package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import static com.ruoyi.medical.contracts.CommonProtocol.constant;

public final class DatasetProtocol {
    private DatasetProtocol() { }
    public enum Format { PDF, DOCX, XLSX, CSV, TXT, MARKDOWN, PPTX, DOC_REFERENCE }
    public enum LocatorKind { PDF, DOCX, XLSX, CSV, TXT, MARKDOWN, PPTX }
    public enum Nature { OFFICIAL_ORIGINAL, DERIVED_PREVIEW, DERIVED_TEXT, PROJECT_SPECIFICATION }
    public enum AllowedUse { LOCAL_DESIGN_REVIEW, LOCAL_PARSER_CHECK, APPROVED_PRODUCTION, APPROVED_EXTERNAL_PROCESSING }
    public enum Sensitivity { PUBLIC, INTERNAL, RESTRICTED, PERSONAL_HEALTH }
    public enum ProductionStatus { PENDING_APPROVAL, APPROVED, DISABLED }
    public enum PageBasis { ORIGINAL, CONVERTED_PREVIEW }
    public enum ValueBasis { LITERAL, FORMULA, CACHED_RESULT, VERIFIED_CALCULATION }

    public record SourceDataset(String schemaVersion, String datasetVersion, String createdAt,
                                List<RegisteredSource> sources, List<RegisteredRecord> records) {
        public SourceDataset { constant(schemaVersion, "1.0.0"); sources = List.copyOf(sources); records = List.copyOf(records); }
    }
    public record RegisteredSource(String sourceId, String sourceRef, String owner, String officialUrl,
                                   String publisherVersion, String sha256, long sizeBytes, Format format,
                                   Nature nature, String parentSourceId, String license, List<AllowedUse> allowedUse,
                                   Sensitivity sensitivity, String retention, ProductionStatus productionStatus) {
        public RegisteredSource { allowedUse = List.copyOf(allowedUse); }
    }
    public record RegisteredRecord(String recordId, String sourceId, String sourceVersion, String parseVersion,
                                   String chunkId, String sectionTitle, String text, String quoteHash, Locator locator,
                                   List<String> checks, boolean formalEvidenceEligible) {
        public RegisteredRecord { checks = List.copyOf(checks); }
    }
    // 登记身份沿用 dataset 的文本定义，业务 UUID 由接口对应字段独立校验。
    public record EvidenceRef(String schemaVersion, String tenantId, String projectId, String sourceId,
                              String sourceVersion, String parseVersion, String chunkId, LocatorKind locatorKind,
                              Locator locator, String quoteHash, String approvalRevision) {
        public EvidenceRef { constant(schemaVersion, "1.0.0"); }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = PdfLocator.class, name = "PDF"), @JsonSubTypes.Type(value = DocxLocator.class, name = "DOCX"),
        @JsonSubTypes.Type(value = XlsxLocator.class, name = "XLSX"), @JsonSubTypes.Type(value = CsvLocator.class, name = "CSV"),
        @JsonSubTypes.Type(value = TextLocator.class, name = "TXT"), @JsonSubTypes.Type(value = MarkdownLocator.class, name = "MARKDOWN"),
        @JsonSubTypes.Type(value = PptxLocator.class, name = "PPTX")})
    public sealed interface Locator permits PdfLocator, DocxLocator, XlsxLocator, CsvLocator, TextLocator, MarkdownLocator, PptxLocator { String kind(); }

    public record PdfLocator(String kind, long pageIndex, PageBasis pageBasis, String printedLabel, PdfRegion region) implements Locator {
        public PdfLocator { constant(kind, "PDF"); }
    }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = WholePage.class, name = "WHOLE_PAGE"), @JsonSubTypes.Type(value = Box.class, name = "BOX")})
    public sealed interface PdfRegion permits WholePage, Box { String kind(); }
    public record WholePage(String kind) implements PdfRegion { public WholePage { constant(kind, "WHOLE_PAGE"); } }
    public record Box(String kind, double x0, double y0, double x1, double y1, String coordinateSystem, int rotation) implements PdfRegion {
        public Box { constant(kind, "BOX"); constant(coordinateSystem, "CROPBOX_TOP_LEFT_NORMALIZED"); }
    }
    public record DocxLocator(String kind, String part, DocxAddress address) implements Locator { public DocxLocator { constant(kind, "DOCX"); } }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = ParagraphAddress.class, name = "PARAGRAPH"), @JsonSubTypes.Type(value = CellAddress.class, name = "CELL"), @JsonSubTypes.Type(value = NodeAddress.class, name = "NODE")})
    public sealed interface DocxAddress permits ParagraphAddress, CellAddress, NodeAddress { String kind(); }
    public record ParagraphAddress(String kind, long paragraphIndex) implements DocxAddress { public ParagraphAddress { constant(kind, "PARAGRAPH"); } }
    public record CellAddress(String kind, long tableIndex, long rowIndex, long columnIndex) implements DocxAddress { public CellAddress { constant(kind, "CELL"); } }
    public record NodeAddress(String kind, String nodeId, String nodeHash, List<Long> structuralPath) implements DocxAddress {
        public NodeAddress { constant(kind, "NODE"); structuralPath = List.copyOf(structuralPath); }
    }
    public record XlsxLocator(String kind, String sheetId, String sheetName, String cellRange, ValueBasis valueBasis, String calculationArtifactId) implements Locator {
        public XlsxLocator { constant(kind, "XLSX"); }
    }
    public record CsvLocator(String kind, String tableId, String dialectVersion, long recordIndex,
                             List<Long> columnIndices, long headerRecordCount) implements Locator {
        public CsvLocator { constant(kind, "CSV"); columnIndices = List.copyOf(columnIndices); }
    }
    public record TextLocator(String kind, String encoding, String newlinePolicy, long charStart, long charEnd, long lineStart, long lineEnd) implements Locator {
        public TextLocator { constant(kind, "TXT"); constant(encoding, "UTF-8"); constant(newlinePolicy, "PRESERVE"); }
    }
    public record MarkdownLocator(String kind, long nodeIndex, long lineStart, long lineEnd, List<String> headingPath) implements Locator {
        public MarkdownLocator { constant(kind, "MARKDOWN"); headingPath = List.copyOf(headingPath); }
    }
    public record PptxLocator(String kind, String slideId, long slideIndex, String shapeId, Long paragraphIndex, TableCell tableCell) implements Locator {
        public PptxLocator { constant(kind, "PPTX"); }
    }
    public record TableCell(long rowIndex, long columnIndex) { }
}
