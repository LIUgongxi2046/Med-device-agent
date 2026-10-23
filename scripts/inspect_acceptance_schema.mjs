import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '../tests/browser/node_modules/playwright/index.mjs'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  const page = await context.newPage()
  await page.goto(origin + '/index', { waitUntil: 'networkidle', timeout: 90000 })
  const result = await page.evaluate(async () => {
    const { schemaIssues } = await import('/src/api/medical/protocol.ts')
    const state = await (await fetch('/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()
    const headers = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': state.data.current_context.context_revision }
    const results = []
    for (const [path, reference] of [
      ['/api/source-versions?limit=25&lifecycle=ACTIVE', 'urn:medical:source-governance:1.0.0#/$defs/Listing'],
      ['/api/source-folders', 'urn:medical:source-governance:1.0.0#/$defs/FolderList'],
      ['/api/tasks/0ea64430-30e7-4694-90ec-ff98cc5daf2d', 'https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml#/$defs/TaskDetail'],
      ['/api/documents/123d7bbf-a1f5-465a-a00c-83da28e1ba76', 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/DocumentSnapshot'],
      ['/api/runs/bdd4f101-4f1a-49cc-9ca0-630b25a4ff28', 'urn:medical:run-view:1.0.0#/$defs/View']
    ]) {
      const response = await fetch(path, { headers }); const body = await response.json()
      results.push({ path, status: response.status, issues: schemaIssues(reference, body.data), value: body.data })
    }
    const { createParser } = await import('/node_modules/eventsource-parser/dist/index.js')
    const stream = await fetch('/api/runs/bdd4f101-4f1a-49cc-9ca0-630b25a4ff28/events', { headers, signal: AbortSignal.timeout(15000) })
    const reader = stream.body.getReader(), decoder = new TextDecoder('utf-8', { fatal: true })
    let message = null
    const parser = createParser({ onEvent: value => { message = value }, onError: error => { throw error } })
    while (message === null) { const value = await reader.read(); if (value.done) throw new Error('运行事件连接未提供持久帧'); parser.feed(decoder.decode(value.value, { stream: true })) }
    await reader.cancel()
    const event = JSON.parse(message.data)
    results.push({ path: '/api/runs/events', status: stream.status, issues: schemaIssues('https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/PublicEvent', event), value: event })
    const { readSavedDocument } = await import('/src/api/medical/documents.ts')
    const document = await readSavedDocument(state.data, 'd783e1d9-33d7-4532-bdbe-1de108698e17', '123d7bbf-a1f5-465a-a00c-83da28e1ba76', { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }, AbortSignal.timeout(90000))
    results.push({ path: 'readSavedDocument', status: 200, issues: [], value: { revision: document.snapshot.document_revision, nodes: document.body.nodes.length } })
    return results
  })
  writeFileSync(new URL('../.implementation-work/source-catalogue-schema-diagnostic.json', import.meta.url), JSON.stringify(result, null, 2), { mode: 0o600 })
  console.log(JSON.stringify(result.map(({ path, status, issues }) => ({ path, status, issues }))))
} finally { await context.close(); await browser.close() }
