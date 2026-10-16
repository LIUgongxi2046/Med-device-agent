import type { DocumentBody, Node, Selection } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentBytesDigest, documentReference, immutableDocumentValue } from './document-body'
import { tablePositions } from './document-tables'
import { fail, validate } from './protocol'

export type SelectionRequest = { kind: 'TEXT'; node_id: string; start_utf16: number; end_utf16: number }
  | { kind: 'IMAGE'; node_id: string }
  | { kind: 'TABLE'; node_id: string; cell_ids: string[] }
export interface OutlineEntry { node: Node; depth: number }

export function orderedDocumentNodes(body: DocumentBody): OutlineEntry[] {
  validate(documentReference + 'DocumentBody', body)
  const nodes = new Map(body.nodes.map(node => [node.node_id, node]))
  if (nodes.size !== body.nodes.length) fail('SELECTION_INVALID', '正文包含重复节点身份')
  const children = new Map<string | null, Node[]>()
  for (const node of body.nodes) {
    if (node.parent_id !== null && !nodes.has(node.parent_id)) fail('SELECTION_INVALID', '正文节点缺少父节点')
    const group = children.get(node.parent_id) ?? []
    if (group.some(item => item.ordinal === node.ordinal)) fail('SELECTION_INVALID', '同一目录中的节点顺序重复')
    group.push(node); children.set(node.parent_id, group)
  }
  const result: OutlineEntry[] = []; const visited = new Set<string>()
  function visit(parent: string | null, depth: number): void {
    for (const node of (children.get(parent) ?? []).sort((a, b) => a.ordinal - b.ordinal)) {
      if (visited.has(node.node_id)) fail('SELECTION_INVALID', '正文目录包含循环关系')
      visited.add(node.node_id); result.push({ node, depth }); visit(node.node_id, depth + 1)
    }
  }
  visit(null, 0)
  if (visited.size !== nodes.size) fail('SELECTION_INVALID', '正文目录包含无法访问的节点')
  return result
}

export function unicodeCodePointOffset(text: string, offset: number): number {
  if (!Number.isSafeInteger(offset) || offset < 0 || offset > text.length) fail('SELECTION_INVALID', '文字选区范围不存在')
  // DOM 使用 UTF-16，接口使用 Unicode code point；代理对必须保持完整。
  if (offset > 0 && offset < text.length && /[\uD800-\uDBFF]/u.test(text[offset - 1]!) && /[\uDC00-\uDFFF]/u.test(text[offset]!)) {
    fail('SELECTION_INVALID', '文字选区端点必须位于完整Unicode字符之间')
  }
  canonicalDocumentValue(text)
  return Array.from(text.slice(0, offset)).length
}

async function digest(value: unknown): Promise<string> {
  return documentBytesDigest(new TextEncoder().encode(canonicalDocumentValue(value)))
}

