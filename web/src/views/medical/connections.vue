<template>
  <main class="connections-page">
    <p v-if="preview" class="preview-label" role="status">界面预览 · 模拟数据 / 未连接后端 · 不产生真实连接</p>
    <p class="breadcrumb">管理 / 企业空间与连接</p>
    <header class="page-heading"><div><h1>企业空间与系统连接</h1><p>明确数据边界，核对接入条件，再验证与同步。</p></div><span class="status blocked">未连接后端 · BLOCKED</span></header>
    <section class="scope-grid" aria-label="企业空间概览">
      <article><span>企业身份与管理员</span><strong>待权威资料确认</strong><p>不推断企业身份、管理员或服务状态。</p></article>
      <article><span>数据与导出边界</span><strong>企业与项目分别授权</strong><p>连接管理权限不授予项目原文读取权限。</p></article>
      <article><span>接入进度</span><strong>准备接入条件</strong><p>配置 → 真实验证 → 批准范围内同步</p></article>
    </section>
    <div v-if="access === 'denied'" class="state-panel" role="alert"><h2>没有企业连接管理权限</h2><p>请联系企业管理员核实授权。此页面不展示连接名称、数量、配置或错误详情。</p></div>
    <template v-else>
      <div v-if="access === 'unknown'" class="permission-note" role="status">企业连接管理权限尚未确认。下方仅为接入类别和本地准备表单，不是企业实际配置。</div>
      <section class="state-panel" :class="viewState" :role="viewState === 'error' || viewState === 'sync-error' ? 'alert' : 'status'" :aria-busy="viewState === 'loading'">
        <span class="state-icon" aria-hidden="true">{{ viewState === 'loading' ? '…' : '!' }}</span><div><h2>{{ stateCopy.title }}</h2><p>{{ stateCopy.detail }}</p></div>
      </section>
      <div v-if="viewState !== 'loading'" class="workspace-grid">
        <section class="card catalogue"><div class="section-heading"><div><h2>接入类别</h2><p>业务接入方向，非已配置连接列表</p></div></div>
          <label class="search-label" for="connection-search">查找接入类别</label><input id="connection-search" v-model="search" type="search" placeholder="搜索账号、资料、服务" maxlength="100"/>
          <div class="category-list" aria-label="接入类别列表"><button v-for="category in visibleCategories" :key="category.id" type="button" class="category" :class="{ selected: selected === category.id }" :aria-pressed="selected === category.id" @click="selectCategory(category.id)"><span class="category-icon" aria-hidden="true">{{ category.icon }}</span><span><strong>{{ category.name }}</strong><small>{{ category.description }}</small><span class="category-state">待确认接入条件</span></span><span aria-hidden="true">›</span></button></div>
          <p v-if="!visibleCategories.length" class="empty" role="status">没有匹配的接入类别。请尝试其他关键词。</p>
          <p class="footnote">不展示未经核实的服务商、连接数或成功记录。</p>
        </section>
        <section class="card detail" aria-labelledby="connection-detail-title"><div class="section-heading"><div><p class="eyebrow">接入说明</p><h2 id="connection-detail-title">{{ currentCategory.name }}</h2></div><span class="status">未核实</span></div><p>{{ currentCategory.description }}</p>
          <dl class="facts"><div><dt>当前配置 / 版本</dt><dd>未知 · 尚无权威记录</dd></div><div><dt>最近验证 / 同步时间</dt><dd>未知 · 尚无真实回执</dd></div><div><dt>授权与同步范围</dt><dd>待明确批准，不默认全企业</dd></div><div><dt>错误来源 / 影响 / 重试条件</dt><dd>未知 · 等待实际错误记录</dd></div></dl>
          <div class="requirements"><h3>开始之前需要</h3><ol><li>已批准的客户系统与接口规格版本</li><li>账号映射、文档归属和固定同步范围</li><li>当前连接管理权限及目标资料写入授权</li><li>受控秘密引用及真实验证回执</li></ol></div>
          <div class="action-row"><button type="button" class="primary" @click="formOpen = !formOpen" :aria-expanded="formOpen" aria-controls="connection-form">{{ formOpen ? '收起准备表单' : '准备配置资料' }}</button><button type="button" disabled aria-describedby="connection-disabled-reason">验证连接</button><button type="button" disabled aria-describedby="connection-disabled-reason">开始同步</button></div>
          <p id="connection-disabled-reason" class="footnote">未接入 CT-01—CT-03，缺少已批准配置与权限及真实回执。所有保存、验证、同步操作保持禁用。</p>
        </section>
        <section v-if="formOpen" id="connection-form" class="card form-card"><div class="section-heading"><div><h2>配置资料准备</h2><p>仅在本页内存编辑，不发送、不保存、不验证接口。</p></div><span class="status">未提交</span></div>
          <p class="privacy-note">不要填写密码、令牌、密钥或真实敏感资料。凭据必须通过未来受控秘密流程管理，本表单不提供秘密输入。</p>
          <form @submit.prevent="validate" novalidate><div class="form-grid"><div v-for="field in fields" :key="field.key" class="field" :class="{ wide: field.multiline }"><label :for="'connection-' + field.key">{{ field.label }} <span aria-hidden="true">*</span></label><textarea v-if="field.multiline" :id="'connection-' + field.key" v-model="draft[field.key]" rows="3" maxlength="500" required :aria-invalid="Boolean(errors[field.key])" :aria-describedby="errors[field.key] ? 'error-' + field.key : undefined" :placeholder="field.placeholder"/><input v-else :id="'connection-' + field.key" v-model="draft[field.key]" :type="field.key === 'endpoint' ? 'url' : 'text'" :maxlength="field.key === 'endpoint' ? 2048 : 500" required autocomplete="off" :aria-invalid="Boolean(errors[field.key])" :aria-describedby="errors[field.key] ? 'error-' + field.key : undefined" :placeholder="field.placeholder"/><p v-if="errors[field.key]" :id="'error-' + field.key" class="field-error">{{ errors[field.key] }}</p></div></div>
            <p v-if="validationMessage" class="validation-message" role="status">{{ validationMessage }}</p><div class="action-row"><button type="submit">检查本地必填项</button><button type="button" @click="resetDraft">清空本地内容</button><button type="button" class="primary" disabled aria-describedby="connection-disabled-reason">保存并连接</button></div><p class="footnote">本地格式通过不表示规格、范围或权限已获批准，不产生待验证连接。</p>
          </form>
        </section>
      </div>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { blankConnectionDraft, connectionCategories, connectionStateCopy, validateConnectionDraft } from './connections-presentation'
