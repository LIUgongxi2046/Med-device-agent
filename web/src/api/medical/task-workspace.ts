export type TaskWorkspaceStage = 'config' | 'write' | 'review'
export function taskWorkspaceStage(path: string): TaskWorkspaceStage {
  const tail = path.replace(/\/$/, '').split('/').at(-1)
  return tail === 'write' || tail === 'review' ? tail : 'config'
}
/** Never substitute another document for an explicitly requested unauthorized ID. */
export function authorizedWorkspaceDocument(ids: string[], requested: unknown): string | null {
  if (typeof requested === 'string' && requested.length > 0) return ids.includes(requested) ? requested : null
  if (requested !== undefined && requested !== null) return null
  return ids[0] ?? null
}
export function workspaceDocumentPath(project: string, task: string, document: string, stage: 'write' | 'review'): string {
  return `/projects/${encodeURIComponent(project)}/tasks/${encodeURIComponent(task)}/documents/${encodeURIComponent(document)}/${stage}`
}

/** Route input is only a requested fixed revision, never an authorization proof. */
export function workspaceHistoryRevision(value: unknown): { revision: string | null; issue: string } {
  if (value === undefined) return { revision: null, issue: '' }
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)) {
    return { revision: null, issue: '原版本参数无效，请使用单个小写标准格式的正文版本标识。' }
  }
  return { revision: value, issue: '' }
}
