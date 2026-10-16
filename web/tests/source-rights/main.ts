import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { validate } from '../../src/api/medical/protocol'
import { rightsInputIssues } from '../../src/api/medical/source-rights'
import { clearExpiredRequestMetadata, expireRequestMetadata, prepareRequestMetadata, readRequestMetadata } from '../../src/api/medical/request-metadata'

declare global {
  interface Window { sourceRightsChecks: { validate: typeof validate; rightsInputIssues: typeof rightsInputIssues;
    clearExpiredRequestMetadata: typeof clearExpiredRequestMetadata; expireRequestMetadata: typeof expireRequestMetadata;
    prepareRequestMetadata: typeof prepareRequestMetadata; readRequestMetadata: typeof readRequestMetadata } }
}
window.sourceRightsChecks = { validate, rightsInputIssues, clearExpiredRequestMetadata, expireRequestMetadata, prepareRequestMetadata, readRequestMetadata }
createApp({ render: () => h('h1', '来源权利Schema检查') }).use(createPinia()).mount('#app')
