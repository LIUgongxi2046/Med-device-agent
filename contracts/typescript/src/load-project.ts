import { readFile, realpath } from 'node:fs/promises'
import { isAbsolute, relative, resolve, sep } from 'node:path'
import { mergePublishedCatalogue, SchemaCatalogue } from './catalogue.js'

export async function loadProject(root: string): Promise<SchemaCatalogue> {
  const allowed = await realpath(root)
  const readRegistered = async (registered: string): Promise<Uint8Array> => {
    const path = await realpath(resolve(allowed, registered))
    const location = relative(allowed, path)
    if (isAbsolute(location) || location === '..' || location.startsWith(`..${sep}`)) throw new Error('Schema 路径超出登记目录')
    return new Uint8Array(await readFile(path))
  }
  const manifest = await mergePublishedCatalogue(
    await readRegistered('contracts/catalogue.json'),
    await readRegistered('contracts/extensions/evaluation-results.catalogue.json'),
    await readRegistered('contracts/extensions/simulation-execution.catalogue.json'),
    await readRegistered('contracts/extensions/task-runtime.catalogue.json'),
    await readRegistered('contracts/extensions/evaluation-context-compiler.catalogue.json'),
    await readRegistered('contracts/extensions/evaluation-planning-context-compiler.catalogue.json'),
    await readRegistered('contracts/extensions/task-tool-read.catalogue.json'),
    await readRegistered('contracts/extensions/task-professional-package.catalogue.json'),
    await readRegistered('contracts/extensions/task-plan-draft-package.catalogue.json'),
    await readRegistered('contracts/extensions/professional-review-packages.catalogue.json'),
    await readRegistered('contracts/extensions/source-evidence-v2.catalogue.json'),
    await readRegistered('contracts/extensions/document-evidence-v2.catalogue.json'),
    await readRegistered('contracts/extensions/template-application.catalogue.json'),
    await readRegistered('contracts/extensions/strict-field-evidence-v2.catalogue.json'),
    await readRegistered('contracts/extensions/document-operations-v2.catalogue.json'),
    await readRegistered('contracts/extensions/evaluation-business-v2.catalogue.json'),
    await readRegistered('contracts/extensions/task-professional-package-v2.catalogue.json'),
    await readRegistered('contracts/extensions/evaluation-stage-input.catalogue.json'),
  )
  const files = new Map<string, Uint8Array>()
  for (const entry of manifest.schemas) {
    files.set(entry.path, await readRegistered(entry.path))
  }
  return SchemaCatalogue.create(manifest, files)
}
