from typing import Annotated, Literal, Self

from jsonschema import Draft202012Validator
from pydantic import Field, model_validator

from medical_contracts.domain import DomainModel, PositiveInt, Text
from medical_contracts.model_stream import RequestParameters


class PlainMessage(DomainModel):
    role: Literal["system", "user"]
    content: Text


class FunctionCall(DomainModel):
    name: Text
    arguments: Text


class ToolCall(DomainModel):
    id: Text
    type: Literal["function"]
    function: FunctionCall


class AssistantMessage(DomainModel):
    role: Literal["assistant"]
    content: str | None
    tool_calls: list[ToolCall]


class ToolMessage(DomainModel):
    role: Literal["tool"]
    content: str
    tool_call_id: Text


class FunctionDefinition(DomainModel):
    name: Annotated[str, Field(pattern=r"^[A-Za-z0-9_-]{1,128}$")]
    description: Text
    parameters: dict

    @model_validator(mode="after")
    def validate_parameters(self) -> Self:
        Draft202012Validator.check_schema(self.parameters)
        return self


class ToolDefinition(DomainModel):
    type: Literal["function"]
    function: FunctionDefinition


Message = Annotated[PlainMessage | AssistantMessage | ToolMessage, Field(discriminator="role")]


class ExactInput(DomainModel):
    messages: Annotated[list[Message], Field(min_length=1)]
    tools: list[ToolDefinition]

    @model_validator(mode="after")
    def validate_history(self) -> Self:
        names = [tool.function.name for tool in self.tools]
        if len(names) != len(set(names)):
            raise ValueError("已发布工具名称必须唯一")
        pending = set()
        known = set()
        for message in self.messages:
            if pending and not isinstance(message, ToolMessage):
                raise ValueError("历史工具调用尚未取得全部结果")
            if isinstance(message, AssistantMessage):
                if message.content is None and not message.tool_calls:
                    raise ValueError("历史助手消息缺少内容")
                for call in message.tool_calls:
                    if call.id in known:
                        raise ValueError("历史工具身份重复")
                    known.add(call.id)
                    pending.add(call.id)
            elif isinstance(message, ToolMessage):
                if message.tool_call_id not in pending:
                    raise ValueError("历史工具结果没有对应请求")
                pending.remove(message.tool_call_id)
        if pending:
            raise ValueError("历史工具调用尚未取得全部结果")
        return self


class ProviderSettings(DomainModel):
    model_id: Text
    model_version: Text
    max_output_tokens: PositiveInt
    deadline_seconds: Annotated[int, Field(ge=1, le=300)]
    temperature_enabled: bool
    reasoning_efforts: list[Literal["none", "low", "high", "max"]]
    stop_limit: Annotated[int, Field(ge=0, le=16)]

    def request_options(self, parameters: RequestParameters, exact_input: ExactInput) -> dict:
        if parameters.max_output_tokens > self.max_output_tokens:
            raise ValueError("输出Token超过固定配置上限")
        if parameters.reasoning_effort is not None and parameters.reasoning_effort not in self.reasoning_efforts:
            raise ValueError("推理参数没有取得固定配置许可")
        if parameters.temperature is not None:
            if not self.temperature_enabled or not 0 <= parameters.temperature <= 2:
                raise ValueError("temperature参数没有取得固定配置许可")
            if parameters.reasoning_effort != "none":
                raise ValueError("当前模式无法保证temperature参数实际生效")
        if len(parameters.stop) > self.stop_limit:
            raise ValueError("停止序列超过固定配置上限")
        # 当前供应商的推理工具继续流程要求内部推理历史，发布规则禁止保存该内容。
        if exact_input.tools and parameters.reasoning_effort != "none":
            raise ValueError("工具模式需要明确关闭内部推理")
        result = {"model": self.model_id, "max_tokens": parameters.max_output_tokens,
                  "messages": [message.model_dump(mode="json") for message in exact_input.messages],
                  "stream": True, "stream_options": {"include_usage": True}}
        if parameters.reasoning_effort is not None:
            result["reasoning_effort"] = parameters.reasoning_effort
        if parameters.temperature is not None:
            result["temperature"] = parameters.temperature
        if parameters.stop:
            result["stop"] = list(parameters.stop)
        if exact_input.tools:
            result["tools"] = [tool.model_dump(mode="json") for tool in exact_input.tools]
        return result
