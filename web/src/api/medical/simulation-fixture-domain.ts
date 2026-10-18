import { canonicalizeEx } from 'json-canonicalize'
import { fail, schemaIssues, validate } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
export const fixtureSchema = 'urn:medical:simulation-execution:1.0.0#/$defs/'
export const inputSchema = 'urn:medical:simulation-input-artifact:1.0.0#/$defs/'
export interface Parameters { max_output_tokens: number; temperature: number | null; reasoning_effort: string | null; stop: string[]; purpose: 'EVALUATION' }
export interface FixtureRequest { schema_version: '1.0.0'; project_id: string | null; model_config_revision: string; case_source_version: string; case_rights_revision: string; input_source_version: string; input_rights_revision: string; request_parameters: Parameters }
export interface FixtureReceipt { schema_version: '1.0.0'; case_id: string; case_receipt_id: string; model_config_revision: string; case_sha256: string; input_sha256: string; input_data_kind: 'SIMULATION'; production_eligible: false }
export interface InputRequest { schema_version: '1.0.0'; project_id: string | null; model_config_revision: string; request_parameters: Parameters }
export interface InputReceipt { schema_version: '1.0.0'; input_data_kind: 'SIMULATION'; production_eligible: false; input_ref: { version_id: string; content_sha256: string }; source_binding: { source_id: string; source_version: string; rights_revision: string; case_id: string; model_config_revision: string } }
export interface PendingPreparation { actor: string; context: Context; stage: 'FIXTURE' | 'INPUT'; key: string; request: FixtureRequest; case_id: string; case_hash: string; input_source_id: string; raw_hash: string; digest: string }
export const canonicalFixture = (value: unknown) => canonicalizeEx(value, { strictUndefined: true })
export const fixtureIssues = (value: unknown) => schemaIssues(fixtureSchema + 'SimulationFixtureRegistrationRequest', value)
export function inputRequest(request: FixtureRequest): InputRequest { return { schema_version: '1.0.0', project_id: request.project_id, model_config_revision: request.model_config_revision, request_parameters: request.request_parameters } }
export function preparationAllowed(session: MedicalSession | null, action: string): boolean { return !!session?.current_context && session.action_summary.some(item => item.action === action) }
function identity(session: MedicalSession) { if (!session.current_context) fail('ACCESS_DENIED', '需要当前企业身份'); return 'medical.simulation.preparation:' + session.user.user_id + ':' + session.current_context.tenant_id }
export async function preparationDigest(p: Omit<PendingPreparation, 'digest'> | PendingPreparation): Promise<string> { const { digest: _ignored, ...value } = p as PendingPreparation; return sourceDigest(value) }
export async function verifyPending(session: MedicalSession, p: PendingPreparation, sameContext: boolean): Promise<void> {
  validate(fixtureSchema + 'SimulationFixtureRegistrationRequest', p.request)
  if (p.actor !== session.user.user_id || p.context.tenant_id !== session.current_context?.tenant_id || !['FIXTURE', 'INPUT'].includes(p.stage)
    || !/^[0-9a-f-]{36}$/.test(p.key) || !/^[0-9a-f-]{36}$/.test(p.case_id) || !/^[0-9a-f-]{36}$/.test(p.input_source_id)
    || !/^[a-f0-9]{64}$/.test(p.case_hash) || !/^[a-f0-9]{64}$/.test(p.raw_hash) || await preparationDigest(p) !== p.digest
    || sameContext && canonicalFixture(p.context) !== canonicalFixture(session.current_context)) fail('CONTENT_MISMATCH', '原登记身份、参数摘要或上下文不一致')
}
export function savePreparation(session: MedicalSession, p: PendingPreparation): void { sessionStorage.setItem(identity(session), JSON.stringify(p)) }
export function readPreparation(session: MedicalSession): PendingPreparation | null { const raw = sessionStorage.getItem(identity(session)); if (!raw) return null; try { return JSON.parse(raw) } catch { return fail('CONTENT_MISMATCH', '原登记恢复记录损坏') } }
export function clearPreparation(session: MedicalSession): void { sessionStorage.removeItem(identity(session)) }
export function verifyFixtureReceipt(p: PendingPreparation, r: FixtureReceipt): void {
  if (r.case_id !== p.case_id || r.model_config_revision !== p.request.model_config_revision || r.case_sha256 !== p.case_hash) fail('CONTENT_MISMATCH', '案例回执与固定配置不一致')
}
export function verifyInputReceipt(p: PendingPreparation, r: InputReceipt): void {
 const b=r.source_binding
 if(b.case_id!==p.case_id || b.model_config_revision!==p.request.model_config_revision || b.source_version!==p.request.input_source_version || b.rights_revision!==p.request.input_rights_revision || b.source_id!==p.input_source_id || r.input_ref.content_sha256!==p.raw_hash || r.input_ref.version_id===p.request.input_source_version) fail('CONTENT_MISMATCH','输入制品回执与原来源/原字节摘要不一致')
}
