import request from '@/utils/request'
import { fail } from './protocol'
import type { MedicalSession } from './protocol'

export const nativeImageSimulationProvider = 'local-synthetic-image-v1' as const
export interface SimImage {
  source_version: string; rights_revision: string; object_ref: string; sha256: string
  mime: 'image/png' | 'image/jpeg'; width: number; height: number
  region: { x: number; y: number; width: number; height: number }; base64: string
}
export interface ImageSimulationProfile {
  schema_version: '1.0.0'; mode: 'SIM'; provider: typeof nativeImageSimulationProvider
  capability: 'NATIVE_IMAGE_TRANSPORT_SIM'; fixture: SimImage; synthetic: true
  formal_evidence_eligible: false; persistence: 'PROCESS_LOCAL_ONLY'
}
export interface ImageSimulationContext {
  schema_version: '1.0.0'; mode: 'SIM'; provider: typeof nativeImageSimulationProvider
  capability: 'NATIVE_IMAGE_TRANSPORT_SIM'; tenant_id: string; project_id: string
  session_id: string; request_id: string; question: string; images: SimImage[]; maximum_units: number
}
export interface ImageSimulationResponse {
  schema_version: '1.0.0'; mode: 'SIM'; synthetic: true; formal_evidence_eligible: false
  reserved_units: number; settled_units: number; data: Record<string, unknown>
}
const base = '/api/multimodal-qa/simulations'
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('SCHEMA_INVALID', '模拟接口响应不是对象')
  return value as Record<string, unknown>
}
function synthetic(value: Record<string, unknown>): void {
  if (value.schema_version !== '1.0.0' || value.mode !== 'SIM' || value.synthetic !== true || value.formal_evidence_eligible !== false)
    fail('CONTENT_MISMATCH', '模拟接口身份不匹配，不能作为正式结果')
}
function context(session: MedicalSession) {
  if (!session.current_context) fail('CONTEXT_CHANGED', '请先选择企业')
  return session.current_context
}
function checkedResponse(response: { status: number; headers: Record<string, unknown>; data: unknown }): Record<string, unknown> {
  if (response.status === 404) fail('DEPENDENCY_UNAVAILABLE', '图像模拟接口未启用；需要服务器显式开启')
  if (response.status < 200 || response.status >= 300) fail('DEPENDENCY_UNAVAILABLE', `图像模拟请求未完成（HTTP ${response.status}），请保留原请求并核查`)
  if (response.headers['x-schema-version'] !== '1.0.0') fail('CONTENT_MISMATCH', '模拟接口版本头不匹配')
  const value = object(response.data); synthetic(value); return value
}
export async function readImageSimulationProfile(session: MedicalSession, project: string, signal: AbortSignal): Promise<ImageSimulationProfile> {
  if (!uuid.test(project)) fail('SCHEMA_INVALID', '请选择有效项目')
  const value = checkedResponse(await request.get(`${base}/projects/${project}/profile`, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision } }))
  if (value.provider !== nativeImageSimulationProvider || value.capability !== 'NATIVE_IMAGE_TRANSPORT_SIM' || value.persistence !== 'PROCESS_LOCAL_ONLY')
    fail('CONTENT_MISMATCH', '未知模拟提供方或持久化策略')
  const image = object(value.fixture), region = object(image.region)
  if (typeof image.source_version !== 'string' || !uuid.test(image.source_version)
      || typeof image.rights_revision !== 'string' || !uuid.test(image.rights_revision)
      || typeof image.object_ref !== 'string' || !/^fixture\/[A-Za-z0-9._/-]{1,200}$/.test(image.object_ref) || image.object_ref.includes('..'))
    fail('SCHEMA_INVALID', '模拟图片来源或权限标识无效')
  if (!['image/png', 'image/jpeg'].includes(String(image.mime)) || typeof image.base64 !== 'string' || image.base64.length === 0 || image.base64.length > Math.ceil(4 * 1024 * 1024 / 3) * 4
      || !/^[A-Za-z0-9+/]*={0,2}$/.test(image.base64) || typeof image.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(image.sha256))
    fail('SCHEMA_INVALID', '模拟图片格式或摘要无效')
  for (const key of ['width', 'height']) if (!Number.isSafeInteger(image[key]) || Number(image[key]) <= 0) fail('SCHEMA_INVALID', '图片尺寸无效')
  for (const key of ['x', 'y', 'width', 'height']) if (!Number.isSafeInteger(region[key]) || Number(region[key]) < 0) fail('SCHEMA_INVALID', '图片区域无效')
  if (Number(image.width) * Number(image.height) > 16_000_000 || Number(region.width) <= 0 || Number(region.height) <= 0
      || Number(region.x) + Number(region.width) > Number(image.width) || Number(region.y) + Number(region.height) > Number(image.height))
    fail('SCHEMA_INVALID', '模拟图片尺寸或区域超出范围')
  let decoded: string
  try { decoded = atob(image.base64) } catch { fail('SCHEMA_INVALID', '模拟图片编码无效') }
  const bytes = Uint8Array.from(decoded, character => character.charCodeAt(0))
  if (bytes.length === 0 || bytes.length > 4 * 1024 * 1024) fail('SCHEMA_INVALID', '模拟图片字节数超出范围')
  const digest = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(byte => byte.toString(16).padStart(2, '0')).join('')
  signal.throwIfAborted()
  if (digest !== image.sha256) fail('CONTENT_MISMATCH', '模拟图片字节与摘要不一致')
  return value as unknown as ImageSimulationProfile
}
export function newImageSimulationContext(session: MedicalSession, project: string, question: string, profile: ImageSimulationProfile): ImageSimulationContext {
  if (!uuid.test(project) || !question.trim() || question.length > 8000) fail('SCHEMA_INVALID', '项目或问题无效')
  return { schema_version: '1.0.0', mode: 'SIM', provider: nativeImageSimulationProvider, capability: 'NATIVE_IMAGE_TRANSPORT_SIM',
    tenant_id: context(session).tenant_id, project_id: project, session_id: crypto.randomUUID(), request_id: crypto.randomUUID(),
    question, images: [JSON.parse(JSON.stringify(profile.fixture)) as SimImage], maximum_units: 100 }
}
export async function callImageSimulation(session: MedicalSession, operation: 'prepare' | 'dispatch' | 'recover' | 'review',
  body: ImageSimulationContext | { answer: ImageSimulationContext; reviewer: ImageSimulationContext }): Promise<ImageSimulationResponse> {
  const answer = 'answer' in body ? body.answer : body
  if (answer.tenant_id !== context(session).tenant_id) fail('CONTEXT_CHANGED', '当前企业与固定请求不一致')
  const value = checkedResponse(await request.post(`${base}/${operation}`, body, { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision,
    'Idempotency-Key': answer.request_id, [session.csrf.header_name]: session.csrf.token
  } }))
  if (![value.reserved_units, value.settled_units].every(v => Number.isSafeInteger(v) && Number(v) >= 0)) fail('SCHEMA_INVALID', '模拟预算计数无效')
  const data = object(value.data)
  if ((operation === 'dispatch' || operation === 'recover') && data.request_id !== undefined
      && (data.request_id !== answer.request_id || data.session_id !== answer.session_id || data.mode !== 'SIM' || data.synthetic !== true || data.formal_evidence_eligible !== false))
    fail('CONTENT_MISMATCH', '模拟回执与原请求不一致')
  if (operation === 'review' && data.verdict !== 'SYNTHETIC_TRANSPORT_VERIFIED_NOT_SEMANTIC_SUPPORT') fail('CONTENT_MISMATCH', '不能把模拟复核当作真实语义支持')
  return value as unknown as ImageSimulationResponse
}
