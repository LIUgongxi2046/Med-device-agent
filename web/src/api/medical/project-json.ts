import { parseTree } from 'jsonc-parser'
import type { Node, ParseError } from 'jsonc-parser'
import { LosslessNumber } from 'lossless-json'
import { stringify } from 'json-custom-numbers'
import { fail } from './protocol'

export function projectJsonIssue(text: string): string {
  const errors: ParseError[] = []
  const tree = parseTree(text, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (errors.length || tree === undefined) return '请填写完整JSON，检查语法、注释及重复内容'
  function unique(node: Node): boolean {
    if (node.type === 'object') {
      const names = (node.children ?? []).map(property => property.children?.[0]?.value as string)
      if (new Set(names).size !== names.length) return false
    }
    return (node.children ?? []).every(unique)
  }
  return unique(tree) ? '' : 'JSON包含重复字段，请核查全部属性'
}

export function readProjectJson(text: unknown): unknown {
  if (typeof text !== 'string') fail('SCHEMA_INVALID', '项目JSON需要原始文本')
  const source = text
  const errors: ParseError[] = []
  const tree = parseTree(source, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (errors.length !== 0 || tree === undefined) fail('SCHEMA_INVALID', '项目JSON语法无效')
  // 语法和定位由成熟组件提供；数值节点使用原始文本，字段不通过原型属性赋值。
  function value(node: Node): unknown {
    switch (node.type) {
      case 'number': return Object.freeze(new LosslessNumber(source.slice(node.offset, node.offset + node.length)))
      case 'string': case 'boolean': case 'null': return node.value as string | boolean | null
      case 'array': return (node.children ?? []).map(value)
      case 'object': {
        const names = new Set<string>()
        const entries = (node.children ?? []).map(property => {
          const key = property.children?.[0]
          const child = property.children?.[1]
          if (property.type !== 'property' || property.children?.length !== 2 || key?.type !== 'string'
              || typeof key.value !== 'string' || child === undefined) fail('SCHEMA_INVALID', '项目JSON字段结构无效')
          if (names.has(key.value)) fail('SCHEMA_INVALID', '项目JSON包含重复字段')
          names.add(key.value)
          return [key.value, value(child)] as const
        })
        return Object.fromEntries(entries)
      }
      default: return fail('SCHEMA_INVALID', '项目JSON节点无效')
    }
  }
  return value(tree)
}

export function formatProjectJson(value: unknown): string {
  const text = stringify(value, undefined, undefined, (_key: string, current: unknown, type: string) => {
    if (current instanceof LosslessNumber) return new LosslessNumber(current.value).toString()
    if (type === 'number' && (typeof current !== 'number' || !Number.isFinite(current)
        || Number.isInteger(current) && !Number.isSafeInteger(current))) fail('SCHEMA_INVALID', '项目数值需要完整原始表示')
    if (type === 'undefined' || type === 'function' || type === 'symbol' || type === 'bigint') {
      fail('SCHEMA_INVALID', '项目内容包含未经JSON定义的值')
    }
  }, { skipToJSON: true })
  if (typeof text !== 'string') fail('SCHEMA_INVALID', '项目内容无法编码')
  return text
}

export function writeProjectJson(value: unknown): string | undefined {
  if (value === undefined) return undefined
  if (typeof value === 'string') {
    readProjectJson(value)
    return value
  }
  return formatProjectJson(value)
}

export function readBoundedJson(text: unknown): unknown {
  if (typeof text !== 'string' || projectJsonIssue(text)) fail('SCHEMA_INVALID', '接口JSON语法或字段重复检查未通过')
  const value: unknown = JSON.parse(text)
  // 成熟语法组件核验原始字段，现有序列化组件拒绝不精确整数。
  formatProjectJson(value)
  return value
}
