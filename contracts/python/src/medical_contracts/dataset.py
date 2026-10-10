from typing import Annotated, Literal

from pydantic import AfterValidator, BeforeValidator, Field

from .catalogue import SchemaCatalogue
from .domain import DomainModel, Hash, NonnegativeInt, PositiveInt, Text, Timestamp, unique_items
from .typed import TypedSchemaBindings


Id = Annotated[str, Field(min_length=1, max_length=128)]
LocatorKind = Literal["PDF", "DOCX", "XLSX", "CSV", "TXT", "MARKDOWN", "PPTX"]
AllowedUse = Literal["LOCAL_DESIGN_REVIEW", "LOCAL_PARSER_CHECK", "APPROVED_PRODUCTION", "APPROVED_EXTERNAL_PROCESSING"]


def integer_enum(value: int) -> int:
    if type(value) is not int:
        raise ValueError("枚举必须使用整数")
    return value


class WholePage(DomainModel):
    kind: Literal["WHOLE_PAGE"]


class Box(DomainModel):
    kind: Literal["BOX"]
    x0: Annotated[float, Field(ge=0, le=1)]
    y0: Annotated[float, Field(ge=0, le=1)]
    x1: Annotated[float, Field(ge=0, le=1)]
    y1: Annotated[float, Field(ge=0, le=1)]
    coordinate_system: Literal["CROPBOX_TOP_LEFT_NORMALIZED"]
    rotation: Annotated[Literal[0, 90, 180, 270], BeforeValidator(integer_enum)]


PdfRegion = Annotated[WholePage | Box, Field(discriminator="kind")]


class PdfLocator(DomainModel):
    kind: Literal["PDF"]
    page_index: NonnegativeInt
    page_basis: Literal["ORIGINAL", "CONVERTED_PREVIEW"]
    printed_label: str | None
    region: PdfRegion


class ParagraphAddress(DomainModel):
    kind: Literal["PARAGRAPH"]
    paragraph_index: NonnegativeInt


class CellAddress(DomainModel):
    kind: Literal["CELL"]
    table_index: NonnegativeInt
    row_index: NonnegativeInt
    column_index: NonnegativeInt


class NodeAddress(DomainModel):
    kind: Literal["NODE"]
    node_id: Id
    node_hash: Hash
    structural_path: Annotated[list[NonnegativeInt], Field(min_length=1)]


DocxAddress = Annotated[ParagraphAddress | CellAddress | NodeAddress, Field(discriminator="kind")]


class DocxLocator(DomainModel):
    kind: Literal["DOCX"]
    part: Text
    address: DocxAddress


class XlsxLocator(DomainModel):
    kind: Literal["XLSX"]
    sheet_id: Id
    sheet_name: Text
    cell_range: Annotated[str, Field(pattern=r"^[A-Z]+[1-9][0-9]*(:[A-Z]+[1-9][0-9]*)?$")]
    value_basis: Literal["LITERAL", "FORMULA", "CACHED_RESULT", "VERIFIED_CALCULATION"]
    calculation_artifact_id: str | None


class CsvLocator(DomainModel):
    kind: Literal["CSV"]
    table_id: Id
    dialect_version: Id
    record_index: NonnegativeInt
    column_indices: Annotated[list[NonnegativeInt], Field(min_length=1), AfterValidator(unique_items)]
    header_record_count: NonnegativeInt


class TextLocator(DomainModel):
    kind: Literal["TXT"]
    encoding: Literal["UTF-8"]
    newline_policy: Literal["PRESERVE"]
    char_start: NonnegativeInt
    char_end: PositiveInt
    line_start: PositiveInt
    line_end: PositiveInt


class MarkdownLocator(DomainModel):
    kind: Literal["MARKDOWN"]
    node_index: NonnegativeInt
    line_start: PositiveInt
    line_end: PositiveInt
    heading_path: list[str]


class TableCell(DomainModel):
    row_index: NonnegativeInt
    column_index: NonnegativeInt


class PptxLocator(DomainModel):
    kind: Literal["PPTX"]
    slide_id: Id
    slide_index: NonnegativeInt
    shape_id: Id
    paragraph_index: NonnegativeInt | None
    table_cell: TableCell | None


Locator = Annotated[
    PdfLocator | DocxLocator | XlsxLocator | CsvLocator | TextLocator | MarkdownLocator | PptxLocator,
    Field(discriminator="kind"),
]


class EvidenceRef(DomainModel):
    schema_version: Literal["1.0.0"]
    tenant_id: Id
    project_id: Id
    source_id: Id
    source_version: Id
    parse_version: Id
    chunk_id: Id
    locator_kind: LocatorKind
    locator: Locator
    quote_hash: Hash
    approval_revision: Id


class RegisteredSource(DomainModel):
    source_id: Id
    source_ref: Text
    owner: Text
    official_url: str | None
    publisher_version: Text
    sha256: Hash
    size_bytes: PositiveInt
    format: Literal["PDF", "DOCX", "XLSX", "CSV", "TXT", "MARKDOWN", "PPTX", "DOC_REFERENCE"]
    nature: Literal["OFFICIAL_ORIGINAL", "DERIVED_PREVIEW", "DERIVED_TEXT", "PROJECT_SPECIFICATION"]
    parent_source_id: str | None
    license: Text
    allowed_use: Annotated[list[AllowedUse], Field(min_length=1), AfterValidator(unique_items)]
    sensitivity: Literal["PUBLIC", "INTERNAL", "RESTRICTED", "PERSONAL_HEALTH"]
    retention: Text
    production_status: Literal["PENDING_APPROVAL", "APPROVED", "DISABLED"]


class RegisteredRecord(DomainModel):
    record_id: Id
    source_id: Id
    source_version: Hash
    parse_version: Text
    chunk_id: Id
    section_title: Text
    text: Text
    quote_hash: Hash
    locator: Locator
    checks: Annotated[list[Text], Field(min_length=1)]
    formal_evidence_eligible: bool


class SourceDataset(DomainModel):
    schema_version: Literal["1.0.0"]
    dataset_version: Text
    created_at: Timestamp
    sources: Annotated[list[RegisteredSource], Field(min_length=1)]
    records: Annotated[list[RegisteredRecord], Field(min_length=1)]


class DatasetBindings(TypedSchemaBindings):
    def __init__(self, catalogue: SchemaCatalogue):
        super().__init__(catalogue, "dataset", {
            "id": Id, "hash": Hash, "source": RegisteredSource, "record": RegisteredRecord,
            "locator": Locator, "pdf": PdfLocator, "docx": DocxLocator, "xlsx": XlsxLocator,
            "csv": CsvLocator, "text": TextLocator, "markdown": MarkdownLocator,
            "pptx": PptxLocator, "EvidenceRef": EvidenceRef,
        }, SourceDataset)
