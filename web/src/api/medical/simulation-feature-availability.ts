import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import request from '@/utils/request';
import schema from '../../../../business/medical-persistence/src/main/resources/simulation-feature-availability.schema.json';
import { envelope, fail, validContext } from './protocol';
import type { Context, MedicalSession } from './protocol';
export type SimulationFeature = 'input_artifact_registration' | 'rule_workflow' | 'evaluation_preparation';
export interface SimulationFeatureAvailability {
    schema_version: '1.0.0';
    current_context: Context;
    features: Record<SimulationFeature, boolean>;
}
const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const validate = ajv.compile<SimulationFeatureAvailability>(schema);
/** Availability is deployment state only. Existing operation permission checks remain mandatory. */
export async function readSimulationFeatureAvailability(session: MedicalSession, signal: AbortSignal): Promise<SimulationFeatureAvailability> {
    const current = session.current_context;
    if (!current)
        fail('ACCESS_DENIED', '功能可用性需要当前企业身份');
    const context = { ...current };
    validContext(context);
    const raw = await request.get('/api/simulation-feature-availability', {
        medical: 'envelope', signal,
        headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
    });
    const result = envelope(raw);
    if (!validate(result.data))
        fail('SCHEMA_INVALID', '功能可用性响应不符合固定合同');
    const value = result.data;
    validContext(value.current_context);
    if (raw.status !== 200 || result.resource_revision !== context.context_revision
        || value.current_context.tenant_id !== context.tenant_id
        || value.current_context.context_revision !== context.context_revision
        || value.current_context.auth_epoch !== context.auth_epoch
        || value.current_context.governance_epoch !== context.governance_epoch) {
        fail('CONTENT_MISMATCH', '功能可用性与当前企业身份不一致');
    }
    return value;
}
