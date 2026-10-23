import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const seed = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/acceptance-workspace-seed.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
try {
  const page = await context.newPage()
  page.setDefaultTimeout(90000)
  const failures = []
  page.on('pageerror', error => failures.push(error.message))
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
  await page.getByLabel('密码', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  const path = '/rules/' + seed.workspace_rule_version
  await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), path)
  await page.locator('[data-rule-preview]').waitFor()
  await page.getByText("#attributes['simulation_state'] == 'READY'", { exact: true }).waitFor()
  await page.getByRole('button', { name: '登记规则人工决定', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认规则人工决定', exact: true })
  await dialog.locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '退回', exact: true }).click()
  await dialog.getByLabel('决定理由', { exact: true }).fill('SIMULATION 验收退回：当前表达式仅供本地功能验证，缺少专业适用依据。')
  await dialog.getByText('已经核查规则表达式、输入属性、全部常量及获授权依据', { exact: true }).click()
  const result = page.waitForResponse(response => response.url().includes('/approval-decisions') && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: '确认登记规则决定', exact: true }).click()
  const response = await result
  assert.equal(response.status(), 200)
  const body = (await response.json()).data
  assert.equal(body.decision, 'RETURN')
  assert.equal(body.status, 'RETURNED')
  await page.locator('[data-rule-completed]').waitFor()
  assert.deepEqual(failures, [])
  const proof = { verified_at: new Date().toISOString(), rule_version: seed.workspace_rule_version, decision: body.decision, status: body.status, operation_id: body.operation_id, actual_browser_form: true, professional_approval: false }
  writeFileSync(new URL('../../.implementation-work/rule-acceptance-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
  console.log(JSON.stringify(proof))
} finally { await context.close(); await browser.close() }
