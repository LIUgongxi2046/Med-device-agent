<template>
 <section class="manifest-field">
  <label :for="fieldId">{{ label }}</label>
  <output v-if="'const' in resolved">{{ resolved.const }}</output>
  <template v-else-if="identityPicker"><select :id="fieldId" :value="modelValue" :disabled="disabled" @change="inputValue(($event.target as HTMLSelectElement).value)"><option value="">选择已读取的真实身份</option><option v-for="item in identities" :key="item.value" :value="item.value">{{ item.label }}</option></select><p v-if="!identities.length" class="muted">请先读取当前授权范围的真实成员、角色、项目或固定依赖目录。</p></template>
  <template v-else-if="referenceType">
   <select :id="fieldId" :disabled="disabled" :value="selected" @change="choose(($event.target as HTMLSelectElement).value)"><option value="">选择真实固定候选</option><option v-for="(item,i) in options" :key="i" :value="String(i)">{{ item.label }}</option></select>
   <p v-if="!options.length" class="muted">尚无此类已读取候选。请先在依赖目录读取真实版本；目录可读不代表可发布。</p>
   <details v-if="modelValue && Object.values(modelValue).some(Boolean)"><summary>已选固定身份</summary><dl><template v-for="(value,key) in modelValue" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl></details>
  </template>
  <details v-else-if="resolved.type==='object'" open><summary>{{ label }}字段</summary><formal-agent-manifest-field v-for="(child,key) in resolved.properties" :key="key" :schema="child" :path="path ? path+'.'+String(key) : String(key)" :model-value="modelValue?.[key]" :choices="choices" :disabled="disabled" @update:model-value="setField(String(key),$event)"/></details>
  <template v-else-if="resolved.type==='array'">
   <p class="muted">至少 {{ resolved.minItems||0 }} 项<span v-if="resolved.maxItems">，最多 {{ resolved.maxItems }} 项</span>；按完整固定声明保存。</p>
   <article v-for="(item,index) in (Array.isArray(modelValue)?modelValue:[])" :key="index"><formal-agent-manifest-field :schema="resolved.items" :path="path+'.'+index" :model-value="item" :choices="choices" :disabled="disabled" @update:model-value="setItem(index,$event)"/><button type="button" :disabled="disabled" @click="removeItem(index)">删除第 {{ index+1 }} 项</button></article>
   <button type="button" :disabled="disabled || modelValue?.length >= (resolved.maxItems||256)" @click="addItem">添加 {{ label }}</button>
  </template>
  <select v-else-if="resolved.enum" :id="fieldId" :value="modelValue" :disabled="disabled" @change="inputValue(($event.target as HTMLSelectElement).value)"><option value="">请选择</option><option v-for="value in resolved.enum" :key="String(value)" :value="value">{{ value }}</option></select>
  <input v-else :id="fieldId" :type="resolved.type==='integer'||resolved.type==='number'?'number':'text'" :value="modelValue??''" :disabled="disabled" :maxlength="resolved.maxLength||2048" @input="inputValue(($event.target as HTMLInputElement).value)"/>
 </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { resolveManifestSchema, emptyManifestValue, manifestReferenceType, manifestLabel, referenceChoices, selectedManifestChoice, identityChoices } from '@/api/medical/formal-agent-manifest-form';
import type { ManifestChoice } from '@/api/medical/formal-agent-manifest-form';
import { draftCanonical } from '@/api/medical/formal-agent-drafts';
const props = defineProps<{
    schema: Record<string, any>;
    path: string;
    modelValue: any;
    choices: ManifestChoice[];
    disabled: boolean;
}>();
const emit = defineEmits<{
    (event: 'update:modelValue', value: any): void;
}>();
const resolved = computed(() => resolveManifestSchema(props.schema)), referenceType = computed(() => manifestReferenceType(props.schema)), label = computed(() => manifestLabel(props.path.split('.').at(-1) || '声明')), fieldId = computed(() => 'manifest-' + props.path.replaceAll('.', '-'));
const identityPicker = computed(() => resolved.value.format === 'uuid' && !props.path.endsWith('operation_id')), identities = computed(() => identityChoices(props.path, props.choices));
const options = computed(() => referenceChoices(props.path, referenceType.value || '', props.choices));
const selected = computed(() => { const i = options.value.findIndex(x => draftCanonical(x.value) === draftCanonical(props.modelValue ?? null)); return i < 0 ? '' : String(i); });
function choose(index: string) { if (props.disabled)
    return; const value = selectedManifestChoice(options.value, index); if (value)
    emit('update:modelValue', value); }
function setField(key: string, value: any) { if (props.disabled)
    return; emit('update:modelValue', { ...(props.modelValue || {}), [key]: value }); }
function setItem(index: number, value: any) { if (props.disabled)
    return; const items = [...(props.modelValue || [])]; items[index] = value; emit('update:modelValue', items); }
function addItem() { if (props.disabled)
    return; emit('update:modelValue', [...(props.modelValue || []), emptyManifestValue(resolved.value.items)]); }
function removeItem(index: number) { if (props.disabled)
    return; emit('update:modelValue', (props.modelValue || []).filter((_: unknown, i: number) => i !== index)); }
function inputValue(value: string) { if (props.disabled)
    return; emit('update:modelValue', ['integer', 'number'].includes(resolved.value.type) && value !== '' ? Number(value) : value); }
</script>
<style scoped>
.manifest-field{display:grid;gap:.5rem;margin:.7rem 0;min-width:0}.manifest-field>label{font-weight:600}.manifest-field details,.manifest-field article{border-left:2px solid var(--el-border-color);padding-left:1rem}.manifest-field input,.manifest-field select{width:100%;padding:.55rem;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base);background:var(--el-fill-color-blank);color:var(--el-text-color-primary)}.muted,dt{color:var(--el-text-color-secondary);font-size:.85rem}dd{overflow-wrap:anywhere;margin:.2rem 0 .5rem}button{justify-self:start;padding:.4rem .7rem}output{color:var(--el-text-color-secondary)}
</style>
