import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext, validSession } from './protocol'
import type { Context, MedicalSession, SelectionOptions } from './protocol'

export async function readSession(signal: AbortSignal): Promise<MedicalSession> {
  const value = envelope(await request.get('/api/session', { medical: 'envelope', signal, timeout: 60000, headers: { 'X-Schema-Version': '1.0.0' } }))
  const session = validate<MedicalSession>('urn:medical:identity', value.data)
  validSession(session, value.resource_revision)
  return session
}

export async function readOptions(signal: AbortSignal): Promise<SelectionOptions> {
  const value = envelope(await request.get('/api/session/tenant-selection-options', { medical: 'envelope', signal, timeout: 60000, headers: { 'X-Schema-Version': '1.0.0' } }))
  const options = validate<SelectionOptions>('urn:medical:selection-options', value.data)
  revision(options.expected_context_revision)
  if (value.resource_revision !== options.expected_context_revision || (options.selected_tenant_id === null) !== (options.expected_context_revision === '0')) {
    fail('SCHEMA_INVALID', '企业选择候选的上下文版本无效')
  }
  return options
}

export async function readSessionCsrf(signal: AbortSignal): Promise<MedicalSession['csrf']> {
  const response = await request.get('/auth/csrf', { medical: 'auth', signal })
  const csrf: unknown = response.data
  if (csrf === null || typeof csrf !== 'object' || Object.keys(csrf).sort().join(',') !== 'header_name,parameter_name,token'
      || !('header_name' in csrf) || csrf.header_name !== 'X-CSRF-TOKEN' || !('parameter_name' in csrf) || csrf.parameter_name !== '_csrf'
      || !('token' in csrf) || typeof csrf.token !== 'string' || csrf.token.length === 0 || response.headers['cache-control'] !== 'no-store') {
    fail('SCHEMA_INVALID', '账户安全会话响应无效')
  }
  return { header_name: csrf.header_name, token: csrf.token }
}

export async function loginAccount(username: string, password: string): Promise<void> {
  const csrf = await readSessionCsrf(new AbortController().signal)
  const validator = new URLSearchParams()
  validator.set('username', username); validator.set('password', password); validator.set('_csrf', csrf.token)
  // 密码校验包含 BCrypt 运算，独立设置认证等待时间以覆盖本机服务繁忙时的处理。
  const logged = await request.post('/auth/login', validator, { medical: 'auth', timeout: 60000, headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
  if (logged.status !== 200) envelope(logged)
  const target = new URL(logged.request.responseURL)
  if (target.origin !== location.origin || target.pathname !== '/local-auth/account.html') fail('AUTHENTICATION_FAILED', '账户登录未取得明确结果')
}

export async function logoutAccount(session: MedicalSession): Promise<void> {
  const response = await request.post('/auth/logout', null, { medical: 'auth', headers: { [session.csrf.header_name]: session.csrf.token } })
  if (response.status !== 204 || response.headers['cache-control'] !== 'no-store') {
    if (response.status >= 400) envelope(response)
    fail('SCHEMA_INVALID', '账户注销未取得明确结果')
  }
}

export async function selectEnterprise(session: { user: { user_id: string }; csrf: MedicalSession['csrf'] }, options: SelectionOptions, target: string, key: string, expectedRevision: string): Promise<Context> {
  if (session.user.user_id !== options.user_id || !options.memberships.some(member => member.tenant_id === target && member.status === 'ACTIVE')) {
    fail('ACCESS_DENIED', '当前账户没有目标企业的有效成员身份')
  }
  const body = { tenant_id: target, expected_context_revision: revision(expectedRevision) }
  validate('urn:medical:identity#/$defs/SelectionRequest', body)
  if (revision(body.expected_context_revision) === '18446744073709551615') fail('REVISION_LIMIT', '企业上下文版本已经达到上限')
  const value = envelope(await request.post('/api/session/tenant-selections', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': body.expected_context_revision, 'Idempotency-Key': key,
    [session.csrf.header_name]: session.csrf.token
  } }))
  const receipt = validate<{ schema_version: string; operation_id: string; current_context: Context }>('urn:medical:identity#/$defs/SelectionReceipt', value.data)
  validContext(receipt.current_context)
  if (receipt.current_context.tenant_id !== target || receipt.current_context.context_revision !== (BigInt(body.expected_context_revision) + 1n).toString()
      || value.resource_revision !== receipt.current_context.context_revision) fail('SCHEMA_INVALID', '企业选择响应与原目标不一致')
  return receipt.current_context
}
