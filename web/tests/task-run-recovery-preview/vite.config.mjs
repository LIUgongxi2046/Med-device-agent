import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
export default defineConfig({ plugins: [vue()], resolve: { alias: { '@': fileURLToPath(new URL('../../src', import.meta.url)) } }, server: { host: '0.0.0.0', port: 18089, strictPort: true }, build: { outDir: '/tmp/task-run-recovery-preview', assetsInlineLimit: 10000000, cssCodeSplit: false, rollupOptions: { input: fileURLToPath(new URL('./index.html', import.meta.url)), output: { inlineDynamicImports: true } } } })
