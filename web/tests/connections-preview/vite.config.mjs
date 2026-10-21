import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
export default defineConfig({ root: fileURLToPath(new URL('.', import.meta.url)), plugins: [vue()], build: { outDir: '/tmp/connections-preview', emptyOutDir: true, assetsInlineLimit: 10000000, cssCodeSplit: false } })
