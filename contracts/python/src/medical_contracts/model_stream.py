from typing import Annotated, Literal, Self

from pydantic import AfterValidator, Field, model_validator

from .catalogue import SchemaCatalogue
from .domain import DomainModel, Hash, NonnegativeInt, PositiveInt, Text, Timestamp, WireId, unique_items
from .typed import TypedSchemaBindings


NullableText = Text | None
TokenCount = NonnegativeInt | None
SettlementStatus = Literal["PENDING", "SETTLED", "DISPUTED"]


class RequestParameters(DomainModel):
    max_output_tokens: PositiveInt
    temperature: float | None
    reasoning_effort: NullableText
    stop: Annotated[list[Text], AfterValidator(unique_items)]
    purpose: Literal["TASK", "COMPACTION", "VERIFICATION", "EVALUATION"]


class StreamRequest(DomainModel):
    schema_version: Literal["1.0.0"]
    run_id: WireId
    step_id: WireId
    action_id: WireId
    context_manifest_id: WireId
    model_config_revision: WireId
    request_parameters: RequestParameters
    lease_generation: PositiveInt


class CancelRequest(DomainModel):
    schema_version: Literal["1.0.0"]
    run_id: WireId
    step_id: WireId
    action_id: WireId
    invocation_id: WireId
    reason_code: Literal["RUN_CANCEL_REQUESTED", "DEADLINE_EXCEEDED", "AUTH_REVOKED", "PROTOCOL_ERROR"]
    lease_generation: PositiveInt


class InvocationStatus(DomainModel):
    invocation_id: WireId
    origin_action_id: WireId
    context_manifest_id: WireId
    model_config_revision: WireId
    execution_status: Literal["RESERVED", "DISPATCHING", "IN_FLIGHT", "SUCCEEDED", "FAILED", "CANCEL_REQUESTED", "CANCELLED", "UNKNOWN"]
    settlement_status: SettlementStatus
    provider_request_id: NullableText
    result_artifact_id: WireId | None
    result_sha256: Hash | None
    failure_code: NullableText


class Failure(DomainModel):
    code: Text
    message: Text
    provider_status: Annotated[int, Field(ge=100, le=599)] | None
    provider_request_id: NullableText


class TextBlock(DomainModel):
    type: Literal["text"]
    text: str


class ToolBlock(DomainModel):
    type: Literal["tool-call"]
    id: Text
    name: Text
    arguments: Text


Block = Annotated[TextBlock | ToolBlock, Field(discriminator="type")]


class Usage(DomainModel):
    uncached_input_tokens: TokenCount
    cache_read_tokens: TokenCount
    cache_write_tokens: TokenCount
    output_tokens: TokenCount
    total_tokens: TokenCount
    reasoning_tokens: TokenCount
    source_receipt_id: WireId


class FinishReason(DomainModel):
    kind: Literal["stop", "tool-calls", "max-tokens", "aborted", "error"]
    failure: Failure | None

    @model_validator(mode="after")
    def failure_matches_kind(self) -> Self:
        if (self.kind in {"aborted", "error"}) != (self.failure is not None):
            raise ValueError("结束原因与失败信息不一致")
        return self


class Accepted(DomainModel):
    type: Literal["accepted"]
    execution_status: Literal["RESERVED", "DISPATCHING", "IN_FLIGHT"]


class BlockStart(DomainModel):
    type: Literal["block_start"]
    index: NonnegativeInt
    block_type: Literal["text", "tool-call"]


class TextDelta(DomainModel):
    type: Literal["text_delta"]
    index: NonnegativeInt
    text: Text


class ToolDelta(DomainModel):
    type: Literal["tool_call_delta"]
    index: NonnegativeInt
    call_id: Text
    name: NullableText
    arguments_delta: str


class BlockEnd(DomainModel):
    type: Literal["block_end"]
    index: NonnegativeInt
    block: Block


class UsageEvent(DomainModel):
    type: Literal["usage"]
    usage: Usage


class FinishEvent(DomainModel):
    type: Literal["finish"]
    reason: FinishReason
    execution_status: Literal["SUCCEEDED", "FAILED", "CANCELLED"]
    settlement_status: SettlementStatus
    result_artifact_id: WireId
    result_sha256: Hash
    replay_state_id: WireId | None

    @model_validator(mode="after")
    def execution_matches_reason(self) -> Self:
        statuses = {"stop": "SUCCEEDED", "tool-calls": "SUCCEEDED", "max-tokens": "SUCCEEDED", "error": "FAILED", "aborted": "CANCELLED"}
        if self.execution_status != statuses[self.reason.kind]:
            raise ValueError("执行状态与结束原因不一致")
        return self


class ErrorEvent(DomainModel):
    type: Literal["error"]
    failure: Failure
    execution_status: Literal["FAILED", "UNKNOWN", "CANCEL_REQUESTED"]
    settlement_status: SettlementStatus


StreamPayload = Annotated[
    Accepted | BlockStart | TextDelta | ToolDelta | BlockEnd | UsageEvent | FinishEvent | ErrorEvent,
    Field(discriminator="type"),
]


class StreamEvent(DomainModel):
    schema_version: Literal["1.0.0"]
    invocation_id: WireId
    run_id: WireId
    step_id: WireId
    action_id: WireId
    context_manifest_id: WireId
    model_config_revision: WireId
    sequence: NonnegativeInt
    emitted_at: Timestamp
    payload: StreamPayload


class ModelResult(DomainModel):
    schema_version: Literal["1.0.0"]
    invocation_id: WireId
    context_manifest_id: WireId
    model_config_revision: WireId
    provider_request_id: NullableText
    blocks: list[Block]
    usage: Usage | None
    reason: FinishReason
    replay_state_id: WireId | None


EnterpriseModelProtocol = StreamRequest | StreamEvent | ModelResult | InvocationStatus | CancelRequest


class ModelStreamBindings(TypedSchemaBindings):
    def __init__(self, catalogue: SchemaCatalogue):
        super().__init__(catalogue, "model-stream", {
            "Id": WireId, "Text": Text, "NullableText": NullableText, "Hash": Hash, "TokenCount": TokenCount,
            "StreamRequest": StreamRequest, "RequestParameters": RequestParameters, "CancelRequest": CancelRequest,
            "InvocationStatus": InvocationStatus, "Failure": Failure, "TextBlock": TextBlock,
            "ToolBlock": ToolBlock, "Block": Block, "Usage": Usage, "FinishReason": FinishReason,
            "Accepted": Accepted, "BlockStart": BlockStart, "TextDelta": TextDelta, "ToolDelta": ToolDelta,
            "BlockEnd": BlockEnd, "UsageEvent": UsageEvent, "FinishEvent": FinishEvent,
            "ErrorEvent": ErrorEvent, "StreamEvent": StreamEvent, "ModelResult": ModelResult,
        }, EnterpriseModelProtocol)
