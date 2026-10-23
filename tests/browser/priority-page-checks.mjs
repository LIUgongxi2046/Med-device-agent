import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
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
  await page.evaluate(() => document.getElementById('app').__vue_app__.config.globalProperties.$router.push('/templates'))
  await page.getByRole('heading', { name: '模板人工审查', exact: true }).waitFor()
  assert.equal(await page.locator('[data-template-list]').count(), 1, '模板审查应当提供可筛选的已授权目录')
} finally {
  await context.close()
  await browser.close()
}
