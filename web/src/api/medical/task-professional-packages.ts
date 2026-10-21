import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import { envelope, fail, schemaIssues, MedicalFailure } from './protocol'
import type { MedicalSession, ApiError } from './protocol'
import { capabilityAllowed } from './formal-capability-domain'
import { byteDigest, sourceDigest } from './content-digest'
import { canonical, detached, fieldTargetGuard, checked, verifyPackagePreparation, packageIssue, readPendingPackage, packageStorageKey, verifyPackageContent } from './task-professional-package-domain'
import type { PendingPackage, Preparation, Request, Status } from './task-professional-package-domain'
export * from './task-professional-package-domain'
type Reader = () => MedicalSession
const base = (project: string) => { checked('Id', project); return '/api/projects/' + project }
const headers = (s: MedicalSession) => ({ 'X-Schema-Version': '1.0.0', 'X-Context-Revision': s.current_context!.context_revision })
const rawDigest = (s: string) => byteDigest(new TextEncoder().encode(s))
export async function readPackagePreparation(reader: Reader, project: string, signal: AbortSignal): Promise<Preparation> {
  const g = fieldTargetGuard(reader, signal), raw = await request.get(base(project) + '/task-professional-package-preparation', { medical: 'envelope', signal, headers: headers(g.session) }); g.assert()
  const e = envelope(raw), value = checked<Preparation>('Preparation', e.data); verifyPackagePreparation(value, project, g.context)
  if (raw.status !== 200 || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '准备信息没有返回确切读取状态')
  return value
}
async function assertOriginal(reader: Reader, p: PendingPackage, signal: AbortSignal) {
  const g = fieldTargetGuard(reader, signal)
  if (canonical(readPendingPackage(g.session, p.project_id)) !== canonical(p) || await sourceDigest(p.body) !== p.body_sha256) fail('CONTENT_MISMATCH', '原登记请求的完整内容或身份不一致')
  g.assert()
}
async function verifyStatus(reader: Reader, p: PendingPackage, raw: AxiosResponse<unknown>, signal: AbortSignal, mode: 'CREATE' | 'READ' | 'RECOVER'): Promise<Status> {
  const g = fieldTargetGuard(reader, signal), e = envelope(raw), value = checked<Status>('Status', e.data)
  await verifyPackageContent(value, p); g.assert()
  if (value.state === 'NOT_FOUND') {
    if (mode !== 'RECOVER' || raw.status !== 200 || e.resource_revision !== '1' || p.operation_id !== null || p.request_sha256 !== null) fail('CONTENT_MISMATCH', '原登记操作不能消失或被读取结果替代')
    return value
  }
  if (value.original_key_sha256 !== await rawDigest(p.key) || !value.request_sha256 || p.request_sha256 !== null && value.request_sha256 !== p.request_sha256) fail('CONTENT_MISMATCH', '原登记键或原作用域摘要不一致'); g.assert()
  const expectedStatus = value.state === 'PROCESSING' ? 202 : mode === 'CREATE' ? 201 : 200
  if (raw.status !== expectedStatus || (value.state === 'PROCESSING' ? !['1', '2'].includes(e.resource_revision ?? '') : e.resource_revision !== '3')) fail('CONTENT_MISMATCH', '登记状态与实际操作修订不一致')
  return value
}
async function observe(reader: Reader, p: PendingPackage, status: Status, signal: AbortSignal): Promise<PendingPackage> {
  const g = fieldTargetGuard(reader, signal); await assertOriginal(reader, p, signal); g.assert()
  if (status.state === 'NOT_FOUND') return p
  const updated = detached({ ...p, operation_id: status.operation_id, request_sha256: status.request_sha256 })
  const key = packageStorageKey(g.session, p.project_id), saved = canonical(updated)
  sessionStorage.setItem(key, saved); if (sessionStorage.getItem(key) !== saved) fail('CONTENT_MISMATCH', '原登记操作身份未能完整保存，请保留原请求并核查')
  g.assert(); return updated
}
async function readOriginalStatus(reader: Reader, p: PendingPackage, signal: AbortSignal): Promise<Status> {
  if (!p.operation_id) fail('CONTENT_MISMATCH', '缺少已核实的原操作身份')
  const g = fieldTargetGuard(reader, signal), raw = await request.get(base(p.project_id) + '/task-professional-packages/' + p.operation_id, { medical: 'envelope', signal, headers: headers(g.session) }); g.assert()
  return verifyStatus(reader, p, raw, signal, 'READ')
}
async function finish(reader: Reader, p: PendingPackage, status: Status, signal: AbortSignal, onPending: (p: PendingPackage | null) => void): Promise<Status> {
  const g = fieldTargetGuard(reader, signal), original = await observe(reader, p, status, signal); g.assert(); onPending(detached(original)); g.assert()
  if (status.state !== 'REGISTERED') return status
  const fixed = await readOriginalStatus(reader, original, signal); g.assert()
  if (canonical(fixed) !== canonical(status)) fail('CONTENT_MISMATCH', '登记回执与原操作固定读回不一致')
  await assertOriginal(reader, original, signal); g.assert()
  const active = packageStorageKey(g.session, p.project_id); sessionStorage.removeItem(active)
  if (sessionStorage.getItem(active) !== null) fail('CONTENT_MISMATCH', '已核实登记但原请求未能清除，请重新读取并核查')
  return fixed
}
/** Only a fresh first dispatch may release the original using an exact pre-intent rejection proof. */
async function archivePreIntentRejection(reader: Reader, p: PendingPackage, wire: string, raw: AxiosResponse<unknown>, signal: AbortSignal): Promise<boolean> {
  const h = raw.headers, e = raw.data as ApiError
  if (![403, 409, 422].includes(raw.status) || h['x-task-package-outcome'] !== 'REJECTED_PRE_INTENT') return false
  if (h['cache-control'] !== 'no-store' || h['x-schema-version'] !== '1.0.0' || schemaIssues('urn:medical:shared#/$defs/Error', e).length || h['x-request-id'] !== e.request_id || !['SCHEMA_INVALID', 'ACCESS_DENIED', 'REVISION_CONFLICT', 'SCOPE_STALE'].includes(e.code)
      || e.operation_id !== null || h['x-task-package-project'] !== p.project_id || h['x-task-package-operation'] !== 'TASK-PROFESSIONAL-PACKAGE' || h['x-task-package-context-revision'] !== p.context.context_revision || p.operation_id !== null || p.request_sha256 !== null) return false
  const g = fieldTargetGuard(reader, signal), keyHash = await rawDigest(p.key); g.assert(); const wireHash = await rawDigest(wire); g.assert()
  if (h['x-task-package-key-sha256'] !== keyHash || h['x-task-package-wire-sha256'] !== wireHash) return false
  await assertOriginal(reader, p, signal); g.assert()
  const active = packageStorageKey(g.session, p.project_id), archive = active + ':rejected:' + p.key
  const record = canonical({ original: p, first_dispatch_only: true, outcome: 'REJECTED_PRE_INTENT', http_status: raw.status, request_id: e.request_id, error_code: e.code, wire_sha256: wireHash })
  sessionStorage.setItem(archive, record); if (sessionStorage.getItem(archive) !== record) fail('CONTENT_MISMATCH', '拒绝回执未能完整保存，原登记请求仍保留')
  g.assert(); sessionStorage.removeItem(active)
  if (sessionStorage.getItem(active) !== null) fail('CONTENT_MISMATCH', '原登记请求未能解除，请重新读取并核查')
  return true
}
export async function createPackage(reader: Reader, project: string, proposed: Request, signal: AbortSignal, onPending: (p: PendingPackage | null) => void): Promise<Status> {
  const g = fieldTargetGuard(reader, signal), body = detached(proposed); checked('Request', body)
  if (!capabilityAllowed(g.session, project)) fail('ACCESS_DENIED', '缺少当前项目的正式能力配置权限')
  if (readPendingPackage(g.session, project)) fail('IDEMPOTENCY_CONFLICT', '请先核查原登记请求，不能创建替代写入')
  const prep = await readPackagePreparation(reader, project, signal); g.assert(); const issue = packageIssue(body, prep); if (issue) fail('SCHEMA_INVALID', issue)
  const wire = canonical(body), bodyHash = await sourceDigest(body); g.assert()
  const p: PendingPackage = { schema_version: '1.0.0', actor: g.session.user.user_id, tenant_id: g.context.tenant_id, project_id: project, context: g.context, key: crypto.randomUUID(), body, body_sha256: bodyHash, operation_id: null, request_sha256: null }
  if (readPendingPackage(g.session, project)) fail('IDEMPOTENCY_CONFLICT', '已有原登记请求待核查')
  const saved = canonical(p); if (new TextEncoder().encode(saved).length > 2200000) fail('SCHEMA_INVALID', '完整原请求超过本地恢复上限，未发送写入')
  const active = packageStorageKey(g.session, project)
  sessionStorage.setItem(active, saved); if (sessionStorage.getItem(active) !== saved) fail('CONTENT_MISMATCH', '完整原请求未能保存，未发送写入')
  g.assert(); onPending(detached(p)); g.assert()
  const raw = await request.post(base(project) + '/task-professional-packages', wire, { medical: 'envelope', signal, headers: { ...headers(g.session), 'Idempotency-Key': p.key, [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert()
  const rejected = await archivePreIntentRejection(reader, p, wire, raw, signal); g.assert()
  if (rejected) {
    onPending(null); g.assert()
    const error = raw.data as ApiError; throw new MedicalFailure(error.code, error.message, error.request_id)
  }
  const status = await verifyStatus(reader, p, raw, signal, 'CREATE'); g.assert(); return finish(reader, p, status, signal, onPending)
}
export async function recoverPackage(reader: Reader, p: PendingPackage, signal: AbortSignal, onPending: (p: PendingPackage | null) => void): Promise<Status> {
  const g = fieldTargetGuard(reader, signal); await assertOriginal(reader, p, signal); g.assert()
  const body = { schema_version: '1.0.0', original_key: p.key, original_request: detached(p.body) }; checked('Recovery', body)
  const raw = await request.post(base(p.project_id) + '/task-professional-package-recoveries', canonical(body), { medical: 'envelope', signal, headers: { ...headers(g.session), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert()
  const status = await verifyStatus(reader, p, raw, signal, 'RECOVER'); g.assert(); return finish(reader, p, status, signal, onPending)
}
