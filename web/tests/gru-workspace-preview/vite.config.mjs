import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
const local = name => fileURLToPath(new URL(name, import.meta.url))
export default defineConfig({ plugins: [vue()], resolve: { alias: [
 {find:'@/utils/request',replacement:local('./transport.ts')},
 {find:'@/store/modules/user',replacement:local('./user.ts')},
 {find:'@',replacement:local('../../src')}
] }, build: { outDir:'/tmp/gru-workspace-preview', assetsInlineLimit:10000000, cssCodeSplit:false, rollupOptions:{input:local('./index.html'),output:{inlineDynamicImports:true}} } })
