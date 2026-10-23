import test from 'node:test'
import assert from 'node:assert/strict'
import { activeLibrarySection, libraryLocation } from '../../src/components/Medical/navigation.js'
import { prepareMedicalFrameworkRoutes, createMedicalFrameworkRegistry, isMedicalFrameworkPath } from '../../src/components/Medical/framework-routes.js'
const server = [{ path: '/system', name: 'System', component: 'Layout', children: [
  { path: 'user', name: 'User', component: 'system/user/index', meta: { title: '用户管理', public: true, link: 'https://invalid.example' } }
] }]
const view = name => name === 'system/user/index'
// 业务分类通过 tests/browser/business-menu-checks.mjs 使用实际服务验证。
test('library sections retain explicit project scope and discard fixed-search history', () => {
  assert.equal(activeLibrarySection('/library/search/exact'), '/library/search')
  assert.equal(activeLibrarySection('/library/collections/new'), '/library/collections')
  assert.equal(activeLibrarySection('/library/versions/one'), '/library')
  assert.deepEqual(libraryLocation('/library/processing', { project_id: 'project', retrieval_id: 'old', page: '2' }), { path: '/library/processing', query: { project_id: 'project' } })
})
test('framework routes receive private medical markers and no external metadata', () => {
  const [root] = prepareMedicalFrameworkRoutes(server, [], view)
  assert.deepEqual(root.children[0].meta, { title: '用户管理', icon: 'system', medical: true, medicalFramework: true, public: false, noCache: true })
  assert.equal(isMedicalFrameworkPath('/systematic'), false)
})
test('rejects reserved collisions, unsafe paths, aliases and unknown components', () => {
  assert.equal(prepareMedicalFrameworkRoutes(server, [{ name: 'System', path: '/reserved' }], view).length, 0)
  assert.equal(prepareMedicalFrameworkRoutes(server, [{ name: 'Other', path: '/system' }], view).length, 0)
  for (const path of ['/projects', '//system', '/system/../projects', '/system/%2e%2e/projects', 'https://host/system']) {
    assert.equal(prepareMedicalFrameworkRoutes([{ ...server[0], path }], [], view).length, 0)
  }
  assert.equal(prepareMedicalFrameworkRoutes([{ ...server[0], alias: '/projects' }], [], view).length, 0)
  assert.equal(prepareMedicalFrameworkRoutes(server, [], () => false).length, 0)
  assert.equal(prepareMedicalFrameworkRoutes([...server, ...server], [], view).length, 1)
})
test('revocation removes registered routes and remembered names, no client-only grant', () => {
  const installed = new Set()
  const registry = createMedicalFrameworkRegistry({ addRoute(route) { installed.add(route.name); return () => installed.delete(route.name) } })
  registry.replace(prepareMedicalFrameworkRoutes(server, [], view))
  assert.equal(registry.has('User'), true)
  assert.equal(registry.has('MedicalProjects'), false)
  registry.replace([])
  assert.equal(registry.has('User'), false)
  assert.equal(installed.size, 0)
  registry.replace(prepareMedicalFrameworkRoutes(server, [], view)); registry.clear()
  assert.equal(registry.has('System'), false)
})
