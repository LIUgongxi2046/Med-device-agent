import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
import { brand } from '../../web/branding.mjs'

const origin = 'http://127.0.0.1:18083'
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
assert.equal(credentials.username, 'liuhaonan')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const checks = []
const errors = []
const network = []
try {
  const page = await context.newPage()
  page.setDefaultTimeout(60000)
  page.on('pageerror', error => errors.push(error.message))
  page.on('requestfailed', request => network.push({ path: new URL(request.url()).pathname, failure: request.failure() }))
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.getByRole('button', { name: '填入登录表单', exact: true }).waitFor()
  assert.equal(await page.locator('[data-local-owner-username]').textContent(), credentials.username)
  assert.ok((await page.title()).includes(brand.chineseName))
  assert.equal(await page.locator('.login-brand__suffix').textContent(), brand.englishName)
  assert.equal(await page.locator('.login-brand img').getAttribute('src'), brand.logo)
  assert.equal(await page.locator('link[rel="icon"]').getAttribute('href'), brand.logo)
  assert.equal((await context.request.get(origin + brand.logo)).status(), 200)
  assert.equal(await page.locator('.login-brand img').evaluate(image => image.complete && image.naturalWidth > 0), true)
  checks.push('账号、完整品牌名称、登录Logo与页签资源')
  assert.notEqual(await page.locator('[data-local-owner-password]').textContent(), credentials.password)
  await page.getByRole('button', { name: '显示超管密码', exact: true }).click()
  assert.equal(await page.locator('[data-local-owner-password]').textContent(), credentials.password)
  await page.getByRole('button', { name: '隐藏超管密码', exact: true }).click()
  checks.push('密码显示控制')
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.getByText('请输入您的账号', { exact: true }).waitFor()
  await page.getByText('请输入您的密码', { exact: true }).waitFor()
  checks.push('空表单校验')
  for (const width of [375, 820, 1440]) {
    await page.setViewportSize({ width, height: 1000 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, String(width))
    assert.ok(await page.locator('.login-brand img').evaluate(image => image.getBoundingClientRect().width >= 40))
  }
  checks.push('375、820、1440px登录页面布局')
  await page.evaluate(() => { document.documentElement.style.zoom = '2' })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  await page.evaluate(() => { document.documentElement.style.zoom = '' })
  checks.push('200%页面缩放')
  for (let attempt = 1; attempt <= 2; attempt++) {
    await page.getByRole('button', { name: '填入登录表单', exact: true }).click()
    assert.equal(await page.getByRole('textbox', { name: '用户名', exact: true }).inputValue(), credentials.username)
    const response = page.waitForResponse(value => value.url() === origin + '/auth/login' && value.request().method() === 'POST')
    const started = Date.now()
    await page.getByRole('button', { name: '登录工作台', exact: true }).click()
    assert.equal((await response).status(), 302)
    await page.waitForURL(url => url.pathname === '/index', { timeout: 90000, waitUntil: 'domcontentloaded' })
    await page.locator('.workbench').getByRole('heading', { name: brand.chineseName, exact: true }).waitFor()
    assert.equal(await page.locator('.workbench__eyebrow').textContent(), brand.englishName)
    assert.equal(await page.locator('.workbench__brand img').getAttribute('src'), brand.logo)
    assert.equal(await page.locator('.sidebar-logo').first().getAttribute('src'), brand.logo)
    await page.locator('.hamburger-container').click()
    await page.locator('.sidebar-logo-container.collapse').waitFor()
    assert.equal(await page.locator('.sidebar-logo-container.collapse img').getAttribute('src'), brand.logo)
    await page.locator('.hamburger-container').click()
    await page.locator('.sidebar-logo-container:not(.collapse)').waitFor()
    const sessionResponse = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
    assert.equal(sessionResponse.status(), 200)
    const session = (await sessionResponse.json()).data
    assert.equal(session.user.username, 'liuhaonan')
    assert.equal(session.user.platform_role, 'PLATFORM_ADMIN')
    assert.equal(await page.evaluate(secret => [localStorage, sessionStorage].every(storage =>
      Object.keys(storage).every(key => !storage.getItem(key).includes(secret))), credentials.password), true)
    checks.push({ check: '真实表单登录、首页与导航Logo、会话身份', attempt, milliseconds: Date.now() - started })
    const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
    assert.equal((await context.request.post(origin + '/auth/logout', { headers: { Origin: origin, [csrf.header_name]: csrf.token } })).status(), 204)
    await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '填入登录表单', exact: true }).waitFor()
  }
  assert.deepEqual(errors, [])
  assert.deepEqual(network, [])
  const result = { verified_at: new Date().toISOString(), checks, page_errors: errors, failed_requests: network,
    browser: browser.version(), visual_review: 'NOT_RUN', screenshots: false }
  writeFileSync(new URL('../../.implementation-work/login-brand-browser.json', import.meta.url), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally { await context.close(); await browser.close() }
