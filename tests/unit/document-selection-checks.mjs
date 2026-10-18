// Pure Selection regressions using the real frontend schema, hashing and table helpers.
// No browser, HTTP server, socket, database or network is used.
import assert from 'node:assert/strict'
import { createHash, webcrypto } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from '../../web/node_modules/esbuild/lib/main.js'

const root = path.resolve(import.meta.dirname, '../..')
const output = path.join(root, '.implementation-work/document-selection-unit')
const dependency = createRequire(path.join(root, 'web/package.json'))
const { canonicalizeEx } = dependency('json-canonicalize')
await mkdir(output, { recursive: true })
await build({ entryPoints: [path.join(root, 'web/src/api/medical/document-selection.ts')], bundle: true, platform: 'node', format: 'cjs',
  outfile: path.join(output, 'selection.cjs'), plugins: [
    { name: 'local-dependencies-and-schema', setup(bundler) {
      bundler.onResolve({ filter: /^[a-z]/ }, args => ({ path: dependency.resolve(args.path), external: true }))
      bundler.onResolve({ filter: /\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path.replace(/\?raw$/, '')), namespace: 'raw' }))
      bundler.onLoad({ filter: /.*/, namespace: 'raw' }, async args => ({ contents: await readFile(args.path, 'utf8'), loader: 'text' }))
    } }
  ] })
Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
globalThis.window = new EventTarget()
const { createDocumentSelection } = (await import(pathToFileURL(path.join(output, 'selection.cjs')))).default
const id = () => crypto.randomUUID()
const hash = value => createHash('sha256').update(value).digest('hex')
const digest = value => hash(canonicalizeEx(value))
const checks = []
const pass = name => checks.push({ name, result: 'PASS' })

function fixture() {
  const nodes = []
  function node(parent, ordinal, content) {
    const value = { node_id: id(), parent_id: parent?.node_id ?? null, ordinal, field_ids: [], evidence_link_ids: [], content }
    nodes.push(value); return value
  }
  const text = (parent, ordinal) => node(parent, ordinal, { kind: 'TEXT', text: '甲😀e\u0301\n乙', marks: [] })
  const image = (parent, ordinal) => node(parent, ordinal, { kind: 'IMAGE', artifact_id: id(), sha256: 'a'.repeat(64), alt_text: 'SIMULATION', caption: '' })
  function table(parent, ordinal, rows, columns) {
    const value = node(parent, ordinal, { kind: 'TABLE', row_ids: Array.from({ length: rows }, id), column_ids: Array.from({ length: columns }, id), cells: [], caption: 'SIMULATION' })
    return value
  }
  function cell(table, row, column, rowSpan, columnSpan, children) {
    const value = { cell_id: id(), row_id: table.content.row_ids[row], column_id: table.content.column_ids[column],
      row_span: rowSpan, column_span: columnSpan, node_ids: children.map(child => child.node_id) }
    table.content.cells.push(value); return value
  }
  const document = node(null, 0, { kind: 'DOCUMENT', title: 'SIMULATION selection' })
  const outer = table(document, 0, 2, 2)
  const insideText = text(outer, 0)
  const insideParagraph = node(outer, 1, { kind: 'PARAGRAPH', title: '' })
  const deepText = text(insideParagraph, 0)
  const insideImage = image(insideParagraph, 1)
  const insideTable = table(insideParagraph, 2, 1, 1)
  const nestedImage = image(insideTable, 0)
  const insideCell = cell(insideTable, 0, 0, 1, 1, [nestedImage])
  const outsideText = text(outer, 2)
  const outsideParagraph = node(outer, 3, { kind: 'PARAGRAPH', title: '' })
  const outsideImage = image(outsideParagraph, 0)
  const outsideTable = table(outsideParagraph, 1, 1, 2)
  const nestedText = text(outsideTable, 0)
  const otherNestedText = text(outsideTable, 1)
  const outsideCell = cell(outsideTable, 0, 0, 1, 1, [nestedText])
  const otherOutsideCell = cell(outsideTable, 0, 1, 1, 1, [otherNestedText])
  const bottomText = text(outer, 4)
  const tail = text(document, 1)
  const merged = cell(outer, 0, 0, 2, 1, [insideText, insideParagraph])
  const upper = cell(outer, 0, 1, 1, 1, [outsideText, outsideParagraph])
  const bottom = cell(outer, 1, 1, 1, 1, [bottomText])
  return { body: { schema_version: '1.0.0', template_version: id(), nodes, fields: [], evidence_links: [], fact_usages: [] },
    revision: id(), outer, merged, upper, bottom, insideText, deepText, insideImage, insideTable, insideCell, nestedImage,
    outsideText, outsideImage, outsideTable, outsideCell, otherOutsideCell, nestedText, otherNestedText, bottomText, tail }
}
const textRequest = node => ({ kind: 'TEXT', node_id: node.node_id, start_utf16: 1, end_utf16: 3 })
const imageRequest = node => ({ kind: 'IMAGE', node_id: node.node_id })
const tableRequest = (node, ...cells) => ({ kind: 'TABLE', node_id: node.node_id, cell_ids: cells.map(cell => cell.cell_id) })
const select = (fixture, requests) => createDocumentSelection(fixture.body, fixture.revision, requests, new AbortController().signal)
const invalid = (fixture, requests) => assert.rejects(() => select(fixture, requests), error => error.code === 'SELECTION_INVALID')

const f = fixture()
const tableOnly = await select(f, [tableRequest(f.outer, f.merged)])
assert.deepEqual(tableOnly.segments, [{ kind: 'TABLE', node_id: f.outer.node_id, row_ids: f.outer.content.row_ids,
  column_ids: [f.outer.content.column_ids[0]], cell_ids: [f.merged.cell_id], table_sha256: digest(f.outer) }])
