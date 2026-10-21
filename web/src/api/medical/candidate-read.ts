import request from '@/utils/request'
import type { CandidateView } from '../../../../contracts/typescript/src/interfaces'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'

const shared = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/'
export const candidateViewReference = shared + 'CandidateView'
export type CandidateReadState = 'UNAVAILABLE' | 'LOADING' | 'READY' | 'ERROR' | 'STALE_AUTH' | 'CANCELLED'

/** This is only a local read guard. The server remains the authorization authority. */
export function candidateReadAllowed(session: MedicalSession | null, projectId: string, candidateId: string): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === 'candidate.read'
    && (item.project_id === null || item.project_id === projectId)
    && (item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
      || item.resource_kind === 'PROJECT' && item.project_id === projectId && item.resource_id === projectId
      || item.resource_kind === 'CANDIDATE' && item.project_id === projectId && item.resource_id === candidateId))
}
export function candidateReadScope(session: MedicalSession): string {
  const context = session.current_context
  return JSON.stringify([session.user.user_id, context?.tenant_id, context?.context_revision,
    context?.auth_epoch, context?.governance_epoch, session.action_summary])
}

/** BX-07 reads metadata only. Artifact references do not authorize content downloads or adoption. */
export async function readCandidateMetadata(session: () => MedicalSession, projectId: string, candidateId: string,
  signal: AbortSignal): Promise<CandidateView> {
  signal.throwIfAborted()
  validate(shared + 'Id', projectId); validate(shared + 'Id', candidateId)
  const current = session(), context = current.current_context
  if (!context || !candidateReadAllowed(current, projectId, candidateId)) fail('ACCESS_DENIED', '当前身份没有此候选的读取权限')
  validContext(context)
  validate(shared + 'Id', context.tenant_id); validate(shared + 'Id', current.user.user_id)
  const scope = candidateReadScope(current)
  const raw = await request.get('/api/candidates/' + candidateId, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } })
  signal.throwIfAborted()
  if (candidateReadScope(session()) !== scope || !candidateReadAllowed(session(), projectId, candidateId)) {
    fail('CONTEXT_CHANGED', '候选读取期间身份或权限已变化，请重新核验')
  }
  const wrapped = envelope(raw)
  const value = validate<CandidateView>(candidateViewReference, wrapped.data)
  if (raw.status !== 200 || value.candidate_id !== candidateId || revision(value.revision) === '0'
    || wrapped.resource_revision !== value.revision || raw.headers['x-resource-revision'] !== value.revision) {
    fail('CONTENT_MISMATCH', '候选标识或修订与读取响应不一致')
  }
  return value
}
