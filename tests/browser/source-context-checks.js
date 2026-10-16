import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const frontend = path.resolve(process.argv[2])
assert(frontend.startsWith(path.join(root, '.implementation-work') + path.sep))
const output = path.join(root, '.implementation-work/FRONT-SOURCE-CONTEXT-20261013')
await mkdir(output, { recursive: true })
const owner = JSON.parse(await readFile(path.join(root, '.implementation-work/runtime/owner-credentials.json'), 'utf8'))
const proxy = {}
for (const prefix of ['/api', '/auth', '/local-auth']) proxy[prefix] = {
  target: 'http://127.0.0.1:18080', changeOrigin: true, autoRewrite: true,
  configure(value) {
    value.on('proxyReq', (forward, request) => {
      if (request.headers.origin !== undefined) {
        assert(['http://127.0.0.1:18083', 'http://127.0.0.1:18084'].includes(request.headers.origin))
        forward.setHeader('Origin', 'http://127.0.0.1:18080')
      }
    })
  }
}
process.chdir(path.join(root, 'web'))
const checks = []
for (const mode of ['development', 'production']) {
  const options = { configFile: path.join(root, 'web/vite.config.js'), mode: 'medical', cacheDir: path.join(output, 'vite-cache'),
    server: { host: '127.0.0.1', port: 18083, strictPort: true, open: false, proxy },
    preview: { host: '127.0.0.1', port: 18084, strictPort: true, open: false, proxy }, build: { outDir: frontend } }
  const server = mode === 'development' ? await createServer(options) : await preview(options)
  try {
    if (mode === 'development') await server.listen()
    const origin = 'http://127.0.0.1:' + (mode === 'development' ? '18083' : '18084')
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const page = await browser.newPage(); const errors = []; const writes = []
      page.setDefaultNavigationTimeout(120000); page.setDefaultTimeout(60000)
      page.on('pageerror', error => errors.push(error.message))
      page.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname.startsWith('/api/')) writes.push(new URL(request.url()).pathname) })
      await page.goto(origin + '/access', { waitUntil: 'domcontentloaded' })
      await page.getByRole('heading', { name: '医疗器械业务平台', exact: true }).waitFor({ timeout: 120000 })
      console.log(JSON.stringify({ mode, stage: 'LOGIN_READY' }))
      if (mode === 'production') {
        const served = await page.request.get(origin + '/index.html')
        assert.deepEqual(await served.body(), await readFile(path.join(frontend, 'index.html')))
        for (const element of await page.locator('script[src],link[rel=stylesheet]').all()) {
          const name = await element.evaluate(value => value.tagName === 'SCRIPT' ? value.getAttribute('src') : value.getAttribute('href'))
          const resource = new URL(name, origin); assert.equal(resource.origin, origin)
          const actual = await page.request.get(resource.href); assert.equal(actual.status(), 200)
          assert.deepEqual(await actual.body(), await readFile(path.join(frontend, resource.pathname)))
        }
      }
      await page.getByRole('textbox', { name: '用户名', exact: true }).fill(owner.username)
      await page.getByLabel('密码', { exact: true }).fill(owner.password)
      await page.getByRole('button', { name: '登录账户', exact: true }).click()
      await page.waitForURL(origin + '/index')
      console.log(JSON.stringify({ mode, stage: 'LOGIN_COMPLETE' }))
      for (const [pathname, filterName] of [['/library', '资料筛选'], ['/library/processing', '处理队列筛选'], ['/library/artifacts', '成果筛选']]) {
        await page.goto(origin + pathname, { waitUntil: 'domcontentloaded' })
        const filters = page.locator('form[aria-label="' + filterName + '"]'); await filters.waitFor()
        const input = filters.getByRole('textbox').first(); await input.fill(randomUUID())
        if (pathname === '/library') await page.getByRole('textbox', { name: '文件夹名称', exact: true }).fill('SIMULATION 文件夹')
        const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/session' && value.status() === 200)
        await page.getByRole('button', { name: '重新读取会话', exact: true }).click()
        await response
        await page.getByRole('button', { name: '重新读取会话', exact: true }).waitFor({ state: 'visible' })
        assert.equal(await input.inputValue(), '')
        if (pathname === '/library') assert.equal(await page.getByRole('textbox', { name: '文件夹名称', exact: true }).inputValue(), '')
        checks.push({ mode, pathname, data_kind: 'SIMULATION', result: 'PASS' })
        console.log(JSON.stringify({ mode, pathname, result: 'PASS' }))
      }
      await page.goto(origin + '/library/search', { waitUntil: 'domcontentloaded' })
      const query = page.getByRole('textbox', { name: '全文查询', exact: true }); await query.fill('SIMULATION 查询原文')
      const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/session' && value.status() === 200)
      await page.getByRole('button', { name: '重新读取会话', exact: true }).click(); await response
      assert.equal(await query.inputValue(), '')
      assert.equal(new URL(page.url()).search.includes('SIMULATION'), false)
      checks.push({ mode, pathname: '/library/search', data_kind: 'SIMULATION', result: 'PASS' })
      console.log(JSON.stringify({ mode, pathname: '/library/search', result: 'PASS' }))
      assert.deepEqual(errors, []); assert.deepEqual(writes, [])
    } finally { await browser.close() }
  } finally {
    if (mode === 'development') await server.close()
    else await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
  }
}
await writeFile(path.join(output, 'checks.json'), JSON.stringify({ scope: 'SESSION_INPUT_CLEARING', checks }, null, 2) + '\n')
console.log(JSON.stringify({ scope: 'SESSION_INPUT_CLEARING', result: 'PASS', checks: checks.length }))
