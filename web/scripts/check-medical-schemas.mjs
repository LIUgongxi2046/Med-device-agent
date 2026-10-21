import { loadMedicalProtocol } from './medical-schema-module.mjs'
const protocol = await loadMedicalProtocol()
const count = protocol.compileAllMedicalSchemas()
console.log(`Medical schema gate: ${count} fixed references compiled successfully`)

// Exact publication bytes, not parsed JSON equivalence, gate the additional authoring choices.
const { readFile } = await import('node:fs/promises')
const { createHash } = await import('node:crypto')
const root = new URL('../../', import.meta.url)
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
const extensionBytes = await readFile(new URL('contracts/extensions/task-tool-read.catalogue.json', root))
if (digest(extensionBytes) !== 'd5401ee6c04ded4543b96bb7ec3c1fe57ef39ee3fa2cc9a6ddca191671c93cb1') throw Error('Task tool read extension bytes changed')
const extension = JSON.parse(extensionBytes.toString('utf8'))
if (digest(await readFile(new URL('contracts/catalogue.json', root))) !== extension.base_catalogue_sha256) throw Error('Task tool read extension base changed')
for (const entry of extension.schemas) if (digest(await readFile(new URL(entry.path, root))) !== entry.sha256) throw Error('Task tool read registered schema bytes changed: ' + entry.name)
console.log('Task tool authoring choices: exact published extension and two schema files verified')

const packageBytes = await readFile(new URL('contracts/extensions/task-professional-package.catalogue.json', root))
if (digest(packageBytes) !== 'f1b0d3877e11eb4d87eb69dd809f8c1c2a285bd56a05b2cb24d8ae9ebab370fc') throw Error('Task professional package publication bytes changed')
const packagePublication = JSON.parse(packageBytes.toString('utf8'))
if (packagePublication.base_catalogue_sha256 !== extension.base_catalogue_sha256) throw Error('Task professional package base changed')
for (const entry of packagePublication.schemas) if (digest(await readFile(new URL(entry.path, root))) !== entry.sha256) throw Error('Task professional package schema bytes changed')
console.log('Task professional package authoring: exact publication and schema bytes verified')

const packageAuthoringBytes = await readFile(new URL('business/medical-persistence/src/main/resources/task-professional-package-authoring.schema.json', root))
if (digest(packageAuthoringBytes) !== '4e1904ea23571d016a344ce4ef2212c202e8a4fad078cc7fcd305ec1d9b11e19') throw Error('Reconstructed task package authoring schema bytes changed')
console.log('Reconstructed task package creation/recovery contract bytes verified')
