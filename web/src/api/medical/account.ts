import request from '@/utils/request'
import { envelope, fail, revision, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { canonicalize } from 'json-canonicalize'

export interface OwnAccount {
  user_id: string; username: string; display_name: string; platform_role: 'USER' | 'PLATFORM_ADMIN'; revision: string
}
export interface PendingCredential { actor: string; key: string; revision: string }
export interface OwnProfileFields { nickName: string; email: string | null; phonenumber: string | null; sex: string | null }
export interface PendingOwnProfile extends PendingCredential { sha256: string }
export interface RuoYiProfile {
  code: 200; roleGroup: string | null; postGroup: string | null
  data: OwnProfileFields & { userId: number; userName: string; avatar: string | null; createTime: string | null
    dept?: { deptName: string } | null }
}
export interface FrameworkProfile extends RuoYiProfile { account_revision: string; business_user_id: string }
interface AccountMutation {
  schema_version: '1.0.0'; operation_id: string; reauthentication_required: boolean
  account: OwnAccount & { status: 'ACTIVE' | 'DISABLED'; credential_revision: string }
}
const pendingPrefix = 'medical.own.credential:'
const profilePrefix = 'medical.own.profile:'

export function readPendingOwnProfile(actor: string): PendingOwnProfile | null {
  const saved = sessionStorage.getItem(profilePrefix + actor)
  if (saved === null) return null
  const pending = validate<PendingOwnProfile>('urn:medical:own-profile:1.0.0#/$defs/Pending', JSON.parse(saved))
  revision(pending.revision)
  if (pending.actor !== actor) fail('CONTENT_MISMATCH', '原资料请求与当前账户不一致')
  return pending
}
export function clearOwnProfile(actor: string): void { sessionStorage.removeItem(profilePrefix + actor) }
async function profileSha256(fields: OwnProfileFields): Promise<string> {
  const encoded = new TextEncoder().encode(canonicalize(fields))
  const digest = await crypto.subtle.digest('SHA-256', encoded)
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}
export async function beginOwnProfile(account: OwnAccount, fields: OwnProfileFields): Promise<PendingOwnProfile> {
  validate('urn:medical:own-profile:1.0.0#/$defs/Request', { ...fields, user_id: account.user_id, expected_revision: account.revision })
  const sha256 = await profileSha256(fields)
  const existing = readPendingOwnProfile(account.user_id)
  if (existing !== null) {
    if (existing.sha256 !== sha256) fail('CONTENT_MISMATCH', '原资料请求需要本人重新输入完整原资料')
    return existing
  }
  const pending = { actor: account.user_id, key: crypto.randomUUID(), revision: revision(account.revision), sha256 }
  validate('urn:medical:own-profile:1.0.0#/$defs/Pending', pending)
  sessionStorage.setItem(profilePrefix + account.user_id, JSON.stringify(pending))
  return pending
}
export async function readFrameworkProfile(session: MedicalSession, account: OwnAccount, signal: AbortSignal): Promise<FrameworkProfile> {
  const response = await request.get('/api/system/user/profile', { medical: 'auth', signal, headers: { 'X-Schema-Version': '1.0.0' } })
  if (response.status !== 200) envelope(response)
  if (response.headers['cache-control'] !== 'no-store') fail('SCHEMA_INVALID', '本人资料响应禁止缓存')
  const result = validate<FrameworkProfile>('urn:medical:own-profile:1.0.0#/$defs/Profile', response.data)
  revision(result.account_revision)
  if (result.business_user_id !== session.user.user_id || result.business_user_id !== account.user_id
      || result.account_revision !== account.revision || result.data.userName !== account.username || result.data.nickName !== account.display_name) {
    fail('CONTEXT_CHANGED', '本人原资料与当前账户版本不一致，请重新读取会话')
  }
  return result
}
export async function changeOwnProfile(session: MedicalSession, account: OwnAccount, fields: OwnProfileFields,
  pending: PendingOwnProfile): Promise<AccountMutation> {
  if (pending.actor !== session.user.user_id || pending.actor !== account.user_id) fail('ACCESS_DENIED', '原资料请求没有当前本人身份')
  if (pending.sha256 !== await profileSha256(fields)) fail('CONTENT_MISMATCH', '原资料请求内容已经变化')
  const body = { ...fields, user_id: pending.actor, expected_revision: revision(pending.revision) }
  validate('urn:medical:own-profile:1.0.0#/$defs/Request', body)
  const value = envelope(await request.post('/api/local-account/profile-revisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<AccountMutation>('urn:medical:identity#/$defs/LocalAccountMutation', value.data)
  revision(result.account.revision); revision(result.account.credential_revision)
  if (result.account.user_id !== pending.actor || result.account.username !== account.username
      || result.account.display_name !== fields.nickName || result.account.platform_role !== account.platform_role
      || result.account.status !== 'ACTIVE' || result.reauthentication_required || value.resource_revision !== result.account.revision
      || result.account.revision !== (BigInt(pending.revision) + 1n).toString()) fail('CONTENT_MISMATCH', '原资料响应与当前本人请求不一致')
  return result
}

export function validPassword(value: string): boolean {
  const characters = Array.from(value)
  return characters.length >= 15 && new TextEncoder().encode(value).length <= 72
    && !characters.some(character => character.length === 1 && /[\uD800-\uDFFF]/u.test(character))
}

export function readPendingCredential(actor: string): PendingCredential | null {
  const saved = sessionStorage.getItem(pendingPrefix + actor)
  if (saved === null) return null
  const pending = validate<PendingCredential>('urn:medical:local-account-view:1.0.0#/$defs/Pending', JSON.parse(saved))
  revision(pending.revision)
  if (pending.actor !== actor || pending.revision === '0') fail('CONTENT_MISMATCH', '原密码请求与当前账户不一致')
  return pending
}

export function beginCredential(account: OwnAccount): PendingCredential {
  const existing = readPendingCredential(account.user_id)
  if (existing !== null) return existing
  const pending = { actor: account.user_id, key: crypto.randomUUID(), revision: revision(account.revision) }
  validate('urn:medical:local-account-view:1.0.0#/$defs/Pending', pending)
  // 仅登记原请求身份与公开版本，秘密输入始终保存在当前操作内存。
  sessionStorage.setItem(pendingPrefix + account.user_id, JSON.stringify(pending))
  return pending
}

export function clearCredential(actor: string): void { sessionStorage.removeItem(pendingPrefix + actor) }

export async function readOwnAccount(session: MedicalSession, signal: AbortSignal): Promise<OwnAccount> {
  const response = await request.get('/auth/account', { medical: 'auth', signal })
  if (response.status !== 200) envelope(response)
  if (response.headers['cache-control'] !== 'no-store') fail('SCHEMA_INVALID', '本人账户响应禁止缓存')
  const account = validate<OwnAccount>('urn:medical:local-account-view:1.0.0#/$defs/View', response.data)
  revision(account.revision)
  if (account.revision === '0' || account.user_id !== session.user.user_id || account.username !== session.user.username
      || account.display_name !== session.user.display_name || account.platform_role !== session.user.platform_role) {
    fail('CONTENT_MISMATCH', '本人账户元数据与当前真实会话不一致')
  }
  const value = envelope(await request.get('/api/session/identity-binding', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0' } }))
  const binding = validate<{ mode: 'local' | 'oidc'; user_id: string }>('urn:medical:identity-binding:1.0.0', value.data)
  if (value.resource_revision !== null || binding.mode !== 'local' || binding.user_id !== account.user_id) {
    fail('ACCESS_DENIED', '本人密码操作需要当前系统独立账户')
  }
  return account
}

export async function changeCredential(session: MedicalSession, account: OwnAccount, pending: PendingCredential,
  currentPassword: string, password: string): Promise<AccountMutation> {
  validate('urn:medical:local-account-view:1.0.0#/$defs/Pending', pending)
  if (pending.actor !== session.user.user_id || account.user_id !== pending.actor) fail('ACCESS_DENIED', '原密码请求没有当前本人身份')
  if (!validPassword(currentPassword) || !validPassword(password)) fail('SCHEMA_INVALID', '密码需要至少15个字符，UTF-8编码最多72个字节')
  if (revision(pending.revision) === '18446744073709551615') fail('REVISION_LIMIT', '账户版本已经达到上限')
  const body = { current_password: currentPassword, password, expected_revision: pending.revision }
  validate('urn:medical:identity#/$defs/LocalAccountCredential', body)
  const value = envelope(await request.post('/api/local-account/credential-revisions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token
  } }))
  const result = validate<AccountMutation>('urn:medical:identity#/$defs/LocalAccountMutation', value.data)
  revision(result.account.revision); revision(result.account.credential_revision)
  if (result.account.user_id !== pending.actor || result.account.username !== account.username || result.account.status !== 'ACTIVE'
      || !result.reauthentication_required || value.resource_revision !== result.account.revision
      || result.account.revision !== (BigInt(pending.revision) + 1n).toString()) {
    fail('CONTENT_MISMATCH', '密码变更原响应与本人及原版本不一致')
  }
  return result
}
