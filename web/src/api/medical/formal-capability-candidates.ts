import { listModelConfigurations } from './model-configurations'
import { readArtifactCatalogue, readSourceCatalogue } from './source-governance'
import type { ArtifactCatalogueItem } from './source-governance'
import { listProjects } from './projects'
import type { Project } from './protocol'
import { fail } from './protocol'
import { capabilityAuthorization, capabilityGuard, detached } from './formal-capability-domain'
import type { CapabilitySessionReader, CapabilityResourceCandidate } from './formal-capability-domain'

export interface CapabilityArtifactPage { items: ArtifactCatalogueItem[]; next_after: string | null }
/** Catalogue metadata is an actual artifact candidate, never proof of AGENT_PUBLICATION eligibility. */
export async function capabilityArtifactCandidates(reader: CapabilitySessionReader, project: string | null, after: string | null, signal: AbortSignal): Promise<CapabilityArtifactPage> {
  const guard = capabilityGuard(reader, signal)
  // Without a project filter the existing endpoint includes enterprise artifacts, needed by either scope.
  const page = await readArtifactCatalogue(guard.session, { limit: 50, ...(after ? { after } : {}) }, signal); guard.assert()
  return { items: page.items.filter(item => item.project_id === null || project !== null && item.project_id === project), next_after: page.next_after }
}
export async function capabilityProjectCandidates(reader: CapabilitySessionReader, after: string | null, signal: AbortSignal): Promise<{ projects: Project[]; next_after: string | null; resources: CapabilityResourceCandidate[] }> {
  const guard = capabilityGuard(reader, signal)
  const page = await listProjects(guard.context, after ? { after } : {}, signal); guard.assert()
  if (page.projects.some(item => item.tenant_id !== guard.context.tenant_id)) fail('CONTENT_MISMATCH', '项目候选不属于当前企业')
  return { projects: page.projects, next_after: page.next_after, resources: page.projects.map(item => ({
    actor: guard.session.user.user_id, context: detached(guard.context), label: item.name,
    scope: { tenant_id: item.tenant_id, project_id: item.project_id, resource_kind: 'PROJECT', resource_id: item.project_id }
  })) }
}

/** Source IDs and ownership come from the tenant-scoped catalogue, never from version IDs or input text. */
export async function capabilitySourceCandidates(reader: CapabilitySessionReader, project: string | null,
    after: string | null, signal: AbortSignal): Promise<{ resources: CapabilityResourceCandidate[]; next_after: string | null }> {
  const guard = capabilityGuard(reader, signal)
  const page = await readSourceCatalogue(guard.session, { limit: 50, ...(after ? { after } : {}),
    ...(project === null ? { ownership: 'ENTERPRISE' as const } : { project_id: project }) }, signal)
  guard.assert()
  if (after !== null && page.next_after === after) fail('CONTENT_MISMATCH', '资料候选分页没有前进，请重新读取')
  const resources: CapabilityResourceCandidate[] = [], owners = new Map<string, string | null>()
  for (const item of page.items) {
    if (item.ownership === 'SESSION') continue
    if (item.ownership === 'ENTERPRISE' ? item.owner_project_id !== null : item.owner_project_id === null)
      fail('CONTENT_MISMATCH', '资料候选的归属身份不一致')
    if (owners.has(item.source_id) && owners.get(item.source_id) !== item.owner_project_id)
      fail('CONTENT_MISMATCH', '同一资料的归属身份发生变化，请重新读取')
    owners.set(item.source_id, item.owner_project_id)
    if (item.owner_project_id !== null && item.owner_project_id !== project) continue
    const scope = { tenant_id: guard.context.tenant_id, project_id: item.owner_project_id,
      resource_kind: 'SOURCE', resource_id: item.source_id }
    const prior = resources.find(value => value.scope.resource_id === item.source_id)
    if (!prior) resources.push({ actor: guard.session.user.user_id, context: detached(guard.context), scope, label: item.title })
  }
  return { resources, next_after: page.next_after }
}

/** Keep all readable identities for cross-page conflict checks; the domain filters actual ownership for the form. */
export async function capabilityModelCandidates(reader: CapabilitySessionReader, after: string | null,
    signal: AbortSignal): Promise<{ resources: CapabilityResourceCandidate[]; next_after: string | null }> {
  const guard = capabilityGuard(reader, signal), authorization = capabilityAuthorization(guard.session)
  const page = await listModelConfigurations(guard.session, { limit: 50, ...(after ? { after } : {}) }, signal)
  guard.assert()
  if (after !== null && page.next_after === after) fail('CONTENT_MISMATCH', '模型配置候选分页没有前进，请重新读取')
  return { resources: page.items.map(item => ({
    actor: guard.session.user.user_id, context: detached(guard.context), authorization,
    scope: { tenant_id: item.tenant_id, project_id: item.project_id, resource_kind: 'CONFIGURATION', resource_id: item.config_id },
    label: [item.definition.provider, item.definition.model_id, item.definition.model_version, item.status,
      item.project_id === null ? '企业归属' : '项目归属'].join(' · '),
    model: { config_id: item.config_id, model_config_revision: item.model_config_revision,
      content_sha256: item.content_sha256, revision: item.revision, status: item.status }
  })), next_after: page.next_after }
}
