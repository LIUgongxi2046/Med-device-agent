import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { runnerImport } from '../web/node_modules/vite/dist/node/index.js'

const root = fileURLToPath(new URL('../web/', import.meta.url))
const { module: protocol } = await runnerImport(fileURLToPath(new URL('../web/src/api/medical/protocol.ts', import.meta.url)), { root, mode: 'medical' })
  const references = [
    'urn:medical:tasks:1.0.0#/$defs/Create',
    'urn:medical:task-create-view:1.0.0',
    'urn:medical:task-create-draft:1.0.0',
    'urn:medical:task-creation-selection:1.0.0#/$defs/Owners',
    'urn:medical:task-scope-selection:1.0.0#/$defs/Page',
    ...['Query', 'Page'].map(name => 'urn:medical:scenario-catalogue:1.0.0#/$defs/' + name),
    'urn:medical:tasks:1.0.0#/$defs/PlanVersionInput',
    'urn:medical:task-plan-view:1.0.0#/$defs/View',
    'urn:medical:task-plan-view:1.0.0#/$defs/Basis',
    'urn:medical:task-plan-write-view:1.0.0',
    ...['Baseline', 'Request', 'Receipt'].map(name => 'urn:medical:task-plan-decision:1.0.0#/$defs/' + name),
    'urn:medical:task-plan-return-view:1.0.0'
  ]
  for (const reference of references) {
    const issues = protocol.schemaIssues(reference, {})
    assert.ok(issues.length > 0, '计划输入需要拒绝缺少全部字段的对象')
    assert.ok(issues.some(issue => issue.code === 'required'), '计划定义需要实际编译全部必填字段')
  }
  assert.equal(protocol.schemaIssues('urn:medical:task-plan-write-view:1.0.0', null).length > 0, true)
  process.stdout.write(JSON.stringify({ checks: references.length + 1, frontend_schema_runtime_verified: true,
    full_plan_business_verified: false, visual_verified: false }) + '\n')
