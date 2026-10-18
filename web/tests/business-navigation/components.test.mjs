import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const components = [
  'views/medical/project.vue', 'views/medical/session-panel.vue',
  'views/medical/source-catalogue.vue', 'views/medical/source-processing.vue',
  'views/medical/artifact-catalogue.vue', 'views/medical/collections.vue',
  'views/medical/source-candidates.vue', 'components/Medical/LibrarySections.vue',
  'components/Medical/MobileNavigation.vue', 'components/Medical/ProjectScopeSelect.vue',
  'layout/components/Sidebar/index.vue', 'layout/components/Navbar.vue', 'layout/components/AppMain.vue'
]
for (const component of components) test(`compiles business navigation component: ${component}`, () => {
  const filename = new URL('../../src/' + component, import.meta.url).pathname
  const parsed = parse(readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(parsed.errors, [])
  const script = compileScript(parsed.descriptor, { id: component })
  const template = compileTemplate({ source: parsed.descriptor.template.content, filename, id: component, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
})
