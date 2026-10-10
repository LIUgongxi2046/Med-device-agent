import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { userInfo } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.resolve(process.argv[2])
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
assert.equal(owner.username, userInfo().username)
process.chdir(path.join(root, 'web'))
for (const mode of ['development', 'production']) {
const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
  build: { outDir: path.join(output, 'production') },
  server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false } }
const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
try {
  if (mode === 'development') await server.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const context = await browser.newContext()
    try {
      const page = await context.newPage()
      const errors = []
      const requests = []
      page.on('pageerror', error => errors.push(error.name))
      page.on('response', response => {
        const pathname = new URL(response.url()).pathname
        if (pathname.startsWith('/api/') || pathname.startsWith('/auth/')) console.log(JSON.stringify({
          source: 'ACTUAL_HTTP', path: pathname, status: response.status(), schema: response.headers()['x-schema-version'] ?? null
        }))
      })
      page.on('request', request => {
        if (new URL(request.url()).pathname.startsWith('/api/')) requests.push({
          path: new URL(request.url()).pathname, method: request.method(), authorization: request.headers().authorization ?? null
        })
      })
      await page.goto(origin + '/access')
      await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
      console.log(JSON.stringify({ source: 'ACTUAL_DOM', fields: await page.locator('input').evaluateAll(elements => elements.map(element => ({
        id: element.id, type: element.type, label: [...element.labels].map(label => label.textContent), ariaLabel: element.getAttribute('aria-label')
      }))) }))
      await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
      await page.getByLabel('密码', { exact: true }).fill(owner.password)
      const loginResult = page.waitForResponse(response => new URL(response.url()).pathname === '/auth/login' && response.request().method() === 'POST')
      await page.getByRole('button', { name: '登录账户', exact: true }).click()
      const loginResponse = await loginResult
      assert.equal(loginResponse.status(), 302)
      console.log(JSON.stringify({ source: 'ACTUAL_LOGIN_REDIRECT', location: loginResponse.headers().location }))
      await page.waitForFunction(() => location.pathname === '/projects' || document.querySelector('[role=alert]')?.textContent?.trim())
      console.log(JSON.stringify({ source: 'ACTUAL_LOGIN_RESULT', path: new URL(page.url()).pathname,
        error: await page.locator('[role=alert]').allTextContents() }))
      assert.equal(new URL(page.url()).pathname, '/projects')
      await page.waitForURL(origin + '/projects')
      await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
      assert.equal(await page.getByRole('heading', { name: '项目中心', exact: true }).count(), 1)
      assert.equal(await page.locator('[data-medical-user]').textContent(), owner.username)
      assert.equal(await page.getByRole('button', { name: '创建项目', exact: true }).isDisabled(), true)
      assert.equal(await page.locator('[data-project-table]').count(), 0)
      assert.equal(requests.filter(item => item.path === '/api/projects').length, 0)
      assert(requests.every(item => item.authorization === null))
      assert.deepEqual(errors, [])
      const checks = [{ name: 'actual_cookie_login_and_no_enterprise', result: 'PASS' }]
      const style = await page.locator('.navbar').evaluate(element => ({ height: getComputedStyle(element).height }))
      assert.equal(style.height, '68px')

      await page.getByRole('link', { name: '企业登记与审查', exact: true }).click()
      await page.waitForURL(origin + '/enterprises/registrations')
      await page.getByRole('heading', { name: '企业登记申请', exact: true }).waitFor({ timeout: 10000 })
      await page.getByText('尚无本人提交的申请', { exact: true }).waitFor()
      await page.getByRole('button', { name: '保存企业登记申请', exact: true }).waitFor()
      assert.equal(await page.getByLabel('企业法定名称', { exact: true }).inputValue(), '')
      assert.equal(await page.getByLabel('授权依据', { exact: true }).inputValue(), '')
      assert.equal(await page.getByRole('checkbox', { name: '确认已取得企业登记及管理员指定的授权', exact: true }).isChecked(), false)
      assert.equal(await page.getByLabel('首位管理员', { exact: true }).inputValue(), '')
      await page.getByRole('button', { name: '保存企业登记申请', exact: true }).click()
      await page.getByText('请填写企业法定名称', { exact: true }).waitFor()
      assert.equal(requests.filter(item => item.path === '/api/enterprise-registrations' && item.method === 'POST').length, 0)
      assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage)), [])
      checks.push({ name: 'registration_form_requires_actual_company_and_explicit_authorization', result: 'PASS' })

      await page.getByLabel('首位管理员', { exact: true }).click()
      await page.getByRole('option', { name: new RegExp(owner.username) }).click()
      await page.getByRole('link', { name: '返回项目中心', exact: true }).click()
      await page.getByRole('dialog', { name: '离开企业登记', exact: true }).waitFor()
      await page.getByRole('button', { name: '继续填写', exact: true }).click()
      assert.equal(new URL(page.url()).pathname, '/enterprises/registrations')
      await page.getByRole('link', { name: '返回项目中心', exact: true }).click()
      await page.getByRole('button', { name: '放弃未保存输入并离开', exact: true }).click()
      await page.waitForURL(origin + '/projects')
      await page.getByRole('link', { name: '企业登记与审查', exact: true }).click()
      await page.getByText('尚无本人提交的申请', { exact: true }).waitFor()
      assert.equal(await page.getByLabel('首位管理员', { exact: true }).inputValue(), '')
      assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage)), [])
      checks.push({ name: 'registration_unsaved_input_confirmation_and_clear', result: 'PASS' })

      await page.reload()
      await page.getByText('尚无本人提交的申请', { exact: true }).waitFor()
      await context.setOffline(true)
      const registrationFailed = page.waitForEvent('requestfailed', { predicate: request => request.url() === origin + '/api/session' })
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await registrationFailed
      await page.getByText('连接请求未完成，请恢复连接后重新读取会话', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-enterprise-registration-form]').count(), 0)
      assert.equal(await page.locator('[data-enterprise-registration-detail]').count(), 0)
      await context.setOffline(false)
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await page.getByText('尚无本人提交的申请', { exact: true }).waitFor()
      assert.deepEqual(errors, [])
      checks.push({ name: 'registration_refresh_connection_failure_and_current_identity', result: 'PASS' })

      await page.getByRole('link', { name: '审查企业申请', exact: true }).click()
      await page.waitForURL(origin + '/enterprises/registration-reviews', { timeout: 8000 })
      await page.getByRole('heading', { name: '企业申请人工审查', exact: true }).waitFor()
      await page.getByText('当前筛选没有企业申请', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-enterprise-review-form]').count(), 0)
      for (const [name, state] of [['已经批准', 'APPROVED'], ['已经拒绝', 'REJECTED'], ['等待审查', 'PENDING_REVIEW']]) {
        await page.getByRole('button', { name: '重新读取审查列表', exact: true }).click({ trial: true })
        await page.getByLabel('审查状态', { exact: true }).press('Enter')
        const filtered = page.waitForResponse(response => {
          const url = new URL(response.url())
          return url.pathname === '/api/enterprise-registration-reviews' && url.searchParams.get('status') === state
        })
        await page.getByRole('option', { name, exact: true }).click()
        const filteredResponse = await filtered
        assert.equal(filteredResponse.status(), 200)
        assert.equal(filteredResponse.headers()['x-schema-version'], '1.0.0')
        assert.deepEqual((await filteredResponse.json()).data.items, [])
        await page.getByText('当前筛选没有企业申请', { exact: true }).waitFor()
      }
      await page.reload()
      await page.getByText('当前筛选没有企业申请', { exact: true }).waitFor()
      assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage)), [])
      checks.push({ name: 'review_formal_route_actual_status_filters_and_refresh', result: 'PASS' })

      const reviewSession = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
      assert.equal(reviewSession.status(), 200)
      const reviewActor = (await reviewSession.json()).data.user.user_id
      const deniedReview = page.waitForResponse(response => new URL(response.url()).pathname === '/api/enterprise-registration-reviews/' + reviewActor)
      await page.goto(origin + '/enterprises/registration-reviews/' + reviewActor)
      assert.equal((await deniedReview).status(), 403)
      await page.locator('[role=alert]').filter({ hasText: '访问权限不足' }).waitFor()
      assert.equal(await page.locator('[data-enterprise-review-detail], [data-enterprise-review-form]').count(), 0)
      assert.equal(requests.filter(item => item.path.startsWith('/api/enterprise-registration-reviews/') && item.method === 'POST').length, 0)
      checks.push({ name: 'review_actual_account_identifier_has_no_application_or_decision_authority', result: 'PASS' })

      await page.goto(origin + '/enterprises/registration-reviews')
      await page.getByText('当前筛选没有企业申请', { exact: true }).waitFor()
      await context.setOffline(true)
      const reviewFailed = page.waitForEvent('requestfailed', { predicate: request => request.url() === origin + '/api/session' })
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await reviewFailed
      await page.getByText('连接请求未完成，请恢复连接后重新读取会话', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-enterprise-review-detail], [data-enterprise-review-form], [data-enterprise-review-list]').count(), 0)
      await context.setOffline(false)
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await page.getByText('当前筛选没有企业申请', { exact: true }).waitFor()
      assert.deepEqual(errors, [])
      checks.push({ name: 'review_connection_failure_clears_current_data_and_restores_identity', result: 'PASS' })

      await page.goto(origin + '/projects')

      await page.reload()
      await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
      const session = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
      assert.equal(session.status(), 200)
      const actual = (await session.json()).data
      assert.equal(actual.user.username, owner.username)
      assert.equal(actual.current_context, null)
      assert.deepEqual(actual.memberships, [])
      await page.goto(origin + '/projects/' + actual.user.user_id)
      await page.getByRole('heading', { name: '项目详情', exact: true }).waitFor()
      await page.getByText('尚未选择企业空间', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-project-detail]').count(), 0)
      assert.equal(requests.filter(item => item.path.startsWith('/api/projects/')).length, 0)
      checks.push({ name: 'refresh_and_unauthorized_deep_link', result: 'PASS' })

      await page.goto(origin + '/projects/new')
      await page.getByRole('heading', { name: '创建项目', exact: true }).waitFor()
      await page.getByText('创建项目需要当前企业的明确授权', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-project-write-form]').count(), 0)
      assert.equal(requests.filter(item => item.path === '/api/projects' && item.method === 'POST').length, 0)
      assert.deepEqual(errors, [])
      checks.push({ name: 'project_creation_route_requires_actual_enterprise_authority', result: 'PASS' })
      for (const entry of [
        { path: '/projects/' + actual.user.user_id + '/profile', title: '修订项目属性' },
        { path: '/projects/' + actual.user.user_id + '/status', title: '决定项目状态' }
      ]) {
        await page.goto(origin + entry.path)
        await page.getByRole('heading', { name: entry.title, exact: true }).waitFor()
        await page.getByText('当前项目动作需要明确读取及管理授权', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-project-write-form]').count(), 0)
        assert.equal(requests.filter(item => item.path.startsWith('/api/projects/') && item.method === 'POST').length, 0)
      }
      assert.deepEqual(errors, [])
      checks.push({ name: 'project_revision_and_state_routes_require_actual_authority', result: 'PASS' })

      await page.goto(origin + '/agents/budgets')
      await page.getByRole('heading', { name: '模型预算', exact: true }).waitFor()
      await page.getByText('预算读取需要当前企业的明确授权', { exact: true }).waitFor()
      assert.equal(await page.getByRole('button', { name: '创建预算', exact: true }).isDisabled(), true)
      assert.equal(await page.locator('[data-budget-table]').count(), 0)
      await page.reload()
      await page.getByRole('heading', { name: '模型预算', exact: true }).waitFor()
      for (const entry of [
        { path: '/agents/budgets/new', title: '创建预算', reason: '创建预算需要当前企业的明确批准授权' },
        { path: '/agents/budgets/' + actual.user.user_id, title: '预算详情', reason: '预算读取需要当前企业的明确授权' },
        { path: '/agents/budgets/' + actual.user.user_id + '/decisions', title: '追加预算批准', reason: '追加预算批准需要当前账户的读取及批准授权' },
        { path: '/agents/budgets/' + actual.user.user_id + '/revisions', title: '预算批准历史', reason: '预算历史读取需要当前企业的明确授权' },
        { path: '/agents/budgets/' + actual.user.user_id + '/revisions/' + actual.user.user_id, title: '预算批准版本', reason: '预算历史读取需要当前企业的明确授权' }
      ]) {
        await page.goto(origin + entry.path)
        await page.getByRole('heading', { name: entry.title, exact: true }).waitFor()
        await page.getByText(entry.reason, { exact: true }).waitFor()
        assert.equal(await page.locator('[data-budget-write-form]').count(), 0)
        assert.equal(await page.locator('[data-budget-detail]').count(), 0)
        assert.equal(await page.locator('[data-budget-history-table], [data-budget-history-entry]').count(), 0)
      }
      assert.equal(requests.filter(item => item.path === '/api/budgets' || item.path.startsWith('/api/budgets/')).length, 0)
      assert.deepEqual(errors, [])
      checks.push({ name: 'actual_budget_routes_refresh_and_authority_denial', result: 'PASS' })

      if (mode === 'development') {
        const checked = await page.evaluate(async actualSession => {
          const budgets = await import('/src/api/medical/budgets.ts')
          const protocol = await import('/src/api/medical/protocol.ts')
          const failures = await Promise.allSettled([
            budgets.prepareBudgetWrite(actualSession, 'BD-02', null, {}),
            budgets.prepareBudgetWrite(actualSession, 'BD-04', actualSession.user.user_id, {}),
            budgets.listBudgetHistory(actualSession, actualSession.user.user_id, { limit: '20' }, new AbortController().signal),
            budgets.readBudgetHistory(actualSession, actualSession.user.user_id, actualSession.user.user_id, new AbortController().signal)
          ])
          return { allowed: budgets.budgetActionAllowed(actualSession, 'budget.approve'),
            codes: failures.map(result => result.status === 'rejected' ? result.reason.code : 'UNEXPECTED_SUCCESS'),
            schemaIssues: ['Create', 'Decide'].map(name => protocol.schemaIssues('urn:medical:budgets:1.0.0#/$defs/' + name, {}).length),
            historySchemaIssues: ['Entry', 'Listing'].map(name => protocol.schemaIssues('urn:medical:budget-history:1.0.0#/$defs/' + name, {}).length),
            storage: Object.keys(sessionStorage) }
        }, actual)
        assert.equal(checked.allowed, false)
        assert.deepEqual(checked.codes, ['ACCESS_DENIED', 'ACCESS_DENIED', 'ACCESS_DENIED', 'ACCESS_DENIED'])
        assert(checked.schemaIssues.every(count => count > 0))
        assert(checked.historySchemaIssues.every(count => count > 0))
        assert.deepEqual(checked.storage, [])
        checks.push({ name: 'actual_budget_sdk_denial_and_closed_schema', result: 'PASS' })
      }

      if (mode === 'development') {
        const checked = await page.evaluate(async actualSession => {
          const writes = await import('/src/api/medical/project-write.ts')
          const operations = await import('/src/api/medical/operations.ts')
          const protocol = await import('/src/api/medical/protocol.ts')
          const failures = await Promise.allSettled([
            writes.prepareProjectWrite(actualSession, 'PJ-02', null, {}),
            operations.lookupOperation(actualSession, 'PJ-02', crypto.randomUUID(), new AbortController().signal)
          ])
          return { allowed: writes.projectActionAllowed(actualSession, 'project.create', null),
            codes: failures.map(result => result.status === 'rejected' ? result.reason.code : 'UNEXPECTED_SUCCESS'),
            schemaIssues: ['Create', 'Revise', 'Decide'].map(name => protocol.schemaIssues('urn:medical:projects:1.0.0#/$defs/' + name, {}).length),
            storage: Object.keys(sessionStorage) }
        }, actual)
        assert.equal(checked.allowed, false)
        assert.deepEqual(checked.codes, ['ACCESS_DENIED', 'ACCESS_DENIED'])
        assert(checked.schemaIssues.every(count => count > 0))
        assert.deepEqual(checked.storage, [])
        checks.push({ name: 'actual_session_blocks_sdk_writes_and_lookup_without_enterprise', result: 'PASS' })
      }

      if (mode === 'development') {
        const transform = await page.evaluateHandle(async () => {
          const request = (await import('/src/utils/request.js')).default
          const response = await request.get('/api/projects', { medical: 'envelope', headers: {
            'X-Schema-Version': '1.0.0', 'X-Context-Revision': '0'
          } })
          if (response.status !== 403) throw new Error('当前无企业读取必须取得真实拒绝')
          return { configuration: response.config, read: response.config.transformResponse[0], write: response.config.transformRequest[0] }
        })
        try {
          // 直接执行真实请求登记的转换器；数学边界不建立业务对象或者替代HTTP响应。
          const boundaries = ['2.370', '9123372036854000123', '2.3e+500', '2.3e-500', '-0',
            '0.66666666666666666667', '[1,18446744073709551615,null,true,"2.370"]',
            '{"__proto__":{"constructor":18446744073709551615},"toString":2.370}',
            '{"isLosslessNumber":true,"value":18446744073709551615,"toJSON":"literal"}',
            '{"a":{},"b":[],"c":"😀"}']
          for (const text of boundaries) {
            const result = await transform.evaluate(async (actual, source) => {
              const { formatProjectJson } = await import('/src/api/medical/project-json.ts')
              const value = actual.read.call(actual.configuration, source)
              return { display: formatProjectJson(value), wire: actual.write.call(actual.configuration, value, actual.configuration.headers) }
            }, text)
            assert.equal(result.display, text, '项目数值显示必须保留原始内容')
            assert.equal(result.wire, text, '项目数值提交必须保留原始内容')
          }
          for (const text of ['{"a":1,"a":1}', '{"a":1,"a":2}', '{"a":{"b":true,"b":true}}',
            '{"a":1,"\\u0061":1}', '/* */{}', '[1,]', 'true false', '']) {
            await assert.rejects(transform.evaluate((actual, source) => actual.read.call(actual.configuration, source), text))
          }
          checks.push({ name: 'actual_project_transport_and_mathematical_json_boundaries', result: 'PASS',
            scope: 'ENGINEERING_ALGORITHM_AND_ACTUAL_DENIAL', round_trips: boundaries.length, rejected_inputs: 8 })
        } finally { await transform.dispose() }
      }

      await page.goto(origin + '/projects')
      await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
      await context.setOffline(true)
      const failed = page.waitForEvent('requestfailed', { predicate: request => request.url() === origin + '/api/session' })
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await failed
      await page.getByText('连接请求未完成，请恢复连接后重新读取会话', { exact: true }).waitFor()
      assert.equal(await page.locator('[data-medical-user]').count(), 0)
      assert.equal(await page.locator('[data-project-detail]').count(), 0)
      await context.setOffline(false)
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
      await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
      checks.push({ name: 'actual_connection_failure_and_revalidation', result: 'PASS' })

      const storage = await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage),
        theme: localStorage.getItem('vueuse-color-scheme'),
        cookies: document.cookie.split(';').map(item => item.trim().split('=')[0]) }))
      console.log(JSON.stringify({ source: 'ACTUAL_STORAGE_KEYS', ...storage }))
      assert.deepEqual(storage.session, [])
      assert.deepEqual(storage.local, ['vueuse-color-scheme'])
      assert.equal(storage.theme, 'auto')
      assert(!storage.cookies.includes('MEDICAL_SESSION'))
      assert(!storage.cookies.includes('Admin-Token'))
      await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
      await page.waitForURL(origin + '/access')
      assert.equal((await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).status(), 401)
      await page.goto(origin + '/projects')
      await page.waitForURL(origin + '/access')
      assert.equal(await page.locator('[data-medical-user]').count(), 0)
      assert.equal(requests.filter(item => item.method !== 'GET').length, 0)
      assert.deepEqual(errors, [])
      checks.push({ name: 'no_browser_secret_storage_and_actual_logout', result: 'PASS' })
      await writeFile(path.join(output, 'medical-session-result-' + mode + '.json'), JSON.stringify({
        check_id: 'IC-06-SESSION-BROWSER', scope: 'ACTUAL_OWNER_WITHOUT_ENTERPRISE', browser_version: browser.version(),
        mode, visual: 'NOT_RUN', checks, requests
      }, null, 2) + '\n')
      console.log(JSON.stringify({ check_id: 'IC-06-SESSION-BROWSER', result: 'PASS', mode, checks: checks.length, visual: 'NOT_RUN' }))
    } finally { await context.close() }
  } finally { await browser.close() }
} finally { await (mode === 'development' ? server.close() : new Promise(resolve => server.httpServer.close(resolve))) }
}
