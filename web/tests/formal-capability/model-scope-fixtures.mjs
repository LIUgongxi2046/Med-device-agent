import { id, plain } from './helpers.mjs'
export const broad = (action, project = null) => ({ action, resource_kind: project ? 'PROJECT' : 'ENTERPRISE', project_id: project, resource_id: project || id(2) })
export function model(n = 40, project = null) {
 const basis = { version_id: id(80), content_sha256: 'a'.repeat(64) }
 return { schema_version: '1.0.0', tenant_id: id(2), project_id: project, config_id: id(n), model_config_revision: id(n + 100), current_model_revision: id(n + 100),
  based_on_revision: null, definition: { provider: 'Registered provider', base_url: 'https://provider.invalid/v1', region: 'region-1', model_id: 'Readable model ' + n,
   model_version: 'fixed-provider-version', credential_reference_id: id(70), limits: { context_window: '10000', max_output_tokens: '1000', max_concurrency: '2', requests_per_minute: '10', tokens_per_minute: '10000', deadline_seconds: 60, temperature_enabled: true, reasoning_efforts: ['none'], stop_limit: 4 },
   price_basis: basis, currency: 'CNY', data_policy: { allowed_classifications: ['INTERNAL'], allowed_regions: ['region-1'], allow_training: false, retention_days: '0', policy_basis: basis },
   request_parameter_schema_ref: 'urn:fixture:registered-request:1.0.0', request_parameter_schema_sha256: 'c'.repeat(64), model_protocol_revision: '1.0.0' },
  content_sha256: 'd'.repeat(64), status: 'DRAFT', revision: '1', created_by: id(1), created_at: '2026-10-19T00:00:00Z', dependency_validation: 'REGISTRY_REFERENCE_ONLY', allowed_actions: ['configuration.read'] }
}
export function modelResource(d, s, n = 40, project = null) {
 const m = model(n, project)
 return { actor: s.user.user_id, context: plain(s.current_context), authorization: d.capabilityAuthorization(s), label: `${m.definition.model_id} · DRAFT`,
  scope: { tenant_id: m.tenant_id, project_id: m.project_id, resource_kind: 'CONFIGURATION', resource_id: m.config_id },
  model: { config_id: m.config_id, model_config_revision: m.model_config_revision, content_sha256: m.content_sha256, revision: m.revision, status: m.status } }
}
