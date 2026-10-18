<template>
  <section class="simulation-availability" :aria-label="label + '服务可用性'">
    <session-panel v-if="!user.medicalSession?.current_context" />
    <p v-if="!user.medicalSession?.current_context">请先恢复当前企业身份，再读取服务可用性。</p>
    <p v-else-if="loading" role="status">正在核查{{ label }}是否已由服务器启用…</p>
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" />
    <el-alert v-else-if="checked && !enabled" :title="label + '尚未在当前服务器启用'" type="info" :closable="false">
      此状态与账号权限不同。页面不会尝试调用未启用的业务接口；请联系管理员确认部署状态。
    </el-alert>
    <p v-if="checked && enabled" class="availability-note">{{ label }}已由服务器启用。具体操作仍需当前身份、权限及业务条件核验。</p>
    <el-button v-if="user.medicalSession?.current_context && !enabled" :disabled="loading" @click="refresh">重新核查服务状态</el-button>
    <slot v-if="enabled" />
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import useUserStore from '@/store/modules/user';
import SessionPanel from './session-panel.vue';
import { readSimulationFeatureAvailability } from '@/api/medical/simulation-feature-availability';
import type { SimulationFeature } from '@/api/medical/simulation-feature-availability';
import type { MedicalSession } from '@/api/medical/protocol';
const props = defineProps<{
    feature: SimulationFeature;
}>();
const emit = defineEmits<{
    change: [
        enabled: boolean
    ];
}>();
const user = useUserStore();
const enabled = ref(false), checked = ref(false), loading = ref(false), error = ref('');
const labels: Record<SimulationFeature, string> = {
    input_artifact_registration: 'SIM 精确输入登记',
    rule_workflow: 'SIM 规则工作流',
    evaluation_preparation: 'SIM 评估资料准备'
};
const label = computed(() => labels[props.feature]);
let generation = 0, active = true;
let controller: AbortController | null = null;
function reset(): number {
    generation++;
    controller?.abort();
    controller = null;
    enabled.value = false;
    checked.value = false;
    loading.value = false;
    error.value = '';
    emit('change', false);
    return generation;
}
async function refresh(): Promise<void> {
    const version = reset(), session: MedicalSession | null = user.medicalSession, feature = props.feature;
    if (!active || !session?.current_context || document.hidden)
        return;
    controller = new AbortController();
    loading.value = true;
    try {
        const value = await readSimulationFeatureAvailability(session, controller.signal);
        if (!active || version !== generation || user.medicalSession !== session || props.feature !== feature || document.hidden)
            return;
        checked.value = true;
        enabled.value = value.features[feature];
        emit('change', enabled.value);
    }
    catch (failure) {
        if (active && version === generation && user.medicalSession === session && !document.hidden) {
            error.value = failure instanceof Error ? failure.message : '暂时无法核查服务状态，请稍后重试';
        }
    }
    finally {
        if (version === generation)
            loading.value = false;
    }
}
function visibility(): void { if (document.hidden)
    reset();
else
    void refresh(); }
watch(() => [user.medicalSession, user.medicalSession?.current_context?.tenant_id,
    user.medicalSession?.current_context?.context_revision, user.medicalSession?.current_context?.auth_epoch,
    user.medicalSession?.current_context?.governance_epoch, props.feature], refresh, { immediate: true, flush: 'sync' });
document.addEventListener('visibilitychange', visibility);
onBeforeUnmount(() => { active = false; reset(); document.removeEventListener('visibilitychange', visibility); });
</script>
<style scoped>
.simulation-availability { min-width: 0; }
.availability-note { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 16px; }
</style>
