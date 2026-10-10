import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { build, createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const output = path.resolve(process.argv[2])
const metrics = JSON.parse(await readFile(path.join(output, 'redis-metrics.json'), 'utf8'))
assert.equal(metrics.redis_version, '7.4.8')
assert(metrics.cache.commandStats.length > 0)
const iconDirectory = path.join(root, 'web/src/assets/icons/svg')
const icons = (await readdir(iconDirectory)).filter(name => name.endsWith('.svg')).map(name => path.basename(name, '.svg')).sort()
assert.equal(icons.length, 90)
process.chdir(path.join(root, 'web'))
const options = {
  configFile: path.join(root, 'web/vite.config.js'),
  define: { __WEB_REDIS_METRICS__: JSON.stringify(metrics) },
  server: { host: '127.0.0.1', port: 18081, strictPort: true, open: false },
  build: {
    outDir: path.join(output, 'compatibility'),
    emptyOutDir: false,
    rollupOptions: { input: path.join(root, 'web/tests/engineering/index.html') }
  }
}
await build(options)
const production = await preview({ ...options, preview: { host: '127.0.0.1', port: 18082, strictPort: true, open: false } })
try {
  const development = await createServer(options)
  try {
    await development.listen()
    const browser = await chromium.launch({ channel: 'chrome', headless: true })
    try {
      const checks = []
      for (const [mode, origin] of [['development', 'http://127.0.0.1:18081'], ['production', 'http://127.0.0.1:18082']]) {
        const page = await browser.newPage()
        try {
          const errors = []
          page.on('pageerror', error => errors.push(error.message))
          await page.goto(origin + '/tests/engineering/index.html')
          await page.waitForFunction(() => Boolean(window.engineeringCheck))
          assert.deepEqual(await page.evaluate(() => [...window.engineeringCheck.icons].sort()), icons)
          assert.equal(await page.locator('[data-catalogue] svg').count(), 90)
          const catalogue = await page.locator('[data-catalogue] svg').evaluateAll(async elements => {
            const references = elements.map(element => ({
              name: element.dataset.icon,
              href: element.querySelector('use').getAttribute('xlink:href'),
              fill: element.querySelector('use').getAttribute('fill'),
              className: element.getAttribute('class'),
              ariaHidden: element.getAttribute('aria-hidden')
            }))
            const addresses = [...new Set(references.map(item => new URL(item.href, location.href).pathname))]
            const resources = []
            for (const address of addresses) {
              const response = await fetch(address)
              if (!response.ok) throw new Error('图标资源读取失败')
              const source = await response.text()
              const document = new DOMParser().parseFromString(source, 'image/svg+xml')
              if (document.querySelector('parsererror')) throw new Error('图标资源XML无效')
              resources.push({ address, symbols: [...document.querySelectorAll('symbol')].map(symbol => ({
                id: symbol.id, viewBox: symbol.getAttribute('viewBox'), children: symbol.children.length
              })) })
            }
            return { references, resources }
          })
          assert.equal(catalogue.resources.length, 1)
          const symbols = catalogue.resources[0].symbols
          assert.deepEqual(symbols.map(symbol => symbol.id).sort(), icons.map(name => 'icon-' + name))
          assert(symbols.every(symbol => symbol.viewBox && symbol.children > 0))
          for (const item of catalogue.references) {
            assert.equal(new URL(item.href, origin).hash, '#icon-' + item.name)
            assert.equal(item.fill, '#123456')
            assert.equal(item.className, 'svg-icon engineering-icon')
            assert.equal(item.ariaHidden, 'true')
          }
          if (mode === 'production') assert(!catalogue.resources[0].address.includes('__spritemap'))
          checks.push({ mode, name: 'actual_icon_catalogue', count: 90, resource: catalogue.resources[0].address })

          const input = page.getByPlaceholder('请输入图标名称')
          const selectedIcon = icons.find(name => name === 'user')
          assert(selectedIcon)
          await input.fill(selectedIcon)
          const matching = icons.filter(name => name.includes(selectedIcon))
          await page.waitForFunction(count => document.querySelectorAll('.icon-item-wrapper').length === count, matching.length)
          await page.locator('.icon-item-wrapper').filter({ has: page.locator('span', { hasText: /^user$/ }) }).click()
          await page.waitForFunction(name => document.querySelector('[data-selected]').textContent === name, selectedIcon)
          assert.equal(await page.locator('.icon-item.active span').textContent(), selectedIcon)
          await page.locator('[data-reset]').click()
          assert.equal(await input.inputValue(), '')
          assert.equal(await page.locator('.icon-item-wrapper').count(), 90)
          await input.fill(selectedIcon)
          await input.fill('')
          await page.waitForFunction(() => document.querySelectorAll('.icon-item-wrapper').length === 90)
          checks.push({ mode, name: 'actual_icon_selection_filter_reset', selected: selectedIcon })

          const charts = await page.evaluate(() => ({ version: window.engineeringCheck.chartVersion, options: window.engineeringCheck.chartOptions() }))
          assert.equal(charts.version, '6.1.0')
          assert.equal(charts.options.commands.series[0].type, 'pie')
          assert.deepEqual(charts.options.commands.series[0].data, metrics.cache.commandStats)
          assert.equal(charts.options.commands.series[0].roseType, 'radius')
          assert.equal(charts.options.memory.series[0].type, 'gauge')
          assert.equal(charts.options.memory.series[0].data[0].value, Number.parseFloat(metrics.cache.info.used_memory_human))
          assert.equal(charts.options.memory.series[0].detail.formatter, metrics.cache.info.used_memory_human)
          assert.deepEqual(await page.evaluate(() => window.engineeringCheck.resize()), [480, 320, 480, 320])
          assert.equal(await page.evaluate(() => window.engineeringCheck.dispose()), true)
          assert.deepEqual(errors, [])
          checks.push({ mode, name: 'actual_redis_charts_resize_dispose', command_count: metrics.cache.commandStats.length })
        } finally {
          await page.close()
        }
      }
      const receipt = { browser_version: browser.version(), check_count: checks.length, checks, redis_run_id: metrics.redis_run_id }
      await writeFile(path.join(output, 'compatibility-result.json'), JSON.stringify(receipt, null, 2) + '\n')
      console.log(JSON.stringify(receipt))
    } finally {
      await browser.close()
    }
  } finally {
    await development.close()
  }
} finally {
  await new Promise(resolve => production.httpServer.close(resolve))
}

const artifacts = []
for (const directory of ['production', 'compatibility']) {
  for (const entry of await readdir(path.join(output, directory), { recursive: true, withFileTypes: true })) {
    if (entry.isFile()) {
      const filename = path.join(entry.parentPath, entry.name)
      artifacts.push({ path: filename, sha256: createHash('sha256').update(await readFile(filename)).digest('hex') })
    }
  }
}
await writeFile(path.join(output, 'build-artifacts.json'), JSON.stringify(artifacts, null, 2) + '\n')
