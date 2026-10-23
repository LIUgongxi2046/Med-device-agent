import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { canonicalize } from 'json-canonicalize'
const root = new URL('../../', import.meta.url)
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const identity = { schema_version: '1.0.0', 'tasks.schema.json': hash(await readFile(new URL('business/medical-persistence/src/main/resources/tasks.schema.json', root))), 'contracts/catalogue.json': hash(await readFile(new URL('contracts/catalogue.json', root))) }
console.log(JSON.stringify({ identity, sha256: hash(canonicalize(identity)) }))
