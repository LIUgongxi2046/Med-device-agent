import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const raw = fs.readFileSync(new URL('../web/src/permission.js', import.meta.url), 'utf8')
function setup({ names = [], resolved = {}, revoke = false, mode = 'medical' } = {}) {
  let guard; let reads = 0
  const authorized = new Set(names)
  const user = { medicalMode: false, medicalPendingNavigation: null, async readMedical() { reads++; this.medicalMode = true; if (revoke) authorized.clear(); return {} } }
  const permission = { isMedicalFrameworkRoute(name) { return authorized.has(name) } }
  const context = { router: { beforeEach(fn) { guard = fn }, afterEach() {}, resolve() { return resolved } },
    NProgress: { configure() {}, start() {}, done() {} }, useUserStore: () => user,
    usePermissionStore: () => permission, useSettingsStore: () => ({ setTitle() {} }),
    useLockStore: () => ({ isLock: false }), getToken: () => null,
    isPathMatch: (pattern, path) => pattern === path, isHttp: () => false, ElMessage: { error() {} }, isRelogin: { show: false } }
  const code = raw.replace(/^import .*$/gm, '').replaceAll('import.meta.env.VITE_APP_ENV', JSON.stringify(mode))
  vm.runInNewContext(code, context)
  return { run: (to) => guard({ fullPath: to.path, meta: {}, ...to }, {}), reads: () => reads }
}
let current = setup({ names: ['SystemUser'], resolved: { name: 'SystemUser', meta: { medicalFramework: true } } })
let result = await current.run({ path: '/system/user' }); assert.equal(result.path, '/system/user'); assert.equal(result.replace, true); assert.equal(current.reads(), 1)
current = setup({ resolved: { name: 'Invented', meta: { medicalFramework: true } } })
result = await current.run({ path: '/system/user' }); assert.equal(result.path, '/access/denied')
current = setup({ names: ['SystemUser'], revoke: true })
result = await current.run({ path: '/system/user', name: 'SystemUser', meta: { medical: true, medicalFramework: true } }); assert.equal(result.path, '/access/denied')
current = setup({ names: ['SystemUser'] })
assert.equal(await current.run({ path: '/system/user', name: 'SystemUser', meta: { medical: true, medicalFramework: true } }), true)
current = setup(); await current.run({ path: '/system-evil' }); assert.equal(current.reads(), 0)
current = setup(); assert.equal(await current.run({ path: '/projects', meta: { medical: true } }), true); assert.equal(current.reads(), 1)
current = setup(); assert.equal(await current.run({ path: '/access', meta: { medical: true, public: true } }), true); assert.equal(current.reads(), 0)
current = setup({ mode: 'production' }); await current.run({ path: '/system/user' }); assert.equal(current.reads(), 0)
console.log('PASS medical framework guard: authorized direct links, refreshed named-route membership, revocation, prefix isolation, normal medical/public and legacy branches. Router/session doubles; no browser or backend claimed.')
