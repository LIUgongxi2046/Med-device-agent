import type { DocumentBody, EvidenceLink, Node } from '../../../../contracts/typescript/src/interfaces'

export interface EvidenceChapter { node_id: string; title: string }

/** Navigation only: a recorded relationship is not a professional verification result. */
export function evidenceChapters(body: DocumentBody, link: EvidenceLink): EvidenceChapter[] {
  if (!body.evidence_links.includes(link) && !body.evidence_links.some(item => item.evidence_link_id === link.evidence_link_id)) return []
  const current = body.evidence_links.find(item => item.evidence_link_id === link.evidence_link_id)
  if (!current) return []
  const nodes = new Map(body.nodes.map(node => [node.node_id, node]))
  const chapters = new Map<string, EvidenceChapter>()
  for (const node of body.nodes) {
    if (!current.node_ids.includes(node.node_id) && !node.evidence_link_ids.includes(current.evidence_link_id)) continue
    let ancestor: Node | undefined = node
    const visited = new Set<string>()
    while (ancestor && !visited.has(ancestor.node_id)) {
      visited.add(ancestor.node_id)
      if (ancestor.content.kind === 'SECTION' || ancestor.content.kind === 'DOCUMENT') {
        chapters.set(ancestor.node_id, { node_id: ancestor.node_id, title: ancestor.content.title || '未填写标题' })
        break
      }
      ancestor = ancestor.parent_id ? nodes.get(ancestor.parent_id) : undefined
    }
  }
  return [...chapters.values()]
}

export function chapterEvidence(body: DocumentBody, chapterId: string): EvidenceLink[] {
  return body.evidence_links.filter(link => evidenceChapters(body, link).some(chapter => chapter.node_id === chapterId))
}
