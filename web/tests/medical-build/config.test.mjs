import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { resolveConfig } from '../../node_modules/vite/dist/node/index.js'
const web = new URL('../../', import.meta.url).pathname
const pkg = JSON.parse(await readFile(web + 'package.json', 'utf8'))
test('medical start and build explicitly select medical mode without private env files', async () => {
  assert.match(pkg.scripts['build:medical'], /--mode medical/)
  assert.match(pkg.scripts['dev:medical'], /--mode medical/)
  const config = await resolveConfig({ root: web, configFile: web + 'vite.config.js' }, 'build', 'medical')
  assert.equal(config.define['import.meta.env.VITE_APP_ENV'], '"medical"')
  assert.equal(typeof JSON.parse(config.define['import.meta.env.VITE_APP_TITLE']), 'string')
  assert.ok(JSON.parse(config.define['import.meta.env.VITE_APP_TITLE']).length > 0)
})
test('legacy production mode is not forced into medical mode', async () => {
  const config = await resolveConfig({ root: web, configFile: web + 'vite.config.js' }, 'build', 'production')
  assert.equal(config.define['import.meta.env.VITE_APP_ENV'], undefined)
  assert.equal(JSON.parse(config.define['import.meta.env.VITE_APP_TITLE']), process.env.VITE_APP_TITLE || '若依管理系统')
})
