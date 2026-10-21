import { LosslessNumber, isSafeNumber } from 'lossless-json';
import { readProjectJson } from './project-json';
import { fail, MedicalFailure } from './protocol';

/** N01 HTTP contracts contain integer numbers only; inspect original tokens before Number conversion. */
export function readN01Json(raw: unknown): unknown {
    if (typeof raw !== 'string') fail('SCHEMA_INVALID', 'N01 响应需要原始 JSON 文本');
    if (new TextEncoder().encode(raw).byteLength > 8388608) throw new MedicalFailure('CONTEXT_LIMIT', 'N01 响应超过客户端核验容量');
    function exact(value: unknown): unknown {
        if (value instanceof LosslessNumber) {
            const number = Number(value.value);
            if (!Number.isSafeInteger(number) || !isSafeNumber(value.value, { approx: false }))
                fail('SCHEMA_INVALID', 'N01 数值必须为可精确读取的整数，不能舍入或替换为零');
            return number;
        }
        if (Array.isArray(value)) return value.map(exact);
        if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, exact(item)]));
        return value;
    }
    return exact(readProjectJson(raw));
}
