from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import re
from uuid import UUID


UNSIGNED_MAX = (1 << 64) - 1
REVISION_PATTERN = re.compile(r"(?:0|[1-9][0-9]*)", flags=re.ASCII)
DECIMAL_PATTERN = re.compile(r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", flags=re.ASCII)
UTC_PATTERN = re.compile(
    r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
    r"(?:\.[0-9]{1,6})?(?:Z|\+00:00)", flags=re.ASCII
)


def uuid_value(value: str) -> UUID:
    if not isinstance(value, str):
        raise ValueError("UUID 必须使用字符串")
    parsed = UUID(value)
    if str(parsed) != value:
        raise ValueError("UUID 必须使用小写标准形式")
    return parsed


def unsigned_revision(value: str) -> int:
    if not isinstance(value, str) or len(value) > 20 or not REVISION_PATTERN.fullmatch(value):
        raise ValueError("revision 必须使用非负十进制字符串")
    parsed = int(value)
    if parsed > UNSIGNED_MAX:
        raise ValueError("revision 超出 BIGINT UNSIGNED 范围")
    return parsed


def decimal_value(value: str, precision: int, scale: int) -> Decimal:
    if precision <= 0 or scale < 0 or scale > precision:
        raise ValueError("Decimal 字段精度配置无效")
    if not isinstance(value, str) or not DECIMAL_PATTERN.fullmatch(value):
        raise ValueError("Decimal 必须使用普通十进制字符串")
    parsed = Decimal(value)
    components = parsed.as_tuple()
    integer_digits = 0 if parsed.is_zero() else max(0, len(components.digits) + components.exponent)
    fractional_digits = max(0, -components.exponent)
    if integer_digits > precision - scale or fractional_digits > scale:
        raise ValueError("Decimal 超出字段精度")
    return parsed


def utc_time(value: str) -> datetime:
    if not isinstance(value, str) or not UTC_PATTERN.fullmatch(value):
        raise ValueError("时间必须使用 UTC ISO 8601 和微秒精度")
    parsed = datetime.fromisoformat(value)
    if parsed.utcoffset() != timezone.utc.utcoffset(parsed):
        raise ValueError("时间必须使用 UTC")
    return parsed


def code_point_quote(text: str, start: int, end: int, expected_sha256: str) -> str:
    if not isinstance(text, str) or type(start) is not int or type(end) is not int:
        raise ValueError("选区必须提供原文和整数 code point 范围")
    if start < 0 or end <= start or end > len(text):
        raise ValueError("选区超出原文范围")
    quote = text[start:end]
    if hashlib.sha256(quote.encode("utf-8")).hexdigest() != expected_sha256:
        raise ValueError("选区摘要不一致")
    return quote
