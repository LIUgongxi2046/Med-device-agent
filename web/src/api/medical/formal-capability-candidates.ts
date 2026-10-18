import { readArtifactCatalogue } from './source-governance'
import type { ArtifactCatalogueItem } from './source-governance'
import { listProjects } from './projects'
import type { Project } from './protocol'
import { fail } from './protocol'
import { capabilityGuard } from './formal-capability-domain'
import type { CapabilitySessionReader } from './formal-capability-domain'

export interface CapabilityArtifactPage { items: ArtifactCatalogueItem[]; next_after: string | null }
/** Catalogue metadata is an actual artifact candidate, never proof of AGENT_PUBLICATION eligibility. */
export async function capabilityArtifactCandidates(reader: CapabilitySessionReader, project: string | null, after: string | null, signal: AbortSignal): Promise<CapabilityArtifactPage> {
  const guard = capabilityGuard(reader, signal)
  // Without a project filter the existing endpoint includes enterprise artifacts, needed by either scope.
  const page = await readArtifactCatalogue(guard.session, { limit: 50, ...(after ? { after } : {}) }, signal); guard.assert()
  return { items: page.items.filter(item => item.project_id === null || project !== null && item.project_id === project), next_after: page.next_after }
}
export async function capabilityProjectCandidates(reader: CapabilitySessionReader, after: string | null, signal: AbortSignal): Promise<{ projects: Project[]; next_after: string | null }> {
  const guard = capabilityGuard(reader, signal)
  const page = await listProjects(guard.context, after ? { after } : {}, signal); guard.assert()
  if (page.projects.some(item => item.tenant_id !== guard.context.tenant_id)) fail('CONTENT_MISMATCH', '项目候选不属于当前企业')
  return { projects: page.projects, next_after: page.next_after }
}
