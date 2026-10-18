import { loadMedicalProtocol } from './medical-schema-module.mjs'
const protocol = await loadMedicalProtocol()
const count = protocol.compileAllMedicalSchemas()
console.log(`Medical schema gate: ${count} fixed references compiled successfully`)
