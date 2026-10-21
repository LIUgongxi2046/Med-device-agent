import assert from 'node:assert/strict'
import test from 'node:test'
import { resolve } from 'node:path'
import { loadProject } from '../src/load-project.js'
import { decodeEvidence } from '../src/source-evidence.js'
test('explicit HTML v2 is not accepted as v1', async () => {
  const catalogue = await loadProject(resolve('../..'))
  const value = { schema_version: '2.0.0', tenant_id: 'tenant', project_id: 'project', source_id: 'source',
    source_version: 'version', parse_version: 'parse', chunk_id: 'chunk', locator_kind: 'HTML',
    locator: { schema_version: '1.0.0', kind: 'HTML', original_sha256: 'a'.repeat(64),
      address: { kind: 'HTML_ELEMENT', element_path: '/html[1]/body[1]/p[1]', text_start: 0, text_end: 4 } },
    quote_hash: 'b'.repeat(64), approval_revision: 'revision' }
  assert.equal(decodeEvidence(catalogue, value).locator.kind, 'HTML')
  assert.throws(() => decodeEvidence(catalogue, { ...value, schema_version: '1.0.0' }))
})