import type { ConnectionAccess, ConnectionCategory, ConnectionDraft, ConnectionViewState, DraftErrors } from './connections-presentation'
const props = withDefaults(defineProps<{ access?: ConnectionAccess; viewState?: ConnectionViewState; preview?: boolean }>(), { access: 'unknown', viewState: 'blocked', preview: false })
const search = ref(''), selected = ref<ConnectionCategory>('documents'), formOpen = ref(false)
const draft = reactive(blankConnectionDraft()), errors = ref<DraftErrors>({}), validationMessage = ref('')
const currentCategory = computed(() => connectionCategories.find(item => item.id === selected.value) ?? connectionCategories[1])
const visibleCategories = computed(() => connectionCategories.filter(item => `${item.name}${item.description}`.includes(search.value.trim())))
const stateCopy = computed(() => connectionStateCopy[props.viewState])
const fields: { key: keyof ConnectionDraft; label: string; placeholder: string; multiline?: boolean }[] = [
  { key: 'name', label: '连接名称', placeholder: '填写用途，不包含敏感资料' }, { key: 'system', label: '目标系统', placeholder: '待批准的系统名称' },
  { key: 'endpoint', label: '接口地址', placeholder: 'HTTPS 地址，不含凭据或查询参数' }, { key: 'specification', label: '接口规格版本', placeholder: '待核实的接口文档版本' },
  { key: 'mapping', label: '账号映射规则', placeholder: '描述映射原则，不填写实际账号资料', multiline: true }, { key: 'ownership', label: '文档归属规则', placeholder: '描述企业与项目的文档归属边界', multiline: true },
  { key: 'scope', label: '授权与同步范围', placeholder: '明确目标与范围，不默认全部项目', multiline: true }, { key: 'reason', label: '配置理由', placeholder: '说明用途与影响，实际批准另行核实', multiline: true }
]
function resetDraft() { Object.assign(draft, blankConnectionDraft()); errors.value = {}; validationMessage.value = '' }
function selectCategory(id: ConnectionCategory) { selected.value = id }
async function validate() { errors.value = validateConnectionDraft(draft); const first = Object.keys(errors.value)[0]; validationMessage.value = first ? '请修正标出的字段。内容仅在本页，不会提交。' : '本地必填项与地址格式检查通过。尚未取得批准或真实验证，不能保存连接。'; if (first) { await nextTick(); document.getElementById('connection-' + first)?.focus() } }
watch(draft, () => { errors.value = {}; validationMessage.value = '' })
watch(() => props.access, () => { resetDraft(); formOpen.value = false; search.value = '' })
function clearOnHidden() { if (document.hidden) { resetDraft(); formOpen.value = false } }
onMounted(() => document.addEventListener('visibilitychange', clearOnHidden))
onBeforeUnmount(() => { document.removeEventListener('visibilitychange', clearOnHidden); resetDraft() })
</script>
<style scoped>
.connections-page{padding:28px;background:#f4f6fa;min-height:100%;color:#243247;font-size:14px;line-height:1.6}.breadcrumb,.eyebrow{font-size:12px;color:#64748b}.page-heading,.section-heading,.action-row{display:flex;justify-content:space-between;align-items:center;gap:16px}.page-heading{margin-bottom:24px}h1{font-size:26px;margin:4px 0}h2{font-size:18px;margin:0}h3{font-size:14px;margin:0 0 8px}p{margin:6px 0;color:#526279}.status{display:inline-block;background:#f1f5f9;color:#526279;padding:4px 10px;border-radius:6px;font-size:12px;white-space:nowrap}.blocked{color:#885609;background:#fff3da}.scope-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:20px}.scope-grid article,.card{background:#fff;border:1px solid #dce3ec;border-radius:10px;padding:22px;min-width:0}.scope-grid article>span{font-size:12px;color:#64748b}.scope-grid strong{display:block;font-size:16px;margin:6px 0}.scope-grid p{font-size:12px}.permission-note,.privacy-note,.preview-label{padding:12px 16px;background:#edf4ff;border-radius:7px;margin:16px 0;color:#275887}.preview-label{font-weight:600}.state-panel{display:flex;align-items:flex-start;gap:14px;padding:18px 20px;margin:18px 0 22px;border:1px solid #e7d29e;border-radius:9px;background:#fffaf0}.state-panel h2{font-size:15px}.state-panel p{font-size:13px}.state-icon{border-radius:50%;background:#f5e5bc;color:#885609;width:24px;min-width:24px;text-align:center;font-weight:bold}.state-panel.error,.state-panel.sync-error{background:#fff4f3;border-color:#e8bcb7}.workspace-grid{display:grid;grid-template-columns:minmax(260px, .85fr) minmax(0,1.5fr);gap:20px}.section-heading{margin-bottom:18px}.section-heading p{font-size:12px}.search-label{display:block;font-size:12px;color:#526279;margin-bottom:6px}input,textarea{box-sizing:border-box;width:100%;padding:10px 12px;background:#fff;border:1px solid #b9c6d6;border-radius:6px;font:inherit;color:inherit}textarea{resize:vertical}button{font:inherit;border:1px solid #c8d3e0;border-radius:6px;padding:9px 14px;background:#fff;color:#30435b;cursor:pointer}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:2px solid #1768c4;outline-offset:3px}button.primary{background:#1768c4;border-color:#1768c4;color:white}button:disabled{background:#f1f4f8;color:#7b899a;border-color:#dce3ec;cursor:not-allowed}.category-list{display:grid;gap:10px;margin-top:18px}.category{display:grid;grid-template-columns:38px minmax(0,1fr) 8px;gap:12px;text-align:left;padding:16px 12px;align-items:start}.category.selected{background:#f0f6ff;border-color:#377fce;box-shadow:inset 3px 0 #1768c4}.category-icon{font-size:10px;font-weight:bold;color:#1768c4;background:#e3edfb;border-radius:7px;width:36px;height:36px;display:grid;place-items:center}.category strong,.category small,.category-state{display:block}.category small{font-size:12px;color:#637186;margin:5px 0}.category-state{font-size:11px;color:#826526}.footnote{font-size:12px;color:#64748b;margin-top:16px}.facts{margin:20px 0;display:grid;grid-template-columns:1fr 1fr;gap:18px}.facts dt{font-size:12px;color:#64748b}.facts dd{margin:4px 0;font-size:13px;overflow-wrap:anywhere}.requirements{background:#f7f9fc;border-radius:8px;padding:16px}.requirements ol{padding-left:22px;margin:0;color:#526279;font-size:13px}.action-row{justify-content:flex-start;flex-wrap:wrap;margin-top:20px}.form-card{grid-column:1/-1}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.field label{display:block;margin-bottom:7px;font-weight:600;font-size:13px}.field label span{color:#ac352c}.field-error{font-size:12px;color:#ac352c}.field [aria-invalid=true]{border-color:#ac352c}.validation-message{padding:10px 12px;background:#f0f5fb;border-radius:6px}.empty{padding:24px;text-align:center}@media(max-width:900px){.workspace-grid{grid-template-columns:1fr}.scope-grid{grid-template-columns:1fr}.page-heading{align-items:flex-start;flex-direction:column}}@media(max-width:600px){.connections-page{padding:16px}.card,.scope-grid article{padding:16px}.facts,.form-grid{grid-template-columns:1fr}.section-heading{align-items:flex-start}h1{font-size:22px}}
</style>
