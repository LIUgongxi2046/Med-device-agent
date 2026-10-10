import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.resolve(process.argv[2])
const backend = new URL(process.argv[3])
assert.equal(backend.hostname, '127.0.0.1')
assert.equal(backend.protocol, 'http:')
assert(Number(backend.port) > 0)
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
process.chdir(path.join(root, 'web'))
const proxy = {}
for (const prefix of ['/api', '/auth', '/local-auth']) proxy[prefix] = {
  target: backend.origin, changeOrigin: true, autoRewrite: true,
  configure(value) {
    value.on('proxyReq', (forward, request) => {
      if (request.headers.origin !== undefined) {
        assert(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(request.headers.origin))
        forward.setHeader('Origin', 'http://127.0.0.1:18080')
      }
    })
  }
}
for (const mode of ['development', 'production']) {
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'),
    build: { outDir: path.join(output, 'production') },
    server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false, proxy },
    preview: { host: '127.0.0.1', port: 18084, strictPort: true, proxy } }
  const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
  try {
    if (mode === 'development') await server.listen()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const context = await browser.newContext()
      try {
        const page = await context.newPage(); const errors = []; const requests = []; const authenticationRequests = []
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          const pathname = new URL(request.url()).pathname
          if (pathname.startsWith('/api/')) requests.push({
            path: pathname, method: request.method(), authorization: request.headers().authorization ?? null,
            csrf_present: typeof request.headers()['x-csrf-token'] === 'string' })
          if (pathname.startsWith('/auth/') || pathname.startsWith('/dev-api/')) authenticationRequests.push({
            path: pathname, method: request.method(), authorization: request.headers().authorization ?? null })
        })
        const anonymous = await context.request.get(origin + '/api/framework/navigation', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(anonymous.status(), 401)
        assert.equal((await anonymous.json()).code, 'AUTHENTICATION_REQUIRED')
        const checks = [{ name: 'anonymous_native_navigation_rejected', result: 'PASS' }]
        await page.goto(origin + '/access')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        assert.equal(await page.locator('form.login-form').count(), 1, '业务登录需要使用原RuoYi登录表单')
        assert.equal(await page.getByRole('textbox', { name: '用户名', exact: true }).inputValue(), '')
        assert.equal(await page.getByLabel('密码', { exact: true }).inputValue(), '')
        assert.equal(await page.getByRole('checkbox').count(), 0)
        assert.equal(await page.getByPlaceholder('验证码', { exact: true }).count(), 0)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.getByText('请输入您的账号', { exact: true }).waitFor()
        await page.getByText('请输入您的密码', { exact: true }).waitFor()
        assert.equal(authenticationRequests.filter(request => request.method === 'POST').length, 0)
        checks.push({ name: 'original_login_form_validates_required_fields_without_submitting_or_storing_password', result: 'PASS' })
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        const navigationResult = page.waitForResponse(response => new URL(response.url()).pathname === '/api/framework/navigation')
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        const response = await navigationResult
        assert.equal(response.status(), 200)
        assert.equal(response.headers()['cache-control'], 'no-store')
        const navigation = (await response.json()).data
        assert.equal(navigation.framework_revision, 'ruoyi-3.9.2')
        await page.waitForURL(origin + '/projects')
        await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
        const sessionResult = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(sessionResult.status(), 200)
        const session = (await sessionResult.json()).data
        assert.equal(session.user.user_id, navigation.user_id)
        assert.deepEqual(session.action_summary, []); assert.deepEqual(session.memberships, [])
        assert.deepEqual(navigation.roles, []); assert.deepEqual(navigation.permissions, []); assert.deepEqual(navigation.routers, [])
        assert.equal(await page.getByRole('button', { name: '创建项目', exact: true }).isDisabled(), true)
        assert(requests.every(request => request.authorization === null))
        assert.equal(authenticationRequests.filter(request => request.path === '/auth/csrf' && request.method === 'GET').length, 1)
        assert.equal(authenticationRequests.filter(request => request.path === '/auth/login' && request.method === 'POST').length, 1)
        assert(authenticationRequests.every(request => request.authorization === null && !request.path.startsWith('/dev-api/')))
        assert.equal((await context.cookies()).some(cookie => cookie.name === 'password' || cookie.name === 'rememberMe'), false)
        checks.push({ name: 'actual_vue_cookie_login_reads_original_navigation_without_roles_or_business_grants', result: 'PASS' })
        const nativeResponse = page.waitForResponse(value => new URL(value.url()).pathname.endsWith('/system/config/configKey/sys.account.captchaEnabled'))
        const nativeResult = await page.evaluate(async () => {
          const application = document.getElementById('app').__vue_app__
          return application.config.globalProperties.getConfigKey('sys.account.captchaEnabled').then(
            () => ({ success: true }), error => ({ success: false, code: error.code }))
        })
        const nativeHttp = await nativeResponse
        assert.equal(new URL(nativeHttp.url()).pathname, '/api/system/config/configKey/sys.account.captchaEnabled', '原管理API必须进入原Controller')
        assert.equal(nativeHttp.status(), 403, '原管理API必须核验当前原权限')
        assert.deepEqual(nativeResult, { success: false, code: 'ACCESS_DENIED' })
        assert.equal(await page.evaluate(() => sessionStorage.getItem('sessionObj')), null)
        await page.reload()
        await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
        checks.push({ name: 'original_global_config_api_uses_cookie_controller_and_actual_permission_denial', result: 'PASS' })
        if (mode === 'development') {
          const nativeProfile = await page.evaluate(async () => {
            const request = (await import('/src/utils/request.js')).default
            const value = await request.get('/system/user/profile')
            return { code: value.code, actor: value.business_user_id, user: value.data.userId, password: Object.hasOwn(value.data, 'password') }
          })
          assert.deepEqual(nativeProfile, { code: 200, actor: session.user.user_id, user: Number(navigation.framework_user_id), password: false })
          checks.push({ name: 'original_request_unwraps_actual_native_success_without_password', result: 'PASS' })
          const apiReads = [
            ['system/user', 'listUser', '/api/system/user/list'],
            ['system/role', 'listRole', '/api/system/role/list'],
            ['system/dept', 'listDept', '/api/system/dept/list'],
            ['system/menu', 'listMenu', '/api/system/menu/list'],
            ['system/post', 'listPost', '/api/system/post/list'],
            ['system/dict/type', 'listType', '/api/system/dict/type/list'],
            ['system/dict/data', 'listData', '/api/system/dict/data/list'],
            ['system/config', 'listConfig', '/api/system/config/list'],
            ['system/notice', 'listNotice', '/api/system/notice/list'],
            ['monitor/operlog', 'list', '/api/monitor/operlog/list'],
            ['monitor/logininfor', 'list', '/api/monitor/logininfor/list']
          ]
          for (const [module, method, pathname] of apiReads) {
            const observed = page.waitForResponse(value => new URL(value.url()).pathname === pathname)
            const result = await page.evaluate(async ({ module, method }) => {
              const api = await import('/src/api/' + module + '.js')
              return api[method]({ pageNum: 1, pageSize: 10 }).then(
                () => ({ success: true }), error => ({ success: false, code: error.code }))
            }, { module, method })
            const actual = await observed
            assert.equal(actual.status(), 403)
            const actualUrl = new URL(actual.url())
            assert.equal(actualUrl.searchParams.get('pageNum'), '1'); assert.equal(actualUrl.searchParams.get('pageSize'), '10')
            assert.deepEqual(result, { success: false, code: 'ACCESS_DENIED' })
            assert.equal(await page.evaluate(() => sessionStorage.getItem('sessionObj')), null)
            await page.reload()
            await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
          }
          checks.push({ name: 'eleven_original_management_list_apis_preserve_pagination_and_current_denials', result: 'PASS', methods: apiReads.length })
          const statusResponse = page.waitForResponse(value => new URL(value.url()).pathname === '/api/system/user/changeStatus')
          const statusResult = await page.evaluate(async userId => {
            const api = await import('/src/api/system/user.js')
            return api.changeUserStatus(userId, '0').then(
              () => ({ success: true }), error => ({ success: false, code: error.code }))
          }, Number(navigation.framework_user_id))
          const statusHttp = await statusResponse
          assert.equal(statusHttp.status(), 403)
          assert.equal(statusHttp.request().method(), 'PUT')
          assert.equal(statusHttp.request().headers()['x-csrf-token'], session.csrf.token)
          assert.equal(statusHttp.request().headers().authorization, undefined)
          assert.deepEqual(statusResult, { success: false, code: 'ACCESS_DENIED' })
          assert.equal(await page.evaluate(() => sessionStorage.getItem('sessionObj')), null)
          await page.reload()
          await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
          checks.push({ name: 'original_user_status_api_uses_current_csrf_without_body_storage_or_permission_grants', result: 'PASS' })
          const blobResponse = page.waitForResponse(value => new URL(value.url()).pathname === '/api/system/user/importTemplate')
          const blobResult = await page.evaluate(async () => {
            const request = (await import('/src/utils/request.js')).default
            return request.post('/system/user/importTemplate', null, { responseType: 'blob' }).then(
              () => ({ success: true }), error => ({ success: false, code: error.code }))
          })
          assert.equal((await blobResponse).status(), 403)
          assert.deepEqual(blobResult, { success: false, code: 'ACCESS_DENIED' })
          await page.reload()
          await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
          checks.push({ name: 'native_file_request_validates_real_error_blob_before_returning_download', result: 'PASS' })
          const previousCount = requests.length
          const external = await page.evaluate(async target => {
            const request = (await import('/src/utils/request.js')).default
            return request.get(target).then(
              () => ({ success: true }), error => ({ success: false, code: error.code }))
          }, backend.origin + '/api/system/user/list')
          assert.deepEqual(external, { success: false, code: 'ACCESS_DENIED' })
          assert.equal(requests.length, previousCount)
          await page.reload()
          await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
          checks.push({ name: 'original_request_rejects_external_destination_before_network', result: 'PASS' })
        }
        if (mode === 'development') {
          const state = await page.evaluate(async () => {
            const users = (await import('/src/store/modules/user.js')).default()
            const permissions = (await import('/src/store/modules/permission.js')).default()
            return { user: users.id, framework: users.frameworkNavigation, roles: users.roles,
              permissions: users.permissions, routes: permissions.frameworkRoutes }
          })
          assert.equal(state.user, session.user.user_id); assert.deepEqual(state.framework, navigation)
          assert.deepEqual(state.roles, []); assert.deepEqual(state.permissions, []); assert.deepEqual(state.routes, [])
          checks.push({ name: 'original_pinia_stores_preserve_current_user_and_empty_native_authority', result: 'PASS' })
        }
        const refreshed = page.waitForResponse(value => new URL(value.url()).pathname === '/api/framework/navigation')
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        assert.equal((await refreshed).status(), 200)
        await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
        checks.push({ name: 'explicit_session_refresh_reads_current_framework_navigation', result: 'PASS' })
        await page.goto(origin + '/templates')
        await page.getByRole('heading', { name: '模板人工审查', exact: true }).waitFor()
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        assert.equal(await page.getByRole('button', { name: '打开模板审查', exact: true }).isDisabled(), true)
        assert.equal(await page.getByRole('button', { name: '创建模板草稿', exact: true }).isDisabled(), true)
        checks.push({ name: 'shared_template_entry_retains_independent_business_authority', result: 'PASS' })
        const taskRequestsBefore = requests.filter(request => request.path.endsWith('/tasks')).length
        await page.goto(origin + '/projects/' + session.user.user_id + '/tasks')
        await page.getByRole('heading', { name: '项目任务', exact: true }).waitFor()
        await page.getByText('任务列表需要当前企业身份及项目任务读取许可', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-task-table]').count(), 0)
        assert.equal(requests.filter(request => request.path.endsWith('/tasks')).length, taskRequestsBefore)
        checks.push({ name: 'project_task_page_requires_real_enterprise_without_preloading_task_records', result: 'PASS' })
        await context.setOffline(true)
        const failed = page.waitForEvent('requestfailed', { predicate: request => new URL(request.url()).pathname === '/api/session' })
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        await failed
        await page.getByText('连接请求未完成，请恢复连接后重新读取会话', { exact: true }).waitFor()
        if (mode === 'development') {
          assert.deepEqual(await page.evaluate(async () => {
            const users = (await import('/src/store/modules/user.js')).default()
            const permissions = (await import('/src/store/modules/permission.js')).default()
            return { framework: users.frameworkNavigation, roles: users.roles, permissions: users.permissions, routes: permissions.frameworkRoutes }
          }), { framework: null, roles: [], permissions: [], routes: [] })
        }
        await context.setOffline(false)
        const recovered = page.waitForResponse(value => new URL(value.url()).pathname === '/api/framework/navigation')
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        assert.equal((await recovered).status(), 200)
        await page.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
        checks.push({ name: 'connection_interruption_clears_native_state_and_recovery_reads_real_identity', result: 'PASS' })
        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        assert.equal(await page.locator('form.login-form').count(), 1)
        assert.equal(await page.getByLabel('密码', { exact: true }).inputValue(), '')
        assert.equal((await context.request.get(origin + '/api/framework/navigation', { headers: { 'X-Schema-Version': '1.0.0' } })).status(), 401)
        if (mode === 'development') {
          assert.deepEqual(await page.evaluate(async () => {
            const users = (await import('/src/store/modules/user.js')).default()
            const permissions = (await import('/src/store/modules/permission.js')).default()
            return { framework: users.frameworkNavigation, roles: users.roles, permissions: users.permissions, routes: permissions.frameworkRoutes }
          }), { framework: null, roles: [], permissions: [], routes: [] })
        }
        const loginsBeforeInvalidTarget = authenticationRequests.filter(request => request.path === '/auth/login' && request.method === 'POST').length
        await page.goto(origin + '/login?redirect=' + encodeURIComponent(backend.origin + '/projects'))
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.getByText('登录目标需要使用当前业务页面', { exact: true }).waitFor()
        assert.equal(authenticationRequests.filter(request => request.path === '/auth/login' && request.method === 'POST').length, loginsBeforeInvalidTarget)
        assert.equal(await page.getByLabel('密码', { exact: true }).inputValue(), '')
        checks.push({ name: 'original_login_rejects_external_redirect_before_authentication', result: 'PASS' })
        await page.goto(origin + '/login?redirect=/templates')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        assert.equal(await page.locator('form.login-form').count(), 1)
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
        await page.getByLabel('密码', { exact: true }).fill(owner.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/templates')
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        await page.reload()
        await page.getByText('模板审查需要当前企业的明确核准权限', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-medical-user]').textContent(), owner.username)
        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
        assert.equal(await page.getByLabel('密码', { exact: true }).inputValue(), '')
        assert.equal((await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).status(), 401)
        assert.equal((await context.cookies()).some(cookie => cookie.name === 'password' || cookie.name === 'rememberMe'), false)
        checks.push({ name: 'original_login_entry_honors_business_redirect_and_cookie_refresh_then_revokes_session', result: 'PASS' })
        assert.deepEqual(errors, [])
        assert(requests.every(request => request.authorization === null && (request.method === 'GET'
          || request.csrf_present && ['/api/system/user/changeStatus', '/api/system/user/importTemplate'].includes(request.path))))
        checks.push({ name: 'logout_clears_native_state_and_rejects_cookie_reuse_without_business_writes', result: 'PASS' })
        await writeFile(path.join(output, 'navigation-browser-' + mode + '.json'), JSON.stringify({
          scope: 'ACTUAL_LOCAL_USER_NATIVE_NAVIGATION_WITHOUT_CUSTOMER_ENTERPRISE', mode, visual: 'NOT_RUN',
          backend: backend.origin, actor: navigation.user_id, browser_version: browser.version(), checks }, null, 2) + '\n')
        console.log(JSON.stringify({ scope: 'ACTUAL_NATIVE_NAVIGATION_DOM', mode, checks: checks.length, result: 'PASS' }))
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await server.close() }
}
