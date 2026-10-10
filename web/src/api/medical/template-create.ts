import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'
import type { ReviewTemplate, TemplateContent } from './template-approval'
import { projectTextIssue } from './project-write'
import { readOperation } from './operations'
import type { Operation } from './operations'

export interface TemplateCreateBody {
  schema_version: '1.0.0'; base_version: Basis | null; schema_ref: 'urn:medical:templates:1.0.0#/$defs/Content'
  content: TemplateContent; reason: string
}
export interface TemplateCreated {
  schema_version: '1.0.0'; operation_id: string; template: ReviewTemplate & { status: 'DRAFT'; revision: '1' }; allowed_actions: []
}
export interface PendingTemplateCreate {
  actor: string; tenant_id: string; operation: 'TP-01'; key: string; context: Context; body_sha256: string; content_sha256: string
}
const prefix = 'medical.template.create:'
const schema = 'urn:medical:templates:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:template-create-view:1.0.0'

export function templateCreateAllowed(session: MedicalSession | null): boolean {
  return Boolean(session?.current_context && session.action_summary.some(item => item.action === 'template.write'
    && item.project_id === null && item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id))
}
function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '创建模板需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
function storageKey(session: MedicalSession): string { return prefix + session.user.user_id + ':' + current(session).tenant_id }

export function validateTemplateCreate(body: unknown): TemplateCreateBody {
  const value = validate<TemplateCreateBody>(schema + 'VersionInput', body)
  if (projectTextIssue(value.reason, 2000)) fail('SCHEMA_INVALID', '模板创建理由无效')
  for (const item of [...value.content.repeat_definitions, ...value.content.attachment_requirements]) {
    revision(item.min_count); if (item.max_count !== null) revision(item.max_count)
  }
  templateBases(value)
  return value
}
function templateBases(value: unknown): Basis[] {
  const refs = new Map<string, Basis>()
  function visit(item: unknown): void {
    if (item === null || typeof item !== 'object') return
    if (Array.isArray(item)) { item.forEach(visit); return }
    if (Object.hasOwn(item, 'version_id')) {
      const ref = validate<Basis>(schema + 'Basis', item); const prior = refs.get(ref.version_id)
      if (prior && prior.content_sha256 !== ref.content_sha256) fail('CONTENT_MISMATCH', '同一依据版本包含不同内容摘要')
      refs.set(ref.version_id, ref)
    }
    Object.values(item).forEach(visit)
  }
  visit(value)
  return [...refs.values()].sort((a, b) => a.version_id.localeCompare(b.version_id))
}
export function readPendingTemplateCreate(session: MedicalSession): PendingTemplateCreate | null {
  const saved = sessionStorage.getItem(storageKey(session)); if (saved === null) return null
  const value = validate<PendingTemplateCreate>(pendingSchema, JSON.parse(saved)); validContext(value.context)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id || value.context.tenant_id !== value.tenant_id) {
    fail('CONTENT_MISMATCH', '原模板创建请求与当前主体或企业不一致')
  }
  return value
}
export async function prepareTemplateCreate(session: MedicalSession, body: TemplateCreateBody): Promise<PendingTemplateCreate> {
  const context = current(session); validateTemplateCreate(body)
  if (!templateCreateAllowed(session)) fail('ACCESS_DENIED', '当前企业没有模板维护权限')
  const hash = await templateValueDigest(body); const saved = readPendingTemplateCreate(session)
  if (saved) {
    if (saved.body_sha256 !== hash || canonicalTemplateValue(saved.context) !== canonicalTemplateValue(context)) {
      fail('IDEMPOTENCY_CONFLICT', '原模板创建仍待核查，请保留完整原参数、上下文及请求标识')
    }
    return saved
  }
  const pending: PendingTemplateCreate = { actor: session.user.user_id, tenant_id: context.tenant_id, operation: 'TP-01',
    key: crypto.randomUUID(), context: { ...context }, body_sha256: hash, content_sha256: await templateValueDigest(body.content) }
  validate(pendingSchema, pending)
  // 受限正文、依据和理由仅保存在当前页面内存；浏览器保留原操作身份及摘要。
  sessionStorage.setItem(storageKey(session), JSON.stringify(pending)); return pending
}
export async function submitTemplateCreate(session: MedicalSession, pending: PendingTemplateCreate, body: TemplateCreateBody): Promise<TemplateCreated> {
  const context = current(session); validate(pendingSchema, pending); validateTemplateCreate(body)
  if (!templateCreateAllowed(session) || pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id
      || canonicalTemplateValue(context) !== canonicalTemplateValue(pending.context) || await templateValueDigest(body) !== pending.body_sha256
      || await templateValueDigest(body.content) !== pending.content_sha256) fail('CONTENT_MISMATCH', '模板创建请求与当前身份或原参数不一致')
  const raw = await request.post('/api/template-versions', body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key,
    [session.csrf.header_name]: session.csrf.token } })
  const response = envelope(raw); const result = validate<TemplateCreated>('urn:medical:template-operations:1.0.0#/$defs/Created', response.data)
  const stored = result.template
  if (raw.status !== 201 || response.resource_revision !== '1' || stored.tenant_id !== pending.tenant_id || stored.created_by !== pending.actor
      || revision(stored.authority_revision) === '0' || stored.content_sha256 !== pending.content_sha256
      || await templateValueDigest(stored.content) !== pending.content_sha256 || canonicalTemplateValue(stored.content) !== canonicalTemplateValue(body.content)
      || canonicalTemplateValue(stored.base_version) !== canonicalTemplateValue(body.base_version) || stored.reason !== body.reason
      || new Set(stored.dependency_refs.map(ref => ref.version_id)).size !== stored.dependency_refs.length
      || canonicalTemplateValue(templateBases(body)) !== canonicalTemplateValue([...stored.dependency_refs].sort((a, b) => a.version_id.localeCompare(b.version_id)))) {
    fail('CONTENT_MISMATCH', '模板创建回执与完整原定义、依据或主体不一致')
  }
  return result
}
export async function confirmTemplateCreate(session: MedicalSession, pending: PendingTemplateCreate, receipt: TemplateCreated): Promise<Operation> {
  validate(pendingSchema, pending); validate('urn:medical:template-operations:1.0.0#/$defs/Created', receipt)
  if (!templateCreateAllowed(session) || pending.actor !== session.user.user_id || pending.tenant_id !== current(session).tenant_id) fail('ACCESS_DENIED', '原模板操作需要当前本人及企业维护权限')
  if (receipt.template.created_by !== pending.actor || receipt.template.tenant_id !== pending.tenant_id
      || receipt.template.content_sha256 !== pending.content_sha256) fail('CONTENT_MISMATCH', '原模板创建回执与请求身份不一致')
  const operation = await readOperation(session, receipt.operation_id, new AbortController().signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== receipt.template.template_version) fail('CONTENT_MISMATCH', '模板创建操作未取得一致的完成结果')
  const saved = readPendingTemplateCreate(session)
  if (!saved || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256) fail('CONTENT_MISMATCH', '原模板创建请求需要核查')
  sessionStorage.removeItem(storageKey(session)); return operation
}
