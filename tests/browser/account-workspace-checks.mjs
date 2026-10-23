import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
try {
  const page = await context.newPage()
  page.setDefaultTimeout(90000)
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const response = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
  assert.equal(response.status(), 200)
  const session = (await response.json()).data
  await page.locator('.sidebar-container .el-sub-menu__title').first().waitFor()
  const secondaryIcons = await page.locator('.sidebar-container .nest-menu .el-menu-item .svg-icon').count()
  const result = { verified_at: new Date().toISOString(), username: session.user.username, current_context: session.current_context,
    active_memberships: session.memberships.filter(member => member.status === 'ACTIVE').length, secondary_icons: secondaryIcons, screenshots: false }
  console.log(JSON.stringify(result, null, 2))
  writeFileSync(new URL('../../.implementation-work/account-workspace-browser.json', import.meta.url), JSON.stringify(result, null, 2))
  assert.ok(session.current_context, '账号登录后应当自动进入绑定工作组织')
  assert.equal(secondaryIcons, 0, '二级菜单不显示icon')
  const other = session.memberships.find(member => member.status === 'ACTIVE' && member.tenant_id !== session.current_context.tenant_id)
  assert.ok(other, '需要已有的第二个有效组织验证账户绑定')
  const selector = page.locator('.navbar [data-organization-switcher]')
  await selector.getByRole('combobox', { name: '工作组织', exact: true }).click()
  await page.getByRole('option').filter({ hasText: other.tenant_id }).click()
  await selector.getByRole('button', { name: '切换组织', exact: true }).click()
  await page.getByRole('dialog', { name: '切换工作组织', exact: true }).getByRole('button', { name: '确认切换', exact: true }).click()
  await selector.locator('[data-current-organization]').filter({ hasText: other.tenant_id }).waitFor()
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/logout', { headers: { Origin: origin, [csrf.header_name]: csrf.token } })).status(), 204)
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const restored = (await (await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
  assert.equal(restored.current_context.tenant_id, other.tenant_id, '重新登录后保持账号工作组织')
  result.restored_tenant_id = restored.current_context.tenant_id
  result.account_binding_persisted = true
  writeFileSync(new URL('../../.implementation-work/account-workspace-browser.json', import.meta.url), JSON.stringify(result, null, 2))
  console.log('账号组织绑定与重新登录验证通过')
} finally {
  await context.close()
  await browser.close()
}