export async function createDocumentSelection(body: DocumentBody, documentRevision: string,
    requests: SelectionRequest[], signal: AbortSignal): Promise<Selection> {
  signal.throwIfAborted(); validate(documentReference + 'Id', documentRevision)
  const original = immutableDocumentValue(body); const ordered = orderedDocumentNodes(original)
  if (requests.length === 0) fail('SELECTION_INVALID', '请选择正文文字、图片或表格单元格')
  const seen = new Set<string>(); let previous = -1
  const segments: Selection['segments'] = []
  for (const request of requests) {
    const index = ordered.findIndex(item => item.node.node_id === request.node_id)
    const node = ordered[index]?.node
    if (!node || seen.has(request.node_id) || index <= previous) fail('SELECTION_INVALID', '选区需要按正文顺序选择不同节点')
    seen.add(request.node_id); previous = index
    if (request.kind === 'TEXT') {
      if (node.content.kind !== 'TEXT') fail('SELECTION_INVALID', '文字选区必须对应文字节点')
      const start = unicodeCodePointOffset(node.content.text, request.start_utf16)
      const end = unicodeCodePointOffset(node.content.text, request.end_utf16)
      if (start >= end) fail('SELECTION_INVALID', '文字选区必须包含原文')
      const quote = node.content.text.slice(request.start_utf16, request.end_utf16)
      segments.push({ kind: 'TEXT', node_id: node.node_id, node_sha256: await digest(node), start_cp: start, end_cp: end,
        quote_sha256: await documentBytesDigest(new TextEncoder().encode(quote)) })
    } else if (request.kind === 'IMAGE') {
      if (node.content.kind !== 'IMAGE') fail('SELECTION_INVALID', '图片选区必须对应固定图片节点')
      segments.push({ kind: 'IMAGE', node_id: node.node_id, image_artifact_id: node.content.artifact_id, image_sha256: node.content.sha256 })
    } else {
      if (node.content.kind !== 'TABLE' || request.cell_ids.length === 0 || new Set(request.cell_ids).size !== request.cell_ids.length) {
        fail('SELECTION_INVALID', '表格选区需要不同的实际单元格')
      }
      const positions = tablePositions(node.content)
      const cells = positions.filter(position => request.cell_ids.includes(position.cell.cell_id))
      if (cells.length !== request.cell_ids.length) fail('SELECTION_INVALID', '表格选区包含未知单元格')
      const rows = new Set<string>(); const columns = new Set<string>()
      for (const position of cells) {
        node.content.row_ids.slice(position.row, position.row + position.cell.row_span).forEach(id => rows.add(id))
        node.content.column_ids.slice(position.column, position.column + position.cell.column_span).forEach(id => columns.add(id))
      }
      segments.push({ kind: 'TABLE', node_id: node.node_id, row_ids: node.content.row_ids.filter(id => rows.has(id)),
        column_ids: node.content.column_ids.filter(id => columns.has(id)), cell_ids: cells.map(position => position.cell.cell_id),
        table_sha256: await digest(node) })
    }
    signal.throwIfAborted()
  }
  return immutableDocumentValue(validate<Selection>(documentReference + 'Selection', { document_revision: documentRevision, segments }))
}

export function browserTextSelection(container: HTMLElement, range: Range, body: DocumentBody): SelectionRequest[] {
  if (range.collapsed || !container.contains(range.startContainer) || !container.contains(range.endContainer)) {
    fail('SELECTION_INVALID', '请选择当前正文范围内的完整文字')
  }
  const requests: SelectionRequest[] = []
  for (const element of Array.from(container.querySelectorAll<HTMLElement>('[data-selection-text]'))) {
    if (!range.intersectsNode(element)) continue
    const node = body.nodes.find(item => item.node_id === element.dataset.selectionText)
    const text = element.firstChild
    if (!node || node.content.kind !== 'TEXT' || element.childNodes.length > 1
        || text !== null && text.nodeType !== globalThis.Node.TEXT_NODE
        || element.textContent !== node.content.text) fail('SELECTION_STALE', '页面文字与当前正文节点不一致')
    if (node.content.text.length === 0) continue
    if (text === null) fail('SELECTION_STALE', '当前非空文字节点没有对应原文')
    const selected = range.cloneRange(); const whole = document.createRange(); whole.selectNodeContents(element)
    if (selected.compareBoundaryPoints(Range.START_TO_START, whole) < 0) selected.setStart(whole.startContainer, whole.startOffset)
    if (selected.compareBoundaryPoints(Range.END_TO_END, whole) > 0) selected.setEnd(whole.endContainer, whole.endOffset)
    if (selected.collapsed) continue
    const before = document.createRange(); before.selectNodeContents(element); before.setEnd(selected.startContainer, selected.startOffset)
    const start = before.toString().length; const end = start + selected.toString().length
    requests.push({ kind: 'TEXT', node_id: node.node_id, start_utf16: start, end_utf16: end })
  }
  return requests
}
