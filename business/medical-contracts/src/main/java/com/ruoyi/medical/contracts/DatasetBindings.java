package com.ruoyi.medical.contracts;

import java.util.List;
import static com.ruoyi.medical.contracts.TypedSchemaBindings.definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.Definition;

public final class DatasetBindings {
    private DatasetBindings() { }
    public static final Definition<DatasetProtocol.SourceDataset> DATASET = definition("dataset", null, DatasetProtocol.SourceDataset.class);
    public static final Definition<String> ID = definition("dataset", "id", String.class);
    public static final Definition<String> HASH = definition("dataset", "hash", String.class);
    public static final Definition<DatasetProtocol.RegisteredSource> SOURCE = definition("dataset", "source", DatasetProtocol.RegisteredSource.class);
    public static final Definition<DatasetProtocol.RegisteredRecord> RECORD = definition("dataset", "record", DatasetProtocol.RegisteredRecord.class);
    public static final Definition<DatasetProtocol.Locator> LOCATOR = definition("dataset", "locator", DatasetProtocol.Locator.class);
    public static final Definition<DatasetProtocol.PdfLocator> PDF = definition("dataset", "pdf", DatasetProtocol.PdfLocator.class);
    public static final Definition<DatasetProtocol.DocxLocator> DOCX = definition("dataset", "docx", DatasetProtocol.DocxLocator.class);
    public static final Definition<DatasetProtocol.XlsxLocator> XLSX = definition("dataset", "xlsx", DatasetProtocol.XlsxLocator.class);
    public static final Definition<DatasetProtocol.CsvLocator> CSV = definition("dataset", "csv", DatasetProtocol.CsvLocator.class);
    public static final Definition<DatasetProtocol.TextLocator> TEXT = definition("dataset", "text", DatasetProtocol.TextLocator.class);
    public static final Definition<DatasetProtocol.MarkdownLocator> MARKDOWN = definition("dataset", "markdown", DatasetProtocol.MarkdownLocator.class);
    public static final Definition<DatasetProtocol.PptxLocator> PPTX = definition("dataset", "pptx", DatasetProtocol.PptxLocator.class);
    public static final Definition<DatasetProtocol.EvidenceRef> EVIDENCE_REF = definition("dataset", "EvidenceRef", DatasetProtocol.EvidenceRef.class);
    public static List<Definition<?>> definitions() { return List.of(ID, HASH, SOURCE, RECORD, LOCATOR, PDF, DOCX, XLSX, CSV, TEXT, MARKDOWN, PPTX, EVIDENCE_REF); }
}
