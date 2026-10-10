import { readFile, realpath } from 'node:fs/promises'
import { isAbsolute, relative, resolve } from 'node:path'
import $RefParser from '@apidevtools/json-schema-ref-parser'
import { SchemaCatalogue, type CatalogueManifest } from '@medical-agent/contracts'
import { readJson } from './json.js'

const additionalSchemas = [
  { name: 'tool-inputs', path: 'agent-design/tool-inputs.schema.json', sha256: '197f61b9cb8b9fdf6ac8015a25ae46ba360a43a5229918722d12556297fea1d8' },
  { name: 'tool-success', path: 'agent-design/tool-success.schema.json', sha256: 'ce92b0cd6bc3d67279a48e9d147f24397ea8064fdceb368dcb8a3226d1a340c7' },
] as const

export async function loadCatalogue(root: string) {
  const allowed = await realpath(root)
  const original = readJson(await readFile(resolve(allowed, 'contracts/catalogue.json'), 'utf-8')) as unknown as CatalogueManifest
  const manifest: CatalogueManifest = { ...original, schemas: [...original.schemas, ...additionalSchemas] }
  const files = new Map<string, Uint8Array>()
  for (const entry of manifest.schemas) {
    const path = await realpath(resolve(allowed, entry.path))
    const location = relative(allowed, path)
    if (isAbsolute(location) || location.startsWith('..')) throw new Error('Schema 文件超出固定目录')
    files.set(entry.path, await readFile(path))
  }
  const catalogue = await SchemaCatalogue.create(manifest, files)
  const published = new Map<string, Uint8Array>()
  for (const entry of manifest.schemas) published.set(catalogue.schemaRef(entry.name), files.get(entry.path)!)
  const document = await new $RefParser<Record<string, unknown>>().dereference(catalogue.schemaRef('tool-inputs'),
    readJson(new TextDecoder('utf-8', { fatal: true }).decode(files.get(additionalSchemas[0].path)!)), {
      resolve: { file: false, http: false, published: { order: 1, canRead: file => published.has(file.url),
        read: file => { const bytes = published.get(file.url); if (bytes === undefined) throw new Error('Schema 引用未发布'); return Buffer.from(bytes) } } },
      dereference: { circular: false }, mutateInputSchema: false, timeoutMs: 10_000,
    })
  const definitions = document['$defs']
  if (definitions === null || typeof definitions !== 'object' || Array.isArray(definitions)) throw new Error('工具 Schema 缺少定义')
  return { catalogue, inputDefinitions: definitions as Record<string, Record<string, unknown>> }
}
