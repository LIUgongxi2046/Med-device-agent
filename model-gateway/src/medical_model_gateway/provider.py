import asyncio
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
import json
import logging
from pathlib import Path

import httpx2
from jsonschema import Draft202012Validator
from openai import AsyncOpenAI
from openai.types.chat import ChatCompletionChunk
from openai.types.completion_usage import CompletionUsage
from pydantic import TypeAdapter
from referencing import Registry

from medical_contracts.domain import NonnegativeInt, WireId
from medical_contracts.model_stream import (
    Block, BlockEnd, BlockStart, Failure, FinishReason, RequestParameters,
    StreamPayload, TextBlock, TextDelta, ToolBlock, ToolDelta, Usage, UsageEvent,
)

from .credentials import read_key
from .protocol import ExactInput, ProviderSettings


COUNT = TypeAdapter(NonnegativeInt)
IDENTITY = TypeAdapter(WireId)


@dataclass(frozen=True, repr=False)
class ProviderCompletion:
    provider_request_id: str
    response_model: str
    blocks: list[Block]
    usage: Usage | None
    reason: FinishReason


@dataclass(repr=False)
class _Tool:
    index: int
    call_id: str
    name: str
    arguments: str = ""


@dataclass(repr=False)
class _Response:
    receipt_id: str
    settings: ProviderSettings
    exact_input: ExactInput
    provider_request_id: str | None = None
    response_model: str | None = None
    finish_reason: str | None = None
    text_index: int | None = None
    text: str = ""
    next_index: int = 0
    tools: dict[int, _Tool] = field(default_factory=dict)
    usage: Usage | None = None

    def consume(self, chunk: ChatCompletionChunk) -> list[StreamPayload]:
        if not isinstance(chunk.id, str) or not chunk.id or not isinstance(chunk.model, str) or not chunk.model:
            raise ValueError("模型响应缺少供应商身份")
        if self.provider_request_id is None:
            self.provider_request_id, self.response_model = chunk.id, chunk.model
            if chunk.model not in {self.settings.model_id, self.settings.model_version}:
                raise ValueError("供应商响应模型与固定配置不一致")
        elif chunk.id != self.provider_request_id or chunk.model != self.response_model:
            raise ValueError("模型流中的供应商身份发生变化")
        if self.usage is not None:
            raise ValueError("最终用量之后仍然出现模型片段")
        events = []
        if len(chunk.choices) > 1:
            raise ValueError("模型流只能包含单一候选")
        if chunk.choices:
            choice = chunk.choices[0]
            delta = choice.delta
            if type(choice.index) is not int or choice.index != 0:
                raise ValueError("模型候选序号无效")
            if delta.role not in {None, "assistant"} or delta.function_call is not None or delta.refusal is not None:
                raise ValueError("模型片段包含未经发布的响应字段")
            if set(delta.model_extra or {}) - {"reasoning_content"}:
                raise ValueError("模型片段包含未知扩展字段")
            if self.finish_reason is not None:
                raise ValueError("模型终态之后仍然出现候选")
            if delta.content:
                if self.text_index is None:
                    self.text_index = self.next_index
                    self.next_index += 1
                    events.append(BlockStart(type="block_start", index=self.text_index, block_type="text"))
                self.text += delta.content
                events.append(TextDelta(type="text_delta", index=self.text_index, text=delta.content))
            for fragment in delta.tool_calls or []:
                if type(fragment.index) is not int or fragment.index < 0 or fragment.type not in {None, "function"}:
                    raise ValueError("工具片段类型或序号无效")
                function = fragment.function
                if fragment.index not in self.tools:
                    if not fragment.id or function is None or not function.name:
                        raise ValueError("首次工具片段缺少完整身份")
                    if fragment.id in {tool.call_id for tool in self.tools.values()}:
                        raise ValueError("工具调用身份重复")
                    if function.name not in {tool.function.name for tool in self.exact_input.tools}:
                        raise ValueError("模型请求了未经发布的工具")
                    tool = _Tool(self.next_index, fragment.id, function.name)
                    self.next_index += 1
                    self.tools[fragment.index] = tool
                    events.append(BlockStart(type="block_start", index=tool.index, block_type="tool-call"))
                tool = self.tools[fragment.index]
                if fragment.id is not None and fragment.id != tool.call_id:
                    raise ValueError("工具调用身份发生变化")
                if function is not None:
                    if function.name is not None and function.name != tool.name:
                        raise ValueError("工具名称发生变化")
                    if function.arguments is not None:
                        tool.arguments += function.arguments
                        events.append(ToolDelta(type="tool_call_delta", index=tool.index,
                                                call_id=tool.call_id, name=tool.name,
                                                arguments_delta=function.arguments))
            if choice.finish_reason is not None:
                if choice.finish_reason not in {"stop", "length", "tool_calls", "content_filter",
                                                "insufficient_system_resource", "aborted"}:
                    raise ValueError("供应商结束原因未经批准")
                self.finish_reason = choice.finish_reason
        elif chunk.usage is None or self.finish_reason is None:
            raise ValueError("空候选片段没有最终用量")
        if chunk.usage is not None:
            if self.finish_reason is None:
                raise ValueError("用量在供应商终态之前出现")
            self.usage = normalize_usage(chunk.usage, self.receipt_id)
        return events

    def complete(self) -> tuple[list[StreamPayload], ProviderCompletion]:
        if self.finish_reason is None or self.provider_request_id is None or self.response_model is None:
            raise ValueError("模型连接结束但没有有效供应商终态")
        events = []
        indexed = {}
        if self.text_index is not None:
            indexed[self.text_index] = TextBlock(type="text", text=self.text)
        if self.finish_reason == "tool_calls" and not self.tools:
            raise ValueError("工具终态缺少工具调用")
        if self.finish_reason == "stop" and self.tools:
            raise ValueError("文本终态包含待执行工具")
        if self.tools and self.finish_reason != "tool_calls":
            raise ValueError("未取得可执行的完整工具终态")
        definitions = {tool.function.name: tool.function.parameters for tool in self.exact_input.tools}
        for tool in self.tools.values():
            arguments = json.loads(tool.arguments)
            validator = Draft202012Validator(definitions[tool.name], registry=Registry())
            if not isinstance(arguments, dict) or next(validator.iter_errors(arguments), None) is not None:
                raise ValueError("完整工具参数未通过已发布Schema")
            indexed[tool.index] = ToolBlock(type="tool-call", id=tool.call_id,
                                           name=tool.name, arguments=tool.arguments)
        for index in sorted(indexed):
            events.append(BlockEnd(type="block_end", index=index, block=indexed[index]))
        if self.usage is not None:
            events.append(UsageEvent(type="usage", usage=self.usage))
        if self.finish_reason in {"stop", "length", "tool_calls"}:
            kind = {"stop": "stop", "length": "max-tokens", "tool_calls": "tool-calls"}[self.finish_reason]
            reason = FinishReason(kind=kind, failure=None)
        else:
            reason = FinishReason(kind="error", failure=Failure(
                code="MODEL_PROVIDER_TERMINATED", message="供应商终止了当前生成",
                provider_status=None, provider_request_id=self.provider_request_id))
        return events, ProviderCompletion(self.provider_request_id, self.response_model,
                                          [indexed[index] for index in sorted(indexed)], self.usage, reason)


