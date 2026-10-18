import { parseTree } from 'jsonc-parser'
import type { Node } from 'jsonc-parser'
import { isSafeNumber } from 'lossless-json'
import { projectJsonIssue, readBoundedJson } from './project-json'
import { fail } from './protocol'

// Validate the original numeric tokens, before JSON.parse can round them. The
// comparison accepts equivalent decimal/exponent notation, never approximation.
export function ruleConstantJsonIssue(text: string): string {
  const syntax = projectJsonIssue(text)
  if (syntax) return syntax
  const tree = parseTree(text)
  if (!tree) return '请填写完整常量JSON'
  function exact(node: Node): boolean {
    if (node.type === 'number') {
      const token = text.slice(node.offset, node.offset + node.length)
      const value = Number(token)
      if (!Number.isFinite(value) || Number.isInteger(value) && !Number.isSafeInteger(value)
          || !isSafeNumber(token, { approx: false })) return false
    }
    return (node.children ?? []).every(exact)
  }
  return exact(tree) ? '' : '常量包含无法原样保存的数值，请核查来源及数据规范；系统不会自动舍入、改写为字符串或替换为零'
}

export function readRuleConstantJson(text: string): unknown {
  if (typeof text !== 'string' || ruleConstantJsonIssue(text)) fail('SCHEMA_INVALID', '规则常量JSON无法无损读取')
  return readBoundedJson(text)
}
