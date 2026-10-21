// Deferred regression assertions; no tests were executed during the compatibility repair.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import test from 'node:test'
import { loadProject } from '../src/load-project.js'
import { sha256 } from '../src/catalogue.js'

const root = resolve('../..')
test('legacy publication stays byte-identical while application namespace is additive', async () => {
  assert.equal(await sha256(await readFile(resolve(root, 'contracts/catalogue.json'))),
    'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915')
  assert.equal(await sha256(await readFile(resolve(root, 'business/medical-persistence/src/main/resources/template-applicability.schema.json'))),
    '0c304401621f198af342a823fa51b826a101b2e07ff616d09d3a1f190b01f498')
  const catalogue = await loadProject(root)
  assert.equal(catalogue.schemaRef('template-application','FixedView'), 'urn:medical:template-application:1.0.0#/$defs/FixedView')
  const legacy = JSON.parse(await readFile(resolve(root,'business/medical-persistence/src/main/resources/template-applicability.schema.json'),'utf8'))
  const application = JSON.parse(await readFile(resolve(root,'business/medical-persistence/src/main/resources/template-application.schema.json'),'utf8'))
  assert.equal(legacy.$defs.FixedView, undefined)
  assert.equal(legacy.$defs.TemplateView.properties.human_decisions, undefined)
  assert.ok(application.$defs.TemplateView.required.includes('human_decisions'))
  assert.ok(application.$defs.TemplateView.required.includes('machine_conditions'))
  assert.equal(application.$defs.HumanDecisionReceipt.properties.professional_approval.const,false)
})