pass('merged cell retains full-node table hash and full row span')

await invalid(f, [tableRequest(f.outer, f.merged), textRequest(f.insideText)])
await invalid(f, [tableRequest(f.outer, f.merged), textRequest(f.deepText)])
pass('selected cell rejects its direct text and text below a paragraph')

await invalid(f, [tableRequest(f.outer, f.merged), imageRequest(f.insideImage)])
await invalid(f, [tableRequest(f.outer, f.merged), tableRequest(f.insideTable, f.insideCell)])
await invalid(f, [tableRequest(f.outer, f.merged), imageRequest(f.nestedImage)])
pass('selected cell rejects descendant image, nested table and nested image')

const disjoint = await select(f, [tableRequest(f.outer, f.merged), textRequest(f.outsideText), imageRequest(f.outsideImage),
  tableRequest(f.outsideTable, f.outsideCell), textRequest(f.otherNestedText), textRequest(f.bottomText), textRequest(f.tail)])
assert.deepEqual(disjoint.segments.map(segment => segment.node_id), [f.outer, f.outsideText, f.outsideImage, f.outsideTable,
  f.otherNestedText, f.bottomText, f.tail].map(node => node.node_id))
assert.deepEqual(disjoint.segments[1], { kind: 'TEXT', node_id: f.outsideText.node_id, node_sha256: digest(f.outsideText),
  start_cp: 1, end_cp: 2, quote_sha256: hash('😀') })
assert.deepEqual(disjoint.segments[2], { kind: 'IMAGE', node_id: f.outsideImage.node_id,
  image_artifact_id: f.outsideImage.content.artifact_id, image_sha256: f.outsideImage.content.sha256 })
assert.equal(disjoint.segments[3].table_sha256, digest(f.outsideTable))
assert.equal(Object.isFrozen(disjoint), true)
assert.equal(Object.isFrozen(disjoint.segments), true)
assert.equal(disjoint.document_revision, f.revision)
pass('unselected cells and following nodes allow disjoint TEXT, IMAGE and nested TABLE; hashes and code points are unchanged')

await invalid(f, [tableRequest(f.outer, f.merged), tableRequest(f.outsideTable, f.outsideCell), textRequest(f.nestedText)])
const nestedDisjoint = await select(f, [tableRequest(f.outer, f.merged), tableRequest(f.outsideTable, f.otherOutsideCell), textRequest(f.nestedText)])
assert.equal(nestedDisjoint.segments.length, 3)
pass('nested selections track their own selected cells without covering other cells')

const partialRectangle = await select(f, [tableRequest(f.outer, f.bottom, f.merged), textRequest(f.outsideText)])
assert.deepEqual(partialRectangle.segments[0].row_ids, f.outer.content.row_ids)
assert.deepEqual(partialRectangle.segments[0].column_ids, f.outer.content.column_ids)
assert.deepEqual(partialRectangle.segments[0].cell_ids, [f.merged.cell_id, f.bottom.cell_id])
await invalid(f, [tableRequest(f.outer, f.merged, f.bottom), textRequest(f.bottomText)])
pass('merged cells use exact cell membership, not the union row/column bounding rectangle')

const columnSpan = fixture()
columnSpan.merged.row_span = 1; columnSpan.merged.column_span = 2
columnSpan.upper.row_id = columnSpan.outer.content.row_ids[1]; columnSpan.upper.column_id = columnSpan.outer.content.column_ids[0]
const mergedColumns = await select(columnSpan, [tableRequest(columnSpan.outer, columnSpan.merged), textRequest(columnSpan.outsideText)])
assert.deepEqual(mergedColumns.segments[0].row_ids, [columnSpan.outer.content.row_ids[0]])
assert.deepEqual(mergedColumns.segments[0].column_ids, columnSpan.outer.content.column_ids)
await invalid(columnSpan, [tableRequest(columnSpan.outer, columnSpan.merged), textRequest(columnSpan.deepText)])
pass('column-spanning merged cell rejects descendants and preserves outside-cell selection')

for (const badRoot of ['missing', 'unrelated', 'indirect']) {
  const malformed = fixture()
  malformed.merged.node_ids = [badRoot === 'missing' ? id() : badRoot === 'unrelated' ? malformed.tail.node_id : malformed.deepText.node_id]
  await invalid(malformed, [tableRequest(malformed.outer, malformed.merged)])
}
const sharedRoot = fixture()
sharedRoot.upper.node_ids.push(sharedRoot.insideText.node_id)
await invalid(sharedRoot, [tableRequest(sharedRoot.outer, sharedRoot.merged, sharedRoot.upper)])
pass('selected cell roots must exist, be direct table children and have unique selected-cell ownership')

await invalid(f, [textRequest(f.outsideText), tableRequest(f.outer, f.merged)])
await invalid(f, [textRequest(f.outsideText), { ...textRequest(f.outsideText), start_utf16: 3, end_utf16: 4 }])
await invalid(f, [tableRequest(f.outer, f.merged, f.merged)])
await invalid(f, [tableRequest(f.outer, { cell_id: id() })])
await invalid(f, [{ ...textRequest(f.outsideText), start_utf16: 2 }])
await invalid(f, [])
pass('existing order, duplicate node/cell, unknown cell, Unicode boundary and empty selection rejections remain')

await writeFile(path.join(output, 'result.json'), JSON.stringify({ scope: 'PURE_SELECTION_SEMANTICS', browser: 'NOT_RUN', live_backend: 'NOT_RUN', checks }, null, 2) + '\n')
console.log(JSON.stringify({ result: 'PASS', scope: 'PURE_SELECTION_SEMANTICS', checks: checks.length }))
