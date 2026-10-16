import { fail, revision, validate } from './protocol'
import type { SourceUploadResult, SourceUploadState, UploadJournal } from './source-uploads'

export function verifySourceUploadResult(value: SourceUploadResult, journal: UploadJournal, state: SourceUploadState): void {
  validate('urn:medical:source-upload-status:1.0.0#/$defs/View', value)
  revision(value.upload_revision); revision(value.received_size)
  if (value.upload_id !== journal.upload_id || value.upload_id !== state.registration.upload_id || value.upload_revision !== state.revision
      || value.received_size !== state.registration.received_size || BigInt(value.received_size) > BigInt(journal.size_bytes)
      || value.upload_status !== state.registration.upload_status || value.transfer_state !== state.registration.transfer_state) {
    fail('CONTENT_MISMATCH', '上传记录与处理结果在查询期间发生变化，请重新查询')
  }
  const { completion, source, attempt } = value
  if (source) {
    if (!completion || completion.operation_state !== 'COMPLETED' || source.source_id !== completion.source_id
        || source.source_version !== completion.source_version || source.original_sha256 !== journal.file_sha256) {
      fail('CONTENT_MISMATCH', '完成结果与固定来源版本或原文件摘要不一致')
    }
    const parses = new Set<string>(); const reviews = new Set<string>()
    for (const review of source.reviews) {
      validate('urn:medical:source-governance:1.0.0#/$defs/Review', review); revision(review.gate_revision)
      const identity = review.parse_version + ':' + review.index_version
      if (reviews.has(identity) || review.lifecycle !== source.lifecycle) fail('CONTENT_MISMATCH', '来源解析及索引组合重复或生命周期不一致')
      reviews.add(identity)
      parses.add(review.parse_version)
      for (const count of [review.expected_count, review.verified_count]) if (count !== null) revision(count)
    }
    if (completion.result_status === 'REGISTERED' && (!completion.parse_version || !parses.has(completion.parse_version))) {
      fail('CONTENT_MISMATCH', '登记结果缺少对应固定解析版本')
    }
  }
  if (attempt) {
    revision(attempt.revision)
    if (!completion || completion.result_status !== 'REGISTERED' || attempt.attempt_id !== completion.attempt_id || attempt.run_id !== completion.run_id) {
      fail('CONTENT_MISMATCH', '处理尝试与原登记结果不一致')
    }
  }
}
