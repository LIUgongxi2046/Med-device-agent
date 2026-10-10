from typing import Annotated, TypeVar

from pydantic import AfterValidator, BaseModel, ConfigDict, Field

from .wire import decimal_value, unsigned_revision, utc_time, uuid_value


JSON_INTEGER_MAX = (1 << 53) - 1


class DomainModel(BaseModel):
    model_config = ConfigDict(
        strict=True, extra="forbid", frozen=True, revalidate_instances="always",
        hide_input_in_errors=True, allow_inf_nan=False,
    )


def preserve_uuid(value: str) -> str:
    uuid_value(value)
    return value


def preserve_revision(value: str) -> str:
    unsigned_revision(value)
    return value


def preserve_timestamp(value: str) -> str:
    utc_time(value)
    return value


def preserve_money(value: str) -> str:
    decimal_value(value, 24, 8)
    return value


def preserve_measurement(value: str) -> str:
    decimal_value(value, 38, 12)
    return value


Item = TypeVar("Item", str, int)


def unique_items(value: list[Item]) -> list[Item]:
    if len(value) != len(set(value)):
        raise ValueError("数组成员必须唯一")
    return value


# 表示校验保持原始字符串；数值转换仅由使用方在计算时执行。
WireId = Annotated[str, AfterValidator(preserve_uuid)]
Revision = Annotated[str, AfterValidator(preserve_revision)]
Timestamp = Annotated[str, AfterValidator(preserve_timestamp)]
MoneyDecimal = Annotated[str, AfterValidator(preserve_money)]
MeasurementDecimal = Annotated[str, AfterValidator(preserve_measurement)]
Text = Annotated[str, Field(min_length=1)]
Hash = Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]
NonnegativeInt = Annotated[int, Field(ge=0, le=JSON_INTEGER_MAX)]
PositiveInt = Annotated[int, Field(ge=1, le=JSON_INTEGER_MAX)]
