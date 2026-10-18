import { createHash, randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
export function simulationConfigurationFixture() {
  const schema = JSON.parse(readFileSync(new URL('../business/medical-persistence/src/main/resources/simulation-model-configuration.schema.json', import.meta.url), 'utf8'))
  const identity = Object.fromEntries(Object.entries(schema.$defs.Definition.properties).filter(([, value]) => 'const' in value).map(([key, value]) => [key, value.const]))
  const tenant = randomUUID(), actor = randomUUID(), project = randomUUID(), config = randomUUID(), fixed = randomUUID(), operationId = randomUUID()
  const session = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: null, platform_role: 'USER' },
    current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
    csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION-CSRF' }, memberships: [],
    action_summary: ['model.configure', 'configuration.read'].map(action => ({ action, resource_kind: 'ENTERPRISE', resource_id: tenant, project_id: null })),
    invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } }
  const body = { schema_version: '1.0.0', project_id: null, config_id: null, expected_revision: '0', based_on_revision: null, reason: 'SIMULATION 仅声明模拟配置草稿',
    definition: { ...identity, model_id: 'SIMULATION:declared-case', model_version: 'SIMULATION:version-1',
      case_binding: { case_id: randomUUID(), case_sha256: 'a'.repeat(64), validation: 'DECLARED_ONLY' },
      limits: { context_window: '128000', max_output_tokens: '8192', max_concurrency: '2', requests_per_minute: '10', tokens_per_minute: '200000', deadline_seconds: 60,
        temperature_enabled: true, reasoning_efforts: ['none', 'high'], stop_limit: 4 } } }
  const view = { schema_version: '1.0.0', tenant_id: tenant, project_id: null, config_id: config, model_config_revision: fixed, based_on_revision: null,
    definition: structuredClone(body.definition), content_sha256: 'd'.repeat(64), status: 'DRAFT', current_model_revision: fixed, revision: '1', created_by: actor,
    created_at: '2026-10-18T00:00:00Z', dependency_validation: 'DECLARED_SIMULATION_BINDING_ONLY', allowed_actions: ['configuration.read', 'model.configure'] }
  const operation = { operation_id: operationId, resource_id: fixed, status: 'COMPLETED', status_url: '/api/operations/' + operationId }
  const receipt = { schema_version: '1.0.0', operation_id: operationId, tenant_id: tenant, project_id: null, config_id: config, model_config_revision: fixed,
    content_sha256: view.content_sha256, status: 'DRAFT', revision: '1', current_context: { ...session.current_context, context_revision: '2', governance_epoch: '2' },
    execution_mode: 'SIMULATION', production_eligible: false }
  const listing = { schema_version: '1.0.0', tenant_id: tenant, current_context: session.current_context, items: [view], next_after: null }
  return { tenant, actor, project, config, fixed, session, body, view, operation, receipt, listing, schema, identity }
}
export const fileDigest = value => createHash('sha256').update(value).digest('hex')
