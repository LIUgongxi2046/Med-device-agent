from types import MappingProxyType
from typing import Annotated, Literal, Self

from pydantic import AfterValidator, Field, model_validator

from .domain import DomainModel, Hash, NonnegativeInt, PositiveInt, Revision, Text, Timestamp, WireId, unique_items


Id = WireId
NullableId = Id | None
NullableHash = Hash | None
NullableText = str | None
Ids = Annotated[list[Id], AfterValidator(unique_items)]
Texts = Annotated[list[Text], AfterValidator(unique_items)]


class Coverage(DomainModel):
    selection_sha256: Hash
    delivered_sha256: Hash
    complete: bool
    next_cursor: Text | None

    @model_validator(mode="after")
    def cursor_matches_coverage(self) -> Self:
        if self.complete != (self.next_cursor is None):
            raise ValueError("完整覆盖状态与下一页游标不一致")
        return self


class ArtifactRef(DomainModel):
    artifact_id: Id
    sha256: Hash
    schema_ref: Text
    coverage: Coverage


class TextSelection(DomainModel):
    kind: Literal["TEXT"]
    node_id: Id
    node_sha256: Hash
    start_cp: NonnegativeInt
    end_cp: PositiveInt
    quote_sha256: Hash


class ImageSelection(DomainModel):
    kind: Literal["IMAGE"]
    node_id: Id
    image_artifact_id: Id
    image_sha256: Hash


class TableSelection(DomainModel):
    kind: Literal["TABLE"]
    node_id: Id
    row_ids: Annotated[Ids, Field(min_length=1)]
    column_ids: Annotated[Ids, Field(min_length=1)]
    cell_ids: Annotated[Ids, Field(min_length=1)]
    table_sha256: Hash


SelectionSegment = Annotated[TextSelection | ImageSelection | TableSelection, Field(discriminator="kind")]


class Selection(DomainModel):
    document_revision: Id
    segments: Annotated[list[SelectionSegment], Field(min_length=1)]


class SourceScope(DomainModel):
    mode: Literal["PROJECT_APPROVED", "COLLECTION_VERSIONS", "SOURCE_VERSIONS", "COMBINED"]
    collection_versions: Ids
    source_versions: Ids
    network_enabled: bool
    network_policy_revision: NullableId
    approved_query_revision: NullableId

    @model_validator(mode="after")
    def versions_match_mode(self) -> Self:
        if self.mode == "PROJECT_APPROVED" and (self.collection_versions or self.source_versions):
            raise ValueError("项目来源范围不能包含固定集合或来源版本")
        if self.mode == "COLLECTION_VERSIONS" and self.source_versions:
            raise ValueError("集合来源范围不能包含固定来源版本")
        if self.mode == "SOURCE_VERSIONS" and self.collection_versions:
            raise ValueError("固定来源范围不能包含集合版本")
        if self.network_enabled:
            if self.network_policy_revision is None or self.approved_query_revision is None:
                raise ValueError("联网来源范围缺少政策版本或批准查询版本")
        elif self.network_policy_revision is not None or self.approved_query_revision is not None:
            raise ValueError("关闭联网的来源范围不能包含联网版本")
        return self


class EvidenceDecision(DomainModel):
    evidence_link_id: Id
    selection: Selection
    access: Literal["PASS", "FAIL"]
    location: Literal["PASS", "FAIL"]
    support: Literal["SUPPORTED", "CONTRADICTED", "INSUFFICIENT", "NOT_APPLICABLE"]
    reason: Text
    rule_revision: NullableId


COMMON_DEFINITIONS = MappingProxyType({
    "Id": Id, "Revision": Revision, "Hash": Hash, "NullableId": NullableId,
    "NullableHash": NullableHash, "NullableText": NullableText, "Ids": Ids,
    "Texts": Texts, "Timestamp": Timestamp, "Coverage": Coverage, "ArtifactRef": ArtifactRef,
    "Selection": Selection, "TextSelection": TextSelection, "ImageSelection": ImageSelection,
    "TableSelection": TableSelection, "SourceScope": SourceScope, "EvidenceDecision": EvidenceDecision,
})
