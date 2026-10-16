import request from '@/utils/request'
import { envelope, fail, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { sourceDigest } from './source-uploads'
import { verifySourceIndexListing } from './source-index-result'
import type { SourceIndexListing } from './source-index-result'

export async function readSourceIndexBuilds(session: MedicalSession, sourceId: string, sourceVersion: string, projectId: string,
    signal: AbortSignal): Promise<SourceIndexListing> {
  signal.throwIfAborted()
  if (!session.current_context) fail('ACCESS_DENIED', '读取索引记录需要当前企业身份')
  validContext(session.current_context)
  for (const id of [sourceId, sourceVersion, projectId]) validate('urn:medical:templates:1.0.0#/$defs/Id', id)
  const raw = await request.get('/api/source-versions/' + sourceVersion + '/index-builds', { medical: 'envelope', signal,
    params: { project_id: projectId }, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision } })
  const response = envelope(raw); const value = validate<SourceIndexListing>('urn:medical:source-index-view:1.0.0#/$defs/Listing', response.data)
  if (raw.status !== 200 || response.resource_revision !== null) fail('CONTENT_MISMATCH', '完整索引记录缺少正确读取回执')
  verifySourceIndexListing(value, sourceId, sourceVersion, projectId)
  const digest = await sourceDigest({ source_version: value.source_version, project_id: value.project_id, items: value.items })
  signal.throwIfAborted()
  if (digest !== value.listing_sha256) fail('CONTENT_MISMATCH', '索引记录清单摘要不一致')
  return value
}
