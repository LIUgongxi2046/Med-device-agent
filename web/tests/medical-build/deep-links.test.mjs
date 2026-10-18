import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
const root=path.resolve(import.meta.dirname,'../../..')
const require=createRequire(path.join(root,'web/package.json'))
const {parse}=require('@babel/parser')
test('every medical business leaf route has an exact server SPA entry, including optional parameters',()=>{
  const ast=parse(fs.readFileSync(path.join(root,'web/src/router/index.js'),'utf8'),{sourceType:'module'})
  const array=ast.program.body.find(n=>n.type==='ExportNamedDeclaration'&&n.declaration?.declarations?.some(d=>d.id.name==='medicalRoutes')).declaration.declarations.find(d=>d.id.name==='medicalRoutes').init
  const routes=[]
  function visit(nodes,parent='') {
    for(const n of nodes){
      const props=Object.fromEntries(n.properties.map(p=>[p.key.name,p.value]))
      const value=props.path?.value
      assert.equal(typeof value,'string','medical path must be literal')
      const full=(value.startsWith('/')?value:parent+'/'+value).replace(/\/$/,'')||'/'
      if(props.children)visit(props.children.elements,full)
      else if(/\/:\w+\?$/.test(full)) {
        routes.push(full.replace(/\/:\w+\?$/,''))
        routes.push(full.replace(/:(\w+)\?/g,'{$1}'))
      } else routes.push(full.replace(/:(\w+)/g,'{$1}'))
    }
  }
  visit(array.elements)
  const java=fs.readFileSync(path.join(root,'business/medical-identity/src/main/java/com/ruoyi/medical/identity/FrameworkFrontendConfiguration.java'),'utf8')
  const entries=new Set([...java.slice(java.indexOf('ROUTES = List.of('),java.indexOf('\n    );')).matchAll(/"([^"\n]+)"/g)].map(m=>m[1]))
  assert.ok(routes.length>=50)
  for(const route of routes)assert.ok(entries.has(route),'Missing server refresh entry: '+route)
  for(const entry of entries)assert.ok(!entry.startsWith('/api/')&&!entry.includes('*'),'SPA entries must remain exact UI routes')
})