def normalize_usage(value: CompletionUsage, receipt_id: str) -> Usage:
    IDENTITY.validate_python(receipt_id)
    prompt = COUNT.validate_python(value.prompt_tokens)
    output = COUNT.validate_python(value.completion_tokens)
    total = COUNT.validate_python(value.total_tokens)
    if prompt + output != total:
        raise ValueError("供应商总Token与输入输出计数不一致")
    extensions = value.model_extra or {}
    hit = extensions.get("prompt_cache_hit_tokens")
    miss = extensions.get("prompt_cache_miss_tokens")
    if hit is not None:
        hit = COUNT.validate_python(hit)
    if miss is not None:
        miss = COUNT.validate_python(miss)
    if hit is not None and miss is not None and hit + miss != prompt:
        raise ValueError("供应商缓存与未缓存计数不一致")
    details = value.completion_tokens_details
    reasoning = None if details is None else details.reasoning_tokens
    if reasoning is not None and COUNT.validate_python(reasoning) > output:
        raise ValueError("推理Token超过输出Token")
    prompt_details = value.prompt_tokens_details
    if prompt_details is not None and prompt_details.cached_tokens is not None:
        cached = COUNT.validate_python(prompt_details.cached_tokens)
        if cached > prompt or (hit is not None and hit != cached):
            raise ValueError("供应商缓存计数口径不一致")
    return Usage(uncached_input_tokens=miss, cache_read_tokens=hit, cache_write_tokens=None,
                 output_tokens=output, total_tokens=total, reasoning_tokens=reasoning,
                 source_receipt_id=receipt_id)


class DeepSeekProvider:
    BASE_URL = "https://api.deepseek.com"

    def __init__(self, key_path: Path):
        self._key_path = key_path

    def client(self) -> AsyncOpenAI:
        key = read_key(self._key_path)
        for name in ("openai", "openai._base_client", "httpx2", "httpcore2"):
            logging.getLogger(name).setLevel(logging.CRITICAL + 1)
        transport = httpx2.AsyncClient(trust_env=False, follow_redirects=False,
                                      timeout=httpx2.Timeout(60, connect=5, write=60, pool=5))
        return AsyncOpenAI(api_key=key.get_secret_value(), base_url=self.BASE_URL,
                           max_retries=0, http_client=transport)

    async def list_models(self) -> list[dict]:
        async with asyncio.timeout(10), self.client() as client:
            page = await client.models.list()
            models = [model.model_dump(mode="json") for model in page.data]
            identities = [model["id"] for model in models]
            if not identities or any(not isinstance(identity, str) or not identity for identity in identities):
                raise ValueError("供应商模型清单缺少有效身份")
            if len(identities) != len(set(identities)):
                raise ValueError("供应商模型清单包含重复身份")
            return models

    async def stream(self, settings: ProviderSettings, parameters: RequestParameters,
                     exact_input: ExactInput, receipt_id: str) -> AsyncIterator[StreamPayload | ProviderCompletion]:
        settings = ProviderSettings.model_validate(settings.model_dump(mode="python"))
        parameters = RequestParameters.model_validate(parameters.model_dump(mode="python"))
        exact_input = ExactInput.model_validate(exact_input.model_dump(mode="python"))
        options = settings.request_options(parameters, exact_input)
        IDENTITY.validate_python(receipt_id)
        state = _Response(receipt_id, settings, exact_input)
        # 调用方必须已经提交一次发送准入；本组件不提供浏览器或专业Worker入口。
        async with asyncio.timeout(settings.deadline_seconds), self.client() as client:
            response = await client.chat.completions.create(**options)
            async with response:
                async for chunk in response:
                    for event in state.consume(chunk):
                        yield event
            events, completion = state.complete()
            for event in events:
                yield event
            # C07保存并重读ModelResult后才能向宿主发送finish。
            yield completion
