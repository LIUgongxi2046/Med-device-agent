from decimal import Decimal, ROUND_05UP, ROUND_CEILING, ROUND_DOWN, ROUND_FLOOR, ROUND_HALF_DOWN, ROUND_HALF_EVEN, ROUND_HALF_UP, ROUND_UP, localcontext

from pydantic import TypeAdapter

from medical_contracts.domain import NonnegativeInt, PositiveInt, preserve_money


INPUT_COUNT = TypeAdapter(NonnegativeInt)
OUTPUT_COUNT = TypeAdapter(PositiveInt)
OPTIONAL_COUNT = TypeAdapter(NonnegativeInt | None)
ROUNDING = frozenset((ROUND_05UP, ROUND_CEILING, ROUND_DOWN, ROUND_FLOOR, ROUND_HALF_DOWN, ROUND_HALF_EVEN, ROUND_HALF_UP, ROUND_UP))


def _rates(*values: str) -> list[Decimal]:
    rates = [Decimal(preserve_money(value)) for value in values]
    if any(value < 0 for value in rates):
        raise ValueError("价格不能包含负数")
    return rates


def upper_amount(input_rate: str, cache_read_rate: str, output_rate: str,
                 max_input_tokens: int, max_output_tokens: int, *, cache_write_rate: str) -> str:
    input_count = INPUT_COUNT.validate_python(max_input_tokens, strict=True)
    output_count = OUTPUT_COUNT.validate_python(max_output_tokens, strict=True)
    rates = _rates(input_rate, cache_read_rate, cache_write_rate, output_rate)
    # 预留使用输入类别中的最高单价，缓存命中不能扩大可用预算。
    with localcontext() as context:
        context.prec = 100
        amount = (max(rates[:3]) * input_count + rates[3] * output_count) / Decimal(1_000_000)
        rounded = amount.quantize(Decimal("0.00000001"), rounding=ROUND_CEILING)
        return preserve_money(format(rounded, "f"))


def actual_amount(input_rate: str, cache_read_rate: str, cache_write_rate: str, output_rate: str,
                  uncached_input_tokens: int | None, cache_read_tokens: int | None,
                  cache_write_tokens: int | None, output_tokens: int | None, *, rounding: str) -> str | None:
    rates = _rates(input_rate, cache_read_rate, cache_write_rate, output_rate)
    counts = [OPTIONAL_COUNT.validate_python(value, strict=True) for value in
              (uncached_input_tokens, cache_read_tokens, cache_write_tokens, output_tokens)]
    if rounding not in ROUNDING:
        raise ValueError("价格版本缺少有效舍入规则")
    # 零单价的金额不依赖计数；原用量保持空值，不产生补充用量记录。
    if any(count is None and rate != 0 for rate, count in zip(rates, counts, strict=True)):
        return None
    with localcontext() as context:
        context.prec = 100
        amount = sum((rate * count for rate, count in zip(rates, counts, strict=True)
                      if count is not None), Decimal(0)) / Decimal(1_000_000)
        rounded = amount.quantize(Decimal("0.00000001"), rounding=rounding)
        return preserve_money(format(rounded, "f"))
