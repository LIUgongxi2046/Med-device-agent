from collections.abc import AsyncIterator
from dataclasses import dataclass
import hashlib
import json
from typing import Literal, Self

from jsonschema import Draft202012Validator
from pydantic import model_validator
from referencing import Registry
import rfc8785

from medical_contracts.domain import DomainModel, Hash, WireId
from medical_contracts.model_stream import (
    Block, BlockEnd, BlockStart, FinishReason, RequestParameters,
    StreamPayload, TextBlock, TextDelta, ToolBlock, ToolDelta, Usage, UsageEvent,
)

from .protocol import ExactInput, ProviderSettings
from .provider import ProviderCompletion


def content_hash(value: DomainModel) -> str:
    return hashlib.sha256(rfc8785.dumps(value.model_dump(mode='json'))).hexdigest()


class SimulationCase(DomainModel):
    schema_version: Literal['1.0.0']
    input_data_kind: Literal['SIMULATION']
    case_id: WireId
    receipt_id: WireId
    settings_sha256: Hash
    input_sha256: Hash
    parameters_sha256: Hash
    blocks: list[Block]
    reason: FinishReason
    usage: Usage | None

    @model_validator(mode='after')
    def validate_result(self) -> Self:
        tools = [block for block in self.blocks if isinstance(block, ToolBlock)]
        if len({tool.id for tool in tools}) != len(tools):
            raise ValueError('模拟工具身份必须唯一')
        if self.reason.kind == 'tool-calls' and not tools:
            raise ValueError('模拟工具终态缺少完整工具')
        if tools and self.reason.kind != 'tool-calls':
            raise ValueError('模拟工具必须具有工具终态')
        if self.reason.kind == 'aborted':
            raise ValueError('模型响应不能提供运行取消证明')
        if self.reason.kind in {'stop', 'max-tokens'} and not self.blocks:
            raise ValueError('模拟成功响应缺少完整内容')
        if self.usage is not None and self.usage.source_receipt_id != self.receipt_id:
            raise ValueError('模拟用量必须引用原回执')
        return self


@dataclass(frozen=True, repr=False)
class SimulationCompletion(ProviderCompletion):
    case_id: str
    input_data_kind: Literal['SIMULATION'] = 'SIMULATION'


class SimulationProvider:
    def __init__(self, case: SimulationCase):
        self._case = SimulationCase.model_validate(case.model_dump(mode='python'))

    async def stream(self, settings: ProviderSettings, parameters: RequestParameters,
                     exact_input: ExactInput, receipt_id: str) -> AsyncIterator[StreamPayload | SimulationCompletion]:
        settings = ProviderSettings.model_validate(settings.model_dump(mode='python'))
        parameters = RequestParameters.model_validate(parameters.model_dump(mode='python'))
        exact_input = ExactInput.model_validate(exact_input.model_dump(mode='python'))
        case = SimulationCase.model_validate(self._case.model_dump(mode='python'))
        if not settings.model_id.startswith('SIMULATION:') or not settings.model_version.startswith('SIMULATION:'):
            raise ValueError('模拟模型必须明确登记SIMULATION身份')
        settings.request_options(parameters, exact_input)
        if (case.receipt_id != receipt_id or case.settings_sha256 != content_hash(settings)
                or case.input_sha256 != content_hash(exact_input) or case.parameters_sha256 != content_hash(parameters)):
            raise ValueError('模拟响应与固定输入或原回执不一致')
        definitions = {tool.function.name: tool.function.parameters for tool in exact_input.tools}
        for block in case.blocks:
            if isinstance(block, ToolBlock):
                if block.name not in definitions:
                    raise ValueError('模拟响应包含未经发布的工具')
                arguments = json.loads(block.arguments)
                validator = Draft202012Validator(definitions[block.name], registry=Registry())
                if not isinstance(arguments, dict) or next(validator.iter_errors(arguments), None) is not None:
                    raise ValueError('模拟工具参数未通过已发布Schema')
        # 全部固定内容验证完成后才产生事件，不读取供应商秘密或发送外部请求。
        for index, block in enumerate(case.blocks):
            if isinstance(block, TextBlock):
                yield BlockStart(type='block_start', index=index, block_type='text')
                yield TextDelta(type='text_delta', index=index, text=block.text)
            else:
                yield BlockStart(type='block_start', index=index, block_type='tool-call')
                yield ToolDelta(type='tool_call_delta', index=index, call_id=block.id, name=block.name, arguments_delta=block.arguments)
            yield BlockEnd(type='block_end', index=index, block=block)
        if case.usage is not None:
            yield UsageEvent(type='usage', usage=case.usage)
        yield SimulationCompletion('SIMULATION:' + case.case_id, settings.model_version,
            case.blocks, case.usage, case.reason, case.case_id)
