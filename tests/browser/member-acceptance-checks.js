import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const [outputValue, backendValue, frontendValue] = process.argv.slice(2)
const output = path.resolve(outputValue); const backend = new URL(backendValue)
assert.equal(backend.hostname, '127.0.0.1')
const input = JSON.parse(await readFile('/dev/stdin', 'utf8'))
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
const results = []
async function checkManagement(browser, origin, item, acceptance) {
  const account = item.account
  const context = await browser.newContext()
  try {
    const page = await context.newPage(); const checks = []; const errors = []; const reads = []
    page.on('pageerror', error => errors.push(error.name))
    page.on('request', request => {
      if (request.method() === 'GET' && ['/api/enterprise-members', '/api/permission-grants', '/api/member-invitations'].includes(new URL(request.url()).pathname)) {
        assert.equal(request.headers().authorization, undefined); reads.push(request.url())
      }
    })
    await page.goto(origin + '/access')
    await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 60000 })
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill(input.owner.username)
    await page.getByLabel('密码', { exact: true }).fill(input.owner.password)
    await page.getByRole('button', { name: '登录账户', exact: true }).click(); await page.waitForURL(origin + '/projects')
    await page.getByRole('region', { name: '当前账户和企业空间' }).getByRole('combobox').press('Enter')
    await page.getByRole('option', { name: input.tenant, exact: true }).click()
    await page.getByRole('button', { name: '确认选择企业', exact: true }).click()
    await page.getByText('当前企业：' + input.tenant + '；上下文版本：', { exact: false }).waitFor()
    await page.getByRole('link', { name: '管理企业成员与授权', exact: true }).click()
    await page.getByRole('heading', { name: '企业成员与授权', exact: true }).waitFor()
    await page.locator('[data-governance-list]').waitFor(); checks.push('authorized_manager_entry')
    await page.getByLabel('账户标识筛选', { exact: true }).fill('invalid')
    const before = reads.length
    await page.getByRole('button', { name: '查询当前记录', exact: true }).click()
    await page.getByText('账户标识需要规范 UUID', { exact: true }).waitFor()
    assert.equal(reads.length, before); checks.push('invalid_subject_prevents_read')
    await page.getByLabel('账户标识筛选', { exact: true }).fill(account.user_id)
    await page.getByRole('button', { name: '查询当前记录', exact: true }).click()
    const row = page.locator('[data-governance-list] tr').filter({ hasText: acceptance.member_id })
    await row.getByText('有效成员', { exact: true }).waitFor()
    await row.getByRole('button', { name: '查看完整记录', exact: true }).click()
    await page.locator('[data-governance-detail]').getByText(acceptance.member_id, { exact: false }).waitFor()
    checks.push('accepted_member_and_revision_reopened')
    await page.getByRole('button', { name: '读取邀请记录', exact: true }).click()
    await page.locator('[data-governance-list] tr').filter({ hasText: acceptance.invitation_id }).getByText('已接受', { exact: true }).waitFor()
    checks.push('actual_invitation_state_without_secret')
    await page.getByRole('button', { name: '读取授权记录', exact: true }).click()
    for (const id of acceptance.grants) await page.locator('[data-governance-list]').getByText(id, { exact: true }).waitFor()
    assert.equal(await page.locator('[data-governance-list] tbody tr').count(), 2)
    checks.push('actual_grant_records_reopened')
    await page.getByRole('button', { name: '读取成员记录', exact: true }).click()
    await page.getByLabel('账户标识筛选', { exact: true }).fill('')
    await page.getByLabel('每页数量', { exact: true }).fill('1')
    const firstRead = page.waitForResponse(value => new URL(value.url()).pathname === '/api/enterprise-members' && new URL(value.url()).searchParams.get('limit') === '1')
    await page.getByRole('button', { name: '查询当前记录', exact: true }).click()
    const first = (await (await firstRead).json()).data
    await page.locator('[data-governance-list]').getByText(first.items[0].member_id, { exact: true }).waitFor()
    const nextRead = page.waitForResponse(value => new URL(value.url()).pathname === '/api/enterprise-members' && new URL(value.url()).searchParams.has('after'))
    await page.getByRole('button', { name: '读取下一页', exact: true }).click()
    const nextResponse = await nextRead; const next = (await nextResponse.json()).data
    assert.equal(nextResponse.status(), 200); assert.equal(next.dataset_watermark, first.dataset_watermark)
    assert(new URL(nextResponse.url()).searchParams.get('dataset_watermark') === first.dataset_watermark)
    assert.notEqual(next.items[0].member_id, first.items[0].member_id)
    await page.locator('[data-governance-list]').getByText(next.items[0].member_id, { exact: true }).waitFor()
    checks.push('filtered_pagination_uses_original_watermark')
    await page.reload(); await page.locator('[data-governance-list]').waitFor(); checks.push('refresh_reads_actual_current_records')
    await context.setOffline(true)
    await page.waitForFunction(() => document.querySelector('[data-governance-list]') === null && document.querySelector('[data-governance-detail]') === null)
    await context.setOffline(false)
    await page.getByRole('button', { name: '重新读取管理身份', exact: true }).click()
    await page.locator('[data-governance-list]').waitFor(); checks.push('offline_clears_restricted_records')
    await page.getByRole('button', { name: '邀请企业成员', exact: true }).waitFor()
    const writeChecks = await checkManagementWrites(page, context, browser, origin, item, acceptance)
    assert.deepEqual(errors, []); return { checks, writeChecks }
  } finally { await context.close() }
}
async function checkManagementWrites(page, context, browser, origin, item, acceptance) {
  const checks = []; const writes = []
  page.on('request', request => {
    if (request.method() === 'POST' && ['/api/member-invitations', '/api/permission-decisions'].includes(new URL(request.url()).pathname)) {
      assert.equal(request.headers().authorization, undefined)
      writes.push({ path: new URL(request.url()).pathname, key: request.headers()['idempotency-key'] })
    }
  })
  const fill = async (dialog, body) => {
    await dialog.getByLabel('完整管理范围（JSON）', { exact: true }).fill(JSON.stringify(body.scope))
    await dialog.getByLabel('决定依据版本', { exact: true }).fill(body.basis.version_id)
    await dialog.getByLabel('决定依据摘要', { exact: true }).fill(body.basis.content_sha256)
    if (body.identity_binding) {
      await dialog.getByLabel('完整认证绑定（JSON）', { exact: true }).fill(JSON.stringify(body.identity_binding))
      await dialog.getByLabel('固定岗位引用（JSON）', { exact: true }).fill(JSON.stringify(body.roles))
      await dialog.getByLabel('邀请截止时间（UTC）', { exact: true }).fill(body.expires_at)
    } else {
      await dialog.getByLabel('决定原因', { exact: true }).fill(body.reason)
      if (body.decision === 'GRANT') {
        await dialog.getByLabel('授权主体标识', { exact: true }).fill(body.subject_id)
        await dialog.getByLabel('独立动作', { exact: true }).fill(body.action)
        await dialog.getByLabel('完整授权条件（JSON）', { exact: true }).fill(JSON.stringify(body.condition))
        await dialog.getByText('使用服务端当前时间作为有效起始时间', { exact: true }).click()
        await dialog.getByText('明确不设置授权截止时间', { exact: true }).click()
        assert(await dialog.getByRole('checkbox', { name: '使用服务端当前时间作为有效起始时间', exact: true }).isChecked())
        assert(await dialog.getByRole('checkbox', { name: '明确不设置授权截止时间', exact: true }).isChecked())
      }
    }
  }
  const submit = async (dialog, path) => {
    const receive = page.waitForResponse(value => new URL(value.url()).pathname === path && value.request().method() === 'POST')
    await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
    const response = await receive; assert.equal(response.status(), path === '/api/member-invitations' ? 201 : 200)
    const result = (await response.json()).data
    await page.locator('[data-completed-governance]').getByText(result.operation_id, { exact: true }).waitFor()
    assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.member.write:'))), [])
    return result
  }
  await page.getByRole('button', { name: '授予独立权限', exact: true }).click()
  let dialog = page.getByRole('dialog', { name: '授予独立权限', exact: true }); await dialog.waitFor()
  await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
  await dialog.getByText('请填写规范授权主体标识', { exact: true }).waitFor(); assert.equal(writes.length, 0)
  checks.push('required_complete_fields_prevent_write')
  await fill(dialog, item.grant_request)
  await dialog.getByRole('button', { name: '关闭编辑', exact: true }).click()
  const discard = page.getByRole('dialog', { name: '处理未保存的成员管理输入', exact: true })
  await discard.waitFor(); await discard.getByRole('button', { name: '继续编辑', exact: true }).click()
  assert.equal(await dialog.getByLabel('授权主体标识', { exact: true }).inputValue(), item.account.user_id)
  checks.push('unsaved_write_requires_explicit_choice')
  await page.route('**/api/permission-decisions', route => route.abort('failed'))
  const notSent = page.waitForEvent('requestfailed', { predicate: request => new URL(request.url()).pathname === '/api/permission-decisions' })
  await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
  await notSent; await page.unroute('**/api/permission-decisions')
  await page.reload(); await page.locator('[data-pending-governance]').waitFor()
  await page.getByRole('button', { name: '查询原成员管理操作', exact: true }).click()
  await page.getByText('尚未找到原操作，保持原请求键', { exact: true }).waitFor()
  await page.getByRole('button', { name: '填写原管理参数', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '授予独立权限', exact: true }); await dialog.waitFor()
  await fill(dialog, item.grant_request)
  checks.push('not_found_recovery_keeps_original_key_and_parameters')
  let committed = false
  await page.route('**/api/permission-decisions', async route => {
    const response = await route.fetch(); assert.equal(response.status(), 200); committed = true; await route.abort('failed')
  })
  const failed = page.waitForEvent('requestfailed', { predicate: request => new URL(request.url()).pathname === '/api/permission-decisions' })
  await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
  await failed; assert(committed); await page.unroute('**/api/permission-decisions')
  assert.equal(await page.locator('[data-completed-governance]').count(), 0)
  await page.reload(); await page.locator('[data-pending-governance]').waitFor()
  const metadata = await page.evaluate(() => Object.entries(sessionStorage).filter(([key]) => key.startsWith('medical.member.write:')).map(([, value]) => JSON.parse(value)))
  assert.equal(metadata.length, 1)
  assert.deepEqual(Object.keys(metadata[0]).sort(), ['actor','tenant_id','operation','decision','subject_id','target_id','key','context','body_sha256','expected_revision'].sort())
  assert.equal(metadata[0].key, writes[0].key)
  assert.equal(writes[0].key, writes[1].key)
  checks.push('committed_interruption_retains_only_original_metadata')
  await page.getByRole('button', { name: '查询原成员管理操作', exact: true }).click()
  await page.getByText('找到原操作；操作完成，仍需原参数及目标核查', { exact: true }).waitFor()
  await page.getByRole('button', { name: '填写原管理参数', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '授予独立权限', exact: true }); await dialog.waitFor()
  assert.equal(await dialog.getByLabel('完整授权条件（JSON）', { exact: true }).inputValue(), '')
  await fill(dialog, item.grant_request)
  await dialog.getByLabel('决定原因', { exact: true }).fill('SIMULATION 不同原参数拒绝验证')
  await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
  await page.getByText('请填写完整原参数并保持原请求身份', { exact: false }).waitFor()
  assert.equal(writes.length, 2)
  await page.reload(); await page.locator('[data-pending-governance]').waitFor()
  const unchanged = await page.evaluate(() => Object.entries(sessionStorage).filter(([key]) => key.startsWith('medical.member.write:')).map(([, value]) => JSON.parse(value)))
  assert.deepEqual(unchanged, metadata)
  await page.getByRole('button', { name: '查询原成员管理操作', exact: true }).click()
  await page.getByText('找到原操作；操作完成，仍需原参数及目标核查', { exact: true }).waitFor()
  await page.getByRole('button', { name: '填写原管理参数', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '授予独立权限', exact: true }); await dialog.waitFor()
  await fill(dialog, item.grant_request)
  checks.push('different_original_parameters_refused_without_new_write')
  const grant = await submit(dialog, '/api/permission-decisions')
  assert.equal(writes[0].key, writes[2].key); assert.equal(grant.grant.status, 'ACTIVE'); assert.equal(grant.grant.revision, '1')
  await page.locator('[data-governance-detail]').getByText(grant.grant.id, { exact: false }).waitFor()
  checks.push('original_grant_replay_reopens_actual_target_and_operation')
  await page.getByRole('button', { name: '撤销当前授权', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '撤销当前授权', exact: true }); await dialog.waitFor()
  assert.equal(await dialog.getByLabel('原授权标识', { exact: true }).inputValue(), grant.grant.id)
  assert.equal(await dialog.getByLabel('原对象版本', { exact: true }).inputValue(), '1')
  await dialog.getByLabel('决定依据版本', { exact: true }).fill(item.grant_request.basis.version_id)
  await dialog.getByLabel('决定依据摘要', { exact: true }).fill(item.grant_request.basis.content_sha256)
  await dialog.getByLabel('决定原因', { exact: true }).fill('SIMULATION 撤销独立授权验证')
  const revoked = await submit(dialog, '/api/permission-decisions')
  assert.equal(revoked.grant.id, grant.grant.id); assert.equal(revoked.grant.status, 'REVOKED'); assert.equal(revoked.grant.revision, '2')
  await page.locator('[data-governance-list] tr').filter({ hasText: grant.grant.id }).getByText('已撤销', { exact: true }).waitFor()
  checks.push('revoke_uses_original_grant_and_revision_then_reopens')
  await page.getByRole('button', { name: '读取成员记录', exact: true }).click()
  const member = page.locator('[data-governance-list] tr').filter({ hasText: acceptance.member_id })
  await member.getByRole('button', { name: '查看完整记录', exact: true }).click()
  await page.getByRole('button', { name: '停用当前成员', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '停用当前成员', exact: true }); await dialog.waitFor()
  assert.equal(await dialog.getByLabel('原对象版本', { exact: true }).inputValue(), acceptance.member_revision)
  await dialog.getByLabel('决定依据版本', { exact: true }).fill(item.grant_request.basis.version_id)
  await dialog.getByLabel('决定依据摘要', { exact: true }).fill(item.grant_request.basis.content_sha256)
  await dialog.getByLabel('决定原因', { exact: true }).fill('SIMULATION 停用成员验证')
  const disabled = await submit(dialog, '/api/permission-decisions')
  assert.equal(disabled.member.id, acceptance.member_id); assert.equal(disabled.member.status, 'DISABLED')
  assert.equal(disabled.member.revision, (BigInt(acceptance.member_revision) + 1n).toString()); assert.equal(disabled.revoked_grants, '2')
  checks.push('disable_reopens_member_and_revokes_role_grants')
  await page.getByRole('button', { name: '读取授权记录', exact: true }).click()
  for (const id of acceptance.grants) await page.locator('[data-governance-list] tr').filter({ hasText: id }).getByText('已撤销', { exact: true }).waitFor()
  checks.push('disabled_member_grant_records_reopened')
  await page.getByRole('button', { name: '邀请企业成员', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '邀请企业成员', exact: true }); await dialog.waitFor()
  await fill(dialog, item.invitation_request)
  let invitationCommitted = false
  await page.route('**/api/member-invitations', async route => {
    const response = await route.fetch(); assert.equal(response.status(), 201); invitationCommitted = true; await route.abort('failed')
  })
  const invitationFailed = page.waitForEvent('requestfailed', { predicate: request => new URL(request.url()).pathname === '/api/member-invitations' })
  await dialog.getByRole('button', { name: '保存成员管理决定', exact: true }).click()
  await invitationFailed; assert(invitationCommitted); await page.unroute('**/api/member-invitations')
  assert.equal(await page.locator('[data-completed-governance]').count(), 0)
  await page.reload(); await page.locator('[data-pending-governance]').waitFor()
  await page.getByRole('button', { name: '查询原成员管理操作', exact: true }).click()
  await page.getByText('找到原操作；操作完成，仍需原参数及目标核查', { exact: true }).waitFor()
  await page.getByRole('button', { name: '填写原管理参数', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '邀请企业成员', exact: true }); await dialog.waitFor()
  assert.equal(await dialog.getByLabel('完整认证绑定（JSON）', { exact: true }).inputValue(), '')
  await fill(dialog, item.invitation_request)
  const invitation = await submit(dialog, '/api/member-invitations')
  assert.equal(writes[5].key, writes[6].key)
  checks.push('committed_invitation_recovery_uses_original_key_and_parameters')
  assert.equal(invitation.user_id, item.new_account.user_id); assert.equal(invitation.revision, '1')
  assert.equal(await page.getByLabel('本次邀请凭据', { exact: true }).inputValue(), invitation.credential)
  const stored = await page.evaluate(() => [...Object.values(sessionStorage), ...Object.values(localStorage)])
  assert(stored.every(value => !value.includes(invitation.credential)))
  await page.getByRole('button', { name: '清除邀请凭据', exact: true }).click()
  assert.equal(await page.locator('#governance-credential').count(), 0)
  checks.push('actual_invitation_reopened_and_secret_stays_in_memory')
  const invited = await browser.newContext()
  try {
    const target = await invited.newPage()
    await target.goto(origin + '/access'); await target.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor()
    await target.getByRole('textbox', { name: '用户名', exact: true }).fill(item.new_account.username)
    await target.getByLabel('密码', { exact: true }).fill(item.new_account.password)
    await target.getByRole('button', { name: '登录账户', exact: true }).click(); await target.waitForURL(origin + '/projects')
    await target.getByRole('link', { name: '接受企业邀请', exact: true }).click()
    await target.getByLabel('企业标识', { exact: true }).fill(input.tenant)
    await target.getByLabel('邀请标识', { exact: true }).fill(invitation.invitation_id)
    await target.getByLabel('邀请原版本', { exact: true }).fill('1')
    await target.getByLabel('邀请凭据', { exact: true }).fill(invitation.credential)
    await target.getByRole('button', { name: '确认接受邀请', exact: true }).click()
    await target.locator('[data-accepted-invitation]').waitFor()
    await target.getByRole('button', { name: '选择接受邀请的企业', exact: true }).click(); await target.waitForURL(origin + '/projects')
    const session = (await (await invited.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })).json()).data
    for (const path of ['/api/projects/' + input.project, '/api/tasks/' + input.task]) {
      assert.equal((await invited.request.get(origin + path, { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision } })).status(), 200)
    }
    checks.push('manager_invitation_accepted_by_actual_account_reaches_consumers')
  } finally { await invited.close() }
  assert.equal(writes.length, 7); assert.equal(new Set(writes.map(write => write.key)).size, 4)
  return checks
}
for (const [index, mode] of ['development', 'production'].entries()) {
  const item = input.accounts[index]
  const origin = mode === 'development' ? 'http://127.0.0.1:18083' : 'http://127.0.0.1:18084'
  const configuration = { mode: 'medical', configFile: path.join(root, 'web/vite.config.js'), build: { outDir: path.resolve(frontendValue) },
    server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false, proxy },
    preview: { host: '127.0.0.1', port: 18084, strictPort: true, proxy } }
  const server = mode === 'development' ? await createServer(configuration) : await preview(configuration)
  try {
    if (mode === 'development') await server.listen()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const context = await browser.newContext()
      try {
        const page = await context.newPage(); const checks = []; const writes = []; const errors = []
        page.on('pageerror', error => errors.push(error.name))
        const acceptPath = '/api/member-invitations/' + item.invitation.invitation_id + '/acceptances'
        page.on('request', request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname === acceptPath) {
            assert.equal(request.headers()['x-context-revision'], undefined)
            assert.equal(request.headers().authorization, undefined)
            writes.push(request.headers()['idempotency-key'])
          }
        })
        await page.goto(origin + '/access')
        await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 60000 })
        await page.getByRole('textbox', { name: '用户名', exact: true }).fill(item.account.username)
        await page.getByLabel('密码', { exact: true }).fill(item.account.password)
        await page.getByRole('button', { name: '登录账户', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        await page.getByRole('link', { name: '接受企业邀请', exact: true }).click()
        await page.getByRole('heading', { name: '接受企业邀请', exact: true }).waitFor()
        await page.locator('[data-invitation-actor]').getByText(item.account.user_id, { exact: true }).waitFor()
        checks.push('authenticated_entry_without_selected_enterprise')
        await page.getByRole('button', { name: '确认接受邀请', exact: true }).click()
        await page.getByText('请填写完整邀请凭据', { exact: true }).waitFor()
        assert.equal(writes.length, 0); checks.push('required_inputs_prevent_submission')
        await page.getByLabel('企业标识', { exact: true }).fill(input.tenant)
        await page.getByLabel('邀请标识', { exact: true }).fill(item.invitation.invitation_id)
        await page.getByLabel('邀请原版本', { exact: true }).fill('1')
        await page.getByLabel('邀请凭据', { exact: true }).fill(item.invitation.credential)
        await page.getByRole('link', { name: '返回项目中心', exact: true }).click()
        const discard = page.getByRole('dialog', { name: '处理未提交的邀请输入', exact: true })
        await discard.waitFor(); await discard.getByRole('button', { name: '继续编辑', exact: true }).click()
        assert((await page.getByLabel('邀请凭据', { exact: true }).inputValue()) === item.invitation.credential, '继续编辑须保留当前输入')
        checks.push('unsaved_navigation_requires_explicit_choice')
        const wrong = item.invitation.credential[0] === 'A' ? 'B' : 'A'
        await page.getByLabel('邀请凭据', { exact: true }).fill(wrong + item.invitation.credential.slice(1))
        const refused = page.waitForResponse(value => new URL(value.url()).pathname === acceptPath && value.request().method() === 'POST')
        await page.getByRole('button', { name: '确认接受邀请', exact: true }).click()
        assert.equal((await refused).status(), 403)
        await page.locator('[data-pending-invitation]').waitFor()
        assert.equal(await page.locator('[data-accepted-invitation]').count(), 0)
        await page.getByRole('button', { name: '重新核查邀请身份', exact: true }).click()
        await page.locator('[data-invitation-actor]').waitFor()
        checks.push('credential_refusal_keeps_original_request')
        await page.getByLabel('邀请凭据', { exact: true }).fill(item.invitation.credential)
        let committed = false
        await page.route('**' + acceptPath, async route => {
          const actual = await route.fetch(); assert.equal(actual.status(), 200); committed = true; await route.abort('failed')
        })
        const interrupted = page.waitForEvent('requestfailed', { predicate: value => new URL(value.url()).pathname === acceptPath })
        await page.getByRole('button', { name: '核查原邀请接受结果', exact: true }).click()
        await interrupted; assert(committed); await page.unroute('**' + acceptPath)
        assert.equal(await page.locator('[data-accepted-invitation]').count(), 0)
        checks.push('committed_interruption_has_no_false_success')
        await page.reload()
        await page.locator('[data-pending-invitation]').waitFor()
        assert.equal(await page.getByLabel('邀请凭据', { exact: true }).inputValue(), '')
        const stored = await page.evaluate(() => [...Object.values(sessionStorage), ...Object.values(localStorage)])
        assert(stored.every(value => !value.includes(item.invitation.credential) && !value.includes(item.account.password)))
        assert.equal(new URL(page.url()).search, ''); assert.equal(new URL(page.url()).hash, '')
        checks.push('refresh_keeps_metadata_without_secret')
        await page.getByLabel('邀请凭据', { exact: true }).fill(item.invitation.credential)
        const replay = page.waitForResponse(value => new URL(value.url()).pathname === acceptPath && value.request().method() === 'POST')
        await page.getByRole('button', { name: '核查原邀请接受结果', exact: true }).click()
        const received = await replay; assert.equal(received.status(), 200)
        const result = (await received.json()).data
        await page.locator('[data-accepted-invitation]').getByText(result.member_id, { exact: true }).waitFor()
        assert.equal(result.grants.length, 2); assert.equal(new Set(writes).size, 1); assert.equal(writes.length, 3)
        assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.invitation.accept:'))), [])
        const actualOptions = await context.request.get(origin + '/api/session/tenant-selection-options', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(actualOptions.status(), 200); assert.equal((await actualOptions.json()).data.selected_tenant_id, null)
        checks.push('original_replay_reopens_actual_membership')
        const selectionPath = '/api/session/tenant-selections'; const selections = []
        page.on('request', request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname === selectionPath) selections.push(request.headers()['idempotency-key'])
        })
        let selectionCommitted = false
        await page.route('**' + selectionPath, async route => {
          const actual = await route.fetch(); assert.equal(actual.status(), 200); selectionCommitted = true; await route.abort('failed')
        })
        const selectionInterrupted = page.waitForEvent('requestfailed', { predicate: value => new URL(value.url()).pathname === selectionPath })
        await page.getByRole('button', { name: '选择接受邀请的企业', exact: true }).click()
        await selectionInterrupted; assert(selectionCommitted); await page.unroute('**' + selectionPath)
        await page.reload()
        await page.locator('[data-pending-invitation-selection]').waitFor()
        await page.getByRole('button', { name: '核查原企业选择结果', exact: true }).click()
        await page.waitForURL(origin + '/projects')
        assert.equal(selections.length, 2); assert.equal(new Set(selections).size, 1)
        assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('medical.invitation.selection:'))), [])
        checks.push('enterprise_selection_interruption_preserves_original_request')
        await page.getByText('当前企业：' + input.tenant + '；上下文版本：', { exact: false }).waitFor()
        const sessionResponse = await context.request.get(origin + '/api/session', { headers: { 'X-Schema-Version': '1.0.0' } })
        assert.equal(sessionResponse.status(), 200)
        const session = (await sessionResponse.json()).data
        for (const target of ['/api/projects/' + input.project, '/api/tasks/' + input.task]) {
          assert.equal((await context.request.get(origin + target, { headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision } })).status(), 200)
        }
        checks.push('explicit_enterprise_selection_reaches_authorized_consumers')
        const governanceReads = []
        page.on('request', request => {
          if (request.method() === 'GET' && ['/api/enterprise-members', '/api/permission-grants', '/api/member-invitations'].includes(new URL(request.url()).pathname)) governanceReads.push(request.url())
        })
        await page.goto(origin + '/enterprises/members')
        await page.getByRole('heading', { name: '企业成员与授权', exact: true }).waitFor()
        await page.getByText('当前企业没有成员管理或项目授权许可，请核查当前身份与授权。', { exact: true }).waitFor()
        assert.equal(await page.locator('[data-governance-list]').count(), 0); assert.equal(governanceReads.length, 0)
        for (const name of ['邀请企业成员', '授予独立权限', '撤销当前授权', '停用当前成员']) assert.equal(await page.getByRole('button', { name, exact: true }).count(), 0)
        checks.push('direct_manager_page_refuses_ordinary_member')
        await page.getByRole('link', { name: '接受企业邀请', exact: true }).click()
        await page.getByLabel('邀请凭据', { exact: true }).fill(item.invitation.credential)
        await context.setOffline(true)
        await page.waitForFunction(() => document.querySelector('#invitation-credential')?.value === '')
        await context.setOffline(false)
        await page.getByRole('button', { name: '重新核查邀请身份', exact: true }).click()
        await page.locator('[data-invitation-actor]').waitFor()
        assert.equal(await page.getByLabel('邀请凭据', { exact: true }).inputValue(), '')
        checks.push('offline_clears_secret_and_identity_reopens')
        const managementChecks = await checkManagement(browser, origin, item, result)
        assert.deepEqual(errors, [])
        results.push({ mode, browser_version: browser.version(), checks, management_checks: managementChecks.checks, management_write_checks: managementChecks.writeChecks, data_kind: 'SIMULATION', customer_success_verified: false })
      } finally { await context.close() }
    } finally { await browser.close() }
  } finally { await server.close() }
}
await writeFile(path.join(output, 'member-browser-result.json'), JSON.stringify({ results }, null, 2) + '\n')
console.log(JSON.stringify({ results }))
