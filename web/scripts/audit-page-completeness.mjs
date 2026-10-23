import assert from 'node:assert/strict'
import { readdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { parse as parseJavaScript } from '@babel/parser'
import { parse as parseVue, compileScript, compileTemplate } from '@vue/compiler-sfc'

const root = fileURLToPath(new URL('../../', import.meta.url))
const routerFile = path.join(root, 'web/src/router/index.js')
const source = await readFile(routerFile, 'utf8')
const program = parseJavaScript(source, { sourceType: 'module' })
const routes = []
const property = (node, name) => node.properties.find(value => value.type === 'ObjectProperty' && value.key.name === name)?.value
const literal = node => node?.type === 'StringLiteral' ? node.value : null
function collect(array, parent, collection) {
  for (const node of array.elements) {
    if (node?.type !== 'ObjectExpression') continue
    const supplied = literal(property(node, 'path'))
    if (supplied === null) continue
    const fullPath = supplied.startsWith('/') ? supplied : `${parent}/${supplied}`.replaceAll('//', '/')
    const component = property(node, 'component')
    const imported = component?.type === 'ArrowFunctionExpression' && component.body.type === 'CallExpression'
      && component.body.callee.type === 'Import' ? literal(component.body.arguments[0]) : null
    const meta = property(node, 'meta')
    const children = property(node, 'children')
    routes.push({ path: fullPath, name: literal(property(node, 'name')), title: meta?.type === 'ObjectExpression' ? literal(property(meta, 'title')) : null,
      source: imported, collection, parameterized: fullPath.includes(':'), evidence: 'SOURCE_OBSERVED', business_verified: false })
    if (children?.type === 'ArrayExpression') collect(children, fullPath === '/' ? '' : fullPath, collection)
  }
}
for (const statement of program.program.body) {
  if (statement.type !== 'ExportNamedDeclaration' || statement.declaration?.type !== 'VariableDeclaration') continue
  for (const variable of statement.declaration.declarations) {
    if (['medicalRoutes', 'constantRoutes', 'dynamicRoutes'].includes(variable.id.name) && variable.init?.type === 'ArrayExpression') collect(variable.init, '', variable.id.name)
  }
}
async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(entry => entry.isDirectory() ? files(path.join(directory, entry.name)) : entry.name.endsWith('.vue') ? [path.join(directory, entry.name)] : []))
  return nested.flat()
}
const pages = []
for (const filename of await files(path.join(root, 'web/src/views'))) {
  const relative = path.relative(root, filename)
  const { descriptor, errors } = parseVue(await readFile(filename, 'utf8'), { filename })
  assert.equal(errors.length, 0, relative)
  const script = descriptor.script || descriptor.scriptSetup ? compileScript(descriptor, { id: relative,
    fs: { fileExists: existsSync, readFile: filename => readFileSync(filename, 'utf8') } }) : null
  if (descriptor.template) {
    const compiled = compileTemplate({ id: relative, filename, source: descriptor.template.content, compilerOptions: { bindingMetadata: script?.bindings } })
    assert.equal(compiled.errors.length, 0, relative)
  }
  const apiImports = Object.values(script?.imports ?? {}).filter(item => item.source.startsWith('@/api/')).map(item => ({ source: item.source, name: item.imported }))
  pages.push({ source: relative, compiled: true, api_imports: apiImports, bindings: Object.keys(script?.bindings ?? {}), business_verified: false })
}
const result = { inspected_at: new Date().toISOString(), source: 'CURRENT_WORKTREE', routes, pages, counts: { route_records: routes.length, vue_pages: pages.length },
  scope: 'SOURCE_AND_COMPILATION_INVENTORY', business_execution_status: 'REQUIRES_REAL_PAGE_VALIDATION', visual_review: 'NOT_RUN' }
await writeFile(path.join(root, '.implementation-work/page-source-inventory.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify({ inspected_at: result.inspected_at, counts: result.counts, scope: result.scope }))
