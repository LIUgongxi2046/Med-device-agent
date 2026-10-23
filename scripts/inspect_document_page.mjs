import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '../tests/browser/node_modules/playwright/index.mjs'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const seed = JSON.parse(readFileSync(new URL('../.implementation-work/runtime/acceptance-workspace-seed.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  const page = await context.newPage()
  await page.addInitScript(() => {
    window.addEventListener('unhandledrejection', event => console.error('DOCUMENT_ERROR', event.reason?.message, event.reason?.stack))
    window.addEventListener('medical-failure', event => console.error('DOCUMENT_ERROR', event.detail?.message, event.detail?.stack))
  })
  page.on('console', message => { if (message.text().startsWith('DOCUMENT_ERROR')) console.log(message.text()) })
  page.on('response', async response => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/')) console.log(response.status(), path)
    if (path.endsWith('/events')) {
      const headers = await response.allHeaders()
      console.log('EVENT_HEADERS', JSON.stringify({ content_type: headers['content-type'], cache_control: headers['cache-control'], schema_version: headers['x-schema-version'] }))
      assert.equal(headers['cache-control'], 'no-store')
      assert.equal(headers['x-schema-version'], '1.0.0')
    }
  })
  await page.goto(origin + `/projects/${seed.project_id}/tasks/${seed.task_id}/documents/${seed.document_id}/write`, { waitUntil: 'domcontentloaded', timeout: 90000 })
  await page.getByRole('button', { name: '编辑正文', exact: true }).waitFor({ timeout: 240000 })
  await page.waitForLoadState('networkidle')
  writeFileSync(new URL('../.implementation-work/document-page-check.json', import.meta.url), JSON.stringify({ verified_at: new Date().toISOString(), result: 'PASS', text: await page.locator('.app-main').innerText() }, null, 2))
} finally { await context.close(); await browser.close() }
