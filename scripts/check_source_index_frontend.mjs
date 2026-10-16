import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

globalThis.window = new EventTarget()
const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: index } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/source-index-result.ts', import.meta.url)), { root, mode: 'medical' })
const source = randomUUID(), version = randomUUID(), project = randomUUID(), parse = randomUUID()
const build = { event_id: randomUUID(), parse_version: parse, index_version: randomUUID(), mode: 'FULLTEXT', build_status: 'READY',
  index_status: 'READY', delivery_status: 'DELIVERED', expected_count: '18446744073709551615', verified_count: '18446744073709551615',
  started_at: '2026-10-14T01:02:03.123456Z', execution_deadline: '2026-10-14T01:02:04.123456Z',
  updated_at: '2026-10-14T01:02:03.123457Z', revision: '1',
  result: { status: 'READY', expected_count: '18446744073709551615', verified_count: '18446744073709551615', failure_code: null } }
const listing = { schema_version: '1.0.0', source_id: source, source_version: version, project_id: project,
  total: '1', listing_sha256: 'a'.repeat(64), items: [build] }
const verify = value => index.verifySourceIndexListing(value, source, version, project)
const rejected = value => assert.throws(() => verify(value), error => ['CONTENT_MISMATCH', 'SCHEMA_INVALID'].includes(error.code))
verify(listing); verify({ ...listing, total: '0', items: [] })
const checks = ['complete_result_empty_listing_and_unsigned_precision']
for (const status of ['READY', 'FAILED', 'BUILDING']) {
  const value = structuredClone(listing); const item = value.items[0]
  item.build_status = status; item.index_status = 'WITHDRAWN'
  if (status !== 'READY') {
    item.verified_count = '0'; item.delivery_status = 'PENDING'
    item.result = status === 'BUILDING' ? null : { status: 'FAILED', expected_count: item.expected_count, verified_count: '0', failure_code: 'INDEX_LIMIT' }
  }
  verify(value)
}
checks.push('withdrawn_index_preserves_all_original_build_results')
for (const field of ['source_id', 'source_version', 'project_id']) rejected({ ...listing, [field]: randomUUID() })
rejected({ ...listing, total: '2' })
rejected({ ...listing, total: '2', items: [build, build] })
checks.push('source_project_total_and_unique_index_identity_required')
for (const field of ['expected_count', 'verified_count', 'revision']) {
  const value = structuredClone(listing); value.items[0][field] = '18446744073709551616'
  rejected(value)
}
const zero = structuredClone(listing); zero.items[0].revision = '0'; rejected(zero)
checks.push('unsigned_overflow_and_zero_revision_rejected')
for (const mutation of [item => item.execution_deadline = item.started_at,
  item => item.updated_at = '2026-10-14T01:02:03.123455Z', item => item.verified_count = '1', item => item.delivery_status = 'PENDING']) {
  const value = structuredClone(listing); mutation(value.items[0]); rejected(value)
}
checks.push('exact_microsecond_order_complete_count_and_delivery_required')
const waiting = structuredClone(listing); Object.assign(waiting.items[0], { build_status: 'BUILDING', index_status: 'BUILDING',
  delivery_status: 'DELIVERING', verified_count: '0', result: null }); verify(waiting)
const invalidWaiting = structuredClone(waiting); invalidWaiting.items[0].result = build.result; rejected(invalidWaiting)
const invalidFailure = structuredClone(waiting); Object.assign(invalidFailure.items[0], { build_status: 'FAILED', index_status: 'FAILED',
  delivery_status: 'FAILED', result: { ...build.result, status: 'FAILED', verified_count: '0' } }); rejected(invalidFailure)
checks.push('pending_result_and_failure_reason_remain_independent')
for (const mutation of [value => value.unpublished_field = true, value => value.items[0].expected_count = 9007199254740993,
  value => value.items[0].started_at = '2026-10-14']) {
  const value = structuredClone(listing); mutation(value); assert.throws(() => verify(value), error => error.code === 'SCHEMA_INVALID')
}
checks.push('closed_schema_invalid_time_and_numeric_count_rejected')
process.stdout.write(JSON.stringify({ result: 'PASS', data_kind: 'SIMULATION', checks, actual_index_worker_verified: false, visual: 'NOT_RUN' }) + '\n')
