import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: uploads } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/source-upload-result.ts', import.meta.url)), { root, mode: 'medical' })
const upload = randomUUID(), source = randomUUID(), version = randomUUID(), parse = randomUUID(), rights = randomUUID()
const operation = randomUUID(), attempt = randomUUID(), run = randomUUID(), hash = 'a'.repeat(64)
const journal = { upload_id: upload, file_sha256: hash, size_bytes: '18446744073709551615' }
const state = { revision: '18446744073709551615', registration: { upload_id: upload, received_size: journal.size_bytes,
  upload_status: 'RECEIVED', transfer_state: 'COMPLETED' } }
const review = { parse_version: parse, parse_status: 'COMPLETE', index_version: null, index_mode: null, index_status: null,
  expected_count: null, verified_count: null, approval_revision: null, approval_status: 'PENDING', upload_status: 'RECEIVED',
  lifecycle: 'ACTIVE', gate_revision: '1', current_decision: null, reviewed_manifest_sha256: null, decided_at: null,
  review_manifest: {}, review_manifest_sha256: hash }
const registered = { schema_version: '1.0.0', upload_id: upload, upload_revision: state.revision, received_size: journal.size_bytes,
  upload_status: 'RECEIVED', transfer_state: 'COMPLETED', completion: { operation_id: operation, operation_state: 'COMPLETED',
    result_status: 'REGISTERED', source_id: source, source_version: version, registered_rights_revision: rights, parse_version: parse,
    attempt_id: attempt, run_id: run, error_code: null, reason: null },
  source: { source_id: source, source_version: version, original_sha256: hash, current_rights: { version_id: randomUUID(), content_sha256: hash },
    rights_status: 'PENDING_REVIEW', lifecycle: 'ACTIVE', reviews: [review] },
  attempt: { attempt_id: attempt, run_id: run, config_revision: randomUUID(), status: 'SUCCEEDED', revision: '1',
    updated_at: '2026-10-14T01:02:03.123456Z', failure_manifest: { schema_version: '1.0.0', issues: [] } } }
const verify = value => uploads.verifySourceUploadResult(value, journal, state)
verify(registered)
assert.notEqual(registered.source.current_rights.version_id, registered.completion.registered_rights_revision)
const checks = ['full_registered_result_keeps_unsigned_precision_and_independent_current_rights']
const indexed = structuredClone(registered)
indexed.source.reviews = [
  { ...review, index_version: randomUUID(), index_mode: 'FULLTEXT', index_status: 'READY', expected_count: '1', verified_count: '1' },
  { ...review, index_version: randomUUID(), index_mode: 'FULLTEXT', index_status: 'PENDING', expected_count: '1', verified_count: '0' }
]
verify(indexed)
checks.push('all_fixed_indexes_for_same_parse_remain_independent')
const duplicate = structuredClone(registered)
Object.assign(duplicate.completion, { result_status: 'DUPLICATE', parse_version: null, attempt_id: null, run_id: null })
duplicate.attempt = null; verify(duplicate)
assert.throws(() => verify({ ...duplicate, attempt: registered.attempt }))
checks.push('duplicate_has_fixed_source_without_new_processing_attempt')
for (const field of ['source_version', 'source_id', 'original_sha256']) {
  const value = structuredClone(registered); value.source[field] = field === 'original_sha256' ? 'b'.repeat(64) : randomUUID()
  assert.throws(() => verify(value), error => error.code === 'CONTENT_MISMATCH')
}
checks.push('mismatched_source_identity_and_original_content_rejected')
for (const field of ['upload_id', 'upload_revision', 'received_size', 'upload_status', 'transfer_state']) {
  const value = structuredClone(registered)
  value[field] = { upload_id: randomUUID(), upload_revision: '1', received_size: '0', upload_status: 'FAILED', transfer_state: 'FAILED' }[field]
  assert.throws(() => verify(value), error => error.code === 'CONTENT_MISMATCH')
}
checks.push('changed_registration_snapshot_requires_fresh_query')
for (const field of ['attempt_id', 'run_id']) {
  const value = structuredClone(registered); value.attempt[field] = randomUUID()
  assert.throws(() => verify(value), error => error.code === 'CONTENT_MISMATCH')
}
checks.push('processing_attempt_and_original_run_identity_required')
for (const mutation of [value => value.source.reviews.push(review), value => value.source.reviews[0].parse_version = randomUUID(),
  value => value.source.reviews[0].lifecycle = 'RECYCLED', value => value.source.reviews[0].gate_revision = '18446744073709551616']) {
  const value = structuredClone(registered); mutation(value)
  assert.throws(() => verify(value))
}
checks.push('duplicate_parse_missing_registered_parse_and_invalid_review_rejected')
for (const mutation of [value => value.upload_revision = 9007199254740993, value => value.unpublished_field = true,
  value => value.completion.parse_version = null, value => value.attempt.updated_at = '2026-10-14']) {
  const value = structuredClone(registered); mutation(value)
  assert.throws(() => verify(value), error => error.code === 'SCHEMA_INVALID')
}
checks.push('unknown_fields_invalid_time_and_incomplete_registered_result_rejected')
for (const operationState of ['PROCESSING', 'FAILED']) {
  const value = structuredClone(registered)
  Object.assign(value, { upload_status: operationState === 'FAILED' ? 'FAILED' : 'UPLOADING',
    transfer_state: operationState === 'FAILED' ? 'FAILED' : 'COMPLETING', source: null, attempt: null })
  Object.assign(value.completion, { operation_state: operationState, result_status: operationState === 'FAILED' ? 'FAILED' : null,
    source_id: null, source_version: null, registered_rights_revision: null, parse_version: null, attempt_id: null, run_id: null,
    error_code: operationState === 'FAILED' ? 'PROCESSING_FAILED' : null, reason: operationState === 'FAILED' ? 'SOURCE_VALIDATION_FAILED' : null })
  uploads.verifySourceUploadResult(value, journal, { ...state, registration: { ...state.registration, upload_status: value.upload_status, transfer_state: value.transfer_state } })
  assert.throws(() => verify({ ...value, source: registered.source }))
}
checks.push('pending_and_failed_completion_cannot_claim_registered_source')
process.stdout.write(JSON.stringify({ data_kind: 'SIMULATION', checks, result: 'PASS', actual_http_verified: false, visual: 'NOT_RUN' }) + '\n')
