import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const proof = []
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { form: owner, headers: { Origin: origin, [csrf.header_name]: csrf.token }, maxRedirects: 0 })).status(), 302)
  const page = await context.newPage()
  await page.goto(origin + '/index', { waitUntil: 'networkidle', timeout: 120000 })
  for (const path of ['/tool/build', '/tool/gen', '/tool/swagger']) {
    await page.evaluate(path => document.getElementById('app').__vue_app__.config.globalProperties.$router.push(path), path)
    await page.waitForTimeout(1000)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    const state = await page.evaluate(() => {
      const app = document.getElementById('app').__vue_app__, user = app.config.globalProperties.$pinia._s.get('user')
      return { loading: user.medicalLoading, session: !!user.medicalSession, matched: app.config.globalProperties.$router.currentRoute.value.matched.map(row => ({ path: row.path, name: row.name, meta: row.meta, component: row.components?.default?.__name ?? row.components?.default?.name, file: row.components?.default?.__file })), html: document.querySelector('.app-main').innerHTML.slice(0, 3000) }
    })
    proof.push({ path, state, text: await page.locator('.app-main').innerText() })
    console.log(JSON.stringify(proof[proof.length - 1]))
  }
  assert.ok(proof.every(value => value.text.trim().length > 0))
} finally {
  writeFileSync(new URL('../../.implementation-work/framework-tools-checks.json', import.meta.url), JSON.stringify(proof, null, 2))
  await context.close(); await browser.close()
}
