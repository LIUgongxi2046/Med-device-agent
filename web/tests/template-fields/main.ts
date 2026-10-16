import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import TemplateCreate from '../../src/views/medical/template-create.vue'
import useUserStore from '../../src/store/modules/user'
import { validate } from '../../src/api/medical/protocol'
import type { MedicalSession } from '../../src/api/medical/protocol'

const tenant = crypto.randomUUID(); const actor = crypto.randomUUID()
const session: MedicalSession = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: 'SIMULATION 字段检查', platform_role: null },
  memberships: [{ tenant_id: tenant, member_id: crypto.randomUUID(), status: 'ACTIVE', valid_from: '2026-10-13T00:00:00Z', valid_to: null, revision: '1' }],
  current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
  csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION_LOCAL_COMPONENT_INPUT' },
  action_summary: [{ action: 'template.write', project_id: null, resource_kind: 'ENTERPRISE', resource_id: tenant }],
  invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } }
validate('urn:medical:identity', session)
const pinia = createPinia(); useUserStore(pinia).medicalSession = session
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: TemplateCreate }] })
async function mount(): Promise<void> {
  await router.push('/')
  createApp(TemplateCreate).use(pinia).use(router).use(ElementPlus).mount('#app')
}
void mount()
