import { readFile, realpath } from 'node:fs/promises'
import { isAbsolute, relative, resolve } from 'node:path'
import { SchemaCatalogue } from './catalogue.js'
import type { CatalogueManifest } from './catalogue.js'

export async function loadProject(root: string): Promise<SchemaCatalogue> {
  const allowed = await realpath(root)
  const manifest: CatalogueManifest = JSON.parse(await readFile(resolve(allowed, 'contracts/catalogue.json'), 'utf-8'))
  const files = new Map<string, Uint8Array>()
  for (const entry of manifest.schemas) {
    const path = await realpath(resolve(allowed, entry.path))
    const location = relative(allowed, path)
    if (isAbsolute(location) || location.startsWith('..')) throw new Error('Schema 路径超出登记目录')
    files.set(entry.path, new Uint8Array(await readFile(path)))
  }
  return SchemaCatalogue.create(manifest, files)
}
