import type { MedicalSession } from './protocol'

export interface SourceReturnOrigin { path: string; documentRevision: string; chapterId: string; focusNodeId: string; annotationId?: string }
interface Entry extends SourceReturnOrigin { scope: string; sourceVersion: string; expires: number }
const entries = new Map<string, Entry>()
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const documentPath = /^\/projects\/([0-9a-f-]+)\/tasks\/([0-9a-f-]+)\/documents\/([0-9a-f-]+)\/(write|review)$/
export function sourceReturnScope(session: MedicalSession): string | null {
  const context = session.current_context
  return context ? JSON.stringify([session.user.user_id, context.tenant_id, context.context_revision,
    context.auth_epoch, context.governance_epoch, session.action_summary]) : null
}
function validPath(path: string): boolean { const match = documentPath.exec(path); return !!match && match.slice(1, 4).every(value => uuid.test(value!)) }
function lookup(session: MedicalSession, token: unknown): Entry | null {
  if (typeof token !== 'string' || !uuid.test(token)) return null
  const entry = entries.get(token)
  if (!entry) return null
  if (entry.scope !== sourceReturnScope(session) || entry.expires <= Date.now()) { entries.delete(token); return null }
  return entry
}
// Ephemeral navigation hints only: no body, draft, URL supplied by the reader, or authority is retained.
export function createSourceReturn(session: MedicalSession, sourceVersion: string, origin: SourceReturnOrigin): string | null {
  const scope = sourceReturnScope(session)
  if (origin.annotationId !== undefined && !uuid.test(origin.annotationId)) return null
  if (!scope || !validPath(origin.path) || ![sourceVersion, origin.documentRevision, origin.chapterId, origin.focusNodeId].every(value => uuid.test(value))) return null
  for (const [token, entry] of entries) if (entry.expires <= Date.now() || entry.scope !== scope) entries.delete(token)
  while (entries.size >= 32) entries.delete(entries.keys().next().value!)
  const token = crypto.randomUUID()
  entries.set(token, { path: origin.path, documentRevision: origin.documentRevision, chapterId: origin.chapterId,
    focusNodeId: origin.focusNodeId, ...(origin.annotationId ? { annotationId: origin.annotationId } : {}),
    scope, sourceVersion, expires: Date.now() + 30 * 60 * 1000 })
  return token
}
export function sourceReturnRoute(session: MedicalSession, sourceVersion: string, token: unknown): { path: string; query: { source_return: string } } | null {
  const entry = lookup(session, token)
  return entry && entry.sourceVersion === sourceVersion ? { path: entry.path, query: { source_return: token as string } } : null
}
export type SourceReturnResult = { status: 'ready'; chapterId: string; focusNodeId: string; annotationId?: string } | { status: 'invalid' | 'stale' | 'blocked' }
export function consumeSourceReturn(session: MedicalSession, token: unknown, current: {
  path: string; documentRevision: string; currentRevision: string | null; active: boolean; dirty: boolean; nodeIds: string[]
}): SourceReturnResult {
  const entry = lookup(session, token)
  if (!entry || entry.path !== current.path) return { status: 'invalid' }
  if (!current.active || current.dirty) return { status: 'blocked' }
  entries.delete(token as string)
  if (entry.documentRevision !== current.documentRevision || entry.documentRevision !== current.currentRevision
      || !current.nodeIds.includes(entry.chapterId) || !current.nodeIds.includes(entry.focusNodeId)) return { status: 'stale' }
  return { status: 'ready', chapterId: entry.chapterId, focusNodeId: entry.focusNodeId, ...(entry.annotationId ? { annotationId: entry.annotationId } : {}) }
}
if (typeof window !== 'undefined') window.addEventListener?.('medical-context-invalidated', () => entries.clear())
