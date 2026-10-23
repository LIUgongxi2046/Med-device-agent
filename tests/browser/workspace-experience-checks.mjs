import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = process.argv[2] ?? 'http://127.0.0.1:18083'
assert.ok(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(origin))
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const errors = []
let registrationWrites = 0
try {
  const page = await context.newPage()
  page.setDefaultTimeout(60000)
  page.on('request', incoming => {
    if (incoming.method() === 'POST' && new URL(incoming.url()).pathname === '/api/enterprise-registrations') registrationWrites += 1
  })
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => {
    if (message.text().startsWith('会话错误') || message.text().startsWith('VUE_ERROR')) { errors.push(message.text()); console.log(message.text()) }
  })
  page.on('response', async response => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/') && response.status() >= 400) {
      const failure = await response.json()
      console.log('请求错误', path, response.status(), failure.code, failure.message)
    }
  })
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    window.addEventListener('medical-failure', event => console.log('会话错误', event.detail.code, event.detail.message))
    const app = document.getElementById('app').__vue_app__
    const original = app.config.errorHandler
    app.config.errorHandler = (error, ...arguments_) => {
      if (error.code !== 'ERR_CANCELED') console.log('VUE_ERROR', error.message, error.stack)
      original(error, ...arguments_)
    }
  })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const selector = page.locator('.navbar [data-organization-switcher]')
  await selector.waitFor()
  assert.equal(await page.locator('[data-workspace-navigation] .workspace-navigation__link').count(), 47)
  console.log('顶部组织入口存在')
  const session = async () => {
    const response = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
    assert.equal(response.status(), 200)
    return (await response.json()).data
  }
  const members = (await session()).memberships.filter(member => member.status === 'ACTIVE')
  assert.ok(members.length >= 2, '需要当前账户已有两项有效组织')
  const selectOrganization = async (tenant, expectModelRefresh = false) => {
    await selector.getByRole('combobox', { name: '工作组织', exact: true }).click()
    await page.getByRole('option').filter({ hasText: tenant }).click()
    await selector.getByRole('button', { name: /确认选择组织|切换组织/ }).click()
    await page.getByRole('dialog', { name: '切换工作组织', exact: true }).waitFor()
    const selected = page.waitForResponse(response => new URL(response.url()).pathname === '/api/session/tenant-selections' && response.request().method() === 'POST')
    const refresh = expectModelRefresh ? page.waitForResponse(response => new URL(response.url()).pathname === '/api/model-config-revisions' && response.request().method() === 'GET') : null
    await page.getByRole('dialog', { name: '切换工作组织', exact: true }).getByRole('button', { name: '确认切换', exact: true }).click()
    assert.equal((await selected).status(), 200)
    await selector.locator('[data-current-organization]').filter({ hasText: tenant }).waitFor()
    assert.equal((await session()).current_context.tenant_id, tenant)
    if (refresh) {
      const response = await refresh
      assert.equal(response.status(), 200)
      assert.equal((await response.json()).data.tenant_id, tenant)
      console.log('当前模型页面自动重新读取组织数据')
    }
    console.log('组织切换完成', tenant)
    console.log('当前页面状态', await page.evaluate(() => {
      const stores = document.getElementById('app').__vue_app__.config.globalProperties.$pinia._s
      const user = stores.get('user')
      return { loading: user.medicalLoading, writing: user.medicalWriting, session: Boolean(user.medicalSession), error: user.medicalError?.message,
        routes: stores.get('permission').sidebarRouters.length }
    }))
  }
  await selectOrganization(members[0].tenant_id)
  await page.evaluate(() => {
    const sidebar = document.querySelector('.sidebar-container')
    window.__navigationEvidence = { root: sidebar, title: sidebar.querySelector('.el-sub-menu__title'), minimum: sidebar.querySelectorAll('.el-sub-menu__title').length, replaced: false }
    window.__navigationObserver = new MutationObserver(() => {
      const evidence = window.__navigationEvidence
      evidence.minimum = Math.min(evidence.minimum, sidebar.querySelectorAll('.el-sub-menu__title').length)
      if (!evidence.root.isConnected || !evidence.title.isConnected) evidence.replaced = true
    })
    window.__navigationObserver.observe(document.getElementById('app'), { subtree: true, childList: true })
  })
  const visit = async (group, label, path) => {
    const sidebar = page.locator('.sidebar-container')
    const title = sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: group })
    if (await title.getAttribute('aria-expanded') !== 'true') await title.click()
    await sidebar.getByText(label, { exact: true }).click()
    await page.waitForURL(url => url.pathname === path)
    await sidebar.locator('.el-menu-item.is-active').getByText(label, { exact: true }).waitFor()
    await selector.getByRole('combobox').waitFor({ state: 'visible' })
    assert.equal((await session()).current_context.tenant_id, members[0].tenant_id)
    assert.equal(await page.locator('.app-main [role="combobox"][aria-label="工作组织"]').count(), 0)
    console.log('菜单访问完成', path)
  }
  await visit('项目与业务', '项目中心', '/projects')
  await visit('资料与文稿', '资料中心', '/library')
  await visit('Agent 与模型', '模型配置草稿', '/model-configurations')
  const stable = await page.evaluate(() => {
    window.__navigationObserver.disconnect()
    const evidence = window.__navigationEvidence
    return { minimum: evidence.minimum, replaced: evidence.replaced, connected: evidence.title.isConnected }
  })
  assert.equal(stable.replaced, false, '菜单切换应保留侧栏DOM')
  assert.ok(stable.minimum >= 8)
  await selectOrganization(members[1].tenant_id)
  await page.waitForURL(url => url.pathname === '/model-configurations')
  assert.equal(await page.getByRole('button', { name: '查询配置', exact: true }).isDisabled(), true, '当前实际组织没有配置读取授权')
  assert.equal(await page.getByRole('button', { name: '新建草稿', exact: true }).isDisabled(), true, '模型维护继续使用实际组织授权')
  await selectOrganization(members[0].tenant_id, true)
  const modelRequest = page.waitForResponse(response => new URL(response.url()).pathname === '/api/model-config-revisions' && response.request().method() === 'GET')
  const [models] = await Promise.all([modelRequest, page.getByRole('button', { name: '查询配置', exact: true }).click()])
  assert.equal(models.status(), 200)
  const currentContext = (await session()).current_context
  assert.equal(models.request().headers()['x-context-revision'], currentContext.context_revision)
  assert.equal((await models.json()).data.tenant_id, members[0].tenant_id)
  await page.locator('.sidebar-container .el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^企业与协作$/ }).click()
  await page.locator('.sidebar-container').getByText('企业登记申请', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/enterprises/registrations')
  const editor = page.locator('[data-enterprise-registration-form]')
  await editor.waitFor()
  const sections = await editor.locator('.el-form-item').count()
  assert.equal(sections, 6)
  await editor.getByRole('button', { name: '保存企业登记申请', exact: true }).click()
  assert.ok(await editor.locator('.el-form-item.is-error').count() > 0, '空表单需要显示实际字段校验')
  assert.equal(await editor.locator('.el-form-item__error').evaluateAll(elements => elements.every(element => getComputedStyle(element).position === 'static')), true)
  const firstField = editor.locator('.el-form-item').filter({ hasText: '企业法定名称' }).getByRole('textbox')
  await firstField.focus()
  await firstField.press('Tab')
  assert.equal(await editor.evaluate(element => element.contains(document.activeElement)), true)
  const widths = []
  for (const width of [1440, 820, 375]) {
    await page.setViewportSize({ width, height: 1000 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, String(width))
    assert.equal(await selector.isVisible(), true)
    await page.waitForFunction(() => document.querySelector('.app-main').getBoundingClientRect().top >= document.querySelector('.fixed-header').getBoundingClientRect().bottom - 1)
    widths.push(width)
  }
  const theme = await editor.evaluate(element => ({ color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor }))
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  const darkTheme = await editor.evaluate(element => ({ color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor }))
  assert.notEqual(theme.color, darkTheme.color)
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.locator('.app-wrapper:not(.mobile)').waitFor()
  if (await page.locator('.sidebar-container .el-menu--collapse').count()) await page.locator('#hamburger-container').click()
  await page.locator('.sidebar-container .el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^系统管理$/ }).click()
  const usersResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/system/user/list')
  await page.locator('.sidebar-container').getByText('用户管理', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/system/user')
  const users = await usersResponse
  assert.equal(users.status(), 200)
  assert.equal(users.request().headers()['x-context-revision'], (await session()).current_context.context_revision)
  await page.locator('.navbar .avatar-container').click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/access')
  const revoked = await page.evaluate(() => {
    const stores = document.getElementById('app').__vue_app__.config.globalProperties.$pinia._s
    return { routes: stores.get('permission').sidebarRouters.length, permissions: stores.get('user').permissions.length,
      registered: stores.get('permission').isMedicalFrameworkRoute('User') }
  })
  assert.deepEqual(revoked, { routes: 0, permissions: 0, registered: false })
  assert.equal((await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).status(), 401)
  assert.deepEqual(errors, [])
  assert.equal(registrationWrites, 0, '表单空值校验不能创建业务申请')
  const result = { verified_at: new Date().toISOString(), organizations: members.slice(0, 2).map(member => member.tenant_id), global_organization: true,
    stable_sidebar: stable, verified_registration_fields: sections, model_read_and_permission_states: true, viewport_widths: widths, theme, darkTheme,
    native_context_verified: true, logout_revoked: revoked, registration_writes: registrationWrites, errors, screenshots: false }
  writeFileSync(new URL(`../../.implementation-work/workspace-experience-${origin.endsWith(':18083') ? 'browser' : 'production'}.json`, import.meta.url), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally { await context.close(); await browser.close() }
