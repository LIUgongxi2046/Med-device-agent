import assert from 'node:assert/strict'
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { parse } from '@babel/parser'
import { parse as parseVue } from '@vue/compiler-sfc'
import { loadMedicalProtocol } from './medical-schema-module.mjs'

const protocol = await loadMedicalProtocol()
const webRoot=new URL('../',import.meta.url)
const references = []
function walk(node, visit) {
  if (!node || typeof node !== 'object') return
  if (typeof node.type === 'string') visit(node)
  for (const [key, value] of Object.entries(node)) {
    if (['loc', 'start', 'end', 'extra'].includes(key)) continue
    if (Array.isArray(value)) value.forEach(item => walk(item, visit))
    else if (value && typeof value === 'object') walk(value, visit)
  }
}
async function sourceFiles(directory){
  const files=[]
  for(const entry of await readdir(directory,{withFileTypes:true})){
    const path=new URL(entry.name,directory)
    if(entry.isDirectory())files.push(...await sourceFiles(new URL(entry.name+'/',directory)))
    else if(entry.isFile()&&/\.(ts|js|vue)$/.test(entry.name)&&entry.name!=='protocol.ts')files.push(path)
  }
  return files
}
const files=(await Promise.all(['src/api/medical/','src/views/medical/','src/components/Medical/'].map(path=>sourceFiles(new URL(path,webRoot))))).flat()
for (const file of files) {
  const text=await readFile(file,'utf8'),name=decodeURIComponent(file.pathname.slice(webRoot.pathname.length))
  const descriptor=file.pathname.endsWith('.vue')?parseVue(text,{filename:name}).descriptor:null
  const blocks=descriptor?[descriptor.script,descriptor.scriptSetup].filter(Boolean):[{content:text,loc:{start:{line:1}}}]
  for(const block of blocks){
  const ast = parse(block.content, { sourceType: 'module', plugins: ['typescript'] })
  const consumers=new Set()
  for(const statement of ast.program.body)if(statement.type==='ImportDeclaration'&&/(^|\/)protocol(?:\.ts)?$/.test(statement.source.value))for(const specifier of statement.specifiers){
    if(specifier.type==='ImportSpecifier'&&['validate','schemaIssues'].includes(specifier.imported.name))consumers.add(specifier.local.name)
  }
  const constants = new Map()
  function value(node) {
    if (node?.type === 'StringLiteral') return node.value
    if (node?.type === 'Identifier') return constants.get(node.name)
    if (node?.type === 'BinaryExpression' && node.operator === '+') {
      const left = value(node.left), right = value(node.right)
      if (typeof left === 'string' && typeof right === 'string') return left + right
    }
    return undefined
  }
  walk(ast, node => { if (node.type === 'VariableDeclarator' && node.id.type === 'Identifier' && typeof value(node.init) === 'string') constants.set(node.id.name, value(node.init)) })
  walk(ast, node => {
    if (node.type !== 'CallExpression' || node.callee.type !== 'Identifier' || !consumers.has(node.callee.name)) return
    const reference = value(node.arguments[0])
    if (typeof reference === 'string' && (reference.startsWith('urn:medical:') || reference.startsWith('https://schemas.medical-agent.invalid/'))) references.push({ file: name, line: node.loc.start.line+block.loc.start.line-1, reference })
  })
  }
}
const results = await Promise.allSettled(references.map(async item => { protocol.schemaIssues(item.reference, undefined); return item }))
const missing = results.flatMap((result, index) => result.status === 'rejected' ? [{ ...references[index], message: result.reason.message }] : [])
const report = { verified_at: new Date().toISOString(), inspected_calls: references.length, missing }
await mkdir(new URL('../../.implementation-work/', import.meta.url), { recursive: true, mode: 0o700 })
await writeFile(new URL('../../.implementation-work/schema-consumer-audit.json', import.meta.url), JSON.stringify(report, null, 2))
console.log(JSON.stringify({ inspected_calls: references.length, missing }, null, 2))
assert.deepEqual(missing, [])
