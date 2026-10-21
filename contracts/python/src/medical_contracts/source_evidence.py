"""Opt-in evidence v2; the v1 dataset remains immutable and strict."""
from typing import Annotated, Literal
from pydantic import Field, model_validator
from .domain import DomainModel, Hash, NonnegativeInt, PositiveInt
from .dataset import EvidenceRef, Locator, DatasetBindings
from .typed import TypedSchemaBindings

class ImageLocator(DomainModel):
    schema_version: Literal["1.0.0"]
    kind: Literal["IMAGE"]
    frame_index: Annotated[int, Field(ge=0, le=999)]
    original_sha256: Hash
    width: Annotated[int, Field(ge=1, le=100000)]
    height: Annotated[int, Field(ge=1, le=100000)]
    x0: Annotated[float, Field(ge=0, le=1)]
    y0: Annotated[float, Field(ge=0, le=1)]
    x1: Annotated[float, Field(ge=0, le=1)]
    y1: Annotated[float, Field(ge=0, le=1)]
    coordinate_system: Literal["ORIGINAL_TOP_LEFT_NORMALIZED"]
    @model_validator(mode="after")
    def ordered(self):
        if self.x0 >= self.x1 or self.y0 >= self.y1:
            raise ValueError("IMAGE_LOCATION_INVALID")
        return self

class HtmlAddress(DomainModel):
    kind: Literal["HTML_ELEMENT"]
    element_path: Annotated[str, Field(pattern=r"^(/[a-z][a-z0-9:-]*\[[1-9][0-9]*\])+$", max_length=8192)]
    text_start: NonnegativeInt
    text_end: PositiveInt
    @model_validator(mode="after")
    def ordered(self):
        if self.text_start >= self.text_end:
            raise ValueError("HTML_LOCATION_INVALID")
        return self

class HtmlLocator(DomainModel):
    schema_version: Literal["1.0.0"]
    kind: Literal["HTML"]
    original_sha256: Hash
    address: HtmlAddress

LocatorV2 = Locator | ImageLocator | HtmlLocator
class EvidenceRefV2(EvidenceRef):
    schema_version: Literal["2.0.0"]
    locator_kind: Literal["PDF", "DOCX", "XLSX", "CSV", "TXT", "MARKDOWN", "PPTX", "IMAGE", "HTML"]
    locator: LocatorV2
    @model_validator(mode="after")
    def matching_kind(self):
        if self.locator_kind != self.locator.kind:
            raise ValueError("LOCATOR_KIND_MISMATCH")
        return self

class SourceEvidenceBindings(TypedSchemaBindings):
    def __init__(self, catalogue):
        super().__init__(catalogue, "source-evidence-v2", {
            "EvidenceRef": EvidenceRefV2, "locator": LocatorV2,
            "ImageLocator": ImageLocator, "HtmlLocator": HtmlLocator,
        }, EvidenceRefV2)

def bind_evidence(catalogue, value):
    if value.get("schema_version") == "1.0.0":
        return DatasetBindings(catalogue).bind(value, "EvidenceRef")
    if value.get("schema_version") == "2.0.0":
        return SourceEvidenceBindings(catalogue).bind(value, "EvidenceRef")
    raise ValueError("EVIDENCE_CONTRACT_UNSUPPORTED")
