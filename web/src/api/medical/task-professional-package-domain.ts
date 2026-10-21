import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import contract from '../../../../business/medical-persistence/src/main/resources/task-professional-package-authoring.schema.json'
import publication from '../../../../contracts/extensions/task-professional-package.catalogue.json'
import { canonical, detached, fieldTargetGuard } from './task-field-target-domain'
import { fail, validContext } from './protocol'
import type { MedicalSession, Context } from './protocol'
import { sourceDigest } from './content-digest'
import { projectJsonIssue } from './project-json'
import type { Kind, Preparation, Request, Status } from './task-professional-package-types'
export type * from './task-professional-package-types'
export { canonical, detached, fieldTargetGuard }
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true }); addFormats(ajv); ajv.addSchema(contract)
const packageHash = publication.schemas.find(v => v.name === 'task-professional-package')!.sha256
export const packageLabels = { PROMPT: '提示词来源包', ROLE: '角色来源包', PROFILE: '运行配置来源包' } as const
const definitions = { PROMPT: 'PromptPackage', ROLE: 'RolePackage', PROFILE: 'ProfilePackage' } as const
export const prerequisiteLabels = { MANAGED_ADAPTER_IMPORT_REQUIRED: '缺少经过管理的适配器导入记录，不能编造适配器引用。', PHYSICAL_PROFILE_DEPLOYMENT_REQUIRED: '配置文件仍需实际部署并核对。登记声明不会部署或运行软件。', INDEPENDENT_CAPABILITY_AUTHORING_REQUIRED: '来源文件登记后，还需单独创建正式能力声明。', INDEPENDENT_RELEASE_APPROVAL_REQUIRED: '正式运行前还需独立审核发布。' } as const
export function packageSchema(kind: Kind) { return { schema_ref: 'urn:medical:task-professional-package:1.0.0#/$defs/' + definitions[kind], schema_sha256: packageHash } }
export function checked<T>(name: string, value: unknown): T { const v = ajv.getSchema(contract.$id + '#/$defs/' + name); if (!v || !v(value)) fail('CONTENT_MISMATCH', '配置包接口合同不匹配：' + name); return value as T }
export function profilePathIssue(path: string): string {
  if (!path.startsWith('profiles/') || path.includes('\\') || /[\u0000-\u001f\u007f-\u009f%]/u.test(path) || path.split('/').some(p => !p || p === '.' || p === '..')) return '配置文件路径必须位于 profiles/ 下，不能含路径跳转、空层级或转义字符'
  return ''
}
export function verifyPackagePreparation(value: Preparation, project: string, context: Context): void {
  checked('Preparation', value)
  if (value.project_id !== project || value.context_revision !== context.context_revision) fail('CONTENT_MISMATCH', '配置包准备信息不属于当前项目或身份修订')
  const kinds = new Set(value.kinds.map(v => v.kind)); if (kinds.size !== 3) fail('CONTENT_MISMATCH', '来源包类型清单重复或不完整')
  for (const item of value.kinds) if (canonical(item.schema) !== canonical(packageSchema(item.kind))) fail('CONTENT_MISMATCH', '来源包必须使用实际发布的固定契约')
  for (const item of value.prompts) if (canonical(item.schema) !== canonical(packageSchema('PROMPT'))) fail('CONTENT_MISMATCH', '提示词候选没有对应的真实提示词来源包')
  for (const item of value.profiles) if (canonical(item.schema) !== canonical(packageSchema('PROFILE'))) fail('CONTENT_MISMATCH', '配置候选不是固定配置来源包')
  for (const [items, ids] of [[value.prompts, value.prompts.map(v => v.version.version_id)], [value.profiles, value.profiles.map(v => v.artifact.artifact_id)], [value.adapters, value.adapters.map(v => v.artifact.artifact_id)], [value.sources, value.sources.map(v => v.version.version_id)]] as const) if (items.length > value.maximum_choices || new Set(ids).size !== ids.length) fail('CONTENT_MISMATCH', '候选数量超限或身份重复')
}
export function packageIssue(body: Request, prep: Preparation): string {
  const v = ajv.getSchema(contract.$id + '#/$defs/Request'); if (!v || !v(body)) return '请完整填写所选类型的来源包内容和固定引用'
  if (projectJsonIssue(canonical(body))) return '来源包内容含有无效的Unicode或不支持的JSON值'
  if (body.preparation_sha256 !== prep.preparation_sha256 || !prep.kinds.some(k => k.kind === body.kind)) return '准备依据已变化，请重新读取后明确确认'
  if (new TextEncoder().encode(canonical(body.package)).length > prep.maximum_package_bytes) return '来源包内容超过当前大小限制'
  if (body.kind === 'PROMPT' && (/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u.test(body.package.system_instructions) || new TextEncoder().encode(body.package.system_instructions).length > 262144)) return '提示词正文不能为空白且最多262144个UTF-8字节'
  if (body.kind === 'PROFILE' && new TextEncoder().encode(body.package.system_prefix).length > 262144) return '系统前缀最多262144个UTF-8字节'
  if (new Set(body.content_sources.map(v => v.version_id)).size !== body.content_sources.length || body.content_sources.some(v => !prep.sources.some(s => canonical(s.version) === canonical(v)))) return '内容来源必须选择当前真实可用的固定版本，不能重复或伪造'
  if (body.kind === 'ROLE') {
    if (!prep.prompts.some(v => canonical(v.version) === canonical(body.package.prompt_basis))) return '请选择真实存在的提示词能力版本；登记不等于批准发布'
    if (!prep.profiles.some(v => canonical(v.artifact) === canonical(body.package.profile_artifact))) return '请选择实际登记的配置来源包'
  }
  if (body.kind === 'PROFILE') {
    if (!prep.adapters.some(v => canonical(v.artifact) === canonical(body.package.adapter_artifact))) return '请选择真实登记的适配器；缺少导入记录时不能手填替代引用'
    const paths = new Set<string>()
    for (const file of body.package.files) { const issue = profilePathIssue(file.relative); if (issue) return issue; if (paths.has(file.relative)) return '配置文件相对路径重复'; paths.add(file.relative) }
  }
  return ''
}
export interface PendingPackage { schema_version: '1.0.0'; actor: string; tenant_id: string; project_id: string; context: Context; key: string; body: Request; body_sha256: string; operation_id: string | null; request_sha256: string | null }
export const packageStorageKey = (s: MedicalSession, project: string) => ['medical-task-professional-package-v1', s.user.user_id, s.current_context?.tenant_id, project].join(':')
export function readPendingPackage(s: MedicalSession, project: string): PendingPackage | null {
  const raw = sessionStorage.getItem(packageStorageKey(s, project)); if (!raw) return null
  if (new TextEncoder().encode(raw).length > 2200000) fail('CONTENT_MISMATCH', '原登记请求超出本地恢复边界')
  let p: PendingPackage; try { p = JSON.parse(raw) } catch { return fail('CONTENT_MISMATCH', '原登记请求损坏，请保留并核查') }
  if (!p || p.schema_version !== '1.0.0' || p.actor !== s.user.user_id || p.tenant_id !== s.current_context?.tenant_id || p.project_id !== project || p.context?.tenant_id !== p.tenant_id) fail('CONTENT_MISMATCH', '原登记请求身份不一致')
  if (Object.keys(p).sort().join(',') !== ['schema_version','actor','tenant_id','project_id','context','key','body','body_sha256','operation_id','request_sha256'].sort().join(',') || Object.keys(p.context).sort().join(',') !== ['tenant_id','context_revision','auth_epoch','governance_epoch'].sort().join(',')) fail('CONTENT_MISMATCH', '原登记请求结构不完整或含有未知字段')
  validContext(p.context)
  checked('Request', p.body); checked('Key', p.key); checked('Hash', p.body_sha256); if (p.operation_id !== null) checked('Id', p.operation_id); if (p.request_sha256 !== null) checked('Hash', p.request_sha256)
  return p
}
export async function verifyPackageContent(status: Status, original: PendingPackage): Promise<void> {
  checked('Status', status)
  if (status.tenant_id !== original.tenant_id || status.project_id !== original.project_id || status.created_by !== original.actor) fail('CONTENT_MISMATCH', '来源包登记状态不属于原请求身份')
  if (status.state === 'NOT_FOUND') return
  if (status.kind !== original.body.kind || canonical(status.schema) !== canonical(packageSchema(original.body.kind)) || status.request_body_sha256 !== original.body_sha256 || original.operation_id !== null && status.operation_id !== original.operation_id) fail('CONTENT_MISMATCH', '来源包状态与原类型、完整请求或操作身份不一致')
  if (status.state === 'REGISTERED' && (!status.artifact || status.artifact.artifact_id !== status.target_id || status.artifact.sha256 !== await sourceDigest(original.body.package) || status.artifact.size_bytes !== String(new TextEncoder().encode(canonical(original.body.package)).length))) fail('CONTENT_MISMATCH', '实际登记的对象与原配置包内容、目标或字节数不一致')
}
