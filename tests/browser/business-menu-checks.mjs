import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = process.argv[2] ?? 'http://127.0.0.1:18083'
assert.ok(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(origin))
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const baselinePath = new URL('../../.implementation-work/business-menu-baseline.json', import.meta.url)
const expectedGroups = ['项目与业务', '资料与文稿', 'Agent 与模型', '评测与发布', '企业与协作', '系统管理', '运行监控', '开发工具']
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const errors = []
try {
  const page = await context.newPage()
  page.setDefaultTimeout(60000)
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  console.log('登录完成')
  await page.getByRole('heading', { name: '功能导航', exact: true }).waitFor()
  const functions = await page.locator('[data-workspace-navigation] .workspace-navigation__link').evaluateAll(elements => elements.map(element => element.getAttribute('href')).sort())
  assert.equal(functions.length, 47)
  assert.equal(new Set(functions).size, 47)
  if (!existsSync(baselinePath)) writeFileSync(baselinePath, JSON.stringify(functions, null, 2))
  assert.deepEqual(functions, JSON.parse(readFileSync(baselinePath)), '当前已有功能必须完整保留')
  const sidebar = page.locator('.sidebar-container')
  const groups = await sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title .menu-title').allTextContents()
  assert.deepEqual(groups, expectedGroups, '侧栏需要完整业务分类')
  console.log('分类与47项功能检查通过')
  const memberships = {
    '项目与业务': ['项目中心', '场景目录', '客户系统连接'],
    '资料与文稿': ['资料中心', '上传企业资料', '模板人工审查', '规则管理与人工审查', '文稿保存审计'],
    'Agent 与模型': ['Agent 管理', '执行策略', '模型预算'],
    '评测与发布': ['评测与结果', '模拟评测准备', '正式验收与发布'],
    '企业与协作': ['企业成员与授权', '企业岗位', '本人账户'],
    '系统管理': ['用户管理', '角色管理'],
    '运行监控': ['在线用户', '服务监控'],
    '开发工具': ['代码生成', '系统接口']
  }
  for (const [title, labels] of Object.entries(memberships)) {
    const group = sidebar.locator('.el-menu > .el-sub-menu').filter({ has: page.locator('.el-sub-menu__title .menu-title').getByText(title, { exact: true }) })
    await group.locator(':scope > .el-sub-menu__title').click()
    for (const label of labels) assert.equal(await group.getByText(label, { exact: true }).isVisible(), true, title + '/' + label)
    console.log('分类检查', title)
  }
  const sidebarLinks = await sidebar.locator('.el-menu a[href]').evaluateAll(elements => elements.map(element => element.getAttribute('href')).sort())
  assert.deepEqual(sidebarLinks, functions, '侧栏与首页包含相同功能')
  await sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^系统管理$/ }).click()
  const users = page.waitForResponse(response => new URL(response.url()).pathname === '/api/system/user/list')
  await sidebar.getByText('用户管理', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/system/user')
  assert.equal((await users).status(), 200)
  await sidebar.locator('.el-menu-item.is-active').getByText('用户管理', { exact: true }).waitFor()
  assert.equal(await sidebar.locator('.el-sub-menu.is-active.is-opened > .el-sub-menu__title').textContent(), '系统管理')
  await sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^评测与发布$/ }).click()
  await sidebar.getByText('模拟评分规则', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/agents/simulation-rules')
  await sidebar.locator('.el-menu-item.is-active').getByText('模拟评分规则', { exact: true }).waitFor()
  assert.equal(await sidebar.locator('.el-sub-menu.is-active.is-opened > .el-sub-menu__title').textContent(), '评测与发布')
  console.log('实际页面与跨分类高亮检查通过')
  await page.reload({ waitUntil: 'domcontentloaded' })
  await sidebar.locator('.el-menu-item.is-active').getByText('模拟评分规则', { exact: true }).waitFor()
  assert.equal(await sidebar.locator('.el-sub-menu.is-active.is-opened > .el-sub-menu__title').textContent(), '评测与发布')
  await page.locator('#hamburger-container').click()
  await sidebar.locator('.el-menu--collapse').waitFor()
  await sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^资料与文稿$/ }).hover()
  await page.locator('.el-menu--popup:visible').getByText('资料中心', { exact: true }).waitFor()
  await page.locator('#hamburger-container').click()
  await page.setViewportSize({ width: 375, height: 900 })
  await page.locator('.mobile').waitFor()
  await page.locator('#hamburger-container').click()
  await page.locator('.drawer-bg').waitFor()
  await sidebar.locator('.el-menu > .el-sub-menu > .el-sub-menu__title').filter({ hasText: /^项目与业务$/ }).click()
  await sidebar.getByText('场景目录', { exact: true }).click()
  await page.waitForURL(url => url.pathname === '/scenarios')
  await page.locator('.hideSidebar.mobile').waitFor()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  const navigation = await context.request.get(origin + '/api/framework/navigation', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(navigation.status(), 200)
  const authority = (await navigation.json()).data
  assert.ok(authority.roles.includes('admin'))
  assert.ok(authority.permissions.includes('*:*:*'))
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/logout', { headers: { Origin: origin, [csrf.header_name]: csrf.token } })).status(), 204)
  assert.deepEqual(errors, [])
  const result = { verified_at: new Date().toISOString(), groups, function_count: functions.length, preserved_functions: true,
    native_user_management: true, active_group_after_reload: true, collapsed_popup: true, mobile_navigation: true,
    authority: { roles: authority.roles, permissions: authority.permissions }, errors, screenshots: false }
  writeFileSync(new URL(`../../.implementation-work/business-menu-${origin.endsWith(':18083') ? 'browser' : 'production'}.json`, import.meta.url), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally { await context.close(); await browser.close() }
