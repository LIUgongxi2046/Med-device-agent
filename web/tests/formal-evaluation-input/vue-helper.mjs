import {createRequire} from 'node:module'
import {readFileSync} from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import {webRoot} from '../formal-capability/helpers.mjs'
const require=createRequire(import.meta.url)
export function setupVue(name, overrides = {}) {
 const vue = require('vue'), ts = require('typescript'), { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
 const filename = path.join(webRoot, 'src/views/medical', name + '.vue'), source = readFileSync(filename, 'utf8'), { descriptor } = parse(source, { filename })
 const compiled = compileScript(descriptor, { id: name }), template = compileTemplate({ source: descriptor.template.content, filename, id: name, compilerOptions: { bindingMetadata: compiled.bindings } })
 if (template.errors.length) throw Error(template.errors.join('\n'))
 const code = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
 const exports = {}, scope = vue.effectScope(), cleanups = [], listeners = new Map(), doc = { hidden: false, addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }
 const user = overrides.user ?? vue.reactive({ medicalSession: null, medicalWriting: false }), route = overrides.route ?? vue.reactive({ params: {} }), routes = []
 let guard
 const dependencies = { '@/store/modules/user': { default: () => user }, 'vue-router': { useRouter: () => ({ push: async value => { routes.push(value) } }), useRoute: () => route, onBeforeRouteLeave: fn => { guard = fn } }, ...overrides.modules }
 vm.runInNewContext(code, { exports, require: target => target === 'vue' ? { ...vue, onBeforeUnmount: fn => cleanups.push(fn) } : dependencies[target] ?? {}, console, AbortController, TextEncoder,
  document: doc, window: { addEventListener: (name,fn) => listeners.set(name,fn), removeEventListener: name => listeners.delete(name) }, HTMLInputElement: class {} })
 const emissions = []; const state = scope.run(() => exports.default.setup(overrides.props ?? {}, { expose() {}, emit: (...args) => emissions.push(args) }))
 return { state, user, route, doc, listeners, routes, emissions, guard: (...args) => guard?.(...args), stop: () => { for (const fn of cleanups) fn(); scope.stop() } }
}
