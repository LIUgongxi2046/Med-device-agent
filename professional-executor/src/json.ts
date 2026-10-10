import { parse, toSafeNumberOrThrow } from 'lossless-json'
import { assertJson, type JsonValue } from '@medical-agent/contracts'

export function readJson(text: string): JsonValue {
  const value = parse(text, null, {
    parseNumber: toSafeNumberOrThrow,
    onDuplicateKey: () => { throw new Error('JSON 包含重复字段') },
  })
  assertJson(value)
  return value
}
