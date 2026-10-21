// Run from web/. Builds the actual Vue preview; does not create a static imitation.
import { spawnSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const web = fileURLToPath(new URL('../..', import.meta.url))
const output = process.argv[2] || '/workspace/shared/chapter-candidates-preview.html'
if (!process.argv.includes('--inline-only')) {
  const result = spawnSync(process.execPath, ['--max-old-space-size=3072', path.join(web, 'node_modules/vite/bin/vite.js'), 'build', '--config', 'tests/chapter-candidates-preview/vite.config.mjs'], { cwd: web, stdio: 'inherit' })
  if (result.status !== 0) process.exit(result.status || 1)
}
const directory = '/tmp/chapter-candidates-preview'
let html = await readFile(path.join(directory, 'tests/chapter-candidates-preview/index.html'), 'utf8')
for (const match of [...html.matchAll(/<script\b[^>]*src="([^"]+)"[^>]*><\/script>/g)]) {
  const code = await readFile(path.join(directory, match[1].replace(/^\//, '')), 'utf8')
  html = html.replace(match[0], () => `<script type="module">${code.replace(/<\/script/gi, '<\\/script')}</script>`)
}
for (const match of [...html.matchAll(/<link\b[^>]*href="([^"]+\.css)"[^>]*>/g)]) {
  const css = await readFile(path.join(directory, match[1].replace(/^\//, '')), 'utf8')
  html = html.replace(match[0], () => `<style>${css.replace(/<\/style/gi, '<\\/style')}</style>`)
}
if (/<script\b[^>]*src=|<link\b[^>]*href=/i.test(html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '').replace(/<style>[\s\S]*?<\/style>/gi, ''))) throw Error('External entry assets remain')
await writeFile(output, html)
console.log(`Standalone actual-Vue preview: ${output} (${Buffer.byteLength(html)} bytes)`)
