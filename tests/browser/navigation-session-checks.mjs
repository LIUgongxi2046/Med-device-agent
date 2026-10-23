import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const credentials = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const errors = []
const checks = []
try {
  const page = await context.newPage()
  await page.addInitScript(() => window.addEventListener('medical-failure', event => console.error('NAVIGATION_FAILURE', event.detail?.code, event.detail?.message)))
  page.setDefaultTimeout(90000)
  page.on('console', message => {
    if (message.text().startsWith('ROUTER_ERROR')) console.log(message.text())
    if (message.text().startsWith('NAVIGATION_FAILURE')) errors.push(message.text())
  })
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(credentials.username)
  await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.evaluate(() => {
    const router = document.getElementById('app').__vue_app__.config.globalProperties.$router
    router.onError((error, target) => console.log('ROUTER_ERROR', JSON.stringify({ name: error.name, code: error.code, message: error.message, target: target.fullPath, stack: error.stack })))
  })
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/index')
  for (const path of ['/projects', '/library', '/model-configurations', '/system/user', '/system/role', '/system/menu', '/index']) {
    await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), path)
    assert.equal(new URL(page.url()).pathname, path)
    checks.push({ path, mode: 'sequential' })
  }
  for (let attempt = 0; attempt < 3; attempt++) {
    const result = await page.evaluate(async () => {
      const app = document.getElementById('app').__vue_app__
      const router = app.config.globalProperties.$router
      const sessionRequest = new Promise(resolve => {
        const user = app.config.globalProperties.$pinia._s.get('user')
        const unsubscribe = user.$onAction(({ name }) => {
          if (name === 'readMedical') { unsubscribe(); resolve() }
        })
      })
      const first = router.push('/projects')
      await sessionRequest
      const second = router.push('/library')
      await Promise.allSettled([first, second])
      return { path: router.currentRoute.value.path, error: app.config.globalProperties.$pinia._s.get('user').medicalError?.code }
    })
    console.log('并发导航结果', JSON.stringify(result))
    assert.equal(result.path, '/library')
    assert.equal(result.error, undefined)
    checks.push({ path: result.path, mode: 'superseded', attempt })
    await page.evaluate(() => document.getElementById('app').__vue_app__.config.globalProperties.$router.push('/index'))
  }
  await page.waitForLoadState('networkidle')
  assert.deepEqual(errors, [])
  const result = { verified_at: new Date().toISOString(), checks, page_errors: errors, screenshots: false }
  writeFileSync(new URL('../../.implementation-work/navigation-session-checks.json', import.meta.url), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally {
  await context.close()
  await browser.close()
}
