import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'
import { readFile, open, rename, stat, writeFile } from 'node:fs/promises'
import { userInfo } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.resolve(process.argv[2])
const backend = new URL(process.argv[3])
assert.equal(backend.hostname, '127.0.0.1')
assert.equal(backend.protocol, 'http:')
assert(Number(backend.port) > 0)
const runtime = path.join(root, '.implementation-work/runtime')
const credentials = path.join(runtime, 'owner-credentials.json')
assert.equal((await stat(credentials)).mode & 0o777, 0o600)
assert.equal((await stat(runtime)).mode & 0o777, 0o700)
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

async function syncDirectory() {
  const directory = await open(runtime, 'r')
  try { await directory.sync() } finally { await directory.close() }
}

for (const mode of ['development', 'production']) {
  console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', mode, lifecycle: 'START' }))
  const owner = JSON.parse(await readFile(credentials, 'utf8'))
  assert.equal(owner.username, userInfo().username)
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
      const oldContext = await browser.newContext()
      try {
        const page = await context.newPage()
        const oldPage = await oldContext.newPage()
        const errors = []; const changes = []
        page.on('pageerror', error => errors.push(error.name))
        page.on('request', request => {
          if (new URL(request.url()).pathname === '/api/local-account/credential-revisions') {
            changes.push({ method: request.method(), key: request.headers()['idempotency-key'], authorization: request.headers().authorization ?? null })
          }
        })
        async function login(target, password) {
          await target.goto(origin + '/access')
          await target.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
          await target.getByLabel('密码', { exact: true }).fill(password)
          const response = target.waitForResponse(item => new URL(item.url()).pathname === '/auth/login' && item.request().method() === 'POST')
          await target.getByRole('button', { name: '登录账户', exact: true }).click()
          assert.equal((await response).status(), 302)
          await target.waitForURL(origin + '/projects')
          await target.getByText('当前账户没有企业成员身份', { exact: true }).waitFor()
        }
        async function enterAccount() {
          await page.getByRole('link', { name: '本人账户', exact: true }).click()
          await page.getByRole('heading', { name: '本人账户', exact: true }).waitFor()
          await page.locator('[data-own-account]').waitFor()
          await page.waitForFunction(() => !document.querySelector('[data-credential-submit]').disabled)
        }
        async function fillPasswords(current, password, confirmation) {
          await page.getByLabel('当前密码', { exact: true }).fill(current)
          await page.getByLabel('新密码', { exact: true }).fill(password)
          await page.getByLabel('确认新密码', { exact: true }).fill(confirmation)
        }
        async function noSecrets(secrets) {
          const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
          const cookies = await context.cookies(origin)
          const session = cookies.filter(cookie => cookie.name === 'MEDICAL_SESSION')
          assert.equal(session.length, 1)
          assert.equal(session[0].httpOnly, true)
          const safe = await page.evaluate(values => {
            const stored = JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage }, cookie: document.cookie, url: location.href })
            return values.every(value => !stored.includes(value)) && !document.cookie.includes('MEDICAL_SESSION=')
          }, [...secrets, csrf.token, session[0].value])
          assert.equal(safe, true, '浏览器存储、URL和可读Cookie不能包含秘密')
        }
        await login(page, owner.password)
        await login(oldPage, owner.password)
        await enterAccount()
        assert.equal(await page.locator('[data-framework-reset-pwd]').count(), 1, '本人密码操作必须使用原RuoYi组件')
        const beforeResponse = await context.request.get(origin + '/auth/account')
        assert.equal(beforeResponse.status(), 200)
        let before = await beforeResponse.json()
        assert.equal(await page.locator('[data-own-username]').textContent(), owner.username)
        assert.equal(await page.locator('[data-own-id]').textContent(), before.user_id)
        const checks = [{ name: 'actual_owner_account_metadata', result: 'PASS' }]
        assert.equal(await page.locator('[data-framework-profile]').count(), 1, '本人账户必须直接使用原个人信息页面')
        await page.getByRole('tab', { name: '基本资料', exact: true }).click()
        const nativeResponse = await context.request.get(origin + '/api/system/user/profile', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(nativeResponse.status(), 200)
        const profile = await nativeResponse.json()
        const fields = Object.fromEntries(['nickName', 'email', 'phonenumber', 'sex'].map(name => [name, profile.data[name]]))
        assert.equal(await page.getByLabel('用户昵称', { exact: true }).inputValue(), fields.nickName)
        const userInfo = page.locator('[data-framework-user-info]')
        assert.equal(await userInfo.count(), 1)
        await context.setOffline(true)
        await userInfo.getByRole('button', { name: '保存', exact: true }).click()
        await page.getByRole('alert').filter({ hasText: '连接请求未完成' }).waitFor()
        await page.locator('[data-pending-profile]').waitFor()
        const pendingProfile = await page.evaluate(actor => JSON.parse(sessionStorage.getItem('medical.own.profile:' + actor)), before.user_id)
        assert.deepEqual(Object.keys(pendingProfile).sort(), ['actor', 'key', 'revision', 'sha256'])
        assert.equal(pendingProfile.actor, before.user_id)
        assert.equal(pendingProfile.revision, before.revision)
        await context.setOffline(false)
        await page.reload()
        await page.locator('[data-own-account]').waitFor()
        await page.locator('[data-pending-profile]').waitFor()
        await page.getByRole('tab', { name: '基本资料', exact: true }).click()
        const changedProfile = page.waitForResponse(item => new URL(item.url()).pathname === '/api/local-account/profile-revisions')
        await page.locator('[data-framework-user-info]').getByRole('button', { name: '保存', exact: true }).click()
        const profileSaved = await changedProfile
        assert.equal(profileSaved.status(), 200)
        assert.equal(profileSaved.request().headers()['idempotency-key'], pendingProfile.key)
        const profileReceipt = await profileSaved.json()
        assert.equal(profileReceipt.data.reauthentication_required, false)
        assert.equal(profileReceipt.data.account.revision, String(BigInt(before.revision) + 1n))
        await page.locator('[data-own-account]').waitFor()
        await page.waitForFunction(actor => sessionStorage.getItem('medical.own.profile:' + actor) === null, before.user_id)
        const profileCsrf = await (await context.request.get(origin + '/auth/csrf')).json()
        const profileReplay = await context.request.post(origin + '/api/local-account/profile-revisions', { headers: {
          'X-Schema-Version': '1.0.0', 'Idempotency-Key': pendingProfile.key,
          [profileCsrf.header_name]: profileCsrf.token, Origin: origin
        }, data: { ...fields, user_id: before.user_id, expected_revision: pendingProfile.revision } })
        assert.equal(profileReplay.status(), 200)
        assert.deepEqual(await profileReplay.json(), profileReceipt)
        const persistedProfile = await (await context.request.get(origin + '/api/system/user/profile', { headers: { 'X-Schema-Version': '1.0.0' } })).json()
        assert.deepEqual(Object.fromEntries(Object.keys(fields).map(name => [name, persistedProfile.data[name]])), fields)
        assert.equal(persistedProfile.account_revision, profileReceipt.data.account.revision)
        assert.equal((await oldContext.request.get(origin + '/auth/account')).status(), 200)
        checks.push({ name: 'native_profile_save_offline_refresh_replay_and_reread', result: 'PASS',
          actor: before.user_id, key: pendingProfile.key, previous_revision: before.revision, envelope: profileReceipt })
        before = await (await context.request.get(origin + '/auth/account')).json()
        await page.getByRole('tab', { name: '修改密码', exact: true }).click()

        const nextOwner = { ...owner, password: randomBytes(24).toString('hex') }
        await fillPasswords(owner.password, nextOwner.password, owner.password)
        await page.getByRole('button', { name: '确认变更本人密码', exact: true }).click()
        await page.getByText('两次输入的新密码需要一致', { exact: true }).waitFor()
        assert.equal(changes.length, 0)
        await fillPasswords(owner.password, nextOwner.password, nextOwner.password)
        assert.equal(await page.getByText('两次输入的新密码需要一致', { exact: true }).count(), 0)
        // 多字节输入使用当前本人操作检验BCrypt边界，不创建其他业务记录。
        await fillPasswords(owner.password, '密'.repeat(25), '密'.repeat(25))
        await page.getByRole('button', { name: '确认变更本人密码', exact: true }).click()
        await page.getByText('密码需要至少15个字符，UTF-8编码最多72个字节', { exact: true }).waitFor()
        assert.equal(changes.length, 0)
        checks.push({ name: 'confirmation_and_utf8_boundary_without_request', result: 'PASS' })

        await fillPasswords(randomBytes(24).toString('hex'), nextOwner.password, nextOwner.password)
        const rejected = page.waitForResponse(item => new URL(item.url()).pathname === '/api/local-account/credential-revisions')
        await page.getByRole('button', { name: '确认变更本人密码', exact: true }).click()
        const rejectedResponse = await rejected
        assert.equal(rejectedResponse.status(), 401)
        assert.equal((await rejectedResponse.json()).code, 'AUTHENTICATION_FAILED')
        await page.locator('[data-pending-credential]').waitFor()
        await noSecrets([owner.password, nextOwner.password])
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        await page.locator('[data-own-account]').waitFor()
        await page.getByRole('button', { name: '清除待提交请求', exact: true }).click()
        for (const label of ['当前密码', '新密码', '确认新密码']) assert.equal(await page.getByLabel(label, { exact: true }).inputValue(), '')
        const unchanged = await (await context.request.get(origin + '/auth/account')).json()
        assert.equal(unchanged.revision, before.revision)
        checks.push({ name: 'actual_current_password_rejection_and_clear', result: 'PASS' })

        await fillPasswords(owner.password, nextOwner.password, nextOwner.password)
        await context.setOffline(true)
        const failed = page.waitForEvent('requestfailed', { predicate: request => new URL(request.url()).pathname === '/api/local-account/credential-revisions' })
        await page.getByRole('button', { name: '确认变更本人密码', exact: true }).click()
        await failed
        await page.locator('[data-pending-credential]').waitFor()
        const pending = await page.evaluate(actor => JSON.parse(sessionStorage.getItem('medical.own.credential:' + actor)), before.user_id)
        assert.deepEqual(Object.keys(pending).sort(), ['actor', 'key', 'revision'])
        assert.equal(pending.actor, before.user_id)
        assert.equal(pending.revision, before.revision)
        await noSecretsAfterOffline()
        async function noSecretsAfterOffline() {
          assert.equal(await page.evaluate(values => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }).includes(values[0])
            || JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }).includes(values[1]), [owner.password, nextOwner.password]), false)
        }
        await context.setOffline(false)
        await page.reload()
        await page.locator('[data-own-account]').waitFor()
        assert.deepEqual(await page.evaluate(actor => JSON.parse(sessionStorage.getItem('medical.own.credential:' + actor)), before.user_id), pending)
        for (const label of ['当前密码', '新密码', '确认新密码']) assert.equal(await page.getByLabel(label, { exact: true }).inputValue(), '')
        checks.push({ name: 'actual_offline_request_identity_and_refresh', result: 'PASS' })

        const candidate = path.join(runtime, 'owner-credentials-vue-' + randomUUID() + '.json')
        const privateFile = await open(candidate, 'wx', 0o600)
        try { await privateFile.writeFile(JSON.stringify(nextOwner)); await privateFile.sync() } finally { await privateFile.close() }
        await syncDirectory()
        await fillPasswords(owner.password, nextOwner.password, nextOwner.password)
        const changed = page.waitForResponse(item => new URL(item.url()).pathname === '/api/local-account/credential-revisions')
        await page.getByRole('button', { name: '确认变更本人密码', exact: true }).click()
        const response = await changed
        assert.equal(response.status(), 200)
        assert.equal(response.request().headers()['idempotency-key'], pending.key)
        const receipt = await response.json()
        assert.equal(receipt.data.account.user_id, before.user_id)
        assert.equal(receipt.data.account.revision, String(BigInt(before.revision) + 1n))
        assert.equal(receipt.data.reauthentication_required, true)
        await page.waitForURL(origin + '/access')
        const oldResponse = await oldContext.request.get(origin + '/auth/account')
        assert.equal(oldResponse.status(), 403)
        assert.equal((await oldResponse.json()).code, 'ACCESS_DENIED')
        await login(page, nextOwner.password)
        await rename(candidate, credentials)
        await syncDirectory()
        const after = await (await context.request.get(origin + '/auth/account')).json()
        // 成功登录共同清除失败次数并增加账户版本，原密码操作响应保持独立版本。
        assert.deepEqual(after, { ...before, revision: String(BigInt(receipt.data.account.revision) + 1n) })
        await noSecrets([owner.password, nextOwner.password])
        assert.equal(await page.evaluate(actor => sessionStorage.getItem('medical.own.credential:' + actor), before.user_id), null)
        const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
        const replay = await context.request.post(origin + '/api/local-account/credential-revisions', { headers: {
          'X-Schema-Version': '1.0.0', 'Idempotency-Key': pending.key, [csrf.header_name]: csrf.token, Origin: origin
        }, data: { current_password: owner.password, password: nextOwner.password, expected_revision: pending.revision } })
        assert.equal(replay.status(), 200)
        assert.deepEqual(await replay.json(), receipt)
        assert.equal((await (await context.request.get(origin + '/auth/account')).json()).revision, after.revision)
        assert(changes.every(item => item.authorization === null))
        await page.getByRole('button', { name: '退出业务账户', exact: true }).click()
        await page.waitForURL(origin + '/access')
        assert.equal((await context.request.get(origin + '/auth/account')).status(), 401)
        assert.deepEqual(errors, [])
        checks.push({ name: 'credential_change_revocation_login_replay_and_logout', result: 'PASS',
          actor: before.user_id, key: pending.key, previous_revision: before.revision, envelope: receipt })
        await writeFile(path.join(output, 'medical-account-result-' + mode + '.json'), JSON.stringify({
          check_id: 'IC-06-ACCOUNT-BROWSER', scope: 'ACTUAL_OWNER_PROFILE_CREDENTIAL_AND_SESSION', mode,
          backend: backend.origin, browser_version: browser.version(), visual: 'NOT_RUN', checks
        }, null, 2) + '\n')
        console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', result: 'PASS', mode, checks: checks.length }))
      } finally {
        console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', mode, lifecycle: 'CONTEXT_CLOSING' }))
        await context.close(); await oldContext.close()
      }
    } finally {
      console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', mode, lifecycle: 'BROWSER_CLOSING' }))
      await browser.close()
    }
  } finally {
    console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', mode, lifecycle: 'SERVER_CLOSING' }))
    await server.close()
    console.log(JSON.stringify({ check_id: 'IC-06-ACCOUNT-BROWSER', mode, lifecycle: 'CLOSED' }))
  }
}
