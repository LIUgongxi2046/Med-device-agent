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
// A checked-out draft has one owner until it is retained or explicitly returned.
// Keep the lease through async authorization reads so invalidation revokes it too.
const checkedOut = new Map<string, SessionDocumentDraft>()
let scope: string | null = null
function clear(): void {
  for (const value of [...drafts.values(), ...checkedOut.values()]) { value.pending?.invalidate(); value.draft.invalidate() }
  drafts.clear(); checkedOut.clear(); keys.clear(); scope = null
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
  const owner = checkedOut.get(key)
  if (owner && owner.draft !== value.draft) return false
  checkedOut.delete(key); drafts.set(key, value); return true
}
export function resumeDocumentDraft(key: string): SessionDocumentDraft | null {
  const value = drafts.get(key)
  if (!value || checkedOut.has(key)) return null
  drafts.delete(key); checkedOut.set(key, value); return value
}

/** Return only the exact live checkout; a context clear must never resurrect it. */
export function returnDocumentDraft(key: string, value: SessionDocumentDraft): boolean {
  if (!keys.has(key) || checkedOut.get(key) !== value) {
    value.pending?.invalidate(); value.draft.invalidate(); return false
  }
  checkedOut.delete(key); drafts.set(key, value); return true
}

/** Transfer an authorized checkout to the mounted editor. */
export function acceptDocumentDraft(key: string, value: SessionDocumentDraft): boolean {
  if (!keys.has(key) || checkedOut.get(key) !== value) return false
  checkedOut.delete(key); return true
}
