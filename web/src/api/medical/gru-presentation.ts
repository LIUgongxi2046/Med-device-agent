import type { DocumentBody, Selection } from '../../../../contracts/typescript/src/interfaces'
import type { GruAppend } from './gru-records'

export const gruIntentLabels: Record<GruAppend['intent'], string> = {
  QUERY: '提问与说明', RETRIEVE: '资料检索需求', CHECK: '检查需求', PROPOSE_EDIT: '修改建议需求'
}

/** Presentation only: never reconstruct a write payload or imply a verified response. */
export function gruSelectionPreview(selection: Selection | null | undefined, revision: string, body?: DocumentBody): string[] {
  if (!selection) return []
  if (selection.document_revision !== revision) return ['选区属于另一正文版本，请重新选择。']
  return selection.segments.map(segment => {
    const node = body?.nodes.find(item => item.node_id === segment.node_id)
    if (segment.kind === 'TEXT') {
      const points = node?.content.kind === 'TEXT' ? Array.from(node.content.text) : null
      if (points && Number.isSafeInteger(segment.start_cp) && Number.isSafeInteger(segment.end_cp)
          && segment.start_cp >= 0 && segment.end_cp > segment.start_cp && segment.end_cp <= points.length) {
        return points.slice(segment.start_cp, segment.end_cp).join('')
      }
      return `文字节点 ${segment.node_id} · 字符 ${segment.start_cp}–${segment.end_cp}（原文未加载）`
    }
    if (segment.kind === 'IMAGE') return `图片节点 ${segment.node_id}`
    return `表格节点 ${segment.node_id} · 已选 ${segment.cell_ids.length} 个单元格`
  })
}
