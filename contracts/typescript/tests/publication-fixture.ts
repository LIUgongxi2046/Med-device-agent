import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { parse } from 'yaml'
import type { CatalogueManifest } from '../src/catalogue.js'

// Independently enumerate the released manifest chain, not the loader's validators.
export const publicationPaths = [
  'contracts/catalogue.json',
  'contracts/extensions/evaluation-results.catalogue.json',
  'contracts/extensions/simulation-execution.catalogue.json',
  'contracts/extensions/task-runtime.catalogue.json',
  'contracts/extensions/evaluation-context-compiler.catalogue.json',
  'contracts/extensions/evaluation-planning-context-compiler.catalogue.json',
  'contracts/extensions/task-tool-read.catalogue.json',
  'contracts/extensions/task-professional-package.catalogue.json',
  'contracts/extensions/task-plan-draft-package.catalogue.json',
  'contracts/extensions/professional-review-packages.catalogue.json',
  'contracts/extensions/source-evidence-v2.catalogue.json',
  'contracts/extensions/document-evidence-v2.catalogue.json',
  'contracts/extensions/template-application.catalogue.json',
  'contracts/extensions/strict-field-evidence-v2.catalogue.json',
  'contracts/extensions/document-operations-v2.catalogue.json',
  'contracts/extensions/evaluation-business-v2.catalogue.json',
  'contracts/extensions/task-professional-package-v2.catalogue.json',
  'contracts/extensions/evaluation-stage-input.catalogue.json',
] as const

export async function publicationFixture(root: string) {
  const files = new Set<string>(publicationPaths)
  let definitionCount = 0
  for (const path of publicationPaths) {
    const manifest = JSON.parse(await readFile(resolve(root, path), 'utf8')) as CatalogueManifest
    for (const entry of manifest.schemas) {
      files.add(entry.path)
      const text = await readFile(resolve(root, entry.path), 'utf8')
      const schema = entry.path.endsWith('.yaml') ? parse(text) : JSON.parse(text)
      definitionCount += Object.keys(schema.$defs ?? {}).length
    }
  }
  return { files: [...files], definitionCount }
}
