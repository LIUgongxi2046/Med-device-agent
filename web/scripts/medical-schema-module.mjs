// Isolated Node loader for the actual protocol module, shared by the build gate and tests.
import { build } from 'esbuild'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const protocol = fileURLToPath(new URL('../src/api/medical/protocol.ts', import.meta.url))
export async function loadMedicalProtocol({ transform = value => value, globals = {} } = {}) {
  const bundled = await build({ entryPoints: [protocol], bundle: true, write: false, platform: 'node', format: 'cjs', logLevel: 'silent',
    plugins: [{ name: 'medical-schema-local-sources', setup(builder) {
      builder.onLoad({ filter: /protocol\.ts$/ }, async () => ({ contents: transform(await readFile(protocol, 'utf8')), loader: 'ts', resolveDir: path.dirname(protocol) }))
      builder.onResolve({ filter: /\.yaml\?raw$/ }, args => ({ path: path.resolve(args.resolveDir, args.path) }))
      builder.onLoad({ filter: /\.yaml\?raw$/ }, async args => ({ contents: await readFile(args.path.replace(/\?raw$/, ''), 'utf8'), loader: 'text' }))
    } }] })
  const module = { exports: {} }
  const context = vm.createContext({ module, exports: module.exports, require: createRequire(import.meta.url), console, ...globals })
  new vm.Script(bundled.outputFiles[0].text, { filename: 'medical-protocol-isolated.cjs' }).runInContext(context)
  return module.exports
}
