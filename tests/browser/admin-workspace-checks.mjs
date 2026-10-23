import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = process.argv[2] ?? 'http://127.0.0.1:18083'
assert.ok(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(origin))
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const errors = []
try {
  const page = await context.newPage()
  page.setDefaultTimeout(60000)
  page.on('console', message => { if (message.text().startsWith('VUE_ERROR')) errors.push(message.text()) })
  page.on('pageerror', error => { errors.push(error.message); console.log('PAGE_ERROR', error.message) })
  page.on('response', async response => {
    if (new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400) console.log('API_FAILURE', new URL(response.url()).pathname, response.status(), await response.text())
  })
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.getByRole('button', { name: '登录工作台', exact: true }).waitFor()
  await page.evaluate(() => {
    const app = document.getElementById('app').__vue_app__
    const original = app.config.errorHandler
    app.config.errorHandler = (error, ...arguments_) => { console.log('VUE_ERROR', error.message, error.stack); original(error, ...arguments_) }
  })
  if (origin.endsWith(':18083')) await page.getByRole('button', { name: '填入登录表单', exact: true }).click()
  else {
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
    await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  }
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index', { waitUntil: 'domcontentloaded', timeout: 90000 })
  const navigationResponse = await context.request.get(origin + '/api/framework/navigation', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(navigationResponse.status(), 200)
  const navigation = (await navigationResponse.json()).data
  assert.ok(navigation.roles.includes('admin'), '超管需要真实若依admin角色')
  assert.ok(navigation.permissions.includes('*:*:*'), '超管需要完整框架权限')
  await page.getByRole('heading', { name: '功能导航', exact: true }).waitFor()
  const links = await page.locator('[data-workspace-navigation] .workspace-navigation__link').evaluateAll(elements => elements.map(element => element.getAttribute('href')))
  assert.ok(links.length > 30)
  assert.equal(new Set(links).size, links.length, '功能入口必须唯一')
  assert.ok(links.every(path => path.startsWith('/') && !path.includes(':')), '功能入口需要能够直接打开')
  await page.locator('.sidebar-container .el-sub-menu__title').filter({ hasText: /^系统管理$/ }).click()
  await page.locator('.sidebar-container').getByText('用户管理', { exact: true }).waitFor()
  assert.ok(await page.locator('.tags-view-container').isVisible())
  const light = await page.locator('.workbench').evaluate(element => ({ color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor }))
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  const dark = await page.locator('.workbench').evaluate(element => ({ color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor }))
  assert.notEqual(light.color, dark.color, '深色主题必须使用对应字体颜色')
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  for (const width of [375, 820, 1440]) {
    await page.setViewportSize({ width, height: 1000 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, String(width))
    await page.getByRole('heading', { name: '功能导航', exact: true }).waitFor()
  }
  const workspace = page.locator('.navbar [data-organization-switcher]')
  await workspace.locator('.el-select').click()
  await page.locator('.el-select-dropdown__item:visible').first().click()
  await workspace.getByRole('button', { name: '确认选择组织', exact: true }).click()
  const selection = page.waitForResponse(response => response.url() === origin + '/api/session/tenant-selections' && response.request().method() === 'POST')
  const projects = page.waitForResponse(response => new URL(response.url()).pathname === '/api/projects' && response.request().method() === 'GET')
  await page.getByRole('dialog', { name: '切换工作组织', exact: true }).getByRole('button', { name: '确认切换', exact: true }).click()
  assert.equal((await selection).status(), 200)
  const projectResponse = await projects
  assert.equal(projectResponse.status(), 200)
  const projectPage = (await projectResponse.json()).data
  await page.getByRole('heading', { name: '重点项目', exact: true }).waitFor()
  await page.locator('.metric-card strong').first().filter({ hasText: String(projectPage.total) }).waitFor()
  const users = page.waitForResponse(response => new URL(response.url()).pathname === '/api/system/user/list')
  await page.locator('[data-workspace-navigation]').getByText('用户管理', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/system/user', { waitUntil: 'domcontentloaded' })
  assert.equal((await users).status(), 200)
  await page.locator('.el-table__body').getByText('liuhaonan', { exact: true }).first().waitFor()
  await page.locator('.tags-view-item[data-path="/index"]').click()
  await page.getByRole('heading', { name: '功能导航', exact: true }).waitFor()
  const sessionResponse = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(sessionResponse.status(), 200)
  const session = (await sessionResponse.json()).data
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/logout', { headers: { Origin: origin, [csrf.header_name]: csrf.token } })).status(), 204)
  assert.deepEqual(errors, [])
  const result = { verified_at: new Date().toISOString(), role: navigation.roles, permissions: navigation.permissions,
    framework_groups: navigation.routers.length, memberships: session.memberships.length, function_count: links.length,
    project_total: projectPage.total, native_user_management: true, tags_navigation: true, light, dark,
    responsive_widths: [375, 820, 1440], page_errors: errors, screenshots: false }
  writeFileSync(new URL(`../../.implementation-work/home-admin-${origin.endsWith(':18083') ? 'browser' : 'production'}.json`, import.meta.url), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally { await context.close(); await browser.close() }
