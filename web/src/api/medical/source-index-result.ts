import { Temporal } from '@js-temporal/polyfill'
import { fail, revision, validate } from './protocol'

export interface SourceIndexBuild {
  event_id: string; parse_version: string; index_version: string; mode: 'FULLTEXT'
  build_status: 'BUILDING' | 'READY' | 'FAILED'; index_status: 'BUILDING' | 'READY' | 'FAILED' | 'WITHDRAWN'
  delivery_status: 'PENDING' | 'DELIVERING' | 'DELIVERED' | 'FAILED'; expected_count: string; verified_count: string
  started_at: string; execution_deadline: string; updated_at: string; revision: string
  result: { status: 'READY' | 'FAILED'; expected_count: string; verified_count: string; failure_code: 'INDEX_LIMIT' | null } | null
}
export interface SourceIndexListing {
  schema_version: '1.0.0'; source_id: string; source_version: string; project_id: string
  total: string; listing_sha256: string; items: SourceIndexBuild[]
}
export function verifySourceIndexListing(value: SourceIndexListing, sourceId: string, sourceVersion: string, projectId: string): void {
  validate('urn:medical:source-index-view:1.0.0#/$defs/Listing', value); revision(value.total)
  if (value.source_id !== sourceId || value.source_version !== sourceVersion || value.project_id !== projectId
      || value.total !== String(value.items.length)) fail('CONTENT_MISMATCH', '索引记录与固定来源、项目或完整数量不一致')
  const indexes = new Set<string>()
  for (const item of value.items) {
    revision(item.revision); revision(item.expected_count); revision(item.verified_count)
    const started = Temporal.Instant.from(item.started_at), deadline = Temporal.Instant.from(item.execution_deadline)
    const updated = Temporal.Instant.from(item.updated_at)
    if (item.revision === '0' || item.expected_count === '0' || BigInt(item.verified_count) > BigInt(item.expected_count)
        || indexes.has(item.index_version) || Temporal.Instant.compare(deadline, started) <= 0 || Temporal.Instant.compare(updated, started) < 0) {
      fail('CONTENT_MISMATCH', '索引版本、数量、修订或执行时间不一致')
    }
    indexes.add(item.index_version)
    if (item.build_status === 'BUILDING') {
      if (item.result !== null || item.verified_count !== '0' || !['BUILDING', 'WITHDRAWN'].includes(item.index_status)
          || !['PENDING', 'DELIVERING'].includes(item.delivery_status)) fail('CONTENT_MISMATCH', '正在构建的索引缺少一致的等待状态')
    } else {
      if (!item.result || item.result.status !== item.build_status || item.result.expected_count !== item.expected_count
          || item.result.verified_count !== item.verified_count) fail('CONTENT_MISMATCH', '索引终态与完整处理回执不一致')
      if (item.build_status === 'READY') {
        if (item.expected_count !== item.verified_count || item.delivery_status !== 'DELIVERED' || item.result.failure_code !== null
            || !['READY', 'WITHDRAWN'].includes(item.index_status)) fail('CONTENT_MISMATCH', '索引完成数量或投递结果不一致')
      } else if (item.verified_count !== '0' || item.result.failure_code !== 'INDEX_LIMIT'
          || !['FAILED', 'WITHDRAWN'].includes(item.index_status) || !['FAILED', 'PENDING'].includes(item.delivery_status)) {
        fail('CONTENT_MISMATCH', '索引失败状态及原因不一致')
      }
    }
  }
}
