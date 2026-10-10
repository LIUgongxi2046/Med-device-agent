import type { DocumentBody, DocumentSnapshot, FieldValue } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from './protocol'
import type { prepareDocumentDraftSave } from './documents'
import { DocumentEditBuffer } from './document-body'
import { fail } from './protocol'

export interface SessionDocumentDraft {
  snapshot: DocumentSnapshot; baseline: DocumentBody; draft: DocumentEditBuffer; dependency: string; reason: string
  pending: Awaited<ReturnType<typeof prepareDocumentDraftSave>> | null
  fieldInputs: Record<string, FieldValue>
}
const drafts = new Map<string, SessionDocumentDraft>()
const keys = new Set<string>()
let scope: string | null = null
function clear(): void {
  for (const value of drafts.values()) { value.pending?.invalidate(); value.draft.invalidate() }
  drafts.clear(); keys.clear(); scope = null
}
window.addEventListener('medical-context-invalidated', clear)
export function documentDraftKey(session: MedicalSession, project: string, document: string): string {
  const context = session.current_context
  if (context === null) fail('CONTEXT_CHANGED', '文稿草稿需要当前企业上下文')
  const current = JSON.stringify([session.user.user_id, context.tenant_id, context.context_revision, context.auth_epoch, context.governance_epoch])
  if (scope !== null && scope !== current) clear()
  scope = current; const key = JSON.stringify([current, project, document]); keys.add(key); return key
}
export function retainDocumentDraft(key: string, value: SessionDocumentDraft): boolean {
  if (!keys.has(key)) { value.pending?.invalidate(); value.draft.invalidate(); return false }
  drafts.set(key, value); return true
}
export function resumeDocumentDraft(key: string): SessionDocumentDraft | null {
  const value = drafts.get(key); drafts.delete(key); return value ?? null
}
