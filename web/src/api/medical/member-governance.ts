import request from '@/utils/request'
import { canonicalize } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import type { RoleCondition } from './enterprise-roles'

export type GovernanceKind = 'Members' | 'Grants' | 'Invitations'
export interface GovernanceScope { tenant_id: string; project_id: string | null; resource_kind: string; resource_id: string | null }
export interface GovernanceMember {
  member_id: string; user_id: string; status: 'INVITED' | 'ACTIVE' | 'DISABLED'; revision: string; valid_from: string; valid_to: string | null
}
export interface GovernanceGrant {
  grant_id: string; subject_id: string; scope: GovernanceScope; action: string; condition: RoleCondition
  status: 'ACTIVE' | 'REVOKED'; revision: string; valid_from: string; valid_to: string | null; basis: Basis
}
export interface GovernanceInvitation {
  invitation_id: string; user_id: string; roles: { role_id: string; version_id: string; content_sha256: string }[]
  scope: GovernanceScope; status: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED'; revision: string
  expires_at: string; created_by: string; created_at: string; basis: Basis
}
export type GovernanceItem = GovernanceMember | GovernanceGrant | GovernanceInvitation
export interface GovernancePage {
  schema_version: '1.0.0'; tenant_id: string; items: GovernanceItem[]; next_after: string | null
  dataset_watermark: string; current_context: Context
}
export interface GovernanceQuery { limit: number; subject_id?: string; after?: string; dataset_watermark?: string }
const paths: Record<GovernanceKind, string> = { Members: '/api/enterprise-members', Grants: '/api/permission-grants', Invitations: '/api/member-invitations' }
export function governanceAllowed(session: MedicalSession | null, kind?: GovernanceKind): boolean {
  return !!session?.current_context && session.action_summary.some(item => item.action === 'member.manage'
    || (kind === undefined || kind === 'Grants') && item.action === 'project.grant')
}
export const governanceReference = (name: string): string => 'urn:medical:member-governance-read:1.0.0#/$defs/' + name
export function governanceId(item: GovernanceItem): string {
  return 'member_id' in item ? item.member_id : 'grant_id' in item ? item.grant_id : item.invitation_id
}
export async function listGovernance(session: MedicalSession, kind: GovernanceKind, query: GovernanceQuery, signal: AbortSignal): Promise<GovernancePage> {
  if (!session.current_context || !governanceAllowed(session, kind)) fail('ACCESS_DENIED', '当前企业没有相应管理许可')
  const current = session.current_context; validContext(current); validate(governanceReference('Query'), query)
  const result = envelope(await request.get(paths[kind], { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.context_revision } }))
  const page = validate<GovernancePage>(governanceReference(kind + 'Page'), result.data); validContext(page.current_context)
  const last = page.items.at(-1)
  if (result.resource_revision !== null || page.tenant_id !== current.tenant_id || canonicalize(page.current_context) !== canonicalize(current)
      || page.items.length > query.limit || query.dataset_watermark !== undefined && query.dataset_watermark !== page.dataset_watermark
      || page.next_after !== null && (!last || page.next_after !== governanceId(last))
      || new Set(page.items.map(governanceId)).size !== page.items.length) fail('CONTENT_MISMATCH', '当前管理集合、企业上下文或分页身份不一致')
  let previous = query.after
  for (const item of page.items) {
    if (revision(item.revision) === '0' || previous !== undefined && governanceId(item) <= previous) fail('CONTENT_MISMATCH', '管理记录版本或分页顺序不一致')
    const user = 'subject_id' in item ? item.subject_id : item.user_id
    if (query.subject_id !== undefined && user !== query.subject_id || 'scope' in item && item.scope.tenant_id !== current.tenant_id) fail('CONTENT_MISMATCH', '管理记录主体或企业范围不一致')
    if ('roles' in item && new Set(item.roles.map(role => role.role_id)).size !== item.roles.length) fail('CONTENT_MISMATCH', '邀请岗位引用重复')
    previous = governanceId(item)
  }
  return page
}
