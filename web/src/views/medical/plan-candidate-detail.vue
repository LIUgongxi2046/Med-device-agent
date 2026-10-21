<template>
  <section class="plan-detail" aria-label="完整目录计划预览">
    <h3>完整目录计划</h3>
    <p>目录节点 {{ plan.requirement_node_ids.length }} 个 · 章节 {{ plan.sections.length }} 个 · 研究问题 {{ plan.questions.length }} 个</p>
    <p v-if="missingOwners || missingRules" class="warning" role="status">待补充：{{ missingOwners }} 个章节尚未分配责任人，{{ missingRules }} 个章节尚无完成规则。
    </p>
    <section v-for="(section,index) in plan.sections" :key="section.section_id" class="section">
      <h4>{{ index + 1 }}. {{ section.goal }}</h4>
      <dl class="summary">
        <div>
          <dt>责任人员</dt>
          <dd>{{ section.owner_id || '尚未分配' }}</dd>
        </div>
        <div>
          <dt>处理方法</dt>
          <dd>{{ methodLabels[section.method] }}</dd>
        </div>
        <div>
          <dt>目录覆盖</dt>
          <dd>{{ section.requirement_node_ids.length }} 个固定节点</dd>
        </div>
        <div>
          <dt>完成规则</dt>
          <dd>{{ section.completion_rule_ids.length ? section.completion_rule_ids.length + ' 条已声明规则' : '尚未声明，不能据此确认完成' }}</dd>
        </div>
      </dl>
      <p v-if="section.depends_on_section_ids.length">依赖章节：{{ section.depends_on_section_ids.map(sectionLabel).join('；') }}</p>
      <p v-else>没有声明章节依赖。</p>
      <ul v-if="questions(section.section_id).length">
        <li v-for="question in questions(section.section_id)" :key="question.question_key">{{ question.question }}<span
            v-if="!question.completion_rule_ids.length">（尚无完成规则）</span></li>
      </ul>
      <details>
        <summary>章节固定标识与规则</summary>
        <dl>
          <dt>章节标识</dt>
          <dd>{{ section.section_id }}</dd>
          <dt>目录节点标识</dt>
          <dd>{{ section.requirement_node_ids.join('、') }}</dd>
          <dt>完成规则标识</dt>
          <dd>{{ section.completion_rule_ids.join('、') || '无' }}</dd>
        </dl>
      </details>
    </section>
    <section v-if="plan.pending_condition_ids.length" class="warning" aria-label="待确认条件">
      <h4>仍待确认的条件</h4>
      <p>以下 {{ plan.pending_condition_ids.length }} 个条件保持待确认状态，未被推断为通过。</p>
      <ul>
        <li v-for="id in plan.pending_condition_ids" :key="id">{{ id }}</li>
      </ul>
    </section>
    <p v-if="!plan.questions.length">当前计划未声明研究问题。</p>
    <details>
      <summary>计划固定版本与完整目录标识</summary>
      <dl>
        <dt>模板版本</dt>
        <dd>{{ plan.template_version }}</dd>
        <dt>项目属性版本</dt>
        <dd>{{ plan.profile_revision }}</dd>
        <dt>全部目录节点</dt>
        <dd>{{ plan.requirement_node_ids.join('、') }}</dd>
      </dl>
    </details>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { PlanCandidate, PlanSection } from '@/api/medical/task-plans';
const props = defineProps<{
    plan: PlanCandidate;
}>();
const methodLabels: Record<PlanSection['method'], string> = { DETERMINISTIC: '确定性处理', MODEL_ASSISTED: '模型辅助处理', HUMAN_PROVIDED: '人工提供内容' };
const missingOwners = computed(() => props.plan.sections.filter(section => section.owner_id === null).length);
const missingRules = computed(() => props.plan.sections.filter(section => section.completion_rule_ids.length === 0).length);
const sectionLabel = (id: string) => props.plan.sections.find(section => section.section_id === id)?.goal ?? id;
const questions = (id: string) => props.plan.questions.filter(question => question.section_id === id);
</script>
<style scoped>
.plan-detail {
  display: grid;
  gap: 1rem
}

.section {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 1rem
}

h3,
h4 {
  margin: .2rem 0 .7rem
}

.summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: .8rem
}

dt {
  font-weight: 600
}

dd {
  margin: .3rem 0;
  overflow-wrap: anywhere
}

.warning {
  padding: 1rem;
  background: var(--el-color-warning-light-9);
  border-left: 3px solid var(--el-color-warning)
}

p,
li {
  line-height: 1.6
}

details {
  margin-top: .8rem
}

summary {
  cursor: pointer;
  color: var(--el-color-primary)
}

@media(max-width:680px) {
  .summary {
    grid-template-columns: 1fr
  }
}
</style>
