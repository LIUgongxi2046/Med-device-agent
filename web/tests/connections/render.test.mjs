import test from 'node:test'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
const server = await createServer({ root: fileURLToPath(new URL('../..', import.meta.url)), configFile: false, plugins: [vue()], optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true }, appType: 'custom' })
try {
  const { default: Connections } = await server.ssrLoadModule('/src/views/medical/connections.vue')
  await test('default rendered screen labels unknown data and no real connection', async () => {
    const html = await renderToString(createSSRApp(Connections))
    assert.match(html, /未连接后端 · BLOCKED/)
    assert.match(html, /企业连接管理权限尚未确认/)
    assert.match(html, /未知 · 尚无真实回执/)
    assert.match(html, /业务接入方向，非已配置连接列表/)
    assert.doesNotMatch(html, /保存成功|连接成功|已连接/)
  })
  await test('permission-denied screen never renders configuration catalogue or details', async () => {
    const html = await renderToString(createSSRApp(Connections, { access: 'denied' }))
    assert.match(html, /没有企业连接管理权限/)
    assert.doesNotMatch(html, /账号与单点登录|资料系统连接|connection-detail-title|connection-search|connection-form/)
  })
  await test('loading screen clears catalogue and exposes busy state', async () => {
    const html = await renderToString(createSSRApp(Connections, { viewState: 'loading', preview: true }))
    assert.match(html, /aria-busy="true"/)
    assert.match(html, /模拟数据 \/ 未连接后端/)
    assert.doesNotMatch(html, /connection-search|connection-detail-title/)
  })
  await test('error, disconnected and receipt states render distinctly without success', async () => {
    for (const [viewState, label] of [['error','连接信息读取失败'], ['disconnected','连接状态无法确认'], ['verifying','等待验证回执'], ['sync-error','同步错误待核查'], ['empty','暂无可展示的连接记录']]) {
      const html = await renderToString(createSSRApp(Connections, { viewState }))
      assert.ok(html.includes(label))
      assert.match(html, /disabled aria-describedby="connection-disabled-reason"[^>]*>验证连接/)
    }
  })
} finally { await server.close() }
