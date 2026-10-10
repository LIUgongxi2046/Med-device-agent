import { Temporal } from '@js-temporal/polyfill'
import Decimal from 'decimal.js'
import { sha256 } from './catalogue.js'

const revisionPattern = /^(?:0|[1-9][0-9]*)$/
const decimalPattern = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$/
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const utcPattern = /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?(?:Z|\+00:00)$/
const unsignedMax = (1n << 64n) - 1n

function matches(pattern: RegExp, value: unknown): value is string {
  return typeof value === 'string' && pattern.exec(value)?.[0] === value
}

export function uuidValue(value: unknown): string {
  if (!matches(uuidPattern, value)) throw new Error('UUID 必须使用小写标准形式')
  return value
}

export function unsignedRevision(value: unknown): bigint {
  if (!matches(revisionPattern, value) || value.length > 20) throw new Error('revision 必须使用非负十进制字符串')
  const parsed = BigInt(value)
  if (parsed > unsignedMax) throw new Error('revision 超出 BIGINT UNSIGNED 范围')
  return parsed
}

export function decimalValue(value: unknown, precision: number, scale: number): Decimal {
  if (!Number.isInteger(precision) || !Number.isInteger(scale) || precision <= 0 || scale < 0 || scale > precision) throw new Error('Decimal 字段精度配置无效')
  if (!matches(decimalPattern, value)) throw new Error('Decimal 必须使用普通十进制字符串')
  const parsed = new Decimal(value)
  const point = value.indexOf('.')
  const fractionalDigits = point < 0 ? 0 : value.length - point - 1
  const integerDigits = parsed.isZero() ? 0 : Math.max(0, parsed.e + 1)
  if (integerDigits > precision - scale || fractionalDigits > scale) throw new Error('Decimal 超出字段精度')
  return parsed
}

export function utcTime(value: unknown): Temporal.Instant {
  if (!matches(utcPattern, value)) throw new Error('时间必须使用 UTC ISO 8601 和微秒精度')
  return Temporal.Instant.from(value)
}

export async function codePointQuote(text: string, start: number, end: number, expectedSha256: string): Promise<string> {
  const characters = Array.from(text)
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end <= start || end > characters.length) throw new Error('选区超出原文范围')
  const quote = characters.slice(start, end).join('')
  if (await sha256(new TextEncoder().encode(quote)) !== expectedSha256) throw new Error('选区摘要不一致')
  return quote
}
