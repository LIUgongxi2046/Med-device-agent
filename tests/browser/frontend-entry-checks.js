import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

const output = path.resolve(process.argv[2])
const origin = new URL(process.argv[3]).origin
assert.equal(origin, 'http://127.0.0.1:18085')
const owner = JSON.parse(await readFile('.implementation-work/runtime/owner-credentials.json', 'utf8'))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const context = await browser.newContext()
  try {
    const page = await context.newPage()
    const errors = []; const requests = []; const checks = []
    page.on('pageerror', error => errors.push(error.name))
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) {
        requests.push({ path: url.pathname, method: request.method(), authorization: request.headers().authorization ?? null })
      }
    })
    await page.goto(origin + '/auth/login')
    await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
    assert.equal(await page.locator('form.login-form').count(), 1)
    assert.equal(await page.getByRole('checkbox').count(), 0)
    const originalIndex = await readFile(path.join(output, 'production/index.html'))
    const routes = await page.evaluate(() => document.getElementById('app').__vue_app__.config.globalProperties.$router.getRoutes()
      .filter(route => route.meta.medical).map(route => route.path))
    for (const element of await page.locator('script[src],link[rel=stylesheet]').all()) {
      const resource = await element.getAttribute(await element.evaluate(value => value.tagName === 'SCRIPT' ? 'src' : 'href'))
      const url = new URL(resource, origin)
      assert.equal(url.origin, origin)
      const response = await context.request.get(url.href)
      assert.equal(response.status(), 200)
      assert.deepEqual(await response.body(), await readFile(path.join(output, 'production', url.pathname)))
    }
    await page.getByRole('button', { name: '登录账户', exact: true }).click()
    await page.getByText('请输入您的账号', { exact: true }).waitFor()
    await page.getByText('请输入您的密码', { exact: true }).waitFor()
    assert.equal(requests.filter(value => value.method === 'POST').length, 0)
    checks.push({ name: 'backend_login_serves_original_ruoyi_form_and_required_validation', result: 'PASS' })
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
    await page.getByLabel('密码', { exact: true }).fill(owner.password)
    await page.getByRole('button', { name: '登录账户', exact: true }).click()
    await page.waitForURL(origin + '/projects')
    await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
    const session = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
    assert.equal(session.status(), 200)
    const actual = (await session.json()).data
    assert.equal(actual.user.username, owner.username)
    assert.deepEqual(actual.memberships, []); assert.deepEqual(actual.action_summary, [])
    for (const route of new Set(routes)) {
      const actualPath = route.replace(/:[A-Za-z]+/g, actual.user.user_id)
      const response = await context.request.get(origin + actualPath)
      assert.equal(response.status(), 200, actualPath)
      assert.deepEqual(await response.body(), originalIndex)
    }
    assert(requests.every(value => value.authorization === null))
    assert.equal((await context.cookies()).some(value => ['password', 'rememberMe'].includes(value.name)), false)
    checks.push({ name: 'same_origin_actual_cookie_login_without_enterprise_permission_or_saved_password', result: 'PASS' })
    const taskRequests = requests.filter(value => value.path.endsWith('/tasks')).length
    await page.goto(origin + '/projects/' + actual.user.user_id + '/tasks')
    await page.getByRole('heading', { name: '项目任务', exact: true }).waitFor()
    await page.getByText('任务列表需要当前企业身份及项目任务读取许可', { exact: true }).waitFor()
    assert.equal(await page.locator('[data-task-table]').count(), 0)
    await page.reload()
    await page.getByText('任务列表需要当前企业身份及项目任务读取许可', { exact: true }).waitFor()
    assert.equal(requests.filter(value => value.path.endsWith('/tasks')).length, taskRequests)
    checks.push({ name: 'same_origin_task_page_refresh_requires_actual_enterprise_and_project_permission', result: 'PASS' })
    await page.goto(origin + '/account')
    await page.getByText('个人信息', { exact: true }).waitFor()
    await page.getByRole('tab', { name: '修改密码', exact: true }).waitFor()
    await page.reload()
    await page.getByText('个人信息', { exact: true }).waitFor()
    for (const input of await page.locator('input[type=password]').all()) assert.equal(await input.inputValue(), '')
    checks.push({ name: 'original_profile_refresh_uses_bundled_frontend_and_current_cookie', result: 'PASS' })
    const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
    const logout = await context.request.post(origin + '/auth/logout', {
      headers: { [csrf.header_name]: csrf.token, Origin: origin }
    })
    assert.equal(logout.status(), 204)
    const revoked = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
    assert.equal(revoked.status(), 401)
    await page.goto(origin + '/auth/login')
    await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
    assert.equal(await page.getByLabel('密码', { exact: true }).inputValue(), '')
    assert.deepEqual(errors, [])
    checks.push({ name: 'logout_revokes_actual_cookie_and_original_login_clears_password', result: 'PASS' })
    await writeFile(path.join(output, 'frontend-browser-result.json'), JSON.stringify({
      browser_version: browser.version(), checks, frontend_routes_verified: [...new Set(routes)].length,
      request_metadata: requests, customer_success_verified: false
    }, null, 2) + '\n')
  } finally { await context.close() }
} finally { await browser.close() }
