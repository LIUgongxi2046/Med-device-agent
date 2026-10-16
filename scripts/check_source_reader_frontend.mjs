import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const { module: source } = await runnerImport(root + 'web/src/api/medical/document-body.ts', { root: root + 'web', mode: 'medical' })
assert.equal(typeof source.VerifiedByteAssembly, 'function', '固定原件必须核验完整字节并支持受控清理')
const dataset = JSON.parse(await readFile(root + 'data-design/sample_dataset.json', 'utf8'))
const checks = []
const originals = dataset.sources.filter(value => ['DOCX', 'TXT', 'MARKDOWN', 'PDF'].includes(value.format))
for (const format of ['CSV', 'XLSX', 'PPTX']) {
  const source_ref = '.implementation-work/document-tests/simulation-tasks.' + format.toLowerCase()
  const bytes = await readFile(root + source_ref)
  originals.push({ source_id: 'SIMULATION-' + format, source_ref, format, data_kind: 'SIMULATION', size_bytes: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex') })
}
for (const original of originals) {
  if (original.data_kind !== 'SIMULATION') assert.ok(original.allowed_use.includes('LOCAL_PARSER_CHECK'))
  const bytes = await readFile(root + original.source_ref)
  assert.equal(createHash('sha256').update(bytes).digest('hex'), original.sha256)
  assert.equal(bytes.length, original.size_bytes)
  const assembly = new source.VerifiedByteAssembly(String(bytes.length), original.sha256)
  for (let start = 0; start < bytes.length; start += 1024) {
    const part = bytes.subarray(start, Math.min(start + 1024, bytes.length))
    await assembly.append(String(start), part, createHash('sha256').update(part).digest('hex'))
  }
  const blob = await assembly.complete('application/octet-stream')
  assert.deepEqual(Buffer.from(await blob.arrayBuffer()), bytes)
  assert.equal(assembly.receivedBytes, String(bytes.length))
  assembly.dispose()
  await assert.rejects(() => assembly.complete('application/octet-stream'), error => error.code === 'ACTION_STALE')
  const incomplete = new source.VerifiedByteAssembly(String(bytes.length), original.sha256)
  const first = bytes.subarray(0, Math.min(1024, bytes.length - 1))
  await incomplete.append('0', first, createHash('sha256').update(first).digest('hex'))
  await assert.rejects(() => incomplete.complete('application/octet-stream'), error => error.code === 'CONTENT_MISMATCH')
  await assert.rejects(() => incomplete.append('0', first, createHash('sha256').update(first).digest('hex')), error => error.code === 'CONTENT_MISMATCH')
  incomplete.dispose()
  const damaged = new source.VerifiedByteAssembly(String(bytes.length), original.sha256)
  const changed = Buffer.from(bytes); changed[0] ^= 1
  await damaged.append('0', changed, createHash('sha256').update(changed).digest('hex'))
  await assert.rejects(() => damaged.complete('application/octet-stream'), error => error.code === 'CONTENT_MISMATCH')
  damaged.dispose()
  const rejected = new source.VerifiedByteAssembly(String(bytes.length), original.sha256)
  await assert.rejects(() => rejected.append('0', changed, original.sha256), error => error.code === 'CONTENT_MISMATCH')
  assert.equal(rejected.receivedBytes, '0'); rejected.dispose()
  checks.push({ source_id: original.source_id, format: original.format, data_kind: original.data_kind ?? 'LOCAL_PARSER_CHECK', sha256: original.sha256, result: 'PASS' })
}
assert.ok(checks.length > 0)
const output = root + '.implementation-work/FRONT-SOURCE-READER-20261013'
await mkdir(output, { recursive: true })
await writeFile(output + '/byte-checks.json', JSON.stringify({ scope: 'LOCAL_PARSER_CHECK', checks }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', checks: checks.length, scope: 'LOCAL_PARSER_CHECK' }))
