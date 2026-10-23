import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const pages = []
const failures = []
let activePath = '/access'
try {
  const page = await context.newPage()
  await page.addInitScript(() => window.addEventListener('medical-failure', event => console.error('PAGE_READ_FAILURE', JSON.stringify({ code: event.detail?.code, message: event.detail?.message, stack: event.detail?.stack }))))
  page.on('console', message => { if (message.text().startsWith('PAGE_READ_FAILURE')) console.log(activePath, message.text()) })
  const reading = new Set()
  page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) reading.add(request) })
  page.on('requestfinished', request => reading.delete(request))
  page.on('requestfailed', request => reading.delete(request))
  async function settledReads() {
    const started = Date.now()
    while (Date.now() - started < 120000) {
      await page.waitForTimeout(250)
      if (reading.size) continue
      await page.waitForTimeout(500)
      if (!reading.size) return
    }
    assert.fail('当前页面接口读取没有在验收期限内完成：' + activePath)
  }
  page.setDefaultTimeout(90000)
  page.on('pageerror', error => failures.push({ path: activePath, type: 'PAGE_ERROR', message: error.message }))
  page.on('response', response => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/') && response.status() >= 400) failures.push({ path: activePath, type: 'API_RESPONSE', endpoint: url.pathname, status: response.status() })
  })
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
  await page.getByLabel('密码', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const entries = await page.locator('[data-workspace-navigation] .workspace-navigation__link').evaluateAll(elements => elements.map(element => ({ path: element.getAttribute('href'), title: element.textContent.trim() })))
  assert.ok(entries.length >= 40)
  for (const entry of entries) {
    activePath = entry.path
    const navigation = await page.evaluate(async path => {
      const app = document.getElementById('app').__vue_app__
      const router = app.config.globalProperties.$router
      const result = await Promise.allSettled([router.push(path)])
      return result.map(item => ({ status: item.status, message: item.status === 'rejected' ? item.reason.message : null }))
    }, entry.path)
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    await page.waitForFunction(() => !document.querySelector('.fade-transform-leave-active,.fade-transform-enter-active'))
    await page.waitForLoadState('networkidle')
    await settledReads()
    const actual = new URL(page.url()).pathname
    const content = await page.locator('.app-main').count() ? page.locator('.app-main') : page.locator('main')
    await content.first().waitFor()
    const headings = await content.locator('h1,h2,h3').allTextContents()
    const buttons = await content.getByRole('button').evaluateAll(elements => elements.map(element => ({ name: element.textContent.trim(), disabled: element.disabled })))
    const inputs = await content.locator('input,textarea,select').evaluateAll(elements => elements.map(element => ({ type: element.type, placeholder: element.getAttribute('placeholder'), disabled: element.disabled })))
    const state = await page.evaluate(() => {
      const user = document.getElementById('app').__vue_app__.config.globalProperties.$pinia._s.get('user')
      return { session: Boolean(user.medicalSession), context: user.medicalSession?.current_context?.tenant_id, error: user.medicalError?.code }
    })
    const content_text = await content.innerText()
    const frames = []
    for (const frame of page.frames().slice(1)) {
      if (frame.url() === 'about:blank') { frames.push({ url: frame.url(), status: 'EMPTY_FRAME' }); continue }
      const address = new URL(frame.url())
      if (!['http://127.0.0.1:18083', 'http://127.0.0.1:18080'].includes(address.origin)) { frames.push({ url: address.origin + address.pathname, status: 'OUTSIDE_REGISTERED_LOCAL_SERVICES' }); continue }
      frames.push({ url: address.origin + address.pathname, content_text: await frame.locator('body').innerText({ timeout: 20000 }), status: 'FRAME_DOM_READ' })
    }
    const result = { ...entry, actual, navigation, headings, buttons, inputs, content_text, frames, state, scope: 'ENTRY_RENDER_AND_AUTHORIZATION', business_complete: 'REQUIRES_ACTION_EVIDENCE' }
    pages.push(result)
    console.log(JSON.stringify({ path: entry.path, actual, headings: headings.length, buttons: buttons.length, state }))
    writeFileSync(new URL('../../.implementation-work/all-pages-acceptance.json', import.meta.url), JSON.stringify({ inspected_at: new Date().toISOString(), pages, failures, screenshots: false }, null, 2))
  }
  assert.equal(pages.length, entries.length)
  assert.deepEqual(pages.filter(page => !page.content_text.trim() && !page.frames.some(frame => frame.status === 'FRAME_DOM_READ' && frame.content_text.trim())).map(page => page.path), [])
  assert.deepEqual(pages.filter(page => page.actual !== page.path || page.state.error || !page.state.session).map(page => ({ path: page.path, actual: page.actual, state: page.state })), [])
  assert.deepEqual(failures, [])
} finally {
  await context.close()
  await browser.close()
}
