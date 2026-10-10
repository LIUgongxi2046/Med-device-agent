import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { SchemaCatalogue, sha256 } from './catalogue.js'
import { decodeDataset } from './dataset.js'
import { loadProject } from './load-project.js'
import { ERROR_CODES } from './errors.js'
import { codePointQuote, decimalValue, unsignedRevision, utcTime, uuidValue } from './wire.js'

const root = resolve('../..')
const executionId = process.argv[2]
if (!executionId) throw new Error('需要实际检查执行身份')
const catalogue: SchemaCatalogue = await loadProject(root)
const dataset = decodeDataset(catalogue, JSON.parse(await readFile(resolve(root, 'data-design/sample_dataset.json'), 'utf-8')))
const locators: Record<string, string> = {}
const sources: Record<string, string> = {}
const textRanges: Record<string, string> = {}
const scalarDigests: Record<string, string> = {}
const sourceRecords = new Map(dataset.sources.map(source => [source.source_id, source]))
for (const source of dataset.sources) {
  const bytes = new Uint8Array(await readFile(resolve(root, source.source_ref)))
  const digest = await sha256(bytes)
  if (digest !== source.sha256 || bytes.length !== source.size_bytes) throw new Error('原件摘要或长度不一致')
  sources[source.source_id] = digest
  for (const [field, definition, value] of [
    ['source_id', 'Text', source.source_id], ['sha256', 'Hash', source.sha256],
    ['size_bytes', 'TokenCount', source.size_bytes], ['parent_source_id', 'NullableText', source.parent_source_id],
  ] as const) scalarDigests[source.source_id + '/' + field] = await sha256(new TextEncoder().encode(catalogue.bind('model-stream', value, definition).canonicalJson))
}
for (const record of dataset.records) {
  scalarDigests[record.record_id + '/text'] = await sha256(new TextEncoder().encode(catalogue.bind('model-stream', record.text, 'Text').canonicalJson))
  locators[record.record_id] = await sha256(new TextEncoder().encode(catalogue.bind('dataset', record.locator, 'locator').canonicalJson))
  if (record.locator.kind !== 'TXT') continue
  const source = sourceRecords.get(record.source_id)
  if (!source) throw new Error('来源身份不存在')
  const text = await readFile(resolve(root, source.source_ref), 'utf-8')
  const quote = await codePointQuote(text, record.locator.char_start, record.locator.char_end, record.quote_hash)
  textRanges[record.record_id] = await sha256(new TextEncoder().encode(quote))
}
console.log(JSON.stringify({
  execution_id: executionId,
  dataset_sha256: await sha256(new TextEncoder().encode(catalogue.bind('dataset', dataset).canonicalJson)),
  source_sha256: sources,
  locator_sha256: locators,
  text_ranges: textRanges,
  schema_scalar_sha256: scalarDigests,
  definition_count: catalogue.definitionCount,
  error_codes: [...ERROR_CODES].sort(),
  technical_wire: {
    execution_uuid: uuidValue(executionId),
    unsigned_max: unsignedRevision(((1n << 64n) - 1n).toString()).toString(),
    money_boundary: decimalValue('0.00000001', 24, 8).toFixed(8),
    measurement_boundary: decimalValue('0.000000000001', 38, 12).toFixed(12),
    utc_time: utcTime(dataset.created_at).toString({ fractionalSecondDigits: 6 })
  }
}))
